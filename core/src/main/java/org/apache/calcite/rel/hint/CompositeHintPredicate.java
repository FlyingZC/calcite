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
package org.apache.calcite.rel.hint;  // 包声明：定义该类属于org.apache.calcite.rel.hint包，这是Calcite框架中处理关系表达式提示(hint)的包

import org.apache.calcite.rel.RelNode;  // 导入RelNode类：关系表达式节点，是Calcite中所有关系操作符的基类

import com.google.common.collect.ImmutableList;  // 导入ImmutableList类：Google Guava库提供的不可变列表实现，用于存储不可变的谓词列表

import static com.google.common.base.Preconditions.checkArgument;  // 导入checkArgument静态方法：用于参数校验，如果参数不满足条件则抛出IllegalArgumentException异常

/**
 * A {@link HintPredicate} to combine multiple hint predicates into one.
 * 一个HintPredicate实现类，用于将多个提示谓词组合成一个复合谓词
 *
 * <p>The composition can be {@code AND} or {@code OR}.
 * 组合方式可以是AND（逻辑与）或OR（逻辑或），用于决定多个谓词的组合逻辑
 * 
 * <p>该类实现了HintPredicate接口，用于判断给定的hint是否适用于某个RelNode
 * 它通过组合多个HintPredicate来实现复合的判断逻辑，支持AND和OR两种组合方式
 * AND方式：所有子谓词都返回true时，复合谓词才返回true
 * OR方式：只要有一个子谓词返回true，复合谓词就返回true
 */
public class CompositeHintPredicate implements HintPredicate {  // 类声明：CompositeHintPredicate实现了HintPredicate接口，表示这是一个复合的提示谓词
  //~ Enums ------------------------------------------------------------------  // 枚举类型部分：定义该类中使用的枚举类型

  /** How hint predicates are composed. */
  /** 枚举类型：定义提示谓词的组合方式，用于决定多个谓词如何组合在一起 */
  public enum Composition {  // Composition枚举：表示谓词的组合逻辑
    AND, OR  // AND表示逻辑与组合，OR表示逻辑或组合
  }

  //~ Instance fields --------------------------------------------------------  // 实例字段部分：定义该类的成员变量

  private final ImmutableList<HintPredicate> predicates;  // predicates成员变量：存储所有子谓词的不可变列表，使用ImmutableList确保线程安全和不可变性
  private final Composition composition;  // composition成员变量：存储谓词的组合方式(AND或OR)，使用final修饰确保初始化后不可修改

  /**
   * Creates a {@link CompositeHintPredicate} with a {@link Composition}
   * and an array of hint predicates.
   * 创建一个CompositeHintPredicate对象，需要指定组合方式和一组提示谓词
   *
   * <p>Make this constructor package-protected intentionally.
   * 该构造函数被有意设置为包级访问权限，防止外部直接创建实例
   * 
   * <p>Use utility methods in {@link HintPredicates}
   * to create a {@link CompositeHintPredicate}.
   * 建议使用HintPredicates工具类中的方法来创建CompositeHintPredicate实例
   * 
   * @param composition 谓词的组合方式，必须是AND或OR
   * @param predicates 可变参数数组，包含至少两个HintPredicate对象，用于组合成复合谓词
   */
  CompositeHintPredicate(Composition composition, HintPredicate... predicates) {  // 构造函数：接收组合方式和可变数量的谓词参数
    this.predicates = ImmutableList.copyOf(predicates);  // 将传入的谓词数组转换为不可变的ImmutableList，确保谓词列表不会被修改
    checkArgument(this.predicates.size() > 1);  // 参数校验：确保至少有两个谓词，否则抛出IllegalArgumentException异常
    this.composition = composition;  // 保存组合方式到成员变量
  }

  //~ Methods ----------------------------------------------------------------  // 方法部分：定义该类的方法

  @Override public boolean apply(RelHint hint, RelNode rel) {  // apply方法：实现HintPredicate接口的方法，判断给定的hint是否适用于rel
    return apply(composition, hint, rel);  // 调用私有apply方法，传入组合方式、hint和rel参数，返回判断结果
  }

  private boolean apply(Composition composition, RelHint hint, RelNode rel) {  // 私有apply方法：根据组合方式执行谓词判断逻辑
    switch (composition) {  // 根据组合方式进行分支判断
    case AND:  // AND组合方式：所有谓词都必须返回true
      for (HintPredicate predicate : predicates) {  // 遍历所有子谓词
        if (!predicate.apply(hint, rel)) {  // 如果任意一个谓词返回false
          return false;  // 立即返回false，AND逻辑中只要有一个false，整体结果就是false
        }
      }
      return true;  // 所有谓词都返回true，返回true
    case OR:  // OR组合方式：只要有一个谓词返回true即可
    default:  // 默认情况也使用OR逻辑
      for (HintPredicate predicate : predicates) {  // 遍历所有子谓词
        if (predicate.apply(hint, rel)) {  // 如果任意一个谓词返回true
          return true;  // 立即返回true，OR逻辑中只要有一个true，整体结果就是true
        }
      }
      return false;  // 所有谓词都返回false，返回false
    }
  }
}
