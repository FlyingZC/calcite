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
package org.apache.calcite.materialize; // 定义包名：org.apache.calcite.materialize，这个包包含物化相关的类

import org.apache.calcite.util.ImmutableBitSet; // 导入 Calcite 工具类 ImmutableBitSet，用于表示不可变的位集合，常用于标识列的索引集合

import com.google.common.collect.ImmutableList; // 导入 Google Guava 库的 ImmutableList 类，用于表示不可变的列表集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 CheckerFramework 的 @Nullable 注解，用于标记可能为 null 的参数或返回值

import java.util.Objects; // 导入 Java 标准库的 Objects 工具类，提供对象操作的实用方法，如 hash() 和 equals() 等

/** Definition of a particular combination of dimensions and measures of a // 类注释：定义了某个特定的维度和度量组合，这个组合构成了物化的基础
 * lattice that is the basis of a materialization. // 这个组合属于 lattice（立方体结构），是物化的基础
 *
 * <p>Holds similar information to a // 段落注释：包含与 Lattice.Tile 类相似的信息
 * {@link org.apache.calcite.materialize.Lattice.Tile} but a lattice is // 但是 Lattice 对象是不可变的，且 tiles 在创建后不会被添加
 * immutable and tiles are not added after their creation. */ // 这意味着 TileKey 比 Tile 更灵活，可以动态创建
public class TileKey { // 类声明：TileKey 类，用于唯一标识一个特定的 tile（瓦片）组合
  public final Lattice lattice; // 成员变量：lattice，表示所属的 Lattice 对象，final 表示不可变，public 表示外部可访问
  public final ImmutableBitSet dimensions; // 成员变量：dimensions，使用 ImmutableBitSet 表示维度列的索引集合，final 不可变，标识哪些列是维度
  public final ImmutableList<Lattice.Measure> measures; // 成员变量：measures，使用 ImmutableList 表示度量（Measure）列表，final 不可变，包含所有度量信息

  /** Creates a TileKey. */ // 方法注释：创建一个 TileKey 对象的构造方法
  public TileKey(Lattice lattice, ImmutableBitSet dimensions, // 构造方法声明：接收 lattice 对象、维度集合和度量列表作为参数
      ImmutableList<Lattice.Measure> measures) { // 继续构造方法参数：度量列表参数，使用泛型指定为 Lattice.Measure 类型
    this.lattice = lattice; // 赋值语句：将传入的 lattice 参数赋值给成员变量 lattice，使用 this 关键字区分成员变量和参数
    this.dimensions = dimensions; // 赋值语句：将传入的 dimensions 参数赋值给成员变量 dimensions，标识维度列集合
    this.measures = measures; // 赋值语句：将传入的 measures 参数赋值给成员变量 measures，存储度量列表
  } // 构造方法结束

  @Override public int hashCode() { // 方法声明：重写 Object 类的 hashCode() 方法，用于计算对象的哈希值，@Override 注解表示这是重写的方法
    return Objects.hash(lattice, dimensions); // 返回语句：使用 Objects.hash() 方法计算哈希值，只使用 lattice 和 dimensions 两个字段，不包含 measures
  } // hashCode() 方法结束

  @Override public boolean equals(@Nullable Object obj) { // 方法声明：重写 Object 类的 equals() 方法，用于比较两个对象是否相等，@Nullable 表示参数可能为 null
    return obj == this // 返回语句：首先检查 obj 是否是当前对象本身（引用相等），如果是则直接返回 true
        || obj instanceof TileKey // 或者检查 obj 是否是 TileKey 类的实例，使用 instanceof 运算符进行类型检查
        && lattice == ((TileKey) obj).lattice // 如果是 TileKey 实例，则检查 lattice 成员变量是否引用相等（使用 == 比较）
        && dimensions.equals(((TileKey) obj).dimensions) // 检查 dimensions 是否相等（使用 equals() 方法比较内容）
        && measures.equals(((TileKey) obj).measures); // 检查 measures 是否相等（使用 equals() 方法比较列表内容）
  } // equals() 方法结束

  @Override public String toString() { // 方法声明：重写 Object 类的 toString() 方法，用于返回对象的字符串表示，便于调试和日志输出
    return "dimensions: " + dimensions + ", measures: " + measures; // 返回语句：返回包含 dimensions 和 measures 信息的字符串，格式为 "dimensions: xxx, measures: xxx"
  } // toString() 方法结束
} // TileKey 类结束
