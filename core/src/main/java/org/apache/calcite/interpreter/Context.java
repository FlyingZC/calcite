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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证，允许自由使用和修改但需保留版权声明
package org.apache.calcite.interpreter; // 声明包名，该类属于org.apache.calcite.interpreter包，是Calcite解释器模块的一部分

import org.apache.calcite.DataContext; // 导入Calcite的数据上下文接口，提供查询执行时的数据访问能力

import org.checkerframework.checker.nullness.qual.MonotonicNonNull; // 导入CheckerFramework注解，表示字段在初始化后不会从非null变为null，但可能从null变为非null
import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework注解，表示字段可能为null，用于静态空值检查

/**
 * Context for executing a scalar expression in an interpreter.
 */ // JavaDoc注释：这是在解释器中执行标量表达式（即返回单个值的表达式，如列引用、字面量、函数调用等）的上下文类
public class Context { // 定义Context类，该类作为标量表达式解释执行的上下文环境，提供访问数据和存储中间结果的能力
  public final DataContext root; // 定义公共final成员变量root，类型为DataContext，表示根数据上下文，提供对数据源、表、变量等全局资源的访问，final表示该引用一旦初始化不可改变

  /** Values of incoming columns from all inputs. */ // JavaDoc注释：说明values字段用于存储来自所有输入的传入列的值
  public @Nullable Object @MonotonicNonNull [] values; // 定义公共成员变量values，类型为Object数组，@Nullable表示该数组可能为null，@MonotonicNonNull表示数组一旦非null，其元素也不会变为null，用于存储当前行的所有列值，支持表达式对这些列值的访问

  Context(DataContext root) { // 定义构造方法，参数为DataContext类型的root，用于创建Context实例
    this.root = root; // 将传入的root参数赋值给成员变量root，初始化根数据上下文，注意values数组未在此初始化，允许延迟初始化
  } // 构造方法结束
} // Context类定义结束
