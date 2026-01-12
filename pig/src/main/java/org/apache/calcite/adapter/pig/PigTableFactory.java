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
// Apache Calcite Pig适配器包，包含与Apache Pig集成的相关类
package org.apache.calcite.adapter.pig;

// 导入ModelHandler类，用于处理模型文件中的额外操作数（如基础目录）
import org.apache.calcite.model.ModelHandler;
// 导入RelDataType类，用于表示关系数据类型
import org.apache.calcite.rel.type.RelDataType;
// 导入SchemaPlus类，用于表示可扩展的模式（Schema）
import org.apache.calcite.schema.SchemaPlus;
// 导入TableFactory接口，用于创建表的工厂接口
import org.apache.calcite.schema.TableFactory;

// 导入Nullable注解，用于标记可能为null的参数
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入File类，用于处理文件路径
import java.io.File;
// 导入List接口，用于存储字段名称列表
import java.util.List;
// 导入Map接口，用于存储操作数（operand）参数
import java.util.Map;

/**
 * Factory that creates a {@link PigTable}.
 * // 创建PigTable的工厂类，实现了TableFactory接口
 *
 * <p>Allows a Pig table to be included in a model.json file.
 * // 允许在model.json配置文件中包含Pig表的定义
 * // PigTableFactory是Calcite适配器模式中的关键组件，负责根据配置信息创建PigTable实例
 * // 通过这个工厂类，Calcite可以将Pig脚本或Pig数据源作为表集成到SQL查询中
 * // 使用工厂模式使得表的创建过程解耦，便于扩展和维护
 */
// PigTableFactory类实现了TableFactory<PigTable>接口，泛型参数指定了创建的表类型为PigTable
public class PigTableFactory implements TableFactory<PigTable> {
  // public constructor, per factory contract
  // 公共构造方法，符合工厂接口的契约要求，TableFactory接口要求必须有无参的公共构造方法
  public PigTableFactory() {
    // 空构造方法，不需要任何初始化操作
  }

  // @SuppressWarnings注解用于抑制未检查的类型转换警告，因为从Map中获取的值会被强制转换为List<String>
  @SuppressWarnings("unchecked")
  // @Override注解表示该方法实现了TableFactory接口的create方法
  // create方法用于根据提供的参数创建PigTable实例
  // 参数说明：
  //   - SchemaPlus schema: 包含此表的Schema对象，可以用于获取Schema级别的信息或注册表
  //   - String name: 要创建的表的名称，用于在Schema中标识这个表
  //   - Map<String, Object> operand: 包含表配置信息的映射，通常从model.json文件中读取
  //   - RelDataType rowType: 可选参数，指定表的行类型（字段类型信息），可能为null
  // 返回值: PigTable 创建的PigTable实例
  @Override public PigTable create(SchemaPlus schema, String name,
      Map<String, Object> operand, @Nullable RelDataType rowType) {
    // 从operand映射中获取"file"键对应的值，这个值应该是Pig脚本文件的路径字符串
    String fileName = (String) operand.get("file");
    // 使用文件路径字符串创建File对象，用于后续的文件路径处理
    File file = new File(fileName);
    // 从operand映射中获取基础目录（BASE_DIRECTORY）的值
    // ModelHandler.ExtraOperand.BASE_DIRECTORY.camelName获取基础目录的键名（通常是"baseDirectory"）
    // 基础目录用于解析相对路径，当文件路径不是绝对路径时，会相对于这个基础目录
    final File base =
        (File) operand.get(ModelHandler.ExtraOperand.BASE_DIRECTORY.camelName);
    // 检查基础目录是否存在且文件路径不是绝对路径
    // 如果条件成立，说明需要将相对路径转换为绝对路径
    if (base != null && !file.isAbsolute()) {
      // 使用基础目录和文件名创建新的File对象，将相对路径解析为绝对路径
      // 这样可以确保无论model.json文件在哪里，都能正确找到Pig脚本文件
      file = new File(base, fileName);
    }
    // 从operand映射中获取"columns"键对应的值，这个值应该是字段名称列表
    // 字段名称列表指定了Pig表包含哪些列，列的顺序很重要
    final List<String> fieldNames = (List<String>) operand.get("columns");
    // 创建PigTable实例，传入文件的绝对路径和字段名称数组
    // file.getAbsolutePath()获取文件的绝对路径字符串
    // fieldNames.toArray(new String[0])将List<String>转换为String数组
    final PigTable result = new PigTable(file.getAbsolutePath(), fieldNames.toArray(new String[0]));
    // 将创建的PigTable实例注册到PigSchema中
    // schema.unwrap(PigSchema.class)从SchemaPlus中获取底层的PigSchema对象
    // registerTable方法将表名称和表实例的映射关系存储在PigSchema中
    // 这样后续查询时可以通过表名称找到对应的PigTable
    schema.unwrap(PigSchema.class).registerTable(name, result);
    // 返回创建的PigTable实例
    return result;
  }
}
