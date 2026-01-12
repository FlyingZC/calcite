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
package org.apache.calcite.adapter.geode.rel; // 指定该类所在的包路径，org.apache.calcite.adapter.geode.rel表示这是Calcite框架中Geode适配器的关系模型包

import org.slf4j.Logger; // 导入SLF4J日志框架的Logger接口，用于记录日志信息
import org.slf4j.LoggerFactory; // 导入SLF4J日志框架的LoggerFactory工厂类，用于创建Logger实例

import java.sql.Connection; // 导入JDBC的Connection接口，用于建立与数据库的连接
import java.sql.DriverManager; // 导入JDBC的DriverManager类，用于管理JDBC驱动程序和创建数据库连接
import java.sql.ResultSet; // 导入JDBC的ResultSet接口，用于表示数据库查询结果集
import java.sql.ResultSetMetaData; // 导入JDBC的ResultSetMetaData接口，用于获取结果集的元数据信息（如列数、列名、列类型等）
import java.sql.Statement; // 导入JDBC的Statement接口，用于执行SQL语句
import java.util.Properties; // 导入Java的Properties类，用于存储键值对配置信息

/**
 * Example of using Geode via JDBC. // 这是一个演示如何通过JDBC方式使用Apache Geode数据库的示例类
 *
 * <p>Before using this example, you need to populate Geode, as follows: // 在使用此示例之前，需要先准备Geode测试数据，步骤如下：
 *
 * <blockquote><code> // 使用代码块格式展示需要执行的命令
 * git clone https://github.com/vlsi/calcite-test-dataset<br> // 克隆calcite-test-dataset仓库，该仓库包含了Geode测试数据集
 * cd calcite-test-dataset<br> // 进入克隆的目录
 * mvn install // 使用Maven安装项目，这会创建一个包含Geode和"bookshop"、"zips"测试数据集的虚拟机
 * </code></blockquote>
 *
 * <p>This will create a virtual machine with Geode and the "bookshop" and "zips" // 这将创建一个包含Geode和"bookshop"（书店）、"zips"（邮政编码）测试数据集的虚拟机
 * test data sets. // 测试数据集
 */
public class RelationalJdbcExample { // 定义公共类RelationalJdbcExample，这是一个关系型JDBC示例类，展示如何通过JDBC接口访问Geode数据库

  protected static final Logger LOGGER = // 声明一个受保护的静态final类型的Logger常量，用于记录日志信息
      LoggerFactory.getLogger(RelationalJdbcExample.class.getName()); // 使用LoggerFactory创建一个Logger实例，参数是当前类的全限定名，这样日志输出时会显示是哪个类产生的日志

  private RelationalJdbcExample() { // 私有构造方法，防止外部实例化该类，因为该类只通过main方法作为程序入口使用
  } // 私有构造方法结束，表示不允许创建该类的实例

  public static void main(String[] args) throws Exception { // 程序的主入口方法，静态方法，接收命令行参数数组args，声明可能抛出Exception异常

    final String geodeModelJson = // 声明一个final类型的字符串变量，用于存储Geode的模型配置JSON
        "inline:" // 使用inline:前缀表示模型配置直接内联在连接字符串中，而不是从外部文件加载
            + "{\n" // JSON配置的开始符号，使用\n换行符格式化JSON
            + "  version: '1.0',\n" // 指定模型配置的版本号为1.0，这是Calcite模型配置的版本标识
            + "  schemas: [\n" // 定义schemas数组，包含一个或多个schema（数据库模式/命名空间）配置
            + "     {\n" // schemas数组的第一个元素开始
            + "       type: 'custom',\n" // 指定schema类型为custom（自定义），表示使用自定义的SchemaFactory来创建schema
            + "       name: 'TEST',\n" // 定义schema的名称为TEST，在SQL查询中会使用这个名称作为表名的前缀，如TEST.BookMaster
            + "       factory: 'org.apache.calcite.adapter.geode.rel.GeodeSchemaFactory',\n" // 指定用于创建schema的工厂类全限定名，GeodeSchemaFactory是Calcite提供的Geode适配器Schema工厂，负责创建GeodeSchema实例
            + "       operand: {\n" // 定义传递给SchemaFactory的参数对象（operand），这些参数会被传递给GeodeSchemaFactory的create方法
            + "         locatorHost: 'localhost',\n" // Geode定位器的主机地址，指定为localhost表示Geode定位器运行在本地机器上
            + "         locatorPort: '10334',\n" // Geode定位器的端口号，默认为10334，用于客户端连接到Geode集群
            + "         regions: 'BookMaster,BookCustomer,BookInventory,BookOrder',\n" // 指定要映射的Geode区域（Region）列表，用逗号分隔，每个Region对应一个表：BookMaster（图书主表）、BookCustomer（图书客户表）、BookInventory（图书库存表）、BookOrder（图书订单表）
            + "         pdxSerializablePackagePath: 'org.apache.calcite.adapter.geode.domain.*'\n" // 指定PDX序列化类的包路径，PDX是Geode的高效序列化格式，这里指定了org.apache.calcite.adapter.geode.domain包下的所有类都使用PDX序列化
            + "       }\n" // operand对象结束
            + "     }\n" // schemas数组的第一个元素结束
            + "   ]\n" // schemas数组结束
            + "}"; // JSON配置结束符号

    Class.forName("org.apache.calcite.jdbc.Driver"); // 显式加载并注册Calcite的JDBC驱动类，这是建立JDBC连接的必要步骤，Driver类在加载时会自动注册到DriverManager

    Properties info = new Properties(); // 创建一个Properties对象，用于存储连接属性配置信息
    info.put("model", geodeModelJson); // 将Geode模型配置JSON字符串放入Properties对象，键为"model"，值为geodeModelJson，这个配置会被Calcite驱动读取并解析

    Connection connection = DriverManager.getConnection("jdbc:calcite:", info); // 通过DriverManager获取数据库连接，连接URL为"jdbc:calcite:"表示使用Calcite JDBC驱动，info参数包含模型配置，返回一个Connection对象代表与Calcite的连接

    Statement statement = connection.createStatement(); // 从Connection对象创建一个Statement对象，Statement用于执行静态SQL语句并返回结果
    String sql = "SELECT \"b\".\"author\", \"b\".\"retailCost\", \"i\".\"quantityInStock\"\n" // 定义要执行的SQL查询语句，从BookMaster表（别名b）选择author（作者）、retailCost（零售成本）字段，从BookInventory表（别名i）选择quantityInStock（库存数量）字段
        + "FROM \"TEST\".\"BookMaster\" AS \"b\" " // 指定主表为TEST schema下的BookMaster表，使用别名"b"以便在查询中引用
        + " INNER JOIN \"TEST\".\"BookInventory\" AS \"i\"" // 与TEST schema下的BookInventory表进行内连接，使用别名"i"
        + "  ON \"b\".\"itemNumber\" = \"i\".\"itemNumber\"\n " // 指定连接条件，两个表的itemNumber（项目编号）字段相等
        + "WHERE  \"b\".\"retailCost\" > 0"; // 添加过滤条件，只选择零售成本大于0的记录
    ResultSet resultSet = statement.executeQuery(sql); // 执行SQL查询并返回结果集对象ResultSet，resultSet包含查询结果的所有行

    final StringBuilder buf = new StringBuilder(); // 创建一个StringBuilder对象用于构建结果字符串，StringBuilder比String更适合频繁的字符串拼接操作
    while (resultSet.next()) { // 循环遍历结果集的每一行，resultSet.next()方法将光标移动到下一行，如果还有数据则返回true，否则返回false
      ResultSetMetaData metaData = resultSet.getMetaData(); // 获取结果集的元数据对象，元数据包含结果集的结构信息，如列数、列名、列类型等
      for (int i = 1; i <= metaData.getColumnCount(); i++) { // 遍历结果集的每一列，从第1列开始到总列数结束，getColumnCount()返回列的总数
        buf.append(i > 1 ? "; " : "") // 如果不是第一列（i > 1），则添加"; "作为分隔符，否则不添加任何内容，这样可以在多个字段值之间用分号和空格分隔
            .append(metaData.getColumnLabel(i)) // 获取当前列的列标签（列名），并追加到buf中
            .append("=") // 追加等号作为字段名和字段值之间的分隔符
            .append(resultSet.getObject(i)); // 获取当前列的值（使用getObject方法可以获取任意类型的值），并追加到buf中
      } // for循环结束，完成一行所有列的处理
      LOGGER.info("Result entry: " + buf); // 使用INFO级别记录当前行的结果信息，输出格式为"Result entry: 字段名1=值1; 字段名2=值2; ..."
      buf.setLength(0); // 清空StringBuilder的内容，准备处理下一行数据，setLength(0)将StringBuilder的长度设置为0，相当于清空
    } // while循环结束，完成所有结果行的处理
    resultSet.close(); // 关闭ResultSet对象，释放数据库资源，这是良好的资源管理实践
    statement.close(); // 关闭Statement对象，释放数据库资源
    connection.close(); // 关闭Connection对象，断开与数据库的连接，释放所有相关资源
  } // main方法结束
} // RelationalJdbcExample类定义结束
