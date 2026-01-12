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
// 声明包名，表示这个类属于 org.apache.calcite.adapter.csv 包，是Calcite CSV适配器的一部分
package org.apache.calcite.adapter.csv;

// 导入DataContext类，用于提供查询执行时的上下文信息，包含类型工厂、取消标志等
import org.apache.calcite.DataContext;
// 导入CsvEnumerator类，用于枚举CSV文件中的数据行，将CSV数据转换为对象数组
import org.apache.calcite.adapter.file.CsvEnumerator;
// 导入JavaTypeFactory接口，用于创建Java类型系统中的类型对象
import org.apache.calcite.adapter.java.JavaTypeFactory;
// 导入AbstractEnumerable抽象类，用于实现可枚举的数据集合，支持LINQ风格的查询
import org.apache.calcite.linq4j.AbstractEnumerable;
// 导入Enumerable接口，表示可以被枚举的数据集合，提供数据遍历能力
import org.apache.calcite.linq4j.Enumerable;
// 导入Enumerator接口，用于枚举数据集合中的元素，支持逐行读取数据
import org.apache.calcite.linq4j.Enumerator;
// 导入RelDataType类，表示关系型数据类型，描述字段的数据类型信息
import org.apache.calcite.rel.type.RelDataType;
// 导入RelProtoDataType接口，表示关系型数据类型的原型，用于延迟创建数据类型
import org.apache.calcite.rel.type.RelProtoDataType;
// 导入ScannableTable接口，表示可以被扫描的表，通过scan方法获取数据
import org.apache.calcite.schema.ScannableTable;
// 导入ImmutableIntList类，表示不可变的整数列表，用于存储字段索引
import org.apache.calcite.util.ImmutableIntList;
// 导入Source类，表示数据源，封装了文件的路径和内容
import org.apache.calcite.util.Source;

// 导入Nullable注解，用于标记参数或返回值可以为null
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入List接口，用于存储字段类型列表
import java.util.List;
// 导入AtomicBoolean类，用于线程安全的布尔值，支持原子操作，用于取消标志
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Table based on a CSV file.
 * // 基于CSV文件的表实现，这是Calcite中CSV适配器的核心类之一
 *
 * <p>It implements the {@link ScannableTable} interface, so Calcite gets
 * data by calling the {@link #scan(DataContext)} method.
 * // 它实现了ScannableTable接口，Calcite通过调用scan(DataContext)方法来获取数据
 * // 这个类的作用是将CSV文件包装成一个可被Calcite查询引擎扫描的表
 * // 继承自CsvTable基类，复用了CSV表的基本功能，如类型推断、字段获取等
 * // 实现了ScannableTable接口，提供了scan方法用于扫描CSV文件数据
 */
// 定义CsvScannableTable类，继承CsvTable基类，实现ScannableTable接口
// CsvTable提供了CSV表的基本功能，ScannableTable接口定义了扫描数据的能力
public class CsvScannableTable extends CsvTable
    implements ScannableTable {
  /** Creates a CsvScannableTable. */
  // 构造方法，创建一个CsvScannableTable实例
  // 参数source: CSV文件的Source对象，包含文件路径和内容
  // 参数protoRowType: 行数据类型的原型，用于延迟创建RelDataType，可以为null
  // 这个构造方法直接调用父类CsvTable的构造方法，初始化数据源和行类型
  CsvScannableTable(Source source, @Nullable RelProtoDataType protoRowType) {
    // 调用父类CsvTable的构造方法，传递数据源和行类型原型
    // 父类会保存这些参数，用于后续的类型推断和数据访问
    super(source, protoRowType);
  }

  // 重写toString方法，返回类的字符串表示
  // 这个方法主要用于调试和日志输出，便于识别表的类型
  @Override public String toString() {
    // 返回固定的字符串"CsvScannableTable"，表示这是一个可扫描的CSV表
    return "CsvScannableTable";
  }

  // 实现ScannableTable接口的scan方法，扫描CSV文件并返回可枚举的数据集合
  // 参数root: 数据上下文对象，包含查询执行时的环境信息，如类型工厂、取消标志等
  // 返回值: Enumerable<Object[]>，表示可枚举的对象数组集合，每个数组代表一行数据
  // 这个方法是Calcite查询引擎获取CSV数据的入口，通过它逐行读取CSV文件
  @Override public Enumerable<@Nullable Object[]> scan(DataContext root) {
    // 从数据上下文中获取Java类型工厂，用于创建和获取字段的数据类型
    // 类型工厂是Calcite类型系统的核心，负责管理所有数据类型的创建和转换
    JavaTypeFactory typeFactory = root.getTypeFactory();
    // 获取当前表中所有字段的类型列表，使用类型工厂来解析字段类型
    // fieldTypes是一个RelDataType列表，每个元素对应一个字段的数据类型
    // 例如: [INTEGER, VARCHAR, DATE]等
    final List<RelDataType> fieldTypes = getFieldTypes(typeFactory);
    // 创建一个整数列表，包含从0到fieldTypes.size()-1的所有索引
    // 这个列表表示要查询的字段索引，identity方法返回[0, 1, 2, ...]
    // 用于在枚举数据时知道每一列的数据应该映射到哪个字段
    final List<Integer> fields = ImmutableIntList.identity(fieldTypes.size());
    // 从数据上下文中获取取消标志，这是一个线程安全的布尔值
    // 当查询被取消时，这个标志会被设置为true，枚举器会停止读取数据
    // 用于支持查询的异步取消，避免长时间运行的查询无法中断
    final AtomicBoolean cancelFlag = DataContext.Variable.CANCEL_FLAG.get(root);
    // 创建并返回一个AbstractEnumerable的匿名子类实例
    // AbstractEnumerable是一个抽象类，实现了Enumerable接口
    // 它通过实现enumerator方法来提供数据的枚举能力
    return new AbstractEnumerable<@Nullable Object[]>() {
      // 重写enumerator方法，创建并返回一个数据枚举器
      // 枚举器负责逐行读取CSV文件，并将每行数据转换为Object数组
      // @Nullable注解表示数组中的元素可能为null
      @Override public Enumerator<@Nullable Object[]> enumerator() {
        // 创建并返回一个CsvEnumerator实例，用于枚举CSV文件数据
        // 参数source: CSV文件的数据源对象，包含文件路径和内容
        // 参数cancelFlag: 取消标志，用于在查询被取消时停止数据读取
        // 参数false: 表示不跳过第一行（false表示不跳过，true表示跳过）
        // 参数null: 表示没有自定义的过滤条件
        // 参数CsvEnumerator.arrayConverter(...): 类型转换器，将CSV字符串转换为对应的Java对象
        //   arrayConverter方法创建一个转换器，根据fieldTypes将每列数据转换为正确的类型
        //   fields参数指定了字段的索引顺序
        //   false参数表示不启用额外的转换选项
        return new CsvEnumerator<>(source, cancelFlag, false, null,
            CsvEnumerator.arrayConverter(fieldTypes, fields, false));
      }
    };
  }
}
