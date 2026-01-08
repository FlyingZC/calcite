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
import org.apache.calcite.sql.SqlExplainLevel;
import org.apache.calcite.util.JsonBuilder;
import org.apache.calcite.util.Pair;

import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * 用于将关系表达式(RelNode)序列化为JSON格式的写入器，实现了RelWriter接口
 * 
 * <p>这个类是Calcite框架中将关系代数树转换为JSON表示的核心组件。
 * 当调用explain()方法时，它会遍历整个关系表达式树，并将每个节点转换为
 * JSON对象，包含节点的类型、属性、输入等信息。生成的JSON可以用于：
 * <ul>
 *   <li>调试和日志记录：以结构化格式输出查询计划</li>
 *   <li>查询优化器的可视化：将查询计划传递给前端工具展示</li>
 *   <li>查询计划的持久化：将计划保存到文件或数据库</li>
 *   <li>跨系统传递：在不同系统间传递查询计划</li>
 * </ul>
 *
 * <p>工作原理：
 * <ol>
 *   <li>当explain()被调用时，创建一个Map来存储当前RelNode的信息</li>
 *   <li>为每个RelNode分配一个唯一的ID（使用IdentityHashMap保证对象级别的唯一性）</li>
 *   <li>收集RelNode的所有属性（通过item()方法）</li>
 *   <li>递归处理所有输入的RelNode，建立父子关系</li>
 *   <li>将所有RelNode的信息添加到relList中</li>
 *   <li>最后通过asString()生成最终的JSON字符串</li>
 * </ol>
 *
 * <p>生成的JSON格式示例：
 * <pre>
 * {
 *   "rels": [
 *     {
 *       "id": "0",
 *       "relOp": "LogicalFilter",
 *       "inputs": ["1"],
 *       "condition": "...",
 *       ...
 *     },
 *     {
 *       "id": "1",
 *       "relOp": "LogicalScan",
 *       "table": "EMP",
 *       ...
 *     }
 *   ]
 * }
 * </pre>
 *
 * @see RelJsonReader // 用于从JSON读取并重建关系表达式的对应类
 */
public class RelJsonWriter implements RelWriter {
  //~ Instance fields ----------------------------------------------------------

  /**
   * JSON构建器，用于创建JSON对象、数组和字符串
   * 这是底层的JSON工具类，负责实际的JSON序列化工作
   * 通过它可以创建Map（对应JSON对象）、List（对应JSON数组）等数据结构
   */
  protected final JsonBuilder jsonBuilder;
  /**
   * RelJson对象，负责将RelNode相关的对象转换为JSON格式
   * 它包含了将各种类型（如RelNode类、字段、表达式等）转换为JSON的逻辑
   * 通过withJsonBuilder()方法关联了jsonBuilder，确保使用相同的JSON构建器
   */
  protected final RelJson relJson;
  /**
   * 使用IdentityHashMap存储RelNode到ID的映射关系
   * IdentityHashMap使用==而不是equals()来比较键，这意味着它基于对象身份而非对象内容
   * 这对于RelNode很重要，因为可能有多个内容相同但实例不同的RelNode对象
   * 每个RelNode会被分配一个唯一的字符串ID（如"0", "1", "2"...）
   * 这个映射用于：
   * 1. 避免重复序列化同一个RelNode对象
   * 2. 建立RelNode之间的引用关系（通过inputs数组引用其他RelNode的ID）
   * 3. 保持对象引用的完整性
   */
  private final IdentityHashMap<RelNode, String> relIdMap = new IdentityHashMap<>();
  /**
   * 存储所有已序列化的RelNode的列表，每个元素是一个Map（代表一个JSON对象）
   * 这个列表最终会成为JSON中"rels"数组的值
   * 每个Map包含一个RelNode的完整信息：id、relOp、inputs以及各种属性
   * 列表的顺序反映了RelNode被访问的顺序（通常是深度优先遍历）
   */
  protected final List<@Nullable Object> relList;
  /**
   * 临时存储当前RelNode的属性键值对列表
   * 当调用item()方法时，属性会被添加到这个列表中
   * 当调用done()方法时，这些属性会被复制并用于构建最终的JSON对象
   * 使用Pair<String, Object>来存储属性名和属性值
   * 这个列表会在每次done()调用后被清空，为下一个RelNode做准备
   */
  private final List<Pair<String, @Nullable Object>> values = new ArrayList<>();
  /**
   * 记录上一个序列化的RelNode的ID
   * 用于优化inputs数组：如果当前RelNode的输入是上一个RelNode，则可以省略inputs字段
   * 因为在深度优先遍历中，子节点通常紧跟在父节点之后被访问
   * 例如：如果inputs=["1"]而previousId也是"1"，则可以省略inputs字段
   * 这种优化可以减少生成的JSON大小
   */
  private @Nullable String previousId;

  //~ Constructors -------------------------------------------------------------

  /**
   * 默认构造函数，创建一个使用私有JsonBuilder的RelJsonWriter
   * 这个构造函数会创建一个新的JsonBuilder实例，因此每个RelJsonWriter都有自己独立的JSON构建器
   * 适用于不需要共享JsonBuilder的简单场景
   */
  public RelJsonWriter() {
    this(new JsonBuilder());
  }

  /**
   * 使用指定的JsonBuilder创建RelJsonWriter
   * 允许外部提供JsonBuilder，可以用于：
   * 1. 共享JsonBuilder实例（例如在多个RelJsonWriter之间）
   * 2. 使用自定义配置的JsonBuilder
   * 3. 在测试中注入Mock对象
   *
   * @param jsonBuilder 用于构建JSON的JsonBuilder实例，不能为null
   */
  public RelJsonWriter(JsonBuilder jsonBuilder) {
    this(jsonBuilder, UnaryOperator.identity());
  }

  /**
   * 完整构造函数，允许自定义JsonBuilder和RelJson转换器
   *
   * @param jsonBuilder 用于构建JSON的JsonBuilder实例，不能为null
   * @param relJsonTransform 一个函数式转换器，用于修改或包装默认的RelJson实例
   *                        允许用户自定义RelJson的行为，例如添加自定义类型的序列化逻辑
   *                        UnaryOperator.identity()表示不进行任何转换，使用默认的RelJson
   *                        这个参数提供了扩展点，使得用户可以定制序列化行为而不需要继承RelJsonWriter
   */
  public RelJsonWriter(JsonBuilder jsonBuilder,
      UnaryOperator<RelJson> relJsonTransform) {
    this.jsonBuilder = requireNonNull(jsonBuilder, "jsonBuilder"); // 确保jsonBuilder不为null
    relList = this.jsonBuilder.list(); // 使用jsonBuilder创建一个空的List，用于存储所有RelNode
    relJson =
        relJsonTransform.apply(RelJson.create().withJsonBuilder(jsonBuilder)); // 创建RelJson实例，应用转换器，并关联jsonBuilder
  }

  //~ Methods ------------------------------------------------------------------

  /**
   * 内部核心方法，将RelNode序列化为JSON并添加到relList中
   * 这个方法是整个序列化过程的核心，负责构建单个RelNode的JSON表示
   *
   * @param rel 要序列化的关系表达式节点
   * @param values 该RelNode的属性键值对列表，由item()方法收集而来
   *
   * 处理流程：
   * 1. 创建一个Map来存储当前RelNode的所有JSON属性
   * 2. 先设置id为null（占位），确保id是第一个属性（虽然最终会被真实ID替换）
   * 3. 设置relOp属性，值为RelNode类的类型名称（通过classToTypeName转换）
   * 4. 遍历values列表，将每个属性添加到Map中（跳过RelNode类型的值，因为它们通过inputs引用）
   * 5. 处理输入节点：递归调用explain()序列化所有输入，得到输入节点ID列表
   * 6. 优化：如果inputs只有一个元素且等于previousId，则省略inputs字段
   * 7. 为当前RelNode分配唯一ID（基于relIdMap的当前大小）
   * 8. 更新Map中的id为实际分配的ID
   * 9. 将Map添加到relList中
   * 10. 更新previousId为当前ID，供下一次优化使用
   */
  protected void explain_(RelNode rel, List<Pair<String, @Nullable Object>> values) {
    final Map<String, @Nullable Object> map = jsonBuilder.map(); // 创建一个空的Map，用于存储当前RelNode的所有JSON属性

    map.put("id", null); // 先将id设为null作为占位符，确保id在JSON中是第一个属性，便于阅读
    map.put("relOp", relJson.classToTypeName(rel.getClass())); // 设置relOp属性，值为RelNode类的类型名称（如"LogicalFilter"、"LogicalScan"等）
    for (Pair<String, @Nullable Object> value : values) { // 遍历所有的属性键值对
      if (value.right instanceof RelNode) { // 如果属性值是RelNode类型
        continue; // 跳过，因为RelNode会通过inputs字段引用，不需要在这里序列化
      }
      put(map, value.left, value.right); // 将属性名和值添加到Map中，值会通过relJson.toJson()转换为JSON兼容格式
    }
    // omit 'inputs: ["3"]' if "3" is the preceding rel
    final List<@Nullable Object> list = explainInputs(rel.getInputs()); // 递归序列化所有输入节点，返回输入节点的ID列表
    if (list.size() != 1 || !Objects.equals(list.get(0), previousId)) { // 优化：如果inputs只有一个元素且等于previousId（上一个节点ID）
      map.put("inputs", list); // 则省略inputs字段，否则添加inputs字段
    }

    final String id = Integer.toString(relIdMap.size()); // 为当前RelNode分配唯一ID，使用relIdMap的当前大小作为ID（从0开始递增）
    relIdMap.put(rel, id); // 将RelNode和ID的映射关系存入relIdMap，确保同一个RelNode对象不会重复分配ID
    map.put("id", id); // 更新Map中的id为实际分配的ID值

    relList.add(map); // 将构建好的Map（代表一个JSON对象）添加到relList中
    previousId = id; // 更新previousId为当前ID，供下一次优化使用（用于省略inputs字段）
  }

  /**
   * 辅助方法，将属性值通过relJson转换为JSON格式并存入Map
   *
   * @param map 要存储属性的Map
   * @param name 属性名
   * @param value 属性值，可能为null
   *
   * 这个方法的作用是：
   * 1. 将任意类型的value通过relJson.toJson()转换为JSON兼容的格式
   * 2. 将转换后的值存入Map中，使用name作为键
   * 3. relJson.toJson()会处理各种类型：基本类型、集合、RelNode相关对象等
   */
  private void put(Map<String, @Nullable Object> map, String name, @Nullable Object value) {
    map.put(name, relJson.toJson(value)); // 使用relJson将value转换为JSON格式并存入map
  }

  /**
   * 递归序列化输入节点列表，返回输入节点的ID列表
   * 这个方法实现了对RelNode树的深度优先遍历
   *
   * @param inputs 当前RelNode的输入节点列表（可能有0个、1个或多个输入）
   * @return 输入节点的ID列表，顺序与inputs参数相同
   *
   * 处理流程：
   * 1. 创建一个空的List来存储输入节点的ID
   * 2. 遍历每个输入节点：
   *    a. 如果输入节点已经在relIdMap中（已经序列化过），直接获取其ID
   *    b. 如果输入节点未序列化，递归调用explain()进行序列化，然后获取其ID（即previousId）
   * 3. 将ID添加到列表中
   * 4. 返回ID列表
   *
   * 注意：这个方法会改变relIdMap、relList和previousId的状态（如果需要序列化新节点）
   */
  private List<@Nullable Object> explainInputs(List<RelNode> inputs) {
    final List<@Nullable Object> list = jsonBuilder.list(); // 创建一个空的List，用于存储输入节点的ID
    for (RelNode input : inputs) { // 遍历每个输入节点
      String id = relIdMap.get(input); // 尝试从relIdMap中获取输入节点的ID（如果已序列化）
      if (id == null) { // 如果输入节点还没有被序列化
        input.explain(this); // 递归调用explain()方法序列化该输入节点，这会触发整个子树的序列化
        id = previousId; // 序列化完成后，previousId就是刚刚序列化的输入节点的ID
      }
      list.add(id); // 将ID添加到列表中
    }
    return list; // 返回输入节点的ID列表
  }

  /**
   * RelWriter接口的实现方法，用于解释（序列化）一个RelNode
   * 这个方法是外部调用的入口，内部委托给explain_()方法
   *
   * @param rel 要序列化的关系表达式节点
   * @param valueList 该RelNode的属性键值对列表
   */
  @Override public final void explain(RelNode rel, List<Pair<String, @Nullable Object>> valueList) {
    explain_(rel, valueList); // 委托给内部方法explain_()进行实际的序列化工作
  }

  /**
   * RelWriter接口的实现方法，返回详细级别
   * 详细级别决定了在序列化时包含多少属性信息
   *
   * @return SqlExplainLevel.ALL_ATTRIBUTES，表示包含所有属性
   *
   * SqlExplainLevel的几种级别：
   * - NO_ATTRIBUTES: 不包含任何属性
   * - EXPPLAN_ATTRIBUTES: 只包含基本属性
   * - ALL_ATTRIBUTES: 包含所有属性（包括内部实现细节）
   *
   * RelJsonWriter总是返回ALL_ATTRIBUTES，因为它需要尽可能详细的信息来重建RelNode
   */
  @Override public SqlExplainLevel getDetailLevel() {
    return SqlExplainLevel.ALL_ATTRIBUTES; // 返回最高详细级别，包含所有属性
  }

  /**
   * RelWriter接口的实现方法，用于收集当前RelNode的一个属性
   * 这个方法通常在RelNode.explain()实现中被多次调用，每次添加一个属性
   *
   * @param term 属性名，如"condition"、"table"、"fields"等
   * @param value 属性值，可以是任意对象（包括null）
   * @return 返回this，支持链式调用
   *
   * 使用示例：
   * RelWriter writer = ...;
   * writer.item("condition", condition)
   *       .item("table", table)
   *       .item("fields", fieldList)
   *       .done(relNode);
   */
  @Override public RelWriter item(String term, @Nullable Object value) {
    values.add(Pair.of(term, value)); // 将属性名和值组成Pair，添加到values列表中
    return this; // 返回this，支持链式调用
  }

  /**
   * RelWriter接口的实现方法，表示当前RelNode的属性收集完成
   * 这个方法会触发当前RelNode的序列化，并清空values列表为下一个RelNode做准备
   *
   * @param node 已经收集完属性的RelNode节点
   * @return 返回this，支持链式调用
   *
   * 处理流程：
   * 1. 创建values的不可变副本（ImmutableList.copyOf），防止后续修改
   * 2. 清空values列表，为下一个RelNode做准备
   * 3. 调用explain_()方法，使用values副本序列化当前RelNode
   * 4. 返回this
   *
   * 注意：这个方法是RelNode.explain()流程的终点，它标志着当前RelNode的属性已经收集完毕
   */
  @Override public RelWriter done(RelNode node) {
    final List<Pair<String, @Nullable Object>> valuesCopy =
        ImmutableList.copyOf(values); // 创建values的不可变副本，确保在序列化过程中不会被修改
    values.clear(); // 清空values列表，为下一个RelNode收集属性做准备
    explain_(node, valuesCopy); // 调用explain_()方法，使用values副本序列化当前RelNode
    return this; // 返回this，支持链式调用
  }

  /**
   * RelWriter接口的实现方法，询问是否需要嵌套处理
   * 如果返回true，表示RelWriter会递归处理子节点
   * 如果返回false，表示RelWriter期望RelNode自己处理子节点
   *
   * @return 总是返回true，表示RelJsonWriter会递归处理所有子节点
   *
   * 对于RelJsonWriter，总是返回true，因为它需要遍历整个RelNode树来生成完整的JSON
   */
  @Override public boolean nest() {
    return true; // 返回true，表示会递归处理子节点
  }

  /**
   * 生成最终的JSON字符串，包含所有已序列化的RelNode
   * 这个方法应该在所有RelNode都序列化完成后调用
   *
   * @return JSON字符串，格式为{"rels": [...]}
   *
   * 生成的JSON结构：
   * {
   *   "rels": [
   *     {
   *       "id": "0",
   *       "relOp": "LogicalFilter",
   *       "inputs": ["1"],
   *       "condition": {...},
   *       ...
   *     },
   *     ...
   *   ]
   * }
   *
   * 注意：调用这个方法后，可以继续使用RelJsonWriter序列化更多的RelNode
   * 但通常一个RelJsonWriter只用于一次完整的序列化过程
   */
  public String asString() {
    final Map<String, @Nullable Object> map = jsonBuilder.map(); // 创建一个Map作为最外层的JSON对象
    map.put("rels", relList); // 将relList作为"rels"字段的值，relList包含了所有已序列化的RelNode
    return jsonBuilder.toJsonString(map); // 将Map转换为JSON字符串并返回
  }
}
