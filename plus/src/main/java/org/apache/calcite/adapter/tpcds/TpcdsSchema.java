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
 */ // Apache许可证声明：说明代码遵循Apache 2.0许可证，允许自由使用和修改
package org.apache.calcite.adapter.tpcds; // 包声明：TPC-DS适配器包，提供TPC-DS基准测试的数据源支持

import org.apache.calcite.adapter.java.AbstractQueryableTable; // 导入：抽象可查询表基类，支持Java语言的LINQ查询
import org.apache.calcite.avatica.util.DateTimeUtils; // 导入：日期时间工具类，用于日期时间格式转换
import org.apache.calcite.linq4j.Enumerable; // 导入：可枚举接口，支持LINQ风格的集合操作
import org.apache.calcite.linq4j.Enumerator; // 导入：枚举器接口，用于遍历数据集合
import org.apache.calcite.linq4j.Linq4j; // 导入：LINQ4j工具类，提供LINQ操作支持
import org.apache.calcite.linq4j.QueryProvider; // 导入：查询提供者接口，用于创建和执行LINQ查询
import org.apache.calcite.linq4j.Queryable; // 导入：可查询接口，表示可执行LINQ查询的数据源
import org.apache.calcite.linq4j.function.Function1; // 导入：单参数函数接口，用于LINQ转换操作
import org.apache.calcite.rel.type.RelDataType; // 导入：关系数据类型接口，表示表或表达式的类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入：关系数据类型工厂接口，用于创建各种数据类型
import org.apache.calcite.schema.SchemaPlus; // 导入：模式增强接口，支持嵌套模式和额外元数据
import org.apache.calcite.schema.Statistic; // 导入：统计信息接口，提供表的行数和索引信息
import org.apache.calcite.schema.Statistics; // 导入：统计信息工具类，用于创建统计信息对象
import org.apache.calcite.schema.Table; // 导入：表接口，定义表的基本行为
import org.apache.calcite.schema.impl.AbstractSchema; // 导入：抽象模式基类，提供模式的基本实现
import org.apache.calcite.schema.impl.AbstractTableQueryable; // 导入：抽象表可查询类，提供表查询的通用实现
import org.apache.calcite.sql.type.SqlTypeName; // 导入：SQL类型名枚举，定义所有SQL标准数据类型
import org.apache.calcite.util.Bug; // 导入：Bug工具类，用于标记待修复或待改进的问题
import org.apache.calcite.util.Util; // 导入：通用工具类，提供各种辅助方法

import com.google.common.collect.ImmutableList; // 导入：Guava不可变列表类，提供线程安全的列表实现
import com.google.common.collect.ImmutableMap; // 导入：Guava不可变映射类，提供线程安全的映射实现
import com.teradata.tpcds.Results; // 导入：TPC-DS结果类，用于存储查询结果数据
import com.teradata.tpcds.Session; // 导入：TPC-DS会话类，管理查询会话和配置
import com.teradata.tpcds.column.Column; // 导入：TPC-DS列类，定义表的列结构
import com.teradata.tpcds.column.ColumnType; // 导入：TPC-DS列类型类，定义列的数据类型

import org.checkerframework.checker.nullness.qual.Nullable; // 导入：可空注解，标记可能为null的值

import java.math.BigDecimal; // 导入：BigDecimal类，用于高精度十进制运算
import java.util.ArrayList; // 导入：ArrayList动态数组类，用于存储可变长度的对象列表
import java.util.List; // 导入：List接口，定义有序集合的基本行为
import java.util.Locale; // 导入：Locale类，用于本地化和大小写转换
import java.util.Map; // 导入：Map接口，定义键值对映射的基本行为

import static java.util.Objects.requireNonNull; // 导入：静态导入requireNonNull方法，用于空值检查

/** Schema that provides TPC-DS tables, populated according to a
 * particular scale factor. */ // TPC-DS模式类：提供了根据指定缩放因子填充的TPC-DS基准测试表，用于数据库性能测试场景
public class TpcdsSchema extends AbstractSchema { // 继承AbstractSchema抽象类，实现Calcite模式的接口
  private final double scaleFactor; // 缩放因子：控制TPC-DS数据集的大小，例如1表示1GB数据，10表示10GB数据
  private final ImmutableMap<String, Table> tableMap; // 表映射：存储表名到Table对象的不可变映射，用于快速查找表

  // From TPC-DS spec, table 3-2 "Database Row Counts", for 1G sizing. // 根据TPC-DS规范表3-2"数据库行数"定义的1GB规模下各表的行数统计
  private static final ImmutableMap<String, Integer> TABLE_ROW_COUNTS = // 静态常量：存储TPC-DS各表在1GB规模下的行数，用于统计信息和查询优化
      ImmutableMap.<String, Integer>builder() // 使用Guava的ImmutableMap构建器创建不可变映射
          .put("CALL_CENTER", 8) // 客户中心表：8行记录
          .put("CATALOG_PAGE", 11718) // 目录页面表：11718行记录
          .put("CATALOG_RETURNS", 144067) // 目录退货表：144067行记录
          .put("CATALOG_SALES", 1441548) // 目录销售表：1441548行记录
          .put("CUSTOMER", 100000) // 客户表：100000行记录
          .put("CUSTOMER_ADDRESS", 50000) // 客户地址表：50000行记录
          .put("CUSTOMER_DEMOGRAPHICS", 1920800) // 客户人口统计表：1920800行记录
          .put("DATE_DIM", 73049) // 日期维度表：73049行记录（约200年的日期）
          .put("DBGEN_VERSION", 1) // 数据库生成版本表：1行记录
          .put("HOUSEHOLD_DEMOGRAPHICS", 7200) // 家庭人口统计表：7200行记录
          .put("INCOME_BAND", 20) // 收入区间表：20行记录（将收入分为20个等级）
          .put("INVENTORY", 11745000) // 库存表：11745000行记录
          .put("ITEM", 18000) // 商品表：18000行记录
          .put("PROMOTION", 300) // 促销活动表：300行记录
          .put("REASON", 35) // 退货原因表：35行记录
          .put("SHIP_MODE", 20) // 运输方式表：20行记录
          .put("STORE", 12) // 商店表：12行记录
          .put("STORE_RETURNS", 287514) // 商店退货表：287514行记录
          .put("STORE_SALES", 2880404) // 商店销售表：2880404行记录
          .put("TIME_DIM", 86400) // 时间维度表：86400行记录（24小时×60分钟×60秒）
          .put("WAREHOUSE", 5) // 仓库表：5行记录
          .put("WEB_PAGE", 60) // 网页表：60行记录
          .put("WEB_RETURNS", 71763) // 网页退货表：71763行记录
          .put("WEB_SALES", 719384) // 网页销售表：719384行记录
          .put("WEB_SITE", 1) // 网站表：1行记录
          .build(); // 构建不可变映射对象

  @Deprecated // 标记为已过时的注解，表示此构造方法不再推荐使用
  public TpcdsSchema(double scaleFactor, int part, int partCount) { // 已过时的构造方法：接受缩放因子、分区号和分区总数参数
    this(scaleFactor); // 调用单参数构造方法初始化scaleFactor
    Util.discard(part); // 丢弃part参数，表示不再支持分区功能
    Util.discard(partCount); // 丢弃partCount参数，表示不再支持分区功能
  }

  /** Creates a TpcdsSchema. */ // 构造方法说明：创建TPC-DS模式实例
  public TpcdsSchema(double scaleFactor) { // 主构造方法：接受缩放因子参数
    this.scaleFactor = scaleFactor; // 初始化缩放因子成员变量

    final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder(); // 创建不可变映射构建器，用于构建表名到Table对象的映射
    for (com.teradata.tpcds.Table tpcdsTable // 遍历TPC-DS所有基础表
        : com.teradata.tpcds.Table.getBaseTables()) { // 获取TPC-DS规范定义的所有基础表
      builder.put(tpcdsTable.name().toUpperCase(Locale.ROOT), // 将表名转为大写作为键（确保大小写不敏感）
          new TpcdsQueryableTable(tpcdsTable)); // 为每个TPC-DS表创建对应的TpcdsQueryableTable对象作为值
    }
    this.tableMap = builder.build(); // 构建不可变映射并赋值给tableMap成员变量
  }

  @Override protected Map<String, Table> getTableMap() { // 重写父类方法：返回包含所有表的映射，供Calcite查询引擎使用
    return tableMap; // 返回表名到Table对象的不可变映射
  }

  private static @Nullable Object convert(@Nullable String string, Column column) { // 静态工具方法：将字符串值转换为对应Java类型对象，根据列的类型进行类型转换
    if (string == null) { // 检查输入字符串是否为null
      return null; // 如果为null则直接返回null
    }
    switch (column.getType().getBase()) { // 根据列的基础类型进行分支处理
    case IDENTIFIER: // 标识符类型：通常是主键或外键
      return Long.valueOf(string); // 转换为Long类型（长整型）
    case INTEGER: // 整数类型
      return Integer.valueOf(string); // 转换为Integer类型（整型）
    case CHAR: // 固定长度字符类型
    case VARCHAR: // 可变长度字符类型
      return string; // 直接返回字符串，无需转换
    case DATE: // 日期类型
      return DateTimeUtils.dateStringToUnixDate(string); // 将日期字符串转换为Unix日期格式（整数天数）
    case TIME: // 时间类型
      return DateTimeUtils.timeStringToUnixDate(string); // 将时间字符串转换为Unix时间格式
    case DECIMAL: // 十进制类型（高精度数值）
      return new BigDecimal(string); // 转换为BigDecimal对象，保持精度
    default: // 未知类型
      throw new AssertionError(column); // 抛出断言错误，表示遇到了不支持的类型
    }
  }

  /** Definition of a table in the TPC-DS schema.
   *
   * @param <E> entity type */ // 内部类说明：定义TPC-DS模式中的表，支持LINQ查询
  private class TpcdsQueryableTable<E extends com.teradata.tpcds.Table> // 内部类：表示TPC-DS中可查询的表，继承AbstractQueryableTable实现查询功能
      extends AbstractQueryableTable { // 继承抽象可查询表类，提供LINQ查询支持
    private final com.teradata.tpcds.Table tpcdsTable; // 成员变量：引用TPC-DS库中的表对象，包含表的元数据和列定义

    TpcdsQueryableTable(com.teradata.tpcds.Table tpcdsTable) { // 构造方法：接受TPC-DS表对象作为参数
      super(Object[].class); // 调用父类构造方法，指定行类型为Object数组（每行数据存储为对象数组）
      this.tpcdsTable = tpcdsTable; // 保存TPC-DS表对象的引用
    }

    @Override public Statistic getStatistic() { // 重写方法：返回表的统计信息，用于查询优化器进行成本估算
      Bug.upgrade("add row count estimate to TpcdsTable, and use it"); // 标记待改进项：建议将行数估计添加到TpcdsTable中
      Integer rowCount = TABLE_ROW_COUNTS.get(tpcdsTable.name()); // 从静态映射中获取该表在1GB规模下的行数
      requireNonNull(rowCount, "table has null row count: " + tpcdsTable); // 确保行数不为null，否则抛出异常
      return Statistics.of(rowCount, ImmutableList.of()); // 创建统计信息对象，包含行数和空列表（无索引信息）
    }

    @Override public <T> Queryable<T> asQueryable(final QueryProvider queryProvider, // 重写方法：将表转换为可查询对象，支持LINQ查询操作
        final SchemaPlus schema, final String tableName) { // 参数：查询提供者、模式对象、表名
      //noinspection unchecked // 抑制未检查类型转换警告
      return (Queryable) new AbstractTableQueryable<@Nullable Object[]>(queryProvider, // 创建抽象表可查询对象，行类型为可空对象数组
          schema, this, tableName) { // 参数：查询提供者、模式、表对象、表名
        @Override public Enumerator<@Nullable Object[]> enumerator() { // 重写方法：返回枚举器，用于遍历表数据
          final Session session = // 创建TPC-DS会话对象
              Session.getDefaultSession() // 获取默认会话配置
                  .withTable(tpcdsTable) // 设置要查询的表
                  .withScale(scaleFactor); // 设置数据缩放因子
          final Results results = Results.constructResults(tpcdsTable, session); // 根据表和会话构造查询结果
          return Linq4j.asEnumerable(results) // 将结果转换为可枚举对象
              .selectMany( // 使用selectMany操作符展平结果集
                  new Function1<List<List<@Nullable String>>, Enumerable<@Nullable Object[]>>() { // 匿名函数：将字符串列表转换为对象数组列表
                    final Column[] columns = tpcdsTable.getColumns(); // 获取表的所有列定义

                    @Override public Enumerable<@Nullable Object[]> apply( // 重写apply方法：执行转换逻辑
                        List<List<@Nullable String>> inRows) { // 输入：字符串列表的列表（多行数据）
                      final List<@Nullable Object[]> rows = new ArrayList<>(); // 创建结果列表，存储转换后的对象数组
                      for (List<@Nullable String> strings : inRows) { // 遍历每一行数据
                        final @Nullable Object[] values = new Object[columns.length]; // 创建对象数组，长度等于列数
                        for (int i = 0; i < strings.size(); i++) { // 遍历每列数据
                          values[i] = convert(strings.get(i), columns[i]); // 将字符串值转换为对应Java类型
                        }
                        rows.add(values); // 将转换后的对象数组添加到结果列表
                      }
                      return Linq4j.asEnumerable(rows); // 将结果列表转换为可枚举对象返回
                    }

                  })
              .enumerator(); // 获取枚举器，用于遍历数据
        }
      };
    }

    @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写方法：返回表的行类型（结构定义），包含所有列的类型信息
      final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建类型工厂构建器，用于构建行类型
      for (Column column : tpcdsTable.getColumns()) { // 遍历表的所有列
        builder.add(column.getName().toUpperCase(Locale.ROOT), // 将列名转为大写并添加到构建器
            type(typeFactory, column)); // 调用type方法获取列的RelDataType类型
      }
      return builder.build(); // 构建并返回行类型对象
    }

    private RelDataType type(RelDataTypeFactory typeFactory, Column column) { // 私有方法：将TPC-DS列类型转换为Calcite的RelDataType类型
      final ColumnType type = column.getType(); // 获取列的类型信息
      switch (type.getBase()) { // 根据基础类型进行分支处理
      case DATE: // 日期类型
        return typeFactory.createSqlType(SqlTypeName.DATE); // 创建DATE类型的RelDataType
      case TIME: // 时间类型
        return typeFactory.createSqlType(SqlTypeName.TIME); // 创建TIME类型的RelDataType
      case INTEGER: // 整数类型
        return typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建INTEGER类型的RelDataType
      case IDENTIFIER: // 标识符类型
        return typeFactory.createSqlType(SqlTypeName.BIGINT); // 创建BIGINT类型的RelDataType（64位整数）
      case DECIMAL: // 十进制类型
        return typeFactory.createSqlType(SqlTypeName.DECIMAL, // 创建DECIMAL类型的RelDataType
            type.getPrecision().get(), type.getScale().get()); // 指定精度和小数位数
      case VARCHAR: // 可变长度字符串类型
        return typeFactory.createSqlType(SqlTypeName.VARCHAR, // 创建VARCHAR类型的RelDataType
            type.getPrecision().get()); // 指定最大长度
      case CHAR: // 固定长度字符串类型
        return typeFactory.createSqlType(SqlTypeName.CHAR, // 创建CHAR类型的RelDataType
            type.getPrecision().get()); // 指定固定长度
      default: // 未知类型
        throw new AssertionError(type.getBase() + ": " + column); // 抛出断言错误，表示遇到了不支持的类型
      }
    }
  }
}
