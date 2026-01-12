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
 */ // Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.linq4j.function; // 声明包路径，该接口属于org.apache.calcite.linq4j.function包

/**
 * Function that takes one parameter and returns a native {@code double} value.
 * 接收一个参数并返回原生double值的函数接口
 *
 * @param <T0> Type of argument #0
 */ // 泛型参数T0表示第一个参数的类型
public interface DoubleFunction1<T0> extends Function<Double> { // 定义一个泛型接口DoubleFunction1，继承自Function<Double>，表示这是一个函数式接口
  double apply(T0 v0); // 抽象方法apply，接收一个类型为T0的参数v0，返回一个double类型的值，这是该函数式接口的核心方法
}
