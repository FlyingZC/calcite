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
// Apache Calcite 是一个动态数据管理框架，提供 SQL 解析、优化、执行等功能
// Arrow 是 Apache Arrow 项目，提供高效的列式内存格式
// 本包实现了 Calcite 与 Apache Arrow 数据源的适配器，使 Calcite 能够查询 Arrow 格式的数据
package org.apache.calcite.adapter.arrow;

// RelOptUtil 是 Calcite 关系表达式优化工具类，提供表达式分解、优化等功能
import org.apache.calcite.plan.RelOptUtil;
// RelDataType 表示关系数据类型，描述表或查询结果的结构（字段名、类型等）
import org.apache.calcite.rel.type.RelDataType;
// RelDataTypeField 表示关系数据类型中的单个字段，包含字段名和类型信息
import org.apache.calcite.rel.type.RelDataTypeField;
// RexBuilder 是行表达式构建器，用于创建各种类型的 RexNode 表达式
import org.apache.calcite.rex.RexBuilder;
// RexCall 表示函数调用表达式，如函数调用、运算符调用等
import org.apache.calcite.rex.RexCall;
// RexInputRef 表示对输入行的引用，即引用输入行的某个字段（类似 SQL 中的列名）
import org.apache.calcite.rex.RexInputRef;
// RexLiteral 表示字面量常量表达式，如数字、字符串、日期等常量值
import org.apache.calcite.rex.RexLiteral;
// RexNode 是所有行表达式的基类，表示 Calcite 表达式树的节点
import org.apache.calcite.rex.RexNode;
// RexUtil 提供行表达式工具方法，用于表达式转换、简化等操作
import org.apache.calcite.rex.RexUtil;
// SqlKind 表示 SQL 操作符的类别，如 SELECT、WHERE、JOIN、AND、OR 等
import org.apache.calcite.sql.SqlKind;
// SqlTypeName 表示 SQL 数据类型名称，如 INTEGER、VARCHAR、DATE、TIMESTAMP 等
import org.apache.calcite.sql.type.SqlTypeName;
// DateString 表示日期字符串，用于处理和格式化日期值
import org.apache.calcite.util.DateString;

// @Nullable 注解标记可能为 null 的值，来自 CheckerFramework 框架，用于空值检查
import org.checkerframework.checker.nullness.qual.Nullable;

// SimpleDateFormat 是 Java 日期格式化工具，用于将日期对象格式化为字符串
import java.text.SimpleDateFormat;
// ArrayList 是动态数组实现，用于存储可变长度的元素列表
import java.util.ArrayList;
// List 是集合接口，定义了有序集合的操作规范
import java.util.List;

// ISO_DATETIME_FRACTIONAL_SECOND_FORMAT 是 ISO 8601 标准的日期时间格式常量，包含秒的小数部分
import static org.apache.calcite.util.DateTimeStringUtils.ISO_DATETIME_FRACTIONAL_SECOND_FORMAT;
// getDateFormatter 方法用于获取指定格式的日期格式化器
import static org.apache.calcite.util.DateTimeStringUtils.getDateFormatter;

// requireNonNull 是 Objects 类的静态方法，用于检查对象是否为 null，如果为 null 则抛出 NullPointerException
import static java.util.Objects.requireNonNull;

/**
 * 将 Calcite 的 RexNode 表达式翻译为 Gandiva 过滤字符串
 * 
 * 类作用详解：
 * -----------
 * ArrowTranslator 是 Apache Calcite 与 Apache Arrow 集成的核心翻译器类。
 * 它负责将 Calcite 的行表达式（RexNode）转换为 Apache Arrow Gandiva 表达式字符串。
 * 
 * 核心功能：
 * 1. 表达式翻译：将 SQL WHERE 条件中的表达式（如 a > 10, name = 'test'）翻译为 Gandiva 过滤表达式
 * 2. 类型转换：处理不同数据类型（整数、浮点数、字符串、日期、时间戳等）的转换
 * 3. 操作符映射：将 SQL 操作符（=, <, >, IS NULL 等）映射到 Gandiva 操作符
 * 4. 条件组合：处理 AND、OR 等逻辑条件的组合
 * 
 * Gandiva 是 Apache Arrow 的表达式求值引擎，提供高效的列式数据过滤能力。
 * 该类是 Calcite 查询优化器与 Arrow 数据源之间的桥梁，使得 Calcite 能够利用
 * Arrow 的高性能列式存储和计算能力。
 * 
 * 使用场景：
 * - 当 Calcite 查询需要过滤 Arrow 格式数据时
 * - 在 ArrowTableScan 或 ArrowFilter 中使用
 * - 将 SQL WHERE 子句转换为下推到 Arrow 的过滤条件
 * 
 * 设计模式：
 * - 访问者模式：遍历 RexNode 表达式树，根据节点类型进行不同的翻译
 * - 递归处理：对于嵌套表达式，递归调用翻译方法
 * - 工厂方法：通过 create() 静态方法创建实例
 */
class ArrowTranslator {
  // RexBuilder 是行表达式构建器，用于创建和操作 RexNode 表达式
  // 成员变量作用：保存用于构建表达式的构建器，可用于创建新的表达式或转换现有表达式
  final RexBuilder rexBuilder;
  
  // RelDataType 表示行的数据类型，描述行的结构（包含哪些字段及其类型）
  // 成员变量作用：保存当前行的类型信息，用于获取字段名称、类型等元数据
  final RelDataType rowType;
  
  // List<String> 存储字段名称列表
  // 成员变量作用：保存当前行中所有字段的名称列表，用于将字段索引映射到字段名
  // 字段名与行类型中的字段顺序一致，通过索引可以快速访问字段名
  final List<String> fieldNames;

  /** 私有构造方法，防止外部直接实例化，必须通过 create() 工厂方法创建
   * 构造方法作用：初始化翻译器的核心成员变量
   * 
   * @param rexBuilder 行表达式构建器，用于构建和操作表达式
   * @param rowType 行数据类型，描述行的结构（字段名和类型）
   * 
   * 初始化流程：
   * 1. 保存 RexBuilder 实例，用于后续表达式操作
   * 2. 保存 RelDataType 实例，用于获取行结构信息
   * 3. 调用 ArrowRules.arrowFieldNames() 提取所有字段名，建立索引到名称的映射
   */
  ArrowTranslator(RexBuilder rexBuilder, RelDataType rowType) {
    // 将传入的 RexBuilder 保存到成员变量，用于后续表达式构建和转换
    this.rexBuilder = rexBuilder;
    // 将传入的 RelDataType 保存到成员变量，用于获取行结构信息
    this.rowType = rowType;
    // 从 rowType 中提取所有字段名称，保存到 fieldNames 列表
    // ArrowRules.arrowFieldNames() 方法会遍历 rowType 的所有字段，返回字段名列表
    // 这个列表用于将字段索引（RexInputRef.getIndex()）映射到实际的字段名
    this.fieldNames = ArrowRules.arrowFieldNames(rowType);
  }

  /** 创建 ArrowTranslator 实例的静态工厂方法
   * 方法作用：提供统一的创建入口，封装构造逻辑
   * 
   * @param rexBuilder 行表达式构建器，用于构建和操作表达式
   * @param rowType 行数据类型，描述行的结构（字段名和类型）
   * @return 新创建的 ArrowTranslator 实例
   * 
   * 设计意图：
   * - 使用工厂方法而非直接构造，便于未来扩展（如缓存实例、配置参数等）
   * - 提供更清晰的语义，明确表示"创建"而非"构造"
   * - 与其他 Calcite 适配器的创建模式保持一致
   */
  public static ArrowTranslator create(RexBuilder rexBuilder,
      RelDataType rowType) {
    // 调用私有构造方法创建并返回新的 ArrowTranslator 实例
    return new ArrowTranslator(rexBuilder, rowType);
  }

  /** 翻译匹配条件表达式，将 RexNode 条件转换为 Gandiva 过滤字符串列表
   * 方法作用：翻译 WHERE 子句中的条件表达式，支持简单的 AND 条件
   * 
   * @param condition 要翻译的条件表达式（RexNode），通常是 SQL WHERE 子句中的布尔表达式
   * @return Gandiva 过滤字符串列表，每个字符串代表一个过滤条件
   * 
   * 翻译流程：
   * 1. 使用 RelOptUtil.disjunctions() 将条件分解为 OR 子句列表
   * 2. 如果只有一个 OR 子句（即没有 OR 操作），则调用 translateAnd() 处理 AND 条件
   * 3. 如果有多个 OR 子句，抛出异常，因为当前不支持 OR 条件
   * 
   * 限制：
   * - 不支持 OR 操作（disjunctions.size() > 1 时抛出异常）
   * - 只支持简单的 AND 条件组合
   * - 复杂的嵌套逻辑可能无法处理
   * 
   * 示例：
   * - 输入：a > 10 AND b = 'test'
   * - 输出：["a greater_than 10 integer", "b equal 'test' string"]
   */
  List<String> translateMatch(RexNode condition) {
    // RelOptUtil.disjunctions() 将条件表达式分解为 OR 子句列表
    // 例如：(a > 10) OR (b = 'test') 会被分解为 [a > 10, b = 'test']
    List<RexNode> disjunctions = RelOptUtil.disjunctions(condition);
    // 检查 OR 子句数量，如果只有一个，说明没有 OR 操作，可以处理
    if (disjunctions.size() == 1) {
      // 调用 translateAnd() 方法处理 AND 条件组合
      // 即使没有 AND，单个条件也会被正确处理
      return translateAnd(disjunctions.get(0));
    } else {
      // 如果有多个 OR 子句，抛出不支持异常
      // 当前版本不支持 OR 操作，因为 Gandiva 的 OR 处理较复杂
      throw new UnsupportedOperationException("Unsupported disjunctive condition " + condition);
    }
  }

  /**
   * 返回字面量的值
   * 方法作用：从 RexLiteral 中提取实际的 Java 值，处理特殊类型（时间戳、日期）
   * 
   * @param literal 要翻译的字面量表达式
   * @return 字面量的实际值，类型取决于字面量的类型
   * 
   * 处理逻辑：
   * 1. TIMESTAMP：将毫秒数格式化为 ISO 8601 字符串
   * 2. TIMESTAMP_WITH_LOCAL_TIME_ZONE：同 TIMESTAMP
   * 3. DATE：将日期转换为 DateString 对象，再转为字符串
   * 4. 其他类型：直接返回 getValue3() 的值
   * 
   * 为什么使用 getValue3()：
   * - getValue() 返回 Object，需要类型转换
   * - getValue2() 返回 Comparable
   * - getValue3() 返回实际类型的值，最直接
   * 
   * 时间戳格式：
   * - 使用 ISO 8601 标准格式，如 "2024-01-06 10:30:45.123"
   * - 包含小数秒部分，提高精度
   */
  private static Object literalValue(RexLiteral literal) {
    // 根据字面量的类型名称进行分支处理
    switch (literal.getTypeName()) {
    // TIMESTAMP 类型：时间戳，包含日期和时间
    case TIMESTAMP:
    // TIMESTAMP_WITH_LOCAL_TIME_ZONE 类型：带本地时区的时间戳
    case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
      // 获取 ISO 8601 格式的日期时间格式化器（包含小数秒）
      final SimpleDateFormat dateFormatter =
          getDateFormatter(ISO_DATETIME_FRACTIONAL_SECOND_FORMAT);
      // 从字面量中提取毫秒数（Long 类型）
      Long millis = literal.getValueAs(Long.class);
      // 使用格式化器将毫秒数格式化为 ISO 8601 字符串
      // requireNonNull 确保毫秒数不为 null
      return dateFormatter.format(requireNonNull(millis, "millis"));
    // DATE 类型：日期（不含时间）
    case DATE:
      // 从字面量中提取 DateString 对象
      final DateString dateString = literal.getValueAs(DateString.class);
      // 将 DateString 转换为字符串格式返回
      // requireNonNull 确保日期字符串不为 null
      return requireNonNull(dateString, "dateString").toString();
    // 其他类型：直接返回字面量的值
    default:
      // getValue3() 返回字面量的实际值（如 Integer、String、Double 等）
      // requireNonNull 确保值不为 null
      return requireNonNull(literal.getValue3());
    }
  }

  /**
   * 将合取谓词（AND 条件）翻译为过滤字符串列表
   * 方法作用：处理 AND 操作符连接的多个条件，将每个条件翻译为独立的过滤字符串
   * 
   * @param condition 合取谓词，即用 AND 连接的多个条件表达式
   * @return 过滤字符串列表，每个字符串代表一个 AND 子条件
   * 
   * 翻译流程：
   * 1. 使用 RelOptUtil.conjunctions() 将条件分解为 AND 子句列表
   * 2. 遍历每个 AND 子句：
   *    a. 如果是 SEARCH 操作符（IN、BETWEEN 等），先展开为多个条件
   *    b. 否则，直接翻译为过滤字符串
   * 3. 返回所有过滤字符串的列表
   * 
   * SEARCH 操作符展开：
   * - a IN (1, 2, 3) 会被展开为：a = 1 OR a = 2 OR a = 3
   * - RexUtil.expandSearch() 执行展开操作
   * 
   * 示例：
   * - 输入：a > 10 AND b = 'test' AND c IS NULL
   * - 输出：["a greater_than 10 integer", "b equal 'test' string", "c isnull"]
   */
  private List<String> translateAnd(RexNode condition) {
    // 创建列表存储翻译后的谓词字符串
    List<String> predicates = new ArrayList<>();
    // RelOptUtil.conjunctions() 将条件分解为 AND 子句列表
    // 例如：(a > 10) AND (b = 'test') 会被分解为 [a > 10, b = 'test']
    for (RexNode node : RelOptUtil.conjunctions(condition)) {
      // 检查节点类型是否为 SEARCH（IN、BETWEEN 等操作符）
      if (node.getKind() == SqlKind.SEARCH) {
        // RexUtil.expandSearch() 将 SEARCH 表达式展开为多个 OR 条件
        // 例如：a IN (1, 2, 3) -> a = 1 OR a = 2 OR a = 3
        final RexNode node2 = RexUtil.expandSearch(rexBuilder, null, node);
        // 递归调用 translateMatch() 处理展开后的表达式
        // 注意：这里会再次检查 OR 条件，如果展开后有多个 OR 会抛出异常
        predicates.addAll(translateMatch(node2));
      } else {
        // 非 SEARCH 操作符，直接翻译为过滤字符串
        predicates.add(translateMatch2(node));
      }
    }
    // 返回所有谓词字符串的列表
    return predicates;
  }

  /**
   * 翻译二元或一元关系表达式
   * 方法作用：根据表达式类型（比较、IS NULL、NOT 等）调用相应的翻译方法
   * 
   * @param node 总是求值为布尔表达式的 RexNode，当前仅从 translateAnd 调用
   * @return 关系表达式的翻译字符串
   * 
   * 支持的操作符：
   * - 比较操作符：=, <>, <, <=, >, >=
   * - 空值检查：IS NULL, IS NOT NULL
   * - 布尔检查：IS NOT TRUE, IS NOT FALSE
   * - 逻辑操作：NOT
   * - 字段引用：INPUT_REF（字段名 + " istrue"）
   * 
   * 操作符映射：
   * - EQUALS -> "equal"
   * - NOT_EQUALS -> "not_equal"
   * - LESS_THAN -> "less_than"（注意：参数顺序反转）
   * - LESS_THAN_OR_EQUAL -> "less_than_or_equal_to"（参数顺序反转）
   * - GREATER_THAN -> "greater_than"（参数顺序反转）
   * - GREATER_THAN_OR_EQUAL -> "greater_than_or_equal_to"（参数顺序反转）
   * 
   * 参数顺序反转说明：
   * Gandiva 的比较操作符参数顺序与 SQL 相反
   * SQL: a > b  -> Gandiva: b less_than a
   * 因此 translateBinary() 会尝试两种顺序
   * 
   * 示例：
   * - 输入：a > 10 -> 输出："a greater_than 10 integer"
   * - 输入：name IS NULL -> 输出："name isnull"
   */
  private String translateMatch2(RexNode node) {
    // 根据节点类型（SqlKind）进行分支处理
    switch (node.getKind()) {
    // EQUALS：等于操作符（=）
    case EQUALS:
      // 调用 translateBinary() 翻译二元操作符
      // "equal" 是 Gandiva 的等于操作符
      // "=" 是反向操作符（如果第一次尝试失败，会尝试反向）
      return translateBinary("equal", "=", (RexCall) node);
    // NOT_EQUALS：不等于操作符（<> 或 !=）
    case NOT_EQUALS:
      return translateBinary("not_equal", "<>", (RexCall) node);
    // LESS_THAN：小于操作符（<）
    case LESS_THAN:
      // 注意：Gandiva 的 less_than 参数顺序与 SQL 相反
      // SQL: a < b -> Gandiva: b less_than a
      // 因此这里使用 ">" 作为反向操作符
      return translateBinary("less_than", ">", (RexCall) node);
    // LESS_THAN_OR_EQUAL：小于等于操作符（<=）
    case LESS_THAN_OR_EQUAL:
      return translateBinary("less_than_or_equal_to", ">=", (RexCall) node);
    // GREATER_THAN：大于操作符（>）
    case GREATER_THAN:
      return translateBinary("greater_than", "<", (RexCall) node);
    // GREATER_THAN_OR_EQUAL：大于等于操作符（>=）
    case GREATER_THAN_OR_EQUAL:
      return translateBinary("greater_than_or_equal_to", "<=", (RexCall) node);
    // IS_NULL：判断是否为 NULL
    case IS_NULL:
      // 调用 translateUnary() 翻译一元操作符
      return translateUnary("isnull", (RexCall) node);
    // IS_NOT_NULL：判断是否不为 NULL
    case IS_NOT_NULL:
      return translateUnary("isnotnull", (RexCall) node);
    // IS_NOT_TRUE：判断是否不为 TRUE
    case IS_NOT_TRUE:
      return translateUnary("isnottrue", (RexCall) node);
    // IS_NOT_FALSE：判断是否不为 FALSE
    case IS_NOT_FALSE:
      return translateUnary("isnotfalse", (RexCall) node);
    // INPUT_REF：字段引用（列名）
    case INPUT_REF:
      // 将 RexCall 强制转换为 RexInputRef
      final RexInputRef inputRef = (RexInputRef) node;
      // 获取字段名并添加 " istrue" 后缀
      // "istrue" 是 Gandiva 的布尔判断操作符
      return fieldNames.get(inputRef.getIndex()) + " istrue";
    // NOT：逻辑非操作符（! 或 NOT）
    case NOT:
      // 将 NOT 转换为 "isfalse" 操作符
      return translateUnary("isfalse", (RexCall) node);
    // 其他不支持的操作符
    default:
      // 抛出不支持异常
      throw new UnsupportedOperationException("Unsupported operator " + node);
    }
  }

  /**
   * 翻译二元操作符调用，必要时反转参数
   * 方法作用：处理二元操作符，尝试两种参数顺序以适应 Gandiva 的操作符定义
   * 
   * @param op Gandiva 操作符名称（如 "equal", "less_than"）
   * @param rop 反向操作符名称（如果第一种顺序失败，尝试此操作符）
   * @param call 二元操作符调用表达式
   * @return 翻译后的过滤字符串
   * 
   * 翻译策略：
   * 1. 首先尝试使用 op 操作符，保持原始参数顺序（left, right）
   * 2. 如果失败，尝试使用 rop 操作符，反转参数顺序（right, left）
   * 3. 如果两种方式都失败，抛出不支持异常
   * 
   * 为什么需要反转参数：
   * - Gandiva 的某些操作符参数顺序与 SQL 相反
   * - 例如：SQL 的 a > b 对应 Gandiva 的 b less_than a
   * - 通过尝试两种顺序，可以正确处理所有情况
   * 
   * 示例：
   * - 输入：a > 10, op="greater_than", rop="<"
   * - 第一次尝试：a greater_than 10（失败，因为 Gandiva 不支持）
   * - 第二次尝试：10 less_than a（成功）
   */
  private String translateBinary(String op, String rop, RexCall call) {
    // 获取操作符的左操作数（第一个操作数）
    final RexNode left = call.operands.get(0);
    // 获取操作符的右操作数（第二个操作数）
    final RexNode right = call.operands.get(1);
    // 尝试使用 op 操作符翻译，保持原始参数顺序（left, right）
    @Nullable String expression = translateBinary2(op, left, right);
    // 如果翻译成功（expression 不为 null），直接返回结果
    if (expression != null) {
      return expression;
    }
    // 第一次尝试失败，尝试使用 rop 操作符，反转参数顺序（right, left）
    expression = translateBinary2(rop, right, left);
    // 如果第二次尝试成功，返回结果
    if (expression != null) {
      return expression;
    }
    // 两次尝试都失败，抛出不支持异常
    throw new UnsupportedOperationException("Unsupported binary operator " + call);
  }

  /** 翻译二元操作符调用，失败时返回 null
   * 方法作用：尝试将二元操作符翻译为 Gandiva 表达式，只支持特定的操作数组合
   * 
   * @param op Gandiva 操作符名称
   * @param left 左操作数
   * @param right 右操作数
   * @return 翻译后的字符串，失败时返回 null
   * 
   * 支持的操作数组合：
   * 1. 左操作数是 INPUT_REF（字段引用），右操作数是 LITERAL（字面量）
   *    - 例如：a > 10，其中 a 是字段，10 是字面量
   * 2. 左操作数是 CAST（类型转换），右操作数是 LITERAL
   *    - 例如：CAST(a AS INTEGER) > 10
   *    - 会递归处理 CAST 表达式
   * 
   * 不支持的操作数组合：
   * - 字段 vs 字段：a > b（不支持）
   * - 字面量 vs 字段：10 > a（由 translateBinary 的反转处理）
   * - 复杂表达式：a + b > 10（不支持）
   * 
   * 递归处理 CAST：
   * - 如果左操作数是 CAST，会提取 CAST 的操作数继续处理
   * - 例如：CAST(a AS INTEGER) > 10 -> a > 10
   * - 注意：这可能忽略类型转换的语义（FIXME 注释）
   */
  private @Nullable String translateBinary2(String op, RexNode left, RexNode right) {
    // 检查右操作数是否为字面量（LITERAL）
    // 当前只支持 字段 操作符 字面量 的形式
    if (right.getKind() != SqlKind.LITERAL) {
      // 如果右操作数不是字面量，返回 null 表示无法翻译
      return null;
    }
    // 将右操作数强制转换为 RexLiteral
    final RexLiteral rightLiteral = (RexLiteral) right;
    // 根据左操作数的类型进行分支处理
    switch (left.getKind()) {
    // INPUT_REF：字段引用（列名）
    case INPUT_REF:
      // 将左操作数强制转换为 RexInputRef
      final RexInputRef left1 = (RexInputRef) left;
      // 根据字段索引获取字段名
      String name = fieldNames.get(left1.getIndex());
      // 调用 translateOp2() 组合字段名、操作符和字面量
      return translateOp2(op, name, rightLiteral);
    // CAST：类型转换表达式
    case CAST:
      // FIXME 注释：这在所有情况下都不起作用（例如，我们忽略字符串编码）
      // 递归处理：提取 CAST 的操作数（第一个操作数），继续尝试翻译
      // 例如：CAST(a AS INTEGER) > 10 -> a > 10
      // 注意：这可能忽略类型转换的语义，导致错误结果
      return translateBinary2(op, ((RexCall) left).operands.get(0), right);
    // 其他类型的左操作数不支持
    default:
      // 返回 null 表示无法翻译
      return null;
    }
  }

  /** 组合字段名、操作符和字面量生成谓词字符串
   * 方法作用：将翻译后的各部分组合成最终的 Gandiva 过滤字符串
   * 
   * @param op Gandiva 操作符名称（如 "equal", "greater_than"）
   * @param name 字段名
   * @param right 字面量值
   * @return 格式化的过滤字符串，格式：字段名 操作符 值 类型
   * 
   * 输出格式：
   * - 字符串值：字段名 操作符 '值' 类型（如 "name equal 'test' string"）
   * - 非字符串值：字段名 操作符 值 类型（如 "age greater_than 18 integer"）
   * 
   * 类型后缀：
   * - integer：整数类型
   * - float：单精度浮点数
   * - double：双精度浮点数
   * - string：字符串类型
   * - decimal(precision,scale)：十进制数，指定精度和小数位数
   * 
   * 字符串引号处理：
   * - VARCHAR 类型：值需要用单引号括起来
   * - CHAR 类型：值不需要引号（固定长度字符）
   * 
   * 示例：
   * - 输入：op="equal", name="name", right="test" (VARCHAR)
   * - 输出："name equal 'test' string"
   * 
   * - 输入：op="greater_than", name="age", right=18 (INTEGER)
   * - 输出："age greater_than 18 integer"
   */
  private String translateOp2(String op, String name, RexLiteral right) {
    // 调用 literalValue() 提取字面量的实际值
    Object value = literalValue(right);
    // 将值转换为字符串
    String valueString = value.toString();
    // 调用 getLiteralType() 获取字面量的类型名称
    String valueType = getLiteralType(right.getType());

    // 检查值是否为字符串类型
    if (value instanceof String) {
      // 从行类型中获取指定字段的字段信息
      // requireNonNull 确保字段存在
      final RelDataTypeField field = requireNonNull(rowType.getField(name, true, false), "field");
      // 获取字段的 SQL 类型名称
      SqlTypeName typeName = field.getType().getSqlTypeName();
      // 如果字段类型不是 CHAR（固定长度字符），则需要添加引号
      // VARCHAR 类型需要引号，CHAR 类型不需要
      if (typeName != SqlTypeName.CHAR) {
        // 在字符串值前后添加单引号
        valueString = "'" + valueString + "'";
      }
    }
    // 组合字段名、操作符、值和类型，生成最终的过滤字符串
    // 格式：字段名 操作符 值 类型
    return name + " " + op + " " + valueString + " " + valueType;
  }

  /** 翻译一元操作符调用
   * 方法作用：处理只有一个操作数的操作符，如 IS NULL、IS NOT NULL 等
   * 
   * @param op Gandiva 一元操作符名称（如 "isnull", "isnotnull"）
   * @param call 一元操作符调用表达式
   * @return 翻译后的过滤字符串
   * 
   * 支持的一元操作符：
   * - isnull：判断是否为 NULL
   * - isnotnull：判断是否不为 NULL
   * - isnottrue：判断是否不为 TRUE
   * - isnotfalse：判断是否不为 FALSE
   * - isfalse：判断是否为 FALSE（用于 NOT 操作符）
   * 
   * 翻译流程：
   * 1. 获取操作符的操作数（第一个操作数）
   * 2. 调用 translateUnary2() 尝试翻译
   * 3. 如果翻译失败，抛出不支持异常
   * 
   * 示例：
   * - 输入：op="isnull", call=IS_NULL(name)
   * - 输出："name isnull"
   * 
   * - 输入：op="isnotnull", call=IS_NOT_NULL(age)
   * - 输出："age isnotnull"
   */
  private String translateUnary(String op, RexCall call) {
    // 获取操作符的操作数（第一个操作数）
    final RexNode opNode = call.operands.get(0);
    // 调用 translateUnary2() 尝试翻译一元操作符
    @Nullable String expression = translateUnary2(op, opNode);

    // 如果翻译成功（expression 不为 null），直接返回结果
    if (expression != null) {
      return expression;
    }

    // 翻译失败，抛出不支持异常
    throw new UnsupportedOperationException("Unsupported unary operator " + call);
  }

  /** 翻译一元操作符调用，失败时返回 null
   * 方法作用：尝试将一元操作符翻译为 Gandiva 表达式，只支持字段引用作为操作数
   * 
   * @param op Gandiva 一元操作符名称
   * @param opNode 操作数节点
   * @return 翻译后的字符串，失败时返回 null
   * 
   * 支持的操作数类型：
   * - INPUT_REF：字段引用（列名）
   *   - 例如：name IS NULL -> name isnull
   * 
   * 不支持的操作数类型：
   * - 字面量：10 IS NULL（无意义）
   * - 复杂表达式：a + b IS NULL（不支持）
   * - 函数调用：UPPER(name) IS NULL（不支持）
   * 
   * 翻译逻辑：
   * 1. 检查操作数是否为 INPUT_REF（字段引用）
   * 2. 如果是，获取字段名并调用 translateUnaryOp()
   * 3. 如果不是，返回 null 表示无法翻译
   * 
   * 示例：
   * - 输入：op="isnull", opNode=RexInputRef(name)
   * - 输出："name isnull"
   * 
   * - 输入：op="isnotnull", opNode=RexInputRef(age)
   * - 输出："age isnotnull"
   */
  private @Nullable String translateUnary2(String op, RexNode opNode) {
    // 检查操作数是否为 INPUT_REF（字段引用）
    if (opNode.getKind() == SqlKind.INPUT_REF) {
      // 将操作数强制转换为 RexInputRef
      final RexInputRef inputRef = (RexInputRef) opNode;
      // 根据字段索引获取字段名
      final String name = fieldNames.get(inputRef.getIndex());
      // 调用 translateUnaryOp() 组合字段名和操作符
      return translateUnaryOp(op, name);
    }

    // 操作数不是字段引用，返回 null 表示无法翻译
    return null;
  }

  /** 组合字段名和一元操作符生成谓词字符串
   * 方法作用：将字段名和一元操作符组合成最终的 Gandiva 过滤字符串
   * 
   * @param op Gandiva 一元操作符名称（如 "isnull", "isnotnull"）
   * @param name 字段名
   * @return 格式化的过滤字符串，格式：字段名 操作符
   * 
   * 输出格式：
   * - 字段名 操作符（如 "name isnull", "age isnotnull"）
   * 
   * 示例：
   * - 输入：op="isnull", name="name"
   * - 输出："name isnull"
   * 
   * - 输入：op="isnotnull", name="age"
   * - 输出："age isnotnull"
   * 
   * - 输入：op="isfalse", name="flag"
   * - 输出："flag isfalse"
   */
  private String translateUnaryOp(String op, String name) {
    // 组合字段名和操作符，用空格分隔
    return name + " " + op;
  }

  /** 获取字面量的类型名称
   * 方法作用：将 Calcite 的 SQL 类型映射为 Gandiva 的类型名称
   * 
   * @param type 字面量的关系数据类型
   * @return Gandiva 类型名称字符串
   * 
   * 类型映射：
   * - DECIMAL(precision,scale) -> decimal(precision,scale)
   *   - 例如：DECIMAL(10,2) -> decimal(10,2)
   *   - precision：总位数
   *   - scale：小数位数
   * 
   * - REAL -> float
   *   - 单精度浮点数
   * 
   * - DOUBLE -> double
   *   - 双精度浮点数
   * 
   * - INTEGER -> integer
   *   - 32位整数
   * 
   * - VARCHAR -> string
   *   - 可变长度字符串
   * 
   * - CHAR -> string
   *   - 固定长度字符串
   * 
   * 不支持的类型：
   * - TIMESTAMP：抛出异常
   * - DATE：抛出异常
   * - BOOLEAN：抛出异常
   * - BINARY：抛出异常
   * - 其他类型：抛出异常
   * 
   * 注意：
   * - 当前版本不支持时间戳、日期等复杂类型
   * - 所有字符串类型（VARCHAR、CHAR）都映射为 "string"
   */
  private static String getLiteralType(RelDataType  type) {
    // 检查是否为 DECIMAL 类型（十进制数）
    if (type.getSqlTypeName() == SqlTypeName.DECIMAL) {
      // 返回格式：decimal(precision,scale)
      // getPrecision() 获取总位数
      // getScale() 获取小数位数
      return "decimal" + "(" + type.getPrecision() + "," + type.getScale() + ")";
    // 检查是否为 REAL 类型（单精度浮点数）
    } else if (type.getSqlTypeName() == SqlTypeName.REAL) {
      // 返回 "float"
      return "float";
    // 检查是否为 DOUBLE 类型（双精度浮点数）
    } else if (type.getSqlTypeName() == SqlTypeName.DOUBLE) {
      // 返回 "double"
      return "double";
    // 检查是否为 INTEGER 类型（32位整数）
    } else if (type.getSqlTypeName() == SqlTypeName.INTEGER) {
      // 返回 "integer"
      return "integer";
    // 检查是否为 VARCHAR 或 CHAR 类型（字符串）
    } else if (type.getSqlTypeName() == SqlTypeName.VARCHAR
        || type.getSqlTypeName() == SqlTypeName.CHAR) {
      // 返回 "string"
      return "string";
    // 其他类型不支持
    } else {
      // 抛出不支持异常
      throw new UnsupportedOperationException("Unsupported type " + type);
    }
  }
}
