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
// Apache Calcite 版权声明和许可证信息
package org.apache.calcite.materialize; // 包声明：该类位于 org.apache.calcite.materialize 包下，用于物化视图相关功能

import org.apache.calcite.util.mapping.IntPair; // 导入 IntPair 类，用于表示整数对，通常用于映射关系

import com.google.common.collect.Ordering; // 导入 Google Guava 的 Ordering 类，用于提供灵活的排序功能

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 CheckerFramework 的 Nullable 注解，用于标记可能为 null 的值

import java.util.ArrayList; // 导入 ArrayList 类，用于实现动态数组
import java.util.Comparator; // 导入 Comparator 接口，用于自定义比较逻辑
import java.util.HashSet; // 导入 HashSet 类，用于实现哈希集合
import java.util.List; // 导入 List 接口，用于列表操作
import java.util.Objects; // 导入 Objects 类，用于对象操作和比较
import java.util.Set; // 导入 Set 接口，用于集合操作

import static java.util.Objects.requireNonNull; // 静态导入 requireNonNull 方法，用于参数非空检查

/** Mutable version of {@link LatticeNode}, used while a graph is being
 * built. */
// 类说明：MutableNode 是 LatticeNode 的可变版本，用于在构建图结构时使用
// Lattice（晶格）是 Calcite 中用于物化视图优化的一种数据结构，表示表之间的连接关系
// MutableNode 允许在构建过程中动态修改节点和连接关系
class MutableNode { // 类声明：MutableNode 类，表示一个可变的晶格节点
  final LatticeTable table; // 成员变量：该节点关联的 LatticeTable 对象，表示一个表及其在晶格中的信息
  final @Nullable MutableNode parent; // 成员变量：父节点，可能为 null（根节点没有父节点），表示在图结构中的上级节点
  final @Nullable Step step; // 成员变量：从父节点到当前节点的步骤（Step），包含连接条件等信息，可能为 null（根节点）
  int startCol; // 成员变量：起始列索引，表示该节点在展平的列表中的起始位置
  int endCol; // 成员变量：结束列索引，表示该节点在展平的列表中的结束位置（通常是 startCol + 表的列数）
  @Nullable String alias; // 成员变量：表的别名，可能为 null，用于在 SQL 查询中标识该表
  final List<MutableNode> children = new ArrayList<>(); // 成员变量：子节点列表，存储所有直接子节点，使用 ArrayList 实现动态数组

  /** Comparator for sorting children within a parent. */
  // 静态成员变量：用于对父节点中的子节点进行排序的比较器
  // 排序规则：首先按表的限定名排序，如果表相同，则按外键列排序
  static final Ordering<MutableNode> ORDERING = // 声明一个静态的 Ordering 对象，用于排序
      Ordering.from( // 从一个 Comparator 创建 Ordering 对象
          new Comparator<MutableNode>() { // 创建一个匿名内部类实现 Comparator 接口
            @Override public int compare(MutableNode o1, MutableNode o2) { // 重写 compare 方法，定义比较逻辑
              int c = // 声明一个整型变量 c 用于存储比较结果
                  Ordering.<String>natural().lexicographical() // 创建一个字符串的自然字典序排序器
                      .compare(o1.table.t.getQualifiedName(), // 比较第一个节点的表的限定名（全名，如 schema.table）
                          o2.table.t.getQualifiedName()); // 与第二个节点的表的限定名进行比较
              if (c == 0 && o1.step != null && o2.step != null) { // 如果表名相同且两个节点的 step 都不为 null
                // The nodes have the same table. Now compare them based on the
                // columns they use as foreign key.
                // 注释：节点具有相同的表，现在根据它们作为外键使用的列进行比较
                c = // 重新赋值比较结果 c
                    Ordering.<Integer>natural().lexicographical() // 创建一个整数的自然字典序排序器
                        .compare(IntPair.left(o1.step.keys), // 比较第一个节点的 step 的键的左部分（外键列索引）
                            IntPair.left(o2.step.keys)); // 与第二个节点的 step 的键的左部分进行比较
              }
              return c; // 返回比较结果
            } // compare 方法结束
          }); // 匿名内部类结束

  /** Creates a root node. */
  // 构造方法说明：创建一个根节点（没有父节点和步骤）
  MutableNode(LatticeTable table) { // 构造方法：接受一个 LatticeTable 参数
    this(table, null, null); // 调用另一个构造方法，传入 table、parent 为 null、step 为 null
  } // 构造方法结束

  /** Creates a non-root node. */
  // 构造方法说明：创建一个非根节点（有父节点和步骤）
  @SuppressWarnings("argument.type.incompatible") // 抑制编译器警告：参数类型不兼容
  MutableNode(LatticeTable table, @Nullable MutableNode parent, @Nullable Step step) { // 构造方法：接受表、父节点和步骤参数
    this.table = requireNonNull(table, "table"); // 初始化 table 成员变量，并检查 table 不为 null
    this.parent = parent; // 初始化 parent 成员变量，可能为 null
    this.step = step; // 初始化 step 成员变量，可能为 null
    if (parent != null) { // 如果父节点不为 null（即不是根节点）
      parent.children.add(this); // 将当前节点添加到父节点的子节点列表中
      parent.children.sort(ORDERING); // 对父节点的子节点列表进行排序，确保子节点按固定顺序排列
    } // 条件判断结束
  } // 构造方法结束

  /** Populates a flattened list of mutable nodes. */
  // 方法说明：将节点树展平为一个列表，按照深度优先遍历的顺序
  void flatten(List<MutableNode> flatNodes) { // 方法声明：接受一个 MutableNode 列表作为参数
    flatNodes.add(this); // 将当前节点添加到展平列表中
    for (MutableNode child : children) { // 遍历所有子节点
      child.flatten(flatNodes); // 递归调用 flatten 方法，将子节点及其后代添加到列表中
    } // 循环结束
  } // 方法结束

  /** Returns whether this node is cylic, in an undirected sense; that is,
   * whether the same descendant can be reached by more than one route. */
  // 方法说明：检查节点是否构成循环（在无向图意义上），即是否可以通过多条路径到达同一个后代节点
  boolean isCyclic() { // 方法声明：返回布尔值，表示是否存在循环
    final Set<MutableNode> descendants = new HashSet<>(); // 创建一个 HashSet 用于存储访问过的后代节点
    return isCyclicRecurse(descendants); // 调用递归方法检查循环，传入后代节点集合
  } // 方法结束

  private boolean isCyclicRecurse(Set<MutableNode> descendants) { // 私有方法：递归检查循环
    if (!descendants.add(this)) { // 尝试将当前节点添加到集合中，如果添加失败（说明节点已存在）
      return true; // 返回 true，表示检测到循环
    } // 条件判断结束
    for (MutableNode child : children) { // 遍历所有子节点
      if (child.isCyclicRecurse(descendants)) { // 递归检查子节点是否构成循环
        return true; // 如果子节点检测到循环，返回 true
      } // 条件判断结束
    } // 循环结束
    return false; // 所有子节点都没有检测到循环，返回 false
  } // 方法结束

  void addPath(Path path, @Nullable String alias) { // 方法声明：添加一条路径到节点树中
    MutableNode n = this; // 创建一个临时变量 n，初始指向当前节点
    for (Step step1 : path.steps) { // 遍历路径中的每个步骤
      MutableNode n2 = n.findChild(step1); // 在当前节点 n 的子节点中查找与 step1 匹配的节点
      if (n2 == null) { // 如果没有找到匹配的子节点
        n2 = new MutableNode(step1.target(), n, step1); // 创建一个新的子节点，目标表为 step1 的目标，父节点为 n，步骤为 step1
        if (alias != null) { // 如果提供了别名
          n2.alias = alias; // 将别名赋给新创建的节点
        } // 条件判断结束
      } // 条件判断结束
      n = n2; // 将 n 移动到下一个节点（新创建或已存在的子节点）
    } // 循环结束
  } // 方法结束

  private @Nullable MutableNode findChild(Step step) { // 私有方法：在子节点列表中查找与给定步骤匹配的子节点
    for (MutableNode child : children) { // 遍历所有子节点
      if (Objects.equals(child.table, step.target()) // 检查子节点的表是否与步骤的目标表相同
          && Objects.equals(child.step, step)) { // 并且检查子节点的步骤是否与给定的步骤相同
        return child; // 如果匹配，返回该子节点
      } // 条件判断结束
    } // 循环结束
    return null; // 没有找到匹配的子节点，返回 null
  } // 方法结束
} // 类结束
