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
package org.apache.calcite.sql;  // 定义包名，表示这个类属于 org.apache.calcite.sql 包
import org.apache.calcite.sql.parser.SqlParseException;  // 导入 SqlParseException 类，用于处理 SQL 解析异常
import org.apache.calcite.sql.parser.SqlParser;  // 导入 SqlParser 类，用于解析 SQL 语句

import org.junit.jupiter.api.Test;  // 导入 JUnit5 的 Test 注解，用于标记测试方法

import static org.hamcrest.CoreMatchers.equalTo;  // 导入 equalTo 匹配器，用于断言两个对象相等
import static org.hamcrest.CoreMatchers.is;  // 导入 is 匹配器，用于断言条件为真
import static org.hamcrest.MatcherAssert.assertThat;  // 导入 assertThat 断言方法，用于编写断言语句
import static org.hamcrest.Matchers.hasToString;  // 导入 hasToString 匹配器，用于断言对象的字符串表示

/**
 * Test for {@link SqlSetOption}.
 * SqlSetOptionOperatorTest 类：这是 SqlSetOption 操作符的测试类
 * SqlSetOption 是 Calcite 中表示 SET 和 RESET 语句的 SQL 节点
 * 该测试类的主要作用是验证 SqlSetOption 操作符的功能是否正常
 * 测试内容包括：
 * 1. 测试带作用域的 SET 操作符（ALTER SYSTEM SET）
 * 2. 测试不带作用域的 SET 操作符（SET）
 * 3. 测试带作用域的 RESET 操作符（ALTER SESSION RESET）
 * 4. 测试不带作用域的 RESET 操作符（RESET）
 * 
 * 核心测试逻辑：
 * - 解析 SQL 语句生成 SqlSetOption 节点
 * - 使用操作符的 createCall 方法重新创建节点
 * - 验证原始节点和重建节点的所有属性是否一致
 * 
 * 涉及的核心概念：
 * - SqlNode: SQL 抽象语法树的节点基类
 * - SqlCall: 表示函数调用或操作符调用的 SQL 节点
 * - SqlSetOption: 表示 SET/RESET 语句的具体节点类型
 * - SqlOperator: SQL 操作符，定义如何创建和验证 SQL 节点
 * - Scope: 作用域，包括 SYSTEM、SESSION 等，表示配置的作用范围
 */
class SqlSetOptionOperatorTest {  // 测试类定义

  @Test void testSqlSetOptionOperatorScopeSet() throws SqlParseException {  // 测试方法：测试带作用域的 SET 操作符（ALTER SYSTEM SET）
    SqlNode node = parse("alter system set optionA.optionB.optionC = true");  // 解析 SQL 语句，返回 SqlNode 节点
    checkSqlSetOptionSame(node);  // 调用验证方法，检查操作符的 createCall 方法是否能正确重建节点
  }  // 测试方法结束

  public SqlNode parse(String s) throws SqlParseException {  // 辅助方法：解析 SQL 字符串为 SqlNode 节点
    return SqlParser.create(s).parseStmt();  // 创建 SQL 解析器并解析 SQL 语句，返回解析后的 AST 根节点
  }  // 辅助方法结束

  @Test void testSqlSetOptionOperatorSet() throws SqlParseException {  // 测试方法：测试不带作用域的 SET 操作符（SET）
    SqlNode node = parse("set optionA.optionB.optionC = true");  // 解析 SQL 语句，返回 SqlNode 节点
    checkSqlSetOptionSame(node);  // 调用验证方法，检查操作符的 createCall 方法是否能正确重建节点
  }  // 测试方法结束

  @Test void testSqlSetOptionOperatorScopeReset() throws SqlParseException {  // 测试方法：测试带作用域的 RESET 操作符（ALTER SESSION RESET）
    SqlNode node = parse("alter session reset param1.param2.param3");  // 解析 SQL 语句，返回 SqlNode 节点
    checkSqlSetOptionSame(node);  // 调用验证方法，检查操作符的 createCall 方法是否能正确重建节点
  }  // 测试方法结束

  @Test void testSqlSetOptionOperatorReset() throws SqlParseException {  // 测试方法：测试不带作用域的 RESET 操作符（RESET）
    SqlNode node = parse("reset param1.param2.param3");  // 解析 SQL 语句，返回 SqlNode 节点
    checkSqlSetOptionSame(node);  // 调用验证方法，检查操作符的 createCall 方法是否能正确重建节点
  }  // 测试方法结束

  private static void checkSqlSetOptionSame(SqlNode node) {  // 私有静态验证方法：验证操作符的 createCall 方法是否能正确重建节点
    SqlSetOption opt = (SqlSetOption) node;  // 将 SqlNode 强制转换为 SqlSetOption 类型
    SqlCall returned =  // 声明一个 SqlCall 变量，用于存储通过 createCall 方法重建的节点
        opt.getOperator().createCall(opt.getFunctionQuantifier(),  // 调用操作符的 createCall 方法，传入函数量词（如 DISTINCT、ALL 等）
            opt.getParserPosition(),  // 传入解析位置信息，用于错误提示和调试
            opt.getOperandList());  // 传入操作数列表，包括作用域、选项名、值等
    assertThat(opt.getClass(), equalTo(returned.getClass()));  // 断言：验证原始节点和重建节点的类型是否相同
    SqlSetOption optRet = (SqlSetOption) returned;  // 将重建的 SqlCall 转换为 SqlSetOption 类型
    assertThat(optRet.getScope(), is(opt.getScope()));  // 断言：验证作用域（SYSTEM、SESSION 等）是否相同
    assertThat(optRet.name(), is(opt.name()));  // 断言：验证选项名称（如 optionA.optionB.optionC）是否相同
    assertThat(optRet.getFunctionQuantifier(), is(opt.getFunctionQuantifier()));  // 断言：验证函数量词是否相同
    assertThat(optRet.getParserPosition(), is(opt.getParserPosition()));  // 断言：验证解析位置信息是否相同
    assertThat(optRet.getValue(), is(opt.getValue()));  // 断言：验证选项值（如 true）是否相同
    assertThat(optRet, hasToString(opt.toString()));  // 断言：验证字符串表示是否相同，确保 toString 方法输出一致
  }  // 验证方法结束

}  // 测试类结束
