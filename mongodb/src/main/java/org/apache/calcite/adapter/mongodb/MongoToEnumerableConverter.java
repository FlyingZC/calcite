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
package org.apache.calcite.adapter.mongodb;  // MongoDB适配器包，包含MongoDB相关的适配器实现

import org.apache.calcite.adapter.enumerable.EnumerableRel;  // 可枚举关系表达式接口，定义了可转换为Java代码的关系节点
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor;  // 可枚举关系表达式实现器，负责生成Java代码
import org.apache.calcite.adapter.enumerable.JavaRowFormat;  // Java行格式枚举，定义行数据的表示方式（如数组、对象等）
import org.apache.calcite.adapter.enumerable.PhysType;  // 物理类型接口，描述Java层面的数据结构
import org.apache.calcite.adapter.enumerable.PhysTypeImpl;  // 物理类型实现类，提供类型转换和访问功能
import org.apache.calcite.config.CalciteSystemProperty;  // Calcite系统属性配置，用于控制调试等行为
import org.apache.calcite.linq4j.tree.BlockBuilder;  // 代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.Expression;  // 表达式基类，表示Java代码中的表达式
import org.apache.calcite.linq4j.tree.Expressions;  // 表达式工具类，提供创建各种表达式的静态方法
import org.apache.calcite.linq4j.tree.MethodCallExpression;  // 方法调用表达式，表示Java代码中的方法调用
import org.apache.calcite.plan.ConventionTraitDef;  // 约定特征定义，定义关系节点的调用约定（如物理实现方式）
import org.apache.calcite.plan.RelOptCluster;  // 关系优化集群，包含查询优化所需的全局信息
import org.apache.calcite.plan.RelOptCost;  // 关系优化成本，表示查询执行的成本估计
import org.apache.calcite.plan.RelOptPlanner;  // 关系优化规划器，负责查询优化
import org.apache.calcite.plan.RelTraitSet;  // 关系特征集合，包含关系节点的各种特征
import org.apache.calcite.rel.RelNode;  // 关系节点接口，表示关系代数操作
import org.apache.calcite.rel.convert.ConverterImpl;  // 转换器实现基类，用于将一种关系节点转换为另一种
import org.apache.calcite.rel.metadata.RelMetadataQuery;  // 关系元数据查询，提供查询关系节点元数据的接口
import org.apache.calcite.rel.type.RelDataType;  // 关系数据类型，描述关系数据的类型信息
import org.apache.calcite.runtime.Hook;  // 钩子类，用于在特定事件触发时执行自定义逻辑
import org.apache.calcite.util.BuiltInMethod;  // 内置方法枚举，定义常用的Java内置方法
import org.apache.calcite.util.Pair;  // 键值对类，用于存储两个相关联的值
import org.apache.calcite.util.Util;  // 工具类，提供常用的工具方法

import org.checkerframework.checker.nullness.qual.Nullable;  // 可空注解，用于标记可能为null的值

import java.util.AbstractList;  // 抽象列表类，提供列表的基本实现
import java.util.List;  // 列表接口，表示有序的元素集合

/**
 * Relational expression representing a scan of a table in a Mongo data source.
 * 表示MongoDB数据源中表扫描的关系表达式，用于将MongoDB的物理算子转换为可枚举的关系算子
 * 这个类是Calcite适配器模式中的关键组件，负责将MongoDB特有的关系表达式转换为Calcite可执行的Enumerable形式
 * 它实现了EnumerableRel接口，使得MongoDB数据源可以像其他数据源一样参与Calcite的查询优化和执行
 */
public class MongoToEnumerableConverter
    extends ConverterImpl
    implements EnumerableRel {
  protected MongoToEnumerableConverter(
      RelOptCluster cluster,
      RelTraitSet traits,
      RelNode input) {
    super(cluster, ConventionTraitDef.INSTANCE, traits, input);  // 调用父类ConverterImpl的构造函数，初始化集群、特征集和输入节点
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    return new MongoToEnumerableConverter(  // 创建当前节点的副本，用于优化器进行规则转换时的节点复制
        getCluster(), traitSet, sole(inputs));  // 传入集群、新的特征集和唯一的输入节点
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    return super.computeSelfCost(planner, mq).multiplyBy(.1);  // 计算当前节点的执行成本，乘以0.1表示这个转换节点的成本较低，鼓励优化器尽早进行转换
  }

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) {
    // Generates a call to "find" or "aggregate", depending upon whether
    // an aggregate is present.
    // 生成对MongoDB的"find"或"aggregate"方法的调用，取决于是否存在聚合操作
    // 示例1: 简单查询
    //   ((MongoTable) schema.getTable("zips")).find(
    //     "{state: 'CA'}",
    //     "{city: 1, zipcode: 1}")
    // 示例2: 聚合查询
    //   ((MongoTable) schema.getTable("zips")).aggregate(
    //     "{$filter: {state: 'CA'}}",
    //     "{$group: {_id: '$city', c: {$sum: 1}, p: {$sum: "$pop"}}")
    final BlockBuilder list = new BlockBuilder();  // 创建代码块构建器，用于生成Java代码表达式
    final MongoRel.Implementor mongoImplementor =
        new MongoRel.Implementor(getCluster().getRexBuilder());  // 创建MongoDB实现器，用于遍历和生成MongoDB查询
    mongoImplementor.visitChild(0, getInput());  // 访问子节点，收集MongoDB查询操作信息
    final RelDataType rowType = getRowType();  // 获取当前节点的行类型信息
    final PhysType physType =
        PhysTypeImpl.of(  // 创建物理类型对象，描述Java层面的数据结构
            implementor.getTypeFactory(), rowType,  // 使用类型工厂和行类型
            pref.prefer(JavaRowFormat.ARRAY));  // 优先使用数组格式来表示行数据
    final Expression fields =
        list.append("fields",  // 将字段信息表达式添加到代码块
            constantArrayList(  // 创建常量数组列表表达式
                Pair.zip(MongoRules.mongoFieldNames(rowType),  // 将MongoDB字段名与Java类型配对
                    new AbstractList<Class>() {  // 创建抽象列表，提供字段的Java类型信息
                      @Override public Class get(int index) {
                        return physType.fieldClass(index);  // 返回指定索引位置字段的Java类类型
                      }

                      @Override public int size() {
                        return rowType.getFieldCount();  // 返回字段总数
                      }
                    }),
                Pair.class));  // 指定列表元素类型为Pair
    final Expression table =
        list.append("table",  // 将表表达式添加到代码块
            mongoImplementor.table.getExpression(  // 获取MongoDB表的表达式
                MongoTable.MongoQueryable.class));  // 指定返回类型为MongoQueryable
    List<String> opList = mongoImplementor.list.rightList();  // 获取MongoDB操作符列表（右侧列表）
    final Expression ops =
        list.append("ops",  // 将操作符列表表达式添加到代码块
            constantArrayList(opList, String.class));  // 创建字符串常量数组列表
    Expression enumerable =
        list.append("enumerable",  // 将可枚举表达式添加到代码块
            Expressions.call(table,  // 调用MongoDB表的aggregate方法
                MongoMethod.MONGO_QUERYABLE_AGGREGATE.method, fields, ops));  // 传入字段和操作符参数
    if (CalciteSystemProperty.DEBUG.value()) {  // 如果开启了DEBUG模式
      System.out.println("Mongo: " + opList);  // 打印MongoDB操作符列表，用于调试
    }
    Hook.QUERY_PLAN.run(opList);  // 通过钩子机制输出查询计划，方便监控和分析
    list.add(
        Expressions.return_(null, enumerable));  // 添加返回语句，返回可枚举对象
    return implementor.result(physType, list.toBlock());  // 返回实现结果，包含物理类型和生成的代码块
  }

  /** E.g. {@code constantArrayList("x", "y")} returns
   * "Arrays.asList('x', 'y')".
   * 例如：{@code constantArrayList("x", "y")} 返回 "Arrays.asList('x', 'y')"
   * 这个方法用于生成Java代码中的Arrays.asList调用表达式，用于创建常量列表
   *
   * @param values List of values  值列表，要转换为常量表达式的值
   * @param clazz Type of values  值的类型，用于确定数组初始化的类型
   * @return expression  返回方法调用表达式，表示Arrays.asList方法调用
   */
  private static <T> MethodCallExpression constantArrayList(List<T> values,
      Class clazz) {
    return Expressions.call(  // 创建方法调用表达式
        BuiltInMethod.ARRAYS_AS_LIST.method,  // 指定调用Arrays.asList方法
        Expressions.newArrayInit(clazz, constantList(values)));  // 创建数组初始化表达式，传入类型和常量值列表
  }

  /** E.g. {@code constantList("x", "y")} returns
   * {@code {ConstantExpression("x"), ConstantExpression("y")}}.
   * 例如：{@code constantList("x", "y")} 返回 {@code {ConstantExpression("x"), ConstantExpression("y")}}
   * 这个方法将值列表转换为常量表达式列表，用于代码生成
   */
  private static <T> List<Expression> constantList(List<T> values) {
    return Util.transform(values, Expressions::constant);  // 使用工具类将每个值转换为常量表达式
  }
}
