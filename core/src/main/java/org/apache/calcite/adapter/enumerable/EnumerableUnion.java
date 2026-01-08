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
// Apache许可证声明，说明代码的版权和使用条款

package org.apache.calcite.adapter.enumerable; // 声明包路径，该类位于org.apache.calcite.adapter.enumerable包下

import org.apache.calcite.linq4j.Ord; // 导入Ord类，用于为集合元素添加索引，方便在循环中同时获取元素和索引
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入BlockBuilder类，用于构建代码块，是LINQ4J表达式树构建工具
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类，表示LINQ4J中的表达式节点
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions类，提供创建各种表达式的静态工厂方法
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系代数优化器集群，包含查询优化所需的共享信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系节点的特征集合，如物理实现方式、排序等
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数树中的节点，是所有关系节点的基类
import org.apache.calcite.rel.core.Union; // 导入Union类，表示SQL中的UNION操作，用于合并多个查询结果
import org.apache.calcite.util.BuiltInMethod; // 导入BuiltInMethod类，定义了LINQ4J内置方法的枚举，如CONCAT、UNION等

import java.util.List; // 导入List接口，表示有序集合，用于存储输入的多个关系节点

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于检查对象是否为null

/** Implementation of {@link org.apache.calcite.rel.core.Union} in
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}. */
// 类注释：这是Union操作在EnumerableConvention（可枚举调用约定）下的实现
// EnumerableConvention是Calcite中的一种物理实现约定，表示该关系节点可以被转换为可枚举的Java集合
// 该类负责将UNION操作转换为可以执行的LINQ4J表达式树，最终生成Java代码来执行联合操作
public class EnumerableUnion extends Union implements EnumerableRel { // 声明EnumerableUnion类，继承自Union基类，实现EnumerableRel接口
  // 继承Union：获取UNION操作的语义，如是否保留重复行（UNION ALL）、输入列表等
  // 实现EnumerableRel：表示该节点可以被转换为可枚举的Java代码，提供implement方法来生成执行代码

  public EnumerableUnion(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法参数：cluster-优化器集群，包含类型工厂、表达式工厂等共享信息
      List<RelNode> inputs, boolean all) { // inputs-输入的关系节点列表，表示要UNION的多个查询结果；all-是否保留重复行，true表示UNION ALL，false表示UNION
    super(cluster, traitSet, inputs, all); // 调用父类Union的构造方法，初始化基类成员变量
  } // 构造方法结束，创建一个EnumerableUnion实例

  @Override public EnumerableUnion copy(RelTraitSet traitSet, List<RelNode> inputs, // 重写copy方法，用于复制当前节点并修改某些属性
      boolean all) { // 参数：traitSet-新的特征集合；inputs-新的输入列表；all-新的all标志
    return new EnumerableUnion(getCluster(), traitSet, inputs, all); // 创建并返回一个新的EnumerableUnion实例，保持相同的cluster，使用新的参数
  } // copy方法结束，用于在优化过程中创建修改后的节点副本

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 重写implement方法，核心方法：将当前UNION节点转换为可执行的LINQ4J表达式树
    // 参数：implementor-实现器，负责访问子节点和管理代码生成过程；pref-偏好设置，指示如何格式化输出行
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于构建最终的代码块表达式
    Expression unionExp = null; // 声明unionExp变量，用于累积构建UNION表达式，初始为null
    for (Ord<RelNode> ord : Ord.zip(inputs)) { // 遍历所有输入节点，Ord.zip为每个元素添加索引，ord包含索引i和元素e
      EnumerableRel input = (EnumerableRel) ord.e; // 将当前输入节点强制转换为EnumerableRel类型，因为只有可枚举节点才能被实现
      final Result result = implementor.visitChild(this, ord.i, input, pref); // 调用实现器访问子节点，生成子节点的执行代码
      // visitChild会递归地调用子节点的implement方法，返回Result对象包含生成的代码块和物理类型信息
      Expression childExp = // 声明childExp变量，用于存储子节点的表达式
          builder.append( // 将子节点的代码块添加到当前代码块构建器中
              "child" + ord.i, // 为子表达式生成变量名，如child0、child1等
              result.block); // 传入子节点生成的代码块，append返回该代码块的引用表达式

      if (unionExp == null) { // 如果是第一个输入节点
        unionExp = childExp; // 直接将第一个子节点的表达式赋值给unionExp，作为初始值
      } else { // 如果不是第一个输入节点，需要与之前的unionExp进行合并
        unionExp = all // 根据all标志决定使用CONCAT还是UNION方法
            ? Expressions.call(unionExp, BuiltInMethod.CONCAT.method, childExp) // 如果all为true，使用CONCAT方法，保留所有行（包括重复行）
            : Expressions.call(unionExp, // 如果all为false，使用UNION方法，去除重复行
                BuiltInMethod.UNION.method, // 调用LINQ4J的UNION方法，需要比较器来去重
                Expressions.list(childExp) // 创建表达式列表，包含子节点表达式
                    .appendIfNotNull(result.physType.comparer())); // 如果物理类型提供了比较器，则添加到参数列表中，用于去重比较
      } // if-else结束，完成当前节点与之前累积结果的合并
    } // for循环结束，所有输入节点都已合并到unionExp中

    builder.add(requireNonNull(unionExp, "unionExp")); // 将最终的unionExp表达式添加到代码块中，requireNonNull确保unionExp不为null
    final PhysType physType = // 创建物理类型对象，描述输出行的物理表示方式
        PhysTypeImpl.of( // 使用PhysTypeImpl工厂方法创建
            implementor.getTypeFactory(), // 使用实现器的类型工厂，确保类型一致性
            getRowType(), // 获取当前UNION节点的行类型（输出行的schema）
            pref.prefer(JavaRowFormat.CUSTOM)); // 根据偏好设置选择行格式，CUSTOM表示使用自定义格式
    return implementor.result(physType, builder.toBlock()); // 调用实现器的result方法，创建并返回Result对象
    // Result对象包含：物理类型信息、生成的代码块，这些信息会被父节点使用
  } // implement方法结束，完成了从UNION关系节点到可执行代码的转换
} // 类定义结束，EnumerableUnion类的完整实现
