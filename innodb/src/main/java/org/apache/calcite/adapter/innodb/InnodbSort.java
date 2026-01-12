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
package org.apache.calcite.adapter.innodb; // 包声明：InnoDB适配器包，包含与MySQL InnoDB存储引擎相关的适配器类

import org.apache.calcite.plan.RelOptCluster; // 导入：关系表达式集群，用于管理关系表达式和共享信息
import org.apache.calcite.plan.RelOptCost; // 导入：关系表达式成本，用于表示执行计划的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入：关系优化器，用于优化执行计划
import org.apache.calcite.plan.RelTraitSet; // 导入：关系特征集合，定义关系表达式的物理属性
import org.apache.calcite.rel.RelCollation; // 导入：排序规则，定义字段的排序方式
import org.apache.calcite.rel.RelFieldCollation; // 导入：字段排序规则，定义单个字段的排序方向和空值处理
import org.apache.calcite.rel.RelNode; // 导入：关系节点接口，所有关系表达式的基类
import org.apache.calcite.rel.core.Sort; // 导入：Sort关系表达式基类，实现排序操作
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入：关系元数据查询，用于获取统计信息
import org.apache.calcite.rex.RexNode; // 导入：行表达式节点，用于表示表达式

import org.checkerframework.checker.nullness.qual.Nullable; // 导入：可空类型注解，标记可能为null的返回值

import java.util.List; // 导入：List集合接口，用于存储字段排序规则列表

import static com.google.common.base.Preconditions.checkState; // 导入静态：状态检查工具，用于验证条件

import static java.util.Objects.requireNonNull; // 导入静态：非空检查工具，确保对象不为null

/**
 * Implementation of {@link org.apache.calcite.rel.core.Sort}
 * relational expression for an InnoDB data source.
 * InnoDB数据源的Sort关系表达式实现类
 * 
 * 本类是Calcite适配器中专门针对InnoDB存储引擎的排序操作实现
 * 它继承自Sort基类并实现了InnodbRel接口，表示这是一个物理执行节点
 * 主要功能：
 * 1. 将SQL中的ORDER BY操作转换为InnoDB适配器可以理解的排序指令
 * 2. 计算排序操作的执行成本，帮助优化器选择最优执行计划
 * 3. 实现排序关系的拷贝，用于优化过程中的重写
 * 4. 将排序操作转换为InnoDB适配器的实现代码
 * 
 * 与标准Sort的区别：
 * - 专门针对InnoDB存储引擎优化，成本计算考虑InnoDB的特性
 * - 支持InnoDB特定的排序规则（如索引排序）
 * - 通过InnodbRel接口与InnoDB适配器集成
 */
public class InnodbSort extends Sort implements InnodbRel { // 类定义：InnoDB排序关系表达式，继承Sort基类并实现InnodbRel接口
  InnodbSort(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法：创建InnoDB排序关系表达式，参数包括关系集群、特征集合、输入节点和排序规则
      RelNode input, RelCollation collation) { // 参数：input表示要排序的输入关系节点，collation定义排序的字段和方向
    super(cluster, traitSet, input, collation, null, null); // 调用父类Sort的构造方法，初始化排序关系表达式，offset和fetch参数设为null表示不限制结果集

    assert getConvention() == InnodbRel.CONVENTION; // 断言：确保当前节点的特征约定是InnoDB约定，验证节点类型正确
    assert getConvention() == input.getConvention(); // 断言：确保输入节点的特征约定与当前节点一致，保证整个计划的约定统一
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 方法重写：计算当前排序节点的执行成本，返回可能为null的成本对象
      RelMetadataQuery mq) { // 参数：planner表示关系优化器，mq用于查询元数据统计信息
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq)); // 调用父类方法计算基础成本，并确保结果不为null
    if (!collation.getFieldCollations().isEmpty()) { // 判断：如果存在排序规则（即有字段需要排序）
      return cost.multiplyBy(0.05); // 返回：将基础成本乘以0.05，表示InnoDB的排序操作相对便宜（可能利用了索引）
    } else { // 如果没有排序规则（即不进行排序）
      return cost; // 返回：基础成本不变
    }
  }

  @Override public Sort copy(RelTraitSet traitSet, RelNode input, // 方法重写：创建当前排序节点的副本，用于优化过程中的重写
      RelCollation newCollation, RexNode offset, RexNode fetch) { // 参数：新的特征集合、输入节点、排序规则、偏移量和获取行数
    return new InnodbSort(getCluster(), traitSet, input, collation); // 返回：创建新的InnodbSort实例，注意使用原有的collation而非newCollation
  }

  @Override public void implement(Implementor implementor) { // 方法重写：实现排序操作，将逻辑关系转换为物理实现
    implementor.visitChild(0, getInput()); // 访问并实现子节点（输入关系），确保输入关系先被实现

    List<RelFieldCollation> sortCollations = collation.getFieldCollations(); // 获取所有字段的排序规则列表，每个规则定义了一个字段的排序方向
    boolean allDesc = sortCollations.stream().allMatch(r -> r.direction.isDescending()); // 判断：检查所有排序字段是否都是降序排列
    boolean allNonDesc = sortCollations.stream().noneMatch(r -> r.direction.isDescending()); // 判断：检查所有排序字段是否都不是降序（即都是升序）
    // field collation should be in a series of ascending or descending collations // 注释：字段排序规则应该是一致的，要么全升序要么全降序
    checkState(allDesc || allNonDesc, "ordering should be in a " // 状态检查：验证排序规则的一致性，如果混合了升序和降序则抛出异常
        + "series of ascending or descending collations " + sortCollations); // 错误信息：说明排序规则必须是全升序或全降序
    implementor.setAscOrder(!allDesc); // 设置排序顺序：如果不是全降序则设置为升序，否则设置为降序
  }
} // 类结束：InnodbSort类定义结束
