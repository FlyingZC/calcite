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
package org.apache.calcite.rel.externalize;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.sql.SqlExplainLevel;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.Util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.value.Value;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Utility to dump a rel node plan in dot format.
 * 用于将关系表达式节点(RelNode)计划以DOT格式导出的工具类，DOT是Graphviz工具使用的图形描述语言
 * 该类可以将Calcite的查询计划树转换为DOT格式的图形描述，便于可视化展示查询计划的结构
 * 生成的DOT文件可以通过Graphviz工具渲染为图形，直观地展示关系算子之间的连接关系和数据流向
 */
@Value.Enclosing  // Value.Enclosing注解，表示该类包含嵌套的值类型(Immutable WriteOption接口)，用于Immutables库自动生成不可变实现类
public class RelDotWriter extends RelWriterImpl {  // 继承自RelWriterImpl，实现关系表达式节点的写入功能

  //~ Instance fields --------------------------------------------------------  // 实例字段区域标记

  /**
   * Adjacent list of the plan graph.
   * 存储计划图的邻接表，键为父节点，值为该父节点的所有输入子节点列表
   * 用于构建查询计划图中节点之间的连接关系，表示数据流向从子节点流向父节点
   * 使用LinkedHashMap保持插入顺序，确保输出结果的确定性
   */
  private final Map<RelNode, List<RelNode>> outArcTable = new LinkedHashMap<>();  // 邻接表，记录每个RelNode的输出弧(即输入节点)

  private final Map<RelNode, String> nodeLabels = new HashMap<>();  // 节点标签映射，存储每个RelNode对应的DOT格式标签字符串，用于在图中显示节点信息

  private final Multimap<RelNode, String> nodeStyles = HashMultimap.create();  // 节点样式映射，存储每个RelNode需要应用的样式集合(如bold等)，用于高亮特定节点

  private final WriteOption option;  // 写入选项配置，控制DOT格式输出的各种参数，如节点标签长度限制、每行最大字符数等

  //~ Constructors -----------------------------------------------------------  // 构造方法区域标记

  /**
   * 构造方法：创建一个使用默认写入选项的RelDotWriter实例
   * @param pw PrintWriter输出流，用于写入DOT格式的输出结果
   * @param detailLevel 详细程度级别，控制输出信息的详细程度(如NO_ATTRIBUTES、NON_COST_ATTRIBUTES、ALL_ATTRIBUTES等)
   * @param withIdPrefix 是否在节点标签前添加节点ID前缀，便于标识和调试
   */
  public RelDotWriter(
      PrintWriter pw, SqlExplainLevel detailLevel,  // SQL解释级别，控制输出信息的详细程度
      boolean withIdPrefix) {  // 是否在节点标签前添加节点ID
    this(pw, detailLevel, withIdPrefix, WriteOption.DEFAULT);  // 调用完整的构造方法，使用默认的写入选项配置
  }

  /**
   * 完整构造方法：创建一个RelDotWriter实例，允许自定义所有参数
   * @param pw PrintWriter输出流，用于写入DOT格式的输出结果
   * @param detailLevel 详细程度级别，控制输出信息的详细程度
   * @param withIdPrefix 是否在节点标签前添加节点ID前缀
   * @param option 写入选项配置对象，可以自定义节点标签长度、每行最大字符数等参数
   */
  public RelDotWriter(
      PrintWriter pw, SqlExplainLevel detailLevel,  // SQL解释级别
      boolean withIdPrefix, WriteOption option) {  // 是否添加节点ID前缀和自定义写入选项
    super(pw, detailLevel, withIdPrefix);  // 调用父类RelWriterImpl的构造方法，初始化基本参数
    this.option = option;  // 保存写入选项配置
  }

  //~ Methods ----------------------------------------------------------------  // 方法区域标记

  /**
   * 解释单个关系表达式节点，构建DOT格式所需的节点信息和边信息
   * 该方法会被RelWriter的explain方法调用，用于递归遍历整个查询计划树
   * @param rel 当前要解释的关系表达式节点
   * @param values 该节点的属性键值对列表，包含节点名称、条件、输入引用等信息
   */
  @Override protected void explain_(RelNode rel,  // 当前要处理的关系表达式节点
      List<Pair<String, @Nullable Object>> values) {  // 节点的属性键值对列表
    // get inputs  // 获取当前节点的所有输入节点
    List<RelNode> inputs = getInputs(rel);  // 调用getInputs方法获取输入节点列表，去除可能存在的装饰器节点
    outArcTable.put(rel, inputs);  // 将当前节点及其输入节点存入邻接表，构建图的边关系

    // generate node label  // 生成当前节点的DOT格式标签
    String label = getRelNodeLabel(rel, values);  // 调用getRelNodeLabel方法生成节点的显示标签
    nodeLabels.put(rel, label);  // 将节点和标签存入映射表，用于后续输出

    if (highlightNode(rel)) {  // 检查当前节点是否需要高亮显示
      nodeStyles.put(rel, "bold");  // 如果需要高亮，添加bold样式到该节点的样式集合
    }

    explainInputs(inputs);  // 递归解释所有输入节点，继续遍历查询计划树
  }

  /**
   * 生成关系表达式节点的DOT格式标签字符串
   * 标签包含节点类型、ID、属性、行数、成本等信息，格式化为多行显示
   * @param rel 关系表达式节点
   * @param values 节点的属性键值对列表
   * @return DOT格式的节点标签字符串，包含引号和换行符转义
   */
  protected String getRelNodeLabel(
      RelNode rel,  // 关系表达式节点
      List<Pair<String, @Nullable Object>> values) {  // 节点属性键值对列表
    List<String> labels = new ArrayList<>();  // 存储标签的各个部分，每部分对应一行
    StringBuilder sb = new StringBuilder();  // 字符串构建器，用于构建标签内容

    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery();  // 获取元数据查询对象，用于查询节点的统计信息如行数和成本
    if (withIdPrefix) {  // 如果配置了添加ID前缀
      sb.append(rel.getId()).append(":");  // 在标签开头添加节点ID和冒号分隔符
    }
    sb.append(rel.getRelTypeName());  // 添加节点类型名称(如"LogicalFilter"、"LogicalProject"等)
    labels.add(sb.toString());  // 将第一行标签加入标签列表
    sb.setLength(0);  // 清空字符串构建器，准备构建下一行

    if (detailLevel != SqlExplainLevel.NO_ATTRIBUTES) {  // 如果详细程度不是"无属性"级别
      for (Pair<String, @Nullable Object> value : values) {  // 遍历所有属性键值对
        if (value.right instanceof RelNode) {  // 如果属性值是RelNode类型，跳过(因为RelNode会单独作为节点显示)
          continue;  // 跳过此属性
        }
        sb.append(value.left)  // 添加属性名
            .append(" = ")  // 添加等号分隔符
            .append(value.right);  // 添加属性值
        labels.add(sb.toString());  // 将属性行加入标签列表
        sb.setLength(0);  // 清空字符串构建器
      }
    }

    switch (detailLevel) {  // 根据详细程度级别添加统计信息
    case ALL_ATTRIBUTES:  // 如果是全部属性级别
      sb.append("rowcount = ")  // 添加行数信息
          .append(mq.getRowCount(rel))  // 查询节点预估输出行数
          .append(" cumulative cost = ")  // 添加累积成本信息
          .append(mq.getCumulativeCost(rel))  // 查询节点累积成本
          .append(" ");  // 添加空格
      break;  // 退出switch
    default:  // 其他级别不添加统计信息
      break;  // 退出switch
    }
    switch (detailLevel) {  // 根据详细程度级别添加ID信息
    case NON_COST_ATTRIBUTES:  // 非成本属性级别
    case ALL_ATTRIBUTES:  // 全部属性级别
      if (!withIdPrefix) {  // 如果没有在开头添加ID前缀
        // If we didn't print the rel id at the start of the line, print it at the end.  // 在标签末尾添加ID
        sb.append("id = ").append(rel.getId());  // 在标签末尾添加节点ID
      }
      break;  // 退出switch
    default:  // 其他级别不添加ID
      break;  // 退出switch
    }
    labels.add(sb.toString().trim());  // 将统计信息行加入标签列表，并去除首尾空格
    sb.setLength(0);  // 清空字符串构建器

    // format labels separately and then concat them  // 分别格式化标签然后拼接
    int leftSpace = option.maxNodeLabelLength();  // 获取节点标签的最大长度限制
    List<String> newlabels = new ArrayList<>();  // 存储格式化后的标签列表
    for (int i = 0; i < labels.size(); i++) {  // 遍历所有标签行
      if (option.maxNodeLabelLength() != -1 && leftSpace <= 0) {  // 如果设置了长度限制且剩余空间已用完
        if (i < labels.size() - 1) {  // 如果不是最后一行标签
          // this is not the last label, but we have to stop here  // 添加省略号表示截断
          newlabels.add("...");  // 添加省略号
        }
        break;  // 停止添加更多标签
      }
      String formatted = formatNodeLabel(labels.get(i), option.maxNodeLabelLength());  // 格式化当前标签行
      newlabels.add(formatted);  // 将格式化后的标签加入列表
      leftSpace -= formatted.length();  // 减去已使用的字符数
    }

    return "\"" + String.join("\\n", newlabels) + "\"";  // 返回DOT格式的标签字符串，用换行符连接各行并包裹引号
  }

  /**
   * 获取父节点的所有输入节点，并去除装饰器节点
   * 装饰器节点如RelSubset等会被剥离，只保留核心的关系表达式节点
   * @param parent 父关系表达式节点
   * @return 去除装饰器后的输入节点列表
   */
  private static List<RelNode> getInputs(RelNode parent) {  // 获取父节点的输入列表
    return Util.transform(parent.getInputs(), RelNode::stripped);  // 对每个输入节点调用stripped方法去除装饰器，返回处理后的列表
  }

  /**
   * 递归解释所有输入节点，遍历查询计划树
   * 对每个未访问的输入节点调用explain方法，继续深度优先遍历
   * @param inputs 输入节点列表
   */
  private void explainInputs(List<? extends @Nullable RelNode> inputs) {  // 解释输入节点列表
    for (RelNode input : inputs) {  // 遍历所有输入节点
      if (input == null || nodeLabels.containsKey(input)) {  // 如果输入节点为null或已经访问过(已在nodeLabels中)
        continue;  // 跳过此节点，避免重复处理
      }
      input.explain(this);  // 递归调用输入节点的explain方法，继续遍历子树
    }
  }

  /**
   * 完成节点解释并输出DOT格式的查询计划图
   * 当所有节点都访问完毕后，输出完整的DOT格式图形描述
   * @param node 当前节点
   * @return 返回this，支持链式调用
   */
  @Override public RelWriter done(RelNode node) {  // 完成节点解释
    int numOfVisitedNodes = nodeLabels.size();  // 记录当前已访问的节点数量
    super.done(node);  // 调用父类的done方法
    if (numOfVisitedNodes == 0) {  // 如果节点数量为0，说明刚完成根节点的解释(因为nodeLabels在done之后才添加根节点)
      // When we enter this method call, no node has been visited. So the current node must be the root of the plan.
      // Now we are exiting the method, all nodes in the plan have been visited, so it is time to dump the plan.
      // 进入方法时没有节点被访问，说明当前节点是计划树的根节点。退出方法时所有节点都已访问，是时候输出计划了。

      pw.println("digraph {");  // 输出DOT图的开始标记，digraph表示有向图

      // print nodes with styles  // 输出带样式的节点
      for (RelNode rel : nodeStyles.keySet()) {  // 遍历所有需要应用样式的节点
        String style = String.join(",", nodeStyles.get(rel));  // 将样式集合合并为逗号分隔的字符串
        pw.println(nodeLabels.get(rel) + " [style=\"" + style + "\"]");  // 输出节点及其样式属性
      }

      // ordinary arcs  // 输出普通边(节点之间的连接关系)
      for (Map.Entry<RelNode, List<RelNode>> entry : outArcTable.entrySet()) {  // 遍历邻接表的所有条目
        RelNode src = entry.getKey();  // 获取父节点(目标节点)
        String srcDesc = nodeLabels.get(src);  // 获取父节点的标签描述
        for (int i = 0; i < entry.getValue().size(); i++) {  // 遍历父节点的所有输入节点
          RelNode dst = entry.getValue().get(i);  // 获取子节点(源节点)

          // label is the ordinal of the arc  // 边的标签是输入的序号
          // arc direction from child to parent, to reflect the direction of data flow  // 边的方向从子节点到父节点，反映数据流向
          pw.println(nodeLabels.get(dst) + " -> " + srcDesc + " [label=\"" + i + "\"]");  // 输出边及其标签，方向为子->父
        }
      }
      pw.println("}");  // 输出DOT图的结束标记
      pw.flush();  // 刷新输出流，确保所有内容都写出
    }
    return this;  // 返回this，支持链式调用
  }

  /**
   * Format the label into multiple lines according to the options.
   * 根据配置选项将标签格式化为多行显示，支持长度限制和换行处理
   *
   * @param label the original label. 原始标签字符串
   * @param limit the maximal length of the formatted label. 格式化后标签的最大长度
   *              -1 means no limit. -1表示不限制长度
   * @return the formatted label. 格式化后的标签字符串，包含换行符转义和可能的截断标记
   */
  private String formatNodeLabel(String label, int limit) {  // 格式化节点标签
    label = label.trim();  // 去除标签首尾空格

    // escape quotes in the label.  // 转义标签中的引号字符
    label = label.replace("\"", "\\\"");  // 将双引号替换为转义的双引号，避免破坏DOT语法

    boolean trimmed = false;  // 标记是否进行了截断
    if (limit != -1 && label.length() > limit) {  // 如果设置了长度限制且标签超出限制
      label = label.substring(0, limit);  // 截断标签到最大长度
      trimmed = true;  // 标记已截断
    }

    if (option.maxNodeLabelPerLine() == -1) {  // 如果每行最大字符数为-1(不限制)
      // no need to split into multiple lines.  // 不需要分割为多行
      return label + (trimmed ? "..." : "");  // 返回标签，如果截断了则添加省略号
    }

    List<String> descParts = new ArrayList<>();  // 存储分割后的标签部分
    for (int i = 0; i < label.length(); i += option.maxNodeLabelPerLine()) {  // 按每行最大字符数循环分割
      int endIdx = Math.min(i + option.maxNodeLabelPerLine(), label.length());  // 计算当前行的结束位置
      descParts.add(label.substring(i, endIdx));  // 提取当前行并加入列表
    }

    return String.join("\\n", descParts) + (trimmed ? "..." : "");  // 用换行符连接各行，如果截断了则添加省略号
  }

  /**
   * 判断节点是否需要高亮显示
   * 通过配置的谓词函数来判断特定节点是否应该被高亮
   * @param node 关系表达式节点
   * @return 如果节点需要高亮返回true，否则返回false
   */
  boolean highlightNode(RelNode node) {  // 判断节点是否需要高亮
    Predicate<RelNode> predicate = option.nodePredicate();  // 从选项中获取节点谓词函数
    return predicate != null && predicate.test(node);  // 如果谓词存在且测试通过则返回true
  }

  /**
   * Options for displaying the rel node plan in dot format.
   * 用于控制关系表达式节点计划以DOT格式显示的选项接口
   * 该接口使用Immutables库的@Value.Immutable注解，自动生成不可变实现类
   * 可以自定义节点标签的长度限制、每行最大字符数等参数，以及需要高亮的节点筛选条件
   */
  @Value.Immutable  // Immutables注解，自动生成不可变实现类ImmutableRelDotWriter.WriteOption
  public interface WriteOption {  // 写入选项接口

    /** Default configuration. 默认配置实例，使用所有默认参数值 */
    WriteOption DEFAULT = ImmutableRelDotWriter.WriteOption.of();  // 创建默认配置实例

    /**
     * The max length of node labels.
     * If the label is too long, the visual display would be messy.
     * -1 means no limit to the label length.
     * 节点标签的最大长度限制。如果标签太长，视觉显示会变得混乱。-1表示不限制标签长度。
     */
    @Value.Default default int maxNodeLabelLength() {  // 节点标签的最大长度
      return 100;  // 默认最大长度为100个字符
    }

    /**
     * The max length of node label in a line.
     * -1 means no limitation.
     * 节点标签中每行的最大字符数。-1表示不限制。
     */
    @Value.Default default int maxNodeLabelPerLine() {  // 每行最大字符数
      return 20;  // 默认每行最多20个字符
    }

    /**
     * Predicate for nodes that need to be highlighted.
     * 用于筛选需要高亮显示的节点的谓词函数。返回true的节点会被添加bold样式。
     */
    @Nullable Predicate<RelNode> nodePredicate();  // 节点谓词函数，可为null表示不高亮任何节点
  }
}
