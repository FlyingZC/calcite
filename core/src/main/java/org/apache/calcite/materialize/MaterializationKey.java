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
package org.apache.calcite.materialize;  // 物化化相关的包，包含物化视图、物化表等功能

import org.checkerframework.checker.nullness.qual.Nullable;  // 引入可空性检查注解，用于标识可能为null的参数

import java.io.Serializable;  // 序列化接口，使对象可以被序列化，用于网络传输或持久化存储
import java.util.UUID;  // 通用唯一标识符类，用于生成全局唯一的ID

/**
 * Unique identifier for a materialization.  // 物化化的唯一标识符类
 *
 * <p>It is immutable and can only be created by the  // 该类是不可变的，只能由MaterializationService创建
 * {@link MaterializationService}. For communicating with the service.  // 用于与物化服务进行通信
 */
public class MaterializationKey implements Serializable {  // 定义MaterializationKey类，实现Serializable接口使其可序列化
  private final UUID uuid = UUID.randomUUID();  // 成员变量：使用UUID生成全局唯一标识符，final修饰表示一旦初始化后不可修改，确保标识符的唯一性和不可变性

  @Override public int hashCode() {  // 重写Object类的hashCode方法，用于计算对象的哈希码
    return uuid.hashCode();  // 返回内部uuid的哈希码，确保基于uuid进行哈希计算，保证相同的MaterializationKey对象具有相同的哈希码
  }

  @Override public boolean equals(@Nullable Object obj) {  // 重写Object类的equals方法，用于比较两个MaterializationKey对象是否相等，@Nullable表示obj参数可能为null
    return this == obj  // 首先检查是否是同一个对象引用（内存地址相同），如果是则直接返回true
        || obj instanceof MaterializationKey  // 如果不是同一个引用，检查obj是否是MaterializationKey类的实例
        && uuid.equals(((MaterializationKey) obj).uuid);  // 如果是MaterializationKey实例，则比较两者的uuid是否相等，相等则返回true，否则返回false
  }

  @Override public String toString() {  // 重写Object类的toString方法，用于返回对象的字符串表示
    return uuid.toString();  // 返回内部uuid的字符串表示，便于调试和日志输出
  }
}
