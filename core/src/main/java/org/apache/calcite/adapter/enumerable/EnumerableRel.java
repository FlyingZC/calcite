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
package org.apache.calcite.adapter.enumerable; // 定义包名，该包包含可枚举关系表达式相关的类

import org.apache.calcite.linq4j.tree.BlockStatement; // 导入LINQ4j的块语句类，用于表示Java代码块
import org.apache.calcite.plan.DeriveMode; // 导入派生模式枚举，定义特征派生的策略
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集类，表示关系表达式的特征集合
import org.apache.calcite.rel.PhysicalNode; // 导入物理节点接口，表示物理执行计划节点
import org.apache.calcite.util.Pair; // 导入Pair工具类，用于存储键值对

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的返回值

import java.util.List; // 导入List接口，用于存储列表数据

/**
 * A relational expression of one of the // 这是一个关系表达式，属于
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention} calling // 可枚举约定调用约定之一
 * conventions. // 用于定义可枚举关系表达式的接口
 */
public interface EnumerableRel // 定义可枚举关系表达式接口，这是Calcite中用于生成Java代码执行查询的核心接口
    extends PhysicalNode { // 继承物理节点接口，表示这是一个物理执行节点

  //~ Methods ---------------------------------------------------------------- // 方法分隔符

  @Override default @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraits( // 重写特征传递方法，返回可能为null的键值对，包含特征集和特征集列表
      RelTraitSet required) { // 参数：要求的特征集
    return null; // 默认返回null，表示不传递特征
  }

  @Override default @Nullable Pair<RelTraitSet, List<RelTraitSet>> deriveTraits( // 重写特征派生方法，返回可能为null的键值对，包含特征集和特征集列表
      RelTraitSet childTraits, int childId) { // 参数：子节点的特征集和子节点ID
    return null; // 默认返回null，表示不派生特征
  }

  @Override default DeriveMode getDeriveMode() { // 重写获取派生模式方法
    return DeriveMode.LEFT_FIRST; // 返回左优先派生模式，表示优先从左侧子节点派生特征
  }

  /**
   * Creates a plan for this expression according to a calling convention. // 根据调用约定为此表达式创建执行计划
   *
   * @param implementor Implementor // 参数：实现器，用于生成代码
   * @param pref Preferred representation for rows in result expression // 参数：结果表达式中行的首选表示形式
   * @return Plan for this expression according to a calling convention // 返回：根据调用约定生成的执行计划
   */
  Result implement(EnumerableRelImplementor implementor, Prefer pref); // 核心方法：实现可枚举关系表达式，生成Java代码执行计划

  /** Preferred physical type. */ // 首选物理类型枚举
  enum Prefer { // 定义行数据表示形式的枚举
    /** Records must be represented as arrays. */ // 记录必须表示为数组
    ARRAY, // 数组格式
    /** Consumer would prefer that records are represented as arrays, but can */ // 消费者更倾向于数组格式，但也可以接受对象格式
    /** accommodate records represented as objects. */ // 能够适应对象表示的记录
    ARRAY_NICE, // 数组优先但可选
    /** Records must be represented as objects. */ // 记录必须表示为对象
    CUSTOM, // 自定义对象格式
    /** Consumer would prefer that records are represented as objects, but can */ // 消费者更倾向于对象格式，但也可以接受数组格式
    /** accommodate records represented as arrays. */ // 能够适应数组表示的记录
    CUSTOM_NICE, // 对象优先但可选
    /** Consumer has no preferred representation. */ // 消费者没有首选表示形式
    ANY; // 任意格式

    public JavaRowFormat preferCustom() { // 方法：优先选择自定义对象格式
      return prefer(JavaRowFormat.CUSTOM); // 调用prefer方法返回CUSTOM格式
    }

    public JavaRowFormat preferArray() { // 方法：优先选择数组格式
      return prefer(JavaRowFormat.ARRAY); // 调用prefer方法返回ARRAY格式
    }

    public JavaRowFormat prefer(JavaRowFormat format) { // 方法：根据当前枚举值和参数格式确定最终格式
      switch (this) { // 根据当前枚举值进行分支
      case CUSTOM: // 如果是CUSTOM
        return JavaRowFormat.CUSTOM; // 返回CUSTOM格式
      case ARRAY: // 如果是ARRAY
        return JavaRowFormat.ARRAY; // 返回ARRAY格式
      default: // 其他情况
        return format; // 返回传入的格式参数
      }
    }

    public Prefer of(JavaRowFormat format) { // 方法：根据Java行格式创建对应的Prefer枚举值
      switch (format) { // 根据格式参数进行分支
      case ARRAY: // 如果是ARRAY格式
        return ARRAY; // 返回ARRAY枚举值
      default: // 其他情况
        return CUSTOM; // 返回CUSTOM枚举值
      }
    }
  }

  /** Result of implementing an enumerable relational expression by generating */ // 实现可枚举关系表达式的结果
  /** Java code. */ // 通过生成Java代码实现
  class Result { // 定义结果内部类，封装实现结果
    public final BlockStatement block; // 成员变量：生成的Java代码块语句

    /**
     * Describes the Java type returned by this relational expression, and the // 描述此关系表达式返回的Java类型，以及
     * mapping between it and the fields of the logical row type. // 它与逻辑行类型字段之间的映射关系
     */
    public final PhysType physType; // 成员变量：物理类型，描述Java类型和字段映射
    public final JavaRowFormat format; // 成员变量：Java行格式，表示行的表示形式

    public Result(BlockStatement block, PhysType physType, // 构造方法：创建Result实例
        JavaRowFormat format) { // 参数：代码块、物理类型、行格式
      this.block = block; // 初始化代码块成员变量
      this.physType = physType; // 初始化物理类型成员变量
      this.format = format; // 初始化行格式成员变量
    }
  }
}
