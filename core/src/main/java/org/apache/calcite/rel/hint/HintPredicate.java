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
package org.apache.calcite.rel.hint; // 定义包名,属于calcite框架的rel.hint包,处理关系代数表达式中的提示(hint)功能

import org.apache.calcite.rel.RelNode; // 导入RelNode类,表示关系代数表达式节点,是Calcite中所有关系操作符的基类

/**
 * A {@code HintPredicate} indicates whether a {@link org.apache.calcite.rel.RelNode}
 * can apply the specified hint. // HintPredicate接口用于判断给定的关系表达式节点(RelNode)是否可以应用指定的提示(hint)
 *
 * <p>Every supported hint should register a {@code HintPredicate}
 * into the {@link HintStrategyTable}. For example, {@link HintPredicates#JOIN} implies
 * that this hint would be propagated and applied to the {@link org.apache.calcite.rel.core.Join}
 * relational expressions. // 每个支持的提示都应该在HintStrategyTable中注册一个HintPredicate。例如,HintPredicates#JOIN表示该提示会被传播并应用到Join关系表达式上
 *
 * <p>Usually use {@link NodeTypeHintPredicate} is enough for most of the {@link RelHint}s.
 * Some of the hints can only be matched to the relational expression with special
 * match conditions(not only the relational expression type).
 * i.e. "hash_join(r, st)", this hint can only be applied to JOIN expression that
 * has "r" and "st" as the input table names. To implement this, you can make a custom
 * {@code HintPredicate} instance. // 通常使用NodeTypeHintPredicate就足够处理大多数RelHint。但有些提示只能匹配具有特殊匹配条件的关系表达式(不仅仅是关系表达式类型)。例如"hash_join(r, st)"这个提示只能应用到输入表名为"r"和"st"的JOIN表达式上。要实现这个功能,可以创建自定义的HintPredicate实例
 *
 * <p>A {@code HintPredicate} can be used independently or cascaded with other strategies
 * with method {@link HintPredicates#and}. // HintPredicate可以独立使用,也可以通过HintPredicates#and方法与其他策略级联使用
 *
 * <p>In {@link HintStrategyTable} the predicate is used for
 * hints registration. // 在HintStrategyTable中,该谓词用于提示的注册
 *
 * @see HintStrategyTable // 参见HintStrategyTable类,了解提示策略表的详细信息
 */
public interface HintPredicate { // 定义HintPredicate接口,用于判断提示是否可以应用到关系表达式节点上

  /**
   * Decides if the given {@code hint} can be applied to
   * the relational expression {@code rel}. // 判断给定的提示(hint)是否可以应用到关系表达式(rel)上
   *
   * @param hint The hint // 参数hint:要应用的提示对象,包含提示的名称和可选的参数列表
   * @param rel  The relational expression // 参数rel:关系表达式节点,表示SQL查询中的某个操作符(如Join、Filter等)
   * @return True if the {@code hint} can be applied to the {@code rel} // 返回值:如果提示可以应用到关系表达式上则返回true,否则返回false
   */
  boolean apply(RelHint hint, RelNode rel); // 定义apply方法,用于判断提示是否适用于给定的关系表达式节点,这是HintPredicate接口的核心方法
} // 接口定义结束
