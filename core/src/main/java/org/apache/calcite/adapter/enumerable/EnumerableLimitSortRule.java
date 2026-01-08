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
package org.apache.calcite.adapter.enumerable;  // 声明包名，该类属于org.apache.calcite.adapter.enumerable包，这是一个用于可枚举适配器的包

import org.apache.calcite.plan.RelOptRuleCall;  // 导入RelOptRuleCall类，表示优化规则调用时传递的上下文信息，包含匹配到的关系表达式
import org.apache.calcite.plan.RelRule;  // 导入RelRule基类，所有优化规则的基类，提供了规则配置和匹配的基础功能
import org.apache.calcite.rel.RelNode;  // 导入RelNode接口，表示关系代数表达式，是Calcite中所有关系操作符的基类
import org.apache.calcite.rel.core.Sort;  // 导入Sort类，表示排序操作符，包含排序规则、偏移量和获取行数等属性
import org.apache.calcite.rel.logical.LogicalSort;  // 导入LogicalSort类，表示逻辑层面的排序操作符，是优化器早期阶段的排序表示

import org.immutables.value.Value;  // 导入Immutables库的Value注解，用于生成不可变对象，确保线程安全和一致性

/**
 * Rule to convert an {@link EnumerableLimit} of on
 * {@link EnumerableSort} into an {@link EnumerableLimitSort}.
 * 该规则的作用是将EnumerableLimit和EnumerableSort的组合转换为单一的EnumerableLimitSort操作符
 * 这样可以优化执行计划，避免分别执行limit和sort操作，而是将它们合并为一个更高效的操作
 * EnumerableLimitSort是一个同时执行排序和限制行数的操作符，可以减少中间结果的生成
 */
@Value.Enclosing  // Value.Enclosing注解，表示该类内部包含使用@Value.Immutable注解的嵌套配置接口
public class EnumerableLimitSortRule extends RelRule<EnumerableLimitSortRule.Config> {  // 声明EnumerableLimitSortRule类，继承自RelRule基类，泛型参数为Config接口类型

  /**
   * Creates a EnumerableLimitSortRule.
   * 构造方法：创建一个EnumerableLimitSortRule实例
   * @param config 规则的配置对象，包含规则的各种配置参数，如操作数匹配条件等
   */
  public EnumerableLimitSortRule(Config config) {  // 构造方法，接收一个Config配置对象作为参数
    super(config);  // 调用父类RelRule的构造方法，将配置对象传递给父类进行初始化
  }

  @Override public void onMatch(RelOptRuleCall call) {  // 重写onMatch方法，当规则匹配成功时被调用，执行具体的转换逻辑
    final Sort sort = call.rel(0);  // 从规则调用对象中获取第一个匹配到的关系表达式，这里应该是一个Sort节点
    RelNode input = sort.getInput();  // 获取Sort节点的输入关系表达式，即需要被排序的数据源
    final Sort o =  // 声明一个Sort类型的变量o，用于存储转换后的EnumerableLimitSort节点
        EnumerableLimitSort.create(  // 调用EnumerableLimitSort的静态工厂方法create来创建一个LimitSort节点
            convert(call.getPlanner(), input,  // 调用convert方法将输入节点转换为EnumerableConvention约定
                input.getTraitSet().replace(EnumerableConvention.INSTANCE)),  // 替换输入节点的trait集合，将其约定设置为EnumerableConvention，确保输入是可枚举的
            sort.getCollation(), sort.offset, sort.fetch);  // 传递排序规则、偏移量和获取行数参数给EnumerableLimitSort

    call.transformTo(o);  // 将原始的关系表达式节点转换为新生成的EnumerableLimitSort节点，完成规则转换
  }

  /** Rule configuration.
   * 规则配置接口：定义了该优化规则的配置参数
   * 使用@Value.Immutable注解确保配置对象是不可变的，提供线程安全和一致性保证
   */
  @Value.Immutable  // Value.Immutable注解，指示Immutables库为该接口生成不可变实现类
  public interface Config extends RelRule.Config {  // 声明Config接口，继承自RelRule.Config接口，扩展规则配置
    Config DEFAULT =  // 定义默认的配置实例，该实例定义了规则的默认匹配条件
        ImmutableEnumerableLimitSortRule.Config.of()  // 调用Immutables生成的Config工厂方法创建配置对象
            .withOperandSupplier(b0 ->  // 设置操作数供应器，定义规则匹配的关系表达式模式
                b0.operand(LogicalSort.class)  // 指定操作数为LogicalSort类型，即规则只匹配逻辑排序节点
                    .predicate(sort -> sort.fetch != null)  // 添加谓词条件，只匹配带有fetch参数的Sort节点，即带有限制行数的排序
                    .anyInputs());  // 表示该Sort节点可以接受任意类型的输入，不限制输入节点的类型

    @Override default EnumerableLimitSortRule toRule() {  // 重写toRule方法，将配置对象转换为规则实例
      return new EnumerableLimitSortRule(this);  // 返回一个新的EnumerableLimitSortRule实例，传入当前配置对象
    }
  }
}
