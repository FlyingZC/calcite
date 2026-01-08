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
package org.apache.calcite.prepare;  // 声明包名,该类属于org.apache.calcite.prepare包,用于SQL查询准备和转换

import org.apache.calcite.adapter.java.JavaTypeFactory;  // 导入Java类型工厂,用于处理Java类型与Calcite类型的映射
import org.apache.calcite.linq4j.Queryable;  // 导入Queryable接口,表示可查询的LINQ数据源
import org.apache.calcite.linq4j.tree.BlockStatement;  // 导入块语句,表示代码块表达式
import org.apache.calcite.linq4j.tree.Blocks;  // 导入Blocks工具类,用于处理表达式块
import org.apache.calcite.linq4j.tree.ConstantExpression;  // 导入常量表达式,表示常量值
import org.apache.calcite.linq4j.tree.Expression;  // 导入表达式基类,所有LINQ表达式的基类
import org.apache.calcite.linq4j.tree.FunctionExpression;  // 导入函数表达式,表示lambda函数
import org.apache.calcite.linq4j.tree.MethodCallExpression;  // 导入方法调用表达式,表示方法调用
import org.apache.calcite.linq4j.tree.NewExpression;  // 导入新建对象表达式,表示构造对象
import org.apache.calcite.linq4j.tree.ParameterExpression;  // 导入参数表达式,表示lambda参数
import org.apache.calcite.linq4j.tree.Types;  // 导入Types工具类,用于类型操作
import org.apache.calcite.plan.RelOptCluster;  // 导入关系优化集群,包含RexBuilder等优化工具
import org.apache.calcite.plan.RelOptTable;  // 导入关系优化表,表示表及其元数据
import org.apache.calcite.plan.ViewExpanders;  // 导入视图展开器,用于处理视图展开
import org.apache.calcite.rel.RelNode;  // 导入关系节点,所有关系代数节点的基类
import org.apache.calcite.rel.logical.LogicalFilter;  // 导入逻辑过滤节点,表示WHERE过滤操作
import org.apache.calcite.rel.logical.LogicalProject;  // 导入逻辑投影节点,表示SELECT投影操作
import org.apache.calcite.rel.logical.LogicalTableScan;  // 导入逻辑表扫描节点,表示表扫描操作
import org.apache.calcite.rex.RexBuilder;  // 导入Rex表达式构建器,用于构建行表达式
import org.apache.calcite.rex.RexNode;  // 导入行表达式节点,表示关系表达式
import org.apache.calcite.util.BuiltInMethod;  // 导入内置方法枚举,定义LINQ内置方法

import com.google.common.collect.ImmutableList;  // 导入不可变列表,用于创建不可修改的列表
import com.google.common.collect.ImmutableSet;  // 导入不可变集合,用于创建不可修改的集合

import java.lang.reflect.Type;  // 导入Type类,表示Java类型
import java.util.ArrayList;  // 导入ArrayList动态数组类
import java.util.Collections;  // 导入Collections集合工具类
import java.util.List;  // 导入List接口

import static java.util.Objects.requireNonNull;  // 导入requireNonNull静态方法,用于空值检查

/**
 * Translates a tree of linq4j {@link Queryable} nodes to a tree of  // 类注释:将linq4j的Queryable节点树翻译为RelNode关系代数节点树
 * {@link RelNode} planner nodes.  // 转换为关系代数规划器节点
 *
 * @see QueryableRelBuilder  // 参考QueryableRelBuilder,用于构建关系代数节点
 */
class LixToRelTranslator {  // 类定义:LixToRelTranslator,将LINQ表达式树转换为关系代数树的转换器
  final RelOptCluster cluster;  // 成员变量:关系优化集群,包含RexBuilder、类型工厂等优化工具,用于构建关系表达式
  private final Prepare preparingStmt;  // 成员变量:准备语句,包含SQL查询准备时的上下文信息,用于视图展开等操作
  final JavaTypeFactory typeFactory;  // 成员变量:Java类型工厂,用于Java类型与Calcite类型之间的相互转换

  LixToRelTranslator(RelOptCluster cluster, Prepare preparingStmt) {  // 构造方法:创建LixToRelTranslator实例,初始化转换器
    this.cluster = cluster;  // 初始化关系优化集群
    this.preparingStmt = preparingStmt;  // 初始化准备语句
    this.typeFactory = (JavaTypeFactory) cluster.getTypeFactory();  // 从集群获取类型工厂并强转为JavaTypeFactory
  }

  private static BlockStatement getBody(FunctionExpression<?> expression) {  // 私有静态方法:获取函数表达式的代码块,用于提取函数体
    return requireNonNull(expression.body, () -> "body in " + expression);  // 返回函数体,如果为null则抛出异常
  }

  private static List<ParameterExpression> getParameterList(FunctionExpression<?> expression) {  // 私有静态方法:获取函数表达式的参数列表
    return requireNonNull(expression.parameterList, () -> "parameterList in " + expression);  // 返回参数列表,如果为null则抛出异常
  }

  private static Expression getTargetExpression(MethodCallExpression call) {  // 私有静态方法:获取方法调用的目标对象表达式
    return requireNonNull(call.targetExpression,  // 返回目标表达式,即方法调用的接收者对象
        "translation of static calls is not supported yet");  // 如果为null则抛出异常,因为不支持静态方法调用
  }

  RelOptTable.ToRelContext toRelContext() {  // 方法:创建表到关系节点的转换上下文,用于视图展开
    if (preparingStmt instanceof RelOptTable.ViewExpander) {  // 判断准备语句是否实现了视图展开器接口
      final RelOptTable.ViewExpander viewExpander =  // 如果是,则转换为视图展开器
          (RelOptTable.ViewExpander) this.preparingStmt;  // 强制类型转换
      return ViewExpanders.toRelContext(viewExpander, cluster);  // 创建支持视图展开的转换上下文
    } else {  // 如果不是视图展开器
      return ViewExpanders.simpleContext(cluster);  // 创建简单的转换上下文,不支持视图展开
    }
  }

  public <T> RelNode translate(Queryable<T> queryable) {  // 公共方法:将Queryable对象转换为关系节点,泛型T表示元素类型
    QueryableRelBuilder<T> translatorQueryable =  // 创建QueryableRelBuilder实例,用于构建关系节点
        new QueryableRelBuilder<>(this);  // 传入当前转换器作为参数
    return translatorQueryable.toRel(queryable);  // 调用toRel方法执行转换,返回关系节点
  }

  public RelNode translate(Expression expression) {  // 公共方法:将LINQ表达式转换为关系节点,支持方法调用表达式
    if (expression instanceof MethodCallExpression) {  // 判断表达式是否为方法调用表达式
      final MethodCallExpression call = (MethodCallExpression) expression;  // 强制类型转换为方法调用表达式
      BuiltInMethod method = BuiltInMethod.FUNCTIONS_MAPS.get(call.method);  // 从内置方法映射中获取对应的方法枚举
      if (method == null) {  // 如果方法不在映射表中
        throw new UnsupportedOperationException(  // 抛出不支持操作异常
            "unknown method " + call.method);  // 提示未知的方法
      }
      RelNode input;  // 声明输入关系节点变量
      switch (method) {  // 根据内置方法类型进行分支处理
      case SELECT:  // 处理SELECT投影操作
        input = translate(getTargetExpression(call));  // 递归翻译方法调用目标表达式,获取输入关系节点
        return LogicalProject.create(input,  // 创建逻辑投影节点
            ImmutableList.of(),  // 传入空的特征列表
            toRex(input, (FunctionExpression) call.expressions.get(0)),  // 将投影表达式转换为Rex节点列表
            (List<String>) null,  // 传入null作为字段名列表
            ImmutableSet.of());  // 传入空的标志集合

      case WHERE:  // 处理WHERE过滤操作
        input = translate(getTargetExpression(call));  // 递归翻译方法调用目标表达式,获取输入关系节点
        return LogicalFilter.create(input,  // 创建逻辑过滤节点
            toRex((FunctionExpression) call.expressions.get(0), input));  // 将过滤条件表达式转换为Rex节点

      case AS_QUERYABLE:  // 处理AS_QUERYABLE操作,将对象转换为可查询对象
        return LogicalTableScan.create(cluster,  // 创建逻辑表扫描节点
            RelOptTableImpl.create(null,  // 创建关系优化表实现
                typeFactory.createJavaType(  // 创建Java类型
                    Types.toClass(  // 将类型转换为Class对象
                        getElementType(call))),  // 获取元素类型
                ImmutableList.of(),  // 传入空的字段列表
                getTargetExpression(call)),  // 传入目标表达式作为可查询对象
            ImmutableList.of());  // 传入空的字段名列表

      case SCHEMA_GET_TABLE:  // 处理SCHEMA_GET_TABLE操作,从schema获取表
        return LogicalTableScan.create(cluster,  // 创建逻辑表扫描节点
            RelOptTableImpl.create(null,  // 创建关系优化表实现
                typeFactory.createJavaType((Class)  // 创建Java类型
                    requireNonNull(  // 检查非空
                        ((ConstantExpression) call.expressions.get(1)).value,  // 获取第二个参数的值
                        "argument 1 (0-based) is null Class")),  // 如果为null则抛出异常
                ImmutableList.of(),  // 传入空的字段列表
                getTargetExpression(call)),  // 传入目标表达式
            ImmutableList.of());  // 传入空的字段名列表

      default:  // 默认情况,未知方法
        throw new UnsupportedOperationException(  // 抛出不支持操作异常
            "unknown method " + call.method);  // 提示未知的方法
      }
    }
    throw new UnsupportedOperationException(  // 如果不是方法调用表达式,抛出不支持操作异常
        "unknown expression type " + expression.getNodeType());  // 提示未知的表达式类型
  }

  private static Type getElementType(MethodCallExpression call) {  // 私有静态方法:获取方法调用表达式的元素类型
    Type type = getTargetExpression(call).getType();  // 获取目标表达式的类型
    return requireNonNull(  // 返回非空的元素类型
        Types.getElementType(type),  // 从类型中提取元素类型(如List<String>提取String)
        () -> "unable to figure out element type from " + type);  // 如果无法提取则抛出异常
  }

  private List<RexNode> toRex(  // 私有方法:将函数表达式转换为Rex节点列表,用于投影操作
      RelNode child, FunctionExpression expression) {  // 参数:子关系节点和函数表达式
    RexBuilder rexBuilder = cluster.getRexBuilder();  // 获取Rex构建器,用于构建行表达式
    List<RexNode> list =  // 创建Rex节点列表
        Collections.singletonList(  // 创建单元素列表
            rexBuilder.makeRangeReference(child));  // 为子节点创建范围引用,引用所有字段
    CalcitePrepareImpl.ScalarTranslator translator =  // 创建标量转换器,用于将LINQ表达式转换为Rex表达式
        CalcitePrepareImpl.EmptyScalarTranslator  // 使用空标量转换器
            .empty(rexBuilder)  // 初始化为空,传入Rex构建器
            .bind(getParameterList(expression), list);  // 绑定参数列表到Rex节点列表
    final List<RexNode> rexList = new ArrayList<>();  // 创建Rex节点结果列表
    final Expression simple = Blocks.simple(getBody(expression));  // 简化函数体表达式,转换为简单形式
    for (Expression expression1 : fieldExpressions(simple)) {  // 遍历字段表达式列表
      rexList.add(translator.toRex(expression1));  // 将每个字段表达式转换为Rex节点并添加到结果列表
    }
    return rexList;  // 返回Rex节点列表
  }

  List<Expression> fieldExpressions(Expression expression) {  // 方法:提取表达式中的字段表达式列表
    if (expression instanceof NewExpression) {  // 判断表达式是否为新建对象表达式
      // Note: We are assuming that the arguments to the constructor  // 注释:假设构造函数参数的顺序
      // are the same order as the fields of the class.  // 与类字段的顺序相同
      return ((NewExpression) expression).arguments;  // 返回构造函数参数列表作为字段表达式列表
    }
    throw new RuntimeException(  // 如果不是新建对象表达式,抛出运行时异常
        "unsupported expression type " + expression);  // 提示不支持的表达式类型
  }

  List<RexNode> toRexList(  // 方法:将函数表达式转换为Rex节点列表,支持多个输入关系节点
      FunctionExpression expression,  // 函数表达式
      RelNode... inputs) {  // 可变参数:输入关系节点数组
    List<RexNode> list = new ArrayList<>();  // 创建Rex节点列表
    RexBuilder rexBuilder = cluster.getRexBuilder();  // 获取Rex构建器
    for (RelNode input : inputs) {  // 遍历所有输入关系节点
      list.add(rexBuilder.makeRangeReference(input));  // 为每个输入节点创建范围引用并添加到列表
    }
    return CalcitePrepareImpl.EmptyScalarTranslator.empty(rexBuilder)  // 创建空标量转换器
        .bind(getParameterList(expression), list)  // 绑定参数列表到Rex节点列表
        .toRexList(getBody(expression));  // 将函数体转换为Rex节点列表并返回
  }

  RexNode toRex(  // 方法:将函数表达式转换为单个Rex节点,用于过滤条件
      FunctionExpression expression,  // 函数表达式
      RelNode... inputs) {  // 可变参数:输入关系节点数组
    List<RexNode> list = new ArrayList<>();  // 创建Rex节点列表
    RexBuilder rexBuilder = cluster.getRexBuilder();  // 获取Rex构建器
    for (RelNode input : inputs) {  // 遍历所有输入关系节点
      list.add(rexBuilder.makeRangeReference(input));  // 为每个输入节点创建范围引用并添加到列表
    }
    return CalcitePrepareImpl.EmptyScalarTranslator.empty(rexBuilder)  // 创建空标量转换器
        .bind(getParameterList(expression), list)  // 绑定参数列表到Rex节点列表
        .toRex(getBody(expression));  // 将函数体转换为单个Rex节点并返回
  }
}
