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
// Apache许可证声明，指定本代码的使用条件和限制
package org.apache.calcite.test; // 定义包名，表示此测试类属于org.apache.calcite.test包

import org.apache.calcite.plan.RelOptPredicateList; // 导入RelOptPredicateList类，用于表示关系表达式的谓词列表，包含上拉谓词、左推断谓词、右推断谓词和常量映射
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式的基础接口，所有关系操作都实现此接口
import org.apache.calcite.rel.core.JoinRelType; // 导入JoinRelType枚举，表示连接类型，包括INNER(内连接)、LEFT(左外连接)、RIGHT(右外连接)、FULL(全外连接)等
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据信息，如谓词、行数、唯一键等
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator接口，表示SQL操作符的基类，所有SQL函数和操作符都实现此接口
import org.apache.calcite.sql.fun.SqlLibraryOperators; // 导入SqlLibraryOperators类，包含SQL库特定的操作符定义
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，包含标准SQL操作符的定义，如AND、OR、=、>等
import org.apache.calcite.tools.FrameworkConfig; // 导入FrameworkConfig接口，表示Calcite框架的配置信息，用于创建RelBuilder
import org.apache.calcite.tools.RelBuilder; // 导入RelBuilder类，用于构建关系表达式树的工具类，提供流式API来创建各种关系操作

import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法
import org.junit.jupiter.params.ParameterizedTest; // 导入ParameterizedTest注解，用于标记参数化测试方法
import org.junit.jupiter.params.provider.CsvFileSource; // 导入CsvFileSource注解，用于从CSV文件读取测试参数

import java.util.Arrays; // 导入Arrays工具类，用于数组操作

import static org.apache.calcite.test.Matchers.sortsAs; // 导入sortsAs匹配器，用于验证集合是否按指定顺序排序

import static org.hamcrest.MatcherAssert.assertThat; // 导入assertThat方法，用于断言测试结果
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入assertTrue方法，用于断言条件为真

/** Tests for {@link org.apache.calcite.rel.metadata.RelMdPredicates} class. */ // 类级别的Javadoc注释，说明此类是RelMdPredicates类的测试类
// RelMdPredicates是Calcite中负责计算关系表达式谓词元数据的提供者，主要功能包括：
// 1. 从Filter操作中提取谓词
// 2. 从Join操作中上拉谓词（pull up predicates）
// 3. 推断左表和右表的谓词（inferred predicates）
// 4. 识别常量表达式
// 5. 处理子查询和复杂表达式中的谓词
// 本测试类通过多种场景验证RelMdPredicates的正确性，包括不同类型的Join操作、谓词推断、随机函数处理等
public class RelMdPredicatesTest { // 定义测试类RelMdPredicatesTest，用于测试RelMdPredicates元数据提供者的功能

  @ParameterizedTest(name = "{0}") // 参数化测试注解，name="{0}"表示使用第一个参数作为测试名称，即joinType的值
  @CsvFileSource(resources = "RelMdPredicatesTestPullUpFromJoin.csv", delimiter = ';') // 从CSV文件读取测试参数，文件名为RelMdPredicatesTestPullUpFromJoin.csv，使用分号作为分隔符
  void testPullUpPredicatesFromJoin(JoinRelType joinType, String expectedPredicates) { // 测试方法：验证从Join操作中上拉谓词的功能，参数joinType表示连接类型，expectedPredicates表示期望的上拉谓词列表
    FrameworkConfig config = RelBuilderTest.config().build(); // 创建框架配置对象，使用RelBuilderTest的默认配置构建，配置包括类型系统、规则集等
    RelBuilder b = RelBuilder.create(config); // 创建关系构建器RelBuilder实例，用于构建关系表达式树，传入配置对象
    RelNode rel = b // 开始构建关系表达式树，最终生成一个Join节点
        .scan("EMP") // 扫描EMP表，创建一个TableScan节点，表示从EMP表读取数据
        .filter(b.equals(b.field("ENAME"), b.literal("Victor"))) // 添加Filter节点，过滤条件为ENAME字段等于字符串"Victor"，b.field("ENAME")引用当前输入的第0个字段的ENAME列，b.literal("Victor")创建字符串常量
        .scan("DEPT") // 扫描DEPT表，创建另一个TableScan节点，注意这里会创建一个新的输入分支
        .filter(b.equals(b.field("DNAME"), b.literal("CSD"))) // 添加Filter节点，过滤条件为DNAME字段等于字符串"CSD"，这里引用的是DEPT表的DNAME列
        .join( // 创建Join节点，将前面的两个输入（EMP表和DEPT表）进行连接
            joinType, b.equals( // 指定连接类型（由参数joinType决定，可能是INNER、LEFT、RIGHT或FULL），并指定连接条件
            b.field(2, 0, "DEPTNO"), // 引用连接后第2个输入的第0个分支（即EMP表）的DEPTNO字段，b.field(inputCount, inputIndex, fieldName)用于引用多输入场景下的字段
            b.field(2, 1, "DEPTNO"))) // 引用连接后第2个输入的第1个分支（即DEPT表）的DEPTNO字段，连接条件为EMP.DEPTNO = DEPT.DEPTNO
        .build(); // 完成关系表达式树的构建，返回最终的RelNode节点（Join节点）
    RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象，从关系节点的Cluster中获取，Cluster包含类型工厂、规则集等共享信息
    RelOptPredicateList list = mq.getPulledUpPredicates(rel); // 调用元数据查询获取上拉谓词列表，RelMdPredicates提供者会分析Join节点及其子节点，提取可以上拉的谓词
    assertThat(list.pulledUpPredicates, sortsAs(expectedPredicates)); // 断言实际的上拉谓词列表与期望的谓词列表匹配（排序后），sortsAs匹配器会忽略顺序差异
  } // 方法结束，此测试验证不同Join类型下谓词上拉的正确性，内连接可以上拉所有谓词，外连接只能上拉部分谓词

  @ParameterizedTest(name = "{0}") // 参数化测试注解，使用第一个参数作为测试名称
  @CsvFileSource(resources = "RelMdPredicatesTestLeftInferredFromJoin.csv", delimiter = ';') // 从CSV文件读取测试参数，文件包含不同Join类型和期望的左推断谓词
  void testLeftInferredPredicatesFromJoin(JoinRelType joinType, String expectedPredicates) { // 测试方法：验证从Join操作中推断左表谓词的功能，joinType为连接类型，expectedPredicates为期望的左推断谓词列表
    FrameworkConfig config = RelBuilderTest.config().build(); // 创建框架配置对象，使用默认配置
    RelBuilder b = RelBuilder.create(config); // 创建关系构建器RelBuilder实例
    RelNode rel = b // 开始构建关系表达式树
        .scan("EMP") // 扫描EMP表，创建第一个TableScan节点，作为Join的左输入
        .scan("DEPT") // 扫描DEPT表，创建第二个TableScan节点，作为Join的右输入
        .filter(b.greaterThan(b.field("DEPTNO"), b.literal(10))) // 添加Filter节点到DEPT表，过滤条件为DEPTNO > 10，这个谓词在右表上
        .join( // 创建Join节点，连接EMP表和过滤后的DEPT表
            joinType, b.equals( // 指定连接类型和连接条件
            b.field(2, 0, "DEPTNO"), // 引用左表（EMP）的DEPTNO字段
            b.field(2, 1, "DEPTNO"))) // 引用右表（DEPT过滤后）的DEPTNO字段，连接条件为EMP.DEPTNO = DEPT.DEPTNO
        .build(); // 完成关系表达式树的构建
    RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    RelOptPredicateList list = mq.getPulledUpPredicates(rel); // 获取谓词列表，包含上拉谓词、左推断谓词、右推断谓词等
    assertThat(list.leftInferredPredicates, sortsAs(expectedPredicates)); // 断言左推断谓词列表与期望值匹配，左推断谓词是指根据Join条件和右表谓词推导出的左表上的谓词
  } // 方法结束，此测试验证通过Join条件和右表谓词可以推断出左表上的哪些谓词，例如如果DEPT.DEPTNO > 10且EMP.DEPTNO = DEPT.DEPTNO，则可以推断EMP.DEPTNO > 10

  @ParameterizedTest(name = "{0}") // 参数化测试注解，使用第一个参数作为测试名称
  @CsvFileSource(resources = "RelMdPredicatesTestRightInferredFromJoin.csv", delimiter = ';') // 从CSV文件读取测试参数，文件包含不同Join类型和期望的右推断谓词
  void testRightInferredPredicatesFromJoin(JoinRelType joinType, String expectedPredicates) { // 测试方法：验证从Join操作中推断右表谓词的功能，joinType为连接类型，expectedPredicates为期望的右推断谓词列表
    FrameworkConfig config = RelBuilderTest.config().build(); // 创建框架配置对象，使用默认配置
    RelBuilder b = RelBuilder.create(config); // 创建关系构建器RelBuilder实例
    RelNode rel = b // 开始构建关系表达式树
        .scan("EMP") // 扫描EMP表，创建第一个TableScan节点，作为Join的左输入
        .filter(b.greaterThan(b.field("DEPTNO"), b.literal(10))) // 添加Filter节点到EMP表，过滤条件为DEPTNO > 10，这个谓词在左表上
        .scan("DEPT") // 扫描DEPT表，创建第二个TableScan节点，作为Join的右输入
        .join( // 创建Join节点，连接过滤后的EMP表和DEPT表
            joinType, b.equals( // 指定连接类型和连接条件
            b.field(2, 0, "DEPTNO"), // 引用左表（EMP过滤后）的DEPTNO字段
            b.field(2, 1, "DEPTNO"))) // 引用右表（DEPT）的DEPTNO字段，连接条件为EMP.DEPTNO = DEPT.DEPTNO
        .build(); // 完成关系表达式树的构建
    RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    RelOptPredicateList list = mq.getPulledUpPredicates(rel); // 获取谓词列表，包含上拉谓词、左推断谓词、右推断谓词等
    assertThat(list.rightInferredPredicates, sortsAs(expectedPredicates)); // 断言右推断谓词列表与期望值匹配，右推断谓词是指根据Join条件和左表谓词推导出的右表上的谓词
  } // 方法结束，此测试验证通过Join条件和左表谓词可以推断出右表上的哪些谓词，例如如果EMP.DEPTNO > 10且EMP.DEPTNO = DEPT.DEPTNO，则可以推断DEPT.DEPTNO > 10

  /** Test case for // Javadoc注释开始，说明此测试用例的目的
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6507">[CALCITE-6507] // 引用JIRA问题CALCITE-6507的链接
   * Random functions are incorrectly considered deterministic</a>. */ // 问题描述：随机函数错误地被认为是确定性的（即每次调用返回相同值）
  @Test void testRandomFunctionsAreNotConsideredConstant() { // 测试方法：验证随机函数不被认为是常量，确保谓词元数据正确识别非确定性函数
    FrameworkConfig config = RelBuilderTest.config().build(); // 创建框架配置对象，使用默认配置
    for (SqlOperator randomOp : Arrays.asList(SqlStdOperatorTable.RAND, // 遍历所有随机函数操作符，第一个是RAND（标准SQL的随机函数）
        SqlLibraryOperators.RANDOM, SqlStdOperatorTable.RAND_INTEGER)) { // 第二个是RANDOM（某些SQL库的随机函数），第三个是RAND_INTEGER（返回整数的随机函数）
      RelBuilder b = RelBuilder.create(config); // 为每个随机函数创建一个新的关系构建器实例
      RelNode rel = b // 开始构建关系表达式树
          .scan("EMP") // 扫描EMP表，创建TableScan节点
          .project(b.field(0), b.call(randomOp)) // 创建Project节点，输出两个字段：第一个是EMP表的第0个字段，第二个是调用随机函数的结果
          .sort(1) // 添加Sort节点，按第1个字段（即随机函数的结果）排序
          .build(); // 完成关系表达式树的构建
      RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
      RelOptPredicateList list = mq.getPulledUpPredicates(rel); // 获取谓词列表，包括常量映射
      assertTrue(list.constantMap.isEmpty(), // 断言常量映射为空，因为随机函数每次调用结果不同，不应该被识别为常量
          "Operator " + randomOp + " considered constant: " + list.constantMap); // 如果断言失败，显示错误信息，指出哪个随机函数被错误地认为是常量
    } // 循环结束，测试完所有随机函数
  } // 方法结束，此测试修复了CALCITE-6507问题，确保随机函数不会被优化器错误地当作常量处理

} // 类RelMdPredicatesTest结束，此类包含4个测试方法，全面测试了RelMdPredicates元数据提供者的功能
} // 文件结束，RelMdPredicatesTest.java文件结束
