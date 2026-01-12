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
package org.apache.calcite.adapter.os; // 导入 org.apache.calcite.adapter.os 包，包含操作系统适配器相关类

import org.apache.calcite.config.CalciteConnectionProperty; // 导入 Calcite 连接属性配置类
import org.apache.calcite.config.Lex; // 导入词法分析器配置类，用于控制 SQL 解析的词法规则
import org.apache.calcite.runtime.Hook; // 导入 Hook 类，用于在运行时拦截和修改行为
import org.apache.calcite.sql.validate.SqlConformanceEnum; // 导入 SQL 合规性枚举类，定义 SQL 兼容性级别
import org.apache.calcite.test.CalciteAssert; // 导入 Calcite 测试断言工具类，用于简化测试代码
import org.apache.calcite.util.Holder; // 导入 Holder 工具类，用于持有和传递对象
import org.apache.calcite.util.Sources; // 导入 Sources 工具类，用于处理资源定位
import org.apache.calcite.util.TestUtil; // 导入测试工具类，提供测试辅助方法
import org.apache.calcite.util.Util; // 导入通用工具类，提供各种实用方法

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，标记测试方法

import java.io.ByteArrayInputStream; // 导入 ByteArrayInputStream 类，用于从字节数组创建输入流
import java.io.File; // 导入 File 类，用于文件和目录操作
import java.io.IOException; // 导入 IOException 类，处理 IO 异常
import java.io.InputStream; // 导入 InputStream 接口，表示字节输入流
import java.io.InputStreamReader; // 导入 InputStreamReader 类，将字节流转换为字符流
import java.io.OutputStream; // 导入 OutputStream 接口，表示字节输出流
import java.io.PrintWriter; // 导入 PrintWriter 类，用于格式化文本输出
import java.io.StringWriter; // 导入 StringWriter 类，用于将输出写入字符串缓冲区
import java.net.URL; // 导入 URL 类，用于统一资源定位符操作
import java.nio.charset.StandardCharsets; // 导入 StandardCharsets 类，提供标准字符集常量
import java.sql.SQLException; // 导入 SQLException 类，处理 SQL 异常
import java.util.function.Consumer; // 导入 Consumer 函数式接口，用于消费操作

import static org.apache.calcite.util.TestUtil.rethrow; // 静态导入 rethrow 方法，用于重新抛出异常

import static org.hamcrest.CoreMatchers.any; // 静态导入 any 匹配器，用于匹配任意对象
import static org.hamcrest.CoreMatchers.is; // 静态导入 is 匹配器，用于相等断言
import static org.hamcrest.CoreMatchers.notNullValue; // 静态导入 notNullValue 匹配器，用于非空断言
import static org.hamcrest.CoreMatchers.startsWith; // 静态导入 startsWith 匹配器，用于前缀断言
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入 assertThat 方法，用于断言
import static org.hamcrest.Matchers.hasToString; // 静态导入 hasToString 匹配器，用于 toString 断言
import static org.junit.jupiter.api.Assertions.assertNotNull; // 静态导入 assertNotNull 方法，用于非空断言
import static org.junit.jupiter.api.Assertions.fail; // 静态导入 fail 方法，用于让测试失败
import static org.junit.jupiter.api.Assumptions.assumeFalse; // 静态导入 assumeFalse 方法，用于条件假设
import static org.junit.jupiter.api.Assumptions.assumeTrue; // 静态导入 assumeTrue 方法，用于条件假设

import static java.util.Objects.requireNonNull; // 静态导入 requireNonNull 方法，用于检查非空

/**
 * Unit tests for the OS (operating system) adapter.
 * 操作系统适配器的单元测试类
 *
 * <p>Also please run the following tests manually, from your shell:
 * 也可以从命令行手动运行以下测试：
 *
 * <ul>
 *   <li>./sqlsh select \* from du - 测试磁盘使用情况查询
 *   <li>./sqlsh select \* from files - 测试文件系统查询
 *   <li>./sqlsh select \* from git_commits - 测试 Git 提交记录查询
 *   <li>./sqlsh select \* from ps - 测试进程列表查询
 *   <li>(echo cats; echo and dogs) | ./sqlsh select \* from stdin - 测试标准输入查询
 *   <li>./sqlsh select \* from vmstat - 测试虚拟内存统计查询
 * </ul>
 */
class OsAdapterTest { // 定义 OsAdapterTest 测试类，用于测试操作系统适配器功能
  private static final String OS_NAME = System.getProperty("os.name"); // 静态常量，存储当前操作系统的名称，通过系统属性获取

  private static boolean isWindows() { // 私有静态方法，判断当前操作系统是否为 Windows
    return OS_NAME.startsWith("Windows"); // 检查操作系统名称是否以 "Windows" 开头，返回布尔值
  }

  /** Returns whether there is a ".git" directory in this directory or in a
   * directory between this directory and root.
   * 私有静态方法，检查当前目录或其祖先目录中是否存在 ".git" 目录，用于判断是否在 Git 仓库中 */
  private static boolean hasGit() { // 方法开始，检查 Git 仓库是否存在
    assumeToolExists("git"); // 假设 git 工具存在，如果不存在则跳过测试
    final URL url = requireNonNull(OsAdapterTest.class.getResource("/")); // 获取当前类的根资源 URL，不能为空
    final String path = Sources.of(url).file().getAbsolutePath(); // 将 URL 转换为绝对路径字符串
    File f = new File(path); // 创建 File 对象表示当前路径
    for (;;) { // 无限循环，向上遍历目录树
      if (f == null || !f.exists()) { // 检查文件对象是否为空或不存在
        return false; // abandon hope - 放弃希望，返回 false 表示未找到 Git 仓库
      }
      File[] files = // 声明文件数组，用于存储匹配的文件
          f.listFiles((dir, name) -> name.equals(".git")); // 列出当前目录下名为 ".git" 的子目录
      if (files != null && files.length == 1) { // 检查是否找到且仅找到一个 ".git" 目录
        return true; // there is a ".git" subdirectory - 找到 ".git" 子目录，返回 true
      }
      f = f.getParentFile(); // 获取父目录，继续向上查找
    }
  }

  private static void assumeToolExists(String command) { // 私有静态方法，假设指定的命令工具存在
    assumeTrue(checkProcessExists(command), () -> command + " does not exist"); // 使用 assumeTrue 断言工具存在，如果不存在则跳过测试并显示消息
  }

  private static boolean checkProcessExists(String command) { // 私有静态方法，检查指定的命令工具是否存在和可用
    try { // 开始 try 块，捕获可能的异常
      Process process = new ProcessBuilder().command(command).start(); // 创建并启动一个新的进程来执行命令
      assertNotNull(process); // 断言进程对象不为空
      int errCode = process.waitFor(); // 等待进程执行完成并获取退出码
      assertThat(errCode, is(0)); // 断言退出码为 0，表示命令执行成功
      return true; // 返回 true，表示命令工具存在且可用
    } catch (AssertionError | IOException | InterruptedException e) { // 捕获断言错误、IO 异常或中断异常
      return false; // 返回 false，表示命令工具不存在或不可用
    }
  }

  @Test void testDu() { // 测试方法，测试 du（磁盘使用）表的功能
    assumeFalse(isWindows(), "Skip: the 'du' table does not work on Windows"); // 假设不是 Windows 系统，因为 du 命令在 Windows 上不可用
    assumeToolExists("du"); // 假设 du 命令工具存在
    sql("select * from du") // 执行 SQL 查询，从 du 表中选择所有数据
        .returns(r -> { // 定义结果集处理逻辑
          try { // 开始 try 块，捕获 SQL 异常
            assertThat(r.next(), is(true)); // 断言结果集有下一行数据
            assertThat(r.getInt(1), notNullValue()); // 断言第一列（磁盘使用量）不为空
            assertThat(r.getString(2), startsWith("./")); // 断言第二列（路径）以 "./" 开头
            assertThat(r.wasNull(), is(false)); // 断言上一列读取的值不为 null
          } catch (SQLException e) { // 捕获 SQL 异常
            throw rethrow(e); // 重新抛出异常
          }
        });
  }

  @Test void testDuFilterSortLimit() { // 测试方法，测试 du 表的过滤、排序和限制功能
    assumeFalse(isWindows(), "Skip: the 'du' table does not work on Windows"); // 假设不是 Windows 系统
    assumeToolExists("du"); // 假设 du 命令工具存在
    sql("select * from du where path like '%/src/test/java/%'\n" // 执行 SQL 查询，选择路径包含 '/src/test/java/' 的记录
        + "order by 1 limit 2") // 按第一列升序排序，并限制返回 2 行
        .returns(r -> { // 定义结果集处理逻辑
          try { // 开始 try 块，捕获 SQL 异常
            assertThat(r.next(), is(true)); // 断言结果集有第一行数据
            assertThat(r.getInt(1), notNullValue()); // 断言第一列不为空
            assertThat(r.getString(2), startsWith("./")); // 断言第二列以 "./" 开头
            assertThat(r.wasNull(), is(false)); // 断言上一列读取的值不为 null
            assertThat(r.next(), is(true)); // 断言结果集有第二行数据
            assertThat(r.next(), is(false)); // because of "limit 2" - 断言没有第三行数据，因为使用了 limit 2
          } catch (SQLException e) { // 捕获 SQL 异常
            throw rethrow(e); // 重新抛出异常
          }
        });
  }

  @Test void testFiles() { // 测试方法，测试 files 表的功能
    assumeFalse(isWindows(), "Skip: the 'files' table does not work on Windows"); // 假设不是 Windows 系统
    sql("select distinct type from files") // 执行 SQL 查询，选择文件类型列并去重
        .returnsUnordered("type=d", // 断言返回包含目录类型（'d'）
            "type=f"); // 断言返回包含文件类型（'f'）
  }

  @Test void testPs() { // 测试方法，测试 ps（进程状态）表的功能
    assumeFalse(isWindows(), "Skip: the 'ps' table does not work on Windows"); // 假设不是 Windows 系统
    assumeToolExists("ps"); // 假设 ps 命令工具存在
    sql("select * from ps") // 执行 SQL 查询，从 ps 表中选择所有数据
        .returns(r -> { // 定义结果集处理逻辑
          try { // 开始 try 块，捕获 SQL 异常
            assertThat(r.next(), is(true)); // 断言结果集有下一行数据
            final StringBuilder b = new StringBuilder(); // 创建字符串构建器，用于拼接结果
            final int c = r.getMetaData().getColumnCount(); // 获取结果集的列数
            for (int i = 0; i < c; i++) { // 遍历每一列
              b.append(r.getString(i + 1)).append(';'); // 读取列值并添加到字符串构建器，用分号分隔
              assertThat(r.wasNull(), is(false)); // 断言上一列读取的值不为 null
            }
            assertThat(b, hasToString(notNullValue())); // 断言构建的字符串不为空
          } catch (SQLException e) { // 捕获 SQL 异常
            throw rethrow(e); // 重新抛出异常
          }
        });
  }

  @Test void testPsDistinct() { // 测试方法，测试 ps 表的 distinct 查询功能
    assumeFalse(isWindows(), "Skip: the 'ps' table does not work on Windows"); // 假设不是 Windows 系统
    assumeToolExists("ps"); // 假设 ps 命令工具存在
    sql("select distinct `user` from ps") // 执行 SQL 查询，选择用户列并去重
        .returns(r -> { // 定义结果集处理逻辑
          try { // 开始 try 块，捕获 SQL 异常
            assertThat(r.next(), is(true)); // 断言结果集有下一行数据
            assertThat(r.getString(1), notNullValue()); // 断言第一列（用户名）不为空
            assertThat(r.wasNull(), is(false)); // 断言上一列读取的值不为 null
          } catch (SQLException e) { // 捕获 SQL 异常
            throw rethrow(e); // 重新抛出异常
          }
        });
  }

  @Test void testGitCommits() { // 测试方法，测试 git_commits 表的功能
    assumeTrue(hasGit(), "no git"); // 假设 Git 仓库存在
    sql("select count(*) from git_commits") // 执行 SQL 查询，统计 git_commits 表的记录数
        .returns(r -> { // 定义结果集处理逻辑
          try { // 开始 try 块，捕获 SQL 异常
            assertThat(r.next(), is(true)); // 断言结果集有下一行数据
            assertThat(r.getString(1), notNullValue()); // 断言第一列（计数结果）不为空
            assertThat(r.wasNull(), is(false)); // 断言上一列读取的值不为 null
          } catch (SQLException e) { // 捕获 SQL 异常
            throw TestUtil.rethrow(e); // 使用 TestUtil 重新抛出异常
          }
        });
  }

  @Test void testGitCommitsTop() { // 测试方法，测试 git_commits 表的分组、排序和限制功能
    assumeTrue(hasGit(), "no git"); // 假设 Git 仓库存在
    final String q = "select author from git_commits\n" // 构建 SQL 查询字符串，选择作者列
        + "group by 1 order by count(*) desc limit 2"; // 按作者分组，按提交次数降序排序，限制返回 2 行
    sql(q).returnsUnordered("author=Julian Hyde <julianhyde@gmail.com>", // 断言返回包含 Julian Hyde 的邮箱
        "author=Julian Hyde <jhyde@apache.org>"); // 断言返回包含 Julian Hyde 的 Apache 邮箱
  }

  @Test void testJps() { // 测试方法，测试 jps（Java 进程状态）表的功能
    assumeToolExists("jps"); // 假设 jps 命令工具存在
    final String q = "select pid, info from jps"; // 构建 SQL 查询字符串，选择进程 ID 和信息列
    sql(q).returns(r -> { // 定义结果集处理逻辑
      try { // 开始 try 块，捕获 SQL 异常
        if (r.next()) { // 检查结果集是否有下一行数据
          assertThat(r.getString(1), notNullValue()); // 断言第一列（进程 ID）不为空
          assertThat(r.getString(2), notNullValue()); // 断言第二列（进程信息）不为空
          assertThat(r.wasNull(), is(false)); // 断言上一列读取的值不为 null
        }
      } catch (SQLException e) { // 捕获 SQL 异常
        throw TestUtil.rethrow(e); // 使用 TestUtil 重新抛出异常
      }
    });
  }

  @Test void testVmstat() { // 测试方法，测试 vmstat（虚拟内存统计）表的功能
    assumeFalse(isWindows(), "Skip: the 'files' table does not work on Windows"); // 假设不是 Windows 系统
    assumeToolExists("vmstat"); // 假设 vmstat 命令工具存在
    sql("select * from vmstat") // 执行 SQL 查询，从 vmstat 表中选择所有数据
        .returns(r -> { // 定义结果集处理逻辑
          try { // 开始 try 块，捕获 SQL 异常
            assertThat(r.next(), is(true)); // 断言结果集有下一行数据
            final int c = r.getMetaData().getColumnCount(); // 获取结果集的列数
            for (int i = 0; i < c; i++) { // 遍历每一列
              assertThat(r.getLong(i + 1), notNullValue()); // 断言列值不为空
              assertThat(r.wasNull(), is(false)); // 断言上一列读取的值不为 null
            }
          } catch (SQLException e) { // 捕获 SQL 异常
            throw rethrow(e); // 重新抛出异常
          }
        });
  }

  @Test void testStdin() throws SQLException { // 测试方法，测试 stdin（标准输入）表的功能
    try (Hook.Closeable ignore = // 使用 try-with-resources 管理 Hook 生命周期
        Hook.STANDARD_STREAMS.addThread((Consumer<Holder<Object[]>>) o -> { // 添加标准流钩子，拦截标准输入输出
          final Object[] values = o.get(); // 获取标准流数组 [stdin, stdout, stderr]
          final InputStream in = (InputStream) values[0]; // 获取标准输入流
          final String s = "First line\n" // 定义测试输入字符串
              + "Second line"; // 第二行内容
          final ByteArrayInputStream in2 = // 创建新的字节输入流
              new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)); // 将字符串转换为 UTF-8 字节数组并创建输入流
          final OutputStream out = (OutputStream) values[1]; // 获取标准输出流
          final OutputStream err = (OutputStream) values[2]; // 获取标准错误流
          o.set(new Object[] {in2, out, err}); // 替换标准流数组，使用新的输入流
        })) { // Hook 设置完成
      assertThat(foo("select count(*) as c from stdin"), is("2\n")); // 执行 SQL 查询，断言结果为 "2\n"，表示两行输入
    }
  }

  @Test void testStdinExplain() { // 测试方法，测试 stdin 表的执行计划功能
    // Can't execute stdin, because junit's stdin never ends;
    // 无法真正执行 stdin，因为 JUnit 的标准输入永远不会结束
    // so just run explain
    // 所以只运行 explain 查看执行计划
    final String explain = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableAggregate(group=[{}], c=[COUNT()])\n" // 聚合操作，计算行数
        + "  EnumerableTableFunctionScan(invocation=[stdin(true)], " // 表函数扫描，调用 stdin 函数
        + "rowType=[RecordType(INTEGER ordinal, VARCHAR line)], " // 行类型包含序号和行内容
        + "elementType=[class [Ljava.lang.Object;])"; // 元素类型为对象数组
    sql("select count(*) as c from stdin") // 执行 SQL 查询
        .explainContains(explain); // 断言执行计划包含预期的字符串
  }

  @Test void testSqlShellFormat() throws SQLException { // 测试方法，测试 SQL Shell 的不同输出格式
    final String q = "select * from (values (-1, true, 'a')," // 构建 SQL 查询字符串，使用 values 子句创建测试数据
        + " (2, false, 'b, c')," // 第二行数据
        + " (3, unknown, cast(null as char(1)))) as t(x, y, z)"; // 第三行数据，包含 null 值
    final String empty = q + " where false"; // 构建返回空结果的查询

    final String spacedOut = "-1 true a   \n" // 定义 spaced 格式的预期输出
        + "2 false b, c\n" // 第二行输出
        + "3 null null\n"; // 第三行输出
    assertThat(foo("-o", "spaced", q), is(spacedOut)); // 断言 spaced 格式输出正确

    assertThat(foo("-o", "spaced", empty), is("")); // 断言 empty 查询的 spaced 格式输出为空字符串

    // default is 'spaced'
    // 默认格式是 'spaced'
    assertThat(foo(q), is(spacedOut)); // 断言不指定格式时默认使用 spaced 格式

    final String headersOut = "x y z\n" // 定义 headers 格式的预期输出，包含列头
        + spacedOut; // 加上数据行
    assertThat(foo("-o", "headers", q), is(headersOut)); // 断言 headers 格式输出正确

    final String headersEmptyOut = "x y z\n"; // 定义 empty 查询的 headers 格式预期输出
    assertThat(foo("-o", "headers", empty), is(headersEmptyOut)); // 断言 empty 查询的 headers 格式输出正确

    final String jsonOut = "[\n" // 定义 json 格式的预期输出
        + "{\n" // 第一个对象开始
        + "  \"x\": -1,\n" // x 字段
        + "  \"y\": true,\n" // y 字段
        + "  \"z\": \"a   \"\n" // z 字段
        + "},\n" // 第一个对象结束
        + "{\n" // 第二个对象开始
        + "  \"x\": 2,\n" // x 字段
        + "  \"y\": false,\n" // y 字段
        + "  \"z\": \"b, c\"\n" // z 字段
        + "},\n" // 第二个对象结束
        + "{\n" // 第三个对象开始
        + "  \"x\": 3,\n" // x 字段
        + "  \"y\": null,\n" // y 字段为 null
        + "  \"z\": null\n" // z 字段为 null
        + "}\n" // 第三个对象结束
        + "]\n"; // JSON 数组结束
    assertThat(foo("-o", "json", q), is(jsonOut)); // 断言 json 格式输出正确

    final String jsonEmptyOut = "[\n" // 定义 empty 查询的 json 格式预期输出
        + "]\n"; // 空数组
    assertThat(foo("-o", "json", empty), is(jsonEmptyOut)); // 断言 empty 查询的 json 格式输出正确

    final String csvEmptyOut = "[\n" // 定义 empty 查询的 csv 格式预期输出
        + "]\n"; // 空数组
    assertThat(foo("-o", "json", empty), is(csvEmptyOut)); // 断言 empty 查询的 json 格式输出正确

    final String csvOut = "x,y,z\n" // 定义 csv 格式的预期输出，包含列头
        + "-1,true,a   \n" // 第一行数据
        + "2,false,\"b, c\"\n" // 第二行数据，包含逗号需要引号
        + "3,,"; // 第三行数据，两个 null 值
    assertThat(foo("-o", "csv", q), is(csvOut)); // 断言 csv 格式输出正确

    final String mysqlOut = "" // 定义 mysql 格式的预期输出
        + "+----+-------+------+\n" // 表格边框
        + "|  x | y     | z    |\n" // 列头
        + "+----+-------+------+\n" // 表格分隔线
        + "| -1 | true  | a    |\n" // 第一行数据
        + "|  2 | false | b, c |\n" // 第二行数据
        + "|  3 |       |      |\n" // 第三行数据，null 显示为空
        + "+----+-------+------+\n" // 表格边框
        + "(3 rows)\n" // 行数统计
        + "\n"; // 空行
    assertThat(foo("-o", "mysql", q), is(mysqlOut)); // 断言 mysql 格式输出正确

    final String mysqlEmptyOut = "" // 定义 empty 查询的 mysql 格式预期输出
        + "+---+---+---+\n" // 表格边框
        + "| x | y | z |\n" // 列头
        + "+---+---+---+\n" // 表格分隔线
        + "+---+---+---+\n" // 表格边框
        + "(0 rows)\n" // 行数统计
        + "\n"; // 空行
    assertThat(foo("-o", "mysql", empty), is(mysqlEmptyOut)); // 断言 empty 查询的 mysql 格式输出正确
  }

  private String foo(String... args) throws SQLException { // 私有方法，执行 SQL Shell 并返回输出结果
    final ByteArrayInputStream inStream = new ByteArrayInputStream(new byte[0]); // 创建空的字节输入流，模拟标准输入
    final InputStreamReader in = // 创建输入流读取器
        new InputStreamReader(inStream, StandardCharsets.UTF_8); // 使用 UTF-8 编码
    final StringWriter outSw = new StringWriter(); // 创建字符串写入器，用于捕获标准输出
    final PrintWriter out = new PrintWriter(outSw); // 创建打印写入器，包装字符串写入器
    final StringWriter errSw = new StringWriter(); // 创建字符串写入器，用于捕获标准错误
    final PrintWriter err = new PrintWriter(errSw); // 创建打印写入器，包装字符串写入器
    new SqlShell(in, out, err, args).run(); // 创建 SqlShell 实例并运行，传入输入输出流和参数
    return Util.toLinux(outSw.toString()); // 将输出转换为 Linux 格式（换行符统一为 \n）并返回
  }

  @Test void testSqlShellHelp() throws SQLException { // 测试方法，测试 SQL Shell 的帮助功能
    final String help = "Usage: sqlsh [OPTION]... SQL\n" // 定义预期的帮助信息字符串
        + "Execute a SQL command\n" // 命令描述
        + "\n" // 空行
        + "Options:\n" // 选项说明开始
        + "  -o FORMAT  Print output in FORMAT; options are 'spaced' (the " // -o 选项说明
        + "default), 'csv',\n" // 默认格式和其他格式选项
        + "             'headers', 'json', 'mysql'\n" // 更多格式选项
        + "  -h --help  Print this help\n"; // -h 和 --help 选项说明
    final String q = "select 1"; // 定义一个简单的 SQL 查询
    assertThat(foo("--help", q), is(help)); // 断言 --help 选项输出正确的帮助信息

    assertThat(foo("-h", q), is(help)); // 断言 -h 选项输出正确的帮助信息

    try { // 开始 try 块，测试错误格式
      final String s = foo("-o", "bad", q); // 尝试使用不存在的格式
      fail("expected exception, got " + s); // 如果没有抛出异常，测试失败
    } catch (RuntimeException e) { // 捕获运行时异常
      assertThat(e.getMessage(), is("unknown format: bad")); // 断言异常消息正确
    }
  }

  static CalciteAssert.AssertQuery sql(String sql) { // 静态方法，创建并返回 CalciteAssert 查询断言对象
    return CalciteAssert.that() // 创建 CalciteAssert 构建器
        .withModel(SqlShell.MODEL) // 设置模型配置，使用 SqlShell 的模型
        .with(CalciteConnectionProperty.LEX, Lex.JAVA) // 设置词法分析器为 Java 模式
        .with(CalciteConnectionProperty.CONFORMANCE, SqlConformanceEnum.LENIENT) // 设置 SQL 合规性为宽松模式
        .query(sql); // 创建查询断言对象，传入 SQL 语句
  }

  private void checkOsQuery(String tableName, int colSize) { // 私有方法，检查操作系统查询的基本功能
    final String q = "select * from " + tableName; // 构建 SQL 查询字符串，选择指定表的所有数据
    sql(q).returns(r -> { // 定义结果集处理逻辑
      try { // 开始 try 块，捕获 SQL 异常
        if (r.next()) { // 检查结果集是否有下一行数据
          for (int i = 1; i <= colSize; i++) { // 遍历所有列
            if (r.getString(i) != null) { // 检查列值是否不为 null
              assertThat(r.getString(i), any(String.class)); // 断言列值是字符串类型
            }
          }
          assertThat(r.wasNull(), is(false)); // 断言上一列读取的值不为 null
        }
      } catch (SQLException e) { // 捕获 SQL 异常
        throw TestUtil.rethrow(e); // 使用 TestUtil 重新抛出异常
      }
    });
  }

  @Test void testSystemInfo() { // 测试方法，测试 system_info 表的功能
    checkOsQuery("system_info", 18); // 检查 system_info 表，期望有 18 列
  }

  @Test void testJavaInfo() { // 测试方法，测试 java_info 表的功能
    checkOsQuery("java_info", 18); // 检查 java_info 表，期望有 18 列
  }

  @Test void testCpuInfo() { // 测试方法，测试 cpu_info 表的功能
    checkOsQuery("cpu_info", 9); // 检查 cpu_info 表，期望有 9 列
  }

  @Test void testCpuTime() { // 测试方法，测试 cpu_time 表的功能
    checkOsQuery("cpu_time", 8); // 检查 cpu_time 表，期望有 8 列
  }

  @Test void testOsVersion() { // 测试方法，测试 os_version 表的功能
    checkOsQuery("os_version", 5); // 检查 os_version 表，期望有 5 列
  }

  @Test void testMemoryInfo() { // 测试方法，测试 memory_info 表的功能
    checkOsQuery("memory_info", 8); // 检查 memory_info 表，期望有 8 列
  }

  @Test void testInterfaceAddresses() { // 测试方法，测试 interface_addresses 表的功能
    checkOsQuery("interface_addresses", 5); // 检查 interface_addresses 表，期望有 5 列
  }

  @Test void testInterfaceDetails() { // 测试方法，测试 interface_details 表的功能
    checkOsQuery("interface_details", 13); // 检查 interface_details 表，期望有 13 列
  }

  @Test void testMounts() { // 测试方法，测试 mounts 表的功能
    checkOsQuery("mounts", 8); // 检查 mounts 表，期望有 8 列
  }
}
