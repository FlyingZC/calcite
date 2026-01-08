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
// Apache许可证声明，说明版权和使用许可信息
package org.apache.calcite.rel;  // 声明包名，该类位于org.apache.calcite.rel包下，是Calcite关系表达式核心包

import org.apache.calcite.rel.core.CorrelationId;  // 导入CorrelationId类，用于表示关联ID，用于处理子查询中的关联变量
import org.apache.calcite.util.Litmus;  // 导入Litmus类，用于定义验证失败时的行为模式（如抛出异常、断言失败等）

import com.google.common.collect.ImmutableSet;  // 导入Guava的ImmutableSet类，用于创建不可变的集合对象

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入Nullable注解，用于标记可能为null的参数或返回值

import java.util.ArrayDeque;  // 导入ArrayDeque类，基于数组的双端队列实现，用作栈结构
import java.util.Deque;  // 导入Deque接口，定义双端队列的操作规范
import java.util.Set;  // 导入Set接口，定义集合的操作规范

/**
 * Visitor that checks that every {@link RelNode} in a tree is valid.
 * 访问者模式实现，用于检查关系表达式树中的每一个RelNode节点是否有效
 * 该类继承自RelVisitor，实现了深度优先遍历关系表达式树的功能
 * 同时实现了RelNode.Context接口，为节点验证提供上下文信息（如关联ID集合）
 * 
 * 核心功能：
 * 1. 遍历整个RelNode树结构，访问每个节点
 * 2. 对每个节点调用isValid方法进行有效性验证
 * 3. 统计验证失败的节点数量
 * 4. 维护当前遍历路径上的节点栈，用于计算上下文信息
 *
 * @see RelNode#isValid(Litmus, RelNode.Context)  // 参见RelNode的isValid方法，该方法是节点验证的核心入口
 */
public class RelValidityChecker extends RelVisitor  // 定义RelValidityChecker类，继承RelVisitor以获得关系表达式树的遍历能力
    implements RelNode.Context {  // 实现RelNode.Context接口，为节点验证提供上下文环境（如关联变量集合）
  private int invalidCount;  // 记录验证失败的节点数量，初始值为0，每次遇到无效节点时递增
  private final Deque<RelNode> stack = new ArrayDeque<>();  // 维护当前遍历路径上的节点栈，使用ArrayDeque实现栈结构，用于计算当前上下文中的所有关联变量

  @Override public Set<CorrelationId> correlationIds() {  // 重写RelNode.Context接口的correlationIds方法，返回当前上下文中所有关联变量的集合
    final ImmutableSet.Builder<CorrelationId> builder =  // 创建ImmutableSet构建器，用于构建不可变的关联ID集合
        ImmutableSet.builder();  // 调用builder()方法获取构建器实例
    for (RelNode r : stack) {  // 遍历栈中的每个RelNode节点（从当前节点向上遍历到根节点）
      builder.addAll(r.getVariablesSet());  // 将每个节点使用的关联变量ID添加到构建器中，getVariablesSet()返回该节点引用的所有关联变量
    }
    return builder.build();  // 构建并返回不可变的关联ID集合，该集合包含当前路径上所有节点使用的关联变量
  }

  @Override public void visit(RelNode node, int ordinal,  // 重写RelVisitor的visit方法，访问关系表达式树中的每个节点，node为当前访问的节点，ordinal为该节点在父节点子节点列表中的索引位置
      @Nullable RelNode parent) {  // parent为当前节点的父节点，可能为null（如根节点没有父节点），使用@Nullable注解标记
    try {  // 使用try-finally确保栈操作的正确性，即使发生异常也能保证栈状态一致
      stack.push(node);  // 将当前节点压入栈顶，维护从根节点到当前节点的路径，用于计算上下文信息
      if (!node.isValid(Litmus.THROW, this)) {  // 调用当前节点的isValid方法进行验证，Litmus.THROW表示验证失败时抛出异常，this作为上下文提供关联变量信息
        ++invalidCount;  // 如果节点验证失败（isValid返回false），则递增无效节点计数器
      }
      super.visit(node, ordinal, parent);  // 调用父类RelVisitor的visit方法，继续深度优先遍历子节点，递归访问当前节点的所有子节点
    } finally {  // finally块确保无论是否发生异常都会执行
      stack.pop();  // 将当前节点从栈顶弹出，恢复到访问该节点之前的栈状态，确保回溯时路径正确
    }
  }

  public int invalidCount() {  // 定义公共方法invalidCount，用于获取验证过程中发现的无效节点总数
    return invalidCount;  // 返回invalidCount字段的值，即验证失败的RelNode节点数量
  }
}
