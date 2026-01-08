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
package org.apache.calcite.jdbc; // 声明包名，该类属于 org.apache.calcite.jdbc 包，是 Calcite JDBC 驱动的核心包

import org.apache.calcite.avatica.AvaticaResultSet; // 导入 AvaticaResultSet 基类，Avatica 是 Calcite 的 JDBC 框架底层实现
import org.apache.calcite.avatica.AvaticaResultSetMetaData; // 导入 AvaticaResultSetMetaData，用于描述结果集的元数据信息
import org.apache.calcite.avatica.AvaticaStatement; // 导入 AvaticaStatement，表示 JDBC Statement 的实现
import org.apache.calcite.avatica.ColumnMetaData; // 导入 ColumnMetaData，用于描述列的元数据信息（类型、名称等）
import org.apache.calcite.avatica.Handler; // 导入 Handler，用于处理语句执行的回调接口
import org.apache.calcite.avatica.Meta; // 导入 Meta，Avatica 的元数据接口，包含执行结果框架等
import org.apache.calcite.avatica.util.Cursor; // 导入 Cursor，游标接口，用于遍历查询结果
import org.apache.calcite.linq4j.Enumerator; // 导入 Enumerator，LINQ4J 的枚举器接口，用于遍历数据集合
import org.apache.calcite.linq4j.Linq4j; // 导入 Linq4j，LINQ4J 工具类，提供集合操作和枚举器创建功能
import org.apache.calcite.runtime.ArrayEnumeratorCursor; // 导入 ArrayEnumeratorCursor，将枚举器包装为数组游标
import org.apache.calcite.runtime.ObjectEnumeratorCursor; // 导入 ObjectEnumeratorCursor，将枚举器包装为对象游标

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList，不可变列表实现

import java.sql.ResultSet; // 导入 JDBC ResultSet 接口，标准 JDBC 结果集接口
import java.sql.ResultSetMetaData; // 导入 JDBC ResultSetMetaData 接口，标准 JDBC 结果集元数据接口
import java.sql.SQLException; // 导入 SQLException，标准 JDBC 异常类
import java.util.List; // 导入 List，Java 集合框架的列表接口
import java.util.TimeZone; // 导入 TimeZone，时区类，用于处理时间戳的时区转换

/**
 * Implementation of {@link ResultSet} // 类文档注释：这是 Calcite 引擎的 ResultSet 实现
 * for the Calcite engine. // 专门为 Calcite SQL 查询引擎设计的 JDBC ResultSet 实现
 * 
 * CalciteResultSet 是 Calcite 查询执行结果的核心表示类，它：
 * 1. 继承自 AvaticaResultSet，利用 Avatica 框架提供的基础 JDBC 功能
 * 2. 封装了 Calcite 特有的查询执行逻辑和结果处理
 * 3. 支持从 LINQ4J 枚举器创建结果集
 * 4. 提供与 Calcite 连接和签名（Signature）的集成
 * 5. 处理语句执行的回调通知
 * 
 * 核心功能：
 * - 执行查询并返回结果集
 * - 创建子结果集（用于嵌套查询）
 * - 管理游标以遍历结果数据
 * - 提供对 Calcite 特有元数据和配置的访问
 */
public class CalciteResultSet extends AvaticaResultSet { // CalciteResultSet 类声明，继承自 AvaticaResultSet 基类

  /** Creates a CalciteResultSet. */ // 注释：创建一个 CalciteResultSet 对象的构造方法
  CalciteResultSet(AvaticaStatement statement, // 参数：关联的 AvaticaStatement 对象，表示执行查询的语句
      CalcitePrepare.CalciteSignature calciteSignature, // 参数：Calcite 签名对象，包含查询的元数据信息（SQL、参数、返回类型等）
      ResultSetMetaData resultSetMetaData, // 参数：结果集元数据，描述结果集的列信息
      TimeZone timeZone, // 参数：时区对象，用于时间戳字段的时区转换
      Meta.Frame firstFrame) throws SQLException { // 参数：第一帧数据，包含初始查询结果；可能抛出 SQLException 异常
    super(statement, null, calciteSignature, resultSetMetaData, timeZone, firstFrame); // 调用父类 AvaticaResultSet 的构造方法，初始化结果集
  } // 构造方法结束

  @Override protected CalciteResultSet execute() throws SQLException { // 重写父类的 execute 方法，执行查询并返回结果集
    // Call driver's callback. It is permitted to throw a RuntimeException. // 注释：调用驱动程序的回调函数，允许抛出运行时异常
    CalciteConnectionImpl connection = getCalciteConnection(); // 获取 Calcite 连接对象，用于访问连接配置和驱动
    final boolean autoTemp = connection.config().autoTemp(); // 从连接配置中获取 autoTemp 标志，表示是否自动创建临时表
    Handler.ResultSink resultSink = null; // 初始化结果接收器为 null，用于接收查询结果的回调
    if (autoTemp) { // 如果启用了自动临时表模式
      resultSink = () -> { // 创建一个空的 lambda 表达式作为结果接收器
      }; // lambda 结束，表示不需要特殊处理结果
    } // if 结束
    connection.getDriver().handler.onStatementExecute(statement, resultSink); // 调用驱动程序的处理器，通知语句执行事件，传入语句和结果接收器

    super.execute(); // 调用父类的 execute 方法，执行实际的查询逻辑
    return this; // 返回当前结果集对象，支持链式调用
  } // execute 方法结束

  @Override public ResultSet create(ColumnMetaData.AvaticaType elementType, // 重写父类的 create 方法，创建子结果集
      Iterable<Object> iterable) throws SQLException { // 参数：elementType 表示结果集元素的类型；iterable 是可迭代的数据源；可能抛出 SQLException
    final List<ColumnMetaData> columnMetaDataList; // 声明列元数据列表变量，用于存储结果集的列信息
    if (elementType instanceof ColumnMetaData.StructType) { // 如果元素类型是结构体类型（多列）
      columnMetaDataList = ((ColumnMetaData.StructType) elementType).columns; // 从结构体类型中提取列元数据列表
    } else { // 否则（元素是单个值）
      columnMetaDataList = // 创建列元数据列表
          ImmutableList.of(ColumnMetaData.dummy(elementType, false)); // 创建包含单个虚拟列的不可变列表，false 表示非可为空
    } // if-else 结束
    final CalcitePrepare.CalciteSignature signature = // 声明签名变量
        (CalcitePrepare.CalciteSignature) this.signature; // 将当前结果集的签名转换为 Calcite 签名类型
    final CalcitePrepare.CalciteSignature<Object> newSignature = // 声明新的签名变量，泛型类型为 Object
        new CalcitePrepare.CalciteSignature<>(signature.sql, // 创建新的 Calcite 签名，传入原 SQL
            signature.parameters, // 传入原参数列表
            signature.internalParameters, // 传入内部参数列表
            signature.rowType, // 传入行类型（RelDataType）
            columnMetaDataList, // 传入新的列元数据列表
            Meta.CursorFactory.ARRAY, // 设置游标工厂为数组类型
            signature.rootSchema, // 传入根 Schema
            ImmutableList.of(), // 传入空的状态列表
            -1, // 设置最大行数为 -1（无限制）
            null, // 传入空的绑定信息
            statement.getStatementType()); // 传入语句类型（SELECT、INSERT 等）
    ResultSetMetaData subResultSetMetaData = // 声明子结果集元数据变量
        new AvaticaResultSetMetaData(statement, null, newSignature); // 创建 Avatica 结果集元数据对象
    final CalciteResultSet resultSet = // 声明新的结果集变量
        new CalciteResultSet(statement, signature, subResultSetMetaData, // 创建新的 CalciteResultSet 对象
            localCalendar.getTimeZone(), // 传入本地时区
            new Meta.Frame(0, true, iterable)); // 创建初始帧，偏移量为 0，done 标志为 true，传入可迭代数据源
    final Cursor cursor = CalciteResultSet.createCursor(elementType, iterable); // 根据元素类型和可迭代数据创建游标
    return resultSet.execute2(cursor, columnMetaDataList); // 执行结果集并返回，传入游标和列元数据列表
  } // create 方法结束

  private static Cursor createCursor(ColumnMetaData.AvaticaType elementType, // 私有静态方法：创建游标对象
          Iterable iterable) { // 参数：elementType 表示元素类型；iterable 是可迭代的数据源
    final Enumerator enumerator = Linq4j.iterableEnumerator(iterable); // 使用 Linq4j 将可迭代对象转换为枚举器
    //noinspection unchecked // 注释：抑制未检查的类型转换警告
    return !(elementType instanceof ColumnMetaData.StructType) // 如果元素类型不是结构体类型
        || ((ColumnMetaData.StructType) elementType).columns.size() == 1 // 或者结构体只有一列
        ? new ObjectEnumeratorCursor(enumerator) // 则创建对象枚举器游标（每行返回单个对象）
        : new ArrayEnumeratorCursor(enumerator); // 否则创建数组枚举器游标（每行返回对象数组）
  } // createCursor 方法结束

  // do not make public // 注释：不要将此方法设为 public，仅供内部使用
  <T> CalcitePrepare.CalciteSignature<T> getSignature() { // 泛型方法：获取结果集的 Calcite 签名
    //noinspection unchecked // 注释：抑制未检查的类型转换警告
    return (CalcitePrepare.CalciteSignature) signature; // 将父类的 signature 字段强制转换为 Calcite 签名类型并返回
  } // getSignature 方法结束

  // do not make public // 注释：不要将此方法设为 public，仅供内部使用
  CalciteConnectionImpl getCalciteConnection() throws SQLException { // 方法：获取 Calcite 连接对象
    return (CalciteConnectionImpl) statement.getConnection(); // 从语句中获取连接并强制转换为 CalciteConnectionImpl 类型
  } // getCalciteConnection 方法结束
} // CalciteResultSet 类结束
