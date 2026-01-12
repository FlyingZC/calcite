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
package org.apache.calcite.adapter.file;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelProtoDataType;
import org.apache.calcite.schema.QueryableTable;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Schemas;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.util.ImmutableIntList;
import org.apache.calcite.util.Source;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Table based on a CSV file.
 *
 * <p>Copied from {@code CsvTranslatableTable} in demo CSV adapter,
 * with more advanced features.
 */
// CsvTranslatableTable类：基于CSV文件的可翻译表实现，继承自CsvTable基类，同时实现了QueryableTable和TranslatableTable接口
// 这个类是Calcite适配器框架中用于将CSV文件作为关系表进行查询的核心类之一
// QueryableTable接口：表示该表可以被转换为LINQ风格的Queryable对象，支持使用LINQ查询语法进行查询
// TranslatableTable接口：表示该表可以被转换为Calcite的关系表达式(RelNode)，这是Calcite优化器进行查询优化的基础
// 通过实现这两个接口，CsvTranslatableTable既支持LINQ风格的查询，也支持Calcite的SQL查询优化
public class CsvTranslatableTable extends CsvTable
    implements QueryableTable, TranslatableTable {
  /** Creates a CsvTable. */
  // 构造方法：创建一个CsvTranslatableTable实例
  // 参数source：Source对象，表示CSV文件的源，可以是文件系统中的文件或其他数据源
  // 参数protoRowType：RelProtoDataType对象，表示行的数据类型原型，用于描述表中每一列的数据类型
  // 这个构造方法通过调用父类CsvTable的构造方法来初始化基础属性
  // RelProtoDataType是一个可序列化的数据类型描述，可以在运行时转换为具体的RelDataType
  CsvTranslatableTable(Source source, @Nullable RelProtoDataType protoRowType) {
    super(source, protoRowType);
  }

  // toString方法：返回对象的字符串表示形式，用于调试和日志记录
// 返回值：固定的字符串"CsvTranslatableTable"，标识这个对象的类型
// 这个方法在调试时很有用，可以快速识别当前对象的类型
  @Override public String toString() {
    return "CsvTranslatableTable";
  }

  /** Returns an enumerable over a given projection of the fields. */
  // project方法：返回一个给定字段投影的可枚举对象(Enumerable)，用于支持LINQ风格的查询
  // 这个方法是QueryableTable接口的核心方法之一，允许对表进行字段投影操作
  // 参数root：DataContext对象，表示查询执行上下文，包含类型工厂、取消标志等信息
  // 参数fields：int数组，表示需要投影的字段索引列表，例如[0,2]表示只取第0和第2列
  // 返回值：Enumerable<Object>对象，表示可枚举的数据集合，每个元素是一个Object数组
  // @SuppressWarnings("unused")：抑制未使用警告，因为这个方法通常由生成的代码调用
  // DataContext.Variable.CANCEL_FLAG：从上下文中获取取消标志，用于支持查询取消功能
  // AtomicBoolean：原子布尔类型，用于线程安全地检查查询是否被取消
  // AbstractEnumerable：抽象可枚举类，LINQ4J框架的基础类，需要实现enumerator方法
  // enumerator方法：创建并返回一个枚举器(Enumerator)，用于遍历数据
  // JavaTypeFactory：Java类型工厂，用于创建和管理Java类型
  // CsvEnumerator：CSV枚举器，负责从CSV文件中读取数据并将其转换为对象数组
  // getFieldTypes：获取字段类型列表，用于在枚举时进行类型转换
  // ImmutableIntList：不可变整数列表，用于存储字段索引
  @SuppressWarnings("unused") // called from generated code
  public Enumerable<Object> project(final DataContext root,
      final int[] fields) {
    // 从数据上下文中获取取消标志，这是一个原子布尔值，用于支持查询的取消操作
    final AtomicBoolean cancelFlag = DataContext.Variable.CANCEL_FLAG.get(root);
    // 创建并返回一个匿名内部类，继承自AbstractEnumerable，实现enumerator方法
    return new AbstractEnumerable<Object>() {
      // enumerator方法：创建并返回一个枚举器，用于遍历CSV文件中的数据
      @Override public Enumerator<Object> enumerator() {
        // 从数据上下文中获取Java类型工厂，用于处理类型转换
        JavaTypeFactory typeFactory = root.getTypeFactory();
        // 创建并返回CsvEnumerator实例，负责实际的数据读取和枚举
        // 参数source：CSV文件源
        // 参数cancelFlag：取消标志，用于支持查询取消
        // 参数getFieldTypes(typeFactory)：获取字段类型列表
        // 参数ImmutableIntList.of(fields)：将字段数组转换为不可变列表
        return new CsvEnumerator<>(source, cancelFlag,
            getFieldTypes(typeFactory), ImmutableIntList.of(fields));
      }
    };
  }

  // getExpression方法：获取表的表达式，用于生成LINQ查询表达式
  // 这个方法是QueryableTable接口的方法，用于将表转换为LINQ表达式树
  // 参数schema：SchemaPlus对象，表示表所在的schema，可能包含额外的元数据
  // 参数tableName：String对象，表示表的名称
  // 参数clazz：Class对象，表示查询结果的类型
  // 返回值：Expression对象，表示表的LINQ表达式，可以用于构建更复杂的查询表达式
  // Schemas.tableExpression：工具方法，用于创建表的表达式
  // getElementType：获取元素的类型，即表中每一行的类型
  @Override public Expression getExpression(SchemaPlus schema, String tableName,
      Class clazz) {
    // 使用Schemas工具类的tableExpression方法创建表表达式
    // 这个表达式将表封装为一个可以在LINQ查询中使用的表达式
    return Schemas.tableExpression(schema, getElementType(), tableName, clazz);
  }

  // getElementType方法：获取表中元素的类型，即每一行数据的类型
  // 这个方法是QueryableTable接口的方法，用于确定查询结果的类型
  // 返回值：Object[].class，表示每一行是一个Object数组，数组中的每个元素对应一列的值
  // 使用Object数组是因为CSV文件的列类型可能各不相同，需要用Object来统一表示
  @Override public Type getElementType() {
    return Object[].class;
  }

  // asQueryable方法：将表转换为Queryable对象，用于支持LINQ查询
  // 这个方法是QueryableTable接口的方法，用于创建一个可以在内存中查询的Queryable对象
  // 参数queryProvider：QueryProvider对象，表示查询提供者，用于执行LINQ查询
  // 参数schema：SchemaPlus对象，表示表所在的schema
  // 参数tableName：String对象，表示表的名称
  // 返回值：Queryable<T>对象，表示可查询的数据集合
  // 注意：这个方法抛出UnsupportedOperationException，表示不支持直接转换为Queryable
  // 这是因为CsvTranslatableTable主要通过toRel方法转换为关系表达式，而不是直接转换为Queryable
  // 在Calcite的CSV适配器中，查询是通过转换为RelNode然后执行的，而不是通过LINQ的Queryable
  @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider,
      SchemaPlus schema, String tableName) {
    throw new UnsupportedOperationException();
  }

  // toRel方法：将表转换为关系表达式(RelNode)，这是Calcite查询优化的核心方法
  // 这个方法是TranslatableTable接口的方法，用于将表转换为Calcite的关系代数表达式
  // 参数context：RelOptTable.ToRelContext对象，表示转换上下文，包含集群(Cluster)等优化信息
  // 参数relOptTable：RelOptTable对象，表示优化器中的表对象，包含表的元数据信息
  // 返回值：RelNode对象，表示表的关系表达式，通常是一个TableScan节点
  // 这个方法是Calcite查询优化器的入口点之一，优化器通过这个方法获取表的关系表达式
  // 关系表达式是Calcite进行查询优化的基础，包括谓词下推、投影消除等优化
  @Override public RelNode toRel(
      RelOptTable.ToRelContext context,
      RelOptTable relOptTable) {
    // Request all fields.
    // 获取表的字段数量，即列数
    // getRowType返回表的行类型，包含所有字段的信息
    final int fieldCount = relOptTable.getRowType().getFieldCount();
    // 创建一个字段索引列表，从0到fieldCount-1
    // identityList方法创建一个恒等列表，即[0, 1, 2, ..., fieldCount-1]
    // 这表示请求所有字段，不进行字段投影
    final int[] fields = CsvEnumerator.identityList(fieldCount);
    // 创建并返回CsvTableScan节点
    // CsvTableScan是一个关系节点，表示对CSV表的扫描操作
    // 参数context.getCluster()：获取集群对象，包含优化器的上下文信息
    // 参数relOptTable：优化器中的表对象
    // 参数this：当前CsvTranslatableTable对象
    // 参数fields：字段索引列表，指定需要扫描的字段
    return new CsvTableScan(context.getCluster(), relOptTable, this, fields);
  }
}
