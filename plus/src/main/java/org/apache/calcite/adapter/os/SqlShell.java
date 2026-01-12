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
 */ // Apache许可证声明：本文件遵循Apache 2.0许可证
package org.apache.calcite.adapter.os; // 包声明：SqlShell类属于org.apache.calcite.adapter.os包，Calcite的操作系统适配器包

import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口：用于遍历LINQ风格的枚举集合
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类：提供LINQ风格的查询操作
import org.apache.calcite.util.JsonBuilder; // 导入JsonBuilder工具类：用于构建JSON字符串

import com.google.common.collect.ImmutableList; // 导入ImmutableList：Google Guava提供的不可变列表
import com.google.common.collect.Maps; // 导入Maps：Google Guava提供的Map工具类

import java.io.InputStreamReader; // 导入InputStreamReader：将字节流转换为字符流
import java.io.OutputStreamWriter; // 导入OutputStreamWriter：将字符流转换为字节流
import java.io.PrintWriter; // 导入PrintWriter：用于格式化输出文本
import java.nio.charset.StandardCharsets; // 导入StandardCharsets：标准字符集常量
import java.sql.Connection; // 导入Connection：JDBC数据库连接接口
import java.sql.DriverManager; // 导入DriverManager：JDBC驱动管理器
import java.sql.ResultSet; // 导入ResultSet：JDBC查询结果集接口
import java.sql.ResultSetMetaData; // 导入ResultSetMetaData：结果集元数据接口
import java.sql.SQLException; // 导入SQLException：SQL异常类
import java.sql.Statement; // 导入Statement：JDBC语句执行接口
import java.sql.Types; // 导入Types：JDBC类型常量定义
import java.util.ArrayList; // 导入ArrayList：动态数组实现
import java.util.LinkedHashMap; // 导入LinkedHashMap：保持插入顺序的Map实现
import java.util.List; // 导入List：集合接口
import java.util.Locale; // 导入Locale：地区信息类
import java.util.Map; // 导入Map：键值对集合接口
import java.util.Set; // 导入Set：不重复元素集合接口

import static java.util.Objects.requireNonNull; // 导入requireNonNull：Objects类的静态方法，用于参数非空检查

/**
 * Command that executes its arguments as a SQL query
 * against Calcite's OS adapter.
 */
// SqlShell类：这是一个命令行工具类，用于执行SQL查询并访问Calcite的OS（操作系统）适配器
// 它允许用户通过SQL查询来获取操作系统级别的信息，如进程信息、文件系统、网络接口等
// 该类支持多种输出格式（spaced、csv、headers、json、mysql）来展示查询结果
public class SqlShell {
  static final String MODEL = model(); // 静态常量：存储Calcite模型的JSON配置字符串，该模型定义了OS适配器的schema、表和函数

  private final List<String> args; // 成员变量：存储命令行参数列表，用于解析SQL查询和选项
  @SuppressWarnings("unused")
  private final InputStreamReader in; // 成员变量：输入流读取器，用于读取标准输入（当前未使用）
  private final PrintWriter out; // 成员变量：打印写入器，用于向标准输出写入查询结果
  @SuppressWarnings("unused")
  private final PrintWriter err; // 成员变量：打印写入器，用于向标准错误输出写入错误信息（当前未使用）

  SqlShell(InputStreamReader in, PrintWriter out,
      PrintWriter err, String... args) { // 构造方法：初始化SqlShell实例，设置输入输出流和命令行参数
    this.args = ImmutableList.copyOf(args); // 将可变参数args转换为不可变的列表，确保参数不会被修改
    this.in = requireNonNull(in, "in"); // 验证输入流不为null，否则抛出NullPointerException
    this.out = requireNonNull(out, "out"); // 验证输出流不为null，否则抛出NullPointerException
    this.err = requireNonNull(err, "err"); // 验证错误输出流不为null，否则抛出NullPointerException
  }

  private static String model() { // 静态方法：生成Calcite模型的JSON配置字符串，定义OS适配器的schema结构
    final StringBuilder b = new StringBuilder(); // 创建StringBuilder用于构建JSON字符串
    b.append("{\n") // 开始JSON对象
        .append("  version: '1.0',\n") // 模型版本号
        .append("  defaultSchema: 'os',\n") // 默认schema名称为"os"
        .append("   schemas: [\n") // schemas数组开始
        .append("     {\n") // 第一个schema对象开始
        .append("       \"name\": \"os\",\n") // schema名称为"os"
        .append("       \"tables\": [ {\n"); // tables数组开始，包含多个表定义
    addView(b, "du", "select *, \"size_k\" * 1024 as \"size_b\"\n" // 添加du视图：磁盘使用情况，包含原始数据和计算的字节大小
            + "from table(\"du\"(true))");
    addView(b, "files", "select * from table(\"files\"('.'))"); // 添加files视图：当前目录的文件列表
    addView(b, "git_commits", "select * from table(\"git_commits\"(true))"); // 添加git_commits视图：Git提交记录
    addView(b, "jps", "select * from table(\"jps\"(true))"); // 添加jps视图：Java进程列表
    addView(b, "ps", "select * from table(\"ps\"(true))"); // 添加ps视图：系统进程列表
    addView(b, "stdin", "select * from table(\"stdin\"(true))"); // 添加stdin视图：标准输入数据
    addView(b, "vmstat", "select * from table(\"vmstat\"(true))"); // 添加vmstat视图：虚拟内存统计信息
    addView(b, "system_info", "select * from table(\"system_info\"(true))"); // 添加system_info视图：系统信息
    addView(b, "java_info", "select * from table(\"java_info\"(true))"); // 添加java_info视图：Java运行时信息
    addView(b, "os_version", "select * from table(\"os_version\"(true))"); // 添加os_version视图：操作系统版本信息
    addView(b, "memory_info", "select * from table(\"memory_info\"(true))"); // 添加memory_info视图：内存信息
    addView(b, "cpu_info", "select * from table(\"cpu_info\"(true))"); // 添加cpu_info视图：CPU信息
    addView(b, "cpu_time", "select * from table(\"cpu_time\"(true))"); // 添加cpu_time视图：CPU时间统计
    addView(b, "interface_details", "select * from table(\"interface_details\"(true))"); // 添加interface_details视图：网络接口详细信息
    addView(b, "interface_addresses", "select * from table(\"interface_addresses\"(true))"); // 添加interface_addresses视图：网络接口地址
    addView(b, "mounts", "select * from table(\"mounts\"(true))"); // 添加mounts视图：挂载点信息
    b.append("       } ],\n") // tables数组结束
        .append("       functions: [ {\n"); // functions数组开始，定义表函数
    addFunction(b, "du", DuTableFunction.class); // 添加du表函数
    addFunction(b, "files", FilesTableFunction.class); // 添加files表函数
    addFunction(b, "git_commits", GitCommitsTableFunction.class); // 添加git_commits表函数
    addFunction(b, "jps", JpsTableFunction.class); // 添加jps表函数
    addFunction(b, "ps", PsTableFunction.class); // 添加ps表函数
    addFunction(b, "stdin", StdinTableFunction.class); // 添加stdin表函数
    addFunction(b, "vmstat", VmstatTableFunction.class); // 添加vmstat表函数
    addFunction(b, "system_info", SystemInfoTableFunction.class); // 添加system_info表函数
    addFunction(b, "java_info", JavaInfoTableFunction.class); // 添加java_info表函数
    addFunction(b, "os_version", OsVersionTableFunction.class); // 添加os_version表函数
    addFunction(b, "memory_info", MemoryInfoTableFunction.class); // 添加memory_info表函数
    addFunction(b, "cpu_info", CpuInfoTableFunction.class); // 添加cpu_info表函数
    addFunction(b, "cpu_time", CpuTimeTableFunction.class); // 添加cpu_time表函数
    addFunction(b, "interface_details", InterfaceDetailsTableFunction.class); // 添加interface_details表函数
    addFunction(b, "interface_addresses", InterfaceAddressesTableFunction.class); // 添加interface_addresses表函数
    addFunction(b, "mounts", MountsTableFunction.class); // 添加mounts表函数
    b.append("       } ]\n") // functions数组结束
        .append("     }\n") // schema对象结束
        .append("   ]\n") // schemas数组结束
        .append("}"); // JSON对象结束
    return b.toString(); // 返回构建好的JSON模型字符串
  }

  /** Main entry point. */ // 主入口方法：程序的入口点
  @SuppressWarnings("CatchAndPrintStackTrace")
  public static void main(String[] args) { // 静态方法：程序入口，接收命令行参数并执行SQL查询
    try { // 开始try块，捕获所有异常
      final PrintWriter err = // 创建标准错误输出的打印写入器
          new PrintWriter(
              new OutputStreamWriter(System.err, StandardCharsets.UTF_8)); // 使用UTF-8编码
      final InputStreamReader in = // 创建标准输入的读取器
          new InputStreamReader(System.in, StandardCharsets.UTF_8); // 使用UTF-8编码
      final PrintWriter out = // 创建标准输出的打印写入器
          new PrintWriter(
              new OutputStreamWriter(System.out, StandardCharsets.UTF_8)); // 使用UTF-8编码
      new SqlShell(in, out, err, args).run(); // 创建SqlShell实例并执行run方法
    } catch (Throwable e) { // 捕获所有异常
      e.printStackTrace(System.err); // 将异常堆栈打印到标准错误输出
    }
  }

  void run() throws SQLException { // 实例方法：执行SQL查询并输出结果，可能抛出SQLException
    final String url = "jdbc:calcite:lex=JAVA;conformance=LENIENT" // 构建JDBC连接URL，使用Calcite驱动
        + ";model=inline:" + MODEL; // 使用内联模型配置
    final String help = "Usage: sqlsh [OPTION]... SQL\n" // 帮助信息字符串
        + "Execute a SQL command\n"
        + "\n"
        + "Options:\n"
        + "  -o FORMAT  Print output in FORMAT; options are 'spaced' (the "
        + "default), 'csv',\n"
        + "             'headers', 'json', 'mysql'\n"
        + "  -h --help  Print this help";
    final StringBuilder b = new StringBuilder(); // 创建StringBuilder用于构建SQL查询字符串
    Format format = Format.SPACED; // 默认输出格式为SPACED（空格分隔）
    try (Enumerator<String> args = // 使用try-with-resources创建参数枚举器
             Linq4j.asEnumerable(this.args).enumerator()) { // 将参数列表转换为LINQ可枚举对象
      while (args.moveNext()) { // 遍历所有命令行参数
        if (args.current().equals("-o")) { // 如果参数是"-o"，表示指定输出格式
          if (args.moveNext()) { // 移动到下一个参数（格式名称）
            String formatString = args.current(); // 获取格式字符串
            try { // 尝试解析格式枚举值
              format = Format.valueOf(formatString.toUpperCase(Locale.ROOT)); // 将字符串转为大写并转换为Format枚举
            } catch (IllegalArgumentException e) { // 如果格式无效
              throw new RuntimeException("unknown format: " + formatString); // 抛出运行时异常
            }
          } else { // 如果"-o"后面没有格式参数
            throw new RuntimeException("missing format"); // 抛出运行时异常
          }
        } else if (args.current().equals("-h") // 如果参数是"-h"或"--help"
            || args.current().equals("--help")) {
          out.println(help); // 打印帮助信息
          return; // 直接返回，不执行查询
        } else { // 其他参数作为SQL查询的一部分
          if (b.length() > 0) { // 如果SQL字符串不为空
            b.append(' '); // 添加空格分隔
          }
          b.append(args.current()); // 将参数追加到SQL字符串
        }
      }
    }
    try (Connection connection = DriverManager.getConnection(url); // 使用try-with-resources创建JDBC连接
         Statement s = connection.createStatement(); // 创建Statement对象用于执行SQL
         Enumerator<String> args = // 创建参数枚举器（此代码块中未实际使用）
             Linq4j.asEnumerable(this.args).enumerator()) {
      final ResultSet r = s.executeQuery(b.toString()); // 执行SQL查询并获取结果集
      format.output(out, r); // 使用指定格式输出结果集
      r.close(); // 关闭结果集
    } finally { // finally块确保资源清理
      out.flush(); // 刷新输出流，确保所有数据都被写出
    }
  }


  private static void addView(StringBuilder b, String name, String sql) { // 静态方法：向模型添加视图定义
    if (!name.equals("du")) { // 如果不是第一个视图（du是第一个）
      b.append("}, {\n"); // 添加视图对象之间的分隔符
    }
    b.append("         \"name\": \"") // 开始添加视图名称
        .append(name) // 视图名称
        .append("\",\n") // 名称字段结束
        .append("         \"type\": \"view\",\n") // 类型为view
        .append("         \"sql\": \"") // SQL字段开始
        .append(sql.replace("\"", "\\\"") // 转义SQL中的双引号
            .replace("\n", "")) // 移除SQL中的换行符
        .append("\"\n"); // SQL字段结束
  }

  private static void addFunction(StringBuilder b, String name, Class c) { // 静态方法：向模型添加表函数定义
    if (!name.equals("du")) { // 如果不是第一个函数（du是第一个）
      b.append("}, {\n"); // 添加函数对象之间的分隔符
    }
    b.append("         \"name\": \"") // 开始添加函数名称
        .append(name) // 函数名称
        .append("\",\n") // 名称字段结束
        .append("         \"className\": \"") // className字段开始
        .append(c.getName()) // 获取类的全限定名
        .append("\"\n"); // className字段结束
  }

  /** Output format. */ // Format枚举：定义多种输出格式，用于格式化SQL查询结果
  enum Format {    SPACED { // SPACED格式：使用空格分隔列值，是最简单的输出格式
      @Override protected void output(PrintWriter out, ResultSet r) throws SQLException { // 重写output方法
        final int n = r.getMetaData().getColumnCount(); // 获取结果集的列数
        final StringBuilder b = new StringBuilder(); // 创建StringBuilder用于构建每行输出
        while (r.next()) { // 遍历结果集的每一行
          for (int i = 0; i < n; i++) { // 遍历每一列
            if (i > 0) { // 如果不是第一列
              b.append(' '); // 添加空格分隔
            }
            b.append(r.getString(i + 1)); // 获取列值并追加到StringBuilder（列索引从1开始）
          }
          out.println(b); // 输出当前行
          b.setLength(0); // 清空StringBuilder，准备下一行
        }
      }
    },
    HEADERS { // HEADERS格式：先输出列标题，然后使用SPACED格式输出数据
      @Override protected void output(PrintWriter out, ResultSet r) throws SQLException { // 重写output方法
        final ResultSetMetaData m = r.getMetaData(); // 获取结果集元数据
        final int n = m.getColumnCount(); // 获取列数
        final StringBuilder b = new StringBuilder(); // 创建StringBuilder用于构建标题行
        for (int i = 0; i < n; i++) { // 遍历每一列
          if (i > 0) { // 如果不是第一列
            b.append(' '); // 添加空格分隔
          }
          b.append(m.getColumnLabel(i + 1)); // 获取列标签（列名）并追加
        }
        out.println(b); // 输出标题行
        b.setLength(0); // 清空StringBuilder
        SPACED.output(out, r); // 使用SPACED格式输出数据行
      }
    },
    CSV { // CSV格式：符合RFC 4180标准的逗号分隔值格式
      @Override protected void output(PrintWriter out, ResultSet r) throws SQLException { // 重写output方法
        // We aim to comply with https://tools.ietf.org/html/rfc4180.
        // It's a bug if we don't. // 注释：目标是符合RFC 4180标准，不符合则是bug
        final ResultSetMetaData m = r.getMetaData(); // 获取结果集元数据
        final int n = m.getColumnCount(); // 获取列数
        final StringBuilder b = new StringBuilder(); // 创建StringBuilder用于构建每行输出
        for (int i = 0; i < n; i++) { // 遍历每一列，输出列标题
          if (i > 0) { // 如果不是第一列
            b.append(','); // 添加逗号分隔
          }
          value(b, m.getColumnLabel(i + 1)); // 处理列标签，处理特殊字符（引号、逗号、换行等）
        }
        out.print(b); // 输出标题行（不带换行）
        b.setLength(0); // 清空StringBuilder
        while (r.next()) { // 遍历结果集的每一行
          out.println(); // 输出换行符
          for (int i = 0; i < n; i++) { // 遍历每一列
            if (i > 0) { // 如果不是第一列
              b.append(','); // 添加逗号分隔
            }
            value(b, r.getString(i + 1)); // 处理列值，处理特殊字符
          }
          out.print(b); // 输出当前行（不带换行）
          b.setLength(0); // 清空StringBuilder
        }
      }

      private void value(StringBuilder b, String s) { // 私有方法：将字符串值按照CSV格式规范进行处理
        if (s == null) { // 如果值为null
          // do nothing - unfortunately same as empty string // 不做任何处理，CSV中null和空字符串表现相同
        } else if (s.contains("\"")) { // 如果字符串包含双引号
          b.append('"') // 添加起始引号
              .append(s.replace("\"", "\"\"")) // 将内部的双引号替换为两个双引号（CSV转义规则）
              .append('"'); // 添加结束引号
        } else if (s.indexOf(',') >= 0 // 如果字符串包含逗号
            || s.indexOf('\n') >= 0 // 或者包含换行符
            || s.indexOf('\r') >= 0) { // 或者包含回车符
          b.append('"').append(s).append('"'); // 用引号包裹整个字符串
        } else { // 如果不包含任何特殊字符
          b.append(s); // 直接输出字符串
        }
      }
    },
    JSON { // JSON格式：将结果集输出为JSON数组格式，每行数据为一个JSON对象
      @Override protected void output(PrintWriter out, final ResultSet r) // 重写output方法
          throws SQLException {
        final ResultSetMetaData m = r.getMetaData(); // 获取结果集元数据
        final int n = m.getColumnCount(); // 获取列数
        final Map<String, Integer> fieldOrdinals = new LinkedHashMap<>(); // 创建字段名到列序号的映射（保持插入顺序）
        for (int i = 0; i < n; i++) { // 遍历每一列
          fieldOrdinals.put(m.getColumnLabel(i + 1), // 将列标签作为键
              fieldOrdinals.size() + 1); // 列序号（从1开始）作为值
        }
        final Set<String> fields = fieldOrdinals.keySet(); // 获取所有字段名
        final JsonBuilder json = new JsonBuilder(); // 创建JsonBuilder用于构建JSON
        final StringBuilder b = new StringBuilder(); // 创建StringBuilder用于构建JSON字符串
        out.println("["); // 输出JSON数组开始标记
        int i = 0; // 行计数器
        while (r.next()) { // 遍历结果集的每一行
          if (i++ > 0) { // 如果不是第一行
            out.println(","); // 输出逗号分隔符
          }
          json.append(b, 0, // 使用JsonBuilder将数据转换为JSON格式
              Maps.asMap(fields, columnLabel -> { // 将字段集合转换为Map，值为从结果集中提取的数据
                try { // 捕获SQL异常
                  final int i1 = fieldOrdinals.get(columnLabel); // 获取列序号
                  switch (m.getColumnType(i1)) { // 根据列的SQL类型进行不同处理
                  case Types.BOOLEAN: // 布尔类型
                    final boolean b1 = r.getBoolean(i1); // 获取布尔值
                    return !b1 && r.wasNull() ? null : b1; // 如果值为false且为null则返回null，否则返回布尔值
                  case Types.DECIMAL: // 十进制类型
                  case Types.FLOAT: // 浮点类型
                  case Types.REAL: // 实数类型
                  case Types.DOUBLE: // 双精度类型
                    final double d = r.getDouble(i1); // 获取双精度值
                    return d == 0D && r.wasNull() ? null : d; // 如果值为0且为null则返回null，否则返回数值
                  case Types.BIGINT: // 大整数类型
                  case Types.INTEGER: // 整数类型
                  case Types.SMALLINT: // 小整数类型
                  case Types.TINYINT: // 微整数类型
                    final long v = r.getLong(i1); // 获取长整型值
                    return v == 0L && r.wasNull() ? null : v; // 如果值为0且为null则返回null，否则返回数值
                  default: // 其他类型（如字符串）
                    return r.getString(i1); // 直接返回字符串值
                  }
                } catch (SQLException e) { // 捕获SQL异常
                  throw new RuntimeException(e); // 转换为运行时异常抛出
                }
              }));
          out.append(b); // 输出JSON对象
          b.setLength(0); // 清空StringBuilder
        }
        if (i > 0) { // 如果有数据行
          out.println(); // 输出换行
        }
        out.println("]"); // 输出JSON数组结束标记
      }
    },
    MYSQL { // MYSQL格式：模拟MySQL命令行的表格输出格式，带有边框和对齐
      @Override protected void output(PrintWriter out, final ResultSet r) // 重写output方法
          throws SQLException {
        // E.g. // 示例输出格式
        // +-------+--------+
        // | EMPNO | ENAME  |
        // +-------+--------+
        // |  7369 | SMITH  |
        // |   822 | LEE    |
        // +-------+--------+

        final ResultSetMetaData m = r.getMetaData(); // 获取结果集元数据
        final int n = m.getColumnCount(); // 获取列数
        final List<String> values = new ArrayList<>(); // 创建列表存储所有值（包括标题和数据）
        final int[] lengths = new int[n]; // 创建数组存储每列的最大宽度
        final boolean[] rights = new boolean[n]; // 创建数组标记每列是否右对齐（数值类型）
        for (int i = 0; i < n; i++) { // 遍历每一列
          final String v = m.getColumnLabel(i + 1); // 获取列标题
          values.add(v); // 将列标题添加到值列表
          lengths[i] = v.length(); // 初始化列宽度为标题长度
          switch (m.getColumnType(i + 1)) { // 根据列类型判断是否右对齐
          case Types.BIGINT: // 大整数
          case Types.INTEGER: // 整数
          case Types.SMALLINT: // 小整数
          case Types.TINYINT: // 微整数
          case Types.REAL: // 实数
          case Types.FLOAT: // 浮点数
          case Types.DOUBLE: // 双精度
            rights[i] = true; // 标记为右对齐
            break;
          default: // 其他类型
            break; // 保持左对齐
          }
        }
        while (r.next()) { // 遍历结果集的每一行
          for (int i = 0; i < n; i++) { // 遍历每一列
            final String v = r.getString(i + 1); // 获取列值
            values.add(v); // 将值添加到值列表
            if (v != null && v.length() > lengths[i]) { // 如果值不为null且长度大于当前最大宽度
              lengths[i] = v.length(); // 更新列的最大宽度
            }
          }
        }

        final StringBuilder b = new StringBuilder("+"); // 创建StringBuilder用于构建边框
        for (int length : lengths) { // 遍历每列宽度
          pad(b, length + 2, '-'); // 用'-'填充列宽+2个空格的宽度
          b.append('+'); // 添加边框的'+'角标
        }
        final String bar = b.toString(); // 保存边框字符串
        out.println(bar); // 输出顶部边框
        b.setLength(0); // 清空StringBuilder

        for (int i = 0; i < n; i++) { // 遍历每一列（输出标题行）
          if (i == 0) { // 如果是第一列
            b.append('|'); // 添加左边框
          }
          b.append(' '); // 添加空格
          value(b, values.get(i), lengths[i], rights[i]); // 输出标题值，根据列宽和对齐方式格式化
          b.append(" |"); // 添加右空格和右边框
        }
        out.println(b); // 输出标题行
        b.setLength(0); // 清空StringBuilder
        out.print(bar); // 输出标题和数据之间的分隔边框

        for (int h = n; h < values.size(); h++) { // 遍历所有数据值（跳过前n个标题）
          final int i = h % n; // 计算当前值所在的列索引
          if (i == 0) { // 如果是新的一行的第一列
            out.println(b); // 输出上一行
            b.setLength(0); // 清空StringBuilder
            b.append('|'); // 添加左边框
          }
          b.append(' '); // 添加空格
          value(b, values.get(h), lengths[i], rights[i]); // 输出值，根据列宽和对齐方式格式化
          b.append(" |"); // 添加右空格和右边框
        }
        out.println(b); // 输出最后一行
        out.println(bar); // 输出底部边框

        int rowCount = (values.size() / n) - 1; // 计算数据行数（总值数除以列数减1）
        if (rowCount == 1) { // 如果只有一行
          out.println("(1 row)"); // 输出单数形式
        } else { // 如果有多行
          out.print("("); // 输出左括号
          out.print(rowCount); // 输出行数
          out.println(" rows)"); // 输出右括号和复数形式
        }
        out.println(); // 输出空行
      }

      private void value(StringBuilder b, String value, int length, // 私有方法：根据指定宽度和对齐方式格式化值
          boolean right) {
        if (value == null) { // 如果值为null
          pad(b, length, ' '); // 用空格填充整个宽度
        } else { // 如果值不为null
          final int pad = length - value.length(); // 计算需要填充的空格数
          if (pad == 0) { // 如果值长度正好等于列宽
            b.append(value); // 直接输出值
          } else if (right) { // 如果需要右对齐（数值类型）
            pad(b, pad, ' '); // 在左边填充空格
            b.append(value); // 输出值
          } else { // 左对齐（字符串类型）
            b.append(value); // 输出值
            pad(b, pad, ' '); // 在右边填充空格
          }
        }
      }

      private void pad(StringBuilder b, int pad, char c) { // 私有方法：向StringBuilder中填充指定数量的字符
        for (int j = 0; j < pad; j++) { // 循环pad次
          b.append(c); // 添加指定字符
        }
      }
    };

    protected abstract void output(PrintWriter out, ResultSet r) // 抽象方法：子类必须实现，用于按特定格式输出结果集
        throws SQLException; // 可能抛出SQLException
  }
}
