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
package org.apache.calcite.sql; // 声明包名，该类属于org.apache.calcite.sql包，是Calcite SQL框架的核心包之一

import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入标准SQL操作符表，包含所有标准SQL操作符（如=、>、<等）
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SQL解析位置类，用于标记SQL语法元素在原始SQL语句中的位置信息

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.util.List; // 导入Java集合框架中的List接口，用于操作列表集合

import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具，用于编写可读性强的断言
import static org.hamcrest.Matchers.equalTo; // 导入equalTo匹配器，用于验证两个值是否相等
import static org.hamcrest.Matchers.hasSize; // 导入hasSize匹配器，用于验证集合的大小

/**
 * Tests for SqlCall operands' order and size match with set.
 * See <a href="https://issues.apache.org/jira/browse/CALCITE-6964">[CALCITE-6964]</a>
 * // 该测试类用于测试SqlCall操作数的顺序和大小是否与通过setOperand设置的操作数匹配
 * // SqlCall是Calcite中表示SQL函数调用或操作符调用的抽象基类，它维护一个操作数列表
 * // 该测试主要验证：当通过setOperand方法设置操作数后，getOperandList方法返回的操作数列表
 * // 是否保持了正确的顺序和大小，以及各个操作数是否在正确的位置上
 * // 这个测试是为了修复CALCITE-6964问题，该问题涉及操作数顺序不一致的bug
 *
 */
public class SqlCallOperandsTest { // 定义测试类SqlCallOperandsTest，用于测试SqlCall操作数的相关功能
  @Test void testSqlDeleteGetOperandsMatchWithSetOperand() { // 测试方法：验证SqlDelete的getOperands方法返回的操作数列表与通过setOperand设置的操作数是否匹配
    SqlDelete sqlDelete = // 创建一个SqlDelete对象，表示SQL DELETE语句，SqlDelete继承自SqlCall
        new SqlDelete(SqlParserPos.ZERO, new SqlIdentifier("table1", SqlParserPos.ZERO), // 构造函数参数：解析位置（ZERO表示未知位置）、目标表标识符"table1"
            null, // 别名参数，初始为null
            null, // 条件参数，初始为null
            null); // 源选择参数，初始为null
    SqlNode targetTable = new SqlIdentifier("table2", SqlParserPos.ZERO); // 创建目标表节点，使用标识符"table2"，SqlIdentifier表示SQL标识符（如表名、列名）
    final SqlIdentifier field1 = new SqlIdentifier("field1", SqlParserPos.ZERO); // 创建字段标识符"field1"，用于构建条件表达式
    SqlNode condition = // 创建条件节点，表示DELETE的WHERE条件
        SqlStdOperatorTable.EQUALS.createCall(SqlParserPos.ZERO, field1, // 使用等号操作符创建调用，参数：位置、左操作数field1
            SqlLiteral.createCharString("field1Value", SqlParserPos.ZERO)); // 右操作数为字符串字面量"field1Value"，构建条件：field1 = 'field1Value'
    SqlSelect sourceSelect = // 创建源选择节点，表示DELETE语句中的子查询（用于某些数据库的DELETE语法）
        new SqlSelect(SqlParserPos.ZERO, null, // 构造函数参数：位置、修饰符（如DISTINCT）
            SqlNodeList.of(field1), // 选择列表，包含field1字段
            null, // FROM子句，初始为null
            null, // WHERE子句，初始为null
            null, // GROUP BY子句，初始为null
            null, // HAVING子句，初始为null
            null, // 窗口子句，初始为null
            null, // ORDER BY子句，初始为null
            null, // 偏移量（LIMIT offset），初始为null
            null, // 获取行数（FETCH），初始为null
            null, // 锁子句，初始为null
            null); // 额外的null参数
    SqlIdentifier alias = new SqlIdentifier("alias", SqlParserPos.ZERO); // 创建别名标识符，用于DELETE语句中的表别名
    sqlDelete.setOperand(0, targetTable); // 设置SqlDelete的第0个操作数为targetTable，在SqlDelete中操作数顺序为：目标表、条件、源选择、别名
    sqlDelete.setOperand(1, condition); // 设置SqlDelete的第1个操作数为condition，即WHERE条件
    sqlDelete.setOperand(2, sourceSelect); // 设置SqlDelete的第2个操作数为sourceSelect，即源子查询
    sqlDelete.setOperand(3, alias); // 设置SqlDelete的第3个操作数为alias，即表别名
    final List<SqlNode> operandList = sqlDelete.getOperandList(); // 获取SqlDelete的所有操作数列表，返回一个List<SqlNode>
    // Verify if the operands are in the correct position in the operandList // 验证操作数是否在operandList中的正确位置
    assertThat(operandList, hasSize(4)); // 断言操作数列表的大小为4，确保所有操作数都被正确设置
    assertThat(sqlDelete.getTargetTable(), equalTo(operandList.get(0))); // 断言目标表（通过getTargetTable获取）等于操作数列表的第0个元素
    assertThat(sqlDelete.getCondition(), equalTo(operandList.get(1))); // 断言条件（通过getCondition获取）等于操作数列表的第1个元素
    assertThat(sqlDelete.getSourceSelect(), equalTo(operandList.get(2))); // 断言源选择（通过getSourceSelect获取）等于操作数列表的第2个元素
    assertThat(sqlDelete.getAlias(), equalTo(operandList.get(3))); // 断言别名（通过getAlias获取）等于操作数列表的第3个元素
  } // 测试方法结束，该测试确保了setOperand和getOperandList之间的操作数顺序和大小的一致性
} // 类定义结束
