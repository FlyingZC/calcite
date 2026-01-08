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
// 声明包名，表示该类属于org.apache.calcite.adapter.enumerable包，这是Calcite的可枚举适配器包
package org.apache.calcite.adapter.enumerable;

// 导入Contexts类，用于创建上下文对象，用于存储和传递配置信息
import org.apache.calcite.plan.Contexts;
// 导入Convention接口，这是调用约定的基接口，定义了不同数据访问方式的规范
import org.apache.calcite.plan.Convention;
// 导入ConventionTraitDef类，定义了调用约定特征的元数据，用于特征转换
import org.apache.calcite.plan.ConventionTraitDef;
// 导入RelOptPlanner接口，关系表达式优化器，负责执行查询优化
import org.apache.calcite.plan.RelOptPlanner;
// 导入RelTrait接口，关系特征的基接口，特征包括调用约定、排序、分布等
import org.apache.calcite.plan.RelTrait;
// 导入RelTraitDef接口，关系特征定义的基接口，用于特征的注册和转换
import org.apache.calcite.plan.RelTraitDef;
// 导入RelTraitSet类，关系特征集合，包含一个关系节点的所有特征
import org.apache.calcite.plan.RelTraitSet;
// 导入RelCollation类，表示关系数据的排序规则（排序字段和方向）
import org.apache.calcite.rel.RelCollation;
// 导入RelCollations类，提供创建和操作排序规则的静态工具方法
import org.apache.calcite.rel.RelCollations;
// 导入RelNode接口，关系表达式节点，是Calcite中所有关系操作符的基类
import org.apache.calcite.rel.RelNode;
// 导入RelFactories类，包含创建各种关系节点的工厂方法
import org.apache.calcite.rel.core.RelFactories;

// 静态导入requireNonNull方法，用于非空校验，如果对象为null则抛出NullPointerException
import static java.util.Objects.requireNonNull;

/**
 * Family of calling conventions that return results as an
 * {@link org.apache.calcite.linq4j.Enumerable}.
 * 
 * 这是一个枚举调用约定类，定义了一组返回结果为Enumerable（可枚举集合）的调用约定
 * Enumerable是Calcite中基于LINQ4J的数据访问方式，支持延迟计算和流式处理
 * 这个类实现了Convention接口，表示它是一种特定的数据访问约定
 * 
 * 核心作用：
 * 1. 定义可枚举数据源的访问规范
 * 2. 提供从其他约定转换为可枚举约定的能力
 * 3. 管理可枚举关系节点的创建和转换
 * 
 * 使用场景：
 * - 当查询结果需要以Java流的方式处理时
 * - 当需要延迟计算和迭代式处理时
 * - 当数据源支持LINQ风格的查询时
 */
public enum EnumerableConvention implements Convention {
  // 单例实例，这是该枚举类的唯一实例，代表可枚举调用约定
  INSTANCE;

  /** Cost of an enumerable node versus implementing an equivalent node in a
   * "typical" calling convention.
   * 
   * 成员变量：成本乘数
   * 作用：表示可枚举节点相对于"典型"调用约定实现的成本系数
   * 默认值为1.0d，表示可枚举节点的成本与典型节点相同
   * 
   * 优化器使用说明：
   * - 优化器在计算计划成本时会使用这个乘数
   * - 值越大，优化器越倾向于选择其他约定
   * - 值越小，优化器越倾向于选择可枚举约定
   * - 可以根据实际性能调整此值来影响优化器的选择
   */
  public static final double COST_MULTIPLIER = 1.0d;

  @Override public String toString() {
    // 返回该约定的名称，用于日志输出和调试信息
    return getName();
  }

  @Override public Class getInterface() {
    // 返回该约定对应的关系节点接口类型
    // 所有使用可枚举约定的关系节点都必须实现EnumerableRel接口
    // EnumerableRel接口定义了可枚举关系节点的行为规范
    return EnumerableRel.class;
  }

  @Override public String getName() {
    // 返回约定的字符串名称，用于标识这个调用约定
    // 名称"ENUMERABLE"在优化器日志和错误消息中使用
    return "ENUMERABLE";
  }

  @Override public RelNode enforce(
      final RelNode input,
      final RelTraitSet required) {
    // enforce方法：强制输入关系节点满足指定的特征集合
    // 参数input：输入的关系节点，可能需要转换或包装
    // 参数required：要求的特征集合，包括调用约定、排序等
    
    // 首先将输入节点赋值给临时变量rel，后续可能需要修改
    RelNode rel = input;
    
    // 检查输入节点的调用约定是否已经是可枚举约定
    if (input.getConvention() != INSTANCE) {
      // 如果不是可枚举约定，则需要进行转换
      // 使用ConventionTraitDef将输入节点转换为可枚举约定
      // 获取集群中的优化器，调用convert方法执行转换
      // true参数表示允许转换过程中的损失（如类型转换）
      rel =
          ConventionTraitDef.INSTANCE.convert(input.getCluster().getPlanner(),
              input, INSTANCE, true);
      
      // 检查转换结果是否为null，如果转换失败则抛出异常
      // requireNonNull确保转换成功，否则提供详细的错误信息
      // 错误信息中包含了无法转换的输入节点信息，便于调试
      requireNonNull(rel,
          () -> "Unable to convert input to " + INSTANCE + ", input = " + input);
    }
    
    // 获取要求特征集合中的排序规则（Collation）
    // Collation定义了数据应该按照哪些字段以及什么方向排序
    RelCollation collation = required.getCollation();
    
    // 检查是否要求特定的排序规则，并且排序规则不为空
    // RelCollations.EMPTY表示不需要排序
    if (collation != null && collation != RelCollations.EMPTY) {
      // 如果需要排序，则在当前节点上添加一个排序操作
      // 创建EnumerableSort节点来实现排序功能
      // 参数说明：
      // - rel：输入的关系节点
      // - collation：排序规则
      // - null：offset（偏移量），表示从第几行开始，null表示从头开始
      // - null：fetch（获取行数），表示获取多少行，null表示获取全部
      rel = EnumerableSort.create(rel, collation, null, null);
    }
    
    // 返回最终的关系节点，该节点满足所有要求的特征
    // 可能是原始节点、转换后的节点或添加排序后的节点
    return rel;
  }

  @Override public RelTraitDef getTraitDef() {
    // 获取该特征对应的特征定义
    // 返回ConventionTraitDef.INSTANCE，表示这是调用约定类型的特征
    // 用于优化器在特征注册和转换时识别特征类型
    return ConventionTraitDef.INSTANCE;
  }

  @Override public boolean satisfies(RelTrait trait) {
    // 判断给定特征是否满足当前约定
    // 参数trait：待检查的特征
    // 返回true表示特征相同，满足要求
    // 使用==比较是因为INSTANCE是单例，确保只有完全相同的约定才满足
    return this == trait;
  }

  @Override public void register(RelOptPlanner planner) {}
  // register方法：向优化器注册该约定
  // 参数planner：关系表达式优化器
  // 当前实现为空，不需要特殊的注册逻辑
  // 因为Convention是枚举单例，不需要动态注册

  @Override public boolean canConvertConvention(Convention toConvention) {
    // canConvertConvention方法：判断是否可以转换为指定的目标约定
    // 参数toConvention：目标调用约定
    // 返回false表示不能直接转换
    // 
    // 设计原因：
    // - 可枚举约定通常作为目标约定，而不是源约定
    // - 其他约定转换为可枚举约定通过规则和转换器实现
    // - 返回false避免优化器尝试直接转换，而是使用专门的转换规则
    return false;
  }

  @Override public boolean useAbstractConvertersForConversion(RelTraitSet fromTraits,
      RelTraitSet toTraits) {
    // useAbstractConvertersForConversion方法：判断转换时是否使用抽象转换器
    // 参数fromTraits：源特征集合
    // 参数toTraits：目标特征集合
    // 返回true表示使用抽象转换器
    // 
    // 抽象转换器的作用：
    // - 提供通用的转换逻辑，不依赖于具体的实现
    // - 支持更灵活的特征组合转换
    // - 允许优化器在运行时选择最优的转换路径
    // 
    // 返回true的原因：
    // - 可枚举约定需要支持多种源约定的转换
    // - 抽象转换器可以处理复杂的特征组合
    // - 提高转换的灵活性和可扩展性
    return true;
  }

  @Override public RelFactories.Struct getRelFactories() {
    // getRelFactories方法：获取关系节点工厂结构
    // 返回RelFactories.Struct对象，包含创建各种关系节点的工厂
    // 
    // 工厂的作用：
    // - 提供统一的关系节点创建接口
    // - 支持在运行时动态创建关系节点
    // - 封装节点创建的复杂性
    // 
    // 返回的工厂包括：
    // 1. ENUMERABLE_TABLE_SCAN_FACTORY：创建表扫描节点，用于从数据源读取数据
    // 2. ENUMERABLE_PROJECT_FACTORY：创建投影节点，用于选择和计算字段
    // 3. ENUMERABLE_FILTER_FACTORY：创建过滤节点，用于数据过滤
    // 4. ENUMERABLE_SORT_FACTORY：创建排序节点，用于数据排序
    // 
    // 使用Contexts.of创建上下文，将这些工厂注册到上下文中
    // RelFactories.Struct.fromContext从上下文中提取工厂结构
    return RelFactories.Struct.fromContext(
            Contexts.of(
                EnumerableRelFactories.ENUMERABLE_TABLE_SCAN_FACTORY,
                EnumerableRelFactories.ENUMERABLE_PROJECT_FACTORY,
                EnumerableRelFactories.ENUMERABLE_FILTER_FACTORY,
                EnumerableRelFactories.ENUMERABLE_SORT_FACTORY));
  }
}
