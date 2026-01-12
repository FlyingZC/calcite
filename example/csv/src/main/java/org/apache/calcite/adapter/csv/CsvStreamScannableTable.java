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
package org.apache.calcite.adapter.csv; // 定义包名,表示这个类属于org.apache.calcite.adapter.csv包,是Calcite CSV适配器的一部分

import org.apache.calcite.DataContext; // 导入DataContext类,用于在查询执行过程中传递上下文信息
import org.apache.calcite.adapter.file.CsvEnumerator; // 导入CsvEnumerator类,用于枚举CSV文件中的数据行
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory类,用于创建Java类型系统中的类型
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入AbstractEnumerable抽象类,提供LINQ风格的枚举功能
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口,表示可枚举的数据集合
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口,用于遍历数据集合
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类,表示关系数据类型
import org.apache.calcite.rel.type.RelProtoDataType; // 导入RelProtoDataType类,表示可序列化的关系数据类型原型
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口,表示可以被扫描的表
import org.apache.calcite.schema.StreamableTable; // 导入StreamableTable接口,表示支持流式处理的表
import org.apache.calcite.schema.Table; // 导入Table接口,表示Calcite中的表抽象
import org.apache.calcite.util.ImmutableIntList; // 导入ImmutableIntList类,表示不可变的整数列表
import org.apache.calcite.util.Source; // 导入Source类,表示数据源(如文件)

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解,用于标记可空的类型

import java.util.List; // 导入List接口,用于表示列表集合
import java.util.concurrent.atomic.AtomicBoolean; // 导入AtomicBoolean类,用于线程安全的布尔值操作

/**
 * Table based on a CSV file.
 * 基于CSV文件的表实现,用于将CSV文件作为Calcite中的表进行查询
 *
 * <p>It implements the {@link ScannableTable} interface, so Calcite gets
 * data by calling the {@link #scan(DataContext)} method.
 * 它实现了ScannableTable接口,Calcite通过调用scan(DataContext)方法来获取数据
 *
 * <p>这个类是CsvScannableTable的流式版本,主要特点:
 * 1. 继承CsvScannableTable,复用CSV文件处理的基本功能
 * 2. 实现StreamableTable接口,支持流式数据处理
 * 3. 在scan方法中返回支持流式处理的Enumerator
 * 4. 通过isStream方法返回true标识这是流式表
 *
 * <p>流式表的特点:
 * - 数据可以源源不断地产生,不限于固定大小的数据集
 * - 适用于实时数据处理场景
 * - 支持取消操作(通过AtomicBoolean cancelFlag)
 * - 数据按到达顺序处理,支持无限数据流
 */
public class CsvStreamScannableTable extends CsvScannableTable // 定义CsvStreamScannableTable类,继承CsvScannableTable基类
    implements StreamableTable { // 实现StreamableTable接口,支持流式数据处理
  /** Creates a CsvScannableTable.
   * 构造方法注释:创建CsvStreamScannableTable实例
   * @param source CSV文件的数据源对象,包含文件的路径和信息
   * @param protoRowType 行数据类型的原型,用于延迟创建RelDataType对象,可能为null
   */
  CsvStreamScannableTable(Source source, // 构造方法参数:source表示CSV文件的源对象
      @Nullable RelProtoDataType protoRowType) { // 构造方法参数:protoRowType表示行数据类型原型,可为空
    super(source, protoRowType); // 调用父类CsvScannableTable的构造方法,初始化source和protoRowType
  } // 构造方法结束

  /**
   * 判断是否为流式表
   * @return 返回true,表示这是一个流式表,支持源源不断的数据处理
   * 
   * 这个方法是CsvScannableTable中定义的受保护方法,子类重写以标识表类型
   * 返回true表示表的数据是流式的,数据可能不会结束
   * 流式表的特点:
   * - 数据可以无限产生
   * - 查询可能会一直运行直到被取消
   * - 适用于实时数据监控、日志分析等场景
   */
  @Override protected boolean isStream() { // 重写父类的isStream方法,标记为流式表
    return true; // 返回true,明确标识这是一个流式表
  } // 方法结束

  /**
   * 返回对象的字符串表示
   * @return 返回"CsvStreamScannableTable",用于调试和日志输出
   */
  @Override public String toString() { // 重写toString方法,提供类的字符串表示
    return "CsvStreamScannableTable"; // 返回类名的字符串形式
  } // 方法结束

  /**
   * 扫描表数据并返回可枚举的数据集合
   * 这是Calcite查询执行时调用的核心方法,用于获取表中的数据
   * 
   * @param root DataContext对象,提供查询执行的上下文信息,包括类型工厂和取消标志
   * @return 返回一个Enumerable对象,可以枚举表中的每一行数据,每行数据是一个Object数组
   * 
   * 方法执行流程:
   * 1. 从DataContext中获取JavaTypeFactory,用于创建和处理Java类型
   * 2. 获取表中所有字段的类型信息(RelDataType列表)
   * 3. 创建字段索引列表(0, 1, 2, ..., n-1),用于标识每个字段的位置
   * 4. 从DataContext中获取取消标志(AtomicBoolean),用于支持查询取消操作
   * 5. 创建并返回一个AbstractEnumerable匿名子类,实现enumerator方法
   * 6. enumerator方法返回CsvEnumerator实例,负责实际读取和解析CSV文件
   * 
   * 关键点:
   * - 使用CsvEnumerator.arrayConverter创建行转换器,将CSV字符串转换为Object数组
   * - cancelFlag参数传递给CsvEnumerator,允许外部取消查询
   * - 第三个参数true表示这是流式处理模式
   * - 返回的Enumerator支持延迟加载,按需读取数据
   */
  @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法,实现数据扫描逻辑
    JavaTypeFactory typeFactory = root.getTypeFactory(); // 从DataContext中获取JavaTypeFactory,用于类型转换
    final List<RelDataType> fieldTypes = getFieldTypes(typeFactory); // 获取表中所有字段的RelDataType类型列表
    final List<Integer> fields = ImmutableIntList.identity(fieldTypes.size()); // 创建字段索引列表,从0到n-1,用于标识字段位置
    final AtomicBoolean cancelFlag = DataContext.Variable.CANCEL_FLAG.get(root); // 从DataContext中获取取消标志,支持查询取消操作
    return new AbstractEnumerable<@Nullable Object[]>() { // 创建并返回AbstractEnumerable的匿名子类,提供可枚举的数据集合
      @Override public Enumerator<@Nullable Object[]> enumerator() { // 重写enumerator方法,创建实际的枚举器
        return new CsvEnumerator<>(source, cancelFlag, true, null, // 创建CsvEnumerator实例:source为CSV源,cancelFlag支持取消,true表示流式模式,null表示无额外过滤器
            CsvEnumerator.arrayConverter(fieldTypes, fields, true)); // 使用arrayConverter创建行转换器,将CSV行转换为Object数组,true表示流式处理
      } // enumerator方法结束
    }; // 匿名类结束
  } // scan方法结束

  /**
   * 返回流式表对象
   * @return 返回this,即当前对象本身,因为当前对象已经实现了流式表功能
   * 
   * 这个方法是StreamableTable接口要求的实现
   * 当Calcite需要将表作为流式表处理时调用此方法
   * 返回的Table对象会被用于流式查询的执行
   * 
   * 流式表在查询优化和执行时的特殊处理:
   * - 查询不会等待所有数据就绪才开始处理
   * - 支持持续的数据输入
   * - 查询可能永远不会结束(对于无限数据流)
   */
  @Override public Table stream() { // 重写stream方法,返回流式表对象
    return this; // 返回当前对象本身,因为当前对象已经实现了StreamableTable接口
  } // stream方法结束
} // 类定义结束
