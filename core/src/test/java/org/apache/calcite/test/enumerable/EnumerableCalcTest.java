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
 */ // Apache许可证声明，允许在遵守许可证条款的前提下使用、修改和分发代码
package org.apache.calcite.test.enumerable; // 定义包名，该测试类位于org.apache.calcite.test.enumerable包下，用于测试EnumerableCalc相关功能

import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入ReflectiveSchema，用于将Java对象转换为Calcite可识别的schema
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator，表示SQL操作符的基类
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable，包含标准SQL操作符的表
import org.apache.calcite.test.CalciteAssert; // 导入CalciteAssert，用于构建测试断言和验证查询结果
import org.apache.calcite.test.schemata.catchall.CatchallSchema; // 导入CatchallSchema，测试用的catchall模式schema
import org.apache.calcite.test.schemata.hr.HrSchema; // 导入HrSchema，测试用的人力资源schema

import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法

/**
 * Unit test for
 * {@link org.apache.calcite.adapter.enumerable.EnumerableCalc}.
 */ // 该类是EnumerableCalc的单元测试类，用于测试EnumerableCalc（可枚举计算）操作符的功能和正确性
class EnumerableCalcTest { // 定义EnumerableCalcTest测试类，包含多个测试方法来验证EnumerableCalc的各种功能

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3536">[CALCITE-3536]
   * NPE when executing plan with Coalesce due to wrong NullAs strategy</a>.
   */ // 测试方法：验证COALESCE函数的实现，该测试用例针对CALCITE-3536问题，即由于错误的NullAs策略导致执行包含Coalesce的计划时出现空指针异常
  @Test void testCoalesceImplementation() { // 使用@Test注解标记为测试方法，测试COALESCE函数在EnumerableCalc中的实现
    CalciteAssert.that() // 创建CalciteAssert实例，开始构建测试断言
        .withSchema("s", new ReflectiveSchema(new HrSchema())) // 使用反射schema，将HrSchema（人力资源schema）注册为名为"s"的schema
        .withRel( // 构建关系表达式（RelNode）
            builder -> builder // 使用RelBuilder构建关系表达式
                .scan("s", "emps") // 扫描schema "s"中的"emps"表
                .project( // 创建投影操作，选择或计算需要的字段
                  builder.call( // 调用SQL函数
                    SqlStdOperatorTable.COALESCE, // 使用COALESCE函数，返回第一个非空参数
                    builder.field("commission"), // 第一个参数：commission字段（佣金）
                    builder.literal(0))) // 第二个参数：字面量0，如果commission为null则返回0
                .build()) // 构建完成，返回RelNode
        .planContains("input_value != null ? input_value : 0") // 验证生成的计划中包含指定的代码片段，即COALESCE被正确转换为三元运算符
        .returnsUnordered( // 验证返回结果，不关心顺序
            "$f0=0", // 第一行结果：commission为null，COALESCE返回0
            "$f0=250", // 第二行结果：commission为250
            "$f0=500", // 第三行结果：commission为500
            "$f0=1000"); // 第四行结果：commission为1000
  }

  /**
   * Test cases for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4419">[CALCITE-4419]
   * Posix regex operators cannot be used within RelBuilder</a>.
   */ // 测试方法：验证Posix正则表达式操作符在RelBuilder中的使用，针对CALCITE-4419问题，即Posix正则表达式操作符无法在RelBuilder中使用
  @Test void testPosixRegexCaseSensitive() { // 测试大小写敏感的Posix正则表达式匹配
    checkPosixRegex("E..c", SqlStdOperatorTable.POSIX_REGEX_CASE_SENSITIVE) // 调用辅助方法，使用大小写敏感的正则表达式"E..c"（匹配以E开头，后面跟两个任意字符，以c结尾的字符串）
        .returnsUnordered("empid=200; name=Eric"); // 验证返回结果，只有"Eric"匹配（E开头，ri在中间，c结尾）
    checkPosixRegex("e..c", SqlStdOperatorTable.POSIX_REGEX_CASE_SENSITIVE) // 使用小写"e"的正则表达式
        .returnsUnordered(); // 验证返回结果为空，因为没有以小写e开头的名字
  }

  @Test void testPosixRegexCaseInsensitive() { // 测试大小写不敏感的Posix正则表达式匹配
    checkPosixRegex("E..c", SqlStdOperatorTable.POSIX_REGEX_CASE_INSENSITIVE) // 使用大小写不敏感的正则表达式"E..c"
        .returnsUnordered("empid=200; name=Eric"); // 验证返回结果，"Eric"匹配（不区分大小写）
    checkPosixRegex("e..c", SqlStdOperatorTable.POSIX_REGEX_CASE_INSENSITIVE) // 使用小写"e"的正则表达式（不区分大小写）
        .returnsUnordered("empid=200; name=Eric"); // 验证返回结果，"Eric"仍匹配，因为不区分大小写
  }

  @Test void testNegatedPosixRegexCaseSensitive() { // 测试大小写敏感的否定Posix正则表达式匹配（不匹配指定模式的记录）
    checkPosixRegex("E..c", SqlStdOperatorTable.NEGATED_POSIX_REGEX_CASE_SENSITIVE) // 使用否定的大小写敏感正则表达式"E..c"
        .returnsUnordered("empid=100; name=Bill", // 验证返回结果，所有不以"E..c"模式匹配的员工
            "empid=110; name=Theodore", // Theodore不匹配E..c
            "empid=150; name=Sebastian"); // Sebastian不匹配E..c
    checkPosixRegex("e..c", SqlStdOperatorTable.NEGATED_POSIX_REGEX_CASE_SENSITIVE) // 使用否定的大小写敏感正则表达式"e..c"
        .returnsUnordered("empid=100; name=Bill", // 验证返回结果，所有不以"e..c"模式匹配的员工（包括Eric，因为E不等于e）
            "empid=110; name=Theodore",
            "empid=150; name=Sebastian",
            "empid=200; name=Eric"); // Eric也被包含，因为大小写敏感，E不等于小写e
  }

  @Test void testNegatedPosixRegexCaseInsensitive() { // 测试大小写不敏感的否定Posix正则表达式匹配
    checkPosixRegex("E..c", SqlStdOperatorTable.NEGATED_POSIX_REGEX_CASE_INSENSITIVE) // 使用否定的大小写不敏感正则表达式"E..c"
        .returnsUnordered("empid=100; name=Bill", // 验证返回结果，所有不以"E..c"或"e..c"模式匹配的员工
            "empid=110; name=Theodore",
            "empid=150; name=Sebastian");
    checkPosixRegex("e..c", SqlStdOperatorTable.NEGATED_POSIX_REGEX_CASE_INSENSITIVE) // 使用否定的大小写不敏感正则表达式"e..c"
        .returnsUnordered("empid=100; name=Bill", // 验证返回结果，与上面相同，因为不区分大小写，Eric被排除
            "empid=110; name=Theodore",
            "empid=150; name=Sebastian");
  }

  private CalciteAssert.AssertQuery checkPosixRegex( // 私有辅助方法：构建并返回一个用于测试Posix正则表达式的AssertQuery对象
      String literalValue, // 参数：正则表达式的字面值，如"E..c"或"e..c"
      SqlOperator operator) { // 参数：SQL操作符，指定使用哪种正则表达式操作符（大小写敏感/不敏感，肯定/否定）
    return CalciteAssert.that() // 创建CalciteAssert实例
        .withSchema("s", new ReflectiveSchema(new HrSchema())) // 注册HrSchema为名为"s"的schema
        .withRel( // 构建关系表达式
            builder -> builder // 使用RelBuilder
                .scan("s", "emps") // 扫描"emps"表
                .filter( // 添加过滤条件
                    builder.call( // 调用操作符
                        operator, // 使用传入的正则表达式操作符
                        builder.field("name"), // 对name字段进行匹配
                        builder.literal(literalValue))) // 使用传入的字面值作为正则表达式模式
                .project( // 投影需要的字段
                    builder.field("empid"), // 选择empid字段
                    builder.field("name")) // 选择name字段
                .build()); // 构建完成
  }

  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6680">[CALCITE-6680]
   * RexImpTable erroneously declares NullPolicy.NONE for IS_EMPTY</a>. */ // 测试方法：验证IS_EMPTY函数在数组上的检查，针对CALCITE-6680问题，即RexImpTable错误地将IS_EMPTY声明为NullPolicy.NONE
  @Test public void testEmptyCheckOnArray() { // 测试IS_EMPTY函数在数组类型上的实现
    CalciteAssert.that() // 创建CalciteAssert实例
        .withSchema("s", new ReflectiveSchema(new CatchallSchema())) // 注册CatchallSchema为名为"s"的schema，该schema包含各种类型
        .withRel(builder -> builder // 构建关系表达式
            .scan("s", "everyTypes") // 扫描"everyTypes"表，该表包含各种类型的数据
            .project(builder.call(SqlStdOperatorTable.IS_EMPTY, builder.field("list"))) // 投影IS_EMPTY函数的结果，检查list字段是否为空
            .build()) // 构建完成
        .planContains("input_value != null && input_value.isEmpty()") // 验证生成的计划包含正确的null检查和isEmpty调用，确保先检查null再调用isEmpty
        .returnsUnordered("$f0=false", "$f0=true"); // 验证返回结果，包含false（非空列表）和true（空列表）
  }
}
