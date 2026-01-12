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
package org.apache.calcite.test; // 声明包名，表示该类属于 org.apache.calcite.test 包
import org.apache.calcite.DataContext; // 导入 DataContext 类，用于提供数据上下文信息
import org.apache.calcite.jdbc.CalciteConnection; // 导入 CalciteConnection 类，表示 Calcite JDBC 连接
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入 AbstractEnumerable 类，用于实现可枚举集合的抽象基类
import org.apache.calcite.linq4j.DelegatingEnumerator; // 导入 DelegatingEnumerator 类，用于实现委托枚举器
import org.apache.calcite.linq4j.Enumerable; // 导入 Enumerable 接口，表示可枚举的集合
import org.apache.calcite.linq4j.Enumerator; // 导入 Enumerator 接口，表示枚举器，用于遍历集合元素
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 类，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入 RelDataTypeFactory 接口，用于创建关系数据类型
import org.apache.calcite.rex.RexCall; // 导入 RexCall 类，表示 Rex 表达式调用
import org.apache.calcite.rex.RexInputRef; // 导入 RexInputRef 类，表示 Rex 输入引用
import org.apache.calcite.rex.RexLiteral; // 导入 RexLiteral 类，表示 Rex 字面量
import org.apache.calcite.rex.RexNode; // 导入 RexNode 类，表示 Rex 表达式节点
import org.apache.calcite.runtime.Hook; // 导入 Hook 类，用于运行时钩子机制
import org.apache.calcite.runtime.PairList; // 导入 PairList 类，用于存储键值对列表
import org.apache.calcite.schema.FilterableTable; // 导入 FilterableTable 接口，表示可过滤的表
import org.apache.calcite.schema.ProjectableFilterableTable; // 导入 ProjectableFilterableTable 接口，表示可投影和可过滤的表
import org.apache.calcite.schema.ScannableTable; // 导入 ScannableTable 接口，表示可扫描的表
import org.apache.calcite.schema.Schema; // 导入 Schema 接口，表示数据库模式
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 接口，表示增强的模式接口
import org.apache.calcite.schema.Table; // 导入 Table 接口，表示数据库表
import org.apache.calcite.schema.impl.AbstractSchema; // 导入 AbstractSchema 类，表示抽象的模式实现
import org.apache.calcite.schema.impl.AbstractTable; // 导入 AbstractTable 类，表示抽象的表实现
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入 SqlStdOperatorTable 类，包含标准 SQL 操作符
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SqlTypeName 枚举，定义 SQL 类型名称
import org.apache.calcite.test.CalciteAssert.ConnectionPostProcessor; // 导入 ConnectionPostProcessor 接口，用于连接后处理
import org.apache.calcite.util.NlsString; // 导入 NlsString 类，表示国际化字符串
import org.apache.calcite.util.Pair; // 导入 Pair 类，表示键值对

import com.google.common.collect.ImmutableMap; // 导入 Google Guava 的 ImmutableMap 类，用于创建不可变映射

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Nullable 注解，用于标记可空类型
import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import java.math.BigDecimal; // 导入 BigDecimal 类，用于高精度十进制运算
import java.sql.Connection; // 导入 Connection 接口，表示数据库连接
import java.sql.DriverManager; // 导入 DriverManager 类，用于管理数据库驱动
import java.sql.PreparedStatement; // 导入 PreparedStatement 接口，表示预编译的 SQL 语句
import java.sql.ResultSet; // 导入 ResultSet 接口，表示数据库查询结果集
import java.sql.SQLException; // 导入 SQLException 类，表示 SQL 异常
import java.util.Arrays; // 导入 Arrays 类，用于数组操作
import java.util.Iterator; // 导入 Iterator 接口，用于迭代器
import java.util.List; // 导入 List 接口，表示列表集合
import java.util.Map; // 导入 Map 接口，表示映射集合
import java.util.Properties; // 导入 Properties 类，用于属性配置
import java.util.concurrent.atomic.AtomicInteger; // 导入 AtomicInteger 类，用于原子整数操作

import static org.hamcrest.CoreMatchers.equalTo; // 导入 equalTo 匹配器，用于断言相等
import static org.hamcrest.CoreMatchers.is; // 导入 is 匹配器，用于断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入 assertThat 方法，用于断言
import static org.hamcrest.Matchers.arrayWithSize; // 导入 arrayWithSize 匹配器，用于断言数组大小
import static org.hamcrest.Matchers.hasToString; // 导入 hasToString 匹配器，用于断言字符串表示
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入 assertFalse 方法，用于断言为假
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入 assertTrue 方法，用于断言为真

import static java.util.Objects.requireNonNull; // 导入 requireNonNull 方法，用于检查非空

/**
 * Unit test for {@link org.apache.calcite.schema.ScannableTable}.
 * ScannableTable 接口的单元测试类
 * 
 * 本测试类用于测试 Calcite 的 ScannableTable、FilterableTable 和 ProjectableFilterableTable 接口
 * 主要测试内容包括：
 * 1. 基本的可扫描表功能测试
 * 2. 可过滤表的过滤下推测试
 * 3. 可投影可过滤表的投影和过滤下推测试
 * 4. 协作模式和非协作模式的对比测试
 * 5. 预编译语句的扫描次数测试
 * 6. 表连接查询测试
 * 
 * 该测试类通过模拟不同类型的表实现，验证 Calcite 优化器是否能够正确地将过滤条件和投影下推到数据源
 */
public class ScannableTableTest { // ScannableTableTest 类定义，用于测试 ScannableTable 接口
  @Test void testTens() { // 测试 tens() 方法返回的枚举器，验证其能够正确返回 0, 10, 20, 30 四个值
    try (Enumerator<Object[]> cursor = tens()) { // 创建一个 try-with-resources 块，自动管理枚举器的资源
      assertTrue(cursor.moveNext()); // 断言可以移动到下一个元素，第一个元素存在
      assertThat(cursor.current()[0], equalTo(0)); // 断言当前元素的第一个值等于 0
      assertThat(cursor.current(), arrayWithSize(1)); // 断言当前元素数组的大小为 1
      assertTrue(cursor.moveNext()); // 断言可以移动到下一个元素，第二个元素存在
      assertThat(cursor.current()[0], equalTo(10)); // 断言当前元素的第一个值等于 10
      assertTrue(cursor.moveNext()); // 断言可以移动到下一个元素，第三个元素存在
      assertThat(cursor.current()[0], equalTo(20)); // 断言当前元素的第一个值等于 20
      assertTrue(cursor.moveNext()); // 断言可以移动到下一个元素，第四个元素存在
      assertThat(cursor.current()[0], equalTo(30)); // 断言当前元素的第一个值等于 30
      assertFalse(cursor.moveNext()); // 断言不能再移动到下一个元素，已到达末尾
    } // 自动关闭枚举器，释放资源
  }

  /** A table with one column. */ // 测试只有一个列的表
  @Test void testSimple() { // 测试 SimpleTable，验证单列表的查询功能
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("simple", new SimpleTable()))) // 注册一个名为 "s" 的模式，包含一个名为 "simple" 的 SimpleTable 表
        .query("select * from \"s\".\"simple\"") // 执行查询，选择 "s"."simple" 表的所有列
        .returnsUnordered("i=0", "i=10", "i=20", "i=30"); // 断言查询结果包含四行数据，顺序不限
  }

  /** A table with two columns. */ // 测试有三个列的表
  @Test void testSimple2() { // 测试 BeatlesTable，验证三列表的查询功能
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles", new BeatlesTable()))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles" 的 BeatlesTable 表
        .query("select * from \"s\".\"beatles\"") // 执行查询，选择 "s"."beatles" 表的所有列
        .returnsUnordered("i=4; j=John; k=1940", // 断言查询结果包含第一行数据，顺序不限
            "i=4; j=Paul; k=1942", // 断言查询结果包含第二行数据
            "i=6; j=George; k=1943", // 断言查询结果包含第三行数据
            "i=5; j=Ringo; k=1940"); // 断言查询结果包含第四行数据
  }

  /** A filter on a {@link FilterableTable} with two columns (cooperative). */ // 测试 FilterableTable 的过滤功能（协作模式）
  @Test void testFilterableTableCooperative() { // 测试可过滤表在协作模式下的过滤下推
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final Table table = new BeatlesFilterableTable(buf, true); // 创建一个协作模式的 BeatlesFilterableTable 表实例
    final String explain = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableInterpreter\n" // 第一层：可枚举解释器
        + "  BindableTableScan(table=[[s, beatles]], filters=[[=($0, 4)]])"; // 第二层：可绑定表扫描，包含过滤条件 i = 4
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles" 的表
        .query("select * from \"s\".\"beatles\" where \"i\" = 4") // 执行查询，选择 i = 4 的行
        .explainContains(explain) // 断言执行计划包含预期的字符串
        .returnsUnordered("i=4; j=John; k=1940", // 断言查询结果包含第一行数据
            "i=4; j=Paul; k=1942"); // 断言查询结果包含第二行数据
    // Only 2 rows came out of the table. If the value is 4, it means that the
    // planner did not pass the filter down.
    // 只有 2 行数据从表中返回。如果值是 4，说明优化器没有将过滤下推
    assertThat(buf, hasToString("returnCount=2, filter=<0, 4>")); // 断言表返回了 2 行，并且过滤条件被下推（filter=<0, 4>）
  }

  /** A filter on a {@link FilterableTable} with two columns (noncooperative). */ // 测试 FilterableTable 的过滤功能（非协作模式）
  @Test void testFilterableTableNonCooperative() { // 测试可过滤表在非协作模式下的过滤下推
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final Table table = new BeatlesFilterableTable(buf, false); // 创建一个非协作模式的 BeatlesFilterableTable 表实例
    final String explain = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableInterpreter\n" // 第一层：可枚举解释器
        + "  BindableTableScan(table=[[s, beatles2]], filters=[[=($0, 4)]])"; // 第二层：可绑定表扫描，包含过滤条件 i = 4
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles2", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles2" 的表
        .query("select * from \"s\".\"beatles2\" where \"i\" = 4") // 执行查询，选择 i = 4 的行
        .explainContains(explain) // 断言执行计划包含预期的字符串
        .returnsUnordered("i=4; j=John; k=1940", // 断言查询结果包含第一行数据
            "i=4; j=Paul; k=1942"); // 断言查询结果包含第二行数据
    assertThat(buf, hasToString("returnCount=4")); // 断言表返回了 4 行（所有行），因为非协作模式不接受过滤下推
  }

  /** A filter on a {@link org.apache.calcite.schema.ProjectableFilterableTable}
   * with two columns (cooperative). */ // 测试 ProjectableFilterableTable 的过滤和投影功能（协作模式）
  @Test void testProjectableFilterableCooperative() { // 测试可投影可过滤表在协作模式下的过滤和投影下推
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final Table table = new BeatlesProjectableFilterableTable(buf, true); // 创建一个协作模式的 BeatlesProjectableFilterableTable 表实例
    final String explain = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableInterpreter\n" // 第一层：可枚举解释器
        + "  BindableTableScan(table=[[s, beatles]], filters=[[=($0, 4)]], projects=[[1]])"; // 第二层：可绑定表扫描，包含过滤条件和投影
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles" 的表
        .query("select \"j\" from \"s\".\"beatles\" where \"i\" = 4") // 执行查询，选择 i = 4 的行，并且只投影 j 列
        .explainContains(explain) // 断言执行计划包含预期的字符串
        .returnsUnordered("j=John", // 断言查询结果包含第一行的 j 列
            "j=Paul"); // 断言查询结果包含第二行的 j 列
    // Only 2 rows came out of the table. If the value is 4, it means that the
    // planner did not pass the filter down.
    // 只有 2 行数据从表中返回。如果值是 4，说明优化器没有将过滤下推
    assertThat(buf, // 断言表的执行信息
        hasToString("returnCount=2, filter=<0, 4>, projects=[1, 0]")); // 返回 2 行，过滤被下推，投影被下推（projects=[1, 0]）
  }

  @Test void testProjectableFilterableNonCooperative() { // 测试可投影可过滤表在非协作模式下的过滤和投影下推
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final Table table = new BeatlesProjectableFilterableTable(buf, false); // 创建一个非协作模式的 BeatlesProjectableFilterableTable 表实例
    final String explain = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableInterpreter\n" // 第一层：可枚举解释器
        + "  BindableTableScan(table=[[s, beatles2]], filters=[[=($0, 4)]], projects=[[1]]"; // 第二层：可绑定表扫描，包含过滤条件和投影
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles2", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles2" 的表
        .query("select \"j\" from \"s\".\"beatles2\" where \"i\" = 4") // 执行查询，选择 i = 4 的行，并且只投影 j 列
        .explainContains(explain) // 断言执行计划包含预期的字符串
        .returnsUnordered("j=John", // 断言查询结果包含第一行的 j 列
            "j=Paul"); // 断言查询结果包含第二行的 j 列
    assertThat(buf, hasToString("returnCount=4, projects=[1, 0]")); // 断言表返回了 4 行（所有行），投影被下推
  }

  /** A filter on a {@link org.apache.calcite.schema.ProjectableFilterableTable}
   * with two columns, and a project in the query. (Cooperative)*/ // 测试 ProjectableFilterableTable 的过滤和投影功能，查询中包含投影（协作模式）
  @Test void testProjectableFilterableWithProjectAndFilter() { // 测试可投影可过滤表在协作模式下，查询中包含投影和过滤
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final Table table = new BeatlesProjectableFilterableTable(buf, true); // 创建一个协作模式的 BeatlesProjectableFilterableTable 表实例
    final String explain = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableInterpreter\n" // 第一层：可枚举解释器
        + "  BindableTableScan(table=[[s, beatles]], filters=[[=($0, 4)]], projects=[[2, 1]])"; // 第二层：可绑定表扫描，包含过滤条件和投影
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles" 的表
        .query("select \"k\",\"j\" from \"s\".\"beatles\" where \"i\" = 4") // 执行查询，选择 i = 4 的行，并且投影 k 和 j 列
        .explainContains(explain) // 断言执行计划包含预期的字符串
        .returnsUnordered("k=1940; j=John", // 断言查询结果包含第一行数据
            "k=1942; j=Paul"); // 断言查询结果包含第二行数据
    assertThat(buf, // 断言表的执行信息
        hasToString("returnCount=2, filter=<0, 4>, projects=[2, 1, 0]")); // 返回 2 行，过滤被下推，投影被下推（projects=[2, 1, 0]，包含过滤需要的列）
  }

  /** A filter on a {@link org.apache.calcite.schema.ProjectableFilterableTable}
   * with two columns, and a project in the query (NonCooperative). */ // 测试 ProjectableFilterableTable 的过滤和投影功能，查询中包含投影（非协作模式）
  @Test void testProjectableFilterableWithProjectFilterNonCooperative() { // 测试可投影可过滤表在非协作模式下，查询中包含投影和过滤
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final Table table = new BeatlesProjectableFilterableTable(buf, false); // 创建一个非协作模式的 BeatlesProjectableFilterableTable 表实例
    final String explain = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableInterpreter\n" // 第一层：可枚举解释器
        + "  BindableTableScan(table=[[s, beatles]], filters=[[>($2, 1941)]], " // 第二层：可绑定表扫描，包含过滤条件和投影
        + "projects=[[0, 2]])";
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles" 的表
        .query("select \"i\",\"k\" from \"s\".\"beatles\" where \"k\" > 1941") // 执行查询，选择 k > 1941 的行，并且投影 i 和 k 列
        .explainContains(explain) // 断言执行计划包含预期的字符串
        .returnsUnordered("i=4; k=1942", // 断言查询结果包含第一行数据
            "i=6; k=1943"); // 断言查询结果包含第二行数据
    assertThat(buf, // 断言表的执行信息
        hasToString("returnCount=4, projects=[0, 2]")); // 返回 4 行（所有行），投影被下推
  }

  /** A filter and project on a
   * {@link org.apache.calcite.schema.ProjectableFilterableTable}. The table
   * refuses to execute the filter, so Calcite should add a pull up and
   * transform the filter (projecting the column needed by the filter). */ // 测试 ProjectableFilterableTable 拒绝执行过滤，Calcite 应该添加上拉并转换过滤（投影过滤需要的列）
  @Test void testPFTableRefusesFilterCooperative() { // 测试可投影可过滤表拒绝执行过滤时，Calcite 的处理方式
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final Table table = new BeatlesProjectableFilterableTable(buf, false); // 创建一个非协作模式的 BeatlesProjectableFilterableTable 表实例
    final String explain = "PLAN=EnumerableInterpreter\n" // 定义预期的执行计划字符串
        + "  BindableTableScan(table=[[s, beatles2]], filters=[[=($0, 4)]], projects=[[2]])"; // 可绑定表扫描，包含过滤条件和投影
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles2", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles2" 的表
        .query("select \"k\" from \"s\".\"beatles2\" where \"i\" = 4") // 执行查询，选择 i = 4 的行，并且只投影 k 列
        .explainContains(explain) // 断言执行计划包含预期的字符串
        .returnsUnordered("k=1940", // 断言查询结果包含第一行的 k 列
            "k=1942"); // 断言查询结果包含第二行的 k 列
    assertThat(buf, // 断言表的执行信息
        hasToString("returnCount=4, projects=[2, 0]")); // 返回 4 行（所有行），投影包含 k 和 i 列（i 列用于过滤）
  }

  @Test void testPFPushDownProjectFilterInAggregateNoGroup() { // 测试可投影可过滤表在无分组聚合查询中的投影和过滤下推
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final Table table = new BeatlesProjectableFilterableTable(buf, false); // 创建一个非协作模式的 BeatlesProjectableFilterableTable 表实例
    final String explain = "PLAN=EnumerableAggregate(group=[{}], M=[MAX($0)])\n" // 定义预期的执行计划字符串，第一层是无分组聚合
        + "  EnumerableInterpreter\n" // 第二层：可枚举解释器
        + "    BindableTableScan(table=[[s, beatles]], filters=[[>($0, 1)]], projects=[[2]])"; // 第三层：可绑定表扫描，包含过滤条件和投影
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles" 的表
        .query("select max(\"k\") as m from \"s\".\"beatles\" where \"i\" > 1") // 执行查询，计算 k 列的最大值，过滤条件为 i > 1
        .explainContains(explain) // 断言执行计划包含预期的字符串
        .returnsUnordered("M=1943"); // 断言查询结果的最大值为 1943
  }

  @Test void testPFPushDownProjectFilterAggregateGroup() { // 测试可投影可过滤表在分组聚合查询中的投影和过滤下推
    final String sql = "select \"i\", count(*) as c\n" // 定义 SQL 查询字符串，按 i 分组并计算每组的数量
        + "from \"s\".\"beatles\"\n" // 从 beatles 表查询
        + "where \"k\" > 1900\n" // 过滤条件为 k > 1900
        + "group by \"i\""; // 按 i 分组
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final Table table = new BeatlesProjectableFilterableTable(buf, false); // 创建一个非协作模式的 BeatlesProjectableFilterableTable 表实例
    final String explain = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableAggregate(group=[{0}], C=[COUNT()])\n" // 第一层：分组聚合
        + "  EnumerableInterpreter\n" // 第二层：可枚举解释器
        + "    BindableTableScan(table=[[s, beatles]], filters=[[>($2, 1900)]], " // 第三层：可绑定表扫描，包含过滤条件和投影
        + "projects=[[0]])";
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles" 的表
        .query(sql) // 执行查询
        .explainContains(explain) // 断言执行计划包含预期的字符串
        .returnsUnordered("i=4; C=2", // 断言查询结果包含 i=4 的分组，数量为 2
            "i=5; C=1", // 断言查询结果包含 i=5 的分组，数量为 1
            "i=6; C=1"); // 断言查询结果包含 i=6 的分组，数量为 1
  }

  @Test void testPFPushDownProjectFilterAggregateNested() { // 测试可投影可过滤表在嵌套聚合查询中的投影和过滤下推
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final String sql = "select \"k\", count(*) as c\n" // 定义 SQL 查询字符串，按 k 分组并计算每组的数量
        + "from (\n" // 从子查询中查询
        + "  select \"k\", \"i\" from \"s\".\"beatles\" group by \"k\", \"i\") t\n" // 子查询：按 k 和 i 分组
        + "where \"k\" = 1940\n" // 过滤条件为 k = 1940
        + "group by \"k\""; // 按 k 分组
    final Table table = new BeatlesProjectableFilterableTable(buf, false); // 创建一个非协作模式的 BeatlesProjectableFilterableTable 表实例
    final String explain = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableAggregate(group=[{0}], C=[COUNT()])\n" // 第一层：外层分组聚合
        + "  EnumerableCalc(expr#0=[{inputs}], expr#1=[1940], k=[$t1], i=[$t0])\n" // 第二层：计算节点，添加常量 k=1940
        + "    EnumerableAggregate(group=[{1}])\n" // 第三层：内层分组聚合
        + "      EnumerableInterpreter\n" // 第四层：可枚举解释器
        + "        BindableTableScan(table=[[s, beatles]], filters=[[=($2, 1940)]], projects=[[2, 0]])"; // 第五层：可绑定表扫描，包含过滤条件和投影
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles" 的表
        .query(sql) // 执行查询
        .explainContains(explain) // 断言执行计划包含预期的字符串
        .returnsUnordered("k=1940; C=2"); // 断言查询结果包含 k=1940 的分组，数量为 2
  }

  private static @Nullable Pair<Integer, Object> getFilter(boolean cooperative, // 获取过滤条件的方法，返回列索引和值的键值对
      List<RexNode> filters) { // 参数：cooperative 表示是否协作模式，filters 是过滤条件列表
    final Iterator<RexNode> filterIter = filters.iterator(); // 创建过滤条件列表的迭代器
    while (filterIter.hasNext()) { // 遍历所有过滤条件
      final RexNode node = filterIter.next(); // 获取下一个过滤条件节点
      if (cooperative // 如果是协作模式
          && node instanceof RexCall // 并且节点是 RexCall 类型
          && ((RexCall) node).getOperator() == SqlStdOperatorTable.EQUALS // 并且操作符是等于号
          && ((RexCall) node).getOperands().get(0) instanceof RexInputRef // 并且第一个操作数是输入引用
          && ((RexCall) node).getOperands().get(1) instanceof RexLiteral) { // 并且第二个操作数是字面量
        filterIter.remove(); // 从过滤条件列表中移除该条件（因为已经下推）
        final int pos = ((RexInputRef) ((RexCall) node).getOperands().get(0)).getIndex(); // 获取列索引
        final RexLiteral op1 = (RexLiteral) ((RexCall) node).getOperands().get(1); // 获取字面量值
        switch (pos) { // 根据列索引处理不同类型的值
        case 0: // 第 0 列（i 列）
        case 2: // 第 2 列（k 列）
          return Pair.of(pos, ((BigDecimal) op1.getValue()).intValue()); // 返回整数类型的键值对
        case 1: // 第 1 列（j 列）
          return Pair.of(pos, ((NlsString) op1.getValue()).getValue()); // 返回字符串类型的键值对
        }
      }
    }
    return null; // 如果没有找到符合条件的过滤条件，返回 null
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-458">[CALCITE-458]
   * ArrayIndexOutOfBoundsException when using just a single column in
   * interpreter</a>. */ // 测试用例：修复 CALCITE-458 问题，在解释器中使用单列时出现数组越界异常
  @Test void testPFTableRefusesFilterSingleColumn() { // 测试可投影可过滤表在只投影单列时的行为
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final Table table = new BeatlesProjectableFilterableTable(buf, false); // 创建一个非协作模式的 BeatlesProjectableFilterableTable 表实例
    final String explain = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableInterpreter\n" // 第一层：可枚举解释器
        + "  BindableTableScan(table=[[s, beatles2]], filters=[[>($2, 1941)]], projects=[[2]])"; // 第二层：可绑定表扫描，包含过滤条件和投影
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles2", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles2" 的表
        .query("select \"k\" from \"s\".\"beatles2\" where \"k\" > 1941") // 执行查询，选择 k > 1941 的行，并且只投影 k 列
        .explainContains(explain) // 断言执行计划包含预期的字符串
        .returnsUnordered("k=1942", // 断言查询结果包含第一行的 k 列
            "k=1943"); // 断言查询结果包含第二行的 k 列
    assertThat(buf, hasToString("returnCount=4, projects=[2]")); // 断言表返回了 4 行（所有行），投影只包含 k 列
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3405">[CALCITE-3405]
   * Prune columns for ProjectableFilterable when project is not simple mapping</a>. */ // 测试用例：修复 CALCITE-3405 问题，当投影不是简单映射时剪列
  @Test void testPushNonSimpleMappingProject() { // 测试可投影可过滤表在投影不是简单映射时的列剪裁
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final Table table = new BeatlesProjectableFilterableTable(buf, true); // 创建一个协作模式的 BeatlesProjectableFilterableTable 表实例
    final String explain = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[+($t1, $t1)], expr#3=[3]," // 第一层：计算节点，包含表达式计算
        + " proj#0..1=[{exprs}], k0=[$t0], $f3=[$t2], $f4=[$t3])\n" // 投影表达式
        + "  EnumerableInterpreter\n" // 第二层：可枚举解释器
        + "    BindableTableScan(table=[[s, beatles]], projects=[[2, 0]])"; // 第三层：可绑定表扫描，只投影需要的列
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles" 的表
        .query("select \"k\", \"i\", \"k\", \"i\"+\"i\" \"ii\", 3 from \"s\".\"beatles\"") // 执行查询，包含重复列、表达式计算和常量
        .explainContains(explain) // 断言执行计划包含预期的字符串
        .returnsUnordered( // 断言查询结果包含四行数据
            "k=1940; i=4; k=1940; ii=8; EXPR$3=3", // 第一行数据
            "k=1940; i=5; k=1940; ii=10; EXPR$3=3", // 第二行数据
            "k=1942; i=4; k=1942; ii=8; EXPR$3=3", // 第三行数据
            "k=1943; i=6; k=1943; ii=12; EXPR$3=3"); // 第四行数据
    assertThat(buf, hasToString("returnCount=4, projects=[2, 0]")); // 断言表返回了 4 行，投影只包含 k 和 i 列（用于计算表达式）
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3405">[CALCITE-3405]
   * Prune columns for ProjectableFilterable when project is not simple mapping</a>. */ // 测试用例：修复 CALCITE-3405 问题，当投影是简单映射时剪列
  @Test void testPushSimpleMappingProject() { // 测试可投影可过滤表在投影是简单映射时的列剪裁
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final Table table = new BeatlesProjectableFilterableTable(buf, true); // 创建一个协作模式的 BeatlesProjectableFilterableTable 表实例
    // Note that no redundant Project on EnumerableInterpreter
    // 注意：EnumerableInterpreter 上没有冗余的 Project 节点
    final String explain = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableInterpreter\n" // 第一层：可枚举解释器
        + "  BindableTableScan(table=[[s, beatles]], projects=[[2, 0]])"; // 第二层：可绑定表扫描，只投影需要的列
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
        .with(newSchema("s", PairList.of("beatles", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles" 的表
        .query("select \"k\", \"i\" from \"s\".\"beatles\"") // 执行查询，只投影 k 和 i 列
        .explainContains(explain) // 断言执行计划包含预期的字符串
        .returnsUnordered( // 断言查询结果包含四行数据
            "k=1940; i=4", // 第一行数据
            "k=1940; i=5", // 第二行数据
            "k=1942; i=4", // 第三行数据
            "k=1943; i=6"); // 第四行数据
    assertThat(buf, hasToString("returnCount=4, projects=[2, 0]")); // 断言表返回了 4 行，投影只包含 k 和 i 列
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3479">[CALCITE-3479]
   * Stack overflow error thrown when running join query</a>
   * Test two ProjectableFilterableTable can join and produce right plan.
   */ // 测试用例：修复 CALCITE-3479 问题，运行连接查询时抛出栈溢出错误。测试两个 ProjectableFilterableTable 可以连接并生成正确的执行计划
  @Test void testProjectableFilterableTableJoin() { // 测试两个可投影可过滤表的连接查询
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final String explain = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableNestedLoopJoin(condition=[true], joinType=[inner])\n" // 第一层：嵌套循环连接
        + "  EnumerableInterpreter\n" // 第二层：左表的可枚举解释器
        + "    BindableTableScan(table=[[s, b1]], filters=[[=($0, 10)]])\n" // 第三层：左表的可绑定表扫描，包含过滤条件
        + "  EnumerableInterpreter\n" // 第四层：右表的可枚举解释器
        + "    BindableTableScan(table=[[s, b2]], filters=[[=($0, 10)]])"; // 第五层：右表的可绑定表扫描，包含过滤条件
    CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
            .with( // 注册模式
              newSchema("s", // 创建名为 "s" 的模式
                  PairList.<String, Table>builder() // 创建键值对列表构建器
                      .add("b1", // 添加第一个表
                          new BeatlesProjectableFilterableTable(buf, true)) // 创建协作模式的表实例
                      .add("b2", // 添加第二个表
                          new BeatlesProjectableFilterableTable(buf, true)) // 创建协作模式的表实例
                      .build())) // 构建键值对列表
            .query("select * from \"s\".\"b1\", \"s\".\"b2\" " // 执行连接查询
                    + "where \"s\".\"b1\".\"i\" = 10 and \"s\".\"b2\".\"i\" = 10 " // 过滤条件：两个表的 i 列都等于 10
                    + "and \"s\".\"b1\".\"i\" = \"s\".\"b2\".\"i\"") // 连接条件：两个表的 i 列相等
            .explainContains(explain); // 断言执行计划包含预期的字符串
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5019">[CALCITE-5019]
   * Avoid multiple scans when table is ProjectableFilterableTable</a>.*/
  @Test void testProjectableFilterableWithScanCounter() {
    final StringBuilder buf = new StringBuilder();
    final BeatlesProjectableFilterableTable table =
        new BeatlesProjectableFilterableTable(buf, false);
    final String explain = "PLAN="
        + "EnumerableInterpreter\n"
        + "  BindableTableScan(table=[[s, beatles]], filters=[[=($0, 4)]], projects=[[1]]";
    CalciteAssert.that()
        .with(newSchema("s", PairList.of("beatles", table)))
        .query("select \"j\" from \"s\".\"beatles\" where \"i\" = 4")
        .explainContains(explain)
        .returnsUnordered("j=John", "j=Paul");
    assertThat(table.getScanCount(), is(1));
    assertThat(buf, hasToString("returnCount=4, projects=[1, 0]"));
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1031">[CALCITE-1031]
   * In prepared statement, CsvScannableTable.scan is called twice</a>. */ // 测试用例：修复 CALCITE-1031 问题，在预编译语句中，CsvScannableTable.scan 被调用两次
  @Test void testPrepared2() throws SQLException { // 测试预编译语句的扫描次数，验证每次执行都会调用 scan 方法
    final Properties properties = new Properties(); // 创建属性对象用于配置连接
    properties.setProperty("caseSensitive", "true"); // 设置大小写敏感为 true
    try (Connection connection = // 创建 Calcite 连接
             DriverManager.getConnection("jdbc:calcite:", properties)) { // 使用 JDBC URL 和属性获取连接
      final CalciteConnection calciteConnection = // 解包为 CalciteConnection
          connection.unwrap(CalciteConnection.class); // 获取 CalciteConnection 实例

      final AtomicInteger scanCount = new AtomicInteger(); // 创建扫描计数器
      final AtomicInteger enumerateCount = new AtomicInteger(); // 创建枚举计数器
      final AtomicInteger closeCount = new AtomicInteger(); // 创建关闭计数器
      final Schema schema = // 创建模式
          new AbstractSchema() { // 创建抽象模式的匿名子类
            @Override protected Map<String, Table> getTableMap() { // 重写 getTableMap 方法
              return ImmutableMap.of("TENS", // 返回包含 TENS 表的不可变映射
                  countingTable(scanCount, enumerateCount, closeCount)); // 创建计数表，传入计数器
            }
          };
      calciteConnection.getRootSchema().add("TEST", schema); // 将模式添加到根模式中
      final String sql = "select * from \"TEST\".\"TENS\" where \"i\" < ?"; // 定义 SQL 查询语句，包含参数占位符
      final PreparedStatement statement = // 创建预编译语句
          calciteConnection.prepareStatement(sql); // 准备 SQL 语句
      assertThat(scanCount.get(), is(0)); // 断言扫描次数为 0
      assertThat(enumerateCount.get(), is(0)); // 断言枚举次数为 0

      // First execute
      // 第一次执行
      statement.setInt(1, 20); // 设置参数值为 20
      assertThat(scanCount.get(), is(0)); // 断言扫描次数仍为 0（准备阶段不扫描）
      ResultSet resultSet = statement.executeQuery(); // 执行查询
      assertThat(scanCount.get(), is(1)); // 断言扫描次数为 1
      assertThat(enumerateCount.get(), is(1)); // 断言枚举次数为 1
      assertThat(resultSet, // 断言结果集
          Matchers.returnsUnordered("i=0", "i=10")); // 包含两行数据
      assertThat(scanCount.get(), is(1)); // 断言扫描次数仍为 1
      assertThat(enumerateCount.get(), is(1)); // 断言枚举次数仍为 1

      // Second execute
      // 第二次执行
      resultSet = statement.executeQuery(); // 再次执行查询
      assertThat(scanCount.get(), is(2)); // 断言扫描次数为 2
      assertThat(resultSet, // 断言结果集
          Matchers.returnsUnordered("i=0", "i=10")); // 包含两行数据
      assertThat(scanCount.get(), is(2)); // 断言扫描次数仍为 2

      // Third execute
      // 第三次执行
      statement.setInt(1, 30); // 修改参数值为 30
      resultSet = statement.executeQuery(); // 再次执行查询
      assertThat(scanCount.get(), is(3)); // 断言扫描次数为 3
      assertThat(resultSet, // 断言结果集
          Matchers.returnsUnordered("i=0", "i=10", "i=20")); // 包含三行数据
      assertThat(scanCount.get(), is(3)); // 断言扫描次数仍为 3
    } // 自动关闭连接
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3758">[CALCITE-3758]
   * FilterTableScanRule generate wrong mapping for filter condition
   * when underlying is BindableTableScan</a>. */ // 测试用例：修复 CALCITE-3758 问题，当底层是 BindableTableScan 时，FilterTableScanRule 为过滤条件生成错误的映射
  @Test void testPFTableInBindableConvention() { // 测试可投影可过滤表在 Bindable 约定下的行为
    final StringBuilder buf = new StringBuilder(); // 创建 StringBuilder 用于记录表的执行信息
    final Table table = new BeatlesProjectableFilterableTable(buf, true); // 创建一个协作模式的 BeatlesProjectableFilterableTable 表实例
    try (Hook.Closeable ignored = Hook.ENABLE_BINDABLE.addThread(Hook.propertyJ(true))) { // 启用 Bindable 约定的钩子
      final String explain = "PLAN=" // 定义预期的执行计划字符串
          + "BindableTableScan(table=[[s, beatles]], filters=[[=($1, 'John')]], projects=[[1]])"; // 可绑定表扫描，包含过滤条件和投影
      CalciteAssert.that() // 创建 CalciteAssert 测试工具实例
          .with(newSchema("s", PairList.of("beatles", table))) // 注册一个名为 "s" 的模式，包含一个名为 "beatles" 的表
          .query("select \"j\" from \"s\".\"beatles\" where \"j\" = 'John'") // 执行查询，选择 j = 'John' 的行，并且只投影 j 列
          .explainContains(explain) // 断言执行计划包含预期的字符串
          .returnsUnordered("j=John"); // 断言查询结果包含一行数据
      assertThat(buf, // 断言表的执行信息
          hasToString("returnCount=1, filter=<1, John>, projects=[1]")); // 返回 1 行，过滤被下推，投影被下推
    } // 自动关闭钩子
  }

  protected ConnectionPostProcessor newSchema(final String schemaName, // 创建模式的后处理器方法
      PairList<String, Table> tables) { // 参数：schemaName 是模式名称，tables 是表名到表实例的映射
    return connection -> { // 返回一个连接后处理器
      CalciteConnection con = connection.unwrap(CalciteConnection.class); // 解包为 CalciteConnection
      SchemaPlus rootSchema = con.getRootSchema(); // 获取根模式
      SchemaPlus schema = rootSchema.add(schemaName, new AbstractSchema()); // 添加新模式到根模式
      tables.forEach(schema::add); // 将所有表添加到模式中
      connection.setSchema(schemaName); // 设置连接的默认模式
      return connection; // 返回连接
    };
  }

  /** Table that returns one column via the {@link ScannableTable} interface. */ // 通过 ScannableTable 接口返回一列的表
  public static class SimpleTable extends AbstractTable // SimpleTable 类，继承自 AbstractTable 并实现 ScannableTable 接口
      implements ScannableTable { // 实现 ScannableTable 接口，表示可扫描的表
    public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 获取行类型的方法，定义表的结构
      return typeFactory.builder() // 使用类型工厂创建构建器
          .add("i", SqlTypeName.INTEGER) // 添加一列，列名为 "i"，类型为 INTEGER
          .build(); // 构建行类型
    }

    public Enumerable<@Nullable Object[]> scan(DataContext root) { // 扫描表数据的方法，返回可枚举的对象数组
      return new AbstractEnumerable<Object[]>() { // 返回一个抽象的可枚举对象
        public Enumerator<Object[]> enumerator() { // 实现枚举器方法
          return tens(); // 返回 tens() 方法创建的枚举器
        }
      };
    }

  }

  /** Table that returns two columns via the ScannableTable interface. */ // 通过 ScannableTable 接口返回三列的表
  public static class BeatlesTable extends AbstractTable // BeatlesTable 类，继承自 AbstractTable 并实现 ScannableTable 接口
      implements ScannableTable { // 实现 ScannableTable 接口，表示可扫描的表
    public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 获取行类型的方法，定义表的结构
      return typeFactory.builder() // 使用类型工厂创建构建器
          .add("i", SqlTypeName.INTEGER) // 添加第一列，列名为 "i"，类型为 INTEGER
          .add("j", SqlTypeName.VARCHAR) // 添加第二列，列名为 "j"，类型为 VARCHAR
          .add("k", SqlTypeName.INTEGER) // 添加第三列，列名为 "k"，类型为 INTEGER
          .build(); // 构建行类型
    }

    public Enumerable<@Nullable Object[]> scan(DataContext root) { // 扫描表数据的方法，返回可枚举的对象数组
      return new AbstractEnumerable<Object[]>() { // 返回一个抽象的可枚举对象
        public Enumerator<Object[]> enumerator() { // 实现枚举器方法
          return beatles(new StringBuilder(), null, null); // 返回 beatles() 方法创建的枚举器，不使用过滤和投影
        }
      };
    }
  }

  /** Table that returns two columns via the {@link FilterableTable}
   * interface. */ // 通过 FilterableTable 接口返回三列的表
  public static class BeatlesFilterableTable extends AbstractTable // BeatlesFilterableTable 类，继承自 AbstractTable 并实现 FilterableTable 接口
      implements FilterableTable { // 实现 FilterableTable 接口，表示可过滤的表
    private final StringBuilder buf; // 成员变量：StringBuilder 用于记录执行信息
    private final boolean cooperative; // 成员变量：布尔值，表示是否为协作模式

    public BeatlesFilterableTable(StringBuilder buf, boolean cooperative) { // 构造方法，初始化成员变量
      this.buf = buf; // 保存 StringBuilder 引用
      this.cooperative = cooperative; // 保存协作模式标志
    }

    public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 获取行类型的方法，定义表的结构
      return typeFactory.builder() // 使用类型工厂创建构建器
          .add("i", SqlTypeName.INTEGER) // 添加第一列，列名为 "i"，类型为 INTEGER
          .add("j", SqlTypeName.VARCHAR) // 添加第二列，列名为 "j"，类型为 VARCHAR
          .add("k", SqlTypeName.INTEGER) // 添加第三列，列名为 "k"，类型为 INTEGER
          .build(); // 构建行类型
    }

    public Enumerable<@Nullable Object[]> scan(DataContext root, List<RexNode> filters) { // 扫描表数据的方法，接受过滤条件列表
      final Pair<Integer, Object> filter = getFilter(cooperative, filters); // 获取可下推的过滤条件
      return new AbstractEnumerable<Object[]>() { // 返回一个抽象的可枚举对象
        public Enumerator<Object[]> enumerator() { // 实现枚举器方法
          return beatles(buf, filter, null); // 返回 beatles() 方法创建的枚举器，使用过滤条件但不使用投影
        }
      };
    }
  }

  /** Table that returns two columns via the {@link FilterableTable}
   * interface. */ // 通过 ProjectableFilterableTable 接口返回三列的表
  public static class BeatlesProjectableFilterableTable // BeatlesProjectableFilterableTable 类，继承自 AbstractTable 并实现 ProjectableFilterableTable 接口
      extends AbstractTable implements ProjectableFilterableTable { // 实现 ProjectableFilterableTable 接口，表示可投影和可过滤的表
    private final AtomicInteger scanCounter = new AtomicInteger(); // 成员变量：原子整数，用于记录扫描次数
    private final StringBuilder buf; // 成员变量：StringBuilder 用于记录执行信息
    private final boolean cooperative; // 成员变量：布尔值，表示是否为协作模式

    BeatlesProjectableFilterableTable(StringBuilder buf, // 构造方法，初始化成员变量
        boolean cooperative) { // 参数：buf 用于记录执行信息，cooperative 表示是否为协作模式
      this.buf = buf; // 保存 StringBuilder 引用
      this.cooperative = cooperative; // 保存协作模式标志
    }

    public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 获取行类型的方法，定义表的结构
      return typeFactory.builder() // 使用类型工厂创建构建器
          .add("i", SqlTypeName.INTEGER) // 添加第一列，列名为 "i"，类型为 INTEGER
          .add("j", SqlTypeName.VARCHAR) // 添加第二列，列名为 "j"，类型为 VARCHAR
          .add("k", SqlTypeName.INTEGER) // 添加第三列，列名为 "k"，类型为 INTEGER
          .build(); // 构建行类型
    }

    public Enumerable<@Nullable Object[]> scan(DataContext root, List<RexNode> filters, // 扫描表数据的方法，接受过滤条件列表和投影数组
        final int @Nullable [] projects) { // 参数：root 是数据上下文，filters 是过滤条件列表，projects 是投影列索引数组
      scanCounter.incrementAndGet(); // 扫描计数器加 1
      final Pair<Integer, Object> filter = getFilter(cooperative, filters); // 获取可下推的过滤条件
      return new AbstractEnumerable<Object[]>() { // 返回一个抽象的可枚举对象
        public Enumerator<Object[]> enumerator() { // 实现枚举器方法
          return beatles(buf, filter, projects); // 返回 beatles() 方法创建的枚举器，使用过滤条件和投影
        }
      };
    }

    public int getScanCount() { // 获取扫描次数的方法
      return this.scanCounter.get(); // 返回扫描计数器的当前值
    }
  }

  private static Enumerator<Object[]> tens() { // 创建一个枚举器，返回 0, 10, 20, 30 四个值
    return new Enumerator<Object[]>() { // 返回一个匿名枚举器实例
      int row = -1; // 成员变量：当前行索引，初始值为 -1
      Object @Nullable[] current; // 成员变量：当前行的数据数组

      public Object[] current() { // 获取当前行数据的方法
        return requireNonNull(current, "current"); // 返回当前行数据，确保不为 null
      }

      public boolean moveNext() { // 移动到下一行的方法
        if (++row < 4) { // 如果行索引小于 4（还有数据）
          current = new Object[] {row * 10}; // 设置当前行数据为行索引乘以 10
          return true; // 返回 true 表示成功移动到下一行
        } else { // 如果行索引大于等于 4（没有数据了）
          return false; // 返回 false 表示已经到达末尾
        }
      }

      public void reset() { // 重置枚举器的方法
        row = -1; // 将行索引重置为 -1
      }

      public void close() { // 关闭枚举器的方法
        current = null; // 将当前行数据设置为 null，释放资源
      }
    };
  }

  /** Returns a table that counts the number of calls to
   * {@link ScannableTable#scan}, {@link Enumerable#enumerator()},
   * and {@link Enumerator#close()}. */ // 返回一个表，用于统计对 scan、enumerator 和 close 方法的调用次数
  static SimpleTable countingTable(AtomicInteger scanCount, // countingTable 方法，创建一个计数表
      AtomicInteger enumerateCount, AtomicInteger closeCount) { // 参数：scanCount 是扫描计数器，enumerateCount 是枚举计数器，closeCount 是关闭计数器
    return new SimpleTable() { // 返回一个 SimpleTable 的匿名子类实例
      private Enumerable<Object[]> superScan(DataContext root) { // 私有方法，调用父类的 scan 方法
        return super.scan(root); // 调用父类的 scan 方法并返回结果
      }

      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写 scan 方法
        scanCount.incrementAndGet(); // 扫描计数器加 1
        return new AbstractEnumerable<Object[]>() { // 返回一个抽象的可枚举对象
          @Override public Enumerator<Object[]> enumerator() { // 重写枚举器方法
            enumerateCount.incrementAndGet(); // 枚举计数器加 1
            final Enumerator<Object[]> enumerator = // 获取父类的枚举器
                superScan(root).enumerator(); // 调用 superScan 获取枚举器
            return new DelegatingEnumerator<Object[]>(enumerator) { // 返回一个委托枚举器
              @Override public void close() { // 重写 close 方法
                closeCount.incrementAndGet(); // 关闭计数器加 1
                super.close(); // 调用父类的 close 方法
              }
            };
          }
        };
      }
    };
  }

  private static final Object[][] BEATLES = { // 静态常量：Beatles 乐队的成员数据数组
      {4, "John", 1940}, // 第一行数据：i=4, j=John, k=1940
      {4, "Paul", 1942}, // 第二行数据：i=4, j=Paul, k=1942
      {6, "George", 1943}, // 第三行数据：i=6, j=George, k=1943
      {5, "Ringo", 1940} // 第四行数据：i=5, j=Ringo, k=1940
  };

  private static Enumerator<Object[]> beatles(final StringBuilder buf, // beatles 方法，创建一个枚举器，返回 Beatles 数据
      @Nullable final Pair<Integer, Object> filter, // 参数：buf 用于记录执行信息，filter 是过滤条件
      final int @Nullable[] projects) { // 参数：projects 是投影列索引数组
    return new Enumerator<Object[]>() { // 返回一个匿名枚举器实例
      int row = -1; // 成员变量：当前行索引，初始值为 -1
      int returnCount = 0; // 成员变量：返回行数计数器，初始值为 0
      Object @Nullable[] current; // 成员变量：当前行的数据数组

      public Object[] current() { // 获取当前行数据的方法
        return requireNonNull(current, "current"); // 返回当前行数据，确保不为 null
      }

      public boolean moveNext() { // 移动到下一行的方法
        while (++row < 4) { // 循环遍历所有行，直到行索引大于等于 4
          Object[] current = BEATLES[row % 4]; // 获取当前行的数据（使用取模运算防止越界）
          if (filter == null || filter.right.equals(current[filter.left])) { // 如果没有过滤条件，或者当前行满足过滤条件
            if (projects == null) { // 如果没有投影
              this.current = current; // 直接使用当前行的数据
            } else { // 如果有投影
              Object[] newCurrent = new Object[projects.length]; // 创建一个新的数组用于存储投影后的数据
              for (int i = 0; i < projects.length; i++) { // 遍历所有投影列索引
                newCurrent[i] = current[projects[i]]; // 将投影列的数据复制到新数组中
              }
              this.current = newCurrent; // 使用投影后的数据
            }
            ++returnCount; // 返回行数计数器加 1
            return true; // 返回 true 表示成功移动到下一行
          }
        }
        return false; // 返回 false 表示已经到达末尾
      }

      public void reset() { // 重置枚举器的方法
        row = -1; // 将行索引重置为 -1
      }

      public void close() { // 关闭枚举器的方法
        current = null; // 将当前行数据设置为 null，释放资源
        buf.append("returnCount=").append(returnCount); // 将返回行数记录到 StringBuilder 中
        if (filter != null) { // 如果有过滤条件
          buf.append(", filter=").append(filter); // 将过滤条件记录到 StringBuilder 中
        }
        if (projects != null) { // 如果有投影
          buf.append(", projects=").append(Arrays.toString(projects)); // 将投影列索引数组记录到 StringBuilder 中
        }
      }
    };
  }
} // ScannableTableTest 类结束
