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
package org.apache.calcite.adapter.redis; // 声明包名，该类位于org.apache.calcite.adapter.redis包下，是Calcite Redis适配器的一部分

import org.apache.calcite.schema.Schema; // 导入Calcite的Schema接口，表示数据模式（包含表、视图等数据结构）
import org.apache.calcite.schema.SchemaFactory; // 导入SchemaFactory接口，用于创建Schema实例的工厂接口
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，它是Schema的扩展，允许添加子Schema

import java.util.List; // 导入List接口，用于处理列表集合
import java.util.Map; // 导入Map接口，用于处理键值对映射

import static com.google.common.base.Preconditions.checkArgument; // 导入Guava库的checkArgument静态方法，用于参数校验

import static java.lang.Integer.parseInt; // 导入Integer类的parseInt静态方法，用于将字符串转换为整数

/**
 * Factory that creates a {@link RedisSchema}.
 * RedisSchema工厂类，负责创建RedisSchema实例
 *
 * <p>Allows a custom schema to be included in a redis-test-model.json file.
 * 允许在redis-test-model.json配置文件中包含自定义的schema
 * See <a href="http://calcite.apache.org/docs/file_adapter.html">File adapter</a>.
 * 参考Calcite文档中的文件适配器章节
 */
@SuppressWarnings("UnusedDeclaration") // 抑制未使用声明的警告，因为该类可能通过反射调用
public class RedisSchemaFactory implements SchemaFactory { // RedisSchemaFactory类实现了SchemaFactory接口，用于创建Redis数据源的Schema
  // public constructor, per factory contract // 公共构造函数，符合工厂接口的契约要求
  public RedisSchemaFactory() { // 无参构造函数，用于创建RedisSchemaFactory实例
  } // 构造函数体为空，不需要任何初始化操作

  @Override public Schema create(SchemaPlus schema, String name, // 重写SchemaFactory接口的create方法，用于创建RedisSchema实例，参数schema是父Schema，name是Schema名称
      Map<String, Object> operand) { // operand参数包含创建Schema所需的所有配置信息，以键值对形式提供
    checkArgument(operand.get("tables") != null, // 校验operand中是否包含"tables"配置项，如果为null则抛出IllegalArgumentException
        "tables must be specified"); // 错误提示信息：必须指定tables配置项
    checkArgument(operand.get("host") != null, // 校验operand中是否包含"host"配置项，Redis服务器的主机地址
        "host must be specified"); // 错误提示信息：必须指定host配置项
    checkArgument(operand.get("port") != null, // 校验operand中是否包含"port"配置项，Redis服务器的端口号
        "port must be specified"); // 错误提示信息：必须指定port配置项
    checkArgument(operand.get("database") != null, // 校验operand中是否包含"database"配置项，Redis数据库索引
        "database must be specified"); // 错误提示信息：必须指定database配置项

    @SuppressWarnings("unchecked") List<Map<String, Object>> tables = // 抑制未检查的类型转换警告，从operand中获取tables配置并转换为List类型
        (List) operand.get("tables"); // 将tables配置强制转换为List，每个元素是一个Map，表示一个表的配置信息
    String host = operand.get("host").toString(); // 获取Redis服务器的主机地址，并转换为字符串类型
    int port = (int) operand.get("port"); // 获取Redis服务器的端口号，并转换为int类型
    int database = parseInt(operand.get("database").toString()); // 获取Redis数据库索引，先转为字符串再解析为整数
    String password = operand.get("password") == null ? null // 获取Redis连接密码，如果未指定则为null（表示无密码连接）
        : operand.get("password").toString(); // 如果指定了password，则转换为字符串类型
    return new RedisSchema(host, port, database, password, tables); // 创建并返回RedisSchema实例，传入连接参数和表配置列表
  } // create方法结束，返回创建的RedisSchema对象
} // RedisSchemaFactory类定义结束
