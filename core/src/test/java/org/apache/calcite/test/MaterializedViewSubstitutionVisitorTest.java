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
// Apache许可证声明，说明代码的版权和使用许可
package org.apache.calcite.test; // 定义包名，该类属于org.apache.calcite.test包

import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入Java类型工厂实现类，用于创建Java类型
import org.apache.calcite.plan.RelOptMaterialization; // 导入物化视图优化器类，表示物化视图的定义
import org.apache.calcite.plan.RelOptPredicateList; // 导入关系表达式谓词列表类，用于存储谓词条件
import org.apache.calcite.plan.SubstitutionVisitor; // 导入替换访问者类，用于物化视图替换的核心逻辑
import org.apache.calcite.plan.hep.HepPlanner; // 导入HepPlanner启发式规划器，用于规则驱动的优化
import org.apache.calcite.plan.hep.HepProgram; // 导入HepProgram类，定义HepPlanner的优化程序
import org.apache.calcite.plan.hep.HepProgramBuilder; // 导入HepProgramBuilder类，用于构建HepProgram
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，表示关系代数表达式
import org.apache.calcite.rel.rules.CoreRules; // 导入核心规则集，包含各种优化规则
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示列的类型
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统接口，定义类型系统行为
import org.apache.calcite.rex.RexBuilder; // 导入Rex表达式构建器，用于构建行表达式
import org.apache.calcite.rex.RexInputRef; // 导入Rex输入引用类，表示对输入字段的引用
import org.apache.calcite.rex.RexLiteral; // 导入Rex字面量类，表示常量值
import org.apache.calcite.rex.RexNode; // 导入Rex节点接口，表示行表达式
import org.apache.calcite.rex.RexSimplify; // 导入Rex简化器，用于简化表达式
import org.apache.calcite.rex.RexUtil; // 导入Rex工具类，提供表达式操作的实用方法
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SQL标准操作符表，包含标准SQL函数和操作符
import org.apache.calcite.util.Pair; // 导入Pair工具类，表示键值对

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.junit.jupiter.api.Disabled; // 导入JUnit5的Disabled注解，用于禁用测试
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，标记测试方法

import java.math.BigDecimal; // 导入Java大数类，用于精确的十进制计算
import java.util.List; // 导入Java列表接口

import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest匹配器，判断相等
import static org.hamcrest.CoreMatchers.notNullValue; // 导入Hamcrest匹配器，判断非空
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest匹配器，判断字符串表示
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入JUnit5断言，判断为假
import static org.junit.jupiter.api.Assertions.assertNull; // 导入JUnit5断言，判断为空
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit5断言，判断为真

/**
 * Unit test for {@link SubstitutionVisitor}.
 * SubstitutionVisitor的单元测试类
 * 
 * 该类专门用于测试物化视图替换访问者(SubstitutionVisitor)的功能。
 * SubstitutionVisitor是Calcite中负责物化视图替换的核心组件，
 * 它能够识别查询是否可以使用物化视图来加速执行，并进行相应的替换。
 * 
 * 测试覆盖了以下场景：
 * 1. 基本过滤条件替换
 * 2. 投影替换
 * 3. 聚合替换（包括分组集、ROLLUP、CUBE）
 * 4. 连接替换
 * 5. 集合操作替换（UNION、INTERSECT）
 * 6. 复杂表达式替换
 * 7. 类型兼容性测试
 * 
 * 每个测试方法都模拟了一个物化视图和一个查询，
 * 验证查询是否能够正确地使用物化视图进行替换。
 */
public class MaterializedViewSubstitutionVisitorTest { // 定义测试类
  private static final HepProgram HEP_PROGRAM = // 定义静态的Hep优化程序，用于规范化关系表达式
      new HepProgramBuilder() // 创建Hep程序构建器
          .addRuleInstance(CoreRules.FILTER_PROJECT_TRANSPOSE) // 添加过滤器和投影转置规则
          .addRuleInstance(CoreRules.FILTER_MERGE) // 添加过滤器合并规则
          .addRuleInstance(CoreRules.FILTER_INTO_JOIN) // 添加过滤器下推到连接规则
          .addRuleInstance(CoreRules.JOIN_CONDITION_PUSH) // 添加连接条件下推规则
          .addRuleInstance(CoreRules.FILTER_AGGREGATE_TRANSPOSE) // 添加过滤器和聚合转置规则
          .addRuleInstance(CoreRules.PROJECT_MERGE) // 添加投影合并规则
          .addRuleInstance(CoreRules.PROJECT_REMOVE) // 添加投影移除规则
          .addRuleInstance(CoreRules.PROJECT_JOIN_TRANSPOSE) // 添加投影和连接转置规则
          .addRuleInstance(CoreRules.PROJECT_SET_OP_TRANSPOSE) // 添加投影和集合操作转置规则
          .addRuleInstance(CoreRules.AGGREGATE_PROJECT_PULL_UP_CONSTANTS) // 添加聚合投影常量上拉规则
          .addRuleInstance(CoreRules.FILTER_TO_CALC) // 添加过滤器转换为Calc规则
          .addRuleInstance(CoreRules.PROJECT_TO_CALC) // 添加投影转换为Calc规则
          .addRuleInstance(CoreRules.FILTER_CALC_MERGE) // 添加过滤器和Calc合并规则
          .addRuleInstance(CoreRules.PROJECT_CALC_MERGE) // 添加投影和Calc合并规则
          .addRuleInstance(CoreRules.CALC_MERGE) // 添加Calc合并规则
          .build(); // 构建Hep程序

  public static final MaterializedViewTester TESTER = // 定义静态的物化视图测试器
      new MaterializedViewTester() { // 创建匿名内部类实例
        @Override protected List<RelNode> optimize(RelNode queryRel, // 重写优化方法，接收查询关系节点
            List<RelOptMaterialization> materializationList) { // 和物化视图列表
          RelOptMaterialization materialization = materializationList.get(0); // 获取第一个物化视图
          SubstitutionVisitor substitutionVisitor = // 创建替换访问者
              new SubstitutionVisitor(canonicalize(materialization.queryRel), // 使用规范化的物化视图查询
                  canonicalize(queryRel)); // 和规范化的查询关系节点
          return substitutionVisitor // 返回替换访问者
              .go(materialization.tableRel); // 执行替换操作，传入物化视图表关系节点
        }

        private RelNode canonicalize(RelNode rel) { // 私有方法，规范化关系节点
          final HepPlanner hepPlanner = new HepPlanner(HEP_PROGRAM); // 创建Hep规划器，使用预定义的优化程序
          hepPlanner.setRoot(rel); // 设置关系节点为根节点
          return hepPlanner.findBestExp(); // 查找并返回最佳表达式
        }
      };

  /** Creates a fixture. */ // 注释：创建测试夹具
  protected MaterializedViewFixture fixture(String query) { // 受保护方法，创建物化视图夹具
    return MaterializedViewFixture.create(query, TESTER); // 调用工厂方法创建夹具，传入查询和测试器
  }

  /** Creates a fixture with a given query. */ // 注释：创建带有给定查询的夹具
  protected final MaterializedViewFixture sql(String materialize, // 受保护方法，创建物化视图夹具
      String query) { // 接收物化视图SQL和查询SQL
    return fixture(query) // 调用fixture方法创建夹具
        .withMaterializations(ImmutableList.of(Pair.of(materialize, "MV0"))); // 添加物化视图列表，包含物化视图SQL和名称"MV0"
  }

  @Test void testFilter() { // 测试方法：测试基本过滤条件替换
    sql("select * from \"emps\" where \"deptno\" = 10", // 物化视图：选择部门号为10的员工
        "select \"empid\" + 1 from \"emps\" where \"deptno\" = 10") // 查询：选择部门号为10的员工ID加1
        .ok(); // 断言替换成功
  }

  @Test void testFilterToProject0() { // 测试方法：测试过滤器到投影的替换
    sql("select *, \"empid\" * 2 from \"emps\"", // 物化视图：选择所有员工并计算员工ID乘以2
        "select * from \"emps\" where (\"empid\" * 2) > 3") // 查询：选择员工ID乘以2大于3的员工
        .ok(); // 断言替换成功
  }

  @Test void testFilterToProject1() { // 测试方法：测试过滤器到投影的替换失败场景
    sql("select \"deptno\", \"salary\" from \"emps\"", // 物化视图：选择部门号和薪资
        "select \"empid\", \"deptno\", \"salary\"\n" // 查询：选择员工ID、部门号和薪资
            + "from \"emps\" where (\"salary\" * 0.8) > 10000") // 条件：薪资乘以0.8大于10000
        .noMat(); // 断言不能使用物化视图
  }

  @Test void testFilterQueryOnProjectView() { // 测试方法：测试过滤查询在投影视图上的替换
    sql("select \"deptno\", \"empid\" from \"emps\"", // 物化视图：选择部门号和员工ID
        "select \"empid\" + 1 as x from \"emps\" where \"deptno\" = 10") // 查询：选择部门号为10的员工ID加1
        .ok(); // 断言替换成功
  }

  /** Runs the same test as {@link #testFilterQueryOnProjectView()} but more
   * concisely. */ // 注释：运行与testFilterQueryOnProjectView相同的测试，但更简洁
  @Test void testFilterQueryOnProjectView0() { // 测试方法：测试过滤查询在投影视图上的替换（简化版）
    sql("select \"deptno\", \"empid\" from \"emps\"", // 物化视图：选择部门号和员工ID
        "select \"empid\" + 1 as x from \"emps\" where \"deptno\" = 10") // 查询：选择部门号为10的员工ID加1
        .ok(); // 断言替换成功
  }

  /** As {@link #testFilterQueryOnProjectView()} but with extra column in
   * materialized view. */ // 注释：与testFilterQueryOnProjectView类似，但物化视图中有额外的列
  @Test void testFilterQueryOnProjectView1() { // 测试方法：测试过滤查询在投影视图上的替换（物化视图有额外列）
    sql("select \"deptno\", \"empid\", \"name\" from \"emps\"", // 物化视图：选择部门号、员工ID和姓名
        "select \"empid\" + 1 as x from \"emps\" where \"deptno\" = 10") // 查询：选择部门号为10的员工ID加1
        .ok(); // 断言替换成功
  }

  /** As {@link #testFilterQueryOnProjectView()} but with extra column in both
   * materialized view and query. */ // 注释：与testFilterQueryOnProjectView类似，但物化视图和查询都有额外列
  @Test void testFilterQueryOnProjectView2() { // 测试方法：测试过滤查询在投影视图上的替换（两者都有额外列）
    sql("select \"deptno\", \"empid\", \"name\" from \"emps\"", // 物化视图：选择部门号、员工ID和姓名
        "select \"empid\" + 1 as x, \"name\" from \"emps\" where \"deptno\" = 10") // 查询：选择部门号为10的员工ID加1和姓名
        .ok(); // 断言替换成功
  }

  @Test void testFilterQueryOnProjectView3() { // 测试方法：测试过滤查询在投影视图上的替换（带表达式）
    sql("select \"deptno\" - 10 as \"x\", \"empid\" + 1, \"name\" from \"emps\"", // 物化视图：选择部门号减10、员工ID加1和姓名
        "select \"name\" from \"emps\" where \"deptno\" - 10 = 0") // 查询：选择部门号减10等于0的员工姓名
        .ok(); // 断言替换成功
  }

  /** As {@link #testFilterQueryOnProjectView3()} but materialized view cannot
   * be used because it does not contain required expression. */ // 注释：与testFilterQueryOnProjectView3类似，但物化视图不能使用，因为它不包含所需的表达式
  @Test void testFilterQueryOnProjectView4() { // 测试方法：测试过滤查询在投影视图上的替换失败（缺少表达式）
    sql(
        "select \"deptno\" - 10 as \"x\", \"empid\" + 1, \"name\" from \"emps\"", // 物化视图：选择部门号减10、员工ID加1和姓名
        "select \"name\" from \"emps\" where \"deptno\" + 10 = 20") // 查询：选择部门号加10等于20的员工姓名
        .noMat(); // 断言不能使用物化视图
  }

  /** As {@link #testFilterQueryOnProjectView3()} but also contains an
   * expression column. */ // 注释：与testFilterQueryOnProjectView3类似，但还包含表达式列
  @Test void testFilterQueryOnProjectView5() { // 测试方法：测试过滤查询在投影视图上的替换（带表达式列）
    sql("select \"deptno\" - 10 as \"x\", \"empid\" + 1 as ee, \"name\" from \"emps\"", // 物化视图：选择部门号减10、员工ID加1和姓名
        "select \"name\", \"empid\" + 1 as e from \"emps\" where \"deptno\" - 10 = 2") // 查询：选择部门号减10等于2的员工姓名和员工ID加1
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalCalc(expr#0..2=[{inputs}], expr#3=[2], " // LogicalCalc节点，表示计算操作
            + "expr#4=[=($t0, $t3)], name=[$t2], E=[$t1], $condition=[$t4])\n" // 表达式：检查部门号减10是否等于2
            + "  EnumerableTableScan(table=[[hr, MV0]]") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  /** Cannot materialize because "name" is not projected in the MV. */ // 注释：不能物化，因为"name"列不在物化视图中
  @Test void testFilterQueryOnProjectView6() { // 测试方法：测试过滤查询在投影视图上的替换失败（缺少列）
    sql("select \"deptno\" - 10 as \"x\", \"empid\"  from \"emps\"", // 物化视图：选择部门号减10和员工ID
        "select \"name\" from \"emps\" where \"deptno\" - 10 = 0") // 查询：选择部门号减10等于0的员工姓名
        .noMat(); // 断言不能使用物化视图
  }

  /** As {@link #testFilterQueryOnProjectView3()} but also contains an
   * expression column. */ // 注释：与testFilterQueryOnProjectView3类似，但还包含表达式列
  @Test void testFilterQueryOnProjectView7() { // 测试方法：测试过滤查询在投影视图上的替换失败（表达式不匹配）
    sql("select \"deptno\" - 10 as \"x\", \"empid\" + 1, \"name\" from \"emps\"", // 物化视图：选择部门号减10、员工ID加1和姓名
        "select \"name\", \"empid\" + 2 from \"emps\" where \"deptno\" - 10 = 0") // 查询：选择部门号减10等于0的员工姓名和员工ID加2
        .noMat(); // 断言不能使用物化视图
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-988">[CALCITE-988]
   * FilterToProjectUnifyRule.invert(MutableRel, MutableRel, MutableProject)
   * works incorrectly</a>. */ // 注释：CALCITE-988问题的测试用例
  @Test void testFilterQueryOnProjectView8() { // 测试方法：测试过滤查询在投影视图上的替换（CALCITE-988）
    String mv = "" // 物化视图SQL
        + "select \"salary\", \"commission\",\n" // 选择薪资和佣金
        + "\"deptno\", \"empid\", \"name\" from \"emps\""; // 以及部门号、员工ID和姓名
    String query = "" // 查询SQL
        + "select *\n" // 选择所有列
        + "from (select * from \"emps\" where \"name\" is null)\n" // 从姓名为空的员工中选择
        + "where \"commission\" is null"; // 条件：佣金为空
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testFilterQueryOnFilterView() { // 测试方法：测试过滤查询在过滤视图上的替换
    sql("select \"deptno\", \"empid\", \"name\" from \"emps\" where \"deptno\" = 10", // 物化视图：选择部门号为10的员工
        "select \"empid\" + 1 as x, \"name\" from \"emps\" where \"deptno\" = 10") // 查询：选择部门号为10的员工ID加1和姓名
        .ok(); // 断言替换成功
  }

  /** As {@link #testFilterQueryOnFilterView()} but condition is stronger in
   * query. */ // 注释：与testFilterQueryOnFilterView类似，但查询中的条件更强
  @Test void testFilterQueryOnFilterView2() { // 测试方法：测试过滤查询在过滤视图上的替换（查询条件更强）
    sql("select \"deptno\", \"empid\", \"name\" from \"emps\" where \"deptno\" = 10", // 物化视图：选择部门号为10的员工
        "select \"empid\" + 1 as x, \"name\" from \"emps\" " // 查询：选择部门号为10且员工ID小于150的员工
            + "where \"deptno\" = 10 and \"empid\" < 150")
        .ok(); // 断言替换成功
  }

  /** As {@link #testFilterQueryOnFilterView()} but condition is weaker in
   * view. */ // 注释：与testFilterQueryOnFilterView类似，但视图中的条件更弱
  @Test void testFilterQueryOnFilterView3() { // 测试方法：测试过滤查询在过滤视图上的替换（视图条件更弱）
    sql("select \"deptno\", \"empid\", \"name\" from \"emps\"\n" // 物化视图：选择部门号为10或20或员工ID小于160的员工
            + "where \"deptno\" = 10 or \"deptno\" = 20 or \"empid\" < 160",
        "select \"empid\" + 1 as x, \"name\" from \"emps\" where \"deptno\" = 10") // 查询：选择部门号为10的员工
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalCalc(expr#0..2=[{inputs}], expr#3=[1], expr#4=[+($t1, $t3)], expr#5=[10], " // LogicalCalc节点
            + "expr#6=[CAST($t0):INTEGER NOT NULL], expr#7=[=($t5, $t6)], X=[$t4], " // 表达式：检查部门号是否等于10
            + "name=[$t2], $condition=[$t7])\n" // 输出X和name列
            + "  EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  /** As {@link #testFilterQueryOnFilterView()} but condition is stronger in
   * query. */ // 注释：与testFilterQueryOnFilterView类似，但查询中的条件更强
  @Test void testFilterQueryOnFilterView4() { // 测试方法：测试过滤查询在过滤视图上的替换（查询条件更强）
    sql("select * from \"emps\" where \"deptno\" > 10", // 物化视图：选择部门号大于10的员工
        "select \"name\" from \"emps\" where \"deptno\" > 30") // 查询：选择部门号大于30的员工姓名
        .ok(); // 断言替换成功
  }

  /** As {@link #testFilterQueryOnFilterView()} but condition is stronger in
   * query and columns selected are subset of columns in materialized
   * view. */ // 注释：与testFilterQueryOnFilterView类似，但查询条件更强且选择的列是物化视图列的子集
  @Test void testFilterQueryOnFilterView5() { // 测试方法：测试过滤查询在过滤视图上的替换（列子集）
    sql("select \"name\", \"deptno\" from \"emps\" where \"deptno\" > 10", // 物化视图：选择部门号大于10的员工姓名和部门号
        "select \"name\" from \"emps\" where \"deptno\" > 30") // 查询：选择部门号大于30的员工姓名
        .ok(); // 断言替换成功
  }

  /** As {@link #testFilterQueryOnFilterView()} but condition is stronger in
   * query and columns selected are subset of columns in materialized
   * view. */ // 注释：与testFilterQueryOnFilterView类似，但查询条件更强且选择的列是物化视图列的子集
  @Test void testFilterQueryOnFilterView6() { // 测试方法：测试过滤查询在过滤视图上的替换（复杂条件）
    sql("select \"name\", \"deptno\", \"salary\" from \"emps\" " // 物化视图：选择薪资大于2000.5的员工姓名、部门号和薪资
          + "where \"salary\" > 2000.5",
        "select \"name\" from \"emps\" where \"deptno\" > 30 and \"salary\" > 3000") // 查询：选择部门号大于30且薪资大于3000的员工姓名
        .ok(); // 断言替换成功
  }

  /** As {@link #testFilterQueryOnFilterView()} but condition is stronger in
   * query and columns selected are subset of columns in materialized
   * view. Condition here is complex. */ // 注释：与testFilterQueryOnFilterView类似，但查询条件更强且条件复杂
  @Test void testFilterQueryOnFilterView7() { // 测试方法：测试过滤查询在过滤视图上的替换（复杂OR条件）
    sql("select * from \"emps\" where " // 物化视图：选择满足复杂条件的员工
            + "((\"salary\" < 1111.9 and \"deptno\" > 10)" // 条件1：薪资小于1111.9且部门号大于10
            + "or (\"empid\" > 400 and \"salary\" > 5000) " // 或条件2：员工ID大于400且薪资大于5000
            + "or \"salary\" > 500)", // 或条件3：薪资大于500
        "select \"name\" from \"emps\" where (\"salary\" > 1000 " // 查询：选择满足复杂条件的员工姓名
            + "or (\"deptno\" >= 30 and \"salary\" <= 500))") // 条件：薪资大于1000或部门号大于等于30且薪资小于等于500
        .ok(); // 断言替换成功
  }

  /** As {@link #testFilterQueryOnFilterView()} but condition is stronger in
   * query. However, columns selected are not present in columns of materialized
   * view, Hence should not use materialized view. */ // 注释：与testFilterQueryOnFilterView类似，但查询选择的列不在物化视图中
  @Test void testFilterQueryOnFilterView8() { // 测试方法：测试过滤查询在过滤视图上的替换失败（缺少列）
    sql("select \"name\", \"deptno\" from \"emps\" where \"deptno\" > 10", // 物化视图：选择部门号大于10的员工姓名和部门号
        "select \"name\", \"empid\" from \"emps\" where \"deptno\" > 30") // 查询：选择部门号大于30的员工姓名和员工ID
        .noMat(); // 断言不能使用物化视图
  }

  /** As {@link #testFilterQueryOnFilterView()} but condition is weaker in
   * query. */ // 注释：与testFilterQueryOnFilterView类似，但查询中的条件更弱
  @Test void testFilterQueryOnFilterView9() { // 测试方法：测试过滤查询在过滤视图上的替换失败（查询条件更弱）
    sql("select \"name\", \"deptno\" from \"emps\" where \"deptno\" > 10", // 物化视图：选择部门号大于10的员工姓名和部门号
        "select \"name\", \"empid\" from \"emps\" " // 查询：选择部门号大于30或员工ID大于10的员工
            + "where \"deptno\" > 30 or \"empid\" > 10")
        .noMat(); // 断言不能使用物化视图
  }

  /** As {@link #testFilterQueryOnFilterView()} but condition currently
   * has unsupported type being checked on query. */ // 注释：与testFilterQueryOnFilterView类似，但查询中有不支持的类型
  @Test void testFilterQueryOnFilterView10() { // 测试方法：测试过滤查询在过滤视图上的替换失败（不支持的类型）
    sql("select \"name\", \"deptno\" from \"emps\" where \"deptno\" > 10 " // 物化视图：选择部门号大于10且姓名为'calcite'的员工
            + "and \"name\" = 'calcite'",
        "select \"name\", \"empid\" from \"emps\" where \"deptno\" > 30 " // 查询：选择部门号大于30或员工ID大于10的员工
            + "or \"empid\" > 10")
        .noMat(); // 断言不能使用物化视图
  }

  /** As {@link #testFilterQueryOnFilterView()} but condition is weaker in
   * query and columns selected are subset of columns in materialized
   * view. Condition here is complex. */ // 注释：与testFilterQueryOnFilterView类似，但查询条件更弱且条件复杂
  @Test void testFilterQueryOnFilterView11() { // 测试方法：测试过滤查询在过滤视图上的替换失败（复杂条件）
    sql("select \"name\", \"deptno\" from \"emps\" where " // 物化视图：选择满足复杂条件的员工姓名和部门号
            + "(\"salary\" < 1111.9 and \"deptno\" > 10)" // 条件1：薪资小于1111.9且部门号大于10
            + "or (\"empid\" > 400 and \"salary\" > 5000)", // 或条件2：员工ID大于400且薪资大于5000
        "select \"name\" from \"emps\" where \"deptno\" > 30 and \"salary\" > 3000") // 查询：选择部门号大于30且薪资大于3000的员工姓名
        .noMat(); // 断言不能使用物化视图
  }

  /** As {@link #testFilterQueryOnFilterView()} but condition of
   * query is stronger but is on the column not present in MV (salary).
   */ // 注释：与testFilterQueryOnFilterView类似，但查询条件更强但在物化视图中不存在的列（薪资）上
  @Test void testFilterQueryOnFilterView12() { // 测试方法：测试过滤查询在过滤视图上的替换失败（条件在缺失列上）
    sql("select \"name\", \"deptno\" from \"emps\" where \"salary\" > 2000.5", // 物化视图：选择薪资大于2000.5的员工姓名和部门号
        "select \"name\" from \"emps\" where \"deptno\" > 30 and \"salary\" > 3000") // 查询：选择部门号大于30且薪资大于3000的员工姓名
        .noMat(); // 断言不能使用物化视图
  }

  /** As {@link #testFilterQueryOnFilterView()} but condition is weaker in
   * query and columns selected are subset of columns in materialized
   * view. Condition here is complex. */ // 注释：与testFilterQueryOnFilterView类似，但查询条件更弱且条件复杂
  @Test void testFilterQueryOnFilterView13() { // 测试方法：测试过滤查询在过滤视图上的替换失败（复杂OR条件）
    sql("select * from \"emps\" where " // 物化视图：选择满足复杂条件的员工
            + "(\"salary\" < 1111.9 and \"deptno\" > 10)" // 条件1：薪资小于1111.9且部门号大于10
            + "or (\"empid\" > 400 and \"salary\" > 5000)", // 或条件2：员工ID大于400且薪资大于5000
        "select \"name\" from \"emps\" where \"salary\" > 1000 " // 查询：选择满足复杂条件的员工姓名
            + "or (\"deptno\" > 30 and \"salary\" > 3000)") // 条件：薪资大于1000或部门号大于30且薪资大于3000
        .noMat(); // 断言不能使用物化视图
  }

  /** As {@link #testFilterQueryOnFilterView7()} but columns in materialized
   * view are a permutation of columns in the query. */ // 注释：与testFilterQueryOnFilterView7类似，但物化视图中的列是查询列的排列
  @Test void testFilterQueryOnFilterView14() { // 测试方法：测试过滤查询在过滤视图上的替换（列排列）
    String q = "select * from \"emps\" where (\"salary\" > 1000 " // 查询：选择满足复杂条件的所有员工
        + "or (\"deptno\" >= 30 and \"salary\" <= 500))"; // 条件：薪资大于1000或部门号大于等于30且薪资小于等于500
    String m = "select \"deptno\", \"empid\", \"name\", \"salary\", \"commission\" " // 物化视图：选择满足复杂条件的员工（列顺序不同）
        + "from \"emps\" as em where "
        + "((\"salary\" < 1111.9 and \"deptno\" > 10)" // 条件1：薪资小于1111.9且部门号大于10
        + "or (\"empid\" > 400 and \"salary\" > 5000) " // 或条件2：员工ID大于400且薪资大于5000
        + "or \"salary\" > 500)"; // 或条件3：薪资大于500
    sql(m, q).ok(); // 断言替换成功
  }

  /** As {@link #testFilterQueryOnFilterView13()} but using alias
   * and condition of query is stronger. */ // 注释：与testFilterQueryOnFilterView13类似，但使用别名且查询条件更强
  @Test void testAlias() { // 测试方法：测试使用别名的替换
    sql("select * from \"emps\" as em where " // 物化视图：选择满足复杂条件的员工（使用别名em）
            + "(em.\"salary\" < 1111.9 and em.\"deptno\" > 10)" // 条件1：em的薪资小于1111.9且em的部门号大于10
            + "or (em.\"empid\" > 400 and em.\"salary\" > 5000)", // 或条件2：em的员工ID大于400且em的薪资大于5000
        "select \"name\" as n from \"emps\" as e where " // 查询：选择满足条件的员工姓名（使用别名e）
            + "(e.\"empid\" > 500 and e.\"salary\" > 6000)").ok(); // 条件：e的员工ID大于500且e的薪资大于6000
  }

  /** Aggregation query at same level of aggregation as aggregation
   * materialization. */ // 注释：聚合查询与聚合物化视图处于相同的聚合级别
  @Test void testAggregate0() { // 测试方法：测试相同级别的聚合替换
    sql("select count(*) as c from \"emps\" group by \"empid\"", // 物化视图：按员工ID分组并计数
        "select count(*) + 1 as c from \"emps\" group by \"empid\"") // 查询：按员工ID分组并计数加1
        .ok(); // 断言替换成功
  }

  /**
   * Aggregation query at same level of aggregation as aggregation
   * materialization but with different row types. */ // 注释：聚合查询与聚合物化视图处于相同的聚合级别，但行类型不同
  @Test void testAggregate1() { // 测试方法：测试相同级别的聚合替换（不同行类型）
    sql("select count(*) as c0 from \"emps\" group by \"empid\"", // 物化视图：按员工ID分组并计数（列名c0）
        "select count(*) as c1 from \"emps\" group by \"empid\"") // 查询：按员工ID分组并计数（列名c1）
        .ok(); // 断言替换成功
  }

  @Test void testAggregate2() { // 测试方法：测试聚合替换（多列分组）
    sql("select \"deptno\", count(*) as c, sum(\"empid\") as s\n" // 物化视图：按部门号分组，计数和求和员工ID
            + "from \"emps\" group by \"deptno\"",
        "select count(*) + 1 as c, \"deptno\" from \"emps\" group by \"deptno\"") // 查询：按部门号分组，计数加1
        .ok(); // 断言替换成功
  }

  @Test void testAggregate3() { // 测试方法：测试聚合替换（子查询）
    String mv = "" // 物化视图SQL
        + "select \"deptno\", sum(\"salary\"), sum(\"commission\"), sum(\"k\")\n" // 按部门号分组，求和薪资、佣金和k
        + "from\n"
        + "  (select \"deptno\", \"salary\", \"commission\", 100 as \"k\"\n" // 子查询：选择部门号、薪资、佣金和常量100
        + "  from \"emps\")\n"
        + "group by \"deptno\"";
    String query = "" // 查询SQL
        + "select \"deptno\", sum(\"salary\"), sum(\"k\")\n" // 按部门号分组，求和薪资和k
        + "from\n"
        + "  (select \"deptno\", \"salary\", 100 as \"k\"\n" // 子查询：选择部门号、薪资和常量100
        + "  from \"emps\")\n"
        + "group by \"deptno\"";
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testAggregate4() { // 测试方法：测试聚合替换（分组列包含在查询条件中）
    String mv = "" // 物化视图SQL
        + "select \"deptno\", \"commission\", sum(\"salary\")\n" // 按部门号和佣金分组，求和薪资
        + "from \"emps\"\n"
        + "group by \"deptno\", \"commission\"";
    String query = "" // 查询SQL
        + "select \"deptno\", sum(\"salary\")\n" // 按部门号分组，求和薪资
        + "from \"emps\"\n"
        + "where \"commission\" = 100\n" // 条件：佣金等于100
        + "group by \"deptno\"";
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testAggregate5() { // 测试方法：测试聚合替换（分组列包含表达式）
    String mv = "" // 物化视图SQL
        + "select \"deptno\" + \"commission\", \"commission\", sum(\"salary\")\n" // 按部门号加佣金和佣金分组，求和薪资
        + "from \"emps\"\n"
        + "group by \"deptno\" + \"commission\", \"commission\"";
    String query = "" // 查询SQL
        + "select \"commission\", sum(\"salary\")\n" // 按佣金分组，求和薪资
        + "from \"emps\"\n"
        + "where \"commission\" * (\"deptno\" + \"commission\") = 100\n" // 条件：佣金乘以部门号加佣金等于100
        + "group by \"commission\"";
    sql(mv, query).ok(); // 断言替换成功
  }

  /**
   * Matching failed because the filtering condition under Aggregate
   * references columns for aggregation. */ // 注释：匹配失败，因为聚合下的过滤条件引用了聚合列
  @Test void testAggregate6() { // 测试方法：测试聚合替换失败（过滤条件引用聚合列）
    String mv = "" // 物化视图SQL
        + "select * from\n"
        + "(select \"deptno\", sum(\"salary\") as \"sum_salary\", sum(\"commission\")\n" // 子查询：按部门号分组，求和薪资和佣金
        + "from \"emps\"\n"
        + "group by \"deptno\")\n"
        + "where \"sum_salary\" > 10"; // 条件：求和薪资大于10
    String query = "" // 查询SQL
        + "select * from\n"
        + "(select \"deptno\", sum(\"salary\") as \"sum_salary\"\n" // 子查询：按部门号分组，求和薪资
        + "from \"emps\"\n"
        + "where \"salary\" > 1000\n" // 条件：薪资大于1000（在聚合前）
        + "group by \"deptno\")\n"
        + "where \"sum_salary\" > 10"; // 条件：求和薪资大于10
    sql(mv, query).noMat(); // 断言不能使用物化视图
  }

  /**
   * There will be a compensating Project added after matching of the Aggregate.
   * This rule targets to test if the Calc can be handled. */ // 注释：聚合匹配后会添加补偿Project，测试Calc是否能处理
  @Test void testCompensatingCalcWithAggregate0() { // 测试方法：测试补偿Calc与聚合
    String mv = "" // 物化视图SQL
        + "select * from\n"
        + "(select \"deptno\", sum(\"salary\") as \"sum_salary\", sum(\"commission\")\n" // 子查询：按部门号分组，求和薪资和佣金
        + "from \"emps\"\n"
        + "group by \"deptno\")\n"
        + "where \"sum_salary\" > 10"; // 条件：求和薪资大于10
    String query = "" // 查询SQL
        + "select * from\n"
        + "(select \"deptno\", sum(\"salary\") as \"sum_salary\"\n" // 子查询：按部门号分组，求和薪资
        + "from \"emps\"\n"
        + "group by \"deptno\")\n"
        + "where \"sum_salary\" > 10"; // 条件：求和薪资大于10
    sql(mv, query).ok(); // 断言替换成功
  }

  /**
   * There will be a compensating Project + Filter added after matching of the Aggregate.
   * This rule targets to test if the Calc can be handled. */ // 注释：聚合匹配后会添加补偿Project+Filter，测试Calc是否能处理
  @Test void testCompensatingCalcWithAggregate1() { // 测试方法：测试补偿Calc与聚合（带过滤条件）
    String mv = "" // 物化视图SQL
        + "select * from\n"
        + "(select \"deptno\", sum(\"salary\") as \"sum_salary\", sum(\"commission\")\n" // 子查询：按部门号分组，求和薪资和佣金
        + "from \"emps\"\n"
        + "group by \"deptno\")\n"
        + "where \"sum_salary\" > 10"; // 条件：求和薪资大于10
    String query = "" // 查询SQL
        + "select * from\n"
        + "(select \"deptno\", sum(\"salary\") as \"sum_salary\"\n" // 子查询：按部门号分组，求和薪资
        + "from \"emps\"\n"
        + "where \"deptno\" >=20\n" // 条件：部门号大于等于20
        + "group by \"deptno\")\n"
        + "where \"sum_salary\" > 10"; // 条件：求和薪资大于10
    sql(mv, query).ok(); // 断言替换成功
  }

  /**
   * There will be a compensating Project + Filter added after matching of the Aggregate.
   * This rule targets to test if the Calc can be handled. */ // 注释：聚合匹配后会添加补偿Project+Filter，测试Calc是否能处理
  @Test void testCompensatingCalcWithAggregate2() { // 测试方法：测试补偿Calc与聚合（多个过滤条件）
    String mv = "" // 物化视图SQL
        + "select * from\n"
        + "(select \"deptno\", sum(\"salary\") as \"sum_salary\", sum(\"commission\")\n" // 子查询：按部门号分组，求和薪资和佣金
        + "from \"emps\"\n"
        + "where \"deptno\" >= 10\n" // 条件：部门号大于等于10
        + "group by \"deptno\")\n"
        + "where \"sum_salary\" > 10"; // 条件：求和薪资大于10
    String query = "" // 查询SQL
        + "select * from\n"
        + "(select \"deptno\", sum(\"salary\") as \"sum_salary\"\n" // 子查询：按部门号分组，求和薪资
        + "from \"emps\"\n"
        + "where \"deptno\" >= 20\n" // 条件：部门号大于等于20
        + "group by \"deptno\")\n"
        + "where \"sum_salary\" > 20"; // 条件：求和薪资大于20
    sql(mv, query).ok(); // 断言替换成功
  }

  /** Aggregation query at same level of aggregation as aggregation
   * materialization with grouping sets. */ // 注释：聚合查询与聚合物化视图处于相同的聚合级别，使用分组集
  @Test void testAggregateGroupSets1() { // 测试方法：测试分组集聚合替换（CUBE）
    sql("select \"empid\", \"deptno\", count(*) as c, sum(\"salary\") as s\n" // 物化视图：按员工ID和部门号的CUBE分组，计数和求和薪资
            + "from \"emps\" group by cube(\"empid\",\"deptno\")",
        "select count(*) + 1 as c, \"deptno\"\n" // 查询：按员工ID和部门号的CUBE分组，计数加1
            + "from \"emps\" group by cube(\"empid\",\"deptno\")")
        .ok(); // 断言替换成功
  }

  /** Aggregation query with different grouping sets, should not
   * do materialization. */ // 注释：聚合查询使用不同的分组集，不应该物化
  @Test void testAggregateGroupSets2() { // 测试方法：测试分组集聚合替换失败（不同分组集）
    sql("select \"empid\", \"deptno\", count(*) as c, sum(\"salary\") as s\n" // 物化视图：按员工ID和部门号的CUBE分组
            + "from \"emps\" group by cube(\"empid\",\"deptno\")",
        "select count(*) + 1 as c, \"deptno\"\n" // 查询：按员工ID和部门号的ROLLUP分组
            + "from \"emps\" group by rollup(\"empid\",\"deptno\")")
        .noMat(); // 断言不能使用物化视图
  }

  /** Aggregation query at coarser level of aggregation than aggregation
   * materialization. Requires an additional aggregate to roll up. Note that
   * COUNT is rolled up using SUM0. */ // 注释：聚合查询处于比聚合物化视图更粗的聚合级别，需要额外的聚合进行上卷。COUNT使用SUM0上卷
  @Test void testAggregateRollUp1() { // 测试方法：测试聚合上卷替换
    sql("select \"empid\", \"deptno\", count(*) as c, sum(\"empid\") as s\n" // 物化视图：按员工ID和部门号分组，计数和求和员工ID
            + "from \"emps\" group by \"empid\", \"deptno\"",
        "select count(*) + 1 as c, \"deptno\" from \"emps\" group by \"deptno\"") // 查询：按部门号分组，计数加1
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalCalc(expr#0..1=[{inputs}], expr#2=[1], " // LogicalCalc节点
            + "expr#3=[+($t1, $t2)], C=[$t3], deptno=[$t0])\n" // 表达式：计数加1
            + "  LogicalAggregate(group=[{1}], agg#0=[$SUM0($2)])\n" // LogicalAggregate节点，使用SUM0上卷COUNT
            + "    EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  /**
   * stddev_pop aggregate function does not support roll up.
   */ // 注释：stddev_pop聚合函数不支持上卷
  @Test void testAggregateRollUp2() { // 测试方法：测试聚合上卷失败（不支持上卷的函数）
    final String mv = "" // 物化视图SQL
        + "select \"empid\", stddev_pop(\"deptno\") " // 按员工ID和部门号分组，计算部门号的标准差
        + "from \"emps\" "
        + "group by \"empid\", \"deptno\"";
    final String query = "" // 查询SQL
        + "select \"empid\", stddev_pop(\"deptno\") " // 按员工ID分组，计算部门号的标准差
        + "from \"emps\" "
        + "group by \"empid\"";
    sql(mv, query).noMat(); // 断言不能使用物化视图
  }

  /** Aggregation query with groupSets at coarser level of aggregation than
   * aggregation materialization. Requires an additional aggregate to roll up.
   * Note that COUNT is rolled up using SUM0. */ // 注释：聚合查询使用分组集，处于比聚合物化视图更粗的聚合级别，需要额外的聚合进行上卷
  @Test void testAggregateGroupSetsRollUp() { // 测试方法：测试分组集聚合上卷替换
    sql("select \"empid\", \"deptno\", count(*) as c, sum(\"salary\") as s\n" // 物化视图：按员工ID和部门号分组，计数和求和薪资
            + "from \"emps\" group by \"empid\", \"deptno\"",
        "select count(*) + 1 as c, \"deptno\"\n" // 查询：按员工ID和部门号的CUBE分组，计数加1
            + "from \"emps\" group by cube(\"empid\",\"deptno\")")
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalCalc(expr#0..2=[{inputs}], expr#3=[1], " // LogicalCalc节点
            + "expr#4=[+($t2, $t3)], C=[$t4], deptno=[$t1])\n" // 表达式：计数加1
            + "  LogicalAggregate(group=[{0, 1}], groups=[[{0, 1}, {0}, {1}, {}]], agg#0=[$SUM0($2)])\n" // LogicalAggregate节点，使用分组集和SUM0上卷
            + "    EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  @Test void testAggregateGroupSetsRollUp2() { // 测试方法：测试分组集聚合上卷替换（另一种情况）
    sql("select \"empid\", \"deptno\", count(*) as c, sum(\"empid\") as s from \"emps\" " // 物化视图：按员工ID和部门号分组，计数和求和员工ID
            + "group by \"empid\", \"deptno\"",
        "select count(*) + 1 as c,  \"deptno\" from \"emps\" group by cube(\"empid\",\"deptno\")") // 查询：按员工ID和部门号的CUBE分组，计数加1
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalCalc(expr#0..2=[{inputs}], expr#3=[1], " // LogicalCalc节点
            + "expr#4=[+($t2, $t3)], C=[$t4], deptno=[$t1])\n" // 表达式：计数加1
            + "  LogicalAggregate(group=[{0, 1}], groups=[[{0, 1}, {0}, {1}, {}]], agg#0=[$SUM0($2)])\n" // LogicalAggregate节点，使用分组集和SUM0上卷
            + "    EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3087">[CALCITE-3087]
   * AggregateOnProjectToAggregateUnifyRule ignores Project incorrectly when its
   * Mapping breaks ordering</a>. */ // 注释：CALCITE-3087问题的测试用例
  @Test void testAggregateOnProject1() { // 测试方法：测试聚合在投影上的替换
    sql("select \"empid\", \"deptno\", count(*) as c, sum(\"empid\") as s from \"emps\" " // 物化视图：按员工ID和部门号分组，计数和求和员工ID
            + "group by \"empid\", \"deptno\"",
        "select count(*) + 1 as c, \"deptno\" from \"emps\" group by \"deptno\", \"empid\""); // 查询：按部门号和员工ID分组，计数加1
  }

  @Test void testAggregateOnProject2() { // 测试方法：测试聚合在投影上的替换（CUBE）
    sql("select \"empid\", \"deptno\", count(*) as c, sum(\"salary\") as s from \"emps\" " // 物化视图：按员工ID和部门号分组，计数和求和薪资
            + "group by \"empid\", \"deptno\"",
        "select count(*) + 1 as c,  \"deptno\" from \"emps\" group by cube(\"deptno\", \"empid\")") // 查询：按部门号和员工号的CUBE分组，计数加1
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalCalc(expr#0..2=[{inputs}], expr#3=[1], " // LogicalCalc节点
            + "expr#4=[+($t2, $t3)], C=[$t4], deptno=[$t1])\n" // 表达式：计数加1
            + "  LogicalAggregate(group=[{0, 1}], groups=[[{0, 1}, {0}, {1}, {}]], agg#0=[$SUM0($2)])\n" // LogicalAggregate节点，使用分组集和SUM0上卷
            + "    EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  @Test void testAggregateOnProject3() { // 测试方法：测试聚合在投影上的替换（ROLLUP）
    sql("select \"empid\", \"deptno\", count(*) as c, sum(\"salary\") as s\n" // 物化视图：按员工ID和部门号分组，计数和求和薪资
            + "from \"emps\" group by \"empid\", \"deptno\"",
        "select count(*) + 1 as c,  \"deptno\"\n" // 查询：按部门号和员工号的ROLLUP分组，计数加1
            + "from \"emps\" group by rollup(\"deptno\", \"empid\")")
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalCalc(expr#0..2=[{inputs}], expr#3=[1], " // LogicalCalc节点
            + "expr#4=[+($t2, $t3)], C=[$t4], deptno=[$t1])\n" // 表达式：计数加1
            + "  LogicalAggregate(group=[{0, 1}], groups=[[{0, 1}, {1}, {}]], agg#0=[$SUM0($2)])\n" // LogicalAggregate节点，使用分组集和SUM0上卷
            + "    EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  @Test void testAggregateOnProject4() { // 测试方法：测试聚合在投影上的替换（多列ROLLUP）
    sql("select \"salary\", \"empid\", \"deptno\", count(*) as c, sum(\"commission\") as s\n" // 物化视图：按薪资、员工ID和部门号分组，计数和求和佣金
            + "from \"emps\" group by \"salary\", \"empid\", \"deptno\"",
        "select count(*) + 1 as c,  \"deptno\"\n" // 查询：按员工号、部门号和薪资的ROLLUP分组，计数加1
            + "from \"emps\" group by rollup(\"empid\", \"deptno\", \"salary\")")
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalCalc(expr#0..3=[{inputs}], expr#4=[1], " // LogicalCalc节点
            + "expr#5=[+($t3, $t4)], C=[$t5], deptno=[$t2])\n" // 表达式：计数加1
            + "  LogicalAggregate(group=[{0, 1, 2}], groups=[[{0, 1, 2}, {1, 2}, {1}, {}]], agg#0=[$SUM0($3)])\n" // LogicalAggregate节点，使用分组集和SUM0上卷
            + "    EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3448">[CALCITE-3448]
   * AggregateOnCalcToAggregateUnifyRule ignores Project incorrectly when
   * there's missing grouping or mapping breaks ordering</a>. */ // 注释：CALCITE-3448问题的测试用例
  @Test void testAggregateOnProject5() { // 测试方法：测试聚合在投影上的替换（缺少分组或映射破坏顺序）
    sql("select \"empid\", \"deptno\", \"name\", count(*) from \"emps\"\n" // 物化视图：按员工ID、部门号和姓名分组，计数
            + "group by \"empid\", \"deptno\", \"name\"",
        "select \"name\", \"empid\", count(*) from \"emps\" group by \"name\", \"empid\"") // 查询：按姓名和员工ID分组，计数
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalCalc(expr#0..2=[{inputs}], name=[$t1], empid=[$t0], EXPR$2=[$t2])\n" // LogicalCalc节点，输出name、empid和EXPR$2
            + "  LogicalAggregate(group=[{0, 2}], EXPR$2=[$SUM0($3)])\n" // LogicalAggregate节点，使用SUM0上卷COUNT
            + "    EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  @Test void testAggregateOnProjectAndFilter() { // 测试方法：测试聚合在投影和过滤上的替换
    String mv = "" // 物化视图SQL
        + "select \"deptno\", sum(\"salary\"), count(1)\n" // 按部门号分组，求和薪资和计数
        + "from \"emps\"\n"
        + "group by \"deptno\"";
    String query = "" // 查询SQL
        + "select \"deptno\", count(1)\n" // 按部门号分组，计数
        + "from \"emps\"\n"
        + "where \"deptno\" = 10\n" // 条件：部门号等于10
        + "group by \"deptno\"";
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testProjectOnProject() { // 测试方法：测试投影在投影上的替换
    String mv = "" // 物化视图SQL
        + "select \"deptno\", sum(\"salary\") + 2, sum(\"commission\")\n" // 按部门号分组，求和薪资加2和求和佣金
        + "from \"emps\"\n"
        + "group by \"deptno\"";
    String query = "" // 查询SQL
        + "select \"deptno\", sum(\"salary\") + 2\n" // 按部门号分组，求和薪资加2
        + "from \"emps\"\n"
        + "group by \"deptno\"";
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testPermutationError() { // 测试方法：测试排列错误
    sql("select min(\"salary\"), count(*), max(\"salary\"), sum(\"salary\"), \"empid\" " // 物化视图：按员工ID分组，计算最小薪资、计数、最大薪资、求和薪资
            + "from \"emps\" group by \"empid\"",
        "select count(*), \"empid\" from \"emps\" group by \"empid\"") // 查询：按员工ID分组，计数
        .ok(); // 断言替换成功
  }

  @Test void testJoinOnLeftProjectToJoin() { // 测试方法：测试左投影到连接的替换
    String mv = "" // 物化视图SQL
        + "select * from\n"
        + "  (select \"deptno\", sum(\"salary\"), sum(\"commission\")\n" // 左子查询：按部门号分组，求和薪资和佣金
        + "  from \"emps\"\n"
        + "  group by \"deptno\") \"A\"\n"
        + "  join\n"
        + "  (select \"deptno\", count(\"name\")\n" // 右子查询：按部门号分组，计数姓名
        + "  from \"depts\"\n"
        + "  group by \"deptno\") \"B\"\n"
        + "  on \"A\".\"deptno\" = \"B\".\"deptno\"";
    String query = "" // 查询SQL
        + "select * from\n"
        + "  (select \"deptno\", sum(\"salary\")\n" // 左子查询：按部门号分组，求和薪资
        + "  from \"emps\"\n"
        + "  group by \"deptno\") \"A\"\n"
        + "  join\n"
        + "  (select \"deptno\", count(\"name\")\n" // 右子查询：按部门号分组，计数姓名
        + "  from \"depts\"\n"
        + "  group by \"deptno\") \"B\"\n"
        + "  on \"A\".\"deptno\" = \"B\".\"deptno\"";
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testJoinOnRightProjectToJoin() { // 测试方法：测试右投影到连接的替换
    String mv = "" // 物化视图SQL
        + "select * from\n"
        + "  (select \"deptno\", sum(\"salary\"), sum(\"commission\")\n" // 左子查询：按部门号分组，求和薪资和佣金
        + "  from \"emps\"\n"
        + "  group by \"deptno\") \"A\"\n"
        + "  join\n"
        + "  (select \"deptno\", count(\"name\")\n" // 右子查询：按部门号分组，计数姓名
        + "  from \"depts\"\n"
        + "  group by \"deptno\") \"B\"\n"
        + "  on \"A\".\"deptno\" = \"B\".\"deptno\"";
    String query = "" // 查询SQL
        + "select * from\n"
        + "  (select \"deptno\", sum(\"salary\"), sum(\"commission\")\n" // 左子查询：按部门号分组，求和薪资和佣金
        + "  from \"emps\"\n"
        + "  group by \"deptno\") \"A\"\n"
        + "  join\n"
        + "  (select \"deptno\"\n" // 右子查询：按部门号分组
        + "  from \"depts\"\n"
        + "  group by \"deptno\") \"B\"\n"
        + "  on \"A\".\"deptno\" = \"B\".\"deptno\"";
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testJoinOnProjectsToJoin() { // 测试方法：测试投影到连接的替换
    String mv = "" // 物化视图SQL
        + "select * from\n"
        + "  (select \"deptno\", sum(\"salary\"), sum(\"commission\")\n" // 左子查询：按部门号分组，求和薪资和佣金
        + "  from \"emps\"\n" // 从员工表
        + "  group by \"deptno\") \"A\"\n" // 按部门号分组，别名为A
        + "  join\n" // 连接
        + "  (select \"deptno\", count(\"name\")\n" // 右子查询：按部门号分组，计数姓名
        + "  from \"depts\"\n" // 从部门表
        + "  group by \"deptno\") \"B\"\n" // 按部门号分组，别名为B
        + "  on \"A\".\"deptno\" = \"B\".\"deptno\""; // 连接条件：A的部门号等于B的部门号
    String query = "" // 查询SQL
        + "select * from\n"
        + "  (select \"deptno\", sum(\"salary\")\n" // 左子查询：按部门号分组，求和薪资
        + "  from \"emps\"\n" // 从员工表
        + "  group by \"deptno\") \"A\"\n" // 按部门号分组，别名为A
        + "  join\n" // 连接
        + "  (select \"deptno\"\n" // 右子查询：按部门号分组
        + "  from \"depts\"\n" // 从部门表
        + "  group by \"deptno\") \"B\"\n" // 按部门号分组，别名为B
        + "  on \"A\".\"deptno\" = \"B\".\"deptno\""; // 连接条件：A的部门号等于B的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testJoinOnCalcToJoin0() { // 测试方法：测试Calc到连接的替换（左表有Calc）
    String mv = "" // 物化视图SQL
        + "select \"emps\".\"empid\", \"emps\".\"deptno\", \"depts\".\"deptno\" from\n" // 选择员工ID、员工部门号、部门部门号
        + "\"emps\" join \"depts\"\n" // 员工表连接部门表
        + "on \"emps\".\"deptno\" = \"depts\".\"deptno\""; // 连接条件：员工部门号等于部门部门号
    String query = "" // 查询SQL
        + "select \"A\".\"empid\", \"A\".\"deptno\", \"depts\".\"deptno\" from\n" // 选择员工ID、员工部门号、部门部门号
        + " (select \"empid\", \"deptno\" from \"emps\" where \"deptno\" > 10) A" // 子查询A：从员工表选择员工ID和部门号，条件是部门号大于10
        + " join \"depts\"\n" // 连接部门表
        + "on \"A\".\"deptno\" = \"depts\".\"deptno\""; // 连接条件：A的部门号等于部门部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testJoinOnCalcToJoin1() { // 测试方法：测试Calc到连接的替换（右表有Calc）
    String mv = "" // 物化视图SQL
        + "select \"emps\".\"empid\", \"emps\".\"deptno\", \"depts\".\"deptno\" from\n" // 选择员工ID、员工部门号、部门部门号
        + "\"emps\" join \"depts\"\n" // 员工表连接部门表
        + "on \"emps\".\"deptno\" = \"depts\".\"deptno\""; // 连接条件：员工部门号等于部门部门号
    String query = "" // 查询SQL
        + "select \"emps\".\"empid\", \"emps\".\"deptno\", \"B\".\"deptno\" from\n" // 选择员工ID、员工部门号、部门部门号
        + "\"emps\" join\n" // 员工表连接
        + "(select \"deptno\" from \"depts\" where \"deptno\" > 10) B\n" // 子查询B：从部门表选择部门号，条件是部门号大于10
        + "on \"emps\".\"deptno\" = \"B\".\"deptno\""; // 连接条件：员工部门号等于B的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testJoinOnCalcToJoin2() { // 测试方法：测试Calc到连接的替换（左右表都有Calc）
    String mv = "" // 物化视图SQL
        + "select \"emps\".\"empid\", \"emps\".\"deptno\", \"depts\".\"deptno\" from\n" // 选择员工ID、员工部门号、部门部门号
        + "\"emps\" join \"depts\"\n" // 员工表连接部门表
        + "on \"emps\".\"deptno\" = \"depts\".\"deptno\""; // 连接条件：员工部门号等于部门部门号
    String query = "" // 查询SQL
        + "select * from\n"
        + "(select \"empid\", \"deptno\" from \"emps\" where \"empid\" > 10) A\n" // 子查询A：从员工表选择员工ID和部门号，条件是员工ID大于10
        + "join\n" // 连接
        + "(select \"deptno\" from \"depts\" where \"deptno\" > 10) B\n" // 子查询B：从部门表选择部门号，条件是部门号大于10
        + "on \"A\".\"deptno\" = \"B\".\"deptno\""; // 连接条件：A的部门号等于B的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testJoinOnCalcToJoin3() { // 测试方法：测试Calc到连接的替换失败（连接条件引用非映射投影）
    String mv = "" // 物化视图SQL
        + "select \"emps\".\"empid\", \"emps\".\"deptno\", \"depts\".\"deptno\" from\n" // 选择员工ID、员工部门号、部门部门号
        + "\"emps\" join \"depts\"\n" // 员工表连接部门表
        + "on \"emps\".\"deptno\" = \"depts\".\"deptno\""; // 连接条件：员工部门号等于部门部门号
    String query = "" // 查询SQL
        + "select * from\n"
        + "(select \"empid\", \"deptno\" + 1 as \"deptno\" from \"emps\" where \"empid\" > 10) A\n" // 子查询A：从员工表选择员工ID和部门号加1，条件是员工ID大于10
        + "join\n" // 连接
        + "(select \"deptno\" from \"depts\" where \"deptno\" > 10) B\n" // 子查询B：从部门表选择部门号，条件是部门号大于10
        + "on \"A\".\"deptno\" = \"B\".\"deptno\""; // 连接条件：A的部门号等于B的部门号
    // Match failure because join condition references non-mapping projects. // 匹配失败，因为连接条件引用了非映射的投影
    sql(mv, query).noMat(); // 断言不能使用物化视图
  }

  @Test void testJoinOnCalcToJoin4() { // 测试方法：测试Calc到连接的替换失败（外连接类型且Calc过滤条件非空）
    String mv = "" // 物化视图SQL
        + "select \"emps\".\"empid\", \"emps\".\"deptno\", \"depts\".\"deptno\" from\n" // 选择员工ID、员工部门号、部门部门号
        + "\"emps\" join \"depts\"\n" // 员工表连接部门表
        + "on \"emps\".\"deptno\" = \"depts\".\"deptno\""; // 连接条件：员工部门号等于部门部门号
    String query = "" // 查询SQL
        + "select * from\n"
        + "(select \"empid\", \"deptno\" from \"emps\" where \"empid\" is not null) A\n" // 子查询A：从员工表选择员工ID和部门号，条件是员工ID非空
        + "full join\n" // 全外连接
        + "(select \"deptno\" from \"depts\" where \"deptno\" is not null) B\n" // 子查询B：从部门表选择部门号，条件是部门号非空
        + "on \"A\".\"deptno\" = \"B\".\"deptno\""; // 连接条件：A的部门号等于B的部门号
    // Match failure because of outer join type but filtering condition in Calc is not empty. // 匹配失败，因为外连接类型但Calc中的过滤条件非空
    sql(mv, query).noMat(); // 断言不能使用物化视图
  }

  @Test void testJoinMaterialization() { // 测试方法：测试连接物化
    String q = "select *\n" // 查询SQL
        + "from (select * from \"emps\" where \"empid\" < 300)\n" // 子查询：从员工表选择员工ID小于300的员工
        + "join \"depts\" using (\"deptno\")"; // 使用部门号连接部门表
    sql("select * from \"emps\" where \"empid\" < 500", q).ok(); // 物化视图：选择员工ID小于500的员工，断言替换成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-891">[CALCITE-891]
   * TableScan without Project cannot be substituted by any projected
   * materialization</a>. */ // 注释：CALCITE-891问题的测试用例
  @Test void testJoinMaterialization2() { // 测试方法：测试连接物化（没有投影的表扫描）
    String q = "select *\n" // 查询SQL
        + "from \"emps\"\n" // 从员工表
        + "join \"depts\" using (\"deptno\")"; // 使用部门号连接部门表
    String m = "select \"deptno\", \"empid\", \"name\",\n" // 物化视图SQL：选择部门号、员工ID、姓名
        + "\"salary\", \"commission\" from \"emps\""; // 和薪资、佣金
    sql(m, q).ok(); // 断言替换成功
  }

  @Test void testJoinMaterialization3() { // 测试方法：测试连接物化（带过滤条件）
    String q = "select \"empid\" \"deptno\" from \"emps\"\n" // 查询SQL：从员工表选择员工ID和部门号
        + "join \"depts\" using (\"deptno\") where \"empid\" = 1"; // 使用部门号连接部门表，条件是员工ID等于1
    String m = "select \"empid\" \"deptno\" from \"emps\"\n" // 物化视图SQL：从员工表选择员工ID和部门号
        + "join \"depts\" using (\"deptno\")"; // 使用部门号连接部门表
    sql(m, q).ok(); // 断言替换成功
  }

  @Test void testUnionAll() { // 测试方法：测试UNION ALL替换
    String q = "select * from \"emps\" where \"empid\" > 300\n" // 查询SQL：选择员工ID大于300的员工
        + "union all select * from \"emps\" where \"empid\" < 200"; // UNION ALL员工ID小于200的员工
    String m = "select * from \"emps\" where \"empid\" < 500"; // 物化视图：选择员工ID小于500的员工
    sql(m, q) // 执行SQL
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalUnion(all=[true])\n" // LogicalUnion节点，all为true表示UNION ALL
            + "  LogicalCalc(expr#0..4=[{inputs}], expr#5=[300], expr#6=[>($t0, $t5)], proj#0..4=[{exprs}], $condition=[$t6])\n" // LogicalCalc节点，条件是员工ID大于300
            + "    LogicalTableScan(table=[[hr, emps]])\n" // 表扫描节点，扫描员工表
            + "  LogicalCalc(expr#0..4=[{inputs}], expr#5=[200], expr#6=[<($t0, $t5)], proj#0..4=[{exprs}], $condition=[$t6])\n" // LogicalCalc节点，条件是员工ID小于200
            + "    EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  @Test void testTableModify() { // 测试方法：测试表修改操作
    String m = "select \"deptno\", \"empid\", \"name\"" // 物化视图SQL：选择部门号、员工ID、姓名
        + "from \"emps\" where \"deptno\" = 10"; // 条件：部门号等于10
    String q = "upsert into \"dependents\"" // 查询SQL：插入或更新到dependents表
        + "select \"empid\" + 1 as x, \"name\"" // 选择员工ID加1和姓名
        + "from \"emps\" where \"deptno\" = 10"; // 条件：部门号等于10
    sql(m, q).ok(); // 断言替换成功
  }

  @Test void testSingleMaterializationMultiUsage() { // 测试方法：测试单个物化视图多次使用
    String q = "select *\n" // 查询SQL
        + "from (select * from \"emps\" where \"empid\" < 300)\n" // 子查询1：选择员工ID小于300的员工
        + "join (select * from \"emps\" where \"empid\" < 200) using (\"empid\")"; // 子查询2：选择员工ID小于200的员工，使用员工ID连接
    String m = "select * from \"emps\" where \"empid\" < 500"; // 物化视图：选择员工ID小于500的员工
    sql(m, q) // 执行SQL
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalCalc(expr#0..9=[{inputs}], proj#0..4=[{exprs}], deptno0=[$t6], name0=[$t7], salary0=[$t8], commission0=[$t9])\n" // LogicalCalc节点，输出投影列和额外的列
            + "  LogicalJoin(condition=[=($0, $5)], joinType=[inner])\n" // LogicalJoin节点，内连接
            + "    LogicalCalc(expr#0..4=[{inputs}], expr#5=[300], expr#6=[<($t0, $t5)], proj#0..4=[{exprs}], $condition=[$t6])\n" // 左子节点：LogicalCalc，条件是员工ID小于300
            + "      EnumerableTableScan(table=[[hr, MV0]])\n" // 表扫描节点，扫描物化视图MV0
            + "    LogicalCalc(expr#0..4=[{inputs}], expr#5=[200], expr#6=[<($t0, $t5)], proj#0..4=[{exprs}], $condition=[$t6])\n" // 右子节点：LogicalCalc，条件是员工ID小于200
            + "      EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  @Test void testMaterializationOnJoinQuery() { // 测试方法：测试连接查询上的物化
    sql("select * from \"emps\" where \"empid\" < 500", // 物化视图：选择员工ID小于500的员工
        "select *\n" // 查询SQL
            + "from \"emps\"\n" // 从员工表
            + "join \"depts\" using (\"deptno\") where \"empid\" < 300 ") // 使用部门号连接部门表，条件是员工ID小于300
        .ok(); // 断言替换成功
  }

  @Test void testMaterializationAfterTrimingOfUnusedFields() { // 测试方法：测试修剪未使用字段后的物化
    String sql = // SQL语句
        "select \"y\".\"deptno\", \"y\".\"name\", \"x\".\"sum_salary\"\n" // 选择部门号、姓名、求和薪资
            + "from\n"
            + "  (select \"deptno\", sum(\"salary\") \"sum_salary\"\n" // 子查询x：按部门号分组，求和薪资
            + "  from \"emps\"\n" // 从员工表
            + "  group by \"deptno\") \"x\"\n" // 按部门号分组，别名为x
            + "  join\n" // 连接
            + "  \"depts\" \"y\"\n" // 部门表，别名为y
            + "  on \"x\".\"deptno\"=\"y\".\"deptno\"\n"; // 连接条件：x的部门号等于y的部门号
    sql(sql, sql).ok(); // 物化视图和查询相同，断言替换成功
  }

  @Test void testUnionAllToUnionAll() { // 测试方法：测试UNION ALL到UNION ALL的替换
    String sql0 = "select * from \"emps\" where \"empid\" < 300"; // SQL0：选择员工ID小于300的员工
    String sql1 = "select * from \"emps\" where \"empid\" > 200"; // SQL1：选择员工ID大于200的员工
    sql(sql0 + " union all " + sql1, sql1 + " union all " + sql0).ok(); // 物化视图是sql0 UNION ALL sql1，查询是sql1 UNION ALL sql0，断言替换成功
  }

  @Test void testUnionDistinctToUnionDistinct() { // 测试方法：测试UNION DISTINCT到UNION DISTINCT的替换
    String sql0 = "select * from \"emps\" where \"empid\" < 300"; // SQL0：选择员工ID小于300的员工
    String sql1 = "select * from \"emps\" where \"empid\" > 200"; // SQL1：选择员工ID大于200的员工
    sql(sql0 + " union " + sql1, sql1 + " union " + sql0).ok(); // 物化视图是sql0 UNION sql1，查询是sql1 UNION sql0，断言替换成功
  }

  @Test void testUnionDistinctToUnionAll() { // 测试方法：测试UNION DISTINCT到UNION ALL的替换失败
    String sql0 = "select * from \"emps\" where \"empid\" < 300"; // SQL0：选择员工ID小于300的员工
    String sql1 = "select * from \"emps\" where \"empid\" > 200"; // SQL1：选择员工ID大于200的员工
    sql(sql0 + " union " + sql1, sql0 + " union all " + sql1).noMat(); // 物化视图是UNION，查询是UNION ALL，断言不能使用物化视图
  }

  @Test void testUnionOnCalcsToUnion() { // 测试方法：测试Calc上的UNION到UNION的替换
    String mv = "" // 物化视图SQL
        + "select \"deptno\", \"salary\"\n" // 选择部门号和薪资
        + "from \"emps\"\n" // 从员工表
        + "where \"empid\" > 300\n" // 条件：员工ID大于300
        + "union all\n" // UNION ALL
        + "select \"deptno\", \"salary\"\n" // 选择部门号和薪资
        + "from \"emps\"\n" // 从员工表
        + "where \"empid\" < 100"; // 条件：员工ID小于100
    String query = "" // 查询SQL
        + "select \"deptno\", \"salary\" * 2\n" // 选择部门号和薪资乘以2
        + "from \"emps\"\n" // 从员工表
        + "where \"empid\" > 300 and \"salary\" > 100\n" // 条件：员工ID大于300且薪资大于100
        + "union all\n" // UNION ALL
        + "select \"deptno\", \"salary\" * 2\n" // 选择部门号和薪资乘以2
        + "from \"emps\"\n" // 从员工表
        + "where \"empid\" < 100 and \"salary\" > 100"; // 条件：员工ID小于100且薪资大于100
    sql(mv, query).ok(); // 断言替换成功
  }


  @Test void testIntersectOnCalcsToIntersect() { // 测试方法：测试Calc上的INTERSECT到INTERSECT的替换
    final String mv = "" // 物化视图SQL
        + "select \"deptno\", \"salary\"\n" // 选择部门号和薪资
        + "from \"emps\"\n" // 从员工表
        + "where \"empid\" > 300\n" // 条件：员工ID大于300
        + "intersect all\n" // INTERSECT ALL
        + "select \"deptno\", \"salary\"\n" // 选择部门号和薪资
        + "from \"emps\"\n" // 从员工表
        + "where \"empid\" < 100"; // 条件：员工ID小于100
    final String query = "" // 查询SQL
        + "select \"deptno\", \"salary\" * 2\n" // 选择部门号和薪资乘以2
        + "from \"emps\"\n" // 从员工表
        + "where \"empid\" > 300 and \"salary\" > 100\n" // 条件：员工ID大于300且薪资大于100
        + "intersect all\n" // INTERSECT ALL
        + "select \"deptno\", \"salary\" * 2\n" // 选择部门号和薪资乘以2
        + "from \"emps\"\n" // 从员工表
        + "where \"empid\" < 100 and \"salary\" > 100"; // 条件：员工ID小于100且薪资大于100
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testIntersectToIntersect0() { // 测试方法：测试INTERSECT到INTERSECT的替换
    final String mv = "" // 物化视图SQL
        + "select \"deptno\" from \"emps\"\n" // 从员工表选择部门号
        + "intersect\n" // INTERSECT
        + "select \"deptno\" from \"depts\""; // 从部门表选择部门号
    final String query = "" // 查询SQL
        + "select \"deptno\" from \"depts\"\n" // 从部门表选择部门号
        + "intersect\n" // INTERSECT
        + "select \"deptno\" from \"emps\""; // 从员工表选择部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testIntersectToIntersect1() { // 测试方法：测试INTERSECT ALL到INTERSECT ALL的替换
    final String mv = "" // 物化视图SQL
        + "select \"deptno\" from \"emps\"\n" // 从员工表选择部门号
        + "intersect all\n" // INTERSECT ALL
        + "select \"deptno\" from \"depts\""; // 从部门表选择部门号
    final String query = "" // 查询SQL
        + "select \"deptno\" from \"depts\"\n" // 从部门表选择部门号
        + "intersect all\n" // INTERSECT ALL
        + "select \"deptno\" from \"emps\""; // 从员工表选择部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testIntersectToCalcOnIntersect() { // 测试方法：测试INTERSECT到Calc上的INTERSECT的替换
    final String intersect = "" // INTERSECT语句
        + "select \"deptno\",\"name\" from \"emps\"\n" // 从员工表选择部门号和姓名
        + "intersect all\n" // INTERSECT ALL
        + "select \"deptno\",\"name\" from \"depts\""; // 从部门表选择部门号和姓名
    final String mv = "select \"name\", \"deptno\" from (" + intersect + ")"; // 物化视图：从intersect结果选择姓名和部门号

    final String query = "" // 查询SQL
        + "select \"name\",\"deptno\" from \"depts\"\n" // 从部门表选择姓名和部门号
        + "intersect all\n" // INTERSECT ALL
        + "select \"name\",\"deptno\" from \"emps\""; // 从员工表选择姓名和部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testConstantFilterInAgg() { // 测试方法：测试聚合中的常量过滤器
    final String mv = "" // 物化视图SQL
        + "select \"name\", count(distinct \"deptno\") as cnt\n" // 按姓名分组，计数不同的部门号
        + "from \"emps\" group by \"name\""; // 从员工表按姓名分组
    final String query = "" // 查询SQL
        + "select count(distinct \"deptno\") as cnt\n" // 计数不同的部门号
        + "from \"emps\" where \"name\" = 'hello'"; // 条件：姓名等于'hello'
    sql(mv, query) // 执行SQL
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalCalc(expr#0..1=[{inputs}], expr#2=['hello':VARCHAR], expr#3=[CAST($t0)" // LogicalCalc节点，检查姓名是否等于'hello'
            + ":VARCHAR], expr#4=[=($t2, $t3)], CNT=[$t1], $condition=[$t4])\n" // 输出CNT列
            + "  EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  @Test void testConstantFilterInAgg2() { // 测试方法：测试聚合中的常量过滤器（多列分组）
    final String mv = "" // 物化视图SQL
        + "select \"name\", \"deptno\", count(distinct \"commission\") as cnt\n" // 按姓名和部门号分组，计数不同的佣金
        + "from \"emps\"\n" // 从员工表
        + " group by \"name\", \"deptno\""; // 按姓名和部门号分组
    final String query = "" // 查询SQL
        + "select \"deptno\", count(distinct \"commission\") as cnt\n" // 按部门号分组，计数不同的佣金
        + "from \"emps\" where \"name\" = 'hello'\n" // 条件：姓名等于'hello'
        + "group by \"deptno\""; // 按部门号分组
    sql(mv, query) // 执行SQL
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalCalc(expr#0..2=[{inputs}], expr#3=['hello':VARCHAR], expr#4=[CAST($t0)" // LogicalCalc节点，检查姓名是否等于'hello'
            + ":VARCHAR], expr#5=[=($t3, $t4)], deptno=[$t1], CNT=[$t2], $condition=[$t5])\n" // 输出deptno和CNT列
            + "  EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  @Test void testConstantFilterInAgg3() { // 测试方法：测试聚合中的常量过滤器（多个常量条件）
    final String mv = "" // 物化视图SQL
        + "select \"name\", \"deptno\", count(distinct \"commission\") as cnt\n" // 按姓名和部门号分组，计数不同的佣金
        + "from \"emps\"\n" // 从员工表
        + " group by \"name\", \"deptno\""; // 按姓名和部门号分组
    final String query = "" // 查询SQL
        + "select \"deptno\", count(distinct \"commission\") as cnt\n" // 按部门号分组，计数不同的佣金
        + "from \"emps\" where \"name\" = 'hello' and \"deptno\" = 1\n" // 条件：姓名等于'hello'且部门号等于1
        + "group by \"deptno\""; // 按部门号分组
    sql(mv, query) // 执行SQL
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "LogicalCalc(expr#0..2=[{inputs}], expr#3=['hello':VARCHAR], expr#4=[CAST($t0)" // LogicalCalc节点，检查姓名是否等于'hello'且部门号是否等于1
            + ":VARCHAR], expr#5=[=($t3, $t4)], expr#6=[1], expr#7=[CAST($t1):INTEGER NOT NULL], "
            + "expr#8=[=($t6, $t7)], expr#9=[AND($t5, $t8)], deptno=[$t1], CNT=[$t2], "
            + "$condition=[$t9])\n" // 输出deptno和CNT列
            + "  EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  @Test void testConstantFilterInAgg4() { // 测试方法：测试聚合中的常量过滤器失败（查询需要额外的列）
    final String mv = "" // 物化视图SQL
        + "select \"name\", \"deptno\", count(distinct \"commission\") as cnt\n" // 按姓名和部门号分组，计数不同的佣金
        + "from \"emps\"\n" // 从员工表
        + " group by \"name\", \"deptno\""; // 按姓名和部门号分组
    final String query = "" // 查询SQL
        + "select \"deptno\", \"commission\", count(distinct \"commission\") as cnt\n" // 按部门号和佣金分组，计数不同的佣金
        + "from \"emps\" where \"name\" = 'hello' and \"deptno\" = 1\n" // 条件：姓名等于'hello'且部门号等于1
        + "group by \"deptno\", \"commission\""; // 按部门号和佣金分组
    sql(mv, query).noMat(); // 断言不能使用物化视图
  }

  @Test void testConstantFilterInAggUsingSubquery() { // 测试方法：测试使用子查询的聚合常量过滤器
    final String mv = "" // 物化视图SQL
        + "select \"name\", count(distinct \"deptno\") as cnt " // 按姓名分组，计数不同的部门号
        + "from \"emps\" group by \"name\""; // 从员工表按姓名分组
    final String query = "" // 查询SQL
        + "select cnt from(\n" // 从子查询选择cnt
        + " select \"name\", count(distinct \"deptno\") as cnt " // 子查询：按姓名分组，计数不同的部门号
        + " from \"emps\" group by \"name\") t\n" // 从员工表按姓名分组，别名为t
        + "where \"name\" = 'hello'"; // 条件：姓名等于'hello'
    sql(mv, query).ok(); // 断言替换成功
  }
  /** Unit test for FilterBottomJoin can be pulled up. */ // 注释：单元测试：FilterBottomJoin可以被上拉
  @Test void testLeftFilterOnLeftJoinToJoinOk1() { // 测试方法：测试左连接左表过滤器的替换
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "left join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\" where \"empid\" > 10) \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名，条件是员工ID大于10
        + "left join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testLeftFilterOnLeftJoinToJoinOk2() { // 测试方法：测试左连接左表过滤器的替换（更强的过滤条件）
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\" where \"empid\" > 10) \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名，条件是员工ID大于10
        + "left join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\" where \"empid\" > 30) \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名，条件是员工ID大于30
        + "left join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testRightFilterOnLeftJoinToJoinFail() { // 测试方法：测试左连接右表过滤器的替换失败
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "left join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "left join (select \"deptno\", \"name\" from \"depts\" where \"name\" is not null) \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、姓名，条件是姓名非空
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).noMat(); // 断言不能使用物化视图
  }

  @Test void testRightFilterOnRightJoinToJoinOk() { // 测试方法：测试右连接右表过滤器的替换
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "right join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 右连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "right join (select \"deptno\", \"name\" from \"depts\" where \"name\" is not null) \"t2\"\n" // 右连接子查询t2：从部门表选择部门号、姓名，条件是姓名非空
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6534">[CALCITE-6534]
   * Adjust type when pulling up Calc in JoinUnifyRule</a>. */ // 注释：CALCITE-6534问题的测试用例
  @Test void testLeftProjectOnRightJoinToJoinOk() { // 测试方法：测试右连接左投影的替换
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "right join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 右连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"empid\" a, \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、员工ID（别名为a）、部门号、姓名
        + "right join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 右连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6534">[CALCITE-6534]
   * Adjust type when pulling up Calc in JoinUnifyRule</a>. */ // 注释：CALCITE-6534问题的测试用例
  @Test void testLeftProjectOnFullJoinToJoinOk() { // 测试方法：测试全连接左投影的替换
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "full join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"empid\" a, \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、员工ID（别名为a）、部门号、姓名
        + "full join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6534">[CALCITE-6534]
   * Adjust type when pulling up Calc in JoinUnifyRule</a>. */ // 注释：CALCITE-6534问题的测试用例
  @Test void testRightProjectOnLeftJoinToJoinOk2() { // 测试方法：测试左连接右投影的替换
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "left join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "left join (select \"deptno\", \"deptno\" a, \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、部门号（别名为a）、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testRightProjectOnLeftJoinToJoinOk() { // 测试方法：测试左连接右投影的替换
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "left join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "left join (select \"deptno\", \"deptno\" a, \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、部门号（别名为a）、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6534">[CALCITE-6534]
   * Adjust type when pulling up Calc in JoinUnifyRule</a>. */ // 注释：CALCITE-6534问题的测试用例
  @Test void testRightProjectOnFullJoinToJoinOk() { // 测试方法：测试全连接右投影的替换
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "full join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "full join (select \"deptno\", \"deptno\" a, \"name\" from \"depts\") \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、部门号（别名为a）、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6534">[CALCITE-6534]
   * Adjust type when pulling up Calc in JoinUnifyRule</a>. */ // 注释：CALCITE-6534问题的测试用例
  @Test void testLeftProjectAndRightProjectOnLeftJoinToJoinOk() { // 测试方法：测试左连接左右投影的替换
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "left join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"empid\" a, \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、员工ID（别名为a）、部门号、姓名
        + "left join (select \"deptno\", \"deptno\" a, \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、部门号（别名为a）、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6534">[CALCITE-6534]
   * Adjust type when pulling up Calc in JoinUnifyRule</a>. */ // 注释：CALCITE-6534问题的测试用例
  @Test void testLeftProjectAndRightProjectOnRightJoinToJoinOk() { // 测试方法：测试右连接左右投影的替换
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "right join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 右连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"empid\" a, \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、员工ID（别名为a）、部门号、姓名
        + "right join (select \"deptno\", \"deptno\" a, \"name\" from \"depts\") \"t2\"\n" // 右连接子查询t2：从部门表选择部门号、部门号（别名为a）、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6534">[CALCITE-6534]
   * Adjust type when pulling up Calc in JoinUnifyRule</a>. */ // 注释：CALCITE-6534问题的测试用例
  @Test void testLeftProjectAndRightProjectOnFullJoinToJoinOk() { // 测试方法：测试全连接左右投影的替换
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "full join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "full join (select \"deptno\", FLOOR(\"deptno\") a, \"name\" from \"depts\") \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、部门号向下取整（别名为a）、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).ok(); // 断言替换成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6534">[CALCITE-6534]
   * Adjust type when pulling up Calc in JoinUnifyRule</a>. */ // 注释：CALCITE-6534问题的测试用例
  @Test void testInnerJoinAdjustType() { // 测试方法：测试内连接类型调整
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 内连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String leftCalcQuery = "select * from \n" // 左Calc查询SQL
        + "(select \"empid\", CASE WHEN \"empid\" = 1 THEN 0 ELSE 1 END,\n" // 子查询：从员工表选择员工ID、CASE表达式（员工ID等于1返回0否则返回1）
        + "\"deptno\", \"name\" from \"emps\") \"t1\"\n" // 部门号、姓名
        + "join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 内连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String rightCalcQuery = "select * from \n" // 右Calc查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "join (select \"deptno\", \"deptno\" IS NULL,\"name\" from \"depts\") \"t2\"\n" // 内连接子查询t2：从部门表选择部门号、部门号是否为NULL、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String leftAndRightCalcQuery = "select * from \n" // 左右Calc查询SQL
        + "(select \"empid\", CASE WHEN \"empid\" = 1 THEN 0 ELSE \"empid\" END,\n" // 子查询：从员工表选择员工ID、CASE表达式（员工ID等于1返回0否则返回员工ID）
        + "\"deptno\", \"name\" from \"emps\") \"t1\"\n" // 部门号、姓名
        + "join (select \"deptno\", \"deptno\" IS NOT NULL, \"name\" from \"depts\") \"t2\"\n" // 内连接子查询t2：从部门表选择部门号、部门号是否非NULL、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, leftCalcQuery).ok(); // 测试左Calc查询，断言替换成功
    sql(mv, rightCalcQuery).ok(); // 测试右Calc查询，断言替换成功
    sql(mv, leftAndRightCalcQuery).ok(); // 测试左右Calc查询，断言替换成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6534">[CALCITE-6534]
   * Adjust type when pulling up Calc in JoinUnifyRule</a>. */ // 注释：CALCITE-6534问题的测试用例
  @Test void testLeftJoinAdjustType() { // 测试方法：测试左连接类型调整
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "left join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String leftCalcQuery = "select * from \n" // 左Calc查询SQL
        + "(select \"empid\", CASE WHEN \"empid\" = 1 THEN 0 ELSE 1 END,\n" // 子查询：从员工表选择员工ID、CASE表达式（员工ID等于1返回0否则返回1）
        + "\"deptno\", \"name\" from \"emps\") \"t1\"\n" // 部门号、姓名
        + "left join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String rightCalcQuery = "select * from \n" // 右Calc查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "left join (select \"deptno\", \"deptno\" IS NULL,\"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、部门号是否为NULL、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String leftAndRightCalcQuery = "select * from \n" // 左右Calc查询SQL
        + "(select \"empid\", CASE WHEN \"empid\" = 1 THEN 0 ELSE \"empid\" END,\n" // 子查询：从员工表选择员工ID、CASE表达式（员工ID等于1返回0否则返回员工ID）
        + "\"deptno\", \"name\" from \"emps\") \"t1\"\n" // 部门号、姓名
        + "left join (select \"deptno\", \"deptno\" IS NOT NULL, \"name\" from \"depts\") \"t2\"\n" // 左连接子查询t2：从部门表选择部门号、部门号是否非NULL、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, leftCalcQuery).ok(); // 测试左Calc查询，断言替换成功
    sql(mv, rightCalcQuery).noMat(); // 测试右Calc查询，断言不能使用物化视图
    sql(mv, leftAndRightCalcQuery).noMat(); // 测试左右Calc查询，断言不能使用物化视图
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6534">[CALCITE-6534]
   * Adjust type when pulling up Calc in JoinUnifyRule</a>. */ // 注释：CALCITE-6534问题的测试用例
  @Test void testRightJoinAdjustType() { // 测试方法：测试右连接类型调整
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "right join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 右连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String leftCalcQuery = "select * from \n" // 左Calc查询SQL
        + "(select \"empid\", CASE WHEN \"empid\" = 1 THEN 0 ELSE 1 END,\n" // 子查询：从员工表选择员工ID、CASE表达式（员工ID等于1返回0否则返回1）
        + "\"deptno\", \"name\" from \"emps\") \"t1\"\n" // 部门号、姓名
        + "right join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 右连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String rightCalcQuery = "select * from \n" // 右Calc查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "right join (select \"deptno\", \"deptno\" IS NULL,\"name\" from \"depts\") \"t2\"\n" // 右连接子查询t2：从部门表选择部门号、部门号是否为NULL、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String leftAndRightCalcQuery = "select * from \n" // 左右Calc查询SQL
        + "(select \"empid\", CASE WHEN \"empid\" = 1 THEN 0 ELSE \"empid\" END,\n" // 子查询：从员工表选择员工ID、CASE表达式（员工ID等于1返回0否则返回员工ID）
        + "\"deptno\", \"name\" from \"emps\") \"t1\"\n" // 部门号、姓名
        + "right join (select \"deptno\", \"deptno\" IS NOT NULL, \"name\" from \"depts\") \"t2\"\n" // 右连接子查询t2：从部门表选择部门号、部门号是否非NULL、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, leftCalcQuery).noMat(); // 测试左Calc查询，断言不能使用物化视图
    sql(mv, rightCalcQuery).ok(); // 测试右Calc查询，断言替换成功
    sql(mv, leftAndRightCalcQuery).noMat(); // 测试左右Calc查询，断言不能使用物化视图
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6534">[CALCITE-6534]
   * Adjust type when pulling up Calc in JoinUnifyRule</a>. */ // 注释：CALCITE-6534问题的测试用例
  @Test void testFullJoinAdjustType() { // 测试方法：测试全连接类型调整
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "full join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String leftCalcQuery = "select * from \n" // 左Calc查询SQL
        + "(select \"empid\", CASE WHEN \"empid\" = 1 THEN 0 ELSE 1 END,\n" // 子查询：从员工表选择员工ID、CASE表达式（员工ID等于1返回0否则返回1）
        + "\"deptno\", \"name\" from \"emps\") \"t1\"\n" // 部门号、姓名
        + "full join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String rightCalcQuery = "select * from \n" // 右Calc查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "full join (select \"deptno\", \"deptno\" IS NULL,\"name\" from \"depts\") \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、部门号是否为NULL、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String leftAndRightCalcQuery = "select * from \n" // 左右Calc查询SQL
        + "(select \"empid\", CASE WHEN \"empid\" = 1 THEN 0 ELSE \"empid\" END,\n" // 子查询：从员工表选择员工ID、CASE表达式（员工ID等于1返回0否则返回员工ID）
        + "\"deptno\", \"name\" from \"emps\") \"t1\"\n" // 部门号、姓名
        + "full join (select \"deptno\", \"deptno\" IS NOT NULL, \"name\" from \"depts\") \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、部门号是否非NULL、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, leftCalcQuery).noMat(); // 测试左Calc查询，断言不能使用物化视图
    sql(mv, rightCalcQuery).noMat(); // 测试右Calc查询，断言不能使用物化视图
    sql(mv, leftAndRightCalcQuery).noMat(); // 测试左右Calc查询，断言不能使用物化视图
  }

  @Test void testLeftFilterOnRightJoinToJoinFail() { // 测试方法：测试右连接左过滤器的替换失败
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "right join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 右连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\" where \"empid\" > 30) \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名，条件是员工ID大于30
        + "right join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 右连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).noMat(); // 断言不能使用物化视图
  }

  @Test void testLeftFilterOnFullJoinToJoinFail() { // 测试方法：测试全连接左过滤器的替换失败
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "full join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\" where \"empid\" > 30) \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名，条件是员工ID大于30
        + "full join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).noMat(); // 断言不能使用物化视图
  }

  @Test void testRightFilterOnFullJoinToJoinFail() { // 测试方法：测试全连接右过滤器的替换失败
    String mv = "select * from \n" // 物化视图SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "full join (select \"deptno\", \"name\" from \"depts\") \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、姓名
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    String query = "select * from \n" // 查询SQL
        + "(select \"empid\", \"deptno\", \"name\" from \"emps\") \"t1\"\n" // 子查询t1：从员工表选择员工ID、部门号、姓名
        + "full join (select \"deptno\", \"name\" from \"depts\" where \"name\" is not null) \"t2\"\n" // 全连接子查询t2：从部门表选择部门号、姓名，条件是姓名非空
        + "on \"t1\".\"deptno\" = \"t2\".\"deptno\""; // 连接条件：t1的部门号等于t2的部门号
    sql(mv, query).noMat(); // 断言不能使用物化视图
  }

  @Test void testMoreSameExprInMv() { // 测试方法：测试物化视图中多个相同表达式
    final String mv = "" // 物化视图SQL
        + "select \"empid\", \"deptno\", sum(\"empid\") as s1, sum(\"empid\") as s2, count(*) as c\n" // 按员工ID和部门号分组，求和员工ID两次（s1和s2），计数
        + "from \"emps\" group by \"empid\", \"deptno\""; // 从员工表按员工ID和部门号分组
    final String query = "" // 查询SQL
        +  "select sum(\"empid\"), count(*) from \"emps\" group by \"empid\", \"deptno\""; // 按员工ID和部门号分组，求和员工ID和计数
    sql(mv, query).ok(); // 断言替换成功
  }

  /**
   * It's match, distinct agg-call could be expressed by mv's grouping.
   */ // 注释：匹配成功，distinct聚合调用可以通过物化视图的分组来表达
  @Test void testAggDistinctInMvGrouping() { // 测试方法：测试distinct聚合在物化视图分组中的替换
    final String mv = "" // 物化视图SQL
        + "select \"deptno\", \"name\"" // 选择部门号和姓名
        + "from \"emps\" group by \"deptno\", \"name\""; // 按部门号和姓名分组
    final String query = "" // 查询SQL
        + "select \"deptno\", \"name\", count(distinct \"name\")" // 按部门号和姓名分组，计数不同的姓名
        + "from \"emps\" group by \"deptno\", \"name\""; // 从员工表按部门号和姓名分组
    sql(mv, query).ok(); // 断言替换成功
  }

  /**
   * It's match, `Optionality.IGNORED` agg-call could be expressed by mv's grouping.
   */ // 注释：匹配成功，Optionality.IGNORED聚合调用可以通过物化视图的分组来表达
  @Test void testAggOptionalityInMvGrouping() { // 测试方法：测试Optionality.IGNORED聚合在物化视图分组中的替换
    final String mv = "" // 物化视图SQL
        + "select \"deptno\", \"salary\"" // 选择部门号和薪资
        + "from \"emps\" group by \"deptno\", \"salary\""; // 按部门号和薪资分组
    final String query = "" // 查询SQL
        + "select \"deptno\", \"salary\", max(\"salary\")" // 按部门号和薪资分组，计算最大薪资
        + "from \"emps\" group by \"deptno\", \"salary\""; // 从员工表按部门号和薪资分组
    sql(mv, query).ok(); // 断言替换成功
  }

  /**
   * It's not match, normal agg-call could be expressed by mv's grouping.
   * Such as: sum, count
   */ // 注释：不匹配，普通聚合调用不能通过物化视图的分组来表达，如sum、count
  @Test void testAggNormalInMvGrouping() { // 测试方法：测试普通聚合在物化视图分组中的替换失败
    final String mv = "" // 物化视图SQL
        + "select \"deptno\", \"salary\"" // 选择部门号和薪资
        + "from \"emps\" group by \"deptno\", \"salary\""; // 按部门号和薪资分组
    final String query = "" // 查询SQL
        + "select \"deptno\", sum(\"salary\")" // 按部门号分组，求和薪资
        + "from \"emps\" group by \"deptno\""; // 从员工表按部门号分组
    sql(mv, query).noMat(); // 断言不能使用物化视图
  }

  /**
   * It's not match, which is count(*) with same grouping.
   */ // 注释：不匹配，count(*)使用相同的分组
  @Test void testGenerateQueryAggCallByMvGroupingForEmptyArg1() { // 测试方法：测试通过物化视图分组生成查询聚合调用失败（count(*)）
    final String mv = "" // 物化视图SQL
        + "select \"deptno\"" // 选择部门号
        + "from \"emps\" group by \"deptno\""; // 按部门号分组
    final String query = "" // 查询SQL
        + "select \"deptno\", count(*)" // 按部门号分组，计数
        + "from \"emps\" group by \"deptno\""; // 从员工表按部门号分组
    sql(mv, query).noMat(); // 断言不能使用物化视图
  }

  /**
   * It's not match, which is count(*) with rollup grouping.
   */ // 注释：不匹配，count(*)使用ROLLUP分组
  @Test void testGenerateQueryAggCallByMvGroupingForEmptyArg2() { // 测试方法：测试通过物化视图分组生成查询聚合调用失败（ROLLUP分组）
    final String mv = "" // 物化视图SQL
        + "select \"deptno\", \"commission\", \"salary\"" // 选择部门号、佣金和薪资
        + "from \"emps\" group by \"deptno\", \"commission\", \"salary\""; // 按部门号、佣金和薪资分组
    final String query = "" // 查询SQL
        + "select \"deptno\", \"commission\", count(*)" // 按部门号和佣金分组，计数
        + "from \"emps\" group by \"deptno\", \"commission\""; // 从员工表按部门号和佣金分组
    sql(mv, query).noMat(); // 断言不能使用物化视图
  }

  /**
   * It's match, when query's agg-calls could be both rollup and expressed by mv's grouping.
   */ // 注释：匹配成功，当查询的聚合调用既可以上卷也可以通过物化视图的分组来表达
  @Test void testAggCallBothGenByMvGroupingAndRollupOk() { // 测试方法：测试聚合调用既可以通过物化视图分组也可以通过上卷的替换
    final String mv = "" // 物化视图SQL
        + "select \"name\", \"deptno\", \"empid\", min(\"commission\")" // 按姓名、部门号和员工ID分组，计算最小佣金
        + "from \"emps\" group by \"name\", \"deptno\", \"empid\""; // 从员工表按姓名、部门号和员工ID分组
    final String query = "" // 查询SQL
        + "select \"name\", max(\"deptno\"), count(distinct \"empid\"), min(\"commission\")" // 按姓名分组，计算最大部门号、计数不同的员工ID、最小佣金
        + "from \"emps\" group by \"name\""; // 从员工表按姓名分组
    sql(mv, query).ok(); // 断言替换成功
  }

  /** Unit test for logic functions
   * {@link org.apache.calcite.plan.SubstitutionVisitor#mayBeSatisfiable} and
   * {@link RexUtil#simplify}. */ // 注释：单元测试：测试逻辑函数mayBeSatisfiable和simplify
  @Test void testSatisfiable() { // 测试方法：测试可满足性
    final SatisfiabilityFixture f = new SatisfiabilityFixture(); // 创建可满足性测试夹具
    final RexBuilder rexBuilder = f.rexBuilder; // 获取Rex表达式构建器

    // TRUE may be satisfiable // 注释：TRUE可能可满足
    f.checkSatisfiable(rexBuilder.makeLiteral(true), "true"); // 检查TRUE是否可满足，期望简化为"true"

    // FALSE is not satisfiable // 注释：FALSE不可满足
    f.checkNotSatisfiable(rexBuilder.makeLiteral(false)); // 检查FALSE是否不可满足

    // The expression "$0 = 1". // 注释：表达式"$0 = 1"
    final RexNode i0_eq_0 = // 创建表达式"$0 = 0"（注意：注释说是1，但实际创建的是0）
        rexBuilder.makeCall(
            SqlStdOperatorTable.EQUALS,
            rexBuilder.makeInputRef(
                f.typeFactory.createType(int.class), 0), // 创建对第0个输入字段的引用，类型为int
            rexBuilder.makeExactLiteral(BigDecimal.ZERO)); // 创建字面量0

    // "$0 = 1" may be satisfiable // 注释："$0 = 0"可能可满足
    f.checkSatisfiable(i0_eq_0, "=($0, 0)"); // 检查是否可满足，期望简化为"=($0, 0)"

    // "$0 = 1 AND TRUE" may be satisfiable // 注释："$0 = 0 AND TRUE"可能可满足
    final RexNode e0 = // 创建表达式"$0 = 0 AND TRUE"
        rexBuilder.makeCall(
            SqlStdOperatorTable.AND,
            i0_eq_0,
            rexBuilder.makeLiteral(true)); // 创建TRUE字面量
    f.checkSatisfiable(e0, "=($0, 0)"); // 检查是否可满足，期望简化为"=($0, 0)"

    // "$0 = 1 AND FALSE" is not satisfiable // 注释："$0 = 0 AND FALSE"不可满足
    final RexNode e1 = // 创建表达式"$0 = 0 AND FALSE"
        rexBuilder.makeCall(
            SqlStdOperatorTable.AND,
            i0_eq_0,
            rexBuilder.makeLiteral(false)); // 创建FALSE字面量
    f.checkNotSatisfiable(e1); // 检查是否不可满足

    // "$0 = 0 AND NOT $0 = 0" is not satisfiable // 注释："$0 = 0 AND NOT $0 = 0"不可满足
    final RexNode e2 = // 创建表达式"$0 = 0 AND NOT $0 = 0"
        rexBuilder.makeCall(
            SqlStdOperatorTable.AND,
            i0_eq_0,
            rexBuilder.makeCall(
                SqlStdOperatorTable.NOT,
                i0_eq_0)); // 创建NOT操作
    f.checkNotSatisfiable(e2); // 检查是否不可满足

    // "TRUE AND NOT $0 = 0" may be satisfiable. Can simplify. // 注释："TRUE AND NOT $0 = 0"可能可满足，可以简化
    final RexNode e3 = // 创建表达式"TRUE AND NOT $0 = 0"
        rexBuilder.makeCall(
            SqlStdOperatorTable.AND,
            rexBuilder.makeLiteral(true),
            rexBuilder.makeCall(
                SqlStdOperatorTable.NOT,
                i0_eq_0)); // 创建NOT操作
    f.checkSatisfiable(e3, "<>($0, 0)"); // 检查是否可满足，期望简化为"<>($0, 0)"

    // The expression "$1 = 1". // 注释：表达式"$1 = 1"
    final RexNode i1_eq_1 = // 创建表达式"$1 = 1"
        rexBuilder.makeCall(
            SqlStdOperatorTable.EQUALS,
            rexBuilder.makeInputRef(
                f.typeFactory.createType(int.class), 1), // 创建对第1个输入字段的引用，类型为int
            rexBuilder.makeExactLiteral(BigDecimal.ONE)); // 创建字面量1

    // "$0 = 0 AND $1 = 1 AND NOT $0 = 0" is not satisfiable // 注释："$0 = 0 AND $1 = 1 AND NOT $0 = 0"不可满足
    final RexNode e4 = // 创建表达式"$0 = 0 AND $1 = 1 AND NOT $0 = 0"
        rexBuilder.makeCall(
            SqlStdOperatorTable.AND,
            i0_eq_0,
            rexBuilder.makeCall(
                SqlStdOperatorTable.AND,
                i1_eq_1,
                rexBuilder.makeCall(
                    SqlStdOperatorTable.NOT, i0_eq_0))); // 嵌套AND和NOT操作
    f.checkNotSatisfiable(e4); // 检查是否不可满足

    // "$0 = 0 AND NOT $1 = 1" may be satisfiable. Can't simplify. // 注释："$0 = 0 AND NOT $1 = 1"可能可满足，但不能简化
    final RexNode e5 = // 创建表达式"$0 = 0 AND NOT $1 = 1"
        rexBuilder.makeCall(
            SqlStdOperatorTable.AND,
            i0_eq_0,
            rexBuilder.makeCall(
                SqlStdOperatorTable.NOT,
                i1_eq_1)); // 创建NOT操作
    f.checkSatisfiable(e5, "AND(=($0, 0), <>($1, 1))"); // 检查是否可满足，期望简化为"AND(=($0, 0), <>($1, 1))"

    // "$0 = 0 AND NOT ($0 = 0 AND $1 = 1)" may be satisfiable. Can simplify. // 注释："$0 = 0 AND NOT ($0 = 0 AND $1 = 1)"可能可满足，可以简化
    final RexNode e6 = // 创建表达式"$0 = 0 AND NOT ($0 = 0 AND $1 = 1)"
        rexBuilder.makeCall(
            SqlStdOperatorTable.AND,
            i0_eq_0,
            rexBuilder.makeCall(
                SqlStdOperatorTable.NOT,
                rexBuilder.makeCall(
                    SqlStdOperatorTable.AND,
                    i0_eq_0,
                    i1_eq_1))); // 嵌套AND和NOT操作
    f.checkSatisfiable(e6, "AND(=($0, 0), <>($1, 1))"); // 检查是否可满足，期望简化为"AND(=($0, 0), <>($1, 1))"

    // "$0 = 0 AND ($1 = 1 AND NOT ($0 = 0))" is not satisfiable. // 注释："$0 = 0 AND ($1 = 1 AND NOT ($0 = 0))"不可满足
    final RexNode e7 = // 创建表达式"$0 = 0 AND ($1 = 1 AND NOT ($0 = 0))"
        rexBuilder.makeCall(
            SqlStdOperatorTable.AND,
            i0_eq_0,
            rexBuilder.makeCall(
                SqlStdOperatorTable.AND,
                i1_eq_1,
                rexBuilder.makeCall(
                    SqlStdOperatorTable.NOT,
                    i0_eq_0))); // 嵌套AND和NOT操作
    f.checkNotSatisfiable(e7); // 检查是否不可满足

    // The expression "$2". // 注释：表达式"$2"
    final RexInputRef i2 = // 创建对第2个输入字段的引用
        rexBuilder.makeInputRef(
            f.typeFactory.createType(boolean.class), 2); // 类型为boolean

    // The expression "$3". // 注释：表达式"$3"
    final RexInputRef i3 = // 创建对第3个输入字段的引用
        rexBuilder.makeInputRef(
            f.typeFactory.createType(boolean.class), 3); // 类型为boolean

    // The expression "$4". // 注释：表达式"$4"
    final RexInputRef i4 = // 创建对第4个输入字段的引用
        rexBuilder.makeInputRef(
            f.typeFactory.createType(boolean.class), 4); // 类型为boolean

    // "$0 = 0 AND $2 AND $3 AND NOT ($2 AND $3 AND $4) AND NOT ($2 AND $4)" may
    // be satisfiable. Can't simplify. // 注释：复杂表达式可能可满足，但不能简化
    final RexNode e8 = // 创建复杂表达式
        rexBuilder.makeCall(
            SqlStdOperatorTable.AND,
            i0_eq_0,
            rexBuilder.makeCall(
                SqlStdOperatorTable.AND,
                i2,
                rexBuilder.makeCall(
                    SqlStdOperatorTable.AND,
                    i3,
                    rexBuilder.makeCall(
                        SqlStdOperatorTable.NOT,
                        rexBuilder.makeCall(
                            SqlStdOperatorTable.AND,
                            i2,
                            i3,
                            i4))), // 嵌套多层AND和NOT操作
                    rexBuilder.makeCall(
                        SqlStdOperatorTable.NOT,
                        i4)))); // NOT操作
    f.checkSatisfiable(e8,
        "AND(=($0, 0), $2, $3, OR(NOT($2), NOT($3), NOT($4)), NOT($4))"); // 检查是否可满足，期望简化为指定字符串
  }

  @Test void testSplitFilter() { // 测试方法：测试过滤器分割
    final SatisfiabilityFixture f = new SatisfiabilityFixture(); // 创建可满足性测试夹具
    final RexBuilder rexBuilder = f.rexBuilder; // 获取Rex表达式构建器
    final RexSimplify simplify = f.simplify; // 获取Rex简化器

    final RexLiteral i1 = rexBuilder.makeExactLiteral(BigDecimal.ONE); // 创建字面量1
    final RexLiteral i2 = rexBuilder.makeExactLiteral(BigDecimal.valueOf(2)); // 创建字面量2
    final RexLiteral i3 = rexBuilder.makeExactLiteral(BigDecimal.valueOf(3)); // 创建字面量3

    final RelDataType intType = f.typeFactory.createType(int.class); // 创建int类型
    final RexInputRef x = rexBuilder.makeInputRef(intType, 0); // 创建对第0个输入字段的引用（$0）
    final RexInputRef y = rexBuilder.makeInputRef(intType, 1); // 创建对第1个输入字段的引用（$1）
    final RexInputRef z = rexBuilder.makeInputRef(intType, 2); // 创建对第2个输入字段的引用（$2）

    final RexNode x_eq_1 = // 创建表达式"$0 = 1"
        rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, x, i1);
    final RexNode x_eq_1_b = // 创建表达式"1 = $0"（反向）
        rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, i1, x);
    final RexNode x_eq_2 = // 创建表达式"$0 = 2"
        rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, x, i2);
    final RexNode y_eq_2 = // 创建表达式"$1 = 2"
        rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, y, i2);
    final RexNode z_eq_3 = // 创建表达式"$2 = 3"
        rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, z, i3);

    final RexNode x_plus_y_gt =  // x + y > 2
        rexBuilder.makeCall(
            SqlStdOperatorTable.GREATER_THAN,
            rexBuilder.makeCall(SqlStdOperatorTable.PLUS, x, y),
            i2); // 创建表达式"x + y > 2"
    final RexNode y_plus_x_gt =  // y + x > 2
        rexBuilder.makeCall(
            SqlStdOperatorTable.GREATER_THAN,
            rexBuilder.makeCall(SqlStdOperatorTable.PLUS, y, x),
            i2); // 创建表达式"y + x > 2"

    final RexNode x_times_y_gt = // x*y > 2
        rexBuilder.makeCall(
            SqlStdOperatorTable.GREATER_THAN,
            rexBuilder.makeCall(SqlStdOperatorTable.MULTIPLY, x, y),
            i2); // 创建表达式"x * y > 2"

    final RexNode y_times_x_gt = // 2 < y*x
        rexBuilder.makeCall(
            SqlStdOperatorTable.LESS_THAN,
            i2,
            rexBuilder.makeCall(SqlStdOperatorTable.MULTIPLY, y, x)); // 创建表达式"2 < y * x"

    final RexNode x_plus_x_gt =  // x + x > 2
        rexBuilder.makeCall(
            SqlStdOperatorTable.GREATER_THAN,
            rexBuilder.makeCall(SqlStdOperatorTable.PLUS, x, y),
            i2); // 创建表达式"x + y > 2"（注意：注释说是x+x，但实际是x+y）

    RexNode newFilter; // 声明新的过滤器变量

    // Example 1. // 示例1
    //   condition: x = 1 or y = 2 // 条件：x = 1 or y = 2
    //   target:    y = 2 or 1 = x // 目标：y = 2 or 1 = x
    // yields // 结果
    //   residue:   true // 残余：true
    newFilter = // 调用splitFilter方法分割过滤器
        SubstitutionVisitor.splitFilter(simplify,
            rexBuilder.makeCall(SqlStdOperatorTable.OR, x_eq_1, y_eq_2), // 条件：x = 1 or y = 2
            rexBuilder.makeCall(SqlStdOperatorTable.OR, y_eq_2, x_eq_1_b)); // 目标：y = 2 or 1 = x
    assertThat(newFilter, notNullValue()); // 断言newFilter非空
    assertThat(newFilter.isAlwaysTrue(), equalTo(true)); // 断言newFilter总是true

    // Example 2. // 示例2
    //   condition: x = 1, // 条件：x = 1
    //   target:    x = 1 or z = 3 // 目标：x = 1 or z = 3
    // yields // 结果
    //   residue:   x = 1 // 残余：x = 1
    newFilter = // 调用splitFilter方法分割过滤器
        SubstitutionVisitor.splitFilter(simplify, x_eq_1, // 条件：x = 1
            rexBuilder.makeCall(SqlStdOperatorTable.OR, x_eq_1, z_eq_3)); // 目标：x = 1 or z = 3
    assertThat(newFilter, notNullValue()); // 断言newFilter非空
    assertThat(newFilter, hasToString("=($0, 1)")); // 断言newFilter的字符串表示为"=($0, 1)"

    // 2b. // 示例2b
    //   condition: x = 1 or y = 2 // 条件：x = 1 or y = 2
    //   target:    x = 1 or y = 2 or z = 3 // 目标：x = 1 or y = 2 or z = 3
    // yields // 结果
    //   residue:   x = 1 or y = 2 // 残余：x = 1 or y = 2
    newFilter = // 调用splitFilter方法分割过滤器
        SubstitutionVisitor.splitFilter(simplify,
            rexBuilder.makeCall(SqlStdOperatorTable.OR, x_eq_1, y_eq_2), // 条件：x = 1 or y = 2
            rexBuilder.makeCall(SqlStdOperatorTable.OR, x_eq_1, y_eq_2, z_eq_3)); // 目标：x = 1 or y = 2 or z = 3
    assertThat(newFilter, notNullValue()); // 断言newFilter非空
    assertThat(newFilter, hasToString("OR(=($0, 1), =($1, 2))")); // 断言newFilter的字符串表示为"OR(=($0, 1), =($1, 2))"

    // 2c. // 示例2c
    //   condition: x = 1 // 条件：x = 1
    //   target:    x = 1 or y = 2 or z = 3 // 目标：x = 1 or y = 2 or z = 3
    // yields // 结果
    //   residue:   x = 1 // 残余：x = 1
    newFilter = // 调用splitFilter方法分割过滤器
        SubstitutionVisitor.splitFilter(simplify, x_eq_1, // 条件：x = 1
            rexBuilder.makeCall(SqlStdOperatorTable.OR, x_eq_1, y_eq_2, z_eq_3)); // 目标：x = 1 or y = 2 or z = 3
    assertThat(newFilter, notNullValue()); // 断言newFilter非空
    assertThat(newFilter, hasToString("=($0, 1)")); // 断言newFilter的字符串表示为"=($0, 1)"

    // 2d. // 示例2d
    //   condition: x = 1 or y = 2 // 条件：x = 1 or y = 2
    //   target:    y = 2 or x = 1 // 目标：y = 2 or x = 1
    // yields // 结果
    //   residue:   true // 残余：true
    newFilter = // 调用splitFilter方法分割过滤器
        SubstitutionVisitor.splitFilter(simplify,
            rexBuilder.makeCall(SqlStdOperatorTable.OR, x_eq_1, y_eq_2), // 条件：x = 1 or y = 2
            rexBuilder.makeCall(SqlStdOperatorTable.OR, y_eq_2, x_eq_1)); // 目标：y = 2 or x = 1
    assertThat(newFilter, notNullValue()); // 断言newFilter非空
    assertThat(newFilter.isAlwaysTrue(), equalTo(true)); // 断言newFilter总是true

    // 2e. // 示例2e
    //   condition: x = 1 // 条件：x = 1
    //   target:    x = 1 (different object) // 目标：x = 1（不同的对象）
    // yields // 结果
    //   residue:   true // 残余：true
    newFilter = SubstitutionVisitor.splitFilter(simplify, x_eq_1, x_eq_1_b); // 调用splitFilter方法，条件和目标相同但对象不同
    assertThat(newFilter, notNullValue()); // 断言newFilter非空
    assertThat(newFilter.isAlwaysTrue(), equalTo(true)); // 断言newFilter总是true

    // 2f. // 示例2f
    //   condition: x = 1 or y = 2 // 条件：x = 1 or y = 2
    //   target:    x = 1 // 目标：x = 1
    // yields // 结果
    //   residue:   null // 残余：null
    newFilter = // 调用splitFilter方法分割过滤器
        SubstitutionVisitor.splitFilter(simplify,
            rexBuilder.makeCall(SqlStdOperatorTable.OR, x_eq_1, y_eq_2), // 条件：x = 1 or y = 2
            x_eq_1); // 目标：x = 1
    assertNull(newFilter); // 断言newFilter为null

    // Example 3. // 示例3
    // Condition [x = 1 and y = 2], // 条件：x = 1 and y = 2
    // target [y = 2 and x = 1] yields // 目标：y = 2 and x = 1，结果
    // residue [true]. // 残余：true
    newFilter = // 调用splitFilter方法分割过滤器
        SubstitutionVisitor.splitFilter(simplify,
            rexBuilder.makeCall(SqlStdOperatorTable.AND, x_eq_1, y_eq_2), // 条件：x = 1 and y = 2
            rexBuilder.makeCall(SqlStdOperatorTable.AND, y_eq_2, x_eq_1)); // 目标：y = 2 and x = 1
    assertThat(newFilter, notNullValue()); // 断言newFilter非空
    assertThat(newFilter.isAlwaysTrue(), equalTo(true)); // 断言newFilter总是true

    // Example 4. // 示例4
    //   condition: x = 1 and y = 2 // 条件：x = 1 and y = 2
    //   target:    y = 2 // 目标：y = 2
    // yields // 结果
    //   residue:   x = 1 // 残余：x = 1
    newFilter = // 调用splitFilter方法分割过滤器
        SubstitutionVisitor.splitFilter(simplify,
            rexBuilder.makeCall(SqlStdOperatorTable.AND, x_eq_1, y_eq_2), // 条件：x = 1 and y = 2
            y_eq_2); // 目标：y = 2
    assertThat(newFilter, notNullValue()); // 断言newFilter非空
    assertThat(newFilter, hasToString("=($0, 1)")); // 断言newFilter的字符串表示为"=($0, 1)"

    // Example 5. // 示例5
    //   condition: x = 1 // 条件：x = 1
    //   target:    x = 1 and y = 2 // 目标：x = 1 and y = 2
    // yields // 结果
    //   residue:   null // 残余：null
    newFilter = // 调用splitFilter方法分割过滤器
        SubstitutionVisitor.splitFilter(simplify, x_eq_1, // 条件：x = 1
            rexBuilder.makeCall(SqlStdOperatorTable.AND, x_eq_1, y_eq_2)); // 目标：x = 1 and y = 2
    assertNull(newFilter); // 断言newFilter为null

    // Example 6. // 示例6
    //   condition: x = 1 // 条件：x = 1
    //   target:    y = 2 // 目标：y = 2
    // yields // 结果
    //   residue:   null // 残余：null
    newFilter = SubstitutionVisitor.splitFilter(simplify, x_eq_1, y_eq_2); // 调用splitFilter方法，条件和目标不相关
    assertNull(newFilter); // 断言newFilter为null

    // Example 7. // 示例7
    //   condition: x = 1 // 条件：x = 1
    //   target:    x = 2 // 目标：x = 2
    // yields // 结果
    //   residue:   null // 残余：null
    newFilter = SubstitutionVisitor.splitFilter(simplify, x_eq_1, x_eq_2); // 调用splitFilter方法，条件和目标冲突
    assertNull(newFilter); // 断言newFilter为null

    // Example 8. // 示例8
    //   condition: x + y > 2 // 条件：x + y > 2
    //   target:    y + x > 2 // 目标：y + x > 2
    // yields // 结果
    //   residue:  true // 残余：true
    newFilter = // 调用splitFilter方法分割过滤器
        SubstitutionVisitor.splitFilter(simplify, x_plus_y_gt, y_plus_x_gt); // 条件和目标等价（交换顺序）
    assertThat(newFilter, notNullValue()); // 断言newFilter非空
    assertThat(newFilter.isAlwaysTrue(), equalTo(true)); // 断言newFilter总是true

    // Example 9. // 示例9
    //   condition: x + x > 2 // 条件：x + x > 2
    //   target:    x + x > 2 // 目标：x + x > 2
    // yields // 结果
    //   residue:  true // 残余：true
    newFilter = // 调用splitFilter方法分割过滤器
        SubstitutionVisitor.splitFilter(simplify, x_plus_x_gt, x_plus_x_gt); // 条件和目标相同
    assertThat(newFilter, notNullValue()); // 断言newFilter非空
    assertThat(newFilter.isAlwaysTrue(), equalTo(true)); // 断言newFilter总是true

    // Example 10. // 示例10
    //   condition: x * y > 2 // 条件：x * y > 2
    //   target:    2 < y * x // 目标：2 < y * x
    // yields // 结果
    //   residue:  true // 残余：true
    newFilter = // 调用splitFilter方法分割过滤器
        SubstitutionVisitor.splitFilter(simplify, x_times_y_gt, y_times_x_gt); // 条件和目标等价（交换操作数和操作符）
    assertThat(newFilter, notNullValue()); // 断言newFilter非空
    assertThat(newFilter.isAlwaysTrue(), equalTo(true)); // 断言newFilter总是true
  }

  @Test void testSubQuery() { // 测试方法：测试子查询
    final String q = "select \"empid\", \"deptno\", \"salary\" from \"emps\" e1\n" // 查询SQL：从员工表选择员工ID、部门号、薪资
        + "where \"empid\" = (\n" // 条件：员工ID等于子查询结果
        + "  select max(\"empid\") from \"emps\"\n" // 子查询：从员工表选择最大员工ID
        + "  where \"deptno\" = e1.\"deptno\")"; // 子查询条件：部门号等于外层查询的部门号
    final String m = "select \"empid\", \"deptno\" from \"emps\"\n"; // 物化视图SQL：从员工表选择员工ID、部门号
    sql(m, q).ok(); // 断言替换成功
  }

  /** Tests a complicated star-join query on a complicated materialized
   * star-join query. Some of the features: // 注释：测试复杂的星型连接查询在复杂的物化星型连接查询上。一些特性：
   *
   * <ol> // 有序列表
   * <li>query joins in different order; // 1. 查询连接顺序不同
   * <li>query's join conditions are in where clause; // 2. 查询的连接条件在where子句中
   * <li>query does not use all join tables (safe to omit them because they are
   *    many-to-mandatory-one joins); // 3. 查询不使用所有连接表（可以安全省略，因为它们是多对一强制性连接）
   * <li>query is at higher granularity, therefore needs to roll up; // 4. 查询处于更高的粒度，因此需要上卷
   * <li>query has a condition on one of the materialization's grouping columns.
   *    </ol> // 5. 查询对物化视图的一个分组列有条件
   */
  @Disabled // 禁用该测试
  @Test void testFilterGroupQueryOnStar() { // 测试方法：测试星型连接上的过滤分组查询
    sql("select p.\"product_name\", t.\"the_year\",\n" // 物化视图SQL：选择产品名称、年份、求和销售数量和计数
            + "  sum(f.\"unit_sales\") as \"sum_unit_sales\", count(*) as \"c\"\n"
            + "from \"foodmart\".\"sales_fact_1997\" as f\n" // 从销售事实表
            + "join (\n" // 连接时间维度子查询
            + "    select \"time_id\", \"the_year\", \"the_month\"\n" // 选择时间ID、年份、月份
            + "    from \"foodmart\".\"time_by_day\") as t\n" // 从时间表
            + "  on f.\"time_id\" = t.\"time_id\"\n" // 连接条件：时间ID
            + "join \"foodmart\".\"product\" as p\n" // 连接产品表
            + "  on f.\"product_id\" = p.\"product_id\"\n" // 连接条件：产品ID
            + "join \"foodmart\".\"product_class\" as pc" // 连接产品类别表
            + "  on p.\"product_class_id\" = pc.\"product_class_id\"\n" // 连接条件：产品类别ID
            + "group by t.\"the_year\",\n" // 按年份、月份、产品部门、产品类别、产品名称分组
            + " t.\"the_month\",\n"
            + " pc.\"product_department\",\n"
            + " pc.\"product_category\",\n"
            + " p.\"product_name\"",
        "select t.\"the_month\", count(*) as x\n" // 查询SQL：选择月份和计数
            + "from (\n"
            + "  select \"time_id\", \"the_year\", \"the_month\"\n" // 子查询：从时间表选择时间ID、年份、月份
            + "  from \"foodmart\".\"time_by_day\") as t,\n" // 别名为t
            + " \"foodmart\".\"sales_fact_1997\" as f\n" // 销售事实表，别名为f
            + "where t.\"the_year\" = 1997\n" // 条件：年份等于1997
            + "and t.\"time_id\" = f.\"time_id\"\n" // 条件：时间ID相等
            + "group by t.\"the_year\",\n" // 按年份和月份分组
            + " t.\"the_month\"\n")
        .withDefaultSchemaSpec(CalciteAssert.SchemaSpec.JDBC_FOODMART) // 使用JDBC_FOODMART模式规范
        .ok(); // 断言替换成功
  }

  /** Simpler than {@link #testFilterGroupQueryOnStar()}, tests a query on a
   * materialization that is just a join. */ // 注释：比testFilterGroupQueryOnStar简单，测试连接物化视图上的查询
  @Disabled // 禁用该测试
  @Test void testQueryOnStar() { // 测试方法：测试星型连接上的查询
    String q = "select *\n" // 查询SQL：选择所有列
        + "from \"foodmart\".\"sales_fact_1997\" as f\n" // 从销售事实表
        + "join \"foodmart\".\"time_by_day\" as t on f.\"time_id\" = t.\"time_id\"\n" // 连接时间表
        + "join \"foodmart\".\"product\" as p on f.\"product_id\" = p.\"product_id\"\n" // 连接产品表
        + "join \"foodmart\".\"product_class\" as pc on p.\"product_class_id\" = pc.\"product_class_id\"\n"; // 连接产品类别表
    sql(q, q + "where t.\"month_of_year\" = 10") // 物化视图是q，查询是q加上月份等于10的条件
        .withDefaultSchemaSpec(CalciteAssert.SchemaSpec.JDBC_FOODMART) // 使用JDBC_FOODMART模式规范
        .ok(); // 断言替换成功
  }

  /** A materialization that is a join of a union cannot at present be converted
   * to a star table and therefore cannot be recognized. This test checks that
   * nothing unpleasant happens. */ // 注释：UNION的连接物化视图目前不能转换为星型表，因此不能被识别。该测试检查不会发生不愉快的事情
  @Disabled // 禁用该测试
  @Test void testJoinOnUnionMaterialization() { // 测试方法：测试UNION连接物化视图
    String q = "select *\n" // 查询SQL：选择所有列
        + "from (select * from \"emps\" union all select * from \"emps\")\n" // 从UNION ALL的员工表
        + "join \"depts\" using (\"deptno\")"; // 使用部门号连接部门表
    sql(q, q).noMat(); // 物化视图和查询相同，断言不能使用物化视图
  }

  @Disabled // 禁用该测试
  @Test void testDifferentColumnNames() {} // 测试方法：测试不同列名（空实现）

  @Disabled // 禁用该测试
  @Test void testDifferentType() {} // 测试方法：测试不同类型（空实现）

  @Disabled // 禁用该测试
  @Test void testPartialUnion() {} // 测试方法：测试部分UNION（空实现）

  @Disabled // 禁用该测试
  @Test void testNonDisjointUnion() {} // 测试方法：测试非不相交UNION（空实现）

  @Disabled // 禁用该测试
  @Test void testMaterializationReferencesTableInOtherSchema() {} // 测试方法：测试物化视图引用其他模式的表（空实现）

  @Disabled // 禁用该测试
  @Test void testOrderByQueryOnProjectView() { // 测试方法：测试投影视图上的ORDER BY查询
    sql("select \"deptno\", \"empid\" from \"emps\"", // 物化视图：选择部门号和员工ID
        "select \"empid\" from \"emps\" order by \"deptno\"") // 查询：选择员工ID，按部门号排序
        .ok(); // 断言替换成功
  }

  @Disabled // 禁用该测试
  @Test void testOrderByQueryOnOrderByView() { // 测试方法：测试ORDER BY视图上的ORDER BY查询
    sql("select \"deptno\", \"empid\" from \"emps\" order by \"deptno\"", // 物化视图：选择部门号和员工ID，按部门号排序
        "select \"empid\" from \"emps\" order by \"deptno\"") // 查询：选择员工ID，按部门号排序
        .ok(); // 断言替换成功
  }

  @Test void testQueryDistinctColumnInTargetGroupByList0() { // 测试方法：测试查询目标分组列表中的不同列
    final String mv = "" // 物化视图SQL
        + "select \"name\", \"commission\", \"deptno\"\n" // 选择姓名、佣金、部门号
        + "from \"emps\" group by \"name\", \"commission\", \"deptno\""; // 按姓名、佣金、部门号分组
    final String query = "" // 查询SQL
        + "select \"name\", \"commission\", count(distinct \"deptno\") as cnt\n" // 按姓名、佣金分组，计数不同的部门号
        + "from \"emps\" group by \"name\", \"commission\""; // 从员工表按姓名、佣金分组
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testQueryDistinctColumnInTargetGroupByList1() { // 测试方法：测试查询目标分组列表中的不同列（另一种情况）
    final String mv = "" // 物化视图SQL
        + "select \"name\", \"deptno\" " // 选择姓名、部门号
        + "from \"emps\" group by \"name\", \"deptno\""; // 按姓名、部门号分组
    final String query = "" // 查询SQL
        + "select \"name\", count(distinct \"deptno\")\n" // 按姓名分组，计数不同的部门号
        + "from \"emps\" group by \"name\""; // 从员工表按姓名分组
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testQueryDistinctColumnInTargetGroupByList2() { // 测试方法：测试查询目标分组列表中的不同列（多个不同列）
    final String mv = "" // 物化视图SQL
        + "select \"name\", \"deptno\", \"empid\"\n" // 选择姓名、部门号、员工ID
        + "from \"emps\" group by \"name\", \"deptno\", \"empid\""; // 按姓名、部门号、员工ID分组
    final String query = "" // 查询SQL
        + "select \"name\", count(distinct \"deptno\"), count(distinct \"empid\")\n" // 按姓名分组，计数不同的部门号和员工ID
        + "from \"emps\" group by \"name\""; // 从员工表按姓名分组
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testQueryDistinctColumnInTargetGroupByList3() { // 测试方法：测试查询目标分组列表中的不同列（包含普通聚合）
    final String mv = "" // 物化视图SQL
        + "select \"name\", \"deptno\", \"empid\", count(\"commission\")\n" // 选择姓名、部门号、员工ID、计数佣金
        + "from \"emps\" group by \"name\", \"deptno\", \"empid\""; // 按姓名、部门号、员工ID分组
    final String query = "" // 查询SQL
        + "select \"name\", count(distinct \"deptno\"), count(distinct \"empid\"), count" // 按姓名分组，计数不同的部门号、员工ID和佣金
        + "(\"commission\")\n"
        + "from \"emps\" group by \"name\""; // 从员工表按姓名分组
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testQueryDistinctColumnInTargetGroupByList4() { // 测试方法：测试查询目标分组列表中的不同列（单列）
    final String mv = "" // 物化视图SQL
        + "select \"name\", \"deptno\", \"empid\"\n" // 选择姓名、部门号、员工ID
        + "from \"emps\" group by \"name\", \"deptno\", \"empid\""; // 按姓名、部门号、员工ID分组
    final String query = "" // 查询SQL
        + "select \"name\", count(distinct \"deptno\")\n" // 按姓名分组，计数不同的部门号
        + "from \"emps\" group by \"name\""; // 从员工表按姓名分组
    sql(mv, query).ok(); // 断言替换成功
  }

  @Test void testRexPredicate() { // 测试方法：测试Rex谓词
    final String mv = "" // 物化视图SQL
        + "select \"name\"\n" // 选择姓名
        + "from \"emps\"\n" // 从员工表
        + "where \"deptno\" > 100 and \"deptno\" > 50\n" // 条件：部门号大于100且部门号大于50
        + "group by \"name\""; // 按姓名分组
    final String query = "" // 查询SQL
        + "select \"name\"\n" // 选择姓名
        + "from \"emps\"\n" // 从员工表
        + "where \"deptno\" > 100" // 条件：部门号大于100
        + "group by \"name\""; // 按姓名分组
    sql(mv, query) // 执行SQL
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  @Test void testRexPredicate1() { // 测试方法：测试Rex谓词（反向）
    final String query = "" // 查询SQL
        + "select \"name\"\n" // 选择姓名
        + "from \"emps\"\n" // 从员工表
        + "where \"deptno\" > 100 and \"deptno\" > 50\n" // 条件：部门号大于100且部门号大于50
        + "group by \"name\""; // 按姓名分组
    final String mv = "" // 物化视图SQL
        + "select \"name\"\n" // 选择姓名
        + "from \"emps\"\n" // 从员工表
        + "where \"deptno\" > 100" // 条件：部门号大于100
        + "group by \"name\""; // 按姓名分组
    sql(mv, query) // 执行SQL
        .checkingThatResultContains("" // 验证结果包含指定的字符串
            + "EnumerableTableScan(table=[[hr, MV0]])") // 表扫描节点，扫描物化视图MV0
        .ok(); // 断言替换成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4779">[CALCITE-4779]
   * GroupByList contains constant literal, materialized view recognition failed</a>. */ // 注释：CALCITE-4779问题的测试用例
  @Test void testGroupByListContainsConstantLiteral() { // 测试方法：测试分组列表包含常量字面量
    // Aggregate operator grouping set contains a literal and count(distinct col) function. // 注释：聚合操作符分组集包含字面量和count(distinct col)函数
    final String mv1 = "" // 物化视图SQL
        + "select \"deptno\", \"empid\"\n" // 选择部门号、员工ID
        + "from \"emps\"\n" // 从员工表
        + "group by \"deptno\", \"empid\""; // 按部门号、员工ID分组
    final String query1 = "" // 查询SQL
        + "select 'a', \"deptno\", count(distinct \"empid\")\n" // 选择常量'a'、部门号、计数不同的员工ID
        + "from \"emps\"\n" // 从员工表
        + "group by 'a', \"deptno\""; // 按常量'a'、部门号分组
    sql(mv1, query1).ok(); // 断言替换成功

    // Aggregate operator grouping set contains a literal and sum(col) function. // 注释：聚合操作符分组集包含字面量和sum(col)函数
    final String mv2 = "" // 物化视图SQL
        + "select \"deptno\", \"empid\", sum(\"empid\")\n" // 选择部门号、员工ID、求和员工ID
        + "from \"emps\"\n" // 从员工表
        + "group by \"deptno\", \"empid\""; // 按部门号、员工ID分组
    final String query2 = "" // 查询SQL
        + "select 'a', \"deptno\", sum(\"empid\")\n" // 选择常量'a'、部门号、求和员工ID
        + "from \"emps\"\n" // 从员工表
        + "group by 'a', \"deptno\""; // 按常量'a'、部门号分组
    sql(mv2, query2).ok(); // 断言替换成功
  }

  /** Fixture for tests for whether expressions are satisfiable,
   * specifically {@link SubstitutionVisitor#mayBeSatisfiable(RexNode)}. */ // 注释：测试表达式是否可满足的夹具，特别是mayBeSatisfiable方法
  private static class SatisfiabilityFixture { // 私有静态类：可满足性测试夹具
    final JavaTypeFactoryImpl typeFactory = // Java类型工厂实现
        new JavaTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认的关系数据类型系统
    final RexBuilder rexBuilder = new RexBuilder(typeFactory); // Rex表达式构建器
    final RexSimplify simplify = // Rex简化器
        new RexSimplify(rexBuilder, RelOptPredicateList.EMPTY, RexUtil.EXECUTOR) // 创建简化器，使用空谓词列表和执行器
            .withParanoid(true); // 启用偏执模式（更严格的简化）

    void checkNotSatisfiable(RexNode e) { // 方法：检查表达式不可满足
      assertFalse(SubstitutionVisitor.mayBeSatisfiable(e)); // 断言表达式不可满足
      final RexNode simple = simplify.simplifyUnknownAsFalse(e); // 简化表达式，将UNKNOWN视为FALSE
      assertFalse(RexLiteral.booleanValue(simple)); // 断言简化后的表达式布尔值为FALSE
    }

    void checkSatisfiable(RexNode e, String s) { // 方法：检查表达式可满足
      assertTrue(SubstitutionVisitor.mayBeSatisfiable(e)); // 断言表达式可满足
      final RexNode simple = simplify.simplifyUnknownAsFalse(e); // 简化表达式，将UNKNOWN视为FALSE
      assertThat(simple, hasToString(s)); // 断言简化后的表达式的字符串表示为s
    }
  }

}