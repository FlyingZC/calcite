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
// Apache Calcite 是一个动态数据管理框架，提供 SQL 解析、优化、执行等功能
// 本文件定义了 Convention（调用约定）接口，这是 Calcite 中最核心的 Trait（特征）之一
// Trait 是关系代数表达式（RelNode）的属性集合，用于描述 RelNode 的各种特征
// Convention 描述了 RelNode 的调用约定，即 RelNode 属于哪种计算模型或执行引擎
// 例如：ENUMERABLE（可枚举，Java 迭代器）、PHYSICAL（物理执行计划）、LOGICAL（逻辑计划）等
package org.apache.calcite.plan;

// 导入 RelNode 接口，这是 Calcite 中所有关系代数表达式的基类
// RelNode 表示关系代数中的一个操作，如 Scan、Filter、Project、Join 等
import org.apache.calcite.rel.RelNode;
// 导入 RelFactories 类，用于创建各种 RelNode 的工厂类集合
// 提供了创建不同类型 RelNode 的工厂方法，如 FilterFactory、ProjectFactory 等
import org.apache.calcite.rel.core.RelFactories;

// 导入 Nullable 注解，用于标记可能为 null 的返回值或参数
// 这是 Checker Framework 的注解，用于进行空值检查
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Calling convention trait. // 调用约定特征
 * 
 * 【核心概念解释】：
 * 1. Convention（调用约定）：是 Calcite 中最重要的 Trait 之一，描述了 RelNode 的计算模型和执行方式
 *    - 每个 RelNode 都有一个 Convention，表示它属于哪种计算范式
 *    - 例如：Logical Convention（逻辑层）、Enumerable Convention（可枚举，Java 迭代器）、
 *      Physical Convention（物理执行计划）、Spark Convention（Spark 执行）、Cassandra Convention 等
 * 
 * 2. Trait（特征）：RelNode 的属性集合，用于在优化过程中进行规则匹配和代价计算
 *    - 主要的 Trait 包括：Convention（调用约定）、RelCollation（排序规则）、RelDistribution（数据分布）等
 *    - TraitSet 是多个 Trait 的集合，描述了 RelNode 的完整特征
 * 
 * 3. Convention 的作用：
 *    - 区分不同执行引擎和计算模型：例如逻辑计划、物理计划、Java 迭代器、分布式计算等
 *    - 规则匹配的基础：优化规则通常只在特定 Convention 之间进行转换
 *    - 代价计算的依据：不同 Convention 的执行代价不同
 *    - 转换路径的约束：控制 RelNode 可以转换到哪些其他 Convention
 * 
 * 4. Convention 转换：
 *    - 优化器通过规则将 RelNode 从一个 Convention 转换到另一个 Convention
 *    - 例如：从 Logical Convention 转换到 Enumerable Convention，再到 Physical Convention
 *    - 转换规则由 VolcanoPlanner 或 HepPlanner 等优化器应用
 * 
 * 5. 常见的 Convention 实现：
 *    - Convention.NONE：不支持的约定，表示 RelNode 还未确定具体实现方式
 *    - EnumerableConvention：使用 Java 迭代器接口执行
 *    - LogicalConvention：逻辑层约定，用于逻辑优化阶段
 *    - PhysicalConvention：物理层约定，用于物理执行计划
 *    - 各种适配器约定：CassandraConvention、MongoDBConvention、ElasticsearchConvention 等
 * 
 * 【设计模式】：
 * - 这是一个接口，使用 Trait 模式，允许不同的 Convention 实现定义自己的行为
 * - 使用默认方法提供默认实现，子类可以选择性地覆盖
 * - 内部类 Impl 提供了默认实现，大多数 Convention 可以直接使用或继承它
 */
public interface Convention extends RelTrait { // Convention 接口继承自 RelTrait，表示它是一个 Trait
  /**
   * Convention that for a relational expression that does not support any // 不支持任何约定的关系表达式约定
   * convention. It is not implementable, and has to be transformed to // 它是不可实现的，必须转换为其他形式才能实现
   * something else in order to be implemented. // 为了实现，必须转换为其他东西
   *
   * <p>Relational expressions generally start off in this form. // 关系表达式通常以这种形式开始
   *
   * <p>Such expressions always have infinite cost. // 这样的表达式总是具有无限代价
   * 
   * 【NONE 约定详解】：
   * 1. NONE 是一个特殊的 Convention，表示 RelNode 不属于任何特定的计算模型
   * 2. 它是 RelNode 的初始状态，在优化开始时，很多 RelNode 的 Convention 都是 NONE
   * 3. NONE 约定的 RelNode 不能直接执行，必须通过规则转换为其他 Convention
   * 4. NONE 约定的代价被设置为无限大，迫使优化器寻找其他可执行的 Convention
   * 5. 例如：在逻辑优化阶段，RelNode 可能是 NONE，然后被转换为 LOGICAL 或 ENUMERABLE
   * 
   * 【使用场景】：
   * - 作为默认值，当 RelNode 还未确定具体实现方式时使用
   - 在优化器的规则匹配过程中，作为起点进行转换
   - 某些适配器可能使用 NONE 作为中间状态
   * 
   * 【注意事项】：
   * - 不要直接使用 NONE 约定的 RelNode，它只是一个占位符
   * - 优化器会自动将 NONE 转换为其他可执行的 Convention
   * - 如果最终计划中还有 NONE 约定的 RelNode，说明优化失败
   */
  Convention NONE = new Impl("NONE", RelNode.class); // 创建一个名为 "NONE" 的约定实例，使用 RelNode 作为接口类

  /**
   * 【方法作用】：获取此 Convention 对应的 RelNode 接口类
   * 
   * 【返回值说明】：返回一个 Class 对象，表示此 Convention 支持的 RelNode 接口类型
   * 
   * 【详细解释】：
   * 1. 每个 Convention 都对应一种 RelNode 接口，描述了该 Convention 下 RelNode 的行为
   * 2. 例如：EnumerableConvention 返回 EnumerableRel 接口，PhysicalConvention 返回 PhysicalRel 接口
   * 3. 这个接口定义了该 Convention 下所有 RelNode 必须实现的方法
   * 4. 通过这个接口，可以确保 RelNode 符合特定的 Convention 要求
   * 
   * 【使用场景】：
   * - 在规则匹配时，检查 RelNode 是否实现了正确的接口
   * - 在代码生成时，确定需要生成哪种类型的代码
   * - 在类型转换时，验证 RelNode 是否可以转换到目标 Convention
   * 
   * 【示例】：
   * - EnumerableConvention.getInterface() 返回 EnumerableRel.class
   * - LogicalConvention.getInterface() 返回 LogicalRel.class
   */
  Class getInterface(); // 获取此 Convention 对应的 RelNode 接口类

  /**
   * 【方法作用】：获取此 Convention 的名称
   * 
   * 【返回值说明】：返回一个字符串，表示此 Convention 的唯一标识名称
   * 
   * 【详细解释】：
   * 1. 名称用于唯一标识一个 Convention，通常是大写的常量
   * 2. 名称在调试和日志输出中非常有用，可以快速识别 Convention 类型
   * 3. 常见的名称包括："NONE"、"ENUMERABLE"、"LOGICAL"、"PHYSICAL" 等
   * 4. 名称应该简洁明了，能够准确反映 Convention 的特征
   * 
   * 【使用场景】：
   * - 在日志和调试信息中显示 Convention 类型
   * - 在错误消息中标识 Convention
   * - 在规则匹配时，快速比较 Convention 是否相同
   * 
   * 【示例】：
   * - Convention.NONE.getName() 返回 "NONE"
   * - EnumerableConvention.INSTANCE.getName() 返回 "ENUMERABLE"
   */
  String getName(); // 获取此 Convention 的名称

  /**
   * Given an input and required traits, returns the corresponding // 给定输入和所需的特征，返回相应的
   * enforcer rel nodes, like physical Sort, Exchange etc. // 强制执行器关系节点，如物理 Sort、Exchange 等
   *
   * @param input The input RelNode // 输入的 RelNode
   * @param required The required traits // 所需的特征集合
   * @return Physical enforcer that satisfies the required traitSet, // 满足所需特征集的物理强制执行器
   * or {@code null} if trait enforcement is not allowed or the // 如果不允许特征强制执行或所需特征集无法满足，则返回 null
   * required traitSet can't be satisfied.
   * 
   * 【方法作用】：根据输入 RelNode 和所需的特征集合，返回能够强制满足这些特征的物理执行器节点
   * 
   * 【详细解释】：
   * 1. 在物理优化阶段，可能需要添加额外的物理算子来满足某些特征要求
   * 2. 例如：如果需要排序，可以添加 Sort 算子；如果需要数据重分布，可以添加 Exchange 算子
   * 3. 这个方法决定了 Convention 如何处理特征强制执行
   * 4. 默认实现抛出异常，表示不支持特征强制执行
   * 
   * 【参数说明】：
   * - input：需要强制执行特征的 RelNode
   * - required：目标特征集合，包含了需要满足的所有特征
   * 
   * 【返回值说明】：
   * - 返回一个新的 RelNode，它包装了输入 RelNode 并添加了必要的物理算子
   * - 如果返回 null，表示不支持特征强制执行或无法满足所需特征
   * 
   * 【使用场景】：
   * - 在物理优化阶段，当需要满足排序、分布等特征时调用
   * - 优化器会检查是否可以通过添加物理算子来满足特征要求
   * - 如果可以，则添加相应的算子；否则，尝试其他方式
   * 
   * 【示例】：
   * - 如果 required 包含排序特征，enforce 可能返回一个 SortRel 包装输入
   * - 如果 required 包含数据分布特征，enforce 可能返回一个 ExchangeRel 包装输入
   * - EnumerableConvention 可能不支持 enforce，返回 null
   * - PhysicalConvention 可能支持 enforce，返回相应的物理算子
   */
  default @Nullable RelNode enforce(RelNode input, RelTraitSet required) { // 默认实现：特征强制执行方法
    throw new RuntimeException(getClass().getName() // 抛出运行时异常，表示未实现此方法
        + "#enforce() is not implemented."); // 异常消息包含类名和方法名
  }

  /**
   * Returns whether we should convert from this convention to // 返回是否应该从此约定转换到
   * {@code toConvention}. Used by {@link ConventionTraitDef}. // 目标约定。由 ConventionTraitDef 使用
   *
   * @param toConvention Desired convention to convert to // 要转换到的目标约定
   * @return Whether we should convert from this convention to toConvention // 是否应该从此约定转换到目标约定
   * 
   * 【方法作用】：判断是否允许从此 Convention 转换到目标 Convention
   * 
   * 【详细解释】：
   * 1. Convention 之间的转换不是任意的，需要满足一定的条件
   * 2. 这个方法用于控制转换路径，防止不合理的转换
   * 3. 默认实现返回 false，表示不允许转换
   * 4. ConventionTraitDef 在注册转换规则时会调用此方法
   * 
   * 【参数说明】：
   * - toConvention：目标 Convention，表示要转换到的约定
   * 
   * 【返回值说明】：
   * - true：允许从此 Convention 转换到目标 Convention
   * - false：不允许转换
   * 
   * 【使用场景】：
   * - 在优化器注册转换规则时，检查转换是否被允许
   * - 在规则匹配时，过滤掉不允许的转换路径
   * - 在代价计算时，只考虑允许的转换路径
   * 
   * 【示例】：
   * - LogicalConvention.canConvertConvention(EnumerableConvention) 可能返回 true
   * - EnumerableConvention.canConvertConvention(LogicalConvention) 可能返回 false
   * - NONE 约定可以转换到任何 Convention，但其他 Convention 不能转换到 NONE
   * 
   * 【注意事项】：
   * - 转换应该是双向的或单向的，取决于具体的 Convention
   * - 有些 Convention 可能只允许单向转换，例如从逻辑到物理
   * - 优化器会根据此方法的返回值决定是否注册转换规则
   */
  default boolean canConvertConvention(Convention toConvention) { // 默认实现：检查是否可以转换到目标约定
    return false; // 默认不允许转换
  }

  /**
   * Returns whether we should convert from this trait set to the other trait // 返回是否应该从此特征集转换到另一个特征集
   * set. // 集合
   *
   * <p>The convention decides whether it wants to handle other trait // 约定决定是否要处理其他特征
   * conversions, e.g. collation, distribution, etc.  For a given convention, we // 转换，例如排序、分布等。对于给定的约定，我们
   * will only add abstract converters to handle the trait (convention, // 只会添加抽象转换器来处理特征（约定、
   * collation, distribution, etc.) conversions if this function returns true. // 排序、分布等）转换，如果此函数返回 true
   *
   * @param fromTraits Traits of the RelNode that we are converting from // 我们要转换的 RelNode 的特征
   * @param toTraits Target traits // 目标特征
   * @return Whether we should add converters // 是否应该添加转换器
   * 
   * 【方法作用】：判断是否应该添加抽象转换器来处理特征转换
   * 
   * 【详细解释】：
   * 1. 除了 Convention 转换，还有其他特征的转换，如排序（RelCollation）、分布（RelDistribution）等
   * 2. 这个方法决定了 Convention 是否要处理这些其他特征的转换
   * 3. 如果返回 true，优化器会添加抽象转换器来处理这些特征转换
   * 4. 如果返回 false，优化器不会添加转换器，特征转换需要通过其他方式实现
   * 
   * 【参数说明】：
   * - fromTraits：源特征集合，表示转换前的 RelNode 的所有特征
   * - toTraits：目标特征集合，表示转换后的 RelNode 的所有特征
   * 
   * 【返回值说明】：
   * - true：应该添加抽象转换器来处理特征转换
   * - false：不应该添加转换器
   * 
   * 【使用场景】：
   * - 在优化器进行特征转换时，决定是否需要添加转换器
   * - 不同 Convention 对特征转换的处理方式不同
   * - 有些 Convention 可能需要显式的转换器，有些可能不需要
   * 
   * 【示例】：
   * - PhysicalConvention 可能返回 true，因为它需要处理排序和分布的物理转换
   * - EnumerableConvention 可能返回 false，因为它不关心物理特征
   * - LogicalConvention 可能返回 false，因为逻辑层不处理物理特征
   * 
   * 【注意事项】：
   * - 此方法影响优化器的转换策略
   * - 返回 true 可能会增加转换器的数量，影响优化效率
   * - 返回 false 可能导致某些特征无法满足，需要通过其他方式处理
   */
  default boolean useAbstractConvertersForConversion(RelTraitSet fromTraits, // 默认实现：是否使用抽象转换器进行特征转换
      RelTraitSet toTraits) {
    return false; // 默认不使用抽象转换器
  }

  /** Return RelFactories struct for this convention. It can be used to // 返回此约定的 RelFactories 结构。它可用于
   * build RelNode. // 构建 RelNode
   * 
   * 【方法作用】：返回此 Convention 对应的 RelFactories 结构，用于构建 RelNode
   * 
   * 【详细解释】：
   * 1. RelFactories 是一个结构体，包含了创建各种 RelNode 的工厂方法
   * 2. 不同的 Convention 可能需要不同的工厂方法来创建 RelNode
   * 3. 例如：LogicalConvention 使用 LogicalRelFactories，PhysicalConvention 使用 PhysicalRelFactories
   * 4. 默认实现返回 DEFAULT_STRUCT，表示使用默认的工厂方法
   * 
   * 【返回值说明】：
   * - 返回一个 RelFactories.Struct 对象，包含了各种工厂方法
   * - 默认返回 RelFactories.DEFAULT_STRUCT
   * 
   * 【使用场景】：
   * - 在创建 RelNode 时，使用对应的工厂方法
   * - 在规则应用时，使用工厂方法创建新的 RelNode
   * - 在代码生成时，使用工厂方法生成代码
   * 
   * 【示例】：
   * - EnumerableConvention 可能返回 EnumerableRelFactories
   * - LogicalConvention 可能返回 LogicalRelFactories
   * - PhysicalConvention 可能返回 PhysicalRelFactories
   * 
   * 【RelFactories 包含的工厂】：
   * - FilterFactory：创建 Filter 算子
   * - ProjectFactory：创建 Project 算子
   * - JoinFactory：创建 Join 算子
   * - SortFactory：创建 Sort 算子
   * - AggregateFactory：创建 Aggregate 算子
   * - 等等
   */
  default RelFactories.Struct getRelFactories() { // 默认实现：获取 RelFactories 结构
    return RelFactories.DEFAULT_STRUCT; // 返回默认的工厂结构
  }

  /**
   * Default implementation. // 默认实现
   * 
   * 【类作用】：Convention 接口的默认实现类
   * 
   * 【详细解释】：
   * 1. Impl 类提供了 Convention 接口的基本实现
   * 2. 大多数 Convention 可以直接使用这个类，或者继承它并覆盖部分方法
   * 3. 它包含了 Convention 的基本属性：名称和 RelNode 接口类
   * 4. 提供了默认的方法实现，大部分方法返回默认值或抛出异常
   * 
   * 【设计模式】：
   * - 使用默认实现模式，减少子类的代码量
   * - 提供了基本的 Convention 功能，子类可以按需覆盖
   * - 遵循最小化原则，只实现必要的方法
   * 
   * 【使用场景】：
   * - 作为各种 Convention 的基类
   * - 用于创建简单的 Convention 实例
   * - 作为自定义 Convention 的起点
   * 
   * 【示例】：
   * - Convention.NONE 就是使用 Impl 创建的
   * - EnumerableConvention 可能继承自 Impl 并覆盖部分方法
   * - LogicalConvention 可能继承自 Impl 并添加额外的功能
   */
  class Impl implements Convention { // 内部类：Convention 的默认实现
    /**
     * 【成员变量作用】：Convention 的名称，用于唯一标识此 Convention
     * 
     * 【详细解释】：
     * 1. name 是一个不可变的字符串，在构造函数中初始化后不能再修改
     * 2. 用于在日志、调试信息和错误消息中标识 Convention
     * 3. 通常使用大写的常量名称，如 "NONE"、"ENUMERABLE" 等
     * 4. getName() 方法返回此字段的值
     * 
     * 【使用场景】：
     * - 在 toString() 方法中使用，返回 Convention 的名称
     * - 在日志输出中使用，标识 Convention 类型
     * - 在比较 Convention 是否相同时使用
     * 
     * 【示例】：
     * - Convention.NONE 的 name 是 "NONE"
     * - EnumerableConvention.INSTANCE 的 name 是 "ENUMERABLE"
     */
    private final String name; // Convention 的名称，不可变
    
    /**
     * 【成员变量作用】：此 Convention 支持的 RelNode 接口类
     * 
     * 【详细解释】：
     * 1. relClass 是一个 Class 对象，表示此 Convention 支持的 RelNode 接口
     * 2. 它定义了此 Convention 下所有 RelNode 必须实现的方法
     * 3. getInterface() 方法返回此字段的值
     * 4. 在构造函数中初始化后不能再修改
     * 
     * 【使用场景】：
     * - 在类型检查时，验证 RelNode 是否实现了正确的接口
     * - 在规则匹配时，确保 RelNode 符合 Convention 的要求
     * - 在代码生成时，确定需要生成哪种类型的代码
     * 
     * 【示例】：
     * - Convention.NONE 的 relClass 是 RelNode.class
     * - EnumerableConvention 的 relClass 是 EnumerableRel.class
     * - LogicalConvention 的 relClass 是 LogicalRel.class
     */
    private final Class<? extends RelNode> relClass; // 此 Convention 支持的 RelNode 接口类，不可变

    /**
     * 【构造方法作用】：创建一个新的 Convention 实例
     * 
     * 【参数说明】：
     * - name：Convention 的名称，用于唯一标识
     * - relClass：此 Convention 支持的 RelNode 接口类
     * 
     * 【详细解释】：
     * 1. 构造方法接收名称和接口类两个参数
     * 2. 将参数保存到对应的成员变量中
     * 3. 这两个参数在构造后不能修改，因为字段是 final 的
     * 4. 构造方法是 public 的，允许外部创建 Convention 实例
     * 
     * 【使用场景】：
     * - 创建 Convention.NONE 时使用：new Impl("NONE", RelNode.class)
     * - 创建自定义 Convention 时使用
     * - 在测试代码中创建临时的 Convention 实例
     * 
     * 【注意事项】：
     * - name 应该是唯一的，避免与其他 Convention 冲突
     * - relClass 应该是 RelNode 的子类或实现类
     * - 构造后不能修改字段，确保 Convention 的不可变性
     */
    public Impl(String name, Class<? extends RelNode> relClass) { // 构造方法：初始化 Convention 实例
      this.name = name; // 保存 Convention 的名称
      this.relClass = relClass; // 保存 RelNode 接口类
    }

    /**
     * 【方法作用】：返回 Convention 的字符串表示
     * 
     * 【返回值说明】：返回 Convention 的名称
     * 
     * 【详细解释】：
     * 1. 重写 Object 的 toString() 方法
     * 2. 直接返回 getName() 的结果
     * 3. 用于在日志、调试信息和错误消息中显示 Convention
     * 
     * 【使用场景】：
     * - 在日志输出中使用
     * - 在调试时查看 Convention 的类型
     * - 在错误消息中标识 Convention
     * 
     * 【示例】：
     * - Convention.NONE.toString() 返回 "NONE"
     * - EnumerableConvention.INSTANCE.toString() 返回 "ENUMERABLE"
     */
    @Override public String toString() { // 重写 toString 方法
      return getName(); // 返回 Convention 的名称
    }

    /**
     * 【方法作用】：将此 Convention 注册到优化器中
     * 
     * 【参数说明】：
     * - planner：关系表达式优化器
     * 
     * 【详细解释】：
     * 1. 实现自 RelTrait 接口
     * 2. 默认实现为空，不需要执行任何操作
     * 3. 子类可以覆盖此方法以执行特定的注册逻辑
     * 4. 在某些 Convention 中，可能需要注册特殊的规则或代价模型
     * 
     * 【使用场景】：
     * - 在优化器初始化时调用
     * - 注册 Convention 相关的规则和代价模型
     * - 某些自定义 Convention 可能需要执行初始化逻辑
     * 
     * 【示例】：
     * - EnumerableConvention 可能注册转换规则
     * - PhysicalConvention 可能注册代价模型
     * - Impl 类不需要注册，所以方法体为空
     */
    @Override public void register(RelOptPlanner planner) {} // 注册方法：默认不执行任何操作

    /**
     * 【方法作用】：检查此 Convention 是否满足给定的 Trait
     * 
     * 【参数说明】：
     * - trait：要检查的 Trait
     * 
     * 【返回值说明】：返回 true 如果此 Convention 等于给定的 Trait
     * 
     * 【详细解释】：
     * 1. 实现自 RelTrait 接口
     * 2. 使用对象引用相等（==）进行比较
     * 3. 只有当 trait 就是此 Convention 实例时才返回 true
     * 4. 这确保了 Convention 的唯一性和精确匹配
     * 
     * 【使用场景】：
     * - 在规则匹配时，检查 RelNode 的 Convention 是否符合要求
     * - 在特征集合比较时，检查 Convention 是否匹配
     * - 在转换路径搜索时，验证 Convention 是否可以转换
     * 
     * 【示例】：
     * - Convention.NONE.satisfies(Convention.NONE) 返回 true
     * - Convention.NONE.satisfies(otherConvention) 返回 false
     * - 即使两个 Convention 的名称相同，但不是同一个实例，也返回 false
     * 
     * 【注意事项】：
     * - 使用引用相等而不是 equals() 方法，确保 Convention 的唯一性
     * - Convention 实例应该是单例的，避免创建多个相同名称的实例
     */
    @Override public boolean satisfies(RelTrait trait) { // 检查是否满足给定的 Trait
      return this == trait; // 使用引用相等比较
    }

    /**
     * 【方法作用】：获取此 Convention 支持的 RelNode 接口类
     * 
     * 【返回值说明】：返回此 Convention 支持的 RelNode 接口类的 Class 对象
     * 
     * 【详细解释】：
     * 1. 实现自 Convention 接口
     * 2. 返回 relClass 字段的值
     * 3. 这个接口定义了此 Convention 下 RelNode 必须实现的方法
     * 
     * 【使用场景】：
     * - 在类型检查时，验证 RelNode 是否实现了正确的接口
     * - 在规则匹配时，确保 RelNode 符合 Convention 的要求
     * - 在代码生成时，确定需要生成哪种类型的代码
     * 
     * 【示例】：
     * - Convention.NONE.getInterface() 返回 RelNode.class
     * - EnumerableConvention.INSTANCE.getInterface() 返回 EnumerableRel.class
     */
    @Override public Class getInterface() { // 获取 RelNode 接口类
      return relClass; // 返回 relClass 字段的值
    }

    /**
     * 【方法作用】：获取此 Convention 的名称
     * 
     * 【返回值说明】：返回 Convention 的名称字符串
     * 
     * 【详细解释】：
     * 1. 实现自 Convention 接口
     * 2. 返回 name 字段的值
     * 3. 名称用于唯一标识 Convention
     * 
     * 【使用场景】：
     * - 在日志和调试信息中显示 Convention 类型
     * - 在错误消息中标识 Convention
     * - 在规则匹配时，快速比较 Convention 是否相同
     * 
     * 【示例】：
     * - Convention.NONE.getName() 返回 "NONE"
     * - EnumerableConvention.INSTANCE.getName() 返回 "ENUMERABLE"
     */
    @Override public String getName() { // 获取 Convention 的名称
      return name; // 返回 name 字段的值
    }

    /**
     * 【方法作用】：获取此 Convention 的 TraitDef（特征定义）
     * 
     * 【返回值说明】：返回 ConventionTraitDef.INSTANCE
     * 
     * 【详细解释】：
     * 1. 实现自 RelTrait 接口
     * 2. 返回 ConventionTraitDef 的单例实例
     * 3. ConventionTraitDef 是 Convention 的特征定义，包含了 Convention 的元信息和规则
     * 4. 所有 Convention 都共享同一个 ConventionTraitDef 实例
     * 
     * 【使用场景】：
     * - 在优化器中注册 Convention 时使用
     * - 在规则匹配时，获取 Convention 的元信息
     * - 在特征转换时，获取转换规则
     * 
     * 【ConventionTraitDef 的作用】：
     * - 定义了 Convention 的基本属性和行为
     * - 管理所有 Convention 实例
     * - 提供 Convention 之间的转换规则
     * - 计算 Convention 的代价
     * 
     * 【示例】：
     * - 所有 Convention.getTraitDef() 都返回 ConventionTraitDef.INSTANCE
     * - 优化器通过 ConventionTraitDef 获取 Convention 的相关信息
     */
    @Override public RelTraitDef getTraitDef() { // 获取 TraitDef
      return ConventionTraitDef.INSTANCE; // 返回 ConventionTraitDef 的单例实例
    }

    /**
     * 【方法作用】：特征强制执行方法
     * 
     * 【参数说明】：
     * - input：输入的 RelNode
     * - required：所需的特征集合
     * 
     * 【返回值说明】：返回 null，表示不支持特征强制执行
     * 
     * 【详细解释】：
     * 1. 实现自 Convention 接口
     * 2. 默认实现返回 null，表示不支持特征强制执行
     * 3. 子类可以覆盖此方法以支持特征强制执行
     * 
     * 【使用场景】：
     * - 在物理优化阶段，尝试通过添加物理算子来满足特征要求
     * - Impl 类不支持特征强制执行，所以返回 null
     * - PhysicalConvention 可能覆盖此方法以支持特征强制执行
     * 
     * 【示例】：
     * - Impl.enforce(input, required) 返回 null
     * - PhysicalConvention.enforce(input, required) 可能返回 SortRel 或 ExchangeRel
     */
    @Override public @Nullable RelNode enforce(final RelNode input, // 特征强制执行方法
        final RelTraitSet required) {
      return null; // 返回 null，表示不支持
    }

    /**
     * 【方法作用】：检查是否可以转换到目标 Convention
     * 
     * 【参数说明】：
     * - toConvention：目标 Convention
     * 
     * 【返回值说明】：返回 false，表示不允许转换
     * 
     * 【详细解释】：
     * 1. 实现自 Convention 接口
     * 2. 默认实现返回 false，表示不允许转换
     * 3. 子类可以覆盖此方法以允许特定的转换
     * 
     * 【使用场景】：
     * - 在优化器注册转换规则时，检查转换是否被允许
     * - Impl 类不允许任何转换，所以返回 false
     * - 其他 Convention 可能覆盖此方法以允许特定的转换
     * 
     * 【示例】：
     * - Impl.canConvertConvention(any) 返回 false
     * - EnumerableConvention.canConvertConvention(PhysicalConvention) 可能返回 true
     */
    @Override public boolean canConvertConvention(Convention toConvention) { // 检查是否可以转换
      return false; // 返回 false，表示不允许转换
    }

    /**
     * 【方法作用】：是否使用抽象转换器进行特征转换
     * 
     * 【参数说明】：
     * - fromTraits：源特征集合
     * - toTraits：目标特征集合
     * 
     * 【返回值说明】：返回 false，表示不使用抽象转换器
     * 
     * 【详细解释】：
     * 1. 实现自 Convention 接口
     * 2. 默认实现返回 false，表示不使用抽象转换器
     * 3. 子类可以覆盖此方法以使用抽象转换器
     * 
     * 【使用场景】：
     * - 在特征转换时，决定是否需要添加抽象转换器
     * - Impl 类不使用抽象转换器，所以返回 false
     * - PhysicalConvention 可能覆盖此方法以使用抽象转换器
     * 
     * 【示例】：
     * - Impl.useAbstractConvertersForConversion(any, any) 返回 false
     * - PhysicalConvention.useAbstractConvertersForConversion(from, to) 可能返回 true
     */
    @Override public boolean useAbstractConvertersForConversion(RelTraitSet fromTraits, // 是否使用抽象转换器
        RelTraitSet toTraits) {
      return false; // 返回 false，表示不使用
    }
  }
}
