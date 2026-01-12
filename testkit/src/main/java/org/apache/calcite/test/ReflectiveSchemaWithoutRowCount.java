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
// Apache许可证声明，指定该文件遵循Apache 2.0许可证，允许在特定条件下使用和分发
package org.apache.calcite.test; // 声明该类属于org.apache.calcite.test包，这是一个测试工具包

import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入ReflectiveSchema类，这是Calcite提供的基于反射的Schema适配器基类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值类型

/**
 * A ReflectiveSchema that does not return row count statistics. // 这是一个不返回行数统计信息的ReflectiveSchema
 * Intended to be used with tests written before row count statistics were supported // 旨在用于在行数统计功能被支持之前编写的测试
 * by ReflectiveSchema that rely on legacy behavior. // 这些测试依赖于旧的行为模式
 */
// 类作用说明：ReflectiveSchemaWithoutRowCount是ReflectiveSchema的一个特殊子类，专门用于测试场景
// 它的主要特点是重写了getRowCount方法，使其始终返回null，从而不提供任何行数统计信息
// 这在需要模拟早期ReflectiveSchema行为（不支持行数统计）的测试中非常有用
// 行数统计是查询优化器进行成本估算的重要依据，通过不提供行数统计，可以测试优化器在没有统计信息时的行为
public class ReflectiveSchemaWithoutRowCount extends ReflectiveSchema  { // 继承自ReflectiveSchema类，获得基于反射创建表结构的能力
  /**
   * Creates a ReflectiveSchema. // 创建一个ReflectiveSchema实例
   *
   * @param target Object whose fields will be sub-objects of the schema // 参数target是一个对象，其字段将成为schema的子对象
   */
  // 构造方法作用说明：通过传入一个目标对象来创建ReflectiveSchemaWithoutRowCount实例
  // 目标对象的公共字段将被反射扫描并转换为schema中的表
  // 例如，如果target对象有一个名为"employees"的公共字段，该字段会被转换为一个名为"employees"的表
  // 每个表的结构由对应字段类型的公共属性决定，这些属性会映射为表的列
  // 这是一个典型的反射机制应用，允许通过简单的Java对象来定义数据库schema结构
  public ReflectiveSchemaWithoutRowCount(Object target) { // 构造方法，接收一个目标对象作为参数
    super(target); // 调用父类ReflectiveSchema的构造方法，将目标对象传递给父类进行初始化
    // 父类会保存target对象的引用，并在后续通过反射扫描target的字段来构建schema
  }

  @Override protected @Nullable Double getRowCount(Object o) { // 重写父类的getRowCount方法，接收一个对象参数并返回可能为null的Double值
    return null; // 始终返回null，表示不提供行数统计信息
    // 这个重写是本类的核心功能，通过返回null来禁用行数统计
    // 在正常的ReflectiveSchema中，此方法会尝试估算表的行数，用于查询优化
    // 但在本类中，通过返回null，强制优化器在没有行数统计的情况下工作
    // 参数o通常代表一个表对象，但在本实现中被忽略
    // 返回值null告诉Calcite优化器无法获取该表的行数信息，必须使用其他启发式规则进行成本估算
  }
}
