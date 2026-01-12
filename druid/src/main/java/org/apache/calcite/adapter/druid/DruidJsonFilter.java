/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Apache许可证声明,说明该文件遵循Apache 2.0许可证
package org.apache.calcite.adapter.druid; // 指定该类所属的包为org.apache.calcite.adapter.druid,这是Calcite中Druid适配器的包

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类,用于表示关系数据类型
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类,用于构建RexNode表达式
import org.apache.calcite.rex.RexCall; // 导入RexCall类,表示函数调用表达式
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类,表示字面量表达式
import org.apache.calcite.rex.RexNode; // 导入RexNode类,表示关系表达式节点的基类
import org.apache.calcite.rex.RexUtil; // 导入RexUtil类,提供RexNode的实用工具方法
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举,定义SQL操作符的种类
import org.apache.calcite.sql.type.SqlTypeFamily; // 导入SqlTypeFamily枚举,定义SQL类型家族
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举,定义SQL类型名称
import org.apache.calcite.util.Pair; // 导入Pair类,用于存储键值对

import com.fasterxml.jackson.core.JsonGenerator; // 导入JsonGenerator类,用于生成JSON格式的输出
import com.google.common.annotations.VisibleForTesting; // 导入VisibleForTesting注解,标记仅在测试中可见的成员
import com.google.common.collect.ImmutableList; // 导入ImmutableList类,提供不可变的列表实现
import com.google.common.collect.Iterables; // 导入Iterables类,提供对Iterable的实用工具方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解,标记可能为null的类型

import java.io.IOException; // 导入IOException类,处理输入输出异常
import java.text.SimpleDateFormat; // 导入SimpleDateFormat类,用于日期格式化
import java.util.ArrayList; // 导入ArrayList类,提供可变数组列表实现
import java.util.List; // 导入List接口,定义列表类型
import java.util.Locale; // 导入Locale类,提供本地化信息

import static org.apache.calcite.util.DateTimeStringUtils.ISO_DATETIME_FRACTIONAL_SECOND_FORMAT; // 导入ISO日期时间格式常量
import static org.apache.calcite.util.DateTimeStringUtils.getDateFormatter; // 导入获取日期格式化器的方法

import static java.util.Objects.requireNonNull; // 导入requireNonNull方法,用于检查对象非空

/**
 * Filter element of a Druid "groupBy" or "topN" query.
 */
// DruidJsonFilter是一个抽象类,实现了DruidJson接口
// 它的作用是将Calcite的RexNode表达式树转换为Druid查询中使用的JSON格式过滤器
// Druid是一个分布式实时分析数据库,支持groupBy、topN等查询操作
// 该类是Calcite适配器模式的一部分,负责将SQL查询中的WHERE条件转换为Druid原生的过滤条件
// 主要支持的过滤器类型包括:选择器(selector)、范围(bound)、IN操作、逻辑运算(AND/OR/NOT)、表达式过滤等
abstract class DruidJsonFilter implements DruidJson { // 抽象类定义,实现DruidJson接口以支持JSON序列化

  private static final ThreadLocal<SimpleDateFormat> DATE_FORMATTER = // 定义线程本地变量,用于存储日期格式化器
      ThreadLocal.withInitial(() -> getDateFormatter(ISO_DATETIME_FRACTIONAL_SECOND_FORMAT)); // 使用ThreadLocal保证线程安全,每个线程有自己的SimpleDateFormat实例
// 初始化时使用ISO日期时间格式(包含秒的小数部分),格式如:2026-01-10T12:34:56.789Z
// ThreadLocal的使用避免了SimpleDateFormat在多线程环境下的并发问题

  /**
   * Converts a {@link RexNode} to a Druid JSON filter.
   *
   * @param rexNode    RexNode to translate to Druid Json Filter
   * @param rowType    Row type associated to rexNode
   * @param druidQuery Druid query
   *
   * @return Druid JSON filter, or null if it cannot translate
   */
// 将RexNode表达式转换为Druid JSON过滤器,专门处理等值比较(= 和 !=)操作
// 该方法是私有静态方法,只在类内部使用
// 参数说明:
//   rexNode: 要转换的关系表达式节点,必须是EQUALS或NOT_EQUALS类型
//   rowType: 关系表达式的行类型,包含字段信息
//   druidQuery: Druid查询上下文,包含查询相关的元数据
// 返回值: 转换后的DruidJsonFilter对象,如果无法转换则返回null
private static @Nullable DruidJsonFilter toEqualityKindDruidFilter(RexNode rexNode,
      RelDataType rowType, DruidQuery druidQuery) { // 方法签名,返回可为null的DruidJsonFilter
    if (rexNode.getKind() != SqlKind.EQUALS // 检查表达式是否为等值操作
        && rexNode.getKind() != SqlKind.NOT_EQUALS) { // 检查表达式是否为不等值操作
      throw new AssertionError( // 如果既不是等值也不是不等值,抛出断言错误
          DruidQuery.format("Expecting EQUALS or NOT_EQUALS but got [%s]", rexNode.getKind())); // 格式化错误消息
    }
    final RexCall rexCall = (RexCall) rexNode; // 将RexNode强制转换为RexCall类型,因为等值操作是函数调用
    if (rexCall.getOperands().size() < 2) { // 检查操作数个数是否至少为2个
      return null; // 如果操作数不足,返回null表示无法转换
    }
    final RexLiteral rexLiteral; // 声明字面量变量,用于存储操作数中的字面量
    final RexNode refNode; // 声明引用节点变量,用于存储操作数中的列引用
    final RexNode lhs = rexCall.getOperands().get(0); // 获取左操作数
    final RexNode rhs = rexCall.getOperands().get(1); // 获取右操作数
    if (lhs.getKind() == SqlKind.LITERAL && rhs.getKind() != SqlKind.LITERAL) { // 判断左操作数是否为字面量且右操作数不是字面量
      rexLiteral = (RexLiteral) lhs; // 如果是,则左操作数是字面量
      refNode = rhs; // 右操作数是列引用
    } else if (rhs.getKind() == SqlKind.LITERAL && lhs.getKind() != SqlKind.LITERAL) { // 判断右操作数是否为字面量且左操作数不是字面量
      rexLiteral = (RexLiteral) rhs; // 如果是,则右操作数是字面量
      refNode = lhs; // 左操作数是列引用
    } else { // 如果两个操作数都是字面量或都不是字面量
      // must have at least one literal // 必须至少有一个字面量
      return null; // 返回null表示无法转换
    }

    if (RexLiteral.isNullLiteral(rexLiteral)) { // 检查字面量是否为NULL
      // we are not handling is NULL filter here thus we bail out if Literal is null // 这里不处理IS NULL过滤器,所以如果字面量为null则退出
      return null; // 返回null表示无法转换
    }
    final String literalValue = toDruidLiteral(rexLiteral, rowType, druidQuery); // 将Calcite字面量转换为Druid字面量字符串
    if (literalValue == null) { // 检查转换后的字面量是否为null
      // cannot translate literal; better bail out // 无法转换字面量,最好退出
      return null; // 返回null表示无法转换
    }
    final boolean isNumeric = refNode.getType().getFamily() == SqlTypeFamily.NUMERIC // 判断列引用类型是否为数值类型
        || rexLiteral.getType().getFamily() == SqlTypeFamily.NUMERIC; // 或者字面量类型是否为数值类型
    final Pair<String, ExtractionFunction> druidColumn = // 将Calcite列引用转换为Druid列
        DruidQuery.toDruidColumn(refNode, rowType, druidQuery); // 调用DruidQuery的静态方法进行转换
    final String columnName = druidColumn.left; // 获取Druid列名
    final ExtractionFunction extractionFunction = druidColumn.right; // 获取提取函数(可能为null)
    if (columnName == null) { // 检查列名是否为null
      // no column name better bail out. // 没有列名最好退出
      return null; // 返回null表示无法转换
    }
    final DruidJsonFilter partialFilter; // 声明部分过滤器变量
    if (isNumeric) { // 如果是数值类型
      // need bound filter since it one of operands is numeric // 需要使用边界过滤器,因为其中一个操作数是数值类型
      partialFilter = // 创建JsonBound过滤器
          new JsonBound(columnName, literalValue, false, literalValue, // 参数:列名,下界值,下界是否严格,上界值
              false, true, extractionFunction); // 上界是否严格,是否按数值比较,提取函数
    } else { // 如果不是数值类型
      partialFilter = new JsonSelector(columnName, literalValue, extractionFunction); // 创建JsonSelector选择器过滤器
    }

    if (rexNode.getKind() == SqlKind.EQUALS) { // 如果原始表达式是等值操作
      return partialFilter; // 直接返回部分过滤器
    }
    return toNotDruidFilter(partialFilter); // 如果是不等值操作,则对部分过滤器取反
  }


  /**
   * Converts a {@link RexNode} to a Druid JSON bound filter.
   *
   * @param rexNode    RexNode to translate
   * @param rowType    Row type associated to Filter
   * @param druidQuery Druid query
   *
   * @return valid Druid JSON Bound Filter, or null if it cannot translate the
   * RexNode
   */
// 将RexNode表达式转换为Druid JSON边界过滤器,专门处理范围比较操作(<, <=, >, >=)
// 边界过滤器用于表示数值或字符串的范围条件
// 参数说明:
//   rexNode: 要转换的关系表达式节点,必须是LESS_THAN、LESS_THAN_OR_EQUAL、GREATER_THAN或GREATER_THAN_OR_EQUAL类型
//   rowType: 关系表达式的行类型
//   druidQuery: Druid查询上下文
// 返回值: 转换后的JsonBound对象,如果无法转换则返回null
private static @Nullable DruidJsonFilter toBoundDruidFilter(RexNode rexNode, RelDataType rowType,
      DruidQuery druidQuery) { // 方法签名
    final RexCall rexCall = (RexCall) rexNode; // 将RexNode转换为RexCall类型
    final RexLiteral rexLiteral; // 声明字面量变量
    if (rexCall.getOperands().size() < 2) { // 检查操作数个数
      return null; // 操作数不足则返回null
    }
    final RexNode refNode; // 声明引用节点变量
    final RexNode lhs = rexCall.getOperands().get(0); // 获取左操作数
    final RexNode rhs = rexCall.getOperands().get(1); // 获取右操作数
    final boolean lhsIsRef; // 声明标志位,表示左操作数是否为列引用
    if (lhs.getKind() == SqlKind.LITERAL && rhs.getKind() != SqlKind.LITERAL) { // 左操作数是字面量,右操作数不是
      rexLiteral = (RexLiteral) lhs; // 左操作数是字面量
      refNode = rhs; // 右操作数是列引用
      lhsIsRef = false; // 左操作数不是列引用
    } else if (rhs.getKind() == SqlKind.LITERAL && lhs.getKind() != SqlKind.LITERAL) { // 右操作数是字面量,左操作数不是
      rexLiteral = (RexLiteral) rhs; // 右操作数是字面量
      refNode = lhs; // 左操作数是列引用
      lhsIsRef = true; // 左操作数是列引用
    } else { // 其他情况
      // must have at least one literal // 必须至少有一个字面量
      return null; // 返回null
    }

    if (RexLiteral.isNullLiteral(rexLiteral)) { // 检查字面量是否为NULL
      // we are not handling is NULL filter here; thus we bail out if Literal is null // 这里不处理IS NULL过滤器
      return null; // 返回null
    }
    final String literalValue = // 将Calcite字面量转换为Druid字面量字符串
        DruidJsonFilter.toDruidLiteral(rexLiteral, rowType, druidQuery); // 调用静态转换方法
    if (literalValue == null) { // 检查转换结果
      // cannot translate literal; better bail out // 无法转换字面量
      return null; // 返回null
    }
    final boolean isNumeric = refNode.getType().getFamily() == SqlTypeFamily.NUMERIC // 判断是否为数值类型
        || rexLiteral.getType().getFamily() == SqlTypeFamily.NUMERIC; // 检查字面量类型
    final Pair<String, ExtractionFunction> druidColumn = // 转换为Druid列
        DruidQuery.toDruidColumn(refNode, rowType, druidQuery); // 调用转换方法
    final String columnName = druidColumn.left; // 获取列名
    final ExtractionFunction extractionFunction = druidColumn.right; // 获取提取函数
    if (columnName == null) { // 检查列名
      // no column name better bail out. // 没有列名
      return null; // 返回null
    }
    switch (rexCall.getKind()) { // 根据操作符类型进行分支处理
    case LESS_THAN_OR_EQUAL: // 小于等于操作
    case LESS_THAN: // 小于操作
      if (lhsIsRef) { // 如果左操作数是列引用
        return new JsonBound(columnName, null, false, literalValue, // 创建边界过滤器:无下界,上界为字面量
            rexCall.getKind() == SqlKind.LESS_THAN, isNumeric, // 上界是否严格(小于为true,小于等于为false)
            extractionFunction); // 提取函数
      } else { // 如果左操作数是字面量
        return new JsonBound(columnName, literalValue, rexCall.getKind() == SqlKind.LESS_THAN, null, // 创建边界过滤器:下界为字面量,无上界
            false, isNumeric, // 下界是否严格,上界不严格
            extractionFunction); // 提取函数
      }
    case GREATER_THAN_OR_EQUAL: // 大于等于操作
    case GREATER_THAN: // 大于操作
      if (!lhsIsRef) { // 如果左操作数不是列引用(即右操作数是列引用)
        return new JsonBound(columnName, null, false, literalValue, // 创建边界过滤器:无下界,上界为字面量
            rexCall.getKind() == SqlKind.GREATER_THAN, isNumeric, // 上界是否严格(大于为true,大于等于为false)
            extractionFunction); // 提取函数
      } else { // 如果左操作数是列引用
        return new JsonBound(columnName, literalValue, rexCall.getKind() == SqlKind.GREATER_THAN, // 创建边界过滤器:下界为字面量,无上界
            null, // 上界为null
            false, isNumeric, // 下界是否严格,上界不严格
            extractionFunction); // 提取函数
      }
    default: // 其他操作符
      return null; // 返回null
    }

  }

  /**
   * Converts a {@link RexNode} to a Druid literal.
   *
   * @param rexNode    RexNode to translate to Druid literal equivalant
   * @param rowType    Row type associated to rexNode
   * @param druidQuery Druid query
   *
   * @return non null string, or null if it cannot translate to valid Druid
   * equivalent
   */
// 将Calcite的RexNode字面量转换为Druid可识别的字面量字符串
// 支持的字面量类型包括:数值类型、字符类型、时间戳类型、日期类型
// 参数说明:
//   rexNode: 要转换的RexNode,必须是RexLiteral类型
//   rowType: 行类型(当前未使用,保留用于未来扩展)
//   druidQuery: Druid查询上下文(当前未使用,保留用于未来扩展)
// 返回值: 转换后的字符串表示,如果无法转换则返回null
private static @Nullable String toDruidLiteral(RexNode rexNode,
      @SuppressWarnings("unused") RelDataType rowType, // 抑制未使用参数警告
      @SuppressWarnings("unused") DruidQuery druidQuery) { // 抑制未使用参数警告
    final String val; // 声明返回值变量
    final RexLiteral rhsLiteral = (RexLiteral) rexNode; // 将RexNode转换为RexLiteral类型
    if (SqlTypeName.NUMERIC_TYPES.contains(rhsLiteral.getTypeName())) { // 检查是否为数值类型
      val = String.valueOf(RexLiteral.value(rhsLiteral)); // 转换为字符串值
    } else if (SqlTypeName.CHAR_TYPES.contains(rhsLiteral.getTypeName())) { // 检查是否为字符类型
      val = String.valueOf(RexLiteral.stringValue(rhsLiteral)); // 转换为字符串值
    } else if (SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE == rhsLiteral.getTypeName() // 检查是否为带时区的时间戳
        || SqlTypeName.TIMESTAMP == rhsLiteral.getTypeName() // 检查是否为时间戳
        || SqlTypeName.DATE == rhsLiteral.getTypeName()) { // 检查是否为日期
      Long millisSinceEpoch = DruidDateTimeUtils.literalValue(rexNode); // 获取自纪元以来的毫秒数
      if (millisSinceEpoch == null) { // 检查是否获取成功
        throw new AssertionError( // 抛出断言错误
            "Cannot translate Literal" + rexNode + " of type " // 错误消息
                + rhsLiteral.getTypeName() + " to TimestampString"); // 包含类型信息
      }
      val = DATE_FORMATTER.get().format(millisSinceEpoch); // 使用线程本地日期格式化器格式化为ISO格式字符串
    } else { // 其他类型
      // Don't know how to filter on this kind of literal. // 不知道如何过滤这种类型的字面量
      val = null; // 返回null
    }
    return val; // 返回转换后的字符串
  }

  private static @Nullable DruidJsonFilter toIsNullKindDruidFilter(RexNode rexNode, // 将RexNode转换为Druid IS NULL或IS NOT NULL过滤器
      RelDataType rowType, DruidQuery druidQuery) { // 参数:表达式节点,行类型,Druid查询上下文
    if (rexNode.getKind() != SqlKind.IS_NULL && rexNode.getKind() != SqlKind.IS_NOT_NULL) { // 检查是否为IS NULL或IS NOT NULL操作
      throw new AssertionError( // 如果不是,抛出断言错误
          DruidQuery.format("Expecting IS_NULL or IS_NOT_NULL but got [%s]", rexNode.getKind())); // 格式化错误消息
    }
    final RexCall rexCall = (RexCall) rexNode; // 转换为RexCall类型
    final RexNode refNode = rexCall.getOperands().get(0); // 获取操作数(列引用)
    Pair<String, ExtractionFunction> druidColumn = DruidQuery // 转换为Druid列
        .toDruidColumn(refNode, rowType, druidQuery); // 调用转换方法
    final String columnName = druidColumn.left; // 获取列名
    final ExtractionFunction extractionFunction = druidColumn.right; // 获取提取函数
    if (columnName == null) { // 检查列名是否为null
      return null; // 返回null
    }
    if (rexNode.getKind() == SqlKind.IS_NOT_NULL) { // 如果是IS NOT NULL操作
      return toNotDruidFilter(new JsonSelector(columnName, null, extractionFunction)); // 创建选择器过滤器并取反
    }
    return new JsonSelector(columnName, null, extractionFunction); // 创建选择器过滤器,value为null表示匹配NULL值
  }

  private static @Nullable DruidJsonFilter toInKindDruidFilter(RexNode e, RelDataType rowType, // 将RexNode转换为Druid IN或NOT IN过滤器
      DruidQuery druidQuery) { // 参数:表达式节点,行类型,Druid查询上下文
    switch (e.getKind()) { // 根据操作符类型分支
    case DRUID_IN: // Druid的IN操作
    case DRUID_NOT_IN: // Druid的NOT IN操作
      break; // 继续执行
    default: // 其他操作符
      throw new AssertionError( // 抛出断言错误
          DruidQuery.format("Expecting IN or NOT IN but got [%s]", e.getKind())); // 格式化错误消息
    }

    ImmutableList.Builder<String> listBuilder = ImmutableList.builder(); // 创建不可变列表构建器
    for (RexNode rexNode : ((RexCall) e).getOperands()) { // 遍历所有操作数
      if (rexNode.getKind() == SqlKind.LITERAL) { // 如果是字面量
        String value = toDruidLiteral(rexNode, rowType, druidQuery); // 转换为Druid字面量字符串
        if (value == null) { // 检查转换结果
          return null; // 返回null
        }
        listBuilder.add(value); // 添加到列表
      }
    }
    Pair<String, ExtractionFunction> druidColumn = DruidQuery // 转换为Druid列
        .toDruidColumn(((RexCall) e).getOperands().get(0), // 使用第一个操作数作为列引用
        rowType, druidQuery); // 调用转换方法
    final String columnName = druidColumn.left; // 获取列名
    final ExtractionFunction extractionFunction = druidColumn.right; // 获取提取函数
    if (columnName == null) { // 检查列名
      return null; // 返回null
    }
    if (e.getKind() != SqlKind.DRUID_NOT_IN) { // 如果是IN操作
      return new DruidJsonFilter.JsonInFilter(columnName, listBuilder.build(), extractionFunction); // 创建IN过滤器
    } else { // 如果是NOT IN操作
      return toNotDruidFilter( // 对IN过滤器取反
          new DruidJsonFilter.JsonInFilter(columnName, listBuilder.build(), extractionFunction)); // 创建IN过滤器
    }
  }

  protected static @Nullable DruidJsonFilter toNotDruidFilter(DruidJsonFilter druidJsonFilter) { // 创建Druid NOT过滤器,对给定的过滤器取反
    if (druidJsonFilter == null) { // 检查输入过滤器是否为null
      return null; // 返回null
    }
    return new JsonCompositeFilter(Type.NOT, druidJsonFilter); // 创建复合过滤器,类型为NOT
  }

  private static @Nullable DruidJsonFilter toBetweenDruidFilter(RexNode rexNode, // 将RexNode转换为Druid BETWEEN过滤器
      RelDataType rowType, DruidQuery query) { // 参数:表达式节点,行类型,Druid查询上下文
    if (rexNode.getKind() != SqlKind.BETWEEN) { // 检查是否为BETWEEN操作
      return null; // 返回null
    }
    final RexCall rexCall = (RexCall) rexNode; // 转换为RexCall类型
    if (rexCall.getOperands().size() < 4) { // 检查操作数个数(BETWEEN需要4个操作数)
      return null; // 返回null
    }
    // BETWEEN (ASYMMETRIC, REF, 'lower-bound', 'upper-bound') // BETWEEN操作数的结构说明
    final RexNode refNode = rexCall.getOperands().get(1); // 获取列引用(第二个操作数)
    final RexNode lhs = rexCall.getOperands().get(2); // 获取下界值(第三个操作数)
    final RexNode rhs = rexCall.getOperands().get(3); // 获取上界值(第四个操作数)

    final String lhsLiteralValue = toDruidLiteral(lhs, rowType, query); // 转换下界值为Druid字面量
    final String rhsLiteralValue = toDruidLiteral(rhs, rowType, query); // 转换上界值为Druid字面量
    if (lhsLiteralValue == null || rhsLiteralValue == null) { // 检查转换结果
      return null; // 返回null
    }
    final boolean isNumeric = lhs.getType().getFamily() == SqlTypeFamily.NUMERIC // 判断是否为数值类型
        || rhs.getType().getFamily() == SqlTypeFamily.NUMERIC; // 检查边界值类型
    final Pair<String, ExtractionFunction> druidColumn = DruidQuery // 转换为Druid列
        .toDruidColumn(refNode, rowType, query); // 调用转换方法
    final String columnName = druidColumn.left; // 获取列名
    final ExtractionFunction extractionFunction = druidColumn.right; // 获取提取函数

    if (columnName == null) { // 检查列名
      return null; // 返回null
    }
    return new JsonBound(columnName, lhsLiteralValue, false, rhsLiteralValue, // 创建边界过滤器:下界和上界都不严格
        false, isNumeric, // 上界不严格,是否按数值比较
        extractionFunction); // 提取函数

  }

  private static @Nullable DruidJsonFilter toSimpleDruidFilter(RexNode e, RelDataType rowType, // 将简单的RexNode转换为Druid过滤器
      DruidQuery druidQuery) { // 参数:表达式节点,行类型,Druid查询上下文
    switch (e.getKind()) { // 根据操作符类型分支
    case EQUALS: // 等值操作
    case NOT_EQUALS: // 不等值操作
      return toEqualityKindDruidFilter(e, rowType, druidQuery); // 调用等值过滤器转换方法
    case GREATER_THAN: // 大于操作
    case GREATER_THAN_OR_EQUAL: // 大于等于操作
    case LESS_THAN: // 小于操作
    case LESS_THAN_OR_EQUAL: // 小于等于操作
      return toBoundDruidFilter(e, rowType, druidQuery); // 调用边界过滤器转换方法
    case BETWEEN: // BETWEEN操作
      return toBetweenDruidFilter(e, rowType, druidQuery); // 调用BETWEEN过滤器转换方法
    case DRUID_IN: // Druid的IN操作
    case DRUID_NOT_IN: // Druid的NOT IN操作
      return toInKindDruidFilter(e, rowType, druidQuery); // 调用IN过滤器转换方法
    case IS_NULL: // IS NULL操作
    case IS_NOT_NULL: // IS NOT NULL操作
      return toIsNullKindDruidFilter(e, rowType, druidQuery); // 调用IS NULL过滤器转换方法
    default: // 其他操作符
      return null; // 返回null
    }
  }

  /**
   * Converts a {@link RexNode} to a Druid filter.
   *
   * @param rexNode    RexNode to translate to Druid Filter
   * @param rowType    Row type of filter input
   * @param druidQuery Druid query
   * @param rexBuilder Rex builder
   *
   * @return Druid Json filters, or null when cannot translate to valid Druid
   * filters
   */
// 将Calcite的RexNode表达式树转换为Druid过滤器,这是过滤器转换的入口方法
// 该方法会处理各种复杂的表达式,包括逻辑运算、布尔判断、简单比较等
// 支持的操作包括:AND、OR、NOT、IS_TRUE、IS_FALSE、IS_NOT_TRUE、IS_NOT_FALSE等
// 参数说明:
//   rexNode: 要转换的关系表达式节点
//   rowType: 过滤器输入的行类型
//   druidQuery: Druid查询上下文
//   rexBuilder: Rex表达式构建器,用于展开搜索条件
// 返回值: 转换后的DruidJsonFilter对象,如果无法转换则返回null
static @Nullable DruidJsonFilter toDruidFilters(RexNode rexNode, RelDataType rowType,
      DruidQuery druidQuery, RexBuilder rexBuilder) { // 方法签名
    rexNode = RexUtil.expandSearch(rexBuilder, null, rexNode); // 展开搜索条件(如IN操作转换为OR操作)
    if (rexNode.isAlwaysTrue()) { // 检查表达式是否永远为真
      return JsonExpressionFilter.alwaysTrue(); // 返回总是为真的表达式过滤器
    }
    if (rexNode.isAlwaysFalse()) { // 检查表达式是否永远为假
      return JsonExpressionFilter.alwaysFalse(); // 返回总是为假的表达式过滤器
    }
    switch (rexNode.getKind()) { // 根据操作符类型分支
    case IS_TRUE: // IS TRUE操作
    case IS_NOT_FALSE: // IS NOT FALSE操作
      return toDruidFilters( // 递归转换操作数
          Iterables.getOnlyElement(((RexCall) rexNode).getOperands()), rowType, // 获取唯一的操作数
          druidQuery, rexBuilder); // 传递参数
    case IS_NOT_TRUE: // IS NOT TRUE操作
    case IS_FALSE: // IS FALSE操作
      final DruidJsonFilter simpleFilter = // 递归转换操作数
          toDruidFilters( // 调用转换方法
              Iterables.getOnlyElement(((RexCall) rexNode).getOperands()), // 获取唯一的操作数
              rowType, druidQuery, rexBuilder); // 传递参数
      return simpleFilter != null ? new JsonCompositeFilter(Type.NOT, simpleFilter) // 如果转换成功,创建NOT过滤器
          : simpleFilter; // 否则返回null
    case AND: // AND操作
    case OR: // OR操作
    case NOT: // NOT操作
      final RexCall call = (RexCall) rexNode; // 转换为RexCall类型
      final List<DruidJsonFilter> jsonFilters = new ArrayList<>(); // 创建过滤器列表
      for (final RexNode e : call.getOperands()) { // 遍历所有操作数
        final DruidJsonFilter druidFilter = // 递归转换每个操作数
            toDruidFilters(e, rowType, druidQuery, rexBuilder); // 调用转换方法
        if (druidFilter == null) { // 检查转换结果
          return null; // 返回null
        }
        jsonFilters.add(druidFilter); // 添加到列表
      }
      return new JsonCompositeFilter(Type.valueOf(rexNode.getKind().name()), // 创建复合过滤器
          jsonFilters); // 传递过滤器列表
    default: // 其他操作符
      break; // 跳出switch
    }

    final DruidJsonFilter simpleLeafFilter = toSimpleDruidFilter(rexNode, rowType, druidQuery); // 尝试转换为简单过滤器
    return simpleLeafFilter == null // 如果简单过滤器转换失败
        ? toDruidExpressionFilter(rexNode, rowType, druidQuery) // 则尝试转换为表达式过滤器
        : simpleLeafFilter; // 否则返回简单过滤器
  }

  private static @Nullable DruidJsonFilter toDruidExpressionFilter(RexNode rexNode, // 将RexNode转换为Druid表达式过滤器
      RelDataType rowType, DruidQuery query) { // 参数:表达式节点,行类型,Druid查询上下文
    final String expression = DruidExpressions.toDruidExpression(rexNode, rowType, query); // 调用DruidExpressions工具类转换表达式
    return expression == null ? null : new JsonExpressionFilter(expression); // 如果转换成功则创建表达式过滤器,否则返回null
  }

  /** Supported filter types. */
// 定义Druid支持的过滤器类型枚举
// 这个枚举列出了所有可以在Druid中使用的过滤器类型
// 每个类型对应一种特定的过滤条件或逻辑运算
protected enum Type { // 受保护的枚举类型,子类可以访问
    AND, // 逻辑与操作,表示所有子条件都必须满足
    OR, // 逻辑或操作,表示至少一个子条件满足即可
    NOT, // 逻辑非操作,表示对子条件取反
    SELECTOR, // 选择器过滤器,用于精确匹配列值
    IN, // IN操作过滤器,用于检查列值是否在给定列表中
    BOUND, // 边界过滤器,用于表示数值或字符串的范围条件
    EXPRESSION; // 表达式过滤器,用于支持任意的Druid表达式

    public String lowercase() { // 将枚举名称转换为小写字符串
      return name().toLowerCase(Locale.ROOT); // 使用ROOT语言环境确保一致性
    }
  }

  protected final Type type; // 过滤器类型,存储当前过滤器的类型(AND/OR/NOT/SELECTOR/IN/BOUND/EXPRESSION)

  private DruidJsonFilter(Type type) { // 私有构造方法,用于初始化DruidJsonFilter对象
    this.type = type; // 设置过滤器类型
  }

  /**
   * Druid Expression filter.
   */
// Druid表达式过滤器,用于支持任意的Druid表达式作为过滤条件
// 当其他类型的过滤器无法满足需求时,可以使用表达式过滤器
// 表达式过滤器允许使用Druid支持的表达式语法来定义过滤逻辑
public static class JsonExpressionFilter extends DruidJsonFilter { // 公共静态内部类,继承自DruidJsonFilter
    private final String expression; // 存储Druid表达式字符串

    JsonExpressionFilter(String expression) { // 构造方法
      super(Type.EXPRESSION); // 调用父类构造方法,设置类型为EXPRESSION
      this.expression = requireNonNull(expression, "expression"); // 设置表达式,检查非空
    }

    @Override public void write(JsonGenerator generator) throws IOException { // 重写write方法,将过滤器写入JSON生成器
      generator.writeStartObject(); // 开始写入JSON对象
      generator.writeStringField("type", type.lowercase()); // 写入type字段,值为小写的类型名
      generator.writeStringField("expression", expression); // 写入expression字段,值为表达式字符串
      generator.writeEndObject(); // 结束JSON对象
    }

    /**
     * We need to push to Druid an expression that always evaluates to true.
     */
    // 创建一个总是为真的表达式过滤器,用于表示无过滤条件
    private static JsonExpressionFilter alwaysTrue() { // 私有静态方法
      return new JsonExpressionFilter("1 == 1"); // 返回表达式"1 == 1",这是一个永远为真的条件
    }

    /**
     * We need to push to Druid an expression that always evaluates to false.
     */
    // 创建一个总是为假的表达式过滤器,用于表示永远不匹配任何记录
    private static JsonExpressionFilter alwaysFalse() { // 私有静态方法
      return new JsonExpressionFilter("1 == 2"); // 返回表达式"1 == 2",这是一个永远为假的条件
    }
  }

  /**
   * Equality filter.
   */
// 等值过滤器,用于精确匹配列值
// 这是Druid中最基本的过滤器类型,用于过滤出列值等于指定值的记录
// 也可以用于IS NULL操作(当value为null时)
private static class JsonSelector extends DruidJsonFilter { // 私有静态内部类,继承自DruidJsonFilter
    private final String dimension; // 维度名称,即要过滤的列名
    private final String value; // 要匹配的值,如果为null则表示匹配NULL值
    private final ExtractionFunction extractionFunction; // 提取函数,用于在匹配前对列值进行转换

    private JsonSelector(String dimension, String value, // 构造方法
        ExtractionFunction extractionFunction) { // 参数:维度名,匹配值,提取函数
      super(Type.SELECTOR); // 调用父类构造方法,设置类型为SELECTOR
      this.dimension = dimension; // 设置维度名
      this.value = value; // 设置匹配值
      this.extractionFunction = extractionFunction; // 设置提取函数
    }

    @Override public void write(JsonGenerator generator) throws IOException { // 重写write方法,将过滤器写入JSON生成器
      generator.writeStartObject(); // 开始写入JSON对象
      generator.writeStringField("type", type.lowercase()); // 写入type字段,值为小写的类型名
      generator.writeStringField("dimension", dimension); // 写入dimension字段,值为维度名
      generator.writeStringField("value", value); // 写入value字段,值为匹配值
      DruidQuery.writeFieldIf(generator, "extractionFn", extractionFunction); // 如果提取函数不为null,则写入extractionFn字段
      generator.writeEndObject(); // 结束JSON对象
    }
  }

  /**
   * Bound filter.
   */
// 边界过滤器,用于表示数值或字符串的范围条件
// 支持大于、小于、大于等于、小于等于等操作,以及BETWEEN操作
// 边界过滤器可以指定下界(lower)和/或上界(upper),以及边界是否严格(是否包含边界值)
@VisibleForTesting // 该注解表示该类在测试中可见
protected static class JsonBound extends DruidJsonFilter { // 受保护的静态内部类,继承自DruidJsonFilter
    private final String dimension; // 维度名称,即要过滤的列名
    private final @Nullable String lower; // 下界值,可以为null表示无下界
    private final boolean lowerStrict; // 下界是否严格,true表示不包含下界值(>),false表示包含下界值(>=)
    private final @Nullable String upper; // 上界值,可以为null表示无上界
    private final boolean upperStrict; // 上界是否严格,true表示不包含上界值(<),false表示包含上界值(<=)
    private final boolean alphaNumeric; // 是否按数值比较,true表示数值比较,false表示字典序比较
    private final ExtractionFunction extractionFunction; // 提取函数,用于在比较前对列值进行转换

    protected JsonBound(String dimension, @Nullable String lower, // 构造方法
        boolean lowerStrict, @Nullable String upper, boolean upperStrict, // 参数:维度名,下界值,下界是否严格,上界值,上界是否严格
        boolean alphaNumeric, ExtractionFunction extractionFunction) { // 参数:是否数值比较,提取函数
      super(Type.BOUND); // 调用父类构造方法,设置类型为BOUND
      this.dimension = dimension; // 设置维度名
      this.lower = lower; // 设置下界值
      this.lowerStrict = lowerStrict; // 设置下界是否严格
      this.upper = upper; // 设置上界值
      this.upperStrict = upperStrict; // 设置上界是否严格
      this.alphaNumeric = alphaNumeric; // 设置是否数值比较
      this.extractionFunction = extractionFunction; // 设置提取函数
    }

    @Override public void write(JsonGenerator generator) throws IOException { // 重写write方法,将过滤器写入JSON生成器
      generator.writeStartObject(); // 开始写入JSON对象
      generator.writeStringField("type", type.lowercase()); // 写入type字段,值为小写的类型名
      generator.writeStringField("dimension", dimension); // 写入dimension字段,值为维度名
      if (lower != null) { // 如果下界值不为null
        generator.writeStringField("lower", lower); // 写入lower字段,值为下界值
        generator.writeBooleanField("lowerStrict", lowerStrict); // 写入lowerStrict字段,值为下界是否严格
      }
      if (upper != null) { // 如果上界值不为null
        generator.writeStringField("upper", upper); // 写入upper字段,值为上界值
        generator.writeBooleanField("upperStrict", upperStrict); // 写入upperStrict字段,值为上界是否严格
      }
      if (alphaNumeric) { // 如果按数值比较
        generator.writeStringField("ordering", "numeric"); // 写入ordering字段,值为"numeric"
      } else { // 如果按字典序比较
        generator.writeStringField("ordering", "lexicographic"); // 写入ordering字段,值为"lexicographic"
      }
      DruidQuery.writeFieldIf(generator, "extractionFn", extractionFunction); // 如果提取函数不为null,则写入extractionFn字段
      generator.writeEndObject(); // 结束JSON对象
    }
  }

  /**
   * Filter that combines other filters using a boolean operator.
   */
// 复合过滤器,使用布尔运算符组合其他过滤器
// 支持AND、OR、NOT三种逻辑运算,用于构建复杂的过滤条件
// AND表示所有子过滤器都必须满足,OR表示至少一个子过滤器满足,NOT表示对子过滤器取反
private static class JsonCompositeFilter extends DruidJsonFilter { // 私有静态内部类,继承自DruidJsonFilter
    private final List<? extends DruidJsonFilter> fields; // 子过滤器列表

    private JsonCompositeFilter(Type type, // 构造方法,接收可迭代的过滤器集合
        Iterable<? extends DruidJsonFilter> fields) { // 参数:类型,可迭代的过滤器集合
      super(type); // 调用父类构造方法,设置类型
      this.fields = ImmutableList.copyOf(fields); // 创建不可变列表的副本
    }

    private JsonCompositeFilter(Type type, DruidJsonFilter... fields) { // 构造方法,接收可变参数的过滤器数组
      this(type, ImmutableList.copyOf(fields)); // 调用另一个构造方法,将数组转换为不可变列表
    }

    @Override public void write(JsonGenerator generator) throws IOException { // 重写write方法,将过滤器写入JSON生成器
      generator.writeStartObject(); // 开始写入JSON对象
      generator.writeStringField("type", type.lowercase()); // 写入type字段,值为小写的类型名
      switch (type) { // 根据类型分支
      case NOT: // 如果是NOT操作
        DruidQuery.writeField(generator, "field", fields.get(0)); // 写入field字段,值为第一个过滤器
        break; // 跳出switch
      default: // 其他类型(AND或OR)
        DruidQuery.writeField(generator, "fields", fields); // 写入fields字段,值为过滤器列表
      }
      generator.writeEndObject(); // 结束JSON对象
    }
  }

  /**
   * IN filter.
   */
// IN过滤器,用于检查列值是否在给定的值列表中
// 相当于多个OR条件的简写形式,提高查询效率和可读性
// NOT IN操作可以通过对IN过滤器取反来实现
protected static class JsonInFilter extends DruidJsonFilter { // 受保护的静态内部类,继承自DruidJsonFilter
    private final String dimension; // 维度名称,即要过滤的列名
    private final List<String> values; // 值列表,包含所有可能的匹配值
    private final ExtractionFunction extractionFunction; // 提取函数,用于在匹配前对列值进行转换

    protected JsonInFilter(String dimension, List<String> values, // 构造方法
        ExtractionFunction extractionFunction) { // 参数:维度名,值列表,提取函数
      super(Type.IN); // 调用父类构造方法,设置类型为IN
      this.dimension = dimension; // 设置维度名
      this.values = values; // 设置值列表
      this.extractionFunction = extractionFunction; // 设置提取函数
    }

    @Override public void write(JsonGenerator generator) throws IOException { // 重写write方法,将过滤器写入JSON生成器
      generator.writeStartObject(); // 开始写入JSON对象
      generator.writeStringField("type", type.lowercase()); // 写入type字段,值为小写的类型名
      generator.writeStringField("dimension", dimension); // 写入dimension字段,值为维度名
      DruidQuery.writeField(generator, "values", values); // 写入values字段,值为值列表
      DruidQuery.writeFieldIf(generator, "extractionFn", extractionFunction); // 如果提取函数不为null,则写入extractionFn字段
      generator.writeEndObject(); // 结束JSON对象
    }
  }

  public static DruidJsonFilter getSelectorFilter(String column, String value, // 公共静态方法,创建选择器过滤器
      ExtractionFunction extractionFunction) { // 参数:列名,匹配值,提取函数
    requireNonNull(column, "column"); // 检查列名是否为null
    return new JsonSelector(column, value, extractionFunction); // 创建并返回JsonSelector对象
  }

  /** Druid Having Filter spec. */
// Druid Having过滤器规范,用于在GROUP BY查询后对聚合结果进行过滤
// Having子句与Where子句的区别在于,Where子句在聚合前过滤原始数据,
// 而Having子句在聚合后过滤聚合结果
protected static class JsonDimHavingFilter implements DruidJson { // 受保护的静态内部类,实现DruidJson接口

    private final DruidJsonFilter filter; // 内部的过滤器对象

    public JsonDimHavingFilter(DruidJsonFilter filter) { // 构造方法
      this.filter = filter; // 设置内部过滤器
    }

    @Override public void write(JsonGenerator generator) throws IOException { // 实现write方法,将过滤器写入JSON生成器
      generator.writeStartObject(); // 开始写入JSON对象
      generator.writeStringField("type", "filter"); // 写入type字段,值为"filter"
      DruidQuery.writeField(generator, "filter", filter); // 写入filter字段,值为内部过滤器对象
      generator.writeEndObject(); // 结束JSON对象
    }
  }
}
