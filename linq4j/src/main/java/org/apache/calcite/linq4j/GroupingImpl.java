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
package org.apache.calcite.linq4j; // 定义包名，该类属于 org.apache.calcite.linq4j 包，是 Calcite LINQ4J 框架的一部分

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 CheckerFramework 的可空注解，用于标记可能为 null 的参数

import java.util.List; // 导入 Java 集合框架的 List 接口，用于存储分组后的值列表
import java.util.Map; // 导入 Java 集合框架的 Map 接口，用于实现 Map.Entry 接口

import static java.util.Objects.requireNonNull; // 导入 Objects 类的静态方法，用于参数非空校验

/**
 * Implementation of {@link Grouping}. // Grouping 接口的实现类，用于表示分组操作的结果
 * // 该类实现了 Grouping 接口，提供了分组键和对应值的集合，同时实现了 Map.Entry 接口，使其可以作为 Map 的条目使用
 * // 在 LINQ 查询中，GroupBy 操作会产生多个 Grouping 对象，每个对象包含一个分组键和该键对应的所有元素
 * // 该类继承自 AbstractEnumerable，使其支持枚举操作，可以遍历分组中的所有值
 *
 * @param <K> Key type // 泛型参数 K 表示分组键的类型，例如在按部门分组员工时，K 可能是 String 类型表示部门名称
 * @param <V> Value type // 泛型参数 V 表示分组值的类型，例如在按部门分组员工时，V 可能是 Employee 类型表示员工对象
 */
@SuppressWarnings("type.argument.type.incompatible") // 抑制类型参数不兼容的警告，这是由于泛型擦除导致的类型检查问题
class GroupingImpl<K extends Object, V> extends AbstractEnumerable<V> // 定义 GroupingImpl 类，继承自 AbstractEnumerable<V>，使其具备可枚举的能力
    implements Grouping<K, V>, Map.Entry<K, Enumerable<V>> { // 实现 Grouping<K,V> 接口表示分组，实现 Map.Entry<K,Enumerable<V>> 接口使其可作为 Map 条目
  private final K key; // 成员变量：存储分组的键，使用 final 修饰表示键不可变，例如部门名称、日期等分组依据
  private final List<V> values; // 成员变量：存储该分组键对应的所有值列表，使用 final 修饰表示列表引用不可变，例如某个部门的所有员工

  // 构造方法：创建 GroupingImpl 实例，初始化分组键和值列表
  GroupingImpl(K key, List<V> values) { // 构造函数接收分组键 key 和值列表 values 两个参数
    this.key = requireNonNull(key, "key"); // 使用 requireNonNull 方法校验 key 参数不为 null，如果为 null 则抛出 NullPointerException，并赋值给成员变量 key
    this.values = requireNonNull(values, "values"); // 使用 requireNonNull 方法校验 values 参数不为 null，如果为 null 则抛出 NullPointerException，并赋值给成员变量 values
  }

  // 重写 toString 方法，返回分组对象的字符串表示，格式为 "key: values"
  @Override public String toString() { // 重写 Object 类的 toString 方法，用于返回分组对象的字符串表示
    return key + ": " + values; // 返回格式化的字符串，例如 "研发部: [张三, 李四, 王五]"，便于调试和日志输出
  }

  /** {@inheritDoc} // 继承父类或接口的文档注释，表示该方法实现了接口或父类的规范
   *
   * <p>Computes hash code consistent with // 计算哈希码，与 java.util.Map.Entry.hashCode() 保持一致
   * {@link java.util.Map.Entry#hashCode()}. */ // 确保当 GroupingImpl 用作 Map.Entry 时，其哈希码计算方式与标准 Map.Entry 一致
  @Override public int hashCode() { // 重写 Object 类的 hashCode 方法，用于计算分组对象的哈希码
    return key.hashCode() ^ values.hashCode(); // 使用异或运算组合 key 和 values 的哈希码，这是 Map.Entry 接口的标准哈希码计算方式，确保相同的分组对象具有相同的哈希码
  }

  // 重写 equals 方法，用于比较两个 GroupingImpl 对象是否相等
  @Override public boolean equals(@Nullable Object obj) { // 重写 Object 类的 equals 方法，用于判断当前对象与参数 obj 是否相等
    return obj instanceof GroupingImpl // 首先检查 obj 是否是 GroupingImpl 类的实例，如果不是则直接返回 false
           && key.equals(((GroupingImpl) obj).key) // 如果 obj 是 GroupingImpl 实例，则比较两者的 key 是否相等
           && values.equals(((GroupingImpl) obj).values); // 同时比较两者的 values 列表是否相等，只有 key 和 values 都相等时才返回 true
  }

  // implement Map.Entry // 实现 Map.Entry 接口的 getValue 方法
  @Override public Enumerable<V> getValue() { // 实现 Map.Entry 接口的 getValue 方法，返回该条目的值
    return Linq4j.asEnumerable(values); // 将 values 列表转换为 Enumerable 对象返回，使其支持 LINQ 风格的查询操作
  }

  // implement Map.Entry // 实现 Map.Entry 接口的 setValue 方法
  @Override public Enumerable<V> setValue(Enumerable<V> value) { // 实现 Map.Entry 接口的 setValue 方法，用于设置该条目的值
    // immutable // 该对象是不可变的，不支持修改操作
    throw new UnsupportedOperationException(); // 抛出 UnsupportedOperationException 异常，表示不支持设置值的操作，保持对象的不可变性
  }

  // implement Map.Entry // 实现 Map.Entry 接口的 getKey 方法
  // implement Grouping // 实现 Grouping 接口的 getKey 方法
  @Override public K getKey() { // 实现 Map.Entry 和 Grouping 接口的 getKey 方法，返回分组的键
    return key; // 直接返回成员变量 key，即分组的键
  }

  // 实现 Enumerable 接口的 enumerator 方法，返回枚举器用于遍历分组中的值
  @Override public Enumerator<V> enumerator() { // 实现 Enumerable 接口的 enumerator 方法，返回一个枚举器对象
    return Linq4j.enumerator(values); // 将 values 列表转换为 Enumerator 对象返回，用于遍历分组中的所有值
  }
} // 类定义结束
