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
 */ // Apache许可证头，声明该代码遵循Apache 2.0许可证
package org.apache.calcite.test; // 声明该类所属的包，位于org.apache.calcite.test包下

import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系表达式节点，是Calcite中关系代数的基本构建块
import org.apache.calcite.tools.RelBuilder; // 导入RelBuilder类，用于构建关系表达式树的工具类

import java.util.function.Function; // 导入Function函数式接口，用于表示接收RelBuilder并返回RelNode的函数

/**
 * RelOptTestBase is an abstract base for tests which exercise a planner and/or
 * rules via {@link DiffRepository}.
 */ // 类文档注释：RelOptTestBase是一个抽象基类，用于通过DiffRepository来测试优化器和规则的测试类
abstract class RelOptTestBase { // 声明RelOptTestBase为抽象类，不能直接实例化，需要子类继承实现
  //~ Methods ---------------------------------------------------------------- // 方法区域的分隔符，用于组织代码结构

  /** Creates a fixture for a test. Derived class must override and set
   * {@link RelOptFixture#diffRepos}. */ // 方法文档注释：创建测试用的fixture（测试夹具），派生类必须重写此方法并设置RelOptFixture#diffRepos
  RelOptFixture fixture() { // 声明fixture方法，返回RelOptFixture对象，用于提供测试环境和配置
    return RelOptFixture.DEFAULT; // 返回默认的RelOptFixture实例，DEFAULT是预定义的默认测试夹具
  } // 方法结束

  /**
   * Creates a test context with a SQL query.
   * Default catalog: {@link org.apache.calcite.test.catalog.MockCatalogReaderSimple#init()}.
   */ // 方法文档注释：创建一个包含SQL查询的测试上下文，默认使用MockCatalogReaderSimple#init()作为目录
  protected final RelOptFixture sql(String sql) { // 声明sql方法，接收SQL字符串参数，返回RelOptFixture对象，protected表示子类可访问，final表示不能被子类重写
    return fixture().sql(sql); // 调用fixture()方法获取RelOptFixture实例，然后调用其sql方法传入SQL字符串，返回配置好的测试上下文
  } // 方法结束

  /** Initiates a test case with a given {@link RelNode} supplier. */ // 方法文档注释：使用给定的RelNode供应器（supplier函数）启动一个测试用例
  protected final RelOptFixture relFn(Function<RelBuilder, RelNode> relFn) { // 声明relFn方法，接收一个函数参数，该函数接收RelBuilder并返回RelNode，返回RelOptFixture对象
    return fixture().relFn(relFn); // 调用fixture()方法获取RelOptFixture实例，然后调用其relFn方法传入关系节点构建函数，返回配置好的测试上下文
  } // 方法结束

} // 类定义结束
