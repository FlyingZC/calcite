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
 */ // Apache 开源许可证声明，规定代码使用条件和限制
package org.apache.calcite.adapter.enumerable; // 定义包名，该类位于 Enumerable 适配器包下

import org.apache.calcite.linq4j.function.Experimental; // 导入实验性功能注解，标记 API 不稳定
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入代码块构建器，用于生成 Java 代码块
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，用于表示 LINQ 表达式树
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工具类，用于创建各种表达式
import org.apache.calcite.plan.RelOptCluster; // 导入关系表达式集群，包含优化器上下文
import org.apache.calcite.plan.RelOptTable; // 导入关系表达式表，表示逻辑表
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，定义物理属性
import org.apache.calcite.rel.RelCollationTraitDef; // 导入排序特征定义，用于定义排序特征
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入分布特征定义，用于定义数据分布特征
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系操作的基类
import org.apache.calcite.rel.core.Spool; // 导入 Spool 基类，实现数据缓存功能
import org.apache.calcite.rel.core.TableSpool; // 导入表 Spool 基类，实现表级别的数据缓存
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入关系元数据查询接口，用于获取统计信息
import org.apache.calcite.schema.ModifiableTable; // 导入可修改表接口，支持数据插入和更新
import org.apache.calcite.util.BuiltInMethod; // 导入内置方法枚举，定义常用的反射方法

/**
 * Implementation of {@link TableSpool} in
 * {@link EnumerableConvention enumerable calling convention}
 * that writes into a {@link ModifiableTable} (which must exist in the current
 * schema).
 *
 * <p>NOTE: The current API is experimental and subject to change without
 * notice.
 */ // 文档注释：说明这是 TableSpool 的 Enumerable 约定实现，将数据写入可修改表
@Experimental // 标记该类为实验性 API，可能会在不通知的情况下更改
public class EnumerableTableSpool extends TableSpool implements EnumerableRel { // 类定义：继承 TableSpool 并实现 EnumerableRel 接口，表示可枚举的表 Spool 实现

  private EnumerableTableSpool(RelOptCluster cluster, RelTraitSet traitSet, // 私有构造方法：接收集群、特征集、输入节点、读写类型和表参数
      RelNode input, Type readType, Type writeType, RelOptTable table) { // 参数：input-输入关系节点，readType-读取类型（LAZY/HOLD），writeType-写入类型（LAZY/HOLD），table-目标表
    super(cluster, traitSet, input, readType, writeType, table); // 调用父类 TableSpool 构造方法初始化基类字段
  } // 构造方法结束：完成对象初始化

  /** Creates an EnumerableTableSpool. */ // 方法文档注释：创建 EnumerableTableSpool 实例的工厂方法
  public static EnumerableTableSpool create(RelNode input, Type readType, // 静态工厂方法：创建新的 EnumerableTableSpool 实例
      Type writeType, RelOptTable table) { // 参数：input-输入关系节点，readType-读取类型，writeType-写入类型，table-目标可修改表
    RelOptCluster cluster = input.getCluster(); // 从输入节点获取关系集群，包含优化器和类型工厂
    RelMetadataQuery mq = cluster.getMetadataQuery(); // 获取元数据查询对象，用于查询输入节点的统计信息
    RelTraitSet traitSet = cluster.traitSetOf(EnumerableConvention.INSTANCE) // 创建特征集，首先指定为 Enumerable 约定（可枚举调用约定）
        .replaceIfs(RelCollationTraitDef.INSTANCE, // 如果存在排序特征，则替换为输入节点的排序特征
            () -> mq.collations(input)) // 使用元数据查询获取输入节点的排序信息
        .replaceIf(RelDistributionTraitDef.INSTANCE, // 如果存在分布特征，则替换为输入节点的分布特征
            () -> mq.distribution(input)); // 使用元数据查询获取输入节点的分布信息
    return new EnumerableTableSpool(cluster, traitSet, input, readType, writeType, table); // 创建并返回新的 EnumerableTableSpool 实例
  } // 工厂方法结束：返回配置好的 EnumerableTableSpool 对象

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 实现接口方法：生成可执行的代码实现
    // TODO for the moment only LAZY read & write is supported // 注释：当前仅支持 LAZY（延迟）读写模式
    if (readType != Type.LAZY || writeType != Type.LAZY) { // 检查读写类型是否都为 LAZY，如果不是则抛出异常
      throw new UnsupportedOperationException( // 抛出不支持操作异常
          "EnumerableTableSpool supports for the moment only LAZY read and LAZY write"); // 异常消息：说明当前只支持延迟读写
    } // 条件检查结束：确保只支持 LAZY 模式

    //  ModifiableTable t = (ModifiableTable) root.getRootSchema().getTable(tableName); // 注释：伪代码示例，展示如何获取可修改表
    //  return lazyCollectionSpool(t.getModifiableCollection(), <inputExp>); // 注释：伪代码示例，展示如何调用延迟集合 Spool

    BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于生成 Java 代码块

    RelNode input = getInput(); // 获取输入关系节点，即 Spool 的数据源
    Result inputResult = implementor.visitChild(this, 0, (EnumerableRel) input, pref); // 递归访问子节点，生成输入节点的可执行代码

    String tableName = table.getQualifiedName().get(table.getQualifiedName().size() - 1); // 从表的全限定名中提取表名（取最后一个部分）
    Expression tableExp = // 创建表达式：获取 ModifiableTable 对象
        Expressions.convert_( // 类型转换表达式，将对象转换为 ModifiableTable 类型
            Expressions.call( // 方法调用表达式
                Expressions.call(implementor.getRootExpression(), // 获取根表达式（DataContext）
                    BuiltInMethod.DATA_CONTEXT_GET_ROOT_SCHEMA.method), // 调用 getRootSchema() 方法获取根 schema
                BuiltInMethod.SCHEMA_GET_TABLE.method, // 调用 getTable() 方法获取表对象
                Expressions.constant(tableName, String.class)), // 传入表名常量
            ModifiableTable.class); // 转换为 ModifiableTable 类型
    Expression collectionExp = // 创建表达式：获取可修改集合
        Expressions.call(tableExp, // 调用 ModifiableTable 对象的方法
            BuiltInMethod.MODIFIABLE_TABLE_GET_MODIFIABLE_COLLECTION.method); // 调用 getModifiableCollection() 获取可修改集合

    Expression inputExp = builder.append("input", inputResult.block); // 将输入节点的代码块添加到构建器中，并返回表达式

    Expression spoolExp = // 创建表达式：调用延迟集合 Spool 方法
        Expressions.call(BuiltInMethod.LAZY_COLLECTION_SPOOL.method, // 调用 lazyCollectionSpool 内置方法
            collectionExp, inputExp); // 传入可修改集合和输入表达式
    builder.add(spoolExp); // 将 Spool 表达式添加到代码块中

    PhysType physType = // 创建物理类型对象，描述输出行的物理表示
        PhysTypeImpl.of(implementor.getTypeFactory(), // 使用类型工厂创建物理类型
            getRowType(), // 获取输出行类型
            pref.prefer(inputResult.format)); // 根据偏好选择格式（Java 对象或数组）
    return implementor.result(physType, builder.toBlock()); // 返回执行结果，包含物理类型和生成的代码块
  } // implement 方法结束：返回可执行代码的结果

  @Override protected Spool copy(RelTraitSet traitSet, RelNode input, // 覆盖父类方法：创建 Spool 节点的副本
      Type readType, Type writeType) { // 参数：traitSet-新的特征集，input-新的输入节点，readType-读取类型，writeType-写入类型
    return new EnumerableTableSpool(input.getCluster(), traitSet, input, // 创建并返回新的 EnumerableTableSpool 副本
        readType, writeType, table); // 使用当前表对象创建副本
  } // copy 方法结束：返回 Spool 节点的副本
} // 类定义结束：EnumerableTableSpool 类实现完成