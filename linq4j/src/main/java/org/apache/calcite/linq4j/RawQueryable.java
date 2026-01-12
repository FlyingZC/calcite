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
package org.apache.calcite.linq4j; // 定义包名，该类属于org.apache.calcite.linq4j包，这是Calcite的LINQ4J模块

import org.apache.calcite.linq4j.tree.Expression; // 导入表达式树类，用于表示LINQ查询的表达式树结构

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的返回值
import org.checkerframework.framework.qual.Covariant; // 导入协变注解，用于支持泛型的协变类型

import java.lang.reflect.Type; // 导入Type类，用于表示Java类型信息

/**
 * Core methods that define a {@link Queryable}. // 定义Queryable的核心方法，Queryable是可查询集合的接口
 *
 * <p>The other methods in {@link Queryable}, defined in // Queryable中的其他方法定义在ExtendedQueryable中
 * {@link ExtendedQueryable}, can easily be implemented by calling the // 可以通过调用Extensions中的对应静态方法轻松实现
 * corresponding static methods in {@link Extensions}.
 *
 * @param <T> Element type // 泛型参数T，表示元素的类型
 */
@Covariant(0) // 协变注解，表示该接口的第一个泛型参数支持协变（即可以使用子类型）
public interface RawQueryable<T> extends Enumerable<T> { // 定义RawQueryable接口，继承自Enumerable<T>，表示可查询的数据源
  /**
   * Gets the type of the element(s) that are returned when the expression // 获取当与此Queryable关联的表达式树执行时返回的元素类型
   * tree associated with this Queryable is executed.
   */
  Type getElementType(); // 获取元素类型的方法，返回Type对象表示元素的Java类型

  /**
   * Gets the expression tree that is associated with this Queryable. // 获取与此Queryable关联的表达式树
   *
   * @return null if the expression is not available // 如果表达式不可用则返回null
   */
  @Nullable Expression getExpression(); // 获取表达式树的方法，返回Expression对象，可能为null

  /**
   * Gets the query provider that is associated with this data source. // 获取与此数据源关联的查询提供者
   */
  QueryProvider getProvider(); // 获取查询提供者的方法，返回QueryProvider对象，负责执行查询
}
