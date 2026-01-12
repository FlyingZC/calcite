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
// 指定当前类所属的包路径，这是Calcite SQL类型处理相关的测试包
package org.apache.calcite.sql.type;

// 导入SqlFunction类，用于表示SQL函数
import org.apache.calcite.sql.SqlFunction;
// 导入SqlFunctionCategory类，用于定义SQL函数的分类
import org.apache.calcite.sql.SqlFunctionCategory;
// 导入SqlKind类，用于定义SQL操作符的种类
import org.apache.calcite.sql.SqlKind;
// 导入SqlOperator类，是所有SQL操作符的基类
import org.apache.calcite.sql.SqlOperator;
// 导入SqlOperatorTable接口，用于提供SQL操作符的查找表
import org.apache.calcite.sql.SqlOperatorTable;
// 导入SqlStdOperatorTable类，包含标准SQL操作符的定义
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
// 导入SqlOperatorFixture类，用于SQL操作符测试的测试装置
import org.apache.calcite.sql.test.SqlOperatorFixture;
// 导入SqlOperatorTables工具类，用于创建SQL操作符表
import org.apache.calcite.sql.util.SqlOperatorTables;
// 导入Fixtures工具类，用于创建测试装置
import org.apache.calcite.test.Fixtures;

// 导入Jupiter的Test注解，用于标记测试方法
import org.junit.jupiter.api.Test;

/**
 * Tests for operand checkers in {@link OperandTypes}.
 * 本类是OperandTypes中操作数检查器的测试类
 * OperandTypes是Calcite中用于检查SQL操作符操作数类型的核心工具类
 * 本测试类主要验证操作数类型检查器的各种场景，确保类型检查逻辑正确
 * 测试重点包括：操作数序列检查、类型家族检查、OR组合检查等
 */
public class OperandTypesTest { // 定义OperandTypesTest测试类，使用JUnit 5进行测试

  // 定义测试方法testSequenceOfFamily，用于测试操作数序列的类型家族检查
  // 该方法验证了OperandTypes.sequence方法在处理类型家族时的正确性
  // 测试场景：验证函数F的第一个参数必须是STRING类型，第二个参数必须是TIMESTAMP或DATE类型
  @Test void testSequenceOfFamily() throws Exception { // 测试方法，可能抛出异常
    // Test for https://issues.apache.org/jira/browse/CALCITE-5479. The original checker that
    // inspired this test was doing "and(family(STRING), LITERAL)". We tweak this a bit here because
    // the test fixture does not preserve literals.
    // 上面注释说明：这是针对CALCITE-5479问题的测试，原始检查器是"and(family(STRING), LITERAL)"
    // 这里进行了调整，因为测试装置不保留字面量信息
    // 创建操作数类型检查器fChecker，使用OperandTypes.sequence方法定义参数序列
    // sequence方法定义了一个参数序列：第一个参数必须是STRING类型，第二个参数必须是TIMESTAMP或DATE类型
    // "F(<STRING>, <TIMESTAMP or DATE>)"是类型签名的描述字符串，用于错误消息
    final SqlOperandTypeChecker fChecker = // 声明最终的操作数类型检查器变量
        OperandTypes.sequence("F(<STRING>, <TIMESTAMP or DATE>)", // 调用sequence方法，传入类型签名描述
            OperandTypes.STRING, // 第一个参数类型检查器：必须是STRING类型
            OperandTypes.or(OperandTypes.TIMESTAMP, OperandTypes.DATE)); // 第二个参数类型检查器：必须是TIMESTAMP或DATE类型之一
    // 创建自定义SQL函数fOperator，名字为"F"
    final SqlOperator fOperator = // 声明最终的SQL操作符变量
        new SqlFunction("F", // 函数名称为"F"
            SqlKind.OTHER_FUNCTION, // 函数种类为OTHER_FUNCTION（其他函数）
            ReturnTypes.BIGINT, // 返回类型为BIGINT
            null, // 操作数类型推断器为null
            fChecker, // 使用上面定义的操作数类型检查器
            SqlFunctionCategory.USER_DEFINED_FUNCTION); // 函数分类为用户定义函数
    // 创建SQL操作符表operatorTable，包含自定义函数F和标准ROW操作符
    final SqlOperatorTable operatorTable = // 声明最终的SQL操作符表变量
        SqlOperatorTables.of(fOperator, SqlStdOperatorTable.ROW); // 使用of方法创建操作符表，包含fOperator和ROW
    // 使用try-with-resources创建SQL操作符测试装置f
    // Fixtures.forOperators(false)创建测试装置，false表示不启用验证
    // .withOperatorTable(operatorTable)设置操作符表为上面创建的operatorTable
    try (SqlOperatorFixture f = Fixtures.forOperators(false).withOperatorTable(operatorTable)) { // 创建测试装置并自动管理资源
      f.setFor(fOperator); // 设置测试装置当前测试的操作符为fOperator
      f.checkType("F('foo', TIMESTAMP '2000-01-01 00:00:00')", "BIGINT NOT NULL"); // 验证F函数调用返回BIGINT NOT NULL类型，第一个参数是字符串'foo'，第二个参数是TIMESTAMP字面量
      f.checkFails("^F('foo', 1)^", // 验证F函数调用应该失败，因为第二个参数是整数1而不是TIMESTAMP或DATE
          "Cannot apply 'F' to arguments of type 'F\\(<CHAR\\(3\\)>, <INTEGER>\\)'\\. Supported " // 期望的错误消息第一部分：说明不能应用F函数到CHAR(3)和INTEGER类型的参数
              + "form\\(s\\): F\\(<STRING>, <TIMESTAMP or DATE>\\)", false); // 期望的错误消息第二部分：说明支持的函数签名形式，false表示不检查精确匹配
    } // try-with-resources自动关闭测试装置f

  } // 测试方法结束

} // OperandTypesTest类定义结束
