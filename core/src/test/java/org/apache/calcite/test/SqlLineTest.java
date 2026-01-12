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
package org.apache.calcite.test; // 定义包名，该测试类位于org.apache.calcite.test包下

import org.apache.calcite.util.Bug; // 导入Bug工具类，用于处理已知的bug和升级问题
import org.apache.calcite.util.Pair; // 导入Pair工具类，用于存储键值对
import org.apache.calcite.util.Util; // 导入Util工具类，提供各种实用方法

import org.hamcrest.Matcher; // 导入Matcher接口，用于断言匹配
import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法

import java.io.ByteArrayOutputStream; // 导入ByteArrayOutputStream，用于捕获输出流
import java.io.File; // 导入File类，用于文件操作
import java.io.PrintStream; // 导入PrintStream，用于打印输出流
import java.io.PrintWriter; // 导入PrintWriter，用于写入文本文件
import java.nio.charset.StandardCharsets; // 导入StandardCharsets，指定字符编码
import java.util.ArrayList; // 导入ArrayList，动态数组列表
import java.util.Collections; // 导入Collections，集合工具类
import java.util.List; // 导入List接口，列表集合

import sqlline.SqlLine; // 导入SqlLine类，SQL命令行工具

import static org.hamcrest.CoreMatchers.equalTo; // 导入equalTo匹配器，判断相等
import static org.hamcrest.CoreMatchers.is; // 导入is匹配器，判断是否为
import static org.hamcrest.MatcherAssert.assertThat; // 导入assertThat断言方法

/**
 * Tests that we can invoke SqlLine on a Calcite connection. // 测试类说明：测试是否可以在Calcite连接上调用SqlLine
 * SqlLine是一个基于JDBC的SQL命令行工具，本测试类验证SqlLine与Calcite的集成是否正常工作
 * 主要测试通过SqlLine执行脚本文件的功能，包括使用-f和--run两种不同的执行方式
 */
class SqlLineTest { // 定义SqlLineTest测试类，用于测试SqlLine与Calcite的集成
  /**
   * Execute a script with "sqlline -f". // 方法说明：使用"sqlline -f"命令执行脚本
   *
   * @throws java.lang.Throwable On error // 可能抛出的异常
   * @return The stderr and stdout from running the script // 返回值：包含执行状态和输出字符串的键值对
   * @param args Script arguments // 参数：脚本参数数组
   */
  private static Pair<SqlLine.Status, String> run(String... args) // 定义私有静态方法run，用于执行SqlLine脚本并返回状态和输出
      throws Throwable { // 声明可能抛出异常
    SqlLine sqlline = new SqlLine(); // 创建SqlLine实例，用于执行SQL命令行操作
    ByteArrayOutputStream os = new ByteArrayOutputStream(); // 创建字节数组输出流，用于捕获SqlLine的输出
    PrintStream sqllineOutputStream = // 创建PrintStream对象，将输出重定向到字节数组输出流
        new PrintStream(os, false, StandardCharsets.UTF_8.name()); // 指定UTF-8字符编码，不自动刷新
    sqlline.setOutputStream(sqllineOutputStream); // 设置SqlLine的标准输出流，捕获所有输出
    sqlline.setErrorStream(sqllineOutputStream); // 设置SqlLine的错误输出流，也重定向到同一个输出流
    SqlLine.Status status = SqlLine.Status.OK; // 初始化执行状态为OK，表示默认成功

    Bug.upgrade("[sqlline-35] Make Sqlline.begin public"); // 标记一个已知的bug，等待SqlLine升级使begin方法变为public
    // TODO: status = sqlline.begin(args, null, false); // TODO注释：未来需要调用sqlline.begin方法来实际执行脚本

    return Pair.of(status, os.toString("UTF8")); // 返回包含执行状态和输出字符串的键值对，输出使用UTF-8编码
  }

  private static Pair<SqlLine.Status, String> runScript(File scriptFile, // 定义私有静态方法runScript，用于运行脚本文件
      boolean flag) throws Throwable { // 参数：脚本文件对象和标志位，标志位决定使用-f还是--run参数
    List<String> args = new ArrayList<>(); // 创建字符串列表，用于存储命令行参数
    Collections.addAll(args, "-u", "jdbc:calcite:", "-n", "sa", "-p", ""); // 添加基本的连接参数：-u指定URL，-n指定用户名，-p指定密码
    if (flag) { // 判断标志位是否为true
      args.add("-f"); // 如果为true，添加-f参数，表示以传统方式执行脚本文件
      args.add(scriptFile.getAbsolutePath()); // 添加脚本文件的绝对路径
    } else { // 如果标志位为false
      args.add("--run=" + scriptFile.getAbsolutePath()); // 添加--run参数，使用新的运行方式执行脚本文件
    }
    return run(args.toArray(new String[0])); // 调用run方法执行脚本，将参数列表转换为字符串数组传入
  }

  /**
   * Attempts to execute a simple script file with the -f option to SqlLine. // 方法说明：尝试使用-f选项执行简单脚本文件
   * Tests for presence of an expected pattern in the output (stdout or stderr). // 测试输出中是否存在期望的模式
   *
   * @param scriptText Script text // 参数：脚本文本内容
   * @param flag Command flag (--run or -f) // 参数：命令行标志，决定使用--run还是-f
   * @param statusMatcher Checks whether status is as expected // 参数：状态匹配器，验证执行状态是否符合预期
   * @param outputMatcher Checks whether output is as expected // 参数：输出匹配器，验证输出内容是否符合预期
   * @throws Exception on command execution error // 可能抛出的异常
   */
  private void checkScriptFile(String scriptText, boolean flag, // 定义私有方法checkScriptFile，用于检查脚本文件的执行结果
      Matcher<SqlLine.Status> statusMatcher, // 参数：状态匹配器，用于验证SqlLine执行状态
      Matcher<String> outputMatcher) throws Throwable { // 参数：输出匹配器，用于验证输出内容
    // Put the script content in a temp file // 注释：将脚本内容放入临时文件
    File scriptFile = File.createTempFile("foo", "temp"); // 创建临时文件，前缀为"foo"，后缀为"temp"
    scriptFile.deleteOnExit(); // 设置JVM退出时自动删除该临时文件
    try (PrintWriter w = Util.printWriter(scriptFile)) { // 使用try-with-resources创建PrintWriter，自动管理资源
      w.print(scriptText); // 将脚本文本写入临时文件
    } // 自动关闭PrintWriter资源

    Pair<SqlLine.Status, String> pair = runScript(scriptFile, flag); // 调用runScript方法执行脚本，获取状态和输出

    // Check output before status. It gives a better clue what went wrong. // 注释：先检查输出再检查状态，这样能更好地诊断问题
    assertThat(pair.right, outputMatcher); // 验证输出内容是否符合预期匹配器
    assertThat(pair.left, statusMatcher); // 验证执行状态是否符合预期匹配器
    final boolean delete = scriptFile.delete(); // 手动删除临时文件，并获取删除结果
    assertThat(delete, is(true)); // 断言文件删除成功
  }

  @Test void testSqlLine() throws Throwable { // 定义测试方法testSqlLine，使用@Test注解标记为测试用例
    checkScriptFile("!tables", false, equalTo(SqlLine.Status.OK), equalTo("")); // 调用checkScriptFile测试执行!tables命令，使用--run方式，期望状态为OK，输出为空字符串
  } // 测试方法结束
} // 类定义结束
