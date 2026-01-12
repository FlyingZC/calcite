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
package org.apache.calcite.rex; // 定义包名,该类位于 org.apache.calcite.rex 包下,用于处理关系表达式(RexNode)相关的测试

import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 类,用于表示关系数据类型,是 Calcite 中所有 SQL 类型的基类
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SqlTypeName 枚举,定义了所有 SQL 标准类型名称,如 INTEGER、VARCHAR 等

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解,用于标记测试方法

import static org.hamcrest.CoreMatchers.is; // 导入 Hamcrest 的 is 匹配器,用于断言测试结果
import static org.hamcrest.MatcherAssert.assertThat; // 导入 Hamcrest 的 assertThat 方法,用于编写可读性强的断言

/**
 * Tests for {@link org.apache.calcite.rex.RexUtil#isLosslessCast(RexNode)} and related cases.
 * // 测试类:测试 RexUtil.isLosslessCast(RexNode) 方法及相关用例
 * // 该方法用于判断一个类型转换(CAST)是否是无损的,即转换后不会丢失任何信息
 * // 无损转换的例子: SMALLINT -> INTEGER (范围扩大,不会丢失数据)
 * // 有损转换的例子: INTEGER -> SMALLINT (范围缩小,可能溢出)
 * // 该类继承自 RexProgramTestBase,提供了 Rex 节点构建和简化的测试基础设施
 */
class RexLosslessCastTest extends RexProgramTestBase { // 定义测试类 RexLosslessCastTest,继承自 RexProgramTestBase 基类
  /** Unit test for {@link org.apache.calcite.rex.RexUtil#isLosslessCast(RexNode)}. */
  // // 单元测试:测试 RexUtil.isLosslessCast(RexNode) 方法
  // // 该方法用于判断给定的 RexNode 是否是一个无损的类型转换表达式
  // // 测试涵盖了各种数据类型之间的转换场景,包括数值类型、字符类型等
  @Test void testLosslessCast() { // 定义测试方法 testLosslessCast,使用 @Test 注解标记为 JUnit 测试方法
    final RelDataType tinyIntType = typeFactory.createSqlType(SqlTypeName.TINYINT); // 创建 TINYINT 类型,占用 1 字节,范围 -128 到 127
    final RelDataType smallIntType = typeFactory.createSqlType(SqlTypeName.SMALLINT); // 创建 SMALLINT 类型,占用 2 字节,范围 -32768 到 32767
    final RelDataType intType = typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建 INTEGER 类型,占用 4 字节,范围约 -21亿到21亿
    final RelDataType bigIntType = typeFactory.createSqlType(SqlTypeName.BIGINT); // 创建 BIGINT 类型,占用 8 字节,范围非常大
    final RelDataType floatType = typeFactory.createSqlType(SqlTypeName.FLOAT); // 创建 FLOAT 类型,单精度浮点数,可能有精度损失
    final RelDataType booleanType = typeFactory.createSqlType(SqlTypeName.BOOLEAN); // 创建 BOOLEAN 类型,布尔值 TRUE/FALSE
    final RelDataType charType5 = typeFactory.createSqlType(SqlTypeName.CHAR, 5); // 创建 CHAR(5) 类型,定长字符串,长度固定为 5
    final RelDataType charType6 = typeFactory.createSqlType(SqlTypeName.CHAR, 6); // 创建 CHAR(6) 类型,定长字符串,长度固定为 6
    final RelDataType varCharType10 = typeFactory.createSqlType(SqlTypeName.VARCHAR, 10); // 创建 VARCHAR(10) 类型,变长字符串,最大长度为 10
    final RelDataType varCharType11 = typeFactory.createSqlType(SqlTypeName.VARCHAR, 11); // 创建 VARCHAR(11) 类型,变长字符串,最大长度为 11
    final RelDataType varcharType = typeFactory.createSqlType(SqlTypeName.VARCHAR); // 创建 VARCHAR 类型,变长字符串,无长度限制

    // Negative
    // // 负向测试用例:测试应该返回 false 的场景,即有损转换或非转换表达式
    assertThat(RexUtil.isLosslessCast(rexBuilder.makeInputRef(intType, 0)), is(false)); // 测试:输入引用不是 CAST 表达式,应返回 false
    assertThat(
        RexUtil.isLosslessCast( // 测试:SMALLINT -> TINYINT,范围缩小,可能溢出,应返回 false
            rexBuilder.makeCast( // 创建 CAST 表达式
                tinyIntType, rexBuilder.makeInputRef(smallIntType, 0))), is(false)); // 从 SMALLINT 转换到 TINYINT,范围缩小,数据可能丢失
    assertThat(
        RexUtil.isLosslessCast( // 测试:INTEGER -> SMALLINT,范围缩小,可能溢出,应返回 false
            rexBuilder.makeCast( // 创建 CAST 表达式
                smallIntType, rexBuilder.makeInputRef(intType, 0))), is(false)); // 从 INTEGER 转换到 SMALLINT,范围缩小,数据可能丢失
    assertThat(
        RexUtil.isLosslessCast( // 测试:BIGINT -> INTEGER,范围缩小,可能溢出,应返回 false
            rexBuilder.makeCast( // 创建 CAST 表达式
                intType, rexBuilder.makeInputRef(bigIntType, 0))), is(false)); // 从 BIGINT 转换到 INTEGER,范围缩小,数据可能丢失
    assertThat(
        RexUtil.isLosslessCast( // 测试:FLOAT -> BIGINT,从浮点数转整数,精度丢失,应返回 false
            rexBuilder.makeCast( // 创建 CAST 表达式
                bigIntType, rexBuilder.makeInputRef(floatType, 0))), is(false)); // 从 FLOAT 转换到 BIGINT,小数部分会丢失
    assertThat(
        RexUtil.isLosslessCast( // 测试:BIGINT -> BOOLEAN,类型不兼容,应返回 false
            rexBuilder.makeCast( // 创建 CAST 表达式
                booleanType, rexBuilder.makeInputRef(bigIntType, 0))), is(false)); // 从 BIGINT 转换到 BOOLEAN,语义不明确
    assertThat(
        RexUtil.isLosslessCast( // 测试:CHAR(5) -> INTEGER,字符转整数,应返回 false
            rexBuilder.makeCast( // 创建 CAST 表达式
                intType, rexBuilder.makeInputRef(charType5, 0))), is(false)); // 从 CHAR 转换到 INTEGER,需要解析,可能失败
    assertThat(
        RexUtil.isLosslessCast( // 测试:VARCHAR(10) -> INTEGER,字符转整数,应返回 false
            rexBuilder.makeCast( // 创建 CAST 表达式
                intType, rexBuilder.makeInputRef(varCharType10, 0))), is(false)); // 从 VARCHAR 转换到 INTEGER,需要解析,可能失败
    assertThat(
        RexUtil.isLosslessCast( // 测试:VARCHAR(11) -> VARCHAR(10),长度缩小,可能截断,应返回 false
            rexBuilder.makeCast( // 创建 CAST 表达式
                varCharType10, rexBuilder.makeInputRef(varCharType11, 0))), is(false)); // 从 VARCHAR(11) 转换到 VARCHAR(10),长度缩小,可能截断
    assertThat(
        RexUtil.isLosslessCast( // 测试:BIGINT -> CHAR(5),整数转字符,应返回 false
            rexBuilder.makeCast( // 创建 CAST 表达式
                charType5, rexBuilder.makeInputRef(bigIntType, 0))), is(false)); // 从 BIGINT 转换到 CHAR(5),可能需要截断
    assertThat(
        RexUtil.isLosslessCast( // 测试:SMALLINT -> CHAR(5),整数转字符,应返回 false
            rexBuilder.makeCast( // 创建 CAST 表达式
                charType5, rexBuilder.makeInputRef(smallIntType, 0))), is(false)); // 从 SMALLINT 转换到 CHAR(5),类型转换
    assertThat(
        RexUtil.isLosslessCast( // 测试:INTEGER -> VARCHAR(10),整数转字符,应返回 false
            rexBuilder.makeCast( // 创建 CAST 表达式
                varCharType10, rexBuilder.makeInputRef(intType, 0))), is(false)); // 从 INTEGER 转换到 VARCHAR(10),类型转换

    // Positive
    // // 正向测试用例:测试应该返回 true 的场景,即无损转换
    assertThat(
        RexUtil.isLosslessCast( // 测试:TINYINT -> SMALLINT,范围扩大,应返回 true
            rexBuilder.makeCast( // 创建 CAST 表达式
                smallIntType, rexBuilder.makeInputRef(tinyIntType, 0))), is(true)); // 从 TINYINT 转换到 SMALLINT,范围扩大,不会丢失数据
    assertThat(
        RexUtil.isLosslessCast( // 测试:SMALLINT -> INTEGER,范围扩大,应返回 true
            rexBuilder.makeCast( // 创建 CAST 表达式
                intType, rexBuilder.makeInputRef(smallIntType, 0))), is(true)); // 从 SMALLINT 转换到 INTEGER,范围扩大,不会丢失数据
    assertThat(
        RexUtil.isLosslessCast( // 测试:INTEGER -> BIGINT,范围扩大,应返回 true
            rexBuilder.makeCast( // 创建 CAST 表达式
                bigIntType, rexBuilder.makeInputRef(intType, 0))), is(true)); // 从 INTEGER 转换到 BIGINT,范围扩大,不会丢失数据
    assertThat(
        RexUtil.isLosslessCast( // 测试:INTEGER -> INTEGER,相同类型,应返回 true
            rexBuilder.makeCast( // 创建 CAST 表达式
                intType, rexBuilder.makeInputRef(intType, 0))), is(true)); // 从 INTEGER 转换到 INTEGER,类型相同,不会丢失数据
    assertThat(
        RexUtil.isLosslessCast( // 测试:SMALLINT -> CHAR(6),长度足够,应返回 true
            rexBuilder.makeCast( // 创建 CAST 表达式
                charType6, rexBuilder.makeInputRef(smallIntType, 0))), is(true)); // 从 SMALLINT 转换到 CHAR(6),长度足够容纳 SMALLINT 的字符串表示
    assertThat(
        RexUtil.isLosslessCast( // 测试:SMALLINT -> VARCHAR(10),长度足够,应返回 true
            rexBuilder.makeCast( // 创建 CAST 表达式
                varCharType10, rexBuilder.makeInputRef(smallIntType, 0))), is(true)); // 从 SMALLINT 转换到 VARCHAR(10),长度足够容纳 SMALLINT 的字符串表示
    assertThat(
        RexUtil.isLosslessCast( // 测试:INTEGER -> VARCHAR(11),长度足够,应返回 true
            rexBuilder.makeCast( // 创建 CAST 表达式
                varCharType11, rexBuilder.makeInputRef(intType, 0))), is(true)); // 从 INTEGER 转换到 VARCHAR(11),长度足够容纳 INTEGER 的字符串表示
    assertThat(
        RexUtil.isLosslessCast( // 测试:CHAR(6) -> VARCHAR(11),长度扩大,应返回 true
            rexBuilder.makeCast( // 创建 CAST 表达式
                varCharType11, rexBuilder.makeInputRef(charType6, 0))), is(true)); // 从 CHAR(6) 转换到 VARCHAR(11),长度扩大,不会丢失数据
    assertThat(
        RexUtil.isLosslessCast( // 测试:VARCHAR(10) -> VARCHAR(11),长度扩大,应返回 true
            rexBuilder.makeCast( // 创建 CAST 表达式
                varCharType11, rexBuilder.makeInputRef(varCharType10, 0))), is(true)); // 从 VARCHAR(10) 转换到 VARCHAR(11),长度扩大,不会丢失数据
    assertThat(
        RexUtil.isLosslessCast( // 测试:INTEGER -> VARCHAR(无长度限制),应返回 true
            rexBuilder.makeCast( // 创建 CAST 表达式
                varcharType, rexBuilder.makeInputRef(intType, 0))), is(true)); // 从 INTEGER 转换到 VARCHAR,无长度限制,不会丢失数据
  }

  @Test void removeRedundantCast() { // 定义测试方法 removeRedundantCast,测试移除冗余的类型转换
    checkSimplify(cast(vInt(), nullable(tInt())), "?0.int0"); // 测试:移除可空 INTEGER 到可空 INTEGER 的冗余转换,简化为输入引用
    checkSimplifyUnchanged(cast(vInt(), tInt())); // 测试:INTEGER 到 INTEGER 的转换,但类型不同(非空 vs 可空),不应简化
    checkSimplify(cast(vIntNotNull(), nullable(tInt())), "?0.notNullInt0"); // 测试:移除非空 INTEGER 到可空 INTEGER 的冗余转换,简化为输入引用
    checkSimplify(cast(vIntNotNull(), tInt()), "?0.notNullInt0"); // 测试:移除非空 INTEGER 到非空 INTEGER 的冗余转换,简化为输入引用

    // Nested int int cast is removed
    // // 嵌套的 INTEGER 到 INTEGER 转换应该被移除
    checkSimplify(cast(cast(vVarchar(), tInt()), tInt()), // 测试:嵌套转换 VARCHAR -> INTEGER -> INTEGER,外层转换应被移除
        "CAST(?0.varchar0):INTEGER NOT NULL"); // 预期结果:保留内层的 VARCHAR -> INTEGER 转换
    checkSimplifyUnchanged(cast(cast(vVarchar(), tInt()), tVarchar())); // 测试:嵌套转换 VARCHAR -> INTEGER -> VARCHAR,不应简化,因为类型不同
  }

  @Test void removeLosslesssCastInt() { // 定义测试方法 removeLosslesssCastInt,测试移除整数类型的无损转换
    checkSimplifyUnchanged(cast(vInt(), tBigInt())); // 测试:INTEGER -> BIGINT,范围扩大但不是冗余转换,不应简化
    // A.1
    // // 测试场景 A.1:嵌套转换 INTEGER -> BIGINT -> INTEGER,外层是无损转换,应被移除
    checkSimplify(cast(cast(vInt(), tBigInt()), tInt()), "CAST(?0.int0):INTEGER NOT NULL"); // 预期结果:移除外层的 BIGINT -> INTEGER 转换,保留内层的 INTEGER -> BIGINT -> INTEGER
    RexNode core = cast(vIntNotNull(), tBigInt()); // 创建核心节点:非空 INTEGER -> BIGINT 的转换
    checkSimplify(cast(core, tInt()), "?0.notNullInt0"); // 测试:BIGINT -> INTEGER 是无损的(因为原始是非空 INTEGER),应完全移除转换链
    checkSimplify( // 测试:嵌套转换 BIGINT -> INTEGER -> BIGINT,中间的 INTEGER -> BIGINT 是无损的
        cast(cast(core, tInt()), tBigInt()), // 预期结果:简化为 BIGINT -> BIGINT 的转换
        "CAST(?0.notNullInt0):BIGINT NOT NULL"); // 保留外层转换,内层被简化
    checkSimplify( // 测试:三层嵌套 BIGINT -> INTEGER -> BIGINT -> INTEGER,后两层是无损转换
        cast(cast(cast(core, tInt()), tBigInt()), tInt()), // 预期结果:简化为 BIGINT -> INTEGER 的转换
        "?0.notNullInt0"); // 完全移除转换链,返回原始输入引用
    checkSimplify(cast(cast(vInt(), tVarchar()), tInt()), "CAST(?0.int0):INTEGER NOT NULL"); // 测试:嵌套转换 INTEGER -> VARCHAR -> INTEGER,外层转换应被移除
  }

  @Test void removeLosslesssCastChar() { // 定义测试方法 removeLosslesssCastChar,测试移除字符类型的无损转换
    checkSimplifyUnchanged(cast(vVarchar(), tChar(3))); // 测试:VARCHAR -> CHAR(3),长度缩小,不是无损转换,不应简化
    checkSimplifyUnchanged(cast(cast(vVarchar(), tChar(3)), tVarchar(5))); // 测试:嵌套转换 VARCHAR -> CHAR(3) -> VARCHAR(5),不应简化

    RexNode char2 = vParam("char(2)_", tChar(2)); // 创建 CHAR(2) 类型的输入引用,用于测试
    RexNode char6 = vParam("char(6)_", tChar(6)); // 创建 CHAR(6) 类型的输入引用,用于测试
    RexNode varchar2 = vParam("varchar(2)_", tChar(2)); // 创建 VARCHAR(2) 类型的输入引用,用于测试
    // A.2 in RexSimplify
    // // 测试场景 A.2 (对应 RexSimplify 中的逻辑):嵌套转换 CHAR(2) -> CHAR(5) -> CHAR(2),外层是无损转换
    checkSimplify( // 测试:CHAR(2) -> CHAR(5) -> CHAR(2),外层转换是无损的(因为原始是 CHAR(2))
        cast(cast(char2, tChar(5)), tChar(2)), // 预期结果:移除外层的 CHAR(5) -> CHAR(2) 转换
        "CAST(?0.char(2)_0):CHAR(2) NOT NULL"); // 保留内层的 CHAR(2) -> CHAR(5) 转换
    // B.1
    // // 测试场景 B.1:嵌套转换 CHAR(2) -> CHAR(4) -> CHAR(5),外层转换是无损的
    checkSimplify( // 测试:CHAR(2) -> CHAR(4) -> CHAR(5),外层转换是无损的(因为原始是 CHAR(2))
        cast(cast(char2, tChar(4)), tChar(5)), // 预期结果:简化为 CHAR(2) -> CHAR(5) 的转换
        "CAST(?0.char(2)_0):CHAR(5) NOT NULL"); // 移除中间的 CHAR(4),直接转换为 CHAR(5)
    // B.2
    // // 测试场景 B.2:嵌套转换 CHAR(2) -> CHAR(10) -> CHAR(5),外层转换是无损的
    checkSimplify( // 测试:CHAR(2) -> CHAR(10) -> CHAR(5),外层转换是无损的(因为原始是 CHAR(2))
        cast(cast(char2, tChar(10)), tChar(5)), // 预期结果:简化为 CHAR(2) -> CHAR(5) 的转换
        "CAST(?0.char(2)_0):CHAR(5) NOT NULL"); // 移除中间的 CHAR(10),直接转换为 CHAR(5)
    // B.3
    // // 测试场景 B.3:嵌套转换 CHAR(2) -> VARCHAR(10) -> CHAR(5),外层转换是无损的
    checkSimplify( // 测试:CHAR(2) -> VARCHAR(10) -> CHAR(5),外层转换是无损的(因为原始是 CHAR(2))
        cast(cast(char2, tVarchar(10)), tChar(5)), // 预期结果:简化为 CHAR(2) -> CHAR(5) 的转换
        "CAST(?0.char(2)_0):CHAR(5) NOT NULL"); // 移除中间的 VARCHAR(10),直接转换为 CHAR(5)
    // B.4
    // // 测试场景 B.4:嵌套转换 CHAR(6) -> VARCHAR(10) -> CHAR(5),外层转换是有损的(因为原始是 CHAR(6),目标长度 5 不够)
    checkSimplify( // 测试:CHAR(6) -> VARCHAR(10) -> CHAR(5),外层转换是有损的,但仍然可以简化
        cast(cast(char6, tVarchar(10)), tChar(5)), // 预期结果:简化为 CHAR(6) -> CHAR(5) 的转换
        "CAST(?0.char(6)_0):CHAR(5) NOT NULL"); // 移除中间的 VARCHAR(10),直接转换为 CHAR(5)
    // C.1
    // // 测试场景 C.1:嵌套转换 CHAR(6) -> CHAR(3) -> CHAR(5),中间转换是有损的(长度缩小),不应简化
    checkSimplifyUnchanged( // 测试:CHAR(6) -> CHAR(3) -> CHAR(5),中间的 CHAR(3) 转换是有损的
        cast(cast(char6, tChar(3)), tChar(5))); // 不应简化,因为中间转换可能截断数据
    // C.2
    // // 测试场景 C.2:嵌套转换 VARCHAR(2) -> CHAR(5) -> VARCHAR(2),外层转换是有损的(长度缩小),不应简化
    checkSimplifyUnchanged( // 测试:VARCHAR(2) -> CHAR(5) -> VARCHAR(2),外层的 VARCHAR(2) 转换是有损的
        cast(cast(varchar2, tChar(5)), tVarchar(2))); // 不应简化,因为外层转换可能截断数据
    // C.3
    // // 测试场景 C.3:嵌套转换 CHAR(2) -> CHAR(4) -> VARCHAR(5),外层转换到 VARCHAR,不应简化
    checkSimplifyUnchanged( // 测试:CHAR(2) -> CHAR(4) -> VARCHAR(5),外层转换到 VARCHAR 类型
        cast(cast(char2, tChar(4)), tVarchar(5))); // 不应简化,因为类型从 CHAR 变为 VARCHAR
  }
} // 类定义结束
