/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明，允许在特定条件下使用本代码
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看随本工作分发的NOTICE文件以获取版权信息
 * this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证授权您使用此文件
 * (the "License"); you may not use this file except in compliance with // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at // 您可以从以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // Apache许可证2.0的在线地址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则根据许可证分发的软件
 * distributed under the License is distributed on an "AS IS" BASIS, // 按原样分发，不附带任何明示或暗示的保证或条件
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不提供任何形式的保证或条件，包括但不限于适销性或适用性保证
 * See the License for the specific language governing permissions and // 请参阅许可证以了解特定的语言权限和
 * limitations under the License. // 使用限制
 */
package org.apache.calcite.plan; // 声明包名，此类属于org.apache.calcite.plan包，是Calcite优化器规则操作数相关的类

import org.apache.calcite.rel.RelNode; // 导入RelNode类，Calcite中关系代数表达式的抽象基类

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList，用于创建不可变列表

import org.checkerframework.checker.initialization.qual.NotOnlyInitialized; // 导入Checker框架的注解，用于标记字段可能未完全初始化
import org.checkerframework.checker.initialization.qual.UnknownInitialization; // 导入Checker框架的注解，表示对象处于未知初始化状态
import org.checkerframework.checker.nullness.qual.MonotonicNonNull; // 导入Checker框架的注解，表示字段初始化后不会再变为null
import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker框架的注解，表示字段可以为null

import java.util.List; // 导入Java标准库的List接口，用于存储有序集合
import java.util.Objects; // 导入Java标准库的Objects类，提供对象操作工具方法
import java.util.function.Predicate; // 导入Java函数式接口Predicate，用于定义断言逻辑

import static com.google.common.base.Preconditions.checkArgument; // 导入Google Guava的静态方法，用于参数校验

import static java.util.Objects.requireNonNull; // 导入Java标准库的静态方法，用于检查对象非空

/**
 * Operand that determines whether a {@link RelOptRule} // 操作数，用于确定RelOptRule(优化规则)是否可以应用于特定的关系表达式
 * can be applied to a particular expression. // 判断规则是否适用于某个特定的表达式
 *
 * <p>For example, the rule to pull a filter up from the left side of a join // 例如，将过滤器从连接左侧向上提升的规则
 * takes operands: <code>Join(Filter, Any)</code>. // 需要的操作数是：Join(Filter, Any)，表示Join的左子节点是Filter，右子节点可以是任何类型
 *
 * <p>Note that <code>children</code> means different things if it is empty or // 注意：children字段在空和null时有不同的含义
 * it is <code>null</code>: <code>Join(Filter <b>()</b>, Any)</code> means // Join(Filter(), Any)表示Filter必须没有子操作数（即叶子节点）
 * that, to match the rule, <code>Filter</code> must have no operands. // 为了匹配规则，Filter必须没有操作数
 */
public class RelOptRuleOperand { // RelOptRuleOperand类的定义，表示优化规则的操作数，用于模式匹配
  //~ Instance fields -------------------------------------------------------- // 实例字段区域开始标记

  private @Nullable RelOptRuleOperand parent; // 父操作数引用，可为null，表示此操作数属于哪个父操作数，null表示是根操作数
  private @NotOnlyInitialized RelOptRule rule; // 所属的优化规则引用，使用NotOnlyInitialized注解表示可能未完全初始化，指向包含此操作数的RelOptRule对象
  private final Predicate<RelNode> predicate; // 谓词函数，用于对匹配的RelNode进行额外的条件判断，final表示初始化后不可修改

  // REVIEW jvs 29-Aug-2004: some of these are Volcano-specific and should be // 评审注释：jvs在2004年8月29日指出，有些字段是Volcano优化器特有的，应该被提取出来
  // factored out // 这意味着这些字段可能与特定的优化器实现相关，未来可能需要重构
  public int @MonotonicNonNull [] solveOrder; // 求解顺序数组，MonotonicNonNull注解表示初始化后不会再变为null，用于Volcano优化器中定义子操作数的匹配顺序
  public int ordinalInParent; // 在父操作数中的序号位置，从0开始，标识此操作数是父操作数的第几个子节点
  public int ordinalInRule; // 在整个规则中的序号位置，从0开始，标识此操作数在整个规则操作数树中的全局位置
  public final @Nullable RelTrait trait; // 需要匹配的特征，可为null，表示此操作数要求匹配的RelNode必须具有的特征（如分布特征、排序特征等），null表示匹配任何特征
  private final Class<? extends RelNode> clazz; // 需要匹配的RelNode类类型，final表示不可修改，必须是RelNode的子类，用于类型匹配
  private final ImmutableList<RelOptRuleOperand> children; // 子操作数列表，final不可变列表，存储此操作数的所有子操作数，构成操作数树结构

  /**
   * Whether child operands can be matched in any order. // 子操作数是否可以以任意顺序匹配的策略枚举
   */
  public final RelOptRuleOperandChildPolicy childPolicy; // 子操作数匹配策略，final不可修改，定义子操作数的匹配规则（如必须按顺序、任意顺序、无子节点等）

  //~ Constructors ----------------------------------------------------------- // 构造方法区域开始标记

  /**
   * Creates an operand. // 创建一个操作数对象
   *
   * <p>The {@code childOperands} argument is often populated by calling one // childOperands参数通常通过调用以下方法之一来填充
   * of the following methods: // 这些方法用于构建子操作数列表
   * {@link RelOptRule#some}, // RelOptRule.some()方法，表示匹配部分子操作数
   * {@link RelOptRule#none()}, // RelOptRule.none()方法，表示不匹配任何子操作数
   * {@link RelOptRule#any}, // RelOptRule.any()方法，表示匹配任意类型的子操作数
   * {@link RelOptRule#unordered}, // RelOptRule.unordered()方法，表示子操作数可以无序匹配
   * See {@link org.apache.calcite.plan.RelOptRuleOperandChildren} for more // 参见RelOptRuleOperandChildren类获取更多详细信息
   * details. // 关于子操作数构建的详细说明
   *
   * @param clazz    Class of relational expression to match (must not be null) // 需要匹配的关系表达式类，不能为null，必须是RelNode的子类
   * @param trait    Trait to match, or null to match any trait // 需要匹配的特征，null表示匹配任何特征
   * @param predicate Predicate to apply to relational expression // 应用于关系表达式的谓词函数，用于额外的条件判断
   * @param children Child operands // 子操作数列表，定义此操作数的子节点模式
   *
   * @deprecated Use // 已废弃，建议使用以下方法替代
   * {@link RelOptRule#operand(Class, RelOptRuleOperandChildren)} or one of its // 使用RelOptRule.operand()方法或其重载版本
   * overloaded methods. // 来创建操作数对象
   */
  @Deprecated // to be removed before 2.0; see [CALCITE-1166] // 标记为已废弃，将在2.0版本前移除，参见CALCITE-1166问题
  protected <R extends RelNode> RelOptRuleOperand( // 受保护的泛型构造方法，R是RelNode的子类
      Class<R> clazz, // 参数：需要匹配的RelNode类类型
      RelTrait trait, // 参数：需要匹配的特征，可为null
      Predicate<? super R> predicate, // 参数：谓词函数，接受R或其父类
      RelOptRuleOperandChildren children) { // 参数：子操作数容器对象，包含策略和操作数列表
    this(clazz, trait, predicate, children.policy, children.operands); // 调用私有构造方法，从children对象中提取策略和操作数列表
  }

  /** Private constructor. // 私有构造方法
   *
   * <p>Do not call from outside package, and do not create a sub-class. // 不要从包外部调用，也不要创建子类
   *
   * <p>The other constructor is deprecated; when it is removed, make fields // 另一个构造方法已废弃；当它被移除时，应该将以下字段设为final
   * {@link #parent}, {@link #ordinalInParent} and {@link #solveOrder} final, // parent、ordinalInParent和solveOrder字段应该设为final
   * and add constructor parameters for them. See // 并为它们添加构造方法参数，参见
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1166">[CALCITE-1166] // CALCITE-1166问题：禁止RelOptRuleOperand的子类
   * Disallow sub-classes of RelOptRuleOperand</a>. // 这个问题讨论了移除子类支持的计划
   */
  @SuppressWarnings({"initialization.fields.uninitialized", // 抑制未初始化字段的警告
      "initialization.invalid.field.write.initialized", "unchecked"}) // 抑制无效字段写入和未检查转换的警告
  <R extends RelNode> RelOptRuleOperand( // 私有泛型构造方法，R是RelNode的子类
      Class<R> clazz, // 参数：需要匹配的RelNode类类型
      @Nullable RelTrait trait, // 参数：需要匹配的特征，可为null
      Predicate<? super R> predicate, // 参数：谓词函数，接受R或其父类
      RelOptRuleOperandChildPolicy childPolicy, // 参数：子操作数匹配策略枚举
      ImmutableList<RelOptRuleOperand> children) { // 参数：不可变的子操作数列表
    this.clazz = requireNonNull(clazz, "clazz"); // 初始化clazz字段，检查clazz不能为null，否则抛出NullPointerException
    switch (childPolicy) { // 根据子操作数策略进行不同的验证
    case ANY: // 策略为ANY：表示可以匹配任意子操作数
      break; // 不需要特殊验证，直接跳过
    case LEAF: // 策略为LEAF：表示必须是叶子节点，不能有子操作数
      checkArgument(children.isEmpty()); // 检查children列表必须为空，否则抛出IllegalArgumentException
      break; // 验证完成，跳出switch
    case UNORDERED: // 策略为UNORDERED：表示子操作数可以无序匹配
      assert children.size() == 1; // 断言children的大小必须为1，UNORDERED策略只适用于单个子节点
      break; // 验证完成，跳出switch
    default: // 其他策略（如SOME、NONE等）
      checkArgument(!children.isEmpty()); // 检查children列表不能为空，否则抛出IllegalArgumentException
    } // switch语句结束
    this.childPolicy = requireNonNull(childPolicy, "childPolicy"); // 初始化childPolicy字段，检查不能为null
    this.trait = trait; // 初始化trait字段，可以为null
    this.predicate = requireNonNull((Predicate<RelNode>) predicate); // 初始化predicate字段，检查不能为null，并强制转换为Predicate<RelNode>
    this.children = requireNonNull(children, "children"); // 初始化children字段，检查不能为null
    for (RelOptRuleOperand child : this.children) { // 遍历所有子操作数
      assert child.parent == null : "cannot re-use operands"; // 断言子操作数的parent必须为null，防止操作数被重复使用
      child.parent = this; // 设置子操作数的parent引用为当前操作数，建立父子关系
    } // for循环结束，所有子操作数的parent已设置
  } // 构造方法结束

  //~ Methods ---------------------------------------------------------------- // 方法区域开始标记

  /**
   * Returns the parent operand. // 返回父操作数
   *
   * @return parent operand // 返回值：父操作数对象，可能为null（如果是根操作数）
   */
  public @Nullable RelOptRuleOperand getParent() { // 公共方法，获取父操作数
    return parent; // 返回parent字段的值
  } // 方法结束

  /**
   * Sets the parent operand. // 设置父操作数
   *
   * @param parent Parent operand // 参数：父操作数对象，可为null
   */
  public void setParent(@Nullable RelOptRuleOperand parent) { // 公共方法，设置父操作数
    this.parent = parent; // 将parent字段的值设置为传入的参数
  } // 方法结束

  /**
   * Returns the rule this operand belongs to. // 返回此操作数所属的规则
   *
   * @return containing rule // 返回值：包含此操作数的RelOptRule对象
   */
  public RelOptRule getRule() { // 公共方法，获取所属的优化规则
    return rule; // 返回rule字段的值
  } // 方法结束

  /**
   * Sets the rule this operand belongs to. // 设置此操作数所属的规则
   *
   * @param rule containing rule // 参数：包含此操作数的RelOptRule对象
   */
  @SuppressWarnings("initialization.invalid.field.write.initialized") // 抑制无效字段写入的警告
  public void setRule(@UnknownInitialization RelOptRule rule) { // 公共方法，设置所属的优化规则，使用UnknownInitialization注解
    this.rule = rule; // 将rule字段的值设置为传入的参数
  } // 方法结束

  @Override public int hashCode() { // 重写Object类的hashCode方法，用于哈希表操作
    return Objects.hash(clazz, trait, children); // 使用Objects.hash方法计算哈希值，基于clazz、trait和children字段
  } // 方法结束

  @Override public boolean equals(@Nullable Object obj) { // 重写Object类的equals方法，用于对象相等性比较
    if (this == obj) { // 如果是同一个对象引用
      return true; // 直接返回true
    } // if结束
    if (!(obj instanceof RelOptRuleOperand)) { // 如果obj不是RelOptRuleOperand类型的实例
      return false; // 返回false，类型不匹配
    } // if结束
    RelOptRuleOperand that = (RelOptRuleOperand) obj; // 将obj强转为RelOptRuleOperand类型

    return (this.clazz == that.clazz) // 比较clazz字段是否相同（类类型引用相等）
        && Objects.equals(this.trait, that.trait) // 比较trait字段是否相等（使用Objects.equals处理null情况）
        && this.children.equals(that.children); // 比较children列表是否相等
  } // 方法结束

  /**
   * <b>FOR DEBUG ONLY.</b> // 仅用于调试目的
   *
   * <p>To facilitate IDE shows the operand description in the debugger, // 为了方便IDE在调试器中显示操作数描述
   * returns the root operand description, but highlight current // 返回根操作数的描述，但用'*'高亮显示当前操作数
   * operand's matched class with '*' in the description. // 在描述中用星号标记当前操作数匹配的类
   *
   * <p>e.g. The following are examples of rule operand description for // 例如，以下是匹配LogicalFilter的操作数描述示例
   * the operands that match with {@code LogicalFilter}. // 这些示例展示了不同规则中匹配LogicalFilter的操作数描述
   *
   * <ul> // 无序列表开始
   * <li>SemiJoinRule:project: Project(Join(*RelNode*, Aggregate))</li> // SemiJoinRule示例：高亮显示RelNode类
   * <li>ProjectFilterTransposeRule: LogicalProject(*LogicalFilter*)</li> // ProjectFilterTransposeRule示例：高亮显示LogicalFilter类
   * <li>FilterProjectTransposeRule: *Filter*(Project)</li> // FilterProjectTransposeRule示例：高亮显示Filter类
   * <li>ReduceExpressionsRule(Filter): *LogicalFilter*</li> // ReduceExpressionsRule示例：高亮显示LogicalFilter类
   * <li>PruneEmptyJoin(right): Join(*RelNode*, Values)</li> // PruneEmptyJoin示例：高亮显示RelNode类
   * </ul> // 无序列表结束
   *
   * @see #describeIt(RelOptRuleOperand) // 参见describeIt方法，该方法负责生成描述字符串
   */
  @Override public String toString() { // 重写Object类的toString方法，用于生成调试信息
    RelOptRuleOperand root = this; // 从当前操作数开始，初始化root引用
    while (root.parent != null) { // 循环向上遍历，直到找到根操作数（parent为null的操作数）
      root = root.parent; // 将root设置为父操作数
    } // while循环结束，root现在指向根操作数
    StringBuilder s = root.describeIt(this); // 从根操作数开始生成描述，传入this作为需要高亮的操作数
    return s.toString(); // 返回生成的描述字符串
  } // 方法结束

  /**
   * Returns this rule operand description, and highlight the operand's // 返回此规则操作数的描述，并在操作数等于that时高亮显示类名
   * class name with '*' if {@code that} operand equals current operand. // 如果that操作数等于当前操作数，则在类名前后添加'*'
   *
   * @param that The rule operand that needs to be highlighted // 参数：需要高亮显示的规则操作数
   * @return The string builder that describes this rule operand // 返回值：描述此规则操作数的StringBuilder对象
   * @see #toString() // 参见toString方法，该方法调用此方法生成完整描述
   */
  private StringBuilder describeIt(RelOptRuleOperand that) { // 私有方法，递归生成操作数描述
    StringBuilder s = new StringBuilder(); // 创建StringBuilder对象用于构建描述字符串
    if (parent == null) { // 如果当前操作数是根操作数（parent为null）
      s.append(rule).append(": "); // 先添加规则名称和冒号，例如"FilterProjectTransposeRule: "
    } // if结束
    if (this == that) { // 如果当前操作数是需要高亮的操作数
      s.append('*'); // 在类名前添加'*'标记
    } // if结束
    s.append(clazz.getSimpleName()); // 添加匹配的类的简单名称（不含包名）
    if (this == that) { // 如果当前操作数是需要高亮的操作数
      s.append('*'); // 在类名后添加'*'标记
    } // if结束
    if (!children.isEmpty()) { // 如果有子操作数（children列表不为空）
      s.append('('); // 添加左括号，开始子操作数列表
      boolean first = true; // 标记是否是第一个子操作数，用于控制逗号分隔符
      for (RelOptRuleOperand child : children) { // 遍历所有子操作数
        if (!first) { // 如果不是第一个子操作数
          s.append(", "); // 添加逗号和空格作为分隔符
        } // if结束
        s.append(child.describeIt(that)); // 递归调用describeIt方法，添加子操作数的描述
        first = false; // 将first标记设为false，后续子操作数会添加分隔符
      } // for循环结束，所有子操作数描述已添加
      s.append(')'); // 添加右括号，结束子操作数列表
    } // if结束
    return s; // 返回构建好的StringBuilder对象
  } // 方法结束

  /**
   * Returns relational expression class matched by this operand. // 返回此操作数匹配的关系表达式类
   */
  public Class<? extends RelNode> getMatchedClass() { // 公共方法，获取匹配的RelNode类类型
    return clazz; // 返回clazz字段的值
  } // 方法结束

  /**
   * Returns the child operands. // 返回子操作数列表
   *
   * @return child operands // 返回值：子操作数的不可变列表
   */
  public List<RelOptRuleOperand> getChildOperands() { // 公共方法，获取子操作数列表
    return children; // 返回children字段的值
  } // 方法结束

  /**
   * Returns whether a relational expression matches this operand. It must be // 判断关系表达式是否匹配此操作数，必须满足类型和特征要求
   * of the right class and trait. // 关系表达式必须是正确的类类型并具有正确的特征
   */
  public boolean matches(RelNode rel) { // 公共方法，判断给定的RelNode是否匹配此操作数
    if (!clazz.isInstance(rel)) { // 首先检查rel是否是clazz类型的实例
      return false; // 如果类型不匹配，返回false
    } // if结束，类型匹配
    if ((trait != null) && !rel.getTraitSet().contains(trait)) { // 如果trait不为null且rel的特征集不包含指定的trait
      return false; // 特征不匹配，返回false
    } // if结束，特征匹配
    return predicate.test(rel); // 最后执行谓词测试，返回谓词函数的结果（true表示完全匹配）
  } // 方法结束
} // 类定义结束