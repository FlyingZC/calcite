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
package org.apache.calcite.adapter.geode.simple; // 包声明：定义该类所在的包路径，位于org.apache.calcite.adapter.geode.simple包下

import org.slf4j.Logger; // 导入SLF4J日志接口，用于记录日志信息
import org.slf4j.LoggerFactory; // 导入SLF4J日志工厂类，用于创建Logger实例

import java.sql.Connection; // 导入JDBC Connection接口，用于建立数据库连接
import java.sql.DriverManager; // 导入JDBC DriverManager类，用于管理JDBC驱动和创建连接
import java.sql.ResultSet; // 导入JDBC ResultSet接口，用于表示查询结果集
import java.sql.Statement; // 导入JDBC Statement接口，用于执行SQL语句
import java.util.Properties; // 导入Java Properties类，用于存储键值对配置信息

/**
 * Example of using Geode via JDBC.
 * 演示如何通过JDBC方式使用Apache Geode数据存储的示例类
 * 
 * 该类展示了Calcite框架如何通过JDBC接口查询Geode中的数据
 * Geode是一个分布式内存数据网格，Calcite可以作为SQL查询引擎访问Geode中的Region数据
 * 
 * 核心功能：
 * 1. 配置Calcite连接到Geode数据源
 * 2. 通过JDBC执行SQL查询
 * 3. 遍历并输出查询结果
 * 
 * 使用场景：
 * - 演示Calcite与Geode的集成
 * - 学习如何配置自定义Schema工厂
 * - 理解Calcite的JDBC驱动使用方式
 */
public class SimpleJdbcExample { // 类定义：SimpleJdbcExample类，提供通过JDBC访问Geode的示例实现

  protected static final Logger LOGGER = // 成员变量：日志记录器，使用protected static final修饰符，表示受保护的静态常量
      LoggerFactory.getLogger(SimpleJdbcExample.class.getName()); // 通过LoggerFactory创建Logger实例，参数为当前类的全限定名，用于记录该类的日志信息

  private SimpleJdbcExample() { // 私有构造方法：防止外部实例化该类，因为该类只提供静态main方法作为程序入口
  } // 构造方法体为空，无需任何初始化操作

  public static void main(String[] args) throws Exception { // 主方法：程序入口点，接收命令行参数，可能抛出异常

    Properties info = new Properties(); // 创建Properties对象，用于存储Calcite连接配置信息，包括模型定义等
    final String model = "inline:" // 定义内联模型字符串，使用"inline:"前缀表示模型内容直接嵌入在字符串中
        + "{\n" // 模型开始，使用JSON格式定义Calcite的schema配置
        + "  version: '1.0',\n" // 模型版本号，标识Calcite模型的版本为1.0
        + "  schemas: [\n" // schemas数组开始，定义一个或多个schema配置
        + "     {\n" // 第一个schema对象开始
        + "       type: 'custom',\n" // schema类型为custom，表示使用自定义Schema工厂
        + "       name: 'TEST',\n" // schema名称为TEST，后续SQL中可以通过"TEST"引用该schema
        + "       factory: 'org.apache.calcite.adapter.geode.simple" // 指定自定义Schema工厂类的全限定名，用于创建Schema实例
        + ".GeodeSimpleSchemaFactory',\n" // 工厂类完整名称：GeodeSimpleSchemaFactory，负责创建Geode数据源的Schema
        + "       operand: {\n" // operand对象开始，包含传递给Schema工厂的参数
        + "         locatorHost: 'localhost',\n" // Geode定位器主机地址，指定为localhost，表示本地Geode定位器
        + "         locatorPort: '10334',\n" // Geode定位器端口号，默认为10334，用于连接Geode集群
        + "         regions: 'BookMaster',\n" // 指定要访问的Geode Region名称，这里为BookMaster，相当于数据库表
        + "         pdxSerializablePackagePath: 'org.apache.calcite.adapter.geode.domain.*'\n" // PDX序列化类的包路径，用于Geode对象的序列化和反序列化
        + "       }\n" // operand对象结束
        + "     }\n" // 第一个schema对象结束
        + "  ]\n" // schemas数组结束
        + "}"; // 模型字符串结束
    info.put("model", model); // 将模型字符串放入Properties对象中，键为"model"，Calcite会读取此配置来初始化Schema

    Class.forName("org.apache.calcite.jdbc.Driver"); // 显式加载Calcite JDBC驱动类，触发驱动注册，使DriverManager能够识别jdbc:calcite:协议

    Connection connection = DriverManager.getConnection("jdbc:calcite:", info); // 通过DriverManager获取Calcite连接，URL为jdbc:calcite:，传入包含模型配置的Properties对象

    Statement statement = connection.createStatement(); // 从连接对象创建Statement，用于执行SQL查询语句

    ResultSet resultSet = statement.executeQuery("SELECT * FROM \"TEST\".\"BookMaster\""); // 执行SQL查询，从TEST schema的BookMaster表中选择所有列，返回结果集

    final StringBuilder buf = new StringBuilder(); // 创建StringBuilder对象，用于构建每行查询结果的字符串表示

    while (resultSet.next()) { // 遍历结果集，next()方法移动到下一行，如果有数据返回true，否则返回false

      int columnCount = resultSet.getMetaData().getColumnCount(); // 获取结果集的列数，通过元数据对象获取列的总数

      for (int i = 1; i <= columnCount; i++) { // 遍历每一列，从第1列开始到最后一列（JDBC列索引从1开始）

        buf.append(i > 1 ? "; " : "") // 如果不是第一列，追加分隔符"; "，否则追加空字符串
            .append(resultSet.getMetaData().getColumnLabel(i)) // 获取当前列的列名（标签），追加到buf中
            .append("=") // 追加等号，用于分隔列名和列值
            .append(resultSet.getObject(i)); // 获取当前列的值（作为Object对象），追加到buf中
      } // for循环结束，所有列的值都已追加到buf中

      LOGGER.info("Entry: " + buf.toString()); // 记录当前行的所有列值到日志，格式为"Entry: 列名1=值1; 列名2=值2; ..."

      buf.setLength(0); // 清空StringBuilder内容，准备处理下一行数据
    } // while循环结束，结果集的所有行都已处理完毕

    resultSet.close(); // 关闭ResultSet对象，释放数据库资源
    statement.close(); // 关闭Statement对象，释放数据库资源
    connection.close(); // 关闭Connection对象，断开与Calcite的连接
  } // main方法结束
} // 类定义结束
