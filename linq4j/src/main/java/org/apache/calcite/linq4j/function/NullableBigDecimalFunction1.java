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
 */ // Apache许可证头，声明版权和授权信息
package org.apache.calcite.linq4j.function; // 声明包名，该接口属于org.apache.calcite.linq4j.function包，是Calcite LINQ4J框架中函数式接口定义的一部分

import java.math.BigDecimal; // 导入BigDecimal类，用于表示高精度的十进制数值，这是该接口返回值类型的核心数据类型

/**
 * Function that takes one parameter and returns a {@link BigDecimal} value that
 * may be null.
 * // 这是一个函数式接口，接受一个参数并返回一个可能为null的BigDecimal值
 * // 该接口是Calcite LINQ4J框架中用于定义单参数函数的核心接口之一，专门用于处理返回BigDecimal类型的函数
 * // 在SQL查询中，很多数值计算函数都需要返回精确的十进制数值，BigDecimal提供了高精度的数值计算能力
 * // "Nullable"表示返回值可能为null，这在处理SQL中的NULL值时非常重要，因为SQL中的NULL表示缺失或未知的数据
 * // 该接口扩展了Function1<T0, BigDecimal>接口，使其成为Function1的一个特化版本，专门用于BigDecimal返回类型
 *
 * @param <T0> Type of argument #0
 * // 泛型参数T0表示该函数接受的第一个参数的类型，可以是任意Java类型
 * // 通过泛型参数，该接口可以灵活地处理各种类型的输入参数，然后将它们转换为BigDecimal类型的输出
 * // 例如，T0可以是String、Integer、Double、BigDecimal等类型，函数内部会将输入转换为BigDecimal
 */ // 接口文档注释，详细说明接口的作用、功能和泛型参数的含义
public interface NullableBigDecimalFunction1<T0> // 定义一个公开的接口，名为NullableBigDecimalFunction1，使用泛型参数T0，表示接受一个类型为T0的参数
    extends Function1<T0, BigDecimal> { // 继承Function1<T0, BigDecimal>接口，表明这是一个单参数函数接口，输入类型为T0，输出类型为BigDecimal
} // 接口定义结束，该接口本身没有定义任何方法，所有方法都继承自Function1接口，主要是apply方法
