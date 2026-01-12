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
package org.apache.calcite.linq4j; // 包声明，表示这个类属于org.apache.calcite.linq4j包，这是Calcite的LINQ4J模块，提供了类似.NET LINQ的查询功能

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker Framework的注解，用于标记可能为null的参数，帮助进行空值检查

import static com.google.common.base.Preconditions.checkArgument; // 静态导入Google Guava库的Preconditions类中的checkArgument方法，用于参数校验

/**
 * Represents an integer in modular arithmetic. // 表示模运算中的整数，这是一个不可变类，用于处理模数运算
 * Its {@code value} is between 0 and {@code m - 1} for some modulus {@code m}. // 其值value总是在0到m-1之间，其中m是模数modulus
 *
 * <p>This object is immutable; all operations create a new object. // 这个对象是不可变的，所有操作都会创建新的对象，不会修改原对象
 */
class ModularInteger { // ModularInteger类定义，表示模数运算中的整数，用于处理循环索引、哈希表索引等场景
  private final int value; // 成员变量：存储模数运算后的整数值，范围在[0, modulus-1]之间，final修饰表示不可变
  private final int modulus; // 成员变量：模数，即除数，决定了value的取值范围，final修饰表示不可变

  /** Creates a ModularInteger. */ // 构造方法注释：创建一个ModularInteger对象
  ModularInteger(int value, int modulus) { // 构造方法，接收初始值和模数两个参数
    checkArgument(value >= 0 && value < modulus); // 参数校验：确保value在有效范围内[0, modulus-1]，否则抛出IllegalArgumentException
    this.value = value; // 将参数value赋值给成员变量value，存储模数运算后的值
    this.modulus = modulus; // 将参数modulus赋值给成员变量modulus，存储模数
  }

  @Override public boolean equals(@Nullable Object obj) { // 重写Object类的equals方法，用于比较两个ModularInteger对象是否相等
    return obj == this // 首先检查obj是否就是当前对象(this)，如果是则直接返回true（引用相等）
        || obj instanceof ModularInteger // 如果不是同一个对象，检查obj是否是ModularInteger类型的实例
        && value == ((ModularInteger) obj).value // 如果类型匹配，比较value值是否相等
        && modulus == ((ModularInteger) obj).modulus; // 同时比较modulus值是否相等，只有两者都相等才返回true
  }

  @Override public int hashCode() { // 重写Object类的hashCode方法，用于计算对象的哈希值，支持在哈希集合中使用
    // 8191 is prime and, as 2^13 - 1, can easily be multiplied by bit-shifting // 原注释：8191是质数，且等于2^13 - 1，可以通过位移运算高效地进行乘法
    return value + 8191 * modulus; // 计算哈希值：使用value加上8191倍modulus，确保不同的value和modulus组合产生不同的哈希值
  }

  public int get() { // 公共方法：获取当前ModularInteger对象的值
    return this.value; // 返回成员变量value，即模数运算后的整数值
  }

  public ModularInteger plus(int operand) { // 公共方法：执行加法运算，返回一个新的ModularInteger对象
    if (operand < 0) { // 如果操作数为负数
      return minus(Math.abs(operand)); // 转换为减法运算，取操作数的绝对值后调用minus方法
    }
    return new ModularInteger((value + operand) % modulus, modulus); // 操作数为正数，执行(value + operand) % modulus的模数运算，返回新对象
  }

  public ModularInteger minus(int operand) { // 公共方法：执行减法运算，返回一个新的ModularInteger对象
    assert operand >= 0; // 断言操作数非负，这是一个前置条件，调用者必须保证
    int r = value - operand; // 计算value减去operand的差值
    while (r < 0) { // 如果差值为负数，需要通过加上modulus使其变为正数
      r = r + modulus; // 循环加上modulus，直到r变为非负数，这是模数减法的标准实现
    }
    return new ModularInteger(r, modulus); // 返回新的ModularInteger对象，包含计算后的值和相同的模数
  }

  @Override public String toString() { // 重写Object类的toString方法，用于返回对象的字符串表示
    return value + " mod " + modulus; // 返回格式为"value mod modulus"的字符串，例如"5 mod 10"表示模10的值为5
  }
}
