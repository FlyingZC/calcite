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
// Apache许可证声明，说明代码遵循Apache 2.0许可证，允许在特定条件下使用和修改
package org.apache.calcite.test; // 定义包名，该类属于org.apache.calcite.test包，用于Calcite测试框架

import org.apache.calcite.avatica.ConnectionProperty; // 导入Avatica框架的连接属性类，用于设置连接时的属性

import java.sql.Connection; // 导入JDBC连接接口，表示数据库连接对象
import java.sql.SQLException; // 导入SQL异常类，用于处理数据库操作中的异常

/**
 * Creates JDBC connections for tests.
 * 为测试创建JDBC连接的工厂接口
 *
 * <p>The base class is abstract, and all of the {@code with} methods throw.
 * 基类是抽象的，所有with方法默认抛出异常
 *
 * <p>Avoid creating new sub-classes otherwise it would be hard to support
 * {@code .with(property, value).with(...)} kind of chains.
 * 避免创建新的子类，否则很难支持链式调用（如.with(property, value).with(...)）
 *
 * <p>If you want augment the connection, use
 * {@link CalciteAssert.ConnectionPostProcessor}.
 * 如果要增强连接功能，请使用CalciteAssert.ConnectionPostProcessor
 *
 * @see ConnectionFactories
 * 参见ConnectionFactories类，它是ConnectionFactory的实现工厂类
 */
public interface ConnectionFactory { // 定义ConnectionFactory接口，这是一个用于创建测试用JDBC连接的工厂接口
  Connection createConnection() throws SQLException; // 抽象方法：创建一个新的JDBC连接，可能抛出SQLException异常

  default ConnectionFactory with(String property, Object value) { // 默认方法：通过字符串属性名和值来设置连接属性，返回新的ConnectionFactory对象以支持链式调用
    throw new UnsupportedOperationException(); // 默认抛出不支持操作异常，子类可以覆盖此方法实现具体逻辑
  }

  default ConnectionFactory with(ConnectionProperty property, Object value) { // 默认方法：通过ConnectionProperty枚举和值来设置连接属性，返回新的ConnectionFactory对象以支持链式调用
    throw new UnsupportedOperationException(); // 默认抛出不支持操作异常，子类可以覆盖此方法实现具体逻辑
  }

  default ConnectionFactory with(CalciteAssert.ConnectionPostProcessor postProcessor) { // 默认方法：通过连接后处理器来增强连接功能，返回新的ConnectionFactory对象以支持链式调用
    throw new UnsupportedOperationException(); // 默认抛出不支持操作异常，子类可以覆盖此方法实现具体逻辑
  }
}
