// Apache License 许可证头部声明，说明代码遵循 Apache 2.0 开源许可
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
// 声明包名，此类属于 org.apache.calcite.linq4j 包，是 Calcite LINQ4J 模块的一部分
package org.apache.calcite.linq4j;

// 导入 Google Guava 库的 ImmutableList 类，用于创建不可变列表
import com.google.common.collect.ImmutableList;

// 导入 Checker Framework 的 @Nullable 注解，用于标记可能为 null 的参数
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入 Java 集合框架的 AbstractList 抽象类，作为 OrdList 等内部类的基类
import java.util.AbstractList;
// 导入 Java 集合框架的 Iterator 接口，用于创建迭代器
import java.util.Iterator;
// 导入 Java 集合框架的 List 接口，用于创建列表视图
import java.util.List;
// 导入 Java 集合框架的 Map 接口，Ord 类实现了 Map.Entry 接口
import java.util.Map;
// 导入 Java 工具类的 Objects 类，用于 equals 和 hashCode 方法
import java.util.Objects;
// 导入 Java 集合框架的 RandomAccess 标记接口，用于标记支持快速随机访问的列表
import java.util.RandomAccess;
// 导入 Java 函数式接口 ObjIntConsumer，用于接收对象和索引的操作
import java.util.function.ObjIntConsumer;

/**
 * Pair of an element and an ordinal. // Ord 类是一个包含元素和序号的键值对
 * // 这个类在 LINQ4J 框架中非常重要，它允许在遍历集合时同时访问元素和其索引位置
 * // 实现了 Map.Entry<Integer, E> 接口，使得 Ord 对象可以像 Map.Entry 一样使用
 * // 泛型参数 E 表示元素的类型，可以是任何 Java 对象类型
 *
 * @param <E> Element type // 泛型参数，表示元素的类型
 */
public class Ord<E> implements Map.Entry<Integer, E> {
  // 成员变量 i：表示序号（索引），从 0 开始计数，final 修饰表示一旦赋值就不能改变
  // 这个序号通常对应元素在集合中的位置，用于在遍历时追踪元素的索引
  public final int i;
  // 成员变量 e：表示元素本身，类型为泛型 E，final 修饰表示一旦赋值就不能改变
  // 这个元素可以是任何 Java 对象，存储了实际的数据内容
  public final E e;

  /**
   * Creates an Ord. // 构造方法：创建一个新的 Ord 对象
   */
  public Ord(int i, E e) {
    // 将传入的序号参数 i 赋值给成员变量 i，保存元素的索引位置
    this.i = i;
    // 将传入的元素参数 e 赋值给成员变量 e，保存元素本身
    this.e = e;
  }

  /**
   * Creates an Ord. // 静态工厂方法：创建一个新的 Ord 对象
   * // 这是一个便捷的静态工厂方法，使用 of 命名符合 Java 的函数式编程风格
   * // 与构造方法功能相同，但提供了更简洁的调用方式
   */
  public static <E> Ord<E> of(int n, E e) {
    // 调用构造方法创建并返回一个新的 Ord 对象，序号为 n，元素为 e
    return new Ord<>(n, e);
  }

  // 重写 hashCode 方法：计算 Ord 对象的哈希码
  // 这个方法对于将 Ord 对象用作 HashMap 的键时的性能和正确性至关重要
  @Override public int hashCode() {
    // 使用 Objects.hash 方法计算元素的哈希码和序号的哈希码的组合哈希值
    // 这样可以确保相等的 Ord 对象具有相同的哈希码
    return Objects.hash(e, i);
  }

  // 重写 equals 方法：判断两个 Ord 对象是否相等
  // 这个方法对于正确比较 Ord 对象非常重要，特别是在集合操作中
  @Override public boolean equals(@Nullable Object obj) {
    // 首先检查是否是同一个对象引用，如果是则直接返回 true
    return this == obj
        // 如果不是同一个对象，检查 obj 是否是 Ord 类的实例
        || obj instanceof Ord
        // 检查序号 i 是否相等
        && i == ((Ord<?>) obj).i
        // 检查元素 e 是否相等，使用 Objects.equals 方法可以正确处理 null 值
        && Objects.equals(e, ((Ord<?>) obj).e);
  }

  /**
   * Creates an iterable of {@code Ord}s over an iterable. // 静态方法：将一个 Iterable 转换为 Ord 的 Iterable
   * // 这个方法允许在遍历任何 Iterable 时同时访问元素及其索引
   * // 返回的 Iterable 会为原始集合中的每个元素创建一个包含索引的 Ord 对象
   */
  public static <E> Iterable<Ord<E>> zip(final Iterable<? extends E> iterable) {
    // 返回一个匿名 Iterable，其 iterator() 方法调用 zip(Iterator) 方法
    // 这样可以将 Iterable 转换为 Iterator，然后包装成 Ord 的 Iterator
    return () -> zip(iterable.iterator());
  }

  /**
   * Creates an iterator of {@code Ord}s over an iterator. // 静态方法：将一个 Iterator 转换为 Ord 的 Iterator
   * // 这个方法允许在遍历任何 Iterator 时同时访问元素及其索引
   * // 返回的 Iterator 会为原始迭代器中的每个元素创建一个包含索引的 Ord 对象
   */
  public static <E> Iterator<Ord<E>> zip(final Iterator<? extends E> iterator) {
    // 返回一个匿名 Iterator 实现类，包装原始的 Iterator
    return new Iterator<Ord<E>>() {
      // 内部计数器 n，从 0 开始，用于跟踪当前元素的索引位置
      int n = 0;

      // 实现 hasNext() 方法：检查是否还有下一个元素
      @Override public boolean hasNext() {
        // 委托给原始迭代器的 hasNext() 方法
        return iterator.hasNext();
      }

      // 实现 next() 方法：获取下一个 Ord 对象
      @Override public Ord<E> next() {
        // 创建并返回一个新的 Ord 对象，序号为当前 n 值，元素为原始迭代器的下一个元素
        // 使用后置递增 n++，确保下次调用时序号会自动增加
        return Ord.of(n++, iterator.next());
      }

      // 实现 remove() 方法：移除当前元素
      @Override public void remove() {
        // 委托给原始迭代器的 remove() 方法
        iterator.remove();
      }
    };
  }

  /**
   * Returns a numbered list based on an array. // 静态方法：将数组转换为编号的 Ord 列表
   * // 这个方法允许将数组转换为一个 List<Ord<E>>，其中每个 Ord 包含元素的索引和元素本身
   * // 返回的列表是随机访问的，性能更好
   */
  public static <E> List<Ord<E>> zip(final E[] elements) {
    // 创建并返回一个 OrdArrayList 对象，该对象包装了原始数组
    // OrdArrayList 是一个基于数组的 List<Ord<E>> 实现
    return new OrdArrayList<>(elements);
  }

  /**
   * Returns a numbered list. // 静态方法：将 List 转换为编号的 Ord 列表
   * // 这个方法允许将 List 转换为一个 List<Ord<E>>，其中每个 Ord 包含元素的索引和元素本身
   * // 根据原始 List 是否实现 RandomAccess 接口，选择不同的实现以优化性能
   */
  public static <E> List<Ord<E>> zip(final List<? extends E> elements) {
    // 检查原始 List 是否实现了 RandomAccess 接口（如 ArrayList）
    // RandomAccess 接口表示该 List 支持快速随机访问（get 操作是 O(1) 时间复杂度）
    return elements instanceof RandomAccess
        // 如果实现了 RandomAccess，使用 OrdRandomAccessList，它优化了随机访问性能
        ? new OrdRandomAccessList<>(elements)
        // 如果没有实现 RandomAccess，使用 OrdList，它使用普通的 List 访问方式
        : new OrdList<>(elements);
  }

  /**
   * Iterates over an array in reverse order. // 静态方法：反向遍历数组
   * // 这个方法允许从最后一个元素开始，向前遍历数组，同时访问元素的索引和元素本身
   * // 对于数组 ["a", "b", "c"]，会依次返回 (2, "c"), (1, "b"), (0, "a")
   *
   * <p>Given the array ["a", "b", "c"], returns (2, "c") then (1, "b") then
   * (0, "a"). // 示例：给定数组 ["a", "b", "c"]，返回 (2, "c") 然后 (1, "b") 然后 (0, "a")
   */
  @SafeVarargs // heap pollution is not possible because we only read // @SafeVarargs 注解表示泛型数组参数是安全的，因为我们只读取不写入
  public static <E> Iterable<Ord<E>> reverse(E... elements) {
    // 将可变参数数组转换为不可变的 ImmutableList，然后调用另一个 reverse 方法
    // ImmutableList.copyOf 创建一个不可变的列表副本，确保线程安全
    return reverse(ImmutableList.copyOf(elements));
  }

  /**
   * Iterates over a list in reverse order. // 静态方法：反向遍历 Iterable
   * // 这个方法允许从最后一个元素开始，向前遍历任何 Iterable，同时访问元素的索引和元素本身
   * // 对于列表 ["a", "b", "c"]，会依次返回 (2, "c"), (1, "b"), (0, "a")
   *
   * <p>Given the list ["a", "b", "c"], returns (2, "c") then (1, "b") then
   * (0, "a"). // 示例：给定列表 ["a", "b", "c"]，返回 (2, "c") 然后 (1, "b") 然后 (0, "a")
   */
  public static <E> Iterable<Ord<E>> reverse(Iterable<? extends E> elements) {
    // 将 Iterable 转换为不可变的 ImmutableList，这样可以多次遍历且保证线程安全
    final ImmutableList<E> elementList = ImmutableList.copyOf(elements);
    // 返回一个匿名 Iterable，其 iterator() 方法创建一个反向迭代器
    return () -> new Iterator<Ord<E>>() {
      // 初始化索引 i 为列表的最后一个元素的索引（size - 1）
      int i = elementList.size() - 1;

      // 实现 hasNext() 方法：检查是否还有前一个元素
      @Override public boolean hasNext() {
        // 当索引 i 大于等于 0 时，表示还有元素可以访问
        return i >= 0;
      }

      // 实现 next() 方法：获取前一个 Ord 对象
      @Override public Ord<E> next() {
        // 创建并返回一个新的 Ord 对象，序号为当前 i 值，元素为列表中索引 i 的元素
        // 使用后置递减 i--，确保下次调用时索引会向前移动
        return Ord.of(i, elementList.get(i--));
      }
    };
  }

  // 实现 Map.Entry 接口的 getKey() 方法：返回键（即序号）
  // 因为 Ord 实现了 Map.Entry<Integer, E> 接口，所以必须提供此方法
  @Override public Integer getKey() {
    // 返回序号 i，作为 Map.Entry 的键
    return i;
  }

  // 实现 Map.Entry 接口的 getValue() 方法：返回值（即元素）
  // 因为 Ord 实现了 Map.Entry<Integer, E> 接口，所以必须提供此方法
  @Override public E getValue() {
    // 返回元素 e，作为 Map.Entry 的值
    return e;
  }

  // 实现 Map.Entry 接口的 setValue() 方法：设置值
  // 因为 Ord 实现了 Map.Entry<Integer, E> 接口，所以必须提供此方法
  @Override public E setValue(E value) {
    // Ord 对象是不可变的（成员变量都是 final），所以不支持设置值操作
    // 抛出 UnsupportedOperationException 异常，表示不支持此操作
    throw new UnsupportedOperationException();
  }

  /** Applies an action to every element of an iterable, passing the zero-based
   * ordinal of the element to the action. // 静态方法：遍历 Iterable 并对每个元素执行操作
   * // 这个方法允许在遍历 Iterable 时，对每个元素及其索引执行自定义操作
   * // 类似于 Java 8 的 forEach，但额外提供了元素的索引信息
   *
   * @see List#forEach(java.util.function.Consumer) // 参考 List.forEach 方法
   * @see Map#forEach(java.util.function.BiConsumer) // 参考 Map.forEach 方法
   *
   * @param iterable Iterable // 要遍历的可迭代对象
   * @param action The action to be performed for each element // 对每个元素执行的操作，接收元素和索引
   * @param <T> Element type // 元素类型
   */
  public static <T> void forEach(Iterable<T> iterable,
      ObjIntConsumer<? super T> action) {
    // 初始化索引计数器 i 为 0
    int i = 0;
    // 使用增强 for 循环遍历 iterable 中的每个元素 t
    for (T t : iterable) {
      // 对当前元素 t 和索引 i 执行 action 操作
      // 使用后置递增 i++，确保下次循环时索引会自动增加
      action.accept(t, i++);
    }
  }

  /** Applies an action to every element of an array, passing the zero-based
   * ordinal of the element to the action. // 静态方法：遍历数组并对每个元素执行操作
   * // 这个方法允许在遍历数组时，对每个元素及其索引执行自定义操作
   * // 类似于 Java 8 的 forEach，但额外提供了元素的索引信息
   *
   * @see List#forEach(java.util.function.Consumer) // 参考 List.forEach 方法
   * @see Map#forEach(java.util.function.BiConsumer) // 参考 Map.forEach 方法
   *
   * @param ts Array // 要遍历的数组
   * @param action The action to be performed for each element // 对每个元素执行的操作，接收元素和索引
   * @param <T> Element type // 元素类型
   */
  public static <T> void forEach(T[] ts,
      ObjIntConsumer<? super T> action) {
    // 使用普通 for 循环遍历数组，从索引 0 开始到数组长度减 1
    for (int i = 0; i < ts.length; i++) {
      // 对当前元素 ts[i] 和索引 i 执行 action 操作
      action.accept(ts[i], i);
    }
  }

  /** List of {@link Ord} backed by a list of elements. // 内部类：基于 List 的 Ord 列表
   * // 这是一个装饰器类，将普通的 List<E> 包装成 List<Ord<E>>
   * // 继承 AbstractList<Ord<E>>，只需要实现 get() 和 size() 方法
   * // 适用于不支持快速随机访问的 List（如 LinkedList）
   *
   * @param <E> element type // 元素类型 */
  private static class OrdList<E> extends AbstractList<Ord<E>> {
    // 成员变量：存储原始的元素列表，final 修饰表示不可变
    private final List<? extends E> elements;

    // 构造方法：接收一个元素列表并初始化成员变量
    OrdList(List<? extends E> elements) {
      // 将传入的元素列表赋值给成员变量
      this.elements = elements;
    }

    // 实现 get() 方法：获取指定索引位置的 Ord 对象
    @Override public Ord<E> get(int index) {
      // 创建并返回一个新的 Ord 对象，序号为 index，元素为原始列表中对应索引的元素
      return Ord.of(index, elements.get(index));
    }

    // 实现 size() 方法：返回列表的大小
    @Override public int size() {
      // 返回原始元素列表的大小
      return elements.size();
    }
  }

  /** List of {@link Ord} backed by a random-access list of elements. // 内部类：基于支持随机访问的 List 的 Ord 列表
   * // 这是 OrdList 的子类，专门用于支持快速随机访问的 List（如 ArrayList）
   * // 实现了 RandomAccess 标记接口，表示支持 O(1) 时间复杂度的随机访问
   * // 这样可以提示某些算法（如 Collections.shuffle）使用优化的算法
   *
   * @param <E> element type // 元素类型 */
  private static class OrdRandomAccessList<E> extends OrdList<E>
      implements RandomAccess {
    // 构造方法：接收一个元素列表并调用父类构造方法
    OrdRandomAccessList(List<? extends E> elements) {
      // 调用父类 OrdList 的构造方法，初始化 elements 成员变量
      super(elements);
    }
  }

  /** List of {@link Ord} backed by an array of elements. // 内部类：基于数组的 Ord 列表
   * // 这是一个装饰器类，将数组包装成 List<Ord<E>>
   * // 继承 AbstractList<Ord<E>>，只需要实现 get() 和 size() 方法
   * // 实现了 RandomAccess 标记接口，因为数组支持 O(1) 时间复杂度的随机访问
   * // 直接使用数组访问比 List 访问性能更好
   *
   * @param <E> element type // 元素类型 */
  private static class OrdArrayList<E> extends AbstractList<Ord<E>>
      implements RandomAccess {
    // 成员变量：存储原始的元素数组，final 修饰表示不可变
    private final E[] elements;

    // 构造方法：接收一个元素数组并初始化成员变量
    OrdArrayList(E[] elements) {
      // 将传入的元素数组赋值给成员变量
      this.elements = elements;
    }

    // 实现 get() 方法：获取指定索引位置的 Ord 对象
    @Override public Ord<E> get(int index) {
      // 创建并返回一个新的 Ord 对象，序号为 index，元素为数组中对应索引的元素
      // 使用数组直接访问 elements[index] 比 List.get(index) 性能更好
      return Ord.of(index, elements[index]);
    }

    // 实现 size() 方法：返回列表的大小
    @Override public int size() {
      // 返回数组的长度
      return elements.length;
    }
  }
}
