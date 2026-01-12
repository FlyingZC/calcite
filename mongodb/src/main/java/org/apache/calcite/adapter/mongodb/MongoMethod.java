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
package org.apache.calcite.adapter.mongodb; // 声明包名，该类属于Calcite的MongoDB适配器模块

import org.apache.calcite.linq4j.tree.Types; // 导入Calcite LINQ4J工具类，用于反射查找方法

import com.google.common.collect.ImmutableMap; // 导入Guava的不可变Map类，用于构建方法映射

import java.lang.reflect.Method; // 导入Java反射Method类，用于表示方法对象
import java.util.List; // 导入Java集合List类，用于表示列表类型

/**
 * Builtin methods in the MongoDB adapter. // MongoDB适配器中内置的方法枚举类
 * // 这个枚举类定义了MongoDB适配器支持的所有内置方法，用于将Calcite的查询操作映射到MongoDB的查询操作
 * // 主要包括find（查询）和aggregate（聚合）两种操作类型
 * // 通过枚举类型可以方便地管理这些方法，并提供方法到枚举值的映射关系
 */
public enum MongoMethod { // 定义MongoMethod枚举类，每个枚举值代表一个MongoDB内置方法
  MONGO_QUERYABLE_FIND(MongoTable.MongoQueryable.class, "find", String.class, // 枚举值：MongoDB的find查询方法，用于执行查询操作
      String.class, List.class), // 方法参数类型：第一个String是数据库名，第二个String是集合名，List是查询条件列表
  MONGO_QUERYABLE_AGGREGATE(MongoTable.MongoQueryable.class, "aggregate", // 枚举值：MongoDB的aggregate聚合方法，用于执行聚合操作
      List.class, List.class); // 方法参数类型：第一个List是聚合管道操作列表，第二个List是结果类型列表

  @SuppressWarnings("ImmutableEnumChecker") // 抑制编译器警告，允许枚举中包含非final字段
  public final Method method; // 成员变量：存储Java反射的Method对象，表示该枚举值对应的实际Java方法

  public static final ImmutableMap<Method, MongoMethod> MAP; // 静态成员变量：不可变Map，存储Method到MongoMethod的映射关系，用于反向查找

  static { // 静态初始化块，在类加载时执行，用于构建方法映射表
    final ImmutableMap.Builder<Method, MongoMethod> builder = // 创建不可变Map的构建器对象
        ImmutableMap.builder(); // 调用builder()方法获取构建器实例
    for (MongoMethod value : MongoMethod.values()) { // 遍历枚举类的所有枚举值
      builder.put(value.method, value); // 将每个枚举值的method字段作为key，枚举值本身作为value放入构建器
    }
    MAP = builder.build(); // 调用build()方法构建不可变的Map并赋值给MAP静态变量
  }

  MongoMethod(Class clazz, String methodName, Class... argumentTypes) { // 构造方法：接收类对象、方法名和参数类型数组
    this.method = Types.lookupMethod(clazz, methodName, argumentTypes); // 使用Types工具类通过反射查找指定类的方法，并赋值给method字段
  }
} // 枚举类结束
