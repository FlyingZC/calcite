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
package org.apache.calcite.examples.foodmart.java; // 包声明，指定类所在的包路径

import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入反射模式适配器类，用于将Java对象映射为Calcite Schema
import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接类，是JDBC连接的扩展
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，用于操作和管理Schema

import java.sql.Connection; // 导入JDBC连接接口
import java.sql.DriverManager; // 导入JDBC驱动管理器，用于获取数据库连接
import java.sql.ResultSet; // 导入结果集接口，用于执行SQL查询后获取结果
import java.sql.SQLException; // 导入SQL异常类，处理数据库操作异常
import java.sql.Statement; // 导入Statement接口，用于执行SQL语句

/**
 * Example of using Calcite via JDBC. // 这是一个演示如何通过JDBC使用Calcite框架的示例类
 *
 * <p>Schema is specified programmatically. // Schema是通过编程方式指定的，而不是通过配置文件
 */
public class JdbcExample { // 类定义：JdbcExample类，演示Calcite的JDBC使用方式
  public static void main(String[] args) throws Exception { // 主方法：程序入口点，抛出异常到上层处理
    new JdbcExample().run(); // 创建JdbcExample实例并调用run方法执行演示
  }

  public void run() throws ClassNotFoundException, SQLException { // run方法：执行JDBC连接和查询操作，可能抛出类未找到异常和SQL异常
    Class.forName("org.apache.calcite.jdbc.Driver"); // 加载Calcite JDBC驱动类，注册驱动到DriverManager
    Connection connection = // 声明JDBC连接对象
        DriverManager.getConnection("jdbc:calcite:"); // 通过DriverManager获取Calcite连接，使用内存数据库模式
    CalciteConnection calciteConnection = // 声明Calcite连接对象
        connection.unwrap(CalciteConnection.class); // 将标准JDBC连接解包为CalciteConnection，获取Calcite特有功能
    SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根Schema对象，所有Schema的父容器
    rootSchema.add("hr", new ReflectiveSchema(new Hr())); // 在根Schema下添加名为"hr"的Schema，使用反射模式包装Hr对象
    rootSchema.add("foodmart", new ReflectiveSchema(new Foodmart())); // 在根Schema下添加名为"foodmart"的Schema，使用反射模式包装Foodmart对象
    Statement statement = connection.createStatement(); // 创建Statement对象，用于执行SQL语句
    ResultSet resultSet = // 声明结果集对象
        statement.executeQuery("select *\n" // 执行SQL查询：选择所有列
            + "from \"foodmart\".\"sales_fact_1997\" as s\n" // 从foodmart schema的sales_fact_1997表，别名为s
            + "join \"hr\".\"emps\" as e\n" // 与hr schema的emps表进行连接，别名为e
            + "on e.\"empid\" = s.\"cust_id\""); // 连接条件：员工ID等于客户ID
    final StringBuilder buf = new StringBuilder(); // 创建字符串构建器，用于拼接查询结果
    while (resultSet.next()) { // 遍历结果集的每一行
      int n = resultSet.getMetaData().getColumnCount(); // 获取结果集的列数
      for (int i = 1; i <= n; i++) { // 遍历每一列（列索引从1开始）
        buf.append(i > 1 ? "; " : "") // 如果不是第一列，添加分隔符"; "
            .append(resultSet.getMetaData().getColumnLabel(i)) // 添加列名（列标签）
            .append("=") // 添加等号
            .append(resultSet.getObject(i)); // 添加列值（作为对象获取）
      }
      System.out.println(buf.toString()); // 输出拼接好的结果行到控制台
      buf.setLength(0); // 清空字符串构建器，准备处理下一行
    }
    resultSet.close(); // 关闭结果集，释放资源
    statement.close(); // 关闭Statement，释放资源
    connection.close(); // 关闭数据库连接，释放资源
  }

  /** Object that will be used via reflection to create the "hr" schema. */ // 内部静态类注释：通过反射创建"hr" schema的对象
  public static class Hr { // Hr类：表示人力资源schema，包含员工数据
    public final Employee[] emps = { // emps数组：员工表数据，使用反射映射为表
        new Employee(100, "Bill"), // 创建员工对象：ID为100，姓名为Bill
        new Employee(200, "Eric"), // 创建员工对象：ID为200，姓名为Eric
        new Employee(150, "Sebastian"), // 创建员工对象：ID为150，姓名为Sebastian
    };
  }

  /** Object that will be used via reflection to create the "emps" table. */ // 内部静态类注释：通过反射创建"emps"表的对象
  public static class Employee { // Employee类：表示员工实体，映射为表的一行
    public final int empid; // empid字段：员工ID，对应表的列
    public final String name; // name字段：员工姓名，对应表的列

    public Employee(int empid, String name) { // 构造方法：创建Employee对象
      this.empid = empid; // 初始化员工ID
      this.name = name; // 初始化员工姓名
    }
  }

  /** Object that will be used via reflection to create the "foodmart"
   * schema. */ // 内部静态类注释：通过反射创建"foodmart" schema的对象
  public static class Foodmart { // Foodmart类：表示食品超市schema，包含销售数据
    public final SalesFact[] sales_fact_1997 = { // sales_fact_1997数组：1997年销售事实表数据
        new SalesFact(100, 10), // 创建销售事实对象：客户ID为100，产品ID为10
        new SalesFact(150, 20), // 创建销售事实对象：客户ID为150，产品ID为20
    };
  }


  /** Object that will be used via reflection to create the
   * "sales_fact_1997" fact table. */ // 内部静态类注释：通过反射创建"sales_fact_1997"事实表的对象
  public static class SalesFact { // SalesFact类：表示销售事实实体，映射为事实表的一行
    public final int cust_id; // cust_id字段：客户ID，对应表的列
    public final int prod_id; // prod_id字段：产品ID，对应表的列

    public SalesFact(int cust_id, int prod_id) { // 构造方法：创建SalesFact对象
      this.cust_id = cust_id; // 初始化客户ID
      this.prod_id = prod_id; // 初始化产品ID
    }
  }
}
