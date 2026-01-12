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
package org.apache.calcite.linq4j.tree; // 声明当前类所在的包路径，位于linq4j.tree包下，这是Calcite中处理表达式树的核心包

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的注解，用于标记参数可以为null，帮助进行空值检查

import java.lang.reflect.Array; // 导入Java反射API中的Array类，用于在运行时获取数组的长度
import java.lang.reflect.Type; // 导入Java反射API中的Type接口，表示Java中的类型
import java.util.Objects; // 导入Java工具类Objects，提供用于操作对象的工具方法，如equals和hashCode

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于检查参数是否为null，如果为null则抛出NullPointerException

/**
 * Length field of a RecordType. // 这是一个RecordType（记录类型）的长度字段，用于表示数组长度
 * 该类实现了Types.RecordField接口，表示记录类型中的一个字段，专门用于表示数组的长度属性
 * 在Calcite的LINQ4J实现中，当需要动态创建包含数组长度信息的记录类型时会使用此类
 * 例如：当处理数组类型的数据时，可以通过这个字段访问数组的长度
 */
@SuppressWarnings("rawtypes") // 抑制编译器关于使用原始类型（raw types）的警告，因为Class使用了泛型但这里使用原始类型
public class ArrayLengthRecordField implements Types.RecordField { // 定义ArrayLengthRecordField类，实现Types.RecordField接口，表示记录类型中的字段
  private final String fieldName; // 成员变量：字段名称，用于标识这个长度字段的名称，例如"length"
  private final Class clazz; // 成员变量：声明该字段的类，表示这个字段属于哪个类，用于记录字段所属的类型信息

  public ArrayLengthRecordField(String fieldName, Class clazz) { // 构造方法：创建一个ArrayLengthRecordField实例，参数fieldName指定字段名称，参数clazz指定字段所属的类
    this.fieldName = requireNonNull(fieldName, "fieldName"); // 初始化fieldName成员变量，使用requireNonNull检查fieldName参数是否为null，如果为null则抛出NullPointerException，提示参数名为"fieldName"
    this.clazz = requireNonNull(clazz, "clazz"); // 初始化clazz成员变量，使用requireNonNull检查clazz参数是否为null，如果为null则抛出NullPointerException，提示参数名为"clazz"
  } // 构造方法结束

  @Override public boolean nullable() { // 重写接口方法：判断该字段是否可空，返回boolean类型值
    return false; // 返回false，表示数组长度字段不可为null，因为数组长度总是存在且为int类型
  } // 方法结束，数组长度永远存在，不可能为null

  @Override public String getName() { // 重写接口方法：获取字段的名称，返回String类型
    return fieldName; // 返回成员变量fieldName的值，即该字段的名称
  } // 方法结束，返回字段名称

  @Override public Type getType() { // 重写接口方法：获取字段的类型，返回Type类型，表示该字段的数据类型
    return int.class; // 返回int.class，表示数组长度字段的类型是基本类型int
  } // 方法结束，数组长度总是整数类型

  @Override public int getModifiers() { // 重写接口方法：获取字段的修饰符，返回int类型的修饰符标志位
    return 0; // 返回0，表示该字段没有任何访问修饰符（如public、private、protected等）或其他修饰符
  } // 方法结束，返回0表示无特殊修饰符

  @Override public Object get(@Nullable Object o) { // 重写接口方法：从给定的对象中获取该字段的值，参数o是目标对象，@Nullable表示o可以为null，返回Object类型的字段值
    return Array.getLength(requireNonNull(o, "o")); // 使用Java反射API的Array.getLength方法获取数组长度，requireNonNull检查o是否为null，确保o不为null后再获取长度
  } // 方法结束，返回数组的长度（int值）

  @Override public Type getDeclaringClass() { // 重写接口方法：获取声明该字段的类，返回Type类型，表示声明该字段的类
    return clazz; // 返回成员变量clazz的值，即声明该字段的类
  } // 方法结束，返回字段所属的类

  @Override public boolean equals(@Nullable Object o) { // 重写Object类的equals方法，用于比较两个ArrayLengthRecordField对象是否相等，参数o是要比较的对象，@Nullable表示o可以为null
    if (this == o) { // 首先检查当前对象和参数o是否是同一个引用
      return true; // 如果是同一个引用，直接返回true，表示相等
    } // 判断结束，同一对象必然相等
    if (o == null || getClass() != o.getClass()) { // 检查o是否为null，或者o的类是否与当前对象的类不同
      return false; // 如果o为null或类不相同，返回false，表示不相等
    } // 判断结束，null或不同类型不相等

    ArrayLengthRecordField that = (ArrayLengthRecordField) o; // 将参数o强制转换为ArrayLengthRecordField类型，赋值给that变量
    return clazz.equals(that.clazz) // 比较当前对象的clazz成员变量和that对象的clazz成员变量是否相等
        && fieldName.equals(that.fieldName); // 同时比较当前对象的fieldName成员变量和that对象的fieldName成员变量是否相等，两个条件都满足时才返回true
  } // 方法结束，通过比较clazz和fieldName来判断对象是否相等

  @Override public int hashCode() { // 重写Object类的hashCode方法，用于计算对象的哈希码，用于在哈希表等数据结构中使用
    return Objects.hash(fieldName, clazz); // 使用Objects.hash方法计算fieldName和clazz的哈希值，确保相等的对象具有相同的哈希码
  } // 方法结束，返回基于fieldName和clazz计算的哈希码
} // 类定义结束
