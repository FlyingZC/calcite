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
 */ // Apache许可证头部，声明版权和使用许可
package org.apache.calcite.adapter.java; // 声明包名，位于org.apache.calcite.adapter.java包下，这是Calcite的Java适配器包

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型，是Calcite中用于描述SQL类型的抽象表示
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，这是Calcite中用于创建和管理关系数据类型的工厂接口

import java.lang.reflect.Type; // 导入Type接口，这是Java反射API中表示类型的顶级接口，可以表示类、接口、数组、基本类型等
import java.util.List; // 导入List接口，用于存储类型列表

/**
 * Type factory that can register Java classes as record types.
 */ // JavaTypeFactory是一个类型工厂接口，它扩展了RelDataTypeFactory，增加了将Java类注册为记录类型的能力
public interface JavaTypeFactory extends RelDataTypeFactory { // 定义JavaTypeFactory接口，继承自RelDataTypeFactory，表示这是一个专门处理Java类型的工厂
  /**
   * Creates a record type based upon the public fields of a Java class.
   *
   * @param clazz Java class
   * @return Record type that remembers its Java class
   */ // 方法注释：根据Java类的公共字段创建一个记录类型（结构化类型）
  RelDataType createStructType(Class clazz); // 创建结构化类型的方法，参数clazz是Java类对象，返回一个RelDataType对象，这个对象会记住对应的Java类

  /**
   * Creates a type, deducing whether a record, scalar or primitive type
   * is needed.
   *
   * @param type Java type, such as a {@link Class}
   * @return Record or scalar type
   */ // 方法注释：创建一个类型，自动推断是需要记录类型、标量类型还是原始类型
  RelDataType createType(Type type); // 创建类型的方法，参数type是Java类型（如Class对象），返回相应的记录类型或标量类型

  Type getJavaClass(RelDataType type); // 根据关系数据类型获取对应的Java类对象，参数type是RelDataType对象，返回对应的Java Type对象

  /** Creates a synthetic Java class whose fields have the given Java
   * types. */ // 方法注释：创建一个合成的Java类，该类的字段具有给定的Java类型
  Type createSyntheticType(List<Type> types); // 创建合成类型的方法，参数types是Java类型列表，返回一个合成的Type对象，这个Type代表一个包含指定类型字段的新类

  /** Converts a type in Java format to a SQL-oriented type. */ // 方法注释：将Java格式的类型转换为SQL导向的类型
  RelDataType toSql(RelDataType type); // 类型转换方法，参数type是Java格式的RelDataType对象，返回转换为SQL导向的RelDataType对象
} // 接口结束
