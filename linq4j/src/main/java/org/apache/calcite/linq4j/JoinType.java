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
package org.apache.calcite.linq4j; // 声明包名，该类属于org.apache.calcite.linq4j包，是Calcite的LINQ4J模块

/**
 * Enumeration of join types. // 枚举类型，定义了所有可能的连接类型
 * JoinType枚举类定义了SQL和LINQ查询中所有可能的连接操作类型
 * 包括：内连接、左外连接、右外连接、全外连接、半连接、反连接、ASOF连接和左ASOF连接
 * 这个枚举主要用于SQL查询优化和执行时的连接操作类型判断和决策
 */
public enum JoinType { // 定义一个名为JoinType的公共枚举类，表示连接类型的枚举
  /**
   * Inner join. // 内连接
   * 内连接是最常见的连接类型，只返回两个表中连接条件匹配的行
   * 如果左表的某一行在右表中没有匹配的行，则该行不会出现在结果中
   * 同样，如果右表的某一行在左表中没有匹配的行，该行也不会出现在结果中
   * SQL示例：SELECT * FROM A INNER JOIN B ON A.id = B.id
   */
  INNER, // 枚举常量：内连接类型

  /**
   * Left-outer join. // 左外连接
   * 左外连接返回左表的所有行，以及右表中匹配的行
   * 如果左表的某一行在右表中没有匹配的行，则结果中该行的右表列会显示为NULL
   * 左表的所有行都会出现在结果中，无论是否在右表中找到匹配
   * SQL示例：SELECT * FROM A LEFT OUTER JOIN B ON A.id = B.id
   */
  LEFT, // 枚举常量：左外连接类型

  /**
   * Right-outer join. // 右外连接
   * 右外连接返回右表的所有行，以及左表中匹配的行
   * 如果右表的某一行在左表中没有匹配的行，则结果中该行的左表列会显示为NULL
   * 右表的所有行都会出现在结果中，无论是否在左表中找到匹配
   * SQL示例：SELECT * FROM A RIGHT OUTER JOIN B ON A.id = B.id
   */
  RIGHT, // 枚举常量：右外连接类型

  /**
   * Full-outer join. // 全外连接
   * 全外连接返回左表和右表中的所有行
   * 当某一行在另一个表中没有匹配的行时，缺失的一侧包含NULL值
   * 全外连接是左外连接和右外连接的并集
   * SQL示例：SELECT * FROM A FULL OUTER JOIN B ON A.id = B.id
   */
  FULL, // 枚举常量：全外连接类型

  /**
   * Semi-join. // 半连接
   * 半连接是一种特殊的连接，只返回左表中与右表有匹配的行
   * 与内连接不同，半连接不会返回右表的列，只返回左表的列
   * 半连接通常用于检查存在性，类似于SQL中的EXISTS子查询
   *
   * <p>For example, {@code EMP semi-join DEPT} finds all {@code EMP} records
   * that have a corresponding {@code DEPT} record: // 例如，EMP半连接DEPT会找到所有在DEPT中有对应记录的EMP记录
   * 这个例子展示了半连接的实际应用场景：找出所有属于某个部门的员工
   *
   * <blockquote><pre>
   * SELECT * FROM EMP // 从EMP表中选择所有记录
   * WHERE EXISTS (SELECT 1 FROM DEPT // 使用EXISTS子查询检查是否存在匹配的部门
   *     WHERE DEPT.DEPTNO = EMP.DEPTNO)</pre> // 连接条件：部门编号相等
   * </blockquote>
   */
  SEMI, // 枚举常量：半连接类型

  /**
   * Anti-join (also known as Anti-semi-join). // 反连接（也称为反半连接）
   * 反连接与半连接相反，只返回左表中与右表没有匹配的行
   * 反连接用于检查不存在性，类似于SQL中的NOT EXISTS子查询
   * 反连接在查询优化中很有用，可以快速排除不符合条件的记录
   *
   * <p>For example, {@code EMP anti-join DEPT} finds all {@code EMP} records
   * that do not have a corresponding {@code DEPT} record: // 例如，EMP反连接DEPT会找到所有在DEPT中没有对应记录的EMP记录
   * 这个例子展示了反连接的实际应用场景：找出所有不属于任何部门的员工
   *
   * <blockquote><pre>
   * SELECT * FROM EMP // 从EMP表中选择所有记录
   * WHERE NOT EXISTS (SELECT 1 FROM DEPT // 使用NOT EXISTS子查询检查是否不存在匹配的部门
   *     WHERE DEPT.DEPTNO = EMP.DEPTNO)</pre> // 连接条件：部门编号相等
   * </blockquote>
   */
  ANTI, // 枚举常量：反连接类型

  /**
   * An ASOF JOIN operation combines rows from two tables based on comparable timestamp values. // ASOF连接操作基于可比较的时间戳值组合两个表的行
   * ASOF连接是一种特殊的连接，用于处理时间序列数据
   * 对于左表中的每一行，连接在右表中找到最多一行具有"最接近"时间戳值的行
   * For each row in the left table, the join finds at most one row in the right table that has the
   * "closest" timestamp value. // 对于左表中的每一行，连接在右表中找到最多一行具有"最接近"时间戳值的行
   * The matched row on the right side is the closest match, // 右侧匹配的行是最接近的匹配
   * which could less than or equal or greater than or equal in the timestamp column, // 在时间戳列中可能是小于等于或大于等于
   * as specified by the comparison operator. // 根据比较运算符指定
   * ASOF连接在金融数据分析、物联网数据处理等场景中非常有用
   *
   * <p>Example: // 示例：
   * <blockquote><pre>
   * FROM left_table ASOF JOIN right_table // 从左表ASOF连接右表
   *   MATCH_CONDITION ( left_table.timecol &le; right_table.timecol ) // 匹配条件：左表时间列小于等于右表时间列
   *   ON left_table.col = right_table.col</pre> // 连接条件：其他列相等
   * </blockquote>
   */
  ASOF, // 枚举常量：ASOF连接类型

  /**
   * The left version of an ASOF join, where each row from the left table is part of the output. // ASOF连接的左版本，左表的每一行都是输出的一部分
   * LEFT_ASOF是ASOF连接的变体，保证左表的所有行都会出现在结果中
   * 即使在右表中找不到匹配的行，左表的行也会保留，右表列会显示为NULL
   * 这类似于左外连接，但使用ASOF的匹配逻辑
   */
  LEFT_ASOF; // 枚举常量：左ASOF连接类型

  /**
   * Returns whether a join of this type may generate NULL values on the
   * right-hand side. // 返回此类型的连接是否可能在右侧生成NULL值
   * 这个方法用于判断连接操作是否会在右表侧产生NULL值
   * 主要用于查询优化器确定是否需要处理NULL值
   * 如果返回true，说明连接结果中右表的列可能包含NULL值
   *
   * @return true if the join may generate NULLs on the right, false otherwise // 如果连接可能在右侧生成NULL值则返回true，否则返回false
   */
  public boolean generatesNullsOnRight() { // 定义公共方法，判断连接是否在右侧生成NULL值
    return (this == LEFT) || (this == FULL); // 返回判断结果：如果是左外连接或全外连接，则会在右侧生成NULL值
  }

  /**
   * Returns whether a join of this type may generate NULL values on the
   * left-hand side. // 返回此类型的连接是否可能在左侧生成NULL值
   * 这个方法用于判断连接操作是否会在左表侧产生NULL值
   * 主要用于查询优化器确定是否需要处理NULL值
   * 如果返回true，说明连接结果中左表的列可能包含NULL值
   *
   * @return true if the join may generate NULLs on the left, false otherwise // 如果连接可能在左侧生成NULL值则返回true，否则返回false
   */
  public boolean generatesNullsOnLeft() { // 定义公共方法，判断连接是否在左侧生成NULL值
    return (this == RIGHT) || (this == FULL); // 返回判断结果：如果是右外连接或全外连接，则会在左侧生成NULL值
  }

} // 枚举类定义结束
