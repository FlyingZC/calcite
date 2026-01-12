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
package org.apache.calcite.test; // 声明包名，该类属于org.apache.calcite.test包，是Calcite测试框架的一部分

import com.fasterxml.jackson.core.JsonParser; // 导入Jackson库的JsonParser类，用于解析JSON数据
import com.fasterxml.jackson.databind.JsonNode; // 导入Jackson库的JsonNode类，表示JSON树节点
import com.fasterxml.jackson.databind.ObjectMapper; // 导入Jackson库的ObjectMapper类，用于JSON序列化和反序列化
import com.fasterxml.jackson.databind.SerializationFeature; // 导入Jackson库的SerializationFeature枚举，用于配置序列化特性
import com.fasterxml.jackson.databind.node.ArrayNode; // 导入Jackson库的ArrayNode类，表示JSON数组节点
import com.fasterxml.jackson.databind.node.ObjectNode; // 导入Jackson库的ObjectNode类，表示JSON对象节点

import java.io.IOException; // 导入Java IO异常类，处理输入输出异常
import java.io.UncheckedIOException; // 导入Java未检查IO异常类，用于包装IO异常为运行时异常
import java.util.List; // 导入Java集合框架的List接口，表示有序列表
import java.util.function.Consumer; // 导入Java函数式接口Consumer，表示接受单个参数且不返回结果的操作

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言库的is匹配器，用于断言相等性
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言库的assertThat方法，用于执行断言

import static java.util.Objects.requireNonNull; // 导入Java工具类的requireNonNull方法，用于检查对象是否为null

/**
 * Internal utility methods for Elasticsearch tests.
 * Elasticsearch测试的内部工具方法类
 * 
 * 这个类为Elasticsearch适配器的测试提供工具方法，主要用于验证生成的Elasticsearch查询是否正确
 * 它提供了将点分隔的属性名转换为嵌套JSON对象的功能，以及验证Elasticsearch查询的功能
 * 
 * 主要功能：
 * 1. 提供Elasticsearch查询验证器，用于检查生成的查询是否符合预期
 * 2. 提供点分隔属性名展开功能，将"query.bool.must"这样的属性名转换为嵌套的JSON结构
 * 3. 使用Jackson库处理JSON数据的序列化和反序列化
 * 
 * 使用场景：
 * - 在Calcite的Elasticsearch适配器测试中，验证SQL查询转换后的Elasticsearch查询是否正确
 * - 简化测试用例中的JSON断言编写，使用点分隔语法代替嵌套对象
 */
public class ElasticsearchChecker { // 声明ElasticsearchChecker工具类，提供Elasticsearch测试相关的静态方法

  private static final ObjectMapper MAPPER = new ObjectMapper() // 创建并初始化Jackson的ObjectMapper实例，用于JSON序列化和反序列化，配置为静态常量以提高性能
      .enable(SerializationFeature.INDENT_OUTPUT) // 启用缩进输出特性，使生成的JSON格式化输出，便于阅读和调试
      .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES) // 启用允许未加引号的字段名特性，使JSON更易读，如{a: 1}而不是{"a": 1} // user-friendly settings to
      .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES); // 启用允许单引号特性，使JSON支持单引号，如{'a': 1} // avoid too much quoting

  private ElasticsearchChecker() {} // 私有构造方法，防止实例化，因为这是一个纯工具类，所有方法都是静态的


  /** Returns a function that checks that a particular Elasticsearch pipeline is
   * generated to implement a query.
   * 返回一个函数，用于检查是否生成了特定的Elasticsearch管道来实现查询
   *
   * 这个方法创建一个Consumer函数，用于验证生成的Elasticsearch查询是否符合预期
   * 它接受多个字符串参数，每个字符串代表JSON对象的一部分，然后将这些字符串拼接成完整的JSON对象
   * 最后将实际生成的查询与期望的查询进行比较
   *
   * @param strings expected expressions
   * strings参数：期望的JSON表达式字符串数组，每个字符串代表JSON对象的一部分，如["query: {match: {field: 'value'}}"]
   * @return validation function
   * 返回值：一个Consumer<List>函数，该函数接受一个List参数（包含实际的Elasticsearch查询），并验证它是否符合预期
   */
  public static Consumer<List> elasticsearchChecker(final String... strings) { // 声明公共静态方法，返回一个Consumer函数，用于验证Elasticsearch查询
    requireNonNull(strings, "strings"); // 检查strings参数是否为null，如果是则抛出NullPointerException，确保参数有效性
    return a -> { // 返回一个lambda表达式，实现Consumer接口，接受List参数a（包含实际的Elasticsearch查询）
      ObjectNode actual = // 声明ObjectNode变量actual，用于存储实际的Elasticsearch查询对象
          a == null || a.isEmpty() ? null : (ObjectNode) a.get(0); // 如果List为null或空，则actual为null，否则取List的第一个元素并转换为ObjectNode

      actual = expandDots(actual); // 对actual调用expandDots方法，展开点分隔的属性名为嵌套对象，便于后续比较
      try { // 开始try块，处理可能抛出IO异常的代码

        String json = "{" + String.join(",", strings) + "}"; // 将strings数组中的所有字符串用逗号连接，并用大括号包裹，形成完整的JSON字符串
        ObjectNode expected = (ObjectNode) MAPPER.readTree(json); // 使用MAPPER将JSON字符串解析为JsonNode，并转换为ObjectNode，得到期望的Elasticsearch查询对象
        expected = expandDots(expected); // 对expected调用expandDots方法，展开点分隔的属性名为嵌套对象，与actual保持一致的结构

        if (!expected.equals(actual)) { // 检查expected和actual是否相等，如果不相等则执行断言
          assertThat("expected and actual Elasticsearch queries do not match", // 断言失败时的错误消息，提示期望和实际的Elasticsearch查询不匹配
              MAPPER.writeValueAsString(actual), // 将actual转换为格式化的JSON字符串，作为断言的实际值
              is(MAPPER.writeValueAsString(expected))); // 使用Hamcrest的is匹配器，检查actual是否等于expected（期望值）
        }
      } catch (IOException e) { // 捕获IO异常，处理JSON解析或序列化过程中可能出现的错误
        throw new UncheckedIOException(e); // 将检查型IOException包装为非检查型UncheckedIOException并抛出，简化异常处理
      }
    }; // lambda表达式结束，返回Consumer函数
  } // elasticsearchChecker方法结束

  /**
   * Expands attributes with dots ({@code .}) into sub-nodes.
   * 将包含点（.）的属性扩展为子节点
   * Use for more friendly JSON format:
   * 用于更友好的JSON格式：
   *
   * <pre>
   *   {'a.b.c': 1}
   *   expanded to
   *   {a: { b: {c: 1}}}}
   * </pre>
   *
   * 这个方法将点分隔的属性名转换为嵌套的JSON对象结构
   * 例如：{"query.bool.must": {...}} 转换为 {"query": {"bool": {"must": {...}}}}
   * 这样可以使测试用例中的JSON表达式更简洁易读
   *
   * 处理三种类型的节点：
   * 1. 值节点（ValueNode）：直接返回深拷贝
   * 2. 数组节点（ArrayNode）：递归处理数组中的每个元素
   * 3. 对象节点（ObjectNode）：将点分隔的属性名展开为嵌套对象
   *
   * @param parent current node
   * parent参数：当前要处理的JSON节点，可以是值节点、数组节点或对象节点
   * @param <T> type of node (usually JsonNode).
   * T泛型：节点类型，通常是JsonNode或其子类（ObjectNode、ArrayNode等）
   * @return copy of existing node with field {@code a.b.c} expanded.
   * 返回值：现有节点的副本，其中包含展开后的字段（如a.b.c展开为嵌套结构）
   */
  @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告，因为泛型类型擦除导致编译器无法验证类型转换的安全性
  private static <T extends JsonNode> T expandDots(T parent) { // 声明私有静态泛型方法，接受JsonNode或其子类，返回相同类型的节点副本
    requireNonNull(parent, "parent"); // 检查parent参数是否为null，如果是则抛出NullPointerException，确保参数有效性

    if (parent.isValueNode()) { // 检查parent是否为值节点（如字符串、数字、布尔值等）
      return parent.deepCopy(); // 如果是值节点，直接返回深拷贝，因为值节点不需要展开点分隔的属性名
    } // if语句结束

    // ArrayNode
    // 处理数组节点
    if (parent.isArray()) { // 检查parent是否为数组节点
      ArrayNode arr = (ArrayNode) parent; // 将parent强制转换为ArrayNode类型
      ArrayNode copy = arr.arrayNode(); // 创建一个新的空ArrayNode作为副本
      arr.elements().forEachRemaining(e -> copy.add(expandDots(e))); // 遍历原数组的每个元素，对每个元素递归调用expandDots，并将结果添加到副本数组中
      return (T) copy; // 返回副本数组，强制转换为泛型类型T
    } // if语句结束

    // ObjectNode
    // 处理对象节点
    ObjectNode objectNode = (ObjectNode) parent; // 将parent强制转换为ObjectNode类型
    final ObjectNode copy = objectNode.objectNode(); // 创建一个新的空ObjectNode作为副本
    objectNode.fields().forEachRemaining(e -> { // 遍历原对象的所有字段（键值对）
      final String property = e.getKey(); // 获取当前字段的键（属性名），可能包含点分隔符，如"query.bool.must"
      final JsonNode node = e.getValue(); // 获取当前字段的值（JsonNode），可能是值、数组或对象

      final String[] names = property.split("\\."); // 使用正则表达式按点分割属性名，得到属性名数组，如["query", "bool", "must"]
      ObjectNode copy2 = copy; // 创建一个临时引用，指向副本对象，用于构建嵌套结构
      for (int i = 0; i < names.length - 1; i++) { // 遍历属性名数组，除了最后一个元素（最后一个元素是最终的字段名）
        copy2 = copy2.withObject("/" + names[i]); // 在副本中创建或获取嵌套对象，路径为"/属性名"，逐步构建嵌套结构
      } // for循环结束
      copy2.set(names[names.length - 1], expandDots(node)); // 将最后一个属性名和递归处理后的值设置到嵌套对象的最终位置
    }); // forEachRemaining结束

    return (T) copy; // 返回副本对象，强制转换为泛型类型T
  } // expandDots方法结束

} // 类定义结束
