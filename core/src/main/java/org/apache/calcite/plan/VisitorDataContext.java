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
// 声明包名，表示该类属于 org.apache.calcite.plan 包，这是 Calcite 框架中负责查询计划相关功能的包
package org.apache.calcite.plan;

// 导入 DataContext 接口，这是 Calcite 中用于提供查询执行上下文的核心接口
import org.apache.calcite.DataContext;
// 导入 JavaTypeFactory，用于创建和管理 Java 类型系统中的类型
import org.apache.calcite.adapter.java.JavaTypeFactory;
// 导入 QueryProvider，用于提供 LINQ 风格的查询执行能力
import org.apache.calcite.linq4j.QueryProvider;
// 导入 RelNode，表示关系表达式树的节点，是 Calcite 关系代数模型的核心接口
import org.apache.calcite.rel.RelNode;
// 导入 LogicalFilter，表示逻辑过滤器操作节点，用于实现过滤条件
import org.apache.calcite.rel.logical.LogicalFilter;
// 导入 RelDataType，表示关系数据类型，描述表或表达式的类型信息
import org.apache.calcite.rel.type.RelDataType;
// 导入 RexCall，表示 Rex 表达式中的函数调用节点
import org.apache.calcite.rex.RexCall;
// 导入 RexInputRef，表示对输入字段的引用，用于引用关系表达式中的输入列
import org.apache.calcite.rex.RexInputRef;
// 导入 RexLiteral，表示常量字面量表达式，如数字、字符串等常量值
import org.apache.calcite.rex.RexLiteral;
// 导入 RexNode，所有 Rex 表达式的基类，表示行表达式（Row Expression）
import org.apache.calcite.rex.RexNode;
// 导入 RexUtil，提供 Rex 表达式相关的工具方法
import org.apache.calcite.rex.RexUtil;
// 导入 SchemaPlus，表示 Calcite 的模式（Schema）对象，包含表、函数等元数据
import org.apache.calcite.schema.SchemaPlus;
// 导入 NlsString，表示支持国际化（NLS）的字符串类型
import org.apache.calcite.util.NlsString;
// 导入 Pair，表示键值对，用于存储两个相关联的对象
import org.apache.calcite.util.Pair;
// 导入 CalciteLogger，Calcite 框架专用的日志记录器
import org.apache.calcite.util.trace.CalciteLogger;

// 导入 @Nullable 注解，用于标记可能为 null 的类型，用于静态空值检查
import org.checkerframework.checker.nullness.qual.Nullable;
// 导入 LoggerFactory，用于创建 SLF4J 日志记录器的工厂类
import org.slf4j.LoggerFactory;

// 导入 BigDecimal，用于高精度的十进制数值计算
import java.math.BigDecimal;
// 导入 List，表示有序的集合接口
import java.util.List;

/**
 * 用于评估 RexExpression（行表达式）的 DataContext 实现
 *
 * 类作用说明：
 * -----------
 * VisitorDataContext 是 DataContext 接口的一个特殊实现，主要用于在查询优化和验证阶段
 * 评估 Rex 表达式的值。它不是用于实际执行查询，而是用于：
 *
 * 1. 表达式蕴含检查（Implication Checking）：检查一个条件是否蕴含另一个条件
 * 2. 查询优化：在优化器中评估表达式，帮助做出优化决策
 * 3. 表达式简化：通过提供具体的值来简化表达式
 *
 * 该类的工作原理是：
 * - 维护一个值数组，对应关系表达式的输入字段
 * - 当需要评估表达式时，提供这些值作为上下文
 * - 主要用于处理形如 "字段 = 常量" 这样的简单条件
 *
 * 应用场景：
 * - 在检查谓词蕴含时，需要评估某个谓词是否在给定条件下成立
 * - 在子查询重写和优化中，需要评估条件表达式的结果
 * - 在谓词下推优化中，需要评估过滤条件的有效性
 */
public class VisitorDataContext implements DataContext {
  // 创建静态日志记录器，用于记录该类运行时的警告和错误信息
  // 使用 CalciteLogger 包装 SLF4J 的 LoggerFactory，提供统一的日志接口
  private static final CalciteLogger LOGGER =
      new CalciteLogger(LoggerFactory.getLogger(VisitorDataContext.class.getName()));

  // 成员变量：存储输入记录的值数组
  // 作用：
  // - values 数组对应关系表达式的输入字段，每个元素代表一个字段的值
  // - 数组的索引对应字段在关系类型中的位置（从 0 开始）
  // - 例如：如果关系类型有 3 个字段，values[0] 存储第 1 个字段的值，values[1] 存储第 2 个字段的值
  // - 该数组可能包含 null 值，表示某些字段的值未知或未设置
  // - 使用 @Nullable 注解标记，表示该数组本身可能为 null
  private final @Nullable Object[] values;

  // 构造方法：创建 VisitorDataContext 实例
  // 参数说明：
  // - values: 输入记录的值数组，可以为 null
  //           数组的每个元素对应一个输入字段的值，索引与字段位置对应
  //
  // 构造逻辑：
  // - 将传入的值数组保存到成员变量 values 中
  // - 该构造方法非常简单，只是保存引用，不做任何复制或验证
  //
  // 使用场景：
  // - 在静态工厂方法 of() 中创建实例时使用
  // - 当已经准备好值数组时，可以直接构造实例
  public VisitorDataContext(@Nullable Object[] values) {
    this.values = values;
  }

  // 实现 DataContext 接口方法：获取根模式（Schema）
  //
  // 方法作用：
  // - 返回当前数据上下文的根 Schema，包含所有可用的表、函数等元数据
  //
  // 实现说明：
  // - 该方法直接抛出 RuntimeException 异常，标记为 "Unsupported"（不支持）
  // - 原因：VisitorDataContext 不是用于实际执行查询，而是用于表达式评估
  // - 实际查询执行需要完整的 Schema 信息，但 VisitorDataContext 不需要
  // - 如果调用此方法，说明使用方式错误
  //
  // 返回值：永远不返回，总是抛出异常
  @Override public SchemaPlus getRootSchema() {
    throw new RuntimeException("Unsupported");
  }

  // 实现 DataContext 接口方法：获取类型工厂
  //
  // 方法作用：
  // - 返回 JavaTypeFactory 实例，用于创建和转换 Java 类型
  // - 类型工厂负责在 SQL 类型和 Java 类型之间进行映射
  //
  // 实现说明：
  // - 该方法直接抛出 RuntimeException 异常，标记为 "Unsupported"（不支持）
  // - 原因：VisitorDataContext 不需要类型转换功能，它只处理简单的值评估
  // - 如果调用此方法，说明使用方式错误
  //
  // 返回值：永远不返回，总是抛出异常
  @Override public JavaTypeFactory getTypeFactory() {
    throw new RuntimeException("Unsupported");
  }

  // 实现 DataContext 接口方法：获取查询提供者
  //
  // 方法作用：
  // - 返回 QueryProvider 实例，用于执行 LINQ 风格的查询
  // - QueryProvider 提供了创建和执行可枚举查询的能力
  //
  // 实现说明：
  // - 该方法直接抛出 RuntimeException 异常，标记为 "Unsupported"（不支持）
  // - 原因：VisitorDataContext 不执行实际的查询，只是提供评估上下文
  // - 如果调用此方法，说明使用方式错误
  //
  // 返回值：永远不返回，总是抛出异常
  @Override public QueryProvider getQueryProvider() {
    throw new RuntimeException("Unsupported");
  }

  // 实现 DataContext 接口方法：根据名称获取值
  //
  // 方法作用：
  // - 根据变量名获取对应的值
  // - 这是 DataContext 接口的核心方法，用于在表达式评估时获取变量的值
  //
  // 参数说明：
  // - name: 变量名，标识要获取的值
  //
  // 实现说明：
  // - 如果 name 等于 "inputRecord"，返回 values 数组
  // - "inputRecord" 是一个特殊的预定义名称，用于访问输入记录的所有字段值
  // - 对于其他名称，返回 null，表示不支持该变量
  // - 这个设计允许表达式通过 "inputRecord" 访问整个输入记录
  //
  // 返回值：
  // - 如果 name 是 "inputRecord"，返回 values 数组（可能为 null）
  // - 否则返回 null
  @Override public @Nullable Object get(String name) {
    // 检查请求的变量名是否为 "inputRecord"
    if (name.equals("inputRecord")) {
      // 如果是，返回存储的值数组
      return values;
    } else {
      // 否则返回 null，表示不支持的变量名
      return null;
    }
  }
  // 静态工厂方法：从关系节点和逻辑过滤器创建 DataContext
  //
  // 方法作用：
  // - 根据目标关系节点（targetRel）和查询过滤器（queryRel）创建 DataContext
  // - 这是一个便捷方法，简化了从 RelNode 创建 DataContext 的过程
  //
  // 参数说明：
  // - targetRel: 目标关系节点，表示要操作的关系表达式
  //              通过 getRowType() 可以获取该节点的行类型信息
  // - queryRel: 逻辑过滤器节点，包含过滤条件
  //             通过 getCondition() 可以获取过滤条件表达式
  //
  // 实现说明：
  // - 提取目标关系节点的行类型（row type）
  // - 提取逻辑过滤器的条件表达式（condition）
  // - 调用另一个重载的 of() 方法来创建 DataContext
  //
  // 返回值：
  // - 成功时返回 VisitorDataContext 实例
  // - 如果无法解析条件，返回 null
  public static @Nullable DataContext of(RelNode targetRel, LogicalFilter queryRel) {
    // 调用重载方法，传入目标关系节点的行类型和过滤器的条件表达式
    return of(targetRel.getRowType(), queryRel.getCondition());
  }

  // 静态工厂方法：从行类型和表达式创建 DataContext
  //
  // 方法作用：
  // - 根据行类型（rowType）和 Rex 表达式（rex）创建 DataContext
  // - 该方法处理二元比较表达式（如 "字段 = 常量"）
  // - 提取表达式中的字段引用和常量值，构造值数组
  //
  // 参数说明：
  // - rowType: 关系类型的元数据，描述行的结构（字段列表、类型等）
  // - rex: Rex 表达式，通常是二元比较表达式（如 =, <, > 等）
  //        期望该表达式是一个 RexCall，包含两个操作数
  //
  // 实现逻辑：
  // 1. 获取行类型中的字段数量，确定值数组的大小
  // 2. 将 rex 强制转换为 RexCall，获取操作数列表
  // 3. 提取第一个操作数（通常是字段引用）
  // 4. 提取第二个操作数（通常是常量字面量）
  // 5. 调用 getValue() 方法解析字段索引和值
  // * 6. 如果解析成功，创建值数组并填充对应的值
  // * 7. 返回 VisitorDataContext 实例
  // *
  // * 限制：
  // * - 只处理二元表达式（两个操作数）
  // * - 期望第一个操作数是字段引用，第二个是常量
  // * - 不支持更复杂的表达式
  // *
  // * 返回值：
  // * - 成功时返回 VisitorDataContext 实例
  // * - 如果表达式格式不符合要求或无法解析，返回 null
  // */
  public static @Nullable DataContext of(RelDataType rowType, RexNode rex) {
    // 获取行类型中的字段数量，用于确定值数组的大小
    // fieldList() 返回所有字段的列表，size() 返回字段数量
    final int size = rowType.getFieldList().size();
    // 将 rex 强制转换为 RexCall（函数调用表达式）
    // RexCall 表示函数调用，二元比较操作也是函数调用的一种
    final List<RexNode> operands = ((RexCall) rex).getOperands();
    // 获取第一个操作数，通常是字段引用（RexInputRef）
    final RexNode firstOperand = operands.get(0);
    // 获取第二个操作数，通常是常量字面量（RexLiteral）
    final RexNode secondOperand = operands.get(1);
    // 调用 getValue() 方法解析字段索引和对应的值
    // getValue() 会处理类型转换和值提取
    final Pair<Integer, ?> value = getValue(firstOperand, secondOperand);
    // 如果成功解析出值（value 不为 null）
    if (value != null) {
      // 创建值数组，大小等于字段数量
      // 数组初始化为 null，表示大部分字段的值未设置
      final @Nullable Object[] values = new Object[size];
      // 获取字段索引（在值数组中的位置）
      int index = value.getKey();
      // 在对应位置设置值
      values[index] = value.getValue();
      // 创建并返回 VisitorDataContext 实例
      return new VisitorDataContext(values);
    } else {
      // 如果无法解析值，返回 null
      return null;
    }
  }

  // 静态工厂方法：从行类型和使用列表创建 DataContext
  //
  // 方法作用：
  // - 根据行类型和字段引用-值对列表创建 DataContext
  // - 该方法可以处理多个字段的赋值，比前一个方法更通用
  // * - 用于构建包含多个字段值的 DataContext
  // *
  // * 参数说明：
  // * - rowType: 关系类型的元数据，描述行的结构
  // * - usageList: 字段引用和表达式的配对列表
  // *              每个元素的 key 是 RexInputRef（字段引用）
  // *              每个元素的 value 是 RexNode（通常是常量表达式）
  // *              List 使用通配符 ? extends 表示支持多种 RexNode 子类型
  // *
  // * 实现逻辑：
  // * 1. 获取行类型中的字段数量
  // * 2. 创建值数组，初始化为 null
  // * 3. 遍历 usageList 中的每个配对
  // * 4. 对每个配对，调用 getValue() 解析字段索引和值
  // * 5. 如果任一配对解析失败，记录警告并返回 null
  // * 6. 将解析出的值填充到对应位置
  // * 7. 返回填充好的 VisitorDataContext 实例
  // *
  // * 错误处理：
  // * - 如果任何一个字段-值对无法解析，整个方法返回 null
  // * - 使用日志记录器记录警告信息，便于调试
  // *
  // * 返回值：
  // * - 所有字段都成功解析时，返回 VisitorDataContext 实例
  // * - 任一字段解析失败时，返回 null
  // */
  public static @Nullable DataContext of(RelDataType rowType,
      List<? extends Pair<RexInputRef, ? extends @Nullable RexNode>> usageList) {
    // 获取行类型中的字段数量
    final int size = rowType.getFieldList().size();
    // 创建值数组，初始化为 null
    final @Nullable Object[] values = new Object[size];
    // 遍历使用列表中的每个字段-值对
    for (Pair<RexInputRef, ? extends @Nullable RexNode> elem : usageList) {
      // 调用 getValue() 解析字段索引和值
      // elem.getKey() 是字段引用，elem.getValue() 是表达式
      Pair<Integer, ?> value = getValue(elem.getKey(), elem.getValue());
      // 如果解析失败
      if (value == null) {
        // 记录警告日志，说明该字段-值对无法处理
        LOGGER.warn("{} is not handled for {} for checking implication",
            elem.getKey(), elem.getValue());
        // 返回 null，表示创建失败
        return null;
      }
      // 获取字段索引
      int index = value.getKey();
      // 在对应位置设置值
      values[index] = value.getValue();
    }
    // 所有字段都成功解析，创建并返回 DataContext
    return new VisitorDataContext(values);
  }

  // 静态辅助方法：从字段引用和常量表达式中提取字段索引和值
  // *
  // * 方法作用：
  // * - 核心解析方法，从 RexInputRef 和 RexLiteral 中提取字段索引和对应的 Java 值
  // * - 处理各种 SQL 类型的值转换
  // * - 移除类型转换（CAST）操作，直接获取底层值
  // *
  // * 参数说明：
  // * - inputRef: 字段引用（RexInputRef），指向输入行中的某个字段
  // *             可能为 null，表示没有字段引用
  // * - literal: 常量表达式（RexLiteral），表示一个常量值
  // *            可能为 null，表示没有常量值
  // *
  // * 返回值：
  // * - 成功时返回 Pair<Integer, Object>，其中：
  // *   - Integer: 字段索引（在输入行中的位置）
  // *   - Object: 转换后的 Java 值（根据 SQL 类型映射到对应的 Java 类型）
  // * - 失败时返回 null，包括以下情况：
  // *     * inputRef 不是 RexInputRef 类型
  // *     * literal 不是 RexLiteral 类型
  // *     * 类型信息缺失（SqlTypeName 为 null）
  // *     * 不支持的类型
  // *
  // * 实现逻辑：
  // * 1. 移除输入引用和常量上的类型转换（CAST）
  // * 2. 验证参数类型是否正确
  // * 3. 获取字段索引
  // * 4. 获取字段类型信息
  // * 5. 根据类型进行值转换
  // * 6. 返回字段索引和转换后的值
  // *
  // * 类型映射规则：
  // * - INTEGER -> Integer
  // * - FLOAT/DOUBLE -> Double
  // * - REAL -> Float
  // * - BIGINT -> Long
  // * - SMALLINT -> Short
  // * - TINYINT -> Byte
  // * - DECIMAL -> BigDecimal
  // * - DATE/TIME -> Integer
  // * - TIMESTAMP -> Long
  // * - CHAR -> Character
  // * - VARCHAR -> String
  // * - 其他类型 -> 尝试使用 Comparable 接口
  // */
  public static @Nullable Pair<Integer, ? extends @Nullable Object> getValue(
      @Nullable RexNode inputRef, @Nullable RexNode literal) {
    // 如果 inputRef 不为 null，移除可能的类型转换（CAST）操作
    // RexUtil.removeCast() 会剥离外层的 CAST 表达式，返回底层表达式
    inputRef = inputRef == null ? null : RexUtil.removeCast(inputRef);
    // 如果 literal 不为 null，移除可能的类型转换（CAST）操作
    literal = literal == null ? null : RexUtil.removeCast(literal);

    // 验证参数类型：inputRef 必须是 RexInputRef，literal 必须是 RexLiteral
    // 只有这两种类型组合才能被正确解析
    if (inputRef instanceof RexInputRef
        && literal instanceof RexLiteral)  {
      // 获取字段在输入行中的索引位置
      // RexInputRef.getIndex() 返回该引用指向的字段索引（从 0 开始）
      final int index = ((RexInputRef) inputRef).getIndex();
      // 将 literal 强制转换为 RexLiteral，以便获取常量值
      final RexLiteral rexLiteral = (RexLiteral) literal;
      // 获取字段的类型信息
      // inputRef.getType() 返回该字段的 RelDataType，包含 SQL 类型等信息
      final RelDataType type = inputRef.getType();

      // 检查 SQL 类型名称是否为 null
      // 如果为 null，说明类型信息不完整，无法进行类型转换
      if (type.getSqlTypeName() == null) {
        // 记录警告日志，说明该字段的 SqlTypeName 为 null
        LOGGER.warn("{} returned null SqlTypeName", inputRef.toString());
        // 返回 null，表示解析失败
        return null;
      }

      // 根据字段的 SQL 类型进行值转换
      // 使用 switch 语句处理不同的 SQL 类型
      switch (type.getSqlTypeName()) {
      // 处理 INTEGER 类型（32 位整数）
      case INTEGER:
        // 将 RexLiteral 的值转换为 Integer 类型
        // getValueAs() 方法根据指定类型进行安全的类型转换
        return Pair.of(index, rexLiteral.getValueAs(Integer.class));
      // 处理 FLOAT 和 DOUBLE 类型（浮点数）
      case FLOAT:
      case DOUBLE:
        // 将值转换为 Double 类型（64 位浮点数）
        return Pair.of(index, rexLiteral.getValueAs(Double.class));
      // 处理 REAL 类型（单精度浮点数）
      case REAL:
        // 将值转换为 Float 类型（32 位浮点数）
        return Pair.of(index, rexLiteral.getValueAs(Float.class));
      // 处理 BIGINT 类型（64 位整数）
      case BIGINT:
        // 将值转换为 Long 类型
        return Pair.of(index, rexLiteral.getValueAs(Long.class));
      // 处理 SMALLINT 类型（16 位整数）
      case SMALLINT:
        // 将值转换为 Short 类型
        return Pair.of(index, rexLiteral.getValueAs(Short.class));
      // 处理 TINYINT 类型（8 位整数）
      case TINYINT:
        // 将值转换为 Byte 类型
        return Pair.of(index, rexLiteral.getValueAs(Byte.class));
      // 处理 DECIMAL 类型（高精度十进制数）
      case DECIMAL:
        // 将值转换为 BigDecimal 类型，保持精确的小数精度
        return Pair.of(index, rexLiteral.getValueAs(BigDecimal.class));
      // 处理 DATE 和 TIME 类型
      case DATE:
      case TIME:
        // 将值转换为 Integer 类型
        // Calcite 中 DATE 和 TIME 内部使用整数表示（天数或毫秒数）
        return Pair.of(index, rexLiteral.getValueAs(Integer.class));
      // 处理 TIMESTAMP 类型（时间戳）
      case TIMESTAMP:
        // 将值转换为 Long 类型
        // Calcite 中 TIMESTAMP 内部使用长整数表示（毫秒或微秒数）
        return Pair.of(index, rexLiteral.getValueAs(Long.class));
      // 处理 CHAR 类型（固定长度字符）
      case CHAR:
        // 将值转换为 Character 类型
        // 注意：CHAR 类型可能包含多个字符，这里只取第一个字符
        return Pair.of(index, rexLiteral.getValueAs(Character.class));
      // 处理 VARCHAR 类型（可变长度字符串）
      case VARCHAR:
        // 将值转换为 String 类型，这是最常见的字符串类型
        return Pair.of(index, rexLiteral.getValueAs(String.class));
      // 默认情况：处理未明确列出的类型
      default:
        // TODO 注释：标记这里需要支持更多类型
        // TODO: Support few more supported cases
        // 获取常量的值，使用 Comparable 接口
        Comparable value = rexLiteral.getValue();
        // 记录警告日志，说明该类型使用了默认处理方式
        LOGGER.warn("{} for value of class {} is being handled in default way",
            type.getSqlTypeName(), value == null ? null : value.getClass());
        // 特殊处理 NlsString 类型（国际化字符串）
        // NlsString 包含字符串值和字符集信息
        if (value instanceof NlsString) {
          // 提取 NlsString 中的实际字符串值
          return Pair.of(index, ((NlsString) value).getValue());
        } else {
          // 对于其他类型，直接返回原始值
          return Pair.of(index, value);
        }
      }
    }

    // 如果参数类型不符合要求，返回 null
    // 不支持的参数类型包括：
    // - inputRef 不是 RexInputRef
    // - literal 不是 RexLiteral
    // Unsupported Arguments
    return null;
  }

}
