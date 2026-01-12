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
// Apache许可证声明，说明代码的开源许可信息
package org.apache.calcite.test.concurrent; // 定义包名，该接口位于org.apache.calcite.test.concurrent包下

/**
 * ConcurrentTestCommand represents a command, sequentially executed by
 * {@link ConcurrentTestCommandExecutor}, during a concurrency test
 *
 * <p>ConcurrentTestCommand instances are normally instantiated by the
 * {@link ConcurrentTestCommandGenerator} class.
 */
// ConcurrentTestCommand接口：表示一个在并发测试期间由ConcurrentTestCommandExecutor顺序执行的命令
// 这个接口是并发测试框架的核心接口之一，定义了测试命令的基本行为
// ConcurrentTestCommand实例通常由ConcurrentTestCommandGenerator类实例化
// 该接口支持正常测试和负向测试（预期失败的测试）
public interface ConcurrentTestCommand { // 定义公共接口ConcurrentTestCommand，所有测试命令都必须实现这个接口
  //~ Methods ---------------------------------------------------------------- // 方法分隔符，用于组织代码结构

  /**
   * Executes this command. The ConcurrentTestCommandExecutor provides
   * access to a JDBC connection and previously prepared statements.
   *
   * @param exec the ConcurrentTestCommandExecutor firing this command.
   *
   * @throws Exception to indicate a test failure
   *
   * @see ConcurrentTestCommandExecutor#getStatement()
   * @see ConcurrentTestCommandExecutor#setStatement(java.sql.Statement)
   */
  // execute方法：执行此命令的核心方法
  // ConcurrentTestCommandExecutor提供对JDBC连接和之前准备好的语句的访问
  // 参数exec：触发此命令的ConcurrentTestCommandExecutor执行器对象
  // 抛出Exception：用于指示测试失败
  // 该方法是命令执行的关键入口点，具体的命令逻辑由实现类提供
  void execute(ConcurrentTestCommandExecutor exec) throws Exception; // 定义执行命令的方法签名，接收执行器参数，可能抛出异常

  /**
   * Marks a command to show that it is expected to fail, and indicates how.
   * Used for negative tests. Normally when a command fails the embracing test
   * fails.
   * But when a marked command fails, the error is caught and inspected: if it
   * matches the expected error, the test continues. However if it does not
   * match, if another kind of exception is thrown, or if no exception is
   * caught, then the test fails. Assumes the error is indicated by a
   * java.sql.SQLException. Optionally checks for the expected error condition
   * by matching the error message against a regular expression. (Scans the
   * list of chained SQLExceptions).
   *
   * @param comment a brief description of the expected error
   * @param pattern null, or a regular expression that matches the expected
   * error message.
   */
  // markToFail方法：标记命令预期会失败，并指示如何失败
  // 用于负向测试（测试系统如何处理错误情况）
  // 通常情况下，当命令失败时，整个测试会失败
  // 但是当标记为预期失败的命令失败时，错误会被捕获并检查：如果匹配预期的错误，测试继续
  // 如果不匹配，或者抛出了其他类型的异常，或者没有捕获到异常，测试会失败
  // 假设错误由java.sql.SQLException指示
  // 可以选择通过将错误消息与正则表达式匹配来检查预期的错误条件（扫描链式SQLException列表）
  // 参数comment：预期错误的简要描述
  // 参数pattern：null，或者匹配预期错误消息的正则表达式
  ConcurrentTestCommand markToFail( // 定义标记命令预期失败的方法，返回ConcurrentTestCommand以支持链式调用
      String comment, // 预期错误的描述性注释，用于记录和测试报告
      String pattern); // 匹配预期错误消息的正则表达式模式，可以为null表示不进行模式匹配

  /**
   * Returns true if the command should fail. This allows special error
   * handling for expected failures that don't have patterns.
   *
   * @return true if command is expected to fail
   */
  // isFailureExpected方法：返回命令是否应该失败
  // 这允许对没有模式的预期失败进行特殊错误处理
  // 返回值：如果命令预期失败则返回true，否则返回false
  // 该方法用于在执行前或执行后判断命令的预期结果，以便进行适当的错误处理
  boolean isFailureExpected(); // 定义判断命令是否预期失败的方法，返回布尔值

  /**
   * Set this command to expect a patternless failure.
   */
  // markToFail方法：设置此命令预期失败但不指定失败模式
  // 这是一个重载方法，用于标记命令预期失败但不提供具体的错误模式匹配
  // 返回ConcurrentTestCommand以支持方法链式调用
  // 当只需要知道命令会失败，但不关心具体失败原因时使用此方法
  ConcurrentTestCommand markToFail(); // 定义无参数的标记失败方法，返回ConcurrentTestCommand支持链式调用

  //~ Inner Classes ---------------------------------------------------------- // 内部类分隔符，用于组织代码结构

  /**
   * Indicates that a command should have failed, but instead succeeded, which
   * is a test error.
   */
  // ShouldHaveFailedException内部类：表示命令应该失败但反而成功了，这是一个测试错误
  // 这是一个运行时异常，用于在负向测试中检测到意外成功的情况
  // 当一个被标记为预期失败的命令执行成功时，抛出此异常以指示测试问题
  class ShouldHaveFailedException extends RuntimeException { // 定义内部异常类，继承自RuntimeException
    private final String description; // 私有常量成员变量，存储异常的描述信息，用于说明应该失败但成功的原因

    public ShouldHaveFailedException(String description) { // 构造方法，接收描述字符串参数
      this.description = description; // 将传入的描述字符串赋值给成员变量description
    } // 构造方法结束

    public String getDescription() { // 公共getter方法，用于获取异常的描述信息
      return description; // 返回存储的描述字符串
    } // getter方法结束
  } // ShouldHaveFailedException内部类定义结束
} // ConcurrentTestCommand接口定义结束
