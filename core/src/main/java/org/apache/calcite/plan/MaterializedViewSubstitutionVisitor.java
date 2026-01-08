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
package org.apache.calcite.plan; // 声明包名,该类属于 org.apache.calcite.plan 包,用于 Calcite 查询优化器的计划相关功能

import org.apache.calcite.rel.RelNode; // 导入 RelNode 类,代表关系代数表达式树中的一个节点
import org.apache.calcite.tools.RelBuilderFactory; // 导入 RelBuilderFactory 接口,用于创建 RelBuilder 实例的工厂

/**
 * Extension to {@link SubstitutionVisitor}. // 这是 SubstitutionVisitor 的扩展类
 * 
 * 【类的作用详细说明】:
 * 
 * 1. 物化视图替换访问器:
 *    - 该类专门用于物化视图(Materialized View)的替换和重写优化
 *    - 继承自 SubstitutionVisitor,利用父类的替换能力来实现物化视图的自动替换
 *    - 在查询优化过程中,当检测到查询可以匹配到某个物化视图时,会用物化视图替换原始查询计划
 * 
 * 2. 核心功能:
 *    - 查询重写: 将用户的查询重写为使用物化视图,避免重复计算
 *    - 性能优化: 通过使用预计算的物化视图,大幅提升查询性能
 *    - 自动匹配: 自动识别查询与物化视图的匹配关系
 *    - 残差条件提取: 当物化视图包含的数据多于查询需要时,提取额外的过滤条件
 * 
 * 3. 工作原理:
 *    - target: 表示物化视图的定义查询(即物化视图最初是如何创建的)
 *    - query: 表示用户的实际查询
 *    - 通过比较 target 和 query,判断是否可以用物化视图替换查询
 *    - 如果匹配成功,生成使用物化视图的新查询计划
 * 
 * 4. 使用场景示例:
 *    - 假设有一个物化视图 mv_sales,定义为: SELECT product_id, SUM(amount) FROM sales GROUP BY product_id
 *    - 用户查询: SELECT product_id, SUM(amount) FROM sales WHERE date > '2024-01-01' GROUP BY product_id
 *    - 如果物化视图的数据满足查询需求,可以将查询重写为: SELECT product_id, SUM(amount) FROM mv_sales WHERE date > '2024-01-01'
 *    - 这样就避免了重新执行聚合计算
 * 
 * 5. 与父类的关系:
 *    - SubstitutionVisitor: 通用的替换访问器,提供关系表达式树的替换能力
 *    - MaterializedViewSubstitutionVisitor: 专门针对物化视图场景的特化版本
 *    - 使用 DEFAULT_RULES 规则集,包含各种替换规则(如聚合、过滤、投影等)
 * 
 * 6. @Deprecated 说明:
 *    - 该类已被标记为过时,将在 2.0 版本前移除
 *    - 建议使用新的物化视图替换机制
 *    - 保留是为了向后兼容
 */
@Deprecated // 标记为过时,将在 2.0 版本前移除
public class MaterializedViewSubstitutionVisitor extends SubstitutionVisitor { // 定义类,继承自 SubstitutionVisitor

  /**
   * 【成员变量说明】:
   * 该类没有定义额外的成员变量,所有成员变量都继承自父类 SubstitutionVisitor
   * 
   * 父类 SubstitutionVisitor 的主要成员变量包括:
   * 
   * 1. protected final RelBuilder relBuilder:
   *    - 用于构建关系表达式树的构建器
   *    - 在替换过程中创建新的关系节点
   * 
   * 2. private final ImmutableList<UnifyRule> rules:
   *    - 统一规则列表,定义了如何匹配和替换不同类型的关系节点
   *    - DEFAULT_RULES 包含: TrivialRule, ScanToCalcUnifyRule, CalcToCalcUnifyRule,
   *      JoinOnLeftCalcToJoinUnifyRule, JoinOnRightCalcToJoinUnifyRule, JoinOnCalcsToJoinUnifyRule,
   *      AggregateToAggregateUnifyRule, AggregateOnCalcToAggregateUnifyRule,
   *      UnionToUnionUnifyRule, UnionOnCalcsToUnionUnifyRule,
   *      IntersectToIntersectUnifyRule, IntersectOnCalcsToIntersectUnifyRule
   * 
   * 3. private final Map<Pair<Class, Class>, List<UnifyRule>> ruleMap:
   *    - 规则映射表,根据查询节点类型和目标节点类型快速查找适用的规则
   * 
   * 4. private final RelOptCluster cluster:
   *    - 关系表达式集群,包含共享资源如类型系统、RexBuilder 等
   * 
   * 5. private final RexSimplify simplify:
   *    - Rex 表达式简化器,用于简化条件表达式
   * 
   * 6. private final Holder query:
   *    - 查询关系表达式的可变包装器
   * 
   * 7. private final MutableRel target:
   *    - 目标关系表达式的可变版本(物化视图的定义)
   * 
   * 8. final List<MutableRel> targetLeaves:
   *    - 目标关系表达式树中的叶子节点列表
   * 
   * 9. final List<MutableRel> queryLeaves:
   *    - 查询关系表达式树中的叶子节点列表
   * 
   * 10. final Map<MutableRel, MutableRel> replacementMap:
   *     - 替换映射表,记录哪些节点被替换了
   * 
   * 11. final Multimap<MutableRel, MutableRel> equivalents:
   *     - 等价节点映射,记录等价的关系表达式
   * 
   * 12. protected final MutableRel[] slots:
   *     - 规则匹配时的工作空间,最多需要 2 个槽位
   */

  /**
   * 【构造方法1详细说明】:
   * 
   * 方法签名: public MaterializedViewSubstitutionVisitor(RelNode target_, RelNode query_)
   * 
   * 参数说明:
   * - RelNode target_: 物化视图的定义查询(目标关系表达式)
   *   - 这是物化视图最初创建时的 SQL 查询对应的关系表达式树
   *   - 例如: 如果物化视图定义为 "SELECT product_id, COUNT(*) FROM sales GROUP BY product_id",
   *     那么 target_ 就是这个查询对应的关系表达式树
   *   - 在替换过程中,我们尝试在用户的查询中找到与 target_ 匹配的部分
   * 
   * - RelNode query_: 用户的实际查询(查询关系表达式)
   *   - 这是用户提交的 SQL 查询对应的关系表达式树
   *   - 例如: "SELECT product_id, COUNT(*) FROM sales WHERE date > '2024-01-01' GROUP BY product_id"
   *   - 我们要检查这个查询是否可以用物化视图来替换
   * 
   * 功能说明:
   * - 创建一个使用默认规则集(DEFAULT_RULES)的物化视图替换访问器
   * - 调用父类构造函数,传入 target_, query_ 和 DEFAULT_RULES
   * - 使用默认的逻辑构建器(RelFactories.LOGICAL_BUILDER)
   * 
   * 使用示例:
   * RelNode target = ...; // 物化视图的定义
   * RelNode query = ...;  // 用户的查询
   * MaterializedViewSubstitutionVisitor visitor = 
   *     new MaterializedViewSubstitutionVisitor(target, query);
   * List<RelNode> results = visitor.go(materializedViewTable);
   * 
   * 注意事项:
   * - 这个构造方法使用 DEFAULT_RULES,适用于大多数标准场景
   * - 如果需要自定义规则集,应使用另一个构造方法
   * - 使用默认的逻辑构建器,适用于逻辑查询优化
   */
  public MaterializedViewSubstitutionVisitor(RelNode target_, RelNode query_) { // 构造方法1,接收目标和查询关系节点
    super(target_, query_, DEFAULT_RULES); // 调用父类构造函数,传入目标、查询和默认规则集
  } // 构造方法结束

  /**
   * 【构造方法2详细说明】:
   * 
   * 方法签名: public MaterializedViewSubstitutionVisitor(RelNode target_, RelNode query_, RelBuilderFactory relBuilderFactory)
   * 
   * 参数说明:
   * - RelNode target_: 物化视图的定义查询(目标关系表达式)
   *   - 同构造方法1,表示物化视图最初创建时的查询
   *   - 用于匹配和替换的目标模式
   * 
   * - RelNode query_: 用户的实际查询(查询关系表达式)
   *   - 同构造方法1,表示用户提交的查询
   *   - 需要被重写的查询
   * 
   * - RelBuilderFactory relBuilderFactory: 关系表达式构建器工厂
   *   - 用于创建 RelBuilder 实例的工厂对象
   *   - RelBuilder 是构建关系表达式树的核心工具
   *   - 通过这个参数可以自定义构建器,使用不同的配置或实现
   *   - 例如: 可以使用特定的配置来创建构建器,或者使用自定义的构建器实现
   * 
   * 功能说明:
   * - 创建一个使用默认规则集(DEFAULT_RULES)和自定义构建器工厂的物化视图替换访问器
   * - 调用父类构造函数,传入 target_, query_, DEFAULT_RULES 和 relBuilderFactory
   * - 允许自定义关系表达式构建器,提供更大的灵活性
   * 
   * 与构造方法1的区别:
   * - 构造方法1使用默认的逻辑构建器(RelFactories.LOGICAL_BUILDER)
   * - 构造方法2允许通过 relBuilderFactory 参数指定自定义构建器
   * - 构造方法2适用于需要特殊配置的场景
   * 
   * 使用场景:
   * 1. 使用特定的配置参数创建构建器
   * 2. 使用自定义的 RelBuilder 实现
   * 3. 在不同的上下文中使用不同的构建器配置
   * 4. 测试时注入模拟的构建器
   * 
   * 使用示例:
   * RelBuilder customBuilderFactory = (cluster, schema) -> new CustomRelBuilder(cluster, schema);
   * MaterializedViewSubstitutionVisitor visitor = 
   *     new MaterializedViewSubstitutionVisitor(target, query, customBuilderFactory);
   * 
   * 注意事项:
   * - 仍然使用 DEFAULT_RULES 规则集
   * - relBuilderFactory 不能为 null
   * - 自定义构建器需要与 Calcite 的关系表达式模型兼容
   */
  public MaterializedViewSubstitutionVisitor(RelNode target_, RelNode query_, // 构造方法2,接收目标、查询和构建器工厂
      RelBuilderFactory relBuilderFactory) { // 接收关系表达式构建器工厂参数
    super(target_, query_, DEFAULT_RULES, relBuilderFactory); // 调用父类构造函数,传入目标、查询、默认规则集和构建器工厂
  } // 构造方法2结束
} // 类定义结束
