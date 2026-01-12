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
package org.apache.calcite.rex; // 声明包名，该类属于org.apache.calcite.rex包，用于处理Calcite框架中的行表达式(Rex)相关功能
import org.apache.calcite.DataContext; // 导入DataContext接口，提供数据上下文信息，用于在表达式执行时获取变量值
import org.apache.calcite.DataContexts; // 导入DataContexts工具类，用于创建DataContext实例
import org.apache.calcite.avatica.util.ByteString; // 导入ByteString类，用于处理二进制字符串数据
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.sql.SqlBinaryOperator; // 导入SqlBinaryOperator类，表示二元SQL操作符
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举，定义SQL操作符的种类
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，表示SQL操作符的基类
import org.apache.calcite.sql.fun.SqlLibraryOperators; // 导入SqlLibraryOperators类，包含特定库的SQL操作符
import org.apache.calcite.sql.fun.SqlMonotonicBinaryOperator; // 导入SqlMonotonicBinaryOperator类，表示单调二元操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，包含标准SQL操作符表
import org.apache.calcite.sql.type.InferTypes; // 导入InferTypes类，用于类型推断
import org.apache.calcite.sql.type.OperandTypes; // 导入OperandTypes类，定义操作数类型检查规则
import org.apache.calcite.sql.type.ReturnTypes; // 导入ReturnTypes类，定义返回类型推断规则
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL类型名称
import org.apache.calcite.test.Matchers; // 导入Matchers测试工具类，提供自定义匹配器
import org.apache.calcite.tools.Frameworks; // 导入Frameworks工具类，用于创建Calcite框架环境
import org.apache.calcite.util.DateString; // 导入DateString类，用于处理日期字符串
import org.apache.calcite.util.NlsString; // 导入NlsString类，用于处理国际化字符串
import org.apache.calcite.util.TestUtil; // 导入TestUtil工具类，提供测试辅助方法
import org.apache.calcite.util.TimestampString; // 导入TimestampString类，用于处理时间戳字符串
import org.apache.calcite.util.Util; // 导入Util工具类，提供通用工具方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，用于创建不可变列表
import com.google.common.collect.ImmutableMap; // 导入Google Guava的ImmutableMap类，用于创建不可变映射

import org.hamcrest.Matcher; // 导入Hamcrest的Matcher接口，用于断言匹配
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.math.BigDecimal; // 导入BigDecimal类，用于精确的十进制运算
import java.util.ArrayList; // 导入ArrayList类，动态数组实现
import java.util.List; // 导入List接口，列表集合接口
import java.util.Locale; // 导入Locale类，用于本地化信息
import java.util.Random; // 导入Random类，用于生成随机数
import java.util.TimeZone; // 导入TimeZone类，用于时区信息
import java.util.function.Function; // 导入Function接口，函数式接口

import static org.hamcrest.CoreMatchers.equalTo; // 静态导入equalTo匹配器
import static org.hamcrest.CoreMatchers.instanceOf; // 静态导入instanceOf匹配器
import static org.hamcrest.CoreMatchers.is; // 静态导入is匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入assertThat断言方法
import static org.hamcrest.Matchers.hasSize; // 静态导入hasSize匹配器
import static org.hamcrest.Matchers.hasToString; // 静态导入hasToString匹配器
import static org.junit.jupiter.api.Assertions.fail; // 静态导入fail方法，用于测试失败

import static java.nio.charset.StandardCharsets.UTF_8; // 静态导入UTF_8字符集常量
import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于空值检查

/**
 * Unit test for {@link org.apache.calcite.rex.RexExecutorImpl}.
 * RexExecutorImpl的单元测试类，用于测试Rex表达式执行器的各种功能
 */
class RexExecutorTest { // 定义RexExecutorTest测试类，用于测试RexExecutorImpl表达式执行器的功能
  protected void check(final Action action) { // 定义check方法，用于执行测试动作，接受Action函数式接口参数
    Frameworks.withPrepare((cluster, relOptSchema, rootSchema, statement) -> { // 使用Frameworks.withPrepare创建Calcite框架环境，lambda表达式接收四个参数：cluster(集群对象)、relOptSchema(关系模式)、rootSchema(根模式)、statement(语句)
      final RexBuilder rexBuilder = cluster.getRexBuilder(); // 从cluster中获取RexBuilder对象，用于构建Rex表达式
      final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder(); // 创建不可变Map的构建器，用于存储数据上下文变量
      builder.put(DataContext.Variable.TIME_ZONE.camelName, TimeZone.getTimeZone("GMT")); // 将时区变量放入构建器，设置为GMT时区
      builder.put(DataContext.Variable.LOCALE.camelName, Locale.US); // 将本地化变量放入构建器，设置为美国本地化
      final DataContext dataContext = DataContexts.of(builder.build()); // 使用构建器创建DataContext对象，提供数据上下文
      final RexExecutorImpl executor = new RexExecutorImpl(dataContext); // 创建RexExecutorImpl实例，用于执行Rex表达式
      action.check(rexBuilder, executor); // 调用action的check方法，传入rexBuilder和executor进行测试
      return null; // 返回null，因为withPrepare需要返回值
    });
  }

  /** Tests an executor that uses variables stored in a {@link DataContext}.
   * Can change the value of the variable and execute again. */
  @Test void testVariableExecution() { // 测试使用DataContext中存储变量的执行器，可以修改变量值并再次执行
    check((rexBuilder, executor) -> { // 调用check方法，传入lambda表达式接收rexBuilder和executor
      Object[] values = new Object[1]; // 创建Object数组，用于存储输入记录的值
      final DataContext testContext = // 创建测试DataContext，通过lambda表达式提供变量查找功能
          DataContexts.of(name -> // name是变量名，根据变量名返回对应的值
              name.equals("inputRecord") ? values : fail("unknown: " + name)); // 如果变量名是"inputRecord"则返回values数组，否则抛出失败异常
      final RelDataTypeFactory typeFactory = rexBuilder.getTypeFactory(); // 从rexBuilder获取类型工厂，用于创建SQL类型
      final RelDataType varchar = // 创建VARCHAR类型
          typeFactory.createSqlType(SqlTypeName.VARCHAR);
      final RelDataType integer = // 创建INTEGER类型
          typeFactory.createSqlType(SqlTypeName.INTEGER);
      // Calcite is internally creating the input ref via a RexRangeRef
      // which eventually leads to a RexInputRef. So we are good.
      final RexInputRef input = rexBuilder.makeInputRef(varchar, 0); // 创建输入引用，引用第0列，类型为VARCHAR
      final RexNode lengthArg = rexBuilder.makeLiteral(3, integer, true); // 创建字面量3，类型为INTEGER
      final RexNode substr = // 创建SUBSTRING函数调用，从input的lengthArg位置开始截取
          rexBuilder.makeCall(SqlStdOperatorTable.SUBSTRING, input,
              lengthArg);
      ImmutableList<RexNode> constExps = ImmutableList.of(substr); // 创建包含substr表达式的不可变列表

      final RelDataType rowType = typeFactory.builder() // 创建行类型构建器
          .add("someStr", varchar) // 添加列名"someStr"，类型为VARCHAR
          .build(); // 构建行类型

      final RexExecutable exec = // 创建可执行的Rex表达式
          RexExecutorImpl.getExecutable(rexBuilder, constExps, rowType); // 通过rexBuilder、表达式列表和行类型获取可执行对象
      exec.setDataContext(testContext); // 设置执行器的数据上下文为testContext
      values[0] = "Hello World"; // 设置输入记录的值为"Hello World"
      Object[] result = exec.execute(); // 执行表达式，获取结果数组
      assertThat(result[0], instanceOf(String.class)); // 断言结果第一个元素是String类型
      assertThat((String) result[0], equalTo("llo World")); // 断言结果为"llo World"（从位置3开始截取）
      values[0] = "Calcite"; // 修改输入记录的值为"Calcite"
      result = exec.execute(); // 再次执行表达式
      assertThat(result[0], instanceOf(String.class)); // 断言结果第一个元素是String类型
      assertThat((String) result[0], equalTo("lcite")); // 断言结果为"lcite"（从位置3开始截取）
    });
  }

  @Test void testConstant() { // 测试常量表达式的归约功能
    check((rexBuilder, executor) -> { // 调用check方法，传入lambda表达式接收rexBuilder和executor
      final List<RexNode> reducedValues = new ArrayList<>(); // 创建列表用于存储归约后的表达式
      final RexLiteral ten = rexBuilder.makeExactLiteral(BigDecimal.TEN); // 创建字面量10的RexLiteral对象
      executor.reduce(rexBuilder, ImmutableList.of(ten), // 调用executor的reduce方法，将常量表达式归约
          reducedValues); // 将归约结果存入reducedValues列表
      assertThat(reducedValues, hasSize(1)); // 断言归约结果列表大小为1
      assertThat(reducedValues.get(0), instanceOf(RexLiteral.class)); // 断言归约结果是RexLiteral类型
      assertThat(((RexLiteral) reducedValues.get(0)).getValue2(), equalTo(10L)); // 断言归约结果的值为10
    });
  }

  /** Reduces several expressions to constants. */
  @Test void testConstant2() { // 测试将多个表达式归约为常量
    // Same as testConstant; 10 -> 10
    checkConstant(10L, // 测试常量10归约为10
        rexBuilder -> rexBuilder.makeExactLiteral(BigDecimal.TEN)); // 创建字面量10的表达式
    // 10 + 1 -> 11
    checkConstant(11L, // 测试10+1归约为11
        rexBuilder -> rexBuilder.makeCall(SqlStdOperatorTable.PLUS, // 创建加法表达式
            rexBuilder.makeExactLiteral(BigDecimal.TEN), // 左操作数10
            rexBuilder.makeExactLiteral(BigDecimal.ONE))); // 右操作数1
    // date 'today' <= date 'today' -> true
    checkConstant(true, rexBuilder -> { // 测试日期比较表达式归约为true
      final DateString d = // 获取当前日期
          DateString.fromCalendarFields(Util.calendar());
      return rexBuilder.makeCall(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, // 创建小于等于表达式
          rexBuilder.makeDateLiteral(d), // 左操作数：今天
          rexBuilder.makeDateLiteral(d)); // 右操作数：今天
    });
    // date 'today' < date 'today' -> false
    checkConstant(false, rexBuilder -> { // 测试日期比较表达式归约为false
      final DateString d = // 获取当前日期
          DateString.fromCalendarFields(Util.calendar());
      return rexBuilder.makeCall(SqlStdOperatorTable.LESS_THAN, // 创建小于表达式
          rexBuilder.makeDateLiteral(d), // 左操作数：今天
          rexBuilder.makeDateLiteral(d)); // 右操作数：今天
    });
  }

  private void checkConstant(final Object operand, // 定义checkConstant私有方法，用于检查常量表达式归约，参数operand是期望的结果值
      final Function<RexBuilder, RexNode> function) { // 参数function是创建Rex表达式的函数
    check((rexBuilder, executor) -> { // 调用check方法，传入lambda表达式接收rexBuilder和executor
      final List<RexNode> reducedValues = new ArrayList<>(); // 创建列表用于存储归约后的表达式
      final RexNode expression = requireNonNull(function.apply(rexBuilder)); // 应用function创建Rex表达式，并确保非空
      executor.reduce(rexBuilder, ImmutableList.of(expression), // 调用executor的reduce方法归约表达式
          reducedValues); // 将归约结果存入reducedValues列表
      assertThat(reducedValues, hasSize(1)); // 断言归约结果列表大小为1
      final RexNode reducedValue = reducedValues.get(0); // 获取归约后的表达式
      assertThat(reducedValue, instanceOf(RexLiteral.class)); // 断言归约结果是RexLiteral类型
      final Matcher<Object> matcher; // 定义匹配器变量
      if (((RexLiteral) reducedValue).getTypeName() == SqlTypeName.TIMESTAMP) { // 如果归约结果是TIMESTAMP类型
        final long current = System.currentTimeMillis(); // 获取当前时间戳
        //noinspection unchecked
        matcher = (Matcher) Matchers.between((long) operand, current); // 使用范围匹配器，因为时间戳会有微小差异
      } else { // 如果不是TIMESTAMP类型
        matcher = equalTo(operand); // 使用相等匹配器
      }
      assertThat(((RexLiteral) reducedValue).getValue2(), matcher); // 断言归约结果的值与期望值匹配
    });
  }

  @Test void testUserFromContext() { // 测试从DataContext获取USER变量
    testContextLiteral(SqlStdOperatorTable.USER, // 测试USER操作符
        DataContext.Variable.USER, "happyCalciteUser"); // 期望值为"happyCalciteUser"
  }

  @Test void testSystemUserFromContext() { // 测试从DataContext获取SYSTEM_USER变量
    testContextLiteral(SqlStdOperatorTable.SYSTEM_USER, // 测试SYSTEM_USER操作符
        DataContext.Variable.SYSTEM_USER, ""); // 期望值为空字符串
  }

  @Test void testTimestampFromContext() { // 测试从DataContext获取CURRENT_TIMESTAMP变量
    // CURRENT_TIMESTAMP actually rounds the value to nearest second
    // and that's why we do currentTimeInMillis / 1000 * 1000
    long val = System.currentTimeMillis() / 1000 * 1000; // 获取当前时间戳并四舍五入到秒
    testContextLiteral(SqlStdOperatorTable.CURRENT_TIMESTAMP, // 测试CURRENT_TIMESTAMP操作符
        DataContext.Variable.CURRENT_TIMESTAMP, val); // 期望值为当前时间戳
  }

  /**
   * Ensures that for a given context operator,
   * the correct value is retrieved from the {@link DataContext}.
   *
   * @param operator The Operator to check
   * @param variable The DataContext variable this operator should be bound to
   * @param value The expected value to retrieve.
   */
  private void testContextLiteral( // 定义testContextLiteral私有方法，用于测试从DataContext获取上下文字面量
      final SqlOperator operator, // 参数operator是要测试的SQL操作符
      final DataContext.Variable variable, // 参数variable是DataContext变量
      final Object value) { // 参数value是期望的值
    Frameworks.withPrepare((cluster, relOptSchema, rootSchema, statement) -> { // 使用Frameworks.withPrepare创建Calcite框架环境
      final RexBuilder rexBuilder = cluster.getRexBuilder(); // 从cluster中获取RexBuilder对象
      final RexExecutorImpl executor = // 创建RexExecutorImpl实例
          new RexExecutorImpl( // 传入DataContext
              DataContexts.of(name -> // 创建DataContext，通过lambda表达式提供变量查找功能
                  name.equals(variable.camelName) ? value // 如果变量名匹配则返回期望值
                      : fail("unknown: " + name))); // 否则抛出失败异常
      try { // 尝试执行测试
        checkConstant(value, builder -> { // 调用checkConstant方法检查常量
          final List<RexNode> output = new ArrayList<>(); // 创建列表用于存储输出
          executor.reduce(rexBuilder, // 调用executor的reduce方法归约表达式
              ImmutableList.of(rexBuilder.makeCall(operator)), output); // 创建操作符调用并归约
          return output.get(0); // 返回归约结果的第一个元素
        });
      } catch (Exception e) { // 捕获异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
      return null; // 返回null
    });
  }

  @Test void testSubstring() { // 测试SUBSTRING函数的归约功能
    check((rexBuilder, executor) -> { // 调用check方法，传入lambda表达式接收rexBuilder和executor
      final List<RexNode> reducedValues = new ArrayList<>(); // 创建列表用于存储归约后的表达式
      final RexLiteral hello = // 创建字符字面量"Hello world!"
          rexBuilder.makeCharLiteral(
              new NlsString("Hello world!", null, null));
      final RexNode plus = // 创建加法表达式1+1=2
          rexBuilder.makeCall(SqlStdOperatorTable.PLUS,
              rexBuilder.makeExactLiteral(BigDecimal.ONE),
              rexBuilder.makeExactLiteral(BigDecimal.ONE));
      RexLiteral four = rexBuilder.makeExactLiteral(BigDecimal.valueOf(4)); // 创建字面量4
      final RexNode substring = // 创建SUBSTRING函数调用，从hello的第2个位置开始截取4个字符
          rexBuilder.makeCall(SqlStdOperatorTable.SUBSTRING,
              hello, plus, four);
      executor.reduce(rexBuilder, ImmutableList.of(substring, plus), // 调用executor的reduce方法归约表达式
          reducedValues); // 将归约结果存入reducedValues列表
      assertThat(reducedValues, hasSize(2)); // 断言归约结果列表大小为2
      assertThat(reducedValues.get(0), instanceOf(RexLiteral.class)); // 断言第一个归约结果是RexLiteral类型
      assertThat(((RexLiteral) reducedValues.get(0)).getValue2(), // 断言第一个归约结果的值
          equalTo("ello")); // substring('Hello world!, 2, 4)的结果是"ello"
      assertThat(reducedValues.get(1), instanceOf(RexLiteral.class)); // 断言第二个归约结果是RexLiteral类型
      assertThat(((RexLiteral) reducedValues.get(1)).getValue2(), // 断言第二个归约结果的值
          equalTo(2L)); // 加法表达式1+1的结果是2
    });
  }

  @Test void testBinarySubstring() { // 测试二进制字符串的SUBSTRING函数归约功能
    check((rexBuilder, executor) -> { // 调用check方法，传入lambda表达式接收rexBuilder和executor
      final List<RexNode> reducedValues = new ArrayList<>(); // 创建列表用于存储归约后的表达式
      // hello world! -> 48656c6c6f20776f726c6421
      final RexLiteral binaryHello = // 创建二进制字面量，将"Hello world!"转换为字节数组
          rexBuilder.makeBinaryLiteral(
              new ByteString("Hello world!".getBytes(UTF_8))); // 使用UTF-8编码转换为字节
      final RexNode plus = // 创建加法表达式1+1=2
          rexBuilder.makeCall(SqlStdOperatorTable.PLUS,
              rexBuilder.makeExactLiteral(BigDecimal.ONE),
              rexBuilder.makeExactLiteral(BigDecimal.ONE));
      RexLiteral four = rexBuilder.makeExactLiteral(BigDecimal.valueOf(4)); // 创建字面量4
      final RexNode substring = // 创建SUBSTRING函数调用，从binaryHello的第2个字节开始截取4个字节
          rexBuilder.makeCall(SqlStdOperatorTable.SUBSTRING,
              binaryHello, plus, four);
      executor.reduce(rexBuilder, ImmutableList.of(substring, plus), // 调用executor的reduce方法归约表达式
          reducedValues); // 将归约结果存入reducedValues列表
      assertThat(reducedValues, hasSize(2)); // 断言归约结果列表大小为2
      assertThat(reducedValues.get(0), instanceOf(RexLiteral.class)); // 断言第一个归约结果是RexLiteral类型
      assertThat(((RexLiteral) reducedValues.get(0)).getValue2(), // 断言第一个归约结果的值
          hasToString("656c6c6f")); // substring('Hello world!, 2, 4)的结果是"656c6c6f"（"ello"的十六进制表示）
      assertThat(reducedValues.get(1), instanceOf(RexLiteral.class)); // 断言第二个归约结果是RexLiteral类型
      assertThat(((RexLiteral) reducedValues.get(1)).getValue2(), // 断言第二个归约结果的值
          equalTo(2L)); // 加法表达式1+1的结果是2
    });
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6775">[CALCITE-6775]
   * ToChar and ToTimestamp PG implementors should use translator's root instead of
   * creating a new root expression</a>. */
  @Test void testToCharPg() { // 测试PostgreSQL风格的TO_CHAR函数，确保使用翻译器的根表达式而不是创建新的根表达式
    check((rexBuilder, executor) -> { // 调用check方法，传入lambda表达式接收rexBuilder和executor
      final List<RexNode> reducedValues = new ArrayList<>(); // 创建列表用于存储归约后的表达式
      // GMT: Wednesday, November 12, 1975 11:00:00 AM
      final TimestampString timestamp = TimestampString.fromMillisSinceEpoch(185022000000L); // 创建时间戳字符串，表示1975年11月12日
      final RexNode toChar = // 创建TO_CHAR_PG函数调用，将时间戳转换为ISO 8601星期几格式
          rexBuilder.makeCall(SqlLibraryOperators.TO_CHAR_PG, // 使用PostgreSQL风格的TO_CHAR操作符
              rexBuilder.makeTimestampLiteral(timestamp, 0), // 时间戳字面量，精度为0
              rexBuilder.makeLiteral("ID")); // 格式字符串"ID"表示ISO 8601星期几（Wednesday = 3）
      executor.reduce(rexBuilder, ImmutableList.of(toChar), reducedValues); // 调用executor的reduce方法归约表达式
      assertThat(reducedValues, hasSize(1)); // 断言归约结果列表大小为1
      assertThat(reducedValues.get(0), instanceOf(RexLiteral.class)); // 断言归约结果是RexLiteral类型
      assertThat(((RexLiteral) reducedValues.get(0)).getValueAs(String.class), equalTo("3")); // 断言归约结果为字符串"3"（星期三）
    });
  }

  @Test void testDeterministic1() { // 测试确定性的表达式判断
    check((rexBuilder, executor) -> { // 调用check方法，传入lambda表达式接收rexBuilder和executor
      final RexNode plus = // 创建加法表达式1+1
          rexBuilder.makeCall(SqlStdOperatorTable.PLUS,
              rexBuilder.makeExactLiteral(BigDecimal.ONE),
              rexBuilder.makeExactLiteral(BigDecimal.ONE));
      assertThat(RexUtil.isDeterministic(plus), equalTo(true)); // 断言加法表达式是确定性的
    });
  }

  @Test void testDeterministic2() { // 测试非确定性的表达式判断
    check((rexBuilder, executor) -> { // 调用check方法，传入lambda表达式接收rexBuilder和executor
      final RexNode plus = // 创建使用PLUS_RANDOM操作符的加法表达式
          rexBuilder.makeCall(PLUS_RANDOM, // PLUS_RANDOM是非确定性操作符
              rexBuilder.makeExactLiteral(BigDecimal.ONE),
              rexBuilder.makeExactLiteral(BigDecimal.ONE));
      assertThat(RexUtil.isDeterministic(plus), equalTo(false)); // 断言表达式是非确定性的
    });
  }

  @Test void testDeterministic3() { // 测试包含非确定性子表达式的表达式判断
    check((rexBuilder, executor) -> { // 调用check方法，传入lambda表达式接收rexBuilder和executor
      final RexNode plus = // 创建加法表达式，其中包含非确定性的子表达式
          rexBuilder.makeCall(SqlStdOperatorTable.PLUS, // 外层加法操作符
              rexBuilder.makeCall(PLUS_RANDOM, // 内层使用PLUS_RANDOM操作符，是非确定性的
                  rexBuilder.makeExactLiteral(BigDecimal.ONE),
                  rexBuilder.makeExactLiteral(BigDecimal.ONE)),
              rexBuilder.makeExactLiteral(BigDecimal.ONE));
      assertThat(RexUtil.isDeterministic(plus), equalTo(false)); // 断言整个表达式是非确定性的，因为包含非确定性子表达式
    });
  }

  private static final SqlBinaryOperator PLUS_RANDOM = // 定义PLUS_RANDOM静态常量，这是一个非确定性的二元加法操作符
      new SqlMonotonicBinaryOperator( // 创建单调二元操作符实例
          "+", // 操作符名称
          SqlKind.PLUS, // 操作符种类为PLUS
          40, // 操作符优先级
          true, // 是否允许null
          ReturnTypes.NULLABLE_SUM, // 返回类型推断规则为可空求和
          InferTypes.FIRST_KNOWN, // 类型推断规则为使用第一个已知类型
          OperandTypes.PLUS_OPERATOR) { // 操作数类型检查规则为加法操作符
        @Override public boolean isDeterministic() { // 重写isDeterministic方法
          return false; // 返回false，表示该操作符是非确定性的
        }
      };

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1009">[CALCITE-1009]
   * SelfPopulatingList is not thread-safe</a>. */
  @Test void testSelfPopulatingList() { // 测试SelfPopulatingList的线程安全性
    final List<Thread> threads = new ArrayList<>(); // 创建线程列表
    //noinspection MismatchedQueryAndUpdateOfCollection
    final List<String> list = new RexSlot.SelfPopulatingList("$", 1); // 创建SelfPopulatingList实例，前缀为"$"，从索引1开始
    final Random random = new Random(); // 创建随机数生成器
    for (int i = 0; i < 10; i++) { // 创建10个线程
      threads.add( // 将线程添加到线程列表
          new Thread(() -> { // 创建新线程
            for (int j = 0; j < 1000; j++) { // 每个线程执行1000次操作
              // Random numbers between 0 and ~1m, smaller values more common
              final int index = random.nextInt(1234567) // 生成随机索引，范围0到1234567
                  >> random.nextInt(16) >> random.nextInt(16); // 通过右移操作使较小的值更常见
              list.get(index); // 获取指定索引的元素，触发自动填充
            }
          }));
    }
    for (Thread runnable : threads) { // 启动所有线程
      runnable.start();
    }
    for (Thread runnable : threads) { // 等待所有线程完成
      try {
        runnable.join(); // 阻塞当前线程直到指定线程完成
      } catch (InterruptedException e) { // 捕获中断异常
        e.printStackTrace(); // 打印异常堆栈
      }
    }
    final int size = list.size(); // 获取列表大小
    for (int i = 0; i < size; i++) { // 遍历列表所有元素
      assertThat(list.get(i), is("$" + i)); // 断言每个元素的正确性
    }
  }

  @Test void testSelfPopulatingList30() { // 测试SelfPopulatingList获取索引30的元素
    //noinspection MismatchedQueryAndUpdateOfCollection
    final List<String> list = new RexSlot.SelfPopulatingList("$", 30); // 创建SelfPopulatingList实例，前缀为"$"，从索引30开始
    final String s = list.get(30); // 获取索引30的元素
    assertThat(s, is("$30")); // 断言元素值为"$30"
  }

  /** Callback for {@link #check}. Test code will typically use {@code builder}
   * to create some expressions, call
   * {@link org.apache.calcite.rex.RexExecutorImpl#reduce} to evaluate them into
   * a list, then check that the results are as expected. */
  interface Action { // 定义Action函数式接口，作为check方法的回调
    void check(RexBuilder rexBuilder, RexExecutorImpl executor); // check方法接收RexBuilder和RexExecutorImpl参数
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5949">[CALCITE-5949]
   * RexExecutable should return unchanged original expressions when it fails</a>.
   */
  @Test void testInvalidExpressionInList() { // 测试当表达式执行失败时，RexExecutable应该返回未改变的原始表达式
    check((rexBuilder, executor) -> { // 调用check方法，传入lambda表达式接收rexBuilder和executor
      final List<RexNode> reducedValues = new ArrayList<>(); // 创建列表用于存储归约后的表达式
      final RelDataTypeFactory typeFactory = rexBuilder.getTypeFactory(); // 获取类型工厂
      final RelDataType integer = // 创建INTEGER类型
          typeFactory.createSqlType(SqlTypeName.INTEGER);
      final RexCall first = // 创建LN（自然对数）函数调用，参数为3
          (RexCall) rexBuilder.makeCall(SqlStdOperatorTable.LN,
          rexBuilder.makeLiteral(3, integer, true));
      // Division by zero causes an exception during evaluation
      final RexCall second = // 创建整数除法函数调用，-2除以0会导致异常
          (RexCall) rexBuilder.makeCall(SqlStdOperatorTable.DIVIDE_INTEGER,
              rexBuilder.makeLiteral(-2, integer, true), // 被除数为-2
              rexBuilder.makeLiteral(0, integer, true)); // 除数为0，会引发异常
      executor.reduce(rexBuilder, ImmutableList.of(first, second), // 调用executor的reduce方法归约表达式
          reducedValues); // 将归约结果存入reducedValues列表
      assertThat(reducedValues, hasSize(2)); // 断言归约结果列表大小为2
      assertThat(reducedValues.get(0), instanceOf(RexCall.class)); // 断言第一个结果是RexCall类型（未归约）
      assertThat(reducedValues.get(1), instanceOf(RexCall.class)); // 断言第二个结果是RexCall类型（未归约）
    });
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6168">[CALCITE-6168]
   * RexExecutor can throw during compilation</a>. */
  @Test void testCompileTimeException() { // 测试RexExecutor在编译期间可能抛出异常的情况
    check((rexBuilder, executor) -> { // 调用check方法，传入lambda表达式接收rexBuilder和executor
      final List<RexNode> reducedValues = new ArrayList<>(); // 创建列表用于存储归约后的表达式
      final RelDataTypeFactory typeFactory = rexBuilder.getTypeFactory(); // 获取类型工厂
      // CAST(200 as TINYINT)
      final RelDataType tinyint = // 创建TINYINT类型，范围是-128到127
          typeFactory.createSqlType(SqlTypeName.TINYINT);
      final RelDataType integer  = // 创建INTEGER类型
          typeFactory.createSqlType(SqlTypeName.INTEGER);
      final RexNode cast = // 创建CAST表达式，将200转换为TINYINT类型，但200超出了TINYINT范围
          rexBuilder.makeCast(tinyint, // 目标类型为TINYINT
              rexBuilder.makeLiteral(200, integer, true)); // 源值为200，类型为INTEGER
      executor.reduce(rexBuilder, ImmutableList.of(cast), // 调用executor的reduce方法归约表达式
          reducedValues); // 将归约结果存入reducedValues列表
      assertThat(reducedValues, hasSize(1)); // 断言归约结果列表大小为1
      assertThat(reducedValues.get(0), instanceOf(RexCall.class)); // 断言结果是RexCall类型（未归约，因为转换失败）
    });
  }
}
