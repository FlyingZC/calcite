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
package org.apache.calcite.rel; // 定义包名，该类属于org.apache.calcite.rel包，是Calcite关系代数框架的核心包

import org.apache.calcite.sql.validate.SqlMonotonicity; // 导入SQL单调性枚举，用于描述值的单调变化趋势

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解，用于标记可能为null的参数或返回值

import java.util.Objects; // 导入Objects工具类，用于equals和hashCode方法

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Definition of the ordering of one field of a {@link RelNode} whose
 * output is to be sorted.
 * 定义RelNode（关系节点）输出中单个字段的排序规则
 * RelNode是Calcite中关系代数表达式的抽象表示，代表一个关系操作
 * 该类描述了关系节点输出中某个字段是如何排序的，包括：
 * 1. 字段索引（fieldIndex）：指定要排序的字段位置
 * 2. 排序方向（direction）：升序、降序、严格升序、严格降序或聚类
 * 3. 空值排序方向（nullDirection）：空值排在最前、最后或未指定
 * 
 * 该类是RelCollation（排序规范）的组成部分，一个RelCollation包含多个RelFieldCollation
 * 例如：ORDER BY emp_id ASC, salary DESC NULLS FIRST
 * 对应两个RelFieldCollation对象：emp_id升序和salary降序且空值在前
 *
 * @see RelCollation // 参见RelCollation类，它包含多个RelFieldCollation的集合
 */
public class RelFieldCollation { // 定义RelFieldCollation类，表示单个字段的排序规则
  /** Utility method that compares values taking into account null
   * direction. */
  // 静态工具方法：比较两个可比较对象的值，考虑空值排序方向
  // 参数c1：第一个可比较对象，可能为null
  // 参数c2：第二个可比较对象，可能为null
  // 参数nullComparison：空值比较的方向，-1表示null小于任何值，1表示null大于任何值
  // 返回值：负数表示c1<c2，0表示c1==c2，正数表示c1>c2
  public static int compare(@Nullable Comparable c1, @Nullable Comparable c2, int nullComparison) { // 定义静态比较方法
    if (c1 == c2) { // 如果两个对象引用相同（包括都为null的情况）
      return 0; // 返回0表示相等
    } else if (c1 == null) { // 如果c1为null而c2不为null
      return nullComparison; // 返回nullComparison，根据nullComparison值决定null是"小"还是"大"
    } else if (c2 == null) { // 如果c2为null而c1不为null
      return -nullComparison; // 返回-nullComparison，与c1为null的情况相反
    } else { // 两个值都不为null
      //noinspection unchecked // 抑制未检查的类型转换警告，因为Comparable的泛型类型无法在编译时确定
      return c1.compareTo(c2); // 调用compareTo方法比较两个非null值
    }
  }

  //~ Enums ------------------------------------------------------------------

  /**
   * Direction that a field is ordered in.
   * 定义字段排序方向的枚举类型
   * 排序方向决定了字段值在排序结果中的排列顺序
   */
  public enum Direction { // 定义Direction枚举，表示排序方向
    /**
     * Ascending direction: A value is always followed by a greater or equal
     * value.
     * 升序方向：一个值后面总是跟着大于或等于它的值
     * 允许重复值，例如：1, 2, 2, 3, 3, 3
     */
    ASCENDING("ASC"), // 定义升序枚举值，简写为"ASC"

    /**
     * Strictly ascending direction: A value is always followed by a greater
     * value.
     * 严格升序方向：一个值后面总是跟着比它大的值
     * 不允许重复值，例如：1, 2, 3, 4, 5
     */
    STRICTLY_ASCENDING("SASC"), // 定义严格升序枚举值，简写为"SASC"

    /**
     * Descending direction: A value is always followed by a lesser or equal
     * value.
     * 降序方向：一个值后面总是跟着小于或等于它的值
     * 允许重复值，例如：5, 4, 4, 3, 2, 2
     */
    DESCENDING("DESC"), // 定义降序枚举值，简写为"DESC"

    /**
     * Strictly descending direction: A value is always followed by a lesser
     * value.
     * 严格降序方向：一个值后面总是跟着比它小的值
     * 不允许重复值，例如：5, 4, 3, 2, 1
     */
    STRICTLY_DESCENDING("SDESC"), // 定义严格降序枚举值，简写为"SDESC"

    /**
     * Clustered direction: Values occur in no particular order, and the
     * same value may occur in contiguous groups, but never occurs after
     * that. This sort order tends to occur when values are ordered
     * according to a hash-key.
     * 聚类方向：值没有特定的排序顺序，但相同的值会连续出现，不会分散
     * 这种排序顺序通常在值按照哈希键排序时出现
     * 例如：3, 3, 3, 1, 1, 2, 2, 2, 2（相同值聚集在一起，但整体无序）
     */
    CLUSTERED("CLU"); // 定义聚类枚举值，简写为"CLU"

    public final String shortString; // 定义枚举的字符串简写，用于输出和调试

    Direction(String shortString) { // 枚举构造方法，接收字符串简写作为参数
      this.shortString = shortString; // 将传入的简写字符串赋值给shortString字段
    }

    /** Converts the direction to a
     * {@link org.apache.calcite.sql.validate.SqlMonotonicity}. */
    // 将排序方向转换为SQL单调性类型
    // 单调性描述了值的趋势，用于查询优化器判断是否可以利用索引等
    // 返回值：对应的SqlMonotonicity枚举值
    public SqlMonotonicity monotonicity() { // 定义方法，将Direction转换为SqlMonotonicity
      switch (this) { // 根据当前枚举值进行分支
      case ASCENDING: // 如果是升序
        return SqlMonotonicity.INCREASING; // 返回递增单调性
      case STRICTLY_ASCENDING: // 如果是严格升序
        return SqlMonotonicity.STRICTLY_INCREASING; // 返回严格递增单调性
      case DESCENDING: // 如果是降序
        return SqlMonotonicity.DECREASING; // 返回递减单调性
      case STRICTLY_DESCENDING: // 如果是严格降序
        return SqlMonotonicity.STRICTLY_DECREASING; // 返回严格递减单调性
      case CLUSTERED: // 如果是聚类
        return SqlMonotonicity.MONOTONIC; // 返回单调性（无特定方向）
      default: // 其他情况（理论上不应发生）
        throw new AssertionError("unknown: " + this); // 抛出断言错误
      }
    }

    /** Converts a {@link SqlMonotonicity} to a direction. */
    // 静态方法：将SQL单调性转换为排序方向
    // 这是monotonicity方法的逆操作
    // 参数monotonicity：SQL单调性枚举值
    // 返回值：对应的Direction枚举值
    public static Direction of(SqlMonotonicity monotonicity) { // 定义静态方法，将SqlMonotonicity转换为Direction
      switch (monotonicity) { // 根据单调性值进行分支
      case INCREASING: // 如果是递增
        return ASCENDING; // 返回升序
      case DECREASING: // 如果是递减
        return DESCENDING; // 返回降序
      case STRICTLY_INCREASING: // 如果是严格递增
        return STRICTLY_ASCENDING; // 返回严格升序
      case STRICTLY_DECREASING: // 如果是严格递减
        return STRICTLY_DESCENDING; // 返回严格降序
      case MONOTONIC: // 如果是单调
        return CLUSTERED; // 返回聚类
      default: // 其他情况（理论上不应发生）
        throw new AssertionError("unknown: " + monotonicity); // 抛出断言错误
      }
    }

    /** Returns the null direction if not specified. Consistent with Oracle,
     * NULLS are sorted as if they were positive infinity. */
    // 返回未指定空值方向时的默认空值排序方向
    // 与Oracle数据库一致，NULL值被排序为正无穷大
    // 返回值：对应的NullDirection枚举值
    public NullDirection defaultNullDirection() { // 定义方法，返回默认的空值排序方向
      switch (this) { // 根据当前排序方向进行分支
      case ASCENDING: // 如果是升序
      case STRICTLY_ASCENDING: // 或者严格升序
        return NullDirection.LAST; // 返回LAST，即空值排在最后（因为NULL被视为正无穷大）
      case DESCENDING: // 如果是降序
      case STRICTLY_DESCENDING: // 或者严格降序
        return NullDirection.FIRST; // 返回FIRST，即空值排在最前
      default: // 其他情况（聚类）
        return NullDirection.UNSPECIFIED; // 返回UNSPECIFIED，即未指定
      }
    }

    /** Returns whether this is {@link #DESCENDING} or
     * {@link #STRICTLY_DESCENDING}. */
    // 判断当前排序方向是否为降序（包括降序和严格降序）
    // 返回值：true表示是降序，false表示不是降序
    public boolean isDescending() { // 定义方法，判断是否为降序
      switch (this) { // 根据当前排序方向进行分支
      case DESCENDING: // 如果是降序
      case STRICTLY_DESCENDING: // 或者严格降序
        return true; // 返回true，表示是降序
      default: // 其他情况
        return false; // 返回false，表示不是降序
      }
    }

    /**
     * Returns the reverse of this direction.
     * 返回当前排序方向的反向方向
     * 例如：升序的反向是降序，严格升序的反向是严格降序
     *
     * @return reverse of the input direction
     * 返回输入方向的相反方向
     */
    public Direction reverse() { // 定义方法，返回反向排序方向
      switch (this) { // 根据当前排序方向进行分支
      case ASCENDING: // 如果是升序
        return DESCENDING; // 返回降序
      case STRICTLY_ASCENDING: // 如果是严格升序
        return STRICTLY_DESCENDING; // 返回严格降序
      case DESCENDING: // 如果是降序
        return ASCENDING; // 返回升序
      case STRICTLY_DESCENDING: // 如果是严格降序
        return STRICTLY_ASCENDING; // 返回严格升序
      default: // 其他情况（聚类）
        return this; // 返回自身（聚类的反向还是聚类）
      }
    }

    /** Removes strictness. */
    // 移除排序方向的严格性，将严格升序/降序转换为普通升序/降序
    // 返回值：去掉严格性后的排序方向
    public Direction lax() { // 定义方法，移除严格性
      switch (this) { // 根据当前排序方向进行分支
      case STRICTLY_ASCENDING: // 如果是严格升序
        return ASCENDING; // 返回普通升序
      case STRICTLY_DESCENDING: // 如果是严格降序
        return DESCENDING; // 返回普通降序
      default: // 其他情况
        return this; // 返回自身
      }
    }
  }

  /**
   * Ordering of nulls.
   * 定义空值排序方向的枚举类型
   * 决定了NULL值在排序结果中的位置
   */
  public enum NullDirection { // 定义NullDirection枚举，表示空值的排序方向
    FIRST(-1), // 空值排在最前，nullComparison值为-1（表示null小于任何非null值）
    LAST(1), // 空值排在最后，nullComparison值为1（表示null大于任何非null值）
    UNSPECIFIED(1); // 未指定空值方向，默认与LAST相同，nullComparison值为1

    public final int nullComparison; // 定义空值比较值，用于compare方法中判断空值的相对大小

    NullDirection(int nullComparison) { // 枚举构造方法，接收nullComparison值作为参数
      this.nullComparison = nullComparison; // 将传入的nullComparison值赋值给nullComparison字段
    }
  }

  //~ Instance fields --------------------------------------------------------

  /**
   * 0-based index of field being sorted.
   * 被排序字段的索引，从0开始
   * 表示在关系节点输出字段列表中的位置
   * 例如：如果字段列表是[emp_id, emp_name, salary]，则salary的索引是2
   */
  private final int fieldIndex; // 定义字段索引，private final表示不可修改

  /**
   * Direction of sorting.
   * 排序方向
   * 可以是升序、降序、严格升序、严格降序或聚类
   */
  public final Direction direction; // 定义排序方向，public final表示外部可访问但不可修改

  /**
   * Direction of sorting of nulls.
   * 空值的排序方向
   * 可以是FIRST（空值最前）、LAST（空值最后）或UNSPECIFIED（未指定）
   */
  public final NullDirection nullDirection; // 定义空值排序方向，public final表示外部可访问但不可修改

  //~ Constructors -----------------------------------------------------------

  /**
   * Creates an ascending field collation.
   * 创建一个升序字段排序规则
   * 空值方向使用默认值（升序时为LAST）
   * 参数fieldIndex：要排序的字段索引（从0开始）
   */
  public RelFieldCollation(int fieldIndex) { // 定义构造方法，只指定字段索引
    this(fieldIndex, Direction.ASCENDING); // 调用三参数构造方法，方向设为升序
  }

  /**
   * Creates a field collation with unspecified null direction.
   * 创建一个字段排序规则，空值方向未指定（使用默认值）
   * 参数fieldIndex：要排序的字段索引（从0开始）
   * 参数direction：排序方向（升序、降序等）
   */
  public RelFieldCollation(int fieldIndex, Direction direction) { // 定义构造方法，指定字段索引和排序方向
    this(fieldIndex, direction, direction.defaultNullDirection()); // 调用三参数构造方法，空值方向使用默认值
  }

  /**
   * Creates a field collation.
   * 创建一个完整的字段排序规则
   * 参数fieldIndex：要排序的字段索引（从0开始）
   * 参数direction：排序方向（升序、降序等）
   * 参数nullDirection：空值排序方向（FIRST、LAST或UNSPECIFIED）
   */
  public RelFieldCollation( // 定义三参数构造方法，完整指定所有排序属性
      int fieldIndex, // 字段索引参数
      Direction direction, // 排序方向参数
      NullDirection nullDirection) { // 空值排序方向参数
    this.fieldIndex = fieldIndex; // 将字段索引参数赋值给实例字段
    this.direction = requireNonNull(direction, "direction"); // 校验direction非空后赋值
    this.nullDirection = requireNonNull(nullDirection, "nullDirection"); // 校验nullDirection非空后赋值
  }

  //~ Methods ----------------------------------------------------------------

  /**
   * Creates a copy of this RelFieldCollation against a different field.
   * 创建一个RelFieldCollation的副本，但应用到不同的字段
   * 保持排序方向和空值方向不变，只改变字段索引
   * 参数fieldIndex：新的字段索引
   * 返回值：新的RelFieldCollation对象，如果字段索引相同则返回this
   */
  public RelFieldCollation withFieldIndex(int fieldIndex) { // 定义方法，创建应用到不同字段的副本
    return this.fieldIndex == fieldIndex ? this // 如果字段索引相同，返回当前对象
        : new RelFieldCollation(fieldIndex, direction, nullDirection); // 否则创建新对象，使用新字段索引
  }

  @Deprecated // to be removed before 2.0 // 标记为已过时，将在2.0版本前移除
  public RelFieldCollation copy(int target) { // 定义方法，复制到目标字段（已过时）
    return withFieldIndex(target); // 调用withFieldIndex方法实现
  }

  /** Creates a copy of this RelFieldCollation with a different direction. */
  // 创建一个RelFieldCollation的副本，使用不同的排序方向
  // 保持字段索引和空值方向不变，只改变排序方向
  // 参数direction：新的排序方向
  // 返回值：新的RelFieldCollation对象，如果排序方向相同则返回this
  public RelFieldCollation withDirection(Direction direction) { // 定义方法，创建使用不同排序方向的副本
    return this.direction == direction ? this // 如果排序方向相同，返回当前对象
        : new RelFieldCollation(fieldIndex, direction, nullDirection); // 否则创建新对象，使用新排序方向
  }

  /** Creates a copy of this RelFieldCollation with a different null
   * direction. */
  // 创建一个RelFieldCollation的副本，使用不同的空值排序方向
  // 保持字段索引和排序方向不变，只改变空值排序方向
  // 参数nullDirection：新的空值排序方向
  // 返回值：新的RelFieldCollation对象，如果空值排序方向相同则返回this
  public RelFieldCollation withNullDirection(NullDirection nullDirection) { // 定义方法，创建使用不同空值方向的副本
    return this.nullDirection == nullDirection ? this // 如果空值方向相同，返回当前对象
        : new RelFieldCollation(fieldIndex, direction, nullDirection); // 否则创建新对象，使用新空值方向
  }

  /**
   * Returns a copy of this RelFieldCollation with the field index shifted
   * {@code offset} to the right.
   * 返回一个RelFieldCollation的副本，字段索引向右偏移指定的偏移量
   * 例如：原字段索引是2，偏移量是1，则新字段索引是3
   * 参数offset：偏移量，可以是正数或负数
   * 返回值：新的RelFieldCollation对象，字段索引已偏移
   */
  public RelFieldCollation shift(int offset) { // 定义方法，偏移字段索引
    return withFieldIndex(fieldIndex + offset); // 调用withFieldIndex，使用偏移后的索引
  }

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法，用于比较两个RelFieldCollation对象是否相等
    return this == o // 如果引用相同，返回true
        || o instanceof RelFieldCollation // 或者如果o是RelFieldCollation类型
        && fieldIndex == ((RelFieldCollation) o).fieldIndex // 并且字段索引相同
        && direction == ((RelFieldCollation) o).direction // 并且排序方向相同
        && nullDirection == ((RelFieldCollation) o).nullDirection; // 并且空值方向相同
  }

  @Override public int hashCode() { // 重写hashCode方法，用于计算对象的哈希值
    return Objects.hash(fieldIndex, direction, nullDirection); // 使用Objects.hash方法计算哈希值
  }

  public int getFieldIndex() { // 定义getter方法，获取字段索引
    return fieldIndex; // 返回字段索引
  }

  public RelFieldCollation.Direction getDirection() { // 定义getter方法，获取排序方向
    return direction; // 返回排序方向
  }

  @Override public String toString() { // 重写toString方法，返回对象的字符串表示
    if (direction == Direction.ASCENDING // 如果是升序
        && nullDirection == direction.defaultNullDirection()) { // 并且空值方向是默认值
      return String.valueOf(fieldIndex); // 直接返回字段索引的字符串形式，例如："2"
    }
    final StringBuilder sb = new StringBuilder(); // 创建StringBuilder用于构建字符串
    sb.append(fieldIndex).append(" ").append(direction.shortString); // 添加字段索引和排序方向简写，例如："2 ASC"
    if (nullDirection != direction.defaultNullDirection()) { // 如果空值方向不是默认值
      sb.append(" ").append(nullDirection); // 添加空值方向，例如："2 ASC FIRST"
    }
    return sb.toString(); // 返回构建的字符串
  }

  public String shortString() { // 定义方法，返回简短的字符串表示
    if (nullDirection == direction.defaultNullDirection()) { // 如果空值方向是默认值
      return direction.shortString; // 只返回排序方向简写，例如："ASC"
    }
    switch (nullDirection) { // 根据空值方向进行分支
    case FIRST: // 如果空值在最前
      return direction.shortString + "-nulls-first"; // 返回例如："ASC-nulls-first"
    case LAST: // 如果空值在最后
      return direction.shortString + "-nulls-last"; // 返回例如："ASC-nulls-last"
    default: // 其他情况
      return direction.shortString; // 返回排序方向简写
    }
  }

  public String fullString() { // 定义方法，返回完整的字符串表示
    switch (nullDirection) { // 根据空值方向进行分支
    case FIRST: // 如果空值在最前
      return direction.shortString + "-nulls-first"; // 返回例如："ASC-nulls-first"
    case LAST: // 如果空值在最后
      return direction.shortString + "-nulls-last"; // 返回例如："ASC-nulls-last"
    default: // 其他情况
      return direction.shortString; // 返回排序方向简写
    }
  }
}
