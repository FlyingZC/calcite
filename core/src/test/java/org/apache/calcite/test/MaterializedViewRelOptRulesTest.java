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
package org.apache.calcite.test;

import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.plan.RelOptMaterialization;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.tools.Programs;
import org.apache.calcite.util.Pair;

import com.google.common.collect.ImmutableList;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 物化视图规则单元测试类，用于测试 {@link org.apache.calcite.rel.rules.materialize.MaterializedViewRule} 及其子类
 * 
 * 本测试类的主要作用：
 * 1. 测试物化视图规则如何将物化视图与查询计划的结构进行匹配
 * 2. 验证物化视图重写功能的正确性，包括：
 *    - 聚合物化视图的重写（Aggregate Materialization）
 *    - 连接物化视图的重写（Join Materialization）
 *    - 连接+聚合物化视图的重写（Join+Aggregate Materialization）
 * 3. 测试各种复杂的查询场景，如：
 *    - GROUP BY 列的匹配
 *    - 聚合函数的匹配（COUNT, SUM, COUNT DISTINCT等）
 *    - WHERE 条件的匹配和下推
 *    - 表连接顺序的优化
 *    - 时间函数（FLOOR）的处理
 *    - 外键-主键关系的利用
 * 4. 验证物化视图在不同场景下是否能够被正确识别和使用
 * 5. 测试边界情况和错误场景
 * 
 * 核心概念：
 * - 物化视图（Materialized View）：预先计算并存储的查询结果，用于加速查询
 * - 视图重写（View Rewriting）：优化器将查询重写为使用物化视图
 * - 视图匹配（View Matching）：判断查询是否可以使用某个物化视图
 * - 视图替换（View Substitution）：用物化视图替换查询中的部分或全部操作
 * 
 * 测试方法命名规范：
 * - testAggregateMaterialization：测试聚合物化视图
 * - testJoinAggregateMaterialization：测试连接+聚合物化视图
 * - testJoinMaterialization：测试连接物化视图
 * - testQuery/ViewProjectWithBetween：测试BETWEEN表达式的处理
 */
class MaterializedViewRelOptRulesTest {
  // 静态常量：物化视图测试器，用于配置和执行物化视图测试
  // 这是一个匿名内部类，重写了optimize方法来自定义优化过程
  static final MaterializedViewTester TESTER =
      new MaterializedViewTester() {
        // 重写优化方法，自定义查询优化逻辑
        // 参数1 queryRel: 待优化的查询关系表达式树（RelNode）
        // 参数2 materializationList: 可用的物化视图列表
        // 返回值: 优化后的关系表达式列表
        @Override protected List<RelNode> optimize(RelNode queryRel,
            List<RelOptMaterialization> materializationList) {
          // 获取查询的规划器（Planner），负责优化查询计划
          RelOptPlanner planner = queryRel.getCluster().getPlanner();
          // 获取查询的特征集（RelTraitSet），并替换为EnumerableConvention约定
          // EnumerableConvention表示查询将使用可枚举的物理实现（Java迭代器）
          RelTraitSet traitSet = queryRel.getCluster().traitSet()
              .replace(EnumerableConvention.INSTANCE);
          // 向规划器注册默认的优化规则
          // 参数1: planner - 目标规划器
          // 参数2: true - 启用基于成本的优化规则
          // 参数3: false - 不启用确定性规则
          RelOptUtil.registerDefaultRules(planner, true, false);
          // 执行标准优化程序并返回优化后的关系表达式
          // Programs.standard()：获取标准的优化程序（包括预处理和后处理）
          // run()：运行优化程序，传入规划器、查询、特征集、物化视图列表和空的目标列表
          return ImmutableList.of(
              Programs.standard().run(planner, queryRel, traitSet,
                  materializationList, ImmutableList.of()));
        }
      };

  /** Creates a fixture. */ // 创建一个测试夹具（Fixture），用于配置物化视图测试环境
  protected MaterializedViewFixture fixture(String query) { // 参数query: SQL查询字符串，表示要测试的查询
    // 使用MaterializedViewFixture.create静态方法创建测试夹具
    // 参数1: query - 查询字符串
    // 参数2: TESTER - 测试器实例，包含优化逻辑
    // 返回值: 配置好的测试夹具，可以进一步配置物化视图等参数
    return MaterializedViewFixture.create(query, TESTER);
  }

  /** Creates a fixture with a given query. */ // 创建一个包含物化视图和查询的测试夹具
  protected final MaterializedViewFixture sql(String materialize, // 参数materialize: 物化视图的SQL定义字符串
      String query) { // 参数query: 要执行的查询SQL字符串
    // 首先为查询创建基础测试夹具，然后添加物化视图配置
    // withMaterializations方法：添加物化视图列表
    // ImmutableList.of(Pair.of(materialize, "MV0")): 创建包含一个物化视图的不可变列表
    // Pair.of(materialize, "MV0"): 创建一个键值对，键是物化视图的SQL定义，值是物化视图的名称"MV0"
    // 返回值: 配置好物化视图的测试夹具，可以链式调用其他配置方法
    return fixture(query)
        .withMaterializations(ImmutableList.of(Pair.of(materialize, "MV0")));
  }

  @Test void testSwapJoin() { // 测试连接交换优化：验证优化器能否识别并优化连接顺序的交换
    // 物化视图定义：sales_fact_1997表与time_by_day表连接，条件是time_id相等
    // 查询：与物化视图相同的连接，但表的顺序相反（time_by_day在前，sales_fact_1997在后）
    // 测试目的：验证优化器能否识别连接顺序的交换，并正确使用物化视图
    sql("select count(*) as c from \"foodmart\".\"sales_fact_1997\" as s"
            + " join \"foodmart\".\"time_by_day\" as t on s.\"time_id\" = t.\"time_id\"",
        "select count(*) as c from \"foodmart\".\"time_by_day\" as t"
            + " join \"foodmart\".\"sales_fact_1997\" as s on t.\"time_id\" = s.\"time_id\"")
        .withDefaultSchemaSpec(CalciteAssert.SchemaSpec.JDBC_FOODMART) // 设置默认schema为JDBC_FOODMART
        .ok(); // 断言测试通过，即物化视图被正确使用
  }

  /** Aggregation materialization with a project. */ // 测试带投影的聚合物化视图：验证在物化视图包含投影和额外列时的重写能力
  @Test void testAggregateProject() {
    // Note that materialization does not start with the GROUP BY columns.
    // Not a smart way to design a materialization, but people may do it.
    // 注意：物化视图的定义不以GROUP BY列开头，这不是一个明智的设计方式，但人们可能会这样做
    // 物化视图：按empid和deptno分组，计算count(*)、empid+2和sum(empid)
    // 查询：只按deptno分组，计算count(*)+1
    // 测试目的：验证优化器能否从包含更多分组列和表达式的物化视图派生出查询结果
    // 预期结果：使用物化视图MV0，通过Calc节点调整count值，通过Aggregate节点按deptno分组
    sql("select \"deptno\", count(*) as c, \"empid\" + 2, sum(\"empid\") as s "
            + "from \"emps\" group by \"empid\", \"deptno\"",
        "select count(*) + 1 as c, \"deptno\" from \"emps\" group by \"deptno\"")
        .checkingThatResultContains("" // 验证结果包含预期的物理计划
            + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[+($t1, $t2)], C=[$t3], deptno=[$t0])\n" // 计算节点：将count(*)+1，输出C和deptno
            + "  EnumerableAggregate(group=[{0}], agg#0=[$SUM0($1)])\n" // 聚合节点：按第0列（deptno）分组，累加第1列（count）
            + "    EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点：扫描物化视图MV0
        .ok(); // 断言测试通过
  }

  @Test void testAggregateMaterializationNoAggregateFuncs1() { // 测试无聚合函数的物化视图1：验证当物化视图和查询都只有GROUP BY列且完全相同时的匹配
    // 物化视图：按empid和deptno分组，无聚合函数
    // 查询：与物化视图完全相同
    // 测试目的：验证完全相同的查询可以使用物化视图
    sql("select \"empid\", \"deptno\" from \"emps\" group by \"empid\", \"deptno\"",
        "select \"empid\", \"deptno\" from \"emps\" group by \"empid\", \"deptno\"").ok(); // 断言测试通过
  }

  @Test void testAggregateMaterializationNoAggregateFuncs2() { // 测试无聚合函数的物化视图2：验证当查询的分组列是物化视图分组列子集时的匹配
    // 物化视图：按empid和deptno分组
    // 查询：只按deptno分组（是物化视图分组列的子集）
    // 测试目的：验证可以通过聚合减少分组列来使用物化视图
    // 预期结果：使用物化视图MV0，通过Aggregate节点按第1列（deptno）分组
    sql("select \"empid\", \"deptno\" from \"emps\" group by \"empid\", \"deptno\"",
        "select \"deptno\" from \"emps\" group by \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{1}])\n" // 聚合节点：按第1列（deptno）分组
            + "  EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点：扫描物化视图MV0
        .ok(); // 断言测试通过
  }

  @Test void testAggregateMaterializationNoAggregateFuncs3() { // 测试无聚合函数的物化视图3：验证当查询的分组列不是物化视图分组列子集时不匹配
    // 物化视图：只按deptno分组
    // 查询：按empid和deptno分组（查询的分组列不是物化视图的子集）
    // 测试目的：验证当物化视图不包含查询所需的所有分组列时不能使用物化视图
    // 预期结果：不能使用物化视图（noMat）
    sql("select \"deptno\" from \"emps\" group by \"deptno\"",
        "select \"empid\", \"deptno\" from \"emps\" group by \"empid\", \"deptno\"")
        .noMat(); // 断言不能使用物化视图
  }

  @Test void testAggregateMaterializationNoAggregateFuncs4() { // 测试无聚合函数的物化视图4：验证带WHERE条件的无聚合函数物化视图匹配
    // 物化视图：按empid和deptno分组，WHERE deptno = 10
    // 查询：只按deptno分组，WHERE deptno = 10
    // 测试目的：验证WHERE条件相同时可以减少分组列
    // 预期结果：使用物化视图MV0，通过Aggregate节点按deptno分组
    sql("select \"empid\", \"deptno\"\n"
            + "from \"emps\" where \"deptno\" = 10 group by \"empid\", \"deptno\"",
        "select \"deptno\" from \"emps\" where \"deptno\" = 10 group by \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{1}])\n" // 聚合节点：按第1列（deptno）分组
            + "  EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点：扫描物化视图MV0
        .ok(); // 断言测试通过
  }

  @Test void testAggregateMaterializationNoAggregateFuncs5() { // 测试无聚合函数的物化视图5：验证WHERE条件不兼容时不匹配
    // 物化视图：按empid和deptno分组，WHERE deptno = 5
    // 查询：只按deptno分组，WHERE deptno = 10
    // 测试目的：验证WHERE条件不同时不能使用物化视图
    // 预期结果：不能使用物化视图（noMat）
    sql("select \"empid\", \"deptno\"\n"
            + "from \"emps\" where \"deptno\" = 5 group by \"empid\", \"deptno\"",
        "select \"deptno\" from \"emps\" where \"deptno\" = 10 group by \"deptno\"")
        .noMat(); // 断言不能使用物化视图
  }

  @Test void testAggregateMaterializationNoAggregateFuncs6() { // 测试无聚合函数的物化视图6：验证WHERE条件下推时的匹配
    // 物化视图：按empid和deptno分组，WHERE deptno > 5
    // 查询：只按deptno分组，WHERE deptno > 10
    // 测试目的：验证当查询的WHERE条件比物化视图更严格时，可以将条件下推到物化视图之后
    // 预期结果：使用物化视图MV0，通过Calc节点过滤deptno > 10，通过Aggregate节点按deptno分组
    sql("select \"empid\", \"deptno\"\n"
            + "from \"emps\" where \"deptno\" > 5 group by \"empid\", \"deptno\"",
        "select \"deptno\" from \"emps\" where \"deptno\" > 10 group by \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{1}])\n" // 聚合节点：按第1列（deptno）分组
            + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=[10], expr#3=[<($t2, $t1)], proj#0..1=[{exprs}], $condition=[$t3])\n" // 计算节点：过滤deptno > 10
            + "    EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点：扫描物化视图MV0
        .ok(); // 断言测试通过
  }

  @Test void testAggregateMaterializationNoAggregateFuncs7() { // 测试无聚合函数的物化视图7：验证WHERE条件方向不兼容时不匹配
    // 物化视图：按empid和deptno分组，WHERE deptno > 5
    // 查询：只按deptno分组，WHERE deptno < 10
    // 测试目的：验证WHERE条件方向相反时不能使用物化视图
    // 预期结果：不能使用物化视图（noMat）
    sql("select \"empid\", \"deptno\"\n"
            + "from \"emps\" where \"deptno\" > 5 group by \"empid\", \"deptno\"",
        "select \"deptno\" from \"emps\" where \"deptno\" < 10 group by \"deptno\"")
        .noMat(); // 断言不能使用物化视图
  }

  @Test void testAggregateMaterializationNoAggregateFuncs8() { // 测试无聚合函数的物化视图8：验证查询输出列不是物化视图分组列子集时不匹配
    // 物化视图：按empid和deptno分组，但只输出empid
    // 查询：按deptno分组，输出deptno
    // 测试目的：验证查询的输出列不在物化视图的分组列中时不能使用物化视图
    // 预期结果：不能使用物化视图（noMat）
    sql("select \"empid\" from \"emps\" group by \"empid\", \"deptno\"",
        "select \"deptno\" from \"emps\" group by \"deptno\"")
        .noMat(); // 断言不能使用物化视图
  }

  @Test void testAggregateMaterializationNoAggregateFuncs9() { // 测试无聚合函数的物化视图9：验证GROUP BY列不同且WHERE条件不同时不匹配
    // 物化视图：按name、empid和deptno分组，WHERE salary > 1000
    // 查询：按name和empid分组，WHERE salary > 2000
    // 测试目的：验证GROUP BY列不同且WHERE条件不兼容时不能使用物化视图
    // 预期结果：不能使用物化视图（noMat）
    sql("select \"empid\", \"deptno\" from \"emps\"\n"
            + "where \"salary\" > 1000 group by \"name\", \"empid\", \"deptno\"",
        "select \"empid\" from \"emps\"\n"
            + "where \"salary\" > 2000 group by \"name\", \"empid\"")
        .noMat(); // 断言不能使用物化视图
  }

  @Test void testAggregateMaterializationAggregateFuncs1() { // 测试带聚合函数的物化视图1：验证查询忽略物化视图中的聚合函数时的匹配
    // 物化视图：按empid和deptno分组，计算count(*)和sum(empid)
    // 查询：只按deptno分组，忽略所有聚合函数
    // 测试目的：验证查询可以忽略物化视图中的聚合函数
    // 预期结果：使用物化视图MV0，通过Aggregate节点按deptno分组
    sql("select \"empid\", \"deptno\", count(*) as c, sum(\"empid\") as s\n"
            + "from \"emps\" group by \"empid\", \"deptno\"",
        "select \"deptno\" from \"emps\" group by \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{1}])\n" // 聚合节点：按第1列（deptno）分组
            + "  EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点：扫描物化视图MV0
        .ok(); // 断言测试通过
  }

  @Test void testAggregateMaterializationAggregateFuncs2() { // 测试带聚合函数的物化视图2：验证查询使用部分聚合函数时的匹配
    // 物化视图：按empid和deptno分组，计算count(*)和sum(empid)
    // 查询：只按deptno分组，使用count(*)和sum(empid)
    // 测试目的：验证查询可以使用物化视图中的聚合函数，同时减少分组列
    // 预期结果：使用物化视图MV0，通过Aggregate节点按deptno分组并累加count和sum
    sql("select \"empid\", \"deptno\", count(*) as c, sum(\"empid\") as s\n"
            + "from \"emps\" group by \"empid\", \"deptno\"",
        "select \"deptno\", count(*) as c, sum(\"empid\") as s\n"
            + "from \"emps\" group by \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{1}], C=[$SUM0($2)], S=[$SUM0($3)])\n" // 聚合节点：按deptno分组，累加count和sum
            + "  EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点：扫描物化视图MV0
        .ok(); // 断言测试通过
  }

  @Test void testAggregateMaterializationAggregateFuncs3() { // 测试带聚合函数的物化视图3：验证查询使用所有聚合函数时的匹配
    // 物化视图：按empid和deptno分组，计算count(*)和sum(empid)
    // 查询：按empid和deptno分组，使用sum(empid)和count(*)（顺序不同）
    // 测试目的：验证查询可以使用物化视图中的所有聚合函数，只需重新排列输出列
    // 预期结果：使用物化视图MV0，通过Calc节点重新排列输出列
    sql("select \"empid\", \"deptno\", count(*) as c, sum(\"empid\") as s\n"
            + "from \"emps\" group by \"empid\", \"deptno\"",
        "select \"deptno\", \"empid\", sum(\"empid\") as s, count(*) as c\n"
            + "from \"emps\" group by \"empid\", \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..3=[{inputs}], deptno=[$t1], empid=[$t0], S=[$t3], C=[$t2])\n" // 计算节点：重新排列输出列顺序
            + "  EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点：扫描物化视图MV0
        .ok(); // 断言测试通过
  }

  @Test void testAggregateMaterializationAggregateFuncs4() { // 测试带聚合函数的物化视图4：验证带WHERE条件和聚合函数时的匹配
    // 物化视图：按empid和deptno分组，WHERE deptno >= 10，计算count(*)和sum(empid)
    // 查询：只按deptno分组，WHERE deptno > 10，只使用sum(empid)
    // 测试目的：验证带WHERE条件的物化视图可以通过条件下推和聚合函数选择来匹配查询
    // 预期结果：使用物化视图MV0，通过Calc过滤deptno > 10，通过Aggregate按deptno分组并累加sum
    sql("select \"empid\", \"deptno\", count(*) as c, sum(\"empid\") as s\n"
            + "from \"emps\" where \"deptno\" >= 10 group by \"empid\", \"deptno\"",
        "select \"deptno\", sum(\"empid\") as s\n"
            + "from \"emps\" where \"deptno\" > 10 group by \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{1}], S=[$SUM0($3)])\n" // 聚合节点：按deptno分组，累加sum
            + "  EnumerableCalc(expr#0..3=[{inputs}], expr#4=[10], expr#5=[<($t4, $t1)], "
            + "proj#0..3=[{exprs}], $condition=[$t5])\n" // 计算节点：过滤deptno > 10
            + "    EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点：扫描物化视图MV0
        .ok(); // 断言测试通过
  }

  @Test void testAggregateMaterializationAggregateFuncs5() { // 测试带聚合函数的物化视图5：验证聚合函数表达式重写时的匹配
    // 物化视图：按empid和deptno分组，WHERE deptno >= 10，计算count(*)+1和sum(empid)
    // 查询：只按deptno分组，WHERE deptno > 10，计算sum(empid)+1
    // 测试目的：验证聚合函数表达式可以重写，count(*)+1可以转换为SUM0(count)
    // 预期结果：使用物化视图MV0，通过Calc过滤deptno > 10，通过Aggregate累加sum，通过Calc计算sum+1
    sql("select \"empid\", \"deptno\", count(*) + 1 as c, sum(\"empid\") as s\n"
            + "from \"emps\" where \"deptno\" >= 10 group by \"empid\", \"deptno\"",
        "select \"deptno\", sum(\"empid\") + 1 as s\n"
            + "from \"emps\" where \"deptno\" > 10 group by \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[+($t1, $t2)],"
            + " deptno=[$t0], S=[$t3])\n" // 计算节点：计算sum+1
            + "  EnumerableAggregate(group=[{1}], agg#0=[$SUM0($3)])\n" // 聚合节点：按deptno分组，累加sum
            + "    EnumerableCalc(expr#0..3=[{inputs}], expr#4=[10], expr#5=[<($t4, $t1)], "
            + "proj#0..3=[{exprs}], $condition=[$t5])\n" // 计算节点：过滤deptno > 10
            + "      EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点：扫描物化视图MV0
        .ok(); // 断言测试通过
  }

  @Test void testAggregateMaterializationAggregateFuncs6() { // 测试带聚合函数的物化视图6：验证聚合函数表达式不兼容时不匹配
    // 物化视图：按empid和deptno分组，WHERE deptno >= 10，计算count(*)+1和sum(empid)+2
    // 查询：只按deptno分组，WHERE deptno > 10，计算sum(empid)+1
    // 测试目的：验证当聚合函数表达式不兼容时不能使用物化视图
    // 预期结果：不能使用物化视图（noMat），因为sum(empid)+2无法转换为sum(empid)+1
    sql("select \"empid\", \"deptno\", count(*) + 1 as c, sum(\"empid\") + 2 as s\n"
            + "from \"emps\" where \"deptno\" >= 10 group by \"empid\", \"deptno\"",
        "select \"deptno\", sum(\"empid\") + 1 as s\n"
            + "from \"emps\" where \"deptno\" > 10 group by \"deptno\"")
        .noMat(); // 断言不能使用物化视图
  }

  @Test void testAggregateMaterializationAggregateFuncs7() {
    // 测试带聚合函数的物化视图7：验证GROUP BY列表达式和聚合函数表达式重写时的匹配
            + "from \"emps\" where \"deptno\" >= 10 group by \"empid\", \"deptno\"",
        "select \"deptno\" + 1, sum(\"empid\") + 1 as s\n"
            + "from \"emps\" where \"deptno\" > 10 group by \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[+($t0, $t2)], "
            + "expr#4=[+($t1, $t2)], EXPR$0=[$t3], S=[$t4])\n"
            + "  EnumerableAggregate(group=[{1}], agg#0=[$SUM0($3)])\n"
            + "    EnumerableCalc(expr#0..3=[{inputs}], expr#4=[10], expr#5=[<($t4, $t1)], "
            + "proj#0..3=[{exprs}], $condition=[$t5])\n"
            + "      EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Disabled
  @Test void testAggregateMaterializationAggregateFuncs8() {
    // It needs further checking.
    sql("select \"empid\", \"deptno\" + 1, count(*) + 1 as c, sum(\"empid\") as s\n"
            + "from \"emps\" where \"deptno\" >= 10 group by \"empid\", \"deptno\"",
        "select \"deptno\" + 1, sum(\"empid\") + 1 as s\n"
            + "from \"emps\" where \"deptno\" > 10 group by \"deptno\"")
        .ok();
  }

  @Test void testAggregateMaterializationAggregateFuncs9() {
    // 测试带聚合函数的物化视图9：验证时间函数FLOOR到不同粒度时的匹配
            + "count(*) + 1 as c, sum(\"empid\") as s\n"
            + "from \"emps\"\n"
            + "group by \"empid\", floor(cast('1997-01-20 12:34:56' as timestamp) to month)",
        "select floor(cast('1997-01-20 12:34:56' as timestamp) to year), sum(\"empid\") as s\n"
            + "from \"emps\" group by floor(cast('1997-01-20 12:34:56' as timestamp) to year)")
        .ok();
  }

  @Test void testAggregateMaterializationAggregateFuncs10() {
    // 测试带聚合函数的物化视图10：验证时间函数FLOOR和聚合函数表达式重写时的匹配
            + "count(*) + 1 as c, sum(\"empid\") as s\n"
            + "from \"emps\"\n"
            + "group by \"empid\", floor(cast('1997-01-20 12:34:56' as timestamp) to month)",
        "select floor(cast('1997-01-20 12:34:56' as timestamp) to year), sum(\"empid\") + 1 as s\n"
            + "from \"emps\" group by floor(cast('1997-01-20 12:34:56' as timestamp) to year)")
        .ok();
  }

  @Test void testAggregateMaterializationAggregateFuncs11() {
    // 测试带聚合函数的物化视图11：验证时间函数FLOOR到秒和分钟时的匹配
            + "count(*) + 1 as c, sum(\"empid\") as s\n"
            + "from \"emps\"\n"
            + "group by \"empid\", floor(cast('1997-01-20 12:34:56' as timestamp) to second)",
        "select floor(cast('1997-01-20 12:34:56' as timestamp) to minute), sum(\"empid\") as s\n"
            + "from \"emps\" group by floor(cast('1997-01-20 12:34:56' as timestamp) to minute)")
        .ok();
  }

  @Test void testAggregateMaterializationAggregateFuncs12() {
    // 测试带聚合函数的物化视图12：验证时间函数FLOOR到秒和月时的匹配
            + "count(*) + 1 as c, sum(\"empid\") as s\n"
            + "from \"emps\"\n"
            + "group by \"empid\", floor(cast('1997-01-20 12:34:56' as timestamp) to second)",
        "select floor(cast('1997-01-20 12:34:56' as timestamp) to month), sum(\"empid\") as s\n"
            + "from \"emps\" group by floor(cast('1997-01-20 12:34:56' as timestamp) to month)")
        .ok();
  }

  @Test void testAggregateMaterializationAggregateFuncs13() {
    // 测试带聚合函数的物化视图13：验证CAST时间戳到不同FLOOR粒度时的匹配
            + "count(*) + 1 as c, sum(\"empid\") as s\n"
            + "from \"emps\"\n"
            + "group by \"empid\", cast('1997-01-20 12:34:56' as timestamp)",
        "select floor(cast('1997-01-20 12:34:56' as timestamp) to year), sum(\"empid\") as s\n"
            + "from \"emps\" group by floor(cast('1997-01-20 12:34:56' as timestamp) to year)")
        .ok();
  }

  @Test void testAggregateMaterializationAggregateFuncs14() {
    // 测试带聚合函数的物化视图14：验证时间函数FLOOR到月和小时时的匹配
            + "count(*) + 1 as c, sum(\"empid\") as s\n"
            + "from \"emps\"\n"
            + "group by \"empid\", floor(cast('1997-01-20 12:34:56' as timestamp) to month)",
        "select floor(cast('1997-01-20 12:34:56' as timestamp) to hour), sum(\"empid\") as s\n"
            + "from \"emps\" group by floor(cast('1997-01-20 12:34:56' as timestamp) to hour)")
        .ok();
  }

  @Test void testAggregateMaterializationAggregateFuncs15() {
    // 测试带聚合函数的物化视图15：验证动态列的时间函数FLOOR匹配
            + "count(*) + 1 as c, sum(\"eventid\") as s\n"
            + "from \"events\" group by \"eventid\", floor(cast(\"ts\" as timestamp) to second)",
        "select floor(cast(\"ts\" as timestamp) to minute), sum(\"eventid\") as s\n"
            + "from \"events\" group by floor(cast(\"ts\" as timestamp) to minute)")
        .ok();
  }

  @Test void testAggregateMaterializationAggregateFuncs16() {
    // 测试带聚合函数的物化视图16：验证CAST时间戳到FLOOR年时的匹配
            + "from \"events\" group by \"eventid\", cast(\"ts\" as timestamp)",
        "select floor(cast(\"ts\" as timestamp) to year), sum(\"eventid\") as s\n"
            + "from \"events\" group by floor(cast(\"ts\" as timestamp) to year)")
        .ok();
  }

  @Test void testAggregateMaterializationAggregateFuncs17() {
    // 测试带聚合函数的物化视图17：验证时间函数FLOOR到月和小时时的匹配
            + "count(*) + 1 as c, sum(\"eventid\") as s\n"
            + "from \"events\" group by \"eventid\", floor(cast(\"ts\" as timestamp) to month)",
        "select floor(cast(\"ts\" as timestamp) to hour), sum(\"eventid\") as s\n"
            + "from \"events\" group by floor(cast(\"ts\" as timestamp) to hour)")
        .checkingThatResultContains("EnumerableTableScan(table=[[hr, events]])")
        .ok();
  }

  @Test void testAggregateMaterializationAggregateFuncs18() {
    // 测试带聚合函数的物化视图18：验证GROUP BY列是表达式时的匹配
            + "from \"emps\" group by \"empid\", \"deptno\"",
        "select \"empid\"*\"deptno\", sum(\"empid\") as s\n"
            + "from \"emps\" group by \"empid\"*\"deptno\"")
        .ok();
  }

  @Test void testAggregateMaterializationAggregateFuncs19() {
    // 测试带聚合函数的物化视图19：验证GROUP BY列和聚合函数都是表达式时的匹配
            + "from \"emps\" group by \"empid\", \"deptno\"",
        "select \"empid\" + 10, count(*) + 1 as c\n"
            + "from \"emps\" group by \"empid\" + 10")
        .ok();
  }

  @Test void testAggregateMaterializationAggregateFuncs20() {
    // 测试带聚合函数的物化视图20：验证常量GROUP BY和子查询时的匹配
        "select * from\n"
            + "(select 11 as \"empno\", 22 as \"sal\", count(*)\n"
            + "from \"emps\" group by 11, 22) tmp\n"
            + "where \"sal\" = 33")
        .checkingThatResultContains("EnumerableValues(tuples=[[]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationNoAggregateFuncs1() {
    // 测试连接+聚合无聚合函数1：验证连接+聚合的物化视图匹配
            + "join \"depts\" using (\"deptno\") where \"depts\".\"deptno\" > 10\n"
            + "group by \"empid\", \"depts\".\"deptno\"",
        "select \"empid\" from \"emps\"\n"
            + "join \"depts\" using (\"deptno\") where \"depts\".\"deptno\" > 20\n"
            + "group by \"empid\", \"depts\".\"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[20], expr#3=[<($t2, $t1)], "
            + "empid=[$t0], $condition=[$t3])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationNoAggregateFuncs2() {
    // 测试连接+聚合无聚合函数2：验证连接顺序改变时的匹配
            + "join \"emps\" using (\"deptno\") where \"depts\".\"deptno\" > 10\n"
            + "group by \"empid\", \"depts\".\"deptno\"",
        "select \"empid\" from \"emps\"\n"
            + "join \"depts\" using (\"deptno\") where \"depts\".\"deptno\" > 20\n"
            + "group by \"empid\", \"depts\".\"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[20], expr#3=[<($t2, $t0)], "
            + "empid=[$t1], $condition=[$t3])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationNoAggregateFuncs3() {
    sql("select \"empid\" from \"emps\"\n"
            + "join \"depts\" using (\"deptno\") where \"depts\".\"deptno\" > 10\n"
            + "group by \"empid\", \"depts\".\"deptno\"",
        "select \"empid\" from \"emps\"\n"
            + "join \"depts\" using (\"deptno\") where \"depts\".\"deptno\" > 20\n"
            + "group by \"empid\", \"depts\".\"deptno\"")
        .noMat();
  }

  @Test void testJoinAggregateMaterializationNoAggregateFuncs4() {
    // 测试连接+聚合无聚合函数4：验证WHERE条件在不同表时的匹配
            + "join \"depts\" using (\"deptno\") where \"emps\".\"deptno\" > 10\n"
            + "group by \"empid\", \"depts\".\"deptno\"",
        "select \"empid\" from \"emps\"\n"
            + "join \"depts\" using (\"deptno\") where \"depts\".\"deptno\" > 20\n"
            + "group by \"empid\", \"depts\".\"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[20], expr#3=[<($t2, $t1)], "
            + "empid=[$t0], $condition=[$t3])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationNoAggregateFuncs5() {
    // 测试连接+聚合无聚合函数5：验证WHERE条件在不同列时的匹配
            + "join \"emps\" using (\"deptno\") where \"emps\".\"empid\" > 10\n"
            + "group by \"depts\".\"deptno\", \"emps\".\"empid\"",
        "select \"depts\".\"deptno\" from \"depts\"\n"
            + "join \"emps\" using (\"deptno\") where \"emps\".\"empid\" > 15\n"
            + "group by \"depts\".\"deptno\", \"emps\".\"empid\"")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[15], expr#3=[<($t2, $t1)], "
            + "deptno=[$t0], $condition=[$t3])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationNoAggregateFuncs6() {
    // 测试连接+聚合无聚合函数6：验证减少分组列时的匹配
            + "join \"emps\" using (\"deptno\") where \"emps\".\"empid\" > 10\n"
            + "group by \"depts\".\"deptno\", \"emps\".\"empid\"",
        "select \"depts\".\"deptno\" from \"depts\"\n"
            + "join \"emps\" using (\"deptno\") where \"emps\".\"empid\" > 15\n"
            + "group by \"depts\".\"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{0}])\n"
            + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=[15], expr#3=[<($t2, $t1)], "
            + "proj#0..1=[{exprs}], $condition=[$t3])\n"
            + "    EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationNoAggregateFuncs7() {
    // 测试连接+聚合无聚合函数7：验证多表连接和WHERE条件区间分割时的匹配
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"locations\" on (\"locations\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 11\n"
            + "group by \"depts\".\"deptno\", \"dependents\".\"empid\"",
        "select \"dependents\".\"empid\"\n"
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"locations\" on (\"locations\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 10\n"
            + "group by \"dependents\".\"empid\"")
        .checkingThatResultContains("EnumerableAggregate(group=[{0}])",
                "EnumerableUnion(all=[true])",
                "EnumerableAggregate(group=[{2}])",
                "EnumerableTableScan(table=[[hr, MV0]])",
                "expr#5=[Sarg[(10..11]]], expr#6=[SEARCH($t0, $t5)]")
        .ok();
  }

  @Test void testJoinAggregateMaterializationNoAggregateFuncs8() {
    // 测试连接+聚合无聚合函数8：验证WHERE条件区间不重叠时不匹配
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"locations\" on (\"locations\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 20\n"
            + "group by \"depts\".\"deptno\", \"dependents\".\"empid\"",
        "select \"dependents\".\"empid\"\n"
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"locations\" on (\"locations\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 10 and \"depts\".\"deptno\" < 20\n"
            + "group by \"dependents\".\"empid\"")
        .noMat();
  }

  @Test void testJoinAggregateMaterializationNoAggregateFuncs9() {
    // 测试连接+聚合无聚合函数9：验证WHERE条件区间部分重叠时的匹配
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"locations\" on (\"locations\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 11 and \"depts\".\"deptno\" < 19\n"
            + "group by \"depts\".\"deptno\", \"dependents\".\"empid\"",
        "select \"dependents\".\"empid\"\n"
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"locations\" on (\"locations\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 10 and \"depts\".\"deptno\" < 20\n"
            + "group by \"dependents\".\"empid\"")
        .checkingThatResultContains("EnumerableAggregate(group=[{0}])",
            "EnumerableUnion(all=[true])",
            "EnumerableAggregate(group=[{2}])",
            "EnumerableTableScan(table=[[hr, MV0]])",
            "expr#5=[Sarg[(10..11], [19..20)]], expr#6=[SEARCH($t0, $t5)]")
        .ok();
  }

  @Test void testJoinAggregateMaterializationNoAggregateFuncs10() {
    // 测试连接+聚合无聚合函数10：验证笛卡尔积到内连接的匹配
            + "\"emps\".\"deptno\", \"depts\".\"deptno\" as \"deptno2\", "
            + "\"dependents\".\"empid\"\n"
            + "from \"depts\", \"dependents\", \"emps\"\n"
            + "where \"depts\".\"deptno\" > 10\n"
            + "group by \"depts\".\"name\", \"dependents\".\"name\", "
            + "\"emps\".\"deptno\", \"depts\".\"deptno\", "
            + "\"dependents\".\"empid\"",
        "select \"dependents\".\"empid\"\n"
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 10\n"
            + "group by \"dependents\".\"empid\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{4}])\n"
            + "  EnumerableCalc(expr#0..4=[{inputs}], expr#5=[=($t2, $t3)], "
            + "expr#6=[CAST($t1):VARCHAR], "
            + "expr#7=[CAST($t0):VARCHAR], "
            + "expr#8=[=($t6, $t7)], expr#9=[AND($t5, $t8)], proj#0..4=[{exprs}], $condition=[$t9])\n"
            + "    EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationAggregateFuncs1() {
    sql("select \"empid\", \"depts\".\"deptno\", count(*) as c, sum(\"empid\") as s\n"
            + "from \"emps\" join \"depts\" using (\"deptno\")\n"
            + "group by \"empid\", \"depts\".\"deptno\"",
        "select \"deptno\" from \"emps\" group by \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{1}])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationAggregateFuncs2() {
    // 测试连接+聚合带聚合函数2：验证连接顺序改变时的聚合函数匹配
            + "from \"emps\" join \"depts\" using (\"deptno\")\n"
            + "group by \"empid\", \"emps\".\"deptno\"",
        "select \"depts\".\"deptno\", count(*) as c, sum(\"empid\") as s\n"
            + "from \"emps\" join \"depts\" using (\"deptno\")\n"
            + "group by \"depts\".\"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{1}], C=[$SUM0($2)], S=[$SUM0($3)])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationAggregateFuncs3() {
    sql("select \"empid\", \"depts\".\"deptno\", count(*) as c, sum(\"empid\") as s\n"
            + "from \"emps\" join \"depts\" using (\"deptno\")\n"
            + "group by \"empid\", \"depts\".\"deptno\"",
        "select \"deptno\", \"empid\", sum(\"empid\") as s, count(*) as c\n"
            + "from \"emps\" group by \"empid\", \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..3=[{inputs}], deptno=[$t1], empid=[$t0], S=[$t3], C=[$t2])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationAggregateFuncs4() {
    // 测试连接+聚合带聚合函数4：验证带WHERE条件和聚合函数时的匹配
            + "from \"emps\" join \"depts\" using (\"deptno\")\n"
            + "where \"emps\".\"deptno\" >= 10 group by \"empid\", \"emps\".\"deptno\"",
        "select \"depts\".\"deptno\", sum(\"empid\") as s\n"
            + "from \"emps\" join \"depts\" using (\"deptno\")\n"
            + "where \"emps\".\"deptno\" > 10 group by \"depts\".\"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{1}], S=[$SUM0($3)])\n"
            + "  EnumerableCalc(expr#0..3=[{inputs}], expr#4=[10], expr#5=[<($t4, $t1)], "
            + "proj#0..3=[{exprs}], $condition=[$t5])\n"
            + "    EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationAggregateFuncs5() {
    // 测试连接+聚合带聚合函数5：验证聚合函数表达式重写时的匹配
            + "from \"emps\" join \"depts\" using (\"deptno\")\n"
            + "where \"depts\".\"deptno\" >= 10 group by \"empid\", \"depts\".\"deptno\"",
        "select \"depts\".\"deptno\", sum(\"empid\") + 1 as s\n"
            + "from \"emps\" join \"depts\" using (\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 10 group by \"depts\".\"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[+($t1, $t2)], "
            + "deptno=[$t0], S=[$t3])\n"
            + "  EnumerableAggregate(group=[{1}], agg#0=[$SUM0($3)])\n"
            + "    EnumerableCalc(expr#0..3=[{inputs}], expr#4=[10], expr#5=[<($t4, $t1)], "
            + "proj#0..3=[{exprs}], $condition=[$t5])\n"
            + "      EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Disabled
  @Test void testJoinAggregateMaterializationAggregateFuncs6() {
    // since the materialized view would match the sub-query.
    // Initial investigation after enabling AggregateJoinTransposeRule.EXTENDED
    // shows that the rewriting with pre-aggregations is generated and the
    // materialized view rewriting happens.
    // However, we end up discarding the plan with the materialized view and still
    // using the plan with the pre-aggregations.
    // TODO: Explore and extend to choose best rewriting.
    final String m = "select \"depts\".\"name\", sum(\"salary\") as s\n"
        + "from \"emps\"\n"
        + "join \"depts\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
        + "group by \"depts\".\"name\"";
    final String q = "select \"dependents\".\"empid\", sum(\"salary\") as s\n"
        + "from \"emps\"\n"
        + "join \"depts\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
        + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
        + "group by \"dependents\".\"empid\"";
    sql(m, q).ok();
  }

  @Test void testJoinAggregateMaterializationAggregateFuncs7() {
    // 测试连接+聚合带聚合函数7：验证减少连接表时的匹配
            + "from \"emps\"\n"
            + "join \"dependents\" on (\"emps\".\"empid\" = \"dependents\".\"empid\")\n"
            + "group by \"dependents\".\"empid\", \"emps\".\"deptno\"",
        "select \"dependents\".\"empid\", sum(\"salary\") as s\n"
            + "from \"emps\"\n"
            + "join \"depts\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "join \"dependents\" on (\"emps\".\"empid\" = \"dependents\".\"empid\")\n"
            + "group by \"dependents\".\"empid\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{0}], S=[$SUM0($2)])\n"
            + "  EnumerableHashJoin(condition=[=($1, $3)], joinType=[inner])\n"
            + "    EnumerableTableScan(table=[[hr, MV0]])\n"
            + "    EnumerableTableScan(table=[[hr, depts]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationAggregateFuncs8() {
    // 测试连接+聚合带聚合函数8：验证改变分组列时的匹配
            + "from \"emps\"\n"
            + "join \"dependents\" on (\"emps\".\"empid\" = \"dependents\".\"empid\")\n"
            + "group by \"dependents\".\"empid\", \"emps\".\"deptno\"",
        "select \"depts\".\"name\", sum(\"salary\") as s\n"
            + "from \"emps\"\n"
            + "join \"depts\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "join \"dependents\" on (\"emps\".\"empid\" = \"dependents\".\"empid\")\n"
            + "group by \"depts\".\"name\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{4}], S=[$SUM0($2)])\n"
            + "  EnumerableHashJoin(condition=[=($1, $3)], joinType=[inner])\n"
            + "    EnumerableTableScan(table=[[hr, MV0]])\n"
            + "    EnumerableTableScan(table=[[hr, depts]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationAggregateFuncs9() {
    // 测试连接+聚合带聚合函数9：验证COUNT DISTINCT的匹配
            + "from \"emps\"\n"
            + "join \"dependents\" on (\"emps\".\"empid\" = \"dependents\".\"empid\")\n"
            + "group by \"dependents\".\"empid\", \"emps\".\"deptno\"",
        "select \"emps\".\"deptno\", count(distinct \"salary\") as s\n"
            + "from \"emps\"\n"
            + "join \"dependents\" on (\"emps\".\"empid\" = \"dependents\".\"empid\")\n"
            + "group by \"dependents\".\"empid\", \"emps\".\"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..2=[{inputs}], deptno=[$t1], S=[$t2])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinAggregateMaterializationAggregateFuncs10() {
    // 测试连接+聚合带聚合函数10：验证COUNT DISTINCT不兼容时不匹配
            + "from \"emps\"\n"
            + "join \"dependents\" on (\"emps\".\"empid\" = \"dependents\".\"empid\")\n"
            + "group by \"dependents\".\"empid\", \"emps\".\"deptno\"",
        "select \"emps\".\"deptno\", count(distinct \"salary\") as s\n"
            + "from \"emps\"\n"
            + "join \"dependents\" on (\"emps\".\"empid\" = \"dependents\".\"empid\")\n"
            + "group by \"emps\".\"deptno\"")
        .noMat();
  }

  @Test void testJoinAggregateMaterializationAggregateFuncs11() {
    // 测试连接+聚合带聚合函数11：验证COUNT DISTINCT和区间分割时的匹配
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"locations\" on (\"locations\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 11 and \"depts\".\"deptno\" < 19\n"
            + "group by \"depts\".\"deptno\", \"dependents\".\"empid\"",
        "select \"dependents\".\"empid\", count(\"emps\".\"salary\") + 1\n"
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"locations\" on (\"locations\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 10 and \"depts\".\"deptno\" < 20\n"
            + "group by \"dependents\".\"empid\"")
        .checkingThatResultContains("EnumerableCalc(expr#0..1=[{inputs}], "
                + "expr#2=[1], expr#3=[+($t1, $t2)], empid=[$t0], EXPR$1=[$t3])\n"
                + "  EnumerableAggregate(group=[{0}], agg#0=[$SUM0($1)])",
            "EnumerableUnion(all=[true])",
            "EnumerableAggregate(group=[{2}], agg#0=[COUNT()])",
            "EnumerableAggregate(group=[{1}], agg#0=[$SUM0($2)])",
            "EnumerableTableScan(table=[[hr, MV0]])",
            "expr#5=[Sarg[(10..11], [19..20)]], expr#6=[SEARCH($t0, $t5)]")
        .ok();
  }

  @Test void testJoinAggregateMaterializationAggregateFuncs12() {
    // 测试连接+聚合带聚合函数12：验证COUNT DISTINCT不兼容时不匹配
            + "count(distinct \"emps\".\"salary\") as s\n"
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"locations\" on (\"locations\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 11 and \"depts\".\"deptno\" < 19\n"
            + "group by \"depts\".\"deptno\", \"dependents\".\"empid\"",
        "select \"dependents\".\"empid\", count(distinct \"emps\".\"salary\") + 1\n"
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"locations\" on (\"locations\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 10 and \"depts\".\"deptno\" < 20\n"
            + "group by \"dependents\".\"empid\"")
        .noMat();
  }

  @Test void testJoinAggregateMaterializationAggregateFuncs13() {
    // 测试连接+聚合带聚合函数13：验证COUNT和COUNT DISTINCT不兼容时不匹配
            + "from \"emps\"\n"
            + "join \"dependents\" on (\"emps\".\"empid\" = \"dependents\".\"empid\")\n"
            + "group by \"dependents\".\"empid\", \"emps\".\"deptno\"",
        "select \"emps\".\"deptno\", count(\"salary\") as s\n"
            + "from \"emps\"\n"
            + "join \"dependents\" on (\"emps\".\"empid\" = \"dependents\".\"empid\")\n"
            + "group by \"dependents\".\"empid\", \"emps\".\"deptno\"")
        .noMat();
  }

  @Test void testJoinAggregateMaterializationAggregateFuncs14() {
    // 测试连接+聚合带聚合函数14：验证复杂OR条件下的匹配
            + "count(*) as c, sum(\"empid\") as s\n"
            + "from \"emps\" join \"depts\" using (\"deptno\")\n"
            + "where (\"depts\".\"name\" is not null and \"emps\".\"name\" = 'a') or "
            + "(\"depts\".\"name\" is not null and \"emps\".\"name\" = 'b')\n"
            + "group by \"empid\", \"emps\".\"name\", \"depts\".\"name\", \"emps\".\"deptno\"",
        "select \"depts\".\"deptno\", sum(\"empid\") as s\n"
            + "from \"emps\" join \"depts\" using (\"deptno\")\n"
            + "where \"depts\".\"name\" is not null and \"emps\".\"name\" = 'a'\n"
            + "group by \"depts\".\"deptno\"")
        .ok();
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4276">[CALCITE-4276]
   * If query contains join and rollup function (FLOOR), rewrite to materialized
   * view contains bad field offset</a>. */
  @Test void testJoinAggregateMaterializationAggregateFuncs15() {
    // 测试连接+聚合带聚合函数15：验证CALCITE-4276：FLOOR函数和连接时的字段偏移问题
        + "SELECT \"deptno\",\n"
        + "  COUNT(*) AS \"dept_size\",\n"
        + "  SUM(\"salary\") AS \"dept_budget\"\n"
        + "FROM \"emps\"\n"
        + "GROUP BY \"deptno\"";
    final String q = ""
        + "SELECT FLOOR(\"CREATED_AT\" TO YEAR) AS by_year,\n"
        + "  COUNT(*) AS \"num_emps\"\n"
        + "FROM (SELECT\"deptno\"\n"
        + "    FROM \"emps\") AS \"t\"\n"
        + "JOIN (SELECT \"deptno\",\n"
        + "        \"inceptionDate\" as \"CREATED_AT\"\n"
        + "    FROM \"depts2\") using (\"deptno\")\n"
        + "GROUP BY FLOOR(\"CREATED_AT\" TO YEAR)";
    String plan = ""
        + "EnumerableAggregate(group=[{8}], num_emps=[$SUM0($1)])\n"
        + "  EnumerableCalc(expr#0..7=[{inputs}], expr#8=[FLAG(YEAR)], "
        + "expr#9=[FLOOR($t3, $t8)], proj#0..7=[{exprs}], $f8=[$t9])\n"
        + "    EnumerableHashJoin(condition=[=($0, $4)], joinType=[inner])\n"
        + "      EnumerableTableScan(table=[[hr, MV0]])\n"
        + "      EnumerableTableScan(table=[[hr, depts2]])\n";
    sql(m, q)
        .checkingThatResultContains(plan)
        .ok();
  }

  @Test void testJoinMaterialization1() {
    // 测试连接物化视图1：验证子查询和连接的匹配
        + "from (select * from \"emps\" where \"empid\" < 300)\n"
        + "join \"depts\" using (\"deptno\")";
    sql("select * from \"emps\" where \"empid\" < 500", q).ok();
  }

  @Disabled
  @Test void testJoinMaterialization2() {
    // 测试连接物化视图2：验证列顺序改变时的匹配（已禁用）
        + "from \"emps\"\n"
        + "join \"depts\" using (\"deptno\")";
    String m = "select \"deptno\", \"empid\", \"name\",\n"
        + "\"salary\", \"commission\" from \"emps\"";
    sql(m, q).ok();
  }

  @Test void testJoinMaterialization3() {
    // 测试连接物化视图3：验证WHERE条件下推时的匹配
        + "join \"depts\" using (\"deptno\") where \"empid\" = 1";
    String m = "select \"empid\" \"deptno\" from \"emps\"\n"
        + "join \"depts\" using (\"deptno\")";
    sql(m, q).ok();
  }

  @Test void testJoinMaterialization4() {
    // 测试连接物化视图4：验证WHERE条件下推时的匹配
            + "join \"depts\" using (\"deptno\")",
        "select \"empid\" \"deptno\" from \"emps\"\n"
            + "join \"depts\" using (\"deptno\") where \"empid\" = 1")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0=[{inputs}], expr#1=[CAST($t0):INTEGER NOT NULL], expr#2=[1], "
            + "expr#3=[=($t1, $t2)], deptno=[$t0], $condition=[$t3])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinMaterialization5() {
    // 测试连接物化视图5：验证类型转换和WHERE条件的匹配
            + "join \"depts\" using (\"deptno\")",
        "select \"empid\" \"deptno\" from \"emps\"\n"
            + "join \"depts\" using (\"deptno\") where \"empid\" > 1")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0=[{inputs}], expr#1=[CAST($t0):JavaType(int) NOT NULL], "
            + "expr#2=[1], expr#3=[<($t2, $t1)], EXPR$0=[$t1], $condition=[$t3])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinMaterialization6() {
    // 测试连接物化视图6：验证类型转换和WHERE条件的匹配
            + "join \"depts\" using (\"deptno\")",
        "select \"empid\" \"deptno\" from \"emps\"\n"
            + "join \"depts\" using (\"deptno\") where \"empid\" = 1")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0=[{inputs}], expr#1=[CAST($t0):JavaType(int) NOT NULL], "
            + "expr#2=[1], expr#3=[CAST($t1):INTEGER NOT NULL], expr#4=[=($t2, $t3)], "
            + "EXPR$0=[$t1], $condition=[$t4])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinMaterialization7() {
    // 测试连接物化视图7：验证增加连接表时的匹配
            + "from \"emps\"\n"
            + "join \"depts\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")",
        "select \"dependents\".\"empid\"\n"
            + "from \"emps\"\n"
            + "join \"depts\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..2=[{inputs}], empid=[$t1])\n"
            + "  EnumerableHashJoin(condition=[=($0, $2)], joinType=[inner])\n"
            + "    EnumerableCalc(expr#0=[{inputs}], expr#1=[CAST($t0):VARCHAR], name=[$t1])\n"
            + "      EnumerableTableScan(table=[[hr, MV0]])\n"
            + "    EnumerableCalc(expr#0..1=[{inputs}], expr#2=[CAST($t1):VARCHAR], empid=[$t0], name0=[$t2])\n"
            + "      EnumerableTableScan(table=[[hr, dependents]])")
        .ok();
  }

  @Test void testJoinMaterialization8() {
    // 测试连接物化视图8：验证连接顺序改变时的匹配
            + "from \"emps\"\n"
            + "join \"depts\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")",
        "select \"dependents\".\"empid\"\n"
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..4=[{inputs}], empid=[$t2])\n"
            + "  EnumerableHashJoin(condition=[=($1, $4)], joinType=[inner])\n"
            + "    EnumerableCalc(expr#0=[{inputs}], expr#1=[CAST($t0):VARCHAR], proj#0..1=[{exprs}])\n"
            + "      EnumerableTableScan(table=[[hr, MV0]])\n"
            + "    EnumerableCalc(expr#0..1=[{inputs}], expr#2=[CAST($t1):VARCHAR], proj#0..2=[{exprs}])\n"
            + "      EnumerableTableScan(table=[[hr, dependents]])")
        .ok();
  }

  @Test void testJoinMaterialization9() {
    // 测试连接物化视图9：验证增加更多连接表时的匹配
            + "from \"emps\"\n"
            + "join \"depts\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")",
        "select \"dependents\".\"empid\"\n"
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"locations\" on (\"locations\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")")
        .ok();
  }

  @Test void testJoinMaterialization10() {
    // 测试连接物化视图10：验证WHERE条件区间分割时的匹配
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 30",
        "select \"dependents\".\"empid\"\n"
            + "from \"depts\"\n"
            + "join \"dependents\" on (\"depts\".\"name\" = \"dependents\".\"name\")\n"
            + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")\n"
            + "where \"depts\".\"deptno\" > 10")
        .checkingThatResultContains("EnumerableUnion(all=[true])",
                "EnumerableTableScan(table=[[hr, MV0]])",
                "expr#5=[Sarg[(10..30]]], expr#6=[SEARCH($t0, $t5)]")
        .ok();
  }

  @Test void testJoinMaterialization11() {
    // 测试连接物化视图11：验证子查询不匹配
            + "join \"depts\" using (\"deptno\")",
        "select \"empid\" from \"emps\"\n"
            + "where \"deptno\" in (select \"deptno\" from \"depts\")")
        .noMat();
  }

  @Test void testJoinMaterialization12() {
    // 测试连接物化视图12：验证复杂OR条件下的匹配
            + "from \"emps\" join \"depts\" using (\"deptno\")\n"
            + "where (\"depts\".\"name\" is not null and \"emps\".\"name\" = 'a') or "
            + "(\"depts\".\"name\" is not null and \"emps\".\"name\" = 'b') or "
            + "(\"depts\".\"name\" is not null and \"emps\".\"name\" = 'c')",
        "select \"depts\".\"deptno\", \"depts\".\"name\"\n"
            + "from \"emps\" join \"depts\" using (\"deptno\")\n"
            + "where (\"depts\".\"name\" is not null and \"emps\".\"name\" = 'a') or "
            + "(\"depts\".\"name\" is not null and \"emps\".\"name\" = 'b')")
        .ok();
  }

  @Test void testJoinMaterializationUKFK1() {
    // 测试连接物化视图外键主键1：验证外键-主键关系下的匹配
            + "(select * from \"emps\" where \"empid\" = 1) \"a\"\n"
            + "join \"depts\" using (\"deptno\")\n"
            + "join \"dependents\" using (\"empid\")",
        "select \"a\".\"empid\" from \n"
            + "(select * from \"emps\" where \"empid\" = 1) \"a\"\n"
            + "join \"dependents\" using (\"empid\")")
        .ok();
  }

  @Test void testJoinMaterializationUKFK2() {
    // 测试连接物化视图外键主键2：验证外键-主键关系减少列时的匹配
            + "(select * from \"emps\" where \"empid\" = 1) \"a\"\n"
            + "join \"depts\" using (\"deptno\")\n"
            + "join \"dependents\" using (\"empid\")",
        "select \"a\".\"empid\" from \n"
            + "(select * from \"emps\" where \"empid\" = 1) \"a\"\n"
            + "join \"dependents\" using (\"empid\")\n")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..1=[{inputs}], empid=[$t0])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinMaterializationUKFK3() {
    // 测试连接物化视图外键主键3：验证外键-主键关系改变输出列时不匹配
            + "(select * from \"emps\" where \"empid\" = 1) \"a\"\n"
            + "join \"depts\" using (\"deptno\")\n"
            + "join \"dependents\" using (\"empid\")",
        "select \"a\".\"name\" from \n"
            + "(select * from \"emps\" where \"empid\" = 1) \"a\"\n"
            + "join \"dependents\" using (\"empid\")\n")
        .noMat();
  }

  @Test void testJoinMaterializationUKFK4() {
    // 测试连接物化视图外键主键4：验证外键-主键关系简化连接时的匹配
            + "(select * from \"emps\" where \"empid\" = 1)\n"
            + "join \"depts\" using (\"deptno\")",
        "select \"empid\" from \"emps\" where \"empid\" = 1\n")
        .ok();
  }

  @Test void testJoinMaterializationUKFK5() {
    // 测试连接物化视图外键主键5：验证外键-主键关系多连接时的匹配
            + "join \"depts\" using (\"deptno\")\n"
            + "join \"dependents\" using (\"empid\")"
            + "where \"emps\".\"empid\" = 1",
        "select \"emps\".\"empid\" from \"emps\"\n"
            + "join \"dependents\" using (\"empid\")\n"
            + "where \"emps\".\"empid\" = 1")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..1=[{inputs}], empid=[$t0])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinMaterializationUKFK6() {
    // 测试连接物化视图外键主键6：验证外键-主键关系重复连接时的匹配
            + "join \"depts\" \"a\" on (\"emps\".\"deptno\"=\"a\".\"deptno\")\n"
            + "join \"depts\" \"b\" on (\"emps\".\"deptno\"=\"b\".\"deptno\")\n"
            + "join \"dependents\" using (\"empid\")"
            + "where \"emps\".\"empid\" = 1",
        "select \"emps\".\"empid\" from \"emps\"\n"
            + "join \"dependents\" using (\"empid\")\n"
            + "where \"emps\".\"empid\" = 1")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..1=[{inputs}], empid=[$t0])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]])")
        .ok();
  }

  @Test void testJoinMaterializationUKFK7() {
    // 测试连接物化视图外键主键7：验证外键-主键关系不同连接条件时不匹配
            + "join \"depts\" \"a\" on (\"emps\".\"name\"=\"a\".\"name\")\n"
            + "join \"depts\" \"b\" on (\"emps\".\"name\"=\"b\".\"name\")\n"
            + "join \"dependents\" using (\"empid\")"
            + "where \"emps\".\"empid\" = 1",
        "select \"emps\".\"empid\" from \"emps\"\n"
            + "join \"dependents\" using (\"empid\")\n"
            + "where \"emps\".\"empid\" = 1")
        .noMat();
  }

  @Test void testJoinMaterializationUKFK8() {
    // 测试连接物化视图外键主键8：验证外键-主键关系混合连接条件时不匹配
            + "join \"depts\" \"a\" on (\"emps\".\"deptno\"=\"a\".\"deptno\")\n"
            + "join \"depts\" \"b\" on (\"emps\".\"name\"=\"b\".\"name\")\n"
            + "join \"dependents\" using (\"empid\")"
            + "where \"emps\".\"empid\" = 1",
        "select \"emps\".\"empid\" from \"emps\"\n"
            + "join \"dependents\" using (\"empid\")\n"
            + "where \"emps\".\"empid\" = 1")
        .noMat();
  }

  @Test void testJoinMaterializationUKFK9() {
    // 测试连接物化视图外键主键9：验证外键-主键关系增加连接时的匹配
            + "join \"dependents\" using (\"empid\")",
        "select \"emps\".\"empid\", \"dependents\".\"empid\", \"emps\".\"deptno\"\n"
            + "from \"emps\"\n"
            + "join \"dependents\" using (\"empid\")"
            + "join \"depts\" \"a\" on (\"emps\".\"deptno\"=\"a\".\"deptno\")\n"
            + "where \"emps\".\"name\" = 'Bill'")
        .ok();
  }

  @Test void testQueryProjectWithBetween() {
    // 测试查询BETWEEN表达式：验证BETWEEN表达式的下推
            + " from \"foodmart\".\"sales_fact_1997\" as s"
            + " where s.\"store_id\" = 1",
        "select s.\"time_id\" between 1 and 3"
            + " from \"foodmart\".\"sales_fact_1997\" as s"
            + " where s.\"store_id\" = 1")
        .withDefaultSchemaSpec(CalciteAssert.SchemaSpec.JDBC_FOODMART)
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..7=[{inputs}], expr#8=[Sarg[[1..3]]], "
            + "expr#9=[SEARCH($t1, $t8)], $f0=[$t9])\n"
            + "  EnumerableTableScan(table=[[foodmart, MV0]])")
        .ok();
  }

  @Test void testJoinQueryProjectWithBetween() {
    // 测试连接查询BETWEEN表达式：验证连接查询中BETWEEN表达式的下推
            + " from \"foodmart\".\"sales_fact_1997\" as s"
            + " join \"foodmart\".\"time_by_day\" as t on s.\"time_id\" = t.\"time_id\""
            + " where s.\"store_id\" = 1",
        "select s.\"time_id\" between 1 and 3"
            + " from \"foodmart\".\"sales_fact_1997\" as s"
            + " join \"foodmart\".\"time_by_day\" as t on s.\"time_id\" = t.\"time_id\""
            + " where s.\"store_id\" = 1")
        .withDefaultSchemaSpec(CalciteAssert.SchemaSpec.JDBC_FOODMART)
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..17=[{inputs}], expr#18=[Sarg[[1..3]]], "
            + "expr#19=[SEARCH($t8, $t18)], $f0=[$t19])\n"
            + "  EnumerableTableScan(table=[[foodmart, MV0]])")
        .ok();
  }

  @Test void testViewProjectWithBetween() {
    // 测试视图BETWEEN表达式：验证视图中BETWEEN表达式的处理
            + " from \"foodmart\".\"sales_fact_1997\" as s"
            + " where s.\"store_id\" = 1",
        "select s.\"time_id\""
            + " from \"foodmart\".\"sales_fact_1997\" as s"
            + " where s.\"store_id\" = 1")
        .withDefaultSchemaSpec(CalciteAssert.SchemaSpec.JDBC_FOODMART)
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..1=[{inputs}], time_id=[$t0])\n"
            + "  EnumerableTableScan(table=[[foodmart, MV0]])")
        .ok();
  }

  @Test void testQueryAndViewProjectWithBetween() {
    // 测试查询和视图BETWEEN表达式：验证BETWEEN表达式的匹配
            + " from \"foodmart\".\"sales_fact_1997\" as s"
            + " where s.\"store_id\" = 1",
        "select s.\"time_id\" between 1 and 3"
            + " from \"foodmart\".\"sales_fact_1997\" as s"
            + " where s.\"store_id\" = 1")
        .withDefaultSchemaSpec(CalciteAssert.SchemaSpec.JDBC_FOODMART)
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..1=[{inputs}], EXPR$1=[$t1])\n"
            + "  EnumerableTableScan(table=[[foodmart, MV0]])")
        .ok();
  }

  @Test void testViewProjectWithMultifieldExpressions() {
    // 测试视图多字段表达式：验证多字段表达式的处理
            + " s.\"time_id\" >= 1 or s.\"time_id\" < 3, "
            + " s.\"time_id\" + s.\"time_id\", "
            + " s.\"time_id\" * s.\"time_id\""
            + " from \"foodmart\".\"sales_fact_1997\" as s"
            + " where s.\"store_id\" = 1",
        "select s.\"time_id\""
            + " from \"foodmart\".\"sales_fact_1997\" as s"
            + " where s.\"store_id\" = 1")
        .withDefaultSchemaSpec(CalciteAssert.SchemaSpec.JDBC_FOODMART)
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..4=[{inputs}], time_id=[$t0])\n"
            + "  EnumerableTableScan(table=[[foodmart, MV0]])")
        .ok();
  }

  @Test void testAggregateOnJoinKeys() {
    // 测试连接键上的聚合：验证在连接键上进行聚合的匹配
            + "from \"emps\"\n"
            + "group by \"deptno\", \"empid\", \"salary\"",
        "select \"empid\", \"depts\".\"deptno\" "
            + "from \"emps\"\n"
            + "join \"depts\" on \"depts\".\"deptno\" = \"empid\" group by \"empid\", \"depts\".\"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0=[{inputs}], empid=[$t0], empid0=[$t0])\n"
            + "  EnumerableAggregate(group=[{1}])\n"
            + "    EnumerableHashJoin(condition=[=($1, $3)], joinType=[inner])\n"
            + "      EnumerableTableScan(table=[[hr, MV0]])\n"
            + "      EnumerableTableScan(table=[[hr, depts]])")
        .ok();
  }

  @Test void testAggregateOnJoinKeys2() {
    // 测试连接键上的聚合2：验证在连接键上进行聚合和常量聚合的匹配
            + "from \"emps\"\n"
            + "group by \"deptno\", \"empid\", \"salary\"",
        "select sum(1) "
            + "from \"emps\"\n"
            + "join \"depts\" on \"depts\".\"deptno\" = \"empid\" group by \"empid\", \"depts\".\"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..1=[{inputs}], EXPR$0=[$t1])\n"
            + "  EnumerableAggregate(group=[{1}], EXPR$0=[$SUM0($3)])\n"
            + "    EnumerableHashJoin(condition=[=($1, $4)], joinType=[inner])\n"
            + "      EnumerableTableScan(table=[[hr, MV0]])\n"
            + "      EnumerableTableScan(table=[[hr, depts]])")
        .ok();
  }

  @Test void testAggregateMaterializationOnCountDistinctQuery1() { // 测试COUNT DISTINCT查询物化1：验证COUNT DISTINCT在唯一列上的优化
    // in the COUNT of the resulting rewriting
    // 注意：empid列已经是唯一的，因此COUNT DISTINCT可以优化为COUNT
    sql("select \"deptno\", \"empid\", \"salary\"\n"
            + "from \"emps\"\n"
            + "group by \"deptno\", \"empid\", \"salary\"",
        "select \"deptno\", count(distinct \"empid\") as c from (\n"
            + "select \"deptno\", \"empid\"\n"
            + "from \"emps\"\n"
            + "group by \"deptno\", \"empid\")\n"
            + "group by \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{0}], C=[COUNT($1)])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]]")
        .ok();
  }

  @Test void testAggregateMaterializationOnCountDistinctQuery2() { // 测试COUNT DISTINCT查询物化2：验证COUNT DISTINCT在唯一列上的优化（列顺序不同）
    // in the COUNT of the resulting rewriting
    // 注意：empid列已经是唯一的，因此COUNT DISTINCT可以优化为COUNT
    sql("select \"deptno\", \"salary\", \"empid\"\n"
            + "from \"emps\"\n"
            + "group by \"deptno\", \"salary\", \"empid\"",
        "select \"deptno\", count(distinct \"empid\") as c from (\n"
            + "select \"deptno\", \"empid\"\n"
            + "from \"emps\"\n"
            + "group by \"deptno\", \"empid\")\n"
            + "group by \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{0}], C=[COUNT($2)])\n"
            + "  EnumerableTableScan(table=[[hr, MV0]]")
        .ok();
  }

  @Test void testAggregateMaterializationOnCountDistinctQuery3() { // 测试COUNT DISTINCT查询物化3：验证COUNT DISTINCT在非唯一列上的处理
    // a different rewriting
    // 注意：salary列不是唯一的，因此需要不同的重写策略
    sql("select \"deptno\", \"empid\", \"salary\"\n"
            + "from \"emps\"\n"
            + "group by \"deptno\", \"empid\", \"salary\"",
        "select \"deptno\", count(distinct \"salary\") from (\n"
            + "select \"deptno\", \"salary\"\n"
            + "from \"emps\"\n"
            + "group by \"deptno\", \"salary\")\n"
            + "group by \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{0}], EXPR$1=[COUNT($1)])\n"
            + "  EnumerableAggregate(group=[{0, 2}])\n"
            + "    EnumerableTableScan(table=[[hr, MV0]]")
        .ok();
  }

  @Test void testAggregateMaterializationOnCountDistinctQuery4() { // 测试COUNT DISTINCT查询物化4：验证COUNT与COUNT DISTINCT的等价性
    // equivalent to previous query
    // 注意：虽然COUNT中没有DISTINCT，但这与前一个查询等价
    sql("select \"deptno\", \"salary\", \"empid\"\n"
          + "from \"emps\"\n"
          + "group by \"deptno\", \"salary\", \"empid\"",
        "select \"deptno\", count(\"salary\") from (\n"
            + "select \"deptno\", \"salary\"\n"
            + "from \"emps\"\n"
            + "group by \"deptno\", \"salary\")\n"
            + "group by \"deptno\"")
        .checkingThatResultContains(""
            + "EnumerableAggregate(group=[{0}], EXPR$1=[COUNT()])\n"
            + "  EnumerableAggregate(group=[{0, 1}])\n"
            + "    EnumerableTableScan(table=[[hr, MV0]]")
        .ok();
  }

  @Test public void testNpeInSplitFilterOfSubstitutionVisitor() { // 测试SubstitutionVisitor中的空指针异常：验证过滤器分割的正确性
    sql("select \"col1\", \"col2\""
            + " from \"nullables\""
            + " where \"col1\" <> \"col2\" and \"col3\" = 1",
        "select \"col1\", \"col2\""
            + " from \"nullables\""
            + " where \"col1\" = \"col2\" and \"col3\" = 1")
        .checkingThatResultContains(""
            + "EnumerableCalc(expr#0..2=[{inputs}], expr#3=[=($t0, $t1)], expr#4=[CAST($t2):INTEGER NOT NULL], expr#5=[1], expr#6=[=($t4, $t5)], expr#7=[AND($t3, $t6)], proj#0..1=[{exprs}], $condition=[$t7])\n"
            + "  EnumerableTableScan(table=[[hr, nullables]])")
        .ok();
  }
}
