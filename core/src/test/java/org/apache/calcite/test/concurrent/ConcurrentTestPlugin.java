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
package org.apache.calcite.test.concurrent; // 声明包名，该类属于org.apache.calcite.test.concurrent包，用于并发测试相关功能

import java.util.ArrayList; // 导入ArrayList类，用于存储命令列表

/**
 * Used to extend functionality of mtsql. // ConcurrentTestPlugin是一个抽象类，用于扩展mtsql（多线程SQL测试）的功能
 * 该类定义了并发测试插件的基本接口和默认行为，允许开发者通过继承此类来创建自定义的测试插件
 * 插件可以在并发测试的不同阶段（如设置阶段、线程执行阶段）添加自定义命令和逻辑
 * 这是插件模式的应用，通过抽象类定义插件契约，具体插件实现类可以提供特定的测试功能
 */ // 类级注释：这是一个用于扩展多线程SQL测试功能的抽象插件基类
public abstract class ConcurrentTestPlugin { // 定义抽象类ConcurrentTestPlugin，表示并发测试插件的基类，不能直接实例化，必须被子类继承

  /**
   * Returns whether the containing test should be disabled. // 方法说明：返回包含此插件的测试是否应该被禁用
   *
   * @return true if containing test should be disabled // 返回值说明：如果测试应该被禁用则返回true，否则返回false
   */ // 方法注释：判断测试是否应该被禁用，默认实现返回false表示不禁用
  public boolean isTestDisabled() { // 定义公共方法isTestDisabled，无参数，返回布尔值，用于判断测试是否需要禁用
    return false; // 默认返回false，表示测试不被禁用，子类可以重写此方法来提供自定义的禁用逻辑
  } // 方法结束

  /**
   * What commands are supported by this plugin within // 方法说明：获取此插件在线程或重复部分内支持的命令列表
   * a thread or repeat section. Commands should start with '@'. // 命令应该以'@'符号开头，表示这是插件命令而非普通SQL语句
   *
   * @return List of supported commands // 返回值说明：返回支持的命令名称的可迭代集合，通常是ArrayList
   */ // 方法注释：获取插件在线程执行阶段支持的命令列表，默认返回空列表
  public Iterable<String> getSupportedThreadCommands() { // 定义公共方法getSupportedThreadCommands，无参数，返回字符串可迭代对象
    return new ArrayList<String>(); // 返回一个新的空ArrayList，表示默认不支持任何线程命令，子类可以重写此方法添加支持的命令
  } // 方法结束

  /**
   * What commands are supported by this plugin before // 方法说明：获取此插件在设置阶段之前支持的命令列表
   * the setup section. Commands should start with '@'. // 命令应该以'@'符号开头，表示这是插件命令
   *
   * @return List of supported commands // 返回值说明：返回支持的命令名称的可迭代集合，通常是ArrayList
   */ // 方法注释：获取插件在测试设置阶段之前支持的命令列表，默认返回空列表
  public Iterable<String> getSupportedPreSetupCommands() { // 定义公共方法getSupportedPreSetupCommands，无参数，返回字符串可迭代对象
    return new ArrayList<String>(); // 返回一个新的空ArrayList，表示默认不支持任何前置设置命令，子类可以重写此方法添加支持的命令
  } // 方法结束

  /**
   * Create and return plugin command for given name. // 方法说明：根据给定的命令名称创建并返回对应的插件命令对象
   *
   * @param name   Name of command plugin // 参数说明：name表示插件命令的名称，用于标识要创建哪种类型的命令
   * @param params parameters for command. // 参数说明：params表示命令的参数字符串，包含执行该命令所需的参数信息
   * @return Initialized plugin command. // 返回值说明：返回初始化完成的ConcurrentTestPluginCommand对象，该对象封装了命令的执行逻辑
   */ // 方法注释：抽象方法，根据命令名称和参数创建对应的插件命令对象，子类必须实现此方法
  public abstract ConcurrentTestPluginCommand getCommandFor( // 定义抽象方法getCommandFor，子类必须实现此方法以提供具体的命令创建逻辑
      String name, String params); // 方法参数：name是命令名称，params是命令参数字符串

  /**
   * Do pre-setup action for given command and parameters. // 方法说明：执行给定命令和参数的预设置动作
   *
   * @param name   Name of command plugin // 参数说明：name表示插件命令的名称，标识要执行预设置的命令类型
   * @param params parameters for command. // 参数说明：params表示命令的参数字符串，包含执行预设置所需的参数信息
   */ // 方法注释：执行命令的预设置动作，默认实现为空，子类可以重写此方法来添加自定义的预设置逻辑
  public void preSetupFor(String name, String params) { // 定义公共方法preSetupFor，接受命令名称和参数，无返回值
  } // 方法体为空，表示默认不执行任何预设置动作
} // 类定义结束
