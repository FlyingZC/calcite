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
// Apache许可证声明，说明代码版权归属和使用许可条件
package org.apache.calcite.adapter.file;

// 导入Java类型工厂，用于创建和管理Java类型
import org.apache.calcite.adapter.java.JavaTypeFactory;
// 导入关系数据类型，表示表中的行类型
import org.apache.calcite.rel.type.RelDataType;
// 导入关系数据类型工厂接口，用于创建各种关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;
// 导入关系数据类型原型，用于延迟创建数据类型
import org.apache.calcite.rel.type.RelProtoDataType;
// 导入抽象表基类，所有表实现都需要继承此类
import org.apache.calcite.schema.impl.AbstractTable;
// 导入Source类，表示数据源（如文件）
import org.apache.calcite.util.Source;

// 导入可空注解，用于标记可能为null的字段
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入ArrayList动态数组类
import java.util.ArrayList;
// 导入List接口
import java.util.List;

/**
 * Base class for table that reads CSV files.
 * // 读取CSV文件的表基类，是所有CSV表实现的抽象基类，提供了CSV表的基本功能
 * // 该类继承自AbstractTable，实现了Calcite表接口，用于将CSV文件作为数据源
 * // 支持从CSV文件中读取数据并转换为Calcite可以处理的关系数据结构
 * // 提供了行类型推断、字段类型获取等核心功能
 *
 * <p>Copied from {@code CsvFilterableTable} in demo CSV adapter,
 * with more advanced features.
 * // 从演示CSV适配器中的CsvFilterableTable复制而来，并提供了更高级的功能
 * // 相比原始版本，这个实现支持更多特性，如流式处理、更灵活的类型推断等
 */
// 定义抽象类CsvTable，继承AbstractTable，表示CSV表的抽象实现
public abstract class CsvTable extends AbstractTable {
  // 成员变量：source，表示CSV文件的数据源，使用protected修饰符允许子类访问
  // Source是Calcite提供的抽象数据源接口，可以表示文件、URL等多种数据源
  // final关键字表示该引用不可变，但Source对象本身的内容可能可变
  protected final Source source;
  // 成员变量：protoRowType，表示行类型的原型，使用protected修饰符允许子类访问
  // RelProtoDataType是一个函数式接口，可以延迟创建RelDataType对象
  // 使用@Nullable注解表示该字段可能为null，当为null时需要从CSV文件推断类型
  // 这种延迟创建机制可以避免在不需要时创建类型对象，提高性能
  protected final @Nullable RelProtoDataType protoRowType;
  // 成员变量：rowType，表示表的行类型，使用private修饰符封装实现细节
  // RelDataType表示关系数据类型，包含字段名、字段类型等信息
  // 使用@Nullable注解表示该字段可能为null，初始时为null，需要时才创建
  // 使用延迟初始化模式，避免在构造时进行昂贵的类型推断操作
  private @Nullable RelDataType rowType;
  // 成员变量：fieldTypes，表示表中所有字段的类型列表，使用private修饰符封装实现细节
  // List<RelDataType>存储每个字段的数据类型，用于查询优化和执行
  // 使用@Nullable注解表示该字段可能为null，初始时为null，需要时才创建
  // 使用延迟初始化模式，只在第一次调用getFieldTypes方法时创建
  private @Nullable List<RelDataType> fieldTypes;

  /** Creates a CsvTable. */
  // 构造方法：创建CsvTable实例，初始化CSV表的基本信息
  // 参数source：CSV文件的数据源，指定要读取的CSV文件位置
  // 参数protoRowType：行类型原型，可以为null，为null时需要从文件推断类型
  // 该构造方法是protected访问级别，只能被子类调用，符合抽象类的设计原则
  CsvTable(Source source, @Nullable RelProtoDataType protoRowType) {
    // 将传入的source参数赋值给成员变量source，初始化数据源
    this.source = source;
    // 将传入的protoRowType参数赋值给成员变量protoRowType，初始化行类型原型
    // 如果protoRowType不为null，后续可以直接使用它创建行类型，无需从文件推断
    this.protoRowType = protoRowType;
  }

  // 重写方法：获取表的行类型，返回包含所有字段信息的关系数据类型
  // 该方法是Calcite Table接口的核心方法，用于描述表的结构（schema）
  // 参数typeFactory：关系数据类型工厂，用于创建RelDataType对象
  // 返回值：表的行类型对象，包含字段名、字段类型等信息
  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    // 检查protoRowType是否不为null，如果存在预定义的行类型原型
    if (protoRowType != null) {
      // 直接应用protoRowType到typeFactory，创建并返回RelDataType对象
      // 这种方式避免了从CSV文件推断类型的开销，性能更高
      // protoRowType.apply()是一个工厂方法，根据typeFactory创建具体的数据类型
      return protoRowType.apply(typeFactory);
    }
    // 如果protoRowType为null，检查rowType是否已初始化（延迟初始化模式）
    if (rowType == null) {
      // 调用CsvEnumerator的deduceRowType方法从CSV文件推断行类型
      // 参数1：(JavaTypeFactory) typeFactory，类型转换后的Java类型工厂，用于创建Java类型
      // 参数2：source，CSV文件数据源，从中读取数据以推断类型
      // 参数3：null，表示不需要单独收集字段类型列表，只需要返回行类型
      // 参数4：isStream()，判断是否为流式表，流式表和批处理表的类型推断逻辑可能不同
      // deduceRowType方法会读取CSV文件的头部和数据，推断每列的数据类型
      rowType =
          CsvEnumerator.deduceRowType((JavaTypeFactory) typeFactory, source,
              null, isStream());
    }
    // 返回推断或缓存得到的行类型对象
    return rowType;
  }

  /** Returns the field types of this CSV table. */
  // 方法：获取CSV表的字段类型列表，返回表中每个字段的类型信息
  // 该方法可用于查询优化器了解字段类型，以便进行类型转换和优化
  // 参数typeFactory：关系数据类型工厂，用于创建RelDataType对象
  // 返回值：字段类型列表，每个元素对应表中一个字段的数据类型
  public List<RelDataType> getFieldTypes(RelDataTypeFactory typeFactory) {
    // 检查fieldTypes是否已初始化（延迟初始化模式）
    if (fieldTypes == null) {
      // 创建新的ArrayList用于存储字段类型
      fieldTypes = new ArrayList<>();
      // 调用CsvEnumerator的deduceRowType方法从CSV文件推断字段类型
      // 参数1：(JavaTypeFactory) typeFactory，类型转换后的Java类型工厂
      // 参数2：source，CSV文件数据源，从中读取数据以推断类型
      // 参数3：fieldTypes，将推断出的字段类型填充到这个列表中
      // 参数4：isStream()，判断是否为流式表，影响类型推断逻辑
      // 与getRowType方法不同，这里将推断结果填充到fieldTypes列表中
      CsvEnumerator.deduceRowType((JavaTypeFactory) typeFactory, source,
          fieldTypes, isStream());
    }
    // 返回字段类型列表，如果已初始化则直接返回缓存的列表
    return fieldTypes;
  }

  /** Returns whether the table represents a stream. */
  // 方法：判断表是否表示流式数据源，返回true表示是流式表，false表示批处理表
  // 流式表和批处理表在数据处理方式上有本质区别：
  // 流式表：数据持续流入，需要实时处理，可能无法回溯
  // 批处理表：数据一次性加载完成，可以多次扫描
  // 默认实现返回false，表示是批处理表，子类可以重写此方法以支持流式处理
  protected boolean isStream() {
    // 默认返回false，表示这是一个批处理表，数据可以多次扫描
    // 子类如CsvStreamTable会重写此方法返回true，支持流式数据处理
    return false;
  }

  /** Various degrees of table "intelligence". */
  // 枚举：定义表的不同"智能"级别，表示表支持的功能特性程度
  // 这个枚举用于区分不同类型的CSV表实现，每个级别支持不同的查询优化能力
  // SCANNABLE：可扫描表，最基本的表类型，只能进行全表扫描
  // FILTERABLE：可过滤表，支持在数据源层面应用过滤条件，减少数据传输
  // TRANSLATABLE：可转换表，支持将SQL操作转换为数据源的原生操作，性能最优
  public enum Flavor {
    // SCANNABLE：可扫描级别，表只支持全表扫描，所有过滤和投影都在读取数据后进行
    // 这是最基础的表实现，适用于不支持下推优化的数据源
    SCANNABLE,
    // FILTERABLE：可过滤级别，表支持在读取数据时应用过滤条件
    // 可以将WHERE条件下推到数据源，只读取符合条件的数据，减少I/O和内存消耗
    FILTERABLE,
    // TRANSLATABLE：可转换级别，表支持将SQL操作转换为数据源的原生操作
    // 这是最高级的表实现，可以将多个操作（过滤、投影、聚合等）下推到数据源
    // 例如，对于数据库表，可以将SQL转换为数据库的查询语句
    TRANSLATABLE
  }
}
