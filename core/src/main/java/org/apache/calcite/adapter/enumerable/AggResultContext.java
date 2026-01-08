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
// Apache许可证声明,说明该代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.enumerable; // 声明包名,该接口位于org.apache.calcite.adapter.enumerable包中,这是Calcite中可枚举适配器相关的包

import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类,用于表示LINQ表达式树中的表达式节点,是代码生成的基础构建块
import org.apache.calcite.rel.core.AggregateCall; // 导入AggregateCall类,表示聚合函数调用,包含聚合函数的类型、参数等信息

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解,用于标记返回值可能为null的方法

/**
 * Information for a call to // 提供了调用AggImplementor.implementResult方法时所需的信息上下文
 * {@link AggImplementor#implementResult(AggContext, AggResultContext)} // 该接口作为implementResult方法的第二个参数,传递聚合结果处理所需的所有上下文信息
 *
 * <p>Typically, the aggregation implementation will convert // 通常情况下,聚合实现会将累加器(accumulator)转换为聚合的最终结果值
 * {@link #accumulator()} to the resulting value of the aggregation.  The // accumulator()方法从父接口AggResetContext继承,返回累加器的表达式
 * implementation MUST NOT destroy the contents of {@link #accumulator()}. // 实现必须销毁累加器的内容,即不能修改累加器的原始值,因为累加器可能被后续操作使用
 */ // 该接口是聚合结果转换阶段的核心上下文接口,负责提供从累加器状态生成最终聚合结果所需的所有信息
public interface AggResultContext extends NestedBlockBuilder, AggResetContext { // 定义AggResultContext接口,继承自NestedBlockBuilder(嵌套代码块构建器)和AggResetContext(聚合重置上下文),使其具备代码构建和累加器访问能力
  /** Expression by which to reference the key upon which the values in the // 返回用于引用聚合键(key)的表达式,该键是分组聚合时使用的分组字段
   * accumulator were aggregated. Most aggregate functions depend on only the // 累加器中的值是根据该键进行聚合的,大多数聚合函数(如SUM、COUNT等)只依赖于累加器的值
   * accumulator, but quasi-aggregate functions such as GROUPING access at the // 但一些准聚合函数(如GROUPING函数)需要访问分组键本身,用于判断某个字段是否参与了分组
   * key. */ // GROUPING函数返回一个位掩码,标识哪些字段在GROUP BY中存在,这对于处理ROLLUP、CUBE等高级分组操作很重要
  @Nullable Expression key(); // 返回分组键的表达式,可能为null(当没有分组时),使用@Nullable注解标记,类型是Expression(LINQ表达式)

  /** Returns an expression that references the {@code i}th field of the key, // 返回引用分组键中第i个字段的表达式,该表达式会自动转换为适当的类型
   * cast to the appropriate type. */ // 当分组键包含多个字段时(如GROUP BY a, b),可以通过此方法访问特定字段,i从0开始计数
  Expression keyField(int i); // 方法参数i是字段的索引,返回值是Expression类型的表达式,表示访问该字段的表达式

  /** Returns the aggregate call. */ // 返回当前的聚合调用对象,包含聚合函数的完整信息
  AggregateCall call(); // 返回AggregateCall对象,该对象包含聚合函数类型(如SUM、AVG、COUNT等)、参数列表、是否distinct、过滤条件等信息

  /** Returns a {@code RexToLixTranslator} // 返回一个RexToLixTranslator对象,用于将Calcite的Rex表达式转换为LINQ表达式
   * suitable to transform the result. */ // 该转换器专门用于在结果转换阶段,将Rex表达式(关系表达式)转换为可执行的LINQ表达式(Expression)
  RexToLixTranslator resultTranslator(); // 返回RexToLixTranslator实例,该实例配置了当前聚合结果的转换规则和类型映射
} // 接口定义结束,该接口提供了聚合结果生成所需的全部上下文信息,包括分组键、聚合调用信息和表达式转换器
