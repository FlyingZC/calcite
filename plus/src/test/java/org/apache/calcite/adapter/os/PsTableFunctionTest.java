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
// Apache Calcite 是一个动态数据管理框架，提供标准的 SQL 查询、优化和执行功能
// 本包 org.apache.calcite.adapter.os 包含与操作系统相关的适配器实现
package org.apache.calcite.adapter.os; // 声明当前类所在的包，属于操作系统适配器包的一部分

import com.google.common.collect.ImmutableList; // 导入 Google Guava 库的不可变列表类，用于创建不可修改的列表

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import java.util.ArrayList; // 导入 Java 集合框架的 ArrayList 类，用于动态数组
import java.util.Arrays; // 导入 Java 工具类的 Arrays 类，用于数组操作
import java.util.HashMap; // 导入 Java 集合框架的 HashMap 类，用于键值对映射
import java.util.List; // 导入 Java 集合框架的 List 接口，用于列表操作
import java.util.Map; // 导入 Java 集合框架的 Map 接口，用于映射操作

import static org.hamcrest.CoreMatchers.is; // 导入 Hamcrest 断言库的 is 匹配器，用于值比较
import static org.hamcrest.MatcherAssert.assertThat; // 导入 Hamcrest 断言库的 assertThat 方法，用于断言测试结果

/**
 * Unit tests for the ps (process status) table function.
 * // PsTableFunctionTest 类的单元测试类，用于测试 ps（进程状态）表函数
 * // ps 命令是 Unix/Linux 系统中用于显示当前进程状态的命令
 * // 本测试类主要测试 PsTableFunction 类中的 LineParser 解析器是否能正确解析 ps 命令的输出
 * // 特别是测试当用户名包含空格时，解析器是否能正确处理而不会抛出 NumberFormatException 异常
 */
class PsTableFunctionTest { // 定义 PsTableFunctionTest 测试类，用于测试 ps 表函数的功能

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6388">[CALCITE-6388]
   * PsTableFunction throws NumberFormatException when the 'user' column has spaces</a>.
   * // 测试用例：测试 PsTableFunction 在 'user' 列包含空格时是否会抛出 NumberFormatException 异常
   * // 这是一个回归测试，用于修复 CALCITE-6388 问题
   * // 问题描述：当 ps 命令输出的用户名包含空格时，LineParser 解析器会抛出数字格式异常
   * // 本测试验证了解析器能够正确处理包含空格的用户名、带点的用户名以及普通用户名
   */
  @Test void testPsInfoParsing() { // 定义测试方法 testPsInfoParsing，使用 @Test 注解标记为测试方法
    // 创建输入列表，用于存储模拟的 ps 命令输出行
    final List<String> input = new ArrayList<>(); // 创建一个 ArrayList 对象，用于存储测试输入数据（ps 命令的输出行）
    // 添加第一行测试数据：用户名为 "startup user"（包含空格），测试解析器是否能正确处理带空格的用户名
    input.add("startup user     56399     1 56399    0 S      0.0  0.0 410348128   6672 ??" // 添加第一行 ps 输出，用户名包含空格
        + "        3:25PM   0:00.22   501   501      0 /usr/lib exec/trustd"); // 继续第一行数据，包含进程路径（也包含空格）
    // 添加第二行测试数据：用户名为 "root"（普通用户名），测试解析器的正常功能
    input.add("root                 1   107   107    0 Ss     0.0  0.0 410142784   4016 ??" // 添加第二行 ps 输出，用户名为 root
        + "       11Apr24   0:52.32     0     0      0 " // 继续第二行数据，包含长路径
        + "/System/Library/PrivateFrameworks/Uninstall.framework/Resources/uninstalld"); // 完整的系统路径
    // 添加第三行测试数据：用户名为 "user.name"（包含点号），测试解析器是否能正确处理带点的用户名
    input.add("user.name     1  1661  1661    0 S      0.7  0.2 412094800  75232 ??       " // 添加第三行 ps 输出，用户名包含点号
        + "11Apr24 325:33.63 775020228 775020228      0 " // 继续第三行数据，包含大数值
        + "/System/Library/CoreServices/ControlCenter app/Contents/MacOS/ControlCenter"); // 完整的控制中心路径（包含空格）

    // 创建期望的输出列表，用于验证解析器的解析结果是否正确
    final List<List<Object>> output = // 创建一个不可变的列表，包含期望的解析结果
        ImmutableList.of( // 使用 ImmutableList.of 创建不可变列表，包含三个子列表（对应三行输入）
            // 第一行期望的解析结果：用户名、PID、PPID、PGID、WINPID、STATE、CPU、MEM、VSZ、RSS、TTY、STARTED、TIME、UID、GID、WAIT、COMMAND
            Arrays.asList("startup user", 56399, 1, 56399, 0, "S", 0, 0, "410348128", "6672", "??", // 第一行解析结果的前12个字段
            "3:25PM", 220L, "501", "501", "0", "/usr/lib exec/trustd"), // 第一行解析结果的后6个字段，注意 TIME 转换为毫秒（220L）
        // 第二行期望的解析结果
        Arrays.asList("root", 1, 107, 107, 0, "Ss", 0, 0, "410142784", "4016", "??", // 第二行解析结果的前12个字段
            "11Apr24", 52320L, "0", "0", "0", // 第二行解析结果的中间字段，注意 TIME 转换为毫秒（52320L）
            "/System/Library/PrivateFrameworks/Uninstall.framework/Resources/uninstalld"), // 第二行解析结果的 COMMAND 字段
        // 第三行期望的解析结果
        Arrays.asList("user.name", 1, 1661, 1661, 0, "S", 7, 2, "412094800", "75232", "??", // 第三行解析结果的前12个字段
            "11Apr24", 19533630L, "775020228", "775020228", "0", // 第三行解析结果的中间字段，注意 TIME 转换为毫秒（19533630L）
            "/System/Library/CoreServices/ControlCenter app/Contents/MacOS/ControlCenter")); // 第三行解析结果的 COMMAND 字段（包含空格）

    // 创建测试值映射，将输入行与期望的输出结果关联起来
    final Map<String, List<Object>> testValues = new HashMap<>(); // 创建一个 HashMap 对象，用于存储输入行与期望输出的映射关系
    // 遍历输入列表，将每行输入与其对应的期望输出关联起来
    for (int i = 0; i < input.size(); i++) { // 循环遍历输入列表，i 为索引
      testValues.put(input.get(i), output.get(i)); // 将输入行作为键，对应的期望输出作为值，存入 HashMap
    }

    // 创建 PsTableFunction.LineParser 解析器实例，用于解析 ps 命令的输出行
    final PsTableFunction.LineParser psLineParser = new PsTableFunction.LineParser(); // 创建 LineParser 对象，用于解析 ps 输出行
    // 遍历测试值映射，对每行输入进行解析并验证解析结果
    for (Map.Entry<String, List<Object>> e : testValues.entrySet()) { // 循环遍历 HashMap 的每个条目
      // 调用解析器的 apply 方法解析输入行，并断言解析结果与期望结果一致
      assertThat(psLineParser.apply(e.getKey()), is(e.getValue().toArray())); // 使用 assertThat 断言，验证解析结果是否等于期望结果
    }
  }
} // 类定义结束
