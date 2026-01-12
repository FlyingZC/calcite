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
 */ // Apache许可证声明,说明代码开源协议和使用权限
package org.apache.calcite.adapter.enumerable; // 定义包名,该包包含可枚举适配器相关类

import org.apache.calcite.plan.Contexts; // 导入上下文工具类,用于创建空的上下文对象
import org.apache.calcite.plan.RelOptCluster; // 导入关系表达式优化集群类,管理关系表达式和Rex构建器
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合类,定义物理属性如排序、分布等
import org.apache.calcite.rel.AbstractRelNode; // 导入抽象关系节点类,用于创建测试用的关系节点
import org.apache.calcite.rel.RelNode; // 导入关系节点接口,所有关系表达式的基类
import org.apache.calcite.rel.core.Window; // 导入窗口操作关系节点类,用于SQL窗口函数
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口,描述列的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口,用于创建数据类型
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统接口,定义类型系统的行为
import org.apache.calcite.rel.type.RelDataTypeSystemImpl; // 导入关系数据类型系统实现类
import org.apache.calcite.rex.RexBuilder; // 导入行表达式构建器类,用于构建行表达式
import org.apache.calcite.rex.RexLiteral; // 导入行表达式字面量类,表示常量值
import org.apache.calcite.sql.type.BasicSqlType; // 导入基本SQL类型类,表示SQL基本数据类型
import org.apache.calcite.sql.type.SqlTypeFactoryImpl; // 导入SQL类型工厂实现类,用于创建SQL类型
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举,定义所有SQL类型名称
import org.apache.calcite.test.MockRelOptPlanner; // 导入模拟关系优化计划器类,用于测试

import org.junit.jupiter.api.Test; // 导入JUnit5测试注解,标记测试方法

import java.util.Collections; // 导入集合工具类,提供不可变集合的静态方法
import java.util.List; // 导入列表接口,表示有序集合

import static org.hamcrest.MatcherAssert.assertThat; // 静态导入断言方法,用于验证条件
import static org.hamcrest.Matchers.hasSize; // 静态导入集合大小匹配器,用于验证集合大小
import static org.junit.jupiter.api.Assertions.assertNotSame; // 静态导入断言方法,验证两个对象不是同一个实例
import static org.junit.jupiter.api.Assertions.assertSame; // 静态导入断言方法,验证两个对象是同一个实例

/**
 * Test for {@link org.apache.calcite.adapter.enumerable.EnumerableWindow}.
 */ // 类注释:这是EnumerableWindow类的测试类,用于测试可枚举窗口操作的功能
public class EnumerableWindowTest { // 定义测试类,测试EnumerableWindow的copyWithConstants方法
  @Test void testCopyWithConstants() { // 测试方法注解,标记为JUnit5测试方法;测试EnumerableWindow的copyWithConstants方法,验证常量复制功能
    final MockRelOptPlanner planner = new MockRelOptPlanner(Contexts.empty()); // 创建模拟关系优化计划器,传入空上下文,用于测试优化过程
    final RelDataTypeFactory typeFactory = // 声明关系数据类型工厂变量,用于创建SQL数据类型
        new SqlTypeFactoryImpl(org.apache.calcite.rel.type.RelDataTypeSystem.DEFAULT); // 创建SQL类型工厂实例,使用默认的关系数据类型系统
    final RelOptCluster cluster = RelOptCluster.create(planner, new RexBuilder(typeFactory)); // 创建关系优化集群,包含计划器和行表达式构建器,是关系操作的上下文环境
    final RelTraitSet traitSet = RelTraitSet.createEmpty(); // 创建空的关系特征集合,用于定义物理属性如排序、分区等
    final RelNode relNode = new AbstractRelNode(cluster, traitSet) { // 创建匿名抽象关系节点,作为测试用的输入关系节点
    }; // 匿名类结束,作为窗口操作的输入节点
    final RelDataTypeSystem dataTypeSystem = new RelDataTypeSystemImpl() { // 创建匿名关系数据类型系统实例,用于定义类型系统行为
    }; // 匿名类结束,自定义类型系统

    final RelDataType dataType = new BasicSqlType(dataTypeSystem, SqlTypeName.BOOLEAN); // 创建布尔类型的SQL数据类型,用于创建字面量常量
    final List<RexLiteral> constants = // 声明常量列表变量,用于存储窗口操作中使用的常量表达式
        Collections.singletonList( // 创建只包含一个元素的不可变列表
            RexLiteral.fromJdbcString(dataType, // 从JDBC字符串创建行表达式字面量,传入数据类型
                SqlTypeName.BOOLEAN, // 指定类型名称为布尔型
                "TRUE")); // 传入JDBC格式的字符串值"TRUE",创建布尔字面量常量
    final RelDataType rowDataType = new BasicSqlType(dataTypeSystem, SqlTypeName.ROW); // 创建行类型的数据类型,表示整个行的数据结构
    final List<Window.Group> groups = Collections.emptyList(); // 创建空的窗口分组列表,表示没有窗口分组

    final EnumerableWindow original = // 声明原始可枚举窗口对象变量
        new EnumerableWindow(cluster, traitSet, relNode, constants, rowDataType, groups); // 创建可枚举窗口实例,传入集群、特征集、输入节点、常量列表、行类型和分组列表
    final List<RexLiteral> newConstants = // 声明新的常量列表变量,用于测试copy方法
        Collections.singletonList( // 创建只包含一个元素的不可变列表
            RexLiteral.fromJdbcString(dataType, // 从JDBC字符串创建行表达式字面量,传入数据类型
                SqlTypeName.BOOLEAN, // 指定类型名称为布尔型
                "FALSE")); // 传入JDBC格式的字符串值"FALSE",创建布尔字面量常量,与原常量值不同
    final Window updated = original.copy(newConstants); // 调用copy方法创建新的窗口对象,传入新的常量列表,返回更新后的窗口节点

    assertNotSame(original, updated); // 断言验证:original和updated不是同一个对象实例,证明copy方法创建了新对象
    assertThat(original.getConstants(), hasSize(1)); // 断言验证:原始对象的常量列表大小为1,包含一个常量
    assertSame(constants.get(0), original.getConstants().get(0)); // 断言验证:原始对象的常量就是最初创建的常量对象,证明常量未被修改
    assertThat(updated.getConstants(), hasSize(1)); // 断言验证:更新后对象的常量列表大小为1,包含一个常量
    assertSame(newConstants.get(0), updated.getConstants().get(0)); // 断言验证:更新后对象的常量就是新传入的常量对象,证明copy方法正确替换了常量
  } // 测试方法结束
} // 类定义结束
