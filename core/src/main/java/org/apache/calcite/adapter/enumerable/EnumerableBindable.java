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
package org.apache.calcite.adapter.enumerable;  // 声明包名，这个类属于Calcite的enumerable适配器包，用于处理可枚举的数据源

import org.apache.calcite.DataContext;  // 导入DataContext接口，用于在查询执行期间提供上下文信息（如数据源、变量等）
import org.apache.calcite.interpreter.BindableConvention;  // 导入BindableConvention，表示可绑定调用约定，用于解释器执行
import org.apache.calcite.interpreter.BindableRel;  // 导入BindableRel接口，表示可以被解释器绑定的关系表达式
import org.apache.calcite.interpreter.Node;  // 导入Node接口，表示解释器执行树中的一个节点
import org.apache.calcite.interpreter.Row;  // 导入Row类，表示查询结果的一行数据
import org.apache.calcite.interpreter.Sink;  // 导入Sink接口，表示数据接收器，用于接收查询结果
import org.apache.calcite.linq4j.Enumerable;  // 导入Enumerable接口，表示可枚举的数据集合，支持LINQ风格的查询
import org.apache.calcite.linq4j.Enumerator;  // 导入Enumerator接口，表示枚举器，用于遍历数据集合
import org.apache.calcite.plan.ConventionTraitDef;  // 导入ConventionTraitDef，表示调用约定特征定义
import org.apache.calcite.plan.RelOptCluster;  // 导入RelOptCluster，表示关系表达式优化集群，包含优化器的共享信息
import org.apache.calcite.plan.RelTraitSet;  // 导入RelTraitSet，表示关系表达式特征集合（如调用约定、排序方式等）
import org.apache.calcite.rel.RelNode;  // 导入RelNode接口，表示关系表达式（关系代数操作符）的基类
import org.apache.calcite.rel.convert.ConverterImpl;  // 导入ConverterImpl，表示转换器实现类，用于将一种调用约定转换为另一种
import org.apache.calcite.rel.convert.ConverterRule;  // 导入ConverterRule，表示转换规则，用于优化器将关系表达式从一种约定转换为另一种
import org.apache.calcite.runtime.ArrayBindable;  // 导入ArrayBindable接口，表示可以绑定到Object[]数组的可绑定对象
import org.apache.calcite.runtime.Bindable;  // 导入Bindable接口，表示可以被绑定到DataContext的可执行对象

import com.google.common.collect.ImmutableMap;  // 导入Google Guava的ImmutableMap，表示不可变的Map集合

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入Nullable注解，用于标记可空的类型

import java.util.List;  // 导入List接口，表示有序列表

import static java.util.Objects.requireNonNull;  // 静态导入requireNonNull方法，用于检查对象是否为null

/**
 * Relational expression that converts an enumerable input to interpretable
 * calling convention.
 * 关系表达式，将可枚举输入转换为可解释的调用约定
 * 这个类的作用是作为转换器，将EnumerableConvention（可枚举约定）的关系表达式转换为BindableConvention（可绑定约定）
 * 这样可以在解释器中执行原本以可枚举方式实现的关系操作
 * 
 * @see org.apache.calcite.adapter.enumerable.EnumerableConvention  // 参见EnumerableConvention，定义可枚举调用约定
 * @see org.apache.calcite.interpreter.BindableConvention  // 参见BindableConvention，定义可绑定调用约定
 */
public class EnumerableBindable extends ConverterImpl implements BindableRel {  // 类声明：继承ConverterImpl并实现BindableRel接口
  protected EnumerableBindable(RelOptCluster cluster, RelNode input) {  // 构造方法：创建一个EnumerableBindable转换器实例
    super(cluster, ConventionTraitDef.INSTANCE,  // 调用父类ConverterImpl的构造方法，传入集群和约定特征定义
        cluster.traitSetOf(BindableConvention.INSTANCE), input);  // 设置特征集为包含BindableConvention，传入输入关系节点
  }

  @Override public EnumerableBindable copy(RelTraitSet traitSet,  // 复制方法：创建当前关系节点的副本，用于优化器进行等价变换
      List<RelNode> inputs) {  // 输入参数：新的特征集和输入节点列表
    return new EnumerableBindable(getCluster(), sole(inputs));  // 返回新的EnumerableBindable实例，使用当前集群和唯一的输入节点
  }

  @Override public Class<Object[]> getElementType() {  // 获取元素类型方法：返回此关系表达式产生的数据行类型
    return Object[].class;  // 返回Object[]类，表示每行数据以对象数组形式存储
  }

  @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) {  // 绑定方法：将此关系表达式绑定到数据上下文，返回可枚举的结果集
    final ImmutableMap<String, Object> map = ImmutableMap.of();  // 创建空的不可变Map，用于存储额外的参数和上下文信息
    final Bindable bindable =  // 创建Bindable对象，用于执行可枚举关系表达式
        EnumerableInterpretable.toBindable(map, null,  // 调用EnumerableInterpretable的toBindable静态方法，传入参数Map和null参数
            (EnumerableRel) getInput(), EnumerableRel.Prefer.ARRAY);  // 将输入关系节点转换为EnumerableRel，并指定偏好ARRAY格式
    final ArrayBindable arrayBindable = EnumerableInterpretable.box(bindable);  // 将Bindable包装为ArrayBindable，使其返回Object[]数组
    return arrayBindable.bind(dataContext);  // 绑定到数据上下文并返回可枚举的结果集
  }

  @Override public Node implement(final InterpreterImplementor implementor) {  // 实现方法：为解释器创建执行节点，生成可执行的代码
    return () -> {  // 返回一个Node的lambda表达式，该表达式在执行时会被调用
      final Sink sink =  // 获取数据接收器，用于将查询结果发送到下游
          requireNonNull(implementor.relSinks.get(EnumerableBindable.this),  // 从实现器的relSinks映射中获取当前节点的sink列表，要求非null
              () -> "relSinks.get is null for " + EnumerableBindable.this).get(0);  // 如果为null，抛出异常并提示；否则获取第一个sink
      final Enumerable<@Nullable Object[]> enumerable = bind(implementor.dataContext);  // 调用bind方法，绑定到数据上下文，获取可枚举的结果集
      final Enumerator<@Nullable Object[]> enumerator = enumerable.enumerator();  // 从可枚举集合中获取枚举器，用于遍历结果
      while (enumerator.moveNext()) {  // 循环遍历枚举器中的每一行数据
        sink.send(Row.asCopy(enumerator.current()));  // 将当前行数据转换为Row副本，并发送到sink接收器
      }
    };
  }

  /**
   * Rule that converts any enumerable relational expression to bindable.
   * 转换规则：将任何可枚举的关系表达式转换为可绑定的
   * 这个规则在优化器中用于将EnumerableConvention的关系节点转换为BindableConvention的节点
   * 
   * @see EnumerableRules#TO_BINDABLE  // 参见EnumerableRules中的TO_BINDABLE规则定义
   */
  public static class EnumerableToBindableConverterRule extends ConverterRule {  // 内部静态类：定义从Enumerable到Bindable的转换规则
    /** Default configuration. */  // 默认配置注释
    public static final Config DEFAULT_CONFIG = Config.INSTANCE  // 定义默认配置：从Config.INSTANCE开始配置
        .withConversion(EnumerableRel.class,  // 指定转换的源类型为EnumerableRel接口（所有可枚举关系表达式）
            EnumerableConvention.INSTANCE, BindableConvention.INSTANCE,  // 转换调用约定从EnumerableConvention到BindableConvention
            "EnumerableToBindableConverterRule")  // 设置规则的名称为"EnumerableToBindableConverterRule"
        .withRuleFactory(EnumerableToBindableConverterRule::new);  // 设置规则工厂，使用构造函数引用创建规则实例

    protected EnumerableToBindableConverterRule(Config config) {  // 构造方法：使用配置创建转换规则实例
      super(config);  // 调用父类ConverterRule的构造方法，传入配置对象
    }

    @Override public RelNode convert(RelNode rel) {  // 转换方法：将给定的关系表达式转换为EnumerableBindable
      return new EnumerableBindable(rel.getCluster(), rel);  // 创建新的EnumerableBindable实例，使用原节点的集群和节点本身作为输入
    }
  }
}
