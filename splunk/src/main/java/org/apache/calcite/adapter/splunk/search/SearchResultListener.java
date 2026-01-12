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
package org.apache.calcite.adapter.splunk.search; // 定义包名，位于org.apache.calcite.adapter.splunk.search包下，用于Splunk适配器的搜索功能

/**
 * Called each time a search returns a record. // 每次搜索返回一条记录时调用此接口
 * 
 * 这个接口是Splunk搜索结果监听器的核心接口，用于处理从Splunk搜索返回的每一条记录。
 * 它采用了观察者模式（Observer Pattern）的设计思想，当Splunk执行搜索并返回结果时，
 * 会通过这个接口的回调方法来逐条处理搜索结果。
 * 
 * 主要功能：
 * 1. 提供回调机制，让调用者能够逐条处理搜索结果
 * 2. 支持字段名称的设置，用于理解结果数据的结构
 * 3. 允许控制搜索结果的处理流程（继续或停止）
 * 
 * 使用场景：
 * - 当Calcite需要通过Splunk适配器执行查询时
 * - 需要将Splunk的搜索结果转换为Calcite可处理的格式时
 * - 需要流式处理大量搜索结果时，避免一次性加载所有数据到内存
 * 
 * 实现注意事项：
 * - 实现类需要考虑线程安全性，因为可能被多个线程调用
 * - processSearchResult方法返回false可以提前终止搜索结果的处理
 * - 字段名称应该在处理第一条记录之前设置，以便正确解析字段值
 */
public interface SearchResultListener { // 定义搜索结果监听器接口，用于处理Splunk搜索返回的记录
  /**
   * Handles a record from a search result. // 处理搜索结果中的一条记录
   *
   * @param fieldValues Values of the record // 记录的字段值数组，每个元素对应一个字段的值
   * @return true to continue parsing, false otherwise // 返回true继续解析下一条记录，返回false停止解析
   * 
   * 方法详细说明：
   * 这个方法是核心回调方法，每当Splunk搜索返回一条记录时就会被调用。
   * 
   * 参数详解：
   * - fieldValues: 字符串数组，包含当前记录的所有字段值
   *   - 数组的顺序应该与setFieldNames方法设置的字段名称数组顺序一致
   *   - 例如：如果字段名称是["name", "age", "city"]，那么fieldValues可能是["John", "25", "New York"]
   *   - 字段值都是字符串类型，需要根据实际类型进行转换
   *   - 如果某个字段值为空，对应的数组元素可能是null或空字符串
   * 
   * 返回值详解：
   * - true: 表示希望继续处理后续的搜索结果记录
   * - false: 表示希望停止处理，不再接收后续记录
   *   - 这可以用于实现分页查询，只处理前N条记录
   *   - 也可以用于在满足特定条件时提前终止处理
   * 
   * 使用示例：
   * ```java
   * public boolean processSearchResult(String[] fieldValues) {
   *     // 将字段值转换为Calcite的行格式
   *     Object[] row = convertToRow(fieldValues);
   *     // 添加到结果集合中
   *     results.add(row);
   *     // 如果已经收集了100条记录，就停止处理
   *     return results.size() < 100;
   * }
   * ```
   * 
   * 异常处理：
   * - 如果在处理过程中发生异常，实现类应该捕获并处理
   * - 不应该让异常传播到调用方，因为这会影响整个搜索流程
   * - 可以记录错误日志并决定是否继续处理
   * 
   * 性能考虑：
   * - 这个方法可能会被调用很多次（对于大量搜索结果）
   * - 应该避免在方法内部进行重量级的操作
   * - 建议将字段值缓存或批量处理，而不是逐条立即处理
   */
  boolean processSearchResult(String[] fieldValues); // 处理搜索结果记录的方法，接收字段值数组，返回是否继续处理的标志

  void setFieldNames(String[] fieldNames); // 设置字段名称的方法，接收字段名称数组，用于定义结果数据的结构
  /**
   * 设置字段名称的方法，用于定义搜索结果中每个字段的名称和顺序。
   * 
   * 参数详解：
   * - fieldNames: 字符串数组，包含所有字段的名称
   *   - 例如：["name", "age", "city", "timestamp"]
   *   - 字段名称的顺序决定了fieldValues数组中值的对应关系
   *   - 字段名称应该是唯一的，不能重复
   *   - 字段名称通常与Splunk搜索中指定的字段一致
   * 
   * 调用时机：
   * - 这个方法通常在开始处理搜索结果之前被调用
   * - 应该在第一次调用processSearchResult之前调用
   * - 对于同一个搜索结果，这个方法只会被调用一次
   * 
   * 使用场景：
   * - 用于建立字段名称到字段值的映射关系
   * - 用于生成列的元数据信息
   * - 用于验证字段名称是否符合预期
   * 
   * 实现注意事项：
   * - 实现类应该保存字段名称数组，供后续processSearchResult方法使用
   * - 可以在这个方法中做一些初始化工作，比如创建列映射
   * - 如果字段名称为null或空数组，应该进行适当的错误处理
   * 
   * 示例代码：
   * ```java
   * private String[] fieldNames;
   * private Map<String, Integer> fieldIndexMap;
   * 
   * public void setFieldNames(String[] fieldNames) {
   *     this.fieldNames = fieldNames;
   *     this.fieldIndexMap = new HashMap<>();
   *     for (int i = 0; i < fieldNames.length; i++) {
   *         fieldIndexMap.put(fieldNames[i], i);
   *     }
   * }
   * 
   * public boolean processSearchResult(String[] fieldValues) {
   *     // 使用字段名称索引来访问特定字段的值
   *     int nameIndex = fieldIndexMap.get("name");
   *     String name = fieldValues[nameIndex];
   *     // ...
   * }
   * ```
   */
}
