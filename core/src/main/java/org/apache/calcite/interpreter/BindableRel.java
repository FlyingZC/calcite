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
 */ // Apache许可证声明，声明代码版权归属和使用许可
package org.apache.calcite.interpreter; // 声明包名，此接口属于org.apache.calcite.interpreter包，该包负责Calcite的解释器相关功能

import org.apache.calcite.rel.RelNode; // 导入RelNode接口，这是Calcite中所有关系表达式（关系算子）的基接口，表示关系代数中的一个节点
import org.apache.calcite.runtime.ArrayBindable; // 导入ArrayBindable接口，该接口定义了将关系表达式绑定到数组的能力，用于数据访问

/**
 * Relational expression that can implement itself in Bindable // 类注释：这是一个关系表达式接口，它能够在Bindable约定下实现自己
 * convention. // "约定"（convention）是Calcite中的一个重要概念，表示关系表达式如何被物理实现，Bindable约定意味着该关系表达式可以通过绑定（bind）的方式直接执行数据访问
 *
 * @see org.apache.calcite.interpreter.BindableConvention // 参考说明：建议查看BindableConvention类以了解Bindable约定的详细定义和实现
 */ // 类注释结束，说明此接口的作用和用途
public interface BindableRel extends RelNode, ArrayBindable, InterpretableRel { // 定义BindableRel接口，它继承自三个接口：RelNode（关系表达式基类）、ArrayBindable（数组绑定能力）、InterpretableRel（可解释执行能力），这意味着BindableRel既是一个关系表达式，又具备绑定和解释执行的能力
} // 接口定义结束，注意这是一个标记接口（marker interface），本身没有定义任何方法，所有方法都从父接口继承
