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
package org.apache.calcite.rel.metadata;  // 声明包名，该类位于 org.apache.calcite.rel.metadata 包下，属于 Calcite 关系表达式元数据管理模块

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入 Checker Framework 的注解，用于标记可能为 null 的类型

/** Placeholder for null values. */  // 类文档注释：NullSentinel 是一个枚举类，用作 null 值的占位符
public enum NullSentinel {  // 定义一个枚举类 NullSentinel，用于在元数据系统中表示特殊状态
  /** Placeholder for a null value. */  // 实例文档注释：INSTANCE 枚举常量用作 null 值的占位符
  INSTANCE {  // 定义第一个枚举常量 INSTANCE，用于表示真正的 null 值，在元数据系统中代替 null 使用
    @Override public String toString() {  // 重写 toString 方法，提供自定义的字符串表示
      return "NULL";  // 返回字符串 "NULL"，用于调试和日志输出，表示这是一个 null 占位符
    }  // toString 方法结束
  },  // INSTANCE 枚举常量定义结束

  /** Placeholder that means that a request for metadata is already active,
   * therefore this request forms a cycle. */  // 实例文档注释：ACTIVE 枚举常量表示元数据请求正在处理中，形成循环依赖
  ACTIVE;  // 定义第二个枚举常量 ACTIVE，用于表示元数据请求处于活动状态，检测循环依赖

  public static Comparable mask(@Nullable Comparable value) {  // 静态方法：将可能为 null 的 Comparable 值转换为非 null 值，如果输入为 null 则返回 INSTANCE 占位符
    if (value == null) {  // 检查输入值是否为 null
      return INSTANCE;  // 如果输入值为 null，返回 INSTANCE 枚举常量作为占位符，避免在元数据系统中使用真正的 null
    }  // if 条件判断结束
    return value;  // 如果输入值不为 null，直接返回原值
  }  // mask 方法结束

  public static Object mask(@Nullable Object value) {  // 静态方法重载：将可能为 null 的 Object 值转换为非 null 值，如果输入为 null 则返回 INSTANCE 占位符
    if (value == null) {  // 检查输入值是否为 null
      return INSTANCE;  // 如果输入值为 null，返回 INSTANCE 枚举常量作为占位符，避免在元数据系统中使用真正的 null
    }  // if 条件判断结束
    return value;  // 如果输入值不为 null，直接返回原值
  }  // mask 方法重载结束
}  // NullSentinel 枚举类定义结束
