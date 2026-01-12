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
package org.apache.calcite.adapter.splunk; // 定义包名，表示该类位于org.apache.calcite.adapter.splunk包下，这是Calcite框架中Splunk适配器的包路径

import org.apache.calcite.adapter.splunk.search.SplunkConnection; // 导入SplunkConnection类，用于建立和管理与Splunk服务器的连接
import org.apache.calcite.schema.Table; // 导入Table接口，这是Calcite中表的抽象表示
import org.apache.calcite.schema.impl.AbstractSchema; // 导入AbstractSchema抽象类，这是Calcite中Schema（模式/数据库）的基类，提供了Schema的基本实现

import com.google.common.collect.ImmutableMap; // 导入Google Guava库的ImmutableMap类，用于创建不可变的Map集合，保证线程安全和数据一致性

import java.util.Map; // 导入Java标准库的Map接口，用于存储键值对映射关系

/**
 * Splunk schema. // Splunk模式的类文档注释，说明这个类代表Splunk的数据模式（类似于数据库中的schema）
 * 
 * 这个类继承自AbstractSchema，是Calcite适配器架构中用于表示Splunk数据源的Schema实现
 * Schema在Calcite中相当于一个数据库命名空间，包含一组表
 * SplunkSchema负责管理Splunk数据源中的表定义，并提供给Calcite查询优化器使用
 * 
 * 主要功能：
 * 1. 维护与Splunk服务器的连接（通过SplunkConnection）
 * 2. 提供Splunk数据源中可用的表（目前只有一个名为"splunk"的表）
 * 3. 作为Calcite查询引擎访问Splunk数据的入口点
 * 
 * 在Calcite适配器架构中的位置：
 * - 位于Calcite适配器层，负责将Splunk数据源抽象为Calcite可识别的Schema
 * - 上层：被Calcite的SchemaPlus包装，注册到Calcite的Schema树中
 * - 下层：通过SplunkConnection与Splunk服务器通信，通过SplunkTable表示具体的表结构
 * 
 * 使用场景：
 * 当用户需要通过SQL查询Splunk中的日志数据时，需要创建一个SplunkSchema实例
 * 并将其注册到Calcite的Schema中，然后就可以通过SQL查询Splunk数据了
 */
public class SplunkSchema extends AbstractSchema { // 定义SplunkSchema类，继承自AbstractSchema，表示Splunk的数据模式
  /** The name of the one and only table. */ // 常量注释：说明这是唯一表的名称
  public static final String SPLUNK_TABLE_NAME = "splunk"; // 定义静态常量字符串，表示Splunk Schema中唯一表的名称为"splunk"，这个名称在SQL查询中用于引用Splunk表，例如：SELECT * FROM splunk

  public static final ImmutableMap<String, Table> TABLE_MAP = // 定义静态不可变Map，存储Schema中所有表的映射关系，键为表名，值为Table对象
      ImmutableMap.of(SPLUNK_TABLE_NAME, SplunkTable.INSTANCE); // 使用Guava的ImmutableMap创建一个只包含一个条目的Map，将"splunk"表名映射到SplunkTable单例实例，这个Map在getTableMap()方法中被返回

  public final SplunkConnection splunkConnection; // 定义最终的成员变量，存储与Splunk服务器的连接对象，这个连接用于执行实际的Splunk查询并获取结果，final表示该引用不可变，但连接对象本身的状态可以改变

  /** Creates a SplunkSchema. */ // 构造方法注释：说明这是创建SplunkSchema实例的构造方法
  public SplunkSchema(SplunkConnection splunkConnection) { // 定义构造方法，接收一个SplunkConnection参数，用于初始化Schema与Splunk服务器的连接
    super(); // 调用父类AbstractSchema的构造方法，完成父类的初始化工作
    this.splunkConnection = splunkConnection; // 将传入的SplunkConnection参数赋值给成员变量，保存与Splunk服务器的连接引用，后续可以通过这个连接执行查询
  }

  @Override protected Map<String, Table> getTableMap() { // 重写父类AbstractSchema的getTableMap()方法，protected表示只能被子类或同包类访问，返回Schema中所有表的映射
    return TABLE_MAP; // 返回预定义的不可变Map TABLE_MAP，其中包含"splunk"表名到SplunkTable单例的映射，这样Calcite查询引擎就能知道这个Schema中有哪些表可用
  }
}
