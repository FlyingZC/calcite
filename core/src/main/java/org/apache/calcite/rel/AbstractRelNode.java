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
package org.apache.calcite.rel; // 声明包名，该类属于org.apache.calcite.rel包，是Calcite关系代数表达式的核心包

import org.apache.calcite.plan.Convention; // 导入Convention接口，定义关系表达式的调用约定（如逻辑、物理等）
import org.apache.calcite.plan.ConventionTraitDef; // 导入ConventionTraitDef，定义Convention特征的trait定义
import org.apache.calcite.plan.RelDigest; // 导入RelDigest，用于唯一标识关系表达式节点的摘要信息
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster，关系表达式优化集群，包含共享资源如类型工厂
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost，表示关系表达式执行成本的接口
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner，关系表达式优化器接口
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable，表示优化器中的表
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil，提供关系表达式优化工具方法
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet，关系表达式特征集合（如排序、分区等）
import org.apache.calcite.rel.core.CorrelationId; // 导入CorrelationId，表示相关变量的唯一标识符
import org.apache.calcite.rel.hint.Hintable; // 导入Hintable接口，支持提示的关系表达式
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint，表示关系表达式提示
import org.apache.calcite.rel.metadata.Metadata; // 导入Metadata，元数据接口
import org.apache.calcite.rel.metadata.MetadataFactory; // 导入MetadataFactory，元数据工厂，用于创建元数据查询
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery，元数据查询对象
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType，表示关系数据类型（行类型）
import org.apache.calcite.rex.RexShuttle; // 导入RexShuttle，用于遍历和修改行表达式的访问器
import org.apache.calcite.runtime.PairList; // 导入PairList，键值对列表，用于存储多个键值对
import org.apache.calcite.sql.SqlExplainLevel; // 导入SqlExplainLevel，定义SQL解释的详细级别
import org.apache.calcite.util.Litmus; // 导入Litmus，用于验证和断言的工具类
import org.apache.calcite.util.Pair; // 导入Pair，键值对类型
import org.apache.calcite.util.Util; // 导入Util，Calcite通用工具类

import com.google.common.collect.ImmutableSet; // 导入Google Guava的不可变集合类

import org.apiguardian.api.API; // 导入API注解，用于标记API的稳定性和版本
import org.checkerframework.checker.initialization.qual.UnknownInitialization; // 导入CheckerFramework注解，标记对象未初始化状态
import org.checkerframework.checker.nullness.qual.MonotonicNonNull; // 导入CheckerFramework注解，表示字段单调非空（初始化后不再变为null）
import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework注解，表示字段可为null
import org.checkerframework.dataflow.qual.Pure; // 导入CheckerFramework注解，标记纯函数（无副作用）

import java.util.ArrayList; // 导入Java ArrayList，动态数组实现
import java.util.Collections; // 导入Java Collections，集合工具类
import java.util.List; // 导入Java List，列表接口
import java.util.Map; // 导入Java Map，映射接口
import java.util.Set; // 导入Java Set，集合接口
import java.util.concurrent.atomic.AtomicInteger; // 导入Java AtomicInteger，原子整数，用于线程安全的计数

import static com.google.common.base.Preconditions.checkNotNull; // 导入Google Guava的前置条件检查方法

import static java.util.Objects.requireNonNull; // 导入Java Objects的requireNonNull方法

/**
 * Base class for every relational expression ({@link RelNode}).
 * 所有关系表达式（RelNode）的抽象基类
 * 
 * 这个类是Calcite关系代数表达式的核心基类，提供了所有关系表达式节点的通用功能：
 * 1. 唯一标识符管理：为每个节点分配唯一的ID用于调试和跟踪
 * 2. 特征集合管理：管理关系表达式的特征（如调用约定、排序等）
 * 3. 行类型管理：缓存和派生关系表达式的输出行类型
 * 4. 摘要计算：生成用于比较和缓存的节点摘要
 * 5. 优化器集成：支持注册到优化器和成本估算
 * 6. 访问者模式：支持RelVisitor和RelShuttle遍历
 * 7. 元数据查询：提供统一的元数据查询接口
 * 
 * 具体的关系表达式（如LogicalProject、LogicalJoin等）都继承自这个类
 */
public abstract class AbstractRelNode implements RelNode { // 定义抽象类，实现RelNode接口，所有关系表达式节点的基类
  //~ Static fields/initializers --------------------------------------------- // 静态字段/初始化器分隔符

  /** Generator for {@link #id} values. */
  private static final AtomicInteger NEXT_ID = new AtomicInteger(0); // 原子整数计数器，用于为每个RelNode生成唯一的ID，线程安全

  //~ Instance fields -------------------------------------------------------- // 实例字段分隔符

  /**
   * Cached type of this relational expression.
   * 缓存此关系表达式的行类型（输出数据类型）
   */
  protected @MonotonicNonNull RelDataType rowType; // 行类型缓存，表示此关系表达式输出的行结构（列名、列类型），@MonotonicNonNull表示初始化后不会变为null

  /**
   * The digest that uniquely identifies the node.
   * 唯一标识此节点的摘要信息，用于缓存和比较
   */
  @API(since = "1.24", status = API.Status.INTERNAL) // API注解：自1.24版本引入，状态为内部API
  protected final RelDigest digest; // 关系表达式摘要，包含节点特征和属性的字符串表示，用于唯一标识和比较节点

  private final RelOptCluster cluster; // 关系优化集群，包含类型工厂、表达式工厂等共享资源，是整个查询计划的上下文

  /** Unique id of this object, for debugging. */
  protected final int id; // 此对象的唯一ID，用于调试和日志，全局递增，帮助识别和跟踪关系表达式节点

  /** RelTraitSet that describes the traits of this RelNode. */
  protected final RelTraitSet traitSet; // 特征集合，描述此关系表达式的物理和逻辑特征（如调用约定、排序方式、分区策略等）

  //~ Constructors ----------------------------------------------------------- // 构造函数分隔符

  /**
   * Creates an <code>AbstractRelNode</code>.
   * 创建AbstractRelNode实例
   * 
   * @param cluster 关系优化集群，提供类型工厂等共享资源
   * @param traitSet 特征集合，描述此节点的物理和逻辑特征
   */
  protected AbstractRelNode(RelOptCluster cluster, RelTraitSet traitSet) { // 受保护的构造函数，只能由子类调用，初始化关系表达式节点
    super(); // 调用父类Object的构造函数
    this.cluster = requireNonNull(cluster, "cluster"); // 设置集群，检查cluster不为null，否则抛出NullPointerException
    this.traitSet = requireNonNull(traitSet, "traitSet"); // 设置特征集合，检查traitSet不为null
    this.id = NEXT_ID.getAndIncrement(); // 原子递增并获取唯一ID，确保线程安全
    this.digest = new InnerRelDigest(); // 创建内部摘要对象，用于节点的唯一标识和比较
  }

  //~ Methods ---------------------------------------------------------------- // 方法分隔符

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制关系表达式，可以改变特征集合和输入
    // Note that empty set equals empty set, so relational expressions
    // with zero inputs do not generally need to implement their own copy
    // method.
    // 注意：空集合等于空集合，因此没有输入的关系表达式通常不需要实现自己的copy方法
    if (getInputs().equals(inputs) // 如果输入列表相同
        && traitSet == getTraitSet()) { // 且特征集合相同（使用==比较，因为是同一个对象）
      return this; // 返回当前对象，无需复制
    }
    throw new AssertionError("Relational expression should override copy. " // 抛出断言错误，要求子类重写copy方法
        + "Class=[" + getClass() // 显示当前类名
        + "]; traits=[" + getTraitSet() // 显示当前特征集合
        + "]; desired traits=[" + traitSet // 显示期望的特征集合
        + "]");
  }

  protected static <T> T sole(List<T> collection) { // 静态辅助方法，从集合中获取唯一元素
    assert collection.size() == 1; // 断言集合大小为1，确保只有一个元素
    return collection.get(0); // 返回集合中的第一个（也是唯一）元素
  }

  @Override public final RelOptCluster getCluster() { // 获取关系优化集群
    return cluster; // 返回此节点所属的优化集群，包含类型工厂等共享资源
  }

  @Pure // 标记为纯函数，无副作用
  @Override public final @Nullable Convention getConvention( // 获取调用约定（Convention）
      @UnknownInitialization AbstractRelNode this) { // @UnknownInitialization表示对象可能未完全初始化
    return traitSet == null ? null : traitSet.getTrait(ConventionTraitDef.INSTANCE); // 从特征集合中获取Convention，如果traitSet为null则返回null
  }

  @Override public RelTraitSet getTraitSet() { // 获取特征集合
    return traitSet; // 返回此关系表达式的特征集合，包含所有物理和逻辑特征
  }

  @Override public @Nullable String getCorrelVariable() { // 获取相关变量名称
    return null; // 默认返回null，表示不使用相关变量，子类可以重写
  }

  @Override public int getId() { // 获取节点唯一ID
    return id; // 返回此节点的唯一标识符，用于调试和跟踪
  }

  @Override public RelNode getInput(int i) { // 获取指定索引的输入节点
    List<RelNode> inputs = getInputs(); // 获取所有输入节点列表
    return inputs.get(i); // 返回指定索引的输入节点，如果索引越界会抛出IndexOutOfBoundsException
  }

  @Override public void register(RelOptPlanner planner) { // 将此节点注册到优化器
    Util.discard(planner); // 丢弃planner参数，默认不做任何操作，子类可以重写以实现自定义注册逻辑
  }

  // It is not recommended to override this method, but sub-classes can do it at their own risk.
  // 不建议重写此方法，但子类可以自行承担风险
  @Override public String getRelTypeName() { // 获取关系表达式类型名称
    String cn = getClass().getName(); // 获取类的全限定名
    int i = cn.length(); // 获取类名长度
    while (--i >= 0) { // 从后向前遍历类名字符串
      if (cn.charAt(i) == '$' || cn.charAt(i) == '.') { // 如果遇到$或.分隔符
        return cn.substring(i + 1); // 返回分隔符后的部分（简单类名）
      }
    }
    return cn; // 如果没有分隔符，返回完整类名
  }

  @Override public boolean isValid(Litmus litmus, @Nullable Context context) { // 验证此关系表达式是否有效
    return litmus.succeed(); // 默认返回成功，子类可以重写以实现自定义验证逻辑
  }

  @Override public final RelDataType getRowType() { // 获取行类型（输出数据类型）
    if (rowType == null) { // 如果行类型尚未缓存
      rowType = checkNotNull(deriveRowType(), "null row type for %s", this); // 调用deriveRowType派生行类型，并检查不为null
    }
    return rowType; // 返回缓存的行类型
  }

  protected RelDataType deriveRowType() { // 派生行类型，由子类实现
    // This method is only called if rowType is null, so you don't NEED to
    // implement it if rowType is always set.
    // 此方法仅在rowType为null时调用，因此如果rowType总是被设置，则不需要实现此方法
    throw new UnsupportedOperationException(); // 默认抛出不支持操作异常，子类必须重写以提供行类型派生逻辑
  }

  @Override public RelDataType getExpectedInputRowType(int ordinalInParent) { // 获取期望的输入行类型
    return getRowType(); // 默认返回自己的行类型，子类可以重写以指定不同的输入类型要求
  }

  @Override public List<RelNode> getInputs() { // 获取所有输入节点
    return Collections.emptyList(); // 默认返回空列表，表示没有输入，子类可以重写以返回实际的输入列表
  }

  @Override public double estimateRowCount(RelMetadataQuery mq) { // 估算行数
    return 1.0; // 默认返回1.0，子类应该重写以提供更准确的行数估算
  }

  @Override public Set<CorrelationId> getVariablesSet() { // 获取此节点设置的相关变量集合
    return ImmutableSet.of(); // 默认返回空集合，表示不设置任何相关变量，子类可以重写
  }

  @Override public void collectVariablesUsed(Set<CorrelationId> variableSet) { // 收集此节点使用的相关变量
    // for default case, nothing to do
    // 默认情况下不做任何操作，子类可以重写以收集实际使用的相关变量
  }

  @Override public boolean isEnforcer() { // 判断此节点是否为强制节点（用于强制执行某些特征）
    return false; // 默认返回false，表示不是强制节点，子类可以重写
  }

  @Override public void collectVariablesSet(Set<CorrelationId> variableSet) { // 收集此节点设置的相关变量到给定集合
    // 默认不做任何操作，子类可以重写以收集实际设置的相关变量
  }

  @Override public void childrenAccept(RelVisitor visitor) { // 接受访问者访问所有子节点
    List<RelNode> inputs = getInputs(); // 获取所有输入节点
    for (int i = 0; i < inputs.size(); i++) { // 遍历所有输入节点
      visitor.visit(inputs.get(i), i, this); // 让访问者访问每个子节点，传入子节点、索引和父节点
    }
  }

  @Override public RelNode accept(RelShuttle shuttle) { // 接受关系表达式访问者
    // Call fall-back method. Specific logical types (such as LogicalProject
    // and LogicalJoin) have their own RelShuttle.visit methods.
    // 调用回退方法。特定的逻辑类型（如LogicalProject和LogicalJoin）有自己的RelShuttle.visit方法
    return shuttle.visit(this); // 让访问者访问当前节点，访问者会根据具体类型调用相应的visit方法
  }

  @Override public RelNode accept(RexShuttle shuttle) { // 接受行表达式访问者
    return this; // 默认返回this，表示不修改，子类可以重写以遍历和修改内部的行表达式
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 计算此节点的执行成本
      RelMetadataQuery mq) { // 元数据查询对象
    // by default, assume cost is proportional to number of rows
    // 默认假设成本与行数成正比
    double rowCount = mq.getRowCount(this); // 从元数据查询获取行数
    return planner.getCostFactory().makeCost(rowCount, rowCount, 0); // 创建成本对象：CPU成本=rowCount，IO成本=rowCount，内存成本=0
  }

  @Deprecated // to be removed before 2.0 // 标记为已弃用，将在2.0版本前移除
  @Override public final <@Nullable M extends @Nullable Metadata> M metadata(Class<M> metadataClass, // 查询元数据
      RelMetadataQuery mq) { // 元数据查询对象
    final MetadataFactory factory = cluster.getMetadataFactory(); // 从集群获取元数据工厂
    final M metadata = factory.query(this, mq, metadataClass); // 使用工厂查询指定的元数据类型
    checkNotNull(metadata, "no provider found (rel=%s, m=%s); " // 检查元数据不为null
        + "a backstop provider is recommended", this, metadataClass); // 如果为null，抛出异常并建议添加后备提供者
    // Usually the metadata belongs to the rel that created it. RelSubset and
    // HepRelVertex are notable exceptions, so disable the assertion. It's not
    // worth the performance hit to override this method for them.
    // 通常元数据属于创建它的rel。RelSubset和HepRelVertex是明显的例外，所以禁用断言。
    // 为它们重写此方法的性能代价不值得。
    //   assert metadata.rel() == this : "someone else's metadata";
    return metadata; // 返回查询到的元数据对象
  }

  @Override public void explain(RelWriter pw) { // 解释此关系表达式，生成可读的描述
    explainTerms(pw).done(this); // 调用explainTerms获取描述项，然后调用done完成解释
  }

  /**
   * Describes the inputs and attributes of this relational expression.
   * 描述此关系表达式的输入和属性
   * Each node should call {@code super.explainTerms}, then call the
   * {@link org.apache.calcite.rel.externalize.RelWriterImpl#input(String, RelNode)}
   * and
   * {@link RelWriter#item(String, Object)}
   * methods for each input and attribute.
   * 每个节点应该调用super.explainTerms，然后为每个输入和属性调用input和item方法
   *
   * @param pw Plan writer // 计划写入器，用于输出解释信息
   * @return Plan writer for fluent-explain pattern // 返回计划写入器以支持流式解释模式
   */
  public RelWriter explainTerms(RelWriter pw) { // 解释此节点的术语（输入和属性）
    return pw; // 默认直接返回写入器，子类应该重写以添加具体的输入和属性描述
  }

  @Override public RelNode onRegister(RelOptPlanner planner) { // 注册后的回调方法
    List<RelNode> oldInputs = getInputs(); // 获取原始输入列表
    List<RelNode> inputs = new ArrayList<>(oldInputs.size()); // 创建新的输入列表
    for (final RelNode input : oldInputs) { // 遍历所有原始输入
      RelNode e = planner.ensureRegistered(input, null); // 确保输入已注册，返回注册后的节点
      assert e == input || RelOptUtil.equal("rowtype of rel before registration", // 断言：如果节点改变，行类型应该相同
          input.getRowType(), // 注册前的行类型
          "rowtype of rel after registration", // 注册后的行类型
          e.getRowType(), // 断言失败时抛出异常
          Litmus.THROW);
      inputs.add(e); // 添加注册后的节点到新列表
    }
    RelNode r = this; // 初始化结果为当前节点
    if (!Util.equalShallow(oldInputs, inputs)) { // 如果输入列表发生改变
      r = copy(getTraitSet(), inputs); // 复制节点并使用新的输入
    }
    r.recomputeDigest(); // 重新计算节点的摘要
    assert r.isValid(Litmus.THROW, null); // 断言节点有效
    return r; // 返回处理后的节点
  }

  @Override public void recomputeDigest() { // 重新计算节点的摘要
    digest.clear(); // 清除缓存的摘要，下次访问时会重新计算
  }

  @Override public void replaceInput( // 替换指定索引的输入节点
      int ordinalInParent, // 输入索引
      RelNode p) { // 新的输入节点
    throw new UnsupportedOperationException("replaceInput called on " + this); // 默认抛出不支持操作异常，子类可以重写
  }

  /** Description; consists of id plus digest. */
  // 描述：由ID和摘要组成
  @Override public String toString() { // 转换为字符串表示
    return "rel#" + id + ':' + getDigest(); // 返回格式为"rel#id:digest"的字符串
  }

  @Deprecated // to be removed before 2.0 // 标记为已弃用，将在2.0版本前移除
  @Override public final String getDescription() { // 获取描述信息
    return this.toString(); // 返回toString的结果，已弃用，建议直接使用toString
  }

  @Override public String getDigest() { // 获取摘要字符串
    return digest.toString(); // 返回摘要对象的字符串表示
  }

  @Override public final RelDigest getRelDigest() { // 获取摘要对象
    return digest; // 返回RelDigest对象，用于比较和缓存
  }

  @Override public @Nullable RelOptTable getTable() { // 获取关联的表
    return null; // 默认返回null，表示不直接关联表，子类可以重写以返回实际的表
  }

  /**
   * {@inheritDoc}
   * 继承自父类
   *
   * <p>This method (and {@link #hashCode} is intentionally final. We do not want
   * sub-classes of {@link RelNode} to redefine identity. Various algorithms
   * (e.g. visitors, planner) can define the identity as meets their needs.
   * 此方法（和hashCode）故意设为final。我们不希望RelNode的子类重新定义标识。
   * 各种算法（如访问者、优化器）可以根据需要定义标识。
   */
  @Override public final boolean equals(@Nullable Object obj) { // 相等性判断，final方法不可重写
    return super.equals(obj); // 使用Object的equals方法（基于对象引用比较）
  }

  /**
   * {@inheritDoc}
   * 继承自父类
   *
   * <p>This method (and {@link #equals} is intentionally final. We do not want
   * sub-classes of {@link RelNode} to redefine identity. Various algorithms
   * (e.g. visitors, planner) can define the identity as meets their needs.
   * 此方法（和equals）故意设为final。我们不希望RelNode的子类重新定义标识。
   * 各种算法（如访问者、优化器）可以根据需要定义标识。
   */
  @Override public final int hashCode() { // 哈希码计算，final方法不可重写
    return super.hashCode(); // 使用Object的hashCode方法（基于对象地址）
  }

  /**
   * Equality check for RelNode digest.
   * RelNode摘要的相等性检查
   *
   * <p>By default this method collects digest attributes from
   * {@link #explainTerms(RelWriter)}, then compares each attribute pair.
   * This should work well for most cases. If this method is a performance
   * bottleneck for your project, or the default behavior can't handle
   * your scenario properly, you can choose to override this method and
   * {@link #deepHashCode()}. See {@code LogicalJoin} as an example.
   * 默认情况下，此方法从explainTerms收集摘要属性，然后比较每个属性对。
   * 这对大多数情况应该工作良好。如果此方法成为项目的性能瓶颈，
   * 或者默认行为无法正确处理您的场景，您可以选择重写此方法和deepHashCode。
   * 参见LogicalJoin作为示例。
   *
   * @return Whether the 2 RelNodes are equivalent or have the same digest.
   * 返回两个RelNode是否等价或具有相同的摘要
   * @see #deepHashCode() // 参见deepHashCode方法
   */
  @API(since = "1.25", status = API.Status.MAINTAINED) // API注解：自1.25版本引入，状态为维护中
  @Override public boolean deepEquals(@Nullable Object obj) { // 深度相等性检查，比较节点的语义等价性
    if (this == obj) { // 如果是同一个对象
      return true; // 返回true
    }
    if (obj == null || this.getClass() != obj.getClass()) { // 如果obj为null或类型不同
      return false; // 返回false
    }
    AbstractRelNode that = (AbstractRelNode) obj; // 强制转换为AbstractRelNode
    boolean result = this.getTraitSet().equals(that.getTraitSet()) // 比较特征集合是否相等
        && this.getRowType().equalsSansFieldNames(that.getRowType()); // 比较行类型是否相等（忽略字段名）
    if (!result) { // 如果特征集合或行类型不相等
      return false; // 返回false
    }
    PairList<String, @Nullable Object> items1 = this.getDigestItems(); // 获取此节点的摘要项列表
    PairList<String, @Nullable Object> items2 = that.getDigestItems(); // 获取目标节点的摘要项列表
    if (items1.size() != items2.size()) { // 如果摘要项数量不同
      return false; // 返回false
    }
    for (int i = 0; result && i < items1.size(); i++) { // 遍历所有摘要项
      Map.Entry<String, @Nullable Object> attr1 = items1.get(i); // 获取此节点的第i个属性
      Map.Entry<String, @Nullable Object> attr2 = items2.get(i); // 获取目标节点的第i个属性
      if (attr1.getValue() instanceof RelNode) { // 如果属性值是RelNode
        result = ((RelNode) attr1.getValue()).deepEquals(attr2.getValue()); // 递归调用deepEquals比较子节点
      } else { // 如果属性值是普通对象
        result = attr1.equals(attr2); // 使用equals比较
      }
    }
    return result; // 返回比较结果
  }

  /**
   * Compute hash code for RelNode digest.
   * 计算RelNode摘要的哈希码
   *
   * @see RelNode#deepEquals(Object) // 参见deepEquals方法
   */
  @API(since = "1.25", status = API.Status.MAINTAINED) // API注解：自1.25版本引入，状态为维护中
  @Override public int deepHashCode() { // 计算深度哈希码，与deepEquals对应
    int result = 31 + getTraitSet().hashCode(); // 初始值为31加上特征集合的哈希码
    PairList<String, @Nullable Object> items = this.getDigestItems(); // 获取摘要项列表
    for (@Nullable Object value : items.rightList()) { // 遍历所有摘要项的值
      final int h; // 声明哈希码变量
      if (value == null) { // 如果值为null
        h = 0; // 哈希码为0
      } else if (value instanceof RelNode) { // 如果值是RelNode
        h = ((RelNode) value).deepHashCode(); // 递归调用deepHashCode
      } else { // 如果值是普通对象
        h = value.hashCode(); // 调用对象的hashCode方法
      }
      result = result * 31 + h; // 使用31作为乘数计算组合哈希码
    }
    return result; // 返回最终哈希码
  }

  private PairList<String, @Nullable Object> getDigestItems() { // 获取摘要项列表，用于计算摘要和哈希码
    RelDigestWriter rdw = new RelDigestWriter(); // 创建摘要写入器
    explainTerms(rdw); // 调用explainTerms填充摘要项
    if (this instanceof Hintable) { // 如果此节点实现了Hintable接口
      List<RelHint> hints = ((Hintable) this).getHints(); // 获取提示列表
      rdw.itemIf("hints", hints, !hints.isEmpty()); // 如果提示列表非空，添加到摘要项
    }
    return rdw.attrs; // 返回摘要项列表
  }

  /** Implementation of {@link RelDigest}. */
// RelDigest接口的实现，用于表示关系表达式的摘要
private class InnerRelDigest implements RelDigest { // 内部类，实现RelDigest接口，管理节点的摘要信息
  /** Cached hash code. */
  // 缓存的哈希码
  private int hash = 0; // 摘要的哈希码缓存，避免重复计算

  @Override public RelNode getRel() { // 获取关联的关系表达式节点
    return AbstractRelNode.this; // 返回外部类的实例（即当前节点）
  }

  @Override public void clear() { // 清除缓存的摘要
    hash = 0; // 重置哈希码为0，下次访问时会重新计算
  }

  @Override public boolean equals(final @Nullable Object o) { // 比较两个摘要是否相等
    if (this == o) { // 如果是同一个对象
      return true; // 返回true
    }
    if (o == null || getClass() != o.getClass()) { // 如果o为null或类型不同
      return false; // 返回false
    }
    final InnerRelDigest relDigest = (InnerRelDigest) o; // 强制转换为InnerRelDigest
    return deepEquals(relDigest.getRel()); // 使用deepEquals比较关联的节点
  }

  @Override public int hashCode() { // 获取摘要的哈希码
    if (hash == 0) { // 如果哈希码尚未计算
      hash = deepHashCode(); // 调用deepHashCode计算并缓存
    }
    return hash; // 返回缓存的哈希码
  }

  @Override public String toString() { // 转换为字符串表示
    RelDigestWriter rdw = new RelDigestWriter(); // 创建摘要写入器
    explain(rdw); // 调用explain方法生成摘要
    return requireNonNull(rdw.digest, "digest"); // 返回摘要字符串，确保不为null
  }
}

  /**
   * A writer object used exclusively for computing the digest of a RelNode.
   * 专门用于计算RelNode摘要的写入器对象
   *
   * <p>The writer is meant to be used only for computing a single digest and
   * then thrown away.  After calling {@link #done(RelNode)} the writer should
   * be used only to obtain the computed {@link #digest}. Any other action is
   * prohibited.
   * 此写入器仅用于计算单个摘要，然后被丢弃。调用done(RelNode)后，
   * 写入器应该仅用于获取计算出的digest。禁止任何其他操作。
   */
private static final class RelDigestWriter implements RelWriter { // 静态内部类，实现RelWriter接口，专门用于计算摘要
  private final PairList<String, @Nullable Object> attrs = PairList.of(); // 摘要属性列表，存储键值对

  @Nullable String digest = null; // 计算出的摘要字符串，初始为null

  @Override public void explain(final RelNode rel, // 解释关系表达式，此方法不应被调用
      final List<Pair<String, @Nullable Object>> valueList) { // 值列表参数
    throw new IllegalStateException("Should not be called for computing digest"); // 抛出异常，因为此方法不应在计算摘要时被调用
  }

  @Override public SqlExplainLevel getDetailLevel() { // 获取详细级别
    return SqlExplainLevel.DIGEST_ATTRIBUTES; // 返回摘要属性级别，表示用于计算摘要
  }

  @Override public RelWriter item(String term, @Nullable Object value) { // 添加一个摘要项
    if (value != null && value.getClass().isArray()) { // 如果值不为null且是数组
      // We can't call hashCode and equals on Array, so
      // convert it to String to keep the same behaviour.
      // 我们不能对数组调用hashCode和equals，所以将其转换为字符串以保持相同的行为
      value = "" + value; // 将数组转换为字符串
    }
    attrs.add(term, value); // 添加键值对到属性列表
    return this; // 返回this以支持链式调用
  }

  @Override public RelWriter done(RelNode node) { // 完成摘要计算
    StringBuilder sb = new StringBuilder(); // 创建字符串构建器
    sb.append(node.getRelTypeName()); // 添加节点类型名
    sb.append('.'); // 添加分隔符
    sb.append(node.getTraitSet()); // 添加特征集合
    sb.append('('); // 添加左括号
    attrs.forEachIndexed((j, left, right) -> { // 遍历所有属性
      if (j > 0) { // 如果不是第一个属性
        sb.append(','); // 添加逗号分隔符
      }
      sb.append(left); // 添加属性名
      sb.append('='); // 添加等号
      if (right instanceof RelNode) { // 如果属性值是RelNode
        RelNode input = (RelNode) right; // 强制转换为RelNode
        sb.append(input.getRelTypeName()); // 添加节点类型名
        sb.append('#'); // 添加#分隔符
        sb.append(input.getId()); // 添加节点ID
      } else { // 如果属性值是普通对象
        sb.append(right); // 直接添加值的字符串表示
      }
    });
    sb.append(')'); // 添加右括号
    digest = sb.toString(); // 将构建的字符串赋值给digest
    return this; // 返回this
  }
} // 结束RelDigestWriter内部类
} // 结束AbstractRelNode类
