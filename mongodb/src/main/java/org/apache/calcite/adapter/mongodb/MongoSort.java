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
package org.apache.calcite.adapter.mongodb; // MongoDB适配器包，包含MongoDB相关的Calcite适配器实现

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式集群，包含优化器的上下文信息
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，表示关系表达式的成本估算
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，表示关系表达式优化器
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类，表示排序规则集合
import org.apache.calcite.rel.RelFieldCollation; // 导入RelFieldCollation类，表示单个字段的排序规则
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式节点
import org.apache.calcite.rel.core.Sort; // 导入Sort类，表示排序关系表达式
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类，表示关系数据类型的字段
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，表示行表达式字面量
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，表示行表达式节点
import org.apache.calcite.util.Util; // 导入Util工具类，提供各种实用方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可空类型

import java.util.ArrayList; // 导入ArrayList类，用于动态数组列表
import java.util.List; // 导入List接口，表示列表集合

/**
* Implementation of {@link org.apache.calcite.rel.core.Sort}
* relational expression in MongoDB.
* MongoSort类是Calcite中Sort关系表达式在MongoDB中的实现
* 它负责将Calcite的排序操作转换为MongoDB的$sort、$skip和$limit聚合管道操作
* 该类继承自Sort基类，并实现了MongoRel接口，表明它是一个MongoDB适配器特有的关系表达式
* 支持的功能包括：多字段排序（升序/降序）、偏移量（skip）和限制返回行数（limit）
*/
public class MongoSort extends Sort implements MongoRel { // MongoSort类继承Sort基类并实现MongoRel接口，表示MongoDB的排序操作
  public MongoSort(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法参数：cluster是优化器集群，包含优化器上下文；traitSet是特征集合，定义了该节点的物理属性
      RelNode child, RelCollation collation, RexNode offset, RexNode fetch) { // 参数：child是子节点（输入关系表达式）；collation是排序规则；offset是偏移量；fetch是限制返回的行数
    super(cluster, traitSet, child, collation, offset, fetch); // 调用父类Sort的构造方法，初始化排序关系表达式
    assert getConvention() == MongoRel.CONVENTION; // 断言当前节点的convention是MongoRel.CONVENTION，确保是MongoDB的物理实现
    assert getConvention() == child.getConvention(); // 断言子节点的convention与当前节点相同，确保整个计划的一致性
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算当前节点的执行成本；planner是优化器实例；mq是元数据查询对象
      RelMetadataQuery mq) { // 参数：RelMetadataQuery用于查询元数据信息
    return super.computeSelfCost(planner, mq).multiplyBy(0.05); // 调用父类的成本计算方法，并将结果乘以0.05，表示MongoDB的排序操作相对便宜，鼓励优化器优先使用MongoDB的排序
  }

  @Override public Sort copy(RelTraitSet traitSet, RelNode input, // 重写copy方法，创建当前节点的副本；traitSet是新的特征集合；input是新的输入节点
      RelCollation newCollation, RexNode offset, RexNode fetch) { // 参数：newCollation是新的排序规则；offset是新的偏移量；fetch是新的限制行数
    return new MongoSort(getCluster(), traitSet, input, collation, offset, // 创建并返回一个新的MongoSort实例，注意这里使用的是成员变量collation而不是参数newCollation
        fetch); // 返回新的MongoSort对象，保持原有的排序规则
  }

  @Override public void implement(Implementor implementor) { // 重写implement方法，将当前关系表达式转换为MongoDB的聚合管道操作；implementor是实现器对象，用于构建MongoDB查询
    implementor.visitChild(0, getInput()); // 访问并实现子节点（输入关系表达式），将子节点的MongoDB操作添加到实现器中
    if (!collation.getFieldCollations().isEmpty()) { // 检查是否有排序字段，如果有则生成MongoDB的$sort操作
      final List<String> keys = new ArrayList<>(); // 创建字符串列表，用于存储排序字段的键值对
      final List<RelDataTypeField> fields = getRowType().getFieldList(); // 获取当前节点的所有字段信息
      for (RelFieldCollation fieldCollation : collation.getFieldCollations()) { // 遍历每个字段的排序规则
        final String name = // 获取排序字段的名称
            fields.get(fieldCollation.getFieldIndex()).getName(); // 根据字段索引获取字段名称
        keys.add(MongoRules.maybeQuote(name) + ": " + direction(fieldCollation)); // 构建MongoDB排序键，字段名可能需要加引号，并添加排序方向（1升序，-1降序）
        if (false) { // 这个if条件永远为false，表示这段代码暂时不执行
          // TODO: NULLS FIRST and NULLS LAST // TODO注释：将来需要支持NULLS FIRST和NULLS LAST功能
          switch (fieldCollation.nullDirection) { // 根据null值的排序方向进行处理
          case FIRST: // 如果要求NULL值排在最前面
            break; // 暂时为空，等待实现
          case LAST: // 如果要求NULL值排在最后面
            break; // 暂时为空，等待实现
          default: // 其他情况
            break; // 暂时为空，等待实现
          }
        }
      }
      implementor.add(null, // 向实现器添加MongoDB的$sort聚合管道操作，第一个参数为null表示不需要特定的文档
          "{$sort: " + Util.toString(keys, "{", ", ", "}") + "}"); // 构建MongoDB的$sort操作字符串，使用Util工具类将键列表转换为MongoDB对象格式
    }
    if (offset != null) { // 检查是否有偏移量（OFFSET子句）
      implementor.add(null, // 向实现器添加MongoDB的$skip聚合管道操作
          "{$skip: " + ((RexLiteral) offset).getValue() + "}"); // 构建MongoDB的$skip操作字符串，跳过指定数量的文档
    }
    if (fetch != null) { // 检查是否有限制行数（LIMIT子句）
      implementor.add(null, // 向实现器添加MongoDB的$limit聚合管道操作
          "{$limit: " + ((RexLiteral) fetch).getValue() + "}"); // 构建MongoDB的$limit操作字符串，限制返回的文档数量
    }
  }

  private static int direction(RelFieldCollation fieldCollation) { // 私有静态方法，将Calcite的排序方向转换为MongoDB的排序方向；fieldCollation是字段排序规则对象
    switch (fieldCollation.getDirection()) { // 根据排序方向进行判断
    case DESCENDING: // 如果是降序排序
    case STRICTLY_DESCENDING: // 如果是严格降序排序
      return -1; // 返回-1，表示MongoDB中的降序排序
    case ASCENDING: // 如果是升序排序
    case STRICTLY_ASCENDING: // 如果是严格升序排序
    default: // 其他情况（默认）
      return 1; // 返回1，表示MongoDB中的升序排序
    }
  }
}
