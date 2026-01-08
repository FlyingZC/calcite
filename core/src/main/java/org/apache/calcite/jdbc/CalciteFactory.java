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
package org.apache.calcite.jdbc; // 定义包名，该类属于org.apache.calcite.jdbc包，提供JDBC相关功能

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory接口，用于创建Java类型系统，处理Java类型与SQL类型之间的映射
import org.apache.calcite.avatica.AvaticaConnection; // 导入AvaticaConnection类，Avatica是Calcite的底层JDBC框架，提供连接管理功能
import org.apache.calcite.avatica.AvaticaFactory; // 导入AvaticaFactory接口，这是Avatica框架的工厂接口，用于创建连接、语句等JDBC对象
import org.apache.calcite.avatica.UnregisteredDriver; // 导入UnregisteredDriver类，表示未注册的JDBC驱动，用于动态加载驱动

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记参数或返回值可能为null，帮助进行空值检查

import java.util.Properties; // 导入Properties类，用于存储JDBC连接的属性信息，如用户名、密码、URL参数等

/**
 * Extension of {@link org.apache.calcite.avatica.AvaticaFactory}
 * for Calcite.
 * 这是AvaticaFactory的Calcite扩展类，用于为Calcite提供JDBC工厂功能
 * 作用：作为Calcite JDBC驱动的工厂基类，负责创建Calcite连接对象，管理JDBC版本信息
 * 核心功能：
 * 1. 存储和管理JDBC的主版本号和次版本号
 * 2. 提供创建Avatica连接对象的能力
 * 3. 扩展AvaticaFactory以支持Calcite特有的schema和type factory
 *
 * 设计模式：抽象工厂模式，定义了创建Calcite连接的接口，具体实现由子类完成
 * 继承关系：CalciteFactory -> AvaticaFactory（接口）
 */
public abstract class CalciteFactory implements AvaticaFactory { // 定义CalciteFactory为抽象类，实现AvaticaFactory接口
  protected final int major; // 成员变量：JDBC主版本号，protected修饰允许子类访问，final表示初始化后不可修改，例如4代表JDBC 4.x
  protected final int minor; // 成员变量：JDBC次版本号，protected修饰允许子类访问，final表示初始化后不可修改，例如1代表JDBC 4.1

  /** Creates a JDBC factory with given major/minor version number.
   * 构造方法：创建一个具有指定主版本号和次版本号的JDBC工厂对象
   * @param major JDBC主版本号，例如4表示JDBC 4.x版本
   * @param minor JDBC次版本号，例如1表示JDBC 4.1版本
   * 作用：初始化工厂对象的版本信息，这些版本号用于标识支持的JDBC规范版本
   */
  protected CalciteFactory(int major, int minor) { // 构造方法定义，接收主版本号和次版本号两个参数
    this.major = major; // 将传入的主版本号参数赋值给成员变量major，完成初始化
    this.minor = minor; // 将传入的次版本号参数赋值给成员变量minor，完成初始化
  }

  @Override public int getJdbcMajorVersion() { // 重写AvaticaFactory接口方法，获取JDBC主版本号，@Override表示这是接口方法的实现
    return major; // 返回存储的主版本号成员变量，供调用者查询当前工厂支持的JDBC主版本
  }

  @Override public int getJdbcMinorVersion() { // 重写AvaticaFactory接口方法，获取JDBC次版本号，@Override表示这是接口方法的实现
    return minor; // 返回存储的次版本号成员变量，供调用者查询当前工厂支持的JDBC次版本
  }

  @Override public final AvaticaConnection newConnection( // 重写AvaticaFactory接口方法，创建新的Avatica连接对象，final表示子类不能重写此方法
      UnregisteredDriver driver, // 参数1：未注册的JDBC驱动对象，用于实际的JDBC驱动功能
      AvaticaFactory factory, // 参数2：工厂对象自身，用于在连接中引用创建它的工厂
      String url, // 参数3：JDBC连接URL字符串，指定要连接的数据库地址和参数
      Properties info) { // 参数4：连接属性集合，包含用户名、密码等连接配置信息
    return newConnection(driver, factory, url, info, null, null); // 调用重载的newConnection方法，传入null作为rootSchema和typeFactory参数，使用默认配置创建连接
  }

  /** Creates a connection with a root schema.
   * 抽象方法：创建带有根schema的连接对象，由子类实现具体的连接创建逻辑
   * @param driver 未注册的JDBC驱动对象，负责底层的JDBC操作
   * @param factory 工厂对象，用于创建连接的工厂实例
   * @param url JDBC连接URL，指定连接地址
   * @param info 连接属性，包含连接配置信息
   * @param rootSchema 可选的根Schema对象，用于定义数据库的schema结构，@Nullable表示可能为null
   * @param typeFactory 可选的Java类型工厂，用于处理Java类型和SQL类型的映射，@Nullable表示可能为null
   * @return 返回新创建的AvaticaConnection连接对象
   * 作用：这是Calcite特有的连接创建方法，允许传入Calcite的rootSchema和typeFactory
   *       rootSchema定义了数据库的元数据结构，typeFactory定义了类型系统
   */
  public abstract AvaticaConnection newConnection(UnregisteredDriver driver, // 抽象方法定义，由子类实现具体的连接创建逻辑
      AvaticaFactory factory, String url, Properties info, // 参数：工厂对象、连接URL、连接属性
      @Nullable CalciteSchema rootSchema, @Nullable JavaTypeFactory typeFactory); // 可选参数：Calcite根schema和Java类型工厂，可能为null
}
