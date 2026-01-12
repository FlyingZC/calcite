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
package org.apache.calcite.test; // 声明包名，该类位于org.apache.calcite.test包下

import org.apache.calcite.plan.ConventionTraitDef; // 导入ConventionTraitDef类，用于定义关系表达式的约定特征
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系优化集群，包含RexBuilder等共享对象
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil工具类，提供关系表达式优化的实用方法
import org.apache.calcite.plan.hep.HepPlanner; // 导入HepPlanner类，基于启发式规则的规划器
import org.apache.calcite.plan.hep.HepProgram; // 导入HepProgram类，定义HepPlanner的规则执行程序
import org.apache.calcite.plan.visualizer.RuleMatchVisualizer; // 导入RuleMatchVisualizer类，用于可视化规则匹配过程
import org.apache.calcite.plan.volcano.VolcanoPlanner; // 导入VolcanoPlanner类，基于代价的火山式优化器
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef类，用于定义关系表达式的排序特征
import org.apache.calcite.rel.rules.CoreRules; // 导入CoreRules类，包含Calcite的核心优化规则

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值
import org.junit.jupiter.api.AfterAll; // 导入AfterAll注解，用于标记在所有测试方法执行后运行的方法
import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法

import java.util.HashMap; // 导入HashMap类，用于存储键值对映射
import java.util.Map; // 导入Map接口，提供映射操作的抽象
import java.util.regex.Matcher; // 导入Matcher类，用于正则表达式匹配
import java.util.regex.Pattern; // 导入Pattern类，用于编译正则表达式

import static java.util.Objects.requireNonNull; // 导入requireNonNull静态方法，用于检查对象不为null

/**
 * 检查 {@link RuleMatchVisualizer} 的输出结果
 * 
 * 这个测试类用于验证规则匹配可视化工具（RuleMatchVisualizer）的功能
 * RuleMatchVisualizer是一个工具类，用于在优化器执行规则匹配时收集和可视化相关信息
 * 它可以记录哪些规则被匹配、在哪些关系表达式上匹配、匹配的顺序等详细信息
 * 
 * 该测试类主要测试两种优化器：
 * 1. HepPlanner（启发式规划器）：基于规则的优化器，按照预定义的规则顺序执行
 * 2. VolcanoPlanner（火山规划器）：基于代价的优化器，使用动态规划算法
 * 
 * 测试方法会：
 * - 创建优化器并配置规则
 * - 附加RuleMatchVisualizer到优化器
 * - 执行SQL查询优化
 * - 获取可视化输出的JSON字符串
 * - 对输出进行规范化处理（重命名ID以避免测试间的依赖）
 * - 验证输出是否符合预期
 */
class RuleMatchVisualizerTest extends RelOptTestBase { // 定义RuleMatchVisualizerTest类，继承自RelOptTestBase基类

  @Nullable // 使用Nullable注解标记该字段可能为null
  private static DiffRepository diffRepos = null; // 静态成员变量：差异仓库，用于存储和比较测试的实际输出与预期输出，初始为null

  @AfterAll // 使用AfterAll注解，表示该方法在所有测试方法执行完毕后运行一次
  public static void checkActualAndReferenceFiles() { // 静态方法：检查实际输出文件和参考文件是否一致
    if (diffRepos != null) { // 如果差异仓库不为null
      diffRepos.checkActualAndReferenceFiles(); // 调用差异仓库的检查方法，比较实际输出和预期输出
    }
  }

  @Override // 重写父类的方法
  RelOptFixture fixture() { // 方法：创建并配置RelOptFixture测试装置对象
    RelOptFixture fixture = super.fixture() // 调用父类的fixture()方法获取基础测试装置
        .withDiffRepos(DiffRepository.lookup(RuleMatchVisualizerTest.class)); // 使用DiffRepository查找当前类的差异仓库并设置到测试装置中
    diffRepos = fixture.diffRepos(); // 从测试装置中获取差异仓库引用并保存到静态变量
    return fixture; // 返回配置好的测试装置对象
  }

  @Test // 使用Test注解，表示这是一个测试方法
  void testHepPlanner() { // 方法：测试HepPlanner（启发式规划器）的规则匹配可视化功能
    final String sql = "select a.name from dept a\n" // 定义测试SQL语句，从dept表a中选择name字段
        + "union all\n" // 使用UNION ALL合并两个查询结果
        + "select b.name from dept b\n" // 从dept表b中选择name字段
        + "order by name limit 10"; // 按name排序并限制返回10条记录

    final HepProgram program = HepProgram.builder() // 创建HepProgram构建器，用于构建启发式规则执行程序
        .addRuleInstance(CoreRules.PROJECT_SET_OP_TRANSPOSE) // 添加PROJECT_SET_OP_TRANSPOSE规则，该规则将Project操作下推到SetOp（集合操作）之下
        .addRuleInstance(CoreRules.SORT_UNION_TRANSPOSE) // 添加SORT_UNION_TRANSPOSE规则，该规则将Sort操作与Union操作交换位置
        .build(); // 构建HepProgram对象
    HepPlanner planner = new HepPlanner(program); // 创建HepPlanner实例，传入构建好的规则执行程序

    RuleMatchVisualizer viz = new RuleMatchVisualizer(); // 创建RuleMatchVisualizer实例，用于收集规则匹配信息
    viz.attachTo(planner); // 将可视化工具附加到规划器上，这样规划器执行时会记录规则匹配信息

    final RelOptFixture fixture = sql(sql).withPlanner(planner); // 创建测试装置，设置SQL语句和使用的规划器
    fixture.check(); // 执行测试，验证SQL优化结果

    String result = normalize(viz.getJsonStringResult()); // 获取可视化工具的JSON输出字符串，并进行规范化处理
    fixture.diffRepos().assertEquals("visualizer", "${visualizer}", result); // 将规范化后的结果与预期输出进行比较
  }

  @Test // 使用Test注解，表示这是一个测试方法
  void testVolcanoPlanner() { // 方法：测试VolcanoPlanner（火山规划器）的规则匹配可视化功能
    final String sql = "select a.name from dept a"; // 定义简单的测试SQL语句，从dept表a中选择name字段

    VolcanoPlanner planner = new VolcanoPlanner(); // 创建VolcanoPlanner实例，这是基于代价的优化器
    planner.setTopDownOpt(false); // 设置优化策略为自底向上（false表示不自顶向下优化）
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 添加Convention特征定义，用于表示关系表达式的物理实现约定
    planner.addRelTraitDef(RelCollationTraitDef.INSTANCE); // 添加RelCollation特征定义，用于表示关系表达式的排序信息

    RelOptUtil.registerDefaultRules(planner, false, false); // 向规划器注册默认的优化规则，两个false参数表示不注册特定类型的规则

    RuleMatchVisualizer viz = new RuleMatchVisualizer(); // 创建RuleMatchVisualizer实例，用于收集规则匹配信息
    viz.attachTo(planner); // 将可视化工具附加到规划器上

    final RelOptFixture fixture = sql(sql) // 创建测试装置，设置SQL语句
        .withPlanner(planner) // 设置使用的规划器
        .withFactory(t -> // 使用工厂方法配置测试装置
            t.withCluster(cluster -> // 配置关系表达式集群
                RelOptCluster.create(planner, cluster.getRexBuilder()))); // 使用规划器和RexBuilder创建新的RelOptCluster
    fixture.check(); // 执行测试，验证SQL优化结果

    String result = normalize(viz.getJsonStringResult()); // 获取可视化工具的JSON输出字符串，并进行规范化处理
    fixture.diffRepos().assertEquals("visualizer", "${visualizer}", result); // 将规范化后的结果与预期输出进行比较
  }

  /**
   * 规范化可视化输出，使其独立于其他测试
   * 
   * 这个方法的目的是将可视化输出中的ID（如关系ID、规则调用ID等）重命名
   * 为连续的索引值，从而避免测试间的依赖性
   * 
   * 例如，如果不同测试运行时生成的ID不同，规范化后可以使输出保持一致
   * 这样就可以使用固定的参考文件来验证测试结果
   * 
   * @param str 原始的JSON字符串输出
   * @return 规范化后的JSON字符串
   */
  private String normalize(String str) { // 方法：规范化可视化输出字符串，使其独立于其他测试
    // rename rel ids // 重命名关系ID
    str = // 将重命名后的字符串赋值给str
        renameMatches(str, // 调用renameMatches方法重命名匹配的字符串
            Pattern.compile("\"([0-9]+)\"|" // 编译正则表达式，匹配双引号中的数字（关系ID）
                + "\"label\" *: *\"#([0-9]+)-|" // 匹配label字段中的#数字-格式（关系标签）
                + "\"label\" *: *\"subset#([0-9]+)-|" // 匹配label字段中的subset#数字-格式（子集标签）
                + "\"explanation\" *: *\"\\{subset=rel#([0-9]+):"), // 匹配explanation字段中的subset=rel#数字:格式（解释信息）
            1000); // 起始偏移量为1000，避免与规则调用ID冲突
    // rename rule call ids // 重命名规则调用ID
    str = renameMatches(str, Pattern.compile("\"id\" *: *\"([0-9]+)-"), 100); // 重命名id字段中的数字-格式，起始偏移量为100
    return str; // 返回规范化后的字符串
  }

  /**
   * 将每个匹配的第一个组重命名为从偏移量开始的连续索引
   * 
   * 这个方法使用正则表达式匹配字符串中的特定模式，并将匹配到的值
   * 重命名为连续的索引值。例如，将"rel#5"、"rel#10"等重命名为"rel#1000"、"rel#1001"等
   * 
   * 工作原理：
   * 1. 使用正则表达式在字符串中查找所有匹配项
   * 2. 对于每个匹配项，找到第一个非空的捕获组
   * 3. 使用HashMap记录旧名称到新名称的映射
   * 4. 将字符串中的旧名称替换为新名称
   * 
   * @param str 要处理的原始字符串
   * @param pattern 正则表达式模式，用于匹配需要重命名的内容
   * @param offset 起始偏移量，新名称从这个值开始递增
   * @return 重命名后的字符串
   */
  private String renameMatches(final String str, // 参数：要处理的原始字符串
      final Pattern pattern, int offset) { // 参数：正则表达式模式和起始偏移量
    Map<String, String> rename = new HashMap<>(); // 创建HashMap用于存储旧名称到新名称的映射关系
    StringBuilder sb = new StringBuilder(); // 创建StringBuilder用于构建结果字符串
    Matcher m = pattern.matcher(str); // 使用正则表达式模式创建Matcher对象，用于在字符串中查找匹配

    int last = 0; // 记录上次处理的结束位置
    while (m.find()) { // 循环查找所有匹配项
      int start = -1; // 初始化匹配的起始位置为-1
      int end = -1; // 初始化匹配的结束位置为-1
      String oldName = null; // 初始化旧名称为null
      for (int i = 1; i <= m.groupCount(); i++) { // 遍历所有捕获组（从第1组开始，第0组是整个匹配）
        if (m.group(i) != null) { // 如果当前捕获组不为null（表示该组有匹配内容）
          oldName = m.group(i); // 获取捕获组的值作为旧名称
          start = m.start(i); // 获取捕获组的起始位置
          end = m.end(i); // 获取捕获组的结束位置
          break; // 找到第一个非空捕获组后退出循环
        }
      }
      requireNonNull(oldName, "oldName"); // 检查oldName不为null，如果为null则抛出NullPointerException
      String newName = rename.computeIfAbsent(oldName, k -> "" + (rename.size() + offset)); // 如果旧名称不在映射中，则创建新名称（当前映射大小+偏移量）
      sb.append(str, last, start); // 将上次结束位置到当前匹配起始位置之间的字符串添加到StringBuilder
      sb.append(newName); // 将新名称添加到StringBuilder
      last = end; // 更新上次处理的结束位置为当前匹配的结束位置
    }
    sb.append(str.substring(last)); // 将剩余的字符串（从last到末尾）添加到StringBuilder
    return sb.toString(); // 返回构建好的结果字符串
  }

} // 类定义结束
