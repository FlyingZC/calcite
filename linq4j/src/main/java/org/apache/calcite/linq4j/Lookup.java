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
// 声明包名：org.apache.calcite.linq4j，表示该类属于Calcite的LINQ4J模块
package org.apache.calcite.linq4j;  // LINQ4J是Calcite对.NET LINQ的Java实现

// 导入Function2类：表示接受两个参数并返回一个结果的函数接口
import org.apache.calcite.linq4j.function.Function2;  // 用于applyResultSelector方法中的结果选择器

// 导入Map接口：Java标准库的映射接口，用于键值对存储
import java.util.Map;  // Lookup接口继承自Map，因此需要导入

/**
 * Represents a collection of keys each mapped to one or more values.
 * 表示一个键映射到多个值的集合，类似于多值映射（multimap）
 * Lookup是LINQ4J中用于分组操作的核心接口，它将键映射到一个或多个值
 * 例如：对学生按班级分组，班级是键，学生集合是值
 * 
 * 该接口同时继承了两个接口：
 * 1. Map<K, Enumerable<V>>：表示这是一个映射，键类型为K，值类型为Enumerable<V>（可枚举的值集合）
 * 2. Enumerable<Grouping<K, V>>：表示这个Lookup本身也是一个可枚举对象，可以枚举出所有的分组（Grouping）
 *
 * @param <K> Key type - 键的类型，例如班级名称
 * @param <V> Value type - 值的类型，例如学生对象
 */
public interface Lookup<K, V>  // 定义泛型接口Lookup，K表示键类型，V表示值类型
    extends Map<K, Enumerable<V>>, Enumerable<Grouping<K, V>> {  // 继承Map接口（键到值集合的映射）和Enumerable接口（可枚举的分组集合）
  /**
   * Applies a transform function to each key and its associated values and
   * returns the results.
   * 对每个键及其关联的值应用转换函数，并返回转换后的结果集合
   * 
   * 该方法是Lookup接口的核心功能之一，允许用户对每个分组进行自定义的转换处理
   * 例如：将班级和该班级的所有学生转换为一个包含班级统计信息的对象
   * 
   * @param resultSelector Result selector - 结果选择器函数，接受键和值集合作为参数，返回转换后的结果
   *        这是一个Function2接口，接受两个参数：K类型的键和Enumerable<V>类型的值集合，返回TResult类型的结果
   * @param <TResult> Result type - 转换结果的类型，可以是任意类型
   *        例如：统计结果对象、字符串、数字等
   *
   * @return Enumerable over results - 返回包含所有转换结果的Enumerable集合
   *         每个元素都是对原分组应用resultSelector函数后的结果
   */
  <TResult> Enumerable<TResult> applyResultSelector(  // 定义泛型方法，TResult是转换结果的类型
      Function2<K, Enumerable<V>, TResult> resultSelector);  // 参数：结果选择器函数，接受键和值集合，返回转换后的结果
}  // 接口定义结束
