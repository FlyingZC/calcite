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
package org.apache.calcite.plan; // 声明包名，表示这个类属于 org.apache.calcite.plan 包

import org.apache.calcite.rel.RelNode; // 导入 RelNode 类，表示关系表达式节点
import org.apache.calcite.rel.convert.Converter; // 导入 Converter 类，用于转换规则
import org.apache.calcite.rel.convert.ConverterRule; // 导入 ConverterRule 类，转换规则的基类
import org.apache.calcite.rel.core.RelFactories; // 导入 RelFactories 类，提供关系表达式工厂
import org.apache.calcite.tools.RelBuilderFactory; // 导入 RelBuilderFactory 接口，用于构建关系表达式
import org.apache.calcite.util.Util; // 导入 Util 工具类，提供通用工具方法

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的不可变列表类
import com.google.common.collect.Lists; // 导入 Google Guava 的列表工具类

import org.checkerframework.checker.initialization.qual.UnderInitialization; // 导入初始化检查注解
import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性检查注解

import java.util.ArrayList; // 导入 Java 标准库的 ArrayList 类
import java.util.List; // 导入 Java 标准库的 List 接口
import java.util.function.Predicate; // 导入 Java 函数式接口 Predicate，用于谓词判断

import static java.util.Objects.requireNonNull; // 导入 Objects.requireNonNull 静态方法，用于非空检查

/**
 * RelOptRule（关系优化规则）是 Calcite 查询优化器中的核心抽象类
 * 它定义了如何将一个关系表达式（RelNode）转换为另一个关系表达式
 * 
 * 【类的作用】：
 * 1. 定义优化规则的基本结构，所有具体的优化规则都必须继承这个抽象类
 * 2. 描述规则能够匹配的关系表达式模式（通过 RelOptRuleOperand）
 * 3. 提供规则匹配后的转换逻辑（通过 onMatch 方法）
 * 
 * 【工作原理】：
 * - 每个规则包含一个或多个 RelOptRuleOperand（操作数），这些操作数定义了规则能够匹配的关系表达式模式
 * - 优化器会遍历关系表达式树，找出哪些规则可以应用到当前的表达式树
 * - 当规则匹配成功时，优化器调用 onMatch 方法，规则可以创建新的关系表达式并注册到优化器中
 * 
 * 【使用场景示例】：
 * - 下推过滤规则（FilterIntoJoinRule）：将过滤条件从 Join 节点下推到 Join 的输入节点
 * - 投影消除规则（ProjectRemoveRule）：移除不必要的投影节点
 * - Join 交换规则（JoinCommuteRule）：交换 Join 的左右输入顺序以优化性能
 * 
 * 【重要概念】：
 * - Operand（操作数）：定义规则匹配的关系表达式类型和结构
 * - RelNode（关系表达式）：表示查询计划中的节点，如 Scan、Filter、Project、Join 等
 * - RelOptPlanner（优化器）：负责应用规则来优化查询计划
 * - RelOptRuleCall（规则调用）：封装了规则匹配时的上下文信息
 * 
 * 【规则的生命周期】：
 * 1. 规则被创建并注册到优化器
 * 2. 优化器通过 operands 检查规则是否能匹配当前的关系表达式
 * 3. 如果匹配成功，调用 matches 方法进行额外的条件检查
 * 4. 如果 matches 返回 true，调用 onMatch 方法执行转换
 * 5. onMatch 方法创建新的关系表达式并通过 transformTo 注册到优化器
 */
public abstract class RelOptRule { // 定义抽象类 RelOptRule，所有优化规则都必须继承此类
  //~ Static fields/initializers ---------------------------------------------
  // 静态字段和初始化块区域（当前类没有静态字段）

  //~ Instance fields --------------------------------------------------------
  // 实例字段区域，定义类的成员变量

  /**
   * Description of rule, must be unique within planner. Default is the name
   * of the class sans package name, but derived classes are encouraged to
   * override.
   */
  protected final String description; // 规则的描述信息，必须在优化器中唯一。默认值是类名（不含包名），子类可以覆盖

  /**
   * Root of operand tree.
   */
  private final RelOptRuleOperand operand; // 操作数树的根节点，定义了规则能够匹配的关系表达式模式（根节点）

  /** Factory for a builder for relational expressions.
   *
   * <p>The actual builder is available via {@link RelOptRuleCall#builder()}. */
  public final RelBuilderFactory relBuilderFactory; // 关系表达式构建器工厂，用于在 onMatch 方法中创建新的关系表达式。实际构建器通过 RelOptRuleCall.builder() 获取

  /**
   * Flattened list of operands.
   */
  public final List<RelOptRuleOperand> operands; // 操作数的扁平化列表，包含规则中所有的操作数（包括根操作数及其所有子操作数），便于快速遍历

  //~ Constructors -----------------------------------------------------------
  // 构造方法区域

  /**
   * Creates a rule.
   *
   * @param operand root operand, must not be null
   */
  protected RelOptRule(RelOptRuleOperand operand) { // 构造方法1：仅指定根操作数。使用默认的逻辑构建器，描述通过类名自动生成
    this(operand, RelFactories.LOGICAL_BUILDER, null); // 调用主构造方法，使用默认的逻辑构建器，描述为 null（后续会自动生成）
  }

  /**
   * Creates a rule with an explicit description.
   *
   * @param operand     root operand, must not be null
   * @param description Description, or null to guess description
   */
  protected RelOptRule(RelOptRuleOperand operand, String description) { // 构造方法2：指定根操作数和描述。使用默认的逻辑构建器
    this(operand, RelFactories.LOGICAL_BUILDER, description); // 调用主构造方法，使用默认的逻辑构建器，使用提供的描述
  }

  /**
   * Creates a rule with an explicit description.
   *
   * @param operand     root operand, must not be null
   * @param description Description, or null to guess description
   * @param relBuilderFactory Builder for relational expressions
   */
  protected RelOptRule(RelOptRuleOperand operand, // 主构造方法：指定根操作数、关系表达式构建器工厂和描述
      RelBuilderFactory relBuilderFactory, @Nullable String description) {
    this.operand = requireNonNull(operand, "operand"); // 设置根操作数，使用 requireNonNull 确保不为 null
    this.relBuilderFactory = // 设置关系表达式构建器工厂，使用 requireNonNull 确保不为 null
        requireNonNull(relBuilderFactory, "relBuilderFactory");
    if (description == null) { // 如果描述为 null，则通过类名自动生成描述
      description = guessDescription(getClass().getName()); // 调用 guessDescription 方法从类名生成描述
    }
    if (!description.matches("[A-Za-z][-A-Za-z0-9_.(),\\[\\]\\s:]*")) { // 验证描述格式：必须以字母开头，只能包含字母、数字、下划线、点、括号、逗号、空格、冒号等
      throw new RuntimeException("Rule description '" + description // 如果描述格式不合法，抛出运行时异常
          + "' is not valid");
    }
    this.description = description; // 设置规则描述
    this.operands = flattenOperands(operand); // 将操作数树扁平化为列表，便于快速遍历和匹配
    assignSolveOrder(operands); // 为每个操作数分配求解顺序，用于优化匹配过程
  }

  //~ Methods for creating operands ------------------------------------------
  // 创建操作数的方法区域，用于构建规则匹配的模式

  /**
   * Creates an operand that matches a relational expression that has no
   * children.
   *
   * @param clazz Class of relational expression to match (must not be null)
   * @param operandList Child operands
   * @param <R> Class of relational expression to match
   * @return Operand that matches a relational expression that has no
   *   children
   *
   * @deprecated Use {@link RelRule.OperandBuilder#operand(Class)}
   */
  @Deprecated // to be removed before 2.0 // 标记为已弃用，建议使用新的 API
  public static <R extends RelNode> RelOptRuleOperand operand( // 创建操作数的静态方法1：指定类和子操作数列表
      Class<R> clazz, // 要匹配的关系表达式类（如 Filter.class, Join.class）
      RelOptRuleOperandChildren operandList) { // 子操作数列表（定义匹配的子节点结构）
    return new RelOptRuleOperand(clazz, null, r -> true, // 创建 RelOptRuleOperand 对象：指定类，不指定 trait，谓词总是返回 true
        operandList.policy, operandList.operands); // 使用提供的子操作数策略和操作数列表
  }

  /**
   * Creates an operand that matches a relational expression that has no
   * children.
   *
   * @param clazz Class of relational expression to match (must not be null)
   * @param trait Trait to match, or null to match any trait
   * @param operandList Child operands
   * @param <R> Class of relational expression to match
   * @return Operand that matches a relational expression that has no
   *   children
   *
   * @deprecated Use {@link RelRule.OperandBuilder#operand(Class)}
   */
  @Deprecated // to be removed before 2.0 // 标记为已弃用
  public static <R extends RelNode> RelOptRuleOperand operand( // 创建操作数的静态方法2：指定类、trait 和子操作数列表
      Class<R> clazz, // 要匹配的关系表达式类
      RelTrait trait, // 要匹配的 trait（如 Convention, RelCollation），null 表示匹配任意 trait
      RelOptRuleOperandChildren operandList) { // 子操作数列表
    return new RelOptRuleOperand(clazz, trait, r -> true, // 创建 RelOptRuleOperand 对象：指定类和 trait，谓词总是返回 true
        operandList.policy, operandList.operands); // 使用提供的子操作数策略和操作数列表
  }

  /**
   * Creates an operand that matches a relational expression that has a
   * particular trait and predicate.
   *
   * @param clazz Class of relational expression to match (must not be null)
   * @param trait Trait to match, or null to match any trait
   * @param predicate Additional match predicate
   * @param operandList Child operands
   * @param <R> Class of relational expression to match
   * @return Operand that matches a relational expression that has a
   *   particular trait and predicate
   *
   * @deprecated Use {@link RelRule.OperandBuilder#operand(Class)}
   */
  @Deprecated // to be removed before 2.0 // 标记为已弃用
  public static <R extends RelNode> RelOptRuleOperand operandJ( // 创建操作数的静态方法3：指定类、trait、谓词和子操作数列表（使用 Java 8 Predicate）
      Class<R> clazz, // 要匹配的关系表达式类
      RelTrait trait, // 要匹配的 trait
      Predicate<? super R> predicate, // 额外的匹配谓词，用于更精细的匹配条件（如检查特定属性）
      RelOptRuleOperandChildren operandList) { // 子操作数列表
    return new RelOptRuleOperand(clazz, trait, predicate, operandList.policy, // 创建 RelOptRuleOperand 对象，包含所有参数
        operandList.operands);
  }

  // CHECKSTYLE: IGNORE 1 // 告诉 Checkstyle 忽略这一行的检查
  /** @deprecated Use {@link #operandJ} */
  @SuppressWarnings("Guava") // 抑制 Guava 相关的警告
  @Deprecated // to be removed before 2.0 // 标记为已弃用，这是 Guava Predicate 版本的方法
  public static <R extends RelNode> RelOptRuleOperand operand( // 创建操作数的静态方法4：使用 Guava Predicate
      Class<R> clazz, // 要匹配的关系表达式类
      RelTrait trait, // 要匹配的 trait
      com.google.common.base.Predicate<? super R> predicate, // Guava 版本的谓词
      RelOptRuleOperandChildren operandList) { // 子操作数列表
    return operandJ(clazz, trait, (Predicate<? super R>) predicate::apply, // 将 Guava Predicate 转换为 Java 8 Predicate，然后调用 operandJ
        operandList);
  }

  /**
   * Creates an operand that matches a relational expression that has no
   * children.
   *
   * @param clazz Class of relational expression to match (must not be null)
   * @param trait Trait to match, or null to match any trait
   * @param predicate Additional match predicate
   * @param first First operand
   * @param rest Rest operands
   * @param <R> Class of relational expression to match
   * @return Operand
   *
   * @deprecated Use {@link RelRule.OperandBuilder#operand(Class)}
   */
  @Deprecated // to be removed before 2.0 // 标记为已弃用
  public static <R extends RelNode> RelOptRuleOperand operandJ( // 创建操作数的静态方法5：使用 some() 方法简化子操作数列表的创建
      Class<R> clazz, // 要匹配的关系表达式类
      RelTrait trait, // 要匹配的 trait
      Predicate<? super R> predicate, // 额外的匹配谓词
      RelOptRuleOperand first, // 第一个子操作数
      RelOptRuleOperand... rest) { // 其余的子操作数（可变参数）
    return operandJ(clazz, trait, predicate, some(first, rest)); // 调用 operandJ，使用 some() 方法将操作数列表转换为 RelOptRuleOperandChildren
  }

  @SuppressWarnings("Guava") // 抑制 Guava 相关的警告
  @Deprecated // to be removed before 2.0 // 标记为已弃用
  public static <R extends RelNode> RelOptRuleOperand operand( // 创建操作数的静态方法6：使用 Guava Predicate 版本
      Class<R> clazz, // 要匹配的关系表达式类
      RelTrait trait, // 要匹配的 trait
      com.google.common.base.Predicate<? super R> predicate, // Guava 版本的谓词
      RelOptRuleOperand first, // 第一个子操作数
      RelOptRuleOperand... rest) { // 其余的子操作数（可变参数）
    return operandJ(clazz, trait, (Predicate<? super R>) predicate::apply, // 将 Guava Predicate 转换为 Java 8 Predicate
        first, rest); // 调用 operandJ 方法
  }

  /**
   * Creates an operand that matches a relational expression with a given
   * list of children.
   *
   * <p>Shorthand for <code>operand(clazz, some(...))</code>.
   *
   * <p>If you wish to match a relational expression that has no children
   * (that is, a leaf node), write <code>operand(clazz, none())</code>.
   *
   * <p>If you wish to match a relational expression that has any number of
   * children, write <code>operand(clazz, any())</code>.
   *
   * @param clazz Class of relational expression to match (must not be null)
   * @param first First operand
   * @param rest Rest operands
   * @param <R> Class of relational expression to match
   * @return Operand that matches a relational expression with a given
   *   list of children
   *
   * @deprecated Use {@link RelRule.OperandBuilder#operand(Class)}
   */
  @Deprecated // to be removed before 2.0 // 标记为已弃用
  public static <R extends RelNode> RelOptRuleOperand operand( // 创建操作数的静态方法7：最常用的简化版本，自动使用 some() 包装子操作数
      Class<R> clazz, // 要匹配的关系表达式类
      RelOptRuleOperand first, // 第一个子操作数
      RelOptRuleOperand... rest) { // 其余的子操作数（可变参数）
    return operand(clazz, some(first, rest)); // 调用 operand 方法，使用 some() 创建子操作数列表
  }

  /**
   * Creates an operand for a converter rule.
   *
   * @param clazz    Class of relational expression to match (must not be null)
   * @param trait    Trait to match, or null to match any trait
   * @param predicate Predicate to apply to relational expression
   */
  @Deprecated // to be removed before 2.0 // 标记为已弃用
  protected static <R extends RelNode> ConverterRelOptRuleOperand // 创建转换规则的操作数（用于 ConverterRule）
      convertOperand(Class<R> clazz, Predicate<? super R> predicate, // 要匹配的关系表达式类和谓词
      RelTrait trait) { // 要匹配的 trait（通常是目标 trait）
    return new ConverterRelOptRuleOperand(clazz, trait, predicate); // 创建 ConverterRelOptRuleOperand 对象
  }

  // CHECKSTYLE: IGNORE 1 // 告诉 Checkstyle 忽略这一行的检查
  /** @deprecated Use {@link #convertOperand(Class, Predicate, RelTrait)}. */
  @SuppressWarnings("Guava") // 抑制 Guava 相关的警告
  @Deprecated // to be removed before 2.0 // 标记为已弃用，这是 Guava Predicate 版本的方法
  protected static <R extends RelNode> ConverterRelOptRuleOperand // 创建转换规则的操作数（Guava 版本）
      convertOperand(Class<R> clazz, // 要匹配的关系表达式类
      com.google.common.base.Predicate<? super R> predicate, // Guava 版本的谓词
      RelTrait trait) { // 要匹配的 trait
    return new ConverterRelOptRuleOperand(clazz, trait, predicate::apply); // 创建 ConverterRelOptRuleOperand 对象，转换谓词
  }

  //~ Methods for creating lists of child operands ---------------------------
  // 创建子操作数列表的方法区域，用于定义操作数的子节点匹配策略

  /**
   * Creates a list of child operands that matches child relational
   * expressions in the order they appear.
   *
   * @param first First child operand
   * @param rest  Remaining child operands (may be empty)
   * @return List of child operands that matches child relational
   *   expressions in the order
   *
   * @deprecated Use {@link RelRule.OperandDetailBuilder#inputs}
   */
  @Deprecated // to be removed before 2.0 // 标记为已弃用
  public static RelOptRuleOperandChildren some( // 创建子操作数列表的方法1：SOME 策略，表示按顺序匹配子节点
      RelOptRuleOperand first, // 第一个子操作数
      RelOptRuleOperand... rest) { // 其余的子操作数（可变参数）
    return new RelOptRuleOperandChildren(RelOptRuleOperandChildPolicy.SOME, // 创建 RelOptRuleOperandChildren 对象，使用 SOME 策略（严格按顺序匹配）
        Lists.asList(first, rest)); // 使用 Guava 的 Lists.asList 将操作数转换为列表
  }


  /**
   * Creates a list of child operands that matches child relational
   * expressions in any order.
   *
   * <p>This is useful when matching a relational expression which
   * can have a variable number of children. For example, the rule to
   * eliminate empty children of a Union would have operands
   *
   * <blockquote>Operand(Union, true, Operand(Empty))</blockquote>
   *
   * <p>and given the relational expressions
   *
   * <blockquote>Union(LogicalFilter, Empty, LogicalProject)</blockquote>
   *
   * <p>would fire the rule with arguments
   *
   * <blockquote>{Union, Empty}</blockquote>
   *
   * <p>It is up to the rule to deduce the other children, or indeed the
   * position of the matched child.
   *
   * @param first First child operand
   * @param rest  Remaining child operands (may be empty)
   * @return List of child operands that matches child relational
   *   expressions in any order
   */
  @Deprecated // to be removed before 2.0 // 标记为已弃用
  public static RelOptRuleOperandChildren unordered( // 创建子操作数列表的方法2：UNORDERED 策略，表示不按顺序匹配子节点
      RelOptRuleOperand first, // 第一个子操作数
      RelOptRuleOperand... rest) { // 其余的子操作数（可变参数）
    return new RelOptRuleOperandChildren( // 创建 RelOptRuleOperandChildren 对象
        RelOptRuleOperandChildPolicy.UNORDERED, // 使用 UNORDERED 策略（不按顺序匹配，只要包含指定的子节点即可）
        Lists.asList(first, rest)); // 使用 Guava 的 Lists.asList 将操作数转换为列表
  }

  /**
   * Creates an empty list of child operands.
   *
   * @return Empty list of child operands
   *
   * @deprecated Use {@link RelRule.OperandDetailBuilder#noInputs()}
   */
  @Deprecated // to be removed before 2.0 // 标记为已弃用
  public static RelOptRuleOperandChildren none() { // 创建子操作数列表的方法3：NONE 策略，表示没有子节点（叶子节点）
    return RelOptRuleOperandChildren.LEAF_CHILDREN; // 返回预定义的叶子节点常量
  }

  /**
   * Creates a list of child operands that signifies that the operand matches
   * any number of child relational expressions.
   *
   * @return List of child operands that signifies that the operand matches
   *   any number of child relational expressions
   *
   * @deprecated Use {@link RelRule.OperandDetailBuilder#anyInputs()}
   */
  @Deprecated // to be removed before 2.0 // 标记为已弃用
  public static RelOptRuleOperandChildren any() { // 创建子操作数列表的方法4：ANY 策略，表示匹配任意数量的子节点
    return RelOptRuleOperandChildren.ANY_CHILDREN; // 返回预定义的任意子节点常量
  }

  //~ Methods ----------------------------------------------------------------
  // 实例方法区域，提供规则的各种功能方法

  /**
   * Creates a flattened list of this operand and its descendants in prefix
   * order.
   *
   * @param rootOperand Root operand
   * @return Flattened list of operands
   */
  private List<RelOptRuleOperand> flattenOperands( // 将操作数树扁平化为列表的方法，使用前序遍历
      @UnderInitialization RelOptRule this, // 表示 this 对象尚未完全初始化（用于 CheckerFramework 的初始化检查）
      RelOptRuleOperand rootOperand) { // 根操作数
    final List<RelOptRuleOperand> operandList = new ArrayList<>(); // 创建操作数列表，用于存储扁平化后的操作数

    // Flatten the operands into a list.
    rootOperand.setRule(this); // 设置根操作数所属的规则
    rootOperand.setParent(null); // 设置根操作数的父节点为 null（因为它是根）
    rootOperand.ordinalInParent = 0; // 设置根操作数在父节点中的序号为 0
    rootOperand.ordinalInRule = operandList.size(); // 设置根操作数在规则中的序号为当前列表大小（即 0）
    operandList.add(rootOperand); // 将根操作数添加到列表中
    flattenRecurse(operandList, rootOperand); // 递归处理根操作数的所有子操作数
    return ImmutableList.copyOf(operandList); // 返回不可变的操作数列表副本
  }

  /**
   * Adds the operand and its descendants to the list in prefix order.
   *
   * @param operandList   Flattened list of operands
   * @param parentOperand Parent of this operand
   */
  private void flattenRecurse( // 递归扁平化操作数的辅助方法，使用前序遍历
      @UnderInitialization RelOptRule this, // 表示 this 对象尚未完全初始化
      List<RelOptRuleOperand> operandList, // 操作数列表（用于存储结果）
      RelOptRuleOperand parentOperand) { // 父操作数（需要处理其子操作数）
    int k = 0; // 初始化子操作数在父节点中的序号计数器
    for (RelOptRuleOperand operand : parentOperand.getChildOperands()) { // 遍历父操作数的所有子操作数
      operand.setRule(this); // 设置子操作数所属的规则
      operand.setParent(parentOperand); // 设置子操作数的父节点
      operand.ordinalInParent = k++; // 设置子操作数在父节点中的序号，并递增计数器
      operand.ordinalInRule = operandList.size(); // 设置子操作数在规则中的序号为当前列表大小
      operandList.add(operand); // 将子操作数添加到列表中
      flattenRecurse(operandList, operand); // 递归处理该子操作数的所有子操作数
    }
  }

  /**
   * Builds each operand's solve-order. Start with itself, then its parent, up
   * to the root, then the remaining operands in prefix order.
   */
  private static void assignSolveOrder(List<RelOptRuleOperand> operands) { // 为每个操作数分配求解顺序，用于优化匹配过程
    for (RelOptRuleOperand operand : operands) { // 遍历所有操作数
      operand.solveOrder = new int[operands.size()]; // 为每个操作数创建求解顺序数组，大小为操作数总数
      int m = 0; // 初始化求解顺序数组的索引
      for (RelOptRuleOperand o = operand; o != null; o = o.getParent()) { // 从当前操作数向上遍历到根节点
        operand.solveOrder[m++] = o.ordinalInRule; // 将操作数在规则中的序号添加到求解顺序数组中（从自身到根节点）
      }
      for (int k = 0; k < operands.size(); k++) { // 遍历所有操作数序号
        boolean exists = false; // 标记当前序号是否已存在于求解顺序数组中
        for (int n = 0; n < m; n++) { // 检查当前序号是否已在求解顺序数组中
          if (operand.solveOrder[n] == k) { // 如果找到相同的序号
            exists = true; // 标记为已存在
            break; // 跳出内层循环
          }
        }
        if (!exists) { // 如果当前序号不存在于求解顺序数组中
          operand.solveOrder[m++] = k; // 将其添加到求解顺序数组中
        }
      }

      // Assert: operand appears once in the sort-order.
      assert m == operands.size(); // 断言：求解顺序数组的大小等于操作数总数（确保每个操作数只出现一次）
    }
  }

  /**
   * Returns the root operand of this rule.
   *
   * @return the root operand of this rule
   */
  public RelOptRuleOperand getOperand() { // 获取规则的根操作数
    return operand; // 返回根操作数
  }

  /**
   * Returns a flattened list of operands of this rule.
   *
   * @return flattened list of operands
   */
  public List<RelOptRuleOperand> getOperands() { // 获取规则的所有操作数（扁平化列表）
    return ImmutableList.copyOf(operands); // 返回不可变的操作数列表副本
  }

  @Override public int hashCode() { // 重写 hashCode 方法，用于哈希表等数据结构
    // Conventionally, hashCode() and equals() should use the same
    // criteria, whereas here we only look at the description. This is
    // okay, because the planner requires all rule instances to have
    // distinct descriptions.
    return description.hashCode(); // 仅使用描述的哈希码，因为优化器要求所有规则实例必须有唯一的描述
  }

  @Override public boolean equals(@Nullable Object obj) { // 重写 equals 方法，用于比较两个规则是否相等
    return (obj instanceof RelOptRule) // 首先检查 obj 是否是 RelOptRule 的实例
        && equals((RelOptRule) obj); // 如果是，调用重载的 equals 方法进行比较
  }

  /**
   * Returns whether this rule is equal to another rule.
   *
   * <p>The base implementation checks that the rules have the same class and
   * that the operands are equal; derived classes can override.
   *
   * @param that Another rule
   * @return Whether this rule is equal to another rule
   */
  @SuppressWarnings("NonOverridingEquals") // 抑制警告：这个 equals 方法不是重写 Object.equals
  protected boolean equals(RelOptRule that) { // 重载的 equals 方法，用于比较两个规则是否相等
    // Include operands and class in the equality criteria just in case
    // they have chosen a poor description.
    return this == that // 如果是同一个对象引用，直接返回 true
        || this.getClass() == that.getClass() // 检查两个规则的类是否相同
        && this.description.equals(that.description) // 检查描述是否相同
        && this.operand.equals(that.operand); // 检查操作数是否相同（以防描述选择不当）
  }

  /**
   * Returns whether this rule could possibly match the given operands.
   *
   * <p>This method is an opportunity to apply side-conditions to a rule. The
   * {@link RelOptPlanner} calls this method after matching all operands of
   * the rule, and before calling {@link #onMatch(RelOptRuleCall)}.
   *
   * <p>In implementations of {@link RelOptPlanner} which may queue up a
   * matched {@link RelOptRuleCall} for a long time before calling
   * {@link #onMatch(RelOptRuleCall)}, this method is beneficial because it
   * allows the planner to discard rules earlier in the process.
   *
   * <p>The default implementation of this method returns <code>true</code>.
   * It is acceptable for any implementation of this method to give a false
   * positives, that is, to say that the rule matches the operands but have
   * {@link #onMatch(RelOptRuleCall)} subsequently not generate any
   * successors.
   *
   * <p>The following script is useful to identify rules which commonly
   * produce no successors. You should override this method for these rules:
   *
   * <blockquote>
   * <pre><code>awk '
   * /Apply rule/ {rule=$4; ruleCount[rule]++;}
   * /generated 0 successors/ {ruleMiss[rule]++;}
   * END {
   *   printf "%-30s %s %s\n", "Rule", "Fire", "Miss";
   *   for (i in ruleCount) {
   *     printf "%-30s %5d %5d\n", i, ruleCount[i], ruleMiss[i];
   *   }
   * } ' FarragoTrace.log</code></pre>
   * </blockquote>
   *
   * @param call Rule call which has been determined to match all operands of
   *             this rule
   * @return whether this RelOptRule matches a given RelOptRuleCall
   */
  public boolean matches(RelOptRuleCall call) { // 检查规则是否匹配给定的规则调用（额外的条件检查）
    return true; // 默认实现返回 true，表示总是匹配。子类可以重写此方法以添加额外的匹配条件
  }

  /**
   * Receives notification about a rule match. At the time that this method is
   * called, {@link RelOptRuleCall#rels call.rels} holds the set of relational
   * expressions which match the operands to the rule; <code>
   * call.rels[0]</code> is the root expression.
   *
   * <p>Typically a rule would check that the nodes are valid matches, creates
   * a new expression, then calls back {@link RelOptRuleCall#transformTo} to
   * register the expression.
   *
   * @param call Rule call
   * @see #matches(RelOptRuleCall)
   */
  public abstract void onMatch(RelOptRuleCall call); // 抽象方法：当规则匹配成功时被调用。子类必须实现此方法来定义规则的转换逻辑

  /**
   * Returns the convention of the result of firing this rule, null if
   * not known.
   *
   * @return Convention of the result of firing this rule, null if
   *   not known
   */
  public @Nullable Convention getOutConvention() { // 获取规则执行后的约定（Convention），如果未知则返回 null
    return null; // 默认实现返回 null，子类可以重写此方法以返回具体的约定
  }

  /**
   * Returns the trait which will be modified as a result of firing this rule,
   * or null if the rule is not a converter rule.
   *
   * @return Trait which will be modified as a result of firing this rule,
   *   or null if the rule is not a converter rule
   */
  public @Nullable RelTrait getOutTrait() { // 获取规则执行后将要修改的 trait，如果不是转换规则则返回 null
    return null; // 默认实现返回 null，子类可以重写此方法以返回具体的 trait
  }

  /**
   * Returns the description of this rule.
   *
   * <p>It must be unique (for rules that are not equal) and must consist of
   * only the characters A-Z, a-z, 0-9, '_', '.', '(', ')', '-', ',', '[', ']', ':', ' '.
   * It must start with a letter. */
  @Override public final String toString() { // 重写 toString 方法，返回规则的描述
    return description; // 返回规则的描述字符串
  }

  /**
   * Converts a relation expression to a given set of traits, if it does not
   * already have those traits.
   *
   * @param rel      Relational expression to convert
   * @param toTraits desired traits
   * @return a relational expression with the desired traits; never null
   */
  public static RelNode convert(RelNode rel, RelTraitSet toTraits) { // 转换关系表达式到指定的 trait 集合（静态方法1）
    return convert(rel.getCluster().getPlanner(), rel, toTraits); // 调用重载的 convert 方法，从关系表达式中获取优化器
  }

  public static RelNode convert(RelOptPlanner planner, RelNode rel, RelTraitSet toTraits) { // 转换关系表达式到指定的 trait 集合（静态方法2）
    RelTraitSet outTraits = rel.getTraitSet(); // 获取当前关系表达式的 trait 集合
    for (int i = 0; i < toTraits.size(); i++) { // 遍历目标 trait 集合中的每个 trait
      RelTrait toTrait = toTraits.getTrait(i); // 获取索引 i 处的 trait
      if (toTrait != null) { // 如果 trait 不为 null
        outTraits = outTraits.replace(i, toTrait); // 将当前 trait 集合中索引 i 处的 trait 替换为目标 trait
      }
    }

    if (rel.getTraitSet().matches(outTraits)) { // 如果当前关系表达式的 trait 集合已经匹配目标 trait 集合
      return rel; // 直接返回原关系表达式（不需要转换）
    }

    return planner.changeTraits(rel, outTraits); // 否则，调用优化器的 changeTraits 方法进行转换
  }

  /**
   * Converts one trait of a relational expression, if it does not
   * already have that trait.
   *
   * @param rel      Relational expression to convert
   * @param toTrait  Desired trait
   * @return a relational expression with the desired trait; never null
   */
  public static RelNode convert(RelNode rel, @Nullable RelTrait toTrait) { // 转换关系表达式到指定的单个 trait（静态方法3）
    return convert(rel.getCluster().getPlanner(), rel, toTrait); // 调用重载的 convert 方法，从关系表达式中获取优化器
  }

  public static RelNode convert(RelOptPlanner planner, RelNode rel, @Nullable RelTrait toTrait) { // 转换关系表达式到指定的单个 trait（静态方法4）
    RelTraitSet outTraits = rel.getTraitSet(); // 获取当前关系表达式的 trait 集合
    if (toTrait != null) { // 如果目标 trait 不为 null
      outTraits = outTraits.replace(toTrait); // 将当前 trait 集合中对应类型的 trait 替换为目标 trait
    }

    if (rel.getTraitSet().matches(outTraits)) { // 如果当前关系表达式的 trait 集合已经匹配目标 trait 集合
      return rel; // 直接返回原关系表达式（不需要转换）
    }

    return planner.changeTraits(rel, outTraits.simplify()); // 否则，调用优化器的 changeTraits 方法进行转换，并简化 trait 集合
  }

  /**
   * Converts a list of relational expressions.
   *
   * @param rels     Relational expressions
   * @param trait   Trait to add to each relational expression
   * @return List of converted relational expressions, never null
   */
  protected static List<RelNode> convertList(List<RelNode> rels, // 转换关系表达式列表中的每个表达式到指定的 trait
      final RelTrait trait) { // 要添加到每个关系表达式的 trait
    return Util.transform(rels, // 使用 Util.transform 工具方法转换列表中的每个元素
        rel -> convert(rel, rel.getTraitSet().replace(trait))); // 对每个关系表达式调用 convert 方法，将 trait 添加到其 trait 集合中
  }

  /**
   * Deduces a name for a rule by taking the name of its class and returning
   * the segment after the last '.' or '$'.
   *
   * <p>Examples:
   * <ul>
   * <li>"com.foo.Bar" yields "Bar";</li>
   * <li>"com.flatten.Bar$Baz" yields "Baz";</li>
   * <li>"com.foo.Bar$1" yields "1" (which as an integer is an invalid
   * name, and writer of the rule is encouraged to give it an
   * explicit name).</li>
   * </ul>
   *
   * @param className Name of the rule's class
   * @return Last segment of the class
   */
  static String guessDescription(String className) { // 从类名推断规则的描述名称
    String description = className; // 初始化描述为完整的类名
    int punc = // 找到最后一个分隔符的位置（可以是 '.' 或 '$'）
        Math.max( // 取最大值
            className.lastIndexOf('.'), // 查找最后一个 '.' 的位置
            className.lastIndexOf('$')); // 查找最后一个 '$' 的位置（用于内部类）
    if (punc >= 0) { // 如果找到了分隔符
      description = className.substring(punc + 1); // 提取分隔符之后的部分作为描述
    }
    if (description.matches("[0-9]+")) { // 如果描述只包含数字（如匿名内部类 "Bar$1"）
      throw new RuntimeException("Derived description of rule class " // 抛出运行时异常，提示描述无效
          + className + " is an integer, not valid. " // 要求手动提供描述
          + "Supply a description manually.");
    }
    return description; // 返回推断的描述名称
  }

  /**
   * Operand to an instance of the converter rule.
   */
  protected static class ConverterRelOptRuleOperand extends RelOptRuleOperand { // 内部类：转换规则的操作数，继承自 RelOptRuleOperand
    <R extends RelNode> ConverterRelOptRuleOperand(Class<R> clazz, RelTrait in, // 构造方法：创建转换规则的操作数
        Predicate<? super R> predicate) { // 参数：要匹配的关系表达式类、输入 trait、匹配谓词
      super(clazz, in, predicate, RelOptRuleOperandChildPolicy.ANY, // 调用父类构造方法，使用 ANY 子操作数策略（匹配任意数量的子节点）
          ImmutableList.of()); // 子操作数列表为空（转换规则通常不需要指定子操作数）
    }

    @Override public boolean matches(RelNode rel) { // 重写 matches 方法，检查关系表达式是否匹配此操作数
      // Don't apply converters to converters that operate
      // on the same RelTraitDef -- otherwise we get
      // an n^2 effect.
      if (rel instanceof Converter) { // 如果关系表达式是 Converter 类型（即已经是一个转换节点）
        if (((ConverterRule) getRule()).getTraitDef() // 检查当前规则的 trait 定义
            == ((Converter) rel).getTraitDef()) { // 是否与关系表达式的 trait 定义相同
          return false; // 如果相同，返回 false（避免对相同 trait 定义进行重复转换，防止 n^2 效应）
        }
      }
      return super.matches(rel); // 否则，调用父类的 matches 方法进行常规匹配检查
    }
  }
}
