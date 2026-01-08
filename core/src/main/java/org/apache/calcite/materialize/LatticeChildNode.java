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
package org.apache.calcite.materialize; // 声明包名为 org.apache.calcite.materialize，表示该类属于 Calcite 框架的物化视图相关模块

import org.apache.calcite.util.mapping.IntPair; // 导入 IntPair 类，用于表示整数对（通常用于表示列索引的映射关系）

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList 类，用于创建不可变的列表

import java.util.List; // 导入 Java 标准库的 List 接口，用于表示列表集合

import static java.util.Objects.requireNonNull; // 导入 Java Objects 类的 requireNonNull 静态方法，用于参数非空校验

/** Non-root node in a {@link Lattice}. */
// LatticeChildNode 类：表示 Lattice（格子/立方体）结构中的非根节点
// Lattice 是 Calcite 中用于物化视图优化的数据结构，表示一个星型或雪花型模式
// LatticeChildNode 继承自 LatticeNode，专门表示有父节点的非根节点（即维度表节点）
public class LatticeChildNode extends LatticeNode { // 定义 LatticeChildNode 类，继承自抽象类 LatticeNode
  public final LatticeNode parent; // 父节点引用，指向该节点的父节点（在星型模式中通常是事实表或上一级维度表），final 表示不可修改
  public final ImmutableList<IntPair> link; // 链接关系列表，存储该节点与父节点之间的连接键对（外键-主键映射），final 表示不可修改，ImmutableList 保证线程安全

  // 构造方法：创建一个 LatticeChildNode 实例
  // 参数 space: LatticeSpace 对象，表示 Lattice 的命名空间，包含所有表的元数据信息
  // 参数 parent: 父节点，指向该节点的父 LatticeNode 对象
  // 参数 mutableNode: MutableNode 对象，表示构建过程中的可变节点，包含节点的临时信息
  LatticeChildNode(LatticeSpace space, LatticeNode parent, // 构造方法声明，接收 LatticeSpace、父节点和可变节点作为参数
      MutableNode mutableNode) { // 可变节点参数，包含构建时的临时数据
    super(space, parent, mutableNode); // 调用父类 LatticeNode 的构造方法，初始化继承的成员变量（table、startCol、endCol、alias、children、digest）
    this.parent = requireNonNull(parent, "parent"); // 初始化 parent 成员变量，使用 requireNonNull 确保 parent 参数不为 null，否则抛出 NullPointerException
    this.link = ImmutableList.copyOf(requireNonNull(mutableNode.step, "step").keys); // 初始化 link 成员变量：从 mutableNode.step.keys 获取连接键对列表，使用 ImmutableList.copyOf 创建不可变副本，确保线程安全；requireNonNull 确保 mutableNode.step 不为 null
  }

  // use 方法：标记该节点及其祖先节点为已使用状态
  // 这是一个递归方法，用于拓扑遍历，确保在使用子节点前先标记其所有祖先节点
  // 参数 usedNodes: 已使用节点的列表，用于记录已经被标记为使用的节点，避免重复标记
  @Override void use(List<LatticeNode> usedNodes) { // 覆盖父类 LatticeNode 的抽象方法 use，实现节点使用标记逻辑
    if (!usedNodes.contains(this)) { // 检查当前节点是否已经在 usedNodes 列表中，避免重复处理
      parent.use(usedNodes); // 递归调用父节点的 use 方法，先标记父节点为已使用（确保祖先节点先于子节点被标记，符合拓扑排序原则）
      usedNodes.add(this); // 将当前节点添加到已使用节点列表中，标记为已使用
    }
  }
} // 类定义结束
