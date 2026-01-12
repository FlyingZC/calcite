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
// 包声明：指定该接口所属的包为org.apache.calcite.linq4j.function，这是Calcite的LINQ4J功能包，包含各种函数式接口定义
package org.apache.calcite.linq4j.function;

/**
 * 单参数函数接口，接收一个参数并返回一个可能为null的Float值
 * Function that takes one parameter and returns a {@link Float} value that
 * may be null.
 * 
 * 该接口是Calcite LINQ4J框架中定义的函数式接口之一，用于表示接受一个参数并返回Float类型的函数
 * 与普通的FloatFunction1不同，该接口返回的Float值允许为null，这在处理可能缺失的数据时非常有用
 * 该接口继承自Function1<T0, Float>，因此可以用于任何需要Function1的上下文中
 * 
 * 应用场景：
 * 1. 数据转换：将一种类型的数据转换为Float类型，转换结果可能为null
 * 2. 数据提取：从复杂对象中提取Float属性，该属性可能不存在
 * 3. 条件计算：根据输入参数进行计算，计算结果可能为null
 * 4. SQL函数映射：映射SQL中返回可空Float值的标量函数
 *
 * @param <T0> 类型参数，表示函数第一个参数的类型 Type of argument #0
 *           T0可以是任意Java类型，包括基本类型的包装类和自定义对象类型
 *           泛型设计使得该接口可以灵活地处理各种输入类型
 */
public interface NullableFloatFunction1<T0> extends Function1<T0, Float> {
}
