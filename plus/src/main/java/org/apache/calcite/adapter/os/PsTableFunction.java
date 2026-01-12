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
package org.apache.calcite.adapter.os;

import org.apache.calcite.DataContext; // 数据上下文接口，提供运行时环境信息
import org.apache.calcite.avatica.util.TimeUnit; // 时间单位枚举，用于定义时间间隔类型
import org.apache.calcite.linq4j.Enumerable; // 可枚举接口，支持LINQ风格的查询操作
import org.apache.calcite.linq4j.function.Function1; // 单参数函数接口，用于转换操作
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型接口，描述表的结构
import org.apache.calcite.rel.type.RelDataTypeFactory; // 关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.schema.ScannableTable; // 可扫描表接口，支持顺序扫描数据
import org.apache.calcite.sql.type.SqlTypeName; // SQL类型名称枚举，定义标准的SQL数据类型
import org.apache.calcite.util.Util; // Calcite工具类，提供各种实用方法

import com.google.common.annotations.VisibleForTesting; // Guava注解，标记仅在测试中可见的成员
import com.google.common.collect.ImmutableList; // Guava不可变列表类，线程安全的列表实现
import com.google.common.collect.ImmutableMap; // Guava不可变映射类，线程安全的映射实现

import org.checkerframework.checker.nullness.qual.Nullable; // 空值检查注解，标记可能为null的值

import java.util.List; // Java标准列表接口
import java.util.regex.Matcher; // 正则表达式匹配器类
import java.util.regex.Pattern; // 正则表达式模式类
import java.util.stream.Collectors; // 流收集器类，用于流的聚合操作

import static java.lang.Float.parseFloat; // 静态导入Float.parseFloat方法
import static java.lang.Long.parseLong; // 静态导入Long.parseLong方法

/**
 * 表函数，用于执行操作系统的"ps"命令来列出进程信息
 * 
 * 这个类是Calcite适配器框架的一部分，专门用于将操作系统的进程信息转换为可查询的表结构
 * 主要功能：
 * 1. 通过执行系统的ps命令获取进程列表
 * 2. 解析ps命令的输出，将其转换为结构化的数据行
 * 3. 支持跨平台（Linux和macOS）的ps命令差异处理
 * 4. 提供标准的ScannableTable接口，使进程信息可以通过SQL查询
 * 
 * 设计特点：
 * - 使用工具类模式，所有方法都是静态的，不允许实例化
 * - 内部维护了ps命令输出字段的标准化映射
 * - 提供了专门的LineParser类来解析ps命令的输出行
 * - 支持字段名称在不同操作系统间的自动转换（如Linux的pgrp到macOS的pgid）
 * 
 * 使用场景：当需要通过SQL查询系统进程信息时，可以通过这个表函数
 * 将操作系统的进程信息暴露为Calcite可查询的表
 */
public class PsTableFunction {
  // 正则表达式模式：用于匹配"分:秒:毫秒"格式的时间字符串（如"12:34:567"）
  // 捕获组1：分钟数，捕获组2：秒数，捕获组3：毫秒数
  private static final Pattern MINUTE_SECOND_MILLIS_PATTERN =
      Pattern.compile("([0-9]+):([0-9]+):([0-9]+)");
  // 正则表达式模式：用于匹配"分:秒.毫秒"格式的时间字符串（如"12:34.567"）
  // 捕获组1：分钟数，捕获组2：秒数，捕获组3：毫秒数
  private static final Pattern HOUR_MINUTE_SECOND_PATTERN =
      Pattern.compile("([0-9]+):([0-9]+)\\.([0-9]+)");
  // 正则表达式模式：用于匹配纯数字字符串（如"123"）
  // 用于判断某个token是否为纯数字，帮助区分用户名和进程ID
  private static final Pattern NUMERIC_PATTERN = Pattern.compile("(\\d+)");

  // Unix/Linux到macOS的ps命令字段名称映射表
  // 这是一个部分映射，未映射的字段保持原样（例如"user" -> "user"）
  // 用于解决不同操作系统间ps命令输出字段名称的差异
  private static final ImmutableMap<String, String> UNIX_TO_MAC_PS_FIELDS =
      ImmutableMap.<String, String>builder()
          .put("pgrp", "pgid") // Linux的进程组ID字段名在macOS中是"pgid"
          .put("start_time", "lstart") // Linux的启动时间字段名在macOS中是"lstart"
          .put("euid", "uid") // Linux的有效用户ID字段名在macOS中是"uid"
          .build();

  // ps命令要输出的字段名称列表，定义了进程信息的完整结构
  // 这些字段对应ps命令的标准输出，包含了进程的完整信息
  private static final List<String> PS_FIELD_NAMES =
      ImmutableList.of("user",      // 进程所属用户名
      "pid",                        // 进程ID（Process ID）
      "ppid",                       // 父进程ID（Parent Process ID）
      "pgrp",                       // 进程组ID（Process Group ID，Linux）
      "tpgid",                      // 终端进程组ID（Terminal Process Group ID）
      "stat",                       // 进程状态（State，如R/S/D/Z等）
      "pcpu",                       // CPU使用率（Percent CPU）
      "pmem",                       // 内存使用率（Percent Memory）
      "vsz",                        // 虚拟内存大小（Virtual Memory Size，单位KB）
      "rss",                        // 常驻内存大小（Resident Set Size，单位KB）
      "tty",                        // 控制终端（Teletype）
      "start_time",                 // 进程启动时间（Start Time，Linux）
      "time",                       // 进程累计CPU时间（Time）
      "euid",                       // 有效用户ID（Effective User ID，Linux）
      "ruid",                       // 实际用户ID（Real User ID）
      "sess",                       // 会话ID（Session ID）
      "comm");                      // 命令名称（Command）

  // 私有构造函数，防止类被实例化
  // 这是一个工具类，所有方法都是静态的，不应该创建实例
  private PsTableFunction() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * 行解析器类，用于逐行解析ps命令的输出
   * 
   * 这个类实现了Function1<String, Object[]>接口，是一个函数式接口的实现
   * 主要功能：
   * 1. 接收ps命令输出的一行文本作为输入
   * 2. 将该行文本按照预定义的字段列表进行解析
   * 3. 处理字段中可能包含的空格（如用户名和命令名）
   * 4. 将字符串值转换为适当的数据类型（整数、浮点数等）
   * 5. 返回一个Object数组，包含所有解析后的字段值
   * 
   * 设计特点：
   * - 使用@VisibleForTesting注解，表示这个类主要用于测试
   * - 实现了Function1接口，可以作为LINQ4J的转换函数使用
   * - 智能处理字段边界，能够正确识别包含空格的用户名和命令名
   * - 提供详细的错误信息，帮助定位解析失败的位置
   * 
   * 使用方式：作为Processes.processLines().select()的参数，自动解析每一行输出
   */
  @VisibleForTesting
  protected static class LineParser implements Function1<String, Object[]> {

    // 应用函数：解析ps命令输出的一行文本，将其转换为对象数组
    // 参数：line - ps命令输出的一行文本
    // 返回：包含所有字段值的Object数组，数组长度等于PS_FIELD_NAMES.size()
    @Override public Object[] apply(String line) {
      // 去除行首尾空白，然后按一个或多个空格分割成token数组
      final String[] tokens = line.trim().split(" +");
      // 创建结果数组，大小等于预定义的字段数量
      final Object[] values = new Object[PS_FIELD_NAMES.size()];

      // 检查token数量是否足够，如果不足说明ps命令输出格式异常
      if (tokens.length < PS_FIELD_NAMES.size()) {
        throw new IllegalArgumentException(
            "Expected at least " + PS_FIELD_NAMES.size() + ", got " + tokens.length);
      }

      // 字段索引：当前正在处理的字段在PS_FIELD_NAMES中的位置
      int fieldIdx = 0;
      // 已处理的token数量：记录已经从tokens数组中消费了多少个元素
      int processedTokens = 0;
      // 特殊情况处理：当token数量多于字段数量时，说明"user"或"comm"字段（或两者）包含空格
      // 假设用户名不包含由空格分隔的数字部分（例如"root 123"），因此当遇到数字token时
      // 我们停止处理，假设它是"pid"字段，而"user"字段已经结束
      if (tokens.length > PS_FIELD_NAMES.size()) {
        StringBuilder sb = new StringBuilder();
        // 遍历所有token，直到遇到第一个数字token
        for (String field : tokens) {
          if (NUMERIC_PATTERN.matcher(field).matches()) {
            break; // 遇到数字，说明到了pid字段，停止处理用户名
          }
          processedTokens++; // 增加已处理的token计数
          sb.append(field).append(" "); // 将token追加到用户名字符串
        }
        // 将拼接好的用户名字符串（去掉末尾空格）转换为适当类型并存储
        values[fieldIdx] =
            field(PS_FIELD_NAMES.get(fieldIdx), sb.deleteCharAt(sb.length() - 1).toString());
        fieldIdx++; // 移动到下一个字段
      }

      // 处理中间的字段（从当前字段到倒数第二个字段）
      // 这些字段通常是简单的一对一映射，不包含空格
      for (; fieldIdx < values.length - 1; fieldIdx++) {
        try {
          // 将当前token转换为适当类型并存储到values数组中
          values[fieldIdx] = field(PS_FIELD_NAMES.get(fieldIdx), tokens[processedTokens++]);
        } catch (RuntimeException e) {
          // 如果解析失败，提供详细的错误信息，包括字段名、值和原始行
          throw new RuntimeException("while parsing value ["
              + tokens[fieldIdx] + "] of field [" + PS_FIELD_NAMES.get(fieldIdx)
              + "] in line [" + line + "]");
        }
      }

      // 处理最后一个字段"comm"（命令名称），它也可能包含空格
      if (processedTokens < tokens.length - 1) {
        // 如果还有多个token未处理，说明命令名包含空格，需要拼接
        StringBuilder sb = new StringBuilder();
        while (processedTokens < tokens.length) {
          sb.append(tokens[processedTokens++]).append(" "); // 拼接所有剩余token
        }
        // 将拼接好的命令名字符串（去掉末尾空格）转换为适当类型并存储
        values[fieldIdx] =
            field(PS_FIELD_NAMES.get(fieldIdx), sb.deleteCharAt(sb.length() - 1).toString());
      } else {
        // 如果只剩一个token，直接处理
        values[fieldIdx] = field(PS_FIELD_NAMES.get(fieldIdx), tokens[processedTokens]);
      }
      return values; // 返回解析后的值数组
    }

    // 字段值转换方法：根据字段名称将字符串值转换为适当的数据类型
    // 参数：field - 字段名称，value - 字段的字符串值
    // 返回：转换后的对象（Integer、Long或String）
    private static Object field(String field, String value) {
      switch (field) {
      case "pid": // 进程ID，转换为整数
      case "ppid": // 父进程ID，转换为整数
      case "pgrp": // 进程组ID，转换为整数（仅Linux，macOS对应pgid）
      case "pgid": // 进程组ID，转换为整数（仅macOS，Linux对应pgrp）
      case "tpgid": // 终端进程组ID，转换为整数
        return Integer.valueOf(value);
      case "pcpu": // CPU使用率，转换为整数（原值乘以10，如12.5% -> 125）
      case "pmem": // 内存使用率，转换为整数（原值乘以10，如12.5% -> 125）
        return (int) (parseFloat(value) * 10f);
      case "time": // 进程累计CPU时间，转换为毫秒数（Long类型）
        final Matcher m1 =
            MINUTE_SECOND_MILLIS_PATTERN.matcher(value); // 尝试匹配"分:秒:毫秒"格式
        if (m1.matches()) {
          final long h = parseLong(m1.group(1)); // 提取分钟数
          final long m = parseLong(m1.group(2)); // 提取秒数
          final long s = parseLong(m1.group(3)); // 提取毫秒数
          return h * 3600000L + m * 60000L + s * 1000L; // 转换为总毫秒数
        }
        final Matcher m2 =
            HOUR_MINUTE_SECOND_PATTERN.matcher(value); // 尝试匹配"分:秒.毫秒"格式
        if (m2.matches()) {
          final long m = parseLong(m2.group(1)); // 提取分钟数
          final long s = parseLong(m2.group(2)); // 提取秒数
          StringBuilder g3 = new StringBuilder(m2.group(3)); // 提取毫秒部分
          while (g3.length() < 3) {
            g3.append("0"); // 补齐毫秒部分到3位（如"5" -> "500"）
          }
          final long millis = parseLong(g3.toString()); // 转换为毫秒数
          return m * 60000L + s * 1000L + millis; // 转换为总毫秒数
        }
        return 0L; // 如果无法匹配任何格式，返回0
      case "start_time": // 进程启动时间，保持为字符串（仅Linux，macOS对应lstart）
      case "lstart": // 进程启动时间，保持为字符串（仅macOS，Linux对应start_time）
      case "euid": // 有效用户ID，保持为字符串（仅Linux，macOS对应uid）
      case "uid": // 有效用户ID，保持为字符串（仅macOS，Linux对应euid）
      default: // 其他所有字段（用户名、状态、命令等），保持为字符串
        return value;
      }
    }
  }

  // 评估函数：创建一个可扫描的表对象，用于执行ps命令并返回进程信息
    // 参数：b - 布尔标志，当前未使用（保留用于未来扩展）
    // 返回：ScannableTable对象，可以通过scan方法获取进程数据
    public static ScannableTable eval(boolean b) {
    return new AbstractBaseScannableTable() {
      // 扫描方法：执行ps命令并返回可枚举的进程数据行
      // 参数：root - 数据上下文，提供类型工厂等信息
      // 返回：包含所有进程数据的可枚举对象，每行是一个Object数组
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) {
        // 获取行类型信息，包含字段名称和类型
        final RelDataType rowType = getRowType(root.getTypeFactory());
        // 提取字段名称列表
        final List<String> fieldNames = ImmutableList.copyOf(rowType.getFieldNames());
        // ps命令的参数数组
        final String[] args;
        // 获取操作系统名称
        final String osName = System.getProperty("os.name");
        // 获取操作系统版本（当前未使用，但保留用于未来扩展）
        final String osVersion = System.getProperty("os.version");
        Util.discard(osVersion); // 显式忽略版本变量，避免编译器警告
        // 根据操作系统类型构建不同的ps命令
        switch (osName) {
        case "Mac OS X": // macOS系统（测试版本：10.12.5）
          // macOS的ps命令格式：ps ax -o field1=,field2=,...
          // 需要将Linux字段名映射到macOS字段名，并在每个字段名后添加"="
          args = new String[] {
              "ps", "ax", "-o",
              fieldNames.stream()
                  .map(s -> UNIX_TO_MAC_PS_FIELDS.getOrDefault(s, s) + "=")
                  .collect(Collectors.joining(","))};
          break;
        default: // Linux和其他Unix系统
          // Linux的ps命令格式：ps --no-headers axo field1,field2,...
          // --no-headers表示不输出标题行
          args = new String[] {
              "ps", "--no-headers", "axo", String.join(",", fieldNames)};
        }
        // 执行ps命令，将输出行转换为对象数组
        // Processes.processLines执行命令并返回行的可枚举对象
        // .select(new LineParser())使用LineParser解析每一行
        return Processes.processLines(args).select(new LineParser());
      }

      // 获取行类型方法：定义表的元数据结构，包括字段名称和数据类型
      // 参数：typeFactory - 关系数据类型工厂，用于创建数据类型
      // 返回：RelDataType对象，描述表的完整结构
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        return typeFactory.builder()
            .add(PS_FIELD_NAMES.get(0), SqlTypeName.VARCHAR) // user: VARCHAR类型
            .add(PS_FIELD_NAMES.get(1), SqlTypeName.INTEGER) // pid: INTEGER类型
            .add(PS_FIELD_NAMES.get(2), SqlTypeName.INTEGER) // ppid: INTEGER类型
            .add(PS_FIELD_NAMES.get(3), SqlTypeName.INTEGER) // pgrp: INTEGER类型
            .add(PS_FIELD_NAMES.get(4), SqlTypeName.INTEGER) // tpgid: INTEGER类型
            .add(PS_FIELD_NAMES.get(5), SqlTypeName.VARCHAR) // stat: VARCHAR类型
            .add(PS_FIELD_NAMES.get(6), SqlTypeName.DECIMAL, 3, 1) // pcpu: DECIMAL(3,1)类型，精度3，小数位1
            .add(PS_FIELD_NAMES.get(7), SqlTypeName.DECIMAL, 3, 1) // pmem: DECIMAL(3,1)类型，精度3，小数位1
            .add(PS_FIELD_NAMES.get(8), SqlTypeName.INTEGER) // vsz: INTEGER类型
            .add(PS_FIELD_NAMES.get(9), SqlTypeName.INTEGER) // rss: INTEGER类型
            .add(PS_FIELD_NAMES.get(10), SqlTypeName.VARCHAR) // tty: VARCHAR类型
            .add(PS_FIELD_NAMES.get(11), SqlTypeName.VARCHAR) // start_time: VARCHAR类型
            .add(PS_FIELD_NAMES.get(12), TimeUnit.HOUR, -1, TimeUnit.SECOND, 0) // time: 时间间隔类型，从小时到秒
            .add(PS_FIELD_NAMES.get(13), SqlTypeName.VARCHAR) // euid: VARCHAR类型
            .add(PS_FIELD_NAMES.get(14), SqlTypeName.VARCHAR) // ruid: VARCHAR类型
            .add(PS_FIELD_NAMES.get(15), SqlTypeName.VARCHAR) // sess: VARCHAR类型
            .add(PS_FIELD_NAMES.get(16), SqlTypeName.VARCHAR) // comm: VARCHAR类型
            .build();
      }
    };
  }
}
