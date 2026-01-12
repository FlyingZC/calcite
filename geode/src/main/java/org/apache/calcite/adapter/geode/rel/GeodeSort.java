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
package org.apache.calcite.adapter.geode.rel; // 声明包名，该类属于 org.apache.calcite.adapter.geode.rel 包，是 Apache Calcite 框架中 Geode 适配器的关系表达式包

import org.apache.calcite.plan.RelOptCluster; // 导入关系表达式集群类，包含了一组关系表达式和共享的上下文信息
import org.apache.calcite.plan.RelOptCost; // 导入关系优化代价类，用于表示查询计划的执行成本
import org.apache.calcite.plan.RelOptPlanner; // 导入关系优化规划器接口，用于优化查询计划
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合类，包含关系表达式的物理属性（如约定、排序规则等）
import org.apache.calcite.rel.RelCollation; // 导入关系排序规则类，定义了字段的排序方式
import org.apache.calcite.rel.RelFieldCollation; // 导入关系字段排序类，定义单个字段的排序方向和空值处理
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系表达式的基础接口
import org.apache.calcite.rel.core.Sort; // 导入排序关系表达式类，GeodeSort 继承自此类
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入关系元数据查询类，用于查询关系表达式的元数据信息
import org.apache.calcite.rex.RexLiteral; // 导入行表达式字面量类，表示常量值
import org.apache.calcite.rex.RexNode; // 导入行表达式节点接口，所有行表达式的基础接口

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为 null 的参数或返回值

import java.util.ArrayList; // 导入动态数组类，用于存储可变长度的元素列表
import java.util.List; // 导入列表接口，定义了列表的基本操作

import static java.util.Objects.requireNonNull; // 静态导入 Objects.requireNonNull 方法，用于非空检查

/**
 * Implementation of
 * {@link Sort}
 * relational expression in Geode.
 */
// 类注释：GeodeSort 是 Sort 关系表达式在 Apache Geode 数据存储中的实现类
// 它继承自 Sort（排序关系表达式基类），并实现了 GeodeRel 接口（Geode 关系表达式接口）
// 该类负责将 SQL 中的 ORDER BY 子句转换为 Geode 的 OQL 查询语句中的排序操作
// GeodeSort 是 Calcite 优化器生成的物理算子，用于在 Geode 数据源上执行排序操作
public class GeodeSort extends Sort implements GeodeRel { // 类声明：GeodeSort 继承 Sort 类并实现 GeodeRel 接口

  public static final String ASC = "ASC"; // 静态常量：表示升序排序的字符串常量，对应 SQL 中的 ASC 关键字
  public static final String DESC = "DESC"; // 静态常量：表示降序排序的字符串常量，对应 SQL 中的 DESC 关键字

  /** Creates a GeodeSort. */
  // 构造方法注释：创建一个 GeodeSort 实例，用于表示 Geode 数据源上的排序操作
  GeodeSort(RelOptCluster cluster, RelTraitSet traitSet, // 参数 cluster：关系表达式集群，包含优化器和类型工厂
      RelNode input, RelCollation collation, @Nullable RexNode fetch) { // 参数 input：输入关系节点；参数 collation：排序规则；参数 fetch：限制返回的行数（LIMIT）
    super(cluster, traitSet, input, collation, null, fetch); // 调用父类 Sort 的构造方法，初始化排序关系表达式，offset 传 null 表示不跳过行

    assert getConvention() == GeodeRel.CONVENTION; // 断言：确保当前节点的约定（Convention）是 GeodeRel.CONVENTION，即该节点属于 Geode 适配器
    assert getConvention() == input.getConvention(); // 断言：确保当前节点的约定与输入节点的约定一致，保证物理属性匹配
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 方法注解：重写父类方法，计算当前排序节点的执行代价
      RelMetadataQuery mq) { // 参数 planner：关系优化规划器；参数 mq：关系元数据查询对象，用于获取元数据信息
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq)); // 调用父类方法计算基础代价，并使用 requireNonNull 确保结果非空
    if (fetch != null) { // 如果存在 fetch 限制（LIMIT 子句），说明只需要返回少量结果
      return cost.multiplyBy(0.05); // 返回代价乘以 0.05，表示有 LIMIT 时代价很低，因为 Geode 可以利用索引快速获取少量数据
    } else { // 如果不存在 fetch 限制，需要返回所有排序结果
      return cost.multiplyBy(0.9); // 返回代价乘以 0.9，表示全量排序代价较高，但仍然比内存排序便宜
    }
  }

  @Override public Sort copy(RelTraitSet traitSet, RelNode input, // 方法注解：重写父类方法，创建当前排序节点的副本，用于优化器进行规则转换
      RelCollation newCollation, RexNode offset, RexNode fetch) { // 参数 traitSet：新的特征集合；参数 input：新的输入节点；参数 newCollation：新的排序规则；参数 offset：偏移量；参数 fetch：限制行数
    return new GeodeSort(getCluster(), traitSet, input, collation, fetch); // 创建新的 GeodeSort 实例，注意使用当前的 collation 而不是 newCollation，保持排序规则不变
  }

  @Override public void implement(GeodeImplementContext geodeImplementContext) { // 方法注解：实现 Geode 关系表达式，将排序操作转换为 Geode OQL 查询语句
    geodeImplementContext.visitChild(getInput()); // 首先访问子节点（输入关系），让子节点先生成对应的 OQL 查询片段

    List<RelFieldCollation> sortCollations = collation.getFieldCollations(); // 获取排序规则中的字段排序列表，每个元素表示一个字段的排序方式

    if (!sortCollations.isEmpty()) { // 如果存在排序字段（即有 ORDER BY 子句）

      List<String> orderByFields = new ArrayList<>(); // 创建字符串列表，用于存储排序字段的 OQL 表达式（如 "name ASC"、"age DESC"）

      for (RelFieldCollation fieldCollation : sortCollations) { // 遍历每个字段排序规则
        final String name = fieldName(fieldCollation.getFieldIndex()); // 根据字段索引获取字段名称
        orderByFields.add(name + " " + direction(fieldCollation.getDirection())); // 将字段名和排序方向组合成字符串（如 "name ASC"）并添加到列表
      }
      geodeImplementContext.addOrderByFields(orderByFields); // 将排序字段列表添加到实现上下文中，用于生成 OQL 的 ORDER BY 子句
    }

    if (fetch != null) { // 如果存在 fetch 限制（即有 LIMIT 子句）
      geodeImplementContext.setLimit(RexLiteral.numberValue(fetch).longValue()); // 从 RexLiteral 中提取数值，设置为查询的 LIMIT 值，转换为 long 类型
    }
  }

  private String fieldName(int index) { // 私有方法：根据字段索引获取字段名称，用于生成 OQL 查询中的字段引用
    return getRowType().getFieldList().get(index).getName(); // 获取当前关系表达式的行类型，从字段列表中取出指定索引的字段，并返回其名称
  }

  private static String direction(RelFieldCollation.Direction relDirection) { // 私有静态方法：将 Calcite 的排序方向枚举转换为 Geode OQL 的排序方向字符串
    if (relDirection == RelFieldCollation.Direction.DESCENDING) { // 如果排序方向是降序（DESCENDING）
      return DESC; // 返回 "DESC" 字符串常量
    }
    return ASC; // 否则（升序或未指定），返回 "ASC" 字符串常量
  }
} // 类结束括号：GeodeSort 类定义结束
