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
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于可枚举适配器包，用于将逻辑算子转换为可执行的枚举形式

import org.apache.calcite.plan.RelOptRuleCall; // 导入优化规则调用类，用于在优化器中调用规则
import org.apache.calcite.plan.RelRule; // 导入关系规则基类，所有优化规则的父类
import org.apache.calcite.rel.RelCollation; // 导入关系排序信息类，描述字段的排序方式
import org.apache.calcite.rel.RelFieldCollation; // 导入字段排序信息类，描述单个字段的排序方向等
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，表示关系代数中的一个操作节点
import org.apache.calcite.rel.core.Sort; // 导入排序操作类，表示排序操作
import org.apache.calcite.rel.core.Union; // 导入并集操作类，表示并集操作
import org.apache.calcite.rel.logical.LogicalSort; // 导入逻辑排序类，表示逻辑层面的排序操作
import org.apache.calcite.rel.logical.LogicalUnion; // 导入逻辑并集类，表示逻辑层面的并集操作
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型类，描述字段的数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段类，描述字段的信息
import org.apache.calcite.rex.RexLiteral; // 导入Rex字面量类，表示常量表达式
import org.apache.calcite.rex.RexNode; // 导入Rex节点接口，表示行表达式
import org.apache.calcite.tools.RelBuilder; // 导入关系构建器类，用于构建关系表达式树
import org.apache.calcite.util.ImmutableBitSet; // 导入不可变位集合类，用于高效地标记一组索引

import org.immutables.value.Value; // 导入不可变值注解，用于生成不可变的配置类

import java.util.ArrayList; // 导入动态数组类，用于存储可变长度的列表
import java.util.List; // 导入列表接口，表示有序的元素集合
import java.util.Objects; // 导入对象工具类，用于对象比较等操作

/**
 * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalSort} on top of a
 * {@link org.apache.calcite.rel.logical.LogicalUnion} into a {@link EnumerableMergeUnion}.
 * 该规则用于将 LogicalSort（逻辑排序）节点下方的 LogicalUnion（逻辑并集）节点
 * 转换为 EnumerableMergeUnion（可枚举归并并集）节点，从而优化查询性能
 * 
 * 核心思想：当需要对并集操作的结果进行排序时，传统的做法是先执行并集，再对结果排序
 * 该规则通过将排序下推到并集的每个输入上，然后使用归并排序的方式合并有序的输入，
 * 可以显著提高性能，因为归并排序的时间复杂度是 O(n)，而普通排序是 O(n log n)
 *
 * @see EnumerableRules#ENUMERABLE_MERGE_UNION_RULE
 */
@Value.Enclosing // 声明该类包含不可变值配置类，由Immutables库自动生成实现
public class EnumerableMergeUnionRule extends RelRule<EnumerableMergeUnionRule.Config> { // 定义可枚举归并并集规则类，继承自RelRule基类，泛型参数为配置类型

  /** Rule configuration. */
  @Value.Immutable // 声明该接口为不可变值接口，Immutables库会自动生成实现类
  public interface Config extends RelRule.Config { // 定义规则配置接口，继承自RelRule.Config，用于配置规则的行为
    Config DEFAULT_CONFIG = ImmutableEnumerableMergeUnionRule.Config.of() // 创建默认配置实例，使用Immutables生成的工厂方法
        .withDescription("EnumerableMergeUnionRule").withOperandSupplier( // 设置规则描述为"EnumerableMergeUnionRule"，并设置操作数提供器
            b0 -> b0.operand(LogicalSort.class).oneInput( // 定义规则匹配模式：匹配LogicalSort节点，该节点有一个输入
                b1 -> b1.operand(LogicalUnion.class).anyInputs())); // LogicalSort的输入是LogicalUnion节点，LogicalUnion可以有任意数量的输入

    @Override default EnumerableMergeUnionRule toRule() { // 重写toRule方法，用于将配置转换为规则实例
      return new EnumerableMergeUnionRule(this); // 创建并返回新的EnumerableMergeUnionRule实例，传入当前配置
    }
  }

  public EnumerableMergeUnionRule(Config config) { // 构造方法，接收配置对象作为参数
    super(config); // 调用父类RelRule的构造方法，传入配置对象，完成规则初始化
  }

  @Override public boolean matches(RelOptRuleCall call) { // 重写matches方法，用于判断当前规则是否适用于给定的规则调用
    final Sort sort = call.rel(0); // 获取规则调用中的第0个关系节点，即LogicalSort节点
    final RelCollation collation = sort.getCollation(); // 获取排序节点的排序规则（Collation），包含排序字段和方向等信息
    if (collation == null || collation.getFieldCollations().isEmpty()) { // 如果排序规则为空或者没有字段排序信息
      return false; // 返回false，表示该规则不适用，因为没有排序需求
    }

    final Union union = call.rel(1); // 获取规则调用中的第1个关系节点，即LogicalUnion节点
    if (union.getInputs().size() < 2) { // 如果并集节点的输入数量小于2
      return false; // 返回false，表示该规则不适用，因为归并并集至少需要两个输入才能体现优势
    }

    return true; // 返回true，表示该规则适用，可以执行转换
  }

  @Override public void onMatch(RelOptRuleCall call) { // 重写onMatch方法，当规则匹配成功时执行实际的转换逻辑
    final Sort sort = call.rel(0); // 获取规则调用中的第0个关系节点，即LogicalSort节点
    final RelCollation collation = sort.getCollation(); // 获取排序节点的排序规则，用于后续下推排序
    final Union union = call.rel(1); // 获取规则调用中的第1个关系节点，即LogicalUnion节点
    final int unionInputsSize = union.getInputs().size(); // 获取并集节点的输入数量，用于后续循环处理

    // Push down sort limit, if possible.
    // 如果可能的话，将排序的LIMIT下推到并集的每个输入上，这样可以减少需要排序的数据量
    RexNode inputFetch = null; // 初始化inputFetch为null，表示默认不下推LIMIT
    if (sort.fetch != null) { // 如果排序节点有LIMIT子句（fetch表示限制返回的行数）
      if (sort.offset == null) { // 如果排序节点没有OFFSET子句（offset表示跳过的行数）
        inputFetch = sort.fetch; // 直接将LIMIT下推到输入上，因为不需要考虑OFFSET
      } else if (sort.fetch instanceof RexLiteral && sort.offset instanceof RexLiteral) { // 如果LIMIT和OFFSET都是字面量（常量）
        inputFetch = // 计算下推后的LIMIT值
            call.builder().literal(RexLiteral.intValue(sort.fetch) // 获取LIMIT的整数值
                + RexLiteral.intValue(sort.offset)); // 加上OFFSET的整数值，因为需要获取OFFSET+LIMIT行才能保证最终结果正确
      }
    }

    final RelBuilder builder = call.builder(); // 获取关系构建器实例，用于构建新的关系表达式树
    final List<RelDataTypeField> unionFieldList = union.getRowType().getFieldList(); // 获取并集节点的字段列表，用于类型检查和转换
    final List<RelNode> inputs = new ArrayList<>(unionInputsSize); // 创建关系节点列表，用于存储转换后的输入，初始容量为并集输入数量
    for (RelNode input : union.getInputs()) { // 遍历并集节点的每个输入
      // Check if type collations match, otherwise store it in this bitset to generate a cast later
      // to guarantee that all inputs will be sorted in the same way
      // 检查输入字段的排序规则是否与并集字段的排序规则匹配，如果不匹配则记录下来后续生成类型转换
      // 这样可以保证所有输入都以相同的方式排序，避免因排序规则不同导致归并结果错误
      final ImmutableBitSet.Builder fieldsRequiringCastBuilder = ImmutableBitSet.builder(); // 创建不可变位集合构建器，用于标记需要类型转换的字段索引
      for (RelFieldCollation fieldCollation : collation.getFieldCollations()) { // 遍历排序规则中的每个字段排序信息
        final int index = fieldCollation.getFieldIndex(); // 获取当前排序字段的索引
        final RelDataType unionType = unionFieldList.get(index).getType(); // 获取并集中该字段的数据类型
        final RelDataType inputType = input.getRowType().getFieldList().get(index).getType(); // 获取输入中该字段的数据类型
        if (!Objects.equals(unionType.getCollation(), inputType.getCollation())) { // 比较并集字段和输入字段的排序规则是否相同
          fieldsRequiringCastBuilder.set(index); // 如果不同，则将该字段索引添加到位集合中，标记需要类型转换
        }
      }
      final ImmutableBitSet fieldsRequiringCast = fieldsRequiringCastBuilder.build(); // 构建不可变位集合，包含所有需要类型转换的字段索引
      final RelNode unsortedInput; // 声明未排序的输入节点变量
      if (fieldsRequiringCast.isEmpty()) { // 如果没有字段需要类型转换
        unsortedInput = input; // 直接使用原始输入，无需类型转换
      } else { // 如果有字段需要类型转换
        // At least one cast is required, generate the corresponding projection
        // 至少需要一个类型转换，生成相应的投影表达式来执行类型转换
        builder.push(input); // 将输入节点压入关系构建器栈中
        final List<RexNode> fields = builder.fields(); // 获取输入的所有字段表达式
        final List<RexNode> projFields = new ArrayList<>(fields.size()); // 创建投影字段列表，用于存储转换后的字段表达式
        for (int i = 0; i < fields.size(); i++) { // 遍历所有字段
          RexNode node = fields.get(i); // 获取当前字段的表达式
          if (fieldsRequiringCast.get(i)) { // 如果当前字段需要类型转换
            final RelDataType targetType = unionFieldList.get(i).getType(); // 获取目标类型（并集字段的类型）
            node = builder.getRexBuilder().makeCast(targetType, node); // 创建类型转换表达式，将字段转换为目标类型
          }
          projFields.add(node); // 将字段表达式（可能已转换）添加到投影字段列表中
        }
        builder.project(projFields); // 使用投影字段列表创建投影节点，实现类型转换
        unsortedInput = builder.build(); // 构建完成后的节点作为未排序的输入
      }
      final RelNode newInput = // 创建新的输入节点，将排序下推到输入上
          sort.copy(sort.getTraitSet(), unsortedInput, collation, null, inputFetch); // 复制排序节点，应用到未排序的输入上，使用相同的排序规则，offset为null（已处理），fetch为下推的LIMIT
      inputs.add( // 将转换后的输入添加到输入列表中
          convert(call.getPlanner(), newInput, // 将新输入转换为可枚举约定，使其可以被EnumerableMergeUnion使用
              newInput.getTraitSet().replace(EnumerableConvention.INSTANCE))); // 替换特征集为可枚举约定
    }

    RelNode result = EnumerableMergeUnion.create(sort.getCollation(), inputs, union.all); // 创建EnumerableMergeUnion节点，传入排序规则、转换后的输入列表和是否保留重复标志

    // If Sort contained a LIMIT / OFFSET, then put it back as an EnumerableLimit.
    // The output of the MergeUnion is already sorted, so we do not need a sort anymore.
    // 如果原始排序节点包含LIMIT或OFFSET，则需要添加一个EnumerableLimit节点来应用这些限制
    // 因为MergeUnion的输出已经是有序的，所以不需要再添加排序节点
    if (sort.offset != null || sort.fetch != null) { // 如果排序节点有OFFSET或LIMIT
      result = EnumerableLimit.create(result, sort.offset, sort.fetch); // 创建EnumerableLimit节点，应用到MergeUnion的输出上，保留原始的OFFSET和LIMIT
    }

    call.transformTo(result); // 将原始子树转换为新的结果节点，完成规则转换
  }
}
