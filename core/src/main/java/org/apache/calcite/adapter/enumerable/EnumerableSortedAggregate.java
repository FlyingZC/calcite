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
package org.apache.calcite.adapter.enumerable;  // 声明包名，该类属于org.apache.calcite.adapter.enumerable包，这是Calcite的可枚举适配器包

import org.apache.calcite.adapter.enumerable.impl.AggResultContextImpl;  // 导入聚合结果上下文实现类，用于处理聚合结果
import org.apache.calcite.adapter.java.JavaTypeFactory;  // 导入Java类型工厂，用于创建Java类型
import org.apache.calcite.linq4j.Ord;  // 导入有序包装类，用于为元素添加索引
import org.apache.calcite.linq4j.function.Function0;  // 导入无参数函数接口，用于初始化器
import org.apache.calcite.linq4j.function.Function2;  // 导入双参数函数接口，用于累加器和结果选择器
import org.apache.calcite.linq4j.tree.BlockBuilder;  // 导入代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.Expression;  // 导入表达式类，用于表示Java表达式
import org.apache.calcite.linq4j.tree.Expressions;  // 导入表达式工具类，用于创建各种表达式
import org.apache.calcite.linq4j.tree.ParameterExpression;  // 导入参数表达式类，用于表示方法参数
import org.apache.calcite.plan.RelOptCluster;  // 导入关系优化集群，包含优化器上下文
import org.apache.calcite.plan.RelTraitSet;  // 导入关系特征集合，定义关系的物理属性
import org.apache.calcite.rel.RelCollation;  // 导入关系排序属性，描述数据的排序方式
import org.apache.calcite.rel.RelCollations;  // 导入关系排序工具类，提供排序相关操作
import org.apache.calcite.rel.RelFieldCollation;  // 导入字段排序描述，描述单个字段的排序方向
import org.apache.calcite.rel.RelNode;  // 导入关系节点接口，所有关系算子的基类
import org.apache.calcite.rel.core.Aggregate;  // 导入聚合算子基类，定义聚合操作的通用逻辑
import org.apache.calcite.rel.core.AggregateCall;  // 导入聚合调用，描述具体的聚合函数调用
import org.apache.calcite.rex.RexUtil;  // 导入Rex表达式工具类，提供表达式操作方法
import org.apache.calcite.util.BuiltInMethod;  // 导入内置方法枚举，包含Calcite内置方法
import org.apache.calcite.util.ImmutableBitSet;  // 导入不可变位集合，用于高效表示字段集合
import org.apache.calcite.util.Pair;  // 导入键值对类，用于存储两个相关对象
import org.apache.calcite.util.Util;  // 导入工具类，提供通用辅助方法
import org.apache.calcite.util.mapping.Mappings;  // 导入映射工具类，用于字段映射转换

import com.google.common.collect.ImmutableList;  // 导入Google不可变列表，提供不可变列表实现

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入可空注解，用于标记可能为null的参数

import java.lang.reflect.Type;  // 导入Java类型类，表示Java类型
import java.util.ArrayList;  // 导入数组列表，可动态调整大小的数组实现
import java.util.List;  // 导入列表接口，定义有序集合

import static java.util.Objects.requireNonNull;  // 静态导入requireNonNull方法，用于非空检查

/** Sort based physical implementation of {@link Aggregate} in
 * {@link EnumerableConvention enumerable calling convention}. */
// 基于排序的聚合算子物理实现，使用可枚举调用约定
// 这个实现利用输入数据的排序特性，通过一次扫描完成分组聚合，提高性能
// 相比于普通的EnumerableAggregate，这个实现要求输入数据按照分组键排序，从而可以使用更高效的分组算法
public class EnumerableSortedAggregate extends EnumerableAggregateBase implements EnumerableRel {  // 定义类，继承自EnumerableAggregateBase基类，实现EnumerableRel接口
  public EnumerableSortedAggregate(  // 构造方法：创建一个基于排序的聚合算子实例
      RelOptCluster cluster,  // 参数：关系优化集群，包含优化器上下文和元数据
      RelTraitSet traitSet,  // 参数：关系特征集合，定义该算子的物理属性（如排序、约定等）
      RelNode input,  // 参数：输入关系节点，即要聚合的数据源
      ImmutableBitSet groupSet,  // 参数：分组字段集合，使用位集合表示哪些字段用于分组
      @Nullable List<ImmutableBitSet> groupSets,  // 参数：多级分组集合，用于GROUPING SETS、ROLLUP、CUBE等高级分组，可能为null
      List<AggregateCall> aggCalls) {  // 参数：聚合函数调用列表，描述每个聚合函数的调用方式
    super(cluster, traitSet, ImmutableList.of(), input, groupSet, groupSets, aggCalls);  // 调用父类构造方法，初始化聚合算子的基本属性，Indicators为空列表表示不使用indicators
    assert getConvention() instanceof EnumerableConvention;  // 断言：确保调用约定是EnumerableConvention类型，这是可枚举约定
  }  // 构造方法结束

  @Override public EnumerableSortedAggregate copy(RelTraitSet traitSet, RelNode input,  // 重写copy方法：创建该算子的副本，用于优化过程中的变换
      ImmutableBitSet groupSet,  // 参数：新的分组字段集合
      @Nullable List<ImmutableBitSet> groupSets,  // 参数：新的多级分组集合
      List<AggregateCall> aggCalls) {  // 参数：新的聚合函数调用列表
    return new EnumerableSortedAggregate(getCluster(), traitSet, input,  // 返回一个新的EnumerableSortedAggregate实例，保持相同的集群，使用新的特征集合和输入
        groupSet, groupSets, aggCalls);  // 传递新的分组集合和聚合调用列表
  }  // copy方法结束

  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraits(  // 重写passThroughTraits方法：传递特征到子节点，用于优化器推导子节点需要的物理属性
      final RelTraitSet required) {  // 参数：父节点要求的特征集合，包含排序等物理属性要求
    if (!isSimple(this)) {  // 如果当前聚合算子不是简单聚合（包含GROUPING SETS等复杂分组）
      return null;  // 返回null表示无法传递特征，因为复杂分组难以利用排序特性
    }  // if判断结束

    RelTraitSet inputTraits = getInput().getTraitSet();  // 获取输入节点的当前特征集合
    RelCollation collation =  // 获取要求的排序属性
        requireNonNull(required.getCollation(),  // 从required中获取排序属性，如果为null则抛出异常
            () -> "collation trait is null, required traits are " + required);  // 错误信息：排序属性为null
    ImmutableBitSet requiredKeys = ImmutableBitSet.of(RelCollations.ordinals(collation));  // 从排序属性中提取排序字段的索引，转换为不可变位集合
    ImmutableBitSet groupKeys = ImmutableBitSet.range(groupSet.cardinality());  // 生成分组键的位集合，范围从0到分组字段数量-1

    Mappings.TargetMapping mapping =  // 创建从分组键到输入字段的映射
        Mappings.source(groupSet.toList(), input.getRowType().getFieldCount());  // 将分组字段索引映射到输入行的字段索引

    if (requiredKeys.equals(groupKeys)) {  // 如果要求的排序字段完全匹配分组字段（例如：GROUP BY a,b,c ORDER BY a,b,c）
      RelCollation inputCollation = RexUtil.apply(mapping, collation);  // 将排序属性应用到输入字段的映射上，得到输入节点需要的排序
      return Pair.of(required, ImmutableList.of(inputTraits.replace(inputCollation)));  // 返回对：当前节点保持required特征，输入节点需要inputCollation排序
    } else if (groupKeys.contains(requiredKeys)) {  // 如果分组字段包含要求的排序字段（例如：GROUP BY a,b,c ORDER BY c）
      // group by a,b,c order by c,b  // 注释：示例场景，分组字段多于排序字段
      List<RelFieldCollation> list = new ArrayList<>(collation.getFieldCollations());  // 复制排序字段列表
      groupKeys.except(requiredKeys).forEachInt(k ->  // 遍历分组键中不在排序键中的字段
          list.add(new RelFieldCollation(k)));  // 将这些字段添加到排序列表末尾，得到完整的分组排序
      RelCollation aggCollation = RelCollations.of(list);  // 从字段排序列表创建完整的排序属性
      RelCollation inputCollation = RexUtil.apply(mapping, aggCollation);  // 将排序属性应用到输入字段映射
      return Pair.of(traitSet.replace(aggCollation),  // 返回对：当前节点使用完整的分组排序，输入节点需要对应的排序
          ImmutableList.of(inputTraits.replace(inputCollation)));  // 输入节点的排序特征
    }  // else if判断结束

    // Group keys doesn't contain all the required keys, e.g.  // 注释：分组键不包含所有要求的键
    // group by a,b order by a,b,c  // 注释：示例场景，排序字段包含非分组字段
    // nothing we can do to propagate traits to child nodes.  // 注释：无法将特征传递给子节点
    return null;  // 返回null表示无法利用排序特性，需要子节点提供完整排序
  }  // passThroughTraits方法结束

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) {  // 重写implement方法：生成Java代码实现，将关系算子转换为可执行的Java代码
    if (!Aggregate.isSimple(this)) {  // 如果当前聚合不是简单聚合
      throw Util.needToImplement("EnumerableSortedAggregate");  // 抛出异常，表示需要实现复杂聚合支持
    }  // if判断结束

    final JavaTypeFactory typeFactory = implementor.getTypeFactory();  // 获取Java类型工厂，用于创建Java类型
    final BlockBuilder builder = new BlockBuilder();  // 创建代码块构建器，用于构建生成的Java代码
    final EnumerableRel child = (EnumerableRel) getInput();  // 获取输入节点，转换为EnumerableRel类型
    final Result result = implementor.visitChild(this, 0, child, pref);  // 递归访问子节点，生成子节点的实现代码
    Expression childExp =  // 创建子节点表达式
        builder.append(  // 将子节点的代码块添加到builder中
            "child",  // 变量名：child
            result.block);  // 子节点的代码块

    final PhysType physType =  // 创建输出行的物理类型描述
        PhysTypeImpl.of(  // 使用PhysTypeImpl创建物理类型
            typeFactory, getRowType(), pref.preferCustom());  // 传入类型工厂、行类型和格式偏好

    final PhysType inputPhysType = result.physType;  // 获取输入行的物理类型描述

    ParameterExpression parameter =  // 创建参数表达式，表示输入行
        Expressions.parameter(inputPhysType.getJavaRowType(), "a0");  // 参数类型为输入行的Java类型，参数名为a0

    final PhysType keyPhysType =  // 创建分组键的物理类型描述
        inputPhysType.project(groupSet.asList(), getGroupType() != Group.SIMPLE,  // 投影分组字段，如果不是简单分组则特殊处理
            JavaRowFormat.LIST);  // 使用LIST格式存储分组键
    final int groupCount = getGroupCount();  // 获取分组字段的数量

    final List<AggImpState> aggs = new ArrayList<>(aggCalls.size());  // 创建聚合实现状态列表，大小等于聚合调用数量
    for (Ord<AggregateCall> call : Ord.zip(aggCalls)) {  // 遍历所有聚合调用，使用Ord包装以获得索引
      aggs.add(new AggImpState(call.i, call.e, false));  // 为每个聚合调用创建实现状态，false表示不使用filter
    }  // for循环结束

    // Function0<Object[]> accumulatorInitializer =  // 注释：累加器初始化器的示例代码
    //     new Function0<Object[]>() {  // 注释：无参数函数，返回累加器数组
    //         public Object[] apply() {  // 注释：应用方法
    //             return new Object[] {0, 0};  // 注释：返回初始化的累加器数组
    //         }  // 注释：apply方法结束
    //     };  // 注释：lambda表达式结束
    final List<Expression> initExpressions = new ArrayList<>();  // 创建初始化表达式列表，用于存储累加器的初始化代码
    final BlockBuilder initBlock = new BlockBuilder();  // 创建初始化代码块构建器

    final List<Type> aggStateTypes =  // 创建聚合状态类型列表
        createAggStateTypes(initExpressions, initBlock, aggs, typeFactory);  // 根据聚合函数生成状态类型和初始化表达式

    final PhysType accPhysType =  // 创建累加器类型的物理类型描述
        PhysTypeImpl.of(typeFactory,  // 使用类型工厂
            typeFactory.createSyntheticType(aggStateTypes));  // 创建包含所有聚合状态的合成类型

    declareParentAccumulator(initExpressions, initBlock, accPhysType);  // 声明父类累加器变量，添加到初始化代码块

    final Expression accumulatorInitializer =  // 创建累加器初始化器表达式
        builder.append("accumulatorInitializer",  // 添加到builder，变量名为accumulatorInitializer
            Expressions.lambda(  // 创建lambda表达式
                Function0.class,  // 函数接口类型为Function0（无参数）
                initBlock.toBlock()));  // lambda的代码体是初始化代码块

    // Function2<Object[], Employee, Object[]> accumulatorAdder =  // 注释：累加器添加器的示例代码
    //     new Function2<Object[], Employee, Object[]>() {  // 注释：双参数函数，接受累加器和输入行，返回更新后的累加器
    //         public Object[] apply(Object[] acc, Employee in) {  // 注释：应用方法
    //              acc[0] = ((Integer) acc[0]) + 1;  // 注释：更新计数累加器
    //              acc[1] = ((Integer) acc[1]) + in.salary;  // 注释：更新工资总和累加器
    //             return acc;  // 注释：返回更新后的累加器
    //         }  // 注释：apply方法结束
    //     };  // 注释：lambda表达式结束
    final ParameterExpression inParameter =  // 创建输入参数表达式，表示当前处理的行
        Expressions.parameter(inputPhysType.getJavaRowType(), "in");  // 参数类型为输入行的Java类型，参数名为in
    final ParameterExpression acc_ =  // 创建累加器参数表达式，表示累加器对象
        Expressions.parameter(accPhysType.getJavaRowType(), "acc");  // 参数类型为累加器的Java类型，参数名为acc

    createAccumulatorAdders(  // 创建累加器添加器的代码
        inParameter, aggs, accPhysType, acc_, inputPhysType, builder, implementor, typeFactory);  // 传递所有需要的参数和上下文

    final ParameterExpression lambdaFactory =  // 创建lambda工厂参数表达式
        Expressions.parameter(AggregateLambdaFactory.class,  // 参数类型为AggregateLambdaFactory（聚合lambda工厂）
            builder.newName("lambdaFactory"));  // 参数名为lambdaFactory，使用builder生成的唯一名称

    implementLambdaFactory(builder, inputPhysType, aggs, accumulatorInitializer,  // 实现lambda工厂，创建包含初始化器和添加器的工厂对象
        false, lambdaFactory);  // false表示不使用filter参数，lambdaFactory用于接收工厂表达式

    final BlockBuilder resultBlock = new BlockBuilder();  // 创建结果代码块构建器，用于构建结果选择器的代码
    final List<Expression> results = Expressions.list();  // 创建结果表达式列表，用于存储输出行的所有字段
    final ParameterExpression key_;  // 声明分组键参数表达式
    final Type keyType = keyPhysType.getJavaRowType();  // 获取分组键的Java类型
    key_ = Expressions.parameter(keyType, "key");  // 创建分组键参数，参数名为key
    for (int j = 0; j < groupCount; j++) {  // 遍历所有分组字段
      final Expression ref = keyPhysType.fieldReference(key_, j);  // 创建对分组键第j个字段的引用
      results.add(ref);  // 将字段引用添加到结果列表
    }  // for循环结束

    for (final AggImpState agg : aggs) {  // 遍历所有聚合实现状态
      results.add(  // 将聚合结果添加到结果列表
          agg.implementor.implementResult(  // 调用聚合实现器的结果实现方法
              requireNonNull(agg.context, () -> "agg.context is null for " + agg),  // 获取聚合上下文，非空检查
              new AggResultContextImpl(resultBlock, agg.call,  // 创建聚合结果上下文，包含结果代码块、聚合调用、状态
                  requireNonNull(agg.state, () -> "agg.state is null for " + agg),  // 获取聚合状态，非空检查
                  key_,  // 分组键参数
                  keyPhysType)));  // 分组键的物理类型
    }  // for循环结束
    resultBlock.add(physType.record(results));  // 将结果列表转换为记录表达式，添加到结果代码块

    final Expression keySelector_ =  // 创建键选择器表达式，用于从输入行提取分组键
        builder.append("keySelector",  // 添加到builder，变量名为keySelector
            inputPhysType.generateSelector(parameter, groupSet.asList(),  // 生成选择器lambda，从输入参数中提取分组字段
                keyPhysType.getFormat()));  // 使用分组键的格式
    // Generate the appropriate key Comparator. In the case of NULL values  // 注释：生成适当的键比较器
    // in group keys, the comparator must be able to support NULL values by giving a  // 注释：在分组键中存在NULL值的情况下
    // consistent sort ordering.  // 注释：比较器必须能够支持NULL值，提供一致的排序顺序
    final Expression comparator =  // 创建比较器表达式，用于比较分组键
        keyPhysType.generateComparator(  // 生成分组键的比较器
            requireNonNull(getTraitSet().getCollation(),  // 获取当前节点的排序属性，非空检查
                () -> "getTraitSet().getCollation() is null; traits are "  // 错误信息：排序属性为null
                    + getTraitSet()));  // 输出当前特征集合

    final Expression resultSelector_ =  // 创建结果选择器表达式，用于从分组键和累加器生成最终结果
        builder.append("resultSelector",  // 添加到builder，变量名为resultSelector
            Expressions.lambda(Function2.class, resultBlock.toBlock(), key_,  // 创建lambda表达式，接受分组键和累加器，执行结果代码块
                acc_));  // 参数包括分组键和累加器

    builder.add(  // 添加返回语句到builder
        Expressions.return_(null,  // 创建return表达式，返回值为null
            Expressions.call(childExp,  // 调用子节点的sortedGroupBy方法
                BuiltInMethod.SORTED_GROUP_BY.method,  // 使用内置的SORTED_GROUP_BY方法
                Expressions.list(keySelector_,  // 参数1：键选择器，用于提取分组键
                    Expressions.call(lambdaFactory,  // 参数2：从lambda工厂获取累加器初始化器
                        BuiltInMethod.AGG_LAMBDA_FACTORY_ACC_INITIALIZER.method),  // 调用ACC_INITIALIZER方法
                    Expressions.call(lambdaFactory,  // 参数3：从lambda工厂获取累加器添加器
                        BuiltInMethod.AGG_LAMBDA_FACTORY_ACC_ADDER.method),  // 调用ACC_ADDER方法
                    Expressions.call(lambdaFactory,  // 参数4：从lambda工厂获取结果选择器，并传入自定义结果选择器
                        BuiltInMethod.AGG_LAMBDA_FACTORY_ACC_RESULT_SELECTOR.method,  // 调用ACC_RESULT_SELECTOR方法
                        resultSelector_),  // 传入自定义的结果选择器
                    comparator))));  // 参数5：比较器，用于比较分组键

    return implementor.result(physType, builder.toBlock());  // 返回实现结果，包含输出物理类型和生成的代码块
  }  // implement方法结束
}  // 类定义结束
