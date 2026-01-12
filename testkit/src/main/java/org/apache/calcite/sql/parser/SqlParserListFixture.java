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
package org.apache.calcite.sql.parser; // 指定当前类所在的包路径，位于org.apache.calcite.sql.parser包下

import org.apache.calcite.sql.SqlDialect; // 导入SQL方言类，用于处理不同数据库的SQL语法差异

import org.apache.calcite.sql.test.SqlTestFactory; // 导入SQL测试工厂类，用于创建测试所需的SQL解析器等对象

import com.google.common.collect.ImmutableList; // 导入Google Guava库的不可变列表类，用于存储不可变的列表集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值检查框架的注解，用于标记可能为null的字段

import java.util.function.UnaryOperator; // 导入Java函数式接口，用于定义对字符串进行一元操作的函数

/**
 * Helper class for building fluent code, // 这是一个辅助类，用于构建流畅的测试代码
 * similar to {@link SqlParserFixture}, but used to manipulate // 类似于SqlParserFixture类，但专门用于操作
 * a list of statements, such as // 一条SQL语句列表，例如
 * {@code sqlList("select * from a;").ok();}. // 使用链式调用方式验证SQL语句列表
 */
class SqlParserListFixture { // 定义SqlParserListFixture类，用于SQL语句列表的测试辅助
  final SqlTestFactory factory; // 测试工厂对象，用于创建测试所需的SQL解析器、验证器等组件，final表示不可变
  final SqlParserTest.Tester tester; // 测试器对象，用于执行实际的测试验证逻辑，final表示不可变
  final @Nullable SqlDialect dialect; // SQL方言对象，用于指定特定数据库的SQL语法规则，可为null表示使用默认方言，final表示不可变
  final boolean convertToLinux; // 布尔标志，指示是否将换行符转换为Linux格式（\n），final表示不可变
  final StringAndPos sap; // 字符串和位置对象，包含要测试的SQL语句列表及其位置信息，final表示不可变

  SqlParserListFixture(SqlTestFactory factory, SqlParserTest.Tester tester, // 构造方法，用于创建SqlParserListFixture实例，接收测试工厂、测试器等参数
      @Nullable SqlDialect dialect, boolean convertToLinux, // SQL方言参数（可为null）、换行符转换标志参数
      StringAndPos sap) { // 字符串和位置对象参数
    this.factory = factory; // 将传入的factory参数赋值给实例变量factory，用于后续测试中使用
    this.tester = tester; // 将传入的tester参数赋值给实例变量tester，用于执行测试验证
    this.dialect = dialect; // 将传入的dialect参数赋值给实例变量dialect，用于指定SQL方言
    this.convertToLinux = convertToLinux; // 将传入的convertToLinux参数赋值给实例变量convertToLinux，用于控制换行符转换
    this.sap = sap; // 将传入的sap参数赋值给实例变量sap，存储要测试的SQL语句列表和位置信息
  }

  public SqlParserListFixture ok(String... expected) { // 方法：验证SQL语句列表解析成功，参数expected是期望的解析结果字符串数组
    final UnaryOperator<String> converter = SqlParserTest.linux(convertToLinux); // 创建字符串转换器，根据convertToLinux标志决定是否将换行符转换为Linux格式
    tester.checkList(factory, sap, dialect, converter, // 调用测试器的checkList方法，验证SQL语句列表解析结果是否符合预期
        ImmutableList.copyOf(expected)); // 将期望结果数组转换为不可变列表，传递给测试器进行验证
    return this; // 返回当前对象，支持链式调用
  }

  public SqlParserListFixture fails(String expectedMsgPattern) { // 方法：验证SQL语句列表解析失败，参数expectedMsgPattern是期望的错误消息正则表达式
    tester.checkFails(factory, sap, true, expectedMsgPattern); // 调用测试器的checkFails方法，验证SQL语句列表解析是否抛出符合预期的错误
    return this; // 返回当前对象，支持链式调用
  }
}
