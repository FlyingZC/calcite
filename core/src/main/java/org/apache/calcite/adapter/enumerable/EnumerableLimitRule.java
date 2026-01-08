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
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于org.apache.calcite.adapter.enumerable包，这是Calcite可枚举适配器包，用于将关系代数转换为可枚举代码

import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall类，表示规则调用的上下文，包含关系表达式和优化器的相关信息
import org.apache.calcite.plan.RelRule; // 导入RelRule基类，所有优化规则的基类，提供规则的基本框架和配置机制
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式的基础接口，所有关系节点都实现此接口
import org.apache.calcite.rel.core.Sort; // 导入Sort类，表示排序操作的关系节点，包含排序键、偏移量和获取量等信息

import org.immutables.value.Value; // 导入Immutables库的Value注解，用于生成不可变值对象，提供编译时生成高效、线程安全的不可变类

/**
 * Rule to convert an {@link org.apache.calcite.rel.core.Sort} that has
 * {@code offset} or {@code fetch} set to an
 * {@link EnumerableLimit}
 * on top of a "pure" {@code Sort} that has no offset or fetch.
 * 该规则用于将带有offset（偏移量）或fetch（获取数量）的Sort节点转换为
 * 在一个不包含offset和fetch的"纯"Sort节点之上的EnumerableLimit节点
 * 这样可以将限制操作（LIMIT/OFFSET）与排序操作分离，优化执行计划
 *
 * @see EnumerableRules#ENUMERABLE_LIMIT_RULE
 */
@Value.Enclosing // 使用Immutables的Enclosing注解，标记此类作为嵌套配置类的外部类，允许内部接口生成不可变实现
public class EnumerableLimitRule // 定义类名，这是一个优化规则，用于将Sort转换为EnumerableLimit
    extends RelRule<EnumerableLimitRule.Config> { // 继承RelRule基类，泛型参数为Config接口类型，表示该规则的配置类型
  /** Creates an EnumerableLimitRule. */ // Javadoc注释：构造方法的说明文档，用于创建EnumerableLimitRule实例
  protected EnumerableLimitRule(Config config) { // 受保护的构造方法，接收Config配置对象作为参数，用于初始化规则
    super(config); // 调用父类RelRule的构造方法，传入配置对象，完成父类的初始化
  } // 构造方法结束

  @Deprecated // 标记为过时的构造方法，表示该构造方法已被废弃，建议使用带Config参数的构造方法
  // to be removed before 2.0 // 说明废弃原因：将在2.0版本之前移除，这是向后兼容的临时构造方法
  EnumerableLimitRule() { // 无参构造方法，默认构造方法，用于向后兼容，现已废弃
    this(Config.DEFAULT); // 调用带Config参数的构造方法，传入默认配置Config.DEFAULT
  } // 废弃的构造方法结束

  @Override public void onMatch(RelOptRuleCall call) { // 重写onMatch方法，当规则匹配成功时被调用，call参数包含规则调用的上下文信息
    final Sort sort = call.rel(0); // 从规则调用中获取第一个关系表达式（索引为0），强制转换为Sort类型，这是待处理的排序节点
    if (sort.offset == null && sort.fetch == null) { // 检查Sort节点是否既没有offset也没有fetch，如果都没有则不需要转换
      return; // 直接返回，不进行任何转换，因为只有当存在offset或fetch时才需要应用此规则
    } // 条件判断结束
    RelNode input = sort.getInput(); // 获取Sort节点的输入关系节点，这是排序操作的数据源
    if (!sort.getCollation().getFieldCollations().isEmpty()) { // 检查Sort节点是否有排序字段（collation不为空），如果有则需要进行排序
      // Create a sort with the same sort key, but no offset or fetch. // 注释说明：创建一个具有相同排序键但没有offset和fetch的Sort节点
      input = // 将input变量重新赋值为新的Sort节点，这个新节点只负责排序，不负责限制
          sort.copy(sort.getTraitSet(), input, sort.getCollation(), null, null); // 调用Sort的copy方法创建新的Sort节点，参数依次为：特征集、输入节点、排序规则、offset设为null、fetch设为null
    } // 条件判断结束，完成排序节点的创建
    call.transformTo( // 调用transformTo方法进行关系树转换，将原始的Sort节点转换为新的一组节点
        EnumerableLimit.create( // 创建EnumerableLimit节点，这是可枚举的限制操作节点，用于实现LIMIT和OFFSET功能
            convert(call.getPlanner(), input, // 调用convert方法将输入节点转换为EnumerableConvention约定，使其可枚举
                input.getTraitSet().replace(EnumerableConvention.INSTANCE)), // 替换特征集，将约定替换为EnumerableConvention.INSTANCE，表示使用可枚举约定
            sort.offset, // 传入原始Sort节点的offset参数，表示跳过的行数
            sort.fetch)); // 传入原始Sort节点的fetch参数，表示获取的行数
  } // onMatch方法结束

  /** Rule configuration. */ // Javadoc注释：规则配置接口的说明文档
  @Value.Immutable // 使用Immutables的Immutable注解，标记此接口为不可变配置接口，编译时会自动生成不可变实现类
  public interface Config extends RelRule.Config { // 定义Config接口，继承RelRule.Config接口，用于配置规则的行为和匹配条件
    Config DEFAULT = ImmutableEnumerableLimitRule.Config.of() // 定义默认配置常量，通过Immutables生成的工厂方法创建配置实例
        .withOperandSupplier(b -> b.operand(Sort.class).anyInputs()); // 配置操作数提供器，指定规则匹配Sort节点，且输入可以是任意类型

    @Override default EnumerableLimitRule toRule() { // 重写toRule方法，用于将配置转换为规则实例
      return new EnumerableLimitRule(this); // 创建并返回一个新的EnumerableLimitRule实例，传入当前配置对象this
    } // toRule方法结束
  } // Config接口结束
} // EnumerableLimitRule类结束
