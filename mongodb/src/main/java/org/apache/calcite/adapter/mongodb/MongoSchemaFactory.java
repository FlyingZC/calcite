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
 */ // Apache许可证声明，表明该代码遵循Apache 2.0开源协议
package org.apache.calcite.adapter.mongodb; // 声明该类所属的包，位于org.apache.calcite.adapter.mongodb包下，这是Calcite MongoDB适配器的包

import org.apache.calcite.schema.Schema; // 导入Calcite核心Schema接口，表示数据库模式的抽象
import org.apache.calcite.schema.SchemaFactory; // 导入SchemaFactory接口，用于创建Schema实例的工厂接口
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，是Schema的扩展接口，允许添加子Schema

import com.mongodb.AuthenticationMechanism; // 导入MongoDB认证机制枚举类，支持多种认证方式如PLAIN、SCRAM等
import com.mongodb.ConnectionString; // 导入MongoDB连接字符串类，用于解析和构建MongoDB连接URL
import com.mongodb.MongoClientSettings; // 导入MongoDB客户端设置类，用于配置MongoDB客户端的各种参数
import com.mongodb.MongoCredential; // 导入MongoDB凭据类，用于存储MongoDB连接的认证信息

import java.util.Map; // 导入Map接口，用于存储键值对集合，这里用于接收配置参数

/**
 * Factory that creates a {@link MongoSchema}. // 工厂类，用于创建MongoSchema实例，MongoSchema是MongoDB数据库模式的Calcite表示
 *
 * <p>Allows a custom schema to be included in a model.json file. // 允许将自定义schema包含在model.json配置文件中，这样Calcite就可以通过配置文件自动加载MongoDB schema
 */ // 该类实现了SchemaFactory接口，是Calcite适配器架构的一部分，负责根据配置创建MongoDB的Schema对象
public class MongoSchemaFactory implements SchemaFactory { // 定义MongoSchemaFactory类，实现SchemaFactory接口，遵循工厂模式
  // public constructor, per factory contract // 公共无参构造函数，符合SchemaFactory接口的契约要求，Calcite通过反射调用此构造函数
  public MongoSchemaFactory() { // 无参构造函数，不需要任何初始化参数，因为所有配置都通过create方法传入
  } // 构造函数体为空，因为工厂类不需要维护状态

  @Override public Schema create(SchemaPlus parentSchema, String name, // 重写SchemaFactory接口的create方法，创建MongoSchema实例，parentSchema是父Schema，name是当前Schema名称，operand是配置参数Map
      Map<String, Object> operand) { // operand参数包含从model.json文件传入的配置信息，如host、database、authMechanism等
    final String host = (String) operand.get("host"); // 从配置参数中获取MongoDB服务器地址，通常是mongodb://localhost:27017格式的连接字符串
    final String database = (String) operand.get("database"); // 从配置参数中获取要连接的数据库名称，这是要映射到Calcite的MongoDB数据库
    final String authMechanismName = (String) operand.get("authMechanism"); // 从配置参数中获取认证机制名称，如PLAIN、SCRAM_SHA_1、SCRAM_SHA_256等

    final MongoClientSettings.Builder settings = // 创建MongoClientSettings构建器，用于构建MongoDB客户端的配置对象
        MongoClientSettings // 调用MongoClientSettings的builder静态方法获取构建器实例
            .builder() // 创建新的Builder实例
            .applyConnectionString(new ConnectionString(host)); // 应用连接字符串，解析host参数并设置到MongoClientSettings中，包含服务器地址、端口等信息

    if (authMechanismName != null) { // 如果配置中指定了认证机制名称，则需要配置认证凭据
      settings.credential(createCredential(operand)); // 调用createCredential方法创建认证凭据，并设置到settings中，用于MongoDB连接认证
    } // 如果没有指定认证机制，则使用匿名连接

    return new MongoSchema(settings.build(), database); // 构建MongoClientSettings实例，并创建MongoSchema对象返回，MongoSchema封装了MongoDB连接和数据库信息
  } // create方法结束，返回创建的MongoSchema实例

  private static MongoCredential createCredential(Map<String, Object> map) { // 私有静态方法，根据配置参数创建MongoCredential认证凭据对象，支持多种MongoDB认证机制
    final String authMechanismName = (String) map.get("authMechanism"); // 从配置Map中获取认证机制名称，用于确定使用哪种认证方式
    final AuthenticationMechanism authenticationMechanism = // 将认证机制名称转换为AuthenticationMechanism枚举对象，MongoDB支持的枚举值包括PLAIN、SCRAM_SHA_1、SCRAM_SHA_256、GSSAPI、MONGODB_X509等
        AuthenticationMechanism.fromMechanismName(authMechanismName); // 根据字符串名称查找对应的AuthenticationMechanism枚举值
    final String username = (String) map.get("username"); // 从配置Map中获取用户名，用于MongoDB认证
    final String authDatabase = (String) map.get("authDatabase"); // 从配置Map中获取认证数据库名称，MongoDB认证时需要指定认证数据库，通常是admin
    final String password = (String) map.get("password"); // 从配置Map中获取密码，用于需要密码的认证机制

    switch (authenticationMechanism) { // 根据认证机制类型，使用不同的方法创建MongoCredential对象
    case PLAIN: // PLAIN认证机制，使用明文用户名和密码认证，通常用于内部网络或SSL加密连接
      return MongoCredential.createPlainCredential(username, authDatabase, // 创建PLAIN认证凭据，需要用户名、认证数据库和密码
          password.toCharArray()); // 将密码转换为字符数组，符合MongoCredential的API要求
    case SCRAM_SHA_1: // SCRAM_SHA_1认证机制，使用SHA-1哈希算法的Salted Challenge Response Authentication Mechanism，是MongoDB 3.0+的默认认证方式
      return MongoCredential.createScramSha1Credential(username, authDatabase, // 创建SCRAM_SHA_1认证凭据，需要用户名、认证数据库和密码
          password.toCharArray()); // 将密码转换为字符数组，用于SCRAM哈希计算
    case SCRAM_SHA_256: // SCRAM_SHA_256认证机制，使用更安全的SHA-256哈希算法，是MongoDB 4.0+推荐的认证方式
      return MongoCredential.createScramSha256Credential(username, authDatabase, // 创建SCRAM_SHA_256认证凭据，需要用户名、认证数据库和密码
          password.toCharArray()); // 将密码转换为字符数组，用于SCRAM-SHA-256哈希计算
    case GSSAPI: // GSSAPI认证机制，使用Kerberos协议进行认证，通常用于企业级环境，需要Kerberos服务器支持
      return MongoCredential.createGSSAPICredential(username); // 创建GSSAPI认证凭据，只需要用户名，密码由Kerberos管理
    case MONGODB_X509: // MONGODB_X509认证机制，使用X.509证书进行认证，是最安全的认证方式，需要配置SSL/TLS和客户端证书
      return MongoCredential.createMongoX509Credential(username); // 创建X.509认证凭据，只需要用户名（通常是证书中的主题DN）
    default: // 默认分支，如果传入的认证机制不在上述支持范围内，则执行此分支
      break; // 跳出switch语句
    } // switch语句结束
    throw new IllegalArgumentException("Unsupported authentication mechanism " // 抛出非法参数异常，表示不支持该认证机制
        + authMechanismName); // 在异常信息中包含不支持的认证机制名称，方便调试
  } // createCredential方法结束，返回创建的MongoCredential对象
} // MongoSchemaFactory类定义结束
