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
package org.apache.calcite.sql2rel;  // 定义包名，该类属于sql2rel子包，负责SQL到关系代数转换相关的测试

import org.apache.calcite.plan.RelTraitDef;  // 导入RelTraitDef类，用于定义关系表达式的特征（如排序、分布等）
import org.apache.calcite.rel.RelNode;  // 导入RelNode类，表示关系代数表达式树中的节点
import org.apache.calcite.rel.core.JoinRelType;  // 导入JoinRelType枚举，定义连接类型（内连接、左外连接、右外连接等）
import org.apache.calcite.rel.core.RelFactories;  // 导入RelFactories类，提供创建关系表达式节点的工厂方法
import org.apache.calcite.rex.RexCorrelVariable;  // 导入RexCorrelVariable类，表示相关变量，用于处理子查询中的相关性
import org.apache.calcite.schema.SchemaPlus;  // 导入SchemaPlus类，表示Calcite中的模式（Schema），包含表、函数等元数据
import org.apache.calcite.sql.fun.SqlStdOperatorTable;  // 导入SqlStdOperatorTable类，包含标准SQL操作符（如PLUS、MINUS等）
import org.apache.calcite.sql.parser.SqlParser;  // 导入SqlParser类，用于解析SQL语句为抽象语法树
import org.apache.calcite.test.CalciteAssert;  // 导入CalciteAssert类，提供测试工具和断言方法
import org.apache.calcite.tools.Frameworks;  // 导入Frameworks类，提供创建Calcite框架配置的工具
import org.apache.calcite.tools.RelBuilder;  // 导入RelBuilder类，用于构建关系代数表达式树的构建器
import org.apache.calcite.util.Holder;  // 导入Holder类，用于持有和传递可变值

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入Nullable注解，标记可能为null的值
import org.junit.jupiter.api.Test;  // 导入Test注解，标记测试方法

import java.util.List;  // 导入List接口，用于处理列表集合

import static org.apache.calcite.test.Matchers.hasTree;  // 静态导入hasTree匹配器，用于验证关系表达式树的形状

import static org.hamcrest.MatcherAssert.assertThat;  // 静态导入assertThat方法，用于断言验证

/**
 * Tests for {@link CorrelateProjectExtractor}.
 * CorrelateProjectExtractor测试类
 * 
 * 该测试类用于测试CorrelateProjectExtractor（关联投影提取器）的功能。
 * CorrelateProjectExtractor是一个关系代数优化器，用于处理包含相关子查询的SQL语句。
 * 
 * 核心功能说明：
 * 1. 相关子查询（Correlated Subquery）：子查询引用了外部查询的列，需要在执行时建立关联关系
 * 2. Correlate节点：Calcite中用Correlate节点表示相关连接，类似于嵌套循环连接
 * 3. Project提取：将Filter条件中对相关变量的复杂表达式提取为Project节点，提高优化空间
 * 
 * 优化原理：
 * - 原始查询中，Filter条件可能包含对相关变量的复杂表达式（如 $cor0.DEPTNO + 10）
 * - 这些表达式在每次迭代中都需要重新计算，影响性能
 * - CorrelateProjectExtractor将这些表达式提取到Project节点中，预先计算并存储
 * - 这样可以避免重复计算，并允许后续优化器进行更多优化（如常量折叠、表达式简化等）
 * 
 * 测试场景：
 * - testSingleCorrelationCallOverVariableInFilter：测试单个相关变量在Filter中的表达式提取
 * - testDoubleCorrelationCallOverVariableInFilters：测试多个相关变量在多个Filter中的表达式提取
 */
public class CorrelateProjectExtractorTest {  // 定义测试类，用于测试CorrelateProjectExtractor的功能
  
  /**
   * 创建测试用的Frameworks配置构建器
   * 
   * 作用：
   * - 创建Calcite框架的配置，用于构建关系代数表达式
   * - 配置SQL解析器、默认Schema、特征定义等
   * 
   * 返回值：
   * - Frameworks.ConfigBuilder：框架配置构建器，可用于创建RelBuilder
   * 
   * 配置说明：
   * - 使用默认的SQL解析器配置
   * - 默认Schema使用SCOTT_WITH_TEMPORAL，包含EMP、DEPT等经典测试表
   * - 特征定义设为null，使用默认的特征集
   * 
   * 使用场景：
   * - 在测试方法中调用，创建RelBuilder来构建测试用的关系代数表达式
   * - 确保所有测试使用相同的配置，保证测试结果的一致性
   */
  public static Frameworks.ConfigBuilder config() {  // 定义静态方法，创建框架配置构建器
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true);  // 创建根Schema，true表示添加内置函数
    return Frameworks.newConfigBuilder()  // 创建新的配置构建器
        .parserConfig(SqlParser.Config.DEFAULT)  // 设置SQL解析器配置为默认值
        .defaultSchema(  // 设置默认Schema
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.SCOTT_WITH_TEMPORAL))  // 添加SCOTT_WITH_TEMPORAL Schema到根Schema
        .traitDefs((List<RelTraitDef>) null);  // 设置特征定义为null，使用默认特征
  }

  /**
   * 测试单个相关变量在Filter条件中的表达式提取
   * 
   * 测试目的：
   * - 验证CorrelateProjectExtractor能够正确识别并提取Filter中对相关变量的复杂表达式
   * - 验证提取后的关系代数树结构符合预期
   * - 验证requiredColumns列集合的更新是否正确
   * 
   * 测试场景：
   * - EMP表与DEPT表进行左外连接
   * - 连接条件：DEPT.DEPTNO = EMP.DEPTNO + 10
   * - 表达式"EMP.DEPTNO + 10"在Filter条件中，应该被提取为Project节点
   * 
   * 优化前（before）：
   * - Correlate节点直接连接两个TableScan
   * - Filter条件中包含复杂表达式 +(10, $cor0.DEPTNO)
   * - requiredColumns={7}，表示只需要EMP表的第7列（DEPTNO）
   * 
   * 优化后（after）：
   * - 在EMP表上添加Project节点，计算新列$f8 = DEPTNO + 10
   * - Filter条件简化为 =($0, $cor0.$f8)，直接使用预计算的列
   * - requiredColumns={8}，更新为需要新计算的第8列
   * - 顶层添加Project节点，重新组织输出列的顺序和命名
   * 
   * 验证点：
   * - 关系代数树的结构是否符合预期
   * - Project节点是否正确添加
   * - Filter条件是否正确简化
   * - requiredColumns是否正确更新
   */
  @Test void testSingleCorrelationCallOverVariableInFilter() {  // 定义测试方法，测试单个相关变量的表达式提取
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例，用于构建关系代数表达式
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty();  // 创建Holder对象，用于持有相关变量引用
    RelNode before = builder.scan("EMP")  // 扫描EMP表，创建LogicalTableScan节点
        .variable(v::set)  // 将EMP表标记为相关变量，并将变量引用保存到Holder中
        .scan("DEPT")  // 扫描DEPT表，创建LogicalTableScan节点
        .filter(  // 添加Filter节点，过滤DEPT表的数据
            builder.equals(builder.field(0),  // 创建等值条件：DEPT表的第0列（DEPTNO）等于...
                builder.call(  // 调用函数表达式
                    SqlStdOperatorTable.PLUS,  // 使用加法操作符
                    builder.literal(10),  // 常量10
                    builder.field(v.get(), "DEPTNO"))))  // 相关变量$cor0的DEPTNO字段，即EMP.DEPTNO
        .correlate(JoinRelType.LEFT, v.get().id, builder.field(2, 0, "DEPTNO"))  // 创建Correlate节点，左外连接，基于EMP.DEPTNO
        .build();  // 构建关系代数表达式树

    // 定义优化前的预期计划字符串，用于验证
    final String planBefore = ""  // 初始化计划字符串
        + "LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{7}])\n"  // Correlate节点，相关变量$cor0，左外连接，需要EMP表的第7列（DEPTNO）
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // EMP表的扫描节点
        + "  LogicalFilter(condition=[=($0, +(10, $cor0.DEPTNO))])\n"  // Filter节点，条件：DEPTNO = 10 + $cor0.DEPTNO
        + "    LogicalTableScan(table=[[scott, DEPT]])\n";  // DEPT表的扫描节点
    assertThat(before, hasTree(planBefore));  // 验证before的关系代数树结构与预期一致

    // 应用CorrelateProjectExtractor优化器，提取Filter中的表达式到Project节点
    RelNode after = before.accept(new CorrelateProjectExtractor(RelFactories.LOGICAL_BUILDER));  // 接受优化器访问，返回优化后的关系代数树
    // 定义优化后的预期计划字符串，用于验证
    final String planAfter = ""  // 初始化优化后的计划字符串
        + "LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], DEPTNO0=[$9], DNAME=[$10], LOC=[$11])\n"  // 顶层Project节点，重新组织输出列
        + "  LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{8}])\n"  // Correlate节点，requiredColumns更新为{8}，需要新计算的列
        + "    LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], $f8=[+(10, $7)])\n"  // 在EMP表上添加Project节点，计算新列$f8 = DEPTNO + 10
        + "      LogicalTableScan(table=[[scott, EMP]])\n"  // EMP表的扫描节点
        + "    LogicalFilter(condition=[=($0, $cor0.$f8)])\n"  // Filter节点，条件简化为：DEPTNO = $cor0.$f8，使用预计算的列
        + "    LogicalTableScan(table=[[scott, DEPT]])\n";  // DEPT表的扫描节点
    assertThat(after, hasTree(planAfter));  // 验证after的关系代数树结构与预期一致
  }

  /**
   * 测试多个相关变量在多个Filter条件中的表达式提取
   * 
   * 测试目的：
   * - 验证CorrelateProjectExtractor能够处理多个相关变量的场景
   * - 验证嵌套Correlate节点中的表达式提取是否正确
   * - 验证每个相关变量对应的requiredColumns是否正确更新
   * 
   * 测试场景：
   * - EMP表与第一个DEPT表左外连接，条件：DEPT.DEPTNO = EMP.DEPTNO + 10
   * - 结果与第二个DEPT表左外连接，条件：DEPT.DEPTNO = EMP.DEPTNO - 50
   * - 两个Filter条件中都包含对相关变量的表达式，都应该被提取
   * 
   * 优化前（before）：
   * - 两个嵌套的Correlate节点，分别使用$cor0和$cor1
   * - 两个Filter条件中分别包含复杂表达式 +(10, $cor0.DEPTNO) 和 -(50, $cor1.DEPTNO)
   * - 两个cor0和cor1的requiredColumns都是{7}，都需要EMP表的DEPTNO列
   * 
   * 优化后（after）：
   * - 在EMP表上添加Project节点，计算新列$f8 = DEPTNO + 10
   * - 在中间结果上添加Project节点，计算新列$f11 = DEPTNO - 50
   * - 第一个Filter条件简化为 =($0, $cor0.$f8)
   * - 第二个Filter条件简化为 =($0, $cor1.$f11)
   * - cor0的requiredColumns更新为{8}，cor1的requiredColumns更新为{11}
   * - 顶层添加Project节点，重新组织输出列
   * 
   * 验证点：
   * - 嵌套Correlate节点的处理是否正确
   * - 多个Project节点的添加位置是否正确
   * - 两个Filter条件是否都正确简化
   * - 两个requiredColumns是否都正确更新
   * - 输出列的命名和顺序是否正确
   */
  @Test void testDoubleCorrelationCallOverVariableInFilters() {  // 定义测试方法，测试多个相关变量的表达式提取
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例，用于构建关系代数表达式
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty();  // 创建Holder对象，用于持有相关变量引用
    RelNode before = builder  // 开始构建关系代数表达式
        .scan("EMP")  // 扫描EMP表，创建LogicalTableScan节点
        .variable(v::set)  // 将EMP表标记为相关变量$cor0，并将变量引用保存到Holder中
        .scan("DEPT")  // 扫描第一个DEPT表，创建LogicalTableScan节点
        .filter(  // 添加Filter节点，过滤第一个DEPT表的数据
            builder.equals(builder.field("DEPTNO"),  // 创建等值条件：DEPT.DEPTNO等于...
                builder.call(SqlStdOperatorTable.PLUS,  // 调用加法函数
                    builder.literal(10),  // 常量10
                    builder.field(v.get(), "DEPTNO"))))  // 相关变量$cor0的DEPTNO字段，即EMP.DEPTNO
        .correlate(JoinRelType.LEFT, v.get().id, builder.field(2, 0, "DEPTNO"))  // 创建第一个Correlate节点，左外连接，基于EMP.DEPTNO
        .variable(v::set)  // 将中间结果标记为新的相关变量$cor1，覆盖Holder中的引用
        .scan("DEPT")  // 扫描第二个DEPT表，创建LogicalTableScan节点
        .filter(  // 添加Filter节点，过滤第二个DEPT表的数据
            builder.equals(builder.field("DEPTNO"),  // 创建等值条件：DEPT.DEPTNO等于...
                builder.call(SqlStdOperatorTable.MINUS,  // 调用减法函数
                    builder.literal(50),  // 常量50
                    builder.field(v.get(), "DEPTNO"))))  // 相关变量$cor1的DEPTNO字段，即EMP.DEPTNO
        .correlate(JoinRelType.LEFT, v.get().id, builder.field(2, 0, "DEPTNO"))  // 创建第二个Correlate节点，左外连接，基于EMP.DEPTNO
        .build();  // 构建关系代数表达式树

    // 定义优化前的预期计划字符串，用于验证
    final String planBefore = ""  // 初始化计划字符串
        + "LogicalCorrelate(correlation=[$cor1], joinType=[left], requiredColumns=[{7}])\n"  // 外层Correlate节点，相关变量$cor1，左外连接，需要EMP表的第7列（DEPTNO）
        + "  LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{7}])\n"  // 内层Correlate节点，相关变量$cor0，左外连接，需要EMP表的第7列（DEPTNO）
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // EMP表的扫描节点
        + "    LogicalFilter(condition=[=($0, +(10, $cor0.DEPTNO))])\n"  // 第一个Filter节点，条件：DEPTNO = 10 + $cor0.DEPTNO
        + "      LogicalTableScan(table=[[scott, DEPT]])\n"  // 第一个DEPT表的扫描节点
        + "  LogicalFilter(condition=[=($0, -(50, $cor1.DEPTNO))])\n"  // 第二个Filter节点，条件：DEPTNO = 50 - $cor1.DEPTNO
        + "    LogicalTableScan(table=[[scott, DEPT]])\n";  // 第二个DEPT表的扫描节点
    assertThat(before, hasTree(planBefore));  // 验证before的关系代数树结构与预期一致

    // 应用CorrelateProjectExtractor优化器，提取Filter中的表达式到Project节点
    RelNode after = before.accept(new CorrelateProjectExtractor(RelFactories.LOGICAL_BUILDER));  // 接受优化器访问，返回优化后的关系代数树
    // 定义优化后的预期计划字符串，用于验证
    final String planAfter = ""  // 初始化优化后的计划字符串
        + "LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], DEPTNO0=[$8], DNAME=[$9], LOC=[$10], DEPTNO1=[$12], DNAME0=[$13], LOC0=[$14])\n"  // 顶层Project节点，重新组织输出列，区分两个DEPT表的列
        + "  LogicalCorrelate(correlation=[$cor1], joinType=[left], requiredColumns=[{11}])\n"  // 外层Correlate节点，requiredColumns更新为{11}，需要新计算的列$f11
        + "    LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], DEPTNO0=[$9], DNAME=[$10], LOC=[$11], $f11=[-(50, $7)])\n"  // 在内层结果上添加Project节点，计算新列$f11 = DEPTNO - 50
        + "      LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{8}])\n"  // 内层Correlate节点，requiredColumns更新为{8}，需要新计算的列$f8
        + "        LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], $f8=[+(10, $7)])\n"  // 在EMP表上添加Project节点，计算新列$f8 = DEPTNO + 10
        + "          LogicalTableScan(table=[[scott, EMP]])\n"  // EMP表的扫描节点
        + "        LogicalFilter(condition=[=($0, $cor0.$f8)])\n"  // 第一个Filter节点，条件简化为：DEPTNO = $cor0.$f8
        + "          LogicalTableScan(table=[[scott, DEPT]])\n"  // 第一个DEPT表的扫描节点
        + "    LogicalFilter(condition=[=($0, $cor1.$f11)])\n"  // 第二个Filter节点，条件简化为：DEPTNO = $cor1.$f11
        + "      LogicalTableScan(table=[[scott, DEPT]])\n";  // 第二个DEPT表的扫描节点
    assertThat(after, hasTree(planAfter));  // 验证after的关系代数树结构与预期一致
  }
}
