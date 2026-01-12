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
package org.apache.calcite.rel.metadata.janino; // 声明包名，该类位于 org.apache.calcite.rel.metadata.janino 包下，属于元数据处理器生成工具的测试包

import org.apache.calcite.rel.metadata.BuiltInMetadata; // 导入内置元数据接口定义，包含了Calcite中所有标准元数据类型的定义（如RowCount、Cost、Selectivity等）
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider; // 导入默认的关系表达式元数据提供者，用于获取已注册的元数据处理器
import org.apache.calcite.rel.metadata.MetadataHandler; // 导入元数据处理器接口，所有元数据处理器都必须实现此接口
import org.apache.calcite.util.Sources; // 导入Calcite工具类，用于从URL或文件路径读取资源文件内容

import com.google.common.io.CharStreams; // 导入Google Guava库的字符流工具类，用于高效读取字符流内容

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.io.IOException; // 导入IO异常类，用于处理文件读写时可能出现的异常
import java.io.Reader; // 导入读取器接口，用于从字符流中读取数据
import java.io.Writer; // 导入写入器接口，用于向字符流中写入数据
import java.net.URL; // 导入URL类，用于定位和访问网络或本地资源
import java.nio.file.Files; // 导入NIO文件工具类，提供文件和目录操作的静态方法
import java.nio.file.Path; // 导入NIO路径接口，用于表示文件系统中的路径对象
import java.nio.file.Paths; // 导入NIO路径工具类，用于将字符串路径转换为Path对象

import static org.apache.calcite.linq4j.Nullness.castNonNull; // 静态导入Nullness工具类的castNonNull方法，用于显式告诉编译器对象非空

import static org.hamcrest.CoreMatchers.is; // 静态导入Hamcrest匹配器的is方法，用于断言两个值相等
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入Hamcrest的断言工具类，用于执行断言验证

/**
 * Test {@link RelMetadataHandlerGeneratorUtil}.
 * // 测试类：用于测试 RelMetadataHandlerGeneratorUtil 工具类的功能
 * // RelMetadataHandlerGeneratorUtil 是Calcite中用于动态生成元数据处理器代码的核心工具类
 * // 该测试类通过回归测试的方式，验证生成的元数据处理器代码是否符合预期
 * // 测试原理：为每个内置元数据类型生成处理器代码，然后将生成的代码与预期的代码进行对比
 * // 如果两者一致，说明代码生成逻辑正确；如果不一致，说明代码生成逻辑可能存在问题
 * // 这个测试对于保证Calcite元数据系统的稳定性和正确性至关重要
 */
class RelMetadataHandlerGeneratorUtilTest { // 声明测试类，使用JUnit 5的默认测试类命名规范
  private static final Path RESULT_DIR = Paths.get("build/metadata"); // 定义静态常量：用于存储生成的测试结果文件的目录路径，路径为 "build/metadata"，所有生成的处理器代码都会写入此目录

  @Test void testAllPredicatesGenerateHandler() { // 测试方法：测试 AllPredicates（所有谓词）元数据处理器的代码生成，使用@Test注解标记为JUnit测试方法
    checkGenerateHandler(BuiltInMetadata.AllPredicates.Handler.class); // 调用通用检查方法，传入 AllPredicates.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testCollationGenerateHandler() { // 测试方法：测试 Collation（排序规则）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.Collation.Handler.class); // 调用通用检查方法，传入 Collation.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testColumnOriginGenerateHandler() { // 测试方法：测试 ColumnOrigin（列来源）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.ColumnOrigin.Handler.class); // 调用通用检查方法，传入 ColumnOrigin.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testColumnUniquenessGenerateHandler() { // 测试方法：测试 ColumnUniqueness（列唯一性）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.ColumnUniqueness.Handler.class); // 调用通用检查方法，传入 ColumnUniqueness.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testCumulativeCostGenerateHandler() { // 测试方法：测试 CumulativeCost（累积成本）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.CumulativeCost.Handler.class); // 调用通用检查方法，传入 CumulativeCost.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testDistinctRowCountGenerateHandler() { // 测试方法：测试 DistinctRowCount（不同行数）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.DistinctRowCount.Handler.class); // 调用通用检查方法，传入 DistinctRowCount.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testDistributionGenerateHandler() { // 测试方法：测试 Distribution（数据分布）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.Distribution.Handler.class); // 调用通用检查方法，传入 Distribution.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testExplainVisibilityGenerateHandler() { // 测试方法：测试 ExplainVisibility（解释可见性）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.ExplainVisibility.Handler.class); // 调用通用检查方法，传入 ExplainVisibility.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testExpressionLineageGenerateHandler() { // 测试方法：测试 ExpressionLineage（表达式血缘）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.ExpressionLineage.Handler.class); // 调用通用检查方法，传入 ExpressionLineage.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testLowerBoundCostGenerateHandler() { // 测试方法：测试 LowerBoundCost（成本下界）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.LowerBoundCost.Handler.class); // 调用通用检查方法，传入 LowerBoundCost.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testMaxRowCountGenerateHandler() { // 测试方法：测试 MaxRowCount（最大行数）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.MaxRowCount.Handler.class); // 调用通用检查方法，传入 MaxRowCount.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testMemoryGenerateHandler() { // 测试方法：测试 Memory（内存使用）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.Memory.Handler.class); // 调用通用检查方法，传入 Memory.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testMinRowCountGenerateHandler() { // 测试方法：测试 MinRowCount（最小行数）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.MinRowCount.Handler.class); // 调用通用检查方法，传入 MinRowCount.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testNodeTypesGenerateHandler() { // 测试方法：测试 NodeTypes（节点类型）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.NodeTypes.Handler.class); // 调用通用检查方法，传入 NodeTypes.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testNonCumulativeCostGenerateHandler() { // 测试方法：测试 NonCumulativeCost（非累积成本）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.NonCumulativeCost.Handler.class); // 调用通用检查方法，传入 NonCumulativeCost.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testParallelismGenerateHandler() { // 测试方法：测试 Parallelism（并行度）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.Parallelism.Handler.class); // 调用通用检查方法，传入 Parallelism.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testPercentageOriginalRowsGenerateHandler() { // 测试方法：测试 PercentageOriginalRows（原始行百分比）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.PercentageOriginalRows.Handler.class); // 调用通用检查方法，传入 PercentageOriginalRows.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testPopulationSizeGenerateHandler() { // 测试方法：测试 PopulationSize（总体大小）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.PopulationSize.Handler.class); // 调用通用检查方法，传入 PopulationSize.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testPredicatesGenerateHandler() { // 测试方法：测试 Predicates（谓词）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.Predicates.Handler.class); // 调用通用检查方法，传入 Predicates.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testRowCountGenerateHandler() { // 测试方法：测试 RowCount（行数）元数据处理器的代码生成，这是最常用的元数据之一
    checkGenerateHandler(BuiltInMetadata.RowCount.Handler.class); // 调用通用检查方法，传入 RowCount.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testSelectivityGenerateHandler() { // 测试方法：测试 Selectivity（选择性）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.Selectivity.Handler.class); // 调用通用检查方法，传入 Selectivity.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testSizeGenerateHandler() { // 测试方法：测试 Size（大小）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.Size.Handler.class); // 调用通用检查方法，传入 Size.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testTableReferencesGenerateHandler() { // 测试方法：测试 TableReferences（表引用）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.TableReferences.Handler.class); // 调用通用检查方法，传入 TableReferences.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  @Test void testUniqueKeysGenerateHandler() { // 测试方法：测试 UniqueKeys（唯一键）元数据处理器的代码生成
    checkGenerateHandler(BuiltInMetadata.UniqueKeys.Handler.class); // 调用通用检查方法，传入 UniqueKeys.Handler 类，验证该处理器的代码生成是否正确
  } // 测试方法结束

  /**
   * Performance a regression test on the generated code for a given handler.
   * // 方法说明：对给定的元数据处理器执行回归测试
   * // 回归测试的目的：确保代码生成逻辑的修改不会破坏已有的功能
   * // 测试流程：
   * // 1. 调用 RelMetadataHandlerGeneratorUtil.generateHandler() 动态生成处理器代码
   * // 2. 将生成的代码写入到 build/metadata 目录，方便开发者查看和调试
   * // 3. 从资源文件中读取预期的代码（预先生成的标准代码）
   * // 4. 验证生成的代码与预期代码是否完全一致
   * // 参数说明：handlerClass - 要测试的元数据处理器类，必须是 MetadataHandler 的子类
   * // 返回值：无
   * // 异常处理：如果生成的代码与预期代码不一致，测试会失败
   */
  private void checkGenerateHandler(Class<? extends MetadataHandler<?>> handlerClass) { // 声明私有方法，接收一个泛型参数，限定为 MetadataHandler 的子类
    RelMetadataHandlerGeneratorUtil.HandlerNameAndGeneratedCode nameAndGeneratedCode = // 声明变量，用于存储生成的处理器名称和代码
        RelMetadataHandlerGeneratorUtil.generateHandler(handlerClass, // 调用代码生成工具的静态方法，传入处理器类
            DefaultRelMetadataProvider.INSTANCE.handlers(handlerClass)); // 获取默认元数据提供者中注册的该处理器实例，用于生成代码
    String resourcePath = // 声明变量，用于存储资源文件路径
        nameAndGeneratedCode.getHandlerName().replace(".", "/") + ".java"; // 将处理器名称转换为资源路径，例如 "org.apache.calcite.XXX" -> "org/apache/calcite/XXX.java"
    writeActualResults(resourcePath, // 调用方法将生成的代码写入文件，方便调试和查看
        nameAndGeneratedCode.getGeneratedCode()); // 传入生成的代码内容
    String expected = readResource(resourcePath); // 调用方法从资源文件中读取预期的代码
    assert !expected.contains("\r") : "Expected code should not contain \\r"; // 断言：确保预期代码不包含Windows风格的回车符\r
    assert !nameAndGeneratedCode.getGeneratedCode().equals("\r") // 断言：确保生成的代码不等于"\r"（防止Windows换行符问题）
        : "Generated code should not contain \\r"; // 断言失败时的错误提示信息
    assertThat(nameAndGeneratedCode.getGeneratedCode(), is(expected)); // 使用Hamcrest断言验证生成的代码与预期代码完全相等
  } // 方法结束

  private static String readResource(String resourceName) { // 声明私有静态方法，用于从资源文件中读取内容，参数为资源名称
    URL url = // 声明URL变量，用于存储资源文件的URL地址
        castNonNull( // 调用castNonNull方法，显式告诉编译器该URL对象非空，避免空指针警告
            RelMetadataHandlerGeneratorUtilTest.class.getClassLoader() // 获取当前测试类的类加载器
                .getResource(resourceName)); // 使用类加载器加载指定名称的资源文件，返回URL对象
    try (Reader reader = Sources.of(url).reader()) { // 使用try-with-resources语法，自动关闭Reader，从URL创建字符流读取器
      return CharStreams.toString(reader).replace("\r\n", "\n"); // 使用Guava工具将Reader内容转换为字符串，并将Windows换行符\r\n替换为Unix换行符\n，确保跨平台一致性
    } catch (IOException e) { // 捕获IO异常
      throw new RuntimeException(e); // 将受检异常转换为非受检异常，抛出运行时异常
    } // try-catch块结束
  } // 方法结束

  private static void writeActualResults(String resourceName, String expectedResults) { // 声明私有静态方法，用于将生成的代码写入文件，参数为资源名称和要写入的内容
    try { // 开始try块，捕获IO异常
      Path target = RESULT_DIR.resolve(resourceName); // 将资源名称解析为 RESULT_DIR 下的完整路径
      Files.createDirectories(target.getParent()); // 创建目标文件的所有父目录，如果目录已存在则不报错
      if (Files.exists(target)) { // 检查目标文件是否已存在
        Files.delete(target); // 如果文件存在，先删除旧文件，确保写入最新的内容
      } // if块结束
      try (Writer writer = Files.newBufferedWriter(target)) { // 使用try-with-resources语法，自动关闭Writer，创建缓冲写入器
        writer.write(expectedResults); // 将生成的代码内容写入文件
      } // try-with-resources块结束
    } catch (IOException e) { // 捕获IO异常
      throw new RuntimeException(e); // 将受检异常转换为非受检异常，抛出运行时异常
    } // try-catch块结束
  } // 方法结束
} // 类定义结束
