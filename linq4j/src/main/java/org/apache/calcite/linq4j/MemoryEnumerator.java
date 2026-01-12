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
// 声明包名，表示这个类属于 org.apache.calcite.linq4j 包，这是 Calcite LINQ4J 模块的核心包
package org.apache.calcite.linq4j;

// 导入 CheckerFramework 的可空注解，用于标记可能为 null 的泛型类型参数，帮助静态分析工具进行空值检查
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入 Java 并发包中的原子整数类，用于线程安全的整数计数操作
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enumerator that keeps some recent and some "future" values.
 * 这是一个枚举器实现类，用于在遍历数据时保留一些"过去"的值和一些"未来"的值
 * 
 * 这个类的主要功能是：
 * 1. 包装一个普通的枚举器（Enumerator），为其增加记忆功能
 * 2. 在遍历过程中可以访问历史值（之前访问过的元素）和未来值（即将访问的元素）
 * 3. 这种功能在窗口函数、滑动窗口计算等场景中非常有用，例如计算移动平均、前后行比较等
 * 4. 通过 MemoryFactory 来管理这些历史值和未来值的存储和访问
 * 5. 支持指定要保留的历史记录数量和未来记录数量
 *
 * @param <E> Row value - 泛型类型参数，表示枚举器中元素的类型，可以是任意类型，使用 @Nullable 标记表示可以为 null
 */
// 定义 MemoryEnumerator 类，实现 Enumerator 接口，枚举的元素类型是 MemoryFactory.Memory<E>
// MemoryFactory.Memory<E> 是一个内存对象，包含了当前位置的历史值和未来值
public class MemoryEnumerator<@Nullable E> implements Enumerator<MemoryFactory.Memory<E>> {
  // 成员变量：原始枚举器，这是被包装的基础数据源枚举器，负责提供实际的数据元素
  // 使用 final 修饰表示一旦初始化就不能重新赋值，保证线程安全性
  private final Enumerator<E> enumerator;
  // 成员变量：内存工厂对象，用于创建和管理 Memory 对象
  // MemoryFactory 负责维护一个滑动窗口，存储历史值和未来值
  // 构造时会传入 history（历史记录数）和 future（未来记录数）参数
  private final MemoryFactory<E> memoryFactory;
  // 成员变量：前置计数器，使用原子整数保证线程安全
  // 这个计数器用于控制初始化时的"预热"过程，在开始遍历前预读指定数量的未来值
  // 初始值为 future 参数，表示需要预读的未来值数量
  private final AtomicInteger prevCounter;
  // 成员变量：后置计数器，使用原子整数保证线程安全
  // 这个计数器用于控制在枚举器结束后继续添加 null 值的数量
  // 这样可以确保在数据源结束后，窗口函数仍然能够正确处理边界情况
  // 初始值为 future 参数，表示数据源结束后需要添加的 null 值数量
  private final AtomicInteger postCounter;

  /**
   * Creates a MemoryEnumerator.
   * 构造方法：创建一个 MemoryEnumerator 实例
   *
   * <p>Use factory method {@link MemoryEnumerable#enumerator()}.
   * 建议通过工厂方法 MemoryEnumerable#enumerator() 来创建实例，而不是直接调用此构造方法
   *
   * @param enumerator The Enumerator that memory should be "wrapped" around
   *                  参数：基础枚举器，这是被包装的数据源枚举器，提供实际的数据元素
   * @param history Number of present steps to remember
   *               参数：历史记录数量，表示要保留多少个之前访问过的元素
   *                  例如 history=2 表示可以访问当前元素的前两个元素
   * @param future Number of future steps to "remember"
   *              参数：未来记录数量，表示要预读多少个即将访问的元素
   *                 例如 future=1 表示可以访问当前元素的下一个元素
   */
  // 构造方法实现：初始化 MemoryEnumerator 实例
  MemoryEnumerator(Enumerator<E> enumerator, int history, int future) {
    // 将传入的基础枚举器赋值给成员变量，这是数据源
    this.enumerator = enumerator;
    // 创建 MemoryFactory 实例，传入历史记录数和未来记录数
    // MemoryFactory 会创建一个固定大小的滑动窗口来存储这些值
    this.memoryFactory = new MemoryFactory<>(history, future);
    // 初始化前置计数器，值为 future，表示需要预读 future 个未来值
    // 这个计数器用于在第一次调用 moveNext() 时进行预热
    this.prevCounter = new AtomicInteger(future);
    // 初始化后置计数器，值为 future，表示在数据源结束后需要添加 future 个 null 值
    // 这样确保在数据结束时窗口函数仍然能正确处理边界情况
    this.postCounter = new AtomicInteger(future);
  }

  /**
   * 获取当前元素，返回一个 Memory 对象
   * 该 Memory 对象包含了当前位置可以访问的历史值和未来值
   * 
   * @return MemoryFactory.Memory<E> - 返回一个内存对象，该对象提供了对历史值和未来值的访问
   *                                    通过这个对象可以获取当前行之前和之后的元素
   */
  @Override public MemoryFactory.Memory<E> current() {
    // 调用 memoryFactory 的 create() 方法创建并返回一个 Memory 对象
    // 这个 Memory 对象封装了当前窗口中的所有值（历史值、当前值、未来值）
    // 用户可以通过这个对象访问窗口中的任意元素
    return memoryFactory.create();
  }

  /**
   * 移动到下一个元素
   * 这个方法实现了窗口的滑动逻辑，每次调用都会：
   * 1. 如果是第一次调用（prevCounter > 0），则预读指定数量的未来值进行预热
   * 2. 正常情况下，移动到下一个元素并更新窗口
   * 3. 如果数据源已结束，根据 postCounter 的值继续返回 null 值
   * 
   * @return boolean - 如果成功移动到下一个元素返回 true，否则返回 false
   */
  @Override public boolean moveNext() {
    // 检查前置计数器是否大于 0，如果大于 0 说明还需要进行预热
    // 预热的目的是在第一次真正返回数据前，先预读 future 个未来值
    // 这样第一次调用 current() 时就能访问到未来的值
    if (prevCounter.get() > 0) {
      // 声明一个变量来记录最后一次移动的结果
      boolean lastMove = false;
      // 循环调用 moveNextInternal() 直到 prevCounter 减到 0
      // getAndDecrement() 是原子操作，先获取当前值然后减 1
      // 每次循环都预读一个元素，直到预读了 future 个元素
      while (prevCounter.getAndDecrement() >= 0) {
        // 调用内部移动方法，更新窗口内容
        // 记录最后一次移动的结果，这是最终要返回的结果
        lastMove = moveNextInternal();
      }
      // 返回最后一次移动的结果
      // 如果预读过程中都成功，则返回 true，否则返回 false
      return lastMove;
    } else {
      // 如果前置计数器已经为 0，说明预热完成，正常移动到下一个元素
      return moveNextInternal();
    }
  }

  /**
   * 内部移动方法，实际执行移动到下一个元素的操作
   * 这个方法是 moveNext() 的核心实现，负责：
   * 1. 调用基础枚举器的 moveNext() 方法获取下一个元素
   * 2. 如果成功获取元素，将其添加到内存工厂中
   * 3. 如果数据源已结束，根据后置计数器添加 null 值
   * 
   * @return boolean - 如果成功移动到下一个元素返回 true，否则返回 false
   */
  private boolean moveNextInternal() {
    // 调用基础枚举器的 moveNext() 方法，尝试移动到下一个元素
    // 返回值表示是否成功移动到下一个元素
    final boolean moveNext = enumerator.moveNext();
    // 如果成功移动到下一个元素
    if (moveNext) {
      // 将当前元素添加到内存工厂中
      // memoryFactory.add() 会将元素添加到滑动窗口中
      // 并自动移除超出历史记录数量的旧元素
      memoryFactory.add(enumerator.current());
      // 返回 true 表示成功移动
      return true;
    } else {
      // 如果基础枚举器已经没有更多元素（数据源结束）
      // 检查后置计数器是否大于 0，如果大于 0则需要添加 null 值
      // 这是为了处理窗口函数的边界情况，确保在数据结束时窗口仍然完整
      // Check if we have to add "history" additional values
      if (postCounter.getAndDecrement() > 0) {
        // 添加一个 null 值到内存工厂中
        // 这样可以保证窗口函数在数据结束时仍然能访问到"未来"的值（虽然是 null）
        memoryFactory.add(null);
        // 返回 true 表示成功移动（虽然添加的是 null）
        return true;
      }
    }
    // 如果基础枚举器已结束且后置计数器也减到 0，则返回 false
    // 表示没有更多元素可以遍历了
    return false;
  }

  /**
   * 重置枚举器到初始状态
   * 这个方法会将基础枚举器重置，使遍历可以重新开始
   * 注意：这个方法不会重置 prevCounter 和 postCounter，需要重新创建实例
   */
  @Override public void reset() {
    // 调用基础枚举器的 reset() 方法，将其重置到初始状态
    // 这样可以重新开始遍历数据
    enumerator.reset();
  }

  /**
   * 关闭枚举器，释放相关资源
   * 这个方法会关闭基础枚举器，释放其占用的资源
   * 建议在使用完枚举器后调用此方法以避免资源泄漏
   */
  @Override public void close() {
    // 调用基础枚举器的 close() 方法，关闭枚举器并释放资源
    // 这是良好的编程实践，确保资源被正确释放
    enumerator.close();
  }
}
