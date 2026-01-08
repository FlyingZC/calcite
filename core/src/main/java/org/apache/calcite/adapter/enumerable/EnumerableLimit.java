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
package org.apache.calcite.adapter.enumerable;  // 声明包名，属于可枚举适配器包，用于实现可枚举的关系代数运算

import org.apache.calcite.DataContext;  // 导入数据上下文类，用于在运行时获取参数值
import org.apache.calcite.linq4j.tree.BlockBuilder;  // 导入代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.Expression;  // 导入表达式类，表示LINQ4J表达式树中的节点
import org.apache.calcite.linq4j.tree.Expressions;  // 导入表达式工具类，提供创建表达式的静态方法
import org.apache.calcite.plan.RelOptCluster;  // 导入关系优化集群，包含优化器和元数据
import org.apache.calcite.plan.RelTraitSet;  // 导入关系特征集合，定义关系节点的物理属性
import org.apache.calcite.rel.RelCollationTraitDef;  // 导入排序特征定义，用于描述数据的排序方式
import org.apache.calcite.rel.RelDistributionTraitDef;  // 导入分布特征定义，用于描述数据的分布方式
import org.apache.calcite.rel.RelNode;  // 导入关系节点接口，所有关系代数运算的基类
import org.apache.calcite.rel.RelWriter;  // 导入关系写入器，用于将关系节点转换为可读的字符串
import org.apache.calcite.rel.SingleRel;  // 导入单输入关系节点基类，表示只有一个输入的关系运算
import org.apache.calcite.rel.metadata.RelMdCollation;  // 导入排序元数据提供者，用于推断Limit后的排序属性
import org.apache.calcite.rel.metadata.RelMdDistribution;  // 导入分布元数据提供者，用于推断Limit后的分布属性
import org.apache.calcite.rel.metadata.RelMetadataQuery;  // 导入元数据查询接口，用于查询关系节点的元数据
import org.apache.calcite.rex.RexDynamicParam;  // 导入动态参数类，表示SQL中的参数化值（如?）
import org.apache.calcite.rex.RexLiteral;  // 导入字面量类，表示SQL中的常量值
import org.apache.calcite.rex.RexNode;  // 导入行表达式节点接口，表示行表达式的基类
import org.apache.calcite.util.BuiltInMethod;  // 导入内置方法枚举，定义了LINQ4J的内置方法

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入可空注解，用于标记可能为null的值

import java.util.List;  // 导入List接口，用于处理集合数据

/** Relational expression that applies a limit and/or offset to its input. */  // 类注释：应用limit（限制行数）和/或offset（跳过行数）的关系表达式
public class EnumerableLimit extends SingleRel implements EnumerableRel {  // 类定义：可枚举的Limit操作，继承自单输入关系节点，实现可枚举关系接口
  public final @Nullable RexNode offset;  // 成员变量：offset表示跳过的行数，类型为行表达式节点，可为null（表示不跳过），final表示不可变
  public final @Nullable RexNode fetch;  // 成员变量：fetch表示获取的行数，类型为行表达式节点，可为null（表示获取所有行），final表示不可变

  /** Creates an EnumerableLimit.  // 方法注释：创建一个EnumerableLimit实例
   *
   * <p>Use {@link #create} unless you know what you're doing. */  // 建议：除非你清楚自己在做什么，否则使用create方法
  public EnumerableLimit(  // 构造方法：创建EnumerableLimit实例
      RelOptCluster cluster,  // 参数：关系优化集群，包含优化器和元数据查询能力
      RelTraitSet traitSet,  // 参数：关系特征集合，定义此Limit操作的物理属性（如约定、排序、分布等）
      RelNode input,  // 参数：输入关系节点，即Limit操作要处理的数据源
      @Nullable RexNode offset,  // 参数：跳过的行数表达式，可为null
      @Nullable RexNode fetch) {  // 参数：获取的行数表达式，可为null
    super(cluster, traitSet, input);  // 调用父类SingleRel构造方法，初始化集群、特征集和输入节点
    this.offset = offset;  // 将offset参数赋值给成员变量
    this.fetch = fetch;  // 将fetch参数赋值给成员变量
    assert getConvention() instanceof EnumerableConvention;  // 断言：确保当前操作的约定是可枚举约定
    assert getConvention() == input.getConvention();  // 断言：确保当前操作的约定与输入节点的约定一致
  }  // 构造方法结束

  /** Creates an EnumerableLimit. */  // 方法注释：工厂方法，创建一个EnumerableLimit实例（推荐使用此方法）
  public static EnumerableLimit create(final RelNode input, @Nullable RexNode offset,  // 静态工厂方法：创建Limit节点
      @Nullable RexNode fetch) {  // 参数：offset和fetch的含义同构造方法
    final RelOptCluster cluster = input.getCluster();  // 获取输入节点的优化集群
    final RelMetadataQuery mq = cluster.getMetadataQuery();  // 从集群中获取元数据查询对象
    final RelTraitSet traitSet =  // 创建关系特征集合
        cluster.traitSetOf(EnumerableConvention.INSTANCE)  // 基础特征：使用可枚举约定
            .replaceIfs(  // 条件替换排序特征
                RelCollationTraitDef.INSTANCE,  // 排序特征定义
                () -> RelMdCollation.limit(mq, input))  // 使用元数据推断Limit后的排序属性（Limit可能保持或改变排序）
            .replaceIf(RelDistributionTraitDef.INSTANCE,  // 条件替换分布特征
                () -> RelMdDistribution.limit(mq, input));  // 使用元数据推断Limit后的分布属性（Limit可能改变分布）
    return new EnumerableLimit(cluster, traitSet, input, offset, fetch);  // 调用构造方法创建并返回EnumerableLimit实例
  }  // 工厂方法结束

  @Override public EnumerableLimit copy(  // 方法注释：复制此Limit节点，用于优化器重写关系树
      RelTraitSet traitSet,  // 参数：新的关系特征集合
      List<RelNode> newInputs) {  // 参数：新的输入节点列表
    return new EnumerableLimit(  // 创建并返回新的EnumerableLimit实例
        getCluster(),  // 使用当前集群
        traitSet,  // 使用新的特征集
        sole(newInputs),  // 从输入列表中获取唯一的输入节点（SingleRel只有一个输入）
        offset,  // 保持原有的offset
        fetch);  // 保持原有的fetch
  }  // copy方法结束

  @Override public RelWriter explainTerms(RelWriter pw) {  // 方法注释：将此Limit节点的信息写入关系写入器，用于生成可读的解释字符串
    return super.explainTerms(pw)  // 调用父类方法，写入基础信息（如输入、约定等）
        .itemIf("offset", offset, offset != null)  // 如果offset不为null，则写入offset项
        .itemIf("fetch", fetch, fetch != null);  // 如果fetch不为null，则写入fetch项
  }  // explainTerms方法结束

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) {  // 方法注释：实现此Limit节点，生成可执行的Java代码
    final BlockBuilder builder = new BlockBuilder();  // 创建代码块构建器，用于构建Java代码块
    final EnumerableRel child = (EnumerableRel) getInput();  // 获取输入节点并转换为可枚举关系节点
    final Result result = implementor.visitChild(this, 0, child, pref);  // 递归实现子节点，获取子节点的实现结果
    final PhysType physType =  // 创建物理类型，描述输出数据的物理表示
        PhysTypeImpl.of(implementor.getTypeFactory(), getRowType(),  // 使用类型工厂和行类型创建
            result.format);  // 使用子节点的格式

    Expression v = builder.append("child", result.block);  // 将子节点的代码块添加到builder中，并获取表达式
    if (offset != null) {  // 如果设置了offset（需要跳过行数）
      v =  // 更新表达式v，应用skip操作
          builder.append("offset",  // 添加offset操作到builder
              Expressions.call(v, BuiltInMethod.SKIP.method,  // 调用LINQ的skip方法，跳过指定行数
                  getExpression(offset)));  // 将offset表达式转换为Java表达式
    }  // offset处理结束
    if (fetch != null) {  // 如果设置了fetch（需要限制行数）
      v =  // 更新表达式v，应用take操作
          builder.append("fetch",  // 添加fetch操作到builder
              Expressions.call(v, BuiltInMethod.TAKE.method,  // 调用LINQ的take方法，获取指定行数
                  getExpression(fetch)));  // 将fetch表达式转换为Java表达式
    }  // fetch处理结束

    builder.add(Expressions.return_(null, v));  // 将表达式v作为返回值添加到代码块
    return implementor.result(physType, builder.toBlock());  // 返回实现结果，包含物理类型和生成的代码块
  }  // implement方法结束

  static Expression getExpression(RexNode rexNode) {  // 方法注释：将RexNode（行表达式）转换为Java表达式
    if (rexNode instanceof RexDynamicParam) {  // 如果是动态参数（如SQL中的?）
      final RexDynamicParam param = (RexDynamicParam) rexNode;  // 强制转换为动态参数类型
      return Expressions.convert_(  // 返回类型转换表达式
          Expressions.call(DataContext.ROOT,  // 调用DataContext.ROOT的get方法
              BuiltInMethod.DATA_CONTEXT_GET.method,  // 使用get方法获取参数值
              Expressions.constant("?" + param.getIndex())),  // 构造参数名（如"?0", "?1"）
          Integer.class);  // 将获取的值转换为Integer类型
    } else {  // 如果不是动态参数（通常是字面量）
      return Expressions.constant(RexLiteral.intValue(rexNode));  // 直接返回常量表达式，从RexLiteral中提取整数值
    }  // else分支结束
  }  // getExpression方法结束
}  // 类定义结束
