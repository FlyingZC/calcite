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
package org.apache.calcite.adapter.innodb; // 定义包名，该类属于InnoDB适配器包

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于表示关系表达式集群，包含优化器的共享信息
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，用于表示关系操作的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，用于表示查询优化器
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系表达式的特征集合
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类，用于表示排序规则
import org.apache.calcite.rel.RelNode; // 导入RelNode类，这是所有关系表达式的基类
import org.apache.calcite.rel.RelWriter; // 导入RelWriter类，用于将关系表达式输出为可读格式
import org.apache.calcite.rel.core.Filter; // 导入Filter类，这是过滤操作的关系表达式基类
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据
import org.apache.calcite.rex.RexNode; // 导入RexNode类，用于表示行表达式（Row Expression）

import com.alibaba.innodb.java.reader.schema.TableDef; // 导入TableDef类，用于表示InnoDB表的元数据定义

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的参数或返回值

import static java.util.Objects.requireNonNull; // 导入requireNonNull静态方法，用于检查对象是否为null

/**
 * Implementation of a {@link org.apache.calcite.rel.core.Filter}
 * relational expression for an InnoDB data source.
 * // InnoDB数据源的Filter关系表达式实现类
 * // 该类继承自Filter基类，实现了InnodbRel接口，专门用于InnoDB数据源的过滤操作
 * // 主要功能：
 * // 1. 将SQL的WHERE条件转换为InnoDB的索引条件，利用索引加速查询
 * // 2. 支持强制使用指定索引（force index）功能
 * // 3. 提供成本计算，优化器可以根据此成本选择最优执行计划
 * // 4. 实现了InnodbRel接口的implement方法，用于生成可执行代码
 * // 5. 支持索引下推（Index Push Down）优化，将过滤条件尽可能下推到存储引擎层
 */
public class InnodbFilter extends Filter implements InnodbRel { // 定义InnodbFilter类，继承Filter并实现InnodbRel接口
  private final TableDef tableDef; // 成员变量：存储InnoDB表的元数据定义，包含表结构、索引信息等，用于生成查询计划
  public final IndexCondition indexCondition; // 成员变量：存储索引条件对象，包含了可以下推到索引的过滤条件，用于利用索引加速查询
  private final @Nullable String forceIndexName; // 成员变量：强制使用的索引名称，如果为null表示不强制使用特定索引，用于实现MySQL的FORCE INDEX功能

  /** Creates an InnodbFilter; but use {@link #create} if possible. */ // 创建InnodbFilter的私有构造方法注释，建议使用create静态工厂方法
  private InnodbFilter(RelOptCluster cluster, RelTraitSet traitSet, // 私有构造方法：cluster-关系表达式集群，包含优化器共享信息；traitSet-特征集合，包含物理属性如约定（convention）
      RelNode input, RexNode condition, IndexCondition indexCondition, // input-输入关系节点（通常是表扫描）；condition-过滤条件（RexNode表达式）；indexCondition-索引条件对象
      TableDef tableDef, @Nullable String forceIndexName) { // tableDef-表定义对象；forceIndexName-强制使用的索引名称（可为null）
    super(cluster, traitSet, input, condition); // 调用父类Filter的构造方法，初始化基本属性

    this.tableDef = requireNonNull(tableDef, "tableDef"); // 初始化tableDef成员变量，使用requireNonNull确保不为null，否则抛出NullPointerException
    this.indexCondition = requireNonNull(indexCondition, "indexCondition"); // 初始化indexCondition成员变量，使用requireNonNull确保不为null，否则抛出NullPointerException
    this.forceIndexName = forceIndexName; // 初始化forceIndexName成员变量，允许为null

    assert getConvention() == InnodbRel.CONVENTION; // 断言：确保当前节点的约定（convention）是InnoDB约定，用于类型安全检查
    assert getConvention() == input.getConvention(); // 断言：确保输入节点的约定与当前节点一致，保证关系表达式树的约定一致性
  }

  /** Creates an InnodbFilter. */ // 创建InnodbFilter的静态工厂方法注释
  public static InnodbFilter create(RelOptCluster cluster, RelTraitSet traitSet, // 静态工厂方法：用于创建InnodbFilter实例，推荐使用此方法而非直接调用构造函数
      RelNode input, RexNode condition, IndexCondition indexCondition, // 参数含义与构造方法相同：input-输入节点；condition-过滤条件；indexCondition-索引条件
      TableDef tableDef, @Nullable String forceIndexName) { // tableDef-表定义；forceIndexName-强制索引名称
    return new InnodbFilter(cluster, traitSet, input, condition, indexCondition, // 调用私有构造函数创建并返回InnodbFilter实例
        tableDef, forceIndexName);
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法：计算当前节点的成本，用于优化器选择最优执行计划
      RelMetadataQuery mq) { // planner-优化器对象；mq-元数据查询对象，用于获取统计信息
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq)); // 调用父类的computeSelfCost计算基础成本，使用requireNonNull确保结果不为null
    return cost.multiplyBy(0.1); // 将基础成本乘以0.1，表示InnoDBFilter操作成本较低，因为可以利用索引加速过滤，优化器更倾向于选择此计划
  }

  @Override public InnodbFilter copy(RelTraitSet traitSet, RelNode input, // 重写copy方法：创建当前节点的副本，用于优化器重写规则
      RexNode condition) { // traitSet-新的特征集合；input-新的输入节点；condition-新的过滤条件
    return new InnodbFilter(getCluster(), traitSet, input, condition, // 创建并返回新的InnodbFilter实例，保持原有的tableDef、indexCondition和forceIndexName
        indexCondition, tableDef, forceIndexName);
  }

  @Override public void implement(Implementor implementor) { // 重写implement方法：实现InnodbRel接口，用于将关系表达式转换为可执行的查询代码
    implementor.visitChild(0, getInput()); // 访问子节点（索引0），通常是表扫描节点，确保子节点先被实现
    implementor.setIndexCondition(indexCondition); // 将索引条件设置到实现器中，用于生成带有索引过滤的查询语句
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写explainTerms方法：生成执行计划的解释信息，用于调试和展示查询计划
    pw.input("input", getInput()); // 输出输入节点信息，显示此Filter的输入是什么（通常是表扫描）
    pw.itemIf("condition", indexCondition, indexCondition.canPushDown()); // 如果索引条件可以下推，则输出条件信息，用于展示使用了哪些索引条件
    return pw; // 返回RelWriter对象，支持链式调用
  }

  /**
   * Returns the resulting collation by the primary or secondary
   * indexes after filtering.
   *
   * @return the implicit collation based on the natural sorting by specific index
   */ // 方法注释：返回过滤后由主键或二级索引产生的排序规则
  public RelCollation getImplicitCollation() { // 获取隐式排序方法：返回基于索引自然排序的排序规则
    return indexCondition.getImplicitCollation(); // 从索引条件中获取隐式排序规则，用于优化器判断是否可以省略额外的排序操作
  }
}
