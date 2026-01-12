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
package org.apache.calcite.adapter.tpch; // 声明包名，该类属于 org.apache.calcite.adapter.tpch 包，是 Calcite 的 TPC-H 数据适配器包

import org.apache.calcite.schema.Schema; // 导入 Schema 接口，表示数据库模式对象，包含表、视图等
import org.apache.calcite.schema.SchemaFactory; // 导入 SchemaFactory 接口，用于创建 Schema 实例的工厂接口
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 接口，扩展了 Schema 接口，允许添加子模式
import org.apache.calcite.util.Util; // 导入 Util 工具类，提供各种实用工具方法

import java.util.Map; // 导入 Map 接口，用于存储键值对映射

/**
 * Factory that creates a {@link TpchSchema}. // 工厂类，用于创建 TpchSchema 实例，TpchSchema 是基于 TPC-H 基准测试数据集的 Schema 实现
 * // TPC-H 是一个决策支持基准测试，包含8个业务表（如 orders、lineitem、customer、part 等）
 * // 该工厂类允许通过 model.json 配置文件动态创建 TPC-H 数据库模式
 *
 * <p>Allows a custom schema to be included in a model.json file. // 允许在 model.json 配置文件中包含自定义的 Schema
 * // model.json 是 Calcite 的模型配置文件，用于定义数据源、Schema、视图等元数据信息
 * // 通过此工厂类，可以在模型文件中声明使用 TPC-H 数据集作为 Schema
 */
@SuppressWarnings("UnusedDeclaration") // 抑制未使用声明的警告，因为该类通常通过反射机制被加载和调用
public class TpchSchemaFactory implements SchemaFactory { // 声明 TpchSchemaFactory 类，实现 SchemaFactory 接口，成为 Schema 的创建工厂
  // public constructor, per factory contract // 公开构造函数，符合工厂契约要求，工厂类必须有无参的公开构造函数
  public TpchSchemaFactory() { // 定义无参构造函数，用于创建 TpchSchemaFactory 实例，通常通过反射机制调用
  } // 构造函数体为空，不需要执行任何初始化操作

  @Override public Schema create(SchemaPlus parentSchema, String name, // 重写 SchemaFactory 接口的 create 方法，创建并返回一个 Schema 实例
      Map<String, Object> operand) { // operand 参数包含创建 Schema 所需的配置参数，从 model.json 文件中读取
    Map map = (Map) operand; // 将 operand 转换为 Map 类型，便于后续访问配置参数
    double scale = Util.first((Double) map.get("scale"), 1D); // 从配置中获取 scale 参数（数据集缩放因子），如果未指定则默认为 1.0；scale 控制数据集大小，1 代表约1GB数据
    int part = Util.first((Integer) map.get("part"), 1); // 从配置中获取 part 参数（当前分区编号），如果未指定则默认为1；用于将大数据集分区处理，part 表示当前是第几个分区
    int partCount = Util.first((Integer) map.get("partCount"), 1); // 从配置中获取 partCount 参数（总分区数），如果未指定则默认为1；partCount 表示数据集总共被分成多少个分区
    boolean columnPrefix = Util.first((Boolean) map.get("columnPrefix"), true); // 从配置中获取 columnPrefix 参数（是否使用列前缀），如果未指定则默认为true；columnPrefix 控制是否在表字段名前添加表名前缀以避免字段名冲突
    return new TpchSchema(scale, part, partCount, columnPrefix); // 使用获取的参数创建并返回 TpchSchema 实例，该 Schema 包含所有 TPC-H 基准测试表
  } // 方法结束，返回创建的 TpchSchema 对象
} // 类定义结束
