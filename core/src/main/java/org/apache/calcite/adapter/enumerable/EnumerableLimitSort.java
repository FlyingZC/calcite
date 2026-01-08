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
// 声明包名，表明该类属于 org.apache.calcite.adapter.enumerable 包，这是 Calcite 框架中用于可枚举调用约定的适配器包
package org.apache.calcite.adapter.enumerable;

// 导入 BlockBuilder 类，用于构建代码块，是生成 LINQ 表达式树的核心工具类
import org.apache.calcite.linq4j.tree.BlockBuilder;
// 导入 Expression 类，表示 LINQ 表达式树中的表达式节点
import org.apache.calcite.linq4j.tree.Expression;
// 导入 Expressions 类，提供了创建各种表达式节点的静态工厂方法
import org.apache.calcite.linq4j.tree.Expressions;
// 导入 RelOptCluster 类，表示关系代数优化器的集群，包含所有共享的优化器信息
import org.apache.calcite.plan.RelOptCluster;
// 导入 RelTraitSet 类，表示关系节点的特征集合，如调用约定、排序方式等
import org.apache.calcite.plan.RelTraitSet;
// 导入 RelCollation 类，表示排序规则，定义了字段的排序顺序和方向
import org.apache.calcite.rel.RelCollation;
// 导入 RelNode 接口，表示关系代数树中的节点，是所有关系操作符的基接口
import org.apache.calcite.rel.RelNode;
// 导入 Sort 类，表示排序操作的关系节点，是本类的父类
import org.apache.calcite.rel.core.Sort;
// 导入 RexNode 类，表示行表达式，用于在关系代数中描述表达式
import org.apache.calcite.rex.RexNode;
// 导入 BuiltInMethod 类，定义了 Calcite 内置的方法，用于代码生成
import org.apache.calcite.util.BuiltInMethod;
// 导入 Pair 类，表示一个键值对，用于存储两个相关联的值
import org.apache.calcite.util.Pair;

// 导入 Nullable 注解，用于标记参数或返回值可以为 null
import org.checkerframework.checker.nullness.qual.Nullable;

// 静态导入 getExpression 方法，用于将 RexNode 转换为 Expression
import static org.apache.calcite.adapter.enumerable.EnumerableLimit.getExpression;

/**
 * EnumerableLimitSort 类的作用：
 * 
 * 1. 类概述：这是 {@link org.apache.calcite.rel.core.Sort} 在 {@link org.apache.calcite.adapter.enumerable.EnumerableConvention}
 *    可枚举调用约定中的实现类。
 * 
 * 2. 核心功能：专门优化带有 LIMIT（限制返回行数）和可选 OFFSET（偏移量）的排序操作。
 *    与普通的 Sort 不同，这个类能够利用 LIMIT 和 OFFSET 的特性进行优化，避免对整个数据集进行完全排序。
 * 
 * 3. 优化原理：当只需要排序后的前 N 条记录时，可以使用堆排序或优先队列等算法，
 *    只维护前 N 个元素，从而减少内存使用和计算时间。
 * 
 * 4. 使用场景：在 SQL 查询中，当出现 ORDER BY ... LIMIT ... OFFSET ... 这样的语句时，
 *    Calcite 会生成 EnumerableLimitSort 节点来执行这个操作。
 * 
 * 5. 实现方式：通过实现 EnumerableRel 接口，该类可以将关系代数树转换为可执行的 LINQ 表达式，
 *    最终生成 Java 代码来执行排序和限制操作。
 */
public class EnumerableLimitSort extends Sort implements EnumerableRel {

  /**
   * 构造方法的作用：
   * 
   * 1. 创建一个 EnumerableLimitSort 实例，初始化排序、限制和偏移操作。
   * 
   * 2. 参数说明：
   *    - cluster: 关系代数集群，包含优化器的共享信息
   *    - traitSet: 特征集合，包含调用约定和排序规则
   *    - input: 输入的关系节点，即需要排序的数据源
   *    - collation: 排序规则，定义了按哪些字段排序以及排序方向
   *    - offset: 偏移量，表示跳过前多少条记录，可以为 null（表示不跳过）
   *    - fetch: 获取数量，表示最多返回多少条记录，可以为 null（表示返回所有）
   * 
   * 3. 注意事项：
   *    - 除非你清楚自己在做什么，否则建议使用 {@link #create} 工厂方法来创建实例
   *    - 构造方法会验证调用约定是否为 EnumerableConvention
   *    - 构造方法会验证当前节点和输入节点的调用约定是否一致
   * 
   * 4. 断言说明：
   *    - 第一个断言确保调用约定是 EnumerableConvention 类型
   *    - 第二个断言确保当前节点和输入节点使用相同的调用约定
   */
  public EnumerableLimitSort(
      RelOptCluster cluster, // 关系代数集群参数，提供优化器的共享信息和上下文
      RelTraitSet traitSet, // 特征集合参数，定义节点的物理属性，如调用约定和排序规则
      RelNode input, // 输入关系节点参数，表示要排序的数据源
      RelCollation collation, // 排序规则参数，定义字段的排序顺序和升序/降序方向
      @Nullable RexNode offset, // 偏移量参数，表示跳过前多少条记录，可为 null 表示不跳过
      @Nullable RexNode fetch) { // 获取数量参数，表示最多返回多少条记录，可为 null 表示返回所有
    super(cluster, traitSet, input, collation, offset, fetch); // 调用父类 Sort 的构造方法，初始化基本属性
    assert this.getConvention() instanceof EnumerableConvention; // 断言当前节点的调用约定必须是 EnumerableConvention 类型
    assert this.getConvention() == input.getConvention(); // 断言当前节点和输入节点的调用约定必须一致
  }

  /**
   * 工厂方法的作用：
   * 
   * 1. 提供了一种更便捷和安全的创建 EnumerableLimitSort 实例的方式。
   * 
   * 2. 自动处理特征集合的构建，确保调用约定正确设置。
   * 
   * 3. 参数说明：
   *    - input: 输入的关系节点
   *    - collation: 排序规则
   *    - offset: 偏移量（可选）
   *    - fetch: 获取数量（可选）
   * 
   * 4. 实现步骤：
   *    - 从输入节点获取集群
   *    - 创建特征集合，包含 EnumerableConvention 和排序规则
   *    - 使用构造方法创建实例
   * 
   * 5. 推荐使用：相比直接使用构造方法，这个工厂方法更安全，因为它自动处理了特征集合的构建。
   */
  public static EnumerableLimitSort create( // 静态工厂方法，用于创建 EnumerableLimitSort 实例
      RelNode input, // 输入关系节点参数，表示要排序的数据源
      RelCollation collation, // 排序规则参数，定义字段的排序顺序和方向
      @Nullable RexNode offset, // 偏移量参数，表示跳过前多少条记录，可为 null
      @Nullable RexNode fetch) { // 获取数量参数，表示最多返回多少条记录，可为 null
    final RelOptCluster cluster = input.getCluster(); // 从输入节点获取关系代数集群
    final RelTraitSet traitSet = // 创建特征集合
        cluster.traitSetOf(EnumerableConvention.INSTANCE).replace(collation); // 首先设置 EnumerableConvention 调用约定，然后替换为指定的排序规则
    return new EnumerableLimitSort(cluster, traitSet, input, collation, offset, fetch); // 使用构造方法创建并返回 EnumerableLimitSort 实例
  }

  /**
   * copy 方法的作用：
   * 
   * 1. 创建当前节点的副本，但可以修改某些属性。
   * 
   * 2. 这是 RelNode 接口要求的方法，用于在优化过程中创建节点的修改版本。
   * 
   * 3. 参数说明：
   *    - traitSet: 新的特征集合
   *    - newInput: 新的输入节点
   *    - newCollation: 新的排序规则
   *    - offset: 偏移量
   *    - fetch: 获取数量
   * 
   * 4. 返回值：返回一个新的 EnumerableLimitSort 实例，具有指定的属性。
   * 
   * 5. 使用场景：在规则应用和优化过程中，需要创建节点的变体时调用此方法。
   */
  @Override public EnumerableLimitSort copy( // 重写父类的 copy 方法，用于创建节点的副本
      RelTraitSet traitSet, // 新的特征集合参数
      RelNode newInput, // 新的输入节点参数
      RelCollation newCollation, // 新的排序规则参数
      @Nullable RexNode offset, // 偏移量参数
      @Nullable RexNode fetch) { // 获取数量参数
    return new EnumerableLimitSort( // 创建并返回新的 EnumerableLimitSort 实例
        this.getCluster(), // 使用当前节点的集群
        traitSet, // 使用新的特征集合
        newInput, // 使用新的输入节点
        newCollation, // 使用新的排序规则
        offset, // 使用指定的偏移量
        fetch); // 使用指定的获取数量
  }

  /**
   * implement 方法的作用：
   * 
   * 1. 这是 EnumerableRel 接口的核心方法，负责将关系节点转换为可执行的 LINQ 表达式。
   * 
   * 2. 实现步骤：
   *    a. 创建代码块构建器 BlockBuilder
   *    b. 递归访问子节点，获取子节点的实现结果
   *    c. 生成物理类型信息 PhysType
   *    d. 生成排序键和比较器
   *    e. 处理 fetch 和 offset 参数
   *    f. 生成调用 ORDER_BY_WITH_FETCH_AND_OFFSET 方法的表达式
   *    g. 返回最终的结果对象
   * 
   * 3. 参数说明：
   *    - implementor: 实现器，负责协调整个代码生成过程
   *    - pref: 优先级偏好，影响代码生成的策略
   * 
   * 4. 返回值：返回 Result 对象，包含生成的代码块和物理类型信息。
   * 
   * 5. 核心逻辑：
   *    - 使用 BuiltInMethod.ORDER_BY_WITH_FETCH_AND_OFFSET 方法来实现排序和限制
   *    - 该方法接受输入枚举、键选择器、比较器、偏移量和获取数量
   *    - 生成的代码会执行排序、跳过偏移量、限制返回数量的操作
   */
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 重写 implement 方法，将关系节点转换为可执行的 LINQ 表达式
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于逐步构建代码块
    final EnumerableRel child = (EnumerableRel) this.getInput(); // 获取输入节点并转换为 EnumerableRel 类型
    final Result result = implementor.visitChild(this, 0, child, pref); // 递归访问子节点，获取子节点的实现结果
    final PhysType physType = // 创建物理类型对象，描述输出行的物理表示
        PhysTypeImpl.of(implementor.getTypeFactory(), this.getRowType(), // 使用类型工厂和行类型创建 PhysType
            result.format); // 使用子节点的格式化方式
    final Expression childExp = builder.append("child", result.block); // 将子节点的代码块添加到构建器中，并获取表达式

    final PhysType inputPhysType = result.physType; // 获取输入的物理类型
    final Pair<Expression, Expression> pair = // 生成排序键和比较器，Pair.left 是键选择器，Pair.right 是比较器
        inputPhysType.generateCollationKey(this.collation.getFieldCollations()); // 根据排序规则生成排序键

    final Expression fetchVal; // 声明获取数量的表达式变量
    if (this.fetch == null) { // 如果 fetch 为 null，表示没有限制返回数量
      fetchVal = Expressions.constant(Integer.MAX_VALUE); // 使用最大整数值表示返回所有记录
    } else { // 如果 fetch 不为 null
      fetchVal = getExpression(this.fetch); // 将 RexNode 转换为 Expression
    }

    final Expression offsetVal; // 声明偏移量的表达式变量
    if (this.offset == null) { // 如果 offset 为 null，表示不跳过任何记录
      offsetVal = Expressions.constant(0); // 使用 0 表示不跳过记录
    } else { // 如果 offset 不为 null
      offsetVal = getExpression(this.offset); // 将 RexNode 转换为 Expression
    }

    builder.add( // 向代码块构建器添加返回语句
        Expressions.return_(null, // 创建返回表达式，返回值为 null 表示没有返回变量名
            Expressions.call(BuiltInMethod.ORDER_BY_WITH_FETCH_AND_OFFSET.method, // 调用内置的 ORDER_BY_WITH_FETCH_AND_OFFSET 方法
                Expressions.list(childExp, // 创建表达式列表，第一个参数是子节点的表达式
                        builder.append("keySelector", pair.left)) // 添加键选择器表达式
                    .appendIfNotNull( // 如果不为 null 则添加
                        builder.appendIfNotNull("comparator", pair.right)) // 添加比较器表达式
                    .appendIfNotNull( // 如果不为 null 则添加
                        builder.appendIfNotNull("offset", // 添加偏移量表达式
                            Expressions.constant(offsetVal))) // 将偏移量值包装为常量表达式
                    .appendIfNotNull( // 如果不为 null 则添加
                        builder.appendIfNotNull("fetch", // 添加获取数量表达式
                            Expressions.constant(fetchVal)))))); // 将获取数量值包装为常量表达式
    return implementor.result(physType, builder.toBlock()); // 返回实现结果，包含物理类型和生成的代码块
  }
}
