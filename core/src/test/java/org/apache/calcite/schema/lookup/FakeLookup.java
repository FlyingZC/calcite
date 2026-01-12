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
package org.apache.calcite.schema.lookup; // 指定当前类所在的包路径，org.apache.calcite.schema.lookup 包包含了各种查找相关的类和接口

import org.apache.calcite.linq4j.function.Predicate1; // 导入 Predicate1 接口，这是一个函数式接口，用于定义一个接受一个参数并返回布尔值的谓词，常用于过滤操作

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 @Nullable 注解，用于标记可能为 null 的返回值或参数，帮助进行空值检查

import java.util.HashMap; // 导入 HashMap 类，这是 Java 集合框架中的哈希表实现，用于存储键值对映射
import java.util.Locale; // 导入 Locale 类，用于表示特定的地理、政治或文化区域，这里用于字符串大小写转换时的本地化处理
import java.util.Map; // 导入 Map 接口，这是 Java 集合框架中的映射接口，定义了键值对映射的基本操作
import java.util.Set; // 导入 Set 接口，这是 Java 集合框架中的集合接口，表示不包含重复元素的集合
import java.util.stream.Collectors; // 导入 Collectors 类，提供了各种有用的归约操作（如将流转换为集合）的静态方法

/**
 * Simple utility class to test other implementations of Lookup. // 这是一个简单的工具类，用于测试其他 Lookup 接口的实现，它提供了一个基于 Map 的简单查找实现
 * 
 * FakeLookup 是一个用于测试目的的 Lookup 接口实现类，它使用内部 Map 来存储键值对映射， // 类的作用说明
 * 提供了基本的查找功能，包括精确查找、忽略大小写查找和模式匹配查找。这个类主要用于 // 类的作用说明
 * 单元测试中，作为 Lookup 接口的模拟实现，帮助验证其他依赖 Lookup 接口的代码的正确性。 // 类的作用说明
 * 它支持泛型类型 String，即键和值都是 String 类型。 // 类的作用说明
 */
class FakeLookup implements Lookup<String> { // 定义 FakeLookup 类，它实现了 Lookup<String> 接口，表示这是一个字符串类型的查找器
  private final Map<String, String> map; // 成员变量：map 是一个不可变的 Map，用于存储键值对映射，键是字符串，值也是字符串，这个 Map 是区分大小写的
  private final Map<String, Named<String>> ignoreCaseMap; // 成员变量：ignoreCaseMap 是一个不可变的 Map，用于存储键值对映射，但键是转换为小写的，值是 Named<String> 对象，这个 Map 用于支持忽略大小写的查找

  FakeLookup(String... keyAndValues) { // 构造方法：接受可变参数 keyAndValues，这是一个字符串数组，包含交替的键和值，用于初始化查找器
    this.map = new HashMap<>(); // 初始化 map 成员变量，创建一个新的 HashMap 实例，用于存储键值对
    for (int i = 0; i < keyAndValues.length - 1; i += 2) { // 遍历 keyAndValues 数组，每次步进 2，因为数组中键和值是交替出现的
      map.put(keyAndValues[i], keyAndValues[i + 1]); // 将键和值放入 map 中，keyAndValues[i] 是键，keyAndValues[i + 1] 是对应的值
    } // 循环结束，此时 map 中包含了所有的键值对
    this.ignoreCaseMap = this.map.entrySet().stream() // 初始化 ignoreCaseMap 成员变量，通过流式处理 map 的所有条目
        .collect( // 将流中的元素收集到一个新的 Map 中
            Collectors.toMap( // 使用 Collectors.toMap 方法将流转换为 Map
                entry -> entry.getKey().toLowerCase(Locale.ROOT), // Map 的键是原键转换为小写的形式，使用 Locale.ROOT 确保转换的一致性
                entry -> new Named<>(entry.getKey(), entry.getValue()))); // Map 的值是一个 Named 对象，包含原始键和对应的值，Named 对象可以保存原始键的大小写形式
  } // 构造方法结束，此时 ignoreCaseMap 中包含了所有键的小写形式到 Named 对象的映射

  @Override public @Nullable String get(final String name) { // 方法：根据名称精确查找对应的值，@Override 表示这是接口方法的实现，@Nullable 表示返回值可能为 null
    return map.get(name); // 从 map 中获取指定名称对应的值，如果名称不存在则返回 null
  } // 方法结束，返回查找到的值或 null

  @Override public @Nullable Named<String> getIgnoreCase(final String name) { // 方法：根据名称忽略大小写查找对应的 Named 对象，@Override 表示这是接口方法的实现，@Nullable 表示返回值可能为 null
    return ignoreCaseMap.get(name.toLowerCase(Locale.ROOT)); // 将输入的名称转换为小写，然后从 ignoreCaseMap 中查找对应的 Named 对象，如果不存在则返回 null
  } // 方法结束，返回查找到的 Named 对象或 null，Named 对象中包含了原始键和对应的值

  @Override public Set<String> getNames(final LikePattern pattern) { // 方法：根据 LikePattern 模式匹配查找所有符合条件的名称集合，@Override 表示这是接口方法的实现
    Predicate1<String> predicate = pattern.matcher(); // 从 LikePattern 对象中获取一个 Predicate1 谓词，这个谓词可以判断一个字符串是否匹配给定的模式
    return map.keySet().stream() // 获取 map 的所有键的集合，并将其转换为流
        .filter(predicate::apply) // 使用谓词过滤流中的元素，只保留匹配模式的键
        .collect(Collectors.toSet()); // 将过滤后的流收集到一个 Set 集合中并返回
  } // 方法结束，返回所有匹配模式的名称集合
} // 类定义结束
