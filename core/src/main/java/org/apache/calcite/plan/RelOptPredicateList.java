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
package org.apache.calcite.plan; // 所属包:org.apache.calcite.plan,这是Calcite查询优化器核心包

import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类,用于构建行表达式(RexNode)
import org.apache.calcite.rex.RexCall; // 导入RexCall类,表示函数调用类型的行表达式
import org.apache.calcite.rex.RexNode; // 导入RexNode类,表示行表达式的基类
import org.apache.calcite.rex.RexUtil; // 导入RexUtil类,提供行表达式的工具方法
import org.apache.calcite.sql.SqlKind; // 导入SqlKind类,定义SQL操作的类型枚举

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类
import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变映射类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解,用于标记可能为null的参数

import java.util.Collection; // 导入Java集合接口
import java.util.List; // 导入Java列表接口

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法,用于非空校验

/**
 * Predicates that are known to hold in the output of a particular relational
 * expression.
 * 已知在特定关系表达式输出中成立的谓词集合
 *
 * <p><b>Pulled up predicates</b> (field {@link #pulledUpPredicates} are
 * predicates that apply to every row output by the relational expression. They
 * are inferred from the input relational expression(s) and the relational
 * operator.
 * <b>上推谓词</b>(字段 {@link #pulledUpPredicates})是适用于关系表达式输出的每一行的谓词
 * 它们是从输入关系表达式和关系运算符推断出来的
 *
 * <p>For example, if you apply {@code Filter(x > 1)} to a relational
 * expression that has a predicate {@code y < 10} then the pulled up predicates
 * for the Filter are {@code [y < 10, x > 1]}.
 * 例如,如果你将 {@code Filter(x > 1)} 应用于一个具有谓词 {@code y < 10} 的关系表达式
 * 那么该Filter的上推谓词为 {@code [y < 10, x > 1]}
 *
 * <p><b>Inferred predicates</b> only apply to joins. If there there is a
 * predicate on the left input to a join, and that predicate is over columns
 * used in the join condition, then a predicate can be inferred on the right
 * input to the join. (And vice versa.)
 * <b>推断谓词</b>仅适用于连接操作。如果连接的左输入上有谓词,并且该谓词作用于连接条件中使用的列
 * 那么可以推断出右输入上的谓词(反之亦然)
 *
 * <p>For example, in the query
 * <blockquote>SELECT *<br>
 * FROM emp<br>
 * JOIN dept ON emp.deptno = dept.deptno
 * WHERE emp.gender = 'F' AND emp.deptno &lt; 10</blockquote>
 * we have
 * <ul>
 *   <li>left: {@code Filter(Scan(EMP), deptno < 10},
 *       predicates: {@code [deptno < 10]}
 *   <li>right: {@code Scan(DEPT)}, predicates: {@code []}
 *   <li>join: {@code Join(left, right, emp.deptno = dept.deptno},
 *      leftInferredPredicates: [],
 *      rightInferredPredicates: [deptno &lt; 10],
 *      pulledUpPredicates: [emp.gender = 'F', emp.deptno &lt; 10,
 *      emp.deptno = dept.deptno, dept.deptno &lt; 10]
 * </ul>
 * 例如,在查询
 * <ul>
 *   <li>左输入: {@code Filter(Scan(EMP), deptno < 10}, 谓词: {@code [deptno < 10]}
 *   <li>右输入: {@code Scan(DEPT)}, 谓词: {@code []}
 *   <li>连接: {@code Join(left, right, emp.deptno = dept.deptno},
 *      leftInferredPredicates: [],
 *      rightInferredPredicates: [deptno &lt; 10],
 *      pulledUpPredicates: [emp.gender = 'F', emp.deptno &lt; 10,
 *      emp.deptno = dept.deptno, dept.deptno &lt; 10]
 * </ul>
 *
 * <p>Note that the predicate from the left input appears in
 * {@code rightInferredPredicates}. Predicates from several sources appear in
 * {@code pulledUpPredicates}.
 * 注意,来自左输入的谓词出现在 {@code rightInferredPredicates} 中
 * 来自多个源的谓词出现在 {@code pulledUpPredicates} 中
 */
public class RelOptPredicateList { // 定义关系表达式优化谓词列表类,用于管理和追踪谓词信息
  private static final ImmutableList<RexNode> EMPTY_LIST = ImmutableList.of(); // 静态常量:空的不可变RexNode列表,用于初始化空谓词列表
  public static final RelOptPredicateList EMPTY = // 静态常量:空的RelOptPredicateList实例,所有谓词列表都为空
      new RelOptPredicateList(EMPTY_LIST, EMPTY_LIST, EMPTY_LIST, // 创建空的谓词列表对象
          ImmutableMap.of()); // 常量映射也为空

  /** Predicates that can be pulled up from the relational expression and its
   * inputs. */
  public final ImmutableList<RexNode> pulledUpPredicates; // 上推谓词列表:可以从关系表达式及其输入中上推的谓词,适用于输出的每一行

  /** Predicates that were inferred from the right input.
   * Empty if the relational expression is not a join. */
  public final ImmutableList<RexNode> leftInferredPredicates; // 左推断谓词列表:从右输入推断出的谓词,仅用于连接操作,非连接时为空

  /** Predicates that were inferred from the left input.
   * Empty if the relational expression is not a join. */
  public final ImmutableList<RexNode> rightInferredPredicates; // 右推断谓词列表:从左输入推断出的谓词,仅用于连接操作,非连接时为空

  /** A map of each (e, constant) pair that occurs within
   * {@link #pulledUpPredicates}. */
  public final ImmutableMap<RexNode, RexNode> constantMap; // 常量映射:存储pulledUpPredicates中出现的每个(表达式,常量)对,用于快速查找常量表达式

  private RelOptPredicateList(ImmutableList<RexNode> pulledUpPredicates, // 私有构造方法:创建RelOptPredicateList实例
      ImmutableList<RexNode> leftInferredPredicates, // 参数:左推断谓词列表
      ImmutableList<RexNode> rightInferredPredicates, // 参数:右推断谓词列表
      ImmutableMap<RexNode, RexNode> constantMap) { // 参数:常量映射
    this.pulledUpPredicates = // 初始化上推谓词列表
        requireNonNull(pulledUpPredicates, "pulledUpPredicates"); // 非空校验,确保pulledUpPredicates不为null
    this.leftInferredPredicates = // 初始化左推断谓词列表
        requireNonNull(leftInferredPredicates, "leftInferredPredicates"); // 非空校验,确保leftInferredPredicates不为null
    this.rightInferredPredicates = // 初始化右推断谓词列表
        requireNonNull(rightInferredPredicates, "rightInferredPredicates"); // 非空校验,确保rightInferredPredicates不为null
    this.constantMap = requireNonNull(constantMap, "constantMap"); // 初始化常量映射,非空校验
  }

  /** Creates a RelOptPredicateList with only pulled-up predicates, no inferred
   * predicates.
   * 创建一个只包含上推谓词的RelOptPredicateList,不包含推断谓词
   *
   * <p>Use this for relational expressions other than joins.
   * 用于非连接的关系表达式
   *
   * @param pulledUpPredicates Predicates that apply to the rows returned by the
   * relational expression
   * 参数:适用于关系表达式返回行的谓词列表
   */
  public static RelOptPredicateList of(RexBuilder rexBuilder, // 静态工厂方法:创建只包含上推谓词的RelOptPredicateList
      Iterable<RexNode> pulledUpPredicates) { // 参数:可迭代的上推谓词集合
    ImmutableList<RexNode> pulledUpPredicatesList = // 将可迭代谓词转换为不可变列表
        ImmutableList.copyOf(pulledUpPredicates); // 使用Guava工具类创建不可变副本
    if (pulledUpPredicatesList.isEmpty()) { // 如果上推谓词列表为空
      return EMPTY; // 返回预定义的空谓词列表对象,避免创建不必要的对象
    }
    return of(rexBuilder, pulledUpPredicatesList, EMPTY_LIST, EMPTY_LIST); // 调用完整的of方法,推断谓词列表为空
  }

  /**
   * Returns true if given predicate list is empty.
   * 判断给定的谓词列表是否为空
   *
   * @param value input predicate list
   * 参数:要检查的谓词列表
   * @return true if all the predicates are empty or if the argument is null
   * 返回值:如果所有谓词都为空或参数为null,则返回true
   */
  public static boolean isEmpty(@Nullable RelOptPredicateList value) { // 静态方法:检查谓词列表是否为空
    if (value == null || value == EMPTY) { // 如果值为null或等于预定义的EMPTY对象
      return true; // 返回true,表示为空
    }
    return value.constantMap.isEmpty() // 检查常量映射是否为空
        && value.leftInferredPredicates.isEmpty() // 并且左推断谓词列表为空
        && value.rightInferredPredicates.isEmpty() // 并且右推断谓词列表为空
        && value.pulledUpPredicates.isEmpty(); // 并且上推谓词列表为空
  }

  /** Creates a RelOptPredicateList for a join.
   * 为连接操作创建RelOptPredicateList
   *
   * @param rexBuilder Rex builder
   * 参数:Rex构建器,用于构建行表达式
   * @param pulledUpPredicates Predicates that apply to the rows returned by the
   * relational expression
   * 参数:适用于关系表达式返回行的谓词列表
   * @param leftInferredPredicates Predicates that were inferred from the right
   *                               input
   * 参数:从右输入推断出的谓词列表
   * @param rightInferredPredicates Predicates that were inferred from the left
   *                                input
   * 参数:从左输入推断出的谓词列表
   */
  public static RelOptPredicateList of(RexBuilder rexBuilder, // 静态工厂方法:创建完整的RelOptPredicateList,用于连接操作
      Iterable<RexNode> pulledUpPredicates, // 参数:可迭代的上推谓词集合
      Iterable<RexNode> leftInferredPredicates, // 参数:可迭代的左推断谓词集合
      Iterable<RexNode> rightInferredPredicates) { // 参数:可迭代的右推断谓词集合
    final ImmutableList<RexNode> pulledUpPredicatesList = // 将上推谓词转换为不可变列表
        ImmutableList.copyOf(pulledUpPredicates); // 创建不可变副本
    final ImmutableList<RexNode> leftInferredPredicateList = // 将左推断谓词转换为不可变列表
        ImmutableList.copyOf(leftInferredPredicates); // 创建不可变副本
    final ImmutableList<RexNode> rightInferredPredicatesList = // 将右推断谓词转换为不可变列表
        ImmutableList.copyOf(rightInferredPredicates); // 创建不可变副本
    if (pulledUpPredicatesList.isEmpty() // 如果所有谓词列表都为空
        && leftInferredPredicateList.isEmpty() // 左推断谓词为空
        && rightInferredPredicatesList.isEmpty()) { // 右推断谓词为空
      return EMPTY; // 返回预定义的空谓词列表对象
    }
    final ImmutableMap<RexNode, RexNode> constantMap = // 从上推谓词中提取常量映射
        RexUtil.predicateConstants(RexNode.class, rexBuilder, // 调用RexUtil工具方法提取常量
            pulledUpPredicatesList); // 从上推谓词列表中提取表达式到常量的映射
    return new RelOptPredicateList(pulledUpPredicatesList, // 创建并返回新的RelOptPredicateList实例
        leftInferredPredicateList, rightInferredPredicatesList, constantMap); // 传入所有参数
  }

  @Override public String toString() { // 重写toString方法,返回谓词列表的字符串表示
    final StringBuilder b = new StringBuilder("{"); // 创建StringBuilder用于构建字符串
    append(b, "pulled", pulledUpPredicates); // 添加上推谓词信息
    append(b, "left", leftInferredPredicates); // 添加左推断谓词信息
    append(b, "right", rightInferredPredicates); // 添加右推断谓词信息
    append(b, "constants", constantMap.entrySet()); // 添加常量映射信息
    return b.append("}").toString(); // 添加闭合括号并返回完整字符串
  }

  private static void append(StringBuilder b, String key, Collection<?> value) { // 私有静态方法:向StringBuilder追加集合信息
    if (!value.isEmpty()) { // 如果集合不为空
      if (b.length() > 1) { // 如果StringBuilder已经有内容(长度大于1)
        b.append(", "); // 添加逗号和空格作为分隔符
      }
      b.append(key); // 添加键名
      b.append(value); // 添加集合值
    }
  }

  public RelOptPredicateList union(RexBuilder rexBuilder, // 公共方法:合并两个谓词列表,返回包含所有谓词的新列表
      RelOptPredicateList list) { // 参数:要合并的另一个谓词列表
    if (this == EMPTY) { // 如果当前对象是空列表
      return list; // 直接返回另一个列表
    } else if (list == EMPTY) { // 如果另一个列表是空列表
      return this; // 直接返回当前对象
    } else { // 两个列表都不为空
      return RelOptPredicateList.of(rexBuilder, // 创建新的谓词列表,包含两个列表的所有谓词
          concat(pulledUpPredicates, list.pulledUpPredicates), // 合并上推谓词
          concat(leftInferredPredicates, list.leftInferredPredicates), // 合并左推断谓词
          concat(rightInferredPredicates, list.rightInferredPredicates)); // 合并右推断谓词
    }
  }

  /** Concatenates two immutable lists, avoiding a copy it possible.
   * 连接两个不可变列表,尽可能避免复制操作以提高性能 */
  private static <E> ImmutableList<E> concat(ImmutableList<E> list1, // 私有静态方法:连接两个不可变列表
      ImmutableList<E> list2) { // 参数:两个要连接的不可变列表
    if (list1.isEmpty()) { // 如果第一个列表为空
      return list2; // 直接返回第二个列表,避免复制
    } else if (list2.isEmpty()) { // 如果第二个列表为空
      return list1; // 直接返回第一个列表,避免复制
    } else { // 两个列表都不为空
      return ImmutableList.<E>builder().addAll(list1).addAll(list2).build(); // 使用builder模式创建包含两个列表元素的新列表
    }
  }

  public RelOptPredicateList shift(RexBuilder rexBuilder, int offset) { // 公共方法:偏移谓词中的字段引用,用于处理字段位置变化
    return RelOptPredicateList.of(rexBuilder, // 创建新的谓词列表,所有谓词的字段引用都偏移指定量
        RexUtil.shift(pulledUpPredicates, offset), // 偏移上推谓词中的字段引用
        RexUtil.shift(leftInferredPredicates, offset), // 偏移左推断谓词中的字段引用
        RexUtil.shift(rightInferredPredicates, offset)); // 偏移右推断谓词中的字段引用
  }

  /** Returns whether an expression is effectively NOT NULL due to an
   * {@code e IS NOT NULL} condition in this predicate list.
   * 判断表达式是否由于谓词列表中的IS NOT NULL条件而实际上非空 */
  public boolean isEffectivelyNotNull(RexNode e) { // 公共方法:检查表达式是否实际上非空
    if (!e.getType().isNullable()) { // 如果表达式的类型本身不可为空
      return true; // 直接返回true,表达式非空
    }
    for (RexNode p : pulledUpPredicates) { // 遍历上推谓词列表
      if (p.getKind() == SqlKind.IS_NOT_NULL) { // 如果谓词是IS_NOT_NULL操作
        // if e IS NOT NULL and e is TINYINT then cast(e as INTEGER) IS NOT NULL
        // 如果e IS NOT NULL且e是TINYINT,那么cast(e as INTEGER) IS NOT NULL也成立
        if (RexUtil.isLosslessCast(e)) { // 检查e是否是无损类型转换
          if (isEffectivelyNotNull(((RexCall) e).getOperands().get(0))) { // 递归检查转换前的表达式是否非空
            return true; // 如果转换前的表达式非空,则转换后的表达式也非空
          }
        }
        if (((RexCall) p).getOperands().get(0).equals(e)) { // 如果IS_NOT_NULL谓词的操作数等于当前表达式e
          return true; // 表达式e确实非空
        }
      }
    }
    if (SqlKind.COMPARISON.contains(e.getKind())) { // 如果表达式是比较操作
      List<RexNode> operands = ((RexCall) e).getOperands(); // 获取比较操作的所有操作数
      for (RexNode operand : operands) { // 遍历所有操作数
        if (!isEffectivelyNotNull(operand)) { // 递归检查每个操作数是否非空
          return false; // 如果有任何一个操作数可能为空,则整个比较表达式可能为空
        }
      }
      return true; // 所有操作数都非空,比较表达式非空
    }
    return false; // 其他情况,表达式可能为空
  }
}
