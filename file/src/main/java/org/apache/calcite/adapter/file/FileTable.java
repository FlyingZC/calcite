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
package org.apache.calcite.adapter.file; // 声明包名，表示该类属于org.apache.calcite.adapter.file包，这是Calcite文件适配器模块

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入EnumerableConvention，用于表示可枚举的约定，是可枚举关系表达式的特征
import org.apache.calcite.adapter.enumerable.EnumerableTableScan; // 导入EnumerableTableScan，用于表示可枚举的表扫描关系节点
import org.apache.calcite.adapter.java.AbstractQueryableTable; // 导入AbstractQueryableTable，抽象可查询表基类，提供可查询表的基础实现
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory，Java类型工厂，用于创建和管理Java类型
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入AbstractEnumerable，抽象可枚举类，提供LINQ可枚举的基础实现
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable，可枚举接口，表示可以遍历的数据集合
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator，枚举器接口，用于遍历数据集合
import org.apache.calcite.linq4j.QueryProvider; // 导入QueryProvider，查询提供者接口，用于创建和执行查询
import org.apache.calcite.linq4j.Queryable; // 导入Queryable，可查询接口，表示可以执行查询的数据集合
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable，关系优化表接口，表示优化器中的表
import org.apache.calcite.rel.RelNode; // 导入RelNode，关系节点接口，表示关系代数表达式树中的节点
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType，关系数据类型接口，表示关系型数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory，关系数据类型工厂接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelProtoDataType; // 导入RelProtoDataType，关系原型数据类型接口，用于延迟创建关系数据类型
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus，增强的模式接口，提供模式注册和查找功能
import org.apache.calcite.schema.Statistic; // 导入Statistic，统计信息接口，提供表的统计信息
import org.apache.calcite.schema.Statistics; // 导入Statistics，统计信息工具类，提供常用的统计信息实现
import org.apache.calcite.schema.TranslatableTable; // 导入TranslatableTable，可转换表接口，表示可以转换为关系节点的表
import org.apache.calcite.schema.impl.AbstractTableQueryable; // 导入AbstractTableQueryable，抽象表可查询类，提供表查询的基础实现
import org.apache.calcite.util.Source; // 导入Source，源类，表示数据源（如文件、URL等）

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值

import java.util.List; // 导入List接口，表示有序集合
import java.util.Map; // 导入Map接口，表示键值对映射

/**
 * Table implementation wrapping a URL / HTML table. // 类注释：FileTable是包装URL/HTML表的表实现类
 * 该类用于将文件（如CSV、JSON、HTML表格等）作为Calcite的数据源表
 * 继承AbstractQueryableTable使其支持LINQ查询，实现TranslatableTable使其可以转换为关系代数节点
 * 主要功能包括：读取文件数据、转换数据类型、提供查询接口、转换为关系节点
 */
class FileTable extends AbstractQueryableTable // 定义FileTable类，继承AbstractQueryableTable以获得可查询表的基础功能
    implements TranslatableTable { // 实现TranslatableTable接口，使表可以转换为关系代数节点

  private final @Nullable RelProtoDataType protoRowType; // 成员变量：关系原型数据类型，用于延迟创建行类型，可能为null
  private final FileReader reader; // 成员变量：文件读取器，负责从文件源中读取原始数据
  private final FileRowConverter converter; // 成员变量：文件行转换器，负责将读取的原始数据转换为Calcite可用的行数据

  /** Creates a FileTable. */ // 方法注释：创建FileTable实例的私有构造方法
  private FileTable(Source source, String selector, Integer index, // 构造方法参数：source-数据源，selector-选择器（如CSS选择器），index-索引（用于选择特定的表）
      @Nullable RelProtoDataType protoRowType, // 构造方法参数：protoRowType-关系原型数据类型，可能为null
      List<Map<String, Object>> fieldConfigs) { // 构造方法参数：fieldConfigs-字段配置列表，每个字段包含名称、类型等配置信息
    super(Object[].class); // 调用父类构造方法，指定元素类型为Object数组，表示每行数据是一个对象数组

    this.protoRowType = protoRowType; // 初始化成员变量protoRowType，保存传入的关系原型数据类型
    this.reader = new FileReader(source, selector, index); // 初始化成员变量reader，创建FileReader实例用于读取文件数据
    this.converter = new FileRowConverter(this.reader, fieldConfigs); // 初始化成员变量converter，创建FileRowConverter实例用于转换行数据
  }

  /** Creates a FileTable. */ // 方法注释：创建FileTable实例的静态工厂方法
  static FileTable create(Source source, Map<String, Object> tableDef) { // 静态工厂方法：source-数据源，tableDef-表定义映射，包含字段配置、选择器、索引等信息
    @SuppressWarnings("unchecked") List<Map<String, Object>> fieldConfigs = // 获取字段配置列表，并抑制未检查类型转换警告
        (List<Map<String, Object>>) tableDef.get("fields"); // 从表定义中获取"fields"键对应的字段配置列表
    String selector = (String) tableDef.get("selector"); // 从表定义中获取"selector"键对应的选择器字符串
    Integer index = (Integer) tableDef.get("index"); // 从表定义中获取"index"键对应的索引值
    return new FileTable(source, selector, index, null, fieldConfigs); // 创建并返回FileTable实例，protoRowType传null表示使用默认类型推断
  }

  @Override public String toString() { // 重写toString方法，返回表的字符串表示
    return "FileTable"; // 返回固定的字符串"FileTable"作为表的标识
  }

  @Override public Statistic getStatistic() { // 重写getStatistic方法，获取表的统计信息
    return Statistics.UNKNOWN; // 返回UNKNOWN统计信息，表示表的统计信息未知（如行数、列数等）
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，获取表的行类型（即表的schema）
    if (protoRowType != null) { // 如果protoRowType不为null，说明有预定义的行类型
      return protoRowType.apply(typeFactory); // 使用protoRowType应用类型工厂创建并返回关系数据类型
    }
    return this.converter.getRowType((JavaTypeFactory) typeFactory); // 否则使用converter根据字段配置推断并返回行类型
  }

  @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider, // 重写asQueryable方法，将表转换为可查询对象
      SchemaPlus schema, String tableName) { // 参数：queryProvider-查询提供者，schema-所属模式，tableName-表名
    return new AbstractTableQueryable<T>(queryProvider, schema, this, // 创建并返回AbstractTableQueryable匿名子类实例
        tableName) { // 传入查询提供者、模式、当前表和表名
      @Override public Enumerator<T> enumerator() { // 重写enumerator方法，创建数据枚举器用于遍历表数据
        try { // 开始try块，捕获可能的异常
          FileEnumerator enumerator = // 创建FileEnumerator实例，用于枚举文件数据
              new FileEnumerator(reader.iterator(), converter); // 使用reader的迭代器和converter创建枚举器
          //noinspection unchecked // 抑制未检查类型转换警告
          return (Enumerator<T>) enumerator; // 将FileEnumerator强制转换为泛型类型T的枚举器并返回
        } catch (Exception e) { // 捕获所有异常
          throw new RuntimeException(e); // 将异常包装为运行时异常并抛出
        }
      }
    };
  }

  /** Returns an enumerable over a given projection of the fields. */ // 方法注释：返回对给定字段投影的可枚举对象
  public Enumerable<Object> project(final int[] fields) { // 方法参数：fields-字段索引数组，表示要投影的字段
    return new AbstractEnumerable<Object>() { // 创建并返回AbstractEnumerable匿名子类实例
      @Override public Enumerator<Object> enumerator() { // 重写enumerator方法，创建数据枚举器用于遍历投影后的数据
        try { // 开始try块，捕获可能的异常
          return new FileEnumerator(reader.iterator(), converter, fields); // 创建并返回FileEnumerator实例，传入字段数组用于投影
        } catch (Exception e) { // 捕获所有异常
          throw new RuntimeException(e); // 将异常包装为运行时异常并抛出
        }
      }
    };
  }

  @Override public RelNode toRel(RelOptTable.ToRelContext context, // 重写toRel方法，将表转换为关系节点
      RelOptTable relOptTable) { // 参数：context-转换上下文，relOptTable-关系优化表对象
    return new EnumerableTableScan(context.getCluster(), // 创建并返回EnumerableTableScan节点，表示可枚举的表扫描操作
        context.getCluster().traitSetOf(EnumerableConvention.INSTANCE), // 设置节点的特征集为可枚举约定
        relOptTable, (Class) getElementType()); // 传入关系优化表和元素类型
  }
}
