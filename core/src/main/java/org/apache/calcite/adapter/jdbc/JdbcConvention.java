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
package org.apache.calcite.adapter.jdbc; // JDBC适配器包，包含JDBC数据源相关的适配器实现

import org.apache.calcite.linq4j.tree.Expression; // 导入LINQ4J表达式类，用于表示代码表达式
import org.apache.calcite.plan.Convention; // 导入Convention类，表示关系代数操作的调用约定
import org.apache.calcite.plan.RelOptPlanner; // 导入关系优化器接口，用于优化关系表达式
import org.apache.calcite.plan.RelOptRule; // 导入优化规则接口，定义关系代数转换规则
import org.apache.calcite.rel.rules.CoreRules; // 导入核心优化规则集合，包含常用的优化规则
import org.apache.calcite.sql.SqlDialect; // 导入SQL方言类，用于处理不同数据库的SQL语法差异

/**
 * Calling convention for relational operations that occur in a JDBC
 * database. // JDBC数据库中关系操作的调用约定
 *
 * <p>The convention is a slight misnomer. The operations occur in whatever
 * data-flow architecture the database uses internally. Nevertheless, the result
 * pops out in JDBC. // 这个约定名称有点误导性，操作实际上发生在数据库内部使用的任何数据流架构中，但最终结果通过JDBC接口输出
 *
 * <p>This is the only convention, thus far, that is not a singleton. Each
 * instance contains a JDBC schema (and therefore a data source). If Calcite is
 * working with two different databases, it would even make sense to convert
 * from "JDBC#A" convention to "JDBC#B", even though we don't do it currently.
 * (That would involve asking database B to open a database link to database
 * A.) // 这是目前唯一不是单例的约定，每个实例都包含一个JDBC模式（因此也包含一个数据源）。如果Calcite同时处理两个不同的数据库，从"JDBC#A"约定转换到"JDBC#B"约定是有意义的（虽然目前未实现），这需要数据库B打开到数据库A的数据库链接
 *
 * <p>As a result, converter rules from and to this convention need to be
 * instantiated, at the start of planning, for each JDBC database in play.
 * // 因此，在规划开始时，需要为每个参与的JDBC数据库实例化进出此约定的转换规则
 */
public class JdbcConvention extends Convention.Impl { // JdbcConvention类继承自Convention.Impl，表示JDBC数据库的调用约定
  /** Cost of a JDBC node versus implementing an equivalent node in a "typical"
   * calling convention. */ // JDBC节点相对于在"典型"调用约定中实现等效节点的成本系数
  public static final double COST_MULTIPLIER = 0.8d; // 成本乘数常量，0.8表示JDBC节点比典型实现便宜20%，鼓励优化器优先使用JDBC下推

  public final SqlDialect dialect; // SQL方言成员变量，存储特定数据库的SQL语法特性（如MySQL、PostgreSQL等）
  public final Expression expression; // LINQ4J表达式成员变量，表示数据源的表达式形式，用于代码生成

  public JdbcConvention(SqlDialect dialect, Expression expression, // 构造方法：创建JdbcConvention实例
      String name) { // name参数：约定的名称，用于标识不同的JDBC约定实例
    super("JDBC." + name, JdbcRel.class); // 调用父类Convention.Impl的构造方法，设置约定名称为"JDBC."+name，关联的Rel接口为JdbcRel.class
    this.dialect = dialect; // 初始化dialect成员变量，保存SQL方言
    this.expression = expression; // 初始化expression成员变量，保存数据源表达式
  }

  public static JdbcConvention of(SqlDialect dialect, Expression expression, // 静态工厂方法：创建JdbcConvention实例的便捷方法
      String name) { // name参数：约定的名称
    return new JdbcConvention(dialect, expression, name); // 返回新创建的JdbcConvention实例
  }

  @Override public void register(RelOptPlanner planner) { // 重写register方法：向优化器注册与JDBC约定相关的优化规则
    for (RelOptRule rule : JdbcRules.rules(this)) { // 遍历JdbcRules.rules(this)返回的所有规则，这些规则是针对当前JdbcConvention实例定制的
      planner.addRule(rule); // 将每个规则添加到优化器中，使优化器能够应用这些规则进行查询重写和优化
    }
    planner.addRule(CoreRules.FILTER_SET_OP_TRANSPOSE); // 注册Filter与集合操作交换的规则，允许将Filter下推到集合操作下方
    planner.addRule(CoreRules.PROJECT_REMOVE); // 注册Project移除规则，用于移除冗余的投影操作
  }
}
