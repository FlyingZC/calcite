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
package org.apache.calcite.adapter.enumerable;  // 声明包名，该类位于可枚举适配器包中

import org.apache.calcite.adapter.java.JavaTypeFactory;  // 导入Java类型工厂，用于创建Java类型
import org.apache.calcite.linq4j.EnumerableDefaults;  // 导入LINQ4j默认实现类，提供可枚举集合的默认方法
import org.apache.calcite.linq4j.tree.BlockBuilder;  // 导入代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.Expression;  // 导入表达式类，用于表示Java表达式
import org.apache.calcite.linq4j.tree.Expressions;  // 导入表达式工具类，用于创建表达式
import org.apache.calcite.linq4j.tree.ParameterExpression;  // 导入参数表达式类，用于表示方法参数
import org.apache.calcite.plan.DeriveMode;  // 导入派生模式枚举，定义如何从子节点派生特征
import org.apache.calcite.plan.RelOptCluster;  // 导入关系优化集群，包含表达式工厂和类型工厂
import org.apache.calcite.plan.RelOptCost;  // 导入关系优化成本接口，用于表示操作成本
import org.apache.calcite.plan.RelOptPlanner;  // 导入关系优化规划器，用于优化查询计划
import org.apache.calcite.plan.RelTraitSet;  // 导入关系特征集合，描述关系的物理属性
import org.apache.calcite.rel.RelCollation;  // 导入排序特征，描述字段的排序顺序
import org.apache.calcite.rel.RelCollationTraitDef;  // 导入排序特征定义，用于定义排序特征
import org.apache.calcite.rel.RelCollations;  // 导入排序工具类，提供排序相关的工具方法
import org.apache.calcite.rel.RelFieldCollation;  // 导入字段排序类，描述单个字段的排序方式
import org.apache.calcite.rel.RelNode;  // 导入关系节点接口，所有关系操作符的基类
import org.apache.calcite.rel.core.CorrelationId;  // 导入关联ID，用于标识相关变量
import org.apache.calcite.rel.core.Join;  // 导入Join基类，所有连接操作符的父类
import org.apache.calcite.rel.core.JoinRelType;  // 导入连接类型枚举，如INNER、LEFT、RIGHT等
import org.apache.calcite.rel.metadata.RelMdCollation;  // 导入排序元数据提供者
import org.apache.calcite.rel.metadata.RelMetadataQuery;  // 导入元数据查询接口
import org.apache.calcite.rel.type.RelDataType;  // 导入关系数据类型，描述列的类型
import org.apache.calcite.rex.RexNode;  // 导入行表达式节点，表示行上的表达式
import org.apache.calcite.rex.RexUtil;  // 导入行表达式工具类，提供表达式操作方法
import org.apache.calcite.util.BuiltInMethod;  // 导入内置方法枚举，定义Calcite内置方法
import org.apache.calcite.util.ImmutableBitSet;  // 导入不可变位集，用于高效表示字段集合
import org.apache.calcite.util.ImmutableIntList;  // 导入不可变整数列表，用于存储字段索引
import org.apache.calcite.util.Pair;  // 导入键值对类，用于存储两个相关对象
import org.apache.calcite.util.Util;  // 导入通用工具类，提供各种实用方法
import org.apache.calcite.util.mapping.Mappings;  // 导入映射工具类，用于字段映射

import com.google.common.collect.ImmutableList;  // 导入Google Guava的不可变列表
import com.google.common.collect.ImmutableSet;  // 导入Google Guava的不可变集合

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入可空注解，用于标记可能为null的值

import java.lang.reflect.Type;  // 导入Java类型类
import java.util.ArrayList;  // 导入Java动态数组列表
import java.util.HashMap;  // 导入Java哈希映射
import java.util.List;  // 导入Java列表接口
import java.util.Map;  // 导入Java映射接口
import java.util.Set;  // 导入Java集合接口

import static com.google.common.base.Preconditions.checkArgument;  // 静态导入参数检查方法

import static org.apache.calcite.rel.RelCollations.containsOrderless;  // 静态导入排序包含检查方法

import static java.util.Objects.requireNonNull;  // 静态导入非空检查方法

/** Implementation of {@link org.apache.calcite.rel.core.Join} in  // 类注释：这是Join操作符在可枚举调用约定下的实现
 * {@link EnumerableConvention enumerable calling convention} using  // 使用可枚举调用约定
 * a merge algorithm. */  // 使用归并排序算法实现连接
public class EnumerableMergeJoin extends Join implements EnumerableRel {  // 类定义：可枚举归并连接类，继承自Join并实现EnumerableRel接口
  protected EnumerableMergeJoin(  // 构造方法：创建可枚举归并连接节点
      RelOptCluster cluster,  // 参数cluster：关系优化集群，包含表达式工厂和类型工厂
      RelTraitSet traits,  // 参数traits：关系特征集合，包含物理属性如排序约定
      RelNode left,  // 参数left：左子节点，连接的左输入
      RelNode right,  // 参数right：右子节点，连接的右输入
      RexNode condition,  // 参数condition：连接条件，通常为等值条件
      Set<CorrelationId> variablesSet,  // 参数variablesSet：相关变量集合，用于处理相关子查询
      JoinRelType joinType) {  // 参数joinType：连接类型，如INNER、LEFT、RIGHT、SEMI等
    super(cluster, traits, ImmutableList.of(), left, right, condition, variablesSet, joinType);  // 调用父类Join的构造方法初始化基础属性
    assert getConvention() instanceof EnumerableConvention;  // 断言：确保调用约定是可枚举约定
    final List<RelCollation> leftCollations = getCollations(left.getTraitSet());  // 获取左子节点的所有排序特征
    final List<RelCollation> rightCollations = getCollations(right.getTraitSet());  // 获取右子节点的所有排序特征

    // If the join keys are not distinct, the sanity check doesn't apply.  // 如果连接键不唯一，则不需要进行完整性检查
    // e.g. t1.a=t2.b and t1.a=t2.c  // 例如：t1.a=t2.b and t1.a=t2.c，这种情况下连接键不唯一
    boolean isDistinct = Util.isDistinct(joinInfo.leftKeys)  // 检查左连接键是否唯一（无重复）
        && Util.isDistinct(joinInfo.rightKeys);  // 检查右连接键是否唯一（无重复）

    if (!RelCollations.collationsContainKeysOrderless(leftCollations, joinInfo.leftKeys)  // 如果左子节点的排序不包含左连接键
        || !RelCollations.collationsContainKeysOrderless(rightCollations, joinInfo.rightKeys)) {  // 或右子节点的排序不包含右连接键
      if (isDistinct) {  // 如果连接键是唯一的
        throw new RuntimeException("wrong collation in left or right input");  // 抛出异常：左右输入的排序不正确
      }  // 如果连接键不唯一，则不抛出异常，因为可能存在重复键的情况
    }  // 归并连接要求输入数据必须按照连接键排序

    final List<RelCollation> collations =  // 获取当前节点的排序特征列表
        traits.getTraits(RelCollationTraitDef.INSTANCE);  // 从特征集合中获取排序特征
    requireNonNull(collations, "collations");  // 检查排序特征不为null
    checkArgument(!collations.isEmpty());  // 检查排序特征列表不为空
    ImmutableIntList rightKeys = joinInfo.rightKeys  // 将右连接键的索引转换为全局索引（相对于整个连接结果）
        .incr(left.getRowType().getFieldCount());  // 增加左表的字段数，使右键索引指向全局位置
    // Currently it has very limited ability to represent the equivalent traits  // 当前表示等价特征的能力非常有限
    // due to the flaw of RelCompositeTrait, so the following case is totally  // 由于RelCompositeTrait的缺陷，以下情况是完全合法的
    // legit, but not yet supported:  // 但暂不支持：
    // SELECT * FROM foo JOIN bar ON foo.a = bar.c AND foo.b = bar.d;  // 查询示例：foo.a = bar.c AND foo.b = bar.d
    // MergeJoin has collation on [a, d], or [b, c]  // 归并连接可能有[a, d]或[b, c]的排序
    if (!RelCollations.collationsContainKeysOrderless(collations, joinInfo.leftKeys)  // 如果当前排序不包含左连接键
        && !RelCollations.collationsContainKeysOrderless(collations, rightKeys)  // 且不包含右连接键
        && !RelCollations.keysContainCollationsOrderless(joinInfo.leftKeys, collations)  // 且左连接键不包含当前排序
        && !RelCollations.keysContainCollationsOrderless(rightKeys, collations)) {  // 且右连接键不包含当前排序
      if (isDistinct) {  // 如果连接键是唯一的
        throw new RuntimeException("wrong collation for mergejoin");  // 抛出异常：归并连接的排序不正确
      }  // 这个检查确保归并连接的排序特征是合理的
    }  // 归并连接要求输出数据的排序必须与连接键相关
    if (!isMergeJoinSupported(joinType)) {  // 检查是否支持该连接类型
      throw new UnsupportedOperationException(  // 抛出不支持操作异常
          "EnumerableMergeJoin unsupported for join type " + joinType);  // 提示不支持的连接类型
    }  // 归并连接只支持部分连接类型（如INNER、LEFT等）
  }  // 构造方法结束

  public static boolean isMergeJoinSupported(JoinRelType joinType) {  // 静态方法：检查是否支持指定类型的归并连接
    return EnumerableDefaults.isMergeJoinSupported(EnumUtils.toLinq4jJoinType(joinType));  // 调用LINQ4j的默认实现检查支持性，将Calcite连接类型转换为LINQ4j类型
  }  // 方法结束：返回布尔值表示是否支持

  private static RelCollation getCollation(RelTraitSet traits) {  // 私有静态方法：从特征集合中获取单个排序特征
    return requireNonNull(traits.getCollation(),  // 获取排序特征并确保不为null
        () -> "no collation trait in " + traits);  // 如果为null，提供错误信息
  }  // 方法结束：返回排序特征对象

  private static List<RelCollation> getCollations(RelTraitSet traits) {  // 私有静态方法：从特征集合中获取所有排序特征
    return requireNonNull(traits.getTraits(RelCollationTraitDef.INSTANCE),  // 获取排序特征列表并确保不为null
        () -> "no collation trait in " + traits);  // 如果为null，提供错误信息
  }  // 方法结束：返回排序特征列表

  @Deprecated // to be removed before 2.0  // 注释：该方法已废弃，将在2.0版本前移除
  EnumerableMergeJoin(RelOptCluster cluster, RelTraitSet traits, RelNode left,  // 废弃构造方法：使用显式的左右连接键创建归并连接
      RelNode right, RexNode condition, ImmutableIntList leftKeys,  // 参数leftKeys：左连接键索引列表（已废弃，现在从joinInfo中获取）
      ImmutableIntList rightKeys, Set<CorrelationId> variablesSet,  // 参数rightKeys：右连接键索引列表（已废弃）
      JoinRelType joinType) {  // 调用新的构造方法，忽略leftKeys和rightKeys参数
    this(cluster, traits, left, right, condition, variablesSet, joinType);  // 委托给主构造方法
  }  // 废弃构造方法结束

  @Deprecated // to be removed before 2.0  // 注释：该方法已废弃，将在2.0版本前移除
  EnumerableMergeJoin(RelOptCluster cluster, RelTraitSet traits, RelNode left,  // 废弃构造方法：使用旧式的variablesStopped参数
      RelNode right, RexNode condition, ImmutableIntList leftKeys,  // 参数variablesStopped：已停止的变量名集合（旧式API）
      ImmutableIntList rightKeys, JoinRelType joinType,  // 现在使用CorrelationId集合代替字符串集合
      Set<String> variablesStopped) {  // 将字符串集合转换为CorrelationId集合
    this(cluster, traits, left, right, condition, leftKeys, rightKeys,  // 委托给前一个废弃构造方法
        CorrelationId.setOf(variablesStopped), joinType);  // 转换并传递相关变量集合
  }  // 废弃构造方法结束

  /**
   * Pass collations through can have three cases:  // 方法注释：将排序特征传递给子节点有三种情况
   *
   * <p>1. If sort keys are equal to either left join keys, or right join keys,  // 情况1：如果排序键等于左连接键或右连接键
   * collations can be pushed to both join sides with correct mappings.  // 排序特征可以通过正确的映射传递到连接的两边
   * For example, for the query  // 例如，对于查询
   *
   * <blockquote><pre>{@code  // 代码块示例
   *    select * from foo join bar  // 从foo和bar表连接查询
   *        on foo.a=bar.b  // 连接条件：foo.a等于bar.b
   *    order by foo.a desc  // 按foo.a降序排序
   * }</pre></blockquote>  // 代码块结束
   *
   * <p>after traits pass through it will be equivalent to  // 特征传递后等价于
   *
   * <blockquote><pre>{@code  // 代码块示例
   *    select * from  // 查询
   *        (select * from foo order by foo.a desc)  // 左表按foo.a降序排序
   *        join  // 连接
   *        (select * from bar order by bar.b desc)  // 右表按bar.b降序排序
   * }</pre></blockquote>  // 代码块结束
   *
   * <p>2. If sort keys are sub-set of either left join keys, or right join  // 情况2：如果排序键是左连接键或右连接键的子集
   * keys, collations have to be extended to cover all joins keys before  // 排序特征必须扩展以覆盖所有连接键
   * passing through, because merge join requires all join keys are sorted.  // 因为归并连接要求所有连接键都必须排序
   * For example, for the query  // 例如，对于查询
   *
   * <blockquote><pre>{@code  // 代码块示例
   *    select * from foo join bar  // 从foo和bar表连接查询
   *        on foo.a=bar.b and foo.c=bar.d  // 连接条件：foo.a=bar.b且foo.c=bar.d
   *    order by foo.a desc  // 按foo.a降序排序
   * }</pre></blockquote>  // 代码块结束
   *
   * <p>after traits pass through it will be equivalent to  // 特征传递后等价于
   *
   * <blockquote><pre>{@code  // 代码块示例
   *    select * from  // 查询
   *        (select * from foo order by foo.a desc, foo.c)  // 左表按foo.a降序、foo.c升序排序
   *        join  // 连接
   *        (select * from bar order by bar.b desc, bar.d)  // 右表按bar.b降序、bar.d升序排序
   * }</pre></blockquote>  // 代码块结束
   *
   * <p>3. If sort keys are super-set of either left join keys, or right join  // 情况3：如果排序键是左连接键或右连接键的超集
   * keys, but not both, collations can be completely passed to the join key  // 但不是两者都是，排序特征可以完全传递给连接键
   * whose join keys match the prefix of collations. Meanwhile, partial mapped  // 其连接键匹配排序的前缀。同时，部分映射的
   * collations can be passed to another join side to make sure join keys are  // 排序特征可以传递给另一边以确保连接键排序
   * sorted. For example, for the query  // 例如，对于查询

   * <blockquote><pre>{@code  // 代码块示例
   *    select * from foo join bar  // 从foo和bar表连接查询
   *        on foo.a=bar.b and foo.c=bar.d  // 连接条件：foo.a=bar.b且foo.c=bar.d
   *        order by foo.a desc, foo.c desc, foo.e  // 按foo.a降序、foo.c降序、foo.e排序
   * }</pre></blockquote>  // 代码块结束
   *
   * <p>after traits pass through it will be equivalent to  // 特征传递后等价于
   *
   * <blockquote><pre>{@code  // 代码块示例
   *    select * from  // 查询
   *        (select * from foo order by foo.a desc, foo.c desc, foo.e)  // 左表按foo.a降序、foo.c降序、foo.e排序
   *        join  // 连接
   *        (select * from bar order by bar.b desc, bar.d desc)  // 右表按bar.b降序、bar.d降序排序
   * }</pre></blockquote>  // 代码块结束
   */
  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraits(  // 重写方法：将排序特征传递给子节点
      final RelTraitSet required) {  // 参数required：需要传递给子节点的特征集合
    // Required collation keys can be subset or superset of merge join keys.  // 注释：要求的排序键可以是归并连接键的子集或超集
    RelCollation collation = getCollation(required);  // 获取要求的排序特征
    int leftInputFieldCount = left.getRowType().getFieldCount();  // 获取左输入的字段数量

    List<Integer> reqKeys = RelCollations.ordinals(collation);  // 获取排序键的字段索引列表
    List<Integer> leftKeys = joinInfo.leftKeys.toIntegerList();  // 获取左连接键的索引列表
    List<Integer> rightKeys =  // 获取右连接键的索引列表（转换为全局索引）
        joinInfo.rightKeys.incr(leftInputFieldCount).toIntegerList();  // 增加左表字段数以转换为全局索引

    ImmutableBitSet reqKeySet = ImmutableBitSet.of(reqKeys);  // 将排序键转换为不可变位集
    ImmutableBitSet leftKeySet = ImmutableBitSet.of(joinInfo.leftKeys);  // 将左连接键转换为不可变位集
    ImmutableBitSet rightKeySet = ImmutableBitSet.of(joinInfo.rightKeys)  // 将右连接键转换为不可变位集（全局索引）
        .shift(leftInputFieldCount);  // 向左移动以转换为全局索引

    if (reqKeySet.equals(leftKeySet)) {  // 如果排序键等于左连接键
      // if sort keys equal to left join keys, we can pass through all collations directly.  // 注释：如果排序键等于左连接键，可以直接传递所有排序特征
      Mappings.TargetMapping mapping = buildMapping(true);  // 构建从左到右的映射
      RelCollation rightCollation = collation.apply(mapping);  // 应用映射得到右边的排序特征
      return Pair.of(  // 返回键值对
          required, ImmutableList.of(required,  // 第一个元素：当前节点的特征集合
          required.replace(rightCollation)));  // 第二个元素：左右子节点的特征集合列表（左边不变，右边用映射后的排序）
    } else if (containsOrderless(leftKeys, collation)) {  // 如果排序键是左连接键的子集
      // if sort keys are subset of left join keys, we can extend collations to make sure all join  // 注释：如果排序键是左连接键的子集，扩展排序以确保所有连接键排序
      // keys are sorted.  // 归并连接要求所有连接键都必须排序
      collation = extendCollation(collation, leftKeys);  // 扩展排序特征以包含所有左连接键
      Mappings.TargetMapping mapping = buildMapping(true);  // 构建从左到右的映射
      RelCollation rightCollation = collation.apply(mapping);  // 应用映射得到右边的排序特征
      return Pair.of(  // 返回键值对
          required, ImmutableList.of(required.replace(collation),  // 第一个元素：当前节点的特征集合
              required.replace(rightCollation)));  // 第二个元素：左右子节点的特征集合列表（左边用扩展后的排序，右边用映射后的排序）
    } else if (containsOrderless(collation, leftKeys)  // 如果排序键包含左连接键
        && reqKeys.stream().allMatch(i -> i < leftInputFieldCount)) {  // 且所有排序键都来自左表
      // if sort keys are superset of left join keys, and left join keys is prefix of sort keys  // 注释：如果排序键是左连接键的超集，且左连接键是排序键的前缀
      // (order not matter), also sort keys are all from left join input.  // （顺序不重要），且排序键都来自左输入
      Mappings.TargetMapping mapping = buildMapping(true);  // 构建从左到右的映射
      RelCollation rightCollation =  // 计算右边的排序特征
          RexUtil.apply(  // 应用映射
              mapping,  // 映射关系
              intersectCollationAndJoinKey(collation, joinInfo.leftKeys));  // 取排序和连接键的交集
      return Pair.of(  // 返回键值对
          required, ImmutableList.of(required,  // 第一个元素：当前节点的特征集合
              required.replace(rightCollation)));  // 第二个元素：左右子节点的特征集合列表（左边不变，右边用交集后的排序）
    } else if (reqKeySet.equals(rightKeySet)) {  // 如果排序键等于右连接键
      // if sort keys equal to right join keys, we can pass through all collations directly.  // 注释：如果排序键等于右连接键，可以直接传递所有排序特征
      RelCollation rightCollation = RelCollations.shift(collation, -leftInputFieldCount);  // 将排序特征转换为右表局部索引
      Mappings.TargetMapping mapping = buildMapping(false);  // 构建从右到左的映射
      RelCollation leftCollation = rightCollation.apply(mapping);  // 应用映射得到左边的排序特征
      return Pair.of(  // 返回键值对
          required, ImmutableList.of(  // 第一个元素：当前节点的特征集合
          required.replace(leftCollation),  // 第二个元素：左右子节点的特征集合列表（左边用映射后的排序，右边用局部排序）
          required.replace(rightCollation)));
    } else if (containsOrderless(rightKeys, collation)) {  // 如果排序键是右连接键的子集
      // if sort keys are subset of right join keys, we can extend collations to make sure all join  // 注释：如果排序键是右连接键的子集，扩展排序以确保所有连接键排序
      // keys are sorted.  // 归并连接要求所有连接键都必须排序
      collation = extendCollation(collation, rightKeys);  // 扩展排序特征以包含所有右连接键
      RelCollation rightCollation = RelCollations.shift(collation, -leftInputFieldCount);  // 将排序特征转换为右表局部索引
      Mappings.TargetMapping mapping = buildMapping(false);  // 构建从右到左的映射
      RelCollation leftCollation = RexUtil.apply(mapping, rightCollation);  // 应用映射得到左边的排序特征
      return Pair.of(  // 返回键值对
          required, ImmutableList.of(  // 第一个元素：当前节点的特征集合
              required.replace(leftCollation),  // 第二个元素：左右子节点的特征集合列表（左边用映射后的排序，右边用扩展后的局部排序）
              required.replace(rightCollation)));
    } else if (containsOrderless(collation, rightKeys)  // 如果排序键包含右连接键
        && reqKeys.stream().allMatch(i -> i >= leftInputFieldCount)) {  // 且所有排序键都来自右表
      // if sort keys are superset of right join keys, and right join keys is prefix of sort keys  // 注释：如果排序键是右连接键的超集，且右连接键是排序键的前缀
      // (order not matter), also sort keys are all from right join input.  // （顺序不重要），且排序键都来自右输入
      RelCollation rightCollation = RelCollations.shift(collation, -leftInputFieldCount);  // 将排序特征转换为右表局部索引
      Mappings.TargetMapping mapping = buildMapping(false);  // 构建从右到左的映射
      RelCollation leftCollation =  // 计算左边的排序特征
          RexUtil.apply(  // 应用映射
              mapping,  // 映射关系
              intersectCollationAndJoinKey(rightCollation, joinInfo.rightKeys));  // 取排序和连接键的交集
      return Pair.of(  // 返回键值对
          required, ImmutableList.of(  // 第一个元素：当前节点的特征集合
              required.replace(leftCollation),  // 第二个元素：左右子节点的特征集合列表（左边用交集后的排序，右边用局部排序）
              required.replace(rightCollation)));
    }  // 以上六种情况涵盖了排序特征传递的主要场景

    return null;  // 如果无法传递排序特征，返回null
  }  // 方法结束

  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> deriveTraits(  // 重写方法：从子节点派生特征
      final RelTraitSet childTraits, final int childId) {  // 参数childTraits：子节点的特征集合，参数childId：子节点ID（0=左，1=右）
    final int keyCount = joinInfo.leftKeys.size();  // 获取连接键的数量
    RelCollation collation = getCollation(childTraits);  // 从子节点特征中获取排序特征
    final int colCount = collation.getFieldCollations().size();  // 获取排序字段的数量
    if (colCount < keyCount || keyCount == 0) {  // 如果排序字段数少于连接键数或连接键数为0
      return null;  // 返回null，无法派生特征
    }  // 归并连接要求排序字段数至少等于连接键数

    if (colCount > keyCount) {  // 如果排序字段数多于连接键数
      collation = RelCollations.of(collation.getFieldCollations().subList(0, keyCount));  // 截取前keyCount个排序字段
    }  // 只保留连接键对应的排序字段

    ImmutableIntList sourceKeys = childId == 0 ? joinInfo.leftKeys : joinInfo.rightKeys;  // 根据childId选择源连接键
    ImmutableBitSet keySet = ImmutableBitSet.of(sourceKeys);  // 将源连接键转换为位集
    ImmutableBitSet childCollationKeys =  // 将子节点的排序字段转换为位集
        ImmutableBitSet.of(RelCollations.ordinals(collation));  // 获取排序字段的索引
    if (!childCollationKeys.equals(keySet)) {  // 如果排序字段不等于连接键
      return null;  // 返回null，无法派生特征
    }  // 子节点的排序必须完全匹配连接键

    Mappings.TargetMapping mapping = buildMapping(childId == 0);  // 构建从源到目标的映射
    RelCollation targetCollation = collation.apply(mapping);  // 应用映射得到目标节点的排序特征

    if (childId == 0) {  // 如果是左子节点（childId=0）
      // traits from left child  // 注释：从左子节点派生特征
      RelTraitSet joinTraits = getTraitSet().replace(collation);  // 创建连接节点的特征集合，使用子节点的排序
      // Forget about the equiv keys for the moment  // 注释：暂时忽略等价键
      return Pair.of(joinTraits,  // 返回键值对
          ImmutableList.of(childTraits,  // 第一个元素：连接节点的特征集合
          right.getTraitSet().replace(targetCollation)));  // 第二个元素：左右子节点的特征集合列表（左边不变，右边用映射后的排序）
    } else {  // 如果是右子节点（childId=1）
      // traits from right child  // 注释：从右子节点派生特征
      assert childId == 1;  // 断言：确保childId为1
      RelTraitSet joinTraits = getTraitSet().replace(targetCollation);  // 创建连接节点的特征集合，使用映射后的排序
      // Forget about the equiv keys for the moment  // 注释：暂时忽略等价键
      return Pair.of(joinTraits,  // 返回键值对
          ImmutableList.of(joinTraits,  // 第一个元素：连接节点的特征集合
          childTraits.replace(collation)));  // 第二个元素：左右子节点的特征集合列表（左边用连接节点的特征，右边不变）
    }  // 根据子节点ID返回不同的特征派生结果
  }  // 方法结束

  @Override public DeriveMode getDeriveMode() {  // 重写方法：获取特征派生模式
    return DeriveMode.BOTH;  // 返回BOTH模式，表示可以从任意子节点派生特征
  }  // 方法结束：归并连接可以从左右两边派生排序特征

  private Mappings.TargetMapping buildMapping(boolean left2Right) {  // 私有方法：构建连接键之间的映射
    ImmutableIntList sourceKeys = left2Right ? joinInfo.leftKeys : joinInfo.rightKeys;  // 根据方向选择源连接键
    ImmutableIntList targetKeys = left2Right ? joinInfo.rightKeys : joinInfo.leftKeys;  // 根据方向选择目标连接键
    Map<Integer, Integer> keyMap = new HashMap<>();  // 创建键映射的哈希表
    for (int i = 0; i < joinInfo.leftKeys.size(); i++) {  // 遍历所有连接键对
      keyMap.put(sourceKeys.get(i), targetKeys.get(i));  // 建立源键到目标键的映射
    }  // 例如：left2Right=true时，映射左表的字段索引到右表的字段索引

    return Mappings.target(keyMap,  // 创建目标映射
        (left2Right ? left : right).getRowType().getFieldCount(),  // 源表的字段数
        (left2Right ? right : left).getRowType().getFieldCount());  // 目标表的字段数
  }  // 方法结束：返回连接键的映射对象

  /**
   * This function extends collation by appending new collation fields defined on keys.  // 方法注释：此函数通过添加键上定义的新排序字段来扩展排序特征
   */
  private static RelCollation extendCollation(RelCollation collation, List<Integer> keys) {  // 私有静态方法：扩展排序特征以包含所有键
    List<RelFieldCollation> fieldsForNewCollation = new ArrayList<>(keys.size());  // 创建新排序字段列表
    fieldsForNewCollation.addAll(collation.getFieldCollations());  // 添加原有的排序字段

    ImmutableBitSet keysBitset = ImmutableBitSet.of(keys);  // 将所有键转换为位集
    ImmutableBitSet colKeysBitset = ImmutableBitSet.of(collation.getKeys());  // 将当前排序的键转换为位集
    ImmutableBitSet exceptBitset = keysBitset.except(colKeysBitset);  // 计算差集：在keys中但不在当前排序中的键
    for (Integer i : exceptBitset) {  // 遍历差集中的每个键
      fieldsForNewCollation.add(new RelFieldCollation(i));  // 添加新的排序字段（默认升序）
    }  // 这样可以确保所有连接键都被排序
    return RelCollations.of(fieldsForNewCollation);  // 返回扩展后的排序特征
  }  // 方法结束：返回包含所有键的排序特征

  /**
   * This function will remove collations that are not defined on join keys.  // 方法注释：此函数将移除未在连接键上定义的排序字段
   * For example:  // 例如：
   *    select * from  // 查询
   *    foo join bar  // foo和bar表连接
   *    on foo.a = bar.a and foo.c=bar.c  // 连接条件：foo.a=bar.a且foo.c=bar.c
   *    order by bar.a, bar.c, bar.b;  // 按bar.a、bar.c、bar.b排序
   *
   * <p>The collation [bar.a, bar.c, bar.b] can be pushed down to bar. However,  // 排序[bar.a, bar.c, bar.b]可以下推到bar表。但是，
   * only [a, c] can be pushed down to foo. This function will help create [a,  // 只有[a, c]可以下推到foo表。此函数将通过从要求的排序中移除b来为foo创建[a,
   * c] for foo by removing b from the required collation, because b is not  // c]，因为b未在连接键上定义
   * defined on join keys.  // b不是连接键，所以不能传递到foo表
   *
   * @param collation collation defined on the JOIN  // 参数collation：连接上定义的排序特征
   * @param joinKeys  the join keys  // 参数joinKeys：连接键列表
   */
  private static RelCollation intersectCollationAndJoinKey(  // 私有静态方法：取排序和连接键的交集
      RelCollation collation, ImmutableIntList joinKeys) {  // 参数collation：排序特征，参数joinKeys：连接键
    List<RelFieldCollation> fieldCollations = new ArrayList<>();  // 创建新的排序字段列表
    for (RelFieldCollation rf : collation.getFieldCollations()) {  // 遍历当前排序的所有字段
      if (joinKeys.contains(rf.getFieldIndex())) {  // 如果该字段是连接键之一
        fieldCollations.add(rf);  // 保留该排序字段
      }  // 否则忽略该字段
    }  // 这样可以只保留连接键上的排序
    return RelCollations.of(fieldCollations);  // 返回只包含连接键的排序特征
  }  // 方法结束：返回排序和连接键的交集

  public static EnumerableMergeJoin create(RelNode left, RelNode right,  // 静态工厂方法：创建可枚举归并连接节点
      RexNode condition, ImmutableIntList leftKeys,  // 参数condition：连接条件，参数leftKeys：左连接键索引
      ImmutableIntList rightKeys, JoinRelType joinType) {  // 参数rightKeys：右连接键索引，参数joinType：连接类型
    final RelOptCluster cluster = right.getCluster();  // 获取关系优化集群
    RelTraitSet traitSet = cluster.traitSetOf(EnumerableConvention.INSTANCE);  // 创建包含可枚举约定的特征集合
    if (traitSet.isEnabled(RelCollationTraitDef.INSTANCE)) {  // 如果启用了排序特征定义
      final RelMetadataQuery mq = cluster.getMetadataQuery();  // 获取元数据查询对象
      final List<RelCollation> collations =  // 计算归并连接的排序特征
          RelMdCollation.mergeJoin(mq, left, right, leftKeys, rightKeys, joinType);  // 基于左右输入和连接键计算排序
      traitSet = traitSet.replaceIfs(RelCollationTraitDef.INSTANCE, () -> collations);  // 替换特征集合中的排序特征
    }  // 如果启用了排序特征，则计算并设置排序
    return new EnumerableMergeJoin(cluster, traitSet, left, right, condition,  // 创建并返回新的归并连接节点
        ImmutableSet.of(), joinType);  // 使用空的相关变量集合
  }  // 方法结束：返回新创建的归并连接节点

  @Override public EnumerableMergeJoin copy(RelTraitSet traitSet,  // 重写方法：创建归并连接的副本
      RexNode condition, RelNode left, RelNode right, JoinRelType joinType,  // 参数：新特征集合、连接条件、左右子节点、连接类型
      boolean semiJoinDone) {  // 参数semiJoinDone：是否完成半连接处理（未使用）
    return new EnumerableMergeJoin(getCluster(), traitSet, left, right,  // 创建新的归并连接节点
        condition, variablesSet, joinType);  // 使用当前的相关变量集合
  }  // 方法结束：返回归并连接的副本

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,  // 重写方法：计算归并连接的成本
      RelMetadataQuery mq) {  // 参数planner：优化规划器，参数mq：元数据查询
    // We assume that the inputs are sorted. The price of sorting them has  // 注释：我们假设输入已排序。排序它们的代价
    // already been paid. The cost of the join is therefore proportional to the  // 已经支付。因此连接的代价与
    // input and output size.  // 输入和输出大小成正比
    final double rightRowCount = mq.getRowCount(right);  // 获取右子节点的行数
    final double leftRowCount = mq.getRowCount(left);  // 获取左子节点的行数
    final double rowCount = mq.getRowCount(this);  // 获取当前节点的行数
    final double d = leftRowCount + rightRowCount + rowCount;  // 计算总代价：左行数+右行数+输出行数
    return planner.getCostFactory().makeCost(d, 0, 0);  // 返回成本对象（CPU=d，I/O=0，内存=0）
  }  // 方法结束：返回归并连接的估计成本

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) {  // 重写方法：实现归并连接的可枚举代码生成
    BlockBuilder builder = new BlockBuilder();  // 创建代码块构建器，用于生成Java代码
    final Result leftResult =  // 访问左子节点并生成代码
        implementor.visitChild(this, 0, (EnumerableRel) left, pref);  // 实现左输入的可枚举代码
    final Expression leftExpression =  // 将左子节点的代码块添加到构建器
        builder.append("left", leftResult.block);  // 左子节点的表达式
    final ParameterExpression left_ =  // 创建左子节点的参数表达式
        Expressions.parameter(leftResult.physType.getJavaRowType(), "left");  // 参数名为"left"，类型为左子节点的Java行类型
    final Result rightResult =  // 访问右子节点并生成代码
        implementor.visitChild(this, 1, (EnumerableRel) right, pref);  // 实现右输入的可枚举代码
    final Expression rightExpression =  // 将右子节点的代码块添加到构建器
        builder.append("right", rightResult.block);  // 右子节点的表达式
    final ParameterExpression right_ =  // 创建右子节点的参数表达式
        Expressions.parameter(rightResult.physType.getJavaRowType(), "right");  // 参数名为"right"，类型为右子节点的Java行类型
    final JavaTypeFactory typeFactory = implementor.getTypeFactory();  // 获取Java类型工厂
    final PhysType physType =  // 创建当前节点的物理类型
        PhysTypeImpl.of(typeFactory, getRowType(), pref.preferArray());  // 基于行类型和偏好创建物理类型
    final List<Expression> leftExpressions = new ArrayList<>();  // 创建左键表达式列表
    final List<Expression> rightExpressions = new ArrayList<>();  // 创建右键表达式列表
    for (Pair<Integer, Integer> pair : Pair.zip(joinInfo.leftKeys, joinInfo.rightKeys)) {  // 遍历所有连接键对
      RelDataType leftType = left.getRowType().getFieldList().get(pair.left).getType();  // 获取左键的数据类型
      RelDataType rightType = right.getRowType().getFieldList().get(pair.right).getType();  // 获取右键的数据类型
      final RelDataType keyType =  // 计算键的最小限制类型（公共超类型）
          requireNonNull(  // 确保类型不为null
              typeFactory.leastRestrictive(ImmutableList.of(leftType, rightType)),  // 找到两个类型的公共超类型
              () -> "leastRestrictive returns null for " + leftType  // 如果为null，提供错误信息
                  + " and " + rightType);  // 错误信息包含两个类型
      final Type keyClass = typeFactory.getJavaClass(keyType);  // 获取键类型的Java类
      leftExpressions.add(  // 添加左键表达式
          EnumUtils.convert(  // 转换表达式类型
              leftResult.physType.fieldReference(left_, pair.left), keyClass));  // 引用左表的连接键字段并转换类型
      rightExpressions.add(  // 添加右键表达式
          EnumUtils.convert(  // 转换表达式类型
              rightResult.physType.fieldReference(right_, pair.right), keyClass));  // 引用右表的连接键字段并转换类型
    }  // 为每个连接键对创建左右键表达式
    Expression predicate = Expressions.constant(null);  // 初始化非等值连接条件为null
    if (!joinInfo.nonEquiConditions.isEmpty()) {  // 如果存在非等值连接条件
      final RexNode nonEquiCondition =  // 组合所有非等值条件
          RexUtil.composeConjunction(getCluster().getRexBuilder(),  // 使用表达式构建器
              joinInfo.nonEquiConditions, true);  // 组合条件列表为AND表达式
      if (nonEquiCondition != null) {  // 如果组合后的条件不为null
        predicate =  // 生成谓词表达式
            EnumUtils.generatePredicate(implementor,  // 使用实现器生成谓词
                getCluster().getRexBuilder(), left, right, leftResult.physType,  // 传递必要的参数
                rightResult.physType, nonEquiCondition);  // 传递非等值条件
      }  // 生成非等值条件的Java代码
    }  // 处理非等值连接条件
    final PhysType leftKeyPhysType =  // 创建左键的物理类型
        leftResult.physType.project(joinInfo.leftKeys, JavaRowFormat.LIST);  // 投影到连接键，使用LIST格式
    final PhysType rightKeyPhysType =  // 创建右键的物理类型
        rightResult.physType.project(joinInfo.rightKeys, JavaRowFormat.LIST);  // 投影到连接键，使用LIST格式

    // Generate the appropriate key Comparator (keys must be sorted in ascending order, nulls last).  // 注释：生成适当的键比较器（键必须按升序排序，null值最后）
    final int keysSize = joinInfo.leftKeys.size();  // 获取连接键的数量
    final List<RelFieldCollation> fieldCollations = new ArrayList<>(keysSize);  // 创建字段排序列表
    for (int i = 0; i < keysSize; i++) {  // 遍历所有连接键
      fieldCollations.add(  // 添加字段排序定义
          new RelFieldCollation(i, RelFieldCollation.Direction.ASCENDING,  // 字段索引i，升序排序
              RelFieldCollation.NullDirection.LAST));  // null值排在最后
    }  // 归并连接需要键按升序排序，null值最后
    final RelCollation collation = RelCollations.of(fieldCollations);  // 创建排序特征对象
    final Expression comparator = leftKeyPhysType.generateMergeJoinComparator(collation);  // 生成归并连接的比较器表达式

    return implementor.result(  // 返回实现结果
        physType,  // 结果的物理类型
        builder.append(  // 添加归并连接调用的代码块
            Expressions.call(  // 创建方法调用表达式
                BuiltInMethod.MERGE_JOIN.method,  // 调用MERGE_JOIN内置方法
                Expressions.list(  // 创建参数列表
                    leftExpression,  // 参数1：左输入的可枚举表达式
                    rightExpression,  // 参数2：右输入的可枚举表达式
                    Expressions.lambda(  // 参数3：左键提取函数（lambda表达式）
                        leftKeyPhysType.record(leftExpressions), left_),  // 从左行提取连接键
                    Expressions.lambda(  // 参数4：右键提取函数（lambda表达式）
                        rightKeyPhysType.record(rightExpressions), right_),  // 从右行提取连接键
                    predicate,  // 参数5：非等值连接条件谓词
                    EnumUtils.joinSelector(joinType,  // 参数6：连接结果选择器函数
                        physType,  // 结果的物理类型
                        ImmutableList.of(  // 左右子节点的物理类型列表
                            leftResult.physType, rightResult.physType)),  // 用于生成连接结果的代码
                    Expressions.constant(EnumUtils.toLinq4jJoinType(joinType)),  // 参数7：连接类型（转换为LINQ4j类型）
                    comparator,  // 参数8：键比较器
                    Util.first(  // 参数9：键比较器（备选）
                        leftKeyPhysType.comparer(),  // 左键的比较器
                        Expressions.constant(null))))).toBlock());  // 如果没有比较器则为null
  }  // 方法结束：返回归并连接的代码生成结果
}
