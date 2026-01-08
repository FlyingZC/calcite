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
 */ // Apache许可证声明,说明代码的开源许可协议和使用限制
package org.apache.calcite.plan; // 声明包名,表示这个类属于org.apache.calcite.plan包,该包包含Calcite优化器相关的核心类

import org.apache.calcite.plan.volcano.RelSubset; // 导入RelSubset类,表示关系表达式等价集合,在火山优化器中用于管理等价的RelNode
import org.apache.calcite.rel.RelNode; // 导入RelNode接口,表示关系代数节点,是Calcite中所有关系操作的抽象表示(如Scan、Filter、Join等)
import org.apache.calcite.rel.metadata.RelMetadataProvider; // 导入元数据提供者接口,用于提供关系表达式的元数据信息(如行数、大小等)
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入元数据查询类,用于查询关系表达式的元数据信息
import org.apache.calcite.rex.RexExecutor; // 导入RexExecutor类,用于执行表达式(RexNode)的求值和简化操作
import org.apache.calcite.sql2rel.RelDecorrelator; // 导入RelDecorrelator类,用于去除子查询中的相关性,将相关子查询转换为非相关子查询
import org.apache.calcite.util.CancelFlag; // 导入取消标志类,用于在长时间运行的优化过程中支持取消操作
import org.apache.calcite.util.Pair; // 导入Pair工具类,用于存储键值对
import org.apache.calcite.util.Util; // 导入Util工具类,提供各种静态工具方法
import org.apache.calcite.util.trace.CalciteTrace; // 导入CalciteTrace工具类,用于获取Calcite的日志记录器

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类,用于创建不可修改的列表

import org.checkerframework.checker.initialization.qual.UnknownInitialization; // 导入CheckerFramework注解,表示对象可能处于未初始化状态
import org.checkerframework.checker.nullness.qual.MonotonicNonNull; // 导入CheckerFramework注解,表示字段从null变为非null后不再变回null
import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework注解,表示字段或返回值可能为null
import org.checkerframework.dataflow.qual.Pure; // 导入CheckerFramework注解,表示方法不修改对象状态
import org.slf4j.Logger; // 导入SLF4J日志接口,用于日志记录

import java.text.NumberFormat; // 导入数字格式化类,用于格式化数字输出
import java.util.ArrayList; // 导入ArrayList动态数组类
import java.util.HashMap; // 导入HashMap哈希映射类
import java.util.HashSet; // 导入HashSet哈希集合类
import java.util.LinkedHashMap; // 导入LinkedHashMap有序哈希映射类,保持插入顺序
import java.util.List; // 导入List接口
import java.util.Locale; // 导入Locale类,用于本地化格式化
import java.util.Map; // 导入Map接口
import java.util.Set; // 导入Set接口
import java.util.concurrent.atomic.AtomicBoolean; // 导入原子布尔类,支持线程安全的布尔操作
import java.util.regex.Pattern; // 导入正则表达式模式类

import static org.apache.calcite.util.Static.RESOURCE; // 导入静态资源类,用于获取本地化资源

import static java.util.Objects.requireNonNull; // 导入Objects工具类的requireNonNull方法,用于参数非空检查

/**
 * Abstract base for implementations of the {@link RelOptPlanner} interface.
 */ // 类注释:这是RelOptPlanner接口的抽象基类实现,为所有优化器提供公共功能
public abstract class AbstractRelOptPlanner implements RelOptPlanner { // 声明抽象类AbstractRelOptPlanner,实现RelOptPlanner接口,作为所有优化器的基类
  //~ Static fields/initializers --------------------------------------------- // 静态字段/初始化器区域分隔符

  /** Logger for rule attempts information. */ // 注释:用于记录规则尝试信息的日志记录器
  private static final Logger RULE_ATTEMPTS_LOGGER = CalciteTrace.getRuleAttemptsTracer(); // 静态日志记录器,用于记录优化规则的尝试次数和执行时间,帮助分析优化器性能

  //~ Instance fields -------------------------------------------------------- // 实例字段区域分隔符

  /**
   * Maps rule description to rule, just to ensure that rules' descriptions
   * are unique.
   */ // 注释:将规则描述映射到规则对象,用于确保规则的描述是唯一的
  protected final Map<String, RelOptRule> mapDescToRule = new LinkedHashMap<>(); // 规则映射表,使用LinkedHashMap保持插入顺序,key是规则的toString()描述,value是规则对象,protected允许子类访问

  protected final RelOptCostFactory costFactory; // 成本工厂,用于创建RelOptCost对象来评估关系表达式的执行成本(如CPU、IO、内存等),final表示初始化后不可修改

  private @MonotonicNonNull MulticastRelOptListener listener; // 多播优化器监听器,用于在优化过程中广播事件给多个监听器,初始为null,一旦设置后不再变回null

  private @MonotonicNonNull RuleAttemptsListener ruleAttemptsListener; // 规则尝试监听器,用于统计每个规则的尝试次数和执行时间,仅在DEBUG日志级别启用

  private @Nullable Pattern ruleDescExclusionFilter; // 规则描述排除过滤器,使用正则表达式匹配规则描述,匹配的规则将被排除不执行

  protected final AtomicBoolean cancelFlag; // 原子布尔标志,用于支持优化过程的取消操作,线程安全,final表示初始化后不可修改

  private final Set<Class<? extends RelNode>> classes = new HashSet<>(); // 已注册的RelNode类集合,用于跟踪优化过程中遇到的所有关系表达式类型

  private final Set<Convention> conventions = new HashSet<>(); // 已注册的约定集合,用于跟踪优化过程中遇到的所有调用约定(如物理约定、逻辑约定等)

  /** External context. Never null. */ // 注释:外部上下文,永不为null
  protected final Context context; // 外部上下文对象,用于传递配置信息和依赖项,final表示初始化后不可修改

  private @Nullable RexExecutor executor; // 表达式执行器,用于执行常量折叠、表达式简化等操作,可能为null

  private @Nullable RelDecorrelator decorrelator; // 去相关器,用于去除子查询的相关性,将相关子查询转换为非相关子查询,可能为null

  //~ Constructors ----------------------------------------------------------- // 构造方法区域分隔符

  /**
   * Creates an AbstractRelOptPlanner.
   */ // 注释:创建一个AbstractRelOptPlanner实例
  protected AbstractRelOptPlanner(RelOptCostFactory costFactory, // 构造方法,接收成本工厂和上下文作为参数,protected表示只能被子类调用
      @Nullable Context context) { // 参数:context可能为null,表示外部上下文
    this.costFactory = requireNonNull(costFactory, "costFactory"); // 初始化成本工厂,使用requireNonNull确保costFactory不为null,否则抛出NullPointerException
    if (context == null) { // 如果传入的context为null
      context = Contexts.empty(); // 则使用空的上下文对象
    } // end if
    this.context = context; // 初始化上下文字段

    this.cancelFlag = // 初始化取消标志
        context.maybeUnwrap(CancelFlag.class) // 尝试从上下文中解包CancelFlag对象
            .map(flag -> flag.atomicBoolean) // 如果成功解包,则使用其内部的atomicBoolean
            .orElseGet(AtomicBoolean::new); // 如果解包失败,则创建一个新的AtomicBoolean

    // Add abstract RelNode classes. No RelNodes will ever be registered with
    // these types, but some operands may use them.
    // 注释:添加抽象RelNode类。不会有RelNode使用这些类型注册,但某些操作数可能会使用它们
    classes.add(RelNode.class); // 添加RelNode基类到已注册类集合
    classes.add(RelSubset.class); // 添加RelSubset类到已注册类集合

    if (RULE_ATTEMPTS_LOGGER.isDebugEnabled()) { // 如果规则尝试日志记录器启用了DEBUG级别
      this.ruleAttemptsListener = new RuleAttemptsListener(); // 创建规则尝试监听器实例
      addListener(this.ruleAttemptsListener); // 将监听器添加到优化器的监听器列表中
    } // end if
    addListener(new RuleEventLogger()); // 添加规则事件日志记录器,用于记录规则执行事件
  } // 构造方法结束

  //~ Methods ---------------------------------------------------------------- // 方法区域分隔符

  @Override public void clear() {} // 清空优化器状态,默认实现为空,子类可以重写以实现特定的清理逻辑

  @Override public Context getContext() { // 获取外部上下文
    return context; // 返回上下文对象
  } // 方法结束

  @Override public RelOptCostFactory getCostFactory() { // 获取成本工厂
    return costFactory; // 返回成本工厂对象
  } // 方法结束

  @SuppressWarnings("deprecation") // 抑制过时警告
  @Override public void setCancelFlag(CancelFlag cancelFlag) { // 设置取消标志,已过时
    // ignored // 注释:忽略此操作,因为取消标志现在通过构造函数从上下文中获取
  } // 方法结束

  /**
   * Checks to see whether cancellation has been requested, and if so, throws
   * an exception.
   */ // 注释:检查是否请求了取消,如果是则抛出异常
  public void checkCancel() { // 检查取消标志的方法
    if (cancelFlag.get()) { // 如果取消标志为true
      throw RESOURCE.preparationAborted().ex(); // 抛出准备中止异常,表示优化过程被取消
    } // end if
  } // 方法结束

  @Override public List<RelOptRule> getRules() { // 获取所有已注册的优化规则
    return ImmutableList.copyOf(mapDescToRule.values()); // 返回规则映射表中所有值的不可变副本
  } // 方法结束

  @Override public boolean addRule(RelOptRule rule) { // 添加优化规则到优化器
    // Check that there isn't a rule with the same description
    // 注释:检查是否已存在具有相同描述的规则
    final String description = requireNonNull(rule.toString()); // 获取规则的描述字符串,并确保不为null

    RelOptRule existingRule = mapDescToRule.put(description, rule); // 将规则放入映射表,返回之前相同描述的规则(如果有)
    if (existingRule != null) { // 如果已存在相同描述的规则
      if (existingRule.equals(rule)) { // 如果已存在的规则与新规则相等(使用equals方法)
        return false; // 返回false,表示规则已存在,没有添加
      } else { // 如果描述相同但规则不相等
        // This rule has the same description as one previously
        // registered, yet it is not equal. You may need to fix the
        // rule's equals and hashCode methods.
        // 注释:此规则与之前注册的规则有相同的描述,但不相等。可能需要修复规则的equals和hashCode方法
        throw new AssertionError("Rule's description should be unique; " // 抛出断言错误,提示规则描述必须唯一
            + "existing rule=" + existingRule + "; new rule=" + rule); // 错误信息包含现有规则和新规则
      } // end else
    } // end if
    return true; // 返回true,表示规则成功添加
  } // 方法结束

  @Override public boolean removeRule(RelOptRule rule) { // 从优化器中移除优化规则
    String description = rule.toString(); // 获取规则的描述字符串
    RelOptRule removed = mapDescToRule.remove(description); // 从映射表中移除规则,返回被移除的规则(如果有)
    return removed != null; // 返回true表示成功移除,false表示规则不存在
  } // 方法结束

  /**
   * Returns the rule with a given description.
   *
   * @param description Description
   * @return Rule with given description, or null if not found
   */ // 注释:返回具有给定描述的规则
  protected @Nullable RelOptRule getRuleByDescription(String description) { // 根据描述获取规则
    return mapDescToRule.get(description); // 从映射表中查找并返回规则,如果不存在则返回null
  } // 方法结束

  @Override public void setRuleDescExclusionFilter(@Nullable Pattern exclusionFilter) { // 设置规则描述排除过滤器
    ruleDescExclusionFilter = exclusionFilter; // 保存正则表达式过滤器
  } // 方法结束

  /**
   * Determines whether a given rule is excluded by ruleDescExclusionFilter.
   *
   * @param rule rule to test
   * @return true iff rule should be excluded
   */ // 注释:确定给定规则是否被规则描述排除过滤器排除
  public boolean isRuleExcluded(RelOptRule rule) { // 检查规则是否被排除
    return ruleDescExclusionFilter != null // 如果过滤器不为null
        && ruleDescExclusionFilter.matcher(rule.toString()).matches(); // 并且规则描述匹配正则表达式模式,则返回true
  } // 方法结束

  @Override public RelOptPlanner chooseDelegate() { // 选择委托优化器
    return this; // 默认返回自身,表示不委托给其他优化器
  } // 方法结束

  @Override public void addMaterialization(RelOptMaterialization materialization) { // 添加物化视图
    // ignore - this planner does not support materializations
    // 注释:忽略 - 此优化器不支持物化视图
  } // 方法结束

  @Override public List<RelOptMaterialization> getMaterializations() { // 获取所有物化视图
    return ImmutableList.of(); // 返回空列表,表示没有物化视图
  } // 方法结束

  @Override public void addLattice(RelOptLattice lattice) { // 添加格结构
    // ignore - this planner does not support lattices
    // 注释:忽略 - 此优化器不支持格结构
  } // 方法结束

  @Override public @Nullable RelOptLattice getLattice(RelOptTable table) { // 获取表的格结构
    // this planner does not support lattices
    // 注释:此优化器不支持格结构
    return null; // 返回null
  } // 方法结束

  @Override public void registerSchema(RelOptSchema schema) { // 注册Schema
  } // 方法结束,默认为空实现

  @Deprecated // to be removed before 2.0 // 标记为已过时,将在2.0版本前移除
  @Override public long getRelMetadataTimestamp(RelNode rel) { // 获取关系表达式元数据时间戳,已过时
    return 0; // 返回0,表示不支持
  } // 方法结束

  @Override public void prune(RelNode rel) { // 修剪关系表达式
  } // 方法结束,默认为空实现

  @Override public void registerClass(RelNode node) { // 注册关系表达式类
    final Class<? extends RelNode> clazz = node.getClass(); // 获取关系表达式的类对象
    if (classes.add(clazz)) { // 如果类是新添加的(之前不存在)
      onNewClass(node); // 调用新类处理方法
    } // end if
    Convention convention = node.getConvention(); // 获取关系表达式的调用约定
    if (convention != null && conventions.add(convention)) { // 如果约定不为null且是新添加的
      convention.register(this); // 在约定中注册此优化器
    } // end if
  } // 方法结束

  /** Called when a new class of {@link RelNode} is seen. */ // 注释:当看到新的RelNode类时调用
  protected void onNewClass(RelNode node) { // 处理新类的方法
    node.register(this); // 在关系表达式中注册此优化器
  } // 方法结束

  @Override public RelTraitSet emptyTraitSet() { // 获取空的特性集合
    return RelTraitSet.createEmpty(); // 创建并返回空的特性集合
  } // 方法结束

  @Override public @Nullable RelOptCost getCost(RelNode rel, RelMetadataQuery mq) { // 获取关系表达式的成本
    return mq.getCumulativeCost(rel); // 使用元数据查询获取累积成本
  } // 方法结束

  @Deprecated // to be removed before 2.0 // 标记为已过时,将在2.0版本前移除
  @Override public @Nullable RelOptCost getCost(RelNode rel) { // 获取关系表达式的成本(已过时版本)
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 从关系表达式的簇中获取元数据查询对象
    return getCost(rel, mq); // 调用新版本的getCost方法
  } // 方法结束

  @Override public void addListener( // 添加监听器
      @UnknownInitialization AbstractRelOptPlanner this, // 注解表示此对象可能处于未初始化状态
      RelOptListener newListener) { // 新的监听器
    if (listener == null) { // 如果当前监听器为null
      listener = new MulticastRelOptListener(); // 创建多播监听器
    } // end if
    listener.addListener(newListener); // 将新监听器添加到多播监听器中
  } // 方法结束

  @Deprecated // to be removed before 2.0 // 标记为已过时,将在2.0版本前移除
  @Override public void registerMetadataProviders(List<RelMetadataProvider> list) { // 注册元数据提供者,已过时
  } // 方法结束,默认为空实现

  @Override public boolean addRelTraitDef(RelTraitDef relTraitDef) { // 添加关系特性定义
    return false; // 返回false,表示不支持
  } // 方法结束

  @Override public void clearRelTraitDefs() {} // 清空关系特性定义
  @Override public List<RelTraitDef> getRelTraitDefs() { // 获取所有关系特性定义
    return ImmutableList.of(); // 返回空列表
  } // 方法结束

  @Override public void setExecutor(@Nullable RexExecutor executor) { // 设置表达式执行器
    this.executor = executor; // 保存执行器
  } // 方法结束

  @Override public @Nullable RexExecutor getExecutor() { // 获取表达式执行器
    return executor; // 返回执行器,可能为null
  } // 方法结束

  @Override public void setDecorrelator(@Nullable RelDecorrelator decorrelator) { // 设置去相关器
    this.decorrelator = decorrelator; // 保存去相关器
  } // 方法结束

  @Override public RelDecorrelator getDecorrelator() { // 获取去相关器
    if (decorrelator == null) { // 如果去相关器为null
      throw new IllegalStateException("RelDecorrelator has not been set"); // 抛出非法状态异常
    } // end if
    return decorrelator; // 返回去相关器
  } // 方法结束

  @Override public void onCopy(RelNode rel, RelNode newRel) { // 当关系表达式被复制时调用
    // do nothing // 注释:不做任何事情
  } // 方法结束

  protected void dumpRuleAttemptsInfo() { // 输出规则尝试信息
    if (this.ruleAttemptsListener != null) { // 如果规则尝试监听器不为null
      RULE_ATTEMPTS_LOGGER.debug("Rule Attempts Info for " + this.getClass().getSimpleName()); // 记录调试日志,输出类名
      RULE_ATTEMPTS_LOGGER.debug(this.ruleAttemptsListener.dump()); // 记录调试日志,输出规则尝试统计信息
    } // end if
  } // 方法结束

  /**
   * Fires a rule, taking care of tracing and listener notification.
   *
   * @param ruleCall description of rule call
   */ // 注释:触发规则,处理跟踪和监听器通知
  protected void fireRule( // 触发规则的方法
      RelOptRuleCall ruleCall) { // 规则调用对象,包含规则和匹配的关系表达式
    checkCancel(); // 检查是否请求取消

    assert ruleCall.getRule().matches(ruleCall); // 断言规则匹配规则调用
    if (isRuleExcluded(ruleCall.getRule())) { // 如果规则被排除过滤器排除
      LOGGER.debug("call#{}: Rule [{}] not fired due to exclusion filter", // 记录调试日志
          ruleCall.id, ruleCall.getRule()); // 输出规则ID和规则对象
      return; // 直接返回,不执行规则
    } // end if

    if (ruleCall.isRuleExcluded()) { // 如果规则调用标记为排除
      LOGGER.debug("call#{}: Rule [{}] not fired due to exclusion hint", // 记录调试日志
          ruleCall.id, ruleCall.getRule()); // 输出规则ID和规则对象
      return; // 直接返回,不执行规则
    } // end if

    if (listener != null) { // 如果有监听器
      RelOptListener.RuleAttemptedEvent event = // 创建规则尝试事件
          new RelOptListener.RuleAttemptedEvent( // 事件构造
              this, // 优化器引用
              ruleCall.rel(0), // 第一个关系表达式
              ruleCall, // 规则调用对象
              true); // true表示规则执行前
      listener.ruleAttempted(event); // 通知监听器规则尝试开始
    } // end if

    ruleCall.getRule().onMatch(ruleCall); // 调用规则的onMatch方法执行规则转换

    if (listener != null) { // 如果有监听器
      RelOptListener.RuleAttemptedEvent event = // 创建规则尝试事件
          new RelOptListener.RuleAttemptedEvent( // 事件构造
              this, // 优化器引用
              ruleCall.rel(0), // 第一个关系表达式
              ruleCall, // 规则调用对象
              false); // false表示规则执行后
      listener.ruleAttempted(event); // 通知监听器规则尝试结束
    } // end if
  } // 方法结束

  /**
   * Takes care of tracing and listener notification when a rule's
   * transformation is applied.
   *
   * @param ruleCall description of rule call
   * @param newRel   result of transformation
   * @param before   true before registration of new rel; false after
   */ // 注释:处理规则转换应用时的跟踪和监听器通知
  protected void notifyTransformation( // 通知转换的方法
      RelOptRuleCall ruleCall, // 规则调用对象
      RelNode newRel, // 转换后的新关系表达式
      boolean before) { // true表示注册新关系表达式前,false表示注册后
    if (listener != null) { // 如果有监听器
      RelOptListener.RuleProductionEvent event = // 创建规则产生事件
          new RelOptListener.RuleProductionEvent( // 事件构造
              this, // 优化器引用
              newRel, // 新的关系表达式
              ruleCall, // 规则调用对象
              before); // before标志
      listener.ruleProductionSucceeded(event); // 通知监听器规则产生成功
    } // end if
  } // 方法结束

  /**
   * Takes care of tracing and listener notification when a rel is chosen as
   * part of the final plan.
   *
   * @param rel chosen rel
   */ // 注释:处理关系表达式被选为最终计划一部分时的跟踪和监听器通知
  protected void notifyChosen(RelNode rel) { // 通知选择的方法
    LOGGER.debug("For final plan, using {}", rel); // 记录调试日志,输出被选择的关系表达式

    if (listener != null) { // 如果有监听器
      RelOptListener.RelChosenEvent event = // 创建关系选择事件
          new RelOptListener.RelChosenEvent( // 事件构造
              this, // 优化器引用
              rel); // 被选择的关系表达式
      listener.relChosen(event); // 通知监听器关系被选择
    } // end if
  } // 方法结束

  /**
   * Takes care of tracing and listener notification when a rel equivalence is
   * detected.
   *
   * @param rel chosen rel
   */ // 注释:处理检测到关系表达式等价时的跟踪和监听器通知
  protected void notifyEquivalence( // 通知等价的方法
      RelNode rel, // 关系表达式
      Object equivalenceClass, // 等价类对象
      boolean physical) { // 是否为物理等价
    if (listener != null) { // 如果有监听器
      RelOptListener.RelEquivalenceEvent event = // 创建关系等价事件
          new RelOptListener.RelEquivalenceEvent( // 事件构造
              this, // 优化器引用
              rel, // 关系表达式
              equivalenceClass, // 等价类
              physical); // 物理标志
      listener.relEquivalenceFound(event); // 通知监听器发现等价关系
    } // end if
  } // 方法结束

  /**
   * Takes care of tracing and listener notification when a rel is discarded.
   *
   * @param rel Discarded rel
   */ // 注释:处理关系表达式被丢弃时的跟踪和监听器通知
  protected void notifyDiscard(RelNode rel) { // 通知丢弃的方法
    if (listener != null) { // 如果有监听器
      RelOptListener.RelDiscardedEvent event = // 创建关系丢弃事件
          new RelOptListener.RelDiscardedEvent( // 事件构造
              this, // 优化器引用
              rel); // 被丢弃的关系表达式
      listener.relDiscarded(event); // 通知监听器关系被丢弃
    } // end if
  } // 方法结束

  @Pure // 注解表示此方法不修改对象状态
  public @Nullable RelOptListener getListener() { // 获取监听器
    return listener; // 返回监听器,可能为null
  } // 方法结束

  /** Returns sub-classes of relational expression. */ // 注释:返回关系表达式的子类
  public Iterable<Class<? extends RelNode>> subClasses( // 获取子类的方法
      final Class<? extends RelNode> clazz) { // 要查询的父类
    return Util.filter(classes, c -> { // 过滤已注册的类集合
      // RelSubset must be exact type, not subclass
      // 注释:RelSubset必须是精确类型,不能是子类
      if (c == RelSubset.class) { // 如果是RelSubset类
        return c == clazz; // 则必须完全相等
      } // end if
      return clazz.isAssignableFrom(c); // 否则检查是否可赋值(即c是clazz的子类或本身)
    }); // 返回过滤后的迭代器
  } // 方法结束

  /** Listener for counting the attempts of each rule. Only enabled under DEBUG level.*/ // 注释:用于统计每个规则尝试次数的监听器,仅在DEBUG级别启用
  private static class RuleAttemptsListener implements RelOptListener { // 规则尝试监听器内部类,实现RelOptListener接口
    private long beforeTimestamp; // 规则执行前的时间戳,用于计算执行时间
    private final Map<String, Pair<Long, Long>> ruleAttempts; // 规则尝试统计映射,key是规则描述,value是Pair<尝试次数,总执行时间(微秒)>

    RuleAttemptsListener() { // 构造方法
      ruleAttempts = new HashMap<>(); // 初始化规则尝试映射表
    } // 构造方法结束

    @Override public void relEquivalenceFound(RelEquivalenceEvent event) { // 当发现关系等价时调用
    } // 方法结束,空实现

    @Override public void ruleAttempted(RuleAttemptedEvent event) { // 当规则尝试时调用
      if (event.isBefore()) { // 如果是规则执行前
        this.beforeTimestamp = System.nanoTime(); // 记录当前时间戳(纳秒)
      } else { // 如果是规则执行后
        long elapsed = (System.nanoTime() - this.beforeTimestamp) / 1000; // 计算执行时间(微秒)
        String rule = event.getRuleCall().getRule().toString(); // 获取规则描述
        ruleAttempts.compute(rule, (k, p) -> // 更新规则尝试统计
            p == null // 如果之前没有统计记录
                ? Pair.of(1L,  elapsed) // 则创建新记录,尝试次数为1,时间为elapsed
                : Pair.of(p.left + 1, p.right + elapsed)); // 否则累加尝试次数和时间
      } // end else
    } // 方法结束

    @Override public void ruleProductionSucceeded(RuleProductionEvent event) { // 当规则产生成功时调用
    } // 方法结束,空实现

    @Override public void relDiscarded(RelDiscardedEvent event) { // 当关系被丢弃时调用
    } // 方法结束,空实现

    @Override public void relChosen(RelChosenEvent event) { // 当关系被选择时调用
    } // 方法结束,空实现

    public String dump() { // 输出规则尝试统计信息
      // Sort rules by number of attempts descending, then by rule elapsed time descending,
      // then by rule name ascending.
      // 注释:按尝试次数降序、执行时间降序、规则名称升序排序
      List<Map.Entry<String, Pair<Long, Long>>> list = // 创建列表
          new ArrayList<>(this.ruleAttempts.entrySet()); // 将映射表条目转换为列表
      list.sort((left, right) -> { // 对列表进行排序
        int res = right.getValue().left.compareTo(left.getValue().left); // 先按尝试次数降序比较
        if (res == 0) { // 如果尝试次数相同
          res = right.getValue().right.compareTo(left.getValue().right); // 则按执行时间降序比较
        } // end if
        if (res == 0) { // 如果执行时间也相同
          res = left.getKey().compareTo(right.getKey()); // 则按规则名称升序比较
        } // end if
        return res; // 返回比较结果
      }); // 排序结束

      // Print out rule attempts and time
      // 注释:输出规则尝试次数和时间
      StringBuilder sb = new StringBuilder(); // 创建字符串构建器
      sb.append(String // 添加表头
          .format(Locale.ROOT, "%n%-60s%20s%20s%n", "Rules", "Attempts", "Time (us)")); // 格式化输出表头
      NumberFormat usFormat = NumberFormat.getNumberInstance(Locale.US); // 创建美国数字格式化器
      long totalAttempts = 0; // 总尝试次数
      long totalTime = 0; // 总执行时间
      for (Map.Entry<String, Pair<Long, Long>> entry : list) { // 遍历每个规则
        sb.append( // 添加规则统计信息
            String.format(Locale.ROOT, "%-60s%20s%20s%n", // 格式化输出
                entry.getKey(), // 规则名称
                usFormat.format(entry.getValue().left), // 尝试次数
                usFormat.format(entry.getValue().right))); // 执行时间
        totalAttempts += entry.getValue().left; // 累加总尝试次数
        totalTime += entry.getValue().right; // 累加总执行时间
      } // end for
      sb.append( // 添加总计行
          String.format(Locale.ROOT, "%-60s%20s%20s%n", // 格式化输出
              "* Total", // 总计标记
              usFormat.format(totalAttempts), // 总尝试次数
              usFormat.format(totalTime))); // 总执行时间

      return sb.toString(); // 返回格式化后的字符串
    } // 方法结束
  } // 内部类结束
} // 类结束
