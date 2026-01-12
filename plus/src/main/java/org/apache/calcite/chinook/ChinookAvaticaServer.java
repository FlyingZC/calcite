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
// Apache许可证声明，表明该代码遵循Apache 2.0许可证，允许在特定条件下使用和修改
package org.apache.calcite.chinook; // 声明包名，该类位于org.apache.calcite.chinook包下

import org.apache.calcite.avatica.Meta; // 导入Avatica的Meta接口，定义了元数据操作的规范
import org.apache.calcite.avatica.jdbc.JdbcMeta; // 导入JdbcMeta类，用于通过JDBC访问数据库元数据
import org.apache.calcite.avatica.remote.Driver; // 导入远程驱动类，支持通过远程协议连接Avatica服务器
import org.apache.calcite.avatica.server.AvaticaProtobufHandler; // 导入Protobuf处理器，用于处理基于Protobuf协议的请求
import org.apache.calcite.avatica.server.HttpServer; // 导入HTTP服务器类，用于启动Avatica的HTTP服务
import org.apache.calcite.avatica.server.Main; // 导入Main类，提供启动Avatica服务器的主要方法

import net.hydromatic.chinook.data.hsqldb.ChinookHsqldb; // 导入ChinookHsqldb类，提供Chinook示例数据库的HSQLDB实现

import java.io.IOException; // 导入IO异常类，处理输入输出操作中的异常
import java.sql.SQLException; // 导入SQL异常类，处理数据库操作中的异常
import java.util.List; // 导入List接口，用于处理列表集合

/**
 * Wrapping Calcite engine with Avatica tansport for testing JDBC capabilities
 * between Avatica JDBC transport and Calcite.
 */
// 类功能说明：这是一个包装类，将Calcite引擎与Avatica传输层结合，用于测试Avatica JDBC传输和Calcite之间的JDBC能力
// 该类主要用于创建和管理一个HTTP服务器，通过Avatica协议提供对Chinook示例数据库的访问
public class ChinookAvaticaServer { // 定义ChinookAvaticaServer类，作为Avatica服务器的启动器和管理器
  private HttpServer server; // 成员变量：HTTP服务器实例，用于运行Avatica服务器，监听客户端请求

  public void startWithCalcite() throws Exception { // 方法：使用Calcite引擎启动Avatica服务器
    final String[] args = {CalciteChinookMetaFactory.class.getName()}; // 创建参数数组，包含CalciteChinookMetaFactory的完全限定类名，用于指定使用Calcite作为元数据工厂
    this.server = Main.start(args, 0, AvaticaProtobufHandler::new); // 调用Main.start方法启动服务器，传入参数、端口0表示自动选择可用端口，使用Protobuf处理器处理请求
  }

  public void startWithRaw() throws Exception { // 方法：使用原始HSQLDB启动Avatica服务器（不通过Calcite）
    final String[] args = {RawChinookMetaFactory.class.getName()}; // 创建参数数组，包含RawChinookMetaFactory的完全限定类名，用于指定使用原生HSQLDB作为元数据工厂
    this.server = Main.start(args, 0, AvaticaProtobufHandler::new); // 调用Main.start方法启动服务器，传入参数、端口0表示自动选择可用端口，使用Protobuf处理器处理请求
  }

  public String getURL() { // 方法：获取连接到该Avatica服务器的JDBC URL
    return "jdbc:avatica:remote:url=http://localhost:" + server.getPort() // 返回JDBC URL，使用avatica:remote协议，指定服务器地址为localhost和动态分配的端口
        + ";serialization=" + Driver.Serialization.PROTOBUF.name(); // 指定序列化方式为PROTOBUF，用于客户端和服务器之间的数据传输
  }

  public void stop() { // 方法：停止Avatica服务器，释放资源
    server.stop(); // 调用HTTP服务器的stop方法，停止服务器并释放相关资源
  }

  /**
   * Factory for Chinook Calcite database wrapped in meta for Avatica.
   */
  // 内部类说明：这是一个静态内部类，实现了Meta.Factory接口，用于创建基于Calcite的Chinook数据库的元数据实例
  // 该工厂类负责创建JdbcMeta实例，通过Calcite引擎访问Chinook示例数据库
  public static class CalciteChinookMetaFactory implements Meta.Factory { // 定义CalciteChinookMetaFactory类，实现Meta.Factory接口
    private static final CalciteConnectionProvider CONNECTION_PROVIDER = // 成员变量：Calcite连接提供者，用于提供Calcite数据库连接信息
        new CalciteConnectionProvider(); // 创建CalciteConnectionProvider实例，该实例负责提供Calcite JDBC连接的URL和属性

    private static volatile JdbcMeta instance = null; // 成员变量：JdbcMeta单例实例，使用volatile关键字确保多线程环境下的可见性，采用双重检查锁定模式实现懒加载单例

    private static JdbcMeta getInstance() { // 方法：获取JdbcMeta单例实例，使用双重检查锁定模式确保线程安全
      if (instance == null) { // 第一次检查：如果实例为null，则进入同步块创建实例（避免不必要的同步）
        synchronized (CalciteChinookMetaFactory.class) { // 使用类对象作为锁，确保同一时间只有一个线程能执行创建逻辑
          try {
            instance = // 第二次检查：在同步块内再次检查，防止多个线程同时通过第一次检查后重复创建
                new JdbcMeta(CalciteConnectionProvider.DRIVER_URL, // 创建JdbcMeta实例，传入Calcite的JDBC驱动URL
                    CONNECTION_PROVIDER.provideConnectionInfo()); // 传入连接信息（包括用户名、密码等属性）
          } catch (SQLException | IOException e) { // 捕获SQL异常和IO异常
            throw new RuntimeException(e); // 将检查异常转换为运行时异常抛出
          }
        }
      }
      return instance; // 返回JdbcMeta单例实例
    }

    @Override public Meta create(List<String> args) { // 方法：实现Meta.Factory接口的create方法，用于创建Meta实例
      return getInstance(); // 返回JdbcMeta单例实例，该实例实现了Meta接口
    }
  }

  /**
   * Factory for Chinook Calcite database wrapped in meta for Avatica.
   */
  // 内部类说明：这是一个静态内部类，实现了Meta.Factory接口，用于创建基于原生HSQLDB的Chinook数据库的元数据实例
  // 该工厂类负责创建JdbcMeta实例，直接通过HSQLDB访问Chinook示例数据库，不经过Calcite引擎
  public static class RawChinookMetaFactory implements Meta.Factory { // 定义RawChinookMetaFactory类，实现Meta.Factory接口
    private static volatile JdbcMeta instance = null; // 成员变量：JdbcMeta单例实例，使用volatile关键字确保多线程环境下的可见性，采用双重检查锁定模式实现懒加载单例

    private static JdbcMeta getInstance() { // 方法：获取JdbcMeta单例实例，使用双重检查锁定模式确保线程安全
      if (instance == null) { // 第一次检查：如果实例为null，则进入同步块创建实例（避免不必要的同步）
        synchronized (RawChinookMetaFactory.class) { // 使用类对象作为锁，确保同一时间只有一个线程能执行创建逻辑
          if (instance == null) { // 第二次检查：在同步块内再次检查，防止多个线程同时通过第一次检查后重复创建
            try {
              instance = // 创建JdbcMeta实例，直接使用HSQLDB的连接参数
                  new JdbcMeta(ChinookHsqldb.URI, ChinookHsqldb.USER, ChinookHsqldb.PASSWORD); // 传入HSQLDB的URI、用户名和密码
            } catch (SQLException e) { // 捕获SQL异常
              throw new RuntimeException(e); // 将检查异常转换为运行时异常抛出
            }
          }
        }
      }
      return instance; // 返回JdbcMeta单例实例
    }

    @Override public Meta create(List<String> args) { // 方法：实现Meta.Factory接口的create方法，用于创建Meta实例
      return getInstance(); // 返回JdbcMeta单例实例，该实例实现了Meta接口
    }
  }
}
