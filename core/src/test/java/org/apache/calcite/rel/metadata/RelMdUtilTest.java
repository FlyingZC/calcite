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
package org.apache.calcite.rel.metadata; // 包声明：定义该测试类所在的包路径，位于org.apache.calcite.rel.metadata包下，该包包含关系表达式元数据相关的类和工具

import org.apache.calcite.plan.hep.HepPlanner; // 导入HepPlanner类：HepPlanner是Hep（启发式）规划器，用于执行基于规则的优化
import org.apache.calcite.plan.hep.HepProgram; // 导入HepProgram类：HepProgram定义了HepPlanner要执行的规则序列
import org.apache.calcite.plan.hep.HepProgramBuilder; // 导入HepProgramBuilder类：用于构建HepProgram的构建器类
import org.apache.calcite.rel.RelCollations; // 导入RelCollations类：提供创建和操作排序集合的实用方法
import org.apache.calcite.rel.RelNode; // 导入RelNode接口：代表关系代数表达式的基本接口，是所有关系节点的基类
import org.apache.calcite.rel.core.Aggregate; // 导入Aggregate类：表示聚合操作的关系节点，如GROUP BY、COUNT、SUM等
import org.apache.calcite.rel.core.Sort; // 导入Sort类：表示排序操作的关系节点，包含ORDER BY、LIMIT、OFFSET等
import org.apache.calcite.rel.rules.CoreRules; // 导入CoreRules类：包含Calcite核心优化规则集合
import org.apache.calcite.test.RelMetadataFixture; // 导入RelMetadataFixture类：测试元数据功能的测试工具类，提供SQL到RelNode的转换
import org.apache.calcite.tools.Frameworks; // 导入Frameworks类：提供创建Calcite框架和规划器的工厂方法
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类：不可变的位集合，用于高效表示和操作索引集合

import org.junit.jupiter.api.Test; // 导入Test注解：JUnit 5的测试注解，标记测试方法

import static org.apache.calcite.rel.metadata.RelMdUtil.numDistinctVals; // 静态导入：导入RelMdUtil类的numDistinctVals方法，用于计算不同值的数量

import static org.hamcrest.CoreMatchers.not; // 静态导入：导入Hamcrest的not匹配器，用于断言否定条件
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入：导入Hamcrest的断言方法，用于验证测试结果
import static org.hamcrest.Matchers.closeTo; // 静态导入：导入closeTo匹配器，用于验证浮点数是否接近期望值（考虑误差）
import static org.hamcrest.Matchers.lessThanOrEqualTo; // 静态导入：导入lessThanOrEqualTo匹配器，用于验证数值是否小于等于期望值
import static org.junit.jupiter.api.Assertions.assertEquals; // 静态导入：导入assertEquals断言方法，用于验证两个值是否相等
import static org.junit.jupiter.api.Assertions.assertFalse; // 静态导入：导入assertFalse断言方法，用于验证条件为false

/**
 * Test cases for {@link RelMdUtil}. // 测试类文档注释：该类包含RelMdUtil工具类的测试用例
 * RelMdUtil是Calcite中用于处理关系表达式元数据的工具类，提供了多种元数据计算和验证的静态方法
 * 测试用例覆盖了不同值数量计算、动态参数处理、聚合键映射等核心功能
 */ // 类文档注释结束
public class RelMdUtilTest { // 类声明：RelMdUtilTest是RelMdUtil工具类的测试类，包含多个测试方法验证其功能正确性

  /** Creates a fixture. */ // 方法文档注释：创建并返回一个RelMetadataFixture测试工具实例，用于后续测试
  protected RelMetadataFixture fixture() { // 方法声明：fixture方法是一个受保护的实例方法，返回RelMetadataFixture对象
    return RelMetadataFixture.DEFAULT; // 返回默认的RelMetadataFixture实例，该实例配置了标准的测试环境
  } // 方法结束

  final RelMetadataFixture sql(String sql) { // 方法声明：sql方法接受一个SQL字符串，返回RelMetadataFixture对象，用于构建关系表达式
    return fixture().withSql(sql); // 调用fixture()获取测试工具实例，然后调用withSql方法设置SQL语句，返回配置好的fixture
  } // 方法结束

  private static final double EPSILON = 1e-5; // 常量声明：EPSILON是一个静态最终double常量，值为1e-5（0.00001），用于浮点数比较的误差容忍度

  @Test void testNumDistinctVals() { // 测试方法声明：测试numDistinctVals方法的基本功能，验证其在不同场景下的计算正确性
    // the first element must be distinct, the second one has half chance of being distinct // 注释说明：第一个元素肯定是不同的，第二个元素有50%的概率与第一个不同
    assertThat(numDistinctVals(2.0, 2.0), closeTo(1.5, EPSILON)); // 断言：当域大小为2，选择2个元素时，期望的不同值数量为1.5（1 + 0.5），误差在EPSILON范围内

    // when no selection is made, we get no distinct value // 注释说明：当没有选择任何元素时，不同值的数量为0
    double domainSize = 100; // 变量声明：domainSize表示域的大小，即可能的不同值的总数，这里设为100
    assertThat(numDistinctVals(domainSize, 0.0), closeTo(0, EPSILON)); // 断言：当选择数量为0时，不同值数量应为0

    // when we perform one selection, we always have 1 distinct value, // 注释说明：当选择1个元素时，无论域大小如何，都只有1个不同的值
    // regardless of the domain size // 注释继续：这个值与域大小无关
    for (double dSize = 1; dSize < 100; dSize += 1) { // 循环：遍历域大小从1到99，每次增加1，测试不同域大小下选择1个元素的情况
      assertThat(numDistinctVals(dSize, 1.0), closeTo(1.0, EPSILON)); // 断言：无论域大小如何，选择1个元素时，不同值数量始终为1
    } // 循环结束

    // when we select n objects from a set with n values // 注释说明：当从n个值的集合中选择n个对象时
    // we get no more than n distinct values // 注释继续：我们得到的不同值数量不会超过n
    for (double dSize = 1; dSize < 100; dSize += 1) { // 循环：遍历域大小从1到99，测试选择数量等于域大小的情况
      assertThat(numDistinctVals(dSize, dSize), lessThanOrEqualTo(dSize)); // 断言：不同值数量应该小于或等于域大小
    } // 循环结束

    // when the number of selections is large enough // 注释说明：当选择数量足够大时
    // we get all distinct values, w.h.p. // 注释继续：我们以高概率（with high probability）获得所有不同的值
    assertThat(numDistinctVals(domainSize, domainSize * 100), // 断言：当选择数量是域大小的100倍时
        closeTo(domainSize, EPSILON)); // 期望的不同值数量接近域大小（即所有值都被选中）

    assertThat(numDistinctVals(100.0, 2.0), closeTo(1.99, EPSILON)); // 断言：域大小100，选择2个元素时，不同值数量约为1.99（2 - 2^2/100 + ...）
    assertThat(numDistinctVals(1000.0, 2.0), closeTo(1.999, EPSILON)); // 断言：域大小1000，选择2个元素时，不同值数量约为1.999（随着域增大，接近2）
    assertThat(numDistinctVals(10000.0, 2.0), closeTo(1.9999, EPSILON)); // 断言：域大小10000，选择2个元素时，不同值数量约为1.9999（更接近2）
  } // 测试方法结束

  @Test void testNumDistinctValsWithLargeDomain() { // 测试方法声明：测试numDistinctVals方法在超大域（1e18到1e20）下的性能和正确性
    double[] domainSizes = {1e18, 1e20}; // 数组声明：domainSizes包含两个超大的域大小：10的18次方和10的20次方
    double[] numSels = {1e2, 1e4, 1e6, 1e8, 1e10, 1e12}; // 数组声明：numSels包含6个不同的选择数量，从100到1万亿
    double res; // 变量声明：res用于存储numDistinctVals方法的返回结果
    for (double domainSize : domainSizes) { // 外层循环：遍历每个域大小
      for (double numSel : numSels) { // 内层循环：遍历每个选择数量
        res = numDistinctVals(domainSize, numSel); // 计算在给定域大小和选择数量下的不同值数量
        assertThat(res, not(0)); // 断言：结果不应该为0（因为至少选择了一些元素）
        // due to the possible duplicate selections, the distinct values // 注释说明：由于可能存在重复选择
        // must be smaller than or equal to the number of selections // 注释继续：不同值的数量必须小于或等于选择数量
        assertThat(res, lessThanOrEqualTo(numSel)); // 断言：验证不同值数量不超过选择数量
      } // 内层循环结束
      res = numDistinctVals(domainSize, 1.0); // 测试：从超大域中选择1个元素
      assertThat(res, closeTo(1.0, EPSILON)); // 断言：结果应该为1

      res = numDistinctVals(domainSize, 2.0); // 测试：从超大域中选择2个元素
      assertThat(res, closeTo(2.0, EPSILON)); // 断言：结果应该为2（因为域非常大，重复概率极低）
    } // 外层循环结束
  } // 测试方法结束

  @Test void testDynamicParameterInLimitOffset() { // 测试方法声明：测试在LIMIT和OFFSET中使用动态参数（问号?）时的元数据检查功能
    Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> { // 使用Frameworks工具创建规划器并执行lambda表达式
      RelMetadataQuery mq = cluster.getMetadataQuery(); // 获取元数据查询对象，用于查询关系表达式的元数据
      RelNode rel = sql("select * from emp limit ? offset ?").toRel(); // 将SQL转换为关系表达式，SQL中的问号是动态参数
      Sort sort = (Sort) rel; // 将关系表达式转换为Sort节点（因为包含LIMIT和OFFSET）
      assertFalse( // 断言：验证以下条件为false
          RelMdUtil.checkInputForCollationAndLimit(mq, sort.getInput(), // 调用RelMdUtil方法检查输入是否支持排序和限制
              RelCollations.EMPTY, sort.offset, sort.fetch), // 传入空排序集合和动态参数的offset、fetch，期望返回false
      return null; // lambda表达式返回null（因为不需要返回值）
    }); // withPlanner方法调用结束
  } // 测试方法结束

  /** Test case for // 测试方法文档注释开始：该测试用例用于验证CALCITE-6749问题
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6749">[CALCITE-6749] // JIRA问题链接：CALCITE-6749
   * RelMdUtil#setAggChildKeys may return an incorrect result</a>. // 问题描述：RelMdUtil的setAggChildKeys方法可能返回错误结果
   */ // 测试方法文档注释结束
  @Test void testSetAggChildKeys() { // 测试方法声明：测试setAggChildKeys方法在复杂聚合场景下的正确性，特别是右外连接后的聚合
    Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> { // 使用Frameworks工具创建规划器并执行lambda表达式
      RelNode rel = sql("select d.deptno, count(distinct e.job)\n" // SQL查询：选择部门编号和员工职位的不同计数
          + "from sales.emp e\n" // 从员工表e
          + "right outer join sales.dept d on e.deptno = d.deptno\n" // 右外连接部门表d，连接条件是部门编号相等
          + "group by d.deptno") // 按部门编号分组
          .withRelTransform(relNode -> { // 对关系表达式进行转换
            final HepProgramBuilder builder = HepProgram.builder(); // 创建HepProgram构建器
            builder.addRuleInstance(CoreRules.AGGREGATE_PROJECT_MERGE); // 添加聚合-投影合并规则
            final HepPlanner prePlanner = new HepPlanner(builder.build()); // 创建HepPlanner并传入构建的程序
            prePlanner.setRoot(relNode); // 设置规划器的根节点
            return prePlanner.findBestExp(); // 执行优化并返回最优表达式
          }).toRel(); // 转换为关系表达式
      final Aggregate agg = (Aggregate) rel; // 将关系表达式转换为Aggregate节点
      // We should get an Aggregate(group=[{9}], EXPR$1=[COUNT(DISTINCT $2)]) // 注释说明：期望的聚合节点结构
      assertEquals(1, agg.getGroupCount()); // 断言：分组键的数量为1（只有deptno）
      assertEquals(9, agg.getGroupSet().asList().get(0), // 断言：第一个分组键在子节点中的索引是9
      assertEquals(1, agg.getAggCallList().size()); // 断言：聚合调用列表大小为1（只有COUNT）
      assertEquals(1, agg.getAggCallList().get(0).getArgList().size()); // 断言：COUNT函数的参数列表大小为1
      assertEquals(2, agg.getAggCallList().get(0).getArgList().get(0), // 断言：COUNT函数的参数在子节点中的索引是2
      // The childKey corresponding to 0 (group key) must be 9 // 注释说明：聚合节点索引0（分组键）对应的子节点索引必须是9
      final ImmutableBitSet.Builder builder1 = ImmutableBitSet.builder(); // 创建第一个位集合构建器
      RelMdUtil.setAggChildKeys(ImmutableBitSet.of(0), agg, builder1); // 调用setAggChildKeys方法，将聚合节点索引0映射到子节点索引
      assertEquals(ImmutableBitSet.of(9), builder1.build()); // 断言：验证映射结果为索引9
      // The childKey corresponding to 1 (count aggCall) must be 2 // 注释说明：聚合节点索引1（COUNT调用）对应的子节点索引必须是2
      final ImmutableBitSet.Builder builder2 = ImmutableBitSet.builder(); // 创建第二个位集合构建器
      RelMdUtil.setAggChildKeys(ImmutableBitSet.of(1), agg, builder2); // 调用setAggChildKeys方法，将聚合节点索引1映射到子节点索引
      assertEquals(ImmutableBitSet.of(2), builder2.build()); // 断言：验证映射结果为索引2
      return null; // lambda表达式返回null
    }); // withPlanner方法调用结束
  } // 测试方法结束

} // 类结束
