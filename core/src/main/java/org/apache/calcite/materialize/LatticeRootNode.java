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
package org.apache.calcite.materialize; // 声明包名，该类属于org.apache.calcite.materialize包，用于处理物化视图相关的功能

import org.apache.calcite.util.Litmus; // 导入Litmus工具类，用于验证和报告错误状态

import com.google.common.collect.ImmutableList; // 导入Google Guava库的不可变列表类，用于存储不可变的节点列表

import java.util.ArrayList; // 导入Java标准库的动态数组类，用于构建可变的节点列表
import java.util.List; // 导入Java标准库的列表接口，用于存储节点集合

/** Root node in a {@link Lattice}. It has no parent. */ // 类注释：Lattice（多维数据集）中的根节点，没有父节点，是整个节点树的起点
public class LatticeRootNode extends LatticeNode { // 定义LatticeRootNode类，继承自LatticeNode基类，表示Lattice的根节点
  /** Descendants, in prefix order. This root node is at position 0. */ // 注释：descendants字段存储所有后代节点，按前序遍历顺序排列，根节点位于位置0
  public final ImmutableList<LatticeNode> descendants; // 成员变量：不可变的后代节点列表，包含从根节点开始的所有节点，按前序遍历顺序存储
  final ImmutableList<Path> paths; // 成员变量：不可变的路径列表，存储从根节点到各个子节点的所有可能路径，用于查询优化和路径选择

  @SuppressWarnings("method.invocation.invalid") // 注解：抑制编译器警告，因为构造函数中调用了父类方法，这在某些情况下可能被认为是无效的
  LatticeRootNode(LatticeSpace space, MutableNode mutableNode) { // 构造函数：创建LatticeRootNode实例，参数space表示Lattice空间，mutableNode表示可变节点
    super(space, null, mutableNode); // 调用父类LatticeNode的构造函数，传入space、null（因为根节点没有父节点）和mutableNode

    final ImmutableList.Builder<LatticeNode> b = ImmutableList.builder(); // 创建不可变列表构建器，用于构建后代节点列表
    flattenTo(b); // 调用flattenTo方法（继承自父类），将节点树扁平化并添加到构建器中，转换为前序遍历的列表
    this.descendants = b.build(); // 构建不可变的后代节点列表并赋值给descendants字段
    this.paths = createPaths(space); // 调用createPaths方法创建所有可能的路径列表并赋值给paths字段
  }

  private ImmutableList<Path> createPaths(LatticeSpace space) { // 私有方法：创建从根节点到各个子节点的所有路径，参数space表示Lattice空间
    final List<Step> steps = new ArrayList<>(); // 创建步骤列表，用于临时存储路径中的步骤（边）
    final List<Path> paths = new ArrayList<>(); // 创建路径列表，用于存储生成的所有路径
    createPathsRecurse(space, steps, paths); // 调用递归方法createPathsRecurse，深度优先遍历节点树并生成所有路径
    assert steps.isEmpty(); // 断言：确保递归结束后steps列表为空，表示所有步骤都已被正确处理和清理
    return ImmutableList.copyOf(paths); // 将可变的路径列表转换为不可变列表并返回，确保路径集合的不可变性
  }

  @Override void use(List<LatticeNode> usedNodes) { // 重写父类方法：标记当前节点为已使用，参数usedNodes存储已使用的节点列表
    if (!usedNodes.contains(this)) { // 检查当前节点是否已经在usedNodes列表中
      usedNodes.add(this); // 如果当前节点未被使用，则将其添加到usedNodes列表中，标记为已使用
    }
  }

  /** Validates that nodes form a tree; each node except the first references
   * a predecessor. */ // 方法注释：验证节点是否构成一棵树，除第一个节点外，每个节点都必须引用一个前驱节点（父节点）
  boolean isValid(Litmus litmus) { // 方法：验证节点树的有效性，参数litmus用于验证和报告错误状态，返回验证结果
    for (int i = 0; i < descendants.size(); i++) { // 遍历所有后代节点，使用索引i进行迭代
      LatticeNode node = descendants.get(i); // 获取当前位置的节点
      if (i == 0) { // 如果是第一个节点（索引为0）
        if (node != this) { // 检查第一个节点是否为根节点（this）
          return litmus.fail("node 0 should be root"); // 如果不是根节点，验证失败并返回错误信息
        }
      } else { // 如果不是第一个节点
        if (!(node instanceof LatticeChildNode)) { // 检查节点是否为LatticeChildNode类型（子节点）
          return litmus.fail("node after 0 should be child"); // 如果不是子节点类型，验证失败并返回错误信息
        }
        final LatticeChildNode child = (LatticeChildNode) node; // 将节点强制转换为LatticeChildNode类型
        if (!descendants.subList(0, i).contains(child.parent)) { // 检查子节点的父节点是否出现在当前节点之前的位置列表中
          return litmus.fail("parent not in preceding list"); // 如果父节点不在前置列表中，验证失败并返回错误信息
        }
      }
    }
    return litmus.succeed(); // 所有节点都通过验证，返回成功状态
  }


  /** Whether this node's graph is a super-set of (or equal to) another node's
   * graph. */ // 方法注释：判断当前节点的图是否是另一个节点图的超集（或相等），用于比较两个Lattice的覆盖范围
  public boolean contains(LatticeRootNode node) { // 公共方法：检查当前根节点的路径集合是否包含另一个根节点的所有路径
    return paths.containsAll(node.paths); // 使用containsAll方法比较路径集合，如果当前paths包含node的所有paths则返回true
  }

} // 类定义结束
