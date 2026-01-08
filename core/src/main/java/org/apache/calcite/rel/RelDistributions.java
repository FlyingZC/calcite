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
package org.apache.calcite.rel; // 包声明：org.apache.calcite.rel包，包含关系代数相关的核心类

import org.apache.calcite.plan.RelMultipleTrait; // 导入RelMultipleTrait接口：表示可以有多种值的Relational特征
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口：查询优化器接口
import org.apache.calcite.plan.RelTrait; // 导入RelTrait接口：关系特征接口
import org.apache.calcite.util.ImmutableIntList; // 导入ImmutableIntList：不可变的整数列表工具类
import org.apache.calcite.util.Util; // 导入Util：通用工具类
import org.apache.calcite.util.mapping.Mapping; // 导入Mapping：映射接口
import org.apache.calcite.util.mapping.Mappings; // 导入Mappings：映射工具类

import com.google.common.collect.Ordering; // 导入Ordering：Google Guava的排序工具类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解：标记可能为null的值

import java.util.Collection; // 导入Collection：集合接口
import java.util.List; // 导入List：列表接口
import java.util.Objects; // 导入Objects：对象工具类，用于equals和hashCode

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法：用于非空检查

/**
 * Utilities concerning {@link org.apache.calcite.rel.RelDistribution}.
 * RelDistributions类：RelDistribution（关系分布）的工具类，用于创建和管理各种数据分布策略
 * 
 * 核心功能：
 * 1. 提供预定义的常用分布类型常量（SINGLETON、RANDOM、ROUND_ROBIN、BROADCAST等）
 * 2. 提供工厂方法创建不同类型的分布（hash分布、range分布等）
 * 3. 实现RelDistribution接口的具体实现类RelDistributionImpl
 * 
 * 数据分布是Calcite查询优化器中用于描述数据如何在物理节点上分布的概念
 * 它影响查询执行计划的并行化、数据重分布和join策略等关键决策
 * 
 * 支持的分布类型：
 * - SINGLETON：单节点分布，所有数据在一个节点上
 * - RANDOM_DISTRIBUTED：随机分布
 * - ROUND_ROBIN_DISTRIBUTED：轮询分布
 * - BROADCAST_DISTRIBUTED：广播分布，所有节点都有完整数据副本
 * - HASH_DISTRIBUTED：哈希分布，根据指定列的哈希值分布
 * - RANGE_DISTRIBUTED：范围分布，根据指定列的值范围分布
 * - ANY：任意分布，作为通配符使用
 */
public class RelDistributions {
  public static final ImmutableIntList EMPTY = ImmutableIntList.of(); // 空键列表常量，用于不需要分布键的分布类型（如SINGLETON、BROADCAST等）

  /** The singleton singleton distribution. */
  public static final RelDistribution SINGLETON = // 单例分布常量：所有数据都在单个节点上，适用于小表或需要全局聚合的场景
      new RelDistributionImpl(RelDistribution.Type.SINGLETON, EMPTY);

  /** The singleton random distribution. */
  public static final RelDistribution RANDOM_DISTRIBUTED = // 随机分布常量：数据随机分配到各个节点，没有特定的分布键
      new RelDistributionImpl(RelDistribution.Type.RANDOM_DISTRIBUTED, EMPTY);

  /** The singleton round-robin distribution. */
  public static final RelDistribution ROUND_ROBIN_DISTRIBUTED = // 轮询分布常量：数据按顺序轮流分配到各个节点，保证负载均衡
      new RelDistributionImpl(RelDistribution.Type.ROUND_ROBIN_DISTRIBUTED,
          EMPTY);

  /** The singleton broadcast distribution. */
  public static final RelDistribution BROADCAST_DISTRIBUTED = // 广播分布常量：所有数据都复制到每个节点上，常用于小表与大表的join操作
      new RelDistributionImpl(RelDistribution.Type.BROADCAST_DISTRIBUTED,
          EMPTY);

  public static final RelDistribution ANY = // 任意分布常量：作为通配符使用，表示可以接受任何分布类型
      new RelDistributionImpl(RelDistribution.Type.ANY, EMPTY);

  private RelDistributions() {} // 私有构造函数，防止实例化，因为这是一个工具类，所有成员都是静态的

  /** Creates a hash distribution. */
  public static RelDistribution hash(Collection<? extends Number> numbers) { // 创建哈希分布：根据指定的列索引集合创建哈希分布，数据根据这些列的哈希值分布到各个节点
    ImmutableIntList list = normalizeKeys(numbers); // 规范化键列表（排序并去重）
    return of(RelDistribution.Type.HASH_DISTRIBUTED, list); // 创建HASH_DISTRIBUTED类型的分布对象
  }

  /** Creates a range distribution. */
  public static RelDistribution range(Collection<? extends Number> numbers) { // 创建范围分布：根据指定的列索引集合创建范围分布，数据根据这些列的值范围分布到各个节点
    ImmutableIntList list = ImmutableIntList.copyOf(numbers); // 将集合转换为不可变的整数列表
    return of(RelDistribution.Type.RANGE_DISTRIBUTED, list); // 创建RANGE_DISTRIBUTED类型的分布对象
  }

  public static RelDistribution of(RelDistribution.Type type, ImmutableIntList keys) { // 工厂方法：创建指定类型和键的分布对象，并通过TraitDef进行规范化处理
    RelDistribution distribution = new RelDistributionImpl(type, keys); // 创建RelDistributionImpl实例
    return RelDistributionTraitDef.INSTANCE.canonize(distribution); // 通过TraitDef规范化（可能返回已存在的等价对象）
  }

  /** Creates ordered immutable copy of keys collection.  */
  private static ImmutableIntList normalizeKeys(Collection<? extends Number> keys) { // 规范化键集合：创建有序的不可变副本，确保键列表是有序的
    ImmutableIntList list = ImmutableIntList.copyOf(keys); // 将集合转换为不可变列表
    if (list.size() > 1 // 如果有多个键
        && !Ordering.natural().isOrdered(list)) { // 且列表不是有序的
      list = ImmutableIntList.copyOf(Ordering.natural().sortedCopy(list)); // 则对列表进行排序并创建新的不可变副本
    }
    return list; // 返回规范化的键列表
  }

  /** Implementation of {@link org.apache.calcite.rel.RelDistribution}. */
  private static class RelDistributionImpl implements RelDistribution { // RelDistribution接口的实现类，表示具体的数据分布策略
    private static final Ordering<Iterable<Integer>> ORDERING = // 用于比较整数列表的排序器，采用字典序（lexicographical）比较
        Ordering.<Integer>natural().lexicographical();
    private final Type type; // 分布类型：SINGLETON、HASH_DISTRIBUTED、RANGE_DISTRIBUTED等
    private final ImmutableIntList keys; // 分布键列表：用于HASH_DISTRIBUTED和RANGE_DISTRIBUTED的列索引列表

    private RelDistributionImpl(Type type, ImmutableIntList keys) { // 构造函数：创建指定类型和键的分布对象
      this.type = requireNonNull(type, "type"); // 设置分布类型，要求非空
      this.keys = ImmutableIntList.copyOf(keys); // 创建键列表的不可变副本
      assert type != Type.HASH_DISTRIBUTED // 断言：如果是哈希分布，则键列表必须是有序的
          || keys.size() < 2
          || Ordering.natural().isOrdered(keys)
          : "key columns of hash distribution must be in order";
      assert type == Type.HASH_DISTRIBUTED // 断言：只有HASH_DISTRIBUTED和RANGE_DISTRIBUTED可以有非空的键列表
          || type == Type.RANGE_DISTRIBUTED
          || keys.isEmpty();
    }

    @Override public int hashCode() { // 重写hashCode方法：基于类型和键列表计算哈希值
      return Objects.hash(type, keys); // 使用Objects.hash方法组合type和keys的哈希值
    }

    @Override public boolean equals(@Nullable Object obj) { // 重写equals方法：判断两个分布对象是否相等
      return this == obj // 先比较引用是否相同
          || obj instanceof RelDistributionImpl // 再检查类型是否相同
          && type == ((RelDistributionImpl) obj).type // 比较分布类型
          && keys.equals(((RelDistributionImpl) obj).keys); // 比较键列表
    }

    @Override public String toString() { // 重写toString方法：返回分布的字符串表示
      if (keys.isEmpty()) { // 如果没有键
        return type.shortName; // 只返回类型简称（如"SINGLETON"）
      } else { // 如果有键
        return type.shortName + keys; // 返回类型简称和键列表（如"HASH[0, 1]"）
      }
    }

    @Override public Type getType() { // 获取分布类型：返回此分布的类型（SINGLETON、HASH_DISTRIBUTED等）
      return type; // 返回类型字段
    }

    @Override public List<Integer> getKeys() { // 获取分布键列表：返回用于分布的列索引列表
      return keys; // 返回键列表字段
    }

    @Override public RelDistributionTraitDef getTraitDef() { // 获取特征定义：返回RelDistribution的特征定义对象
      return RelDistributionTraitDef.INSTANCE; // 返回RelDistributionTraitDef的单例实例
    }

    @Override public RelDistribution apply(Mappings.TargetMapping mapping) { // 应用映射：根据字段映射关系转换分布键，返回新的分布对象
      if (keys.isEmpty()) { // 如果没有分布键
        return this; // 直接返回当前对象（因为不需要转换）
      }
      for (int key : keys) { // 遍历所有分布键
        if (mapping.getTargetOpt(key) == -1) { // 如果某个键在映射中不存在（被映射到-1）
          return ANY; // 返回ANY分布（因为无法保持原有的分布策略）
        }
      }
      List<Integer> mappedKeys0 = Mappings.apply2((Mapping) mapping, keys); // 应用映射到所有键，得到映射后的键列表
      ImmutableIntList mappedKeys = normalizeKeys(mappedKeys0); // 规范化映射后的键列表（排序）
      return of(type, mappedKeys); // 创建并返回新的分布对象
    }

    @Override public boolean satisfies(RelTrait trait) { // 判断是否满足指定特征：检查当前分布是否满足或兼容给定的分布特征
      if (trait == this || trait == ANY) { // 如果是同一个对象或目标特征是ANY
        return true; // 则满足条件
      }
      if (trait instanceof RelDistributionImpl) { // 如果目标特征也是RelDistributionImpl实例
        RelDistributionImpl distribution = (RelDistributionImpl) trait; // 强制类型转换
        if (type == distribution.type) { // 如果分布类型相同
          switch (type) { // 根据不同的分布类型进行不同的判断逻辑
          case HASH_DISTRIBUTED: // 哈希分布：要求键列表完全相同
            // The "leading edge" property of Range does not apply to Hash.
            // Only Hash[x, y] satisfies Hash[x, y].
            return keys.equals(distribution.keys); // 只有键列表完全相等才满足
          case RANGE_DISTRIBUTED: // 范围分布：支持"前缀匹配"特性
            // Range[x, y] satisfies Range[x, y, z] but not Range[x]
            return Util.startsWith(distribution.keys, keys); // 如果当前键是目标键的前缀则满足（Range[0,1]满足Range[0,1,2]）
          default: // 其他类型（SINGLETON、BROADCAST等）
            return true; // 只要类型相同就满足
          }
        }
      }
      if (trait == RANDOM_DISTRIBUTED) { // 如果目标特征是RANDOM_DISTRIBUTED
        // RANDOM is satisfied by HASH, ROUND-ROBIN, RANDOM, RANGE;
        // we've already checked RANDOM
        return type == Type.HASH_DISTRIBUTED // HASH、ROUND-ROBIN、RANGE都满足RANDOM要求
            || type == Type.ROUND_ROBIN_DISTRIBUTED
            || type == Type.RANGE_DISTRIBUTED;
      }
      return false; // 其他情况都不满足
    }

    @Override public void register(RelOptPlanner planner) { // 注册到优化器：将此分布特征注册到查询优化器中（当前实现为空）
    }

    @Override public boolean isTop() { // 判断是否为顶层特征：检查此分布是否为ANY类型（ANY是分布特征的顶层元素）
      return type == Type.ANY; // 如果类型是ANY则返回true
    }

    @Override public int compareTo(RelMultipleTrait o) { // 比较方法：实现Comparable接口，用于分布特征的排序和比较
      final RelDistribution distribution = (RelDistribution) o; // 强制类型转换为RelDistribution
      if (type == distribution.getType() // 如果分布类型相同
          && (type == Type.HASH_DISTRIBUTED // 且是HASH或RANGE分布（这两种分布有键列表）
              || type == Type.RANGE_DISTRIBUTED)) {
        return ORDERING.compare(getKeys(), distribution.getKeys()); // 则比较键列表的字典序
      }

      return type.compareTo(distribution.getType()); // 否则比较分布类型的枚举顺序
    }
  }
}
