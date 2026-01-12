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
package org.apache.calcite.test; // 包声明：该类属于 org.apache.calcite.test 包，用于测试 Calcite 框架中 SQL 类型限制相关的功能

import org.apache.calcite.avatica.util.DateTimeUtils; // 导入日期时间工具类，用于处理时区等日期时间相关操作
import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入 Java 类型工厂实现类，用于创建 Java 类型的 RelDataType
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示 Calcite 中的数据类型
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统接口，定义数据类型系统的行为
import org.apache.calcite.sql.SqlLiteral; // 导入 SQL 字面量类，表示 SQL 中的常量值
import org.apache.calcite.sql.dialect.AnsiSqlDialect; // 导入 ANSI SQL 方言类，用于生成标准的 SQL 语句
import org.apache.calcite.sql.parser.SqlParserPos; // 导入 SQL 解析位置类，标记 SQL 元素在原始 SQL 中的位置
import org.apache.calcite.sql.test.SqlTests; // 导入 SQL 测试工具类，提供测试用的辅助方法
import org.apache.calcite.sql.type.BasicSqlType; // 导入基本 SQL 类型类，表示 SQL 中的基本数据类型
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SQL 类型名称枚举，定义所有支持的 SQL 数据类型名称
import org.apache.calcite.testlib.annotations.LocaleEnUs; // 导入测试注解，指定测试运行时使用美国英语语言环境

import org.junit.jupiter.api.Test; // 导入 JUnit 5 测试注解，标记测试方法

import java.io.PrintWriter; // 导入打印写入器类，用于格式化输出文本到字符流
import java.io.StringWriter; // 导入字符串写入器类，用于将输出收集到字符串缓冲区
import java.text.DateFormat; // 导入日期格式化抽象类，用于日期和时间的格式化和解析
import java.text.SimpleDateFormat; // 导入简单日期格式化类，是 DateFormat 的具体实现
import java.util.Calendar; // 导入日历类，用于处理日期和时间的计算与转换
import java.util.List; // 导入列表接口，表示有序的元素集合
import java.util.Locale; // 导入语言环境类，用于特定的地理、政治或文化区域设置

/**
 * Unit test for SQL limits. // SQL 限制的单元测试类
 * 
 * 该测试类用于验证 Calcite 框架中各种 SQL 数据类型的边界值和限制条件，
 * 包括最小值、最大值、零值、溢出和下溢等边界情况。
 * 
 * 主要功能：
 * 1. 测试各种 SQL 数据类型（如整数、小数、日期、时间等）的边界值
 * 2. 验证类型限制的正确性，包括溢出（OVERFLOW）和下溢（UNDERFLOW）
 * 3. 检查类型字面量在不同边界条件下的 SQL 表示
 * 4. 确保类型系统对边界值的处理符合 SQL 标准
 * 
 * 测试方法：
 * - testPrintLimits(): 打印所有数据类型的各种边界值，用于验证和文档化
 * 
 * 辅助方法：
 * - getDiffRepos(): 获取差异仓库，用于测试结果对比
 * - printLimit(): 打印特定类型的限制值
 * - getDateFormat(): 获取日期类型的格式化器
 */
@LocaleEnUs // 指定测试运行时使用美国英语语言环境，确保日期时间格式的一致性
public class SqlLimitsTest { // 定义 SqlLimitsTest 类，用于测试 SQL 数据类型的限制和边界值
  protected DiffRepository getDiffRepos() { // 定义受保护的方法，获取差异仓库对象
    return DiffRepository.lookup(SqlLimitsTest.class); // 通过类查找并返回对应的差异仓库实例，用于存储和对比测试结果
  }

  @Test void testPrintLimits() { // 使用 JUnit 5 的 @Test 注解标记测试方法，测试打印所有 SQL 类型的限制值
    StringWriter sw = new StringWriter(); // 创建字符串写入器，用于收集输出结果到内存中的字符串缓冲区
    PrintWriter pw = new PrintWriter(sw); // 创建打印写入器，包装字符串写入器，提供格式化输出功能
    final List<RelDataType> types = // 声明最终变量，存储所有 SQL 类型的列表
        SqlTests.getTypes(new JavaTypeFactoryImpl(RelDataTypeSystem.DEFAULT)); // 调用 SqlTests.getTypes 方法获取所有数据类型，使用默认的关系数据类型系统和 Java 类型工厂
    for (RelDataType type : types) { // 遍历每一个数据类型
      pw.println(type.toString()); // 打印当前数据类型的字符串表示
      printLimit( // 调用 printLimit 方法打印最小值减去 epsilon 的限制值（比最小值更小的值，会导致溢出）
          pw, // 打印写入器
          "  min - epsilon:          ", // 描述文本：最小值减去 epsilon
          type, // 当前数据类型
          false, // 符号为负（false 表示负号）
          SqlTypeName.Limit.OVERFLOW, // 限制类型为溢出
          true); // 超出边界（true 表示超出边界）
      printLimit( // 调用 printLimit 方法打印最小值的限制值
          pw, // 打印写入器
          "  min:                    ", // 描述文本：最小值
          type, // 当前数据类型
          false, // 符号为负（false 表示负号）
          SqlTypeName.Limit.OVERFLOW, // 限制类型为溢出
          false); // 不超出边界（false 表示边界值本身）
      printLimit( // 调用 printLimit 方法打印零值减去 delta 的限制值（比零值更小的值，会导致下溢）
          pw, // 打印写入器
          "  zero - delta:           ", // 描述文本：零值减去 delta
          type, // 当前数据类型
          false, // 符号为负（false 表示负号）
          SqlTypeName.Limit.UNDERFLOW, // 限制类型为下溢
          false); // 不超出边界（false 表示边界值本身）
      printLimit( // 调用 printLimit 方法打印零值减去 delta 加上 epsilon 的限制值（比零值减去 delta 稍大的值）
          pw, // 打印写入器
          "  zero - delta + epsilon: ", // 描述文本：零值减去 delta 加上 epsilon
          type, // 当前数据类型
          false, // 符号为负（false 表示负号）
          SqlTypeName.Limit.UNDERFLOW, // 限制类型为下溢
          true); // 超出边界（true 表示超出边界）
      printLimit( // 调用 printLimit 方法打印零值的限制值
          pw, // 打印写入器
          "  zero:                   ", // 描述文本：零值
          type, // 当前数据类型
          false, // 符号为负（false 表示负号）
          SqlTypeName.Limit.ZERO, // 限制类型为零
          false); // 不超出边界（false 表示边界值本身）
      printLimit( // 调用 printLimit 方法打印零值加上 delta 减去 epsilon 的限制值（比零值加上 delta 稍小的值）
          pw, // 打印写入器
          "  zero + delta - epsilon: ", // 描述文本：零值加上 delta 减去 epsilon
          type, // 当前数据类型
          true, // 符号为正（true 表示正号）
          SqlTypeName.Limit.UNDERFLOW, // 限制类型为下溢
          true); // 超出边界（true 表示超出边界）
      printLimit( // 调用 printLimit 方法打印零值加上 delta 的限制值
          pw, // 打印写入器
          "  zero + delta:           ", // 描述文本：零值加上 delta
          type, // 当前数据类型
          true, // 符号为正（true 表示正号）
          SqlTypeName.Limit.UNDERFLOW, // 限制类型为下溢
          false); // 不超出边界（false 表示边界值本身）
      printLimit( // 调用 printLimit 方法打印最大值的限制值
          pw, // 打印写入器
          "  max:                    ", // 描述文本：最大值
          type, // 当前数据类型
          true, // 符号为正（true 表示正号）
          SqlTypeName.Limit.OVERFLOW, // 限制类型为溢出
          false); // 不超出边界（false 表示边界值本身）
      printLimit( // 调用 printLimit 方法打印最大值加上 epsilon 的限制值（比最大值更大的值，会导致溢出）
          pw, // 打印写入器
          "  max + epsilon:          ", // 描述文本：最大值加上 epsilon
          type, // 当前数据类型
          true, // 符号为正（true 表示正号）
          SqlTypeName.Limit.OVERFLOW, // 限制类型为溢出
          true); // 超出边界（true 表示超出边界）
      pw.println(); // 打印空行，分隔不同数据类型的输出
    }
    pw.flush(); // 刷新打印写入器，确保所有输出都写入到字符串写入器
    getDiffRepos().assertEquals("output", "${output}", sw.toString()); // 获取差异仓库并断言输出结果与预期值匹配，使用 "output" 作为键，"${output}" 作为占位符
  }

  private void printLimit( // 定义私有方法，打印特定数据类型的限制值
      PrintWriter pw, // 打印写入器，用于输出结果
      String desc, // 描述文本，说明当前打印的限制值的含义
      RelDataType type, // 关系数据类型，表示要测试的 SQL 数据类型
      boolean sign, // 符号标志，true 表示正号，false 表示负号
      SqlTypeName.Limit limit, // 限制类型枚举，包括 OVERFLOW（溢出）、UNDERFLOW（下溢）、ZERO（零值）
      boolean beyond) { // 超出边界标志，true 表示超出边界，false 表示边界值本身
    Object o = ((BasicSqlType) type).getLimit(sign, limit, beyond); // 调用 BasicSqlType 的 getLimit 方法获取限制值，将 RelDataType 强制转换为 BasicSqlType
    if (o == null) { // 如果获取的限制值为 null，说明该类型不支持该限制
      return; // 直接返回，不进行打印
    }
    pw.print(desc); // 打印描述文本
    String s; // 声明字符串变量，用于存储限制值的字符串表示
    if (o instanceof byte[]) { // 如果限制值是字节数组（通常用于 BINARY 或 VARBINARY 类型）
      int k = 0; // 声明计数器，用于控制逗号的添加
      StringBuilder buf = new StringBuilder("{"); // 创建字符串构建器，初始化为 "{"
      for (byte b : (byte[]) o) { // 遍历字节数组中的每个字节
        if (k++ > 0) { // 如果不是第一个字节
          buf.append(", "); // 在构建器中添加逗号和空格作为分隔符
        }
        buf.append(Integer.toHexString(b & 0xff)); // 将字节转换为十六进制字符串并添加到构建器中（使用 & 0xff 确保无符号）
      }
      buf.append("}"); // 在构建器末尾添加 "}"
      s = buf.toString(); // 将构建器转换为字符串
    } else if (o instanceof Calendar) { // 如果限制值是日历对象（通常用于 DATE、TIME、TIMESTAMP 类型）
      Calendar calendar = (Calendar) o; // 将对象强制转换为 Calendar 类型
      DateFormat dateFormat = getDateFormat(type.getSqlTypeName()); // 根据数据类型名称获取对应的日期格式化器
      dateFormat.setTimeZone(DateTimeUtils.UTC_ZONE); // 设置时区为 UTC 时区，确保日期时间的一致性
      s = dateFormat.format(calendar.getTime()); // 使用日期格式化器格式化日历的时间为字符串
    } else { // 如果限制值是其他类型（如数字、字符串等）
      s = o.toString(); // 直接调用 toString 方法获取字符串表示
    }
    pw.print(s); // 打印限制值的字符串表示
    SqlLiteral literal = // 声明 SQL 字面量变量
        type.getSqlTypeName().createLiteral(o, SqlParserPos.ZERO); // 根据数据类型名称创建 SQL 字面量，使用零位置（表示生成的字面量）
    pw.print("; as SQL: "); // 打印分隔符和 SQL 提示文本
    pw.print(literal.toSqlString(AnsiSqlDialect.DEFAULT)); // 将字面量转换为 ANSI SQL 方言的 SQL 字符串并打印
    pw.println(); // 打印换行符，结束当前行的输出
  }

  private DateFormat getDateFormat(SqlTypeName typeName) { // 定义私有方法，根据 SQL 类型名称获取对应的日期格式化器
    switch (typeName) { // 使用 switch 语句根据类型名称进行分支处理
    case DATE: // 如果类型是 DATE（日期类型）
      return new SimpleDateFormat("MMM d, yyyy", Locale.ROOT); // 返回日期格式化器，格式为 "月 日, 年"（如 "Jan 1, 2026"），使用根语言环境
    case TIME: // 如果类型是 TIME（时间类型）
      return new SimpleDateFormat("hh:mm:ss a", Locale.ROOT); // 返回时间格式化器，格式为 "时:分:秒 AM/PM"（如 "12:00:00 PM"），使用根语言环境
    default: // 如果类型是其他类型（如 TIMESTAMP）
      return new SimpleDateFormat("MMM d, yyyy hh:mm:ss a", Locale.ROOT); // 返回日期时间格式化器，格式为 "月 日, 年 时:分:秒 AM/PM"（如 "Jan 1, 2026 12:00:00 PM"），使用根语言环境
    }
  }
}
