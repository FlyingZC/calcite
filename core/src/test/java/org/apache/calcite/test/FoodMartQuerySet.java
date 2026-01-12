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
// Apache许可证头，声明该代码遵循Apache 2.0许可证
package org.apache.calcite.test; // 声明包名，该类位于org.apache.calcite.test包下

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 导入Jackson注解，用于在JSON反序列化时忽略指定属性
import com.fasterxml.jackson.core.JsonParser; // 导入Jackson核心解析器类，用于配置JSON解析特性
import com.fasterxml.jackson.databind.ObjectMapper; // 导入Jackson对象映射器类，用于JSON与Java对象的相互转换

import java.io.IOException; // 导入IO异常类，处理输入输出错误
import java.io.InputStream; // 导入输入流类，用于读取数据
import java.lang.ref.SoftReference; // 导入软引用类，用于实现内存敏感的缓存机制
import java.util.ArrayList; // 导入动态数组列表类，用于存储查询列表
import java.util.LinkedHashMap; // 导入链式哈希映射类，用于维护插入顺序的查询映射
import java.util.List; // 导入列表接口，定义查询集合的类型
import java.util.Map; // 导入映射接口，定义查询ID到查询对象的映射类型

/** Set of queries against the FoodMart database. */ // 类的文档注释：说明这是一个针对FoodMart数据库的查询集合
public class FoodMartQuerySet { // 定义FoodMartQuerySet类，用于管理和加载FoodMart数据库的测试查询集合
  private static SoftReference<FoodMartQuerySet> ref; // 声明静态软引用变量，用于缓存FoodMartQuerySet单例实例，软引用可以在内存不足时被GC回收

  public final Map<Integer, FoodmartQuery> queries = new LinkedHashMap<>(); // 声明公共最终成员变量，使用LinkedHashMap存储查询集合，键为查询ID，值为FoodmartQuery对象，LinkedHashMap保持插入顺序

  private FoodMartQuerySet() throws IOException { // 私有构造方法，防止外部直接实例化，确保单例模式，声明可能抛出IOException异常
    final ObjectMapper mapper = new ObjectMapper(); // 创建Jackson的ObjectMapper实例，用于JSON解析
    mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true); // 配置JSON解析器允许字段名不带引号，提高JSON解析的灵活性
    mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true); // 配置JSON解析器允许使用单引号，增强JSON格式兼容性

    final InputStream inputStream = // 声明输入流变量，用于读取JSON格式的查询数据
        new net.hydromatic.foodmart.queries.FoodmartQuerySet().getQueries(); // 通过net.hydromatic.foodmart.queries包中的FoodmartQuerySet类获取查询数据的输入流
    FoodmartRoot root = mapper.readValue(inputStream, FoodmartRoot.class); // 使用ObjectMapper将输入流中的JSON数据解析为FoodmartRoot对象
    for (FoodmartQuery query : root.queries) { // 遍历解析出的查询列表
      queries.put(query.id, query); // 将查询ID作为键，查询对象作为值存入LinkedHashMap中
    }
  }

  /** Returns the singleton instance of the query set. It is backed by a
   * soft reference, so it may be freed if memory is short and no one is
   * using it. */ // 方法文档注释：说明该方法返回查询集的单例实例，使用软引用支持，内存不足时可能被释放
  public static synchronized FoodMartQuerySet instance() throws IOException { // 公共静态同步方法，获取FoodMartQuerySet单例实例，声明可能抛出IOException异常
    final SoftReference<FoodMartQuerySet> refLocal = ref; // 将静态软引用赋值给局部变量，避免多线程环境下的竞态条件
    if (refLocal != null) { // 检查软引用是否不为空
      final FoodMartQuerySet set = refLocal.get(); // 尝试从软引用中获取FoodMartQuerySet实例
      if (set != null) { // 检查获取的实例是否不为空（即未被GC回收）
        return set; // 返回缓存的实例
      }
    }
    final FoodMartQuerySet set = new FoodMartQuerySet(); // 创建新的FoodMartQuerySet实例，从JSON文件加载查询数据
    ref = new SoftReference<>(set); // 将新创建的实例包装在软引用中，赋值给静态变量
    return set; // 返回新创建的实例
  }

  /** JSON root element. */ // 内部类文档注释：说明这是JSON根元素类
  public static class FoodmartRoot { // 定义FoodmartRoot静态内部类，用于映射JSON根元素结构
    public final List<FoodmartQuery> queries = new ArrayList<>(); // 声明公共最终成员变量，使用ArrayList存储FoodmartQuery对象列表，对应JSON中的queries数组
  }

  /** JSON query element. */ // 内部类文档注释：说明这是JSON查询元素类
  @JsonIgnoreProperties(value = {"columns", "rows"}) // 使用Jackson注解配置在反序列化时忽略columns和rows属性，避免JSON中包含但Java类中不存在的字段导致错误
  public static class FoodmartQuery { // 定义FoodmartQuery静态内部类，用于映射单个查询的JSON结构
    public int id; // 声明公共成员变量，存储查询的ID标识符
    public String sql; // 声明公共成员变量，存储查询的SQL语句字符串

    @Override public String toString() { // 重写toString方法，提供对象的字符串表示
      return "id=" + id; // 返回查询ID的字符串表示，格式为"id=xxx"
    }
  }
} // 类结束
