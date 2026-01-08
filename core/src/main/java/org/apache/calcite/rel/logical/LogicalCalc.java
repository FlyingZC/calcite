/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看随此工作分发的NOTICE文件
 * this work for additional information regarding copyright ownership.  // 获取关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache许可证2.0版授权给您
 * (the "License"); you may not use this file except in compliance with // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下网址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS, // 根据许可证分发的软件是按"原样"基础分发的
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不含任何明示或暗示的保证或条件
 * See the License for the specific language governing permissions and // 查看许可证以了解特定语言的权限和
 * limitations under the License. // 限制
 */
package org.apache.calcite.rel.logical; // 声明包名，LogicalCalc类属于org.apache.calcite.rel.logical包

import org.apache.calcite.plan.Convention; // 导入Convention类，定义关系表达式的调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式集群
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil工具类，提供关系表达式优化相关的实用方法
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类，表示排序规则
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef类，定义排序特征
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入RelDistributionTraitDef类，定义分布特征
import org.apache.calcite.rel.RelInput; // 导入RelInput类，用于从序列化输入创建关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式节点
import org.apache.calcite.rel.RelShuttle; // 导入RelShuttle接口，用于遍历关系表达式树
import org.apache.calcite.rel.core.Calc; // 导入Calc基类，LogicalCalc继承自Calc
import org.apache.calcite.rel.core.CorrelationId; // 导入CorrelationId类，表示相关子查询的标识符
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint类，表示关系表达式的提示信息
import org.apache.calcite.rel.metadata.RelMdCollation; // 导入RelMdCollation类，提供排序元数据
import org.apache.calcite.rel.metadata.RelMdDistribution; // 导入RelMdDistribution类，提供分布元数据
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询元数据
import org.apache.calcite.rel.rules.FilterToCalcRule; // 导入FilterToCalcRule类，将LogicalFilter转换为LogicalCalc的规则
import org.apache.calcite.rel.rules.ProjectToCalcRule; // 导入ProjectToCalcRule类，将LogicalProject转换为LogicalCalc的规则
import org.apache.calcite.rex.RexProgram; // 导入RexProgram类，表示行表达式程序，包含投影和过滤逻辑
import org.apache.calcite.util.Util; // 导入Util工具类，提供通用实用方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，用于创建不可变列表

import java.util.List; // 导入Java的List接口
import java.util.Set; // 导入Java的Set接口

/**
 * A relational expression which computes project expressions and also filters. // 一个关系表达式，用于计算投影表达式并执行过滤操作
 *
 * <p>This relational expression combines the functionality of // 此关系表达式结合了LogicalProject和LogicalFilter的功能
 * {@link LogicalProject} and {@link LogicalFilter}. // 它合并了投影和过滤两种操作
 * It should be created in the later // 它应该在优化的后期阶段创建，通过合并
 * stages of optimization, by merging consecutive {@link LogicalProject} and // 连续的LogicalProject和LogicalFilter节点
 * {@link LogicalFilter} nodes together. // 来实现
 *
 * <p>The following rules relate to <code>LogicalCalc</code>: // 以下规则与LogicalCalc相关：
 *
 * <ul>
 * <li>{@link FilterToCalcRule} creates this from a {@link LogicalFilter} // FilterToCalcRule从LogicalFilter创建LogicalCalc
 * <li>{@link ProjectToCalcRule} creates this from a {@link LogicalProject} // ProjectToCalcRule从LogicalProject创建LogicalCalc
 * <li>{@link org.apache.calcite.rel.rules.FilterCalcMergeRule} // FilterCalcMergeRule将LogicalFilter与LogicalCalc合并
 *     merges this with a {@link LogicalFilter} // 合并此节点与LogicalFilter
 * <li>{@link org.apache.calcite.rel.rules.ProjectCalcMergeRule} // ProjectCalcMergeRule将LogicalProject与LogicalCalc合并
 *     merges this with a {@link LogicalProject} // 合并此节点与LogicalProject
 * <li>{@link org.apache.calcite.rel.rules.CalcMergeRule} // CalcMergeRule合并两个LogicalCalc节点
 *     merges two {@code LogicalCalc}s // 合并两个LogicalCalc节点
 * </ul>
 */
public final class LogicalCalc extends Calc { // LogicalCalc类声明，继承自Calc基类，使用final修饰表示不可被继承
  //~ Static fields/initializers --------------------------------------------- // 静态字段/初始化器区域分隔符

  //~ Constructors ----------------------------------------------------------- // 构造方法区域分隔符

  /** Creates a LogicalCalc. */ // 创建LogicalCalc实例的构造方法
  public LogicalCalc( // 构造方法声明，用于创建LogicalCalc对象
      RelOptCluster cluster, // 参数：关系表达式集群，包含共享的优化环境和元数据
      RelTraitSet traitSet, // 参数：特征集合，定义关系表达式的物理属性（如排序、分布等）
      List<RelHint> hints, // 参数：提示列表，包含优化器提示信息
      RelNode child, // 参数：子节点，表示输入的关系表达式
      RexProgram program) { // 参数：行表达式程序，定义投影和过滤逻辑
    super(cluster, traitSet, hints, child, program); // 调用父类Calc的构造方法，初始化基类字段
  }

  @Deprecated // to be removed before 2.0 // 标记为已过时，将在2.0版本前移除
  public LogicalCalc( // 构造方法声明，已过时的版本
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      RelNode child, // 参数：子节点
      RexProgram program) { // 参数：行表达式程序
    this(cluster, traitSet, ImmutableList.of(), child, program); // 调用主构造方法，hints参数设为空列表
  }

  /**
   * Creates a LogicalCalc by parsing serialized output. // 通过解析序列化输出来创建LogicalCalc
   */
  public LogicalCalc(RelInput input) { // 构造方法声明，从RelInput对象创建LogicalCalc
    this(input.getCluster(), // 调用主构造方法，从RelInput获取集群信息
        input.getTraitSet(), // 从RelInput获取特征集合
        ImmutableList.of(), // hints设为空列表
        input.getInput(), // 从RelInput获取输入节点
        RexProgram.create(input)); // 从RelInput创建RexProgram
  }

  @Deprecated // to be removed before 2.0 // 标记为已过时，将在2.0版本前移除
  public LogicalCalc( // 构造方法声明，已过时的版本
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      RelNode child, // 参数：子节点
      RexProgram program, // 参数：行表达式程序
      List<RelCollation> collationList) { // 参数：排序规则列表（已废弃）
    this(cluster, traitSet, ImmutableList.of(), child, program); // 调用主构造方法，忽略collationList参数
    Util.discard(collationList); // 丢弃collationList参数，避免编译器警告
  }

  public static LogicalCalc create(final RelNode input, // 静态工厂方法，创建LogicalCalc实例
      final RexProgram program) { // 参数：输入关系表达式和行表达式程序
    final RelOptCluster cluster = input.getCluster(); // 从输入节点获取关系表达式集群
    final RelMetadataQuery mq = cluster.getMetadataQuery(); // 获取元数据查询对象，用于查询元数据
    final RelTraitSet traitSet = cluster.traitSet() // 获取集群的特征集合
        .replace(Convention.NONE) // 替换调用约定为NONE，表示逻辑层
        .replaceIfs(RelCollationTraitDef.INSTANCE, // 如果存在排序特征，则替换为计算出的排序
            () -> RelMdCollation.calc(mq, input, program)) // 使用RelMdCollation.calc计算排序特征
        .replaceIf(RelDistributionTraitDef.INSTANCE, // 如果存在分布特征，则替换为计算出的分布
            () -> RelMdDistribution.calc(mq, input, program)); // 使用RelMdDistribution.calc计算分布特征
    return new LogicalCalc(cluster, traitSet, ImmutableList.of(), input, program); // 创建并返回新的LogicalCalc实例
  }

  //~ Methods ---------------------------------------------------------------- // 方法区域分隔符

  @Override public LogicalCalc copy(RelTraitSet traitSet, RelNode child, // 重写copy方法，用于复制LogicalCalc节点
      RexProgram program) { // 参数：新的特征集合、新的子节点、新的行表达式程序
    return new LogicalCalc(getCluster(), traitSet, hints, child, program); // 创建并返回新的LogicalCalc实例，保持相同的集群和提示
  }

  @Override public void collectVariablesUsed(Set<CorrelationId> variableSet) { // 重写collectVariablesUsed方法，收集使用的相关变量
    final RelOptUtil.VariableUsedVisitor vuv = // 创建变量使用访问者，用于遍历表达式
        new RelOptUtil.VariableUsedVisitor(null); // 初始化访问者，不指定起始变量
    vuv.visitEach(program.getExprList()); // 访问RexProgram中的所有表达式，收集使用的变量
    variableSet.addAll(vuv.variables); // 将收集到的变量添加到传入的变量集合中
  }

  @Override public RelNode withHints(List<RelHint> hintList) { // 重写withHints方法，创建带有新提示的节点
    return new LogicalCalc(getCluster(), traitSet, // 创建新的LogicalCalc实例，使用相同的集群和特征集合
        ImmutableList.copyOf(hintList), input, program); // 使用新的提示列表，保持相同的输入和程序
  }

  @Override public RelNode accept(RelShuttle shuttle) { // 重写accept方法，接受RelShuttle访问者
    return shuttle.visit(this); // 将访问者传递给RelShuttle的visit方法，返回访问后的节点
  }
}
