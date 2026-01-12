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
// Apache许可证头部声明，说明本代码遵循Apache 2.0许可证协议，允许自由使用、修改和分发
package org.apache.calcite.sql.test; // 定义包名，位于calcite的SQL测试模块中，包含SQL测试相关的工具类

// 引入Calcite关系代数类型相关类，用于处理关系数据类型（RelDataType表示关系数据类型，RelDataTypeFactory用于创建数据类型，RelDataTypeImpl提供数据类型的默认实现）
import org.apache.calcite.rel.type.RelDataType; // 引入关系数据类型接口，表示SQL中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 引入关系数据类型工厂接口，用于创建各种数据类型
import org.apache.calcite.rel.type.RelDataTypeImpl; // 引入关系数据类型实现类，提供数据类型的基础实现

// 引入Calcite运行时异常类，用于处理Calcite上下文中的异常情况
import org.apache.calcite.runtime.CalciteContextException; // 引入Calcite上下文异常类，包含SQL解析和验证过程中的错误信息

// 引入SQL解析相关类，用于处理SQL解析异常和解析工具
import org.apache.calcite.sql.parser.SqlParseException; // 引入SQL解析异常类，表示SQL解析过程中发生的错误
import org.apache.calcite.sql.parser.SqlParserUtil; // 引入SQL解析工具类，提供SQL解析的辅助方法
import org.apache.calcite.sql.parser.StringAndPos; // 引入字符串和位置类，包含SQL语句及其在原始文本中的位置信息

// 引入SQL类型相关类，用于处理SQL类型名称
import org.apache.calcite.sql.type.SqlTypeName; // 引入SQL类型名称枚举，定义了所有SQL标准数据类型（如INTEGER、VARCHAR等）

// 引入Calcite工具类，用于测试和通用工具方法
import org.apache.calcite.util.TestUtil; // 引入测试工具类，提供测试相关的辅助方法
import org.apache.calcite.util.Util; // 引入通用工具类，提供常用的工具方法

// 引入Google Guava的不可变集合类，用于创建不可变的列表
import com.google.common.collect.ImmutableList; // 引入不可变列表类，确保创建的列表不可修改

// 引入Checker Framework的空值检查注解，用于静态分析空值安全性
import org.checkerframework.checker.nullness.qual.Nullable; // 引入可空注解，标记参数或返回值可能为null

// 引入Java基础类，用于数组和集合操作
import java.util.Arrays; // 引入数组工具类，提供数组操作的静态方法
import java.util.List; // 引入列表接口，表示有序集合
import java.util.function.Supplier; // 引入函数式接口，用于延迟计算或提供值
import java.util.regex.Pattern; // 引入正则表达式模式类，用于编译正则表达式

// 静态导入SqlTester接口中的内部接口，简化代码编写
import static org.apache.calcite.sql.test.SqlTester.ParameterChecker; // 静态导入参数检查器接口，用于检查SQL语句的参数
import static org.apache.calcite.sql.test.SqlTester.ResultChecker; // 静态导入结果检查器接口，用于检查SQL执行结果
import static org.apache.calcite.sql.test.SqlTester.TypeChecker; // 静态导入类型检查器接口，用于检查SQL表达式的类型

// 静态导入Hamcrest断言库的匹配器，用于编写更清晰的断言
import static org.hamcrest.CoreMatchers.is; // 静态导入is匹配器，用于判断相等性
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入assertThat断言方法，用于执行断言
import static org.hamcrest.Matchers.hasToString; // 静态导入hasToString匹配器，用于检查对象的字符串表示

// 静态导入JUnit断言方法，用于测试失败
import static org.junit.jupiter.api.Assertions.fail; // 静态导入fail方法，用于标记测试失败

// 静态导入Integer的parseInt方法，用于将字符串转换为整数
import static java.lang.Integer.parseInt; // 静态导入parseInt方法，用于解析整数字符串

/**
 * Utility methods.
 */
// SqlTests类：SQL测试工具类，提供SQL测试中常用的静态方法和工具
// 这是一个抽象类，包含各种类型检查器、查询生成器和异常验证方法
// 主要功能包括：
// 1. 提供预定义的类型检查器（INTEGER、BOOLEAN、ANY等）
// 2. 生成测试SQL查询（聚合查询、窗口聚合查询等）
// 3. 验证异常是否符合预期（包括异常消息和位置信息）
// 4. 提供数据类型字符串表示的转换方法
// 5. 定义查询处理阶段的枚举（PARSER、VALIDATOR、RUNTIME）
public abstract class SqlTests { // 定义抽象类，不能直接实例化，只提供静态工具方法
  //~ Static fields/initializers ---------------------------------------------
  // 静态字段和初始化块区域标记，以下定义静态常量

  // INTEGER_TYPE_CHECKER：整数类型检查器，用于验证SQL表达式的结果类型是否为INTEGER
  // 这是一个静态常量，使用SqlTypeChecker内部类实现，检查类型名称是否为"INTEGER"
  public static final TypeChecker INTEGER_TYPE_CHECKER = // 定义公共静态常量，类型为TypeChecker接口
      new SqlTypeChecker(SqlTypeName.INTEGER); // 创建SqlTypeChecker实例，检查类型是否为INTEGER

  // BOOLEAN_TYPE_CHECKER：布尔类型检查器，用于验证SQL表达式的结果类型是否为BOOLEAN
  // 这是一个静态常量，使用SqlTypeChecker内部类实现，检查类型名称是否为"BOOLEAN"
  public static final TypeChecker BOOLEAN_TYPE_CHECKER = // 定义公共静态常量，类型为TypeChecker接口
      new SqlTypeChecker(SqlTypeName.BOOLEAN); // 创建SqlTypeChecker实例，检查类型是否为BOOLEAN

  /**
   * Checker which allows any type.
   */
  // ANY_TYPE_CHECKER：任意类型检查器，接受任何数据类型
  // 使用lambda表达式实现TypeChecker接口，不执行任何检查操作
  // 用于测试场景中不需要验证结果类型的情况
  public static final TypeChecker ANY_TYPE_CHECKER = (sql, type) -> { // 使用lambda表达式创建类型检查器，接受任意类型
  }; // 空的lambda体，不执行任何检查

  /**
   * Checker which enforces that a data type is nullable.
   */
  // ANY_NULLABLE_TYPE_CHECKER：可空类型检查器，验证数据类型是否可空
  // 使用lambda表达式实现，检查类型的isNullable()方法是否返回true
  // 如果类型不可空，则调用fail()方法标记测试失败
  public static final TypeChecker ANY_NULLABLE_TYPE_CHECKER = (sql, type) -> { // 使用lambda表达式创建类型检查器
    if (!type.isNullable()) { // 检查类型是否不可空
      fail("expected nullable, got " + sql); // 如果不可空，则测试失败，输出期望可空的错误信息
    } // 结束if语句
  }; // 结束lambda表达式

  /**
   * Checker that allows any number or type of parameters.
   */
  // ANY_PARAMETER_CHECKER：任意参数检查器，接受任何数量和类型的参数
  // 使用lambda表达式实现ParameterChecker接口，不执行任何检查操作
  // 用于测试场景中不需要验证参数类型和数量的情况
  public static final ParameterChecker ANY_PARAMETER_CHECKER = parameterRowType -> { // 使用lambda表达式创建参数检查器
  }; // 空的lambda体，不执行任何检查

  /**
   * Checker that allows any result.
   */
  // ANY_RESULT_CHECKER：任意结果检查器，接受任何查询结果
  // 使用lambda表达式实现ResultChecker接口，遍历所有结果行但不做任何验证
  // 用于测试场景中只需要执行查询而不需要验证结果的情况
  public static final ResultChecker ANY_RESULT_CHECKER = (sql, result) -> { // 使用lambda表达式创建结果检查器
    while (true) { // 无限循环，用于遍历结果集
      if (!result.next()) { // 尝试移动到下一行，如果没有更多行则返回false
        break; // 如果没有更多行，则跳出循环
      } // 结束if语句
    } // 结束while循环
  }; // 结束lambda表达式

  // LINE_COL_PATTERN：正则表达式模式，用于匹配错误消息中的行号和列号
  // 匹配格式："At line 10, column 5"
  // 用于从错误消息中提取错误位置信息
  private static final Pattern LINE_COL_PATTERN = // 定义私有静态常量，正则表达式模式
      Pattern.compile("At line ([0-9]+), column ([0-9]+)"); // 编译正则表达式，匹配单点位置信息

  // LINE_COL_TWICE_PATTERN：正则表达式模式，用于匹配错误消息中的起始和结束行号、列号
  // 匹配格式："From line 1, column 5 to line 2, column 10: error message"
  // 用于从错误消息中提取错误范围信息和错误消息内容
  private static final Pattern LINE_COL_TWICE_PATTERN = // 定义私有静态常量，正则表达式模式
      Pattern.compile( // 编译正则表达式
          "(?s)From line ([0-9]+), column ([0-9]+) to line ([0-9]+), column ([0-9]+): (.*)"); // 匹配范围位置信息和错误消息，(?s)表示DOTALL模式，使.匹配换行符

  /**
   * Helper function to get the string representation of a RelDataType
   * (include precision/scale but no charset or collation).
   *
   * @param sqlType Type
   * @return String representation of type
   */
  // getTypeString方法：获取关系数据类型的字符串表示
  // 该方法返回类型的字符串表示，包括精度和小数位数，但不包括字符集和排序规则
  // 对于VARCHAR和CHAR类型，会附加精度信息；对于不可空类型，会附加NOT NULL后缀
  // 参数sqlType：要转换的关系数据类型对象
  // 返回值：类型的字符串表示，如"VARCHAR(10)"、"INTEGER NOT NULL"等
  public static String getTypeString(RelDataType sqlType) { // 定义公共静态方法，获取类型的字符串表示
    switch (sqlType.getSqlTypeName()) { // 根据SQL类型名称进行分支判断
    case VARCHAR: // 如果类型是VARCHAR（可变长度字符串）
    case CHAR: // 如果类型是CHAR（固定长度字符串）
      String actual = sqlType.getSqlTypeName().name(); // 获取类型名称（如"VARCHAR"、"CHAR"）
      if (sqlType.getPrecision() != RelDataType.PRECISION_NOT_SPECIFIED) { // 如果精度已指定（即不是默认值）
        actual = actual + "(" + sqlType.getPrecision() + ")"; // 在类型名称后附加精度信息，如"VARCHAR(10)"
      } // 结束if语句
      if (!sqlType.isNullable()) { // 如果类型不可空
        actual += RelDataTypeImpl.NON_NULLABLE_SUFFIX; // 在类型字符串后附加NOT NULL后缀
      } // 结束if语句
      return actual; // 返回构建好的类型字符串

    default: // 对于其他所有类型
      return sqlType.getFullTypeString(); // 直接返回类型的完整字符串表示（包含所有信息）
    } // 结束switch语句
  } // 结束getTypeString方法

  /** Returns a list of typical types. */
  // getTypes方法：返回典型SQL数据类型的列表
  // 该方法使用类型工厂创建各种常用的SQL数据类型，用于测试目的
  // 创建的类型包括：布尔类型、各种整数类型、各种小数类型、字符类型、二进制类型、日期时间类型等
  // 参数typeFactory：关系数据类型工厂，用于创建各种数据类型
  // 返回值：包含各种典型数据类型的不可变列表
  public static List<RelDataType> getTypes(RelDataTypeFactory typeFactory) { // 定义公共静态方法，获取典型类型列表
    final int maxPrecision = // 获取DECIMAL类型的最大精度
        typeFactory.getTypeSystem().getMaxPrecision(SqlTypeName.DECIMAL); // 从类型系统中获取DECIMAL类型的最大精度值
    return ImmutableList.of( // 返回不可变列表，包含以下各种类型
        typeFactory.createSqlType(SqlTypeName.BOOLEAN), // 创建BOOLEAN类型（布尔值）
        typeFactory.createSqlType(SqlTypeName.TINYINT), // 创建TINYINT类型（1字节整数）
        typeFactory.createSqlType(SqlTypeName.SMALLINT), // 创建SMALLINT类型（2字节整数）
        typeFactory.createSqlType(SqlTypeName.INTEGER), // 创建INTEGER类型（4字节整数）
        typeFactory.createSqlType(SqlTypeName.BIGINT), // 创建BIGINT类型（8字节整数）
        typeFactory.createSqlType(SqlTypeName.DECIMAL), // 创建DECIMAL类型（默认精度的小数）
        typeFactory.createSqlType(SqlTypeName.DECIMAL, 5), // 创建DECIMAL类型，精度为5
        typeFactory.createSqlType(SqlTypeName.DECIMAL, 6, 2), // 创建DECIMAL类型，精度为6，小数位数为2
        typeFactory.createSqlType(SqlTypeName.DECIMAL, maxPrecision, 0), // 创建DECIMAL类型，使用最大精度，小数位数为0
        typeFactory.createSqlType(SqlTypeName.DECIMAL, maxPrecision, 5), // 创建DECIMAL类型，使用最大精度，小数位数为5

        // todo: test IntervalDayTime and IntervalYearMonth
        // TODO注释：未来需要测试IntervalDayTime（天时间间隔）和IntervalYearMonth（年月间隔）类型

        // todo: test Float, Real, Double
        // TODO注释：未来需要测试Float、Real、Double浮点类型

        typeFactory.createSqlType(SqlTypeName.CHAR, 5), // 创建CHAR类型，固定长度为5
        typeFactory.createSqlType(SqlTypeName.VARCHAR, 1), // 创建VARCHAR类型，最大长度为1
        typeFactory.createSqlType(SqlTypeName.VARCHAR, 20), // 创建VARCHAR类型，最大长度为20
        typeFactory.createSqlType(SqlTypeName.BINARY, 3), // 创建BINARY类型，固定长度为3
        typeFactory.createSqlType(SqlTypeName.VARBINARY, 4), // 创建VARBINARY类型，最大长度为4
        typeFactory.createSqlType(SqlTypeName.DATE), // 创建DATE类型（日期）
        typeFactory.createSqlType(SqlTypeName.TIME, 0), // 创建TIME类型，精度为0（不包含秒的小数部分）
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP, 0)); // 创建TIMESTAMP类型，精度为0（不包含秒的小数部分）
  } // 结束getTypes方法

  // generateAggQuery方法：生成聚合查询的SQL语句
  // 该方法根据给定的聚合表达式和输入值数组，生成一个测试用的聚合查询SQL
  // 生成的查询使用UNION ALL将各个输入值组合成一个临时表，然后对临时表执行聚合操作
  // 参数expr：聚合表达式，如"SUM(x)"、"COUNT(*)"等
  // 参数inputValues：输入值数组，每个值作为一行数据
  // 返回值：生成的聚合查询SQL字符串
  public static String generateAggQuery(String expr, String[] inputValues) { // 定义公共静态方法，生成聚合查询
    StringBuilder buf = new StringBuilder(); // 创建字符串构建器，用于构建SQL语句
    buf.append("SELECT ").append(expr).append(" FROM "); // 添加SELECT子句和FROM关键字
    if (inputValues.length == 0) { // 如果输入值数组为空
      buf.append("(VALUES 1) AS t(x) WHERE false"); // 生成一个总是返回空结果的查询，使用WHERE false确保无结果
    } else { // 如果输入值数组不为空
      buf.append("("); // 添加左括号，开始子查询
      for (int i = 0; i < inputValues.length; i++) { // 遍历输入值数组
        if (i > 0) { // 如果不是第一个值
          buf.append(" UNION ALL "); // 添加UNION ALL，连接各个值
        } // 结束if语句
        buf.append("SELECT "); // 添加SELECT关键字
        String inputValue = inputValues[i]; // 获取当前输入值
        buf.append(inputValue).append(" AS x FROM (VALUES (1))"); // 添加值和列别名，从VALUES子句中选择
      } // 结束for循环
      buf.append(")"); // 添加右括号，结束子查询
    } // 结束if-else语句
    return buf.toString(); // 返回构建好的SQL字符串
  } // 结束generateAggQuery方法

  // generateAggQueryWithMultipleArgs方法：生成多参数聚合查询的SQL语句
  // 该方法用于生成包含多个参数的聚合函数测试查询，如CORR(x1, x2)等需要多个参数的聚合函数
  // 首先验证所有行的参数数量一致，然后生成包含多列的临时表，最后执行聚合操作
  // 参数expr：聚合表达式，可能包含多个参数引用
  // 参数inputValues：二维字符串数组，每行代表一组参数值
  // 返回值：生成的多参数聚合查询SQL字符串
  public static String generateAggQueryWithMultipleArgs(String expr, // 定义公共静态方法，生成多参数聚合查询
      String[][] inputValues) { // 输入值为二维数组，每个子数组代表一行数据
    int argCount = -1; // 初始化参数计数为-1，表示尚未确定
    for (String[] row : inputValues) { // 遍历每一行输入值
      if (argCount == -1) { // 如果是第一行
        argCount = row.length; // 将当前行的长度作为参数数量
      } else if (argCount != row.length) { // 如果后续行的长度与第一行不一致
        throw new IllegalArgumentException("invalid test input: " // 抛出非法参数异常
            + Arrays.toString(row)); // 输出错误的行数据
      } // 结束if-else语句
    } // 结束for循环
    StringBuilder buf = new StringBuilder(); // 创建字符串构建器
    buf.append("SELECT ").append(expr).append(" FROM "); // 添加SELECT子句和FROM关键字
    if (inputValues.length == 0) { // 如果输入值数组为空
      buf.append("(VALUES 1) AS t(x) WHERE false"); // 生成一个总是返回空结果的查询
    } else { // 如果输入值数组不为空
      buf.append("("); // 添加左括号，开始子查询
      for (int i = 0; i < inputValues.length; i++) { // 遍历每一行
        if (i > 0) { // 如果不是第一行
          buf.append(" UNION ALL "); // 添加UNION ALL
        } // 结束if语句
        buf.append("SELECT "); // 添加SELECT关键字
        for (int j = 0; j < argCount; j++) { // 遍历该行的每个参数
          if (j != 0) { // 如果不是第一个参数
            buf.append(", "); // 添加逗号分隔符
          } // 结束if语句
          String inputValue = inputValues[i][j]; // 获取当前参数值
          buf.append(inputValue).append(" AS x"); // 添加参数值和列别名x
          if (j != 0) { // 如果不是第一个参数
            buf.append(j + 1); // 在列名后添加序号，形成x1, x2, x3等
          } // 结束if语句
        } // 结束内层for循环
        buf.append(" FROM (VALUES (1))"); // 添加FROM子句
      } // 结束外层for循环
      buf.append(")"); // 添加右括号，结束子查询
    } // 结束if-else语句
    return buf.toString(); // 返回构建好的SQL字符串
  } // 结束generateAggQueryWithMultipleArgs方法

  // generateWinAggQuery方法：生成窗口聚合查询的SQL语句
  // 该方法生成包含窗口函数（OVER子句）的测试查询
  // 窗口聚合函数会对每一行数据应用聚合操作，而不是像普通聚合函数那样将所有行聚合为一个结果
  // 参数expr：窗口聚合表达式，如"SUM(x) OVER (ORDER BY x)"
  // 参数windowSpec：窗口规范，定义窗口的分区、排序和范围
  // 参数inputValues：输入值数组，用于生成测试数据
  // 返回值：生成的窗口聚合查询SQL字符串
  public static String generateWinAggQuery( // 定义公共静态方法，生成窗口聚合查询
      String expr, // 窗口聚合表达式
      String windowSpec, // 窗口规范
      String[] inputValues) { // 输入值数组
    StringBuilder buf = new StringBuilder(); // 创建字符串构建器
    buf.append("SELECT ").append(expr).append(" OVER (").append(windowSpec) // 添加SELECT子句和OVER子句
        .append(") FROM ("); // 添加FROM关键字和左括号
    for (int i = 0; i < inputValues.length; i++) { // 遍历输入值数组
      if (i > 0) { // 如果不是第一个值
        buf.append(" UNION ALL "); // 添加UNION ALL
      } // 结束if语句
      buf.append("SELECT "); // 添加SELECT关键字
      String inputValue = inputValues[i]; // 获取当前输入值
      buf.append(inputValue).append(" AS x FROM (VALUES (1))"); // 添加值和列别名
    } // 结束for循环
    buf.append(")"); // 添加右括号，结束子查询
    return buf.toString(); // 返回构建好的SQL字符串
  } // 结束generateWinAggQuery方法

  /**
   * Checks whether an exception matches the expected pattern. If
   * <code>sap</code> contains an error location, checks this too.
   *
   * @param ex                 Exception thrown
   * @param expectedMsgPattern Expected pattern
   * @param sap                Query and (optional) position in query
   * @param stage              Query processing stage
   */
  // checkEx方法：检查异常是否符合预期
  // 该方法验证抛出的异常是否符合预期的错误消息模式和位置信息
  // 支持多种异常类型的检查：CalciteContextException、SqlParseException、IllegalStateException等
  // 如果异常不符合预期，会抛出AssertionError并显示详细的错误信息
  // 参数ex：实际抛出的异常，可能为null
  // 参数expectedMsgPattern：期望的错误消息正则表达式模式，可能为null
  // 参数sap：SQL语句及其位置信息
  // 参数stage：查询处理阶段（PARSER、VALIDATOR或RUNTIME）
  public static void checkEx(@Nullable Throwable ex, // 定义公共静态方法，检查异常
      @Nullable String expectedMsgPattern, // 期望的错误消息模式
      StringAndPos sap, // SQL语句和位置信息
      Stage stage) { // 查询处理阶段
    if (null == ex) { // 如果没有抛出异常
      if (expectedMsgPattern == null) { // 如果也不期望有异常
        // No error expected, and no error happened.
        return; // 直接返回，测试通过
      } else { // 如果期望有异常但没有抛出
        throw new AssertionError("Expected query to throw exception, " // 抛出断言错误
            + "but it did not; query [" + sap.sql // 显示SQL语句
            + "]; expected [" + expectedMsgPattern + "]"); // 显示期望的错误模式
      } // 结束if-else语句
    } // 结束if语句
    Throwable actualException = ex; // 保存实际异常对象
    String actualMessage = actualException.getMessage(); // 获取异常消息
    int actualLine = -1; // 初始化实际错误行号为-1
    int actualColumn = -1; // 初始化实际错误列号为-1
    int actualEndLine = 100; // 初始化实际错误结束行号为100（默认值）
    int actualEndColumn = 99; // 初始化实际错误结束列号为99（默认值）

    if (ex instanceof ExceptionInInitializerError) { // 如果异常是静态初始化错误
      ex = ((ExceptionInInitializerError) ex).getException(); // 获取包装的真正异常
    } // 结束if语句

    // Search for an CalciteContextException somewhere in the stack.
    // 在异常堆栈中查找CalciteContextException
    // CalciteContextException是Calcite特有的上下文异常，包含详细的错误位置信息
    CalciteContextException ece = null; // 初始化CalciteContextException引用为null
    for (Throwable x = ex; x != null; x = x.getCause()) { // 遍历异常链
      if (x instanceof CalciteContextException) { // 如果找到CalciteContextException
        ece = (CalciteContextException) x; // 保存该异常
        break; // 跳出循环
      } // 结束if语句
      if (x.getCause() == x) { // 如果异常的cause指向自己（循环引用）
        break; // 跳出循环，避免无限循环
      } // 结束if语句
    } // 结束for循环

    // Search for an IllegalStateException somewhere in the stack.
    // These are thrown by the enumerable implementors when evaluating
    // an expression that produces an error.
    // 在异常堆栈中查找IllegalStateException
    // 这些异常通常由可枚举实现器在计算产生错误的表达式时抛出
    IllegalStateException ise = null; // 初始化IllegalStateException引用为null
    for (Throwable x = ex; x != null; x = x.getCause()) { // 遍历异常链
      if (x instanceof IllegalStateException) { // 如果找到IllegalStateException
        ise = (IllegalStateException) x; // 保存该异常
        break; // 跳出循环
      } // 结束if语句
      if (x.getCause() == x) { // 如果异常的cause指向自己
        break; // 跳出循环
      } // 结束if语句
    } // 结束for循环

    // Search for a SqlParseException -- with its position set -- somewhere
    // in the stack.
    // 在异常堆栈中查找设置了位置信息的SqlParseException
    // SqlParseException是SQL解析异常，包含解析错误的详细信息
    SqlParseException spe = null; // 初始化SqlParseException引用为null
    for (Throwable x = ex; x != null; x = x.getCause()) { // 遍历异常链
      if ((x instanceof SqlParseException) // 如果是SqlParseException
          && (((SqlParseException) x).getPos() != null)) { // 并且位置信息不为null
        spe = (SqlParseException) x; // 保存该异常
        break; // 跳出循环
      } // 结束if语句
      if (x.getCause() == x) { // 如果异常的cause指向自己
        break; // 跳出循环
      } // 结束if语句
    } // 结束for循环

    if (ece != null) { // 如果找到CalciteContextException
      actualLine = ece.getPosLine(); // 获取错误起始行号
      actualColumn = ece.getPosColumn(); // 获取错误起始列号
      actualEndLine = ece.getEndPosLine(); // 获取错误结束行号
      actualEndColumn = ece.getEndPosColumn(); // 获取错误结束列号
      if (ece.getCause() != null) { // 如果有cause异常
        actualException = ece.getCause(); // 使用cause作为实际异常
        actualMessage = actualException.getMessage(); // 获取cause的消息
      } // 结束if语句
    } else if (spe != null) { // 如果找到SqlParseException
      actualLine = spe.getPos().getLineNum(); // 获取错误起始行号
      actualColumn = spe.getPos().getColumnNum(); // 获取错误起始列号
      actualEndLine = spe.getPos().getEndLineNum(); // 获取错误结束行号
      actualEndColumn = spe.getPos().getEndColumnNum(); // 获取错误结束列号
      if (spe.getCause() != null) { // 如果有cause异常
        actualException = spe.getCause(); // 使用cause作为实际异常
        actualMessage = actualException.getMessage(); // 获取cause的消息
      } // 结束if语句
    } else if (ise != null) { // 如果找到IllegalStateException
      Throwable[] suppressed = ise.getSuppressed(); // 获取被抑制的异常数组
      if (suppressed.length > 0) { // 如果有被抑制的异常
        actualException = suppressed[0]; // 使用第一个被抑制的异常作为实际异常
        actualMessage = actualException.getMessage(); // 获取该异常的消息
      } // 结束if语句
    } else { // 如果以上异常都未找到
      actualMessage = ex.getMessage(); // 获取原始异常的消息
      if (ex instanceof NumberFormatException) { // 如果是数字格式异常
        // The message from NumberFormatException is not very usable
        // NumberFormatException的消息不够友好，需要改进
        actualMessage = "Number has wrong format " + actualMessage; // 添加更友好的前缀
      } // 结束if语句
      if (actualMessage != null) { // 如果消息不为null
        java.util.regex.Matcher matcher = // 创建正则匹配器
            LINE_COL_TWICE_PATTERN.matcher(actualMessage); // 尝试匹配范围位置模式
        if (matcher.matches()) { // 如果匹配成功
          actualLine = parseInt(matcher.group(1)); // 提取起始行号
          actualColumn = parseInt(matcher.group(2)); // 提取起始列号
          actualEndLine = parseInt(matcher.group(3)); // 提取结束行号
          actualEndColumn = parseInt(matcher.group(4)); // 提取结束列号
          actualMessage = matcher.group(5); // 提取错误消息内容
        } else { // 如果范围模式不匹配
          matcher = LINE_COL_PATTERN.matcher(actualMessage); // 尝试匹配单点位置模式
          if (matcher.matches()) { // 如果匹配成功
            actualLine = parseInt(matcher.group(1)); // 提取行号
            actualColumn = parseInt(matcher.group(2)); // 提取列号
          } else { // 如果单点模式也不匹配
            if (expectedMsgPattern != null // 如果期望有错误模式
                && actualMessage.matches(expectedMsgPattern)) { // 并且消息匹配期望模式
              return; // 直接返回，测试通过
            } // 结束if语句
          } // 结束if-else语句
        } // 结束if-else语句
      } // 结束if语句
    } // 结束if-else语句

    if (null == expectedMsgPattern) { // 如果不期望有异常
      actualException.printStackTrace(); // 打印异常堆栈跟踪
      fail(stage.componentName + " threw unexpected exception" // 测试失败，显示组件名称
          + "; query [" + sap.sql // 显示SQL语句
          + "]; exception [" + actualMessage // 显示异常消息
          + "]; class [" + actualException.getClass() // 显示异常类名
          + "]; pos [line " + actualLine // 显示错误位置
          + " col " + actualColumn
          + " thru line " + actualLine
          + " col " + actualColumn + "]");
    } // 结束if语句

    final String sqlWithCarets; // 声明包含插入符的SQL字符串
    if (actualColumn <= 0 // 如果列号无效
        || actualLine <= 0 // 或行号无效
        || actualEndColumn <= 0 // 或结束列号无效
        || actualEndLine <= 0) { // 或结束行号无效
      if (sap.pos != null) { // 如果期望有位置信息
        throw new AssertionError("Expected error to have position," // 抛出断言错误
            + " but actual error did not: " // 说明期望的位置信息缺失
            + " actual pos [line " + actualLine // 显示实际位置
            + " col " + actualColumn
            + " thru line " + actualEndLine + " col "
            + actualEndColumn + "]", actualException); // 包含异常对象
      } // 结束if语句
      sqlWithCarets = sap.sql; // 使用原始SQL，不添加插入符
    } else { // 如果位置信息有效
      sqlWithCarets = // 使用SqlParserUtil添加插入符
          SqlParserUtil.addCarets( // 调用工具方法在错误位置添加插入符
              sap.sql, // SQL语句
              actualLine, // 起始行号
              actualColumn, // 起始列号
              actualEndLine, // 结束行号
              actualEndColumn + 1); // 结束列号（加1以包含最后一个字符）
      if (sap.pos == null) { // 如果不期望有位置信息
        throw new AssertionError("Actual error had a position, but expected " // 抛出断言错误
            + "error did not. Add error position carets to sql:\n" // 说明实际有位置但期望没有
            + sqlWithCarets); // 显示带插入符的SQL
      } // 结束if语句
    } // 结束if-else语句

    if (actualMessage != null) { // 如果消息不为null
      actualMessage = Util.toLinux(actualMessage); // 将消息转换为Linux格式（统一换行符）
    } // 结束if语句

    if (actualMessage == null // 如果消息为null
        || !actualMessage.matches(expectedMsgPattern)) { // 或消息不匹配期望模式
      actualException.printStackTrace(); // 打印异常堆栈跟踪
      final String actualJavaRegexp = // 创建Java格式的正则表达式字符串
          (actualMessage == null) // 如果消息为null
              ? "null" // 使用"null"字符串
              : TestUtil.quoteForJava( // 否则对消息进行Java转义
              TestUtil.quotePattern(actualMessage)); // 先对正则表达式特殊字符进行转义
      fail(stage.componentName + " threw different " // 测试失败，显示组件名称
          + "exception than expected; query [" + sap.sql // 显示SQL语句
          + "];\n" // 换行
          + " expected pattern [" + expectedMsgPattern // 显示期望的模式
          + "];\n" // 换行
          + " actual [" + actualMessage // 显示实际消息
          + "];\n" // 换行
          + " actual as java regexp [" + actualJavaRegexp // 显示Java格式的正则
          + "]; pos [" + actualLine // 显示位置信息
          + " col " + actualColumn
          + " thru line " + actualEndLine
          + " col " + actualEndColumn
          + "]; sql [" + sqlWithCarets + "]"); // 显示带插入符的SQL
    } else if (sap.pos != null // 如果期望有位置信息
        && (actualLine != sap.pos.getLineNum() // 但实际起始行号不匹配
        || actualColumn != sap.pos.getColumnNum() // 或实际起始列号不匹配
        || actualEndLine != sap.pos.getEndLineNum() // 或实际结束行号不匹配
        || actualEndColumn != sap.pos.getEndColumnNum())) { // 或实际结束列号不匹配
      fail(stage.componentName + " threw expected " // 测试失败，显示组件名称
          + "exception [" + actualMessage // 显示异常消息
          + "];\nbut at pos [line " + actualLine // 显示实际位置
          + " col " + actualColumn
          + " thru line " + actualEndLine
          + " col " + actualEndColumn
          + "];\nsql [" + sqlWithCarets + "]"); // 显示带插入符的SQL
    } // 结束if-else语句
  } // 结束checkEx方法

  /** Stage of query processing. */
  // Stage枚举：定义查询处理的各个阶段
  // 用于标识在查询处理的哪个阶段发生了错误，便于在测试中精确验证异常来源
  public enum Stage { // 定义公共枚举类型
    PARSE("Parser"), // 解析阶段：SQL语句被解析为抽象语法树（AST）
    VALIDATE("Validator"), // 验证阶段：验证SQL语句的语义正确性（如表存在性、类型匹配等）
    RUNTIME("Executor"); // 运行时阶段：执行查询并返回结果

    public final String componentName; // 组件名称字段，用于显示错误信息

    Stage(String componentName) { // 枚举构造函数
      this.componentName = componentName; // 初始化组件名称
    } // 结束构造函数
  } // 结束Stage枚举

  //~ Inner Classes ----------------------------------------------------------
  // 内部类区域标记，以下定义内部类

  /**
   * Checks that a type matches a given SQL type. Does not care about
   * nullability.
   */
  // SqlTypeChecker内部类：SQL类型检查器
  // 该类实现了TypeChecker接口，用于检查关系数据类型是否匹配指定的SQL类型名称
  // 不关心类型的可空性（nullable），只检查类型名称是否匹配
  private static class SqlTypeChecker implements TypeChecker { // 定义私有静态内部类，实现TypeChecker接口
    private final SqlTypeName typeName; // 类型名称字段，存储期望的SQL类型名称

    SqlTypeChecker(SqlTypeName typeName) { // 构造函数，接受SQL类型名称
      this.typeName = typeName; // 保存类型名称
    } // 结束构造函数

    @Override public void checkType(Supplier<String> sql, RelDataType type) { // 实现checkType方法
      assertThat(sql.get(), type, hasToString(typeName.toString())); // 使用Hamcrest断言检查类型的字符串表示是否匹配
    } // 结束checkType方法
  } // 结束SqlTypeChecker内部类

  /**
   * Type checker which compares types to a specified string.
   *
   * <p>The string contains "NOT NULL" constraints, but does not contain
   * collations and charsets. For example,
   *
   * <ul>
   * <li><code>INTEGER NOT NULL</code></li>
   * <li><code>BOOLEAN</code></li>
   * <li><code>DOUBLE NOT NULL MULTISET NOT NULL</code></li>
   * <li><code>CHAR(3) NOT NULL</code></li>
   * <li><code>RecordType(INTEGER X, VARCHAR(10) Y)</code></li>
   * </ul>
   */
  // StringTypeChecker内部类：字符串类型检查器
  // 该类实现了TypeChecker接口，用于将关系数据类型的字符串表示与期望的字符串进行比较
  // 字符串包含"NOT NULL"约束，但不包含排序规则和字符集信息
  // 支持的类型字符串示例：
  // - INTEGER NOT NULL：不可空的整数类型
  // - BOOLEAN：可空的布尔类型
  // - DOUBLE NOT NULL MULTISET NOT NULL：不可空的双精度多集类型
  // - CHAR(3) NOT NULL：长度为3的不可空字符类型
  // - RecordType(INTEGER X, VARCHAR(10) Y)：记录类型，包含整数和字符串字段
  public static class StringTypeChecker implements TypeChecker { // 定义公共静态内部类，实现TypeChecker接口
    private final String expected; // 期望的类型字符串

    public StringTypeChecker(String expected) { // 构造函数，接受期望的类型字符串
      this.expected = expected; // 保存期望字符串
    } // 结束构造函数

    @Override public void checkType(Supplier<String> sql, RelDataType type) { // 实现checkType方法
      String actual = getTypeString(type); // 获取类型的字符串表示
      assertThat(sql.get(), actual, is(expected)); // 使用Hamcrest断言检查实际字符串是否等于期望字符串
    } // 结束checkType方法
  } // 结束StringTypeChecker内部类

} // 结束SqlTests类
