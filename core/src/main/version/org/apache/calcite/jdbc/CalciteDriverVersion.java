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
package org.apache.calcite.jdbc;  // 定义包名，该类位于org.apache.calcite.jdbc包下，是Calcite JDBC驱动相关的版本信息类

import org.apache.calcite.avatica.DriverVersion;  // 导入Avatica框架的DriverVersion类，用于表示驱动程序版本信息，Calcite基于Avatica构建

/**
 * Provides information on the current Calcite version.  // 提供当前Calcite版本的信息，该类是一个版本信息持有者，用于存储和管理Calcite JDBC驱动的版本详情
 * 该类的作用：作为Calcite JDBC驱动版本信息的单例持有者，封装了驱动的名称、版本号、产品名称等关键元数据
 * 它继承自Avatica的DriverVersion体系，为Calcite提供标准化的版本信息接口
 * 在构建过程中，版本号会被动态替换为实际的版本号（通过Maven或其他构建工具处理:version占位符）
 */
class CalciteDriverVersion {  // 定义CalciteDriverVersion类，使用默认（包私有）访问修饰符，表示该类仅在org.apache.calcite.jdbc包内可见
  static final DriverVersion INSTANCE =  // 定义一个静态常量INSTANCE，类型为DriverVersion，这是该类的核心成员变量，用于存储唯一的版本信息实例
      new DriverVersion(  // 创建DriverVersion实例，通过构造函数初始化版本信息的各个字段
          "Calcite JDBC Driver",  // 参数1：驱动程序名称，设置为"Calcite JDBC Driver"，标识这是一个Calcite的JDBC驱动
          "1.0.0-unprocessed-SNAPSHOT" /* :version */,  // 参数2：驱动程序版本字符串，初始值为占位符"1.0.0-unprocessed-SNAPSHOT"，构建时会被替换为实际版本号，注释标记为:version供构建工具识别
          "Calcite",  // 参数3：产品名称，设置为"Calcite"，表示该驱动属于Calcite产品家族
          "1.0.0-unprocessed-SNAPSHOT" /* :version */,  // 参数4：产品版本字符串，同样使用占位符，构建时会被替换为实际版本号，与驱动版本保持一致
          true,  // 参数5：布尔值，设置为true，表示该驱动是JDBC兼容的，支持JDBC标准接口
          1 /* :major */,  // 参数6：驱动程序主版本号，初始值为1，注释标记为:major供构建工具动态替换
          0 /* :minor */,  // 参数7：驱动程序次版本号，初始值为0，注释标记为:minor供构建工具动态替换
          1 /* :major */,  // 参数8：产品主版本号，初始值为1，与驱动主版本号保持一致，注释标记为:major供构建工具动态替换
          0 /* :minor */);  // 参数9：产品次版本号，初始值为0，与驱动次版本号保持一致，注释标记为:minor供构建工具动态替换

  private CalciteDriverVersion() {  // 定义私有构造方法，防止外部实例化该类，确保INSTANCE是唯一的实例
  }  // 构造方法体为空，因为该类不需要实例化，所有版本信息都通过静态常量INSTANCE提供
}  // 类定义结束
