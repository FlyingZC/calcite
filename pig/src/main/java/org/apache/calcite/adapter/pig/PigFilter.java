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
// Apache 许可证头，声明该代码遵循 Apache 2.0 许可协议
package org.apache.calcite.adapter.pig; // 声明该类所在的包，属于 Calcite 的 Pig 适配器包

import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster 类，用于表示关系代数操作的集群，包含优化器和类型工厂等共享资源
import org.apache.calcite.plan.RelOptTable; // 导入 RelOptTable 类，用于表示优化过程中的表对象
import org.apache.calcite.plan.RelOptUtil; // 导入 RelOptUtil 工具类，提供关系表达式操作的实用方法
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet 类，用于表示关系表达式的特征集合（如物理实现方式）
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，是所有关系表达式（如 Filter、Project 等）的基类
import org.apache.calcite.rel.core.Filter; // 导入 Filter 类，是 Calcite 中表示过滤操作的核心类
import org.apache.calcite.rex.RexCall; // 导入 RexCall 类，表示 Rex 表达式中的函数调用（如 a > 5）
import org.apache.calcite.rex.RexInputRef; // 导入 RexInputRef 类，表示对输入字段的引用（如 $0, $1）
import org.apache.calcite.rex.RexLiteral; // 导入 RexLiteral 类，表示 Rex 表达式中的字面量值（如 5, 'hello'）
import org.apache.calcite.rex.RexNode; // 导入 RexNode 抽象类，是所有行表达式（Rex）的基类

import java.util.ArrayList; // 导入 ArrayList 类，用于动态数组存储
import java.util.List; // 导入 List 接口，用于列表操作

import static com.google.common.base.Preconditions.checkState; // 导入 Google Guava 的 checkState 静态方法，用于检查状态是否满足条件

import static org.apache.calcite.sql.SqlKind.INPUT_REF; // 导入 INPUT_REF 枚举值，表示输入引用类型的 SQL 操作符
import static org.apache.calcite.sql.SqlKind.LITERAL; // 导入 LITERAL 枚举值，表示字面量类型的 SQL 操作符

/**
 * PigFilter 类是 Calcite 中 Filter 关系操作的 Pig 实现。
 * 
 * 【类的作用】
 * 该类实现了 Pig 调用约定（PigRel.CONVENTION）下的 Filter 操作节点。Filter 操作用于根据条件过滤数据行，
 * 在 Pig Latin 中对应 FILTER 语句。例如：table = FILTER table BY score > 2.0;
 * 
 * 【核心功能】
 * 1. 将 Calcite 的关系代数 Filter 节点转换为 Pig Latin 的 FILTER 语句
 * 2. 支持常见的比较操作：等于（==）、小于（<）、小于等于（<=）、大于（>）、大于等于（>=）
 * 3. 支持多个过滤条件的 AND 组合（不支持 OR 操作）
 * 4. 确保过滤条件是简单的字段与字面量的比较（不支持复杂的表达式）
 * 
 * 【继承关系】
 * - 继承自 Filter：复用 Calcite 核心 Filter 的功能
 * - 实现 PigRel 接口：表明这是一个 Pig 适配器的实现，遵循 Pig 调用约定
 * 
 * 【使用场景】
 * 当查询包含 WHERE 子句时，Calcite 优化器会生成 Filter 节点。如果查询的目标是 Pig 数据源，
 * 则会使用 PigFilter 来实现该过滤操作，最终生成 Pig Latin 代码。
 * 
 * 【限制条件】
 * - 只支持 AND 连接的多个条件，不支持 OR 或 NOT
 * - 每个条件必须是字段与字面量的简单比较
 * - 不支持复杂的表达式、函数调用或子查询
 */
public class PigFilter extends Filter implements PigRel { // 定义 PigFilter 类，继承 Filter 并实现 PigRel 接口

  /**
   * PigFilter 构造方法，创建一个 Pig 过滤节点。
   * 
   * 【参数说明】
   * @param cluster 关系操作集群，包含优化器和类型工厂等共享资源，用于在整个查询计划中共享信息
   * @param traitSet 特征集合，定义了该关系节点的物理实现特征，必须包含 PigRel.CONVENTION
   * @param input 输入关系节点，通常是 TableScan 或其他 RelNode，表示要过滤的数据源
   * @param condition 过滤条件，以 RexNode 表示，例如：score > 2.0 或 name = 'John'
   * 
   * 【构造方法作用】
   * 1. 调用父类 Filter 的构造方法，初始化 Filter 节点的基本信息
   * 2. 验证该节点的调用约定是 PigRel.CONVENTION，确保这是 Pig 实现
   * 
   * 【调用约定检查】
   * assert 语句在开发时验证特征集合包含 Pig 约定，如果不符合会在运行时抛出 AssertionError
   * 这确保了 PigFilter 只被用于 Pig 数据源的查询计划中
   */
  public PigFilter(RelOptCluster cluster, RelTraitSet traitSet, RelNode input, RexNode condition) { // 构造方法声明，接收集群、特征集、输入节点和过滤条件
    super(cluster, traitSet, input, condition); // 调用父类 Filter 的构造方法，初始化 Filter 节点
    assert getConvention() == PigRel.CONVENTION; // 断言检查：确保该节点的调用约定是 PigRel.CONVENTION，保证这是 Pig 实现
  } // 构造方法结束

  /**
   * copy 方法用于创建 PigFilter 的副本，通常在优化过程中用于创建等价的节点。
   * 
   * 【方法作用】
   * 在 Calcite 的优化过程中，优化器可能会修改节点的某些属性（如特征集），
   * 这时需要创建节点的副本。该方法允许用新的参数创建一个新的 PigFilter 实例。
   * 
   * 【参数说明】
   * @param traitSet 新的特征集合，可能包含不同的物理实现特征
   * @param input 新的输入节点，可能经过优化转换
   * @param condition 新的过滤条件，可能经过重写或优化
   * @return 返回一个新的 PigFilter 实例，包含指定的参数
   * 
   * 【使用场景】
   * - 规则引擎应用转换规则后创建新节点
   * - 优化器调整物理实现属性
   * - 复制节点用于不同的分支
   */
  @Override public Filter copy(RelTraitSet traitSet, RelNode input, RexNode condition) { // 重写父类的 copy 方法，返回 Filter 类型
    return new PigFilter(getCluster(), traitSet, input, condition); // 创建并返回新的 PigFilter 实例，使用当前集群和传入的参数
  } // copy 方法结束

  /**
   * implement 方法用于将 PigFilter 节点转换为 Pig Latin 代码。
   * 
   * 【方法作用】
   * 这是 PigRel 接口的核心方法，负责将关系节点转换为可执行的 Pig Latin 语句。
   * 该方法会：
   * 1. 先访问子节点（输入节点），确保子节点的 Pig Latin 代码已经生成
   * 2. 然后生成当前 Filter 节点的 FILTER 语句并添加到实现器中
   * 
   * 【实现流程】
   * 1. 调用 implementor.visitChild(0, getInput()) 访问第一个（也是唯一一个）子节点
   *    - 这会递归地生成输入节点的 Pig Latin 代码
   *    - 确保在生成 FILTER 语句之前，输入关系已经定义好
   * 2. 调用 getPigFilterStatement(implementor) 生成 FILTER 语句
   * 3. 调用 implementor.addStatement() 将生成的语句添加到实现器中
   * 
   * 【参数说明】
   * @param implementor 实现器对象，负责收集和管理生成的 Pig Latin 代码
   * 
   * 【代码生成示例】
   * 假设过滤条件是 score > 2.0，输入关系别名是 'students'，
   * 则会生成语句：students = FILTER students BY (score > '2.0');
   */
  @Override public void implement(Implementor implementor) { // 重写 PigRel 接口的 implement 方法
    implementor.visitChild(0, getInput()); // 访问子节点（索引0的子节点，即输入节点），先处理子节点的代码生成，确保输入关系已经定义
    implementor.addStatement(getPigFilterStatement(implementor)); // 生成当前 Filter 节点的 FILTER 语句并添加到实现器中
  } // implement 方法结束

  /**
   * getTable 方法用于获取该节点操作的表。
   * 
   * 【方法作用】
   * PigFilter 本身不直接持有表信息，它只是对输入数据进行过滤。
   * 因此，需要向下遍历关系树，找到实际的表扫描节点（TableScan）。
   * 该方法通过调用输入节点的 getTable() 方法递归地查找表。
   * 
   * 【重写原因】
   * Filter 基类的 getTable() 方法可能返回 null，因为 Filter 不是直接操作表的节点。
   * 在 Pig 适配器中，我们需要知道底层操作的表来生成正确的 Pig Latin 代码，
   * 因此重写该方法以返回实际的表信息。
   * 
   * 【返回值说明】
   * @return 返回该 Filter 节点最终操作的表对象，如果找不到则返回 null
   * 
   * 【使用场景】
   * - 生成 Pig Latin 代码时需要知道表名和别名
   * - 优化过程中需要访问表的元数据
   * - 验证查询的合法性
   */
  /**
   * 重写此方法，使其向下遍历关系树以查找该节点操作的表。
   * Filter 节点本身不直接持有表信息，需要通过输入节点递归查找。
   */
  @Override public RelOptTable getTable() { // 重写父类的 getTable 方法，返回 RelOptTable 类型
    return getInput().getTable(); // 调用输入节点的 getTable() 方法，递归查找底层操作的表
  } // getTable 方法结束

  /**
   * getPigFilterStatement 方法用于生成 Pig Latin 的 FILTER 语句。
   * 
   * 【方法作用】
   * 将 Calcite 的过滤条件（RexNode）转换为 Pig Latin 的 FILTER 语句字符串。
   * 支持多个条件用 AND 连接，每个条件必须是字段与字面量的比较。
   * 
   * 【生成格式】
   * 基本格式：relationAlias = FILTER relationAlias BY condition1 AND condition2 ...;
   * 示例：students = FILTER students BY (score > '2.0') AND (age < '30');
   * 
   * 【实现步骤】
   * 1. 检查过滤条件是否只包含 AND 连接（不包含 OR）
   * 2. 获取当前节点的关系别名（用于 Pig Latin 中的关系引用）
   * 3. 将过滤条件分解为多个 AND 连接的子条件
   * 4. 将每个子条件转换为 Pig Latin 格式
   * 5. 用 " AND " 连接所有条件
   * 6. 组装成完整的 FILTER 语句
   * 
   * 【参数说明】
   * @param implementor 实现器对象，提供获取关系别名和字段名的功能
   * @return 返回完整的 FILTER 语句字符串，以分号结尾
   * 
   * 【异常处理】
   * 如果过滤条件包含 OR 操作，会抛出 IllegalStateException
   * 
   * 【代码示例】
   * 输入条件：score > 2.0 AND age < 30
   * 输出语句：students = FILTER students BY (score > '2.0') AND (age < '30');
   */
  /**
   * 生成 Pig Latin 过滤语句。例如：
   * 
   * <blockquote>
   *   <pre>table = FILTER table BY score &gt; 2.0;</pre>
   * </blockquote>
   * 该方法将 Calcite 的 RexNode 过滤条件转换为 Pig Latin 的 FILTER 语句字符串。
   */
  private String getPigFilterStatement(Implementor implementor) { // 私有方法，生成 Pig FILTER 语句
    checkState(containsOnlyConjunctions(condition)); // 检查过滤条件是否只包含 AND 连接，不包含 OR 操作，否则抛出异常
    String relationAlias = implementor.getPigRelationAlias(this); // 从实现器中获取当前节点的关系别名，用于 Pig Latin 中的关系引用
    List<String> filterConditionsConjunction = new ArrayList<>(); // 创建列表，用于存储每个过滤条件的 Pig Latin 表示
    for (RexNode node : RelOptUtil.conjunctions(condition)) { // 遍历过滤条件中的所有 AND 连接的子条件
      filterConditionsConjunction.add(getSingleFilterCondition(implementor, node)); // 将每个子条件转换为 Pig Latin 格式并添加到列表
    } // for 循环结束
    String allFilterConditions = // 声明变量，存储所有条件连接后的字符串
        String.join(" AND ", filterConditionsConjunction); // 用 " AND " 连接所有过滤条件，生成完整的条件表达式
    return relationAlias + " = FILTER " + relationAlias + " BY " + allFilterConditions + ';'; // 组装完整的 FILTER 语句：别名 = FILTER 别名 BY 条件;
  } // getPigFilterStatement 方法结束

  /**
   * getSingleFilterCondition 方法将单个过滤条件转换为 Pig Latin 格式。
   * 
   * 【方法重载】
   * 该方法有两个重载版本：
   * - 本方法：根据 RexNode 的类型（SqlKind）选择对应的 Pig 操作符
   * - 另一个方法：使用指定的操作符生成条件表达式
   * 
   * 【方法作用】
   * 根据 RexNode 的操作类型（SqlKind），选择对应的 Pig Latin 比较操作符，
   * 然后调用另一个重载方法生成完整的条件表达式。
   * 
   * 【支持的操作类型】
   * - EQUALS：等于操作，转换为 "=="
   * - LESS_THAN：小于操作，转换为 "<"
   * - LESS_THAN_OR_EQUAL：小于等于操作，转换为 "<="
   * - GREATER_THAN：大于操作，转换为 ">"
   * - GREATER_THAN_OR_EQUAL：大于等于操作，转换为 ">="
   * 
   * 【参数说明】
   * @param implementor 实现器对象，用于获取字段名
   * @param node 单个过滤条件的 RexNode 表示
   * @return 返回 Pig Latin 格式的条件表达式，如 "(score > '2.0')"
   * 
   * 【异常处理】
   * 如果遇到不支持的操作类型，抛出 IllegalArgumentException
   * 
   * 【不支持的操作】
   * - OR、NOT 等逻辑操作
   * - LIKE、IN 等复杂操作
   * - 函数调用
   */
  private String getSingleFilterCondition(Implementor implementor, RexNode node) { // 私有方法，根据操作类型生成单个过滤条件
    switch (node.getKind()) { // 根据 RexNode 的操作类型（SqlKind）进行分支处理
    case EQUALS: // 如果是等于操作
      return getSingleFilterCondition(implementor, "==", (RexCall) node); // 调用重载方法，使用 Pig 的 "==" 操作符生成条件
    case LESS_THAN: // 如果是小于操作
      return getSingleFilterCondition(implementor, "<", (RexCall) node); // 调用重载方法，使用 Pig 的 "<" 操作符生成条件
    case LESS_THAN_OR_EQUAL: // 如果是小于等于操作
      return getSingleFilterCondition(implementor, "<=", (RexCall) node); // 调用重载方法，使用 Pig 的 "<=" 操作符生成条件
    case GREATER_THAN: // 如果是大于操作
      return getSingleFilterCondition(implementor, ">", (RexCall) node); // 调用重载方法，使用 Pig 的 ">" 操作符生成条件
    case GREATER_THAN_OR_EQUAL: // 如果是大于等于操作
      return getSingleFilterCondition(implementor, ">=", (RexCall) node); // 调用重载方法，使用 Pig 的 ">=" 操作符生成条件
    default: // 如果是不支持的操作类型
      throw new IllegalArgumentException("Cannot translate node " + node); // 抛出异常，提示无法转换该节点
    } // switch 语句结束
  } // getSingleFilterCondition 方法结束

  /**
   * getSingleFilterCondition 方法使用指定的操作符生成 Pig Latin 条件表达式。
   * 
   * 【方法重载】
   * 这是第二个重载版本，接收指定的操作符字符串，直接生成条件表达式。
   * 
   * 【方法作用】
   * 将一个二元比较操作（如 a > 5）转换为 Pig Latin 格式。
   * 该方法要求操作数必须是一个字段引用和一个字面量，顺序可以任意。
   * 
   * 【支持的格式】
   * - 字段 OP 字面量：score > 2.0
   * - 字面量 OP 字段：2.0 < score（会自动转换为字段 OP 字面量格式）
   * 
   * 【实现步骤】
   * 1. 获取比较操作的左右两个操作数
   * 2. 判断哪个操作数是字面量，哪个是字段引用
   * 3. 根据操作数的位置和类型，提取字段名和字面量值
   * 4. 验证操作数必须是一个字段和一个字面量
   * 5. 组装成 Pig Latin 格式的条件表达式：(fieldName op literal)
   * 
   * 【参数说明】
   * @param implementor 实现器对象，用于获取字段名
   * @param op Pig Latin 比较操作符字符串，如 ">", "<", "==", ">=", "<="
   * @param call RexCall 对象，表示二元比较操作
   * @return 返回 Pig Latin 格式的条件表达式，如 "(score > '2.0')"
   * 
   * 【异常处理】
   * 如果操作数不是字段和字面量的组合，抛出 IllegalArgumentException
   * 
   * 【代码示例】
   * 输入：call = score > 2.0, op = ">"
   * 输出："(score > '2.0')"
   */
  private String getSingleFilterCondition(Implementor implementor, String op, RexCall call) { // 私有方法，使用指定操作符生成条件表达式
    final String fieldName; // 声明字段名字符串变量，用于存储 Pig 中的字段名
    final String literal; // 声明字面量字符串变量，用于存储 Pig 中的字面量值
    final RexNode left = call.operands.get(0); // 获取比较操作的左操作数（第一个操作数）
    final RexNode right = call.operands.get(1); // 获取比较操作的右操作数（第二个操作数）
    if (left.getKind() == LITERAL) { // 如果左操作数是字面量
      if (right.getKind() != INPUT_REF) { // 检查右操作数是否不是输入引用
        throw new IllegalArgumentException( // 抛出异常
            "Expected a RexCall with a single field and single literal"); // 提示期望一个字段和一个字面量的组合
      } else { // 如果右操作数是输入引用
        fieldName = implementor.getFieldName(this, ((RexInputRef) right).getIndex()); // 从实现器中获取右操作数对应的字段名
        literal = getLiteralAsString((RexLiteral) left); // 将左操作数（字面量）转换为 Pig Latin 字符串格式
      } // if-else 结束
    } else if (right.getKind() == LITERAL) { // 如果右操作数是字面量
      if (left.getKind() != INPUT_REF) { // 检查左操作数是否不是输入引用
        throw new IllegalArgumentException( // 抛出异常
            "Expected a RexCall with a single field and single literal"); // 提示期望一个字段和一个字面量的组合
      } else { // 如果左操作数是输入引用
        fieldName = implementor.getFieldName(this, ((RexInputRef) left).getIndex()); // 从实现器中获取左操作数对应的字段名
        literal = getLiteralAsString((RexLiteral) right); // 将右操作数（字面量）转换为 Pig Latin 字符串格式
      } // if-else 结束
    } else { // 如果两个操作数都不是字面量
      throw new IllegalArgumentException( // 抛出异常
          "Expected a RexCall with a single field and single literal"); // 提示期望一个字段和一个字面量的组合
    } // if-else 结束

    return '(' + fieldName + ' ' + op + ' ' + literal + ')'; // 返回完整的条件表达式，格式为：(fieldName op literal)
  } // getSingleFilterCondition 方法结束

  /**
   * containsOnlyConjunctions 方法检查过滤条件是否只包含 AND 连接。
   * 
   * 【方法作用】
   * 验证过滤条件是否只使用 AND 连接多个子条件，不包含 OR 操作。
   * Pig Latin 的 FILTER 语句只支持简单的 AND 连接条件，不支持 OR。
   * 
   * 【实现原理】
   * 使用 RelOptUtil.disjunctions() 方法将过滤条件分解为 OR 连接的部分。
   * 如果只包含一个部分，说明没有 OR 操作；如果有多个部分，说明包含 OR 操作。
   * 
   * 【参数说明】
   * @param condition 过滤条件，以 RexNode 表示
   * @return 如果只包含 AND 连接返回 true，如果包含 OR 操作返回 false
   * 
   * 【示例】
   * - condition = a > 5 AND b < 10 → disjunctions() 返回 [a > 5 AND b < 10] → size=1 → return true
   * - condition = a > 5 OR b < 10 → disjunctions() 返回 [a > 5, b < 10] → size=2 → return false
   * - condition = (a > 5 AND b < 10) OR (c = 3) → disjunctions() 返回 [a > 5 AND b < 10, c = 3] → size=2 → return false
   * 
   * 【使用场景】
   * 在生成 Pig FILTER 语句之前，必须调用此方法验证条件的合法性。
   * 如果条件包含 OR，则无法直接转换为 Pig Latin，需要其他处理方式。
   */
  private static boolean containsOnlyConjunctions(RexNode condition) { // 私有静态方法，检查条件是否只包含 AND 连接
    return RelOptUtil.disjunctions(condition).size() == 1; // 使用 RelOptUtil.disjunctions() 分解条件，如果只有一个部分说明没有 OR 操作
  } // containsOnlyConjunctions 方法结束

  /**
   * getLiteralAsString 方法将 RexLiteral 转换为 Pig Latin 字符串字面量。
   * 
   * 【方法作用】
   * 将 Calcite 的字面量值转换为 Pig Latin 可以识别的字符串格式。
   * 目前实现比较简单，直接用单引号包裹字符串值。
   * 
   * 【当前实现】
   * - 使用 RexLiteral.stringValue() 获取字面量的字符串表示
   * - 用单引号包裹字符串，如 'hello'
   * 
   * 【TODO 说明】
   * 当前实现有以下限制：
   * 1. 没有正确处理不同类型的字面量（数字、日期、布尔值等）
   * 2. 没有处理字符串中的特殊字符转义（如单引号、反斜杠等）
   * 3. 所有值都转换为字符串格式，可能不是最优的
   * 
   * 【参数说明】
   * @param literal RexLiteral 对象，表示一个字面量值
   * @return 返回 Pig Latin 格式的字符串字面量，如 "'hello'"
   * 
   * 【改进方向】
   * - 根据字面量的类型选择合适的格式（数字不需要引号，字符串需要引号）
   * - 实现字符串转义，处理特殊字符
   * - 支持日期、时间等特殊类型的格式化
   * 
   * 【代码示例】
   * 输入：RexLiteral(5) → 输出："'5'"
   * 输入：RexLiteral("John") → 输出："'John'"
   */
  /**
   * 将字面量转换为 Pig Latin 字符串字面量。
   * 
   * <p>TODO: 需要实现正确的字面量到字符串的转换和转义处理
   * 当前实现比较简单，没有处理不同类型和特殊字符转义。
   */
  private static String getLiteralAsString(RexLiteral literal) { // 私有静态方法，将字面量转换为字符串
    return '\'' + RexLiteral.stringValue(literal) + '\''; // 用单引号包裹字面量的字符串表示，生成 Pig Latin 字符串字面量
  } // getLiteralAsString 方法结束
} // PigFilter 类定义结束
