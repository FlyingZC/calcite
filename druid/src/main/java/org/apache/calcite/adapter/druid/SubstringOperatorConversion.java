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
// 声明包名，该类属于Apache Calcite的Druid适配器模块，用于将Calcite的SQL表达式转换为Druid表达式
package org.apache.calcite.adapter.druid;

// 导入RelDataType类，用于表示关系数据类型，描述表中字段的类型信息
import org.apache.calcite.rel.type.RelDataType;
// 导入RexCall类，表示Rex表达式中的函数调用节点，如SUBSTRING函数调用
import org.apache.calcite.rex.RexCall;
// 导入RexLiteral类，表示Rex表达式中的常量字面量，如数字、字符串等
import org.apache.calcite.rex.RexLiteral;
// 导入RexNode类，表示Rex表达式树的基类，所有表达式节点都继承自此类
import org.apache.calcite.rex.RexNode;
// 导入SqlKind枚举，定义了SQL操作符的种类，如LITERAL表示字面量
import org.apache.calcite.sql.SqlKind;
// 导入SqlOperator接口，表示SQL操作符，如SUBSTRING操作符
import org.apache.calcite.sql.SqlOperator;
// 导入SqlStdOperatorTable类，包含标准SQL操作符的常量表，如SUBSTRING操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable;

// 导入Nullable注解，用于标记可空的返回值或参数，来自CheckerFramework框架
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Converts Calcite SUBSTRING call to Druid Expression when possible.
 * // 子字符串操作符转换器类，负责将Calcite的SUBSTRING函数调用转换为Druid表达式
 * // 实现DruidSqlOperatorConverter接口，作为Druid适配器的一部分
 * // 该类处理SQL中的SUBSTRING函数，支持两种形式：
 * // 1. SUBSTRING(str, start) - 从指定位置截取到字符串末尾
 * // 2. SUBSTRING(str, start, length) - 从指定位置截取指定长度的子字符串
 * // 关键转换逻辑：SQL使用1-based索引（从1开始），而Druid使用0-based索引（从0开始），需要转换
 * // 如果无法转换（如表达式太复杂），则返回null表示转换失败
 */
public class SubstringOperatorConversion implements DruidSqlOperatorConverter {
  // 重写calciteOperator方法，返回该转换器处理的Calcite SQL操作符
  // 返回SqlStdOperatorTable.SUBSTRING表示该转换器专门处理SUBSTRING函数
  @Override public SqlOperator calciteOperator() {
    // 返回标准SQL的SUBSTRING操作符，用于匹配Calcite中的SUBSTRING函数调用
    return SqlStdOperatorTable.SUBSTRING;
  }

  // 重写toDruidExpression方法，将Calcite的SUBSTRING表达式转换为Druid表达式
  // 参数rexNode: 要转换的Rex表达式节点，类型为RexCall，表示SUBSTRING函数调用
  // 参数rowType: 行类型，包含当前查询中所有字段的类型信息
  // 参数query: DruidQuery对象，包含Druid查询的上下文信息
  // 返回值: 转换后的Druid表达式字符串，如果转换失败则返回null（使用@Nullable注解标记）
  @Override public @Nullable String toDruidExpression(RexNode rexNode,
      RelDataType rowType, DruidQuery query) {
    // 将rexNode强制转换为RexCall类型，因为SUBSTRING是一个函数调用
    // 在Calcite中，所有函数调用都表示为RexCall节点
    final RexCall call = (RexCall) rexNode;
    // 处理SUBSTRING函数的第一个参数：源字符串
    // 调用DruidExpressions.toDruidExpression方法将第一个操作数转换为Druid表达式
    // call.getOperands().get(0)获取SUBSTRING的第一个参数（要截取的字符串）
    // rowType提供字段类型信息，query提供查询上下文
    // arg变量存储转换后的Druid表达式，如果转换失败则为null
    final String arg =
        DruidExpressions.toDruidExpression(call.getOperands().get(0), rowType,
            query);
    // 检查第一个参数是否转换成功，如果转换失败则返回null表示无法转换整个表达式
    if (arg == null) {
      // 返回null表示转换失败，Druid适配器会使用其他方式处理该表达式
      return null;
    }

    // 声明startIndex和length变量，分别表示起始索引和截取长度的Druid表达式
    // 这两个变量将在后续逻辑中被赋值，用于构建最终的Druid substring表达式
    final String startIndex;
    final String length;
    // 重要注释：SQL使用1-based索引（从1开始计数），而Druid使用0-based索引（从0开始计数）
    // 因此需要将SQL的起始位置减1才能得到Druid的起始位置
    // 判断SUBSTRING的第二个参数（起始索引）是否为字面量常量
    if (!call.getOperands().get(1).isA(SqlKind.LITERAL)) {
      // 如果第二个参数不是字面量常量（例如是一个表达式或字段引用）
      // 则需要将该表达式转换为Druid表达式
      // call.getOperands().get(1)获取SUBSTRING的第二个参数（起始索引）
      // arg1变量存储转换后的Druid表达式
      final String arg1 =
          DruidExpressions.toDruidExpression(call.getOperands().get(1), rowType,
              query);
      // 检查第二个参数是否转换成功，如果转换失败则返回null
      if (arg1 == null) {
        // 无法推断起始索引表达式，转换失败，返回null
        return null;
      }
      // 由于SQL是1-based，Druid是0-based，需要将转换后的表达式减1
      // 使用DruidQuery.format方法格式化表达式，生成"(arg1 - 1)"形式的Druid表达式
      startIndex = DruidQuery.format("(%s - 1)", arg1);
    } else {
      // 如果第二个参数是字面量常量（如SUBSTRING(str, 3, 2)中的3）
      // 则直接获取该常量的整数值并减1，然后转换为Druid数字字面量
      // RexLiteral.intValue获取字面量的整数值
      // 减1是为了从SQL的1-based索引转换为Druid的0-based索引
      startIndex =
          DruidExpressions.numberLiteral(
              RexLiteral.intValue(call.getOperands().get(1)) - 1);
    }

    // 检查SUBSTRING函数是否有第三个参数（截取长度）
    // SUBSTRING函数有两种形式：SUBSTRING(str, start)和SUBSTRING(str, start, length)
    // 如果操作数数量大于2，说明有第三个参数（截取长度）
    if (call.getOperands().size() > 2) {
      // 处理带有长度参数的SUBSTRING函数：SUBSTRING(str, start, length)
      // 检查第三个参数（截取长度）是否为字面量常量
      if (!call.getOperands().get(2).isA(SqlKind.LITERAL)) {
        // 如果第三个参数不是字面量常量（例如是一个表达式）
        // 则需要将该表达式转换为Druid表达式
        // call.getOperands().get(2)获取SUBSTRING的第三个参数（截取长度）
        // length变量存储转换后的Druid表达式
        length =
            DruidExpressions.toDruidExpression(call.getOperands().get(2),
                rowType, query);
        // 检查第三个参数是否转换成功，如果转换失败则返回null
        if (length == null) {
          // 转换失败，返回null
          return null;
        }
      } else {
        // 如果第三个参数是字面量常量（如SUBSTRING(str, 1, 5)中的5）
        // 则直接获取该常量的整数值并转换为Druid数字字面量
        // 截取长度不需要进行索引转换，直接使用原值即可
        length =
            DruidExpressions.numberLiteral(
                RexLiteral.intValue(call.getOperands().get(2)));
      }

    } else {
      // 处理不带长度参数的SUBSTRING函数：SUBSTRING(str, start)
      // 表示从起始位置截取到字符串末尾
      // 在Druid中，使用-1表示截取到字符串末尾
      // 因此将length设置为-1的Druid数字字面量
      length = DruidExpressions.numberLiteral(-1);
    }
    // 构建最终的Druid substring表达式并返回
    // Druid的substring函数格式：substring(字符串, 起始索引, 截取长度)
    // 使用DruidQuery.format方法格式化表达式，将arg、startIndex、length三个参数插入模板
    // 例如：substring("hello", 0, 3)会返回"hel"
    return DruidQuery.format("substring(%s, %s, %s)", arg, startIndex, length);
  }
}
