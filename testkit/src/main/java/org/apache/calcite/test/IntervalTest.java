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
package org.apache.calcite.test; // 包声明:org.apache.calcite.test,属于Calcite测试包

/** Test cases for intervals. // 测试用例:用于测试SQL INTERVAL(时间间隔)类型的功能
 *
 * <p>Called, with varying implementations of {@link Fixture}, // 通过Fixture接口的不同实现来调用
 * from both parser and validator test. // 从解析器和验证器测试中调用
 */
public class IntervalTest { // IntervalTest类:专门用于测试SQL INTERVAL时间间隔类型的测试类
  private final Fixture f; // 成员变量f:Fixture接口实例,用于提供测试的绑定和验证功能,final表示不可变

  public IntervalTest(Fixture fixture) { // 构造方法:接收Fixture接口实例作为参数
    this.f = fixture; // 将传入的fixture参数赋值给成员变量f,保存测试装置引用
  }

  /** Runs all tests. */ // 方法作用:运行所有INTERVAL相关的测试用例
  public void testAll() { // testAll方法:主测试方法,按顺序执行所有子测试
    // Tests that should pass both parser and validator // 注释:以下测试用例应该通过解析器和验证器的双重验证
    subTestIntervalYearPositive(); // 调用INTERVAL YEAR正向测试方法
    subTestIntervalYearToMonthPositive(); // 调用INTERVAL YEAR TO MONTH正向测试方法
    subTestIntervalMonthPositive(); // 调用INTERVAL MONTH正向测试方法
    subTestIntervalDayPositive(); // 调用INTERVAL DAY正向测试方法
    subTestIntervalDayToHourPositive(); // 调用INTERVAL DAY TO HOUR正向测试方法
    subTestIntervalDayToMinutePositive(); // 调用INTERVAL DAY TO MINUTE正向测试方法
    subTestIntervalDayToSecondPositive(); // 调用INTERVAL DAY TO SECOND正向测试方法
    subTestIntervalHourPositive(); // 调用INTERVAL HOUR正向测试方法
    subTestIntervalHourToMinutePositive(); // 调用INTERVAL HOUR TO MINUTE正向测试方法
    subTestIntervalHourToSecondPositive(); // 调用INTERVAL HOUR TO SECOND正向测试方法
    subTestIntervalMinutePositive(); // 调用INTERVAL MINUTE正向测试方法
    subTestIntervalMinuteToSecondPositive(); // 调用INTERVAL MINUTE TO SECOND正向测试方法
    subTestIntervalSecondPositive(); // 调用INTERVAL SECOND正向测试方法
    subTestIntervalWeekPositive(); // 调用INTERVAL WEEK正向测试方法
    subTestIntervalQuarterPositive(); // 调用INTERVAL QUARTER正向测试方法
    subTestIntervalPlural(); // 调用INTERVAL复数形式测试方法

    // Tests that should pass parser but fail validator // 注释:以下测试用例应该通过解析器但验证器应该失败(测试错误处理)
    subTestIntervalYearNegative(); // 调用INTERVAL YEAR负向测试方法(测试错误情况)
    subTestIntervalYearToMonthNegative(); // 调用INTERVAL YEAR TO MONTH负向测试方法
    subTestIntervalMonthNegative(); // 调用INTERVAL MONTH负向测试方法
    subTestIntervalDayNegative(); // 调用INTERVAL DAY负向测试方法
    subTestIntervalDayToHourNegative(); // 调用INTERVAL DAY TO HOUR负向测试方法
    subTestIntervalDayToMinuteNegative(); // 调用INTERVAL DAY TO MINUTE负向测试方法
    subTestIntervalDayToSecondNegative(); // 调用INTERVAL DAY TO SECOND负向测试方法
    subTestIntervalHourNegative(); // 调用INTERVAL HOUR负向测试方法
    subTestIntervalHourToMinuteNegative(); // 调用INTERVAL HOUR TO MINUTE负向测试方法
    subTestIntervalHourToSecondNegative(); // 调用INTERVAL HOUR TO SECOND负向测试方法
    subTestIntervalMinuteNegative(); // 调用INTERVAL MINUTE负向测试方法
    subTestIntervalMinuteToSecondNegative(); // 调用INTERVAL MINUTE TO SECOND负向测试方法
    subTestIntervalSecondNegative(); // 调用INTERVAL SECOND负向测试方法

    subTestMisc(); // 调用杂项测试方法
  }

  /**
   * Runs tests for INTERVAL... YEAR that should pass both parser and // 运行INTERVAL YEAR类型的测试,这些测试应该通过解析器和验证器
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalYearPositive() { // 方法作用:测试INTERVAL YEAR类型的正向用例(应该成功的测试)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '1' YEAR") // 表达式:INTERVAL '1' YEAR,表示1年的时间间隔
        .columnType("INTERVAL YEAR NOT NULL"); // 断言:列类型为INTERVAL YEAR NOT NULL
    f.expr("INTERVAL '99' YEAR") // 表达式:INTERVAL '99' YEAR,表示99年的时间间隔
        .columnType("INTERVAL YEAR NOT NULL"); // 断言:列类型为INTERVAL YEAR NOT NULL

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '1' YEAR(2)") // 表达式:INTERVAL '1' YEAR(2),显式指定精度为2
        .columnType("INTERVAL YEAR(2) NOT NULL"); // 断言:列类型为INTERVAL YEAR(2) NOT NULL
    f.expr("INTERVAL '99' YEAR(2)") // 表达式:INTERVAL '99' YEAR(2),显式指定精度为2
        .columnType("INTERVAL YEAR(2) NOT NULL"); // 断言:列类型为INTERVAL YEAR(2) NOT NULL

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647' YEAR(10)") // 表达式:最大整数值,精度为10
        .columnType("INTERVAL YEAR(10) NOT NULL"); // 断言:列类型为INTERVAL YEAR(10) NOT NULL

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0' YEAR(1)") // 表达式:0年,精度为1
        .columnType("INTERVAL YEAR(1) NOT NULL"); // 断言:列类型为INTERVAL YEAR(1) NOT NULL

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '1234' YEAR(4)") // 表达式:1234年,精度为4
        .columnType("INTERVAL YEAR(4) NOT NULL"); // 断言:列类型为INTERVAL YEAR(4) NOT NULL

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '+1' YEAR") // 表达式:字面量带正号+1
        .columnType("INTERVAL YEAR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '-1' YEAR") // 表达式:字面量带负号-1
        .columnType("INTERVAL YEAR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'1' YEAR") // 表达式:INTERVAL前加单目运算符+,字面量为'1'
        .assertParse("INTERVAL '1' YEAR") // 断言:解析结果应该是INTERVAL '1' YEAR(简化了符号)
        .columnType("INTERVAL YEAR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+1' YEAR") // 表达式:INTERVAL前加单目运算符+,字面量为'+1'
        .assertParse("INTERVAL '+1' YEAR") // 断言:解析结果保持为INTERVAL '+1' YEAR
        .columnType("INTERVAL YEAR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-1' YEAR") // 表达式:INTERVAL前加单目运算符+,字面量为'-1'
        .assertParse("INTERVAL '-1' YEAR") // 断言:解析结果保持为INTERVAL '-1' YEAR
        .columnType("INTERVAL YEAR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'1' YEAR") // 表达式:INTERVAL前加单目运算符-,字面量为'1'
        .columnType("INTERVAL YEAR NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'+1' YEAR") // 表达式:INTERVAL前加单目运算符-,字面量为'+1'
        .columnType("INTERVAL YEAR NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'-1' YEAR") // 表达式:INTERVAL前加单目运算符-,字面量为'-1'
        .columnType("INTERVAL YEAR NOT NULL"); // 断言:列类型正确,负负得正,实际值为1
  }

  /**
   * Runs tests for INTERVAL... YEAR TO MONTH that should pass both parser and // 运行INTERVAL YEAR TO MONTH类型的测试,应该通过解析器和验证器
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalYearToMonthPositive() { // 方法作用:测试INTERVAL YEAR TO MONTH类型的正向用例(年-月时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '1-2' YEAR TO MONTH") // 表达式:1年2月,格式为'年-月'
        .columnType("INTERVAL YEAR TO MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99-11' YEAR TO MONTH") // 表达式:99年11月
        .columnType("INTERVAL YEAR TO MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99-0' YEAR TO MONTH") // 表达式:99年0月
        .columnType("INTERVAL YEAR TO MONTH NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '1-2' YEAR(2) TO MONTH") // 表达式:显式指定YEAR精度为2
        .columnType("INTERVAL YEAR(2) TO MONTH NOT NULL"); // 断言:列类型为INTERVAL YEAR(2) TO MONTH NOT NULL
    f.expr("INTERVAL '99-11' YEAR(2) TO MONTH") // 表达式:显式指定YEAR精度为2
        .columnType("INTERVAL YEAR(2) TO MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99-0' YEAR(2) TO MONTH") // 表达式:显式指定YEAR精度为2
        .columnType("INTERVAL YEAR(2) TO MONTH NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647-11' YEAR(10) TO MONTH") // 表达式:最大年值,11月
        .columnType("INTERVAL YEAR(10) TO MONTH NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0-0' YEAR(1) TO MONTH") // 表达式:0年0月,精度为1
        .columnType("INTERVAL YEAR(1) TO MONTH NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '2006-2' YEAR(4) TO MONTH") // 表达式:2006年2月,精度为4
        .columnType("INTERVAL YEAR(4) TO MONTH NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '-1-2' YEAR TO MONTH") // 表达式:字面量带负号-1年2月
        .columnType("INTERVAL YEAR TO MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '+1-2' YEAR TO MONTH") // 表达式:字面量带正号+1年2月
        .columnType("INTERVAL YEAR TO MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'1-2' YEAR TO MONTH") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '1-2' YEAR TO MONTH") // 断言:解析结果简化为INTERVAL '1-2' YEAR TO MONTH
        .columnType("INTERVAL YEAR TO MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-1-2' YEAR TO MONTH") // 表达式:INTERVAL前加单目运算符+,字面量为'-1-2'
        .assertParse("INTERVAL '-1-2' YEAR TO MONTH") // 断言:解析结果保持原样
        .columnType("INTERVAL YEAR TO MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+1-2' YEAR TO MONTH") // 表达式:INTERVAL前加单目运算符+,字面量为'+1-2'
        .assertParse("INTERVAL '+1-2' YEAR TO MONTH") // 断言:解析结果保持原样
        .columnType("INTERVAL YEAR TO MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'1-2' YEAR TO MONTH") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL YEAR TO MONTH NOT NULL"); // 断言:列类型正确,实际值为-1年-2月
    f.expr("INTERVAL -'-1-2' YEAR TO MONTH") // 表达式:INTERVAL前加单目运算符-,字面量为'-1-2'
        .columnType("INTERVAL YEAR TO MONTH NOT NULL"); // 断言:列类型正确,负负得正
    f.expr("INTERVAL -'+1-2' YEAR TO MONTH") // 表达式:INTERVAL前加单目运算符-,字面量为'+1-2'
        .columnType("INTERVAL YEAR TO MONTH NOT NULL"); // 断言:列类型正确,实际值为-1年-2月
  }

  /**
   * Runs tests for INTERVAL... MONTH that should pass both parser and // 运行INTERVAL MONTH类型的测试,应该通过解析器和验证器
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalMonthPositive() { // 方法作用:测试INTERVAL MONTH类型的正向用例(月时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '1' MONTH") // 表达式:1个月
        .columnType("INTERVAL MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' MONTH") // 表达式:99个月
        .columnType("INTERVAL MONTH NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '1' MONTH(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL MONTH(2) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' MONTH(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL MONTH(2) NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647' MONTH(10)") // 表达式:最大整数值,精度为10
        .columnType("INTERVAL MONTH(10) NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0' MONTH(1)") // 表达式:0个月,精度为1
        .columnType("INTERVAL MONTH(1) NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '1234' MONTH(4)") // 表达式:1234个月,精度为4
        .columnType("INTERVAL MONTH(4) NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '+1' MONTH") // 表达式:字面量带正号+1
        .columnType("INTERVAL MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '-1' MONTH") // 表达式:字面量带负号-1
        .columnType("INTERVAL MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'1' MONTH") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '1' MONTH") // 断言:解析结果简化
        .columnType("INTERVAL MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+1' MONTH") // 表达式:INTERVAL前加单目运算符+,字面量为'+1'
        .assertParse("INTERVAL '+1' MONTH") // 断言:解析结果保持
        .columnType("INTERVAL MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-1' MONTH") // 表达式:INTERVAL前加单目运算符+,字面量为'-1'
        .assertParse("INTERVAL '-1' MONTH") // 断言:解析结果保持
        .columnType("INTERVAL MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'1' MONTH") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL MONTH NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'+1' MONTH") // 表达式:INTERVAL前加单目运算符-,字面量为'+1'
        .columnType("INTERVAL MONTH NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'-1' MONTH") // 表达式:INTERVAL前加单目运算符-,字面量为'-1'
        .columnType("INTERVAL MONTH NOT NULL"); // 断言:列类型正确,负负得正
  }

  /**
   * Runs tests for INTERVAL... DAY that should pass both parser and // 运行INTERVAL DAY类型的测试,应该通过解析器和验证器
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalDayPositive() { // 方法作用:测试INTERVAL DAY类型的正向用例(天时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '1' DAY") // 表达式:1天
        .columnType("INTERVAL DAY NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' DAY") // 表达式:99天
        .columnType("INTERVAL DAY NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '1' DAY(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL DAY(2) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' DAY(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL DAY(2) NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647' DAY(10)") // 表达式:最大整数值,精度为10
        .columnType("INTERVAL DAY(10) NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0' DAY(1)") // 表达式:0天,精度为1
        .columnType("INTERVAL DAY(1) NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '1234' DAY(4)") // 表达式:1234天,精度为4
        .columnType("INTERVAL DAY(4) NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '+1' DAY") // 表达式:字面量带正号+1
        .columnType("INTERVAL DAY NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '-1' DAY") // 表达式:字面量带负号-1
        .columnType("INTERVAL DAY NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'1' DAY") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '1' DAY") // 断言:解析结果简化
        .columnType("INTERVAL DAY NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+1' DAY") // 表达式:INTERVAL前加单目运算符+,字面量为'+1'
        .assertParse("INTERVAL '+1' DAY") // 断言:解析结果保持
        .columnType("INTERVAL DAY NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-1' DAY") // 表达式:INTERVAL前加单目运算符+,字面量为'-1'
        .assertParse("INTERVAL '-1' DAY") // 断言:解析结果保持
        .columnType("INTERVAL DAY NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'1' DAY") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL DAY NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'+1' DAY") // 表达式:INTERVAL前加单目运算符-,字面量为'+1'
        .columnType("INTERVAL DAY NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'-1' DAY") // 表达式:INTERVAL前加单目运算符-,字面量为'-1'
        .columnType("INTERVAL DAY NOT NULL"); // 断言:列类型正确,负负得正
  }

  public void subTestIntervalDayToHourPositive() { // 方法作用:测试INTERVAL DAY TO HOUR类型的正向用例(天-小时时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '1 2' DAY TO HOUR") // 表达式:1天2小时,格式为'天 小时'
        .columnType("INTERVAL DAY TO HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 23' DAY TO HOUR") // 表达式:99天23小时
        .columnType("INTERVAL DAY TO HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 0' DAY TO HOUR") // 表达式:99天0小时
        .columnType("INTERVAL DAY TO HOUR NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '1 2' DAY(2) TO HOUR") // 表达式:显式指定DAY精度为2
        .columnType("INTERVAL DAY(2) TO HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 23' DAY(2) TO HOUR") // 表达式:显式指定DAY精度为2
        .columnType("INTERVAL DAY(2) TO HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 0' DAY(2) TO HOUR") // 表达式:显式指定DAY精度为2
        .columnType("INTERVAL DAY(2) TO HOUR NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647 23' DAY(10) TO HOUR") // 表达式:最大天值,23小时
        .columnType("INTERVAL DAY(10) TO HOUR NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0 0' DAY(1) TO HOUR") // 表达式:0天0小时,精度为1
        .columnType("INTERVAL DAY(1) TO HOUR NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '2345 2' DAY(4) TO HOUR") // 表达式:2345天2小时,精度为4
        .columnType("INTERVAL DAY(4) TO HOUR NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '-1 2' DAY TO HOUR") // 表达式:字面量带负号-1天2小时
        .columnType("INTERVAL DAY TO HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '+1 2' DAY TO HOUR") // 表达式:字面量带正号+1天2小时
        .columnType("INTERVAL DAY TO HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'1 2' DAY TO HOUR") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '1 2' DAY TO HOUR") // 断言:解析结果简化
        .columnType("INTERVAL DAY TO HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-1 2' DAY TO HOUR") // 表达式:INTERVAL前加单目运算符+,字面量为'-1 2'
        .assertParse("INTERVAL '-1 2' DAY TO HOUR") // 断言:解析结果保持
        .columnType("INTERVAL DAY TO HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+1 2' DAY TO HOUR") // 表达式:INTERVAL前加单目运算符+,字面量为'+1 2'
        .assertParse("INTERVAL '+1 2' DAY TO HOUR") // 断言:解析结果保持
        .columnType("INTERVAL DAY TO HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'1 2' DAY TO HOUR") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL DAY TO HOUR NOT NULL"); // 断言:列类型正确,实际值为-1天-2小时
    f.expr("INTERVAL -'-1 2' DAY TO HOUR") // 表达式:INTERVAL前加单目运算符-,字面量为'-1 2'
        .columnType("INTERVAL DAY TO HOUR NOT NULL"); // 断言:列类型正确,负负得正
    f.expr("INTERVAL -'+1 2' DAY TO HOUR") // 表达式:INTERVAL前加单目运算符-,字面量为'+1 2'
        .columnType("INTERVAL DAY TO HOUR NOT NULL"); // 断言:列类型正确,实际值为-1天-2小时
  }

  /**
   * Runs tests for INTERVAL... DAY TO MINUTE that should pass both parser and // 运行INTERVAL DAY TO MINUTE类型的测试,应该通过解析器和验证器
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalDayToMinutePositive() { // 方法作用:测试INTERVAL DAY TO MINUTE类型的正向用例(天-小时-分钟时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '1 2:3' DAY TO MINUTE") // 表达式:1天2小时3分钟,格式为'天 小时:分钟'
        .columnType("INTERVAL DAY TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 23:59' DAY TO MINUTE") // 表达式:99天23小时59分钟
        .columnType("INTERVAL DAY TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 0:0' DAY TO MINUTE") // 表达式:99天0小时0分钟
        .columnType("INTERVAL DAY TO MINUTE NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '1 2:3' DAY(2) TO MINUTE") // 表达式:显式指定DAY精度为2
        .columnType("INTERVAL DAY(2) TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 23:59' DAY(2) TO MINUTE") // 表达式:显式指定DAY精度为2
        .columnType("INTERVAL DAY(2) TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 0:0' DAY(2) TO MINUTE") // 表达式:显式指定DAY精度为2
        .columnType("INTERVAL DAY(2) TO MINUTE NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647 23:59' DAY(10) TO MINUTE") // 表达式:最大天值,23小时59分钟
        .columnType("INTERVAL DAY(10) TO MINUTE NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0 0:0' DAY(1) TO MINUTE") // 表达式:0天0小时0分钟,精度为1
        .columnType("INTERVAL DAY(1) TO MINUTE NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '2345 6:7' DAY(4) TO MINUTE") // 表达式:2345天6小时7分钟,精度为4
        .columnType("INTERVAL DAY(4) TO MINUTE NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '-1 2:3' DAY TO MINUTE") // 表达式:字面量带负号-1天2小时3分钟
        .columnType("INTERVAL DAY TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '+1 2:3' DAY TO MINUTE") // 表达式:字面量带正号+1天2小时3分钟
        .assertParse("INTERVAL '+1 2:3' DAY TO MINUTE") // 断言:解析结果保持
        .columnType("INTERVAL DAY TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'1 2:3' DAY TO MINUTE") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '1 2:3' DAY TO MINUTE") // 断言:解析结果简化
        .columnType("INTERVAL DAY TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-1 2:3' DAY TO MINUTE") // 表达式:INTERVAL前加单目运算符+,字面量为'-1 2:3'
        .assertParse("INTERVAL '-1 2:3' DAY TO MINUTE") // 断言:解析结果保持
        .columnType("INTERVAL DAY TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+1 2:3' DAY TO MINUTE") // 表达式:INTERVAL前加单目运算符+,字面量为'+1 2:3'
        .assertParse("INTERVAL '+1 2:3' DAY TO MINUTE") // 断言:解析结果保持
        .columnType("INTERVAL DAY TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'1 2:3' DAY TO MINUTE") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL DAY TO MINUTE NOT NULL"); // 断言:列类型正确,实际值为-1天-2小时-3分钟
    f.expr("INTERVAL -'-1 2:3' DAY TO MINUTE") // 表达式:INTERVAL前加单目运算符-,字面量为'-1 2:3'
        .columnType("INTERVAL DAY TO MINUTE NOT NULL"); // 断言:列类型正确,负负得正
    f.expr("INTERVAL -'+1 2:3' DAY TO MINUTE") // 表达式:INTERVAL前加单目运算符-,字面量为'+1 2:3'
        .columnType("INTERVAL DAY TO MINUTE NOT NULL"); // 断言:列类型正确,实际值为-1天-2小时-3分钟
  }

  /**
   * Runs tests for INTERVAL... DAY TO SECOND that should pass both parser and // 运行INTERVAL DAY TO SECOND类型的测试,应该通过解析器和验证器
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalDayToSecondPositive() { // 方法作用:测试INTERVAL DAY TO SECOND类型的正向用例(天-小时-分钟-秒时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '1 2:3:4' DAY TO SECOND") // 表达式:1天2小时3分钟4秒,格式为'天 小时:分钟:秒'
        .columnType("INTERVAL DAY TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 23:59:59' DAY TO SECOND") // 表达式:99天23小时59分钟59秒
        .columnType("INTERVAL DAY TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 0:0:0' DAY TO SECOND") // 表达式:99天0小时0分钟0秒
        .columnType("INTERVAL DAY TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 23:59:59.999999' DAY TO SECOND") // 表达式:99天23小时59分钟59.999999秒(带小数秒)
        .columnType("INTERVAL DAY TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 0:0:0.0' DAY TO SECOND") // 表达式:99天0小时0分钟0.0秒(带小数秒)
        .columnType("INTERVAL DAY TO SECOND NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '1 2:3:4' DAY(2) TO SECOND") // 表达式:显式指定DAY精度为2
        .columnType("INTERVAL DAY(2) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 23:59:59' DAY(2) TO SECOND") // 表达式:显式指定DAY精度为2
        .columnType("INTERVAL DAY(2) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 0:0:0' DAY(2) TO SECOND") // 表达式:显式指定DAY精度为2
        .columnType("INTERVAL DAY(2) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 23:59:59.999999' DAY TO SECOND(6)") // 表达式:显式指定秒的小数精度为6
        .columnType("INTERVAL DAY TO SECOND(6) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99 0:0:0.0' DAY TO SECOND(6)") // 表达式:显式指定秒的小数精度为6
        .columnType("INTERVAL DAY TO SECOND(6) NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647 23:59:59' DAY(10) TO SECOND") // 表达式:最大天值,23小时59分钟59秒
        .columnType("INTERVAL DAY(10) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '2147483647 23:59:59.999999999' DAY(10) TO SECOND(9)") // 表达式:最大天值,最大秒的小数精度9
        .columnType("INTERVAL DAY(10) TO SECOND(9) NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0 0:0:0' DAY(1) TO SECOND") // 表达式:0天0小时0分钟0秒,精度为1
        .columnType("INTERVAL DAY(1) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '0 0:0:0.0' DAY(1) TO SECOND(1)") // 表达式:0天0小时0分钟0.0秒,秒的小数精度为1
        .columnType("INTERVAL DAY(1) TO SECOND(1) NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '2345 6:7:8' DAY(4) TO SECOND") // 表达式:2345天6小时7分钟8秒,精度为4
        .columnType("INTERVAL DAY(4) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '2345 6:7:8.9012' DAY(4) TO SECOND(4)") // 表达式:2345天6小时7分钟8.9012秒,秒的小数精度为4
        .columnType("INTERVAL DAY(4) TO SECOND(4) NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '-1 2:3:4' DAY TO SECOND") // 表达式:字面量带负号-1天2小时3分钟4秒
        .columnType("INTERVAL DAY TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '+1 2:3:4' DAY TO SECOND") // 表达式:字面量带正号+1天2小时3分钟4秒
        .columnType("INTERVAL DAY TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'1 2:3:4' DAY TO SECOND") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '1 2:3:4' DAY TO SECOND") // 断言:解析结果简化
        .columnType("INTERVAL DAY TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-1 2:3:4' DAY TO SECOND") // 表达式:INTERVAL前加单目运算符+,字面量为'-1 2:3:4'
        .assertParse("INTERVAL '-1 2:3:4' DAY TO SECOND") // 断言:解析结果保持
        .columnType("INTERVAL DAY TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+1 2:3:4' DAY TO SECOND") // 表达式:INTERVAL前加单目运算符+,字面量为'+1 2:3:4'
        .assertParse("INTERVAL '+1 2:3:4' DAY TO SECOND") // 断言:解析结果保持
        .columnType("INTERVAL DAY TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'1 2:3:4' DAY TO SECOND") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL DAY TO SECOND NOT NULL"); // 断言:列类型正确,实际值为-1天-2小时-3分钟-4秒
    f.expr("INTERVAL -'-1 2:3:4' DAY TO SECOND") // 表达式:INTERVAL前加单目运算符-,字面量为'-1 2:3:4'
        .columnType("INTERVAL DAY TO SECOND NOT NULL"); // 断言:列类型正确,负负得正
    f.expr("INTERVAL -'+1 2:3:4' DAY TO SECOND") // 表达式:INTERVAL前加单目运算符-,字面量为'+1 2:3:4'
        .columnType("INTERVAL DAY TO SECOND NOT NULL"); // 断言:列类型正确,实际值为-1天-2小时-3分钟-4秒
  }

  /**
   * Runs tests for INTERVAL... HOUR that should pass both parser and // 运行INTERVAL HOUR类型的测试,应该通过解析器和验证器
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalHourPositive() { // 方法作用:测试INTERVAL HOUR类型的正向用例(小时时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '1' HOUR") // 表达式:1小时
        .columnType("INTERVAL HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' HOUR") // 表达式:99小时
        .columnType("INTERVAL HOUR NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '1' HOUR(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL HOUR(2) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' HOUR(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL HOUR(2) NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647' HOUR(10)") // 表达式:最大整数值,精度为10
        .columnType("INTERVAL HOUR(10) NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0' HOUR(1)") // 表达式:0小时,精度为1
        .columnType("INTERVAL HOUR(1) NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '1234' HOUR(4)") // 表达式:1234小时,精度为4
        .columnType("INTERVAL HOUR(4) NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '+1' HOUR") // 表达式:字面量带正号+1
        .columnType("INTERVAL HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '-1' HOUR") // 表达式:字面量带负号-1
        .columnType("INTERVAL HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'1' HOUR") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '1' HOUR") // 断言:解析结果简化
        .columnType("INTERVAL HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+1' HOUR") // 表达式:INTERVAL前加单目运算符+,字面量为'+1'
        .assertParse("INTERVAL '+1' HOUR") // 断言:解析结果保持
        .columnType("INTERVAL HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-1' HOUR") // 表达式:INTERVAL前加单目运算符+,字面量为'-1'
        .assertParse("INTERVAL '-1' HOUR") // 断言:解析结果保持
        .columnType("INTERVAL HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'1' HOUR") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL HOUR NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'+1' HOUR") // 表达式:INTERVAL前加单目运算符-,字面量为'+1'
        .columnType("INTERVAL HOUR NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'-1' HOUR") // 表达式:INTERVAL前加单目运算符-,字面量为'-1'
        .columnType("INTERVAL HOUR NOT NULL"); // 断言:列类型正确,负负得正
  }

  /**
   * Runs tests for INTERVAL... HOUR TO MINUTE that should pass both parser // 运行INTERVAL HOUR TO MINUTE类型的测试,应该通过解析器和验证器
   * and validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalHourToMinutePositive() { // 方法作用:测试INTERVAL HOUR TO MINUTE类型的正向用例(小时-分钟时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '2:3' HOUR TO MINUTE") // 表达式:2小时3分钟,格式为'小时:分钟'
        .columnType("INTERVAL HOUR TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '23:59' HOUR TO MINUTE") // 表达式:23小时59分钟
        .columnType("INTERVAL HOUR TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:0' HOUR TO MINUTE") // 表达式:99小时0分钟
        .columnType("INTERVAL HOUR TO MINUTE NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '2:3' HOUR(2) TO MINUTE") // 表达式:显式指定HOUR精度为2
        .columnType("INTERVAL HOUR(2) TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '23:59' HOUR(2) TO MINUTE") // 表达式:显式指定HOUR精度为2
        .columnType("INTERVAL HOUR(2) TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:0' HOUR(2) TO MINUTE") // 表达式:显式指定HOUR精度为2
        .columnType("INTERVAL HOUR(2) TO MINUTE NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647:59' HOUR(10) TO MINUTE") // 表达式:最大小时值,59分钟
        .columnType("INTERVAL HOUR(10) TO MINUTE NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0:0' HOUR(1) TO MINUTE") // 表达式:0小时0分钟,精度为1
        .columnType("INTERVAL HOUR(1) TO MINUTE NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '2345:7' HOUR(4) TO MINUTE") // 表达式:2345小时7分钟,精度为4
        .columnType("INTERVAL HOUR(4) TO MINUTE NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '-1:3' HOUR TO MINUTE") // 表达式:字面量带负号-1小时3分钟
        .columnType("INTERVAL HOUR TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '+1:3' HOUR TO MINUTE") // 表达式:字面量带正号+1小时3分钟
        .columnType("INTERVAL HOUR TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'2:3' HOUR TO MINUTE") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '2:3' HOUR TO MINUTE") // 断言:解析结果简化
        .columnType("INTERVAL HOUR TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-2:3' HOUR TO MINUTE") // 表达式:INTERVAL前加单目运算符+,字面量为'-2:3'
        .assertParse("INTERVAL '-2:3' HOUR TO MINUTE") // 断言:解析结果保持
        .columnType("INTERVAL HOUR TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+2:3' HOUR TO MINUTE") // 表达式:INTERVAL前加单目运算符+,字面量为'+2:3'
        .assertParse("INTERVAL '+2:3' HOUR TO MINUTE") // 断言:解析结果保持
        .columnType("INTERVAL HOUR TO MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'2:3' HOUR TO MINUTE") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL HOUR TO MINUTE NOT NULL"); // 断言:列类型正确,实际值为-2小时-3分钟
    f.expr("INTERVAL -'-2:3' HOUR TO MINUTE") // 表达式:INTERVAL前加单目运算符-,字面量为'-2:3'
        .columnType("INTERVAL HOUR TO MINUTE NOT NULL"); // 断言:列类型正确,负负得正
    f.expr("INTERVAL -'+2:3' HOUR TO MINUTE") // 表达式:INTERVAL前加单目运算符-,字面量为'+2:3'
        .columnType("INTERVAL HOUR TO MINUTE NOT NULL"); // 断言:列类型正确,实际值为-2小时-3分钟
  }

  /**
   * Runs tests for INTERVAL... HOUR TO SECOND that should pass both parser // 运行INTERVAL HOUR TO SECOND类型的测试,应该通过解析器和验证器
   * and validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalHourToSecondPositive() { // 方法作用:测试INTERVAL HOUR TO SECOND类型的正向用例(小时-分钟-秒时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '2:3:4' HOUR TO SECOND") // 表达式:2小时3分钟4秒,格式为'小时:分钟:秒'
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '23:59:59' HOUR TO SECOND") // 表达式:23小时59分钟59秒
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:0:0' HOUR TO SECOND") // 表达式:99小时0分钟0秒
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '23:59:59.999999' HOUR TO SECOND") // 表达式:23小时59分钟59.999999秒(带小数秒)
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:0:0.0' HOUR TO SECOND") // 表达式:99小时0分钟0.0秒(带小数秒)
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '2:3:4' HOUR(2) TO SECOND") // 表达式:显式指定HOUR精度为2
        .columnType("INTERVAL HOUR(2) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:59:59' HOUR(2) TO SECOND") // 表达式:显式指定HOUR精度为2
        .columnType("INTERVAL HOUR(2) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:0:0' HOUR(2) TO SECOND") // 表达式:显式指定HOUR精度为2
        .columnType("INTERVAL HOUR(2) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:59:59.999999' HOUR TO SECOND(6)") // 表达式:显式指定秒的小数精度为6
        .columnType("INTERVAL HOUR TO SECOND(6) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:0:0.0' HOUR TO SECOND(6)") // 表达式:显式指定秒的小数精度为6
        .columnType("INTERVAL HOUR TO SECOND(6) NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647:59:59' HOUR(10) TO SECOND") // 表达式:最大小时值,59分钟59秒
        .columnType("INTERVAL HOUR(10) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '2147483647:59:59.999999999' HOUR(10) TO SECOND(9)") // 表达式:最大小时值,最大秒的小数精度9
        .columnType("INTERVAL HOUR(10) TO SECOND(9) NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0:0:0' HOUR(1) TO SECOND") // 表达式:0小时0分钟0秒,精度为1
        .columnType("INTERVAL HOUR(1) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '0:0:0.0' HOUR(1) TO SECOND(1)") // 表达式:0小时0分钟0.0秒,秒的小数精度为1
        .columnType("INTERVAL HOUR(1) TO SECOND(1) NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '2345:7:8' HOUR(4) TO SECOND") // 表达式:2345小时7分钟8秒,精度为4
        .columnType("INTERVAL HOUR(4) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '2345:7:8.9012' HOUR(4) TO SECOND(4)") // 表达式:2345小时7分钟8.9012秒,秒的小数精度为4
        .columnType("INTERVAL HOUR(4) TO SECOND(4) NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '-2:3:4' HOUR TO SECOND") // 表达式:字面量带负号-2小时3分钟4秒
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '+2:3:4' HOUR TO SECOND") // 表达式:字面量带正号+2小时3分钟4秒
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'2:3:4' HOUR TO SECOND") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '2:3:4' HOUR TO SECOND") // 断言:解析结果简化
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-2:3:4' HOUR TO SECOND") // 表达式:INTERVAL前加单目运算符+,字面量为'-2:3:4'
        .assertParse("INTERVAL '-2:3:4' HOUR TO SECOND") // 断言:解析结果保持
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+2:3:4' HOUR TO SECOND") // 表达式:INTERVAL前加单目运算符+,字面量为'+2:3:4'
        .assertParse("INTERVAL '+2:3:4' HOUR TO SECOND") // 断言:解析结果保持
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'2:3:4' HOUR TO SECOND") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:列类型正确,实际值为-2小时-3分钟-4秒
    f.expr("INTERVAL -'-2:3:4' HOUR TO SECOND") // 表达式:INTERVAL前加单目运算符-,字面量为'-2:3:4'
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:列类型正确,负负得正
    f.expr("INTERVAL -'+2:3:4' HOUR TO SECOND") // 表达式:INTERVAL前加单目运算符-,字面量为'+2:3:4'
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:列类型正确,实际值为-2小时-3分钟-4秒
  }

  /**
   * Runs tests for INTERVAL... MINUTE that should pass both parser and // 运行INTERVAL MINUTE类型的测试,应该通过解析器和验证器
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalMinutePositive() { // 方法作用:测试INTERVAL MINUTE类型的正向用例(分钟时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '1' MINUTE") // 表达式:1分钟
        .columnType("INTERVAL MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' MINUTE") // 表达式:99分钟
        .columnType("INTERVAL MINUTE NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '1' MINUTE(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL MINUTE(2) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' MINUTE(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL MINUTE(2) NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647' MINUTE(10)") // 表达式:最大整数值,精度为10
        .columnType("INTERVAL MINUTE(10) NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0' MINUTE(1)") // 表达式:0分钟,精度为1
        .columnType("INTERVAL MINUTE(1) NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '1234' MINUTE(4)") // 表达式:1234分钟,精度为4
        .columnType("INTERVAL MINUTE(4) NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '+1' MINUTE") // 表达式:字面量带正号+1
        .columnType("INTERVAL MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '-1' MINUTE") // 表达式:字面量带负号-1
        .columnType("INTERVAL MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'1' MINUTE") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '1' MINUTE") // 断言:解析结果简化
        .columnType("INTERVAL MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+1' MINUTE") // 表达式:INTERVAL前加单目运算符+,字面量为'+1'
        .assertParse("INTERVAL '+1' MINUTE") // 断言:解析结果保持
        .columnType("INTERVAL MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-1' MINUTE") // 表达式:INTERVAL前加单目运算符+,字面量为'-1'
        .assertParse("INTERVAL '-1' MINUTE") // 断言:解析结果保持
        .columnType("INTERVAL MINUTE NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'1' MINUTE") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL MINUTE NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'+1' MINUTE") // 表达式:INTERVAL前加单目运算符-,字面量为'+1'
        .columnType("INTERVAL MINUTE NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'-1' MINUTE") // 表达式:INTERVAL前加单目运算符-,字面量为'-1'
        .columnType("INTERVAL MINUTE NOT NULL"); // 断言:列类型正确,负负得正
  }

  /**
   * Runs tests for INTERVAL... MINUTE TO SECOND that should pass both parser // 运行INTERVAL MINUTE TO SECOND类型的测试,应该通过解析器和验证器
   * and validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalMinuteToSecondPositive() { // 方法作用:测试INTERVAL MINUTE TO SECOND类型的正向用例(分钟-秒时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '2:4' MINUTE TO SECOND") // 表达式:2分钟4秒,格式为'分钟:秒'
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '59:59' MINUTE TO SECOND") // 表达式:59分钟59秒
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:0' MINUTE TO SECOND") // 表达式:99分钟0秒
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '59:59.999999' MINUTE TO SECOND") // 表达式:59分钟59.999999秒(带小数秒)
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:0.0' MINUTE TO SECOND") // 表达式:99分钟0.0秒(带小数秒)
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '2:4' MINUTE(2) TO SECOND") // 表达式:显式指定MINUTE精度为2
        .columnType("INTERVAL MINUTE(2) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:59' MINUTE(2) TO SECOND") // 表达式:显式指定MINUTE精度为2
        .columnType("INTERVAL MINUTE(2) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:0' MINUTE(2) TO SECOND") // 表达式:显式指定MINUTE精度为2
        .columnType("INTERVAL MINUTE(2) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:59.999999' MINUTE TO SECOND(6)") // 表达式:显式指定秒的小数精度为6
        .columnType("INTERVAL MINUTE TO SECOND(6) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99:0.0' MINUTE TO SECOND(6)") // 表达式:显式指定秒的小数精度为6
        .columnType("INTERVAL MINUTE TO SECOND(6) NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647:59' MINUTE(10) TO SECOND") // 表达式:最大分钟值,59秒
        .columnType("INTERVAL MINUTE(10) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '2147483647:59.999999999' MINUTE(10) TO SECOND(9)") // 表达式:最大分钟值,最大秒的小数精度9
        .columnType("INTERVAL MINUTE(10) TO SECOND(9) NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0:0' MINUTE(1) TO SECOND") // 表达式:0分钟0秒,精度为1
        .columnType("INTERVAL MINUTE(1) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '0:0.0' MINUTE(1) TO SECOND(1)") // 表达式:0分钟0.0秒,秒的小数精度为1
        .columnType("INTERVAL MINUTE(1) TO SECOND(1) NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '2345:8' MINUTE(4) TO SECOND") // 表达式:2345分钟8秒,精度为4
        .columnType("INTERVAL MINUTE(4) TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '2345:7.8901' MINUTE(4) TO SECOND(4)") // 表达式:2345分钟7.8901秒,秒的小数精度为4
        .columnType("INTERVAL MINUTE(4) TO SECOND(4) NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '-3:4' MINUTE TO SECOND") // 表达式:字面量带负号-3分钟4秒
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '+3:4' MINUTE TO SECOND") // 表达式:字面量带正号+3分钟4秒
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'3:4' MINUTE TO SECOND") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '3:4' MINUTE TO SECOND") // 断言:解析结果简化
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-3:4' MINUTE TO SECOND") // 表达式:INTERVAL前加单目运算符+,字面量为'-3:4'
        .assertParse("INTERVAL '-3:4' MINUTE TO SECOND") // 断言:解析结果保持
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+3:4' MINUTE TO SECOND") // 表达式:INTERVAL前加单目运算符+,字面量为'+3:4'
        .assertParse("INTERVAL '+3:4' MINUTE TO SECOND") // 断言:解析结果保持
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'3:4' MINUTE TO SECOND") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:列类型正确,实际值为-3分钟-4秒
    f.expr("INTERVAL -'-3:4' MINUTE TO SECOND") // 表达式:INTERVAL前加单目运算符-,字面量为'-3:4'
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:列类型正确,负负得正
    f.expr("INTERVAL -'+3:4' MINUTE TO SECOND") // 表达式:INTERVAL前加单目运算符-,字面量为'+3:4'
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:列类型正确,实际值为-3分钟-4秒
  }

  /**
   * Runs tests for INTERVAL... SECOND that should pass both parser and // 运行INTERVAL SECOND类型的测试,应该通过解析器和验证器
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalSecondPositive() { // 方法作用:测试INTERVAL SECOND类型的正向用例(秒时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '1' SECOND") // 表达式:1秒
        .columnType("INTERVAL SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' SECOND") // 表达式:99秒
        .columnType("INTERVAL SECOND NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '1' SECOND(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL SECOND(2) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' SECOND(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL SECOND(2) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '1' SECOND(2, 6)") // 表达式:显式指定秒精度为2,小数精度为6
        .columnType("INTERVAL SECOND(2, 6) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' SECOND(2, 6)") // 表达式:显式指定秒精度为2,小数精度为6
        .columnType("INTERVAL SECOND(2, 6) NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647' SECOND(10)") // 表达式:最大整数值,精度为10
        .columnType("INTERVAL SECOND(10) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '2147483647.999999999' SECOND(10, 9)") // 表达式:最大整数值.最大小数精度9
        .columnType("INTERVAL SECOND(10, 9) NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0' SECOND(1)") // 表达式:0秒,精度为1
        .columnType("INTERVAL SECOND(1) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '0.0' SECOND(1, 1)") // 表达式:0.0秒,秒精度为1,小数精度为1
        .columnType("INTERVAL SECOND(1, 1) NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '1234' SECOND(4)") // 表达式:1234秒,精度为4
        .columnType("INTERVAL SECOND(4) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '1234.56789' SECOND(4, 5)") // 表达式:1234.56789秒,秒精度为4,小数精度为5
        .columnType("INTERVAL SECOND(4, 5) NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '+1' SECOND") // 表达式:字面量带正号+1
        .columnType("INTERVAL SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '-1' SECOND") // 表达式:字面量带负号-1
        .columnType("INTERVAL SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'1' SECOND") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '1' SECOND") // 断言:解析结果简化
        .columnType("INTERVAL SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+1' SECOND") // 表达式:INTERVAL前加单目运算符+,字面量为'+1'
        .assertParse("INTERVAL '+1' SECOND") // 断言:解析结果保持
        .columnType("INTERVAL SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-1' SECOND") // 表达式:INTERVAL前加单目运算符+,字面量为'-1'
        .assertParse("INTERVAL '-1' SECOND") // 断言:解析结果保持
        .columnType("INTERVAL SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'1' SECOND") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL SECOND NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'+1' SECOND") // 表达式:INTERVAL前加单目运算符-,字面量为'+1'
        .columnType("INTERVAL SECOND NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'-1' SECOND") // 表达式:INTERVAL前加单目运算符-,字面量为'-1'
        .columnType("INTERVAL SECOND NOT NULL"); // 断言:列类型正确,负负得正
  }

  /**
   * Runs tests for INTERVAL... YEAR that should pass parser but fail // 运行INTERVAL YEAR类型的测试,应该通过解析器但验证器应该失败(测试错误处理)
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXNegative() tests. // 其他12个subTestIntervalXXXNegative()测试方法中
   */
  public void subTestIntervalYearNegative() { // 方法作用:测试INTERVAL YEAR类型的负向用例(测试各种错误情况)
    // Qualifier - field mismatches // 注释:测试限定符与字段格式不匹配的情况
    f.wholeExpr("INTERVAL '-' YEAR") // 表达式:只有负号,没有数字
        .fails("Illegal interval literal format '-' for INTERVAL YEAR.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1-2' YEAR") // 表达式:使用'1-2'格式,但YEAR不需要月部分
        .fails("Illegal interval literal format '1-2' for INTERVAL YEAR.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1.2' YEAR") // 表达式:使用小数格式,但YEAR不支持小数
        .fails("Illegal interval literal format '1.2' for INTERVAL YEAR.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 2' YEAR") // 表达式:使用空格分隔两个数字,但YEAR只需要一个字段
        .fails("Illegal interval literal format '1 2' for INTERVAL YEAR.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1-2' YEAR(2)") // 表达式:使用'1-2'格式,显式指定精度为2
        .fails("Illegal interval literal format '1-2' for INTERVAL YEAR\\(2\\)"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL 'bogus text' YEAR") // 表达式:使用无效的文本
        .fails("Illegal interval literal format 'bogus text' for INTERVAL YEAR.*"); // 断言:应该失败,格式错误

    // negative field values // 注释:测试负数字段值(双重负号)
    f.wholeExpr("INTERVAL '--1' YEAR") // 表达式:使用双重负号
        .fails("Illegal interval literal format '--1' for INTERVAL YEAR.*"); // 断言:应该失败,格式错误

    // Field value out of range // 注释:测试字段值超出范围的情况
    //  (default, explicit default, alt, neg alt, max, neg max) // 包括:默认精度、显式默认精度、其他精度、负值其他精度、最大值、负最大值
    f.wholeExpr("INTERVAL '100' YEAR") // 表达式:100年,默认精度为2,但100需要3位
        .columnType("INTERVAL YEAR NOT NULL"); // 断言:解析器接受,但验证器应该拒绝(精度不足)
    f.wholeExpr("INTERVAL '100' YEAR(2)") // 表达式:100年,显式指定精度为2
        .fails("Interval field value 100 exceeds precision of YEAR\\(2\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1000' YEAR(3)") // 表达式:1000年,精度为3
        .fails("Interval field value 1,000 exceeds precision of YEAR\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-1000' YEAR(3)") // 表达式:-1000年,精度为3
        .fails("Interval field value -1,000 exceeds precision of YEAR\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '2147483648' YEAR(10)") // 表达式:2147483648年,精度为10,超出int范围
        .fails("Interval field value 2,147,483,648 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "YEAR\\(10\\) field.*");
    f.wholeExpr("INTERVAL '-2147483648' YEAR(10)") // 表达式:-2147483648年,精度为10,超出int范围
        .fails("Interval field value -2,147,483,648 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "YEAR\\(10\\) field");

    // precision > maximum // 注释:测试精度大于最大值的情况
    f.expr("INTERVAL '1' ^YEAR(11)^") // 表达式:精度为11,超过最大值10
        .fails("Interval leading field precision '11' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL YEAR\\(11\\)");

    // precision < minimum allowed) // 注释:测试精度小于最小允许值的情况
    // note: parser will catch negative values, here we // 注意:解析器会捕获负值,这里我们
    // just need to check for 0 // 只需要检查0的情况
    f.expr("INTERVAL '0' ^YEAR(0)^") // 表达式:精度为0,小于最小值1
        .fails("Interval leading field precision '0' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL YEAR\\(0\\)");
  }

  /**
   * Runs tests for INTERVAL... YEAR TO MONTH that should pass parser but fail // 运行INTERVAL YEAR TO MONTH类型的测试,应该通过解析器但验证器应该失败(测试错误处理)
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXNegative() tests. // 其他12个subTestIntervalXXXNegative()测试方法中
   */
  public void subTestIntervalYearToMonthNegative() { // 方法作用:测试INTERVAL YEAR TO MONTH类型的负向用例(测试各种错误情况)
    // Qualifier - field mismatches // 注释:测试限定符与字段格式不匹配的情况
    f.wholeExpr("INTERVAL '-' YEAR TO MONTH") // 表达式:只有负号,没有数字
        .fails("Illegal interval literal format '-' for INTERVAL YEAR TO MONTH"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1' YEAR TO MONTH") // 表达式:只有年,缺少月部分
        .fails("Illegal interval literal format '1' for INTERVAL YEAR TO MONTH"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:2' YEAR TO MONTH") // 表达式:使用冒号分隔,应该使用连字符
        .fails("Illegal interval literal format '1:2' for INTERVAL YEAR TO MONTH"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1.2' YEAR TO MONTH") // 表达式:使用小数点分隔,应该使用连字符
        .fails("Illegal interval literal format '1.2' for INTERVAL YEAR TO MONTH"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 2' YEAR TO MONTH") // 表达式:使用空格分隔,应该使用连字符
        .fails("Illegal interval literal format '1 2' for INTERVAL YEAR TO MONTH"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:2' YEAR(2) TO MONTH") // 表达式:使用冒号分隔,显式指定精度为2
        .fails("Illegal interval literal format '1:2' for " // 断言:应该失败,格式错误
            + "INTERVAL YEAR\\(2\\) TO MONTH");
    f.wholeExpr("INTERVAL 'bogus text' YEAR TO MONTH") // 表达式:使用无效的文本
        .fails("Illegal interval literal format 'bogus text' for " // 断言:应该失败,格式错误
            + "INTERVAL YEAR TO MONTH");

    // negative field values // 注释:测试负数字段值(双重负号)
    f.wholeExpr("INTERVAL '--1-2' YEAR TO MONTH") // 表达式:年部分使用双重负号
        .fails("Illegal interval literal format '--1-2' for " // 断言:应该失败,格式错误
            + "INTERVAL YEAR TO MONTH");
    f.wholeExpr("INTERVAL '1--2' YEAR TO MONTH") // 表达式:月部分使用双重负号
        .fails("Illegal interval literal format '1--2' for " // 断言:应该失败,格式错误
            + "INTERVAL YEAR TO MONTH");

    // Field value out of range // 注释:测试字段值超出范围的情况
    //  (default, explicit default, alt, neg alt, max, neg max) // 包括:默认精度、显式默认精度、其他精度、负值其他精度、最大值、负最大值
    //  plus >max value for mid/end fields // 以及中间/结束字段的值大于最大值的情况
    f.wholeExpr("INTERVAL '100-0' YEAR TO MONTH") // 表达式:100年0月,默认精度为2
        .columnType("INTERVAL YEAR TO MONTH NOT NULL"); // 断言:解析器接受,但验证器应该拒绝(精度不足)
    f.wholeExpr("INTERVAL '100-0' YEAR(2) TO MONTH") // 表达式:100年0月,显式指定精度为2
        .fails("Interval field value 100 exceeds precision of YEAR\\(2\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1000-0' YEAR(3) TO MONTH") // 表达式:1000年0月,精度为3
        .fails("Interval field value 1,000 exceeds precision of YEAR\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-1000-0' YEAR(3) TO MONTH") // 表达式:-1000年0月,精度为3
        .fails("Interval field value -1,000 exceeds precision of YEAR\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '2147483648-0' YEAR(10) TO MONTH") // 表达式:2147483648年0月,精度为10,超出int范围
        .fails("Interval field value 2,147,483,648 exceeds precision of YEAR\\(10\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-2147483648-0' YEAR(10) TO MONTH") // 表达式:-2147483648年0月,精度为10,超出int范围
        .fails("Interval field value -2,147,483,648 exceeds precision of YEAR\\(10\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1-12' YEAR TO MONTH") // 表达式:1年12月,月值超出有效范围(0-11)
        .fails("Illegal interval literal format '1-12' for INTERVAL YEAR TO MONTH.*"); // 断言:应该失败,月值超出范围

    // precision > maximum // 注释:测试精度大于最大值的情况
    f.expr("INTERVAL '1-1' ^YEAR(11) TO MONTH^") // 表达式:精度为11,超过最大值10
        .fails("Interval leading field precision '11' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL YEAR\\(11\\) TO MONTH");

    // precision < minimum allowed) // 注释:测试精度小于最小允许值的情况
    // note: parser will catch negative values, here we // 注意:解析器会捕获负值,这里我们
    // just need to check for 0 // 只需要检查0的情况
    f.expr("INTERVAL '0-0' ^YEAR(0) TO MONTH^") // 表达式:精度为0,小于最小值1
        .fails("Interval leading field precision '0' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL YEAR\\(0\\) TO MONTH");
  }

  /**
   * Runs tests for INTERVAL... WEEK that should pass both parser and // 运行INTERVAL WEEK类型的测试,应该通过解析器和验证器
   * validator. A substantially identical set of tests exists in // 验证器。在SqlValidatorTest中存在基本相同的测试集
   * SqlValidatorTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalWeekPositive() { // 方法作用:测试INTERVAL WEEK类型的正向用例(周时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '1' WEEK") // 表达式:1周
        .columnType("INTERVAL WEEK NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' WEEK") // 表达式:99周
        .columnType("INTERVAL WEEK NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '1' WEEK(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL WEEK(2) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' WEEK(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL WEEK(2) NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647' WEEK(10)") // 表达式:最大整数值,精度为10
        .columnType("INTERVAL WEEK(10) NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0' WEEK(1)") // 表达式:0周,精度为1
        .columnType("INTERVAL WEEK(1) NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '1234' WEEK(4)") // 表达式:1234周,精度为4
        .columnType("INTERVAL WEEK(4) NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '+1' WEEK") // 表达式:字面量带正号+1
        .columnType("INTERVAL WEEK NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '-1' WEEK") // 表达式:字面量带负号-1
        .columnType("INTERVAL WEEK NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'1' WEEK") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '1' WEEK") // 断言:解析结果简化
        .columnType("INTERVAL WEEK NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+1' WEEK") // 表达式:INTERVAL前加单目运算符+,字面量为'+1'
        .assertParse("INTERVAL '+1' WEEK") // 断言:解析结果保持
        .columnType("INTERVAL WEEK NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-1' WEEK") // 表达式:INTERVAL前加单目运算符+,字面量为'-1'
        .assertParse("INTERVAL '-1' WEEK") // 断言:解析结果保持
        .columnType("INTERVAL WEEK NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'1' WEEK") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL WEEK NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'+1' WEEK") // 表达式:INTERVAL前加单目运算符-,字面量为'+1'
        .columnType("INTERVAL WEEK NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'-1' WEEK") // 表达式:INTERVAL前加单目运算符-,字面量为'-1'
        .columnType("INTERVAL WEEK NOT NULL"); // 断言:列类型正确,负负得正
  }

  /**
   * Runs tests for INTERVAL... QUARTER that should pass both parser and // 运行INTERVAL QUARTER类型的测试,应该通过解析器和验证器
   * validator. A substantially identical set of tests exists in // 验证器。在SqlValidatorTest中存在基本相同的测试集
   * SqlValidatorTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXPositive() tests. // 其他12个subTestIntervalXXXPositive()测试方法中
   */
  public void subTestIntervalQuarterPositive() { // 方法作用:测试INTERVAL QUARTER类型的正向用例(季度时间间隔)
    // default precision // 注释:测试默认精度(不指定精度时使用默认值2)
    f.expr("INTERVAL '1' QUARTER") // 表达式:1季度
        .columnType("INTERVAL QUARTER NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' QUARTER") // 表达式:99季度
        .columnType("INTERVAL QUARTER NOT NULL"); // 断言:列类型正确

    // explicit precision equal to default // 注释:测试显式指定精度等于默认值2的情况
    f.expr("INTERVAL '1' QUARTER(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL QUARTER(2) NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '99' QUARTER(2)") // 表达式:显式指定精度为2
        .columnType("INTERVAL QUARTER(2) NOT NULL"); // 断言:列类型正确

    // max precision // 注释:测试最大精度(10位)
    f.expr("INTERVAL '2147483647' QUARTER(10)") // 表达式:最大整数值,精度为10
        .columnType("INTERVAL QUARTER(10) NOT NULL"); // 断言:列类型正确

    // min precision // 注释:测试最小精度(1位)
    f.expr("INTERVAL '0' QUARTER(1)") // 表达式:0季度,精度为1
        .columnType("INTERVAL QUARTER(1) NOT NULL"); // 断言:列类型正确

    // alternate precision // 注释:测试其他精度值(4位)
    f.expr("INTERVAL '1234' QUARTER(4)") // 表达式:1234季度,精度为4
        .columnType("INTERVAL QUARTER(4) NOT NULL"); // 断言:列类型正确

    // sign // 注释:测试正负号的各种组合
    f.expr("INTERVAL '+1' QUARTER") // 表达式:字面量带正号+1
        .columnType("INTERVAL QUARTER NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '-1' QUARTER") // 表达式:字面量带负号-1
        .columnType("INTERVAL QUARTER NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'1' QUARTER") // 表达式:INTERVAL前加单目运算符+
        .assertParse("INTERVAL '1' QUARTER") // 断言:解析结果简化
        .columnType("INTERVAL QUARTER NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'+1' QUARTER") // 表达式:INTERVAL前加单目运算符+,字面量为'+1'
        .assertParse("INTERVAL '+1' QUARTER") // 断言:解析结果保持
        .columnType("INTERVAL QUARTER NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL +'-1' QUARTER") // 表达式:INTERVAL前加单目运算符+,字面量为'-1'
        .assertParse("INTERVAL '-1' QUARTER") // 断言:解析结果保持
        .columnType("INTERVAL QUARTER NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL -'1' QUARTER") // 表达式:INTERVAL前加单目运算符-
        .columnType("INTERVAL QUARTER NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'+1' QUARTER") // 表达式:INTERVAL前加单目运算符-,字面量为'+1'
        .columnType("INTERVAL QUARTER NOT NULL"); // 断言:列类型正确,实际值为-1
    f.expr("INTERVAL -'-1' QUARTER") // 表达式:INTERVAL前加单目运算符-,字面量为'-1'
        .columnType("INTERVAL QUARTER NOT NULL"); // 断言:列类型正确,负负得正
  }

  public void subTestIntervalPlural() { // 方法作用:测试INTERVAL时间间隔单位的复数形式(支持单数和复数两种形式)
    f.expr("INTERVAL '+2' SECONDS") // 表达式:使用复数形式SECONDS(秒)
        .assertParse("INTERVAL '+2' SECOND") // 断言:解析结果转换为单数形式SECOND
        .columnType("INTERVAL SECOND NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '+2' HOURS") // 表达式:使用复数形式HOURS(小时)
        .assertParse("INTERVAL '+2' HOUR") // 断言:解析结果转换为单数形式HOUR
        .columnType("INTERVAL HOUR NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '+2' DAYS") // 表达式:使用复数形式DAYS(天)
        .assertParse("INTERVAL '+2' DAY") // 断言:解析结果转换为单数形式DAY
        .columnType("INTERVAL DAY NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '+2' WEEKS") // 表达式:使用复数形式WEEKS(周)
        .assertParse("INTERVAL '+2' WEEK") // 断言:解析结果转换为单数形式WEEK
        .columnType("INTERVAL WEEK NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '+2' QUARTERS") // 表达式:使用复数形式QUARTERS(季度)
        .assertParse("INTERVAL '+2' QUARTER") // 断言:解析结果转换为单数形式QUARTER
        .columnType("INTERVAL QUARTER NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '+2' MONTHS") // 表达式:使用复数形式MONTHS(月)
        .assertParse("INTERVAL '+2' MONTH") // 断言:解析结果转换为单数形式MONTH
        .columnType("INTERVAL MONTH NOT NULL"); // 断言:列类型正确
    f.expr("INTERVAL '+2' YEARS") // 表达式:使用复数形式YEARS(年)
        .assertParse("INTERVAL '+2' YEAR") // 断言:解析结果转换为单数形式YEAR
        .columnType("INTERVAL YEAR NOT NULL"); // 断言:列类型正确
  }

  /**
   * Runs tests for INTERVAL... MONTH that should pass parser but fail // 运行INTERVAL MONTH类型的测试,应该通过解析器但验证器应该失败(测试错误处理)
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXNegative() tests. // 其他12个subTestIntervalXXXNegative()测试方法中
   */
  public void subTestIntervalMonthNegative() { // 方法作用:测试INTERVAL MONTH类型的负向用例(测试各种错误情况)
    // Qualifier - field mismatches // 注释:测试限定符与字段格式不匹配的情况
    f.wholeExpr("INTERVAL '-' MONTH") // 表达式:只有负号,没有数字
        .fails("Illegal interval literal format '-' for INTERVAL MONTH.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1-2' MONTH") // 表达式:使用'1-2'格式,但MONTH只需要一个字段
        .fails("Illegal interval literal format '1-2' for INTERVAL MONTH.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1.2' MONTH") // 表达式:使用小数格式,但MONTH不支持小数
        .fails("Illegal interval literal format '1.2' for INTERVAL MONTH.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 2' MONTH") // 表达式:使用空格分隔两个数字,但MONTH只需要一个字段
        .fails("Illegal interval literal format '1 2' for INTERVAL MONTH.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1-2' MONTH(2)") // 表达式:使用'1-2'格式,显式指定精度为2
        .fails("Illegal interval literal format '1-2' for INTERVAL MONTH\\(2\\)"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL 'bogus text' MONTH") // 表达式:使用无效的文本
        .fails("Illegal interval literal format 'bogus text' for INTERVAL MONTH.*"); // 断言:应该失败,格式错误

    // negative field values // 注释:测试负数字段值(双重负号)
    f.wholeExpr("INTERVAL '--1' MONTH") // 表达式:使用双重负号
        .fails("Illegal interval literal format '--1' for INTERVAL MONTH.*"); // 断言:应该失败,格式错误

    // Field value out of range // 注释:测试字段值超出范围的情况
    //  (default, explicit default, alt, neg alt, max, neg max) // 包括:默认精度、显式默认精度、其他精度、负值其他精度、最大值、负最大值
    f.wholeExpr("INTERVAL '100' MONTH") // 表达式:100月,默认精度为2,但100需要3位
        .columnType("INTERVAL MONTH NOT NULL"); // 断言:解析器接受,但验证器应该拒绝(精度不足)
    f.wholeExpr("INTERVAL '100' MONTH(2)") // 表达式:100月,显式指定精度为2
        .fails("Interval field value 100 exceeds precision of MONTH\\(2\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1000' MONTH(3)") // 表达式:1000月,精度为3
        .fails("Interval field value 1,000 exceeds precision of MONTH\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-1000' MONTH(3)") // 表达式:-1000月,精度为3
        .fails("Interval field value -1,000 exceeds precision of MONTH\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '2147483648' MONTH(10)") // 表达式:2147483648月,精度为10,超出int范围
        .fails("Interval field value 2,147,483,648 exceeds precision of MONTH\\(10\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-2147483648' MONTH(10)") // 表达式:-2147483648月,精度为10,超出int范围
        .fails("Interval field value -2,147,483,648 exceeds precision of MONTH\\(10\\) field.*"); // 断言:应该失败,值超出精度范围

    // precision > maximum // 注释:测试精度大于最大值的情况
    f.expr("INTERVAL '1' ^MONTH(11)^") // 表达式:精度为11,超过最大值10
        .fails("Interval leading field precision '11' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL MONTH\\(11\\)");

    // precision < minimum allowed) // 注释:测试精度小于最小允许值的情况
    // note: parser will catch negative values, here we // 注意:解析器会捕获负值,这里我们
    // just need to check for 0 // 只需要检查0的情况
    f.expr("INTERVAL '0' ^MONTH(0)^") // 表达式:精度为0,小于最小值1
        .fails("Interval leading field precision '0' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL MONTH\\(0\\)");
  }

  /**
   * Runs tests for INTERVAL... DAY that should pass parser but fail // 运行INTERVAL DAY类型的测试,应该通过解析器但验证器应该失败(测试错误处理)
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXNegative() tests. // 其他12个subTestIntervalXXXNegative()测试方法中
   */
  public void subTestIntervalDayNegative() { // 方法作用:测试INTERVAL DAY类型的负向用例(测试各种错误情况)
    // Qualifier - field mismatches // 注释:测试限定符与字段格式不匹配的情况
    f.wholeExpr("INTERVAL '-' DAY") // 表达式:只有负号,没有数字
        .fails("Illegal interval literal format '-' for INTERVAL DAY.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1-2' DAY") // 表达式:使用'1-2'格式,但DAY只需要一个字段
        .fails("Illegal interval literal format '1-2' for INTERVAL DAY.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1.2' DAY") // 表达式:使用小数格式,但DAY不支持小数
        .fails("Illegal interval literal format '1.2' for INTERVAL DAY.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 2' DAY") // 表达式:使用空格分隔两个数字,但DAY只需要一个字段
        .fails("Illegal interval literal format '1 2' for INTERVAL DAY.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:2' DAY") // 表达式:使用冒号分隔,但DAY只需要一个字段
        .fails("Illegal interval literal format '1:2' for INTERVAL DAY.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1-2' DAY(2)") // 表达式:使用'1-2'格式,显式指定精度为2
        .fails("Illegal interval literal format '1-2' for INTERVAL DAY\\(2\\)"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL 'bogus text' DAY") // 表达式:使用无效的文本
        .fails("Illegal interval literal format 'bogus text' for INTERVAL DAY.*"); // 断言:应该失败,格式错误

    // negative field values // 注释:测试负数字段值(双重负号)
    f.wholeExpr("INTERVAL '--1' DAY") // 表达式:使用双重负号
        .fails("Illegal interval literal format '--1' for INTERVAL DAY.*"); // 断言:应该失败,格式错误

    // Field value out of range // 注释:测试字段值超出范围的情况
    //  (default, explicit default, alt, neg alt, max, neg max) // 包括:默认精度、显式默认精度、其他精度、负值其他精度、最大值、负最大值
    f.wholeExpr("INTERVAL '100' DAY") // 表达式:100天,默认精度为2,但100需要3位
        .columnType("INTERVAL DAY NOT NULL"); // 断言:解析器接受,但验证器应该拒绝(精度不足)
    f.wholeExpr("INTERVAL '100' DAY(2)") // 表达式:100天,显式指定精度为2
        .fails("Interval field value 100 exceeds precision of DAY\\(2\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1000' DAY(3)") // 表达式:1000天,精度为3
        .fails("Interval field value 1,000 exceeds precision of DAY\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-1000' DAY(3)") // 表达式:-1000天,精度为3
        .fails("Interval field value -1,000 exceeds precision of DAY\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '2147483648' DAY(10)") // 表达式:2147483648天,精度为10,超出int范围
        .fails("Interval field value 2,147,483,648 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "DAY\\(10\\) field.*");
    f.wholeExpr("INTERVAL '-2147483648' DAY(10)") // 表达式:-2147483648天,精度为10,超出int范围
        .fails("Interval field value -2,147,483,648 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "DAY\\(10\\) field.*");

    // precision > maximum // 注释:测试精度大于最大值的情况
    f.expr("INTERVAL '1' ^DAY(11)^") // 表达式:精度为11,超过最大值10
        .fails("Interval leading field precision '11' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL DAY\\(11\\)");

    // precision < minimum allowed) // 注释:测试精度小于最小允许值的情况
    // note: parser will catch negative values, here we // 注意:解析器会捕获负值,这里我们
    // just need to check for 0 // 只需要检查0的情况
    f.expr("INTERVAL '0' ^DAY(0)^") // 表达式:精度为0,小于最小值1
        .fails("Interval leading field precision '0' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL DAY\\(0\\)");
  }

  /**
   * Runs tests for INTERVAL... DAY TO HOUR that should pass parser but fail // 运行INTERVAL DAY TO HOUR类型的测试,应该通过解析器但验证器应该失败(测试错误处理)
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXNegative() tests. // 其他12个subTestIntervalXXXNegative()测试方法中
   */
  public void subTestIntervalDayToHourNegative() { // 方法作用:测试INTERVAL DAY TO HOUR类型的负向用例(测试各种错误情况)
    // Qualifier - field mismatches // 注释:测试限定符与字段格式不匹配的情况
    f.wholeExpr("INTERVAL '-' DAY TO HOUR") // 表达式:只有负号,没有数字
        .fails("Illegal interval literal format '-' for INTERVAL DAY TO HOUR"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1' DAY TO HOUR") // 表达式:只有天,缺少小时部分
        .fails("Illegal interval literal format '1' for INTERVAL DAY TO HOUR"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:2' DAY TO HOUR") // 表达式:使用冒号分隔,应该使用空格
        .fails("Illegal interval literal format '1:2' for INTERVAL DAY TO HOUR"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1.2' DAY TO HOUR") // 表达式:使用小数点分隔,应该使用空格
        .fails("Illegal interval literal format '1.2' for INTERVAL DAY TO HOUR"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 x' DAY TO HOUR") // 表达式:小时部分使用无效字符
        .fails("Illegal interval literal format '1 x' for INTERVAL DAY TO HOUR"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL ' ' DAY TO HOUR") // 表达式:只有空格
        .fails("Illegal interval literal format ' ' for INTERVAL DAY TO HOUR"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:2' DAY(2) TO HOUR") // 表达式:使用冒号分隔,显式指定精度为2
        .fails("Illegal interval literal format '1:2' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY\\(2\\) TO HOUR");
    f.wholeExpr("INTERVAL 'bogus text' DAY TO HOUR") // 表达式:使用无效的文本
        .fails("Illegal interval literal format 'bogus text' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO HOUR");

    // negative field values // 注释:测试负数字段值(双重负号)
    f.wholeExpr("INTERVAL '--1 1' DAY TO HOUR") // 表达式:天部分使用双重负号
        .fails("Illegal interval literal format '--1 1' for INTERVAL DAY TO HOUR"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 -1' DAY TO HOUR") // 表达式:小时部分使用负号(不允许负小时)
        .fails("Illegal interval literal format '1 -1' for INTERVAL DAY TO HOUR"); // 断言:应该失败,格式错误

    // Field value out of range // 注释:测试字段值超出范围的情况
    //  (default, explicit default, alt, neg alt, max, neg max) // 包括:默认精度、显式默认精度、其他精度、负值其他精度、最大值、负最大值
    //  plus >max value for mid/end fields // 以及中间/结束字段的值大于最大值的情况
    f.wholeExpr("INTERVAL '100 0' DAY TO HOUR") // 表达式:100天0小时,默认精度为2
        .columnType("INTERVAL DAY TO HOUR NOT NULL"); // 断言:解析器接受,但验证器应该拒绝(精度不足)
    f.wholeExpr("INTERVAL '100 0' DAY(2) TO HOUR") // 表达式:100天0小时,显式指定精度为2
        .fails("Interval field value 100 exceeds precision of DAY\\(2\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1000 0' DAY(3) TO HOUR") // 表达式:1000天0小时,精度为3
        .fails("Interval field value 1,000 exceeds precision of DAY\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-1000 0' DAY(3) TO HOUR") // 表达式:-1000天0小时,精度为3
        .fails("Interval field value -1,000 exceeds precision of DAY\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '2147483648 0' DAY(10) TO HOUR") // 表达式:2147483648天0小时,精度为10,超出int范围
        .fails("Interval field value 2,147,483,648 exceeds precision of DAY\\(10\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-2147483648 0' DAY(10) TO HOUR") // 表达式:-2147483648天0小时,精度为10,超出int范围
        .fails("Interval field value -2,147,483,648 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "DAY\\(10\\) field.*");
    f.wholeExpr("INTERVAL '1 24' DAY TO HOUR") // 表达式:1天24小时,小时值超出有效范围(0-23)
        .fails("Illegal interval literal format '1 24' for INTERVAL DAY TO HOUR.*"); // 断言:应该失败,小时值超出范围

    // precision > maximum // 注释:测试精度大于最大值的情况
    f.expr("INTERVAL '1 1' ^DAY(11) TO HOUR^") // 表达式:精度为11,超过最大值10
        .fails("Interval leading field precision '11' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL DAY\\(11\\) TO HOUR");

    // precision < minimum allowed) // 注释:测试精度小于最小允许值的情况
    // note: parser will catch negative values, here we // 注意:解析器会捕获负值,这里我们
    // just need to check for 0 // 只需要检查0的情况
    f.expr("INTERVAL '0 0' ^DAY(0) TO HOUR^") // 表达式:精度为0,小于最小值1
        .fails("Interval leading field precision '0' out of range for INTERVAL DAY\\(0\\) TO HOUR"); // 断言:应该失败,精度超出范围
  }

  /**
   * Runs tests for INTERVAL... DAY TO MINUTE that should pass parser but fail // 运行INTERVAL DAY TO MINUTE类型的测试,应该通过解析器但验证器应该失败(测试错误处理)
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXNegative() tests. // 其他12个subTestIntervalXXXNegative()测试方法中
   */
  public void subTestIntervalDayToMinuteNegative() { // 方法作用:测试INTERVAL DAY TO MINUTE类型的负向用例(测试各种错误情况)
    // Qualifier - field mismatches // 注释:测试限定符与字段格式不匹配的情况
    f.wholeExpr("INTERVAL ' :' DAY TO MINUTE") // 表达式:只有空格和冒号
        .fails("Illegal interval literal format ' :' for INTERVAL DAY TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1' DAY TO MINUTE") // 表达式:只有天,缺少小时和分钟部分
        .fails("Illegal interval literal format '1' for INTERVAL DAY TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 2' DAY TO MINUTE") // 表达式:只有天和小时,缺少分钟部分
        .fails("Illegal interval literal format '1 2' for INTERVAL DAY TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:2' DAY TO MINUTE") // 表达式:使用冒号分隔天和小时,应该使用空格
        .fails("Illegal interval literal format '1:2' for INTERVAL DAY TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1.2' DAY TO MINUTE") // 表达式:使用小数点分隔,应该使用空格和冒号
        .fails("Illegal interval literal format '1.2' for INTERVAL DAY TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL 'x 1:1' DAY TO MINUTE") // 表达式:天部分使用无效字符
        .fails("Illegal interval literal format 'x 1:1' for INTERVAL DAY TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 x:1' DAY TO MINUTE") // 表达式:小时部分使用无效字符
        .fails("Illegal interval literal format '1 x:1' for INTERVAL DAY TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 1:x' DAY TO MINUTE") // 表达式:分钟部分使用无效字符
        .fails("Illegal interval literal format '1 1:x' for INTERVAL DAY TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 1:2:3' DAY TO MINUTE") // 表达式:包含秒部分,不应该有秒
        .fails("Illegal interval literal format '1 1:2:3' for INTERVAL DAY TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 1:1:1.2' DAY TO MINUTE") // 表达式:包含秒部分,不应该有秒
        .fails("Illegal interval literal format '1 1:1:1.2' for INTERVAL DAY TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 1:2:3' DAY(2) TO MINUTE") // 表达式:包含秒部分,显式指定精度为2
        .fails("Illegal interval literal format '1 1:2:3' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY\\(2\\) TO MINUTE");
    f.wholeExpr("INTERVAL '1 1' DAY(2) TO MINUTE") // 表达式:缺少分钟部分,显式指定精度为2
        .fails("Illegal interval literal format '1 1' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY\\(2\\) TO MINUTE");
    f.wholeExpr("INTERVAL 'bogus text' DAY TO MINUTE") // 表达式:使用无效的文本
        .fails("Illegal interval literal format 'bogus text' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO MINUTE");

    // negative field values // 注释:测试负数字段值(双重负号或负号)
    f.wholeExpr("INTERVAL '--1 1:1' DAY TO MINUTE") // 表达式:天部分使用双重负号
        .fails("Illegal interval literal format '--1 1:1' for INTERVAL DAY TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 -1:1' DAY TO MINUTE") // 表达式:小时部分使用负号(不允许负小时)
        .fails("Illegal interval literal format '1 -1:1' for INTERVAL DAY TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 1:-1' DAY TO MINUTE") // 表达式:分钟部分使用负号(不允许负分钟)
        .fails("Illegal interval literal format '1 1:-1' for INTERVAL DAY TO MINUTE"); // 断言:应该失败,格式错误

    // Field value out of range // 注释:测试字段值超出范围的情况
    //  (default, explicit default, alt, neg alt, max, neg max) // 包括:默认精度、显式默认精度、其他精度、负值其他精度、最大值、负最大值
    //  plus >max value for mid/end fields // 以及中间/结束字段的值大于最大值的情况
    f.wholeExpr("INTERVAL '100 0:0' DAY TO MINUTE") // 表达式:100天0小时0分钟,默认精度为2
        .columnType("INTERVAL DAY TO MINUTE NOT NULL"); // 断言:解析器接受,但验证器应该拒绝(精度不足)
    f.wholeExpr("INTERVAL '100 0:0' DAY(2) TO MINUTE") // 表达式:100天0小时0分钟,显式指定精度为2
        .fails("Interval field value 100 exceeds precision of DAY\\(2\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1000 0:0' DAY(3) TO MINUTE") // 表达式:1000天0小时0分钟,精度为3
        .fails("Interval field value 1,000 exceeds precision of DAY\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-1000 0:0' DAY(3) TO MINUTE") // 表达式:-1000天0小时0分钟,精度为3
        .fails("Interval field value -1,000 exceeds precision of DAY\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '2147483648 0:0' DAY(10) TO MINUTE") // 表达式:2147483648天0小时0分钟,精度为10,超出int范围
        .fails("Interval field value 2,147,483,648 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "DAY\\(10\\) field.*");
    f.wholeExpr("INTERVAL '-2147483648 0:0' DAY(10) TO MINUTE") // 表达式:-2147483648天0小时0分钟,精度为10,超出int范围
        .fails("Interval field value -2,147,483,648 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "DAY\\(10\\) field.*");
    f.wholeExpr("INTERVAL '1 24:1' DAY TO MINUTE") // 表达式:1天24小时1分钟,小时值超出有效范围(0-23)
        .fails("Illegal interval literal format '1 24:1' for " // 断言:应该失败,小时值超出范围
            + "INTERVAL DAY TO MINUTE.*");
    f.wholeExpr("INTERVAL '1 1:60' DAY TO MINUTE") // 表达式:1天1小时60分钟,分钟值超出有效范围(0-59)
        .fails("Illegal interval literal format '1 1:60' for INTERVAL DAY TO MINUTE.*"); // 断言:应该失败,分钟值超出范围

    // precision > maximum // 注释:测试精度大于最大值的情况
    f.expr("INTERVAL '1 1:1' ^DAY(11) TO MINUTE^") // 表达式:精度为11,超过最大值10
        .fails("Interval leading field precision '11' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL DAY\\(11\\) TO MINUTE");

    // precision < minimum allowed) // 注释:测试精度小于最小允许值的情况
    // note: parser will catch negative values, here we // 注意:解析器会捕获负值,这里我们
    // just need to check for 0 // 只需要检查0的情况
    f.expr("INTERVAL '0 0' ^DAY(0) TO MINUTE^") // 表达式:精度为0,小于最小值1
        .fails("Interval leading field precision '0' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL DAY\\(0\\) TO MINUTE");
  }

  /**
   * Runs tests for INTERVAL... DAY TO SECOND that should pass parser but fail // 运行INTERVAL DAY TO SECOND类型的测试,应该通过解析器但验证器应该失败(测试错误处理)
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXNegative() tests. // 其他12个subTestIntervalXXXNegative()测试方法中
   */
  public void subTestIntervalDayToSecondNegative() { // 方法作用:测试INTERVAL DAY TO SECOND类型的负向用例(测试各种错误情况)
    // Qualifier - field mismatches // 注释:测试限定符与字段格式不匹配的情况
    f.wholeExpr("INTERVAL ' ::' DAY TO SECOND") // 表达式:只有空格和冒号
        .fails("Illegal interval literal format ' ::' for INTERVAL DAY TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL ' ::.' DAY TO SECOND") // 表达式:只有空格、冒号和小数点
        .fails("Illegal interval literal format ' ::\\.' for INTERVAL DAY TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1' DAY TO SECOND") // 表达式:只有天,缺少小时、分钟和秒部分
        .fails("Illegal interval literal format '1' for INTERVAL DAY TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 2' DAY TO SECOND") // 表达式:只有天和小时,缺少分钟和秒部分
        .fails("Illegal interval literal format '1 2' for INTERVAL DAY TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:2' DAY TO SECOND") // 表达式:使用冒号分隔天和小时,应该使用空格
        .fails("Illegal interval literal format '1:2' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO SECOND");
    f.wholeExpr("INTERVAL '1.2' DAY TO SECOND") // 表达式:使用小数点分隔,应该使用空格和冒号
        .fails("Illegal interval literal format '1\\.2' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO SECOND");
    f.wholeExpr("INTERVAL '1 1:2' DAY TO SECOND") // 表达式:只有天、小时和分钟,缺少秒部分
        .fails("Illegal interval literal format '1 1:2' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO SECOND");
    f.wholeExpr("INTERVAL '1 1:2:x' DAY TO SECOND") // 表达式:秒部分使用无效字符
        .fails("Illegal interval literal format '1 1:2:x' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO SECOND");
    f.wholeExpr("INTERVAL '1:2:3' DAY TO SECOND") // 表达式:缺少天部分,只有小时:分钟:秒
        .fails("Illegal interval literal format '1:2:3' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO SECOND");
    f.wholeExpr("INTERVAL '1:1:1.2' DAY TO SECOND") // 表达式:缺少天部分,只有小时:分钟:秒
        .fails("Illegal interval literal format '1:1:1\\.2' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO SECOND");
    f.wholeExpr("INTERVAL '1 1:2' DAY(2) TO SECOND") // 表达式:缺少秒部分,显式指定精度为2
        .fails("Illegal interval literal format '1 1:2' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY\\(2\\) TO SECOND");
    f.wholeExpr("INTERVAL '1 1' DAY(2) TO SECOND") // 表达式:缺少分钟和秒部分,显式指定精度为2
        .fails("Illegal interval literal format '1 1' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY\\(2\\) TO SECOND");
    f.wholeExpr("INTERVAL 'bogus text' DAY TO SECOND") // 表达式:使用无效的文本
        .fails("Illegal interval literal format 'bogus text' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO SECOND");
    f.wholeExpr("INTERVAL '2345 6:7:8901' DAY TO SECOND(4)") // 表达式:秒的小数部分超出指定精度4
        .fails("Illegal interval literal format '2345 6:7:8901' for " // 断言:应该失败,小数精度超出范围
            + "INTERVAL DAY TO SECOND\\(4\\)");

    // negative field values // 注释:测试负数字段值(双重负号或负号)
    f.wholeExpr("INTERVAL '--1 1:1:1' DAY TO SECOND") // 表达式:天部分使用双重负号
        .fails("Illegal interval literal format '--1 1:1:1' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO SECOND");
    f.wholeExpr("INTERVAL '1 -1:1:1' DAY TO SECOND") // 表达式:小时部分使用负号(不允许负小时)
        .fails("Illegal interval literal format '1 -1:1:1' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO SECOND");
    f.wholeExpr("INTERVAL '1 1:-1:1' DAY TO SECOND") // 表达式:分钟部分使用负号(不允许负分钟)
        .fails("Illegal interval literal format '1 1:-1:1' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO SECOND");
    f.wholeExpr("INTERVAL '1 1:1:-1' DAY TO SECOND") // 表达式:秒部分使用负号(不允许负秒)
        .fails("Illegal interval literal format '1 1:1:-1' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO SECOND");
    f.wholeExpr("INTERVAL '1 1:1:1.-1' DAY TO SECOND") // 表达式:秒的小数部分使用负号(不允许负小数)
        .fails("Illegal interval literal format '1 1:1:1.-1' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO SECOND");

    // Field value out of range // 注释:测试字段值超出范围的情况
    //  (default, explicit default, alt, neg alt, max, neg max) // 包括:默认精度、显式默认精度、其他精度、负值其他精度、最大值、负最大值
    //  plus >max value for mid/end fields // 以及中间/结束字段的值大于最大值的情况
    f.wholeExpr("INTERVAL '100 0' DAY TO SECOND") // 表达式:100天0,缺少小时、分钟和秒部分
        .fails("Illegal interval literal format '100 0' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY TO SECOND.*");
    f.wholeExpr("INTERVAL '100 0' DAY(2) TO SECOND") // 表达式:100天0,显式指定精度为2,缺少小时、分钟和秒部分
        .fails("Illegal interval literal format '100 0' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY\\(2\\) TO SECOND.*");
    f.wholeExpr("INTERVAL '1000 0' DAY(3) TO SECOND") // 表达式:1000天0,精度为3,缺少小时、分钟和秒部分
        .fails("Illegal interval literal format '1000 0' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY\\(3\\) TO SECOND.*");
    f.wholeExpr("INTERVAL '-1000 0' DAY(3) TO SECOND") // 表达式:-1000天0,精度为3,缺少小时、分钟和秒部分
        .fails("Illegal interval literal format '-1000 0' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY\\(3\\) TO SECOND.*");
    f.wholeExpr("INTERVAL '2147483648 1:1:0' DAY(10) TO SECOND") // 表达式:2147483648天1小时1分钟0秒,精度为10,超出int范围
        .fails("Interval field value 2,147,483,648 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "DAY\\(10\\) field.*");
    f.wholeExpr("INTERVAL '-2147483648 1:1:0' DAY(10) TO SECOND") // 表达式:-2147483648天1小时1分钟0秒,精度为10,超出int范围
        .fails("Interval field value -2,147,483,648 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "DAY\\(10\\) field.*");
    f.wholeExpr("INTERVAL '2147483648 0' DAY(10) TO SECOND") // 表达式:2147483648天0,精度为10,超出int范围,缺少小时、分钟和秒部分
        .fails("Illegal interval literal format '2147483648 0' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY\\(10\\) TO SECOND.*");
    f.wholeExpr("INTERVAL '-2147483648 0' DAY(10) TO SECOND") // 表达式:-2147483648天0,精度为10,超出int范围,缺少小时、分钟和秒部分
        .fails("Illegal interval literal format '-2147483648 0' for " // 断言:应该失败,格式错误
            + "INTERVAL DAY\\(10\\) TO SECOND.*");
    f.wholeExpr("INTERVAL '1 24:1:1' DAY TO SECOND") // 表达式:1天24小时1分钟1秒,小时值超出有效范围(0-23)
        .fails("Illegal interval literal format '1 24:1:1' for " // 断言:应该失败,小时值超出范围
            + "INTERVAL DAY TO SECOND.*");
    f.wholeExpr("INTERVAL '1 1:60:1' DAY TO SECOND") // 表达式:1天1小时60分钟1秒,分钟值超出有效范围(0-59)
        .fails("Illegal interval literal format '1 1:60:1' for " // 断言:应该失败,分钟值超出范围
            + "INTERVAL DAY TO SECOND.*");
    f.wholeExpr("INTERVAL '1 1:1:60' DAY TO SECOND") // 表达式:1天1小时1分钟60秒,秒值超出有效范围(0-59)
        .fails("Illegal interval literal format '1 1:1:60' for " // 断言:应该失败,秒值超出范围
            + "INTERVAL DAY TO SECOND.*");
    f.wholeExpr("INTERVAL '1 1:1:1.0000001' DAY TO SECOND") // 表达式:1天1小时1分钟1.0000001秒,小数精度超出默认值6
        .fails("Illegal interval literal format '1 1:1:1\\.0000001' for " // 断言:应该失败,小数精度超出范围
            + "INTERVAL DAY TO SECOND.*");
    f.wholeExpr("INTERVAL '1 1:1:1.0001' DAY TO SECOND(3)") // 表达式:1天1小时1分钟1.0001秒,小数精度超出指定值3
        .fails("Illegal interval literal format '1 1:1:1\\.0001' for " // 断言:应该失败,小数精度超出范围
            + "INTERVAL DAY TO SECOND\\(3\\).*");

    // precision > maximum // 注释:测试精度大于最大值的情况
    f.expr("INTERVAL '1 1' ^DAY(11) TO SECOND^") // 表达式:精度为11,超过最大值10
        .fails("Interval leading field precision '11' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL DAY\\(11\\) TO SECOND");
    f.expr("INTERVAL '1 1' ^DAY TO SECOND(10)^") // 表达式:秒的小数精度为10,超过最大值9
        .fails("Interval fractional second precision '10' out of range for " // 断言:应该失败,小数精度超出范围
            + "INTERVAL DAY TO SECOND\\(10\\)");

    // precision < minimum allowed) // 注释:测试精度小于最小允许值的情况
    // note: parser will catch negative values, here we // 注意:解析器会捕获负值,这里我们
    // just need to check for 0 // 只需要检查0的情况
    f.expr("INTERVAL '0 0:0:0' ^DAY(0) TO SECOND^") // 表达式:精度为0,小于最小值1
        .fails("Interval leading field precision '0' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL DAY\\(0\\) TO SECOND");
  }

  /**
   * Runs tests for INTERVAL... HOUR that should pass parser but fail // 运行INTERVAL HOUR类型的测试,应该通过解析器但验证器应该失败(测试错误处理)
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXNegative() tests. // 其他12个subTestIntervalXXXNegative()测试方法中
   */
  public void subTestIntervalHourNegative() { // 方法作用:测试INTERVAL HOUR类型的负向用例(测试各种错误情况)
    // Qualifier - field mismatches // 注释:测试限定符与字段格式不匹配的情况
    f.wholeExpr("INTERVAL '-' HOUR") // 表达式:只有负号,没有数字
        .fails("Illegal interval literal format '-' for INTERVAL HOUR.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1-2' HOUR") // 表达式:使用'1-2'格式,但HOUR只需要一个字段
        .fails("Illegal interval literal format '1-2' for INTERVAL HOUR.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1.2' HOUR") // 表达式:使用小数格式,但HOUR不支持小数
        .fails("Illegal interval literal format '1.2' for INTERVAL HOUR.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 2' HOUR") // 表达式:使用空格分隔两个数字,但HOUR只需要一个字段
        .fails("Illegal interval literal format '1 2' for INTERVAL HOUR.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:2' HOUR") // 表达式:使用冒号分隔两个数字,但HOUR只需要一个字段
        .fails("Illegal interval literal format '1:2' for INTERVAL HOUR.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1-2' HOUR(2)") // 表达式:使用'1-2'格式,显式指定精度为2
        .fails("Illegal interval literal format '1-2' for INTERVAL HOUR\\(2\\)"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL 'bogus text' HOUR") // 表达式:使用无效的文本
        .fails("Illegal interval literal format 'bogus text' for " // 断言:应该失败,格式错误
            + "INTERVAL HOUR.*");

    // negative field values // 注释:测试负数字段值(双重负号)
    f.wholeExpr("INTERVAL '--1' HOUR") // 表达式:使用双重负号
        .fails("Illegal interval literal format '--1' for INTERVAL HOUR.*"); // 断言:应该失败,格式错误

    // Field value out of range // 注释:测试字段值超出范围的情况
    //  (default, explicit default, alt, neg alt, max, neg max) // 包括:默认精度、显式默认精度、其他精度、负值其他精度、最大值、负最大值
    f.wholeExpr("INTERVAL '100' HOUR") // 表达式:100小时,默认精度为2,但100需要3位
        .columnType("INTERVAL HOUR NOT NULL"); // 断言:解析器接受,但验证器应该拒绝(精度不足)
    f.wholeExpr("INTERVAL '100' HOUR(2)") // 表达式:100小时,显式指定精度为2
        .fails("Interval field value 100 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "HOUR\\(2\\) field.*");
    f.wholeExpr("INTERVAL '1000' HOUR(3)") // 表达式:1000小时,精度为3
        .fails("Interval field value 1,000 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "HOUR\\(3\\) field.*");
    f.wholeExpr("INTERVAL '-1000' HOUR(3)") // 表达式:-1000小时,精度为3
        .fails("Interval field value -1,000 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "HOUR\\(3\\) field.*");
    f.wholeExpr("INTERVAL '2147483648' HOUR(10)") // 表达式:2147483648小时,精度为10,超出int范围
        .fails("Interval field value 2,147,483,648 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "HOUR\\(10\\) field.*");
    f.wholeExpr("INTERVAL '-2147483648' HOUR(10)") // 表达式:-2147483648小时,精度为10,超出int范围
        .fails("Interval field value -2,147,483,648 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "HOUR\\(10\\) field.*");

    // precision > maximum // 注释:测试精度大于最大值的情况
    f.expr("INTERVAL '1' ^HOUR(11)^") // 表达式:精度为11,超过最大值10
        .fails("Interval leading field precision '11' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL HOUR\\(11\\)");

    // precision < minimum allowed) // 注释:测试精度小于最小允许值的情况
    // note: parser will catch negative values, here we // 注意:解析器会捕获负值,这里我们
    // just need to check for 0 // 只需要检查0的情况
    f.expr("INTERVAL '0' ^HOUR(0)^") // 表达式:精度为0,小于最小值1
        .fails("Interval leading field precision '0' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL HOUR\\(0\\)");
  }

  /**
   * Runs tests for INTERVAL... HOUR TO MINUTE that should pass parser but // 运行INTERVAL HOUR TO MINUTE类型的测试,应该通过解析器但验证器应该失败(测试错误处理)
   * fail validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXNegative() tests. // 其他12个subTestIntervalXXXNegative()测试方法中
   */
  public void subTestIntervalHourToMinuteNegative() { // 方法作用:测试INTERVAL HOUR TO MINUTE类型的负向用例(测试各种错误情况)
    // Qualifier - field mismatches // 注释:测试限定符与字段格式不匹配的情况
    f.wholeExpr("INTERVAL ':' HOUR TO MINUTE") // 表达式:只有冒号,没有数字
        .fails("Illegal interval literal format ':' for INTERVAL HOUR TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1' HOUR TO MINUTE") // 表达式:只有小时,缺少分钟部分
        .fails("Illegal interval literal format '1' for INTERVAL HOUR TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:x' HOUR TO MINUTE") // 表达式:分钟部分使用无效字符
        .fails("Illegal interval literal format '1:x' for INTERVAL HOUR TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1.2' HOUR TO MINUTE") // 表达式:使用小数点分隔,应该使用冒号
        .fails("Illegal interval literal format '1.2' for INTERVAL HOUR TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 2' HOUR TO MINUTE") // 表达式:使用空格分隔,应该使用冒号
        .fails("Illegal interval literal format '1 2' for INTERVAL HOUR TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:2:3' HOUR TO MINUTE") // 表达式:包含秒部分,不应该有秒
        .fails("Illegal interval literal format '1:2:3' for INTERVAL HOUR TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 2' HOUR(2) TO MINUTE") // 表达式:使用空格分隔,显式指定精度为2
        .fails("Illegal interval literal format '1 2' for " // 断言:应该失败,格式错误
            + "INTERVAL HOUR\\(2\\) TO MINUTE");
    f.wholeExpr("INTERVAL 'bogus text' HOUR TO MINUTE") // 表达式:使用无效的文本
        .fails("Illegal interval literal format 'bogus text' for " // 断言:应该失败,格式错误
            + "INTERVAL HOUR TO MINUTE");

    // negative field values // 注释:测试负数字段值(双重负号或负号)
    f.wholeExpr("INTERVAL '--1:1' HOUR TO MINUTE") // 表达式:小时部分使用双重负号
        .fails("Illegal interval literal format '--1:1' for INTERVAL HOUR TO MINUTE"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:-1' HOUR TO MINUTE") // 表达式:分钟部分使用负号(不允许负分钟)
        .fails("Illegal interval literal format '1:-1' for INTERVAL HOUR TO MINUTE"); // 断言:应该失败,格式错误

    // Field value out of range // 注释:测试字段值超出范围的情况
    //  (default, explicit default, alt, neg alt, max, neg max) // 包括:默认精度、显式默认精度、其他精度、负值其他精度、最大值、负最大值
    //  plus >max value for mid/end fields // 以及中间/结束字段的值大于最大值的情况
    f.wholeExpr("INTERVAL '100:0' HOUR TO MINUTE") // 表达式:100小时0分钟,默认精度为2
        .columnType("INTERVAL HOUR TO MINUTE NOT NULL"); // 断言:解析器接受,但验证器应该拒绝(精度不足)
    f.wholeExpr("INTERVAL '100:0' HOUR(2) TO MINUTE") // 表达式:100小时0分钟,显式指定精度为2
        .fails("Interval field value 100 exceeds precision of HOUR\\(2\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1000:0' HOUR(3) TO MINUTE") // 表达式:1000小时0分钟,精度为3
        .fails("Interval field value 1,000 exceeds precision of HOUR\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-1000:0' HOUR(3) TO MINUTE") // 表达式:-1000小时0分钟,精度为3
        .fails("Interval field value -1,000 exceeds precision of HOUR\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '2147483648:0' HOUR(10) TO MINUTE") // 表达式:2147483648小时0分钟,精度为10,超出int范围
        .fails("Interval field value 2,147,483,648 exceeds precision of HOUR\\(10\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-2147483648:0' HOUR(10) TO MINUTE") // 表达式:-2147483648小时0分钟,精度为10,超出int范围
        .fails("Interval field value -2,147,483,648 exceeds precision of HOUR\\(10\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1:60' HOUR TO MINUTE") // 表达式:1小时60分钟,分钟值超出有效范围(0-59)
        .fails("Illegal interval literal format '1:60' for INTERVAL HOUR TO MINUTE.*"); // 断言:应该失败,分钟值超出范围

    // precision > maximum // 注释:测试精度大于最大值的情况
    f.expr("INTERVAL '1:1' ^HOUR(11) TO MINUTE^") // 表达式:精度为11,超过最大值10
        .fails("Interval leading field precision '11' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL HOUR\\(11\\) TO MINUTE");

    // precision < minimum allowed) // 注释:测试精度小于最小允许值的情况
    // note: parser will catch negative values, here we // 注意:解析器会捕获负值,这里我们
    // just need to check for 0 // 只需要检查0的情况
    f.expr("INTERVAL '0:0' ^HOUR(0) TO MINUTE^") // 表达式:精度为0,小于最小值1
        .fails("Interval leading field precision '0' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL HOUR\\(0\\) TO MINUTE");
  }

  /**
   * Runs tests for INTERVAL... HOUR TO SECOND that should pass parser but
   * fail validator. A substantially identical set of tests exists in
   * SqlParserTest, and any changes here should be synchronized there.
   * Similarly, any changes to tests here should be echoed appropriately to
   * each of the other 12 subTestIntervalXXXNegative() tests.
   */
  public void subTestIntervalHourToSecondNegative() { // 方法作用:测试INTERVAL HOUR TO SECOND类型的负向用例(测试各种错误情况)
    // Qualifier - field mismatches // 注释:测试限定符与字段格式不匹配的情况
    f.wholeExpr("INTERVAL '::' HOUR TO SECOND") // 表达式:只有冒号,没有数字
        .fails("Illegal interval literal format '::' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '::.' HOUR TO SECOND") // 表达式:只有冒号和小数点,没有数字
        .fails("Illegal interval literal format '::\\.' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1' HOUR TO SECOND") // 表达式:只有小时,缺少分钟和秒部分
        .fails("Illegal interval literal format '1' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 2' HOUR TO SECOND") // 表达式:使用空格分隔,应该使用冒号
        .fails("Illegal interval literal format '1 2' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:2' HOUR TO SECOND") // 表达式:只有小时和分钟,缺少秒部分
        .fails("Illegal interval literal format '1:2' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1.2' HOUR TO SECOND") // 表达式:使用小数点分隔,应该使用冒号
        .fails("Illegal interval literal format '1\\.2' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 1:2' HOUR TO SECOND") // 表达式:使用空格分隔小时和分钟,应该使用冒号
        .fails("Illegal interval literal format '1 1:2' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:2:x' HOUR TO SECOND") // 表达式:秒部分使用无效字符
        .fails("Illegal interval literal format '1:2:x' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:x:3' HOUR TO SECOND") // 表达式:分钟部分使用无效字符
        .fails("Illegal interval literal format '1:x:3' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:1:1.x' HOUR TO SECOND") // 表达式:秒的小数部分使用无效字符
        .fails("Illegal interval literal format '1:1:1\\.x' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 1:2' HOUR(2) TO SECOND") // 表达式:使用空格分隔,显式指定精度为2
        .fails("Illegal interval literal format '1 1:2' for INTERVAL HOUR\\(2\\) TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 1' HOUR(2) TO SECOND") // 表达式:使用空格分隔,缺少分钟和秒部分,显式指定精度为2
        .fails("Illegal interval literal format '1 1' for INTERVAL HOUR\\(2\\) TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL 'bogus text' HOUR TO SECOND") // 表达式:使用无效的文本
        .fails("Illegal interval literal format 'bogus text' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '6:7:8901' HOUR TO SECOND(4)") // 表达式:秒的小数部分超出指定精度4
        .fails("Illegal interval literal format '6:7:8901' for INTERVAL HOUR TO SECOND\\(4\\)"); // 断言:应该失败,小数精度超出范围

    // negative field values // 注释:测试负数字段值(双重负号或负号)
    f.wholeExpr("INTERVAL '--1:1:1' HOUR TO SECOND") // 表达式:小时部分使用双重负号
        .fails("Illegal interval literal format '--1:1:1' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:-1:1' HOUR TO SECOND") // 表达式:分钟部分使用负号(不允许负分钟)
        .fails("Illegal interval literal format '1:-1:1' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:1:-1' HOUR TO SECOND") // 表达式:秒部分使用负号(不允许负秒)
        .fails("Illegal interval literal format '1:1:-1' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:1:1.-1' HOUR TO SECOND") // 表达式:秒的小数部分使用负号(不允许负小数)
        .fails("Illegal interval literal format '1:1:1\\.-1' for INTERVAL HOUR TO SECOND"); // 断言:应该失败,格式错误

    // Field value out of range // 注释:测试字段值超出范围的情况
    //  (default, explicit default, alt, neg alt, max, neg max) // 包括:默认精度、显式默认精度、其他精度、负值其他精度、最大值、负最大值
    //  plus >max value for mid/end fields // 以及中间/结束字段的值大于最大值的情况
    f.wholeExpr("INTERVAL '100:0:0' HOUR TO SECOND") // 表达式:100小时0分钟0秒,默认精度为2
        .columnType("INTERVAL HOUR TO SECOND NOT NULL"); // 断言:解析器接受,但验证器应该拒绝(精度不足)
    f.wholeExpr("INTERVAL '100:0:0' HOUR(2) TO SECOND") // 表达式:100小时0分钟0秒,显式指定精度为2
        .fails("Interval field value 100 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "HOUR\\(2\\) field.*");
    f.wholeExpr("INTERVAL '1000:0:0' HOUR(3) TO SECOND") // 表达式:1000小时0分钟0秒,精度为3
        .fails("Interval field value 1,000 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "HOUR\\(3\\) field.*");
    f.wholeExpr("INTERVAL '-1000:0:0' HOUR(3) TO SECOND") // 表达式:-1000小时0分钟0秒,精度为3
        .fails("Interval field value -1,000 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "HOUR\\(3\\) field.*");
    f.wholeExpr("INTERVAL '2147483648:0:0' HOUR(10) TO SECOND") // 表达式:2147483648小时0分钟0秒,精度为10,超出int范围
        .fails("Interval field value 2,147,483,648 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "HOUR\\(10\\) field.*");
    f.wholeExpr("INTERVAL '-2147483648:0:0' HOUR(10) TO SECOND") // 表达式:-2147483648小时0分钟0秒,精度为10,超出int范围
        .fails("Interval field value -2,147,483,648 exceeds precision of " // 断言:应该失败,值超出精度范围
            + "HOUR\\(10\\) field.*");
    f.wholeExpr("INTERVAL '1:60:1' HOUR TO SECOND") // 表达式:1小时60分钟1秒,分钟值超出有效范围(0-59)
        .fails("Illegal interval literal format '1:60:1' for " // 断言:应该失败,分钟值超出范围
            + "INTERVAL HOUR TO SECOND.*");
    f.wholeExpr("INTERVAL '1:1:60' HOUR TO SECOND") // 表达式:1小时1分钟60秒,秒值超出有效范围(0-59)
        .fails("Illegal interval literal format '1:1:60' for " // 断言:应该失败,秒值超出范围
            + "INTERVAL HOUR TO SECOND.*");
    f.wholeExpr("INTERVAL '1:1:1.0000001' HOUR TO SECOND") // 表达式:1小时1分钟1.0000001秒,小数精度超出默认值6
        .fails("Illegal interval literal format '1:1:1\\.0000001' for " // 断言:应该失败,小数精度超出范围
            + "INTERVAL HOUR TO SECOND.*");
    f.wholeExpr("INTERVAL '1:1:1.0001' HOUR TO SECOND(3)") // 表达式:1小时1分钟1.0001秒,小数精度超出指定值3
        .fails("Illegal interval literal format '1:1:1\\.0001' for " // 断言:应该失败,小数精度超出范围
            + "INTERVAL HOUR TO SECOND\\(3\\).*");

    // precision > maximum // 注释:测试精度大于最大值的情况
    f.expr("INTERVAL '1:1:1' ^HOUR(11) TO SECOND^") // 表达式:精度为11,超过最大值10
        .fails("Interval leading field precision '11' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL HOUR\\(11\\) TO SECOND");
    f.expr("INTERVAL '1:1:1' ^HOUR TO SECOND(10)^") // 表达式:秒的小数精度为10,超过最大值9
        .fails("Interval fractional second precision '10' out of range for " // 断言:应该失败,小数精度超出范围
            + "INTERVAL HOUR TO SECOND\\(10\\)");

    // precision < minimum allowed) // 注释:测试精度小于最小允许值的情况
    // note: parser will catch negative values, here we // 注意:解析器会捕获负值,这里我们
    // just need to check for 0 // 只需要检查0的情况
    f.expr("INTERVAL '0:0:0' ^HOUR(0) TO SECOND^") // 表达式:精度为0,小于最小值1
        .fails("Interval leading field precision '0' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL HOUR\\(0\\) TO SECOND");
  }

  /**
   * Runs tests for INTERVAL... MINUTE that should pass parser but fail // 运行INTERVAL MINUTE类型的测试,应该通过解析器但验证器应该失败(测试错误处理)
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXNegative() tests. // 其他12个subTestIntervalXXXNegative()测试方法中
   */
  public void subTestIntervalMinuteNegative() { // 方法作用:测试INTERVAL MINUTE类型的负向用例(测试各种错误情况)
    // Qualifier - field mismatches // 注释:测试限定符与字段格式不匹配的情况
    f.wholeExpr("INTERVAL '-' MINUTE") // 表达式:只有负号,没有数字
        .fails("Illegal interval literal format '-' for INTERVAL MINUTE.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1-2' MINUTE") // 表达式:使用'1-2'格式,但MINUTE只需要一个字段
        .fails("Illegal interval literal format '1-2' for INTERVAL MINUTE.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1.2' MINUTE") // 表达式:使用小数格式,但MINUTE不支持小数
        .fails("Illegal interval literal format '1.2' for INTERVAL MINUTE.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 2' MINUTE") // 表达式:使用空格分隔两个数字,但MINUTE只需要一个字段
        .fails("Illegal interval literal format '1 2' for INTERVAL MINUTE.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:2' MINUTE") // 表达式:使用冒号分隔两个数字,但MINUTE只需要一个字段
        .fails("Illegal interval literal format '1:2' for INTERVAL MINUTE.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1-2' MINUTE(2)") // 表达式:使用'1-2'格式,显式指定精度为2
        .fails("Illegal interval literal format '1-2' for INTERVAL MINUTE\\(2\\)"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL 'bogus text' MINUTE") // 表达式:使用无效的文本
        .fails("Illegal interval literal format 'bogus text' for INTERVAL MINUTE.*"); // 断言:应该失败,格式错误

    // negative field values // 注释:测试负数字段值(双重负号)
    f.wholeExpr("INTERVAL '--1' MINUTE") // 表达式:使用双重负号
        .fails("Illegal interval literal format '--1' for INTERVAL MINUTE.*"); // 断言:应该失败,格式错误

    // Field value out of range // 注释:测试字段值超出范围的情况
    //  (default, explicit default, alt, neg alt, max, neg max) // 包括:默认精度、显式默认精度、其他精度、负值其他精度、最大值、负最大值
    f.wholeExpr("INTERVAL '100' MINUTE") // 表达式:100分钟,默认精度为2,但100需要3位
        .columnType("INTERVAL MINUTE NOT NULL"); // 断言:解析器接受,但验证器应该拒绝(精度不足)
    f.wholeExpr("INTERVAL '100' MINUTE(2)") // 表达式:100分钟,显式指定精度为2
        .fails("Interval field value 100 exceeds precision of MINUTE\\(2\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1000' MINUTE(3)") // 表达式:1000分钟,精度为3
        .fails("Interval field value 1,000 exceeds precision of MINUTE\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-1000' MINUTE(3)") // 表达式:-1000分钟,精度为3
        .fails("Interval field value -1,000 exceeds precision of MINUTE\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '2147483648' MINUTE(10)") // 表达式:2147483648分钟,精度为10,超出int范围
        .fails("Interval field value 2,147,483,648 exceeds precision of MINUTE\\(10\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-2147483648' MINUTE(10)") // 表达式:-2147483648分钟,精度为10,超出int范围
        .fails("Interval field value -2,147,483,648 exceeds precision of MINUTE\\(10\\) field.*"); // 断言:应该失败,值超出精度范围

    // precision > maximum // 注释:测试精度大于最大值的情况
    f.expr("INTERVAL '1' ^MINUTE(11)^") // 表达式:精度为11,超过最大值10
        .fails("Interval leading field precision '11' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL MINUTE\\(11\\)");

    // precision < minimum allowed) // 注释:测试精度小于最小允许值的情况
    // note: parser will catch negative values, here we // 注意:解析器会捕获负值,这里我们
    // just need to check for 0 // 只需要检查0的情况
    f.expr("INTERVAL '0' ^MINUTE(0)^") // 表达式:精度为0,小于最小值1
        .fails("Interval leading field precision '0' out of range for " // 断言:应该失败,精度超出范围
            + "INTERVAL MINUTE\\(0\\)");
  }

  /**
   * Runs tests for INTERVAL... MINUTE TO SECOND that should pass parser but // 运行INTERVAL MINUTE TO SECOND类型的测试,应该通过解析器但验证器应该失败(测试错误处理)
   * fail validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXNegative() tests. // 其他12个subTestIntervalXXXNegative()测试方法中
   */
  public void subTestIntervalMinuteToSecondNegative() { // 方法作用:测试INTERVAL MINUTE TO SECOND类型的负向用例(测试各种错误情况)
    // Qualifier - field mismatches // 注释:测试限定符与字段格式不匹配的情况
    f.wholeExpr("INTERVAL ':' MINUTE TO SECOND") // 表达式:只有冒号,没有数字
        .fails("Illegal interval literal format ':' for INTERVAL MINUTE TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL ':.' MINUTE TO SECOND") // 表达式:只有冒号和小数点,没有数字
        .fails("Illegal interval literal format ':\\.' for INTERVAL MINUTE TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1' MINUTE TO SECOND") // 表达式:只有分钟,缺少秒部分
        .fails("Illegal interval literal format '1' for INTERVAL MINUTE TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 2' MINUTE TO SECOND") // 表达式:使用空格分隔,应该使用冒号
        .fails("Illegal interval literal format '1 2' for INTERVAL MINUTE TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1.2' MINUTE TO SECOND") // 表达式:使用小数点分隔,应该使用冒号
        .fails("Illegal interval literal format '1\\.2' for INTERVAL MINUTE TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 1:2' MINUTE TO SECOND") // 表达式:使用空格分隔分钟和秒,应该使用冒号
        .fails("Illegal interval literal format '1 1:2' for INTERVAL MINUTE TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:x' MINUTE TO SECOND") // 表达式:秒部分使用无效字符
        .fails("Illegal interval literal format '1:x' for INTERVAL MINUTE TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL 'x:3' MINUTE TO SECOND") // 表达式:分钟部分使用无效字符
        .fails("Illegal interval literal format 'x:3' for INTERVAL MINUTE TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:1.x' MINUTE TO SECOND") // 表达式:秒的小数部分使用无效字符
        .fails("Illegal interval literal format '1:1\\.x' for INTERVAL MINUTE TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 1:2' MINUTE(2) TO SECOND") // 表达式:使用空格分隔,显式指定精度为2
        .fails("Illegal interval literal format '1 1:2' for INTERVAL MINUTE\\(2\\) TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 1' MINUTE(2) TO SECOND") // 表达式:使用空格分隔,缺少秒部分,显式指定精度为2
        .fails("Illegal interval literal format '1 1' for INTERVAL MINUTE\\(2\\) TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL 'bogus text' MINUTE TO SECOND") // 表达式:使用无效的文本
        .fails("Illegal interval literal format 'bogus text' for INTERVAL MINUTE TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '7:8901' MINUTE TO SECOND(4)") // 表达式:秒的小数部分超出指定精度4
        .fails("Illegal interval literal format '7:8901' for INTERVAL MINUTE TO SECOND\\(4\\)"); // 断言:应该失败,小数精度超出范围

    // negative field values // 注释:测试负数字段值(双重负号或负号)
    f.wholeExpr("INTERVAL '--1:1' MINUTE TO SECOND") // 表达式:分钟部分使用双重负号
        .fails("Illegal interval literal format '--1:1' for INTERVAL MINUTE TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:-1' MINUTE TO SECOND") // 表达式:秒部分使用负号(不允许负秒)
        .fails("Illegal interval literal format '1:-1' for INTERVAL MINUTE TO SECOND"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:1.-1' MINUTE TO SECOND") // 表达式:秒的小数部分使用负号(不允许负小数)
        .fails("Illegal interval literal format '1:1.-1' for INTERVAL MINUTE TO SECOND"); // 断言:应该失败,格式错误

    // Field value out of range // 注释:测试字段值超出范围的情况
    //  (default, explicit default, alt, neg alt, max, neg max) // 包括:默认精度、显式默认精度、其他精度、负值其他精度、最大值、负最大值
    //  plus >max value for mid/end fields // 以及中间/结束字段的值大于最大值的情况
    f.wholeExpr("INTERVAL '100:0' MINUTE TO SECOND") // 表达式:100分钟0秒,默认精度为2
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL"); // 断言:解析器接受,但验证器应该拒绝(精度不足)
    f.wholeExpr("INTERVAL '100:0' MINUTE(2) TO SECOND") // 表达式:100分钟0秒,显式指定精度为2
        .fails("Interval field value 100 exceeds precision of MINUTE\\(2\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1000:0' MINUTE(3) TO SECOND") // 表达式:1000分钟0秒,精度为3
        .fails("Interval field value 1,000 exceeds precision of MINUTE\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-1000:0' MINUTE(3) TO SECOND") // 表达式:-1000分钟0秒,精度为3
        .fails("Interval field value -1,000 exceeds precision of MINUTE\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '2147483648:0' MINUTE(10) TO SECOND") // 表达式:2147483648分钟0秒,精度为10,超出int范围
        .fails("Interval field value 2,147,483,648 exceeds precision of MINUTE\\(10\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-2147483648:0' MINUTE(10) TO SECOND") // 表达式:-2147483648分钟0秒,精度为10,超出int范围
        .fails("Interval field value -2,147,483,648 exceeds precision of MINUTE\\(10\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1:60' MINUTE TO SECOND") // 表达式:1分钟60秒,秒值超出有效范围(0-59)
        .fails("Illegal interval literal format '1:60' for" // 断言:应该失败,秒值超出范围
            + " INTERVAL MINUTE TO SECOND.*");
    f.wholeExpr("INTERVAL '1:1.0000001' MINUTE TO SECOND") // 表达式:1分钟1.0000001秒,小数精度超出默认值6
        .fails("Illegal interval literal format '1:1\\.0000001' for" // 断言:应该失败,小数精度超出范围
            + " INTERVAL MINUTE TO SECOND.*");
    f.wholeExpr("INTERVAL '1:1:1.0001' MINUTE TO SECOND(3)") // 表达式:1分钟1秒(格式错误),小数精度超出指定值3
        .fails("Illegal interval literal format '1:1:1\\.0001' for" // 断言:应该失败,格式错误且小数精度超出范围
            + " INTERVAL MINUTE TO SECOND\\(3\\).*");

    // precision > maximum // 注释:测试精度大于最大值的情况
    f.expr("INTERVAL '1:1' ^MINUTE(11) TO SECOND^") // 表达式:精度为11,超过最大值10
        .fails("Interval leading field precision '11' out of range for" // 断言:应该失败,精度超出范围
            + " INTERVAL MINUTE\\(11\\) TO SECOND");
    f.expr("INTERVAL '1:1' ^MINUTE TO SECOND(10)^") // 表达式:秒的小数精度为10,超过最大值9
        .fails("Interval fractional second precision '10' out of range for" // 断言:应该失败,小数精度超出范围
            + " INTERVAL MINUTE TO SECOND\\(10\\)");

    // precision < minimum allowed) // 注释:测试精度小于最小允许值的情况
    // note: parser will catch negative values, here we // 注意:解析器会捕获负值,这里我们
    // just need to check for 0 // 只需要检查0的情况
    f.expr("INTERVAL '0:0' ^MINUTE(0) TO SECOND^") // 表达式:精度为0,小于最小值1
        .fails("Interval leading field precision '0' out of range for" // 断言:应该失败,精度超出范围
            + " INTERVAL MINUTE\\(0\\) TO SECOND");
  }

  /**
   * Runs tests for INTERVAL... SECOND that should pass parser but fail // 运行INTERVAL SECOND类型的测试,应该通过解析器但验证器应该失败(测试错误处理)
   * validator. A substantially identical set of tests exists in // 验证器。在SqlParserTest中存在基本相同的测试集
   * SqlParserTest, and any changes here should be synchronized there. // 此处的任何更改都应该同步到那里
   * Similarly, any changes to tests here should be echoed appropriately to // 类似地,此处的任何测试更改都应该适当地反映到
   * each of the other 12 subTestIntervalXXXNegative() tests. // 其他12个subTestIntervalXXXNegative()测试方法中
   */
  public void subTestIntervalSecondNegative() { // 方法作用:测试INTERVAL SECOND类型的负向用例(测试各种错误情况)
    // Qualifier - field mismatches // 注释:测试限定符与字段格式不匹配的情况
    f.wholeExpr("INTERVAL ':' SECOND") // 表达式:只有冒号,没有数字
        .fails("Illegal interval literal format ':' for INTERVAL SECOND.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '.' SECOND") // 表达式:只有小数点,没有数字
        .fails("Illegal interval literal format '\\.' for INTERVAL SECOND.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1-2' SECOND") // 表达式:使用'1-2'格式,但SECOND只需要一个字段
        .fails("Illegal interval literal format '1-2' for INTERVAL SECOND.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1.x' SECOND") // 表达式:秒的小数部分使用无效字符
        .fails("Illegal interval literal format '1\\.x' for INTERVAL SECOND.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL 'x.1' SECOND") // 表达式:秒的整数部分使用无效字符
        .fails("Illegal interval literal format 'x\\.1' for INTERVAL SECOND.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1 2' SECOND") // 表达式:使用空格分隔两个数字,但SECOND只需要一个字段
        .fails("Illegal interval literal format '1 2' for INTERVAL SECOND.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1:2' SECOND") // 表达式:使用冒号分隔两个数字,但SECOND只需要一个字段
        .fails("Illegal interval literal format '1:2' for INTERVAL SECOND.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1-2' SECOND(2)") // 表达式:使用'1-2'格式,显式指定精度为2
        .fails("Illegal interval literal format '1-2' for INTERVAL SECOND\\(2\\)"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL 'bogus text' SECOND") // 表达式:使用无效的文本
        .fails("Illegal interval literal format 'bogus text' for INTERVAL SECOND.*"); // 断言:应该失败,格式错误

    // negative field values // 注释:测试负数字段值(双重负号或负号)
    f.wholeExpr("INTERVAL '--1' SECOND") // 表达式:使用双重负号
        .fails("Illegal interval literal format '--1' for INTERVAL SECOND.*"); // 断言:应该失败,格式错误
    f.wholeExpr("INTERVAL '1.-1' SECOND") // 表达式:秒的小数部分使用负号(不允许负小数)
        .fails("Illegal interval literal format '1.-1' for INTERVAL SECOND.*"); // 断言:应该失败,格式错误

    // Field value out of range // 注释:测试字段值超出范围的情况
    //  (default, explicit default, alt, neg alt, max, neg max) // 包括:默认精度、显式默认精度、其他精度、负值其他精度、最大值、负最大值
    f.wholeExpr("INTERVAL '100' SECOND") // 表达式:100秒,默认精度为2,但100需要3位
        .columnType("INTERVAL SECOND NOT NULL"); // 断言:解析器接受,但验证器应该拒绝(精度不足)
    f.wholeExpr("INTERVAL '100' SECOND(2)") // 表达式:100秒,显式指定精度为2
        .fails("Interval field value 100 exceeds precision of SECOND\\(2\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1000' SECOND(3)") // 表达式:1000秒,精度为3
        .fails("Interval field value 1,000 exceeds precision of SECOND\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-1000' SECOND(3)") // 表达式:-1000秒,精度为3
        .fails("Interval field value -1,000 exceeds precision of SECOND\\(3\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '2147483648' SECOND(10)") // 表达式:2147483648秒,精度为10,超出int范围
        .fails("Interval field value 2,147,483,648 exceeds precision of SECOND\\(10\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '-2147483648' SECOND(10)") // 表达式:-2147483648秒,精度为10,超出int范围
        .fails("Interval field value -2,147,483,648 exceeds precision of SECOND\\(10\\) field.*"); // 断言:应该失败,值超出精度范围
    f.wholeExpr("INTERVAL '1.0000001' SECOND") // 表达式:1.0000001秒,小数精度超出默认值6
        .fails("Illegal interval literal format '1\\.0000001' for INTERVAL SECOND.*"); // 断言:应该失败,小数精度超出范围
    f.wholeExpr("INTERVAL '1.0000001' SECOND(2)") // 表达式:1.0000001秒,秒精度为2,小数精度超出默认值6
        .fails("Illegal interval literal format '1\\.0000001' for INTERVAL SECOND\\(2\\).*"); // 断言:应该失败,小数精度超出范围
    f.wholeExpr("INTERVAL '1.0001' SECOND(2, 3)") // 表达式:1.0001秒,秒精度为2,小数精度超出指定值3
        .fails("Illegal interval literal format '1\\.0001' for INTERVAL SECOND\\(2, 3\\).*"); // 断言:应该失败,小数精度超出范围
    f.wholeExpr("INTERVAL '1.0000000001' SECOND(2, 9)") // 表达式:1.0000000001秒,秒精度为2,小数精度超出指定值9(10位小数)
        .fails("Illegal interval literal format '1\\.0000000001' for" // 断言:应该失败,小数精度超出范围
            + " INTERVAL SECOND\\(2, 9\\).*");

    // precision > maximum // 注释:测试精度大于最大值的情况
    f.expr("INTERVAL '1' ^SECOND(11)^") // 表达式:精度为11,超过最大值10
        .fails("Interval leading field precision '11' out of range for" // 断言:应该失败,精度超出范围
            + " INTERVAL SECOND\\(11\\)");
    f.expr("INTERVAL '1.1' ^SECOND(1, 10)^") // 表达式:秒的小数精度为10,超过最大值9
        .fails("Interval fractional second precision '10' out of range for" // 断言:应该失败,小数精度超出范围
            + " INTERVAL SECOND\\(1, 10\\)");

    // precision < minimum allowed) // 注释:测试精度小于最小允许值的情况
    // note: parser will catch negative values, here we // 注意:解析器会捕获负值,这里我们
    // just need to check for 0 // 只需要检查0的情况
    f.expr("INTERVAL '0' ^SECOND(0)^") // 表达式:精度为0,小于最小值1
        .fails("Interval leading field precision '0' out of range for" // 断言:应该失败,精度超出范围
            + " INTERVAL SECOND\\(0\\)");
  }

  public void subTestMisc() { // 方法作用:测试INTERVAL的杂项情况(边界条件和特殊情况)
    // Miscellaneous // 注释:杂项测试,包括小数部分和前导零的处理
    // fractional value is not OK, even if it is 0 // 注释:小数值不被允许,即使小数部分是0
    f.wholeExpr("INTERVAL '1.0' HOUR") // 表达式:1.0小时(带小数部分)
        .fails("Illegal interval literal format '1.0' for INTERVAL HOUR"); // 断言:应该失败,因为HOUR类型不允许小数
    // only seconds are allowed to have a fractional part // 注释:只有SECOND类型允许有小数部分
    f.expr("INTERVAL '1.0' SECOND") // 表达式:1.0秒(带小数部分)
        .columnType("INTERVAL SECOND NOT NULL"); // 断言:列类型正确,SECOND允许小数
    // leading zeros do not cause precision to be exceeded // 注释:前导零不会导致精度超出
    f.expr("INTERVAL '0999' MONTH(3)") // 表达式:0999个月(前导零,精度为3)
        .columnType("INTERVAL MONTH(3) NOT NULL"); // 断言:列类型正确,前导零不影响精度判断
  }

  /** Fluent interface for binding an expression to create a fixture that can // 流式接口:用于绑定表达式以创建测试装置,该装置可用于验证、检查AST或检查类型
   * be used to validate, check AST, or check type. */
  public interface Fixture { // Fixture接口:表达式绑定的流式接口,提供创建测试装置的方法
    Fixture2 expr(String s); // 方法:绑定一个表达式字符串,返回Fixture2对象用于进一步验证
    Fixture2 wholeExpr(String s); // 方法:绑定一个完整表达式字符串,返回Fixture2对象用于进一步验证
  }

  /** Fluent interface to validate an expression. */ // 流式接口:用于验证表达式
  public interface Fixture2 { // Fixture2接口:表达式验证的流式接口,提供各种断言方法
    /** Checks that the expression is valid in the parser // 方法作用:检查表达式在解析器中有效,但在验证器中无效(并抛出指定的错误消息)
     * but invalid (with the given error message) in the validator. */
    void fails(String expected); // 参数:expected-预期的错误消息模式;用于测试错误情况

    /** Checks that the expression is valid in the parser and validator // 方法作用:检查表达式在解析器和验证器中都有效,并且具有给定的列类型
     * and has the given column type. */
    void columnType(String expectedType); // 参数:expectedType-预期的列类型字符串;用于验证类型推断

    /** Checks that the expression parses successfully and produces the given // 方法作用:检查表达式成功解析,并在反解析时产生给定的SQL
     * SQL when unparsed. */
    Fixture2 assertParse(String expectedAst); // 参数:expectedAst-预期的解析结果字符串;返回:Fixture2对象以支持链式调用
  }
}
