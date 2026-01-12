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
package org.apache.calcite.linq4j; // 导入linq4j包，这是Calcite的LINQ（Language Integrated Query）实现的核心包

import java.util.List; // 导入Java集合框架中的List接口，用于存储枚举器列表

/**
 * Enumerator over the cartesian product of enumerators. // 这是一个用于遍历多个枚举器笛卡尔积的枚举器类
 * // 笛卡尔积是指从多个集合中各取一个元素的所有可能组合，例如集合A={a,b}和集合B={1,2}的笛卡尔积为{(a,1),(a,2),(b,1),(b,2)}
 * // 这个类实现了笛卡尔积的遍历算法，使用嵌套循环的思想，但通过状态机的方式实现
 * // 算法核心类似于数字进位系统，最右边的枚举器变化最快，最左边的变化最慢
 *
 * @param <T> Input element type // 类型参数T表示输入枚举器中元素的类型，即各个源集合中元素的类型
 * @param <E> Element type // 类型参数E表示最终输出元素的类型，由子类决定如何组合T类型的元素
 */
public abstract class CartesianProductEnumerator<T, E> implements Enumerator<E> { // 抽象类，实现Enumerator接口，表示可以遍历的枚举器
  private final List<Enumerator<T>> enumerators; // 成员变量：存储所有输入枚举器的列表，final修饰表示初始化后不可改变
  // 这个列表中的每个Enumerator代表一个源集合，例如笛卡尔积A×B×C，enumerators就包含A、B、C三个枚举器
  protected final T[] elements; // 成员变量：数组，用于存储当前笛卡尔积组合中各个枚举器的当前元素
  // protected修饰允许子类访问这个数组，以便子类根据这些元素构建最终的输出类型E
  // 数组长度等于enumerators的大小，elements[i]存储第i个枚举器的当前元素
  private boolean first = true; // 成员变量：标志位，表示是否是第一次调用moveNext()方法
  // 用于区分初始化阶段和正常遍历阶段，first=true时需要初始化所有枚举器的第一个元素

  protected CartesianProductEnumerator(List<Enumerator<T>> enumerators) { // 构造方法，接收一个枚举器列表作为参数
    this.enumerators = enumerators; // 将传入的枚举器列表赋值给成员变量，保存所有需要计算笛卡尔积的源枚举器
    //noinspection unchecked // 抑制未检查的类型转换警告，因为Java的泛型数组创建需要强制类型转换
    this.elements = (T[]) new Object[enumerators.size()]; // 创建一个Object数组并强制转换为T[]类型，数组大小等于枚举器数量
    // 这个数组将用于存储笛卡尔积当前组合中每个枚举器的当前元素值
  }

  @Override public boolean moveNext() { // 重写Enumerator接口的moveNext方法，移动到下一个元素
    // 返回true表示成功移动到下一个元素，false表示已经遍历完所有组合
    if (first) { // 如果是第一次调用moveNext方法，进入初始化阶段
      int i = 0; // 初始化索引变量i，用于遍历enumerators列表
      for (Enumerator<T> enumerator : enumerators) { // 遍历所有枚举器，为每个枚举器获取第一个元素
        if (!enumerator.moveNext()) { // 尝试移动枚举器到第一个元素，如果返回false表示该枚举器为空
          return false; // 如果任何一个枚举器为空，整个笛卡尔积就是空的，直接返回false
        }
        elements[i++] = enumerator.current(); // 获取当前枚举器的当前元素，存入elements数组的对应位置，然后索引递增
        // 这样就初始化了笛卡尔积的第一个组合，即所有枚举器的第一个元素组成的组合
      }
      first = false; // 将first标志设为false，表示初始化完成，后续调用将进入正常遍历逻辑
      return true; // 返回true表示成功获取到第一个组合
    }
    // 以下是正常遍历阶段的逻辑，类似于数字进位系统的递增
    for (int ordinal = enumerators.size() - 1; ordinal >= 0; --ordinal) { // 从最右边的枚举器开始向左遍历
      // ordinal表示当前处理的枚举器的序号，从最右边（size-1）开始向左递减
      final Enumerator<T> enumerator = enumerators.get(ordinal); // 获取当前序号对应的枚举器
      if (enumerator.moveNext()) { // 尝试移动当前枚举器到下一个元素
        elements[ordinal] = enumerator.current(); // 如果成功移动，更新elements数组中对应位置的元素值
        return true; // 返回true表示成功获取到下一个组合，此时只有当前枚举器及其右边的枚举器发生了变化
      }

      // Move back to first element. // 如果当前枚举器已经遍历完所有元素，需要重置并进位
      enumerator.reset(); // 将当前枚举器重置到初始状态，回到第一个元素之前的位置
      if (!enumerator.moveNext()) { // 尝试移动到第一个元素，如果返回false表示该枚举器为空
        // Very strange... this was empty all along. // 这种情况很奇怪，因为初始化时已经检查过枚举器不为空
        return false; // 如果枚举器突然变空，返回false结束遍历
      }
      elements[ordinal] = enumerator.current(); // 获取重置后的第一个元素，更新elements数组
      // 然后继续循环，向左边的枚举器进位，重复上述过程
    }
    return false; // 如果所有枚举器都遍历完成（最左边的枚举器也遍历完了），返回false表示笛卡尔积遍历完毕
  }

  @Override public void reset() { // 重写Enumerator接口的reset方法，重置枚举器到初始状态
    first = true; // 将first标志设为true，表示下次moveNext调用将重新初始化
    for (Enumerator<T> enumerator : enumerators) { // 遍历所有枚举器
      enumerator.reset(); // 调用每个枚举器的reset方法，将其重置到初始状态
      // 这样下次调用moveNext时，将从笛卡尔积的第一个组合开始重新遍历
    }
  }

  @Override public void close() { // 重写Enumerator接口的close方法，关闭所有枚举器并释放资源
    // If there is one or more exceptions, carry on and close all enumerators, // 如果关闭过程中出现异常，继续关闭所有枚举器
    // then throw the first. // 然后抛出第一个异常，确保所有枚举器都被关闭
    Throwable rte = null; // 声明一个Throwable变量，用于保存第一个遇到的异常
    for (Enumerator<T> enumerator : enumerators) { // 遍历所有枚举器，逐个关闭
      try { // 使用try-catch捕获关闭过程中可能出现的异常
        enumerator.close(); // 调用枚举器的close方法，释放其占用的资源
      } catch (Throwable e) { // 捕获所有类型的异常（包括Error和RuntimeException）
        if (rte == null) { // 如果这是第一个异常
          rte = e; // 将异常保存到rte变量中
        } else { // 如果这不是第一个异常
          rte.addSuppressed(e); // 将当前异常作为被抑制的异常添加到第一个异常中
          // 这样可以保留所有异常信息，便于调试
        }
      }
    }
    if (rte != null) { // 如果在关闭过程中发生了异常
      if (rte instanceof Error) { // 判断异常类型是否为Error
        throw (Error) rte; // 如果是Error，直接抛出
      } else { // 否则认为是RuntimeException
        throw (RuntimeException) rte; // 强制转换为RuntimeException并抛出
      }
    }
  }
}
