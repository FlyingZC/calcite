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
package org.apache.calcite.jdbc; // 指定当前类所属的包，位于 org.apache.calcite.jdbc 包下，这是 Calcite JDBC 模块的根包

import java.sql.SQLException; // 导入标准 JDBC API 中的 SQLException 类，因为 SqlTimeoutException 需要继承它

/**
 * Indicates that an operation timed out. This is not an error; you can
 * retry the operation.
 * 表示某个操作发生了超时。这不是一个真正的错误，你可以重试该操作。
 * 
 * 【类的作用详解】
 * SqlTimeoutException 是 Calcite 框架中用于表示 SQL 操作超时的异常类。
 * 
 * 【为什么需要这个异常类？】
 * 1. 在执行 SQL 查询时，可能会因为查询时间过长而超时，例如设置了查询超时时间
 * 2. 超时不同于真正的错误（如语法错误、权限错误），它只是表示操作在指定时间内未完成
 * 3. 应用程序可以捕获这个异常，选择重试操作或者向用户报告超时情况
 * 
 * 【继承关系】
 * - 继承自 java.sql.SQLException：遵循 JDBC 标准规范，使 Calcite 的异常能被标准的 JDBC 代码捕获
 * - SQLException 本身继承自 java.lang.Exception，所以这是一个受检异常（Checked Exception）
 * 
 * 【使用场景】
 * - 当用户通过 JDBC 执行查询时，如果设置了查询超时时间（Statement.setQueryTimeout()）
 * - 如果查询在指定时间内没有完成，Calcite 会抛出这个异常
 * - 应用程序可以捕获这个异常并决定是否重试
 * 
 * 【与其他异常的区别】
 * - SQLException：通用的 SQL 异常基类
 * - SqlTimeoutException：专门表示超时的异常，是 SQLException 的子类
 * - 与 SqlSyntaxException（语法错误）、SqlValidatorException（验证错误）等不同
 * 
 * 【异常处理建议】
 * - 捕获 SqlTimeoutException 后，可以选择：
 *   a. 重试操作（如果操作是幂等的）
 *   b. 提示用户查询时间过长，建议优化查询
 *   c. 增加超时时间后重试
 *   d. 记录日志用于监控和优化
 */
public class SqlTimeoutException // 定义公共类 SqlTimeoutException，表示 SQL 操作超时异常
    extends SQLException { // 继承 SQLException，使其成为标准的 JDBC 异常类型

  /**
   * 私有构造方法，创建一个 SqlTimeoutException 实例
   * 
   * 【构造方法的作用】
   * 创建一个新的 SqlTimeoutException 对象，用于表示 SQL 操作超时的情况。
   * 
   * 【为什么是私有的？】
   * - 这个构造方法是包私有的（默认访问权限，没有 public 修饰符）
   * - 意味着只有同一个包（org.apache.calcite.jdbc）内的类可以创建这个异常
   * - 外部代码不能直接 new SqlTimeoutException()
   * - 这样设计是因为超时异常应该由 Calcite 内部机制抛出，而不是由用户代码创建
   * 
   * 【构造方法参数】
   * 无参数，使用固定的错误信息
   * 
   * 【父类构造调用详解】
   * super("timeout", null, 0) 调用 SQLException 的构造方法：
   * 
   * 1. 第一个参数 "timeout"：
   *    - 这是异常的描述信息（reason），表示超时
   *    - 当调用 getMessage() 时会返回这个字符串
   *    - 用户可以通过异常信息了解发生了什么
   * 
   * 2. 第二个参数 null：
   *    - 这是 SQLState，表示符合 X/Open SQL 标准的状态码
   *    - null 表示没有特定的 SQLState
   *    - SQLState 是一个 5 字符的字符串，用于标准化错误类型
   *    - 例如：08001 表示连接失败，23000 表示完整性约束违反
   *    - 注释中提到 "REVIEW mb 19-Jul-05 Is there a standard SQLState?"
   *      表示当时在讨论是否有标准化的 SQLState 用于超时
   *      目前使用 null，说明没有找到合适的标准 SQLState
   * 
   * 3. 第三个参数 0：
   *    - 这是 vendorCode，厂商特定的错误代码
   *    - 0 表示没有特定的厂商错误代码
   *    - 不同的数据库厂商可以定义自己的错误代码
   *    - Calcite 作为框架，使用 0 表示这是通用的超时异常
   * 
   * 【异常创建时机】
   * - 当执行查询超时时，Calcite 的执行引擎会创建并抛出这个异常
   - 例如在 Statement.execute() 或 Statement.executeQuery() 执行时间超过设定值时
   * - 超时时间通过 Statement.setQueryTimeout(int seconds) 设置
   * 
   * 【异常传播机制】
   * 1. Calcite 检测到查询超时
   * 2. 创建 SqlTimeoutException 实例（调用此构造方法）
   * 3. 抛出异常，异常沿着调用栈向上传播
   * 4. 用户代码通过 try-catch 捕获异常
   * 
   * 【异常处理示例】
   * ```java
   * try {
   *     statement.setQueryTimeout(30); // 设置 30 秒超时
   *     resultSet = statement.executeQuery(sql);
   * } catch (SqlTimeoutException e) {
   *     // 处理超时情况
   *     System.out.println("查询超时: " + e.getMessage());
   *     // 可以选择重试或提示用户
   * }
   * ```
   */
  SqlTimeoutException() { // 私有构造方法，创建超时异常实例
    // SQLException(reason, SQLState, vendorCode) // 注释说明父类构造的参数含义
    // REVIEW mb 19-Jul-05 Is there a standard SQLState? // 历史注释，2005年7月19日由 mb 审查时质疑是否有标准的 SQLState 状态码
    super("timeout", null, 0); // 调用父类 SQLException 构造方法：错误信息为 "timeout"，SQLState 为 null（无标准状态码），厂商错误代码为 0（通用超时）
  } // 构造方法结束
} // 类定义结束
