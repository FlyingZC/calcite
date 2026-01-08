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
package org.apache.calcite.adapter.enumerable;  // 声明包名，表示该类属于可枚举适配器包

import org.apache.calcite.linq4j.tree.BlockBuilder;  // 导入代码块构建器，用于构建Java表达式块
import org.apache.calcite.linq4j.tree.Expression;  // 导入表达式类，用于表示LINQ表达式树中的节点
import org.apache.calcite.linq4j.tree.Expressions;  // 导入表达式工具类，提供创建各种表达式的方法
import org.apache.calcite.plan.RelOptCluster;  // 导入关系优化集群，包含查询计划的相关信息
import org.apache.calcite.plan.RelTraitSet;  // 导入关系特征集合，定义关系节点的物理属性
import org.apache.calcite.rel.RelCollation;  // 导入关系排序规范，定义排序的字段和方向
import org.apache.calcite.rel.RelNode;  // 导入关系节点接口，表示关系代数运算的基本单元
import org.apache.calcite.rel.core.Sort;  // 导入排序关系节点基类，提供排序操作的抽象实现
import org.apache.calcite.rex.RexNode;  // 导入行表达式节点，表示行级别的表达式
import org.apache.calcite.util.BuiltInMethod;  // 导入内置方法枚举，定义LINQ4J中的内置方法
import org.apache.calcite.util.Pair;  // 导入键值对工具类，用于存储两个相关的对象

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入可空注解，标记可能为null的参数

/** Implementation of {@link org.apache.calcite.rel.core.Sort} in  // 类文档注释：这是Sort关系节点在可枚举约定下的实现
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}. */
public class EnumerableSort extends Sort implements EnumerableRel {  // 声明EnumerableSort类，继承Sort基类并实现EnumerableRel接口，表示可枚举的排序操作
  /**
   * Creates an EnumerableSort.  // 方法文档注释：创建一个EnumerableSort实例
   *
   * <p>Use {@link #create} unless you know what you're doing.  // 建议使用create工厂方法，除非你明确知道自己在做什么
   */
  public EnumerableSort(RelOptCluster cluster, RelTraitSet traitSet,  // 构造函数参数：cluster-关系优化集群，包含类型工厂等共享资源
      RelNode input, RelCollation collation, @Nullable RexNode offset, @Nullable RexNode fetch) {  // 构造函数参数：input-输入关系节点；collation-排序规范；offset-偏移量；fetch-获取行数限制
    super(cluster, traitSet, input, collation, offset, fetch);  // 调用父类Sort的构造函数，初始化基本属性
    assert getConvention() instanceof EnumerableConvention;  // 断言当前节点的调用约定必须是可枚举约定，确保物理实现类型正确
    assert getConvention() == input.getConvention();  // 断言当前节点与输入节点的调用约定必须一致，确保特征集兼容
    assert fetch == null : "fetch must be null";  // 断言fetch必须为null，限制功能暂不支持
    assert offset == null : "offset must be null";  // 断言offset必须为null，偏移功能暂不支持
  }  // 构造函数结束

  /** Creates an EnumerableSort. */  // 静态工厂方法文档注释：创建EnumerableSort实例的推荐方式
  public static EnumerableSort create(RelNode child, RelCollation collation,  // 方法参数：child-子节点（输入关系）；collation-排序规范
      @Nullable RexNode offset, @Nullable RexNode fetch) {  // 方法参数：offset-偏移量；fetch-获取行数限制
    final RelOptCluster cluster = child.getCluster();  // 从子节点获取关系优化集群，用于共享类型工厂等资源
    final RelTraitSet traitSet =  // 创建关系特征集合
        cluster.traitSetOf(EnumerableConvention.INSTANCE)  // 首先设置调用约定为可枚举约定
            .replace(collation);  // 然后替换为指定的排序规范，确保特征集包含排序信息
    return new EnumerableSort(cluster, traitSet, child, collation, offset,  // 返回新创建的EnumerableSort实例
        fetch);  // 传入offset和fetch参数（虽然当前实现不支持）
  }  // 静态工厂方法结束

  @Override public EnumerableSort copy(  // 覆盖copy方法：创建当前节点的副本，用于优化过程中的节点转换
      RelTraitSet traitSet,  // 参数：新的关系特征集合
      RelNode newInput,  // 参数：新的输入关系节点
      RelCollation newCollation,  // 参数：新的排序规范
      @Nullable RexNode offset,  // 参数：偏移量表达式
      @Nullable RexNode fetch) {  // 参数：获取行数限制表达式
    return new EnumerableSort(getCluster(), traitSet, newInput, newCollation,  // 返回新的EnumerableSort实例，使用当前集群和提供的参数
        offset,  // 传入offset参数
        fetch);  // 传入fetch参数
  }  // copy方法结束

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) {  // 实现方法：将关系节点转换为可执行的Java代码，返回执行结果
    final BlockBuilder builder = new BlockBuilder();  // 创建代码块构建器，用于构建Java方法的代码块
    final EnumerableRel child = (EnumerableRel) getInput();  // 获取输入子节点并转换为可枚举关系类型
    final Result result = implementor.visitChild(this, 0, child, pref);  // 递归访问子节点，生成子节点的执行代码和物理类型信息
    final PhysType physType =  // 创建当前节点的物理类型描述
        PhysTypeImpl.of(  // 使用物理类型实现类工厂方法
            implementor.getTypeFactory(),  // 传入类型工厂，用于创建Java类型
            getRowType(),  // 传入行类型，描述输出行的结构
            result.format);  // 传入格式偏好，决定Java类的生成方式
    Expression childExp =  // 创建子节点执行的表达式
        builder.append("child", result.block);  // 将子节点的代码块添加到构建器中，并返回对应的表达式引用

    PhysType inputPhysType = result.physType;  // 获取输入的物理类型，用于生成排序键
    final Pair<Expression, Expression> pair =  // 创建键值对，存储排序键选择器和比较器
        inputPhysType.generateCollationKey(  // 根据输入物理类型生成排序键
            collation.getFieldCollations());  // 传入字段排序规范列表，生成对应的键选择器和比较器表达式

    builder.add(  // 向代码块构建器添加返回语句
        Expressions.return_(null,  // 创建返回表达式，不使用标签
            Expressions.call(childExp,  // 创建方法调用表达式，在子节点表达式上调用排序方法
                BuiltInMethod.ORDER_BY.method,  // 调用LINQ4J的ORDER_BY内置方法，实现排序功能
                Expressions.list(  // 创建方法参数列表
                    builder.append("keySelector", pair.left))  // 添加键选择器参数，用于提取排序键
                    .appendIfNotNull(  // 如果不为null则添加参数
                        builder.appendIfNotNull("comparator", pair.right)))));  // 添加比较器参数，用于自定义排序规则
    return implementor.result(physType, builder.toBlock());  // 返回实现结果，包含物理类型和生成的代码块
  }  // implement方法结束
}  // 类定义结束
