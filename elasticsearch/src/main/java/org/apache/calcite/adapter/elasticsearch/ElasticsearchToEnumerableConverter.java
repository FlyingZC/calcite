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
 * Apache许可证2.0版本，允许自由使用、修改和分发本代码
 */
// 包声明：定义当前类所属的包为org.apache.calcite.adapter.elasticsearch
// 这是Calcite项目中Elasticsearch适配器的包，包含所有与Elasticsearch相关的适配器类
package org.apache.calcite.adapter.elasticsearch;

// 导入EnumerableRel接口：可枚举关系接口，表示可以被转换为可执行Java代码的关系节点
import org.apache.calcite.adapter.enumerable.EnumerableRel;
// 导入EnumerableRelImplementor类：可枚举关系实现器，负责生成Java代码并管理实现上下文
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor;
// 导入JavaRowFormat枚举：Java行格式枚举，定义了Java代码中表示行数据的格式（如数组、对象等）
import org.apache.calcite.adapter.enumerable.JavaRowFormat;
// 导入PhysType接口：物理类型接口，描述Java代码中的数据类型和格式
import org.apache.calcite.adapter.enumerable.PhysType;
// 导入PhysTypeImpl类：物理类型接口的实现类，提供具体的物理类型实现
import org.apache.calcite.adapter.enumerable.PhysTypeImpl;
// 导入BlockBuilder类：代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.BlockBuilder;
// 导入Expression类：表达式类，表示Java代码中的表达式
import org.apache.calcite.linq4j.tree.Expression;
// 导入Expressions类：表达式工具类，提供创建各种表达式的静态方法
import org.apache.calcite.linq4j.tree.Expressions;
// 导入MethodCallExpression类：方法调用表达式，表示Java代码中的方法调用
import org.apache.calcite.linq4j.tree.MethodCallExpression;
// 导入ConventionTraitDef类：约定特征定义，定义了关系节点的约定特征（如物理实现方式）
import org.apache.calcite.plan.ConventionTraitDef;
// 导入RelOptCluster类：关系优化集群，包含共享的优化上下文信息（如类型工厂、表达式构建器等）
import org.apache.calcite.plan.RelOptCluster;
// 导入RelOptCost接口：关系优化成本接口，表示执行计划的成本
import org.apache.calcite.plan.RelOptCost;
// 导入RelOptPlanner接口：关系优化器接口，负责优化查询计划
import org.apache.calcite.plan.RelOptPlanner;
// 导入RelTraitSet类：关系特征集合，定义了关系节点的物理属性（如约定、排序、分布等）
import org.apache.calcite.plan.RelTraitSet;
// 导入RelNode接口：关系节点接口，表示查询计划中的一个节点
import org.apache.calcite.rel.RelNode;
// 导入ConverterImpl类：转换器抽象基类，用于将一种约定转换为另一种约定的节点
import org.apache.calcite.rel.convert.ConverterImpl;
// 导入RelMetadataQuery类：关系元数据查询类，用于查询关系节点的元数据信息
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// 导入RelDataType接口：关系数据类型接口，描述关系数据的类型信息
import org.apache.calcite.rel.type.RelDataType;
// 导入BuiltInMethod枚举：内置方法枚举，定义了常用的Java方法
import org.apache.calcite.util.BuiltInMethod;
// 导入Pair类：键值对类，用于存储两个关联的值
import org.apache.calcite.util.Pair;

// 导入Nullable注解：可空注解，表示返回值可能为null（来自CheckerFramework框架）
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入AbstractList类：抽象列表类，Java集合框架的基础类
import java.util.AbstractList;
// 导入List接口：列表接口，表示有序的元素集合
import java.util.List;
// 导入Collectors类：收集器工具类，提供Stream的收集操作
import java.util.stream.Collectors;

// 静态导入requireNonNull方法：非空检查方法，如果值为null则抛出NullPointerException
import static java.util.Objects.requireNonNull;

/**
 * Relational expression representing a scan of a table in an Elasticsearch data source.
 * // 表示从Elasticsearch数据源扫描表的关联表达式，负责将Elasticsearch的关联表达式转换为可枚举的关联表达式
 * // 这是Calcite适配器模式中的关键转换器，用于将逻辑计划转换为可执行的物理计划
 * // 实现了EnumerableRel接口，表示该节点可以被枚举成Java代码执行
 * // 继承自ConverterImpl，表明这是一个转换器节点，用于将一种约定转换为另一种约定
 */
public class ElasticsearchToEnumerableConverter extends ConverterImpl implements EnumerableRel {
  // 构造方法：创建Elasticsearch到可枚举转换器的实例
  // 参数cluster：关系优化集群，包含共享的优化上下文信息
  // 参数traits：关系特征集合，定义了该节点的物理属性（如约定、排序等）
  // 参数input：输入的关系节点，即需要被转换的Elasticsearch关系表达式
  ElasticsearchToEnumerableConverter(RelOptCluster cluster, RelTraitSet traits,
      RelNode input) {
    // 调用父类ConverterImpl的构造方法，传入集群、约定特征定义和特征集合以及输入节点
    // ConventionTraitDef.INSTANCE表示使用标准的约定特征定义
    super(cluster, ConventionTraitDef.INSTANCE, traits, input);
  }

  // 重写copy方法：创建当前节点的副本，用于优化器在转换过程中创建新的节点实例
  // 参数traitSet：新的关系特征集合，可能包含不同的物理属性
  // 参数inputs：输入节点列表，用于替换当前节点的输入
  // 返回值：返回一个新的ElasticsearchToEnumerableConverter实例，包含新的特征集和输入
  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    // 创建并返回一个新的转换器实例，使用当前节点的集群、新的特征集和唯一的输入节点
    // sole(inputs)方法确保输入列表中只有一个元素，因为该转换器只接受单个输入
    return new ElasticsearchToEnumerableConverter(getCluster(), traitSet, sole(inputs));
  }

  // 重写computeSelfCost方法：计算当前节点的执行成本，用于优化器选择最优的执行计划
  // 参数planner：关系优化器，用于访问优化器相关的信息
  // 参数mq：关系元数据查询对象，用于获取元数据信息
  // 返回值：返回计算出的成本对象，可能为null（由于注解@Nullable）
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    // 调用父类的computeSelfCost方法计算基础成本，并确保结果不为null
    // requireNonNull是一个工具方法，如果值为null会抛出NullPointerException
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq));
    // 将成本乘以0.1，表示这个转换节点的成本较低
    // 这是因为Elasticsearch查询本身有成本，转换只是适配层，不应该增加太多成本
    // 这样可以鼓励优化器优先选择使用Elasticsearch原生能力的计划
    return cost.multiplyBy(.1);
  }

  // 重写implement方法：实现该关系节点为可执行的Java代码，这是Calcite代码生成的核心方法
  // 参数relImplementor：可枚举关系实现器，用于生成Java代码和管理实现上下文
  // 参数prefer：首选格式，表示生成代码时首选的行格式（如数组、对象等）
  // 返回值：返回Result对象，包含生成的代码块和物理类型信息
  @Override public Result implement(EnumerableRelImplementor relImplementor, Prefer prefer) {
    // 创建代码块构建器，用于构建Java代码块
    final BlockBuilder block = new BlockBuilder();
    // 创建Elasticsearch关系的实现器，用于收集和生成Elasticsearch查询的相关信息
    final ElasticsearchRel.Implementor implementor = new ElasticsearchRel.Implementor();
    // 访问子节点（输入节点），将输入节点的信息收集到implementor中
    // 参数0表示访问第一个子节点，getInput()获取当前节点的输入
    implementor.visitChild(0, getInput());

    // 获取当前节点的行类型，描述输出行的结构（字段名、类型等）
    final RelDataType rowType = getRowType();
    // 创建物理类型对象，用于描述Java代码中的数据类型和格式
    // PhysTypeImpl.of根据类型工厂、行类型和首选格式创建物理类型
    // prefer.prefer(JavaRowFormat.ARRAY)表示首选数组格式存储行数据
    final PhysType physType =
        PhysTypeImpl.of(relImplementor.getTypeFactory(),
            rowType, prefer.prefer(JavaRowFormat.ARRAY));
    // 创建字段表达式，描述查询结果的字段信息
    // block.append将表达式添加到代码块中，并返回表达式引用
    // constantArrayList创建一个常量列表表达式
    // Pair.zip将字段名和字段类配对成Pair列表
    // ElasticsearchRules.elasticsearchFieldNames(rowType)获取Elasticsearch字段名
    // AbstractList匿名类提供每个字段的Java类类型和字段数量
    final Expression fields =
        block.append("fields",
            constantArrayList(
                Pair.zip(ElasticsearchRules.elasticsearchFieldNames(rowType),
                    new AbstractList<Class>() {
                      // 获取指定索引位置的字段的Java类类型
                      @Override public Class get(int index) {
                        return physType.fieldClass(index);
                      }

                      // 获取字段总数
                      @Override public int size() {
                        return rowType.getFieldCount();
                      }
                    }),
                Pair.class));
    // 创建表表达式，表示要查询的Elasticsearch表
    // implementor.table是ElasticsearchTable对象
    // getExpression方法将表转换为表达式
    // ElasticsearchTable.ElasticsearchQueryable.class是可查询接口的类对象
    final Expression table =
        block.append("table",
            requireNonNull(
                implementor.table.getExpression(
                    ElasticsearchTable.ElasticsearchQueryable.class)));
    // 创建操作表达式，表示Elasticsearch查询的过滤条件
    // implementor.list包含了所有过滤条件的列表
    final Expression ops = block.append("ops", Expressions.constant(implementor.list));
    // 创建排序表达式，表示查询结果的排序规则
    // implementor.sort包含了排序字段和排序方向的列表
    final Expression sort = block.append("sort", constantArrayList(implementor.sort, Pair.class));
    // 创建空值排序表达式，表示空值在排序中的位置
    // implementor.nullsSort包含了空值排序规则的列表
    final Expression nullsSort =
        block.append("nullsSort", constantArrayList(implementor.nullsSort, Pair.class));
    // 创建分组表达式，表示GROUP BY子句的字段列表
    // implementor.groupBy包含了分组字段的列表
    final Expression groupBy = block.append("groupBy", Expressions.constant(implementor.groupBy));
    // 创建聚合表达式，表示聚合函数（如SUM、COUNT等）的列表
    // implementor.aggregations包含了聚合函数名和表达式对的列表
    final Expression aggregations =
        block.append("aggregations",
            constantArrayList(implementor.aggregations, Pair.class));

    // 创建映射表达式，表示表达式到项的映射关系
    // implementor.expressionItemMap包含了表达式到映射项的映射字典
    final Expression mappings =
        block.append("mappings",
            Expressions.constant(implementor.expressionItemMap));

    // 创建偏移量表达式，表示LIMIT子句的OFFSET值
    // implementor.offset是跳过的行数
    final Expression offset = block.append("offset", Expressions.constant(implementor.offset));
    // 创建获取行数表达式，表示LIMIT子句的FETCH值
    // implementor.fetch是返回的最大行数
    final Expression fetch = block.append("fetch", Expressions.constant(implementor.fetch));

    // 创建可枚举表达式，这是最终生成的查询表达式
    // Expressions.call调用ElasticsearchQueryable的find方法
    // 参数依次是：表对象、操作列表、字段列表、排序、空值排序、分组、聚合、映射、偏移量、获取行数
    // 这个调用会生成实际的Elasticsearch查询代码
    Expression enumerable =
        block.append("enumerable",
            Expressions.call(table,
                ElasticsearchMethod.ELASTICSEARCH_QUERYABLE_FIND.method, ops,
                fields, sort, nullsSort, groupBy, aggregations, mappings, offset, fetch));
    // 添加返回语句，将可枚举对象作为代码块的返回值
    block.add(Expressions.return_(null, enumerable));
    // 返回实现结果，包含物理类型和生成的代码块
    // relImplementor.result方法创建Result对象，用于后续代码生成
    return relImplementor.result(physType, block.toBlock());
  }

  // 辅助方法：创建一个常量数组列表的表达式，用于生成Java代码中的Arrays.asList调用
  // 例如：constantArrayList("x", "y") 会生成 "Arrays.asList('x', 'y')" 这样的表达式
  // 参数values：值列表，要包含在数组中的元素集合
  // 参数clazz：运行时类对象，表示列表中每个元素的类型
  // 参数<T>：列表元素的泛型类型
  // 返回值：返回方法调用表达式，用于创建列表
  /** E.g. {@code constantArrayList("x", "y")} returns
   * "Arrays.asList('x', 'y')".
   *
   * @param values list of values
   * @param clazz runtime class representing each element in the list
   * @param <T> type of elements in the list
   * @return method call which creates a list
   */
  private static <T> MethodCallExpression constantArrayList(List<T> values, Class clazz) {
    // 创建Arrays.asList方法调用表达式
    // BuiltInMethod.ARRAYS_AS_LIST.method表示Arrays类的asList方法
    // Expressions.newArrayInit创建数组初始化表达式，使用指定的类型和常量值列表
    // constantList(values)将值列表转换为常量表达式列表
    return Expressions.call(BuiltInMethod.ARRAYS_AS_LIST.method,
        Expressions.newArrayInit(clazz, constantList(values)));
  }

  // 辅助方法：将值列表转换为常量表达式列表，用于生成Java代码中的常量值
  // 例如：constantList("x", "y") 会返回 {ConstantExpression("x"), ConstantExpression("y")}
  // 参数values：元素列表，要转换为常量表达式的值集合
  // 参数<T>：列表元素的泛型类型
  // 返回值：返回常量表达式列表，每个元素都是一个常量表达式对象
  /** E.g. {@code constantList("x", "y")} returns
   * {@code {ConstantExpression("x"), ConstantExpression("y")}}.
   *
   * @param values list of elements
   * @param <T> type of elements inside this list
   * @return list of constant expressions
   */
  private static <T> List<Expression> constantList(List<T> values) {
    // 使用Java Stream API将值列表转换为常量表达式列表
    // values.stream()创建流
    // .map(Expressions::constant)将每个值映射为常量表达式
    // .collect(Collectors.toList())将流收集为列表
    return values.stream().map(Expressions::constant).collect(Collectors.toList());
  }
}
