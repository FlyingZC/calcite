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
package org.apache.calcite.rel.core;

import org.apache.calcite.linq4j.function.Experimental;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;

import static java.util.Objects.requireNonNull;

/**
 * Spool that writes into a table. // 将数据写入表的Spool操作符
 *
 * <p>NOTE: The current API is experimental and subject to change without // 注意：当前API是实验性的，可能会在没有通知的情况下发生变化
 * notice.
 */
@Experimental // 标记为实验性API，表示该接口可能在未来版本中发生变化
public abstract class TableSpool extends Spool { // TableSpool抽象类，继承自Spool，表示将数据写入表的Spool操作符

  protected final RelOptTable table; // 目标表对象，表示Spool操作要写入的表，RelOptTable是Calcite中表的抽象表示，包含表的元数据信息

  protected TableSpool(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法：创建一个TableSpool实例，cluster是RelOptCluster对象，包含查询优化器的集群信息；traitSet是RelTraitSet对象，表示该关系节点的特征集合
      RelNode input, Type readType, Type writeType, RelOptTable table) { // input是输入的RelNode节点，表示Spool操作的数据源；readType是Spool读类型，表示如何从Spool中读取数据；writeType是Spool写类型，表示如何向Spool中写入数据；table是目标表对象
    super(cluster, traitSet, input, readType, writeType); // 调用父类Spool的构造方法，初始化基本属性
    this.table = requireNonNull(table, "table"); // 初始化目标表对象，使用requireNonNull确保table不为null，如果为null则抛出NullPointerException
  }

  @Override public RelOptTable getTable() { // 重写父类方法，获取Spool操作的目标表
    return table; // 返回目标表对象
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写父类方法，用于生成该关系节点的解释信息，pw是RelWriter对象，用于构建解释文本
    super.explainTerms(pw); // 调用父类的explainTerms方法，先输出父类的解释信息
    return pw.item("table", table.getQualifiedName()); // 添加表的限定名称到解释信息中，并返回RelWriter对象以便链式调用
  }
}
