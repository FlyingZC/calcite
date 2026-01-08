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
// Apache许可证声明，规定了代码的使用权限和限制
package org.apache.calcite.rel.hint; // 声明当前类所在的包，位于rel.hint子包中，rel表示关系代数相关，hint表示提示相关功能

import org.apache.calcite.linq4j.function.Experimental; // 导入实验性功能注解，用于标记不稳定的API
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，是Calcite中所有关系表达式的基类

import com.google.common.collect.ImmutableList; // 导入Google Guava库的不可变列表类，用于存储提示列表

import java.util.ArrayList; // 导入Java标准库的动态数组类，用于存储可变的提示列表
import java.util.LinkedHashSet; // 导入Java标准库的链式哈希集合类，用于去重同时保持插入顺序
import java.util.List; // 导入Java标准库的列表接口
import java.util.Set; // 导入Java标准库的集合接口

import static java.util.Objects.requireNonNull; // 静态导入对象非空检查方法，用于参数验证

/**
 * {@link Hintable} 是一种可以附加 {@link RelHint}（关系提示）的 {@link RelNode}（关系节点）接口
 * Hintable接口定义了关系表达式支持提示（Hint）功能的能力，提示是一种机制，允许用户通过SQL注释或特殊语法
 * 来影响查询优化器的行为，例如指定使用特定的索引、调整连接顺序、控制并行度等
 *
 * <p>This interface is experimental, {@link RelNode}s that implement it
 * have a constructor parameter named "hints" used to construct relational expression
 * with given hints.
 * 这个接口是实验性的，实现此接口的RelNode必须有一个名为"hints"的构造函数参数，
 * 用于在构造关系表达式时传入提示列表，这样创建的关系表达式就携带了用户指定的提示信息
 *
 * <p>Current design is not that elegant and mature, because we have to
 * copy the hints whenever these relational expressions are copied or used to
 * derive new relational expressions.
 * 当前设计还不够优雅和成熟，因为每当这些关系表达式被复制或用于派生新的关系表达式时，
 * 我们都必须复制提示信息，这增加了实现的复杂度和内存开销
 * Even though we have implemented the mechanism to propagate the hints, for large queries,
 * there would be many cases where the hints are not copied to the right RelNode,
 * and the effort/memory is wasted if we are copying the hint to a RelNode
 * but the hint is not used.
 * 尽管我们已经实现了提示传播的机制，但对于大型查询，仍会有很多情况下提示没有被复制到正确的RelNode，
 * 而且如果我们将提示复制到一个RelNode但该提示未被使用，那么这种努力和内存就被浪费了
 */
@Experimental // 使用Experimental注解标记此接口为实验性功能，表示API可能在未来版本中发生变化
public interface Hintable { // 声明Hintable接口，所有支持提示功能的关系节点都需要实现此接口

  /**
   * Attaches list of hints to this relational expression.
   * 将提示列表附加到此关系表达式上，此方法用于在关系表达式上添加用户指定的提示信息
   *
   * <p>This method is only for internal use during sql-to-rel conversion.
   * 此方法仅供内部使用，在SQL到关系表达式（sql-to-rel）的转换过程中调用
   * SQL解析器解析到提示后，会通过此方法将提示附加到对应的关系表达式上
   *
   * <p>Sub-class should return a new copy of the relational expression.
   * 子类应该返回关系表达式的新副本，遵循不可变对象的设计原则
   * Calcite中的关系表达式通常是不可变的，修改属性时需要返回新的对象
   *
   * <p>The default implementation merges the given hints with existing ones,
   * put them in one list and eliminate the duplicates; then
   * returns a new copy of this relational expression with the merged hints.
   * 默认实现将给定的提示与现有提示合并，将它们放在一个列表中并消除重复项（使用LinkedHashSet去重）；
   * 然后返回带有合并后提示的此关系表达式的新副本
   * 合并过程确保了提示的唯一性，同时保留了插入顺序
   *
   * @param hintList The hints to attach to this relational expression
   * hintList参数是要附加到此关系表达式的提示列表，不能为null
   * @return Relational expression with the hints {@code hintList} attached
   * 返回值是附加了指定提示的关系表达式，是一个新的RelNode对象
   */
  default RelNode attachHints(List<RelHint> hintList) { // 默认方法实现，将提示列表附加到当前关系表达式
    final Set<RelHint> hints = new LinkedHashSet<>(getHints()); // 创建一个LinkedHashSet集合，初始化为当前关系表达式的所有提示，使用LinkedHashSet是为了在去重的同时保持插入顺序
    hints.addAll(requireNonNull(hintList, "hintList")); // 将新的提示列表添加到集合中，requireNonNull方法确保hintList不为null，否则抛出NullPointerException
    return withHints(new ArrayList<>(hints)); // 调用withHints方法，传入转换后的ArrayList，返回带有合并后提示的新关系表达式副本
  }

  /**
   * Returns a new relational expression with the specified hints {@code hintList}.
   * 返回一个带有指定提示列表hintList的新关系表达式
   *
   * <p>This method should be overridden by every logical node that supports hint.
   * 每个支持提示的逻辑节点都应该重写此方法，以正确处理提示的设置
   * 逻辑节点指的是在逻辑计划阶段的RelNode，如LogicalTableScan、LogicalJoin等
   * It is only for internal use during decorrelation.
   * 此方法仅供内部使用，在去相关化（decorrelation）过程中调用
   * 去相关化是查询优化中的一个重要步骤，用于处理子查询和相关子查询
   *
   * <p>Sub-class should return a new copy of the relational expression.
   * 子类应该返回关系表达式的新副本，遵循不可变对象的设计原则
   *
   * <p>The default implementation returns the relational expression directly
   * only because not every kind of relational expression supports hints.
   * 默认实现直接返回关系表达式本身，这仅仅是因为并非所有类型的关系表达式都支持提示
   * 如果子类不支持提示，可以继承此默认实现
   *
   * @return Relational expression with set up hints
   * 返回值是设置了提示的关系表达式，默认实现返回this（即不改变）
   */
  default RelNode withHints(List<RelHint> hintList) { // 默认方法实现，设置关系表达式的提示列表
    return (RelNode) this; // 默认实现直接返回当前对象，不做任何修改，子类应该重写此方法以返回带有新提示的新对象
  }

  /**
   * Returns the hints of this relational expressions as an immutable list.
   * 以不可变列表的形式返回此关系表达式的所有提示
   * 返回的ImmutableList确保了提示列表不会被外部修改，保证了数据的安全性
   * 调用者可以安全地遍历和读取提示，但不能修改列表内容
   */
  ImmutableList<RelHint> getHints(); // 抽象方法，要求实现类返回关系表达式的提示列表，返回类型是ImmutableList<RelHint>，表示不可变的RelHint列表
} // 接口定义结束
