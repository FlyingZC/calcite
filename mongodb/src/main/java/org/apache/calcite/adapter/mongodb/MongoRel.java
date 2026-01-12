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
package org.apache.calcite.adapter.mongodb; // 定义MongoDB适配器包，包含所有与MongoDB相关的适配器类

import org.apache.calcite.plan.Convention; // 导入Convention类，用于定义关系表达式的调用约定，表示关系表达式如何被实现
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，表示优化过程中的表对象，包含表的元数据信息
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，是Calcite中所有关系表达式节点的基类，代表关系代数操作
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，用于构建行表达式(RexNode)，如条件表达式、投影表达式等
import org.apache.calcite.runtime.PairList; // 导入PairList类，用于存储键值对列表，这里用于存储MongoDB查询操作

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的字段或参数

/**
 * Relational expression that uses Mongo calling convention.
 * 使用MongoDB调用约定的关系表达式接口
 * 
 * 这个接口是MongoDB适配器中所有关系表达式节点的基接口，它扩展了Calcite的RelNode接口
 * 所有MongoDB适配器中的关系表达式节点（如MongoFilter、MongoProject等）都实现这个接口
 * 
 * 主要作用：
 * 1. 定义MongoDB适配器中关系表达式的共同行为
 * 2. 提供implement方法，将关系表达式树转换为MongoDB查询
 * 3. 定义CONVENTION常量，标识MongoDB调用约定
 * 4. 提供Implementor内部类，用于实现MongoDB查询的转换过程
 */
public interface MongoRel extends RelNode { // 定义MongoRel接口，扩展RelNode接口，表示MongoDB关系表达式
  void implement(Implementor implementor); // 定义implement方法，用于将当前关系表达式节点转换为MongoDB查询操作，参数Implementor是转换器

  /** Calling convention for relational operations that occur in MongoDB.
   *  定义MongoDB关系操作的调用约定常量
   *  调用约定(Convention)是Calcite中的一个重要概念，用于标识关系表达式如何被实现
   *  这个常量标识了使用MongoDB来实现的关系表达式，所有MongoDB适配器中的关系表达式都使用这个约定
   */
  Convention CONVENTION = new Convention.Impl("MONGO", MongoRel.class); // 创建MongoDB调用约定实例，名称为"MONGO"，对应的接口是MongoRel

  /** Callback for the implementation process that converts a tree of
   * {@link MongoRel} nodes into a MongoDB query.
   *  实现过程的回调类，用于将MongoRel节点树转换为MongoDB查询
   *  
   *  这个类是MongoDB查询转换的核心工具类，负责遍历关系表达式树并生成对应的MongoDB查询语句
   *  
   *  主要功能：
   *  1. 维护MongoDB查询操作的列表（find操作和聚合操作）
   *  2. 存储当前处理的表信息和MongoDB表信息
   *  3. 提供RexBuilder用于构建行表达式
   *  4. 提供add方法添加MongoDB查询操作
   *  5. 提供visitChild方法访问子节点
   */
  class Implementor { // 定义Implementor内部类，负责实现MongoDB查询的转换
    final PairList<@Nullable String, String> list = PairList.of(); // 定义键值对列表，存储MongoDB查询操作，每个键值对包含find操作和聚合操作，@Nullable表示第一个String可能为null
    final RexBuilder rexBuilder; // 定义RexBuilder实例，用于构建行表达式，final表示初始化后不可改变
    @Nullable RelOptTable table; // 定义RelOptTable实例，表示当前处理的表对象，可能为null，用于存储表的元数据信息
    @Nullable MongoTable mongoTable; // 定义MongoTable实例，表示MongoDB表对象，可能为null，包含MongoDB特有的表信息

    public Implementor(RexBuilder rexBuilder) { // 定义Implementor构造方法，接收RexBuilder参数
      this.rexBuilder = rexBuilder; // 将传入的RexBuilder赋值给成员变量rexBuilder，用于后续构建行表达式
    }

    public void add(@Nullable String findOp, String aggOp) { // 定义add方法，用于添加MongoDB查询操作，findOp是find操作可能为null，aggOp是聚合操作
      list.add(findOp, aggOp); // 将find操作和聚合操作作为键值对添加到列表中，用于构建MongoDB查询管道
    }

    public void visitChild(int ordinal, RelNode input) { // 定义visitChild方法，用于访问子节点，ordinal是子节点序号，input是子节点关系表达式
      assert ordinal == 0; // 断言子节点序号必须为0，MongoDB适配器通常只处理单个子节点
      ((MongoRel) input).implement(this); // 将子节点转换为MongoRel类型并调用其implement方法，传入当前Implementor实例，递归处理子节点
    }
  }
}
