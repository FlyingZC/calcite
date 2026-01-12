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
// Apache许可证头部声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.rel.externalize; // 定义包路径，该类位于org.apache.calcite.rel.externalize包下，用于关系表达式外部化相关功能

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型，用于描述表或查询结果的类型信息
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，关系数据类型工厂，用于创建各种关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField接口，表示关系数据类型的字段，包含字段名、索引和类型信息
import org.apache.calcite.rel.type.RelDataTypeFieldImpl; // 导入RelDataTypeFieldImpl实现类，RelDataTypeField接口的具体实现
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入RelDataTypeSystem接口，关系数据类型系统，定义类型系统的规则和限制
import org.apache.calcite.sql.type.SqlTypeFactoryImpl; // 导入SqlTypeFactoryImpl类，SQL类型工厂的实现类，用于创建SQL类型
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义所有SQL标准类型名称（如VARCHAR、INTEGER等）
import org.apache.calcite.test.DiffRepository; // 导入DiffRepository类，差异仓库，用于测试中比较期望值和实际值
import org.apache.calcite.util.JsonBuilder; // 导入JsonBuilder类，JSON构建器，用于构建JSON对象

import org.junit.jupiter.api.Test; // 导入Test注解，JUnit 5的测试注解，标记测试方法

/**
 * Unit tests for @{@link RelJson}.
 */
// 类的JavaDoc注释：说明这是RelJson类的单元测试，RelJson是用于将关系表达式序列化为JSON格式的工具类
public class RelJsonTest { // 定义测试类RelJsonTest，用于测试RelJson类的功能

  // 静态常量REPO：差异仓库实例，通过DiffRepository.lookup方法获取，用于管理测试用例的期望输出和实际输出的比较
  // DiffRepository会自动从资源文件中加载期望的测试结果，并与实际测试结果进行对比
  private static final DiffRepository REPO =  DiffRepository.lookup(RelJsonTest.class); // 查找并获取当前测试类的差异仓库实例

  // 测试方法：测试将包含结构化关系数据类型的字段转换为JSON格式
  // 该方法验证RelJson能够正确处理复杂的数据类型（包含多个子字段的结构体类型）的序列化
  @Test void testToJsonWithStructRelDatatypeField() { // 定义测试方法，使用@Test注解标记为JUnit测试方法
    // 创建关系数据类型工厂实例，使用默认的关系数据类型系统（RelDataTypeSystem.DEFAULT）
    // SqlTypeFactoryImpl是RelDataTypeFactory接口的实现，负责创建各种SQL类型
    RelDataTypeFactory typeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 实例化类型工厂，使用默认类型系统
    // 使用类型工厂的builder方法创建一个结构化类型，该类型包含三个字段：
    // 1. street：VARCHAR类型，最大长度50
    // 2. number：INTEGER类型
    // 3. building：VARCHAR类型，最大长度20，且可为空（nullable）
    // 这种结构化类型类似于数据库中的复合类型或嵌套表结构
    RelDataType type = typeFactory.builder() // 获取类型构建器，开始构建结构化类型
        .add("street", SqlTypeName.VARCHAR, 50) // 添加street字段，VARCHAR(50)，表示街道名称
        .add("number", SqlTypeName.INTEGER) // 添加number字段，INTEGER类型，表示门牌号
        .add("building", SqlTypeName.VARCHAR, 20).nullable(true) // 添加building字段，VARCHAR(20)，可为空，表示建筑物名称
        .build(); // 构建完成，返回RelDataType对象
    // 创建关系数据类型字段实例，表示一个名为"address"的字段
    // 参数说明：
    // - "address"：字段名称
    // - 0：字段索引（在结构体中的位置，从0开始）
    // - type：字段的数据类型（前面构建的包含street、number、building的结构化类型）
    RelDataTypeField address = // 创建address字段对象
        new RelDataTypeFieldImpl("address", 0, type); // 使用RelDataTypeFieldImpl实现类创建字段实例

    // 创建JSON构建器实例，用于构建和序列化JSON对象
    // JsonBuilder是Calcite提供的JSON工具类，用于简化JSON的构建过程
    JsonBuilder builder = new JsonBuilder(); // 实例化JSON构建器
    // 将address字段转换为JSON对象
    // 步骤解析：
    // 1. RelJson.create()：创建RelJson实例，RelJson是关系表达式到JSON的转换器
    // 2. withJsonBuilder(builder)：设置JSON构建器，用于构建JSON对象
    // 3. toJson(address)：将RelDataTypeField对象转换为JSON格式
    // 返回的jsonObj是一个JSON对象，包含address字段及其所有子字段的结构化表示
    Object jsonObj = RelJson.create().withJsonBuilder(builder).toJson(address); // 执行JSON转换，将address字段转为JSON对象
    // 将JSON对象转换为字符串并与期望值进行比较
    // 参数说明：
    // - "content"：差异仓库中的键名，用于标识这个测试用例
    // - "${content}"：占位符，表示从差异仓库中读取期望的JSON字符串
    // - builder.toJsonString(jsonObj)：将JSON对象序列化为字符串，这是实际生成的结果
    // DiffRepository会自动查找资源文件中对应的期望值，并与实际值进行对比
    // 如果不匹配，测试会失败并显示差异信息
    REPO.assertEquals("content", "${content}", builder.toJsonString(jsonObj)); // 断言实际JSON字符串与期望值相等
  } // 测试方法结束

} // 类定义结束
