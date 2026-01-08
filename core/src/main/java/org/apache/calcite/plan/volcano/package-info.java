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

/**
 * Optimizes relational expressions. // 优化关系表达式，这是整个火山优化器包的核心功能
 *
 * <h2>Overview</h2> // 概览部分，介绍火山优化器的基本概念和架构
 *
 * <p>A <dfn>planner</dfn> (also known as an <dfn>optimizer</dfn>) finds the // 规划器（也称为优化器）用于查找
 * most efficient implementation of a // 关系表达式最高效的实现方式
 * {@link org.apache.calcite.rel.RelNode relational expression}. // 关系表达式是Calcite中表示SQL查询的基本数据结构
 *
 * <p>Interface {@link org.apache.calcite.plan.RelOptPlanner} defines a planner, // RelOptPlanner接口定义了规划器的标准行为
 * and class {@link org.apache.calcite.plan.volcano.VolcanoPlanner} is an // VolcanoPlanner类是该接口的具体实现
 * implementation which uses a dynamic programming technique. It is based upon // 使用动态规划技术，基于著名的Volcano优化器论文
 * the Volcano optimizer [<a href="#graefe93">1</a>]. // 引用了Graefe 1993年的经典论文
 *
 * <p>Interface {@link org.apache.calcite.plan.RelOptCost} defines a cost // RelOptCost接口定义了成本模型，用于评估不同执行计划的优劣
 * model; class {@link org.apache.calcite.plan.volcano.VolcanoCost} is // VolcanoCost类是VolcanoPlanner使用的具体成本实现
 * the implementation for a <code>VolcanoPlanner</code>. // 成本模型考虑CPU、I/O和内存等资源消耗
 *
 * <p>A {@link org.apache.calcite.plan.volcano.RelSet} is a set of equivalent // RelSet是一个等价关系表达式集合
 * relational expressions.  They are equivalent because they will produce the // 它们等价是因为对于任何输入数据集都产生相同的结果
 * same result for any set of input data. It is an equivalence class: two // 它是一个等价类：两个表达式在同一个集合中当且仅当它们在同一个RelSet中
 * expressions are in the same set if and only if they are in the same // 这是优化器进行等价变换的基础
 * <code>RelSet</code>. // RelSet是优化器空间搜索的核心数据结构
 *
 * <p>One of the unique features of the optimizer is that expressions can take // 优化器的独特特性之一是表达式可以具有多种物理特征
 * on a variety of physical traits. Each relational expression has a set of // 每个关系表达式都有一组特征
 * traits. Each trait is described by an implementation of // 每个特征由RelTraitDef的实现来描述
 * {@link org.apache.calcite.plan.RelTraitDef}.  Manifestations of the trait // 特征的具体实现继承自RelTrait接口
 * implement {@link org.apache.calcite.plan.RelTrait}. The most common example // 最常见的特征示例是调用约定
 * of a trait is calling convention: the protocol used to receive and transmit // 调用约定是接收和传输数据的协议
 * data. {@link org.apache.calcite.plan.ConventionTraitDef} defines the trait // ConventionTraitDef定义了该特征
 * and {@link org.apache.calcite.plan.Convention} enumerates the // Convention枚举了各种协议类型
 * protocols. Every relational expression has a single calling convention by // 每个关系表达式都有一个单一的调用约定来返回其结果
 * which it returns its results. Some examples: // 以下是几种常见的调用约定示例：
 *
 * <ul>
 *     <li>{@link org.apache.calcite.adapter.jdbc.JdbcConvention} is a fairly // JdbcConvention是一个相当标准的约定
 *         conventional convention; the results are rows from a // 结果来自JDBC结果集的行
 *         {@link java.sql.ResultSet JDBC result set}. // 适用于将查询下推到关系型数据库执行
 *     </li>
 *     <li>{@link org.apache.calcite.plan.Convention#NONE} means that a // Convention.NONE表示关系表达式无法直接实现
 *         relational // 通常需要通过规则将其转换为可实现的等价表达式
 *         expression cannot be implemented; typically there are rules which can // 这在优化过程中作为中间状态使用
 *         transform it to equivalent, implementable expressions. // NONE约定标志着需要进一步转换
 *     </li>
 *     <li>{@link org.apache.calcite.adapter.enumerable.EnumerableConvention} // EnumerableConvention通过生成Java代码来实现表达式
 *         implements the expression by // 代码将当前行放在Java变量中
 *         generating Java code. The code places the current row in a Java // 然后调用实现消费关系表达式的代码片段
 *         variable, then // 这种方式实现了迭代器模式，支持流式处理
 *         calls the piece of code which implements the consuming relational // 适用于内存计算和复杂逻辑实现
 *         expression. // 例如，names数组的Java数组读取器会生成如下代码：
 *         For example, a Java array reader of the <code>names</code> array
 *         would generate the following code:
 *         <blockquote>
 *     <pre>String[] names;
 * for (int i = 0; i &lt; names.length; i++) {
 *     String name = names[i];
 *     // ... code for consuming relational expression ...
 * }</pre>
 *         </blockquote>
 *     </li>
 * </ul>
 *
 * <p>New traits are added to the planner in one of two ways: // 新特征通过以下两种方式添加到规划器：
 * <ol>
 * <li>If the new trait is integral to Calcite, then each and every // 如果新特征是Calcite的核心部分，那么每一个
 *     implementation of {@link org.apache.calcite.rel.RelNode} should include // RelNode实现都应该在其构造函数传递的RelTraitSet中
 *     its manifestation of the trait as part of the // 包含该特征的具体实现
 *     {@link org.apache.calcite.plan.RelTraitSet} passed to // AbstractRelNode的构造函数接收特征集
 *     {@link org.apache.calcite.rel.AbstractRelNode}'s constructor. It may be // 如果大多数关系表达式使用特征的单一实现
 *     useful to provide alternate <code>AbstractRelNode</code> constructors // 提供替代的AbstractRelNode构造函数可能很有用
 *     if most relational expressions use a single manifestation of the // 这样可以简化RelNode的创建过程
 *     trait.</li>
 *
 * <li>If the new trait describes some aspect of a Farrago extension, then // 如果新特征描述了Farrago扩展的某个方面
 *     the RelNodes passed to // 那么传递给VolcanoPlanner.setRoot()的RelNode
 *     {@link org.apache.calcite.plan.volcano.VolcanoPlanner#setRoot(org.apache.calcite.rel.RelNode)} // 应该在调用setRoot(RelNode)之前扩展其特征集
 *     should have their trait sets expanded before the // 这样可以确保规划器能够识别和处理新特征
 *     <code>setRoot(RelNode)</code> call.</li>
 *
 * </ol>
 *
 * <p>The second trait extension mechanism requires that implementations of // 第二种特征扩展机制要求AbstractRelNode.clone()的实现
 * {@link org.apache.calcite.rel.AbstractRelNode#clone()} must not assume the // 不能假设其特征集中特征的类型和数量
 * type and quantity of traits in their trait set. In either case, the new // 这确保了克隆操作能够正确处理动态特征集
 * <code>RelTraitDef</code> implementation must be // 在任何情况下，新的RelTraitDef实现都必须通过
 * {@link org.apache.calcite.plan.volcano.VolcanoPlanner#addRelTraitDef(org.apache.calcite.plan.RelTraitDef)} // VolcanoPlanner.addRelTraitDef()方法注册到规划器
 * registered with the planner. // 这样规划器才能识别和使用该特征定义
 *
 * <p>A {@link org.apache.calcite.plan.volcano.RelSubset} is a subset of a // RelSubset是RelSet的子集
 * <code>RelSet</code> containing expressions which are equivalent and which // 包含等价且具有相同Convention的表达式
 * have the same <code>Convention</code>. Like <code>RelSet</code>, it is an // 像RelSet一样，它也是一个等价类
 * equivalence class. // RelSubset用于管理相同调用约定的等价表达式
 *
 * <h2>Related packages</h2> // 相关包部分
 * <ul>
 * <li>{@code <a href="../rel/package-summary.html">org.apache.calcite.rel</a>} // org.apache.calcite.rel包
 *     defines {@link org.apache.calcite.rel.RelNode relational expressions}. // 定义了关系表达式RelNode接口及其实现
 * </li>
 * </ul>
 *
 * <h2>Details</h2> // 详细部分，深入解释优化器的工作机制
 *
 * <p>Sets merge when the result of a rule already exists in another set. This // 当规则的结果已经存在于另一个集合中时，集合会合并
 *     implies that all of the expressions are equivalent. The RelSets are // 这意味着所有表达式都是等价的，RelSets会合并
 *     merged, and so are the contained RelSubsets. // 包含的RelSubsets也会相应合并
 *
 * <p>Expression registration. // 表达式注册过程
 * <ul>
 *     <li>Expression is added to a set. We may find that an equivalent // 表达式被添加到集合中，我们可能会发现已经存在等价表达式
 *         expression already exists. Otherwise, this is the moment when an // 否则，这是表达式变为公开和固定的时刻
 *         expression becomes public, and fixed. Its digest is assigned, which // 分配其摘要（digest），这允许我们快速查找相同的表达式
 *         allows us to quickly find identical expressions.</li> // digest是表达式的唯一标识符，用于去重和快速查找
 *
 *     <li>We match operands, figure out which rules are applicable, and // 我们匹配操作数，确定哪些规则适用，并生成规则调用
 *         generate rule calls. The rule calls are placed on a queue, and the // 规则调用被放入队列，重要的规则稍后调用
 *         important ones are called later.</li> // 这种延迟执行机制允许优化器优先处理关键路径
 *
 *     <li>RuleCalls allow us to defer the invocation of rules. When an // RuleCalls允许我们延迟规则的调用，当一个表达式被注册时
 *         expression is registered </li> // 系统会创建相应的RuleCall对象
 * </ul>
 *
 * <p>Algorithm // 优化算法流程
 *
 * <p>To optimize a relational expression R: // 要优化关系表达式R：
 *
 * <p>1. Register R. // 1. 注册R到优化器
 *
 * <p>2. Create rule-calls for all applicable rules. // 2. 为所有适用的规则创建规则调用
 *
 * <p>3. Rank the rule calls by importance. // 3. 按重要性对规则调用进行排序
 *
 * <p>4. Call the most important rule // 4. 调用最重要的规则
 *
 * <p>5. Repeat. // 5. 重复上述过程，直到满足终止条件
 *
 * <p><b>Importance</b>. A rule-call is important if it is likely to produce // 重要性：如果规则调用可能产生计划关键路径上关系表达式的更好实现，则它很重要
 *     better implementation of a relexp on the plan's critical path. Hence (a) // 因此，满足以下条件的规则调用更重要：
 *     it produces a member of an important RelSubset, (b) its children are // (a) 它产生重要RelSubset的成员，(b) 它的子节点成本低
 *     cheap. // 重要性计算考虑了成本、深度、规则优先级等因素
 *
 * <p>Conversion. Conversions are difficult because we have to work backwards // 转换：转换很困难，因为我们必须从目标反向工作
 *     from the goal. // 这意味着优化器需要考虑从当前状态到目标状态的所有可能路径
 *
 * <p><b>Rule triggering</b> // 规则触发机制
 *
 * <p>The rules are: // 以下是示例规则：
 * <ol>
 *     <li><code>PushFilterThroughProjectRule</code>. Operands: // PushFilterThroughProjectRule：将过滤器下推通过投影
 *         <blockquote>
 *     <pre>Filter
 *   Project</pre>
 *         </blockquote>
 *     </li>
 *     <li><code>CombineProjectsRule</code>. Operands: // CombineProjectsRule：合并连续的投影操作
 *         <blockquote>
 *     <pre>Project
 *   Project</pre>
 *         </blockquote>
 *     </li>
 * </ol>
 *
 * <p>A rule can be triggered by a change to any of its operands. Consider the // 规则可以被其任何操作数的变化触发。考虑将两个过滤器合并为一个的规则
 *     rule to combine two filters into one. It would have operands [Filter // 它的操作数是[Filter [Filter]]。如果我注册一个新的Filter，它会在2个位置触发该规则
 *     [Filter]].  If I register a new Filter, it will trigger the rule in 2 // 考虑以下示例查询计划：
 *     places. Consider:
 *
 * <blockquote>
 *   <pre>Project (deptno)                              [exp 1, subset A]
 *   Filter (gender='F')                         [exp 2, subset B]
 *     Project (deptno, gender, empno)           [exp 3, subset C]
 *       Project (deptno, gender, empno, salary) [exp 4, subset D]
 *         TableScan (emp)                       [exp 0, subset X]</pre>
 * </blockquote>
 *
 * <p>Apply <code>PushFilterThroughProjectRule</code> to [exp 2, exp 3]: // 将PushFilterThroughProjectRule应用于[exp 2, exp 3]：
 *
 * <blockquote>
 *   <pre>Project (deptno)                              [exp 1, subset A]
 *   Project (deptno, gender, empno)             [exp 5, subset B]
 *     Filter (gender='F')                       [exp 6, subset E]
 *       Project (deptno, gender, empno, salary) [exp 4, subset D]
 *         TableScan (emp)                       [exp 0, subset X]</pre>
 * </blockquote>
 *
 * <p>Two new expressions are created. Expression 5 is in subset B (because it // 创建了两个新表达式。表达式5在subset B中（因为它与表达式2等价）
 *     is equivalent to expression 2), and expression 6 is in a new equivalence // 表达式6在一个新的等价类subset E中
 *     class, subset E. // 这展示了规则如何产生新的表达式和等价类
 *
 * <p>The products of a applying a rule can trigger a cascade of rules. Even in // 应用规则的结果可以触发级联的规则。即使在这个简单系统（2个规则和4个初始表达式）中
 *     this simple system (2 rules and 4 initial expressions), two more rules // 也会触发两个更多规则：
 *     are triggered:
 *
 * <ul>
 *
 * <li>Registering exp 5 triggers <code>CombineProjectsRule</code>(exp 1, // 注册exp 5触发CombineProjectsRule(exp 1, exp 5)，创建：
 *     exp 5), which creates
 *
 * <blockquote>
 *     <pre>Project (deptno)                              [exp 7, subset A]
 *   Filter (gender='F')                         [exp 6, subset E]
 *     Project (deptno, gender, empno, salary)   [exp 4, subset D]
 *       TableScan (emp)                         [exp 0, subset X]</pre>
 * </blockquote>
 * </li>
 *
 * <li>Registering exp 6 triggers // 注册exp 6触发PushFilterThroughProjectRule(exp 6, exp 4)，创建：
 *     <code>PushFilterThroughProjectRule</code>(exp 6, exp 4), which
 *     creates
 *
 * <blockquote>
 *     <pre>Project (deptno)                              [exp 1, subset A]
 *   Project (deptno, gender, empno)             [exp 5, subset B]
 *     Project (deptno, gender, empno, salary)   [exp 8, subset E]
 *       Filter (gender='F')                     [exp 9, subset F]
 *         TableScan (emp)                       [exp 0, subset X]</pre>
 * </blockquote>
 * </li>
 * </ul>
 *
 * <p>Each rule application adds additional members to existing subsets. The // 每个规则应用都向现有子集添加额外成员。现在非单例子集是A {1, 7}、B {2, 5}和E {6, 8}
 *     non-singleton subsets are now A {1, 7}, B {2, 5} and E {6, 8}, and new // 并且新的组合成为可能。例如，CombineProjectsRule(exp 7, exp 8)进一步将树深度减少为：
 *     combinations are possible. For example,
 *     <code>CombineProjectsRule</code>(exp 7, exp 8) further reduces the depth
 *     of the tree to:
 *
 * <blockquote>
 *   <pre>Project (deptno)                          [exp 10, subset A]
 *   Filter (gender='F')                     [exp 9, subset F]
 *     TableScan (emp)                       [exp 0, subset X]</pre>
 * </blockquote>
 *
 * <p>Todo: show how rules can cause subsets to merge. // 待办：展示规则如何导致子集合并
 *
 * <p>Conclusion: // 结论：
 *
 * <ol>
 * <li>A rule can be triggered by any of its operands.</li> // 1. 规则可以由其任何操作数触发
 * <li>If a subset is a child of more than one parent, it can trigger rule // 2. 如果子集是多个父节点的子节点，它可以触发任何父节点的规则匹配
 *     matches for any of its parents.
 * </li>
 *
 * <li>Registering one relexp can trigger several rules (and even the same // 3. 注册一个关系表达式可以触发多个规则（甚至多次触发同一规则）
 *     rule several times).</li>
 *
 * <li>Firing rules can cause subsets to merge.</li> // 4. 触发规则可以导致子集合并
 * </ol>
 * <h2>References</h2> // 参考文献部分
 *
 * <p>1. <a id="graefe93" href="http://citeseer.nj.nec.com/graefe93volcano.html">The // 1. Graefe和McKenna 1993年的经典论文，描述了Volcano优化器的设计和实现
 *     Volcano Optimizer // 这篇论文奠定了现代查询优化器的基础，引入了动态规划和成本模型的概念
 *     Generator: Extensibility and Efficient Search - Goetz Graefe, William J. // 该优化器强调可扩展性和高效搜索，是Calcite优化器的理论基础
 *     McKenna
 *     (1993)</a>.
 */
package org.apache.calcite.plan.volcano; // 声明这是org.apache.calcite.plan.volcano包的包级别文档，包含火山优化器的所有核心类
