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
package org.apache.calcite.adapter.redis; // 定义包名，表示这个类属于org.apache.calcite.adapter.redis包

/**
 * Redis配置类，用于封装连接Redis服务器所需的配置参数
 * 该类是一个不可变对象（immutable），所有成员变量都是final的，一旦创建就不能修改
 * 主要用于存储Redis服务器的主机地址、端口号、数据库索引和密码等连接信息
 * 在RedisAdapter中创建Redis连接时会使用该配置对象
 */
public class RedisConfig { // Redis配置类，封装了Redis连接所需的所有配置参数
  private final String host; // Redis服务器的主机地址（IP地址或域名），例如"localhost"或"192.168.1.100"
  private final int port; // Redis服务器的端口号，默认为6379，用于建立TCP连接
  private final int database; // Redis数据库索引，Redis支持多个数据库（默认0-15），用于逻辑隔离不同的数据
  private final String password; // Redis服务器的访问密码，如果Redis设置了认证则需要提供，否则可以为null

  // 构造方法，用于创建RedisConfig对象，初始化Redis连接所需的所有配置参数
  public RedisConfig(String host, int port, int database, String password) { // 构造方法：接受主机、端口、数据库索引和密码四个参数
    this.host = host; // 将传入的主机地址赋值给成员变量host，使用final保证不可变
    this.port = port; // 将传入的端口号赋值给成员变量port，使用final保证不可变
    this.database = database; // 将传入的数据库索引赋值给成员变量database，使用final保证不可变
    this.password = password; // 将传入的密码赋值给成员变量password，使用final保证不可变
  }

  // Getter方法：获取Redis服务器的主机地址
  public String getHost() { // 返回Redis服务器的主机地址
    return host; // 返回成员变量host的值
  }

  // Getter方法：获取Redis服务器的端口号
  public int getPort() { // 返回Redis服务器的端口号
    return port; // 返回成员变量port的值
  }

  // Getter方法：获取Redis数据库索引
  public int getDatabase() { // 返回Redis数据库的索引号
    return database; // 返回成员变量database的值
  }

  // Getter方法：获取Redis服务器的访问密码
  public String getPassword() { // 返回Redis服务器的密码
    return password; // 返回成员变量password的值
  }
}
