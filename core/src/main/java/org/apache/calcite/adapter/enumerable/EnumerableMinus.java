/*  Apache 软件基金会许可证声明,说明该代码遵循 Apache 2.0 许可证,允许在特定条件下使用、修改和分发 */
/*  Licensed to the Apache Software Foundation (ASF) under one or more */
/*  贡献者许可协议,用于声明版权归属信息 */
/*  contributor license agreements.  See the NOTICE file distributed with */
/*  本文件以获取更多关于版权所有权的信息 */
/*  this work for additional information regarding copyright ownership.  */
/*  ASF 根据本许可证将文件授予您使用,除非您同意遵守许可证条款,否则不得使用本文件 */
/*  The ASF licenses this file to you under the Apache License, Version 2.0 */
/*  指定许可证版本为 2.0 */
/*  (the "License"); you may not use this file except in compliance with */
/*  您可以在以下地址获取许可证副本 */
/*  the License.  You may obtain a copy of the License at */
/*  Apache 官方网站地址 */
/*   * */
/*  http://www.apache.org/licenses/LICENSE-2.0 */
/*  除非适用法律要求或书面同意,否则按"原样"分发软件,不提供任何明示或暗示的担保 */
/*   * */
/*  Unless required by applicable law or agreed to in writing, software */
/*  distributed under the License is distributed on an "AS IS" BASIS, */
/*  不提供任何形式的担保,包括但不限于适销性、特定用途适用性和非侵权性的担保 */
/*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  */
/*  详见许可证文件中关于权限和限制的具体语言描述 */
/*  See the License for the specific language governing permissions and */
/*  许可证限制说明 */
/*  limitations under the License.  */
/*  声明本类属于 org.apache.calcite.adapter.enumerable 包,这是 Calcite 框架中用于可枚举调用约定的适配器包 */
package org.apache.calcite.adapter.enumerable;

/*  导入 Ord 类,用于在遍历集合时获取元素的索引信息,Ordinal 的缩写 */
import org.apache.calcite.linq4j.Ord;
/*  导入 BlockBuilder 类,用于构建代码块,是生成 LINQ 表达式树的工具类 */
import org.apache.calcite.linq4j.tree.BlockBuilder;
/*  导入 Expression 类,表示一个表达式,是 LINQ 表达式树的基本构建单元 */
import org.apache.calcite.linq4j.tree.Expression;
/*  导入 Expressions 类,提供创建各种表达式的方法,如方法调用、常量等 */
import org.apache.calcite.linq4j.tree.Expressions;
/*  导入 RelOptCluster 类,表示关系代数优化器的集群,包含优化器上下文信息 */
import org.apache.calcite.plan.RelOptCluster;
/*  导入 RelTraitSet 类,表示关系节点的特征集合,如约定、排序等 */
import org.apache.calcite.plan.RelTraitSet;
/*  导入 RelNode 接口,表示关系代数树中的节点,是所有关系操作的基础接口 */
import org.apache.calcite.rel.RelNode;
/*  导入 Minus 类,表示集合差操作的关系节点,即 EXCEPT 操作 */
import org.apache.calcite.rel.core.Minus;
/*  导入 BuiltInMethod 类,包含内置方法的枚举,用于生成调用这些方法的表达式 */
import org.apache.calcite.util.BuiltInMethod;

/*  导入 Java 标准库的 List 接口,用于存储有序的元素集合 */
import java.util.List;

/*  静态导入 requireNonNull 方法,用于检查对象是否为 null,如果为 null 则抛出 NullPointerException */
import static java.util.Objects.requireNonNull;

/*  类的 JavaDoc 注释:说明这是 Minus 操作在可枚举调用约定(EnumerableConvention)下的实现 */
/*  Minus 是 SQL 中的 EXCEPT 操作,用于返回第一个查询结果中不存在于第二个查询结果中的行 */
/** Implementation of {@link org.apache.calcite.rel.core.Minus} in */
/*  说明该类实现了可枚举调用约定,这意味着它可以生成 LINQ 风格的 Java 代码来执行查询 */
/** {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}. */
/*  EnumerableMinus 类声明,继承自 Minus 类并实现 EnumerableRel 接口 */
/*  Minus 类提供了差集操作的逻辑,EnumerableRel 接口提供了可枚举实现的能力 */
public class EnumerableMinus extends Minus implements EnumerableRel {
  /*  构造方法,用于创建 EnumerableMinus 实例 */
  /*  参数 cluster: 关系优化器集群,包含类型工厂等共享资源 */
  /*  参数 traitSet: 关系节点的特征集合,如调用约定、排序等 */
  /*  参数 inputs: 输入关系节点列表,即参与差集操作的多个查询结果 */
  /*  参数 all: 是否保留重复行,true 表示 EXCEPT ALL,false 表示 EXCEPT DISTINCT */
  public EnumerableMinus(RelOptCluster cluster, RelTraitSet traitSet,
      List<RelNode> inputs, boolean all) {
    /*  调用父类 Minus 的构造方法,初始化父类的成员变量 */
    /*  将 cluster、traitSet、inputs 和 all 参数传递给父类,完成基本初始化 */
    super(cluster, traitSet, inputs, all);
  }

  /*  复制方法,用于创建当前关系节点的副本,可以修改特征集、输入或 all 标志 */
  /*  参数 traitSet: 新的特征集合,可能包含不同的调用约定或其他特征 */
  /*  参数 inputs: 新的输入关系节点列表 */
  /*  参数 all: 新的 all 标志,决定是否保留重复行 */
  /*  返回值: 返回新的 EnumerableMinus 实例,是当前节点的副本 */
  @Override public EnumerableMinus copy(RelTraitSet traitSet, List<RelNode> inputs,
      boolean all) {
    /*  创建并返回新的 EnumerableMinus 实例 */
    /*  使用当前节点的集群(getCluster())和传入的新参数来构造副本 */
    /*  这样可以保持集群不变,但允许修改其他属性 */
    return new EnumerableMinus(getCluster(), traitSet, inputs, all);
  }

  /*  实现方法,用于将关系节点转换为可执行的代码块 */
  /*  参数 implementor: 关系实现器,负责生成执行代码并管理类型系统 */
  /*  参数 pref: 偏好设置,用于指定行格式和其他实现偏好 */
  /*  返回值: 返回 Result 对象,包含生成的代码块和物理类型信息 */
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) {
    /*  创建代码块构建器,用于逐步构建执行代码 */
    /*  BlockBuilder 会自动管理变量的声明和作用域 */
    final BlockBuilder builder = new BlockBuilder();
    /*  初始化差集表达式为 null,将在循环中逐步构建 */
    /*  minusExp 最终将表示整个差集操作的执行表达式 */
    Expression minusExp = null;
    /*  遍历所有输入关系节点,Ord.zip 会为每个元素添加索引 */
    /*  ord.i 是索引,ord.e 是对应的关系节点 */
    for (Ord<RelNode> ord : Ord.zip(inputs)) {
      /*  将当前输入节点转换为 EnumerableRel 类型 */
      /*  因为只有实现了 EnumerableRel 接口的节点才能生成可执行代码 */
      EnumerableRel input = (EnumerableRel) ord.e;
      /*  访问子节点并生成其实现代码 */
      /*  implementor.visitChild 会递归地调用子节点的 implement 方法 */
      /*  this 是当前节点,ord.i 是子节点索引,input 是子节点,pref 是偏好设置 */
      final Result result = implementor.visitChild(this, ord.i, input, pref);
      /*  将子节点的代码块添加到构建器中,并获取对应的表达式 */
      /*  "child" + ord.i 是变量名,如 child0、child1 等 */
      /*  result.block 是子节点生成的代码块,会被赋值给这个变量 */
      Expression childExp =
          builder.append(
              "child" + ord.i,
              result.block);
      /*  检查 childExp 是否为 null,如果为 null 则抛出异常 */
      /*  这是防御性编程,确保后续代码不会因为 null 值而失败 */
      requireNonNull(childExp, "childExp");

      /*  如果这是第一个输入节点,则直接将其表达式赋值给 minusExp */
      /*  第一个输入作为差集操作的基准集合 */
      if (minusExp == null) {
        minusExp = childExp;
      } else {
        /*  如果不是第一个输入节点,则生成 EXCEPT 方法调用表达式 */
        /*  这将当前累积的差集结果(minusExp)与新的子节点(childExp)进行差集运算 */
        minusExp =
            Expressions.call(minusExp,
                /*  调用内置的 EXCEPT 方法,该方法实现了差集逻辑 */
                BuiltInMethod.EXCEPT.method,
                /*  构建参数列表 */
                Expressions.list(childExp)
                    /*  添加比较器,用于比较行是否相等 */
                    /*  comparer() 可能返回 null,只有非 null 时才添加 */
                    .appendIfNotNull(result.physType.comparer())
                    /*  添加 all 标志,决定是否保留重复行 */
                    .append(Expressions.constant(all)));
      }

      /*  一旦第一个输入选择了其格式,要求其他输入使用相同的格式 */
      /*  pref.of(result.format) 会更新偏好设置,使用第一个输入的格式 */
      /*  Once the first input has chosen its format, ask for the same for */
      /*  other inputs. */
      pref = pref.of(result.format);
    }

    /*  将最终的差集表达式添加到代码块构建器中 */
    /*  requireNonNull 确保 minusExp 不为 null,否则抛出异常 */
    /*  lambda 表达式用于生成详细的错误信息,包含输入列表和当前节点信息 */
    builder.add(
        requireNonNull(minusExp, () -> "minusExp is null, inputs=" + inputs + ", rel=" + this));
    /*  创建物理类型对象,用于描述输出行的 Java 类型表示 */
    /*  implementor.getTypeFactory() 获取类型工厂 */
    /*  getRowType() 获取逻辑行类型 */
    /*  pref.prefer(JavaRowFormat.CUSTOM) 根据偏好选择行格式,默认为 CUSTOM */
    final PhysType physType =
        PhysTypeImpl.of(
            implementor.getTypeFactory(),
            getRowType(),
            pref.prefer(JavaRowFormat.CUSTOM));
    /*  返回实现结果,包含物理类型和生成的代码块 */
    /*  implementor.result 会创建 Result 对象,将所有信息打包返回 */
    return implementor.result(physType, builder.toBlock());
  }
}
