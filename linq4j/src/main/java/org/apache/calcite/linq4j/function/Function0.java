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
package org.apache.calcite.linq4j.function; // 定义包名，该接口属于org.apache.calcite.linq4j.function包，用于LINQ4J框架中的函数式接口定义

/**
 * Function with no parameters. // 函数式接口，表示一个没有参数的函数，用于封装无参操作的逻辑
 * // Function0是Calcite LINQ4J框架中定义的函数式接口之一，用于表示不接受任何参数但返回一个结果的函数
 * // 这个接口通常用于延迟计算、工厂方法、提供者模式等场景，其中函数的执行结果可能依赖于某些外部状态或计算
 * // Function0中的"0"表示该函数不接受任何参数，这是Calcite函数式接口命名规范的一部分
 * // 该接口继承自Function<R>，是Calcite函数式接口体系的一部分，与Function1<T,R>、Function2<T0,T1,R>等接口形成完整的参数数量体系
 *
 * @param <R> Result type // 泛型参数R表示该函数的返回值类型，可以是任何Java类型
 */
public interface Function0<R> extends Function<R> { // 定义Function0接口，继承自Function<R>，表示这是一个无参数的函数式接口
  R apply(); // 抽象方法，定义函数的执行逻辑，调用此方法将执行函数并返回类型为R的结果，该方法不接受任何参数
}
