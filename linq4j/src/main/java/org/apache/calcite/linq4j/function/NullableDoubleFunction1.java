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
package org.apache.calcite.linq4j.function; // 指定该接口所属的包，位于linq4j的function子包中，linq4j是Calcite对LINQ(Language Integrated Query)的Java实现

/**
 * Function that takes one parameter and returns a {@link Double} value that
 * may be null.
 * 这是一个函数式接口，定义了一个接受一个参数并可能返回null的Double值的函数
 * 该接口主要用于表示那些输入一个值，输出一个可能为null的Double类型结果的转换操作
 * 
 * @param <T0> Type of argument #0 // 泛型参数T0表示该函数接受的第一个参数的类型，可以是任意Java类型
 */
public interface NullableDoubleFunction1<T0> extends Function1<T0, Double> { // 定义一个泛型接口NullableDoubleFunction1，继承自Function1<T0, Double>，表示这是一个接受一个T0类型参数并返回Double类型结果的函数，Double可能为null
} // 接口定义结束，这是一个标记接口，继承了Function1的所有方法，主要用于类型安全和函数式编程场景
