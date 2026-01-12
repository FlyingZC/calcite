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
// Apache软件基金会许可证声明：本代码遵循Apache 2.0许可证，允许在遵守许可证条款的前提下自由使用、修改和分发
package org.apache.calcite.rex; // 声明本类属于org.apache.calcite.rex包，该包包含了Calcite中行表达式(Rex)相关的核心类
import org.apache.calcite.avatica.util.ByteString; // 导入Avatica工具类中的ByteString，用于处理二进制字符串数据
import org.apache.calcite.avatica.util.DateTimeUtils; // 导入日期时间工具类，提供日期时间相关的常量和工具方法
import org.apache.calcite.avatica.util.Spaces; // 导入空格字符串工具类，用于生成指定长度的空格字符串
import org.apache.calcite.avatica.util.TimeUnit; // 导入时间单位枚举，定义了年、月、日、时、分、秒等时间单位
import org.apache.calcite.rel.core.CorrelationId; // 导入关联ID类，用于标识子查询中的关联关系
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示Calcite中的SQL数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建各种SQL数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段接口，表示结构化类型中的一个字段
import org.apache.calcite.rel.type.RelDataTypeFieldImpl; // 导入关系数据类型字段实现类
import org.apache.calcite.rel.type.RelDataTypeImpl; // 导入关系数据类型实现类，提供数据类型的基本实现
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统接口，定义了类型系统的行为
import org.apache.calcite.rel.type.RelDataTypeSystemImpl; // 导入关系数据类型系统默认实现类
import org.apache.calcite.sql.SqlCollation; // 导入SQL排序规则类，用于定义字符串的排序和比较规则
import org.apache.calcite.sql.SqlKind; // 导入SQL操作类型枚举，定义了SELECT、JOIN、AND等SQL操作
import org.apache.calcite.sql.fun.SqlLibraryOperators; // 导入SQL库操作符集合，包含特定SQL库的操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入标准SQL操作符表，包含所有标准SQL操作符
import org.apache.calcite.sql.type.ArraySqlType; // 导入数组SQL类型类，表示ARRAY类型
import org.apache.calcite.sql.type.BasicSqlType; // 导入基本SQL类型类，表示INT、VARCHAR等基本类型
import org.apache.calcite.sql.type.MapSqlType; // 导入映射SQL类型类，表示MAP类型
import org.apache.calcite.sql.type.MultisetSqlType; // 导入多重集SQL类型类，表示MULTISET类型
import org.apache.calcite.sql.type.SqlTypeFactoryImpl; // 导入SQL类型工厂实现类，提供类型工厂的具体实现
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义了所有SQL类型名称
import org.apache.calcite.test.CustomTypeSystems; // 导入自定义类型系统测试工具类
import org.apache.calcite.test.RexImplicationCheckerFixtures; // 导入Rex蕴含检查测试夹具类
import org.apache.calcite.util.DateString; // 导入日期字符串类，用于表示和操作日期值
import org.apache.calcite.util.Litmus; // 导入断言模式枚举，用于控制断言失败时的行为
import org.apache.calcite.util.NlsString; // 导入国际化字符串类，支持字符集和排序规则
import org.apache.calcite.util.TimeString; // 导入时间字符串类，用于表示和操作时间值
import org.apache.calcite.util.TimeWithTimeZoneString; // 导入带时区的时间字符串类
import org.apache.calcite.util.TimestampString; // 导入时间戳字符串类，用于表示和操作时间戳值
import org.apache.calcite.util.TimestampWithTimeZoneString; // 导入带时区的时间戳字符串类
import org.apache.calcite.util.Util; // 导入通用工具类，提供各种辅助方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类
import com.google.common.collect.ImmutableSet; // 导入Google Guava的不可变集合类

import org.junit.jupiter.api.Test; // 导入JUnit 5的测试注解，标记测试方法
import org.junit.jupiter.params.ParameterizedTest; // 导入JUnit 5的参数化测试注解
import org.junit.jupiter.params.provider.Arguments; // 导入JUnit 5的参数提供者接口
import org.junit.jupiter.params.provider.MethodSource; // 导入JUnit 5的方法源参数提供者注解

import java.math.BigDecimal; // 导入Java数学库中的BigDecimal类，用于精确的十进制运算
import java.math.RoundingMode; // 导入舍入模式枚举，定义了各种舍入策略
import java.nio.charset.StandardCharsets; // 导入标准字符集枚举，如UTF-8、ISO-8859-1等
import java.util.Arrays; // 导入Java数组工具类，提供数组操作方法
import java.util.Calendar; // 导入Java日历类，用于日期时间操作
import java.util.TimeZone; // 导入Java时区类，用于处理时区相关信息
import java.util.function.BiFunction; // 导入Java双参数函数式接口
import java.util.function.Function; // 导入Java单参数函数式接口
import java.util.stream.Stream; // 导入Java流API，支持函数式编程

import static org.hamcrest.CoreMatchers.containsString; // 导入Hamcrest断言工具：包含字符串匹配器
import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest断言工具：相等匹配器
import static org.hamcrest.CoreMatchers.instanceOf; // 导入Hamcrest断言工具：类型实例匹配器
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言工具：相等匹配器（别名）
import static org.hamcrest.CoreMatchers.notNullValue; // 导入Hamcrest断言工具：非空匹配器
import static org.hamcrest.CoreMatchers.nullValue; // 导入Hamcrest断言工具：空值匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具：断言方法
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest断言工具：toString匹配器
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入JUnit 5断言工具：假值断言
import static org.junit.jupiter.api.Assertions.assertNotEquals; // 导入JUnit 5断言工具：不等断言
import static org.junit.jupiter.api.Assertions.assertThrows; // 导入JUnit 5断言工具：异常断言
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit 5断言工具：真值断言
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit 5断言工具：失败断言
import static org.junit.jupiter.params.provider.Arguments.of; // 导入JUnit 5参数工具：创建参数对象

/**
 * Test for {@link RexBuilder}.
 */
// RexBuilderTest类：RexBuilder类的单元测试类，用于测试RexBuilder构建器类的各种功能
// RexBuilder是Calcite中用于构建行表达式(RexNode)的核心构建器，可以创建字面量、函数调用、输入引用等各种表达式
class RexBuilderTest { // 测试类声明，使用JUnit 5测试框架

  private static final int PRECISION = 256; // 静态常量：定义VARCHAR类型的精度为256，用于测试类型工厂的自定义行为

  /**
   * MySqlTypeFactoryImpl provides a specific implementation of
   * {@link SqlTypeFactoryImpl} which sets precision to 256 for VARCHAR.
   */
  // MySqlTypeFactoryImpl内部类：自定义的SQL类型工厂实现类，继承自SqlTypeFactoryImpl
  // 该实现类会强制将所有VARCHAR类型的精度设置为256，用于测试RexBuilder在类型转换时的行为
  private static class MySqlTypeFactoryImpl extends SqlTypeFactoryImpl { // 内部类声明，继承SqlTypeFactoryImpl

    MySqlTypeFactoryImpl(RelDataTypeSystem typeSystem) { // 构造方法：接收一个类型系统参数
      super(typeSystem); // 调用父类构造方法，初始化类型工厂
    }

    @Override public RelDataType createTypeWithNullability( // 重写父类方法：创建带可空性约束的类型
        final RelDataType type, // 参数type：原始类型
        final boolean nullable) { // 参数nullable：是否可空
      if (type.getSqlTypeName() == SqlTypeName.VARCHAR) { // 如果类型是VARCHAR
        return new BasicSqlType(this.typeSystem, type.getSqlTypeName(), // 创建新的VARCHAR类型，强制精度为256
            PRECISION); // 使用静态常量PRECISION作为精度值
      }
      return super.createTypeWithNullability(type, nullable); // 对于非VARCHAR类型，调用父类方法保持原有行为
    }
  } // MySqlTypeFactoryImpl内部类结束


  /**
   * Test RexBuilder.ensureType()
   */
  // testEnsureTypeWithAny测试方法：测试RexBuilder.ensureType()方法在目标类型为ANY时的行为
  // ensureType()方法用于确保表达式节点具有指定的类型，如果类型不匹配则进行类型转换
  @Test void testEnsureTypeWithAny() { // 测试方法声明，使用@Test注解标记为JUnit测试方法
    final RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建默认类型系统工厂
    RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例，传入类型工厂

    RexNode node = // 创建一个RexNode节点，类型为BOOLEAN
        new RexLiteral(Boolean.TRUE, // 创建布尔字面量，值为true
            typeFactory.createSqlType(SqlTypeName.BOOLEAN), SqlTypeName.BOOLEAN); // 指定类型为BOOLEAN
    RexNode ensuredNode = // 调用ensureType方法，确保节点类型为ANY
        builder.ensureType(typeFactory.createSqlType(SqlTypeName.ANY), node, // 目标类型为ANY，源节点为node
            true); // 允许类型转换

    assertThat(ensuredNode, is(node)); // 断言：确保后的节点应该与原节点相同（因为ANY类型可以接受任何类型）
  }

  /**
   * Test RexBuilder.ensureType()
   */
  // testEnsureTypeWithItself测试方法：测试RexBuilder.ensureType()方法在目标类型与源类型相同时的行为
  @Test void testEnsureTypeWithItself() { // 测试方法声明
    final RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建默认类型系统工厂
    RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

    RexNode node = // 创建BOOLEAN类型的字面量节点
        new RexLiteral(Boolean.TRUE, // 值为true
            typeFactory.createSqlType(SqlTypeName.BOOLEAN), SqlTypeName.BOOLEAN); // 类型为BOOLEAN
    RexNode ensuredNode = // 调用ensureType方法，目标类型也是BOOLEAN
        builder.ensureType(typeFactory.createSqlType(SqlTypeName.BOOLEAN), node, // 目标类型与源类型相同
            true); // 允许类型转换

    assertThat(ensuredNode, is(node)); // 断言：当目标类型与源类型相同时，应该返回原节点，不进行转换
  }

  /**
   * Test RexBuilder.ensureType()
   */
  // testEnsureTypeWithDifference测试方法：测试RexBuilder.ensureType()方法在目标类型与源类型不同时的行为
  @Test void testEnsureTypeWithDifference() { // 测试方法声明
    final RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建默认类型系统工厂
    RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

    RexNode node = // 创建BOOLEAN类型的字面量节点
        new RexLiteral(Boolean.TRUE, // 值为true
            typeFactory.createSqlType(SqlTypeName.BOOLEAN), SqlTypeName.BOOLEAN); // 类型为BOOLEAN
    RexNode ensuredNode = // 调用ensureType方法，目标类型为INTEGER（与源类型不同）
        builder.ensureType(typeFactory.createSqlType(SqlTypeName.INTEGER), node, // 目标类型为INTEGER，源类型为BOOLEAN
            true); // 允许类型转换

    assertNotEquals(node, ensuredNode); // 断言：转换后的节点应该与原节点不同
    assertThat(typeFactory.createSqlType(SqlTypeName.INTEGER), // 断言：转换后节点的类型应该是INTEGER
        is(ensuredNode.getType())); // 检查ensuredNode的类型是否为INTEGER
  }

  private static final long MOON = -14159025000L; // 静态常量：表示阿波罗11号登月时刻的毫秒数（1969年7月21日02:56:15 GMT）

  private static final int MOON_DAY = -164; // 静态常量：表示阿波罗登月日期的天数偏移量（相对于1970-01-01）

  private static final int MOON_TIME = 10575000; // 静态常量：表示阿波罗登月时刻的毫秒数（从午夜开始计算）

  /** Tests {@link RexBuilder#makeTimestampLiteral(TimestampString, int)}. */
  // testTimestampLiteral测试方法：测试RexBuilder.makeTimestampLiteral()方法，用于创建TIMESTAMP类型的字面量
  // 该方法支持多种精度（0-18位小数），测试了从Calendar、Long和TimestampString三种方式创建时间戳字面量
  @Test void testTimestampLiteral() { // 测试方法声明
    final RelDataTypeFactory typeFactory = // 创建类型工厂实例
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    final RelDataType timestampType = // 创建TIMESTAMP类型（默认精度0）
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP); // 不指定精度，使用默认值
    final RelDataType timestampType3 = // 创建TIMESTAMP类型（精度3，支持毫秒）
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP, 3); // 精度为3，表示毫秒级精度
    final RelDataType timestampType9 = // 创建TIMESTAMP类型（精度9，支持纳秒）
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP, 9); // 精度为9，表示纳秒级精度
    final RelDataType timestampType18 = // 创建TIMESTAMP类型（精度18，支持更高精度）
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP, 18); // 精度为18，支持更高精度的小数
    final RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

    // Old way: provide a Calendar
    final Calendar calendar = Util.calendar(); // 创建日历对象（旧方式1：使用Calendar）
    calendar.set(1969, Calendar.JULY, 21, 2, 56, 15); // 设置日期时间为1969年7月21日02:56:15（阿波罗登月时刻）
    calendar.set(Calendar.MILLISECOND, 0); // 设置毫秒为0
    checkTimestamp(builder.makeLiteral(calendar, timestampType)); // 使用Calendar创建时间戳字面量并验证

    // Old way #2: Provide a Long
    checkTimestamp(builder.makeLiteral(MOON, timestampType)); // 使用Long值创建时间戳字面量并验证（旧方式2）

    // The new way
    final TimestampString ts = new TimestampString(1969, 7, 21, 2, 56, 15); // 创建TimestampString对象（新方式）
    checkTimestamp(builder.makeLiteral(ts, timestampType)); // 使用TimestampString创建时间戳字面量并验证

    // Now with milliseconds
    final TimestampString ts2 = ts.withMillis(56); // 添加56毫秒
    assertThat(ts2, hasToString("1969-07-21 02:56:15.056")); // 验证字符串表示
    final RexLiteral literal2 = builder.makeLiteral(ts2, timestampType3); // 创建精度为3的时间戳字面量
    assertThat(literal2.getValueAs(TimestampString.class), // 验证字面量值
        hasToString("1969-07-21 02:56:15.056")); // 应该包含毫秒部分

    // Now with nanoseconds
    final TimestampString ts3 = ts.withNanos(56); // 添加56纳秒
    final RexLiteral literal3 = builder.makeLiteral(ts3, timestampType9); // 创建精度为9的时间戳字面量
    assertThat(literal3.getValueAs(TimestampString.class), // 验证字面量值
        hasToString("1969-07-21 02:56:15")); // 56纳秒会被截断，因为精度不足
    final TimestampString ts3b = ts.withNanos(2345678); // 添加2345678纳秒（约2.345678毫秒）
    final RexLiteral literal3b = builder.makeLiteral(ts3b, timestampType9); // 创建精度为9的时间戳字面量
    assertThat(literal3b.getValueAs(TimestampString.class), // 验证字面量值
        hasToString("1969-07-21 02:56:15.002")); // 应该显示为.002（四舍五入）

    // Now with a very long fraction
    final TimestampString ts4 = ts.withFraction("102030405060708090102"); // 添加很长的分数部分
    final RexLiteral literal4 = builder.makeLiteral(ts4, timestampType18); // 创建精度为18的时间戳字面量
    assertThat(literal4.getValueAs(TimestampString.class), // 验证字面量值
        hasToString("1969-07-21 02:56:15.102")); // 应该截断到精度18，显示为.102
  }

  /** Test cases for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6389">[CALCITE-6389]
   * RexBuilder.removeCastFromLiteral does not preserve semantics for some types of literal</a>. */
  // testRemoveCast测试方法：测试RexBuilder.canRemoveCastFromLiteral()方法
  // 该方法用于判断是否可以安全地移除字面量的类型转换而不改变其语义
  // 测试用例来自JIRA issue CALCITE-6389，验证各种类型转换场景下的语义保持性
  @Test void testRemoveCast() { // 测试方法声明
    final RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂
    RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

    // Can remove cast of an integer to an integer
    BigDecimal value = new BigDecimal(10); // 创建BigDecimal值10
    RelDataType toType = builder.typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建INTEGER类型
    assertTrue(builder.canRemoveCastFromLiteral(toType, value, SqlTypeName.INTEGER)); // 断言：INTEGER到INTEGER的转换可以移除

    // Can remove cast from integer to decimal
    toType = builder.typeFactory.createSqlType(SqlTypeName.DECIMAL); // 创建DECIMAL类型
    assertTrue(builder.canRemoveCastFromLiteral(toType, value, SqlTypeName.INTEGER)); // 断言：INTEGER到DECIMAL的转换可以移除

    // 250 is too large for a TINYINT
    value = new BigDecimal(250); // 创建BigDecimal值250
    toType = builder.typeFactory.createSqlType(SqlTypeName.TINYINT); // 创建TINYINT类型（范围-128到127）
    assertFalse(builder.canRemoveCastFromLiteral(toType, value, SqlTypeName.INTEGER)); // 断言：250超出TINYINT范围，不能移除转换

    // 50 isn't too large for a TINYINT
    value = new BigDecimal(50); // 创建BigDecimal值50
    toType = builder.typeFactory.createSqlType(SqlTypeName.TINYINT); // 创建TINYINT类型
    assertTrue(builder.canRemoveCastFromLiteral(toType, value, SqlTypeName.INTEGER)); // 断言：50在TINYINT范围内，可以移除转换

    // 120.25 cannot be represented with precision 2 and scale 2 without loss
    value = new BigDecimal("120.25"); // 创建BigDecimal值120.25
    toType = builder.typeFactory.createSqlType(SqlTypeName.DECIMAL, 2, 2); // 创建DECIMAL(2,2)类型（精度2，小数位2）
    assertFalse(builder.canRemoveCastFromLiteral(toType, value, SqlTypeName.DECIMAL)); // 断言：120.25需要精度5，无法用DECIMAL(2,2)表示

    // 120.25 cannot be represented with precision 5 and scale 1 without rounding
    value = new BigDecimal("120.25"); // 创建BigDecimal值120.25
    toType = builder.typeFactory.createSqlType(SqlTypeName.DECIMAL, 5, 1); // 创建DECIMAL(5,1)类型（精度5，小数位1）
    assertFalse(builder.canRemoveCastFromLiteral(toType, value, SqlTypeName.DECIMAL)); // 断言：120.25需要2位小数，转换为DECIMAL(5,1)需要舍入

    // longmax + 1 cannot be represented as a long
    value = new BigDecimal(Long.MAX_VALUE).add(BigDecimal.ONE); // 创建BigDecimal值Long.MAX_VALUE + 1
    toType = builder.typeFactory.createSqlType(SqlTypeName.BIGINT); // 创建BIGINT类型
    assertFalse(builder.canRemoveCastFromLiteral(toType, value, SqlTypeName.DECIMAL)); // 断言：超出BIGINT范围，不能移除转换

    // Cast to decimal of an INTERVAL '5' seconds cannot be removed
    value = new BigDecimal("5"); // 创建BigDecimal值5
    toType = builder.typeFactory.createSqlType(SqlTypeName.DECIMAL, 5, 1); // 创建DECIMAL(5,1)类型
    assertFalse(builder.canRemoveCastFromLiteral(toType, value, SqlTypeName.INTERVAL_SECOND)); // 断言：INTERVAL类型的转换不能移除

    // Cast to decimal of an INTERVAL '5' minutes cannot be removed
    value = new BigDecimal("5"); // 创建BigDecimal值5
    toType = builder.typeFactory.createSqlType(SqlTypeName.DECIMAL, 5, 1); // 创建DECIMAL(5,1)类型
    assertFalse(builder.canRemoveCastFromLiteral(toType, value, SqlTypeName.INTERVAL_MINUTE)); // 断言：INTERVAL类型的转换不能移除
  }

  @Test void testTimestampString() { // testTimestampString测试方法：测试TimestampString类的各种操作，包括创建、舍入、字符串转换等
    final TimestampString ts = new TimestampString(1969, 7, 21, 2, 56, 15); // 创建时间戳字符串对象（年月日时分秒）
    assertThat(ts, hasToString("1969-07-21 02:56:15")); // 验证字符串表示
    assertThat(ts.round(1), is(ts)); // 舍入到1位小数，应该保持不变（因为没有小数部分）

    // Now with milliseconds
    final TimestampString ts2 = ts.withMillis(56); // 添加56毫秒
    assertThat(ts2, hasToString("1969-07-21 02:56:15.056")); // 验证字符串表示包含毫秒

    // toString
    assertThat(ts2.round(1), hasToString("1969-07-21 02:56:15")); // 舍入到1位小数（十分之一秒），应该舍去毫秒
    assertThat(ts2.round(2), hasToString("1969-07-21 02:56:15.05")); // 舍入到2位小数（百分之一秒）
    assertThat(ts2.round(3), hasToString("1969-07-21 02:56:15.056")); // 舍入到3位小数（千分之一秒），应该保持不变
    assertThat(ts2.round(4), hasToString("1969-07-21 02:56:15.056")); // 舍入到4位小数，应该保持不变

    assertThat(ts2.toString(6), is("1969-07-21 02:56:15.056000")); // 转换为6位小数格式
    assertThat(ts2.toString(1), is("1969-07-21 02:56:15.0")); // 转换为1位小数格式
    assertThat(ts2.toString(0), is("1969-07-21 02:56:15")); // 转换为0位小数格式

    assertThat(ts2.round(0), hasToString("1969-07-21 02:56:15")); // 舍入到0位小数
    assertThat(ts2.round(0).toString(0), is("1969-07-21 02:56:15")); // 舍入后转换为0位小数格式
    assertThat(ts2.round(0).toString(1), is("1969-07-21 02:56:15.0")); // 舍入后转换为1位小数格式
    assertThat(ts2.round(0).toString(2), is("1969-07-21 02:56:15.00")); // 舍入后转换为2位小数格式

    // Now with milliseconds ending in zero (3 equivalent strings).
    final TimestampString ts3 = ts.withMillis(10); // 添加10毫秒
    assertThat(ts3, hasToString("1969-07-21 02:56:15.01")); // 验证字符串表示（末尾的0会被省略）

    final TimestampString ts3b = new TimestampString("1969-07-21 02:56:15.01"); // 从字符串创建
    assertThat(ts3b, hasToString("1969-07-21 02:56:15.01")); // 验证字符串表示
    assertThat(ts3b, is(ts3)); // 验证两个对象相等

    final TimestampString ts3c = new TimestampString("1969-07-21 02:56:15.010"); // 从字符串创建（带末尾0）
    assertThat(ts3c, hasToString("1969-07-21 02:56:15.01")); // 验证字符串表示（末尾0被省略）
    assertThat(ts3c, is(ts3)); // 验证两个对象相等

    // Now with nanoseconds
    final TimestampString ts4 = ts.withNanos(56); // 添加56纳秒
    assertThat(ts4, hasToString("1969-07-21 02:56:15.000000056")); // 验证字符串表示（纳秒部分）

    // Check rounding; uses RoundingMode.DOWN
    final TimestampString ts5 = ts.withNanos(2345670); // 添加2345670纳秒（约2.34567毫秒）
    assertThat(ts5, hasToString("1969-07-21 02:56:15.00234567")); // 验证字符串表示
    assertThat(ts5.round(0), hasToString("1969-07-21 02:56:15")); // 舍入到0位小数
    assertThat(ts5.round(1), hasToString("1969-07-21 02:56:15")); // 舍入到1位小数
    assertThat(ts5.round(2), hasToString("1969-07-21 02:56:15")); // 舍入到2位小数
    assertThat(ts5.round(3), hasToString("1969-07-21 02:56:15.002")); // 舍入到3位小数
    assertThat(ts5.round(4), hasToString("1969-07-21 02:56:15.0023")); // 舍入到4位小数
    assertThat(ts5.round(5), hasToString("1969-07-21 02:56:15.00234")); // 舍入到5位小数
    assertThat(ts5.round(6), hasToString("1969-07-21 02:56:15.002345")); // 舍入到6位小数
    assertThat(ts5.round(600), hasToString("1969-07-21 02:56:15.00234567")); // 舍入到600位小数（保持原值）

    // Now with a very long fraction
    final TimestampString ts6 = ts.withFraction("102030405060708090102"); // 添加很长的分数部分
    assertThat(ts6, hasToString("1969-07-21 02:56:15.102030405060708090102")); // 验证字符串表示

    // From milliseconds
    final TimestampString ts7 = // 从自纪元以来的毫秒数创建
        TimestampString.fromMillisSinceEpoch(1456513560123L); // 2016-02-26 19:06:00.123
    assertThat(ts7, hasToString("2016-02-26 19:06:00.123")); // 验证字符串表示

    final TimestampString ts8 = // 从自纪元以来的毫秒数创建
        TimestampString.fromMillisSinceEpoch(1456513560120L); // 2016-02-26 19:06:00.12
    assertThat(ts8, hasToString("2016-02-26 19:06:00.12")); // 验证字符串表示

    final TimestampString ts9 = ts8.withFraction("9876543210"); // 添加分数部分
    assertThat(ts9, hasToString("2016-02-26 19:06:00.987654321")); // 验证字符串表示

    // TimestampString.toCalendar
    final Calendar c = ts9.toCalendar(); // 转换为Calendar对象
    assertThat(c.get(Calendar.ERA), is(1)); // 验证纪元（1表示公元）
    assertThat(c.get(Calendar.YEAR), is(2016)); // 验证年份
    assertThat(c.get(Calendar.MONTH), is(1)); // 验证月份（1表示二月，从0开始）
    assertThat(c.get(Calendar.DATE), is(26)); // 验证日期
    assertThat(c.get(Calendar.HOUR_OF_DAY), is(19)); // 验证小时（24小时制）
    assertThat(c.get(Calendar.MINUTE), is(6)); // 验证分钟
    assertThat(c.get(Calendar.SECOND), is(0)); // 验证秒
    assertThat(c.get(Calendar.MILLISECOND), is(987)); // 验证毫秒（使用RoundingMode.DOWN舍入）
    assertThat(ts9.getMillisSinceEpoch(), is(c.getTimeInMillis())); // 验证自纪元以来的毫秒数一致

    // TimestampString.fromCalendarFields
    c.set(Calendar.YEAR, 1969); // 修改年份为1969
    final TimestampString ts10 = TimestampString.fromCalendarFields(c); // 从Calendar字段创建
    assertThat(ts10, hasToString("1969-02-26 19:06:00.987")); // 验证字符串表示
    assertThat(ts10.getMillisSinceEpoch(), is(c.getTimeInMillis())); // 验证自纪元以来的毫秒数一致
  }

  @Test void testTimeString() { // testTimeString测试方法：测试TimeString类的各种操作，包括创建、舍入、字符串转换等
    final TimeString t = new TimeString(2, 56, 15); // 创建时间字符串对象（时分秒）
    assertThat(t, hasToString("02:56:15")); // 验证字符串表示
    assertThat(t.round(1), is(t)); // 舍入到1位小数，应该保持不变（因为没有小数部分）

    // Now with milliseconds
    final TimeString t2 = t.withMillis(56); // 添加56毫秒
    assertThat(t2, hasToString("02:56:15.056")); // 验证字符串表示包含毫秒

    // toString
    assertThat(t2.round(1), hasToString("02:56:15")); // 舍入到1位小数（十分之一秒），应该舍去毫秒
    assertThat(t2.round(2), hasToString("02:56:15.05")); // 舍入到2位小数（百分之一秒）
    assertThat(t2.round(3), hasToString("02:56:15.056")); // 舍入到3位小数（千分之一秒），应该保持不变
    assertThat(t2.round(4), hasToString("02:56:15.056")); // 舍入到4位小数，应该保持不变

    assertThat(t2.toString(6), is("02:56:15.056000")); // 转换为6位小数格式
    assertThat(t2.toString(1), is("02:56:15.0")); // 转换为1位小数格式
    assertThat(t2.toString(0), is("02:56:15")); // 转换为0位小数格式

    assertThat(t2.round(0), hasToString("02:56:15")); // 舍入到0位小数
    assertThat(t2.round(0).toString(0), is("02:56:15")); // 舍入后转换为0位小数格式
    assertThat(t2.round(0).toString(1), is("02:56:15.0")); // 舍入后转换为1位小数格式
    assertThat(t2.round(0).toString(2), is("02:56:15.00")); // 舍入后转换为2位小数格式

    // Now with milliseconds ending in zero (3 equivalent strings).
    final TimeString t3 = t.withMillis(10); // 添加10毫秒
    assertThat(t3, hasToString("02:56:15.01")); // 验证字符串表示（末尾的0会被省略）

    final TimeString t3b = new TimeString("02:56:15.01"); // 从字符串创建
    assertThat(t3b, hasToString("02:56:15.01")); // 验证字符串表示
    assertThat(t3b, is(t3)); // 验证两个对象相等

    final TimeString t3c = new TimeString("02:56:15.010"); // 从字符串创建（带末尾0）
    assertThat(t3c, hasToString("02:56:15.01")); // 验证字符串表示（末尾0被省略）
    assertThat(t3c, is(t3)); // 验证两个对象相等

    // Now with nanoseconds
    final TimeString t4 = t.withNanos(56); // 添加56纳秒
    assertThat(t4, hasToString("02:56:15.000000056")); // 验证字符串表示（纳秒部分）

    // Check rounding; uses RoundingMode.DOWN
    final TimeString t5 = t.withNanos(2345670); // 添加2345670纳秒（约2.34567毫秒）
    assertThat(t5, hasToString("02:56:15.00234567")); // 验证字符串表示
    assertThat(t5.round(0), hasToString("02:56:15")); // 舍入到0位小数
    assertThat(t5.round(1), hasToString("02:56:15")); // 舍入到1位小数
    assertThat(t5.round(2), hasToString("02:56:15")); // 舍入到2位小数
    assertThat(t5.round(3), hasToString("02:56:15.002")); // 舍入到3位小数
    assertThat(t5.round(4), hasToString("02:56:15.0023")); // 舍入到4位小数
    assertThat(t5.round(5), hasToString("02:56:15.00234")); // 舍入到5位小数
    assertThat(t5.round(6), hasToString("02:56:15.002345")); // 舍入到6位小数
    assertThat(t5.round(600), hasToString("02:56:15.00234567")); // 舍入到600位小数（保持原值）

    // Now with a very long fraction
    final TimeString t6 = t.withFraction("102030405060708090102"); // 添加很长的分数部分
    assertThat(t6, hasToString("02:56:15.102030405060708090102")); // 验证字符串表示
  }

  private void checkTimestamp(RexLiteral literal) { // checkTimestamp辅助方法：验证时间戳字面量的各种属性
    assertThat(literal, hasToString("1969-07-21 02:56:15")); // 验证字符串表示
    assertThat(literal.getValue() instanceof Calendar, is(true)); // 验证getValue()返回Calendar对象
    assertThat(literal.getValue2() instanceof Long, is(true)); // 验证getValue2()返回Long对象（毫秒数）
    assertThat(literal.getValue3() instanceof Long, is(true)); // 验证getValue3()返回Long对象（天数偏移）
    assertThat((Long) literal.getValue2(), is(MOON)); // 验证毫秒数为MOON常量
    assertThat(literal.getValueAs(Calendar.class), notNullValue()); // 验证可以转换为Calendar
    assertThat(literal.getValueAs(TimestampString.class), notNullValue()); // 验证可以转换为TimestampString
  }

  /** Tests
   * {@link RexBuilder#makeTimestampWithLocalTimeZoneLiteral(TimestampString, int)}. */
  // testTimestampWithLocalTimeZoneLiteral测试方法：测试RexBuilder.makeTimestampWithLocalTimeZoneLiteral()方法
  // 该方法用于创建TIMESTAMP WITH LOCAL TIME ZONE类型的字面量，支持多种精度
  @Test void testTimestampWithLocalTimeZoneLiteral() { // 测试方法声明
    final RelDataTypeFactory typeFactory = // 创建类型工厂实例
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    final RelDataType timestampType = // 创建TIMESTAMP WITH LOCAL TIME ZONE类型（默认精度0）
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE); // 不指定精度
    final RelDataType timestampType3 = // 创建TIMESTAMP WITH LOCAL TIME ZONE类型（精度3）
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE, 3); // 精度为3
    final RelDataType timestampType9 = // 创建TIMESTAMP WITH LOCAL TIME ZONE类型（精度9）
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE, 9); // 精度为9
    final RelDataType timestampType18 = // 创建TIMESTAMP WITH LOCAL TIME ZONE类型（精度18）
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE, 18); // 精度为18
    final RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

    // The new way
    final TimestampWithTimeZoneString ts = // 创建带时区的时间戳字符串对象
        new TimestampWithTimeZoneString(1969, 7, 21, 2, 56, 15, // 年月日时分秒
            TimeZone.getTimeZone("PST").getID()); // 使用PST时区（太平洋标准时间）
    checkTimestampWithLocalTimeZone( // 验证时间戳字面量
        builder.makeLiteral(ts.getLocalTimestampString(), timestampType)); // 使用本地时间戳字符串创建字面量

    // Now with milliseconds
    final TimestampWithTimeZoneString ts2 = ts.withMillis(56); // 添加56毫秒
    assertThat(ts2, hasToString("1969-07-21 02:56:15.056 PST")); // 验证字符串表示
    final RexLiteral literal2 = // 创建精度为3的时间戳字面量
        builder.makeLiteral(ts2.getLocalTimestampString(), timestampType3); // 使用本地时间戳字符串
    assertThat(literal2.getValue(), hasToString("1969-07-21 02:56:15.056")); // 验证字面量值

    // Now with nanoseconds
    final TimestampWithTimeZoneString ts3 = ts.withNanos(56); // 添加56纳秒
    final RexLiteral literal3 = // 创建精度为9的时间戳字面量
        builder.makeLiteral(ts3.getLocalTimestampString(), timestampType9); // 使用本地时间戳字符串
    assertThat(literal3.getValueAs(TimestampString.class), // 验证字面量值
        hasToString("1969-07-21 02:56:15")); // 56纳秒被截断
    final TimestampWithTimeZoneString ts3b = ts.withNanos(2345678); // 添加2345678纳秒
    final RexLiteral literal3b = // 创建精度为9的时间戳字面量
        builder.makeLiteral(ts3b.getLocalTimestampString(), timestampType9); // 使用本地时间戳字符串
    assertThat(literal3b.getValueAs(TimestampString.class), // 验证字面量值
        hasToString("1969-07-21 02:56:15.002")); // 四舍五入到.002

    // Now with a very long fraction
    final TimestampWithTimeZoneString ts4 = ts.withFraction("102030405060708090102"); // 添加很长的分数部分
    final RexLiteral literal4 = // 创建精度为18的时间戳字面量
        builder.makeLiteral(ts4.getLocalTimestampString(), timestampType18); // 使用本地时间戳字符串
    assertThat(literal4.getValueAs(TimestampString.class), // 验证字面量值
        hasToString("1969-07-21 02:56:15.102")); // 截断到精度18

    // toString
    assertThat(ts2.round(1), hasToString("1969-07-21 02:56:15 PST")); // 舍入到1位小数
    assertThat(ts2.round(2), hasToString("1969-07-21 02:56:15.05 PST")); // 舍入到2位小数
    assertThat(ts2.round(3), hasToString("1969-07-21 02:56:15.056 PST")); // 舍入到3位小数
    assertThat(ts2.round(4), hasToString("1969-07-21 02:56:15.056 PST")); // 舍入到4位小数

    assertThat(ts2.toString(6), is("1969-07-21 02:56:15.056000 PST")); // 转换为6位小数格式
    assertThat(ts2.toString(1), is("1969-07-21 02:56:15.0 PST")); // 转换为1位小数格式
    assertThat(ts2.toString(0), is("1969-07-21 02:56:15 PST")); // 转换为0位小数格式

    assertThat(ts2.round(0), hasToString("1969-07-21 02:56:15 PST")); // 舍入到0位小数
    assertThat(ts2.round(0).toString(0), is("1969-07-21 02:56:15 PST")); // 舍入后转换为0位小数格式
    assertThat(ts2.round(0).toString(1), is("1969-07-21 02:56:15.0 PST")); // 舍入后转换为1位小数格式
    assertThat(ts2.round(0).toString(2), is("1969-07-21 02:56:15.00 PST")); // 舍入后转换为2位小数格式
  }

  private void checkTimestampWithLocalTimeZone(RexLiteral literal) { // checkTimestampWithLocalTimeZone辅助方法：验证带本地时区的时间戳字面量
    assertThat(literal, // 验证字面量的字符串表示
        hasToString("1969-07-21 02:56:15:TIMESTAMP_WITH_LOCAL_TIME_ZONE(0)")); // 包含类型信息
    assertThat(literal.getValue() instanceof TimestampString, is(true)); // 验证getValue()返回TimestampString
    assertThat(literal.getValue2() instanceof Long, is(true)); // 验证getValue2()返回Long
    assertThat(literal.getValue3() instanceof Long, is(true)); // 验证getValue3()返回Long
  }

  /** Tests
   * {@link RexBuilder#makeTimestampTzLiteral(TimestampWithTimeZoneString, int)}. */
  // testTimestampTzLiterals测试方法：测试RexBuilder.makeTimestampTzLiteral()方法
  // 该方法用于创建TIMESTAMP WITH TIME ZONE类型的字面量，支持多种精度
  @Test void testTimestampTzLiterals() { // 测试方法声明
    final RelDataTypeFactory typeFactory = // 创建类型工厂实例
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    final RelDataType timestampType = // 创建TIMESTAMP_TZ类型（默认精度0）
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP_TZ); // 不指定精度
    final RelDataType timestampType3 = // 创建TIMESTAMP_TZ类型（精度3）
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP_TZ, 3); // 精度为3
    final RelDataType timestampType9 = // 创建TIMESTAMP_TZ类型（精度9）
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP_TZ, 9); // 精度为9
    final RelDataType timestampType18 = // 创建TIMESTAMP_TZ类型（精度18）
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP_TZ, 18); // 精度为18
    final RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

    // The new way
    final TimestampWithTimeZoneString ts = // 创建带时区的时间戳字符串对象
        new TimestampWithTimeZoneString(1969, 7, 21, 2, 56, 15, // 年月日时分秒
            TimeZone.getTimeZone("PST").getID()); // 使用PST时区（太平洋标准时间）
    checkTimestampTz(builder.makeLiteral(ts, timestampType)); // 验证时间戳字面量

    // Now with milliseconds
    final TimestampWithTimeZoneString ts2 = ts.withMillis(56); // 添加56毫秒
    assertThat(ts2, hasToString("1969-07-21 02:56:15.056 PST")); // 验证字符串表示
    final RexLiteral literal2 = // 创建精度为3的时间戳字面量
        builder.makeLiteral(ts2, timestampType3); // 使用TimestampWithTimeZoneString
    assertThat(literal2.getValue(), hasToString("1969-07-21 02:56:15.056 PST")); // 验证字面量值

    // Now with nanoseconds
    final TimestampWithTimeZoneString ts3 = ts.withNanos(56); // 添加56纳秒
    final RexLiteral literal3 = // 创建精度为9的时间戳字面量
        builder.makeLiteral(ts3, timestampType9); // 使用TimestampWithTimeZoneString
    assertThat(literal3.getValueAs(TimestampWithTimeZoneString.class), // 验证字面量值
        hasToString("1969-07-21 02:56:15 PST")); // 56纳秒被截断
    final TimestampWithTimeZoneString ts3b = ts.withNanos(2345678); // 添加2345678纳秒
    final RexLiteral literal3b = // 创建精度为9的时间戳字面量
        builder.makeLiteral(ts3b, timestampType9); // 使用TimestampWithTimeZoneString
    assertThat(literal3b.getValueAs(TimestampWithTimeZoneString.class), // 验证字面量值
        hasToString("1969-07-21 02:56:15.002 PST")); // 四舍五入到.002

    // Now with a very long fraction
    final TimestampWithTimeZoneString ts4 = ts.withFraction("102030405060708090102"); // 添加很长的分数部分
    final RexLiteral literal4 = // 创建精度为18的时间戳字面量
        builder.makeLiteral(ts4, timestampType18); // 使用TimestampWithTimeZoneString
    assertThat(literal4.getValueAs(TimestampWithTimeZoneString.class), // 验证字面量值
        hasToString("1969-07-21 02:56:15.102 PST")); // 截断到精度18

    // toString
    assertThat(ts2.round(1), hasToString("1969-07-21 02:56:15 PST")); // 舍入到1位小数
    assertThat(ts2.round(2), hasToString("1969-07-21 02:56:15.05 PST")); // 舍入到2位小数
    assertThat(ts2.round(3), hasToString("1969-07-21 02:56:15.056 PST")); // 舍入到3位小数
    assertThat(ts2.round(4), hasToString("1969-07-21 02:56:15.056 PST")); // 舍入到4位小数

    assertThat(ts2.toString(6), is("1969-07-21 02:56:15.056000 PST")); // 转换为6位小数格式
    assertThat(ts2.toString(1), is("1969-07-21 02:56:15.0 PST")); // 转换为1位小数格式
    assertThat(ts2.toString(0), is("1969-07-21 02:56:15 PST")); // 转换为0位小数格式

    assertThat(ts2.round(0), hasToString("1969-07-21 02:56:15 PST")); // 舍入到0位小数
    assertThat(ts2.round(0).toString(0), is("1969-07-21 02:56:15 PST")); // 舍入后转换为0位小数格式
    assertThat(ts2.round(0).toString(1), is("1969-07-21 02:56:15.0 PST")); // 舍入后转换为1位小数格式
    assertThat(ts2.round(0).toString(2), is("1969-07-21 02:56:15.00 PST")); // 舍入后转换为2位小数格式
  }

  private void checkTimestampTz(RexLiteral literal) { // checkTimestampTz辅助方法：验证带时区的时间戳字面量
    assertThat(literal, // 验证字面量的字符串表示
        hasToString("1969-07-21 02:56:15 PST:TIMESTAMP_TZ(0)")); // 包含时区和类型信息
    assertThat(literal.getValue() instanceof TimestampWithTimeZoneString, is(true)); // 验证getValue()返回TimestampWithTimeZoneString
    assertThat(literal.getValue2() instanceof Long, is(true)); // 验证getValue2()返回Long
    assertThat(literal.getValue3() instanceof Long, is(true)); // 验证getValue3()返回Long
  }

  /** Tests {@link RexBuilder#makeTimeLiteral(TimeString, int)}. */
  // testTimeLiteral测试方法：测试RexBuilder.makeTimeLiteral()方法
  // 该方法用于创建TIME类型的字面量，支持多种精度，测试了从Calendar、Long和TimeString三种方式创建时间字面量
  @Test void testTimeLiteral() { // 测试方法声明
    final RelDataTypeFactory typeFactory = // 创建类型工厂实例
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    RelDataType timeType = typeFactory.createSqlType(SqlTypeName.TIME); // 创建TIME类型（默认精度0）
    final RelDataType timeType3 = // 创建TIME类型（精度3）
        typeFactory.createSqlType(SqlTypeName.TIME, 3); // 精度为3，支持毫秒
    final RelDataType timeType9 = // 创建TIME类型（精度9）
        typeFactory.createSqlType(SqlTypeName.TIME, 9); // 精度为9，支持纳秒
    final RelDataType timeType18 = // 创建TIME类型（精度18）
        typeFactory.createSqlType(SqlTypeName.TIME, 18); // 精度为18，支持更高精度
    final RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

    // Old way: provide a Calendar
    final Calendar calendar = Util.calendar(); // 创建日历对象（旧方式1：使用Calendar）
    calendar.set(1969, Calendar.JULY, 21, 2, 56, 15); // 设置日期时间为1969年7月21日02:56:15
    calendar.set(Calendar.MILLISECOND, 0); // 设置毫秒为0
    checkTime(builder.makeLiteral(calendar, timeType)); // 使用Calendar创建时间字面量并验证

    // Old way #2: Provide a Long
    checkTime(builder.makeLiteral(MOON_TIME, timeType)); // 使用Long值创建时间字面量并验证（旧方式2）

    // The new way
    final TimeString t = new TimeString(2, 56, 15); // 创建TimeString对象（新方式）
    assertThat(t.getMillisOfDay(), is(10575000)); // 验证一天的毫秒数（2小时56分15秒=10575000毫秒）
    checkTime(builder.makeLiteral(t, timeType)); // 使用TimeString创建时间字面量并验证

    // Now with milliseconds
    final TimeString t2 = t.withMillis(56); // 添加56毫秒
    assertThat(t2.getMillisOfDay(), is(10575056)); // 验证一天的毫秒数（10575000+56=10575056）
    assertThat(t2, hasToString("02:56:15.056")); // 验证字符串表示
    final RexLiteral literal2 = builder.makeLiteral(t2, timeType3); // 创建精度为3的时间字面量
    assertThat(literal2.getValueAs(TimeString.class), // 验证字面量值
        hasToString("02:56:15.056")); // 应该包含毫秒部分

    // Now with nanoseconds
    final TimeString t3 = t.withNanos(2345678); // 添加2345678纳秒（约2.345678毫秒）
    assertThat(t3.getMillisOfDay(), is(10575002)); // 验证一天的毫秒数（四舍五入到毫秒）
    final RexLiteral literal3 = builder.makeLiteral(t3, timeType9); // 创建精度为9的时间字面量
    assertThat(literal3.getValueAs(TimeString.class), // 验证字面量值
        hasToString("02:56:15.002")); // 应该显示为.002（四舍五入）

    // Now with a very long fraction
    final TimeString t4 = t.withFraction("102030405060708090102"); // 添加很长的分数部分
    assertThat(t4.getMillisOfDay(), is(10575102)); // 验证一天的毫秒数（截断到毫秒）
    final RexLiteral literal4 = builder.makeLiteral(t4, timeType18); // 创建精度为18的时间字面量
    assertThat(literal4.getValueAs(TimeString.class), // 验证字面量值
        hasToString("02:56:15.102")); // 应该截断到精度18，显示为.102

    // toString
    assertThat(t2.round(1), hasToString("02:56:15")); // 舍入到1位小数
    assertThat(t2.round(2), hasToString("02:56:15.05")); // 舍入到2位小数
    assertThat(t2.round(3), hasToString("02:56:15.056")); // 舍入到3位小数
    assertThat(t2.round(4), hasToString("02:56:15.056")); // 舍入到4位小数

    assertThat(t2.toString(6), is("02:56:15.056000")); // 转换为6位小数格式
    assertThat(t2.toString(1), is("02:56:15.0")); // 转换为1位小数格式
    assertThat(t2.toString(0), is("02:56:15")); // 转换为0位小数格式

    assertThat(t2.round(0), hasToString("02:56:15")); // 舍入到0位小数
    assertThat(t2.round(0).toString(0), is("02:56:15")); // 舍入后转换为0位小数格式
    assertThat(t2.round(0).toString(1), is("02:56:15.0")); // 舍入后转换为1位小数格式
    assertThat(t2.round(0).toString(2), is("02:56:15.00")); // 舍入后转换为2位小数格式

    assertThat(TimeString.fromMillisOfDay(53560123), // 从一天的毫秒数创建TimeString
        hasToString("14:52:40.123")); // 验证字符串表示（14小时52分40秒123毫秒）
  }

  private void checkTime(RexLiteral literal) { // checkTime辅助方法：验证时间字面量的各种属性
    assertThat(literal, hasToString("02:56:15")); // 验证字符串表示
    assertThat(literal.getValue() instanceof Calendar, is(true)); // 验证getValue()返回Calendar对象
    assertThat(literal.getValue2() instanceof Integer, is(true)); // 验证getValue2()返回Integer对象（毫秒数）
    assertThat(literal.getValue3() instanceof Integer, is(true)); // 验证getValue3()返回Integer对象（天数偏移）
    assertThat((Integer) literal.getValue2(), is(MOON_TIME)); // 验证毫秒数为MOON_TIME常量
    assertThat(literal.getValueAs(Calendar.class), notNullValue()); // 验证可以转换为Calendar
    assertThat(literal.getValueAs(TimeString.class), notNullValue()); // 验证可以转换为TimeString
  }

  /** Tests {@link RexBuilder#makeDateLiteral(DateString)}. */
  // testDateLiteral测试方法：测试RexBuilder.makeDateLiteral()方法
  // 该方法用于创建DATE类型的字面量，测试了从Calendar、Integer和DateString三种方式创建日期字面量
  @Test void testDateLiteral() { // 测试方法声明
    final RelDataTypeFactory typeFactory = // 创建类型工厂实例
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    RelDataType dateType = typeFactory.createSqlType(SqlTypeName.DATE); // 创建DATE类型
    final RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

    // Old way: provide a Calendar
    final Calendar calendar = Util.calendar(); // 创建日历对象（旧方式1：使用Calendar）
    calendar.set(1969, Calendar.JULY, 21); // 设置日期为1969年7月21日（阿波罗登月）
    calendar.set(Calendar.MILLISECOND, 0); // 设置毫秒为0
    checkDate(builder.makeLiteral(calendar, dateType)); // 使用Calendar创建日期字面量并验证

    // Old way #2: Provide in Integer
    checkDate(builder.makeLiteral(MOON_DAY, dateType)); // 使用Integer值创建日期字面量并验证（旧方式2）

    // The new way
    final DateString d = new DateString(1969, 7, 21); // 创建DateString对象（新方式）
    checkDate(builder.makeLiteral(d, dateType)); // 使用DateString创建日期字面量并验证
  }

  private void checkDate(RexLiteral literal) { // checkDate辅助方法：验证日期字面量的各种属性
    assertThat(literal, hasToString("1969-07-21")); // 验证字符串表示
    assertThat(literal.getValue() instanceof Calendar, is(true)); // 验证getValue()返回Calendar对象
    assertThat(literal.getValue2() instanceof Integer, is(true)); // 验证getValue2()返回Integer对象（天数偏移）
    assertThat(literal.getValue3() instanceof Integer, is(true)); // 验证getValue3()返回Integer对象（天数偏移）
    assertThat((Integer) literal.getValue2(), is(MOON_DAY)); // 验证天数为MOON_DAY常量
    assertThat(literal.getValueAs(Calendar.class), notNullValue()); // 验证可以转换为Calendar
    assertThat(literal.getValueAs(DateString.class), notNullValue()); // 验证可以转换为DateString
  }

  /** Test case for

     * <a href="https://issues.apache.org/jira/browse/CALCITE-2306">[CALCITE-2306]

     * AssertionError in {@link RexLiteral#getValue3} with null literal of type

     * DECIMAL</a>. */

    // testDecimalLiteral测试方法：测试DECIMAL类型的null字面量创建

    // 测试用例来自JIRA issue CALCITE-2306，验证null字面量的getValue3()方法不会抛出AssertionError

    @Test void testDecimalLiteral() { // 测试方法声明

      final RelDataTypeFactory typeFactory = // 创建类型工厂实例

  

          new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统

      final RelDataType type = typeFactory.createSqlType(SqlTypeName.DECIMAL); // 创建DECIMAL类型

      final RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

      final RexLiteral literal = builder.makeExactLiteral(null, type); // 创建null字面量

      assertThat(literal.getValue3(), nullValue()); // 验证getValue3()返回null

    }

  

    /** Test case for

     * <a href="https://issues.apache.org/jira/browse/CALCITE-3587">[CALCITE-3587]

     * RexBuilder may lose decimal fraction for creating literal with DECIMAL type</a>.

     */

    // testDecimal测试方法：测试使用makeLiteral()创建DECIMAL类型字面量时的错误处理

    // 测试用例来自JIRA issue CALCITE-3587，验证使用Double值创建DECIMAL字面量会抛出AssertionError

    @Test void testDecimal() { // 测试方法声明

      final RelDataTypeFactory typeFactory = // 创建类型工厂实例

          new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统

      final RelDataType type = typeFactory.createSqlType(SqlTypeName.DECIMAL, 4, 2); // 创建DECIMAL(4,2)类型

      final RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

      try { // 尝试使用Double值创建DECIMAL字面量

        builder.makeLiteral(12.3, type); // 使用Double值12.3创建字面量（应该失败）

        fail(); // 如果没有抛出异常，测试失败

      } catch (AssertionError e) { // 捕获AssertionError

        assertThat(e.getMessage(), // 验证错误消息

            is("java.lang.Double is not compatible with DECIMAL, try to use makeExactLiteral")); // 提示使用makeExactLiteral

      }

    }

  

    /** Tests {@link RexBuilder#makeExactLiteral(BigDecimal, RelDataType)}. */

    // testDecimalWithRoundingMode测试方法：测试makeExactLiteral()方法在不同舍入模式下的行为

    // 测试了默认舍入模式（DOWN）和HALF_UP舍入模式对DECIMAL值的影响

    @Test void testDecimalWithRoundingMode() { // 测试方法声明

      final RelDataTypeFactory typeFactory = // 创建类型工厂实例

          new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统（舍入模式为DOWN）

      final RelDataType type = typeFactory.createSqlType(SqlTypeName.DECIMAL, 4, 2); // 创建DECIMAL(4,2)类型

      final RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

      RexLiteral rexLiteral = builder.makeExactLiteral(new BigDecimal("13.556"), type); // 创建精确字面量，值为13.556

      assertThat(rexLiteral.getValue() instanceof BigDecimal, is(true)); // 验证值为BigDecimal类型

      assertThat(rexLiteral.getValue(), hasToString("13.55")); // 验证值为13.55（使用DOWN舍入模式，直接截断）

      final RelDataTypeFactory typeFactoryHalfUp = // 创建使用HALF_UP舍入模式的类型工厂

          new SqlTypeFactoryImpl(new RelDataTypeSystemImpl() { // 匿名子类，重写舍入模式

            @Override public RoundingMode roundingMode() { // 重写舍入模式方法

              return RoundingMode.HALF_UP; // 返回HALF_UP舍入模式（四舍五入）

            }

          });

      final RelDataType typeHalfUp = // 创建DECIMAL(4,2)类型

          typeFactoryHalfUp.createSqlType(SqlTypeName.DECIMAL, 4, 2); // 精度4，小数位2

      final RexBuilder builderHalfUp = new RexBuilder(typeFactoryHalfUp); // 创建RexBuilder实例

      RexLiteral rexLiteralHalfUp = // 创建精确字面量，值为13.556

          builderHalfUp.makeExactLiteral(new BigDecimal("13.556"), typeHalfUp); // 使用HALF_UP舍入模式

      assertThat(rexLiteralHalfUp.getValue() instanceof BigDecimal, is(true)); // 验证值为BigDecimal类型

      assertThat(rexLiteralHalfUp.getValue(), hasToString("13.56")); // 验证值为13.56（使用HALF_UP舍入模式，四舍五入）

    }

  

    @Test void testDecimalWithNegativeScaleRoundingHalfUp() { // testDecimalWithNegativeScaleRoundingHalfUp测试方法：测试负小数位数的DECIMAL类型在HALF_UP舍入模式下的行为

      final RelDataTypeFactory typeFactory = // 创建类型工厂实例

          new SqlTypeFactoryImpl(new RelDataTypeSystemImpl() { // 匿名子类，重写最小小数位数和舍入模式

            @Override public int getMinScale(SqlTypeName typeName) { // 重写最小小数位数方法

              switch (typeName) { // 根据类型名称

              case DECIMAL: // 如果是DECIMAL类型

                return -2; // 返回-2（表示小数位数为负，即舍入到百位）

              default: // 其他类型

                return super.getMinScale(typeName); // 调用父类方法

              }

            }

  

            @Override public RoundingMode roundingMode() { // 重写舍入模式方法

              return RoundingMode.HALF_UP; // 返回HALF_UP舍入模式（四舍五入）

            }

          });

      final RelDataType type = typeFactory.createSqlType(SqlTypeName.DECIMAL, 3, -2); // 创建DECIMAL(3,-2)类型（精度3，小数位-2）

      final RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

      RexLiteral rexLiteral = builder.makeLiteral(new BigDecimal("12355"), type); // 创建字面量，值为12355

      assertThat(rexLiteral.getValue() instanceof BigDecimal, is(true)); // 验证值为BigDecimal类型

      assertThat(rexLiteral.getValue(), hasToString("12400")); // 验证值为12400（舍入到百位，355四舍五入为400）

    }

  

    @Test void testDecimalWithNegativeScaleRoundingDown() { // testDecimalWithNegativeScaleRoundingDown测试方法：测试负小数位数的DECIMAL类型在DOWN舍入模式下的行为

      final RelDataTypeFactory typeFactory = // 创建类型工厂实例

          new SqlTypeFactoryImpl( // 使用自定义类型系统

              CustomTypeSystems.withMinScale(RelDataTypeSystem.DEFAULT, // 设置最小小数位数为-2

                  typeName -> -2)); // 对于所有类型，最小小数位数为-2

      final RelDataType type = typeFactory.createSqlType(SqlTypeName.DECIMAL, 3, -2); // 创建DECIMAL(3,-2)类型

      final RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

      RexLiteral rexLiteralHalfUp = builder.makeLiteral(new BigDecimal("12355"), type); // 创建字面量，值为12355

      assertThat(rexLiteralHalfUp.getValue() instanceof BigDecimal, is(true)); // 验证值为BigDecimal类型

      assertThat(rexLiteralHalfUp.getValue(), hasToString("12300")); // 验证值为12300（舍入到百位，355被截断为300）

    }

  /** Tests {@link DateString} year range. */
  // testDateStringYearError测试方法：测试DateString的年份范围验证
  // 验证超出范围的年份会抛出IllegalArgumentException
  @Test void testDateStringYearError() { // 测试方法声明
    try { // 尝试创建年份为11969的DateString（超出范围）
      final DateString dateString = new DateString(11969, 7, 21); // 年份11969超出允许范围
      fail("expected exception, got " + dateString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), containsString("Year out of range: [11969]")); // 验证错误消息
    }
    try { // 尝试从字符串创建年份为12345的DateString
      final DateString dateString = new DateString("12345-01-23"); // 年份12345超出允许范围
      fail("expected exception, got " + dateString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), // 验证错误消息
          containsString("Invalid date format: [12345-01-23]")); // 提示日期格式无效
    }
  }

  /** Tests {@link DateString} month range. */
  // testDateStringMonthError测试方法：测试DateString的月份范围验证
  // 验证超出范围的月份（1-12）会抛出IllegalArgumentException
  @Test void testDateStringMonthError() { // 测试方法声明
    try { // 尝试创建月份为27的DateString（超出范围）
      final DateString dateString = new DateString(1969, 27, 21); // 月份27超出范围（1-12）
      fail("expected exception, got " + dateString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), containsString("Month out of range: [27]")); // 验证错误消息
    }
    try { // 尝试从字符串创建月份为13的DateString
      final DateString dateString = new DateString("1234-13-02"); // 月份13超出范围（1-12）
      fail("expected exception, got " + dateString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), containsString("Month out of range: [13]")); // 验证错误消息
    }
  }

  /** Tests {@link DateString} day range. */
  // testDateStringDayError测试方法：测试DateString的日期范围验证
  // 验证超出范围的日期（1-31）会抛出IllegalArgumentException
  @Test void testDateStringDayError() { // 测试方法声明
    try { // 尝试创建日期为41的DateString（超出范围）
      final DateString dateString = new DateString(1969, 7, 41); // 日期41超出范围（1-31）
      fail("expected exception, got " + dateString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), containsString("Day out of range: [41]")); // 验证错误消息
    }
    try { // 尝试从字符串创建日期为32的DateString
      final DateString dateString = new DateString("1234-01-32"); // 日期32超出范围（1-31）
      fail("expected exception, got " + dateString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), containsString("Day out of range: [32]")); // 验证错误消息
    }
    // We don't worry about the number of days in a month. 30 is in range.
    final DateString dateString = new DateString("1234-02-30"); // 创建2月30日的DateString（在范围内）
    assertThat(dateString, notNullValue()); // 验证对象不为null（不检查每个月的实际天数）
  }

  /** Tests {@link TimeString} hour range. */
  // testTimeStringHourError测试方法：测试TimeString的小时范围验证
  // 验证超出范围的小时（0-23）会抛出IllegalArgumentException
  @Test void testTimeStringHourError() { // 测试方法声明
    try { // 尝试创建小时为111的TimeString（超出范围）
      final TimeString timeString = new TimeString(111, 34, 56); // 小时111超出范围（0-23）
      fail("expected exception, got " + timeString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), containsString("Hour out of range: [111]")); // 验证错误消息
    }
    try { // 尝试从字符串创建小时为24的TimeString
      final TimeString timeString = new TimeString("24:00:00"); // 小时24超出范围（0-23）
      fail("expected exception, got " + timeString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), containsString("Hour out of range: [24]")); // 验证错误消息
    }
    try { // 尝试从字符串创建小时为24的TimeString（简化格式）
      final TimeString timeString = new TimeString("24:00"); // 小时24超出范围（0-23）
      fail("expected exception, got " + timeString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), // 验证错误消息
          containsString("Invalid time format: [24:00]")); // 提示时间格式无效
    }
  }

  /** Tests {@link TimeString} minute range. */
  // testTimeStringMinuteError测试方法：测试TimeString的分钟范围验证
  // 验证超出范围的分钟（0-59）会抛出IllegalArgumentException
  @Test void testTimeStringMinuteError() { // 测试方法声明
    try { // 尝试创建分钟为334的TimeString（超出范围）
      final TimeString timeString = new TimeString(12, 334, 56); // 分钟334超出范围（0-59）
      fail("expected exception, got " + timeString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), containsString("Minute out of range: [334]")); // 验证错误消息
    }
    try { // 尝试从字符串创建分钟为60的TimeString
      final TimeString timeString = new TimeString("12:60:23"); // 分钟60超出范围（0-59）
      fail("expected exception, got " + timeString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), containsString("Minute out of range: [60]")); // 验证错误消息
    }
  }

  /** Tests {@link TimeString} second range. */
  // testTimeStringSecondError测试方法：测试TimeString的秒范围验证
  // 验证超出范围的秒（0-59）会抛出IllegalArgumentException
  @Test void testTimeStringSecondError() { // 测试方法声明
    try { // 尝试创建秒为567的TimeString（超出范围）
      final TimeString timeString = new TimeString(12, 34, 567); // 秒567超出范围（0-59）
      fail("expected exception, got " + timeString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), containsString("Second out of range: [567]")); // 验证错误消息
    }
    try { // 尝试创建秒为-4的TimeString（负数）
      final TimeString timeString = new TimeString(12, 34, -4); // 秒-4超出范围（不能为负数）
      fail("expected exception, got " + timeString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), containsString("Second out of range: [-4]")); // 验证错误消息
    }
    try { // 尝试从字符串创建秒为60的TimeString
      final TimeString timeString = new TimeString("12:34:60"); // 秒60超出范围（0-59）
      fail("expected exception, got " + timeString); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), containsString("Second out of range: [60]")); // 验证错误消息
    }
  }

  /**
   * Test string literal encoding.
   */
  // testStringLiteral测试方法：测试字符串字面量的编码和字符集处理
  // 测试了不同字符集（LATIN1、UTF8、GB2312）的字符串字面量创建
  @Test void testStringLiteral() { // 测试方法声明
    final RelDataTypeFactory typeFactory = // 创建类型工厂实例
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    final RelDataType varchar = // 创建VARCHAR类型
        typeFactory.createSqlType(SqlTypeName.VARCHAR); // 不指定精度，使用默认值
    final RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

    final NlsString latin1 = new NlsString("foobar", "LATIN1", SqlCollation.IMPLICIT); // 创建LATIN1字符集的NlsString
    final NlsString utf8 = new NlsString("foobar", "UTF8", SqlCollation.IMPLICIT); // 创建UTF8字符集的NlsString

    RexLiteral literal = builder.makePreciseStringLiteral("foobar"); // 创建精确字符串字面量（使用默认字符集）
    assertThat(literal, hasToString("'foobar'")); // 验证字符串表示
    literal = // 创建精确字符串字面量（使用ByteString和UTF8字符集）
        builder.makePreciseStringLiteral( // 调用makePreciseStringLiteral方法
            new ByteString(new byte[] { 'f', 'o', 'o', 'b', 'a', 'r'}), // 创建ByteString对象
            "UTF8", SqlCollation.IMPLICIT); // 指定UTF8字符集和隐式排序规则
    assertThat(literal, hasToString("_UTF8'foobar'")); // 验证字符串表示包含字符集前缀
    assertThat(literal.computeDigest(RexDigestIncludeType.ALWAYS), // 计算摘要（包含类型信息）
        is("_UTF8'foobar':CHAR(6) CHARACTER SET \"UTF-8\"")); // 验证摘要包含字符集信息
    literal = // 创建精确字符串字面量（使用中文字符和UTF8字符集）
        builder.makePreciseStringLiteral( // 调用makePreciseStringLiteral方法
            new ByteString("\u82f1\u56fd".getBytes(StandardCharsets.UTF_8)), // 创建ByteString对象（"英国"的UTF-8编码）
            "UTF8", SqlCollation.IMPLICIT); // 指定UTF8字符集和隐式排序规则
    assertThat(literal, hasToString("_UTF8'\u82f1\u56fd'")); // 验证字符串表示包含中文字符
    // Test again to check decode cache.
    literal = // 再次创建精确字符串字面量（测试解码缓存）
        builder.makePreciseStringLiteral( // 调用makePreciseStringLiteral方法
            new ByteString("\u82f1".getBytes(StandardCharsets.UTF_8)), // 创建ByteString对象（"英"的UTF-8编码）
            "UTF8", SqlCollation.IMPLICIT); // 指定UTF8字符集和隐式排序规则
    assertThat(literal, hasToString("_UTF8'\u82f1'")); // 验证字符串表示（使用缓存）
    try { // 尝试使用错误的字符集创建字符串字面量
      literal = // 尝试创建精确字符串字面量（使用GB2312字符集但数据是UTF-8编码）
          builder.makePreciseStringLiteral( // 调用makePreciseStringLiteral方法
              new ByteString("\u82f1\u56fd".getBytes(StandardCharsets.UTF_8)), // 创建ByteString对象（UTF-8编码）
              "GB2312", SqlCollation.IMPLICIT); // 指定GB2312字符集（与实际编码不匹配）
      fail("expected exception, got " + literal); // 如果没有抛出异常，测试失败
    } catch (RuntimeException e) { // 捕获RuntimeException
      assertThat(e.getMessage(), containsString("Failed to encode")); // 验证错误消息提示编码失败
    }
    literal = builder.makeLiteral(latin1, varchar); // 使用NlsString创建字面量
    assertThat(literal, hasToString("_LATIN1'foobar'")); // 验证字符串表示包含LATIN1字符集前缀
    literal = builder.makeLiteral(utf8, varchar); // 使用NlsString创建字面量
    assertThat(literal, hasToString("_UTF8'foobar'")); // 验证字符串表示包含UTF8字符集前缀
  }

  /** Tests {@link RexBuilder#makeExactLiteral(java.math.BigDecimal)}. */
  // testBigDecimalLiteral测试方法：测试makeExactLiteral()方法创建BigDecimal字面量
  // 测试了各种BigDecimal值，包括正数、负数、小数、大数等
  @Test void testBigDecimalLiteral() { // 测试方法声明
    final RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(new RelDataTypeSystemImpl() { // 创建类型工厂实例（自定义最大精度）
      @Override public int getMaxPrecision(SqlTypeName typeName) { // 重写最大精度方法
        return 38; // 返回38（DECIMAL类型的最大精度）
      }
    });
    final RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例
    checkBigDecimalLiteral(builder, "25"); // 验证整数字面量
    checkBigDecimalLiteral(builder, "9.9"); // 验证小数字面量
    checkBigDecimalLiteral(builder, "0"); // 验证零值
    checkBigDecimalLiteral(builder, "-75.5"); // 验证负数小数字面量
    checkBigDecimalLiteral(builder, "10000000"); // 验证大整数字面量
    checkBigDecimalLiteral(builder, "100000.111111111111111111"); // 验证高精度小数字面量
    checkBigDecimalLiteral(builder, "-100000.111111111111111111"); // 验证负数高精度小数字面量
    checkBigDecimalLiteral(builder, "73786976294838206464"); // 验证2^66的大数字面量
    checkBigDecimalLiteral(builder, "-73786976294838206464"); // 验证-2^66的大数字面量
  }

  @Test void testMakeIn() { // testMakeIn测试方法：测试RexBuilder.makeIn()方法创建IN表达式
    final RelDataTypeFactory typeFactory = // 创建类型工厂实例
            new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    final RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建RexBuilder实例
    final RelDataType floatType = typeFactory.createSqlType(SqlTypeName.REAL); // 创建REAL类型
    RexNode left = rexBuilder.makeInputRef(floatType, 0); // 创建输入引用（第0列）
    final RexNode literal1 = rexBuilder.makeLiteral(1.0f, floatType); // 创建字面量1.0
    final RexNode literal2 = rexBuilder.makeLiteral(2.0f, floatType); // 创建字面量2.0
    RexNode inCall = rexBuilder.makeIn(left, ImmutableList.of(literal1, literal2)); // 创建IN表达式
    assertThat(inCall.getKind(), is(SqlKind.SEARCH)); // 验证表达式类型为SEARCH
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6989">[CALCITE-6989]
   * Enhance RexBuilder#makeIn to create SEARCH for ARRAY literals</a>.
   */
  // testMakeInReturnsSearchForArrayLiterals测试方法：测试makeIn()方法对ARRAY类型字面量的处理
  // 测试用例来自JIRA issue CALCITE-6989，验证ARRAY字面量会创建SEARCH表达式
  @Test void testMakeInReturnsSearchForArrayLiterals() { // 测试方法声明
    RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂实例
    RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建RexBuilder实例
    RelDataType intType = typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建INTEGER类型
    RelDataType arrayIntType = typeFactory.createArrayType(intType, -1); // 创建ARRAY<INTEGER>类型
    RexNode column = rexBuilder.makeInputRef(arrayIntType, 0); // 创建输入引用（第0列）
    RexNode l1 = rexBuilder.makeLiteral(ImmutableList.of(100, 200), arrayIntType, false); // 创建ARRAY字面量[100, 200]
    RexNode l2 = rexBuilder.makeLiteral(ImmutableList.of(300, 400), arrayIntType, false); // 创建ARRAY字面量[300, 400]
    RexNode inCall = rexBuilder.makeIn(column, ImmutableList.of(l1, l2)); // 创建IN表达式
    assertThat( // 验证IN表达式的字符串表示
        inCall, hasToString("SEARCH($0, Sarg[" // 包含SEARCH操作和Sarg（搜索参数）
        + "[100:INTEGER, 200:INTEGER]:INTEGER NOT NULL ARRAY, " // 第一个ARRAY字面量
        + "[300:INTEGER, 400:INTEGER]:INTEGER NOT NULL ARRAY" // 第二个ARRAY字面量
        + "]:INTEGER NOT NULL ARRAY)")); // Sarg的类型
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6608">[CALCITE-6608]
   * RexBuilder#makeIn should create EQUALS instead of SEARCH for single point values</a>.
   */
  // testMakeInReturnsEqualsForSingleLiteral测试方法：测试makeIn()方法对单个字面量的优化
  // 测试用例来自JIRA issue CALCITE-6608，验证单个字面量会创建EQUALS表达式而不是SEARCH
  @Test void testMakeInReturnsEqualsForSingleLiteral() { // 测试方法声明
    RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂实例
    RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建RexBuilder实例
    RelDataType intType = typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建INTEGER类型
    RexNode column = rexBuilder.makeInputRef(intType, 0); // 创建输入引用（第0列）
    RexLiteral literal = rexBuilder.makeLiteral(100, intType); // 创建字面量100
    RexNode inCall = rexBuilder.makeIn(column, ImmutableList.of(literal)); // 创建IN表达式（只有一个字面量）
    assertThat(inCall, hasToString("=($0, 100)")); // 验证优化为EQUALS表达式
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6608">[CALCITE-6608]
   * RexBuilder#makeIn should create EQUALS instead of SEARCH for single point values</a>.
   */
  // testMakeInReturnsEqualsForDuplicateLiterals测试方法：测试makeIn()方法对重复字面量的优化
  // 测试用例来自JIRA issue CALCITE-6608，验证重复字面量会创建EQUALS表达式而不是SEARCH
  @Test void testMakeInReturnsEqualsForDuplicateLiterals() { // 测试方法声明
    RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂实例
    RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建RexBuilder实例
    RelDataType intType = typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建INTEGER类型
    RexNode column = rexBuilder.makeInputRef(intType, 0); // 创建输入引用（第0列）
    RexLiteral literal = rexBuilder.makeLiteral(100, intType); // 创建字面量100
    RexNode inCall = rexBuilder.makeIn(column, ImmutableList.of(literal, literal)); // 创建IN表达式（两个相同的字面量）
    assertThat(inCall, hasToString("=($0, 100)")); // 验证优化为EQUALS表达式（去重）
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6608">[CALCITE-6608]
   * RexBuilder#makeIn should create EQUALS instead of SEARCH for single point values</a>.
   */
  // testMakeInReturnsEqualsForSingleExpression测试方法：测试makeIn()方法对单个表达式的优化
  // 测试用例来自JIRA issue CALCITE-6608，验证单个表达式会创建EQUALS表达式而不是SEARCH
  @Test void testMakeInReturnsEqualsForSingleExpression() { // 测试方法声明
    RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂实例
    RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建RexBuilder实例
    RelDataType intType = typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建INTEGER类型
    RexNode column0 = rexBuilder.makeInputRef(intType, 0); // 创建输入引用（第0列）
    RexNode plusCall = // 创建加法表达式
        rexBuilder.makeCall(SqlStdOperatorTable.PLUS, // 使用PLUS操作符
            rexBuilder.makeInputRef(intType, 1), // 第1列
            rexBuilder.makeInputRef(intType, 2)); // 第2列
    RexNode inCall = rexBuilder.makeIn(column0, ImmutableList.of(plusCall)); // 创建IN表达式（只有一个表达式）
    assertThat(inCall, hasToString("=($0, +($1, $2))")); // 验证优化为EQUALS表达式
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6608">[CALCITE-6608]
   * RexBuilder#makeIn should create EQUALS instead of SEARCH for single point values</a>.
   */
  // testMakeInReturnsEqualsForDuplicateExpressions测试方法：测试makeIn()方法对重复表达式的优化
  // 测试用例来自JIRA issue CALCITE-6608，验证重复表达式会创建EQUALS表达式而不是SEARCH
  @Test void testMakeInReturnsEqualsForDuplicateExpressions() { // 测试方法声明
    RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂实例
    RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建RexBuilder实例
    RelDataType intType = typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建INTEGER类型
    RexNode column0 = rexBuilder.makeInputRef(intType, 0); // 创建输入引用（第0列）
    RexNode plusCall = // 创建加法表达式
        rexBuilder.makeCall(SqlStdOperatorTable.PLUS, // 使用PLUS操作符
            rexBuilder.makeInputRef(intType, 1), // 第1列
            rexBuilder.makeInputRef(intType, 2)); // 第2列
    RexNode inCall = rexBuilder.makeIn(column0, ImmutableList.of(plusCall, plusCall)); // 创建IN表达式（两个相同的表达式）
    assertThat(inCall, hasToString("=($0, +($1, $2))")); // 验证优化为EQUALS表达式（去重）
  }

  @Test void testMakeInReturnsOrForMultipleExpressions() { // testMakeInReturnsOrForMultipleExpressions测试方法：测试makeIn()方法对多个表达式的处理
    RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂实例
    RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建RexBuilder实例
    RelDataType intType = typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建INTEGER类型
    RexNode column0 = rexBuilder.makeInputRef(intType, 0); // 创建输入引用（第0列）
    RexNode plusCall = // 创建加法表达式
        rexBuilder.makeCall(SqlStdOperatorTable.PLUS, // 使用PLUS操作符
            rexBuilder.makeInputRef(intType, 1), // 第1列
            rexBuilder.makeInputRef(intType, 2)); // 第2列
    RexNode minusCall = // 创建减法表达式
        rexBuilder.makeCall(SqlStdOperatorTable.MINUS, // 使用MINUS操作符
            rexBuilder.makeInputRef(intType, 1), // 第1列
            rexBuilder.makeInputRef(intType, 2)); // 第2列
    RexNode inCall = rexBuilder.makeIn(column0, ImmutableList.of(plusCall, minusCall)); // 创建IN表达式（两个不同的表达式）
    assertThat(inCall, hasToString("OR(=($0, +($1, $2)), =($0, -($1, $2)))")); // 验证转换为OR表达式
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4555">[CALCITE-4555]
   * Invalid zero literal value is used for
   * TIMESTAMP WITH LOCAL TIME ZONE type in RexBuilder</a>. */
  // testMakeZeroLiteral测试方法：测试RexBuilder.makeZeroLiteral()方法创建零值字面量
  // 测试用例来自JIRA issue CALCITE-4555，验证各种类型的零值字面量创建是否正确
  @ParameterizedTest // 参数化测试注解
  @MethodSource("testData4testMakeZeroLiteral") // 指定参数提供者方法
  void testMakeZeroLiteral(RelDataType type, RexLiteral expected) { // 测试方法声明，接收类型和期望值
    final RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂实例
    final RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建RexBuilder实例
    assertThat(rexBuilder.makeZeroLiteral(type), is(equalTo(expected))); // 验证makeZeroLiteral()返回期望的零值字面量
  }

  private static Stream<Arguments> testData4testMakeZeroLiteral() { // testData4testMakeZeroLiteral参数提供者方法：为testMakeZeroLiteral测试方法提供测试数据
    final RelDataTypeFactory typeFactory = // 创建类型工厂实例
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    final RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建RexBuilder实例
    BiFunction<RelDataType, Function<RelDataType, Comparable>, Arguments> type2rexLiteral = // 定义类型到字面量的转换函数
        (relDataType, relDataTypeComparableFunction) -> // 接收类型和值生成函数
            of(relDataType, // 创建Arguments对象，包含类型
                rexBuilder.makeLiteral( // 创建字面量
                    relDataTypeComparableFunction.apply(relDataType), relDataType)); // 应用值生成函数
    return Stream.of( // 返回测试数据流
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.CHAR), // CHAR类型
            relDataType -> new NlsString(Spaces.of(relDataType.getPrecision()), null, null)), // 零值为空格字符串
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.VARCHAR), // VARCHAR类型
            relDataType -> new NlsString("", null, null)), // 零值为空字符串
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.BINARY), // BINARY类型
            relDataType -> new ByteString(new byte[relDataType.getPrecision()])), // 零值为全零字节数组
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.VARBINARY), // VARBINARY类型
            relDataType -> ByteString.EMPTY), // 零值为空字节字符串
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.TINYINT), // TINYINT类型
            relDataType -> BigDecimal.ZERO), // 零值为0
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.SMALLINT), // SMALLINT类型
            relDataType -> BigDecimal.ZERO), // 零值为0
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.INTEGER), // INTEGER类型
            relDataType -> BigDecimal.ZERO), // 零值为0
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.BIGINT), // BIGINT类型
            relDataType -> BigDecimal.ZERO), // 零值为0
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.DECIMAL), // DECIMAL类型
            relDataType -> BigDecimal.ZERO), // 零值为0
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.FLOAT), // FLOAT类型
            relDataType -> BigDecimal.ZERO), // 零值为0
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.REAL), // REAL类型
            relDataType -> BigDecimal.ZERO), // 零值为0
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.DOUBLE), // DOUBLE类型
            relDataType -> BigDecimal.ZERO), // 零值为0
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.BOOLEAN), // BOOLEAN类型
            relDataType -> false), // 零值为false
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.TIME), // TIME类型
            relDataType -> DateTimeUtils.ZERO_CALENDAR), // 零值为零时刻
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.DATE), // DATE类型
            relDataType -> DateTimeUtils.ZERO_CALENDAR), // 零值为零日期
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.TIMESTAMP), // TIMESTAMP类型
            relDataType -> DateTimeUtils.ZERO_CALENDAR), // 零值为零时间戳
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.TIME_WITH_LOCAL_TIME_ZONE), // TIME WITH LOCAL TIME ZONE类型
            relDataType -> new TimeString(0, 0, 0)), // 零值为00:00:00
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE), // TIMESTAMP WITH LOCAL TIME ZONE类型
            relDataType -> new TimestampString(1, 1, 1, 0, 0, 0)), // 零值为0001-01-01 00:00:00
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.TIME_TZ), // TIME_TZ类型
            relDataType -> new TimeWithTimeZoneString(0, 0, 0, "GMT+00:00")), // 零值为00:00:00 GMT+00:00
        type2rexLiteral.apply(typeFactory.createSqlType(SqlTypeName.TIMESTAMP_TZ), // TIMESTAMP_TZ类型
            relDataType -> new TimestampWithTimeZoneString(1, 1, 1, 0, 0, 0, "GMT+00:00"))); // 零值为0001-01-01 00:00:00 GMT+00:00
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6938">[CALCITE-6938]
   * Support zero value creation of nested data types</a>. */
  // testMakeZeroForNestedType测试方法：测试RexBuilder.makeZeroRexNode()方法创建嵌套类型的零值
  // 测试用例来自JIRA issue CALCITE-6938，验证ARRAY、MULTISET、MAP、ROW等嵌套类型的零值创建
  @ParameterizedTest // 参数化测试注解
  @MethodSource("testData4testMakeZeroForNestedType") // 指定参数提供者方法
  void testMakeZeroForNestedType(RelDataType type, RexNode expected) { // 测试方法声明，接收类型和期望值
    final RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂实例
    final RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建RexBuilder实例
    assertThat(rexBuilder.makeZeroRexNode(type), is(equalTo(expected))); // 验证makeZeroRexNode()返回期望的零值节点
  }

  @Test void testCreateCoalesce() { // testCreateCoalesce测试方法：测试COALESCE函数的创建
    RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂实例
    RexBuilder b = new RexBuilder(typeFactory); // 创建RexBuilder实例
    RelDataType varcharType = typeFactory.createSqlType(SqlTypeName.VARCHAR); // 创建VARCHAR类型

    RelDataType arrayType = new ArraySqlType(varcharType, false); // 创建ARRAY<VARCHAR>类型（不可为空）
    RexNode arrayZero = b.makeZeroRexNode(arrayType); // 创建ARRAY类型的零值节点

    RexNode array = // 创建ARRAY字面量
        b.makeCall(arrayType, SqlStdOperatorTable.ARRAY_VALUE_CONSTRUCTOR, // 使用ARRAY值构造器
        ImmutableList.of( // 包含一个元素
            b.makeLiteral("1", varcharType))); // 字符串字面量"1"

    RexNode coalesce1 = b.makeCall(SqlStdOperatorTable.COALESCE, array, arrayZero); // 创建COALESCE表达式
    assertThat( // 验证COALESCE表达式的字符串表示
        coalesce1, hasToString( // 包含ARRAY字面量和零值
        "COALESCE(ARRAY('1'), CAST(ARRAY()):VARCHAR NOT NULL ARRAY NOT NULL)")); // 零值被转换为相同类型

    RelDataType mapType = new MapSqlType(arrayType, arrayType, true); // 创建MAP<ARRAY, ARRAY>类型（可为空）
    RexNode mapZero = b.makeZeroRexNode(mapType); // 创建MAP类型的零值节点

    RexNode map = // 创建MAP字面量
        b.makeCall(new MapSqlType(arrayType, arrayType, true), // 使用MAP值构造器
        SqlStdOperatorTable.MAP_VALUE_CONSTRUCTOR, // MAP值构造器操作符
        ImmutableList.of(array, array)); // 包含键值对（两个ARRAY）

    RexNode coalesce2 = b.makeCall(SqlStdOperatorTable.COALESCE, map, mapZero); // 创建COALESCE表达式
    assertThat( // 验证COALESCE表达式的字符串表示
        coalesce2, hasToString( // 包含MAP字面量和零值
        "COALESCE(MAP(ARRAY('1'), ARRAY('1')), " // MAP字面量
        + "CAST(MAP()):(VARCHAR NOT NULL ARRAY NOT NULL, VARCHAR NOT NULL ARRAY NOT NULL) MAP)")); // 零值被转换为相同类型
  }

  private static Stream<Arguments> testData4testMakeZeroForNestedType() { // testData4testMakeZeroForNestedType参数提供者方法：为testMakeZeroForNestedType测试方法提供测试数据
    RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂实例
    RexBuilder b = new RexBuilder(typeFactory); // 创建RexBuilder实例

    RelDataType integerType = typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建INTEGER类型
    RelDataType varcharType = typeFactory.createSqlType(SqlTypeName.VARCHAR); // 创建VARCHAR类型

    // ARRAY<INTEGER>
    RelDataType arrayType = new ArraySqlType(integerType, false); // 创建ARRAY<INTEGER>类型
    RexNode expectedArray = // 期望的零值节点
        b.makeCast( // 转换为ARRAY类型
            arrayType, b.makeCall(arrayType, SqlStdOperatorTable.ARRAY_VALUE_CONSTRUCTOR, // 创建空ARRAY
            ImmutableList.of())); // 空列表

    // MULTISET<INTEGER>
    RelDataType multisetType = new MultisetSqlType(integerType, false); // 创建MULTISET<INTEGER>类型
    RexNode expectedMultiset = // 期望的零值节点
        b.makeCast( // 转换为MULTISET类型
            multisetType, b.makeCall(multisetType, SqlStdOperatorTable.MULTISET_VALUE, // 创建空MULTISET
            ImmutableList.of())); // 空列表

    // MAP<VARCHAR, INTEGER>
    RelDataType mapType = new MapSqlType(varcharType, integerType, false); // 创建MAP<VARCHAR, INTEGER>类型
    RexNode expectedMap = // 期望的零值节点
        b.makeCast( // 转换为MAP类型
            mapType, b.makeCall(mapType, SqlStdOperatorTable.MAP_VALUE_CONSTRUCTOR, // 创建空MAP
            ImmutableList.of())); // 空列表

    // ROW<INTEGER, VARCHAR>
    RelDataType rowType = // 创建ROW类型
        typeFactory.createStructType( // 创建结构化类型
            ImmutableList.of( // 包含两个字段
            new RelDataTypeFieldImpl("integer", 0, integerType), // 第一个字段：integer
            new RelDataTypeFieldImpl("varchar", 1, varcharType))); // 第二个字段：varchar
    RexNode expectedRow = // 期望的零值节点
        b.makeCall(rowType, SqlStdOperatorTable.ROW, // 创建ROW表达式
            ImmutableList.of(b.makeZeroLiteral(integerType), // 第一个元素的零值
                b.makeZeroLiteral(varcharType))); // 第二个元素的零值

    // ARRAY<ARRAY<INTEGER>>
    RelDataType arrayArrayType = new ArraySqlType(arrayType, false); // 创建ARRAY<ARRAY<INTEGER>>类型
    RexNode expectedArrayArray = // 期望的零值节点
        b.makeCast( // 转换为ARRAY<ARRAY>类型
            arrayArrayType, b.makeCall(arrayArrayType, SqlStdOperatorTable.ARRAY_VALUE_CONSTRUCTOR, // 创建空ARRAY<ARRAY>
            ImmutableList.of())); // 空列表

    // ARRAY<MAP<VARCHAR, INTEGER>>
    RelDataType arrayMapType = new ArraySqlType(mapType, false); // 创建ARRAY<MAP<VARCHAR, INTEGER>>类型
    RexNode expectedArrayMap = // 期望的零值节点
        b.makeCast( // 转换为ARRAY<MAP>类型
            arrayMapType, b.makeCall(arrayMapType, SqlStdOperatorTable.ARRAY_VALUE_CONSTRUCTOR, // 创建空ARRAY<MAP>
            ImmutableList.of())); // 空列表

    // MAP<MAP<INTEGER, INTEGER>
    RelDataType mapMapType = new MapSqlType(mapType, integerType, false); // 创建MAP<MAP<INTEGER, INTEGER>, INTEGER>类型
    RexNode expectedMapMap = // 期望的零值节点
        b.makeCast( // 转换为MAP<MAP>类型
            mapMapType, b.makeCall(mapMapType, SqlStdOperatorTable.MAP_VALUE_CONSTRUCTOR, // 创建空MAP<MAP>
            ImmutableList.of())); // 空列表

    // MAP<ARRAY<INTEGER>, INTEGER>
    RelDataType mapArrayType = new MapSqlType(arrayType, integerType, false); // 创建MAP<ARRAY<INTEGER>, INTEGER>类型
    RexNode expectedMapArray = // 期望的零值节点
        b.makeCast( // 转换为MAP<ARRAY>类型
            mapArrayType, b.makeCall(mapArrayType, SqlStdOperatorTable.MAP_VALUE_CONSTRUCTOR, // 创建空MAP<ARRAY>
            ImmutableList.of())); // 空列表

    // ROW<ARRAY<INTEGER>, VARCHAR>
    RelDataType rowArrayType = // 创建ROW<ARRAY<INTEGER>, VARCHAR>类型
        typeFactory.createStructType( // 创建结构化类型
            ImmutableList.of( // 包含两个字段
                new RelDataTypeFieldImpl("array", 0, arrayType), // 第一个字段：array
                new RelDataTypeFieldImpl("varchar", 1, varcharType))); // 第二个字段：varchar
    RexNode expectedRowArray = // 期望的零值节点
        b.makeCall(rowArrayType, SqlStdOperatorTable.ROW, // 创建ROW表达式
            ImmutableList.of(expectedArray, // 第一个元素的零值（ARRAY的零值）
                b.makeZeroLiteral(varcharType))); // 第二个元素的零值（VARCHAR的零值）

    return Stream.of( // 返回测试数据流
        Arguments.of(arrayType, expectedArray), // ARRAY<INTEGER>测试用例
        Arguments.of(multisetType, expectedMultiset), // MULTISET<INTEGER>测试用例
        Arguments.of(mapType, expectedMap), // MAP<VARCHAR, INTEGER>测试用例
        Arguments.of(rowType, expectedRow), // ROW<INTEGER, VARCHAR>测试用例
        Arguments.of(arrayArrayType, expectedArrayArray), // ARRAY<ARRAY<INTEGER>>测试用例
        Arguments.of(arrayMapType, expectedArrayMap), // ARRAY<MAP<VARCHAR, INTEGER>>测试用例
        Arguments.of(mapMapType, expectedMapMap), // MAP<MAP<INTEGER, INTEGER>, INTEGER>测试用例
        Arguments.of(mapArrayType, expectedMapArray), // MAP<ARRAY<INTEGER>, INTEGER>测试用例
        Arguments.of(rowArrayType, expectedRowArray)); // ROW<ARRAY<INTEGER>, VARCHAR>测试用例
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4632">[CALCITE-4632]
   * Find the least restrictive datatype for SARG</a>. */
  // testLeastRestrictiveTypeForSargMakeIn测试方法：测试makeIn()方法中SARG（搜索参数）的最小限制类型
  // 测试用例来自JIRA issue CALCITE-4632，验证SARG能够找到容纳所有字面量的最小限制类型
  @Test void testLeastRestrictiveTypeForSargMakeIn() { // 测试方法声明
    final RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂实例
    final RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建RexBuilder实例
    final RelDataType decimalType = typeFactory.createSqlType(SqlTypeName.DECIMAL); // 创建DECIMAL类型
    RexNode left = rexBuilder.makeInputRef(decimalType, 0); // 创建输入引用（第0列）
    final RexNode literal1 = rexBuilder.makeExactLiteral(new BigDecimal("1.0")); // 创建字面量1.0
    final RexNode literal2 = rexBuilder.makeExactLiteral(new BigDecimal("20000.0")); // 创建字面量20000.0

    RexNode inCall = rexBuilder.makeIn(left, ImmutableList.of(literal1, literal2)); // 创建IN表达式
    assertThat(inCall.getKind(), is(SqlKind.SEARCH)); // 验证表达式类型为SEARCH

    final RexNode sarg = ((RexCall) inCall).operands.get(1); // 获取SARG节点（第二个操作数）
    RelDataType expected = typeFactory.createSqlType(SqlTypeName.DECIMAL, 6, 1); // 期望类型为DECIMAL(6,1)（容纳1.0和20000.0）
    assertThat(expected, is(sarg.getType())); // 验证SARG的类型为DECIMAL(6,1)
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4632">[CALCITE-4632]
   * Find the least restrictive datatype for SARG</a>. */
  // testLeastRestrictiveTypeForSargMakeBetween测试方法：测试makeBetween()方法中SARG（搜索参数）的最小限制类型
  // 测试用例来自JIRA issue CALCITE-4632，验证SARG能够找到容纳所有字面量的最小限制类型
  @Test void testLeastRestrictiveTypeForSargMakeBetween() { // 测试方法声明
    final RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂实例
    final RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建RexBuilder实例
    final RelDataType decimalType = typeFactory.createSqlType(SqlTypeName.DECIMAL); // 创建DECIMAL类型
    RexNode left = rexBuilder.makeInputRef(decimalType, 0); // 创建输入引用（第0列）
    final RexNode literal1 = rexBuilder.makeExactLiteral(new BigDecimal("1.0")); // 创建字面量1.0（下界）
    final RexNode literal2 = rexBuilder.makeExactLiteral(new BigDecimal("20000.0")); // 创建字面量20000.0（上界）

    RexNode betweenCall = rexBuilder.makeBetween(left, literal1, literal2); // 创建BETWEEN表达式
    assertThat(betweenCall.getKind(), is(SqlKind.SEARCH)); // 验证表达式类型为SEARCH

    final RexNode sarg = ((RexCall) betweenCall).operands.get(1); // 获取SARG节点（第二个操作数）
    RelDataType expected = typeFactory.createSqlType(SqlTypeName.DECIMAL, 6, 1); // 期望类型为DECIMAL(6,1)（容纳1.0和20000.0）
    assertThat(expected, is(sarg.getType())); // 验证SARG的类型为DECIMAL(6,1)
  }

  /** Tests {@link RexCopier#visitOver(RexOver)}. */
  // testCopyOver测试方法：测试RexCopier.copy()方法对RexOver（窗口函数）节点的复制
  // 验证在复制过程中，VARCHAR类型的精度会从65536转换为256（MySqlTypeFactoryImpl的自定义行为）
  @Test void testCopyOver() { // 测试方法声明
    final RelDataTypeFactory sourceTypeFactory = // 创建源类型工厂实例
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    RelDataType type = sourceTypeFactory.createSqlType(SqlTypeName.VARCHAR, 65536); // 创建VARCHAR(65536)类型

    final RelDataTypeFactory targetTypeFactory = // 创建目标类型工厂实例（使用MySqlTypeFactoryImpl）
        new MySqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用MySqlTypeFactoryImpl（将VARCHAR精度强制为256）
    final RexBuilder builder = new RexBuilder(targetTypeFactory); // 创建RexBuilder实例

    final RexOver node = // 创建RexOver节点（窗口函数）
        (RexOver) builder.makeOver(type, SqlStdOperatorTable.COUNT, // 创建COUNT窗口函数
            ImmutableList.of(builder.makeInputRef(type, 0)), // 聚合参数（第0列）
            ImmutableList.of(builder.makeInputRef(type, 1)), // 分区键（第1列）
            ImmutableList.of( // 排序键
                new RexFieldCollation(builder.makeInputRef(type, 2), // 排序字段（第2列）
                    ImmutableSet.of())), // 排序规则（空集合）
            RexWindowBounds.UNBOUNDED_PRECEDING, // 窗口下界：无界前驱
            RexWindowBounds.CURRENT_ROW, // 窗口上界：当前行
            true, true, false, false, false); // 其他参数：distinct=true, ignoreNulls=true等
    final RexNode copy = builder.copy(node); // 复制节点
    assertThat(copy, instanceOf(RexOver.class)); // 验证复制后的节点类型为RexOver

    RexOver result = (RexOver) copy; // 强制转换为RexOver
    assertThat(result.getType().getSqlTypeName(), is(SqlTypeName.VARCHAR)); // 验证类型名称为VARCHAR
    assertThat(result.getType().getPrecision(), is(PRECISION)); // 验证精度为256（MySqlTypeFactoryImpl的自定义行为）
    assertThat(result.getWindow(), is(node.getWindow())); // 验证窗口属性保持不变
    assertThat(result.getAggOperator(), is(node.getAggOperator())); // 验证聚合操作符保持不变
    assertThat(result.getAggOperator(), is(node.getAggOperator())); // 验证聚合操作符保持不变（重复检查）
    assertThat(result.isDistinct(), is(node.isDistinct())); // 验证distinct属性保持不变
    assertThat(result.ignoreNulls(), is(node.ignoreNulls())); // 验证ignoreNulls属性保持不变
    for (int i = 0; i < node.getOperands().size(); i++) { // 遍历所有操作数
      assertThat(result.getOperands().get(i).getType().getSqlTypeName(), // 验证操作数类型名称
          is(node.getOperands().get(i).getType().getSqlTypeName())); // 与原节点相同
      assertThat(result.getOperands().get(i).getType().getPrecision(), // 验证操作数类型精度
          is(PRECISION)); // 为256（MySqlTypeFactoryImpl的自定义行为）
    }
  }

  /** Tests {@link RexCopier#visitCorrelVariable(RexCorrelVariable)}. */
  // testCopyCorrelVariable测试方法：测试RexCopier.copy()方法对RexCorrelVariable（关联变量）节点的复制
  // 验证在复制过程中，VARCHAR类型的精度会从65536转换为256（MySqlTypeFactoryImpl的自定义行为）
  @Test void testCopyCorrelVariable() { // 测试方法声明
    final RelDataTypeFactory sourceTypeFactory = // 创建源类型工厂实例
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    RelDataType type = sourceTypeFactory.createSqlType(SqlTypeName.VARCHAR, 65536); // 创建VARCHAR(65536)类型

    final RelDataTypeFactory targetTypeFactory = // 创建目标类型工厂实例（使用MySqlTypeFactoryImpl）
        new MySqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用MySqlTypeFactoryImpl
    final RexBuilder builder = new RexBuilder(targetTypeFactory); // 创建RexBuilder实例

    final RexCorrelVariable node = // 创建RexCorrelVariable节点（关联变量）
        (RexCorrelVariable) builder.makeCorrel(type, new CorrelationId(0)); // 关联ID为0
    final RexNode copy = builder.copy(node); // 复制节点
    assertThat(copy, instanceOf(RexCorrelVariable.class)); // 验证复制后的节点类型为RexCorrelVariable

    final RexCorrelVariable result = (RexCorrelVariable) copy; // 强制转换为RexCorrelVariable
    assertThat(result.id, is(node.id)); // 验证关联ID保持不变
    assertThat(result.getType().getSqlTypeName(), is(SqlTypeName.VARCHAR)); // 验证类型名称为VARCHAR
    assertThat(result.getType().getPrecision(), is(PRECISION)); // 验证精度为256（MySqlTypeFactoryImpl的自定义行为）
  }

  /** Tests {@link RexCopier#visitLocalRef(RexLocalRef)}. */
  // testCopyLocalRef测试方法：测试RexCopier.copy()方法对RexLocalRef（局部引用）节点的复制
  // 验证在复制过程中，VARCHAR类型的精度会从65536转换为256（MySqlTypeFactoryImpl的自定义行为）
  @Test void testCopyLocalRef() { // 测试方法声明
    final RelDataTypeFactory sourceTypeFactory = // 创建源类型工厂实例
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    RelDataType type = sourceTypeFactory.createSqlType(SqlTypeName.VARCHAR, 65536); // 创建VARCHAR(65536)类型

    final RelDataTypeFactory targetTypeFactory = // 创建目标类型工厂实例（使用MySqlTypeFactoryImpl）
        new MySqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用MySqlTypeFactoryImpl
    final RexBuilder builder = new RexBuilder(targetTypeFactory); // 创建RexBuilder实例

    final RexLocalRef node = new RexLocalRef(0, type); // 创建RexLocalRef节点（索引为0）
    final RexNode copy = builder.copy(node); // 复制节点
    assertThat(copy, instanceOf(RexLocalRef.class)); // 验证复制后的节点类型为RexLocalRef

    final RexLocalRef result = (RexLocalRef) copy; // 强制转换为RexLocalRef
    assertThat(result.getIndex(), is(node.getIndex())); // 验证索引保持不变
    assertThat(result.getType().getSqlTypeName(), is(SqlTypeName.VARCHAR)); // 验证类型名称为VARCHAR
    assertThat(result.getType().getPrecision(), is(PRECISION)); // 验证精度为256（MySqlTypeFactoryImpl的自定义行为）
  }

  /** Tests {@link RexCopier#visitDynamicParam(RexDynamicParam)}. */
  // testCopyDynamicParam测试方法：测试RexCopier.copy()方法对RexDynamicParam（动态参数）节点的复制
  // 验证在复制过程中，VARCHAR类型的精度会从65536转换为256（MySqlTypeFactoryImpl的自定义行为）
  @Test void testCopyDynamicParam() { // 测试方法声明
    final RelDataTypeFactory sourceTypeFactory = // 创建源类型工厂实例
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    RelDataType type = sourceTypeFactory.createSqlType(SqlTypeName.VARCHAR, 65536); // 创建VARCHAR(65536)类型

    final RelDataTypeFactory targetTypeFactory = // 创建目标类型工厂实例（使用MySqlTypeFactoryImpl）
        new MySqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用MySqlTypeFactoryImpl
    final RexBuilder builder = new RexBuilder(targetTypeFactory); // 创建RexBuilder实例

    final RexDynamicParam node = builder.makeDynamicParam(type, 0); // 创建RexDynamicParam节点（索引为0）
    final RexNode copy = builder.copy(node); // 复制节点
    assertThat(copy, instanceOf(RexDynamicParam.class)); // 验证复制后的节点类型为RexDynamicParam

    final RexDynamicParam result = (RexDynamicParam) copy; // 强制转换为RexDynamicParam
    assertThat(result.getIndex(), is(node.getIndex())); // 验证索引保持不变
    assertThat(result.getType().getSqlTypeName(), is(SqlTypeName.VARCHAR)); // 验证类型名称为VARCHAR
    assertThat(result.getType().getPrecision(), is(PRECISION)); // 验证精度为256（MySqlTypeFactoryImpl的自定义行为）
  }

  /** Tests {@link RexCopier#visitRangeRef(RexRangeRef)}. */
  // testCopyRangeRef测试方法：测试RexCopier.copy()方法对RexRangeRef（范围引用）节点的复制
  // 验证在复制过程中，VARCHAR类型的精度会从65536转换为256（MySqlTypeFactoryImpl的自定义行为）
  @Test void testCopyRangeRef() { // 测试方法声明
    final RelDataTypeFactory sourceTypeFactory = // 创建源类型工厂实例
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    RelDataType type = sourceTypeFactory.createSqlType(SqlTypeName.VARCHAR, 65536); // 创建VARCHAR(65536)类型

    final RelDataTypeFactory targetTypeFactory = // 创建目标类型工厂实例（使用MySqlTypeFactoryImpl）
        new MySqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用MySqlTypeFactoryImpl
    final RexBuilder builder = new RexBuilder(targetTypeFactory); // 创建RexBuilder实例

    final RexRangeRef node = builder.makeRangeReference(type, 1, true); // 创建RexRangeRef节点（偏移量为1）
    final RexNode copy = builder.copy(node); // 复制节点
    assertThat(copy, instanceOf(RexRangeRef.class)); // 验证复制后的节点类型为RexRangeRef

    final RexRangeRef result = (RexRangeRef) copy; // 强制转换为RexRangeRef
    assertThat(result.getOffset(), is(node.getOffset())); // 验证偏移量保持不变
    assertThat(result.getType().getSqlTypeName(), is(SqlTypeName.VARCHAR)); // 验证类型名称为VARCHAR
    assertThat(result.getType().getPrecision(), is(PRECISION)); // 验证精度为256（MySqlTypeFactoryImpl的自定义行为）
  }

  private void checkBigDecimalLiteral(RexBuilder builder, String val) { // checkBigDecimalLiteral辅助方法：验证BigDecimal字面量的值是否正确
    final RexLiteral literal = builder.makeExactLiteral(new BigDecimal(val)); // 创建精确字面量
    assertThat("builder.makeExactLiteral(new BigDecimal(" + val // 验证字面量值
            + ")).getValueAs(BigDecimal.class).toString()", // 获取BigDecimal值并转换为字符串
        literal.getValueAs(BigDecimal.class), hasToString(val)); // 验证字符串表示与输入值相同
  }

  @Test void testValidateRexFieldAccess() { // testValidateRexFieldAccess测试方法：测试RexFieldAccess的验证逻辑
    final RelDataTypeFactory typeFactory = // 创建类型工厂实例
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    final RexBuilder builder = new RexBuilder(typeFactory); // 创建RexBuilder实例

    RelDataType intType = typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建INTEGER类型
    RelDataType longType = typeFactory.createSqlType(SqlTypeName.BIGINT); // 创建BIGINT类型

    RelDataType structType = // 创建结构化类型（包含两个字段）
        typeFactory.createStructType(Arrays.asList(intType, longType), // 字段类型列表
            Arrays.asList("x", "y")); // 字段名称列表
    RexInputRef inputRef = builder.makeInputRef(structType, 0); // 创建输入引用（第0列，类型为structType）

    // construct RexFieldAccess fails because of negative index
    IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class, () -> { // 验证负索引会抛出异常
      RelDataTypeField field = new RelDataTypeFieldImpl("z", -1, intType); // 创建字段（索引为-1）
      new RexFieldAccess(inputRef, field); // 尝试创建RexFieldAccess（应该失败）
    });
    assertThat(e1.getMessage(), // 验证异常消息
        is("Field #-1: z INTEGER does not exist for expression $0")); // 提示字段不存在

    // construct RexFieldAccess fails because of too large index
    IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> { // 验证过大索引会抛出异常
      RelDataTypeField field = new RelDataTypeFieldImpl("z", 2, intType); // 创建字段（索引为2，超出范围）
      new RexFieldAccess(inputRef, field); // 尝试创建RexFieldAccess（应该失败）
    });
    assertThat(e2.getMessage(), // 验证异常消息
        is("Field #2: z INTEGER does not exist for expression $0")); // 提示字段不存在

    // construct RexFieldAccess fails because of incorrect type
    IllegalArgumentException e3 = assertThrows(IllegalArgumentException.class, () -> { // 验证类型不匹配会抛出异常
      RelDataTypeField field = new RelDataTypeFieldImpl("z", 0, longType); // 创建字段（索引为0，类型为BIGINT）
      new RexFieldAccess(inputRef, field); // 尝试创建RexFieldAccess（应该失败，因为实际类型是INTEGER）
    });
    assertThat(e3.getMessage(), // 验证异常消息
        is("Field #0: z BIGINT does not exist for expression $0")); // 提示字段不存在

    // construct RexFieldAccess successfully
    RelDataTypeField field = new RelDataTypeFieldImpl("x", 0, intType); // 创建字段（索引为0，类型为INTEGER，与实际类型匹配）
    RexFieldAccess fieldAccess = new RexFieldAccess(inputRef, field); // 创建RexFieldAccess（应该成功）
    RexChecker checker = new RexChecker(structType, () -> null, Litmus.THROW); // 创建RexChecker验证器
    assertThat(fieldAccess.accept(checker), is(true)); // 验证RexFieldAccess通过检查
  }

  /** Emulate a user defined type. */
  // UDT内部类：模拟用户自定义类型（User Defined Type）
  // 继承自RelDataTypeImpl，用于测试自定义类型的字面量摘要（digest）生成
  private static class UDT extends RelDataTypeImpl { // 内部类声明，继承RelDataTypeImpl
    UDT() { // 构造方法
      this.digest = "(udt)NOT NULL"; // 设置类型摘要（注意：NOT NULL前缺少空格）
    }

    @Override protected void generateTypeString(StringBuilder sb, boolean withDetail) { // 重写类型字符串生成方法
      sb.append("udt"); // 返回类型字符串为"udt"
    }
  }

  @Test void testUDTLiteralDigest() { // testUDTLiteralDigest测试方法：测试自定义类型字面量的摘要生成
    RexLiteral literal = new RexLiteral(new BigDecimal(0L), new UDT(), SqlTypeName.BIGINT); // 创建自定义类型的字面量

    // when the space before "NOT NULL" is missing, the digest is not correct
    // and the suffix should not be removed.
    assertThat(literal.digest, is("0L:(udt)NOT NULL")); // 验证摘要包含"(udt)NOT NULL"（注意：NOT NULL前缺少空格）
  }


  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5489">[CALCITE-5489]
   * RexCall to TIMESTAMP_DIFF function fails to convert a TIMESTAMP literal to
   * a org.apache.calcite.avatica.util.TimeUnit</a>. */
  // testTimestampDiffCall测试方法：测试TIMESTAMP_DIFF函数的调用
  // 测试用例来自JIRA issue CALCITE-5489，验证TIMESTAMP_DIFF函数能够正确处理TIMESTAMP字面量和TimeUnit标志
  @Test void testTimestampDiffCall() { // 测试方法声明
    final RexImplicationCheckerFixtures.Fixture f = // 创建测试夹具
        new RexImplicationCheckerFixtures.Fixture(); // 使用RexImplicationCheckerFixtures提供的测试夹具
    final TimestampString ts = // 创建时间戳字符串
        TimestampString.fromCalendarFields(Util.calendar()); // 从当前日历创建
    final RexNode literal = f.timestampLiteral(ts); // 创建时间戳字面量
    final RexLiteral flag = f.rexBuilder.makeFlag(TimeUnit.QUARTER); // 创建TimeUnit标志（季度）
    assertThat( // 验证DATEDIFF函数调用成功
        f.rexBuilder.makeCall(SqlLibraryOperators.DATEDIFF, // 使用DATEDIFF操作符
            flag, literal, literal), // 参数：标志、时间戳字面量、时间戳字面量
        notNullValue()); // 验证返回值不为null
    assertThat( // 验证TIMESTAMP_DIFF函数调用成功
        f.rexBuilder.makeCall(SqlStdOperatorTable.TIMESTAMP_DIFF, // 使用TIMESTAMP_DIFF操作符
            flag, literal, literal), // 参数：标志、时间戳字面量、时间戳字面量
        notNullValue()); // 验证返回值不为null
    assertThat( // 验证TIMESTAMP_DIFF3函数调用成功
        f.rexBuilder.makeCall(SqlLibraryOperators.TIMESTAMP_DIFF3, // 使用TIMESTAMP_DIFF3操作符
            literal, literal, flag), // 参数：时间戳字面量、时间戳字面量、标志（参数顺序不同）
        notNullValue()); // 验证返回值不为null
    assertThat( // 验证TIME_DIFF函数调用成功
        f.rexBuilder.makeCall(SqlLibraryOperators.TIME_DIFF, // 使用TIME_DIFF操作符
            literal, literal, flag), // 参数：时间戳字面量、时间戳字面量、标志
        notNullValue()); // 验证返回值不为null
  }
}
