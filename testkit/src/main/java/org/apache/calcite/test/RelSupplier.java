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
// Apache许可证声明，说明此代码遵循Apache 2.0许可证
package org.apache.calcite.test; // 包声明，定义此接口属于org.apache.calcite.test包

import org.apache.calcite.plan.RelTraitDef; // 导入RelTraitDef类，用于定义关系表达式的特征（如排序、分布等）
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数表达式树中的节点
import org.apache.calcite.sql.parser.SqlParser; // 导入SqlParser类，用于SQL解析
import org.apache.calcite.tools.FrameworkConfig; // 导入FrameworkConfig类，用于配置Calcite框架
import org.apache.calcite.tools.Frameworks; // 导入Frameworks工具类，用于创建框架配置
import org.apache.calcite.tools.Programs; // 导入Programs工具类，用于定义优化程序
import org.apache.calcite.tools.RelBuilder; // 导入RelBuilder类，用于以编程方式构建关系表达式树

import java.util.List; // 导入List接口，用于存储特征定义列表
import java.util.function.Function; // 导入Function函数式接口，用于定义转换函数

/**
 * The source of a {@link RelNode} for running a test.
 */
// 这是一个函数式接口，用于为测试提供RelNode（关系表达式节点）
// RelSupplier是RelNode的提供者，可以理解为RelNode的工厂或生成器
// 它定义了两种不同的方式来创建RelNode：通过SQL字符串或通过RelBuilder函数
interface RelSupplier { // 定义RelSupplier接口，这是一个函数式接口，用于提供RelNode对象
  RelNode apply(RelOptFixture fixture); // apply方法：根据RelOptFixture测试夹具创建RelNode，主要用于优化器测试
  RelNode apply2(RelMetadataFixture metadataFixture); // apply2方法：根据RelMetadataFixture元数据测试夹具创建RelNode，主要用于元数据测试


  RelSupplier NONE = new RelSupplier() { // 定义一个特殊的RelSupplier实例NONE，表示空的RelNode提供者，总是抛出异常
    @Override public RelNode apply(RelOptFixture fixture) { // 重写apply方法，不提供任何RelNode
      throw new UnsupportedOperationException(); // 抛出不支持操作异常，表示此方法不被支持
    }

    @Override public RelNode apply2(RelMetadataFixture metadataFixture) { // 重写apply2方法，不提供任何RelNode
      throw new UnsupportedOperationException(); // 抛出不支持操作异常，表示此方法不被支持
    }
  };

  static RelSupplier of(String sql) { // 静态工厂方法：根据SQL字符串创建一个SqlRelSupplier实例
    if (sql.contains(" \n")) { // 检查SQL字符串是否包含行尾的空格（即空格后跟换行符）
      throw new AssertionError("trailing whitespace"); // 如果发现尾随空格，抛出断言错误，因为尾随空格可能导致测试不一致
    }
    return new SqlRelSupplier(sql); // 创建并返回一个SqlRelSupplier实例，封装了SQL字符串
  }

  /**
   * RelBuilder config based on the "scott" schema.
   */
  // FrameworkConfig：RelBuilder的配置对象，基于"scott"测试模式构建
  // "scott"模式是Calcite测试中常用的测试数据库模式，包含EMP、DEPT等经典测试表
  FrameworkConfig FRAMEWORK_CONFIG = // 定义静态的FrameworkConfig配置，所有FnRelSupplier实例共享此配置
      Frameworks.newConfigBuilder() // 创建一个新的框架配置构建器
          .parserConfig(SqlParser.Config.DEFAULT) // 设置SQL解析器配置为默认配置
          .defaultSchema( // 设置默认的Schema（数据库模式）
              CalciteAssert.addSchema( // 添加Schema到根Schema
                  Frameworks.createRootSchema(true), // 创建一个根Schema，参数true表示启用缓存
                  CalciteAssert.SchemaSpec.SCOTT_WITH_TEMPORAL)) // 使用包含时间类型字段的scott测试模式
          .traitDefs((List<RelTraitDef>) null) // 设置特征定义列表为null，表示使用默认的特征定义
          .programs(Programs.heuristicJoinOrder(Programs.RULE_SET, true, 2)) // 设置优化程序，使用启发式连接顺序优化，规则集为标准规则集，启用强制枚举，最大枚举数为2
          .build(); // 构建FrameworkConfig配置对象

  static RelSupplier of(Function<RelBuilder, RelNode> relFn) { // 静态工厂方法：根据RelBuilder函数创建一个FnRelSupplier实例
    return new FnRelSupplier(relFn); // 创建并返回一个FnRelSupplier实例，封装了RelBuilder函数
  }

  /** Creates a RelNode by parsing SQL. */
  // SqlRelSupplier是RelSupplier的实现类，通过解析SQL字符串来创建RelNode
  // 这个类允许测试用例直接使用SQL语句来测试查询优化和元数据功能
  class SqlRelSupplier implements RelSupplier { // 实现RelSupplier接口，通过SQL解析创建RelNode
    private final String sql; // 成员变量：存储SQL字符串，用于创建RelNode，final表示初始化后不可修改

    private SqlRelSupplier(String sql) { // 私有构造方法：接收SQL字符串并初始化成员变量
      this.sql = sql; // 将传入的SQL字符串赋值给成员变量sql
    }

    @Override public String toString() { // 重写toString方法，返回SQL字符串的字符串表示
      return sql; // 返回存储的SQL字符串
    }

    @Override public boolean equals(Object o) { // 重写equals方法，用于比较两个SqlRelSupplier对象是否相等
      return o == this // 如果引用相同，返回true
          || o instanceof SqlRelSupplier // 或者如果o是SqlRelSupplier的实例
          && ((SqlRelSupplier) o).sql.equals(this.sql); // 并且SQL字符串内容相同，返回true
    }

    @Override public int hashCode() { // 重写hashCode方法，用于支持HashMap等基于哈希的数据结构
      return 3709 + sql.hashCode(); // 返回一个固定值加上SQL字符串的哈希码，3709是一个任意的素数以减少哈希冲突
    }

    @Override public RelNode apply(RelOptFixture fixture) { // 实现apply方法：根据RelOptFixture测试夹具创建RelNode
      String sql2 = fixture.diffRepos().expand("sql", sql); // 使用差异仓库（diffRepos）扩展SQL，可能进行SQL变体替换
      return fixture.tester // 访问测试夹具中的tester对象
          .convertSqlToRel(fixture.factory, sql2, fixture.decorrelate, // 调用convertSqlToRel将SQL转换为关系表达式
              fixture.factory.sqlToRelConfig.isTrimUnusedFields()) // 传递是否修剪未使用字段的配置
          .project(); // 调用project方法确保返回一个ProjectRel节点（投影节点）
    }

    @Override public RelNode apply2(RelMetadataFixture metadataFixture) { // 实现apply2方法：根据RelMetadataFixture元数据测试夹具创建RelNode
      return metadataFixture.sqlToRel(sql); // 调用元数据测试夹具的sqlToRel方法，直接将SQL转换为RelNode
    }
  }

  /** Creates a RelNode by passing a lambda to a {@link RelBuilder}. */
  // FnRelSupplier是RelSupplier的实现类，通过RelBuilder函数来创建RelNode
  // 这个类允许测试用例使用编程方式（而非SQL）来构建关系表达式树
  // 提供了更灵活和类型安全的方式来创建测试用例
  class FnRelSupplier implements RelSupplier { // 实现RelSupplier接口，通过RelBuilder函数创建RelNode
    private final Function<RelBuilder, RelNode> relFn; // 成员变量：存储函数式接口，接收RelBuilder并返回RelNode，final表示初始化后不可修改

    private FnRelSupplier(Function<RelBuilder, RelNode> relFn) { // 私有构造方法：接收RelBuilder函数并初始化成员变量
      this.relFn = relFn; // 将传入的RelBuilder函数赋值给成员变量relFn
    }

    @Override public String toString() { // 重写toString方法，返回函数的字符串表示
      return "<relFn>"; // 返回"<relFn>"作为标识，表示这是一个RelBuilder函数
    }

    @Override public int hashCode() { // 重写hashCode方法，用于支持HashMap等基于哈希的数据结构
      return relFn.hashCode(); // 返回RelBuilder函数的哈希码
    }

    @Override public boolean equals(Object o) { // 重写equals方法，用于比较两个FnRelSupplier对象是否相等
      return o == this // 如果引用相同，返回true
          || o instanceof FnRelSupplier // 或者如果o是FnRelSupplier的实例
          && ((FnRelSupplier) o).relFn == relFn; // 并且RelBuilder函数引用相同（不是equals，而是==），返回true
    }

    @Override public RelNode apply(RelOptFixture fixture) { // 实现apply方法：根据RelOptFixture测试夹具创建RelNode
      return relFn.apply(RelBuilder.create(FRAMEWORK_CONFIG)); // 使用预定义的FRAMEWORK_CONFIG创建RelBuilder，然后调用relFn函数生成RelNode
    }

    @Override public RelNode apply2(RelMetadataFixture metadataFixture) { // 实现apply2方法：根据RelMetadataFixture元数据测试夹具创建RelNode
      return relFn.apply(RelBuilder.create(FRAMEWORK_CONFIG)); // 使用预定义的FRAMEWORK_CONFIG创建RelBuilder，然后调用relFn函数生成RelNode
    }
  }
}
