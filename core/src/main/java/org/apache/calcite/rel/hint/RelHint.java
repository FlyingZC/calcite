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
package org.apache.calcite.rel.hint; // 包声明：org.apache.calcite.rel.hint包，包含关系表达式提示相关的类

import com.google.common.collect.ImmutableList; // 导入Guava的不可变列表类，用于存储不可变的列表数据
import com.google.common.collect.ImmutableMap; // 导入Guava的不可变映射类，用于存储不可变的键值对数据

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性检查注解，用于标记可能为null的参数

import java.util.ArrayList; // 导入Java的动态数组列表类，用于存储可变的列表数据
import java.util.Arrays; // 导入Java的数组工具类，用于数组操作
import java.util.LinkedHashMap; // 导入Java的链式哈希映射类，用于保持插入顺序的键值对存储
import java.util.List; // 导入Java的列表接口，用于列表数据结构
import java.util.Map; // 导入Java的映射接口，用于键值对数据结构
import java.util.Objects; // 导入Java的Objects工具类，用于对象比较和哈希计算

import static com.google.common.base.Preconditions.checkState; // 静态导入Guava的前置条件检查方法，用于状态检查

import static java.util.Objects.requireNonNull; // 静态导入Java的requireNonNull方法，用于非空检查

/**
 * Hint attached to a relation expression.
 * 关系表达式提示类：附加到关系表达式上的提示信息，用于影响查询优化器的行为
 *
 * <p>A hint can be used to: 提示可用于以下场景：
 *
 * <ul>
 *   <li>Enforce planner: there's no perfect planner, so it makes sense to implement hints to
 *   allow user better control the execution. For instance, "never merge this subquery with others",
 *   "treat those tables as leading ones" in the join ordering, etc.
 *   强制优化器：没有完美的优化器，因此实现提示允许用户更好地控制执行是合理的。例如，
 *   "不要将此子查询与其他子查询合并"、"在连接顺序中将这些表视为领先表"等。</li>
 *   <li>Append meta data/statistics: Some statistics like "table index for scan" and
 *   "skew info of some shuffle keys" are somewhat dynamic for the query, it would be very
 *   convenient to config them with hints because our planning metadata from the planner is very
 *   often not that accurate.
 *   附加元数据/统计信息：一些统计信息（如"扫描的表索引"和"某些shuffle键的倾斜信息"）对于查询来说是动态的，
 *   用提示配置它们会很方便，因为来自优化器的规划元数据往往不够准确。</li>
 *   <li>Operator resource constraints: For many cases, we would give a default resource
 *   configuration for the execution operators, i.e. min parallelism or
 *   managed memory (resource consuming UDF) or special resource requirement (GPU or SSD disk)
 *   and so on, it would be very flexible to profile the resource with hints per query
 *   (instead of the Job).
 *   操作符资源约束：在许多情况下，我们会为执行操作符提供默认的资源配置，即最小并行度或
 *   托管内存（资源消耗型UDF）或特殊资源需求（GPU或SSD磁盘）等，
 *   使用提示按查询（而不是作业）配置资源会非常灵活。</li>
 * </ul>
 *
 * <p>In order to support hint override, each hint has a {@code inheritPath} (integers list) to
 * record its propagate path from the root node, number `0` represents the hint was propagated
 * along the first(left) child, number `1` represents the hint was propagated along the
 * second(right) child. Given a relational expression tree with initial attached hints:
 * 为了支持提示覆盖，每个提示都有一个{@code inheritPath}（整数列表）来记录它从根节点开始的传播路径，
 * 数字`0`表示提示是沿着第一个（左）子节点传播的，数字`1`表示提示是沿着第二个（右）子节点传播的。
 * 给定一个带有初始附加提示的关系表达式树：
 *
 * <blockquote><pre>
 *            Filter (Hint1)
 *                |
 *               Join
 *              /    \
 *            Scan  Project (Hint2)
 *                     |
 *                    Scan2
 * </pre></blockquote>
 *
 * <p>The plan would have hints path as follows (assumes each hint can be propagated to all
 * child nodes):
 * 计划将具有以下提示路径（假设每个提示都可以传播到所有子节点）：
 *
 * <blockquote><ul>
 *   <li>Filter &#8594; {Hint1[]}</li>
 *   <li>Join &#8594; {Hint1[0]}</li>
 *   <li>Scan &#8594; {Hint1[0, 0]}</li>
 *   <li>Project &#8594; {Hint1[0,1], Hint2[]}</li>
 *   <li>Scan2 &#8594; {[Hint1[0, 1, 0], Hint2[0]}</li>
 * </ul></blockquote>
 *
 * <p>{@code listOptions} and {@code kvOptions} are supposed to contain the same information,
 * they are mutually exclusive, that means, they can not both be non-empty.
 * {@code listOptions}和{@code kvOptions}应该包含相同的信息，它们是互斥的，这意味着它们不能同时非空。
 *
 * <p>RelHint is immutable. RelHint是不可变类，一旦创建就不能修改
 */
public class RelHint { // 公共类RelHint：表示附加到关系表达式上的提示
  //~ Instance fields -------------------------------------------------------- // 实例字段分隔符注释

  public final ImmutableList<Integer> inheritPath; // 继承路径：不可变整数列表，记录提示从根节点到当前节点的传播路径，0表示左子节点，1表示右子节点
  public final String hintName; // 提示名称：字符串类型，表示提示的名称，用于标识提示类型
  public final List<String> listOptions; // 列表选项：不可变字符串列表，存储提示的选项信息，与kvOptions互斥
  public final Map<String, String> kvOptions; // 键值对选项：不可变字符串映射，存储提示的键值对选项信息，与listOptions互斥

  //~ Constructors ----------------------------------------------------------- // 构造方法分隔符注释

  /**
   * Creates a {@code RelHint}.
   * 创建一个RelHint实例
   *
   * @param inheritPath Hint inherit path 提示的继承路径，记录提示从根节点传播到当前节点的路径
   * @param hintName    Hint name 提示的名称，标识提示的类型
   * @param listOption  Hint options as string list 提示的选项，以字符串列表形式提供，与kvOptions互斥
   * @param kvOptions   Hint options as string key value pair 提示的选项，以键值对形式提供，与listOption互斥
   */
  private RelHint( // 私有构造方法：通过Builder模式创建实例，确保对象不可变
      Iterable<Integer> inheritPath, // 参数：提示的继承路径，可迭代的整数集合
      String hintName, // 参数：提示名称，字符串类型
      @Nullable List<String> listOption, // 参数：列表选项，可能为null的可空字符串列表
      @Nullable Map<String, String> kvOptions) { // 参数：键值对选项，可能为null的可空字符串映射
    this.inheritPath = ImmutableList.copyOf(inheritPath); // 将继承路径转换为不可变列表并赋值，确保不可变性
    this.hintName = requireNonNull(hintName, "hintName"); // 检查提示名称非null，否则抛出NullPointerException
    this.listOptions = listOption == null ? ImmutableList.of() : ImmutableList.copyOf(listOption); // 如果listOption为null则创建空列表，否则转换为不可变列表
    this.kvOptions = kvOptions == null ? ImmutableMap.of() : ImmutableMap.copyOf(kvOptions); // 如果kvOptions为null则创建空映射，否则转换为不可变映射
  }

  //~ Methods ---------------------------------------------------------------- // 方法分隔符注释

  /** Creates a hint builder with specified hint name. 使用指定的提示名称创建一个提示构建器 */
  public static Builder builder(String hintName) { // 静态工厂方法：创建Builder实例
    return new Builder(hintName); // 返回一个新的Builder对象，传入提示名称
  }

  /**
   * Returns a copy of this hint with specified inherit path.
   * 返回具有指定继承路径的此提示的副本
   *
   * @param inheritPath Hint path 新的提示路径，整数列表
   * @return the new {@code RelHint} 返回新的RelHint对象，具有相同的提示名称和选项，但继承路径不同
   */
  public RelHint copy(List<Integer> inheritPath) { // 方法：创建提示的副本，修改继承路径
    requireNonNull(inheritPath, "inheritPath"); // 检查继承路径非null，否则抛出NullPointerException
    return new RelHint(inheritPath, hintName, listOptions, kvOptions); // 返回新的RelHint对象，使用新的继承路径和原有的其他属性
  }

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法：判断两个RelHint对象是否相等
    if (this == o) { // 如果是同一个对象引用
      return true; // 直接返回true
    }
    if (o == null || getClass() != o.getClass()) { // 如果对象为null或类类型不同
      return false; // 返回false
    }
    RelHint hint = (RelHint) o; // 将对象转换为RelHint类型
    return inheritPath.equals(hint.inheritPath) // 比较继承路径是否相等
        && hintName.equals(hint.hintName) // 比较提示名称是否相等
        && Objects.equals(listOptions, hint.listOptions) // 比较列表选项是否相等
        && Objects.equals(kvOptions, hint.kvOptions); // 比较键值对选项是否相等
  }

  @Override public int hashCode() { // 重写hashCode方法：计算对象的哈希码
    return Objects.hash(this.hintName, this.inheritPath, // 使用Objects.hash方法基于提示名称、继承路径
        this.listOptions, this.kvOptions); // 列表选项和键值对选项计算哈希码
  }

  @Override public String toString() { // 重写toString方法：返回对象的字符串表示
    StringBuilder builder = new StringBuilder(); // 创建字符串构建器
    builder.append("[") // 添加左方括号
        .append(this.hintName) // 添加提示名称
        .append(" inheritPath:") // 添加继承路径标签
        .append(this.inheritPath); // 添加继承路径值
    if (!this.listOptions.isEmpty() || !this.kvOptions.isEmpty()) { // 如果列表选项或键值对选项不为空
      builder.append(" options:"); // 添加选项标签
      if (!this.listOptions.isEmpty()) { // 如果列表选项不为空
        builder.append(this.listOptions); // 添加列表选项值
      } else { // 如果键值对选项不为空
        builder.append(this.kvOptions); // 添加键值对选项值
      }
    }
    builder.append("]"); // 添加右方括号
    return builder.toString(); // 返回构建的字符串
  }

  //~ Inner Class ------------------------------------------------------------ // 内部类分隔符注释

  /** Builder for {@link RelHint}. RelHint的构建器类，用于构建RelHint实例 */
  public static class Builder { // 公共静态内部类Builder：构建器模式，用于创建RelHint对象
    private final String hintName; // 提示名称：final字段，在构造时初始化，不可修改
    private List<Integer> inheritPath; // 继承路径：整数列表，用于存储提示的传播路径

    private List<String> listOptions; // 列表选项：字符串列表，用于存储提示的列表形式选项
    private Map<String, String> kvOptions; // 键值对选项：字符串映射，用于存储提示的键值对形式选项

    private Builder(String hintName) { // 私有构造方法：创建Builder实例
      this.listOptions = new ArrayList<>(); // 初始化列表选项为空数组列表
      this.kvOptions = new LinkedHashMap<>(); // 初始化键值对选项为空链式哈希映射，保持插入顺序
      this.hintName = hintName; // 设置提示名称
      this.inheritPath = ImmutableList.of(); // 初始化继承路径为空不可变列表
    }

    /** Sets up the inherit path with given integer list. 使用给定的整数列表设置继承路径 */
    public Builder inheritPath(Iterable<Integer> inheritPath) { // 方法：设置继承路径
      this.inheritPath = ImmutableList.copyOf(inheritPath); // 将传入的路径转换为不可变列表并赋值
      return this; // 返回当前Builder对象，支持链式调用
    }

    /** Sets up the inherit path with given integer array. 使用给定的整数数组设置继承路径 */
    public Builder inheritPath(Integer... inheritPath) { // 方法：设置继承路径，可变参数
      this.inheritPath = Arrays.asList(inheritPath); // 将数组转换为列表并赋值
      return this; // 返回当前Builder对象，支持链式调用
    }

    /** Add a hint option as string. 添加一个字符串形式的提示选项 */
    public Builder hintOption(String hintOption) { // 方法：添加单个字符串选项
      requireNonNull(hintOption, "hintOption"); // 检查选项非null，否则抛出NullPointerException
      checkState(this.kvOptions.isEmpty(), // 检查键值对选项是否为空
          "List options and key value options can not be mixed in"); // 如果不为空则抛出IllegalStateException，因为两种选项类型不能混用
      this.listOptions.add(hintOption); // 将选项添加到列表选项中
      return this; // 返回当前Builder对象，支持链式调用
    }

    /** Add multiple string hint options. 添加多个字符串形式的提示选项 */
    public Builder hintOptions(Iterable<String> hintOptions) { // 方法：添加多个字符串选项
      requireNonNull(hintOptions, "hintOptions"); // 检查选项集合非null，否则抛出NullPointerException
      checkState(this.kvOptions.isEmpty(), // 检查键值对选项是否为空
          "List options and key value options can not be mixed in"); // 如果不为空则抛出IllegalStateException，因为两种选项类型不能混用
      this.listOptions = ImmutableList.copyOf(hintOptions); // 将选项集合转换为不可变列表并赋值
      return this; // 返回当前Builder对象，支持链式调用
    }

    /** Add a hint option as string key-value pair. 添加一个键值对形式的提示选项 */
    public Builder hintOption(String optionKey, String optionValue) { // 方法：添加单个键值对选项
      requireNonNull(optionKey, "optionKey"); // 检查选项键非null，否则抛出NullPointerException
      requireNonNull(optionValue, "optionValue"); // 检查选项值非null，否则抛出NullPointerException
      checkState(this.listOptions.isEmpty(), // 检查列表选项是否为空
          "List options and key value options can not be mixed in"); // 如果不为空则抛出IllegalStateException，因为两种选项类型不能混用
      this.kvOptions.put(optionKey, optionValue); // 将键值对添加到键值对选项映射中
      return this; // 返回当前Builder对象，支持链式调用
    }

    /** Add multiple string key-value pair hint options. 添加多个键值对形式的提示选项 */
    public Builder hintOptions(Map<String, String> kvOptions) { // 方法：添加多个键值对选项
      requireNonNull(kvOptions, "kvOptions"); // 检查键值对映射非null，否则抛出NullPointerException
      checkState(this.listOptions.isEmpty(), // 检查列表选项是否为空
          "List options and key value options can not be mixed in"); // 如果不为空则抛出IllegalStateException，因为两种选项类型不能混用
      this.kvOptions = kvOptions; // 直接赋值键值对选项映射
      return this; // 返回当前Builder对象，支持链式调用
    }

    public RelHint build() { // 方法：构建RelHint对象
      return new RelHint(this.inheritPath, this.hintName, this.listOptions, this.kvOptions); // 使用当前Builder的所有属性创建新的RelHint实例并返回
    }
  }
}