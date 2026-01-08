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
package org.apache.calcite.rel.externalize; // 包声明：关系表达式外部化工具包，用于将关系表达式转换为可读的字符串格式

import org.apache.calcite.avatica.util.Spacer; // 导入Spacer工具类，用于管理缩进空格
import org.apache.calcite.linq4j.Ord; // 导入Ord工具类，用于为集合元素添加索引
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，代表关系代数表达式
import org.apache.calcite.rel.RelWriter; // 导入关系写入器接口，用于将关系节点输出为字符串
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入元数据查询接口，用于获取关系节点的元数据信息
import org.apache.calcite.sql.SqlExplainLevel; // 导入SQL解释级别枚举，定义不同的解释详细程度
import org.apache.calcite.util.Pair; // 导入Pair工具类，用于存储键值对

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表工具类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，用于标记可能为null的值

import java.io.PrintWriter; // 导入打印写入器，用于输出文本到流
import java.util.ArrayList; // 导入动态数组列表
import java.util.List; // 导入列表接口

/**
 * RelWriter实现类：负责将关系表达式树(RelNode树)以文本形式输出
 * 功能说明：
 * 1. 将关系表达式树转换为可读的字符串表示，用于调试和EXPLAIN功能
 * 2. 支持不同详细程度的输出级别(NO_ATTRIBUTES/EXPPLAN_ATTRIBUTES/NON_COST_ATTRIBUTES/ALL_ATTRIBUTES)
 * 3. 支持缩进格式化，使输出具有层次结构
 * 4. 支持显示关系节点ID、类型、属性、输入节点等信息
 * 5. 支持输出元数据信息如行数(rowcount)和累计成本(cumulative cost)
 * 
 * 使用场景：
 * - SQL查询计划的可视化输出
 * - 调试和优化关系表达式树
 * - 查询计划分析和比较
 * 
 * 实现原理：
 * - 使用spacer对象管理缩进，通过add/subtract方法控制缩进层级
 * - 使用values列表收集关系节点的属性键值对
 * - 递归遍历关系表达式树，自顶向下输出每个节点
 * - 根据detailLevel决定输出哪些信息
 */
public class RelWriterImpl implements RelWriter { // 类定义：实现RelWriter接口，提供关系表达式的文本输出功能
  //~ Instance fields -------------------------------------------------------- // 成员变量区域标记

  protected final PrintWriter pw; // 成员变量：输出写入器，用于将解释结果输出到指定的输出流(如控制台、文件等)
  protected final SqlExplainLevel detailLevel; // 成员变量：解释详细级别，控制输出信息的详细程度(NO_ATTRIBUTES/EXPPLAN_ATTRIBUTES/NON_COST_ATTRIBUTES/ALL_ATTRIBUTES)
  protected final boolean withIdPrefix; // 成员变量：是否在行首显示关系节点ID前缀，true则在每行开头显示"ID:"，false则不显示
  protected final boolean expand; // 成员变量：是否展开输出，控制输出格式的展开方式
  protected final Spacer spacer = new Spacer(); // 成员变量：缩进管理器，用于管理和控制输出的缩进级别，使输出具有层次结构
  private final List<Pair<String, @Nullable Object>> values = new ArrayList<>(); // 成员变量：属性值列表，用于临时存储关系节点的属性键值对，格式为(属性名, 属性值)

  //~ Constructors ----------------------------------------------------------- // 构造方法区域标记

  public RelWriterImpl(PrintWriter pw) { // 构造方法：使用默认参数创建RelWriterImpl实例
    this(pw, SqlExplainLevel.EXPPLAN_ATTRIBUTES, true); // 调用完整构造方法，使用默认的详细级别(EXPLAN_ATTRIBUTES)和显示ID前缀(true)
  }

  public RelWriterImpl( // 构造方法：使用指定参数创建RelWriterImpl实例，expand默认为false
      PrintWriter pw, SqlExplainLevel detailLevel, // 参数：输出写入器、解释详细级别
      boolean withIdPrefix) { // 参数：是否显示ID前缀
    this(pw, detailLevel, withIdPrefix, false); // 调用完整构造方法，expand参数默认设为false
  }
  public RelWriterImpl( // 构造方法：完整参数构造方法
      PrintWriter pw, SqlExplainLevel detailLevel, // 参数：输出写入器、解释详细级别
      boolean withIdPrefix, boolean expand) { // 参数：是否显示ID前缀、是否展开输出
    this.pw = pw; // 初始化输出写入器成员变量
    this.detailLevel = detailLevel; // 初始化解释详细级别成员变量
    this.withIdPrefix = withIdPrefix; // 初始化是否显示ID前缀成员变量
    this.expand = expand; // 初始化是否展开输出成员变量
  }

  //~ Methods ---------------------------------------------------------------- // 方法区域标记

  protected void explain_(RelNode rel, // 方法：核心解释方法，将关系节点及其属性输出到PrintWriter
      List<Pair<String, @Nullable Object>> values) { // 参数：要解释的关系节点、属性键值对列表
    List<RelNode> inputs = rel.getInputs(); // 获取当前关系节点的所有输入节点列表
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象，用于查询节点的元数据信息(如行数、成本等)
    if (!mq.isVisibleInExplain(rel, detailLevel)) { // 检查当前节点在指定的详细级别下是否可见
      // render children in place of this, at same level // 如果当前节点不可见，则直接渲染其子节点，保持在同一层级
      explainInputs(inputs); // 递归解释输入节点，跳过当前节点
      return; // 直接返回，不输出当前节点
    }

    StringBuilder s = new StringBuilder(); // 创建字符串构建器，用于构建当前行的输出内容
    spacer.spaces(s); // 根据当前缩进级别，将相应数量的空格添加到字符串构建器
    if (withIdPrefix) { // 如果配置了显示ID前缀
      s.append(rel.getId()).append(":"); // 在行首添加关系节点ID和冒号，如"1:"
    }
    s.append(rel.getRelTypeName()); // 添加关系节点类型名称，如"LogicalFilter"、"LogicalProject"等
    if (detailLevel != SqlExplainLevel.NO_ATTRIBUTES) { // 如果详细级别不是NO_ATTRIBUTES(即需要显示属性)
      int j = 0; // 初始化计数器，用于跟踪是否是第一个属性
      for (Pair<String, @Nullable Object> value : values) { // 遍历所有属性键值对
        if (value.right instanceof RelNode) { // 如果属性值是RelNode类型(通常是输入节点)
          continue; // 跳过，因为输入节点会单独处理
        }
        if (j++ == 0) { // 如果是第一个属性
          s.append("("); // 添加左括号，开始属性列表
        } else { // 如果不是第一个属性
          s.append(", "); // 添加逗号和空格，分隔多个属性
        }
        s.append(value.left) // 添加属性名
            .append("=[") // 添加等号和左方括号
            .append(value.right) // 添加属性值
            .append("]"); // 添加右方括号，格式如"condition=[>($0, 10)]"
      }
      if (j > 0) { // 如果至少有一个属性
        s.append(")"); // 添加右括号，结束属性列表
      }
    }
    switch (detailLevel) { // 根据详细级别决定是否输出额外的元数据信息
    case ALL_ATTRIBUTES: // 如果详细级别是ALL_ATTRIBUTES(显示所有属性)
      s.append(": rowcount = ") // 添加行数标签
          .append(mq.getRowCount(rel)) // 添加行数元数据值
          .append(", cumulative cost = ") // 添加累计成本标签
          .append(mq.getCumulativeCost(rel)); // 添加累计成本元数据值
      break; // 跳出switch
    default: // 其他详细级别
      break; // 不输出额外信息
    }
    switch (detailLevel) { // 根据详细级别决定是否输出节点ID
    case NON_COST_ATTRIBUTES: // 如果详细级别是NON_COST_ATTRIBUTES(非成本属性)
    case ALL_ATTRIBUTES: // 或ALL_ATTRIBUTES(所有属性)
      if (!withIdPrefix) { // 如果配置了不在行首显示ID前缀
        // If we didn't print the rel id at the start of the line, print // 如果行首没有打印节点ID
        // it at the end. // 则在行尾打印节点ID
        s.append(", id = ").append(rel.getId()); // 在行尾添加节点ID，格式如", id = 1"
      }
      break; // 跳出switch
    default: // 其他详细级别
      break; // 不输出ID
    }
    pw.println(s); // 将构建好的字符串输出到PrintWriter并换行
    spacer.add(2); // 增加缩进级别(增加2个空格)，为子节点的输出做准备
    explainInputs(inputs); // 递归解释所有输入节点(子节点)
    spacer.subtract(2); // 减少缩进级别(减少2个空格)，恢复到当前节点的缩进级别
  }

  private void explainInputs(List<RelNode> inputs) { // 方法：解释输入节点列表
    for (RelNode input : inputs) { // 遍历所有输入节点
      input.explain(this); // 调用输入节点的explain方法，使用当前RelWriterImpl作为参数，递归解释子节点
    }
  }

  @Override public final void explain(RelNode rel, List<Pair<String, @Nullable Object>> valueList) { // 方法：RelWriter接口实现，解释关系节点
    explain_(rel, valueList); // 调用内部实现方法explain_执行实际的解释逻辑
  }

  @Override public SqlExplainLevel getDetailLevel() { // 方法：RelWriter接口实现，获取当前的解释详细级别
    return detailLevel; // 返回detailLevel成员变量
  }

  @Override public RelWriter item(String term, @Nullable Object value) { // 方法：RelWriter接口实现，添加一个属性项到values列表
    values.add(Pair.of(term, value)); // 将属性名和属性值组成Pair对象，添加到values列表中
    return this; // 返回this，支持链式调用
  }

  @Override public RelWriter done(RelNode node) { // 方法：RelWriter接口实现，完成当前节点的解释并输出
    assert checkInputsPresentInExplain(node); // 断言检查：确保所有输入节点都已经在values列表中
    final List<Pair<String, @Nullable Object>> valuesCopy = // 创建values列表的不可变副本
        ImmutableList.copyOf(values); // 使用Guava的ImmutableList创建不可变副本，防止后续修改
    values.clear(); // 清空values列表，为下一个节点的解释做准备
    explain_(node, valuesCopy); // 调用explain_方法，使用values副本解释当前节点
    pw.flush(); // 刷新PrintWriter缓冲区，确保输出立即写入
    return this; // 返回this，支持链式调用
  }

  private boolean checkInputsPresentInExplain(RelNode node) { // 方法：检查所有输入节点是否都存在于values列表中
    int i = 0; // 初始化索引，用于遍历values列表
    if (!values.isEmpty() && values.get(0).left.equals("subset")) { // 如果values列表不为空且第一个元素的key是"subset"
      ++i; // 跳过第一个元素(subset)，从第二个元素开始检查
    }
    for (RelNode input : node.getInputs()) { // 遍历当前节点的所有输入节点
      assert values.get(i).right == input; // 断言检查：确保values列表中对应位置的值就是该输入节点
      ++i; // 索引递增，继续检查下一个输入节点
    }
    return true; // 如果所有断言都通过，返回true
  }

  /**
   * Converts the collected terms and values to a string. Does not write to
   * the parent writer.
   * 方法：将收集到的属性项和值转换为字符串格式，不输出到PrintWriter
   * 用途：用于获取属性字符串表示，而不实际写入输出流
   * 返回格式：如"(condition=[>($0, 10)], expr=[$0])"
   */
  public String simple() { // 方法：将values列表转换为简单的字符串表示
    final StringBuilder buf = new StringBuilder("("); // 创建字符串构建器，以左括号开始
    for (Ord<Pair<String, @Nullable Object>> ord : Ord.zip(values)) { // 遍历values列表，使用Ord为每个元素添加索引
      if (ord.i > 0) { // 如果不是第一个元素
        buf.append(", "); // 添加逗号和空格作为分隔符
      }
      buf.append(ord.e.left).append("=[").append(ord.e.right).append("]"); // 添加属性名和值，格式"key=[value]"
    }
    buf.append(")"); // 添加右括号，结束字符串
    return buf.toString(); // 返回构建好的字符串
  }

  @Override public boolean expand() { // 方法：RelWriter接口实现，返回是否展开输出的标志
    return this.expand; // 返回expand成员变量
  }
} // 类结束
