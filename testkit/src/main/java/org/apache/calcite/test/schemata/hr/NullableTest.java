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
package org.apache.calcite.test.schemata.hr; // 定义包路径，该类位于org.apache.calcite.test.schemata.hr包下，属于测试schema中的hr（人力资源）模块

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker Framework的@Nullable注解，用于标记可能为null的字段、参数或返回值，帮助进行静态空值检查

import java.util.Objects; // 导入Java工具类Objects，提供用于操作对象的静态方法，特别是equals和hash等工具方法

/**
 * A table model that contains nullable columns. // 这是一个包含可空列的表模型类，用于在Calcite测试中模拟数据库表中包含NULL值的列
 * 该类主要用于测试Calcite框架对可空列（nullable columns）的处理能力，包括查询优化、类型推断、空值处理等功能
 * 通过提供包含可空和不可空列的数据模型，可以验证Calcite在处理SQL查询时正确处理NULL值的能力
 * 该类实现了equals和hashCode方法，使其可以作为Map的键或在集合中正确使用
 */
public class NullableTest { // 定义公共类NullableTest，表示一个包含可空列的测试表模型
  public final @Nullable Integer col1; // 定义第一个列col1，类型为Integer包装类（可为null），使用@Nullable注解明确标记该字段可以为null，final修饰表示该字段在构造后不可修改
  public final @Nullable Integer col2; // 定义第二个列col2，类型为Integer包装类（可为null），使用@Nullable注解明确标记该字段可以为null，final修饰表示该字段在构造后不可修改
  public final int col3; // 定义第三个列col3，类型为int基本类型（不可为null），final修饰表示该字段在构造后不可修改，用于对比测试可空列的处理

  public NullableTest(@Nullable Integer col1, @Nullable Integer col2, // 构造方法，接收三个参数：col1和col2为可空的Integer类型，col3为不可空的int类型
      int col3) { // 构造方法参数续行，接收第三个参数col3
    this.col1 = col1; // 将传入的col1参数赋值给实例变量col1，初始化第一个可空列
    this.col2 = col2; // 将传入的col2参数赋值给实例变量col2，初始化第二个可空列
    this.col3 = col3; // 将传入的col3参数赋值给实例变量col3，初始化不可空列
  }

  @Override public String toString() { // 重写Object类的toString方法，用于返回对象的字符串表示，便于调试和日志输出
    return "DependentNullable [col1: " + col1 + ", col2: " + col2 + ", col3: " + col3 + "]"; // 返回格式化的字符串，包含所有三个列的名称和当前值，注意类名显示为DependentNullable（可能是历史遗留问题）
  }

  @Override public boolean equals(Object obj) { // 重写Object类的equals方法，用于比较两个NullableTest对象是否相等，基于所有三个列的值进行比较
    return obj == this // 首先检查obj是否就是当前对象引用，如果是则直接返回true（自反性）
        || obj instanceof NullableTest // 如果不是同一个引用，检查obj是否是NullableTest类的实例
        && Objects.equals(col1, ((NullableTest) obj).col1) // 使用Objects.equals方法比较col1字段，能正确处理null值的情况（两个null返回true）
        && Objects.equals(col2, ((NullableTest) obj).col2) // 使用Objects.equals方法比较col2字段，能正确处理null值的情况
        && col3 == ((NullableTest) obj).col3; // 使用==运算符比较col3字段（基本类型int），直接比较值
  }

  @Override public int hashCode() { // 重写Object类的hashCode方法，用于计算对象的哈希码，与equals方法保持一致，确保相等的对象具有相同的哈希码
    return Objects.hash(col1, col2, col3); // 使用Objects.hash方法基于所有三个字段计算哈希码，能正确处理null值，保证equals相等的对象hashCode也相等
  }
}
