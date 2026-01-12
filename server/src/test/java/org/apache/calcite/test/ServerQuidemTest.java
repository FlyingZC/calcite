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
// Apache许可证头，声明版权和授权信息
// Apache Calcite是一个动态数据管理框架，提供SQL解析、优化、执行等功能
package org.apache.calcite.test; // 包声明，该类属于org.apache.calcite.test测试包

import org.apache.calcite.materialize.MaterializationService; // 导入物化服务类，用于管理物化视图

import net.hydromatic.quidem.Quidem; // 导入Quidem测试框架，用于执行SQL测试脚本

import org.junit.jupiter.api.BeforeEach; // 导入JUnit5注解，表示在每个测试方法执行前运行

import java.sql.Connection; // 导入JDBC连接接口
import java.util.Collection; // 导入集合接口

/**
 * Unit tests for server and DDL.
 */
// 类注释：ServerQuidemTest类，用于测试Calcite服务器和DDL（数据定义语言）功能
// 该类继承自QuidemTest基类，使用Quidem框架来执行SQL测试脚本
// Quidem是一种基于SQL的测试框架，允许编写包含SQL语句、预期结果和验证逻辑的测试脚本
// 主要测试内容包括：CREATE/DROP SCHEMA、CREATE/DROP TABLE、CREATE/DROP TYPE等DDL语句
// 继承关系：ServerQuidemTest -> QuidemTest（抽象基类）
// 测试文件位置：sql/table.iq等.iq文件，位于server模块的测试资源目录中
class ServerQuidemTest extends QuidemTest { // 定义ServerQuidemTest类，继承QuidemTest抽象基类
  /** Runs a test from the command line.
   *
   * <p>For example:
   *
   * <blockquote>
   *   <code>java ServerQuidemTest sql/table.iq</code>
   * </blockquote> */
  // main方法：允许从命令行运行单个测试
  // 参数：args是命令行参数数组，每个参数代表一个测试文件路径
  // 用途：用于在IDE外部或命令行中执行特定的Quidem测试文件
  // 示例：java ServerQuidemTest sql/table.iq 将执行sql/table.iq测试文件
  public static void main(String[] args) throws Exception { // 声明静态main方法，抛出异常
    for (String arg : args) { // 遍历命令行参数，每个参数是一个测试文件路径
      new ServerQuidemTest().test(arg); // 创建ServerQuidemTest实例并调用test方法执行测试
    }
  }

  @BeforeEach // JUnit5注解，表示该方法在每个测试方法执行前运行
  public void setup() { // setup方法：测试前的初始化设置
    MaterializationService.setThreadLocal(); // 设置物化服务为线程本地模式，确保每个测试线程有独立的物化服务实例
    // 物化服务用于管理物化视图的创建、刷新和查询优化
    // 线程本地模式可以避免多线程测试时的状态污染
  }

  /** For {@link QuidemTest#test(String)} parameters. */
  // getPath方法：重写父类QuidemTest的抽象方法
  // 返回值：Collection<String>类型，包含所有测试文件的路径集合
  // 作用：为参数化测试提供测试数据源，JUnit5会使用这些路径作为test方法的参数
  // 工作原理：
  // 1. 指定一个已存在的测试文件"sql/table.iq"作为起始点
  // 2. 通过data方法查找该文件所在目录
  // 3. 列出该目录下所有.iq文件（Quidem测试文件）
  // 4. 返回这些文件的路径集合
  // .iq文件格式：包含SQL语句、注释和预期输出结果的测试脚本
  @Override protected Collection<String> getPath() { // 重写父类的getPath方法
    // Start with a test file we know exists, then find the directory and list
    // its files.
    // 注释说明：从一个已知存在的测试文件开始，然后找到目录并列出其中的文件
    final String first = "sql/table.iq"; // 定义起始测试文件路径，该文件位于server模块的测试资源目录中
    return data(first); // 调用父类的data方法，根据first文件路径查找所在目录并返回所有.iq文件的路径集合
    // data方法实现逻辑（在父类QuidemTest中）：
    // 1. 通过类加载器获取first文件的完整URL路径
    // 2. 提取文件所在目录
    // 3. 使用文件过滤器查找所有.iq文件
    // 4. 返回相对路径集合
  }

  @Override protected Quidem.ConnectionFactory createConnectionFactory() { // 重写父类的createConnectionFactory方法
    // 返回值：Quidem.ConnectionFactory接口实现，用于创建数据库连接
    // 作用：为Quidem测试框架提供自定义的连接工厂，根据测试脚本中的连接名称创建相应的数据库连接
    // 工作原理：
    // 1. 创建匿名内部类实现QuidemConnectionFactory
    // 2. 重写connect方法，根据name参数返回不同的连接
    // 3. 当name为"server"时，返回ServerTest.connect()创建的连接
    // 4. 其他情况调用父类的默认连接工厂
    return new QuidemConnectionFactory() { // 创建QuidemConnectionFactory匿名内部类实例
      @Override public Connection connect(String name, boolean reference) // 重写connect方法
          throws Exception { // 声明可能抛出异常
        switch (name) { // 根据连接名称进行分支判断
        case "server": // 当连接名称为"server"时
          return ServerTest.connect(); // 调用ServerTest的静态connect方法创建连接
          // ServerTest.connect()返回的是一个配置了DDL执行器的Calcite连接
          // 该连接支持CREATE/DROP SCHEMA、CREATE/DROP TABLE等DDL操作
          // 连接配置包括：使用ServerDdlExecutor作为解析器工厂、启用物化视图、支持Oracle函数等
        }
        return super.connect(name, reference); // 对于其他连接名称，调用父类的默认连接工厂
        // 父类QuidemConnectionFactory支持多种测试连接：hr、foodmart、scott、geo等
        // 这些连接对应不同的测试模式和数据集
      }
    };
  }
} // 类结束
