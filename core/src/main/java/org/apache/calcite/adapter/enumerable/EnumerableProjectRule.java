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
// 声明包名，表示这个类属于org.apache.calcite.adapter.enumerable包，该包包含了可枚举适配器的实现
package org.apache.calcite.adapter.enumerable;

// 导入Convention类，用于表示关系代数表达式的约定（如物理实现方式）
import org.apache.calcite.plan.Convention;
// 导入RelOptRuleCall类，表示优化规则调用的上下文，包含规则匹配时的相关信息
import org.apache.calcite.plan.RelOptRuleCall;
// 导入RelNode类，是Calcite中所有关系代数节点的基类
import org.apache.calcite.rel.RelNode;
// 导入ConverterRule类，是转换规则的基类，用于将一种约定的节点转换为另一种约定的节点
import org.apache.calcite.rel.convert.ConverterRule;
// 导入Project类，表示投影操作的关系代数节点，用于选择、重命名和计算列
import org.apache.calcite.rel.core.Project;
// 导入LogicalProject类，表示逻辑层面的投影节点，是Project的子类
import org.apache.calcite.rel.logical.LogicalProject;
// 导入RexUtil类，提供RexNode（关系表达式）的工具方法
import org.apache.calcite.rex.RexUtil;

/**
 * 这是一个规则类，用于将逻辑投影节点（LogicalProject）转换为可枚举投影节点（EnumerableProject）。
 * 
 * 类的作用：
 * 1. 在Calcite的优化器中，规则（Rule）用于将逻辑计划转换为物理执行计划
 * 2. 这个规则专门负责将LogicalProject（逻辑投影）转换为EnumerableProject（可枚举投影）
 * 3. EnumerableProject是物理实现，支持通过Enumerable接口进行行级别的迭代和访问
 * 4. 你可以提供自定义配置来转换其他继承自Project的节点
 * 
 * 转换条件：
 * - 投影中不能包含窗口函数（OVER子句）
 * - 投影中不能包含多值表达式（M2V = Multi-Value）
 * - 投影中不能包含子查询
 * 
 * @see EnumerableRules#ENUMERABLE_PROJECT_RULE 查看默认的规则实例
 */
// EnumerableProjectRule继承自ConverterRule，表示这是一个转换规则
class EnumerableProjectRule extends ConverterRule {
  /**
   * 默认配置对象，用于定义规则的转换条件和行为
   * 
   * DEFAULT_CONFIG的作用：
   * 1. 定义了这个规则可以匹配哪些节点（这里是LogicalProject）
   * 2. 定义了转换的源约定和目标约定（从Convention.NONE到EnumerableConvention.INSTANCE）
   * 3. 定义了匹配条件（不能包含窗口函数、多值表达式、子查询）
   * 4. 定义了规则工厂方法，用于创建规则实例
   */
  static final Config DEFAULT_CONFIG = Config.INSTANCE
      // 将基础配置转换为Config类型，Config是ConverterRule的配置接口
      .as(Config.class)
      // 配置转换规则：
      // - LogicalProject.class：要转换的源节点类型
      // - p -> !p.containsOver() && !RexUtil.M2V_FINDER.inProject(p) && !RexUtil.SubQueryFinder.containsSubQuery(p)：匹配条件
      //   * p.containsOver()：检查投影是否包含窗口函数（如OVER子句），包含则不能转换
      //   * RexUtil.M2V_FINDER.inProject(p)：检查投影是否包含多值表达式（一个表达式产生多行），包含则不能转换
      //   * RexUtil.SubQueryFinder.containsSubQuery(p)：检查投影是否包含子查询，包含则不能转换
      // - Convention.NONE：源约定，表示逻辑层面的节点
      // - EnumerableConvention.INSTANCE：目标约定，表示转换为可枚举的物理实现
      // - "EnumerableProjectRule"：规则的名称，用于调试和日志
      .withConversion(LogicalProject.class, p ->
              !p.containsOver()  // 不包含窗口函数
                  && !RexUtil.M2V_FINDER.inProject(p)  // 不包含多值表达式
                  && !RexUtil.SubQueryFinder.containsSubQuery(p),  // 不包含子查询
          Convention.NONE, EnumerableConvention.INSTANCE,
          "EnumerableProjectRule")
      // 设置规则工厂方法，使用方法引用EnumerableProjectRule::new来创建规则实例
      .withRuleFactory(EnumerableProjectRule::new);

  /**
   * 构造方法，用于创建EnumerableProjectRule实例
   * 
   * @param config 规则的配置对象，包含了转换条件、源约定、目标约定等信息
   * 
   * 构造方法的作用：
   * 1. 通过传入的配置对象初始化规则
   * 2. 调用父类ConverterRule的构造方法，将配置传递给父类
   * 3. 保护类型（protected），允许子类扩展，但不允许外部直接实例化
   */
  protected EnumerableProjectRule(Config config) {
    // 调用父类ConverterRule的构造方法，传入配置对象
    // 父类会根据配置初始化规则的各种属性（如匹配条件、转换目标等）
    super(config);
  }

  /**
   * 判断当前规则是否可以应用于给定的规则调用
   * 
   * @param call 规则调用的上下文对象，包含匹配的节点信息
   * @return 如果规则可以应用返回true，否则返回false
   * 
   * matches方法的作用：
   * 1. 在默认配置的匹配条件之外，提供额外的匹配检查
   * 2. 这里检查投影节点是否使用了变量（如correlated variables）
   * 3. 如果投影使用了变量，则不能转换为EnumerableProject，因为可枚举实现不支持相关变量
   * 4. 这个检查是必要的，因为相关变量需要特殊的处理机制
   */
  @Override public boolean matches(RelOptRuleCall call) {
    // 从规则调用中获取第0个关系节点（即LogicalProject）
    // RelOptRuleCall.rel(0)获取匹配的第一个节点，在这个规则中就是Project节点
    Project project = call.rel(0);
    // 检查投影节点是否使用了变量（variables set）
    // project.getVariablesSet()返回投影中使用的变量集合
    // isEmpty()表示没有使用变量，可以转换；如果有变量则不能转换
    return project.getVariablesSet().isEmpty();
  }

  /**
   * 执行实际的转换操作，将逻辑投影节点转换为可枚举投影节点
   * 
   * @param rel 要转换的关系节点，这里应该是Project类型
   * @return 转换后的EnumerableProject节点
   * 
   * convert方法的作用：
   * 1. 将输入的LogicalProject节点转换为EnumerableProject节点
   * 2. 首先需要转换输入节点，将其也转换为Enumerable约定
   * 3. 然后创建新的EnumerableProject节点，保持原有的投影表达式和行类型
   * 
   * 转换过程：
   * 1. 获取原始Project节点的输入
   * 2. 将输入节点的约定从Convention.NONE转换为EnumerableConvention.INSTANCE
   * 3. 使用转换后的输入、原始的投影表达式和行类型创建新的EnumerableProject
   */
  @Override public RelNode convert(RelNode rel) {
    // 将输入的RelNode强制转换为Project类型
    // 这里可以安全转换，因为matches方法已经确保了节点类型正确
    final Project project = (Project) rel;
    // 创建并返回EnumerableProject节点
    // EnumerableProject.create是静态工厂方法，用于创建EnumerableProject实例
    return EnumerableProject.create(
        // 转换输入节点：
        // 1. project.getInput()：获取Project的输入节点（可能是LogicalTableScan或其他节点）
        // 2. convert()方法：递归调用转换方法，将输入节点也转换为Enumerable约定
        // 3. project.getInput().getTraitSet()：获取输入节点的特征集合（TraitSet）
        // 4. .replace(EnumerableConvention.INSTANCE)：将特征集合中的约定替换为EnumerableConvention
        //    这样就将输入节点从逻辑约定转换为可枚举的物理约定
        convert(project.getInput(),
            project.getInput().getTraitSet()
                .replace(EnumerableConvention.INSTANCE)),
        // project.getProjects()：获取投影中的表达式列表
        // 这些表达式定义了如何从输入行生成输出行的每一列
        // 例如：["EMP.DEPTNO", "EMP.ENAME * 2"]表示输出两列，第一列是部门号，第二列是员工名的两倍
        project.getProjects(),
        // project.getRowType()：获取投影的行类型
        // 行类型定义了输出行的结构，包括列名、列类型等信息
        // 例如：{DEPTNO: INTEGER, DOUBLE_NAME: VARCHAR}
        project.getRowType());
  }
}
