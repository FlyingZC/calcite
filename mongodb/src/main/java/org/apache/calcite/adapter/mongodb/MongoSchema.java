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
// Apache许可证声明，表明该代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.mongodb; // 定义包名，该类属于Calcite框架的MongoDB适配器模块

import org.apache.calcite.schema.Table; // 导入Calcite的Table接口，表示数据库表
import org.apache.calcite.schema.impl.AbstractSchema; // 导入Calcite的抽象Schema基类，MongoSchema将继承此类

import com.google.common.annotations.VisibleForTesting; // 导入Google Guava的注解，用于标记测试可见的方法
import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map类，用于构建表映射
import com.mongodb.MongoClientSettings; // 导入MongoDB客户端设置类，用于配置连接参数
import com.mongodb.client.MongoClient; // 导入MongoDB客户端接口，用于与MongoDB服务器通信
import com.mongodb.client.MongoClients; // 导入MongoDB客户端工厂类，用于创建MongoClient实例
import com.mongodb.client.MongoDatabase; // 导入MongoDB数据库接口，表示MongoDB中的一个数据库

import java.util.Map; // 导入Java的Map接口，用于存储键值对

import static java.util.Objects.requireNonNull; // 静态导入Objects的requireNonNull方法，用于空值检查

/**
 * Schema mapped onto a directory of MONGO files. Each table in the schema
 * is a MONGO file in that directory.
 */
// 类注释：MongoSchema类表示映射到MongoDB数据库的Schema（模式）
// 在Calcite框架中，Schema是一个逻辑概念，表示一组相关的表
// 这个类继承自AbstractSchema，实现了与MongoDB数据库的集成
// MongoDB中的每个集合(Collection)对应Calcite中的一个表(Table)
public class MongoSchema extends AbstractSchema { // MongoSchema类定义，继承AbstractSchema抽象类
  final MongoDatabase mongoDb; // 成员变量：MongoDatabase实例，表示连接到的MongoDB数据库
                                // final关键字表示该引用不可变，确保数据库连接的稳定性
                                // MongoDatabase是MongoDB Java驱动提供的接口，用于操作数据库

  /**
   * Creates a MongoDB schema.
   *
   * @param settings Mongo connection settings, including host and credentials
   * @param database Mongo database name, e.g. "foodmart"
   */
  // 方法注释：构造函数，根据MongoDB连接设置和数据库名称创建MongoSchema实例
  // @param settings MongoClientSettings对象，包含MongoDB连接配置（主机地址、端口、认证信息等）
  // @param database 数据库名称字符串，例如"foodmart"
  MongoSchema(MongoClientSettings settings, String database) { // 构造函数定义，接收连接设置和数据库名
    super(); // 调用父类AbstractSchema的构造函数，完成父类的初始化

    final MongoClient mongo = MongoClients.create(settings); // 使用MongoClients工厂创建MongoClient实例
                                                              // MongoClient是与MongoDB服务器通信的入口点
                                                              // settings参数包含连接池、超时、认证等配置
    try { // 开始try块，用于捕获数据库操作可能抛出的异常
      this.mongoDb = mongo.getDatabase(database); // 从MongoClient获取指定名称的MongoDatabase实例
                                                  // getDatabase方法不会立即连接数据库，而是返回一个代理对象
                                                  // 只有在实际执行操作时才会建立连接
    } catch (Exception e) { // 捕获获取数据库时可能抛出的异常（如数据库名称无效、权限不足等）
      mongo.close(); // 发生异常时关闭MongoClient，释放资源
                    // close方法会关闭底层的连接池，避免资源泄漏
      throw new RuntimeException(e); // 将捕获的异常包装为RuntimeException抛出
                                     // RuntimeException是未检查异常，调用者可以选择处理或不处理
    } // try-catch块结束
  } // 构造函数结束

  /**
   * Allows tests to inject their instance of the database.
   *
   * @param mongoDb existing mongo database instance
   */
  // 方法注释：测试用的构造函数，允许直接注入MongoDatabase实例
  // 这个构造函数使用@VisibleForTesting注解，表示主要用于测试目的
  // 在单元测试中，可以注入模拟的MongoDatabase对象，避免需要真实的MongoDB连接
  // @param mongoDb 已存在的MongoDatabase实例
  @VisibleForTesting // Google Guava注解，表示该方法或构造函数对测试代码可见
  MongoSchema(MongoDatabase mongoDb) { // 测试构造函数定义，直接接收MongoDatabase实例
    super(); // 调用父类AbstractSchema的构造函数
    this.mongoDb = requireNonNull(mongoDb, "mongoDb"); // 使用requireNonNull方法检查mongoDb参数是否为null
                                                        // 如果为null，抛出NullPointerException，并附带错误信息"mongoDb"
                                                        // 这是一个防御性编程实践，确保对象状态的有效性
  } // 测试构造函数结束

  @Override protected Map<String, Table> getTableMap() { // 重写AbstractSchema的getTableMap方法
                                                          // @Override注解表示这是对父类方法的重写
                                                          // protected表示该方法对子类和同包类可见
                                                          // 返回类型是Map<String, Table>，表示表名到Table对象的映射
    final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder(); // 创建不可变Map的构建器
                                                                                 // ImmutableMap是线程安全的，一旦创建就不能修改
                                                                                 // Builder模式提供流畅的API来构建Map
    for (String collectionName : mongoDb.listCollectionNames()) { // 遍历MongoDB数据库中的所有集合名称
                                                                   // listCollectionNames()返回数据库中所有集合的迭代器
                                                                   // MongoDB中的集合相当于关系数据库中的表
      builder.put(collectionName, new MongoTable(collectionName)); // 将集合名称和对应的MongoTable对象放入构建器
                                                                   // collectionName作为Map的键
                                                                   // 创建新的MongoTable实例作为Map的值
                                                                   // MongoTable是Calcite中MongoDB集合的抽象表示
    } // for循环结束
    return builder.build(); // 调用builder的build方法，构建并返回不可变的Map
                            // 返回的Map包含了数据库中所有集合对应的Table对象
                            // Calcite会使用这个Map来查询和操作表
  } // getTableMap方法结束
} // MongoSchema类结束
