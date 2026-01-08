/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // Apache软件基金会许可证声明，说明本代码遵循Apache 2.0许可证
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可协议，详见NOTICE文件中的版权信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache 2.0许可证授权给您使用本文件
 * (the "License"); you may not use this file except in compliance with  // 您只能在遵守许可证的情况下使用本文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache 2.0许可证的官方网址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS,  // 本软件按"原样"基础分发，不提供任何形式的担保
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 无论是明示的还是暗示的担保或条件
 * See the License for the specific language governing permissions and  // 请参阅许可证以了解具体的权限和
 * limitations under the License.  // 限制条件
 */

/**
 * Defines hints interfaces and utilities for relational expressions.  // 定义了关系表达式的提示（hint）接口和工具类，用于影响查询优化器的行为
 *
 * <h2>The Syntax</h2>  // 提示语法部分
 * We support the Oracle style hint grammar for both query hint(right after the "SELECT" keyword)  // 我们支持Oracle风格的提示语法，包括查询提示（紧跟在SELECT关键字后）
 * and the table hint(right after the table name reference). i.e.  // 和表提示（紧跟在表名引用后）。例如：
 *
 * <blockquote><pre>  // 代码块开始，展示提示语法的示例
 *   select &#47;&#42;&#43; NO_HASH_JOIN, RESOURCE(mem='128mb', parallelism='24') &#42;&#47;  // 查询级别的提示：NO_HASH_JOIN禁止哈希连接，RESOURCE指定内存和并行度
 *   from  // FROM子句开始
 *     emp &#47;&#42;&#43; INDEX(idx1, idx2) &#42;&#47;  // 表提示：指定emp表使用idx1和idx2索引
 *     join  // JOIN操作
 *     dept &#47;&#42;&#43; PROPERTIES(k1='v1', k2='v2') &#42;&#47;  // 表提示：为dept表设置属性k1和k2
 *     on emp.deptno=dept.deptno  // 连接条件
 * </pre></blockquote>  // 代码块结束
 *
 * <h2>Customize Hint Match Rules</h2>  // 自定义提示匹配规则部分
 * Calcite implements a framework to define and propagate the hints. In order to make the hints  // Calcite实现了一个框架来定义和传播提示。为了使提示
 * propagate efficiently, every hint referenced in the sql statement needs to  // 高效传播，SQL语句中引用的每个提示都需要
 * register the match rules for hints propagation.  // 注册提示传播的匹配规则
 *
 * <p>A match rule is defined though {@link org.apache.calcite.rel.hint.HintPredicate}.  // 匹配规则通过HintPredicate接口定义
 * {@link org.apache.calcite.rel.hint.NodeTypeHintPredicate} matches a relational expression  // NodeTypeHintPredicate根据节点类型匹配关系表达式
 * by its node type; you can also define a custom instance with more complicated rules,  // 您也可以定义更复杂规则的自定义实例
 * i.e. JOIN with specified relations from the hint options.  // 例如从提示选项中指定关系的JOIN
 *
 * <p>Here is the code snippet to illustrate how to config the strategies:  // 下面的代码片段说明如何配置策略：
 *
 * <pre>  // 代码块开始，展示如何配置提示策略
 *       // Initialize a HintStrategyTable.  // 初始化一个HintStrategyTable（提示策略表）
 *       HintStrategyTable strategies = HintStrategyTable.builder()  // 使用构建器模式创建HintStrategyTable实例
 *         .addHintStrategy("time_zone", HintPredicates.SET_VAR)  // 添加提示策略：time_zone提示使用SET_VAR谓词（用于设置会话变量）
 *         .addHintStrategy("index", HintPredicates.TABLE_SCAN)  // 添加提示策略：index提示使用TABLE_SCAN谓词（匹配表扫描节点）
 *         .addHintStrategy("resource", HintPredicates.PROJECT)  // 添加提示策略：resource提示使用PROJECT谓词（匹配投影节点）
 *         .addHintStrategy("use_hash_join",  // 添加提示策略：use_hash_join提示
 *             HintPredicates.and(HintPredicates.JOIN,  // 使用AND谓词组合：必须同时满足JOIN条件
 *                 HintPredicates.explicit((hint, rel) -&gt; {  // 以及自定义的显式谓词，接受hint和rel参数
 *                   ...  // 自定义匹配逻辑，可以检查提示选项和关系表达式的属性
 *                 })))  // 自定义谓词结束
 *         .hintStrategy("use_merge_join",  // 添加提示策略：use_merge_join提示
 *             HintStrategyTable.strategyBuilder(  // 使用策略构建器
 *                 HintPredicates.and(HintPredicates.JOIN, joinWithFixedTableName()))  // 匹配JOIN且表名固定的条件
 *                 .excludedRules(EnumerableRules.ENUMERABLE_JOIN_RULE).build())  // 排除可枚举连接规则
 *         .build();  // 构建HintStrategyTable完成
 *       // Config the strategies in the config.  // 在配置中配置这些策略
 *       SqlToRelConverter.Config config = SqlToRelConverter.configBuilder()  // 创建SQL到关系表达式转换器的配置构建器
 *         .withHintStrategyTable(strategies)  // 设置提示策略表
 *         .build();  // 构建配置完成
 *       // Use the config to initialize the SqlToRelConverter.  // 使用配置初始化SqlToRelConverter
 *   ...  // 其他初始化代码
 * </pre>  // 代码块结束
 *
 * <h2>Hints Propagation</h2>  // 提示传播部分
 * There are two cases that need to consider the hints propagation:  // 有两种情况需要考虑提示传播：
 *
 * <ul>  // 无序列表开始
 *   <li>Right after a {@code SqlNode} tree is converted to {@code RelNode} tree, we would  // 当SqlNode树转换为RelNode树后，我们会
 *   propagate the hints from the attaching node to its input(children) nodes. The hints are  // 将提示从附加节点传播到其输入（子）节点。提示通过
 *   propagated recursively with a {@code RelShuttle}, see  // RelShuttle递归传播，详见
 *   RelOptUtil#RelHintPropagateShuttle for how it works.</li>  // RelOptUtil.RelHintPropagateShuttle以了解其工作原理
 *   <li>During rule planning, in the transforming phrase of a {@code RelOptRule},  // 在规则规划期间，在RelOptRule的转换阶段
 *   you <strong>should not</strong> copy the hints by hand. To ensure correctness,  // 您<strong>不应该</strong>手动复制提示。为确保正确性，
 *   the hints copy work within planner rule is taken care of by Calcite;  // 规划器规则中的提示复制工作由Calcite处理；
 *   We make some effort to make the thing easier: right before the new relational expression  // 我们努力简化这个过程：在新的关系表达式
 *   was registered into the planner, the hints of the old relational expression was  // 注册到规划器之前，旧关系表达式的提示会被
 *   copied into the new expression sub-tree(by "new" we mean, the node was created  // 复制到新表达式子树中（"新"指的是节点是在规划器规则中创建的）
 *   just in the planner rule) if the nodes implement  // 如果节点实现了
 *   {@link org.apache.calcite.rel.hint.Hintable}.</li>  // Hintable接口
 * </ul>  // 无序列表结束
 *
 * <h2>Design Doc</h2>  // 设计文档部分
 * <a href="https://docs.google.com/document/d/1mykz-w2t1Yw7CH6NjUWpWqCAf_6YNKxSc59gXafrNCs/edit?usp=sharing">Calcite SQL and Planner Hints Design</a>.  // Calcite SQL和规划器提示设计文档的链接
 */
package org.apache.calcite.rel.hint;  // 包声明，定义org.apache.calcite.rel.hint包，包含提示相关的接口和工具类
