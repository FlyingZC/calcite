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
package org.apache.calcite.piglet; // 指定这个类属于 org.apache.calcite.piglet 包，这是 Calcite 项目中 Piglet 模块的包路径

import org.apache.pig.builtin.BigDecimalMax; // 导入 Apache Pig 的 BigDecimalMax 类，用于计算 BigDecimal 类型的最大值
import org.apache.pig.builtin.BigDecimalSum; // 导入 Apache Pig 的 BigDecimalSum 类，用于计算 BigDecimal 类型的总和
import org.apache.pig.data.Tuple; // 导入 Apache Pig 的 Tuple 类，表示 Pig 中的元组数据结构，相当于一行数据

import java.io.IOException; // 导入 Java 的 IOException 类，用于处理输入输出异常
import java.math.BigDecimal; // 导入 Java 的 BigDecimal 类，用于高精度的十进制数值计算

/**
 * Implementation methods. // 这个类提供了 Pig 用户定义函数（UDF）的实现方法
 *
 * <p>Found by {@link PigUdfFinder} using reflection. // 这些方法通过 PigUdfFinder 使用反射机制被发现和调用
 */
public class PigUdfs { // 定义 PigUdfs 类，这是一个工具类，包含 Pig UDF 的静态实现方法
  private PigUdfs() {} // 私有构造方法，防止实例化，因为这是一个只包含静态方法的工具类

  public static BigDecimal bigdecimalsum(Tuple input) throws IOException { // 定义静态方法 bigdecimalsum，计算输入元组中所有 BigDecimal 值的总和，参数 input 是包含数据的元组，返回 BigDecimal 类型的总和，可能抛出 IOException 异常
    // "exec" method is declared in the parent class of // 注释说明：exec 方法是在 AlgebraicBigDecimalMathBase 父类中声明的
    // AlgebraicBigDecimalMathBase // 注释继续：AlgebraicBigDecimalMathBase 是 BigDecimalSum 的父类，提供了 exec 方法
    return new BigDecimalSum().exec(input); // 创建 BigDecimalSum 实例并调用其 exec 方法处理输入元组，返回计算得到的总和结果
  } // 方法结束

  public static BigDecimal bigdecimalmax(Tuple input) throws IOException { // 定义静态方法 bigdecimalmax，计算输入元组中所有 BigDecimal 值的最大值，参数 input 是包含数据的元组，返回 BigDecimal 类型的最大值，可能抛出 IOException 异常
    // "exec" method is declared in the parent class of // 注释说明：exec 方法是在 AlgebraicBigDecimalMathBase 父类中声明的
    // AlgebraicBigDecimalMathBase // 注释继续：AlgebraicBigDecimalMathBase 是 BigDecimalMax 的父类，提供了 exec 方法
    return new BigDecimalMax().exec(input); // 创建 BigDecimalMax 实例并调用其 exec 方法处理输入元组，返回计算得到的最大值结果
  } // 方法结束
} // 类定义结束
