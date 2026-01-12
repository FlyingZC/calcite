/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // 许可声明：本软件根据 Apache 许可证 2.0 版本授权
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，详见 NOTICE 文件
 * this work for additional information regarding copyright ownership. // 获取版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF 根据 Apache 许可证 2.0 版本授权您使用本文件
 * (the "License"); you may not use this file except in compliance with // （"许可证"）；除非遵守许可证，否则不得使用本文件
 * the License.  You may obtain a copy of the License at // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // 许可证网址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS, // 根据许可证分发的软件按"原样"分发
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不提供任何明示或暗示的保证或条件
 * See the License for the specific language governing permissions and // 详见许可证，了解特定语言的权限和
 * limitations under the License. // 使用限制
 */
package org.apache.calcite.sql.test; // 包声明：SQL 测试工具包

import org.apache.calcite.sql.SqlFunction; // 导入 SQL 函数类
import org.apache.calcite.sql.SqlOperator; // 导入 SQL 操作符基类
import org.apache.calcite.sql.SqlOperatorTable; // 导入 SQL 操作符表接口
import org.apache.calcite.sql.SqlSpecialOperator; // 导入 SQL 特殊操作符类
import org.apache.calcite.sql.fun.SqlLibrary; // 导入 SQL 库枚举
import org.apache.calcite.sql.fun.SqlLibraryOperatorTableFactory; // 导入 SQL 库操作符表工厂
import org.apache.calcite.sql.fun.SqlOverlapsOperator; // 导入时间重叠操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入标准 SQL 操作符表
import org.apache.calcite.sql.parser.SqlAbstractParserImpl; // 导入 SQL 抽象解析器实现
import org.apache.calcite.sql.parser.SqlParserTest; // 导入 SQL 解析器测试类
import org.apache.calcite.test.DiffTestCase; // 导入差异测试用例类
import org.apache.calcite.util.TestUtil; // 导入测试工具类
import org.apache.calcite.util.Util; // 导入通用工具类

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的测试注解

import java.io.BufferedReader; // 导入缓冲读取器
import java.io.File; // 导入文件类
import java.io.FileOutputStream; // 导入文件输出流
import java.io.IOException; // 导入 IO 异常类
import java.io.LineNumberReader; // 导入带行号的读取器
import java.io.PrintWriter; // 导入打印写入器
import java.util.EnumSet; // 导入枚举集合
import java.util.HashSet; // 导入哈希集合
import java.util.List; // 导入列表接口
import java.util.Map; // 导入映射接口
import java.util.Set; // 导入集合接口
import java.util.TreeMap; // 导入树形映射（有序）
import java.util.TreeSet; // 导入树形集合（有序）
import java.util.regex.Pattern; // 导入正则表达式模式类
import java.util.stream.Collectors; // 导入流收集工具

import static org.hamcrest.CoreMatchers.is; // 导入 Hamcrest 匹配器：is
import static org.hamcrest.MatcherAssert.assertThat; // 导入 Hamcrest 断言工具

/** Various automated checks on the documentation. */ // 文档的各种自动化检查测试类，用于验证 Calcite 项目的文档（特别是 reference.md）与代码实现的一致性
class DocumentationTest {
  /** Generates a copy of {@code reference.md} with the current set of key
   * words. Fails if the copy is different from the original. */ // 生成 reference.md 的副本，包含当前的关键字集合，如果生成的副本与原始文件不同则测试失败
  @Test void testGenerateKeyWords() throws IOException { // 测试方法：生成关键字文档并验证一致性
    final FileFixture f = new FileFixture(); // 创建文件路径配置对象，用于定位输入和输出文件
    f.outFile.getParentFile().mkdirs(); // 创建输出文件的父目录，确保目录存在
    try (BufferedReader r = Util.reader(f.inFile); // 创建缓冲读取器读取输入文件（reference.md）
         FileOutputStream fos = new FileOutputStream(f.outFile); // 创建文件输出流用于写入生成的文件
         PrintWriter w = Util.printWriter(f.outFile)) { // 创建打印写入器用于方便地写入文本
      String line; // 声明字符串变量用于存储读取的每一行
      int stage = 0; // 阶段标记：0=开始前，1=关键字生成区域，2=结束后
      while ((line = r.readLine()) != null) { // 逐行读取输入文件内容
        if (line.equals("{% comment %} end {% endcomment %}")) { // 如果遇到结束标记
          ++stage; // 进入下一阶段（从关键字生成区域退出）
        }
        if (stage != 1) { // 如果不在关键字生成区域（阶段1）
          w.println(line); // 直接写入该行到输出文件
        }
        if (line.equals("{% comment %} start {% endcomment %}")) { // 如果遇到开始标记
          ++stage; // 进入关键字生成区域（阶段1）
          SqlAbstractParserImpl.Metadata metadata = // 获取 SQL 解析器的元数据对象
              new SqlParserTest().fixture().parser().getMetadata(); // 通过解析器测试工具获取元数据
          int z = 0; // 计数器，用于控制关键字之间的逗号分隔
          for (String s : metadata.getTokens()) { // 遍历所有解析器 token
            if (z++ > 0) { // 如果不是第一个 token
              w.println(","); // 在 token 之间添加逗号分隔
            }
            if (metadata.isKeyword(s)) { // 如果当前 token 是关键字
              w.print(metadata.isReservedWord(s) ? ("**" + s + "**") : s); // 保留字用粗体标记，普通关键字直接输出
            }
          }
          w.println("."); // 关键字列表结束后添加句号
        }
      }
      w.flush(); // 刷新打印写入器的缓冲区，确保所有内容写入
      fos.flush(); // 刷新文件输出流的缓冲区
      fos.getFD().sync(); // 强制将文件内容同步到磁盘
    }
    String diff = DiffTestCase.diff(f.outFile, f.inFile); // 比较生成的文件与原始文件的差异
    if (!diff.isEmpty()) { // 如果存在差异
      throw new AssertionError("Mismatch between " + f.outFile // 抛出断言错误，显示不匹配信息
          + " and " + f.inFile + ":\n"
          + diff); // 包含详细的差异内容
    }
  } // testGenerateKeyWords 方法结束

  /** Tests that every function in {@link SqlStdOperatorTable} is documented in
   * reference.md. */ // 测试 SqlStdOperatorTable 中的每个函数是否都在 reference.md 文档中有记录
  @Test void testAllFunctionsAreDocumented() throws IOException { // 测试方法：验证所有函数都已文档化
    final FileFixture f = new FileFixture(); // 创建文件路径配置对象
    final Map<String, PatternOp> map = new TreeMap<>(); // 创建有序映射，存储正则表达式到操作符的映射

    final SqlStdOperatorTable standard = SqlStdOperatorTable.instance(); // 获取标准 SQL 操作符表单例
    addOperators(map, "", standard.getOperatorList()); // 将标准操作符添加到映射中，前缀为空

    for (SqlLibrary library : SqlLibrary.values()) { // 遍历所有 SQL 库类型
      final SqlOperatorTable libraryTable = // 获取对应库的操作符表
          SqlLibraryOperatorTableFactory.INSTANCE
              .getOperatorTable(EnumSet.of(library), false); // 使用工厂方法创建操作符表
      switch (library) { // 根据库类型进行不同处理
      case STANDARD: // 标准库已处理，跳过
      case SPATIAL: // 空间库已处理，跳过
        continue; // 跳过当前循环
      case ALL: // 全部库
        addOperators(map, "\\| \\* ", libraryTable.getOperatorList()); // 添加操作符，使用星号前缀
        continue; // 跳过当前循环
      default: // 其他库
        addOperators(map, "\\| [^|]*" + library.abbrev + "[^|]* ", // 添加操作符，使用库缩写前缀
            libraryTable.getOperatorList());
      }
    }
    final Set<String> regexSeen = new HashSet<>(); // 创建集合存储已文档化的函数正则表达式
    try (LineNumberReader r = new LineNumberReader(Util.reader(f.inFile))) { // 创建行号读取器读取文档文件
      for (;;) { // 无限循环直到文件结束
        final String line = r.readLine(); // 读取一行
        if (line == null) { // 如果读到文件末尾
          break; // 退出循环
        }
        for (Map.Entry<String, PatternOp> entry : map.entrySet()) { // 遍历所有操作符映射
          if (entry.getValue().pattern.matcher(line).matches()) { // 如果当前行匹配操作符的正则表达式
            regexSeen.add(entry.getKey()); // 标记该函数已文档化
          }
        }
      }
    }
    final Set<String> regexNotSeen = new TreeSet<>(map.keySet()); // 创建有序集合存储所有操作符的正则表达式
    regexNotSeen.removeAll(regexSeen); // 移除已文档化的操作符，剩下未文档化的
    assertThat("some functions are not documented: " + map.entrySet().stream() // 断言检查是否有未文档化的函数
            .filter(e -> regexNotSeen.contains(e.getKey())) // 过滤出未文档化的条目
            .map(e -> e.getValue().opName + "(" + e.getKey() + ")") // 格式化为函数名(正则表达式)
            .collect(Collectors.joining(", ")), // 用逗号连接所有未文档化的函数
        regexNotSeen.isEmpty(), is(true)); // 断言未文档化的集合为空（所有函数都已文档化）
  } // testAllFunctionsAreDocumented 方法结束

  private void addOperators(Map<String, PatternOp> map, String prefix, // 私有方法：将操作符添加到映射中
      List<SqlOperator> operatorList) { // 参数：映射对象、前缀字符串、操作符列表
    for (SqlOperator op : operatorList) { // 遍历操作符列表
      final String name = op.getName().equals("TRANSLATE3") ? "TRANSLATE" // 特殊处理：TRANSLATE3 统一为 TRANSLATE
          : op.getName(); // 获取操作符名称
      if (op instanceof SqlSpecialOperator // 如果是特殊操作符
          || !name.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) { // 或者名称不符合标识符规则
        continue; // 跳过该操作符
      }
      final String regex; // 声明正则表达式字符串
      if (op instanceof SqlOverlapsOperator) { // 如果是时间重叠操作符
        regex = "[ ]*<td>period1 " + name + " period2</td>"; // 使用特殊的 HTML 表格格式
      } else if (op instanceof SqlFunction // 如果是函数操作符
          && (op.getOperandTypeChecker() == null // 且没有操作数类型检查器
              || op.getOperandTypeChecker().getOperandCountRange().getMin() // 或者最小操作数不为0
                  != 0)) {
        regex = prefix + "\\| .*" + name + "\\(.*"; // 生成带括号的函数正则表达式
      } else { // 其他操作符
        regex = prefix + "\\| .*" + name + ".*"; // 生成不带括号的一般操作符正则表达式
      }
      map.put(regex, new PatternOp(Pattern.compile(regex), name)); // 将正则表达式编译并存储到映射中
    }
  } // addOperators 方法结束

  /** A compiled regex and an operator name. An item to be found in the
   * documentation. */ // 编译后的正则表达式和操作符名称，表示需要在文档中查找的项
  private static class PatternOp { // 私有静态内部类：模式操作符
    final Pattern pattern; // 编译后的正则表达式模式，用于匹配文档内容
    final String opName; // 操作符名称

    private PatternOp(Pattern pattern, String opName) { // 私有构造方法
      this.pattern = pattern; // 初始化正则表达式模式
      this.opName = opName; // 初始化操作符名称
    }
  } // PatternOp 内部类结束

  /** Defines paths needed by a couple of tests. */ // 定义测试所需的文件路径
  private static class FileFixture { // 私有静态内部类：文件路径配置
    final File base; // 基础目录路径
    final File inFile; // 输入文件路径（reference.md）
    final File outFile; // 输出文件路径（生成的 reference.md）

    FileFixture() { // 构造方法：初始化文件路径
      base = TestUtil.getBaseDir(DocumentationTest.class); // 获取测试类所在的基础目录
      inFile = new File(base, "site/_docs/reference.md"); // 设置输入文件为文档目录下的 reference.md
      // TODO: replace with core/build/ when Maven is migrated to Gradle // 待办：Maven 迁移到 Gradle 后替换路径
      // It does work in Gradle, however, we don't want to create "target" folder in Gradle // 在 Gradle 中工作，但不想创建 target 文件夹
      outFile = new File(base, "core/build/reports/documentationTest/reference.md"); // 设置输出文件到构建报告目录
    }
  } // FileFixture 内部类结束
} // DocumentationTest 类结束
