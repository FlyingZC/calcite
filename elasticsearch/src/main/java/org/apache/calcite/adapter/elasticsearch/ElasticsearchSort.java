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
package org.apache.calcite.adapter.elasticsearch;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link org.apache.calcite.rel.core.Sort}
 * relational expression in Elasticsearch.
 */ // Elasticsearch排序操作符的实现类，继承自Calcite的Sort抽象类并实现ElasticsearchRel接口，用于将Calcite的排序逻辑转换为Elasticsearch的查询排序语句
public class ElasticsearchSort extends Sort implements ElasticsearchRel {
  ElasticsearchSort(RelOptCluster cluster, RelTraitSet traitSet, RelNode input,
      RelCollation collation, @Nullable RexNode offset,
      @Nullable RexNode fetch) { // 构造方法，创建Elasticsearch排序节点，参数包括：cluster-优化器集群，traitSet-特征集合，input-输入关系表达式，collation-排序规则（包含排序字段和方向），offset-偏移量（跳过前N行），fetch-获取行数（限制返回结果数）
    super(cluster, traitSet, input, collation, offset, fetch); // 调用父类Sort的构造方法初始化基本属性
    assert getConvention() == ElasticsearchRel.CONVENTION; // 断言当前节点的convention是ElasticsearchRel.CONVENTION，确保是Elasticsearch适配器的节点
    assert getConvention() == input.getConvention(); // 断言输入节点的convention与当前节点一致，确保整个关系表达式树使用相同的约定
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) { // 重写computeSelfCost方法，计算当前排序节点的执行成本，参数：planner-优化器，mq-元数据查询对象，返回值：计算出的成本对象
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq)); // 调用父类方法计算基础成本，requireNonNull确保结果不为null
    return cost.multiplyBy(0.05); // 将基础成本乘以0.05，表示Elasticsearch排序操作相对便宜，因为Elasticsearch原生支持高效排序
  }

  @Override public Sort copy(RelTraitSet traitSet, RelNode input,
      RelCollation relCollation, @Nullable RexNode offset,
      @Nullable RexNode fetch) { // 重写copy方法，创建当前节点的副本，参数：traitSet-新的特征集合，input-新的输入节点，relCollation-新的排序规则，offset-新的偏移量，fetch-新的获取行数，返回值：新的ElasticsearchSort节点
    return new ElasticsearchSort(getCluster(), traitSet, input, collation, // 创建并返回新的ElasticsearchSort实例，使用当前集群、新的特征集合、新的输入、当前排序规则、新的偏移量和获取行数
        offset, fetch); // 传递偏移量和获取行数参数
  }

  @Override public void implement(Implementor implementor) { // 重写implement方法，将当前排序节点转换为Elasticsearch查询语句，这是核心实现方法，参数：implementor-Elasticsearch查询构建器实现者对象
    implementor.visitChild(0, getInput()); // 先访问子节点（输入节点），确保子节点的查询逻辑已经被实现，构建出基础查询
    final List<RelDataTypeField> fields = getRowType().getFieldList(); // 获取当前节点的输出字段列表，用于后续字段名映射

    for (RelFieldCollation fieldCollation : collation.getFieldCollations()) { // 遍历排序规则中的每个字段排序条件
      final String name = fields.get(fieldCollation.getFieldIndex()).getName(); // 根据字段索引获取字段名称
      final String rawName = implementor.expressionItemMap.getOrDefault(name, name); // 从expressionItemMap中获取原始字段名（可能经过表达式转换），如果没有映射则使用原字段名
      // if nulls order is not specified, default NULLS LAST/FIRST for ASC/DESC // 如果未指定null值的排序顺序，则根据升序/降序默认使用NULLS LAST/FIRST
      implementor.addNullsSort(rawName, fieldCollation.nullDirection); // 向implementor添加null值的排序规则，指定null值应该排在前面还是后面
      implementor.addSort(rawName, fieldCollation.getDirection()); // 向implementor添加字段排序规则，指定字段的排序方向（升序或降序）
    }

    if (offset != null) { // 如果设置了偏移量（跳过前N行）
      implementor.offset(RexLiteral.numberValue(offset).longValue()); // 从offset表达式提取数值，转换为long类型，并设置到implementor中，对应Elasticsearch的from参数
    }

    if (fetch != null) { // 如果设置了获取行数（限制返回结果数）
      implementor.fetch(RexLiteral.numberValue(fetch).longValue()); // 从fetch表达式提取数值，转换为long类型，并设置到implementor中，对应Elasticsearch的size参数
    }
  }

}
