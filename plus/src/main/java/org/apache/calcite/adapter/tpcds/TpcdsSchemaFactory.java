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
// 声明包名，表示这个类属于 org.apache.calcite.adapter.tpcds 包，是 Calcite 框架中 TPC-DS 数据集适配器的一部分
package org.apache.calcite.adapter.tpcds;

// 导入 Schema 接口，表示 Calcite 中的模式（数据库 schema）抽象
import org.apache.calcite.schema.Schema;
// 导入 SchemaFactory 接口，这是 Calcite 中用于创建 Schema 的工厂接口
import org.apache.calcite.schema.SchemaFactory;
// 导入 SchemaPlus 接口，这是 Schema 的扩展接口，提供了添加子 schema 的能力
import org.apache.calcite.schema.SchemaPlus;
// 导入 Util 工具类，提供各种实用方法
import org.apache.calcite.util.Util;

// 导入 Map 接口，用于存储键值对数据
import java.util.Map;

/**
 * Factory that creates a {@link TpcdsSchema}.
 * // 这是一个工厂类，用于创建 TpcdsSchema 实例，TpcdsSchema 是基于 TPC-DS 基准测试数据集的 Calcite Schema 实现
 *
 * <p>Allows a custom schema to be included in a model.json file.
 * // 允许将自定义的 schema 包含在 model.json 配置文件中，这样 Calcite 就可以通过配置文件动态加载 TPC-DS 数据集
 */
// 抑制未使用声明的警告，因为这个类可能通过反射被调用，IDE 可能会误报某些方法未使用
@SuppressWarnings("UnusedDeclaration")
// 定义 TpcdsSchemaFactory 类，实现 SchemaFactory 接口，这是一个工厂类，负责创建 TpcdsSchema 实例
public class TpcdsSchemaFactory implements SchemaFactory {
  // public constructor, per factory contract
  // // 公共构造方法，符合工厂接口的契约要求，Calcite 需要一个无参的公共构造方法来实例化工厂类
  public TpcdsSchemaFactory() {
    // // 空构造方法体，不需要任何初始化操作
  }

  // // 重写 SchemaFactory 接口的 create 方法，用于创建 TpcdsSchema 实例
  // // parentSchema: 父 schema 对象，提供上下文信息
  // // name: schema 的名称，在 model.json 中配置
  // // operand: 包含配置参数的 Map，从 model.json 中读取的配置信息
  @Override public Schema create(SchemaPlus parentSchema, String name,
      Map<String, Object> operand) {
    // // 抑制原始类型可以使用泛型的警告，这里为了兼容性使用原始 Map 类型
    @SuppressWarnings("RawTypeCanBeGeneric") final Map map = operand;
    // // 从配置参数中获取 "scale" 参数，表示 TPC-DS 数据集的缩放因子（数据规模）
    // // scale 参数决定了生成数据的大小，1 表示基准大小（1GB），其他值按比例缩放
    // // 如果配置中没有提供 scale 参数，则使用默认值 1D（1.0）
    // // Util.first 方法返回第一个非 null 的值
    double scale = Util.first((Double) map.get("scale"), 1D);
    // // 创建并返回新的 TpcdsSchema 实例，传入 scale 参数用于确定数据集规模
    // // TpcdsSchema 会根据 scale 参数生成对应规模的 TPC-DS 测试数据表
    return new TpcdsSchema(scale);
  }
}
