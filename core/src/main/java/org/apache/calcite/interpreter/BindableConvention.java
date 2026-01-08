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
package org.apache.calcite.interpreter; // 声明包名，该类位于org.apache.calcite.interpreter包下，属于Calcite解释器模块

import org.apache.calcite.plan.Convention; // 导入Convention接口，这是Calcite中表示调用约定的核心接口
import org.apache.calcite.plan.ConventionTraitDef; // 导入ConventionTraitDef类，定义了调用约定特征的定义
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，关系表达式优化器接口
import org.apache.calcite.plan.RelTrait; // 导入RelTrait接口，关系表达式的特征接口
import org.apache.calcite.plan.RelTraitDef; // 导入RelTraitDef接口，关系表达式特征的定义接口
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，关系表达式特征集合
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式树的节点

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可空类型

/**
 * Calling convention that returns results as an // 这是一个调用约定（Calling Convention），用于将查询结果以对象数组的形式返回
 * {@link org.apache.calcite.linq4j.Enumerable} of object arrays. // 结果以Enumerable<Object[]>的形式返回，其中每个元素是一个Object数组表示一行数据
 *
 * <p>The relational expression needs to implement // 关系表达式需要实现ArrayBindable接口才能使用此约定
 * {@link org.apache.calcite.runtime.ArrayBindable}. // ArrayBindable接口定义了将关系表达式绑定到可执行代码的能力
 * Unlike {@link org.apache.calcite.adapter.enumerable.EnumerableConvention}, // 与EnumerableConvention不同，它不需要代码生成
 * no code generation is required. // 这意味着可以在运行时直接解释执行，而不需要生成Java代码，简化了实现但可能牺牲性能
 */
public enum BindableConvention implements Convention { // 定义一个枚举类BindableConvention，实现Convention接口，表示可绑定的调用约定
  INSTANCE; // 定义唯一的枚举实例INSTANCE，因为调用约定通常是单例的

  /** Cost of a bindable node versus implementing an equivalent node in a // 可绑定节点的成本因子，与在"典型"调用约定中实现等效节点的成本相比
   * "typical" calling convention. */ // 用于优化器计算成本时，BindableConvention的实现成本是典型约定的2倍，因为解释执行比编译代码慢
  public static final double COST_MULTIPLIER = 2.0d; // 成本因子常量，值为2.0，表示BindableConvention的实现成本是默认的两倍

  @Override public String toString() { // 重写toString方法，将枚举实例转换为字符串表示
    return getName(); // 返回调用约定的名称，调用getName方法获取
  }

  @Override public Class getInterface() { // 重写getInterface方法，返回实现此约定的关系表达式必须实现的接口
    return BindableRel.class; // 返回BindableRel.class，表示使用此约定的关系表达式必须实现BindableRel接口
  }

  @Override public String getName() { // 重写getName方法，返回调用约定的名称
    return "BINDABLE"; // 返回字符串"BINDABLE"，这是BindableConvention的标识名称
  }

  @Override public @Nullable RelNode enforce(RelNode input, RelTraitSet required) { // 重写enforce方法，用于强制将输入关系表达式转换为满足所需特征集的表达式
    return null; // 返回null，表示不提供强制转换功能，即BindableConvention不能通过此方法自动转换其他约定
  }

  @Override public RelTraitDef getTraitDef() { // 重写getTraitDef方法，返回此特征的定义
    return ConventionTraitDef.INSTANCE; // 返回ConventionTraitDef.INSTANCE，表示这是Convention类型的特征定义
  }

  @Override public boolean satisfies(RelTrait trait) { // 重写satisfies方法，判断给定的特征是否满足此约定
    return this == trait; // 只有当给定的特征就是当前约定实例时才返回true，表示只有完全相同的约定才满足
  }

  @Override public void register(RelOptPlanner planner) {} // 重写register方法，向优化器注册此约定，当前为空实现，表示不需要特殊注册

  @Override public boolean canConvertConvention(Convention toConvention) { // 重写canConvertConvention方法，判断是否能转换为其他调用约定
    return false; // 返回false，表示BindableConvention不能直接转换为其他调用约定
  }

  @Override public boolean useAbstractConvertersForConversion(RelTraitSet fromTraits, // 重写useAbstractConvertersForConversion方法，判断转换时是否使用抽象转换器
      RelTraitSet toTraits) { // 参数fromTraits表示源特征集，toTraits表示目标特征集
    return false; // 返回false，表示不使用抽象转换器进行特征转换
  }
}
