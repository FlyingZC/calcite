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
// 声明包名：org.apache.calcite.adapter.enumerable，表示这个类属于Calcite的可枚举适配器包
package org.apache.calcite.adapter.enumerable;

// 导入Convention类：Calcite中的约定（Convention）概念，表示RelNode的物理实现方式
import org.apache.calcite.plan.Convention;
// 导入RelNode类：Calcite中关系代数表达式的基本接口，所有关系操作节点都实现此接口
import org.apache.calcite.rel.RelNode;
// 导入ConverterRule类：转换规则的基类，用于将一个RelNode从一种约定转换为另一种约定
import org.apache.calcite.rel.convert.ConverterRule;
// 导入Sort类：表示SQL中的ORDER BY操作，用于对结果集进行排序
import org.apache.calcite.rel.core.Sort;

// 导入Nullable注解：用于标记方法返回值可能为null，来自CheckerFramework框架的空值检查工具
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Rule to convert an {@link org.apache.calcite.rel.core.Sort} to an
 * {@link EnumerableSort}.
 * 这是一个转换规则类，用于将Sort关系节点转换为EnumerableSort节点
 * Sort是逻辑层面的排序操作，而EnumerableSort是可枚举约定下的物理实现
 * 这个规则在Calcite的优化器中用于将逻辑计划转换为物理执行计划
 *
 * @see EnumerableRules#ENUMERABLE_SORT_RULE  参见EnumerableRules类中定义的ENUMERABLE_SORT_RULE常量
 */
// 定义EnumerableSortRule类，继承自ConverterRule基类，表示这是一个转换规则
// ConverterRule是Calcite中用于实现RelNode转换的基类，它定义了转换的基本框架
class EnumerableSortRule extends ConverterRule {
  /** Default configuration. */
  // 定义默认配置常量DEFAULT_CONFIG，类型为Config（ConverterRule的内部配置类）
  // Config.INSTANCE获取ConverterRule的基础配置实例
  // withConversion方法配置转换规则：
  //   - Sort.class：指定要转换的源节点类型为Sort
  //   - Convention.NONE：指定源节点的约定为NONE（表示逻辑层，尚未指定物理实现方式）
  //   - EnumerableConvention.INSTANCE：指定目标节点的约定为ENUMERABLE（表示使用可枚举方式实现）
  //   - "EnumerableSortRule"：指定规则的描述名称
  // withRuleFactory方法指定规则工厂：使用方法引用EnumerableSortRule::new创建规则实例
  public static final Config DEFAULT_CONFIG = Config.INSTANCE
      .withConversion(Sort.class, Convention.NONE,
          EnumerableConvention.INSTANCE, "EnumerableSortRule")
      .withRuleFactory(EnumerableSortRule::new);

  /** Called from the Config. */
  // 定义受保护的构造方法，接收Config参数
  // 这个构造方法由Config调用，用于创建EnumerableSortRule实例
  // super(config)调用父类ConverterRule的构造方法，传入配置对象
  protected EnumerableSortRule(Config config) {
    super(config);
  }

  // 重写父类ConverterRule的convert方法，实现具体的转换逻辑
  // @Nullable注解表示该方法可能返回null
  // 参数RelNode rel：要转换的关系节点，这里应该是Sort类型的节点
  // 返回值RelNode：转换后的节点，如果无法转换则返回null
  @Override public @Nullable RelNode convert(RelNode rel) {
    // 将输入的RelNode强制转换为Sort类型，因为此规则只处理Sort节点
    final Sort sort = (Sort) rel;
    // 检查Sort节点是否包含offset（偏移量）或fetch（限制行数）
    // 如果存在offset或fetch，说明这是带有LIMIT和OFFSET的查询
    // 这种情况下不进行转换，返回null，让其他规则（如EnumerableLimitSortRule）来处理
    // offset表示跳过的行数（对应SQL的OFFSET子句）
    // fetch表示返回的最大行数（对应SQL的LIMIT子句）
    if (sort.offset != null || sort.fetch != null) {
      return null;
    }
    // 获取Sort节点的输入RelNode，即要排序的数据源
    final RelNode input = sort.getInput();
    // 创建并返回EnumerableSort节点，这是可枚举约定下的物理排序实现
    // EnumerableSort.create方法创建EnumerableSort实例，参数包括：
    //   1. convert(input, input.getTraitSet().replace(EnumerableConvention.INSTANCE))：
    //      先将输入节点转换为EnumerableConvention约定
    //      input.getTraitSet()获取输入节点的特征集合
    //      .replace(EnumerableConvention.INSTANCE)将约定替换为ENUMERABLE
    //      convert方法递归地将输入节点转换为可枚举约定
    //   2. sort.getCollation()：获取排序的排序规则（排序键和排序方向）
    //   3. null：offset参数设为null，表示不跳过行
    //   4. null：fetch参数设为null，表示不限制行数
    // 这样创建的EnumerableSort只负责排序，不负责分页
    return EnumerableSort.create(
        convert(
            input,
            input.getTraitSet().replace(EnumerableConvention.INSTANCE)),
        sort.getCollation(),
        null,
        null);
  }
}
