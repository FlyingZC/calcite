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
package org.apache.calcite.plan.volcano; // 包声明：RelSetTest类位于org.apache.calcite.plan.volcano包下，该包包含Volcano优化器的核心组件

import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil工具类，提供关系表达式优化相关的实用方法
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式（如TableScan、Project、Join等）
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，表示Calcite中的数据库模式，可以包含表、函数等
import org.apache.calcite.sql.parser.SqlParser; // 导入SqlParser类，用于解析SQL语句并转换为抽象语法树（AST）
import org.apache.calcite.test.CalciteAssert; // 导入CalciteAssert工具类，提供测试用的辅助方法和预定义的测试数据
import org.apache.calcite.tools.FrameworkConfig; // 导入FrameworkConfig接口，表示Calcite框架的配置信息
import org.apache.calcite.tools.Frameworks; // 导入Frameworks工具类，用于创建和配置Calcite框架
import org.apache.calcite.tools.RelBuilder; // 导入RelBuilder类，用于以编程方式构建关系表达式树
import org.apache.calcite.util.Util; // 导入Util工具类，提供集合操作、空值检查等通用工具方法

import org.junit.jupiter.api.Test; // 导入Test注解，标记测试方法，JUnit5使用

import static org.junit.jupiter.api.Assertions.assertThrows; // 导入静态方法assertThrows，用于验证代码是否抛出预期异常

/**
 * Unit test for {@link RelSet}. // 类文档注释：RelSet的单元测试类，用于测试RelSet类的功能
 * RelSet是Volcano优化器中的一个重要概念，表示一组等价的RelNode集合
 * 等价的RelNode是指产生相同结果集但实现方式不同的关系表达式
 * 在优化过程中，Volcano优化器会将逻辑上等价的物理实现归入同一个RelSet
 * 然后基于代价模型从中选择最优的实现方案
 * 本测试类主要验证RelSet在添加RelNode时对行类型（RelDataType）的一致性检查
 */ 
public class RelSetTest { // 类定义：RelSetTest，用于测试RelSet类的行为

  /**
   * Tests for adding RelNode with same RelDataType. // 方法文档注释：测试添加具有相同行类型（RelDataType）的RelNode到RelSet中
   * 该测试验证RelSet能够正确接受行类型相同的RelNode
   * 在Volcano优化器中，只有行类型相同的RelNode才能被归入同一个RelSet
   * 因为它们在逻辑上是等价的，可以相互替换
   * 测试场景：创建两个Project操作，分别选择同一张表中的字段"a"和字段"e"，这两个字段类型相同，应该都能成功添加到RelSet
   */
  @Test void testAddRelNodeWithSameRowType() { // 测试方法：验证RelSet能够接受行类型相同的RelNode
    RelBuilder builder = createRelBuilder(); // 创建RelBuilder实例，用于构建关系表达式树
    RelNode relNodeA = // 声明RelNode变量relNodeA，表示第一个关系表达式节点
        builder.scan("myTable").project(builder.field("a")).build(); // 构建关系表达式：扫描表myTable并投影出字段"a"，生成RelNode
    RelNode relNodeE = // 声明RelNode变量relNodeE，表示第二个关系表达式节点
        builder.scan("myTable").project(builder.field("e")).build(); // 构建关系表达式：扫描表myTable并投影出字段"e"，生成RelNode
    RelSet relSet = // 声明RelSet变量relSet，表示一个关系表达式集合
        new RelSet(1, // 创建RelSet实例，第一个参数是集合的ID，设为1
            Util.minus(RelOptUtil.getVariablesSet(relNodeA), // 计算外部变量集合：从relNodeA涉及的所有变量中减去relNodeA自身定义的变量
                relNodeA.getVariablesSet()), // 得到relNodeA使用的外部变量（即来自输入的变量）
            RelOptUtil.getVariablesUsed(relNodeA)); // 获取relNodeA使用的所有变量（包括内部和外部变量）
    relSet.add(relNodeA); // 将relNodeA添加到relSet中，这是第一个添加的节点
    relSet.add(relNodeE); // 将relNodeE添加到relSet中，由于relNodeE和relNodeA的行类型相同，此操作应该成功
  }

  /**
   * Tests for adding RelNode with different RelDataType. // 方法文档注释：测试添加具有不同行类型（RelDataType）的RelNode到RelSet中
   * 该测试验证RelSet会拒绝行类型不同的RelNode
   * 在Volcano优化器中，行类型不同的RelNode不能归入同一个RelSet
   * 因为它们在逻辑上不等价，不能相互替换
   * 测试场景：创建两个Project操作，分别选择同一张表中的字段"a"和字段"n1"，这两个字段类型不同
   * 第一个节点应该能成功添加，第二个节点应该抛出AssertionError异常
   */
  @Test void testAddRelNodeWithDifferentRowType() { // 测试方法：验证RelSet会拒绝行类型不同的RelNode
    RelBuilder builder = createRelBuilder(); // 创建RelBuilder实例，用于构建关系表达式树
    RelNode relNodeA = // 声明RelNode变量relNodeA，表示第一个关系表达式节点
        builder.scan("myTable").project(builder.field("a")).build(); // 构建关系表达式：扫描表myTable并投影出字段"a"，生成RelNode
    RelNode relNodeN = // 声明RelNode变量relNodeN，表示第二个关系表达式节点（行类型与relNodeA不同）
        builder.scan("myTable").project(builder.field("n1")).build(); // 构建关系表达式：扫描表myTable并投影出字段"n1"，生成RelNode
    RelSet relSet = // 声明RelSet变量relSet，表示一个关系表达式集合
        new RelSet(1, // 创建RelSet实例，第一个参数是集合的ID，设为1
            Util.minus(RelOptUtil.getVariablesSet(relNodeA), // 计算外部变量集合：从relNodeA涉及的所有变量中减去relNodeA自身定义的变量
                relNodeA.getVariablesSet()), // 得到relNodeA使用的外部变量（即来自输入的变量）
            RelOptUtil.getVariablesUsed(relNodeA)); // 获取relNodeA使用的所有变量（包括内部和外部变量）
    relSet.add(relNodeA); // 将relNodeA添加到relSet中，这是第一个添加的节点，应该成功
    assertThrows(AssertionError.class, () -> relSet.add(relNodeN)); // 断言：调用relSet.add(relNodeN)会抛出AssertionError异常，因为relNodeN的行类型与relNodeA不同
  }

  private RelBuilder createRelBuilder() { // 私有方法：创建并配置RelBuilder实例，用于构建测试用的关系表达式
    SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根Schema，参数true表示添加内置函数和类型
    SchemaPlus defaultSchema = CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.MY_DB); // 添加MY_DB测试Schema到根Schema，获取默认Schema
    FrameworkConfig config = Frameworks.newConfigBuilder() // 创建FrameworkConfig构建器，用于配置Calcite框架
        .parserConfig(SqlParser.Config.DEFAULT) // 设置SQL解析器配置为默认配置
        .defaultSchema(defaultSchema) // 设置默认Schema为上面创建的defaultSchema
        .build(); // 构建FrameworkConfig配置对象
    return RelBuilder.create(config); // 使用配置创建RelBuilder实例并返回
  }
} // 类定义结束
