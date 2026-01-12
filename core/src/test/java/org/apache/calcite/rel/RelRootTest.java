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
package org.apache.calcite.rel; // 声明包名，该类属于org.apache.calcite.rel包，用于关系代数相关测试

import org.apache.calcite.rel.logical.LogicalProject; // 导入LogicalProject类，用于逻辑投影操作
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField接口，表示关系数据类型字段
import org.apache.calcite.rel.type.RelDataTypeFieldImpl; // 导入RelDataTypeFieldImpl实现类，关系数据类型字段的具体实现
import org.apache.calcite.rel.type.RelRecordType; // 导入RelRecordType类，表示关系记录类型
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，用于表示可扩展的Schema
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举，表示SQL操作的类型
import org.apache.calcite.test.CalciteAssert; // 导入CalciteAssert类，提供测试辅助工具
import org.apache.calcite.test.RelBuilderTest; // 导入RelBuilderTest类，提供RelBuilder测试配置
import org.apache.calcite.tools.FrameworkConfig; // 导入FrameworkConfig接口，用于Calcite框架配置
import org.apache.calcite.tools.Frameworks; // 导入Frameworks类，用于创建Calcite框架组件
import org.apache.calcite.tools.RelBuilder; // 导入RelBuilder类，用于构建关系表达式

import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法

import java.util.Collections; // 导入Collections工具类，提供集合操作方法
import java.util.List; // 导入List接口，表示列表集合

import static org.hamcrest.CoreMatchers.equalTo; // 导入equalTo匹配器，用于断言相等
import static org.hamcrest.CoreMatchers.instanceOf; // 导入instanceOf匹配器，用于断言类型
import static org.hamcrest.CoreMatchers.is; // 导入is匹配器，用于断言条件
import static org.hamcrest.CoreMatchers.not; // 导入not匹配器，用于断言否定条件
import static org.hamcrest.MatcherAssert.assertThat; // 导入assertThat方法，用于执行断言

/**
 * Tests for {@link RelRoot}. // RelRoot类的测试类
 * RelRoot是Calcite中关系表达式树的根节点，包含了查询的最终结果类型和SQL操作类型
 * 该测试类主要测试RelRoot的project()方法在不同场景下的行为
 * 特别关注当字段映射不是名称平凡映射时，是否正确生成LogicalProject节点
 */ // 类的详细说明：该类包含两个测试方法，分别测试RelRoot在字段名称不同和相同时的行为
public class RelRootTest { // 定义RelRootTest测试类
  /** Test case for // 测试用例说明：测试当字段映射不是名称平凡映射时，强制生成LogicalProject
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6877">[CALCITE-6877] // 关联JIRA问题编号CALCITE-6877
   * Generate LogicalProject in RelRoot.project() when mapping is not name trivial</a>. */ // 问题描述：在RelRoot.project()中当映射不是名称平凡映射时生成LogicalProject
  @Test void testRelRootProjectForceNonNameTrivial() { // 测试方法：测试RelRoot在字段名称不同时强制生成LogicalProject的行为
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根Schema，参数true表示添加内置函数
    final SchemaPlus defaultSchema = // 声明默认Schema变量
        CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.HR); // 向根Schema添加HR（人力资源）Schema
    final FrameworkConfig frameworkConfig = RelBuilderTest.config() // 创建框架配置，使用RelBuilderTest的默认配置
        .defaultSchema(defaultSchema) // 设置默认Schema为HR Schema
        .build(); // 构建框架配置对象
    final RelBuilder relBuilder = RelBuilder.create(frameworkConfig); // 使用框架配置创建RelBuilder实例
    final RelNode inputRel = relBuilder.scan("emps") // 扫描emps表创建关系节点
        .project(relBuilder.fields(Collections.singletonList("empid"))).build(); // 投影empid字段并构建关系节点

    final List<RelDataTypeField> fields = // 声明字段列表变量，用于定义RelRoot的输出字段
        Collections.singletonList( // 创建只包含一个元素的不可变列表
            // rename empid to empno via RelRoot // 注释：通过RelRoot将empid重命名为empno
            new RelDataTypeFieldImpl("empno", // 创建字段实现，字段名为empno
                inputRel.getRowType().getFieldList().get(0).getIndex(), // 获取输入关系第一个字段的索引
                inputRel.getRowType().getFieldList().get(0).getType())); // 获取输入关系第一个字段的类型

    final RelRoot root = RelRoot.of(inputRel, new RelRecordType(fields), SqlKind.SELECT); // 创建RelRoot实例，指定输入关系、输出记录类型和SQL操作类型

    // inner LogicalProject selects one field and RelRoot only has one field // 注释：内部LogicalProject选择一个字段且RelRoot只有一个字段
    assertThat(root.isRefTrivial(), is(true)); // 断言：引用映射是平凡的（即字段引用不需要转换）

    // inner LogicalProject has different field name than RelRoot // 注释：内部LogicalProject的字段名与RelRoot的字段名不同
    assertThat(root.isNameTrivial(), is(false)); // 断言：名称映射不是平凡的（即字段名称需要重命名）

    final RelNode project = root.project(); // 调用project()方法生成投影节点（不强制）
    assertThat(project, equalTo(inputRel)); // 断言：返回的投影节点等于输入关系节点（因为名称映射不平凡但引用映射平凡）

    // regular project() and force project() are different // 注释：常规project()和强制project()的结果不同
    final RelNode forceProject = root.project(true); // 调用project(true)方法强制生成投影节点
    assertThat(forceProject, not(equalTo(project))); // 断言：强制投影节点不等于常规投影节点

    // new LogicalProject on top of inputRel // 注释：在输入关系节点之上创建新的LogicalProject
    assertThat(forceProject, instanceOf(LogicalProject.class)); // 断言：强制投影节点是LogicalProject类型
    assertThat(forceProject.getInput(0), equalTo(inputRel)); // 断言：强制投影节点的输入等于输入关系节点

    // new LogicalProject renames field // 注释：新的LogicalProject重命名字段
    if (forceProject instanceof LogicalProject) { // 检查强制投影节点是否为LogicalProject类型
      assertThat(((LogicalProject) forceProject).getNamedProjects().get(0).getValue(), // 获取第一个命名项目的值
          equalTo("empno")); // 断言：字段值等于"empno"（验证字段重命名成功）
    }
  }

  /** Test case for // 测试用例说明：测试当字段映射是名称平凡映射时，不生成额外的LogicalProject
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6877">[CALCITE-6877] // 关联JIRA问题编号CALCITE-6877
   * Generate LogicalProject in RelRoot.project() when mapping is not name trivial</a>. */ // 问题描述：在RelRoot.project()中当映射不是名称平凡映射时生成LogicalProject
  @Test void testRelRootProjectForceNameTrivial() { // 测试方法：测试RelRoot在字段名称相同时的行为
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根Schema，参数true表示添加内置函数
    final SchemaPlus defaultSchema = // 声明默认Schema变量
        CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.HR); // 向根Schema添加HR（人力资源）Schema
    final FrameworkConfig frameworkConfig = RelBuilderTest.config() // 创建框架配置，使用RelBuilderTest的默认配置
        .defaultSchema(defaultSchema) // 设置默认Schema为HR Schema
        .build(); // 构建框架配置对象
    final RelBuilder relBuilder = RelBuilder.create(frameworkConfig); // 使用框架配置创建RelBuilder实例
    final RelNode inputRel = relBuilder.scan("emps") // 扫描emps表创建关系节点
        .project(relBuilder.fields(Collections.singletonList("empid"))).build(); // 投影empid字段并构建关系节点

    final RelRoot root = RelRoot.of(inputRel, SqlKind.SELECT); // 创建RelRoot实例，使用输入关系的行类型，指定SQL操作类型

    // inner LogicalProject selects one field and RelRoot only has one field // 注释：内部LogicalProject选择一个字段且RelRoot只有一个字段
    assertThat(root.isRefTrivial(), is(true)); // 断言：引用映射是平凡的（即字段引用不需要转换）

    // inner LogicalProject has same field name as RelRoot // 注释：内部LogicalProject的字段名与RelRoot的字段名相同
    assertThat(root.isNameTrivial(), is(true)); // 断言：名称映射是平凡的（即字段名称不需要重命名）

    final RelNode project = root.project(); // 调用project()方法生成投影节点（不强制）
    assertThat(project, equalTo(inputRel)); // 断言：返回的投影节点等于输入关系节点（因为映射都是平凡的）

    // regular project() and force project() are the same // 注释：常规project()和强制project()的结果相同
    final RelNode forceProject = root.project(true); // 调用project(true)方法强制生成投影节点
    assertThat(forceProject, equalTo(project)); // 断言：强制投影节点等于常规投影节点（因为映射都是平凡的，无需生成新的LogicalProject）
  }
}