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
package org.apache.calcite.rel.logical;  // 包声明：LogicalIntersect类位于org.apache.calcite.rel.logical包下，该包包含了逻辑关系表达式

import org.apache.calcite.plan.Convention;  // 导入Convention接口，用于定义关系表达式的调用约定（如物理实现方式）
import org.apache.calcite.plan.RelOptCluster;  // 导入RelOptCluster类，表示关系优化集群，包含优化器的上下文信息
import org.apache.calcite.plan.RelTraitSet;  // 导入RelTraitSet类，表示关系表达式的特征集合（如物理实现特征）
import org.apache.calcite.rel.RelInput;  // 导入RelInput接口，用于从序列化输出中解析关系表达式
import org.apache.calcite.rel.RelNode;  // 导入RelNode接口，表示关系表达式节点，是Calcite中所有关系操作的基类
import org.apache.calcite.rel.RelShuttle;  // 导入RelShuttle接口，用于遍历和修改关系表达式树
import org.apache.calcite.rel.core.Intersect;  // 导入Intersect类，LogicalIntersect的父类，实现了交集操作的通用逻辑
import org.apache.calcite.rel.hint.RelHint;  // 导入RelHint接口，用于存储关系表达式的提示信息（如优化提示）

import java.util.Collections;  // 导入Collections工具类，用于创建不可修改的空集合
import java.util.List;  // 导入List接口，用于存储关系表达式列表

/**
 * Sub-class of {@link org.apache.calcite.rel.core.Intersect}  // LogicalIntersect是Intersect的子类
 * not targeted at any particular engine or calling convention.  // 不针对任何特定的引擎或调用约定，表示这是一个逻辑层面的交集操作
 * 
 * 类作用说明：
 * LogicalIntersect表示SQL中INTERSECT操作（集合交集）的逻辑关系表达式。
 * 它继承自Intersect类，实现了多个关系代数集合的交集运算。
 * 
 * SQL示例：
 * SELECT * FROM table1 INTERSECT SELECT * FROM table2;
 * 
 * 核心功能：
 * 1. 计算多个输入关系表达式的交集结果
 * 2. 支持ALL关键字（INTERSECT ALL）保留重复行
 * 3. 作为逻辑算子，不涉及具体的物理实现细节
 * 4. 可以通过优化器转换为物理实现（如EnumerableIntersect）
 * 
 * 重要特性：
 * - all参数控制是否保留重复行：true表示保留（INTERSECT ALL），false表示去重（INTERSECT）
 * - 输入的所有关系表达式必须有相同的行类型（字段数量和类型）
 * - 结果的行类型与输入的行类型相同
 * - 交集操作会去除重复行（除非all=true）
 */
public final class LogicalIntersect extends Intersect {  // 声明LogicalIntersect类，继承自Intersect，final表示不可被继承
  //~ Constructors -----------------------------------------------------------  // 构造方法区域的分隔符

  /**
   * Creates a LogicalIntersect.  // 创建LogicalIntersect对象
   *
   * <p>Use {@link #create} unless you know what you're doing.  // 除非你清楚自己在做什么，否则建议使用create工厂方法
   * 
   * 构造方法说明：
   * 这是LogicalIntersect的主要构造方法，用于创建逻辑交集操作节点。
   * 
   * 参数说明：
   * - cluster: 关系优化集群，包含优化器的上下文信息（如类型系统、表达式工厂等）
   * - traitSet: 关系表达式的特征集合，定义了物理实现特征（如约定、排序等）
   * - hints: 提示列表，用于向优化器提供优化建议
   * - inputs: 输入关系表达式列表，所有输入必须有相同的行类型，至少包含2个输入
   * - all: 是否保留重复行的标志，true表示保留重复（INTERSECT ALL），false表示去重（INTERSECT）
   * 
   * 使用场景：
   * 优化器在构建查询计划树时会调用此构造方法创建LogicalIntersect节点。
   */
  public LogicalIntersect(  // 构造方法声明：创建LogicalIntersect实例
      RelOptCluster cluster,  // 参数：关系优化集群对象，提供优化器上下文信息
      RelTraitSet traitSet,  // 参数：关系特征集合，定义物理实现特征
      List<RelHint> hints,  // 参数：提示列表，包含优化提示信息
      List<RelNode> inputs,  // 参数：输入关系表达式列表，至少包含2个输入
      boolean all) {  // 参数：是否保留重复行的标志
    super(cluster, traitSet, hints, inputs, all);  // 调用父类Intersect的构造方法，初始化基类成员
  }

  /**
   * Creates a LogicalIntersect.  // 创建LogicalIntersect对象
   *
   * <p>Use {@link #create} unless you know what you're doing.  // 除非你清楚自己在做什么，否则建议使用create工厂方法
   * 
   * 构造方法说明：
   * 这是一个简化版的构造方法，不提供hints参数，内部使用空列表。
   * 
   * 参数说明：
   * - cluster: 关系优化集群，包含优化器的上下文信息
   * - traitSet: 关系表达式的特征集合
   * - inputs: 输入关系表达式列表
   * - all: 是否保留重复行的标志
   * 
   * 使用场景：
   * 当不需要提供优化提示时使用此构造方法。
   */
  public LogicalIntersect(  // 构造方法声明：创建LogicalIntersect实例（无提示版本）
      RelOptCluster cluster,  // 参数：关系优化集群对象
      RelTraitSet traitSet,  // 参数：关系特征集合
      List<RelNode> inputs,  // 参数：输入关系表达式列表
      boolean all) {  // 参数：是否保留重复行的标志
    this(cluster, traitSet, Collections.emptyList(), inputs, all);  // 调用主构造方法，使用空列表作为hints参数
  }

  @Deprecated // to be removed before 2.0  // 标记为已过时，将在2.0版本前移除
  public LogicalIntersect(RelOptCluster cluster, List<RelNode> inputs,  // 构造方法声明：已过时的构造方法
      boolean all) {  // 参数：是否保留重复行的标志
    this(cluster, cluster.traitSetOf(Convention.NONE), inputs, all);  // 调用简化版构造方法，使用Convention.NONE作为特征集
  }


  /** Creates a LogicalIntersect by parsing serialized output.  // 通过解析序列化输出创建LogicalIntersect
   * 
   * 构造方法说明：
   * 这个构造方法用于从序列化格式（如JSON）中恢复LogicalIntersect对象。
   * 
   * 参数说明：
   * - input: 关系输入对象，包含序列化的关系表达式信息
   * 
   * 使用场景：
   * 当从持久化存储或网络传输中恢复查询计划时使用。
   */
  public LogicalIntersect(RelInput input) {  // 构造方法声明：从序列化输入创建实例
    super(input);  // 调用父类Intersect的构造方法，从RelInput对象中恢复状态
  }

  /** Creates a LogicalIntersect.  // 创建LogicalIntersect对象
   * 
   * 工厂方法说明：
   * 这是推荐的创建LogicalIntersect实例的方式，它会自动设置正确的特征集。
   * 
   * 参数说明：
   * - inputs: 输入关系表达式列表，至少包含2个输入
   * - all: 是否保留重复行的标志
   * 
   * 返回值：
   * 返回新创建的LogicalIntersect实例
   * 
   * 使用场景：
   * 在构建查询计划时，应该优先使用此工厂方法而不是直接调用构造方法。
   * 
   * 实现细节：
   * - 从第一个输入获取cluster对象（所有输入共享同一个cluster）
   * - 创建Convention.NONE特征集（表示逻辑约定）
   * - 调用构造方法创建实例
   */
  public static LogicalIntersect create(List<RelNode> inputs, boolean all) {  // 工厂方法声明：创建LogicalIntersect实例
    final RelOptCluster cluster = inputs.get(0).getCluster();  // 从第一个输入获取cluster对象（所有输入应该属于同一个cluster）
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE);  // 创建Convention.NONE特征集，表示这是一个逻辑关系表达式
    return new LogicalIntersect(cluster, traitSet, inputs, all);  // 调用构造方法创建并返回LogicalIntersect实例
  }

  //~ Methods ----------------------------------------------------------------  // 方法区域的分隔符

  /**
   * 复制方法说明：
   * 创建当前LogicalIntersect节点的副本，可以修改特征集、输入和all参数。
   * 
   * 参数说明：
   * - traitSet: 新的特征集合
   * - inputs: 新的输入关系表达式列表
   * - all: 新的all标志值
   * 
   * 返回值：
   * 返回新的LogicalIntersect实例，包含指定的特征集、输入和all参数
   * 
   * 使用场景：
   * 优化器在应用优化规则时，需要创建修改后的关系表达式副本。
   * 
   * 实现细节：
   * - 保留原有的cluster和hints
   * - 使用新的traitSet、inputs和all参数
   */
  @Override public LogicalIntersect copy(RelTraitSet traitSet,  // 重写copy方法：创建节点副本
      List<RelNode> inputs, boolean all) {  // 参数：新的特征集、输入列表和all标志
    return new LogicalIntersect(getCluster(), traitSet, hints, inputs, all);  // 返回新的LogicalIntersect实例，使用当前cluster和hints
  }

  /**
   * 访问者模式方法说明：
   * 接受一个RelShuttle访问者，让访问者遍历或修改这个关系表达式树。
   * 
   * 参数说明：
   * - shuttle: 关系表达式访问者对象
   * 
   * 返回值：
   * 返回访问者处理后的关系表达式（可能是修改后的）
   * 
   * 使用场景：
   * 当需要遍历或修改关系表达式树时使用，例如：
   * - 收集统计信息
   * - 应用优化规则
   * - 转换关系表达式
   * 
   * 实现细节：
   * - 将当前节点传递给访问者的visit方法
   * - 访问者会决定如何处理这个节点（可能是返回原节点或创建新节点）
   */
  @Override public RelNode accept(RelShuttle shuttle) {  // 重写accept方法：接受访问者
    return shuttle.visit(this);  // 调用访问者的visit方法，传入当前LogicalIntersect实例
  }

  /**
   * 设置提示方法说明：
   * 创建一个新的LogicalIntersect实例，使用新的提示列表。
   * 
   * 参数说明：
   * - hintList: 新的提示列表
   * 
   * 返回值：
   * 返回新的LogicalIntersect实例，包含指定的提示列表
   * 
   * 使用场景：
   * 当需要添加或修改优化提示时使用，例如：
   * - 指定使用特定的物理实现
   * - 提供优化器提示
   * 
   * 实现细节：
   * - 保留原有的cluster、traitSet、inputs和all参数
   * - 使用新的hintList创建新实例
   * - 返回新实例，保持原实例不变（不可变对象模式）
   */
  @Override public RelNode withHints(List<RelHint> hintList) {  // 重写withHints方法：设置提示列表
    return new LogicalIntersect(getCluster(), traitSet, hintList, inputs, all);  // 返回新的LogicalIntersect实例，使用新的提示列表
  }
}
