/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看NOTICE文件获取版权信息
 * this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证授权此文件
 * (the "License"); you may not use this file except in compliance with // 除非符合许可证，否则不得使用此文件
 * the License.  You may obtain a copy of the License at // 可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // 许可证URL
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意
 * distributed under the License is distributed on an "AS IS" BASIS, // 否则按"原样"分发，不提供任何形式的担保
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 无论是明示的还是暗示的
 * See the License for the specific language governing permissions and // 查看许可证以了解特定语言的权限和
 * limitations under the License. // 限制
 */
package org.apache.calcite.rel; // 包声明：org.apache.calcite.rel，包含关系表达式相关类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker框架的空值注解，用于标记可空类型

/**
 * A <code>RelVisitor</code> is a Visitor role in the // RelVisitor是访问者模式中的访问者角色
 * {@link org.apache.calcite.util.Glossary#VISITOR_PATTERN visitor pattern} and // 用于访问者模式，参考术语表中的VISITOR_PATTERN
 * visits {@link RelNode} objects as the role of Element. Other components in // 访问RelNode对象作为元素角色
 * the pattern: {@link RelNode#childrenAccept(RelVisitor)}. // 模式中的其他组件：RelNode的childrenAccept方法
 * 
 * 【类作用说明】：RelVisitor是Calcite关系代数树（RelNode树）的访问者基类，实现了访问者设计模式。
 * 它提供了遍历关系表达式树的标准机制，允许子类自定义访问行为。主要功能包括：
 * 1. 深度优先遍历RelNode树结构
 * 2. 在遍历过程中访问每个节点（RelNode）
 * 3. 支持节点替换操作（通过replaceRoot方法）
 * 4. 提供扩展点，子类可以重写visit方法实现自定义的访问逻辑
 * 
 * 使用场景：
 * - 关系表达式树的分析和验证
 * - 关系表达式树的转换和优化
 * - 收集统计信息和元数据
 * - 实现自定义的规则和优化器
 * 
 * 工作原理：
 * - 通过go方法启动遍历，从根节点开始
 * - 每个节点的visit方法会被调用，接收节点本身、序号和父节点
 * - visit方法默认调用node.childrenAccept(this)继续访问子节点
 * - 子类可以重写visit方法实现自定义逻辑
 * - 遍历是深度优先的，先访问父节点再访问子节点
 */
public abstract class RelVisitor { // 抽象类：RelVisitor，关系表达式访问者，必须被子类继承使用
  //~ Instance fields -------------------------------------------------------- // 实例字段分隔注释

  private @Nullable RelNode root; // 成员变量：root，表示当前遍历的根节点，类型为RelNode（可空），用于存储遍历的起始节点，可能被替换

  //~ Methods ---------------------------------------------------------------- // 方法分隔注释

  /**
   * Visits a node during a traversal. // 方法功能：在遍历过程中访问一个节点
   *
   * @param node    Node to visit // 参数：node，要访问的关系表达式节点
   * @param ordinal Ordinal of node within its parent // 参数：ordinal，节点在父节点中的序号（第几个子节点），从0开始
   * @param parent  Parent of the node, or null if it is the root of the // 参数：parent，节点的父节点，如果是根节点则为null
   *                traversal // 继续参数说明
   * 
   * 【方法详细说明】：visit方法是访问者模式的核心方法，在遍历关系表达式树时对每个节点调用。
   * 
   * 默认实现：
   * - 直接调用node.childrenAccept(this)，继续访问当前节点的所有子节点
   * - 实现了深度优先遍历策略
   * 
   * 参数说明：
   * - node：当前正在访问的RelNode节点，可能是各种类型的关系操作（如Scan、Filter、Project、Join等）
   * - ordinal：当前节点在父节点的子节点列表中的位置索引，用于识别节点在树中的位置
   * - parent：父节点引用，根节点时为null，可以用于向上遍历或访问父节点信息
   * 
   * 子类重写建议：
   * - 如果需要在访问节点时执行特定操作，应该重写此方法
   * - 重写时通常需要调用super.visit()来继续遍历子节点
   * - 可以根据节点类型执行不同的处理逻辑
   * - 可以收集信息、修改节点结构或进行验证
   * 
   * 使用示例：
   * - 收集特定类型的节点
   * - 验证节点属性
   * - 统计节点数量
   * - 应用转换规则
   */
  public void visit( // 访问方法：public void visit，访问关系表达式节点
      RelNode node, // 参数：node，要访问的RelNode节点
      int ordinal, // 参数：ordinal，节点在父节点中的序号
      @Nullable RelNode parent) { // 参数：parent，父节点，可为null
    node.childrenAccept(this); // 方法体：调用节点的childrenAccept方法，让节点接受此访问者，从而访问其所有子节点，实现深度优先遍历
  }

  /**
   * Replaces the root node of this traversal. // 方法功能：替换当前遍历的根节点
   *
   * @param node The new root node // 参数：node，新的根节点
   * 
   * 【方法详细说明】：replaceRoot方法用于在遍历过程中替换根节点。
   * 
   * 使用场景：
   * - 在visit方法中，当发现需要替换整个树时调用
   * - 在优化过程中，当根节点被转换为新节点时使用
   * - 在验证过程中，当需要返回修改后的树时使用
   * 
   * 工作原理：
   * - 修改成员变量root的值为传入的新节点
   * - 新节点会在go方法返回时被返回
   * - 允许访问者在遍历过程中修改树结构
   * 
   * 注意事项：
   * - 此方法只修改root成员变量，不影响当前的遍历过程
   * - 替换后的根节点会在遍历完成后通过go方法返回
   * - 可以在visit方法中调用此方法来替换根节点
   * 
   * 使用示例：
   * - 在优化器中，当根节点被重写时调用
   * - 在验证器中，当需要返回修正后的树时调用
   * - 在转换器中，当需要返回转换后的树时调用
   */
  public void replaceRoot(@Nullable RelNode node) { // 替换根节点方法：public void replaceRoot，设置新的根节点
    this.root = node; // 方法体：将成员变量root设置为传入的新节点，完成根节点替换
  }

  /**
   * Starts an iteration. // 方法功能：启动一次遍历迭代
   * 
   * 【方法详细说明】：go方法是启动关系表达式树遍历的入口方法。
   * 
   * 工作流程：
   * 1. 将传入的节点p设置为根节点（root = p）
   * 2. 调用visit方法开始遍历，参数为(p, 0, null)，表示从根节点开始，序号为0，无父节点
   * 3. 遍历过程中会递归访问所有子节点
   * 4. 返回最终的root节点（可能在遍历过程中被替换）
   * 
   * 参数说明：
   * - p：要开始遍历的关系表达式树的根节点
   * 
   * 返回值：
   * - 返回RelNode类型的根节点，可能是原始节点，也可能是在遍历过程中被替换的新节点
   * - 返回值可能为null（如果root被设置为null）
   * 
   * 使用场景：
   * - 开始遍历一个关系表达式树
   * - 应用访问者模式处理整个树
   * - 获取处理后的树结构
   * 
   * 注意事项：
   * - 每次调用go都会重新设置root为传入的节点p
   * - 遍历是深度优先的
   * - 如果在遍历过程中调用了replaceRoot，返回的节点可能不同于传入的节点
   * - 此方法是遍历的起点，必须先调用go才能开始遍历
   * 
   * 使用示例：
   * - RelVisitor visitor = new MyVisitor();
   * - RelNode newRoot = visitor.go(oldRoot);
   * - newRoot可能等于oldRoot，也可能是在遍历过程中被替换的新节点
   */
  public @Nullable RelNode go(RelNode p) { // 启动遍历方法：public RelNode go，开始遍历关系表达式树，返回可能的修改后的根节点
    this.root = p; // 方法体：将传入的节点p设置为根节点，初始化遍历的起点
    visit(p, 0, null); // 方法体：调用visit方法开始遍历，参数p为根节点，序号为0，父节点为null
    return root; // 方法体：返回最终的root节点，可能在遍历过程中被替换，返回处理后的树根
  }
} // 类结束
