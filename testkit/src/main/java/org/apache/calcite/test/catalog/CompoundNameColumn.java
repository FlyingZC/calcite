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
package org.apache.calcite.test.catalog; // 包声明：该类属于org.apache.calcite.test.catalog包，是Calcite测试工具包中用于目录测试的一部分

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型，是Calcite中描述列类型的核心接口

/** Column having names with multiple parts. */ // 类文档注释：表示具有多部分名称的列，例如"schema.table"或"catalog.schema.table"这样的复合名称结构
final class CompoundNameColumn { // CompoundNameColumn类：用于表示具有复合名称的列，final类表示不可被继承，确保列名称结构的稳定性
  final String first; // 成员变量first：表示复合名称的第一部分，通常是schema名称或catalog名称，final确保不可变
  final String second; // 成员变量second：表示复合名称的第二部分，通常是table名称或column名称，final确保不可变
  final RelDataType type; // 成员变量type：表示该列的数据类型，使用Calcite的RelDataType类型系统，final确保不可变

  CompoundNameColumn(String first, String second, RelDataType type) { // 构造方法：创建一个新的CompoundNameColumn实例，初始化复合名称的各个部分和数据类型
    this.first = first; // 将传入的first参数赋值给成员变量first，存储复合名称的第一部分
    this.second = second; // 将传入的second参数赋值给成员变量second，存储复合名称的第二部分
    this.type = type; // 将传入的type参数赋值给成员变量type，存储该列的数据类型信息
  } // 构造方法结束

  String getName() { // getName方法：获取该列的完整复合名称，返回格式化的字符串表示
    return (first.isEmpty() ? "" : ("\"" + first + "\".")) // 如果first部分不为空，则用双引号包裹并加上点号分隔符，例如"schema."
        + ("\"" + second + "\""); // 将second部分用双引号包裹，例如"table"，最终返回类似"schema.table"或"table"的格式
  } // getName方法结束
} // CompoundNameColumn类结束
