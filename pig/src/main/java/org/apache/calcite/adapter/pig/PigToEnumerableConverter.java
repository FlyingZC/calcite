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
 */ // Apache许可证声明，指定代码的使用权限和限制
package org.apache.calcite.adapter.pig; // 包声明：Pig适配器包，包含Pig数据源相关的适配器实现

import org.apache.calcite.adapter.enumerable.EnumerableRel; // 导入可枚举关系表达式接口，用于可枚举的物理实现
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor; // 导入可枚举关系表达式实现器，用于生成执行代码
import org.apache.calcite.adapter.enumerable.JavaRowFormat; // 导入Java行格式枚举，定义行数据在Java中的表示形式
import org.apache.calcite.adapter.enumerable.PhysType; // 导入物理类型接口，描述物理执行时的行类型
import org.apache.calcite.adapter.enumerable.PhysTypeImpl; // 导入物理类型实现类
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工具类，用于创建各种表达式
import org.apache.calcite.plan.ConventionTraitDef; // 导入约定特征定义，定义关系表达式的调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，包含优化器的上下文信息
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，描述关系表达式的物理属性
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系表达式的基础接口
import org.apache.calcite.rel.convert.ConverterImpl; // 导入转换器实现类，用于关系表达式之间的转换
import org.apache.calcite.runtime.Hook; // 导入钩子类，用于在特定点执行自定义逻辑
import org.apache.calcite.util.BuiltInMethod; // 导入内置方法枚举，包含Calcite预定义的常用方法

import java.util.List; // 导入List接口，用于处理集合类型

/**
 * Relational expression representing a scan of a table in a Pig data source.
 * 表示扫描Pig数据源中表的关系表达式
 * 
 * 这个类是Calcite适配器模式中的关键转换器，负责将Pig逻辑计划转换为可枚举的物理计划
 * 它实现了EnumerableRel接口，意味着它可以生成可枚举的结果集，即可以通过LINQ4J进行迭代访问
 * 
 * 主要功能：
 * 1. 将PigRel（Pig逻辑关系表达式）转换为EnumerableRel（可枚举关系表达式）
 * 2. 生成Pig Latin脚本用于后续执行
 * 3. 提供物理实现的代码生成接口
 * 
 * 工作流程：
 * 1. 接收Pig逻辑计划作为输入
 * 2. 通过PigRel.Implementor访问子节点并生成Pig Latin脚本
 * 3. 将生成的脚本通过Hook钩子暴露给外部（主要用于测试验证）
 * 4. 返回一个空的可枚举结果（当前实现未真正执行Pig脚本）
 * 
 * 注意：当前实现主要用于测试和验证，实际执行Pig脚本的功能尚未完成
 */
public class PigToEnumerableConverter // 类定义：Pig到可枚举转换器，继承自ConverterImpl并实现EnumerableRel接口
    extends ConverterImpl // 继承转换器实现基类，提供关系表达式转换的基础功能
    implements EnumerableRel { // 实现可枚举关系表达式接口，支持生成可枚举的执行代码
  /** Creates a PigToEnumerableConverter. */ // 构造函数注释：创建PigToEnumerableConverter实例
  protected PigToEnumerableConverter( // 构造函数：创建Pig到可枚举转换器实例
      RelOptCluster cluster, // 参数：关系优化集群，包含优化器的上下文信息（如RexBuilder、RelOptPlanner等）
      RelTraitSet traits, // 参数：关系特征集合，描述此关系表达式的物理属性（如约定、排序、分布等）
      RelNode input) { // 参数：输入关系节点，即需要转换的Pig逻辑计划节点
    super(cluster, ConventionTraitDef.INSTANCE, traits, input); // 调用父类构造函数，传入集群、约定特征定义、特征集和输入节点
  } // 构造函数结束，初始化转换器实例

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法：创建此关系节点的副本，用于优化过程中的节点复制
    return new PigToEnumerableConverter( // 返回新的PigToEnumerableConverter实例
        getCluster(), traitSet, sole(inputs)); // 获取当前集群、使用传入的特征集、从输入列表中获取唯一的输入节点
  } // copy方法结束，返回转换后的新节点

  /**
   * {@inheritDoc} // 继承父类方法的文档注释
   *
   * <p>This implementation does not actually execute the associated Pig Latin // 此实现不实际执行相关的Pig Latin脚本
   * script and return results. Instead it returns an empty // 而是返回一个空的可枚举结果
   * {@link org.apache.calcite.adapter.enumerable.EnumerableRel.Result} // 返回空的EnumerableRel.Result对象
   * in order to allow for testing and verification of every step of query // 以允许测试和验证查询处理的每一步
   * processing up to actual physical execution and result verification. // 直到实际的物理执行和结果验证
   *
   * <p>Next step is to invoke Pig from here, likely in local mode, have it // 下一步是从这里调用Pig，可能是在本地模式下
   * store results in a predefined file so they can be read here and returned as // 将结果存储在预定义的文件中，以便在这里读取并返回
   * a {@code Result} object. // 作为Result对象
   */
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 重写implement方法：实现可枚举关系表达式，生成执行代码块
    final BlockBuilder list = new BlockBuilder(); // 创建代码块构建器，用于构建Java方法的代码块
    final PhysType physType = // 定义物理类型变量，描述物理执行时的行数据类型
        PhysTypeImpl.of(implementor.getTypeFactory(), rowType, // 使用实现器的类型工厂和当前行的类型创建物理类型实例
            pref.prefer(JavaRowFormat.ARRAY)); // 根据偏好选择Java行格式（这里选择数组格式）
    PigRel.Implementor impl = new PigRel.Implementor(); // 创建Pig实现器实例，用于遍历Pig关系树并生成Pig Latin脚本
    impl.visitChild(0, getInput()); // 访问第0个子节点（即输入节点），开始遍历Pig关系树
    Hook.QUERY_PLAN.run(impl.getScript()); // 运行查询计划钩子，将生成的Pig Latin脚本传递给钩子（主要用于测试验证脚本正确性）
    list.add( // 向代码块构建器添加表达式
        Expressions.return_(null, // 添加return语句，返回空值
            Expressions.call( // 调用方法表达式
                BuiltInMethod.EMPTY_ENUMERABLE.method))); // 调用EMPTY_ENUMERABLE方法，返回空的枚举对象（当前实现不实际执行Pig）
    return implementor.result(physType, list.toBlock()); // 返回实现结果，包含物理类型和生成的代码块
  } // implement方法结束，返回可枚举的执行结果
} // 类定义结束