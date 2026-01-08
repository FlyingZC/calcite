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
package org.apache.calcite.adapter.enumerable; // 包声明:定义该类属于org.apache.calcite.adapter.enumerable包,该包包含可枚举适配器的实现

import org.apache.calcite.linq4j.Ord; // 导入Ord类:用于将集合转换为带索引的有序集合,方便在迭代时获取元素的索引位置
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入BlockBuilder类:用于构建Java代码块,是生成LINQ表达式树的核心工具类
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类:表示LINQ表达式树的节点基类,用于构建表达式树
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions类:提供静态工厂方法用于创建各种类型的表达式节点
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入ParameterExpression类:表示方法参数的表达式节点类型
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类:表示关系代数优化集群,包含优化器需要的上下文信息和共享资源
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类:表示关系节点的特征集合,如排序规则、调用约定等物理属性
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类:表示关系数据的排序规则,定义了字段的排序顺序和方向
import org.apache.calcite.rel.RelNode; // 导入RelNode接口:表示关系代数树中的节点基类,所有关系操作都继承自该接口
import org.apache.calcite.util.BuiltInMethod; // 导入BuiltInMethod类:枚举Calcite内置的方法,用于在表达式树中引用预定义的方法
import org.apache.calcite.util.Pair; // 导入Pair类:表示不可变的键值对,用于存储两个相关联的值
import org.apache.calcite.util.Util; // 导入Util类:提供各种工具方法,如获取默认值、空值检查等通用功能

import java.util.ArrayList; // 导入ArrayList类:动态数组实现,用于存储可变长度的元素列表
import java.util.List; // 导入List接口:表示有序集合接口,定义了列表的基本操作方法

/** Implementation of {@link org.apache.calcite.rel.core.Union} in
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}.
 * 这是Union操作在可枚举调用约定下的实现类,Union操作用于合并多个输入结果集
 *
 * <p>Performs a union (or union all) of all its inputs (which must be already
 * sorted), respecting the order. */
// 该类执行所有输入的union或union all操作,所有输入必须已经按照指定的排序规则排序,并且输出结果会保持这个排序顺序
public class EnumerableMergeUnion extends EnumerableUnion { // 类定义:继承自EnumerableUnion,实现可枚举的合并union操作
  // 该类没有显式定义成员变量,所有成员变量都继承自父类EnumerableUnion,包括:
  // - cluster: RelOptCluster类型,关系优化集群,包含优化器上下文
  // - traitSet: RelTraitSet类型,特征集合,包含物理属性如排序规则、调用约定等
  // - inputs: List<RelNode>类型,输入关系节点列表,代表要union的所有输入
  // - all: boolean类型,标志是否执行union all(包含重复行)还是union(去重)

  protected EnumerableMergeUnion(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法:创建一个EnumerableMergeUnion实例,参数包括优化集群、特征集合、输入节点列表和是否union all标志
      List<RelNode> inputs, boolean all) { // inputs参数:要union的所有输入关系节点列表,all参数:true表示union all(保留重复),false表示union(去重)
    super(cluster, traitSet, inputs, all); // 调用父类EnumerableUnion的构造方法,初始化继承的成员变量
    final RelCollation collation = traitSet.getCollation(); // 从特征集合中获取排序规则collation,定义了输出结果应该按照哪些字段以及什么顺序排序
    if (collation == null || collation.getFieldCollations().isEmpty()) { // 检查排序规则是否为空或没有字段排序定义,如果是则抛出异常
      throw new IllegalArgumentException("EnumerableMergeUnion with no collation"); // 抛出非法参数异常:EnumerableMergeUnion必须要有排序规则,因为它是基于排序的合并算法
    } // 结束if语句
    for (RelNode input : inputs) { // 遍历所有输入节点,检查每个输入是否满足所需的排序规则
      final RelCollation inputCollation = input.getTraitSet().getCollation(); // 获取当前输入节点的排序规则
      if (inputCollation == null || !inputCollation.satisfies(collation)) { // 检查输入的排序规则是否为空,或者是否满足所需的排序规则(satisfies方法判断输入排序是否包含或兼容所需排序)
        throw new IllegalArgumentException("EnumerableMergeUnion input does " // 抛出非法参数异常:输入不满足排序要求
            + "not satisfy collation. EnumerableMergeUnion collation: " // 异常消息:说明union操作需要的排序规则
            + collation + ". Input collation: " + inputCollation + ". Input: " // 异常消息:说明输入实际的排序规则和输入节点信息
            + input); // 异常消息:拼接输入节点信息
      } // 结束if语句
    } // 结束for循环
  } // 结束构造方法

  public static EnumerableMergeUnion create(RelCollation collation, // 静态工厂方法:创建EnumerableMergeUnion实例,参数包括排序规则、输入列表和union all标志
      List<RelNode> inputs, boolean all) { // inputs参数:输入关系节点列表,all参数:是否执行union all
    final RelOptCluster cluster = inputs.get(0).getCluster(); // 从第一个输入节点获取优化集群RelOptCluster,所有输入应该共享同一个集群
    final RelTraitSet traitSet = // 创建特征集合traitSet
        cluster.traitSetOf(EnumerableConvention.INSTANCE).replace(collation); // 首先创建包含EnumerableConvention调用约定的特征集合,然后替换为指定的排序规则collation
    return new EnumerableMergeUnion(cluster, traitSet, inputs, all); // 调用构造方法创建并返回EnumerableMergeUnion实例
  } // 结束静态工厂方法

  @Override public EnumerableMergeUnion copy(RelTraitSet traitSet, // 重写copy方法:创建当前节点的副本,可以修改特征集合、输入列表和union all标志
      List<RelNode> inputs, boolean all) { // traitSet参数:新的特征集合,inputs参数:新的输入列表,all参数:新的union all标志
    return new EnumerableMergeUnion(getCluster(), traitSet, inputs, all); // 调用构造方法创建新的EnumerableMergeUnion实例,使用当前节点的集群和新的参数
  } // 结束copy方法

  @Override public Result implement(EnumerableRelImplementor implementor, // 重写implement方法:实现该关系节点为可执行的表达式树,生成Java代码
      Prefer pref) { // implementor参数:关系实现器,负责访问子节点和生成代码,pref参数:偏好设置,指示如何生成代码(如行格式偏好)
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器BlockBuilder,用于逐步构建Java代码块

    final ParameterExpression inputListExp = // 创建参数表达式inputListExp,代表一个List类型的变量,用于存储所有输入的枚举结果
        Expressions.parameter(List.class, // 参数类型为List,表示这是一个列表对象
            builder.newName("mergeUnionInputs" // 使用BlockBuilder生成唯一的变量名,前缀为"mergeUnionInputs"
                + Integer.toUnsignedString(this.getId()))); // 追加当前节点的ID作为后缀,确保变量名唯一
    builder.add( // 向代码块中添加一个变量声明语句
        Expressions.declare(0, inputListExp, Expressions.new_(ArrayList.class))); // 声明inputListExp变量,初始化为new ArrayList(),0表示不使用修饰符(public/private等)

    for (Ord<RelNode> ord : Ord.zip(inputs)) { // 使用Ord.zip将inputs转换为带索引的有序集合,ord包含索引i和元素e
      final EnumerableRel input = (EnumerableRel) ord.e; // 将当前输入节点转换为EnumerableRel类型,因为只有可枚举关系才能实现为代码
      final Result result = implementor.visitChild(this, ord.i, input, pref); // 调用实现器的visitChild方法访问子节点,生成子节点的可执行代码,返回Result对象包含生成的代码块
      final Expression childExp = builder.append("child" + ord.i, result.block); // 将子节点的代码块追加到当前代码块中,生成一个表达式childExp,变量名为"child"加上索引
      builder.add( // 向代码块中添加一个语句
          Expressions.statement( // 创建语句表达式
              Expressions.call(inputListExp, // 调用inputListExp(即ArrayList)的方法
                  BuiltInMethod.COLLECTION_ADD.method, childExp))); // 调用add方法,将childExp添加到inputListExp列表中,即收集所有子节点的结果
    } // 结束for循环

    final PhysType physType = // 创建物理类型PhysType,描述行数据的物理表示方式(如Java类型、格式等)
        PhysTypeImpl.of(implementor.getTypeFactory(), getRowType(), // 使用实现器的类型工厂和当前节点的行类型创建PhysType
            pref.prefer(JavaRowFormat.CUSTOM)); // 根据偏好设置选择行格式,CUSTOM表示自定义格式,优先选择最合适的格式

    final RelCollation collation = getTraitSet().getCollation(); // 获取当前节点的排序规则collation
    if (collation == null || collation.getFieldCollations().isEmpty()) { // 检查排序规则是否为空,理论上不应该发生,因为构造方法已经验证过
      // should not happen // 注释:这种情况不应该发生
      throw new IllegalStateException("EnumerableMergeUnion with no collation"); // 抛出非法状态异常:排序规则缺失
    } // 结束if语句
    final Pair<Expression, Expression> pair = // 生成排序键对,Pair包含两个表达式:排序键选择器和排序比较器
        physType.generateCollationKey(collation.getFieldCollations()); // 根据排序规则的字段排序定义生成排序键表达式
    final Expression sortKeySelector = pair.left; // 获取Pair的第一个元素:排序键选择器表达式,用于从每行数据中提取排序键
    final Expression sortComparator = pair.right; // 获取Pair的第二个元素:排序比较器表达式,用于比较两个排序键的大小

    final Expression equalityComparator = // 创建相等比较器表达式,用于判断两行数据是否相等(用于union去重)
        Util.first(physType.comparer(), // 首先尝试使用PhysType的comparer方法获取比较器
            Expressions.call(BuiltInMethod.IDENTITY_COMPARER.method)); // 如果comparer返回null,则使用恒等比较器(直接使用==比较)

    final Expression unionExp = // 创建union操作的表达式
        Expressions.call(BuiltInMethod.MERGE_UNION.method, inputListExp, // 调用MERGE_UNION内置方法,传入输入列表inputListExp
            sortKeySelector, sortComparator, // 传入排序键选择器和排序比较器,用于保持合并后的排序顺序
            Expressions.constant(all, boolean.class), equalityComparator); // 传入all常量(boolean类型)表示是否union all,以及相等比较器用于去重
    builder.add(unionExp); // 将union表达式添加到代码块中,这是最终生成的代码

    return implementor.result(physType, builder.toBlock()); // 调用实现器的result方法,使用物理类型和构建的代码块创建Result对象并返回
  } // 结束implement方法
} // 结束类定义
