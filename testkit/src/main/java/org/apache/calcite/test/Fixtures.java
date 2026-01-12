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
package org.apache.calcite.test; // 声明包名，该类位于 org.apache.calcite.test 包下，用于测试相关功能

import org.apache.calcite.sql.parser.SqlParserFixture; // 导入SQL解析器测试工具类，用于提供SQL解析测试的fixture
import org.apache.calcite.sql.parser.SqlParserTest; // 导入SQL解析器测试类，用于创建SQL解析测试实例
import org.apache.calcite.sql.test.SqlOperatorFixture; // 导入SQL操作符测试工具类，用于提供SQL操作符测试的fixture

/** Fluent test fixtures for typical Calcite tests (parser, validator, // 类注释：为Calcite典型测试（解析器、验证器、sql-to-rel和rel-rules）提供流式测试工具，可以在依赖项目中轻松使用
 * sql-to-rel and rel-rules) that can easily be used in dependent projects. */
public class Fixtures { // 定义Fixtures类，这是一个工具类，用于创建各种测试的fixture（测试夹具），提供统一的测试环境配置
  private Fixtures() {} // 私有构造方法，防止实例化，因为该类只提供静态工厂方法来创建各种测试fixture

  /** Creates a fixture for parser tests. */ // 方法注释：创建一个用于解析器测试的fixture
  public static SqlParserFixture forParser() { // 公共静态方法，返回SqlParserFixture对象，用于SQL解析器测试
    return new SqlParserTest().fixture(); // 创建一个新的SqlParserTest实例并调用其fixture()方法，返回配置好的解析器测试fixture
  }

  /** Creates a fixture for validation tests. */ // 方法注释：创建一个用于验证器测试的fixture
  public static SqlValidatorFixture forValidator() { // 公共静态方法，返回SqlValidatorFixture对象，用于SQL验证器测试
    return SqlValidatorTestCase.FIXTURE; // 返回SqlValidatorTestCase中预定义的FIXTURE常量，这是一个配置好的验证器测试fixture
  }

  /** Creates a fixture for SQL-to-Rel tests. */ // 方法注释：创建一个用于SQL到关系代数转换测试的fixture
  public static SqlToRelFixture forSqlToRel() { // 公共静态方法，返回SqlToRelFixture对象，用于SQL到Rel转换测试
    return SqlToRelFixture.DEFAULT; // 返回SqlToRelFixture中预定义的DEFAULT常量，这是一个配置好的SQL-to-Rel测试fixture
  }

  /** Creates a fixture for rule tests. */ // 方法注释：创建一个用于规则测试的fixture
  public static RelOptFixture forRules() { // 公共静态方法，返回RelOptFixture对象，用于关系代数优化规则测试
    return RelOptFixture.DEFAULT; // 返回RelOptFixture中预定义的DEFAULT常量，这是一个配置好的规则测试fixture
  }

  /** Creates a fixture for operator tests. */ // 方法注释：创建一个用于操作符测试的fixture
  public static SqlOperatorFixture forOperators(boolean execute) { // 公共静态方法，接受一个boolean参数execute，返回SqlOperatorFixture对象，用于SQL操作符测试
    return execute // 根据execute参数决定是否配置测试执行器
        ? SqlOperatorFixtureImpl.DEFAULT.withTester(t -> SqlOperatorTest.TESTER) // 如果execute为true，返回配置了SqlOperatorTest.TESTER的fixture，会实际执行测试
        : SqlOperatorFixtureImpl.DEFAULT; // 如果execute为false，返回默认的fixture，不执行实际测试
  }

  /** Creates a fixture for metadata tests. */ // 方法注释：创建一个用于元数据测试的fixture
  public static RelMetadataFixture forMetadata() { // 公共静态方法，返回RelMetadataFixture对象，用于关系代数元数据测试
    return RelMetadataFixture.DEFAULT; // 返回RelMetadataFixture中预定义的DEFAULT常量，这是一个配置好的元数据测试fixture
  }
} // 类结束
