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
package org.apache.calcite.adapter.enumerable;  // 声明包名，该类位于org.apache.calcite.adapter.enumerable包中

import org.apache.calcite.linq4j.function.Experimental;  // 导入实验性功能注解，标记该API是实验性的
import org.apache.calcite.linq4j.function.Function0;  // 导入无参数函数接口，用于创建lambda表达式
import org.apache.calcite.linq4j.tree.BlockBuilder;  // 导入代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.Expression;  // 导入表达式类，用于表示Java表达式
import org.apache.calcite.linq4j.tree.Expressions;  // 导入表达式工具类，用于创建各种表达式
import org.apache.calcite.plan.RelOptCluster;  // 导入关系表达式集群，用于管理关系表达式
import org.apache.calcite.plan.RelOptTable;  // 导入关系优化表，表示表对象
import org.apache.calcite.plan.RelTraitSet;  // 导入关系特征集合，定义关系表达式的物理属性
import org.apache.calcite.rel.RelNode;  // 导入关系节点接口，所有关系表达式的基类
import org.apache.calcite.rel.core.RepeatUnion;  // 导入重复联合关系节点基类，用于实现递归查询
import org.apache.calcite.schema.TransientTable;  // 导入临时表接口，用于存储中间结果
import org.apache.calcite.util.BuiltInMethod;  // 导入内置方法枚举，包含Calcite预定义的方法
import org.apache.calcite.util.Util;  // 导入工具类，提供各种实用方法

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入可空注解，用于标记可能为null的参数

import java.util.List;  // 导入List接口，用于存储集合数据

import static org.apache.calcite.util.Util.last;  // 静态导入last方法，用于获取集合的最后一个元素

import static java.util.Objects.requireNonNull;  // 静态导入requireNonNull方法，用于非空检查

/**
 * Implementation of {@link RepeatUnion} in
 * {@link EnumerableConvention enumerable calling convention}.
 * 在可枚举调用约定中实现RepeatUnion关系节点
 *
 * <p>RepeatUnion是一个特殊的关系运算符，用于实现递归查询（如WITH RECURSIVE子句）
 * 它的工作原理是：
 * 1. 从seed（种子）关系开始，这是递归的初始结果集
 * 2. 将seed与iterative（迭代）关系进行联合（UNION）
 * 3. 使用联合后的结果再次与iterative进行联合
 * 4. 重复此过程，直到没有新的行产生或达到迭代次数限制
 * 
 * 例如，对于递归查询：
 * WITH RECURSIVE cte AS (
 *   SELECT 1 AS n  -- seed部分
 *   UNION ALL
 *   SELECT n + 1 FROM cte WHERE n < 10  -- iterative部分
 * )
 * SELECT * FROM cte;
 * 
 * seed会生成初始行(1)，然后iterative会基于当前结果生成新行(2,3,4...)，直到n>=10
 *
 * <p>NOTE: The current API is experimental and subject to change without
 * notice.
 * 注意：当前API是实验性的，可能会在没有通知的情况下更改
 */
@Experimental  // 标记该类为实验性API
public class EnumerableRepeatUnion extends RepeatUnion implements EnumerableRel {  // 声明类，继承RepeatUnion并实现EnumerableRel接口，表示这是可枚举的重复联合节点

  /**
   * Creates an EnumerableRepeatUnion.
   * 创建一个EnumerableRepeatUnion实例
   * 
   * 构造函数参数说明：
   * @param cluster 关系表达式集群，包含所有关系表达式的共享信息（如类型工厂、表达式工厂等）
   * @param traitSet 关系特征集合，定义该节点的物理属性（如约定、排序、分布等）
   * @param seed 种子关系节点，递归查询的初始结果集（如WITH RECURSIVE的第一个SELECT）
   * @param iterative 迭代关系节点，每次迭代要执行的关系操作（如WITH RECURSIVE的第二个SELECT）
   * @param all 是否保留重复行，true表示UNION ALL（保留重复），false表示UNION（去重）
   * @param iterationLimit 迭代次数限制，防止无限递归，0表示无限制
   * @param transientTable 临时表，可选参数，用于存储中间结果以提高性能
   */
  EnumerableRepeatUnion(RelOptCluster cluster, RelTraitSet traitSet,  // 构造函数参数：集群和特征集合
      RelNode seed, RelNode iterative, boolean all, int iterationLimit,  // 构造函数参数：种子节点、迭代节点、是否保留重复、迭代限制
      @Nullable RelOptTable transientTable) {  // 构造函数参数：可选的临时表
    super(cluster, traitSet, seed, iterative, all, iterationLimit, transientTable);  // 调用父类RepeatUnion的构造函数，初始化所有成员变量
  }

  /**
   * Copies this relational expression, substituting traits and inputs.
   * 复制该关系表达式，替换特征集合和输入节点
   * 
   * 这是Calcite关系代数中的标准方法，用于创建该节点的副本
   * 在优化过程中，优化器会频繁复制节点并修改其属性（如特征集合）
   * 
   * @param traitSet 新的特征集合，可能包含不同的物理属性（如不同的排序或分布）
   * @param inputs 新的输入节点列表，对于RepeatUnion应该包含2个节点：seed和iterative
   * @return 新的EnumerableRepeatUnion实例，具有指定的特征集合和输入节点
   */
  @Override public EnumerableRepeatUnion copy(RelTraitSet traitSet, List<RelNode> inputs) {  // 重写copy方法，返回新的EnumerableRepeatUnion实例
    assert inputs.size() == 2;  // 断言输入列表必须包含2个节点（seed和iterative），否则抛出AssertionError
    return new EnumerableRepeatUnion(getCluster(), traitSet,  // 创建新实例，使用当前集群、新特征集合
        inputs.get(0), inputs.get(1), all, iterationLimit, transientTable);  // 使用输入列表的两个节点作为seed和iterative，并保留其他属性
  }

  /**
   * Implements this relational expression as a Java expression.
   * 实现该关系表达式为Java表达式
   * 
   * 这是EnumerableRel接口的核心方法，负责将关系节点转换为可执行的Java代码
   * 生成的代码会使用LINQ4J库来处理数据流
   * 
   * 实现过程：
   * 1. 创建代码块构建器（BlockBuilder）
   * 2. 如果有临时表，先注册临时表到schema中
   * 3. 递归实现seed和iterative子节点
   * 4. 创建repeatUnion方法调用表达式
   * 5. 返回包含实现代码的Result对象
   * 
   * @param implementor 可枚举关系实现器，负责生成Java代码
   * @param pref 实现偏好，指示如何格式化结果（如数组和列表的偏好）
   * @return Result对象，包含生成的Java代码块和物理类型信息
   */
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) {  // 重写implement方法，生成Java代码实现

    // return repeatUnion(<seedExp>, <iterativeExp>, iterationLimit, all, <comparer>);
    // 最终生成的代码会调用repeatUnion方法，传入种子表达式、迭代表达式、迭代限制、是否保留重复和比较器

    BlockBuilder builder = new BlockBuilder();  // 创建代码块构建器，用于构建Java代码块的语句序列
    RelNode seed = getSeedRel();  // 获取种子关系节点（递归的初始结果集）
    RelNode iteration = getIterativeRel();  // 获取迭代关系节点（每次迭代要执行的操作）

    // 初始化清理函数表达式为null，如果没有临时表则不需要清理
    Expression cleanUpFunctionExp = Expressions.constant(null);  // 创建常量null表达式，用于清理函数（默认为null）
    if (transientTable != null) {  // 如果存在临时表（用于存储中间结果以提高性能）
      // root.getRootSchema().add(tableName, table);
      // 以下代码生成：将临时表注册到根schema中，使其可以在查询中被访问
      
      // 将临时表对象存储到实现器的stash中，并获取其表达式表示
      Expression tableExp =  // 创建临时表的表达式
          implementor.stash(  // 调用实现器的stash方法，将对象存储并返回表达式
              requireNonNull(transientTable.unwrap(TransientTable.class)),  // 从RelOptTable中解包TransientTable对象，确保不为null
              TransientTable.class);  // 指定对象的类型为TransientTable
      String tableName = last(transientTable.getQualifiedName());  // 获取临时表的名称（从限定名称中取最后一个部分）
      Expression tableNameExp = Expressions.constant(tableName, String.class);  // 创建表名的字符串常量表达式
      
      // 生成代码：root.getRootSchema().add(tableName, table)
      // 将临时表添加到根schema中，使其可以在查询中被引用
      builder.append(  // 将表达式追加到代码块中
          Expressions.call(  // 创建方法调用表达式
              Expressions.call(  // 创建嵌套的方法调用：先调用getRootSchema()
                  implementor.getRootExpression(),  // 获取根表达式（DataContext）
                  BuiltInMethod.DATA_CONTEXT_GET_ROOT_SCHEMA.method),  // 调用getRootSchema()方法
              BuiltInMethod.SCHEMA_PLUS_ADD_TABLE.method,  // 然后调用addTable()方法
              tableNameExp,  // 第一个参数：表名
              tableExp));  // 第二个参数：临时表对象
      
      // root.getRootSchema().removeTable(tableName);
      // 以下代码生成：创建清理函数，在查询完成后从schema中移除临时表
      
      // 生成lambda表达式：() -> root.getRootSchema().removeTable(tableName)
      // 这是一个无参数的函数，用于在查询结束后清理临时表
      cleanUpFunctionExp =  // 创建清理函数表达式
          Expressions.lambda(Function0.class,  // 创建lambda表达式，类型为Function0（无参数函数）
              Expressions.call(  // lambda体：调用removeTable方法
                  Expressions.call(implementor.getRootExpression(),  // 获取根表达式
                      BuiltInMethod.DATA_CONTEXT_GET_ROOT_SCHEMA.method),  // 调用getRootSchema()方法
                  BuiltInMethod.SCHEMA_PLUS_REMOVE_TABLE.method, tableNameExp));  // 调用removeTable()方法，传入表名
    }

    // 递归实现子节点，生成它们的Java代码
    // visitChild方法会调用子节点的implement方法，返回包含实现代码的Result对象
    Result seedResult = implementor.visitChild(this, 0, (EnumerableRel) seed, pref);  // 实现seed子节点（索引0），获取实现结果
    Result iterationResult = implementor.visitChild(this, 1, (EnumerableRel) iteration, pref);  // 实现iterative子节点（索引1），获取实现结果

    // 将子节点的代码块追加到builder中，并获取表达式
    // 这些表达式代表子节点生成的可枚举对象
    Expression seedExp = builder.append("seed", seedResult.block);  // 将seed的代码块追加到builder，命名为"seed"，获取表达式
    Expression iterativeExp = builder.append("iteration", iterationResult.block);  // 将iterative的代码块追加到builder，命名为"iteration"，获取表达式

    // 创建物理类型对象，描述行数据的Java表示形式
    // PhysType包含行类型信息、格式偏好和比较器等
    PhysType physType =  // 创建物理类型对象
        PhysTypeImpl.of(implementor.getTypeFactory(), getRowType(),  // 使用类型工厂和行类型创建
            pref.prefer(seedResult.format));  // 根据seed结果的格式偏好来选择物理类型格式

    // 创建repeatUnion方法调用表达式
    // 该方法会执行递归联合操作，不断迭代直到满足终止条件
    Expression unionExp =  // 创建repeatUnion方法调用表达式
        Expressions.call(BuiltInMethod.REPEAT_UNION.method,  // 调用内置的REPEAT_UNION方法
            seedExp,  // 第1个参数：种子可枚举表达式（初始结果集）
            iterativeExp,  // 第2个参数：迭代可枚举表达式（每次迭代的操作）
            Expressions.constant(iterationLimit, int.class),  // 第3个参数：迭代次数限制（常量表达式）
            Expressions.constant(all, boolean.class),  // 第4个参数：是否保留重复行（常量表达式）
            Util.first(physType.comparer(),  // 第5个参数：比较器，用于判断行是否相等（用于去重）
                Expressions.call(BuiltInMethod.IDENTITY_COMPARER.method)),  // 如果没有自定义比较器，使用恒等比较器
            cleanUpFunctionExp);  // 第6个参数：清理函数（用于移除临时表），可能为null
    builder.add(unionExp);  // 将repeatUnion调用添加到代码块中

    // 返回实现结果，包含生成的代码块和物理类型信息
    return implementor.result(physType, builder.toBlock());  // 调用实现器的result方法，创建Result对象并返回
  }

}  // 类定义结束
