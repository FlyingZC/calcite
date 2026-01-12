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
package org.apache.calcite.test;

import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.StringAndPos;
import org.apache.calcite.sql.test.AbstractSqlTester;
import org.apache.calcite.sql.test.SqlTestFactory;
import org.apache.calcite.sql.test.SqlTests;
import org.apache.calcite.sql.validate.SqlValidator;

import org.checkerframework.checker.nullness.qual.Nullable;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tester of {@link SqlValidator} and runtime execution of the input SQL.
 * Sql验证器和SQL运行时执行的测试器，用于测试SQL语句的验证和运行时执行
 */
class SqlRuntimeTester extends AbstractSqlTester { // 继承AbstractSqlTester抽象类，实现SQL验证器和运行时执行的测试功能
  SqlRuntimeTester() { // 默认构造方法，创建SqlRuntimeTester实例
  }

  @Override public void checkFails(SqlTestFactory factory, StringAndPos sap, // 重写checkFails方法，检查SQL执行是否失败，factory: SQL测试工厂，sap: SQL字符串和位置信息，expectedError: 期望的错误消息，runtime: 是否在运行时检查
      String expectedError, boolean runtime) { // expectedError: 期望的错误消息，runtime: 是否在运行时检查
    final StringAndPos sap2 = StringAndPos.of(buildQuery(sap.addCarets())); // 构建完整查询语句并添加脱字符(^)标记错误位置
    assertExceptionIsThrown(factory, sap2, expectedError, runtime); // 断言抛出异常，验证是否产生期望的错误
  }

  @Override public void checkAggFails(SqlTestFactory factory, // 重写checkAggFails方法，检查聚合函数执行是否失败，factory: SQL测试工厂
      String expr, // expr: 聚合表达式
      String[] inputValues, // inputValues: 输入值数组
      String expectedError, // expectedError: 期望的错误消息
      boolean runtime) { // runtime: 是否在运行时检查
    String query = // 生成聚合查询语句
        SqlTests.generateAggQuery(expr, inputValues); // 使用SqlTests工具类生成聚合查询
    final StringAndPos sap = StringAndPos.of(query); // 将查询字符串转换为StringAndPos对象
    assertExceptionIsThrown(factory, sap, expectedError, runtime); // 断言抛出异常，验证是否产生期望的错误
  }

  @Override public void assertExceptionIsThrown(SqlTestFactory factory, // 重写assertExceptionIsThrown方法，断言抛出异常（默认在验证阶段检查），factory: SQL测试工厂，sap: SQL字符串和位置信息，expectedMsgPattern: 期望的错误消息模式
      StringAndPos sap, @Nullable String expectedMsgPattern) { // expectedMsgPattern: 期望的错误消息模式，可为空
    assertExceptionIsThrown(factory, sap, expectedMsgPattern, false); // 调用重载方法，runtime参数设为false，表示在验证阶段检查
  }

  public void assertExceptionIsThrown(SqlTestFactory factory, // 断言抛出异常的完整方法，factory: SQL测试工厂，sap: SQL字符串和位置信息，expectedMsgPattern: 期望的错误消息模式，runtime: 是否在运行时检查
      StringAndPos sap, @Nullable String expectedMsgPattern, boolean runtime) { // expectedMsgPattern: 期望的错误消息模式，可为空，runtime: 是否在运行时检查
    final SqlNode sqlNode; // 声明SQL节点变量，用于存储解析后的SQL抽象语法树
    try { // 尝试解析SQL语句
      sqlNode = parseQuery(factory, sap.sql); // 解析SQL字符串为SqlNode节点
    } catch (Throwable e) { // 捕获解析过程中抛出的异常
      checkParseEx(e, expectedMsgPattern, sap); // 检查解析异常是否符合期望的错误模式
      return; // 如果是解析阶段异常，直接返回
    }

    Throwable thrown = null; // 声明抛出的异常变量，初始为null
    final SqlTests.Stage stage; // 声明测试阶段变量，用于标识是在哪个阶段检查异常
    final SqlValidator validator = factory.createValidator(); // 从工厂创建SQL验证器实例
    if (runtime) { // 如果是运行时检查模式
      stage = SqlTests.Stage.RUNTIME; // 设置测试阶段为运行时阶段
      SqlNode validated = validator.validate(sqlNode); // 验证SQL节点，返回验证后的SQL节点
      assertNotNull(validated); // 断言验证后的节点不为空
      try { // 尝试执行SQL检查
        check(factory, sap.sql, SqlTests.ANY_TYPE_CHECKER, // 执行SQL检查，使用任意类型检查器
            SqlTests.ANY_PARAMETER_CHECKER, SqlTests.ANY_RESULT_CHECKER); // 使用任意参数检查器和任意结果检查器
      } catch (Throwable ex) { // 捕获运行时检查过程中抛出的异常
        // get the real exception in runtime check // 获取运行时检查中的真实异常
        thrown = ex; // 将捕获的异常赋值给thrown变量
      }
    } else { // 如果不是运行时检查模式（即验证阶段检查）
      stage = SqlTests.Stage.VALIDATE; // 设置测试阶段为验证阶段
      try { // 尝试验证SQL节点
        validator.validate(sqlNode); // 调用验证器验证SQL节点
      } catch (Throwable ex) { // 捕获验证过程中抛出的异常
        thrown = ex; // 将捕获的异常赋值给thrown变量
      }
    }

    SqlTests.checkEx(thrown, expectedMsgPattern, sap, stage); // 使用SqlTests工具类检查异常是否符合期望的错误模式
  }
}
