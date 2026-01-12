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
package org.apache.calcite.chinook; // 定义包名，这个类属于org.apache.calcite.chinook包，chinook是Calcite示例项目中的示例数据库名称

/**
 * Example query for checking query projections. // 这是一个用于检查查询投影的示例查询类
 * StringConcatFunction类演示了如何在Calcite中实现一个自定义的字符串连接函数
 * 该类可以在SQL查询中被调用，用于将两个字符串参数连接起来并返回格式化的结果
 * 这个类通常作为Calcite UDF（用户定义函数）的一个简单示例，展示了如何创建可被SQL查询调用的Java方法
 */
public class StringConcatFunction { // 定义一个公共类StringConcatFunction，表示字符串连接函数

  public String eval(String first, String second) { // 定义一个公共方法eval，接收两个String类型的参数first和second，返回一个String类型的结果，这个方法可以被Calcite在SQL查询中调用
    return "CONCAT = [" + first + "+" + second + "]"; // 返回一个格式化的字符串，将两个参数用"+"连接起来，并用"CONCAT = ["和"]"包裹，例如输入"Hello"和"World"将返回"CONCAT = [Hello+World]"
  } // eval方法结束

} // StringConcatFunction类定义结束
