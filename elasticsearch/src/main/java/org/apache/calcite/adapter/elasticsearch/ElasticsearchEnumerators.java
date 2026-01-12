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
package org.apache.calcite.adapter.elasticsearch; // 声明包名，表示该类属于org.apache.calcite.adapter.elasticsearch包，这是Calcite项目中Elasticsearch适配器的核心包

import org.apache.calcite.avatica.util.DateTimeUtils; // 导入日期时间工具类，用于日期转换常量（如MILLIS_PER_DAY）
import org.apache.calcite.linq4j.function.Function1; // 导入函数式接口，表示接受一个参数并返回一个结果的函数，用于定义数据转换逻辑
import org.apache.calcite.linq4j.tree.Primitive; // 导入原始类型包装类，用于处理Java基本类型与包装类型之间的转换

import java.util.Date; // 导入Java标准日期类，用于处理Elasticsearch返回的日期类型数据
import java.util.List; // 导入Java集合接口，用于处理字段列表
import java.util.Map; // 导入Java映射接口，用于处理键值对数据（如字段映射关系）

import static java.util.Objects.requireNonNull; // 导入静态方法，用于参数非空校验

/**
 * Elasticsearch枚举器工具类，提供将Elasticsearch搜索结果转换为Calcite特定返回类型的工具函数
 * 该类主要负责将 {@link ElasticsearchJson.SearchHit} 对象（Elasticsearch的单条搜索结果）
 * 转换为Calcite可以识别和使用的格式，包括Map、Object[]数组、List等
 * 这是Elasticsearch适配器中数据转换的核心类，负责处理从Elasticsearch查询结果到Calcite数据类型的映射
 */
class ElasticsearchEnumerators { // 声明ElasticsearchEnumerators工具类，使用默认访问权限（包私有），仅在同包内可访问

  private ElasticsearchEnumerators() {} // 私有构造函数，防止实例化，因为这是一个纯工具类，所有方法都是静态的

  @SuppressWarnings("unused") // 抑制未使用警告，该方法可能在其他地方被调用或作为预留接口
  private static Function1<ElasticsearchJson.SearchHit, Map> mapGetter() { // 创建一个函数，将SearchHit转换为Map类型，用于处理SELECT *查询场景
    return ElasticsearchJson.SearchHit::sourceOrFields; // 返回一个函数引用，该函数直接调用SearchHit的sourceOrFields()方法，获取原始数据或字段数据
  }

  private static Function1<ElasticsearchJson.SearchHit, Object> singletonGetter( // 创建单字段获取器函数，用于处理只查询单个字段的场景（如SELECT name FROM table）
      final String fieldName, // 参数：要获取的字段名称
      final Class fieldClass, // 参数：字段的目标类型（用于类型转换）
      final Map<String, String> mapping) { // 参数：字段名映射关系（用于处理字段名转换）
    return hit -> { // 返回一个lambda函数，该函数接收SearchHit对象并返回转换后的字段值
      final String key; // 声明用于存储实际字段键的变量
      if (hit.sourceOrFields().containsKey(fieldName)) { // 检查原始数据中是否包含该字段名
        key = fieldName; // 如果包含，直接使用原始字段名作为键
      } else { // 如果原始数据中不包含该字段名
        key = mapping.getOrDefault(fieldName, fieldName); // 从映射表中获取映射后的字段名，如果没有映射则使用原始字段名
      }

      final Object value; // 声明用于存储字段值的变量
      if (ElasticsearchConstants.ID.equals(key) // 判断是否是_id字段（Elasticsearch文档的唯一标识符）
          || ElasticsearchConstants.ID.equals(mapping.getOrDefault(fieldName, fieldName))) { // 或者映射后的字段名是_id
        // is the original projection on _id field? // 判断原始投影是否在_id字段上
        value = hit.id(); // 如果是_id字段，直接从SearchHit中获取文档ID
      } else { // 如果不是_id字段
        value = hit.valueOrNull(key); // 从SearchHit中根据键获取字段值，如果不存在则返回null
      }
      return convert(value, fieldClass); // 将获取的值转换为目标类型并返回
    };
  }

  /**
   * 多字段提取器函数，用于从Elasticsearch搜索结果对象中提取指定的多个字段
   * 该方法创建一个函数，将SearchHit转换为Object[]数组，数组中的每个元素对应一个查询字段
   * 用于处理多字段查询场景（如SELECT a, b, c FROM table）
   *
   * @param fields List of fields to project // 参数：要投影的字段列表，每个元素包含字段名和字段类型
   *
   * @return function that converts the search result into a generic array // 返回值：一个函数，该函数将搜索结果转换为通用对象数组
   */
  private static Function1<ElasticsearchJson.SearchHit, Object[]> listGetter( // 创建多字段获取器函数，用于处理查询多个字段的场景
      final List<Map.Entry<String, Class>> fields, Map<String, String> mapping) { // 参数：字段列表（包含字段名和类型）、字段名映射关系
    return hit -> { // 返回一个lambda函数，该函数接收SearchHit对象并返回对象数组
      Object[] objects = new Object[fields.size()]; // 创建与字段数量相同大小的对象数组，用于存储转换后的字段值
      for (int i = 0; i < fields.size(); i++) { // 遍历所有字段
        final Map.Entry<String, Class> field = fields.get(i); // 获取当前字段（包含字段名和类型）
        final String key; // 声明用于存储实际字段键的变量
        if (hit.sourceOrFields().containsKey(field.getKey())) { // 检查原始数据中是否包含该字段名
          key = field.getKey(); // 如果包含，直接使用原始字段名作为键
        } else { // 如果原始数据中不包含该字段名
          key = mapping.getOrDefault(field.getKey(), field.getKey()); // 从映射表中获取映射后的字段名，如果没有映射则使用原始字段名
        }

        final Object value; // 声明用于存储字段值的变量
        if (ElasticsearchConstants.ID.equals(key) // 判断是否是_id字段
            || ElasticsearchConstants.ID.equals(mapping.get(field.getKey())) // 或者映射后的字段名是_id
            || ElasticsearchConstants.ID.equals(field.getKey())) { // 或者原始字段名是_id
          // is the original projection on _id field? // 判断原始投影是否在_id字段上
          value = hit.id(); // 如果是_id字段，直接从SearchHit中获取文档ID
        } else { // 如果不是_id字段
          value = hit.valueOrNull(key); // 从SearchHit中根据键获取字段值，如果不存在则返回null
        }

        final Class type = field.getValue(); // 获取字段的目标类型
        objects[i] = convert(value, type); // 将获取的值转换为目标类型并存入数组对应位置
      }
      return objects; // 返回包含所有字段值的对象数组
    };
  }

  static Function1<ElasticsearchJson.SearchHit, Object> getter( // 静态工厂方法，根据字段数量返回合适的getter函数
      List<Map.Entry<String, Class>> fields, Map<String, String> mapping) { // 参数：字段列表、字段名映射关系
    requireNonNull(fields, "fields"); // 校验fields参数非空，如果为null则抛出NullPointerException
    //noinspection unchecked // 抑制未检查类型转换警告，因为getter的实际类型会在运行时确定
    final Function1 getter; // 声明getter函数变量
    if (fields.size() == 1) { // 如果只有一个字段
      // select foo from table // 场景1：查询单个字段
      // select * from table // 场景2：查询所有字段（此时fields.size()可能为1，具体实现取决于调用方）
      getter = singletonGetter(fields.get(0).getKey(), fields.get(0).getValue(), mapping); // 使用单字段获取器
    } else { // 如果有多个字段
      // select a, b, c from table // 场景：查询多个字段
      getter = listGetter(fields, mapping); // 使用多字段获取器
    }

    return getter; // 返回创建的getter函数
  }

  @SuppressWarnings("JavaUtilDate") // 抑制使用Java旧版Date类的警告，因为Elasticsearch可能返回Date类型数据
  private static Object convert(Object o, Class clazz) { // 类型转换方法，将Elasticsearch返回的值转换为目标类型
    if (o == null) { // 如果输入值为null
      return null; // 直接返回null
    }
    Primitive primitive = Primitive.of(clazz); // 尝试从目标类型获取对应的原始类型（如int、long等）
    if (primitive != null) { // 如果目标类型是原始类型（如int.class）
      clazz = primitive.boxClass; // 将其转换为包装类型（如Integer.class），便于后续处理
    } else { // 如果目标类型不是原始类型
      primitive = Primitive.ofBox(clazz); // 尝试从包装类型获取对应的原始类型（如Integer -> int）
    }
    if (clazz.isInstance(o)) { // 检查输入值是否已经是目标类型的实例
      return o; // 如果类型匹配，直接返回原值
    }
    if (o instanceof Date && primitive != null) { // 如果输入值是Date类型且目标类型是原始类型
      o = ((Date) o).getTime() / DateTimeUtils.MILLIS_PER_DAY; // 将日期转换为天数（从1970-01-01开始的天数），Calcite内部使用整数表示日期
    }
    if (o instanceof Number && primitive != null) { // 如果输入值是数字类型且目标类型是原始类型
      return primitive.number((Number) o); // 将数字转换为目标原始类型（如将BigDecimal转换为int、long等）
    }
    return o; // 如果以上条件都不满足，返回原值
  }
}
