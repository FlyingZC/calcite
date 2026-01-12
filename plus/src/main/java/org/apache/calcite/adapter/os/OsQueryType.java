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
// Apache许可证声明，说明代码的授权和使用条款
package org.apache.calcite.adapter.os; // 声明包名，该类属于org.apache.calcite.adapter.os包，用于操作系统适配器功能

import java.util.List; // 导入Java集合框架中的List接口，用于存储对象数组列表

// 静态导入OsQueryTableUtil工具类的各个方法，方便直接调用而无需类名前缀
import static org.apache.calcite.adapter.utils.OsQueryTableUtil.getCpuInfo; // 静态导入获取CPU信息的方法
import static org.apache.calcite.adapter.utils.OsQueryTableUtil.getCpuTimeInfo; // 静态导入获取CPU时间信息的方法
import static org.apache.calcite.adapter.utils.OsQueryTableUtil.getInterfaceAddressesInfo; // 静态导入获取网络接口地址信息的方法
import static org.apache.calcite.adapter.utils.OsQueryTableUtil.getInterfaceDetailsInfo; // 静态导入获取网络接口详细信息的方法
import static org.apache.calcite.adapter.utils.OsQueryTableUtil.getJavaInfo; // 静态导入获取Java运行环境信息的方法
import static org.apache.calcite.adapter.utils.OsQueryTableUtil.getMemoryInfo; // 静态导入获取内存信息的方法
import static org.apache.calcite.adapter.utils.OsQueryTableUtil.getMountsInfo; // 静态导入获取挂载点信息的方法
import static org.apache.calcite.adapter.utils.OsQueryTableUtil.getOsVersionInfo; // 静态导入获取操作系统版本信息的方法
import static org.apache.calcite.adapter.utils.OsQueryTableUtil.getSystemInfo; // 静态导入获取系统信息的方法

/**
 * Get system enumeration information.
 * 获取系统枚举信息，这是一个枚举类，定义了各种可以查询的操作系统信息类型
 * 每个枚举常量代表一种系统信息类型，并通过抽象方法getInfo()获取对应的数据
 * 该类作为Calcite查询引擎的操作系统适配器的一部分，允许通过SQL查询系统状态信息
 */
public enum OsQueryType { // 定义一个枚举类，包含各种操作系统查询类型，每个类型对应不同的系统信息
  SYSTEM_INFO { // 定义枚举常量SYSTEM_INFO，表示系统基本信息，如主机名、操作系统类型、启动时间等
    @Override public List<Object[]> getInfo() { // 重写抽象方法getInfo()，获取系统基本信息
      return getSystemInfo(); // 调用OsQueryTableUtil工具类的getSystemInfo()方法，返回系统信息列表
    } // 方法结束
  }, // 枚举常量SYSTEM_INFO定义结束
  JAVA_INFO { // 定义枚举常量JAVA_INFO，表示Java虚拟机信息，如Java版本、JVM参数等
    @Override public List<Object[]> getInfo() { // 重写抽象方法getInfo()，获取Java虚拟机信息
      return getJavaInfo(); // 调用OsQueryTableUtil工具类的getJavaInfo()方法，返回Java信息列表
    } // 方法结束
  }, // 枚举常量JAVA_INFO定义结束
  OS_VERSION { // 定义枚举常量OS_VERSION，表示操作系统版本信息，如名称、版本号、架构等
    @Override public List<Object[]> getInfo() { // 重写抽象方法getInfo()，获取操作系统版本信息
      return getOsVersionInfo(); // 调用OsQueryTableUtil工具类的getOsVersionInfo()方法，返回OS版本信息列表
    } // 方法结束
  }, // 枚举常量OS_VERSION定义结束
  MEMORY_INFO { // 定义枚举常量MEMORY_INFO，表示内存使用信息，如总内存、可用内存、缓存等
    @Override public List<Object[]> getInfo() { // 重写抽象方法getInfo()，获取内存使用信息
      return getMemoryInfo(); // 调用OsQueryTableUtil工具类的getMemoryInfo()方法，返回内存信息列表
    } // 方法结束
  }, // 枚举常量MEMORY_INFO定义结束
  CPU_INFO { // 定义枚举常量CPU_INFO，表示CPU硬件信息，如CPU型号、核心数、频率等
    @Override public List<Object[]> getInfo() { // 重写抽象方法getInfo()，获取CPU硬件信息
      return getCpuInfo(); // 调用OsQueryTableUtil工具类的getCpuInfo()方法，返回CPU信息列表
    } // 方法结束
  }, // 枚举常量CPU_INFO定义结束
  CPU_TIME { // 定义枚举常量CPU_TIME，表示CPU时间统计信息，如用户态时间、系统态时间、空闲时间等
    @Override public List<Object[]> getInfo() { // 重写抽象方法getInfo()，获取CPU时间统计信息
      return getCpuTimeInfo(); // 调用OsQueryTableUtil工具类的getCpuTimeInfo()方法，返回CPU时间信息列表
    } // 方法结束
  }, // 枚举常量CPU_TIME定义结束
  INTERFACE_ADDRESSES { // 定义枚举常量INTERFACE_ADDRESSES，表示网络接口地址信息，如IP地址、子网掩码等
    @Override public List<Object[]> getInfo() { // 重写抽象方法getInfo()，获取网络接口地址信息
      return getInterfaceAddressesInfo(); // 调用OsQueryTableUtil工具类的getInterfaceAddressesInfo()方法，返回接口地址信息列表
    } // 方法结束
  }, // 枚举常量INTERFACE_ADDRESSES定义结束
  INTERFACE_DETAILS { // 定义枚举常量INTERFACE_DETAILS，表示网络接口详细信息，如MAC地址、MTU、状态等
    @Override public List<Object[]> getInfo() { // 重写抽象方法getInfo()，获取网络接口详细信息
      return getInterfaceDetailsInfo(); // 调用OsQueryTableUtil工具类的getInterfaceDetailsInfo()方法，返回接口详细信息列表
    } // 方法结束
  }, // 枚举常量INTERFACE_DETAILS定义结束
  MOUNTS { // 定义枚举常量MOUNTS，表示磁盘挂载点信息，如设备名称、挂载点、文件系统类型等
    @Override public List<Object[]> getInfo() { // 重写抽象方法getInfo()，获取磁盘挂载点信息
      return getMountsInfo(); // 调用OsQueryTableUtil工具类的getMountsInfo()方法，返回挂载点信息列表
    } // 方法结束
  }; // 枚举常量MOUNTS定义结束，所有枚举常量定义完成

  public abstract List<Object[]> getInfo(); // 声明抽象方法getInfo()，返回对象数组列表，每个枚举常量必须实现此方法以获取对应的系统信息
} // 枚举类OsQueryType定义结束
