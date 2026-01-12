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
// Apache Calcite是一个动态数据管理框架，提供SQL解析、优化、执行等功能
// 本文件是Calcite测试框架的核心基类，用于测试SQL到关系代数的转换过程
package org.apache.calcite.test;

import org.apache.calcite.plan.RelOptCluster; // 关系代数簇：包含关系代数表达式、表达式工厂等核心组件，是关系代数树的根节点
import org.apache.calcite.plan.RelTraitSet; // 关系代数特征集合：定义关系节点的物理属性，如排序规则、分布方式等，用于优化器选择最佳执行计划
import org.apache.calcite.rel.RelNode; // 关系节点：关系代数树的基本节点类型，所有关系操作（扫描、过滤、连接等）都继承自此类
import org.apache.calcite.rel.RelShuttle; // 关系代数访问器：用于遍历和转换关系代数树的访问者模式接口
import org.apache.calcite.rel.core.Correlate; // 相关子查询节点：表示SQL中的相关子查询（子查询引用外部查询的列）
import org.apache.calcite.rel.core.CorrelationId; // 相关ID：唯一标识一个相关子查询，用于在优化过程中跟踪和去相关
import org.apache.calcite.rel.core.JoinRelType; // 连接类型：定义连接操作的类型，如内连接(INNER)、左外连接(LEFT)、右外连接(RIGHT)、全外连接(FULL)
import org.apache.calcite.rel.hint.RelHint; // 关系提示：用于向优化器提供执行建议的元数据，如使用特定索引、并行度等
import org.apache.calcite.test.catalog.MockCatalogReader; // 模拟目录读取器：提供测试用的元数据（表、列、类型等），模拟真实数据库的catalog
import org.apache.calcite.util.ImmutableBitSet; // 不可变位集合：高效表示一组整数索引，常用于表示列集合或位置集合

import java.util.List; // Java集合框架的List接口，用于存储有序的对象集合

/**
 * SqlToRelTestBase is an abstract base for tests which involve conversion from
 * SQL to relational algebra.
 *
 * <p>SQL statements to be translated can use the schema defined in
 * {@link MockCatalogReader}; note that this is slightly different from
 * Farrago's SALES schema. If you get a parser or validator error from your test
 * SQL, look down in the stack until you see "Caused by", which will usually
 * tell you the real error.
 */
// SqlToRelTestBase类：SQL到关系代数转换测试的抽象基类
// 作用：为所有涉及SQL解析和关系代数转换的测试提供基础框架和工具方法
// 核心功能：
// 1. 提供测试夹具(fixture)机制，允许子类自定义测试环境配置
// 2. 提供便捷方法将SQL字符串转换为关系代数树
// 3. 提供自定义的Correlate节点用于测试相关子查询
// 4. 使用MockCatalogReader提供测试用的元数据，避免依赖真实数据库
// 使用场景：测试SQL解析器、验证器、SQL到关系代数转换器、优化器等组件
// 测试SQL可以使用MockCatalogReader中定义的schema，包含EMP、DEPT等测试表
// 如果测试SQL出现解析或验证错误，查看堆栈跟踪中的"Caused by"部分可以找到根本原因
public abstract class SqlToRelTestBase {
  //~ Static fields/initializers ---------------------------------------------
  // 静态字段和初始化器区域标记（用于代码组织，将静态成员与其他成员分隔）

  protected static final String NL = System.getProperty("line.separator"); // NL：换行符常量，从系统属性中获取当前操作系统的换行符（Windows为"\r\n"，Unix/Linux/macOS为"\n"），用于构建跨平台的字符串输出

  //~ Instance fields --------------------------------------------------------
  // 实例字段区域标记（用于代码组织，将实例成员与其他成员分隔）
  // 当前类没有定义实例字段，所有测试配置通过fixture()方法提供的SqlToRelFixture对象管理

//~ Methods ----------------------------------------------------------------
  // 方法区域标记（用于代码组织，将方法与其他成员分隔）

  /**
   * Creates the test fixture that determines the behavior of tests.
   * Sub-classes that, say, test different parser implementations should
   * override.
   */
  // fixture()方法：创建测试夹具，决定测试的行为和配置
  // 返回值：SqlToRelFixture对象，包含测试所需的所有配置（parser、validator、catalog等）
  // 作用：
  // 1. 提供统一的测试环境配置入口
  // 2. 允许子类覆盖以自定义测试行为（如使用不同的parser实现）
  // 3. 默认返回SqlToRelFixture.DEFAULT，使用标准配置
  // 使用场景：
  // - 测试不同SQL方言时，可以覆盖此方法返回使用特定parser的fixture
  // - 测试不同特性时，可以返回启用/禁用特定功能的fixture
  public SqlToRelFixture fixture() {
    return SqlToRelFixture.DEFAULT; // 返回默认的测试夹具，包含标准的parser、validator和catalog配置
  }

  /**
   * Creates a test context with a SQL query.
   * Default catalog: {@link org.apache.calcite.test.catalog.MockCatalogReaderSimple#init()}.
   */
  // sql()方法：创建包含SQL查询的测试上下文
  // 参数：sql - 要测试的SQL查询字符串
  // 返回值：SqlToRelFixture对象，已配置好指定的SQL查询
  // 作用：
  // 1. 便捷方法，用于快速创建SQL测试场景
  // 2. 使用默认的catalog（MockCatalogReaderSimple#init()），包含基本的测试表
  // 3. 返回的fixture可以进一步配置或直接执行转换
  // 使用场景：
  // - 测试简单的SQL查询转换：sql("SELECT * FROM emp").convert()
  // - 链式调用配置：sql("SELECT * FROM emp").withConvention(EnumerableConvention.INSTANCE).convert()
  // 注意：默认catalog与Farrago的SALES schema略有不同，使用时要注意表结构差异
  public final SqlToRelFixture sql(String sql) {
    return fixture().withSql(sql); // 获取当前fixture并设置SQL查询，返回配置好的fixture对象
  }

  // expr()方法：创建包含SQL表达式的测试上下文（表达式模式）
  // 参数：sql - 要测试的SQL表达式字符串（如"1 + 2"、"emp.deptno"等）
  // 返回值：SqlToRelFixture对象，已配置为表达式模式并包含指定的SQL表达式
  // 作用：
  // 1. 与sql()方法类似，但专门用于测试表达式而非完整查询
  // 2. 调用expression(true)将fixture设置为表达式模式
  // 3. 表达式模式下，SQL会被当作表达式处理，而不是完整的SELECT语句
  // 使用场景：
  // - 测试标量表达式：expr("1 + 2 * 3").convert()
  // - 测试列引用表达式：expr("emp.deptno").convert()
  // - 测试函数调用表达式：expr("UPPER(emp.ename)").convert()
  // 注意：表达式模式与完整查询模式在解析和转换逻辑上有所不同
  public final SqlToRelFixture expr(String sql) {
    return fixture().expression(true).withSql(sql); // 获取fixture，设置为表达式模式，然后设置SQL表达式
  }

  //~ Inner Classes ----------------------------------------------------------
  // 内部类区域标记（用于代码组织，将内部类与其他成员分隔）

  /**
   * Custom implementation of Correlate for testing.
   */
  // CustomCorrelate类：Correlate的自定义实现，用于测试相关子查询
  // 继承自：Correlate（Calcite关系代数中的相关子查询节点）
  // 作用：
  // 1. 提供专门用于测试的Correlate实现
  // 2. 允许测试代码注入自定义行为或验证相关子查询的处理
  // 3. 作为测试框架的扩展点，可以在此添加测试特定的逻辑
  // 相关子查询：SQL中引用外部查询列的子查询，如"SELECT * FROM emp WHERE deptno = (SELECT deptno FROM dept WHERE emp.deptno = dept.deptno)"
  // 在关系代数中，相关子查询通过Correlate节点表示，需要特殊处理（去相关、优化等）
  public static class CustomCorrelate extends Correlate {
    // CustomCorrelate构造方法：创建自定义相关子查询节点
    // 参数：
    // - cluster: 关系代数簇，包含表达式工厂、类型系统等核心组件，是关系代数树的根节点
    // - traits: 关系特征集合，定义节点的物理属性（如排序、分布），用于优化器选择执行计划
    // - hints: 关系提示列表，向优化器提供执行建议（如使用索引、并行度等）
    // - left: 左子节点（外部查询的关系代数树）
    // - right: 右子节点（子查询的关系代数树）
    // - correlationId: 相关ID，唯一标识这个相关子查询，用于在优化过程中跟踪和去相关
    // - requiredColumns: 需要从左子节点传递到右子查询的列集合（位集合表示）
    // - joinType: 连接类型（INNER、LEFT、RIGHT、FULL），定义相关子查询与外部查询的连接方式
    // 作用：初始化CustomCorrelate节点，将所有参数传递给父类Correlate的构造方法
    public CustomCorrelate(
        RelOptCluster cluster,
        RelTraitSet traits,
        List<RelHint> hints,
        RelNode left,
        RelNode right,
        CorrelationId correlationId,
        ImmutableBitSet requiredColumns,
        JoinRelType joinType) {
      super(cluster, traits, hints, left, right, correlationId, requiredColumns,
          joinType); // 调用父类Correlate的构造方法，初始化相关子查询节点的所有属性
    }

    // copy()方法：复制当前Correlate节点，可以修改部分属性
    // 参数：
    // - traitSet: 新的特征集合（可能不同于原节点的特征）
    // - left: 新的左子节点
    // - right: 新的右子节点
    // - correlationId: 新的相关ID
    // - requiredColumns: 新的必需列集合
    // - joinType: 新的连接类型
    // 返回值：新的CustomCorrelate节点，包含指定的属性修改
    // 作用：
    // 1. 实现RelNode接口的copy方法，用于优化器在转换过程中创建节点副本
    // 2. 优化器可能会修改节点的特征（如添加排序规则）或替换子节点
    // 3. 返回CustomCorrelate实例而不是普通的Correlate，保持测试特定的行为
    // 使用场景：优化器重写规则、特征转换、子节点替换等
    @Override
    public Correlate copy(RelTraitSet traitSet,
        RelNode left, RelNode right, CorrelationId correlationId,
        ImmutableBitSet requiredColumns, JoinRelType joinType) {
      return new CustomCorrelate(getCluster(), traitSet, hints, left, right,
          correlationId, requiredColumns, joinType); // 创建新的CustomCorrelate实例，使用当前cluster和hints，其他参数使用传入的新值
    }

    // withHints()方法：创建带有新提示列表的节点副本
    // 参数：hintList: 新的关系提示列表
    // 返回值：新的CustomCorrelate节点，使用新的提示列表
    // 作用：
    // 1. 实现RelNode接口的withHints方法，用于添加或替换关系提示
    // 2. 关系提示可以向优化器提供执行建议，如使用特定索引、设置并行度等
    // 3. 返回CustomCorrelate实例而不是普通的Correlate，保持测试特定的行为
    // 使用场景：测试提示功能、优化器基于提示选择执行计划等
    @Override
    public RelNode withHints(List<RelHint> hintList) {
      return new CustomCorrelate(getCluster(), traitSet, hintList, left, right,
          correlationId, requiredColumns, joinType); // 创建新的CustomCorrelate实例，使用新的提示列表，其他属性保持不变
    }

    // accept()方法：接受关系代数访问器，实现访问者模式
    // 参数：shuttle: 关系代数访问器（RelShuttle），用于遍历和转换关系代数树
    // 返回值：访问器处理后的结果（可能是修改后的节点或新节点）
    // 作用：
    // 1. 实现RelNode接口的accept方法，支持访问者模式
    // 2. 允许外部代码遍历和转换关系代数树，而不需要修改节点类本身
    // 3. 访问器可以执行各种操作：收集信息、验证、转换、优化等
    // 使用场景：
    // - 关系代数树的遍历和打印
    // - 节点转换和重写
    // - 信息收集（如统计信息收集）
    // - 测试验证（如检查特定节点是否存在）
    @Override
    public RelNode accept(RelShuttle shuttle) {
      return shuttle.visit(this); // 将当前CustomCorrelate节点传递给访问器，让访问器决定如何处理
    }
  }
}
