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
package org.apache.calcite.linq4j.function; // 声明包名，该接口位于 org.apache.calcite.linq4j.function 包中，属于 Calcite LINQ4J 框架的核心函数式接口包

/**
 * Function with one parameter. // 这是一个带有一个参数的函数接口，是 Calcite LINQ4J 框架中用于表示单参数函数的核心接口
 *
 * @param <R> Result type // 泛型参数 R 表示函数的返回值类型
 * @param <T0> Type of parameter 0 // 泛型参数 T0 表示函数第一个（也是唯一一个）参数的类型
 */
public interface Function1<T0, R> extends Function<R> { // 定义 Function1 接口，继承自 Function<R>，表示这是一个返回类型为 R 的函数，接受一个类型为 T0 的参数
  /**
   * The identity function. // 恒等函数，即输入什么就输出什么，不进行任何转换的函数
   *
   * @see Functions#identitySelector() // 参见 Functions 类中的 identitySelector() 方法，该方法提供了恒等函数的实现
   */
  Function1<Object, Object> IDENTITY = v0 -> v0; // 定义恒等函数常量，接受一个 Object 类型的参数 v0，并直接返回该参数 v0，这是函数式编程中常用的工具函数

  R apply(T0 a0); // 抽象方法，接受一个类型为 T0 的参数 a0，返回类型为 R 的结果，这是 Function1 接口的核心方法，所有实现类都必须实现该方法来定义具体的函数逻辑
}
