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
package org.apache.calcite.rel.core;  // JoinInfo类所在的包，包含关系代数核心类

import org.apache.calcite.plan.RelOptUtil;  // 关系优化工具类，提供拆分连接条件等方法
import org.apache.calcite.rel.RelNode;  // 关系节点接口，代表查询计划中的关系操作
import org.apache.calcite.rex.RexBuilder;  // Rex表达式构建器，用于创建Rex表达式
import org.apache.calcite.rex.RexNode;  // Rex表达式节点接口，代表行表达式
import org.apache.calcite.rex.RexUtil;  // Rex表达式工具类，提供表达式组合等工具方法
import org.apache.calcite.runtime.FlatLists;  // 扁平列表工具类，用于创建扁平化的列表结构
import org.apache.calcite.util.ImmutableBitSet;  // 不可变的位集合，用于高效表示列索引集合
import org.apache.calcite.util.ImmutableIntList;  // 不可变的整数列表，用于存储列索引序列
import org.apache.calcite.util.mapping.IntPair;  // 整数对，用于表示左右表的列索引对应关系

import com.google.common.collect.ImmutableList;  // Google Guava库的不可变列表实现

import java.util.ArrayList;  // Java标准库的动态数组列表
import java.util.List;  // Java标准库的列表接口

import static java.util.Objects.requireNonNull;  // 静态导入对象非空检查方法

/** JoinInfo类：一个经过分析的连接条件对象
 *
 * <p>该类对于许多需要判断连接是否为等值连接的算法非常有用。
 * 它将连接条件分解为等值连接键和非等值条件两部分，便于优化器进行连接重排序、连接下推等优化操作。
 *
 * <p>你可以使用{@link #createWithStrictEquality}方法创建JoinInfo对象，
 * 或者调用{@link Join#analyzeCondition()}方法；许多类型的Join会缓存其join info，
 * 特别是等值连接会频繁使用这些信息。
 *
 * <p>该类的主要作用：
 * 1. 存储连接条件中的等值连接键（leftKeys和rightKeys）
 * 2. 存储连接条件中的非等值条件（nonEquiConditions）
 * 3. 提供判断是否为等值连接的方法（isEqui）
 * 4. 提供构建等值连接条件的方法（getEquiCondition）
 * 5. 提供获取键对的方法（pairs、keys）
 *
 * @see Join#analyzeCondition()  参见Join类的analyzeCondition方法 */
public class JoinInfo {  // JoinInfo类：连接条件分析结果类，用于存储和操作连接条件的信息
  public final ImmutableIntList leftKeys;  // 左表的等值连接键列表，存储左表中参与等值连接的列索引，不可变
  public final ImmutableIntList rightKeys;  // 右表的等值连接键列表，存储右表中参与等值连接的列索引，不可变，与leftKeys一一对应
  public final ImmutableList<RexNode> nonEquiConditions;  // 非等值连接条件列表，存储连接条件中除等值条件外的其他条件表达式（如大于、小于等），不可变

  /** Creates a JoinInfo.  创建一个JoinInfo对象（受保护的构造方法）
   *
   * <p>构造方法说明：
   * 1. 接收左表键列表、右表键列表和非等值条件列表作为参数
   * 2. 对所有参数进行非空检查，确保参数有效性
   * 3. 断言左键列表和右键列表的大小必须相等，保证等值连接键的一一对应关系
   * 4. 将参数赋值给对应的成员变量
   *
   * @param leftKeys 左表的等值连接键列表，包含左表中参与等值连接的列索引，不可为null
   * @param rightKeys 右表的等值连接键列表，包含右表中参与等值连接的列索引，不可为null，必须与leftKeys大小相等
   * @param nonEquiConditions 非等值连接条件列表，包含除等值条件外的其他连接条件，不可为null */
  protected JoinInfo(ImmutableIntList leftKeys, ImmutableIntList rightKeys,
      ImmutableList<RexNode> nonEquiConditions) {  // 受保护的构造方法，用于创建JoinInfo实例
    this.leftKeys = requireNonNull(leftKeys, "leftKeys");  // 将左键列表赋值给成员变量，并进行非空检查，如果为null则抛出NullPointerException
    this.rightKeys = requireNonNull(rightKeys, "rightKeys");  // 将右键列表赋值给成员变量，并进行非空检查，如果为null则抛出NullPointerException
    this.nonEquiConditions =  // 将非等值条件列表赋值给成员变量，并进行非空检查
        requireNonNull(nonEquiConditions, "nonEquiConditions");  // 如果nonEquiConditions为null则抛出NullPointerException
    assert leftKeys.size() == rightKeys.size();  // 断言左键列表和右键列表的大小必须相等，确保等值连接键的一一对应关系，如果不相等则抛出AssertionError
  }

  /** Creates a {@code JoinInfo} by analyzing a condition.  通过分析连接条件创建JoinInfo对象（静态工厂方法）
   *
   * <p>方法说明：
   * 1. 接收左表关系节点、右表关系节点和连接条件表达式作为参数
   * 2. 创建临时列表用于存储分析结果：左键、右键、是否过滤null、非等值条件
   * 3. 调用RelOptUtil.splitJoinCondition方法拆分连接条件
   * 4. 将临时列表转换为不可变列表并创建JoinInfo对象返回
   *
   * <p>注意：此方法将IS_NOT_DISTINCT_FROM操作视为等值条件（通过filterNulls参数控制）
   *
   * @param left 左表关系节点，代表连接操作的左输入表
   * @param right 右表关系节点，代表连接操作的右输入表
   * @param condition 连接条件表达式，可以是AND、OR、=、>、<等Rex表达式
   * @return JoinInfo对象，包含分析后的等值连接键和非等值条件 */
  public static JoinInfo of(RelNode left, RelNode right, RexNode condition) {  // 静态工厂方法，通过分析连接条件创建JoinInfo对象
    final List<Integer> leftKeys = new ArrayList<>();  // 创建左键列表，用于存储左表中参与等值连接的列索引
    final List<Integer> rightKeys = new ArrayList<>();  // 创建右键列表，用于存储右表中参与等值连接的列索引
    final List<Boolean> filterNulls = new ArrayList<>();  // 创建是否过滤null的列表，用于标记每个等值条件是否需要过滤null值
    final List<RexNode> nonEquiList = new ArrayList<>();  // 创建非等值条件列表，用于存储连接条件中的非等值部分
    RelOptUtil.splitJoinCondition(left, right, condition, leftKeys, rightKeys,  // 调用RelOptUtil工具类的splitJoinCondition方法，将连接条件拆分为等值键和非等值条件
        filterNulls, nonEquiList);  // 传入filterNulls列表以支持IS_NOT_DISTINCT_FROM操作，非等值条件存入nonEquiList
    return new JoinInfo(ImmutableIntList.copyOf(leftKeys),  // 将左键列表转换为不可变列表
        ImmutableIntList.copyOf(rightKeys), ImmutableList.copyOf(nonEquiList));  // 将右键列表和非等值条件列表转换为不可变列表，并创建JoinInfo对象返回
  }

  /** Creates a {@code JoinInfo} by analyzing a condition.  通过分析连接条件创建JoinInfo对象（严格等值模式）
   *
   * <p>方法说明：
   * 1. 接收左表关系节点、右表关系节点和连接条件表达式作为参数
   * 2. 创建临时列表用于存储分析结果：左键、右键、非等值条件
   * 3. 调用RelOptUtil.splitJoinCondition方法拆分连接条件，传入null作为filterNulls参数
   * 4. 将临时列表转换为不可变列表并创建JoinInfo对象返回
   *
   * <p>与of方法的重要区别：
   * - 此方法只将EQUALS（=）操作视为等值条件
   * - IS_NOT_DISTINCT_FROM操作不被视为等值条件，而是放入非等值条件列表
   * - 这种严格模式适用于需要精确区分等值和非等值连接的场景
   *
   * @param left 左表关系节点，代表连接操作的左输入表
   * @param right 右表关系节点，代表连接操作的右输入表
   * @param condition 连接条件表达式，可以是AND、OR、=、>、<等Rex表达式
   * @return JoinInfo对象，包含分析后的等值连接键（仅包含EQUALS操作）和非等值条件（包含IS_NOT_DISTINCT_FROM操作） */
  public static JoinInfo createWithStrictEquality(RelNode left,  // 静态工厂方法，使用严格等值模式创建JoinInfo对象
      RelNode right, RexNode condition) {  // 接收左表、右表和连接条件参数
    final List<Integer> leftKeys = new ArrayList<>();  // 创建左键列表，用于存储左表中参与等值连接的列索引
    final List<Integer> rightKeys = new ArrayList<>();  // 创建右键列表，用于存储右表中参与等值连接的列索引
    final List<RexNode> nonEquiList = new ArrayList<>();  // 创建非等值条件列表，用于存储连接条件中的非等值部分
    RelOptUtil.splitJoinCondition(left, right, condition, leftKeys, rightKeys,  // 调用RelOptUtil工具类的splitJoinCondition方法，将连接条件拆分为等值键和非等值条件
        null, nonEquiList);  // 传入null作为filterNulls参数，表示不使用IS_NOT_DISTINCT_FROM作为等值条件，将其放入非等值条件列表
    return new JoinInfo(ImmutableIntList.copyOf(leftKeys),  // 将左键列表转换为不可变列表
        ImmutableIntList.copyOf(rightKeys), ImmutableList.copyOf(nonEquiList));  // 将右键列表和非等值条件列表转换为不可变列表，并创建JoinInfo对象返回
  }

  /** Creates an equi-join.  创建一个等值连接的JoinInfo对象（静态工厂方法）
   *
   * <p>方法说明：
   * 1. 接收左键列表和右键列表作为参数
   * 2. 创建一个JoinInfo对象，其中非等值条件列表为空
   * 3. 返回的JoinInfo对象表示纯等值连接，没有额外的非等值条件
   *
   * <p>使用场景：
   * - 当已知连接是纯等值连接时，可以直接使用此方法创建JoinInfo
   * - 避免了分析连接条件的开销
   * - 适用于连接重排序、连接下推等优化操作
   *
   * @param leftKeys 左表的等值连接键列表，包含左表中参与等值连接的列索引
   * @param rightKeys 右表的等值连接键列表，包含右表中参与等值连接的列索引，必须与leftKeys大小相等
   * @return JoinInfo对象，表示纯等值连接，nonEquiConditions为空列表 */
  public static JoinInfo of(ImmutableIntList leftKeys,  // 静态工厂方法，创建等值连接的JoinInfo对象
      ImmutableIntList rightKeys) {  // 接收左键列表和右键列表参数
    return new JoinInfo(leftKeys, rightKeys, ImmutableList.of());  // 创建JoinInfo对象，传入空列表作为非等值条件，表示纯等值连接
  }

  /** Returns whether this is an equi-join.  判断此连接是否为等值连接
   *
   * <p>方法说明：
   * 1. 检查非等值条件列表是否为空
   * 2. 如果非等值条件列表为空，则表示这是一个纯等值连接
   * 3. 如果非等值条件列表不为空，则表示这是一个混合连接（既有等值条件又有非等值条件）
   *
   * <p>等值连接的定义：
   * - 连接条件仅包含等值比较（=操作）
   * - 不包含大于、小于、不等于等非等值比较
   * - 等值连接是连接优化的重要基础，支持连接重排序、连接下推等优化
   *
   * <p>使用场景：
   * - 优化器判断是否可以使用哈希连接、合并连接等高效连接算法
   * - 判断是否可以进行连接重排序
   * - 判断是否可以将条件下推到表扫描
   *
   * @return 如果是等值连接返回true，否则返回false */
  public boolean isEqui() {  // 公共方法，判断此连接是否为等值连接
    return nonEquiConditions.isEmpty();  // 返回非等值条件列表是否为空，为空表示纯等值连接
  }

  /** Returns a list of (left, right) key ordinals.  返回等值连接键的（左表列索引，右表列索引）对列表
   *
   * <p>方法说明：
   * 1. 将左键列表和右键列表压缩成键对列表
   * 2. 每个IntPair对象包含一个左表列索引和一个右表列索引
   * 3. 键对的顺序与leftKeys和rightKeys的顺序一致
   *
   * <p>使用场景：
   * - 连接重排序时需要知道左右表的列对应关系
   * - 构建等值连接条件时需要键对信息
   * - 连接下推时需要映射列索引
   * - 连接算法（如哈希连接）需要键对信息来构建哈希表
   *
   * <p>示例：
   * 如果leftKeys = [0, 2], rightKeys = [1, 3]
   * 则返回的pairs为 [(0, 1), (2, 3)]
   * 表示左表的第0列等于右表的第1列，左表的第2列等于右表的第3列
   *
   * @return 键对列表，每个IntPair包含一个左表列索引和一个右表列索引 */
  public List<IntPair> pairs() {  // 公共方法，返回等值连接键的键对列表
    return IntPair.zip(leftKeys, rightKeys);  // 使用IntPair.zip方法将左键列表和右键列表压缩成键对列表并返回
  }

  public ImmutableBitSet leftSet() {  // 公共方法，返回左表等值连接键的位集合
    return ImmutableBitSet.of(leftKeys);  // 将左键列表转换为不可变的位集合并返回，位集合可以高效地进行集合运算
  }

  public ImmutableBitSet rightSet() {  // 公共方法，返回右表等值连接键的位集合
    return ImmutableBitSet.of(rightKeys);  // 将右键列表转换为不可变的位集合并返回，位集合可以高效地进行集合运算
  }

  @Deprecated // to be removed before 2.0  // 已废弃的方法，将在2.0版本前移除
  public RexNode getRemaining(RexBuilder rexBuilder) {  // 公共方法，获取剩余的非等值条件（已废弃）
    return RexUtil.composeConjunction(rexBuilder, nonEquiConditions);  // 使用RexUtil工具类将非等值条件列表组合成一个AND连接的Rex表达式并返回
  }

  public RexNode getEquiCondition(RelNode left, RelNode right,  // 公共方法，根据等值连接键构建等值连接条件表达式
      RexBuilder rexBuilder) {  // 接收左表、右表和Rex构建器参数
    return RelOptUtil.createEquiJoinCondition(left, leftKeys, right, rightKeys,  // 调用RelOptUtil工具类的createEquiJoinCondition方法，根据左右表的等值键创建等值连接条件
        rexBuilder);  // 传入Rex构建器用于构建Rex表达式，返回一个包含所有等值条件的AND连接的Rex表达式
  }

  public List<ImmutableIntList> keys() {  // 公共方法，返回包含左键列表和右键列表的扁平列表
    return FlatLists.of(leftKeys, rightKeys);  // 使用FlatLists工具类创建一个包含左键列表和右键列表的扁平列表并返回
  }

}
