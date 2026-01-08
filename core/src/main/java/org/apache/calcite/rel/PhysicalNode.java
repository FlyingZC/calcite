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
package org.apache.calcite.rel; // 声明包名，该接口位于org.apache.calcite.rel包下，是Calcite关系代数框架的核心包

import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系代数节点的调用约定（如Enumerable、JDBC等）
import org.apache.calcite.plan.DeriveMode; // 导入DeriveMode枚举，定义了物理特性推导的模式
import org.apache.calcite.plan.RelOptRule; // 导入RelOptRule类，用于关系代数优化规则的转换
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系代数节点的特性集合（如排序、分布等）
import org.apache.calcite.rel.core.Sort; // 导入Sort类，用于排序操作，作为强制执行器的示例
import org.apache.calcite.util.Pair; // 导入Pair工具类，用于存储键值对

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值

import java.util.ArrayList; // 导入ArrayList类，用于动态数组
import java.util.List; // 导入List接口，用于列表集合

/**
 * PhysicalNode: 物理节点接口，用于在优化器中进行物理特性的传播和推导
 * 
 * 【核心作用】:
 * 1. 定义物理节点的能力：支持自顶向下的特性传递和自底向上的特性推导
 * 2. 实现VolcanoPlanner的top-down优化：通过特性传递和推导机制优化物理计划
 * 3. 提供灵活的特性推导模式：支持LEFT_FIRST、RIGHT_FIRST、OMAKASE等多种模式
 * 
 * 【使用场景】:
 * - 当需要实现自定义的物理优化器时
 * - 当需要控制物理特性的传播和推导逻辑时
 * - 当需要实现top-down优化策略时
 *
 * <p>How to use? 使用步骤详解:
 *
 * <ol>
 * <li>Enable top-down optimization by setting
 * {@link org.apache.calcite.plan.volcano.VolcanoPlanner#setTopDownOpt(boolean)}.
 * 步骤1: 启用自顶向下优化，通过设置VolcanoPlanner的topDownOpt属性为true
 * </li>
 *
 * <li>Let your convention's rel interface extends {@link PhysicalNode},
 * see {@link org.apache.calcite.adapter.enumerable.EnumerableRel} as
 * an example.
 * 步骤2: 让你的调用约定（Convention）的关系接口继承PhysicalNode，参考EnumerableRel的实现
 * </li>
 *
 * <li>Each physical operator overrides any one of the two methods:
 * {@link PhysicalNode#passThrough(RelTraitSet)} or
 * {@link PhysicalNode#passThroughTraits(RelTraitSet)} depending on
 * your needs.
 * 步骤3: 每个物理操作符根据需要重写passThrough或passThroughTraits方法之一，用于特性传递
 * </li>
 *
 * <li>Choose derive mode for each physical operator by overriding
 * {@link PhysicalNode#getDeriveMode()}.
 * 步骤4: 通过重写getDeriveMode方法为每个物理操作符选择特性推导模式
 * </li>
 *
 * <li>If the derive mode is {@link DeriveMode#OMAKASE}, override
 * method {@link PhysicalNode#derive(List)} in the physical operator,
 * otherwise, override {@link PhysicalNode#derive(RelTraitSet, int)}
 * or {@link PhysicalNode#deriveTraits(RelTraitSet, int)}.
 * 步骤5: 如果推导模式是OMAKASE，重写derive方法；否则重写derive或deriveTraits方法
 * </li>
 *
 * <li>Mark your enforcer operator by overriding {@link RelNode#isEnforcer()},
 * see {@link Sort#isEnforcer()} as an example. This is important,
 * because it can help {@code VolcanoPlanner} avoid unnecessary
 * trait propagation and derivation, therefore improve optimization
 * efficiency.
 * 步骤6: 通过重写isEnforcer方法标记强制执行器操作符，这很重要，可以帮助VolcanoPlanner避免不必要的特性传播和推导，从而提高优化效率
 * </li>
 *
 * <li>Implement {@link Convention#enforce(RelNode, RelTraitSet)}
 * in your convention, which generates appropriate physical enforcer.
 * See {@link org.apache.calcite.adapter.enumerable.EnumerableConvention}
 * as example. Simply return {@code null} if you don't want physical
 * trait enforcement.
 * 步骤7: 在你的调用约定中实现enforce方法，用于生成适当的物理强制执行器。参考EnumerableConvention的实现。如果不需要物理特性强制执行，直接返回null
 * </li>
 * </ol>
 */
public interface PhysicalNode extends RelNode { // 定义PhysicalNode接口，继承自RelNode，表示物理节点

  /**
   * passThrough: 将父节点要求的特性集合传递给子节点，返回特性传递后的新节点
   * 
   * 【方法作用】:
   * 1. 执行自顶向下的特性传递：将父节点要求的物理特性向下传播到子节点
   * 2. 转换子节点：根据要求的特性将子节点转换为满足要求的节点
   * 3. 生成新节点：使用转换后的子节点和新的特性集合创建新的物理节点
   * 
   * 【参数说明】:
   * - required: 父节点要求的特性集合（RelTraitSet），包含排序、分布等物理特性
   * 
   * 【返回值说明】:
   * - 返回特性传递后的新节点（RelNode），如果无法传递则返回null
   * 
   * 【实现逻辑】:
   * 1. 调用passThroughTraits获取本节点和子节点的新特性集合
   * 2. 如果返回null，表示无法传递，直接返回null
   * 3. 遍历所有子节点，使用RelOptRule.convert将每个子节点转换为满足要求的特性
   * 4. 使用copy方法创建新的节点，传入新的特性集合和转换后的子节点列表
   * 
   * 【使用场景】:
   * - 在top-down优化过程中，父节点将物理要求传递给子节点
   * - 例如：Sort节点要求输入数据已排序，这个排序要求需要传递给子节点
   */
  default @Nullable RelNode passThrough(RelTraitSet required) { // 默认实现方法，接收父节点要求的特性集合，返回可能为null的新节点
    Pair<RelTraitSet, List<RelTraitSet>> p = passThroughTraits(required); // 调用passThroughTraits方法获取特性传递的Pair对象，包含本节点的新特性和子节点的要求特性列表
    if (p == null) { // 如果返回的Pair对象为null
      return null; // 返回null，表示无法进行特性传递
    } // 结束if判断
    int size = getInputs().size(); // 获取当前节点的子节点数量
    assert size == p.right.size(); // 断言子节点数量与特性列表大小一致，确保数据一致性
    List<RelNode> list = new ArrayList<>(size); // 创建新的RelNode列表，初始容量为子节点数量
    for (int i = 0; i < size; i++) { // 遍历所有子节点
      RelNode n = RelOptRule.convert(getInput(i), p.right.get(i)); // 使用RelOptRule.convert方法将第i个子节点转换为满足p.right.get(i)特性的节点
      list.add(n); // 将转换后的节点添加到列表中
    } // 结束for循环
    return copy(p.left, list); // 调用copy方法，使用新的特性集合p.left和转换后的子节点列表创建新节点并返回
  } // 结束passThrough方法

  /**
   * passThroughTraits: 将父节点要求的特性集合传递给子节点，返回特性传递后的特性对
   * 
   * 【方法作用】:
   * 1. 计算特性传播：确定本节点应该具备的特性以及每个子节点应该满足的特性
   * 2. 提供灵活的实现：子类可以重写此方法来实现自定义的特性传播逻辑
   * 3. 分离计算和转换：将特性计算与节点转换分离，提高代码复用性
   * 
   * 【参数说明】:
   * - required: 父节点要求的特性集合（RelTraitSet）
   * 
   * 【返回值说明】:
   * - Pair.left: 本节点的新特性集合（RelTraitSet），表示本节点在满足父节点要求后应该具备的特性
   * - Pair.right: 子节点的要求特性列表（List<RelTraitSet>），每个元素对应一个子节点应该满足的特性
   * - 返回null表示无法进行特性传递
   * 
   * 【实现逻辑】:
   * - 默认实现抛出异常，要求子类必须实现此方法
   * - 子类需要根据具体的操作符类型和物理特性要求来实现特性传播逻辑
   * 
   * 【使用场景】:
   * - 当需要自定义特性传播逻辑时，子类重写此方法
   * - 例如：Filter操作符可能直接将排序要求传递给子节点
   * - 例如：Join操作符可能将排序要求传递给左子节点或右子节点
   */
  default @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraits( // 默认实现方法，接收父节点要求的特性集合，返回包含本节点特性和子节点特性要求的Pair对象
      RelTraitSet required) { // 参数：父节点要求的特性集合
    throw new RuntimeException(getClass().getName() // 抛出运行时异常，提示子类必须实现此方法
        + "#passThroughTraits() is not implemented."); // 异常信息：方法未实现
  } // 结束passThroughTraits方法

  /**
   * derive: 从子节点推导特性集合，返回特性推导后的新节点
   * 
   * 【方法作用】:
   * 1. 执行自底向上的特性推导：根据子节点的特性推导出本节点应该具备的特性
   * 2. 转换子节点：根据推导出的要求将子节点转换为满足要求的节点
   * 3. 生成新节点：使用转换后的子节点和推导出的特性集合创建新的物理节点
   * 
   * 【参数说明】:
   * - childTraits: 指定子节点的特性集合（RelTraitSet），用于推导本节点的特性
   * - childId: 子节点的索引（int），标识是哪个子节点的特性发生了变化
   * 
   * 【返回值说明】:
   * - 返回特性推导后的新节点（RelNode），如果无法推导则返回null
   * 
   * 【实现逻辑】:
   * 1. 调用deriveTraits获取本节点和子节点的新特性集合
   * 2. 如果返回null，表示无法推导，直接返回null
   * 3. 遍历所有子节点，使用RelOptRule.convert将每个子节点转换为满足要求的特性
   * 4. 使用copy方法创建新的节点，传入推导出的特性集合和转换后的子节点列表
   * 
   * 【使用场景】:
   * - 在bottom-up优化过程中，子节点的特性变化向上传播到父节点
   * - 例如：子节点产生了排序后的数据，父节点可以利用这个排序特性
   */
  default @Nullable RelNode derive(RelTraitSet childTraits, int childId) { // 默认实现方法，接收子节点特性和子节点索引，返回可能为null的新节点
    Pair<RelTraitSet, List<RelTraitSet>> p = deriveTraits(childTraits, childId); // 调用deriveTraits方法获取特性推导的Pair对象，包含本节点的新特性和子节点的要求特性列表
    if (p == null) { // 如果返回的Pair对象为null
      return null; // 返回null，表示无法进行特性推导
    } // 结束if判断
    int size = getInputs().size(); // 获取当前节点的子节点数量
    assert size == p.right.size(); // 断言子节点数量与特性列表大小一致，确保数据一致性
    List<RelNode> list = new ArrayList<>(size); // 创建新的RelNode列表，初始容量为子节点数量
    for (int i = 0; i < size; i++) { // 遍历所有子节点
      RelNode node = getInput(i); // 获取第i个子节点
      node = RelOptRule.convert(node, p.right.get(i)); // 使用RelOptRule.convert方法将该子节点转换为满足p.right.get(i)特性的节点
      list.add(node); // 将转换后的节点添加到列表中
    } // 结束for循环
    return copy(p.left, list); // 调用copy方法，使用推导出的特性集合p.left和转换后的子节点列表创建新节点并返回
  } // 结束derive方法

  /**
   * deriveTraits: 从子节点推导特性集合，返回特性推导后的特性对
   * 
   * 【方法作用】:
   * 1. 计算特性推导：根据子节点的特性推导出本节点应该具备的特性
   * 2. 确定子节点要求：推导出每个子节点应该满足的特性要求
   * 3. 提供灵活的实现：子类可以重写此方法来实现自定义的特性推导逻辑
   * 
   * 【参数说明】:
   * - childTraits: 指定子节点的特性集合（RelTraitSet）
   * - childId: 子节点的索引（int），标识是哪个子节点的特性发生了变化
   * 
   * 【返回值说明】:
   * - Pair.left: 推导出的本节点特性集合（RelTraitSet），表示本节点基于子节点特性应该具备的特性
   * - Pair.right: 子节点的要求特性列表（List<RelTraitSet>），每个元素对应一个子节点应该满足的特性
   * - 返回null表示无法进行特性推导
   * 
   * 【实现逻辑】:
   * - 默认实现抛出异常，要求子类必须实现此方法
   * - 子类需要根据具体的操作符类型和物理特性推导规则来实现特性推导逻辑
   * 
   * 【使用场景】:
   * - 当需要自定义特性推导逻辑时，子类重写此方法
   * - 例如：MergeJoin操作符可以从已排序的子节点推导出本节点的排序特性
   * - 例如：Aggregate操作符可以从子节点的分布特性推导出本节点的分布特性
   */
  default @Nullable Pair<RelTraitSet, List<RelTraitSet>> deriveTraits( // 默认实现方法，接收子节点特性和子节点索引，返回包含本节点特性和子节点特性要求的Pair对象
      RelTraitSet childTraits, int childId) { // 参数：子节点特性和子节点索引
    throw new RuntimeException(getClass().getName() // 抛出运行时异常，提示子类必须实现此方法
        + "#deriveTraits() is not implemented."); // 异常信息：方法未实现
  } // 结束deriveTraits方法

  /**
   * derive: 根据所有子节点的特性集合进行特性推导，返回特性推导后的节点列表
   * 
   * 【方法作用】:
   * 1. 批量特性推导：同时考虑所有子节点的特性，进行综合的特性推导
   * 2. OMAKASE模式专用：此方法仅在推导模式为OMAKASE时被调用
   * 3. 灵活的推导策略：OMAKASE模式允许操作符自由决定如何推导特性
   * 
   * 【参数说明】:
   * - inputTraits: 子节点特性集合的列表（List<List<RelTraitSet>>），外层列表对应子节点，内层列表对应每个子节点的可能特性
   * - 约束条件：inputTraits.size() == getInput().size()，确保每个子节点都有对应的特性集合
   * 
   * 【返回值说明】:
   * - 返回特性推导后的节点列表（List<RelNode>），每个元素对应一个子节点
   * 
   * 【实现逻辑】:
   * - 默认实现抛出异常，要求子类必须实现此方法（如果使用OMAKASE模式）
   * - 子类需要根据所有子节点的特性集合，实现自定义的综合特性推导逻辑
   * 
   * 【使用场景】:
   * - 当操作符需要同时考虑所有子节点的特性时，使用OMAKASE模式
   * - 例如：HashJoin操作符可能需要根据左右子节点的分布特性来决定本节点的分布特性
   * - 例如：Union操作符可能需要根据所有子节点的排序特性来决定本节点的排序特性
   */
  default List<RelNode> derive(List<List<RelTraitSet>> inputTraits) { // 默认实现方法，接收所有子节点的特性集合列表，返回特性推导后的节点列表
    throw new RuntimeException(getClass().getName() // 抛出运行时异常，提示子类必须实现此方法
        + "#derive() is not implemented."); // 异常信息：方法未实现
  } // 结束derive方法

  /**
   * getDeriveMode: 返回特性推导的模式
   * 
   * 【方法作用】:
   * 1. 定义推导策略：决定特性推导的顺序和方式
   * 2. 优化性能：不同的推导模式会影响优化器的性能和优化质量
   * 3. 控制推导行为：通过模式选择来控制特性推导的行为
   * 
   * 【返回值说明】:
   * - 返回DeriveMode枚举值，表示特性推导的模式
   * - 默认返回LEFT_FIRST，表示先从左子节点开始推导
   * 
   * 【DeriveMode枚举值说明】:
   * - LEFT_FIRST: 先从左子节点开始推导，然后推导右子节点
   * - RIGHT_FIRST: 先从右子节点开始推导，然后推导左子节点
   * - OMAKASE: 自由模式，操作符可以自定义推导逻辑，需要重写derive(List)方法
   * 
   * 【使用场景】:
   * - 对于左深树（left-deep tree），使用LEFT_FIRST可以提高性能
   * - 对于右深树（right-deep tree），使用RIGHT_FIRST可以提高性能
   * - 对于复杂的操作符，使用OMAKASE模式可以实现自定义的推导逻辑
   */
  default DeriveMode getDeriveMode() { // 默认实现方法，无参数，返回DeriveMode枚举值
    return DeriveMode.LEFT_FIRST; // 返回LEFT_FIRST模式，表示先从左子节点开始推导
  } // 结束getDeriveMode方法
} // 结束PhysicalNode接口定义
