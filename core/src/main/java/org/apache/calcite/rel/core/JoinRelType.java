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
package org.apache.calcite.rel.core; // 定义包名，该类位于org.apache.calcite.rel.core包中，属于Calcite关系表达式核心模块

import org.apiguardian.api.API; // 导入API注解类，用于标记API的稳定性和版本信息

import java.util.Locale; // 导入Locale类，用于处理本地化相关的字符串转换，这里用于将枚举名称转换为小写

/**
 * Enumeration of join types.
 * JoinRelType是一个枚举类，用于表示关系代数中不同类型的连接操作
 * 连接操作是SQL查询中最核心的操作之一，用于将两个或多个表中的行根据某种条件组合在一起
 * 该枚举定义了Calcite支持的所有连接类型，包括内连接、外连接、半连接、反连接以及ASOF连接等
 * 每种连接类型都有不同的语义和结果集生成规则，理解这些类型对于SQL查询优化和执行至关重要
 */
public enum JoinRelType { // 定义JoinRelType枚举类，枚举类型保证了连接类型的类型安全和预定义性
  /**
   * Inner join.
   * 内连接：最常用的连接类型，只返回两个表中满足连接条件的行
   * 只有当左表和右表的行在连接条件上匹配时，才会出现在结果集中
   * 不匹配的行会被过滤掉，不会出现在结果中
   * 例如：SELECT * FROM EMP INNER JOIN DEPT ON EMP.DEPTNO = DEPT.DEPTNO
   * 只返回有对应部门的员工记录
   */
  INNER, // 内连接枚举常量

  /**
   * Left-outer join.
   * 左外连接：返回左表的所有行，以及右表中满足连接条件的行
   * 如果左表的某一行在右表中没有匹配的行，则结果集中右表的列显示为NULL
   * 左表是"保留表"，右表是"可选表"
   * 例如：SELECT * FROM EMP LEFT OUTER JOIN DEPT ON EMP.DEPTNO = DEPT.DEPTNO
   * 即使员工没有对应部门，也会返回员工记录，部门信息为NULL
   */
  LEFT, // 左外连接枚举常量

  /**
   * Right-outer join.
   * 右外连接：返回右表的所有行，以及左表中满足连接条件的行
   * 如果右表的某一行在左表中没有匹配的行，则结果集中左表的列显示为NULL
   * 右表是"保留表"，左表是"可选表"
   * 例如：SELECT * FROM EMP RIGHT OUTER JOIN DEPT ON EMP.DEPTNO = DEPT.DEPTNO
   * 即使部门没有员工，也会返回部门记录，员工信息为NULL
   */
  RIGHT, // 右外连接枚举常量

  /**
   * Full-outer join.
   * 全外连接：返回左表和右表的所有行
   * 当某一行在另一个表中没有匹配时，结果集中对应表的列显示为NULL
   * 全外连接是左外连接和右外连接的并集
   * 例如：SELECT * FROM EMP FULL OUTER JOIN DEPT ON EMP.DEPTNO = DEPT.DEPTNO
   * 返回所有员工和所有部门，无论是否有匹配关系
   */
  FULL, // 全外连接枚举常量

  /**
   * Semi-join.
   * 半连接：只返回左表中满足连接条件的行，但不包含右表的列
   * 半连接用于判断左表的行是否在右表中存在匹配，类似于EXISTS子查询
   * 结果集只包含左表的列，右表的列不会出现在输出中
   * 
   * <p>For example, {@code EMP semi-join DEPT} finds all {@code EMP} records
   * that have a corresponding {@code DEPT} record:
   * 例如：EMP表与DEPT表进行半连接，找出所有在DEPT表中有对应记录的EMP记录
   * 
   * <blockquote><pre>
   * SELECT * FROM EMP
   * WHERE EXISTS (SELECT 1 FROM DEPT
   *     WHERE DEPT.DEPTNO = EMP.DEPTNO)</pre>
   * </blockquote>
   * 等价SQL：查询所有有对应部门的员工，但只返回员工信息，不包含部门信息
   * 半连接常用于子查询优化，可以减少数据传输量
   */
  SEMI, // 半连接枚举常量

  /**
   * Anti-join (also known as Anti-semi-join).
   * 反连接：只返回左表中不满足连接条件的行，不包含右表的列
   * 反连接用于判断左表的行是否在右表中不存在匹配，类似于NOT EXISTS子查询
   * 结果集只包含左表的列，右表的列不会出现在输出中
   * 
   * <p>For example, {@code EMP anti-join DEPT} finds all {@code EMP} records
   * that do not have a corresponding {@code DEPT} record:
   * 例如：EMP表与DEPT表进行反连接，找出所有在DEPT表中没有对应记录的EMP记录
   * 
   * <blockquote><pre>
   * SELECT * FROM EMP
   * WHERE NOT EXISTS (SELECT 1 FROM DEPT
   *     WHERE DEPT.DEPTNO = EMP.DEPTNO)</pre>
   * </blockquote>
   * 等价SQL：查询所有没有对应部门的员工，只返回员工信息，不包含部门信息
   * 反连接常用于查找不满足某种条件的记录
   */
  ANTI, // 反连接枚举常量

  /**
   * An ASOF JOIN operation combines rows from two tables based on comparable timestamp values.
   * ASOF连接是一种特殊的连接操作，基于时间戳值将两个表的行进行组合
   * 对于左表中的每一行，在右表中找到最多一行具有"最接近"时间戳值的行
   * 右表中的匹配行是最接近的匹配，可以根据比较操作符指定是小于等于还是大于等于时间戳列
   * ASOF连接常用于时间序列数据的处理，例如将事件数据与状态数据在时间维度上对齐
   * 
   * <p>Example:
   * 示例：
   * <blockquote><pre>
   * FROM left_table ASOF JOIN right_table
   *   MATCH_CONDITION ( left_table.timecol &le; right_table.timecol )
   *   ON left_table.col = right_table.col</pre>
   * </blockquote>
   * 说明：从左表ASOF连接右表，匹配条件是左表的时间列小于等于右表的时间列
   * 对于左表的每一行，在右表中找到时间戳最接近且满足匹配条件的行
   */
  ASOF, // ASOF连接枚举常量

  /**
   * The left version of an ASOF join, where each row from the left table is part of the output.
   * ASOF连接的左版本，左表的每一行都会出现在输出结果中
   * 这意味着即使右表中没有找到匹配的行，左表的行也会保留，右表列显示为NULL
   * LEFT_ASOF类似于LEFT OUTER JOIN的语义，但结合了ASOF的时间匹配特性
   * 这种连接类型确保左表的完整性，适用于必须保留左表所有记录的场景
   */
  LEFT_ASOF; // 左ASOF连接枚举常量

  /** Lower-case name. 
   * lowerName是一个公共的最终字符串成员变量，存储该枚举常量的名称的小写形式
   * 例如：INNER的lowerName为"inner"，LEFT的lowerName为"left"
   * 使用Locale.ROOT确保在不同语言环境下都生成相同的小写名称
   * 该变量在枚举常量创建时自动初始化，用于在需要小写名称的场景中使用
   * 例如在SQL生成、日志输出等场景中，小写名称更符合SQL标准
   */
  public final String lowerName = name().toLowerCase(Locale.ROOT); // 初始化lowerName，将枚举名称转换为小写

  /**
   * Returns whether a join of this type may generate NULL values on the
   * right-hand side.
   * 判断该连接类型是否可能在右侧生成NULL值
   * 右侧生成NULL意味着右表的某些行可能不满足连接条件，但仍出现在结果集中
   * 这通常发生在左外连接、全外连接等保留左表所有行的连接类型中
   * 
   * @return 如果该连接类型可能在右侧生成NULL值则返回true，否则返回false
   */
  public boolean generatesNullsOnRight() { // 定义方法，判断是否在右侧生成NULL
    return (this == LEFT) || (this == FULL) || (this == LEFT_ASOF); // 左外连接、全外连接、左ASOF连接都会在右侧生成NULL
  }

  /**
   * Returns whether a join of this type may generate NULL values on the
   * left-hand side.
   * 判断该连接类型是否可能在左侧生成NULL值
   * 左侧生成NULL意味着左表的某些行可能不满足连接条件，但仍出现在结果集中
   * 这通常发生在右外连接、全外连接等保留右表所有行的连接类型中
   * 
   * @return 如果该连接类型可能在左侧生成NULL值则返回true，否则返回false
   */
  public boolean generatesNullsOnLeft() { // 定义方法，判断是否在左侧生成NULL
    return (this == RIGHT) || (this == FULL); // 右外连接和全外连接都会在左侧生成NULL
  }

  /**
   * Returns whether a join of this type is an outer join, returns true if the join type may
   * generate NULL values, either on the left-hand side or right-hand side.
   * 判断该连接类型是否为外连接
   * 外连接的定义是：可能在左侧或右侧生成NULL值的连接类型
   * 外连接包括左外连接、右外连接、全外连接等
   * 内连接不会生成NULL值，因此不是外连接
   * 
   * @return 如果该连接类型是外连接则返回true，否则返回false
   */
  public boolean isOuterJoin() { // 定义方法，判断是否为外连接
    return (this == LEFT) || (this == RIGHT) || (this == FULL) || (this == LEFT_ASOF); // 左外、右外、全外、左ASOF都是外连接
  }

  /**
   * Swaps left to right, and vice versa.
   * 交换左右表的位置，返回对应的连接类型
   * 例如：左外连接交换后变为右外连接，右外连接交换后变为左外连接
   * 内连接、全外连接等对称的连接类型交换后保持不变
   * 该方法在查询优化器中用于连接顺序的重新排列和优化
   * 
   * @return 交换左右表位置后的连接类型
   */
  public JoinRelType swap() { // 定义方法，交换左右表位置
    switch (this) { // 使用switch语句根据当前连接类型进行判断
    case LEFT: // 如果是左外连接
      return RIGHT; // 交换后变为右外连接
    case RIGHT: // 如果是右外连接
      return LEFT; // 交换后变为左外连接
    default: // 其他情况（如INNER、FULL等）
      return this; // 返回原连接类型，因为它们是对称的
    }
  }

  /** Returns whether this join type generates nulls on side #{@code i}. 
   * 判断该连接类型是否在指定的一侧生成NULL值
   * 参数i表示侧：0表示左侧，1表示右侧
   * 该方法提供了一个统一的接口来查询任意一侧是否生成NULL
   * 
   * @param i 侧标识，0表示左侧，1表示右侧
   * @return 如果在指定侧生成NULL值则返回true，否则返回false
   * @throws IllegalArgumentException 如果i不是0或1则抛出异常
   */
  public boolean generatesNullsOn(int i) { // 定义方法，判断在指定侧是否生成NULL
    switch (i) { // 使用switch语句根据侧标识进行判断
    case 0: // 如果是左侧（i=0）
      return generatesNullsOnLeft(); // 调用左侧NULL生成判断方法
    case 1: // 如果是右侧（i=1）
      return generatesNullsOnRight(); // 调用右侧NULL生成判断方法
    default: // 其他情况
      throw new IllegalArgumentException("invalid: " + i); // 抛出非法参数异常
    }
  }

  /** Returns a join type similar to this but that does not generate nulls on
   * the left. 
   * 返回一个类似的连接类型，但不生成左侧NULL值
   * 该方法用于将连接类型转换为更严格的类型，消除左侧的NULL生成
   * 例如：全外连接消除左侧NULL后变为左外连接
   * 查询优化器可以使用此方法来简化连接操作
   * 
   * @return 不生成左侧NULL值的连接类型
   */
  public JoinRelType cancelNullsOnLeft() { // 定义方法，取消左侧NULL生成
    switch (this) { // 使用switch语句根据当前连接类型进行判断
    case RIGHT: // 如果是右外连接
      return INNER; // 取消左侧NULL后变为内连接
    case FULL: // 如果是全外连接
      return LEFT; // 取消左侧NULL后变为左外连接
    default: // 其他情况
      return this; // 返回原连接类型
    }
  }

  /** Returns a join type similar to this but that does not generate nulls on
   * the right. 
   * 返回一个类似的连接类型，但不生成右侧NULL值
   * 该方法用于将连接类型转换为更严格的类型，消除右侧的NULL生成
   * 例如：全外连接消除右侧NULL后变为右外连接
   * 查询优化器可以使用此方法来简化连接操作
   * 
   * @return 不生成右侧NULL值的连接类型
   */
  public JoinRelType cancelNullsOnRight() { // 定义方法，取消右侧NULL生成
    switch (this) { // 使用switch语句根据当前连接类型进行判断
    case LEFT: // 如果是左外连接
      return INNER; // 取消右侧NULL后变为内连接
    case FULL: // 如果是全外连接
      return RIGHT; // 取消右侧NULL后变为右外连接
    default: // 其他情况
      return this; // 返回原连接类型
    }
  }

  public boolean projectsRight() { // 定义方法，判断是否投影右表（输出右表的列）
    return this != SEMI && this != ANTI; // 半连接和反连接不投影右表，其他连接类型都投影右表
  }

  /** Returns whether this join type accepts pushing predicates from above into its predicate. 
   * 判断该连接类型是否接受从上方将谓词下推到连接谓词中
   * 从上方下推是指将连接操作上方的过滤条件推入到连接条件中
   * 只有内连接和半连接支持这种优化，因为它们不会改变结果的语义
   * 外连接中下推谓词可能会影响NULL值的生成，因此不支持
   * 
   * @return 如果支持从上方下推谓词到连接谓词则返回true，否则返回false
   */
  @API(since = "1.28", status = API.Status.EXPERIMENTAL) // API注解，标记该方法从1.28版本开始，处于实验状态
  public boolean canPushIntoFromAbove() { // 定义方法，判断是否支持从上方下推到连接谓词
    return (this == INNER) || (this == SEMI); // 只有内连接和半连接支持这种优化
  }

  /** Returns whether this join type accepts pushing predicates from above into its left input. 
   * 判断该连接类型是否接受从上方将谓词下推到左输入中
   * 从上方下推到左输入是指将连接操作上方的过滤条件推入到左表扫描中
   * 内连接、左外连接、半连接、反连接都支持这种优化
   * 右外连接不支持，因为左表的所有行都需要保留
   * 
   * @return 如果支持从上方下推谓词到左输入则返回true，否则返回false
   */
  @API(since = "1.28", status = API.Status.EXPERIMENTAL) // API注解，标记该方法从1.28版本开始，处于实验状态
  public boolean canPushLeftFromAbove() { // 定义方法，判断是否支持从上方下推到左输入
    return (this == INNER) || (this == LEFT) || (this == SEMI) || (this == ANTI); // 内连接、左外连接、半连接、反连接都支持
  }

  /** Returns whether this join type accepts pushing predicates from above into its right input. 
   * 判断该连接类型是否接受从上方将谓词下推到右输入中
   * 从上方下推到右输入是指将连接操作上方的过滤条件推入到右表扫描中
   * 只有内连接和右外连接支持这种优化
   * 左外连接不支持，因为右表的所有行都需要保留（除了满足条件的行）
   * 
   * @return 如果支持从上方下推谓词到右输入则返回true，否则返回false
   */
  @API(since = "1.28", status = API.Status.EXPERIMENTAL) // API注解，标记该方法从1.28版本开始，处于实验状态
  public boolean canPushRightFromAbove() { // 定义方法，判断是否支持从上方下推到右输入
    return (this == INNER) || (this == RIGHT); // 只有内连接和右外连接支持
  }

  /** Returns whether this join type accepts pushing predicates from within into its left input. 
   * 判断该连接类型是否接受从内部将谓词下推到左输入中
   * 从内部下推是指将连接条件中只涉及左表列的谓词提取出来推入到左表扫描中
   * 内连接、右外连接、半连接都支持这种优化
   * 左外连接不支持，因为可能会影响结果的完整性
   * 
   * @return 如果支持从内部下推谓词到左输入则返回true，否则返回false
   */
  @API(since = "1.28", status = API.Status.EXPERIMENTAL) // API注解，标记该方法从1.28版本开始，处于实验状态
  public boolean canPushLeftFromWithin() { // 定义方法，判断是否支持从内部下推到左输入
    return (this == INNER) || (this == RIGHT) || (this == SEMI); // 内连接、右外连接、半连接都支持
  }

  /** Returns whether this join type accepts pushing predicates from within into its right input. 
   * 判断该连接类型是否接受从内部将谓词下推到右输入中
   * 从内部下推是指将连接条件中只涉及右表列的谓词提取出来推入到右表扫描中
   * 内连接、左外连接、半连接都支持这种优化
   * 右外连接不支持，因为可能会影响结果的完整性
   * 
   * @return 如果支持从内部下推谓词到右输入则返回true，否则返回false
   */
  @API(since = "1.28", status = API.Status.EXPERIMENTAL) // API注解，标记该方法从1.28版本开始，处于实验状态
  public boolean canPushRightFromWithin() { // 定义方法，判断是否支持从内部下推到右输入
    return (this == INNER) || (this == LEFT) || (this == SEMI); // 内连接、左外连接、半连接都支持
  }
} // 枚举类结束
