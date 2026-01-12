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
// Apache许可证头，声明代码版权和使用许可
package org.apache.calcite.adapter.druid; // 声明包名，该类属于Calcite的Druid适配器包

import org.apache.calcite.schema.Schema; // 导入Schema接口，Calcite中Schema代表数据模式（类似数据库）
import org.apache.calcite.schema.SchemaFactory; // 导入SchemaFactory接口，用于创建Schema实例的工厂接口
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，是Schema的扩展接口，支持添加子Schema

import java.util.List; // 导入List接口，用于处理列表类型的数据
import java.util.Map; // 导入Map接口，用于处理键值对类型的数据

/**
 * Schema factory that creates Druid schemas.
 * Schema工厂，用于创建Druid数据模式的实例
 *
 * <table>
 *   <caption>Druid schema operands</caption>
 *   <tr>
 *     <th>Operand</th>
 *     <th>Description</th>
 *     <th>Required</th>
 *   </tr>
 *   <tr>
 *     <td>url</td>
 *     <td>URL of Druid's query node.
 *     Druid查询节点的URL地址，默认值为"http://localhost:8082"
 *     The default is "http://localhost:8082".</td>
 *     <td>No</td>
 *   </tr>
 *   <tr>
 *     <td>coordinatorUrl</td>
 *     <td>URL of Druid's coordinator node.
 *     Druid协调器节点的URL地址，默认值基于url参数，将端口号8082替换为8081，例如"http://localhost:8081"
 *     The default is <code>url</code>, replacing "8082" with "8081",
 *     for example "http://localhost:8081".</td>
 *     <td>No</td>
 *   </tr>
 * </table>
 */
public class DruidSchemaFactory implements SchemaFactory { // DruidSchemaFactory类，实现SchemaFactory接口，用于创建连接到Druid数据源的Schema实例
  /** Default Druid URL. */
  // 默认的Druid查询节点URL地址，当用户没有提供url参数时使用此默认值
  public static final String DEFAULT_URL = "http://localhost:8082"; // 定义静态常量，存储默认的Druid Broker节点URL，端口8082是Druid Broker的默认端口

  @Override public Schema create(SchemaPlus parentSchema, String name,
      Map<String, Object> operand) { // 实现SchemaFactory接口的create方法，用于创建DruidSchema实例；parentSchema是父Schema，name是当前Schema的名称，operand是配置参数的键值对映射
    final String url = operand.get("url") instanceof String // 从operand参数中获取"url"键的值，并检查是否为String类型
        ? (String) operand.get("url") // 如果url参数存在且是字符串类型，则使用该值作为Druid查询节点的URL
        : DEFAULT_URL; // 如果url参数不存在或不是字符串类型，则使用默认值DEFAULT_URL（即http://localhost:8082）
    final String coordinatorUrl = operand.get("coordinatorUrl") instanceof String // 从operand参数中获取"coordinatorUrl"键的值，并检查是否为String类型
        ? (String) operand.get("coordinatorUrl") // 如果coordinatorUrl参数存在且是字符串类型，则使用该值作为Druid协调器节点的URL
        : url.replace(":8082", ":8081"); // 如果coordinatorUrl参数不存在或不是字符串类型，则基于url参数的值，将端口号8082替换为8081来生成协调器URL（Druid协调器默认端口是8081）
    // "tables" is a hidden attribute, copied in from the enclosing custom
    // schema
    // "tables"是一个隐藏属性，从外层的自定义schema中复制而来，用于判断是否已经定义了表结构
    final boolean containsTables = // 定义布尔变量，用于判断operand中是否包含非空的tables列表
        operand.get("tables") instanceof List // 检查operand中的"tables"键的值是否为List类型
            && !((List) operand.get("tables")).isEmpty(); // 并且检查该列表是否不为空，如果两者都为真，则containsTables为true
    return new DruidSchema(url, coordinatorUrl, !containsTables); // 创建并返回DruidSchema实例，传入查询节点URL、协调器URL和discoverTables参数；如果containsTables为true（已定义表），则discoverTables为false（不自动发现表），反之则自动发现表
  } // create方法结束
} // DruidSchemaFactory类定义结束
