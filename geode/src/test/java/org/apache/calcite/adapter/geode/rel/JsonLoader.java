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
package org.apache.calcite.adapter.geode.rel; // 指定这个类所在的包路径，属于Apache Calcite的Geode适配器模块的rel包

import org.apache.geode.cache.Region; // 导入Apache Geode的Region接口，用于操作Geode缓存区域
import org.apache.geode.pdx.PdxInstance; // 导入PdxInstance接口，表示PDX(Portable Data Exchange)实例，用于跨版本数据交换
import org.apache.geode.pdx.PdxInstanceFactory; // 导入PdxInstanceFactory接口，用于创建PDX实例的工厂类

import com.fasterxml.jackson.databind.ObjectMapper; // 导入Jackson库的ObjectMapper类，用于JSON数据的序列化和反序列化

import java.io.BufferedReader; // 导入BufferedReader类，用于缓冲读取字符流
import java.io.IOException; // 导入IOException类，用于处理输入输出异常
import java.io.InputStream; // 导入InputStream类，表示字节输入流
import java.io.InputStreamReader; // 导入InputStreamReader类，用于将字节流转换为字符流
import java.io.Reader; // 导入Reader类，表示字符输入流的抽象基类
import java.nio.charset.StandardCharsets; // 导入StandardCharsets类，用于指定标准字符集(如UTF-8)
import java.util.ArrayList; // 导入ArrayList类，提供动态数组的实现
import java.util.List; // 导入List接口，表示有序集合
import java.util.Map; // 导入Map接口，表示键值对映射

import static java.util.Objects.requireNonNull; // 静态导入Objects类的requireNonNull方法，用于参数非空校验

/**
 * Populates a geode region from a file having JSON entries (line by line).
 * 从包含JSON条目(逐行)的文件中填充Geode区域
 * 
 * 这个类的主要功能是：
 * 1. 读取包含JSON数据的文件(每行一个JSON对象)
 * 2. 将JSON数据解析为Map对象
 * 3. 将Map对象转换为Geode的PDX实例
 * 4. 将PDX实例存储到指定的Geode Region中
 * 
 * 使用场景：
 * - 在测试环境中快速初始化Geode Region的数据
 * - 从JSON文件批量导入数据到Geode缓存
 * - 支持嵌套的JSON结构，递归转换为PDX对象
 */
class JsonLoader { // 定义JsonLoader类，用于将JSON数据加载到Geode Region中

  private static final String ROOT_PACKATE = "org.apache.calcite.adapter.geode"; // 定义根包名常量，用作PDX类型的基础包名(注意：PACKATE是PACKAGE的拼写错误，但为了保持兼容性未修改)

  private final String rootPackage; // 成员变量：存储PDX实例的根包名，用于生成PDX类型名称
  private final Region region; // 成员变量：引用Geode的Region对象，用于存储加载的数据
  private final ObjectMapper mapper; // 成员变量：Jackson的ObjectMapper实例，用于JSON数据的解析和序列化

  JsonLoader(Region<?, ?> region) { // 构造方法：创建JsonLoader实例，初始化必要的成员变量
    this.region = requireNonNull(region, "region"); // 校验region参数不为null，如果为null则抛出NullPointerException，并赋值给成员变量
    this.rootPackage = ROOT_PACKATE; // 将根包名常量赋值给成员变量，用于后续PDX实例创建
    this.mapper = new ObjectMapper(); // 创建新的ObjectMapper实例，用于JSON解析
  }

  private void load(Reader reader) throws IOException { // 私有方法：从Reader中读取JSON数据并加载到Region中
    requireNonNull(reader, "reader"); // 校验reader参数不为null，如果为null则抛出NullPointerException
    try (BufferedReader br = new BufferedReader(reader)) { // 使用try-with-resources创建BufferedReader，自动管理资源关闭
      List<Map<String, Object>> mapList = new ArrayList<>(); // 创建ArrayList用于存储解析后的JSON Map对象列表
      for (String line; (line = br.readLine()) != null;) { // 逐行读取文件内容，直到文件结束
        @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告，因为mapper.readValue返回的是原始类型
        Map<String, Object> jsonMap = mapper.readValue(line, Map.class); // 将每行JSON字符串解析为Map对象，键为String，值为Object
        mapList.add(jsonMap); // 将解析后的Map对象添加到列表中
      }
      loadMapList(mapList); // 调用loadMapList方法，将Map列表加载到Region中
    }
  }

  void loadMapList(List<Map<String, Object>> mapList) { // 公共方法：将Map列表转换为PDX实例并存储到Region中
    int key = 0; // 初始化Region的键为0，使用递增的整数作为键
    for (Map<String, Object> jsonMap : mapList) { // 遍历Map列表中的每个Map对象
      PdxInstance pdxInstance = mapToPdx(rootPackage, jsonMap); // 调用mapToPdx方法，将Map转换为PDX实例
      region.put(key++, pdxInstance); // 将PDX实例存入Region，使用递增的整数作为键
    }
  }

  void loadClasspathResource(String location) throws IOException { // 公共方法：从类路径资源加载JSON数据
    requireNonNull(location, "location"); // 校验location参数不为null，如果为null则抛出NullPointerException
    InputStream is = getClass().getResourceAsStream(location); // 从类路径中获取指定位置的资源输入流
    if (is == null) { // 检查资源是否存在
      throw new IllegalArgumentException("Resource " + location + " not found in classpath"); // 如果资源不存在，抛出IllegalArgumentException异常
    }

    load(new InputStreamReader(is, StandardCharsets.UTF_8)); // 将输入流转换为UTF-8编码的Reader，并调用load方法加载数据
  }

  private PdxInstance mapToPdx(String packageName, Map<String, Object> map) { // 私有方法：将Map递归转换为PDX实例
    PdxInstanceFactory pdxBuilder = region.getRegionService().createPdxInstanceFactory(packageName); // 从Region服务获取PdxInstanceFactory，使用指定的包名创建PDX构建器

    for (String name : map.keySet()) { // 遍历Map中的所有键名
      Object value = map.get(name); // 获取当前键对应的值

      if (value instanceof Map) { // 检查值是否为Map类型(表示嵌套的JSON对象)
        pdxBuilder.writeObject(name, mapToPdx(packageName + "." + name, (Map) value)); // 如果是嵌套Map，递归调用mapToPdx方法，将包名和当前字段名拼接作为新包名，创建嵌套的PDX实例
      } else { // 如果值不是Map类型(基本类型或简单对象)
        pdxBuilder.writeObject(name, value); // 直接将值写入PDX实例的指定字段
      }
    }

    return pdxBuilder.create(); // 创建并返回PDX实例
  }

}