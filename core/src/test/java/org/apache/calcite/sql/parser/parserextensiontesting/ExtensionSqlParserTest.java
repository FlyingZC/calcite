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
// Apache许可证声明：该文件遵循Apache 2.0许可证，允许在遵守许可证条款的情况下使用、修改和分发
package org.apache.calcite.sql.parser.parserextensiontesting; // 包声明：该类位于org.apache.calcite.sql.parser.parserextensiontesting包下，专门用于测试SQL解析器的扩展功能

import org.apache.calcite.sql.SqlNode; // 导入SqlNode类：这是Calcite中SQL语法树的抽象节点类，所有SQL语句解析后的结果都表示为SqlNode及其子类的实例
import org.apache.calcite.sql.parser.SqlParserFixture; // 导入SqlParserFixture类：这是SQL解析器的测试工具类，提供了配置和执行SQL解析测试的fixture（测试夹具）
import org.apache.calcite.sql.parser.SqlParserTest; // 导入SqlParserTest类：这是SQL解析器的基础测试类，提供了测试SQL解析功能的通用方法和框架

import org.hamcrest.core.IsNull; // 导入IsNull类：这是Hamcrest测试框架的匹配器，用于验证对象是否为null，常用于断言测试结果
import org.junit.jupiter.api.Test; // 导入Test注解：这是JUnit 5的测试注解，用于标记测试方法，JUnit会自动执行带有此注解的方法

/**
 * Testing for extension functionality of the base SQL parser impl.
 * // 测试基础SQL解析器实现的扩展功能
 *
 * <p>This test runs all test cases of the base {@link SqlParserTest}, as well
 * as verifying specific extension points.
 * // 该测试运行基础SqlParserTest的所有测试用例，并验证特定的扩展点
 */
// 类注释：ExtensionSqlParserTest类继承自SqlParserTest，专门用于测试SQL解析器的扩展功能
// 它会运行父类的所有测试用例，同时验证自定义的SQL语法扩展是否正常工作
// 这个类的主要目的是确保Calcite的SQL解析器能够通过扩展机制支持自定义的SQL语法和语句
class ExtensionSqlParserTest extends SqlParserTest { // 类定义：ExtensionSqlParserTest继承自SqlParserTest，重写了fixture()方法以使用扩展的SQL解析器实现

  @Override public SqlParserFixture fixture() { // 方法定义：重写父类的fixture()方法，返回配置了扩展SQL解析器的测试夹具
    // @Override注解表示该方法重写了父类的方法
    // SqlParserFixture是测试夹具，用于配置和执行SQL解析测试
    return super.fixture() // 调用父类的fixture()方法获取基础的测试夹具配置
        .withConfig(c -> c.withParserFactory(ExtensionSqlParserImpl.FACTORY)); // 通过withConfig方法配置解析器工厂，使用ExtensionSqlParserImpl.FACTORY作为自定义解析器工厂
    // c -> c.withParserFactory(...)是一个lambda表达式，用于配置SqlParser.Config对象
    // ExtensionSqlParserImpl.FACTORY是自定义的SQL解析器工厂，用于创建支持扩展语法的解析器实例
    // 这样配置后，所有测试用例都会使用这个扩展的解析器来解析SQL语句
  }

  @Test void testAlterSystemExtension() { // 测试方法定义：测试ALTER SYSTEM扩展语法，@Test注解标记这是一个JUnit测试方法
    // 测试ALTER SYSTEM UPLOAD JAR语句的解析功能
    // 这是Calcite支持的自定义SQL语法扩展，用于上传JAR文件到系统
    sql("alter system upload jar '/path/to/jar'") // sql()方法：传入要解析的SQL语句字符串，这里测试的是alter system upload jar语句
        .ok("ALTER SYSTEM UPLOAD JAR '/path/to/jar'"); // .ok()方法：验证解析成功，并期望输出的规范化SQL语句为"ALTER SYSTEM UPLOAD JAR '/path/to/jar'"
    // 这个测试验证了自定义的ALTER SYSTEM扩展语法能够被正确解析
    // 解析器能够识别"alter system upload jar"这个语法结构，并将其转换为正确的SqlNode对象
    // .ok()方法会检查解析后的SQL语句是否与期望的规范化格式一致
  }

  @Test void testAlterSystemExtensionWithoutAlter() { // 测试方法定义：测试不带ALTER关键字的扩展语法，验证错误处理
    // 测试缺少ALTER关键字时的错误处理
    // 这个测试验证解析器能够正确检测并报告语法错误
    // We need to include the scope for custom alter operations
    // 我们需要为自定义的alter操作包含作用域（scope）
    // 这意味着自定义的alter操作必须在ALTER语句的作用域内才能被识别
    sql("^upload^ jar '/path/to/jar'") // sql()方法：传入包含语法错误的SQL语句，^符号标记了预期出现错误的位置
        .fails("(?s).*Encountered \"upload\" at .*"); // .fails()方法：验证解析失败，并期望错误消息匹配给定的正则表达式
    // "(?s).*Encountered \"upload\" at .*"是一个正则表达式，(?s)表示单行模式，.匹配包括换行符在内的所有字符
    // 这个正则表达式匹配包含"Encountered \"upload\" at"的错误消息
    // 测试验证了当缺少ALTER关键字时，解析器能够正确识别并报告语法错误
    // ^upload^表示在upload位置应该出现错误，解析器应该检测到这个语法错误
  }

  @Test void testCreateTable() { // 测试方法定义：测试CREATE TABLE语句的解析功能
    // 测试标准的CREATE TABLE语句解析
    // 验证解析器能够正确处理带数据类型和约束的表创建语句
    sql("CREATE TABLE foo.baz(i INTEGER, j VARCHAR(10) NOT NULL)") // sql()方法：传入CREATE TABLE语句，包含表名foo.baz和两个列定义
        .ok("CREATE TABLE `FOO`.`BAZ` (`I` INTEGER, `J` VARCHAR(10) NOT NULL)"); // .ok()方法：验证解析成功，并期望输出的规范化SQL语句中标识符被反引号括起来并转换为大写
    // 这个测试验证了CREATE TABLE语句的解析功能
    // 原始语句：CREATE TABLE foo.baz(i INTEGER, j VARCHAR(10) NOT NULL)
    // 期望输出：CREATE TABLE `FOO`.`BAZ` (`I` INTEGER, `J` VARCHAR(10) NOT NULL)
    // 解析器将标识符（表名和列名）规范化为大写并用反引号括起来
    // i INTEGER -> `I` INTEGER
    // j VARCHAR(10) NOT NULL -> `J` VARCHAR(10) NOT NULL
    // foo.baz -> `FOO`.`BAZ`
  }

  @Test void testExtendedSqlStmt() { // 测试方法定义：测试扩展的SQL语句解析功能
    // 测试自定义的DESCRIBE扩展语法
    // 验证特定的扩展语句能够被正确解析或正确报错
    sql("DESCRIBE SPACE POWER") // sql()方法：传入DESCRIBE SPACE POWER语句，这是自定义的扩展语法
        .node(new IsNull<SqlNode>()); // .node()方法：验证解析后的SqlNode对象，使用IsNull匹配器验证结果为null
    // 这个测试验证了DESCRIBE SPACE POWER语句被解析为null节点
    // 可能是因为这个扩展语法在当前实现中被忽略或特殊处理
    // IsNull<SqlNode>()是一个Hamcrest匹配器，用于断言对象为null
    sql("DESCRIBE SEA ^POWER^") // sql()方法：传入包含语法错误的DESCRIBE语句，^POWER^标记了预期出现错误的位置
        .fails("(?s)Incorrect syntax near the keyword 'POWER' at line 1, column 14.*"); // .fails()方法：验证解析失败，并期望错误消息匹配给定的正则表达式
    // 这个测试验证了错误的DESCRIBE语句能够被正确识别并报告语法错误
    // "(?s)Incorrect syntax near the keyword 'POWER' at line 1, column 14.*"是一个正则表达式
    // 它匹配包含"Incorrect syntax near the keyword 'POWER' at line 1, column 14"的错误消息
    // 测试验证了解析器能够检测到SEA后面跟POWER的语法错误，并在第1行第14列位置报告错误
  }
} // 类结束：ExtensionSqlParserTest类的结束大括号