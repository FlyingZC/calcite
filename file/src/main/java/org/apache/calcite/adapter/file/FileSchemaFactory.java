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
package org.apache.calcite.adapter.file; // 声明包名，该类属于org.apache.calcite.adapter.file包，这是Calcite文件适配器的核心包

import org.apache.calcite.model.ModelHandler; // 导入ModelHandler类，用于处理模型配置中的额外操作数
import org.apache.calcite.schema.Schema; // 导入Schema接口，Calcite中所有模式(schema)的基接口
import org.apache.calcite.schema.SchemaFactory; // 导入SchemaFactory接口，用于创建Schema的工厂接口
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，Schema的扩展接口，提供了父级schema的上下文信息

import java.io.File; // 导入File类，用于文件系统操作
import java.util.List; // 导入List接口，用于存储表定义的列表
import java.util.Map; // 导入Map接口，用于存储配置参数的键值对

/**
 * Factory that creates a {@link FileSchema}.
 * 创建FileSchema的工厂类，实现了SchemaFactory接口
 *
 * <p>Allows a custom schema to be included in a model.json file.
 * 允许将自定义schema包含在model.json配置文件中，通过JSON配置可以动态加载文件数据源
 * See <a href="http://calcite.apache.org/docs/file_adapter.html">File adapter</a>.
 * 参考文档：Calcite文件适配器的官方文档
 */
@SuppressWarnings("UnusedDeclaration") // 抑制未使用声明的警告，因为工厂类通过反射调用，某些方法可能看起来未使用
public class FileSchemaFactory implements SchemaFactory { // 定义FileSchemaFactory类，实现SchemaFactory接口，负责创建基于文件的Schema
  /** Public singleton, per factory contract. */
  // 公有单例实例，按照SchemaFactory的契约要求，工厂类通常提供单例模式以减少对象创建开销
  public static final FileSchemaFactory INSTANCE = new FileSchemaFactory(); // 创建并初始化FileSchemaFactory的单例实例

  /** Name of the column that is implicitly created in a CSV stream table
   * to hold the data arrival time. */
  // 静态常量：在CSV流式表中隐式创建的列名，用于存储数据到达时间戳，支持流式数据处理场景
  static final String ROWTIME_COLUMN_NAME = "ROWTIME"; // 定义列名为"ROWTIME"，这是流式数据的系统时间列

  private FileSchemaFactory() { // 私有构造方法，实现单例模式，防止外部直接实例化
    // 私有构造方法体为空，单例初始化在静态字段INSTANCE处完成
  }

  @Override public Schema create(SchemaPlus parentSchema, String name, // 重写SchemaFactory接口的create方法，创建FileSchema实例
      Map<String, Object> operand) { // operand参数包含从model.json配置文件中读取的配置参数，是一个键值对Map
    @SuppressWarnings("unchecked") List<Map<String, Object>> tables = // 从operand中获取"tables"参数，包含所有表的定义信息，每个表用一个Map表示
        (List) operand.get("tables"); // 强制类型转换，将Object类型转换为List<Map<String, Object>>，存储表定义列表
    final File baseDirectory = // 从operand中获取基础目录参数，用于解析相对路径
        (File) operand.get(ModelHandler.ExtraOperand.BASE_DIRECTORY.camelName); // 通过ModelHandler获取BASE_DIRECTORY参数，这是model.json文件所在目录
    final String directory = (String) operand.get("directory"); // 从operand中获取"directory"参数，指定数据文件所在的目录路径
    File directoryFile = null; // 初始化directoryFile为null，用于存储最终的目录文件对象
    if (directory != null) { // 如果directory参数不为空，说明用户指定了数据目录
      directoryFile = new File(directory); // 创建File对象，将目录字符串转换为File对象
    } // 结束if语句，directoryFile可能被赋值也可能仍为null
    if (baseDirectory != null) { // 如果baseDirectory不为空，说明有基础目录可以用来解析相对路径
      if (directoryFile == null) { // 如果用户没有指定directory参数，则使用baseDirectory作为数据目录
        directoryFile = baseDirectory; // 将directoryFile设置为baseDirectory，使用基础目录
      } else if (!directoryFile.isAbsolute()) { // 如果directoryFile不是绝对路径，则需要与baseDirectory组合
        directoryFile = new File(baseDirectory, directory); // 创建新的File对象，将相对路径转换为相对于baseDirectory的绝对路径
      } // 结束else if，完成路径解析
    } // 结束if语句，directoryFile现在包含最终的目录路径
    return new FileSchema(parentSchema, name, directoryFile, tables); // 创建并返回FileSchema实例，传入父schema、schema名称、目录文件和表定义列表
  } // 结束create方法
} // 结束FileSchemaFactory类定义
