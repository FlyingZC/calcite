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
package org.apache.calcite.linq4j.function; // 定义包名，该包包含LINQ4J框架中的函数相关类和接口

/**
 * Base interface for all functions. // 所有函数的基础接口，这是Calcite的LINQ4J框架中函数体系的根接口
 *
 * @param <R> Result type // 泛型参数R表示函数的返回类型，所有函数都必须指定返回值的类型
 */ // 该接口不包含任何方法，仅作为标记接口，用于标识函数类型
public interface Function<R> { // 定义Function接口，使用泛型R表示返回类型，这是所有函数接口的基类
} // 接口定义结束，该接口为空接口，主要起到类型标记和泛型约束的作用
