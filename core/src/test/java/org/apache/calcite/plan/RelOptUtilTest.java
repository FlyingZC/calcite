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
package org.apache.calcite.plan;

import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelCollationTraitDef;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelDistribution;
import org.apache.calcite.rel.RelDistributionTraitDef;
import org.apache.calcite.rel.RelDistributions;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.ConverterRule;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.core.RelFactories;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeSystem;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.type.SqlTypeFactoryImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.test.CalciteAssert;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.TestUtil;
import org.apache.calcite.util.Util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.apache.calcite.test.Matchers.isListOf;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link RelOptUtil} and other classes in this package.
 * // RelOptUtil工具类的单元测试类,用于测试RelOptUtil及其相关包中的类的功能
 * // 该测试类主要测试以下功能:
 * // 1. 类型系统相关的测试(类型转储、类型差异比较)
 * // 2. 优化规则相关的测试(规则描述生成、转换规则)
 * // 3. Join条件处理的测试(Join条件分割、Join条件下推)
 * // 4. IS NOT DISTINCT FROM表达式的折叠和展开
 * // 5. 类型转换关系的创建
 * // 使用scott schema作为测试数据源,包含EMP和DEPT两张表
 */
class RelOptUtilTest {
  /** Creates a config based on the "scott" schema. */
  // 创建基于"scott" schema的配置构建器,用于测试环境搭建
  private static Frameworks.ConfigBuilder config() {
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根Schema,启用默认元数据
    return Frameworks.newConfigBuilder()
        .parserConfig(SqlParser.Config.DEFAULT) // 设置SQL解析器配置为默认配置
        .defaultSchema(CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.SCOTT)); // 设置默认Schema为scott schema(包含EMP、DEPT等测试表)
  }

  private RelBuilder relBuilder; // 关系表达式构建器,用于构建关系代数树

  private RelNode empScan; // EMP表的扫描节点,表示对EMP表的访问
  private RelNode deptScan; // DEPT表的扫描节点,表示对DEPT表的访问

  private RelDataType empRow; // EMP表的行类型,包含EMP表的所有字段信息
  private RelDataType deptRow; // DEPT表的行类型,包含DEPT表的所有字段信息

  private List<RelDataTypeField> empDeptJoinRelFields; // EMP和DEPT表Join后的字段列表,包含两个表的所有字段

  @BeforeEach public void setUp() {
    relBuilder = RelBuilder.create(config().build()); // 创建关系表达式构建器实例

    empScan = relBuilder.scan("EMP").build(); // 构建EMP表的扫描节点
    deptScan = relBuilder.scan("DEPT").build(); // 构建DEPT表的扫描节点

    empRow = empScan.getRowType(); // 获取EMP表的行类型
    deptRow = deptScan.getRowType(); // 获取DEPT表的行类型

    empDeptJoinRelFields =
        Lists.newArrayList(Iterables.concat(empRow.getFieldList(), deptRow.getFieldList())); // 将EMP和DEPT表的字段列表合并,用于Join操作
  }

  @Test void testTypeDump() {
    RelDataTypeFactory typeFactory =
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂,使用默认类型系统
    RelDataType t1 =
        typeFactory.builder()
            .add("f0", SqlTypeName.DECIMAL, 5, 2) // 添加DECIMAL类型字段f0,精度5,小数位数2
            .add("f1", SqlTypeName.VARCHAR, 10) // 添加VARCHAR类型字段f1,长度10
            .build(); // 构建类型t1
    TestUtil.assertEqualsVerbose(
        TestUtil.fold(
            "f0 DECIMAL(5, 2) NOT NULL,",
            "f1 VARCHAR(10) NOT NULL"),
        Util.toLinux(RelOptUtil.dumpType(t1) + "\n")); // 验证RelOptUtil.dumpType方法能正确转储类型信息

    RelDataType t2 =
        typeFactory.builder()
            .add("f0", t1) // 添加f0字段,类型为t1(即RECORD类型)
            .add("f1", typeFactory.createMultisetType(t1, -1)) // 添加f1字段,类型为t1的MULTISET类型
            .build(); // 构建嵌套类型t2
    TestUtil.assertEqualsVerbose(
        TestUtil.fold(
            "f0 RECORD (",
            "  f0 DECIMAL(5, 2) NOT NULL,",
            "  f1 VARCHAR(10) NOT NULL) NOT NULL,",
            "f1 RECORD (",
            "  f0 DECIMAL(5, 2) NOT NULL,",
            "  f1 VARCHAR(10) NOT NULL) NOT NULL MULTISET NOT NULL"),
        Util.toLinux(RelOptUtil.dumpType(t2) + "\n")); // 验证嵌套类型的转储
  }

  /**
   * Test {@link RelOptUtil#getFullTypeDifferenceString(String, RelDataType, String, RelDataType)}
   * which returns the detained difference of two types.
   */
  @Test void testTypeDifference() {
    final RelDataTypeFactory typeFactory =
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建类型工厂

    final RelDataType t0 =
        typeFactory.builder()
            .add("f0", SqlTypeName.DECIMAL, 5, 2) // 创建只有一个DECIMAL字段的类型t0
            .build();

    final RelDataType t1 =
        typeFactory.builder()
            .add("f0", SqlTypeName.DECIMAL, 5, 2)
            .add("f1", SqlTypeName.VARCHAR, 10) // 创建有两个字段的类型t1
            .build();

    TestUtil.assertEqualsVerbose(
        TestUtil.fold(
            "Type mismatch: the field sizes are not equal.",
            "source: RecordType(DECIMAL(5, 2) NOT NULL f0) NOT NULL",
            "target: RecordType(DECIMAL(5, 2) NOT NULL f0, VARCHAR(10) NOT NULL f1) NOT NULL"),
        Util.toLinux(RelOptUtil.getFullTypeDifferenceString("source", t0, "target", t1) + "\n")); // 测试字段数量不匹配的情况

    RelDataType t2 =
        typeFactory.builder()
            .add("f0", SqlTypeName.DECIMAL, 5, 2)
            .add("f1", SqlTypeName.VARCHAR, 5) // f1字段长度改为5
            .build();

    TestUtil.assertEqualsVerbose(
        TestUtil.fold(
            "Type mismatch:",
            "source: RecordType(DECIMAL(5, 2) NOT NULL f0, VARCHAR(10) NOT NULL f1) NOT NULL",
            "target: RecordType(DECIMAL(5, 2) NOT NULL f0, VARCHAR(5) NOT NULL f1) NOT NULL",
            "Difference:",
            "f1: VARCHAR(10) NOT NULL -> VARCHAR(5) NOT NULL",
            ""),
        Util.toLinux(RelOptUtil.getFullTypeDifferenceString("source", t1, "target", t2) + "\n")); // 测试字段长度不匹配的情况

    t2 =
        typeFactory.builder()
            .add("f0", SqlTypeName.DECIMAL, 4, 2) // f0精度改为4
            .add("f1", SqlTypeName.BIGINT) // f1类型改为BIGINT
            .build();

    TestUtil.assertEqualsVerbose(
        TestUtil.fold(
            "Type mismatch:",
            "source: RecordType(DECIMAL(5, 2) NOT NULL f0, VARCHAR(10) NOT NULL f1) NOT NULL",
            "target: RecordType(DECIMAL(4, 2) NOT NULL f0, BIGINT NOT NULL f1) NOT NULL",
            "Difference:",
            "f0: DECIMAL(5, 2) NOT NULL -> DECIMAL(4, 2) NOT NULL",
            "f1: VARCHAR(10) NOT NULL -> BIGINT NOT NULL",
            ""),
        Util.toLinux(RelOptUtil.getFullTypeDifferenceString("source", t1, "target", t2) + "\n")); // 测试字段类型不匹配的情况

    t2 =
        typeFactory.builder()
            .add("f0", SqlTypeName.DECIMAL, 5, 2)
            .add("f1", SqlTypeName.VARCHAR, 10)
            .build();
    // Test identical types.
    assertThat(RelOptUtil.getFullTypeDifferenceString("source", t1, "target", t2), equalTo("")); // 测试相同类型应返回空字符串
    assertThat(RelOptUtil.getFullTypeDifferenceString("source", t1, "target", t1), equalTo("")); // 测试自身比较应返回空字符串
  }

  /**
   * Tests the rules for how we name rules.
   */
  @Test void testRuleGuessDescription() {
    assertThat(RelOptRule.guessDescription("com.foo.Bar"), is("Bar")); // 测试从类名中提取规则描述:提取最后一个类名部分
    assertThat(RelOptRule.guessDescription("com.flatten.Bar$Baz"), is("Baz")); // 测试从内部类名中提取规则描述

    // yields "1" (which as an integer is an invalid
    try {
      Util.discard(RelOptRule.guessDescription("com.foo.Bar$1")); // 测试匿名内部类的情况
      fail("expected exception");
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Derived description of rule class com.foo.Bar$1 is an "
              + "integer, not valid. Supply a description manually.")); // 验证抛出异常,提示需要手动提供描述
    }
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3136">[CALCITE-3136]
   * Fix the default rule description of ConverterRule</a>. */
  @Test void testConvertRuleDefaultRuleDescription() {
    final RelCollation collation1 =
        RelCollations.of(new RelFieldCollation(4, RelFieldCollation.Direction.DESCENDING)); // 创建排序规则:第4字段降序
    final RelCollation collation2 =
        RelCollations.of(new RelFieldCollation(0, RelFieldCollation.Direction.DESCENDING)); // 创建排序规则:第0字段降序
    final RelDistribution distribution1 = RelDistributions.hash(ImmutableList.of(0, 1)); // 创建分布规则:按第0和第1字段哈希分布
    final RelDistribution distribution2 = RelDistributions.range(ImmutableList.of()); // 创建分布规则:范围分布
    final RelOptRule collationConvertRule =
        MyConverterRule.create(collation1, collation2); // 创建排序转换规则
    final RelOptRule distributionConvertRule =
        MyConverterRule.create(distribution1, distribution2); // 创建分布转换规则
    final RelOptRule compositeConvertRule =
        MyConverterRule.create(
            RelCompositeTrait.of(RelCollationTraitDef.INSTANCE,
                ImmutableList.of(collation2, collation1)), // 创建复合排序特征
            RelCompositeTrait.of(RelCollationTraitDef.INSTANCE,
                ImmutableList.of(collation1))); // 创建复合排序转换规则
    final RelOptRule compositeConvertRule0 =
        MyConverterRule.create(
            RelCompositeTrait.of(RelDistributionTraitDef.INSTANCE,
                ImmutableList.of(distribution1, distribution2)), // 创建复合分布特征
            RelCompositeTrait.of(RelDistributionTraitDef.INSTANCE,
                ImmutableList.of(distribution1))); // 创建复合分布转换规则
    assertThat(collationConvertRule,
        hasToString("ConverterRule(in:[4 DESC],out:[0 DESC])")); // 验证排序转换规则的描述
    assertThat(distributionConvertRule,
        hasToString("ConverterRule(in:hash[0, 1],out:range)")); // 验证分布转换规则的描述
    assertThat(compositeConvertRule,
        hasToString("ConverterRule(in:[[0 DESC], [4 DESC]],out:[4 DESC])")); // 验证复合排序转换规则的描述
    assertThat(compositeConvertRule0,
        hasToString("ConverterRule(in:[hash[0, 1], range],out:hash[0, 1])")); // 验证复合分布转换规则的描述
    try {
      Util.discard(
          MyConverterRule.create(
              new Convention.Impl("{sourceConvention}", RelNode.class), // 创建包含特殊字符的约定
              new Convention.Impl("<targetConvention>", RelNode.class)));
      fail("expected exception");
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Rule description 'ConverterRule(in:{sourceConvention},"
              + "out:<targetConvention>)' is not valid")); // 验证包含特殊字符的规则描述会抛出异常
    }
  }

  /**
   * Test {@link RelOptUtil#splitJoinCondition(RelNode, RelNode, RexNode, List, List, List)}
   * where the join condition contains just one which is a EQUAL operator.
   */
  @Test void testSplitJoinConditionEquals() {
    int leftJoinIndex = empScan.getRowType().getFieldNames().indexOf("DEPTNO"); // 获取EMP表中DEPTNO字段的索引
    int rightJoinIndex = deptRow.getFieldNames().indexOf("DEPTNO"); // 获取DEPT表中DEPTNO字段的索引

    RexNode joinCond =
        relBuilder.equals(RexInputRef.of(leftJoinIndex, empDeptJoinRelFields), // 创建Join条件: EMP.DEPTNO = DEPT.DEPTNO
            RexInputRef.of(empRow.getFieldCount() + rightJoinIndex,
                empDeptJoinRelFields)); // 右侧字段索引需要加上EMP表的字段数

    splitJoinConditionHelper(
        joinCond, // Join条件表达式
        Collections.singletonList(leftJoinIndex), // 期望的左侧Join键索引列表
        Collections.singletonList(rightJoinIndex), // 期望的右侧Join键索引列表
        Collections.singletonList(true), // 期望的过滤空值标志列表(true表示过滤空值)
        relBuilder.literal(true)); // 期望的剩余条件(应为true)
  }

  @Test void testSplitJoinConditionWithoutEqualCondition() {
    final List<RelDataTypeField> sysFieldList = Collections.emptyList(); // 系统字段列表为空
    final List<List<RexNode>> joinKeys =
        Arrays.asList(new ArrayList<>(), new ArrayList<>()); // Join键列表初始化为空
    final RexNode joinCondition =
        relBuilder.equals(RexInputRef.of(0, empDeptJoinRelFields), // 创建Join条件: 字段0 = 1 (常量比较,不是等值Join)
            relBuilder.literal(1));
    final RexNode result =
        RelOptUtil.splitJoinCondition(sysFieldList,
            Arrays.asList(empScan, deptScan), joinCondition, joinKeys, null, // 分割Join条件
            null);
    assertThat(joinKeys,
        isListOf(Collections.emptyList(), Collections.emptyList())); // 验证Join键列表为空(因为没有等值条件)
    assertThat(result, is(joinCondition)); // 验证剩余条件就是原始Join条件
  }

  /**
   * Test {@link RelOptUtil#splitJoinCondition(RelNode, RelNode, RexNode, List, List, List)}
   * where the join condition contains just one which is a IS NOT DISTINCT operator.
   */
  @Test void testSplitJoinConditionIsNotDistinctFrom() {
    int leftJoinIndex = empScan.getRowType().getFieldNames().indexOf("DEPTNO"); // 获取EMP表中DEPTNO字段的索引
    int rightJoinIndex = deptRow.getFieldNames().indexOf("DEPTNO"); // 获取DEPT表中DEPTNO字段的索引

    RexNode joinCond =
        relBuilder.isNotDistinctFrom(
            RexInputRef.of(leftJoinIndex, empDeptJoinRelFields), // 创建Join条件: EMP.DEPTNO IS NOT DISTINCT FROM DEPT.DEPTNO
            RexInputRef.of(empRow.getFieldCount() + rightJoinIndex,
                empDeptJoinRelFields)); // IS NOT DISTINCT FROM操作符会匹配相等的值和相等的NULL

    splitJoinConditionHelper(
        joinCond,
        Collections.singletonList(leftJoinIndex),
        Collections.singletonList(rightJoinIndex),
        Collections.singletonList(false), // false表示不过滤空值(IS NOT DISTINCT FROM会匹配NULL)
        relBuilder.literal(true));
  }

  /**
   * Tests {@link RelOptUtil#splitJoinCondition(RelNode, RelNode, RexNode, List, List, List)}
   * where the join condition contains an expanded version of IS NOT DISTINCT.
   */
  @Test void testSplitJoinConditionExpandedIsNotDistinctFrom() {
    int leftJoinIndex = empScan.getRowType().getFieldNames().indexOf("DEPTNO"); // 获取EMP表中DEPTNO字段的索引
    int rightJoinIndex = deptRow.getFieldNames().indexOf("DEPTNO"); // 获取DEPT表中DEPTNO字段的索引

    RexInputRef leftKeyInputRef = RexInputRef.of(leftJoinIndex, empDeptJoinRelFields); // 创建左侧字段引用
    RexInputRef rightKeyInputRef =
        RexInputRef.of(empRow.getFieldCount() + rightJoinIndex, empDeptJoinRelFields); // 创建右侧字段引用
    RexNode joinCond =
        relBuilder.or(relBuilder.equals(leftKeyInputRef, rightKeyInputRef), // 创建展开的IS NOT DISTINCT FROM条件: (a = b) OR (a IS NULL AND b IS NULL)
            relBuilder.call(SqlStdOperatorTable.AND,
                relBuilder.isNull(leftKeyInputRef),
                relBuilder.isNull(rightKeyInputRef))); // 这是IS NOT DISTINCT FROM的展开形式

    splitJoinConditionHelper(
        joinCond,
        Collections.singletonList(leftJoinIndex),
        Collections.singletonList(rightJoinIndex),
        Collections.singletonList(false), // false表示不过滤空值
        relBuilder.literal(true));
  }

  /**
   * Tests {@link RelOptUtil#splitJoinCondition(RelNode, RelNode, RexNode, List, List, List)}
   * where the join condition contains an expanded version of IS NOT DISTINCT
   * using CASE.
   */
  @Test void testSplitJoinConditionExpandedIsNotDistinctFromUsingCase() {
    int leftJoinIndex = empScan.getRowType().getFieldNames().indexOf("DEPTNO"); // 获取EMP表中DEPTNO字段的索引
    int rightJoinIndex = deptRow.getFieldNames().indexOf("DEPTNO"); // 获取DEPT表中DEPTNO字段的索引

    RexInputRef leftKeyInputRef = RexInputRef.of(leftJoinIndex, empDeptJoinRelFields); // 创建左侧字段引用
    RexInputRef rightKeyInputRef =
        RexInputRef.of(empRow.getFieldCount() + rightJoinIndex, empDeptJoinRelFields); // 创建右侧字段引用
    RexNode joinCond =
        RelOptUtil.isDistinctFrom(relBuilder.getRexBuilder(),
            leftKeyInputRef, rightKeyInputRef, true); // 使用CASE表达式创建IS NOT DISTINCT FROM的展开形式

    splitJoinConditionHelper(joinCond,
        Collections.singletonList(leftJoinIndex),
        Collections.singletonList(rightJoinIndex),
        Collections.singletonList(false), // false表示不过滤空值
        relBuilder.literal(true));
  }

  /**
   * Tests {@link RelOptUtil#splitJoinCondition(RelNode, RelNode, RexNode, List, List, List)}
   * where the join condition contains an expanded version of IS NOT DISTINCT
   * using CASE.
   */
  @Test void testSplitJoinConditionExpandedIsNotDistinctFromUsingCase2() {
    int leftJoinIndex = empScan.getRowType().getFieldNames().indexOf("DEPTNO"); // 获取EMP表中DEPTNO字段的索引
    int rightJoinIndex = deptRow.getFieldNames().indexOf("DEPTNO"); // 获取DEPT表中DEPTNO字段的索引

    RexInputRef leftKeyInputRef = RexInputRef.of(leftJoinIndex, empDeptJoinRelFields); // 创建左侧字段引用
    RexInputRef rightKeyInputRef =
        RexInputRef.of(empRow.getFieldCount() + rightJoinIndex, empDeptJoinRelFields); // 创建右侧字段引用
    RexNode joinCond =
        relBuilder.call(SqlStdOperatorTable.CASE, // 使用CASE表达式创建IS NOT DISTINCT FROM的展开形式
            relBuilder.isNull(leftKeyInputRef), // WHEN left IS NULL THEN
            relBuilder.isNull(rightKeyInputRef), // right IS NULL
            relBuilder.isNull(rightKeyInputRef), // WHEN right IS NULL THEN
            relBuilder.isNull(leftKeyInputRef), // left IS NULL
            relBuilder.equals(leftKeyInputRef, rightKeyInputRef)); // ELSE left = right

    splitJoinConditionHelper(
        joinCond,
        Collections.singletonList(leftJoinIndex),
        Collections.singletonList(rightJoinIndex),
        Collections.singletonList(false), // false表示不过滤空值
        relBuilder.literal(true));
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-7013">[CALCITE-7013]
   * Support building RexLiterals from Character values</a>. */
  @Test void testCharacterLiteral() {
    char c = 'c'; // 定义字符常量
    relBuilder.literal(c); // 测试从字符值创建RexLiteral,验证CALCITE-7013问题修复
  }

  private void splitJoinConditionHelper(RexNode joinCond, List<Integer> expLeftKeys,
      List<Integer> expRightKeys, List<Boolean> expFilterNulls, RexNode expRemaining) {
    List<Integer> actLeftKeys = new ArrayList<>(); // 实际的左侧Join键索引列表
    List<Integer> actRightKeys = new ArrayList<>(); // 实际的右侧Join键索引列表
    List<Boolean> actFilterNulls = new ArrayList<>(); // 实际的过滤空值标志列表

    RexNode actRemaining =
        RelOptUtil.splitJoinCondition(empScan, deptScan, joinCond, actLeftKeys, // 调用splitJoinCondition分割Join条件
            actRightKeys, actFilterNulls);

    assertThat(actRemaining, is(expRemaining)); // 验证剩余条件是否符合期望
    assertThat(actFilterNulls, is(expFilterNulls)); // 验证过滤空值标志是否符合期望
    assertThat(actLeftKeys, is(expLeftKeys)); // 验证左侧Join键是否符合期望
    assertThat(actRightKeys, is(expRightKeys)); // 验证右侧Join键是否符合期望
  }

  /**
   * Test that {@link RelOptUtil#collapseExpandedIsNotDistinctFromExpr(RexCall, RexBuilder)}
   * collapses an expanded version of IS NOT DISTINCT using OR.
   */
  @Test void testCollapseExpandedIsNotDistinctFromUsingOr() {
    final RexBuilder rexBuilder = relBuilder.getRexBuilder(); // 获取Rex表达式构建器

    final RexNode leftEmpNo =
        RexInputRef.of(empScan.getRowType().getFieldNames().indexOf("EMPNO"), // 创建左侧EMPNO字段引用
            empDeptJoinRelFields);
    final RexNode rightEmpNo =
        RexInputRef.of(empRow.getFieldCount() + deptRow.getFieldNames().indexOf("EMPNO"), // 创建右侧EMPNO字段引用
            empDeptJoinRelFields);

    // OR(AND(IS NULL($0), IS NULL($7)), IS TRUE(=($0, $7)))
    RexNode expanded = relBuilder.isNotDistinctFrom(leftEmpNo, rightEmpNo); // 创建展开的IS NOT DISTINCT FROM表达式(使用OR形式)

    // IS NOT DISTINCT FROM($0, $7)
    RexNode collapsed =
        RelOptUtil.collapseExpandedIsNotDistinctFromExpr((RexCall) expanded, rexBuilder); // 折叠展开的IS NOT DISTINCT FROM表达式

    RexNode expected =
        rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT DISTINCT_FROM, leftEmpNo, rightEmpNo); // 期望的结果:简洁的IS NOT DISTINCT FROM表达式

    assertThat(collapsed, is(expected)); // 验证折叠结果符合期望
  }

  /**
   * Test that {@link RelOptUtil#collapseExpandedIsNotDistinctFromExpr(RexCall, RexBuilder)}
   * collapses an expanded version of IS NOT DISTINCT using CASE.
   */
  @Test void testCollapseExpandedIsNotDistinctFromUsingCase() {
    final RexBuilder rexBuilder = relBuilder.getRexBuilder(); // 获取Rex表达式构建器

    final RexNode leftEmpNo =
        RexInputRef.of(empScan.getRowType().getFieldNames().indexOf("EMPNO"), // 创建左侧EMPNO字段引用
            empDeptJoinRelFields);
    final RexNode rightEmpNo =
        RexInputRef.of(empRow.getFieldCount() + deptRow.getFieldNames().indexOf("EMPNO"), // 创建右侧EMPNO字段引用
            empDeptJoinRelFields);

    // CASE(IS NULL($0), IS NULL($7), IS NULL($7), IS NULL($0), =($0, $7))
    RexNode expanded =
        relBuilder.call(SqlStdOperatorTable.CASE, relBuilder.isNull(leftEmpNo), // 创建展开的IS NOT DISTINCT FROM表达式(使用CASE形式)
            relBuilder.isNull(rightEmpNo),
            relBuilder.isNull(rightEmpNo),
            relBuilder.isNull(leftEmpNo),
            relBuilder.equals(leftEmpNo, rightEmpNo));

    // IS NOT DISTINCT FROM($0, $7)
    RexNode collapsed =
        RelOptUtil.collapseExpandedIsNotDistinctFromExpr((RexCall) expanded, rexBuilder); // 折叠展开的IS NOT DISTINCT FROM表达式

    RexNode expected =
        rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, leftEmpNo, rightEmpNo); // 期望的结果:简洁的IS NOT DISTINCT FROM表达式

    assertThat(collapsed, is(expected)); // 验证折叠结果符合期望
  }

  /**
   * Test that {@link RelOptUtil#collapseExpandedIsNotDistinctFromExpr(RexCall, RexBuilder)}
   * collapses an expression with expanded versions of IS NOT DISTINCT using OR and CASE.
   */
  @Test void testCollapseExpandedIsNotDistinctFromUsingOrAndCase() {
    final RexBuilder rexBuilder = relBuilder.getRexBuilder(); // 获取Rex表达式构建器

    final RexNode leftEmpNo =
        RexInputRef.of(empScan.getRowType().getFieldNames().indexOf("EMPNO"), // 创建左侧EMPNO字段引用
            empDeptJoinRelFields);
    final RexNode rightEmpNo =
        RexInputRef.of(empRow.getFieldCount() + deptRow.getFieldNames().indexOf("EMPNO"), // 创建右侧EMPNO字段引用
            empDeptJoinRelFields);

    final RexNode leftDeptNo =
        RexInputRef.of(empScan.getRowType().getFieldNames().indexOf("DEPTNO"), // 创建左侧DEPTNO字段引用
            empDeptJoinRelFields);
    final RexNode rightDeptNo =
        RexInputRef.of(empRow.getFieldCount() + deptRow.getFieldNames().indexOf("DEPTNO"), // 创建右侧DEPTNO字段引用
            empDeptJoinRelFields);

    // An IS NOT DISTINCT FROM expanded in "CASE" shape
    // CASE(IS NULL($0), IS NULL($7), IS NULL($7), IS NULL($0), =($0, $7))
    RexNode expandedCase =
        relBuilder.call(SqlStdOperatorTable.CASE, relBuilder.isNull(leftEmpNo), // 创建使用CASE形式的IS NOT DISTINCT FROM展开表达式
            relBuilder.isNull(rightEmpNo),
            relBuilder.isNull(rightEmpNo),
            relBuilder.isNull(leftEmpNo),
            relBuilder.equals(leftEmpNo, rightEmpNo));

    // An IS NOT DISTINCT FROM expanded in "OR" shape
    // OR(AND(IS NULL($7), IS NULL($8)), =($7, $8))
    RexNode expandedOr =
        relBuilder.call(
            SqlStdOperatorTable.OR, relBuilder.call(SqlStdOperatorTable.AND, // 创建使用OR形式的IS NOT DISTINCT FROM展开表达式
                relBuilder.isNull(leftDeptNo),
                relBuilder.isNull(rightDeptNo)),
            relBuilder.call(SqlStdOperatorTable.EQUALS, leftDeptNo, rightDeptNo));

    // AND(
    //  OR(AND(IS NULL($7), IS NULL($8)), =($7, $8)),
    //  CASE(IS NULL($0), IS NULL($7), IS NULL($7), IS NULL($0), =($0, $7))
    // )
    RexNode expanded = relBuilder.and(expandedOr, expandedCase); // 将两个展开的IS NOT DISTINCT FROM表达式用AND连接

    // AND(IS NOT DISTINCT FROM($7, $8), IS NOT DISTINCT FROM($0, $7))
    RexNode collapsed =
        RelOptUtil.collapseExpandedIsNotDistinctFromExpr((RexCall) expanded, rexBuilder); // 折叠展开的IS NOT DISTINCT FROM表达式

    RexNode expected =
        rexBuilder.makeCall(
            // Expected is nullable because `expandedCase` is nullable
            relBuilder.getTypeFactory().createTypeWithNullability(expanded.getType(), true), // 期望的类型包含nullability
            SqlStdOperatorTable.AND,
            ImmutableList.of(
                rexBuilder.makeCall(
                    SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
                    leftEmpNo,
                    rightEmpNo),
                rexBuilder.makeCall(
                    SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
                    leftDeptNo,
                    rightDeptNo))); // 期望的结果:两个简洁的IS NOT DISTINCT FROM表达式用AND连接

    assertThat(collapsed, is(expected)); // 验证折叠结果符合期望
  }

  /**
   * Test that {@link RelOptUtil#collapseExpandedIsNotDistinctFromExpr(RexCall, RexBuilder)}
   * recursively collapses expanded versions of IS NOT DISTINCT.
   */
  @Test void testCollapseExpandedIsNotDistinctFromRecursively() {
    final RexBuilder rexBuilder = relBuilder.getRexBuilder(); // 获取Rex表达式构建器

    final RexNode leftEmpNo =
        RexInputRef.of(empScan.getRowType().getFieldNames().indexOf("EMPNO"), // 创建左侧EMPNO字段引用
            empDeptJoinRelFields);
    final RexNode rightEmpNo =
        RexInputRef.of(empRow.getFieldCount() + deptRow.getFieldNames().indexOf("EMPNO"), // 创建右侧EMPNO字段引用
            empDeptJoinRelFields);

    // OR(
    //  AND(
    //   IS NULL(OR(AND(IS NULL($0), IS NULL($7)), IS TRUE(=($0, $7)))),
    //   IS NULL(OR(AND(IS NULL($0), IS NULL($7)), IS TRUE(=($0, $7))))),
    //   IS TRUE(=(
    //    OR(AND(IS NULL($0), IS NULL($7)), IS TRUE(=($0, $7))),
    //    OR(AND(IS NULL($0), IS NULL($7)), IS TRUE(=($0, $7)))
    //   )
    //  )
    // )
    RexNode expanded =
        relBuilder.isNotDistinctFrom(
            relBuilder.isNotDistinctFrom(leftEmpNo, rightEmpNo), // 创建嵌套的IS NOT DISTINCT FROM表达式
            relBuilder.isNotDistinctFrom(leftEmpNo, rightEmpNo));

    // IS NOT DISTINCT FROM(IS NOT DISTINCT FROM($0, $7), IS NOT DISTINCT FROM($0, $7))
    RexNode collapsed =
        RelOptUtil.collapseExpandedIsNotDistinctFromExpr((RexCall) expanded, rexBuilder); // 递归折叠展开的IS NOT DISTINCT FROM表达式

    RexNode expected =
        rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
            rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, // 期望的结果:嵌套的简洁IS NOT DISTINCT FROM表达式
                leftEmpNo,
                rightEmpNo),

            rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
                leftEmpNo,
                rightEmpNo));

    assertThat(collapsed, is(expected)); // 验证折叠结果符合期望
  }

  /**
   * Test that {@link RelOptUtil#collapseExpandedIsNotDistinctFromExpr(RexCall, RexBuilder)}
   * will collapse IS NOT DISTINCT FROM nested within RexNodes.
   */
  @Test void testCollapseExpandedIsNotDistinctFromInsideRexNode() {
    final RexBuilder rexBuilder = relBuilder.getRexBuilder(); // 获取Rex表达式构建器

    final RexNode leftEmpNo =
        RexInputRef.of(empScan.getRowType().getFieldNames().indexOf("EMPNO"), // 创建左侧EMPNO字段引用
            empDeptJoinRelFields);
    final RexNode rightEmpNo =
        RexInputRef.of(empRow.getFieldCount() + deptRow.getFieldNames().indexOf("EMPNO"), // 创建右侧EMPNO字段引用
            empDeptJoinRelFields);

    RexNode expandedIsNotDistinctFrom = relBuilder.isNotDistinctFrom(leftEmpNo, rightEmpNo); // 创建IS NOT DISTINCT FROM表达式
    // NULLIF(
    //  NOT(OR(AND(IS NULL($0), IS NULL($7)), IS TRUE(=($0, $7)))),
    //  IS NOT NULL(OR(AND(IS NULL($0), IS NULL($7)), IS TRUE(=($0, $7))))
    // )
    RexNode expanded =
        relBuilder.call(SqlStdOperatorTable.NULLIF, // 创建包含IS NOT DISTINCT FROM的复杂表达式
            relBuilder.not(expandedIsNotDistinctFrom),
            relBuilder.isNotNull(expandedIsNotDistinctFrom));

    // NULLIF(NOT(IS NOT DISTINCT FROM($0, $7)), IS NOT NULL(IS NOT DISTINCT FROM($0, $7)))
    RexNode collapsed =
        RelOptUtil.collapseExpandedIsNotDistinctFromExpr((RexCall) expanded, rexBuilder); // 折叠表达式中的IS NOT DISTINCT FROM

    RexNode collapsedIsNotDistinctFrom =
        rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, leftEmpNo, rightEmpNo); // 创建简洁的IS NOT DISTINCT FROM表达式
    RexNode expected =
        rexBuilder.makeCall(
            SqlStdOperatorTable.NULLIF,
            relBuilder.not(collapsedIsNotDistinctFrom),
            relBuilder.isNotNull(collapsedIsNotDistinctFrom)); // 期望的结果:包含简洁IS NOT DISTINCT FROM的NULLIF表达式

    assertThat(collapsed, is(expected)); // 验证折叠结果符合期望
  }

  /**
   * Test that {@link RelOptUtil#collapseExpandedIsNotDistinctFromExpr(RexCall, RexBuilder)}
   * can handle collapsing IS NOT DISTINCT FROM composed of other RexNodes.
   */
  @Test void testCollapseExpandedIsNotDistinctFromOnContainingRexNodes() {
    final RexBuilder rexBuilder = relBuilder.getRexBuilder(); // 获取Rex表达式构建器

    final RexNode leftEmpNo =
        RexInputRef.of(empScan.getRowType().getFieldNames().indexOf("EMPNO"), // 创建左侧EMPNO字段引用
            empDeptJoinRelFields);
    final RexNode rightEmpNo =
        RexInputRef.of(empRow.getFieldCount() + deptRow.getFieldNames().indexOf("EMPNO"), // 创建右侧EMPNO字段引用
            empDeptJoinRelFields);

    final RexNode leftDeptNo =
        RexInputRef.of(empScan.getRowType().getFieldNames().indexOf("DEPTNO"), // 创建左侧DEPTNO字段引用
            empDeptJoinRelFields);
    final RexNode rightDeptNo =
        RexInputRef.of(empRow.getFieldCount() + deptRow.getFieldNames().indexOf("DEPTNO"), // 创建右侧DEPTNO字段引用
            empDeptJoinRelFields);

    // OR(
    //  AND(IS NULL(NULLIF($0, $7)), IS NULL(NULLIF($7, $8))),
    //  IS TRUE(=(NULLIF($0, $7), NULLIF($7, $8)))
    // )
    RexNode expanded =
        relBuilder.isNotDistinctFrom(
            relBuilder.call(SqlStdOperatorTable.NULLIF, leftEmpNo, rightEmpNo), // 创建包含NULLIF的IS NOT DISTINCT FROM表达式
            relBuilder.call(SqlStdOperatorTable.NULLIF, leftDeptNo, rightDeptNo));

    // IS NOT DISTINCT FROM(NULLIF($0, $7), NULLIF($7, $8))
    RexNode collapsed =
        RelOptUtil.collapseExpandedIsNotDistinctFromExpr((RexCall) expanded, rexBuilder); // 折叠展开的IS NOT DISTINCT FROM表达式

    RexNode expected =
        rexBuilder.makeCall(
            SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
            rexBuilder.makeCall(SqlStdOperatorTable.NULLIF, leftEmpNo, rightEmpNo), // 期望的结果:包含NULLIF的简洁IS NOT DISTINCT FROM表达式
            rexBuilder.makeCall(SqlStdOperatorTable.NULLIF, leftDeptNo, rightDeptNo));

    assertThat(collapsed, is(expected)); // 验证折叠结果符合期望
  }

  /**
   * Tests {@link RelOptUtil#pushDownJoinConditions(org.apache.calcite.rel.core.Join, RelBuilder)}
   * where the join condition contains a complex expression.
   */
  @Test void testPushDownJoinConditions() {
    int leftJoinIndex = empScan.getRowType().getFieldNames().indexOf("DEPTNO"); // 获取EMP表中DEPTNO字段的索引
    int rightJoinIndex = deptRow.getFieldNames().indexOf("DEPTNO"); // 获取DEPT表中DEPTNO字段的索引

    RexInputRef leftKeyInputRef = RexInputRef.of(leftJoinIndex, empDeptJoinRelFields); // 创建左侧字段引用
    RexInputRef rightKeyInputRef =
        RexInputRef.of(empRow.getFieldCount() + rightJoinIndex, empDeptJoinRelFields); // 创建右侧字段引用
    RexNode joinCond =
        relBuilder.equals(
            relBuilder.call(SqlStdOperatorTable.PLUS, leftKeyInputRef, // 创建Join条件: EMP.DEPTNO + 1 = DEPT.DEPTNO
                relBuilder.literal(1)),
            rightKeyInputRef);

    // Build the join operator and push down join conditions
    relBuilder.push(empScan); // 压入EMP扫描节点
    relBuilder.push(deptScan); // 压入DEPT扫描节点
    relBuilder.join(JoinRelType.INNER, joinCond); // 创建内连接
    Join join = (Join) relBuilder.build(); // 构建Join节点
    RelNode transformed = RelOptUtil.pushDownJoinConditions(join, relBuilder); // 下推Join条件

    // Assert the new join operator
    assertThat(transformed.getRowType(), is(join.getRowType())); // 验证行类型不变
    assertThat(transformed, is(instanceOf(Project.class))); // 验证结果为Project节点
    RelNode transformedInput = transformed.getInput(0); // 获取Project的输入
    assertThat(transformedInput, is(instanceOf(Join.class))); // 验证输入为Join节点
    Join newJoin = (Join) transformedInput;
    assertThat(newJoin.getCondition(),
        hasToString(
            relBuilder.call(
                SqlStdOperatorTable.EQUALS,
                // Computed field is added at the end (and index start at 0)
                RexInputRef.of(empRow.getFieldCount(), join.getRowType()), // 计算字段添加在末尾
                // Right side is shifted by 1
                RexInputRef.of(empRow.getFieldCount() + 1 + rightJoinIndex, join.getRowType())) // 右侧字段索引偏移1
            .toString()));
    assertThat(newJoin.getLeft(), is(instanceOf(Project.class))); // 验证左侧输入为Project节点
    Project leftInput = (Project) newJoin.getLeft();
    assertThat(leftInput.getProjects().get(empRow.getFieldCount()),
        hasToString(
            relBuilder.call(SqlStdOperatorTable.PLUS, leftKeyInputRef, // 验证计算字段表达式
                    relBuilder.literal(1)).toString()));
  }

  /**
   * Tests {@link RelOptUtil#pushDownJoinConditions(org.apache.calcite.rel.core.Join, RelBuilder)}
   * where the join condition contains a complex expression.
   */
  @Test void testPushDownJoinConditionsWithIsNotDistinct() {
    int leftJoinIndex = empScan.getRowType().getFieldNames().indexOf("DEPTNO"); // 获取EMP表中DEPTNO字段的索引
    int rightJoinIndex = deptRow.getFieldNames().indexOf("DEPTNO"); // 获取DEPT表中DEPTNO字段的索引

    RexInputRef leftKeyInputRef = RexInputRef.of(leftJoinIndex, empDeptJoinRelFields); // 创建左侧字段引用
    RexInputRef rightKeyInputRef =
        RexInputRef.of(empRow.getFieldCount() + rightJoinIndex, empDeptJoinRelFields); // 创建右侧字段引用
    RexNode joinCond =
        relBuilder.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, // 创建Join条件: EMP.DEPTNO + 1 IS NOT DISTINCT FROM DEPT.DEPTNO
            relBuilder.call(SqlStdOperatorTable.PLUS, leftKeyInputRef,
                relBuilder.literal(1)),
            rightKeyInputRef);

    // Build the join operator and push down join conditions
    relBuilder.push(empScan); // 压入EMP扫描节点
    relBuilder.push(deptScan); // 压入DEPT扫描节点
    relBuilder.join(JoinRelType.INNER, joinCond); // 创建内连接
    Join join = (Join) relBuilder.build(); // 构建Join节点
    RelNode transformed = RelOptUtil.pushDownJoinConditions(join, relBuilder); // 下推Join条件

    // Assert the new join operator
    assertThat(transformed.getRowType(), is(join.getRowType())); // 验证行类型不变
    assertThat(transformed, is(instanceOf(Project.class))); // 验证结果为Project节点
    RelNode transformedInput = transformed.getInput(0); // 获取Project的输入
    assertThat(transformedInput, is(instanceOf(Join.class))); // 验证输入为Join节点
    Join newJoin = (Join) transformedInput;
    assertThat(newJoin.getCondition(),
        hasToString(
            relBuilder.call(
                SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
                // Computed field is added at the end (and index start at 0)
                RexInputRef.of(empRow.getFieldCount(), join.getRowType()), // 计算字段添加在末尾
                // Right side is shifted by 1
                RexInputRef.of(empRow.getFieldCount() + 1 + rightJoinIndex, join.getRowType())) // 右侧字段索引偏移1
            .toString()));
    assertThat(newJoin.getLeft(), is(instanceOf(Project.class))); // 验证左侧输入为Project节点
    Project leftInput = (Project) newJoin.getLeft();
    assertThat(leftInput.getProjects().get(empRow.getFieldCount()),
        hasToString(
            relBuilder.call(SqlStdOperatorTable.PLUS, leftKeyInputRef, // 验证计算字段表达式
                    relBuilder.literal(1)).toString()));
  }

  /**
   * Tests {@link RelOptUtil#pushDownJoinConditions(org.apache.calcite.rel.core.Join, RelBuilder)}
   * where the join condition contains a complex expression.
   */
  @Test void testPushDownJoinConditionsWithExpandedIsNotDistinct() {
    int leftJoinIndex = empScan.getRowType().getFieldNames().indexOf("DEPTNO"); // 获取EMP表中DEPTNO字段的索引
    int rightJoinIndex = deptRow.getFieldNames().indexOf("DEPTNO"); // 获取DEPT表中DEPTNO字段的索引

    RexInputRef leftKeyInputRef = RexInputRef.of(leftJoinIndex, empDeptJoinRelFields); // 创建左侧字段引用
    RexInputRef rightKeyInputRef =
        RexInputRef.of(empRow.getFieldCount() + rightJoinIndex, empDeptJoinRelFields); // 创建右侧字段引用
    RexNode joinCond =
        relBuilder.or(
            relBuilder.equals(
                relBuilder.call(SqlStdOperatorTable.PLUS, leftKeyInputRef, // 创建展开的IS NOT DISTINCT FROM条件: (EMP.DEPTNO + 1 = DEPT.DEPTNO) OR (EMP.DEPTNO + 1 IS NULL AND DEPT.DEPTNO IS NULL)
                    relBuilder.literal(1)),
                rightKeyInputRef),
        relBuilder.call(SqlStdOperatorTable.AND,
            relBuilder.isNull(
                relBuilder.call(SqlStdOperatorTable.PLUS, leftKeyInputRef,
                    relBuilder.literal(1))),
            relBuilder.isNull(rightKeyInputRef)));


    // Build the join operator and push down join conditions
    relBuilder.push(empScan); // 压入EMP扫描节点
    relBuilder.push(deptScan); // 压入DEPT扫描节点
    relBuilder.join(JoinRelType.INNER, joinCond); // 创建内连接
    Join join = (Join) relBuilder.build(); // 构建Join节点
    RelNode transformed = RelOptUtil.pushDownJoinConditions(join, relBuilder); // 下推Join条件

    // Assert the new join operator
    assertThat(transformed.getRowType(), is(join.getRowType())); // 验证行类型不变
    assertThat(transformed, is(instanceOf(Project.class))); // 验证结果为Project节点
    RelNode transformedInput = transformed.getInput(0); // 获取Project的输入
    assertThat(transformedInput, is(instanceOf(Join.class))); // 验证输入为Join节点
    Join newJoin = (Join) transformedInput;
    assertThat(newJoin.getCondition(),
        hasToString(
            relBuilder.call(
                SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
                // Computed field is added at the end (and index start at 0)
                RexInputRef.of(empRow.getFieldCount(), join.getRowType()), // 计算字段添加在末尾
                // Right side is shifted by 1
                RexInputRef.of(empRow.getFieldCount() + 1 + rightJoinIndex, join.getRowType())) // 右侧字段索引偏移1
                .toString()));
    assertThat(newJoin.getLeft(), is(instanceOf(Project.class))); // 验证左侧输入为Project节点
    Project leftInput = (Project) newJoin.getLeft();
    assertThat(leftInput.getProjects().get(empRow.getFieldCount()),
        hasToString(
            relBuilder.call(SqlStdOperatorTable.PLUS, leftKeyInputRef, // 验证计算字段表达式
                    relBuilder.literal(1)).toString()));
  }

  /**
   * Tests {@link RelOptUtil#pushDownJoinConditions(org.apache.calcite.rel.core.Join, RelBuilder)}
   * where the join condition contains a complex expression.
   */
  @Test void testPushDownJoinConditionsWithExpandedIsNotDistinctUsingCase() {
    int leftJoinIndex = empScan.getRowType().getFieldNames().indexOf("DEPTNO"); // 获取EMP表中DEPTNO字段的索引
    int rightJoinIndex = deptRow.getFieldNames().indexOf("DEPTNO"); // 获取DEPT表中DEPTNO字段的索引

    RexInputRef leftKeyInputRef = RexInputRef.of(leftJoinIndex, empDeptJoinRelFields); // 创建左侧字段引用
    RexInputRef rightKeyInputRef =
        RexInputRef.of(empRow.getFieldCount() + rightJoinIndex, empDeptJoinRelFields); // 创建右侧字段引用
    RexNode joinCond =
        relBuilder.call(SqlStdOperatorTable.CASE, // 使用CASE表达式创建展开的IS NOT DISTINCT FROM条件
            relBuilder.isNull(
                relBuilder.call(SqlStdOperatorTable.PLUS, leftKeyInputRef,
                    relBuilder.literal(1))),
            relBuilder.isNull(rightKeyInputRef),
            relBuilder.isNull(rightKeyInputRef),
            relBuilder.isNull(
                relBuilder.call(SqlStdOperatorTable.PLUS, leftKeyInputRef,
                    relBuilder.literal(1))),
            relBuilder.equals(
                relBuilder.call(SqlStdOperatorTable.PLUS, leftKeyInputRef,
                    relBuilder.literal(1)),
                rightKeyInputRef));

    // Build the join operator and push down join conditions
    relBuilder.push(empScan); // 压入EMP扫描节点
    relBuilder.push(deptScan); // 压入DEPT扫描节点
    relBuilder.join(JoinRelType.INNER, joinCond); // 创建内连接
    Join join = (Join) relBuilder.build(); // 构建Join节点
    RelNode transformed = RelOptUtil.pushDownJoinConditions(join, relBuilder); // 下推Join条件

    // Assert the new join operator
    assertThat(transformed.getRowType(), is(join.getRowType())); // 验证行类型不变
    assertThat(transformed, is(instanceOf(Project.class))); // 验证结果为Project节点
    RelNode transformedInput = transformed.getInput(0); // 获取Project的输入
    assertThat(transformedInput, is(instanceOf(Join.class))); // 验证输入为Join节点
    Join newJoin = (Join) transformedInput;
    assertThat(newJoin.getCondition(),
        hasToString(
            relBuilder.call(
                SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
                // Computed field is added at the end (and index start at 0)
                RexInputRef.of(empRow.getFieldCount(), join.getRowType()), // 计算字段添加在末尾
                // Right side is shifted by 1
                RexInputRef.of(empRow.getFieldCount() + 1 + rightJoinIndex,
                    join.getRowType())).toString()));
    assertThat(newJoin.getLeft(), is(instanceOf(Project.class))); // 验证左侧输入为Project节点
    Project leftInput = (Project) newJoin.getLeft();
    assertThat(leftInput.getProjects().get(empRow.getFieldCount()),
        hasToString(
            relBuilder.call(SqlStdOperatorTable.PLUS, leftKeyInputRef, // 验证计算字段表达式
                    relBuilder.literal(1)).toString()));
  }

  /**
   * Test {@link RelOptUtil#createCastRel(RelNode, RelDataType, boolean)}
   * with changed field nullability or field name.
   */
  @Test void testCreateCastRel() {
    // Equivalent SQL:
    // select empno, ename, count(job)
    // from emp
    // group by empno, ename

    // Row type:
    // RecordType(SMALLINT NOT NULL EMPNO, VARCHAR(10) ENAME, BIGINT NOT NULL $f2) NOT NULL
    final RelNode agg = relBuilder
        .push(empScan)
        .aggregate(
            relBuilder.groupKey("EMPNO", "ENAME"), // 按EMPNO和ENAME分组
            relBuilder.count(relBuilder.field("JOB"))) // 计算JOB的数量
        .build(); // 构建聚合节点
    // Cast with row type(change nullability):
    // RecordType(SMALLINT EMPNO, VARCHAR(10) ENAME, BIGINT $f2) NOT NULL
    // The fields.
    final RelDataTypeField fieldEmpno = agg.getRowType().getField("EMPNO", false, false); // 获取EMPNO字段
    final RelDataTypeField fieldEname = agg.getRowType().getField("ENAME", false, false); // 获取ENAME字段
    final RelDataTypeField fieldJobCnt = Util.last(agg.getRowType().getFieldList()); // 获取最后一个字段(计数结果)
    final RelDataTypeFactory typeFactory = relBuilder.getTypeFactory(); // 获取类型工厂
    // The field types.
    final RelDataType fieldTypeEmpnoNullable = typeFactory
        .createTypeWithNullability(fieldEmpno.getType(), true); // 创建可空的EMPNO类型
    final RelDataType fieldTypeJobCntNullable = typeFactory
        .createTypeWithNullability(fieldJobCnt.getType(), true); // 创建可空的计数类型

    final RexBuilder rexBuilder = relBuilder.getRexBuilder(); // 获取Rex表达式构建器
    final RelDataType castRowType = typeFactory
        .createStructType(
            ImmutableList.of(
            Pair.of(fieldEmpno.getName(), fieldTypeEmpnoNullable), // EMPNO字段改为可空
            Pair.of(fieldEname.getName(), fieldEname.getType()),
            Pair.of(fieldJobCnt.getName(), fieldTypeJobCntNullable))); // 计数字段改为可空
    final RelNode castNode = RelOptUtil.createCastRel(agg, castRowType, false); // 创建类型转换节点
    final RelNode expectNode = relBuilder
        .push(agg)
        .project(
            rexBuilder.makeCast(
                fieldTypeEmpnoNullable,
                RexInputRef.of(0, agg.getRowType()),
                true, false),
            RexInputRef.of(1, agg.getRowType()),
            rexBuilder.makeCast(
                fieldTypeJobCntNullable,
                RexInputRef.of(2, agg.getRowType()),
                true, false))
        .build(); // 期望的结果:包含类型转换的Project节点
    assertThat(castNode.explain(), is(expectNode.explain())); // 验证类型转换节点符合期望

    // Cast with row type(change field name):
    // RecordType(SMALLINT NOT NULL EMPNO, VARCHAR(10) ENAME, BIGINT NOT NULL JOB_CNT) NOT NULL
    final RelDataType castRowType1 = typeFactory
        .createStructType(
            ImmutableList.of(
            Pair.of(fieldEmpno.getName(), fieldEmpno.getType()),
            Pair.of(fieldEname.getName(), fieldEname.getType()),
            Pair.of("JOB_CNT", fieldJobCnt.getType()))); // 将计数字段重命名为JOB_CNT
    final RelNode castNode1 = RelOptUtil.createCastRel(agg, castRowType1, true); // 创建类型转换节点(允许重命名)
    final RelNode expectNode1 = RelFactories
        .DEFAULT_PROJECT_FACTORY
        .createProject(
            agg,
            ImmutableList.of(),
            ImmutableList.of(
                RexInputRef.of(0, agg.getRowType()),
                RexInputRef.of(1, agg.getRowType()),
                RexInputRef.of(2, agg.getRowType())),
            ImmutableList.of(
                fieldEmpno.getName(),
                fieldEname.getName(),
                "JOB_CNT"), // 验证字段名改为JOB_CNT
            ImmutableSet.of());
    assertThat(castNode1.explain(), is(expectNode1.explain())); // 验证类型转换节点符合期望
    // Change the field JOB_CNT field name again.
    // The projection expect to be merged.
    final RelDataType castRowType2 = typeFactory
        .createStructType(
            ImmutableList.of(
            Pair.of(fieldEmpno.getName(), fieldEmpno.getType()),
            Pair.of(fieldEname.getName(), fieldEname.getType()),
            Pair.of("JOB_CNT2", fieldJobCnt.getType()))); // 将计数字段重命名为JOB_CNT2
    final RelNode castNode2 = RelOptUtil.createCastRel(agg, castRowType2, true); // 创建类型转换节点(允许重命名)
    final RelNode expectNode2 = RelFactories
        .DEFAULT_PROJECT_FACTORY
        .createProject(
            agg,
            ImmutableList.of(),
            ImmutableList.of(
                RexInputRef.of(0, agg.getRowType()),
                RexInputRef.of(1, agg.getRowType()),
                RexInputRef.of(2, agg.getRowType())),
            ImmutableList.of(
                fieldEmpno.getName(),
                fieldEname.getName(),
                "JOB_CNT2"), // 验证字段名改为JOB_CNT2
            ImmutableSet.of());
    assertThat(castNode2.explain(), is(expectNode2.explain())); // 验证类型转换节点符合期望
  }

  /** Dummy sub-class of ConverterRule, to check whether generated descriptions
   * are OK. */
  // ConverterRule的虚拟子类,用于测试生成的规则描述是否正确
  private static class MyConverterRule extends ConverterRule {
    static MyConverterRule create(RelTrait in, RelTrait out) {
      return Config.INSTANCE.withConversion(RelNode.class, in, out, null) // 创建转换规则配置:从in特征转换为out特征
          .withRuleFactory(MyConverterRule::new) // 设置规则工厂
          .toRule(MyConverterRule.class); // 转换为规则实例
    }

    MyConverterRule(Config config) {
      super(config); // 调用父类构造函数
    }

    @Override public RelNode convert(RelNode rel) {
      throw new UnsupportedOperationException(); // 抛出不支持操作异常,因为这只是测试用的虚拟类
    }
  }
}
