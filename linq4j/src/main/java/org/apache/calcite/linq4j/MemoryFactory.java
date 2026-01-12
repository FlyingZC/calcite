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
package org.apache.calcite.linq4j; // 定义包名，该类属于linq4j包，linq4j是Calcite中的语言集成查询框架

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的字段或参数

import java.util.Arrays; // 导入Arrays工具类，用于数组操作和转换为字符串

/**
 * Contains the State and changes internally.
 * with the {@link #create()} method one can get immutable Snapshots.
 * 内存工厂类，用于维护状态和内部变化，支持创建不可变的快照
 * 该类主要用于实现SQL中的MATCH_RECOGNIZE操作符，该操作符用于模式匹配
 * 它维护一个滑动窗口，可以访问当前记录之前（历史）和之后（未来）的记录
 *
 * @param <E> Type of the base Object // 泛型参数E表示存储元素的类型，通常是行对象
 */
public class MemoryFactory<E> { // 定义MemoryFactory类，泛型E表示元素类型

  private final int history; // 历史记录数量，表示可以访问当前记录之前多少条记录（负索引）
  private final int future; // 未来记录数量，表示可以访问当前记录之后多少条记录（正索引）
  // Index:      0   1   2   3   4 // 数组索引示例，展示如何在内部数组中存储不同时间点的记录
  // Idea       -2  -1   0  +1  +2 // 对应的逻辑位置，负数表示历史，0表示当前，正数表示未来
  ModularInteger offset; // 模整数偏移量，用于在循环数组中定位当前记录的位置，支持模运算实现循环
  private final @Nullable Object[] values; // 存储历史、当前和未来记录的数组，使用Object数组支持泛型

  public MemoryFactory(int history, int future) { // 构造方法，初始化内存工厂，参数为历史和未来记录数量
    this.history = history; // 初始化历史记录数量
    this.future = future; // 初始化未来记录数量
    this.values = new Object[history + future + 1]; // 创建数组，大小为历史+当前+未来记录总数
    this.offset = new ModularInteger(0, history + future + 1); // 初始化偏移量为0，模数为数组大小，实现循环数组
  }

  public void add(E current) { // 添加当前记录到内存中，参数current为当前记录
    values[offset.get()] = current; // 将当前记录存储到当前偏移量指向的数组位置
    this.offset = offset.plus(1); // 偏移量加1，移动到下一个位置（模运算实现循环）
  }

  public Memory<E> create() { // 创建当前状态的不可变快照，返回Memory对象
    return new Memory<>(history, future, offset, values.clone()); // 创建新的Memory对象，克隆values数组确保不可变性
  }

  /**
   * Contents of a "memory segment", used for implementing the
   * {@code MATCH_RECOGNIZE} operator.
   * 内存段类，包含一个内存段的内容，用于实现MATCH_RECOGNIZE操作符
   * 该类是不可变的快照，保存了特定时刻的历史、当前和未来记录
   *
   * <p>Memory maintains a "window" of records preceding and following a record;
   * the records can be browsed using the {@link #get()} or {@link #get(int)}
   * methods.
   * Memory维护一个记录窗口，包含当前记录之前和之后的记录
   * 可以通过get()或get(int)方法浏览这些记录
   *
   * @param <E> Row type // 泛型参数E表示行类型
   */
  public static class Memory<E> { // 定义Memory内部静态类，表示不可变的内存快照
    private final int history; // 历史记录数量，表示可以回溯多少条记录
    private final int future; // 未来记录数量，表示可以向前看多少条记录
    private final ModularInteger offset; // 当前记录在数组中的偏移量，使用模整数支持循环数组
    private final @Nullable Object[] values; // 存储所有记录的数组，包含历史、当前和未来记录

    public Memory(int history, int future, // Memory构造方法，初始化内存快照
        ModularInteger offset, @Nullable Object[] values) { // 参数：历史数量、未来数量、偏移量、值数组
      this.history = history; // 初始化历史记录数量
      this.future = future; // 初始化未来记录数量
      this.offset = offset; // 保存偏移量引用
      this.values = values; // 保存值数组引用
    }

    @Override public String toString() { // 重写toString方法，用于调试输出
      return Arrays.toString(this.values); // 将数组转换为字符串格式输出
    }

    public E get() { // 获取当前记录（位置为0的记录）
      return get(0); // 调用get(0)获取当前记录
    }

    public E get(int position) { // 根据相对位置获取记录，position为相对位置（负数表示历史，0表示当前，正数表示未来）
      if (position < 0 && position < -1 * history) { // 检查是否超出历史记录范围
        throw new IllegalArgumentException("History can only go back " + history // 抛出异常，说明历史记录限制
            + " points in time, you wanted " + Math.abs(position)); // 提示用户请求的位置超出历史范围
      }
      if (position > 0 && position > future) { // 检查是否超出未来记录范围
        throw new IllegalArgumentException("Future can only see next " + future // 抛出异常，说明未来记录限制
            + " points in time, you wanted " + position); // 提示用户请求的位置超出未来范围
      }
      return (E) this.values[this.offset.plus(position - 1 - future).get()]; // 计算数组索引并返回对应记录，使用模运算实现循环访问
    }
  }
}
