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
 */ // Apache许可证头，声明代码版权和使用许可
package org.apache.calcite.test; // 声明包名为org.apache.calcite.test，表示这是Calcite测试包中的一个类

import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入可枚举适配器的规则类，用于定义可枚举转换规则
import org.apache.calcite.plan.RelOptPlanner; // 导入关系优化计划器接口，用于查询优化
import org.apache.calcite.runtime.Hook; // 导入Hook类，用于在运行时注入自定义行为
import org.apache.calcite.util.Sources; // 导入Sources工具类，用于处理数据源文件路径

import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map类，用于创建不可修改的映射

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.util.List; // 导入Java集合框架的List接口，用于处理列表数据
import java.util.function.Consumer; // 导入Java函数式接口Consumer，用于接受单个输入参数并执行操作

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器的is方法，用于断言值相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言工具类，用于编写断言

/**
 * Tests for the {@code org.apache.calcite.adapter.pig} package.
 */ // Javadoc注释：这是对org.apache.calcite.adapter.pig包的测试类
class PigAdapterTest extends AbstractPigTest { // 定义PigAdapterTest类，继承自AbstractPigTest基类，用于测试Pig适配器功能

  // Undo the %20 replacement of a space by URL // 注释说明：撤销URL中空格被替换为%20的处理
  public static final ImmutableMap<String, String> MODEL = // 定义公共静态常量MODEL，类型为不可变Map，键值对均为String类型，用于存储模型配置
      ImmutableMap.of("model", // 创建不可变Map，设置键为"model"
          Sources.of(PigAdapterTest.class.getResource("/model.json")) // 获取当前类所在路径下的model.json资源文件，并转换为Source对象
              .file().getAbsolutePath()); // 获取Source对象的文件对象，并返回绝对路径字符串，作为model键的值

  @Test void testScanAndFilter() { // 测试方法：测试表扫描和过滤操作，使用@Test注解标记为JUnit测试方法
    CalciteAssert.that() // 创建CalciteAssert构建器实例，用于构建测试断言链
        .with(MODEL) // 设置模型配置为前面定义的MODEL常量，包含model.json的路径
        .query("select * from \"t\" where \"tc0\" > 'abc'") // 执行SQL查询：从表t中选择所有字段，条件是tc0列的值大于'abc'
        .explainContains("PigToEnumerableConverter\n" // 验证执行计划包含PigToEnumerableConverter节点，表示将Pig关系转换为可枚举形式
            + "  PigFilter(condition=[>($0, 'abc')])\n" // 验证包含PigFilter过滤节点，过滤条件为第0列大于'abc'，$0表示第一列
            + "    PigTableScan(table=[[PIG, t]])") // 验证包含PigTableScan表扫描节点，扫描PIG命名空间下的t表
        .runs() // 执行查询并验证可以正常运行
        .queryContains( // 验证生成的查询包含预期的内容
            pigScriptChecker("t = LOAD '" // 调用pigScriptChecker方法验证生成的Pig Latin脚本，开始LOAD语句加载data.txt文件
                + getFullPathForTestDataFile("data.txt") // 获取测试数据文件data.txt的完整路径
                + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 使用PigStorage()函数加载数据，定义两个列tc0和tc1，类型为chararray（字符数组）
                + "t = FILTER t BY (tc0 > 'abc');")); // 使用FILTER语句过滤数据，保留tc0列大于'abc'的记录
  }

  @Test void testImplWithMultipleFilters() { // 测试方法：测试包含多个过滤条件的实现，验证AND逻辑运算符的正确转换
    CalciteAssert.that() // 创建CalciteAssert构建器实例
        .with(MODEL) // 设置模型配置
        .query("select * from \"t\" where \"tc0\" > 'abc' and \"tc1\" = '3'") // 执行SQL查询：从表t中选择所有字段，条件是tc0>'abc'且tc1='3'
        .explainContains("PigToEnumerableConverter\n" // 验证执行计划包含PigToEnumerableConverter节点
            + "  PigFilter(condition=[AND(>($0, 'abc'), =($1, '3'))])\n" // 验证包含PigFilter节点，条件为AND逻辑运算，两个子条件分别是$0>'abc'和$1='3'
            + "    PigTableScan(table=[[PIG, t]])") // 验证包含PigTableScan节点扫描t表
        .runs() // 执行查询验证可以正常运行
        .queryContains( // 验证生成的查询内容
            pigScriptChecker("t = LOAD '" // 验证Pig Latin脚本开始LOAD语句
                + getFullPathForTestDataFile("data.txt") // 获取data.txt完整路径
                + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 使用PigStorage加载并定义列结构
                + "t = FILTER t BY (tc0 > 'abc') AND (tc1 == '3');")); // 使用FILTER语句，条件为tc0>'abc'且tc1=='3'（注意Pig中相等用==）
  }

  @Test void testImplWithGroupByAndCount() { // 测试方法：测试GROUP BY分组和COUNT聚合函数的实现
    CalciteAssert.that() // 创建CalciteAssert构建器实例
        .with(MODEL) // 设置模型配置
        .query("select count(\"tc1\") c from \"t\" group by \"tc0\"") // 执行SQL查询：按tc0列分组，统计每组中tc1列的记录数，结果列别名为c
        .explainContains("PigToEnumerableConverter\n" // 验证执行计划包含PigToEnumerableConverter节点
            + "    PigAggregate(group=[{0}], C=[COUNT($1)])\n" // 验证包含PigAggregate聚合节点，按第0列分组，COUNT聚合第1列，结果别名为C
            + "      PigTableScan(table=[[PIG, t]])") // 验证包含PigTableScan节点扫描t表
        .runs() // 执行查询验证可以正常运行
        .queryContains( // 验证生成的查询内容
            pigScriptChecker("t = LOAD '" // 验证Pig Latin脚本开始LOAD语句
                + getFullPathForTestDataFile("data.txt") // 获取data.txt完整路径
                + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 使用PigStorage加载并定义列结构
                + "t = GROUP t BY (tc0);\n" // 使用GROUP语句按tc0列进行分组
                + "t = FOREACH t {\n" // 使用FOREACH语句对每个分组进行处理，开始代码块
                + "  GENERATE group AS tc0, COUNT(t.tc1) AS C;\n" // 生成两列：group（分组键）重命名为tc0，统计t.tc1的数量并命名为C
                + "};")); // 结束FOREACH代码块
  }

  @Test void testImplWithCountWithoutGroupBy() { // 测试方法：测试不带GROUP BY的COUNT聚合函数实现，即全表统计
    CalciteAssert.that() // 创建CalciteAssert构建器实例
        .with(MODEL) // 设置模型配置
        .query("select count(\"tc0\") c from \"t\"") // 执行SQL查询：统计表t中tc0列的总记录数，结果列别名为c
        .explainContains("PigToEnumerableConverter\n" // 验证执行计划包含PigToEnumerableConverter节点
            + "  PigAggregate(group=[{}], C=[COUNT($0)])\n" // 验证包含PigAggregate节点，group为空集合{}表示不分组（GROUP ALL），COUNT聚合第0列
            + "    PigTableScan(table=[[PIG, t]])") // 验证包含PigTableScan节点扫描t表
        .runs() // 执行查询验证可以正常运行
        .queryContains( // 验证生成的查询内容
            pigScriptChecker("t = LOAD '" // 验证Pig Latin脚本开始LOAD语句
                + getFullPathForTestDataFile("data.txt") // 获取data.txt完整路径
                + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 使用PigStorage加载并定义列结构
                + "t = GROUP t ALL;\n" // 使用GROUP ALL语句将所有记录分到一个组中，相当于全表聚合
                + "t = FOREACH t {\n" // 使用FOREACH语句处理这个单一分组
                + "  GENERATE COUNT(t.tc0) AS C;\n" // 生成一列：统计t.tc0的总数并命名为C
                + "};")); // 结束FOREACH代码块
  }

  @Test void testImplWithGroupByMultipleFields() { // 测试方法：测试按多个字段进行GROUP BY分组的实现
    CalciteAssert.that() // 创建CalciteAssert构建器实例
        .with(MODEL) // 设置模型配置
        .query("select * from \"t\" group by \"tc1\", \"tc0\"") // 执行SQL查询：从表t中选择所有字段，按tc1和tc0两列进行分组
        .explainContains("PigToEnumerableConverter\n" // 验证执行计划包含PigToEnumerableConverter节点
            + "  PigAggregate(group=[{0, 1}])\n" // 验证包含PigAggregate节点，按第0列和第1列分组，即按(tc0, tc1)组合分组
            + "    PigTableScan(table=[[PIG, t]])") // 验证包含PigTableScan节点扫描t表
        .runs() // 执行查询验证可以正常运行
        .queryContains( // 验证生成的查询内容
            pigScriptChecker("t = LOAD '" // 验证Pig Latin脚本开始LOAD语句
                + getFullPathForTestDataFile("data.txt") // 获取data.txt完整路径
                + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 使用PigStorage加载并定义列结构
                + "t = GROUP t BY (tc0, tc1);\n" // 使用GROUP语句按tc0和tc1两列的组合进行分组
                + "t = FOREACH t {\n" // 使用FOREACH语句处理每个分组
                + "  GENERATE group.tc0 AS tc0, group.tc1 AS tc1;\n" // 生成两列：从group元组中提取tc0和tc1字段
                + "};")); // 结束FOREACH代码块
  }

  @Test void testImplWithGroupByCountDistinct() { // 测试方法：测试GROUP BY配合COUNT DISTINCT去重计数的实现
    CalciteAssert.that() // 创建CalciteAssert构建器实例
        .with(MODEL) // 设置模型配置
        .query("select count(distinct \"tc0\") c from \"t\" group by \"tc1\"") // 执行SQL查询：按tc1列分组，统计每组中tc0列的不同值个数，结果列别名为c
        .explainContains("PigToEnumerableConverter\n" // 验证执行计划包含PigToEnumerableConverter节点
            + "    PigAggregate(group=[{1}], C=[COUNT(DISTINCT $0)])\n" // 验证包含PigAggregate节点，按第1列(tc1)分组，COUNT(DISTINCT $0)统计第0列(tc0)的不同值数量
            + "      PigTableScan(table=[[PIG, t]])") // 验证包含PigTableScan节点扫描t表
        .runs() // 执行查询验证可以正常运行
        .queryContains( // 验证生成的查询内容
            pigScriptChecker("t = LOAD '" // 验证Pig Latin脚本开始LOAD语句
                + getFullPathForTestDataFile("data.txt") // 获取data.txt完整路径
                + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 使用PigStorage加载并定义列结构
                + "t = GROUP t BY (tc1);\n" // 使用GROUP语句按tc1列进行分组
                + "t = FOREACH t {\n" // 使用FOREACH语句处理每个分组，开始代码块
                + "  tc0_DISTINCT = DISTINCT t.tc0;\n" // 使用DISTINCT操作去除t.tc0中的重复值，结果存储到tc0_DISTINCT
                + "  GENERATE group AS tc1, COUNT(tc0_DISTINCT) AS C;\n" // 生成两列：group（分组键）重命名为tc1，统计tc0_DISTINCT的数量并命名为C
                + "};")); // 结束FOREACH代码块
  }

  @Test void testImplWithJoin() { // 测试方法：测试JOIN连接操作的实现
    CalciteAssert.that() // 创建CalciteAssert构建器实例
        .with(MODEL) // 设置模型配置
        .query("select * from \"t\" join \"s\" on \"tc1\"=\"sc0\"") // 执行SQL查询：将表t和表s进行内连接，连接条件是t.tc1 = s.sc0
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 使用Hook机制在计划器中注入自定义行为
            planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE)) // 移除枚举合并连接规则，强制使用Pig的JOIN实现而不是枚举实现
        .explainContains("PigToEnumerableConverter\n" // 验证执行计划包含PigToEnumerableConverter节点
            + "  PigJoin(condition=[=($1, $2)], joinType=[inner])\n" // 验证包含PigJoin连接节点，连接条件为第1列($1)等于第2列($2)，连接类型为inner（内连接）
            + "    PigTableScan(table=[[PIG, t]])\n" // 验证包含第一个PigTableScan节点扫描t表
            + "    PigTableScan(table=[[PIG, s]])") // 验证包含第二个PigTableScan节点扫描s表
        .runs() // 执行查询验证可以正常运行
        .queryContains( // 验证生成的查询内容
            pigScriptChecker("t = LOAD '" // 验证Pig Latin脚本开始LOAD语句，加载表t的数据
                + getFullPathForTestDataFile("data.txt") // 获取data.txt完整路径作为t表的数据文件
                + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 使用PigStorage加载t表数据，定义tc0和tc1两列
                + "s = LOAD '" + getFullPathForTestDataFile("data2.txt") // 加载表s的数据，获取data2.txt完整路径
                + "' USING PigStorage() AS (sc0:chararray, sc1:chararray);\n" // 使用PigStorage加载s表数据，定义sc0和sc1两列
                + "t = JOIN t BY tc1 , s BY sc0;")); // 使用JOIN语句连接t和s表，连接条件是t.tc1 = s.sc0
  }

  /** Returns a function that checks that a particular Pig Latin scriptis
   * generated to implement a query. */ // Javadoc注释：返回一个函数，用于检查是否生成了特定的Pig Latin脚本来实现查询
  @SuppressWarnings("rawtypes") // 抑制编译器关于使用原始类型（raw type）的警告，这里List使用了原始类型
  private static Consumer<List> pigScriptChecker(final String... strings) { // 私有静态方法，返回一个Consumer<List>函数对象，用于验证生成的Pig脚本
    return actual -> { // 返回一个lambda表达式，实现了Consumer接口，接受一个List参数
      String actualArray = // 声明变量actualArray用于存储实际的Pig脚本字符串
          actual == null || actual.isEmpty() // 检查actual参数是否为null或空列表
              ? null // 如果actual为null或空，则actualArray设为null
              : (String) actual.get(0); // 否则，获取actual列表的第一个元素，并强制转换为String类型
      assertThat("expected Pig script not found", actualArray, is(strings[0])); // 使用Hamcrest断言验证actualArray是否等于预期的第一个字符串strings[0]，如果相等则测试通过，否则失败并显示"expected Pig script not found"错误信息
    }; // 结束lambda表达式
  }
} // 结束PigAdapterTest类定义
