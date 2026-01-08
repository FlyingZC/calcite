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
 */ // Apache License 2.0许可证声明：本代码由Apache软件基金会授权，遵循Apache 2.0开源协议
package org.apache.calcite.rel.logical; // 包声明：LogicalMatch类位于org.apache.calcite.rel.logical包下，这是逻辑关系表达式所在的包

import org.apache.calcite.plan.Convention; // 导入Convention类，定义关系表达式的调用约定（如逻辑层、物理层等）
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系代数表达式集群，包含共享的元数据和工厂
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合（如排序、分布等）
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类，表示排序规范（定义排序列和排序方向）
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，所有关系表达式节点的基类
import org.apache.calcite.rel.RelShuttle; // 导入RelShuttle接口，用于访问者模式遍历和修改关系表达式树
import org.apache.calcite.rel.core.Match; // 导入Match基类，LogicalMatch继承自这个类，Match定义了模式匹配的核心功能
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型（行类型、字段类型等）
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式（用于条件、计算等）
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合（用于表示列索引集合）

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，标记参数或返回值可以为null（来自CheckerFramework）

import java.util.List; // 导入List接口，表示有序列表
import java.util.Map; // 导入Map接口，表示键值对映射
import java.util.SortedSet; // 导入SortedSet接口，表示有序集合

/**
 * Sub-class of {@link Match}
 * not targeted at any particular engine or calling convention.
 * LogicalMatch是Match逻辑操作符的子类，用于表示SQL中的MATCH_RECOGNIZE子句
 * MATCH_RECOGNIZE是SQL标准中用于模式匹配的功能，可以在数据流中识别复杂的模式
 * 这个类不针对特定的引擎或调用约定，是逻辑层面的表示，后续可以被转换为物理执行计划
 * 主要功能：在有序的数据流中识别符合特定模式的行序列（例如股票价格走势、欺诈检测等场景）
 */
public class LogicalMatch extends Match {

  /**
   * Creates a LogicalMatch. // 创建一个LogicalMatch实例
   *
   * <p>Use {@link #create} unless you know what you're doing. // 除非你清楚自己在做什么，否则建议使用create静态工厂方法
   *
   * @param cluster Cluster // RelOptCluster对象，包含关系代数表达式集群的共享信息（如类型系统、表达式工厂等）
   * @param traitSet Trait set // RelTraitSet对象，定义关系表达式的特征集合（如调用约定、排序方式等）
   * @param input Input relational expression // 输入的关系表达式（子查询或表）
   * @param rowType Row type // RelDataType对象，定义输出行的类型结构
   * @param pattern Regular Expression defining pattern variables // RexNode对象，定义模式变量的正则表达式（如 PATTERN (A B+ C)）
   * @param strictStart Whether it is a strict start pattern // 是否为严格开始模式（true表示匹配必须从分区第一行开始）
   * @param strictEnd Whether it is a strict end pattern // 是否为严格结束模式（true表示匹配必须到分区最后一行结束）
   * @param patternDefinitions Pattern definitions // Map对象，键为模式变量名，值为定义该变量的布尔表达式（如 A AS A.price > 100）
   * @param measures Measure definitions // Map对象，键为度量名称，值为计算该度量的表达式（如 FINAL FIRST(A.price) AS start_price）
   * @param after After match definitions // RexNode对象，定义匹配后的跳过策略（如 AFTER MATCH SKIP TO NEXT ROW）
   * @param subsets Subset definitions // Map对象，键为子集名称，值为包含的模式变量集合（如 SUBSET AB = (A, B)）
   * @param allRows Whether all rows per match (false means one row per match) // 是否每行匹配都输出（false表示每个匹配只输出一行）
   * @param partitionKeys Partition by columns // ImmutableBitSet对象，指定分区列的索引集合（类似GROUP BY的分组列）
   * @param orderKeys Order by columns // RelCollation对象，定义排序列和排序方向（用于保证输入行的顺序）
   * @param interval Interval definition, null if WITHIN clause is not defined // RexNode对象，定义时间间隔限制（如 WITHIN 1 HOUR），null表示没有间隔限制
   */
  public LogicalMatch(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法：接收所有参数并初始化LogicalMatch对象
      RelNode input, RelDataType rowType, RexNode pattern, // 参数：输入关系表达式、输出行类型、模式正则表达式
      boolean strictStart, boolean strictEnd, // 参数：是否严格开始、是否严格结束
      Map<String, RexNode> patternDefinitions, Map<String, RexNode> measures, // 参数：模式定义映射、度量定义映射
      RexNode after, Map<String, ? extends SortedSet<String>> subsets, // 参数：匹配后跳过策略、子集定义映射
      boolean allRows, ImmutableBitSet partitionKeys, RelCollation orderKeys, // 参数：是否输出所有行、分区键、排序列
      @Nullable RexNode interval) { // 参数：时间间隔定义（可为null）
    super(cluster, traitSet, input, rowType, pattern, strictStart, strictEnd, // 调用父类Match的构造方法，传递所有参数
        patternDefinitions, measures, after, subsets, allRows, partitionKeys, // 继续传递参数
        orderKeys, interval); // 传递剩余参数
  } // 构造方法结束

  /**
   * Creates a LogicalMatch. // 创建LogicalMatch实例的静态工厂方法（简化版本）
   * 这个方法会自动从输入节点获取cluster，并使用Convention.NONE作为traitSet
   */
  public static LogicalMatch create(RelNode input, RelDataType rowType, // 参数：模式正则表达式、严格开始标志、严格结束标志
      RexNode pattern, boolean strictStart, boolean strictEnd, // 参数：模式定义映射、度量定义映射
      Map<String, RexNode> patternDefinitions, Map<String, RexNode> measures, // 参数：匹配后跳过策略、子集定义映射
      RexNode after, Map<String, ? extends SortedSet<String>> subsets, boolean allRows, // 参数：是否输出所有行、分区键、排序列
      ImmutableBitSet partitionKeys, RelCollation orderKeys, @Nullable RexNode interval) { // 参数：时间间隔定义
    final RelOptCluster cluster = input.getCluster(); // 从输入节点获取RelOptCluster（包含类型系统等共享信息）
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE); // 创建traitSet，使用Convention.NONE（表示逻辑层，不针对特定引擎）
    return create(cluster, traitSet, input, rowType, pattern, // 调用重载的create方法，传递所有参数
        strictStart, strictEnd, patternDefinitions, measures, after, subsets, // 继续传递参数
        allRows, partitionKeys, orderKeys, interval); // 传递剩余参数并返回LogicalMatch实例
  } // 静态工厂方法结束

  /**
   * Creates a LogicalMatch. // 创建LogicalMatch实例的静态工厂方法（完整版本）
   * 这个方法允许自定义cluster和traitSet，提供更灵活的创建方式
   */
  public static LogicalMatch create(RelOptCluster cluster, // 参数：RelTraitSet对象、输入关系表达式、输出行类型
      RelTraitSet traitSet, RelNode input, RelDataType rowType, // 参数：模式正则表达式、严格开始标志、严格结束标志
      RexNode pattern, boolean strictStart, boolean strictEnd, // 参数：模式定义映射、度量定义映射
      Map<String, RexNode> patternDefinitions, Map<String, RexNode> measures, // 参数：匹配后跳过策略、子集定义映射
      RexNode after, Map<String, ? extends SortedSet<String>> subsets, // 参数：是否输出所有行、分区键、排序列
      boolean allRows, ImmutableBitSet partitionKeys, RelCollation orderKeys, // 参数：时间间隔定义
      @Nullable RexNode interval) { // 创建并返回新的LogicalMatch实例
    return new LogicalMatch(cluster, traitSet, input, rowType, pattern, // 调用构造方法创建LogicalMatch对象
        strictStart, strictEnd, patternDefinitions, measures, after, subsets, // 传递所有参数
        allRows, partitionKeys, orderKeys, interval); // 传递剩余参数并返回新创建的实例
  } // 静态工厂方法结束

  //~ Methods ------------------------------------------------------ // 方法部分分隔符

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 参数：新的traitSet集合、输入节点列表
    return new LogicalMatch(getCluster(), traitSet, inputs.get(0), getRowType(), // 创建新的LogicalMatch实例，使用新的traitSet和输入
        pattern, strictStart, strictEnd, patternDefinitions, measures, after, // 复制所有模式匹配相关的属性
        subsets, allRows, partitionKeys, orderKeys, interval); // 复制剩余属性并返回新节点
  } // copy方法结束

  @Override public RelNode accept(RelShuttle shuttle) { // 重写accept方法：接受访问者模式的访问
    return shuttle.visit(this); // 将当前LogicalMatch节点传递给RelShuttle访问者进行处理，返回处理后的节点
  } // accept方法结束
}
