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
// Apache许可证声明，说明代码的使用权限和限制
package org.apache.calcite.server; // 声明包名，该类属于org.apache.calcite.server包

import org.apache.calcite.materialize.MaterializationKey; // 导入物化视图键类，用于唯一标识物化视图
import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系型数据类型原型接口，用于描述行数据类型
import org.apache.calcite.schema.Schema; // 导入Schema接口，定义数据库模式的基本结构
import org.apache.calcite.sql2rel.NullInitializerExpressionFactory; // 导入空初始化表达式工厂，用于创建空值初始化器

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的字段或方法返回值

/** A table that implements a materialized view. */ // 类注释：实现物化视图的表
// MaterializedViewTable类：物化视图表的实现类，继承自MutableArrayTable
// 物化视图是预先计算并存储的查询结果，可以提高查询性能
// 该类提供了物化视图的基本功能，包括存储物化视图的键信息和类型转换
class MaterializedViewTable // 定义类名为MaterializedViewTable
    extends MutableArrayTable { // 继承MutableArrayTable类，获得可变数组表的基础功能
  /** The key with which this was stored in the materialization service,
   * or null if not (yet) materialized. */ // 字段注释：物化视图键，用于在物化服务中标识该视图
  // key成员变量：MaterializationKey类型的字段，用于唯一标识物化视图
  // 当物化视图被存储到物化服务中时，会被分配一个键；如果尚未物化，则为null
  // 这个键对于物化视图的管理、查找和更新非常重要
  @Nullable MaterializationKey key; // 声明key字段，类型为MaterializationKey，使用@Nullable注解标记可能为null

  // 构造方法：创建物化视图表实例
  MaterializedViewTable(String name, RelProtoDataType protoRowType) { // 接收表名和行数据类型原型作为参数
    super(name, protoRowType, protoRowType, // 调用父类MutableArrayTable的构造方法，传入表名和行类型
        NullInitializerExpressionFactory.INSTANCE); // 传入空初始化表达式工厂实例，用于初始化新行的默认值
  } // 构造方法结束

  // 重写getJdbcTableType方法：返回JDBC表类型
  @Override public Schema.TableType getJdbcTableType() { // 使用@Override注解标记重写父类方法，返回Schema.TableType类型
    return Schema.TableType.MATERIALIZED_VIEW; // 返回物化视图表类型，标识该表为物化视图而非普通表
  } // 方法结束

  // 重写unwrap方法：尝试将当前对象转换为指定类型
  @Override public <C extends Object> @Nullable C unwrap(Class<C> aClass) { // 泛型方法，尝试将对象转换为指定类型C，使用@Override注解标记重写
    if (MaterializationKey.class.isAssignableFrom(aClass) // 检查请求的类型是否是MaterializationKey类或其子类
        && aClass.isInstance(key)) { // 进一步检查key字段是否是请求的类型的实例
      return aClass.cast(key); // 如果条件满足，将key转换为请求的类型并返回
    } // if语句结束
    return super.unwrap(aClass); // 如果不满足条件，调用父类的unwrap方法继续处理
  } // 方法结束
} // 类定义结束
