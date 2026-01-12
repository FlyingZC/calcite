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
 */ // Apache许可证声明，规定了代码的使用权限和限制条件
package org.apache.calcite.adapter.csv; // 定义当前类所在的包路径，org.apache.calcite.adapter.csv表示这是Calcite框架中CSV适配器相关的代码

import org.apache.calcite.model.ModelHandler; // 导入ModelHandler类，用于处理模型（model.json）中的额外操作数（operands）
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型，用于描述表的结构（列名、列类型等）
import org.apache.calcite.rel.type.RelDataTypeImpl; // 导入RelDataTypeImpl类，RelDataType的实现类，提供将RelDataType转换为RelProtoDataType的方法
import org.apache.calcite.rel.type.RelProtoDataType; // 导入RelProtoDataType接口，表示关系数据类型的原型，可以延迟创建实际的RelDataType对象
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，表示Calcite中的模式（Schema），是Schema的扩展版本，允许动态添加表
import org.apache.calcite.schema.TableFactory; // 导入TableFactory接口，这是Calcite中用于创建表的工厂接口，所有自定义表工厂都需要实现此接口
import org.apache.calcite.util.Source; // 导入Source接口，表示数据源，可以是文件、URL或其他来源
import org.apache.calcite.util.Sources; // 导入Sources工具类，提供创建Source对象的静态方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，表示参数或返回值可以为null，用于空值检查

import java.io.File; // 导入File类，用于表示文件系统中的文件和目录
import java.util.Map; // 导入Map接口，表示键值对集合，用于存储配置参数

/**
 * Factory that creates a {@link CsvTranslatableTable}.
 * 工厂类，用于创建CsvTranslatableTable（可转换的CSV表）实例
 *
 * <p>Allows a CSV table to be included in a model.json file, even in a
 * schema that is not based upon {@link CsvSchema}.
 * 允许CSV表被包含在model.json文件中，即使该schema不是基于CsvSchema的
 * 这意味着可以在任何类型的schema中通过配置文件来定义CSV表，提供了更大的灵活性
 */
@SuppressWarnings("UnusedDeclaration") // 抑制未使用声明的警告，因为工厂类的方法可能通过反射调用，编译器无法检测到
public class CsvTableFactory implements TableFactory<CsvTable> { // 定义CsvTableFactory类，实现TableFactory接口，泛型参数CsvTable表示该工厂创建的是CsvTable类型的表
  // public constructor, per factory contract // 公共构造函数，符合工厂接口的契约要求，必须提供无参的公共构造函数
  public CsvTableFactory() { // 无参构造函数，用于创建CsvTableFactory实例
  } // 构造函数体为空，因为该工厂类不需要初始化任何成员变量

  @Override public CsvTable create(SchemaPlus schema, String name, // 覆盖TableFactory接口的create方法，用于创建CsvTable实例；schema参数表示包含该表的SchemaPlus对象；name参数表示表的名称
      Map<String, Object> operand, @Nullable RelDataType rowType) { // operand参数表示包含表配置信息的键值对映射；rowType参数表示表的行类型（可选，可以为null），描述表的结构
    String fileName = (String) operand.get("file"); // 从operand映射中获取"file"键对应的值，即CSV文件的文件名，并强制转换为String类型
    final File base = // 声明并初始化base变量，表示基准目录，用于解析相对路径的文件名
        (File) operand.get(ModelHandler.ExtraOperand.BASE_DIRECTORY.camelName); // 从operand映射中获取基准目录对象，使用ModelHandler的ExtraOperand枚举中定义的BASE_DIRECTORY常量，并获取其驼峰命名的键名
    final Source source = Sources.file(base, fileName); // 使用Sources工具类的file方法，基于基准目录base和文件名fileName创建Source对象，表示CSV文件的数据源
    final RelProtoDataType protoRowType = // 声明并初始化protoRowType变量，表示行类型的原型，用于延迟创建实际的RelDataType
        rowType != null ? RelDataTypeImpl.proto(rowType) : null; // 如果rowType不为null，则使用RelDataTypeImpl的proto方法将其转换为RelProtoDataType对象；否则设置为null，表示使用默认的行类型推断
    return new CsvScannableTable(source, protoRowType); // 创建并返回CsvScannableTable实例，传入source（CSV文件数据源）和protoRowType（行类型原型）；CsvScannableTable是CsvTable的一个实现，表示可扫描的CSV表
  } // create方法结束，返回创建的CsvTable对象
} // CsvTableFactory类定义结束
