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
package org.apache.calcite.adapter.geode.rel;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.distributed.AbstractLauncher;
import org.apache.geode.distributed.ServerLauncher;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkState;

import static java.util.Objects.requireNonNull;

/**
 * 使用原生 {@link ServerLauncher} 管理嵌入式 Geode 实例的策略类。
 * 
 * <p>这个类实现了 JUnit 5 的扩展接口，用于在测试生命周期中自动管理 Geode 服务器的启动和停止。
 * 它提供了一个嵌入式 Geode 实例，用于集成测试中，无需手动启动外部 Geode 服务器。
 * 
 * <p>主要功能包括：
 * <ul>
 *   <li>在所有测试方法执行前启动嵌入式 Geode 服务器（通过 beforeAll 方法）</li>
 *   <li>在所有测试方法执行后停止嵌入式 Geode 服务器（通过 afterAll 方法）</li>
 *   <li>提供对 Geode Cache 实例的访问（通过 cache 方法）</li>
 *   <li>支持多个测试类共享同一个 Geode 实例（通过 share 方法）</li>
 * </ul>
 * 
 * <p>这个类使用了 ServerLauncher 来启动 Geode 服务器，配置为：
 * <ul>
 *   <li>使用随机可用端口（避免端口冲突）</li>
 *   <li>绑定到本地回环地址（仅接受本地连接）</li>
 *   <li>最小化日志输出（减少测试噪音）</li>
 *   <li>PDX 序列化配置优化</li>
 * </ul>
 * 
 * @see ServerLauncher
 * @see BeforeAllCallback
 * @see AfterAllCallback
 */
public class GeodeEmbeddedPolicy implements BeforeAllCallback, AfterAllCallback {

  // Geode 服务器启动器，用于控制嵌入式 Geode 实例的生命周期（启动、停止、状态查询等）
  // 这个字段是 final 的，一旦初始化就不能改变，确保了服务器启动器的稳定性
  private final ServerLauncher launcher;

  /**
   * 私有构造方法，用于创建 GeodeEmbeddedPolicy 实例。
   * 
   * <p>这个构造方法接收一个 ServerLauncher 实例，并进行必要的验证：
   * <ul>
   *   <li>确保 launcher 不为 null，否则抛出 NullPointerException</li>
   *   <li>确保 launcher 没有正在运行，否则抛出 IllegalStateException</li>
   * </ul>
   * 
   * <p>这种设计确保了每个 GeodeEmbeddedPolicy 实例都对应一个有效的、未运行的服务器启动器。
   * 
   * @param launcher Geode 服务器启动器实例，不能为 null 且不能已经在运行
   * @throws NullPointerException 如果 launcher 为 null
   * @throws IllegalStateException 如果 launcher 已经在运行
   */
  private GeodeEmbeddedPolicy(final ServerLauncher launcher) {
    this.launcher = requireNonNull(launcher, "launcher");  // 将启动器保存到实例变量，确保不为 null
    checkState(!launcher.isRunning(), "Launcher process is already running");  // 验证启动器没有在运行，避免重复启动
  }

  /**
   * JUnit 5 生命周期回调方法，在所有测试方法执行前被调用。
   * 
   * <p>这个方法用于启动嵌入式 Geode 服务器。在启动前，它会验证服务器当前处于 NOT_RESPONDING 状态，
   * 确保服务器没有被意外启动。然后调用 launcher.start() 启动服务器。
   * 
   * <p>这个方法通常在测试类的 @BeforeAll 注解的方法之前被调用，确保测试环境已经准备好。
   * 
   * @param context JUnit 5 扩展上下文，提供关于当前测试执行的元数据
   * @throws IllegalStateException 如果服务器不在预期的 NOT_RESPONDING 状态
   */
  @Override public void beforeAll(ExtensionContext context) {
    requireStatus(AbstractLauncher.Status.NOT_RESPONDING);  // 确保服务器处于未响应状态，即未启动状态
    launcher.start();  // 启动 Geode 服务器进程
  }

  /**
   * JUnit 5 生命周期回调方法，在所有测试方法执行后被调用。
   * 
   * <p>这个方法用于清理和停止嵌入式 Geode 服务器。执行步骤包括：
   * <ol>
   *   <li>检查服务器是否处于 ONLINE 状态，如果是则关闭 Cache 实例</li>
   *   <li>停止服务器启动器</li>
   *   <li>删除 PID 文件，避免下次启动时因 PID 文件存在而报错</li>
   * </ol>
   * 
   * <p>PID 文件的清理非常重要，因为 Geode 会在下次启动时检查 PID 文件，
   * 如果文件存在且进程 ID 对应的进程仍在运行，会拒绝启动。
   * 
   * @param context JUnit 5 扩展上下文，提供关于当前测试执行的元数据
   */
  @Override public void afterAll(ExtensionContext context) {
    // 检查服务器是否在线，如果在线则关闭 Cache 实例
    if (launcher.status().getStatus() == AbstractLauncher.Status.ONLINE) {
      CacheFactory.getAnyInstance().close();  // 获取并关闭当前 Cache 实例，释放资源
    }

    // 构建 PID 文件路径，文件名固定为 "vf.gf.server.pid"
    final Path pidFile = Paths.get(launcher.getWorkingDirectory()).resolve("vf.gf.server.pid");  // 获取工作目录下的 PID 文件路径
    launcher.stop();  // 停止 Geode 服务器进程

    // 如果 PID 文件仍然存在，则删除它
    if (Files.exists(pidFile)) {
      // 删除 PID 文件。否则（"下一个"）geode 实例会抱怨现有进程
      try {
        Files.delete(pidFile);  // 删除 PID 文件，避免下次启动时出现冲突
      } catch (IOException e) {
        throw new UncheckedIOException(e);  // 将受检异常转换为非受检异常，简化错误处理
      }
    }
  }

  /**
   * 允许此实例被多个测试类（并行）共享。
   * 
   * <p>这个方法返回一个引用计数策略包装器，确保 beforeAll 和 afterAll 方法只会被调用一次。
   * 这种设置对于并发测试执行非常有用，因为并发测试可能会多次初始化（或销毁）相同的资源。
   * 
   * <p>引用计数的工作原理：
   * <ul>
   *   <li>第一个调用者触发 beforeAll（初始化资源）</li>
   *   <li>后续调用者不触发 beforeAll（资源已经初始化）</li>
   *   <li>最后一个调用者触发 afterAll（销毁资源）</li>
   *   <li>其他调用者不触发 afterAll（资源仍然被其他调用者使用）</li>
   * </ul>
   * 
   * <p>这种机制确保了资源的正确共享和清理，避免了重复初始化和过早销毁的问题。
   * 
   * @return 一个新的 RefCountPolicy 实例，包装了当前策略并添加引用计数功能
   */
  GeodeEmbeddedPolicy share() {
    return new RefCountPolicy(this);  // 创建并返回引用计数策略包装器
  }

  /**
   * 返回为测试初始化的当前 Cache 实例。
   * 
   * <p>这个方法提供了一个便捷的方式来访问 Geode Cache 实例，该实例在服务器启动时自动创建。
   * 在返回 Cache 实例之前，它会验证服务器处于 ONLINE 状态，确保服务器已经成功启动。
   * 
   * <p>Cache 是 Geode 的核心数据管理接口，提供了对分布式缓存区域的访问和操作能力。
   * 测试代码可以使用这个 Cache 实例来创建区域、存储数据、执行查询等操作。
   * 
   * @return 当前的 Geode Cache 实例
   * @throws IllegalStateException 如果服务器进程没有启动（状态不是 ONLINE）
   */
  Cache cache() {
    requireStatus(AbstractLauncher.Status.ONLINE);  // 确保服务器处于在线状态
    return CacheFactory.getAnyInstance();  // 获取并返回当前 Cache 实例
  }

  /**
   * 验证服务器处于预期的状态。
   * 
   * <p>这是一个辅助方法，用于确保服务器在执行某些操作前处于正确的状态。
   * 如果当前状态与预期状态不符，会抛出 IllegalStateException 异常。
   * 
   * <p>可能的状态包括：
   * <ul>
   *   <li>NOT_RESPONDING：服务器未启动或无法响应</li>
   *   <li>STARTING：服务器正在启动中</li>
   *   <li>ONLINE：服务器已启动并正常运行</li>
   *   <li>STOPPING：服务器正在停止中</li>
   *   <li>STOPPED：服务器已停止</li>
   * </ul>
   * 
   * @param expected 期望的服务器状态
   * @throws IllegalStateException 如果当前状态与预期状态不符
   */
  private void requireStatus(AbstractLauncher.Status expected) {
    final AbstractLauncher.Status current = launcher.status().getStatus();  // 获取服务器当前状态
    checkState(current == expected,  // 验证当前状态是否等于预期状态
        "Expected state %s but got %s", expected, current);  // 如果状态不匹配，抛出异常并显示期望状态和实际状态
  }

  /**
   * 工厂方法，创建并配置一个新的 GeodeEmbeddedPolicy 实例。
   * 
   * <p>这个方法使用 Builder 模式创建 ServerLauncher 实例，并配置以下参数：
   * <ul>
   *   <li>成员名称：fake-geode（用于标识这个 Geode 成员）</li>
   *   <li>日志文件：空字符串（表示输出到标准输出）</li>
   *   <li>日志级别：severe（只记录严重错误，减少日志噪音）</li>
   *   <li>绑定地址：127.0.0.1（只接受本地连接，提高安全性）</li>
   *   <li>服务器端口：0（自动选择可用端口，避免端口冲突）</li>
   *   <li>PDX 持久化：false（不持久化 PDX 元数据）</li>
   *   <li>PDX 读取序列化：true（优化序列化读取性能）</li>
   * </ul>
   * 
   * <p>PDX (Portable Data eXchange) 是 Geode 的序列化机制，配置为非持久化可以避免元数据文件，
   * 读取序列化可以避免反序列化开销，提高性能。
   * 
   * @return 一个新创建并配置好的 GeodeEmbeddedPolicy 实例
   */
  static GeodeEmbeddedPolicy create() {
    // 使用 Builder 创建 ServerLauncher 实例
    final ServerLauncher launcher  = new ServerLauncher.Builder()
        .setMemberName("fake-geode")  // 设置成员名称为 "fake-geode"，用于标识这个测试服务器
        .set("log-file", "")  // 设置日志文件为空字符串，表示日志输出到标准输出（控制台）
        .set("log-level", "severe")  // 设置日志级别为 severe，只记录严重错误，减少测试日志噪音
        .set("bind-address", "127.0.0.1")  // 设置绑定地址为本地回环地址，只接受本地连接，提高安全性
        .setServerPort(0)  // 设置服务器端口为 0，让系统自动选择可用端口，避免端口冲突
        .setPdxPersistent(false)  // 设置 PDX 持久化为 false，不持久化 PDX 类型元数据到磁盘
        .setPdxReadSerialized(true)  // 设置 PDX 读取序列化为 true，优化序列化数据的读取性能
        .build();  // 构建 ServerLauncher 实例

    return new GeodeEmbeddedPolicy(launcher);  // 使用配置好的 launcher 创建 GeodeEmbeddedPolicy 实例并返回
  }

  /**
   * 引用计数策略类，确保 before() 和 after() 方法只被调用一次（分别对应第一个和最后一个订阅者）。
   * 
   * <p>这个内部类通过计数 {@link #beforeAll(ExtensionContext)} 被调用的次数来确定"客户端"数量。
   * 当计数再次达到零时（即最后一个"客户端"调用了该方法），委托调用 {@link #afterAll(ExtensionContext)}。
   * 
   * <p>这种设计模式称为引用计数（Reference Counting），常用于资源管理和共享场景。
   * 它确保了资源在第一个使用者需要时初始化，在最后一个使用者释放时销毁。
   * 
   * <p>工作流程示例：
   * <pre>
   * 测试类 A 调用 beforeAll -> refCount = 1 -> 初始化资源
   * 测试类 B 调用 beforeAll -> refCount = 2 -> 不初始化
   * 测试类 C 调用 beforeAll -> refCount = 3 -> 不初始化
   * 测试类 A 调用 afterAll  -> refCount = 2 -> 不销毁
   * 测试类 B 调用 afterAll  -> refCount = 1 -> 不销毁
   * 测试类 C 调用 afterAll  -> refCount = 0 -> 销毁资源
   * </pre>
   * 
   * <p>这种机制特别适合并行测试场景，多个测试类可以共享同一个 Geode 实例，
   * 避免了每个测试类都启动和停止服务器的开销。
   */
  private static class RefCountPolicy extends GeodeEmbeddedPolicy {

    // 原子整数引用计数器，用于跟踪当前有多少个测试类正在使用这个策略
    // 使用 AtomicInteger 确保在多线程环境下的线程安全性
    private final AtomicInteger refCount;

    // 委托的原始 GeodeEmbeddedPolicy 实例，实际的资源管理由这个策略执行
    // RefCountPolicy 只负责引用计数，实际的启动和停止操作委托给这个策略
    private final GeodeEmbeddedPolicy policy;

    /**
     * RefCountPolicy 的构造方法。
     * 
     * <p>这个构造方法接收一个 GeodeEmbeddedPolicy 实例，并将其包装为引用计数策略。
     * 它初始化引用计数器为 0，表示当前没有测试类在使用这个策略。
     * 
     * <p>构造方法还调用父类构造方法，传入 launcher 实例，确保父类正确初始化。
     * 
     * @param policy 要包装的 GeodeEmbeddedPolicy 实例，不能为 null
     * @throws NullPointerException 如果 policy 为 null
     */
    RefCountPolicy(final GeodeEmbeddedPolicy policy) {
      super(requireNonNull(policy, "policy").launcher);  // 调用父类构造方法，传入 launcher，确保 policy 不为 null
      this.policy = policy;  // 保存委托策略实例
      this.refCount = new AtomicInteger();  // 初始化引用计数器为 0
    }

    /**
     * 重写 share 方法，返回当前实例本身。
     * 
     * <p>这个方法处理像 share().share() 这样的链式调用场景。
     * 因为 RefCountPolicy 已经提供了引用计数功能，再次调用 share() 不需要创建新的包装器，
     * 直接返回当前实例即可。
     * 
     * @return 当前 RefCountPolicy 实例
     */
    @Override GeodeEmbeddedPolicy share() {
      return this;  // 对于像 share().share() 这样的情况，直接返回当前实例
    }

    /**
     * 重写 beforeAll 方法，使用引用计数确保资源只初始化一次。
     * 
     * <p>这个方法是同步的（synchronized），确保在多线程环境下的线程安全。
     * 它使用原子操作 getAndIncrement() 来原子地获取当前计数并递增。
     * 
     * <p>只有当计数从 0 变为 1 时（即第一个调用者），才会调用委托策略的 beforeAll 方法
     * 来初始化资源。后续调用者只会增加计数，不会触发初始化。
     * 
     * @param context JUnit 5 扩展上下文，提供关于当前测试执行的元数据
     */
    @Override public synchronized void beforeAll(ExtensionContext context) {
      if (refCount.getAndIncrement() == 0) {  // 原子地获取当前计数并递增，如果是 0 则表示第一个调用者
        // 只初始化一次
        policy.beforeAll(context);  // 调用委托策略的 beforeAll 方法，启动 Geode 服务器
      }
    }

    /**
     * 重写 afterAll 方法，使用引用计数确保资源只销毁一次。
     * 
     * <p>这个方法使用原子操作 decrementAndGet() 来原子地递减计数并获取新值。
     * 只有当计数从 1 变为 0 时（即最后一个调用者），才会调用委托策略的 afterAll 方法
     * 来销毁资源。其他调用者只会减少计数，不会触发销毁。
     * 
     * <p>注意：这个方法不是同步的，因为 decrementAndGet() 本身是原子操作，
     * 而且只有在计数达到 0 时才执行销毁操作，不会出现竞态条件。
     * 
     * @param context JUnit 5 扩展上下文，提供关于当前测试执行的元数据
     */
    @Override public void afterAll(ExtensionContext context) {
      if (refCount.decrementAndGet() == 0) {  // 原子地递减计数并获取新值，如果是 0 则表示最后一个调用者
        // 只销毁一次
        policy.afterAll(context);  // 调用委托策略的 afterAll 方法，停止 Geode 服务器
      }
    }
  }
}
