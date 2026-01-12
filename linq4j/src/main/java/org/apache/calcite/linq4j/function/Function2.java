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
 */ // Apache许可证声明，定义了代码的使用权限和限制条件
package org.apache.calcite.linq4j.function; // 定义包路径，org.apache.calcite.linq4j.function表示这是Calcite项目中LINQ4J模块的函数包

/**
 * Function with two parameters. // 这是一个带有两个参数的函数接口，是Calcite LINQ4J框架中用于表示双参数函数的基础接口
 * // 该接口继承自Function<R>，是函数式接口体系的一部分，用于支持函数式编程风格
 *
 * @param <R> Result type // R是返回值类型，表示该函数执行后返回的结果类型
 * @param <T0> Type of argument #0 // T0是第一个参数的类型，表示函数的第一个输入参数的类型
 * @param <T1> Type of argument #1 // T1是第二个参数的类型，表示函数的第二个输入参数的类型
 * // 这个接口的设计遵循Java泛型规范，通过泛型参数实现类型安全的函数定义，可以在编译时进行类型检查
 */ // 该接口可以被lambda表达式或方法引用实现，是函数式编程的核心接口之一
public interface Function2<T0, T1, R> extends Function<R> { // 定义Function2接口，继承自Function<R>，表示这是一个返回类型为R的函数接口
  R apply(T0 v0, T1 v1); // 定义抽象方法apply，接收两个参数v0和v1，返回类型为R的结果；这是函数式接口的核心方法，需要由实现类提供具体的执行逻辑
} // 接口定义结束
