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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证，保留版权信息
package org.apache.calcite.adapter.splunk; // 定义包名，该类位于org.apache.calcite.adapter.splunk包下，是Calcite框架中Splunk适配器的一部分

import org.apache.calcite.avatica.DriverVersion; // 导入Avatica框架的DriverVersion类，SplunkDriverVersion将继承此类来定义驱动版本信息

/**
 * Version information for Calcite JDBC Driver for Splunk.
 */ // 类的JavaDoc文档注释，说明该类用于存储Calcite JDBC驱动程序连接Splunk的版本信息
class SplunkDriverVersion extends DriverVersion { // 定义SplunkDriverVersion类，继承自DriverVersion基类，用于封装Splunk JDBC驱动的版本相关信息
  /** Creates a SplunkDriverVersion. */ // 构造方法的JavaDoc文档注释，说明该构造函数用于创建SplunkDriverVersion实例
  SplunkDriverVersion() { // 无参构造函数，初始化Splunk JDBC驱动的版本信息
    super( // 调用父类DriverVersion的构造函数，传递驱动版本相关的参数
        "Calcite JDBC Driver for Splunk", // 参数1：驱动程序的完整名称，说明这是Calcite框架提供的用于连接Splunk的JDBC驱动
        "0.2", // 参数2：驱动程序的主版本号，当前版本为0.2，表示这是一个早期开发版本
        "Calcite-Splunk", // 参数3：驱动程序的简称或产品名称，用于标识这是Calcite-Splunk适配器
        "0.2", // 参数4：产品版本号，与驱动版本号保持一致，都是0.2
        true, // 参数5：布尔值，表示该驱动程序是否支持JDBC 4.0规范，true表示支持
        0, // 参数6：JDBC主版本号，0表示遵循标准的JDBC规范，通常用于Avatica框架的自定义驱动
        1, // 参数7：JDBC次版本号，1表示JDBC规范的次版本，通常与JDBC主版本号配合使用
        0, // 参数8：数据库主版本号，0表示数据库版本未指定或通用版本
        1); // 参数9：数据库次版本号，1表示数据库次版本，通常与数据库主版本号配合使用
  }
} // 类结束，SplunkDriverVersion类定义完毕，该类主要用于提供Splunk JDBC驱动的版本信息，供驱动管理器使用
