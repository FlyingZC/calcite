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
package org.apache.calcite.adapter.file; // 声明包名，表示这个类属于 org.apache.calcite.adapter.file 包，是 Calcite 文件适配器模块的一部分

/**
 * Indicates that the FileReader failed. // 表示 FileReader 读取失败时抛出的异常类，用于标识文件读取操作中的错误情况
 */
public class FileReaderException extends Exception { // 定义 FileReaderException 类，继承自 Exception，表示这是一个受检异常类，用于处理文件读取相关的错误
  FileReaderException(String message) { // 构造方法：接收错误消息字符串，创建一个只包含错误消息的 FileReaderException 异常实例
    super(message); // 调用父类 Exception 的构造方法，将错误消息传递给父类，设置异常的详细信息
  }
  FileReaderException(String message, Throwable e) { // 构造方法：接收错误消息字符串和原始异常对象，创建一个包含错误消息和原始异常的 FileReaderException 异常实例
    super(message, e); // 调用父类 Exception 的构造方法，将错误消息和原始异常传递给父类，实现异常链，便于追踪根本原因
  }
} // 类定义结束
