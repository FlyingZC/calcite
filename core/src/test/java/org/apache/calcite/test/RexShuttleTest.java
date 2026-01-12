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
package org.apache.calcite.test;

import org.apache.calcite.plan.hep.HepPlanner; // 导入HepPlanner类，用于基于规则的优化器
import org.apache.calcite.plan.hep.HepProgram; // 导入HepProgram类，用于定义优化规则程序
import org.apache.calcite.plan.hep.HepProgramBuilder; // 导入HepProgramBuilder类，用于构建HepProgram
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数表达式树的节点
import org.apache.calcite.rel.logical.LogicalCalc; // 导入LogicalCalc类，表示逻辑计算操作节点
import org.apache.calcite.rel.rules.CoreRules; // 导入CoreRules类，包含Calcite核心转换规则
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示行表达式中的函数调用
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类，表示对输入字段的引用
import org.apache.calcite.rex.RexLocalRef; // 导入RexLocalRef类，表示对局部变量的引用
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式的基类
import org.apache.calcite.rex.RexShuttle; // 导入RexShuttle类，用于遍历和转换Rex节点的访问者模式工具
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，包含标准SQL操作符
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName类，定义SQL类型名称枚举
import org.apache.calcite.tools.RelBuilder; // 导入RelBuilder类，用于构建关系代数表达式树的构建器

import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法

import static org.hamcrest.CoreMatchers.is; // 导入is匹配器，用于断言比较
import static org.hamcrest.MatcherAssert.assertThat; // 导入断言工具类

/**
 * Unit tests for {@link RexShuttle}.
 * RexShuttle的单元测试类
 * 
 * RexShuttle是Calcite中用于遍历和转换RexNode（行表达式）的访问者模式工具类
 * 它提供了一种机制来遍历表达式树，并在遍历过程中对节点进行修改或替换
 * 这个测试类主要验证RexShuttle在修改Project和Calc节点时能否正确更新它们的rowType（行类型）
 * 
 * 主要测试场景：
 * 1. testProjectUpdatesRowType: 测试使用RexShuttle修改Project节点中的表达式后，rowType是否正确更新
 * 2. testCalcUpdatesRowType: 测试使用RexShuttle修改Calc节点中的表达式后，rowType是否正确更新
 * 
 * 这两个测试都针对CALCITE-3165这个JIRA问题，该问题描述了Project节点使用RexShuttle修改表达式后，
 * rowType没有正确更新的bug
 */
class RexShuttleTest { // 定义RexShuttleTest测试类

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3165">[CALCITE-3165]
   * Project#accept(RexShuttle shuttle) does not update rowType</a>.
   * 测试用例：验证使用RexShuttle修改Project节点后，rowType是否正确更新
   * 
   * 这个测试针对CALCITE-3165问题，该问题描述了Project节点使用RexShuttle修改表达式后，
   * rowType没有正确更新的bug
   * 
   * 测试步骤：
   * 1. 创建一个Project节点，查询EMP表的DEPTNO和SAL字段
   * 2. 创建另一个Project节点，将DEPTNO和SAL都转换为VARCHAR类型
   * 3. 使用RexShuttle修改第一个Project节点，将所有RexInputRef转换为CAST(RexInputRef AS VARCHAR)
   * 4. 验证修改后的Project节点的rowType与第二个Project节点的rowType是否一致
   * 
   * 预期结果：两个rowType应该完全一致，证明RexShuttle正确更新了rowType
   */
  @Test void testProjectUpdatesRowType() { // 定义测试方法testProjectUpdatesRowType
    final RelBuilder builder = RelBuilder.create(RelBuilderTest.config().build()); // 创建RelBuilder实例，用于构建关系代数表达式树

    // Equivalent SQL: SELECT deptno, sal FROM emp
    // 等价SQL：从EMP表中选择deptno和sal字段
    final RelNode root = // 创建root RelNode，表示初始的关系代数表达式树
        builder // 使用builder构建器
            .scan("EMP") // 扫描EMP表，生成LogicalTableScan节点
            .project( // 创建Project节点，对输入进行投影操作
                builder.field("DEPTNO"), // 添加DEPTNO字段到投影列表
                builder.field("SAL")) // 添加SAL字段到投影列表
            .build(); // 构建完整的RelNode树

    // Equivalent SQL: SELECT CAST(deptno AS VARCHAR), CAST(sal AS VARCHAR) FROM emp
    // 等价SQL：从EMP表中选择deptno和sal字段，并将它们都转换为VARCHAR类型
    final RelNode rootWithCast = // 创建rootWithCast RelNode，表示包含类型转换的关系代数表达式树
        builder // 使用builder构建器
            .scan("EMP") // 扫描EMP表，生成LogicalTableScan节点
            .project( // 创建Project节点，对输入进行投影操作
                builder.cast(builder.field("DEPTNO"), SqlTypeName.VARCHAR), // 将DEPTNO字段转换为VARCHAR类型
                builder.cast(builder.field("SAL"), SqlTypeName.VARCHAR)) // 将SAL字段转换为VARCHAR类型
            .build(); // 构建完整的RelNode树
    final RelDataType type = rootWithCast.getRowType(); // 获取rootWithCast的rowType（行类型），作为期望的结果类型

    // Transform the first expression into the second one, by using a RexShuttle
    // that converts every RexInputRef into a 'CAST(RexInputRef AS VARCHAR)'
    // 使用RexShuttle将第一个表达式转换为第二个表达式，将每个RexInputRef转换为'CAST(RexInputRef AS VARCHAR)'
    final RelNode rootWithCastViaRexShuttle = root.accept(new RexShuttle() { // 使用RexShuttle访问并修改root节点，生成新的RelNode
      @Override public RexNode visitInputRef(RexInputRef inputRef) { // 重写visitInputRef方法，处理输入引用节点
        return  builder.cast(inputRef, SqlTypeName.VARCHAR); // 将输入引用转换为VARCHAR类型的CAST表达式
      }
    }); // 匿名RexShuttle类定义结束
    final RelDataType type2 = rootWithCastViaRexShuttle.getRowType(); // 获取修改后的RelNode的rowType

    assertThat(type, is(type2)); // 断言：验证type和type2是否相等，即rowType是否正确更新
  } // testProjectUpdatesRowType方法结束

  @Test void testCalcUpdatesRowType() { // 定义测试方法testCalcUpdatesRowType，测试Calc节点的rowType更新
    final RelBuilder builder = RelBuilder.create(RelBuilderTest.config().build()); // 创建RelBuilder实例

    // Equivalent SQL: SELECT deptno, sal, sal + 20 FROM emp
    // 等价SQL：从EMP表中选择deptno、sal字段，以及sal+20的计算结果
    final RelNode root = // 创建root RelNode，表示初始的关系代数表达式树
        builder // 使用builder构建器
            .scan("EMP") // 扫描EMP表，生成LogicalTableScan节点
            .project( // 创建Project节点，对输入进行投影操作
                builder.field("DEPTNO"), // 添加DEPTNO字段到投影列表
                builder.field("SAL"), // 添加SAL字段到投影列表
                builder.call(SqlStdOperatorTable.PLUS, // 创建加法调用表达式
                    builder.field("SAL"), builder.literal(20))) // 计算SAL + 20
            .build(); // 构建完整的RelNode树

    HepProgram program = new HepProgramBuilder() // 创建HepProgramBuilder，用于构建优化规则程序
        .addRuleInstance(CoreRules.PROJECT_TO_CALC) // 添加PROJECT_TO_CALC规则，将Project节点转换为Calc节点
        .build(); // 构建HepProgram
    HepPlanner planner = new HepPlanner(program); // 创建HepPlanner优化器实例，使用HepProgram
    planner.setRoot(root); // 设置优化器的根节点为root
    LogicalCalc calc = (LogicalCalc) planner.findBestExp(); // 执行优化，获取最优的LogicalCalc节点

    final RelNode calcWithCastViaRexShuttle = calc.accept(new RexShuttle() { // 使用RexShuttle访问并修改calc节点
      @Override public RexNode visitCall(RexCall call) { // 重写visitCall方法，处理函数调用节点
        return builder.cast(call, SqlTypeName.VARCHAR); // 将函数调用转换为VARCHAR类型的CAST表达式
      }

      @Override public RexNode visitLocalRef(RexLocalRef localRef) { // 重写visitLocalRef方法，处理局部引用节点
        if (calc.getProgram().getExprList().get(localRef.getIndex()) // 检查局部引用对应的表达式是否为RexCall
            instanceof RexCall) { // 如果是RexCall类型
          return new RexLocalRef(localRef.getIndex(), // 创建新的RexLocalRef，保持相同的索引
              builder.getTypeFactory().createSqlType(SqlTypeName.VARCHAR)); // 但将类型改为VARCHAR
        } else { // 如果不是RexCall类型
          return localRef; // 返回原始的局部引用，不做修改
        }
      }
    }); // 匿名RexShuttle类定义结束

    // Equivalent SQL: SELECT deptno, sal, CAST(sal + 20 AS VARCHAR) FROM emp
    // 等价SQL：从EMP表中选择deptno、sal字段，以及sal+20的计算结果并转换为VARCHAR类型
    final RelNode rootWithCast = // 创建rootWithCast RelNode，表示包含类型转换的关系代数表达式树
        builder // 使用builder构建器
            .scan("EMP") // 扫描EMP表，生成LogicalTableScan节点
            .project( // 创建Project节点，对输入进行投影操作
                builder.field("DEPTNO"), // 添加DEPTNO字段到投影列表
                builder.field("SAL"), // 添加SAL字段到投影列表
                builder.cast( // 创建CAST表达式
                    builder.call(SqlStdOperatorTable.PLUS, // 创建加法调用表达式
                        builder.field("SAL"), builder.literal(20)), SqlTypeName.VARCHAR)) // 将SAL + 20转换为VARCHAR
            .build(); // 构建完整的RelNode树
    assertThat(calcWithCastViaRexShuttle.getRowType(), is(rootWithCast.getRowType())); // 断言：验证修改后的Calc节点的rowType与期望的rowType是否一致
  }
} // RexShuttleTest类结束
