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
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于org.apache.calcite.adapter.enumerable包，这是Calcite的可枚举适配器包

import org.apache.calcite.linq4j.Ord; // 导入Ord类，用于为集合中的元素添加索引，方便在循环中同时获取元素和索引
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入BlockBuilder类，用于构建Java代码块，是LINQ4J表达式树的构建工具
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类，表示Java表达式，是LINQ4J表达式树的基础类型
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions工具类，提供了创建各种表达式对象的静态方法
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系代数优化集群，包含了查询优化所需的各种共享资源
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式特征集合，定义了物理属性如约定、排序等
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式，是Calcite中所有关系操作节点的基类
import org.apache.calcite.rel.core.Intersect; // 导入Intersect类，表示集合交集操作，是关系代数中的INTERSECT操作符
import org.apache.calcite.util.BuiltInMethod; // 导入BuiltInMethod枚举，定义了Calcite内置的LINQ方法，如INTERSECT等

import java.util.List; // 导入List接口，表示有序集合，用于存储输入的关系节点列表

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于检查对象引用是否为null

/**
 * Implementation of {@link org.apache.calcite.rel.core.Intersect} in
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}.
 * 这是Intersect操作在可枚举调用约定下的实现类
 * Intersect是SQL中的集合交集操作，用于返回两个或多个查询结果的交集
 * 可枚举调用约定（EnumerableConvention）意味着该操作可以被转换为Java代码执行
 * 该类负责将逻辑层面的INTERSECT操作转换为物理层面的可执行代码
 */
public class EnumerableIntersect extends Intersect implements EnumerableRel { // 定义类EnumerableIntersect，继承自Intersect基类，实现EnumerableRel接口
  /**
   * 构造方法
   * @param cluster 关系优化集群，包含了查询优化所需的共享资源如类型工厂、RexBuilder等
   * @param traitSet 关系特征集合，定义了该节点的物理属性，如约定、排序、分布等
   * @param inputs 输入关系节点列表，INTERSECT操作需要两个或多个输入集合
   * @param all 是否保留重复行，true表示INTERSECT ALL（保留重复），false表示INTERSECT DISTINCT（去重）
   */
  public EnumerableIntersect(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法接收集群、特征集合、输入列表和all标志
      List<RelNode> inputs, boolean all) { // 输入列表和是否保留重复行标志
    super(cluster, traitSet, inputs, all); // 调用父类Intersect的构造方法，初始化基类成员变量
  }

  /**
   * 复制方法，用于创建当前节点的一个副本
   * 在查询优化过程中，经常需要创建节点的副本进行转换
   * @param traitSet 新的特征集合，可以与当前节点的特征集不同，用于优化转换
   * @param inputs 新的输入节点列表，可以与当前输入不同
   * @param all 新的all标志，可以与当前all标志不同
   * @return 返回一个新的EnumerableIntersect实例，包含指定的特征集、输入列表和all标志
   */
  @Override public EnumerableIntersect copy(RelTraitSet traitSet, List<RelNode> inputs, // 重写copy方法，接收新的特征集、输入列表和all标志
      boolean all) { // all标志参数
    return new EnumerableIntersect(getCluster(), traitSet, inputs, all); // 创建并返回一个新的EnumerableIntersect实例，使用当前集群和传入的参数
  }

  /**
   * 实现方法，负责将该关系节点转换为可执行的Java代码
   * 这是EnumerableRel接口的核心方法，用于生成LINQ表达式树
   * @param implementor 实现器，负责遍历关系树并生成代码
   * @param pref 偏好设置，表示对输出格式的偏好，如数组、列表、自定义等
   * @return 返回Result对象，包含了生成的代码块和物理类型信息
   */
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 重写implement方法，接收实现器和偏好参数
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于构建生成的Java代码块
    Expression intersectExp = null; // 声明交集表达式变量，初始为null，用于累积多个输入的交集操作
    for (Ord<RelNode> ord : Ord.zip(inputs)) { // 遍历所有输入节点，Ord.zip为每个元素添加索引，ord.i是索引，ord.e是元素
      EnumerableRel input = (EnumerableRel) ord.e; // 将当前输入节点强制转换为EnumerableRel类型，确保它是可枚举的
      final Result result = implementor.visitChild(this, ord.i, input, pref); // 访问子节点，生成子节点的可执行代码，返回结果包含代码块和物理类型
      Expression childExp = // 声明子表达式变量
          builder.append( // 将子节点的代码块添加到构建器中，并返回对应的表达式
              "child" + ord.i, // 为子表达式生成变量名，如child0, child1等
              result.block); // 子节点生成的代码块

      if (intersectExp == null) { // 如果是第一个输入
        intersectExp = childExp; // 直接将第一个子表达式赋值给intersectExp，作为初始值
      } else { // 如果不是第一个输入
        intersectExp = // 更新intersectExp，调用INTERSECT方法计算与当前子表达式的交集
            Expressions.call(intersectExp, // 调用表达式对象的方法，第一个参数是目标表达式（之前的累积结果）
                BuiltInMethod.INTERSECT.method, // 使用内置的INTERSECT方法，该方法实现了集合交集操作
                Expressions.list(childExp) // 创建表达式列表，包含当前子表达式
                    .appendIfNotNull(result.physType.comparer()) // 如果物理类型有比较器，则添加比较器参数，用于元素比较
                    .append(Expressions.constant(all))); // 添加all标志参数，表示是否保留重复行
      }

      // Once the first input has chosen its format, ask for the same for
      // other inputs.
      // 一旦第一个输入选择了它的格式，要求其他输入使用相同的格式
      // 这样可以确保所有输入的格式一致，便于进行交集操作
      pref = pref.of(result.format); // 更新偏好设置，使用当前子节点的输出格式，确保后续输入使用相同格式
    }

    builder.add(requireNonNull(intersectExp, "intersectExp")); // 将最终的交集表达式添加到代码块中，检查不为null
    final PhysType physType = // 声明物理类型变量，用于描述输出的Java类型
        PhysTypeImpl.of( // 创建物理类型实现
            implementor.getTypeFactory(), // 使用实现器的类型工厂，用于创建Java类型
            getRowType(), // 获取当前节点的行类型，定义了输出的字段结构
            pref.prefer(JavaRowFormat.CUSTOM)); // 根据偏好选择行格式，CUSTOM表示自定义格式
    return implementor.result(physType, builder.toBlock()); // 返回结果对象，包含物理类型和构建好的代码块
  }
} // 类定义结束
