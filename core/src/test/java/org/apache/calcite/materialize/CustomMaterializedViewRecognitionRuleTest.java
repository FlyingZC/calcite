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
// 声明当前类所在的包，属于Apache Calcite框架的物化视图模块
package org.apache.calcite.materialize;

// 导入RelOptMaterialization类，表示物化视图的优化信息
import org.apache.calcite.plan.RelOptMaterialization;
// 导入RelOptMaterializations类，提供物化视图使用的静态方法
import org.apache.calcite.plan.RelOptMaterializations;
// 导入RelOptUtil类，提供关系表达式操作的工具方法
import org.apache.calcite.plan.RelOptUtil;
// 导入RelTraitDef类，表示关系表达式特征定义
import org.apache.calcite.plan.RelTraitDef;
// 导入SubstitutionVisitor类，用于访问和替换关系表达式
import org.apache.calcite.plan.SubstitutionVisitor;
// 导入SubstitutionVisitor.UnifyRule接口，定义统一规则的接口
import org.apache.calcite.plan.SubstitutionVisitor.UnifyRule;
// 导入RelNode接口，表示关系表达式的基类
import org.apache.calcite.rel.RelNode;
// 导入MutableCalc类，表示可变的Calc（计算）节点
import org.apache.calcite.rel.mutable.MutableCalc;
// 导入RelDataType接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataType;
// 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;
// 导入RexCall类，表示Rex表达式中的函数调用
import org.apache.calcite.rex.RexCall;
// 导入RexInputRef类，表示Rex表达式中的输入字段引用
import org.apache.calcite.rex.RexInputRef;
// 导入RexLiteral类，表示Rex表达式中的字面量常量
import org.apache.calcite.rex.RexLiteral;
// 导入RexNode接口，表示行表达式的基类
import org.apache.calcite.rex.RexNode;
// 导入RexUtil类，提供Rex表达式操作的工具方法
import org.apache.calcite.rex.RexUtil;
// 导入SchemaPlus接口，表示可扩展的Schema
import org.apache.calcite.schema.SchemaPlus;
// 导入AbstractTable抽象类，提供表的抽象实现
import org.apache.calcite.schema.impl.AbstractTable;
// 导入SqlKind枚举，表示SQL操作符的种类
import org.apache.calcite.sql.SqlKind;
// 导入SqlStdOperatorTable类，提供标准SQL操作符表
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
// 导入SqlParser类，用于解析SQL语句
import org.apache.calcite.sql.parser.SqlParser;
// 导入SqlTypeName枚举，表示SQL数据类型名称
import org.apache.calcite.sql.type.SqlTypeName;
// 导入CalciteAssert类，提供Calcite测试断言工具
import org.apache.calcite.test.CalciteAssert;
// 导入SqlToRelTestBase类，提供SQL到关系表达式转换的测试基类
import org.apache.calcite.test.SqlToRelTestBase;
// 导入Frameworks类，提供Calcite框架配置工具
import org.apache.calcite.tools.Frameworks;
// 导入RelBuilder接口，用于构建关系表达式
import org.apache.calcite.tools.RelBuilder;
// 导入NlsString类，表示国际化字符串
import org.apache.calcite.util.NlsString;
// 导入Pair类，表示键值对
import org.apache.calcite.util.Pair;

// 导入ImmutableList类，提供不可变列表实现
import com.google.common.collect.ImmutableList;
// 导入Lists类，提供列表操作工具
import com.google.common.collect.Lists;

// 导入Nullable注解，用于标记可空类型
import org.checkerframework.checker.nullness.qual.Nullable;
// 导入Test注解，用于标记测试方法
import org.junit.jupiter.api.Test;

// 导入ArrayList类，提供动态数组实现
import java.util.ArrayList;
// 导入List接口，表示列表集合
import java.util.List;

// 导入isLinux匹配器，用于比较字符串是否匹配Linux格式
import static org.apache.calcite.test.Matchers.isLinux;

// 导入assertThat断言方法，用于测试验证
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit tests for {@link RelOptMaterializations#useMaterializedViews}.
 * 单元测试类，用于测试RelOptMaterializations.useMaterializedViews方法
 * 该方法用于识别和使用物化视图来优化查询
 * 测试重点是自定义的物化视图识别规则，特别是针对LIKE操作符的匹配和补偿机制
 */
// 声明CustomMaterializedViewRecognitionRuleTest类，继承自SqlToRelTestBase测试基类
// 该类专门用于测试自定义物化视图识别规则的单元测试
public class CustomMaterializedViewRecognitionRuleTest extends SqlToRelTestBase {

  // 定义静态方法config，用于创建Calcite框架配置构建器
  // 返回值：Frameworks.ConfigBuilder对象，用于构建Calcite框架配置
  public static Frameworks.ConfigBuilder config() {
    // 创建根Schema，传入true表示启用默认Schema
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true);
    // 向根Schema中添加名为"mv0"的表，使用匿名内部类创建AbstractTable实例
    // 这个表模拟物化视图mv0，包含员工表的字段结构
    rootSchema.add("mv0", new AbstractTable() {
      // 重写getRowType方法，定义表的行类型（字段结构）
      // 参数：typeFactory - 关系数据类型工厂，用于创建数据类型
      // 返回值：RelDataType对象，描述表的字段及其类型
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        // 使用类型工厂构建器创建行类型
        return typeFactory.builder()
            // 添加empno字段，类型为INTEGER（员工编号）
            .add("empno", SqlTypeName.INTEGER)
            // 添加ename字段，类型为VARCHAR（员工姓名）
            .add("ename", SqlTypeName.VARCHAR)
            // 添加job字段，类型为VARCHAR（职位）
            .add("job", SqlTypeName.VARCHAR)
            // 添加mgr字段，类型为SMALLINT（经理编号）
            .add("mgr", SqlTypeName.SMALLINT)
            // 添加hiredate字段，类型为DATE（雇佣日期）
            .add("hiredate", SqlTypeName.DATE)
            // 添加sal字段，类型为DECIMAL（薪水）
            .add("sal", SqlTypeName.DECIMAL)
            // 添加comm字段，类型为DECIMAL（佣金）
            .add("comm", SqlTypeName.DECIMAL)
            // 添加deptno字段，类型为TINYINT（部门编号）
            .add("deptno", SqlTypeName.TINYINT)
            // 构建并返回行类型
            .build();
      }
    });
    // 创建新的框架配置构建器
    return Frameworks.newConfigBuilder()
        // 设置SQL解析器配置为默认配置
        .parserConfig(SqlParser.Config.DEFAULT)
        // 设置默认Schema，添加SCOTT_WITH_TEMPORAL规范（包含时间维度的SCOTT测试Schema）
        .defaultSchema(
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.SCOTT_WITH_TEMPORAL))
        // 设置关系特征定义为null（使用默认特征定义）
        .traitDefs((List<RelTraitDef>) null);
  }

  // 使用@Test注解标记这是一个测试方法
  // 方法名：testCushionLikeOperatorRecognitionRule，测试LIKE操作符的物化视图识别规则
  // 测试目的：验证当查询使用LIKE 'ABCD%'时，能否识别并使用物化视图mv0（该视图对应LIKE 'ABC%'）
  // 关键点：测试自定义的CustomizedMaterializationRule能否通过补偿机制识别出查询可以使用物化视图
  @Test void testCushionLikeOperatorRecognitionRule() {
    // 创建RelBuilder实例，用于构建关系表达式树
    // 使用config()方法获取的配置来创建RelBuilder
    final RelBuilder relBuilder = RelBuilder.create(config().build());
    // 构建查询关系表达式：扫描EMP表，然后过滤ename字段以'ABCD%'开头的数据
    // relBuilder.scan("EMP")：扫描EMP表
    // relBuilder.field(1)：引用第1个字段（ename字段，索引从0开始）
    // relBuilder.literal("ABCD%")：创建字面量'ABCD%'
    // SqlStdOperatorTable.LIKE：使用LIKE操作符
    // .build()：构建查询关系表达式
    final RelNode query = relBuilder.scan("EMP")
        .filter(
            relBuilder.call(SqlStdOperatorTable.LIKE,
            relBuilder.field(1), relBuilder.literal("ABCD%")))
        .build();
    // 构建目标关系表达式：扫描EMP表，然后过滤ename字段以'ABC%'开头的数据
    // 这个目标表达式代表物化视图的定义
    // 与query的区别是LIKE模式从'ABCD%'变为'ABC%'，更宽泛的条件
    final RelNode target = relBuilder.scan("EMP")
        .filter(
            relBuilder.call(SqlStdOperatorTable.LIKE,
            relBuilder.field(1), relBuilder.literal("ABC%")))
        .build();
    // 构建替换关系表达式：直接扫描物化视图mv0
    // 这是实际用来替换查询的关系表达式
    final RelNode replacement = relBuilder.scan("mv0").build();
    // 创建物化视图优化对象RelOptMaterialization
    // 参数1：replacement - 物化视图的实际表（mv0）
    // 参数2：target - 物化视图的定义（LIKE 'ABC%'的查询）
    // 参数3：null - 星型连接（未使用）
    // 参数4：Lists.newArrayList("mv0") - 物化视图的表名列表
    final RelOptMaterialization relOptMaterialization =
        new RelOptMaterialization(replacement,
            target, null, Lists.newArrayList("mv0"));
    // 创建统一规则列表，用于匹配和替换关系表达式
    // 从SubstitutionVisitor.DEFAULT_RULES复制默认规则
    final List<UnifyRule> rules =
        new ArrayList<>(SubstitutionVisitor.DEFAULT_RULES);
    // 添加自定义的物化视图规则CustomizedMaterializationRule到规则列表
    // 这个规则专门处理LIKE操作符的匹配和补偿
    rules.add(CustomizedMaterializationRule.INSTANCE);
    // 使用物化视图优化查询
    // 参数1：query - 原始查询关系表达式
    // 参数2：ImmutableList.of(relOptMaterialization) - 物化视图列表
    // 参数3：rules - 统一规则列表（包含自定义规则）
    // 返回值：优化后的关系表达式列表，每个元素是Pair<RelNode, List<RelOptMaterialization>>
    //   Key是优化后的关系表达式，Value是使用的物化视图列表
    final List<Pair<RelNode, List<RelOptMaterialization>>> relOptimized =
        RelOptMaterializations.useMaterializedViews(query,
            ImmutableList.of(relOptMaterialization), rules);
    // 定义期望的优化后的关系表达式字符串
    // 这个字符串表示查询被重写为：扫描mv0表，然后过滤ename字段以'ABCD%'开头
    // 关键点：原始查询扫描EMP表，优化后扫描物化视图mv0，并保留原始的过滤条件
    final String optimized = ""
        + "LogicalCalc(expr#0..7=[{inputs}], expr#8=['ABCD%'], expr#9=[LIKE($t1, $t8)], proj#0."
        + ".7=[{exprs}], $condition=[$t9])\n"
        + "  LogicalProject(empno=[CAST($0):SMALLINT NOT NULL], ename=[CAST($1):VARCHAR(10)], "
        + "job=[CAST($2):VARCHAR(9)], mgr=[CAST($3):SMALLINT], hiredate=[CAST($4):DATE], "
        + "sal=[CAST($5):DECIMAL(7, 2)], comm=[CAST($6):DECIMAL(7, 2)], deptno=[CAST($7)"
        + ":TINYINT])\n"
        + "    LogicalTableScan(table=[[mv0]])\n";
    // 将优化后的关系表达式转换为字符串格式
    // relOptimized.get(0).getKey()获取第一个优化结果的关系表达式
    final String relOptimizedStr = RelOptUtil.toString(relOptimized.get(0).getKey());
    // 断言优化后的关系表达式字符串是否与期望的字符串匹配
    // isLinux(optimized)确保字符串格式符合Linux规范（换行符等）
    assertThat(relOptimizedStr, isLinux(optimized));
  }

  /**
   * A customized materialization rule, which match expression of 'LIKE'
   * and match by compensation.
   * 自定义物化视图规则，专门处理LIKE操作符的匹配
   * 该规则通过补偿机制识别查询是否可以使用物化视图
   * 核心思想：如果查询的LIKE模式（如'ABCD%'）是物化视图LIKE模式（如'ABC%'）的子集，
   *          则可以在物化视图基础上添加额外的过滤条件来满足查询需求
   */
  // 声明CustomizedMaterializationRule内部静态类
  // 继承自SubstitutionVisitor.AbstractUnifyRule抽象类
  // AbstractUnifyRule是SubstitutionVisitor用于统一查询和目标表达式的抽象规则
  private static class CustomizedMaterializationRule
      extends SubstitutionVisitor.AbstractUnifyRule {

    // 声明静态常量INSTANCE，表示该规则的唯一实例（单例模式）
    // 使用static final确保全局只有一个实例，避免重复创建
    public static final CustomizedMaterializationRule INSTANCE =
        new CustomizedMaterializationRule();

    // 私有构造方法，防止外部创建实例（单例模式）
    // 调用父类构造方法，定义规则的匹配模式
    private CustomizedMaterializationRule() {
      // 调用父类AbstractUnifyRule的构造方法
      // 参数1：operand(MutableCalc.class, query(0)) - 定义查询表达式的匹配模式
      //        MutableCalc.class：查询必须包含MutableCalc节点
      //        query(0)：查询的第0个输入（即MutableCalc的子节点）
      // 参数2：operand(MutableCalc.class, target(0)) - 定义目标表达式（物化视图）的匹配模式
      //        MutableCalc.class：目标必须包含MutableCalc节点
      //        target(0)：目标的第0个输入
      // 参数3：1 - 规则的优先级，数值越小优先级越高
      super(operand(MutableCalc.class, query(0)),
          operand(MutableCalc.class, target(0)), 1);
    }

    // 重写apply方法，实现自定义的统一匹配逻辑
    // 参数：call - UnifyRuleCall对象，包含查询和目标表达式信息
    // 返回值：UnifyResult对象，表示匹配成功的结果；null表示匹配失败
    @Override protected SubstitutionVisitor.@Nullable UnifyResult apply(
        SubstitutionVisitor.UnifyRuleCall call) {
      // 从调用对象中获取查询表达式（MutableCalc节点）
      // call.query是待优化的原始查询
      final MutableCalc query = (MutableCalc) call.query;
      // 使用SubstitutionVisitor.explainCalc方法解析查询的MutableCalc节点
      // 该方法将MutableCalc分解为条件和投影两部分
      // 返回值：Pair对象，left是条件表达式，right是投影表达式列表
      final Pair<RexNode, List<RexNode>> queryExplained = SubstitutionVisitor.explainCalc(query);
      // 获取查询的条件表达式（WHERE子句）
      final RexNode queryCond = queryExplained.left;
      // 获取查询的投影表达式列表（SELECT子句）
      final List<RexNode> queryProjs = queryExplained.right;

      // 从调用对象中获取目标表达式（物化视图的MutableCalc节点）
      // call.target是物化视图的定义
      final MutableCalc target = (MutableCalc) call.target;
      // 使用SubstitutionVisitor.explainCalc方法解析目标的MutableCalc节点
      final Pair<RexNode, List<RexNode>> targetExplained =
          SubstitutionVisitor.explainCalc(target);
      // 获取目标的条件表达式（WHERE子句）
      final RexNode targetCond = targetExplained.left;
      // 获取目标的投影表达式列表（SELECT子句）
      final List<RexNode> targetProjs = targetExplained.right;
      // 解析查询条件中的LIKE表达式
      // 调用parseLikeCondition方法，将LIKE条件解析为（字段引用, 字面量）对
      // 如果不是LIKE条件或格式不符合，返回null
      final @Nullable Pair<RexNode, NlsString> parsedQ =
          parseLikeCondition(queryCond);
      // 解析目标条件中的LIKE表达式
      // 如果不是LIKE条件或格式不符合，返回null
      final @Nullable Pair<RexNode, NlsString> parsedT =
          parseLikeCondition(targetCond);
      // 检查是否满足物化视图使用的条件
      // 条件1：RexUtil.isIdentity(queryProjs, query.getInput().rowType) - 查询的投影是恒等投影（没有修改字段）
      // 条件2：RexUtil.isIdentity(targetProjs, target.getInput().rowType) - 目标的投影是恒等投影
      // 条件3：parsedQ != null - 查询条件是有效的LIKE表达式
      // 条件4：parsedT != null - 目标条件是有效的LIKE表达式
      if (RexUtil.isIdentity(queryProjs, query.getInput().rowType)
          && RexUtil.isIdentity(targetProjs, target.getInput().rowType)
          && parsedQ != null && parsedT != null) {
        // 检查查询和目标的LIKE操作是否作用于同一个字段
        // parsedQ.left是查询的字段引用，parsedT.left是目标的字段引用
        if (parsedQ.left.equals(parsedT.left)) {
          // 获取查询LIKE模式中的字面量字符串（如"ABCD%"）
          String literalQ = parsedQ.right.getValue();
          // 获取目标LIKE模式中的字面量字符串（如"ABC%"）
          String literalT = parsedT.right.getValue();
          // 检查是否满足补偿条件：
          // 条件1：literalQ.endsWith("%") - 查询模式以%结尾（前缀匹配）
          // 条件2：literalT.endsWith("%") - 目标模式以%结尾（前缀匹配）
          // 条件3：!literalQ.equals(literalT) - 查询模式和目标模式不相同
          // 条件4：literalQ.startsWith(literalT.substring(0, literalT.length() - 1)) - 查询模式以目标模式（去掉%）开头
          //        例如：查询"ABCD%"以"ABC"开头，目标"ABC%"
          if (literalQ.endsWith("%") && literalT.endsWith("%")
              && !literalQ.equals(literalT)
              && literalQ.startsWith(literalT.substring(0, literalT.length() - 1))) {
            // 返回匹配成功的结果
            // 创建新的MutableCalc节点，使用目标的输入，但应用查询的程序（条件）
            // 这样就在物化视图基础上保留了原始查询的过滤条件，实现了补偿
            return call.result(MutableCalc.of(target, query.program));
          }
        }
      }
      // 返回null，表示匹配失败，无法使用物化视图
      return null;
    }

    // 私有辅助方法，用于解析LIKE条件表达式
    // 参数：rexNode - 待解析的Rex表达式节点
    // 返回值：Pair对象，left是被LIKE的字段引用，right是LIKE模式的字面量字符串；如果不是LIKE条件则返回null
    private @Nullable Pair<RexNode, NlsString> parseLikeCondition(
        RexNode rexNode) {
      // 检查rexNode是否是RexCall实例（函数调用表达式）
      if (rexNode instanceof RexCall) {
        // 将rexNode强转为RexCall类型
        RexCall rexCall = (RexCall) rexNode;
        // 检查该调用是否是LIKE操作
        // 条件1：rexCall.getKind() == SqlKind.LIKE - 操作符类型是LIKE
        // 条件2：rexCall.operands.get(0) instanceof RexInputRef - 第一个操作数是字段引用（被LIKE的字段）
        // 条件3：rexCall.operands.get(1) instanceof RexLiteral - 第二个操作数是字面量（LIKE模式）
        if (rexCall.getKind() == SqlKind.LIKE
            && rexCall.operands.get(0) instanceof RexInputRef
            && rexCall.operands.get(1) instanceof RexLiteral) {
          // 返回Pair对象，包含字段引用和LIKE模式字符串
          // rexCall.operands.get(0) - 被LIKE的字段引用
          // ((RexLiteral) (rexCall.operands.get(1))).getValue() - 获取字面量的值，强转为NlsString类型
          // NlsString是Calcite中用于表示国际化字符串的类
          return Pair.of(rexCall.operands.get(0),
              (NlsString) ((RexLiteral) (rexCall.operands.get(1))).getValue());
        }
      }
      // 如果不是LIKE条件或格式不符合，返回null
      return null;
    }
  }

// 类定义结束
