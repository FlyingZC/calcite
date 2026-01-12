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
package org.apache.calcite.sql.test; // 声明包名，属于org.apache.calcite.sql.test包，这是Calcite SQL测试工具包

import org.apache.calcite.avatica.ColumnMetaData; // 导入Avatica的列元数据类，用于描述结果集列的元数据信息
import org.apache.calcite.util.ImmutableNullableSet; // 导入不可变可空集合工具类，用于创建包含null元素的不可变集合
import org.apache.calcite.util.JdbcType; // 导入JDBC类型工具类，用于处理JDBC类型转换和获取

import com.google.common.collect.ImmutableSet; // 导入Google Guava的不可变集合类，用于创建不可变集合

import org.hamcrest.Matcher; // 导入Hamcrest匹配器接口，用于灵活的断言匹配

import java.math.BigDecimal; // 导入BigDecimal类，用于精确的十进制数值计算和比较
import java.sql.ResultSet; // 导入JDBC结果集接口，用于表示SQL查询返回的数据集
import java.sql.Types; // 导入JDBC类型常量类，定义了各种SQL类型的常量值
import java.time.LocalDateTime; // 导入Java 8的本地日期时间类，用于表示不带时区的日期时间
import java.time.LocalTime; // 导入Java 8的本地时间类，用于表示不带时区的时间
import java.time.ZoneOffset; // 导入时区偏移类，用于处理时区转换
import java.util.Collection; // 导入集合接口，表示一组对象的集合
import java.util.Collections; // 导入集合工具类，提供集合操作的静态方法
import java.util.HashSet; // 导入HashSet类，基于哈希表的Set实现
import java.util.Set; // 导入Set接口，表示不包含重复元素的集合
import java.util.regex.Pattern; // 导入正则表达式模式类，用于字符串模式匹配

import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest的equalTo匹配器，用于相等性断言
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器，用于包装其他匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言方法，用于执行匹配断言
import static org.hamcrest.Matchers.closeTo; // 导入Hamcrest的closeTo匹配器，用于浮点数近似值断言
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit的fail方法，用于使测试失败

import static java.lang.Double.parseDouble; // 导入Double的静态parseDouble方法，用于解析字符串为double
import static java.lang.Long.parseLong; // 导入Long的静态parseLong方法，用于解析字符串为long
import static java.util.Objects.requireNonNull; // 导入Objects的静态requireNonNull方法，用于参数非空检查

/** Utilities for {@link SqlTester.ResultChecker}. */ // 类文档注释：提供SqlTester.ResultChecker的工具类，用于创建各种结果检查器
public class ResultCheckers { // 声明ResultCheckers工具类，提供创建ResultChecker的各种静态工厂方法
  private ResultCheckers() { // 私有构造方法，防止实例化，因为这是一个纯工具类
  } // 私有构造方法结束，确保该类只能通过静态方法使用

  public static SqlTester.ResultChecker isExactly(double value) { // 静态工厂方法：创建一个检查器，验证结果是否精确等于指定的double值
    return new MatcherResultChecker<>(is(value), // 创建MatcherResultChecker，使用is匹配器精确匹配double值
        JdbcType.DOUBLE); // 指定JDBC类型为DOUBLE，用于从结果集中正确获取值
  } // 方法结束，返回一个ResultChecker实例

  public static SqlTester.ResultChecker isExactly(String value) { // 静态工厂方法：创建一个检查器，验证结果是否精确等于指定的字符串表示的BigDecimal值
    return new MatcherResultChecker<>(is(new BigDecimal(value)), // 创建MatcherResultChecker，将字符串转换为BigDecimal后精确匹配
        JdbcType.BIG_DECIMAL); // 指定JDBC类型为BIG_DECIMAL，用于精确的十进制数值比较
  } // 方法结束，返回一个ResultChecker实例

  public static SqlTester.ResultChecker isExactDateTime(LocalDateTime dateTime) { // 静态工厂方法：创建一个检查器，验证结果是否精确等于指定的日期时间
    return new MatcherResultChecker<>( // 创建MatcherResultChecker
        is(BigDecimal.valueOf(dateTime.toInstant(ZoneOffset.UTC).toEpochMilli())), // 将LocalDateTime转换为UTC时间戳的毫秒数，再转为BigDecimal进行精确匹配
        JdbcType.BIG_DECIMAL); // 指定JDBC类型为BIG_DECIMAL，因为时间戳作为数值存储
  } // 方法结束，返回一个ResultChecker实例

  public static SqlTester.ResultChecker isExactTime(LocalTime time) { // 静态工厂方法：创建一个检查器，验证结果是否精确等于指定的本地时间
    return new MatcherResultChecker<>( // 创建MatcherResultChecker
        is((int) (time.toNanoOfDay() / 1000_000)), // 将时间转换为纳秒数，再转换为毫秒数（整数）进行精确匹配
        JdbcType.INTEGER); // 指定JDBC类型为INTEGER，因为时间毫秒数作为整数存储
  } // 方法结束，返回一个ResultChecker实例

  public static SqlTester.ResultChecker isWithin(double value, double delta) { // 静态工厂方法：创建一个检查器，验证结果是否在指定值的误差范围内
    return new MatcherResultChecker<>(closeTo(value, delta), JdbcType.DOUBLE); // 创建MatcherResultChecker，使用closeTo匹配器进行近似值匹配
  } // 方法结束，返回一个ResultChecker实例

  public static SqlTester.ResultChecker isSingle(double delta, String value) { // 静态工厂方法：创建一个检查器，验证单个字符串结果（delta参数已废弃）
    assert delta == 0d; // 断言delta必须为0，如果不为0应该调用其他方法
    return isSingle(value); // 调用isSingle(String)方法处理字符串值
  } // 方法结束，返回一个ResultChecker实例

  public static SqlTester.ResultChecker isSingle(String value) { // 静态工厂方法：创建一个检查器，验证结果是否精确等于指定的字符串值
    return new MatcherResultChecker<>(is(value), // 创建MatcherResultChecker，使用is匹配器精确匹配字符串
        JdbcType.STRING_NULLABLE); // 指定JDBC类型为STRING_NULLABLE，允许字符串为null
  } // 方法结束，返回一个ResultChecker实例

  public static SqlTester.ResultChecker isSingle(boolean value) { // 静态工厂方法：创建一个检查器，验证结果是否精确等于指定的布尔值
    return new MatcherResultChecker<>(is(value), // 创建MatcherResultChecker，使用is匹配器精确匹配布尔值
        JdbcType.BOOLEAN); // 指定JDBC类型为BOOLEAN，用于从结果集中获取布尔值
  } // 方法结束，返回一个ResultChecker实例

  public static SqlTester.ResultChecker isSingle(int value) { // 静态工厂方法：创建一个检查器，验证结果是否精确等于指定的整数值
    return new MatcherResultChecker<>(is(value), // 创建MatcherResultChecker，使用is匹配器精确匹配整数值
        JdbcType.INTEGER); // 指定JDBC类型为INTEGER，用于从结果集中获取整数值
  } // 方法结束，返回一个ResultChecker实例

  public static SqlTester.ResultChecker isDecimal(String value) { // 静态工厂方法：创建一个检查器，验证结果是否精确等于指定的BigDecimal值
    return new MatcherResultChecker<>(is(new BigDecimal(value)), // 创建MatcherResultChecker，将字符串转换为BigDecimal后精确匹配
        JdbcType.BIG_DECIMAL); // 指定JDBC类型为BIG_DECIMAL，用于精确的十进制数值比较
  } // 方法结束，返回一个ResultChecker实例

  public static SqlTester.ResultChecker isSet(String... values) { // 静态工厂方法：创建一个检查器，验证结果集是否包含指定的值集合（不考虑顺序）
    return new RefSetResultChecker(ImmutableSet.copyOf(values)); // 创建RefSetResultChecker，将可变参数转换为不可变集合用于比较
  } // 方法结束，返回一个ResultChecker实例

  public static SqlTester.ResultChecker isNullValue() { // 静态工厂方法：创建一个检查器，验证结果是否为null值
    return new RefSetResultChecker(Collections.singleton(null)); // 创建RefSetResultChecker，使用只包含null的单元素集合进行比较
  } // 方法结束，返回一个ResultChecker实例

  /**
   * Compares the first column of a result set against a String-valued
   * reference set, disregarding order entirely.
   * 比较结果集的第一列与字符串值参考集合，完全忽略顺序。
   *
   * @param sql       SQL to show in case of failure - SQL语句，用于在失败时显示错误信息
   * @param resultSet Result set - JDBC结果集，包含查询返回的数据
   * @param refSet    Expected results - 期望的结果集合，包含所有预期的字符串值
   * @throws Exception - 可能抛出的异常
   */
  static void compareResultSet(String sql, ResultSet resultSet, // 静态方法：比较结果集第一列与参考集合
      Set<String> refSet) throws Exception { // 参数：SQL语句、结果集、参考集合，可能抛出异常
    Set<String> actualSet = new HashSet<>(); // 创建HashSet用于存储实际结果集中的值
    final int columnType = resultSet.getMetaData().getColumnType(1); // 获取结果集第一列的JDBC类型
    final ColumnMetaData.Rep rep = rep(columnType); // 根据JDBC类型获取对应的列表示形式
    final String msg = "Query: " + sql; // 构造错误消息前缀，包含SQL语句便于调试
    while (resultSet.next()) { // 遍历结果集的每一行
      final String s = resultSet.getString(1); // 获取第一列的字符串值
      final String s0 = s == null ? "0" : s; // 如果值为null则用"0"代替，否则使用原值
      final boolean wasNull0 = resultSet.wasNull(); // 检查刚才读取的值是否为null
      actualSet.add(s); // 将字符串值添加到实际结果集合中
      switch (rep) { // 根据列的数据类型进行类型特定的验证
      case BOOLEAN: // 布尔类型
      case PRIMITIVE_BOOLEAN: // 原始布尔类型
        assertThat(msg, resultSet.getBoolean(1), equalTo(Boolean.valueOf(s))); // 验证布尔值是否匹配
        break; // 跳出switch
      case BYTE: // 字节类型
      case PRIMITIVE_BYTE: // 原始字节类型
      case SHORT: // 短整型
      case PRIMITIVE_SHORT: // 原始短整型
      case INTEGER: // 整型
      case PRIMITIVE_INT: // 原始整型
      case LONG: // 长整型
      case PRIMITIVE_LONG: // 原始长整型
        long l; // 声明长整型变量
        try { // 尝试解析长整型
          l = parseLong(s0); // 将字符串解析为长整型
        } catch (NumberFormatException e) { // 捕获数字格式异常
          // Large integers come out in scientific format, say "5E+06" // 大整数以科学计数法格式输出，如"5E+06"
          l = (long) parseDouble(s0); // 改用double解析，然后转换为long
        } // 结束try-catch块
        assertThat(msg, resultSet.getByte(1), equalTo((byte) l)); // 验证字节值是否匹配
        assertThat(msg, resultSet.getShort(1), equalTo((short) l)); // 验证短整型值是否匹配
        assertThat(msg, resultSet.getInt(1), equalTo((int) l)); // 验证整型值是否匹配
        assertThat(msg, resultSet.getLong(1), equalTo(l)); // 验证长整型值是否匹配
        break; // 跳出switch
      case FLOAT: // 浮点类型
      case PRIMITIVE_FLOAT: // 原始浮点类型
      case DOUBLE: // 双精度类型
      case PRIMITIVE_DOUBLE: // 原始双精度类型
        final double d = parseDouble(s0); // 将字符串解析为double类型
        assertThat(msg, resultSet.getFloat(1), equalTo((float) d)); // 验证浮点值是否匹配
        assertThat(msg, resultSet.getDouble(1), equalTo(d)); // 验证双精度值是否匹配
        break; // 跳出switch
      default: // 默认情况
        // fall through; no type-specific validation is necessary // 直接通过，不需要特定类型的验证
      } // 结束switch语句
      final boolean wasNull1 = resultSet.wasNull(); // 再次检查是否为null（用于验证一致性）
      final Object object = resultSet.getObject(1); // 获取第一列的对象值
      final boolean wasNull2 = resultSet.wasNull(); // 再次检查是否为null（用于验证一致性）
      assertThat(msg, object == null, equalTo(wasNull0)); // 验证对象是否为null与初始检查一致
      assertThat(msg, wasNull1, equalTo(wasNull0)); // 验证第一次wasNull调用结果与初始检查一致
      assertThat(msg, wasNull2, equalTo(wasNull0)); // 验证第二次wasNull调用结果与初始检查一致
    } // 结束while循环
    resultSet.close(); // 关闭结果集，释放资源
    assertThat(msg, actualSet, is(refSet)); // 验证实际结果集合与参考集合完全匹配
  } // 方法结束

  private static ColumnMetaData.Rep rep(int columnType) { // 私有静态方法：将JDBC类型转换为对应的列表示形式
    switch (columnType) { // 根据JDBC类型进行switch判断
    case Types.BOOLEAN: // 如果是布尔类型
      return ColumnMetaData.Rep.BOOLEAN; // 返回布尔类型的表示形式
    case Types.TINYINT: // 如果是tinyint类型
      return ColumnMetaData.Rep.BYTE; // 返回字节类型的表示形式
    case Types.SMALLINT: // 如果是smallint类型
      return ColumnMetaData.Rep.SHORT; // 返回短整型表示形式
    case Types.INTEGER: // 如果是int类型
      return ColumnMetaData.Rep.INTEGER; // 返回整型表示形式
    case Types.BIGINT: // 如果是bigint类型
      return ColumnMetaData.Rep.LONG; // 返回长整型表示形式
    case Types.REAL: // 如果是real类型
      return ColumnMetaData.Rep.FLOAT; // 返回浮点类型表示形式
    case Types.DOUBLE: // 如果是double类型
      return ColumnMetaData.Rep.DOUBLE; // 返回双精度类型表示形式
    case Types.TIME: // 如果是time类型
      return ColumnMetaData.Rep.JAVA_SQL_TIME; // 返回SQL时间类型表示形式
    case Types.TIMESTAMP: // 如果是timestamp类型
      return ColumnMetaData.Rep.JAVA_SQL_TIMESTAMP; // 返回SQL时间戳类型表示形式
    case Types.DATE: // 如果是date类型
      return ColumnMetaData.Rep.JAVA_SQL_DATE; // 返回SQL日期类型表示形式
    default: // 默认情况
      return ColumnMetaData.Rep.OBJECT; // 返回对象类型表示形式
    } // 结束switch语句
  } // 方法结束

  /**
   * Compares the first column of a result set against a pattern. The result
   * set must return exactly one row.
   * 比较结果集的第一列是否匹配指定的正则表达式模式。结果集必须只返回一行。
   *
   * @param sql       SQL to show in case of failure - SQL语句，用于在失败时显示错误信息
   * @param resultSet Result set - JDBC结果集，包含查询返回的数据
   * @param pattern   Expected pattern - 期望的正则表达式模式
   */
  static void compareResultSetWithPattern(String sql, ResultSet resultSet, // 静态方法：使用正则表达式模式比较结果集
      Pattern pattern) throws Exception { // 参数：SQL语句、结果集、正则表达式模式，可能抛出异常
    if (!resultSet.next()) { // 检查结果集是否有下一行
      fail("Query \"" + sql + "\"returned 0 rows, expected 1"); // 如果没有行，测试失败，期望1行但返回0行
    } // 结束if语句
    String actual = resultSet.getString(1); // 获取第一列的实际字符串值
    if (resultSet.next()) { // 检查结果集是否还有更多行
      fail("Query \"" + sql + "\"returned 2 or more rows, expected 1"); // 如果还有行，测试失败，期望1行但返回2行或更多
    } // 结束if语句
    if (!pattern.matcher(actual).matches()) { // 检查实际值是否匹配正则表达式模式
      fail("Query \"" + sql + "\"returned '" // 如果不匹配，测试失败并显示详细信息
              + actual // 显示实际返回的值
              + "', expected '" // 显示期望值的前缀
              + pattern.pattern() // 显示期望的正则表达式模式
              + "'"); // 结束错误消息
    } // 结束if语句
  } // 方法结束

  /**
   * Compares the first column of a result set against a {@link Matcher}.
   * The result set must return exactly one row.
   * 使用Hamcrest匹配器比较结果集的第一列。结果集必须只返回一行。
   *
   * @param sql       SQL to show in case of failure - SQL语句，用于在失败时显示错误信息
   * @param resultSet Result set - JDBC结果集，包含查询返回的数据
   * @param matcher   Matcher - Hamcrest匹配器，用于验证值是否符合预期
   *
   * @param <T> Value type - 泛型类型参数，表示值的类型
   */
  static <T> void compareResultSetWithMatcher(String sql, ResultSet resultSet, // 静态泛型方法：使用匹配器比较结果集
      JdbcType<T> jdbcType, Matcher<T> matcher) throws Exception { // 参数：SQL语句、结果集、JDBC类型、匹配器，可能抛出异常
    if (!resultSet.next()) { // 检查结果集是否有下一行
      fail("Query returned 0 rows, expected 1"); // 如果没有行，测试失败，期望1行但返回0行
    } // 结束if语句
    T actual = jdbcType.get(1, resultSet); // 使用JdbcType工具从结果集第一列获取指定类型的值
    if (resultSet.next()) { // 检查结果集是否还有更多行
      fail("Query returned 2 or more rows, expected 1"); // 如果还有行，测试失败，期望1行但返回2行或更多
    } // 结束if语句
    assertThat("Query: " + sql, actual, matcher); // 使用Hamcrest断言验证实际值是否匹配期望的匹配器
  } // 方法结束

  /** Creates a ResultChecker that accesses a column of a given type
   * and then uses a Hamcrest matcher to check the value.
   * 创建一个ResultChecker，访问指定类型的列，然后使用Hamcrest匹配器检查值。 */
  public static <T> SqlTester.ResultChecker createChecker(Matcher<T> matcher, // 静态泛型工厂方法：创建基于匹配器的ResultChecker
      JdbcType<T> jdbcType) { // 参数：Hamcrest匹配器、JDBC类型
    return new MatcherResultChecker<>(matcher, jdbcType); // 创建并返回MatcherResultChecker实例
  } // 方法结束

  /** Creates a ResultChecker from an expected result.
   *
   * <p>The result may be a {@link SqlTester.ResultChecker},
   * a regular expression ({@link Pattern}),
   * a Hamcrest {@link Matcher},
   * a {@link Collection} of strings (representing the values of one column).
   * 从期望结果创建ResultChecker。
   * 结果可以是SqlTester.ResultChecker、正则表达式(Pattern)、Hamcrest匹配器、字符串集合(表示一列的值)。
   *
   * <p>If none of the above, the value is converted to a string and compared
   * with the value of a single column, single row result set that is converted
   * to a string.
   * 如果以上都不是，则将值转换为字符串，与单列单行结果集转换后的字符串进行比较。
   */
  public static SqlTester.ResultChecker createChecker(Object result) { // 静态工厂方法：根据期望结果类型创建合适的ResultChecker
    requireNonNull(result, "to check for a null result, use isNullValue()"); // 检查结果不为null，如果为null提示使用isNullValue()方法
    if (result instanceof Pattern) { // 如果结果是正则表达式模式
      return new PatternResultChecker((Pattern) result); // 创建PatternResultChecker
    } else if (result instanceof SqlTester.ResultChecker) { // 如果结果已经是ResultChecker
      return (SqlTester.ResultChecker) result; // 直接返回该ResultChecker
    } else if (result instanceof Matcher) { // 如果结果是Hamcrest匹配器
      //noinspection unchecked,rawtypes // 忽略未检查的转换警告
      return createChecker((Matcher) result, JdbcType.DOUBLE); // 创建基于匹配器的ResultChecker，默认使用DOUBLE类型
    } else if (result instanceof Collection) { // 如果结果是集合
      //noinspection unchecked // 忽略未检查的转换警告
      final Collection<String> collection = (Collection<String>) result; // 将集合转换为字符串集合
      return new RefSetResultChecker(ImmutableNullableSet.copyOf(collection)); // 创建RefSetResultChecker，使用不可变可空集合
    } else { // 其他情况
      return isSingle(result.toString()); // 将结果转换为字符串，创建单值检查器
    } // 结束if-else语句
  } // 方法结束

  /**
   * Result checker that checks a result against a regular expression.
   * 结果检查器，使用正则表达式检查结果。
   */
  static class PatternResultChecker implements SqlTester.ResultChecker { // 静态内部类：实现ResultChecker接口，使用正则表达式验证结果
    final Pattern pattern; // 成员变量：存储正则表达式模式，final表示不可变

    PatternResultChecker(Pattern pattern) { // 构造方法：初始化PatternResultChecker
      this.pattern = requireNonNull(pattern, "pattern"); // 存储正则表达式模式，要求pattern参数不能为null
    } // 构造方法结束

    @Override public void checkResult(String sql, ResultSet resultSet) throws Exception { // 实现接口方法：检查结果
      compareResultSetWithPattern(sql, resultSet, pattern); // 调用静态方法使用正则表达式模式比较结果集
    } // 方法结束
  } // 内部类结束

  /**
   * Result checker that checks a result using a {@link org.hamcrest.Matcher}.
   * 结果检查器，使用Hamcrest匹配器检查结果。
   *
   * @param <T> Result type - 泛型类型参数，表示结果类型
   */
  static class MatcherResultChecker<T> implements SqlTester.ResultChecker { // 静态泛型内部类：实现ResultChecker接口，使用Hamcrest匹配器验证结果
    private final Matcher<T> matcher; // 成员变量：存储Hamcrest匹配器，用于验证值
    private final JdbcType<T> jdbcType; // 成员变量：存储JDBC类型，用于从结果集中正确获取值

    MatcherResultChecker(Matcher<T> matcher, JdbcType<T> jdbcType) { // 构造方法：初始化MatcherResultChecker
      this.matcher = requireNonNull(matcher, "matcher"); // 存储匹配器，要求matcher参数不能为null
      this.jdbcType = requireNonNull(jdbcType, "jdbcType"); // 存储JDBC类型，要求jdbcType参数不能为null
    } // 构造方法结束

    @Override public void checkResult(String sql, ResultSet resultSet) throws Exception { // 实现接口方法：检查结果
      compareResultSetWithMatcher(sql, resultSet, jdbcType, matcher); // 调用静态方法使用匹配器比较结果集
    } // 方法结束
  } // 内部类结束

  /**
   * Result checker that checks a result against a list of expected strings.
   * 结果检查器，将结果与期望的字符串列表进行比较。
   */
  static class RefSetResultChecker implements SqlTester.ResultChecker { // 静态内部类：实现ResultChecker接口，使用参考集合验证结果
    private final Set<String> expected; // 成员变量：存储期望的字符串集合，用于与实际结果比较

    RefSetResultChecker(Set<String> expected) { // 构造方法：初始化RefSetResultChecker
      this.expected = ImmutableNullableSet.copyOf(expected); // 将输入集合转换为不可变可空集合并存储
    } // 构造方法结束

    @Override public void checkResult(String sql, ResultSet resultSet) throws Exception { // 实现接口方法：检查结果
      compareResultSet(sql, resultSet, expected); // 调用静态方法比较结果集与期望集合
    } // 方法结束
  } // 内部类结束
} // 类结束
