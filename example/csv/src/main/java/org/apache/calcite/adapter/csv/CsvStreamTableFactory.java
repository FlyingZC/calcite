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
package org.apache.calcite.adapter.csv; // 声明该类所属的包，位于org.apache.calcite.adapter.csv包下，这是Calcite CSV适配器的包路径

import org.apache.calcite.model.ModelHandler; // 导入ModelHandler类，用于处理模型文件中的额外操作数，特别是基础目录路径
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示Calcite中的关系数据类型，描述表的结构信息
import org.apache.calcite.rel.type.RelDataTypeImpl; // 导入RelDataTypeImpl类，提供RelDataType的实现，包含创建原型数据类型的方法
import org.apache.calcite.rel.type.RelProtoDataType; // 导入RelProtoDataType接口，表示关系数据类型的原型，用于延迟解析数据类型
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，表示Calcite中的模式（schema），可以包含多个表和其他模式
import org.apache.calcite.schema.TableFactory; // 导入TableFactory接口，这是Calcite中表的工厂接口，用于创建表实例
import org.apache.calcite.util.Source; // 导入Source类，表示Calcite中的数据源抽象，可以是文件、URL或其他数据来源
import org.apache.calcite.util.Sources; // 导入Sources工具类，提供创建Source实例的静态方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，标记参数或返回值可以为null，用于静态类型检查

import java.io.File; // 导入File类，用于文件系统操作，表示文件或目录路径
import java.util.Map; // 导入Map接口，用于存储键值对，这里用于存储表创建时的配置参数

/**
 * Factory that creates a {@link CsvTranslatableTable}.
 * 工厂类，用于创建CsvTranslatableTable实例，这是CSV流式表的工厂实现
 *
 * <p>Allows a CSV table to be included in a model.json file, even in a
 * schema that is not based upon {@link CsvSchema}.
 * 允许将CSV表包含在model.json配置文件中，即使该表所在的schema不是基于CsvSchema的
 * 这意味着可以在任何schema中通过配置文件的方式声明CSV表，而不必强制使用CsvSchema
 * 这个类实现了TableFactory接口，遵循Calcite的表工厂模式，用于动态创建表对象
 */
@SuppressWarnings("UnusedDeclaration") // 抑制未使用声明的警告，因为工厂类中的某些方法可能被反射调用，IDE无法识别
public class CsvStreamTableFactory implements TableFactory<CsvTable> { // 定义CsvStreamTableFactory类，实现TableFactory<CsvTable>接口，泛型参数指定创建的表类型为CsvTable
  // public constructor, per factory contract // 根据工厂接口契约，必须提供公共的无参构造函数
  public CsvStreamTableFactory() { // 公共无参构造函数，用于实例化工厂对象，Calcite通过反射调用此构造函数创建工厂实例
  } // 构造函数体为空，因为工厂类不需要维护任何状态，所有信息都通过create方法的参数传入

  @Override public CsvTable create(SchemaPlus schema, String name, // 重写create方法，创建CsvTable实例，参数包括：schema-表所属的schema，name-表名称
      Map<String, Object> operand, @Nullable RelDataType rowType) { // operand-配置参数的映射，rowType-可选的行类型定义，可为null
    String fileName = (String) operand.get("file"); // 从operand参数中获取"file"键对应的值，强制转换为String类型，这是CSV文件的路径
    File file = new File(fileName); // 使用文件路径创建File对象，表示CSV文件在文件系统中的位置
    final File base = // 声明一个final变量base，用于存储基础目录路径，用于解析相对路径
        (File) operand.get(ModelHandler.ExtraOperand.BASE_DIRECTORY.camelName); // 从operand中获取基础目录，使用ModelHandler中定义的BASE_DIRECTORY常量的驼峰命名形式
    if (base != null && !file.isAbsolute()) { // 如果基础目录不为null且文件路径是相对路径（不是绝对路径）
      file = new File(base, fileName); // 则将文件路径解析为相对于基础目录的绝对路径，创建新的File对象
    } // 结束if语句，此时file变量已经指向正确的绝对路径
    final Source source = Sources.of(file); // 使用Sources工具类的of方法，将File对象转换为Source对象，Source是Calcite统一的数据源抽象
    final RelProtoDataType protoRowType = // 声明一个final变量protoRowType，用于存储行类型的原型，原型可以延迟解析
        rowType != null ? RelDataTypeImpl.proto(rowType) : null; // 如果rowType不为null，则创建其原型；否则设为null，表示使用CSV文件中的类型推断
    return new CsvStreamScannableTable(source, protoRowType); // 创建并返回CsvStreamScannableTable实例，传入数据源和行类型原型，这是可扫描的CSV流式表实现
  } // 结束create方法，返回创建的CsvTable对象
} // 结束CsvStreamTableFactory类的定义
