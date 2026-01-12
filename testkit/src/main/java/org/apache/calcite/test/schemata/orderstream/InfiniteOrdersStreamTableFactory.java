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
package org.apache.calcite.test.schemata.orderstream; // 声明包名，该类属于org.apache.calcite.test.schemata.orderstream包，用于测试流式订单数据

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型，描述表的结构信息
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，表示Calcite中的模式（Schema），可以包含多个表
import org.apache.calcite.schema.Table; // 导入Table接口，表示Calcite中的表，可以是普通表或流表
import org.apache.calcite.schema.TableFactory; // 导入TableFactory接口，用于创建表的工厂接口，实现该接口可以自定义表的创建逻辑

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记参数或返回值可以为null，来自静态检查框架

import java.util.Map; // 导入Map接口，用于存储键值对，这里用于传递创建表时的配置参数

/**
 * Mock table that returns a stream of orders from a fixed array.
 */
@SuppressWarnings("UnusedDeclaration") // 抑制未使用声明的编译警告，因为该类可能通过反射被调用，IDE可能误报未使用
public class InfiniteOrdersStreamTableFactory implements TableFactory<Table> { // 定义无限订单流表工厂类，实现TableFactory接口，用于创建无限流式订单表
  // public constructor, per factory contract // 公共构造函数，符合工厂接口契约要求，Calcite通过反射调用无参构造函数
  public InfiniteOrdersStreamTableFactory() { // 无参构造函数，用于创建工厂实例，不需要任何初始化参数
  } // 构造函数结束，此处为空实现，因为工厂实例不需要维护任何状态

  @Override public Table create(SchemaPlus schema, String name, // 重写create方法，用于创建Table实例，schema是父模式对象，name是表名称
      Map<String, Object> operand, @Nullable RelDataType rowType) { // operand是表配置参数的键值对映射，rowType是可选的行类型定义，可用于指定表的字段类型
    return new InfiniteOrdersTable(); // 返回一个新的InfiniteOrdersTable实例，这是一个无限流式订单表，可以持续产生订单数据
  } // create方法结束，返回创建的流式订单表实例
}
