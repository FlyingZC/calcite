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
package org.apache.calcite.piglet;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.validate.SqlUserDefinedFunction;

import org.apache.pig.Accumulator;
import org.apache.pig.FuncSpec;
import org.apache.pig.impl.logicalLayer.FrontendException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * This class maps a Pig UDF to a corresponding SQL built-in function/operator.
 * If such mapping is not available, it creates a wrapper to allow SQL engines
 * call Pig UDFs directly.
 * 本类用于将 Pig UDF（用户定义函数）映射到对应的 SQL 内置函数/操作符。
 * 如果找不到对应的映射，它会创建一个包装器，允许 SQL 引擎直接调用 Pig UDF。
 * 这是 Piglet 模块中的核心转换器，负责在 Pig 脚本和 Calcite 关系代数之间建立函数调用的桥梁。
 *
 */
class PigRelUdfConverter { // PigRelUdfConverter 类：Pig UDF 到 SQL 操作符的转换器，负责将 Pig 函数调用转换为 Calcite 的 RexNode 表达式

  private PigRelUdfConverter() {} // 私有构造函数，防止实例化，这是一个纯静态工具类

  private static final PigUdfFinder PIG_UDF_FINDER = new PigUdfFinder(); // Pig UDF 查找器实例，用于通过反射查找 Pig UDF 类中的实现方法（如 exec、accumulate 等）

  private static final Map<String, SqlOperator> BUILTIN_FUNC = // 内置函数映射表：将 Pig 内置函数类名映射到 Calcite 的标准 SQL 操作符，用于标量函数
      ImmutableMap.<String, SqlOperator>builder()
          .put("org.apache.pig.builtin.ABS", SqlStdOperatorTable.ABS) // 绝对值函数：Pig 的 ABS 映射到 SQL 的 ABS
          .put("org.apache.pig.builtin.BigDecimalAbs", SqlStdOperatorTable.ABS) // BigDecimal 类型的绝对值
          .put("org.apache.pig.builtin.BigIntegerAbs", SqlStdOperatorTable.ABS) // BigInteger 类型的绝对值
          .put("org.apache.pig.builtin.DoubleAbs", SqlStdOperatorTable.ABS) // Double 类型的绝对值
          .put("org.apache.pig.builtin.FloatAbs", SqlStdOperatorTable.ABS) // Float 类型的绝对值
          .put("org.apache.pig.builtin.IntAbs", SqlStdOperatorTable.ABS) // Int 类型的绝对值
          .put("org.apache.pig.builtin.LongAbs", SqlStdOperatorTable.ABS) // Long 类型的绝对值
          .put("org.apache.pig.builtin.CEIL", SqlStdOperatorTable.CEIL) // 向上取整函数
          .put("org.apache.pig.builtin.CONCAT", SqlStdOperatorTable.CONCAT) // 字符串连接函数
          .put("org.apache.pig.builtin.StringConcat", SqlStdOperatorTable.CONCAT) // 字符串连接函数的另一种实现
          .put("org.apache.pig.builtin.EXP", SqlStdOperatorTable.EXP) // 指数函数 e^x
          .put("org.apache.pig.builtin.FLOOR", SqlStdOperatorTable.FLOOR) // 向下取整函数
          .put("org.apache.pig.builtin.LOG", SqlStdOperatorTable.LN) // 自然对数函数 ln(x)
          .put("org.apache.pig.builtin.LOG10", SqlStdOperatorTable.LOG10) // 以 10 为底的对数函数
          .put("org.apache.pig.builtin.LOWER", SqlStdOperatorTable.LOWER) // 转换为小写
          .put("org.apache.pig.builtin.RANDOM", SqlStdOperatorTable.RAND) // 随机数函数
          .put("org.apache.pig.builtin.SQRT", SqlStdOperatorTable.SQRT) // 平方根函数
          .put("org.apache.pig.builtin.StringSize", SqlStdOperatorTable.CHAR_LENGTH) // 字符串长度函数
          .put("org.apache.pig.builtin.SUBSTRING", SqlStdOperatorTable.SUBSTRING) // 子字符串提取函数
          .put("org.apache.pig.builtin.TOTUPLE", SqlStdOperatorTable.ROW) // 将值转换为元组/行
          .put("org.apache.pig.builtin.UPPER", SqlStdOperatorTable.UPPER) // 转换为大写
          .build();

  private static final Map<String, SqlAggFunction> BUILTIN_AGG_FUNC = // 内置聚合函数映射表：将 Pig 聚合 UDF 映射到 Calcite 的标准 SQL 聚合函数
      ImmutableMap.<String, SqlAggFunction>builder()
          // AVG() - 平均值聚合函数
          .put("org.apache.pig.builtin.AVG", SqlStdOperatorTable.AVG) // 通用平均数函数
          .put("org.apache.pig.builtin.BigDecimalAvg", SqlStdOperatorTable.AVG) // BigDecimal 类型的平均值
          .put("org.apache.pig.builtin.BigIntegerAvg", SqlStdOperatorTable.AVG) // BigInteger 类型的平均值
          .put("org.apache.pig.builtin.DoubleAvg", SqlStdOperatorTable.AVG) // Double 类型的平均值
          .put("org.apache.pig.builtin.FloatAvg", SqlStdOperatorTable.AVG) // Float 类型的平均值
          .put("org.apache.pig.builtin.IntAvg", SqlStdOperatorTable.AVG) // Int 类型的平均值
          .put("org.apache.pig.builtin.LongAvg", SqlStdOperatorTable.AVG) // Long 类型的平均值
          // COUNT() - 计数聚合函数
          .put("org.apache.pig.builtin.COUNT", SqlStdOperatorTable.COUNT) // 计数函数
          // MAX() - 最大值聚合函数
          .put("org.apache.pig.builtin.MAX", SqlStdOperatorTable.MAX) // 通用最大值函数
          .put("org.apache.pig.builtin.BigDecimalMax", SqlStdOperatorTable.MAX) // BigDecimal 类型的最大值
          .put("org.apache.pig.builtin.BigIntegerMax", SqlStdOperatorTable.MAX) // BigInteger 类型的最大值
          .put("org.apache.pig.builtin.DateTimeMax", SqlStdOperatorTable.MAX) // DateTime 类型的最大值
          .put("org.apache.pig.builtin.DoubleMax", SqlStdOperatorTable.MAX) // Double 类型的最大值
          .put("org.apache.pig.builtin.FloatMax", SqlStdOperatorTable.MAX) // Float 类型的最大值
          .put("org.apache.pig.builtin.IntMax", SqlStdOperatorTable.MAX) // Int 类型的最大值
          .put("org.apache.pig.builtin.LongMax", SqlStdOperatorTable.MAX) // Long 类型的最大值
          .put("org.apache.pig.builtin.StringMax", SqlStdOperatorTable.MAX) // String 类型的最大值
          // MIN() - 最小值聚合函数
          .put("org.apache.pig.builtin.MIN", SqlStdOperatorTable.MIN) // 通用最小值函数
          .put("org.apache.pig.builtin.BigDecimalMin", SqlStdOperatorTable.MIN) // BigDecimal 类型的最小值
          .put("org.apache.pig.builtin.BigIntegerMin", SqlStdOperatorTable.MIN) // BigInteger 类型的最小值
          .put("org.apache.pig.builtin.DateTimeMin", SqlStdOperatorTable.MIN) // DateTime 类型的最小值
          .put("org.apache.pig.builtin.DoubleMin", SqlStdOperatorTable.MIN) // Double 类型的最小值
          .put("org.apache.pig.builtin.FloatMin", SqlStdOperatorTable.MIN) // Float 类型的最小值
          .put("org.apache.pig.builtin.IntMin", SqlStdOperatorTable.MIN) // Int 类型的最小值
          .put("org.apache.pig.builtin.LongMin", SqlStdOperatorTable.MIN) // Long 类型的最小值
          .put("org.apache.pig.builtin.StringMin", SqlStdOperatorTable.MIN) // String 类型的最小值
          // SUM() - 求和聚合函数
          .put("org.apache.pig.builtin.BigDecimalSum", SqlStdOperatorTable.SUM) // BigDecimal 类型的求和
          .put("org.apache.pig.builtin.BigIntegerSum", SqlStdOperatorTable.SUM) // BigInteger 类型的求和
          .put("org.apache.pig.builtin.DoubleSum", SqlStdOperatorTable.SUM) // Double 类型的求和
          .put("org.apache.pig.builtin.FloatSum", SqlStdOperatorTable.SUM) // Float 类型的求和
          .put("org.apache.pig.builtin.IntSum", SqlStdOperatorTable.SUM) // Int 类型的求和
          .put("org.apache.pig.builtin.LongSum", SqlStdOperatorTable.SUM) // Long 类型的求和
          .build();

  /**
   * Converts a Pig UDF, given its {@link FuncSpec} and a list of relational
   * operands (function arguments). To call this function, the arguments of
   * Pig functions need to be converted into the relational types before.
   * 将 Pig UDF 转换为对应的 SQL 函数调用。根据 Pig 函数规范和关系操作数列表，
   * 将 Pig 函数调用转换为 Calcite 的 RexNode 表达式。在调用此函数之前，
   * Pig 函数的参数需要先转换为关系类型。
   *
   * @param builder The relational builder - 关系构建器，用于构建 SQL 表达式
   * @param pigFunc Pig function description - Pig 函数规范，包含函数类名等信息
   * @param operands Relational operands for the function - 函数的关系操作数（参数列表）
   * @param returnType Function return data type - 函数的返回数据类型
   * @return The SQL calls equivalent to the Pig function - 等价于 Pig 函数的 SQL 调用表达式（RexNode）
   */
  static RexNode convertPigFunction(PigRelBuilder builder, FuncSpec pigFunc, // 静态方法：将 Pig UDF 转换为 RexNode 表达式
      ImmutableList<RexNode> operands, RelDataType returnType) throws FrontendException { // 参数：关系构建器、Pig 函数规范、操作数列表、返回类型；可能抛出前端异常
    // First, check the map for the direct mapping SQL builtin - 首先检查映射表，查找是否有直接对应的 SQL 内置函数
    final SqlOperator operator = BUILTIN_FUNC.get(pigFunc.getClassName()); // 从内置函数映射表中获取对应的 SQL 操作符
    if (operator != null) { // 如果找到了对应的 SQL 操作符
      return builder.call(operator, operands); // 直接使用 SQL 操作符构建表达式并返回，无需创建包装器
    }

    // If no mapping found, build the argument wrapper to convert the relation operands - 如果没有找到映射，构建参数包装器将关系操作数
    // into a Pig tuple so that the Pig function can consume it. - 转换为 Pig 元组，以便 Pig 函数可以消费
    try { // 开始尝试加载 Pig UDF 类并创建包装器
      // Find the implementation method for the Pig function from - 从定义 UDF 的类中查找 Pig 函数的实现方法
      // the class defining the UDF. - （如 exec、accumulate 等方法）
      final Class clazz = Class.forName(pigFunc.getClassName()); // 通过反射加载 Pig UDF 类
      final Method method = // 使用 Pig UDF 查找器找到实现方法
          PIG_UDF_FINDER.findPigUdfImplementationMethod(clazz); // 通过反射查找 UDF 类中的实现方法

      // Now create the argument wrapper. Depend on the type of the UDF, the - 现在创建参数包装器。根据 UDF 的类型，
      // relational operands are converted into a Pig Tuple or Pig DataBag - 关系操作数将被转换为 Pig 元组或 Pig 数据包
      // with the appropriate wrapper. - 使用适当的包装器
      final SqlUserDefinedFunction convertOp = // 定义参数转换操作符
          Accumulator.class.isAssignableFrom(clazz) // 检查 UDF 是否实现 Accumulator 接口（聚合函数）
              ? PigRelSqlUdfs.createPigBagUDF(operands) // 如果是聚合函数，创建 Pig 数据包包装器（用于处理多行数据）
              : PigRelSqlUdfs.createPigTupleUDF(operands); // 如果是标量函数，创建 Pig 元组包装器（用于处理单行数据）
      final RexNode rexTuple = builder.call(convertOp, operands); // 调用包装器函数，将关系操作数转换为 Pig 元组/数据包

      // Then convert the Pig function into a @SqlUserDefinedFunction. - 然后将 Pig 函数转换为 SQL 用户定义函数
      SqlUserDefinedFunction userFuncOp = // 创建 SQL 用户定义函数操作符
          PigRelSqlUdfs.createGeneralPigUdf(clazz.getSimpleName(), // 参数：类名（简单名称）
              method, pigFunc, rexTuple.getType(), returnType); // 参数：实现方法、Pig 函数规范、输入类型、返回类型

      // Ready to return SqlCall after having SqlUDF and operand - 准备返回 SQL 调用，现在有了 SQL UDF 和操作数
      return builder.call(userFuncOp, ImmutableList.of(rexTuple)); // 调用用户定义函数，传入转换后的 Pig 元组作为参数
    } catch (ClassNotFoundException e) { // 捕获类未找到异常
      throw new FrontendException("Cannot find the implementation for Pig UDF class: " // 抛出前端异常，提示无法找到 Pig UDF 类的实现
          + pigFunc.getClassName()); // 异常信息包含无法加载的类名
    }
  }

  /**
   * Gets the {@link SqlAggFunction} for the corresponding Pig aggregate
   * UDF call; returns null for invalid rex call.
   * 获取对应 Pig 聚合 UDF 调用的 SQL 聚合函数；对于无效的 Rex 调用返回 null。
   * 此方法用于在优化阶段将 Pig 聚合函数替换为 Calcite 的标准 SQL 聚合函数，
   * 以便进行更高效的查询优化。
   *
   * @param call Pig aggregate UDF call - Pig 聚合 UDF 调用的 RexCall 表达式
   * @return 对应的 SQL 聚合函数，如果找不到映射或调用无效则返回 null
   */
  static SqlAggFunction getSqlAggFuncForPigUdf(RexCall call) { // 静态方法：获取 Pig 聚合 UDF 对应的 SQL 聚合函数
    if (!(call.getOperator() instanceof PigUserDefinedFunction)) { // 检查操作符是否为 Pig 用户定义函数
      return null; // 如果不是 Pig UDF，返回 null（无效调用）
    }

    final PigUserDefinedFunction pigUdf = (PigUserDefinedFunction) call.getOperator(); // 将操作符转换为 Pig 用户定义函数类型
    if (pigUdf.funcSpec != null) { // 检查 Pig 函数规范是否存在
      final String pigUdfClassName = pigUdf.funcSpec.getClassName(); // 获取 Pig UDF 的完整类名
      final SqlAggFunction sqlAggFunction = BUILTIN_AGG_FUNC.get(pigUdfClassName); // 从内置聚合函数映射表中查找对应的 SQL 聚合函数
      if (sqlAggFunction == null) { // 如果没有找到对应的 SQL 聚合函数
        final Class udfClass = // 获取 UDF 的实际类
            ((ScalarFunctionImpl) pigUdf.getFunction()).method.getDeclaringClass(); // 从函数实现中获取声明类
        if (Accumulator.class.isAssignableFrom(udfClass)) { // 检查 UDF 是否实现 Accumulator 接口（即是否为聚合函数）
          throw new UnsupportedOperationException( // 如果是聚合函数但没有找到映射，抛出不支持操作异常
              "Cannot find corresponding SqlAgg func for Pig aggegate " + pigUdfClassName); // 异常信息：无法找到对应的 SQL 聚合函数
        }
      }
      return sqlAggFunction; // 返回找到的 SQL 聚合函数（如果找到）
    }
    return null; // 如果 funcSpec 为 null，返回 null
  }
} // PigRelUdfConverter 类结束
