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
// Apache许可证声明，说明代码版权归属和使用许可
package org.apache.calcite.adapter.enumerable; // 定义当前类所在的包路径，org.apache.calcite.adapter.enumerable是Calcite可枚举适配器包

import org.apache.calcite.plan.Convention; // 导入Convention接口，用于定义关系代数表达式的调用约定（如逻辑约定、物理约定等）
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，是Calcite中所有关系代数节点（表、连接、过滤等）的基类
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule抽象类，是所有转换规则的基类，用于将一种RelNode转换为另一种RelNode
import org.apache.calcite.rel.logical.LogicalAsofJoin; // 导入LogicalAsofJoin类，表示逻辑层面的"as-of"连接操作，用于根据时间戳或序列号查找匹配的记录

import java.util.ArrayList; // 导入ArrayList类，用于动态数组列表，存储转换后的输入节点
import java.util.List; // 导入List接口，定义列表集合的通用接口

/** Planner rule that converts a
 * {@link LogicalAsofJoin} relational expression
 * {@link EnumerableConvention enumerable calling convention}.
 *
 * @see EnumerableRules#ENUMERABLE_JOIN_RULE */
// 类文档注释：这是一个规划器规则类，用于将逻辑层面的AsOf连接关系表达式转换为可枚举调用约定
// AsOf连接是一种特殊的连接操作，用于查找"截至某个时间点"的匹配记录，常用于时间序列数据处理
// 该规则继承自ConverterRule，实现了从逻辑层到物理层的转换
// @see引用了EnumerableRules中的枚举连接规则
class EnumerableAsofJoinRule extends ConverterRule { // 定义EnumerableAsofJoinRule类，继承自ConverterRule，实现AsOf连接的转换规则
  /** Default configuration. */
  // 成员变量DEFAULT_CONFIG：默认配置对象，静态常量，所有实例共享
  // Config.INSTANCE是ConverterRule.Config的默认实例，提供了基础的转换规则配置
  public static final Config DEFAULT_CONFIG = Config.INSTANCE // 获取默认配置实例
      .withConversion(LogicalAsofJoin.class, Convention.NONE, // 配置转换规则：指定源类型为LogicalAsofJoin（逻辑AsOf连接），源约定为NONE（无约定，表示逻辑层）
          EnumerableConvention.INSTANCE, "EnumerableAsofJoinRule") // 目标约定为EnumerableConvention.INSTANCE（可枚举约定），规则名称为"EnumerableAsofJoinRule"
      .withRuleFactory(EnumerableAsofJoinRule::new); // 设置规则工厂，使用方法引用EnumerableAsofJoinRule::new，当需要创建规则实例时会调用构造函数

  /** Called from the Config. */
  // 构造方法文档注释：说明此构造方法由Config对象调用
  protected EnumerableAsofJoinRule(Config config) { // 受保护的构造方法，接收Config参数，用于初始化转换规则
    super(config); // 调用父类ConverterRule的构造方法，传入config参数，完成规则的初始化配置
  }

  @Override public RelNode convert(RelNode rel) { // 重写convert方法，执行实际的转换操作，接收RelNode参数，返回转换后的RelNode
    LogicalAsofJoin join = (LogicalAsofJoin) rel; // 将输入的RelNode强制转换为LogicalAsofJoin类型，获取逻辑AsOf连接节点
    List<RelNode> newInputs = new ArrayList<>(); // 创建新的输入节点列表，用于存储转换后的左右输入节点
    for (RelNode input : join.getInputs()) { // 遍历LogicalAsofJoin的所有输入节点（通常是左表和右表）
      if (!(input.getConvention() instanceof EnumerableConvention)) { // 检查当前输入节点的约定是否已经是EnumerableConvention，如果不是则需要进行转换
        input = // 赋值操作，将转换后的节点重新赋值给input变量
            convert( // 调用convert方法进行节点转换，将节点从当前约定转换为可枚举约定
                input, // 第一个参数：需要转换的输入节点
                input.getTraitSet() // 获取输入节点的特征集合（TraitSet），特征集合包含了节点的各种属性（如约定、排序、分布等）
                    .replace(EnumerableConvention.INSTANCE)); // 将特征集合中的约定替换为EnumerableConvention.INSTANCE，生成新的特征集合
      }
      newInputs.add(input); // 将处理后的输入节点（可能是原始节点或转换后的节点）添加到newInputs列表中
    }
    final RelNode left = newInputs.get(0); // 从newInputs列表中获取第一个节点作为左表，final修饰表示引用不可变
    final RelNode right = newInputs.get(1); // 从newInputs列表中获取第二个节点作为右表，final修饰表示引用不可变

    return EnumerableAsofJoin.create( // 创建并返回物理层面的EnumerableAsofJoin节点，完成从逻辑层到物理层的转换
        left, // 第一个参数：左表节点，已转换为可枚举约定
        right, // 第二个参数：右表节点，已转换为可枚举约定
        join.getCondition(), // 第三个参数：连接条件，从原始LogicalAsofJoin中获取，定义了两个表之间的连接谓词
        join.getMatchCondition(), // 第四个参数：匹配条件，AsOf连接特有的条件，用于确定"截至"点的匹配规则
        join.getVariablesSet(), // 第五个参数：变量集合，AsOf连接中使用的变量集合，通常包含时间戳或序列号字段
        join.getJoinType()); // 第六个参数：连接类型（如INNER、LEFT等），从原始LogicalAsofJoin中获取
  }
}
