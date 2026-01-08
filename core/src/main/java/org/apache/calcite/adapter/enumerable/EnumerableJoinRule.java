/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看随此工作分发的NOTICE文件
 * this work for additional information regarding copyright ownership.  // 以获取有关版权所有权的附加信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache许可证2.0版授予您使用此文件的许可
 * (the "License"); you may not use this file except in compliance with // ("许可证")；除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下位置获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // 许可证网址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则根据许可证分发的软件
 * distributed under the License is distributed on an "AS IS" BASIS, // 按"原样"基础分发，不附带任何明示或暗示的保证或条件
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 无论是明示的还是暗示的
 * See the License for the specific language governing permissions and // 请参阅许可证以获取特定语言的权限和
 * limitations under the License. // 许可下的限制
 */
package org.apache.calcite.adapter.enumerable; // 声明包名：org.apache.calcite.adapter.enumerable，这个包包含可枚举适配器相关类

import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系代数表达式的调用约定
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式树的节点
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，用于将一种调用约定转换为另一种调用约定的规则
import org.apache.calcite.rel.core.Join; // 导入Join抽象类，表示关系代数中的连接操作
import org.apache.calcite.rel.core.JoinInfo; // 导入JoinInfo类，包含连接条件的分析信息
import org.apache.calcite.rel.logical.LogicalJoin; // 导入LogicalJoin类，表示逻辑层面的连接操作
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，用于构建行表达式（RexNode）
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，表示行表达式
import org.apache.calcite.rex.RexUtil; // 导入RexUtil工具类，提供行表达式的实用方法

import java.util.ArrayList; // 导入ArrayList类，动态数组实现
import java.util.Arrays; // 导入Arrays类，提供数组操作的实用方法
import java.util.List; // 导入List接口，表示有序集合

/** // 类文档注释开始
 * Planner rule that converts a // 这是一个优化器规则，用于将
 * {@link LogicalJoin} relational expression // 逻辑连接（LogicalJoin）关系表达式
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}. // 转换为可枚举调用约定（EnumerableConvention）
 * You may provide a custom config to convert other nodes that extend {@link Join}. // 您可以提供自定义配置来转换其他继承自Join的节点
 *
 * @see EnumerableRules#ENUMERABLE_JOIN_RULE */ // 参见EnumerableRules中的ENUMERABLE_JOIN_RULE常量
class EnumerableJoinRule extends ConverterRule { // EnumerableJoinRule类继承自ConverterRule，用于将逻辑连接转换为可枚举连接
  /** Default configuration. */ // 默认配置
  public static final Config DEFAULT_CONFIG = Config.INSTANCE // 声明并初始化默认配置，使用Config.INSTANCE作为基础
      .withConversion(LogicalJoin.class, Convention.NONE, // 配置转换规则：将LogicalJoin类从无约定（Convention.NONE）
          EnumerableConvention.INSTANCE, "EnumerableJoinRule") // 转换为可枚举约定（EnumerableConvention.INSTANCE），规则名称为"EnumerableJoinRule"
      .withRuleFactory(EnumerableJoinRule::new); // 设置规则工厂，使用EnumerableJoinRule的构造函数引用

  /** Called from the Config. */ // 从配置中调用
  protected EnumerableJoinRule(Config config) { // 受保护的构造方法，接收Config对象作为参数
    super(config); // 调用父类ConverterRule的构造方法，传入配置对象
  }

  @Override public RelNode convert(RelNode rel) { // 重写convert方法，将RelNode转换为可枚举约定
    Join join = (Join) rel; // 将传入的RelNode强制转换为Join类型
    List<RelNode> newInputs = new ArrayList<>(); // 创建新的输入节点列表，用于存储转换后的输入
    for (RelNode input : join.getInputs()) { // 遍历连接的所有输入节点（通常是左右两个输入）
      if (!(input.getConvention() instanceof EnumerableConvention)) { // 如果输入节点的调用约定不是可枚举约定
        input = // 则需要转换该输入节点
            convert( // 调用convert方法进行转换
                input, // 要转换的输入节点
                input.getTraitSet() // 获取输入节点的特征集合
                    .replace(EnumerableConvention.INSTANCE)); // 将特征集合中的约定替换为可枚举约定
      }
      newInputs.add(input); // 将（可能转换后的）输入节点添加到新输入列表中
    }
    final RexBuilder rexBuilder = join.getCluster().getRexBuilder(); // 获取连接所在集群的RexBuilder，用于构建行表达式
    final RelNode left = newInputs.get(0); // 获取左输入节点（列表第一个元素）
    final RelNode right = newInputs.get(1); // 获取右输入节点（列表第二个元素）
    final JoinInfo info = join.analyzeCondition(); // 分析连接条件，获取JoinInfo对象，包含等值连接键和非等值条件信息

    // If the join has equiKeys (i.e. complete or partial equi-join), // 如果连接有等值键（即完全或部分等值连接）
    // create an EnumerableHashJoin, which supports all types of joins, // 则创建EnumerableHashJoin，它支持所有类型的连接
    // even if the join condition contains partial non-equi sub-conditions; // 即使连接条件包含部分非等值子条件
    // otherwise (complete non-equi-join), create an EnumerableNestedLoopJoin, // 否则（完全非等值连接），创建EnumerableNestedLoopJoin
    // since a hash join strategy in this case would not be beneficial. // 因为在这种情况下哈希连接策略不会有任何优势
    final boolean hasEquiKeys = !info.leftKeys.isEmpty() // 判断是否有等值键：左键列表不为空
        && !info.rightKeys.isEmpty(); // 且右键列表也不为空
    if (hasEquiKeys) { // 如果存在等值键
      // Re-arrange condition: first the equi-join elements, then the non-equi-join ones (if any); // 重排条件：先等值连接元素，后非等值连接元素（如果有）
      // this is not strictly necessary but it will be useful to avoid spurious errors in the // 这不是严格必要的，但有助于避免单元测试中验证计划时
      // unit tests when verifying the plan. // 出现虚假错误
      final RexNode equi = info.getEquiCondition(left, right, rexBuilder); // 构建等值连接条件表达式
      final RexNode condition; // 声明最终连接条件变量
      if (info.isEqui()) { // 如果是完全等值连接（没有非等值条件）
        condition = equi; // 则最终条件就是等值条件
      } else { // 如果是部分等值连接（还有非等值条件）
        final RexNode nonEqui = RexUtil.composeConjunction(rexBuilder, info.nonEquiConditions); // 使用RexUtil将所有非等值条件组合成一个AND表达式
        condition = RexUtil.composeConjunction(rexBuilder, Arrays.asList(equi, nonEqui)); // 将等值条件和非等值条件组合成最终的AND连接条件
      }
      return EnumerableHashJoin.create( // 创建并返回哈希连接节点
          left, // 左输入节点
          right, // 右输入节点
          condition, // 连接条件
          join.getVariablesSet(), // 变量集合（用于半连接等特殊连接类型）
          join.getJoinType()); // 连接类型（INNER、LEFT、RIGHT等）
    }
    return EnumerableNestedLoopJoin.create( // 如果没有等值键，创建并返回嵌套循环连接节点
        left, // 左输入节点
        right, // 右输入节点
        join.getCondition(), // 原始连接条件
        join.getVariablesSet(), // 变量集合
        join.getJoinType()); // 连接类型
  }
}
