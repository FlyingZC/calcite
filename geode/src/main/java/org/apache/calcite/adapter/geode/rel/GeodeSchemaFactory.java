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
package org.apache.calcite.adapter.geode.rel; // 指定当前类所在的包路径，即 org.apache.calcite.adapter.geode.rel

import org.apache.calcite.model.ModelHandler; // 导入 ModelHandler 类，用于处理模型相关的操作，如添加函数等
import org.apache.calcite.runtime.SpatialTypeFunctions; // 导入空间类型函数类，提供空间相关的函数支持
import org.apache.calcite.schema.Schema; // 导入 Schema 接口，表示 Calcite 中的模式定义
import org.apache.calcite.schema.SchemaFactory; // 导入 SchemaFactory 接口，用于创建 Schema 的工厂接口
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 类，扩展了 Schema 接口，提供额外的功能

import com.google.common.collect.ImmutableList; // 导入 Google Guava 库中的 ImmutableList 类，用于创建不可变列表

import java.util.Arrays; // 导入 Java 标准库中的 Arrays 类，用于数组操作
import java.util.Map; // 导入 Java 标准库中的 Map 接口，用于存储键值对

import static org.apache.calcite.adapter.geode.util.GeodeUtils.createClientCache; // 静态导入 GeodeUtils 类中的 createClientCache 方法，用于创建 Geode 客户端缓存

/**
 * Factory that creates a {@link GeodeSchema}. // 这是一个工厂类，用于创建 GeodeSchema 实例，GeodeSchema 是 Apache Geode 数据库在 Calcite 中的模式表示
 * 该类实现了 SchemaFactory 接口，是 Calcite 适配器模式的一部分，负责根据配置参数创建 Geode 数据源的 Schema
 * Apache Geode 是一个内存数据网格平台，提供分布式数据管理功能，该工厂类允许 Calcite 查询引擎连接和查询 Geode 数据
 */ // 类级别的 JavaDoc 注释，说明该类的作用是创建 GeodeSchema 的工厂
@SuppressWarnings("UnusedDeclaration") // 抑制未使用声明的警告，表示该类可能通过反射或其他方式被使用，编译器无需警告
public class GeodeSchemaFactory implements SchemaFactory { // 定义 GeodeSchemaFactory 类，实现 SchemaFactory 接口，使其能够被 Calcite 识别和使用

  public static final String LOCATOR_HOST = "locatorHost"; // 定义常量 LOCATOR_HOST，表示 Geode 定位器的主机地址配置参数名，用于连接 Geode 集群
  public static final String LOCATOR_PORT = "locatorPort"; // 定义常量 LOCATOR_PORT，表示 Geode 定位器的端口号配置参数名，用于连接 Geode 集群
  public static final String REGIONS = "regions"; // 定义常量 REGIONS，表示 Geode 区域名称列表的配置参数名，多个区域用逗号分隔
  public static final String PDX_SERIALIZABLE_PACKAGE_PATH = "pdxSerializablePackagePath"; // 定义常量 PDX_SERIALIZABLE_PACKAGE_PATH，表示 PDX 可序列化类的包路径配置参数名，PDX 是 Geode 的序列化机制
  public static final String ALLOW_SPATIAL_FUNCTIONS = "spatialFunction"; // 定义常量 ALLOW_SPATIAL_FUNCTIONS，表示是否允许空间函数的配置参数名，用于启用或禁用空间函数支持
  public static final String COMMA_DELIMITER = ","; // 定义常量 COMMA_DELIMITER，表示逗号分隔符，用于分割区域名称列表

  public GeodeSchemaFactory() { // 定义无参构造方法，用于创建 GeodeSchemaFactory 实例
    // Do Nothing // 构造方法体为空，不需要执行任何初始化操作
  }

  @Override public synchronized Schema create(SchemaPlus parentSchema, String name, // 重写 SchemaFactory 接口的 create 方法，使用 synchronized 关键字保证线程安全，创建并返回一个 GeodeSchema 实例
      Map<String, Object> operand) { // operand 参数包含创建 Schema 所需的配置信息，如定位器地址、端口、区域名称等
    Map map = (Map) operand; // 将 operand 参数强制转换为 Map 类型，方便后续操作获取配置值
    String locatorHost = (String) map.get(LOCATOR_HOST); // 从配置 Map 中获取定位器主机地址，用于连接 Geode 集群
    int locatorPort = Integer.valueOf((String) map.get(LOCATOR_PORT)); // 从配置 Map 中获取定位器端口号，并转换为整型，用于连接 Geode 集群
    String[] regionNames = ((String) map.get(REGIONS)).split(COMMA_DELIMITER); // 从配置 Map 中获取区域名称字符串，使用逗号分隔符分割成字符串数组，每个元素代表一个 Geode 区域
    String pbxSerializablePackagePath = (String) map.get(PDX_SERIALIZABLE_PACKAGE_PATH); // 从配置 Map 中获取 PDX 可序列化类的包路径，用于 Geode 的反序列化操作

    boolean allowSpatialFunctions = true; // 初始化 allowSpatialFunctions 变量为 true，默认允许使用空间函数
    if (map.containsKey(ALLOW_SPATIAL_FUNCTIONS)) { // 检查配置 Map 中是否包含空间函数的配置项
      allowSpatialFunctions = Boolean.valueOf((String) map.get(ALLOW_SPATIAL_FUNCTIONS)); // 如果包含，则从配置中获取该值并转换为布尔类型，覆盖默认值
    }

    if (allowSpatialFunctions) { // 如果允许使用空间函数
      ModelHandler.addFunctions(parentSchema, null, ImmutableList.of(), // 调用 ModelHandler.addFunctions 方法，将空间类型函数添加到父 Schema 中，使 SQL 查询可以使用空间函数
          SpatialTypeFunctions.class.getName(), "*", true); // 参数说明：parentSchema 为父 Schema，null 表示不使用特定方法名，ImmutableList.of() 表示空参数列表，SpatialTypeFunctions.class.getName() 为函数类名，"*" 表示所有函数，true 表示是否替换
    }

    return new GeodeSchema( // 创建并返回一个新的 GeodeSchema 实例，该实例封装了与 Geode 数据源的连接和区域信息
        createClientCache(locatorHost, locatorPort, pbxSerializablePackagePath, true), // 调用 createClientCache 方法创建 Geode 客户端缓存，传入定位器主机、端口、PDX 包路径和 true 表示使用池
        Arrays.asList(regionNames)); // 将区域名称数组转换为列表，传递给 GeodeSchema 构造方法，指定要访问的 Geode 区域
  }
}
