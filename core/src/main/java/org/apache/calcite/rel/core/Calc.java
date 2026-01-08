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
package org.apache.calcite.rel.core; // 包声明：Calc类位于org.apache.calcite.rel.core包中

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster：关系表达式集群，包含类型工厂和表达式构建器
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost：关系表达式的成本模型接口
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner：关系表达式优化器接口
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil：关系表达式工具类，提供各种实用方法
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet：关系表达式特征集合，如约定、排序等
import org.apache.calcite.rel.RelCollation; // 导入RelCollation：排序规范，描述字段的排序顺序
import org.apache.calcite.rel.RelNode; // 导入RelNode：关系表达式接口，代表关系代数操作
import org.apache.calcite.rel.RelWriter; // 导入RelWriter：用于将关系表达式输出为可读格式
import org.apache.calcite.rel.SingleRel; // 导入SingleRel：只有一个子节点的关系表达式基类
import org.apache.calcite.rel.hint.Hintable; // 导入Hintable：支持提示（hint）的接口
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint：关系表达式提示，用于指导优化器
import org.apache.calcite.rel.metadata.RelMdUtil; // 导入RelMdUtil：关系表达式元数据工具类
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery：元数据查询接口
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType：关系数据类型，描述表的结构
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder：行表达式构建器，用于创建各种表达式
import org.apache.calcite.rex.RexLocalRef; // 导入RexLocalRef：局部引用，引用程序中的表达式
import org.apache.calcite.rex.RexNode; // 导入RexNode：行表达式节点接口，所有表达式的基类
import org.apache.calcite.rex.RexOver; // 导入RexOver：窗口函数表达式，如SUM() OVER()
import org.apache.calcite.rex.RexProgram; // 导入RexProgram：行表达式程序，包含表达式列表、投影和条件
import org.apache.calcite.rex.RexProgramBuilder; // 导入RexProgramBuilder：行表达式程序构建器
import org.apache.calcite.rex.RexShuttle; // 导入RexShuttle：行表达式访问器，用于遍历和修改表达式
import org.apache.calcite.rex.RexUtil; // 导入RexUtil：行表达式工具类
import org.apache.calcite.util.Litmus; // 导入Litmus：验证级别枚举，用于控制验证严格程度
import org.apache.calcite.util.Util; // 导入Util：通用工具类

import com.google.common.collect.ImmutableList; // 导入ImmutableList：Google Guava的不可变列表实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable：可空注解，标记可能为null的值

import java.util.List; // 导入List：Java标准库的列表接口

/**
 * Calc类是LogicalCalc的抽象基类，用于表示计算操作
 * Calc是Calcite中最重要的关系表达式之一，它将投影（Projection）、过滤（Filter）和计算（Calculation）合并到一个操作中
 * 一个Calc包含一个RexProgram，该程序定义了如何将输入行转换为输出行
 * RexProgram包含三个主要部分：
 * 1. 表达式列表：程序中使用的所有表达式的集合，每个表达式都有一个索引
 * 2. 投影列表：定义输出行包含哪些字段，通过引用表达式列表中的索引
 * 3. 条件：可选的过滤条件，也是一个对表达式列表的引用
 * 
 * Calc的作用：
 * - 将多个操作（投影、过滤、计算）合并为一个，减少中间结果
 * - 支持复杂的表达式计算，包括算术运算、逻辑运算、函数调用等
 * - 支持窗口函数（OVER子句）
 * - 可以被优化器重写和转换
 * 
 * 典型使用场景：
 * - SQL中的SELECT子句（投影）
 * - SQL中的WHERE子句（过滤）
 * - SQL中的CASE WHEN表达式（条件计算）
 * - SQL中的窗口函数
 * 
 * Calc的子类包括：
 * - LogicalCalc：逻辑层面的Calc，用于逻辑优化阶段
 * - EnumerableCalc：可枚举的Calc，用于物理执行阶段
 * - 其他特定实现的Calc
 */
public abstract class Calc extends SingleRel implements Hintable { // Calc是抽象类，继承SingleRel（单子节点），实现Hintable（支持提示）
  //~ Instance fields -------------------------------------------------------- // 成员变量部分标记

  protected final ImmutableList<RelHint> hints; // hints字段：存储此Calc关联的所有提示（hints），提示用于指导优化器的决策，例如强制使用索引、提示数据分布等。使用ImmutableList保证不可变性，避免意外修改。

  protected final RexProgram program; // program字段：核心成员，存储RexProgram对象。RexProgram定义了Calc的计算逻辑，包括：1）表达式列表（exprList）：程序中所有表达式的集合；2）投影列表（projectList）：定义输出字段；3）条件（condition）：可选的过滤条件。RexProgram是Calc的核心，它将输入行转换为输出行。

  //~ Constructors ----------------------------------------------------------- // 构造方法部分标记
  /**
   * 创建一个Calc实例，这是主要构造方法
   * 构造过程：
   * 1. 调用父类SingleRel的构造方法，传递cluster、traits和child
   * 2. 从program中提取输出行类型，设置到rowType字段（继承自父类）
   * 3. 保存program和hints到成员变量
   * 4. 验证Calc的有效性，如果验证失败会抛出异常
   *
   * 参数说明：
   * @param cluster 关系表达式集群，包含类型工厂、表达式构建器等共享资源
   * @param traits 特征集合，定义此Calc的物理和逻辑特征，如约定（Convention）、排序（Collation）等
   * @param hints 提示列表，用于指导优化器的决策，如强制使用特定索引、提示数据分布等
   * @param child 输入关系表达式，Calc只有一个子节点，即输入数据源
   * @param program RexProgram对象，定义Calc的计算逻辑，包括表达式、投影和条件
   */
  @SuppressWarnings("method.invocation.invalid") // 抑制警告：方法调用无效（调用父类构造方法时的警告）
  protected Calc(
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traits, // 参数：特征集合
      List<RelHint> hints, // 参数：提示列表
      RelNode child, // 参数：输入关系表达式
      RexProgram program) { // 参数：RexProgram程序
    super(cluster, traits, child); // 调用父类SingleRel的构造方法，初始化集群、特征和子节点
    this.rowType = program.getOutputRowType(); // 设置输出行类型，从program中获取输出行的结构信息
    this.program = program; // 保存program到成员变量
    this.hints = ImmutableList.copyOf(hints); // 将hints转换为不可变列表并保存，确保线程安全和不可变性
    assert isValid(Litmus.THROW, null); // 验证Calc的有效性，使用THROW级别，验证失败时抛出异常
  }

  @Deprecated // 标记为已废弃，将在2.0版本之前移除，建议使用带hints参数的构造方法
  protected Calc(
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traits, // 参数：特征集合
      RelNode child, // 参数：输入关系表达式
      RexProgram program) { // 参数：RexProgram程序
    this(cluster, traits, ImmutableList.of(), child, program); // 调用主构造方法，传入空的hints列表
  }

  @Deprecated // 标记为已废弃，将在2.0版本之前移除，collationList参数不再使用
  protected Calc(
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traits, // 参数：特征集合
      RelNode child, // 参数：输入关系表达式
      RexProgram program, // 参数：RexProgram程序
      List<RelCollation> collationList) { // 参数：排序列表（已废弃，不再使用）
    this(cluster, traits, ImmutableList.of(), child, program); // 调用主构造方法，传入空的hints列表
    Util.discard(collationList); // 丢弃collationList参数，显式表示不使用此参数
  }

  //~ Methods ---------------------------------------------------------------- // 方法部分标记

  @Override public final Calc copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写父类的copy方法，使用final修饰，子类不能重写
    return copy(traitSet, sole(inputs), program); // 从inputs列表中获取唯一的子节点，然后调用抽象的copy方法，保持program不变
  }

  /**
   * 创建此Calc的副本，这是核心的抽象方法，子类必须实现
   * 此方法用于在优化过程中创建Calc的新实例，可能修改特征集、子节点或程序
   * 典型使用场景：
   * 1. 改变特征集：例如从LogicalConvention转为EnumerableConvention
   * 2. 改变子节点：例如替换输入的表或视图
   * 3. 改变程序：例如重写表达式、优化条件等
   *
   * 实现约定：
   * - 如果所有参数都与当前Calc相同，可以返回this
   * - 如果有任何参数不同，必须返回新的Calc实例
   * - 新实例应该是深拷贝，不应共享可变状态
   *
   * @param traitSet 新的特征集合，可能包含不同的约定、排序等
   * @param child 新的输入关系表达式
   * @param program 新的RexProgram程序，定义新的计算逻辑
   * @return 新的Calc实例，如果参数都相同则返回this
   *
   * @see #copy(org.apache.calcite.plan.RelTraitSet, java.util.List) 相关的copy方法重载
   */
  public abstract Calc copy(
      RelTraitSet traitSet, // 参数：新的特征集合
      RelNode child, // 参数：新的输入关系表达式
      RexProgram program); // 参数：新的RexProgram程序

  @Deprecated // 标记为已废弃，将在2.0版本之前移除，collationList参数不再使用
  public Calc copy(
      RelTraitSet traitSet, // 参数：新的特征集合
      RelNode child, // 参数：新的输入关系表达式
      RexProgram program, // 参数：新的RexProgram程序
      List<RelCollation> collationList) { // 参数：排序列表（已废弃）
    Util.discard(collationList); // 丢弃collationList参数，显式表示不使用此参数
    return copy(traitSet, child, program); // 调用三参数的copy方法
  }

  /** 检查此Calc是否包含任何窗口聚合函数（OVER子句） */
  public final boolean containsOver() { // 方法：检查是否包含窗口函数
    return RexOver.containsOver(program); // 委托给RexOver工具类，检查program中是否包含RexOver表达式
  }

  @Override public boolean isValid(Litmus litmus, @Nullable Context context) { // 重写父类的isValid方法，验证Calc的有效性
    if (!RelOptUtil.equal( // 检查program的输入类型是否与子节点的输出类型匹配
        "program's input type", // 描述：program的输入类型
        program.getInputRowType(), // program的输入行类型
        "child's output type", // 描述：子节点的输出类型
        getInput().getRowType(), litmus)) { // 子节点的输出行类型，以及验证级别
      return litmus.fail(null); // 如果类型不匹配，验证失败
    }
    if (!program.isValid(litmus, context)) { // 验证program本身的有效性
      return litmus.fail(null); // 如果program无效，验证失败
    }
    if (!program.isNormalized(litmus, getCluster().getRexBuilder())) { // 验证program是否已规范化
      return litmus.fail(null); // 如果program未规范化，验证失败
    }
    if (RexUtil.M2V_FINDER.inProgram(program)) { // 检查program中是否包含多值引用（M2V）
      return litmus.fail("program contains M2V"); // 如果包含M2V，验证失败，M2V是已废弃的特性
    }
    return litmus.succeed(); // 所有检查都通过，验证成功
  }

  public RexProgram getProgram() { // 获取Calc的RexProgram
    return program; // 返回program成员变量
  }

  @Override public ImmutableList<RelHint> getHints() { // 实现Hintable接口，获取提示列表
    return hints; // 返回hints成员变量
  }

  @Override public double estimateRowCount(RelMetadataQuery mq) { // 重写父类的estimateRowCount方法，估算输出行数
    return RelMdUtil.estimateFilteredRows(getInput(), program, mq); // 使用RelMdUtil工具类，基于子节点行数和program中的过滤条件估算输出行数
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写父类的computeSelfCost方法，计算Calc自身的成本
      RelMetadataQuery mq) { // 参数：元数据查询对象，用于获取各种元数据
    double dRows = mq.getRowCount(this); // 获取此Calc的输出行数
    double dCpu = mq.getRowCount(getInput()) // CPU成本：输入行数乘以表达式数量，表示需要计算的表达式总数
        * program.getExprCount(); // program中的表达式数量
    double dIo = 0; // IO成本：Calc是内存计算操作，不产生IO，所以IO成本为0
    return planner.getCostFactory().makeCost(dRows, dCpu, dIo); // 使用成本工厂创建成本对象，包含行数、CPU和IO三个维度
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写父类的explainTerms方法，输出Calc的详细信息
    return program.explainCalc(super.explainTerms(pw)); // 先调用父类方法输出基本信息，然后让program添加详细的计算逻辑信息
  }

  @Override public RelNode accept(RexShuttle shuttle) { // 重写父类的accept方法，接受RexShuttle访问器来遍历和修改表达式
    List<RexNode> oldExprs = program.getExprList(); // 获取原始表达式列表
    List<RexNode> exprs = shuttle.apply(oldExprs); // 使用shuttle访问并可能修改所有表达式
    List<RexLocalRef> oldProjects = program.getProjectList(); // 获取原始投影列表
    List<RexLocalRef> projects = shuttle.apply(oldProjects); // 使用shuttle访问并可能修改投影引用
    RexLocalRef oldCondition = program.getCondition(); // 获取原始条件
    RexNode condition; // 声明新的条件变量
    if (oldCondition != null) { // 如果存在条件
      condition = shuttle.apply(oldCondition); // 使用shuttle访问并可能修改条件
      assert condition instanceof RexLocalRef // 断言：条件必须是RexLocalRef类型
          : "Invalid condition after rewrite. Expected RexLocalRef, got " // 错误消息
          + condition; // 实际得到的类型
    } else { // 如果不存在条件
      condition = null; // 条件保持为null
    }
    if (exprs == oldExprs // 检查所有内容是否都没有改变
        && projects == oldProjects // 检查投影列表是否未改变
        && condition == oldCondition) { // 检查条件是否未改变
      return this; // 如果都没有改变，返回this，避免创建新对象
    }

    final RexBuilder rexBuilder = getCluster().getRexBuilder(); // 获取表达式构建器
    final RelDataType rowType = // 创建新的行类型
        RexUtil.createStructType( // 使用RexUtil创建结构类型
            rexBuilder.getTypeFactory(), // 类型工厂
            projects, // 投影列表
            getRowType().getFieldNames(), // 字段名称
            null); // 可选的nullability信息
    final RexProgram newProgram = // 创建新的RexProgram
        RexProgramBuilder.create( // 使用RexProgramBuilder创建程序
            rexBuilder, program.getInputRowType(), exprs, projects, // 表达式构建器、输入类型、表达式列表、投影列表
            condition, rowType, true, null) // 条件、输出类型、规范化标志、可选标志
        .getProgram(false); // 获取程序，false表示不进行验证
    return copy(traitSet, getInput(), newProgram); // 使用新的program创建Calc的副本
  }
}
