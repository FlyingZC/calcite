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
package org.apache.calcite.plan.volcano; // 包声明：Volcano优化器相关的包，包含火山优化器的核心实现

import org.apache.calcite.plan.RelOptRuleOperand; // 导入RelOptRuleOperand类：表示优化规则的操作数
import org.apache.calcite.rel.RelNode; // 导入RelNode类：表示关系代数表达式节点
import org.apache.calcite.util.Litmus; // 导入Litmus类：用于断言和验证的工具类

import java.util.List; // 导入List接口：Java集合框架的列表接口
import java.util.Map; // 导入Map接口：Java集合框架的映射接口

/**
 * A match of a rule to a particular set of target relational expressions, // 类的作用：表示一个规则与特定目标关系表达式集合的匹配
 * frozen in time. // 这个匹配是在特定时间点冻结的，意味着它捕获了某个时刻的状态
 * 
 * VolcanoRuleMatch是VolcanoPlanner优化器中的核心类之一，用于表示：
 * 1. 一个优化规则（RelOptRule）与一组关系表达式（RelNode）的匹配情况
 * 2. 当优化器发现某个规则可以应用于某个关系表达式时，就会创建一个VolcanoRuleMatch对象
 * 3. 这个对象记录了规则、匹配的关系表达式以及它们之间的映射关系
 * 4. 通过digest字段可以唯一标识这个匹配，用于去重和比较
 * 
 * 典型使用场景：
 * - 在优化过程中，优化器会不断尝试将规则应用于关系表达式
 * - 每次成功匹配都会创建一个VolcanoRuleMatch对象
 * - 这些匹配对象会被放入优先级队列中，按照某种策略选择执行
 * - 执行匹配会生成新的关系表达式，进一步推动优化过程
 */
class VolcanoRuleMatch extends VolcanoRuleCall { // 类定义：继承自VolcanoRuleCall，表示规则匹配的调用
  //~ Instance fields -------------------------------------------------------- // 成员变量区域的分隔符

  private String digest; // 成员变量：digest（摘要/指纹），用于唯一标识这个规则匹配
                        // 作用：通过digest可以快速判断两个规则匹配是否相同，避免重复应用相同的规则
                        // digest的格式为："rule [规则名称] rels [关系表达式ID1,关系表达式ID2,...]"
                        // 例如："rule [FilterToCalcRule] rels [#123,#456]"
                        // 这个字段在构造函数中通过computeDigest()方法计算并赋值

  //~ Constructors ----------------------------------------------------------- // 构造函数区域的分隔符

  /**
   * Creates a <code>VolcanoRuleMatch</code>. // 创建一个VolcanoRuleMatch对象
   *
   * @param operand0 Primary operand // 参数：operand0，主要操作数（RelOptRuleOperand类型），表示规则的主要操作数
   * @param rels     List of targets; copied by the constructor, so the client // 参数：rels，目标关系表达式数组，被构造函数复制，所以客户端可以后续修改原始数组
   *                 can modify it later // 说明：构造函数会克隆这个数组，确保外部修改不影响内部状态
   * @param nodeInputs Map from relational expressions to their inputs // 参数：nodeInputs，关系表达式到其输入的映射，用于表示关系表达式树的结构
   */
  @SuppressWarnings("method.invocation.invalid") // 注解：抑制方法调用无效的警告，因为这里调用父类构造函数时可能触发某些静态分析警告
  VolcanoRuleMatch(VolcanoPlanner volcanoPlanner, RelOptRuleOperand operand0, // 构造函数：创建VolcanoRuleMatch对象，参数包括优化器、操作数、关系表达式数组和输入映射
      RelNode[] rels, Map<RelNode, List<RelNode>> nodeInputs) { // 参数继续
    super(volcanoPlanner, operand0, rels.clone(), nodeInputs); // 调用父类VolcanoRuleCall的构造函数，传入克隆的关系表达式数组以确保安全
    assert allNotNull(rels, Litmus.THROW); // 断言：确保rels数组中的所有元素都不为null，如果为null则抛出异常

    digest = computeDigest(); // 计算并设置digest值，通过computeDigest()方法生成唯一标识字符串
  }

  //~ Methods ---------------------------------------------------------------- // 方法区域的分隔符

  @Override public String toString() { // 方法：重写toString()方法，返回对象的字符串表示
    return digest; // 返回digest字段，即规则匹配的摘要信息
  }

  /**
   * Computes a string describing this rule match. Two rule matches are // 方法：计算描述这个规则匹配的字符串。两个规则匹配等价当且仅当它们的digest相同
   * equivalent if and only if their digests are the same. // 说明：digest的相等性是判断规则匹配是否相同的唯一标准
   *
   * @return description of this rule match // 返回值：规则匹配的描述字符串
   */
  private String computeDigest() { // 方法：计算并返回规则匹配的摘要字符串
    StringBuilder buf = // 创建StringBuilder对象用于构建字符串
        new StringBuilder("rule [" + getRule() + "] rels ["); // 初始化字符串，包含规则名称
    for (int i = 0; i < rels.length; i++) { // 遍历关系表达式数组
      if (i > 0) { // 如果不是第一个元素
        buf.append(','); // 添加逗号分隔符
      }
      buf.append('#').append(rels[i].getId()); // 添加关系表达式的ID，格式为#ID
    }
    buf.append(']'); // 添加右括号
    return buf.toString(); // 返回构建好的字符串
  }

  /**
   * Recomputes the digest of this VolcanoRuleMatch. // 方法：重新计算这个VolcanoRuleMatch的digest值
   * 
   * 注意：这个方法已被标记为@Deprecated，将在2.0版本之前移除
   * 原因：digest在构造时就应该确定，不应该在后续被修改
   * 如果需要重新计算，应该创建新的VolcanoRuleMatch对象
   */
  @Deprecated // to be removed before 2.0 // 注解：标记为已废弃，将在2.0版本之前移除
  public void recomputeDigest() { // 方法：重新计算digest值
    digest = computeDigest(); // 调用computeDigest()方法重新计算并赋值给digest字段
  }

  /** Returns whether all elements of a given array are not-null; // 方法：判断给定数组的所有元素是否都不为null
   * fails if any are null. // 说明：如果任何元素为null，则验证失败
   * 
   * 这是一个静态泛型方法，用于验证数组中不存在null元素
   * 主要用于构造函数中的断言检查，确保传入的rels数组不包含null值
   * 
   * @param <E> 泛型类型参数，可以是任何对象类型
   * @param es 要检查的数组
   * @param litmus Litmus对象，用于处理断言结果（成功或失败）
   * @return 如果所有元素都不为null返回true，否则返回false
   */
  private static <E> boolean allNotNull(E[] es, Litmus litmus) { // 静态泛型方法：检查数组元素是否都不为null
    for (E e : es) { // 遍历数组中的每个元素
      if (e == null) { // 如果当前元素为null
        return litmus.fail("was null", (Object) es); // 调用litmus.fail()方法记录失败并返回false
      }
    }
    return litmus.succeed(); // 如果所有元素都不为null，调用litmus.succeed()方法返回true
  }

} // 类结束
