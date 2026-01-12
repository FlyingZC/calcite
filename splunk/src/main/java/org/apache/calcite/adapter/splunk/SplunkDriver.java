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
package org.apache.calcite.adapter.splunk; // Splunk适配器包，包含Splunk数据源相关的适配器实现

import org.apache.calcite.adapter.splunk.search.SearchResultListener; // 导入搜索结果监听器接口，用于异步接收搜索结果
import org.apache.calcite.adapter.splunk.search.SplunkConnection; // 导入Splunk连接接口，定义与Splunk服务器通信的规范
import org.apache.calcite.adapter.splunk.search.SplunkConnectionImpl; // 导入Splunk连接实现类，提供实际的连接功能
import org.apache.calcite.avatica.DriverVersion; // 导入驱动版本类，用于管理驱动版本信息
import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接类，提供Calcite特有的连接功能
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历查询结果集
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，支持动态添加和修改schema

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数

import java.net.URI; // 导入URI类，用于统一资源标识符的处理
import java.net.URL; // 导入URL类，用于统一资源定位符的处理
import java.sql.Connection; // 导入Connection接口，JDBC标准连接接口
import java.sql.SQLException; // 导入SQLException类，处理SQL相关异常
import java.util.List; // 导入List接口，用于处理列表集合
import java.util.Map; // 导入Map接口，用于处理键值对集合
import java.util.Properties; // 导入Properties类，用于处理连接属性配置

/**
 * JDBC driver for Splunk.
 * Splunk的JDBC驱动程序，用于连接和查询Splunk数据源
 *
 * <p>It accepts connect strings that start with "jdbc:splunk:".
 * 它接受以"jdbc:splunk:"开头的连接字符串
 */
public class SplunkDriver extends org.apache.calcite.jdbc.Driver { // SplunkDriver类继承自Calcite的Driver基类，实现Splunk数据源的JDBC驱动功能
  protected SplunkDriver() { // 受保护的构造方法，用于创建SplunkDriver实例
    super(); // 调用父类Driver的构造方法，完成基本的初始化工作
  }

  static { // 静态初始化块，在类加载时执行
    new SplunkDriver().register(); // 创建SplunkDriver实例并注册到JDBC驱动管理器，使驱动可以被JDBC识别和使用
  }

  @Override protected String getConnectStringPrefix() { // 重写父类方法，获取连接字符串前缀
    return "jdbc:splunk:"; // 返回Splunk驱动的连接字符串前缀，用于标识这是一个Splunk连接
  }

  @Override protected DriverVersion createDriverVersion() { // 重写父类方法，创建驱动版本对象
    return new SplunkDriverVersion(); // 返回SplunkDriverVersion实例，包含驱动版本信息
  }

  @Override public Connection connect(String url, Properties info) // 重写父类方法，建立与Splunk的连接
      throws SQLException { // 可能抛出SQL异常
    Connection connection = super.connect(url, info); // 调用父类connect方法，创建基础的Calcite连接对象
    CalciteConnection calciteConnection = (CalciteConnection) connection; // 将连接转换为CalciteConnection类型，以便使用Calcite特有功能
    SplunkConnection splunkConnection; // 声明Splunk连接对象，用于与Splunk服务器通信
    try { // 开始try块，处理连接过程中可能出现的异常
      String url1 = info.getProperty("url"); // 从属性中获取Splunk服务器的URL地址
      if (url1 == null) { // 检查URL是否为空
        throw new IllegalArgumentException( // 如果为空，抛出非法参数异常
            "Must specify 'url' property"); // 异常消息提示必须指定url属性
      }
      if (url1.equals("mock")) { // 检查URL是否为"mock"，用于测试场景
        splunkConnection = new MockSplunkConnection(); // 创建模拟连接对象，用于测试而不连接真实服务器
      } else { // 如果不是mock模式，则创建真实连接
        String user = info.getProperty("user"); // 从属性中获取用户名
        if (user == null) { // 检查用户名是否为空
          throw new IllegalArgumentException( // 如果为空，抛出非法参数异常
              "Must specify 'user' property"); // 异常消息提示必须指定user属性
        }
        String password = info.getProperty("password"); // 从属性中获取密码
        if (password == null) { // 检查密码是否为空
          throw new IllegalArgumentException( // 如果为空，抛出非法参数异常
              "Must specify 'password' property"); // 异常消息提示必须指定password属性
        }
        URL url2 = URI.create(url1).toURL(); // 将字符串URL转换为URI对象，再转换为URL对象
        splunkConnection = new SplunkConnectionImpl(url2, user, password); // 创建真实的Splunk连接实现，使用URL、用户名和密码
      }
    } catch (Exception e) { // 捕获所有异常
      throw new SQLException("Cannot connect", e); // 将异常包装为SQLException抛出，消息为"Cannot connect"
    }
    final SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根Schema对象，用于注册Splunk schema
    rootSchema.add("splunk", new SplunkSchema(splunkConnection)); // 将Splunk schema添加到根schema中，名称为"splunk"，使用创建的连接

    return connection; // 返回创建的连接对象
  }

  /** Connection that looks up responses from a static map. */
  /** 从静态映射查找响应的连接对象，用于测试场景，不连接真实的Splunk服务器 */
  @SuppressWarnings("unused") // 抑制未使用警告，该类在特定测试场景下使用
  private static class MockSplunkConnection implements SplunkConnection { // MockSplunkConnection内部类，实现SplunkConnection接口，提供模拟连接功能
    @Override public Enumerator<Object> getSearchResultEnumerator(String search, // 重写接口方法，获取搜索结果的枚举器
        Map<String, String> otherArgs, @Nullable List<String> fieldList) { // 参数：search搜索语句，otherArgs其他参数，fieldList字段列表（可空）
      throw new NullPointerException(); // 抛出空指针异常，表示此方法在mock模式下不支持
    }

    @Override public void getSearchResults(String search, // 重写接口方法，异步获取搜索结果
        Map<String, String> otherArgs, // 其他参数映射
        @Nullable List<String> fieldList, SearchResultListener srl) { // 字段列表（可空）和搜索结果监听器
      throw new UnsupportedOperationException(); // 抛出不支持操作异常，表示此方法在mock模式下不支持
    }
  }

  /** Connection that records requests and responses. */
  /** 记录请求和响应的连接对象，用于调试和日志记录，包装真实连接以记录所有操作 */
  @SuppressWarnings("unused") // 抑制未使用警告，该类在特定调试场景下使用
  private static class WrappingSplunkConnection implements SplunkConnection { // WrappingSplunkConnection内部类，实现SplunkConnection接口，提供连接包装功能
    WrappingSplunkConnection(SplunkConnection connection) { // 构造方法，接收一个Splunk连接对象进行包装
    } // 构造方法体为空，仅用于接收连接对象

    @Override public void getSearchResults(String search, // 重写接口方法，异步获取搜索结果并记录日志
        Map<String, String> otherArgs, // 其他参数映射
        @Nullable List<String> fieldList, SearchResultListener srl) { // 字段列表（可空）和搜索结果监听器
      System.out.println("search='" + search // 输出搜索语句到控制台
          + "', otherArgs=" + otherArgs // 输出其他参数到控制台
          + ", fieldList='" + fieldList); // 输出字段列表到控制台
    }

    @Override public Enumerator<Object> getSearchResultEnumerator(String search, // 重写接口方法，获取搜索结果的枚举器
        Map<String, String> otherArgs, @Nullable List<String> fieldList) { // 参数：search搜索语句，otherArgs其他参数，fieldList字段列表（可空）
      throw new UnsupportedOperationException(); // 抛出不支持操作异常，表示此方法不支持
    }
  }
}
