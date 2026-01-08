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
package org.apache.calcite.plan;  // 声明该接口所属的包，位于org.apache.calcite.plan包下，这是Calcite优化器相关的核心包

import org.apache.calcite.rel.type.RelDataTypeFactory;  // 导入RelDataTypeFactory类，用于创建和管理关系数据类型

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入Nullable注解，用于标记方法返回值可能为null

import java.util.List;  // 导入List接口，用于存储字符串列表

/**
 * A <code>RelOptSchema</code> is a set of {@link RelOptTable} objects.  // RelOptSchema是一个包含多个RelOptTable对象的集合接口，代表一个关系优化模式（schema）
 * 
 * 这个接口定义了Calcite优化器中Schema的核心行为和功能：
 * 1. Schema是数据库中表的集合，类似于数据库中的命名空间或目录
 * 2. 每个Schema包含多个表（RelOptTable），这些表可以被优化器访问和优化
 * 3. Schema提供了表的查找、类型获取和规则注册等功能
 * 
 * 典型使用场景：
 * - 在SQL解析和验证阶段，通过Schema获取表的定义和元数据
 * - 在查询优化阶段，优化器通过Schema了解可用的表及其结构
 * - 在规则注册阶段，Schema可以注册特定的优化规则
 * 
 * 实现类示例：
 * - CalciteSchema：Calcite框架中的标准Schema实现
 * - MockRelOptSchema：用于测试的模拟Schema实现
 */
public interface RelOptSchema {  // 定义一个公共接口RelOptSchema，表示关系优化模式
  //~ Methods ----------------------------------------------------------------  // 分隔符，表示下面是方法定义部分

  /**
   * Retrieves a {@link RelOptTable} based upon a member access.  // 根据成员访问路径获取对应的RelOptTable对象
   *
   * <p>For example, the Saffron expression <code>salesSchema.emps</code>  // 例如：Saffron表达式salesSchema.emps
   * would be resolved using a call to <code>salesSchema.getTableForMember(new  // 可以通过调用salesSchema.getTableForMember(new
   * String[]{"emps" })</code>.  // String[]{"emps"})来解析
   *
   * <p>Note that name.length is only greater than 1 for queries originating  // 注意：names列表的长度大于1的情况仅出现在来自JDBC的查询中
   * from JDBC.  // JDBC查询可能包含多级schema名称，如catalog.schema.table
   *
   * @param names Qualified name  // 参数names是限定名称，可能包含catalog、schema、table等多级名称
   * @return 返回找到的RelOptTable对象，如果未找到则返回null
   * 
   * 方法详细说明：
   * - 输入参数names是一个字符串列表，表示表的限定名称
   * - 对于简单表名，names可能只包含一个元素，如["emps"]
   * - 对于完全限定名，names可能包含多个元素，如["catalog", "schema", "emps"]
   * - 方法返回对应的RelOptTable对象，该对象包含表的元数据和统计信息
   * - 如果找不到对应的表，返回null
   * 
   * 使用示例：
   * - getTableForMember(Arrays.asList("emps")) - 获取当前schema中的emps表
   * - getTableForMember(Arrays.asList("sales", "emps")) - 获取sales schema中的emps表
   * - getTableForMember(Arrays.asList("prod", "sales", "emps")) - 获取prod catalog中sales schema的emps表
   */
  @Nullable RelOptTable getTableForMember(List<String> names);  // 声明方法：根据限定名称列表获取表对象，返回值可能为null

  /**
   * Returns the {@link RelDataTypeFactory type factory} used to generate  // 返回用于为该schema生成类型的RelDataTypeFactory类型工厂
   * types for this schema.  // 这个工厂负责创建和管理schema中所有表和字段的数据类型
   *
   * @return 返回RelDataTypeFactory实例，用于创建关系数据类型
   * 
   * 方法详细说明：
   * - RelDataTypeFactory是Calcite中用于创建数据类型的工厂接口
   * - 通过这个工厂可以创建各种SQL数据类型，如INTEGER、VARCHAR、DATE等
   * - Schema中的所有表和字段类型都通过这个工厂来创建
   * - 类型工厂确保了类型系统的一致性和可扩展性
   * 
   * 使用场景：
   * - 在创建表定义时，使用类型工厂创建字段类型
   * - 在类型推导和验证时，使用类型工厂检查类型兼容性
   * - 在代码生成时，使用类型工厂获取类型信息
   * 
   * 重要说明：
   * - 同一个Schema应该使用同一个类型工厂，以确保类型一致性
   * - 类型工厂通常由RelOptPlanner或RelOptCluster提供
   * - 不同的Schema可能使用不同的类型工厂，但通常共享同一个
   */
  RelDataTypeFactory getTypeFactory();  // 声明方法：返回该schema使用的类型工厂

  /**
   * Registers all the rules supported by this schema. Only called by  // 注册该schema支持的所有规则，仅由RelOptPlanner.registerSchema调用
   * {@link RelOptPlanner#registerSchema}.  // 在优化器注册schema时自动调用此方法
   *
   * @param planner 关系优化器，用于注册schema特定的优化规则
   * 
   * 方法详细说明：
   * - 这个方法允许Schema向优化器注册特定的优化规则
   * - 这些规则可能针对该Schema的表结构或特性进行优化
   * - 不同的Schema实现可能注册不同的规则集
   * - 规则注册后，优化器在查询优化过程中会考虑这些规则
   * 
   * 规则类型示例：
   * - 表扫描优化规则：针对特定存储引擎的扫描优化
   * - 谓词下推规则：将过滤条件推送到数据源
   * - 投影下推规则：只读取需要的列
   * - 连接重排序规则：基于统计信息优化连接顺序
   * 
   * 调用时机：
   * - 在优化器初始化阶段，当注册Schema时调用
   * - 每个Schema只注册一次规则
   * - 规则注册后在整个优化会话中保持有效
   * 
   * 实现建议：
   * - Schema实现类应该在此方法中注册所有相关的优化规则
   * - 规则应该与Schema的特性紧密相关
   * - 避免注册过于通用的规则，这些规则应该在优化器全局注册
   * - 考虑规则的优先级和依赖关系
   */
  void registerRules(RelOptPlanner planner);  // 声明方法：向优化器注册该schema支持的规则
}
