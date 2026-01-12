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
package org.apache.calcite.linq4j; // 声明包名，该类属于org.apache.calcite.linq4j包，是Calcite的LINQ4J实现的核心包

import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，用于表示LINQ查询的表达式树

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的字段或方法返回值

import java.lang.reflect.Type; // 导入Type类，用于表示Java类型
import java.util.Iterator; // 导入Iterator接口，用于遍历查询结果的迭代器

/**
 * Skeleton implementation of {@link Queryable}. // Queryable接口的骨架实现类，提供了可查询集合的基础功能
 *
 * <p>The default implementation of {@link #enumerator()} calls the provider's
 * {@link QueryProvider#executeQuery(Queryable)} method, but the derived class
 * can override. // enumerator()方法的默认实现调用provider的executeQuery方法来执行查询，但派生类可以重写此方法
 *
 * @param <TSource> Element type // 泛型参数TSource表示查询集合中元素的类型
 */
public abstract class BaseQueryable<TSource> // 定义抽象类BaseQueryable，继承自AbstractQueryable<TSource>
    extends AbstractQueryable<TSource> { // 继承抽象查询基类，获得查询的基本功能
  protected final QueryProvider provider; // 查询提供者对象，负责执行查询操作，是整个查询系统的核心执行引擎
  protected final Type elementType; // 元素类型，表示查询结果中每个元素的Java类型信息
  protected final @Nullable Expression expression; // 查询表达式树，表示当前查询的LINQ表达式，可能为null

  protected BaseQueryable(QueryProvider provider, Type elementType, // 构造方法，初始化查询对象，接收查询提供者、元素类型和表达式
      @Nullable Expression expression) { // 参数expression是可选的查询表达式，可以为null
    this.provider = provider; // 将传入的查询提供者赋值给成员变量provider
    this.elementType = elementType; // 将传入的元素类型赋值给成员变量elementType
    this.expression = expression; // 将传入的表达式赋值给成员变量expression
  }

  @Override public QueryProvider getProvider() { // 重写接口方法，获取当前查询对象的查询提供者
    return provider; // 返回成员变量provider，即查询提供者对象
  }

  @Override public Type getElementType() { // 重写接口方法，获取查询结果中元素的类型
    return elementType; // 返回成员变量elementType，即元素的Java类型信息
  }

  @Override public @Nullable Expression getExpression() { // 重写接口方法，获取当前查询的表达式树
    return expression; // 返回成员变量expression，即查询的LINQ表达式树，可能为null
  }

  @Override public Iterator<TSource> iterator() { // 重写Iterable接口方法，返回用于遍历查询结果的迭代器，支持for-each循环
    return Linq4j.enumeratorIterator(enumerator()); // 调用Linq4j工具方法将枚举器转换为标准的Java迭代器
  }

  @Override public Enumerator<TSource> enumerator() { // 重写接口方法，返回用于枚举查询结果的枚举器，这是LINQ4J的核心方法
    return provider.executeQuery(this); // 委托给查询提供者执行查询，传入当前查询对象this，返回枚举器
  }
}
