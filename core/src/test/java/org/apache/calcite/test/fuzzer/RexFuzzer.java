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
package org.apache.calcite.test.fuzzer; // 声明包名，该类属于 org.apache.calcite.test.fuzzer 包，用于测试模糊测试功能

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入 JavaTypeFactory，用于创建 Java 类型的工厂
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType，表示关系数据类型
import org.apache.calcite.rex.RexBuilder; // 导入 RexBuilder，用于构建 RexNode 表达式节点的构建器
import org.apache.calcite.rex.RexNode; // 导入 RexNode，表示行表达式节点，是 Calcite 中表达式的基本接口
import org.apache.calcite.rex.RexProgramBuilderBase; // 导入 RexProgramBuilderBase，作为 RexProgram 构建器的基类
import org.apache.calcite.rex.RexUnknownAs; // 导入 RexUnknownAs，枚举类型，定义如何处理未知值（NULL）的行为
import org.apache.calcite.sql.SqlOperator; // 导入 SqlOperator，表示 SQL 操作符的抽象基类
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入 SqlStdOperatorTable，包含所有标准 SQL 操作符
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SqlTypeName，枚举类型，定义所有 SQL 标准数据类型名称
import org.apache.calcite.util.Pair; // 导入 Pair，工具类，用于存储键值对
import org.apache.calcite.util.Sarg; // 导入 Sarg，表示搜索参数（Search Argument），用于范围搜索优化

import com.google.common.collect.Range; // 导入 Range，Google Guava 库中的范围类，表示一个区间
import com.google.common.collect.RangeSet; // 导入 RangeSet，Google Guava 库中的范围集合类，表示多个不相交的范围集合
import com.google.common.collect.TreeRangeSet; // 导入 TreeRangeSet，Google Guava 库中基于树实现的 RangeSet

import java.math.BigDecimal; // 导入 BigDecimal，用于精确的十进制数值计算
import java.util.ArrayList; // 导入 ArrayList，动态数组实现
import java.util.List; // 导入 List，列表接口
import java.util.Map; // 导入 Map，映射接口
import java.util.Random; // 导入 Random，随机数生成器
import java.util.function.Function; // 导入 Function，函数式接口，表示接受一个参数并返回结果的函数

/**
 * RexFuzzer 类：随机生成 RexNode 表达式实例的模糊测试工具类
 * 
 * 类作用：
 * 1. 为测试目的生成随机的 RexNode 表达式树，用于测试 Calcite 的表达式处理能力
 * 2. 支持生成布尔表达式、数值表达式、CASE 表达式、SEARCH 表达式等多种类型
 * 3. 通过随机组合操作符和操作数，创建复杂的嵌套表达式
 * 4. 继承自 RexProgramBuilderBase，复用了父类的一些表达式构建辅助方法
 * 
 * 核心功能：
 * - getComparableExpression: 生成可比较的表达式（布尔或整数）
 * - getBoolExpression: 生成布尔表达式，支持 NOT、AND、OR、IS NULL 等操作符
 * - getIntExpression: 生成整数表达式，支持加减乘、一元运算符
 * - fuzzCase: 生成 CASE WHEN 表达式
 * - fuzzSearch: 生成 SEARCH 表达式，用于范围搜索
 * 
 * 使用场景：
 * 主要用于 Calcite 的单元测试和集成测试，验证表达式优化器、
 * 规则转换器等组件在各种随机表达式下的正确性和鲁棒性
 */
public class RexFuzzer extends RexProgramBuilderBase { // 定义 RexFuzzer 类，继承自 RexProgramBuilderBase
  private static final int MAX_VARS = 2; // 最大变量数量常量，表示在生成表达式时最多使用的变量个数为 2

  // 布尔类型转换到布尔类型的操作符数组，包含一元布尔操作符
  private static final SqlOperator[] BOOL_TO_BOOL = { // 定义从布尔值到布尔值的操作符数组
      SqlStdOperatorTable.NOT, // NOT 操作符：逻辑非
      SqlStdOperatorTable.IS_TRUE, // IS TRUE 操作符：判断是否为 TRUE
      SqlStdOperatorTable.IS_FALSE, // IS FALSE 操作符：判断是否为 FALSE
      SqlStdOperatorTable.IS_NOT_TRUE, // IS NOT TRUE 操作符：判断是否不为 TRUE
      SqlStdOperatorTable.IS_NOT_FALSE, // IS NOT FALSE 操作符：判断是否不为 FALSE
  };

  // 任意类型转换到布尔类型的操作符数组，主要用于 NULL 判断
  private static final SqlOperator[] ANY_TO_BOOL = { // 定义从任意类型到布尔值的操作符数组
      SqlStdOperatorTable.IS_NULL, // IS NULL 操作符：判断是否为 NULL
      SqlStdOperatorTable.IS_NOT_NULL, // IS NOT NULL 操作符：判断是否不为 NULL
      SqlStdOperatorTable.IS_UNKNOWN, // IS UNKNOWN 操作符：判断是否为 UNKNOWN（等同于 IS NULL）
      SqlStdOperatorTable.IS_NOT_UNKNOWN, // IS NOT UNKNOWN 操作符：判断是否不为 UNKNOWN
  };

  // 可比较类型转换到布尔类型的操作符数组，包含比较操作符
  private static final SqlOperator[] COMPARABLE_TO_BOOL = { // 定义从可比较类型到布尔值的操作符数组
      SqlStdOperatorTable.EQUALS, // EQUALS 操作符：等于（=）
      SqlStdOperatorTable.NOT_EQUALS, // NOT_EQUALS 操作符：不等于（<> 或 !=）
      SqlStdOperatorTable.GREATER_THAN, // GREATER_THAN 操作符：大于（>）
      SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, // GREATER_THAN_OR_EQUAL 操作符：大于等于（>=）
      SqlStdOperatorTable.LESS_THAN, // LESS_THAN 操作符：小于（<）
      SqlStdOperatorTable.LESS_THAN_OR_EQUAL, // LESS_THAN_OR_EQUAL 操作符：小于等于（<=）
      SqlStdOperatorTable.IS_DISTINCT_FROM, // IS DISTINCT FROM 操作符：判断两个值是否不同（NULL 被视为普通值）
      SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, // IS NOT DISTINCT FROM 操作符：判断两个值是否相同（NULL 被视为普通值）
  };

  // 多参数布尔类型操作符数组，接受多个布尔参数
  private static final SqlOperator[] BOOL_TO_BOOL_MULTI_ARG = { // 定义多参数布尔操作符数组
      SqlStdOperatorTable.OR, // OR 操作符：逻辑或
      SqlStdOperatorTable.AND, // AND 操作符：逻辑与
      SqlStdOperatorTable.COALESCE, // COALESCE 操作符：返回第一个非 NULL 参数
  };

  // 相同类型的多参数操作符数组
  private static final SqlOperator[] ANY_SAME_TYPE_MULTI_ARG = { // 定义任意类型但要求参数类型相同的多参数操作符数组
      SqlStdOperatorTable.COALESCE, // COALESCE 操作符：返回第一个非 NULL 参数
  };

  // 数值类型转换到数值类型的操作符数组，包含二元算术运算符
  private static final SqlOperator[] NUMERIC_TO_NUMERIC = { // 定义从数值到数值的操作符数组
      SqlStdOperatorTable.PLUS, // PLUS 操作符：加法（+）
      SqlStdOperatorTable.MINUS, // MINUS 操作符：减法（-）
      SqlStdOperatorTable.MULTIPLY, // MULTIPLY 操作符：乘法（*）
      // Divide by zero is not allowed, so we do not generate divide // 除零操作不被允许，因此不生成除法操作符
//      SqlStdOperatorTable.DIVIDE, // 注释掉的 DIVIDE 操作符：除法（/）
//      SqlStdOperatorTable.DIVIDE_INTEGER, // 注释掉的 DIVIDE_INTEGER 操作符：整数除法
  };

  // 一元数值操作符数组，接受一个数值参数
  private static final SqlOperator[] UNARY_NUMERIC = { // 定义一元数值操作符数组
      SqlStdOperatorTable.UNARY_MINUS, // UNARY_MINUS 操作符：一元负号（-）
      SqlStdOperatorTable.UNARY_PLUS, // UNARY_PLUS 操作符：一元正号（+）
  };


  // 预定义的整数值数组，用于生成整数常量
  private static final int[] INT_VALUES = {-1, 0, 1, 100500}; // 预定义的整数值数组，包含边界值和测试值

  // 成员变量：非空整数类型
  private final RelDataType intType; // intType：非空的 INTEGER 类型，用于创建整数常量
  // 成员变量：可空的整数类型
  private final RelDataType nullableIntType; // nullableIntType：可为空的 INTEGER 类型，用于创建可空的整数常量

  /**
   * RexFuzzer 构造方法：初始化随机表达式生成器
   *
   * 构造方法作用：
   * 1. 调用父类 setUp() 方法进行初始化设置
   * 2. 保存 RexBuilder 和 JavaTypeFactory 实例，用于后续创建表达式节点
   * 3. 创建 INTEGER 类型和可空 INTEGER 类型，用于生成整数表达式
   *
   * @param rexBuilder  builder to be used to create nodes // RexBuilder 实例，用于创建 RexNode 节点
   * @param typeFactory type factory // JavaTypeFactory 实例，用于创建数据类型
   */
  public RexFuzzer(RexBuilder rexBuilder, JavaTypeFactory typeFactory) { // 构造方法，接受 RexBuilder 和 JavaTypeFactory 参数
    setUp(); // 调用父类 RexProgramBuilderBase 的 setUp() 方法，进行初始化设置
    this.rexBuilder = rexBuilder; // 保存 RexBuilder 实例到成员变量，用于后续创建表达式节点
    this.typeFactory = typeFactory; // 保存 JavaTypeFactory 实例到成员变量，用于创建数据类型

    intType = typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建非空的 INTEGER 类型并赋值给 intType 成员变量
    nullableIntType = typeFactory.createTypeWithNullability(intType, true); // 基于 intType 创建可空的 INTEGER 类型并赋值给 nullableIntType 成员变量
  }

  /**
   * getExpression 方法：获取随机表达式
   *
   * 方法作用：
   * 1. 作为生成随机表达式的入口方法
   * 2. 委托给 getComparableExpression 方法生成可比较的表达式
   * 3. 支持通过 depth 参数控制表达式的嵌套深度
   *
   * @param r Random 随机数生成器，用于随机选择操作符和操作数
   * @param depth int 表达式的嵌套深度，depth=0 表示生成简单表达式，depth>0 表示生成嵌套表达式
   * @return RexNode 生成的随机表达式节点
   */
  public RexNode getExpression(Random r, int depth) { // 定义获取随机表达式的方法
    return getComparableExpression(r, depth); // 调用 getComparableExpression 方法生成可比较的表达式（布尔或整数）
  }

  /**
   * fuzzOperator 方法（重载1）：使用给定的操作数生成随机操作符调用
   *
   * 方法作用：
   * 1. 从操作符数组中随机选择一个操作符
   * 2. 使用给定的操作数创建调用表达式
   * 3. 用于生成固定操作数的表达式
   *
   * @param r Random 随机数生成器
   * @param operators SqlOperator[] 操作符数组，从中随机选择一个操作符
   * @param args RexNode... 可变参数，表示操作数
   * @return RexNode 生成的操作符调用表达式节点
   */
  private RexNode fuzzOperator(Random r, SqlOperator[] operators, RexNode... args) { // 定义使用给定操作数生成随机操作符调用的方法
    return rexBuilder.makeCall(operators[r.nextInt(operators.length)], args); // 使用 RexBuilder 创建调用表达式，从操作符数组中随机选择一个操作符
  }

  /**
   * fuzzOperator 方法（重载2）：使用工厂函数生成指定数量的操作数，然后生成随机操作符调用
   *
   * 方法作用：
   * 1. 使用工厂函数生成指定数量的操作数
   * 2. 从操作符数组中随机选择一个操作符
   * 3. 使用生成的操作数创建调用表达式
   * 4. 用于生成可变操作数的表达式
   *
   * @param r Random 随机数生成器
   * @param operators SqlOperator[] 操作符数组，从中随机选择一个操作符
   * @param length int 要生成的操作数数量
   * @param factory Function<Random, RexNode> 工厂函数，用于生成每个操作数
   * @return RexNode 生成的操作符调用表达式节点
   */
  private RexNode fuzzOperator(Random r, SqlOperator[] operators, int length, // 定义使用工厂函数生成操作数的随机操作符调用方法
      Function<Random, RexNode> factory) { // 工厂函数参数
    List<RexNode> args = new ArrayList<>(length); // 创建操作数列表，初始容量为 length
    for (int i = 0; i < length; i++) { // 循环生成指定数量的操作数
      args.add(factory.apply(r)); // 使用工厂函数生成一个操作数并添加到列表中
    }
    return rexBuilder.makeCall(operators[r.nextInt(operators.length)], args); // 使用 RexBuilder 创建调用表达式，从操作符数组中随机选择一个操作符
  }

  /**
   * getComparableExpression 方法：生成可比较的表达式
   *
   * 方法作用：
   * 1. 随机选择生成布尔表达式或整数表达式
   * 2. 作为可比较类型表达式的生成入口
   * 3. 支持通过 depth 参数控制表达式的嵌套深度
   *
   * @param r Random 随机数生成器
   * @param depth int 表达式的嵌套深度
   * @return RexNode 生成的可比较表达式节点（布尔或整数）
   */
  public RexNode getComparableExpression(Random r, int depth) { // 定义生成可比较表达式的方法
    int v = r.nextInt(2); // 生成 0 或 1 的随机数，用于选择生成布尔表达式还是整数表达式
    switch (v) { // 根据随机数进行分支
    case 0: // 如果 v=0
      return getBoolExpression(r, depth); // 生成布尔表达式
    case 1: // 如果 v=1
      return getIntExpression(r, depth); // 生成整数表达式
    }
    throw new AssertionError("should not reach here"); // 理论上不会执行到这里，如果执行则抛出断言错误
  }

  /**
   * getSimpleBool 方法：生成简单的布尔表达式
   *
   * 方法作用：
   * 1. 生成不包含嵌套的简单布尔表达式
   * 2. 可能生成：变量引用、布尔字面量（TRUE/FALSE）、NULL 布尔值
   * 3. 随机选择是否生成可空的变量引用
   *
   * @param r Random 随机数生成器
   * @return RexNode 生成的简单布尔表达式节点
   */
  public RexNode getSimpleBool(Random r) { // 定义生成简单布尔表达式的方法
    int v = r.nextInt(2); // 生成 0 或 1 的随机数（注意：这里应该是 nextInt(3) 以支持 case 2）
    switch (v) { // 根据随机数进行分支
    case 0: // 如果 v=0
      boolean nullable = r.nextBoolean(); // 随机决定是否生成可空的变量引用
      int field = r.nextInt(MAX_VARS); // 随机选择一个变量索引（0 到 MAX_VARS-1）
      return nullable ? vBool(field) : vBoolNotNull(field); // 如果 nullable 为 true 则返回可空的布尔变量，否则返回非空的布尔变量
    case 1: // 如果 v=1
      return r.nextBoolean() ? trueLiteral : falseLiteral; // 随机返回 TRUE 或 FALSE 字面量
    case 2: // 如果 v=2（由于 nextInt(2) 不会生成 2，这个分支实际上不会执行）
      return nullBool; // 返回 NULL 布尔值
    }
    throw new AssertionError("should not reach here"); // 理论上不会执行到这里，如果执行则抛出断言错误
  }

  /**
   * getBoolExpression 方法：生成布尔表达式
   *
   * 方法作用：
   * 1. 生成可能包含嵌套的复杂布尔表达式
   * 2. 支持多种布尔操作符：NOT、AND、OR、IS NULL、比较操作符等
   * 3. 支持生成 CASE WHEN 表达式和 SEARCH 表达式
   * 4. 通过 depth 参数控制表达式的嵌套深度，depth=0 时只生成简单表达式
   *
   * @param r Random 随机数生成器
   * @param depth int 表达式的嵌套深度，depth<=0 时只生成简单表达式
   * @return RexNode 生成的布尔表达式节点
   */
  public RexNode getBoolExpression(Random r, int depth) { // 定义生成布尔表达式的方法
    int v = depth <= 0 ? 0 : r.nextInt(8); // 如果 depth<=0 则 v=0（只生成简单表达式），否则生成 0-7 的随机数
    switch (v) { // 根据随机数进行分支
    case 0: // 如果 v=0
      return getSimpleBool(r); // 生成简单布尔表达式（变量引用或字面量）
    case 1: // 如果 v=1
      return fuzzOperator(r, ANY_TO_BOOL, getExpression(r, depth - 1)); // 生成任意类型到布尔值的操作符调用（如 IS NULL）
    case 2: // 如果 v=2
      return fuzzOperator(r, BOOL_TO_BOOL, getBoolExpression(r, depth - 1)); // 生成布尔到布尔的一元操作符调用（如 NOT）
    case 3: // 如果 v=3
      return fuzzOperator(r, COMPARABLE_TO_BOOL, getBoolExpression(r, depth - 1), // 生成布尔值的比较操作符调用
          getBoolExpression(r, depth - 1)); // 两个布尔操作数
    case 4: // 如果 v=4
      return fuzzOperator(r, COMPARABLE_TO_BOOL, getIntExpression(r, depth - 1), // 生成整数值的比较操作符调用
          getIntExpression(r, depth - 1)); // 两个整数操作数
    case 5: // 如果 v=5
      return fuzzOperator(r, BOOL_TO_BOOL_MULTI_ARG, r.nextInt(3) + 2, // 生成多参数布尔操作符调用（AND、OR、COALESCE）
          x -> getBoolExpression(x, depth - 1)); // 参数数量为 2-4 个，使用工厂函数生成每个布尔操作数
    case 6: // 如果 v=6
      return fuzzCase(r, depth - 1, // 生成 CASE WHEN 表达式，结果类型为布尔
          x -> getBoolExpression(x, depth - 1)); // 使用工厂函数生成 THEN 和 ELSE 子句的布尔表达式
    case 7: // 如果 v=7
      return fuzzSearch(r, getIntExpression(r, depth - 1)); // 生成 SEARCH 表达式，对整数表达式进行范围搜索
    }
    throw new AssertionError("should not reach here"); // 理论上不会执行到这里，如果执行则抛出断言错误
  }

  /**
   * getSimpleInt 方法：生成简单的整数表达式
   *
   * 方法作用：
   * 1. 生成不包含嵌套的简单整数表达式
   * 2. 可能生成：变量引用、整数字面量、NULL 整数值
   * 3. 随机选择是否生成可空的变量引用或字面量
   * 4. 整数字面量可能从预定义值中选择，也可能是随机生成的整数
   *
   * @param r Random 随机数生成器
   * @return RexNode 生成的简单整数表达式节点
   */
  public RexNode getSimpleInt(Random r) { // 定义生成简单整数表达式的方法
    int v = r.nextInt(3); // 生成 0、1 或 2 的随机数
    switch (v) { // 根据随机数进行分支
    case 0: // 如果 v=0
      boolean nullable = r.nextBoolean(); // 随机决定是否生成可空的变量引用
      int field = r.nextInt(MAX_VARS); // 随机选择一个变量索引（0 到 MAX_VARS-1）
      return nullable ? vInt(field) : vIntNotNull(field); // 如果 nullable 为 true 则返回可空的整数变量，否则返回非空的整数变量
    case 1: { // 如果 v=1
      int i = r.nextInt(INT_VALUES.length + 1); // 生成 0 到 INT_VALUES.length 的随机数
      int val = i < INT_VALUES.length ? INT_VALUES[i] : r.nextInt(); // 如果 i 在范围内则使用预定义值，否则生成随机整数
      return rexBuilder.makeLiteral(val, // 创建整数字面量
          r.nextBoolean() ? intType : nullableIntType); // 随机选择使用非空还是可空的整数类型
    }
    case 2: // 如果 v=2
      return nullInt; // 返回 NULL 整数值
    }
    throw new AssertionError("should not reach here"); // 理论上不会执行到这里，如果执行则抛出断言错误
  }

  /**
   * getIntExpression 方法：生成整数表达式
   *
   * 方法作用：
   * 1. 生成可能包含嵌套的复杂整数表达式
   * 2. 支持多种数值操作符：加减乘、一元正负号、COALESCE
   * 3. 支持生成 CASE WHEN 表达式
   * 4. 通过 depth 参数控制表达式的嵌套深度，depth=0 时只生成简单表达式
   *
   * @param r Random 随机数生成器
   * @param depth int 表达式的嵌套深度，depth<=0 时只生成简单表达式
   * @return RexNode 生成的整数表达式节点
   */
  public RexNode getIntExpression(Random r, int depth) { // 定义生成整数表达式的方法
    int v = depth <= 0 ? 0 : r.nextInt(5); // 如果 depth<=0 则 v=0（只生成简单表达式），否则生成 0-4 的随机数
    switch (v) { // 根据随机数进行分支
    case 0: // 如果 v=0
      return getSimpleInt(r); // 生成简单整数表达式（变量引用或字面量）
    case 1: // 如果 v=1
      return fuzzOperator(r, UNARY_NUMERIC, getIntExpression(r, depth - 1)); // 生成一元数值操作符调用（一元正负号）
    case 2: // 如果 v=2
      return fuzzOperator(r, NUMERIC_TO_NUMERIC, getIntExpression(r, depth - 1), // 生成二元数值操作符调用（加减乘）
          getIntExpression(r, depth - 1)); // 两个整数操作数
    case 3: // 如果 v=3
      return fuzzOperator(r, ANY_SAME_TYPE_MULTI_ARG, r.nextInt(3) + 2, // 生成多参数操作符调用（COALESCE）
          x -> getIntExpression(x, depth - 1)); // 参数数量为 2-4 个，使用工厂函数生成每个整数操作数
    case 4: // 如果 v=4
      return fuzzCase(r, depth - 1, // 生成 CASE WHEN 表达式，结果类型为整数
          x -> getIntExpression(x, depth - 1)); // 使用工厂函数生成 THEN 和 ELSE 子句的整数表达式
    }
    throw new AssertionError("should not reach here"); // 理论上不会执行到这里，如果执行则抛出断言错误
  }

  /**
   * fuzzCase 方法：生成 CASE WHEN 表达式
   *
   * 方法作用：
   * 1. 生成两种形式的 CASE WHEN 表达式：
   *    - 标准 CASE WHEN：CASE WHEN condition1 THEN result1 WHEN condition2 THEN result2 ... ELSE result END
   *    - 简化 CASE WHEN：CASE expr WHEN value1 THEN result1 WHEN value2 THEN result2 ... ELSE result END
   * 2. 随机决定分支数量（1-3 个 WHEN 子句）
   * 3. 使用 resultFactory 工厂函数生成 THEN 和 ELSE 子句的结果表达式
   * 4. 通过 depth 参数控制表达式的嵌套深度
   *
   * @param r Random 随机数生成器
   * @param depth int 表达式的嵌套深度
   * @param resultFactory Function<Random, RexNode> 工厂函数，用于生成 THEN 和 ELSE 子句的结果表达式
   * @return RexNode 生成的 CASE WHEN 表达式节点
   */
  public RexNode fuzzCase(Random r, int depth, Function<Random, RexNode> resultFactory) { // 定义生成 CASE WHEN 表达式的方法
    boolean caseArgWhen = r.nextBoolean(); // 随机决定是否生成简化形式的 CASE WHEN（CASE expr WHEN value THEN result）
    int caseBranches = 1 + (depth <= 0 ? 0 : r.nextInt(3)); // 计算分支数量，depth<=0 时为 1，否则为 1-3 个分支
    List<RexNode> args = new ArrayList<>(caseBranches + 1); // 创建参数列表，容量为分支数+1（+1 是为了 ELSE 子句）

    Function<Random, RexNode> exprFactory; // 声明表达式工厂函数，用于生成 WHEN 条件
    if (!caseArgWhen) { // 如果不使用简化形式（标准 CASE WHEN）
      exprFactory = x -> getBoolExpression(x, depth - 1); // 表达式工厂函数生成布尔条件（WHEN condition）
    } else { // 如果使用简化形式（CASE expr WHEN value）
      int type = r.nextInt(2); // 随机选择类型（0 或 1）
      RexNode arg; // 声明表达式参数
      Function<Random, RexNode> baseExprFactory; // 声明基础表达式工厂函数
      switch (type) { // 根据类型进行分支
      case 0: // 如果 type=0
        baseExprFactory = x -> getBoolExpression(x, depth - 1); // 基础表达式工厂函数生成布尔表达式
        break; // 跳出 switch
      case 1: // 如果 type=1
        baseExprFactory = x -> getIntExpression(x, depth - 1); // 基础表达式工厂函数生成整数表达式
        break; // 跳出 switch
      default: // 默认情况（理论上不会执行）
        throw new AssertionError("should not reach here: " + type); // 抛出断言错误
      }
      arg = baseExprFactory.apply(r); // 使用基础表达式工厂函数生成表达式参数
      // emulate  case when arg=2 then .. when arg=4 then ... // 模拟 CASE arg WHEN value THEN result 的形式
      exprFactory = x -> eq(arg, baseExprFactory.apply(x)); // 表达式工厂函数生成等值条件（arg = value）
    }

    for (int i = 0; i < caseBranches; i++) { // 循环生成每个 WHEN-THEN 分支
      args.add(exprFactory.apply(r)); // when // 添加 WHEN 条件到参数列表
      args.add(resultFactory.apply(r)); // then // 添加 THEN 结果到参数列表
    }
    args.add(resultFactory.apply(r)); // else // 添加 ELSE 结果到参数列表
    return case_(args); // 调用父类 case_ 方法生成 CASE WHEN 表达式
  }

  /**
   * fuzzSearch 方法：生成 SEARCH 表达式
   *
   * 方法作用：
   * 1. 生成 SEARCH 表达式，用于测试范围搜索功能
   * 2. 创建一个或多个范围（Range），组成范围集合（RangeSet）
   * 3. 使用 Sarg（Search Argument）封装范围集合和未知值处理策略
   * 4. 生成 SEARCH 操作符调用，用于优化查询执行
   *
   * SEARCH 表达式示例：
   *   col SEARCH (1, 5] OR [10, 20) 表示 col 在 (1, 5] 范围内或在 [10, 20) 范围内
   *
   * @param r Random 随机数生成器
   * @param intExpression RexNode 要进行搜索的整数表达式
   * @return RexNode 生成的 SEARCH 表达式节点
   */
  public RexNode fuzzSearch(Random r, RexNode intExpression) { // 定义生成 SEARCH 表达式的方法
    final RangeSet<BigDecimal> rangeSet = TreeRangeSet.create(); // 创建空的树形范围集合，用于存储多个不相交的范围
    final Generator<BigDecimal> integerGenerator = RexFuzzer::fuzzInt; // 创建整数生成器，用于生成范围边界值
    final Generator<RexUnknownAs> unknownGenerator = // 创建未知值处理策略生成器
        enumGenerator(RexUnknownAs.class); // 从 RexUnknownAs 枚举中随机选择一个值（FALSE、TRUE、NULL）
    int i = 0; // 初始化计数器
    for (;;) { // 无限循环
      rangeSet.add(fuzzRange(r, integerGenerator)); // 生成一个随机范围并添加到范围集合中
      if (r.nextBoolean() || i++ == 8) { // 如果随机数为 true 或者已经添加了 8 个范围
        break; // 跳出循环
      }
    }
    final Sarg<BigDecimal> sarg = // 创建搜索参数（Search Argument）
        Sarg.of(unknownGenerator.generate(r), rangeSet); // 使用随机生成的未知值处理策略和范围集合创建 Sarg
    return rexBuilder.makeCall(SqlStdOperatorTable.SEARCH, intExpression, // 创建 SEARCH 操作符调用
        rexBuilder.makeSearchArgumentLiteral(sarg, intExpression.getType())); // 将 Sarg 转换为字面量参数
  }

  /**
   * enumGenerator 方法：创建枚举类型的值生成器
   *
   * 方法作用：
   * 1. 为指定的枚举类型创建一个生成器
   * 2. 生成器可以随机返回该枚举类型的任意一个常量值
   * 3. 用于在模糊测试中随机选择枚举值
   *
   * @param <T> 枚举类型参数，必须继承自 Enum
   * @param enumClass Class<T> 枚举类型的 Class 对象
   * @return Generator<T> 枚举值生成器，可以随机生成该枚举类型的值
   */
  private static <T extends Enum<T>> Generator<T> enumGenerator( // 定义创建枚举类型生成器的方法
      Class<T> enumClass) { // 枚举类型的 Class 对象参数
    final T[] enumConstants = enumClass.getEnumConstants(); // 获取枚举类型的所有常量值数组
    return r -> enumConstants[r.nextInt(enumConstants.length)]; // 返回一个生成器函数，随机选择并返回一个枚举常量
  }

  /**
   * fuzzRange 方法：生成随机范围（Range）
   *
   * 方法作用：
   * 1. 生成各种类型的范围：全范围、单边范围、单点范围、双边范围
   * 2. 支持开区间、闭区间、半开半闭区间
   * 3. 使用生成器工厂函数生成范围边界值
   * 4. 随机选择 10 种可能的范围类型之一
   *
   * 范围类型示例：
   *   Range.all(): (-∞, +∞)
   *   Range.atLeast(5): [5, +∞)
   *   Range.atMost(10): (-∞, 10]
   *   Range.greaterThan(5): (5, +∞)
   *   Range.lessThan(10): (-∞, 10)
   *   Range.singleton(7): [7, 7]
   *   Range.closed(5, 10): [5, 10]
   *   Range.closedOpen(5, 10): [5, 10)
   *   Range.openClosed(5, 10): (5, 10]
   *   Range.open(5, 10): (5, 10)
   *
   * @param <T> 范围边界值的类型，必须实现 Comparable 接口
   * @param r Random 随机数生成器
   * @param generator Generator<T> 值生成器，用于生成范围边界值
   * @return Range<T> 生成的随机范围
   */
  <T extends Comparable<T>> Range<T> fuzzRange(Random r, // 定义生成随机范围的方法
      Generator<T> generator) { // 值生成器参数
    final Map.Entry<T, T> pair; // 声明键值对，用于存储范围的两个边界值
    switch (r.nextInt(10)) { // 生成 0-9 的随机数，根据随机数选择范围类型
    case 0: // 如果随机数为 0
      return Range.all(); // 返回全范围 (-∞, +∞)
    case 1: // 如果随机数为 1
      return Range.atLeast(generator.generate(r)); // 返回左闭右无限范围 [value, +∞)
    case 2: // 如果随机数为 2
      return Range.atMost(generator.generate(r)); // 返回左无限右闭范围 (-∞, value]
    case 3: // 如果随机数为 3
      return Range.greaterThan(generator.generate(r)); // 返回左开右无限范围 (value, +∞)
    case 4: // 如果随机数为 4
      return Range.lessThan(generator.generate(r)); // 返回左无限右开范围 (-∞, value)
    case 5: // 如果随机数为 5
      return Range.singleton(generator.generate(r)); // 返回单点范围 [value, value]
    case 6: // 如果随机数为 6
      pair = orderedPair(r, false, generator); // 生成有序值对（允许相等）
      return Range.closed(pair.getKey(), pair.getValue()); // 返回闭区间 [lower, upper]
    case 7: // 如果随机数为 7
      pair = orderedPair(r, false, generator); // 生成有序值对（允许相等）
      return Range.closedOpen(pair.getKey(), pair.getValue()); // 返回左闭右开区间 [lower, upper)
    case 8: // 如果随机数为 8
      pair = orderedPair(r, false, generator); // 生成有序值对（允许相等）
      return Range.openClosed(pair.getKey(), pair.getValue()); // 返回左开右闭区间 (lower, upper]
    case 9: // 如果随机数为 9
      pair = orderedPair(r, true, generator); // 生成有序值对（不允许相等）
      return Range.open(pair.getKey(), pair.getValue()); // 返回开区间 (lower, upper)
    default: // 默认情况（理论上不会执行）
      throw new AssertionError(); // 抛出断言错误
    }
  }

  /**
   * orderedPair 方法：生成有序的值对
   *
   * 方法作用：
   * 1. 生成两个值，确保第一个值小于或等于第二个值
   * 2. 如果 strict 参数为 true，则要求第一个值严格小于第二个值
   * 3. 如果生成的两个值不满足条件，则重新生成
   * 4. 用于创建有效范围的边界值
   *
   * @param <T> 值的类型，必须实现 Comparable 接口
   * @param r Random 随机数生成器
   * @param strict boolean 是否要求严格小于（不允许相等）
   * @param generator Generator<T> 值生成器
   * @return Pair<T, T> 有序的值对，第一个值小于或等于第二个值
   */
  /** Generates a pair of values, the first being less than or equal to the
   * second. */ // 注释：生成一对值，第一个值小于或等于第二个值
  static <T extends Comparable<T>> Pair<T, T> orderedPair(Random r, // 定义生成有序值对的方法
      boolean strict, Generator<T> generator) { // strict 参数表示是否要求严格小于
    for (;;) { // 无限循环，直到生成满足条件的值对
      final T v0 = generator.generate(r); // 使用生成器生成第一个值
      final T v1 = generator.generate(r); // 使用生成器生成第二个值
      int c = v0.compareTo(v1); // 比较两个值
      if (strict && c == 0) { // 如果要求严格小于且两个值相等
        continue; // 跳过本次循环，重新生成
      }
      return c <= 0 ? Pair.of(v0, v1) : Pair.of(v1, v0); // 如果 v0 <= v1 则返回 (v0, v1)，否则返回 (v1, v0)
    }
  }

  /**
   * fuzzInt 方法：生成随机整数
   *
   * 方法作用：
   * 1. 生成 -5 到 10（包含边界）之间的随机整数
   * 2. 所有值出现的概率相等
   * 3. 返回 BigDecimal 类型以支持精确计算
   * 4. 用于生成范围边界值
   *
   * @param r Random 随机数生成器
   * @return BigDecimal 生成的随机整数，范围在 -5 到 10 之间
   */
  /** Generates an integer between -5 and 10 (inclusive). All values are equally
   * likely. */ // 注释：生成 -5 到 10（包含边界）之间的随机整数，所有值出现的概率相等
  static BigDecimal fuzzInt(Random r) { // 定义生成随机整数的方法
    return BigDecimal.valueOf(r.nextInt(16) - 5); // 生成 0-15 的随机数，减 5 后得到 -5 到 10 的范围，转换为 BigDecimal
  }

  /**
   * Generator 接口：值生成器接口
   *
   * 接口作用：
   * 1. 定义一个函数式接口，用于生成特定类型的值
   * 2. 接受随机数生成器作为参数
   * 3. 返回生成的值
   * 4. 用于在模糊测试中随机生成各种类型的值
   *
   * 使用场景：
   * - 生成范围边界值
   * - 生成枚举值
   * - 生成表达式操作数
   *
   * @param <T> 要生成的值的类型
   */
  /** Generates values of a particular type, given a random-number generator.
   *
   * @param <T> Value type */ // 注释：给定随机数生成器，生成特定类型的值
  interface Generator<T> { // 定义值生成器接口
    T generate(Random r); // 定义生成方法，接受随机数生成器，返回生成的值
  }
} // 类定义结束
