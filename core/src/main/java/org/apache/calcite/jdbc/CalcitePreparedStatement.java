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
 */ // Apache 许可证声明，规定代码的使用条件和限制
package org.apache.calcite.jdbc; // 声明包名，该类属于 org.apache.calcite.jdbc 包，是 Calcite JDBC 驱动的核心包

import org.apache.calcite.avatica.AvaticaPreparedStatement; // 导入 AvaticaPreparedStatement，这是 Avatica 框架中的 PreparedStatement 基类，Calcite 继承自 Avatica 框架
import org.apache.calcite.avatica.Meta; // 导入 Meta 接口，定义了 Avatica 的元数据操作接口，用于处理语句句柄和签名等

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Nullable 注解，用于标记参数可以为 null，帮助进行空值检查

import java.sql.SQLException; // 导入 SQLException，表示数据库操作过程中可能发生的异常

/**
 * Implementation of {@link java.sql.PreparedStatement}
 * for the Calcite engine.
 */ // 类注释：这是 Calcite 引擎的 PreparedStatement 接口实现类
/**
 * <p>This class has sub-classes which implement JDBC 3.0 and JDBC 4.0 APIs;
 * it is instantiated using
 * {@link org.apache.calcite.avatica.AvaticaFactory#newPreparedStatement}.
 */ // 详细说明：此类有子类实现 JDBC 3.0 和 JDBC 4.0 API；通过 AvaticaFactory 的 newPreparedStatement 方法实例化
abstract class CalcitePreparedStatement extends AvaticaPreparedStatement { // 声明抽象类 CalcitePreparedStatement，继承自 AvaticaPreparedStatement，表示这是一个预编译语句的基类
  /**
   * Creates a CalcitePreparedStatement.
   *
   * @param connection Connection
   * @param h Statement handle
   * @param signature Result of preparing statement
   * @param resultSetType Result set type
   * @param resultSetConcurrency Result set concurrency
   * @param resultSetHoldability Result set holdability
   * @throws SQLException if database error occurs
   */ // 构造方法文档注释：创建 CalcitePreparedStatement 实例
  protected CalcitePreparedStatement(CalciteConnectionImpl connection, // 构造方法参数：connection - Calcite 连接对象，表示数据库连接
      Meta.@Nullable StatementHandle h, // 构造方法参数：h - 语句句柄（StatementHandle），用于标识和管理预编译语句，可为 null
      Meta.Signature signature, // 构造方法参数：signature - 预编译语句的签名，包含语句的元数据信息如参数类型、列信息等
      int resultSetType, // 构造方法参数：resultSetType - 结果集类型（如 TYPE_FORWARD_ONLY, TYPE_SCROLL_INSENSITIVE 等）
      int resultSetConcurrency, // 构造方法参数：resultSetConcurrency - 结果集并发类型（如 CONCUR_READ_ONLY, CONCUR_UPDATABLE）
      int resultSetHoldability) throws SQLException { // 构造方法参数：resultSetHoldability - 结果集保持性（如 HOLD_CURSORS_OVER_COMMIT, CLOSE_CURSORS_AT_COMMIT）；可能抛出 SQLException 异常
    super(connection, h, signature, resultSetType, resultSetConcurrency, // 调用父类 AvaticaPreparedStatement 的构造方法，初始化父类的所有属性
        resultSetHoldability); // 传递结果集保持性参数给父类构造方法
  } // 构造方法结束

  @Override public CalciteConnectionImpl getConnection() throws SQLException { // 重写父类的 getConnection 方法，返回 CalciteConnectionImpl 类型的连接对象；可能抛出 SQLException
    return (CalciteConnectionImpl) super.getConnection(); // 调用父类的 getConnection 方法获取连接，并强制转换为 CalciteConnectionImpl 类型返回
  } // getConnection 方法结束
} // CalcitePreparedStatement 类结束
