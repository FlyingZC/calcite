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
package org.apache.calcite.adapter.geode.simple; // 声明包名，该类属于org.apache.calcite.adapter.geode.simple包，是Apache Calcite框架中Geode适配器的简单模式工厂实现

import org.apache.calcite.schema.Schema; // 导入Calcite的Schema接口，表示数据模式，包含表、函数等数据库对象的集合
import org.apache.calcite.schema.SchemaFactory; // 导入Calcite的SchemaFactory接口，用于创建Schema实例的工厂接口
import org.apache.calcite.schema.SchemaPlus; // 导入Calcite的SchemaPlus接口，扩展了Schema接口，允许添加子Schema

import java.util.Map; // 导入Java的Map接口，用于存储键值对集合

/**
 * Geode Simple Table Schema Factory. // 类注释：Geode简单表模式工厂类
 * 
 * 这个类实现了Calcite的SchemaFactory接口，用于创建基于Apache Geode的简单数据模式
 * Apache Geode是一个内存数据网格平台，提供了分布式数据管理功能
 * 该工厂类负责根据配置参数创建GeodeSimpleSchema实例，将Geode的Region映射为Calcite的表
 * 
 * 主要功能：
 * 1. 实现SchemaFactory接口，提供Schema创建能力
 * 2. 解析配置参数（定位器主机、端口、Region名称等）
 * 3. 创建并返回GeodeSimpleSchema实例
 * 
 * 使用场景：
 * - 在Calcite模型配置文件中声明该工厂类
 * - 通过配置参数连接到Geode集群
 * - 将Geode的Region作为表暴露给SQL查询
 */
public class GeodeSimpleSchemaFactory implements SchemaFactory { // 定义公共类GeodeSimpleSchemaFactory，实现SchemaFactory接口

  public static final String LOCATOR_HOST = "locatorHost"; // 定义常量：配置参数键名，用于指定Geode定位器的主机地址或IP
  public static final String LOCATOR_PORT = "locatorPort"; // 定义常量：配置参数键名，用于指定Geode定位器的端口号
  public static final String REGIONS = "regions"; // 定义常量：配置参数键名，用于指定要映射为表的Geode Region名称列表（逗号分隔）
  public static final String PDX_SERIALIZABLE_PACKAGE_PATH = "pdxSerializablePackagePath"; // 定义常量：配置参数键名，用于指定PDX（Portable Data eXchange）可序列化类的包路径，用于反序列化Geode中的对象
  public static final String COMMA_DELIMITER = ","; // 定义常量：逗号分隔符，用于分割Region名称字符串

  public GeodeSimpleSchemaFactory() { // 无参构造方法，创建GeodeSimpleSchemaFactory实例
  } // 构造方法体为空，不需要任何初始化操作

  @SuppressWarnings("rawtypes") // 抑制编译器警告：忽略使用原始类型Map的警告，因为operand参数的类型是泛型Map<String, Object>，但后续会强制转换为原始类型Map
  @Override public Schema create(SchemaPlus parentSchema, // 重写SchemaFactory接口的create方法，创建Schema实例；参数parentSchema：父Schema对象，用于构建Schema层次结构
      String name, Map<String, Object> operand) { // 参数name：Schema的名称；参数operand：包含配置参数的Map对象，键为String类型，值为Object类型
    Map map = (Map) operand; // 将泛型Map<String, Object>强制转换为原始类型Map，以便后续使用get方法时不进行类型检查

    String locatorHost = (String) map.get(LOCATOR_HOST); // 从配置Map中获取定位器主机地址，键为LOCATOR_HOST常量，并强制转换为String类型
    int locatorPort = Integer.valueOf((String) map.get(LOCATOR_PORT)); // 从配置Map中获取定位器端口号，键为LOCATOR_PORT常量，先转换为String再解析为int类型
    String[] regionNames = ((String) map.get(REGIONS)).split(COMMA_DELIMITER); // 从配置Map中获取Region名称字符串，键为REGIONS常量，使用逗号分隔符分割成字符串数组
    String pdxSerializablePackagePath = (String) map.get(PDX_SERIALIZABLE_PACKAGE_PATH); // 从配置Map中获取PDX可序列化类的包路径，键为PDX_SERIALIZABLE_PACKAGE_PATH常量

    return new GeodeSimpleSchema(locatorHost, locatorPort, regionNames, pdxSerializablePackagePath); // 创建并返回GeodeSimpleSchema实例，传入解析出的定位器主机、端口、Region名称数组和PDX包路径
  } // create方法结束，返回创建的Schema对象
} // 类定义结束
