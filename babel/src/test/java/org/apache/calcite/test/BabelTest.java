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
// Apache许可证头，声明代码的版权和使用许可，这是标准的Apache开源项目许可证声明
package org.apache.calcite.test;  // 声明包名，该类属于org.apache.calcite.test包，是Calcite测试包的一部分

// 导入Calcite配置相关的类，用于配置Calcite连接属性
import org.apache.calcite.config.CalciteConnectionProperty;  // 导入Calcite连接属性枚举类，用于配置连接参数
// 导入类型系统相关的类，用于处理数据类型
import org.apache.calcite.rel.type.DelegatingTypeSystem;  // 导入委托类型系统类，用于创建自定义类型系统
import org.apache.calcite.rel.type.TimeFrameSet;  // 导入时间帧集合类，用于定义时间单位（如年、月、日等）
// 导入SQL操作符相关的类，用于SQL函数和操作符
import org.apache.calcite.sql.SqlOperatorTable;  // 导入SQL操作符表接口，包含所有可用的SQL函数和操作符
import org.apache.calcite.sql.fun.SqlLibrary;  // 导入SQL库枚举，定义不同的SQL方言库（如STANDARD、MSSQL、POSTGRESQL等）
import org.apache.calcite.sql.fun.SqlLibraryOperatorTableFactory;  // 导入SQL库操作符表工厂，用于创建特定库的操作符表
// 导入SQL解析器相关的类，用于解析SQL语句
import org.apache.calcite.sql.parser.SqlParserFixture;  // 导入SQL解析器测试夹具，用于测试SQL解析功能
import org.apache.calcite.sql.parser.babel.SqlBabelParserImpl;  // 导入Babel解析器实现类，支持多种SQL方言
import org.apache.calcite.sql.validate.SqlConformanceEnum;  // 导入SQL合规性枚举，定义SQL语法符合的标准

// 导入JUnit测试框架的注解
import org.junit.jupiter.api.Test;  // 导入JUnit 5的Test注解，用于标记测试方法

// 导入JDBC相关的类，用于数据库连接和操作
import java.sql.Connection;  // 导入数据库连接接口，代表与数据库的连接
import java.sql.DriverManager;  // 导入数据库驱动管理器，用于创建数据库连接
import java.sql.ResultSet;  // 导入结果集接口，代表SQL查询的结果
import java.sql.ResultSetMetaData;  // 导入结果集元数据接口，提供结果集的结构信息
import java.sql.SQLException;  // 导入SQL异常类，处理数据库操作中的错误
import java.sql.Statement;  // 导入语句接口，用于执行SQL语句
import java.sql.Types;  // 导入SQL类型常量，定义各种SQL数据类型的常量值
// 导入Java工具类
import java.util.Properties;  // 导入属性类，用于存储键值对形式的配置信息
import java.util.function.UnaryOperator;  // 导入一元操作符函数式接口，用于对单个参数进行操作

// 导入Hamcrest断言库，用于编写更灵活的断言
import static org.hamcrest.CoreMatchers.is;  // 导入is匹配器，用于比较值是否相等
import static org.hamcrest.MatcherAssert.assertThat;  // 导入断言工具类，用于执行断言操作

/**
 * Unit tests for Babel framework.
 * Babel框架的单元测试类
 * 
 * 这个测试类专门用于测试Calcite的Babel框架，Babel框架是Calcite中用于支持多种SQL方言的解析和验证框架
 * 主要测试内容包括：
 * 1. PostgreSQL风格的中缀类型转换语法（如 x::integer）
 * 2. POSIX正则表达式操作符（如 ~, ~*, !~, !~*）
 * 3. 不同SQL方言的解析器功能（如MySQL、PostgreSQL、MSSQL等）
 * 4. 自定义时间帧支持（DATEADD、DATEDIFF、DATEPART、DATE_PART函数）
 * 5. MySQL的空值安全比较操作符（<=>）
 * 6. Spark风格的LEFT SEMI JOIN和LEFT ANTI JOIN语法
 * 
 * 该类使用JUnit 5测试框架，通过CalciteAssert工具类来执行SQL语句并验证结果
 */
class BabelTest {  // 定义BabelTest测试类，用于测试Babel框架的各种功能

  // 定义静态常量URL，表示Calcite的JDBC连接URL
  // jdbc:calcite: 是Calcite的标准JDBC连接字符串，使用内存数据库模式
  static final String URL = "jdbc:calcite:";  // Calcite JDBC连接URL常量，指向内存数据库

  // 静态方法：创建一个配置操作符，用于设置SQL解析器工厂
  // 返回一个UnaryOperator函数，该函数接收PropBuilder并配置使用Babel解析器工厂
  // 这样做的目的是为了在创建连接时统一配置解析器，避免重复代码
  private static UnaryOperator<CalciteAssert.PropBuilder> useParserFactory() {  // 定义静态方法，返回配置解析器工厂的操作符
    return propBuilder ->  // 返回一个lambda表达式，接收PropBuilder参数
        propBuilder.set(CalciteConnectionProperty.PARSER_FACTORY,  // 设置PARSER_FACTORY属性，指定使用哪个解析器工厂
            SqlBabelParserImpl.class.getName() + "#FACTORY");  // 使用SqlBabelParserImpl类的FACTORY静态字段作为解析器工厂
  }  // 方法结束，返回配置好的属性构建器操作符

  // 静态方法：创建一个配置操作符，用于设置SQL函数库列表
  // 参数libraryList是一个逗号分隔的字符串，指定要使用的SQL函数库（如"standard,postgresql"）
  // 这样可以在同一个查询中使用多个SQL方言的函数
  private static UnaryOperator<CalciteAssert.PropBuilder> useLibraryList(  // 定义静态方法，返回配置函数库列表的操作符
      String libraryList) {  // 参数libraryList：要使用的函数库列表，如"standard,postgresql"
    return propBuilder ->  // 返回一个lambda表达式，接收PropBuilder参数
        propBuilder.set(CalciteConnectionProperty.FUN, libraryList);  // 设置FUN属性，指定函数库列表
  }  // 方法结束，返回配置好的属性构建器操作符

  // 静态方法：创建一个配置操作符，用于设置操作符查找的宽松模式
  // 参数lenient为true时表示启用宽松模式，false表示严格模式
  // 宽松模式下，如果找不到某个操作符，不会立即报错，而是尝试其他方式解析
  private static UnaryOperator<CalciteAssert.PropBuilder> useLenientOperatorLookup(  // 定义静态方法，返回配置操作符查找模式的操作符
      boolean lenient) {  // 参数lenient：是否启用宽松模式，true表示宽松，false表示严格
    return propBuilder ->  // 返回一个lambda表达式，接收PropBuilder参数
        propBuilder.set(CalciteConnectionProperty.LENIENT_OPERATOR_LOOKUP,  // 设置LENIENT_OPERATOR_LOOKUP属性
            Boolean.toString(lenient));  // 将布尔值转换为字符串设置
  }  // 方法结束，返回配置好的属性构建器操作符

  // 静态方法：为指定的SQL库创建操作符表
  // 参数library是SQL库枚举，指定要使用的SQL方言库（如MSSQL、POSTGRESQL等）
  // 返回包含STANDARD库和指定库的操作符表，这样可以同时使用标准SQL和特定方言的函数
  static SqlOperatorTable operatorTableFor(SqlLibrary library) {  // 定义静态方法，为指定库创建操作符表
    return SqlLibraryOperatorTableFactory.INSTANCE.getOperatorTable(  // 通过工厂实例获取操作符表
        SqlLibrary.STANDARD, library);  // 传入STANDARD库和指定库，返回合并后的操作符表
  }  // 方法结束，返回操作符表对象

  // 静态方法：创建一个使用默认配置的Calcite数据库连接
  // 使用默认的属性配置，调用重载的connect方法
  // 这是一个便捷方法，用于快速创建连接
  static Connection connect() throws SQLException {  // 定义静态方法，创建默认配置的连接，可能抛出SQL异常
    return connect(UnaryOperator.identity());  // 调用重载的connect方法，传入恒等函数（不做任何修改）
  }  // 方法结束，返回数据库连接对象

  // 静态方法：创建一个使用自定义配置的Calcite数据库连接
  // 参数propBuild是一个配置操作符，用于自定义连接属性
  // 该方法会先应用用户自定义配置，然后应用解析器工厂和宽松操作符查找配置
  static Connection connect(UnaryOperator<CalciteAssert.PropBuilder> propBuild)  // 定义静态方法，创建自定义配置的连接
      throws SQLException {  // 可能抛出SQL异常
    final CalciteAssert.PropBuilder propBuilder = CalciteAssert.propBuilder();  // 创建属性构建器对象
    final Properties info =  // 创建Properties对象存储连接属性
        propBuild.andThen(useParserFactory())  // 先应用用户自定义配置，然后应用解析器工厂配置
            .andThen(useLenientOperatorLookup(true))  // 再应用宽松操作符查找配置，设置为true
            .apply(propBuilder)  // 应用所有配置到构建器
            .build();  // 构建Properties对象
    return DriverManager.getConnection(URL, info);  // 使用驱动管理器创建数据库连接，传入URL和属性
  }  // 方法结束，返回数据库连接对象

  // 测试方法：测试PostgreSQL风格的中缀类型转换语法
  // 使用try-with-resources自动管理Connection和Statement资源
  // 测试将字符串转换为多种数据类型（integer、varchar、boolean、double、bigint）
  @Test void testInfixCast() throws SQLException {  // 定义测试方法，测试中缀类型转换，可能抛出SQL异常
    try (Connection connection = connect(useLibraryList("standard,postgresql"));  // 创建连接，使用standard和postgresql函数库
         Statement statement = connection.createStatement()) {  // 创建SQL语句执行对象
      checkInfixCast(statement, "integer", Types.INTEGER);  // 测试转换为integer类型，期望类型为INTEGER
      checkInfixCast(statement, "varchar", Types.VARCHAR);  // 测试转换为varchar类型，期望类型为VARCHAR
      checkInfixCast(statement, "boolean", Types.BOOLEAN);  // 测试转换为boolean类型，期望类型为BOOLEAN
      checkInfixCast(statement, "double", Types.DOUBLE);  // 测试转换为double类型，期望类型为DOUBLE
      checkInfixCast(statement, "bigint", Types.BIGINT);  // 测试转换为bigint类型，期望类型为BIGINT
    }  // try-with-resources自动关闭Connection和Statement
  }  // 测试方法结束

  // 私有辅助方法：检查中缀类型转换的结果类型是否正确
  // 参数statement是SQL语句执行对象
  // 参数typeName是目标类型名称（如"integer"）
  // 参数sqlType是预期的SQL类型常量（如Types.INTEGER）
  // 构造SQL查询，使用PostgreSQL的::操作符进行类型转换，然后验证结果类型
  private void checkInfixCast(Statement statement, String typeName, int sqlType)  // 定义私有方法，检查类型转换结果
      throws SQLException {  // 可能抛出SQL异常
    final String sql = "SELECT x::" + typeName + "\n"  // 构造SQL语句，使用::操作符将x列转换为指定类型
        + "FROM (VALUES ('1', '2')) as tbl(x, y)";  // 从值表中选择，创建一个包含两列x和y的临时表
    try (ResultSet resultSet = statement.executeQuery(sql)) {  // 执行SQL查询，获取结果集
      final ResultSetMetaData metaData = resultSet.getMetaData();  // 获取结果集的元数据信息
      assertThat("Invalid column count", metaData.getColumnCount(), is(1));  // 断言列数为1
      assertThat("Invalid column type", metaData.getColumnType(1),  // 断言第一列的类型
          is(sqlType));  // 与预期的sqlType比较
    }  // try-with-resources自动关闭ResultSet
  }  // 方法结束

  // 测试方法：测试POSIX正则表达式操作符
  // 使用Fixtures工具创建SQL验证器测试夹具
  // 测试各种正则表达式操作符：~（匹配）、~*（不区分大小写匹配）、!~（不匹配）、!~*（不区分大小写不匹配）
  // 测试包含NULL值的各种情况，确保正则表达式操作符能正确处理NULL
  @Test void testPosixRegex() {  // 定义测试方法，测试POSIX正则表达式操作符
    final SqlValidatorFixture f = Fixtures.forValidator()  // 创建SQL验证器测试夹具
        .withParserConfig(p -> p.withParserFactory(SqlBabelParserImpl.FACTORY));  // 配置使用Babel解析器工厂
    f.withSql("select null !~ 'ab[cd]'").ok();  // 测试NULL不匹配正则表达式，应该成功
    f.withSql("select 'abcd' !~ null").ok();  // 测试字符串不匹配NULL，应该成功
    f.withSql("select null !~ null").ok();  // 测试NULL不匹配NULL，应该成功
    f.withSql("select null !~* 'ab[cd]'").ok();  // 测试NULL不区分大小写不匹配正则表达式，应该成功
    f.withSql("select 'abcd' !~* null").ok();  // 测试字符串不区分大小写不匹配NULL，应该成功
    f.withSql("select null !~* null").ok();  // 测试NULL不区分大小写不匹配NULL，应该成功
    f.withSql("select null ~* null").ok();  // 测试NULL不区分大小写匹配NULL，应该成功
    f.withSql("select 'abcd' ~* null").ok();  // 测试字符串不区分大小写匹配NULL，应该成功
    f.withSql("select null ~* 'ab[cd]'").ok();  // 测试NULL不区分大小写匹配正则表达式，应该成功
    f.withSql("select null ~ null").ok();  // 测试NULL匹配NULL，应该成功
    f.withSql("select 'abcd' ~ null").ok();  // 测试字符串匹配NULL，应该成功
    f.withSql("select null ~ 'ab[cd]'").ok();  // 测试NULL匹配正则表达式，应该成功
    f.withSql("select 'abcd' !~* 'ab[CD]'").ok();  // 测试字符串不区分大小写不匹配正则表达式，应该成功
  }  // 测试方法结束

  /** Tests that you can run tests via {@link Fixtures}. */
  // 测试方法：测试通过Fixtures工具运行测试
  // 测试内容包括：
  // 1. 类型不匹配的错误检测（如整数加日期）
  // 2. 正确的类型推断
  // 3. 关键字作为标识符的处理（as关键字）
  // 4. PostgreSQL类型转换语法的解析
  @Test void testFixtures() {  // 定义测试方法，测试Fixtures工具的使用
    final SqlValidatorFixture v = Fixtures.forValidator();  // 创建SQL验证器测试夹具
    v.withSql("select ^1 + date '2002-03-04'^")  // 测试类型不匹配的错误，^标记错误位置
        .fails("(?s).*Cannot apply '\\+' to arguments of"  // 预期失败，错误消息包含不能将+应用于整数和日期
            + " type '<INTEGER> \\+ <DATE>'.*");  // 正则表达式匹配错误消息

    v.withSql("select 1 + 2 as three")  // 测试正确的类型推断
        .type("RecordType(INTEGER NOT NULL THREE) NOT NULL");  // 预期返回类型为包含THREE列的记录类型

    // 'as' as identifier is invalid with Core parser
    // 注释说明：as作为标识符在Core解析器中是无效的
    final SqlParserFixture p = Fixtures.forParser();  // 创建SQL解析器测试夹具
    p.sql("select ^as^ from t")  // 测试as作为标识符，^标记错误位置
        .fails("(?s)Encountered \"as\".*");  // 预期失败，错误消息包含遇到as关键字

    // 'as' as identifier is invalid if you use Babel's tester and Core parser
    // 注释说明：即使使用Babel的测试器和Core解析器，as作为标识符仍然无效
    p.sql("select ^as^ from t")  // 测试as作为标识符
        .withTester(new BabelParserTest.BabelTesterImpl())  // 使用Babel测试器实现
        .fails("(?s)Encountered \"as\".*");  // 预期失败，错误消息包含遇到as关键字

    // 'as' as identifier is valid with Babel parser
    // 注释说明：as作为标识符在Babel解析器中是有效的
    p.withConfig(c -> c.withParserFactory(SqlBabelParserImpl.FACTORY))  // 配置使用Babel解析器工厂
        .sql("select as from t")  // 测试as作为标识符
        .ok("SELECT `AS`\n"  // 预期成功，SQL被转换为带反引号的AS
            + "FROM `T`");  // 表名也被转换为带反引号的形式

    // Postgres cast is invalid with core parser
    // 注释说明：PostgreSQL类型转换语法在Core解析器中是无效的
    p.sql("select 1 ^:^: integer as x")  // 测试PostgreSQL的::类型转换语法，^标记错误位置
        .fails("(?s).*Encountered \":\" at .*");  // 预期失败，错误消息包含遇到冒号
  }  // 测试方法结束

  /** Tests that DATEADD, DATEDIFF, DATEPART, DATE_PART allow custom time
   * frames. */
  // 测试方法：测试DATEADD、DATEDIFF、DATEPART、DATE_PART函数支持自定义时间帧
  // 测试内容包括：
  // 1. 自定义时间帧的定义（如minute15表示15分钟）
  // 2. 无效时间帧的错误检测
  // 3. MSSQL和PostgreSQL方言的时间函数差异
  // 4. 时间帧在DATEADD、DATEDIFF、DATEPART、DATE_PART函数中的应用
  @Test void testTimeFrames() {  // 定义测试方法，测试自定义时间帧功能
    final SqlValidatorFixture f = Fixtures.forValidator()  // 创建SQL验证器测试夹具
        .withParserConfig(p -> p.withParserFactory(SqlBabelParserImpl.FACTORY))  // 配置使用Babel解析器工厂
        .withOperatorTable(operatorTableFor(SqlLibrary.MSSQL))  // 配置使用MSSQL操作符表
        .withFactory(tf ->  // 配置类型工厂
            tf.withTypeSystem(typeSystem ->  // 配置类型系统
                new DelegatingTypeSystem(typeSystem) {  // 创建委托类型系统
                  @Override public TimeFrameSet deriveTimeFrameSet(  // 重写deriveTimeFrameSet方法
                      TimeFrameSet frameSet) {  // 参数frameSet是原始时间帧集合
                    return TimeFrameSet.builder()  // 创建时间帧集合构建器
                        .addAll(frameSet)  // 添加所有原始时间帧
                        .addDivision("minute15", 4, "HOUR")  // 添加自定义时间帧：minute15，将小时分成4份，每份15分钟
                        .build();  // 构建时间帧集合
                  }  // 方法结束
                }));  // 类型系统配置结束

    final String ts = "timestamp '2020-06-27 12:34:56'";  // 定义时间戳常量1
    final String ts2 = "timestamp '2020-06-27 13:45:56'";  // 定义时间戳常量2
    f.withSql("SELECT DATEADD(YEAR, 3, " + ts + ")").ok();  // 测试DATEADD函数，使用标准时间帧YEAR，应该成功
    f.withSql("SELECT DATEADD(HOUR^.^A, 3, " + ts + ")")  // 测试无效的时间帧语法，^标记错误位置
        .fails("(?s).*Encountered \".\" at .*");  // 预期失败，错误消息包含遇到点号
    f.withSql("SELECT DATEADD(^A^, 3, " + ts + ")")  // 测试无效的时间帧名称A，^标记错误位置
        .fails("'A' is not a valid time frame");  // 预期失败，错误消息说明A不是有效的时间帧
    f.withSql("SELECT DATEADD(minute15, 3, " + ts + ")")  // 测试DATEADD函数使用自定义时间帧minute15
        .ok();  // 应该成功
    f.withSql("SELECT DATEDIFF(^A^, " + ts + ", " + ts2 + ")")  // 测试DATEDIFF函数使用无效时间帧A
        .fails("'A' is not a valid time frame");  // 预期失败，错误消息说明A不是有效的时间帧
    f.withSql("SELECT DATEDIFF(minute15, " + ts + ", " + ts2 + ")")  // 测试DATEDIFF函数使用自定义时间帧minute15
        .ok();  // 应该成功
    f.withSql("SELECT DATEPART(^A^, " + ts + ")")  // 测试DATEPART函数使用无效时间帧A
        .fails("'A' is not a valid time frame");  // 预期失败，错误消息说明A不是有效的时间帧
    f.withSql("SELECT DATEPART(minute15, " + ts + ")")  // 测试DATEPART函数使用自定义时间帧minute15
        .ok();  // 应该成功

    // Where DATEPART is MSSQL, DATE_PART is Postgres
    // 注释说明：DATEPART是MSSQL的函数名，DATE_PART是PostgreSQL的函数名
    f.withSql("SELECT ^DATE_PART(A, " + ts + ")^")  // 测试在MSSQL操作符表中使用PostgreSQL的DATE_PART函数
        .fails("No match found for function signature "  // 预期失败，错误消息说明找不到匹配的函数签名
            + "DATE_PART\\(<INTERVAL_DAY_TIME>, <TIMESTAMP>\\)");  // 错误消息包含预期的函数签名
    final SqlValidatorFixture f2 =  // 创建第二个验证器夹具
        f.withOperatorTable(operatorTableFor(SqlLibrary.POSTGRESQL));  // 配置使用PostgreSQL操作符表
    f2.withSql("SELECT ^DATEPART(A, " + ts + ")^")  // 测试在PostgreSQL操作符表中使用MSSQL的DATEPART函数
        .fails("No match found for function signature "  // 预期失败，错误消息说明找不到匹配的函数签名
            + "DATEPART\\(<INTERVAL_DAY_TIME>, <TIMESTAMP>\\)");  // 错误消息包含预期的函数签名
    f2.withSql("SELECT DATE_PART(^A^, " + ts + ")")  // 测试DATE_PART函数使用无效时间帧A
        .fails("'A' is not a valid time frame");  // 预期失败，错误消息说明A不是有效的时间帧
    f2.withSql("SELECT DATE_PART(minute15, " + ts + ")")  // 测试DATE_PART函数使用自定义时间帧minute15
        .ok();  // 应该成功
  }  // 测试方法结束

  // 测试方法：测试MySQL的空值安全比较操作符<=>
  // <=>操作符的特点是：如果两个操作数都为NULL，则返回true；如果只有一个为NULL，则返回false
  // 这与普通的=操作符不同，=操作符在遇到NULL时返回NULL而不是true或false
  @Test void testNullSafeEqual() {  // 定义测试方法，测试空值安全比较操作符
    // x <=> y
    // 注释说明：测试基本的空值安全比较
    checkSqlResult("mysql", "SELECT 1 <=> NULL", "EXPR$0=false\n");  // 测试1 <=> NULL，应该返回false
    checkSqlResult("mysql", "SELECT NULL <=> NULL", "EXPR$0=true\n");  // 测试NULL <=> NULL，应该返回true
    // (a, b) <=> (x, y)
    // 注释说明：测试元组的空值安全比较
    checkSqlResult("mysql",  // 测试元组比较，一个元组有NULL，另一个元组有NULL但位置不同
        "SELECT (CAST(NULL AS Integer), 1) <=> (1, CAST(NULL AS Integer))",  // 元组1有NULL在第一个位置，元组2有NULL在第二个位置
        "EXPR$0=false\n");  // 应该返回false，因为NULL的位置不同
    checkSqlResult("mysql",  // 测试两个元组都有NULL且位置相同
        "SELECT (CAST(NULL AS Integer), CAST(NULL AS Integer))\n"  // 元组1的两个元素都是NULL
            + "<=> (CAST(NULL AS Integer), CAST(NULL AS Integer))",  // 元组2的两个元素也都是NULL
        "EXPR$0=true\n");  // 应该返回true，因为两个元组完全相同
    // the higher precedence
    // 注释说明：测试<= >操作符的较高优先级
    checkSqlResult("mysql",  // 测试<=>的优先级高于+
        "SELECT x <=> 1 + 3 FROM (VALUES (1, 2)) as tbl(x,y)",  // 查询中x <=> (1 + 3)，因为<=>优先级高于+
        "EXPR$0=false\n");  // x=1，1+3=4，1<=>4返回false
    // the lower precedence
    // 注释说明：测试<=>操作符的较低优先级
    checkSqlResult("mysql",  // 测试<=>的优先级低于NOT
        "SELECT NOT x <=> 1 FROM (VALUES (1, 2)) as tbl(x,y)",  // 查询中NOT (x <=> 1)，因为NOT优先级高于<=>（实际上是NOT结合更紧密）
        "EXPR$0=false\n");  // x=1，1<=>1返回true，NOT true返回false
  }  // 测试方法结束

  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6030">
   * [CALCITE-6030] DATE_PART is not handled by the RexToLixTranslator</a>. */
  // 测试方法：测试CALCITE-6030问题，DATE_PART函数在RexToLixTranslator中未被处理
  // 这个测试确保DATE_PART函数能够正确地从时间中提取秒数
  @Test void testDatePart() {  // 定义测试方法，测试DATE_PART函数
    checkSqlResult("postgresql", "SELECT DATE_PART(second, TIME '10:10:10')",  // 测试从时间中提取秒数
        "EXPR$0=10\n");  // 预期返回10，因为时间是10:10:10
  }  // 测试方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5816">[CALCITE-5816]
   * Query with LEFT SEMI JOIN should not refer to RHS columns</a>. */
  // 测试方法：测试CALCITE-5816问题，LEFT SEMI JOIN查询不应该引用右侧表的列
  // LEFT SEMI JOIN的特点是：只返回左表中与右表匹配的行，且结果中不包含右表的列
  @Test public void testLeftSemiJoin() {  // 定义测试方法，测试LEFT SEMI JOIN
    final SqlValidatorFixture v = Fixtures.forValidator()  // 创建SQL验证器测试夹具
        .withParserConfig(c -> c.withParserFactory(SqlBabelParserImpl.FACTORY))  // 配置使用Babel解析器工厂
        .withConformance(SqlConformanceEnum.BABEL);  // 配置使用Babel合规性标准

    v.withSql("SELECT * FROM dept LEFT SEMI JOIN emp ON emp.deptno = dept.deptno")  // 测试LEFT SEMI JOIN，选择所有列
        .type("RecordType(INTEGER NOT NULL DEPTNO, VARCHAR(10) NOT NULL NAME) NOT NULL");  // 预期返回类型只包含dept表的列，不包含emp表的列

    v.withSql("SELECT deptno FROM dept LEFT SEMI JOIN emp ON emp.deptno = dept.deptno")  // 测试LEFT SEMI JOIN，只选择deptno列
        .type("RecordType(INTEGER NOT NULL DEPTNO) NOT NULL");  // 预期返回类型只包含deptno列
  }  // 测试方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5962">[CALCITE-5962]
   * Support parse Spark-style syntax "LEFT ANTI JOIN" in Babel parser</a>. */
  // 测试方法：测试CALCITE-5962问题，在Babel解析器中支持Spark风格的LEFT ANTI JOIN语法
  // LEFT ANTI JOIN的特点是：只返回左表中与右表不匹配的行，且结果中不包含右表的列
  @Test public void testLeftAntiJoin() {  // 定义测试方法，测试LEFT ANTI JOIN
    final SqlValidatorFixture v = Fixtures.forValidator()  // 创建SQL验证器测试夹具
        .withParserConfig(c -> c.withParserFactory(SqlBabelParserImpl.FACTORY))  // 配置使用Babel解析器工厂
        .withConformance(SqlConformanceEnum.BABEL);  // 配置使用Babel合规性标准

    v.withSql("SELECT * FROM dept LEFT ANTI JOIN emp ON emp.deptno = dept.deptno")  // 测试LEFT ANTI JOIN，选择所有列
        .type("RecordType(INTEGER NOT NULL DEPTNO, VARCHAR(10) NOT NULL NAME) NOT NULL");  // 预期返回类型只包含dept表的列，不包含emp表的列

    v.withSql("SELECT name FROM dept LEFT ANTI JOIN emp ON emp.deptno = dept.deptno")  // 测试LEFT ANTI JOIN，只选择name列
        .type("RecordType(VARCHAR(10) NOT NULL NAME) NOT NULL");  // 预期返回类型只包含name列
  }  // 测试方法结束

  // 私有辅助方法：检查SQL查询的执行结果
  // 参数funLibrary是要使用的函数库（如"mysql"、"postgresql"）
  // 参数query是要执行的SQL查询语句
  // 参数result是预期的查询结果
  // 使用CalciteAssert工具执行查询并验证结果
  private void checkSqlResult(String funLibrary, String query, String result) {  // 定义私有方法，检查SQL查询结果
    CalciteAssert.that()  // 创建CalciteAssert断言对象
        .with(CalciteConnectionProperty.PARSER_FACTORY,  // 配置解析器工厂属性
            SqlBabelParserImpl.class.getName() + "#FACTORY")  // 使用Babel解析器工厂
        .with(CalciteConnectionProperty.FUN, funLibrary)  // 配置函数库属性
        .query(query)  // 执行SQL查询
        .returns(result);  // 验证返回结果
  }  // 方法结束
}  // 类结束
