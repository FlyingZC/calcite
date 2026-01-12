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
// Apache License 2.0 许可证声明，这是 Apache 项目的标准许可证头
package org.apache.calcite.sql.type; // 声明包名，位于 org.apache.calcite.sql.type 包下

import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入 RelDataTypeFactory 接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入 RelDataTypeSystem 接口，定义类型系统的行为

import com.google.common.collect.Sets; // 导入 Google Guava 的 Sets 工具类，用于集合操作

import org.junit.jupiter.params.ParameterizedTest; // 导入 JUnit 5 的参数化测试注解
import org.junit.jupiter.params.provider.MethodSource; // 导入 JUnit 5 的方法源注解，用于提供测试参数

import java.util.Arrays; // 导入 Java 标准库的 Arrays 工具类
import java.util.Collection; // 导入 Java 标准库的 Collection 接口
import java.util.HashSet; // 导入 Java 标准库的 HashSet 类，实现 Set 接口
import java.util.List; // 导入 Java 标准库的 List 接口
import java.util.Set; // 导入 Java 标准库的 Set 接口

import static org.apache.calcite.sql.type.SqlTypeName.BOOLEAN_TYPES; // 导入 SqlTypeName 的布尔类型集合
import static org.apache.calcite.sql.type.SqlTypeName.CHAR_TYPES; // 导入 SqlTypeName 的字符类型集合
import static org.apache.calcite.sql.type.SqlTypeName.DATETIME_TYPES; // 导入 SqlTypeName 的日期时间类型集合
import static org.apache.calcite.sql.type.SqlTypeName.NUMERIC_TYPES; // 导入 SqlTypeName 的数值类型集合

import static org.junit.jupiter.api.Assertions.assertSame; // 导入 JUnit 5 的断言方法，用于验证两个对象引用相同

/**
 * Test to ensure that {@link SqlTypeFactoryImpl} creates canonical types as per its contract.
 * Two (or more) calls to the same method with the same arguments should always give back the same
 * {@code RelDataType} object.
 */
// 测试类文档注释：确保 SqlTypeFactoryImpl 按照其约定创建规范类型
// 对同一方法使用相同参数进行两次或多次调用，应该总是返回同一个 RelDataType 对象
// 这是类型工厂的重要特性，称为"类型规范化"或"类型缓存"
class SqlTypeFactoryCanonicalTest { // 定义测试类，用于测试 SqlTypeFactoryImpl 的类型规范化特性

  @ParameterizedTest(name = "{0}") // 参数化测试注解，使用参数名称作为测试名称
  @MethodSource("typesProvider") // 指定测试数据提供方法为 typesProvider
  void testLeastRestrictive(List<RelDataType> types) { // 测试方法：验证 leastRestrictive 方法返回相同对象的特性
    RelDataTypeFactory f = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建 SQL 类型工厂实例，使用默认类型系统
    RelDataType r1 = f.leastRestrictive(types); // 第一次调用 leastRestrictive 方法，获取类型列表的最小限制类型
    RelDataType r2 = f.leastRestrictive(types); // 第二次调用 leastRestrictive 方法，使用相同的参数
    assertSame(r1, r2); // 断言两次调用返回的对象引用相同，验证类型规范化特性
  } // 方法结束

  static Collection<List<RelDataType>> typesProvider() { // 静态方法：为参数化测试提供测试数据
    RelDataTypeFactory f = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂实例
    Set<RelDataType> numbers = generateComplexTypes(f, NUMERIC_TYPES); // 生成数值类型的复杂类型集合
    Set<RelDataType> chars = generateComplexTypes(f, CHAR_TYPES); // 生成字符类型的复杂类型集合
    Set<RelDataType> datetimes = generateComplexTypes(f, DATETIME_TYPES); // 生成日期时间类型的复杂类型集合
    Set<RelDataType> bools = generateComplexTypes(f, BOOLEAN_TYPES); // 生成布尔类型的复杂类型集合
    Set<List<RelDataType>> products = new HashSet<>(); // 创建集合用于存储类型列表的笛卡尔积
    products.addAll(Sets.cartesianProduct(numbers, numbers)); // 添加数值类型的笛卡尔积，测试数值类型之间的最小限制类型
    products.addAll(Sets.cartesianProduct(chars, chars)); // 添加字符类型的笛卡尔积，测试字符类型之间的最小限制类型
    products.addAll(Sets.cartesianProduct(datetimes, datetimes)); // 添加日期时间类型的笛卡尔积，测试日期时间类型之间的最小限制类型
    products.addAll(Sets.cartesianProduct(bools, bools)); // 添加布尔类型的笛卡尔积，测试布尔类型之间的最小限制类型
    return products; // 返回所有类型组合的集合，作为测试数据
  } // 方法结束

  private static Set<RelDataType> generateComplexTypes(RelDataTypeFactory f, // 私有静态方法：生成给定类型名称列表的复杂类型集合
      List<SqlTypeName> typeName) { // 参数 f：类型工厂；参数 typeName：要生成复杂类型的类型名称列表
    Set<RelDataType> types = new HashSet<>(); // 创建集合用于存储生成的复杂类型
    for (SqlTypeName t : typeName) { // 遍历每个类型名称
      RelDataType basic = f.createSqlType(t); // 创建基本类型，例如 INTEGER、VARCHAR 等
      types.add(basic); // 将基本类型添加到集合中
      types.add(f.createArrayType(basic, -1)); // 创建数组类型，元素类型为 basic，-1 表示未知长度
      types.add(f.createMapType(basic, basic)); // 创建 Map 类型，键和值都使用 basic 类型
      types.add(f.createMultisetType(basic, -1)); // 创建多集类型（Multiset），元素类型为 basic，-1 表示未知基数
      types.add(f.createStructType(Arrays.asList(basic, basic), Arrays.asList("f1", "f2"))); // 创建结构类型，包含两个字段 f1 和 f2，类型都为 basic
    } // 循环结束
    return types; // 返回生成的复杂类型集合
  } // 方法结束

} // 类结束
