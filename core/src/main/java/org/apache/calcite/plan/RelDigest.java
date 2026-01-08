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
 */ // Apache 许可证声明，规定了该文件的使用条款和限制
package org.apache.calcite.plan; // 声明该类属于 org.apache.calcite.plan 包，该包包含 Calcite 查询优化器的核心规划相关类

import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，这是 Calcite 中关系表达式（Relational Expression）的核心接口，代表查询计划中的一个节点

import org.apiguardian.api.API; // 导入 API 注解，用于标记 API 的稳定性和使用范围

/**
 * The digest is the exact representation of the corresponding {@code RelNode}, // 摘要是对应 RelNode 的精确表示
 * at anytime, anywhere. // 在任何时间、任何地点都保持一致
 * The only difference is that digest is compared using // 唯一的区别是摘要使用 equals 和 hashCode 进行比较
 * {@code #equals} and {@code #hashCode}, which are prohibited to override // 这些方法在 RelNode 中被禁止重写
 * for RelNode, for legacy reasons. // 由于历史原因（为了保持向后兼容性）
 *
 * <p>INTERNAL USE ONLY. // 仅限内部使用，不建议外部代码直接使用此接口
 */ // 该接口主要用于 Calcite 内部优化器在比较 RelNode 时使用
@API(since = "1.24", status = API.Status.INTERNAL) // API 注解，标记该接口从 1.24 版本开始存在，状态为 INTERNAL（内部使用）
public interface RelDigest { // 定义 RelDigest 接口，用于表示 RelNode 的摘要信息，提供可比较和可哈希的能力
  /**
   * Reset state, possibly cache of hash code. // 重置状态，可能包括哈希码的缓存
   */ // 该方法用于清除摘要对象的内部状态，特别是缓存的哈希码值，以便在 RelNode 发生变化后重新计算
  void clear(); // 声明 clear 方法，无返回值，用于重置摘要对象的状态

  /**
   * Returns the relnode that this digest is associated with. // 返回与此摘要关联的 RelNode 对象
   */ // 该方法用于获取当前摘要所对应的原始 RelNode 对象，建立摘要与关系表达式之间的映射关系
  RelNode getRel(); // 声明 getRel 方法，返回 RelNode 类型，用于获取关联的关系表达式节点
}
