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
package org.apache.calcite.test; // 包声明：定义该类属于 org.apache.calcite.test 包

import org.apache.calcite.rel.RelNode; // 导入 Calcite 的关系表达式节点类，代表关系代数中的操作
import org.apache.calcite.util.TestUtil; // 导入测试工具类，提供测试辅助方法

import org.hamcrest.Matcher; // 导入 Hamcrest 匹配器接口，用于断言匹配
import org.junit.jupiter.api.Disabled; // 导入 JUnit5 的 Disabled 注解，用于禁用测试
import org.junit.jupiter.api.Test; // 导入 JUnit5 的 Test 注解，标记测试方法

import java.io.IOException; // 导入 IO 异常类，处理输入输出异常

import static org.apache.calcite.test.Matchers.inTree; // 导入静态匹配器方法，用于检查关系树中是否包含特定节点

import static org.hamcrest.CoreMatchers.containsString; // 导入静态匹配器方法，检查字符串是否包含子串
import static org.hamcrest.MatcherAssert.assertThat; // 导入静态断言方法，执行匹配器断言
import static org.hamcrest.Matchers.hasToString; // 导入静态匹配器方法，检查对象的 toString() 结果

/**
 * Tests for {@code PigRelExVisitor}.
 * PigRelExTest 类：用于测试 PigRelExVisitor（Pig 关系表达式访问者）的测试类
 * 该类主要测试 Pig Latin 表达式到 Calcite 关系代数树的转换功能
 * Pig Latin 是 Apache Pig 的脚本语言，用于处理大数据
 * 该测试验证了各种 Pig 表达式（常量、运算符、类型转换、函数等）能否正确转换为 Calcite 的 RelNode
 */
class PigRelExTest extends PigRelTestBase { // PigRelExTest 类继承自 PigRelTestBase 基类，获得 Pig 测试的基础功能
  private void checkTranslation(String pigExpr, Matcher<RelNode> relMatcher) { // checkTranslation 方法：检查 Pig 表达式到关系树的转换是否正确
    String pigScript = "" // 定义 Pig 脚本字符串，使用空字符串开头便于多行拼接
        + "A = LOAD 'test' as (a:int, b:long, c:float, d:double,\n" // 加载数据表 A，定义字段：a(int)、b(long)、c(float)、d(double)
        + "    e:chararray, f:bytearray, g:boolean, h:datetime,\n" // 继续定义字段：e(chararray字符串)、f(bytearray字节数组)、g(boolean)、h(datetime日期时间)
        + "    i:biginteger, j:bigdecimal, k1:tuple(),\n" // 继续定义字段：i(biginteger大整数)、j(bigdecimal大十进制)、k1(空元组)
        + "    k2:tuple(k21:int, k22:(k221:long, k222:chararray)), l1:bag{},\n" // 定义嵌套类型：k2(嵌套元组，包含 k21 和 k22)、l1(空包)
        + "    l2:bag{(l21:int, l22:float)}, m1:map[], m2:map[int],\n" // 定义嵌套类型：l2(包含元组的包)、m1(空映射)、m2(键为字符串，值为整数的映射)
        + "    m3:map[(m31:float)]);\n" // 定义 m3(键为字符串，值为浮点数元组的映射)
        + "B = FILTER A BY " + pigExpr + ";\n"; // 使用 FILTER 操作根据传入的 pigExpr 表达式过滤数据，生成关系 B
    try { // 开始 try 块，捕获可能抛出的异常
      final RelNode rel = // 声明最终的关系节点变量 rel
          converter.pigQuery2Rel(pigScript, false, false, false).get(0); // 使用转换器将 Pig 脚本转换为关系节点列表，获取第一个关系节点
      assertThat(rel, relMatcher); // 使用断言验证转换后的关系节点是否匹配预期的匹配器
    } catch (IOException e) { // 捕获 IO 异常
      throw TestUtil.rethrow(e); // 使用 TestUtil 工具类重新抛出异常，保持异常堆栈信息
    }
  }

  private void checkType(String pigExpr, Matcher<String> rowTypeMatcher) { // checkType 方法：检查 Pig 表达式的类型推断是否正确
    String pigScript = "" // 定义 Pig 脚本字符串
        + "A = LOAD 'test' as (a:int);\n" // 加载数据表 A，只包含一个整数字段 a
        + "B = FOREACH A GENERATE a, " + pigExpr + ";\n"; // 使用 FOREACH 操作生成新关系，包含字段 a 和传入的 pigExpr 表达式
    try { // 开始 try 块
      final RelNode rel = // 声明关系节点变量
          converter.pigQuery2Rel(pigScript, false, false, false).get(0); // 将 Pig 脚本转换为关系节点
      assertThat(rel.getRowType(), hasToString(rowTypeMatcher)); // 断言关系节点的行类型（字段类型）的字符串表示是否匹配预期的匹配器
    } catch (IOException e) { // 捕获 IO 异常
      throw TestUtil.rethrow(e); // 重新抛出异常
    }
  }

  @Test void testConstantBoolean() { // testConstantBoolean 方法：测试布尔常量的转换
    checkTranslation("g == false", inTree("NOT($6)")); // 验证表达式 "g == false" 转换为关系树中的 NOT($6) 节点，g 是第 6 个字段（索引从 0 开始）
  }

  @Test void testConstantType() { // testConstantType 方法：测试常量类型的推断
    checkType("0L as longCol", containsString("BIGINT longCol")); // 验证长整型常量 0L 被推断为 BIGINT 类型，别名为 longCol
    checkType("0 as intCol", containsString("INTEGER intCol")); // 验证整型常量 0 被推断为 INTEGER 类型，别名为 intCol
    checkType("0.0 as doubleCol", containsString("DOUBLE doubleCol")); // 验证双精度浮点常量 0.0 被推断为 DOUBLE 类型，别名为 doubleCol
    checkType("'0.0' as charCol", containsString("CHAR(3) charCol")); // 验证字符串常量 '0.0' 被推断为 CHAR(3) 类型，别名为 charCol
    checkType("true as boolCol", containsString("BOOLEAN boolCol")); // 验证布尔常量 true 被推断为 BOOLEAN 类型，别名为 boolCol
  }

  @Test void testConstantFloat() { // testConstantFloat 方法：测试浮点常量的转换
    // Add a variable d in the expression to prevent it from being simplified to "false".
    // 添加变量 d 到表达式中，防止常量折叠导致表达式被简化为 "false"
    checkTranslation(".1E6 == -2.3 + d", // 验证浮点表达式 ".1E6 == -2.3 + d" 的转换
        // Validator converts -2.3 from DECIMAL to DOUBLE
        // 验证器将 -2.3 从 DECIMAL 类型转换为 DOUBLE 类型
        inTree("=(100000.0E0, +(-2.3E0, $3))")); // 期望转换后的关系树包含 =(100000.0E0, +(-2.3E0, $3))，d 是第 3 个字段
  }

  @Test void testConstantString() { // testConstantString 方法：测试字符串常量的转换
    checkTranslation("'test' == 'passed'", inTree("=('test', 'passed')")); // 验证字符串相等表达式转换为关系树中的 =('test', 'passed') 节点
  }

  @Test void testProjection() { // testProjection 方法：测试投影操作（选择特定字段）
    checkTranslation("g", inTree("=[$6]")); // 验证投影字段 g 转换为关系树中的 =[$6] 节点，表示选择第 6 个字段
  }

  @Test void testNegation() { // testNegation 方法：测试负号运算符
    checkTranslation("-b == -6", inTree("=(-($1), -6)")); // 验证负数表达式 "-b == -6" 转换为关系树中的 =(-($1), -6)，b 是第 1 个字段
  }

  @Test void testEqual() { // testEqual 方法：测试相等运算符
    checkTranslation("a == 10", inTree("=($0, 10)")); // 验证相等表达式 "a == 10" 转换为关系树中的 =($0, 10)，a 是第 0 个字段
  }

  @Test void testNotEqual() { // testNotEqual 方法：测试不等运算符
    checkTranslation("b != 10", inTree("<>($1, 10)")); // 验证不等表达式 "b != 10" 转换为关系树中的 <>($1, 10)
  }

  @Test void testLessThan() { // testLessThan 方法：测试小于运算符
    checkTranslation("b < 10", inTree("<($1, 10)")); // 验证小于表达式 "b < 10" 转换为关系树中的 <($1, 10)
  }

  @Test void testLessThanEqual() { // testLessThanEqual 方法：测试小于等于运算符
    checkTranslation("b <= 10", inTree("<=($1, 10)")); // 验证小于等于表达式 "b <= 10" 转换为关系树中的 <=($1, 10)
  }

  @Test void testGreaterThan() { // testGreaterThan 方法：测试大于运算符
    checkTranslation("b > 10", inTree(">($1, 10)")); // 验证大于表达式 "b > 10" 转换为关系树中的 >($1, 10)
  }

  @Test void testGreaterThanEqual() { // testGreaterThanEqual 方法：测试大于等于运算符
    checkTranslation("b >= 10", inTree(">=($1, 10)")); // 验证大于等于表达式 "b >= 10" 转换为关系树中的 >=($1, 10)
  }

  @Test @Disabled // @Disabled 注解：禁用此测试方法
  public void testMatch() { // testMatch 方法：测试字符串匹配运算符（已禁用）
    checkTranslation("e matches 'A*BC.D'", inTree("LIKE($4, 'A%BC_D')")); // 验证匹配表达式 "e matches 'A*BC.D'" 转换为 LIKE($4, 'A%BC_D')，Pig 的 * 转换为 SQL 的 %，. 转换为 _
  }

  @Test void testIsNull() { // testIsNull 方法：测试 IS NULL 运算符
    checkTranslation("e is null", inTree("IS NULL($4)")); // 验证 NULL 检查表达式 "e is null" 转换为 IS NULL($4)
  }

  @Test void testIsNotNull() { // testIsNotNull 方法：测试 IS NOT NULL 运算符
    checkTranslation("c is not null", inTree("IS NOT NULL($2)")); // 验证非 NULL 检查表达式 "c is not null" 转换为 IS NOT NULL($2)
  }

  @Test void testNot() { // testNot 方法：测试 NOT 逻辑运算符
    checkTranslation("NOT(a is null)", inTree("IS NOT NULL($0)")); // 验证 NOT 表达式可以简化，"NOT(a is null)" 转换为 IS NOT NULL($0)
    checkTranslation("NOT(g)", inTree("NOT($6)")); // 验证 NOT 表达式 "NOT(g)" 转换为 NOT($6)
  }

  @Test void testAnd() { // testAnd 方法：测试 AND 逻辑运算符
    checkTranslation("a > 10 and g", inTree("AND(>($0, 10), $6)")); // 验证 AND 表达式 "a > 10 and g" 转换为 AND(>($0, 10), $6)
  }

  @Test void testOr() { // testOr 方法：测试 OR 逻辑运算符
    checkTranslation("a > 10 or g", inTree("OR(>($0, 10), $6)")); // 验证 OR 表达式 "a > 10 or g" 转换为 OR(>($0, 10), $6)
  }

  @Test void testAdd() { // testAdd 方法：测试加法运算符
    checkTranslation("b + 3", inTree("+($1, 3)")); // 验证加法表达式 "b + 3" 转换为 +($1, 3)
  }

  @Test void testSubtract() { // testSubtract 方法：测试减法运算符
    checkTranslation("b - 3", inTree("-($1, 3)")); // 验证减法表达式 "b - 3" 转换为 -($1, 3)
  }

  @Test void testMultiply() { // testMultiply 方法：测试乘法运算符
    checkTranslation("b * 3", inTree("*($1, 3)")); // 验证乘法表达式 "b * 3" 转换为 *($1, 3)
  }

  @Test void testMod() { // testMod 方法：测试取模运算符
    checkTranslation("b % 3", inTree("MOD($1, 3)")); // 验证取模表达式 "b % 3" 转换为 MOD($1, 3)
  }

  @Test void testDivide() { // testDivide 方法：测试除法运算符
    checkTranslation("b / 3", inTree("/($1, 3)")); // 验证除法表达式 "b / 3" 转换为 /($1, 3)
    checkTranslation("c / 3.1", inTree("/($2, 3.1E0:DOUBLE)")); // 验证除法表达式 "c / 3.1" 转换为 /($2, 3.1E0:DOUBLE)，常量被转换为 DOUBLE 类型
  }

  @Test void testBinCond() { // testBinCond 方法：测试二元条件运算符（三元运算符）
    checkTranslation("(b == 1 ? 2 : 3)", inTree("CASE(=($1, 1), 2, 3)")); // 验证条件表达式 "(b == 1 ? 2 : 3)" 转换为 CASE(=($1, 1), 2, 3)，即 CASE WHEN b=1 THEN 2 ELSE 3
  }

  @Test void testTupleDereference() { // testTupleDereference 方法：测试元组解引用（访问元组字段）
    checkTranslation("k2.k21", inTree("[$11.k21]")); // 验证元组字段访问 "k2.k21" 转换为 [$11.k21]，k2 是第 11 个字段
    checkTranslation("k2.(k21, k22)", inTree("[ROW($11.k21, $11.k22)]")); // 验证元组多字段访问 "k2.(k21, k22)" 转换为 [ROW($11.k21, $11.k22)]，构造新行
    checkTranslation("k2.k22.(k221,k222)", // 验证嵌套元组字段访问
        inTree("[ROW($11.k22.k221, $11.k22.k222)]")); // 转换为 [ROW($11.k22.k221, $11.k22.k222)]，访问嵌套元组的字段
  }

  @Test void testBagDereference() { // testBagDereference 方法：测试包（Bag）解引用
    checkTranslation("l2.l22", inTree("[MULTISET_PROJECTION($13, 1)]")); // 验证包字段访问 "l2.l22" 转换为 [MULTISET_PROJECTION($13, 1)]，投影包的第 1 个字段
    checkTranslation("l2.(l21, l22)", inTree("[MULTISET_PROJECTION($13, 0, 1)]")); // 验证包多字段访问 "l2.(l21, l22)" 转换为 [MULTISET_PROJECTION($13, 0, 1)]，投影包的第 0 和 1 个字段
  }

  @Test void testMapLookup() { // testMapLookup 方法：测试映射（Map）查找
    checkTranslation("m2#'testKey'", inTree("ITEM($15, 'testKey')")); // 验证映射查找 "m2#'testKey'" 转换为 ITEM($15, 'testKey')，使用 ITEM 函数访问映射
  }

  @Test void testCast() { // testCast 方法：测试类型转换
    checkTranslation("(int) b", inTree("CAST($1):INTEGER")); // 验证转换为整型 "(int) b" 转换为 CAST($1):INTEGER
    checkTranslation("(long) a", inTree("CAST($0):BIGINT")); // 验证转换为长整型 "(long) a" 转换为 CAST($0):BIGINT
    checkTranslation("(float) b", inTree("CAST($1):REAL")); // 验证转换为浮点型 "(float) b" 转换为 CAST($1):REAL（Calcite 中 REAL 对应 float）
    checkTranslation("(double) b", inTree("CAST($1):DOUBLE")); // 验证转换为双精度型 "(double) b" 转换为 CAST($1):DOUBLE
    checkTranslation("(chararray) b", inTree("CAST($1):VARCHAR")); // 验证转换为字符串 "(chararray) b" 转换为 CAST($1):VARCHAR
    checkTranslation("(bytearray) b", inTree("CAST($1):BINARY")); // 验证转换为字节数组 "(bytearray) b" 转换为 CAST($1):BINARY
    checkTranslation("(boolean) c", inTree("CAST($2):BOOLEAN")); // 验证转换为布尔型 "(boolean) c" 转换为 CAST($2):BOOLEAN
    checkTranslation("(biginteger) b", inTree("CAST($1):DECIMAL(19, 0)")); // 验证转换为大整数 "(biginteger) b" 转换为 DECIMAL(19, 0)
    checkTranslation("(bigdecimal) b", inTree("CAST($1):DECIMAL(19, 0)")); // 验证转换为大十进制 "(bigdecimal) b" 转换为 DECIMAL(19, 0)
    checkTranslation("(tuple()) b", inTree("CAST($1):(DynamicRecordRow[])")); // 验证转换为空元组 "(tuple()) b" 转换为动态记录行数组
    checkTranslation("(tuple(int, float)) b", // 验证转换为指定类型的元组
        inTree("CAST($1):RecordType(INTEGER $0, REAL $1)")); // 转换为 RecordType(INTEGER $0, REAL $1)，包含两个字段
    checkTranslation("(bag{}) b", // 验证转换为空包
        inTree("CAST($1):(DynamicRecordRow[]) NOT NULL MULTISET")); // 转换为动态记录行数组的多重集（MULTISET 表示包）
    checkTranslation("(bag{tuple(int)}) b", // 验证转换为包含单元素元组的包
        inTree("CAST($1):RecordType(INTEGER $0) MULTISET")); // 转换为 RecordType(INTEGER $0) MULTISET
    checkTranslation("(bag{tuple(int, float)}) b", // 验证转换为包含两元素元组的包
        inTree("CAST($1):RecordType(INTEGER $0, REAL $1) MULTISET")); // 转换为 RecordType(INTEGER $0, REAL $1) MULTISET
    checkTranslation("(map[]) b", // 验证转换为空映射
        inTree("CAST($1):(VARCHAR NOT NULL, BINARY(1) NOT NULL) MAP")); // 转换为 (VARCHAR NOT NULL, BINARY(1) NOT NULL) MAP，键为字符串，值为二进制
    checkTranslation("(map[int]) b", inTree("CAST($1):(VARCHAR NOT NULL, INTEGER")); // 验证转换为值为整数的映射（注：此行不完整，应为映射类型）
    checkTranslation("(map[tuple(int, float)]) b", // 验证转换为值为元组的映射
        inTree("CAST($1):(VARCHAR NOT NULL, RecordType(INTEGER val_0, REAL val_1)) MAP")); // 转换为 (VARCHAR NOT NULL, RecordType(INTEGER val_0, REAL val_1)) MAP
  }

  @Test void testPigBuiltinFunctions() { // testPigBuiltinFunctions 方法：测试 Pig 内置函数的转换
    checkTranslation("ABS(-5)", inTree("ABS(-5)")); // 验证 ABS 绝对值函数 "ABS(-5)" 转换为 ABS(-5)
    checkTranslation("AddDuration(h, 'P1D')", // 验证 AddDuration 时间加法函数
        inTree("AddDuration(PIG_TUPLE($7, 'P1D'))")); // 转换为 AddDuration(PIG_TUPLE($7, 'P1D'))，将参数打包为元组，P1D 表示 1 天
    checkTranslation("CEIL(1.2)", inTree("CEIL(1.2E0:DOUBLE)")); // 验证 CEIL 向上取整函数 "CEIL(1.2)" 转换为 CEIL(1.2E0:DOUBLE)
  }
}
