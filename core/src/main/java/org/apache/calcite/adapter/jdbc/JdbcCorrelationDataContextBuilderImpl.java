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
package org.apache.calcite.adapter.jdbc; // 包声明：JDBC适配器包，包含与JDBC数据源相关的适配器类

import org.apache.calcite.DataContext; // 导入：Calcite的数据上下文接口，提供执行查询时的运行时上下文信息
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor; // 导入：可枚举关系表达式实现器，用于生成可执行的代码
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入：代码块构建器，用于构建Java代码块表达式
import org.apache.calcite.linq4j.tree.Expression; // 导入：表达式基类，表示LINQ4J中的表达式树
import org.apache.calcite.linq4j.tree.Expressions; // 导入：表达式工具类，提供创建各种表达式的静态方法
import org.apache.calcite.linq4j.tree.Types; // 导入：类型工具类，提供反射相关的类型操作方法
import org.apache.calcite.rel.core.CorrelationId; // 导入：关联ID，用于标识子查询中的关联变量

import com.google.common.collect.ImmutableList; // 导入：Google Guava的不可变列表，提供线程安全的列表构建

import java.lang.reflect.Constructor; // 导入：构造函数反射类，用于通过反射获取构造函数
import java.lang.reflect.Type; // 导入：类型反射类，表示Java中的类型信息

/**
 * JdbcCorrelationDataContext的实现类，用于构建JDBC关联数据上下文
 * 
 * 类的作用：
 * 这个类是JdbcCorrelationDataContextBuilder接口的实现，主要用于在处理包含关联子查询的SQL时，
 * 构建能够访问关联变量的数据上下文。当Calcite将包含子查询的逻辑计划转换为可执行代码时，
 * 子查询可能需要引用外部查询中的变量（关联变量），这个类负责收集这些关联变量的访问表达式，
 * 并最终构建一个JdbcCorrelationDataContext对象来封装这些变量。
 * 
 * 工作流程：
 * 1. 通过构造方法接收代码生成所需的上下文信息
 * 2. 通过add方法逐个添加关联变量的访问表达式
 * 3. 通过build方法构建最终的JdbcCorrelationDataContext表达式
 */
public class JdbcCorrelationDataContextBuilderImpl implements JdbcCorrelationDataContextBuilder { // 类定义：JDBC关联数据上下文构建器的实现类
  private static final Constructor NEW = // 成员变量：JdbcCorrelationDataContext类的构造函数的反射对象，用于通过反射创建实例
      Types.lookupConstructor(JdbcCorrelationDataContext.class, DataContext.class, Object[].class); // 查找JdbcCorrelationDataContext类中参数为(DataContext, Object[])的构造函数
  private final ImmutableList.Builder<Expression> parameters = new ImmutableList.Builder<>(); // 成员变量：表达式列表构建器，用于收集所有关联变量的访问表达式，最终构建为不可变列表
  private int offset = JdbcCorrelationDataContext.OFFSET; // 成员变量：偏移量计数器，用于跟踪当前添加的参数在参数数组中的索引位置，初始值为JdbcCorrelationDataContext.OFFSET
  private final EnumerableRelImplementor implementor; // 成员变量：可枚举关系表达式实现器，用于生成代码和获取关联变量的getter方法，在代码生成过程中起到核心作用
  private final BlockBuilder builder; // 成员变量：代码块构建器，用于构建Java代码块，是表达式树构建的基础工具
  private final Expression dataContext; // 成员变量：数据上下文表达式，表示原始的数据上下文对象，用于传递给最终的JdbcCorrelationDataContext

  public JdbcCorrelationDataContextBuilderImpl(EnumerableRelImplementor implementor, // 构造方法：创建JdbcCorrelationDataContextBuilderImpl实例
      BlockBuilder builder, Expression dataContext) { // 参数：builder-代码块构建器，dataContext-数据上下文表达式
    this.implementor = implementor; // 初始化：保存实现器引用，用于后续获取关联变量访问器
    this.builder = builder; // 初始化：保存代码块构建器引用，用于构建字段访问表达式
    this.dataContext = dataContext; // 初始化：保存数据上下文表达式，用于传递给最终的上下文对象
  }

  @Override public int add(CorrelationId id, int ordinal, Type type) { // 方法：添加一个关联变量到构建器中
    parameters.add(implementor.getCorrelVariableGetter(id.getName()).field(builder, ordinal, type)); // 实现细节：根据关联ID名称获取变量getter，然后构建字段访问表达式并添加到参数列表中
    return offset++; // 返回值：返回当前参数的偏移量，然后递增偏移量计数器，为下一个参数做准备
  }

  public Expression build() { // 方法：构建最终的JdbcCorrelationDataContext表达式
    return  Expressions.new_(NEW, dataContext, // 返回值：创建一个new表达式，调用JdbcCorrelationDataContext构造函数，传入数据上下文和参数数组
      Expressions.newArrayInit(Object.class, 1, parameters.build())); // 实现细节：创建Object类型的数组初始化表达式，从索引1开始（跳过索引0），填入所有收集的参数表达式
  }
}
