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
// Apache许可证头文件，声明版权信息和使用授权
package org.apache.calcite.chinook; // 声明包名，该类位于org.apache.calcite.chinook包下，专门用于Chinook测试场景

import com.google.common.io.CharStreams; // 导入Google Guava库的CharStreams工具类，用于高效读取字符流

import java.io.IOException; // 导入IO异常类，用于处理输入输出操作时的异常
import java.io.InputStream; // 导入输入流类，用于读取资源文件
import java.io.InputStreamReader; // 导入输入流读取器类，用于将字节流转换为字符流
import java.nio.charset.StandardCharsets; // 导入标准字符集类，指定UTF-8编码格式
import java.sql.Connection; // 导入JDBC连接接口，表示数据库连接对象
import java.sql.DriverManager; // 导入JDBC驱动管理器类，用于获取数据库连接
import java.sql.SQLException; // 导入SQL异常类，用于处理数据库操作时的异常
import java.util.Properties; // 导入属性类，用于存储键值对配置信息

import static java.util.Objects.requireNonNull; // 静态导入Objects类的requireNonNull方法，用于空值检查

/**
 * Provider of calcite connections for end-to-end tests. // 类级文档注释：这是为端到端测试提供Calcite连接的提供者类
 * 该类的主要职责是创建和配置Calcite数据库连接，专门用于测试场景
 * Calcite是一个动态数据管理框架，支持SQL查询访问多种数据源
 * 该类封装了连接创建的细节，简化了测试代码中的连接获取过程
 */
public class CalciteConnectionProvider { // 定义CalciteConnectionProvider类，提供Calcite连接的工厂类

  public static final String DRIVER_URL = "jdbc:calcite:"; // 定义Calcite JDBC驱动的URL前缀，jdbc:calcite是Calcite数据库的标准连接协议

  public Connection connection() throws IOException, SQLException { // 声明connection方法，返回一个JDBC Connection对象，可能抛出IO异常和SQL异常
    return DriverManager.getConnection(DRIVER_URL, provideConnectionInfo()); // 调用DriverManager获取连接，传入驱动URL和连接属性信息
  } // 方法结束，返回创建好的Calcite数据库连接对象

  public Properties provideConnectionInfo() throws IOException { // 声明provideConnectionInfo方法，返回Properties配置对象，可能抛出IO异常
    Properties info = new Properties(); // 创建一个新的Properties对象，用于存储连接配置信息
    info.setProperty("lex", "MYSQL"); // 设置词法分析器为MySQL模式，使Calcite支持MySQL的SQL语法特性（如反引号引用标识符）
    info.setProperty("model", "inline:" + provideSchema()); // 设置模型配置为内联模式，模型内容由provideSchema方法提供，包含表结构定义
    info.setProperty("conformance", "MYSQL_5"); // 设置SQL符合性级别为MySQL 5.0，使Calcite的SQL行为更接近MySQL 5.0标准
    return info; // 返回配置好的Properties对象，包含所有必要的连接属性
  } // 方法结束，返回连接属性配置对象

  private String provideSchema() throws IOException { // 声明provideSchema私有方法，返回包含模型定义的JSON字符串，可能抛出IO异常
    final InputStream stream = // 声明并初始化输入流对象，用于读取资源文件
        getClass().getResourceAsStream("/chinook/chinook.json"); // 从类路径中加载chinook.json资源文件，该文件包含Chinook数据库的模型定义
    requireNonNull(stream, "stream"); // 检查输入流是否为null，如果为null则抛出NullPointerException，确保资源文件存在
    return CharStreams.toString(new InputStreamReader(stream, StandardCharsets.UTF_8)); // 使用Guava的CharStreams工具将输入流转换为UTF-8编码的字符串并返回
  } // 方法结束，返回包含模型定义的JSON字符串

} // 类定义结束，CalciteConnectionProvider类的右花括号
