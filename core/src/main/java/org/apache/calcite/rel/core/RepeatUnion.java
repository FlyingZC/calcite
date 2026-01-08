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
package org.apache.calcite.rel.core; // 定义包名，该类属于 Calcite 核心关系表达式包

import org.apache.calcite.linq4j.function.Experimental; // 导入实验性功能注解，标记当前 API 为实验性质
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群类，用于管理关系表达式和元数据
import org.apache.calcite.plan.RelOptTable; // 导入关系优化表类，表示表在优化器中的抽象
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合类，定义关系表达式的物理属性
import org.apache.calcite.rel.BiRel; // 导入双输入关系表达式基类，RepeatUnion 继承此类
import org.apache.calcite.rel.RelNode; // 导入关系表达式接口，表示关系代数中的操作
import org.apache.calcite.rel.RelWriter; // 导入关系表达式写入器，用于生成可读的解释信息
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入关系元数据查询接口，用于获取统计信息
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型类，描述关系表达式的行类型
import org.apache.calcite.schema.TransientTable; // 导入临时表接口，用于存储中间结果
import org.apache.calcite.util.Util; // 导入工具类，提供通用工具方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，标记可能为 null 的字段

import java.util.List; // 导入列表接口，用于存储输入行类型

import static java.util.Objects.requireNonNull; // 导入静态方法，用于非空检查

/**
 * Relational expression that computes a repeat union (recursive union in SQL
 * terminology).
 * 关系表达式，用于计算重复联合（在 SQL 术语中称为递归联合）。
 *
 * <p>This operation is executed as follows:
 * 该操作的执行过程如下：
 *
 * <ul>
 * <li>Evaluate the left input (i.e., seed relational expression) once.  For
 *   UNION (but not UNION ALL), discard duplicated rows.
 * 评估左输入（即种子关系表达式）一次。对于 UNION（但不是 UNION ALL），丢弃重复行。
 *
 * <li>Evaluate the right input (i.e., iterative relational expression) over and
 *   over until it produces no more results (or until an optional maximum number
 *   of iterations is reached). For UNION (but not UNION ALL), discard
 *   duplicated results.
 * 反复评估右输入（即迭代关系表达式），直到不再产生结果（或达到可选的最大迭代次数）。对于 UNION（但不是 UNION ALL），丢弃重复结果。
 * </ul>
 *
 * <p>NOTE: The current API is experimental and subject to change without
 * notice.
 * 注意：当前 API 是实验性的，可能会在无通知的情况下更改。
 */
@Experimental // 标记该类为实验性 API，可能会在未来版本中更改
public abstract class RepeatUnion extends BiRel { // 定义抽象类 RepeatUnion，继承自双输入关系表达式 BiRel

  /**
   * Whether duplicates are considered.
   * 是否考虑重复项。如果为 true，表示保留重复项（UNION ALL）；如果为 false，表示去除重复项（UNION）。
   */
  public final boolean all; // 定义公共常量成员变量 all，表示是否保留重复行

  /**
   * Maximum number of times to repeat the iterative relational expression;
   * negative value means no limit, 0 means only seed will be evaluated.
   * 重复迭代关系表达式的最大次数；负值表示无限制，0 表示只评估种子部分。
   */
  public final int iterationLimit; // 定义公共常量成员变量 iterationLimit，表示最大迭代次数限制

  /**
   * Transient table where repeat union's intermediate results will be stored (optional).
   * 用于存储重复联合中间结果的临时表（可选）。如果不为 null，表示使用临时表来存储中间结果。
   */
  protected final @Nullable RelOptTable transientTable; // 定义受保护的最终成员变量 transientTable，表示可选的临时表

  //~ Constructors -----------------------------------------------------------
  // 构造器部分分隔符注释

  protected RepeatUnion(RelOptCluster cluster, RelTraitSet traitSet, // 定义受保护的构造方法，接收集群、特征集、种子和迭代关系等参数
      RelNode seed, RelNode iterative, boolean all, int iterationLimit, // 参数包括种子关系表达式 seed、迭代关系表达式 iterative、是否保留重复 all、迭代限制 iterationLimit
      @Nullable RelOptTable transientTable) { // 参数还包括可选的临时表 transientTable
    super(cluster, traitSet, seed, iterative); // 调用父类 BiRel 的构造方法，传入集群、特征集和两个输入关系表达式
    this.iterationLimit = iterationLimit; // 初始化成员变量 iterationLimit，设置最大迭代次数限制
    this.all = all; // 初始化成员变量 all，设置是否保留重复行
    this.transientTable = transientTable; // 初始化成员变量 transientTable，设置临时表引用
    if (transientTable != null) { // 如果临时表不为 null
      requireNonNull(transientTable.unwrap(TransientTable.class)); // 验证临时表可以解包为 TransientTable 类型，确保类型正确
    }
  }

  @Override public double estimateRowCount(RelMetadataQuery mq) { // 重写估计行数方法，接收元数据查询对象作为参数
    // TODO implement a more accurate row count?
    // TODO：实现更精确的行数估计？
    double seedRowCount = mq.getRowCount(getSeedRel()); // 使用元数据查询获取种子关系表达式的行数估计
    if (iterationLimit == 0) { // 如果迭代限制为 0，表示只执行种子部分
      return seedRowCount; // 直接返回种子部分的行数估计
    }
    return seedRowCount // 否则返回种子行数加上迭代部分行数的估计值
        + mq.getRowCount(getIterativeRel()) * (iterationLimit < 0 ? 10 : iterationLimit); // 迭代部分行数乘以迭代次数，如果无限制则默认乘以 10
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写解释项方法，接收关系写入器对象作为参数
    super.explainTerms(pw); // 调用父类的解释项方法，先输出父类的解释信息
    if (iterationLimit >= 0) { // 如果迭代限制为非负数（有限制）
      pw.item("iterationLimit", iterationLimit); // 向关系写入器添加迭代限制项
    }
    return pw.item("all", all); // 向关系写入器添加 all 项并返回，用于解释是否保留重复行
  }

  public RelNode getSeedRel() { // 定义公共方法 getSeedRel，用于获取种子关系表达式
    return left; // 返回左输入关系表达式（即种子部分）
  }

  public RelNode getIterativeRel() { // 定义公共方法 getIterativeRel，用于获取迭代关系表达式
    return right; // 返回右输入关系表达式（即迭代部分）
  }

  public @Nullable RelOptTable getTransientTable() { // 定义公共方法 getTransientTable，用于获取临时表
    return transientTable; // 返回临时表引用，可能为 null
  }

  @Override protected RelDataType deriveRowType() { // 重写派生行类型方法，用于计算当前关系表达式的行类型
    final List<RelDataType> inputRowTypes = // 定义输入行类型列表，存储所有输入关系表达式的行类型
        Util.transform(getInputs(), RelNode::getRowType); // 使用工具类转换所有输入关系表达式，提取它们的行类型
    final RelDataType rowType = // 定义行类型变量，存储计算出的兼容行类型
        getCluster().getTypeFactory().leastRestrictive(inputRowTypes); // 使用类型工厂计算输入行类型的最小限制类型（最通用的兼容类型）
    if (rowType == null) { // 如果无法计算出兼容的行类型
      throw new IllegalArgumentException("Cannot compute compatible row type " // 抛出非法参数异常，提示无法计算兼容的行类型
          + "for arguments: " // 异常消息包含参数信息
          + Util.sepList(inputRowTypes, ", ")); // 列出所有输入行类型，用逗号分隔
    }
    return rowType; // 返回计算出的兼容行类型
  }
}
