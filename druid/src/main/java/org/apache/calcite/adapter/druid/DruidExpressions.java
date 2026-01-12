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
package org.apache.calcite.adapter.druid; // Druid适配器包，包含将Calcite查询转换为Druid查询的相关类

import org.apache.calcite.rel.type.RelDataType; // 导入Calcite的关系数据类型类，用于描述关系模式
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示Rex表达式中的函数调用
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类，表示对输入行的字段引用
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，表示常量字面量表达式
import org.apache.calcite.rex.RexNode; // 导入RexNode基类，表示行表达式(Row Expression)的抽象语法树节点
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举，定义SQL操作的种类
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，表示SQL操作符
import org.apache.calcite.sql.type.SqlTypeFamily; // 导入SqlTypeFamily枚举，定义SQL类型族
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL类型名称

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类
import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变映射类
import com.google.common.io.BaseEncoding; // 导入Google Guava的Base64编码工具类
import com.google.common.primitives.Chars; // 导入Google Guava的字符数组工具类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，用于标记可能为null的值

import java.math.BigDecimal; // 导入Java的BigDecimal类，用于精确的十进制计算
import java.util.ArrayList; // 导入Java的ArrayList动态数组类
import java.util.Arrays; // 导入Java的Arrays工具类，提供数组操作方法
import java.util.List; // 导入Java的List接口，表示有序集合
import java.util.Map; // 导入Java的Map接口，表示键值对映射
import java.util.TimeZone; // 导入Java的TimeZone类，表示时区信息

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于参数非空检查

/**
 * Expression utility class to transform Calcite expressions to Druid expressions when possible.
 * 表达式工具类，用于在可能的情况下将Calcite表达式转换为Druid表达式。
 *
 * 这个类是Calcite到Druid适配器的核心工具类之一，主要负责：
 * 1. 将Calcite的Rex表达式（RexNode）转换为Druid原生的表达式字符串
 * 2. 提供各种Druid表达式构建方法（字面量、列引用、函数调用等）
 * 3. 维护Calcite类型到Druid类型的映射关系
 * 4. 处理字符串转义和特殊字符
 *
 * Druid表达式是Druid查询引擎使用的原生表达式格式，用于在查询中指定过滤条件、
 * 聚合操作、投影字段等。Calcite使用RexNode抽象语法树来表示表达式，而Druid使用
 * 字符串格式的表达式。这个类负责在这两种表示之间进行转换。
 *
 * 关键概念：
 * - RexNode: Calcite的行表达式抽象语法树节点，包括输入引用(RexInputRef)、字面量(RexLiteral)、函数调用(RexCall)等
 * - Druid Expression: Druid原生的字符串格式表达式，例如 "column_name"、"function(arg1, arg2)"
 * - 类型映射: Calcite的SQL类型需要映射到Druid的原生类型（LONG、DOUBLE、STRING、COMPLEX等）
 *
 * 使用场景：
 * - 在将Calcite的RelNode转换为Druid查询时，需要将表达式转换为Druid格式
 * - 在构建Druid的过滤条件、聚合函数、投影字段时使用
 * - 在处理时间相关的操作（时间截断、时间提取）时使用
 */
public class DruidExpressions { // 定义Druid表达式工具类，所有方法都是静态方法

  /** Type mapping between Calcite SQL family types and native Druid expression
   * types. */
  // 类型映射：将Calcite SQL类型族映射到Druid原生表达式类型
  // 这是一个静态常量映射表，用于在转换表达式时确定目标Druid类型
  // 键是SqlTypeName（Calcite的SQL类型名称），值是DruidType（Druid的原生类型）
  static final Map<SqlTypeName, DruidType> EXPRESSION_TYPES; // 类型映射表，静态不可变映射

  /**
   * Druid expression safe chars, must be sorted.
   * Druid表达式安全字符列表，必须排序
   *
   * 这个字符数组包含了在Druid字符串字面量中不需要转义的字符。
   * 当构建字符串字面量时，如果字符在这个列表中，直接使用；否则需要进行Unicode转义。
   *
   * 安全字符包括：空格、逗号、点、下划线、连字符、分号、冒号、括号、花括号、方括号、
   * 尖括号、感叹号、@符号、井号、美元符号、百分号、脱字符、和符号、星号、反引号、波浪号、
   * 问号、斜杠等。
   *
   * 这个数组必须保持排序状态，因为escape方法使用Arrays.binarySearch进行快速查找。
   */
  private static final char[] SAFE_CHARS = " ,._-;:(){}[]<>!@#$%^&*`~?/".toCharArray(); // 安全字符数组，静态常量

  static { // 静态初始化块，用于初始化静态成员变量
    final ImmutableMap.Builder<SqlTypeName, DruidType> builder = ImmutableMap.builder(); // 创建不可变映射构建器

    for (SqlTypeName type : SqlTypeName.FRACTIONAL_TYPES) { // 遍历所有分数类型（浮点数类型）
      builder.put(type, DruidType.DOUBLE); // 将Calcite的分数类型映射到Druid的DOUBLE类型
    }

    for (SqlTypeName type : SqlTypeName.INT_TYPES) { // 遍历所有整数类型
      builder.put(type, DruidType.LONG); // 将Calcite的整数类型映射到Druid的LONG类型
    }

    for (SqlTypeName type : SqlTypeName.STRING_TYPES) { // 遍历所有字符串类型
      builder.put(type, DruidType.STRING); // 将Calcite的字符串类型映射到Druid的STRING类型
    }

    // booleans in expressions are returned from druid as long.
    // Druid will return 0 for false, non-zero value for true and null for absent value.
    // 布尔值在表达式中被Druid以long类型返回
    // Druid返回0表示false，非零值表示true，null表示值不存在
    for (SqlTypeName type : SqlTypeName.BOOLEAN_TYPES) { // 遍历所有布尔类型
      builder.put(type, DruidType.LONG); // 将Calcite的布尔类型映射到Druid的LONG类型
    }

    // Timestamps are treated as longs (millis since the epoch) in Druid expressions.
    // 时间戳在Druid表达式中被当作long类型处理（自纪元以来的毫秒数）
    builder.put(SqlTypeName.TIMESTAMP, DruidType.LONG); // 将TIMESTAMP类型映射到LONG类型
    builder.put(SqlTypeName.DATE, DruidType.LONG); // 将DATE类型映射到LONG类型
    builder.put(SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE, DruidType.LONG); // 将带本地时区的时间戳映射到LONG类型
    builder.put(SqlTypeName.OTHER, DruidType.COMPLEX); // 将OTHER类型（复杂类型）映射到COMPLEX类型
    EXPRESSION_TYPES = builder.build(); // 构建不可变映射并赋值给EXPRESSION_TYPES常量
    // Safe chars must be sorted
    // 安全字符必须排序，以便使用二分查找
    Arrays.sort(SAFE_CHARS); // 对安全字符数组进行排序
  }
  private DruidExpressions() { // 私有构造函数，防止实例化
    // 私有构造函数确保这个工具类不能被实例化
    // 所有方法都是静态方法，不需要创建对象
  }


  /**
   * Translates a Calcite {@link RexNode} to a Druid expression, if possible;
   * returns null if not possible.
   * 将Calcite的RexNode转换为Druid表达式，如果可能的话；如果不可能则返回null。
   *
   * 这是整个类最核心的方法，负责将Calcite的表达式抽象语法树转换为Druid的字符串表达式。
   *
   * 方法支持的RexNode类型：
   * 1. RexInputRef（输入引用）：转换为列引用表达式，格式为 "column_name"
   * 2. RexCall（函数调用）：通过DruidSqlOperatorConverter转换为对应的Druid表达式
   * 3. RexLiteral（字面量）：根据类型转换为数字、字符串等字面量表达式
   *
   * 特殊处理：
   * - 时间戳字段：如果引用的是Druid表的时间戳字段，使用特殊的默认列名
   * - NULL字面量：返回null，让Calcite处理
   * - 时间类型：使用DruidDateTimeUtils进行转换
   * - 布尔类型：转换为0或1的数字字面量
   *
   * @param rexNode RexNode to convert to a Druid Expression
   *                 要转换为Druid表达式的RexNode
   * @param inputRowType Input row type of the rexNode to translate
   *                     要转换的rexNode的输入行类型，用于获取字段名称
   * @param druidRel Druid query
   *                 Druid查询对象，包含操作符转换映射和Druid表信息
   *
   * @return Druid Expression, or null when can not convert the RexNode
   *         返回Druid表达式字符串，如果无法转换则返回null
   */
  public static @Nullable String toDruidExpression( // 公共静态方法，将RexNode转换为Druid表达式
      final RexNode rexNode, // 要转换的RexNode参数，使用final修饰表示不可修改
      final RelDataType inputRowType, // 输入行类型参数，用于获取字段名称
      final DruidQuery druidRel) { // Druid查询对象参数，包含转换映射和表信息
    SqlKind kind = rexNode.getKind(); // 获取RexNode的种类（INPUT_REF、LITERAL、CALL等）
    SqlTypeName sqlTypeName = rexNode.getType().getSqlTypeName(); // 获取RexNode的SQL类型名称

    if (kind == SqlKind.INPUT_REF) { // 如果RexNode是输入引用（字段引用）
      final RexInputRef ref = (RexInputRef) rexNode; // 强制转换为RexInputRef类型
      final String columnName = inputRowType.getFieldNames().get(ref.getIndex()); // 根据索引获取字段名称
      if (columnName == null) { // 如果字段名称为null
        return null; // 返回null，表示无法转换
      }
      if (druidRel.getDruidTable().timestampFieldName.equals(columnName)) { // 如果字段名是Druid表的时间戳字段名
        return DruidExpressions.fromColumn(DruidTable.DEFAULT_TIMESTAMP_COLUMN); // 返回默认时间戳列的表达式
      }
      return DruidExpressions.fromColumn(columnName); // 返回普通列的表达式
    }

    if (rexNode instanceof RexCall) { // 如果RexNode是函数调用
      final SqlOperator operator = ((RexCall) rexNode).getOperator(); // 获取函数调用对应的SQL操作符
      final DruidSqlOperatorConverter conversion = druidRel.getOperatorConversionMap() // 从Druid查询的操作符转换映射中获取转换器
          .get(operator); // 根据操作符获取对应的转换器
      if (conversion == null) { // 如果转换器为null（表示不支持该操作符）
        // unknown operator; can not translate
        // 未知操作符；无法转换
        return null; // 返回null，表示无法转换
      } else { // 如果转换器存在
        return conversion.toDruidExpression(rexNode, inputRowType, druidRel); // 调用转换器的toDruidExpression方法进行转换
      }
    }
    if (kind == SqlKind.LITERAL) { // 如果RexNode是字面量（常量）
      // Translate literal.
      // 转换字面量
      if (RexLiteral.isNullLiteral(rexNode)) { // 如果是NULL字面量
        // case the filter/project might yield to unknown; let Calcite
        // deal with this for now
        // 过滤器/投影可能会产生未知值；让Calcite处理这种情况
        return null; // 返回null，让Calcite处理
      } else if (SqlTypeName.NUMERIC_TYPES.contains(sqlTypeName)) { // 如果是数值类型
        // This conversion is lossy for Double values.
        // However, Druid does not support floating point literal values
        // if they are formatted using scientific notation.
        // 对于Double值，这种转换是有损的
        // 但是，如果浮点数字面量使用科学计数法格式化，Druid不支持
        return DruidExpressions.numberLiteral( // 返回数字字面量表达式
            requireNonNull((RexLiteral) rexNode).getValueAs(BigDecimal.class)); // 获取BigDecimal值并转换为数字字面量
      } else if (SqlTypeFamily.INTERVAL_DAY_TIME == sqlTypeName.getFamily()) { // 如果是DAY-TIME间隔类型
        // Calcite represents DAY-TIME intervals in milliseconds.
        // Calcite用毫秒表示DAY-TIME间隔
        final long milliseconds = // 获取毫秒值
            requireNonNull((Number) RexLiteral.value(rexNode)).longValue(); // 从字面量中获取Number值并转换为long
        return DruidExpressions.numberLiteral(milliseconds); // 返回毫秒数的数字字面量
      } else if (SqlTypeFamily.INTERVAL_YEAR_MONTH == sqlTypeName.getFamily()) { // 如果是YEAR-MONTH间隔类型
        // Calcite represents YEAR-MONTH intervals in months.
        // Calcite用月数表示YEAR-MONTH间隔
        final long months = // 获取月数值
            requireNonNull((Number) RexLiteral.value(rexNode)).longValue(); // 从字面量中获取Number值并转换为long
        return DruidExpressions.numberLiteral(months); // 返回月数的数字字面量
      } else if (SqlTypeName.STRING_TYPES.contains(sqlTypeName)) { // 如果是字符串类型
        return DruidExpressions.stringLiteral( // 返回字符串字面量表达式
            requireNonNull(RexLiteral.stringValue(rexNode))); // 获取字符串值并转换为字符串字面量
      } else if (SqlTypeName.DATE == sqlTypeName // 如果是DATE类型
          || SqlTypeName.TIMESTAMP == sqlTypeName // 或者是TIMESTAMP类型
          || SqlTypeName.TIME_WITH_LOCAL_TIME_ZONE == sqlTypeName) { // 或者是TIME_WITH_LOCAL_TIME_ZONE类型
        return DruidExpressions.numberLiteral( // 返回数字字面量表达式
            requireNonNull(DruidDateTimeUtils.literalValue(rexNode))); // 使用DruidDateTimeUtils获取字面量值
      } else if (SqlTypeName.BOOLEAN == sqlTypeName) { // 如果是布尔类型
        return DruidExpressions.numberLiteral(RexLiteral.booleanValue(rexNode) ? 1 : 0); // 转换为1（true）或0（false）的数字字面量
      }
    }
    // Not Literal/InputRef/RexCall or unknown type?
    // 不是字面量/输入引用/函数调用或未知类型？
    return null; // 返回null，表示无法转换
  }

  public static String fromColumn(String columnName) { // 公共静态方法，从列名创建Druid列引用表达式
    return DruidQuery.format("\"%s\"", columnName); // 使用DruidQuery.format方法格式化列名，添加双引号
  }

  public static String nullLiteral() { // 公共静态方法，创建Druid的NULL字面量表达式
    return "null"; // 返回字符串"null"
  }

  public static String numberLiteral(final @Nullable Number n) { // 公共静态方法，创建Druid的数字字面量表达式
    return n == null ? nullLiteral() : n.toString(); // 如果数字为null则返回null字面量，否则返回数字的字符串表示
  }

  public static String stringLiteral(final @Nullable String s) { // 公共静态方法，创建Druid的字符串字面量表达式
    return s == null ? nullLiteral() : "'" + escape(s) + "'"; // 如果字符串为null则返回null字面量，否则用单引号包裹并转义
  }

  private static String escape(final String s) { // 私有静态方法，转义字符串中的特殊字符
    final StringBuilder escaped = new StringBuilder(); // 创建StringBuilder用于构建转义后的字符串
    for (int i = 0; i < s.length(); i++) { // 遍历字符串的每个字符
      final char c = s.charAt(i); // 获取当前位置的字符
      if (Character.isLetterOrDigit(c) || Arrays.binarySearch(SAFE_CHARS, c) >= 0) { // 如果是字母数字或在安全字符列表中
        escaped.append(c); // 直接添加字符，不需要转义
      } else { // 如果不是安全字符
        escaped.append("\\u").append(BaseEncoding.base16().encode(Chars.toByteArray(c))); // 使用Unicode转义格式
      }
    }
    return escaped.toString(); // 返回转义后的字符串
  }

  public static String functionCall(final String functionName, final List<String> args) { // 公共静态方法，创建Druid函数调用表达式
    requireNonNull(functionName, "druid functionName"); // 检查函数名不为null
    requireNonNull(args, "args"); // 检查参数列表不为null

    final StringBuilder builder = new StringBuilder(functionName); // 创建StringBuilder，初始值为函数名
    builder.append("("); // 添加左括号
    for (int i = 0; i < args.size(); i++) { // 遍历所有参数
      int finalI = i; // 创建final变量用于lambda表达式
      final String arg = requireNonNull(args.get(i), () -> "arg #" + finalI); // 获取参数并检查不为null，提供错误信息
      builder.append(arg); // 添加参数到表达式
      if (i < args.size() - 1) { // 如果不是最后一个参数
        builder.append(","); // 添加逗号分隔符
      }
    }
    builder.append(")"); // 添加右括号
    return builder.toString(); // 返回完整的函数调用表达式字符串
  }

  public static String nAryOperatorCall(final String druidOperator, final List<String> args) { // 公共静态方法，创建Druid的n元操作符调用表达式
    requireNonNull(druidOperator, "druid operator missing"); // 检查操作符不为null
    requireNonNull(args, "args"); // 检查参数列表不为null
    final StringBuilder builder = new StringBuilder(); // 创建StringBuilder
    builder.append("("); // 添加左括号
    for (int i = 0; i < args.size(); i++) { // 遍历所有参数
      int finalI = i; // 创建final变量用于lambda表达式
      final String arg = requireNonNull(args.get(i), () -> "arg #" + finalI); // 获取参数并检查不为null，提供错误信息
      builder.append(arg); // 添加参数到表达式
      if (i < args.size() - 1) { // 如果不是最后一个参数
        builder.append(druidOperator); // 添加操作符
      }
    }
    builder.append(")"); // 添加右括号
    return builder.toString(); // 返回完整的n元操作符表达式字符串
  }

  /**
   * Translate a list of Calcite {@code RexNode} to Druid expressions.
   * 将Calcite的RexNode列表转换为Druid表达式列表。
   *
   * 这是toDruidExpression方法的批量版本，用于将多个RexNode转换为对应的Druid表达式。
   *
   * 方法特点：
   * - 保持输入和输出的顺序一致
   * - 如果任何一个RexNode无法转换，则整个转换失败，返回null
   * - 如果转换成功，返回的列表中的所有元素都不为null
   *
   * 使用场景：
   * - 转换投影字段列表（SELECT子句中的表达式）
   * - 转换聚合函数参数列表
   * - 转换函数调用的参数列表
   *
   * @param rexNodes list of Calcite expressions meant to be applied on top of the rows
   *                 要在行上应用的Calcite表达式列表
   *
   * @return list of Druid expressions in the same order as rexNodes, or null if not possible.
   *         返回与rexNodes顺序相同的Druid表达式列表，如果无法转换则返回null
   *         If a non-null list is returned, all elements will be non-null.
   *         如果返回非null列表，所有元素都将非null
   */
  public static @Nullable List<String> toDruidExpressions( // 公共静态方法，批量转换RexNode列表
      final DruidQuery druidRel, // Druid查询对象参数
      final RelDataType rowType, // 行类型参数
      final List<RexNode> rexNodes) { // RexNode列表参数
    final List<String> retVal = new ArrayList<>(rexNodes.size()); // 创建结果列表，初始容量为输入列表大小
    for (RexNode rexNode : rexNodes) { // 遍历每个RexNode
      final String druidExpression = toDruidExpression(rexNode, rowType, druidRel); // 转换单个RexNode
      if (druidExpression == null) { // 如果转换结果为null（转换失败）
        return null; // 返回null，表示整个转换失败
      }

      retVal.add(druidExpression); // 将转换结果添加到结果列表
    }
    return retVal; // 返回转换结果列表
  }

  public static String applyTimestampFloor( // 公共静态方法，应用时间戳向下取整函数
      final String input, // 输入时间戳表达式参数
      final String granularity, // 时间粒度参数（如"DAY"、"HOUR"、"MINUTE"等）
      final String origin, // 原点参数（指定时间计算的起点，如"1970-01-01T00:00:00Z"）
      final TimeZone timeZone) { // 时区参数
    requireNonNull(input, "input"); // 检查输入参数不为null
    requireNonNull(granularity, "granularity"); // 检查粒度参数不为null
    return DruidExpressions.functionCall( // 返回函数调用表达式
        "timestamp_floor", // 函数名为timestamp_floor（时间戳向下取整）
        ImmutableList.of(input, // 参数列表：输入时间戳
            DruidExpressions.stringLiteral(granularity), // 粒度参数（字符串字面量）
            DruidExpressions.stringLiteral(origin), // 原点参数（字符串字面量）
            DruidExpressions.stringLiteral(timeZone.getID()))); // 时区参数（字符串字面量）
  }

  public static String applyTimestampCeil( // 公共静态方法，应用时间戳向上取整函数
      final String input, // 输入时间戳表达式参数
      final String granularity, // 时间粒度参数（如"DAY"、"HOUR"、"MINUTE"等）
      final String origin, // 原点参数（指定时间计算的起点）
      final TimeZone timeZone) { // 时区参数
    requireNonNull(input, "input"); // 检查输入参数不为null
    requireNonNull(granularity, "granularity"); // 检查粒度参数不为null
    return DruidExpressions.functionCall( // 返回函数调用表达式
        "timestamp_ceil", // 函数名为timestamp_ceil（时间戳向上取整）
        ImmutableList.of(input, // 参数列表：输入时间戳
            DruidExpressions.stringLiteral(granularity), // 粒度参数（字符串字面量）
            DruidExpressions.stringLiteral(origin), // 原点参数（字符串字面量）
            DruidExpressions.stringLiteral(timeZone.getID()))); // 时区参数（字符串字面量）
  }


  public static String applyTimeExtract(String timeExpression, String druidUnit, // 公共静态方法，应用时间提取函数
      TimeZone timeZone) { // 时区参数
    return DruidExpressions.functionCall( // 返回函数调用表达式
        "timestamp_extract", // 函数名为timestamp_extract（从时间戳中提取部分）
        ImmutableList.of( // 参数列表
            timeExpression, // 输入时间表达式
            DruidExpressions.stringLiteral(druidUnit), // 提取单位（如"YEAR"、"MONTH"、"DAY"、"HOUR"等）
            DruidExpressions.stringLiteral(timeZone.getID()))); // 时区参数（字符串字面量）
  }
}
