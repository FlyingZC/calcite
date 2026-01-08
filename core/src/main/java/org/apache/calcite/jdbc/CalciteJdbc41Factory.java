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
// Apache许可证头，声明版权和使用许可
package org.apache.calcite.jdbc; // 声明包名，属于Calcite JDBC模块

// 导入Calcite相关的类和接口
import org.apache.calcite.adapter.java.JavaTypeFactory; // Java类型工厂，用于创建和管理Java类型
import org.apache.calcite.avatica.AvaticaConnection; // Avatica连接基类，Calcite基于Avatica框架
import org.apache.calcite.avatica.AvaticaDatabaseMetaData; // Avatica数据库元数据基类
import org.apache.calcite.avatica.AvaticaFactory; // Avatica工厂接口，用于创建各种JDBC对象
import org.apache.calcite.avatica.AvaticaPreparedStatement; // Avatica预处理语句基类
import org.apache.calcite.avatica.AvaticaResultSetMetaData; // Avatica结果集元数据基类
import org.apache.calcite.avatica.AvaticaStatement; // Avatica语句基类
import org.apache.calcite.avatica.Meta; // Avatica元数据接口，定义了SQL执行相关的元数据结构
import org.apache.calcite.avatica.QueryState; // 查询状态，表示查询的执行状态
import org.apache.calcite.avatica.UnregisteredDriver; // Avatica未注册驱动基类

// 导入空值检查框架的注解
import org.checkerframework.checker.nullness.qual.Nullable; // 用于标记可能为null的参数或返回值

// 导入Java IO和SQL相关的类
import java.io.InputStream; // 输入流，用于读取二进制数据
import java.io.Reader; // 字符读取器，用于读取文本数据
import java.sql.NClob; // 国家字符大对象，用于存储大文本数据
import java.sql.ResultSetMetaData; // 结果集元数据接口
import java.sql.RowId; // 行ID，用于唯一标识数据库中的行
import java.sql.SQLException; // SQL异常
import java.sql.SQLXML; // XML数据类型
import java.util.Properties; // 属性集合，用于存储连接属性
import java.util.TimeZone; // 时区，用于处理时间相关的数据

/**
 * Implementation of {@link org.apache.calcite.avatica.AvaticaFactory}
 * for Calcite and JDBC 4.1 (corresponds to JDK 1.7).
 * 这是AvaticaFactory接口的Calcite实现，专门用于JDBC 4.1版本（对应JDK 1.7）
 * 工厂模式：负责创建Calcite JDBC API中的各种核心对象，如Connection、Statement、PreparedStatement等
 * JDBC 4.1引入了新的特性，如try-with-resources、RowId支持、NClob支持等
 * 这个类是Calcite与JDBC API之间的桥梁，确保Calcite能够作为JDBC驱动使用
 */
@SuppressWarnings("UnusedDeclaration") // 抑制未使用声明警告，因为某些方法可能由反射调用
public class CalciteJdbc41Factory extends CalciteFactory { // 继承CalciteFactory，Calcite工厂基类
  /** Creates a factory for JDBC version 4.1. */
  // 无参构造函数：创建一个JDBC 4.1版本的工厂
  public CalciteJdbc41Factory() { // 构造函数
    this(4, 1); // 调用带参构造函数，传入主版本号4和次版本号1，代表JDBC 4.1
  }

  /** Creates a JDBC factory with given major/minor version number. */
  // 带参构造函数：根据给定的主次版本号创建JDBC工厂
  // 参数major：JDBC主版本号（如4代表JDBC 4.x）
  // 参数minor：JDBC次版本号（如1代表JDBC 4.1）
  protected CalciteJdbc41Factory(int major, int minor) { // 受保护的构造函数，允许子类自定义版本号
    super(major, minor); // 调用父类CalciteFactory的构造函数，初始化版本号
  }

  // 创建新的数据库连接对象
  // 参数driver：未注册的驱动实例，实际类型是Calcite的Driver
  // 参数factory：Avatica工厂实例，用于创建其他JDBC对象
  // 参数url：连接URL，指定数据库连接字符串
  // 参数info：连接属性，包含用户名、密码等连接配置信息
  // 参数rootSchema：Calcite的根Schema，可能为null，表示数据库的schema结构
  // 参数typeFactory：Java类型工厂，可能为null，用于创建和管理Java类型
  // 返回：CalciteJdbc41Connection实例，实现了JDBC 4.1的Connection接口
  @Override public CalciteJdbc41Connection newConnection(UnregisteredDriver driver,
      AvaticaFactory factory, String url, Properties info,
      @Nullable CalciteSchema rootSchema, @Nullable JavaTypeFactory typeFactory) {
    return new CalciteJdbc41Connection( // 创建并返回JDBC 4.1连接实例
        (Driver) driver, factory, url, info, rootSchema, typeFactory); // 将driver强转为Calcite的Driver类型并传递所有参数
  }

  // 创建新的数据库元数据对象
  // 参数connection：Avatica连接实例，实际类型是CalciteConnectionImpl
  // 返回：CalciteJdbc41DatabaseMetaData实例，用于获取数据库的元数据信息（表、列、索引等）
  @Override public CalciteJdbc41DatabaseMetaData newDatabaseMetaData(
      AvaticaConnection connection) {
    return new CalciteJdbc41DatabaseMetaData( // 创建并返回JDBC 4.1数据库元数据实例
        (CalciteConnectionImpl) connection); // 将connection强转为CalciteConnectionImpl类型
  }

  // 创建新的语句对象（Statement）
  // 参数connection：Avatica连接实例，实际类型是CalciteConnectionImpl
  // 参数h：语句句柄，可能为null，用于标识和管理语句
  // 参数resultSetType：结果集类型，如TYPE_FORWARD_ONLY、TYPE_SCROLL_INSENSITIVE等
  // 参数resultSetConcurrency：结果集并发类型，如CONCUR_READ_ONLY、CONCUR_UPDATABLE
  // 参数resultSetHoldability：结果集可保持性，如HOLD_CURSORS_OVER_COMMIT、CLOSE_CURSORS_AT_COMMIT
  // 返回：CalciteJdbc41Statement实例，用于执行静态SQL语句
  @Override public CalciteJdbc41Statement newStatement(AvaticaConnection connection,
      Meta.@Nullable StatementHandle h,
      int resultSetType,
      int resultSetConcurrency,
      int resultSetHoldability) {
    return new CalciteJdbc41Statement( // 创建并返回JDBC 4.1语句实例
        (CalciteConnectionImpl) connection, // 将connection强转为CalciteConnectionImpl类型
        h, // 传递语句句柄
        resultSetType, resultSetConcurrency, // 传递结果集类型和并发类型
        resultSetHoldability); // 传递结果集可保持性
  }

  // 创建新的预处理语句对象（PreparedStatement）
  // 参数connection：Avatica连接实例，实际类型是CalciteConnectionImpl
  // 参数h：语句句柄，可能为null，用于标识和管理语句
  // 参数signature：语句签名，包含SQL语句的元数据信息（参数类型、返回列类型等）
  // 参数resultSetType：结果集类型
  // 参数resultSetConcurrency：结果集并发类型
  // 参数resultSetHoldability：结果集可保持性
  // 返回：CalciteJdbc41PreparedStatement实例，用于执行带参数的SQL语句
  @Override public AvaticaPreparedStatement newPreparedStatement(
      AvaticaConnection connection,
      Meta.@Nullable StatementHandle h,
      Meta.Signature signature,
      int resultSetType,
      int resultSetConcurrency,
      int resultSetHoldability) throws SQLException {
    return new CalciteJdbc41PreparedStatement( // 创建并返回JDBC 4.1预处理语句实例
        (CalciteConnectionImpl) connection, h, // 将connection强转为CalciteConnectionImpl类型并传递句柄
        (CalcitePrepare.CalciteSignature) signature, resultSetType, // 将signature强转为CalciteSignature类型并传递结果集类型
        resultSetConcurrency, resultSetHoldability); // 传递结果集并发类型和可保持性
  }

  // 创建新的结果集对象（ResultSet）
  // 参数statement：语句对象，包含执行的SQL语句信息
  // 参数state：查询状态，表示查询的当前执行状态
  // 参数signature：语句签名，包含结果集的元数据信息
  // 参数timeZone：时区，用于处理时间相关的数据
  // 参数firstFrame：第一帧数据，包含第一批结果数据
  // 返回：CalciteResultSet实例，用于遍历查询结果
  @Override public CalciteResultSet newResultSet(AvaticaStatement statement, QueryState state,
      Meta.Signature signature, TimeZone timeZone, Meta.Frame firstFrame)
      throws SQLException {
    final ResultSetMetaData metaData = // 声明结果集元数据变量
        newResultSetMetaData(statement, signature); // 创建结果集元数据
    final CalcitePrepare.CalciteSignature calciteSignature = // 声明Calcite签名变量
        (CalcitePrepare.CalciteSignature) signature; // 将signature强转为CalciteSignature类型
    return new CalciteResultSet(statement, calciteSignature, metaData, timeZone, // 创建并返回Calcite结果集实例
        firstFrame); // 传递第一帧数据
  }

  // 创建新的结果集元数据对象（ResultSetMetaData）
  // 参数statement：语句对象
  // 参数signature：语句签名，包含列的元数据信息（列名、类型、精度等）
  // 返回：AvaticaResultSetMetaData实例，用于获取结果集的列信息
  @Override public ResultSetMetaData newResultSetMetaData(AvaticaStatement statement,
      Meta.Signature signature) {
    return new AvaticaResultSetMetaData(statement, null, signature); // 创建并返回Avatica结果集元数据实例，第二个参数为null表示没有catalog
  }

  /** Implementation of connection for JDBC 4.1. */
  // JDBC 4.1连接实现类，继承自CalciteConnectionImpl
  // 这是一个静态内部类，专门用于实现JDBC 4.1规范的连接功能
  // 负责管理数据库连接、事务、schema等连接级别的功能
  private static class CalciteJdbc41Connection extends CalciteConnectionImpl { // 私有静态内部类，继承CalciteConnectionImpl
    // 构造函数：创建JDBC 4.1连接实例
    // 参数driver：Calcite驱动实例
    // 参数factory：Avatica工厂实例
    // 参数url：连接URL
    // 参数info：连接属性
    // 参数rootSchema：根Schema，可能为null
    // 参数typeFactory：Java类型工厂，可能为null
    CalciteJdbc41Connection(Driver driver, AvaticaFactory factory, String url,
        Properties info, @Nullable CalciteSchema rootSchema,
        @Nullable JavaTypeFactory typeFactory) {
      super(driver, factory, url, info, rootSchema, typeFactory); // 调用父类CalciteConnectionImpl的构造函数，初始化连接
    }
  }

  /** Implementation of statement for JDBC 4.1. */
  // JDBC 4.1语句实现类，继承自CalciteStatement
  // 这是一个静态内部类，专门用于实现JDBC 4.1规范的语句功能
  // 负责执行静态SQL语句并管理结果集
  private static class CalciteJdbc41Statement extends CalciteStatement { // 私有静态内部类，继承CalciteStatement
    // 构造函数：创建JDBC 4.1语句实例
    // 参数connection：Calcite连接实例
    // 参数h：语句句柄，可能为null
    // 参数resultSetType：结果集类型
    // 参数resultSetConcurrency：结果集并发类型
    // 参数resultSetHoldability：结果集可保持性
    CalciteJdbc41Statement(CalciteConnectionImpl connection,
        Meta.@Nullable StatementHandle h, int resultSetType, int resultSetConcurrency,
        int resultSetHoldability) {
      super(connection, h, resultSetType, resultSetConcurrency, // 调用父类CalciteStatement的构造函数，初始化语句
          resultSetHoldability); // 传递结果集可保持性
    }
  }

  /** Implementation of prepared statement for JDBC 4.1. */
  // JDBC 4.1预处理语句实现类，继承自CalcitePreparedStatement
  // 这是一个静态内部类，专门用于实现JDBC 4.1规范的预处理语句功能
  // 负责执行带参数的SQL语句，提供参数绑定和类型转换功能
  // JDBC 4.1新增了对NClob、SQLXML、RowId等数据类型的支持
  private static class CalciteJdbc41PreparedStatement // 私有静态内部类，继承CalcitePreparedStatement
      extends CalcitePreparedStatement {
    // 构造函数：创建JDBC 4.1预处理语句实例
    // 参数connection：Calcite连接实例
    // 参数h：语句句柄，可能为null
    // 参数signature：Calcite语句签名，包含SQL语句的元数据信息
    // 参数resultSetType：结果集类型
    // 参数resultSetConcurrency：结果集并发类型
    // 参数resultSetHoldability：结果集可保持性
    CalciteJdbc41PreparedStatement(CalciteConnectionImpl connection,
        Meta.@Nullable StatementHandle h, CalcitePrepare.CalciteSignature signature,
        int resultSetType, int resultSetConcurrency, int resultSetHoldability)
        throws SQLException {
      super(connection, h, signature, resultSetType, resultSetConcurrency, // 调用父类CalcitePreparedStatement的构造函数，初始化预处理语句
          resultSetHoldability); // 传递结果集可保持性
    }

    // 设置RowId参数（JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数x：RowId值，可能为null
    // 功能：将指定参数设置为RowId类型，用于唯一标识数据库中的行
    @Override public void setRowId(
        int parameterIndex,
        @Nullable RowId x) throws SQLException {
      getSite(parameterIndex).setRowId(x); // 获取参数绑定位置并设置RowId值
    }

    // 设置NString参数（JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数value：NString值，可能为null，使用国家字符集
    // 功能：将指定参数设置为NString类型，支持Unicode字符集
    @Override public void setNString(
        int parameterIndex, @Nullable String value) throws SQLException {
      getSite(parameterIndex).setNString(value); // 获取参数绑定位置并设置NString值
    }

    // 设置NCharacterStream参数（带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数value：字符读取器，可能为null，使用国家字符集
    // 参数length：流长度（字符数）
    // 功能：将指定参数设置为NCharacterStream类型，用于读取大量文本数据
    @Override public void setNCharacterStream(
        int parameterIndex,
        @Nullable Reader value,
        long length) throws SQLException {
      getSite(parameterIndex) // 获取参数绑定位置
          .setNCharacterStream(value, length); // 设置NCharacterStream值，指定流长度
    }

    // 设置NClob参数（JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数value：NClob对象，可能为null，国家字符大对象
    // 功能：将指定参数设置为NClob类型，用于存储大文本数据
    @Override public void setNClob(
        int parameterIndex,
        @Nullable NClob value) throws SQLException {
      getSite(parameterIndex).setNClob(value); // 获取参数绑定位置并设置NClob值
    }

    // 设置Clob参数（从Reader读取，带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数reader：字符读取器，可能为null
    // 参数length：流长度（字符数）
    // 功能：将指定参数设置为Clob类型，从Reader读取数据
    @Override public void setClob(
        int parameterIndex,
        @Nullable Reader reader,
        long length) throws SQLException {
      getSite(parameterIndex) // 获取参数绑定位置
          .setClob(reader, length); // 设置Clob值，从Reader读取指定长度的数据
    }

    // 设置Blob参数（从InputStream读取，带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数inputStream：输入流，可能为null
    // 参数length：流长度（字节数）
    // 功能：将指定参数设置为Blob类型，从InputStream读取二进制数据
    @Override public void setBlob(
        int parameterIndex,
        @Nullable InputStream inputStream,
        long length) throws SQLException {
      getSite(parameterIndex) // 获取参数绑定位置
          .setBlob(inputStream, length); // 设置Blob值，从InputStream读取指定长度的数据
    }

    // 设置NClob参数（从Reader读取，带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数reader：字符读取器，可能为null
    // 参数length：流长度（字符数）
    // 功能：将指定参数设置为NClob类型，从Reader读取数据
    @Override public void setNClob(
        int parameterIndex,
        @Nullable Reader reader,
        long length) throws SQLException {
      getSite(parameterIndex).setNClob(reader, length); // 获取参数绑定位置并设置NClob值，从Reader读取指定长度的数据
    }

    // 设置SQLXML参数（JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数xmlObject：SQLXML对象，可能为null
    // 功能：将指定参数设置为SQLXML类型，用于存储XML数据
    @Override public void setSQLXML(
        int parameterIndex, @Nullable SQLXML xmlObject) throws SQLException {
      getSite(parameterIndex).setSQLXML(xmlObject); // 获取参数绑定位置并设置SQLXML值
    }

    // 设置AsciiStream参数（带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数x：输入流，可能为null
    // 参数length：流长度（字节数）
    // 功能：将指定参数设置为AsciiStream类型，用于读取ASCII编码的文本数据
    @Override public void setAsciiStream(
        int parameterIndex,
        @Nullable InputStream x,
        long length) throws SQLException {
      getSite(parameterIndex) // 获取参数绑定位置
          .setAsciiStream(x, length); // 设置AsciiStream值，指定流长度
    }

    // 设置BinaryStream参数（带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数x：输入流，可能为null
    // 参数length：流长度（字节数）
    // 功能：将指定参数设置为BinaryStream类型，用于读取二进制数据
    @Override public void setBinaryStream(
        int parameterIndex,
        @Nullable InputStream x,
        long length) throws SQLException {
      getSite(parameterIndex) // 获取参数绑定位置
          .setBinaryStream(x, length); // 设置BinaryStream值，指定流长度
    }

    // 设置CharacterStream参数（带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数reader：字符读取器，可能为null
    // 参数length：流长度（字符数）
    // 功能：将指定参数设置为CharacterStream类型，用于读取文本数据
    @Override public void setCharacterStream(
        int parameterIndex,
        @Nullable Reader reader,
        long length) throws SQLException {
      getSite(parameterIndex) // 获取参数绑定位置
          .setCharacterStream(reader, length); // 设置CharacterStream值，指定流长度
    }

    // 设置AsciiStream参数（不带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数x：输入流，可能为null
    // 功能：将指定参数设置为AsciiStream类型，流长度未知
    @Override public void setAsciiStream(
        int parameterIndex, @Nullable InputStream x) throws SQLException {
      getSite(parameterIndex).setAsciiStream(x); // 获取参数绑定位置并设置AsciiStream值
    }

    // 设置BinaryStream参数（不带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数x：输入流，可能为null
    // 功能：将指定参数设置为BinaryStream类型，流长度未知
    @Override public void setBinaryStream(
        int parameterIndex, @Nullable InputStream x) throws SQLException {
      getSite(parameterIndex).setBinaryStream(x); // 获取参数绑定位置并设置BinaryStream值
    }

    // 设置CharacterStream参数（不带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数reader：字符读取器，可能为null
    // 功能：将指定参数设置为CharacterStream类型，流长度未知
    @Override public void setCharacterStream(
        int parameterIndex, @Nullable Reader reader) throws SQLException {
      getSite(parameterIndex) // 获取参数绑定位置
          .setCharacterStream(reader); // 设置CharacterStream值，不指定流长度
    }

    // 设置NCharacterStream参数（不带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数value：字符读取器，可能为null，使用国家字符集
    // 功能：将指定参数设置为NCharacterStream类型，流长度未知
    @Override public void setNCharacterStream(
        int parameterIndex, @Nullable Reader value) throws SQLException {
      getSite(parameterIndex) // 获取参数绑定位置
          .setNCharacterStream(value); // 设置NCharacterStream值，不指定流长度
    }

    // 设置Clob参数（从Reader读取，不带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数reader：字符读取器，可能为null
    // 功能：将指定参数设置为Clob类型，从Reader读取数据，长度未知
    @Override public void setClob(
        int parameterIndex,
        @Nullable Reader reader) throws SQLException {
      getSite(parameterIndex).setClob(reader); // 获取参数绑定位置并设置Clob值，不指定流长度
    }

    // 设置Blob参数（从InputStream读取，不带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数inputStream：输入流，可能为null
    // 功能：将指定参数设置为Blob类型，从InputStream读取数据，长度未知
    @Override public void setBlob(
        int parameterIndex, @Nullable InputStream inputStream) throws SQLException {
      getSite(parameterIndex) // 获取参数绑定位置
          .setBlob(inputStream); // 设置Blob值，不指定流长度
    }

    // 设置NClob参数（从Reader读取，不带长度，JDBC 4.1新增方法）
    // 参数parameterIndex：参数索引（从1开始）
    // 参数reader：字符读取器，可能为null
    // 功能：将指定参数设置为NClob类型，从Reader读取数据，长度未知
    @Override public void setNClob(
        int parameterIndex, @Nullable Reader reader) throws SQLException {
      getSite(parameterIndex).setNClob(reader); // 获取参数绑定位置并设置NClob值，不指定流长度
    }
  }

  /** Implementation of database metadata for JDBC 4.1. */
  // JDBC 4.1数据库元数据实现类，继承自AvaticaDatabaseMetaData
  // 这是一个静态内部类，专门用于实现JDBC 4.1规范的数据库元数据功能
  // 负责提供数据库的元数据信息，如表、列、索引、主键、外键等
  private static class CalciteJdbc41DatabaseMetaData // 私有静态内部类，继承AvaticaDatabaseMetaData
      extends AvaticaDatabaseMetaData {
    // 构造函数：创建JDBC 4.1数据库元数据实例
    // 参数connection：Calcite连接实例
    CalciteJdbc41DatabaseMetaData(CalciteConnectionImpl connection) {
      super(connection); // 调用父类AvaticaDatabaseMetaData的构造函数，初始化数据库元数据
    }
  }
}
