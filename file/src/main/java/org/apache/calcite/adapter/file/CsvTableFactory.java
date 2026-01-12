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
package org.apache.calcite.adapter.file; // 声明包名，该类属于org.apache.calcite.adapter.file包，用于文件适配器相关功能

import org.apache.calcite.model.ModelHandler; // 导入ModelHandler类，用于处理模型相关操作，获取基础目录等元数据
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型，描述表中字段的数据类型
import org.apache.calcite.rel.type.RelDataTypeImpl; // 导入RelDataTypeImpl类，RelDataType的实现类，提供类型原型功能
import org.apache.calcite.rel.type.RelProtoDataType; // 导入RelProtoDataType接口，表示关系数据类型的原型，用于延迟类型解析
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，表示Calcite中的模式，可以包含多个表
import org.apache.calcite.schema.TableFactory; // 导入TableFactory接口，表工厂接口，用于创建表实例
import org.apache.calcite.util.Source; // 导入Source类，表示数据源，抽象了对文件或其他数据源的访问
import org.apache.calcite.util.Sources; // 导入Sources工具类，提供创建Source对象的静态方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，标记参数或返回值可以为null

import java.io.File; // 导入File类，用于文件系统操作
import java.util.Map; // 导入Map接口，用于存储键值对，这里用于存储表配置参数

/**
 * Factory that creates a {@link CsvTranslatableTable}. // 工厂类，用于创建CsvTranslatableTable实例
 *
 * <p>Allows a file-based table to be included in a model.json file, even in a // 允许基于文件的表被包含在model.json文件中，即使是在非FileSchema的schema中
 * schema that is not based upon {@link FileSchema}. // 即使该schema不是基于FileSchema的也能使用
 */
@SuppressWarnings("UnusedDeclaration") // 抑制未使用声明的警告，因为工厂类可能通过反射调用
public class CsvTableFactory implements TableFactory<CsvTable> { // CsvTableFactory类，实现TableFactory接口，泛型参数为CsvTable，表示创建CSV表
  // public constructor, per factory contract // 公共构造函数，符合工厂接口的契约要求
  public CsvTableFactory() { // 无参构造函数，用于创建CsvTableFactory实例
  } // 构造函数体为空，不需要初始化操作

  @Override public CsvTable create(SchemaPlus schema, String name, // 重写create方法，创建CsvTable实例，参数包括schema、表名、操作数和行类型
      Map<String, Object> operand, @Nullable RelDataType rowType) { // operand是包含表配置的Map，rowType是可选的行类型定义
    String fileName = (String) operand.get("file"); // 从operand中获取"file"键对应的文件名，指定CSV文件路径
    final File base = // 声明base变量，表示基础目录，用于解析相对路径
        (File) operand.get(ModelHandler.ExtraOperand.BASE_DIRECTORY.camelName); // 从operand中获取基础目录，BASE_DIRECTORY是ModelHandler的额外操作数
    final Source source = Sources.file(base, fileName); // 使用Sources工具类创建Source对象，基于base目录和fileName构建数据源
    final RelProtoDataType protoRowType = // 声明protoRowType变量，表示行的原型数据类型，用于延迟类型解析
        rowType != null ? RelDataTypeImpl.proto(rowType) : null; // 如果rowType不为null，则创建其原型；否则为null，表示使用CSV文件推断的类型
    return new CsvTranslatableTable(source, protoRowType); // 返回新创建的CsvTranslatableTable实例，传入数据源和原型行类型
  } // create方法结束，返回创建的CSV表对象
} // CsvTableFactory类定义结束
