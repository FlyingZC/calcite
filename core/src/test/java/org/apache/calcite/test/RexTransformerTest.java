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
package org.apache.calcite.test; // 声明包名，该测试类位于org.apache.calcite.test包下
import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入Java类型工厂实现类，用于创建Java类型
import org.apache.calcite.plan.RelOptUtil; // 导入关系表达式工具类，提供各种优化和转换方法
import org.apache.calcite.plan.RelOptUtil.Logic; // 导入Logic枚举，表示布尔逻辑类型
import org.apache.calcite.rel.RelNode; // 导入关系表达式接口，代表一个关系代数操作
import org.apache.calcite.rel.logical.LogicalJoin; // 导入逻辑连接操作，表示两个表的JOIN操作
import org.apache.calcite.rel.logical.LogicalProject; // 导入逻辑投影操作，表示投影/选择列
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示一个SQL数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段接口，表示数据类型中的一个字段
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统，提供数据类型的系统规范
import org.apache.calcite.rex.LogicVisitor; // 导入逻辑访问者，用于推导表达式的逻辑属性
import org.apache.calcite.rex.RexBuilder; // 导入Rex构建器，用于构建行表达式(RexNode)
import org.apache.calcite.rex.RexInputRef; // 导入行表达式输入引用，表示对输入字段的引用
import org.apache.calcite.rex.RexLiteral; // 导入行表达式字面量，表示常量值
import org.apache.calcite.rex.RexNode; // 导入行表达式节点接口，代表一个行表达式
import org.apache.calcite.rex.RexTransformer; // 导入行表达式转换器，用于转换行表达式的语义
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入标准SQL操作符表，包含所有标准SQL操作符
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义各种SQL数据类型

import org.junit.jupiter.api.AfterEach; // 导入Jupiter测试框架的AfterEach注解，标记每个测试方法后执行的方法
import org.junit.jupiter.api.BeforeEach; // 导入Jupiter测试框架的BeforeEach注解，标记每个测试方法前执行的方法
import org.junit.jupiter.api.Test; // 导入Jupiter测试框架的Test注解，标记测试方法

import java.math.BigDecimal; // 导入BigDecimal类，用于精确的小数运算
import java.util.ArrayList; // 导入ArrayList动态数组类
import java.util.List; // 导入List集合接口

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言库的is匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言库的assertThat方法
import static org.hamcrest.Matchers.hasSize; // 导入Hamcrest断言库的hasSize匹配器
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest断言库的hasToString匹配器
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入Jupiter断言库的assertFalse方法
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入Jupiter断言库的assertTrue方法
import static org.junit.jupiter.api.Assertions.fail; // 导入Jupiter断言库的fail方法

/**
 * Tests transformations on rex nodes. // RexTransformerTest类：用于测试Rex表达式节点的转换功能
 */ // 该类主要测试RexTransformer如何将Rex表达式转换为包含NULL语义检查的等价表达式
class RexTransformerTest { // 测试类定义，继承自Object
  //~ Instance fields -------------------------------------------------------- // 实例字段区域标记

  RexBuilder rexBuilder = null; // Rex构建器：用于构建各种Rex表达式节点，是创建RexNode的核心工具
  RexNode x; // Rex表达式节点x：代表第一个输入字段的引用，用于测试表达式转换
  RexNode y; // Rex表达式节点y：代表第二个输入字段的引用，用于测试表达式转换
  RexNode z; // Rex表达式节点z：代表第三个输入字段的引用，用于测试表达式转换
  RexNode trueRex; // Rex表达式节点trueRex：表示布尔值true的字面量
  RexNode falseRex; // Rex表达式节点falseRex：表示布尔值false的字面量
  RelDataType boolRelDataType; // 关系数据类型：表示布尔类型的数据类型定义
  RelDataTypeFactory typeFactory; // 关系数据类型工厂：用于创建和管理各种SQL数据类型

  //~ Methods ---------------------------------------------------------------- // 方法区域标记

  /** Converts a SQL string to a relational expression using mock schema. */ // 方法注释：将SQL字符串转换为关系表达式，使用模拟schema
  private static RelNode toRel(String sql) { // 静态方法：将SQL语句转换为关系表达式树
    return SqlToRelFixture.DEFAULT.withSql(sql).toRel(); // 使用默认的SQL到关系转换器工具，将SQL转换为RelNode
  } // 方法结束

  @BeforeEach public void setUp() { // 每个测试方法执行前的初始化方法，使用JUnit的BeforeEach注解
    typeFactory = new JavaTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建Java类型工厂实例，使用默认的类型系统
    rexBuilder = new RexBuilder(typeFactory); // 创建Rex构建器，传入类型工厂用于构建Rex表达式
    boolRelDataType = typeFactory.createSqlType(SqlTypeName.BOOLEAN); // 创建布尔类型的SQL数据类型

    x = // 初始化x变量，创建第一个输入引用
        new RexInputRef(0, // 创建输入引用，索引为0，代表第一个输入字段
            typeFactory.createTypeWithNullability(boolRelDataType, true)); // 创建可空的布尔类型
    y = // 初始化y变量，创建第二个输入引用
        new RexInputRef(1, // 创建输入引用，索引为1，代表第二个输入字段
            typeFactory.createTypeWithNullability(boolRelDataType, true)); // 创建可空的布尔类型
    z = // 初始化z变量，创建第三个输入引用
        new RexInputRef(2, // 创建输入引用，索引为2，代表第三个输入字段
            typeFactory.createTypeWithNullability(boolRelDataType, true)); // 创建可空的布尔类型
    trueRex = rexBuilder.makeLiteral(true); // 创建布尔值true的字面量Rex节点
    falseRex = rexBuilder.makeLiteral(false); // 创建布尔值false的字面量Rex节点
  } // setUp方法结束

  @AfterEach public void testDown() { // 每个测试方法执行后的清理方法，使用JUnit的AfterEach注解
    typeFactory = null; // 清理类型工厂引用，释放资源
    rexBuilder = null; // 清理Rex构建器引用，释放资源
    boolRelDataType = null; // 清理布尔类型引用，释放资源
    x = y = z = trueRex = falseRex = null; // 清理所有Rex表达式节点引用，释放资源
  } // testDown方法结束

  void check( // 检查方法：验证Rex表达式转换后的结果是否符合预期
      Boolean encapsulateType, // 封装类型参数：null表示不封装，TRUE表示用IS_TRUE封装，FALSE表示用IS_FALSE封装
      RexNode node, // 要转换的Rex表达式节点
      String expected) { // 期望的转换结果字符串
    RexNode root; // 声明根节点变量，用于存储可能被封装的表达式
    if (null == encapsulateType) { // 如果封装类型为null
      root = node; // 直接使用原始节点作为根节点
    } else if (encapsulateType.equals(Boolean.TRUE)) { // 如果封装类型为TRUE
      root = isTrue(node); // 用IS_TRUE操作符封装节点
    } else { // 否则封装类型为FALSE
      // encapsulateType.equals(Boolean.FALSE) // 注释说明：封装类型等于FALSE
      root = isFalse(node); // 用IS_FALSE操作符封装节点
    } // if-else结束

    RexTransformer transformer = new RexTransformer(root, rexBuilder); // 创建Rex转换器实例，传入根节点和Rex构建器
    RexNode result = transformer.transformNullSemantics(); // 执行NULL语义转换，将表达式转换为包含NULL检查的等价表达式
    String actual = result.toString(); // 将转换结果转换为字符串
    if (!actual.equals(expected)) { // 如果实际结果与期望结果不符
      String msg = // 构造错误消息
          "\nExpected=<" + expected + ">\n  Actual=<" + actual + ">"; // 格式化期望值和实际值
      fail(msg); // 测试失败，输出错误消息
    } // if结束
  } // check方法结束

  private RexNode lessThan(RexNode a0, RexNode a1) { // 私有方法：创建小于表达式(a0 < a1)
    return rexBuilder.makeCall(SqlStdOperatorTable.LESS_THAN, a0, a1); // 使用Rex构建器创建LESS_THAN操作符调用
  } // lessThan方法结束

  private RexNode lessThanOrEqual(RexNode a0, RexNode a1) { // 私有方法：创建小于等于表达式(a0 <= a1)
    return rexBuilder.makeCall(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, a0, a1); // 使用Rex构建器创建LESS_THAN_OR_EQUAL操作符调用
  } // lessThanOrEqual方法结束

  private RexNode greaterThan(RexNode a0, RexNode a1) { // 私有方法：创建大于表达式(a0 > a1)
    return rexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN, a0, a1); // 使用Rex构建器创建GREATER_THAN操作符调用
  } // greaterThan方法结束

  private RexNode greaterThanOrEqual(RexNode a0, RexNode a1) { // 私有方法：创建大于等于表达式(a0 >= a1)
    return rexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, a0, // 使用Rex构建器创建GREATER_THAN_OR_EQUAL操作符调用
        a1); // 第二个操作数
  } // greaterThanOrEqual方法结束

  private RexNode equals(RexNode a0, RexNode a1) { // 私有方法：创建等于表达式(a0 = a1)
    return rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, a0, a1); // 使用Rex构建器创建EQUALS操作符调用
  } // equals方法结束

  private RexNode notEquals(RexNode a0, RexNode a1) { // 私有方法：创建不等于表达式(a0 <> a1)
    return rexBuilder.makeCall(SqlStdOperatorTable.NOT_EQUALS, a0, a1); // 使用Rex构建器创建NOT_EQUALS操作符调用
  } // notEquals方法结束

  private RexNode and(RexNode a0, RexNode a1) { // 私有方法：创建逻辑与表达式(a0 AND a1)
    return rexBuilder.makeCall(SqlStdOperatorTable.AND, a0, a1); // 使用Rex构建器创建AND操作符调用
  } // and方法结束

  private RexNode or(RexNode a0, RexNode a1) { // 私有方法：创建逻辑或表达式(a0 OR a1)
    return rexBuilder.makeCall(SqlStdOperatorTable.OR, a0, a1); // 使用Rex构建器创建OR操作符调用
  } // or方法结束

  private RexNode not(RexNode a0) { // 私有方法：创建逻辑非表达式(NOT a0)
    return rexBuilder.makeCall(SqlStdOperatorTable.NOT, a0); // 使用Rex构建器创建NOT操作符调用
  } // not方法结束

  private RexNode plus(RexNode a0, RexNode a1) { // 私有方法：创建加法表达式(a0 + a1)
    return rexBuilder.makeCall(SqlStdOperatorTable.PLUS, a0, a1); // 使用Rex构建器创建PLUS操作符调用
  } // plus方法结束

  private RexNode isNotNull(RexNode a0) { // 私有方法：创建IS NOT NULL表达式(a0 IS NOT NULL)
    return rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_NULL, a0); // 使用Rex构建器创建IS_NOT_NULL操作符调用
  } // isNotNull方法结束

  private RexNode isFalse(RexNode node) { // 私有方法：创建IS FALSE表达式(node IS FALSE)
    return rexBuilder.makeCall(SqlStdOperatorTable.IS_FALSE, node); // 使用Rex构建器创建IS_FALSE操作符调用
  } // isFalse方法结束

  private RexNode isTrue(RexNode node) { // 私有方法：创建IS TRUE表达式(node IS TRUE)
    return rexBuilder.makeCall(SqlStdOperatorTable.IS_TRUE, node); // 使用Rex构建器创建IS_TRUE操作符调用
  } // isTrue方法结束

  @Test void testPreTests() { // 测试方法：测试前置条件，验证能否创建可空和不可空的变量
    // can make variable nullable? // 注释：能否创建可空变量？
    RexNode node = // 创建Rex节点
        new RexInputRef( // 创建输入引用
            0, // 索引为0
            typeFactory.createTypeWithNullability( // 创建可空类型
                typeFactory.createSqlType(SqlTypeName.BOOLEAN), // 创建布尔类型
                true)); // 设置为可空
    assertTrue(node.getType().isNullable()); // 断言：验证节点类型是可空的

    // can make variable not nullable? // 注释：能否创建不可空变量？
    node = // 创建Rex节点
        new RexInputRef( // 创建输入引用
            0, // 索引为0
            typeFactory.createTypeWithNullability( // 创建不可空类型
                typeFactory.createSqlType(SqlTypeName.BOOLEAN), // 创建布尔类型
                false)); // 设置为不可空
    assertFalse(node.getType().isNullable()); // 断言：验证节点类型是不可空的
  } // testPreTests方法结束

  @Test void testNonBooleans() { // 测试方法：测试非布尔表达式，验证它们不会被转换
    RexNode node = plus(x, y); // 创建加法表达式(x + y)，这是一个非布尔表达式
    String expected = node.toString(); // 获取期望的字符串表示
    check(Boolean.TRUE, node, expected); // 用IS_TRUE封装后检查，期望结果不变
    check(Boolean.FALSE, node, expected); // 用IS_FALSE封装后检查，期望结果不变
    check(null, node, expected); // 不封装直接检查，期望结果不变
  } // testNonBooleans方法结束

  /**
   * the or operator should pass through unchanged since e.g. x OR y should
   * return true if x=null and y=true if it was transformed into something
   * like (x IS NOT NULL) AND (y IS NOT NULL) AND (x OR y) an incorrect result
   * could be produced
   */ // 方法注释：OR操作符应该保持不变，因为如果x=null且y=true，x OR y应该返回true，如果转换为(x IS NOT NULL) AND (y IS NOT NULL) AND (x OR y)会产生错误结果
  @Test void testOrUnchanged() { // 测试方法：测试OR操作符保持不变
    RexNode node = or(x, y); // 创建OR表达式(x OR y)
    String expected = node.toString(); // 获取期望的字符串表示
    check(Boolean.TRUE, node, expected); // 用IS_TRUE封装后检查，期望结果不变
    check(Boolean.FALSE, node, expected); // 用IS_FALSE封装后检查，期望结果不变
    check(null, node, expected); // 不封装直接检查，期望结果不变
  } // testOrUnchanged方法结束

  @Test void testSimpleAnd() { // 测试方法：测试简单的AND表达式转换
    RexNode node = and(x, y); // 创建AND表达式(x AND y)
    check( // 检查转换结果
        Boolean.FALSE, // 用IS_FALSE封装
        node, // 输入节点
        "AND(AND(IS NOT NULL($0), IS NOT NULL($1)), AND($0, $1))"); // 期望结果：转换为包含NULL检查的AND
  } // testSimpleAnd方法结束

  @Test void testSimpleEquals() { // 测试方法：测试简单的等于表达式转换
    RexNode node = equals(x, y); // 创建等于表达式(x = y)
    check( // 检查转换结果
        Boolean.TRUE, // 用IS_TRUE封装
        node, // 输入节点
        "AND(AND(IS NOT NULL($0), IS NOT NULL($1)), =($0, $1))"); // 期望结果：转换为包含NULL检查的等于
  } // testSimpleEquals方法结束

  @Test void testSimpleNotEquals() { // 测试方法：测试简单的不等于表达式转换
    RexNode node = notEquals(x, y); // 创建不等于表达式(x <> y)
    check( // 检查转换结果
        Boolean.FALSE, // 用IS_FALSE封装
        node, // 输入节点
        "AND(AND(IS NOT NULL($0), IS NOT NULL($1)), <>($0, $1))"); // 期望结果：转换为包含NULL检查的不等于
  } // testSimpleNotEquals方法结束

  @Test void testSimpleGreaterThan() { // 测试方法：测试简单的大于表达式转换
    RexNode node = greaterThan(x, y); // 创建大于表达式(x > y)
    check( // 检查转换结果
        Boolean.TRUE, // 用IS_TRUE封装
        node, // 输入节点
        "AND(AND(IS NOT NULL($0), IS NOT NULL($1)), >($0, $1))"); // 期望结果：转换为包含NULL检查的大于
  } // testSimpleGreaterThan方法结束

  @Test void testSimpleGreaterEquals() { // 测试方法：测试简单的大于等于表达式转换
    RexNode node = greaterThanOrEqual(x, y); // 创建大于等于表达式(x >= y)
    check( // 检查转换结果
        Boolean.FALSE, // 用IS_FALSE封装
        node, // 输入节点
        "AND(AND(IS NOT NULL($0), IS NOT NULL($1)), >=($0, $1))"); // 期望结果：转换为包含NULL检查的大于等于
  } // testSimpleGreaterEquals方法结束

  @Test void testSimpleLessThan() { // 测试方法：测试简单的小于表达式转换
    RexNode node = lessThan(x, y); // 创建小于表达式(x < y)
    check( // 检查转换结果
        Boolean.TRUE, // 用IS_TRUE封装
        node, // 输入节点
        "AND(AND(IS NOT NULL($0), IS NOT NULL($1)), <($0, $1))"); // 期望结果：转换为包含NULL检查的小于
  } // testSimpleLessThan方法结束

  @Test void testSimpleLessEqual() { // 测试方法：测试简单的小于等于表达式转换
    RexNode node = lessThanOrEqual(x, y); // 创建小于等于表达式(x <= y)
    check( // 检查转换结果
        Boolean.FALSE, // 用IS_FALSE封装
        node, // 输入节点
        "AND(AND(IS NOT NULL($0), IS NOT NULL($1)), <=($0, $1))"); // 期望结果：转换为包含NULL检查的小于等于
  } // testSimpleLessEqual方法结束

  @Test void testOptimizeNonNullLiterals() { // 测试方法：测试对非空字面量的优化
    RexNode node = lessThanOrEqual(x, trueRex); // 创建小于等于表达式(x <= true)
    check(Boolean.TRUE, node, "AND(IS NOT NULL($0), <=($0, true))"); // 期望结果：只检查x是否为NULL，因为true是非空的
    node = lessThanOrEqual(trueRex, x); // 创建小于等于表达式(true <= x)
    check(Boolean.FALSE, node, "AND(IS NOT NULL($0), <=(true, $0))"); // 期望结果：只检查x是否为NULL，因为true是非空的
  } // testOptimizeNonNullLiterals方法结束

  @Test void testSimpleIdentifier() { // 测试方法：测试简单的标识符转换
    RexNode node = rexBuilder.makeInputRef(boolRelDataType, 0); // 创建输入引用，类型为布尔类型，索引为0
    check(Boolean.TRUE, node, "=(IS TRUE($0), true)"); // 期望结果：转换为等于表达式，检查$0是否为TRUE
  } // testSimpleIdentifier方法结束

  @Test void testMixed1() { // 测试方法：测试混合表达式1
    // x=true AND y // 注释：表达式为x等于true且y
    RexNode op1 = equals(x, trueRex); // 创建等于表达式(x = true)
    RexNode and = and(op1, y); // 创建AND表达式((x = true) AND y)
    check( // 检查转换结果
        Boolean.FALSE, // 用IS_FALSE封装
        and, // 输入节点
        "AND(IS NOT NULL($1), AND(AND(IS NOT NULL($0), =($0, true)), $1))"); // 期望结果：转换为包含NULL检查的表达式
  } // testMixed1方法结束

  @Test void testMixed2() { // 测试方法：测试混合表达式2
    // x!=true AND y>z // 注释：表达式为x不等于true且y大于z
    RexNode op1 = notEquals(x, trueRex); // 创建不等于表达式(x <> true)
    RexNode op2 = greaterThan(y, z); // 创建大于表达式(y > z)
    RexNode and = and(op1, op2); // 创建AND表达式((x <> true) AND (y > z))
    check( // 检查转换结果
        Boolean.FALSE, // 用IS_FALSE封装
        and, // 输入节点
        "AND(AND(IS NOT NULL($0), <>($0, true)), AND(AND(IS NOT NULL($1), IS NOT NULL($2)), >($1, $2)))"); // 期望结果：转换为包含NULL检查的表达式
  } // testMixed2方法结束

  @Test void testMixed3() { // 测试方法：测试混合表达式3
    // x=y AND false>z // 注释：表达式为x等于y且false大于z
    RexNode op1 = equals(x, y); // 创建等于表达式(x = y)
    RexNode op2 = greaterThan(falseRex, z); // 创建大于表达式(false > z)
    RexNode and = and(op1, op2); // 创建AND表达式((x = y) AND (false > z))
    check( // 检查转换结果
        Boolean.TRUE, // 用IS_TRUE封装
        and, // 输入节点
        "AND(AND(AND(IS NOT NULL($0), IS NOT NULL($1)), =($0, $1)), AND(IS NOT NULL($2), >(false, $2)))"); // 期望结果：转换为包含NULL检查的表达式
  } // testMixed3方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-814">[CALCITE-814]
   * RexBuilder reverses precision and scale of DECIMAL literal</a>
   * and
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1344">[CALCITE-1344]
   * Incorrect inferred precision when BigDecimal value is less than 1</a>. */ // 方法注释：测试CALCITE-814和CALCITE-1344问题，验证DECIMAL字面量的精度和小数位数是否正确
  @Test void testExactLiteral() { // 测试方法：测试精确字面量(DECIMAL类型)
    final RexLiteral literal = // 创建精确字面量
        rexBuilder.makeExactLiteral(new BigDecimal("-1234.56")); // 创建BigDecimal值为-1234.56的字面量
    assertThat(literal.getType().getFullTypeString(), // 断言：验证字面量的类型字符串
        is("DECIMAL(6, 2) NOT NULL")); // 期望：DECIMAL(6, 2) NOT NULL，精度6位，小数2位
    assertThat(literal.getValue(), hasToString("-1234.56")); // 断言：验证字面量的值

    final RexLiteral literal2 = // 创建第二个精确字面量
        rexBuilder.makeExactLiteral(new BigDecimal("1234.56")); // 创建BigDecimal值为1234.56的字面量
    assertThat(literal2.getType().getFullTypeString(), // 断言：验证字面量的类型字符串
        is("DECIMAL(6, 2) NOT NULL")); // 期望：DECIMAL(6, 2) NOT NULL，精度6位，小数2位
    assertThat(literal2.getValue(), hasToString("1234.56")); // 断言：验证字面量的值

    final RexLiteral literal3 = // 创建第三个精确字面量
        rexBuilder.makeExactLiteral(new BigDecimal("0.0123456")); // 创建BigDecimal值为0.0123456的字面量
    assertThat(literal3.getType().getFullTypeString(), // 断言：验证字面量的类型字符串
        is("DECIMAL(8, 7) NOT NULL")); // 期望：DECIMAL(8, 7) NOT NULL，精度8位，小数7位
    assertThat(literal3.getValue(), hasToString("0.0123456")); // 断言：验证字面量的值

    final RexLiteral literal4 = // 创建第四个精确字面量
        rexBuilder.makeExactLiteral(new BigDecimal("0.01234560")); // 创建BigDecimal值为0.01234560的字面量
    assertThat(literal4.getType().getFullTypeString(), // 断言：验证字面量的类型字符串
        is("DECIMAL(9, 8) NOT NULL")); // 期望：DECIMAL(9, 8) NOT NULL，精度9位，小数8位
    assertThat(literal4.getValue(), hasToString("0.01234560")); // 断言：验证字面量的值
  } // testExactLiteral方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-833">[CALCITE-833]
   * RelOptUtil.splitJoinCondition attempts to split a Join-Condition which
   * has a remaining condition</a>. */ // 方法注释：测试CALCITE-833问题，验证splitJoinCondition方法能否正确处理带有剩余条件的连接条件
  @Test void testSplitJoinCondition() { // 测试方法：测试拆分连接条件
    final String sql = "select *\n" // 定义SQL查询字符串
        + "from emp a\n" // 从emp表a选择
        + "INNER JOIN dept b\n" // 与dept表b进行内连接
        + "ON CAST(a.empno AS int) <> b.deptno"; // 连接条件：a.empno转换为int后不等于b.deptno

    final RelNode relNode = toRel(sql); // 将SQL转换为关系表达式树
    final LogicalProject project = (LogicalProject) relNode; // 获取最顶层的LogicalProject节点
    final LogicalJoin join = (LogicalJoin) project.getInput(0); // 获取Project节点的输入，即LogicalJoin节点
    final List<RexNode> leftJoinKeys = new ArrayList<>(); // 创建左连接键列表
    final List<RexNode> rightJoinKeys = new ArrayList<>(); // 创建右连接键列表
    final ArrayList<RelDataTypeField> sysFieldList = new ArrayList<>(); // 创建系统字段列表
    final RexNode remaining = // 调用splitJoinCondition方法拆分连接条件
        RelOptUtil.splitJoinCondition(sysFieldList, join.getInputs().get(0), // 左输入
            join.getInputs().get(1), join.getCondition(), // 右输入和连接条件
            leftJoinKeys, rightJoinKeys, null, null); // 输出参数：左键、右键、过滤器、空指示符

    assertThat(remaining, hasToString("<>($0, $9)")); // 断言：验证剩余条件为<>($0, $9)
    assertThat(leftJoinKeys.isEmpty(), is(true)); // 断言：验证左连接键列表为空
    assertThat(rightJoinKeys.isEmpty(), is(true)); // 断言：验证右连接键列表为空
  } // testSplitJoinCondition方法结束

  /** Test case for {@link org.apache.calcite.rex.LogicVisitor}. */ // 方法注释：测试LogicVisitor逻辑访问者
  @Test void testLogic() { // 测试方法：测试逻辑推导
    // x > FALSE AND ((y = z) IS NOT NULL) // 注释：表达式为x大于FALSE且(y等于z)不为NULL
    final RexNode node = and(greaterThan(x, falseRex), isNotNull(equals(y, z))); // 创建复合表达式
    assertThat(deduceLogic(node, x, Logic.TRUE_FALSE), // 断言：推导x的逻辑属性
        is(Logic.TRUE_FALSE)); // 期望：TRUE_FALSE，表示x必须为TRUE或FALSE
    assertThat(deduceLogic(node, y, Logic.TRUE_FALSE), // 断言：推导y的逻辑属性
        is(Logic.TRUE_FALSE_UNKNOWN)); // 期望：TRUE_FALSE_UNKNOWN，表示y可以为TRUE、FALSE或UNKNOWN
    assertThat(deduceLogic(node, z, Logic.TRUE_FALSE), // 断言：推导z的逻辑属性
        is(Logic.TRUE_FALSE_UNKNOWN)); // 期望：TRUE_FALSE_UNKNOWN，表示z可以为TRUE、FALSE或UNKNOWN

    // TRUE means that a value of FALSE or UNKNOWN will kill the row
    // (therefore we can safely use a semijoin) // 注释：TRUE表示FALSE或UNKNOWN值会过滤掉行（因此可以安全使用半连接）
    assertThat(deduceLogic(and(x, y), x, Logic.TRUE), is(Logic.TRUE)); // 断言：在AND(x, y)中，x的逻辑为TRUE
    assertThat(deduceLogic(and(x, y), y, Logic.TRUE), is(Logic.TRUE)); // 断言：在AND(x, y)中，y的逻辑为TRUE
    assertThat(deduceLogic(and(x, and(y, z)), z, Logic.TRUE), is(Logic.TRUE)); // 断言：在嵌套AND中，z的逻辑为TRUE
    assertThat(deduceLogic(and(x, not(y)), x, Logic.TRUE), is(Logic.TRUE)); // 断言：在AND(x, NOT y)中，x的逻辑为TRUE
    assertThat(deduceLogic(and(x, not(y)), y, Logic.TRUE), // 断言：在AND(x, NOT y)中，y的逻辑
        is(Logic.UNKNOWN_AS_TRUE)); // 期望：UNKNOWN_AS_TRUE，表示UNKNOWN被视为TRUE
    assertThat(deduceLogic(and(x, not(not(y))), y, Logic.TRUE), // 断言：在AND(x, NOT NOT y)中，y的逻辑
        is(Logic.TRUE_FALSE_UNKNOWN)); // 期望：TRUE_FALSE_UNKNOWN，注释说明TRUE_FALSE会更好
    assertThat(deduceLogic(and(x, not(and(y, z))), z, Logic.TRUE), // 断言：在AND(x, NOT AND(y, z))中，z的逻辑
        is(Logic.UNKNOWN_AS_TRUE)); // 期望：UNKNOWN_AS_TRUE，表示UNKNOWN被视为TRUE
    assertThat(deduceLogic(or(x, y), x, Logic.TRUE), // 断言：在OR(x, y)中，x的逻辑
        is(Logic.TRUE_FALSE_UNKNOWN)); // 期望：TRUE_FALSE_UNKNOWN
  } // testLogic方法结束

  private Logic deduceLogic(RexNode root, RexNode seek, Logic logic) { // 私有方法：推导表达式的逻辑属性
    final List<Logic> list = new ArrayList<>(); // 创建逻辑属性列表
    LogicVisitor.collect(root, seek, logic, list); // 使用LogicVisitor收集逻辑属性
    assertThat(list, hasSize(1)); // 断言：验证列表大小为1
    return list.get(0); // 返回列表中的第一个逻辑属性
  } // deduceLogic方法结束
} // RexTransformerTest类结束
