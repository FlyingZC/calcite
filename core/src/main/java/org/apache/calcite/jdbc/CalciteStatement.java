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
// 声明包名，表示这个类属于 org.apache.calcite.jdbc 包，是 Calcite JDBC 驱动的一部分
package org.apache.calcite.jdbc;

// 导入 AvaticaStatement 类，CalciteStatement 继承自这个类，Avatica 是 Calcite 的 JDBC 框架
import org.apache.calcite.avatica.AvaticaStatement;
// 导入 Meta 接口，定义了 Avatica 的元数据操作接口
import org.apache.calcite.avatica.Meta;
// 导入 NoSuchStatementException 异常类，当找不到语句句柄时抛出
import org.apache.calcite.avatica.NoSuchStatementException;
// 导入 Queryable 接口，表示可查询的 LINQ 对象，用于执行查询操作
import org.apache.calcite.linq4j.Queryable;
// 导入 CalciteServerStatement 类，表示 Calcite 服务器端的语句对象
import org.apache.calcite.server.CalciteServerStatement;

// 导入 Nullable 注解，用于标记可能为 null 的参数
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入 SQLException 类，用于处理 SQL 相关的异常
import java.sql.SQLException;

/**
 * CalciteStatement 是 java.sql.Statement 接口的 Calcite 引擎实现类
 * 
 * 【类的作用】：
 * 这个抽象类是 Calcite JDBC 驱动中 Statement 接口的核心实现，它继承自 AvaticaStatement（Avatica 是 Calcite 的 JDBC 框架）。
 * Statement 接口是 JDBC 中用于执行静态 SQL 语句并返回其生成的结果对象的基本接口。
 * 
 * 【核心功能】：
 * 1. 作为 Calcite JDBC 语句的基类，提供了 Statement 接口在 Calcite 中的基本实现
 * 2. 管理语句的生命周期，包括创建、执行和关闭
 * 3. 提供与 Calcite 服务器端语句对象的交互能力
 * 4. 支持查询准备（prepare）操作，用于预编译 SQL 查询
 * 5. 维护语句的元数据，如结果集类型、并发性、可保持性等
 * 
 * 【继承关系】：
 * - 继承自 AvaticaStatement：复用了 Avatica 框架提供的 JDBC Statement 基础实现
 * - 抽象类：不能直接实例化，需要由子类（如 CalcitePreparedStatement）来具体实现
 * 
 * 【关键设计】：
 * - 使用语句句柄（StatementHandle）来标识和管理语句
 * - 通过 CalciteServerStatement 与服务器端进行交互
 * - 支持查询准备机制，提高重复查询的性能
 * 
 * 【使用场景】：
 * - 执行 SQL 查询语句（SELECT）
 * - 执行 SQL 更新语句（INSERT、UPDATE、DELETE）
 * - 执行 DDL 语句（CREATE、DROP、ALTER）
 * - 执行存储过程调用
 */
public abstract class CalciteStatement extends AvaticaStatement {
  /**
   * 构造方法：创建一个 CalciteStatement 实例
   * 
   * 【构造方法作用】：
   * 初始化 CalciteStatement 对象，设置连接信息、语句句柄和结果集属性
   * 
   * 【参数详细说明】：
   * @param connection - CalciteConnectionImpl 对象，表示创建此语句的数据库连接
   *                   - 作用：提供与 Calcite 数据库的连接，用于执行 SQL 语句
   *                   - 类型：CalciteConnectionImpl 是 CalciteConnection 的实现类
   *                   - 重要性：所有语句执行都需要通过连接对象进行
   * 
   * @param h - Meta.StatementHandle 对象，表示语句句柄
   *         - 作用：唯一标识这个语句对象，用于在服务器端查找和操作语句
   *         - 类型：StatementHandle 包含语句的 ID 和连接信息
   *         - 可能为 null：使用 @Nullable 注解标记，表示可以为空
   *         - 重要性：语句句柄是客户端和服务器端通信的桥梁
   * 
   * @param resultSetType - int 类型，表示结果集类型
   *                      - 作用：定义结果集的滚动和更新行为
   *                      - 可选值：
   *                        * ResultSet.TYPE_FORWARD_ONLY：只能向前滚动（默认）
   *                        * ResultSet.TYPE_SCROLL_INSENSITIVE：可滚动，但不反映数据库的变化
   *                        * ResultSet.TYPE_SCROLL_SENSITIVE：可滚动，且反映数据库的变化
   *                      - 重要性：影响结果集的遍历方式和更新能力
   * 
   * @param resultSetConcurrency - int 类型，表示结果集并发性
   *                            - 作用：定义结果集的并发模式，即是否允许更新
   *                            - 可选值：
   *                              * ResultSet.CONCUR_READ_ONLY：只读模式（默认）
   *                              * ResultSet.CONCUR_UPDATABLE：可更新模式
   *                            - 重要性：决定是否可以通过结果集修改数据库数据
   * 
   * @param resultSetHoldability - int 类型，表示结果集可保持性
   *                            - 作用：定义事务提交后结果集是否保持打开
   *                            - 可选值：
   *                              * ResultSet.HOLD_CURSORS_OVER_COMMIT：提交后保持打开（默认）
   *                              * ResultSet.CLOSE_CURSORS_AT_COMMIT：提交时关闭
   *                            - 重要性：影响事务提交后结果集的生命周期
   */
  CalciteStatement(CalciteConnectionImpl connection, Meta.@Nullable StatementHandle h,
      int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
    // 调用父类 AvaticaStatement 的构造方法，初始化父类的成员变量
    // 将连接对象、语句句柄和结果集属性传递给父类，完成基本的初始化工作
    super(connection, h, resultSetType, resultSetConcurrency,
        resultSetHoldability);
  }

  // 实现 Statement 接口的方法

  /**
   * unwrap 方法：将此对象解包为指定的接口类型
   * 
   * 【方法作用】：
   * JDBC 4.0 引入的方法，允许将 JDBC 对象（如 Statement）转换为其特定实现类
   * 当应用程序需要访问特定于驱动程序的功能时，可以使用此方法
   * 
   * 【方法签名】：
   * @param <T> - 泛型类型参数，表示要转换的目标类型
   * @param iface - Class<T> 对象，表示要解包的目标接口或类的 Class 对象
   * @return T - 返回解包后的对象，类型为 T
   * @throws SQLException - 如果解包失败，抛出 SQLException 异常
   * 
   * 【实现逻辑】：
   * 1. 检查请求的类型是否为 CalciteServerStatement.class
   * 2. 如果是，从服务器获取对应的 CalciteServerStatement 对象并返回
   * 3. 如果不是，调用父类的 unwrap 方法继续处理
   * 
   * 【使用场景】：
   * - 应用程序需要访问 Calcite 特有的功能时
   * - 需要从客户端 Statement 获取服务器端 Statement 对象时
   * - 需要访问 Calcite 内部 API 时
   */
  @Override public <T> T unwrap(Class<T> iface) throws SQLException {
    // 检查请求的类型是否为 CalciteServerStatement 类
    // CalciteServerStatement 是服务器端的语句实现，包含更多服务器端的功能
    if (iface == CalciteServerStatement.class) {
      // 声明一个 CalciteServerStatement 变量，用于存储从服务器获取的语句对象
      final CalciteServerStatement statement;
      try {
        // 从连接的服务器端获取与当前语句句柄对应的 CalciteServerStatement 对象
        // getConnection() 获取当前的 CalciteConnectionImpl 对象
        // .server 获取连接中的 CalciteServer 对象（服务器端实例）
        // .getStatement(handle) 根据语句句柄查找对应的服务器端语句
        statement = getConnection().server.getStatement(handle);
      } catch (NoSuchStatementException e) {
        // 如果找不到对应的语句，抛出 AssertionError
        // 这通常表示内部状态不一致，是编程错误而非运行时错误
        throw new AssertionError("invalid statement", e);
      }
      // 将获取到的 statement 对象转换为请求的类型 T 并返回
      // 使用 Class.cast() 方法进行类型安全的转换
      return iface.cast(statement);
    }
    // 如果请求的类型不是 CalciteServerStatement，调用父类的 unwrap 方法
    // 父类可能支持其他类型的解包操作
    return super.unwrap(iface);
  }

  /**
   * getConnection 方法：获取创建此语句的连接对象
   * 
   * 【方法作用】：
   * 返回创建此 Statement 对象的 Connection 对象
   * 覆盖了父类的方法，返回更具体的类型 CalciteConnectionImpl
   * 
   * 【方法签名】：
   * @return CalciteConnectionImpl - 返回创建此语句的 CalciteConnectionImpl 对象
   * 
   * 【实现逻辑】：
   * 1. 将父类的 connection 成员变量（类型为 AvaticaConnection）强制转换为 CalciteConnectionImpl
   * 2. 返回转换后的连接对象
   * 
   * 【使用场景】：
   * - 需要访问 Calcite 特定的连接功能时
   * - 需要获取连接的元数据或配置信息时
   * - 需要在语句执行过程中访问连接对象时
   * 
   * 【设计说明】：
   * - 返回类型是 CalciteConnectionImpl 而不是 Connection，提供更强类型的安全性
   * - 避免了调用者需要手动类型转换的麻烦
   */
  @Override public CalciteConnectionImpl getConnection() {
    // 将父类的 connection 成员变量强制转换为 CalciteConnectionImpl 类型并返回
    // connection 是从 AvaticaStatement 继承的成员变量，类型为 AvaticaConnection
    // 由于实际传入的是 CalciteConnectionImpl，所以可以安全转换
    return (CalciteConnectionImpl) connection;
  }

  /**
   * prepare 方法：准备一个可查询对象（Queryable）用于执行
   * 
   * 【方法作用】：
   * 将一个 Queryable 对象（LINQ 风格的可查询对象）准备为 Calcite 可执行的签名（Signature）
   * 这个方法用于支持 LINQ 查询，将 Queryable 转换为 Calcite 可以理解和执行的形式
   * 
   * 【方法签名】：
   * @param <T> - 泛型类型参数，表示查询结果的元素类型
   * @param queryable - Queryable<T> 对象，表示要准备的可查询对象
   *                  - 作用：封装了查询逻辑和数据源
   *                  - 类型：LINQ4J 的 Queryable 接口，提供类似 LINQ 的查询能力
   *                  - 重要性：是 Calcite 支持 LINQ 查询的关键接口
   * @return CalcitePrepare.CalciteSignature<T> - 返回准备好的查询签名
   *         - 作用：包含查询的元数据和执行信息，如字段类型、参数类型等
   *         - 类型：CalciteSignature 是 Calcite 特有的查询签名类
   *         - 重要性：签名是查询执行的基础，包含所有必要的元数据
   * 
   * 【实现逻辑】：
   * 1. 获取当前的 CalciteConnectionImpl 对象
   * 2. 从连接中获取 CalcitePrepare 工厂对象
   * 3. 从服务器端获取对应的 CalciteServerStatement 对象
   * 4. 创建准备上下文（PrepareContext）
   * 5. 使用 CalcitePrepare 对象准备 Queryable
   * 6. 返回准备好的签名对象
   * 
   * 【使用场景】：
   * - 执行 LINQ 风格的查询时
   * - 需要将 Queryable 对象转换为 SQL 查询时
   * - 需要获取查询的元数据信息时
   * 
   * 【关键概念】：
   * - Queryable：LINQ4J 的可查询接口，支持延迟执行的查询
   * - CalcitePrepare：查询准备器，负责将查询转换为可执行的形式
   * - CalciteSignature：查询签名，包含查询的完整元数据信息
   * - PrepareContext：准备上下文，提供查询准备所需的环境信息
   */
  protected <T> CalcitePrepare.CalciteSignature<T> prepare(
      Queryable<T> queryable) {
    // 获取当前的 CalciteConnectionImpl 对象
    // getConnection() 返回创建此语句的连接对象
    final CalciteConnectionImpl calciteConnection = getConnection();
    // 从连接对象中获取 CalcitePrepare 工厂对象
    // prepareFactory 是一个 Supplier<CalcitePrepare>，提供查询准备器实例
    // .get() 方法获取实际的 CalcitePrepare 对象
    final CalcitePrepare prepare = calciteConnection.prepareFactory.get();
    // 声明一个 CalciteServerStatement 变量，用于存储从服务器获取的语句对象
    final CalciteServerStatement serverStatement;
    try {
      // 从连接的服务器端获取与当前语句句柄对应的 CalciteServerStatement 对象
      // 这个对象包含了服务器端的语句状态和上下文信息
      serverStatement = calciteConnection.server.getStatement(handle);
    } catch (NoSuchStatementException e) {
      // 如果找不到对应的语句，抛出 AssertionError
      // 这通常表示内部状态不一致，是编程错误
      throw new AssertionError("invalid statement", e);
    }
    // 创建准备上下文（PrepareContext）
    // prepareContext 包含了查询准备所需的所有环境信息，如 schema、catalog 等
    final CalcitePrepare.Context prepareContext =
        serverStatement.createPrepareContext();
    // 使用 CalcitePrepare 对象准备 Queryable
    // prepareQueryable 方法将 Queryable 对象转换为 Calcite 可执行的查询签名
    // 返回的签名包含了查询的元数据、参数类型、返回类型等信息
    return prepare.prepareQueryable(prepareContext, queryable);
  }

  /**
   * close_ 方法：关闭语句对象（内部实现方法）
   * 
   * 【方法作用】：
   * 关闭此 Statement 对象，释放相关资源
   * 这是内部使用的关闭方法，以下划线结尾，表示是受保护的实现方法
   * 
   * 【方法签名】：
   * @return void - 无返回值
   * 
   * 【实现逻辑】：
   * 1. 检查语句是否已经关闭
   * 2. 如果未关闭，从服务器端移除此语句
   * 3. 调用父类的 close_ 方法完成关闭操作
   * 
   * 【使用场景】：
   * - 显式调用 Statement.close() 方法时
   * - 连接关闭时自动关闭所有语句时
   * - 语句对象被垃圾回收时（通过 finalize）
   * 
   * 【资源管理】：
   * - 从服务器端移除语句句柄，释放服务器端资源
   * - 调用父类关闭方法，释放客户端资源
   * - 设置 closed 标志为 true，防止重复关闭
   * 
   * 【设计说明】：
   * - 方法名以下划线结尾，遵循 Avatica 框架的命名约定
   * - 是 protected 方法，只能由子类或本类调用
   * - 检查 closed 标志，避免重复关闭
   */
  @Override protected void close_() {
    // 检查语句是否已经关闭
    // closed 是从 AvaticaStatement 继承的成员变量，表示语句是否已关闭
    if (!closed) {
      // 从服务器端移除此语句
      // ((CalciteConnectionImpl) connection) 获取连接对象并转换为 CalciteConnectionImpl
      // .server 获取连接中的 CalciteServer 对象
      // .removeStatement(handle) 根据语句句柄从服务器端移除语句
      // 这会释放服务器端与该语句相关的资源
      ((CalciteConnectionImpl) connection).server.removeStatement(handle);
      // 调用父类的 close_ 方法，完成客户端的关闭操作
      // 父类会设置 closed 标志为 true，并释放其他客户端资源
      super.close_();
    }
  }
}
