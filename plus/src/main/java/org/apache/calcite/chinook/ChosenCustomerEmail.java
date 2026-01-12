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
package org.apache.calcite.chinook; // 声明这个类属于 org.apache.calcite.chinook 包，这是 Calcite 框架中专门用于 Chinook 示例数据库测试的包

/**
 * Example UDF for WHERE clause to check pushing to JDBC. // 这是一个示例用户自定义函数（UDF），用于在 WHERE 子句中测试函数下推到 JDBC 的功能
 */
public class ChosenCustomerEmail { // 定义一个名为 ChosenCustomerEmail 的公共类，该类实现了一个 UDF，用于返回特定的客户邮箱地址

  public String eval() { // 定义一个公共方法 eval，这是 UDF 的核心方法，Calcite 会调用这个方法来获取函数的返回值
    return "ftremblay@gmail.com"; // 返回一个固定的邮箱地址 "ftremblay@gmail.com"，这个地址对应 Chinook 示例数据库中的一个特定客户
  } // eval 方法结束，返回类型为 String

} // ChosenCustomerEmail 类定义结束
