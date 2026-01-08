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
// 声明当前类所属的包，这是Calcite框架中Cassandra适配器模块的包路径
package org.apache.calcite.adapter.cassandra;

// 导入RelOptCluster类，表示关系代数优化器的集群，包含类型工厂、表达式工厂等共享资源
import org.apache.calcite.plan.RelOptCluster;
// 导入RelOptCost类，表示关系代数操作的成本估算，用于查询优化器的成本比较
import org.apache.calcite.plan.RelOptCost;
// 导入RelOptPlanner类，表示查询优化器，用于根据成本选择最优的执行计划
import org.apache.calcite.plan.RelOptPlanner;
// 导入RelTraitSet类，表示关系节点的特征集合，包含约定、排序、分区等特征
import org.apache.calcite.plan.RelTraitSet;
// 导入RelCollation类，表示排序规则，定义了字段如何排序（升序或降序）
import org.apache.calcite.rel.RelCollation;
// 导入RelFieldCollation类，表示单个字段的排序规则，包含字段索引和排序方向
import org.apache.calcite.rel.RelFieldCollation;
// 导入RelNode接口，表示关系代数表达式的基础接口，所有关系节点都实现此接口
import org.apache.calcite.rel.RelNode;
// 导入Sort类，表示排序操作的关系节点，是CassandraSort的父类
import org.apache.calcite.rel.core.Sort;
// 导入RelMetadataQuery类，用于查询关系节点的元数据信息（如行数、大小等）
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// 导入RelDataTypeField类，表示关系数据类型的字段信息，包含字段名和类型
import org.apache.calcite.rel.type.RelDataTypeField;
// 导入RexNode类，表示行表达式（Row Expression），用于表示计算表达式
import org.apache.calcite.rex.RexNode;

// 导入Nullable注解，用于标记可能为null的返回值或参数
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入ArrayList类，用于创建动态数组，存储排序字段信息
import java.util.ArrayList;
// 导入List接口，用于表示有序集合
import java.util.List;

// 静态导入requireNonNull方法，用于检查对象是否为null，为null时抛出NullPointerException
import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link org.apache.calcite.rel.core.Sort}
 * relational expression in Cassandra.
 * 这个类是Calcite框架中Sort关系代数表达式在Cassandra数据源上的具体实现
 * 它负责将Calcite的排序操作转换为Cassandra的ORDER BY子句
 * CassandraSort继承自Sort基类，并实现了CassandraRel接口，表明它是一个Cassandra特定的关系节点
 */
public class CassandraSort extends Sort implements CassandraRel {
  // 构造方法：创建一个CassandraSort节点
  // 参数说明：
  //   - cluster: 关系优化器集群，包含类型工厂等共享资源
  //   - traitSet: 特征集合，包含约定（Cassandra约定）、排序规则等
  //   - child: 子节点，表示排序操作的数据源输入
  //   - collation: 排序规则，定义了按哪些字段排序以及排序方向
  public CassandraSort(RelOptCluster cluster, RelTraitSet traitSet,
      RelNode child, RelCollation collation) {
    // 调用父类Sort的构造方法，传入参数
    // 注意：offset和fetch参数都传null，表示不使用LIMIT和OFFSET
    // 这是因为Cassandra的排序操作不支持offset和fetch（分页）
    super(cluster, traitSet, child, collation, null, null);

    // 断言当前节点的约定是Cassandra约定，确保这是一个Cassandra特定的节点
    assert getConvention() == CassandraRel.CONVENTION;
    // 断言子节点的约定也是Cassandra约定，确保约定一致性
    assert getConvention() == child.getConvention();
  }

  // 重写computeSelfCost方法，计算当前排序节点的执行成本
  // 参数说明：
  //   - planner: 查询优化器，用于成本计算
  //   - mq: 元数据查询接口，用于获取行数、大小等元数据信息
  // 返回值：计算出的成本对象，如果成本无法计算则返回null
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    // 调用父类的成本计算方法，并确保返回值不为null
    // 如果父类返回null，requireNonNull会抛出NullPointerException
    RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq));
    // 检查是否有实际的排序字段（即collation中是否有排序规则）
    if (!collation.getFieldCollations().isEmpty()) {
      // 如果有排序规则，将成本乘以0.05
      // 这个系数表示Cassandra的排序操作相对便宜（可能是因为使用了聚簇主键等优化）
      // 较低的成本会使得优化器更倾向于在Cassandra端进行排序
      return cost.multiplyBy(0.05);
    } else {
      // 如果没有排序规则（即只是Sort节点但未指定排序字段），返回原始成本
      return cost;
    }
  }

  // 重写copy方法，创建当前节点的一个副本
  // 参数说明：
  //   - traitSet: 新节点的特征集合，可以修改约定等特征
  //   - input: 新节点的输入子节点
  //   - newCollation: 新的排序规则（注意：这个参数在实现中未被使用）
  //   - offset: 分页偏移量（注意：这个参数在实现中未被使用，因为Cassandra不支持）
  //   - fetch: 分页限制数量（注意：这个参数在实现中未被使用，因为Cassandra不支持）
  // 返回值：新的CassandraSort节点
  @Override public Sort copy(RelTraitSet traitSet, RelNode input,
      RelCollation newCollation, @Nullable RexNode offset, @Nullable RexNode fetch) {
    // 创建并返回一个新的CassandraSort节点
    // 注意：这里使用的是原始的collation而不是newCollation参数
    // 这可能是因为Cassandra的排序规则在创建后不能改变
    return new CassandraSort(getCluster(), traitSet, input, collation);
  }

  // 重写implement方法，将当前关系节点转换为Cassandra的CQL语句
  // 参数说明：
  //   - implementor: 实现器对象，负责构建CQL语句和管理上下文
  // 返回值：无（void），直接修改implementor对象的状态
  @Override public void implement(Implementor implementor) {
    // 首先访问并实现子节点（即数据源），将子节点的CQL语句添加到实现器中
    // 参数0表示第一个子节点（Sort只有一个子节点）
    implementor.visitChild(0, getInput());

    // 获取排序规则中的字段排序列表，每个RelFieldCollation代表一个字段的排序方式
    List<RelFieldCollation> sortCollations = collation.getFieldCollations();
    // 创建字符串列表，用于存储CQL的ORDER BY子句中的字段排序表达式
    // 格式如："field_name ASC" 或 "field_name DESC"
    List<String> fieldOrder = new ArrayList<>();
    // 检查是否有排序规则（即是否需要添加ORDER BY子句）
    if (!sortCollations.isEmpty()) {
      // 获取当前节点的行类型（即输出结果的字段类型信息）
      // 从行类型中获取所有字段的列表，用于后续通过索引查找字段名
      final List<RelDataTypeField> fields = getRowType().getFieldList();
      // 遍历每个字段的排序规则
      for (RelFieldCollation fieldCollation : sortCollations) {
        // 根据字段索引获取字段名称
        // fieldCollation.getFieldIndex()返回字段在行类型中的索引位置
        final String name =
            fields.get(fieldCollation.getFieldIndex()).getName();
        // 声明排序方向字符串变量
        final String direction;
        // 根据排序方向枚举值确定CQL中的排序关键字
        switch (fieldCollation.getDirection()) {
        case DESCENDING:
          // 如果是降序，使用"DESC"关键字
          direction = "DESC";
          break;
        default:
          // 默认情况（包括ASCENDING）使用"ASC"关键字
          direction = "ASC";
        }
        // 将字段名和排序方向组合成CQL排序表达式，如："age DESC"
        // 添加到fieldOrder列表中
        fieldOrder.add(name + " " + direction);
      }

      // 将所有字段排序表达式添加到实现器中
      // 实现器会将这些表达式组合成ORDER BY子句添加到CQL语句中
      // 例如："ORDER BY age DESC, name ASC"
      implementor.addOrder(fieldOrder);
    }
  }
}
