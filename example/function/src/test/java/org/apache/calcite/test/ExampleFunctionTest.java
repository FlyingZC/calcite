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
 */ // Apache许可证声明，说明代码的开源许可协议
package org.apache.calcite.test; // 声明当前类所在的包为org.apache.calcite.test，这是一个测试包

import org.apache.calcite.example.maze.MazeTable; // 导入MazeTable类，这是一个迷宫表格的实现类，用于生成和解决迷宫
import org.apache.calcite.jdbc.CalciteConnection; // 导入CalciteConnection接口，这是Calcite JDBC驱动的连接接口，提供了访问Calcite特定功能的能力
import org.apache.calcite.linq4j.tree.Types; // 导入Types工具类，用于反射操作，特别是查找方法
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，代表Calcite中的模式（schema），可以包含表、函数等对象
import org.apache.calcite.schema.TableFunction; // 导入TableFunction接口，代表表函数，即返回表格结果的函数
import org.apache.calcite.schema.impl.AbstractSchema; // 导入AbstractSchema抽象类，提供了Schema的基础实现
import org.apache.calcite.schema.impl.TableFunctionImpl; // 导入TableFunctionImpl类，TableFunction接口的实现类

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.lang.reflect.Method; // 导入Method类，用于反射操作，代表类的方法
import java.sql.Connection; // 导入Connection接口，代表数据库连接
import java.sql.DriverManager; // 导入DriverManager类，用于管理JDBC驱动程序并建立数据库连接
import java.sql.ResultSet; // 导入ResultSet接口，代表数据库查询结果集
import java.sql.SQLException; // 导入SQLException类，表示数据库操作过程中发生的异常

import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具，用于编写更具可读性的断言
import static org.hamcrest.Matchers.hasToString; // 导入hasToString匹配器，用于验证对象的字符串表示

import static java.util.Objects.requireNonNull; // 导入requireNonNull方法，用于检查对象是否为null

/**
 * Unit tests for example user-defined functions.
 */ // 类级别的Javadoc注释：这个类用于测试示例用户定义函数
class ExampleFunctionTest { // 定义ExampleFunctionTest测试类，用于测试Calcite中的示例用户定义函数，特别是MazeTable相关的表函数
  public static final Method MAZE_METHOD = // 声明一个公共静态常量MAZE_METHOD，类型为Method，用于存储MazeTable.generate方法的反射对象
      Types.lookupMethod(MazeTable.class, "generate", int.class, int.class, // 使用Types.lookupMethod反射查找MazeTable类中名为"generate"的方法，该方法接受三个int类型参数（迷宫宽度、高度、种子）
          int.class); // 第三个int参数
  public static final Method SOLVE_METHOD = // 声明一个公共静态常量SOLVE_METHOD，类型为Method，用于存储MazeTable.solve方法的反射对象
      Types.lookupMethod(MazeTable.class, "solve", int.class, int.class, // 使用Types.lookupMethod反射查找MazeTable类中名为"solve"的方法，该方法接受三个int类型参数（迷宫宽度、高度、种子），并返回迷宫的解
          int.class); // 第三个int参数

  /** Unit test for {@link MazeTable}. */ // 方法级别的Javadoc注释：这是MazeTable的单元测试
  @Test void testMazeTableFunction() throws SQLException { // 使用@Test注解标记为测试方法，测试MazeTableFunction功能，可能抛出SQLException异常
    final String maze = "" // 定义一个字符串常量maze，用于存储期望的迷宫输出结果（不带解的迷宫）
        + "+--+--+--+--+--+\n" // 迷宫的第一行：顶部边界，由"+"和"-"组成的墙壁
        + "|        |     |\n" // 迷宫的第二行：第一行通道，左侧有7个空格，中间有墙壁"|"，右侧有5个空格
        + "+--+  +--+--+  +\n" // 迷宫的第三行：中间边界，有多个墙壁和通道
        + "|     |  |     |\n" // 迷宫的第四行：第二行通道，有多个墙壁分隔的通道
        + "+  +--+  +--+  +\n" // 迷宫的第五行：中间边界，有墙壁和通道的组合
        + "|              |\n" // 迷宫的第六行：第三行通道，完全开放的通道，没有内部墙壁
        + "+--+--+--+--+--+\n"; // 迷宫的第七行：底部边界，由"+"和"-"组成的墙壁
    checkMazeTableFunction(false, maze); // 调用checkMazeTableFunction方法验证迷宫生成功能，传入false表示不需要解，传入期望的迷宫字符串
  } // 方法结束

  /** Unit test for {@link MazeTable}. */ // 方法级别的Javadoc注释：这是MazeTable的单元测试
  @Test void testMazeTableFunctionWithSolution() throws SQLException { // 使用@Test注解标记为测试方法，测试带解的MazeTableFunction功能，可能抛出SQLException异常
    final String maze = "" // 定义一个字符串常量maze，用于存储期望的迷宫输出结果（带解的迷宫）
        + "+--+--+--+--+--+\n" // 迷宫的第一行：顶部边界
        + "|*  *    |     |\n" // 迷宫的第二行：第一行通道，"*"表示路径的一部分
        + "+--+  +--+--+  +\n" // 迷宫的第三行：中间边界
        + "|*  * |  |     |\n" // 迷宫的第四行：第二行通道，"*"表示路径
        + "+  +--+  +--+  +\n" // 迷宫的第五行：中间边界
        + "|*  *  *  *  * |\n" // 迷宫的第六行：第三行通道，"*"表示完整的解路径
        + "+--+--+--+--+--+\n"; // 迷宫的第七行：底部边界
    checkMazeTableFunction(true, maze); // 调用checkMazeTableFunction方法验证迷宫求解功能，传入true表示需要解，传入期望的带解迷宫字符串
  } // 方法结束

  public void checkMazeTableFunction(Boolean solution, String maze) // 定义公共方法checkMazeTableFunction，用于检查迷宫表函数的输出是否符合预期，接收一个Boolean参数solution表示是否需要解，以及一个String参数maze表示期望的迷宫字符串
      throws SQLException { // 方法可能抛出SQLException异常
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 使用DriverManager建立到Calcite的JDBC连接，连接字符串为"jdbc:calcite:"表示使用内存中的Calcite连接
    CalciteConnection calciteConnection = // 声明CalciteConnection类型的变量calciteConnection
        connection.unwrap(CalciteConnection.class); // 通过unwrap方法将通用的Connection对象转换为CalciteConnection对象，以便访问Calcite特定的功能
    SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取Calcite连接的根Schema，这是所有其他Schema的父容器
    SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根Schema中添加一个名为"s"的新Schema，使用AbstractSchema的实例作为Schema实现
    final TableFunction table = // 声明一个TableFunction类型的变量table
        requireNonNull(TableFunctionImpl.create(MAZE_METHOD)); // 使用TableFunctionImpl.create方法创建表函数，传入MAZE_METHOD作为参数，requireNonNull确保创建的表函数不为null
    schema.add("Maze", table); // 将创建的Maze表函数添加到名为"s"的Schema中，函数名为"Maze"
    final TableFunction table2 = // 声明一个TableFunction类型的变量table2
        requireNonNull(TableFunctionImpl.create(SOLVE_METHOD)); // 使用TableFunctionImpl.create方法创建表函数，传入SOLVE_METHOD作为参数，requireNonNull确保创建的表函数不为null
    schema.add("Solve", table2); // 将创建的Solve表函数添加到名为"s"的Schema中，函数名为"Solve"
    final String sql; // 声明一个String类型的变量sql，用于存储要执行的SQL查询语句
    if (solution) { // 如果solution参数为true，表示需要生成带解的迷宫
      sql = "select *\n" // 构建SQL查询语句，选择所有列
          + "from table(\"s\".\"Solve\"(5, 3, 1)) as t(s)"; // 从"s"Schema的"Solve"表函数中查询，传入参数(5, 3, 1)分别表示迷宫宽度、高度和种子，将结果表别名为t，列别名为s
    } else { // 如果solution参数为false，表示只需要生成迷宫不需要解
      sql = "select *\n" // 构建SQL查询语句，选择所有列
          + "from table(\"s\".\"Maze\"(5, 3, 1)) as t(s)"; // 从"s"Schema的"Maze"表函数中查询，传入参数(5, 3, 1)分别表示迷宫宽度、高度和种子，将结果表别名为t，列别名为s
    } // if-else结束
    ResultSet resultSet = connection.createStatement().executeQuery(sql); // 创建Statement对象并执行SQL查询，返回ResultSet结果集
    final StringBuilder b = new StringBuilder(); // 创建StringBuilder对象b，用于构建实际的迷宫输出字符串
    while (resultSet.next()) { // 遍历ResultSet结果集的每一行
      b.append(resultSet.getString(1)).append("\n"); // 获取当前行第一列的字符串值（迷宫的一行），添加到StringBuilder中，并追加换行符
    } // while循环结束
    assertThat(b, hasToString(maze)); // 使用Hamcrest断言验证StringBuilder的内容是否与期望的迷宫字符串完全匹配
  } // 方法结束
} // 类结束
