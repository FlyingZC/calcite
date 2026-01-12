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
package org.apache.calcite.linq4j.function; // 定义包名，属于Calcite的LINQ4J功能模块，专门处理函数式编程相关功能

/**
 * Function that takes one parameter and returns a native {@code float} value. // 单参数函数接口，接收一个参数并返回原生float类型的值
 *
 * @param <T0> Type of argument #0 // 泛型参数T0表示第一个参数的类型，可以是任意类型
 */
public interface FloatFunction1<T0> extends Function<Float> { // 定义FloatFunction1接口，继承自Function<Float>，表示这是一个返回Float类型的函数，T0是输入参数类型
  float apply(T0 v0); // 抽象方法，接收一个类型为T0的参数v0，返回一个原生float值，这是接口的核心方法，需要实现类提供具体的业务逻辑
}
