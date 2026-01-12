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
package org.apache.calcite.test; // 包声明，该类属于org.apache.calcite.test测试包

import org.apache.calcite.adapter.druid.DruidQuery; // 导入DruidQuery类，用于表示Druid查询规范

import java.util.List; // 导入List接口，用于存储查询规范列表
import java.util.function.Consumer; // 导入Consumer函数式接口，用于消费查询结果

import static org.hamcrest.CoreMatchers.containsString; // 导入Hamcrest匹配器，用于检查字符串包含关系
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具，用于执行断言检查
import static org.hamcrest.Matchers.hasSize; // 导入Hamcrest匹配器，用于检查集合大小

/**
 * DruidChecker类：一个消费者（Consumer），用于检查是否生成了特定的Druid查询来实现SQL查询
 * 
 * 该类主要用于测试场景，验证Calcite将SQL查询转换为Druid查询时生成的查询字符串是否符合预期
 * 它实现了Consumer<List>接口，可以接受一个包含DruidQuery.QuerySpec对象的列表作为输入
 * 然后验证生成的查询字符串是否包含预期的子字符串
 * 
 * 使用场景：
 * 1. 在单元测试中验证SQL到Druid查询的转换是否正确
 * 2. 确保生成的Druid查询包含预期的过滤条件、聚合函数、维度等
 * 3. 支持灵活的字符串匹配，可以检查查询的多个方面
 * 
 * 工作原理：
 * - 接收一个包含DruidQuery.QuerySpec的列表
 * - 验证列表中只有一个查询规范
 * - 获取查询规范的字符串表示
 * - 检查该字符串是否包含所有预期的行（子字符串）
 * 
 * 特性：
 * - 支持单引号和双引号的自动转换
 * - 可以验证多个预期的字符串片段
 * - 使用Hamcrest断言库提供清晰的错误信息
 */
class DruidChecker implements Consumer<List> { // DruidChecker类，实现Consumer<List>接口，用于消费和验证Druid查询结果
  private final String[] lines; // 成员变量：存储预期的查询字符串片段数组，每个元素代表一个应该在生成的Druid查询中出现的字符串
  private final boolean replaceSingleWithDoubleQuotes; // 成员变量：布尔标志，指示是否将单引号替换为双引号，用于处理不同格式的JSON字符串

  DruidChecker(String... lines) { // 构造方法：使用可变参数创建DruidChecker实例，默认启用单引号到双引号的替换
    this(true, lines); // 调用另一个构造方法，传入true作为replaceSingleWithDoubleQuotes参数，表示启用引号替换
  }

  DruidChecker(boolean replaceSingleWithDoubleQuotes, String... lines) { // 构造方法：使用指定的引号替换标志和预期字符串片段创建DruidChecker实例
    this.replaceSingleWithDoubleQuotes = replaceSingleWithDoubleQuotes; // 初始化replaceSingleWithDoubleQuotes成员变量，控制是否进行引号替换
    this.lines = lines; // 初始化lines成员变量，存储预期的查询字符串片段数组
  }

  @Override public void accept(final List list) { // 实现Consumer接口的accept方法，接收包含DruidQuery.QuerySpec的列表并进行验证
    assertThat((List<Object>) list, hasSize(1)); // 断言：验证列表中只有一个元素，确保只生成了一个Druid查询
    DruidQuery.QuerySpec querySpec = (DruidQuery.QuerySpec) list.get(0); // 从列表中获取第一个元素，强制转换为DruidQuery.QuerySpec类型，表示Druid查询规范
    for (String line : lines) { // 遍历所有预期的字符串片段，每个片段都应该在生成的查询中出现
      final String s = // 定义最终要检查的字符串
          replaceSingleWithDoubleQuotes ? line.replace('\'', '"') : line; // 如果启用了引号替换，将单引号替换为双引号，否则使用原始字符串
      assertThat(querySpec.getQueryString(null, -1), containsString(s)); // 断言：验证生成的Druid查询字符串（通过getQueryString方法获取）是否包含预期的字符串片段s，null表示不需要特定的格式化选项，-1表示不限制缩进
    }
  }
}
