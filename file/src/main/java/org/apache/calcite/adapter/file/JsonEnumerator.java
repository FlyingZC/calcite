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
package org.apache.calcite.adapter.file; // 定义包名，该类属于Calcite文件适配器包

// 导入Calcite的LINQ4j枚举器接口，用于实现数据遍历功能
import org.apache.calcite.linq4j.Enumerator;
// 导入Calcite的LINQ4j工具类，用于创建枚举器
import org.apache.calcite.linq4j.Linq4j;
// 导入Calcite的关系数据类型接口，表示表的结构信息
import org.apache.calcite.rel.type.RelDataType;
// 导入Calcite的关系数据类型工厂，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;
// 导入Calcite的Pair工具类，用于将两个值配对
import org.apache.calcite.util.Pair;
// 导入Calcite的数据源类，表示可以读取的数据源（文件、URL等）
import org.apache.calcite.util.Source;

// 导入Jackson的JsonParser，用于配置JSON解析器特性
import com.fasterxml.jackson.core.JsonParser;
// 导入Jackson的ObjectMapper，用于JSON数据的读写和转换
import com.fasterxml.jackson.databind.ObjectMapper;
// 导入Jackson的输入不匹配异常，用于处理JSON格式错误
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

// 导入CheckerFramework的可空注解，用于标记可能为null的值
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入Java的ArrayList类，用于动态数组列表
import java.util.ArrayList;
// 导入Java的Arrays工具类，用于数组操作
import java.util.Arrays;
// 导入Java的Collection接口，表示集合类型
import java.util.Collection;
// 导入Java的LinkedHashMap类，用于保持插入顺序的映射
import java.util.LinkedHashMap;
// 导入Java的List接口，表示列表类型
import java.util.List;
// 导入Java的Map接口，表示映射类型
import java.util.Map;

/**
 * 枚举器类，用于从对象列表中读取数据并转换为Calcite可用的Object[]数组格式
 * 该类实现了Calcite的Enumerator接口，是Calcite适配器文件系统读取JSON数据的核心组件
 * 主要功能是将各种类型的对象（Collection、Map、普通对象）转换为统一的Object[]数组格式
 * 以便Calcite查询引擎能够统一处理不同结构的数据源
 */
public class JsonEnumerator implements Enumerator<@Nullable Object[]> {

  // 内部枚举器，用于实际遍历转换后的Object[]数组列表，这是实际的数据迭代器
  private final Enumerator<@Nullable Object[]> enumerator;

  // 构造方法，接收一个对象列表并将其转换为可枚举的Object[]数组列表
  // 参数list: 包含各种类型对象（Collection、Map或普通对象）的列表
  public JsonEnumerator(List<? extends @Nullable Object> list) {
    // 创建用于存储转换后的Object[]数组的列表
    List<@Nullable Object[]> objs = new ArrayList<>();
    // 遍历输入列表中的每个对象，根据对象类型进行不同的转换处理
    for (Object obj : list) {
      // 如果对象是集合类型（Collection），将其转换为Object[]数组
      if (obj instanceof Collection) {
        //noinspection unchecked
        // 强制转换为List类型并调用toArray()方法转换为数组
        List<Object> tmp = (List<Object>) obj;
        // 将转换后的数组添加到结果列表中
        objs.add(tmp.toArray());
      // 如果对象是Map类型，提取其值部分并转换为Object[]数组
      } else if (obj instanceof Map) {
        // 获取LinkedHashMap的所有值并转换为数组
        objs.add(((LinkedHashMap) obj).values().toArray());
      // 如果是普通对象，将其包装为单元素的Object[]数组
      } else {
        // 创建包含单个元素的数组
        objs.add(new Object[]{obj});
      }
    }
    // 使用Linq4j工具类创建枚举器，用于遍历转换后的Object[]数组列表
    enumerator = Linq4j.enumerator(objs);
  }

  /** 静态方法，通过读取JSON文件的第一行或第一个元素来推断表的列名和列类型
   * 该方法是Calcite适配器从JSON文件推断表结构的核心实现
   * 参数typeFactory: Calcite的关系数据类型工厂，用于创建关系数据类型
   * 参数source: 数据源对象，可以是文件、URL或其他可读的JSON数据源
   * 返回值: JsonDataConverter对象，包含推断出的关系数据类型和原始JSON数据列表
   */
  static JsonDataConverter deduceRowType(RelDataTypeFactory typeFactory, Source source) {
    // 创建Jackson的ObjectMapper对象，用于解析JSON数据
    final ObjectMapper objectMapper = new ObjectMapper();
    // 声明用于存储JSON数据列表的变量
    List<Object> list;
    // 创建LinkedHashMap用于存储JSON字段映射，保持插入顺序
    LinkedHashMap<String, Object> jsonFieldMap = new LinkedHashMap<>(1);
    // 声明用于存储解析后的JSON对象的变量
    Object jsonObj = null;
    // 开始尝试读取和解析JSON数据
    try {
      // 配置ObjectMapper以支持更灵活的JSON格式解析
      // 允许字段名不使用引号
      objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
          // 允许使用单引号
          .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
          // 允许JSON中包含注释
          .configure(JsonParser.Feature.ALLOW_COMMENTS, true);

      // 根据数据源的协议类型选择不同的读取方式
      // 如果是文件协议且文件存在
      if ("file".equals(source.protocol()) && source.file().exists()) {
        //noinspection unchecked
        // 从文件读取JSON数据并转换为Object对象
        jsonObj = objectMapper.readValue(source.file(), Object.class);
      // 如果是HTTP、HTTPS或FTP协议
      } else if (Arrays.asList("http", "https", "ftp").contains(source.protocol())) {
        //noinspection unchecked
        // 从URL读取JSON数据并转换为Object对象
        jsonObj = objectMapper.readValue(source.url(), Object.class);
      // 其他情况，使用通用的Reader方式读取
      } else {
        // 从Reader读取JSON数据并转换为Object对象
        jsonObj = objectMapper.readValue(source.reader(), Object.class);
      }

    // 捕获JSON输入不匹配异常（如空文件或格式错误）
    } catch (MismatchedInputException e) {
      // 如果不是"No content"错误（即不是空文件），则抛出运行时异常
      if (!e.getMessage().contains("No content")) {
        throw new RuntimeException("Couldn't read " + source, e);
      }
    // 捕获其他所有异常并抛出运行时异常
    } catch (Exception e) {
      throw new RuntimeException("Couldn't read " + source, e);
    }

    // 根据解析出的JSON对象类型进行不同的处理
    // 如果JSON对象为null（空文件）
    if (jsonObj == null) {
      // 创建空列表
      list = new ArrayList<>();
      // 添加一个特殊标记字段表示空文件
      jsonFieldMap.put("EmptyFileHasNoColumns", Boolean.TRUE);
    // 如果JSON对象是集合类型（通常是JSON数组）
    } else if (jsonObj instanceof Collection) {
      //noinspection unchecked
      // 将JSON对象强制转换为List类型
      list = (List<Object>) jsonObj;
      //noinspection unchecked
      // 获取列表的第一个元素作为字段映射的参考
      jsonFieldMap = (LinkedHashMap) list.get(0);
    // 如果JSON对象是Map类型（通常是JSON对象）
    } else if (jsonObj instanceof Map) {
      //noinspection unchecked
      // 将JSON对象强制转换为LinkedHashMap作为字段映射
      jsonFieldMap = (LinkedHashMap) jsonObj;
      //noinspection unchecked
      // 创建包含Map所有值的列表
      list = new ArrayList(((LinkedHashMap) jsonObj).values());
    // 如果JSON对象是其他类型（如基本类型）
    } else {
      // 创建一个名为"line"的字段，值为当前JSON对象
      jsonFieldMap.put("line", jsonObj);
      // 创建空列表
      list = new ArrayList<>();
      // 将JSON对象添加到列表的第一个位置
      list.add(0, jsonObj);
    }

    // 创建用于存储关系数据类型的列表，大小与字段映射相同
    final List<RelDataType> types = new ArrayList<RelDataType>(jsonFieldMap.size());
    // 创建用于存储字段名的列表，大小与字段映射相同
    final List<String> names = new ArrayList<String>(jsonFieldMap.size());

    // 遍历字段映射的所有键（字段名）
    for (Object key : jsonFieldMap.keySet()) {
      // 根据字段值的Java类型创建对应的Calcite关系数据类型
      final RelDataType type = typeFactory.createJavaType(jsonFieldMap.get(key).getClass());
      // 将字段名添加到名称列表中
      names.add(key.toString());
      // 将数据类型添加到类型列表中
      types.add(type);
    }

    // 使用类型工厂创建结构化类型（表结构），将字段名和类型配对
    RelDataType relDataType = typeFactory.createStructType(Pair.zip(names, types));
    // 返回包含关系数据类型和原始数据列表的转换器对象
    return new JsonDataConverter(relDataType, list);
  }

  // 实现Enumerator接口的current()方法，返回当前元素的Object[]数组
  // 返回值: 当前元素的Object[]数组，包含一行数据的所有列值
  @Override public Object[] current() {
    // 委托给内部枚举器返回当前元素
    return enumerator.current();
  }

  // 实现Enumerator接口的moveNext()方法，移动到下一个元素
  // 返回值: 如果成功移动到下一个元素返回true，否则返回false
  @Override public boolean moveNext() {
    // 委托给内部枚举器移动到下一个元素
    return enumerator.moveNext();
  }

  // 实现Enumerator接口的reset()方法，重置枚举器到初始位置
  @Override public void reset() {
    // 委托给内部枚举器重置位置
    enumerator.reset();
  }

  // 实现Enumerator接口的close()方法，关闭枚举器并释放资源
  @Override public void close() {
    // 委托给内部枚举器关闭并释放资源
    enumerator.close();
  }

  /**
   * JSON数据和关系数据类型的转换器内部类
   * 该类负责封装从JSON文件解析出的表结构信息（列名、列类型）和原始数据列表
   * 作为deduceRowType方法的返回值，为后续的数据处理提供类型信息和数据源
   */
  static class JsonDataConverter {
    // 存储从JSON文件推断出的关系数据类型，包含表的列名、列类型等结构信息
    private final RelDataType relDataType;
    // 存储从JSON文件解析出的原始数据列表，每行数据对应一个元素
    private final List<Object> dataList;

    // 私有构造方法，创建JSON数据转换器实例
    // 参数relDataType: 从JSON推断出的关系数据类型（表结构）
    // 参数dataList: 从JSON解析出的原始数据列表
    private JsonDataConverter(RelDataType relDataType, List<Object> dataList) {
      // 初始化关系数据类型成员变量
      this.relDataType = relDataType;
      // 初始化数据列表成员变量
      this.dataList = dataList;
    }

    // 获取关系数据类型的方法
    // 返回值: 关系数据类型对象，包含表的列名和列类型信息
    RelDataType getRelDataType() {
      // 返回存储的关系数据类型
      return relDataType;
    }

    // 获取数据列表的方法
    // 返回值: 原始JSON数据列表，每行数据对应一个元素
    List<Object> getDataList() {
      // 返回存储的数据列表
      return dataList;
    }
  }
}
