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
// Apache许可证声明，表明该代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.csv; // 定义包名，该类位于CSV适配器包下

import org.apache.calcite.model.ModelHandler; // 导入模型处理器类，用于处理模型配置
import org.apache.calcite.schema.Schema; // 导入Schema接口，Calcite中Schema代表数据库模式
import org.apache.calcite.schema.SchemaFactory; // 导入SchemaFactory接口，用于创建Schema实例的工厂接口
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，Schema的扩展接口，支持嵌套Schema

import java.io.File; // 导入File类，用于文件操作
import java.util.Locale; // 导入Locale类，用于本地化处理
import java.util.Map; // 导入Map接口，用于存储键值对配置

/**
 * Factory that creates a {@link CsvSchema}. // 工厂类，负责创建CsvSchema实例
 *
 * <p>Allows a custom schema to be included in a <code><i>model</i>.json</code>
 * file. // 允许自定义schema被包含在model.json配置文件中
 */ // 这是一个用于创建CSV Schema的工厂类，通过实现SchemaFactory接口，Calcite可以在模型配置文件中动态创建CSV数据源
@SuppressWarnings("UnusedDeclaration") // 抑制未使用声明的警告，因为该类可能通过反射调用
public class CsvSchemaFactory implements SchemaFactory { // CsvSchemaFactory类实现了SchemaFactory接口，用于创建CSV数据源的Schema
  /** Public singleton, per factory contract. */ // 公共单例实例，符合工厂契约要求
  public static final CsvSchemaFactory INSTANCE = new CsvSchemaFactory(); // 创建CsvSchemaFactory的单例实例，命名为INSTANCE，供外部使用

  private CsvSchemaFactory() { // 私有构造方法，防止外部实例化，确保单例模式
  } // 私有构造方法为空，因为该类是无状态的工厂类

  @Override public Schema create(SchemaPlus parentSchema, String name, // 重写SchemaFactory接口的create方法，用于创建Schema实例，参数包括父Schema、Schema名称和操作数配置
      Map<String, Object> operand) { // operand是一个Map，包含了创建Schema所需的各种配置参数
    final String directory = (String) operand.get("directory"); // 从配置中获取directory参数，即CSV文件所在的目录路径
    final File base = // 获取基础目录File对象，用于处理相对路径
        (File) operand.get(ModelHandler.ExtraOperand.BASE_DIRECTORY.camelName); // 从配置中获取BASE_DIRECTORY参数，这是一个额外的操作数，用于指定基准目录
    File directoryFile = new File(directory); // 将directory字符串转换为File对象
    if (base != null && !directoryFile.isAbsolute()) { // 如果基础目录不为空且directoryFile不是绝对路径
      directoryFile = new File(base, directory); // 则将相对路径转换为基于base的绝对路径
    } // 路径转换完成，确保directoryFile是一个有效的绝对路径
    String flavorName = (String) operand.get("flavor"); // 从配置中获取flavor参数，指定CSV表的类型或行为模式
    CsvTable.Flavor flavor; // 声明CsvTable.Flavor类型的变量，用于存储CSV表的类型
    if (flavorName == null) { // 如果flavorName为空，即未指定flavor参数
      flavor = CsvTable.Flavor.SCANNABLE; // 则使用默认的SCANNABLE类型，表示表可以被扫描
    } else { // 如果flavorName不为空
      flavor = CsvTable.Flavor.valueOf(flavorName.toUpperCase(Locale.ROOT)); // 则将flavorName转换为大写并解析为对应的Flavor枚举值
    } // flavor确定完成，根据配置或默认值设置了CSV表的类型
    return new CsvSchema(directoryFile, flavor); // 创建并返回CsvSchema实例，传入目录文件和flavor类型
  } // create方法结束，返回创建的CsvSchema对象
} // CsvSchemaFactory类结束
