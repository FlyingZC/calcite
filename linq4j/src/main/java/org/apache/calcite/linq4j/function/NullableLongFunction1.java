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
package org.apache.calcite.linq4j.function; // 声明包名，该接口属于org.apache.calcite.linq4j.function包，这是Calcite LINQ4J框架中的函数包

/**
 * Function that takes one parameter and returns a {@link Long} value that
 * may be null.
 * // 函数式接口，接受一个参数并返回一个可能为null的Long值
 * // 这是Calcite LINQ4J框架中用于处理可空Long类型返回值的单参数函数接口
 * // 与非空的LongFunction1接口不同，这个接口允许返回值为null，适用于可能产生空值的计算场景
 * // 例如：在SQL查询中，某些聚合函数或转换函数可能返回null
 *
 * @param <T0> Type of argument #0
 */ // 泛型参数T0表示函数第一个参数的类型，可以是任意Java类型
public interface NullableLongFunction1<T0> extends Function1<T0, Long> { // 定义泛型接口NullableLongFunction1，继承自Function1<T0, Long>，表示这是一个单参数函数，参数类型为T0，返回类型为Long（可为null）
} // 接口定义结束，这是一个标记接口，具体的函数实现需要实现Function1接口中的apply方法
