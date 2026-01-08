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
package org.apache.calcite.plan;  // 声明包名，该类属于org.apache.calcite.plan包，是Calcite优化器规则匹配核心包

import com.google.common.collect.ImmutableList;  // 导入Google Guava库的不可变列表类，用于创建不可变的操作数列表

import java.util.List;  // 导入Java标准库的List接口，用于方法参数类型

/**
 * Children of a {@link org.apache.calcite.plan.RelOptRuleOperand} and the  // RelOptRuleOperand的子节点及其匹配策略的描述类，用于定义规则操作数的子节点如何匹配
 * policy for matching them.  // 匹配策略决定了子节点的匹配方式和规则
 *
 * <p>Often created by calling one of the following methods:  // 通常通过调用以下方法创建该类的实例，这些方法在RelOptRule类中定义
 * {@link RelOptRule#some},  // some()方法表示匹配特定数量和顺序的子节点
 * {@link RelOptRule#none},  // none()方法表示不匹配任何子节点，即叶子节点
 * {@link RelOptRule#any},  // any()方法表示匹配任意数量的子节点
 * {@link RelOptRule#unordered}.  // unordered()方法表示匹配无序的子节点集合
 *
 * @deprecated Use {@link RelRule.OperandBuilder}  // 该类已废弃，建议使用RelRule.OperandBuilder替代，这是Calcite新版本推荐的方式
 */
@Deprecated // to be removed before 2.0  // 标记为废弃，计划在2.0版本前移除
public class RelOptRuleOperandChildren {  // 类定义：RelOptRuleOperandChildren，表示规则操作数的子节点集合及匹配策略
  static final RelOptRuleOperandChildren ANY_CHILDREN =  // 静态常量：表示匹配任意子节点的策略实例，用于定义规则可以匹配任意数量和类型的子节点
      new RelOptRuleOperandChildren(  // 创建新的RelOptRuleOperandChildren实例
          RelOptRuleOperandChildPolicy.ANY,  // 使用ANY策略，表示匹配任意数量的子节点
          ImmutableList.of());  // 空的不可变列表，因为ANY策略不需要具体的子节点定义

  static final RelOptRuleOperandChildren LEAF_CHILDREN =  // 静态常量：表示叶子节点的策略实例，用于定义规则不匹配任何子节点，即该操作数是叶子节点
      new RelOptRuleOperandChildren(  // 创建新的RelOptRuleOperandChildren实例
          RelOptRuleOperandChildPolicy.LEAF,  // 使用LEAF策略，表示该操作数没有子节点
          ImmutableList.of());  // 空的不可变列表，因为LEAF策略没有子节点

  final RelOptRuleOperandChildPolicy policy;  // 成员变量：子节点匹配策略，枚举类型，定义了子节点的匹配规则（如ANY、LEAF、SOME、UNORDERED等）
  final ImmutableList<RelOptRuleOperand> operands;  // 成员变量：不可变的操作数列表，存储了所有子节点的操作数定义，每个RelOptRuleOperand定义了一个子节点的匹配条件

  public RelOptRuleOperandChildren(  // 构造方法：创建RelOptRuleOperandChildren实例，初始化子节点的匹配策略和操作数列表
      RelOptRuleOperandChildPolicy policy,  // 参数：子节点匹配策略，指定如何匹配子节点（如ANY、LEAF、SOME等）
      List<RelOptRuleOperand> operands) {  // 参数：子节点操作数列表，定义了每个子节点的具体匹配条件和类型
    this.policy = policy;  // 将传入的策略参数赋值给成员变量policy，保存子节点的匹配策略
    this.operands = ImmutableList.copyOf(operands);  // 将传入的操作数列表转换为不可变列表并赋值给成员变量operands，确保操作数列表不会被修改
  }
}
