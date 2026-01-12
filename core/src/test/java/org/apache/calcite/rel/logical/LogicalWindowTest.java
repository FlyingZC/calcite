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
package org.apache.calcite.rel.logical; // 声明包名，这个类属于 org.apache.calcite.rel.logical 包

import org.apache.calcite.plan.Contexts; // 导入 Contexts 类，用于创建空的上下文对象
import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster 类，表示关系表达式优化集群，是关系代数树的根节点
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet 类，表示关系表达式的特征集合，如物理特性等
import org.apache.calcite.rel.AbstractRelNode; // 导入 AbstractRelNode 类，抽象关系节点，用于创建测试用的关系节点
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，表示关系表达式树中的一个节点
import org.apache.calcite.rel.core.Window; // 导入 Window 类，表示窗口操作的关系节点
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入 RelDataTypeFactory 接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入 RelDataTypeSystem 接口，定义数据类型系统的行为
import org.apache.calcite.rel.type.RelDataTypeSystemImpl; // 导入 RelDataTypeSystemImpl 类，实现默认的数据类型系统
import org.apache.calcite.rex.RexBuilder; // 导入 RexBuilder 类，用于构建行表达式（RexNode）
import org.apache.calcite.rex.RexLiteral; // 导入 RexLiteral 类，表示常量行表达式
import org.apache.calcite.sql.type.BasicSqlType; // 导入 BasicSqlType 类，表示基本的 SQL 数据类型
import org.apache.calcite.sql.type.SqlTypeFactoryImpl; // 导入 SqlTypeFactoryImpl 类，实现 SQL 类型工厂
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SqlTypeName 枚举，定义 SQL 类型名称
import org.apache.calcite.test.MockRelOptPlanner; // 导入 MockRelOptPlanner 类，用于测试的模拟优化器

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import java.util.Collections; // 导入 Collections 工具类，用于创建不可修改的集合
import java.util.List; // 导入 List 接口，表示列表集合

import static org.apache.calcite.rel.core.Window.Group; // 静态导入 Window.Group 类，表示窗口分组

import static org.hamcrest.MatcherAssert.assertThat; // 静态导入 assertThat 方法，用于断言
import static org.hamcrest.Matchers.hasSize; // 静态导入 hasSize 匹配器，用于验证集合大小
import static org.junit.jupiter.api.Assertions.assertNotSame; // 静态导入 assertNotSame 方法，用于验证两个对象不相同
import static org.junit.jupiter.api.Assertions.assertSame; // 静态导入 assertSame 方法，用于验证两个对象相同

/**
 * Test for {@link org.apache.calcite.rel.logical.LogicalWindow}. // LogicalWindow 的测试类，用于测试逻辑窗口操作的功能
 */
public class LogicalWindowTest { // 定义 LogicalWindowTest 测试类，用于测试 LogicalWindow 的各种行为
  @Test void testCopyWithConstants() { // 测试方法：测试 LogicalWindow 的 copy 方法，验证在复制时使用新的常量列表是否正确工作
    final MockRelOptPlanner planner = new MockRelOptPlanner(Contexts.empty()); // 创建一个模拟的优化器，传入空的上下文对象
    final RelDataTypeFactory typeFactory = // 创建 SQL 类型工厂实例，使用默认的数据类型系统
        new SqlTypeFactoryImpl(org.apache.calcite.rel.type.RelDataTypeSystem.DEFAULT); // 实例化 SqlTypeFactoryImpl，使用默认的 RelDataTypeSystem
    final RelOptCluster cluster = RelOptCluster.create(planner, new RexBuilder(typeFactory)); // 创建关系优化集群，传入优化器和 RexBuilder（用于构建行表达式）
    final RelTraitSet traitSet = RelTraitSet.createEmpty(); // 创建空的特征集合，用于定义关系节点的物理特性
    final RelNode relNode = new AbstractRelNode(cluster, traitSet) { // 创建一个抽象关系节点作为输入，使用匿名内部类
    }; // 匿名内部类结束，这个节点将作为 LogicalWindow 的子节点
    final RelDataTypeSystem dataTypeSystem = new RelDataTypeSystemImpl() { // 创建数据类型系统实例，使用匿名内部类
    }; // 匿名内部类结束，提供数据类型系统的默认实现

    final RelDataType dataType = new BasicSqlType(dataTypeSystem, SqlTypeName.BOOLEAN); // 创建布尔类型的数据类型对象
    final List<RexLiteral> constants = // 创建常量列表，用于存储窗口操作中的常量表达式
        Collections.singletonList( // 创建只包含一个元素的不可变列表
            RexLiteral.fromJdbcString(dataType, // 从 JDBC 字符串创建 RexLiteral 常量
                SqlTypeName.BOOLEAN, // 指定类型为布尔类型
                "TRUE")); // 常量值为字符串 "TRUE"
    final RelDataType rowDataType = new BasicSqlType(dataTypeSystem, SqlTypeName.ROW); // 创建行类型的数据类型对象，表示输出行的类型
    final List<Group> groups = Collections.emptyList(); // 创建空的窗口分组列表，表示没有分组窗口

    final LogicalWindow original = // 创建原始的 LogicalWindow 实例
        new LogicalWindow(cluster, traitSet, relNode, constants, rowDataType, groups); // 实例化 LogicalWindow，传入集群、特征集、输入节点、常量列表、行类型和分组列表
    final List<RexLiteral> newConstants = // 创建新的常量列表，用于测试 copy 方法
        Collections.singletonList( // 创建只包含一个元素的不可变列表
            RexLiteral.fromJdbcString(dataType, // 从 JDBC 字符串创建新的 RexLiteral 常量
                SqlTypeName.BOOLEAN, // 指定类型为布尔类型
                "FALSE")); // 常量值为字符串 "FALSE"，与原始常量不同
    final Window updated = original.copy(newConstants); // 调用 copy 方法，使用新的常量列表创建新的 LogicalWindow 实例

    assertNotSame(original, updated); // 断言：验证 original 和 updated 不是同一个对象，证明 copy 方法创建了新实例
    assertThat(original.getConstants(), hasSize(1)); // 断言：验证原始对象的常量列表大小为 1
    assertSame(constants.get(0), original.getConstants().get(0)); // 断言：验证原始对象的常量是原始创建的那个常量对象
    assertThat(updated.getConstants(), hasSize(1)); // 断言：验证更新后对象的常量列表大小为 1
    assertSame(newConstants.get(0), updated.getConstants().get(0)); // 断言：验证更新后对象的常量是新创建的那个常量对象
  } // 方法结束
}
