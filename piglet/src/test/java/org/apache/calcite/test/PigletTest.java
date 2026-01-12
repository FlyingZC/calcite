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
package org.apache.calcite.test; // 声明包名，该类属于 org.apache.calcite.test 包，是 Calcite 测试套件的一部分

import org.apache.calcite.piglet.parser.ParseException; // 导入 Piglet 解析异常类，用于处理 Pig 脚本解析时的错误

import org.junit.jupiter.api.Disabled; // 导入 JUnit 5 的 Disabled 注解，用于标记禁用的测试方法
import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

/** Unit tests for Piglet. */ // 类文档注释：Piglet 的单元测试类
// Piglet 是 Apache Calcite 的一个子模块，用于支持 Apache Pig Latin 语言的解析和执行
// 该测试类包含了对 Piglet 各种功能的测试用例，包括 LOAD、FILTER、GROUP、ORDER、LIMIT、FOREACH、DISTINCT、VALUES 等操作
// 测试覆盖了 Pig 脚本的解析、转换为 Calcite 关系代数、执行计划生成以及实际执行等功能
class PigletTest { // PigletTest 类定义，用于测试 Piglet 功能的单元测试类
  private static Fluent pig(String pig) { // 静态工厂方法，创建 Fluent 对象，Fluent 是一个流式 API 的辅助类
    // 参数 pig 是要测试的 Pig Latin 脚本字符串
    // 返回 Fluent 对象，该对象提供链式调用的方法来进行断言和验证
    return new Fluent(pig); // 创建并返回新的 Fluent 实例，封装了 Pig 脚本字符串
  }

  @Test void testParseLoad() throws ParseException { // 测试方法：测试 LOAD 语句的解析功能，使用 @Test 注解标记为 JUnit 测试方法
    // throws ParseException 表示该方法可能抛出解析异常
    final String s = "A = LOAD 'Emp';"; // 定义要解析的 Pig Latin 脚本字符串，这是一个 LOAD 语句，将名为 'Emp' 的数据加载到关系 A 中
    final String expected = "{op: PROGRAM, stmts: [\n" // 定义期望的解析结果，使用 JSON 格式表示解析后的抽象语法树（AST）
        + "  {op: LOAD, target: A, name: Emp}]}"; // 期望解析结果包含一个 PROGRAM 操作，其中包含一个 LOAD 语句，目标关系是 A，数据源名称是 Emp
    pig(s).parseContains(expected); // 调用 Fluent 对象的 parseContains 方法，验证解析结果是否包含期望的字符串
    // pig(s) 创建 Fluent 对象封装 Pig 脚本，parseContains(expected) 验证解析后的 AST 是否包含期望的内容
  }

  /** Tests parsing and un-parsing all kinds of operators. */ // 方法文档注释：测试解析和反解析各种操作符
  // 该测试方法验证 Piglet 解析器能否正确解析各种 Pig Latin 操作符，包括 LOAD、DESCRIBE、DUMP、FOREACH、FILTER、DISTINCT、ORDER、LIMIT、GROUP 等
  // 同时也测试嵌套操作（如 FOREACH 中的嵌套操作）和多字段分组
  @Test void testParse2() throws ParseException { // 测试方法：测试各种操作符的解析和反解析功能
    final String s = "A = LOAD 'Emp';\n" // 定义包含多种操作的 Pig Latin 脚本字符串，从加载 'Emp' 数据开始
        + "DESCRIBE A;\n" // DESCRIBE 语句：描述关系 A 的结构（字段信息）
        + "DUMP A;\n" // DUMP 语句：输出关系 A 的内容到控制台
        + "B = FOREACH A GENERATE 1, name;\n" // FOREACH 语句：遍历关系 A 的每一条记录，生成新的关系 B，包含常量 1 和字段 name
        + "B1 = FOREACH A {\n" // 嵌套 FOREACH 语句：在 FOREACH 块内部执行多个操作
        + "  X = DISTINCT A;\n" // 在嵌套块中执行 DISTINCT 操作，对关系 A 去重，结果存储到 X
        + "  Y = FILTER X BY foo;\n" // 在嵌套块中执行 FILTER 操作，过滤关系 X，条件是 foo 字段
        + "  Z = LIMIT Z 3;\n" // 在嵌套块中执行 LIMIT 操作，限制关系 Z 的行数为 3（注意这里 Z 引用自身，可能是个 bug）
        + "  GENERATE 1, name;\n" // 在嵌套块中生成输出，包含常量 1 和字段 name
        + "}\n" // 结束嵌套 FOREACH 块
        + "C = FILTER B BY name;\n" // FILTER 语句：过滤关系 B，条件是 name 字段
        + "D = DISTINCT C;\n" // DISTINCT 语句：对关系 C 去重，结果存储到 D
        + "E = ORDER D BY $1 DESC, $2 ASC, $3;\n" // ORDER 语句：对关系 D 排序，按第 1 列降序、第 2 列升序、第 3 列默认排序
        + "F = ORDER E BY * DESC;\n" // ORDER 语句：对关系 E 的所有列按降序排序（* 表示所有列）
        + "G = LIMIT F -10;\n" // LIMIT 语句：限制关系 F 的行数为 -10（负数通常表示从末尾开始，具体语义取决于实现）
        + "H = GROUP G ALL;\n" // GROUP 语句：将关系 G 的所有记录分组到一个组中（ALL 表示全局分组）
        + "I = GROUP H BY e;\n" // GROUP 语句：按字段 e 对关系 H 进行分组
        + "J = GROUP I BY (e1, e2);\n"; // GROUP 语句：按字段 e1 和 e2 的组合对关系 I 进行分组
    final String expected = "{op: PROGRAM, stmts: [\n" // 定义期望的解析结果，使用 JSON 格式表示解析后的抽象语法树（AST）
        + "  {op: LOAD, target: A, name: Emp},\n" // 期望解析结果包含 LOAD 操作，目标关系是 A，数据源名称是 Emp
        + "  {op: DESCRIBE, relation: A},\n" // 期望解析结果包含 DESCRIBE 操作，描述的关系是 A
        + "  {op: DUMP, relation: A},\n" // 期望解析结果包含 DUMP 操作，输出的关系是 A
        + "  {op: FOREACH, target: B, source: A, expList: [\n" // 期望解析结果包含 FOREACH 操作，目标关系是 B，源关系是 A，表达式列表包含
        + "    1,\n" // 常量 1
        + "    name]},\n" // 字段 name
        + "  {op: FOREACH, target: B1, source: A, nestedOps: [\n" // 期望解析结果包含嵌套 FOREACH 操作，目标关系是 B1，源关系是 A，嵌套操作列表包含
        + "    {op: DISTINCT, target: X, source: A},\n" // 嵌套的 DISTINCT 操作，目标关系是 X，源关系是 A
        + "    {op: FILTER, target: Y, source: X, condition: foo},\n" // 嵌套的 FILTER 操作，目标关系是 Y，源关系是 X，条件是 foo
        + "    {op: LIMIT, target: Z, source: Z, count: 3}], expList: [\n" // 嵌套的 LIMIT 操作，目标关系是 Z，源关系是 Z，限制行数为 3，表达式列表包含
        + "    1,\n" // 常量 1
        + "    name]},\n" // 字段 name
        + "  {op: FILTER, target: C, source: B, condition: name},\n" // 期望解析结果包含 FILTER 操作，目标关系是 C，源关系是 B，条件是 name
        + "  {op: DISTINCT, target: D, source: C},\n" // 期望解析结果包含 DISTINCT 操作，目标关系是 D，源关系是 C
        + "  {op: ORDER, target: E, source: D},\n" // 期望解析结果包含 ORDER 操作，目标关系是 E，源关系是 D
        + "  {op: ORDER, target: F, source: E},\n" // 期望解析结果包含 ORDER 操作，目标关系是 F，源关系是 E
        + "  {op: LIMIT, target: G, source: F, count: -10},\n" // 期望解析结果包含 LIMIT 操作，目标关系是 G，源关系是 F，限制行数为 -10
        + "  {op: GROUP, target: H, source: G},\n" // 期望解析结果包含 GROUP 操作，目标关系是 H，源关系是 G（全局分组）
        + "  {op: GROUP, target: I, source: H, keys: [\n" // 期望解析结果包含 GROUP 操作，目标关系是 I，源关系是 H，分组键列表包含
        + "    e]},\n" // 字段 e
        + "  {op: GROUP, target: J, source: I, keys: [\n" // 期望解析结果包含 GROUP 操作，目标关系是 J，源关系是 I，分组键列表包含
        + "    e1,\n" // 字段 e1
        + "    e2]}]}"; // 字段 e2
    pig(s).parseContains(expected); // 调用 Fluent 对象的 parseContains 方法，验证解析结果是否包含期望的字符串
  }

  @Test void testScan() throws ParseException { // 测试方法：测试表扫描功能，验证 LOAD 语句能否正确转换为 Calcite 的 LogicalTableScan 操作
    final String s = "A = LOAD 'EMP';"; // 定义 Pig Latin 脚本字符串，加载 'EMP' 表到关系 A，这里 'EMP' 是一个表名，会映射到 scott.EMP 表
    final String expected = "LogicalTableScan(table=[[scott, EMP]])\n"; // 定义期望的执行计划，应该生成 LogicalTableScan 操作，扫描 scott 模式下的 EMP 表
    pig(s).explainContains(expected); // 调用 Fluent 对象的 explainContains 方法，验证执行计划是否包含期望的 LogicalTableScan 操作
  }

  @Test void testDump() throws ParseException { // 测试方法：测试 DUMP 语句的功能，验证能否正确输出关系的内容
    final String s = "A = LOAD 'DEPT';\n" // 定义 Pig Latin 脚本字符串，加载 'DEPT' 表到关系 A
        + "DUMP A;"; // DUMP 语句：输出关系 A 的内容到控制台
    final String expected = "LogicalTableScan(table=[[scott, DEPT]])\n"; // 定义期望的执行计划，应该生成 LogicalTableScan 操作，扫描 scott 模式下的 DEPT 表
    final String out = "(10,ACCOUNTING,NEW YORK)\n" // 定义期望的输出结果，DEPT 表包含 4 条记录，每条记录有 3 个字段
        + "(20,RESEARCH,DALLAS)\n" // 第 2 条记录：部门编号 20，部门名称 RESEARCH，地点 DALLAS
        + "(30,SALES,CHICAGO)\n" // 第 3 条记录：部门编号 30，部门名称 SALES，地点 CHICAGO
        + "(40,OPERATIONS,BOSTON)\n"; // 第 4 条记录：部门编号 40，部门名称 OPERATIONS，地点 BOSTON
    pig(s).explainContains(expected).returns(out); // 调用 Fluent 对象的方法链，先验证执行计划，再验证输出结果
  }

  /** VALUES is an extension to Pig. You can achieve the same effect in standard
   * Pig by creating a text file. */ // 方法文档注释：VALUES 是 Pig 的扩展功能，在标准 Pig 中可以通过创建文本文件实现相同效果
  // VALUES 语句允许直接在 Pig 脚本中定义常量数据，而不需要从外部文件加载
  @Test void testDumpValues() throws ParseException { // 测试方法：测试 VALUES 语句和 DUMP 语句的组合使用
    final String s = "A = VALUES (1, 'a'), (2, 'b') AS (x: int, y: string);\n" // 定义 Pig Latin 脚本字符串，使用 VALUES 语句创建包含两行数据的关系 A
        // (1, 'a') 和 (2, 'b') 是两行数据，每行包含一个整数和一个字符串
        // AS (x: int, y: string) 定义了列名和类型，x 是整数类型，y 是字符串类型
        + "DUMP A;"; // DUMP 语句：输出关系 A 的内容到控制台
    final String expected = // 定义期望的执行计划
        "LogicalValues(tuples=[[{ 1, 'a' }, { 2, 'b' }]])\n"; // 应该生成 LogicalValues 操作，包含两行数据 { 1, 'a' } 和 { 2, 'b' }
    final String out = "(1,a)\n(2,b)\n"; // 定义期望的输出结果，每行数据用括号括起来，字段用逗号分隔
    pig(s).explainContains(expected).returns(out); // 调用 Fluent 对象的方法链，先验证执行计划，再验证输出结果
  }

  @Test void testForeach() throws ParseException { // 测试方法：测试 FOREACH 语句的功能，验证能否正确生成投影操作
    final String s = "A = LOAD 'DEPT';\n" // 定义 Pig Latin 脚本字符串，加载 'DEPT' 表到关系 A
        + "B = FOREACH A GENERATE DNAME, $2;"; // FOREACH 语句：遍历关系 A 的每一条记录，生成新的关系 B
    // GENERATE 子句指定输出的字段：DNAME（通过字段名引用）和 $2（通过位置引用，即第 3 列，对应 LOC 字段）
    final String expected = "LogicalProject(DNAME=[$1], LOC=[$2])\n" // 定义期望的执行计划，应该生成 LogicalProject 操作
        // DNAME=[$1] 表示输出 DNAME 字段，对应输入的第 2 列（索引为 1）
        // LOC=[$2] 表示输出 LOC 字段，对应输入的第 3 列（索引为 2）
        + "  LogicalTableScan(table=[[scott, DEPT]])\n"; // LogicalProject 的输入是 LogicalTableScan 操作，扫描 scott 模式下的 DEPT 表
    pig(s).explainContains(expected); // 调用 Fluent 对象的 explainContains 方法，验证执行计划是否包含期望的 LogicalProject 操作
  }

  @Disabled // foreach nested not implemented yet // @Disabled 注解标记该测试方法被禁用，因为嵌套 FOREACH 功能尚未实现
  @Test void testForeachNested() throws ParseException { // 测试方法：测试嵌套 FOREACH 语句的功能（当前被禁用）
    // 嵌套 FOREACH 允许在 FOREACH 块内部执行多个操作，如 ORDER、LIMIT、FILTER 等
    // 这个测试用例演示了如何对每个分组进行排序和限制操作
    final String s = "A = LOAD 'EMP';\n" // 定义 Pig Latin 脚本字符串，加载 'EMP' 表到关系 A
        + "B = GROUP A BY DEPTNO;\n" // GROUP 语句：按 DEPTNO 字段对关系 A 进行分组，结果存储到关系 B
        + "C = FOREACH B {\n" // 嵌套 FOREACH 语句：遍历关系 B 的每个分组，在分组内部执行多个操作
        + "  D = ORDER A BY SAL DESC;\n" // 在分组内部，对关系 A 按 SAL 字段降序排序，结果存储到 D
        + "  E = LIMIT D 3;\n" // 在分组内部，限制关系 D 的行数为 3，即取每个部门薪资最高的 3 个员工
        + "  GENERATE E.DEPTNO, E.EMPNO;\n" // 生成输出，包含 E 关系的 DEPTNO 和 EMPNO 字段
        + "}"; // 结束嵌套 FOREACH 块
    final String expected = "LogicalProject(DNAME=[$1], LOC=[$2])\n" // 定义期望的执行计划（注意：这个期望值可能不正确，因为测试被禁用）
        + "  LogicalTableScan(table=[[scott, DEPT]])\n"; // 期望的执行计划扫描 scott 模式下的 DEPT 表
    pig(s).explainContains(expected); // 调用 Fluent 对象的 explainContains 方法，验证执行计划是否包含期望的操作
  }

  @Test void testGroup() throws ParseException { // 测试方法：测试 GROUP 语句的功能，验证能否正确转换为 Calcite 的聚合操作
    final String s = "A = LOAD 'EMP';\n" // 定义 Pig Latin 脚本字符串，加载 'EMP' 表到关系 A
        + "B = GROUP A BY DEPTNO;"; // GROUP 语句：按 DEPTNO 字段对关系 A 进行分组，结果存储到关系 B
    // 分组操作会将相同 DEPTNO 的记录聚合成一个组，每个组包含一个分组键和对应的记录集合
    final String expected = // 定义期望的执行计划
        + "LogicalAggregate(group=[{0}], A=[COLLECT($1)])\n" // 应该生成 LogicalAggregate 操作，group=[{0}] 表示按第 0 列分组，A=[COLLECT($1)] 表示收集第 1 列的值到一个集合中
        + "  LogicalProject(DEPTNO=[$7], $f8=[ROW($0, $1, $2, $3, $4, $5, $6, $7)])\n" // LogicalProject 操作，输出 DEPTNO 字段（第 8 列，索引为 7）和一个包含所有字段的行（$f8）
        // ROW($0, $1, $2, $3, $4, $5, $6, $7) 创建一个包含所有 8 个字段的行
        + "    LogicalTableScan(table=[[scott, EMP]])\n"; // LogicalProject 的输入是 LogicalTableScan 操作，扫描 scott 模式下的 EMP 表
    pig(s).explainContains(expected); // 调用 Fluent 对象的 explainContains 方法，验证执行计划是否包含期望的操作
  }

  @Test void testGroupExample() throws ParseException { // 测试方法：测试 GROUP 语句的实际执行效果
    // 该测试使用 VALUES 语句创建测试数据，然后按年龄分组，验证分组结果是否正确
    final String pre = "A = VALUES ('John',18,4.0F),\n" // 定义前缀部分，使用 VALUES 语句创建包含 4 条记录的关系 A
        + "('Mary',19,3.8F),\n" // 第 2 条记录：Mary，19 岁，GPA 3.8
        + "('Bill',20,3.9F),\n" // 第 3 条记录：Bill，20 岁，GPA 3.9
        + "('Joe',18,3.8F) AS (name:chararray,age:int,gpa:float);\n"; // 第 4 条记录：Joe，18 岁，GPA 3.8；定义列名和类型
    final String b = pre // 定义完整的 Pig Latin 脚本
        + "B = GROUP A BY age;\n" // GROUP 语句：按 age 字段对关系 A 进行分组，结果存储到关系 B
        + "DUMP B;"; // DUMP 语句：输出关系 B 的内容到控制台
    pig(b).returnsUnordered( // 调用 Fluent 对象的 returnsUnordered 方法，验证输出结果（不关心顺序）
        "(18,{(John,18,4.0F),(Joe,18,3.8F)})", // 期望输出：18 岁组包含 John 和 Joe 两条记录
        "(19,{(Mary,19,3.8F)})", // 期望输出：19 岁组包含 Mary 一条记录
        "(20,{(Bill,20,3.9F)})"); // 期望输出：20 岁组包含 Bill 一条记录
  }

  @Test void testDistinctExample() throws ParseException { // 测试方法：测试 DISTINCT 语句的实际执行效果
    // 该测试使用 VALUES 语句创建包含重复记录的测试数据，然后使用 DISTINCT 去重，验证去重结果是否正确
    final String pre = "A = VALUES (8,3,4),\n" // 定义前缀部分，使用 VALUES 语句创建包含 5 条记录的关系 A，其中包含重复记录
        + "(1,2,3),\n" // 第 2 条记录：(1,2,3)
        + "(4,3,3),\n" // 第 3 条记录：(4,3,3)
        + "(4,3,3),\n" // 第 4 条记录：(4,3,3)，与第 3 条记录重复
        + "(1,2,3) AS (a1:int,a2:int,a3:int);\n"; // 第 5 条记录：(1,2,3)，与第 2 条记录重复；定义列名和类型
    final String x = pre // 定义完整的 Pig Latin 脚本
        + "X = DISTINCT A;\n" // DISTINCT 语句：对关系 A 进行去重操作，结果存储到关系 X
        + "DUMP X;\n"; // DUMP 语句：输出关系 X 的内容到控制台
    pig(x).returnsUnordered("(1,2,3)", // 调用 Fluent 对象的 returnsUnordered 方法，验证输出结果（不关心顺序）
        "(4,3,3)", // 期望输出：(4,3,3)，去重后只保留一条
        "(8,3,4)"); // 期望输出：(8,3,4)，去重后只保留一条
  }

  @Test void testFilter() throws ParseException { // 测试方法：测试 FILTER 语句的功能，验证能否正确转换为 Calcite 的过滤操作
    final String s = "A = LOAD 'DEPT';\n" // 定义 Pig Latin 脚本字符串，加载 'DEPT' 表到关系 A
        + "B = FILTER A BY DEPTNO;"; // FILTER 语句：过滤关系 A，条件是 DEPTNO 字段（非空或非零值）
    // 在 Pig 中，FILTER BY 后面跟一个布尔表达式，如果表达式为 true，则保留该记录
    // 这里 DEPTNO 作为一个布尔条件，相当于 DEPTNO IS NOT NULL AND DEPTNO != 0
    final String expected = "LogicalFilter(condition=[$0])\n" // 定义期望的执行计划，应该生成 LogicalFilter 操作
        // condition=[$0] 表示过滤条件是第 0 列（即 DEPTNO 字段）
        + "  LogicalTableScan(table=[[scott, DEPT]])\n"; // LogicalFilter 的输入是 LogicalTableScan 操作，扫描 scott 模式下的 DEPT 表
    pig(s).explainContains(expected); // 调用 Fluent 对象的 explainContains 方法，验证执行计划是否包含期望的 LogicalFilter 操作
  }

  @Test void testFilterExample() throws ParseException { // 测试方法：测试 FILTER 语句的实际执行效果，包括简单条件和复杂条件
    // 该测试使用 VALUES 语句创建测试数据，然后使用 FILTER 进行过滤，验证过滤结果是否正确
    final String pre = "A = VALUES (1,2,3),\n" // 定义前缀部分，使用 VALUES 语句创建包含 6 条记录的关系 A
        + "(4,2,1),\n" // 第 2 条记录：(4,2,1)
        + "(8,3,4),\n" // 第 3 条记录：(8,3,4)
        + "(4,3,3),\n" // 第 4 条记录：(4,3,3)
        + "(7,2,5),\n" // 第 5 条记录：(7,2,5)
        + "(8,4,3) AS (f1:int,f2:int,f3:int);\n"; // 第 6 条记录：(8,4,3)；定义列名和类型

    final String x = pre // 定义第一个 Pig Latin 脚本，测试简单过滤条件
        + "X = FILTER A BY f3 == 3;\n" // FILTER 语句：过滤关系 A，条件是 f3 字段等于 3
        + "DUMP X;\n"; // DUMP 语句：输出关系 X 的内容到控制台
    final String expected = "(1,2,3)\n" // 定义期望的输出结果，f3 等于 3 的记录有 3 条
        + "(4,3,3)\n" // 第 2 条输出记录：(4,3,3)
        + "(8,4,3)\n"; // 第 3 条输出记录：(8,4,3)
    pig(x).returns(expected); // 调用 Fluent 对象的 returns 方法，验证输出结果

    final String x2 = pre // 定义第二个 Pig Latin 脚本，测试复杂过滤条件
        + "X2 = FILTER A BY (f1 == 8) OR (NOT (f2+f3 > f1));\n" // FILTER 语句：过滤关系 A，条件是 (f1 == 8) OR (NOT (f2+f3 > f1))
        // 条件解析：(f1 == 8) 表示 f1 等于 8
        // OR 表示逻辑或
        // (NOT (f2+f3 > f1)) 表示 f2+f3 不大于 f1，即 f2+f3 <= f1
        + "DUMP X2;\n"; // DUMP 语句：输出关系 X2 的内容到控制台
    final String expected2 = "(4,2,1)\n" // 定义期望的输出结果，满足条件的记录有 4 条
        // (4,2,1): f1=4 不等于 8，但 f2+f3=2+1=3 <= f1=4，所以满足条件
        + "(8,3,4)\n" // 第 2 条输出记录：(8,3,4)，f1=8 满足条件
        + "(7,2,5)\n" // 第 3 条输出记录：(7,2,5)，f1=7 不等于 8，但 f2+f3=2+5=7 <= f1=7，所以满足条件
        + "(8,4,3)\n"; // 第 4 条输出记录：(8,4,3)，f1=8 满足条件
    pig(x2).returns(expected2); // 调用 Fluent 对象的 returns 方法，验证输出结果
  }

  @Test void testLimit() throws ParseException { // 测试方法：测试 LIMIT 语句的功能，验证能否正确转换为 Calcite 的排序获取操作
    final String s = "A = LOAD 'DEPT';\n" // 定义 Pig Latin 脚本字符串，加载 'DEPT' 表到关系 A
        + "B = LIMIT A 3;"; // LIMIT 语句：限制关系 A 的行数为 3，结果存储到关系 B
    // LIMIT 操作在 Calcite 中通常实现为 LogicalSort 操作，其中 fetch 参数指定要获取的行数
    final String expected = "LogicalSort(fetch=[3])\n" // 定义期望的执行计划，应该生成 LogicalSort 操作
        // fetch=[3] 表示只获取前 3 行数据
        + "  LogicalTableScan(table=[[scott, DEPT]])\n"; // LogicalSort 的输入是 LogicalTableScan 操作，扫描 scott 模式下的 DEPT 表
    pig(s).explainContains(expected); // 调用 Fluent 对象的 explainContains 方法，验证执行计划是否包含期望的 LogicalSort 操作
  }

  @Test void testLimitExample() throws ParseException { // 测试方法：测试 LIMIT 语句的实际执行效果
    // 该测试使用 VALUES 语句创建测试数据，然后使用 LIMIT 限制行数，验证限制结果是否正确
    // 同时也测试了 LIMIT 与 ORDER 的组合使用
    final String pre = "A = VALUES (1,2,3),\n" // 定义前缀部分，使用 VALUES 语句创建包含 6 条记录的关系 A
        + "(4,2,1),\n" // 第 2 条记录：(4,2,1)
        + "(8,3,4),\n" // 第 3 条记录：(8,3,4)
        + "(4,3,3),\n" // 第 4 条记录：(4,3,3)
        + "(7,2,5),\n" // 第 5 条记录：(7,2,5)
        + "(8,4,3) AS (f1:int,f2:int,f3:int);\n"; // 第 6 条记录：(8,4,3)；定义列名和类型

    final String x = pre // 定义第一个 Pig Latin 脚本，测试简单的 LIMIT 操作
        + "X = LIMIT A 3;\n" // LIMIT 语句：限制关系 A 的行数为 3，结果存储到关系 X
        + "DUMP X;\n"; // DUMP 语句：输出关系 X 的内容到控制台
    final String expected = "(1,2,3)\n" // 定义期望的输出结果，前 3 条记录
        + "(4,2,1)\n" // 第 2 条输出记录：(4,2,1)
        + "(8,3,4)\n"; // 第 3 条输出记录：(8,3,4)
    pig(x).returns(expected); // 调用 Fluent 对象的 returns 方法，验证输出结果

    final String x2 = pre // 定义第二个 Pig Latin 脚本，测试 LIMIT 与 ORDER 的组合
        + "B = ORDER A BY f1 DESC, f2 ASC;\n" // ORDER 语句：对关系 A 按 f1 降序、f2 升序排序，结果存储到关系 B
        + "X2 = LIMIT B 3;\n" // LIMIT 语句：限制关系 B 的行数为 3，即取排序后的前 3 条记录
        + "DUMP X2;\n"; // DUMP 语句：输出关系 X2 的内容到控制台
    final String expected2 = "(8,3,4)\n" // 定义期望的输出结果，排序后的前 3 条记录
        // 按 f1 降序、f2 升序排序后，顺序为：(8,3,4), (8,4,3), (7,2,5), (4,2,1), (4,3,3), (1,2,3)
        + "(8,4,3)\n" // 第 2 条输出记录：(8,4,3)，f1=8 与第 1 条相同，但 f2=4 > 3，所以排在后面
        + "(7,2,5)\n"; // 第 3 条输出记录：(7,2,5)，f1=7 排在第二位
    pig(x2).returns(expected2); // 调用 Fluent 对象的 returns 方法，验证输出结果
  }

  @Test void testOrder() throws ParseException { // 测试方法：测试 ORDER 语句的功能，验证能否正确转换为 Calcite 的排序操作
    final String s = "A = LOAD 'DEPT';\n" // 定义 Pig Latin 脚本字符串，加载 'DEPT' 表到关系 A
        + "B = ORDER A BY DEPTNO DESC, DNAME;"; // ORDER 语句：对关系 A 按 DEPTNO 降序、DNAME 升序排序，结果存储到关系 B
    // 排序方向：DESC 表示降序，ASC 表示升序（默认）
    final String expected = // 定义期望的执行计划
        + "LogicalSort(sort0=[$0], sort1=[$1], dir0=[DESC], dir1=[ASC])\n" // 应该生成 LogicalSort 操作
        // sort0=[$0] 表示第 0 列（DEPTNO）参与排序
        // sort1=[$1] 表示第 1 列（DNAME）参与排序
        // dir0=[DESC] 表示第 0 列降序排序
        // dir1=[ASC] 表示第 1 列升序排序
        + "  LogicalTableScan(table=[[scott, DEPT]])\n"; // LogicalSort 的输入是 LogicalTableScan 操作，扫描 scott 模式下的 DEPT 表
    pig(s).explainContains(expected); // 调用 Fluent 对象的 explainContains 方法，验证执行计划是否包含期望的 LogicalSort 操作
  }

  @Test void testOrderStar() throws ParseException { // 测试方法：测试 ORDER 语句使用通配符 * 的功能
    // 通配符 * 表示按所有列排序，所有列使用相同的排序方向
    final String s = "A = LOAD 'DEPT';\n" // 定义 Pig Latin 脚本字符串，加载 'DEPT' 表到关系 A
        + "B = ORDER A BY * DESC;"; // ORDER 语句：对关系 A 的所有列按降序排序，结果存储到关系 B
    // * 表示所有列，DESC 表示所有列都按降序排序
    final String expected = // 定义期望的执行计划
        + "LogicalSort(sort0=[$0], sort1=[$1], sort2=[$2], dir0=[DESC], dir1=[DESC], dir2=[DESC])\n" // 应该生成 LogicalSort 操作
        // sort0=[$0] 表示第 0 列参与排序
        // sort1=[$1] 表示第 1 列参与排序
        // sort2=[$2] 表示第 2 列参与排序
        // dir0=[DESC] 表示第 0 列降序排序
        // dir1=[DESC] 表示第 1 列降序排序
        // dir2=[DESC] 表示第 2 列降序排序
        + "  LogicalTableScan(table=[[scott, DEPT]])\n"; // LogicalSort 的输入是 LogicalTableScan 操作，扫描 scott 模式下的 DEPT 表
    pig(s).explainContains(expected); // 调用 Fluent 对象的 explainContains 方法，验证执行计划是否包含期望的 LogicalSort 操作
  }

  @Test void testOrderExample() throws ParseException { // 测试方法：测试 ORDER 语句的实际执行效果
    // 该测试使用 VALUES 语句创建测试数据，然后使用 ORDER 排序，验证排序结果是否正确
    final String pre = "A = VALUES (1,2,3),\n" // 定义前缀部分，使用 VALUES 语句创建包含 6 条记录的关系 A
        + "(4,2,1),\n" // 第 2 条记录：(4,2,1)
        + "(8,3,4),\n" // 第 3 条记录：(8,3,4)
        + "(4,3,3),\n" // 第 4 条记录：(4,3,3)
        + "(7,2,5),\n" // 第 5 条记录：(7,2,5)
        + "(8,4,3) AS (a1:int,a2:int,a3:int);\n"; // 第 6 条记录：(8,4,3)；定义列名和类型

    final String x = pre // 定义完整的 Pig Latin 脚本
        + "X = ORDER A BY a3 DESC;\n" // ORDER 语句：对关系 A 按 a3 降序排序，结果存储到关系 X
        + "DUMP X;\n"; // DUMP 语句：输出关系 X 的内容到控制台
    final String expected = "(7,2,5)\n" // 定义期望的输出结果，按 a3 降序排序
        // 按 a3 降序排序后，顺序为：(7,2,5) a3=5, (8,3,4) a3=4, (1,2,3) a3=3, (4,3,3) a3=3, (8,4,3) a3=3, (4,2,1) a3=1
        // 对于 a3 相同的记录，保持原始顺序（稳定排序）
        + "(8,3,4)\n" // 第 2 条输出记录：(8,3,4)，a3=4
        + "(1,2,3)\n" // 第 3 条输出记录：(1,2,3)，a3=3
        + "(4,3,3)\n" // 第 4 条输出记录：(4,3,3)，a3=3
        + "(8,4,3)\n" // 第 5 条输出记录：(8,4,3)，a3=3
        + "(4,2,1)\n"; // 第 6 条输出记录：(4,2,1)，a3=1
    pig(x).returns(expected); // 调用 Fluent 对象的 returns 方法，验证输出结果
  }

  /** VALUES is an extension to Pig. You can achieve the same effect in standard
   * Pig by creating a text file. */ // 方法文档注释：VALUES 是 Pig 的扩展功能，在标准 Pig 中可以通过创建文本文件实现相同效果
  // VALUES 语句允许直接在 Pig 脚本中定义常量数据，而不需要从外部文件加载
  @Test void testValues() throws ParseException { // 测试方法：测试 VALUES 语句的功能，验证能否正确转换为 Calcite 的常量值操作
    final String s = "A = VALUES (1, 'a'), (2, 'b') AS (x: int, y: string);\n" // 定义 Pig Latin 脚本字符串，使用 VALUES 语句创建包含两行数据的关系 A
        // (1, 'a') 和 (2, 'b') 是两行数据，每行包含一个整数和一个字符串
        // AS (x: int, y: string) 定义了列名和类型，x 是整数类型，y 是字符串类型
        + "DUMP A;"; // DUMP 语句：输出关系 A 的内容到控制台
    final String expected = // 定义期望的执行计划
        "LogicalValues(tuples=[[{ 1, 'a' }, { 2, 'b' }]])\n"; // 应该生成 LogicalValues 操作，包含两行数据 { 1, 'a' } 和 { 2, 'b' }
    pig(s).explainContains(expected); // 调用 Fluent 对象的 explainContains 方法，验证执行计划是否包含期望的 LogicalValues 操作
  }

  @Test void testValuesNested() throws ParseException { // 测试方法：测试 VALUES 语句支持嵌套数据类型（bag）的功能
    // 该测试验证 VALUES 语句能否正确处理包含嵌套 bag 类型的数据
    // bag 是 Pig 中的一种复杂类型，表示一个元组（tuple）的集合
    final String s = "A = VALUES (1, {('a', true), ('b', false)}),\n" // 定义 Pig Latin 脚本字符串，使用 VALUES 语句创建包含嵌套 bag 的关系 A
        // (1, {('a', true), ('b', false)}) 是第一行数据，包含一个整数 1 和一个 bag
        // bag {('a', true), ('b', false)} 包含两个元组：('a', true) 和 ('b', false)
        + " (2, {})\n" // 第二行数据：(2, {})，包含一个整数 2 和一个空 bag
        + "AS (x: int, y: bag {tuple(a: string, b: boolean)});\n" // 定义列名和类型
        // x 是整数类型
        // y 是 bag 类型，包含 tuple 类型的元素，每个 tuple 有两个字段：a（字符串类型）和 b（布尔类型）
        + "DUMP A;"; // DUMP 语句：输出关系 A 的内容到控制台
    final String expected = // 定义期望的执行计划
        "LogicalValues(tuples=[[{ 1, [['a', true], ['b', false]] }, { 2, [] }]])\n"; // 应该生成 LogicalValues 操作
        // 第一行数据：{ 1, [['a', true], ['b', false]] }，其中 [['a', true], ['b', false]] 表示 bag 中的两个元组
        // 第二行数据：{ 2, [] }，其中 [] 表示空 bag
    pig(s).explainContains(expected); // 调用 Fluent 对象的 explainContains 方法，验证执行计划是否包含期望的 LogicalValues 操作
  }
} // PigletTest 类定义结束
