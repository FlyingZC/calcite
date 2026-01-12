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
package org.apache.calcite.linq4j.tree; // 定义包名，该类属于 org.apache.calcite.linq4j.tree 包，用于 LINQ4J 表达式树的处理

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 CheckerFramework 的可空注解，用于标记参数可能为 null

import java.util.Objects; // 导入 Java 工具类，用于生成哈希码和相等性比较

/**
 * Used to represent the target of a {@link GotoStatement}. // 用于表示 GotoStatement（跳转语句）的目标标签，在代码生成过程中标识跳转目标位置
 */
public class LabelTarget { // LabelTarget 类：表示标签目标，用于在生成的代码中标识跳转的目标位置，类似于 Java 中的 label
  public final String name; // 标签名称，用于唯一标识这个标签目标，final 表示该字段不可变

  public LabelTarget(String name) { // 构造方法：创建一个指定名称的标签目标对象
    this.name = name; // 将传入的名称参数赋值给成员变量 name，初始化标签名称
  }

  @Override public boolean equals(@Nullable Object o) { // 重写 equals 方法：用于比较两个 LabelTarget 对象是否相等
    if (this == o) { // 首先判断是否引用同一个对象（内存地址相同）
      return true; // 如果是同一个对象引用，直接返回 true
    }
    if (o == null || getClass() != o.getClass()) { // 判断对象是否为 null 或类型是否不同
      return false; // 如果对象为 null 或类型不匹配，返回 false
    }

    LabelTarget that = (LabelTarget) o; // 将对象强制转换为 LabelTarget 类型

    if (name != null ? !name.equals(that.name) : that.name != null) { // 比较 name 字段是否相等，处理 null 情况
      return false; // 如果 name 不相等，返回 false
    }

    return true; // 所有比较都通过，返回 true 表示两个对象相等
  }

  @Override public int hashCode() { // 重写 hashCode 方法：根据 name 字段生成哈希码，用于支持哈希表等数据结构
    return Objects.hashCode(name); // 使用 Objects 工具类的 hashCode 方法生成 name 的哈希码，处理 null 情况
  }
}
