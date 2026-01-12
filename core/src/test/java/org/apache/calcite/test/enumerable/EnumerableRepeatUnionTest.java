/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看NOTICE文件了解更多版权信息
 * this work for additional information regarding copyright ownership. // 关于版权所有权的附加信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache许可证2.0版授权给你使用此文件
 * (the "License"); you may not use this file except in compliance with // 你只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at // 你可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // 许可证获取地址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则软件按"原样"分发
 * distributed under the License is distributed on an "AS IS" BASIS, // 不提供任何明示或暗示的保证或条件
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 无论是明示的还是暗示的
 * See the License for the specific language governing permissions and // 查看许可证了解具体的权限和限制
 * limitations under the License. // 许可证下的限制
 */
package org.apache.calcite.test.enumerable; // 声明包名，该类属于org.apache.calcite.test.enumerable包，用于测试可枚举功能

import org.apache.calcite.adapter.enumerable.EnumerableRepeatUnion; // 导入可枚举重复联合操作符类，用于实现递归查询
import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入可枚举规则集合，包含各种可枚举相关的优化规则
import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入反射模式类，用于通过反射将Java对象映射为数据库表
import org.apache.calcite.config.CalciteConnectionProperty; // 导入Calcite连接属性枚举，用于配置连接的各种属性
import org.apache.calcite.config.Lex; // 导入词法分析器配置，定义SQL标识符的大小写和引用规则
import org.apache.calcite.plan.RelOptPlanner; // 导入关系优化规划器接口，负责查询优化
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，是所有关系代数操作的基类
import org.apache.calcite.rel.core.JoinRelType; // 导入连接关系类型枚举，定义INNER、LEFT、RIGHT、FULL等连接类型
import org.apache.calcite.rel.rules.JoinCommuteRule; // 导入Join交换规则，用于优化连接操作的顺序
import org.apache.calcite.rel.rules.JoinToCorrelateRule; // 导入Join转Correlate规则，用于将Join转换为相关连接
import org.apache.calcite.runtime.Hook; // 导入钩子类，用于在特定执行点插入自定义逻辑
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SQL标准操作符表，包含各种SQL操作符如+、-、*、/等
import org.apache.calcite.test.CalciteAssert; // 导入Calcite断言工具类，用于构建和测试SQL查询
import org.apache.calcite.test.ReflectiveSchemaWithoutRowCount; // 导入不带行数统计的反射模式类，用于测试场景
import org.apache.calcite.test.schemata.hr.HierarchySchema; // 导入层级模式类，提供员工层级关系的测试数据

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.util.Arrays; // 导入数组工具类，提供数组操作的各种方法
import java.util.function.Consumer; // 导入函数式接口Consumer，表示接受单个输入参数且无返回的操作

/**
 * Unit tests for {@link EnumerableRepeatUnion}. // EnumerableRepeatUnion的单元测试类，用于测试可枚举重复联合操作符的功能
 *
 * <p>Added in
 * <a href="https://issues.apache.org/jira/browse/CALCITE-2812">[CALCITE-2812]
 * Add algebraic operators to allow expressing recursive queries</a>. // 这个测试类是在CALCITE-2812问题中添加的，该问题引入了代数操作符来支持递归查询的表达
 */
class EnumerableRepeatUnionTest { // 测试类定义，用于测试EnumerableRepeatUnion操作符的各种功能场景

  @Test void testGenerateNumbers() { // 测试方法：使用递归CTE生成数字序列1到10，验证基本的递归查询功能
    CalciteAssert.that()
        .withRel(
            //   WITH RECURSIVE delta(n) AS (
            //     VALUES (1)
            //     UNION ALL
            //     SELECT n+1 FROM delta WHERE n < 10
            //   )
            //   SELECT * FROM delta
            builder -> builder
                .values(new String[] { "i" }, 1)
                .transientScan("DELTA")
                .filter(
                    builder.call(SqlStdOperatorTable.LESS_THAN,
                        builder.field(0),
                        builder.literal(10)))
                .project(
                    builder.call(SqlStdOperatorTable.PLUS,
                        builder.field(0),
                        builder.literal(1)))
                .repeatUnion("DELTA", true)
                .build())
        .returnsOrdered("i=1", "i=2", "i=3", "i=4", "i=5", "i=6", "i=7", "i=8", "i=9", "i=10");
  }

  @Test void testGenerateNumbers2UsingSqlCheckPlan() { // 测试方法：使用SQL方式测试递归查询，并验证执行计划的结构
    CalciteAssert.that() // 创建Calcite断言对象
        .with(CalciteConnectionProperty.LEX, Lex.JAVA) // 设置词法分析器为JAVA模式，使用Java风格的标识符
        .withSchema("s", new ReflectiveSchema(new HierarchySchema())) // 注册名为"s"的模式，使用反射模式加载HierarchySchema
        .query("WITH RECURSIVE aux(i) AS (\n" // 执行SQL查询，定义递归CTE名为aux，包含列i
            + "     VALUES (0)" // 初始值为0
            + "     UNION " // 使用UNION（不是UNION ALL），会自动去重
            + "     SELECT MOD((i+1), 10) FROM aux WHERE i < 10" // 递归部分：计算(i+1) mod 10，条件i < 10
            + "  )" // CTE定义结束
            + "   SELECT * FROM aux\n") // 从aux表中选择所有数据
        .explainHookMatches( // 验证执行计划是否匹配预期的结构
            "EnumerableRepeatUnion(all=[false])\n" // 顶层是EnumerableRepeatUnion，all=false表示使用UNION（去重）
                + "  EnumerableTableSpool(readType=[LAZY], writeType=[LAZY], table=[[aux]])\n" // 左分支：表池操作，延迟读写aux表
                + "    EnumerableValues(tuples=[[{ 0 }]])\n" // 初始值：单行数据{0}
                + "  EnumerableTableSpool(readType=[LAZY], writeType=[LAZY], table=[[aux]])\n" // 右分支：表池操作，延迟读写aux表
                + "    EnumerableCalc(expr#0=[{inputs}], expr#1=[1], expr#2=[+($t0, $t1)], " // 计算表达式：输入列+1
                + "expr#3=[10], expr#4=[MOD($t2, $t3)], expr#5=[<($t0, $t3)], EXPR$0=[$t4], $condition=[$t5])\n" // 取模运算和过滤条件
                + "      EnumerableInterpreter\n" // 解释器节点
                + "        BindableTableScan(table=[[aux]])\n" // 绑定表扫描，扫描aux表
        )
        .returnsOrdered("i=0", "i=1", "i=2", "i=3", "i=4", "i=5", "i=6", "i=7", "i=8", "i=9"); // 验证返回结果有序，从0到9
  }

  @Test void testGenerateNumbers2() { // 测试方法：测试使用UNION（去重）的递归查询，生成循环的数字序列
    CalciteAssert.that() // 创建Calcite断言对象
        .withRel( // 使用RelBuilder构建关系代数树
            //   WITH RECURSIVE aux(i) AS ( // 递归CTE定义，名为aux，包含列i
            //     VALUES (0) // 初始值：从0开始
            //     UNION -- (ALL would generate an infinite loop!) // 使用UNION（去重），如果用UNION ALL会无限循环
            //     SELECT (i+1)%10 FROM aux WHERE i < 10 // 递归部分：计算(i+1) mod 10，条件i < 10
            //   ) // CTE定义结束
            //   SELECT * FROM aux // 从aux表中选择所有数据
            builder -> builder // 创建RelBuilder构建器
                .values(new String[] { "i" }, 0) // 创建初始值行：包含列"i"，值为0
                .transientScan("AUX") // 扫描名为"AUX"的临时表，这是递归查询的自引用
                .filter( // 添加过滤条件，控制递归的终止
                    builder.call(SqlStdOperatorTable.LESS_THAN, // 调用小于操作符
                        builder.field(0), // 获取第0列的值（i列）
                        builder.literal(10))) // 字面量10，条件为i < 10
                .project( // 投影操作，对数据进行转换
                    builder.call(SqlStdOperatorTable.MOD, // 调用取模操作符
                        builder.call(SqlStdOperatorTable.PLUS, // 嵌套调用加法操作符
                            builder.field(0), // 获取第0列的值（i列）
                            builder.literal(1)), // 字面量1，计算i+1
                        builder.literal(10))) // 字面量10，计算(i+1) mod 10
                .repeatUnion("AUX", false) // 创建重复联合操作，"AUX"是临时表名，false表示使用UNION（去重）
                .build()) // 构建关系节点
        .returnsOrdered("i=0", "i=1", "i=2", "i=3", "i=4", "i=5", "i=6", "i=7", "i=8", "i=9"); // 验证返回结果有序，从0到9
  }

  @Test void testGenerateNumbers3() { // 测试方法：测试多列递归查询，验证递归过程中可以保持某些列不变
    CalciteAssert.that() // 创建Calcite断言对象
        .withRel( // 使用RelBuilder构建关系代数树
            //   WITH RECURSIVE aux(i, j) AS ( // 递归CTE定义，名为aux，包含两列i和j
            //     VALUES (0, 0) // 初始值：i=0, j=0
            //     UNION -- (ALL would generate an infinite loop!) // 使用UNION（去重）
            //     SELECT (i+1)%10, j FROM aux WHERE i < 10 // 递归部分：i列循环变化，j列保持不变
            //   ) // CTE定义结束
            //   SELECT * FROM aux // 从aux表中选择所有数据
            builder -> builder // 创建RelBuilder构建器
                .values(new String[] { "i", "j" }, 0, 0) // 创建初始值行：包含列"i"和"j"，值分别为0和0
                .transientScan("AUX") // 扫描名为"AUX"的临时表，这是递归查询的自引用
                .filter( // 添加过滤条件，控制递归的终止
                    builder.call(SqlStdOperatorTable.LESS_THAN, // 调用小于操作符
                        builder.field(0), // 获取第0列的值（i列）
                        builder.literal(10))) // 字面量10，条件为i < 10
                .project( // 投影操作，对数据进行转换
                    builder.call(SqlStdOperatorTable.MOD, // 调用取模操作符
                        builder.call(SqlStdOperatorTable.PLUS, // 嵌套调用加法操作符
                            builder.field(0), // 获取第0列的值（i列）
                            builder.literal(1)), // 字面量1，计算i+1
                        builder.literal(10)), // 字面量10，计算(i+1) mod 10
                    builder.field(1)) // 第1列（j列）保持不变
                .repeatUnion("AUX", false) // 创建重复联合操作，"AUX"是临时表名，false表示使用UNION（去重）
                .build()) // 构建关系节点
        .returnsOrdered("i=0; j=0", // 验证返回结果有序，i从0到9循环，j始终保持为0
            "i=1; j=0",
            "i=2; j=0",
            "i=3; j=0",
            "i=4; j=0",
            "i=5; j=0",
            "i=6; j=0",
            "i=7; j=0",
            "i=8; j=0",
            "i=9; j=0");
  }

  @Test void testFactorial() { // 测试方法：使用递归查询计算阶乘，验证递归查询在复杂计算中的能力
    CalciteAssert.that() // 创建Calcite断言对象
        .withRel( // 使用RelBuilder构建关系代数树
            //   WITH RECURSIVE delta(n, fact) AS ( // 递归CTE定义，名为delta，包含两列n和fact
            //     VALUES (0, 1) // 初始值：n=0, fact=1（0的阶乘是1）
            //     UNION ALL // 使用UNION ALL（允许重复）
            //     SELECT n+1, (n+1)*fact FROM delta WHERE n < 7 // 递归部分：n每次加1，fact=(n+1)*fact，条件n < 7
            //   ) // CTE定义结束
            //   SELECT * FROM delta // 从delta表中选择所有数据
            builder -> builder // 创建RelBuilder构建器
                .values(new String[] { "n", "fact" }, 0, 1) // 创建初始值行：包含列"n"和"fact"，值分别为0和1
                .transientScan("D") // 扫描名为"D"的临时表，这是递归查询的自引用
                .filter( // 添加过滤条件，控制递归的终止
                    builder.call(SqlStdOperatorTable.LESS_THAN, // 调用小于操作符
                        builder.field("n"), // 获取"n"列的值
                        builder.literal(7))) // 字面量7，条件为n < 7
                .project( // 投影操作，对数据进行转换
                    Arrays.asList( // 使用列表定义多个投影表达式
                        builder.call(SqlStdOperatorTable.PLUS, // 调用加法操作符
                            builder.field("n"), // 获取"n"列的值
                            builder.literal(1)), // 字面量1，计算n+1
                        builder.call(SqlStdOperatorTable.MULTIPLY, // 调用乘法操作符
                            builder.call(SqlStdOperatorTable.PLUS, // 嵌套调用加法操作符
                                builder.field("n"), // 获取"n"列的值
                                builder.literal(1)), // 字面量1，计算n+1
                            builder.field("fact"))), // 获取"fact"列的值，计算(n+1)*fact
                    Arrays.asList("n", "fact")) // 为投影表达式指定列名
                .repeatUnion("D", true) // 创建重复联合操作，"D"是临时表名，true表示使用UNION ALL（允许重复）
                .build()) // 构建关系节点
        .returnsOrdered("n=0; fact=1", // 验证返回结果有序，显示0到7的阶乘计算过程
            "n=1; fact=1", // 1! = 1
            "n=2; fact=2", // 2! = 2
            "n=3; fact=6", // 3! = 6
            "n=4; fact=24", // 4! = 24
            "n=5; fact=120", // 5! = 120
            "n=6; fact=720", // 6! = 720
            "n=7; fact=5040"); // 7! = 5040
  }

  @Test void testGenerateNumbersNestedRecursion() { // 测试方法：测试嵌套递归查询，验证多层递归的复杂场景
    CalciteAssert.that() // 创建Calcite断言对象
        .withRel( // 使用RelBuilder构建关系代数树
            //   WITH RECURSIVE t_out(n) AS ( // 外层递归CTE定义，名为t_out，包含列n
            //     WITH RECURSIVE t_in(n) AS ( // 内层递归CTE定义，名为t_in，包含列n
            //       VALUES (1) // 初始值：从1开始
            //       UNION ALL // 使用UNION ALL（允许重复）
            //       SELECT n+1 FROM t_in WHERE n < 9 // 递归部分：n每次加1，条件n < 9
            //     ) // 内层CTE结束
            //     SELECT n FROM t_in // 从内层CTE中选择数据作为外层的初始值
            //     UNION ALL // 使用UNION ALL（允许重复）
            //     SELECT n*10 FROM t_out WHERE n < 100 // 外层递归部分：n乘以10，条件n < 100
            //   ) // 外层CTE结束
            //   SELECT n FROM t_out // 从外层CTE中选择所有数据
            builder -> builder // 创建RelBuilder构建器
                .values(new String[] { "n" }, 1) // 创建初始值行：包含列"n"，值为1
                .transientScan("T_IN") // 扫描名为"T_IN"的临时表，这是内层递归查询的自引用
                .filter( // 添加过滤条件，控制内层递归的终止
                    builder.call(SqlStdOperatorTable.LESS_THAN, // 调用小于操作符
                        builder.field("n"), // 获取"n"列的值
                        builder.literal(9))) // 字面量9，条件为n < 9
                .project( // 投影操作，对数据进行转换
                    builder.call(SqlStdOperatorTable.PLUS, // 调用加法操作符
                        builder.field("n"), // 获取"n"列的值
                        builder.literal(1))) // 字面量1，计算n+1
                .repeatUnion("T_IN", true) // 创建内层重复联合操作，"T_IN"是临时表名，true表示使用UNION ALL

                .transientScan("T_OUT") // 扫描名为"T_OUT"的临时表，这是外层递归查询的自引用
                .filter( // 添加过滤条件，控制外层递归的终止
                    builder.call(SqlStdOperatorTable.LESS_THAN, // 调用小于操作符
                        builder.field("n"), // 获取"n"列的值
                        builder.literal(100))) // 字面量100，条件为n < 100
                .project( // 投影操作，对数据进行转换
                    builder.call(SqlStdOperatorTable.MULTIPLY, // 调用乘法操作符
                        builder.field("n"), // 获取"n"列的值
                        builder.literal(10))) // 字面量10，计算n*10
                .repeatUnion("T_OUT", true) // 创建外层重复联合操作，"T_OUT"是临时表名，true表示使用UNION ALL
                .build()) // 构建关系节点
        .returnsOrdered( // 验证返回结果有序，展示嵌套递归的结果
            "n=1",   "n=2",   "n=3",   "n=4",   "n=5",   "n=6",   "n=7",   "n=8",   "n=9", // 内层递归：1到9
            "n=10",  "n=20",  "n=30",  "n=40",  "n=50",  "n=60",  "n=70",  "n=80",  "n=90", // 外层递归第一轮：1-9乘以10
            "n=100", "n=200", "n=300", "n=400", "n=500", "n=600", "n=700", "n=800", "n=900"); // 外层递归第二轮：10-90乘以10
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4139">[CALCITE-4139] // 引用JIRA问题CALCITE-4139
   * Prevent NPE in ListTransientTable</a>. */ // 目的是防止ListTransientTable中的空指针异常
  @Test void testGenerateNumbersWithNull() { // 测试方法：测试递归查询中处理NULL值的能力，验证不会出现NPE
    CalciteAssert.that() // 创建Calcite断言对象
        .withRel( // 使用RelBuilder构建关系代数树
            builder -> builder // 创建RelBuilder构建器
                .values(new String[] { "i" }, 1, 2, null, 3) // 创建初始值行：包含列"i"，值为1, 2, null, 3（包含NULL值）
                .transientScan("DELTA") // 扫描名为"DELTA"的临时表，这是递归查询的自引用
                .filter( // 添加过滤条件，控制递归的终止
                    builder.call(SqlStdOperatorTable.LESS_THAN, // 调用小于操作符
                        builder.field(0), // 获取第0列的值（i列）
                        builder.literal(3))) // 字面量3，条件为i < 3
                .project( // 投影操作，对数据进行转换
                    builder.call(SqlStdOperatorTable.PLUS, // 调用加法操作符
                        builder.field(0), // 获取第0列的值（i列）
                        builder.literal(1))) // 字面量1，计算i+1
                .repeatUnion("DELTA", true) // 创建重复联合操作，"DELTA"是临时表名，true表示使用UNION ALL（允许重复）
                .build()) // 构建关系节点
        .returnsOrdered("i=1", "i=2", "i=null", "i=3", "i=2", "i=3", "i=3"); // 验证返回结果有序，包含NULL值处理
  }

  @Test void testRepeatUnionWithCorrelateWithTransientScanOnItsRightUsingSql() { // 测试方法：使用SQL测试包含Correlate和临时表扫描的递归查询
    CalciteAssert.that() // 创建Calcite断言对象
        .with(CalciteConnectionProperty.LEX, Lex.JAVA) // 设置词法分析器为JAVA模式
        .with(CalciteConnectionProperty.FORCE_DECORRELATE, false) // 禁用强制去相关化，允许保留Correlate节点
        .withSchema("s", new ReflectiveSchemaWithoutRowCount(new HierarchySchema())) // 注册名为"s"的模式，使用反射模式加载HierarchySchema（不提供行数统计）
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置规划器钩子，自定义优化规则
          planner.addRule(JoinToCorrelateRule.Config.DEFAULT.toRule()); // 添加Join转Correlate规则
          planner.removeRule(JoinCommuteRule.Config.DEFAULT.toRule()); // 移除Join交换规则
          planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
          planner.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
        })
        .query("   WITH RECURSIVE delta(empid, name) as (\n" // 执行SQL查询，定义递归CTE名为delta，包含列empid和name
            + "     SELECT empid, name FROM emps WHERE empid = 2\n" // 初始部分：从emps表选择empid=2的员工
            + "     UNION ALL\n" // 使用UNION ALL（允许重复）
            + "     SELECT e.empid, e.name FROM delta d\n" // 递归部分：从delta表出发
            + "                            JOIN hierarchies h ON d.empid = h.managerid\n" // 关联hierarchies表，查找下属关系
            + "                            JOIN emps e        ON h.subordinateid = e.empid\n" // 再关联emps表，获取下属信息
            + "   )\n" // CTE定义结束
            + "   SELECT empid, name FROM delta\n") // 从delta表中选择所有数据
        .explainHookMatches("" // 验证执行计划是否匹配预期的结构
            + "EnumerableRepeatUnion(all=[true])\n" // 顶层是EnumerableRepeatUnion，all=true表示使用UNION ALL
            + "  EnumerableTableSpool(readType=[LAZY], writeType=[LAZY], table=[[delta]])\n" // 左分支：表池操作，延迟读写delta表
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=[CAST($t0):INTEGER NOT NULL], expr#6=[2], expr#7=[=($t5, $t6)], empid=[$t0], name=[$t2], $condition=[$t7])\n" // 计算节点：过滤empid=2
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：扫描emps表
            + "  EnumerableTableSpool(readType=[LAZY], writeType=[LAZY], table=[[delta]])\n" // 右分支：表池操作，延迟读写delta表
            + "    EnumerableCalc(expr#0..4=[{inputs}], empid=[$t3], name=[$t4])\n" // 计算节点：投影empid和name列
            + "      EnumerableCorrelate(correlation=[$cor1], joinType=[inner], requiredColumns=[{2}])\n" // 外层Correlate节点：相关连接
            + "        EnumerableCorrelate(correlation=[$cor0], joinType=[inner], requiredColumns=[{0}])\n" // 内层Correlate节点：相关连接
            + "          EnumerableCalc(expr#0..1=[{inputs}], empid=[$t0])\n" // 计算节点：投影empid列
            + "            EnumerableInterpreter\n" // 解释器节点
            + "              BindableTableScan(table=[[delta]])\n" // 绑定表扫描：扫描delta表
            + "          EnumerableCalc(expr#0..1=[{inputs}], expr#2=[$cor0], expr#3=[$t2.empid], expr#4=[=($t3, $t0)], proj#0..1=[{exprs}], $condition=[$t4])\n" // 计算节点：关联hierarchies表
            + "            EnumerableTableScan(table=[[s, hierarchies]])\n" // 表扫描：扫描hierarchies表
            + "        EnumerableCalc(expr#0..4=[{inputs}], expr#5=[$cor1], expr#6=[$t5.subordinateid], expr#7=[=($t6, $t0)], empid=[$t0], name=[$t2], $condition=[$t7])\n" // 计算节点：关联emps表
            + "          EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：扫描emps表
        .returnsUnordered("empid=2; name=Emp2", // 验证返回结果无序，显示员工及其下属
            "empid=3; name=Emp3", // Emp2的下属
            "empid=5; name=Emp5"); // Emp2的下属
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4054">[CALCITE-4054] // 引用JIRA问题CALCITE-4054
   * RepeatUnion containing a Correlate with a transientScan on its RHS causes NPE</a>. */ // 目的是修复RepeatUnion包含Correlate且右侧有临时表扫描时的空指针异常
  @Test void testRepeatUnionWithCorrelateWithTransientScanOnItsRight() { // 测试方法：使用RelBuilder测试包含Correlate和临时表扫描的递归查询
    CalciteAssert.that() // 创建Calcite断言对象
        .with(CalciteConnectionProperty.LEX, Lex.JAVA) // 设置词法分析器为JAVA模式
        .with(CalciteConnectionProperty.FORCE_DECORRELATE, false) // 禁用强制去相关化，允许保留Correlate节点
        .withSchema("s", new ReflectiveSchemaWithoutRowCount(new HierarchySchema())) // 注册名为"s"的模式，使用反射模式加载HierarchySchema（不提供行数统计）
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置规划器钩子，自定义优化规则
          planner.addRule(JoinToCorrelateRule.Config.DEFAULT.toRule()); // 添加Join转Correlate规则
          planner.removeRule(JoinCommuteRule.Config.DEFAULT.toRule()); // 移除Join交换规则
          planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
          planner.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
        })
        .withRel(builder -> { // 使用RelBuilder构建关系代数树
          builder // 创建RelBuilder构建器
              //   WITH RECURSIVE delta(empid, name) as ( // 递归CTE定义，名为delta，包含列empid和name
              //     SELECT empid, name FROM emps WHERE empid = 2 // 初始部分：从emps表选择empid=2的员工
              //     UNION ALL // 使用UNION ALL（允许重复）
              //     SELECT e.empid, e.name FROM delta d // 递归部分：从delta表出发
              //                            JOIN hierarchies h ON d.empid = h.managerid // 关联hierarchies表，查找下属关系
              //                            JOIN emps e        ON h.subordinateid = e.empid // 再关联emps表，获取下属信息
              //   ) // CTE定义结束
              //   SELECT empid, name FROM delta // 从delta表中选择所有数据
              .scan("s", "emps") // 扫描s模式下的emps表
              .filter( // 添加过滤条件
                  builder.equals( // 调用等于操作符
                      builder.field("empid"), // 获取"empid"列的值
                      builder.literal(2))) // 字面量2，条件为empid = 2
              .project( // 投影操作，选择需要的列
                  builder.field("emps", "empid"), // 获取emps表的empid列
                  builder.field("emps", "name")) // 获取emps表的name列

              .transientScan("#DELTA#"); // 扫描名为"#DELTA#"的临时表，这是递归查询的自引用
          RelNode transientScan = builder.build(); // 构建关系节点并保存，以便后续使用

          builder // 继续使用RelBuilder构建递归部分
              .scan("s", "hierarchies") // 扫描s模式下的hierarchies表
              .push(transientScan) // 将之前构建的transientScan压入栈中，作为连接的右输入
              .join( // 执行连接操作
                  JoinRelType.INNER, // 连接类型为内连接
                  builder.equals( // 连接条件：等于
                      builder.field(2, "#DELTA#", "empid"), // 获取#DELTA#表的empid列
                      builder.field(2, "hierarchies", "managerid"))) // 获取hierarchies表的managerid列

              .scan("s", "emps") // 扫描s模式下的emps表
              .join( // 执行连接操作
                  JoinRelType.INNER, // 连接类型为内连接
                  builder.equals( // 连接条件：等于
                      builder.field(2, "hierarchies", "subordinateid"), // 获取hierarchies表的subordinateid列
                      builder.field(2, "emps", "empid"))) // 获取emps表的empid列
              .project( // 投影操作，选择需要的列
                  builder.field("emps", "empid"), // 获取emps表的empid列
                  builder.field("emps", "name")) // 获取emps表的name列
              .repeatUnion("#DELTA#", true); // 创建重复联合操作，"#DELTA#"是临时表名，true表示使用UNION ALL
          return builder.build(); // 构建最终的关系节点
        })
        .explainHookMatches("" // 验证执行计划是否匹配预期的结构
            + "EnumerableRepeatUnion(all=[true])\n" // 顶层是EnumerableRepeatUnion，all=true表示使用UNION ALL
            + "  EnumerableTableSpool(readType=[LAZY], writeType=[LAZY], table=[[#DELTA#]])\n" // 左分支：表池操作，延迟读写#DELTA#表
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=[2], expr#6=[=($t0, $t5)], empid=[$t0], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤empid=2
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：扫描emps表
            + "  EnumerableTableSpool(readType=[LAZY], writeType=[LAZY], table=[[#DELTA#]])\n" // 右分支：表池操作，延迟读写#DELTA#表
            + "    EnumerableCalc(expr#0..4=[{inputs}], empid=[$t3], name=[$t4])\n" // 计算节点：投影empid和name列
            + "      EnumerableCorrelate(correlation=[$cor1], joinType=[inner], requiredColumns=[{1}])\n" // 外层Correlate节点：相关连接
            // It is important to have EnumerableCorrelate + #DELTA# table scan on its right // 注释说明：必须有EnumerableCorrelate且右侧是#DELTA#表扫描
            // to reproduce the issue CALCITE-4054 // 这样才能重现CALCITE-4054问题
            + "        EnumerableCorrelate(correlation=[$cor0], joinType=[inner], requiredColumns=[{0}])\n" // 内层Correlate节点：相关连接
            + "          EnumerableTableScan(table=[[s, hierarchies]])\n" // 表扫描：扫描hierarchies表
            + "          EnumerableCalc(expr#0..1=[{inputs}], expr#2=[$cor0], expr#3=[$t2.managerid], expr#4=[=($t0, $t3)], empid=[$t0], $condition=[$t4])\n" // 计算节点：关联#DELTA#表
            + "            EnumerableInterpreter\n" // 解释器节点
            + "              BindableTableScan(table=[[#DELTA#]])\n" // 绑定表扫描：扫描#DELTA#表
            + "        EnumerableCalc(expr#0..4=[{inputs}], expr#5=[$cor1], expr#6=[$t5.subordinateid], expr#7=[=($t6, $t0)], empid=[$t0], name=[$t2], $condition=[$t7])\n" // 计算节点：关联emps表
            + "          EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：扫描emps表
        .returnsUnordered("empid=2; name=Emp2", // 验证返回结果无序，显示员工及其下属
            "empid=3; name=Emp3", // Emp2的下属
            "empid=5; name=Emp5"); // Emp2的下属
  }
} // 类定义结束
