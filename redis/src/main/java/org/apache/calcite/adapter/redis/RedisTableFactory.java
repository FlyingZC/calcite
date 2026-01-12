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
package org.apache.calcite.adapter.redis; // 声明包名，该类位于 org.apache.calcite.adapter.redis 包下，是 Calcite Redis 适配器的一部分

import org.apache.calcite.rel.type.RelDataType; // 导入 Calcite 关系数据类型接口，表示关系表达式的类型系统
import org.apache.calcite.rel.type.RelDataTypeImpl; // 导入关系数据类型实现类，提供类型系统的具体实现
import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系数据类型原型接口，用于延迟解析和序列化数据类型
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 接口，表示 Calcite 模式（Schema）的增强版本，支持动态添加表和函数
import org.apache.calcite.schema.Table; // 导入 Table 接口，表示 Calcite 中的表抽象
import org.apache.calcite.schema.TableFactory; // 导入 TableFactory 接口，用于创建表实例的工厂接口

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 CheckerFramework 的可空注解，用于标记可能为 null 的参数

import java.util.Map; // 导入 Java Map 接口，用于存储键值对集合

/**
 * Implementation of {@link TableFactory} for Redis. // 类文档注释：这是 Redis 适配器的 TableFactory 接口实现类，负责创建 Redis 表实例
 *
 * <p>A table corresponds to what Redis calls a "data source". // 表对应于 Redis 中称为"数据源"的概念，即 Redis 中的数据存储结构
 */ // 文档注释结束
public class RedisTableFactory implements TableFactory { // 声明 RedisTableFactory 类，实现 TableFactory 接口，作为 Redis 表的工厂类
  @SuppressWarnings("unused") // 抑制编译器警告，表示下面的变量可能未直接使用，但通过反射等方式被使用
  public static final RedisTableFactory INSTANCE = new RedisTableFactory(); // 声明并初始化 RedisTableFactory 的单例实例，使用静态常量.INSTANCE 提供全局唯一的工厂实例，便于外部通过反射或配置文件调用

  private RedisTableFactory() { // 私有构造方法，防止外部直接实例化，确保只能通过单例 INSTANCE 访问
  } // 构造方法结束，空实现

  // name that is also the same name as a complex metric // 注释：表名称也可以与复杂指标同名，说明表名在 Redis 中可能对应某种指标或数据源
  @Override public Table create(SchemaPlus schema, String tableName, Map operand, // 重写 TableFactory 接口的 create 方法，用于创建 Redis 表实例；参数：schema-父 Schema 对象，tableName-表名称，operand-操作参数映射表
      @Nullable RelDataType rowType) { // 参数：rowType-行类型（可为空），表示表的行数据类型结构
    final RedisSchema redisSchema = schema.unwrap(RedisSchema.class); // 从 SchemaPlus 对象中解包获取 RedisSchema 实例，unwrap 方法用于获取底层的 RedisSchema 对象
    final RelProtoDataType protoRowType = // 声明关系数据类型原型变量，用于延迟解析行类型
        rowType != null ? RelDataTypeImpl.proto(rowType) : null; // 三元运算：如果 rowType 不为空，则通过 RelDataTypeImpl.proto() 方法创建原型；否则为 null，实现可选的行类型处理
    return RedisTable.create(redisSchema, tableName, operand, protoRowType); // 调用 RedisTable.create() 静态方法创建并返回 RedisTable 实例，传入 RedisSchema、表名、操作参数和行类型原型
  } // create 方法结束，返回创建的 Table 对象
} // RedisTableFactory 类结束
