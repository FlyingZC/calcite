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
package org.apache.calcite.rel.rules; // 声明包名，该类位于org.apache.calcite.rel.rules包下，用于测试规则相关的功能
import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入EnumerableConvention类，用于表示可枚举的调用约定，是Calcite中一种特殊的物理实现约定
import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入EnumerableRules类，包含所有可枚举相关的规则，如过滤、排序、限制等规则的集合
import org.apache.calcite.plan.ConventionTraitDef; // 导入ConventionTraitDef类，用于定义调用约定特征，决定关系表达式如何被实现和执行
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系节点的特征集合，包含排序、约定、分布等物理属性
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类，表示关系表达式的排序规则，定义字段如何排序
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef类，用于定义排序特征，描述关系表达式的排序属性
import org.apache.calcite.rel.RelFieldCollation; // 导入RelFieldCollation类，表示单个字段的排序规则，包括排序方向和空值处理方式
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数表达式的基础接口，所有关系节点都实现此接口
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，表示Calcite模式，可以包含表、函数等数据库对象
import org.apache.calcite.schemas.HrClusteredSchema; // 导入HrClusteredSchema类，表示人力资源聚簇模式，用于测试的示例模式
import org.apache.calcite.sql.parser.SqlParser; // 导入SqlParser类，用于解析SQL语句并将其转换为抽象语法树
import org.apache.calcite.tools.FrameworkConfig; // 导入FrameworkConfig类，表示Calcite框架的配置对象，包含解析器、模式、规则等设置
import org.apache.calcite.tools.Frameworks; // 导入Frameworks类，提供创建Calcite框架配置和构建器的工具方法
import org.apache.calcite.tools.Program; // 导入Program类，表示优化程序，用于对关系表达式应用规则进行优化转换
import org.apache.calcite.tools.Programs; // 导入Programs类，提供创建常用优化程序的工厂方法，如标准优化程序
import org.apache.calcite.tools.RelBuilder; // 导入RelBuilder类，用于构建关系表达式的构建器，提供流式API创建查询计划
import org.apache.calcite.tools.RuleSet; // 导入RuleSet类，表示规则集合，包含一组优化规则用于转换关系表达式
import org.apache.calcite.tools.RuleSets; // 导入RuleSets类，提供创建规则集合的工具方法

import com.google.common.collect.ImmutableList; // 导入Google Guava库的ImmutableList类，用于创建不可变列表

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.util.List; // 导入Java标准库的List接口，表示有序集合

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言库的is匹配器，用于验证值是否相等
import static org.hamcrest.CoreMatchers.notNullValue; // 导入Hamcrest断言库的notNullValue匹配器，用于验证值不为null
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言库的assertThat方法，用于执行断言验证
import static org.hamcrest.Matchers.hasSize; // 导入Hamcrest断言库的hasSize匹配器，用于验证集合的大小

/**
 * Tests the application of the {@code EnumerableLimitRule}. // 测试EnumerableLimitRule的应用，该规则用于将Limit节点转换为可枚举的Limit实现
 */
class EnumerableLimitRuleTest { // 测试类，用于测试EnumerableLimitRule规则的行为和正确性，确保Limit操作在可枚举约定下正确转换

  /** Test case for // 测试用例，用于验证特定bug修复
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2941">[CALCITE-2941] // JIRA问题链接，CALCITE-2941问题描述
   * EnumerableLimitRule on Sort with no collation creates EnumerableLimit with // 当EnumerableLimitRule应用于没有排序规则的Sort节点时，会创建具有错误traitSet和cluster的EnumerableLimit
   * wrong traitSet and cluster</a>. // 错误的特征集合和集群信息
   */
  @Test void enumerableLimitOnEmptySort() { // 测试方法，验证EnumerableLimitRule在空排序（无排序规则）情况下的正确性
    RuleSet prepareRules = // 创建规则集合，包含准备阶段需要应用的规则
        RuleSets.ofList(EnumerableRules.ENUMERABLE_FILTER_RULE, // 添加可枚举过滤规则，将Filter节点转换为可枚举实现
            EnumerableRules.ENUMERABLE_SORT_RULE, // 添加可枚举排序规则，将Sort节点转换为可枚举实现
            EnumerableRules.ENUMERABLE_LIMIT_RULE, // 添加可枚举限制规则，将Limit节点转换为可枚举实现
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE); // 添加可枚举表扫描规则，将TableScan节点转换为可枚举实现
    SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根模式对象，true表示添加默认的元数据提供器
    SchemaPlus defSchema = rootSchema.add("hr", new HrClusteredSchema()); // 在根模式下添加名为"hr"的子模式，使用HrClusteredSchema作为人力资源聚簇模式
    FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器，用于构建Calcite框架的配置
        .parserConfig(SqlParser.Config.DEFAULT) // 设置SQL解析器配置为默认配置
        .defaultSchema(defSchema) // 设置默认模式为之前创建的hr模式
        .traitDefs(ConventionTraitDef.INSTANCE, RelCollationTraitDef.INSTANCE) // 定义特征定义，包括调用约定和排序特征
        .programs(Programs.of(prepareRules)) // 设置优化程序，使用之前创建的规则集合
        .build(); // 构建框架配置对象

    RelBuilder builder = RelBuilder.create(config); // 使用配置创建关系表达式构建器，用于构建查询计划
    RelNode planBefore = builder // 开始构建关系表达式，这是优化前的原始计划
        .scan("hr", "emps") // 扫描hr模式下的emps表，创建TableScan节点
        .sort(builder.field(0)) // 按第0个字段排序，创建Sort节点，将在计划中产生排序规则[0]
        .filter( // 添加过滤条件，创建Filter节点
            builder.notEquals( // 创建不等于表达式
                builder.field(0), // 获取第0个字段的引用
                builder.literal(100))) // 创建字面量值100，表示过滤条件为field(0) != 100
        .limit(1, 5) // 添加限制操作，创建Limit节点，偏移量为1，获取5行，这会在一个"空"的Sort（无排序规则）中强制创建Limit
        .build(); // 构建完整的关系表达式树

    RelTraitSet desiredTraits = planBefore.getTraitSet() // 获取原始计划的特征集合
        .replace(EnumerableConvention.INSTANCE); // 将调用约定替换为可枚举约定，表示希望转换为可枚举实现
    Program program = Programs.of(prepareRules); // 创建优化程序，使用之前定义的规则集合
    RelNode planAfter = // 运行优化程序，得到优化后的计划
        program.run(planBefore.getCluster().getPlanner(), planBefore, // 使用原始计划的集群和规划器运行优化
            desiredTraits, ImmutableList.of(), ImmutableList.of()); // 应用期望的特征集合，空的输入和输出特征

    // verify that the collation [0] is not lost in the final plan // 验证排序规则[0]在最终计划中没有丢失
    final RelCollation collation = // 从最终计划中获取排序规则
        planAfter.getTraitSet().getTrait(RelCollationTraitDef.INSTANCE); // 从特征集合中获取排序特征
    assertThat(collation, notNullValue()); // 断言排序规则不为null，确保排序信息被保留
    final List<RelFieldCollation> fieldCollationList = // 获取字段排序列表
        collation.getFieldCollations(); // 从排序规则中提取所有字段的排序信息
    assertThat(fieldCollationList, notNullValue()); // 断言字段排序列表不为null
    assertThat(fieldCollationList, hasSize(1)); // 断言字段排序列表大小为1，表示只有一个字段参与排序
    assertThat(fieldCollationList.get(0).getFieldIndex(), is(0)); // 断言第一个排序字段的索引为0，验证排序规则[0]被正确保留
  }
}
