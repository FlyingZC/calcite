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
 */ // Apache许可证声明，规定代码的使用权限和限制条件
package org.apache.calcite.interpreter; // 声明当前类属于org.apache.calcite.interpreter包，这是Calcite解释器模块的包路径

import org.apache.calcite.adapter.enumerable.EnumerableRel; // 导入EnumerableRel接口，表示可枚举的关系表达式，用于支持LINQ风格的查询
import org.apache.calcite.plan.Convention; // 导入Convention接口，这是Calcite中调用约定的基类，定义了关系表达式如何生成结果的规范
import org.apache.calcite.plan.ConventionTraitDef; // 导入ConventionTraitDef类，这是Convention的特征定义，用于管理调用约定的特征
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，这是关系优化计划器，负责优化关系表达式树
import org.apache.calcite.plan.RelTrait; // 导入RelTrait接口，这是关系特征的基类，特征是关系表达式的一种属性或约束
import org.apache.calcite.plan.RelTraitDef; // 导入RelTraitDef接口，这是关系特征定义的基类，用于定义和管理特征
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，这是关系特征集合，用于存储和管理关系表达式的一组特征

/**
 * Calling convention that returns results as an
 * {@link org.apache.calcite.linq4j.Enumerable} of object arrays.
 * 调用约定，以对象数组的 {@link org.apache.calcite.linq4j.Enumerable} 形式返回结果。
 *
 * <p>Unlike enumerable convention, no code generation is required.
 * 与可枚举约定不同，不需要代码生成。
 *
 * 【类作用详解】：
 * InterpretableConvention是Calcite框架中的一个特殊的调用约定（Calling Convention），
 * 它定义了关系表达式如何以解释器方式执行并返回结果。
 *
 * 核心概念说明：
 * 1. 调用约定（Convention）：在Calcite中，Convention定义了关系表达式如何生成查询结果的方式。
 *    不同的Convention代表不同的执行策略，比如：
 *    - EnumerableConvention：通过代码生成生成Java字节码来执行查询
 *    - InterpretableConvention：通过解释器解释执行查询，不需要代码生成
 *    - 其他数据源特定的Convention（如JdbcConvention、MongoConvention等）
 *
 * 2. 解释器模式 vs 代码生成模式：
 *    - 代码生成模式（EnumerableConvention）：在运行时生成Java代码，编译并执行，性能高但启动慢
 *    - 解释器模式（InterpretableConvention）：直接解释执行关系表达式树，启动快但运行时性能相对较低
 *
 * 3. 为什么需要InterpretableConvention：
 *    - 快速原型开发：不需要代码生成，可以立即执行查询
 *    - 调试和测试：更容易追踪执行过程，适合调试
 *    - 动态查询：对于频繁变化的查询，避免重复代码生成的开销
 *    - 某些场景下的性能优势：对于简单查询，解释器可能比代码生成更快
 *
 * 4. 实现方式：
 *    - 使用枚举类型实现，确保全局唯一实例（单例模式）
 *    - 实现Convention接口，遵循Calcite的调用约定规范
 *    - 返回EnumerableRel接口类型，表示结果以可枚举形式提供
 *    - 禁止自动转换到其他约定，保持解释器模式的独立性
 *
 * 5. 使用场景：
 *    - Calcite的Interpreter模块使用此约定来解释执行关系表达式
 *    - 适合需要快速执行、调试或测试的场景
 *    - 当代码生成开销过大或不适用时使用
 */
public enum InterpretableConvention implements Convention { // 定义一个枚举类InterpretableConvention，实现Convention接口，采用枚举确保全局单例
  INSTANCE; // 枚举常量INSTANCE，表示InterpretableConvention的唯一实例，这是单例模式的实现方式

  @Override public String toString() { // 重写toString方法，用于将枚举实例转换为字符串表示
    return getName(); // 返回约定的名称，通过调用getName()方法获取"INTERPRETABLE"字符串
  }

  @Override public Class getInterface() { // 重写getInterface方法，定义实现此约定的关系表达式必须实现的接口
    return EnumerableRel.class; // 返回EnumerableRel.class，表示实现InterpretableConvention的关系表达式必须是EnumerableRel类型
    // 这意味着解释器模式的关系表达式需要支持可枚举的结果集
  }

  @Override public String getName() { // 重写getName方法，返回约定的名称
    return "INTERPRETABLE"; // 返回字符串"INTERPRETABLE"，这是InterpretableConvention的标识名称
    // 这个名称在日志、调试和规划器中用于标识此约定
  }

  @Override public RelTraitDef getTraitDef() { // 重写getTraitDef方法，返回此约定的特征定义
    return ConventionTraitDef.INSTANCE; // 返回ConventionTraitDef.INSTANCE，表示这是Convention类型的特征定义
    // ConventionTraitDef是管理所有Convention实例的特征定义器
  }

  @Override public boolean satisfies(RelTrait trait) { // 重写satisfies方法，检查给定的特征是否满足此约定
    return this == trait; // 比较当前实例与传入的特征是否相同，只有相同的InterpretableConvention实例才满足
    // 这确保了特征匹配的严格性，只有完全相同的约定才能匹配
  }

  @Override public void register(RelOptPlanner planner) {} // 重写register方法，向优化计划器注册此约定
  // 空实现表示InterpretableConvention不需要特殊的注册逻辑
  // 某些Convention可能需要注册转换规则或其他元数据，但InterpretableConvention不需要

  @Override public boolean canConvertConvention(Convention toConvention) { // 重写canConvertConvention方法，检查是否可以转换到另一个约定
    return false; // 返回false，表示InterpretableConvention不能转换到其他约定
    // 这意味着解释器模式的关系表达式不会被自动转换到其他执行模式
    // 保持解释器模式的独立性和可预测性
  }

  @Override public boolean useAbstractConvertersForConversion(RelTraitSet fromTraits, // 重写useAbstractConvertersForConversion方法，检查转换时是否使用抽象转换器
      RelTraitSet toTraits) { // 参数toTraits：目标特征集合，表示要转换到的特征组合
    return false; // 返回false，表示不使用抽象转换器进行转换
    // 由于canConvertConvention返回false，这个方法实际上不会被调用
    // 但返回false确保了一致性，表明InterpretableConvention不参与自动转换
  }
} // 类定义结束，InterpretableConvention枚举类完成
