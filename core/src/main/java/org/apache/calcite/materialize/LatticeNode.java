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
package org.apache.calcite.materialize; // 声明包名，该类属于org.apache.calcite.materialize包，用于物化视图相关功能

import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，表示关系代数中的表对象，用于优化器表示表
import org.apache.calcite.util.mapping.IntPair; // 导入IntPair类，用于表示一对整数值，常用于表示列的对应关系

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，表示不可变的列表

import org.checkerframework.checker.initialization.qual.Initialized; // 导入CheckerFramework注解，表示对象已初始化
import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework注解，表示字段可能为null

import java.util.List; // 导入Java标准库的List接口

import static com.google.common.base.Preconditions.checkArgument; // 导入Google Guava的前置条件检查方法，用于验证参数合法性

import static java.util.Objects.requireNonNull; // 导入Java Objects类的requireNonNull方法，用于确保对象非空

/** Source relation of a lattice. // 类注释：Lattice（立方体）的源关系
 *
 * <p>Relations form a tree; all relations except the root relation // 关系形成树形结构；除了根关系
 * (the fact table) have precisely one parent and an equi-join // （事实表）外，所有关系都只有一个父节点
 * condition on one or more pairs of columns linking to it. */ // 并且通过一对或多对列的等值连接条件连接到父节点
public abstract class LatticeNode { // 声明抽象类LatticeNode，表示立方体中的一个节点（表）
  public final LatticeTable table; // 成员变量：该节点对应的LatticeTable对象，存储表的元数据和结构信息
  final int startCol; // 成员变量：该表在立方体中列范围的起始索引，表示表的第一列在整个立方体中的位置
  final int endCol; // 成员变量：该表在立方体中列范围的结束索引（不包含），用于标记列范围
  public final @Nullable String alias; // 成员变量：该表的别名，可以为null，用于SQL中的表别名
  private final ImmutableList<LatticeChildNode> children; // 成员变量：该节点的子节点列表（ImmutableList类型，不可变），子节点表示通过连接关联的其他表
  public final String digest; // 成员变量：该节点的摘要字符串，用于标识和调试，包含表名、连接列和子节点信息

  /** Creates a LatticeNode. // 方法注释：创建一个LatticeNode对象
   *
   * <p>The {@code parent} and {@code mutableNode} arguments are used only // parent和mutableNode参数仅在构造期间使用
   * during construction. */ // parent表示父节点，mutableNode表示可变的节点信息
  LatticeNode(LatticeSpace space, @Nullable LatticeNode parent, MutableNode mutableNode) { // 构造方法：创建LatticeNode实例，space表示立方体空间，parent表示父节点，mutableNode包含节点信息
    this.table = requireNonNull(mutableNode.table); // 从mutableNode中获取table对象并赋值，使用requireNonNull确保table不为null
    this.startCol = mutableNode.startCol; // 从mutableNode中获取起始列索引并赋值
    this.endCol = mutableNode.endCol; // 从mutableNode中获取结束列索引并赋值
    this.alias = mutableNode.alias; // 从mutableNode中获取别名并赋值
    checkArgument(startCol >= 0); // 验证起始列索引必须大于等于0，否则抛出IllegalArgumentException
    checkArgument(endCol > startCol); // 验证结束列索引必须大于起始列索引，确保列范围有效

    final StringBuilder sb = new StringBuilder() // 创建StringBuilder对象用于构建digest摘要字符串
        .append(space.simpleName(table)); // 将表的简单名称追加到摘要字符串中
    if (parent != null) { // 如果存在父节点（即不是根节点/事实表）
      sb.append(':'); // 在摘要字符串中添加冒号分隔符
      int i = 0; // 初始化计数器，用于分隔多个连接列
      for (IntPair p : requireNonNull(mutableNode.step, "mutableNode.step").keys) { // 遍历连接条件中的列对，step包含连接信息
        if (i++ > 0) { // 如果不是第一个连接列对
          sb.append(","); // 在摘要字符串中添加逗号分隔符
        }
        sb.append(space.fieldName(parent.table, p.source)); // 将父表中连接列的名称追加到摘要字符串中
      }
    }
    if (mutableNode.children.isEmpty()) { // 如果该节点没有子节点
      this.children = ImmutableList.of(); // 将children设置为空的不可变列表
    } else { // 如果该节点有子节点
      sb.append(" ("); // 在摘要字符串中添加左括号，开始子节点描述
      final ImmutableList.Builder<LatticeChildNode> b = ImmutableList.builder(); // 创建不可变列表构建器，用于构建子节点列表
      int i = 0; // 初始化计数器，用于分隔多个子节点
      for (MutableNode mutableChild : mutableNode.children) { // 遍历所有子节点
        if (i++ > 0) { // 如果不是第一个子节点
          sb.append(' '); // 在摘要字符串中添加空格分隔符
        }
        @SuppressWarnings({"argument.type.incompatible", "assignment.type.incompatible"}) // 抑制类型不兼容警告，因为CheckerFramework的类型检查在此处过于严格
        final @Initialized LatticeChildNode node = // 创建LatticeChildNode对象，表示子节点
            new LatticeChildNode(space, this, mutableChild); // 调用LatticeChildNode构造方法，传入space、当前节点和可变子节点信息
        sb.append(node.digest); // 将子节点的摘要字符串追加到当前节点的摘要中
        b.add(node); // 将子节点添加到列表构建器中
      }
      this.children = b.build(); // 构建不可变的子节点列表并赋值给children成员变量
      sb.append(")"); // 在摘要字符串中添加右括号，结束子节点描述
    }
    this.digest = sb.toString(); // 将构建好的摘要字符串赋值给digest成员变量

  } // 构造方法结束

  @Override public String toString() { // 重写toString方法，用于将对象转换为字符串表示
    return digest; // 返回digest成员变量作为字符串表示
  } // toString方法结束

  public RelOptTable relOptTable() { // 方法：获取该节点对应的RelOptTable对象
    return table.t; // 返回LatticeTable中存储的RelOptTable对象，供优化器使用
  } // relOptTable方法结束

  abstract void use(List<LatticeNode> usedNodes); // 抽象方法：标记该节点及其相关节点为已使用状态，usedNodes列表用于记录已使用的节点

  void flattenTo(ImmutableList.Builder<LatticeNode> builder) { // 方法：将节点树展平为列表，builder用于构建展平后的节点列表
    builder.add(this); // 将当前节点添加到列表构建器中
    for (LatticeChildNode child : children) { // 遍历所有子节点
      child.flattenTo(builder); // 递归调用子节点的flattenTo方法，将子节点及其后代都添加到列表中
    }
  } // flattenTo方法结束

  void createPathsRecurse(LatticeSpace space, List<Step> steps, // 方法：递归创建路径，space表示立方体空间，steps表示当前路径的步骤列表
      List<Path> paths) { // paths表示所有路径的列表，用于存储生成的路径
    paths.add(space.addPath(steps)); // 将当前步骤列表作为路径添加到paths中，space.addPath创建路径对象
    for (LatticeChildNode child : children) { // 遍历所有子节点
      steps.add(space.addEdge(table, child.table, child.link)); // 将当前节点到子节点的边（连接条件）添加到步骤列表中
      child.createPathsRecurse(space, steps, paths); // 递归调用子节点的createPathsRecurse方法，继续创建后续路径
      steps.remove(steps.size() - 1); // 回溯：移除最后添加的步骤，以便探索其他路径
    }
  } // createPathsRecurse方法结束

} // 类定义结束
