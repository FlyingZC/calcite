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
// Apache Calcite 是一个动态数据管理框架，提供SQL解析、优化、执行等功能
// 本包 org.apache.calcite.adapter.csv 包含CSV文件适配器的实现
package org.apache.calcite.adapter.csv;

// 导入RelOptRuleCall类，表示优化规则调用的上下文，包含匹配到的关系表达式节点
import org.apache.calcite.plan.RelOptRuleCall;
// 导入RelRule基类，所有优化规则的基类，提供规则的基础配置和功能
import org.apache.calcite.plan.RelRule;
// 导入LogicalProject类，表示逻辑投影操作节点，用于选择、重命名或计算列
import org.apache.calcite.rel.logical.LogicalProject;
// 导入RexInputRef类，表示行表达式的输入引用，即对输入字段列的引用
import org.apache.calcite.rex.RexInputRef;
// 导入RexNode类，表示行表达式的基类，是所有行表达式节点的父接口
import org.apache.calcite.rex.RexNode;

// 导入Nullable注解，表示字段或返回值可以为null，用于静态空值检查
import org.checkerframework.checker.nullness.qual.Nullable;
// 导入Value注解，用于Immutables库，自动生成不可变对象的实现代码
import org.immutables.value.Value;

// 导入List接口，用于存储有序集合，这里用于存储投影表达式列表
import java.util.List;

/**
 * Planner rule that projects from a {@link CsvTableScan} scan just the columns
 * needed to satisfy a projection. If the projection's expressions are trivial,
 * the projection is removed.
 *
 * @see CsvRules#PROJECT_SCAN
 */
// 该类是一个优化规则，用于将LogicalProject(投影)操作下推到CsvTableScan(表扫描)中
// 这样可以只读取查询所需的列，减少I/O开销，提高查询性能
// 如果投影表达式是简单的字段引用（不包含复杂计算），则移除投影节点，直接在扫描时过滤列
// 这是列式裁剪(Column Pruning)优化技术的一种实现
// @Value.Enclosing注解表示该类包含内部接口Config，用于Immutables生成不可变配置对象
@Value.Enclosing
public class CsvProjectTableScanRule
    extends RelRule<CsvProjectTableScanRule.Config> { // 继承RelRule基类，泛型参数为Config接口类型，用于规则配置

  /** Creates a CsvProjectTableScanRule. */
  // 构造方法，使用Config对象初始化规则
  // Config参数包含规则的配置信息，如匹配的operand模式、描述等
  protected CsvProjectTableScanRule(Config config) {
    super(config); // 调用父类RelRule的构造方法，传入配置对象
  }

  // onMatch方法是RelRule的核心方法，当规则匹配成功时被调用
  // 参数call包含匹配到的关系表达式节点和相关信息
  @Override public void onMatch(RelOptRuleCall call) {
    // 从调用对象中获取第一个关系表达式，即LogicalProject节点
    // call.rel(0)获取匹配模式的第0个操作数，对应LogicalProject
    final LogicalProject project = call.rel(0);
    // 从调用对象中获取第二个关系表达式，即CsvTableScan节点
    // call.rel(1)获取匹配模式的第1个操作数，对应CsvTableScan
    final CsvTableScan scan = call.rel(1);
    // 调用getProjectFields方法分析投影表达式，提取字段索引
    // project.getProjects()返回投影表达式的列表
    // 如果投影包含复杂表达式（如函数调用、运算等），返回null
    @Nullable int[] fields = getProjectFields(project.getProjects());
    // 如果fields为null，说明投影包含复杂表达式，无法下推到扫描阶段
    if (fields == null) {
      // Project contains expressions more complex than just field references.
      return; // 直接返回，不进行优化转换
    }
    // 如果投影只包含简单的字段引用，则进行优化转换
    // call.transformTo()将原始关系表达式树转换为新的优化后的树
    // 创建新的CsvTableScan节点，只读取fields指定的列
    call.transformTo(
        new CsvTableScan(
            scan.getCluster(), // 传递集群信息，包含优化器和类型系统
            scan.getTable(), // 传递表信息，包含表名、字段类型等元数据
            scan.csvTable, // 传递CSV表的具体实现，包含CSV文件读取逻辑
            fields)); // 传递字段索引数组，只读取这些列
  }

  // 静态辅助方法，用于分析投影表达式列表，提取字段索引
  // 参数exps是RexNode列表，表示投影中的每个表达式
  // 返回int数组，包含每个表达式的字段索引；如果包含复杂表达式，返回null
  private static int @Nullable [] getProjectFields(List<RexNode> exps) {
    // 创建与表达式列表大小相同的整型数组，用于存储字段索引
    final int[] fields = new int[exps.size()];
    // 遍历所有投影表达式
    for (int i = 0; i < exps.size(); i++) {
      // 获取第i个表达式
      final RexNode exp = exps.get(i);
      // 检查表达式是否为RexInputRef类型（即简单的字段引用）
      // RexInputRef表示对输入字段的直接引用，如 "SELECT col1 FROM table" 中的col1
      if (exp instanceof RexInputRef) {
        // 如果是字段引用，获取其索引值并存储到fields数组
        // getIndex()返回字段在输入表中的位置（从0开始）
        fields[i] = ((RexInputRef) exp).getIndex();
      } else {
        // 如果表达式不是简单的字段引用（如包含函数、运算等），返回null
        // 这种情况无法下推到扫描阶段，需要在投影阶段计算
        return null; // not a simple projection
      }
    }
    // 所有表达式都是简单的字段引用，返回字段索引数组
    return fields;
  }

  /** Rule configuration. */
  // Config接口定义规则的配置，使用@Value.Immutable注解自动生成不可变实现类
  // singleton=false表示不是单例模式，可以创建多个实例
  @Value.Immutable(singleton = false)
  public interface Config extends RelRule.Config { // 继承RelRule.Config接口，获得基础配置功能
    // DEFAULT常量定义规则的默认配置
    // 使用builder模式构建配置对象
    Config DEFAULT = ImmutableCsvProjectTableScanRule.Config.builder()
        // withOperandSupplier定义规则匹配的操作数模式
        // 使用lambda表达式定义匹配模式：LogicalProject作为根节点，有一个输入CsvTableScan
        // b0表示第一个操作数构建器，b1表示第二个操作数构建器
        .withOperandSupplier(b0 ->
            b0.operand(LogicalProject.class).oneInput(b1 -> // 匹配LogicalProject节点，它有一个输入
                b1.operand(CsvTableScan.class).noInputs())) // LogicaProject的输入是CsvTableScan，CsvTableScan没有输入（叶子节点）
        .build(); // 构建配置对象

    // toRule方法将配置转换为规则实例
    // 这是RelRule.Config接口要求实现的方法
    @Override default CsvProjectTableScanRule toRule() {
      // 创建并返回CsvProjectTableScanRule实例，传入this（当前配置对象）
      return new CsvProjectTableScanRule(this);
    }
  }
}
