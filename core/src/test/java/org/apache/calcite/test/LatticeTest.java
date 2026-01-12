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
package org.apache.calcite.test; // 包声明：Apache Calcite测试包
import org.apache.calcite.jdbc.CalciteSchema; // 导入CalciteSchema类，用于处理Calcite模式
import org.apache.calcite.materialize.Lattice; // 导入Lattice类，表示多维数据结构(晶格)
import org.apache.calcite.materialize.Lattices; // 导入Lattices工具类，提供晶格相关静态方法
import org.apache.calcite.materialize.MaterializationService; // 导入MaterializationService类，用于管理物化视图服务
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，表示关系优化规划器
import org.apache.calcite.plan.RelOptRule; // 导入RelOptRule类，表示关系优化规则
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil工具类，提供关系表达式处理工具方法
import org.apache.calcite.rel.rules.materialize.MaterializedViewRules; // 导入MaterializedViewRules类，提供物化视图规则集合
import org.apache.calcite.runtime.Hook; // 导入Hook类，用于在特定执行点插入自定义逻辑
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，表示可扩展的模式
import org.apache.calcite.sql.SqlDialect; // 导入SqlDialect类，表示SQL方言
import org.apache.calcite.sql.SqlDialect.DatabaseProduct; // 导入DatabaseProduct枚举，表示数据库产品类型
import org.apache.calcite.test.schemata.foodmart.FoodmartSchema; // 导入FoodmartSchema类，提供Foodmart测试数据模式
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合
import org.apache.calcite.util.TestUtil; // 导入TestUtil工具类，提供测试辅助方法

import com.google.common.base.Throwables; // 导入Throwables类，提供异常处理工具方法
import com.google.common.collect.ImmutableList; // 导入ImmutableList类，表示不可变列表

import org.junit.jupiter.api.Disabled; // 导入Disabled注解，用于标记禁用的测试方法
import org.junit.jupiter.api.Tag; // 导入Tag注解，用于标记测试标签
import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法

import java.io.IOException; // 导入IOException类，处理IO异常
import java.sql.Connection; // 导入Connection接口，表示数据库连接
import java.sql.DriverManager; // 导入DriverManager类，管理数据库驱动
import java.sql.ResultSet; // 导入ResultSet接口，表示数据库查询结果集
import java.sql.SQLException; // 导入SQLException类，处理SQL异常
import java.util.ArrayList; // 导入ArrayList类，提供动态数组实现
import java.util.Arrays; // 导入Arrays类，提供数组操作工具方法
import java.util.List; // 导入List接口，表示列表集合
import java.util.Map; // 导入Map接口，表示键值对映射
import java.util.concurrent.atomic.AtomicInteger; // 导入AtomicInteger类，提供原子整数操作
import java.util.function.Consumer; // 导入Consumer接口，表示接受单个参数的操作

import static org.apache.calcite.test.Matchers.containsStringLinux; // 静态导入包含字符串匹配器(Linux换行符)

import static org.hamcrest.CoreMatchers.anyOf; // 静态导入任意匹配器
import static org.hamcrest.CoreMatchers.containsString; // 静态导入包含字符串匹配器
import static org.hamcrest.CoreMatchers.equalTo; // 静态导入等于匹配器
import static org.hamcrest.CoreMatchers.is; // 静态导入is匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入断言方法
import static org.hamcrest.Matchers.closeTo; // 静态导入接近值匹配器
import static org.hamcrest.Matchers.hasSize; // 静态导入大小匹配器
import static org.junit.jupiter.api.Assertions.assertNotEquals; // 静态导入不等于断言
import static org.junit.jupiter.api.Assumptions.assumeTrue; // 静态导入假设为真方法

import static java.util.Objects.requireNonNull; // 静态导入要求非空方法

/**
 * Unit test for lattices. // Lattice(晶格)单元测试类
 * 
 * Lattice是Calcite中用于表示多维数据结构的核心概念，通常用于OLAP(联机分析处理)场景。
 * 晶格由一个事实表和多个维度表通过外键连接组成，形成星型或雪花型模式。
 * 
 * 主要功能：
 * 1. 测试晶格的创建和验证
 * 2. 测试晶格的SQL生成能力
 * 3. 测试晶格的查询重写和优化
 * 4. 测试物化视图的自动推荐和使用
 * 5. 测试聚合表(tile)的生成和利用
 * 
 * 核心概念：
 * - Lattice(晶格)：多维数据结构，包含事实表和维度表
 * - Tile(聚合表)：基于晶格的预聚合表，用于加速查询
 * - Measure(度量)：需要聚合的数值列(如SUM、COUNT)
 * - Dimension(维度)：用于分组的列(如时间、地区)
 * - Star Table(星表)：晶格的虚拟表表示
 * 
 * 测试覆盖：
 * - 晶格SQL生成和方言支持
 * - 晶格属性验证
 * - 查询重写和优化
 * - 物化视图规则
 * - 聚合表算法
 * - 统计信息提供者
 */
@Tag("slow") // 标记为慢速测试，需要在特定条件下运行
class LatticeTest { // Lattice测试类定义
  private static final String SALES_LATTICE = "{\n" // 定义销售晶格的JSON配置字符串常量
      + "  name: 'star',\n" // 晶格名称为'star'，表示星型模式
      + "  sql: [\n" // 定义晶格的SQL结构，由多个SQL片段组成
      + "    'select 1 from \"foodmart\".\"sales_fact_1997\" as \"s\"',\n" // 第1行：从销售事实表(sales_fact_1997)选择，别名为's'，这是事实表
      + "    'join \"foodmart\".\"product\" as \"p\" using (\"product_id\")',\n" // 第2行：连接产品表(product)通过product_id，别名为'p'，这是维度表
      + "    'join \"foodmart\".\"time_by_day\" as \"t\" using (\"time_id\")',\n" // 第3行：连接时间表(time_by_day)通过time_id，别名为't'，这是维度表
      + "    'join \"foodmart\".\"product_class\" as \"pc\" on \"p\".\"product_class_id\" = \"pc\".\"product_class_id\"'\n" // 第4行：连接产品分类表(product_class)通过product_class_id，别名为'pc'，这是维度表
      + "  ],\n" // SQL结构定义结束
      + "  auto: false,\n" // auto=false表示不自动生成聚合表，使用预定义的tiles
      + "  algorithm: true,\n" // algorithm=true表示使用优化算法来选择最佳的聚合表
      + "  algorithmMaxMillis: 10000,\n" // 算法最大运行时间为10000毫秒(10秒)
      + "  rowCountEstimate: 86837,\n" // 估计的行数为86837，用于优化算法的成本估算
      + "  defaultMeasures: [ {\n" // 定义默认度量列表，当tile没有指定measures时使用
      + "    agg: 'count'\n" // 默认度量是count聚合函数，用于计数
      + "  } ],\n" // 默认度量定义结束
      + "  tiles: [ {\n" // 定义预定义的聚合表(tiles)列表
      + "    dimensions: [ 'the_year', ['t', 'quarter'] ],\n" // 维度列：年份(the_year)和季度(来自表t的quarter列)，用于分组
      + "   measures: [ {\n" // 定义该tile的度量列表
      + "      agg: 'sum',\n" // 聚合函数为sum，求和
      + "      args: 'unit_sales'\n" // 度量参数为unit_sales(单位销售量)
      + "    }, {\n" // 第二个度量
      + "      agg: 'sum',\n" // 聚合函数为sum，求和
      + "      args: 'store_sales'\n" // 度量参数为store_sales(商店销售额)
      + "    }, {\n" // 第三个度量
      + "      agg: 'count'\n" // 聚合函数为count，计数
      + "    } ]\n" // 度量列表结束
      + "  } ]\n" // tiles定义结束
      + "}\n"; // JSON配置字符串结束

  private static final String INVENTORY_LATTICE = "{\n" // 定义库存晶格的JSON配置字符串常量
      + "  name: 'warehouse',\n" // 晶格名称为'warehouse'，表示仓库相关的星型模式
      + "  sql: [\n" // 定义晶格的SQL结构，由多个SQL片段组成
      + "  'select 1 from \"foodmart\".\"inventory_fact_1997\" as \"s\"',\n" // 第1行：从库存事实表(inventory_fact_1997)选择，别名为's'，这是事实表
      + "  'join \"foodmart\".\"product\" as \"p\" using (\"product_id\")',\n" // 第2行：连接产品表(product)通过product_id，别名为'p'，这是维度表
      + "  'join \"foodmart\".\"time_by_day\" as \"t\" using (\"time_id\")',\n" // 第3行：连接时间表(time_by_day)通过time_id，别名为't'，这是维度表
      + "  'join \"foodmart\".\"warehouse\" as \"w\" using (\"warehouse_id\")'\n" // 第4行：连接仓库表(warehouse)通过warehouse_id，别名为'w'，这是维度表
      + "  ],\n" // SQL结构定义结束
      + "  auto: false,\n" // auto=false表示不自动生成聚合表，使用预定义的tiles
      + "  algorithm: true,\n" // algorithm=true表示使用优化算法来选择最佳的聚合表
      + "  algorithmMaxMillis: 10000,\n" // 算法最大运行时间为10000毫秒(10秒)
      + "  rowCountEstimate: 4070,\n" // 估计的行数为4070，用于优化算法的成本估算
      + "  defaultMeasures: [ {\n" // 定义默认度量列表，当tile没有指定measures时使用
      + "    agg: 'count'\n" // 默认度量是count聚合函数，用于计数
      + "  } ],\n" // 默认度量定义结束
      + "  tiles: [ {\n" // 定义预定义的聚合表(tiles)列表
      + "    dimensions: [ 'the_year', 'warehouse_name'],\n" // 维度列：年份(the_year)和仓库名称(warehouse_name)，用于分组
      + "    measures: [ {\n" // 定义该tile的度量列表
      + "      agg: 'sum',\n" // 聚合函数为sum，求和
      + "      args: 'store_invoice'\n" // 度量参数为store_invoice(商店发票金额)
      + "    }, {\n" // 第二个度量
      + "      agg: 'sum',\n" // 聚合函数为sum，求和
      + "      args: 'supply_time'\n" // 度量参数为supply_time(供应时间)
      + "    }, {\n" // 第三个度量
      + "      agg: 'sum',\n" // 聚合函数为sum，求和
      + "      args: 'warehouse_cost'\n" // 度量参数为warehouse_cost(仓库成本)
      + "    } ]\n" // 度量列表结束
      + "  } ]\n" // tiles定义结束
      + "}\n"; // JSON配置字符串结束

  private static final String AUTO_LATTICE = "{\n" // 定义自动晶格的JSON配置字符串常量(虽然名称是AUTO，但实际配置与SALES_LATTICE相同)
      + "  name: 'star',\n" // 晶格名称为'star'，表示星型模式
      + "  sql: [\n" // 定义晶格的SQL结构，由多个SQL片段组成
      + "    'select 1 from \"foodmart\".\"sales_fact_1997\" as \"s\"',\n" // 第1行：从销售事实表(sales_fact_1997)选择，别名为's'，这是事实表
      + "    'join \"foodmart\".\"product\" as \"p\" using (\"product_id\")',\n" // 第2行：连接产品表(product)通过product_id，别名为'p'，这是维度表
      + "    'join \"foodmart\".\"time_by_day\" as \"t\" using (\"time_id\")',\n" // 第3行：连接时间表(time_by_day)通过time_id，别名为't'，这是维度表
      + "    'join \"foodmart\".\"product_class\" as \"pc\" on \"p\".\"product_class_id\" = \"pc\".\"product_class_id\"'\n" // 第4行：连接产品分类表(product_class)通过product_class_id，别名为'pc'，这是维度表
      + "  ],\n" // SQL结构定义结束
      + "  auto: false,\n" // auto=false表示不自动生成聚合表，使用预定义的tiles
      + "  algorithm: true,\n" // algorithm=true表示使用优化算法来选择最佳的聚合表
      + "  algorithmMaxMillis: 10000,\n" // 算法最大运行时间为10000毫秒(10秒)
      + "  rowCountEstimate: 86837,\n" // 估计的行数为86837，用于优化算法的成本估算
      + "  defaultMeasures: [ {\n" // 定义默认度量列表，当tile没有指定measures时使用
      + "    agg: 'count'\n" // 默认度量是count聚合函数，用于计数
      + "  } ],\n" // 默认度量定义结束
      + "  tiles: [ {\n" // 定义预定义的聚合表(tiles)列表
      + "    dimensions: [ 'the_year', ['t', 'quarter'] ],\n" // 维度列：年份(the_year)和季度(来自表t的quarter列)，用于分组
      + "   measures: [ {\n" // 定义该tile的度量列表
      + "      agg: 'sum',\n" // 聚合函数为sum，求和
      + "      args: 'unit_sales'\n" // 度量参数为unit_sales(单位销售量)
      + "    }, {\n" // 第二个度量
      + "      agg: 'sum',\n" // 聚合函数为sum，求和
      + "      args: 'store_sales'\n" // 度量参数为store_sales(商店销售额)
      + "    }, {\n" // 第三个度量
      + "      agg: 'count'\n" // 聚合函数为count，计数
      + "    } ]\n" // 度量列表结束
      + "  } ]\n" // tiles定义结束
      + "}\n"; // JSON配置字符串结束

  private static CalciteAssert.AssertThat modelWithLattice(String name, // 静态工厂方法：创建包含单个晶格的CalciteAssert测试模型
      String sql, // 参数name：晶格的名称
      String... extras) { // 参数sql：晶格的SQL定义；参数extras：额外的配置项(可变参数)
    final StringBuilder buf = new StringBuilder("{ name: '") // 创建StringBuilder用于构建JSON配置字符串
        .append(name) // 添加晶格名称
        .append("', sql: ") // 添加sql字段名
        .append(TestUtil.escapeString(sql)); // 添加转义后的SQL字符串
    for (String extra : extras) { // 遍历所有额外的配置项
      buf.append(", ").append(extra); // 将每个额外配置项添加到JSON字符串中
    }
    buf.append("}"); // 添加JSON结束括号
    return modelWithLattices(buf.toString()); // 调用modelWithLattices方法，传入构建好的JSON字符串，返回CalciteAssert.AssertThat对象用于测试断言
  }

  private static CalciteAssert.AssertThat modelWithLattices( // 静态工厂方法：创建包含多个晶格的CalciteAssert测试模型
      String... lattices) { // 参数lattices：可变参数，接受多个晶格的JSON配置字符串
    final Class<JdbcTest.EmpDeptTableFactory> clazz = // 获取EmpDeptTableFactory类的Class对象
        JdbcTest.EmpDeptTableFactory.class; // 这是一个自定义表工厂，用于创建EMPLOYEES表
    return CalciteAssert.model("" // 使用CalciteAssert.model方法创建测试模型
        + "{\n" // 模型配置开始
        + "  version: '1.0',\n" // 模型版本号
        + "   schemas: [\n" // 模式定义开始
        + FoodmartSchema.FOODMART_SCHEMA // 添加Foodmart模式(包含foodmart数据库的所有表)
        + ",\n" // 模式分隔符
        + "     {\n" // 定义adhoc模式
        + "       name: 'adhoc',\n" // 模式名称为'adhoc'，表示临时/即席查询模式
        + "       tables: [\n" // 表定义开始
        + "         {\n" // 定义EMPLOYEES表
        + "           name: 'EMPLOYEES',\n" // 表名为'EMPLOYEES'
        + "           type: 'custom',\n" // 表类型为'custom'，表示使用自定义工厂创建
        + "           factory: '" // 指定工厂类名
        + clazz.getName() // 获取工厂类的完整限定名
        + "',\n" // 工厂类名字符串结束
        + "           operand: {'foo': true, 'bar': 345}\n" // 传递给工厂的参数，包含foo和bar两个属性
        + "         }\n" // EMPLOYEES表定义结束
        + "       ],\n" // 表定义结束
        + "       lattices: " // 晶格定义开始
        + Arrays.toString(lattices) // 将所有晶格配置转换为数组字符串形式
        + "     }\n" // adhoc模式定义结束
        + "   ]\n" // 模式定义结束
        + "}").withDefaultSchema("adhoc"); // 模型配置结束，设置默认模式为'adhoc'
  }

  /** Tests that it's OK for a lattice to have the same name as a table in the
   * schema. */ // 测试方法注释：测试晶格可以与模式中的表同名
  @Test void testLatticeSql() throws Exception { // 测试方法：测试晶格的SQL生成功能
    modelWithLattice("EMPLOYEES", "select * from \"foodmart\".\"days\"") // 创建测试模型，晶格名称为'EMPLOYEES'(与表名相同)，SQL查询days表
        .doWithConnection(c -> { // 使用连接执行操作，参数c是Connection对象
          final SchemaPlus schema = c.getRootSchema(); // 获取根模式对象
          final SchemaPlus adhoc = // 获取adhoc子模式
              requireNonNull(schema.subSchemas().get("adhoc")); // 从子模式映射中获取'adhoc'模式，确保不为null
          assertThat(adhoc.tables().get("EMPLOYEES") != null, is(true)); // 断言：验证adhoc模式中存在名为'EMPLOYEES'的表
          final CalciteSchema adhocSchema = // 获取CalciteSchema对象
              requireNonNull(adhoc.unwrap(CalciteSchema.class)); // 从SchemaPlus解包为CalciteSchema，确保不为null
          final Map.Entry<String, CalciteSchema.LatticeEntry> entry = // 获取晶格映射的第一个条目
              adhocSchema.getLatticeMap().firstEntry(); // 获取晶格映射的第一个键值对(名称到晶格条目)
          final Lattice lattice = entry.getValue().getLattice(); // 从晶格条目中获取Lattice对象
          final String sql = "SELECT \"days\".\"day\"\n" // 预期的SQL字符串：选择day列并按day分组
              + "FROM \"foodmart\".\"days\" AS \"days\"\n" // 从days表选择
              + "GROUP BY \"days\".\"day\""; // 按day列分组
          assertThat( // 断言：验证晶格生成的SQL是否符合预期
              lattice.sql(ImmutableBitSet.of(0), // 调用lattice.sql方法，传入维度列的位集合(第0列)
                  ImmutableList.of()), is(sql)); // 传入空的度量列表，期望生成的SQL等于预期的sql字符串
          final String sql2 = "SELECT" // 预期的第二个SQL字符串：选择day和week_day两列
              + " \"days\".\"day\", \"days\".\"week_day\"\n" // 选择两列
              + "FROM \"foodmart\".\"days\" AS \"days\""; // 从days表选择
          assertThat( // 断言：验证晶格生成的SQL是否符合预期
              lattice.sql(ImmutableBitSet.of(0, 1), false, // 调用lattice.sql方法，传入维度列的位集合(第0和1列)，false表示不分组
                  ImmutableList.of()), // 传入空的度量列表
              is(sql2)); // 期望生成的SQL等于预期的sql2字符串
        }); // doWithConnection操作结束
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6603">[CALCITE-6603]
   * Lattice SQL supports generation of specified dialects</a>. */ // 测试方法注释：测试晶格SQL支持生成指定方言的SQL(CALCITE-6603)
  @Test void testLatticeSqlWithDialect() throws Exception { // 测试方法：测试晶格SQL生成支持特定SQL方言
    final SqlDialect dialect = DatabaseProduct.SPARK.getDialect(); // 获取Spark数据库的SQL方言对象
    modelWithLattice("EMPLOYEES", "select * from \"foodmart\".\"days\"") // 创建测试模型，晶格名称为'EMPLOYEES'，SQL查询days表
        .doWithConnection(c -> { // 使用连接执行操作，参数c是Connection对象
          final SchemaPlus schema = c.getRootSchema(); // 获取根模式对象
          final SchemaPlus adhoc = // 获取adhoc子模式
              requireNonNull(schema.subSchemas().get("adhoc")); // 从子模式映射中获取'adhoc'模式，确保不为null
          assertThat(adhoc.tables().get("EMPLOYEES") != null, is(true)); // 断言：验证adhoc模式中存在名为'EMPLOYEES'的表
          final CalciteSchema adhocSchema = // 获取CalciteSchema对象
              requireNonNull(adhoc.unwrap(CalciteSchema.class)); // 从SchemaPlus解包为CalciteSchema，确保不为null
          final Map.Entry<String, CalciteSchema.LatticeEntry> entry = // 获取晶格映射的第一个条目
              adhocSchema.getLatticeMap().firstEntry(); // 获取晶格映射的第一个键值对(名称到晶格条目)
          final Lattice lattice = entry.getValue().getLattice(); // 从晶格条目中获取Lattice对象
          final String sql = "SELECT `days`.`day`\n" // 预期的Spark方言SQL字符串：使用反引号而不是双引号
              + "FROM `foodmart`.`days` AS `days`\n" // 从days表选择，使用反引号
              + "GROUP BY `days`.`day`"; // 按day列分组，使用反引号
          assertThat( // 断言：验证晶格生成的Spark方言SQL是否符合预期
              lattice.sql(ImmutableBitSet.of(0), true, // 调用lattice.sql方法，传入维度列的位集合(第0列)，true表示分组
                  ImmutableList.of(), dialect), is(sql)); // 传入空的度量列表和Spark方言对象，期望生成的SQL等于预期的sql字符串
          final String sql2 = "SELECT `days`.`day`, `days`.`week_day`\n" // 预期的第二个Spark方言SQL字符串
              + "FROM `foodmart`.`days` AS `days`"; // 选择两列，使用反引号
          assertThat( // 断言：验证晶格生成的Spark方言SQL是否符合预期
              lattice.sql(ImmutableBitSet.of(0, 1), false, // 调用lattice.sql方法，传入维度列的位集合(第0和1列)，false表示不分组
                  ImmutableList.of(), dialect), // 传入空的度量列表和Spark方言对象
              is(sql2)); // 期望生成的SQL等于预期的sql2字符串
        }); // doWithConnection操作结束
  }

  /** Tests some of the properties of the {@link Lattice} data structure. */ // 测试方法注释：测试Lattice数据结构的某些属性
  @Test void testLattice() throws Exception { // 测试方法：测试晶格对象的各种属性
    modelWithLattice("star", // 创建测试模型，晶格名称为'star'
        "select 1 from \"foodmart\".\"sales_fact_1997\" as s\n" // SQL定义：从销售事实表选择，别名为's'
            + "join \"foodmart\".\"product\" as p using (\"product_id\")\n" // 连接产品表，别名为'p'
            + "join \"foodmart\".\"time_by_day\" as t on t.\"time_id\" = s.\"time_id\"") // 连接时间表，别名为't'
        .doWithConnection(c -> { // 使用连接执行操作，参数c是Connection对象
          final SchemaPlus schema = c.getRootSchema(); // 获取根模式对象
          final SchemaPlus adhoc = // 获取adhoc子模式
              requireNonNull(schema.subSchemas().get("adhoc")); // 从子模式映射中获取'adhoc'模式，确保不为null
          assertThat(adhoc.tables().get("EMPLOYEES") != null, is(true)); // 断言：验证adhoc模式中存在名为'EMPLOYEES'的表
          final CalciteSchema adhocSchema = // 获取CalciteSchema对象
              requireNonNull(adhoc.unwrap(CalciteSchema.class)); // 从SchemaPlus解包为CalciteSchema，确保不为null
          final Map.Entry<String, CalciteSchema.LatticeEntry> entry = // 获取晶格映射的第一个条目
              adhocSchema.getLatticeMap().firstEntry(); // 获取晶格映射的第一个键值对(名称到晶格条目)
          final Lattice lattice = entry.getValue().getLattice(); // 从晶格条目中获取Lattice对象
          assertThat(lattice.firstColumn("S"), is(10)); // 断言：验证表别名'S'的第一列索引是10
          assertThat(lattice.firstColumn("P"), is(18)); // 断言：验证表别名'P'的第一列索引是18
          assertThat(lattice.firstColumn("T"), is(0)); // 断言：验证表别名'T'的第一列索引是0
          assertThat(lattice.firstColumn("PC"), is(-1)); // 断言：验证表别名'PC'不存在，返回-1
          assertThat(lattice.defaultMeasures, hasSize(1)); // 断言：验证默认度量列表的大小为1
          assertThat(lattice.rootNode.descendants, hasSize(3)); // 断言：验证根节点的后代节点数量为3(表示有3个维度表)
        }); // doWithConnection操作结束
  }

  /** Tests that it's OK for a lattice to have the same name as a table in the
   * schema. */ // 测试方法注释：测试晶格可以与模式中的表同名
  @Test void testLatticeWithSameNameAsTable() { // 测试方法：测试晶格与表同名的情况
    modelWithLattice("EMPLOYEES", "select * from \"foodmart\".\"days\"") // 创建测试模型，晶格名称为'EMPLOYEES'(与表名相同)，SQL查询days表
        .query("select count(*) from EMPLOYEES") // 执行查询：统计EMPLOYEES的行数
        .returnsValue("4"); // 断言：期望返回值为'4'
  }

  /** Tests that it's an error to have two lattices with the same name in a
   * schema. */ // 测试方法注释：测试在同一个模式中有两个同名的晶格是错误的
  @Test void testTwoLatticesWithSameNameFails() { // 测试方法：测试同名晶格的失败情况
    modelWithLattices( // 创建包含两个晶格的测试模型
        "{name: 'Lattice1', sql: 'select * from \"foodmart\".\"days\"'}", // 第一个晶格：名称为'Lattice1'，查询days表
        "{name: 'Lattice1', sql: 'select * from \"foodmart\".\"time_by_day\"'}") // 第二个晶格：名称也是'Lattice1'，查询time_by_day表(名称重复)
        .connectThrows("Duplicate lattice 'Lattice1'"); // 断言：期望连接时抛出异常，异常消息包含"Duplicate lattice 'Lattice1'"
  }

  /** Tests a lattice whose SQL is invalid. */ // 测试方法注释：测试晶格的SQL无效的情况
  @Test void testLatticeInvalidSqlFails() { // 测试方法：测试无效SQL的晶格
    modelWithLattice("star", "select foo from nonexistent") // 创建测试模型，晶格名称为'star'，SQL查询不存在的表和列
        .connectThrows("Error instantiating JsonLattice(name=star, ") // 断言：期望连接时抛出异常，异常消息包含实例化错误
        .connectThrows("Object 'NONEXISTENT' not found"); // 断言：期望异常消息包含对象未找到的错误
  }

  /** Tests a lattice whose SQL is invalid because it contains a GROUP BY. */ // 测试方法注释：测试晶格SQL包含GROUP BY子句是无效的
  @Test void testLatticeSqlWithGroupByFails() { // 测试方法：测试包含GROUP BY的无效晶格SQL
    modelWithLattice("star", // 创建测试模型，晶格名称为'star'
        "select 1 from \"foodmart\".\"sales_fact_1997\" as s group by \"product_id\"") // SQL包含GROUP BY子句，这在晶格中是不允许的
        .connectThrows("Invalid node type LogicalAggregate in lattice query"); // 断言：期望连接时抛出异常，异常消息指出LogicalAggregate节点类型无效
  }

  /** Tests a lattice whose SQL is invalid because it contains a ORDER BY. */ // 测试方法注释：测试晶格SQL包含ORDER BY子句是无效的
  @Test void testLatticeSqlWithOrderByFails() { // 测试方法：测试包含ORDER BY的无效晶格SQL
    modelWithLattice("star", // 创建测试模型，晶格名称为'star'
        "select 1 from \"foodmart\".\"sales_fact_1997\" as s order by \"product_id\"") // SQL包含ORDER BY子句，这在晶格中是不允许的
        .connectThrows("Invalid node type LogicalSort in lattice query"); // 断言：期望连接时抛出异常，异常消息指出LogicalSort节点类型无效
  }

  /** Tests a lattice whose SQL is invalid because it contains a UNION ALL. */ // 测试方法注释：测试晶格SQL包含UNION ALL是无效的
  @Test void testLatticeSqlWithUnionFails() { // 测试方法：测试包含UNION ALL的无效晶格SQL
    modelWithLattice("star", // 创建测试模型，晶格名称为'star'
        "select 1 from \"foodmart\".\"sales_fact_1997\" as s\n" // SQL第一部分：从销售事实表选择
        + "union all\n" // 使用UNION ALL合并结果
        + "select 1 from \"foodmart\".\"sales_fact_1997\" as s") // SQL第二部分：再次从销售事实表选择
        .connectThrows("Invalid node type LogicalUnion in lattice query"); // 断言：期望连接时抛出异常，异常消息指出LogicalUnion节点类型无效
  }

  /** Tests a lattice with valid join SQL. */ // 测试方法注释：测试具有有效连接SQL的晶格
  @Test void testLatticeSqlWithJoin() { // 测试方法：测试有效的连接SQL晶格
    foodmartModel() // 创建Foodmart测试模型
        .query("values 1") // 执行简单查询：返回值1
        .returnsValue("1"); // 断言：期望返回值为'1'
  }

  /** Tests a lattice with invalid SQL (for a lattice). */ // 测试方法注释：测试晶格的无效SQL
  @Test void testLatticeInvalidSql() { // 测试方法：测试无效的晶格SQL(非等值连接)
    modelWithLattice("star", // 创建测试模型，晶格名称为'star'
        "select 1 from \"foodmart\".\"sales_fact_1997\" as s\n" // SQL第一部分：从销售事实表选择
            + "join \"foodmart\".\"product\" as p using (\"product_id\")\n" // 等值连接产品表
            + "join \"foodmart\".\"time_by_day\" as t on s.\"product_id\" = 100") // 非等值连接：连接条件是常量100，这是无效的
        .connectThrows("only equi-join of columns allowed: 100"); // 断言：期望连接时抛出异常，异常消息指出只允许列的等值连接
  }

  /** Left join is invalid in a lattice. */ // 测试方法注释：测试左连接在晶格中是无效的
  @Test void testLatticeInvalidSql2() { // 测试方法：测试左连接的无效晶格SQL
    modelWithLattice("star", // 创建测试模型，晶格名称为'star'
        "select 1 from \"foodmart\".\"sales_fact_1997\" as s\n" // SQL第一部分：从销售事实表选择
        + "join \"foodmart\".\"product\" as p using (\"product_id\")\n" // 内连接产品表
        + "left join \"foodmart\".\"time_by_day\" as t on s.\"product_id\" = p.\"product_id\"") // 左连接时间表，这在晶格中是不允许的
        .connectThrows("only non nulls-generating join allowed, but got LEFT"); // 断言：期望连接时抛出异常，异常消息指出只允许非null生成连接，但得到了LEFT连接
  }

  /** Each lattice table must have a parent. */ // 测试方法注释：测试每个晶格表必须有父节点
  @Test void testLatticeInvalidSql3() { // 测试方法：测试晶格表必须有父节点的约束
    modelWithLattice("star", // 创建测试模型，晶格名称为'star'
        "select 1 from \"foodmart\".\"sales_fact_1997\" as s\n" // SQL第一部分：从销售事实表选择(事实表)
        + "join \"foodmart\".\"product\" as p using (\"product_id\")\n" // 连接产品表(维度表)
        + "join \"foodmart\".\"time_by_day\" as t on s.\"product_id\" = p.\"product_id\"") // 连接时间表，但连接条件错误(应该连接time_id，却连接了product_id)，导致time_by_day表没有正确的父节点
        .connectThrows("child node must have precisely one parent"); // 断言：期望连接时抛出异常，异常消息指出子节点必须只有一个父节点
  }

  /** When a lattice is registered, there is a table with the same name.
   * It can be used for explain, but not for queries. */ // 测试方法注释：测试当晶格注册时，会创建一个同名的表，可用于explain但不能用于查询
  @Test void testLatticeStarTable() { // 测试方法：测试晶格星表的行为
    final AtomicInteger counter = new AtomicInteger(); // 创建原子计数器，用于记录匹配次数
    try { // 尝试执行测试
      foodmartModel() // 创建Foodmart测试模型
          .query("select count(*) from \"adhoc\".\"star\"") // 执行查询：统计star表的行数
          .convertMatches( // 检查关系表达式转换
              CalciteAssert.checkRel("" // 使用checkRel方法检查关系表达式
                  + "LogicalAggregate(group=[{}], EXPR$0=[COUNT()])\n" // 期望的逻辑聚合节点：执行COUNT(*)操作
                  + "  StarTableScan(table=[[adhoc, star]])\n", // 期望的星表扫描节点：扫描adhoc模式的star表
                  counter)); // 传入计数器，用于记录匹配次数
    } catch (Throwable e) { // 捕获异常
      assertThat(Throwables.getStackTraceAsString(e), // 断言：验证异常堆栈跟踪
          containsString("CannotPlanException")); // 期望异常消息包含"CannotPlanException"(无法规划异常)
    }
    assertThat(counter.get(), equalTo(1)); // 断言：验证计数器值为1，表示匹配成功一次
  }

  /** Tests that a 2-way join query can be mapped 4-way join lattice. */ // 测试方法注释：测试2表连接查询可以映射到4表连接的晶格
  @Test void testLatticeRecognizeJoin() { // 测试方法：测试晶格识别连接的能力
    final AtomicInteger counter = new AtomicInteger(); // 创建原子计数器，用于记录匹配次数
    foodmartModel() // 创建Foodmart测试模型(包含4表连接的晶格：sales_fact_1997 + product + time_by_day + product_class)
        .query("select s.\"unit_sales\", p.\"brand_name\"\n" // 执行查询：选择单位销售量和品牌名称
            + "from \"foodmart\".\"sales_fact_1997\" as s\n" // 从销售事实表选择，别名为's'
            + "join \"foodmart\".\"product\" as p using (\"product_id\")\n") // 连接产品表，别名为'p'(这是2表连接查询)
        .enableMaterializations(true) // 启用物化视图
        .substitutionMatches( // 检查替换匹配(查询重写)
            CalciteAssert.checkRel( // 使用checkRel方法检查关系表达式
                "LogicalProject(unit_sales=[$7], brand_name=[$10])\n" // 期望的逻辑投影节点：选择unit_sales(第7列)和brand_name(第10列)
                    + "  LogicalProject(product_id=[$0], time_id=[$1], customer_id=[$2], promotion_id=[$3], store_id=[$4], store_sales=[$5], store_cost=[$6], unit_sales=[$7], product_class_id=[$8], product_id0=[$9], brand_name=[$10], product_name=[$11], SKU=[$12], SRP=[$13], gross_weight=[$14], net_weight=[$15], recyclable_package=[$16], low_fat=[$17], units_per_case=[$18], cases_per_pallet=[$19], shelf_width=[$20], shelf_height=[$21], shelf_depth=[$22])\n" // 期望的逻辑投影节点：包含所有列的投影
                    + "    StarTableScan(table=[[adhoc, star]])\n", // 期望的星表扫描节点：从4表连接的晶格扫描
                counter)); // 传入计数器，用于记录匹配次数
    assertThat(counter.intValue(), equalTo(1)); // 断言：验证计数器值为1，表示查询成功重写为使用晶格
  }

  /** Tests an aggregate on a 2-way join query can use an aggregate table. */ // 测试方法注释：测试2表连接查询的聚合可以使用聚合表
  @Test void testLatticeRecognizeGroupJoin() { // 测试方法：测试晶格识别分组连接的能力
    final AtomicInteger counter = new AtomicInteger(); // 创建原子计数器，用于记录匹配次数
    CalciteAssert.AssertQuery that = foodmartModel() // 创建Foodmart测试模型并获取AssertQuery对象
        .query("select distinct p.\"brand_name\", s.\"customer_id\"\n" // 执行查询：选择不同的品牌名称和客户ID(DISTINCT去重)
            + "from \"foodmart\".\"sales_fact_1997\" as s\n" // 从销售事实表选择，别名为's'
            + "join \"foodmart\".\"product\" as p using (\"product_id\")\n") // 连接产品表，别名为'p'(2表连接查询)
        .enableMaterializations(true) // 启用物化视图
        .substitutionMatches(relNode -> { // 检查替换匹配(查询重写)
          counter.incrementAndGet(); // 计数器递增
          String s = RelOptUtil.toString(relNode); // 将关系节点转换为字符串
          assertThat(s, // 断言：验证关系表达式字符串
              anyOf( // 期望匹配以下任一模式
                  containsStringLinux( // 模式1：包含投影+聚合+星表扫描
                      "LogicalProject(brand_name=[$1], customer_id=[$0])\n" // 逻辑投影节点
                          + "  LogicalAggregate(group=[{2, 10}])\n" // 逻辑聚合节点：按第2和10列分组
                          + "    StarTableScan(table=[[adhoc, star]])\n"), // 星表扫描节点
                  containsStringLinux( // 模式2：只包含聚合+星表扫描(投影可能被优化掉)
                      "LogicalAggregate(group=[{2, 10}])\n" // 逻辑聚合节点：按第2和10列分组
                          + "  StarTableScan(table=[[adhoc, star]])\n"))); // 星表扫描节点
        }); // substitutionMatches结束
    assertThat(counter.intValue(), equalTo(2)); // 断言：验证计数器值为2，表示匹配成功两次(两种可能的优化模式)
    that.explainContains("" // 验证执行计划包含
        + "EnumerableCalc(expr#0..1=[{inputs}], brand_name=[$t1], customer_id=[$t0])\n" // 可枚举计算节点：重命名列
        + "  EnumerableTableScan(table=[[adhoc, m{2, 10}]])") // 可枚举表扫描节点：扫描聚合表m{2, 10}(按第2和10列分组的聚合表)
        .returnsCount(69203); // 断言：验证返回的行数为69203

    // Run the same query again and see whether it uses the same
    // materialization. // 再次运行相同的查询，查看是否使用相同的物化视图
    that.withHook(Hook.CREATE_MATERIALIZATION, // 设置钩子：在创建物化视图时触发
        materializationName -> counter.incrementAndGet()) // 物化视图名称计数器递增
        .returnsCount(69203); // 断言：验证返回的行数为69203

    // Ideally the counter would stay at 2. It increments to 3 because
    // CalciteAssert.AssertQuery creates a new schema for every request,
    // and therefore cannot re-use lattices or materializations from the
    // previous request. // 理想情况下计数器应该保持为2，但它增加到3，因为CalciteAssert.AssertQuery为每个请求创建新模式，因此无法重用之前的晶格或物化视图
    assertThat(counter.intValue(), equalTo(3)); // 断言：验证计数器值为3
  }

  /** Tests a model with pre-defined tiles. */ // 测试方法注释：测试具有预定义聚合表的模型
  @Test void testLatticeWithPreDefinedTiles() { // 测试方法：测试预定义聚合表(tile)的使用
    foodmartModel(" auto: false,\n" // 创建Foodmart测试模型，禁用自动生成聚合表
        + "  defaultMeasures: [ {\n" // 定义默认度量列表
        + "    agg: 'count'\n" // 默认度量是count聚合函数
        + "  } ],\n" // 默认度量定义结束
        + "  tiles: [ {\n" // 定义预定义的聚合表列表
        + "    dimensions: [ 'the_year', ['t', 'quarter'] ],\n" // 维度列：年份(the_year)和季度(来自表t的quarter列)
        + "    measures: [ ]\n" // 度量列表为空，将使用默认度量(count)
        + "  } ]\n") // 聚合表定义结束
        .query("select distinct t.\"the_year\", t.\"quarter\"\n" // 执行查询：选择不同的年份和季度
            + "from \"foodmart\".\"sales_fact_1997\" as s\n" // 从销售事实表选择，别名为's'
            + "join \"foodmart\".\"time_by_day\" as t using (\"time_id\")\n") // 连接时间表，别名为't'
      .enableMaterializations(true) // 启用物化视图
      .explainContains("EnumerableTableScan(table=[[adhoc, m{32, 36}") // 验证执行计划包含聚合表扫描：m{32, 36}表示按第32和36列分组的聚合表
      .returnsCount(4); // 断言：验证返回的行数为4(1997年的4个季度)
  }

  /** A query that uses a pre-defined aggregate table, at the same
   * granularity but fewer calls to aggregate functions. */ // 测试方法注释：测试使用预定义聚合表的查询，具有相同的粒度但更少的聚合函数调用
  @Test void testLatticeWithPreDefinedTilesFewerMeasures() { // 测试方法：测试预定义聚合表减少度量调用的能力
    foodmartModelWithOneTile() // 创建包含一个聚合表的Foodmart测试模型
        .query("select t.\"the_year\", t.\"quarter\", count(*) as c\n" // 执行查询：选择年份、季度和计数
            + "from \"foodmart\".\"sales_fact_1997\" as s\n" // 从销售事实表选择，别名为's'
            + "join \"foodmart\".\"time_by_day\" as t using (\"time_id\")\n" // 连接时间表，别名为't'
            + "group by t.\"the_year\", t.\"quarter\"") // 按年份和季度分组
      .enableMaterializations(true) // 启用物化视图
      .explainContains("" // 验证执行计划包含
          + "EnumerableCalc(expr#0..4=[{inputs}], proj#0..2=[{exprs}])\n" // 可枚举计算节点：投影前3列
          + "  EnumerableTableScan(table=[[adhoc, m{32, 36}") // 可枚举表扫描节点：扫描聚合表m{32, 36}
      .returnsUnordered("the_year=1997; quarter=Q1; C=21588", // 断言：验证返回的4个季度的数据(无序)
          "the_year=1997; quarter=Q2; C=20368",
          "the_year=1997; quarter=Q3; C=21453",
          "the_year=1997; quarter=Q4; C=23428")
      .sameResultWithMaterializationsDisabled(); // 验证：禁用物化视图后返回相同的结果
  }

  /** Tests a query that uses a pre-defined aggregate table at a lower
   * granularity. Includes a measure computed from a grouping column, a measure
   * based on COUNT rolled up using SUM, and an expression on a measure. */ // 测试方法注释：测试使用预定义聚合表的查询，具有更低的粒度。包括从分组列计算的度量、基于COUNT使用SUM上卷的度量，以及度量上的表达式
  @Test void testLatticeWithPreDefinedTilesRollUp() { // 测试方法：测试预定义聚合表的上卷(roll-up)能力
    foodmartModelWithOneTile() // 创建包含一个聚合表的Foodmart测试模型(按年份和季度分组)
        .query("select t.\"the_year\",\n" // 执行查询：选择年份
            + "  count(*) as c,\n" // 计数
            + "  min(\"quarter\") as q,\n" // 最小季度(从分组列计算的度量)
            + "  sum(\"unit_sales\") * 10 as us\n" // 单位销售量总和乘以10(度量上的表达式)
            + "from \"foodmart\".\"sales_fact_1997\" as s\n" // 从销售事实表选择，别名为's'
            + "join \"foodmart\".\"time_by_day\" as t using (\"time_id\")\n" // 连接时间表，别名为't'
            + "group by t.\"the_year\"") // 按年份分组(比聚合表的粒度更粗，需要从季度上卷到年份)
        .enableMaterializations(true) // 启用物化视图
        .explainContains("" // 验证执行计划包含
            + "EnumerableCalc(expr#0..3=[{inputs}], expr#4=[10], expr#5=[*($t3, $t4)], proj#0..2=[{exprs}], US=[$t5])\n" // 可枚举计算节点：计算单位销售量总和乘以10
            + "  EnumerableAggregate(group=[{0}], C=[$SUM0($2)], Q=[MIN($1)], agg#2=[$SUM0($4)])\n" // 可枚举聚合节点：按第0列分组，C使用SUM0($2)上卷(从季度到年份)，Q使用MIN($1)获取最小季度，agg#2使用SUM0($4)上卷单位销售量
            + "    EnumerableTableScan(table=[[adhoc, m{32, 36}") // 可枚举表扫描节点：扫描聚合表m{32, 36}(按年份和季度分组)
        .enable(CalciteAssert.DB != CalciteAssert.DatabaseInstance.ORACLE) // 启用条件：非Oracle数据库
        .returnsUnordered("the_year=1997; C=86837; Q=Q1; US=2667730.0000") // 断言：验证返回1997年的汇总数据
        .sameResultWithMaterializationsDisabled(); // 验证：禁用物化视图后返回相同的结果
  }

  /** Tests a model that uses an algorithm to generate an initial set of
   * tiles.
   *
   * <p>Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-428">[CALCITE-428]
   * Use optimization algorithm to suggest which tiles of a lattice to
   * materialize</a>. */ // 测试方法注释：测试使用算法生成初始聚合表集合的模型(CALCITE-428)
  @Test void testTileAlgorithm() { // 测试方法：测试聚合表算法
    final String explain = "EnumerableAggregate(group=[{2, 3}])\n" // 期望的执行计划：按第2和3列分组
        + "  EnumerableTableScan(table=[[adhoc, m{16, 17, 32, 36, 37}]])"; // 扫描聚合表m{16, 17, 32, 36, 37}，包含5个列
    checkTileAlgorithm( // 调用checkTileAlgorithm方法验证
        FoodMartLatticeStatisticProvider.class.getCanonicalName() + "#FACTORY", // 使用FoodMartLatticeStatisticProvider的FACTORY统计提供者
        explain); // 传入期望的执行计划
  }

  /** As {@link #testTileAlgorithm()}, but uses the
   * {@link Lattices#CACHED_SQL} statistics provider. */ // 测试方法注释：与testTileAlgorithm相同，但使用CACHED_SQL统计提供者
  @Test void testTileAlgorithm2() { // 测试方法：测试聚合表算法(使用CACHED_SQL统计提供者)
    // Different explain than above, but note that it still selects columns
    // (27, 31). // 与上面的执行计划不同，但注意它仍然选择了列(27, 31)
    final String explain = "EnumerableAggregate(group=[{4, 5}])\n" // 期望的执行计划：按第4和5列分组
        + "  EnumerableTableScan(table=[[adhoc, m{16, 17, 27, 31, 32, 36, 37}]"; // 扫描聚合表m{16, 17, 27, 31, 32, 36, 37}，包含7个列
    checkTileAlgorithm(Lattices.class.getCanonicalName() + "#CACHED_SQL", // 使用Lattices的CACHED_SQL统计提供者
        explain); // 传入期望的执行计划
  }

  /** As {@link #testTileAlgorithm()}, but uses the
   * {@link Lattices#PROFILER} statistics provider. */ // 测试方法注释：与testTileAlgorithm相同，但使用PROFILER统计提供者
  @Test void testTileAlgorithm3() { // 测试方法：测试聚合表算法(使用PROFILER统计提供者)
    assumeTrue(TestUtil.getJavaMajorVersion() >= 8, // 假设条件：Java主版本>=8
        "Yahoo sketches requires JDK 8 or higher"); // 原因：Yahoo sketches需要JDK 8或更高版本
    final String explain = "EnumerableAggregate(group=[{4, 5}])\n" // 期望的执行计划：按第4和5列分组
        + "  EnumerableTableScan(table=[[adhoc, m{16, 17, 27, 31, 32, 36, 37}]"; // 扫描聚合表m{16, 17, 27, 31, 32, 36, 37}，包含7个列
    checkTileAlgorithm(Lattices.class.getCanonicalName() + "#PROFILER", // 使用Lattices的PROFILER统计提供者
        explain); // 传入期望的执行计划
  }

  private void checkTileAlgorithm(String statisticProvider, // 私有辅助方法：验证聚合表算法
      String expectedExplain) { // 参数statisticProvider：统计提供者的完整类名；参数expectedExplain：期望的执行计划字符串
    final RelOptRule[] rules = { // 定义要移除的物化视图规则数组
        MaterializedViewRules.PROJECT_FILTER, // 投影过滤器规则
        MaterializedViewRules.FILTER, // 过滤器规则
        MaterializedViewRules.PROJECT_JOIN, // 投影连接规则
        MaterializedViewRules.JOIN, // 连接规则
        MaterializedViewRules.PROJECT_AGGREGATE, // 投影聚合规则
        MaterializedViewRules.AGGREGATE // 聚合规则
    };
    MaterializationService.setThreadLocal(); // 设置线程本地的物化视图服务
    MaterializationService.instance().clear(); // 清除物化视图服务中的所有物化视图
    foodmartLatticeModel(statisticProvider) // 创建Foodmart晶格测试模型，使用指定的统计提供者
        .query("select distinct t.\"the_year\", t.\"quarter\"\n" // 执行查询：选择不同的年份和季度
            + "from \"foodmart\".\"sales_fact_1997\" as s\n" // 从销售事实表选择，别名为's'
            + "join \"foodmart\".\"time_by_day\" as t using (\"time_id\")\n") // 连接时间表，别名为't'
        .enableMaterializations(true) // 启用物化视图

    // Disable materialization rules from this test. For some reason, there is
    // a weird interaction between these rules and the lattice rewriting that
    // produces non-deterministic rewriting (even when only lattices are present).
    // For more context, see
    // <a href="https://issues.apache.org/jira/browse/CALCITE-2953">[CALCITE-2953]</a>. // 从此测试中禁用物化视图规则。由于某些原因，这些规则与晶格重写之间存在奇怪的交互，导致非确定性的重写(即使只有晶格存在)。有关更多背景，请参见CALCITE-2953
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 设置规划器钩子：在规划器创建时触发
            Arrays.asList(rules).forEach(planner::removeRule)) // 从规划器中移除所有物化视图规则

    // disable for MySQL; times out running star-join query
    // disable for H2; it thinks our generated SQL has invalid syntax // 禁用MySQL：运行星连接查询超时；禁用H2：它认为我们生成的SQL语法无效
        .enable(CalciteAssert.DB != CalciteAssert.DatabaseInstance.MYSQL // 启用条件：非MySQL数据库
            && CalciteAssert.DB != CalciteAssert.DatabaseInstance.H2) // 且非H2数据库
        .explainContains(expectedExplain) // 验证执行计划包含期望的字符串
        .returnsUnordered("the_year=1997; quarter=Q1", // 断言：验证返回4个季度的数据(无序)
            "the_year=1997; quarter=Q2",
            "the_year=1997; quarter=Q3",
            "the_year=1997; quarter=Q4"); // 查询结束
  }

  private static CalciteAssert.AssertThat foodmartLatticeModel( // 私有静态工厂方法：创建Foodmart晶格测试模型
      String statisticProvider) { // 参数statisticProvider：统计提供者的完整类名
    return foodmartModel(" auto: false,\n" // 调用foodmartModel方法，添加晶格配置
        + "  algorithm: true,\n" // 启用算法：使用优化算法生成聚合表
        + "  algorithmMaxMillis: -1,\n" // 算法最大运行时间为-1(无限制)
        + "  rowCountEstimate: 87000,\n" // 估计的行数为87000
        + "  defaultMeasures: [ {\n" // 定义默认度量列表
        + "      agg: 'sum',\n" // 聚合函数为sum
        + "      args: 'unit_sales'\n" // 度量参数为unit_sales(单位销售量)
        + "    }, {\n" // 第二个度量
        + "      agg: 'sum',\n" // 聚合函数为sum
        + "      args: 'store_sales'\n" // 度量参数为store_sales(商店销售额)
        + "    }, {\n" // 第三个度量
        + "      agg: 'count'\n" // 聚合函数为count
        + "  } ],\n" // 默认度量定义结束
        + "  statisticProvider: '" // 指定统计提供者
        + statisticProvider // 插入统计提供者的完整类名
        + "',\n" // 统计提供者字符串结束
        + "  tiles: [ {\n" // 定义预定义的聚合表列表
        + "    dimensions: [ 'the_year', ['t', 'quarter'] ],\n" // 维度列：年份(the_year)和季度(来自表t的quarter列)
        + "    measures: [ ]\n" // 度量列表为空，将使用默认度量
        + "  } ]\n"); // 聚合表定义结束
  }

  /** Tests a query that is created within {@link #testTileAlgorithm()}. */ // 测试方法注释：测试在testTileAlgorithm中创建的查询
  @Test void testJG() { // 测试方法：测试复杂的聚合查询(JG可能是开发者名字缩写)
    final String sql = "" // 定义SQL查询字符串
        + "SELECT \"s\".\"unit_sales\", \"p\".\"recyclable_package\", \"t\".\"the_day\", \"t\".\"the_year\", \"t\".\"quarter\", \"pc\".\"product_family\", COUNT(*) AS \"m0\", SUM(\"s\".\"store_sales\") AS \"m1\", SUM(\"s\".\"unit_sales\") AS \"m2\"\n" // 选择多个列和多个聚合函数
        + "FROM \"foodmart\".\"sales_fact_1997\" AS \"s\"\n" // 从销售事实表选择，别名为's'
        + "JOIN \"foodmart\".\"product\" AS \"p\" ON \"s\".\"product_id\" = \"p\".\"product_id\"\n" // 连接产品表
        + "JOIN \"foodmart\".\"time_by_day\" AS \"t\" ON \"s\".\"time_id\" = \"t\".\"time_id\"\n" // 连接时间表
        + "JOIN \"foodmart\".\"product_class\" AS \"pc\" ON \"p\".\"product_class_id\" = \"pc\".\"product_class_id\"\n" // 连接产品分类表
        + "GROUP BY \"s\".\"unit_sales\", \"p\".\"recyclable_package\", \"t\".\"the_day\", \"t\".\"the_year\", \"t\".\"quarter\", \"pc\".\"product_family\""; // 按6个列分组
    final String explain = "JdbcToEnumerableConverter\n" // 期望的执行计划：JDBC到可枚举转换器
        + "  JdbcAggregate(group=[{3, 6, 8, 9, 10, 12}], m0=[COUNT()], m1=[$SUM0($2)], m2=[$SUM0($3)])\n" // JDBC聚合节点：按第3,6,8,9,10,12列分组，计算COUNT、SUM(store_sales)、SUM(unit_sales)
        + "    JdbcJoin(condition=[=($4, $11)], joinType=[inner])\n" // JDBC连接节点：连接条件为第4列=第11列
        + "      JdbcJoin(condition=[=($1, $7)], joinType=[inner])\n" // JDBC连接节点：连接条件为第1列=第7列
        + "        JdbcJoin(condition=[=($0, $5)], joinType=[inner])\n" // JDBC连接节点：连接条件为第0列=第5列
        + "          JdbcProject(product_id=[$0], time_id=[$1], store_sales=[$5], unit_sales=[$7])\n" // JDBC投影节点：选择4个列
        + "            JdbcTableScan(table=[[foodmart, sales_fact_1997]])\n" // JDBC表扫描节点：扫描sales_fact_1997表
        + "          JdbcProject(product_class_id=[$0], product_id=[$1], recyclable_package=[$8])\n" // JDBC投影节点：选择3个列
        + "            JdbcTableScan(table=[[foodmart, product]])\n" // JDBC表扫描节点：扫描product表
        + "        JdbcProject(time_id=[$0], the_day=[$2], the_year=[$4], quarter=[$8])\n" // JDBC投影节点：选择4个列
        + "          JdbcTableScan(table=[[foodmart, time_by_day]])\n" // JDBC表扫描节点：扫描time_by_day表
        + "      JdbcProject(product_class_id=[$0], product_family=[$4])\n" // JDBC投影节点：选择2个列
        + "        JdbcTableScan(table=[[foodmart, product_class]])"; // JDBC表扫描节点：扫描product_class表
    CalciteAssert.that().with(CalciteAssert.Config.JDBC_FOODMART) // 创建CalciteAssert测试，使用JDBC_FOODMART配置
        .query(sql) // 执行SQL查询
        .explainContains(explain); // 验证执行计划包含期望的字符串
  }

  /** Tests a query that uses no columns from the fact table. */ // 测试方法注释：测试不使用事实表列的查询
  @Test void testGroupByEmpty() { // 测试方法：测试空分组查询(不使用任何维度列)
    foodmartModel() // 创建Foodmart测试模型
        .query("select count(*) as c from \"foodmart\".\"sales_fact_1997\"") // 执行查询：统计销售事实表的总行数(不使用任何维度列)
        .enableMaterializations(true) // 启用物化视图
        .returnsUnordered("C=86837"); // 断言：验证返回的计数为86837
  }

  /** Calls {@link #testDistinctCount()} followed by
   * {@link #testGroupByEmpty()}. */ // 测试方法注释：依次调用testDistinctCount和testGroupByEmpty
  @Test void testGroupByEmptyWithPrelude() { // 测试方法：测试空分组查询(带前导测试)
    testDistinctCount(); // 先调用testDistinctCount方法
    testGroupByEmpty(); // 再调用testGroupByEmpty方法
  }

  /** Tests a query that uses no dimension columns and one measure column. */ // 测试方法注释：测试不使用维度列且使用一个度量列的查询
  @Test void testGroupByEmpty2() { // 测试方法：测试空分组查询(只有一个度量)
    foodmartModel() // 创建Foodmart测试模型
        .query("select sum(\"unit_sales\") as s\n" // 执行查询：计算单位销售量的总和(不使用任何维度列)
            + "from \"foodmart\".\"sales_fact_1997\"") // 从销售事实表选择
        .enableMaterializations(true) // 启用物化视图
        .enable(CalciteAssert.DB != CalciteAssert.DatabaseInstance.ORACLE) // 启用条件：非Oracle数据库
        .returnsUnordered("S=266773.0000"); // 断言：验证返回的总和为266773.0000
  }

  /** Tests that two queries of the same dimensionality that use different
   * measures can use the same materialization. */ // 测试方法注释：测试具有相同维度但使用不同度量的两个查询可以使用相同的物化视图
  @Test void testGroupByEmpty3() { // 测试方法：测试物化视图的复用
    final List<String> mats = new ArrayList<>(); // 创建列表，用于记录创建的物化视图名称
    final CalciteAssert.AssertThat that = foodmartModel().pooled(); // 创建池化的Foodmart测试模型(可以复用连接和模式)
    that.query("select sum(\"unit_sales\") as s, count(*) as c\n" // 执行第一个查询：计算单位销售量总和和计数
            + "from \"foodmart\".\"sales_fact_1997\"") // 从销售事实表选择
        .withHook(Hook.CREATE_MATERIALIZATION, (Consumer<String>) mats::add) // 设置钩子：在创建物化视图时将名称添加到列表
        .enableMaterializations(true) // 启用物化视图
        .explainContains("EnumerableTableScan(table=[[adhoc, m{}]])") // 验证执行计划包含空分组的聚合表m{}
        .enable(CalciteAssert.DB != CalciteAssert.DatabaseInstance.ORACLE) // 启用条件：非Oracle数据库
        .returnsUnordered("S=266773.0000; C=86837"); // 断言：验证返回的总和和计数
    assertThat(mats.toString(), mats, hasSize(2)); // 断言：验证创建了2个物化视图(sum和count各一个)

    // A similar query can use the same materialization. // 类似的查询可以使用相同的物化视图
    that.query("select sum(\"unit_sales\") as s\n" // 执行第二个查询：只计算单位销售量总和
        + "from \"foodmart\".\"sales_fact_1997\"") // 从销售事实表选择
        .withHook(Hook.CREATE_MATERIALIZATION, (Consumer<String>) mats::add) // 设置钩子：在创建物化视图时将名称添加到列表
        .enableMaterializations(true) // 启用物化视图
        .enable(CalciteAssert.DB != CalciteAssert.DatabaseInstance.ORACLE) // 启用条件：非Oracle数据库
        .returnsUnordered("S=266773.0000"); // 断言：验证返回的总和
    assertThat(mats.toString(), mats, hasSize(2)); // 断言：验证物化视图列表大小仍为2，说明复用了第一个查询创建的物化视图
  }

  /** Rolling up SUM. */ // 测试方法注释：测试SUM的上卷
  @Test void testSum() { // 测试方法：测试SUM聚合的上卷
    foodmartModelWithOneTile() // 创建包含一个聚合表的Foodmart测试模型(按年份和季度分组)
        .query("select sum(\"unit_sales\") as c\n" // 执行查询：计算每个产品的单位销售量总和
            + "from \"foodmart\".\"sales_fact_1997\"\n" // 从销售事实表选择
            + "group by \"product_id\"\n" // 按产品ID分组(比聚合表的粒度更细，需要从季度上卷到产品)
            + "order by 1 desc limit 1") // 按总和降序排序，只返回第一条记录
        .enableMaterializations(true) // 启用物化视图
        .enable(CalciteAssert.DB != CalciteAssert.DatabaseInstance.ORACLE) // 启用条件：非Oracle数据库
        .returnsUnordered("C=267.0000"); // 断言：验证返回的最大总和为267.0000
  }

  /** Tests a distinct-count query.
   *
   * <p>We can't just roll up count(distinct ...) as we do count(...), but we
   * can still use the aggregate table if we're smart. */ // 测试方法注释：测试去重计数查询。我们不能像处理count(...)那样简单地上卷count(distinct ...)，但如果我们聪明的话，仍然可以使用聚合表
  @Test void testDistinctCount() { // 测试方法：测试去重计数查询
    foodmartModelWithOneTile() // 创建包含一个聚合表的Foodmart测试模型(按年份和季度分组)
        .query("select count(distinct \"quarter\") as c\n" // 执行查询：计算每年的不同季度数量
            + "from \"foodmart\".\"sales_fact_1997\"\n" // 从销售事实表选择
            + "join \"foodmart\".\"time_by_day\" using (\"time_id\")\n" // 连接时间表
            + "group by \"the_year\"") // 按年份分组
        .enableMaterializations(true) // 启用物化视图
        .explainContains("EnumerableCalc(expr#0..1=[{inputs}], C=[$t1])\n" // 验证执行计划包含：计算节点，选择第1列(季度)
            + "  EnumerableAggregate(group=[{0}], C=[COUNT($1)])\n" // 聚合节点：按第0列(年份)分组，对第1列(季度)进行COUNT
            + "    EnumerableTableScan(table=[[adhoc, m{32, 36}]])") // 扫描聚合表m{32, 36}(按年份和季度分组)
        .returnsUnordered("C=4"); // 断言：验证每年的季度数量为4
  }

  @Test void testDistinctCount2() { // 测试方法：测试去重计数查询(特殊情况)
    foodmartModelWithOneTile() // 创建包含一个聚合表的Foodmart测试模型(按年份和季度分组)
        .query("select count(distinct \"the_year\") as c\n" // 执行查询：计算每年的不同年份数量(这应该总是1)
            + "from \"foodmart\".\"sales_fact_1997\"\n" // 从销售事实表选择
            + "join \"foodmart\".\"time_by_day\" using (\"time_id\")\n" // 连接时间表
            + "group by \"the_year\"") // 按年份分组
        .enableMaterializations(true) // 启用物化视图
        .explainContains("EnumerableCalc(expr#0=[{inputs}], expr#1=[IS NOT NULL($t0)], " // 验证执行计划包含：计算节点，检查年份是否不为null
            + "expr#2=[1:BIGINT], expr#3=[0:BIGINT], expr#4=[CASE($t1, $t2, $t3)], C=[$t4])\n" // 使用CASE表达式：如果不为null返回1，否则返回0
            + "  EnumerableAggregate(group=[{0}])\n" // 聚合节点：按第0列(年份)分组
            + "    EnumerableTableScan(table=[[adhoc, m{32, 36}]])") // 扫描聚合表m{32, 36}(按年份和季度分组)
        .returnsUnordered("C=1"); // 断言：验证每年的不同年份数量为1
  }

  /** Runs all queries against the Foodmart schema, using a lattice.
   *
   * <p>Disabled for normal runs, because it is slow. */ // 测试方法注释：使用晶格运行Foodmart模式的所有查询。禁用于正常运行，因为它很慢
  @Disabled // 标记为禁用的测试方法
  @Test void testAllFoodmartQueries() { // 测试方法：运行所有Foodmart查询
    // Test ids that had bugs in them until recently. Useful for a sanity check. // 最近有bug的测试ID，用于完整性检查
    final List<Integer> fixed = // 定义已修复的测试ID列表
        ImmutableList.of(13, 24, 28, 30, 61, 76, 79, 81, // 这些测试ID之前有bug，现在已经修复
            85, 98, 101, 107, 128, 129, 130, 131);
    // Test ids that still have bugs // 仍然有bug的测试ID
    final List<Integer> bad = ImmutableList.of(382, 423); // 定义仍有bug的测试ID列表
    for (int i = 1; i < 1000; i++) { // 循环遍历测试ID从1到999
      System.out.println("i=" + i); // 打印当前测试ID
      try { // 尝试执行测试
        if (bad.contains(i)) { // 如果测试ID在bad列表中
          continue; // 跳过这个测试
        }
        check(i); // 调用check方法执行测试
      } catch (Throwable e) { // 捕获异常
        throw new RuntimeException("error in " + i, e); // 抛出运行时异常，包含测试ID和原始异常
      }
    }
  }

  private void check(int n) throws IOException { // 私有辅助方法：检查指定的测试查询
    final FoodMartQuerySet set = FoodMartQuerySet.instance(); // 获取FoodMartQuerySet单例实例
    final FoodMartQuerySet.FoodmartQuery query = set.queries.get(n); // 从查询集合中获取指定ID的查询
    if (query == null) { // 如果查询为null(不存在)
      return; // 直接返回
    }
    foodmartModelWithOneTile() // 创建包含一个聚合表的Foodmart测试模型
        .withDefaultSchema("foodmart") // 设置默认模式为'foodmart'
        .query(query.sql) // 执行查询SQL
      .sameResultWithMaterializationsDisabled(); // 验证：禁用物化视图后返回相同的结果
  }

  /** A tile with no measures should inherit default measure list from the
   * lattice. */ // 测试方法注释：没有度量的聚合表应该从晶格继承默认度量列表
  @Test void testTileWithNoMeasures() { // 测试方法：测试聚合表继承默认度量
    foodmartModel(" auto: false,\n" // 创建Foodmart测试模型，禁用自动生成聚合表
        + "  defaultMeasures: [ {\n" // 定义默认度量列表
        + "    agg: 'count'\n" // 默认度量是count聚合函数
        + "  } ],\n" // 默认度量定义结束
        + "  tiles: [ {\n" // 定义预定义的聚合表列表
        + "    dimensions: [ 'the_year', ['t', 'quarter'] ],\n" // 维度列：年份(the_year)和季度(来自表t的quarter列)
        + "    measures: [ ]\n" // 度量列表为空，将继承默认度量(count)
        + "  } ]\n") // 聚合表定义结束
        .query("select count(t.\"the_year\", t.\"quarter\")\n" // 执行查询：计算年份和季度的组合数量
            + "from \"foodmart\".\"sales_fact_1997\" as s\n" // 从销售事实表选择，别名为's'
            + "join \"foodmart\".\"time_by_day\" as t using (\"time_id\")\n") // 连接时间表，别名为't'
        .enableMaterializations(true) // 启用物化视图
        .explainContains("EnumerableAggregate(group=[{}], EXPR$0=[COUNT($0, $1)])\n" // 验证执行计划包含：聚合节点，计算COUNT($0, $1)
            + "  EnumerableTableScan(table=[[adhoc, m{32, 36}") // 扫描聚合表m{32, 36}(按年份和季度分组)
        .returnsCount(1); // 断言：验证返回的行数为1
  }

  /** A lattice with no default measure list should get "count(*)" is its
   * default measure. */ // 测试方法注释：没有默认度量列表的晶格应该将"count(*)"作为其默认度量
  @Test void testLatticeWithNoMeasures() { // 测试方法：测试晶格没有默认度量时的行为
    foodmartModel(" auto: false,\n" // 创建Foodmart测试模型，禁用自动生成聚合表
        + "  tiles: [ {\n" // 定义预定义的聚合表列表
        + "    dimensions: [ 'the_year', ['t', 'quarter'] ],\n" // 维度列：年份(the_year)和季度(来自表t的quarter列)
        + "    measures: [ ]\n" // 度量列表为空
        + "  } ]\n") // 聚合表定义结束(没有定义defaultMeasures)
        .query("select count(*)\n" // 执行查询：统计总行数
            + "from \"foodmart\".\"sales_fact_1997\" as s\n" // 从销售事实表选择，别名为's'
            + "join \"foodmart\".\"time_by_day\" as t using (\"time_id\")\n") // 连接时间表，别名为't'
        .enableMaterializations(true) // 启用物化视图
        .explainContains("EnumerableAggregate(group=[{}], EXPR$0=[COUNT()])\n" // 验证执行计划包含：聚合节点，计算COUNT()
            + "  EnumerableTableScan(table=[[adhoc, m{32, 36}") // 扫描聚合表m{32, 36}(按年份和季度分组)
        .returnsCount(1); // 断言：验证返回的行数为1
  }

  @Test void testDimensionIsInvalidColumn() { // 测试方法：测试维度列无效的情况
    foodmartModel(" auto: false,\n" // 创建Foodmart测试模型，禁用自动生成聚合表
        + "  tiles: [ {\n" // 定义预定义的聚合表列表
        + "    dimensions: [ 'invalid_column'],\n" // 维度列：使用无效的列名'invalid_column'
        + "    measures: [ ]\n" // 度量列表为空
        + "  } ]\n") // 聚合表定义结束
        .connectThrows("Unknown lattice column 'invalid_column'"); // 断言：期望连接时抛出异常，异常消息指出未知的晶格列'invalid_column'
  }

  @Test void testMeasureArgIsInvalidColumn() { // 测试方法：测试度量参数列无效的情况
    foodmartModel(" auto: false,\n" // 创建Foodmart测试模型，禁用自动生成聚合表
        + "  defaultMeasures: [ {\n" // 定义默认度量列表
        + "   agg: 'sum',\n" // 聚合函数为sum
        + "   args: 'invalid_column'\n" // 度量参数为无效的列名'invalid_column'
        + "  } ],\n" // 默认度量定义结束
        + "  tiles: [ {\n" // 定义预定义的聚合表列表
        + "    dimensions: [ 'the_year', ['t', 'quarter'] ],\n" // 维度列：年份(the_year)和季度(来自表t的quarter列)
        + "    measures: [ ]\n" // 度量列表为空，将使用默认度量
        + "  } ]\n") // 聚合表定义结束
        .connectThrows("Unknown lattice column 'invalid_column'"); // 断言：期望连接时抛出异常，异常消息指出未知的晶格列'invalid_column'
  }

  /** It is an error for "time_id" to be a measure arg, because is not a
   * unique alias. Both "s" and "t" have "time_id". */ // 测试方法注释："time_id"作为度量参数是错误的，因为它不是唯一的别名。"s"和"t"都有"time_id"
  @Test void testMeasureArgIsNotUniqueAlias() { // 测试方法：测试度量参数别名不唯一的情况
    foodmartModel(" auto: false,\n" // 创建Foodmart测试模型，禁用自动生成聚合表
        + "  defaultMeasures: [ {\n" // 定义默认度量列表
        + "    agg: 'count',\n" // 聚合函数为count
        + "    args: 'time_id'\n" // 度量参数为'time_id'，但这个列名在多个表中存在(sales_fact_1997和time_by_day)，别名不唯一
        + "  } ],\n" // 默认度量定义结束
        + "  tiles: [ {\n" // 定义预定义的聚合表列表
        + "    dimensions: [ 'the_year', ['t', 'quarter'] ],\n" // 维度列：年份(the_year)和季度(来自表t的quarter列)
        + "    measures: [ ]\n" // 度量列表为空，将使用默认度量
        + "  } ]\n") // 聚合表定义结束
        .connectThrows("Lattice column alias 'time_id' is not unique"); // 断言：期望连接时抛出异常，异常消息指出晶格列别名'time_id'不唯一
  }

  @Test void testMeasureAggIsInvalid() { // 测试方法：测试度量聚合函数无效的情况
    foodmartModel(" auto: false,\n" // 创建Foodmart测试模型，禁用自动生成聚合表
        + "  defaultMeasures: [ {\n" // 定义默认度量列表
        + "    agg: 'invalid_count',\n" // 聚合函数为无效的'invalid_count'
        + "    args: 'customer_id'\n" // 度量参数为customer_id
        + "  } ],\n" // 默认度量定义结束
        + "  tiles: [ {\n" // 定义预定义的聚合表列表
        + "    dimensions: [ 'the_year', ['t', 'quarter'] ],\n" // 维度列：年份(the_year)和季度(来自表t的quarter列)
        + "    measures: [ ]\n" // 度量列表为空，将使用默认度量
        + "  } ]\n") // 聚合表定义结束
        .connectThrows("Unknown lattice aggregate function invalid_count"); // 断言：期望连接时抛出异常，异常消息指出未知的晶格聚合函数invalid_count
  }

  @Test void testTwoLattices() {
    final AtomicInteger counter = new AtomicInteger();
    // disable for MySQL; times out running star-join query
    // disable for H2; it thinks our generated SQL has invalid syntax
    final boolean enabled =
        CalciteAssert.DB != CalciteAssert.DatabaseInstance.MYSQL
            && CalciteAssert.DB != CalciteAssert.DatabaseInstance.H2;
    modelWithLattices(SALES_LATTICE, INVENTORY_LATTICE)
        .query("select s.\"unit_sales\", p.\"brand_name\"\n"
            + "from \"foodmart\".\"sales_fact_1997\" as s\n"
            + "join \"foodmart\".\"product\" as p using (\"product_id\")\n")
        .enableMaterializations(true)
        .enable(enabled)
        .substitutionMatches(
            CalciteAssert.checkRel(
                "LogicalProject(unit_sales=[$7], brand_name=[$10])\n"
                    + "  LogicalProject(product_id=[$0], time_id=[$1], customer_id=[$2], promotion_id=[$3], store_id=[$4], store_sales=[$5], store_cost=[$6], unit_sales=[$7], product_class_id=[$8], product_id0=[$9], brand_name=[$10], product_name=[$11], SKU=[$12], SRP=[$13], gross_weight=[$14], net_weight=[$15], recyclable_package=[$16], low_fat=[$17], units_per_case=[$18], cases_per_pallet=[$19], shelf_width=[$20], shelf_height=[$21], shelf_depth=[$22])\n"
                    + "    StarTableScan(table=[[adhoc, star]])\n",
                counter));
    if (enabled) {
      assertThat(counter.intValue(), is(1));
    }
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-787">[CALCITE-787]
   * Star table wrongly assigned to materialized view</a>. */
  @Test void testOneLatticeOneMV() {
    final AtomicInteger counter = new AtomicInteger();
    final Class<JdbcTest.EmpDeptTableFactory> clazz =
        JdbcTest.EmpDeptTableFactory.class;

    final String mv = "       materializations: [\n"
        + "         {\n"
        + "           table: \"m0\",\n"
        + "           view: \"m0v\",\n"
        + "           sql: \"select * from \\\"foodmart\\\".\\\"sales_fact_1997\\\" "
        + "where \\\"product_id\\\" = 10\" "
        + "         }\n"
        + "       ]\n";

    final String model = ""
        + "{\n"
        + "  version: '1.0',\n"
        + "   schemas: [\n"
        + FoodmartSchema.FOODMART_SCHEMA
        + ",\n"
        + "     {\n"
        + "       name: 'adhoc',\n"
        + "       tables: [\n"
        + "         {\n"
        + "           name: 'EMPLOYEES',\n"
        + "           type: 'custom',\n"
        + "           factory: '"
        + clazz.getName()
        + "',\n"
        + "           operand: {'foo': true, 'bar': 345}\n"
        + "         }\n"
        + "       ],\n"
        + "       lattices: " + "[" + INVENTORY_LATTICE
        + "       ]\n"
        + "     },\n"
        + "     {\n"
        + "       name: 'mat',\n"
        + mv
        + "     }\n"
        + "   ]\n"
        + "}";

    CalciteAssert.model(model)
        .withDefaultSchema("foodmart")
        .query("select * from \"foodmart\".\"sales_fact_1997\" where \"product_id\" = 10")
        .enableMaterializations(true)
        .substitutionMatches(
            CalciteAssert.checkRel(
                "LogicalTableScan(table=[[mat, m0]])\n",
                counter));
    assertThat(counter.intValue(), equalTo(1));
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-760">[CALCITE-760]
   * Aggregate recommender blows up if row count estimate is too high</a>. */ // 测试方法注释：测试CALCITE-760：如果行数估计过高，聚合推荐器会崩溃
  @Disabled // 标记为禁用的测试方法
  @Test void testLatticeWithBadRowCountEstimate() { // 测试方法：测试行数估计过高的情况
    final String lattice = // 定义修改后的晶格配置字符串
        INVENTORY_LATTICE.replace("rowCountEstimate: 4070,", // 将行数估计从4070
            "rowCountEstimate: 4074070,"); // 修改为4074070(增加了1000倍)
    assertNotEquals(lattice, INVENTORY_LATTICE); // 断言：验证lattice不等于INVENTORY_LATTICE，确保替换成功
    modelWithLattices(lattice) // 创建包含修改后晶格的测试模型
        .query("values 1\n") // 执行简单查询：返回值1
        .returns("EXPR$0=1\n"); // 断言：期望返回"EXPR$0=1"
  }

  @Test void testSuggester() { // 测试方法：测试自动晶格推荐功能
    final Class<JdbcTest.EmpDeptTableFactory> clazz = // 获取EmpDeptTableFactory类的Class对象
        JdbcTest.EmpDeptTableFactory.class; // 这是一个自定义表工厂，用于创建EMPLOYEES表
    final String model = "" // 定义模型配置字符串
        + "{\n" // 模型配置开始
        + "  version: '1.0',\n" // 模型版本号
        + "   schemas: [\n" // 模式定义开始
        + FoodmartSchema.FOODMART_SCHEMA // 添加Foodmart模式
        + ",\n" // 模式分隔符
        + "     {\n" // 定义adhoc模式
        + "       name: 'adhoc',\n" // 模式名称为'adhoc'
        + "       tables: [\n" // 表定义开始
        + "         {\n" // 定义EMPLOYEES表
        + "           name: 'EMPLOYEES',\n" // 表名为'EMPLOYEES'
        + "           type: 'custom',\n" // 表类型为'custom'，表示使用自定义工厂创建
        + "           factory: '" + clazz.getName() + "',\n" // 指定工厂类名
        + "           operand: {'foo': true, 'bar': 345}\n" // 传递给工厂的参数，包含foo和bar两个属性
        + "         }\n" // EMPLOYEES表定义结束
        + "       ],\n" // 表定义结束
        + "       \"autoLattice\": true" // 启用自动晶格推荐功能
        + "     }\n" // adhoc模式定义结束
        + "   ]\n" // 模式定义结束
        + "}"; // 模型配置结束
    final String sql = "select count(*)\n" // 定义SQL查询字符串
        + "from \"sales_fact_1997\"\n" // 从sales_fact_1997表选择
        + "join \"time_by_day\" using (\"time_id\")\n"; // 连接time_by_day表
    final String explain = "PLAN=JdbcToEnumerableConverter\n" // 期望的执行计划字符串
        + "  JdbcAggregate(group=[{}], EXPR$0=[COUNT()])\n" // JDBC聚合节点：计算COUNT(*)
        + "    JdbcJoin(condition=[=($0, $1)], joinType=[inner])\n" // JDBC连接节点：连接条件为第0列=第1列
        + "      JdbcProject(time_id=[$1])\n" // JDBC投影节点：选择第1列(time_id)
        + "        JdbcTableScan(table=[[foodmart, sales_fact_1997]])\n" // JDBC表扫描节点：扫描sales_fact_1997表
        + "      JdbcProject(time_id=[$0])\n" // JDBC投影节点：选择第0列(time_id)
        + "        JdbcTableScan(table=[[foodmart, time_by_day]])\n"; // JDBC表扫描节点：扫描time_by_day表
    CalciteAssert.model(model) // 创建CalciteAssert测试，使用指定的模型配置
        .withDefaultSchema("foodmart") // 设置默认模式为'foodmart'
        .query(sql) // 执行SQL查询
        .returns("EXPR$0=86837\n") // 断言：期望返回"EXPR$0=86837"
        .explainContains(explain); // 验证执行计划包含期望的字符串
  }

  private static CalciteAssert.AssertThat foodmartModel(String... extras) { // 私有静态工厂方法：创建Foodmart测试模型
    final String sql = "select 1\n" // 定义晶格的SQL结构：从销售事实表开始，连接3个维度表
        + "from \"foodmart\".\"sales_fact_1997\" as \"s\"\n" // 从销售事实表选择，别名为's'，这是事实表
        + "join \"foodmart\".\"product\" as \"p\" using (\"product_id\")\n" // 连接产品表，别名为'p'，通过product_id连接
        + "join \"foodmart\".\"time_by_day\" as \"t\" using (\"time_id\")\n" // 连接时间表，别名为't'，通过time_id连接
        + "join \"foodmart\".\"product_class\" as \"pc\"\n" // 连接产品分类表，别名为'pc'
        + "  on \"p\".\"product_class_id\" = \"pc\".\"product_class_id\""; // 通过product_class_id连接
    return modelWithLattice("star", sql, extras); // 调用modelWithLattice方法，传入晶格名称'star'、SQL定义和额外配置项
  }

  private CalciteAssert.AssertThat foodmartModelWithOneTile() { // 私有工厂方法：创建包含一个聚合表的Foodmart测试模型
    return foodmartModel(" auto: false,\n" // 调用foodmartModel方法，添加晶格配置
        + "  defaultMeasures: [ {\n" // 定义默认度量列表
        + "    agg: 'count'\n" // 默认度量是count聚合函数
        + "  } ],\n" // 默认度量定义结束
        + "  tiles: [ {\n" // 定义预定义的聚合表列表
        + "    dimensions: [ 'the_year', ['t', 'quarter'] ],\n" // 维度列：年份(the_year)和季度(来自表t的quarter列)
        + "    measures: [ {\n" // 定义该tile的度量列表
        + "      agg: 'sum',\n" // 聚合函数为sum，求和
        + "      args: 'unit_sales'\n" // 度量参数为unit_sales(单位销售量)
        + "    }, {\n" // 第二个度量
        + "      agg: 'sum',\n" // 聚合函数为sum，求和
        + "      args: 'store_sales'\n" // 度量参数为store_sales(商店销售额)
        + "    }, {\n" // 第三个度量
        + "      agg: 'count'\n" // 聚合函数为count，计数
        + "    } ]\n" // 度量列表结束
        + "  } ]\n"); // 聚合表定义结束
  }

  // Just for debugging. // 注释：仅用于调试
  private static void runJdbc() throws SQLException { // 私有静态方法：运行JDBC调试代码
    final String url = "jdbc:calcite:model=" // 定义JDBC连接URL
        + "core/src/test/resources/mysql-foodmart-lattice-model.json"; // 指定模型文件路径
    final Connection connection = DriverManager.getConnection(url); // 获取数据库连接
    final ResultSet resultSet = connection.createStatement() // 创建语句对象
        .executeQuery("select * from \"adhoc\".\"m{32, 36}\""); // 执行查询：扫描聚合表m{32, 36}(按年份和季度分组)
    System.out.println(CalciteAssert.toString(resultSet)); // 打印结果集内容
    connection.close(); // 关闭数据库连接
  }

  /** Unit test for {@link Lattice#getRowCount(double, List)}. */ // 测试方法注释：Lattice.getRowCount方法的单元测试
  @Test void testColumnCount() { // 测试方法：测试Lattice.getRowCount方法(计算聚合表的估计行数)
    assertThat(Lattice.getRowCount(10, 2, 3), closeTo(5.03D, 0.01D)); // 断言：验证总行数10，2个列，3个不同值时的估计行数约为5.03
    assertThat(Lattice.getRowCount(10, 9, 8), closeTo(9.4D, 0.01D)); // 断言：验证总行数10，9个列，8个不同值时的估计行数约为9.4
    assertThat(Lattice.getRowCount(100, 9, 8), closeTo(54.2D, 0.1D)); // 断言：验证总行数100，9个列，8个不同值时的估计行数约为54.2
    assertThat(Lattice.getRowCount(1000, 9, 8), closeTo(72D, 0.01D)); // 断言：验证总行数1000，9个列，8个不同值时的估计行数约为72
    assertThat(Lattice.getRowCount(1000, 1, 1), is(1D)); // 断言：验证总行数1000，1个列，1个不同值时的估计行数为1
    assertThat(Lattice.getRowCount(1, 3, 5), closeTo(1D, 0.01D)); // 断言：验证总行数1，3个列，5个不同值时的估计行数约为1
    assertThat(Lattice.getRowCount(1, 3, 5, 13, 4831), closeTo(1D, 0.01D)); // 断言：验证总行数1，多个列，多个不同值时的估计行数约为1
  }
} // 类定义结束
