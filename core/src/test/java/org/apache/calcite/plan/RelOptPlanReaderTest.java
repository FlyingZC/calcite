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
// 声明包名：org.apache.calcite.plan，表示这个类属于Calcite框架的plan模块，负责关系代数优化器的相关功能
package org.apache.calcite.plan;

// 导入JdbcRules类，用于测试JDBC适配器相关的规则和关系节点类型
import org.apache.calcite.adapter.jdbc.JdbcRules;
// 导入AbstractRelNode，这是所有关系节点的抽象基类，用于创建自定义测试用的关系节点
import org.apache.calcite.rel.AbstractRelNode;
// 导入RelJson类，这是关系表达式与JSON格式之间进行序列化和反序列化的核心工具类
import org.apache.calcite.rel.externalize.RelJson;
// 导入LogicalProject，这是Calcite中逻辑投影关系节点，用于表示SQL中的SELECT子句
import org.apache.calcite.rel.logical.LogicalProject;

// 导入Jupiter测试框架的Test注解，用于标记测试方法
import org.junit.jupiter.api.Test;

// 导入Hamcrest断言库的静态方法，用于编写更易读的断言
// equalTo: 用于判断两个对象是否相等
import static org.hamcrest.CoreMatchers.equalTo;
// is: 用于包装其他匹配器，增强可读性
import static org.hamcrest.CoreMatchers.is;
// sameInstance: 用于判断两个对象引用是否指向同一个实例
import static org.hamcrest.CoreMatchers.sameInstance;
// assertThat: Hamcrest的核心断言方法，用于验证实际值是否符合预期
import static org.hamcrest.MatcherAssert.assertThat;
// fail: 用于在测试中主动标记测试失败，通常用于验证异常情况
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link org.apache.calcite.rel.externalize.RelJson}.
 * RelJson类的单元测试类，用于测试关系表达式与JSON之间的类型转换功能
 * 主要测试classToTypeName（将Java类转换为类型名称）和typeNameToClass（将类型名称转换为Java类）两个方法
 * 这两个方法是RelJson能够将关系表达式序列化为JSON以及从JSON反序列化的核心基础
 */
// RelOptPlanReaderTest类：用于测试RelJson的类型转换功能，确保关系表达式类名和类型名称之间的双向映射正确
class RelOptPlanReaderTest {
  // testTypeToClass方法：测试类型名称与Java类之间的双向转换功能，验证RelJson能够正确处理各种类名格式
  @Test void testTypeToClass() {
    // 创建RelJson实例，RelJson负责关系表达式的JSON序列化和反序列化，create()是工厂方法用于创建实例
    RelJson relJson = RelJson.create();

    // 测试1：验证org.apache.calcite.rel包中的LogicalProject类能够正确转换为类型名称
    // classToTypeName方法将Java的Class对象转换为RelJson使用的类型名称字符串
    assertThat(relJson.classToTypeName(LogicalProject.class),
        is("LogicalProject"));  // 断言转换结果为"LogicalProject"，对于标准包中的类使用简单类名

    // 测试2：验证类型名称能够正确转换回对应的Java类对象
    // typeNameToClass方法将类型名称字符串转换回Java的Class对象
    assertThat(relJson.typeNameToClass("LogicalProject"),
        sameInstance((Class) LogicalProject.class));  // 断言转换结果与LogicalProject.class是同一个实例

    // 测试3：验证JdbcRules外部类中的嵌套类JdbcProject能够正确转换为类型名称
    // JdbcRules.JdbcProject是JdbcRules的静态内部类，表示JDBC适配器的投影节点
    assertThat(relJson.classToTypeName(JdbcRules.JdbcProject.class),
        is("JdbcProject"));  // 断言转换结果为"JdbcProject"，对于嵌套类也使用简单类名

    // 测试4：验证JdbcProject的类型名称能够正确转换回对应的Java类对象
    assertThat(relJson.typeNameToClass("JdbcProject"),
        equalTo((Class) JdbcRules.JdbcProject.class));  // 断言转换结果与JdbcRules.JdbcProject.class相等

    // 测试5：验证当类型名称不存在时会抛出运行时异常
    try {
      // 尝试转换一个不存在的类型名称"NonExistentRel"
      Class clazz = relJson.typeNameToClass("NonExistentRel");
      // 如果没有抛出异常，则测试失败，因为这应该抛出异常
      fail("expected exception, got " + clazz);  // fail方法会立即让测试失败，并输出错误信息
    } catch (RuntimeException e) {
      // 捕获预期的运行时异常，验证异常消息是否正确
      assertThat(e.getMessage(), is("unknown type NonExistentRel"));  // 断言异常消息为"unknown type NonExistentRel"
    }
    // 测试6：验证当使用全限定类名但类型不存在时会抛出运行时异常
    try {
      // 尝试转换一个不存在的全限定类型名称
      Class clazz =
          relJson.typeNameToClass("org.apache.calcite.rel.NonExistentRel");
      // 如果没有抛出异常，则测试失败
      fail("expected exception, got " + clazz);  // fail方法会立即让测试失败
    } catch (RuntimeException e) {
      // 捕获预期的运行时异常，验证异常消息是否正确
      assertThat(e.getMessage(),
          is("unknown type org.apache.calcite.rel.NonExistentRel"));  // 断言异常消息包含完整类型名称
    }

    // 测试7：验证当前测试类中的内部类MyRel能够正确转换为类型名称
    // MyRel是本测试类中定义的内部类，用于测试非标准包中的类的处理
    // In this class; no special treatment. Note: '$MyRel' not '.MyRel'.
    assertThat(relJson.classToTypeName(MyRel.class),
        is("org.apache.calcite.plan.RelOptPlanReaderTest$MyRel"));  // 断言转换结果包含完整路径，内部类使用$分隔符

    // 测试8：验证MyRel的完整类名能够正确转换回对应的Java类对象
    assertThat(relJson.typeNameToClass(MyRel.class.getName()),
        equalTo((Class) MyRel.class));  // 断言转换结果与MyRel.class相等

    // 测试9：验证使用规范名称（canonical name，使用$分隔符）时无法找到类
    // Using canonical name (with '$'), not found
    try {
      // 尝试使用MyRel的规范名称（canonical name）进行转换
      // getCanonicalName()返回的是"org.apache.calcite.plan.RelOptPlanReaderTest.MyRel"
      Class clazz =
          relJson.typeNameToClass(MyRel.class.getCanonicalName());
      // 如果没有抛出异常，则测试失败，因为RelJson期望使用getName()返回的格式（使用$）
      fail("expected exception, got " + clazz);  // fail方法会立即让测试失败
    } catch (RuntimeException e) {
      // 捕获预期的运行时异常，验证异常消息是否正确
      assertThat(e.getMessage(),
          is(
              "unknown type org.apache.calcite.plan.RelOptPlanReaderTest.MyRel"));  // 断言异常消息表明无法找到该类型
    }
  }

  /** Dummy relational expression. */
  // MyRel类：一个简单的测试用关系节点类，继承自AbstractRelNode，用于测试RelJson对非标准位置类的处理
  // 这个类没有实际的业务逻辑，仅用于测试目的
  static class MyRel extends AbstractRelNode {
    // MyRel构造方法：初始化关系节点，接收RelOptCluster和RelTraitSet参数
    // RelOptCluster：关系节点集群，包含所有关系节点共享的对象和上下文信息
    // RelTraitSet：关系节点的特征集合，定义了节点的物理属性（如排序、分布等）
    MyRel(RelOptCluster cluster, RelTraitSet traitSet) {
      // 调用父类AbstractRelNode的构造方法，初始化关系节点的基本属性
      super(cluster, traitSet);  // 将集群和特征集传递给父类进行初始化
    }
  }
}
