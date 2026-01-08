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
package org.apache.calcite.interpreter; // 声明包名，表示Row类属于Calcite解释器包，该包提供SQL解释执行的核心功能

import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值检查注解，用于标记可能为null的字段、参数或返回值，帮助进行静态类型检查

import java.util.Arrays; // 导入Java工具类Arrays，用于数组的哈希计算、数组比较、数组转字符串等操作

/**
 * Row. // 数据行类：代表SQL执行过程中的一行数据，每一行包含多个列值
 * // 该类在Calcite解释器中用于表示关系型数据库中的一行记录
 * // 核心特性：
 * // 1. 封装一个Object数组来存储多个列的值（每个元素代表一个列）
 * // 2. 提供不可变语义的工厂方法（asCopy）和可变语义的构建器（RowBuilder）
 * // 3. 支持按索引访问列值、获取行大小、复制行数据等操作
 * // 4. 实现equals和hashCode以便行对象可以正确比较和作为集合元素
 * // 5. 在解释器执行过程中，Row对象会在操作符之间传递，代表中间结果
 */
public class Row { // 定义Row类，表示Calcite解释器中的数据行对象
  private final @Nullable Object[] values; // 成员变量：存储该行所有列值的数组，final表示引用不可变（但数组元素可变），@Nullable表示数组本身可能为null（代表空行）
  // values数组的设计考虑：
  // 1. 使用Object[]可以存储任意类型的列值（Integer、String、Double等）
  // 2. 数组的长度等于该行的列数
  // 3. 数组索引对应列的顺序（从0开始）
  // 4. final确保values引用一旦赋值就不能改变，保证行的基本结构稳定

  /** Creates a Row. */ // 创建一个Row对象的构造方法说明
  // must stay package-protected, because does not copy // 构造方法必须保持包级访问权限（无修饰符），因为它不复制输入数组，直接使用传入的数组引用
  // 这种设计是为了性能优化，避免不必要的数组复制，但需要调用者确保数组不会被外部修改
  Row(@Nullable Object[] values) { // 包级构造方法：直接使用传入的数组创建Row对象，不进行复制，参数values可能为null
    this.values = values; // 将传入的数组引用直接赋值给成员变量values，实现零拷贝创建
  }

  /** Creates a Row. // 创建一个Row对象的静态工厂方法
   *
   * <p>Makes a defensive copy of the array, so the Row is immutable. // 对输入数组进行防御性复制，确保Row对象是不可变的（外部修改原数组不会影响Row）
   * (If you're worried about the extra copy, call {@link #of(Object)}. // 如果担心复制的性能开销，可以调用of方法（不复制）
   * But the JIT probably avoids the copy.) // 但JIT编译器可能会优化掉这个复制，所以实际性能影响很小
   */
  public static Row asCopy(@Nullable Object... values) { // 静态工厂方法：创建Row对象并对输入数组进行复制，参数values是可变参数，可能为null
    return new Row(values.clone()); // 调用clone()方法创建数组的副本，然后用副本创建Row对象，确保Row的不可变性
  }

  /** Creates a Row with one column value. */ // 创建包含一个列值的Row对象的静态工厂方法
  public static Row of(@Nullable Object value0) { // 静态工厂方法：创建包含单个列值的Row对象，参数value0是第一个列的值，可能为null
    return new Row(new Object[] {value0}); // 创建一个只包含value0的新数组，并用它构造Row对象
  }

  /** Creates a Row with two column values. */ // 创建包含两个列值的Row对象的静态工厂方法
  public static Row of(@Nullable Object value0, @Nullable Object value1) { // 静态工厂方法：创建包含两个列值的Row对象，value0和value1分别是第一和第二列的值，都可能为null
    return new Row(new Object[] {value0, value1}); // 创建包含value0和value1的新数组，并用它构造Row对象
  }

  /** Creates a Row with three column values. */ // 创建包含三个列值的Row对象的静态工厂方法
  public static Row of(@Nullable Object value0, @Nullable Object value1, @Nullable Object value2) { // 静态工厂方法：创建包含三个列值的Row对象，三个参数分别对应三列的值，都可能为null
    return new Row(new Object[] {value0, value1, value2}); // 创建包含三个值的新数组，并用它构造Row对象
  }

  /** Creates a Row with variable number of values. */ // 创建包含可变数量列值的Row对象的静态工厂方法
  public static Row of(@Nullable Object...values) { // 静态工厂方法：创建包含任意数量列值的Row对象，values是可变参数数组，可能为null
    return new Row(values); // 直接使用传入的数组创建Row对象，不进行复制（性能优化，但调用者需确保数组不被修改）
  }

  @Override public int hashCode() { // 重写Object类的hashCode方法，用于计算Row对象的哈希值
    return Arrays.hashCode(values); // 使用Arrays工具类的hashCode方法计算values数组的哈希值，确保内容相同的Row对象有相同的哈希码
  }

  @Override public boolean equals(@Nullable Object obj) { // 重写Object类的equals方法，用于比较两个Row对象是否相等
    return obj == this // 首先检查obj是否就是当前对象（引用相等），如果是则直接返回true
        || obj instanceof Row // 如果不是同一个引用，检查obj是否是Row类的实例
        && Arrays.equals(values, ((Row) obj).values); // 如果是Row实例，使用Arrays.equals比较两个Row的values数组内容是否相等
  }

  @Override public String toString() { // 重写Object类的toString方法，用于将Row对象转换为字符串表示
    return Arrays.toString(values); // 使用Arrays.toString将values数组转换为字符串格式（如[value1, value2, null]）
  }

  public @Nullable Object getObject(int index) { // 获取指定索引位置的列值，参数index是列的索引（从0开始），返回值可能为null
    return values[index]; // 直接返回values数组中index位置的元素，如果index越界会抛出ArrayIndexOutOfBoundsException
  }

  // must stay package-protected // 该方法必须保持包级访问权限，不能暴露给外部包
  @Nullable Object[] getValues() { // 获取Row对象内部的values数组引用，返回值可能为null
    return values; // 直接返回values数组的引用，不进行复制，包级访问确保只有解释器包内的类可以访问
  }

  /** Returns a copy of the values. */ // 返回values数组的副本，确保外部修改不影响Row对象
  // Note: This implements BuiltInMethod.ROW_COPY_VALUES. // 注意：这个方法实现了BuiltInMethod.ROW_COPY_VALUES接口，用于代码生成和反射调用
  @SuppressWarnings("unused") // 抑制未使用警告，因为该方法可能通过反射或代码生成被调用
  public @Nullable Object[] copyValues() { // 创建并返回values数组的副本，返回值可能为null
    return values.clone(); // 调用clone()方法创建values数组的深拷贝，返回新数组，确保原始数据不被修改
  }

  public int size() { // 获取Row对象中列的数量（即values数组的长度）
    return values.length; // 返回values数组的长度，如果values为null会抛出NullPointerException
  }

  /**
   * Create a RowBuilder object that eases creation of a new row. // 创建一个RowBuilder对象，用于方便地构建新的Row对象
   *
   * @param size Number of columns in output data. // 参数size：输出数据的列数（即要构建的Row对象包含多少列）
   * @return New RowBuilder object. // 返回值：新创建的RowBuilder对象，用于逐步设置列值并最终构建Row
   */
  public static RowBuilder newBuilder(int size) { // 静态工厂方法：创建指定大小的RowBuilder对象
    return new RowBuilder(size); // 调用RowBuilder的构造方法，创建一个可以构建size列Row的构建器
  }

  /**
   * Utility class to build row objects. // 用于构建Row对象的工具类（内部静态类）
   * // RowBuilder的设计模式：Builder模式，用于逐步构建复杂的Row对象
   * // 核心特性：
   * // 1. 允许按索引设置每个列的值，而不是一次性传入所有值
   * // 2. 支持重置构建器，复用同一个构建器对象创建多个Row
   * // 3. 提供灵活的行构建方式，特别是在不知道列值的情况下逐步填充
   * // 4. 在解释器执行过程中，常用于在循环中逐个设置列值后构建Row
   */
  public static class RowBuilder { // 定义RowBuilder内部静态类，用于构建Row对象
    @Nullable Object[] values; // 成员变量：存储列值的数组，非final以便可以重置，@Nullable表示数组本身可能为null
    // values数组的作用：
    // 1. 作为构建过程中的临时存储，保存用户设置的列值
    // 2. 最终会传递给Row构造方法创建Row对象
    // 3. 可以通过reset()方法重新分配，实现构建器的复用

    private RowBuilder(int size) { // 私有构造方法：创建指定大小的RowBuilder对象，参数size表示要构建的Row包含多少列
      values = new Object[size]; // 创建长度为size的新Object数组，所有元素初始化为null，准备接收列值
    }

    /**
     * Sets the value of a particular column. // 设置指定索引位置的列值
     *
     * @param index Zero-indexed position of value. // 参数index：列的索引位置（从0开始）
     * @param value Desired column value. // 参数value：要设置的列值，可能为null
     */
    public void set(int index, @Nullable Object value) { // 设置指定索引位置的列值，index是列索引，value是列值
      values[index] = value; // 将value赋值给values数组的index位置，如果index越界会抛出ArrayIndexOutOfBoundsException
    }

    /** Returns a Row. */ // 构建并返回一个Row对象
    public Row build() { // 构建方法：使用当前values数组创建并返回Row对象
      return new Row(values); // 调用Row的包级构造方法，直接使用values数组创建Row对象（不复制）
    }

    /** Allocates a new internal array. */ // 分配新的内部数组，重置构建器状态
    public void reset() { // 重置方法：重新分配values数组，清空之前设置的值
      values = new Object[values.length]; // 创建一个与原数组长度相同的新数组，所有元素初始化为null，实现构建器的复用
    }

    public int size() { // 获取RowBuilder可以构建的Row对象的列数
      return values.length; // 返回values数组的长度，如果values为null会抛出NullPointerException
    }
  }


}