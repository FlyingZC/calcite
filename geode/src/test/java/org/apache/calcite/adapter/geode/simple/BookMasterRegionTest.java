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
package org.apache.calcite.adapter.geode.simple; // 声明该类属于org.apache.calcite.adapter.geode.simple包，这是Calcite框架中Geode适配器的simple子包

import org.apache.geode.cache.Region; // 导入Geode的Region接口，Region是Geode中数据的基本存储和管理单元，类似于Map结构
import org.apache.geode.cache.client.ClientCache; // 导入Geode客户端缓存接口，用于在客户端与Geode集群进行交互
import org.apache.geode.cache.client.ClientCacheFactory; // 导入Geode客户端缓存工厂类，用于创建和配置ClientCache实例
import org.apache.geode.cache.client.ClientRegionShortcut; // 导入客户端Region快捷配置枚举，提供预定义的Region配置选项
import org.apache.geode.cache.query.QueryService; // 导入Geode查询服务接口，用于执行OQL（Object Query Language）查询
import org.apache.geode.cache.query.SelectResults; // 导入查询结果接口，用于表示OQL查询返回的结果集
import org.apache.geode.pdx.ReflectionBasedAutoSerializer; // 导入基于反射的自动序列化器，用于PDX（Portable Data eXchange）格式的序列化和反序列化

/**
 * Test based on BookMaster region. // 这是一个基于BookMaster Region的测试类，用于演示如何使用Geode客户端访问和查询BookMaster数据区域
 * 该类展示了两种访问Geode数据的方式：1. 使用键值对方式直接获取数据 2. 使用OQL查询语言进行复杂查询
 * BookMaster是一个存储图书信息的Region，每本书包含itemNumber（图书编号）、description（描述）、retailCost（零售价格）等字段
 */
class BookMasterRegionTest { // 定义一个名为BookMasterRegionTest的测试类，该类没有访问修饰符，表示包级私有，只能在同一包内访问

  private BookMasterRegionTest() { // 私有构造方法，防止外部创建该类的实例，这是一个工具类模式的实现，所有功能通过静态方法提供
  } // 构造方法体为空，因为该类不需要实例化

  public static void main(String[] args) throws Exception { // 主方法，程序入口点，使用public static void修饰，接收命令行参数数组args，声明可能抛出异常

    ClientCache clientCache = new ClientCacheFactory() // 创建Geode客户端缓存工厂实例，用于配置和构建ClientCache对象
        .addPoolLocator("localhost", 10334) // 添加定位器地址，指定Geode集群的定位器主机为localhost，端口为10334，客户端通过定位器发现和连接到Geode服务器
        .setPdxSerializer(new ReflectionBasedAutoSerializer("org.apache.calcite.adapter.geode.*")) // 设置PDX序列化器，使用反射自动序列化器，自动序列化org.apache.calcite.adapter.geode包下的所有类
        .create(); // 创建并返回配置好的ClientCache实例，建立与Geode集群的连接

    // Using Key/Value // 使用键值对方式访问数据的注释标记
    Region bookMaster = clientCache // 从客户端缓存获取Region工厂，用于创建客户端Region对象
        .createClientRegionFactory(ClientRegionShortcut.PROXY) // 创建客户端Region工厂，使用PROXY快捷配置，PROXY模式表示数据存储在服务器端，客户端只持有代理引用
        .create("BookMaster"); // 创建名为"BookMaster"的Region实例，该Region必须是服务器端已存在的Region

    System.out.println("BookMaster = " + bookMaster.get(789)); // 使用键值对方式获取数据，通过键789从BookMaster Region中获取对应的图书对象，并打印到控制台

    // Using OQL // 使用OQL查询语言访问数据的注释标记
    QueryService queryService = clientCache.getQueryService(); // 从客户端缓存获取查询服务实例，用于执行OQL查询
    String oql = "select itemNumber, description, retailCost from /BookMaster"; // 定义OQL查询语句，从BookMaster Region中选择itemNumber、description和retailCost三个字段，/BookMaster表示Region的路径
    SelectResults result = (SelectResults) queryService.newQuery(oql).execute(); // 创建查询对象并执行查询，将结果转换为SelectResults类型，result包含查询返回的所有记录
    System.out.println(result.asList()); // 将查询结果转换为List集合并打印到控制台，显示所有查询到的图书信息
  } // main方法结束
} // BookMasterRegionTest类定义结束
