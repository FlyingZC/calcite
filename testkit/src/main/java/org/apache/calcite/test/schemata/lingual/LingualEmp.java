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
package org.apache.calcite.test.schemata.lingual; // 定义包路径，该类位于org.apache.calcite.test.schemata.lingual包下，用于测试schema中的lingual模块

import java.util.Objects; // 导入Java工具类Objects，用于生成哈希码和对象比较

/**
 * Lingual emp model. // Lingual员工模型类，这是一个用于Calcite测试的简单POJO（Plain Old Java Object）类
 * 该类模拟了数据库中的员工表（emp表）的数据模型，用于在Calcite的测试场景中表示员工实体
 * Lingual是Calcite中的一个测试schema，用于演示多语言查询功能
 * 这个类只包含了员工最基本的两个属性：员工编号(EMPNO)和部门编号(DEPTNO)
 * 作为测试模型类，它遵循Java Bean的设计模式，提供了equals和hashCode方法以便于对象比较和集合操作
 */
public class LingualEmp { // 定义LingualEmp类，这是一个不可变（immutable）的员工数据模型类
  public final int EMPNO; // 员工编号（Employee Number），使用public final修饰，表示该字段是公开的且不可修改，遵循不可变对象设计原则
  public final int DEPTNO; // 部门编号（Department Number），使用public final修饰，表示该字段是公开的且不可修改，标识员工所属的部门

  public LingualEmp(int EMPNO, int DEPTNO) { // 构造方法，用于创建LingualEmp实例，初始化员工对象
    this.EMPNO = EMPNO; // 将传入的EMPNO参数赋值给实例变量EMPNO，使用this关键字区分成员变量和参数
    this.DEPTNO = DEPTNO; // 将传入的DEPTNO参数赋值给实例变量DEPTNO，初始化员工所属部门
  }

  @Override public boolean equals(Object obj) { // 重写Object类的equals方法，用于比较两个LingualEmp对象是否相等
    return obj == this // 首先检查obj是否是当前对象本身（引用相等），如果是则直接返回true，避免不必要的后续比较
        || obj instanceof LingualEmp // 如果不是同一个引用，检查obj是否是LingualEmp类的实例，使用instanceof进行类型检查
        && EMPNO == ((LingualEmp) obj).EMPNO; // 如果是LingualEmp实例，比较两个对象的EMPNO字段是否相等，这里只比较员工编号作为唯一标识
  }

  @Override public int hashCode() { // 重写Object类的hashCode方法，用于生成对象的哈希码，确保相等的对象具有相同的哈希码
    return Objects.hash(EMPNO); // 使用Objects工具类的hash方法基于EMPNO字段生成哈希码，保持与equals方法的一致性（只根据EMPNO判断相等）
  }
} // 类定义结束
