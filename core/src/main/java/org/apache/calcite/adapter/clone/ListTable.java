/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可协议，查看NOTICE文件获取版权信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的附加信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache 2.0许可证授权给你
 * (the "License"); you may not use this file except in compliance with  // 你可以按照许可证使用此文件，除非符合许可证否则不得使用
 * the License.  You may obtain a copy of the License at  // 你可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // 许可证在线地址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS,  // 本软件按"原样"基础分发
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不含任何明示或暗示的担保或条件
 * See the License for the specific language governing permissions and  // 查看许可证了解特定语言下的权限和
 * limitations under the License.  // 使用限制
 */
package org.apache.calcite.adapter.clone;  // 包声明：属于Calcite的克隆适配器包

import org.apache.calcite.adapter.java.AbstractQueryableTable;  // 导入抽象可查询表基类
import org.apache.calcite.linq4j.AbstractQueryable;  // 导入LINQ抽象可查询接口
import org.apache.calcite.linq4j.Enumerator;  // 导入枚举器接口，用于遍历数据
import org.apache.calcite.linq4j.Linq4j;  // 导入LINQ4j工具类，提供枚举器转换功能
import org.apache.calcite.linq4j.QueryProvider;  // 导入查询提供者接口
import org.apache.calcite.linq4j.Queryable;  // 导入可查询接口
import org.apache.calcite.linq4j.tree.Expression;  // 导入表达式类，用于LINQ表达式树
import org.apache.calcite.rel.type.RelDataType;  // 导入关系数据类型接口
import org.apache.calcite.rel.type.RelDataTypeFactory;  // 导入关系数据类型工厂接口
import org.apache.calcite.rel.type.RelProtoDataType;  // 导入关系原型数据类型接口，延迟创建数据类型
import org.apache.calcite.schema.SchemaPlus;  // 导入SchemaPlus接口，扩展的Schema
import org.apache.calcite.schema.Statistic;  // 导入统计信息接口
import org.apache.calcite.schema.Statistics;  // 导入统计信息工具类

import com.google.common.collect.ImmutableList;  // 导入Google Guava的不可变列表类

import java.lang.reflect.Type;  // 导入Java反射Type接口
import java.util.Iterator;  // 导入Java迭代器接口
import java.util.List;  // 导入Java List接口

/**
 * Implementation of table that reads rows from a read-only list and returns  // 表的实现类，从只读列表中读取行并返回
 * an enumerator of rows. Each row is object (if there is just one column) or  // 行的枚举器。每行是一个对象（如果只有一列）或
 * an object array (if there are multiple columns).  // 对象数组（如果有多个列）
 * 
 * ListTable是Calcite克隆适配器中的一个核心类，它实现了基于内存List的表抽象。
 * 主要作用是：
 * 1. 将Java内存中的List对象包装成Calcite可识别的Table
 * 2. 支持LINQ查询接口，可以被Calcite的查询引擎查询
 * 3. 提供表的元数据信息（行类型、统计信息）
 * 4. 支持单列和多列两种数据模式
 * 
 * 使用场景：
 * - 当需要将内存中的数据集合作为SQL查询的数据源时
 * - 在测试和原型开发中快速创建表
 * - 作为克隆适配器的一部分，用于缓存和复制数据
 * 
 * 数据模型：
 * - 单列表：每行是一个Object对象
 * - 多列表：每行是一个Object[]数组，数组每个元素对应一列
 */
class ListTable extends AbstractQueryableTable {  // ListTable类继承自AbstractQueryableTable，表示一个可查询的表
  private final RelProtoDataType protoRowType;  // 原型行类型：延迟创建的行数据类型，用于描述表的结构（列名、列类型等）
  private final Expression expression;  // LINQ表达式：表示这个表的查询表达式，用于LINQ查询表达式树构建
  private final List list;  // 数据列表：存储实际数据的只读List，每行数据存储在这个List中

  /** Creates a ListTable. */  // 创建ListTable的构造方法注释
  ListTable(  // ListTable构造方法，初始化一个基于List的表
      Type elementType,  // 元素类型：表中每行数据的Java类型（单列时是Object，多列时是Object[]）
      RelProtoDataType protoRowType,  // 原型行类型：表的行结构描述，包含列名和列类型信息
      Expression expression,  // 表达式：表示这个表的LINQ表达式，用于查询优化和执行
      List list) {  // 数据列表：包含实际表数据的List集合
    super(elementType);  // 调用父类AbstractQueryableTable的构造方法，设置元素类型
    this.protoRowType = protoRowType;  // 保存原型行类型到成员变量
    this.expression = expression;  // 保存表达式到成员变量
    this.list = list;  // 保存数据列表到成员变量
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {  // 重写获取行类型方法
    return protoRowType.apply(typeFactory);  // 使用类型工厂将原型行类型转换为实际的RelDataType对象
  }  // 返回表的行类型，包含所有列的名称和数据类型信息

  @Override public Statistic getStatistic() {  // 重写获取统计信息方法
    return Statistics.of(list.size(), ImmutableList.of());  // 创建并返回统计信息对象：包含行数（list.size()）和空的可排序键集合
  }  // 返回表的统计信息，包括行数、排序键等，用于查询优化器进行成本估算

  @Override public <T> Queryable<T> asQueryable(final QueryProvider queryProvider,  // 重写转换为可查询对象方法
      SchemaPlus schema, String tableName) {  // 参数：查询提供者、Schema对象、表名
    return new AbstractQueryable<T>() {  // 返回一个匿名内部类，继承AbstractQueryable，实现可查询接口
      @Override public Type getElementType() {  // 重写获取元素类型方法
        return elementType;  // 返回表中每行数据的Java类型
      }  // 这个类型用于类型安全的LINQ查询

      @Override public Expression getExpression() {  // 重写获取表达式方法
        return expression;  // 返回表示这个表的LINQ表达式
      }  // 表达式用于构建查询树和优化查询

      @Override public QueryProvider getProvider() {  // 重写获取查询提供者方法
        return queryProvider;  // 返回查询提供者对象
      }  // 查询提供者负责执行查询和返回结果

      @Override public Iterator<T> iterator() {  // 重写获取迭代器方法
        //noinspection unchecked  // 抑制未检查类型转换警告
        return list.iterator();  // 返回底层List的迭代器，用于遍历表数据
      }  // 提供Java标准迭代器接口，支持foreach循环

      @Override public Enumerator<T> enumerator() {  // 重写获取枚举器方法
        //noinspection unchecked  // 抑制未检查类型转换警告
        return Linq4j.enumerator(list);  // 使用Linq4j工具类将List转换为枚举器
      }  // 返回LINQ枚举器，用于LINQ查询引擎遍历数据
    };  // 匿名内部类结束
  }  // 方法结束，返回可查询对象供Calcite查询引擎使用
}  // ListTable类结束
