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
 */ // Apache许可证声明，声明代码版权和使用条款
package org.apache.calcite.rel.logical; // 声明包名，该类位于org.apache.calcite.rel.logical包下

import org.apache.calcite.plan.Convention; // 导入Convention类，用于定义关系表达式的调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于表示关系表达式集群
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系表达式的特征集合
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef类，用于定义排序特征
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入RelDistributionTraitDef类，用于定义分布特征
import org.apache.calcite.rel.RelInput; // 导入RelInput类，用于从序列化输入创建关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式节点
import org.apache.calcite.rel.RelShuttle; // 导入RelShuttle接口，用于访问和修改关系表达式树
import org.apache.calcite.rel.core.Values; // 导入Values基类，LogicalValues继承自该类
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint类，用于表示关系表达式的提示
import org.apache.calcite.rel.metadata.RelMdCollation; // 导入RelMdCollation类，用于计算排序元数据
import org.apache.calcite.rel.metadata.RelMdDistribution; // 导入RelMdDistribution类，用于计算分布元数据
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询元数据
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，用于表示行表达式字面量
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，用于定义SQL类型名称

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，用于创建不可变列表

import java.math.BigDecimal; // 导入BigDecimal类，用于精确的十进制数值计算
import java.util.Collections; // 导入Collections工具类，提供集合操作方法
import java.util.List; // 导入List接口，用于列表集合

/**
 * Sub-class of {@link org.apache.calcite.rel.core.Values} // Values类的子类
 * not targeted at any particular engine or calling convention. // 不针对任何特定的引擎或调用约定
 */ // 这表示LogicalValues是一个逻辑层面的Values节点，不绑定到具体的物理实现
public class LogicalValues extends Values { // 声明LogicalValues类，继承自Values基类
  //~ Constructors ----------------------------------------------------------- // 构造方法区域的分隔符

  /**
   * Creates a LogicalValues. // 创建一个LogicalValues实例
   *
   * <p>Use {@link #create} unless you know what you're doing. // 除非你清楚自己在做什么，否则应该使用create静态工厂方法
   *
   * @param cluster Cluster that this relational expression belongs to // 该关系表达式所属的集群
   * @param hints   Hints for this node // 该节点的提示信息
   * @param rowType Row type for tuples produced by this rel // 该关系表达式产生的元组的行类型
   * @param tuples  2-dimensional array of tuple values to be produced; outer // 要产生的元组值的二维数组；外层
   *                list contains tuples; each inner list is one tuple; all // 列表包含元组；每个内层列表是一个元组；所有
   *                tuples must be of same length, conforming to rowType // 元组必须具有相同的长度，符合rowType
   */ // 完整的构造方法文档注释
  public LogicalValues( // 构造方法声明
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：关系表达式特征集合
      List<RelHint> hints, // 参数：提示信息列表
      RelDataType rowType, // 参数：行类型
      ImmutableList<ImmutableList<RexLiteral>> tuples) { // 参数：元组列表，每个元组包含字面量列表
    super(cluster, hints, rowType, tuples, traitSet); // 调用父类Values的构造方法
  } // 构造方法结束

  /**
   * Creates a LogicalValues. // 创建一个LogicalValues实例
   *
   * <p>Use {@link #create} unless you know what you're doing. // 除非你清楚自己在做什么，否则应该使用create静态工厂方法
   *
   * @param cluster Cluster that this relational expression belongs to // 该关系表达式所属的集群
   * @param rowType Row type for tuples produced by this rel // 该关系表达式产生的元组的行类型
   * @param tuples  2-dimensional array of tuple values to be produced; outer // 要产生的元组值的二维数组；外层
   *                list contains tuples; each inner list is one tuple; all // 列表包含元组；每个内层列表是一个元组；所有
   *                tuples must be of same length, conforming to rowType // 元组必须具有相同的长度，符合rowType
   */ // 重载构造方法的文档注释
  public LogicalValues( // 构造方法声明（重载）
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：关系表达式特征集合
      RelDataType rowType, // 参数：行类型
      ImmutableList<ImmutableList<RexLiteral>> tuples) { // 参数：元组列表
    this(cluster, traitSet, Collections.emptyList(), rowType, tuples); // 调用另一个构造方法，传入空提示列表
  } // 构造方法结束

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public LogicalValues( // 废弃的构造方法
      RelOptCluster cluster, // 参数：关系表达式集群
      RelDataType rowType, // 参数：行类型
      ImmutableList<ImmutableList<RexLiteral>> tuples) { // 参数：元组列表
    this(cluster, cluster.traitSetOf(Convention.NONE), rowType, tuples); // 调用另一个构造方法，自动创建特征集合
  } // 构造方法结束

  /**
   * Creates a LogicalValues by parsing serialized output. // 通过解析序列化输出来创建LogicalValues
   */ // 从RelInput创建的构造方法文档注释
  public LogicalValues(RelInput input) { // 构造方法声明，接受RelInput参数
    super(input); // 调用父类的构造方法，从序列化输入初始化
  } // 构造方法结束

  /** Creates a LogicalValues. */ // 创建LogicalValues的静态工厂方法
  public static LogicalValues create(RelOptCluster cluster, // 静态方法声明
      final RelDataType rowType, // 参数：行类型
      final ImmutableList<ImmutableList<RexLiteral>> tuples) { // 参数：元组列表
    final RelMetadataQuery mq = cluster.getMetadataQuery(); // 获取元数据查询对象
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE) // 创建特征集合，使用NONE约定
        .replaceIfs(RelCollationTraitDef.INSTANCE, // 如果满足条件则替换排序特征
            () -> RelMdCollation.values(mq, rowType, tuples)) // 使用RelMdCollation计算排序特征
        .replaceIf(RelDistributionTraitDef.INSTANCE, // 如果满足条件则替换分布特征
            () -> RelMdDistribution.values(rowType, tuples)); // 使用RelMdDistribution计算分布特征
    return new LogicalValues(cluster, traitSet, rowType, tuples); // 创建并返回LogicalValues实例
  } // 静态方法结束

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，用于复制关系表达式
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言特征集合包含NONE约定（如果适用）
    assert inputs.isEmpty(); // 断言输入列表为空（Values节点没有输入）
    return new LogicalValues(getCluster(), traitSet, getRowType(), tuples); // 创建并返回新的LogicalValues副本
  } // 方法结束

  /** Creates a LogicalValues that outputs no rows of a given row type. */ // 创建一个输出零行的LogicalValues
  public static LogicalValues createEmpty(RelOptCluster cluster, // 静态方法声明
      RelDataType rowType) { // 参数：行类型
    return create(cluster, rowType, // 调用create方法，传入空的元组列表
        ImmutableList.of()); // 返回包含零行的LogicalValues
  } // 静态方法结束

  /** Creates a LogicalValues that outputs one row and one column. */ // 创建一个输出一行一列的LogicalValues
  public static LogicalValues createOneRow(RelOptCluster cluster) { // 静态方法声明
    final RelDataType rowType = // 声明行类型变量
        cluster.getTypeFactory().builder() // 获取类型工厂并创建构建器
            .add("ZERO", SqlTypeName.INTEGER).nullable(false) // 添加名为"ZERO"的整数类型列，不可为空
            .build(); // 构建行类型
    final ImmutableList<ImmutableList<RexLiteral>> tuples = // 声明元组列表变量
        ImmutableList.of( // 创建不可变列表
            ImmutableList.of( // 创建包含一个元组的不可变列表
                cluster.getRexBuilder().makeExactLiteral(BigDecimal.ZERO, // 创建精确的字面量0
                    rowType.getFieldList().get(0).getType()))); // 使用行类型中第一个字段的类型
    return create(cluster, rowType, tuples); // 调用create方法并返回LogicalValues实例
  } // 静态方法结束

  @Override public RelNode accept(RelShuttle shuttle) { // 重写accept方法，接受访问者模式
    return shuttle.visit(this); // 调用访问者的visit方法，传入当前节点
  } // 方法结束

  @Override public RelNode withHints(List<RelHint> hintList) { // 重写withHints方法，返回带有新提示的节点
    return new LogicalValues(getCluster(), traitSet, hintList, getRowType(), tuples); // 创建并返回带有新提示的LogicalValues
  } // 方法结束
} // 类定义结束
