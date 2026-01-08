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
package org.apache.calcite.rel; // 定义包名，该接口位于 org.apache.calcite.rel 包下，属于 Calcite 关系表达式核心包

import org.apache.calcite.util.mapping.IntPair; // 导入 IntPair 类，用于表示整数对（源列索引和目标列索引的映射关系）

import java.util.List; // 导入 List 接口，用于存储有序的元素集合

/**
 * Interface for a referential constraint, i.e., Foreign-Key - Unique-Key relationship,
 * between two tables.
 * 表示两个表之间的引用约束（参照完整性约束），即外键-唯一键关系
 * 
 * 此接口定义了两个表之间引用约束的标准接口，用于描述外键关系
 * 在关系数据库中，引用完整性约束确保一个表中的外键值必须匹配另一个表中的主键或唯一键值
 * 
 * 主要用途：
 * 1. 在查询优化过程中，优化器可以利用引用约束信息进行查询重写和优化
 * 2. 支持基于外键关系的连接消除、谓词下推等优化规则
 * 3. 提供表之间的引用关系元数据，帮助优化器做出更好的执行计划选择
 * 
 * 典型场景：
 * - 例如：DEPT 表的 dept_id 字段引用 EMP 表的 id 字段
 * - DEPT 表是引用表（source），EMP 表是被引用表（target）
 * - 优化器可以利用这个关系消除不必要的连接操作
 */
public interface RelReferentialConstraint { // 定义引用约束接口，描述两个表之间的外键-主键关系
  //~ Methods ---------------------------------------------------------------- // 方法区域分隔符，表示以下是方法声明

  /** Returns the number of columns in the keys.
   *
   * @deprecated Use {@code getColumnPairs().size()} */
  @Deprecated // 标记为已废弃的方法，建议使用 getColumnPairs().size() 替代
  default int getNumColumns() { // 默认方法，返回键中的列数（外键关联的列数量）
    return getColumnPairs().size(); // 返回列对的数量，即外键关联的列数
  }

  /**The qualified name of the referencing table, e.g. DEPT. */
  List<String> getSourceQualifiedName(); // 获取引用表的完全限定名称（例如：["catalog", "schema", "DEPT"]），即包含外键的表

  /** The qualified name of the referenced table, e.g. EMP. */
  List<String> getTargetQualifiedName(); // 获取被引用表的完全限定名称（例如：["catalog", "schema", "EMP"]），即包含主键/唯一键的表

  /** The (source, target) column ordinals. */
  List<IntPair> getColumnPairs(); // 获取源表和目标表之间的列对映射列表，每个 IntPair 表示一个外键列（source）到主键列（target）的映射关系

}
