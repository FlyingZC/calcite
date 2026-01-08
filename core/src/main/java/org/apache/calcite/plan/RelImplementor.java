/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // Apache 软件基金会许可证声明，表明此代码遵循 Apache 2.0 许可证
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可协议，详见随此工作分发的 NOTICE 文件以获取版权所有权信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的附加信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF 根据Apache 2.0 许可证将此文件授权给您
 * (the "License"); you may not use this file except in compliance with  // ("许可证");除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下位置获取许可证副本
 *  // 
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache 许可证 2.0 的官方网址
 *  // 
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS,  // 根据许可证分发的软件是按"原样"基础分发的
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不带任何形式的保证或条件，无论是明示的还是暗示的
 * See the License for the specific language governing permissions and  // 请参阅许可证以了解管理权限和
 * limitations under the License.  // 许可证下的限制的特定语言
 */  // 
package org.apache.calcite.plan;  // 声明此接口所属的包，位于 org.apache.calcite.plan 包中，这是 Calcite 框架中负责关系代数表达式规划和实现的核心包

import org.apache.calcite.rel.RelNode;  // 导入 RelNode 类，这是 Calcite 中所有关系表达式（关系代数节点）的基类，代表查询计划中的一个操作节点
import org.apache.calcite.sql.validate.SqlConformance;  // 导入 SqlConformance 接口，定义了 SQL 方言的符合性规则，用于控制 SQL 语法的兼容性和行为

/**
 * This is a marker interface for a callback used to convert a tree of  // 这是一个标记接口，用于定义回调接口，该回调用于将关系表达式树转换为可执行的计划
 * {@link RelNode relational expressions} into a plan. Calling  // 将由 RelNode 表示的关系表达式树转换为执行计划。不同的调用约定
 * conventions typically have their own protocol for walking over a  // 通常有自己的遍历关系表达式树的协议，因此它们有各自对应的实现器
 * tree, and correspondingly have their own implementors  // 相应地，每个约定都有自己的实现器来处理关系表达式树的遍历和转换
 */  // 
public interface RelImplementor {  // 定义 RelImplementor 接口，这是一个标记接口，没有继承其他接口，用于标识关系表达式实现器的类型
  /** Returns the desired SQL conformance. */  // 方法注释：返回所需的 SQL 符合性配置，用于控制 SQL 语法和行为的兼容性规范
  SqlConformance getConformance();  // 声明抽象方法，要求实现类返回 SqlConformance 对象，该对象定义了 SQL 方言的符合性规则（如大小写敏感、标识符引用方式等）
}  // 接口定义结束
