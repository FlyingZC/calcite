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
// 声明包名，表示这个类属于org.apache.calcite.plan包，该包包含与查询优化器规划相关的类
package org.apache.calcite.plan;

// 导入RelNode类，代表关系代数节点，是Calcite中所有关系表达式的基础接口
import org.apache.calcite.rel.RelNode;
// 导入RelVisitor类，提供访问RelNode树结构的访问者模式基类
import org.apache.calcite.rel.RelVisitor;
// 导入Util工具类，提供各种实用方法
import org.apache.calcite.util.Util;

// 导入Nullable注解，用于标记可能为null的参数或返回值，来自CheckerFramework空值检查框架
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * RelTraitPropagationVisitor是一个关系特性传播访问者类，继承自RelVisitor
 * 它的主要功能是遍历RelNode及其<i>未注册的</i>子节点，确保每个RelNode都拥有完整的特性集（traits）
 * 当发现某个RelNode缺少一个或多个特性时，会从构造时传入的RelTraitSet中复制相应的特性
 *
 * 类的核心作用：在关系表达式树中传播特性，确保所有未注册的RelNode都具有完整的特性集
 *
 * 理解Calcite中的RelTrait（关系特性）概念：
 * RelTrait是RelNode的一个属性集合，描述了关系表达式的各种特性和约束，例如：
 * - Convention：约定，表示数据存储和计算的物理实现方式（如LOGICAL、PHYSICAL等）
 * - RelCollation：排序规则，描述数据的排序方式
 * - RelDistribution：分布规则，描述数据的分布方式（如SINGLE、HASH、RANGE等）
 * - 其他自定义特性
 * RelTraitSet是RelTrait的集合，一个RelNode必须有一个完整的RelTraitSet才能被优化器正确处理
 *
 * @deprecated 从1.19版本开始弃用，如果你需要对RelNode树及其包含的特性进行某些断言检查，
 * 建议直接实现自己的RelVisitor或{@link org.apache.calcite.rel.RelShuttle}
 * 弃用此类的原因如下：
 * <ul>
 *   <li>类的契约（Javadoc和命名）与实际行为不一致</li>
 *   <li>框架的其他组件不再使用此类</li>
 *   <li>此类仅用于调试目的</li>
 * </ul>
 *
 */
// 使用@Deprecated注解标记此类已过时，编译器会发出警告
@Deprecated
// 定义RelTraitPropagationVisitor类，继承自RelVisitor，使用访问者模式遍历RelNode树
public class RelTraitPropagationVisitor extends RelVisitor {
  //~ Instance fields --------------------------------------------------------
  // 成员变量分隔符注释

  // baseTraits是一个不可变的RelTraitSet，存储基准特性集
  // 这个特性集用于向缺少特性的RelNode补充特性
  // final修饰符表示一旦初始化就不能被修改，确保特性集的稳定性
  private final RelTraitSet baseTraits;
  // planner是一个不可变的RelOptPlanner引用，代表关系优化器
  // 用于检查RelNode是否已在优化器中注册
  // final修饰符表示一旦初始化就不能被修改
  private final RelOptPlanner planner;

  //~ Constructors -----------------------------------------------------------
  // 构造方法分隔符注释

  // 构造方法：创建RelTraitPropagationVisitor实例
  // 参数说明：
  //   - planner: RelOptPlanner优化器实例，用于检查RelNode的注册状态
  //   - baseTraits: RelTraitSet基准特性集，用于向RelNode补充缺失的特性
  public RelTraitPropagationVisitor(
      RelOptPlanner planner,
      RelTraitSet baseTraits) {
    // 将传入的planner参数赋值给实例变量this.planner
    this.planner = planner;
    // 将传入的baseTraits参数赋值给实例变量this.baseTraits
    this.baseTraits = baseTraits;
  }

  //~ Methods ----------------------------------------------------------------
  // 方法分隔符注释

  // 重写visit方法，这是RelVisitor的核心方法，在访问每个RelNode时被调用
  // 参数说明：
  //   - rel: 当前访问的RelNode节点
  //   - ordinal: 当前节点在父节点中的序号（从0开始）
  //   - parent: 当前节点的父RelNode，可能为null（@Nullable注解表示允许为null）
  @Override public void visit(RelNode rel, int ordinal, @Nullable RelNode parent) {
    // REVIEW: SWZ: 1/31/06: 我们假设任何特殊的RelNode（如VolcanoPlanner的RelSubset）
    // 总是拥有完整的特性集，并且它们要么作为已注册节点出现，要么在调用childrenAccept时不做任何操作
    // 这段注释说明了特殊RelNode的处理假设

    // 检查当前RelNode是否已在优化器中注册
    // 如果已注册，则直接返回，不需要处理（因为已注册的节点应该已经有完整的特性集）
    if (planner.isRegistered(rel)) {
      // 直接返回，跳过已注册的RelNode
      return;
    }

    // 获取当前RelNode的特性集（RelTraitSet）
    // RelTraitSet包含该RelNode的所有特性（如约定、排序、分布等）
    RelTraitSet relTraits = rel.getTraitSet();
    // 遍历基准特性集中的每一个特性
    // 目的是检查当前RelNode是否拥有所有必需的特性
    for (int i = 0; i < baseTraits.size(); i++) {
      // 如果当前RelNode的特性集大小小于基准特性集的当前索引
      // 说明当前RelNode缺少某些特性（特性数量不足）
      if (i >= relTraits.size()) {
        // 复制新RelNode不知道的特性
        // 使用RelOptUtil.addTrait方法向RelNode添加缺失的特性
        // baseTraits.getTrait(i)获取基准特性集中第i个位置的特性
        Util.discard(
            RelOptUtil.addTrait(
                rel,
                baseTraits.getTrait(i)));

        // FIXME: 返回新的RelNode。由于rels和traits现在都是不可变的，
        // 我们不能再原地修改特性
        // 这里抛出AssertionError表示这个实现有问题，因为不可变对象不能原地修改
        // 这也解释了为什么这个类被弃用
        throw new AssertionError();
      } else {
        // 如果当前RelNode有对应位置的特性，则验证这些特性是否来自相同的RelTraitDef
        // RelTraitDef是特性定义，每个特性类型都有一个对应的RelTraitDef
        // 例如：ConventionTraitDef定义了Convention特性
        // 这个断言确保两个特性集在相同位置的特性类型是一致的
        assert relTraits.getTrait(i).getTraitDef()
            == baseTraits.getTrait(i).getTraitDef();
      }
    }

    // 递归访问当前RelNode的所有子节点
    // childrenAccept方法会为每个子节点调用visit方法
    // 这样可以遍历整个RelNode树，确保所有未注册的节点都有完整的特性集
    rel.childrenAccept(this);
  }
}
