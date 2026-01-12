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
package org.apache.calcite.adapter.geode.rel; // 声明包名，此类属于org.apache.calcite.adapter.geode.rel包

import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接类，用于访问Calcite的JDBC连接
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，用于表示Calcite的模式（schema）对象
import org.apache.calcite.test.CalciteAssert; // 导入CalciteAssert类，用于测试SQL查询断言

import org.apache.geode.cache.Cache; // 导入Geode缓存接口，提供对Geode缓存的访问
import org.apache.geode.cache.Region; // 导入Geode Region接口，表示Geode中的数据区域

import com.google.common.collect.ImmutableList; // 导入Guava的不可变列表类，用于创建不可变的列表
import com.google.common.collect.ImmutableMap; // 导入Guava的不可变Map类，用于创建不可变的映射

import org.junit.jupiter.api.BeforeAll; // 导入JUnit5的BeforeAll注解，标记在所有测试方法执行前运行的方法
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，标记测试方法

import java.sql.Connection; // 导入JDBC连接接口，用于数据库连接
import java.sql.Date; // 导入SQL日期类，表示SQL中的DATE类型
import java.sql.DriverManager; // 导入驱动管理器类，用于获取数据库连接
import java.sql.SQLException; // 导入SQL异常类，处理SQL相关异常
import java.sql.Time; // 导入SQL时间类，表示SQL中的TIME类型
import java.sql.Timestamp; // 导入SQL时间戳类，表示SQL中的TIMESTAMP类型
import java.util.Collections; // 导入集合工具类，提供各种集合操作方法
import java.util.List; // 导入List接口，表示有序列表
import java.util.Map; // 导入Map接口，表示键值对映射

/** Test with different types of data, like BOOLEAN, TIME, TIMESTAMP. */ // 类文档注释：测试不同数据类型，如布尔值、时间、时间戳等
class GeodeAllDataTypesTest extends AbstractGeodeTest { // 定义测试类GeodeAllDataTypesTest，继承自AbstractGeodeTest基类，用于测试Geode适配器支持的所有数据类型

  @BeforeAll // JUnit5注解，标记此方法在所有测试方法执行前运行一次，用于初始化测试环境
  public static void setUp() { // 静态设置方法，在所有测试运行前执行，初始化Geode缓存和测试数据
    final Cache cache = POLICY.cache(); // 获取Geode缓存实例，POLICY是从父类继承的缓存策略对象
    final Region<?, ?> region = // 创建Geode Region对象，Region是Geode中数据存储的基本单元
        cache.<String, Object>createRegionFactory() // 创建区域工厂，指定键类型为String，值类型为Object
            .create("allDataTypesRegion"); // 创建名为"allDataTypesRegion"的区域，用于存储所有数据类型的测试数据

    final List<Map<String, Object>> mapList = createMapList(); // 调用createMapList方法创建测试数据列表，每个Map代表一条记录

    new JsonLoader(region).loadMapList(mapList); // 使用JsonLoader将测试数据加载到Geode Region中，JsonLoader负责将Map列表转换为JSON并存入Region
  }

  private static List<Map<String, Object>> createMapList() { // 私有静态方法，创建测试数据列表，返回包含3条记录的不可变列表
    return ImmutableList.of( // 使用Guava的ImmutableList创建不可变列表，包含3个Map对象
        ImmutableMap.<String, Object>builder() // 创建第一个Map构建器，键为String类型，值为Object类型
            .put("booleanValue", true) // 添加布尔值字段，值为true
            .put("dateValue", Date.valueOf("2018-02-03")) // 添加日期字段，值为2018年2月3日
            .put("timeValue", Time.valueOf("02:22:23")) // 添加时间字段，值为2点22分23秒
            .put("timestampValue", Timestamp.valueOf("2018-02-03 02:22:33")) // 添加时间戳字段，值为2018年2月3日2点22分33秒
            .put("stringValue", "abc") // 添加字符串字段，值为"abc"
            .put("floatValue", 1.5678) // 添加浮点数字段，值为1.5678
            .build(), // 构建第一个不可变Map，完成第一条记录
        ImmutableMap.<String, Object>builder() // 创建第二个Map构建器
            .put("booleanValue", false) // 添加布尔值字段，值为false
            .put("dateValue", Date.valueOf("2018-02-04")) // 添加日期字段，值为2018年2月4日
            .put("timeValue", Time.valueOf("03:22:23")) // 添加时间字段，值为3点22分23秒
            .put("timestampValue", Timestamp.valueOf("2018-02-04 04:22:33")) // 添加时间戳字段，值为2018年2月4日4点22分33秒
            .put("stringValue", "def") // 添加字符串字段，值为"def"
            .put("floatValue", 3.5678) // 添加浮点数字段，值为3.5678
            .build(), // 构建第二个不可变Map，完成第二条记录
        ImmutableMap.<String, Object>builder() // 创建第三个Map构建器
            .put("booleanValue", true) // 添加布尔值字段，值为true
            .put("dateValue", Date.valueOf("2018-02-05")) // 添加日期字段，值为2018年2月5日
            .put("timeValue", Time.valueOf("04:22:23")) // 添加时间字段，值为4点22分23秒
            .put("timestampValue", Timestamp.valueOf("2018-02-05 04:22:33")) // 添加时间戳字段，值为2018年2月5日4点22分33秒
            .put("stringValue", "ghi") // 添加字符串字段，值为"ghi"
            .put("floatValue", 8.9267) // 添加浮点数字段，值为8.9267
            .build()); // 构建第三个不可变Map，完成第三条记录
  } // 方法结束，返回包含3条测试数据的不可变列表

  private static Connection createConnection() throws SQLException  { // 私有静态方法，创建Calcite数据库连接，可能抛出SQL异常
    final Connection connection = // 声明JDBC连接对象
        DriverManager.getConnection("jdbc:calcite:lex=JAVA"); // 使用DriverManager获取Calcite JDBC连接，lex=JAVA表示使用Java词法规则
    final SchemaPlus root = // 声明SchemaPlus对象，表示Calcite的根模式
        connection.unwrap(CalciteConnection.class).getRootSchema(); // 将连接解包为CalciteConnection并获取根模式

    root.add("geode", // 向根模式添加名为"geode"的子模式
        new GeodeSchema(POLICY.cache(), // 创建GeodeSchema对象，传入Geode缓存实例
            Collections.singleton("allDataTypesRegion"))); // 创建只包含"allDataTypesRegion"的单元素集合，指定要暴露的Region

    return connection; // 返回配置好的Calcite连接对象
  } // 方法结束

  private CalciteAssert.AssertThat calciteAssert() { // 私有实例方法，创建CalciteAssert断言对象，用于测试SQL查询
    return CalciteAssert.that() // 调用CalciteAssert的静态工厂方法创建断言构建器
        .with(GeodeAllDataTypesTest::createConnection); // 使用方法引用传入createConnection方法，配置连接提供者
  } // 方法结束，返回配置好的CalciteAssert.AssertThat对象

  @Test // JUnit5注解，标记此方法为测试方法
void testSqlSingleBooleanWhereFilter() { // 测试方法：测试单个布尔值WHERE过滤条件，验证SQL查询正确转换为Geode OQL
    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT booleanValue as booleanValue " // 执行SQL查询，选择booleanValue字段
            + "FROM geode.allDataTypesRegion WHERE booleanValue = true") // 从geode.allDataTypesRegion表中选择booleanValue为true的记录
        .returnsCount(2) // 断言查询返回2条记录（测试数据中有2条记录的booleanValue为true）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT booleanValue AS booleanValue FROM /allDataTypesRegion " // 预期的Geode OQL查询语句
                + "WHERE booleanValue = true")); // WHERE条件为booleanValue等于true
  } // 方法结束

  @Test // JUnit5注解，标记此方法为测试方法
void testSqlBooleanColumnIsNotNullFilter() { // 测试方法：测试布尔值列的IS NOT NULL过滤条件，验证SQL的IS NOT NULL正确转换为Geode OQL的<> null
    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT booleanValue as booleanValue " // 执行SQL查询，选择booleanValue字段
            + "FROM geode.allDataTypesRegion WHERE booleanValue is not null") // 从geode.allDataTypesRegion表中选择booleanValue不为null的记录
        .returnsCount(3) // 断言查询返回3条记录（所有测试数据的booleanValue都不为null）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT booleanValue AS booleanValue FROM /allDataTypesRegion " // 预期的Geode OQL查询语句
                + "WHERE booleanValue <> null")); // WHERE条件使用<> null表示不等于null，这是Geode OQL的语法
  } // 方法结束

  @Test // JUnit5注解，标记此方法为测试方法
void testSqlBooleanColumnFilter() { // 测试方法：测试直接使用布尔值列作为WHERE条件，验证SQL的WHERE booleanValue正确转换为WHERE booleanValue = true
    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT booleanValue as booleanValue " // 执行SQL查询，选择booleanValue字段
            + "FROM geode.allDataTypesRegion WHERE booleanValue") // WHERE条件直接使用booleanValue列，SQL中这等价于booleanValue = true
        .returnsCount(2) // 断言查询返回2条记录（booleanValue为true的记录）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT booleanValue AS booleanValue FROM /allDataTypesRegion " // 预期的Geode OQL查询语句
                + "WHERE booleanValue = true")); // WHERE条件被转换为booleanValue = true
  } // 方法结束

  @Test // JUnit5注解，标记此方法为测试方法
void testSqlBooleanColumnNotFilter() { // 测试方法：测试NOT布尔值列作为WHERE条件，验证SQL的WHERE NOT booleanValue正确转换为WHERE booleanValue = false
    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT booleanValue as booleanValue " // 执行SQL查询，选择booleanValue字段
            + "FROM geode.allDataTypesRegion WHERE not booleanValue") // WHERE条件使用NOT booleanValue，SQL中这等价于booleanValue = false
        .returnsCount(1) // 断言查询返回1条记录（booleanValue为false的记录）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT booleanValue AS booleanValue FROM /allDataTypesRegion " // 预期的Geode OQL查询语句
                + "WHERE booleanValue = false")); // WHERE条件被转换为booleanValue = false
  } // 方法结束

  @Test // JUnit5注解，标记此方法为测试方法
void testSqlMultipleBooleanWhereFilter() { // 测试方法：测试多个布尔值WHERE过滤条件，验证OR条件正确转换为Geode OQL
    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT booleanValue as booleanValue " // 执行SQL查询，选择booleanValue字段
            + "FROM geode.allDataTypesRegion WHERE booleanValue = true OR booleanValue = false") // WHERE条件使用OR连接两个布尔值条件
        .returnsCount(3) // 断言查询返回3条记录（所有记录的booleanValue要么是true要么是false）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT booleanValue AS booleanValue FROM /allDataTypesRegion " // 预期的Geode OQL查询语句
                + "WHERE booleanValue = true OR booleanValue = false")); // WHERE条件保持OR逻辑
  } // 方法结束

  @Test // JUnit5注解，标记此方法为测试方法
void testSqlWhereWithMultipleOrForLiteralFields() { // 测试方法：测试多个OR条件的WHERE子句，验证优化器将OR转换为IN SET和简化条件
    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT stringValue " // 执行SQL查询，选择stringValue字段
            + "FROM geode.allDataTypesRegion WHERE (stringValue = 'abc' OR stringValue = 'def') OR " // 第一个OR条件：stringValue等于'abc'或'def'
            + "(floatValue = 1.5678 OR floatValue = null) OR " // 第二个OR条件：floatValue等于1.5678或null
            + "(booleanValue = true OR booleanValue = false OR booleanValue = null)") // 第三个OR条件：booleanValue等于true、false或null
        .returnsCount(3) // 断言查询返回3条记录（所有记录都满足至少一个OR条件）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT stringValue AS stringValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion WHERE " // FROM子句
                + "stringValue IN SET('abc', 'def') OR floatValue = 1.5678 " // 优化器将stringValue的OR条件转换为IN SET语法，简化floatValue条件（移除null）
                + "OR booleanValue = true OR booleanValue = false")); // 简化booleanValue条件（移除null）
  } // 方法结束

  @Test // JUnit5注解，标记此方法为测试方法
void testSqlSingleDateWhereFilter() { // 测试方法：测试单个日期值的WHERE过滤条件，验证日期比较操作（=、>、<）
    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT dateValue\n" // 执行SQL查询，选择dateValue字段
            + "FROM geode.allDataTypesRegion\n" // FROM子句
            + "WHERE dateValue = DATE '2018-02-03'") // WHERE条件：dateValue等于2018年2月3日
        .returnsCount(1) // 断言查询返回1条记录（只有一条记录的dateValue是2018-02-03）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT dateValue AS dateValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion " // FROM子句
                + "WHERE dateValue = DATE '2018-02-03'")); // WHERE条件保持不变

    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT dateValue\n" // 执行SQL查询，选择dateValue字段
            + "FROM geode.allDataTypesRegion\n" // FROM子句
            + "WHERE dateValue > DATE '2018-02-03'") // WHERE条件：dateValue大于2018年2月3日
        .returnsCount(2) // 断言查询返回2条记录（有两条记录的dateValue大于2018-02-03）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT dateValue AS dateValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion " // FROM子句
                + "WHERE dateValue > DATE '2018-02-03'")); // WHERE条件保持不变

    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT dateValue\n" // 执行SQL查询，选择dateValue字段
            + "FROM geode.allDataTypesRegion\n" // FROM子句
            + "WHERE dateValue < DATE '2018-02-03'") // WHERE条件：dateValue小于2018年2月3日
        .returnsCount(0) // 断言查询返回0条记录（没有记录的dateValue小于2018-02-03）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT dateValue AS dateValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion " // FROM子句
                + "WHERE dateValue < DATE '2018-02-03'")); // WHERE条件保持不变
  } // 方法结束

  @Test // JUnit5注解，标记此方法为测试方法
void testSqlMultipleDateWhereFilter() { // 测试方法：测试多个日期值的OR条件，验证优化器将OR转换为IN SET语法
    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT dateValue\n" // 执行SQL查询，选择dateValue字段
            + "FROM geode.allDataTypesRegion\n" // FROM子句
            + "WHERE dateValue = DATE '2018-02-03'\n" // WHERE条件：dateValue等于2018年2月3日
            + "  OR dateValue = DATE '2018-02-04'") // OR条件：dateValue等于2018年2月4日
        .returnsCount(2) // 断言查询返回2条记录（有两条记录满足dateValue等于2018-02-03或2018-02-04）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT dateValue AS dateValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion " // FROM子句
                + "WHERE dateValue IN SET(DATE '2018-02-03'," // 优化器将OR条件转换为IN SET语法
                + " DATE '2018-02-04')")); // IN SET包含两个日期值
  } // 方法结束

  @Test // JUnit5注解，标记此方法为测试方法
void testSqlSingleTimeWhereFilter() { // 测试方法：测试单个时间值的WHERE过滤条件，验证时间比较操作（=、>、<）
    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT timeValue\n" // 执行SQL查询，选择timeValue字段
            + "FROM geode.allDataTypesRegion\n" // FROM子句
            + "WHERE timeValue = TIME '02:22:23'") // WHERE条件：timeValue等于2点22分23秒
        .returnsCount(1) // 断言查询返回1条记录（只有一条记录的timeValue是02:22:23）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT timeValue AS timeValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion " // FROM子句
                + "WHERE timeValue = TIME '02:22:23'")); // WHERE条件保持不变

    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT timeValue\n" // 执行SQL查询，选择timeValue字段
            + "FROM geode.allDataTypesRegion\n" // FROM子句
            + "WHERE timeValue > TIME '02:22:23'") // WHERE条件：timeValue大于2点22分23秒
        .returnsCount(2) // 断言查询返回2条记录（有两条记录的timeValue大于02:22:23）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT timeValue AS timeValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion " // FROM子句
                + "WHERE timeValue > TIME '02:22:23'")); // WHERE条件保持不变

    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT timeValue\n" // 执行SQL查询，选择timeValue字段
            + "FROM geode.allDataTypesRegion\n" // FROM子句
            + "WHERE timeValue < TIME '02:22:23'") // WHERE条件：timeValue小于2点22分23秒
        .returnsCount(0) // 断言查询返回0条记录（没有记录的timeValue小于02:22:23）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT timeValue AS timeValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion " // FROM子句
                + "WHERE timeValue < TIME '02:22:23'")); // WHERE条件保持不变
  } // 方法结束

  @Test // JUnit5注解，标记此方法为测试方法
void testSqlMultipleTimeWhereFilter() { // 测试方法：测试多个时间值的OR条件，验证优化器将OR转换为IN SET语法
    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT timeValue\n" // 执行SQL查询，选择timeValue字段
            + "FROM geode.allDataTypesRegion\n" // FROM子句
            + "WHERE timeValue = TIME '02:22:23'\n" // WHERE条件：timeValue等于2点22分23秒
            + "  OR timeValue = TIME '03:22:23'") // OR条件：timeValue等于3点22分23秒
        .returnsCount(2) // 断言查询返回2条记录（有两条记录满足timeValue等于02:22:23或03:22:23）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT timeValue AS timeValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion " // FROM子句
                + "WHERE timeValue IN SET(TIME '02:22:23', TIME '03:22:23')")); // 优化器将OR条件转换为IN SET语法
  } // 方法结束

  @Test // JUnit5注解，标记此方法为测试方法
void testSqlSingleTimestampWhereFilter() { // 测试方法：测试单个时间戳值的WHERE过滤条件，验证时间戳比较操作（=、>、<）
    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT timestampValue\n" // 执行SQL查询，选择timestampValue字段
            + "FROM geode.allDataTypesRegion\n" // FROM子句
            + "WHERE timestampValue = TIMESTAMP '2018-02-03 02:22:33'") // WHERE条件：timestampValue等于2018年2月3日2点22分33秒
        .returnsCount(1) // 断言查询返回1条记录（只有一条记录的timestampValue是2018-02-03 02:22:33）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT timestampValue AS timestampValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion " // FROM子句
                + "WHERE timestampValue = TIMESTAMP '2018-02-03 02:22:33'")); // WHERE条件保持不变

    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT timestampValue\n" // 执行SQL查询，选择timestampValue字段
            + "FROM geode.allDataTypesRegion\n" // FROM子句
            + "WHERE timestampValue > TIMESTAMP '2018-02-03 02:22:33'") // WHERE条件：timestampValue大于2018年2月3日2点22分33秒
        .returnsCount(2) // 断言查询返回2条记录（有两条记录的timestampValue大于2018-02-03 02:22:33）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT timestampValue AS timestampValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion " // FROM子句
                + "WHERE timestampValue > TIMESTAMP '2018-02-03 02:22:33'")); // WHERE条件保持不变

    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT timestampValue\n" // 执行SQL查询，选择timestampValue字段
            + "FROM geode.allDataTypesRegion\n" // FROM子句
            + "WHERE timestampValue < TIMESTAMP '2018-02-03 02:22:33'") // WHERE条件：timestampValue小于2018年2月3日2点22分33秒
        .returnsCount(0) // 断言查询返回0条记录（没有记录的timestampValue小于2018-02-03 02:22:33）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT timestampValue AS timestampValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion " // FROM子句
                + "WHERE timestampValue < TIMESTAMP '2018-02-03 02:22:33'")); // WHERE条件保持不变
  } // 方法结束

  @Test // JUnit5注解，标记此方法为测试方法
void testSqlMultipleTimestampWhereFilter() { // 测试方法：测试多个时间戳值的OR条件，验证优化器将OR转换为IN SET语法
    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT timestampValue\n" // 执行SQL查询，选择timestampValue字段
            + "FROM geode.allDataTypesRegion\n" // FROM子句
            + "WHERE timestampValue = TIMESTAMP '2018-02-03 02:22:33'\n" // WHERE条件：timestampValue等于2018年2月3日2点22分33秒
            + "  OR timestampValue = TIMESTAMP '2018-02-05 04:22:33'") // OR条件：timestampValue等于2018年2月5日4点22分33秒
        .returnsCount(2) // 断言查询返回2条记录（有两条记录满足timestampValue等于指定值）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT timestampValue AS timestampValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion " // FROM子句
                + "WHERE timestampValue IN SET(" // 优化器将OR条件转换为IN SET语法
                + "TIMESTAMP '2018-02-03 02:22:33', " // IN SET包含第一个时间戳值
                + "TIMESTAMP '2018-02-05 04:22:33')")); // IN SET包含第二个时间戳值
  } // 方法结束

  @Test // JUnit5注解，标记此方法为测试方法
void testSqlWhereWithMultipleOrForAllFields() { // 测试方法：测试包含所有字段类型的复杂OR条件，验证优化器对多种数据类型的OR条件进行优化转换
    calciteAssert() // 获取CalciteAssert断言对象
        .query("SELECT stringValue " // 执行SQL查询，选择stringValue字段
            + "FROM geode.allDataTypesRegion WHERE (stringValue = 'abc' OR stringValue = 'def') OR " // 第一个OR条件：字符串字段
            + "(floatValue = 1.5678 OR floatValue = null) OR " // 第二个OR条件：浮点数字段
            + "(dateValue = DATE '2018-02-05' OR dateValue = DATE '2018-02-06' ) OR " // 第三个OR条件：日期字段
            + "(timeValue = TIME '03:22:23' OR timeValue = TIME '07:22:23') OR " // 第四个OR条件：时间字段
            + "(timestampValue = TIMESTAMP '2018-02-05 04:22:33' OR " // 第五个OR条件：时间戳字段（第一部分）
            + "timestampValue = TIMESTAMP '2017-02-05 04:22:33') OR " // 第五个OR条件：时间戳字段（第二部分）
            + "(booleanValue = true OR booleanValue = false OR booleanValue = null)") // 第六个OR条件：布尔字段
        .returnsCount(3) // 断言查询返回3条记录（所有记录都满足至少一个OR条件）
        .queryContains( // 断言生成的Geode OQL查询包含指定的查询语句
            GeodeAssertions.query("SELECT stringValue AS stringValue " // 预期的Geode OQL查询语句
                + "FROM /allDataTypesRegion WHERE " // FROM子句
                + "stringValue IN SET('abc', 'def') OR floatValue = 1.5678 OR dateValue " // 优化器将字符串、日期、时间、时间戳的OR条件转换为IN SET，简化浮点数和布尔条件
                + "IN SET(DATE '2018-02-05', DATE '2018-02-06') OR timeValue " // 日期的IN SET
                + "IN SET(TIME '03:22:23', TIME '07:22:23') OR timestampValue " // 时间的IN SET
                + "IN SET(TIMESTAMP '2017-02-05 04:22:33', TIMESTAMP '2018-02-05 04:22:33') " // 时间戳的IN SET
                + "OR booleanValue = true OR booleanValue = false")); // 简化的布尔条件
  } // 方法结束
} // 类定义结束
}
