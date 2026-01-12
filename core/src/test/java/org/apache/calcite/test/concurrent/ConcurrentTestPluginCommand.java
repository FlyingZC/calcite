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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证，允许在遵守许可证条款下自由使用
package org.apache.calcite.test.concurrent; // 定义包路径，该类位于org.apache.calcite.test.concurrent包下

// 导入Checker框架的空值检查注解，用于标记可能为null的返回值
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入JDBC的Connection接口，用于数据库连接
import java.sql.Connection;
// 导入JDBC的Statement接口，用于执行SQL语句
import java.sql.Statement;

/**
 * Used to extend functionality of mtsql. // 用于扩展mtsql（多线程SQL测试工具）功能的插件命令接口
 * 
 * 本接口是Calcite并发测试框架中的核心接口，允许开发者通过实现此接口来扩展mtsql测试工具的功能
 * mtsql是一个专门用于测试Calcite在并发环境下SQL执行的工具，通过插件机制可以添加自定义的测试命令
 * 实现此接口的类可以作为插件注册到mtsql测试框架中，在测试脚本中被调用执行自定义逻辑
 * 
 * 主要应用场景：
 * 1. 在并发测试中添加自定义的验证逻辑
 * 2. 执行特殊的SQL操作或数据库操作
 * 3. 收集和记录测试过程中的中间结果
 * 4. 模拟特定的并发场景或边界条件
 * 
 * 使用方式：
 * 1. 创建一个类实现ConcurrentTestPluginCommand接口
 * 2. 实现execute方法，在其中编写自定义逻辑
 * 3. 通过TestContext提供的方法访问数据库连接和执行SQL
 * 4. 在mtsql测试脚本中注册并调用该插件
 */
public interface ConcurrentTestPluginCommand { // 定义一个公共接口，所有插件命令都必须实现此接口

  /** Test context. */ // 测试上下文接口，为插件提供执行环境
  interface TestContext { // 定义内部接口TestContext，封装了测试执行时所需的上下文信息
    /**
     * Store a message as output for mtsql script. // 将消息存储为mtsql脚本的输出
     *
     * @param message Message to be output // 要输出的消息内容
     * 
     * 此方法允许插件将信息输出到测试脚本的结果中，用于：
     * 1. 记录测试过程中的关键信息
     * 2. 输出验证结果
     * 3. 调试和诊断问题
     * 4. 生成测试报告
     * 
     * 注意：输出的消息会被mtsql框架捕获并显示在测试结果中
     */
    void storeMessage(String message); // 声明方法：存储消息到输出，参数message是要输出的消息字符串

    /**
     * Get connection for thread. // 获取当前线程的数据库连接
     *
     * @return connection for thread // 返回当前线程使用的数据库连接对象
     * 
     * 此方法返回当前测试线程的JDBC连接对象，插件可以通过此连接：
     * 1. 执行SQL语句
     * 2. 查询数据库状态
     * 3. 执行数据库事务操作
     * 4. 验证数据库中的数据
     * 
     * 重要：每个线程都有独立的数据库连接，确保并发测试的隔离性
     * 插件应该使用此连接而不是创建新的连接，以保持测试环境的一致性
     */
    Connection getConnection(); // 声明方法：获取当前线程的数据库连接，返回Connection对象

    /**
     * Get current statement for thread, or null if none. // 获取当前线程正在执行的Statement对象，如果没有则返回null
     *
     * @return current statement for thread // 返回当前线程的Statement对象，可能为null
     * 
     * 此方法返回当前线程正在执行的JDBC Statement对象，插件可以：
     * 1. 检查当前执行的SQL语句
     * 2. 获取Statement的执行状态
     * 3. 取消正在执行的查询
     * 4. 访问Statement的元数据信息
     * 
     * 注意：如果当前线程没有正在执行的Statement，此方法返回null
     * @Nullable注解表示返回值可能为null，调用者需要进行空值检查
     */
    @Nullable Statement getCurrentStatement(); // 声明方法：获取当前线程的Statement对象，使用@Nullable注解标记可能返回null
  }

  /**
   * Implement this method to extend functionality of mtsql. // 实现此方法以扩展mtsql的功能
   *
   * @param testContext Exposed context for plugin to run in. // 插件运行时暴露的上下文对象
   * 
   * 这是插件的核心方法，所有插件都必须实现此方法。当mtsql测试脚本调用插件时，
   * 会执行此方法。插件应该在此方法中实现自定义的业务逻辑。
   * 
   * 方法参数说明：
   * - testContext：提供了测试执行所需的上下文信息，包括数据库连接、消息输出等功能
   * 
   * 典型实现模式：
   * 1. 通过testContext.getConnection()获取数据库连接
   * 2. 执行必要的SQL操作或业务逻辑
   * 3. 通过testContext.storeMessage()输出结果或状态信息
   * 4. 可选地通过testContext.getCurrentStatement()检查执行状态
   * 
   * 注意事项：
   * - 此方法可能在多个线程中并发执行，必须保证线程安全
   * - 不要长时间阻塞此方法，以免影响其他线程的执行
   * - 合理处理异常，避免影响整个测试流程
   * - 使用提供的testContext而不是自己创建新的连接
   */
  void execute(TestContext testContext); // 声明方法：执行插件命令，参数testContext为测试上下文对象
} // 接口定义结束
