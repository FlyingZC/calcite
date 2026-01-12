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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.linq4j.function; // 定义包名，该接口属于org.apache.calcite.linq4j.function包，这是Calcite的LINQ4J功能包

import java.math.BigDecimal; // 导入BigDecimal类，用于高精度的十进制数值计算

/**
 * Function that takes one parameter and returns a {@link BigDecimal} value.
 * 接受一个参数并返回BigDecimal值的函数接口
 * 这是一个函数式接口，用于定义接受一个参数并返回BigDecimal类型结果的函数
 * 它继承自Function1<T0, BigDecimal>接口，是Calcite LINQ4J框架中专门用于BigDecimal类型操作的函数接口
 * 
 * @param <T0> Type of argument #0
 * 参数T0的类型声明，表示该函数接受一个类型为T0的参数
 * T0是泛型类型参数，可以是任意Java类型，由调用者指定具体类型
 */
public interface BigDecimalFunction1<T0> extends Function1<T0, BigDecimal> { // 定义BigDecimalFunction1接口，它是一个泛型接口，接受一个类型为T0的参数，继承自Function1<T0, BigDecimal>接口
} // 接口定义结束，由于继承自Function1接口，该接口会自动继承Function1的apply方法，无需额外定义方法
