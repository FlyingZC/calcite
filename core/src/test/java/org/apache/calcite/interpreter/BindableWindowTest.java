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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.interpreter; // 声明包名，该类属于org.apache.calcite.interpreter包，用于Calcite的解释器模块

import org.apache.calcite.plan.Contexts; // 导入Contexts工具类，用于创建上下文对象
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式优化集群，用于管理关系表达式和相关资源
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系特征集合，定义关系表达式的物理和逻辑属性
import org.apache.calcite.rel.AbstractRelNode; // 导入AbstractRelNode类，抽象关系节点基类，所有关系节点的父类
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式节点，是Calcite中所有关系操作的基础接口
import org.apache.calcite.rel.core.Window; // 导入Window类，表示窗口操作关系节点，用于SQL中的窗口函数
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型，描述数据的类型信息
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，关系数据类型工厂，用于创建各种数据类型
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入RelDataTypeSystem接口，关系数据类型系统，定义类型系统的行为
import org.apache.calcite.rel.type.RelDataTypeSystemImpl; // 导入RelDataTypeSystemImpl类，关系数据类型系统的默认实现
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，行表达式构建器，用于构建各种行表达式
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，表示行表达式中的字面量常量
import org.apache.calcite.sql.type.BasicSqlType; // 导入BasicSqlType类，基本SQL类型，表示基础的SQL数据类型
import org.apache.calcite.sql.type.SqlTypeFactoryImpl; // 导入SqlTypeFactoryImpl类，SQL类型工厂的实现类
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，SQL类型名称枚举，定义所有SQL标准类型
import org.apache.calcite.test.MockRelOptPlanner; // 导入MockRelOptPlanner类，模拟的关系优化器，用于测试

import org.junit.jupiter.api.Test; // 导入Test注解，JUnit 5的测试注解，标记测试方法

import java.util.Collections; // 导入Collections工具类，提供集合操作的各种静态方法
import java.util.List; // 导入List接口，表示有序列表集合

import static org.hamcrest.MatcherAssert.assertThat; // 导入断言工具，使用Hamcrest匹配器进行断言
import static org.hamcrest.Matchers.hasSize; // 导入hasSize匹配器，验证集合的大小
import static org.junit.jupiter.api.Assertions.assertNotSame; // 导入断言方法，验证两个对象不是同一个实例
import static org.junit.jupiter.api.Assertions.assertSame; // 导入断言方法，验证两个对象是同一个实例

/**
 * Test for {@link org.apache.calcite.interpreter.Bindables.BindableWindow}.
 * // 测试类：用于测试BindableWindow类的功能
 * // BindableWindow是Calcite解释器中用于执行窗口操作的可绑定关系节点
 * // 该测试类主要验证BindableWindow的copy方法在处理常量时的正确性
 * // 确保当复制窗口节点并替换常量时，原始对象和复制对象是独立的
 */
public class BindableWindowTest { // 测试类定义，继承自Object，用于测试BindableWindow的功能
  @Test void testCopyWithConstants() { // 测试方法：测试BindableWindow的copy方法在处理常量时的行为
    final MockRelOptPlanner planner = new MockRelOptPlanner(Contexts.empty()); // 创建模拟的关系优化器，传入空上下文
    final RelDataTypeFactory typeFactory = // 创建SQL类型工厂实例，使用默认的关系数据类型系统
        new SqlTypeFactoryImpl(org.apache.calcite.rel.type.RelDataTypeSystem.DEFAULT); // 实例化SqlTypeFactoryImpl，传入默认类型系统
    final RelOptCluster cluster = RelOptCluster.create(planner, new RexBuilder(typeFactory)); // 创建关系优化集群，传入优化器和行表达式构建器
    final RelTraitSet traitSet = RelTraitSet.createEmpty(); // 创建空的关系特征集合，用于定义关系节点的属性
    final RelNode relNode = new AbstractRelNode(cluster, traitSet) { // 创建抽象关系节点实例，作为窗口操作的输入节点
    }; // 匿名内部类结束，实现了一个简单的AbstractRelNode
    final RelDataTypeSystem dataTypeSystem = new RelDataTypeSystemImpl() { // 创建自定义的关系数据类型系统实例
    }; // 匿名内部类结束，继承RelDataTypeSystemImpl

    final RelDataType dataType = new BasicSqlType(dataTypeSystem, SqlTypeName.BOOLEAN); // 创建布尔类型的数据类型对象
    final List<RexLiteral> constants = // 创建常量列表，包含一个布尔字面量常量
        Collections.singletonList( // 使用Collections工具类创建只包含一个元素的列表
            RexLiteral.fromJdbcString(dataType, // 从JDBC字符串创建字面量常量，传入数据类型
                SqlTypeName.BOOLEAN, // 指定类型为布尔类型
                "TRUE")); // 常量值为"TRUE"字符串
    final RelDataType rowDataType = new BasicSqlType(dataTypeSystem, SqlTypeName.ROW); // 创建行类型的数据类型对象，表示结果集的行类型
    final List<Window.Group> groups = Collections.emptyList(); // 创建空的窗口分组列表，表示没有窗口分组

    final Bindables.BindableWindow original = // 创建原始的BindableWindow实例
        new Bindables.BindableWindow(cluster, traitSet, relNode, constants, rowDataType, groups); // 传入集群、特征集、输入节点、常量列表、行类型和分组列表
    final List<RexLiteral> newConstants = // 创建新的常量列表，用于替换原始常量
        Collections.singletonList( // 使用Collections工具类创建只包含一个元素的列表
            RexLiteral.fromJdbcString(dataType, // 从JDBC字符串创建字面量常量，传入数据类型
                SqlTypeName.BOOLEAN, // 指定类型为布尔类型
                "FALSE")); // 常量值为"FALSE"字符串，与原始常量不同
    final Window updated = original.copy(newConstants); // 调用copy方法，传入新常量列表，返回更新后的Window节点

    assertNotSame(original, updated); // 断言：验证original和updated不是同一个对象实例，确保copy方法创建了新对象
    assertThat(original.getConstants(), hasSize(1)); // 断言：验证原始对象的常量列表大小为1，确保常量列表未被修改
    assertSame(constants.get(0), original.getConstants().get(0)); // 断言：验证原始对象的常量仍是原始常量对象，确保常量未变
    assertThat(updated.getConstants(), hasSize(1)); // 断言：验证更新对象的常量列表大小为1，确保常量列表正确设置
    assertSame(newConstants.get(0), updated.getConstants().get(0)); // 断言：验证更新对象的常量是新常量对象，确保常量正确替换
  } // 测试方法结束，验证了BindableWindow的copy方法在处理常量时的正确性和独立性
} // 类定义结束
