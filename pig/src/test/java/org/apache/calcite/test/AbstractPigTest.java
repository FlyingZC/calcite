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
package org.apache.calcite.test; // 指定当前类所属的包为 org.apache.calcite.test，这是Calcite项目中用于存放测试类的包

import org.apache.calcite.util.Sources; // 导入Calcite工具类Sources，用于处理资源文件路径的转换和获取

import java.net.URL; // 导入Java标准库中的URL类，用于表示统一资源定位符，处理网络或本地资源的地址

import static java.util.Objects.requireNonNull; // 静态导入Objects类的requireNonNull方法，用于对象非空检查，如果对象为null则抛出NullPointerException

/**
 * Common methods inheritable by all Pig-specific test classes.
 * 所有Pig特定测试类可继承的通用方法
 * 这个抽象类为所有与Pig相关的测试类提供通用功能，主要用于处理测试数据文件的路径获取
 * Pig是Apache的一个大数据分析平台，Calcite提供了对Pig的适配器支持，这个类为相关的测试提供基础工具方法
 */ // 类级别的JavaDoc注释结束，描述了该类的用途和功能


public abstract class AbstractPigTest { // 定义一个抽象类AbstractPigTest，专门为Pig相关的测试类提供通用功能，抽象类不能被实例化，只能被继承

  // protected修饰的成员方法，表示该方法可以在子类中访问，也可以在同包的其他类中访问
  // 方法名getFullPathForTestDataFile表示获取测试数据文件的完整路径
  // 参数fileName：String类型，表示要获取路径的测试数据文件的文件名，可以是相对路径
  // 返回值类型String，返回测试数据文件在文件系统中的绝对路径
  protected String getFullPathForTestDataFile(String fileName) { // 开始定义获取测试数据文件完整路径的方法，该方法用于从classpath中加载测试数据文件并返回其绝对路径
    final URL url = getClass().getResource("/" + fileName); // 使用反射获取当前类的Class对象，然后调用getResource方法从classpath根目录（以/开头）加载指定的资源文件，返回一个URL对象表示资源的位置，final修饰表示url引用不可变
    requireNonNull(url, "url"); // 调用requireNonNull方法检查url对象是否为null，如果为null则抛出NullPointerException，异常消息为"url"，确保资源文件存在，避免后续操作出现空指针异常
    return Sources.of(url).file().getAbsolutePath(); // 使用Sources工具类的of方法将URL对象转换为Source对象，然后调用file()方法获取对应的File对象，最后调用getAbsolutePath()方法返回文件在文件系统中的绝对路径字符串，这个路径可以用于文件读取操作
  } // 方法结束，返回测试数据文件的绝对路径
} // 类定义结束