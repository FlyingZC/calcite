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
package org.apache.calcite.test;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.prepare.Prepare;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableModify;
import org.apache.calcite.rel.logical.LogicalTableModify;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.schema.ModifiableTable;
import org.apache.calcite.schema.impl.AbstractTable;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

/**
 * Abstract base class for implementations of {@link ModifiableTable}.
 */
public abstract class AbstractModifiableTable
    extends AbstractTable implements ModifiableTable {
  protected AbstractModifiableTable(String ignoredTableName) {
  }

  @Override public TableModify toModificationRel( // 重写接口方法，将表修改操作转换为关系表达式节点
      RelOptCluster cluster, // 参数：关系表达式簇，包含类型系统和表达式工厂等信息
      RelOptTable table, // 参数：要修改的目标表对象，包含表的元数据信息
      Prepare.CatalogReader catalogReader, // 参数：目录读取器，用于访问目录中的表和字段信息
      RelNode child, // 参数：子关系节点，表示要插入的数据或用于更新的数据源
      TableModify.Operation operation, // 参数：修改操作类型，包括 INSERT、UPDATE、DELETE 等
      @Nullable List<String> updateColumnList, // 参数：要更新的列名列表，仅在 UPDATE 操作时使用，可为 null
      @Nullable List<RexNode> sourceExpressionList, // 参数：源表达式列表，用于指定更新值或插入值，可为 null
      boolean flattened) { // 参数：是否扁平化，表示是否需要扁平化处理嵌套结构
    return LogicalTableModify.create(table, catalogReader, child, operation, // 创建并返回一个逻辑表修改节点
        updateColumnList, sourceExpressionList, flattened); // 传递所有参数给工厂方法，构建完整的修改操作节点
  } // 方法结束，返回构建的 LogicalTableModify 对象
}
