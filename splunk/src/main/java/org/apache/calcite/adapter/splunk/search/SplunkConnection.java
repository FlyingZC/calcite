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
// Apache许可证声明，定义了代码的使用权限和限制条件
package org.apache.calcite.adapter.splunk.search; // 指定该类所在的包路径，位于org.apache.calcite.adapter.splunk.search包下

import org.apache.calcite.linq4j.Enumerator; // 导入Calcite的LINQ4J库中的Enumerator接口，用于枚举查询结果

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker框架的Nullable注解，用于标记可空参数

import java.util.List; // 导入Java集合框架的List接口，用于存储字段列表
import java.util.Map; // 导入Java集合框架的Map接口，用于存储键值对参数

/**
 * Connection to Splunk.
 * Splunk连接接口，定义了与Splunk服务器进行交互的基本操作
 * Splunk是一个用于搜索、监控和分析机器生成数据的平台
 * 该接口提供了两种方式获取搜索结果：异步回调和同步枚举
 */
public interface SplunkConnection { // 定义SplunkConnection接口，作为连接Splunk服务器的抽象
  void getSearchResults(String search, Map<String, String> otherArgs, // 定义异步获取搜索结果的方法，search参数表示Splunk搜索查询语句
      @Nullable List<String> fieldList, SearchResultListener srl); // fieldList参数指定需要返回的字段列表（可为空），srl参数是搜索结果监听器，用于异步接收结果

  Enumerator<Object> getSearchResultEnumerator(String search, // 定义同步获取搜索结果枚举器的方法，search参数表示Splunk搜索查询语句
      Map<String, String> otherArgs, @Nullable List<String> fieldList); // otherArgs参数表示额外的搜索参数（键值对），fieldList参数指定需要返回的字段列表（可为空）
} // 接口定义结束
