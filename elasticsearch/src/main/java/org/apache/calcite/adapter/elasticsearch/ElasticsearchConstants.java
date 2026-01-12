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
package org.apache.calcite.adapter.elasticsearch; // 定义包路径，Elasticsearch适配器包，包含Calcite与Elasticsearch集成的核心代码

import com.google.common.collect.ImmutableSet; // 导入Google Guava库的不可变集合类，用于创建不可变的Set集合

import java.util.Set; // 导入Java标准库的Set接口，用于存储不重复元素的集合

/**
 * Internal constants referenced in this package.
 */ // 接口注释：ElasticsearchConstants接口定义了Elasticsearch适配器包内部使用的常量，这些常量主要用于与Elasticsearch的元数据和查询操作进行交互，包括索引、类型、文档ID等关键标识符，以及特殊的字段名称检测逻辑
interface ElasticsearchConstants { // 定义Elasticsearch常量接口，包含所有Elasticsearch适配器需要用到的常量定义和工具方法

  String INDEX = "_index"; // 定义Elasticsearch文档的索引名称字段常量，"_index"是Elasticsearch中存储文档所属索引名的元数据字段，用于标识文档属于哪个索引
  String TYPE = "_type"; // 定义Elasticsearch文档的类型名称字段常量，"_type"是Elasticsearch中存储文档类型的元数据字段（注：在ES 7.x+中已废弃，每个索引只有一个类型）
  String FIELDS = "fields"; // 定义Elasticsearch文档的字段集合常量，"fields"用于在查询结果中指定要返回的字段列表，可以控制返回哪些字段数据
  String SOURCE_PAINLESS = "params._source"; // 定义在Painless脚本语言中访问文档源数据的路径常量，Painless是Elasticsearch的安全脚本语言，通过params._source可以访问文档的原始JSON数据
  String SOURCE_GROOVY = "_source"; // 定义在Groovy脚本语言中访问文档源数据的字段常量，"_source"字段存储了文档的原始JSON数据，Groovy是ES早期版本支持的脚本语言（已不推荐使用）

  /**
   * Attribute that uniquely identifies a document (ID).
   *
   * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-id-field.html">ID Field</a>
   */ // 方法注释：定义Elasticsearch文档的唯一标识符字段常量，"_id"字段存储了文档的唯一ID，用于在索引中唯一标识每个文档，可以通过此ID进行精确查询、更新和删除操作
  String ID = "_id"; // 定义Elasticsearch文档ID字段常量，"_id"是Elasticsearch中每个文档的唯一标识符，由用户指定或在索引时自动生成，用于文档的CRUD操作
  String UID = "_uid"; // 定义Elasticsearch文档的复合唯一标识符字段常量，"_uid"是"_type#_id"的组合形式，用于在ES多类型时代唯一标识文档，格式为"类型名#文档ID"

  Set<String> META_COLUMNS = ImmutableSet.of(UID, ID, TYPE, INDEX); // 定义Elasticsearch元数据列的不可变集合，包含所有Elasticsearch系统元数据字段名，用于在查询结果中识别和处理这些特殊字段，ImmutableSet确保集合不可变，防止被意外修改

  /**
   * Detects {@code select * from elastic} types of field name (select star).
   *
   * @param name name of the field
   * @return {@code true} if this field represents whole raw, {@code false} otherwise
   */ // 方法注释：静态方法，用于检测给定的字段名是否代表"select *"查询类型，即查询所有字段的情况，当字段名为"_MAP"时表示需要返回整个文档的原始数据
  static boolean isSelectAll(String name) { // 定义静态工具方法，判断字段名是否为特殊的"_MAP"标识符，该方法用于识别是否需要返回完整的文档源数据
    return "_MAP".equals(name); // 判断输入的字段名是否等于"_MAP"字符串，如果相等则返回true表示这是一个select all查询，需要返回整个文档的原始JSON数据
  } // 方法结束，返回布尔值指示是否为select all查询

} // 接口定义结束，ElasticsearchConstants接口提供了Elasticsearch适配器所需的所有常量定义和工具方法
