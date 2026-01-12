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
package org.apache.calcite.adapter.pig;

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于表示关系代数操作的集群信息，包含表达式工厂和类型工厂等
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，用于表示优化器中的表对象
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil类，提供关系代数操作的实用工具方法，如拆分连接条件等
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系节点的特征集合，如物理实现方式等
import org.apache.calcite.rel.RelNode; // 导入RelNode类，是所有关系代数节点的基类
import org.apache.calcite.rel.core.Join; // 导入Join类，表示连接操作的基类
import org.apache.calcite.rel.core.JoinRelType; // 导入JoinRelType枚举，定义连接类型如INNER、LEFT、RIGHT、FULL等
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示行表达式中的函数调用
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式的基类
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举，定义SQL操作的类型如EQUALS、AND、OR等

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，用于创建不可变的列表
import com.google.common.collect.ImmutableSet; // 导入Google Guava的ImmutableSet类，用于创建不可变的集合

import java.util.ArrayList; // 导入ArrayList类，用于动态数组列表
import java.util.List; // 导入List接口，表示列表集合

/** Implementation of {@link org.apache.calcite.rel.core.Join} in
 * {@link PigRel#CONVENTION Pig calling convention}. */ // PigJoin类实现了Join接口，用于在Pig调用约定中实现连接操作，将Calcite的关系代数连接转换为Pig Latin的JOIN语句
public class PigJoin extends Join implements PigRel { // PigJoin继承自Join基类并实现PigRel接口，表示这是一个Pig适配器中的连接节点

  /** Creates a PigJoin. */ // 创建PigJoin实例的构造方法，用于初始化一个Pig连接节点
  public PigJoin(RelOptCluster cluster, RelTraitSet traitSet, RelNode left, RelNode right, // 参数cluster：关系代数集群，包含表达式工厂和类型工厂；参数traitSet：特征集合，指定物理实现方式为Pig；参数left：左子节点，表示连接的左输入关系；参数right：右子节点，表示连接的右输入关系
      RexNode condition, JoinRelType joinType) { // 参数condition：连接条件，通常是一个等值比较表达式；参数joinType：连接类型，如INNER（内连接）、LEFT OUTER（左外连接）等
    super(cluster, traitSet, ImmutableList.of(), left, right, condition, // 调用父类Join的构造方法，传入集群、特征集、空变量列表、左右子节点、连接条件
        ImmutableSet.of(), joinType); // 传入空字段集合和连接类型，完成父类初始化
    assert getConvention() == PigRel.CONVENTION; // 断言当前节点的调用约定必须是Pig约定，确保这是一个合法的Pig节点
  }

  @Override public Join copy(RelTraitSet traitSet, RexNode conditionExpr, RelNode left, // 重写copy方法，用于创建当前PigJoin节点的副本，可以修改部分属性
      RelNode right, JoinRelType joinType, boolean semiJoinDone) { // 参数traitSet：新的特征集合；参数conditionExpr：新的连接条件表达式；参数left：新的左子节点；参数right：新的右子节点；参数joinType：新的连接类型；参数semiJoinDone：是否完成半连接标记（本类不使用）
    return new PigJoin(getCluster(), traitSet, left, right, conditionExpr, joinType); // 返回一个新的PigJoin实例，使用当前集群、新的特征集、左右子节点、连接条件和连接类型
  }

  @Override public void implement(Implementor implementor) { // 重写implement方法，用于将当前PigJoin节点转换为Pig Latin语句并添加到实现器中
    implementor.visitChild(0, getLeft()); // 访问左子节点，递归地实现左输入关系，生成对应的Pig语句
    implementor.visitChild(0, getRight()); // 访问右子节点，递归地实现右输入关系，生成对应的Pig语句
    implementor.addStatement(getPigJoinStatement(implementor)); // 调用getPigJoinStatement方法生成Pig JOIN语句，并将其添加到实现器的语句列表中
  }

  /**
   * The Pig alias of the joined relation will have the same name as one from
   * the left side of the join.
   */ // 注释说明：连接后的Pig关系别名将与连接左侧的别名相同，这是Pig Latin的命名约定
  @Override public RelOptTable getTable() { // 重写getTable方法，返回连接操作对应的表对象
    return getLeft().getTable(); // 返回左子节点的表对象，因为连接结果的表信息继承自左表
  }

  /**
   * Constructs a Pig JOIN statement in the form of
   * <pre>
   * {@code
   * A = JOIN A BY f1 LEFT OUTER, B BY f2;
   * }
   * </pre>
   * Only supports simple equi-joins with single column on both sides of
   * <code>=</code>.
   */ // 构造Pig JOIN语句，格式为"A = JOIN A BY f1 LEFT OUTER, B BY f2;"，仅支持简单的等值连接，即等号两边各有一个字段
  private String getPigJoinStatement(Implementor implementor) { // 私有方法，用于生成Pig Latin的JOIN语句字符串
    if (!getCondition().isA(SqlKind.EQUALS)) { // 检查连接条件是否是等值比较（EQUALS类型）
      throw new IllegalArgumentException("Only equi-join are supported"); // 如果不是等值连接，抛出异常，因为Pig只支持等值连接
    }
    List<RexNode> operands = ((RexCall) getCondition()).getOperands(); // 将连接条件转换为RexCall对象，并获取其操作数列表（等号两边的字段引用）
    if (operands.size() != 2) { // 检查操作数数量是否为2，确保是简单的二元等值比较
      throw new IllegalArgumentException("Only equi-join are supported"); // 如果操作数不是2个，抛出异常，不支持复杂条件
    }
    List<Integer> leftKeys = new ArrayList<>(1); // 创建左键列表，用于存储左表中参与连接的字段索引
    List<Integer> rightKeys = new ArrayList<>(1); // 创建右键列表，用于存储右表中参与连接的字段索引
    List<Boolean> filterNulls = new ArrayList<>(1); // 创建过滤空值列表，用于标记是否过滤空值
    RelOptUtil.splitJoinCondition(getLeft(), getRight(), getCondition(), leftKeys, rightKeys, // 调用RelOptUtil工具类的splitJoinCondition方法，解析连接条件，提取左右表的连接键字段索引
        filterNulls); // 同时解析空值过滤策略

    String leftRelAlias = implementor.getPigRelationAlias((PigRel) getLeft()); // 从实现器中获取左子节点的Pig关系别名（如"A"）
    String rightRelAlias = implementor.getPigRelationAlias((PigRel) getRight()); // 从实现器中获取右子节点的Pig关系别名（如"B"）
    String leftJoinFieldName = implementor.getFieldName((PigRel) getLeft(), leftKeys.get(0)); // 根据左键索引获取左表中参与连接的字段名（如"f1"）
    String rightJoinFieldName = implementor.getFieldName((PigRel) getRight(), rightKeys.get(0)); // 根据右键索引获取右表中参与连接的字段名（如"f2"）

    return implementor.getPigRelationAlias((PigRel) getLeft()) + " = JOIN " + leftRelAlias + " BY " // 拼接Pig JOIN语句：结果别名 = JOIN 左关系 BY 左字段
        + leftJoinFieldName + ' ' + getPigJoinType() + ", " + rightRelAlias + " BY " // 拼接连接类型和右关系：连接类型, 右关系 BY
        + rightJoinFieldName + ';'; // 拼接右字段和分号：右字段; 完整语句如"A = JOIN A BY f1 LEFT OUTER, B BY f2;"
  }

  /**
   * Get a string representation of the type of join for use in a Pig script.
   * Pig does not have an explicit "inner" marker, so return an empty string in
   * this case.
   */ // 获取连接类型的字符串表示，用于Pig脚本。Pig没有显式的"INNER"标记，因此内连接返回空字符串
  private String getPigJoinType() { // 私有方法，用于获取Pig Latin中连接类型的字符串表示
    switch (getJoinType()) { // 根据当前连接类型进行分支判断
    case INNER: // 如果是内连接（INNER）
      return ""; // 返回空字符串，因为Pig中JOIN默认就是内连接，不需要显式标记
    default: // 对于其他连接类型（LEFT OUTER、RIGHT OUTER、FULL OUTER等）
      return getJoinType().name(); // 返回连接类型的名称字符串，如"LEFT OUTER"、"RIGHT OUTER"等
    }
  }
}
