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
// Apache 许可证头部声明，说明此代码遵循 Apache 2.0 开源协议
package org.apache.calcite.adapter.jdbc; // 声明此类的包路径，属于 JDBC 适配器包，专门处理与 JDBC 数据源的集成

import org.apache.calcite.plan.Convention; // 导入 Convention 类，用于定义关系代数表达式的调用约定，表示关系树中节点的实现方式
import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster 类，代表关系表达式集群，包含查询优化器共享的上下文信息（如类型系统、表达式工厂等）
import org.apache.calcite.plan.RelOptTable; // 导入 RelOptTable 类，代表优化器视角的表对象，包含表的元数据信息（如字段名称、类型、统计信息等）
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet 类，关系特性集合，用于描述关系节点的物理属性（如是否排序、是否分区等）
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，关系代数节点接口，所有关系表达式都实现此接口
import org.apache.calcite.rel.core.TableScan; // 导入 TableScan 抽象类，表示从表中扫描数据的关系表达式
import org.apache.calcite.rel.hint.RelHint; // 导入 RelHint 类，关系提示，用于向优化器提供额外的优化建议

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList 类，用于创建不可变的列表，保证线程安全

import java.util.List; // 导入 Java 标准库的 List 接口，用于存储有序的元素集合

import static org.apache.calcite.linq4j.Nullness.castNonNull; // 导入静态方法 castNonNull，用于告诉类型检查器某个对象非空，避免空指针警告

import static java.util.Objects.requireNonNull; // 导入静态方法 requireNonNull，用于检查对象是否为 null，如果为 null 则抛出 NullPointerException

/**
 * Relational expression representing a scan of a table in a JDBC data source.
 * 表示扫描 JDBC 数据源中表的关系表达式
 * 
 * 类作用说明：
 * JdbcTableScan 是 Calcite 框架中用于表示从 JDBC 数据源扫描表的关系节点
 * 它继承自 TableScan 抽象类，并实现了 JdbcRel 接口，表明这是一个 JDBC 相关的关系表达式
 * 
 * 主要功能：
 * 1. 表示对 JDBC 数据库表的扫描操作，相当于 SQL 中的 "SELECT * FROM table_name"
 * 2. 将 Calcite 的逻辑表扫描转换为 JDBC 数据源可以理解的 SQL 查询
 * 3. 作为关系树的叶子节点，提供数据源的基础访问能力
 * 4. 支持通过 JdbcImplementor 将此节点转换为实际的 SQL 语句
 * 
 * 在查询优化过程中的角色：
 * - 作为物理执行计划的起点，代表从数据库表读取数据的操作
 * - 可以被优化器进一步转换（例如：添加过滤器、投影等操作）
 * - 最终会被翻译成 JDBC 数据源可以执行的 SQL 查询
 * 
 * 使用场景：
 * 当用户查询通过 JDBC 连接的外部数据库表时，Calcite 会创建 JdbcTableScan 节点
 * 例如：SELECT * FROM myschema.mytable，其中 myschema.mytable 是通过 JDBC 连接的表
 */
public class JdbcTableScan extends TableScan implements JdbcRel { // 定义公共类 JdbcTableScan，继承自 TableScan 并实现 JdbcRel 接口
  // 成员变量声明：jdbcTable
  // 作用：保存对 JDBC 表对象的引用，该对象包含了 JDBC 数据源表的元数据信息
  // 详细说明：
  // - JdbcTable 类型，表示 JDBC 数据源中的表，包含表的名称、字段信息、JDBC 连接配置等
  // - 使用 final 修饰，表示一旦初始化后不可更改，保证线程安全
  // - public 修饰符，允许外部访问此表对象
  // - 此字段是 JdbcTableScan 的核心数据，所有与 JDBC 表相关的操作都依赖此对象
  // - 通过 jdbcTable 可以获取表名、字段类型、生成 SQL 语句等信息
  // - 在构造函数中初始化，在整个生命周期内保持不变
  public final JdbcTable jdbcTable; // 声明公共的、不可变的 JDBC 表对象成员变量

  // 构造方法：JdbcTableScan
  // 作用：创建 JdbcTableScan 实例，初始化关系表达式节点
  // 
  // 参数详细说明：
  // @param cluster - RelOptCluster 类型，关系表达式集群对象
  //                 包含查询优化器的共享上下文，如：
  //                 - 类型系统（TypeFactory）：用于创建和操作数据类型
  //                 - 表达式工厂（RexBuilder）：用于创建关系表达式（如条件、投影等）
  //                 - 查询计划相关的元数据
  //                 - 所有相关节点共享此集群对象
  // 
  // @param hints - List<RelHint> 类型，关系提示列表
  //               用于向优化器提供额外的优化建议，例如：
  //               - /*+ INDEX(table_name index_name) */ 提示使用特定索引
  //               - /*+ PARALLEL(4) */ 提示并行度
  //               - /*+ NO_CACHE */ 提示不使用缓存
  //               这些提示可以影响查询的执行计划
  // 
  // @param table - RelOptTable 类型，优化器视角的表对象
  //               包含表的元数据信息，例如：
  //               - 表名和模式名
  //               - 字段列表（名称、类型、可空性等）
  //               - 统计信息（行数、唯一值数量等）
  //               - 约束信息（主键、外键等）
  //               - 表的原始定义（如 Schema.Table）
  // 
  // @param jdbcTable - JdbcTable 类型，JDBC 特定的表对象
  //                   包含 JDBC 数据源表的详细信息，例如：
  //                   - JDBC 连接配置（URL、用户名、密码等）
  //                   - 表的完全限定名（catalog.schema.table）
  //                   - 字段到 SQL 类型的映射
  //                   - 生成 SQL 语句所需的信息
  //                   - 与此表相关的 JDBC 特定操作
  // 
  // @param jdbcConvention - JdbcConvention 类型，JDBC 调用约定
  //                        定义 JDBC 关系表达式的物理属性，例如：
  //                        - 表示此节点应该通过 JDBC 执行
  //                        - 定义如何将关系树转换为 SQL
  //                        - 包含 JDBC 相关的配置和规则
  //                        - 是 RelTraitSet 的一部分
  // 
  // 构造方法详细说明：
  // 1. 调用父类 TableScan 的构造函数，初始化基础属性
  //    - cluster：传入集群对象
  //    - cluster.traitSetOf(jdbcConvention)：创建特性集合，只包含 JDBC 调用约定
  //      这表示此节点使用 JDBC 约定，将通过 JDBC 执行
  //    - hints：传入提示列表
  //    - table：传入优化器表对象
  // 
  // 2. 初始化 jdbcTable 成员变量
  //    - 使用 requireNonNull 方法检查 jdbcTable 参数是否为 null
  //    - 如果为 null，抛出 NullPointerException 并附带错误信息 "jdbcTable"
  //    - 这种防御性编程确保对象状态的完整性
  // 
  // 3. 构造函数是 protected 修饰符，表示只能在包内或子类中访问
  //    - 通常通过工厂方法或规则创建实例
  //    - 保证创建过程的可控性
  protected JdbcTableScan( // 声明受保护的构造方法
      RelOptCluster cluster, // 参数：关系表达式集群对象
      List<RelHint> hints, // 参数：关系提示列表
      RelOptTable table, // 参数：优化器表对象
      JdbcTable jdbcTable, // 参数：JDBC 表对象
      JdbcConvention jdbcConvention) { // 参数：JDBC 调用约定
    super(cluster, cluster.traitSetOf(jdbcConvention), hints, table); // 调用父类构造函数，初始化基础属性
    this.jdbcTable = requireNonNull(jdbcTable, "jdbcTable"); // 初始化 jdbcTable 成员变量，确保非空
  } // 构造方法结束

  // 方法：copy
  // 作用：复制当前关系节点，创建具有新特性集合的副本
  // 
  // 参数详细说明：
  // @param traitSet - RelTraitSet 类型，新的关系特性集合
  //                  包含节点的物理属性，例如：
  //                  - 调用约定（Convention）：如 JDBC、Enumerable 等
  //                  - 排序特性（Collation）：数据的排序方式
  //                  - 分区特性（Partitioning）：数据的分区方式
  //                  - 分布特性（Distribution）：数据的分布方式
  //                  用于在优化过程中改变节点的物理属性
  // 
  // @param inputs - List<RelNode> 类型，输入节点列表
  //                对于 TableScan 节点，应该为空列表，因为表扫描是叶子节点，没有输入
  //                其他类型的节点（如 Join、Filter）会有输入节点
  // 
  // 返回值详细说明：
  // @return RelNode - 返回新的关系节点对象
  //                  返回一个新的 JdbcTableScan 实例，具有相同的表信息但可能有不同的特性集合
  //                  这是关系代数操作的标准模式，用于在不修改原对象的情况下创建变体
  // 
  // 方法详细说明：
  // 1. 使用 @Override 注解，表示重写父类 TableScan 的 copy 方法
  //    - 父类方法定义在 RelNode 接口中
  //    - 必须保持方法签名一致
  // 
  // 2. 断言检查 inputs 是否为空
  //    - assert inputs.isEmpty()：确保输入节点列表为空
  //    - TableScan 是叶子节点，不应该有输入
  //    - 如果断言失败，说明调用者传入了错误的参数
  //    - 断言在调试模式下启用，生产环境可以禁用
  // 
  // 3. 创建并返回新的 JdbcTableScan 实例
  //    - getCluster()：获取当前的集群对象
  //    - getHints()：获取当前的提示列表
  //    - table：获取当前的表对象
  //    - jdbcTable：获取当前的 JDBC 表对象
  //    - castNonNull(getConvention())：获取当前的调用约定，并使用 castNonNull 告诉类型检查器非空
  //    - (JdbcConvention) 类型转换：将调用约定转换为 JdbcConvention 类型
  // 
  // 4. 方法的使用场景
  //    - 优化器在应用规则时需要创建节点的副本
  //    - 改变节点的特性集合（例如：从一种约定转换为另一种）
  //    - 保持不可变性，每次修改都创建新对象
  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写 copy 方法，用于复制节点
    assert inputs.isEmpty(); // 断言输入列表为空，表扫描节点不应该有输入
    return new JdbcTableScan( // 返回新的 JdbcTableScan 实例
        getCluster(), getHints(), table, jdbcTable, (JdbcConvention) castNonNull(getConvention())); // 使用当前属性和新特性集创建新实例
  } // copy 方法结束

  // 方法：implement
  // 作用：实现此关系节点，将其转换为 JDBC SQL 查询
  // 
  // 参数详细说明：
  // @param implementor - JdbcImplementor 类型，JDBC 实现器对象
  //                     负责将关系表达式树转换为 SQL 语句
  //                     包含以下功能：
  //                     - 遍历关系树并生成 SQL
  //                     - 管理子查询的上下文
  //                     - 处理 SQL 子句的嵌套（FROM、WHERE、SELECT 等）
  //                     - 维护 SQL 语句的构建状态
  // 
  // 返回值详细说明：
  // @return JdbcImplementor.Result - 返回实现结果对象
  //                                  包含以下信息：
  //                                  - 生成的 SQL 片段
  //                                  - SQL 子句的类型（FROM、WHERE、SELECT 等）
  //                                  - 关联的关系节点
  //                                  - 可能的子查询结果
  //                                  这个结果会被上层节点组合成完整的 SQL 语句
  // 
  // 方法详细说明：
  // 1. 使用 @Override 注解，表示实现 JdbcRel 接口的 implement 方法
  //    - JdbcRel 接口定义了所有 JDBC 相关关系节点必须实现的方法
  //    - 此方法是核心方法，负责将关系节点转换为 SQL
  // 
  // 2. 调用 implementor.result 方法生成结果
  //    - jdbcTable.tableName()：获取表的名称
  //      - 返回完全限定的表名，如 "catalog.schema.table"
  //      - 这个名称将直接用于 SQL 的 FROM 子句
  //      - 表名可能包含引号（如果表名是保留字或特殊字符）
  // 
  //    - ImmutableList.of(JdbcImplementor.Clause.FROM)：创建不可变的子句列表
  //      - JdbcImplementor.Clause.FROM 表示这是一个 FROM 子句
  //      - FROM 子句是 SQL 查询的基础，指定数据来源
  //      - 使用 ImmutableList 确保线程安全
  // 
  //    - this：传入当前节点对象
  //      - 用于在结果中关联生成此 SQL 的关系节点
  //      - 便于调试和错误追踪
  // 
  //    - null：传入 null 作为附加参数
  //      - 对于 TableScan 节点，没有额外的参数需要传递
  //      - 其他节点（如 Join）可能会传入子查询结果
  // 
  // 3. 生成的 SQL 示例
  //    - 如果 tableName() 返回 "schema1.table1"
  //    - 生成的 SQL 片段为 "schema1.table1"
  //    - 在完整查询中会出现在 FROM 子句：SELECT ... FROM schema1.table1
  // 
  // 4. 方法的调用时机
  //    - 当优化器决定将查询下推到 JDBC 数据源时
  //    - 在生成物理执行计划的过程中
  //    - JdbcToEnumerableConverterRule 会调用此方法
  @Override public JdbcImplementor.Result implement(JdbcImplementor implementor) { // 实现 JDBC 关系节点，转换为 SQL
    return implementor.result(jdbcTable.tableName(), // 获取表名并创建 FROM 子句结果
        ImmutableList.of(JdbcImplementor.Clause.FROM), this, null); // 返回包含 FROM 子句的实现结果
  } // implement 方法结束

  // 方法：withHints
  // 作用：创建具有新提示列表的关系节点副本
  // 
  // 参数详细说明：
  // @param hintList - List<RelHint> 类型，新的关系提示列表
  //                  包含优化器提示，例如：
  //                  - 索引提示：/*+ INDEX(table_name index_name) */
  //                  - 并行提示：/*+ PARALLEL(4) */
  //                  - 缓存提示：/*+ CACHE */ 或 /*+ NO_CACHE */
  //                  - 连接提示：/*+ USE_HASH(table_name) */
  //                  这些提示可以影响查询执行计划的选择
  // 
  // 返回值详细说明：
  // @return RelNode - 返回新的关系节点对象
  //                  返回一个新的 JdbcTableScan 实例，具有相同的表和约定，但使用新的提示列表
  //                  原始对象保持不变（不可变性）
  // 
  // 方法详细说明：
  // 1. 使用 @Override 注解，表示重写父类的方法
  //    - 父类 TableScan 或 RelNode 定义了此方法
  //    - 用于在不修改原对象的情况下创建带有新提示的副本
  // 
  // 2. 获取当前的调用约定
  //    - getConvention()：获取当前节点的调用约定
  //    - requireNonNull(getConvention(), "getConvention()")：确保约定不为 null
  //      - 如果为 null，抛出 NullPointerException
  //      - 错误信息为 "getConvention()"，便于调试
  //    - Convention 是关系节点的物理属性，定义了如何执行此节点
  // 
  // 3. 创建并返回新的 JdbcTableScan 实例
  //    - getCluster()：使用当前的集群对象
  //    - hintList：使用传入的新提示列表（替换原有提示）
  //    - getTable()：使用当前的表对象
  //    - jdbcTable：使用当前的 JDBC 表对象
  //    - (JdbcConvention) convention：将获取的约定转换为 JdbcConvention 类型
  // 
  // 4. 方法的使用场景
  //    - 优化器在应用基于提示的规则时
  //    - 用户在 SQL 中添加了提示，需要创建新的节点
  //    - 在查询重写过程中修改提示信息
  //    - 保持不可变性，不直接修改原对象的提示列表
  // 
  // 5. 示例
  //    - 原节点：JdbcTableScan(hints=[hint1], table=table1)
  //    - 调用 withHints([hint2, hint3])
  //    - 新节点：JdbcTableScan(hints=[hint2, hint3], table=table1)
  //    - 原节点保持不变
  @Override public RelNode withHints(List<RelHint> hintList) { // 重写 withHints 方法，创建带有新提示的副本
    Convention convention = requireNonNull(getConvention(), "getConvention()"); // 获取当前的调用约定，确保非空
    return new JdbcTableScan(getCluster(), hintList, getTable(), jdbcTable, // 返回新的 JdbcTableScan 实例
        (JdbcConvention) convention); // 使用转换后的 JDBC 约定
  } // withHints 方法结束
} // 类定义结束
