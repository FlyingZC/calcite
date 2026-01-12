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
package org.apache.calcite.test; // org.apache.calcite.test包，包含Calcite框架的测试类
import org.apache.calcite.avatica.util.TimeUnitRange; // 导入时间单位范围枚举，用于FLOOR/CEIL等时间函数
import org.apache.calcite.plan.RexImplicationChecker; // 导入Rex表达式蕴含检查器，用于判断一个表达式是否蕴含另一个表达式
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示Calcite中的数据类型
import org.apache.calcite.rex.RexCall; // 导入Rex调用表达式，表示函数调用或操作符调用
import org.apache.calcite.rex.RexLiteral; // 导入Rex字面量表达式，表示常量值
import org.apache.calcite.rex.RexNode; // 导入Rex节点基类，所有Rex表达式的抽象基类
import org.apache.calcite.rex.RexSimplify; // 导入Rex表达式简化器，用于优化和简化Rex表达式
import org.apache.calcite.rex.RexUnknownAs; // 导入未知值处理方式枚举，指定如何处理UNKNOWN（SQL三值逻辑中的NULL）
import org.apache.calcite.sql.SqlKind; // 导入SQL操作符类型枚举，定义各种SQL操作符类型
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入标准SQL操作符表，包含所有标准SQL操作符
import org.apache.calcite.util.DateString; // 导入日期字符串工具类，用于处理日期字面量
import org.apache.calcite.util.TimeString; // 导入时间字符串工具类，用于处理时间字面量
import org.apache.calcite.util.TimestampString; // 导入时间戳字符串工具类，用于处理时间戳字面量
import org.apache.calcite.util.Util; // 导入工具类，提供各种通用工具方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表，用于创建不可修改的列表

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，标记测试方法

import static org.apache.calcite.test.RexImplicationCheckerFixtures.Fixture; // 导入测试夹具类，提供测试辅助方法

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言工具，用于相等性断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具，用于通用断言
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest断言工具，用于字符串匹配断言

/**
 * Unit tests for {@link RexImplicationChecker}.
 * RexImplicationChecker的单元测试类
 *
 * RexImplicationChecker（Rex表达式蕴含检查器）是Calcite中用于判断两个Rex表达式之间蕴含关系的工具类
 * 蕴含关系是指：如果表达式A为真，则表达式B一定为真，记作A -> B（A蕴含B）
 * 这个功能在查询优化中非常重要，特别是在：
 * 1. 谓词下推（Predicate Pushdown）：判断是否可以将过滤条件下推到子查询
 * 2. 物化视图替换（Materialized View Substitution）：判断物化视图是否满足查询要求
 * 3. 条件简化（Condition Simplification）：根据蕴含关系简化复杂的条件表达式
 * 4. 查询重写（Query Rewriting）：根据蕴含关系进行等价转换
 *
 * 本测试类通过大量测试用例验证RexImplicationChecker在各种场景下的正确性，包括：
 * - 基本比较操作符（大于、小于、等于、不等于等）
 * - 各种数据类型（整数、小数、布尔值、字符串、日期、时间戳等）
 * - 逻辑运算符（AND、OR）
 * - NULL值判断（IS NULL、IS NOT NULL）
 * - 范围条件（BETWEEN）
 * - 表达式简化和类型转换
 *
 * 测试类使用了Fixture模式（测试夹具），通过RexImplicationCheckerFixtures.Fixture提供
 * 测试所需的各种辅助方法和数据结构，使测试代码更加简洁和可维护
 */
public class RexImplicationCheckerTest { // RexImplicationChecker的单元测试类，测试表达式蕴含检查功能
  //~ Instance fields -------------------------------------------------------- // 实例字段分隔符，标记成员变量区域的开始
  // 注意：本测试类没有定义实例成员变量，所有测试需要的资源都通过Fixture对象获取
  // Fixture是RexImplicationCheckerFixtures的静态内部类，提供了测试所需的：
  // - RexBuilder：用于构建各种Rex表达式
  // - TypeFactory：用于创建各种数据类型
  // - RexSimplify：用于简化Rex表达式
  // - RexImplicationChecker：实际进行蕴含检查的对象
  // - 各种测试用的字段引用（如f.i表示整数字段，f.str表示字符串字段等）
  // - 创建各种类型字面量的方法（如literal、floatLiteral、longLiteral等）
  // - 创建各种比较表达式的方法（如gt、lt、eq、ne等）
  // - 创建逻辑表达式的方法（如and、or）
  // - 创建NULL检查表达式的方法（如isNull、notNull）
  // - 验证蕴含关系的方法（如checkImplies、checkNotImplies）

  //~ Methods ---------------------------------------------------------------- // 方法分隔符，标记方法区域的开始

  // Simple Tests for Operators // 简单操作符测试部分的注释
  @Test void testSimpleGreaterCond() { // 测试简单的大于条件蕴含关系，验证大于和大于等于操作符的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象，提供测试所需的所有工具和数据
    final RexNode iGt10 = f.gt(f.i, f.literal(10)); // 创建表达式：i > 10，其中f.i是整数字段引用，f.literal(10)是整数字面量10
    final RexNode iGt30 = f.gt(f.i, f.literal(30)); // 创建表达式：i > 30
    final RexNode iGe30 = f.ge(f.i, f.literal(30)); // 创建表达式：i >= 30
    final RexNode iGe10 = f.ge(f.i, f.literal(10)); // 创建表达式：i >= 10
    final RexNode iEq30 = f.eq(f.i, f.literal(30)); // 创建表达式：i = 30
    final RexNode iNe10 = f.ne(f.i, f.literal(10)); // 创建表达式：i != 10

    f.checkImplies(iGt30, iGt10); // 验证：i > 30 蕴含 i > 10（如果i大于30，则必然大于10），应该返回true
    f.checkNotImplies(iGt10, iGt30); // 验证：i > 10 不蕴含 i > 30（i可能等于20），应该返回false
    f.checkNotImplies(iGt10, iGe30); // 验证：i > 10 不蕴含 i >= 30（i可能等于20），应该返回false
    f.checkImplies(iGe30, iGt10); // 验证：i >= 30 蕴含 i > 10（如果i大于等于30，则必然大于10），应该返回true
    f.checkImplies(iEq30, iGt10); // 验证：i = 30 蕴含 i > 10（如果i等于30，则必然大于10），应该返回true
    f.checkNotImplies(iGt10, iEq30); // 验证：i > 10 不蕴含 i = 30（i可能等于20），应该返回false
    f.checkNotImplies(iGt10, iNe10); // 验证：i > 10 不蕴含 i != 10（虽然逻辑上成立，但蕴含检查器可能不处理这种情况）
    f.checkNotImplies(iGe10, iNe10); // 验证：i >= 10 不蕴含 i != 10（当i=10时，i>=10为真但i!=10为假）
    // identity // 自反性测试，任何表达式都蕴含自身
    f.checkImplies(iGt10, iGt10); // 验证：i > 10 蕴含 i > 10（自反性），应该返回true
    f.checkImplies(iGe30, iGe30); // 验证：i >= 30 蕴含 i >= 30（自反性），应该返回true
  }

  @Test void testSimpleLesserCond() { // 测试简单的小于条件蕴含关系，验证小于和小于等于操作符的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final RexNode iLt10 = f.lt(f.i, f.literal(10)); // 创建表达式：i < 10
    final RexNode iLt30 = f.lt(f.i, f.literal(30)); // 创建表达式：i < 30
    final RexNode iLe30 = f.le(f.i, f.literal(30)); // 创建表达式：i <= 30
    final RexNode iLe10 = f.le(f.i, f.literal(10)); // 创建表达式：i <= 10
    final RexNode iEq10 = f.eq(f.i, f.literal(10)); // 创建表达式：i = 10
    final RexNode iNe10 = f.ne(f.i, f.literal(10)); // 创建表达式：i != 10

    f.checkImplies(iLt10, iLt30); // 验证：i < 10 蕴含 i < 30（如果i小于10，则必然小于30），应该返回true
    f.checkNotImplies(iLt30, iLt10); // 验证：i < 30 不蕴含 i < 10（i可能等于20），应该返回false
    f.checkImplies(iLt10, iLe30); // 验证：i < 10 蕴含 i <= 30（如果i小于10，则必然小于等于30），应该返回true
    f.checkNotImplies(iLe30, iLt10); // 验证：i <= 30 不蕴含 i < 10（i可能等于10），应该返回false
    f.checkImplies(iEq10, iLt30); // 验证：i = 10 蕴含 i < 30（如果i等于10，则必然小于30），应该返回true
    f.checkNotImplies(iLt30, iEq10); // 验证：i < 30 不蕴含 i = 10（i可能等于20），应该返回false
    f.checkNotImplies(iLt10, iEq10); // 验证：i < 10 不蕴含 i = 10（i小于10时不可能等于10），应该返回false
    f.checkNotImplies(iLt10, iNe10); // 验证：i < 10 不蕴含 i != 10（虽然逻辑上成立，但蕴含检查器可能不处理）
    f.checkNotImplies(iLe10, iNe10); // 验证：i <= 10 不蕴含 i != 10（当i=10时，i<=10为真但i!=10为假）
    // identity // 自反性测试
    f.checkImplies(iLt10, iLt10); // 验证：i < 10 蕴含 i < 10（自反性），应该返回true
    f.checkImplies(iLe30, iLe30); // 验证：i <= 30 蕴含 i <= 30（自反性），应该返回true
  }

  @Test void testSimpleEq() { // 测试简单的等于条件蕴含关系，验证等于和不等操作符的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final RexNode iEq30 = f.eq(f.i, f.literal(30)); // 创建表达式：i = 30
    final RexNode iNe10 = f.ne(f.i, f.literal(10)); // 创建表达式：i != 10
    final RexNode iNe30 = f.ne(f.i, f.literal(30)); // 创建表达式：i != 30

    f.checkImplies(iEq30, iEq30); // 验证：i = 30 蕴含 i = 30（自反性），应该返回true
    f.checkImplies(iNe10, iNe10); // 验证：i != 10 蕴含 i != 10（自反性），应该返回true
    f.checkImplies(iEq30, iNe10); // 验证：i = 30 蕴含 i != 10（如果i等于30，则必然不等于10），应该返回true
    f.checkNotImplies(iNe10, iEq30); // 验证：i != 10 不蕴含 i = 30（i可能等于20），应该返回false
    f.checkNotImplies(iNe30, iEq30); // 验证：i != 30 不蕴含 i = 30（i不等于30时不可能等于30），应该返回false
  }

  // Simple Tests for DataTypes // 简单数据类型测试部分的注释
  @Test void testSimpleDec() { // 测试简单的小数类型蕴含关系，验证小数比较的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final RexNode node1 = f.lt(f.dec, f.floatLiteral(30.9)); // 创建表达式：dec < 30.9，其中f.dec是小数字段
    final RexNode node2 = f.lt(f.dec, f.floatLiteral(40.33)); // 创建表达式：dec < 40.33

    f.checkImplies(node1, node2); // 验证：dec < 30.9 蕴含 dec < 40.33（如果dec小于30.9，则必然小于40.33），应该返回true
    f.checkNotImplies(node2, node1); // 验证：dec < 40.33 不蕴含 dec < 30.9（dec可能等于35），应该返回false
  }

  @Test void testSimpleBoolean() { // 测试简单的布尔类型蕴含关系，验证布尔值比较的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final RexNode bEqTrue = f.eq(f.bl, f.rexBuilder.makeLiteral(true)); // 创建表达式：bl = TRUE，其中f.bl是布尔字段
    final RexNode bEqFalse = f.eq(f.bl, f.rexBuilder.makeLiteral(false)); // 创建表达式：bl = FALSE

    if (false) { // 条件为false，下面的代码不会执行，这是预留的TODO项
      // TODO: Need to support false -> true // TODO：需要支持 FALSE -> TRUE 的蕴含关系
      f.checkImplies(bEqFalse, bEqTrue); // 如果启用，验证：bl = FALSE 蕴含 bl = TRUE（这在三值逻辑中不成立）
    }
    f.checkNotImplies(bEqTrue, bEqFalse); // 验证：bl = TRUE 不蕴含 bl = FALSE（bl为TRUE时不可能为FALSE），应该返回false
  }

  @Test void testSimpleLong() { // 测试简单的长整型蕴含关系，验证长整型比较的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final RexNode xGeBig = f.ge(f.lg, f.longLiteral(324324L)); // 创建表达式：lg >= 324324L，其中f.lg是长整型字段
    final RexNode xGtBigger = f.gt(f.lg, f.longLiteral(324325L)); // 创建表达式：lg > 324325L
    final RexNode xGeBigger = f.ge(f.lg, f.longLiteral(324325L)); // 创建表达式：lg >= 324325L

    f.checkImplies(xGtBigger, xGeBig); // 验证：lg > 324325L 蕴含 lg >= 324324L（如果lg大于324325，则必然大于等于324324），应该返回true
    f.checkImplies(xGtBigger, xGeBigger); // 验证：lg > 324325L 蕴含 lg >= 324325L（如果lg大于324325，则必然大于等于324325），应该返回true
    f.checkImplies(xGeBigger, xGeBig); // 验证：lg >= 324325L 蕴含 lg >= 324324L（如果lg大于等于324325，则必然大于等于324324），应该返回true
    f.checkNotImplies(xGeBig, xGtBigger); // 验证：lg >= 324324L 不蕴含 lg > 324325L（lg可能等于324324），应该返回false
  }

  @Test void testSimpleShort() { // 测试简单的短整型蕴含关系，验证短整型比较的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final RexNode xGe10 = f.ge(f.sh, f.shortLiteral((short) 10)); // 创建表达式：sh >= 10，其中f.sh是短整型字段
    final RexNode xGe11 = f.ge(f.sh, f.shortLiteral((short) 11)); // 创建表达式：sh >= 11

    f.checkImplies(xGe11, xGe10); // 验证：sh >= 11 蕴含 sh >= 10（如果sh大于等于11，则必然大于等于10），应该返回true
    f.checkNotImplies(xGe10, xGe11); // 验证：sh >= 10 不蕴含 sh >= 11（sh可能等于10），应该返回false
  }

  @Test void testSimpleChar() { // 测试简单的字符类型蕴含关系，验证字符比较的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final RexNode xGeB = f.ge(f.ch, f.charLiteral("b")); // 创建表达式：ch >= 'b'，其中f.ch是字符字段
    final RexNode xGeA = f.ge(f.ch, f.charLiteral("a")); // 创建表达式：ch >= 'a'

    f.checkImplies(xGeB, xGeA); // 验证：ch >= 'b' 蕴含 ch >= 'a'（如果ch大于等于'b'，则必然大于等于'a'），应该返回true
    f.checkNotImplies(xGeA, xGeB); // 验证：ch >= 'a' 不蕴含 ch >= 'b'（ch可能等于'a'），应该返回false
  }

  @Test void testSimpleString() { // 测试简单的字符串类型蕴含关系，验证字符串比较的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final RexNode node1 = f.eq(f.str, f.rexBuilder.makeLiteral("en")); // 创建表达式：str = 'en'，其中f.str是字符串字段

    f.checkImplies(node1, node1); // 验证：str = 'en' 蕴含 str = 'en'（自反性），应该返回true
  }

  @Test void testSimpleDate() { // 测试简单的日期类型蕴含关系，验证日期比较的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final DateString d = DateString.fromCalendarFields(Util.calendar()); // 创建当前日期的DateString对象
    final RexNode node1 = f.ge(f.d, f.dateLiteral(d)); // 创建表达式：d >= 当前日期，其中f.d是日期字段
    final RexNode node2 = f.eq(f.d, f.dateLiteral(d)); // 创建表达式：d = 当前日期
    f.checkImplies(node2, node1); // 验证：d = 当前日期 蕴含 d >= 当前日期（如果d等于当前日期，则必然大于等于当前日期），应该返回true
    f.checkNotImplies(node1, node2); // 验证：d >= 当前日期 不蕴含 d = 当前日期（d可能大于当前日期），应该返回false

    final DateString dBeforeEpoch1 = DateString.fromDaysSinceEpoch(-12345); // 创建epoch之前12345天的日期
    final DateString dBeforeEpoch2 = DateString.fromDaysSinceEpoch(-123); // 创建epoch之前123天的日期
    final RexNode nodeBe1 = f.lt(f.d, f.dateLiteral(dBeforeEpoch1)); // 创建表达式：d < dBeforeEpoch1
    final RexNode nodeBe2 = f.lt(f.d, f.dateLiteral(dBeforeEpoch2)); // 创建表达式：d < dBeforeEpoch2
    f.checkImplies(nodeBe1, nodeBe2); // 验证：d < dBeforeEpoch1 蕴含 d < dBeforeEpoch2（因为-12345 < -123），应该返回true
    f.checkNotImplies(nodeBe2, nodeBe1); // 验证：d < dBeforeEpoch2 不蕴含 d < dBeforeEpoch1（d可能在-12345和-123之间），应该返回false
  }

  @Test void testSimpleTimeStamp() { // 测试简单的时间戳类型蕴含关系，验证时间戳比较的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final TimestampString ts = // 创建当前时间戳的TimestampString对象
        TimestampString.fromCalendarFields(Util.calendar());
    final RexNode node1 = f.lt(f.ts, f.timestampLiteral(ts)); // 创建表达式：ts < 当前时间戳，其中f.ts是时间戳字段
    final RexNode node2 = f.le(f.ts, f.timestampLiteral(ts)); // 创建表达式：ts <= 当前时间戳
    f.checkImplies(node1, node2); // 验证：ts < 当前时间戳 蕴含 ts <= 当前时间戳（如果ts小于当前时间戳，则必然小于等于），应该返回true
    f.checkNotImplies(node2, node1); // 验证：ts <= 当前时间戳 不蕴含 ts < 当前时间戳（ts可能等于当前时间戳），应该返回false

    final TimestampString tsBeforeEpoch1 = // 创建epoch之前的时间戳（毫秒数）
        TimestampString.fromMillisSinceEpoch(-1234567890L);
    final TimestampString tsBeforeEpoch2 = // 创建另一个epoch之前的时间戳（毫秒数更接近0）
        TimestampString.fromMillisSinceEpoch(-1234567L);
    final RexNode nodeBe1 = f.lt(f.ts, f.timestampLiteral(tsBeforeEpoch1)); // 创建表达式：ts < tsBeforeEpoch1
    final RexNode nodeBe2 = f.lt(f.ts, f.timestampLiteral(tsBeforeEpoch2)); // 创建表达式：ts < tsBeforeEpoch2
    f.checkImplies(nodeBe1, nodeBe2); // 验证：ts < tsBeforeEpoch1 蕴含 ts < tsBeforeEpoch2（因为-1234567890 < -1234567），应该返回true
    f.checkNotImplies(nodeBe2, nodeBe1); // 验证：ts < tsBeforeEpoch2 不蕴含 ts < tsBeforeEpoch1（ts可能在两个时间戳之间），应该返回false
  }

  @Test void testSimpleTime() { // 测试简单的时间类型蕴含关系，验证时间比较的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final TimeString t = TimeString.fromCalendarFields(Util.calendar()); // 创建当前时间的TimeString对象
    final RexNode node1 = f.lt(f.t, f.timeLiteral(t)); // 创建表达式：t < 当前时间，其中f.t是时间字段
    final RexNode node2 = f.le(f.t, f.timeLiteral(t)); // 创建表达式：t <= 当前时间
    f.checkImplies(node1, node2); // 验证：t < 当前时间 蕴含 t <= 当前时间（如果t小于当前时间，则必然小于等于），应该返回true
    f.checkNotImplies(node2, node1); // 验证：t <= 当前时间 不蕴含 t < 当前时间（t可能等于当前时间），应该返回false
  }

  @Test void testSimpleBetween() { // 测试简单的BETWEEN条件蕴含关系，验证范围条件的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final RexNode iGe30 = f.ge(f.i, f.literal(30)); // 创建表达式：i >= 30
    final RexNode iLt70 = f.lt(f.i, f.literal(70)); // 创建表达式：i < 70
    final RexNode iGe30AndLt70 = f.and(iGe30, iLt70); // 创建表达式：i >= 30 AND i < 70（即i在[30, 70)范围内）
    final RexNode iGe50 = f.ge(f.i, f.literal(50)); // 创建表达式：i >= 50
    final RexNode iLt60 = f.lt(f.i, f.literal(60)); // 创建表达式：i < 60
    final RexNode iGe50AndLt60 = f.and(iGe50, iLt60); // 创建表达式：i >= 50 AND i < 60（即i在[50, 60)范围内）

    f.checkNotImplies(iGe30AndLt70, iGe50); // 验证：i在[30, 70)范围内 不蕴含 i >= 50（i可能等于40），应该返回false
    f.checkNotImplies(iGe30AndLt70, iLt60); // 验证：i在[30, 70)范围内 不蕴含 i < 60（i可能等于65），应该返回false
    f.checkNotImplies(iGe30AndLt70, iGe50AndLt60); // 验证：i在[30, 70)范围内 不蕴含 i在[50, 60)范围内（i可能在[30, 50)或[60, 70)），应该返回false
    f.checkNotImplies(iGe30, iGe50AndLt60); // 验证：i >= 30 不蕴含 i在[50, 60)范围内（i可能等于40），应该返回false
    f.checkNotImplies(iLt70, iGe50AndLt60); // 验证：i < 70 不蕴含 i在[50, 60)范围内（i可能等于40），应该返回false
    f.checkImplies(iGe50AndLt60, iGe30AndLt70); // 验证：i在[50, 60)范围内 蕴含 i在[30, 70)范围内（因为[50, 60)是[30, 70)的子集），应该返回true
    f.checkImplies(iGe50AndLt60, iLt70); // 验证：i在[50, 60)范围内 蕴含 i < 70（因为[50, 60)的所有值都小于70），应该返回true
    f.checkImplies(iGe50AndLt60, iGe30); // 验证：i在[50, 60)范围内 蕴含 i >= 30（因为[50, 60)的所有值都大于等于30），应该返回true
  }

  @Test void testSimpleBetweenCornerCases() { // 测试BETWEEN条件的边界情况，验证复杂范围条件的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final RexNode node1 = f.gt(f.i, f.literal(30)); // 创建表达式：i > 30
    final RexNode node2 = f.gt(f.i, f.literal(50)); // 创建表达式：i > 50
    final RexNode node3 = f.lt(f.i, f.literal(60)); // 创建表达式：i < 60
    final RexNode node4 = f.lt(f.i, f.literal(80)); // 创建表达式：i < 80
    final RexNode node5 = f.lt(f.i, f.literal(90)); // 创建表达式：i < 90
    final RexNode node6 = f.lt(f.i, f.literal(100)); // 创建表达式：i < 100

    f.checkNotImplies(f.and(node1, node2), f.and(node3, node4)); // 验证：i > 30 AND i > 50（即i > 50）不蕴含 i < 60 AND i < 80（即i < 60），因为i > 50的值不一定小于60，应该返回false
    f.checkNotImplies(f.and(node5, node6), f.and(node3, node4)); // 验证：i < 90 AND i < 100（即i < 90）不蕴含 i < 60 AND i < 80（即i < 60），因为i < 90的值不一定小于60，应该返回false
    f.checkNotImplies(f.and(node1, node2), node6); // 验证：i > 30 AND i > 50（即i > 50）不蕴含 i < 100，因为i > 50的值不一定小于100，应该返回false
    f.checkNotImplies(node6, f.and(node1, node2)); // 验证：i < 100 不蕴含 i > 30 AND i > 50（即i > 50），因为i < 100的值不一定大于50，应该返回false
    f.checkImplies(f.and(node3, node4), f.and(node5, node6)); // 验证：i < 60 AND i < 80（即i < 60）蕴含 i < 90 AND i < 100（即i < 90），因为i < 60的值必然小于90，应该返回true
  }

  /** Similar to {@link MaterializedViewSubstitutionVisitorTest#testAlias()}:
   * {@code x > 1 OR (y > 2 AND z > 4)}
   * implies
   * {@code (y > 3 AND z > 5)}. */
  @Test void testOr() { // 测试OR条件的蕴含关系，验证OR表达式的蕴含逻辑（类似于物化视图替换中的别名测试）
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final RexNode xGt1 = f.gt(f.i, f.literal(1)); // 创建表达式：x > 1
    final RexNode yGt2 = f.gt(f.dec, f.literal(2)); // 创建表达式：y > 2
    final RexNode yGt3 = f.gt(f.dec, f.literal(3)); // 创建表达式：y > 3
    final RexNode zGt4 = f.gt(f.lg, f.literal(4)); // 创建表达式：z > 4
    final RexNode zGt5 = f.gt(f.lg, f.literal(5)); // 创建表达式：z > 5
    final RexNode yGt2AndZGt4 = f.and(yGt2, zGt4); // 创建表达式：y > 2 AND z > 4
    final RexNode yGt3AndZGt5 = f.and(yGt3, zGt5); // 创建表达式：y > 3 AND z > 5
    final RexNode or = f.or(xGt1, yGt2AndZGt4); // 创建表达式：x > 1 OR (y > 2 AND z > 4)
    if (false) { // 条件为false，下面的代码不会执行
      f.checkNotImplies(or, yGt3AndZGt5); // 如果启用，验证：x > 1 OR (y > 2 AND z > 4) 不蕴含 y > 3 AND z > 5
    }
    f.checkImplies(yGt3AndZGt5, or); // 验证：y > 3 AND z > 5 蕴含 x > 1 OR (y > 2 AND z > 4)，因为如果y > 3且z > 5，则y > 2且z > 4必然成立，使得OR表达式的右侧为真，应该返回true
  }

  @Test void testNotNull() { // 测试NOT NULL条件的蕴含关系，验证非空判断的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final RexNode node1 = f.eq(f.str, f.rexBuilder.makeLiteral("en")); // 创建表达式：str = 'en'
    final RexNode node2 = f.notNull(f.str); // 创建表达式：str IS NOT NULL
    final RexNode node3 = f.gt(f.str, f.rexBuilder.makeLiteral("abc")); // 创建表达式：str > 'abc'
    f.checkImplies(node1, node2); // 验证：str = 'en' 蕴含 str IS NOT NULL（如果str等于'en'，则str不为NULL），应该返回true
    f.checkNotImplies(node2, node1); // 验证：str IS NOT NULL 不蕴含 str = 'en'（str不为NULL但可能不等于'en'），应该返回false
    f.checkImplies(node3, node2); // 验证：str > 'abc' 蕴含 str IS NOT NULL（如果str大于'abc'，则str不为NULL），应该返回true
    f.checkImplies(node2, node2); // 验证：str IS NOT NULL 蕴含 str IS NOT NULL（自反性），应该返回true
  }

  @Test void testIsNull() { // 测试IS NULL条件的蕴含关系，验证空值判断的蕴含逻辑
    final Fixture f = new Fixture(); // 创建测试夹具对象
    final RexNode sEqEn = f.eq(f.str, f.charLiteral("en")); // 创建表达式：s = 'en'
    final RexNode sIsNotNull = f.notNull(f.str); // 创建表达式：s IS NOT NULL
    final RexNode sIsNull = f.isNull(f.str); // 创建表达式：s IS NULL
    final RexNode iEq5 = f.eq(f.i, f.literal(5)); // 创建表达式：i = 5
    final RexNode iIsNull = f.isNull(f.i); // 创建表达式：i IS NULL
    final RexNode iIsNotNull = f.notNull(f.i); // 创建表达式：i IS NOT NULL
    f.checkNotImplies(sIsNotNull, sIsNull); // 验证：s IS NOT NULL 不蕴含 s IS NULL（s不为NULL时不可能为NULL），应该返回false
    f.checkNotImplies(sIsNull, sIsNotNull); // 验证：s IS NULL 不蕴含 s IS NOT NULL（s为NULL时不可能不为NULL），应该返回false
    f.checkNotImplies(sEqEn, sIsNull); // 验证：s = 'en' 不蕴含 s IS NULL（s等于'en'时不可能为NULL），应该返回false
    f.checkNotImplies(sIsNull, sEqEn); // 验证：s IS NULL 不蕴含 s = 'en'（s为NULL时不可能等于'en'），应该返回false
    f.checkImplies(sEqEn, sIsNotNull); // 验证：s = 'en' 蕴含 s IS NOT NULL（s等于字面量时必然不为NULL），应该返回true
    f.checkImplies(sIsNotNull, sIsNotNull); // 验证：s IS NOT NULL 蕴含 s IS NOT NULL（自反性），应该返回true
    f.checkImplies(sIsNull, sIsNull); // 验证：s IS NULL 蕴含 s IS NULL（自反性），应该返回true

    // "s is not null and y = 5" implies "s is not null" // 注释：s IS NOT NULL AND y = 5 蕴含 s IS NOT NULL
    f.checkImplies(f.and(sIsNotNull, iEq5), sIsNotNull); // 验证：s IS NOT NULL AND i = 5 蕴含 s IS NOT NULL（AND表达式的真值蕴含其每个操作数），应该返回true

    // "y = 5 and s is not null" implies "s is not null" // 注释：y = 5 AND s IS NOT NULL 蕴含 s IS NOT NULL
    f.checkImplies(f.and(iEq5, sIsNotNull), sIsNotNull); // 验证：i = 5 AND s IS NOT NULL 蕴含 s IS NOT NULL（AND表达式的真值蕴含其每个操作数），应该返回true

    // "y is not null" does not imply "s is not null" // 注释：y IS NOT NULL 不蕴含 s IS NOT NULL
    f.checkNotImplies(iIsNull, sIsNotNull); // 验证：i IS NULL 不蕴含 s IS NOT NULL（i和s是不同的字段），应该返回false

    // "s is not null or i = 5" does not imply "s is not null" // 注释：s IS NOT NULL OR i = 5 不蕴含 s IS NOT NULL
    f.checkNotImplies(f.or(sIsNotNull, iEq5), sIsNotNull); // 验证：s IS NOT NULL OR i = 5 不蕴含 s IS NOT NULL（OR表达式的真值不蕴含其每个操作数），应该返回false

    // "s is not null" implies "s is not null or i = 5" // 注释：s IS NOT NULL 蕴含 s IS NOT NULL OR i = 5
    f.checkImplies(sIsNotNull, f.or(sIsNotNull, iEq5)); // 验证：s IS NOT NULL 蕴含 s IS NOT NULL OR i = 5（操作数的真值蕴含OR表达式），应该返回true

    // "s is not null" implies "i = 5 or s is not null" // 注释：s IS NOT NULL 蕴含 i = 5 OR s IS NOT NULL
    f.checkImplies(sIsNotNull, f.or(iEq5, sIsNotNull)); // 验证：s IS NOT NULL 蕴含 i = 5 OR s IS NOT NULL（操作数的真值蕴含OR表达式），应该返回true

    // "i > 10" implies "x is not null" // 注释：i > 10 蕴含 x IS NOT NULL
    f.checkImplies(f.gt(f.i, f.literal(10)), iIsNotNull); // 验证：i > 10 蕴含 i IS NOT NULL（参与比较操作的字段必然不为NULL），应该返回true

    // "-20 > i" implies "x is not null" // 注释：-20 > i 蕴含 x IS NOT NULL
    f.checkImplies(f.gt(f.literal(-20), f.i), iIsNotNull); // 验证：-20 > i 蕴含 i IS NOT NULL（参与比较操作的字段必然不为NULL），应该返回true

    // "s is null and -20 > i" implies "x is not null" // 注释：s IS NULL AND -20 > i 蕴含 x IS NOT NULL
    f.checkImplies(f.and(sIsNull, f.gt(f.literal(-20), f.i)), iIsNotNull); // 验证：s IS NULL AND -20 > i 蕴含 i IS NOT NULL（i参与比较操作必然不为NULL），应该返回true

    // "i > 10" does not imply "x is null" // 注释：i > 10 不蕴含 x IS NULL
    f.checkNotImplies(f.gt(f.i, f.literal(10)), iIsNull); // 验证：i > 10 不蕴含 i IS NULL（参与比较操作的字段不可能为NULL），应该返回false
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2041">[CALCITE-2041]
   * When simplifying a nullable expression, allow the result to change type to
   * NOT NULL</a> and match nullability.
   *
   * @see RexSimplify#simplifyPreservingType(RexNode, RexUnknownAs, boolean) */
  @Test void testSimplifyCastMatchNullability() { // 测试类型转换的简化与可空性匹配，验证表达式简化时如何处理可空性
    // 这个测试用例针对CALCITE-2041问题：当简化可空表达式时，允许结果类型变为NOT NULL并匹配可空性
    // RexSimplify.simplifyPreservingType方法用于在简化表达式时保留类型信息
    // 参数matchNullability控制是否需要匹配原始表达式的可空性
    final Fixture f = new Fixture(); // 创建测试夹具对象

    // The cast is nullable, while the literal is not nullable. When we simplify
    // it, we end up with the literal. If defaultSimplifier is used, a CAST is
    // introduced on top of the expression, as nullability of the new expression
    // does not match the nullability of the original one. If
    // nonMatchingNullabilitySimplifier is used, the CAST is not added and the
    // simplified expression only consists of the literal.
    // 注释说明：CAST是可空的，而字面量是不可空的。简化后得到字面量。如果使用默认简化器（matchNullability=true），
    // 会在表达式上添加CAST，因为新表达式的可空性与原表达式不匹配。如果使用不匹配可空性的简化器（matchNullability=false），
    // 则不添加CAST，简化后的表达式只包含字面量。
    final RexNode e = f.cast(f.intRelDataType, f.literal(2014)); // 创建表达式：CAST(2014 AS INTEGER)，其中f.intRelDataType是可空的整数类型
    assertThat( // 断言：验证简化后的表达式
        f.simplify.simplifyPreservingType(e, RexUnknownAs.UNKNOWN, true), // 调用简化方法，matchNullability=true表示需要匹配可空性
        hasToString("CAST(2014):JavaType(class java.lang.Integer)")); // 期望结果是CAST(2014)，因为需要保留可空性
    assertThat( // 断言：验证简化后的表达式
        f.simplify.simplifyPreservingType(e, RexUnknownAs.UNKNOWN, false), // 调用简化方法，matchNullability=false表示不需要匹配可空性
        hasToString("2014")); // 期望结果是2014（字面量），因为不需要保留可空性，所以移除CAST

    // In this case, the cast is not nullable. Thus, in both cases, the
    // simplified expression only consists of the literal.
    // 注释说明：在这种情况下，CAST是不可空的。因此，在两种情况下，简化后的表达式只包含字面量。
    RelDataType notNullIntRelDataType = f.typeFactory.createJavaType(int.class); // 创建不可空的整数类型
    final RexNode e2 = // 创建嵌套的CAST表达式：CAST(CAST(2014 AS INTEGER) AS INTEGER)
        f.cast(notNullIntRelDataType, // 外层CAST，使用不可空类型
            f.cast(notNullIntRelDataType, f.literal(2014))); // 内层CAST，也使用不可空类型
    assertThat( // 断言：验证简化后的表达式
        f.simplify.simplifyPreservingType(e2, RexUnknownAs.UNKNOWN, true), // matchNullability=true
        hasToString("2014")); // 期望结果是2014，因为原始类型已经是不可空的，不需要添加CAST
    assertThat( // 断言：验证简化后的表达式
        f.simplify.simplifyPreservingType(e2, RexUnknownAs.UNKNOWN, false), // matchNullability=false
        hasToString("2014")); // 期望结果是2014，因为不需要匹配可空性
  }

  /** Test case for simplifier of ceil/floor. */
  @Test void testSimplifyCeilFloor() { // 测试CEIL和FLOOR函数的简化，验证时间戳函数的嵌套简化逻辑
    // CEIL和FLOOR是SQL中的向上取整和向下取整函数，常用于时间戳操作
    // 例如：FLOOR(ts TO MONTH)表示将时间戳向下取整到月份
    // 嵌套的CEIL/FLOOR可以被简化：FLOOR(FLOOR(ts TO MONTH) TO YEAR) 简化为 FLOOR(ts TO YEAR)
    // 这个测试验证了不同时间单位的嵌套简化规则
    // We can add more time units here once they are supported in
    // RexInterpreter, e.g., TimeUnitRange.HOUR, TimeUnitRange.MINUTE,
    // TimeUnitRange.SECOND.
    // 注释说明：一旦RexInterpreter支持更多时间单位，可以在这里添加它们，例如HOUR、MINUTE、SECOND
    final ImmutableList<TimeUnitRange> timeUnitRanges = // 创建时间单位范围列表，目前只支持YEAR和MONTH
        ImmutableList.of(TimeUnitRange.YEAR, TimeUnitRange.MONTH);
    final Fixture f = new Fixture(); // 创建测试夹具对象

    final RexNode literalTs = // 创建时间戳字面量：2010-10-10 00:00:00
        f.timestampLiteral(new TimestampString("2010-10-10 00:00:00"));
    for (int i = 0; i < timeUnitRanges.size(); i++) { // 遍历时间单位范围（外层循环）
      final RexNode innerFloorCall = // 创建内层FLOOR调用：FLOOR(ts TO timeUnitRanges[i])
          f.rexBuilder.makeCall(SqlStdOperatorTable.FLOOR, literalTs,
              f.rexBuilder.makeFlag(timeUnitRanges.get(i)));
      final RexNode innerCeilCall = // 创建内层CEIL调用：CEIL(ts TO timeUnitRanges[i])
          f.rexBuilder.makeCall(SqlStdOperatorTable.CEIL, literalTs,
              f.rexBuilder.makeFlag(timeUnitRanges.get(i)));
      for (int j = 0; j <= i; j++) { // 遍历时间单位范围（内层循环，j <= i表示外层时间单位更粗粒度）
        final RexNode outerFloorCall = // 创建外层FLOOR调用：FLOOR(FLOOR(ts TO timeUnitRanges[i]) TO timeUnitRanges[j])
            f.rexBuilder.makeCall(SqlStdOperatorTable.FLOOR, innerFloorCall,
                f.rexBuilder.makeFlag(timeUnitRanges.get(j)));
        final RexNode outerCeilCall = // 创建外层CEIL调用：CEIL(CEIL(ts TO timeUnitRanges[i]) TO timeUnitRanges[j])
            f.rexBuilder.makeCall(SqlStdOperatorTable.CEIL, innerCeilCall,
                f.rexBuilder.makeFlag(timeUnitRanges.get(j)));
        final RexCall floorSimplifiedExpr = // 简化外层FLOOR表达式
            (RexCall) f.simplify.simplifyPreservingType(outerFloorCall,
                RexUnknownAs.UNKNOWN, true);
        assertThat(floorSimplifiedExpr.getKind(), is(SqlKind.FLOOR)); // 断言：简化后的表达式类型仍然是FLOOR
        assertThat(((RexLiteral) floorSimplifiedExpr.getOperands().get(1)) // 断言：验证时间单位参数
                .getValue(),
            hasToString(timeUnitRanges.get(j).toString())); // 期望时间单位是timeUnitRanges[j]
        assertThat(floorSimplifiedExpr.getOperands().get(0), // 断言：验证操作数
            hasToString(literalTs.toString())); // 期望操作数是原始时间戳字面量（嵌套的FLOOR被简化掉了）
        final RexCall ceilSimplifiedExpr = // 简化外层CEIL表达式
            (RexCall) f.simplify.simplifyPreservingType(outerCeilCall,
                RexUnknownAs.UNKNOWN, true);
        assertThat(ceilSimplifiedExpr.getKind(), is(SqlKind.CEIL)); // 断言：简化后的表达式类型仍然是CEIL
        assertThat(((RexLiteral) ceilSimplifiedExpr.getOperands().get(1)) // 断言：验证时间单位参数
                .getValue(),
            hasToString(timeUnitRanges.get(j).toString())); // 期望时间单位是timeUnitRanges[j]
        assertThat(ceilSimplifiedExpr.getOperands().get(0), // 断言：验证操作数
            hasToString(literalTs.toString())); // 期望操作数是原始时间戳字面量（嵌套的CEIL被简化掉了）
      }
    }

    // Negative test // 负面测试：验证不应该简化的情况
    for (int i = timeUnitRanges.size() - 1; i >= 0; i--) { // 反向遍历时间单位范围（外层循环）
      final RexNode innerFloorCall = // 创建内层FLOOR调用
          f.rexBuilder.makeCall(SqlStdOperatorTable.FLOOR, literalTs,
              f.rexBuilder.makeFlag(timeUnitRanges.get(i)));
      final RexNode innerCeilCall = // 创建内层CEIL调用
          f.rexBuilder.makeCall(SqlStdOperatorTable.CEIL, literalTs,
              f.rexBuilder.makeFlag(timeUnitRanges.get(i)));
      for (int j = timeUnitRanges.size() - 1; j > i; j--) { // 遍历时间单位范围（内层循环，j > i表示外层时间单位更细粒度）
        // 当外层时间单位比内层更细粒度时，嵌套的CEIL/FLOOR不能简化
        // 例如：FLOOR(FLOOR(ts TO YEAR) TO MONTH) 不能简化，因为FLOOR到YEAR后再FLOOR到MONTH没有意义
        final RexNode outerFloorCall = // 创建外层FLOOR调用（外层更细粒度）
            f.rexBuilder.makeCall(SqlStdOperatorTable.FLOOR, innerFloorCall,
                f.rexBuilder.makeFlag(timeUnitRanges.get(j)));
        final RexNode outerCeilCall = // 创建外层CEIL调用（外层更细粒度）
            f.rexBuilder.makeCall(SqlStdOperatorTable.CEIL, innerCeilCall,
                f.rexBuilder.makeFlag(timeUnitRanges.get(j)));
        final RexCall floorSimplifiedExpr = // 简化外层FLOOR表达式
            (RexCall) f.simplify.simplifyPreservingType(outerFloorCall,
                RexUnknownAs.UNKNOWN, true);
        assertThat(floorSimplifiedExpr, hasToString(outerFloorCall.toString())); // 断言：表达式不应该被简化
        final RexCall ceilSimplifiedExpr = // 简化外层CEIL表达式
            (RexCall) f.simplify.simplifyPreservingType(outerCeilCall,
                RexUnknownAs.UNKNOWN, true);
        assertThat(ceilSimplifiedExpr, hasToString(outerCeilCall.toString())); // 断言：表达式不应该被简化
      }
    }
  }

}
