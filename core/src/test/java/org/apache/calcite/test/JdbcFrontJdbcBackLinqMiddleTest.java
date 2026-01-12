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
package org.apache.calcite.test; // 定义包名，该类位于org.apache.calcite.test包下，是Calcite测试框架的一部分

import org.apache.calcite.util.Bug; // 导入Bug工具类，用于处理已知的软件缺陷和版本兼容性问题

import org.junit.jupiter.api.Disabled; // 导入Disabled注解，用于标记禁用的测试方法
import org.junit.jupiter.api.Test; // 导入Test注解，用于标记JUnit测试方法

import static org.apache.calcite.test.CalciteAssert.that; // 静态导入CalciteAssert的that方法，用于构建测试断言

/**
 * Tests for a JDBC front-end and JDBC back-end where the processing is not
 * pushed down to JDBC (as in {@link JdbcFrontJdbcBackTest}) but is executed
 * in a pipeline of linq4j operators.
 * 
 * 测试类：JDBC前端和JDBC后端的集成测试，其中处理逻辑不会下推到JDBC（与JdbcFrontJdbcBackTest不同），
 * 而是通过linq4j操作符管道来执行。
 * 
 * 核心特点：
 * 1. 前端：使用JDBC接收SQL查询
 * 2. 中间层：使用linq4j操作符管道处理数据（不将处理下推到数据库）
 * 3. 后端：使用JDBC访问数据源
 * 
 * 与JdbcFrontJdbcBackTest的区别：
 * - JdbcFrontJdbcBackTest会将尽可能多的操作下推到JDBC数据库执行
 * - 本测试类则使用linq4j在应用层执行数据处理逻辑
 * 
 * 主要测试场景：
 * - 基本查询（SELECT、WHERE）
 * - 聚合操作（GROUP BY、COUNT、SUM）
 * - 连接操作（JOIN）
 * - CASE表达式
 * - 计划验证
 */
class JdbcFrontJdbcBackLinqMiddleTest { // 测试类定义，使用默认访问权限

  @Test void testTable() { // 测试方法：基本表查询，验证从JDBC数据源读取数据并通过linq4j管道返回结果
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 配置测试使用FoodMart数据库的JDBC连接
        .query("select * from \"foodmart\".\"days\"") // 执行SQL查询：从foodmart.days表中选择所有列
        .returns("day=1; week_day=Sunday\n" // 验证返回结果包含7条记录，每条记录包含day和week_day字段
            + "day=2; week_day=Monday\n" // 注意结果顺序可能不固定，因为ORDER BY子句未指定
            + "day=5; week_day=Thursday\n" // 这个测试验证了基本的表扫描功能
            + "day=4; week_day=Wednesday\n" // 数据通过JDBC从数据库读取，然后通过linq4j管道处理
            + "day=3; week_day=Tuesday\n" // 不涉及任何过滤或转换操作
            + "day=6; week_day=Friday\n"
            + "day=7; week_day=Saturday\n");
  }

  @Test void testWhere() { // 测试方法：WHERE子句过滤，验证linq4j管道中的过滤操作
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 配置使用FoodMart数据库
        .query("select * from \"foodmart\".\"days\" where \"day\" < 3") // 执行带WHERE条件的查询：过滤day小于3的记录
        .returns("day=1; week_day=Sunday\n" // 验证返回2条记录，即day=1和day=2
            + "day=2; week_day=Monday\n"); // 过滤操作在linq4j管道中执行，而不是下推到数据库
  }

  @Test void testWhere2() { // 测试方法：WHERE子句与函数调用，验证linq4j管道中的函数表达式处理
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 配置使用FoodMart数据库
        .query("select * from \"foodmart\".\"days\"\n" // 执行带函数的WHERE查询
            + "where not (lower(\"week_day\") = 'wednesday')") // 条件：使用lower函数将week_day转为小写，不等于'wednesday'
        .returns("day=1; week_day=Sunday\n" // 验证返回6条记录，排除了Wednesday
            + "day=2; week_day=Monday\n" // lower函数在linq4j管道中执行
            + "day=5; week_day=Thursday\n" // NOT逻辑也在linq4j中处理
            + "day=3; week_day=Tuesday\n" // 这个测试验证了字符串函数和布尔逻辑在linq4j中的执行
            + "day=6; week_day=Friday\n"
            + "day=7; week_day=Saturday\n");
  }

  @Test void testCase() { // 测试方法：CASE表达式，验证linq4j管道中的条件表达式处理
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.FOODMART_CLONE) // 配置使用FoodMart克隆数据源（内存中的数据源）
        .query("select \"day\",\n" // 执行带CASE表达式的查询
            + " \"week_day\",\n"
            + " case when \"day\" < 3 then upper(\"week_day\")\n" // CASE分支1：如果day<3，将week_day转为大写
            + "      when \"day\" < 5 then lower(\"week_day\")\n" // CASE分支2：如果day<5，将week_day转为小写
            + "      else \"week_day\" end as d\n" // CASE分支3：否则保持原样
            + "from \"foodmart\".\"days\"\n"
            + "where \"day\" <> 1\n" // 过滤：排除day=1的记录
            + "order by \"day\"") // 排序：按day升序排列
        .returns("day=2; week_day=Monday; D=MONDAY\n" // 验证返回结果，day=2满足第一个条件（<3），所以转为大写MONDAY
            + "day=3; week_day=Tuesday; D=tuesday\n" // day=3不满足第一个条件但满足第二个条件（<5），所以转为小写tuesday
            + "day=4; week_day=Wednesday; D=wednesday\n" // day=4满足第二个条件（<5），转为小写wednesday
            + "day=5; week_day=Thursday; D=Thursday\n" // day=5不满足前两个条件，保持原样Thursday
            + "day=6; week_day=Friday; D=Friday\n" // day=6保持原样
            + "day=7; week_day=Saturday; D=Saturday\n"); // day=7保持原样
  }

  @Test void testGroup() { // 测试方法：GROUP BY聚合，验证linq4j管道中的分组和聚合函数处理
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 配置使用FoodMart数据库
        .query("select s, count(*) as c, min(\"week_day\") as mw from (\n" // 执行带子查询和GROUP BY的查询
            + "select \"week_day\",\n" // 子查询：提取week_day字段
            + "  substring(\"week_day\" from 1 for 1) as s\n" // 使用substring函数提取week_day的第一个字符作为分组键
            + "from \"foodmart\".\"days\")\n" // 从days表查询
            + "group by s") // 按首字母分组，计算每组的记录数和最小的week_day
        .returnsUnordered( // 验证返回结果（顺序不重要，因为是分组结果）
            "S=T; C=2; MW=Thursday", // T组：Thursday和Tuesday共2条，最小的是Thursday
            "S=F; C=1; MW=Friday", // F组：Friday共1条
            "S=W; C=1; MW=Wednesday", // W组：Wednesday共1条
            "S=S; C=2; MW=Saturday", // S组：Saturday和Sunday共2条，最小的是Saturday
            "S=M; C=1; MW=Monday"); // M组：Monday共1条
  }

  @Test void testGroupEmpty() { // 测试方法：空GROUP BY（全表聚合），验证COUNT(*)聚合函数
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 配置使用FoodMart数据库
        .query("select count(*) as c\n" // 执行全表聚合查询，计算days表的总记录数
            + "from \"foodmart\".\"days\"") // 不带GROUP BY，相当于对所有行进行聚合
        .returns("C=7\n"); // 验证返回结果：days表共7条记录
  }

  /** Tests a theta join: a join whose condition cannot be decomposed
   * into input0.x = input1.x and ... input0.z = input1.z.
   * 
   * 测试Theta连接：连接条件不能分解为简单的等值连接（如input0.x = input1.x）。
   * 
   * Theta连接特点：
   * - 使用非等值条件（如s.customer_id - c.customer_id = 0）
   * - 不能使用哈希连接或排序合并连接等高效算法
   * - 通常需要使用嵌套循环连接，效率较低
   *
   * <p>Currently, the query can be planned, but the plan is not efficient (uses
   * cartesian product).
   * 
   * 当前该查询可以被规划，但计划效率不高（使用笛卡尔积）。
   * 实际上是使用嵌套循环连接，性能较差。
   */
  @Disabled("non-deterministic on JDK 1.7 vs 1.8") // 禁用该测试：在JDK 1.7和1.8之间结果不确定
  @Test void testJoinTheta() { // 测试方法：Theta连接，验证非等值连接的处理
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.FOODMART_CLONE) // 配置使用FoodMart克隆数据源
        .query("select count(*) from (\n" // 执行带Theta连接的查询
            + "  select *\n" // 子查询：选择所有字段
            + "  from \"foodmart\".\"sales_fact_1997\" as s\n" // 左表：销售事实表
            + "  join \"foodmart\".\"customer\" as c\n" // 右表：客户表
            + "  on s.\"customer_id\" - c.\"customer_id\" = 0)") // 连接条件：使用减法表达式（非等值连接）
        .explainContains("EnumerableAggregate(group=[{}], EXPR$0=[COUNT()])\n" // 验证执行计划包含聚合操作
            + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=[0], expr#3=[-($t0, $t1)], expr#4=[=($t3, $t2)], DUMMY=[$t2], $condition=[$t4])\n" // 计算节点：实现减法连接条件
            + "    EnumerableNestedLoopJoin(condition=[true], joinType=[inner])\n" // 嵌套循环连接：由于是非等值连接，必须使用嵌套循环
            + "      JdbcToEnumerableConverter\n" // JDBC到Enumerable转换器：将JDBC结果集转为linq4j可枚举对象
            + "        JdbcProject(customer_id=[$2])\n" // JDBC投影：只选择customer_id列
            + "          JdbcTableScan(table=[[foodmart, sales_fact_1997]])\n" // JDBC表扫描：扫描sales_fact_1997表
            + "      JdbcToEnumerableConverter\n" // JDBC到Enumerable转换器
            + "        JdbcProject(customer_id=[$0])\n" // JDBC投影：只选择customer_id列
            + "          JdbcTableScan(table=[[foodmart, customer]])"); // JDBC表扫描：扫描customer表
  }

  @Test void testJoinGroupByEmpty() { // 测试方法：JOIN后的全表聚合，验证连接和聚合的组合操作
    if (CalciteAssert.DB == CalciteAssert.DatabaseInstance.MYSQL // 检查是否使用MySQL数据库
        && !Bug.CALCITE_673_FIXED) { // 检查CALCITE_673缺陷是否已修复
      return; // 如果是MySQL且缺陷未修复，跳过该测试
    }
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 配置使用FoodMart数据库
        .query("select count(*) from (\n" // 执行JOIN后的聚合查询
            + "  select *\n" // 子查询：连接后的所有记录
            + "  from \"foodmart\".\"sales_fact_1997\" as s\n" // 左表：销售事实表
            + "  join \"foodmart\".\"customer\" as c\n" // 右表：客户表
            + "  on s.\"customer_id\" = c.\"customer_id\")") // 等值连接条件：customer_id相等
        .returns("EXPR$0=86837\n"); // 验证返回结果：连接后共86837条记录
  }

  @Test void testJoinGroupByOrderBy() { // 测试方法：JOIN、GROUP BY和ORDER BY的组合，验证复杂查询的处理
    if (CalciteAssert.DB == CalciteAssert.DatabaseInstance.MYSQL // 检查是否使用MySQL数据库
        && !Bug.CALCITE_673_FIXED) { // 检查CALCITE_673缺陷是否已修复
      return; // 如果是MySQL且缺陷未修复，跳过该测试
    }
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 配置使用FoodMart数据库
        .query("select count(*), c.\"state_province\",\n" // 执行带JOIN、GROUP BY和ORDER BY的查询
            + "  sum(s.\"unit_sales\") as s\n" // 聚合函数：计算每个州的销售总量
            + "from \"foodmart\".\"sales_fact_1997\" as s\n" // 左表：销售事实表
            + "  join \"foodmart\".\"customer\" as c\n" // 右表：客户表
            + "  on s.\"customer_id\" = c.\"customer_id\"\n" // 等值连接条件
            + "group by c.\"state_province\"\n" // 按州分组
            + "order by c.\"state_province\"") // 按州排序
        .returns2("EXPR$0=24442; state_province=CA; S=74748\n" // 验证返回结果：CA州24442条记录，销量74748
            + "EXPR$0=21611; state_province=OR; S=67659\n" // OR州21611条记录，销量67659
            + "EXPR$0=40784; state_province=WA; S=124366\n"); // WA州40784条记录，销量124366
  }

  @Test void testCompositeGroupBy() { // 测试方法：复合GROUP BY，验证多列分组和排序
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 配置使用FoodMart数据库
        .query("select count(*) as c, c.\"state_province\"\n" // 执行复合GROUP BY查询
            + "from \"foodmart\".\"customer\" as c\n" // 从客户表查询
            + "group by c.\"state_province\", c.\"country\"\n" // 按州和国家两个维度分组（复合分组键）
            + "order by c, 1") // 按州和计数排序（c是state_province的别名，1是第一列count(*)）
        .returns("C=78; state_province=Sinaloa\n" // 验证返回结果：按州分组后的客户数量
            + "C=90; state_province=Oaxaca\n" // 注意：虽然按state_province和country分组，但只选择了state_province
            + "C=93; state_province=Veracruz\n" // 这意味着同一个state_province可能有多个country的记录
            + "C=97; state_province=Mexico\n" // 但这里显示的是合并后的结果
            + "C=99; state_province=Yucatan\n" // 排序按state_province升序排列
            + "C=104; state_province=Jalisco\n"
            + "C=106; state_province=Guerrero\n"
            + "C=191; state_province=Zacatecas\n"
            + "C=347; state_province=DF\n"
            + "C=1051; state_province=OR\n"
            + "C=1717; state_province=BC\n"
            + "C=2086; state_province=WA\n"
            + "C=4222; state_province=CA\n");
  }

  @Disabled // 禁用该测试（可能是由于某些已知问题或不稳定性）
  @Test void testDistinctCount() { // 测试方法：DISTINCT COUNT聚合，验证去重计数和复杂SQL生成
    // Complicating factors:
    // Composite GROUP BY key // 复杂因素1：复合GROUP BY键
    // Order by select item, referenced by ordinal // 复杂因素2：按选择项排序，使用序号引用（2表示第二列）
    // Distinct count // 复杂因素3：DISTINCT COUNT去重计数
    // Not all GROUP columns are projected // 复杂因素4：并非所有GROUP列都被投影（country被用于分组但未显示）
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 配置使用FoodMart数据库
        .query("select c.\"state_province\",\n" // 执行带DISTINCT COUNT的复杂查询
            + "  sum(s.\"unit_sales\") as s,\n" // 聚合：计算总销量
            + "  count(distinct c.\"customer_id\") as dc\n" // 聚合：计算不同客户的数量（去重计数）
            + "from \"foodmart\".\"sales_fact_1997\" as s\n" // 左表：销售事实表
            + "  join \"foodmart\".\"customer\" as c\n" // 右表：客户表
            + "  on s.\"customer_id\" = c.\"customer_id\"\n" // 连接条件
            + "group by c.\"state_province\", c.\"country\"\n" // 复合分组：按州和国家分组
            + "order by c.\"state_province\", 2") // 排序：按state_province和销量排序（2表示第二列sum）
        .planHasSql("SELECT `state_province`, `S`, `DC`\n" // 验证生成的SQL包含正确的嵌套查询
            + "FROM (SELECT `customer`.`state_province`, `customer`.`country`, SUM(`sales_fact_1997`.`unit_sales`) AS `S`, COUNT(DISTINCT `customer`.`customer_id`) AS `DC`\n" // 内层查询执行GROUP BY聚合
            + "FROM `foodmart`.`sales_fact_1997`\n"
            + "INNER JOIN `foodmart`.`customer` ON `sales_fact_1997`.`customer_id` = `customer`.`customer_id`\n"
            + "GROUP BY `customer`.`state_province`, `customer`.`country`) AS `t0`\n" // 外层查询执行ORDER BY排序
            + "ORDER BY `state_province`, `S`") // 这种嵌套结构是为了在ORDER BY中使用聚合别名
        .returns("state_province=CA; S=74748.0000; DC=2716\n" // 验证返回结果：CA州销量74748，有2716个不同客户
            + "state_province=OR; S=67659.0000; DC=1037\n" // OR州销量67659，有1037个不同客户
            + "state_province=WA; S=124366.0000; DC=1828\n"); // WA州销量124366，有1828个不同客户
  }

  @Disabled // 禁用该测试
  @Test void testPlan() { // 测试方法：计划验证，检查生成的linq4j代码中的过滤逻辑
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 配置使用FoodMart数据库
        .query("select c.\"state_province\"\n" // 执行简单查询
            + "from \"foodmart\".\"customer\" as c\n" // 从客户表查询
            + "where c.\"state_province\" = 'USA'") // WHERE条件：state_province等于'USA'
        .planContains("            public boolean moveNext() {\n" // 验证生成的linq4j代码包含moveNext方法
            + "              while (inputEnumerator.moveNext()) {\n" // while循环：遍历输入枚举器
            + "                final String v = (String) ((Object[]) inputEnumerator.current())[10];\n" // 获取当前行的第10列（state_province字段）
            + "                if (v != null && org.apache.calcite.runtime.SqlFunctions.eq(v, \"USA\")) {\n" // 检查字段值是否等于'USA'
            + "                  return true;\n" // 如果匹配，返回true（包含该记录）
            + "                }\n" // 否则继续循环
            + "              }\n"
            + "              return false;\n" // 如果没有更多记录，返回false
            + "            }\n"); // 这个测试验证了WHERE条件如何转换为linq4j代码
  }

  @Disabled // 禁用该测试
  @Test void testPlan2() { // 测试方法：复杂计划验证，检查包含函数和GROUP BY的linq4j代码
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.JDBC_FOODMART) // 配置使用FoodMart数据库
        .withDefaultSchema("foodmart") // 设置默认schema为foodmart
        .query("select \"customer\".\"state_province\" as \"c0\",\n" // 执行复杂查询
            + " \"customer\".\"country\" as \"c1\"\n"
            + "from \"customer\" as \"customer\"\n"
            + "where (\"customer\".\"country\" = 'USA')\n" // WHERE条件1：country等于'USA'
            + "and UPPER(\"customer\".\"state_province\") = UPPER('CA')\n" // WHERE条件2：state_province转为大写后等于'CA'
            + "group by \"customer\".\"state_province\", \"customer\".\"country\"\n" // GROUP BY：按state_province和country分组
            + "order by \"customer\".\"state_province\" ASC") // ORDER BY：按state_province升序排列
        .planContains("          public boolean moveNext() {\n" // 验证生成的linq4j代码
            + "            while (inputEnumerator.moveNext()) {\n" // while循环遍历
            + "              final Object[] current12 = (Object[]) inputEnumerator.current();\n" // 获取当前行
            + "              final String v1 = (String) current12[10];\n" // 获取第10列（state_province）
            + "              if (org.apache.calcite.runtime.SqlFunctions.eq((String) current12[12], \"USA\") && (v1 != null && org.apache.calcite.runtime.SqlFunctions.eq(org.apache.calcite.runtime.SqlFunctions.upper(v1), org.apache.calcite.runtime.SqlFunctions.trim(org.apache.calcite.runtime.SqlFunctions.upper(\"CA\"))))) {\n" // 复杂的条件判断：检查country和state_province
            + "                return true;\n" // 注意：UPPER函数会被转换为SqlFunctions.upper调用
            + "              }\n" // 并且会自动添加trim操作
            + "            }\n" // 不匹配则返回false
            + "            return false;\n" // 这个测试验证了字符串函数在linq4j中的实现方式
            + "          }\n");
  }

  @Test void testPlan3() { // 测试方法：JOIN计划验证，确保使用哈希连接而非笛卡尔积
    // Plan should contain 'join'. If it doesn't, maybe int-vs-Integer
    // data type incompatibility has caused it to use a cartesian product
    // instead, and that would be wrong.
    // 
    // 计划应该包含'join'。如果不包含，可能是int与Integer的数据类型不兼容
    // 导致使用了笛卡尔积，这是错误的。
    //
    // inventory_fact_1997 is on the LHS because it is larger than store.
    // inventory_fact_1997在左侧，因为它比store表大（优化器选择大表作为左表以减少哈希表大小）
    that() // 开始构建测试断言
        .with(CalciteAssert.Config.FOODMART_CLONE) // 配置使用FoodMart克隆数据源
        .query( // 执行JOIN查询
            "select \"store\".\"store_country\" as \"c0\", sum(\"inventory_fact_1997\".\"supply_time\") as \"m0\" from \"store\" as \"store\", \"inventory_fact_1997\" as \"inventory_fact_1997\" where \"inventory_fact_1997\".\"store_id\" = \"store\".\"store_id\" group by \"store\".\"store_country\"") // 连接store和inventory_fact_1997表，按store_country分组，计算supply_time总和
        .planContains( // 验证执行计划
            " left.hashJoin(right, new org.apache.calcite.linq4j.function.Function1() {\n"); // 确保使用哈希连接（hashJoin）而不是嵌套循环连接
  } // 哈希连接是等值连接的高效实现方式，使用哈希表来快速匹配记录
} // 测试类结束
