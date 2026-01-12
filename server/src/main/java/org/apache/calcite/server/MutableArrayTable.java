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
 */ // Apache许可证声明，允许在符合Apache 2.0许可协议的条件下使用、修改和分发此代码
package org.apache.calcite.server; // 声明包名，表示此类属于org.apache.calcite.server包

import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历查询结果
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供LINQ风格的集合操作功能
import org.apache.calcite.linq4j.QueryProvider; // 导入查询提供者接口，用于创建可查询对象
import org.apache.calcite.linq4j.Queryable; // 导入可查询接口，表示可以执行LINQ查询的数据源
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，用于表示代码表达式树
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示表或表达式的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系原型数据类型接口，可以延迟创建数据类型
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，表示Calcite中的数据模式
import org.apache.calcite.schema.Schemas; // 导入Schemas工具类，提供模式相关的工具方法
import org.apache.calcite.schema.Wrapper; // 导入Wrapper接口，用于将对象包装为其他类型
import org.apache.calcite.schema.impl.AbstractTableQueryable; // 导入抽象表可查询类，提供表查询的基础实现
import org.apache.calcite.sql2rel.InitializerExpressionFactory; // 导入初始化表达式工厂接口，用于创建列的初始化表达式

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的返回值

import java.lang.reflect.Type; // 导入Type类，表示Java类型
import java.util.ArrayList; // 导入ArrayList类，提供动态数组实现
import java.util.Collection; // 导入Collection接口，表示集合
import java.util.List; // 导入List接口，表示有序集合

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空检查

/** Table backed by a Java list. */ // 类注释：MutableArrayTable是一个基于Java List（列表）的可变表实现，它使用内存中的ArrayList来存储表数据
class MutableArrayTable extends AbstractModifiableTable // 类定义：MutableArrayTable继承自AbstractModifiableTable抽象类，表示这是一个可修改的表
    implements Wrapper { // 实现Wrapper接口，支持将此表对象包装为其他类型
  final List rows = new ArrayList(); // 成员变量：rows是一个final的List，用于存储表的所有行数据，使用ArrayList实现，每行数据是一个Object数组
  private final RelProtoDataType protoRowType; // 成员变量：protoRowType是关系原型数据类型，用于延迟创建表的行类型（包括所有列的类型信息）
  private final InitializerExpressionFactory initializerExpressionFactory; // 成员变量：initializerExpressionFactory是初始化表达式工厂，用于在插入数据时计算列的值（特别是虚拟列）

  /** Creates a MutableArrayTable. // 方法注释：构造函数，创建一个MutableArrayTable实例
   *
   * @param name Name of table within its schema // 参数：name表示表在其模式中的名称
   * @param protoStoredRowType Prototype of row type of stored columns (all // 参数：protoStoredRowType是存储列的行类型原型（不包括虚拟列）
   *     columns except virtual columns)
   * @param protoRowType Prototype of row type (all columns) // 参数：protoRowType是完整行类型的原型（包括存储列和虚拟列）
   * @param initializerExpressionFactory How columns are populated // 参数：initializerExpressionFactory是初始化表达式工厂，用于计算列值
   */
  MutableArrayTable(String name, RelProtoDataType protoStoredRowType, // 构造函数定义：接收表名、存储列类型、完整行类型和初始化表达式工厂
      RelProtoDataType protoRowType, // 参数：完整行类型原型
      InitializerExpressionFactory initializerExpressionFactory) { // 参数：初始化表达式工厂
    super(name); // 调用父类AbstractModifiableTable的构造函数，传入表名
    requireNonNull(protoStoredRowType, "protoStoredRowType"); // 检查protoStoredRowType参数不为null，否则抛出NullPointerException
    this.protoRowType = requireNonNull(protoRowType, "protoRowType"); // 检查protoRowType参数不为null并赋值给成员变量，否则抛出NullPointerException
    this.initializerExpressionFactory = // 将initializerExpressionFactory赋值给成员变量
        requireNonNull(initializerExpressionFactory, // 检查initializerExpressionFactory参数不为null，否则抛出NullPointerException
            "initializerExpressionFactory"); // 错误信息：当参数为null时显示的错误提示
  } // 构造函数结束

  @Override public Collection getModifiableCollection() { // 方法重写：获取可修改的集合，返回存储表数据的rows列表
    return rows; // 返回rows列表，允许外部代码直接修改表数据
  } // 方法结束

  @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider, // 方法重写：将表转换为可查询对象，支持LINQ风格的查询操作
      SchemaPlus schema, String tableName) { // 参数：schema是表所属的模式，tableName是表名
    return new AbstractTableQueryable<T>(queryProvider, schema, this, // 创建并返回一个匿名内部类实例，继承自AbstractTableQueryable
        tableName) { // 传入查询提供者、模式、表对象本身和表名
      @Override public Enumerator<T> enumerator() { // 重写enumerator方法，返回一个枚举器用于遍历表数据
        //noinspection unchecked // 忽略未检查的类型转换警告
        return (Enumerator<T>) Linq4j.enumerator(rows); // 将rows列表转换为枚举器，强制转换为泛型类型T的枚举器
      } // 内部方法结束
    }; // 匿名内部类实例创建结束
  } // 方法结束

  @Override public Type getElementType() { // 方法重写：获取表的元素类型，即每行数据的Java类型
    return Object[].class; // 返回Object[].class，表示每行数据是一个对象数组
  } // 方法结束

  @Override public Expression getExpression(SchemaPlus schema, String tableName, // 方法重写：获取表的表达式，用于代码生成和查询优化
      Class clazz) { // 参数：clazz是目标类类型
    return Schemas.tableExpression(schema, getElementType(), // 调用Schemas工具类创建表表达式，传入模式、元素类型、表名和目标类
        tableName, clazz); // 返回表的表达式对象
  } // 方法结束

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 方法重写：获取表的行类型，即表的结构定义（列名和列类型）
    return protoRowType.apply(typeFactory); // 使用原型数据类型创建实际的行类型，传入类型工厂
  } // 方法结束

  @Override public <C extends Object> @Nullable C unwrap(Class<C> aClass) { // 方法重写：将此表对象包装为指定类型，用于类型转换和适配
    if (aClass.isInstance(initializerExpressionFactory)) { // 检查请求的类型是否是initializerExpressionFactory的实例
      return aClass.cast(initializerExpressionFactory); // 如果是，将initializerExpressionFactory转换为请求的类型并返回
    } // 条件判断结束
    return super.unwrap(aClass); // 否则，调用父类的unwrap方法继续尝试转换
  } // 方法结束
} // 类定义结束
