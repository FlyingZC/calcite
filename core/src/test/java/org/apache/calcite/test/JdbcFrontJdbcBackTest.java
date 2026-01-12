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
package org.apache.calcite.test; // org.apache.calcite.test包，包含Calcite框架的测试类

import org.apache.calcite.avatica.MetaImpl.MetaTable; // Avatica框架中的MetaTable接口，用于表示元数据表
import org.apache.calcite.config.CalciteConnectionProperty; // Calcite连接属性配置类
import org.apache.calcite.jdbc.CalciteMetaImpl; // Calcite元数据实现类，提供数据库元数据信息
import org.apache.calcite.jdbc.CalciteMetaImpl.CalciteMetaTable; // Calcite元数据表实现类
import org.apache.calcite.jdbc.CalciteMetaTableFactory; // Calcite元数据表工厂接口，用于创建元数据表实例
import org.apache.calcite.schema.Table; // Calcite表接口，表示数据库表
import org.apache.calcite.util.TestUtil; // Calcite测试工具类，提供测试辅助方法

import com.google.common.collect.ImmutableList; // Google Guava库中的不可变列表类

import org.hamcrest.Matcher; // Hamcrest匹配器接口，用于断言
import org.junit.jupiter.api.Disabled; // JUnit5注解，标记测试方法为禁用状态
import org.junit.jupiter.api.Test; // JUnit5注解，标记测试方法

import java.sql.ResultSet; // JDBC结果集接口，表示数据库查询结果
import java.sql.SQLException; // JDBC异常类，表示数据库操作错误
import java.util.List; // Java集合框架中的List接口

import static org.apache.calcite.test.CalciteAssert.that; // 静态导入CalciteAssert的that方法，用于构建测试断言

import static org.hamcrest.CoreMatchers.is; // Hamcrest匹配器，用于相等性断言
import static org.hamcrest.MatcherAssert.assertThat; // Hamcrest断言方法，用于验证条件
import static org.hamcrest.Matchers.hasToString; // Hamcrest匹配器，用于字符串匹配断言
import static org.junit.jupiter.api.Assertions.assertFalse; // JUnit5断言方法，验证条件为false
import static org.junit.jupiter.api.Assertions.assertTrue; // JUnit5断言方法，验证条件为true

/**
 * Tests for a JDBC front-end and JDBC back-end. // JDBC前端和JDBC后端的测试类
 *
 * <p>The idea is that as much as possible of the query is pushed down // 核心思想是尽可能将查询
 * to the JDBC data source, in the form of a large (and hopefully efficient) // 下推到JDBC数据源，以大SQL语句的形式
 * SQL statement. // （希望是高效的）
 *
 * @see JdbcFrontJdbcBackLinqMiddleTest // 参见JdbcFrontJdbcBackLinqMiddleTest类
 */
class JdbcFrontJdbcBackTest { // JdbcFrontJdbcBackTest类，测试JDBC前端和JDBC后端的集成
  @Test void testWhere2() { // 测试方法：测试WHERE子句的查询下推功能
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 使用JDBC_FOODMART配置，连接到FoodMart数据库
        .query("select * from \"foodmart\".\"days\" where \"day\" < 3") // 执行SQL查询，从days表中选择day小于3的记录
        .returns("day=1; week_day=Sunday\n" // 验证查询返回的结果，包含day=1和day=2两条记录
            + "day=2; week_day=Monday\n"); // 验证结果包含星期日和星期一的数据
  }

  @Disabled // 标记此测试方法为禁用状态，不执行
  @Test void testTables() throws Exception { // 测试方法：测试获取数据库表列表的功能
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 使用JDBC_FOODMART配置
        .doWithConnection(connection -> { // 使用连接执行操作
          try { // 异常处理开始
            ResultSet rset = // 声明结果集变量
                connection.getMetaData().getTables( // 获取数据库元数据中的表信息
                    null, null, null, null); // 参数：catalog、schema、tableNamePattern、types，全部为null表示获取所有表
            StringBuilder buf = new StringBuilder(); // 创建字符串构建器，用于拼接表名
            while (rset.next()) { // 遍历结果集中的每一行
              buf.append(rset.getString(3)).append(';'); // 获取第3列（表名）并添加到字符串构建器
            }
            assertThat(buf, // 断言字符串构建器的内容
                hasToString("account;agg_c_10_sales_fact_1997;" // 验证包含的表名列表
                    + "agg_c_14_sales_fact_1997;agg_c_special_sales_fact_1997;" // 聚合表
                    + "agg_g_ms_pcat_sales_fact_1997;agg_l_03_sales_fact_1997;" // 更多聚合表
                    + "agg_l_04_sales_fact_1997;agg_l_05_sales_fact_1997;" // 聚合表
                    + "agg_lc_06_sales_fact_1997;agg_lc_100_sales_fact_1997;" // 聚合表
                    + "agg_ll_01_sales_fact_1997;agg_pl_01_sales_fact_1997;" // 聚合表
                    + "category;currency;customer;days;department;employee;" // 维度表
                    + "employee_closure;expense_fact;inventory_fact_1997;" // 事实表和闭包表
                    + "inventory_fact_1998;position;product;product_class;" // 更多事实表和维度表
                    + "products;promotion;region;reserve_employee;salary;" // 产品、促销、区域等表
                    + "sales_fact_1997;sales_fact_1998;sales_fact_dec_1998;" // 销售事实表
                    + "store;store_ragged;time_by_day;warehouse;" // 商店、时间、仓库表
                    + "warehouse_class;COLUMNS;TABLES;")); // 仓库类表和系统元数据表
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        });
  }

  @Test void testTablesExtraColumn() throws Exception { // 测试方法：测试带有额外列的元数据表
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 使用JDBC_FOODMART配置
        .with( // 设置连接属性
            CalciteConnectionProperty.META_TABLE_FACTORY.camelName(), // 设置元数据表工厂属性名
            MetaExtraTableFactoryImpl.class.getName()) // 设置元数据表工厂实现类为MetaExtraTableFactoryImpl
        .doWithConnection(connection -> { // 使用连接执行操作
          try { // 异常处理开始
            ResultSet rset = // 声明结果集变量
                connection.getMetaData().getTables( // 获取数据库元数据中的表信息
                    null, null, null, null); // 获取所有表
            assertTrue(rset.next()); // 断言结果集至少有一行数据
            // Asserts that the number of columns in the result set equals // 断言结果集的列数等于
            // MetaExtraTableFactoryImpl's expected number of columns. // MetaExtraTableFactoryImpl期望的列数
            assertThat(rset.getMetaData().getColumnCount(), is(11)); // 断言结果集有11列
            assertThat("EXTRA_LABEL", is(rset.getMetaData().getColumnName(11))); // 断言第11列的列名为EXTRA_LABEL
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        });
  }

  @Test void testTablesByType() throws Exception { // 测试方法：测试按类型获取表的功能
    // check with the form recommended by JDBC // 使用JDBC推荐的格式检查
    checkTablesByType("SYSTEM TABLE", is("COLUMNS;TABLES;")); // 检查SYSTEM TABLE类型，应返回COLUMNS和TABLES
    // the form we used until 1.14 no longer generates results // 1.14版本之前使用的格式不再生成结果
    checkTablesByType("SYSTEM_TABLE", is("")); // 检查SYSTEM_TABLE类型，应返回空字符串
  }

  private void checkTablesByType(final String tableType, // 私有方法：按表类型检查表列表，参数tableType为表类型
      final Matcher<String> matcher) throws Exception { // 参数matcher为匹配器，用于验证结果
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.REGULAR_PLUS_METADATA) // 使用REGULAR_PLUS_METADATA配置
        .doWithConnection(connection -> { // 使用连接执行操作
          try (ResultSet rset = // 使用try-with-resources自动关闭结果集
                   connection.getMetaData().getTables(null, null, null, // 获取指定类型的表信息
                       new String[] {tableType})) { // 传入表类型数组
            StringBuilder buf = new StringBuilder(); // 创建字符串构建器
            while (rset.next()) { // 遍历结果集
              buf.append(rset.getString(3)).append(';'); // 获取表名并添加到字符串构建器
            }
            assertThat(buf, hasToString(matcher)); // 断言结果匹配期望的匹配器
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        });
  }

  @Test void testColumns() throws Exception { // 测试方法：测试获取表列信息的功能
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 使用JDBC_FOODMART配置
        .doWithConnection(connection -> { // 使用连接执行操作
          try { // 异常处理开始
            ResultSet resultSet = // 声明结果集变量
                connection.getMetaData().getColumns( // 获取表的列信息
                    null, null, "sales_fact_1997", null); // 获取sales_fact_1997表的所有列
            StringBuilder buf = new StringBuilder(); // 创建字符串构建器
            while (resultSet.next()) { // 遍历结果集
              buf.append(resultSet.getString(4)).append(';'); // 获取第4列（列名）并添加到字符串构建器
            }
            assertThat(buf, // 断言字符串构建器的内容
                hasToString("product_id;time_id;customer_id;promotion_id;" // 验证包含的列名列表
                    + "store_id;store_sales;store_cost;unit_sales;")); // 销售事实表的列
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        });
  }

  /** Tests a JDBC method known to be not implemented (as it happens, // 测试已知未实现的JDBC方法（恰好是
   * {@link java.sql.DatabaseMetaData#getPrimaryKeys}) that therefore uses // DatabaseMetaData.getPrimaryKeys），因此使用
   * empty result set. */ // 空结果集
  @Test void testEmpty() throws Exception { // 测试方法：测试未实现的JDBC方法返回空结果集
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 使用JDBC_FOODMART配置
        .doWithConnection(connection -> { // 使用连接执行操作
          try { // 异常处理开始
            ResultSet rset = // 声明结果集变量
                connection.getMetaData().getPrimaryKeys( // 获取主键信息（此方法未实现）
                    null, null, "sales_fact_1997"); // 尝试获取sales_fact_1997表的主键
            assertFalse(rset.next()); // 断言结果集为空，没有下一行数据
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        });
  }

  @Test void testCase() { // 测试方法：测试CASE表达式的查询下推功能
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 使用JDBC_FOODMART配置
        .query("select\n" // 执行SQL查询，使用CASE表达式
            + "  case when \"sales_fact_1997\".\"promotion_id\" = 1 then 0\n" // CASE表达式：当promotion_id=1时返回0
            + "  else \"sales_fact_1997\".\"store_sales\" end as \"c0\"\n" // 否则返回store_sales，别名为c0
            + "from \"sales_fact_1997\" as \"sales_fact_1997\"" // 从sales_fact_1997表查询
            + "where \"product_id\" = 1\n" // WHERE条件：product_id等于1
            + "and \"time_id\" < 400") // AND条件：time_id小于400
        .returns2("c0=11.4\n" // 验证查询返回的结果，第一条记录c0=11.4
            + "c0=8.55\n"); // 第二条记录c0=8.55
  }

  /** Mock implementation of {@link CalciteMetaTable}. */ // CalciteMetaTable的模拟实现
  private static class MetaExtraTable extends CalciteMetaTable { // MetaExtraTable类，继承自CalciteMetaTable，用于添加额外列
    final String extraLabel; // 成员变量：额外标签，表示新增的列值

    MetaExtraTable(Table calciteTable, String tableCat, // 构造方法：创建MetaExtraTable实例
        String tableSchem, String tableName) { // 参数：calciteTable为Calcite表，tableCat为目录，tableSchem为模式，tableName为表名
      super(calciteTable, tableCat, tableSchem, tableName); // 调用父类构造方法
      this.extraLabel = "extraLabel1"; // 初始化extraLabel为"extraLabel1"
    }
  }

  /** Mock implementation of {@link CalciteMetaTableFactory} that creates // CalciteMetaTableFactory的模拟实现，创建
   * instances of {@link MetaExtraTable}. Must be public, otherwise it is // MetaExtraTable实例。必须是public，否则
   * inaccessible from {@link org.apache.calcite.jdbc.Driver}. */ // 无法从Driver访问
  public static class MetaExtraTableFactoryImpl // MetaExtraTableFactoryImpl类，实现CalciteMetaTableFactory接口
      implements CalciteMetaTableFactory { // 用于创建MetaExtraTable实例
    public static final MetaExtraTableFactoryImpl INSTANCE = // 静态常量：MetaExtraTableFactoryImpl的单例实例
        new MetaExtraTableFactoryImpl(); // 创建单例对象

    MetaExtraTableFactoryImpl() {} // 私有构造方法，防止外部实例化

    @Override public MetaTable createTable(Table table, String tableCat, // 重写createTable方法：创建元数据表
        String tableSchem, String tableName) { // 参数：table为表对象，tableCat为目录，tableSchem为模式，tableName为表名
      return new MetaExtraTable(table, tableCat, tableSchem, tableName); // 返回MetaExtraTable实例
    }

    @Override public List<String> getColumnNames() { // 重写getColumnNames方法：获取列名列表
      // 11 columns total. // 总共11列
      return ImmutableList.<String>builder() // 创建不可变列表构建器
          .addAll(CalciteMetaImpl.TABLE_COLUMNS) // 添加Calcite默认的表列
          .add("EXTRA_LABEL") // 添加额外的EXTRA_LABEL列
          .build(); // 构建不可变列表
    }

    @Override public Class<? extends MetaTable> getMetaTableClass() { // 重写getMetaTableClass方法：获取元数据表类
      return MetaExtraTable.class; // 返回MetaExtraTable类
    }
  }
}
