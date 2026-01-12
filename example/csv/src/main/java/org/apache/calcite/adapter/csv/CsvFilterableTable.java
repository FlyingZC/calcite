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
package org.apache.calcite.adapter.csv; // 包声明：该类位于org.apache.calcite.adapter.csv包下，属于Calcite CSV适配器模块

import org.apache.calcite.DataContext; // 导入DataContext类，用于在查询执行过程中传递上下文信息（如类型工厂、取消标志等）
import org.apache.calcite.adapter.file.CsvEnumerator; // 导入CsvEnumerator类，用于枚举CSV文件中的数据行
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory接口，用于创建Java类型对应的RelDataType类型
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入AbstractEnumerable抽象类，用于实现可枚举的数据集合
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可以被枚举的数据集合
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，用于遍历数据集合
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系型数据类型
import org.apache.calcite.rel.type.RelProtoDataType; // 导入RelProtoDataType接口，用于延迟创建RelDataType类型
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示Rex表达式中的函数调用
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类，表示对输入字段的引用
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，表示字面量常量
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式的基类
import org.apache.calcite.schema.FilterableTable; // 导入FilterableTable接口，表示可以被过滤的表
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举，表示SQL操作符的种类（如AND、EQUALS等）
import org.apache.calcite.util.ImmutableIntList; // 导入ImmutableIntList类，表示不可变的整数列表
import org.apache.calcite.util.Source; // 导入Source类，表示数据源（如CSV文件）

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值

import java.util.List; // 导入List接口，用于集合操作
import java.util.concurrent.atomic.AtomicBoolean; // 导入AtomicBoolean类，用于支持原子操作的布尔值（用于取消标志）

/**
 * Table based on a CSV file that can implement simple filtering.
 * 基于CSV文件的表，可以实现简单的过滤功能
 *
 * <p>It implements the {@link FilterableTable} interface, so Calcite gets
 * data by calling the {@link #scan(DataContext, List)} method.
 * 它实现了FilterableTable接口，Calcite通过调用scan(DataContext, List)方法来获取数据
 * 
 * 核心功能：
 * 1. 继承自CsvTable，提供CSV文件数据表的基本功能
 * 2. 实现FilterableTable接口，支持在数据源级别进行过滤（谓词下推）
 * 3. 通过解析RexNode表达式树，提取简单的等值条件（field = literal）
 * 4. 将过滤条件传递给CsvEnumerator，在读取CSV数据时就进行过滤，减少内存使用
 * 
 * 过滤策略：
 * - 支持AND条件的递归处理
 * - 支持简单的等值条件（字段 = 常量）
 * - 不支持复杂条件（如OR、LIKE、范围比较等）
 * - 每个字段只能有一个过滤值
 * 
 * 性能优势：
 * - 通过谓词下推，在数据读取时就过滤，避免加载全部数据到内存
 * - 减少后续处理的行数，提升查询性能
 */
public class CsvFilterableTable extends CsvTable // 类定义：CsvFilterableTable继承自CsvTable，表示可过滤的CSV表
    implements FilterableTable { // 实现FilterableTable接口，提供过滤能力
  /** Creates a CsvFilterableTable. */ // 注释：创建CsvFilterableTable实例
  public CsvFilterableTable(Source source, // 构造方法参数：source表示CSV文件的数据源（包含文件路径等信息）
      @Nullable RelProtoDataType protoRowType) { // 构造方法参数：protoRowType表示行类型的原型（延迟创建，可为null）
    super(source, protoRowType); // 调用父类CsvTable的构造方法，初始化source和protoRowType
  } // 构造方法结束

  @Override public String toString() { // 重写toString方法，用于返回对象的字符串表示
    return "CsvFilterableTable"; // 返回类名作为字符串表示
  } // toString方法结束

  @Override public Enumerable<@Nullable Object[]> scan(DataContext root, List<RexNode> filters) { // 实现FilterableTable接口的scan方法，扫描表数据并应用过滤条件
    JavaTypeFactory typeFactory = root.getTypeFactory(); // 从数据上下文中获取Java类型工厂，用于创建Java类型
    final List<RelDataType> fieldTypes = getFieldTypes(typeFactory); // 获取表中所有字段的类型列表，用于后续数据转换
    final @Nullable String[] filterValues = new String[fieldTypes.size()]; // 创建过滤值数组，长度与字段数相同，初始值都为null
    filters.removeIf(filter -> addFilter(filter, filterValues)); // 遍历所有过滤条件，调用addFilter方法处理，返回true的条件会被移除（表示已下推）
    final List<Integer> fields = ImmutableIntList.identity(fieldTypes.size()); // 创建字段索引列表，包含0到fieldTypes.size()-1的所有整数，用于指定要读取哪些字段
    final AtomicBoolean cancelFlag = DataContext.Variable.CANCEL_FLAG.get(root); // 从数据上下文中获取取消标志，用于支持查询取消操作
    return new AbstractEnumerable<@Nullable Object[]>() { // 返回一个抽象的可枚举对象，使用匿名内部类实现
      @Override public Enumerator<@Nullable Object[]> enumerator() { // 实现enumerator方法，返回一个枚举器用于遍历数据
        return new CsvEnumerator<>(source, cancelFlag, false, filterValues, // 创建CsvEnumerator实例，传入数据源、取消标志、streaming标志（false表示不流式处理）、过滤值数组
            CsvEnumerator.arrayConverter(fieldTypes, fields, false)); // 创建数组转换器，用于将CSV行数据转换为Object数组，传入字段类型、字段索引、是否需要返回行号
      } // enumerator方法结束
    }; // 匿名内部类结束
  } // scan方法结束

  private static boolean addFilter(RexNode filter, @Nullable Object[] filterValues) { // 静态方法：将RexNode过滤条件添加到filterValues数组中，返回true表示成功添加（可以下推）
    if (filter.isA(SqlKind.AND)) { // 如果当前过滤条件是AND操作符
        // We cannot refine(remove) the operands of AND,
        // it will cause o.a.c.i.TableScanNode.createFilterable filters check failed.
        // 我们不能移除AND的操作数，否则会导致TableScanNode.createFilterable的过滤器检查失败
      ((RexCall) filter).getOperands().forEach(subFilter -> addFilter(subFilter, filterValues)); // 递归处理AND的每个子条件，将它们添加到filterValues数组中，但不移除AND本身
    } else if (filter.isA(SqlKind.EQUALS)) { // 如果当前过滤条件是等值操作符
      final RexCall call = (RexCall) filter; // 将filter强制转换为RexCall，以便获取操作数
      RexNode left = call.getOperands().get(0); // 获取等值条件的左操作数（通常是字段引用）
      if (left.isA(SqlKind.CAST)) { // 如果左操作数是CAST表达式（类型转换）
        left = ((RexCall) left).operands.get(0); // 去掉CAST包装，获取实际的表达式（字段引用）
      } // if结束
      final RexNode right = call.getOperands().get(1); // 获取等值条件的右操作数（应该是常量值）
      if (left instanceof RexInputRef // 验证左操作数是否为字段引用
          && right instanceof RexLiteral) { // 验证右操作数是否为字面量常量
        final int index = ((RexInputRef) left).getIndex(); // 获取字段引用的索引，表示这是第几个字段（从0开始）
        if (filterValues[index] == null) { // 检查该字段是否已经有过滤值（只能有一个过滤值）
          filterValues[index] = ((RexLiteral) right).getValue2().toString(); // 将字面量值转换为字符串，存入filterValues数组的对应位置
          return true; // 返回true表示成功添加过滤条件，可以下推到数据源
        } // if结束
      } // if结束
    } // else if结束
    return false; // 返回false表示无法添加过滤条件（不是等值条件、左操作数不是字段引用、右操作数不是常量、或字段已有过滤值）
  } // addFilter方法结束
} // 类定义结束
