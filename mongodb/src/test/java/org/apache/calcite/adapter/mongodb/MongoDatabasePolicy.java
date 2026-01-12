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
// 声明包名，表示该类属于org.apache.calcite.adapter.mongodb包，这是Calcite框架中MongoDB适配器的测试包
package org.apache.calcite.adapter.mongodb;

// 导入MongoAssertions类，用于判断当前测试环境应该使用真实的MongoDB还是模拟的MongoDB
import org.apache.calcite.test.MongoAssertions;
// 导入Closer工具类，用于统一管理多个Closeable资源的关闭，确保资源被正确释放
import org.apache.calcite.util.Closer;

// 导入MongoDB连接字符串类，用于构建MongoDB的连接URL
import com.mongodb.ConnectionString;
// 导入MongoDB客户端设置类，用于配置MongoClient的连接参数
import com.mongodb.MongoClientSettings;
// 导入MongoDB客户端接口，定义了与MongoDB服务器交互的基本方法
import com.mongodb.client.MongoClient;
// 导入MongoClients工厂类，用于创建MongoClient实例
import com.mongodb.client.MongoClients;
// 导入MongoDatabase接口，代表MongoDB中的一个数据库实例
import com.mongodb.client.MongoDatabase;

// 导入JUnit5的AfterAllCallback接口，用于在所有测试方法执行完成后执行清理操作
import org.junit.jupiter.api.extension.AfterAllCallback;
// 导入ExtensionContext接口，提供测试扩展的上下文信息
import org.junit.jupiter.api.extension.ExtensionContext;

// 导入Java的Closeable接口，用于标记需要关闭的资源
import java.io.Closeable;
// 导入InetSocketAddress类，用于表示IP地址和端口号
import java.net.InetSocketAddress;

// 导入第三方库MongoServer类，这是一个内存中的MongoDB服务器实现，用于测试
import de.bwaldvogel.mongo.MongoServer;
// 导入MemoryBackend类，这是MongoServer的内存后端实现，数据存储在内存中
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;

// 静态导入Objects.requireNonNull方法，用于检查对象是否为null，如果为null则抛出NullPointerException
import static java.util.Objects.requireNonNull;

/**
 * Instantiates a new connection to a embedded (but fake) or real mongo database
 * depending on current profile (unit or integration tests).
 * // 类的中文说明：这是一个MongoDB数据库策略类，用于根据当前的Maven配置文件（profile）创建一个新的MongoDB数据库连接
 * // 该类支持两种模式：1. 单元测试模式：使用嵌入式的内存MongoDB服务器（fake）进行测试
 * //                   2. 集成测试模式：连接到真实的MongoDB服务器实例
 * // 这样设计的好处是单元测试可以快速执行，不依赖外部MongoDB服务，而集成测试可以验证与真实MongoDB的兼容性
 *
 * <p>By default, this rule is executed as part of a unit test and
 * <a href="https://github.com/bwaldvogel/mongo-java-server">in-memory database</a> is used.
 * // 默认情况下，该规则作为单元测试的一部分执行，使用内存数据库（mongo-java-server）
 * // 内存数据库是基于Java实现的MongoDB服务器模拟器，数据存储在内存中，测试结束后数据会自动清除
 * // 这种方式非常适合单元测试，因为它不需要安装和配置真实的MongoDB服务器
 *
 * <p>However, if the maven profile is set to {@code IT} (eg. via command line
 * {@code $ mvn -Pit install}) this rule will connect to an existing (external)
 * Mongo instance ({@code localhost}).
 * // 但是，如果Maven的profile设置为IT（集成测试），例如通过命令行执行 $ mvn -Pit install
 * // 该规则将连接到一个现有的（外部的）MongoDB实例（默认为localhost）
 * // 这样可以在真实的MongoDB环境中运行测试，验证适配器在实际环境中的行为
 */
// 类定义：MongoDatabasePolicy实现了JUnit5的AfterAllCallback接口，作为测试扩展使用
// 实现AfterAllCallback接口意味着可以在所有测试方法执行完成后执行清理操作（如关闭数据库连接）
class MongoDatabasePolicy implements AfterAllCallback {

  // 定义数据库名称常量，所有测试都将使用名为"test"的数据库
  // 使用常量的好处是便于统一管理和修改数据库名称
  private static final String DB_NAME = "test";

  // 声明MongoDatabase实例变量，用于表示MongoDB数据库连接
  // 使用final修饰符表示该引用在构造函数初始化后不可再改变，保证线程安全性
  // 通过该实例可以执行数据库操作，如创建集合、插入文档、查询数据等
  private final MongoDatabase database;
  // 声明Closer实例变量，用于统一管理需要关闭的资源
  // Closer是Calcite工具类，可以注册多个Closeable对象，并在关闭时按相反顺序依次关闭
  // 使用final修饰符确保closer引用在构造函数初始化后不可变
  private final Closer closer;

  // 私有构造函数，防止外部直接实例化，强制使用静态工厂方法create()创建对象
  // 参数client：MongoClient客户端实例，用于与MongoDB服务器通信
  // 参数closer：Closer资源管理器，用于统一关闭所有资源
  private MongoDatabasePolicy(MongoClient client, Closer closer) {
    // 检查client参数是否为null，如果为null则抛出NullPointerException，错误信息为"client"
    // 使用requireNonNull是防御性编程的最佳实践，可以快速发现空指针错误
    // 注意：这里赋值给client1变量但实际上没有使用，可能是遗留代码
    MongoClient client1 = requireNonNull(client, "client");
    // 通过client获取指定名称的数据库实例，数据库名称由DB_NAME常量指定（"test"）
    // getDatabase方法不会立即连接数据库，而是返回一个数据库对象，操作时才会连接
    // 将获取的数据库实例赋值给成员变量database，供后续方法使用
    this.database = client.getDatabase(DB_NAME);
    // 检查closer参数是否为null，如果为null则抛出NullPointerException，错误信息为"closer"
    // 确保closer不为null，否则后续调用closer.add()会抛出空指针异常
    this.closer = requireNonNull(closer, "closer");
    // 将MongoClient客户端添加到Closer资源管理器中，以便在测试结束后自动关闭
    // Closer会跟踪所有注册的资源，在调用close()方法时按相反顺序依次关闭
    // 这样可以确保MongoClient连接被正确释放，避免资源泄漏
    closer.add(client);
  }

  // 实现AfterAllCallback接口的方法，在所有测试方法执行完成后自动调用
  // 参数context：ExtensionContext对象，提供测试扩展的上下文信息（本方法未使用）
  // 该方法用于清理测试过程中创建的资源，如关闭数据库连接
  @Override public void afterAll(ExtensionContext context) {
    // 调用Closer的close()方法，关闭所有注册的资源
    // Closer会按照资源注册的相反顺序依次关闭每个资源
    // 例如，如果先注册了MongoClient，后注册了MongoServer，则会先关闭MongoServer，再关闭MongoClient
    // 这样可以确保依赖关系正确处理（先关闭依赖方，再关闭被依赖方）
    closer.close();
  }

  /**
   * Creates an instance based on current maven profile (as defined by {@code -Pit}).
   * // 方法说明：根据当前的Maven profile配置创建MongoDatabasePolicy实例
   // 如果使用-Pit参数（集成测试profile），则连接真实的MongoDB服务器
   // 否则使用内存中的模拟MongoDB服务器进行单元测试
   // 这种设计使得同一套测试代码可以在不同环境中运行，提高测试灵活性
   *
   * @return new instance of the policy to be used by unit tests
   * // 返回值说明：返回新创建的MongoDatabasePolicy实例，该实例包含MongoDatabase数据库对象和Closer资源管理器
   */
  // 静态工厂方法，用于创建MongoDatabasePolicy实例
  // 使用静态工厂方法而不是公共构造函数的好处是：1. 可以封装复杂的创建逻辑
  // 2. 可以返回子类实例 3. 可以缓存实例 4. 方法名可以更清晰地表达创建意图
  static MongoDatabasePolicy create() {
    // 声明MongoClient客户端变量，用于存储创建的MongoClient实例
    // 使用final修饰符表示该变量只能赋值一次，保证线程安全性和代码可读性
    final MongoClient client;
    // 创建Closer资源管理器实例，用于统一管理所有需要关闭的资源
    // Closer会跟踪所有注册的Closeable对象，并在close()时自动关闭
    final Closer closer = new Closer();
    // 判断当前测试环境是否应该使用真实的MongoDB服务器
    // MongoAssertions.useMongo()会检查系统属性或环境变量，确定是否使用真实MongoDB
    if (MongoAssertions.useMongo()) {
      // 使用真实的MongoDB客户端，连接到默认的MongoDB实例
      // MongoClients.create()使用默认配置创建客户端，默认连接到localhost:27017
      // 这种方式适用于集成测试，可以验证与真实MongoDB的兼容性
      client = MongoClients.create();
    // 如果不使用真实MongoDB，则判断是否应该使用模拟的MongoDB服务器
    // MongoAssertions.useFake()会检查是否使用内存中的MongoDB模拟器
    } else if (MongoAssertions.useFake()) {
      // 创建内存MongoDB服务器实例，使用MemoryBackend作为后端存储
      // MemoryBackend将数据存储在内存中，不持久化到磁盘，测试结束后数据自动清除
      // MongoServer是一个完全用Java实现的MongoDB协议兼容服务器
      final MongoServer server = new MongoServer(new MemoryBackend());
      // 绑定服务器到一个随机可用的端口，返回绑定的地址信息
      // server.bind()会自动选择一个可用的端口号，避免端口冲突
      // InetSocketAddress包含IP地址（127.0.0.1）和端口号
      final InetSocketAddress address = server.bind();

      // 将MongoServer的关闭操作注册到Closer中
      // server::shutdownNow是一个方法引用，相当于实现了Closeable接口的close()方法
      // 调用shutdownNow()会立即关闭MongoServer，释放所有资源
      // 使用类型转换(Closeable)是因为Closer.add()要求参数是Closeable类型
      closer.add((Closeable) server::shutdownNow);
      // 创建MongoClient实例，连接到内存MongoDB服务器
      // 使用MongoClientSettings.builder()构建器模式配置连接参数
      // applyConnectionString()设置连接字符串，格式为mongodb://host:port
      // address.getPort()获取server绑定的端口号
      // build()完成配置构建，返回MongoClientSettings对象
      // MongoClients.create()根据配置创建MongoClient实例
      client =
              MongoClients.create(MongoClientSettings
                      .builder()
                      .applyConnectionString(
                               new ConnectionString("mongodb://127.0.0.1:" + address.getPort())
                      )
                      .build());
    // 如果既不使用真实MongoDB也不使用模拟MongoDB，则抛出不支持操作异常
    } else {
      // 抛出UnsupportedOperationException异常，表示当前配置不支持
      // 异常消息说明只能连接到MongoDB或Fake实例
      // 这是一个防御性编程实践，确保所有可能的配置都被正确处理
      throw new UnsupportedOperationException("I can only connect to Mongo or Fake instances");
    }

    // 返回新创建的MongoDatabasePolicy实例
    // 将创建的MongoClient和Closer传递给构造函数
    // 构造函数会初始化database成员变量，并将client注册到closer中
    return new MongoDatabasePolicy(client, closer);
  }

  // 获取MongoDatabase实例的方法，包级私有访问权限（default）
  // 该方法提供给测试类使用，用于获取数据库对象以执行数据库操作
  // 方法名使用database()而不是getDatabase()，遵循简洁命名原则
  MongoDatabase database() {
    // 返回MongoDatabase实例，测试类可以使用该实例执行查询、插入、更新等操作
    return database;
  }
}
