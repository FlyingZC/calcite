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
package org.apache.calcite.test.schemata.orderstream; // 定义包路径，该类位于org.apache.calcite.test.schemata.orderstream包下，用于测试订单流相关的schema

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型，描述表的结构信息

import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，表示Calcite中的Schema对象，可以包含多个表

import org.apache.calcite.schema.Table; // 导入Table接口，表示Calcite中的表抽象

import org.apache.calcite.schema.TableFactory; // 导入TableFactory接口，用于创建表实例的工厂接口

import com.google.common.collect.ImmutableList; // 导入Google Guava库的ImmutableList类，用于创建不可变的列表集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记参数可能为null的情况

import java.util.Map; // 导入Map接口，用于存储键值对数据

/**
 * Mocks a simple relation to use for stream joining test.
 * 模拟一个简单的关系表，用于流连接测试。这个工厂类用于创建产品表，主要用于测试流处理场景下的连接操作。
 */
public class ProductsTableFactory implements TableFactory<Table> { // 定义ProductsTableFactory类，实现TableFactory<Table>接口，用于创建产品表实例
  @Override public Table create(SchemaPlus schema, String name, // 重写create方法，创建Table实例，参数schema表示所在的Schema，name表示表名
      Map<String, Object> operand, @Nullable RelDataType rowType) { // 参数operand表示创建表的配置参数，rowType表示表的行类型（可为null）
    final Object[][] rows = { // 定义二维数组rows，用于存储产品表的行数据，每行包含产品名称和库存状态
        {"paint", 1}, // 第一行数据：产品名称为"paint"（油漆），库存状态为1（有库存）
        {"paper", 0}, // 第二行数据：产品名称为"paper"（纸张），库存状态为0（无库存）
        {"brush", 1} // 第三行数据：产品名称为"brush"（画笔），库存状态为1（有库存）
    };
    return new ProductsTable(ImmutableList.copyOf(rows)); // 将二维数组转换为不可变的列表，并创建ProductsTable实例返回
  }
}
