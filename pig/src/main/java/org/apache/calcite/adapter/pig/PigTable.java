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
// Apache许可证声明，指定代码的使用权限和限制条件
package org.apache.calcite.adapter.pig; // 定义包名，该类属于org.apache.calcite.adapter.pig包，是Calcite框架中Pig适配器的一部分

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于表示关系代数操作符的集群，包含优化器的上下文信息
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，用于表示优化过程中的表对象，包含表的元数据信息
import org.apache.calcite.plan.RelOptTable.ToRelContext; // 导入ToRelContext接口，用于将表转换为关系表达式时的上下文信息
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，是所有关系代数操作符的基类，代表查询计划中的一个节点
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型，包含字段类型信息
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型的工厂类
import org.apache.calcite.schema.TranslatableTable; // 导入TranslatableTable接口，标记该表可以被转换为关系表达式
import org.apache.calcite.schema.impl.AbstractTable; // 导入AbstractTable抽象类，作为表的基础实现类，提供表的基本功能

import org.apache.pig.data.DataType; // 导入Pig的DataType类，用于表示Pig中的数据类型

/**
 * Represents a Pig relation that is created by Pig Latin
 * <a href="https://pig.apache.org/docs/r0.13.0/basic.html#load">
 * <code>LOAD</code></a> statement.
 * // 表示通过Pig Latin的LOAD语句创建的Pig关系（relation）
 * // Pig Latin是Pig的数据流语言，LOAD语句用于从外部数据源加载数据
 *
 * <p>Only the default load function is supported at this point (PigStorage()).
 * // 目前只支持默认的加载函数PigStorage()，这是Pig中用于加载和存储文本数据的内置函数
 *
 * <p>Only VARCHAR (CHARARRAY in Pig) type supported at this point.
 * // 目前只支持VARCHAR类型（在Pig中称为CHARARRAY），即字符数组类型
 *
 * @see PigTableFactory
 * // 参见PigTableFactory类，该类用于创建PigTable实例的工厂类
 */
// PigTable类继承自AbstractTable抽象类并实现TranslatableTable接口
// 作用：表示一个可以通过Pig LOAD语句加载的数据表，作为Calcite和Apache Pig之间的桥梁
// AbstractTable提供了表的基本实现，TranslatableTable接口使得该表可以被转换为关系表达式用于查询优化
public class PigTable extends AbstractTable implements TranslatableTable { // 定义PigTable类，继承AbstractTable并实现TranslatableTable接口

  private final String filePath; // 成员变量：存储Pig数据文件的路径，final修饰表示该路径在创建后不可修改
  private final String[] fieldNames; // 成员变量：存储表字段名称的数组，final修饰表示字段名在创建后不可修改

  /** Creates a PigTable. */
  // 构造方法：创建一个PigTable实例
  // 参数filePath：Pig数据文件的路径，指定要加载的数据文件位置
  // 参数fieldNames：表字段名称数组，定义表包含哪些列
  public PigTable(String filePath, String[] fieldNames) { // 构造方法定义，接收文件路径和字段名数组作为参数
    this.filePath = filePath; // 将传入的文件路径赋值给成员变量filePath
    this.fieldNames = fieldNames; // 将传入的字段名数组赋值给成员变量fieldNames
  } // 构造方法结束

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写AbstractTable的getRowType方法，用于获取表的行类型（即表的结构信息）
    // 参数typeFactory：关系数据类型工厂，用于创建数据类型
    // 返回值：RelDataType对象，表示表的行类型，包含所有字段的类型信息
    final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建一个类型构建器，用于构建表的行类型
    for (String fieldName : fieldNames) { // 遍历所有字段名，为每个字段创建相应的数据类型
      // only supports CHARARRAY types for now
      // 目前只支持CHARARRAY类型（Pig中的字符数组类型）
      final RelDataType relDataType = typeFactory // 创建基本数据类型
          .createSqlType(PigDataType.valueOf(DataType.CHARARRAY).getSqlType()); // 将Pig的CHARARRAY类型转换为Calcite的SQL类型
      final RelDataType nullableRelDataType = typeFactory // 创建可空的数据类型
          .createTypeWithNullability(relDataType, true); // 将基本类型设置为可空，第二个参数true表示允许为null
      builder.add(fieldName, nullableRelDataType); // 将字段名和对应的可空类型添加到构建器中
    } // 遍历结束
    return builder.build(); // 构建并返回完整的行类型
  } // getRowType方法结束

  public String getFilePath() { // 定义getFilePath方法，用于获取Pig数据文件的路径
    return filePath; // 返回成员变量filePath的值
  } // getFilePath方法结束

  @Override public RelNode toRel(ToRelContext context, RelOptTable relOptTable) { // 重写TranslatableTable的toRel方法，将表转换为关系表达式节点
    // 参数context：转换上下文，包含集群等信息
    // 参数relOptTable：优化器表对象，包含表的元数据
    // 返回值：RelNode对象，表示表的扫描节点
    final RelOptCluster cluster = context.getCluster(); // 从上下文中获取RelOptCluster，集群包含优化器的上下文信息
    return new PigTableScan(cluster, cluster.traitSetOf(PigRel.CONVENTION), relOptTable); // 创建并返回PigTableScan节点，该节点用于扫描Pig表数据
  } // toRel方法结束，PigTableScan是专门用于扫描Pig表的关系节点，使用PigRel.CONVENTION约定
} // PigTable类定义结束
