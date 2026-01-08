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
package org.apache.calcite.rel.core; // 定义包名，该类属于org.apache.calcite.rel.core包，是Calcite关系代数核心包的一部分

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于表示关系代数优化器的集群，包含优化器上下文信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系节点的特征集合，如物理实现方式等
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，是所有关系代数节点的基类
import org.apache.calcite.rel.RelWriter; // 导入RelWriter接口，用于将关系代数节点以可读格式输出
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint类，用于表示关系代数节点的提示信息
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，用于表示行表达式，是Calcite中表达式的抽象表示

import java.util.List; // 导入List接口，用于表示有序集合
import java.util.Set; // 导入Set接口，用于表示无序不重复集合

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Base class for various ASOF JOIN representations. // 类注释：这是各种ASOF JOIN表示的基类
 * ASOF JOIN是一种特殊的连接操作，用于将左表的每一行与右表中满足某个条件的最近一行进行连接
 * 它常用于时间序列数据的处理，例如将实时数据与历史数据进行匹配
 * 与标准JOIN不同，ASOF JOIN不需要精确匹配，而是找到"最近"的匹配行
 * 该类是抽象类，定义了ASOF JOIN的通用结构和行为，具体的实现由子类完成
 */
public abstract class AsofJoin extends Join { // 定义抽象类AsofJoin，继承自Join类，表示ASOF JOIN关系代数节点
  /** Compared to standard joins, ASOF joins have an additional condition for comparing // 成员变量注释：与标准连接相比，ASOF连接有一个额外的条件用于比较
   * columns that usually contain timestamp values (however, the data type of these columns // 通常包含时间戳值的列（但这些列的数据类型可以是任何支持比较的类型，并不限于TIMESTAMP）
   * can be any type that supports comparisons, and is not restricted to be TIMESTAMP). // matchCondition用于定义ASOF JOIN中"最近"匹配的判断条件
   * 这个条件通常是一个比较表达式，例如 left.timestamp <= right.timestamp
   * 通过这个条件，ASOF JOIN能够找到右表中满足条件的最近一行数据与左表连接
   */ // 成员变量注释结束
  protected final RexNode matchCondition; // 定义受保护的最终成员变量matchCondition，类型为RexNode，用于存储ASOF JOIN的匹配条件表达式

  protected AsofJoin( // 定义受保护的构造方法，用于创建AsofJoin实例
      RelOptCluster cluster, // 参数：RelOptCluster对象，包含关系代数优化器的集群信息和上下文
      RelTraitSet traitSet, // 参数：RelTraitSet对象，定义该关系节点的特征集合，如物理实现方式
      List<RelHint> hints, // 参数：RelHint列表，包含该关系节点的提示信息，用于指导优化器
      RelNode left, // 参数：RelNode对象，表示连接操作的左子节点（左表）
      RelNode right, // 参数：RelNode对象，表示连接操作的右子节点（右表）
      RexNode condition, // 参数：RexNode对象，表示连接条件，即标准JOIN的连接谓词
      RexNode matchCondition, // 参数：RexNode对象，表示ASOF JOIN的匹配条件，用于找到最近的匹配行
      Set<CorrelationId> variablesSet, // 参数：CorrelationId集合，表示相关变量的集合，用于处理相关子查询
      JoinRelType joinType) { // 参数：JoinRelType枚举，表示连接类型（如INNER、LEFT、RIGHT等）
    super(cluster, traitSet, hints, left, right, condition, variablesSet, joinType); // 调用父类Join的构造方法，初始化父类的成员变量
    this.matchCondition = requireNonNull(matchCondition, "matchCondition"); // 初始化matchCondition成员变量，使用requireNonNull确保matchCondition不为null，否则抛出NullPointerException
  } // 构造方法结束

  public RexNode getMatchCondition() { // 定义公共方法getMatchCondition，用于获取ASOF JOIN的匹配条件
    return matchCondition; // 返回matchCondition成员变量的值
  } // 方法结束

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写explainTerms方法，用于将关系代数节点的详细信息输出到RelWriter
    return super.explainTerms(pw) // 调用父类的explainTerms方法，获取父类输出的基础信息
        .item("matchCondition", matchCondition); // 添加matchCondition项到输出中，以便在查询计划中显示ASOF JOIN的匹配条件
  } // 方法结束
} // 类定义结束
