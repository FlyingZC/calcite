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
package org.apache.calcite.adapter.csv; // 声明包名，表示这个类属于org.apache.calcite.adapter.csv包，是Calcite CSV适配器的一部分

import org.apache.calcite.adapter.file.CsvEnumerator; // 导入CsvEnumerator类，用于枚举CSV文件中的数据行
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory接口，用于创建Java相关的数据类型
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系型数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系型数据类型
import org.apache.calcite.rel.type.RelProtoDataType; // 导入RelProtoDataType接口，表示关系型数据类型的原型，可以延迟创建实际的数据类型
import org.apache.calcite.schema.impl.AbstractTable; // 导入AbstractTable抽象类，这是Calcite中所有表的基类
import org.apache.calcite.util.Source; // 导入Source类，表示数据源，可以是文件或其他数据来源

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值

import java.util.ArrayList; // 导入ArrayList类，用于动态数组
import java.util.List; // 导入List接口，表示列表集合

/**
 * Base class for table that reads CSV files. // CSV表基类：这是所有读取CSV文件的表的基类，提供了CSV表的基本功能
 */
public abstract class CsvTable extends AbstractTable { // 定义抽象类CsvTable，继承自AbstractTable，表示CSV表的抽象实现
  protected final Source source; // 成员变量：数据源，表示CSV文件的来源，使用protected final修饰，子类可以访问但不能修改
  protected final @Nullable RelProtoDataType protoRowType; // 成员变量：行数据类型的原型，用于延迟创建实际的行数据类型，可能为null，使用protected final修饰
  private @Nullable RelDataType rowType; // 成员变量：缓存的行数据类型，使用private修饰，避免重复计算，可能为null
  private @Nullable List<RelDataType> fieldTypes; // 成员变量：缓存的字段类型列表，使用private修饰，存储表中每个字段的数据类型，可能为null

  /** Creates a CsvTable. */ // 构造方法注释：创建CsvTable实例
  CsvTable(Source source, @Nullable RelProtoDataType protoRowType) { // 构造方法：初始化CsvTable对象，接收数据源和行数据类型原型作为参数
    this.source = source; // 将传入的数据源赋值给成员变量source
    this.protoRowType = protoRowType; // 将传入的行数据类型原型赋值给成员变量protoRowType
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写方法：获取表的行数据类型，接收RelDataTypeFactory作为参数用于创建数据类型
    if (protoRowType != null) { // 如果行数据类型原型不为null，表示已经预先定义了行类型
      return protoRowType.apply(typeFactory); // 使用原型创建并返回实际的行数据类型
    }
    if (rowType == null) { // 如果缓存的行数据类型为null，需要从CSV文件中推断行类型
      rowType = // 将推断出的行类型赋值给rowType成员变量进行缓存
          CsvEnumerator.deduceRowType((JavaTypeFactory) typeFactory, source, // 调用CsvEnumerator的静态方法deduceRowType，从CSV文件中推断行数据类型
              null, isStream()); // 传入null作为字段类型列表（不需要收集字段类型），传入isStream()判断是否为流式数据
    }
    return rowType; // 返回缓存的行数据类型
  }

  /** Returns the field types of this CSV table. */ // 方法注释：返回CSV表的字段类型列表
  public List<RelDataType> getFieldTypes(RelDataTypeFactory typeFactory) { // 公开方法：获取表中所有字段的数据类型列表，接收RelDataTypeFactory作为参数
    if (fieldTypes == null) { // 如果字段类型列表为null，需要从CSV文件中推断字段类型
      fieldTypes = new ArrayList<>(); // 创建新的ArrayList用于存储字段类型
      CsvEnumerator.deduceRowType((JavaTypeFactory) typeFactory, source, // 调用CsvEnumerator的静态方法deduceRowType，从CSV文件中推断行数据类型
          fieldTypes, isStream()); // 传入fieldTypes列表来收集每个字段的数据类型，传入isStream()判断是否为流式数据
    }
    return fieldTypes; // 返回缓存的字段类型列表
  }

  /** Returns whether the table represents a stream. */ // 方法注释：判断表是否表示流式数据
  protected boolean isStream() { // 受保护方法：子类可以重写此方法来指定表是否为流式数据
    return false; // 默认返回false，表示不是流式数据，子类可以重写返回true
  }

  /** Various degrees of table "intelligence". */ // 枚举注释：定义表的"智能"程度，表示表支持的不同功能级别
  public enum Flavor { // 公开枚举：定义CSV表的三种功能级别
    SCANNABLE, FILTERABLE, TRANSLATABLE // SCANNABLE可扫描：只能全表扫描；FILTERABLE可过滤：支持谓词下推；TRANSLATABLE可转换：支持将操作转换为CSV特定操作
  }
}
