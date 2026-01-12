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
// Apache许可证声明，表明该代码遵循Apache 2.0许可证
package org.apache.calcite.server; // 声明包名，该类属于org.apache.calcite.server包

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式集群，用于优化过程中的上下文信息
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，表示优化过程中的表对象
import org.apache.calcite.prepare.Prepare; // 导入Prepare类，包含SQL准备和编译的相关功能
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式的基础接口
import org.apache.calcite.rel.core.TableModify; // 导入TableModify类，表示表修改操作的关系表达式
import org.apache.calcite.rel.logical.LogicalTableModify; // 导入LogicalTableModify类，表示逻辑层面的表修改操作
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，表示行表达式，用于条件、投影等
import org.apache.calcite.schema.ModifiableTable; // 导入ModifiableTable接口，定义可修改表的行为
import org.apache.calcite.schema.impl.AbstractTable; // 导入AbstractTable类，抽象表的基础实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的参数

import java.util.List; // 导入List接口，用于列表集合

/** Abstract base class for implementations of {@link ModifiableTable}. */ // Javadoc注释：这是ModifiableTable接口实现的抽象基类，提供了可修改表的通用实现框架
abstract class AbstractModifiableTable // 定义抽象类AbstractModifiableTable，继承自AbstractTable并实现ModifiableTable接口
    extends AbstractTable implements ModifiableTable { // 继承AbstractTable抽象表基类，实现ModifiableTable可修改表接口
  AbstractModifiableTable(String ignoredTableName) { // 构造方法，接收一个被忽略的表名参数，该参数不被使用但仍保留以保持接口兼容性
    super(); // 调用父类AbstractTable的构造方法进行初始化
  } // 构造方法结束

  @Override public TableModify toModificationRel( // 重写toModificationRel方法，将表操作转换为关系表达式，返回TableModify对象
      RelOptCluster cluster, // 参数cluster：关系表达式集群，包含优化上下文信息和工厂对象
      RelOptTable table, // 参数table：要修改的表对象，包含表的元数据信息
      Prepare.CatalogReader catalogReader, // 参数catalogReader：目录读取器，用于访问数据库目录信息
      RelNode child, // 参数child：子关系表达式，通常是数据源或查询的结果
      TableModify.Operation operation, // 参数operation：表操作类型，包括INSERT、UPDATE、DELETE等
      @Nullable List<String> updateColumnList, // 参数updateColumnList：UPDATE操作时要更新的列名列表，可为null
      @Nullable List<RexNode> sourceExpressionList, // 参数sourceExpressionList：源表达式列表，用于UPDATE或INSERT的值，可为null
      boolean flattened) { // 参数flattened：是否扁平化，用于处理嵌套结构
    return LogicalTableModify.create(table, catalogReader, child, operation, // 创建并返回LogicalTableModify对象，表示逻辑层面的表修改操作
        updateColumnList, sourceExpressionList, flattened); // 传递更新列列表、源表达式列表和扁平化标志
  } // toModificationRel方法结束
} // AbstractModifiableTable类定义结束
