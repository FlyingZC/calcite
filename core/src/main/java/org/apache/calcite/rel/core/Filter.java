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
package org.apache.calcite.rel.core; // 定义包名,该包包含Calcite的核心关系表达式类

import org.apache.calcite.plan.RelOptCluster; // 导入关系表达式集群类,用于管理关系表达式
import org.apache.calcite.plan.RelOptCost; // 导入关系优化成本类,用于计算查询计划的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入关系优化规划器类,用于执行查询优化
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合类,定义关系表达式的物理属性
import org.apache.calcite.rel.RelInput; // 导入关系输入类,用于从序列化数据创建关系表达式
import org.apache.calcite.rel.RelNode; // 导入关系节点接口,所有关系表达式的基类
import org.apache.calcite.rel.RelWriter; // 导入关系写入器接口,用于输出关系表达式的描述信息
import org.apache.calcite.rel.SingleRel; // 导入单输入关系表达式基类,Filter继承自此类
import org.apache.calcite.rel.hint.Hintable; // 导入可提示接口,允许关系表达式接收优化提示
import org.apache.calcite.rel.hint.RelHint; // 导入关系提示类,用于存储优化提示信息
import org.apache.calcite.rel.metadata.RelMdUtil; // 导入关系元数据工具类,提供元数据计算方法
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入关系元数据查询类,用于查询关系表达式的元数据
import org.apache.calcite.rex.RexChecker; // 导入行表达式检查器类,用于验证行表达式的正确性
import org.apache.calcite.rex.RexNode; // 导入行表达式节点接口,表示行表达式
import org.apache.calcite.rex.RexOver; // 导入行表达式窗口函数类,表示窗口聚合函数
import org.apache.calcite.rex.RexProgram; // 导入行表达式程序类,表示一组行表达式
import org.apache.calcite.rex.RexShuttle; // 导入行表达式穿梭器类,用于遍历和修改行表达式
import org.apache.calcite.rex.RexUtil; // 导入行表达式工具类,提供行表达式操作方法
import org.apache.calcite.util.Litmus; // 导入断言工具类,用于验证条件

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.apiguardian.api.API; // 导入API注解类,用于标记API的稳定性
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf; // 导入非空条件注解,用于空值检查
import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解,标记可空类型

import java.util.List; // 导入Java列表接口
import java.util.Objects; // 导入Java对象工具类,提供对象操作方法

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法,用于非空检查

/**
 * Relational expression that iterates over its input // 关系表达式,用于遍历其输入
 * and returns elements for which <code>condition</code> evaluates to // 并返回condition评估为true的元素
 * <code>true</code>. // 即过滤满足条件的行
 *
 * <p>If the condition allows nulls, then a null value is treated the same as // 如果条件允许null值,则null值被视为
 * false. // false,即不满足条件
 *
 * @see org.apache.calcite.rel.logical.LogicalFilter // 参见逻辑过滤器实现类
 */
public abstract class Filter extends SingleRel implements Hintable { // Filter抽象类,继承自SingleRel(单输入关系表达式),实现Hintable接口(支持优化提示)
  //~ Instance fields -------------------------------------------------------- // 实例字段部分

  protected final RexNode condition; // 过滤条件,是一个行表达式(RexNode),用于判断每一行是否应该被保留

  protected final ImmutableList<RelHint> hints; // 优化提示列表,不可变列表,包含应用于此Filter节点的优化提示

  //~ Constructors ----------------------------------------------------------- // 构造函数部分

  /**
   * Creates a filter. // 创建一个过滤器
   *
   * @param cluster   Cluster that this relational expression belongs to // 关系表达式所属的集群,包含类型工厂等共享资源
   * @param traits    the traits of this rel // 关系表达式的特征集合,定义物理属性如排序、分布等
   * @param hints     Hints for this node // 应用于此节点的优化提示列表
   * @param child     input relational expression // 输入的关系表达式,即要过滤的数据源
   * @param condition boolean expression which determines whether a row is // 布尔表达式,决定一行是否被允许通过
   *                  allowed to pass // 即过滤条件,评估为true时保留该行
   */
  @SuppressWarnings("method.invocation.invalid") // 抑制方法调用无效的警告
  protected Filter( // 受保护的构造函数,用于创建Filter实例
      RelOptCluster cluster, // 参数:关系表达式集群
      RelTraitSet traits, // 参数:特征集合
      List<RelHint> hints, // 参数:优化提示列表
      RelNode child, // 参数:输入关系节点
      RexNode condition) { // 参数:过滤条件
    super(cluster, traits, child); // 调用父类SingleRel的构造函数,初始化集群、特征和输入
    this.condition = requireNonNull(condition, "condition"); // 初始化过滤条件,要求condition非空,否则抛出NullPointerException
    assert RexUtil.isFlat(condition) : "RexUtil.isFlat should be true for condition " + condition; // 断言:条件必须是扁平的(不包含嵌套的AND/OR),否则抛出异常
    assert isValid(Litmus.THROW, null); // 断言:验证Filter节点是否有效,如果无效则抛出异常
    this.hints = ImmutableList.copyOf(hints); // 初始化优化提示列表,创建不可变副本
  }

  /**
   * Creates a filter. // 创建一个过滤器(不带优化提示的简化版本)
   *
   * @param cluster   Cluster that this relational expression belongs to // 关系表达式所属的集群
   * @param traits    the traits of this rel // 关系表达式的特征集合
   * @param child     input relational expression // 输入的关系表达式
   * @param condition boolean expression which determines whether a row is // 布尔表达式,决定一行是否被允许通过
   *                  allowed to pass // 即过滤条件
   */
  protected Filter( // 受保护的构造函数,创建不带优化提示的Filter
      RelOptCluster cluster, // 参数:关系表达式集群
      RelTraitSet traits, // 参数:特征集合
      RelNode child, // 参数:输入关系节点
      RexNode condition) { // 参数:过滤条件
    this(cluster, traits, ImmutableList.of(), child, condition); // 调用完整构造函数,传入空的优化提示列表
  }

  /**
   * Creates a Filter by parsing serialized output. // 通过解析序列化输出创建Filter(用于从JSON等格式反序列化)
   */
  protected Filter(RelInput input) { // 受保护的构造函数,从RelInput对象创建Filter
    this(input.getCluster(), input.getTraitSet(), input.getInput(), // 调用完整构造函数,从RelInput中提取集群、特征和输入
        requireNonNull(input.getExpression("condition"), "condition")); // 从RelInput中提取condition表达式,要求非空
  }

  //~ Methods ---------------------------------------------------------------- // 方法部分

  @Override public final RelNode copy(RelTraitSet traitSet, // 重写copy方法,复制关系表达式
      List<RelNode> inputs) { // 参数:新的输入关系节点列表
    return copy(traitSet, sole(inputs), getCondition()); // 调用抽象copy方法,传入新特征、唯一输入和当前条件
  }

  public abstract Filter copy(RelTraitSet traitSet, RelNode input, // 抽象方法,创建Filter副本,由子类实现具体逻辑
      RexNode condition); // 参数:新特征、新输入节点、新过滤条件,返回新的Filter实例

  @Override public RelNode accept(RexShuttle shuttle) { // 重写accept方法,接受行表达式穿梭器来修改表达式
    RexNode condition = shuttle.apply(this.condition); // 应用穿梭器到条件表达式,可能产生新的条件表达式
    if (this.condition == condition) { // 如果条件表达式未被修改(引用相同)
      return this; // 返回当前对象,无需创建新实例
    }
    return copy(traitSet, getInput(), condition); // 否则创建新的Filter副本,使用修改后的条件
  }

  public RexNode getCondition() { // 获取过滤条件的方法
    return condition; // 返回当前Filter的过滤条件
  }

  /** Returns whether this Filter contains any windowed-aggregate functions. */ // 返回此Filter是否包含任何窗口聚合函数
  public final boolean containsOver() { // 检查是否包含窗口函数的方法
    return RexOver.containsOver(condition); // 使用RexOver工具类检查条件中是否包含窗口函数(OVER子句)
  }

  @Override public boolean isValid(Litmus litmus, @Nullable Context context) { // 重写isValid方法,验证Filter节点是否有效
    if (RexUtil.isNullabilityCast(getCluster().getTypeFactory(), condition)) { // 检查条件是否包含仅用于修改可空性的类型转换
      return litmus.fail("Cast for just nullability not allowed"); // 如果存在,返回失败,不允许仅修改可空性的转换
    }
    final RexChecker checker = // 创建行表达式检查器
        new RexChecker(getInput().getRowType(), context, litmus); // 使用输入行类型、上下文和断言工具初始化检查器
    condition.accept(checker); // 让条件表达式接受检查器,验证表达式是否有效
    if (checker.getFailureCount() > 0) { // 如果检查器发现任何错误
      return litmus.fail(null); // 返回失败
    }
    return litmus.succeed(); // 验证通过,返回成功
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法,计算Filter自身的成本
      RelMetadataQuery mq) { // 参数:优化规划器和元数据查询
    double dRows = mq.getRowCount(this); // 获取Filter输出的行数(过滤后的行数)
    double dCpu = mq.getRowCount(getInput()); // CPU成本等于输入行数(每行都需要评估条件)
    double dIo = 0; // IO成本为0(Filter不产生额外的I/O操作)
    return planner.getCostFactory().makeCost(dRows, dCpu, dIo); // 创建并返回成本对象(行数、CPU、IO)
  }

  @Override public double estimateRowCount(RelMetadataQuery mq) { // 重写estimateRowCount方法,估算Filter输出的行数
    return RelMdUtil.estimateFilteredRows(getInput(), condition, mq); // 使用元数据工具估算过滤后的行数
  }

  @Deprecated // to be removed before 2.0 // 已弃用,将在2.0版本前移除
  public static double estimateFilteredRows(RelNode child, RexProgram program) { // 静态方法,估算过滤后的行数(使用RexProgram)
    final RelMetadataQuery mq = child.getCluster().getMetadataQuery(); // 获取子节点的元数据查询对象
    return RelMdUtil.estimateFilteredRows(child, program, mq); // 使用元数据工具估算过滤行数
  }

  @Deprecated // to be removed before 2.0 // 已弃用,将在2.0版本前移除
  public static double estimateFilteredRows(RelNode child, RexNode condition) { // 静态方法,估算过滤后的行数(使用RexNode条件)
    final RelMetadataQuery mq = child.getCluster().getMetadataQuery(); // 获取子节点的元数据查询对象
    return RelMdUtil.estimateFilteredRows(child, condition, mq); // 使用元数据工具估算过滤行数
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写explainTerms方法,输出Filter的描述信息
    return super.explainTerms(pw) // 调用父类的explainTerms方法
        .item("condition", condition); // 添加条件表达式到输出中
  }

  @API(since = "1.24", status = API.Status.INTERNAL) // API注解:自1.24版本起,状态为内部API
  @EnsuresNonNullIf(expression = "#1", result = true) // 注解:如果方法返回true,则参数#1非空
  protected boolean deepEquals0(@Nullable Object obj) { // 深度比较方法,比较两个Filter是否相等
    if (this == obj) { // 如果是同一个对象
      return true; // 返回true
    }
    if (obj == null || getClass() != obj.getClass()) { // 如果obj为null或类类型不同
      return false; // 返回false
    }
    Filter o = (Filter) obj; // 将obj转换为Filter类型
    return traitSet.equals(o.traitSet) // 比较特征集合是否相等
        && hints.equals(o.hints) // 比较优化提示列表是否相等
        && input.deepEquals(o.input) // 比较输入节点是否深度相等
        && condition.equals(o.condition) // 比较条件表达式是否相等
        && getRowType().equalsSansFieldNames(o.getRowType()); // 比较行类型是否相等(忽略字段名)
  }

  @API(since = "1.24", status = API.Status.INTERNAL) // API注解:自1.24版本起,状态为内部API
  protected int deepHashCode0() { // 深度哈希码方法,计算Filter的哈希码
    return Objects.hash(traitSet, hints, input.deepHashCode(), condition); // 使用Objects.hash计算组合哈希码
  }

  @Override public ImmutableList<RelHint> getHints() { // 重写getHints方法,获取优化提示列表
    return hints; // 返回Filter的优化提示列表
  }
}
