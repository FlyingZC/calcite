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
package org.apache.calcite.materialize;

import org.apache.calcite.materialize.Lattice.Measure;
import org.apache.calcite.prepare.PlannerImpl;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.fun.SqlLibrary;
import org.apache.calcite.sql.fun.SqlLibraryOperatorTableFactory;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.calcite.statistic.MapSqlStatisticProvider;
import org.apache.calcite.statistic.QuerySqlStatisticProvider;
import org.apache.calcite.test.CalciteAssert;
import org.apache.calcite.test.FoodMartQuerySet;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.Planner;
import org.apache.calcite.tools.RelConversionException;
import org.apache.calcite.tools.ValidationException;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.ImmutableBitSet.Builder;
import org.apache.calcite.util.Util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableList.toImmutableList;

import static org.apache.calcite.test.Matchers.isListOf;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasToString;

/**
 * Unit tests for {@link LatticeSuggester}.
 * LatticeSuggesterTest类是LatticeSuggester的单元测试类，用于测试LatticeSuggester的功能
 * LatticeSuggester是Apache Calcite中用于建议物化视图（Materialized Views）的核心组件
 * Lattice（格）是一种数据结构，用于表示表之间的连接关系和聚合操作，是物化视图推荐的基础
 * 该测试类通过多种SQL查询场景验证LatticeSuggester能否正确识别和推荐合适的物化视图结构
 * 主要测试场景包括：
 * 1. 基本的表连接查询模式（如EMP和DEPT表的连接）
 * 2. 复杂的星型模式查询（如FoodMart数据仓库）
 * 3. 聚合表达式处理（包括CASE WHEN等复杂表达式）
 * 4. 自连接查询
 * 5. 共享雪花模式（同一张表被多次引用）
 * 6. 表达式在聚合中的使用
 * 7. 派生列（Derived Column）的处理
 * 8. 不同SQL方言的支持（Redshift、BigQuery等）
 * 9. CTE（公用表表达式）和子查询的处理
 * 10. UNION、INTERSECT、EXCEPT等集合操作
 * 测试类使用内部Tester类来封装测试环境配置和查询执行逻辑
 */
class LatticeSuggesterTest {

  /** Some basic query patterns on the Scott schema with "EMP" and "DEPT"
   * tables. */
  @Test void testEmpDept() throws Exception { // 测试Scott模式中EMP和DEPT表的基本查询模式，验证LatticeSuggester能否正确识别表连接和聚合操作
    final Tester t = new Tester(); // 创建测试器实例，使用默认的Scott模式
    final String q0 = "select dept.dname, count(*), sum(sal)\n" // 构建第一个测试查询q0：查询部门名称、员工数量和薪资总和
        + "from emp\n" // 从emp表
        + "join dept using (deptno)\n" // 使用JOIN语法连接dept表，连接条件是deptno字段
        + "group by dept.dname"; // 按部门名称分组
    assertThat(t.addQuery(q0), // 执行查询并验证结果，期望生成包含EMP(DEPT:DEPTNO)节点和COUNT、SUM聚合的格结构
        isGraphs("EMP (DEPT:DEPTNO)", "[COUNT(), SUM(EMP.SAL)]"));

    // Same as above, but using WHERE rather than JOIN
    final String q1 = "select dept.dname, count(*), sum(sal)\n" // 构建第二个测试查询q1：功能与q0相同，但使用WHERE子句代替JOIN语法
        + "from emp, dept\n" // 使用逗号分隔的表列表（隐式连接）
        + "where emp.deptno = dept.deptno\n" // 在WHERE子句中指定连接条件
        + "group by dept.dname"; // 按部门名称分组
    assertThat(t.addQuery(q1), // 执行查询并验证结果，期望生成与q0相同的格结构，验证LatticeSuggester能识别不同的连接语法
        isGraphs("EMP (DEPT:DEPTNO)", "[COUNT(), SUM(EMP.SAL)]"));

    // With HAVING
    final String q2 = "select dept.dname\n" // 构建第三个测试查询q2：包含HAVING子句的查询
        + "from emp, dept\n" // 从emp和dept表
        + "where emp.deptno = dept.deptno\n" // 连接条件
        + "group by dept.dname\n" // 按部门名称分组
        + "having count(*) > 10"; // 使用HAVING子句过滤分组，只保留员工数大于10的部门
    assertThat(t.addQuery(q2), // 执行查询并验证结果，期望生成包含COUNT聚合的格结构，验证HAVING子句的正确处理
        isGraphs("EMP (DEPT:DEPTNO)", "[COUNT()]"));

    // No joins, therefore graph has a single node and no edges
    final String q3 = "select distinct dname\n" // 构建第四个测试查询q3：没有连接的单表查询
        + "from dept"; // 只查询dept表的部门名称
    assertThat(t.addQuery(q3), // 执行查询并验证结果，期望生成单个DEPT节点且无聚合的格结构
        isGraphs("DEPT", "[]"));

    // Graph is empty because there are no tables
    final String q4 = "select distinct t.c\n" // 构建第五个测试查询q4：使用VALUES子句创建临时数据，不涉及真实表
        + "from (values 1, 2) as t(c)" // 第一个VALUES子句创建临时表t，包含值1和2
        + "join (values 2, 3) as u(c) using (c)\n"; // 第二个VALUES子句创建临时表u，包含值2和3，使用c字段连接
    assertThat(t.addQuery(q4), // 执行查询并验证结果，期望生成空的格结构，因为没有真实表参与
        isGraphs());

    // Self-join
    final String q5 = "select *\n" // 构建第六个测试查询q5：EMP表的自连接查询
        + "from emp as e\n" // 将emp表别名为e（员工）
        + "join emp as m on e.mgr = m.empno"; // 将emp表别名为m（经理），通过mgr字段连接员工和经理
    assertThat(t.addQuery(q5), // 执行查询并验证结果，期望生成包含EMP(EMP:MGR)自连接的格结构
        isGraphs("EMP (EMP:MGR)", "[]"));

    // Self-join, twice
    final String q6 = "select *\n" // 构建第七个测试查询q6：EMP表的两次自连接查询（三级连接）
        + "from emp as e join emp as m on e.mgr = m.empno\n" // 第一级自连接：员工e连接到经理m
        + "join emp as m2 on m.mgr = m2.empno"; // 第二级自连接：经理m连接到上级经理m2
    assertThat(t.addQuery(q6), // 执行查询并验证结果，期望生成包含EMP(EMP:MGR (EMP:MGR))嵌套自连接的格结构
        isGraphs("EMP (EMP:MGR (EMP:MGR))", "[]"));

    // No graphs, because cyclic: e -> m, m -> m2, m2 -> e
    final String q7 = "select *\n" // 构建第八个测试查询q7：形成循环的自连接查询
        + "from emp as e\n" // 员工表别名为e
        + "join emp as m on e.mgr = m.empno\n" // e连接到m
        + "join emp as m2 on m.mgr = m2.empno\n" // m连接到m2
        + "where m2.mgr = e.empno"; // 添加条件使m2连接回e，形成循环e->m->m2->e
    assertThat(t.addQuery(q7), // 执行查询并验证结果，期望生成空的格结构，因为循环连接无法形成有效的格
        isGraphs());

    // The graph of all tables and hops
    final String expected = "graph(" // 构建期望的完整图结构字符串，包含所有表和边
        + "vertices: [[scott, DEPT]," // 顶点包含DEPT表
        + " [scott, EMP]], " // 顶点包含EMP表
        + "edges: [Step([scott, EMP], [scott, DEPT], DEPTNO:DEPTNO)," // 边：EMP通过DEPTNO连接到DEPT
        + " Step([scott, EMP], [scott, EMP], MGR:EMPNO)])"; // 边：EMP通过MGR自连接到EMP
    assertThat(t.s.space.g, hasToString(expected)); // 验证空间图的完整结构是否与期望一致
  }

  @Test void testFoodmart() throws Exception { // 测试FoodMart数据仓库的星型模式查询，验证LatticeSuggester对复杂星型模式的处理能力
    final Tester t = new Tester().foodmart(); // 创建测试器实例，使用FoodMart数据仓库模式
    final String q = "select \"t\".\"the_year\" as \"c0\",\n" // 构建测试查询：查询1997年各季度各产品家族的销售量总和
        + " \"t\".\"quarter\" as \"c1\",\n" // 选择季度
        + " \"pc\".\"product_family\" as \"c2\",\n" // 选择产品家族
        + " sum(\"s\".\"unit_sales\") as \"m0\"\n" // 计算销售量总和
        + "from \"time_by_day\" as \"t\",\n" // 时间维度表别名为t
        + " \"sales_fact_1997\" as \"s\",\n" // 1997年销售事实表别名为s
        + " \"product_class\" as \"pc\",\n" // 产品分类表别名为pc
        + " \"product\" as \"p\"\n" // 产品表别名为p
        + "where \"s\".\"time_id\" = \"t\".\"time_id\"\n" // 连接条件：事实表通过time_id连接时间维度表
        + "and \"t\".\"the_year\" = 1997\n" // 过滤条件：只查询1997年的数据
        + "and \"s\".\"product_id\" = \"p\".\"product_id\"\n" // 连接条件：事实表通过product_id连接产品表
        + "and \"p\".\"product_class_id\" = \"pc\".\"product_class_id\"\n" // 连接条件：产品表通过product_class_id连接产品分类表
        + "group by \"t\".\"the_year\",\n" // 按年份分组
        + " \"t\".\"quarter\",\n" // 按季度分组
        + " \"pc\".\"product_family\""; // 按产品家族分组
    final String g = "sales_fact_1997" // 构建期望的格结构字符串
        + " (product:product_id (product_class:product_class_id)" // 产品表通过product_id连接，产品表再通过product_class_id连接产品分类表
        + " time_by_day:time_id)"; // 时间维度表通过time_id连接
    assertThat(t.addQuery(q), // 执行查询并验证结果，期望生成符合星型模式的格结构
        isGraphs(g, "[SUM(sales_fact_1997.unit_sales)]"));

    // The graph of all tables and hops
    final String expected = "graph(" // 构建期望的完整图结构字符串
        + "vertices: [" // 顶点列表
        + "[foodmart, product], " // 产品表
        + "[foodmart, product_class], " // 产品分类表
        + "[foodmart, sales_fact_1997], " // 销售事实表
        + "[foodmart, time_by_day]], " // 时间维度表
        + "edges: [" // 边列表
        + "Step([foodmart, product], [foodmart, product_class]," // 边：产品表通过product_class_id连接到产品分类表
        + " product_class_id:product_class_id), "
        + "Step([foodmart, sales_fact_1997], [foodmart, product]," // 边：销售事实表通过product_id连接到产品表
        + " product_id:product_id), "
        + "Step([foodmart, sales_fact_1997], [foodmart, time_by_day]," // 边：销售事实表通过time_id连接到时间维度表
        + " time_id:time_id)])";
    assertThat(t.s.space.g, hasToString(expected)); // 验证空间图的完整结构是否与期望一致
  }

  @Test void testAggregateExpression() throws Exception { // 测试包含复杂聚合表达式（CASE WHEN）的查询，验证LatticeSuggester对派生列的处理能力
    final Tester t = new Tester().foodmart(); // 创建测试器实例，使用FoodMart数据仓库模式
    final String q = "select \"t\".\"the_year\" as \"c0\",\n" // 构建测试查询：包含CASE WHEN表达式的聚合查询
        + " \"pc\".\"product_family\" as \"c1\",\n" // 选择产品家族
        + " sum((case when \"s\".\"promotion_id\" = 0\n" // 使用CASE WHEN表达式计算聚合：如果促销ID为0则返回0，否则返回店铺销售额
        + "     then 0 else \"s\".\"store_sales\"\n"
        + "     end)) as \"sum_m0\"\n" // 将聚合结果别名为sum_m0
        + "from \"time_by_day\" as \"t\",\n" // 时间维度表
        + " \"sales_fact_1997\" as \"s\",\n" // 销售事实表
        + " \"product_class\" as \"pc\",\n" // 产品分类表
        + " \"product\" as \"p\"\n" // 产品表
        + "where \"s\".\"time_id\" = \"t\".\"time_id\"\n" // 连接条件
        + " and \"t\".\"the_year\" = 1997\n" // 过滤条件：1997年
        + " and \"s\".\"product_id\" = \"p\".\"product_id\"\n" // 连接条件
        + " and \"p\".\"product_class_id\" = \"pc\".\"product_class_id\"\n" // 连接条件
        + "group by \"t\".\"the_year\",\n" // 按年份分组
        + " \"pc\".\"product_family\"\n"; // 按产品家族分组
    final String g = "sales_fact_1997" // 构建期望的格结构字符串
        + " (product:product_id (product_class:product_class_id)" // 星型模式结构
        + " time_by_day:time_id)";
    final String expected = "[SUM(m0)]"; // 期望的聚合列表，包含派生列m0的SUM聚合
    assertThat(t.addQuery(q), // 执行查询并验证结果，使用多个匹配器验证格结构、度量名称和派生列名称
        allOf(isGraphs(g, expected), // 验证格结构
            hasMeasureNames(0, "sum_m0"), // 验证第一个格的度量名称为sum_m0
            hasDerivedColumnNames(0, "m0"))); // 验证第一个格的派生列名称为m0
  }

  private Matcher<List<Lattice>> hasMeasureNames(int ordinal, // 创建一个Hamcrest匹配器，用于验证指定索引的格（Lattice）的度量名称列表
      String... names) { // 参数ordinal表示格的索引，names表示期望的度量名称数组
    final List<String> nameList = ImmutableList.copyOf(names); // 将期望的度量名称数组转换为不可变列表
    return new TypeSafeMatcher<List<Lattice>>() { // 创建类型安全的匹配器，用于匹配格列表
      public void describeTo(Description description) { // 描述匹配失败时的期望值
        description.appendValue(names); // 将期望的度量名称添加到描述中
      }

      protected boolean matchesSafely(List<Lattice> lattices) { // 执行实际的匹配逻辑
        final Lattice lattice = lattices.get(ordinal); // 获取指定索引的格对象
        final List<String> actualNameList = // 提取格的实际度量名称列表
            Util.transform(lattice.defaultMeasures, measure -> measure.name); // 将度量对象转换为其名称
        return actualNameList.equals(nameList); // 比较实际度量名称列表与期望列表是否相等
      }
    };
  }

  private Matcher<List<Lattice>> hasDerivedColumnNames(int ordinal, // 创建一个Hamcrest匹配器，用于验证指定索引的格（Lattice）的派生列名称列表
      String... names) { // 参数ordinal表示格的索引，names表示期望的派生列名称数组
    final List<String> nameList = ImmutableList.copyOf(names); // 将期望的派生列名称数组转换为不可变列表
    return new TypeSafeMatcher<List<Lattice>>() { // 创建类型安全的匹配器，用于匹配格列表
      public void describeTo(Description description) { // 描述匹配失败时的期望值
        description.appendValue(names); // 将期望的派生列名称添加到描述中
      }

      protected boolean matchesSafely(List<Lattice> lattices) { // 执行实际的匹配逻辑
        final Lattice lattice = lattices.get(ordinal); // 获取指定索引的格对象
        final List<String> actualNameList = // 提取格的实际派生列名称列表
            lattice.columns.stream() // 获取格的所有列的流
                .filter(c -> c instanceof Lattice.DerivedColumn) // 过滤出派生列
                .map(c -> ((Lattice.DerivedColumn) c).alias) // 将派生列转换为其别名
                .collect(Collectors.toList()); // 收集为列表
        return actualNameList.equals(nameList); // 比较实际派生列名称列表与期望列表是否相等
      }
    };
  }

  @Tag("slow") // 标记为慢速测试，因为涉及复杂的共享雪花模式查询
  @Test void testSharedSnowflake() throws Exception { // 测试共享雪花模式，即同一张表（region）被多次引用的情况
    final Tester t = new Tester().foodmart(); // 创建测试器实例，使用FoodMart数据仓库模式
    // foodmart query 5827 (also 5828, 5830, 5832) uses the "region" table
    // twice: once via "store" and once via "customer";
    // TODO: test what happens if FK from "store" to "region" is reversed
    final String q = "select \"s\".\"store_country\" as \"c0\",\n" // 构建测试查询：region表被引用两次的共享雪花模式查询
        + " \"r\".\"sales_region\" as \"c1\",\n" // 选择第一个region表的 sales_region（通过store连接）
        + " \"r1\".\"sales_region\" as \"c2\",\n" // 选择第二个region表的 sales_region（通过customer连接）
        + " sum(\"f\".\"unit_sales\") as \"m0\"\n" // 计算销售量总和
        + "from \"store\" as \"s\",\n" // 店铺表
        + " \"sales_fact_1997\" as \"f\",\n" // 销售事实表
        + " \"region\" as \"r\",\n" // 第一个region表别名r（连接到store）
        + " \"region\" as \"r1\",\n" // 第二个region表别名r1（连接到customer）
        + " \"customer\" as \"c\"\n" // 客户表
        + "where \"f\".\"store_id\" = \"s\".\"store_id\"\n" // 连接条件：事实表连接到店铺
        + " and \"s\".\"store_country\" = 'USA'\n" // 过滤条件：只查询美国的店铺
        + " and \"s\".\"region_id\" = \"r\".\"region_id\"\n" // 连接条件：店铺连接到第一个region表
        + " and \"r\".\"sales_region\" = 'South West'\n" // 过滤条件：只查询西南地区的店铺
        + " and \"f\".\"customer_id\" = \"c\".\"customer_id\"\n" // 连接条件：事实表连接到客户
        + " and \"c\".\"customer_region_id\" = \"r1\".\"region_id\"\n" // 连接条件：客户连接到第二个region表
        + " and \"r1\".\"sales_region\" = 'South West'\n" // 过滤条件：只查询西南地区的客户
        + "group by \"s\".\"store_country\",\n" // 按店铺国家分组
        + " \"r\".\"sales_region\",\n" // 按第一个region的销售区域分组
        + " \"r1\".\"sales_region\"\n"; // 按第二个region的销售区域分组
    final String g = "sales_fact_1997" // 构建期望的格结构字符串
        + " (customer:customer_id (region:customer_region_id)" // 客户连接到region
        + " store:store_id (region:region_id))"; // 店铺连接到region
    assertThat(t.addQuery(q), // 执行查询并验证结果，验证共享雪花模式的正确处理
        isGraphs(g, "[SUM(sales_fact_1997.unit_sales)]"));
  }

  @Test void testExpressionInAggregate() throws Exception { // 测试聚合函数中包含表达式的查询，验证LatticeSuggester对复杂聚合表达式的处理
    final Tester t = new Tester().withEvolve(true).foodmart(); // 创建测试器实例，启用格演化功能，使用FoodMart模式
    final FoodMartQuerySet set = FoodMartQuerySet.instance(); // 获取FoodMart查询集合实例
    for (int id : new int[]{392, 393}) { // 遍历查询ID列表392和393
      t.addQuery(set.queries.get(id).sql); // 从查询集合中获取指定ID的SQL查询并添加到测试器中
    }
  }

  private void checkFoodMartAll(boolean evolve) throws Exception { // 测试FoodMart数据仓库的所有查询，验证LatticeSuggester的完整功能
    final Tester t = new Tester().sqlToRelConverter(config -> config.withExpand(true)) // 创建测试器实例，配置SQL到关系代数转换器启用展开
        .foodmart() // 使用FoodMart数据仓库模式
        .withEvolve(evolve); // 设置是否启用格演化功能
    final FoodMartQuerySet set = FoodMartQuerySet.instance(); // 获取FoodMart查询集合实例
    for (FoodMartQuerySet.FoodmartQuery query : set.queries.values()) { // 遍历查询集合中的所有查询
      if (query.sql.contains("\"agg_10_foo_fact\"") // 跳过包含特定表名的查询（这些表可能不存在或不适合测试）
          || query.sql.contains("\"agg_line_class\"")
          || query.sql.contains("\"agg_tenant\"")
          || query.sql.contains("\"line\"")
          || query.sql.contains("\"line_class\"")
          || query.sql.contains("\"tenant\"")
          || query.sql.contains("\"test_lp_xxx_fact\"")
          || query.sql.contains("\"product_csv\"")
          || query.sql.contains("\"product_cat\"")
          || query.sql.contains("\"cat\"")
          || query.sql.contains("\"fact\"")) {
        continue; // 跳过不合适的查询
      }
      switch (query.id) { // 根据查询ID跳过特定查询
      case 2455: // missing RTRIM function - 缺少RTRIM函数
      case 2456: // missing RTRIM function - 缺少RTRIM函数
      case 2457: // missing RTRIM function - 缺少RTRIM函数
      case 5682: // case sensitivity - 大小写敏感问题
      case 5700: // || applied to smallint - 字符串连接符应用于smallint类型
        continue; // 跳过这些查询
      default:
        t.addQuery(query.sql); // 添加查询到测试器
      }
    }

    // The graph of all tables and hops
    final String expected = "graph(" // 构建期望的完整图结构字符串，包含所有表和边
        + "vertices: [" // 顶点列表，包含所有FoodMart表
        + "[foodmart, agg_c_10_sales_fact_1997], " // 聚合表
        + "[foodmart, agg_c_14_sales_fact_1997], "
        + "[foodmart, agg_c_special_sales_fact_1997], "
        + "[foodmart, agg_g_ms_pcat_sales_fact_1997], "
        + "[foodmart, agg_l_03_sales_fact_1997], "
        + "[foodmart, agg_l_04_sales_fact_1997], "
        + "[foodmart, agg_l_05_sales_fact_1997], "
        + "[foodmart, agg_lc_06_sales_fact_1997], "
        + "[foodmart, agg_lc_100_sales_fact_1997], "
        + "[foodmart, agg_ll_01_sales_fact_1997], "
        + "[foodmart, agg_pl_01_sales_fact_1997], "
        + "[foodmart, customer], " // 客户表
        + "[foodmart, department], " // 部门表
        + "[foodmart, employee], " // 员工表
        + "[foodmart, employee_closure], " // 员工闭包表
        + "[foodmart, inventory_fact_1997], " // 库存事实表
        + "[foodmart, position], " // 职位表
        + "[foodmart, product], " // 产品表
        + "[foodmart, product_class], " // 产品分类表
        + "[foodmart, promotion], " // 促销表
        + "[foodmart, region], " // 区域表
        + "[foodmart, salary], " // 薪资表
        + "[foodmart, sales_fact_1997], " // 销售事实表
        + "[foodmart, store], " // 店铺表
        + "[foodmart, store_ragged], " // 店铺不规则表
        + "[foodmart, time_by_day], " // 时间维度表
        + "[foodmart, warehouse], " // 仓库表
        + "[foodmart, warehouse_class]], " // 仓库分类表
        + "edges: [" // 边列表，包含所有表之间的连接关系
        + "Step([foodmart, agg_c_14_sales_fact_1997], [foodmart, store], store_id:store_id), "
        + "Step([foodmart, customer], [foodmart, region], customer_region_id:region_id), "
        + "Step([foodmart, employee], [foodmart, employee], supervisor_id:employee_id), "
        + "Step([foodmart, employee], [foodmart, position], position_id:position_id), "
        + "Step([foodmart, employee], [foodmart, store], store_id:store_id), "
        + "Step([foodmart, inventory_fact_1997], [foodmart, employee], product_id:employee_id), "
        + "Step([foodmart, inventory_fact_1997], [foodmart, employee], time_id:employee_id), "
        + "Step([foodmart, inventory_fact_1997], [foodmart, product], product_id:product_id), "
        + "Step([foodmart, inventory_fact_1997], [foodmart, store], store_id:store_id), "
        + "Step([foodmart, inventory_fact_1997], [foodmart, store], warehouse_id:store_id), "
        + "Step([foodmart, inventory_fact_1997], [foodmart, time_by_day], time_id:time_id), "
        + "Step([foodmart, inventory_fact_1997], [foodmart, warehouse],"
        + " warehouse_id:warehouse_id), "
        + "Step([foodmart, product], [foodmart, product_class],"
        + " product_class_id:product_class_id), "
        + "Step([foodmart, product], [foodmart, store], product_class_id:store_id), "
        + "Step([foodmart, salary], [foodmart, department], department_id:department_id), "
        + "Step([foodmart, salary], [foodmart, employee], employee_id:employee_id), "
        + "Step([foodmart, salary], [foodmart, employee_closure], employee_id:employee_id), "
        + "Step([foodmart, salary], [foodmart, time_by_day], pay_date:the_date), "
        + "Step([foodmart, sales_fact_1997], [foodmart, customer], customer_id:customer_id), "
        + "Step([foodmart, sales_fact_1997], [foodmart, customer], product_id:customer_id), "
        + "Step([foodmart, sales_fact_1997], [foodmart, customer], store_id:customer_id), "
        + "Step([foodmart, sales_fact_1997], [foodmart, product], product_id:product_id), "
        + "Step([foodmart, sales_fact_1997], [foodmart, promotion], promotion_id:promotion_id), "
        + "Step([foodmart, sales_fact_1997], [foodmart, store], product_id:store_id), "
        + "Step([foodmart, sales_fact_1997], [foodmart, store], store_id:store_id), "
        + "Step([foodmart, sales_fact_1997], [foodmart, store_ragged], store_id:store_id), "
        + "Step([foodmart, sales_fact_1997], [foodmart, time_by_day], product_id:time_id), "
        + "Step([foodmart, sales_fact_1997], [foodmart, time_by_day], time_id:time_id), "
        + "Step([foodmart, store], [foodmart, customer], store_state:state_province), "
        + "Step([foodmart, store], [foodmart, product_class], region_id:product_class_id), "
        + "Step([foodmart, store], [foodmart, region], region_id:region_id), "
        + "Step([foodmart, time_by_day], [foodmart, agg_c_14_sales_fact_1997], month_of_year:month_of_year), "
        + "Step([foodmart, warehouse], [foodmart, store], stores_id:store_id), "
        + "Step([foodmart, warehouse], [foodmart, warehouse_class],"
        + " warehouse_class_id:warehouse_class_id)])";
    assertThat(t.s.space.g, hasToString(expected)); // 验证空间图的完整结构是否与期望一致
    if (evolve) { // 如果启用了格演化功能
      // compared to evolve=false, there are a few more nodes (137 vs 119),
      // the same number of paths, and a lot fewer lattices (27 vs 388)
      assertThat(t.s.space.nodeMap, aMapWithSize(137)); // 验证节点映射包含137个节点（比未启用演化时多18个）
      assertThat(t.s.latticeMap, aMapWithSize(27)); // 验证格映射包含27个格（比未启用演化时少361个，因为格被合并了）
      assertThat(t.s.space.pathMap, aMapWithSize(46)); // 验证路径映射包含46条路径（与未启用演化时相同）
    } else { // 如果未启用格演化功能
      assertThat(t.s.space.nodeMap, aMapWithSize(119)); // 验证节点映射包含119个节点
      assertThat(t.s.latticeMap, aMapWithSize(388)); // 验证格映射包含388个格（每个查询可能生成独立的格）
      assertThat(t.s.space.pathMap, aMapWithSize(46)); // 验证路径映射包含46条路径
    }
  }

  @Tag("slow")
  @Test void testFoodMartAll() throws Exception {
    checkFoodMartAll(false);
  }

  @Tag("slow")
  @Test void testFoodMartAllEvolve() throws Exception {
    checkFoodMartAll(true);
  }

  @Test void testContains() throws Exception { // 测试LatticeRootNode的contains方法，验证节点包含关系的判断逻辑
    final Tester t = new Tester().foodmart(); // 创建测试器实例，使用FoodMart模式
    final LatticeRootNode fNode = t.node("select *\n" // 创建只包含sales_fact_1997表的节点
        + "from \"sales_fact_1997\"");
    final LatticeRootNode fcNode = t.node("select *\n" // 创建包含sales_fact_1997和customer表的节点
        + "from \"sales_fact_1997\"\n"
        + "join \"customer\" using (\"customer_id\")");
    final LatticeRootNode fcpNode = t.node("select *\n" // 创建包含sales_fact_1997、customer和product表的节点
        + "from \"sales_fact_1997\"\n"
        + "join \"customer\" using (\"customer_id\")\n"
        + "join \"product\" using (\"product_id\")");
    assertThat(fNode.contains(fNode), is(true)); // 验证fNode包含自身
    assertThat(fNode.contains(fcNode), is(false)); // 验证fNode不包含fcNode（fcNode有更多表）
    assertThat(fNode.contains(fcpNode), is(false)); // 验证fNode不包含fcpNode（fcpNode有更多表）
    assertThat(fcNode.contains(fNode), is(true)); // 验证fcNode包含fNode（fcNode是fNode的超集）
    assertThat(fcNode.contains(fcNode), is(true)); // 验证fcNode包含自身
    assertThat(fcNode.contains(fcpNode), is(false)); // 验证fcNode不包含fcpNode（fcpNode有更多表）
    assertThat(fcpNode.contains(fNode), is(true)); // 验证fcpNode包含fNode（fcpNode是fNode的超集）
    assertThat(fcpNode.contains(fcNode), is(true)); // 验证fcpNode包含fcNode（fcpNode是fcNode的超集）
    assertThat(fcpNode.contains(fcpNode), is(true)); // 验证fcpNode包含自身
  }

  @Test void testEvolve() throws Exception { // 测试格演化功能，验证多个查询能否合并到同一个格中
    final Tester t = new Tester().foodmart().withEvolve(true); // 创建测试器实例，启用格演化功能

    final String q0 = "select count(*)\n" // 第一个查询：只查询sales_fact_1997表的记录数
        + "from \"sales_fact_1997\"";
    final String l0 = "sales_fact_1997:[COUNT()]"; // 期望的格结构：包含sales_fact_1997表和COUNT聚合
    t.addQuery(q0); // 添加查询到测试器
    assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射包含1个格
    assertThat(Iterables.getOnlyElement(t.s.latticeMap.keySet()), // 验证格的键与期望一致
        is(l0));

    final String q1 = "select sum(\"unit_sales\")\n" // 第二个查询：查询sales_fact_1997和customer表，按城市分组
        + "from \"sales_fact_1997\"\n"
        + "join \"customer\" using (\"customer_id\")\n"
        + "group by \"customer\".\"city\"";
    final String l1 = "sales_fact_1997 (customer:customer_id)" // 期望的格结构：包含customer表连接和两个聚合
        + ":[COUNT(), SUM(sales_fact_1997.unit_sales)]";
    t.addQuery(q1); // 添加查询到测试器
    assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射仍包含1个格（格被演化合并）
    assertThat(Iterables.getOnlyElement(t.s.latticeMap.keySet()), // 验证格的键与期望一致（已演化）
        is(l1));

    final String q2 = "select count(distinct \"the_day\")\n" // 第三个查询：查询sales_fact_1997、time_by_day和product表
        + "from \"sales_fact_1997\"\n"
        + "join \"time_by_day\" using (\"time_id\")\n"
        + "join \"product\" using (\"product_id\")";
    final String l2 = "sales_fact_1997" // 期望的格结构：包含三个维度表和三个聚合
        + " (customer:customer_id product:product_id time_by_day:time_id)"
        + ":[COUNT(), SUM(sales_fact_1997.unit_sales),"
        + " COUNT(DISTINCT time_by_day.the_day)]";
    t.addQuery(q2); // 添加查询到测试器
    assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射仍包含1个格（格继续演化）
    assertThat(Iterables.getOnlyElement(t.s.latticeMap.keySet()), // 验证格的键与期望一致（已演化）
        is(l2));

    final Lattice lattice = Iterables.getOnlyElement(t.s.latticeMap.values()); // 获取唯一的格对象
    final List<List<String>> tableNames = // 提取格中的所有表名
        lattice.tables().stream().map(table -> // 遍历格的所有表
            table.t.getQualifiedName()) // 获取表的限定名称
            .sorted(Comparator.comparing(Object::toString)) // 按字符串排序
            .collect(toImmutableList()); // 收集为不可变列表
    assertThat(tableNames, // 验证表名列表与期望一致
        hasToString("[[foodmart, customer],"
            + " [foodmart, product],"
            + " [foodmart, sales_fact_1997],"
            + " [foodmart, time_by_day]]"));

    final String q3 = "select min(\"product\".\"product_id\")\n" // 第四个查询：查询sales_fact_1997、product和product_class表
        + "from \"sales_fact_1997\"\n"
        + "join \"product\" using (\"product_id\")\n"
        + "join \"product_class\" as pc using (\"product_class_id\")\n"
        + "group by pc.\"product_department\"";
    final String l3 = "sales_fact_1997" // 期望的格结构：包含四个维度表和四个聚合
        + " (customer:customer_id product:product_id"
        + " (product_class:product_class_id) time_by_day:time_id)"
        + ":[COUNT(), SUM(sales_fact_1997.unit_sales),"
        + " MIN(product.product_id), COUNT(DISTINCT time_by_day.the_day)]";
    t.addQuery(q3); // 添加查询到测试器
    assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射仍包含1个格（格继续演化）
    assertThat(Iterables.getOnlyElement(t.s.latticeMap.keySet()), // 验证格的键与期望一致（已演化）
        is(l3));
  }

  @Test void testExpression() throws Exception { // 测试表达式的处理，验证派生列的创建和管理
    final Tester t = new Tester().foodmart().withEvolve(true); // 创建测试器实例，启用格演化功能

    final String q0 = "select\n" // 构建测试查询：包含字符串连接和算术表达式
        + "  \"fname\" || ' ' || \"lname\" as \"full_name\",\n" // 字符串连接表达式，别名为full_name
        + "  count(*) as c,\n" // 计数聚合
        + "  avg(\"total_children\" - \"num_children_at_home\")\n" // 算术表达式的平均值聚合
        + "from \"customer\"\n" // 从customer表
        + "group by \"fname\", \"lname\""; // 按名和姓分组
    final String l0 = "customer:[COUNT(), AVG($f2)]"; // 期望的格结构：包含COUNT和AVG聚合，$f2是内部生成的派生列名
    t.addQuery(q0); // 添加查询到测试器
    assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射包含1个格
    assertThat(Iterables.getOnlyElement(t.s.latticeMap.keySet()), // 验证格的键与期望一致
        is(l0));
    final Lattice lattice = Iterables.getOnlyElement(t.s.latticeMap.values()); // 获取唯一的格对象
    final List<Lattice.DerivedColumn> derivedColumns = lattice.columns.stream() // 提取格中的所有派生列
        .filter(c -> c instanceof Lattice.DerivedColumn) // 过滤出派生列
        .map(c -> (Lattice.DerivedColumn) c) // 转换为派生列类型
        .collect(Collectors.toList()); // 收集为列表
    assertThat(derivedColumns, hasSize(2)); // 验证派生列数量为2个
    final List<String> tables = ImmutableList.of("customer"); // 创建表名列表
    checkDerivedColumn(lattice, tables, derivedColumns, 0, "$f2", true); // 验证第一个派生列：名称为$f2，总是度量
    checkDerivedColumn(lattice, tables, derivedColumns, 1, "full_name", false); // 验证第二个派生列：名称为full_name，不总是度量
  }

  /** As {@link #testExpression()} but with multiple queries.
   * Some expressions are measures in one query and dimensions in another. */
  @Test void testExpressionEvolution() throws Exception { // 测试表达式的演化，验证同一表达式在不同查询中既可以是度量也可以是维度
    final Tester t = new Tester().foodmart().withEvolve(true); // 创建测试器实例，启用格演化功能

    // q0 uses n10 as a measure, n11 as a measure, n12 as a dimension
    final String q0 = "select\n" // 第一个查询：n10和n11作为度量，n12作为维度
        + "  \"num_children_at_home\" + 12 as \"n12\",\n" // n12作为维度（在GROUP BY中）
        + "  sum(\"num_children_at_home\" + 10) as \"n10\",\n" // n10作为度量（在SUM中）
        + "  sum(\"num_children_at_home\" + 11) as \"n11\",\n" // n11作为度量（在SUM中）
        + "  count(*) as c\n"
        + "from \"customer\"\n"
        + "group by \"num_children_at_home\" + 12"; // 按n12分组
    // q1 uses n10 as a dimension, n12 as a measure
    final String q1 = "select\n" // 第二个查询：n10作为维度，n12作为度量
        + "  \"num_children_at_home\" + 10 as \"n10\",\n" // n10作为维度（在GROUP BY中）
        + "  \"num_children_at_home\" + 14 as \"n14\",\n" // n14作为维度（在GROUP BY中）
        + "  sum(\"num_children_at_home\" + 12) as \"n12\",\n" // n12作为度量（在SUM中）
        + "  sum(\"num_children_at_home\" + 13) as \"n13\"\n" // n13作为度量（在SUM中）
        + "from \"customer\"\n"
        + "group by \"num_children_at_home\" + 10," // 按n10和n14分组
        + "   \"num_children_at_home\" + 14";
    // n10 = [measure, dimension] -> not always measure
    // n11 = [measure, _] -> always measure
    // n12 = [dimension, measure] -> not always measure
    // n13 = [_, measure] -> always measure
    // n14 = [_, dimension] -> not always measure
    t.addQuery(q0); // 添加第一个查询
    t.addQuery(q1); // 添加第二个查询
    assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射包含1个格（两个查询合并到一个格）
    final String l0 = // 期望的格结构：包含5个聚合
        "customer:[COUNT(), SUM(n10), SUM(n11), SUM(n12), SUM(n13)]";
    assertThat(Iterables.getOnlyElement(t.s.latticeMap.keySet()), // 验证格的键与期望一致
        is(l0));
    final Lattice lattice = Iterables.getOnlyElement(t.s.latticeMap.values()); // 获取唯一的格对象
    final List<Lattice.DerivedColumn> derivedColumns = lattice.columns.stream() // 提取格中的所有派生列
        .filter(c -> c instanceof Lattice.DerivedColumn) // 过滤出派生列
        .map(c -> (Lattice.DerivedColumn) c) // 转换为派生列类型
        .collect(Collectors.toList()); // 收集为列表
    assertThat(derivedColumns, hasSize(5)); // 验证派生列数量为5个
    final List<String> tables = ImmutableList.of("customer"); // 创建表名列表

    checkDerivedColumn(lattice, tables, derivedColumns, 0, "n10", false); // 验证n10：不总是度量（既是度量又是维度）
    checkDerivedColumn(lattice, tables, derivedColumns, 1, "n11", true); // 验证n11：总是度量（只在第一个查询中作为度量）
    checkDerivedColumn(lattice, tables, derivedColumns, 2, "n12", false); // 验证n12：不总是度量（既是维度又是度量）
    checkDerivedColumn(lattice, tables, derivedColumns, 3, "n13", true); // 验证n13：总是度量（只在第二个查询中作为度量）
    checkDerivedColumn(lattice, tables, derivedColumns, 4, "n14", false); // 验证n14：不总是度量（只在第二个查询中作为维度）
  }

  private void checkDerivedColumn(Lattice lattice, List<String> tables, // 验证派生列的属性是否与期望一致
      List<Lattice.DerivedColumn> derivedColumns, // 派生列列表
      int index, String name, boolean alwaysMeasure) { // 参数：格对象、表名列表、派生列列表、索引、期望的名称、期望的是否总是度量
    final Lattice.DerivedColumn dc0 = derivedColumns.get(index); // 获取指定索引的派生列
    assertThat(dc0.tables, is(tables)); // 验证派生列的表名列表与期望一致
    assertThat(dc0.alias, is(name)); // 验证派生列的别名与期望一致
    assertThat(lattice.isAlwaysMeasure(dc0), is(alwaysMeasure)); // 验证派生列是否总是度量与期望一致
  }

  @Test void testExpressionInJoin() throws Exception { // 测试连接查询中的表达式，验证派生列在连接场景下的正确处理
    final Tester t = new Tester().foodmart().withEvolve(true); // 创建测试器实例，启用格演化功能

    final String q0 = "select\n" // 构建测试查询：包含连接和表达式
        + "  \"fname\" || ' ' || \"lname\" as \"full_name\",\n" // 字符串连接表达式
        + "  count(*) as c,\n" // 计数聚合
        + "  avg(\"total_children\" - \"num_children_at_home\")\n" // 算术表达式的平均值聚合
        + "from \"customer\" join \"sales_fact_1997\" using (\"customer_id\")\n" // 连接customer和sales_fact_1997表
        + "group by \"fname\", \"lname\""; // 按名和姓分组
    final String l0 = "sales_fact_1997 (customer:customer_id)" // 期望的格结构：包含customer连接和两个聚合
        + ":[COUNT(), AVG($f2)]";
    t.addQuery(q0); // 添加查询到测试器
    assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射包含1个格
    assertThat(Iterables.getOnlyElement(t.s.latticeMap.keySet()), // 验证格的键与期望一致
        is(l0));
    final Lattice lattice = Iterables.getOnlyElement(t.s.latticeMap.values()); // 获取唯一的格对象
    final List<Lattice.DerivedColumn> derivedColumns = lattice.columns.stream() // 提取格中的所有派生列
        .filter(c -> c instanceof Lattice.DerivedColumn) // 过滤出派生列
        .map(c -> (Lattice.DerivedColumn) c) // 转换为派生列类型
        .collect(Collectors.toList()); // 收集为列表
    assertThat(derivedColumns, hasSize(2)); // 验证派生列数量为2个
    assertThat(derivedColumns.get(0).tables, isListOf("customer")); // 验证第一个派生列属于customer表
    assertThat(derivedColumns.get(1).tables, isListOf("customer")); // 验证第二个派生列属于customer表
  }

  /** Tests a number of features only available in Redshift: the {@code CONCAT}
   * and {@code CONVERT_TIMEZONE} functions. */
  @Test void testRedshiftDialect() throws Exception { // 测试Redshift方言的特定功能，验证CONCAT和CONVERT_TIMEZONE函数的处理
    final Tester t = new Tester().foodmart().withEvolve(true) // 创建测试器实例，启用格演化功能
        .withDialect(SqlDialect.DatabaseProduct.REDSHIFT.getDialect()) // 设置Redshift方言
        .withLibrary(SqlLibrary.REDSHIFT); // 设置Redshift函数库

    final String q0 = "select\n" // 构建测试查询：使用Redshift特定函数
        // CONCAT function in RedShift only accepts two arguments
        + "  CONCAT(\"fname\", \"lname\") as \"full_name\",\n" // Redshift的CONCAT函数（只接受两个参数）
        + "  convert_timezone('UTC', 'America/Los_Angeles',\n" // 时区转换函数
        + "  cast('2019-01-01 01:00:00' as timestamp)),\n"
        + "  left(\"fname\", 1) as \"initial\",\n" // LEFT函数
        + "  to_date('2019-01-01', 'YYYY-MM-DD'),\n" // TO_DATE函数
        + "  to_timestamp('2019-01-01 01:00:00', 'YYYY-MM-DD HH:MM:SS'),\n" // TO_TIMESTAMP函数
        + "  count(*) as c,\n" // 计数聚合
        + "  avg(\"total_children\" - \"num_children_at_home\")\n" // 平均值聚合
        + "from \"customer\" join \"sales_fact_1997\" using (\"customer_id\")\n" // 连接查询
        + "group by \"fname\", \"lname\""; // 分组
    t.addQuery(q0); // 添加查询到测试器
    assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射包含1个格
  }

  /** Tests a number of features only available in BigQuery: back-ticks;
   * GROUP BY ordinal; case-insensitive unquoted identifiers;
   * the {@code COUNTIF} aggregate function. */
  @Test void testBigQueryDialect() throws Exception { // 测试BigQuery方言的特定功能，验证反引号、GROUP BY序号和COUNTIF函数的处理
    final Tester t = new Tester().foodmart().withEvolve(true) // 创建测试器实例，启用格演化功能
        .withDialect(SqlDialect.DatabaseProduct.BIG_QUERY.getDialect()) // 设置BigQuery方言
        .withLibrary(SqlLibrary.BIG_QUERY); // 设置BigQuery函数库

    final String q0 = "select `product_id`,\n" // 构建测试查询：使用BigQuery特定语法
        + "  countif(unit_sales > 1000) as num_over_thousand,\n" // COUNTIF条件计数函数
        + "  SUM(unit_sales)\n" // SUM聚合
        + "from\n"
        + "  `sales_fact_1997`" // 使用反引号标识符
        + "group by 1"; // 使用序号GROUP BY（1表示第一列）
    t.addQuery(q0); // 添加查询到测试器
    assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射包含1个格
  }

  /** A tricky case involving a CTE (WITH), a join condition that references an

     * expression, a complex WHERE clause, and some other queries. */

    @Test void testJoinUsingExpression() throws Exception { // 测试复杂场景：包含CTE、表达式连接条件、复杂WHERE子句

      final Tester t = new Tester().foodmart().withEvolve(true); // 创建测试器实例，启用格演化功能

  

      final String q0 = "with c as (select\n" // 构建测试查询q0：使用CTE（公用表表达式）

          + "    \"customer_id\" + 1 as \"customer_id\",\n" // CTE中创建派生列customer_id（原值+1）

          + "    \"fname\"\n" // 选择fname字段

          + "  from \"customer\")\n" // 从customer表

          + "select\n"

          + "  COUNT(distinct c.\"customer_id\") as \"customer.count\"\n" // 计算去重后的customer_id数量

          + "from c\n" // 从CTE c

          + "left join \"sales_fact_1997\" using (\"customer_id\")\n" // 使用派生列customer_id进行左连接

          + "where case\n" // 复杂的WHERE子句，使用CASE WHEN表达式

          + "  when lower(substring(\"fname\", 11, 1)) in (0, 1)\n" // 当fname第11个字符（小写）为0或1时

          + "    then 'Amy Adams'\n" // 返回'Amy Adams'

          + "  when lower(substring(\"fname\", 11, 1)) in (2, 3)\n" // 当fname第11个字符（小写）为2或3时

          + "    then 'Barry Manilow'\n" // 返回'Barry Manilow'

          + "  when lower(substring(\"fname\", 11, 1)) in ('y', 'z')\n" // 当fname第11个字符（小写）为y或z时

          + "   then 'Yvonne Zane'\n" // 返回'Yvonne Zane'

          + "  end = 'Barry Manilow'\n" // 只保留CASE结果为'Barry Manilow'的记录

          + "LIMIT 500"; // 限制结果为500条

      final String q1 = "select * from \"customer\""; // 简单查询：查询customer表的所有数据

      final String q2 = "select sum(\"product_id\") from \"product\""; // 简单查询：查询product表的product_id总和

  

      // similar to q0, but "c" is a sub-select rather than CTE

      final String q4 = "select\n" // 构建测试查询q4：与q0类似，但使用子查询而非CTE

          + "  COUNT(distinct c.\"customer_id\") as \"customer.count\"\n"

          + "from (select \"customer_id\" + 1 as \"customer_id\", \"fname\"\n" // 子查询创建派生列

          + "  from \"customer\") as c\n" // 子查询别名为c

          + "left join \"sales_fact_1997\" using (\"customer_id\")\n"; // 使用派生列进行左连接

      t.addQuery(q1); // 添加查询q1

      t.addQuery(q0); // 添加查询q0

      t.addQuery(q1); // 再次添加查询q1

      t.addQuery(q4); // 添加查询q4

      t.addQuery(q2); // 添加查询q2

      assertThat(t.s.latticeMap, aMapWithSize(3)); // 验证格映射包含3个格（customer、sales_fact_1997、product各一个）

    }

  

    @Test void testDerivedColRef() throws Exception { // 测试派生列引用，验证连接条件中使用表达式的处理

      final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置

          .defaultSchema(Tester.schemaFrom(CalciteAssert.SchemaSpec.SCOTT)) // 设置默认模式为Scott

          .statisticProvider(QuerySqlStatisticProvider.SILENT_CACHING_INSTANCE) // 设置统计提供者

          .build(); // 构建配置

      final Tester t = new Tester(config).foodmart().withEvolve(true); // 创建测试器实例，启用格演化功能

  

      final String q0 = "select\n" // 构建测试查询：连接条件使用表达式

          + "  min(c.\"fname\") as \"customer.count\"\n" // 计算fname的最小值

          + "from \"customer\" as c\n" // customer表别名为c

          + "left join \"sales_fact_1997\" as s\n" // sales_fact_1997表别名为s

          + "on c.\"customer_id\" + 1 = s.\"customer_id\" + 2"; // 连接条件：c.customer_id+1 = s.customer_id+2

      t.addQuery(q0); // 添加查询到测试器

      assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射包含1个格

      assertThat(t.s.latticeMap.keySet().iterator().next(), // 验证格的键包含表达式连接

          is("sales_fact_1997 (customer:+($2, 2)):[MIN(customer.fname)]")); // +($2, 2)表示customer_id+2的表达式

      assertThat(t.s.space.g, // 验证空间图包含表达式边

          hasToString("graph(vertices: [[foodmart, customer],"

              + " [foodmart, sales_fact_1997]], "

              + "edges: [Step([foodmart, sales_fact_1997],"

              + " [foodmart, customer], +($2, 2):+($0, 1))])")); // 边包含表达式：+($2, 2):+($0, 1)

    }

  

    /** Tests that we can run the suggester against non-JDBC schemas.

     *

     * <p>{@link org.apache.calcite.test.CalciteAssert.SchemaSpec#FAKE_FOODMART}

     * is not based on {@link org.apache.calcite.adapter.jdbc.JdbcSchema} or

     * {@link org.apache.calcite.adapter.jdbc.JdbcTable} but can provide a

     * {@link javax.sql.DataSource}

     * and {@link SqlDialect} for executing statistics queries.

     *

     * <p>The query has a join, and so we have to execute statistics queries

     * to deduce the direction of the foreign key.

     */

    @Test void testFoodmartSimpleJoin() throws Exception { // 测试简单连接，验证LatticeSuggester可以处理非JDBC模式

      checkFoodmartSimpleJoin(CalciteAssert.SchemaSpec.JDBC_FOODMART); // 测试JDBC FoodMart模式

      checkFoodmartSimpleJoin(CalciteAssert.SchemaSpec.FAKE_FOODMART); // 测试模拟FoodMart模式

    }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6374">[CALCITE-6374]
   * LatticeSuggester throw NullPointerException when agg call covered with cast </a>. */
  @Test void testCastAggrNameExpression() throws Exception { // 测试聚合函数被CAST包裹的情况，验证CALCITE-6374问题的修复
    final Tester t = new Tester().foodmart().withEvolve(true); // 创建测试器实例，启用格演化功能
    final String q0 = "select\n" // 构建测试查询：包含CAST包裹的聚合函数
        + "  \"num_children_at_home\" + 12 as \"n12\",\n" // n12作为维度
        + "  sum(\"num_children_at_home\" + 10) as \"n10\",\n" // n10作为度量
        + "  cast(sum(\"num_children_at_home\" + 11) as double) as \"n11\",\n" // n11作为度量，被CAST为double类型
        + "  count(*) as c\n" // 计数聚合
        + "from \"customer\"\n"
        + "group by \"num_children_at_home\" + 12"; // 按n12分组
    final String l0 = "customer:[COUNT(), SUM(n10), SUM($f2)]"; // 期望的格结构：包含3个聚合，$f2是内部生成的派生列名
    t.addQuery(q0); // 添加查询到测试器
    assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射包含1个格
    assertThat(Iterables.getOnlyElement(t.s.latticeMap.keySet()), // 验证格的键与期望一致
        is(l0));
    final Lattice lattice = Iterables.getOnlyElement(t.s.latticeMap.values()); // 获取唯一的格对象
    final List<Lattice.DerivedColumn> derivedColumns = lattice.columns.stream() // 提取格中的所有派生列
        .filter(c -> c instanceof Lattice.DerivedColumn) // 过滤出派生列
        .map(c -> (Lattice.DerivedColumn) c) // 转换为派生列类型
        .collect(Collectors.toList()); // 收集为列表
    assertThat(derivedColumns, hasSize(4)); // 验证派生列数量为4个
    final List<String> tables = ImmutableList.of("customer"); // 创建表名列表
    checkDerivedColumn(lattice, tables, derivedColumns, 0, "n10", true); // 验证n10：总是度量
    checkDerivedColumn(lattice, tables, derivedColumns, 1, "$f2", true); // 验证$f2：总是度量（CAST包裹的聚合）
    checkDerivedColumn(lattice, tables, derivedColumns, 2, "n12", false); // 验证n12：不总是度量（是维度）
    checkDerivedColumn(lattice, tables, derivedColumns, 3, "n11", false); // 验证n11：不总是度量（虽然被CAST但仍是维度）
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6605">[CALCITE-6605]
   * Lattice SQL supports complex column expressions </a>. */
  @Test void testExpressionLatticeSql() throws Exception { // 测试格SQL生成，验证复杂列表达式的SQL生成
    final Tester t = new Tester().foodmart().withEvolve(true); // 创建测试器实例，启用格演化功能
    final String q0 = "select\n" // 构建测试查询：包含表达式
        + "  \"num_children_at_home\" + 12 as \"n12\",\n" // n12作为维度
        + "  sum(\"num_children_at_home\") as \"n10\",\n" // n10作为度量（直接列引用，非表达式）
        + "  count(*) as c\n" // 计数聚合
        + "from \"customer\"\n"
        + "group by \"num_children_at_home\" + 12"; // 按n12分组
    t.addQuery(q0); // 添加查询到测试器
    assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射包含1个格
    final Lattice lattice = Iterables.getOnlyElement(t.s.latticeMap.values()); // 获取唯一的格对象
    final String l0 = "customer:[COUNT(), SUM(customer.num_children_at_home)]"; // 期望的格结构
    assertThat(Iterables.getOnlyElement(t.s.latticeMap.keySet()), is(l0)); // 验证格的键与期望一致
    ImmutableList<Measure> measures = lattice.defaultMeasures; // 获取默认度量列表
    assert measures.size() == 2; // 断言度量数量为2个
    Builder groupSetBuilder = ImmutableBitSet.builder(); // 创建分组集构建器
    measures.forEach(measure -> groupSetBuilder.addAll(measure.argBitSet())); // 将所有度量的参数位集添加到分组集
    ImmutableBitSet groupSet = groupSetBuilder.build(); // 构建分组集
    String sql = "SELECT \"customer\".\"num_children_at_home\", COUNT(*) AS \"m0\", " // 期望生成的SQL
        + "SUM(\"customer\".\"num_children_at_home\") AS \"m1\"\n"
        + "FROM \"foodmart\".\"customer\" AS \"customer\"\n"
        + "GROUP BY \"customer\".\"num_children_at_home\"";
    assertThat(lattice.sql(groupSet, true, measures), // 验证生成的SQL与期望一致
        is(sql));
  }

  /** Test case for measure field involving a complex column operation,
   * for example sum("num_children_at_home" + 10). */
  @Test void testExpressionLatticeSql2() throws Exception { // 测试包含复杂列操作的度量字段的SQL生成
    final Tester t = new Tester().foodmart().withEvolve(true); // 创建测试器实例，启用格演化功能
    final String q0 = "select\n" // 构建测试查询：包含多个表达式度量
        + "  \"num_children_at_home\" + 12 as \"n12\",\n" // n12作为维度
        + "  sum(\"num_children_at_home\" + 10) as \"n10\",\n" // n10作为度量（表达式）
        + "  sum(\"num_children_at_home\" + 11) as \"n11\",\n" // n11作为度量（表达式）
        + "  count(*) as c\n" // 计数聚合
        + "from \"customer\"\n"
        + "group by \"num_children_at_home\" + 12"; // 按n12分组
    t.addQuery(q0); // 添加查询到测试器
    assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射包含1个格
    final Lattice lattice = Iterables.getOnlyElement(t.s.latticeMap.values()); // 获取唯一的格对象
    final String l0 = "customer:[COUNT(), SUM(n10), SUM(n11)]"; // 期望的格结构
    assertThat(Iterables.getOnlyElement(t.s.latticeMap.keySet()), is(l0)); // 验证格的键与期望一致
    ImmutableList<Measure> measures = lattice.defaultMeasures; // 获取默认度量列表
    assert measures.size() == 3; // 断言度量数量为3个
    Builder groupSetBuilder = ImmutableBitSet.builder(); // 创建分组集构建器
    measures.forEach(measure -> groupSetBuilder.addAll(measure.argBitSet())); // 将所有度量的参数位集添加到分组集
    ImmutableBitSet groupSet = groupSetBuilder.build(); // 构建分组集
    String sql = "SELECT \"num_children_at_home\" + 10 AS \"n10\", " // 期望生成的SQL：包含表达式列和聚合
        + "\"num_children_at_home\" + 11 AS \"n11\", COUNT(*) AS \"m0\", "
        + "SUM(\"num_children_at_home\" + 10) AS \"m1\", "
        + "SUM(\"num_children_at_home\" + 11) AS \"m2\"\n"
        + "FROM \"foodmart\".\"customer\" AS \"customer\"\n"
        + "GROUP BY \"num_children_at_home\" + 10, \"num_children_at_home\" + 11"; // 按两个表达式分组
    assertThat(lattice.sql(groupSet, true, measures), // 验证生成的SQL与期望一致
        is(sql));
  }

  /** Test case for measure field involving a complex column operation with functions,
   * for example sum(cast("num_children_at_home" as double) + 11). */
  @Test void testExpressionLatticeSql3() throws Exception { // 测试包含函数的复杂列操作的度量字段的SQL生成
    final Tester t = new Tester().foodmart().withEvolve(true); // 创建测试器实例，启用格演化功能
    final String q0 = "select\n" // 构建测试查询：包含CAST函数的表达式度量
        + "  \"num_children_at_home\" + 12 as \"n12\",\n" // n12作为维度
        + "  sum(\"num_children_at_home\" + 10) as \"n10\",\n" // n10作为度量（表达式）
        + "  sum(cast(\"num_children_at_home\" as double) + 11) as \"n11\",\n" // n11作为度量（包含CAST的表达式）
        + "  count(*) as c\n" // 计数聚合
        + "from \"customer\"\n"
        + "group by \"num_children_at_home\" + 12"; // 按n12分组
    t.addQuery(q0); // 添加查询到测试器
    assertThat(t.s.latticeMap, aMapWithSize(1)); // 验证格映射包含1个格
    final Lattice lattice = Iterables.getOnlyElement(t.s.latticeMap.values()); // 获取唯一的格对象
    final String l0 = "customer:[COUNT(), SUM(n10), SUM(n11)]"; // 期望的格结构
    assertThat(Iterables.getOnlyElement(t.s.latticeMap.keySet()), is(l0)); // 验证格的键与期望一致
    ImmutableList<Measure> measures = lattice.defaultMeasures; // 获取默认度量列表
    assert measures.size() == 3; // 断言度量数量为3个
    Builder groupSetBuilder = ImmutableBitSet.builder(); // 创建分组集构建器
    measures.forEach(measure -> groupSetBuilder.addAll(measure.argBitSet())); // 将所有度量的参数位集添加到分组集
    ImmutableBitSet groupSet = groupSetBuilder.build(); // 构建分组集
    String sql = "SELECT \"num_children_at_home\" + 10 AS \"n10\", " // 期望生成的SQL：包含CAST函数的表达式
        + "CAST(\"num_children_at_home\" AS DOUBLE) + 11 AS \"n11\", "
        + "COUNT(*) AS \"m0\", SUM(\"num_children_at_home\" + 10) AS \"m1\", "
        + "SUM(CAST(\"num_children_at_home\" AS DOUBLE) + 11) AS \"m2\"\n"
        + "FROM \"foodmart\".\"customer\" AS \"customer\"\n"
        + "GROUP BY \"num_children_at_home\" + 10, CAST(\"num_children_at_home\" AS DOUBLE) + 11"; // 按两个表达式分组
    assertThat(lattice.sql(groupSet, true, measures), // 验证生成的SQL与期望一致
        is(sql));
  }

  private void checkFoodmartSimpleJoin(CalciteAssert.SchemaSpec schemaSpec) // 验证简单连接场景，测试不同模式规范的支持
      throws Exception {
    final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置
        .defaultSchema(Tester.schemaFrom(schemaSpec)) // 设置默认模式
        .statisticProvider(QuerySqlStatisticProvider.SILENT_CACHING_INSTANCE) // 设置统计提供者
        .build(); // 构建配置
    final Tester t = new Tester(config); // 创建测试器实例
    final String q = "select *\n" // 构建测试查询：简单连接
        + "from \"time_by_day\" as \"t\",\n" // 时间维度表
        + " \"sales_fact_1997\" as \"s\"\n" // 销售事实表
        + "where \"s\".\"time_id\" = \"t\".\"time_id\"\n"; // 连接条件
    final String g = "sales_fact_1997 (time_by_day:time_id)"; // 期望的格结构
    assertThat(t.addQuery(q), isGraphs(g, "[]")); // 验证生成的格与期望一致
  }

  @Test void testUnion() throws Exception { // 测试集合操作（UNION、INTERSECT、EXCEPT）的处理
    checkUnion("union"); // 测试UNION操作
    checkUnion("union all"); // 测试UNION ALL操作
    checkUnion("intersect"); // 测试INTERSECT操作
    checkUnion("except"); // 测试EXCEPT操作
  }

  private void checkUnion(String setOp) throws Exception { // 验证指定集合操作的处理
    final Tester t = new Tester().foodmart().withEvolve(true); // 创建测试器实例，启用格演化功能
    final String q = "select \"t\".\"time_id\"\n" // 构建测试查询：包含集合操作
        + "from \"time_by_day\" as \"t\",\n" // 第一个查询：连接time_by_day和sales_fact_1997
        + " \"sales_fact_1997\" as \"s\"\n"
        + "where \"s\".\"time_id\" = \"t\".\"time_id\"\n"
        + setOp + "\n" // 集合操作符（UNION、INTERSECT、EXCEPT等）
        + "select min(\"unit_sales\")\n" // 第二个查询：聚合查询
        + "from \"sales_fact_1997\" as \"s\" join \"product\" as \"p\"\n"
        + " using (\"product_id\")\n" // 连接sales_fact_1997和product
        + "group by \"s\".\"customer_id\""; // 按客户ID分组

    // Adding a query generates two lattices
    final List<Lattice> latticeList = t.addQuery(q); // 添加查询，返回格列表
    assertThat(latticeList, hasSize(2)); // 验证格列表包含2个格（每个查询生成一个格）

    // But because of 'evolve' flag, the lattices are merged into a single
    // lattice
    final String g = "sales_fact_1997 (product:product_id time_by_day:time_id)"; // 期望的合并后的格结构
    final String measures = "[MIN(sales_fact_1997.unit_sales)]"; // 期望的聚合列表
    assertThat(t.s.getLatticeSet(), isGraphs(g, measures)); // 验证合并后的格集合与期望一致
  }

  /** Creates a matcher that matches query graphs to strings. */
  private BaseMatcher<Collection<Lattice>> isGraphs( // 创建一个匹配器，用于验证格集合与期望字符串列表是否匹配
      String... strings) { // 参数：期望的字符串数组（每个格对应两个字符串：节点和聚合）
    final List<String> expectedList = Arrays.asList(strings); // 将期望字符串数组转换为列表
    return new BaseMatcher<Collection<Lattice>>() { // 创建基础匹配器
      public boolean matches(Object item) { // 执行匹配逻辑
        //noinspection unchecked
        return item instanceof Collection // 检查是否为集合类型
            && ((Collection<Object>) item).size() * 2 == expectedList.size() // 检查格数量*2是否等于期望字符串数量
            && allEqual(ImmutableList.copyOf((Collection) item), expectedList); // 检查所有格是否与期望匹配
      }

      private boolean allEqual(List<Lattice> items, // 验证所有格是否与期望字符串匹配
          List<String> expects) { // 参数：格列表、期望字符串列表
        for (int i = 0; i < items.size(); i++) { // 遍历每个格
          final Lattice lattice = items.get(i); // 获取当前格
          final String expectedNode = expects.get(2 * i); // 获取期望的节点字符串（偶数索引）
          if (!lattice.rootNode.digest.equals(expectedNode)) { // 验证格的根节点摘要与期望一致
            return false; // 不匹配则返回false
          }
          final String expectedMeasures = expects.get(2 * i + 1); // 获取期望的聚合字符串（奇数索引）
          if (!lattice.defaultMeasures.toString().equals(expectedMeasures)) { // 验证格的默认聚合与期望一致
            return false; // 不匹配则返回false
          }
        }
        return true; // 所有格都匹配则返回true
      }

      public void describeTo(Description description) { // 描述期望值（用于匹配失败时的错误信息）
        description.appendValue(expectedList); // 将期望列表添加到描述中
      }
    };
  }

  /** Test helper. */
  private static class Tester { // 测试辅助类，用于封装测试环境配置和查询执行逻辑
    final LatticeSuggester s; // LatticeSuggester实例，用于建议物化视图结构
    private final FrameworkConfig config; // 框架配置对象，包含模式、统计提供者等配置

    Tester() { // 默认构造函数，使用Scott模式和MapSqlStatisticProvider
      this( // 调用带参数的构造函数
          Frameworks.newConfigBuilder() // 创建配置构建器
              .defaultSchema(schemaFrom(CalciteAssert.SchemaSpec.SCOTT)) // 设置默认模式为Scott
              .statisticProvider(MapSqlStatisticProvider.INSTANCE) // 设置统计提供者
              .build()); // 构建配置
    }

    private Tester(FrameworkConfig config) { // 带参数的构造函数
      this.config = config; // 保存框架配置
      s = new LatticeSuggester(config); // 创建LatticeSuggester实例
    }

    Tester withConfig(FrameworkConfig config) { // 使用指定配置创建新的Tester实例
      return new Tester(config); // 返回新的Tester实例
    }

    Tester sqlToRelConverter(UnaryOperator<SqlToRelConverter.Config> sqlToRelConverterConfig) { // 配置SQL到关系代数转换器
      return withConfig( // 使用新配置创建Tester
          builder().sqlToRelConverterConfig(sqlToRelConverterConfig // 应用SQL到关系代数转换器配置
              .apply(config.getSqlToRelConverterConfig())).build()); // 构建配置
    }

    Tester foodmart() { // 使用FoodMart模式创建Tester
      return schema(CalciteAssert.SchemaSpec.JDBC_FOODMART); // 返回使用JDBC FoodMart模式的Tester
    }

    private Tester schema(CalciteAssert.SchemaSpec schemaSpec) { // 使用指定模式规范创建Tester
      return withConfig(builder() // 使用新配置创建Tester
          .defaultSchema(schemaFrom(schemaSpec)) // 设置默认模式
          .build()); // 构建配置
    }

    private Frameworks.ConfigBuilder builder() { // 创建配置构建器，基于当前配置
      return Frameworks.newConfigBuilder(config); // 返回配置构建器
    }

    List<Lattice> addQuery(String q) throws SqlParseException, // 添加查询到LatticeSuggester，返回生成的格列表
        ValidationException, RelConversionException { // 可能抛出的异常：SQL解析异常、验证异常、关系转换异常
      final Planner planner = new PlannerImpl(config); // 创建规划器实例
      final SqlNode node = planner.parse(q); // 解析SQL字符串为SQL节点
      final SqlNode node2 = planner.validate(node); // 验证SQL节点
      final RelRoot root = planner.rel(node2); // 将SQL节点转换为关系代数根节点
      return s.addQuery(root.project()); // 将投影后的关系代数添加到LatticeSuggester，返回生成的格列表
    }

    /** Parses a query returns its graph. */
    LatticeRootNode node(String q) throws SqlParseException, // 解析查询并返回其根节点
        ValidationException, RelConversionException { // 可能抛出的异常
      final List<Lattice> list = addQuery(q); // 添加查询并获取格列表
      assertThat(list, hasSize(1)); // 验证格列表只包含一个格
      return list.get(0).rootNode; // 返回格的根节点
    }

    private static SchemaPlus schemaFrom(CalciteAssert.SchemaSpec spec) { // 从模式规范创建SchemaPlus对象
      final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根模式
      return CalciteAssert.addSchema(rootSchema, spec); // 将指定模式添加到根模式并返回
    }

    Tester withEvolve(boolean evolve) { // 配置是否启用格演化功能
      return withConfig(builder().evolveLattice(evolve).build()); // 返回配置了演化功能的Tester
    }

    private Tester withParser(UnaryOperator<SqlParser.Config> transform) { // 配置SQL解析器
      return withConfig( // 使用新配置创建Tester
          builder()
              .parserConfig(transform.apply(config.getParserConfig())) // 应用解析器配置转换
              .build()); // 构建配置
    }

    Tester withDialect(SqlDialect dialect) { // 配置SQL方言
      return withParser(dialect::configureParser); // 使用方言配置解析器
    }

    Tester withLibrary(SqlLibrary library) { // 配置SQL函数库
      SqlOperatorTable opTab = SqlLibraryOperatorTableFactory.INSTANCE // 获取操作符表工厂实例
          .getOperatorTable(EnumSet.of(SqlLibrary.STANDARD, library)); // 获取包含标准库和指定库的操作符表
      return withConfig(builder().operatorTable(opTab).build()); // 返回配置了操作符表的Tester
    }
  }
}
