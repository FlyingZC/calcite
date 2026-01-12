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
package org.apache.calcite.test.schemata.hr; // 包声明：该类位于org.apache.calcite.test.schemata.hr包下，用于测试HR（人力资源）相关的schema

import org.apache.calcite.schema.QueryableTable; // 导入QueryableTable接口：表示可查询的表，支持通过Linq4j进行查询
import org.apache.calcite.schema.TranslatableTable; // 导入TranslatableTable接口：表示可转换为关系代数表达式的表
import org.apache.calcite.util.Smalls; // 导入Smalls工具类：提供测试用的辅助方法和数据生成工具

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList：不可变列表，用于创建安全的集合

import java.util.Arrays; // 导入Arrays类：提供数组操作的工具方法
import java.util.Collections; // 导入Collections类：提供集合操作的工具方法

/**
 * A schema that contains two tables by reflection.
 * 这是一个通过反射机制包含两个表的schema类
 *
 * <p>Here is the SQL to create equivalent tables in Oracle:
 * 下面是在Oracle中创建等效表的SQL语句：
 *
 * <blockquote>
 * <pre>
 * CREATE TABLE "emps" (
 *   "empid" INTEGER NOT NULL,
 *   "deptno" INTEGER NOT NULL,
 *   "name" VARCHAR(10) NOT NULL,
 *   "salary" NUMBER(6, 2) NOT NULL,
 *   "commission" INTEGER);
 * INSERT INTO "emps" VALUES (100, 10, 'Bill', 10000, 1000);
 * INSERT INTO "emps" VALUES (200, 20, 'Eric', 8000, 500);
 * INSERT INTO "emps" VALUES (150, 10, 'Sebastian', 7000, null);
 * INSERT INTO "emps" VALUES (110, 10, 'Theodore', 11500, 250);
 *
 * CREATE TABLE "depts" (
 *   "deptno" INTEGER NOT NULL,
 *   "name" VARCHAR(10) NOT NULL,
 *   "employees" ARRAY OF "Employee",
 *   "location" "Location");
 * INSERT INTO "depts" VALUES (10, 'Sales', null, (-122, 38));
 * INSERT INTO "depts" VALUES (30, 'Marketing', null, (0, 52));
 * INSERT INTO "depts" VALUES (40, 'HR', null, null);
 * </pre>
 * </blockquote>
 */
public class HrSchema { // HrSchema类：用于测试的人力资源Schema，包含员工、部门等表数据，通过反射机制被Calcite识别为数据库schema
  @Override public String toString() { // 重写toString方法：返回schema的字符串表示
    return "HrSchema"; // 返回schema名称字符串"HrSchema"
  }

  public final Employee[] emps = { // emps成员变量：员工数组，模拟数据库中的员工表，包含4条员工记录
      new Employee(100, 10, "Bill", 10000, 1000), // 创建Employee对象：员工ID=100，部门ID=10，姓名=Bill，薪资=10000，佣金=1000
      new Employee(200, 20, "Eric", 8000, 500), // 创建Employee对象：员工ID=200，部门ID=20，姓名=Eric，薪资=8000，佣金=500
      new Employee(150, 10, "Sebastian", 7000, null), // 创建Employee对象：员工ID=150，部门ID=10，姓名=Sebastian，薪资=7000，佣金=null
      new Employee(110, 10, "Theodore", 11500, 250), // 创建Employee对象：员工ID=110，部门ID=10，姓名=Theodore，薪资=11500，佣金=250
  };
  public final Department[] depts = { // depts成员变量：部门数组，模拟数据库中的部门表，包含3条部门记录
      new Department(10, "Sales", Arrays.asList(emps[0], emps[2]), // 创建Department对象：部门ID=10，名称=Sales，员工列表包含Bill和Sebastian
          new Location(-122, 38)), // 位置对象：经度=-122，纬度=38（旧金山附近）
      new Department(30, "Marketing", ImmutableList.of(), new Location(0, 52)), // 创建Department对象：部门ID=30，名称=Marketing，员工列表为空，位置=(0,52)
      new Department(40, "HR", Collections.singletonList(emps[1]), null), // 创建Department对象：部门ID=40，名称=HR，员工列表包含Eric，位置=null
  };
  public final Dependent[] dependents = { // dependents成员变量：受抚养人数组，模拟数据库中的受抚养人表
      new Dependent(10, "Michael"), // 创建Dependent对象：员工ID=10，受抚养人姓名=Michael
      new Dependent(10, "Jane"), // 创建Dependent对象：员工ID=10，受抚养人姓名=Jane
  };
  public final Dependent[] locations = { // locations成员变量：位置数组，模拟数据库中的位置表（注意这里复用了Dependent类）
      new Dependent(10, "San Francisco"), // 创建Dependent对象：位置ID=10，位置名称=San Francisco
      new Dependent(20, "San Diego"), // 创建Dependent对象：位置ID=20，位置名称=San Diego
  };

  public QueryableTable foo(int count) { // foo方法：生成一个可查询的字符串表，用于测试，参数count指定生成的字符串数量
    return Smalls.generateStrings(count); // 调用Smalls工具类的generateStrings方法，生成包含count个字符串的可查询表
  }

  public TranslatableTable view(String s) { // view方法：创建一个可转换的视图表，用于测试视图功能，参数s是视图的SQL定义
    return Smalls.view(s); // 调用Smalls工具类的view方法，根据SQL字符串s创建一个可转换的视图表
  }
}
