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
package org.apache.calcite.test; // 定义包名，该类位于org.apache.calcite.test包下

import org.apache.calcite.sql.SqlNode; // 导入SqlNode类，表示SQL语法树的节点
import org.apache.calcite.sql.parser.SqlParseException; // 导入SqlParseException类，表示SQL解析异常
import org.apache.calcite.sql.parser.SqlParser; // 导入SqlParser类，用于解析SQL语句
import org.apache.calcite.sql.test.SqlOperatorFixture; // 导入SqlOperatorFixture接口，用于SQL操作符测试
import org.apache.calcite.sql.test.SqlTestFactory; // 导入SqlTestFactory类，用于创建SQL测试环境

import org.junit.jupiter.api.Disabled; // 导入Disabled注解，用于禁用测试方法

import java.util.function.Consumer; // 导入Consumer函数式接口，用于消费字符串
import java.util.function.UnaryOperator; // 导入UnaryOperator函数式接口，用于转换SqlTestFactory

/**
 * 本类是SqlOperatorTest的一个变体版本，它在执行测试程序之前会先解析SQL语句，然后再将其反解析回SQL字符串
 * 虽然与{@link org.apache.calcite.sql.parser.SqlUnParserTest}类似，但这个测试类还会在反解析后验证代码的正确性
 * 
 * 类的作用：
 * 1. 测试SQL操作符的解析和反解析功能
 * 2. 确保SQL语句可以被正确解析为语法树，然后又能正确地反解析回SQL字符串
 * 3. 验证反解析后的SQL语句仍然能够正确执行，产生相同的结果
 * 4. 继承自CalciteSqlOperatorTest，复用了大量的SQL操作符测试用例
 * 5. 通过UnparseTester和SqlOperatorFixtureUnparseImpl实现了先解析再测试的流程
 * 
 * 核心工作流程：
 * 1. 接收原始SQL语句
 * 2. 使用SqlParser将SQL解析为SqlNode（语法树）
 * 3. 使用toSqlString方法将SqlNode反解析回SQL字符串
 * 4. 对反解析后的SQL执行测试验证
 * 
 * 这个测试类的主要价值在于：
 * - 验证Calcite的SQL解析器和反解析器的正确性
 * - 确保SQL语法树的序列化和反序列化是双向一致的
 * - 发现和修复SQL操作符在反解析过程中可能出现的问题
 */
@SuppressWarnings("JavadocReference") // 抑制Javadoc引用的警告，因为引用的类可能在某些环境中不可见
public class SqlOperatorUnparseTest extends CalciteSqlOperatorTest { // 定义SqlOperatorUnparseTest类，继承自CalciteSqlOperatorTest
  /** 
   * 内部静态类：SqlOperatorFixtureUnparseImpl
   * 
   * 作用：这是一个测试装置（Fixture）的实现，它在运行操作符测试之前会先解析和反解析查询语句
   * 
   * 继承关系：继承自SqlOperatorFixtureImpl，复用了基本的测试装置功能
   * 
   * 核心功能：
   * 1. 包装了UnparseTester，使得每个测试在执行前都会经过解析和反解析过程
   * 2. 提供了withFactory方法，允许动态转换SqlTestFactory
   * 3. 维护了一个DEFAULT静态实例，作为默认的测试装置
   * 
   * 设计模式：使用了装饰器模式，在原有的SqlOperatorFixtureImpl基础上增加了解析和反解析的功能
   */
  static class SqlOperatorFixtureUnparseImpl extends SqlOperatorFixtureImpl { // 定义内部静态类SqlOperatorFixtureUnparseImpl，继承自SqlOperatorFixtureImpl
    /**
     * 构造方法
     * 
     * @param factory SqlTestFactory对象，用于创建SQL测试环境
     * 
     * 作用：初始化测试装置，创建一个UnparseTester实例作为测试器
     * 
     * 参数说明：
     * - factory: SQL测试工厂，用于创建解析器、验证器等测试所需组件
     * 
     * 调用父类构造函数：
     * - super(factory, new UnparseTester(factory), false)
     *   第一个参数：factory - 传入的测试工厂
     *   第二个参数：new UnparseTester(factory) - 创建自定义的UnparseTester，它会先解析再反解析SQL
     *   第三个参数：false - 表示不启用某些特殊功能
     */
    SqlOperatorFixtureUnparseImpl(SqlTestFactory factory) { // 构造方法，接收SqlTestFactory参数
      super(factory, new UnparseTester(factory), false); // 调用父类构造函数，传入工厂、UnparseTester实例和false标志
    }

    /**
     * 方法：getUnparseTester
     * 
     * 作用：获取内部的UnparseTester实例
     * 
     * 返回值：UnparseTester对象，这是自定义的测试器，实现了解析和反解析功能
     * 
     * 为什么需要向下转型：
     * - 父类SqlOperatorFixtureImpl中的getTester()方法返回的是SqlTester类型
     * - 但我们的UnparseTester实现了更丰富的API，比如withFactory方法
     * - 需要向下转型才能访问UnparseTester特有的方法
     * 
     * 使用场景：
     * - 在withFactory方法中需要调用UnparseTester的withFactory方法
     * - 其他需要访问UnparseTester特有功能的场景
     */
    UnparseTester getUnparseTester() { // 定义获取UnparseTester的方法
      return (UnparseTester) this.getTester(); // 向下转型，将SqlTester转为UnparseTester并返回
    }

    /**
     * 静态常量：DEFAULT
     * 
     * 作用：提供默认的SqlOperatorFixtureUnparseImpl实例，供测试使用
     * 
     * 初始化：使用SqlTestFactory.INSTANCE创建默认实例
     * 
     * 为什么使用静态常量：
     * - 避免重复创建相同的测试装置
     * - 提供一个全局可用的默认配置
     * - 简化测试代码的编写
     * 
     * 使用场景：
     * - 在fixture()方法中返回此默认实例
     * - 测试类中可以通过fixture()方法获取此实例
     */
    public static final SqlOperatorFixtureImpl DEFAULT = // 定义公共静态常量DEFAULT
        new SqlOperatorFixtureUnparseImpl(SqlTestFactory.INSTANCE); // 使用默认的SqlTestFactory创建实例

    /**
     * 方法：withFactory
     * 
     * @param transform UnaryOperator<SqlTestFactory>，用于转换SqlTestFactory的函数
     * @return SqlOperatorFixture，返回转换后的新测试装置
     * 
     * 作用：创建一个新的测试装置，其中SqlTestFactory被指定的转换函数修改
     * 
     * 实现细节：
     * 1. 调用父类的withFactory方法，对fixture本身的factory进行转换
     * 2. 调用withTester方法，对内部的UnparseTester的factory也进行相同的转换
     * 3. 这样确保了fixture和tester使用的是同一个转换后的factory
     * 
     * 为什么需要同时转换fixture和tester的factory：
     * - fixture和tester都依赖SqlTestFactory来创建测试环境
     * - 保持一致性，确保两者使用相同的配置
     * - 避免因factory不一致导致的测试问题
     * 
     * 链式调用：支持链式调用，可以连续应用多个转换
     */
    @Override public SqlOperatorFixture withFactory(UnaryOperator<SqlTestFactory> transform) { // 重写withFactory方法，接收转换函数
      return super // 返回父类方法的结果
          .withFactory(transform) // 对fixture的factory应用转换
          // 将转换函数也传递给测试器，确保tester的factory也被转换
          .withTester(t -> this.getUnparseTester().withFactory(transform)); // 对tester的factory应用相同的转换
    }
  }

  /**
   * 方法：fixture
   * 
   * @return SqlOperatorFixture，返回默认的测试装置实例
   * 
   * 作用：提供测试装置的实例，所有测试方法都通过这个fixture来执行
   * 
   * 重写原因：
   * - 父类CalciteSqlOperatorTest的fixture()方法返回的是普通的SqlOperatorFixture
   * - 我们需要返回带有解析和反解析功能的SqlOperatorFixtureUnparseImpl
   * 
   * 返回值：SqlOperatorFixtureUnparseImpl.DEFAULT
   * - 返回预定义的默认实例
   * - 这个实例内部使用UnparseTester，会在测试前先解析和反解析SQL
   * 
   * 使用场景：
   * - 所有测试方法通过fixture()获取测试装置
   * - 测试装置负责管理测试环境的创建、SQL的解析和反解析、结果的验证等
   */
  @Override protected SqlOperatorFixture fixture() { // 重写fixture方法
    return SqlOperatorFixtureUnparseImpl.DEFAULT; // 返回默认的SqlOperatorFixtureUnparseImpl实例
  }

  /** 
   * 内部静态类：UnparseTester
   * 
   * 作用：这是一个自定义的测试器，它会先解析SQL语句，再反解析回SQL字符串，然后执行测试
   * 
   * 继承关系：继承自TesterImpl，复用了基本的测试功能
   * 
   * 核心功能：
   * 1. rewrite方法：解析SQL为语法树，再反解析回SQL字符串
   * 2. check方法：在执行验证前先调用rewrite方法处理SQL
   * 3. forEachQuery方法：为每个测试查询生成测试语句
   * 
   * 工作流程：
   * 1. 接收原始SQL语句
   * 2. 使用SqlParser将SQL解析为SqlNode（语法树）
   * 3. 使用toSqlString方法将SqlNode反解析回SQL字符串
   * 4. 对反解析后的SQL执行测试验证
   * 
   * 设计目的：
   * - 验证SQL的解析和反解析是双向一致的
   * - 确保反解析后的SQL仍然可以正确执行
   * - 发现SQL操作符在反解析过程中可能存在的问题
   */
  static class UnparseTester extends TesterImpl { // 定义内部静态类UnparseTester，继承自TesterImpl
    /**
     * 成员变量：factory
     * 
     * 类型：public final SqlTestFactory
     * 
     * 作用：SQL测试工厂，用于创建SQL解析器、验证器等测试所需的组件
     * 
     * 为什么需要这个成员变量：
     * - rewrite方法中需要使用factory来创建SqlParser
     * - 将factory存储为成员变量，可以在多个方法中复用
     * 
     * 常见用途：
     * - 创建SQL解析器：factory.createParser(sql)
     * - 创建SQL验证器：factory.createValidator()
     * - 创建数据源：factory.createDataSource()
     * 
     * final修饰：
     * - factory在构造函数中初始化后不可改变
     * - 确保测试过程中使用的是同一个factory实例
     */
    public final SqlTestFactory factory; // 定义公共final成员变量factory，类型为SqlTestFactory

    /**
     * 构造方法
     * 
     * @param factory SqlTestFactory对象，用于创建SQL测试环境
     * 
     * 作用：初始化UnparseTester，保存SqlTestFactory引用
     * 
     * 参数说明：
     * - factory: SQL测试工厂，用于创建解析器、验证器等测试所需组件
     * 
     * 实现细节：
     * - 将factory保存为成员变量，供后续方法使用
     * - factory用于在rewrite方法中创建SqlParser
     */
    UnparseTester(SqlTestFactory factory) { // 构造方法，接收SqlTestFactory参数
      this.factory = factory; // 将传入的factory赋值给成员变量
    }

    /**
     * 方法：withFactory
     * 
     * @param transform UnaryOperator<SqlTestFactory>，用于转换SqlTestFactory的函数
     * @return TesterImpl，返回转换后的新测试器
     * 
     * 作用：创建一个新的UnparseTester实例，其中SqlTestFactory被指定的转换函数修改
     * 
     * 实现细节：
     * 1. 对当前的factory应用转换函数，得到新的factory
     * 2. 使用新的factory创建一个新的UnparseTester实例
     * 3. 返回新实例，保持原实例不变（不可变对象模式）
     * 
     * 为什么返回新实例而不是修改当前实例：
     * - 遵循不可变对象原则，避免副作用
     * - 支持链式调用，可以连续应用多个转换
     * - 保持线程安全
     * 
     * 使用场景：
     * - 在SqlOperatorFixtureUnparseImpl的withFactory方法中被调用
     * - 用于动态调整测试配置
     */
    TesterImpl withFactory(UnaryOperator<SqlTestFactory> transform) { // 定义withFactory方法，接收转换函数
      return new UnparseTester(transform.apply(this.factory)); // 对factory应用转换，创建新的UnparseTester实例
    }

    /**
     * 方法：rewrite
     * 
     * @param sql 原始SQL字符串
     * @return String 反解析后的SQL字符串
     * @throws SqlParseException 如果SQL解析失败则抛出此异常
     * 
     * 作用：将SQL语句解析为语法树，然后再反解析回SQL字符串
     * 
     * 核心流程：
     * 1. 使用SqlTestFactory创建SqlParser
     * 2. 使用SqlParser将SQL字符串解析为SqlNode（语法树）
     * 3. 使用SqlNode的toSqlString方法将语法树反解析回SQL字符串
     * 4. 返回反解析后的SQL字符串
     * 
     * 详细步骤：
     * 1. factory.createParser(sql): 创建SQL解析器，传入原始SQL
     * 2. parser.parseStmt(): 解析SQL语句，得到SqlNode（语法树的根节点）
     * 3. sqlNode.toSqlString(c -> c.withClauseStartsLine(false)): 将语法树转换为SQL字符串
     *    - withClauseStartsLine(false): 配置子句不从新行开始，保持紧凑格式
     * 4. getSql(): 获取生成的SQL字符串
     * 
     * 为什么需要这个方法：
     * - 验证SQL解析器和反解析器的正确性
     * - 确保SQL语法树的序列化和反序列化是双向一致的
     * - 规范化SQL格式（例如统一大小写、空格等）
     * 
     * 可能的变化：
     * - 关键字大小写可能改变
     * - 空格和换行可能被规范化
     * - 某些语法糖可能被展开
     * 
     * 异常处理：
     * - 如果SQL语法错误，抛出SqlParseException
     * - 调用者需要处理这个异常
     */
    String rewrite(String sql) throws SqlParseException { // 定义rewrite方法，接收SQL字符串，可能抛出解析异常
      final SqlParser parser = factory.createParser(sql); // 使用factory创建SQL解析器，传入原始SQL
      final SqlNode sqlNode = parser.parseStmt(); // 使用解析器解析SQL语句，得到SqlNode（语法树）
      return sqlNode.toSqlString(c -> c.withClauseStartsLine(false)).getSql(); // 将语法树反解析为SQL字符串，并返回
    }

    /**
     * 方法：forEachQuery
     * 
     * @param factory SqlTestFactory，SQL测试工厂
     * @param expression String，SQL表达式或查询
     * @param consumer Consumer<String>，消费生成的查询字符串的回调函数
     * 
     * 作用：为给定的表达式生成查询，并将生成的查询传递给consumer处理
     * 
     * 实现细节：
     * - 调用buildQuery2方法将表达式转换为完整的查询语句
     * - 将生成的查询传递给consumer进行处理
     * 
     * 为什么需要这个方法：
     * - 支持批量测试，可以一次处理多个查询
     * - 将表达式转换为完整的查询语句，便于测试
     * - 提供灵活的处理方式，通过consumer可以自定义处理逻辑
     * 
     * buildQuery2方法的作用：
     * - 将简单的表达式转换为完整的SELECT语句
     * - 添加必要的FROM子句
     * - 确保生成的SQL是可执行的
     * 
     * 使用场景：
     * - 在测试框架中遍历多个测试查询
     * - 为SQL表达式生成可执行的查询语句
     */
    @Override public void forEachQuery( // 重写forEachQuery方法
        SqlTestFactory factory, String expression, Consumer<String> consumer) { // 接收工厂、表达式和消费者
      consumer.accept(buildQuery2(factory, expression)); // 构建查询并传递给消费者处理
    }

    /**
     * 方法：check
     * 
     * @param factory SqlTestFactory，SQL测试工厂
     * @param sql String，要测试的SQL语句
     * @param typeChecker TypeChecker，类型检查器，用于验证结果类型
     * @param parameterChecker ParameterChecker，参数检查器，用于验证参数
     * @param resultChecker ResultChecker，结果检查器，用于验证查询结果
     * 
     * 作用：检查SQL语句的执行结果，但在检查前会先解析和反解析SQL
     * 
     * 核心流程：
     * 1. 调用rewrite方法，将原始SQL解析为语法树，再反解析回SQL字符串
     * 2. 调用父类的check方法，使用反解析后的SQL执行测试验证
     * 3. 如果解析过程中出现异常，包装为RuntimeException抛出
     * 
     * 详细步骤：
     * 1. String optQuery = this.rewrite(sql): 解析并反解析SQL，得到规范化后的SQL
     * 2. super.check(factory, optQuery, ...): 使用父类的check方法验证反解析后的SQL
     *    - typeChecker: 验证结果的数据类型是否正确
     *    - parameterChecker: 验证参数是否正确
     *    - resultChecker: 验证查询结果是否符合预期
     * 3. catch (SqlParseException e): 捕获解析异常
     * 4. throw new RuntimeException(e): 将检查异常包装为运行时异常抛出
     * 
     * 为什么要先反解析再测试：
     * - 确保SQL可以被正确解析和反解析
     * - 验证反解析后的SQL仍然可以正确执行
     * - 发现SQL操作符在反解析过程中的问题
     * - 确保测试覆盖的是反解析后的SQL，而不是原始SQL
     * 
     * 异常处理：
     * - SqlParseException: SQL解析失败，包装为RuntimeException抛出
     * - 这样可以避免修改方法签名，保持与父类接口一致
     * 
     * 测试覆盖：
     * - SQL语法正确性
     * - SQL解析器正确性
     * - SQL反解析器正确性
     * - 查询执行结果正确性
     * - 结果类型正确性
     * - 参数正确性
     */
    @Override public void check(SqlTestFactory factory, String sql, TypeChecker typeChecker, // 重写check方法，接收工厂、SQL和检查器
        ParameterChecker parameterChecker, ResultChecker resultChecker) { // 接收参数检查器和结果检查器
      try { // 开始try块，捕获可能的异常
        String optQuery = this.rewrite(sql); // 调用rewrite方法，解析并反解析SQL
        super.check(factory, optQuery, typeChecker, parameterChecker, resultChecker); // 调用父类check方法验证反解析后的SQL
      } catch (SqlParseException e) { // 捕获SQL解析异常
        throw new RuntimeException(e); // 将解析异常包装为运行时异常抛出
      }
    }
  }

  // 下面被禁用的每个测试方法都对应一个已知的bug
  // 当对应的bug被修复后，这些测试方法应该被删除（即移除@Disabled注解）

  /**
   * 测试方法：testSafeOffsetOperator
   * 
   * 作用：测试SAFE_OFFSET操作符的解析和反解析功能
   * 
   * 为什么被禁用：
   * - 存在一个已知bug：CALCITE-5998
   * - SAFE_OFFSET操作符可能导致数组越界异常
   * - 在反解析过程中可能出现问题
   * 
   * Bug详情：
   * - JIRA链接：https://issues.apache.org/jira/browse/CALCITE-5998
   * - 问题描述：SAFE_OFFSET操作符在反解析时可能导致索引越界异常
   * 
   * 实现方式：
   * - 直接调用父类的testSafeOffsetOperator方法
   * - 使用@Disabled注解禁用此测试
   * 
   * 未来处理：
   * - 当bug修复后，应该移除@Disabled注解
   * - 或者如果测试不再需要，可以删除此方法
   */
  @Override @Disabled("https://issues.apache.org/jira/browse/CALCITE-5998 " // 使用@Disabled注解禁用此测试，引用bug链接
      + "The SAFE_OFFSET operator can cause an index out of bounds exception") // 说明bug原因：SAFE_OFFSET操作符可能导致索引越界
  void testSafeOffsetOperator() { // 定义测试方法
    super.testSafeOffsetOperator(); // 调用父类的测试方法
  }

  /**
   * 测试方法：testContainsSubstrFunc
   * 
   * 作用：测试CONTAINS_SUBSTR函数的解析和反解析功能
   * 
   * 为什么被禁用：
   * - 存在一个已知bug：CALCITE-6002
   * - CONTAINS_SUBSTR函数无法正确反解析
   * - 反解析后的SQL可能不正确或丢失某些信息
   * 
   * Bug详情：
   * - JIRA链接：https://issues.apache.org/jira/browse/CALCITE-6002
   * - 问题描述：CONTAINS_SUBSTR函数在反解析时出现错误
   * 
   * 实现方式：
   * - 直接调用父类的testContainsSubstrFunc方法
   * - 使用@Disabled注解禁用此测试
   * 
   * 未来处理：
   * - 当bug修复后，应该移除@Disabled注解
   * - 或者如果测试不再需要，可以删除此方法
   * 
   * CONTAINS_SUBSTR函数说明：
   * - 这是一个字符串包含检查函数
   * - 用于检查一个字符串是否包含另一个子字符串
   * - 在某些数据库中可能有特殊的语法要求
   */
  @Override @Disabled("https://issues.apache.org/jira/browse/CALCITE-6002 " // 使用@Disabled注解禁用此测试，引用bug链接
      + "CONTAINS_SUBSTR does not unparse correctly") // 说明bug原因：CONTAINS_SUBSTR无法正确反解析
  void testContainsSubstrFunc() { // 定义测试方法
    super.testContainsSubstrFunc(); // 调用父类的测试方法
  }
}
