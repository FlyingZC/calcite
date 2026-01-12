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
package org.apache.calcite.adapter.file; // 定义包名，表示这个类属于Calcite框架的文件适配器模块

import org.apache.calcite.plan.RelOptRuleCall; // 导入规则调用类，用于在规则匹配时调用规则
import org.apache.calcite.plan.RelRule; // 导入规则基类，所有优化规则都继承自此类
import org.apache.calcite.rel.logical.LogicalProject; // 导入逻辑投影节点，表示投影操作
import org.apache.calcite.rex.RexInputRef; // 导入行表达式输入引用，表示对输入字段的引用
import org.apache.calcite.rex.RexNode; // 导入行表达式节点基类，表示表达式树中的节点

import org.immutables.value.Value; // 导入不可变值注解，用于生成不可变配置类

import java.util.List; // 导入List接口，用于存储表达式列表

/**
 * Planner rule that projects from a {@link CsvTableScan} scan just the columns
 * needed to satisfy a projection. If the projection's expressions are trivial,
 * the projection is removed.
 * // 这是一个优化规则，用于从CsvTableScan扫描中只投影出满足投影操作所需的列
 * // 如果投影的表达式很简单（只是字段引用），则移除投影节点
 * // 这样可以减少数据扫描量，提高查询性能
 *
 * @see FileRules#PROJECT_SCAN
 * // 参见FileRules中的PROJECT_SCAN规则定义
 */
@Value.Enclosing // 标记这个类包含不可变配置类
public class CsvProjectTableScanRule // 定义CSV投影表扫描规则类
    extends RelRule<CsvProjectTableScanRule.Config> { // 继承自RelRule基类，使用Config作为配置类型

  /** Creates a CsvProjectTableScanRule. */
  protected CsvProjectTableScanRule(Config config) { // 构造方法，接收配置对象
    super(config); // 调用父类RelRule的构造方法初始化配置
  }

  @Override public void onMatch(RelOptRuleCall call) { // 重写onMatch方法，当规则匹配时被调用
    final LogicalProject project = call.rel(0); // 从规则调用中获取第一个关系节点，即LogicalProject投影节点
    final CsvTableScan scan = call.rel(1); // 从规则调用中获取第二个关系节点，即CsvTableScan扫描节点
    int[] fields = getProjectFields(project.getProjects()); // 调用辅助方法获取投影所需的字段索引数组
    if (fields == null) { // 如果返回null，说明投影包含不仅仅是字段引用的复杂表达式
      // Project contains expressions more complex than just field references.
      // 投影包含比字段引用更复杂的表达式，无法优化，直接返回
      return;
    }
    call.transformTo( // 执行转换，将原始关系树转换为优化后的关系树
        new CsvTableScan( // 创建新的CsvTableScan节点，只扫描需要的字段
            scan.getCluster(), // 使用原扫描节点的集群对象
            scan.getTable(), // 使用原扫描节点的表对象
            scan.csvTable, // 使用原扫描节点的CSV表对象
            fields)); // 传入需要扫描的字段索引数组，实现列剪裁优化
  }

  private static int[] getProjectFields(List<RexNode> exps) { // 私有静态辅助方法，从表达式列表中提取字段索引
    final int[] fields = new int[exps.size()]; // 创建与表达式数量相同大小的整型数组，用于存储字段索引
    for (int i = 0; i < exps.size(); i++) { // 遍历所有表达式
      final RexNode exp = exps.get(i); // 获取当前表达式
      if (exp instanceof RexInputRef) { // 检查表达式是否为输入字段引用类型
        fields[i] = ((RexInputRef) exp).getIndex(); // 如果是字段引用，获取其索引并存储到数组中
      } else { // 如果表达式不是简单的字段引用
        return null; // not a simple projection // 不是简单投影，返回null表示无法优化
      }
    }
    return fields; // 所有表达式都是简单字段引用，返回字段索引数组
  }

  /** Rule configuration. */
  @Value.Immutable(singleton = false) // 标记为不可变配置类，不使用单例模式
  public interface Config extends RelRule.Config { // 定义规则配置接口，继承自RelRule.Config
    Config DEFAULT = ImmutableCsvProjectTableScanRule.Config.builder() // 创建默认配置实例
        .withOperandSupplier(b0 -> // 设置操作数提供者，定义规则匹配模式
            b0.operand(LogicalProject.class).oneInput(b1 -> // 匹配LogicalProject节点，它有一个输入
                b1.operand(CsvTableScan.class).noInputs())) // 匹配CsvTableScan节点，它没有输入
        .build(); // 构建配置对象

    @Override default CsvProjectTableScanRule toRule() { // 重写toRule方法，将配置转换为规则实例
      return new CsvProjectTableScanRule(this); // 创建并返回CsvProjectTableScanRule实例
    }
  }
}
