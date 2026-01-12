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
package org.apache.calcite.chinook; // 声明该类所属的包：org.apache.calcite.chinook，这是Calcite框架中用于Chinook示例数据库的包

import net.hydromatic.chinook.data.hsqldb.ChinookHsqldb; // 导入Chinook HSQLDB数据库相关的工具类，提供数据库连接URI、用户名和密码等常量
import net.hydromatic.quidem.Quidem; // 导入Quidem框架，这是一个SQL测试框架，用于验证SQL查询的正确性

import java.sql.Connection; // 导入JDBC连接接口，用于建立与数据库的连接
import java.sql.DriverManager; // 导入JDBC驱动管理器，用于通过驱动程序获取数据库连接

/**
 * Wrapping connection factory for Quidem.
 */
public class ConnectionFactory implements Quidem.ConnectionFactory { // 定义ConnectionFactory类，实现Quidem.ConnectionFactory接口，这是一个用于Quidem测试框架的连接工厂类，负责创建和管理数据库连接

  private static final CalciteConnectionProvider CALCITE = new CalciteConnectionProvider(); // 声明一个静态常量CALCITE，类型为CalciteConnectionProvider，这是Calcite连接提供者，负责创建和管理Calcite数据库连接，使用单例模式确保整个应用中只有一个Calcite连接提供者实例

  @Override public Connection connect(String db, boolean bln) throws Exception { // 重写Quidem.ConnectionFactory接口的connect方法，用于根据数据库名称创建数据库连接，参数db是数据库名称，参数bln是布尔标志（未使用），返回Connection对象表示数据库连接，可能抛出异常
    return DatabaseWrapper.valueOf(db).connection(); // 通过DatabaseWrapper枚举的valueOf方法将数据库名称转换为对应的枚举实例，然后调用其connection方法创建并返回数据库连接
  }

  /**
   * Wrapping with Fairy environmental decoration.
   */
  public enum DatabaseWrapper { // 定义DatabaseWrapper枚举，用于包装不同类型的数据库连接，支持Calcite连接（以管理员或特定用户身份）和原始HSQLDB连接，每个枚举值都实现了connection方法来创建对应的数据库连接
    CALCITE_AS_ADMIN { // 枚举值CALCITE_AS_ADMIN：表示以管理员身份连接到Calcite数据库
      @Override public Connection connection() throws Exception { // 重写connection方法，创建以管理员身份的Calcite数据库连接
        EnvironmentFairy.login(EnvironmentFairy.User.ADMIN); // 调用EnvironmentFairy的login方法，以管理员身份登录环境，设置当前用户的上下文信息
        return CALCITE.connection(); // 调用CalciteConnectionProvider的connection方法，创建并返回Calcite数据库连接
      }
    },
    CALCITE_AS_SPECIFIC_USER { // 枚举值CALCITE_AS_SPECIFIC_USER：表示以特定用户身份连接到Calcite数据库
      @Override public Connection connection() throws Exception { // 重写connection方法，创建以特定用户身份的Calcite数据库连接
        EnvironmentFairy.login(EnvironmentFairy.User.SPECIFIC_USER); // 调用EnvironmentFairy的login方法，以特定用户身份登录环境，设置当前用户的上下文信息
        return CALCITE.connection(); // 调用CalciteConnectionProvider的connection方法，创建并返回Calcite数据库连接
      }
    },
    RAW { // 枚举值RAW：表示直接连接到原始的HSQLDB数据库，不经过Calcite
      @Override public Connection connection() throws Exception { // 重写connection方法，创建原始HSQLDB数据库连接
        return DriverManager.getConnection(ChinookHsqldb.URI, // 使用DriverManager获取数据库连接，传入ChinookHsqldb.URI（数据库连接URL）
            ChinookHsqldb.USER, ChinookHsqldb.PASSWORD); // 传入ChinookHsqldb.USER（用户名）和ChinookHsqldb.PASSWORD（密码）作为认证信息
      }
    };
    public abstract Connection connection() throws Exception; // 声明抽象方法connection，要求每个枚举值都必须实现此方法，用于创建并返回数据库连接，可能抛出异常
  }

}
