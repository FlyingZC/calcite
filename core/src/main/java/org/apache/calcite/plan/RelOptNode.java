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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.plan; // 定义包名，这个接口位于org.apache.calcite.plan包中，是Calcite优化器核心包

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系表达式的行类型（即输出数据的类型信息）

import java.util.List; // 导入List接口，用于存储关系表达式的输入节点列表

/**
 * Node in a planner.
 * 这是Calcite优化器中关系表达式的节点接口
 * 
 * RelOptNode是所有关系表达式节点（RelNode）的顶层接口，定义了优化器中每个节点必须实现的基本方法
 * 
 * 【核心概念】：
 * 1. 关系表达式（Relational Expression）：代表SQL查询中的一个操作，如TableScan、Filter、Project、Join等
 * 2. 优化器（Planner）：负责将SQL查询转换为最优执行计划的组件
 * 3. 关系代数树：由多个RelOptNode节点组成的树形结构，表示查询的执行计划
 * 
 * 【设计模式】：
 * - 组合模式（Composite Pattern）：树形结构，每个节点可以有子节点（输入）
 * - 访问者模式（Visitor Pattern）：通过访问者遍历和转换树结构
 * 
 * 【继承体系】：
 * RelOptNode (接口) → RelNode (接口) → 具体的关系表达式类（如TableScan、Filter、Join等）
 * 
 * 【主要职责】：
 * 1. 提供节点的唯一标识（ID）
 * 2. 提供节点的摘要信息（digest），用于判断节点是否等价
 * 3. 提供节点的特征集合（trait set），描述节点的物理属性（如排序、分区等）
 * 4. 提供节点的输出行类型（row type），描述输出数据的结构
 * 5. 提供节点的输入列表（inputs），构建树形结构
 * 6. 提供节点所属的集群（cluster），共享元数据和类型系统
 */
public interface RelOptNode { // 定义RelOptNode接口，这是所有关系表达式节点的顶层接口
  /**
   * Returns the ID of this relational expression, unique among all relational
   * expressions created since the server was started.
   * 
   * 【方法作用】：获取当前关系表达式节点的唯一标识符
   * 
   * 【详细说明】：
   * 1. ID是服务器启动以来创建的所有关系表达式的唯一标识
   * 2. ID通常是递增的整数，从0或1开始
   * 3. 每个节点在创建时会被分配一个唯一的ID
   * 4. 即使两个节点逻辑上等价，它们的ID也不同
   * 
   * 【使用场景】：
   * - 调试和日志记录：通过ID追踪特定节点
   * - 缓存和记忆化：使用ID作为缓存的键
   * - 唯一性比较：区分不同的节点实例
   * - 可视化工具：在图形化界面中标识节点
   * 
   * 【注意事项】：
   * - ID不能用于判断节点是否等价，只能用于标识唯一性
   * - ID在节点生命周期内保持不变
   * - ID的分配顺序反映了节点创建的顺序
   *
   * @return Unique ID 返回当前节点的唯一ID（整数类型）
   */
  int getId(); // 声明获取节点唯一ID的方法

  /**
   * Returns a string which concisely describes the definition of this
   * relational expression. Two relational expressions are equivalent if
   * their digests and {@link #getRowType()} (except the field names) are the same.
   * 
   * 【方法作用】：获取当前关系表达式节点的摘要字符串，用于判断节点是否等价
   * 
   * 【详细说明】：
   * 1. 摘要（digest）是节点定义的简洁描述，不包含节点的身份信息（ID）
   * 2. 两个节点等价的条件：digest相同 + 行类型相同（字段名除外）
   * 3. digest包含子节点的身份（假设子节点已经被规范化）
   * 4. digest不包含节点自己的ID，这样可以确保逻辑等价的节点digest相同
   * 
   * 【digest的组成】：
   * - 节点类型（如Filter、Project、Join）
   * - 节点的关键属性（如过滤条件、投影表达式、连接条件）
   * - 子节点的引用（通过子节点的ID或digest）
   * 
   * 【等价性判断示例】：
   * - Filter(age > 20) on TableScan(employees) 和 Filter(age > 20) on TableScan(employees) → 等价
   * - Filter(age > 20) on TableScan(employees) 和 Filter(age > 30) on TableScan(employees) → 不等价
   * 
   * 【使用场景】：
   * - 优化器中的子表达式公共子表达式消除（CSE）
   * - 缓存查找：避免重复计算等价的子树
   * - 等价类分析：识别可以共享的节点
   * 
   * 【与toString的区别】：
   * - getDigest()：不包含ID，只包含定义，用于等价性比较
   * - toString()：包含ID，格式为"rel#{id}:{digest}"，用于显示和调试
   *
   * @return Digest string of this {@code RelNode} 返回当前节点的摘要字符串
   */
  String getDigest(); // 声明获取节点摘要字符串的方法

  /**
   * Retrieves this RelNode's traits. Note that although the RelTraitSet
   * returned is modifiable, it <b>must not</b> be modified during
   * optimization. It is legal to modify the traits of a RelNode before or
   * after optimization, although doing so could render a tree of RelNodes
   * unimplementable. If a RelNode's traits need to be modified during
   * optimization, clone the RelNode and change the clone's traits.
   * 
   * 【方法作用】：获取当前关系表达式节点的特征集合（trait set）
   * 
   * 【核心概念 - 特征（Trait）】：
   * 特征是描述关系表达式物理属性的集合，包括：
   * 1. 约定（Convention）：数据的物理表示方式（如逻辑、Java、Enumerable、Cassandra等）
   * 2. 排序（Collation）：数据的排序属性
   * 3. 分区（Partition）：数据的分区属性
   * 4. 分布（Distribution）：数据的分布属性
   * 5. 重分区（RelDistribution）：数据的重分区属性
   * 
   * 【详细说明】：
   * 1. 特征集合（RelTraitSet）是特征的不可变集合
   * 2. 特征决定了节点如何被物理实现
   * 3. 优化器通过修改特征来探索不同的物理实现
   * 
   * 【重要约束】：
   * - 在优化过程中，<b>绝对不能</b>修改节点的特征集合
   * - 如果需要在优化过程中修改特征，必须克隆节点并修改克隆的特征
   * - 修改前后的特征可能导致整个树不可实现
   * 
   * 【使用场景】：
   * 1. 优化器探索不同的物理实现：
   *    - 逻辑约定 → Enumerable约定 → 物理执行
   *    - 不同排序顺序的探索
   *    - 不同分区策略的探索
   * 2. 规则匹配：根据特征决定哪些规则可以应用
   * 3. 成本估算：不同的特征有不同的执行成本
   * 
   * 【示例】：
   * - 逻辑节点：Convention=LOGICAL（逻辑层）
   * - Enumerable节点：Convention=ENUMERABLE（可枚举执行）
   * - Cassandra节点：Convention=CASSANDRA（Cassandra数据库）
   * - 排序节点：Collation=[age ASC]（按age升序）
   * - 分区节点：Partition=[hash(id)]（按id哈希分区）
   *
   * @return this RelNode's trait set 返回当前节点的特征集合
   */
  RelTraitSet getTraitSet(); // 声明获取节点特征集合的方法

  // TODO: We don't want to require that nodes have very详细的行类型. It
  // may not even be known at planning time.
  // TODO注释：说明当前设计中要求节点有非常详细的行类型可能不是最佳方案
  // 因为在规划时，行类型可能还不知道（例如某些外部数据源）
  // 这表明未来可能会放宽对行类型的要求
  /**
   * 【方法作用】：获取当前关系表达式节点的输出行类型
   * 
   * 【核心概念 - 行类型（Row Type）】：
   * 行类型（RelDataType）描述了节点输出数据的结构，包括：
   * 1. 字段数量
   * 2. 每个字段的名称
   * 3. 每个字段的数据类型（如INTEGER, VARCHAR, DATE等）
   * 4. 字段的可为空性（nullable）
   * 5. 字段的精度和标度（对于数值类型）
   * 
   * 【详细说明】：
   * 1. 每个节点必须知道它的输出行类型
   * 2. 行类型用于类型检查和类型推导
   * 3. 行类型在查询编译时确定
   * 4. 子节点的行类型推导父节点的行类型
   * 
   * 【行类型的推导规则】：
   * - TableScan：从表定义中获取行类型
   * - Project：根据投影表达式推导行类型
   * - Filter：不改变行类型，直接传递输入的行类型
   * - Join：合并左右输入的行类型（可能包含重复字段）
   * - Aggregate：根据分组列和聚合函数推导行类型
   * - Sort：不改变行类型，直接传递输入的行类型
   * - Union：要求所有输入的行类型兼容，输出统一的行类型
   * 
   * 【使用场景】：
   * 1. 类型检查：确保SQL表达式的类型正确
   * 2. 类型推导：自动推导表达式和节点的类型
   * 3. 代码生成：根据行类型生成访问字段的代码
   * 4. 数据转换：在不同数据源之间转换数据格式
   * 
   * 【示例】：
   * - 表employees：(id INTEGER, name VARCHAR(100), age INTEGER, dept_id INTEGER)
   * - Project(id, name)输出：(id INTEGER, name VARCHAR(100))
   * - Filter(age > 20)输出：(id INTEGER, name VARCHAR(100), age INTEGER, dept_id INTEGER)
   * - Aggregate(dept_id, COUNT(*))输出：(dept_id INTEGER, count INTEGER)
   * 
   * 【注意事项】：
   * - TODO注释表明未来可能不要求所有节点都有详细的行类型
   * - 某些情况下行类型可能在规划时未知（如外部数据源）
   * - 等价性判断时，行类型比较忽略字段名，只比较类型
   */
  RelDataType getRowType(); // 声明获取节点输出行类型的方法

  /**
   * Returns a string which describes the relational expression and, unlike
   * {@link #getDigest()}, also includes the identity. Typically returns
   * "rel#{id}:{digest}".
   * 
   * 【方法作用】：获取包含身份信息的描述字符串
   * 
   * 【详细说明】：
   * 1. 与getDigest()不同，这个方法包含节点的身份信息（ID）
   * 2. 返回格式通常是："rel#{id}:{digest}"
   * 3. 这个方法已被标记为@Deprecated，将在2.0版本前移除
   * 4. 建议使用toString()方法代替
   * 
   * 【与getDigest的区别】：
   * - getDigest()：只包含定义，不包含ID，用于等价性比较
   * - getDescription()：包含定义和ID，用于显示和调试
   * 
   * 【与toString的关系】：
   * - getDescription()和toString()通常返回相同的结果
   * - toString()是标准Java方法，更推荐使用
   * 
   * 【使用场景】：
   * - 调试日志：记录节点的详细信息
   * - 可视化工具：在图形界面中显示节点
   * - 错误消息：在异常中标识特定节点
   * 
   * 【示例】：
   * - getDigest()：返回 "Filter(age > 20)"
   * - getDescription()：返回 "rel#12:Filter(age > 20)"
   * - 其中12是节点的ID
   * 
   * 【废弃原因】：
   * - 功能与toString()重复
   * - toString()是Java标准方法，更符合习惯
   * - 减少API的复杂性
   *
   * @return String which describes the relational expression and, unlike
   *   {@link #getDigest()}, also includes the identity 返回包含身份信息的描述字符串
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  String getDescription(); // 声明获取包含身份信息的描述字符串的方法

  /**
   * Returns an array of this relational expression's inputs. If there are no
   * inputs, returns an empty list, not {@code null}.
   * 
   * 【方法作用】：获取当前关系表达式节点的输入节点列表
   * 
   * 【核心概念 - 输入（Inputs）】：
   * 输入是当前节点的子节点，表示数据流向：
   * - 数据从子节点流向父节点
   * - 父节点处理子节点的输出
   * - 整个查询计划形成一棵树（或DAG）
   * 
   * 【详细说明】：
   * 1. 返回当前节点的所有输入节点列表
   * 2. 如果没有输入，返回空列表，而不是null
   * 3. 输入列表的顺序通常有意义（如Join的左右输入）
   * 4. 输入节点也是RelOptNode类型
   * 
   * 【不同节点的输入数量】：
   * - TableScan：0个输入（叶子节点）
   * - Values：0个输入（叶子节点）
   * - Filter：1个输入（单目操作符）
   * - Project：1个输入（单目操作符）
   * - Sort：1个输入（单目操作符）
   * - Aggregate：1个输入（单目操作符）
   * - Join：2个输入（左表、右表）
   * - Union：N个输入（多个表的联合）
   * - Intersect：N个输入（多个表的交集）
   * - Minus：2个输入（左表、右表）
   * 
   * 【树形结构示例】：
   * ```
   *         Join
   *        /    \
   *   Filter  Filter
   *      |       |
   *   Scan(A)  Scan(B)
   * 
   * Join的输入：[Filter(A), Filter(B)]
   * Filter(A)的输入：[Scan(A)]
   * Filter(B)的输入：[Scan(B)]
   * Scan(A)的输入：[]
   * Scan(B)的输入：[]
   * ```
   * 
   * 【使用场景】：
   * 1. 遍历查询计划：从根节点递归访问所有节点
   * 2. 规则匹配：检查输入节点是否满足规则条件
   * 3. 转换优化：替换或重构子树
   * 4. 成本估算：递归计算子树的成本
   * 
   * 【注意事项】：
   * - 返回的列表可能是不可变的
   * - 不要直接修改输入列表，应该通过clone和replace方法
   * - 输入列表的顺序对某些操作符很重要（如Join、Minus）
   *
   * @return Array of this relational expression's inputs 返回当前节点的输入节点列表（如果没有输入则返回空列表）
   */
  List<? extends RelOptNode> getInputs(); // 声明获取节点输入列表的方法，返回类型是RelOptNode的列表

  /**
   * Returns the cluster this relational expression belongs to.
   * 
   * 【方法作用】：获取当前关系表达式节点所属的集群（cluster）
   * 
   * 【核心概念 - 集群（Cluster）】：
   * 集群（RelOptCluster）是一个查询计划中的共享上下文，包含：
   * 1. 类型系统（RelDataTypeFactory）：用于创建和操作数据类型
   * 2. 表达式工厂（RexBuilder）：用于创建和操作表达式
   * 3. 元数据提供器（RelMetadataProvider）：提供元数据查询功能
   * 4. 函数注册表（SqlOperatorTable）：注册SQL函数和操作符
   * 5. 转换规则集合（RelOptRule）：优化规则
   * 
   * 【详细说明】：
   * 1. 同一个查询计划中的所有节点共享同一个cluster
   * 2. cluster提供了查询级别的共享资源和上下文
   * 3. cluster确保类型系统、表达式工厂等的一致性
   * 4. cluster在查询编译时创建，在整个优化过程中保持不变
   * 
   * 【cluster的作用】：
   * 1. 类型系统：确保所有节点使用相同的数据类型定义
   * 2. 表达式工厂：确保表达式创建的一致性
   * 3. 元数据查询：提供统一的元数据查询接口
   * 4. 函数注册：提供SQL函数的定义和实现
   * 5. 规则管理：管理优化规则的注册和匹配
   * 
   * 【使用场景】：
   * 1. 创建数据类型：通过cluster.getTypeFactory()创建RelDataType
   * 2. 创建表达式：通过cluster.getRexBuilder()创建RexNode
   * 3. 查询元数据：通过cluster.getMetadataProvider()查询行数、选择度等
   * 4. 注册函数：通过cluster.getPlanner()访问函数注册表
   * 5. 类型检查：确保不同节点的类型兼容
   * 
   * 【示例】：
   * ```java
   * // 获取类型工厂
   * RelDataTypeFactory typeFactory = cluster.getTypeFactory();
   * RelDataType intType = typeFactory.createSqlType(SqlTypeName.INTEGER);
   * 
   * // 获取表达式工厂
   * RexBuilder rexBuilder = cluster.getRexBuilder();
   * RexNode literal = rexBuilder.makeLiteral(100);
   * 
   * // 查询元数据
   * RelMetadataProvider metadataProvider = cluster.getMetadataProvider();
   * Double rowCount = RelMetadataQuery.getRowCount(node);
   * ```
   * 
   * 【注意事项】：
   * - 同一个查询计划中的所有节点必须属于同一个cluster
   * - 不要尝试修改cluster中的共享资源
   * - cluster的生命周期与查询计划相同
   * - cluster是线程安全的，可以在多个线程中共享
   *
   * @return cluster 返回当前节点所属的集群对象
   */
  RelOptCluster getCluster(); // 声明获取节点所属集群的方法
} // 接口定义结束
