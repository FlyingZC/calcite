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
package org.apache.calcite.adapter.csv; // 包声明，CSV适配器包，包含CSV文件相关的表实现

import org.apache.calcite.DataContext; // 导入DataContext接口，提供查询执行上下文信息，如类型工厂、取消标志等
import org.apache.calcite.adapter.file.CsvEnumerator; // 导入CsvEnumerator类，用于枚举CSV文件中的数据行
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory接口，用于创建Java类型系统
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入AbstractEnumerable抽象类，LINQ4J可枚举集合的抽象实现
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的数据集合，支持LINQ查询操作
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，用于遍历数据集合的枚举器
import org.apache.calcite.linq4j.QueryProvider; // 导入QueryProvider接口，用于创建可查询对象
import org.apache.calcite.linq4j.Queryable; // 导入Queryable接口，表示可查询的数据源
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类，表示LINQ表达式树中的表达式节点
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable接口，表示优化器中的表元数据
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式树中的节点
import org.apache.calcite.rel.type.RelProtoDataType; // 导入RelProtoDataType接口，表示关系数据类型的原型，可以延迟创建实际类型
import org.apache.calcite.schema.QueryableTable; // 导入QueryableTable接口，表示可以作为可查询对象的表
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，表示模式（Schema）的扩展版本，支持添加表和函数
import org.apache.calcite.schema.Schemas; // 导入Schemas工具类，提供模式相关的辅助方法
import org.apache.calcite.schema.TranslatableTable; // 导入TranslatableTable接口，表示可以被转换为关系代数节点的表
import org.apache.calcite.util.ImmutableIntList; // 导入ImmutableIntList类，不可变的整数列表，用于存储字段索引
import org.apache.calcite.util.Source; // 导入Source接口，表示数据源，如文件或URL

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值

import java.lang.reflect.Type; // 导入Type接口，表示Java类型
import java.util.concurrent.atomic.AtomicBoolean; // 导入AtomicBoolean类，原子布尔值，用于线程安全的取消标志

/**
 * Table based on a CSV file. // 基于CSV文件的表实现，支持将CSV文件作为Calcite查询引擎中的数据源
 * 这个类继承自CsvTable基类，同时实现了QueryableTable和TranslatableTable接口
 * QueryableTable接口允许表被转换为可查询对象，支持LINQ风格的查询
 * TranslatableTable接口允许表被转换为关系代数RelNode节点，用于查询优化和执行
 * 这是Calcite适配器模式的关键实现，使得CSV文件可以像数据库表一样被查询
 */
public class CsvTranslatableTable extends CsvTable // 定义CsvTranslatableTable类，继承CsvTable基类
    implements QueryableTable, TranslatableTable { // 实现QueryableTable和TranslatableTable接口，使CSV表支持查询和关系代数转换
  /** Creates a CsvTable. */ // 构造方法说明：创建一个CsvTable实例
  CsvTranslatableTable(Source source, @Nullable RelProtoDataType protoRowType) { // 构造方法，接收CSV文件源和行类型原型
    super(source, protoRowType); // 调用父类CsvTable的构造方法，初始化源和行类型
  } // 构造方法结束

  @Override public String toString() { // 重写toString方法，返回对象的字符串表示
    return "CsvTranslatableTable"; // 返回类名作为字符串表示
  } // toString方法结束

  /** Returns an enumerable over a given projection of the fields. */ // 方法说明：返回对给定字段投影的可枚举集合
  @SuppressWarnings("unused") // called from generated code // 抑制未使用警告，此方法由生成的代码调用
  public Enumerable<Object> project(final DataContext root, // 方法定义，返回Object类型的可枚举集合，接收DataContext上下文和字段索引数组
      final int[] fields) { // 参数：fields表示要投影的字段索引数组
    final AtomicBoolean cancelFlag = DataContext.Variable.CANCEL_FLAG.get(root); // 从上下文中获取取消标志，用于支持查询取消操作
    return new AbstractEnumerable<Object>() { // 返回一个匿名内部类，继承AbstractEnumerable，实现可枚举集合
      @Override public Enumerator<Object> enumerator() { // 重写enumerator方法，创建数据枚举器
        JavaTypeFactory typeFactory = root.getTypeFactory(); // 从上下文中获取Java类型工厂，用于处理类型转换
        return new CsvEnumerator<>( // 创建并返回CsvEnumerator实例，用于枚举CSV数据
            source, // CSV文件源
            cancelFlag, // 取消标志
            getFieldTypes(typeFactory), // 获取字段类型列表
            ImmutableIntList.of(fields)); // 将字段数组转换为不可变列表，指定要枚举的字段
      } // enumerator方法结束
    }; // 匿名内部类结束
  } // project方法结束

  @Override public Expression getExpression(SchemaPlus schema, String tableName, // 重写getExpression方法，获取表的LINQ表达式
      Class clazz) { // 参数：clazz表示目标类类型
    return Schemas.tableExpression(schema, getElementType(), tableName, clazz); // 使用Schemas工具创建表表达式，用于LINQ查询
  } // getExpression方法结束

  @Override public Type getElementType() { // 重写getElementType方法，获取元素类型
    return Object[].class; // 返回Object[]类型，表示每行数据是一个对象数组
  } // getElementType方法结束

  @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider, // 重写asQueryable方法，将表转换为可查询对象
      SchemaPlus schema, String tableName) { // 参数：queryProvider提供查询提供者，schema是模式，tableName是表名
    throw new UnsupportedOperationException(); // 抛出不支持操作异常，此方法在CSV适配器中不被支持
  } // asQueryable方法结束

  @Override public RelNode toRel( // 重写toRel方法，将表转换为关系代数节点
      RelOptTable.ToRelContext context, // 参数：context提供转换上下文，包含集群等信息
      RelOptTable relOptTable) { // 参数：relOptTable是优化器中的表元数据
    // Request all fields. // 注释：请求所有字段，即不进行字段投影
    final int fieldCount = relOptTable.getRowType().getFieldCount(); // 获取表的字段总数
    final int[] fields = CsvEnumerator.identityList(fieldCount); // 创建恒等列表，即[0,1,2,...,fieldCount-1]，表示选择所有字段
    return new CsvTableScan(context.getCluster(), relOptTable, this, fields); // 创建并返回CsvTableScan节点，这是关系代数树中的扫描节点
  } // toRel方法结束
} // CsvTranslatableTable类结束
