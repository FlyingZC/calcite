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
// Apache License 许可证头部，声明该代码遵循 Apache 2.0 开源协议
package org.apache.calcite.linq4j; // 定义包名，该接口属于 org.apache.calcite.linq4j 包，这是 Calcite LINQ4J 框架的核心包

import org.checkerframework.framework.qual.Covariant; // 导入 CheckerFramework 的 Covariant 注解，用于标记协变类型参数

/**
 * Represents a collection of objects that have a common key.
 * // 表示一组具有相同键的对象集合，这是 LINQ(Language Integrated Query) 中 GroupBy 操作的核心概念
 * // 在 SQL 中对应 GROUP BY 子句，将数据按照某个键值进行分组，每个分组包含该键值对应的所有元素
 * // 例如：将学生按班级分组，每个分组包含该班级的所有学生
 *
 * @param <K> Key type // 泛型参数 K 表示分组的键类型，例如班级号、部门ID等
 * @param <V> Element type // 泛型参数 V 表示分组中元素的类型，例如学生对象、员工对象等
 */
@Covariant(0) // CheckerFramework 注解，标记第一个泛型参数 K 是协变的，允许 Grouping<子类K, V> 赋值给 Grouping<父类K, V>
public interface Grouping<K, V> extends Enumerable<V> { // 定义 Grouping 接口，继承自 Enumerable<V> 接口，使其具备可枚举能力
  // 接口继承自 Enumerable<V>，意味着 Grouping 实例可以像集合一样遍历其中的元素（V 类型）
  // 这样设计使得 Grouping 既是一个分组（有键），又是一个可遍历的集合（包含该键对应的所有元素）
  
  /**
   * Gets the key of this Grouping.
   * // 获取当前分组的键值，这是该分组中所有元素共有的属性
   * // 例如：如果按班级分组学生，这个方法返回班级号
   */
  K getKey(); // 定义获取分组键的方法，返回类型为泛型 K，每个 Grouping 实现必须提供该方法以返回分组的键
} // 接口定义结束
