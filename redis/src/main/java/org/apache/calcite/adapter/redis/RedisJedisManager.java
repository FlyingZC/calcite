/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */  // Apache许可证头，声明软件的使用权限和限制
package org.apache.calcite.adapter.redis;  // 当前类所属的包名

import org.apache.calcite.util.trace.CalciteTrace;  // 导入Calcite的日志追踪工具类

import org.apache.commons.lang3.StringUtils;  // 导入Apache Commons Lang3的字符串工具类，用于字符串判空等操作
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;  // 导入通用对象池配置类，用于配置连接池参数

import com.google.common.cache.CacheBuilder;  // 导入Google Guava的缓存构建器，用于构建缓存
import com.google.common.cache.CacheLoader;  // 导入Google Guava的缓存加载器，用于缓存数据的自动加载
import com.google.common.cache.LoadingCache;  // 导入Google Guava的加载缓存接口，支持自动加载的缓存
import com.google.common.cache.RemovalListener;  // 导入Google Guava的缓存移除监听器接口，用于监听缓存元素被移除的事件
import com.google.common.cache.RemovalNotification;  // 导入Google Guava的缓存移除通知类，包含被移除元素的信息

import org.slf4j.Logger;  // 导入SLF4J日志接口，用于记录日志

import redis.clients.jedis.Jedis;  // 导入Jedis客户端类，用于与Redis服务器进行交互
import redis.clients.jedis.JedisPool;  // 导入Jedis连接池类，用于管理Redis连接池
import redis.clients.jedis.JedisPoolConfig;  // 导入Jedis连接池配置类，用于配置连接池参数
import redis.clients.jedis.Protocol;  // 导入Jedis协议类，包含Redis协议的默认常量

import static java.util.Objects.requireNonNull;  // 导入Objects工具类的requireNonNull静态方法，用于参数非空校验

/**
 * Manages connections to the Redis nodes.  // RedisJedisManager类的功能说明：管理到Redis节点的连接
 */  // 这是一个连接管理器类，负责创建、缓存和管理Redis连接池，实现AutoCloseable接口支持自动资源释放
public class RedisJedisManager implements AutoCloseable {  // 类定义：Redis连接管理器，实现AutoCloseable接口以便使用try-with-resources语法
  private static final Logger LOGGER = CalciteTrace.getPlannerTracer();  // 静态日志记录器：使用Calcite的追踪器获取日志实例，用于记录Redis连接管理的日志信息
  private final LoadingCache<String, JedisPool> jedisPoolCache;  // Jedis连接池缓存：使用LoadingCache缓存不同Redis主机的连接池，key是主机名，value是对应的JedisPool对象，支持自动加载和移除监听
  private final JedisPoolConfig jedisPoolConfig;  // Jedis连接池配置：存储连接池的配置参数，包括最大连接数、最大空闲连接数、最小空闲连接数等

  private final String host;  // Redis服务器主机名或IP地址：存储Redis服务器的地址信息
  private final String password;  // Redis服务器密码：存储Redis服务器的认证密码，可能为空
  private final int port;  // Redis服务器端口号：存储Redis服务器的监听端口，默认为6379
  private final int database;  // Redis数据库索引：存储要使用的Redis数据库编号，Redis支持多个数据库，默认为0

  public RedisJedisManager(String host, int port, int database, String password) {  // 构造方法：初始化Redis连接管理器，接收主机、端口、数据库索引和密码参数
    JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();  // 创建Jedis连接池配置对象：用于配置连接池的各项参数
    jedisPoolConfig.setMaxTotal(GenericObjectPoolConfig.DEFAULT_MAX_TOTAL);  // 设置连接池最大连接数：使用通用对象池的默认最大连接数，控制连接池中最大活跃连接数
    jedisPoolConfig.setMaxIdle(GenericObjectPoolConfig.DEFAULT_MAX_IDLE);  // 设置连接池最大空闲连接数：使用通用对象池的默认最大空闲连接数，控制连接池中最大空闲连接数
    jedisPoolConfig.setMinIdle(GenericObjectPoolConfig.DEFAULT_MIN_IDLE);  // 设置连接池最小空闲连接数：使用通用对象池的默认最小空闲连接数，控制连接池中保持的最小空闲连接数
    this.host = host;  // 保存Redis主机地址到成员变量
    this.port = port;  // 保存Redis端口号到成员变量
    this.database = database;  // 保存Redis数据库索引到成员变量
    this.password = password;  // 保存Redis密码到成员变量
    this.jedisPoolConfig = jedisPoolConfig;  // 保存连接池配置到成员变量
    this.jedisPoolCache = CacheBuilder.newBuilder()  // 创建缓存构建器：开始构建LoadingCache对象
        .removalListener(new JedisPoolRemovalListener())  // 设置缓存移除监听器：当缓存元素被移除时，调用JedisPoolRemovalListener来销毁对应的JedisPool，释放资源
        .build(CacheLoader.from(this::createConsumer));  // 构建LoadingCache：使用createConsumer方法作为缓存加载器，当缓存中不存在某个主机的连接池时，自动调用该方法创建
  }  // 构造方法结束

  public JedisPool getJedisPool() {  // 获取Jedis连接池方法：根据主机名获取对应的JedisPool对象
    requireNonNull(host, "host is null");  // 校验主机名非空：如果host为null则抛出NullPointerException，确保连接池获取的安全性
    return jedisPoolCache.getUnchecked(host);  // 从缓存中获取连接池：使用主机名作为key从LoadingCache中获取JedisPool，如果不存在则自动调用createConsumer创建
  }  // 方法结束

  public Jedis getResource() {  // 获取Jedis资源方法：从连接池中获取一个Jedis连接对象
    return getJedisPool().getResource();  // 获取连接池并从中获取Jedis连接：先调用getJedisPool获取连接池，再从连接池中获取一个可用的Jedis连接对象
  }  // 方法结束

  private JedisPool createConsumer() {  // 创建Jedis连接池方法：用于缓存加载器，当缓存中不存在连接池时自动调用此方法创建
    String pwd = password;  // 获取密码副本：将成员变量password赋值给局部变量，用于后续处理
    if (StringUtils.isEmpty(pwd)) {  // 判断密码是否为空：使用StringUtils工具类检查密码是否为空或空字符串
      pwd = null;  // 如果密码为空则设置为null：JedisPool构造方法中，null表示不需要密码认证
    }  // 条件判断结束
    return new JedisPool(jedisPoolConfig, host, port, Protocol.DEFAULT_TIMEOUT,  // 创建并返回Jedis连接池：使用配置对象、主机、端口、默认超时时间、密码和数据库索引创建连接池
        pwd, database);  // 传递密码和数据库索引参数
  }  // 方法结束

  /**
   * JedisPoolRemovalListener for remove elements from cache.  // JedisPoolRemovalListener类的功能说明：用于从缓存中移除元素的监听器
   */  // 这是一个静态内部类，实现了RemovalListener接口，当缓存中的JedisPool被移除时，负责销毁连接池释放资源
  private static class JedisPoolRemovalListener  // 私有静态内部类定义：Jedis连接池移除监听器
      implements RemovalListener<String, JedisPool> {  // 实现RemovalListener接口：泛型参数表示缓存的key是String类型，value是JedisPool类型
    @Override public void onRemoval(  // 重写onRemoval方法：当缓存元素被移除时自动调用此方法
        RemovalNotification<String, JedisPool> notification) {  // 参数：包含被移除元素的详细信息，包括key、value和移除原因
      final JedisPool value = requireNonNull(notification.getValue());  // 获取被移除的JedisPool对象：从通知中获取value并确保非空
      try {  // 开始异常捕获：防止销毁连接池时抛出异常影响程序运行
        value.destroy();  // 销毁Jedis连接池：关闭连接池中的所有连接并释放资源
      } catch (Exception e) {  // 捕获异常：处理销毁过程中可能出现的任何异常
        LOGGER.warn("While destroying JedisPool {}", notification.getKey());  // 记录警告日志：当销毁连接池失败时，记录警告信息，包含被移除的主机key
      }  // 异常捕获结束
    }  // 方法结束
  }  // 内部类结束

  @Override public void close() {  // 重写close方法：实现AutoCloseable接口，用于释放资源，支持try-with-resources语法
    jedisPoolCache.invalidateAll();  // 使缓存中所有元素失效：清空LoadingCache中的所有JedisPool，触发RemovalListener销毁所有连接池
  }  // 方法结束
}  // 类结束
