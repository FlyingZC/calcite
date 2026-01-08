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
// 声明包名，表示这个类属于org.apache.calcite.adapter.enumerable包，该包包含了可枚举适配器的相关实现
package org.apache.calcite.adapter.enumerable;

// 导入Convention类，用于表示关系代数节点的调用约定（即数据访问方式，如可枚举、可解释等）
import org.apache.calcite.plan.Convention;
// 导入RelTraitSet类，用于表示关系节点的特征集合，包括调用约定、排序、分区等属性
import org.apache.calcite.plan.RelTraitSet;
// 导入RelNode接口，这是所有关系代数节点的基类接口
import org.apache.calcite.rel.RelNode;
// 导入ConverterRule类，这是转换规则的基类，用于定义如何将一种类型的RelNode转换为另一种类型
import org.apache.calcite.rel.convert.ConverterRule;
// 导入RepeatUnion类，表示重复联合操作的核心关系节点，用于实现迭代查询
import org.apache.calcite.rel.core.RepeatUnion;
// 导入LogicalRepeatUnion类，表示逻辑层的重复联合操作节点
import org.apache.calcite.rel.logical.LogicalRepeatUnion;

/**
 * 这是一个转换规则类，用于将逻辑层的LogicalRepeatUnion节点转换为可枚举层的EnumerableRepeatUnion节点
 * LogicalRepeatUnion是逻辑计划中的重复联合操作，而EnumerableRepeatUnion是物理计划中的可执行实现
 * 可以通过提供自定义配置来转换其他继承自RepeatUnion的节点
 *
 * RepeatUnion是Calcite中用于实现迭代查询的重要操作，它类似于SQL中的递归CTE（Common Table Expression）
 * 它包含一个种子关系（seed）和一个迭代关系（iterative），每次迭代都会将迭代关系的结果与之前的结果合并
 * 直到达到迭代次数限制或不再产生新的结果
 *
 * @see EnumerableRules#ENUMERABLE_REPEAT_UNION_RULE  // 参见EnumerableRules类中定义的ENUMERABLE_REPEAT_UNION_RULE常量
 */
// 定义EnumerableRepeatUnionRule类，继承自ConverterRule，表示这是一个转换规则
// ConverterRule是Calcite优化器规则体系中的基础类，用于定义RelNode之间的转换规则
public class EnumerableRepeatUnionRule extends ConverterRule {
  /** Default configuration. */  // 注释：默认配置，用于创建这个转换规则的默认实例
  // 定义一个静态的默认配置常量DEFAULT_CONFIG
  // Config.INSTANCE表示使用ConverterRule.Config的默认实例
  // withConversion方法配置转换规则：
  //   - LogicalRepeatUnion.class：指定要转换的源节点类型为LogicalRepeatUnion
  //   - Convention.NONE：指定源节点的调用约定为NONE（表示逻辑层）
  //   - EnumerableConvention.INSTANCE：指定目标节点的调用约定为可枚举约定
  //   - "EnumerableRepeatUnionRule"：指定规则的描述名称
  // withRuleFactory方法指定规则工厂，使用方法引用EnumerableRepeatUnionRule::new来创建规则实例
  public static final Config DEFAULT_CONFIG = Config.INSTANCE
      .withConversion(LogicalRepeatUnion.class, Convention.NONE,
          EnumerableConvention.INSTANCE, "EnumerableRepeatUnionRule")
      .withRuleFactory(EnumerableRepeatUnionRule::new);

  /** Called from the Config. */  // 注释：从Config调用，即通过配置创建规则实例时调用
  // 定义受保护的构造方法，接收Config参数
  // 这个构造方法由Config的withRuleFactory方法调用，用于创建规则实例
  // super(config)调用父类ConverterRule的构造方法，传入配置参数进行初始化
  protected EnumerableRepeatUnionRule(Config config) {
    super(config);
  }

  // 重写父类的convert方法，这是转换规则的核心方法，负责执行实际的转换逻辑
  // 参数rel：要转换的源关系节点，这里应该是LogicalRepeatUnion类型
  // 返回值：转换后的目标关系节点，这里返回EnumerableRepeatUnion类型
  @Override public RelNode convert(RelNode rel) {
    // 将传入的RelNode强制类型转换为RepeatUnion，因为只有RepeatUnion及其子类才需要这个规则处理
    // RepeatUnion是逻辑重复联合操作的核心接口，LogicalRepeatUnion实现了这个接口
    RepeatUnion union = (RepeatUnion) rel;
    // 获取EnumerableConvention的实例，这是目标调用约定，表示结果应该是可枚举的
    // EnumerableConvention是Calcite中用于表示可以通过Java迭代器访问数据的约定
    EnumerableConvention out = EnumerableConvention.INSTANCE;
    // 创建新的特征集合，将原节点的特征集合中的调用约定替换为可枚举约定
    // RelTraitSet包含了节点的各种特征属性，replace方法用于替换其中的某个特征
    RelTraitSet traitSet = union.getTraitSet().replace(out);
    // 获取种子关系（seed relation），这是迭代的初始数据源
    // 种子关系是RepeatUnion的第一部分，它提供了迭代的初始结果集
    RelNode seedRel = union.getSeedRel();
    // 获取迭代关系（iterative relation），这是每次迭代要执行的关系操作
    // 迭代关系会引用上一次迭代的结果，产生新的结果，然后与之前的结果合并
    RelNode iterativeRel = union.getIterativeRel();

    // 创建并返回新的EnumerableRepeatUnion节点，这是转换后的物理可执行节点
    // 参数说明：
    //   - rel.getCluster()：获取关系节点的集群信息，包含优化器上下文等元数据
    //   - traitSet：新节点的特征集合，已经设置为可枚举约定
    //   - convert(seedRel, seedRel.getTraitSet().replace(out))：转换种子关系
    //     调用convert方法将种子关系也转换为可枚举约定，这是递归转换
    //   - convert(iterativeRel, iterativeRel.getTraitSet().replace(out))：转换迭代关系
    //     同样将迭代关系转换为可枚举约定
    //   - union.all：表示是否保留重复行（ALL关键字），true表示保留，false表示去重
    //   - union.iterationLimit：迭代次数限制，防止无限循环
    //   - union.getTransientTable()：获取临时表，用于在迭代过程中存储中间结果
    return new EnumerableRepeatUnion(
        rel.getCluster(),
        traitSet,
        convert(seedRel, seedRel.getTraitSet().replace(out)),
        convert(iterativeRel, iterativeRel.getTraitSet().replace(out)),
        union.all,
        union.iterationLimit,
        union.getTransientTable());
  }
}
