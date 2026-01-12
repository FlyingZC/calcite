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
package org.apache.calcite.test; // 声明包名，该类位于 org.apache.calcite.test 包下，属于 Calcite 测试工具包的一部分

import com.google.errorprone.annotations.Immutable; // 导入 Google Error Prone 的 Immutable 注解，用于标记不可变类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Checker Framework 的 Nullable 注解，用于标记可能为 null 的字段

/** Information necessary to create a JDBC connection. // 类文档注释：ConnectionSpec 类用于封装创建 JDBC 连接所需的所有信息，是一个不可变的连接规范类
 *
 * <p>Specify one to run tests against a different database. */ // 允许通过指定不同的连接规范来针对不同的数据库运行测试
@Immutable // 使用注解标记该类为不可变类，一旦创建后其状态就不能被修改，确保线程安全
public class ConnectionSpec { // 定义 ConnectionSpec 类，用于存储数据库连接的配置信息
  public final String url; // JDBC 连接 URL，指定数据库的地址和连接参数，例如 "jdbc:mysql://localhost:3306/test"
  public final String username; // 数据库用户名，用于身份验证
  public final String password; // 数据库密码，用于身份验证
  public final String driver; // JDBC 驱动类全限定名，例如 "com.mysql.jdbc.Driver"，用于加载数据库驱动
  public final String schema; // 数据库模式名称，用于指定连接后默认使用的 schema
  public final @Nullable String catalog; // 数据库目录名称，使用 @Nullable 注解标记该字段可以为 null，用于指定数据库的 catalog

  public ConnectionSpec(String url, String username, String password, // 构造方法：创建 ConnectionSpec 对象，接收数据库连接的基本参数
      String driver, String schema) { // 继续构造方法参数：driver 是驱动类名，schema 是模式名
    this.url = url; // 将传入的 URL 参数赋值给实例变量 url
    this.username = username; // 将传入的用户名参数赋值给实例变量 username
    this.password = password; // 将传入的密码参数赋值给实例变量 password
    this.driver = driver; // 将传入的驱动类名参数赋值给实例变量 driver
    this.schema = schema; // 将传入的模式名参数赋值给实例变量 schema
    this.catalog = null; // 将 catalog 字段初始化为 null，表示不指定特定的 catalog
  }
}
