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
package org.apache.calcite.adapter.innodb;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.hint.HintPredicates;
import org.apache.calcite.rel.hint.HintStrategyTable;
import org.apache.calcite.rel.hint.RelHint;
import org.apache.calcite.rel.type.RelDataType;

import com.alibaba.innodb.java.reader.Constants;
import com.alibaba.innodb.java.reader.schema.KeyMeta;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

import static java.util.Objects.requireNonNull;

/**
 * Relational expression representing a scan of an InnoDB data source.
 * 表示扫描InnoDB数据源的关系表达式，这是Calcite适配器模式中用于表示从InnoDB表读取数据的物理算子
 */
public class InnodbTableScan extends TableScan implements InnodbRel {
  final InnodbTable innodbTable; // InnoDB表对象，包含表的元数据信息，如表结构、索引定义等，是整个表扫描操作的数据源基础
  final @Nullable RelDataType projectRowType; // 投影后的行类型，如果为null则使用表的完整行类型，用于实现列裁剪优化，只查询需要的列
  /** Force to use one specific index from hint. */
  private final @Nullable String forceIndexName; // 强制使用的索引名称，来自SQL hint，如果用户通过hint指定了索引则使用该索引进行扫描
  /** This contains index to scan table and optional condition. */
  private final IndexCondition indexCondition; // 索引条件对象，包含用于扫描表的索引信息以及可选的过滤条件，决定了表的扫描策略

  protected InnodbTableScan(RelOptCluster cluster, RelTraitSet traitSet,
      RelOptTable table, InnodbTable innodbTable,
      @Nullable RelDataType projectRowType, List<RelHint> hints) {
    super(cluster, traitSet, hints, table); // 调用父类TableScan的构造方法，初始化集群、特征集、hint和表信息
    this.innodbTable = requireNonNull(innodbTable, "innodbTable"); // 初始化InnoDB表对象，确保不为空，这是表扫描的核心数据源
    this.projectRowType = projectRowType; // 初始化投影行类型，用于后续的列裁剪优化
    this.forceIndexName = getForceIndexName(hints).orElse(null); // 从hint中解析强制使用的索引名称，如果没有则设为null
    this.indexCondition = getIndexCondition(); // 初始化索引条件，根据forceIndexName决定使用主键索引还是二级索引进行扫描
    checkArgument(getConvention() == InnodbRel.CONVENTION); // 检查特征集是否为InnoDB约定，确保该RelNode属于InnoDB适配器的物理实现
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    assert inputs.isEmpty(); // 断言输入列表为空，因为TableScan是叶子节点，不应该有任何子节点输入
    return this; // 直接返回当前对象，因为InnodbTableScan是不可变的，不需要创建副本
  }

  @Override public RelDataType deriveRowType() {
    return projectRowType != null ? projectRowType : super.deriveRowType(); // 如果有投影行类型则返回投影类型，否则返回父类推导的完整行类型，实现列裁剪优化
  }

  @Override public void register(RelOptPlanner planner) {
    HintStrategyTable strategies = HintStrategyTable.builder()
        .hintStrategy("index", HintPredicates.TABLE_SCAN) // 注册"index" hint策略，该hint只能应用于表扫描操作
        .build(); // 构建hint策略表
    getCluster().setHintStrategies(strategies); // 将hint策略表设置到集群中，使hint能够被正确解析和应用

    planner.addRule(InnodbRules.TO_ENUMERABLE); // 注册将InnoDB关系表达式转换为Enumerable关系表达式的规则，这是执行计划生成的关键转换规则
    for (RelOptRule rule : InnodbRules.RULES) { // 遍历InnoDB规则集合
      planner.addRule(rule); // 将每个规则注册到优化器中，使优化器能够应用这些规则进行查询优化
    }
  }

  @Override public void implement(Implementor implementor) {
    implementor.innodbTable = innodbTable; // 将InnoDB表对象设置到实现器中，用于后续生成执行代码
    implementor.table = table; // 将表对象设置到实现器中，包含表的元数据信息
    implementor.setIndexCondition(indexCondition); // 将索引条件设置到实现器中，决定使用哪个索引以及扫描策略
  }

  @Override public RelWriter explainTerms(RelWriter pw) {
    return super.explainTerms(pw) // 调用父类方法获取基础的解释信息
        .itemIf("forceIndex", forceIndexName, forceIndexName != null); // 如果forceIndexName不为null，则在解释信息中添加强制使用的索引名称
  }

  /**
   * Infer the implicit collation from index.
   * 从索引推导隐式排序规则，当使用索引扫描时，数据会按照索引的顺序返回，这种顺序就是隐式排序
   *
   * @return the implicit collation based on the natural ordering of an index
   * @return 基于索引自然排序的隐式排序规则，可以用于消除不必要的排序操作
   */
  public RelCollation getImplicitCollation() {
    return indexCondition.getImplicitCollation(); // 从索引条件对象中获取隐式排序规则，该规则反映了索引列的排序顺序
  }
  private Optional<String> getForceIndexName(final List<RelHint> hints) {
    if (hints.isEmpty()) { // 如果没有提供hint，直接返回空Optional
      return Optional.empty();
    }
    for (RelHint hint : hints) { // 遍历所有hint
      if ("index".equalsIgnoreCase(hint.hintName)) { // 如果hint名称是"index"（不区分大小写）
        if (!hint.listOptions.isEmpty()) { // 如果hint有选项列表
          Set<String> indexesNameSet = innodbTable.getIndexesNameSet(); // 获取表的所有索引名称集合
          Optional<String> forceIndexName = hint.listOptions.stream().findFirst(); // 从hint选项中获取第一个索引名称
          if (!forceIndexName.isPresent()) { // 如果没有找到索引名称，返回空Optional
            return Optional.empty();
          }
          for (String indexName : indexesNameSet) { // 遍历表的所有索引
            if (indexName != null && indexName.equalsIgnoreCase(forceIndexName.get())) { // 如果索引名称匹配（不区分大小写）
              return Optional.of(indexName); // 返回匹配的索引名称
            }
          }
        }
      }
    }
    return Optional.empty(); // 没有找到匹配的索引，返回空Optional
  }

  public @Nullable String getForceIndexName() {
    return forceIndexName; // 返回强制使用的索引名称，如果没有强制索引则返回null
  }

  private IndexCondition getIndexCondition() {
    // force to use a secondary index to scan table if present
    // 如果指定了强制索引且不是主键索引，则使用二级索引扫描表
    if (forceIndexName != null
        && !forceIndexName.equalsIgnoreCase(Constants.PRIMARY_KEY_NAME)) { // 检查是否指定了非主键的二级索引
      KeyMeta skMeta = innodbTable.getTableDef() // 获取表定义
          .getSecondaryKeyMetaMap().get(forceIndexName); // 从二级索引元数据映射中获取指定索引的元数据
      if (skMeta == null) { // 如果找不到该二级索引的元数据
        throw new AssertionError("secondary index not found " + forceIndexName); // 抛出断言错误，表示索引不存在
      }
      return IndexCondition.create(InnodbRules.innodbFieldNames(getRowType()), // 创建索引条件对象，传入行类型字段名
          forceIndexName, // 传入索引名称
          skMeta.getKeyColumnNames(), // 传入索引的列名
          QueryType.SK_FULL_SCAN); // 设置查询类型为二级索引全扫描
    }
    // by default clustering index will be used to scan table
    // 默认情况下使用聚簇索引（主键索引）扫描表
    return IndexCondition.create(InnodbRules.innodbFieldNames(getRowType()), // 创建索引条件对象，传入行类型字段名
        Constants.PRIMARY_KEY_NAME, // 使用主键索引名称
        innodbTable.getTableDef().getPrimaryKeyColumnNames(), // 传入主键列名
        QueryType.PK_FULL_SCAN); // 设置查询类型为主键全扫描
  }
}
