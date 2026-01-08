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
 */ // Apache License 2.0 许可证声明，说明代码版权归属和使用权限
package org.apache.calcite.rel.core; // 声明包名，表示这个类属于 org.apache.calcite.rel.core 包，这是 Calcite 核心关系表达式包

import org.apache.calcite.plan.Convention; // 导入 Convention 类，用于定义关系表达式的调用约定（如物理实现方式）
import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster 类，表示关系表达式优化集群，包含共享的优化信息
import org.apache.calcite.plan.RelOptSamplingParameters; // 导入 RelOptSamplingParameters 类，用于封装采样参数（采样模式、采样率、是否可重复、种子值）
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet 类，表示关系表达式的特征集合（如物理属性、排序规则等）
import org.apache.calcite.rel.RelInput; // 导入 RelInput 类，用于从序列化数据创建关系表达式
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，所有关系表达式的基接口
import org.apache.calcite.rel.RelWriter; // 导入 RelWriter 接口，用于将关系表达式写入输出流（如调试信息、查询计划）
import org.apache.calcite.rel.SingleRel; // 导入 SingleRel 类，表示只有一个子节点的关系表达式基类

import java.math.BigDecimal; // 导入 BigDecimal 类，用于精确的十进制数值计算（采样率）
import java.util.List; // 导入 List 接口，用于处理子节点列表

/**
 * Relational expression that returns a sample of the rows from its input.
 * 关系表达式，用于从其输入中返回行的样本（采样）
 *
 * <p>In SQL, a sample is expressed using the {@code TABLESAMPLE BERNOULLI} or
 * {@code SYSTEM} keyword applied to a table, view or sub-query.
 * 在 SQL 中，采样通过 {@code TABLESAMPLE BERNOULLI} 或 {@code SYSTEM} 关键字应用于表、视图或子查询来表达
 * BERNOULLI 模式：独立随机采样，每行都有相同的概率被选中
 * SYSTEM 模式：基于页面或块的采样，性能更高但可能不够均匀
 */
public class Sample extends SingleRel { // Sample 类：表示 SQL TABLESAMPLE 操作的关系表达式，继承自 SingleRel（只有一个子节点的关系表达式）
  //~ Instance fields --------------------------------------------------------

  private final RelOptSamplingParameters params; // 采样参数对象，包含采样模式（BERNOULLI/SYSTEM）、采样率、是否可重复、随机种子等信息

  //~ Constructors -----------------------------------------------------------

  public Sample(RelOptCluster cluster, RelNode child,
      RelOptSamplingParameters params) { // 构造函数：创建 Sample 关系表达式
    super(cluster, cluster.traitSetOf(Convention.NONE), child); // 调用父类 SingleRel 构造函数，传入集群、特征集（使用 NONE 约定）和子节点
    this.params = params; // 保存采样参数到成员变量
  }

  /**
   * Creates a Sample by parsing serialized output.
   * 通过解析序列化输出来创建 Sample 对象（用于反序列化）
   */
  public Sample(RelInput input) { // 构造函数：从序列化输入创建 Sample 对象
    this(input.getCluster(), input.getInput(), getSamplingParameters(input)); // 调用主构造函数，从 RelInput 中提取集群、子节点和采样参数
  }

  //~ Methods ----------------------------------------------------------------

  private static RelOptSamplingParameters getSamplingParameters(
      RelInput input) { // 私有静态方法：从序列化输入中提取采样参数
    String mode = input.getString("mode"); // 从输入中获取采样模式字符串（"bernoulli" 或 "system"）
    final boolean bernoulli = "bernoulli".equals(mode); // 判断是否为 BERNOULLI 采样模式
    final BigDecimal rate = input.getBigDecimal("rate"); // 获取采样率（BigDecimal 类型，精确的小数，如 0.1 表示 10%）
    final Object repeatableSeed = input.get("repeatableSeed"); // 获取可重复种子对象（Number 类型或 null）
    final int seed; // 声明种子变量
    final boolean repeatable; // 声明是否可重复标志
    if (repeatableSeed instanceof Number) { // 如果种子是 Number 类型（整数、浮点数等）
      repeatable = true; // 标记为可重复采样
      seed = ((Number) repeatableSeed).intValue(); // 将种子转换为 int 类型
    } else { // 如果种子不是 Number 类型（通常是 null）
      repeatable = false; // 标记为不可重复采样
      seed = 0; // 种子设置为 0（不使用）
    }
    return new RelOptSamplingParameters(bernoulli, rate, repeatable, seed); // 创建并返回 RelOptSamplingParameters 对象，封装所有采样参数
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写 copy 方法：复制当前关系表达式，可能使用新的特征集和子节点
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言：特征集如果适用，必须包含 NONE 约定
    return new Sample(getCluster(), sole(inputs), params); // 创建新的 Sample 对象，使用当前集群、唯一的子节点（sole 方法确保只有一个子节点）和相同的采样参数
  }

  /**
   * Retrieve the sampling parameters for this Sample.
   * 获取此 Sample 操作的采样参数
   */
  public RelOptSamplingParameters getSamplingParameters() { // 公共方法：获取采样参数对象
    return params; // 返回成员变量 params（采样参数对象）
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写 explainTerms 方法：将此 Sample 关系表达式的详细信息写入 RelWriter（用于生成查询计划的可读描述）
    return super.explainTerms(pw) // 调用父类的 explainTerms 方法，获取基础的关系表达式描述
        .item("mode", params.isBernoulli() ? "bernoulli" : "system") // 添加采样模式项：如果是 BERNOULLI 模式显示 "bernoulli"，否则显示 "system"
        .item("rate", params.sampleRate) // 添加采样率项：显示采样率（如 0.1 表示 10%）
        .item("repeatableSeed", // 添加可重复种子项：
            params.isRepeatable() ? params.getRepeatableSeed() : "-"); // 如果可重复则显示种子值，否则显示 "-" 表示不可重复
  }
}
