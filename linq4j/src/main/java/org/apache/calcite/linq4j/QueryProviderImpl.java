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
// Apache许可证声明，允许在Apache 2.0许可证下使用本代码
package org.apache.calcite.linq4j; // 包声明，本类位于org.apache.calcite.linq4j包中，属于LINQ4J框架的核心包

import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，用于表示LINQ查询的表达式树

import java.lang.reflect.Type; // 导入Type类，用于表示Java类型

/**
 * Partial implementation of {@link QueryProvider}.
 * QueryProvider接口的部分实现类，提供了查询提供者的基础功能
 * 
 * <p>Derived class needs to implement {@link #executeQuery}.
 * 派生类需要实现executeQuery方法来完成具体的查询执行逻辑
 * 
 * 这个类是Calcite LINQ4J框架中QueryProvider接口的抽象实现，负责创建和管理可查询对象
 * QueryProvider是LINQ(语言集成查询)模式的核心接口，类似于.NET中的IQueryable提供者
 * 它的主要职责是：
 * 1. 创建可查询对象(Queryable)
 * 2. 执行查询表达式并返回结果
 * 3. 管理查询的上下文和环境
 * 
 * 这个抽象类提供了createQuery方法的默认实现，但将execute方法标记为不支持操作
 * 具体的查询执行逻辑由子类实现，通常是通过实现executeQuery方法来完成
 */
public abstract class QueryProviderImpl implements QueryProvider { // 抽象类，实现QueryProvider接口，提供查询提供者的基础功能
  /**
   * Creates a QueryProviderImpl.
   * 创建QueryProviderImpl实例的构造方法
   * 
   * 这是一个受保护的构造方法，只能被子类调用
   * 构造方法调用父类(Object)的构造方法，完成基本初始化
   */
  protected QueryProviderImpl() { // 受保护的构造方法，确保只能通过子类创建实例
    super(); // 调用父类Object的构造方法，完成基本初始化
  }

  // 根据表达式和行类型(Class类型)创建可查询对象
  // 这个方法实现了QueryProvider接口的createQuery方法
  // 参数expression: 表示查询逻辑的表达式树，包含查询的完整定义
  // 参数rowType: 查询结果中每一行的类型，使用Class<T>表示，支持泛型
  // 返回值: 返回一个Queryable<T>对象，代表可执行的查询
  // 实现逻辑: 创建一个QueryableImpl实例，将当前provider、行类型和表达式传递给它
  @Override public <T> Queryable<T> createQuery(Expression expression, Class<T> rowType) { // 重写接口方法，创建基于Class类型的可查询对象
    return new QueryableImpl<>(this, rowType, expression); // 创建并返回QueryableImpl实例，封装了查询提供者、元素类型和表达式
  }

  // 根据表达式和行类型(Type类型)创建可查询对象
  // 这个方法是createQuery的另一个重载版本，区别在于行类型参数使用Type而不是Class
  // 参数expression: 表示查询逻辑的表达式树
  // 参数rowType: 查询结果中每一行的类型，使用Type表示，比Class更灵活，可以支持泛型类型
  // 返回值: 返回一个Queryable<T>对象
  // 实现逻辑: 同样创建QueryableImpl实例，但使用Type类型的rowType参数
  @Override public <T> Queryable<T> createQuery(Expression expression, Type rowType) { // 重写接口方法，创建基于Type类型的可查询对象
    return new QueryableImpl<>(this, rowType, expression); // 创建并返回QueryableImpl实例，封装了查询提供者、元素类型和表达式
  }

  // 执行表达式查询并返回结果（Class类型版本）
  // 这个方法实现了QueryProvider接口的execute方法
  // 参数expression: 要执行的表达式树
  // 参数type: 期望的返回结果类型，使用Class<T>表示
  // 返回值: 查询执行结果，类型为T
  // 注意: 这个方法抛出UnsupportedOperationException，表示不支持直接执行
  // 原因: QueryProviderImpl是一个抽象基类，具体的查询执行逻辑应该由子类通过executeQuery方法实现
  @Override public <T> T execute(Expression expression, Class<T> type) { // 重写接口方法，执行查询并返回Class类型的结果
    throw new UnsupportedOperationException(); // 抛出不支持操作异常，因为具体的执行逻辑由子类实现
  }

  // 执行表达式查询并返回结果（Type类型版本）
  // 这个方法是execute的另一个重载版本，使用Type而不是Class作为返回类型参数
  // 参数expression: 要执行的表达式树
  // 参数type: 期望的返回结果类型，使用Type表示，比Class更灵活
  // 返回值: 查询执行结果，类型为T
  // 注意: 同样抛出UnsupportedOperationException，不支持直接执行
  @Override public <T> T execute(Expression expression, Type type) { // 重写接口方法，执行查询并返回Type类型的结果
    throw new UnsupportedOperationException(); // 抛出不支持操作异常，因为具体的执行逻辑由子类实现
  }

  /**
   * Binds an expression to this query provider.
   * 将表达式绑定到这个查询提供者，创建一个可查询对象
   * 
   * QueryableImpl是QueryProviderImpl的内部静态类，实现了BaseQueryable<T>
   * 它的作用是将查询表达式与查询提供者绑定，形成一个可以执行查询的可查询对象
   * 
   * 这个类的主要职责：
   * 1. 持有对QueryProviderImpl的引用，以便在需要时执行查询
   * 2. 存储元素类型信息，用于类型安全的查询操作
   * 3. 封装查询表达式，作为查询的表示
   * 4. 提供toString方法，用于调试时显示查询表达式
   * 
   * @param <T> element type
   * 泛型参数T表示可查询对象中元素的类型
   */
  public static class QueryableImpl<T> extends BaseQueryable<T> { // 内部静态类，继承BaseQueryable，实现可查询对象的核心功能
    // QueryableImpl的构造方法
    // 参数provider: 查询提供者实例，用于执行查询
    // 参数elementType: 元素的类型信息，使用Type表示，支持泛型类型
    // 参数expression: 查询表达式树，包含查询的完整逻辑
    // 实现逻辑: 调用父类BaseQueryable的构造方法，将provider、elementType和expression传递给父类
    public QueryableImpl(QueryProviderImpl provider, Type elementType, // 构造方法，初始化可查询对象
        Expression expression) { // 参数expression: 查询表达式
      super(provider, elementType, expression); // 调用父类构造方法，完成初始化
    }

    // 重写toString方法，用于调试和日志输出
    // 返回值: 返回一个字符串，包含查询表达式的信息
    // 实现逻辑: 将表达式转换为字符串形式，便于查看查询内容
    @Override public String toString() { // 重写toString方法，提供可查询对象的字符串表示
      return "Queryable(expr=" + expression + ")"; // 返回包含表达式信息的字符串，格式为"Queryable(expr=...)"
    }
  }
}
