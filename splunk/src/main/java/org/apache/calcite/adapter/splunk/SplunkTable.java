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
package org.apache.calcite.adapter.splunk;  // 包声明：Splunk适配器包，包含Splunk数据源的适配器实现

import org.apache.calcite.adapter.java.AbstractQueryableTable;  // 导入：抽象可查询表基类，提供LINQ查询支持
import org.apache.calcite.adapter.java.JavaTypeFactory;  // 导入：Java类型工厂，用于创建Java类型
import org.apache.calcite.linq4j.Enumerator;  // 导入：枚举器接口，用于遍历查询结果
import org.apache.calcite.linq4j.QueryProvider;  // 导入：查询提供者接口，用于执行LINQ查询
import org.apache.calcite.linq4j.Queryable;  // 导入：可查询接口，表示可执行LINQ查询的数据源
import org.apache.calcite.plan.RelOptTable;  // 导入：关系优化表接口，表示优化过程中的表
import org.apache.calcite.rel.RelNode;  // 导入：关系节点接口，表示关系代数表达式
import org.apache.calcite.rel.type.RelDataType;  // 导入：关系数据类型接口，表示表或字段的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;  // 导入：关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.schema.SchemaPlus;  // 导入：Schema增强接口，提供额外的Schema功能
import org.apache.calcite.schema.TranslatableTable;  // 导入：可转换表接口，支持转换为关系代数节点
import org.apache.calcite.schema.impl.AbstractTableQueryable;  // 导入：抽象表可查询类，提供表查询的基础实现

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入：可空注解，标记可能为null的参数

import java.util.List;  // 导入：List接口，用于存储字段列表

import static java.util.Objects.requireNonNull;  // 静态导入：requireNonNull方法，用于参数非空检查

/**
 * Table based on Splunk.  // 基于Splunk的表实现，表示Splunk数据源中的表
 * 这个类实现了Calcite适配器模式，将Splunk数据源作为Calcite中的表来使用
 * 它继承自AbstractQueryableTable以支持LINQ查询，并实现TranslatableTable以支持转换为关系代数
 */
class SplunkTable extends AbstractQueryableTable implements TranslatableTable {  // 类定义：Splunk表类，继承抽象可查询表并实现可转换表接口
  public static final SplunkTable INSTANCE = new SplunkTable();  // 成员变量：单例实例，全局唯一的SplunkTable对象，用于表示Splunk数据源

  private SplunkTable() {  // 私有构造方法：确保只能通过单例INSTANCE访问，防止外部创建实例
    super(Object[].class);  // 调用父类构造方法：指定行类型为Object数组，表示每行数据是一个对象数组
  }

  @Override public String toString() {  // 方法重写：toString方法，返回表的字符串表示
    return "SplunkTable";  // 返回：表的名称标识
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {  // 方法重写：获取行类型，定义表的结构（字段和类型）
    RelDataType stringType =  // 局部变量：创建String类型的RelDataType对象
        ((JavaTypeFactory) typeFactory).createType(String.class);  // 类型转换：将类型工厂转换为JavaTypeFactory并创建String类型
    return typeFactory.builder()  // 返回：使用类型工厂构建器创建行类型
        .add("source", stringType)  // 添加字段：source字段，表示数据源，类型为String
        .add("sourcetype", stringType)  // 添加字段：sourcetype字段，表示数据源类型，类型为String
        .add("_extra", stringType)  // 添加字段：_extra字段，表示额外数据，类型为String
        .build();  // 构建：完成行类型的构建
  }

  @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider,  // 方法重写：将表转换为可查询对象，支持LINQ查询
      SchemaPlus schema, String tableName) {  // 参数：queryProvider查询提供者，schema所属Schema，tableName表名
    return new SplunkTableQueryable<>(queryProvider, schema, this, tableName);  // 返回：创建并返回SplunkTableQueryable实例，封装查询逻辑
  }

  @Override public RelNode toRel(  // 方法重写：将表转换为关系节点，用于查询优化和执行
      RelOptTable.ToRelContext context,  // 参数：context转换上下文，包含集群等信息
      RelOptTable relOptTable) {  // 参数：relOptTable关系优化表对象
    return new SplunkTableScan(  // 返回：创建SplunkTableScan节点，表示对Splunk表的扫描操作
        context.getCluster(),  // 参数：集群对象，包含优化器的共享信息
        relOptTable,  // 参数：关系优化表对象
        this,  // 参数：当前SplunkTable实例
        "search",  // 参数：默认搜索命令，表示Splunk的search操作
        null,  // 参数：最早时间参数，null表示无时间限制
        null,  // 参数：最晚时间参数，null表示无时间限制
        relOptTable.getRowType().getFieldNames());  // 参数：字段名称列表，从表行类型中获取
  }

  /** Implementation of {@link Queryable} backed by a {@link SplunkTable}.  // 注释：Queryable接口的实现，由SplunkTable支持
   * Generated code uses this get a Splunk connection for executing arbitrary  // 说明：生成的代码使用它来获取Splunk连接以执行任意的
   * Splunk queries.  // Splunk查询
   *
   * @param <T> element type */  // 泛型参数：元素类型T，表示查询结果的元素类型
  public static class SplunkTableQueryable<T>  // 内部类定义：SplunkTableQueryable，实现Queryable接口，提供查询功能
      extends AbstractTableQueryable<T> {  // 继承：抽象表可查询类，继承基础的查询功能
    SplunkTableQueryable(QueryProvider queryProvider, SchemaPlus schema,  // 构造方法：初始化SplunkTableQueryable实例
        SplunkTable table, String tableName) {  // 参数：queryProvider查询提供者，schema所属Schema，table关联的SplunkTable，tableName表名
      super(queryProvider, schema, table, tableName);  // 调用父类构造方法：传递参数给父类进行初始化
    }

    @Override public Enumerator<T> enumerator() {  // 方法重写：创建枚举器，用于遍历查询结果
      final SplunkQuery<T> query = createQuery("search", null, null, null);  // 局部变量：创建默认的Splunk查询对象，使用search命令，无时间限制，无字段列表
      return query.enumerator();  // 返回：从查询对象获取枚举器，用于遍历结果
    }

    public SplunkQuery<T> createQuery(String search, @Nullable String earliest,  // 方法：创建Splunk查询对象，用于构建和执行Splunk查询
        @Nullable String latest, @Nullable List<String> fieldList) {  // 参数：search搜索命令字符串，earliest最早时间，latest最晚时间，fieldList字段列表
      final SplunkSchema splunkSchema =  // 局部变量：从schema中解包获取SplunkSchema对象
          requireNonNull(schema.unwrap(SplunkSchema.class));  // 方法调用：解包Schema并确保非空，获取SplunkSchema实例
      return new SplunkQuery<>(splunkSchema.splunkConnection, search,  // 返回：创建并返回SplunkQuery实例，使用Splunk连接和查询参数
          earliest, latest, fieldList);  // 参数传递：时间范围和字段列表
    }
  }
}
