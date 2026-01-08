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
// 定义包名，该接口位于org.apache.calcite.materialize包下，属于物化视图相关功能模块
package org.apache.calcite.materialize;

// 导入RelOptTable类，这是Calcite中表示优化器表的抽象，包含了表的元数据信息
import org.apache.calcite.plan.RelOptTable;

// 导入List接口，用于表示列索引的集合
import java.util.List;

/**
 * Estimates row counts for tables and columns, and whether combinations of
 * columns form primary/unique and foreign keys.
 * 估算表和列的行数，以及列的组合是否构成主键/唯一键和外键。
 *
 * <p>Unlike {@link LatticeStatisticProvider}, works on raw tables and columns
 * and does not need a {@link Lattice}.
 * 与{@link LatticeStatisticProvider}不同，它作用于原始表和列，不需要{@link Lattice}（立方体/格子）结构。
 * LatticeStatisticProvider是专门为Lattice结构设计的统计提供者，而SqlStatisticProvider更通用，直接操作表。
 *
 * <p>It uses {@link org.apache.calcite.plan.RelOptTable} because that contains
 * enough information to generate and execute SQL, while not being tied to a
 * lattice.
 * 它使用{@link org.apache.calcite.plan.RelOptTable}，因为这个类包含足够的信息来生成和执行SQL查询，
 * 同时又不依赖于Lattice结构，这使得它可以独立于Lattice工作。
 *
 * <p>The main implementation,
 * {@link org.apache.calcite.statistic.QuerySqlStatisticProvider}, executes
 * queries on a populated database. Implementations that use database statistics
 * (from {@code ANALYZE TABLE}, etc.) and catalog information (e.g. primary and
 * foreign key constraints) would also be possible.
 * 主要的实现类{@link org.apache.calcite.statistic.QuerySqlStatisticProvider}在已填充数据的数据库上执行查询。
 * 也可以有其他实现方式，比如使用数据库统计信息（来自{@code ANALYZE TABLE}命令等）和目录信息
 * （例如主键和外键约束信息）。这为不同的数据库适配提供了灵活性。
 */
// 定义SqlStatisticProvider接口，这是一个用于提供SQL统计信息的接口
// 统计信息包括表的基数（行数）、外键关系和唯一键等，这些信息对查询优化器的成本估算至关重要
public interface SqlStatisticProvider {
  /** Returns an estimate of the number of rows in {@code table}. */
  // 方法注释：返回表{@code table}中行数的估计值
  // 这个方法用于获取表的基数（cardinality），即表中大约有多少行数据
  // 基数是查询优化器进行成本估算的重要参数，影响join顺序、索引选择等优化决策
  // 返回值是double类型，因为可能需要返回估计值而不是精确值
  double tableCardinality(RelOptTable table);

  /** Returns whether a join is a foreign key; that is, whether every row in
   * the referencing table is matched by at least one row in the referenced
   * table.
   * 返回一个连接是否是外键关系；也就是说，引用表中的每一行是否都能在被引用表中找到至少一行匹配。
   * 外键关系保证了引用完整性，即引用表中的外键值必须存在于被引用表的主键或唯一键中。
   *
   * <p>For example, {@code isForeignKey(EMP, [DEPTNO], DEPT, [DEPTNO])}
   * returns true.
   * 例如，{@code isForeignKey(EMP, [DEPTNO], DEPT, [DEPTNO])}返回true。
   * 这表示EMP表中的DEPTNO列引用了DEPT表的DEPTNO列，构成了外键关系。
   * 在这个例子中，EMP表是引用表（fromTable），DEPTNO是引用列（fromColumns），
   * DEPT表是被引用表（toTable），DEPTNO是被引用列（toColumns）。
   *
   * <p>To change "at least one" to "exactly one", you also need to call
   * {@link #isKey}.
   * 要将"至少一个"改为"恰好一个"，你还需要调用{@link #isKey}方法。
   * isForeignKey只保证"至少一个"匹配，如果需要保证"恰好一个"（即一对一关系），
   * 还需要检查引用列是否是唯一键。如果isForeignKey和isKey都返回true，则是一对一关系；
   * 如果只有isForeignKey返回true，则是多对一关系。
   */
  // 方法定义：判断两个表之间的列是否构成外键关系
  // 参数说明：
  //   fromTable: 引用表（包含外键的表）
  //   fromColumns: 引用表中构成外键的列的索引列表（0-based索引）
  //   toTable: 被引用表（包含主键或唯一键的表）
  //   toColumns: 被引用表中构成主键或唯一键的列的索引列表
  // 返回值：如果构成外键关系返回true，否则返回false
  boolean isForeignKey(RelOptTable fromTable, List<Integer> fromColumns,
      RelOptTable toTable, List<Integer> toColumns);

  /** Returns whether a collection of columns is a unique (or primary) key.
   * 返回一组列是否是唯一键（或主键）。
   * 唯一键保证了列组合的值在表中是唯一的，不能重复。
   * 主键是特殊的唯一键，不允许为NULL值。
   *
   * <p>For example, {@code isKey(EMP, [DEPTNO]} returns true;
   * {@code isKey(DEPT, [DEPTNO]} returns false.
   * 例如，{@code isKey(EMP, [DEPTNO]}返回true；
   * {@code isKey(DEPT, [DEPTNO]}返回false。
   * 这个例子可能不太准确，因为通常DEPTNO在DEPT表中应该是主键。
   * 更准确的例子可能是：isKey(EMP, [EMPNO])返回true（EMPNO是员工表的主键），
   * isKey(EMP, [ENAME])返回false（ENAME不是唯一的，可能重名）。
   *
   * 注意：唯一键可以是单列，也可以是多列的组合。对于多列组合，只有所有列的值组合在一起才是唯一的。
   */
  // 方法定义：判断表中的一组列是否构成唯一键或主键
  // 参数说明：
  //   table: 要检查的表
  //   columns: 表中列的索引列表，这些列的组合需要检查是否是唯一键
  // 返回值：如果这些列构成唯一键或主键返回true，否则返回false
  boolean isKey(RelOptTable table, List<Integer> columns);
}
