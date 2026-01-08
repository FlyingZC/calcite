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
 * Query optimizer rules for Java calling convention.
 * // 用于 Java 调用约定的查询优化器规则
 * 
 * 这个包 (org.apache.calcite.adapter.enumerable) 是 Apache Calcite 查询优化框架中的核心组件之一,
 * 它实现了基于 Java 编程语言的查询执行适配器。以下是这个包的详细说明:
 * 
 * 【包的核心作用】
 * 1. 将关系代数表达式 (RelNode) 转换为可执行的 Java 代码
 * 2. 提供基于 Enumerable 接口的查询执行机制
 * 3. 实现了从逻辑查询计划到物理执行计划的转换规则
 * 
 * 【核心概念 - Enumerable 约定】
 * - Enumerable 是 LINQ (Language Integrated Query) 风格的接口,表示可枚举的数据集合
 * - Java 调用约定意味着查询结果通过 Java 代码执行,而不是通过 SQL 或其他方式
 * - 这种约定允许 Calcite 将查询编译为 Java 字节码,提供高性能的执行方式
 * 
 * 【主要组件说明】
 * 
 * 1. EnumerableRel 接口
 *    - 所有可枚举关系表达式的基接口
 *    - 定义了如何将关系表达式转换为可执行的 Java 代码
 *    - 实现类需要提供 implement() 方法来生成执行代码
 * 
 * 2. 优化器规则 (Rules)
 *    - EnumerableFilterRule: 将过滤操作转换为可枚举形式
 *    - EnumerableProjectRule: 将投影操作转换为可枚举形式
 *    - EnumerableJoinRule: 将连接操作转换为可枚举形式
 *    - EnumerableSortRule: 将排序操作转换为可枚举形式
 *    - EnumerableAggregateRule: 将聚合操作转换为可枚举形式
 *    - EnumerableScanRule: 将表扫描转换为可枚举形式
 *    - EnumerableCalcRule: 将计算操作转换为可枚举形式
 *    - EnumerableUnionRule: 将联合操作转换为可枚举形式
 *    - EnumerableIntersectRule: 将交集操作转换为可枚举形式
 *    - EnumerableMinusRule: 将差集操作转换为可枚举形式
 *    - EnumerableMatchRule: 将模式匹配操作转换为可枚举形式
 *    - EnumerableCorrelateRule: 将相关子查询转换为可枚举形式
 *    - EnumerableLimitRule: 将限制操作转换为可枚举形式
 *    - EnumerableValuesRule: 将值列表转换为可枚举形式
 *    - EnumerableTableModifyRule: 将表修改操作转换为可枚举形式
 *    - EnumerableWindowRule: 将窗口函数转换为可枚举形式
 * 
 * 3. 物理算子实现
 *    - EnumerableFilter: 实现过滤操作的物理执行
 *    - EnumerableProject: 实现投影操作的物理执行
 *    - EnumerableHashJoin: 实现哈希连接的物理执行
 *    - EnumerableMergeJoin: 实现归并连接的物理执行
 *    - EnumerableNestedLoopJoin: 实现嵌套循环连接的物理执行
 *    - EnumerableSort: 实现排序操作的物理执行
 *    - EnumerableLimit: 实现限制操作的物理执行
 *    - EnumerableAggregate: 实现聚合操作的物理执行
 *    - EnumerableUnion: 实现联合操作的物理执行
 *    - EnumerableIntersect: 实现交集操作的物理执行
 *    - EnumerableMinus: 实现差集操作的物理执行
 *    - EnumerableMatch: 实现模式匹配的物理执行
 *    - EnumerableCorrelate: 实现相关子查询的物理执行
 *    - EnumerableCalc: 实现计算操作的物理执行
 *    - EnumerableValues: 实现值列表的物理执行
 *    - EnumerableTableModify: 实现表修改操作的物理执行
 *    - EnumerableWindow: 实现窗口函数的物理执行
 *    - EnumerableBatchNestedLoopJoin: 实现批处理嵌套循环连接
 *    - EnumerableMergeUnion: 实现归并联合
 *    - EnumerableSortedAggregate: 实现排序聚合
 *    - EnumerableRepeatUnion: 实现重复联合
 *    - EnumerableCollect: 实现集合收集
 *    - EnumerableUncollect: 实现集合解构
 *    - EnumerableInterpreter: 实现解释器模式执行
 * 
 * 4. 代码生成器
 *    - EnumerableRelImplementor: 核心代码生成器接口
 *    - JavaRelImplementor: Java 关系表达式实现器
 *    - PhysType: 物理类型系统
 *    - PhysTypeImpl: 物理类型实现
 *    - JavaRowFormat: Java 行格式定义
 *    - BlockBuilder: 代码块构建器
 *    - BlockStatement: 代码块语句
 *    - Expression: 表达式抽象
 *    - ParameterExpression: 参数表达式
 *    - Variable: 变量定义
 *    - Pair: 键值对
 * 
 * 5. 聚合实现
 *    - AggImplementor: 聚合函数实现器接口
 *    - AggContext: 聚合上下文
 *    - AggAddContext: 聚合添加上下文
 *    - AggResetContext: 聚合重置上下文
 *    - AggResultContext: 聚合结果上下文
 *    - AggregateLambdaFactory: 聚合 Lambda 工厂
 *    - BasicAggregateLambdaFactory: 基础聚合 Lambda 工厂
 *    - BasicLazyAccumulator: 基础惰性累加器
 *    - WinAggImplementor: 窗口聚合实现器
 *    - WinAggContext: 窗口聚合上下文
 *    - WinAggFrameResultContext: 窗口聚合框架结果上下文
 *    - WinAggAddContext: 窗口聚合添加上下文
 *    - WinAggResetContext: 窗口聚合重置上下文
 *    - WinAggResultContext: 窗口聚合结果上下文
 * 
 * 6. 约定和转换
 *    - EnumerableConvention: 可枚举约定定义
 *    - EnumerableRelFactories: 可枚举关系表达式工厂
 *    - EnumerableToEnumerableConverter: 可枚举到可枚举的转换器
 *    - EnumerableBindable: 可枚举可绑定接口
 *    - EnumerableInterpretable: 可枚举可解释接口
 * 
 * 7. 工具类
 *    - EnumUtils: 枚举工具类
 *    - EnumerableLambda: Lambda 表达式工具
 *    - NullPolicy: 空值策略
 *    - JoinType: 连接类型
 *    - ImmutableIntList: 不可变整数列表
 *    - ImmutableBitSet: 不可变位集合
 * 
 * 【执行流程】
 * 1. SQL 解析: 将 SQL 解析为抽象语法树 (AST)
 * 2. 逻辑计划生成: 将 AST 转换为逻辑关系表达式 (RelNode)
 * 3. 优化: 应用优化规则,转换和优化逻辑计划
 * 4. 物理计划生成: 应用 Enumerable 相关规则,将逻辑计划转换为物理计划
 * 5. 代码生成: 使用 EnumerableRelImplementor 生成 Java 代码
 * 6. 执行: 编译并执行生成的 Java 代码,返回结果
 * 
 * 【代码生成机制】
 * - 使用 Janino 编译器在运行时编译生成的 Java 代码
 * - 生成的代码实现了 Enumerable 接口,提供迭代器访问数据
 * - 支持多种行格式:数组、对象、字段等
 * - 支持表达式优化和常量折叠
 * 
 * 【性能优化】
 * 1. 批处理: 支持批量数据处理,减少函数调用开销
 * 2. 向量化: 尽可能使用向量化操作
 * 3. 惰性求值: 支持惰性求值,避免不必要的计算
 * 4. 内存管理: 优化内存使用,减少对象创建
 * 5. 索引利用: 在可能的情况下利用索引加速查询
 * 
 * 【扩展性】
 * - 可以通过实现 EnumerableRel 接口来添加自定义的可枚举操作
 * - 可以通过实现 AggImplementor 接口来添加自定义的聚合函数
 * - 可以通过实现 WinAggImplementor 接口来添加自定义的窗口函数
 * - 可以通过注册自定义规则来扩展优化器
 * 
 * 【与其他包的关系】
 * - org.apache.calcite.rel: 定义关系表达式接口
 * - org.apache.calcite.plan: 提供优化器框架
 * - org.apache.calcite.rex: 提供行表达式
 * - org.apache.calcite.adapter: 提供适配器框架
 * - org.apache.calcite.linq4j: 提供 LINQ4J 库支持
 * 
 * 【使用示例】
 * // 创建可枚举约定
 * EnumerableConvention convention = EnumerableConvention.INSTANCE;
 * 
 * // 应用可枚举规则
 * RelOptPlanner planner = VolcanoPlanner.INSTANCE;
 * planner.addRule(EnumerableFilterRule.INSTANCE);
 * planner.addRule(EnumerableProjectRule.INSTANCE);
 * 
 * // 优化查询
 * RelNode optimized = planner.findBestExp(relNode);
 * 
 * // 生成代码
 * EnumerableRelImplementor implementor = new EnumerableRelImplementor(...);
 * Class<?> clazz = implementor.implementRoot((EnumerableRel) optimized);
 * 
 * // 执行查询
 * Enumerable enumerable = (Enumerable) clazz.newInstance();
 * Enumerator enumerator = enumerable.enumerator();
 * while (enumerator.moveNext()) {
 *     Object[] row = (Object[]) enumerator.current();
 *     // 处理行数据
 * }
 * 
 * 【注意事项】
 * 1. 生成的代码会在运行时编译,需要确保类加载器配置正确
 * 2. 复杂查询可能生成大量代码,需要注意内存使用
 * 3. 代码生成过程可能较慢,建议缓存生成的类
 * 4. 需要处理空值和类型转换
 * 5. 需要考虑并发和线程安全
 * 
 * 【总结】
 * 这个包是 Calcite 查询执行引擎的核心,它通过代码生成技术将查询计划转换为高效的 Java 代码,
 * 提供了灵活、高性能的查询执行方式。理解这个包对于深入理解 Calcite 的工作原理和性能优化至关重要。
 */
package org.apache.calcite.adapter.enumerable;
