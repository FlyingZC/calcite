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
// Apache Calcite是一个动态数据管理框架,提供SQL解析、优化、执行等功能
// 本文件位于adapter/enumerable包下,实现了基于LINQ4J的可枚举调用约定的Hash Join操作
package org.apache.calcite.adapter.enumerable; // 定义包名,该包包含所有实现EnumerableConvention的RelNode

// 导入LINQ4J表达式树构建相关类,用于生成Java代码
import org.apache.calcite.linq4j.tree.BlockBuilder; // 代码块构建器,用于构建Java代码块
import org.apache.calcite.linq4j.tree.Expression; // 表达式基类,表示Java表达式
import org.apache.calcite.linq4j.tree.Expressions; // 表达式工厂类,用于创建各种表达式
// 导入优化器相关类
import org.apache.calcite.plan.DeriveMode; // 特性派生模式,定义如何从子节点派生特性
import org.apache.calcite.plan.RelOptCluster; // 关系表达式集群,包含类型工厂等共享资源
import org.apache.calcite.plan.RelOptCost; // 关系操作的成本接口,用于估算执行成本
import org.apache.calcite.plan.RelOptPlanner; // 关系优化器接口,用于优化查询计划
import org.apache.calcite.plan.RelTraitSet; // 关系特性集合,定义RelNode的物理特性
// 导入关系表达式相关类
import org.apache.calcite.rel.RelCollationTraitDef; // 排序特性定义,描述数据的排序方式
import org.apache.calcite.rel.RelNode; // 关系表达式基类,代表查询计划中的一个操作
import org.apache.calcite.rel.RelNodes; // 关系表达式工具类,提供比较等实用方法
import org.apache.calcite.rel.core.CorrelationId; // 相关性标识,用于标识子查询中的相关性
import org.apache.calcite.rel.core.Join; // Join关系表达式基类,定义Join操作的通用逻辑
import org.apache.calcite.rel.core.JoinRelType; // Join类型枚举,包括INNER、LEFT、RIGHT、FULL、SEMI、ANTI等
// 导入元数据相关类
import org.apache.calcite.rel.metadata.RelMdCollation; // 排序元数据提供者,计算RelNode的排序特性
import org.apache.calcite.rel.metadata.RelMdUtil; // 元数据工具类,提供通用的元数据计算方法
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 元数据查询接口,用于查询RelNode的各种元数据
// 导入Rex表达式相关类
import org.apache.calcite.rex.RexNode; // 行表达式节点,表示SQL中的表达式
import org.apache.calcite.rex.RexUtil; // Rex表达式工具类,提供表达式组合和转换方法
// 导入工具类
import org.apache.calcite.util.BuiltInMethod; // 内置方法枚举,定义了可调用的内置方法
import org.apache.calcite.util.ImmutableIntList; // 不可变整数列表,用于存储字段索引
import org.apache.calcite.util.Pair; // 键值对工具类,用于存储两个相关联的对象
import org.apache.calcite.util.Util; // 通用工具类,提供各种实用方法

import com.google.common.collect.ImmutableList; // Google Guava的不可变列表实现

import org.checkerframework.checker.nullness.qual.Nullable; // 可空注解,用于标记可能为null的值

import java.lang.reflect.Method; // Java反射Method类,用于表示方法
import java.util.List; // Java集合List接口
import java.util.Set; // Java集合Set接口

/**
 * EnumerableHashJoin类详解:
 * 
 * 【类的作用】
 * 这个类实现了基于哈希连接(Hash Join)的Join操作,使用EnumerableConvention调用约定。
 * Hash Join是一种高效的连接算法,特别适合处理大数据集的连接操作。
 * 
 * 【核心概念】
 * 1. Hash Join算法原理:
 *    - 构建阶段(Build Phase): 读取一个输入表(通常是较小的表),根据连接键构建哈希表
 *    - 探测阶段(Probe Phase): 读取另一个输入表,使用连接键在哈希表中查找匹配的行,进行连接
 *    - 优势: 对于大数据集,Hash Join通常比Nested Loop Join更高效,时间复杂度接近O(n+m)
 * 
 * 2. EnumerableConvention:
 *    - 这是Calcite中的一种物理实现约定,表示关系操作可以通过LINQ4J的Enumerable接口执行
 *    - Enumerable接口提供了类似Java Stream的API,支持延迟执行和函数式操作
 * 
 * 3. Join类型支持:
 *    - INNER JOIN: 内连接,只返回两个表中匹配的行
 *    - LEFT OUTER JOIN: 左外连接,返回左表所有行和右表匹配的行,右表不匹配的填充NULL
 *    - RIGHT OUTER JOIN: 右外连接,返回右表所有行和左表匹配的行,左表不匹配的填充NULL
 *    - FULL OUTER JOIN: 全外连接,返回两个表的所有行,不匹配的填充NULL
 *    - SEMI JOIN: 半连接,只返回左表中在右表有匹配的行(不包含右表数据)
 *    - ANTI JOIN: 反连接,只返回左表中在右表没有匹配的行
 * 
 * 4. 实现机制:
 *    - 继承自Join基类,复用了Join的通用逻辑
 *    - 实现EnumerableRel接口,表明可以通过Enumerable方式执行
 *    - 使用LINQ4J的代码生成能力,在运行时生成高效的Java代码
 * 
 * 【使用场景】
 * - 当连接条件包含等值条件时,Hash Join是最佳选择
 * - 当一个输入表明显小于另一个表时,Hash Join效率最高
 * - 适合大数据量的连接操作,特别是OLAP场景
 * 
 * 【性能特点】
 * - 构建哈希表需要内存,小表作为构建端更高效
 * - 探测阶段的时间复杂度为O(m),其中m是探测端的行数
 * - 支持非等值条件的额外过滤(通过predicate参数)
 * 
 * 【与其他Join实现的区别】
 * - EnumerableNestedLoopJoin: 适用于小数据集或没有索引的情况
 * - EnumerableMergeJoin: 适用于已排序的数据,时间复杂度O(n+m)
 * - EnumerableHashJoin: 适用于等值连接,通常是最通用的选择
 */
public class EnumerableHashJoin extends Join implements EnumerableRel {
  /**
   * 【构造方法详解】
   * 创建一个EnumerableHashJoin实例
   * 
   * 【参数说明】
   * @param cluster - 关系表达式集群,包含共享资源如类型工厂、Rex构建器等
   *                - 在整个查询计划中所有RelNode共享同一个cluster
   *                - cluster提供了访问元数据、类型系统等核心功能的入口
   * @param traits - 关系特性集合,定义此RelNode的物理特性
   *                - 包含约定(Convention),此处必须是EnumerableConvention
   *                - 可能包含排序(Collation)、分布(Distribution)等特性
   *                - traits影响优化器的物理计划选择
   * @param left - 左子节点,代表Join操作的左输入关系
   *              - 通常作为探测端(Probe side),因为Hash Join可以优化探测
   *              - 必须是实现了EnumerableRel接口的RelNode
   *              - left和right的顺序会影响Join的实现和性能
   * @param right - 右子节点,代表Join操作的右输入关系
   *               - 通常作为构建端(Build side),用于构建哈希表
   *               - 必须是实现了EnumerableRel接口的RelNode
   *               - 选择较小的表作为right可以提高性能
   * @param condition - Join条件,表示连接条件的Rex表达式
   *                   - 通常是AND组合的多个等值条件(如left.col1 = right.col2 AND left.col3 = right.col4)
   *                   - 也可能包含非等值条件(如left.col1 > right.col2)
   *                   - 等值条件用于哈希查找,非等值条件作为额外的谓词过滤
   * @param variablesSet - 相关性变量集合,用于处理子查询中的相关性
   *                     - 标识此Join操作需要保留的相关性变量
   *                     - 用于支持相关子查询的去相关化
   *                     - 空集合表示没有相关性变量
   * @param joinType - Join类型,定义Join的语义
   *                 - JoinRelType.INNER: 内连接,只返回匹配的行
   *                 - JoinRelType.LEFT: 左外连接,保留左表所有行
   *                 - JoinRelType.RIGHT: 右外连接,保留右表所有行
   *                 - JoinRelType.FULL: 全外连接,保留两表所有行
   *                 - JoinRelType.SEMI: 半连接,只返回左表有匹配的行
   *                 - JoinRelType.ANTI: 反连接,只返回左表无匹配的行
   * 
   * 【构造逻辑】
   * 1. 调用父类Join的构造方法,传递所有必要参数
   * 2. ImmutableList.of()表示没有系统字段,这是Hash Join的典型情况
   * 3. 父类会解析condition,提取等值连接键和非等值条件
   * 4. 父类会初始化joinInfo对象,包含leftKeys、rightKeys等关键信息
   * 
   * 【注意事项】
   * - 此构造方法是protected的,外部应使用create()工厂方法创建实例
   * - create()方法会自动设置正确的traitSet,包括排序特性
   * - 直接使用此构造方法可能导致traitSet不正确,影响优化和执行
   */
  protected EnumerableHashJoin(
      RelOptCluster cluster, // 关系表达式集群参数
      RelTraitSet traits, // 关系特性集合参数
      RelNode left, // 左子节点参数
      RelNode right, // 右子节点参数
      RexNode condition, // Join条件参数
      Set<CorrelationId> variablesSet, // 相关性变量集合参数
      JoinRelType joinType) { // Join类型参数
    super( // 调用父类Join的构造方法
        cluster, // 传递cluster参数
        traits, // 传递traits参数
        ImmutableList.of(), // 传递空的系统字段列表,Hash Join不需要系统字段
        left, // 传递左子节点
        right, // 传递右子节点
        condition, // 传递Join条件
        variablesSet, // 传递相关性变量集合
        joinType); // 传递Join类型
  }

  /**
   * 【已废弃的构造方法】
   * 这是一个旧版本的构造方法,已被标记为@Deprecated
   * 
   * 【废弃原因】
   * - 使用Set<String>表示variablesStopped,已被Set<CorrelationId>取代
   * - 新版本使用CorrelationId对象,提供更强的类型安全和更好的语义
   * - 为了保持向后兼容性暂时保留,将在2.0版本前移除
   * 
   * 【参数说明】
   * @param leftKeys - 左表的连接键索引列表,已废弃,现在从condition自动推导
   * @param rightKeys - 右表的连接键索引列表,已废弃,现在从condition自动推导
   * @param variablesStopped - 停止传播的变量名集合,已被CorrelationId.setOf()转换
   * 
   * 【转换逻辑】
   * - 将旧的Set<String>转换为新的Set<CorrelationId>
   * - 调用新的构造方法完成初始化
   * - 忽略leftKeys和rightKeys参数,因为新版本从condition自动提取
   */
  @Deprecated // 标记为已废弃,将在2.0版本前移除
  protected EnumerableHashJoin(RelOptCluster cluster, RelTraitSet traits, // 集群和特性参数
      RelNode left, RelNode right, RexNode condition, // 左右子节点和条件
      ImmutableIntList leftKeys, // 左键(已废弃)
      ImmutableIntList rightKeys, // 右键(已废弃)
      JoinRelType joinType, // Join类型
      Set<String> variablesStopped) { // 停止变量集合(旧格式)
    this(cluster, traits, left, right, condition, // 调用新构造方法
        CorrelationId.setOf(variablesStopped), joinType); // 将String集合转换为CorrelationId集合
  }

  /**
   * 【工厂方法详解】
   * 创建EnumerableHashJoin实例的推荐方法
   * 
   * 【方法作用】
   * - 提供一个统一的创建入口,确保正确初始化所有属性
   * - 自动设置正确的traitSet,包括排序特性
   * - 计算并应用Hash Join的排序特性,优化后续操作
   * 
   * 【参数说明】
   * @param left - 左子节点,必须是RelNode类型
   *             - 作为探测端(Probe side)
   *             - 可以是任何实现了EnumerableRel的RelNode
   * @param right - 右子节点,必须是RelNode类型
   *              - 作为构建端(Build side)
   *              - 建议选择较小的表以提高性能
   * @param condition - Join条件,RexNode表达式
   *                  - 包含等值连接条件(必须)
   *                  - 可能包含非等值条件(可选)
   * @param variablesSet - 相关性变量集合,Set<CorrelationId>类型
   *                     - 用于处理子查询相关性
   *                     - 通常是空集合
   * @param joinType - Join类型,JoinRelType枚举值
   *                 - 决定Join的语义和结果集
   * 
   * 【实现步骤】
   * 1. 从left获取RelOptCluster,确保所有RelNode使用同一个cluster
   * 2. 获取元数据查询对象RelMetadataQuery,用于查询各种元数据
   * 3. 创建traitSet:
   *    - 基础traitSet包含EnumerableConvention.INSTANCE,表示使用Enumerable约定
   *    - 使用replaceIfs条件性地替换排序特性
   *    - 调用RelMdCollation.enumerableHashJoin计算Hash Join的排序特性
   *    - 排序特性对于后续的Sort、Merge Join等操作很重要
   * 4. 创建并返回EnumerableHashJoin实例
   * 
   * 【排序特性计算】
   * - Hash Join的排序特性取决于joinType和输入的排序特性
   * - INNER JOIN: 如果left已排序,结果可能保持left的排序
   * - LEFT JOIN: 结果保持left的排序
   * - RIGHT JOIN: 结果保持right的排序
   * - FULL JOIN: 结果没有保证的排序
   * - SEMI/ANTI JOIN: 结果保持left的排序
   * 
   * 【使用建议】
   * - 始终使用此方法创建EnumerableHashJoin,不要直接使用构造方法
   * - 确保condition包含等值连接条件,否则Hash Join不适用
   * - 考虑将较小的表放在right位置,以提高性能
   */
  public static EnumerableHashJoin create( // 静态工厂方法
      RelNode left, // 左子节点
      RelNode right, // 右子节点
      RexNode condition, // Join条件
      Set<CorrelationId> variablesSet, // 相关性变量集合
      JoinRelType joinType) { // Join类型
    final RelOptCluster cluster = left.getCluster(); // 从left获取cluster,确保一致性
    final RelMetadataQuery mq = cluster.getMetadataQuery(); // 获取元数据查询对象
    final RelTraitSet traitSet = // 创建traitSet
        cluster.traitSetOf(EnumerableConvention.INSTANCE) // 基础traitSet,包含Enumerable约定
            .replaceIfs(RelCollationTraitDef.INSTANCE, // 条件性地替换排序特性
                () -> RelMdCollation.enumerableHashJoin(mq, left, right, joinType)); // 计算Hash Join的排序特性
    return new EnumerableHashJoin(cluster, traitSet, left, right, condition, // 创建并返回实例
        variablesSet, joinType); // 传递相关变量和Join类型
  }

  /**
   * 【copy方法详解】
   * 创建当前RelNode的副本,用于优化器的规则匹配和转换
   * 
   * 【方法作用】
   * - 在优化过程中,优化器需要创建RelNode的修改副本
   * - 例如:改变Join类型、改变输入顺序、改变特性集合等
   * - 此方法提供了一种标准的方式来创建修改后的副本
   * 
   * 【参数说明】
   * @param traitSet - 新的特性集合,可能包含不同的排序、分布等特性
   *                 - 优化器可能根据代价模型改变特性
   *                 - 必须包含EnumerableConvention
   * @param condition - 新的Join条件,可能被优化器修改
   *                  - 例如:谓词下推、条件简化等
   *                  - 保持不变则传入当前condition
   * @param left - 新的左子节点,可能被优化器修改
   *             - 例如:输入被替换、顺序被改变等
   *             - 保持不变则传入当前left
   * @param right - 新的右子节点,可能被优化器修改
   *              - 例如:输入被替换、顺序被改变等
   *              - 保持不变则传入当前right
   * @param joinType - 新的Join类型,可能被优化器修改
   *                 - 例如:INNER转为LEFT,LEFT转为INNER等
   *                 - 保持不变则传入当前joinType
   * @param semiJoinDone - 标记是否已完成半连接优化
   *                     - 此参数在Hash Join中不使用,但为了兼容Join基类而保留
   *                     - 某些Join实现可能会利用此参数优化
   * 
   * 【返回值】
   * @return EnumerableHashJoin - 新创建的Hash Join实例
   *                           - 包含修改后的traitSet、condition、left、right、joinType
   *                           - variablesSet保持不变,因为相关性变量不应随副本改变
   * 
   * 【使用场景】
   * 1. 优化器规则转换:
   *    - JoinCommuteRule: 交换左右输入顺序
   *    - JoinPushThroughJoinRule: 将Join下推
   *    - JoinConditionPushRule: 优化Join条件
   * 
   * 2. 特性调整:
   *    - 改变排序特性以适应后续操作
   *    - 改变分布特性以适应并行执行
   * 
   * 3. 物理实现转换:
   *    - 从逻辑Join转换为物理Hash Join
   *    - 从一种物理Join转换为另一种物理Join
   * 
   * 【注意事项】
   * - variablesSet从当前实例继承,不应改变
   * - joinInfo会在父类构造方法中重新计算,基于新的condition
   * - 新实例的cluster与当前实例相同
   * - 此方法会被优化器频繁调用,需要保持高效
   */
  @Override // 重写父类Join的copy方法
  public EnumerableHashJoin copy(RelTraitSet traitSet, RexNode condition, // 特性集合和条件
      RelNode left, RelNode right, JoinRelType joinType, // 左右子节点和Join类型
      boolean semiJoinDone) { // 半连接完成标记(未使用)
    return new EnumerableHashJoin(getCluster(), traitSet, left, right, // 创建新实例
        condition, variablesSet, joinType); // 传递条件和变量集合,variablesSet保持不变
  }

  /**
   * 【passThroughTraits方法详解】
   * 将父节点要求的特性传递给子节点
   * 
   * 【方法作用】
   * - 在优化过程中,父节点可能对当前节点有特定的特性要求
   * - 此方法决定哪些特性应该传递给左右子节点
   * - 不同的Join类型对特性传递有不同的规则
   * 
   * 【核心概念】
   * 1. Trait(特性): 定义RelNode的物理属性,如排序、分布等
   * 2. TraitSet(特性集合): 一个RelNode的所有特性的集合
   * 3. Pass-through(传递): 将父节点的要求传递给子节点
   * 
   * 【参数说明】
   * @param required - 父节点要求的特性集合
   *                 - 可能包含排序要求(如按某列排序)
   *                 - 可能包含分布要求(如按某列分区)
   *                 - 必须包含EnumerableConvention
   * 
   * 【返回值】
   * @return Pair<RelTraitSet, List<RelTraitSet>> - 特性传递结果
   *         - Pair.first: 当前节点应该满足的特性集合
   *         - Pair.second[0]: 左子节点应该满足的特性集合
   *         - Pair.second[1]: 右子节点应该满足的特性集合
   *         - 返回null表示无法满足要求
   * 
   * 【传递规则】
   * 1. INNER JOIN:
   *    - 排序要求可以传递给任一子节点
   *    - 通常传递给左子节点,因为左子节点作为探测端
   *    - 如果要求不能被满足,则返回null
   * 
   * 2. LEFT JOIN:
   *    - 排序要求必须传递给左子节点
   *    - 因为LEFT JOIN的结果保持左表的顺序
   *    - 右子节点不需要满足排序要求
   * 
   * 3. RIGHT JOIN:
   *    - 排序要求必须传递给右子节点
   *    - 因为RIGHT JOIN的结果保持右表的顺序
   *    - 左子节点不需要满足排序要求
   * 
   * 4. FULL JOIN:
   *    - 排序要求不能传递给任一子节点
   *    - 因为FULL JOIN的结果没有保证的顺序
   *    - 返回null,要求需要额外的Sort操作
   * 
   * 5. SEMI/ANTI JOIN:
   *    - 排序要求必须传递给左子节点
   *    - 因为SEMI/ANTI JOIN的结果保持左表的顺序
   *    - 右子节点不需要满足排序要求
   * 
   * 【实现细节】
   * - 使用EnumerableTraitsUtils.passThroughTraitsForJoin工具方法
   * - 根据joinType决定传递策略
   * - left.getRowType().getFieldCount()用于确定左表的字段数
   * - getTraitSet()获取当前节点的特性集合
   * 
   * 【使用场景】
   * 1. 优化器特性传播:
   *    - 父节点要求结果按某列排序
   *    - 优化器询问当前节点能否满足此要求
   *    - 如果可以,将要求传递给子节点
   * 
   * 2. 物理计划优化:
   *    - 避免不必要的Sort操作
   *    - 利用子节点已有的排序特性
   *    - 减少数据重排和内存使用
   * 
   * 【注意事项】
   * - Hash Join本身不保证输出排序,除非输入已排序
   * - 特性传递是优化的关键,可以显著提高性能
   * - 返回null表示需要额外的Sort操作,会增加成本
   */
  @Override // 重写RelNode的passThroughTraits方法
  public @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraits( // 特性传递方法
      final RelTraitSet required) { // 父节点要求的特性集合
    return EnumerableTraitsUtils.passThroughTraitsForJoin( // 使用工具方法计算特性传递
        required, // 传递要求
        joinType, // 传递Join类型
        left.getRowType().getFieldCount(), // 传递左表字段数
        getTraitSet()); // 传递当前特性集合
  }

  /**
   * 【deriveTraits方法详解】
   * 从子节点派生当前节点的特性
   * 
   * 【方法作用】
   * - 与passThroughTraits相反,此方法从子节点派生特性
   * - 当子节点满足某些特性时,当前节点可能也满足这些特性
   * - 主要用于排序特性的派生
   * 
   * 【核心概念】
   * 1. Trait Derivation(特性派生): 从子节点的特性推导出父节点的特性
   * 2. 自底向上: 从叶子节点向根节点传播特性
   * 3. 自顶向下: 从根节点向叶子节点传递要求
   * 
   * 【参数说明】
   * @param childTraits - 子节点实际满足的特性集合
   *                   - 可能包含排序特性
   *                   - 可能包含分布特性
   *                   - 必须包含EnumerableConvention
   * @param childId - 子节点标识,0表示左子节点,1表示右子节点
   *               - 用于区分左右子节点
   *               - 不同的子节点对特性派生有不同的影响
   * 
   * 【返回值】
   * @return Pair<RelTraitSet, List<RelTraitSet>> - 特性派生结果
   *         - Pair.first: 当前节点可以满足的特性集合
   *         - Pair.second[0]: 左子节点应该满足的特性集合(可能为null)
   *         - Pair.second[1]: 右子节点应该满足的特性集合(可能为null)
   *         - 返回null表示无法派生特性
   * 
   * 【派生规则】
   * 1. 从左子节点派生:
   *    - INNER JOIN: 如果左子节点已排序,结果可能保持左子节点的排序
   *    - LEFT JOIN: 如果左子节点已排序,结果保持左子节点的排序
   *    - SEMI/ANTI JOIN: 如果左子节点已排序,结果保持左子节点的排序
   *    - RIGHT/FULL JOIN: 无法从左子节点派生排序特性
   * 
   * 2. 从右子节点派生:
   *    - INNER JOIN: 如果右子节点已排序,结果可能保持右子节点的排序
   *    - RIGHT JOIN: 如果右子节点已排序,结果保持右子节点的排序
   *    - LEFT/FULL JOIN: 无法从右子节点派生排序特性
   *    - SEMI/ANTI JOIN: 无法从右子节点派生排序特性
   * 
   * 3. Hash Join的特殊性:
   *    - Hash Join本身不保证输出排序
   *    - 只有在特定条件下才能派生排序特性
   *    - 派生的排序特性是"可能"的,不是"保证"的
   * 
   * 【实现细节】
   * - 使用EnumerableTraitsUtils.deriveTraitsForJoin工具方法
   * - 目前只支持排序特性的派生
   * - 注释说明"should only derive traits (limited to collation for now)"
   * - childId决定从哪个子节点派生
   * - getTraitSet()获取当前节点的特性集合
   * - right.getTraitSet()获取右子节点的特性集合
   * 
   * 【使用场景】
   * 1. 优化器特性传播:
   *    - 子节点满足某些特性
   *    - 优化器询问当前节点能否满足这些特性
   *    - 如果可以,标记当前节点满足这些特性
   * 
   * 2. 物理计划优化:
   *    - 利用子节点已有的排序特性
   *    - 避免在当前节点后添加Sort操作
   *    - 减少数据重排和内存使用
   * 
   * 3. 成本估算:
   *    - 满足更多特性的计划成本可能更低
   *    - 优化器倾向于选择满足更多特性的计划
   * 
   * 【注意事项】
   * - Hash Join的排序派生是有限的,不如Merge Join可靠
   * - 派生的排序特性可能被后续操作依赖
   * - 如果派生不正确,可能导致查询结果错误
   * - 目前只支持排序特性,未来可能支持其他特性
   */
  @Override // 重写RelNode的deriveTraits方法
  public @Nullable Pair<RelTraitSet, List<RelTraitSet>> deriveTraits( // 特性派生方法
      final RelTraitSet childTraits, final int childId) { // 子节点特性和子节点ID
    // 注释:应该只从左子节点派生特性(目前仅限于排序特性)
    // 这是因为Hash Join的探测端通常是左子节点,探测过程可能保持顺序
    return EnumerableTraitsUtils.deriveTraitsForJoin( // 使用工具方法计算特性派生
        childTraits, // 传递子节点特性
        childId, // 传递子节点ID
        joinType, // 传递Join类型
        getTraitSet(), // 传递当前特性集合
        right.getTraitSet()); // 传递右子节点特性集合
  }

  /**
   * 【getDeriveMode方法详解】
   * 获取特性派生模式
   * 
   * 【方法作用】
   * - 告诉优化器如何从子节点派生特性
   * - 不同的派生模式对应不同的派生策略
   * - 影响优化器的特性传播过程
   * 
   * 【核心概念】
   * 1. DeriveMode(派生模式): 定义如何从子节点派生特性
   * 2. 派生模式类型:
   *    - PROHIBITED: 禁止派生,不从子节点派生任何特性
   *    - LEFT_FIRST: 优先从左子节点派生
   *    - RIGHT_FIRST: 优先从右子节点派生
   *    - BOTH: 同时从左右子节点派生
   * 
   * 【返回值】
   * @return DeriveMode - 特性派生模式
   * 
   * 【派生模式规则】
   * 1. FULL JOIN: 返回PROHIBITED
   *    - FULL JOIN的结果没有保证的排序
   *    - 无法从任一子节点派生排序特性
   *    - 必须禁止派生,避免错误的优化
   * 
   * 2. RIGHT JOIN: 返回PROHIBITED
   *    - RIGHT JOIN的结果保持右表的顺序
   *    - 但Hash Join中右表作为构建端,构建哈希表会打乱顺序
   *    - 无法从右子节点派生排序特性
   *    - 必须禁止派生,避免错误的优化
   * 
   * 3. INNER JOIN: 返回LEFT_FIRST
   *    - INNER JOIN可能保持左表的顺序
    *    - 优先从左子节点派生排序特性
   *    - 如果左子节点已排序,结果可能保持排序
   * 
   * 4. LEFT JOIN: 返回LEFT_FIRST
   *    - LEFT JOIN的结果保持左表的顺序
   *    - 必须从左子节点派生排序特性
   *    - 如果左子节点已排序,结果保持排序
   * 
   * 5. SEMI JOIN: 返回LEFT_FIRST
   *    - SEMI JOIN的结果保持左表的顺序
   *    - 必须从左子节点派生排序特性
   *    - 如果左子节点已排序,结果保持排序
   * 
   * 6. ANTI JOIN: 返回LEFT_FIRST
   *    - ANTI JOIN的结果保持左表的顺序
   *    - 必须从左子节点派生排序特性
   *    - 如果左子节点已排序,结果保持排序
   * 
   * 【实现细节】
   * - 根据joinType返回相应的派生模式
   * - FULL和RIGHT JOIN返回PROHIBITED
   * - 其他类型返回LEFT_FIRST
   * 
   * 【使用场景】
   * 1. 优化器特性传播:
   *    - 优化器根据派生模式决定如何传播特性
   *    - PROHIBITED模式阻止特性传播
   *    - LEFT_FIRST模式优先从左子节点传播特性
   * 
   * 2. 物理计划优化:
   *    - 避免错误的排序假设
   *    - 确保查询结果的正确性
   *    - 提高优化器的可靠性
   * 
   * 【注意事项】
   * - RIGHT JOIN返回PROHIBITED可能看起来违反直觉
   * - 这是因为Hash Join中右表作为构建端,顺序会被破坏
   * - 如果需要保证RIGHT JOIN的排序,应该使用Merge Join
   * - 派生模式的选择对查询结果的正确性至关重要
   */
  @Override // 重写RelNode的getDeriveMode方法
  public DeriveMode getDeriveMode() { // 获取特性派生模式
    if (joinType == JoinRelType.FULL || joinType == JoinRelType.RIGHT) { // 如果是FULL或RIGHT JOIN
      return DeriveMode.PROHIBITED; // 返回禁止派生模式
      // 原因:
      // 1. FULL JOIN的结果没有保证的排序
      // 2. RIGHT JOIN中右表作为构建端,构建哈希表会打乱顺序
      // 3. 必须禁止派生,避免错误的优化
    }

    return DeriveMode.LEFT_FIRST; // 其他类型返回左优先模式
    // 原因:
    // 1. INNER/LEFT/SEMI/ANTI JOIN可能保持左表的顺序
    // 2. 左表作为探测端,探测过程可能保持顺序
    // 3. 优先从左子节点派生排序特性
  }

  /**
   * 【computeSelfCost方法详解】
   * 计算Hash Join操作的成本
   * 
   * 【方法作用】
   * - 估算执行Hash Join所需的成本
   * - 优化器使用成本来选择最优的执行计划
   * - 成本模型影响Join顺序、Join类型等决策
   * 
   * 【核心概念】
   * 1. RelOptCost(关系操作成本): 表示执行一个RelNode的成本
   * 2. 成本组成部分:
   *    - rowCount: 产生的行数,影响I/O和后续操作
   *    - cpu: CPU成本,表示计算复杂度
   *    - io: I/O成本,表示磁盘访问次数
   * 3. Hash Join成本模型:
   *    - 构建哈希表成本: O(n), n是构建端的行数
   *    - 探测哈希表成本: O(m), m是探测端的行数
   *    - 总成本: O(n + m)
   * 
   * 【参数说明】
   * @param planner - 优化器实例,用于获取成本工厂
   *                - 提供makeCost方法创建成本对象
   *                - 包含成本计算的全局配置
   * @param mq - 元数据查询对象,用于查询各种元数据
   *           - 用于获取当前节点和子节点的行数
   *           - 用于获取其他影响成本的元数据
   * 
   * 【返回值】
   * @return RelOptCost - Hash Join的成本对象
   *         - rowCount: 估算的输出行数
   *         - cpu: 0(简化模型,不单独计算CPU成本)
   *         - io: 0(简化模型,不单独计算I/O成本)
   *         - SEMI/ANTI JOIN的成本乘以0.01,使其更便宜
   * 
   * 【成本计算步骤】
   * 
   * 步骤1: 获取输出行数
   * - 使用mq.getRowCount(this)获取当前节点的估算行数
   * - 这是Join操作产生的行数
   * - 不同Join类型的输出行数不同:
   *   - INNER JOIN: min(leftRowCount, rightRowCount) 到 leftRowCount * rightRowCount
   *   - LEFT JOIN: leftRowCount
   *   - RIGHT JOIN: rightRowCount
   *   - FULL JOIN: leftRowCount + rightRowCount
   *   - SEMI JOIN: <= leftRowCount
   *   - ANTI JOIN: <= leftRowCount
   * 
   * 步骤2: 处理Join翻转稳定性
   * - Join可以翻转(交换左右输入),两个版本都有相同的成本
   * - 为了使优化器结果稳定,使其中一个版本稍微更贵
   * - 使用RelMdUtil.addEpsilon添加一个很小的值(epsilon)
   * - 稳定性意味着相同的查询在不同优化器版本中产生相同的计划
   * 
   * 步骤3: 根据Join类型调整成本
   * - SEMI/ANTI JOIN: 不能翻转,不添加epsilon
   * - RIGHT JOIN: 添加两次epsilon,使其比LEFT JOIN更贵
   *   - 这样优化器倾向于使用LEFT JOIN而不是RIGHT JOIN
   *   - 因为LEFT JOIN通常更容易优化
   * - 其他类型: 如果left > right(按某种比较),添加epsilon
   *   - 使用RelNodes.COMPARATOR比较左右节点
   *   - 使left <= right的版本更便宜
   * 
   * 步骤4: 添加构建端成本
   * - 获取左表行数: leftRowCount
   * - 如果leftRowCount是无穷大,直接使用无穷大
   * - 否则,添加Util.nLogN(leftRowCount)
   * - nLogN表示构建哈希表的成本: n * log2(n)
   * - log2(n)表示哈希冲突的平均查找次数
   * 
   * 步骤5: 添加探测端成本
   * - 获取右表行数: rightRowCount
   * - 如果rightRowCount是无穷大,直接使用无穷大
   * - 否则,添加rightRowCount
   * - 探测成本是线性的,因为哈希查找平均是O(1)
   * 
   * 步骤6: 处理SEMI/ANTI JOIN
   * - 如果是SEMI/ANTI JOIN,成本乘以0.01
   * - 使SEMI/ANTI JOIN比普通Join更便宜
   * - 因为SEMI/ANTI JOIN只返回左表的行,不包含右表数据
   * - 这鼓励优化器使用EXISTS/NOT EXISTS而不是IN/NOT IN
   * 
   * 【成本模型细节】
   * 
   * 1. 构建端成本:
   * - 读取构建端的数据: O(n)
   * - 计算哈希值: O(n)
   * - 插入哈希表: O(n * log2(n)),考虑哈希冲突
   * - 总构建成本: O(n * log2(n))
   * 
   * 2. 探测端成本:
   * - 读取探测端的数据: O(m)
   * - 计算哈希值: O(m)
   * - 哈希查找: O(m),平均O(1)
   * - 连接结果: O(k), k是匹配的行数
   * - 总探测成本: O(m + k)
   * 
   * 3. 总成本:
   * - 构建成本 + 探测成本
   * - O(n * log2(n) + m + k)
   * - 简化为O(n + m)
   * 
   * 4. 成本优化:
   * - 选择较小的表作为构建端: n越小,构建成本越低
   * - 选择较大的表作为探测端: m越大,但探测成本低
   * - 减少哈希冲突: 好的哈希函数,合适的哈希表大小
   * 
   * 【使用场景】
   * 1. 优化器成本比较:
   *    - 比较不同Join顺序的成本
   *    - 比较不同Join类型(INNER vs LEFT)的成本
   *    - 比较不同Join实现(Hash vs Merge vs Nested Loop)的成本
   * 
   * 2. Join顺序优化:
   *    - 选择最优的Join顺序
   *    - 考虑Join的累积成本
   *    - 使用动态规划或贪心算法
   * 
   * 3. 物理实现选择:
   *    - 选择Hash Join vs Merge Join vs Nested Loop Join
   *    - 根据数据量和Join条件选择最优实现
   * 
   * 【注意事项】
   * - 成本模型是近似的,不是精确的
   * - 实际执行成本可能因硬件、数据分布等因素而不同
   * - 成本模型需要根据实际情况调整
   * - SEMI/ANTI JOIN的成本乘以0.01是一个启发式规则
   * - Join翻转的epsilon处理是为了稳定性,不是性能考虑
   */
  @Override // 重写RelNode的computeSelfCost方法
  public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 优化器参数
      RelMetadataQuery mq) { // 元数据查询参数
    double rowCount = mq.getRowCount(this); // 获取当前节点的估算行数

    // 注释:Join可以翻转,对于许多算法,两个版本都是可行的
    // 并且具有相同的成本。为了使优化器版本之间的结果稳定,
    // 使其中一个版本稍微更贵。
    // 
    // 稳定性意味着相同的查询在不同优化器版本中产生相同的计划
    // 这对于调试、测试和用户预期很重要
    switch (joinType) { // 根据Join类型处理
    case SEMI: // SEMI JOIN
    case ANTI: // ANTI JOIN
      // SEMI和ANTI Join不能翻转
      // 原因:SEMI/ANTI JOIN的语义依赖于左右顺序
      // 不需要添加epsilon
      break; // 跳过
    case RIGHT: // RIGHT JOIN
      rowCount = RelMdUtil.addEpsilon(rowCount); // 添加epsilon,使成本稍高
      // 原因:使RIGHT JOIN比LEFT JOIN更贵
      // 优化器倾向于使用LEFT JOIN而不是RIGHT JOIN
      // 因为LEFT JOIN通常更容易优化
      break; // 跳过
    default: // 其他类型(INNER, LEFT, FULL)
      if (RelNodes.COMPARATOR.compare(left, right) > 0) { // 如果left > right
        rowCount = RelMdUtil.addEpsilon(rowCount); // 添加epsilon,使成本稍高
        // 原因:使left > right的版本更贵
        // 优化器倾向于选择left <= right的版本
        // 这样可以保证Join顺序的一致性
      }
    }

    // 注释:如果较小的行数来自LHS(左端),则更便宜。
    // 通过添加L log L到成本来建模这一点。
    // 
    // Hash Join的成本模型:
    // 1. 构建端成本: n * log2(n), n是构建端的行数
    // 2. 探测端成本: m, m是探测端的行数
    // 3. 总成本: n * log2(n) + m
    // 
    // 这里假设左端是构建端,右端是探测端
    // 实际上,优化器可能会交换左右输入以优化成本
    final double rightRowCount = mq.getRowCount(right); // 获取右表行数
    final double leftRowCount = mq.getRowCount(left); // 获取左表行数
    if (Double.isInfinite(leftRowCount)) { // 如果左表行数是无穷大
      rowCount = leftRowCount; // 使用无穷大
      // 原因:避免数值溢出,直接使用无穷大表示极高的成本
    } else { // 如果左表行数有限
      rowCount += Util.nLogN(leftRowCount); // 添加左表的构建成本: n * log2(n)
      // nLogN = n * log2(n)
      // log2(n)表示哈希冲突的平均查找次数
      // 这是构建哈希表的成本
    }
    if (Double.isInfinite(rightRowCount)) { // 如果右表行数是无穷大
      rowCount = rightRowCount; // 使用无穷大
      // 原因:避免数值溢出,直接使用无穷大表示极高的成本
    } else { // 如果右表行数有限
      rowCount += rightRowCount; // 添加右表的探测成本: m
      // 探测成本是线性的,因为哈希查找平均是O(1)
      // 这是探测哈希表的成本
    }
    if (isSemiJoin()) { // 如果是SEMI JOIN
      return planner.getCostFactory().makeCost(rowCount, 0, 0).multiplyBy(.01d); // 成本乘以0.01
      // 原因:SEMI JOIN只返回左表的行,不包含右表数据
      // 成本应该比普通Join更低
      // 这鼓励优化器使用EXISTS而不是IN
    } else { // 如果是普通Join
      return planner.getCostFactory().makeCost(rowCount, 0, 0); // 返回标准成本
      // rowCount: 输出行数
      // cpu: 0(简化模型)
      // io: 0(简化模型)
    }
  }

  /**
   * 【implement方法详解】
   * 实现Hash Join的代码生成
   * 
   * 【方法作用】
   * - 将Hash Join操作转换为可执行的Java代码
   * - 使用LINQ4J的代码生成能力
   * - 生成高效的Join实现代码
   * 
   * 【核心概念】
   * 1. EnumerableRelImplementor: 代码生成器,负责生成Java代码
   * 2. Result: 代码生成结果,包含生成的代码块和物理类型
   * 3. Prefer: 代码生成偏好,如是否使用数组格式
   * 4. 代码生成: 在运行时生成Java代码,然后编译执行
   * 
   * 【参数说明】
   * @param implementor - 代码生成器实例
   *                   - 负责访问子节点并生成代码
   *                   - 提供代码生成的上下文和工具方法
   *                   - 管理变量名、类型等代码生成细节
   * @param pref - 代码生成偏好
   *             - pref.preferArray(): 是否使用数组格式存储行
   *             - 影响生成的代码风格
   * 
   * 【返回值】
   * @return Result - 代码生成结果
   *         - block: 生成的Java代码块
   *         - physType: 结果行的物理类型
   * 
   * 【实现步骤】
   * 
   * 步骤1: 根据Join类型选择实现方法
   * - SEMI JOIN: 使用implementHashSemiJoin方法
   * - ANTI JOIN: 使用implementHashSemiJoin方法
   * - 其他类型(INNER, LEFT, RIGHT, FULL): 使用implementHashJoin方法
   * 
   * 步骤2: SEMI/ANTI JOIN实现
   * - 调用implementHashSemiJoin方法
   * - 生成半连接或反连接的代码
   * - 使用BuiltInMethod.SEMI_JOIN或BuiltInMethod.ANTI_JOIN
   * - 只返回左表的行,不包含右表数据
   * 
   * 步骤3: 普通Join实现
   * - 调用implementHashJoin方法
   * - 生成普通Join的代码
   * - 使用BuiltInMethod.HASH_JOIN
   * - 返回连接后的完整行
   * 
   * 【代码生成原理】
   * 
   * 1. SEMI/ANTI JOIN代码生成:
   * - 访问左子节点,生成左表的枚举代码
   * - 访问右子节点,生成右表的枚举代码
   * - 生成连接键的访问器代码
   * - 生成比较器代码(如果需要)
   * - 生成谓词代码(如果有非等值条件)
   * - 调用SEMI_JOIN或ANTI_JOIN方法
   * - 返回结果代码块
   * 
   * 2. 普通Join代码生成:
   * - 访问左子节点,生成左表的枚举代码
   * - 访问右子节点,生成右表的枚举代码
   * - 生成连接键的访问器代码
   * - 生成Join选择器代码(用于组合左右行)
   * - 生成比较器代码(如果需要)
   * - 生成谓词代码(如果有非等值条件)
   * - 调用HASH_JOIN方法
   * - 返回结果代码块
   * 
   * 【生成的代码结构】
   * 
   * 1. SEMI JOIN示例:
   * ```java
   * Enumerable<String> left = ...;
   * Enumerable<String> right = ...;
   * Enumerable<String> result = EnumerableDefaults.semiJoin(
   *     left,
   *     right,
   *     leftKeyAccessor,
   *     rightKeyAccessor,
   *     keyComparer,
   *     predicate
   * );
   * return result;
   * ```
   * 
   * 2. 普通Join示例:
   * ```java
   * Enumerable<String> left = ...;
   * Enumerable<String> right = ...;
   * Enumerable<String> result = left.hashJoin(
   *     right,
   *     leftKeyAccessor,
   *     rightKeyAccessor,
   *     joinSelector,
   *     keyComparer,
   *     generateNullsOnLeft,
   *     generateNullsOnRight,
   *     predicate
   * );
   * return result;
   * ```
   * 
   * 【使用场景】
   * 1. 查询执行:
   *    - 优化器选择Hash Join作为物理实现
   *    - 调用implement方法生成代码
   *    - 编译并执行生成的代码
   * 
   * 2. 代码生成:
   *    - 在运行时生成Java代码
   *    - 避免硬编码所有可能的Join场景
   *    - 提高灵活性和性能
   * 
   * 【注意事项】
   * - 代码生成是Calcite的核心特性之一
   * - 生成的代码是类型安全的
   * - 生成的代码是高效的,接近手写代码
   * - SEMI/ANTI JOIN和普通Join使用不同的实现方法
   * - 代码生成需要考虑各种边界情况(如NULL值处理)
   */
  @Override // 重写EnumerableRel的implement方法
  public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 实现代码生成
    switch (joinType) { // 根据Join类型选择实现
    case SEMI: // SEMI JOIN
    case ANTI: // ANTI JOIN
      return implementHashSemiJoin(implementor, pref); // 调用半连接实现方法
      // 半连接只返回左表的行,不包含右表数据
      // 使用SEMI_JOIN或ANTI_JOIN内置方法
    default: // 其他类型(INNER, LEFT, RIGHT, FULL)
      return implementHashJoin(implementor, pref); // 调用普通Join实现方法
      // 普通Join返回连接后的完整行
      // 使用HASH_JOIN内置方法
    }
  }

  /**
   * 【implementHashSemiJoin方法详解】
   * 实现SEMI JOIN或ANTI JOIN的代码生成
   * 
   * 【方法作用】
   * - 生成SEMI JOIN或ANTI JOIN的Java代码
   * - SEMI JOIN: 只返回左表中在右表有匹配的行(类似EXISTS)
   * - ANTI JOIN: 只返回左表中在右表没有匹配的行(类似NOT EXISTS)
   * - 不返回右表的数据,只返回左表的行
   * 
   * 【核心概念】
   * 1. SEMI JOIN(半连接):
   *    - 类似于SQL中的EXISTS子查询
   *    - 只返回左表的行,不包含右表数据
   *    - 如果左表行在右表有匹配,则返回该行
   * 
   * 2. ANTI JOIN(反连接):
   *    - 类似于SQL中的NOT EXISTS子查询
   *    - 只返回左表的行,不包含右表数据
   *    - 如果左表行在右表没有匹配,则返回该行
   * 
   * 3. Hash Semi Join算法:
   *    - 构建阶段: 读取右表,构建哈希表
   *    - 探测阶段: 读取左表,在哈希表中查找
   *    - SEMI: 如果找到匹配,返回左表行
   *    - ANTI: 如果没有找到匹配,返回左表行
   * 
   * 【参数说明】
   * @param implementor - 代码生成器实例
   * @param pref - 代码生成偏好
   * 
   * 【返回值】
   * @return Result - 代码生成结果,包含生成的代码块
   * 
   * 【实现步骤】
   * 
   * 步骤1: 验证Join类型
   * - 使用assert确保joinType是SEMI或ANTI
   * - 如果不是,抛出AssertionError
   * 
   * 步骤2: 选择内置方法
   * - SEMI JOIN: 使用BuiltInMethod.SEMI_JOIN.method
   * - ANTI JOIN: 使用BuiltInMethod.ANTI_JOIN.method
   * - 这些方法在EnumerableDefaults类中实现
   * 
   * 步骤3: 创建代码块构建器
   * - BlockBuilder用于构建Java代码块
   * - 管理变量声明和表达式
   * 
   * 步骤4: 访问左子节点
   * - 调用implementor.visitChild访问左子节点
   * - 生成左表的枚举代码
   * - leftResult包含代码块和物理类型
   * 
   * 步骤5: 添加左表表达式
   * - 将左表代码块添加到builder
   * - 变量名为"left"
   * - leftExpression表示左表的枚举对象
   * 
   * 步骤6: 访问右子节点
   * - 调用implementor.visitChild访问右子节点
   * - 生成右表的枚举代码
   * - rightResult包含代码块和物理类型
   * 
   * 步骤7: 添加右表表达式
   * - 将右表代码块添加到builder
   * - 变量名为"right"
   * - rightExpression表示右表的枚举对象
   * 
   * 步骤8: 确定结果物理类型
   * - 使用leftResult.physType作为结果类型
   * - 因为结果只包含左表的行
   * 
   * 步骤9: 生成键的物理类型
   * - 使用leftResult.physType.project投影连接键
   * - joinInfo.leftKeys包含左表连接键的字段索引
   * - JavaRowFormat.LIST表示使用List格式存储键
   * 
   * 步骤10: 生成谓词表达式
   * - 初始化predicate为null
   * - 如果有非等值条件(nonEquiConditions非空):
   *   - 使用RexUtil.composeConjunction组合非等值条件
   *   - 使用EnumUtils.generatePredicate生成谓词代码
   *   - 谓词用于额外的过滤
   * 
   * 步骤11: 生成Join调用表达式
   * - 调用SEMI_JOIN或ANTI_JOIN方法
   * - 参数包括:
   *   - leftExpression: 左表枚举
   *   - rightExpression: 右表枚举
   *   - leftKeyAccessor: 左表连接键访问器
   *   - rightKeyAccessor: 右表连接键访问器
   *   - keyComparer: 键比较器(如果需要)
   *   - predicate: 非等值条件谓词
   * 
   * 步骤12: 返回结果
   * - 使用implementor.result包装结果
   * - 包含物理类型和代码块
   * 
   * 【生成的代码示例】
   * 
   * SEMI JOIN:
   * ```java
   * // 左表枚举
   * Enumerable<Employee> left = employees.where(e -> e.deptId > 10);
   * // 右表枚举
   * Enumerable<Department> right = departments;
   * // SEMI JOIN调用
   * Enumerable<Employee> result = EnumerableDefaults.semiJoin(
   *     left,
   *     right,
   *     (Employee e) -> e.deptId,  // 左键访问器
   *     (Department d) -> d.id,    // 右键访问器
   *     null,                      // 比较器(使用默认)
   *     null                       // 谓词(无非等值条件)
   * );
   * return result;
   * ```
   * 
   * ANTI JOIN:
   * ```java
   * // 左表枚举
   * Enumerable<Employee> left = employees.where(e -> e.salary < 5000);
   * // 右表枚举
   * Enumerable<Department> right = departments.where(d -> d.budget > 100000);
   * // ANTI JOIN调用
   * Enumerable<Employee> result = EnumerableDefaults.antiJoin(
   *     left,
   *     right,
   *     (Employee e) -> e.deptId,  // 左键访问器
   *     (Department d) -> d.id,    // 右键访问器
   *     null,                      // 比较器(使用默认)
   *     null                       // 谓词(无非等值条件)
   * );
   * return result;
   * ```
   * 
   * 【使用场景】
   * 1. EXISTS子查询:
   *    - SELECT * FROM emp WHERE EXISTS (SELECT 1 FROM dept WHERE dept.id = emp.deptId)
   *    - 转换为SEMI JOIN实现
   * 
   * 2. NOT EXISTS子查询:
   *    - SELECT * FROM emp WHERE NOT EXISTS (SELECT 1 FROM dept WHERE dept.id = emp.deptId AND dept.budget > 100000)
   *    - 转换为ANTI JOIN实现
   * 
   * 3. IN子查询:
   *    - SELECT * FROM emp WHERE emp.deptId IN (SELECT id FROM dept WHERE dept.budget > 100000)
   *    - 可以转换为SEMI JOIN实现
   * 
   * 4. NOT IN子查询:
   *    - SELECT * FROM emp WHERE emp.deptId NOT IN (SELECT id FROM dept WHERE dept.budget > 100000)
   *    - 可以转换为ANTI JOIN实现(需要考虑NULL值)
   * 
   * 【注意事项】
   * - SEMI/ANTI JOIN不返回右表数据,只返回左表行
   * - 只需要构建右表的哈希表,不需要存储右表的所有数据
   * - 半连接通常比普通Join更高效,因为不需要组合左右行
   * - 非等值条件作为额外的谓词过滤
   * - 谓词在哈希查找之后应用
   */
  private Result implementHashSemiJoin(EnumerableRelImplementor implementor, Prefer pref) { // 实现半连接代码生成
    assert joinType == JoinRelType.SEMI || joinType == JoinRelType.ANTI; // 断言Join类型是SEMI或ANTI
    final Method method = joinType == JoinRelType.SEMI // 根据Join类型选择方法
        ? BuiltInMethod.SEMI_JOIN.method // SEMI JOIN使用SEMI_JOIN方法
        : BuiltInMethod.ANTI_JOIN.method; // ANTI JOIN使用ANTI_JOIN方法
    BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器
    final Result leftResult = // 访问左子节点
        implementor.visitChild(this, 0, (EnumerableRel) left, pref); // 生成左表代码
    Expression leftExpression = // 添加左表表达式
        builder.append( // 添加到代码块
            "left", leftResult.block); // 变量名为"left"
    final Result rightResult = // 访问右子节点
        implementor.visitChild(this, 1, (EnumerableRel) right, pref); // 生成右表代码
    Expression rightExpression = // 添加右表表达式
        builder.append( // 添加到代码块
            "right", rightResult.block); // 变量名为"right"
    final PhysType physType = leftResult.physType; // 结果物理类型使用左表类型
    final PhysType keyPhysType = // 生成键的物理类型
        leftResult.physType.project( // 投影连接键
            joinInfo.leftKeys, JavaRowFormat.LIST); // 使用List格式
    Expression predicate = Expressions.constant(null); // 初始化谓词为null
    if (!joinInfo.nonEquiConditions.isEmpty()) { // 如果有非等值条件
      RexNode nonEquiCondition = // 组合非等值条件
          RexUtil.composeConjunction(getCluster().getRexBuilder(), // 使用Rex构建器
              joinInfo.nonEquiConditions, true); // 组合条件,允许null
      if (nonEquiCondition != null) { // 如果组合后的条件不为null
        predicate = // 生成谓词代码
            EnumUtils.generatePredicate(implementor, // 使用代码生成器
                getCluster().getRexBuilder(), left, right, leftResult.physType, // 传递参数
                rightResult.physType, nonEquiCondition); // 传递非等值条件
      }
    }
    return implementor.result( // 返回代码生成结果
        physType, // 结果物理类型
        builder.append( // 添加Join调用表达式
            Expressions.call( // 创建方法调用表达式
                method, // 调用SEMI_JOIN或ANTI_JOIN方法
                Expressions.list( // 参数列表
                    leftExpression, // 左表枚举
                    rightExpression, // 右表枚举
                    leftResult.physType.generateAccessorWithoutNulls(joinInfo.leftKeys), // 左键访问器
                    rightResult.physType.generateAccessorWithoutNulls(joinInfo.rightKeys), // 右键访问器
                    Util.first(keyPhysType.comparer(), // 键比较器(如果有)
                        Expressions.constant(null)), // 否则使用null
                    predicate))) // 非等值条件谓词
            .toBlock()); // 转换为代码块
  }

  /**
   * 【implementHashJoin方法详解】
   * 实现普通Hash Join(INNER/LEFT/RIGHT/FULL)的代码生成
   * 
   * 【方法作用】
   * - 生成普通Hash Join的Java代码
   * - 支持INNER JOIN、LEFT OUTER JOIN、RIGHT OUTER JOIN、FULL OUTER JOIN
   * - 返回连接后的完整行,包含左右表的数据
   * 
   * 【核心概念】
   * 1. Hash Join算法:
   *    - 构建阶段(Build Phase): 读取构建端(通常是右表),构建哈希表
   *    - 探测阶段(Probe Phase): 读取探测端(通常是左表),在哈希表中查找匹配
   *    - 连接阶段: 组合匹配的行,生成结果
   * 
   * 2. Join类型:
   *    - INNER JOIN: 只返回匹配的行
   *    - LEFT OUTER JOIN: 返回左表所有行,右表不匹配的填充NULL
   *    - RIGHT OUTER JOIN: 返回右表所有行,左表不匹配的填充NULL
   *    - FULL OUTER JOIN: 返回两表所有行,不匹配的填充NULL
   * 
   * 3. Join选择器:
   *    - 用于组合左右表的数据
   *    - 根据Join类型决定如何处理NULL值
   * 
   * 【参数说明】
   * @param implementor - 代码生成器实例
   * @param pref - 代码生成偏好
   * 
   * 【返回值】
   * @return Result - 代码生成结果,包含生成的代码块
   * 
   * 【实现步骤】
   * 
   * 步骤1: 创建代码块构建器
   * - BlockBuilder用于构建Java代码块
   * - 管理变量声明和表达式
   * 
   * 步骤2: 访问左子节点
   * - 调用implementor.visitChild访问左子节点
   * - 生成左表的枚举代码
   * - leftResult包含代码块和物理类型
   * 
   * 步骤3: 添加左表表达式
   * - 将左表代码块添加到builder
   * - 变量名为"left"
   * - leftExpression表示左表的枚举对象
   * 
   * 步骤4: 访问右子节点
   * - 调用implementor.visitChild访问右子节点
   * - 生成右表的枚举代码
   * - rightResult包含代码块和物理类型
   * 
   * 步骤5: 添加右表表达式
   * - 将右表代码块添加到builder
   * - 变量名为"right"
   * - rightExpression表示右表的枚举对象
   * 
   * 步骤6: 确定结果物理类型
   * - 使用PhysTypeImpl.of创建结果物理类型
   * - 基于getRowType(连接后的行类型)
   * - 使用pref.preferArray()决定是否使用数组格式
   * 
   * 步骤7: 生成键的物理类型
   * - 使用leftResult.physType.project投影连接键
   * - joinInfo.leftKeys包含左表连接键的字段索引
   * - JavaRowFormat.LIST表示使用List格式存储键
   * 
   * 步骤8: 生成谓词表达式
   * - 初始化predicate为null
   * - 如果有非等值条件(nonEquiConditions非空):
   *   - 使用RexUtil.composeConjunction组合非等值条件
   *   - 使用EnumUtils.generatePredicate生成谓词代码
   *   - 谓词用于额外的过滤
   * 
   * 步骤9: 生成Join调用表达式
   * - 调用leftExpression.hashJoin方法
   * - 参数包括:
   *   - rightExpression: 右表枚举
   *   - leftKeyAccessor: 左表连接键访问器
   *   - rightKeyAccessor: 右表连接键访问器
   *   - joinSelector: Join选择器,用于组合左右行
   *   - keyComparer: 键比较器(如果需要)
   *   - generateNullsOnLeft: 是否在左侧生成NULL(LEFT/FULL JOIN)
   *   - generateNullsOnRight: 是否在右侧生成NULL(RIGHT/FULL JOIN)
   *   - predicate: 非等值条件谓词
   * 
   * 步骤10: 生成Join选择器
   * - 使用EnumUtils.joinSelector生成选择器
   * - 选择器根据joinType处理NULL值
   * - 组合左右表的数据
   * 
   * 步骤11: 返回结果
   * - 使用implementor.result包装结果
   * - 包含物理类型和代码块
   * 
   * 【生成的代码示例】
   * 
   * INNER JOIN:
   * ```java
   * // 左表枚举
   * Enumerable<Employee> left = employees.where(e -> e.salary > 3000);
   * // 右表枚举
   * Enumerable<Department> right = departments.where(d -> d.budget > 50000);
   * // 结果物理类型
   * PhysType physType = ...;
   * // INNER JOIN调用
   * Enumerable<Object> result = left.hashJoin(
   *     right,
   *     (Employee e) -> e.deptId,  // 左键访问器
   *     (Department d) -> d.id,    // 右键访问器
   *     (Employee e, Department d) -> new Object[] { // Join选择器
   *         e.id, e.name, e.salary, d.id, d.name, d.budget
   *     },
   *     null,                      // 比较器(使用默认)
   *     false,                     // 不在左侧生成NULL
   *     false,                     // 不在右侧生成NULL
   *     null                       // 谓词(无非等值条件)
   * );
   * return result;
   * ```
   * 
   * LEFT OUTER JOIN:
   * ```java
   * // 左表枚举
   * Enumerable<Employee> left = employees;
   * // 右表枚举
   * Enumerable<Department> right = departments;
   * // LEFT OUTER JOIN调用
   * Enumerable<Object> result = left.hashJoin(
   *     right,
   *     (Employee e) -> e.deptId,  // 左键访问器
   *     (Department d) -> d.id,    // 右键访问器
   *     (Employee e, Department d) -> new Object[] { // Join选择器
   *         e.id, e.name, e.salary, 
   *         d != null ? d.id : null,  // 可能是NULL
   *         d != null ? d.name : null,// 可能是NULL
   *         d != null ? d.budget : null // 可能是NULL
   *     },
   *     null,                      // 比较器(使用默认)
   *     true,                      // 在左侧生成NULL
   *     false,                     // 不在右侧生成NULL
   *     null                       // 谓词(无非等值条件)
   * );
   * return result;
   * ```
   * 
   * 【Join选择器详解】
   * 
   * 1. INNER JOIN选择器:
   * - 只组合匹配的行
   * - 不处理NULL值
   * - 简单的属性组合
   * 
   * 2. LEFT OUTER JOIN选择器:
   * - 左表所有行都返回
   * - 右表不匹配的填充NULL
   * - 需要检查右表是否为null
   * 
   * 3. RIGHT OUTER JOIN选择器:
   * - 右表所有行都返回
   * - 左表不匹配的填充NULL
   * - 需要检查左表是否为null
   * 
   * 4. FULL OUTER JOIN选择器:
   * - 两表所有行都返回
   * - 不匹配的填充NULL
   * - 需要检查左右表是否为null
   * 
   * 【使用场景】
   * 1. INNER JOIN:
   *    - SELECT * FROM emp, dept WHERE emp.deptId = dept.id
   *    - SELECT * FROM emp INNER JOIN dept ON emp.deptId = dept.id
   * 
   * 2. LEFT OUTER JOIN:
   *    - SELECT * FROM emp LEFT JOIN dept ON emp.deptId = dept.id
   *    - 返回所有员工,即使没有部门
   * 
   * 3. RIGHT OUTER JOIN:
   *    - SELECT * FROM emp RIGHT JOIN dept ON emp.deptId = dept.id
   *    - 返回所有部门,即使没有员工
   * 
   * 4. FULL OUTER JOIN:
   *    - SELECT * FROM emp FULL JOIN dept ON emp.deptId = dept.id
   *    - 返回所有员工和所有部门
   * 
   * 【注意事项】
   * - Hash Join需要等值连接条件
   * - 非等值条件作为额外的谓词过滤
   * - 谓词在哈希查找之后应用
   * - NULL值处理由Join选择器负责
   * - generateNullsOnLeft/OnRight决定是否填充NULL
   * - Join选择器的代码由EnumUtils.joinSelector生成
   * - 结果物理类型基于连接后的行类型
   */
  private Result implementHashJoin(EnumerableRelImplementor implementor, Prefer pref) { // 实现普通Join代码生成
    BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器
    final Result leftResult = // 访问左子节点
        implementor.visitChild(this, 0, (EnumerableRel) left, pref); // 生成左表代码
    Expression leftExpression = // 添加左表表达式
        builder.append( // 添加到代码块
            "left", leftResult.block); // 变量名为"left"
    final Result rightResult = // 访问右子节点
        implementor.visitChild(this, 1, (EnumerableRel) right, pref); // 生成右表代码
    Expression rightExpression = // 添加右表表达式
        builder.append( // 添加到代码块
            "right", rightResult.block); // 变量名为"right"
    final PhysType physType = // 创建结果物理类型
        PhysTypeImpl.of( // 使用PhysTypeImpl工厂方法
            implementor.getTypeFactory(), getRowType(), pref.preferArray()); // 基于行类型和偏好
    final PhysType keyPhysType = // 生成键的物理类型
        leftResult.physType.project( // 投影连接键
            joinInfo.leftKeys, JavaRowFormat.LIST); // 使用List格式
    Expression predicate = Expressions.constant(null); // 初始化谓词为null
    if (!joinInfo.nonEquiConditions.isEmpty()) { // 如果有非等值条件
      RexNode nonEquiCondition = // 组合非等值条件
          RexUtil.composeConjunction(getCluster().getRexBuilder(), // 使用Rex构建器
              joinInfo.nonEquiConditions, true); // 组合条件,允许null
      if (nonEquiCondition != null) { // 如果组合后的条件不为null
        predicate = // 生成谓词代码
            EnumUtils.generatePredicate(implementor, // 使用代码生成器
                getCluster().getRexBuilder(), left, right, leftResult.physType, // 传递参数
                rightResult.physType, nonEquiCondition); // 传递非等值条件
      }
    }
    return implementor.result( // 返回代码生成结果
        physType, // 结果物理类型
        builder.append( // 添加Join调用表达式
            Expressions.call( // 创建方法调用表达式
                leftExpression, // 在左表枚举上调用hashJoin方法
                BuiltInMethod.HASH_JOIN.method, // 调用HASH_JOIN方法
                Expressions.list( // 参数列表
                    rightExpression, // 右表枚举
                    leftResult.physType.generateAccessorWithoutNulls(joinInfo.leftKeys), // 左键访问器
                    rightResult.physType.generateAccessorWithoutNulls(joinInfo.rightKeys), // 右键访问器
                    EnumUtils.joinSelector(joinType, // Join选择器
                        physType, // 结果物理类型
                        ImmutableList.of( // 左右表物理类型列表
                            leftResult.physType, rightResult.physType))) // 传递左右表类型
                    .append( // 追加参数
                        Util.first(keyPhysType.comparer(), // 键比较器(如果有)
                            Expressions.constant(null))) // 否则使用null
                    .append( // 追加参数
                        Expressions.constant(joinType.generatesNullsOnLeft())) // 是否在左侧生成NULL
                    .append( // 追加参数
                        Expressions.constant( // 是否在右侧生成NULL
                            joinType.generatesNullsOnRight()))
                    .append(predicate))) // 非等值条件谓词
            .toBlock()); // 转换为代码块
  }
} // 类结束
