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
package org.apache.calcite.linq4j.function; // 声明包名，该接口位于org.apache.calcite.linq4j.function包下，属于Calcite的LINQ4J（语言集成查询）功能模块

/**
 * Function that takes one parameter and returns a native {@code int} value. // 单参数函数接口，接受一个参数并返回原生int类型值
 * 这是一个函数式接口，定义了接受一个参数并返回int类型值的函数规范，是Calcite LINQ4J框架中用于表示一元整数函数的核心接口
 * 该接口继承自Function<Integer>，使其可以与Calcite的函数式编程体系无缝集成
 *
 * @param <T0> Type of argument #0 // 泛型参数T0表示第一个参数的类型，可以是任意Java类型，函数会将该类型转换为int返回值
 */
public interface IntegerFunction1<T0> extends Function<Integer> { // 定义公共接口IntegerFunction1，使用泛型T0作为参数类型，继承自Function<Integer>接口，使其具备函数式编程能力
  int apply(T0 v0); // 抽象方法，接受一个类型为T0的参数v0，返回int类型的值，这是该接口的核心方法，实现类需要提供具体的转换逻辑
}
