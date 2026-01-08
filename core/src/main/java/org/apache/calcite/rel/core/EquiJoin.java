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
package org.apache.calcite.rel.core;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.ImmutableIntList;

import com.google.common.collect.ImmutableList;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * EquiJoin（等值连接）类的详细说明：
 * 
 * 【类的作用】
 * 这是所有基于列相等性条件的连接操作的基础抽象类。等值连接是指连接条件只包含列与列之间的相等比较。
 * 例如：SELECT * FROM table1 JOIN table2 ON table1.id = table2.id，这就是一个典型的等值连接。
 * 
 * 【核心特性】
 * 1. 等值连接是SQL查询中最常见的连接类型，能够利用索引进行优化，性能通常优于非等值连接
 * 2. 大多数情况下，可以通过 JoinInfo.isEqui() 方法判断一个连接条件是否基于列相等性
 * 3. 这个类是抽象类，专门为Calcite的可枚举连接和其他系统的连接实现提供继承基础
 * 
 * 【使用场景】
 * 如果你的连接实现不支持非等值连接条件（如 table1.id > table2.id），应该继承 EquiJoin 类。
 * Calcite会在一些优化规则中为 EquiJoin 消除某些优化逻辑，例如：
 * - FilterJoinRule 不会将上方过滤器中的非等值连接条件下推到 EquiJoin 中
 * 
 * 【设计模式】
 * 这个类遵循模板方法模式，定义了等值连接的基本结构，子类可以在此基础上实现具体的连接算法
 * 
 * 【性能优化】
 * 等值连接可以利用以下优化技术：
 * - 哈希连接（Hash Join）：使用哈希表快速匹配相等键值
 * - 归并连接（Merge Join）：在有序数据上高效匹配
 * - 索引嵌套循环连接（Indexed Nested Loop Join）：利用索引快速查找匹配行
 * 
 * 【已弃用说明】
 * @deprecated 这个类已经不再需要了。如果你正在编写一个只接受等值条件的 Join 子类，
 * 只需让它继承 {@link Join} 类即可。当其 {@link JoinInfo#nonEquiConditions} 为空列表时，
 * 就可以明显看出它是一个等值连接。这个类将在 2.0 版本之前被移除。
 * 
 * 【为什么弃用】
 * 1. JoinInfo 已经提供了足够的信息来判断是否为等值连接
 * 2. 维护两个类增加了复杂性而没有带来明显的优势
 * 3. 现代的优化规则可以直接通过 JoinInfo 的属性来处理等值连接的特殊情况
 */
@Deprecated // to be removed before 2.0
public abstract class EquiJoin extends Join {
  
  /**
   * 【成员变量：leftKeys】
   * 类型：ImmutableIntList（不可变的整数列表）
   * 作用：存储左表参与等值连接条件的列索引列表
   * 
   * 【详细说明】
   * - 这个列表中的每个整数代表左表（left input）中参与连接条件的列的索引位置
   * - 索引从 0 开始，对应左表输出行中的列位置
   * - 例如：leftKeys = [0, 2] 表示左表的第 0 列和第 2 列参与连接
   * - 该列表与 rightKeys 是一一对应的，即 leftKeys[i] 与 rightKeys[i] 进行相等比较
   * 
   * 【使用示例】
   * 假设有 SQL: SELECT * FROM emp JOIN dept ON emp.deptno = dept.deptno AND emp.location = dept.location
   * - 如果 emp 表有列：[empno, ename, deptno, location]，则 leftKeys 可能为 [2, 3]
   * - 如果 dept 表有列：[deptno, dname, location]，则 rightKeys 可能为 [0, 2]
   * 
   * 【不可变性】
   * 使用 ImmutableIntList 确保一旦创建就不能修改，这是 Calcite 关系代数的重要设计原则，
   * 因为关系代数操作应该是不可变的，每次修改都产生新的对象。
   */
  public final ImmutableIntList leftKeys;
  
  /**
   * 【成员变量：rightKeys】
   * 类型：ImmutableIntList（不可变的整数列表）
   * 作用：存储右表参与等值连接条件的列索引列表
   * 
   * 【详细说明】
   * - 这个列表中的每个整数代表右表（right input）中参与连接条件的列的索引位置
   * - 索引从 0 开始，对应右表输出行中的列位置
   * - 例如：rightKeys = [0, 2] 表示右表的第 0 列和第 2 列参与连接
   * - 该列表与 leftKeys 是一一对应的，即 leftKeys[i] 与 rightKeys[i] 进行相等比较
   * 
   * 【使用示例】
   * 假设有 SQL: SELECT * FROM emp JOIN dept ON emp.deptno = dept.deptno AND emp.location = dept.location
   * - 如果 emp 表有列：[empno, ename, deptno, location]，则 leftKeys 可能为 [2, 3]
   * - 如果 dept 表有列：[deptno, dname, location]，则 rightKeys 可能为 [0, 2]
   * 
   * 【不可变性】
   * 使用 ImmutableIntList 确保一旦创建就不能修改，这是 Calcite 关系代数的重要设计原则，
   * 因为关系代数操作应该是不可变的，每次修改都产生新的对象。
   */
  public final ImmutableIntList rightKeys;

  /**
   * 【构造方法：EquiJoin（推荐版本）】
   * 
   * 【方法作用】
   * 创建一个 EquiJoin 实例，自动从连接条件中提取等值连接键
   * 
   * 【参数说明】
   * @param cluster - RelOptCluster：关系优化集群，包含优化器的全局信息，如成本模型、规则集等
   * @param traits - RelTraitSet：关系特性集合，定义了该连接的物理属性，如约定（Convention）、排序（Collation）、分布（Distribution）等
   * @param left - RelNode：左输入关系，即连接操作符的左表
   * @param right - RelNode：右输入关系，即连接操作符的右表
   * @param condition - RexNode：连接条件表达式，通常是等值比较的组合
   * @param variablesSet - Set<CorrelationId>：相关变量集合，用于处理子查询和相关性
   * @param joinType - JoinRelType：连接类型，包括 INNER（内连接）、LEFT（左外连接）、RIGHT（右外连接）、FULL（全外连接）
   * 
   * 【实现细节】
   * 1. 调用父类 Join 的构造方法初始化基本属性
   * 2. 从父类 Join 自动生成的 joinInfo 对象中提取左键和右键
   * 3. 使用 requireNonNull 确保键值不为 null，提供快速失败机制
   * 4. 使用断言验证连接条件确实是等值连接，如果不是则抛出 AssertionError
   * 
   * 【为什么推荐】
   * - 不需要手动指定 leftKeys 和 rightKeys，自动从 condition 中提取
   * - 减少人为错误，确保键值与条件一致
   * - 代码更简洁，维护性更好
   * 
   * 【注意事项】
   * - 如果连接条件包含非等值部分，这个构造方法会失败（断言失败）
   * - joinInfo 是在父类 Join 构造方法中自动创建的
   */
  /** Creates an EquiJoin. */
  protected EquiJoin(RelOptCluster cluster, RelTraitSet traits, RelNode left,
      RelNode right, RexNode condition, Set<CorrelationId> variablesSet,
      JoinRelType joinType) {
    super(cluster, traits, ImmutableList.of(), left, right, condition, variablesSet, joinType); // 调用父类构造方法，ImmutableList.of()表示没有系统字段
    this.leftKeys = requireNonNull(joinInfo.leftKeys); // 从父类的joinInfo中提取左键，确保不为null
    this.rightKeys = requireNonNull(joinInfo.rightKeys); // 从父类的joinInfo中提取右键，确保不为null
    assert joinInfo.isEqui() : "Create EquiJoin with non-equi join condition."; // 断言：确保连接条件是等值连接，否则抛出错误
  }

  /**
   * 【构造方法：EquiJoin（已弃用版本）】
   * 
   * 【方法作用】
   * 创建一个 EquiJoin 实例，手动指定等值连接键
   * 
   * 【参数说明】
   * @param cluster - RelOptCluster：关系优化集群，包含优化器的全局信息
   * @param traits - RelTraitSet：关系特性集合，定义连接的物理属性
   * @param left - RelNode：左输入关系，即连接操作符的左表
   * @param right - RelNode：右输入关系，即连接操作符的右表
   * @param condition - RexNode：连接条件表达式
   * @param leftKeys - ImmutableIntList：左表参与连接的列索引列表，必须手动指定
   * @param rightKeys - ImmutableIntList：右表参与连接的列索引列表，必须手动指定
   * @param variablesSet - Set<CorrelationId>：相关变量集合，用于处理子查询和相关性
   * @param joinType - JoinRelType：连接类型（INNER、LEFT、RIGHT、FULL）
   * 
   * 【实现细节】
   * 1. 调用父类 Join 的构造方法初始化基本属性
   * 2. 直接使用传入的 leftKeys 和 rightKeys 参数
   * 3. 使用 requireNonNull 确保键值不为 null，并提供有意义的错误消息
   * 
   * 【为什么不推荐】
   * - 需要手动维护 leftKeys 和 rightKeys，容易与 condition 不一致
   * - 增加了代码复杂性和出错的可能性
   * - 与自动提取版本相比没有优势
   * 
   * 【已弃用原因】
   * @deprecated 这个构造方法将在 2.0 版本之前被移除，推荐使用自动提取键值的构造方法
   * 
   * 【使用场景】
   * - 仅在特殊情况下使用，例如需要精确控制键值提取逻辑
   * - 向后兼容旧代码
   */
  /** Creates an EquiJoin. */
  @Deprecated // to be removed before 2.0
  protected EquiJoin(RelOptCluster cluster, RelTraitSet traits, RelNode left,
      RelNode right, RexNode condition, ImmutableIntList leftKeys,
      ImmutableIntList rightKeys, Set<CorrelationId> variablesSet,
      JoinRelType joinType) {
    super(cluster, traits, ImmutableList.of(), left, right, condition, variablesSet, joinType); // 调用父类构造方法，ImmutableList.of()表示没有系统字段
    this.leftKeys = requireNonNull(leftKeys, "leftKeys"); // 直接使用传入的左键，确保不为null，否则抛出异常
    this.rightKeys = requireNonNull(rightKeys, "rightKeys"); // 直接使用传入的右键，确保不为null，否则抛出异常
  }

  /**
   * 【构造方法：EquiJoin（已弃用的旧版本）】
   * 
   * 【方法作用】
   * 创建一个 EquiJoin 实例，使用旧的相关变量表示方式（字符串集合）
   * 
   * 【参数说明】
   * @param cluster - RelOptCluster：关系优化集群
   * @param traits - RelTraitSet：关系特性集合
   * @param left - RelNode：左输入关系
   * @param right - RelNode：右输入关系
   * @param condition - RexNode：连接条件表达式
   * @param leftKeys - ImmutableIntList：左表连接键
   * @param rightKeys - ImmutableIntList：右表连接键
   * @param joinType - JoinRelType：连接类型
   * @param variablesStopped - Set<String>：旧版本的相关变量集合（字符串表示）
   * 
   * 【实现细节】
   * 1. 将字符串集合转换为 CorrelationId 集合
   * 2. 调用新的构造方法完成初始化
   * 
   * 【已弃用原因】
   * @deprecated 使用字符串表示相关变量已经过时，现在使用 CorrelationId 对象
   * 这个构造方法将在 2.0 版本之前被移除
   * 
   * 【向后兼容】
   * 这个方法的存在是为了向后兼容旧代码，新代码不应使用它
   */
  @Deprecated // to be removed before 2.0
  protected EquiJoin(RelOptCluster cluster, RelTraitSet traits, RelNode left,
          RelNode right, RexNode condition, ImmutableIntList leftKeys,
          ImmutableIntList rightKeys, JoinRelType joinType,
          Set<String> variablesStopped) {
    this(cluster, traits, left, right, condition, leftKeys, rightKeys, // 调用新的构造方法，将字符串集合转换为CorrelationId集合
        CorrelationId.setOf(variablesStopped), joinType);
  }

  /**
   * 【方法：getLeftKeys】
   * 
   * 【方法作用】
   * 获取左表参与等值连接条件的列索引列表
   * 
   * 【返回值】
   * @return ImmutableIntList - 左表连接键的不可变列表，每个元素代表左表中参与连接的列索引
   * 
   * 【使用场景】
   * - 优化器需要知道哪些列参与连接，以便进行连接顺序优化
   * - 连接实现需要这些信息来构建哈希表或执行归并连接
   * - 统计信息收集需要知道连接键以估算连接选择性
   * 
   * 【示例】
   * 假设有连接：emp JOIN dept ON emp.deptno = dept.deptno
   * emp 表结构：[empno, ename, deptno, sal]
   * getLeftKeys() 返回 [2]，表示 emp 表的第 2 列（deptno）参与连接
   * 
   * 【注意事项】
   * - 返回的是不可变列表，不能修改
   * - 返回的列表与 rightKeys 的长度相同，且位置一一对应
   */
  public ImmutableIntList getLeftKeys() {
    return leftKeys; // 返回左表连接键列表
  }

  /**
   * 【方法：getRightKeys】
   * 
   * 【方法作用】
   * 获取右表参与等值连接条件的列索引列表
   * 
   * 【返回值】
   * @return ImmutableIntList - 右表连接键的不可变列表，每个元素代表右表中参与连接的列索引
   * 
   * 【使用场景】
   * - 优化器需要知道哪些列参与连接，以便进行连接顺序优化
   * - 连接实现需要这些信息来构建哈希表或执行归并连接
   * - 统计信息收集需要知道连接键以估算连接选择性
   * 
   * 【示例】
   * 假设有连接：emp JOIN dept ON emp.deptno = dept.deptno
   * dept 表结构：[deptno, dname, loc]
   * getRightKeys() 返回 [0]，表示 dept 表的第 0 列（deptno）参与连接
   * 
   * 【注意事项】
   * - 返回的是不可变列表，不能修改
   * - 返回的列表与 leftKeys 的长度相同，且位置一一对应
   * - leftKeys[i] 和 rightKeys[i] 是一对相等的连接条件
   */
  public ImmutableIntList getRightKeys() {
    return rightKeys; // 返回右表连接键列表
  }
}
