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
// Apache许可证声明，说明该代码遵循Apache 2.0开源协议
package org.apache.calcite.linq4j.function; // 声明该接口所属的包，位于Calcite的LINQ4J功能包下的function子包中

/**
 * Function that takes one parameter and returns an {@link Integer} value that
 * may be null.
 * // 这是一个函数式接口，接受一个参数并返回一个可能为null的Integer值
 * // 该接口是Calcite LINQ4J框架中定义的可空整数函数接口，用于表示那些可能返回null的单参数整数函数
 * // 在SQL到Java的转换过程中，很多SQL函数可能返回NULL值，因此需要使用这种可空类型的函数接口
 *
 * @param <T0> Type of argument #0
 * // 泛型参数T0表示该函数接受的第一个参数的类型，可以是任意Java类型
 * // 例如：String、Integer、Double、自定义对象等
 */
// 定义一个泛型接口NullableIntegerFunction1，继承自Function1<T0, Integer>
// Function1是Calcite LINQ4J框架中定义的单参数函数基础接口
// 该接口专门用于处理那些返回Integer类型且可能为null的函数
// 在Calcite的SQL执行引擎中，当SQL表达式可能返回NULL时，会使用这种可空类型的函数接口
public interface NullableIntegerFunction1<T0> extends Function1<T0, Integer> {
} // 接口体结束，该接口本身没有定义新的方法，所有方法都继承自Function1接口
// Function1接口中定义了apply(T0)方法，返回Integer类型，因此NullableIntegerFunction1可以直接使用
// 实现类需要实现apply方法，该方法接受一个T0类型的参数，返回一个可能为null的Integer值
