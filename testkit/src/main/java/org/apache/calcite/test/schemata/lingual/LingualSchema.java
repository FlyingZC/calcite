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
package org.apache.calcite.test.schemata.lingual; // 定义包路径，该类位于org.apache.calcite.test.schemata.lingual包下，是Calcite测试框架中Lingual schema的核心类

/**
 * Lingual schema. // Lingual Schema（Lingual模式/架构）类，这是Calcite测试框架中的一个Schema实现类
 * 
 * 【类的作用详解】：
 * 1. Schema（模式/架构）是Calcite中的核心概念，类似于数据库中的database或namespace，用于组织和命名表、视图等数据对象
 * 2. Lingual Schema是Calcite专门用于测试多语言查询功能的测试Schema，它模拟了一个简单的数据库结构
 * 3. 该类通过定义公开的成员变量来模拟数据库中的表结构，Calcite的反射机制可以自动识别这些公开数组作为表
 * 4. 在Calcite中，一个Schema可以包含多个表，每个表通过一个公开的数组或集合字段来表示
 * 5. Lingual Schema特别简单，只包含一个EMPS（员工）表，用于演示和测试Calcite的基本查询功能
 * 6. 这种基于POJO（Plain Old Java Object）的Schema定义方式是Calcite的一种简化配置方式，无需编写复杂的XML配置
 * 7. 测试时，Calcite会通过反射扫描此类，找到所有公开的字段，并将它们注册为Schema中的表
 * 8. 该Schema主要用于单元测试和集成测试，验证Calcite在不同场景下的SQL解析、优化和执行能力
 */
public class LingualSchema { // 定义LingualSchema类，这是一个基于Java POJO的Schema实现，通过公开字段定义表结构
  public final LingualEmp[] EMPS = { // 定义公开的final数组字段EMPS，表示Schema中的员工表（EMPS表），public final修饰确保该表在Schema创建后不可被修改
      new LingualEmp(1, 10), // 创建第一个员工对象，员工编号为1，部门编号为10，代表部门10的1号员工
      new LingualEmp(2, 30)  // 创建第二个员工对象，员工编号为2，部门编号为30，代表部门30的2号员工
  }; // EMPS数组初始化完成，该数组包含两条员工记录，模拟数据库中的员工表数据
} // LingualSchema类定义结束
