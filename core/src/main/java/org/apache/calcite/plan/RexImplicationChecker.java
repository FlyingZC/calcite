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
package org.apache.calcite.plan; // 声明包名，该类属于org.apache.calcite.plan包，是Calcite查询优化器中的计划相关包

import org.apache.calcite.DataContext; // 导入DataContext接口，用于在表达式执行时提供数据上下文，包含输入变量的值
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型，描述表或表达式的类型信息
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，用于构建RexNode表达式树的构建器
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示函数调用表达式，如x > 10、a AND b等
import org.apache.calcite.rex.RexExecutable; // 导入RexExecutable接口，表示可执行的Rex表达式，可以编译并执行表达式
import org.apache.calcite.rex.RexExecutor; // 导入RexExecutor接口，用于执行Rex表达式并返回结果的执行器
import org.apache.calcite.rex.RexExecutorImpl; // 导入RexExecutorImpl类，RexExecutor接口的默认实现
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类，表示对输入字段的引用，如x、y等变量
import org.apache.calcite.rex.RexNode; // 导入RexNode类，所有行表达式的基类，是表达式树的节点
import org.apache.calcite.rex.RexUtil; // 导入RexUtil类，提供Rex表达式相关的工具方法
import org.apache.calcite.rex.RexVisitorImpl; // 导入RexVisitorImpl类，RexNode访问者模式的默认实现，用于遍历表达式树
import org.apache.calcite.runtime.PairList; // 导入PairList类，表示键值对的列表，用于存储操作符和操作数的配对
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举，定义SQL操作符的种类，如>、<、=、AND、OR等
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，表示SQL操作符，如>、<、=等
import org.apache.calcite.util.Pair; // 导入Pair类，表示不可变的键值对
import org.apache.calcite.util.trace.CalciteLogger; // 导入CalciteLogger类，Calcite框架专用的日志记录器

import com.google.common.collect.ImmutableList; // 导入ImmutableList类，Google Guava库提供的不可变列表实现
import com.google.common.collect.ImmutableSet; // 导入ImmutableSet类，Google Guava库提供的不可变集合实现
import com.google.common.collect.Sets; // 导入Sets类，Google Guava库提供的集合工具类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值
import org.slf4j.LoggerFactory; // 导入LoggerFactory类，SLF4J日志框架的工厂类，用于创建Logger实例

import java.util.HashMap; // 导入HashMap类，Java集合框架的哈希映射实现
import java.util.List; // 导入List接口，Java集合框架的列表接口
import java.util.Map; // 导入Map接口，Java集合框架的映射接口
import java.util.Set; // 导入Set接口，Java集合框架的集合接口

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于检查对象是否为null

/**
 * 检查一个条件是否逻辑上蕴含另一个条件。
 *
 * <p>如果 A ⇒ B，那么每当A为真时，B也必须为真。
 * 蕴含关系是逻辑学中的重要概念，在查询优化中用于简化条件、消除冗余谓词等。
 *
 * <p>例如：
 * <ul>
 * <li>(x > 10) ⇒ (x > 5) - 如果x大于10，那么x肯定大于5，蕴含成立
 * <li>(x = 10) ⇒ (x < 30 OR y > 30) - 如果x等于10，那么x<30为真，整个OR表达式为真
 * <li>(x = 10) ⇒ (x IS NOT NULL) - 如果x等于10，那么x肯定不为null
 * <li>(x > 10 AND y = 20) ⇒ (x > 5) - 如果x>10且y=20，那么x肯定>5
 * </ul>
 *
 * 这个类在查询优化器中非常重要，用于：
 * 1. 谓词下推时判断哪些条件可以下推
 * 2. 消除冗余条件，减少计算开销
 * 3. 判断查询等价性，用于查询重写
 */
public class RexImplicationChecker { // 类声明：RexImplicationChecker类，用于检查条件之间的蕴含关系
  private static final CalciteLogger LOGGER = // 声明静态常量LOGGER，用于日志记录
      new CalciteLogger(LoggerFactory.getLogger(RexImplicationChecker.class)); // 创建CalciteLogger实例，传入RexImplicationChecker类的Logger

  final RexBuilder builder; // 声明final成员变量builder：Rex表达式构建器，用于创建新的RexNode表达式
  final RexExecutor executor; // 声明final成员变量executor：Rex表达式执行器，用于执行表达式并获取结果
  final RelDataType rowType; // 声明final成员变量rowType：行类型，描述输入数据的类型信息，包含字段名和类型

  public RexImplicationChecker( // 构造方法声明：创建RexImplicationChecker实例
      RexBuilder builder, // 参数builder：Rex表达式构建器，用于构建表达式
      RexExecutor executor, // 参数executor：Rex表达式执行器，用于执行表达式
      RelDataType rowType) { // 参数rowType：行类型，描述输入数据的类型
    this.builder = requireNonNull(builder, "builder"); // 将builder参数赋值给成员变量，并检查不为null
    this.executor = requireNonNull(executor, "executor"); // 将executor参数赋值给成员变量，并检查不为null
    this.rowType = requireNonNull(rowType, "rowType"); // 将rowType参数赋值给成员变量，并检查不为null
  }

  /**
   * 检查第一个条件是否蕴含（⇒）第二个条件。
   *
   * <p>这个问题可以归约为SAT（可满足性）问题，这是一个NP完全问题。
   * 当该方法返回true时，表示first确实蕴含second，这是确定的结论。
   * 但当返回false时，并不一定表示first不蕴含second，可能只是无法证明。
   * 这是因为蕴含检查是NP完全问题，我们采用启发式方法，无法保证在所有情况下都能给出确定答案。
   *
   * @param first 第一个条件（前提条件）
   * @param second 第二个条件（结论条件）
   * @return 如果能证明first ⇒ second则返回true；否则返回false，即无法确定蕴含关系是否成立
   */
  public boolean implies(RexNode first, RexNode second) { // 方法声明：检查first是否蕴含second
    // 验证输入参数是否有效
    if (!validate(first, second)) { // 调用validate方法验证输入，如果验证失败
      return false; // 返回false，表示无法证明蕴含关系
    }

    LOGGER.debug("Checking if {} => {}", first.toString(), second.toString()); // 记录调试日志，输出正在检查的两个条件

    // 将两个条件转换为析取范式（DNF）
    // DNF是OR of ANDs的形式，如：(A AND B) OR (C AND D)
    RexNode firstDnf = RexUtil.toDnf(builder, first); // 将第一个条件转换为DNF形式
    RexNode secondDnf = RexUtil.toDnf(builder, second); // 将第二个条件转换为DNF形式

    // 检查简单情况：如果第一个条件恒假或第二个条件恒真，则蕴含必然成立
    // 假蕴含任何条件都为真（因为前提永远不会满足，蕴含空真）
    // 任何条件都蕴含真
    if (firstDnf.isAlwaysFalse() // 如果第一个DNF恒假
        || secondDnf.isAlwaysTrue()) { // 或者第二个DNF恒真
      return true; // 返回true，蕴含成立
    }

    // 将DNF分解为条件列表，每个条件都是一个合取式（AND）
    // 例如：
    //   (x > 10 AND y > 30) OR (z > 90)
    // 被转换为包含2个条件的列表：
    //   (x > 10 AND y > 30)
    //   z > 90
    //
    // 类似地，将CNF分解为条件列表，每个条件都是一个析取式（OR）
    List<RexNode> firsts = RelOptUtil.disjunctions(firstDnf); // 将第一个DNF分解为析取项列表（每个析取项是合取式）
    List<RexNode> seconds = RelOptUtil.disjunctions(secondDnf); // 将第二个DNF分解为析取项列表（每个析取项是合取式）

    for (RexNode f : firsts) { // 遍历第一个条件的每个析取项（合取式）
      // 检查f是否至少蕴含seconds列表中的一个合取式
      // 如果f连seconds中的一个合取式都无法蕴含，那么最终蕴含关系可能为假
      // 因为DNF是OR关系，只要有一个析取项蕴含second，整体就蕴含
      if (!impliesAny(f, seconds)) { // 调用impliesAny方法检查f是否蕴含seconds中的任意一个
        LOGGER.debug("{} does not imply {}", first, second); // 记录调试日志，表示蕴含不成立
        return false; // 返回false，蕴含关系不成立
      }
    }

    LOGGER.debug("{} implies {}", first, second); // 记录调试日志，表示蕴含成立
    return true; // 返回true，蕴含关系成立
  }

  /** 返回谓词 {@code first} 是否蕴含（⇒）{@code seconds} 列表中的至少一个谓词。 */
  private boolean impliesAny(RexNode first, List<RexNode> seconds) { // 方法声明：检查first是否蕴含seconds中的任意一个
    for (RexNode second : seconds) { // 遍历seconds列表中的每个条件
      if (impliesConjunction(first, second)) { // 调用impliesConjunction方法检查first是否蕴含second
        return true; // 如果找到一个蕴含关系，立即返回true
      }
    }
    return false; // 遍历完所有条件都没有找到蕴含关系，返回false
  }

  /** 返回谓词 {@code first} 是否蕴含 {@code second}（两者都可能是合取式）。 */
  private boolean impliesConjunction(RexNode first, RexNode second) { // 方法声明：检查两个合取式之间的蕴含关系
    if (implies2(first, second)) { // 首先尝试直接检查蕴含关系
      return true; // 如果直接检查成功，返回true
    }
    switch (first.getKind()) { // 根据first的类型进行处理
    case AND: // 如果first是AND操作
      for (RexNode f : RelOptUtil.conjunctions(first)) { // 遍历AND的每个子条件
        if (implies2(f, second)) { // 检查任意一个子条件是否蕴含second
          return true; // 如果找到一个子条件蕴含second，返回true
        }
      }
      break; // 跳出switch
    default: // 其他情况
      break; // 跳出switch
    }
    return false; // 所有检查都失败，返回false
  }

  /** 返回谓词 {@code first}（非合取式）是否蕴含 {@code second}。 */
  private boolean implies2(RexNode first, RexNode second) { // 方法声明：检查单个条件之间的蕴含关系
    if (second.isAlwaysFalse()) { // 如果second恒假
      return false; // first不可能蕴含恒假的条件（除非first也恒假，但这种情况已在前面处理）
    }

    // 特殊情况：如果两个条件完全相同，则蕴含成立
    // 例如："x is null" 蕴含 "x is null"
    if (first.equals(second)) { // 检查两个条件是否相等
      return true; // 相等则蕴含成立
    }

    // 特殊情况：多个条件可以蕴含 "IS NOT NULL"
    switch (second.getKind()) { // 根据second的类型进行处理
    case IS_NOT_NULL: // 如果second是IS_NOT_NULL操作
      // 假设我们知道first在second中是强的（strong），即：
      // 如果second为null，那么first也会为null。
      // 那么，first不为null就蕴含second不为null。
      //
      // 例如，first是"x > y"，second是"x"。
      // 如果我们知道"x > y"不为null，我们就可以推断"x"不为null。
      final RexNode operand = ((RexCall) second).getOperands().get(0); // 获取IS_NOT_NULL的操作数
      final Strong strong = new Strong() { // 创建匿名内部类Strong的实例
        @Override public boolean isNull(RexNode node) { // 重写isNull方法
          return node.equals(operand) // 如果节点等于操作数
              || super.isNull(node); // 或者父类的isNull方法返回true
        }
      };
      if (strong.isNull(first)) { // 检查first在second中是否为强依赖
        return true; // 如果是强依赖，蕴含成立
      }
      break; // 跳出switch
    default: // 其他情况
      break; // 跳出switch
    }

    // 创建输入使用查找器，用于分析表达式中变量的使用情况
    final InputUsageFinder firstUsageFinder = new InputUsageFinder(); // 创建第一个条件的输入使用查找器
    final InputUsageFinder secondUsageFinder = new InputUsageFinder(); // 创建第二个条件的输入使用查找器

    // 应用查找器到两个条件，收集变量的使用信息
    RexUtil.apply(firstUsageFinder, ImmutableList.of(), first); // 应用firstUsageFinder到first条件
    RexUtil.apply(secondUsageFinder, ImmutableList.of(), second); // 应用secondUsageFinder到second条件

    // 检查是否支持这种表达式模式
    if (!checkSupport(firstUsageFinder, secondUsageFinder)) { // 调用checkSupport方法检查支持情况
      LOGGER.warn("Support for checking {} => {} is not there", first, second); // 记录警告日志，表示不支持这种检查
      return false; // 返回false，表示无法检查
    }

    // 构建使用情况的笛卡尔积
    // 对于每个变量，可能有多个使用方式，我们需要考虑所有可能的组合
    ImmutableList.Builder<Set<Pair<RexInputRef, @Nullable RexNode>>> usagesBuilder = // 创建列表构建器
        ImmutableList.builder(); // 初始化构建器
    for (Map.Entry<RexInputRef, InputRefUsage<SqlOperator, @Nullable RexNode>> entry // 遍历firstUsageFinder的使用映射
        : firstUsageFinder.usageMap.entrySet()) { // 获取每个输入引用的使用信息
      ImmutableSet.Builder<Pair<RexInputRef, @Nullable RexNode>> usageBuilder = // 创建集合构建器
          ImmutableSet.builder(); // 初始化构建器
      if (!entry.getValue().usageList.isEmpty()) { // 如果使用列表不为空
        entry.getValue().usageList.rightList().forEach(v -> // 遍历使用列表的右侧（字面量）
            usageBuilder.add(Pair.of(entry.getKey(), v))); // 将输入引用和字面量的配对添加到集合中
        usagesBuilder.add(usageBuilder.build()); // 将构建的集合添加到列表中
      }
    }

    // 计算所有使用情况的笛卡尔积
    // 这样可以覆盖所有可能的变量取值组合
    final Set<List<Pair<RexInputRef, @Nullable RexNode>>> usages = // 声明笛卡尔积结果
        Sets.cartesianProduct(usagesBuilder.build()); // 计算笛卡尔积

    for (List<Pair<RexInputRef, @Nullable RexNode>> usageList : usages) { // 遍历所有使用情况组合
      // 从第一个合取式中获取字面量值，并使用它们执行第二个合取式
      //
      // 例如，对于：
      //   x > 30 ⇒ x > 10
      // 我们将在第二个表达式中用30替换x并执行它，即：
      //   30 > 10
      //
      // 如果结果为true，我们就可以推断蕴含关系成立
      final DataContext dataValues = // 创建数据上下文
          VisitorDataContext.of(rowType, usageList); // 从行类型和使用列表创建数据上下文

      if (!isSatisfiable(second, dataValues)) { // 检查第二个条件在给定数据值下是否可满足
        return false; // 如果不可满足，蕴含关系不成立
      }
    }

    return true; // 所有情况都检查通过，蕴含关系成立
  }

  /** 检查给定数据值下，条件是否可满足（即是否为true）。 */
  private boolean isSatisfiable(RexNode second, @Nullable DataContext dataValues) { // 方法声明：检查条件的可满足性
    if (dataValues == null) { // 如果数据上下文为null
      return false; // 返回false，表示不可满足
    }

    ImmutableList<RexNode> constExps = ImmutableList.of(second); // 将条件包装为不可变列表
    final RexExecutable exec = RexExecutorImpl.getExecutable(builder, constExps, rowType); // 获取可执行的表达式对象

    @Nullable Object[] result; // 声明结果数组
    exec.setDataContext(dataValues); // 设置执行器的数据上下文
    try { // 开始异常处理
      result = exec.execute(); // 执行表达式并获取结果
    } catch (Exception e) { // 捕获执行异常
      // TODO: checkSupport方法应该避免抛出这种异常
      // 需要监控并处理所有引发异常的情况
      LOGGER.warn("Exception thrown while checking if => {}: {}", second, e.getMessage()); // 记录警告日志
      return false; // 返回false，表示检查失败
    }
    return result != null // 检查结果不为null
        && result.length == 1 // 检查结果数组长度为1
        && result[0] instanceof Boolean // 检查第一个元素是Boolean类型
        && (Boolean) result[0]; // 检查布尔值为true
  }

  /**
   * 通过检查first和second合取式中变量的使用情况，决定这种表达式模式
   * 当前是否支持用于证明first蕴含second。
   *
   * <ol>
   * <li>变量在两个合取式中应该只使用一次，并且只能使用以下操作符：
   * >、<、≤、≥、=、≠。
   *
   * <li>第二个条件中使用的所有变量都应该在第一个条件中使用。
   *
   * <li>如果变量在first中使用的操作符是op1，在second中是op2，那么我们支持以下合取式组合(op1, op2)，
   * op1和op2必须属于以下集合之一：
   *
   * <ul>
   *    <li>(<, ≤) × (<, ≤) <i>注意：×表示笛卡尔积</i>
   *    <li>(> / ≥) × (>, ≥)
   *    <li>(=) × (>, ≥, <, ≤, =, ≠)
   *    <li>(≠, =)
   * </ul>
   *
   * <li>我们支持在first和second使用中，每个变量最多使用2个操作符。
   *
   * </ol>
   *
   * @return 如果输入使用模式被支持则返回true
   */
  private static boolean checkSupport(InputUsageFinder firstUsageFinder, // 方法声明：检查表达式模式是否被支持
      InputUsageFinder secondUsageFinder) { // 参数：第二个条件的输入使用查找器
    final Map<RexInputRef, InputRefUsage<SqlOperator, @Nullable RexNode>> firstUsageMap = // 获取第一个条件的使用映射
        firstUsageFinder.usageMap; // 从查找器中获取使用映射
    final Map<RexInputRef, InputRefUsage<SqlOperator, @Nullable RexNode>> secondUsageMap = // 获取第二个条件的使用映射
        secondUsageFinder.usageMap; // 从查找器中获取使用映射

    for (Map.Entry<RexInputRef, InputRefUsage<SqlOperator, @Nullable RexNode>> entry // 遍历第二个条件的使用映射
        : secondUsageMap.entrySet()) { // 获取每个条目
      final InputRefUsage<SqlOperator, @Nullable RexNode> secondUsage = // 获取第二个条件的使用信息
          entry.getValue(); // 从条目中获取值
      final PairList<SqlOperator, @Nullable RexNode> secondUsageList = // 获取第二个条件的使用列表
          secondUsage.usageList; // 从使用信息中获取使用列表
      final int secondLen = secondUsageList.size(); // 获取第二个条件使用列表的长度

      // 检查使用次数是否匹配，并且不超过2次
      if (secondUsage.usageCount != secondLen || secondLen > 2) { // 如果使用次数不匹配或超过2次
        return false; // 返回false，不支持
      }

      final InputRefUsage<SqlOperator, @Nullable RexNode> firstUsage = // 获取第一个条件对应变量的使用信息
          firstUsageMap.get(entry.getKey()); // 从第一个条件的使用映射中查找

      // 检查第一个条件中是否使用了该变量，并且使用次数不超过2次
      if (firstUsage == null // 如果第一个条件中没有使用该变量
          || firstUsage.usageList.size() != firstUsage.usageCount // 或者使用次数不匹配
          || firstUsage.usageCount > 2) { // 或者使用次数超过2次
        return false; // 返回false，不支持
      }

      final PairList<SqlOperator, @Nullable RexNode> firstUsageList = // 获取第一个条件的使用列表
          firstUsage.usageList; // 从使用信息中获取
      final int firstLen = firstUsageList.size(); // 获取第一个条件使用列表的长度

      // 获取操作符的类型
      final SqlKind fKind = firstUsageList.get(0).getKey().getKind(); // 获取第一个条件第一个操作符的类型
      final SqlKind sKind = secondUsageList.get(0).getKey().getKind(); // 获取第二个条件第一个操作符的类型
      final SqlKind fKind2 = // 获取第一个条件第二个操作符的类型（如果存在）
          firstLen == 2 ? firstUsageList.get(1).getKey().getKind() : null; // 如果长度为2则获取，否则为null
      final SqlKind sKind2 = // 获取第二个条件第二个操作符的类型（如果存在）
          secondLen == 2 ? secondUsageList.get(1).getKey().getKind() : null; // 如果长度为2则获取，否则为null

      // 检查两个条件都使用2个操作符的情况
      // 注意：isEquivalentOp的参数永远不会为null，但是checker-framework的数据流不够强，
      // 所以第一个参数被标记为nullable
      //noinspection ConstantConditions
      if (firstLen == 2 && secondLen == 2 // 如果两个条件都使用2个操作符
          && fKind2 != null && sKind2 != null // 并且第二个操作符都存在
          && !(isEquivalentOp(fKind, sKind) && isEquivalentOp(fKind2, sKind2)) // 并且不是等价操作符对
          && !(isEquivalentOp(fKind, sKind2) && isEquivalentOp(fKind2, sKind))) { // 并且不是交叉等价操作符对
        return false; // 返回false，不支持
      } else if (firstLen == 1 && secondLen == 1 // 如果两个条件都使用1个操作符
          && fKind != SqlKind.EQUALS && !isSupportedUnaryOperators(sKind) // 并且first不是等号，second不支持一元操作符
          && !isEquivalentOp(fKind, sKind)) { // 并且操作符不等价
        return false; // 返回false，不支持
      } else if (firstLen == 1 && secondLen == 2 && fKind != SqlKind.EQUALS) { // 如果first使用1个操作符，second使用2个，且first不是等号
        return false; // 返回false，不支持
      } else if (firstLen == 2 && secondLen == 1) { // 如果first使用2个操作符，second使用1个
        // 只允许以下情况：
        // x < 30 and x < 40 implies x < 70
        // x > 30 and x < 40 implies x < 70
        // 但不允许以下情况：
        // x > 30 and x > 40 implies x < 70
        //noinspection ConstantConditions
        if (fKind2 != null && !isOppositeOp(fKind, fKind2) && !isSupportedUnaryOperators(sKind) // 如果操作符不是相反的，且second不支持一元操作符
            && !(isEquivalentOp(fKind, fKind2) && isEquivalentOp(fKind, sKind))) { // 并且不是等价操作符对
          return false; // 返回false，不支持
        }
      }
    }

    return true; // 所有检查都通过，支持这种表达式模式
  }

  /** 检查操作符是否是支持的一元操作符。 */
  private static boolean isSupportedUnaryOperators(SqlKind kind) { // 方法声明：检查是否支持的一元操作符
    switch (kind) { // 根据操作符类型
    case IS_NOT_NULL: // 如果是IS_NOT_NULL
    case IS_NULL: // 或者是IS_NULL
      return true; // 返回true，支持
    default: // 其他情况
      return false; // 返回false，不支持
    }
  }

  /** 检查两个操作符是否等价（可以互相替换）。 */
  private static boolean isEquivalentOp(@Nullable SqlKind fKind, SqlKind sKind) { // 方法声明：检查操作符是否等价
    switch (sKind) { // 根据第二个操作符的类型
    case GREATER_THAN: // 如果是大于号
    case GREATER_THAN_OR_EQUAL: // 或者是大于等于号
      if (!(fKind == SqlKind.GREATER_THAN) // 检查第一个操作符是否是大于号
          && !(fKind == SqlKind.GREATER_THAN_OR_EQUAL)) { // 或者大于等于号
        return false; // 如果都不是，返回false，不等价
      }
      break; // 跳出switch
    case LESS_THAN: // 如果是小于号
    case LESS_THAN_OR_EQUAL: // 或者是小于等于号
      if (!(fKind == SqlKind.LESS_THAN) // 检查第一个操作符是否是小于号
          && !(fKind == SqlKind.LESS_THAN_OR_EQUAL)) { // 或者小于等于号
        return false; // 如果都不是，返回false，不等价
      }
      break; // 跳出switch
    default: // 其他情况
      return false; // 返回false，不等价
    }

    return true; // 检查通过，返回true，等价
  }

  /** 检查两个操作符是否相反（如>和<）。 */
  private static boolean isOppositeOp(SqlKind fKind, SqlKind sKind) { // 方法声明：检查操作符是否相反
    switch (sKind) { // 根据第二个操作符的类型
    case GREATER_THAN: // 如果是大于号
    case GREATER_THAN_OR_EQUAL: // 或者是大于等于号
      if (!(fKind == SqlKind.LESS_THAN) // 检查第一个操作符是否是小于号
          && !(fKind == SqlKind.LESS_THAN_OR_EQUAL)) { // 或者小于等于号
        return false; // 如果都不是，返回false，不相反
      }
      break; // 跳出switch
    case LESS_THAN: // 如果是小于号
    case LESS_THAN_OR_EQUAL: // 或者是小于等于号
      if (!(fKind == SqlKind.GREATER_THAN) // 检查第一个操作符是否是大于号
          && !(fKind == SqlKind.GREATER_THAN_OR_EQUAL)) { // 或者大于等于号
        return false; // 如果都不是，返回false，不相反
      }
      break; // 跳出switch
    default: // 其他情况
      return false; // 返回false，不相反
    }
    return true; // 检查通过，返回true，相反
  }

  /** 验证输入参数是否有效。 */
  private static boolean validate(RexNode first, RexNode second) { // 方法声明：验证输入参数
    return first instanceof RexCall && second instanceof RexCall; // 检查两个参数都是RexCall类型（函数调用表达式）
  }

  /**
   * 访问者类，用于构建表达式中输入变量的使用映射。
   *
   * <p>例如：对于 x > 10 AND y < 20 AND x = 40，使用映射如下：
   * <ul>
   * <li>key: x value: {(>, 10),(=, 40), usageCount = 2}
   * <li>key: y value: {(>, 20), usageCount = 1}
   * </ul>
   * 这个映射记录了每个变量在表达式中是如何使用的，包括使用的操作符和字面量值。
   */
  private static class InputUsageFinder extends RexVisitorImpl<Void> { // 内部类声明：输入使用查找器，继承自RexVisitorImpl
    final Map<RexInputRef, InputRefUsage<SqlOperator, @Nullable RexNode>> usageMap = // 声明使用映射，键是输入引用，值是使用信息
        new HashMap<>(); // 创建HashMap实例

    InputUsageFinder() { // 构造方法
      super(true); // 调用父类构造方法，参数true表示深度遍历
    }

    @Override public Void visitInputRef(RexInputRef inputRef) { // 重写visitInputRef方法，处理输入引用
      InputRefUsage<SqlOperator, @Nullable RexNode> inputRefUse = getUsageMap(inputRef); // 获取或创建输入引用的使用信息
      inputRefUse.usageCount++; // 增加使用计数
      return null; // 返回null（Void类型）
    }

    @Override public Void visitCall(RexCall call) { // 重写visitCall方法，处理函数调用
      switch (call.getOperator().getKind()) { // 根据操作符类型
      case GREATER_THAN: // 如果是大于号
      case GREATER_THAN_OR_EQUAL: // 或者大于等于号
      case LESS_THAN: // 或者小于号
      case LESS_THAN_OR_EQUAL: // 或者小于等于号
      case EQUALS: // 或者等号
      case NOT_EQUALS: // 或者不等号
        updateBinaryOpUsage(call); // 调用updateBinaryOpUsage方法更新二元操作符使用信息
        break; // 跳出switch
      case IS_NULL: // 如果是IS_NULL
      case IS_NOT_NULL: // 或者IS_NOT_NULL
        updateUnaryOpUsage(call); // 调用updateUnaryOpUsage方法更新一元操作符使用信息
        break; // 跳出switch
      default: // 其他操作符
      } // 结束switch
      return super.visitCall(call); // 调用父类方法继续遍历子节点
    }

    private void updateUnaryOpUsage(RexCall call) { // 方法声明：更新一元操作符使用信息
      final List<RexNode> operands = call.getOperands(); // 获取操作数列表
      RexNode first = RexUtil.removeCast(operands.get(0)); // 移除第一个操作数的类型转换

      if (first.isA(SqlKind.INPUT_REF)) { // 如果第一个操作数是输入引用
        updateUsage(call.getOperator(), (RexInputRef) first, null); // 更新使用信息，字面量为null（一元操作符没有字面量）
      }
    }

    private void updateBinaryOpUsage(RexCall call) { // 方法声明：更新二元操作符使用信息
      final List<RexNode> operands = call.getOperands(); // 获取操作数列表
      RexNode first = RexUtil.removeCast(operands.get(0)); // 移除第一个操作数的类型转换
      RexNode second = RexUtil.removeCast(operands.get(1)); // 移除第二个操作数的类型转换

      // 处理情况：输入引用 操作符 字面量，如 x > 10
      if (first.isA(SqlKind.INPUT_REF) // 如果第一个操作数是输入引用
          && second.isA(SqlKind.LITERAL)) { // 并且第二个操作数是字面量
        updateUsage(call.getOperator(), (RexInputRef) first, second); // 更新使用信息
      }

      // 处理情况：字面量 操作符 输入引用，如 10 > x（需要转换为 x < 10）
      if (first.isA(SqlKind.LITERAL) // 如果第一个操作数是字面量
          && second.isA(SqlKind.INPUT_REF)) { // 并且第二个操作数是输入引用
        updateUsage(requireNonNull(call.getOperator().reverse()), // 获取反向操作符（如>变成<）
            (RexInputRef) second, first); // 更新使用信息，交换输入引用和字面量
      }
    }

    private void updateUsage(SqlOperator op, RexInputRef inputRef, // 方法声明：更新使用信息
        @Nullable RexNode literal) { // 参数literal：字面量，可能为null
      final InputRefUsage<SqlOperator, @Nullable RexNode> inputRefUse = // 获取输入引用的使用信息
          getUsageMap(inputRef); // 调用getUsageMap方法
      inputRefUse.usageList.add(op, literal); // 将操作符和字面量添加到使用列表中
    }

    private InputRefUsage<SqlOperator, @Nullable RexNode> getUsageMap(RexInputRef rex) { // 方法声明：获取或创建使用映射
      InputRefUsage<SqlOperator, @Nullable RexNode> inputRefUse = usageMap.get(rex); // 从映射中获取使用信息
      if (inputRefUse == null) { // 如果不存在
        inputRefUse = new InputRefUsage<>(); // 创建新的使用信息对象
        usageMap.put(rex, inputRefUse); // 将新对象放入映射中
      }

      return inputRefUse; // 返回使用信息对象
    }
  }

  /**
   * {@link RexInputRef} 在表达式中的使用信息。
   *
   * @param <T1> 左侧类型（操作符类型）
   * @param <T2> 右侧类型（字面量类型）
   */
  private static class InputRefUsage<T1, T2> { // 内部类声明：输入引用使用信息
    private final PairList<T1, T2> usageList = PairList.of(); // 声明使用列表，存储操作符和字面量的配对
    private int usageCount = 0; // 声明使用计数，记录该输入引用在表达式中被使用的次数
  }
}