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
package org.apache.calcite.chinook; // 定义包名，该类属于 org.apache.calcite.chinook 包，用于 Chinook 示例数据库相关功能

/**
 * Fairy simulates environment around Calcite. // 这是一个用于模拟 Calcite 运行环境的工具类（Fairy 表示"仙女"，寓意能够神奇地改变环境）
 *
 * <p>An example property is the user on whose behalf Calcite is running the // 例如，一个属性是代表 Calcite 当前正在为哪个用户执行查询
 * current query. Other properties can change from one query to another. // 其他属性可以在不同的查询之间发生变化
 * Properties are held in thread-locals, so it is safe to set a property then // 属性存储在线程本地变量（ThreadLocal）中，因此在一个线程中设置属性后，
 * read it from the same thread. // 从同一个线程读取该属性是安全的，不会受到其他线程的干扰
 */
public class EnvironmentFairy { // 定义 EnvironmentFairy 类，用于管理 Calcite 的运行环境上下文，使用 ThreadLocal 实现线程隔离

  private static final ThreadLocal<User> USER = // 定义一个静态的 ThreadLocal 变量，用于存储当前线程的用户信息，确保每个线程有独立的用户上下文
      ThreadLocal.withInitial(() -> User.ADMIN); // 使用 ThreadLocal.withInitial() 方法初始化 ThreadLocal，默认值为 User.ADMIN（管理员用户）

  private EnvironmentFairy() { // 私有构造方法，防止外部实例化该类，确保该类只能通过静态方法访问（工具类模式）
  } // 构造方法体为空，因为这是一个纯工具类，不需要实例化

  public static User getUser() { // 定义静态方法 getUser()，用于获取当前线程的用户信息
    return USER.get(); // 返回 ThreadLocal 中存储的当前用户对象，通过 ThreadLocal.get() 方法获取线程本地变量的值
  } // 方法结束，返回当前登录的用户

  public static void login(User user) { // 定义静态方法 login()，用于模拟用户登录操作，设置当前线程的用户信息
    USER.set(user); // 将传入的用户对象设置到 ThreadLocal 中，ThreadLocal.set() 会为当前线程设置独立的用户值
  } // 方法结束，完成用户登录设置

  /**
   * Describes who is emulated to being logged in. // 定义 User 枚举，用于描述模拟登录的用户类型
   */
  public enum User { // 定义 User 枚举类型，包含两种用户角色：管理员和特定用户
    ADMIN, SPECIFIC_USER // ADMIN 表示管理员用户，拥有最高权限；SPECIFIC_USER 表示特定用户，可能具有受限权限
  } // 枚举定义结束

} // 类定义结束
