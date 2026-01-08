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
package org.apache.calcite.rel.core; // 声明包名，该类属于org.apache.calcite.rel.core包，是Calcite核心关系表达式包

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于表示关系表达式所属的集群
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系表达式的特征集合
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类，用于表示排序规范
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef类，用于定义排序特征
import org.apache.calcite.rel.RelDistribution; // 导入RelDistribution类，用于表示数据分布规范
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入RelDistributionTraitDef类，用于定义分布特征
import org.apache.calcite.rel.RelInput; // 导入RelInput类，用于从序列化输入创建关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，所有关系表达式的基接口
import org.apache.calcite.rel.RelWriter; // 导入RelWriter接口，用于将关系表达式输出为可读格式

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于非空检查

/**
 * Relational expression that performs {@link Exchange} and {@link Sort}
 * simultaneously. // 这是一个同时执行Exchange（数据交换/重分布）和Sort（排序）操作的关系表达式
 *
 * <p>Whereas a Sort produces output with a particular
 * {@link org.apache.calcite.rel.RelCollation} and an Exchange produces output
 * with a particular {@link org.apache.calcite.rel.RelDistribution}, the output
 * of a SortExchange has both the required collation and distribution. // 与单独的Sort产生具有特定排序规范的输出，以及单独的Exchange产生具有特定分布规范的输出不同，SortExchange的输出同时具有所需的排序和分布特性
 *
 * <p>Several implementations of SortExchange are possible; the purpose of this
 * base class allows rules to be written that apply to all of those
 * implementations. // SortExchange可以有多种实现方式；这个基类的目的是允许编写适用于所有这些实现的规则
 *
 * 【类作用详解】：SortExchange是Calcite中一个抽象的关系表达式类，它将数据交换（Exchange）和排序（Sort）两个操作合并为一个操作。
 * 在分布式查询执行中，通常需要先对数据进行重分布（按照某种分布策略将数据发送到不同的节点），然后再对数据进行排序。
 * SortExchange将这两个步骤合并，可以提高执行效率，减少中间结果的传输开销。
 *
 * 例如：在分布式环境中执行ORDER BY操作时，可能需要先将数据按照某个字段哈希分布到各个节点，然后在每个节点上进行局部排序，
 * 最后再进行全局排序。SortExchange可以表示这种同时进行数据交换和排序的操作。
 */
public abstract class SortExchange extends Exchange { // 声明SortExchange为抽象类，继承自Exchange基类
  protected final RelCollation collation; // 成员变量：存储排序规范（collation），表示数据应该按照哪些字段以及什么顺序进行排序，final修饰表示初始化后不可变

  //~ Constructors ----------------------------------------------------------- // 构造方法部分的分隔标记

  /**
   * Creates a SortExchange. // 创建一个SortExchange实例
   *
   * @param cluster   Cluster this relational expression belongs to // 参数：cluster，表示该关系表达式所属的集群，包含查询优化器的相关信息
   * @param traitSet  Trait set // 参数：traitSet，特征集合，定义了该关系表达式的物理特性（如分布方式、排序方式等）
   * @param input     Input relational expression // 参数：input，输入的关系表达式，表示SortExchange要处理的数据来源
   * @param distribution Distribution specification // 参数：distribution，分布规范，指定数据应该如何分布（如HASH、RANGE、BROADCAST等）
   * 【构造方法详解】：这是主要的构造方法，用于创建SortExchange实例。它会调用父类Exchange的构造方法，
   * 保存排序规范，并进行断言检查以确保traitSet包含排序规范。
   */
  protected SortExchange(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法开始，接收集群、特征集合、输入关系表达式、分布规范和排序规范作为参数
      RelNode input, RelDistribution distribution, RelCollation collation) { // 继续构造方法参数列表
    super(cluster, traitSet, input, distribution); // 调用父类Exchange的构造方法，传递集群、特征集合、输入关系表达式和分布规范
    this.collation = requireNonNull(collation, "collation"); // 将排序规范赋值给成员变量，使用requireNonNull确保collation不为null，否则抛出NullPointerException

    assert traitSet.containsIfApplicable(collation) // 断言：检查traitSet是否包含适用的排序规范特征
        : "traits=" + traitSet + ", collation=" + collation; // 如果断言失败，输出traits和collation的详细信息用于调试
  } // 构造方法结束

  /**
   * Creates a SortExchange by parsing serialized output. // 通过解析序列化输出来创建SortExchange实例
   * 【构造方法详解】：这个构造方法用于从序列化的RelInput对象中恢复SortExchange实例。
   * 它会从RelInput中提取集群、特征集合、输入关系表达式、分布规范和排序规范，
   * 并对分布规范和排序规范进行规范化处理（canonize），然后调用主构造方法创建实例。
   */
  protected SortExchange(RelInput input) { // 构造方法开始，接收RelInput对象作为参数
    this(input.getCluster(), // 调用主构造方法，第一个参数是从RelInput获取的集群
        input.getTraitSet().plus(input.getCollation()) // 第二个参数是特征集合，通过添加排序规范来增强原有的特征集合
            .plus(input.getDistribution()), // 继续增强特征集合，添加分布规范
        input.getInput(), // 第三个参数是从RelInput获取的输入关系表达式
        RelDistributionTraitDef.INSTANCE.canonize(input.getDistribution()), // 第四个参数是对分布规范进行规范化处理（规范化是指将分布规范转换为标准形式）
        RelCollationTraitDef.INSTANCE.canonize(input.getCollation())); // 第五个参数是对排序规范进行规范化处理
  } // 构造方法结束

  //~ Methods ---------------------------------------------------------------- // 方法部分的分隔标记

  @Override public final SortExchange copy(RelTraitSet traitSet, // 重写父类的copy方法，final修饰表示此方法不能被子类覆盖
      RelNode newInput, RelDistribution newDistribution) { // copy方法参数：新的特征集合、新的输入关系表达式、新的分布规范
    return copy(traitSet, newInput, newDistribution, collation); // 调用抽象的copy方法，传递所有参数并保持原有的排序规范不变
  } // copy方法结束

  public abstract SortExchange copy(RelTraitSet traitSet, RelNode newInput, // 声明抽象的copy方法，子类必须实现此方法以创建SortExchange的副本
      RelDistribution newDistribution, RelCollation newCollation); // 抽象copy方法的参数：新的特征集合、新的输入关系表达式、新的分布规范、新的排序规范
//  【方法详解】：copy方法用于创建SortExchange的副本。第一个copy方法（带有final修饰符）是一个便捷方法，
//  它保持排序规范不变，只允许修改特征集合、输入关系表达式和分布规范。
//  第二个copy方法是抽象方法，要求子类实现，允许修改所有参数包括排序规范。

  /**
   * Returns the array of {@link org.apache.calcite.rel.RelFieldCollation}s
   * asked for by the sort specification, from most significant to least
   * significant. // 返回排序规范要求的字段排序数组，按从最重要到最不重要的顺序排列
   *
   * <p>See also
   * {@link org.apache.calcite.rel.metadata.RelMetadataQuery#collations(RelNode)},
   * which lists all known collations. For example,
   * <code>ORDER BY time_id</code> might also be sorted by
   * <code>the_year, the_month</code> because of a known monotonicity
   * constraint among the columns. {@code getCollation} would return
   * <code>[time_id]</code> and {@code collations} would return
   * <code>[ [time_id], [the_year, the_month] ]</code>.
   * 【方法详解】：getCollation方法返回SortExchange的排序规范。注意getCollation()返回的是显式要求的排序（如ORDER BY子句指定的排序），
   * 而RelMetadataQuery.collations()返回的是所有已知的排序方式（包括隐式的排序）。
   * 例如：如果查询中有ORDER BY time_id，但由于列之间的单调性约束（如time_id的值随着the_year和the_month的增加而增加），
   * 那么数据实际上也是按照the_year和the_month排序的。getCollation()只返回[time_id]，而collations()会返回[[time_id], [the_year, the_month]]。
   */
  public RelCollation getCollation() { // 方法声明：getCollation方法，返回类型为RelCollation，无参数
    return collation; // 返回成员变量collation，即该SortExchange的排序规范
  } // getCollation方法结束

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写父类的explainTerms方法，用于输出关系表达式的可读描述
    return super.explainTerms(pw) // 调用父类的explainTerms方法，获取基础的RelWriter对象
        .item("collation", collation); // 在RelWriter中添加collation项，输出排序规范信息
  } // explainTerms方法结束
} // SortExchange类定义结束
