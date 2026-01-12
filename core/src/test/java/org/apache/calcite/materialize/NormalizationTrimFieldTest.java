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
package org.apache.calcite.materialize; // 物化视图相关的包，包含物化视图的匹配、优化等功能

import org.apache.calcite.plan.RelOptMaterialization; // 物化视图定义类，封装物化视图的目标关系式和替换关系式
import org.apache.calcite.plan.RelOptMaterializations; // 物化视图工具类，提供使用物化视图进行查询优化的方法
import org.apache.calcite.plan.RelOptUtil; // 关系式工具类，提供关系式转换、字符串表示等工具方法
import org.apache.calcite.plan.RelTraitDef; // 关系式特征定义接口，定义关系式的特征（如约定、排序等）
import org.apache.calcite.rel.RelNode; // 关系式节点接口，代表关系代数表达式树的节点
import org.apache.calcite.rel.core.AggregateCall; // 聚合调用类，表示聚合函数的调用（如COUNT、SUM等）
import org.apache.calcite.rel.logical.LogicalAggregate; // 逻辑聚合节点，表示GROUP BY和聚合操作
import org.apache.calcite.rel.logical.LogicalProject; // 逻辑投影节点，表示SELECT子句中的字段投影和表达式计算
import org.apache.calcite.rel.type.RelDataType; // 关系式数据类型接口，表示关系式的行类型或字段类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 关系式数据类型工厂接口，用于创建各种数据类型
import org.apache.calcite.schema.SchemaPlus; // SchemaPlus类，代表一个可扩展的数据库模式
import org.apache.calcite.schema.impl.AbstractTable; // 抽象表类，用于定义自定义表的结构和行为
import org.apache.calcite.sql.parser.SqlParser; // SQL解析器类，用于将SQL字符串解析为抽象语法树
import org.apache.calcite.sql.type.SqlTypeName; // SQL类型名枚举，定义了所有SQL标准数据类型
import org.apache.calcite.test.CalciteAssert; // Calcite测试工具类，提供测试环境配置和断言方法
import org.apache.calcite.test.SqlToRelTestBase; // SQL到关系式转换测试基类，提供测试SQL转换的基础功能
import org.apache.calcite.tools.Frameworks; // Frameworks工具类，用于创建Calcite框架配置和构建器
import org.apache.calcite.tools.RelBuilder; // 关系式构建器接口，提供流式API构建关系式树
import org.apache.calcite.util.ImmutableBitSet; // 不可变位集合类，用于高效表示一组索引（如分组字段）
import org.apache.calcite.util.Pair; // Pair类，表示一个键值对，用于存储两个相关联的对象

import com.google.common.collect.ImmutableList; // Google Guava不可变列表类，提供线程安全的不可变列表实现
import com.google.common.collect.Lists; // Google Guava Lists工具类，提供列表创建和操作的工具方法

import org.junit.jupiter.api.Test; // JUnit 5测试注解，标记测试方法

import java.util.List; // Java标准List接口，表示有序集合

import static org.apache.calcite.test.Matchers.isLinux; // 静态导入isLinux匹配器，用于跨平台字符串比较

import static org.hamcrest.MatcherAssert.assertThat; // 静态导入assertThat方法，用于Hamcrest断言

/** Tests trimming unused fields before materialized view matching. */ // 测试在物化视图匹配前修剪未使用字段的功能
public class NormalizationTrimFieldTest extends SqlToRelTestBase { // 规范化修剪字段测试类，继承自SQL到关系式转换测试基类

  public static Frameworks.ConfigBuilder config() { // 创建并返回Calcite框架配置构建器，用于配置测试环境
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根Schema，参数true表示添加元数据Schema
    rootSchema.add("mv0", new AbstractTable() { // 向根Schema添加名为"mv0"的物化视图表，使用匿名内部类定义表结构
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写方法，定义表的行类型（字段结构）
        return typeFactory.builder() // 创建类型构建器，用于构建表的行类型
            .add("deptno", SqlTypeName.INTEGER) // 添加名为"deptno"的整型字段，表示部门编号
            .add("count_sal", SqlTypeName.BIGINT) // 添加名为"count_sal"的大整型字段，表示薪资计数
            .build(); // 构建并返回行类型
      }
    });
    return Frameworks.newConfigBuilder() // 创建新的框架配置构建器
        .parserConfig(SqlParser.Config.DEFAULT) // 设置SQL解析器配置为默认配置
        .defaultSchema( // 设置默认Schema
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.SCOTT_WITH_TEMPORAL)) // 向根Schema添加SCOTT测试Schema（包含EMP、DEPT等表和时态表）
        .traitDefs((List<RelTraitDef>) null); // 设置关系式特征定义为null，表示使用默认特征定义
  }

  @Test void testMVTrimUnusedFiled() { // 测试方法：测试物化视图在匹配前修剪未使用字段的功能
    final RelBuilder relBuilder = RelBuilder.create(config().build()); // 使用配置创建关系式构建器，用于构建查询关系式树
    final LogicalProject project = (LogicalProject) relBuilder.scan("EMP") // 扫描EMP表，并转换为逻辑投影节点
        .project(relBuilder.field("EMPNO"), // 投影EMPNO字段（员工编号）
            relBuilder.field("ENAME"), // 投影ENAME字段（员工姓名）
            relBuilder.field("JOB"), // 投影JOB字段（职位）
            relBuilder.field("SAL"), // 投影SAL字段（薪资）
            relBuilder.field("DEPTNO")).build(); // 投影DEPTNO字段（部门编号），构建投影节点
    final LogicalAggregate aggregate = (LogicalAggregate) relBuilder.push(project) // 将投影节点压入构建器栈，准备在其上构建聚合
        .aggregate( // 创建聚合操作
            relBuilder.groupKey(relBuilder.field(1, 0, "DEPTNO")), // 设置分组键为DEPTNO字段（第1个输入的第0个字段）
            relBuilder.count(relBuilder.field(1, 0, "SAL"))) // 添加COUNT聚合函数，统计SAL字段的数量
        .build(); // 构建聚合节点
    final ImmutableBitSet groupSet = ImmutableBitSet.of(4); // 创建分组集合，表示按索引4的字段（DEPTNO）分组
    final AggregateCall count = aggregate.getAggCallList().get(0); // 获取聚合节点的第一个聚合调用（COUNT调用）
    final AggregateCall call = // 创建新的聚合调用，修改参数引用以匹配修剪后的字段
        AggregateCall.create(count.getParserPosition(), count.getAggregation(), // 使用原调用的解析位置和聚合函数
            count.isDistinct(), count.isApproximate(), // 保持是否去重和是否近似的设置
            count.ignoreNulls(), count.rexList, ImmutableList.of(3), // 忽略null设置、表达式列表，将参数引用改为索引3（SAL字段在新投影中的位置）
            count.filterArg, null, count.collation, // 过滤参数、null值处理规则、排序规则保持不变
            count.getType(), count.getName()); // 保持返回类型和聚合调用名称
    final RelNode query = // 创建查询关系式，这是需要被物化视图替换的原始查询
        LogicalAggregate.create(project, aggregate.getHints(), // 在原投影节点上创建聚合，使用原聚合的提示信息
            groupSet, ImmutableList.of(groupSet), ImmutableList.of(call)); // 设置分组集合、分组集合列表和聚合调用列表
    final RelNode target = aggregate; // 目标关系式，表示物化视图定义的查询结构（聚合节点）
    final RelNode replacement = relBuilder.scan("mv0").build(); // 替换关系式，表示物化视图的实际存储（扫描mv0表）
    final RelOptMaterialization relOptMaterialization = // 创建物化视图定义，封装替换关系式、目标关系式等信息
        new RelOptMaterialization(replacement, // 替换关系式，即物化视图的实际存储结构
            target, null, Lists.newArrayList("mv0")); // 目标关系式、星型连接、物化视图名称列表
    final List<Pair<RelNode, List<RelOptMaterialization>>> relOptimized = // 使用物化视图优化查询，返回优化后的关系式和使用的物化视图列表
        RelOptMaterializations.useMaterializedViews(query, // 原始查询关系式
            ImmutableList.of(relOptMaterialization)); // 可用的物化视图列表

    final String optimized = "" // 定义期望的优化后关系式字符串表示
        + "LogicalProject(deptno=[CAST($0):TINYINT], count_sal=[$1])\n" // 逻辑投影节点，将mv0表的字段映射为deptno和count_sal
        + "  LogicalTableScan(table=[[mv0]])\n"; // 逻辑表扫描节点，扫描mv0物化视图表
    final String relOptimizedStr = RelOptUtil.toString(relOptimized.get(0).getKey()); // 将实际优化的关系式转换为字符串表示
    assertThat(relOptimizedStr, isLinux(optimized)); // 断言：验证实际的优化后的关系式与期望的关系式字符串匹配（使用跨平台比较）
  }
}
