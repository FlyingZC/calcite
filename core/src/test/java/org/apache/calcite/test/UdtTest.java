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
package org.apache.calcite.test; // 包声明，指定该类属于 org.apache.calcite.test 包

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

/**
 * Tests for user-defined types. // 用户自定义类型（User-Defined Types，简称 UDT）的测试类
 * // 该类用于测试 Calcite 中用户自定义类型的功能，包括类型定义、类型转换等
 * // UDT 允许用户在 SQL 中定义自己的数据类型，扩展了标准 SQL 的类型系统
 */
class UdtTest { // UdtTest 类：用于测试 Calcite 中用户自定义类型功能的测试类
  private CalciteAssert.AssertThat withUdt() { // 私有辅助方法：创建配置了用户自定义类型的 CalciteAssert 断言对象，用于测试 UDT 功能
    final String model = "{\n" // 定义一个 JSON 格式的模型字符串，用于配置 Calcite 的数据模型
        + "  version: '1.0',\n" // 模型版本号，指定为 1.0
        + "  types: [\n" // 根级别的类型定义数组，定义全局可用的用户自定义类型
        + "     {\n" // 第一个根级别的类型定义开始
        + "       name: 'foo',\n" // 类型名称为 'foo'，这是一个全局类型，可以在任何 schema 中使用
        + "       type: 'BIGINT'\n" // 该类型基于 BIGINT 标准类型，即 foo 类型实际上是 BIGINT 的别名
        + "     }" // 第一个根级别类型定义结束
        + "   ],\n" // 根级别类型数组结束
        + "   schemas: [\n" // Schema（模式/数据库）定义数组，可以包含多个 schema
        + "     {\n" // 第一个 schema 定义开始
        + "       name: 'adhoc',\n" // Schema 名称为 'adhoc'，这是一个自定义的 schema
        + "       types: [\n" // 在该 schema 中定义的类型数组，这些类型仅在 'adhoc' schema 中可用
        + "         {\n" // 第一个 schema 级别的类型定义开始
        + "           name: 'mytype1',\n" // 类型名称为 'mytype1'，完整引用为 'adhoc'.mytype1
        + "           type: 'BIGINT'\n" // 该类型基于 BIGINT 标准类型，即 mytype1 是 BIGINT 的别名
        + "         },\n" // 第一个 schema 级别类型定义结束
        + "         {\n" // 第二个 schema 级别的类型定义开始
        + "           name: 'mytype2',\n" // 类型名称为 'mytype2'，完整引用为 'adhoc'.mytype2
        + "           attributes: [\n" // 定义该类型的属性（字段）数组，表示这是一个复合类型（结构体类型）
        + "             {\n" // 第一个属性定义开始
        + "               name: 'ii',\n" // 属性名称为 'ii'，这是复合类型的第一个字段
        + "               type: 'INTEGER'\n" // 属性类型为 INTEGER，即字段 ii 的类型是整数
        + "             },\n" // 第一个属性定义结束
        + "             {\n" // 第二个属性定义开始
        + "               name: 'jj',\n" // 属性名称为 'jj'，这是复合类型的第二个字段
        + "               type: 'INTEGER'\n" // 属性类型为 INTEGER，即字段 jj 的类型是整数
        + "             }\n" // 第二个属性定义结束
        + "           ]\n" // 属性数组结束，mytype2 是一个包含两个 INTEGER 字段的复合类型
        + "         }\n" // 第二个 schema 级别类型定义结束
        + "       ]\n" // schema 类型数组结束
        + "     }\n" // 第一个 schema 定义结束
        + "   ]\n" // Schema 数组结束
        + "}"; // JSON 模型字符串结束
    return CalciteAssert.model(model); // 使用定义的模型创建 CalciteAssert.AssertThat 对象并返回，该对象用于执行 SQL 查询和验证结果
  }

  @Test void testUdt() { // 测试方法：测试在 schema 级别使用用户自定义类型进行类型转换的功能
    final String sql = "select CAST(\"id\" AS \"adhoc\".mytype1) as ld " // 定义 SQL 查询语句：将 id 列的值转换为 'adhoc'.mytype1 类型，并将结果列命名为 ld
        + "from (VALUES ROW(1, 'SameName')) AS \"t\" (\"id\", \"desc\")"; // 使用 VALUES 子句创建一个临时表 t，包含一行数据，id 列值为 1，desc 列值为 'SameName'
    withUdt().query(sql).returns("LD=1\n"); // 使用配置了 UDT 的环境执行查询，并验证返回结果是否为 "LD=1\n"，即类型转换成功
  }

  @Test void testRootUdt() { // 测试方法：测试在根级别（全局）使用用户自定义类型进行类型转换的功能
    final String sql = "select CAST(\"id\" AS foo) as ld " // 定义 SQL 查询语句：将 id 列的值转换为全局类型 foo（不需要指定 schema 前缀），并将结果列命名为 ld
        + "from (VALUES ROW(1, 'SameName')) AS \"t\" (\"id\", \"desc\")"; // 使用 VALUES 子句创建一个临时表 t，包含一行数据，id 列值为 1，desc 列值为 'SameName'
    withUdt().query(sql).returns("LD=1\n"); // 使用配置了 UDT 的环境执行查询，并验证返回结果是否为 "LD=1\n"，即全局类型转换成功
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3045">[CALCITE-3045] // 引用 JIRA 问题 CALCITE-3045
   * NullPointerException when casting null literal to composite user defined type</a>. // 问题描述：将 null 字面量转换为复合用户自定义类型时出现空指针异常
   * // 该测试用于验证修复后的行为，确保将 null 转换为复合类型（如 mytype2）时不会抛出异常，而是正确返回 null
   */
  @Test void testCastNullLiteralToCompositeUdt() { // 测试方法：测试将 null 字面量转换为复合用户自定义类型的功能
    final String sql = "select CAST(null AS \"adhoc\".mytype2) as c " // 定义 SQL 查询语句：将 null 字面量转换为复合类型 'adhoc'.mytype2（包含 ii 和 jj 两个字段），并将结果列命名为 c
        + "from (VALUES (1))"; // 使用 VALUES 子句创建一个临时表，包含一行数据（值为 1），该表仅用于提供查询上下文
    withUdt().query(sql).returns("C=null\n"); // 使用配置了 UDT 的环境执行查询，并验证返回结果是否为 "C=null\n"，即 null 成功转换为复合类型
  }
} // UdtTest 类结束
