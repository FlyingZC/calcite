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
package org.apache.calcite.test.enumerable; // 声明包名，该测试类位于org.apache.calcite.test.enumerable包下

import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入EnumerableRules类，包含可枚举适配器的规则定义
import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入ReflectiveSchema类，用于通过反射创建数据库schema
import org.apache.calcite.config.CalciteConnectionProperty; // 导入CalciteConnectionProperty类，用于配置Calcite连接属性
import org.apache.calcite.config.Lex; // 导入Lex类，用于配置词法分析策略
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，代表关系表达式优化器
import org.apache.calcite.runtime.Hook; // 导入Hook类，用于在特定执行点插入自定义逻辑
import org.apache.calcite.test.CalciteAssert; // 导入CalciteAssert类，用于测试断言和验证
import org.apache.calcite.test.schemata.hr.HrSchemaBig; // 导入HrSchemaBig类，提供大型人力资源测试schema

import org.junit.jupiter.api.Test; // 导入Test注解，标记测试方法

import java.util.function.Consumer; // 导入Consumer函数式接口，用于消费RelOptPlanner对象

/**
 * Unit test for // 单元测试类，用于测试
 * {@link org.apache.calcite.adapter.enumerable.EnumerableMergeUnion}. // EnumerableMergeUnion操作符，该操作符用于合并多个已排序的输入集
 * 该类专门测试EnumerableMergeUnion（可枚举合并联合）操作符的功能和正确性
 * EnumerableMergeUnion是一个优化技术，当UNION的输入都已按相同的顺序排序时，
 * 可以使用归并算法来高效地合并结果，而不需要先合并再排序
 * 测试场景包括：UNION ALL与UNION的区别、不同的排序字段、NULL值处理、LIMIT/OFFSET优化等
 */
class EnumerableMergeUnionTest { // 定义测试类，类名为EnumerableMergeUnionTest

  @Test void mergeUnionAllOrderByEmpid() { // 测试方法：测试UNION ALL操作后按empid升序排序的场景，使用EnumerableMergeUnion优化
    tester(false, // 调用tester方法创建测试环境，第一个参数forceDecorrelate=false表示不强制去关联
        new HrSchemaBig(), // 使用大型人力资源schema作为测试数据源
        "select * from (select empid, name from emps where name like 'G%' union all select empid, name from emps where name like '%l') order by empid") // SQL查询：从emps表中选择name以'G'开头的员工和name以'l'结尾的员工，使用UNION ALL合并所有结果（包括重复行），然后按empid升序排序
        .explainContains("EnumerableMergeUnion(all=[true])\n" // 验证执行计划包含EnumerableMergeUnion节点，all=[true]表示保留重复行
            + "  EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 第一个输入分支：按第0列（empid）升序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['G%'], expr#6=[LIKE($t2, $t5)], empid=[$t0], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'G'开头的记录，选择empid和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：从emps表读取数据
            + "  EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 第二个输入分支：按第0列（empid）升序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['%l'], expr#6=[LIKE($t2, $t5)], empid=[$t0], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'l'结尾的记录，选择empid和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：从emps表读取数据
        .returnsOrdered( // 验证查询结果按指定顺序返回
            "empid=1; name=Bill", // 期望结果第1行：empid=1, name=Bill（来自第二个查询，name以'l'结尾）
            "empid=6; name=Guy", // 期望结果第2行：empid=6, name=Guy（来自第一个查询，name以'G'开头）
            "empid=10; name=Gabriel", // 期望结果第3行：empid=10, name=Gabriel（来自第一个查询，name以'G'开头）
            "empid=10; name=Gabriel", // 期望结果第4行：empid=10, name=Gabriel（来自第二个查询，name以'l'结尾，注意重复行被保留因为是UNION ALL）
            "empid=12; name=Paul", // 期望结果第5行：empid=12, name=Paul（来自第二个查询，name以'l'结尾）
            "empid=29; name=Anibal", // 期望结果第6行：empid=29, name=Anibal（来自第二个查询，name以'l'结尾）
            "empid=40; name=Emmanuel", // 期望结果第7行：empid=40, name=Emmanuel（来自第二个查询，name以'l'结尾）
            "empid=45; name=Pascal"); // 期望结果第8行：empid=45, name=Pascal（来自第二个查询，name以'l'结尾）
  }

  @Test void mergeUnionOrderByEmpid() { // 测试方法：测试UNION操作后按empid升序排序的场景，使用EnumerableMergeUnion优化，注意与UNION ALL的区别是去重
    tester(false, // 调用tester方法创建测试环境，第一个参数forceDecorrelate=false表示不强制去关联
        new HrSchemaBig(), // 使用大型人力资源schema作为测试数据源
        "select * from (select empid, name from emps where name like 'G%' union select empid, name from emps where name like '%l') order by empid") // SQL查询：从emps表中选择name以'G'开头的员工和name以'l'结尾的员工，使用UNION合并结果（自动去重），然后按empid升序排序
        .explainContains("EnumerableMergeUnion(all=[false])\n" // 验证执行计划包含EnumerableMergeUnion节点，all=[false]表示去除重复行
            + "  EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 第一个输入分支：按第0列（empid）升序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['G%'], expr#6=[LIKE($t2, $t5)], empid=[$t0], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'G'开头的记录，选择empid和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：从emps表读取数据
            + "  EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 第二个输入分支：按第0列（empid）升序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['%l'], expr#6=[LIKE($t2, $t5)], empid=[$t0], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'l'结尾的记录，选择empid和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：从emps表读取数据
        .returnsOrdered( // 验证查询结果按指定顺序返回
            "empid=1; name=Bill", // 期望结果第1行：empid=1, name=Bill（来自第二个查询，name以'l'结尾）
            "empid=6; name=Guy", // 期望结果第2行：empid=6, name=Guy（来自第一个查询，name以'G'开头）
            "empid=10; name=Gabriel", // 期望结果第3行：empid=10, name=Gabriel（注意只有一条，重复行被去除因为是UNION）
            "empid=12; name=Paul", // 期望结果第4行：empid=12, name=Paul（来自第二个查询，name以'l'结尾）
            "empid=29; name=Anibal", // 期望结果第5行：empid=29, name=Anibal（来自第二个查询，name以'l'结尾）
            "empid=40; name=Emmanuel", // 期望结果第6行：empid=40, name=Emmanuel（来自第二个查询，name以'l'结尾）
            "empid=45; name=Pascal"); // 期望结果第7行：empid=45, name=Pascal（来自第二个查询，name以'l'结尾）
  }

  @Test void mergeUnionAllOrderByName() { // 测试方法：测试UNION ALL操作后按name升序排序的场景，验证按字符串字段排序的MergeUnion功能
    tester(false, // 调用tester方法创建测试环境，第一个参数forceDecorrelate=false表示不强制去关联
        new HrSchemaBig(), // 使用大型人力资源schema作为测试数据源
        "select * from (select empid, name from emps where name like 'G%' union all select empid, name from emps where name like '%l') order by name") // SQL查询：从emps表中选择name以'G'开头的员工和name以'l'结尾的员工，使用UNION ALL合并所有结果，然后按name升序排序
        .explainContains("EnumerableMergeUnion(all=[true])\n" // 验证执行计划包含EnumerableMergeUnion节点，all=[true]表示保留重复行
            + "  EnumerableSort(sort0=[$1], dir0=[ASC])\n" // 第一个输入分支：按第1列（name）升序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['G%'], expr#6=[LIKE($t2, $t5)], empid=[$t0], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'G'开头的记录，选择empid和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：从emps表读取数据
            + "  EnumerableSort(sort0=[$1], dir0=[ASC])\n" // 第二个输入分支：按第1列（name）升序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['%l'], expr#6=[LIKE($t2, $t5)], empid=[$t0], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'l'结尾的记录，选择empid和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：从emps表读取数据
        .returnsOrdered( // 验证查询结果按name升序返回
            "empid=29; name=Anibal", // 期望结果第1行：name=Anibal（字母顺序A开头）
            "empid=1; name=Bill", // 期望结果第2行：name=Bill（字母顺序B开头）
            "empid=40; name=Emmanuel", // 期望结果第3行：name=Emmanuel（字母顺序E开头）
            "empid=10; name=Gabriel", // 期望结果第4行：name=Gabriel（字母顺序G开头，来自第一个查询）
            "empid=10; name=Gabriel", // 期望结果第5行：name=Gabriel（字母顺序G开头，来自第二个查询，重复行被保留）
            "empid=6; name=Guy", // 期望结果第6行：name=Guy（字母顺序G开头）
            "empid=45; name=Pascal", // 期望结果第7行：name=Pascal（字母顺序P开头）
            "empid=12; name=Paul"); // 期望结果第8行：name=Paul（字母顺序P开头）
  }

  @Test void mergeUnionOrderByName() { // 测试方法：测试UNION操作后按name升序排序的场景，验证按字符串字段排序且去重的MergeUnion功能
    tester(false, // 调用tester方法创建测试环境，第一个参数forceDecorrelate=false表示不强制去关联
        new HrSchemaBig(), // 使用大型人力资源schema作为测试数据源
        "select * from (select empid, name from emps where name like 'G%' union select empid, name from emps where name like '%l') order by name") // SQL查询：从emps表中选择name以'G'开头的员工和name以'l'结尾的员工，使用UNION合并结果（自动去重），然后按name升序排序
        .explainContains("EnumerableMergeUnion(all=[false])\n" // 验证执行计划包含EnumerableMergeUnion节点，all=[false]表示去除重复行
            + "  EnumerableSort(sort0=[$1], dir0=[ASC])\n" // 第一个输入分支：按第1列（name）升序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['G%'], expr#6=[LIKE($t2, $t5)], empid=[$t0], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'G'开头的记录，选择empid和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：从emps表读取数据
            + "  EnumerableSort(sort0=[$1], dir0=[ASC])\n" // 第二个输入分支：按第1列（name）升序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['%l'], expr#6=[LIKE($t2, $t5)], empid=[$t0], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'l'结尾的记录，选择empid和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：从emps表读取数据
        .returnsOrdered( // 验证查询结果按name升序返回
            "empid=29; name=Anibal", // 期望结果第1行：name=Anibal（字母顺序A开头）
            "empid=1; name=Bill", // 期望结果第2行：name=Bill（字母顺序B开头）
            "empid=40; name=Emmanuel", // 期望结果第3行：name=Emmanuel（字母顺序E开头）
            "empid=10; name=Gabriel", // 期望结果第4行：name=Gabriel（字母顺序G开头，注意只有一条，重复行被去除）
            "empid=6; name=Guy", // 期望结果第5行：name=Guy（字母顺序G开头）
            "empid=45; name=Pascal", // 期望结果第6行：name=Pascal（字母顺序P开头）
            "empid=12; name=Paul"); // 期望结果第7行：name=Paul（字母顺序P开头）
  }

  @Test void mergeUnionSingleColumnOrderByName() { // 测试方法：测试UNION操作后对单列（只有name字段）进行排序的场景，验证单列排序的MergeUnion功能
    tester(false, // 调用tester方法创建测试环境，第一个参数forceDecorrelate=false表示不强制去关联
        new HrSchemaBig(), // 使用大型人力资源schema作为测试数据源
        "select * from (select name from emps where name like 'G%' union select name from emps where name like '%l') order by name") // SQL查询：从emps表中选择name以'G'开头的员工和name以'l'结尾的员工，只选择name字段，使用UNION合并结果（自动去重），然后按name升序排序
        .explainContains("EnumerableMergeUnion(all=[false])\n" // 验证执行计划包含EnumerableMergeUnion节点，all=[false]表示去除重复行
            + "  EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 第一个输入分支：按第0列（name）升序排序，注意这里只有一列所以是$0
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['G%'], expr#6=[LIKE($t2, $t5)], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'G'开头的记录，只选择name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：从emps表读取数据
            + "  EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 第二个输入分支：按第0列（name）升序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['%l'], expr#6=[LIKE($t2, $t5)], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'l'结尾的记录，只选择name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：从emps表读取数据
        .returnsOrdered( // 验证查询结果按name升序返回
            "name=Anibal", // 期望结果第1行：name=Anibal（字母顺序A开头）
            "name=Bill", // 期望结果第2行：name=Bill（字母顺序B开头）
            "name=Emmanuel", // 期望结果第3行：name=Emmanuel（字母顺序E开头）
            "name=Gabriel", // 期望结果第4行：name=Gabriel（字母顺序G开头）
            "name=Guy", // 期望结果第5行：name=Guy（字母顺序G开头）
            "name=Pascal", // 期望结果第6行：name=Pascal（字母顺序P开头）
            "name=Paul"); // 期望结果第7行：name=Paul（字母顺序P开头）
  }

  @Test void mergeUnionOrderByNameWithLimit() { // 测试方法：测试UNION操作后按name排序并使用LIMIT限制返回行数的场景，验证LimitSort优化
    tester(false, // 调用tester方法创建测试环境，第一个参数forceDecorrelate=false表示不强制去关联
        new HrSchemaBig(), // 使用大型人力资源schema作为测试数据源
        "select * from (select empid, name from emps where name like 'G%' union select empid, name from emps where name like '%l') order by name limit 3") // SQL查询：从emps表中选择name以'G'开头和name以'l'结尾的员工，使用UNION合并结果，按name升序排序，并只返回前3行
        .explainContains("EnumerableLimit(fetch=[3])\n" // 验证执行计划顶层是EnumerableLimit节点，fetch=[3]表示只获取3行
            + "  EnumerableMergeUnion(all=[false])\n" // 验证执行计划包含EnumerableMergeUnion节点，all=[false]表示去除重复行
            + "    EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], name=[$t2])\n" // 第一个输入分支的计算节点：只选择empid和name字段
            + "      EnumerableLimitSort(sort0=[$2], dir0=[ASC], fetch=[3])\n" // 第一个输入分支使用LimitSort优化：按第2列（name）升序排序，并只取前3行（注意：这里每个分支取前3行，然后MergeUnion再合并去重）
            + "        EnumerableCalc(expr#0..4=[{inputs}], expr#5=['G%'], expr#6=[LIKE($t2, $t5)], proj#0..4=[{exprs}], $condition=[$t6])\n" // 计算节点：过滤name以'G'开头的记录，投影所有字段
            + "          EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：从emps表读取数据
            + "    EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], name=[$t2])\n" // 第二个输入分支的计算节点：只选择empid和name字段
            + "      EnumerableLimitSort(sort0=[$2], dir0=[ASC], fetch=[3])\n" // 第二个输入分支使用LimitSort优化：按第2列（name）升序排序，并只取前3行
            + "        EnumerableCalc(expr#0..4=[{inputs}], expr#5=['%l'], expr#6=[LIKE($t2, $t5)], proj#0..4=[{exprs}], $condition=[$t6])\n" // 计算节点：过滤name以'l'结尾的记录，投影所有字段
            + "          EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：从emps表读取数据
        .returnsOrdered( // 验证查询结果按name升序返回，且只返回前3行
            "empid=29; name=Anibal", // 期望结果第1行：name=Anibal（字母顺序A开头）
            "empid=1; name=Bill", // 期望结果第2行：name=Bill（字母顺序B开头）
            "empid=40; name=Emmanuel"); // 期望结果第3行：name=Emmanuel（字母顺序E开头），只返回3行
  }

  @Test void mergeUnionOrderByNameWithOffset() { // 测试方法：测试UNION操作后按name排序并使用OFFSET跳过前几行的场景，验证OFFSET功能

      tester(false, // 调用tester方法创建测试环境，第一个参数forceDecorrelate=false表示不强制去关联

          new HrSchemaBig(), // 使用大型人力资源schema作为测试数据源

          "select * from (select empid, name from emps where name like 'G%' union select empid, name from emps where name like '%l') order by name offset 2") // SQL查询：从emps表中选择name以'G'开头和name以'l'结尾的员工，使用UNION合并结果，按name升序排序，并跳过前2行

          .explainContains("EnumerableLimit(offset=[2])\n" // 验证执行计划顶层是EnumerableLimit节点，offset=[2]表示跳过前2行

              + "  EnumerableMergeUnion(all=[false])\n" // 验证执行计划包含EnumerableMergeUnion节点，all=[false]表示去除重复行

              + "    EnumerableSort(sort0=[
  ], dir0=[ASC])\n" // 第一个输入分支：按第1列（name）升序排序

              + "      EnumerableCalc(expr#0..4=[{inputs}], expr#5=['G%'], expr#6=[LIKE($t2, $t5)], empid=[$t0], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'G'开头的记录，选择empid和name字段

              + "        EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：从emps表读取数据

              + "    EnumerableSort(sort0=[
  ], dir0=[ASC])\n" // 第二个输入分支：按第1列（name）升序排序

              + "      EnumerableCalc(expr#0..4=[{inputs}], expr#5=['%l'], expr#6=[LIKE($t2, $t5)], empid=[$t0], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'l'结尾的记录，选择empid和name字段

              + "      EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：从emps表读取数据

          .returnsOrdered( // 验证查询结果按name升序返回，跳过前2行

              "empid=40; name=Emmanuel", // 期望结果第1行（实际是第3行）：name=Emmanuel，跳过了Anibal和Bill

              "empid=10; name=Gabriel", // 期望结果第2行（实际是第4行）：name=Gabriel

              "empid=6; name=Guy", // 期望结果第3行（实际是第5行）：name=Guy

              "empid=45; name=Pascal", // 期望结果第4行（实际是第6行）：name=Pascal

              "empid=12; name=Paul"); // 期望结果第5行（实际是第7行）：name=Paul

    }

  @Test void mergeUnionOrderByNameWithLimitAndOffset() { // 测试方法：测试UNION操作后按name排序并同时使用LIMIT和OFFSET的场景，验证分页功能
    tester(false, // 调用tester方法创建测试环境，第一个参数forceDecorrelate=false表示不强制去关联
        new HrSchemaBig(), // 使用大型人力资源schema作为测试数据源
        "select * from (select empid, name from emps where name like 'G%' union select empid, name from emps where name like '%l') order by name limit 3 offset 2") // SQL查询：从emps表中选择name以'G'开头和name以'l'结尾的员工，使用UNION合并结果，按name升序排序，跳过前2行，然后返回3行
        .explainContains("EnumerableLimit(offset=[2], fetch=[3])\n" // 验证执行计划顶层是EnumerableLimit节点，offset=[2]表示跳过前2行，fetch=[3]表示获取3行
            + "  EnumerableMergeUnion(all=[false])\n" // 验证执行计划包含EnumerableMergeUnion节点，all=[false]表示去除重复行
            + "    EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], name=[$t2])\n" // 第一个输入分支的计算节点：只选择empid和name字段
            + "      EnumerableLimitSort(sort0=[$2], dir0=[ASC], fetch=[5])\n" // 第一个输入分支使用LimitSort优化：按第2列（name）升序排序，fetch=[5]表示每个分支取前5行（优化器计算：offset(2) + fetch(3) = 5，确保能获取到足够的数据）
            + "        EnumerableCalc(expr#0..4=[{inputs}], expr#5=['G%'], expr#6=[LIKE($t2, $t5)], proj#0..4=[{exprs}], $condition=[$t6])\n" // 计算节点：过滤name以'G'开头的记录，投影所有字段
            + "          EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：从emps表读取数据
            + "    EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], name=[$t2])\n" // 第二个输入分支的计算节点：只选择empid和name字段
            + "      EnumerableLimitSort(sort0=[$2], dir0=[ASC], fetch=[5])\n" // 第二个输入分支使用LimitSort优化：按第2列（name）升序排序，fetch=[5]表示每个分支取前5行
            + "        EnumerableCalc(expr#0..4=[{inputs}], expr#5=['%l'], expr#6=[LIKE($t2, $t5)], proj#0..4=[{exprs}], $condition=[$t6])\n" // 计算节点：过滤name以'l'结尾的记录，投影所有字段
            + "          EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：从emps表读取数据
        .returnsOrdered( // 验证查询结果按name升序返回，跳过前2行后返回3行
            "empid=40; name=Emmanuel", // 期望结果第1行（实际是第3行）：name=Emmanuel，跳过了Anibal和Bill
            "empid=10; name=Gabriel", // 期望结果第2行（实际是第4行）：name=Gabriel
            "empid=6; name=Guy"); // 期望结果第3行（实际是第5行）：name=Guy，只返回3行
  }

  @Test void mergeUnionAllOrderByCommissionAscNullsFirstAndNameDesc() { // 测试方法：测试UNION ALL操作后按多字段排序（commission升序且NULL值在前，name降序）的场景
    tester(false, // 调用tester方法创建测试环境，第一个参数forceDecorrelate=false表示不强制去关联
        new HrSchemaBig(), // 使用大型人力资源schema作为测试数据源
        "select * from (select commission, name from emps where name like 'R%' union all select commission, name from emps where name like '%y%') order by commission asc nulls first, name desc") // SQL查询：从emps表中选择name以'R'开头和name包含'y'的员工，使用UNION ALL合并所有结果，按commission升序排序（NULL值排在最前），然后按name降序排序
        .explainContains("EnumerableMergeUnion(all=[true])\n" // 验证执行计划包含EnumerableMergeUnion节点，all=[true]表示保留重复行
            + "  EnumerableSort(sort0=[$0], sort1=[$1], dir0=[ASC-nulls-first], dir1=[DESC])\n" // 第一个输入分支：按第0列（commission）升序排序且NULL值在前，按第1列（name）降序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['R%'], expr#6=[LIKE($t2, $t5)], commission=[$t4], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'R'开头的记录，选择commission和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：从emps表读取数据
            + "  EnumerableSort(sort0=[$0], sort1=[$1], dir0=[ASC-nulls-first], dir1=[DESC])\n" // 第二个输入分支：按第0列（commission）升序排序且NULL值在前，按第1列（name）降序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['%y%'], expr#6=[LIKE($t2, $t5)], commission=[$t4], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name包含'y'的记录，选择commission和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：从emps表读取数据
        .returnsOrdered( // 验证查询结果按指定排序规则返回
            "commission=null; name=Taylor", // 期望结果第1行：commission为NULL，name=Taylor（NULL值排在最前，name降序）
            "commission=null; name=Riyad", // 期望结果第2行：commission为NULL，name=Riyad
            "commission=null; name=Riyad", // 期望结果第3行：commission为NULL，name=Riyad（重复行被保留因为是UNION ALL）
            "commission=null; name=Ralf", // 期望结果第4行：commission为NULL，name=Ralf
            "commission=250; name=Seohyun", // 期望结果第5行：commission=250，name=Seohyun（250是第一个非NULL值，name降序）
            "commission=250; name=Hyuna", // 期望结果第6行：commission=250，name=Hyuna
            "commission=250; name=Andy", // 期望结果第7行：commission=250，name=Andy
            "commission=500; name=Kylie", // 期望结果第8行：commission=500，name=Kylie
            "commission=500; name=Guy"); // 期望结果第9行：commission=500，name=Guy
  }

  @Test void mergeUnionOrderByCommissionAscNullsFirstAndNameDesc() { // 测试方法：测试UNION操作后按多字段排序（commission升序且NULL值在前，name降序）的场景，验证去重功能
    tester(false, // 调用tester方法创建测试环境，第一个参数forceDecorrelate=false表示不强制去关联
        new HrSchemaBig(), // 使用大型人力资源schema作为测试数据源
        "select * from (select commission, name from emps where name like 'R%' union select commission, name from emps where name like '%y%') order by commission asc nulls first, name desc") // SQL查询：从emps表中选择name以'R'开头和name包含'y'的员工，使用UNION合并结果（自动去重），按commission升序排序（NULL值排在最前），然后按name降序排序
        .explainContains("EnumerableMergeUnion(all=[false])\n" // 验证执行计划包含EnumerableMergeUnion节点，all=[false]表示去除重复行
            + "  EnumerableSort(sort0=[$0], sort1=[$1], dir0=[ASC-nulls-first], dir1=[DESC])\n" // 第一个输入分支：按第0列（commission）升序排序且NULL值在前，按第1列（name）降序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['R%'], expr#6=[LIKE($t2, $t5)], commission=[$t4], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'R'开头的记录，选择commission和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：从emps表读取数据
            + "  EnumerableSort(sort0=[$0], sort1=[$1], dir0=[ASC-nulls-first], dir1=[DESC])\n" // 第二个输入分支：按第0列（commission）升序排序且NULL值在前，按第1列（name）降序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['%y%'], expr#6=[LIKE($t2, $t5)], commission=[$t4], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name包含'y'的记录，选择commission和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：从emps表读取数据
        .returnsOrdered( // 验证查询结果按指定排序规则返回
            "commission=null; name=Taylor", // 期望结果第1行：commission为NULL，name=Taylor（NULL值排在最前，name降序）
            "commission=null; name=Riyad", // 期望结果第2行：commission为NULL，name=Riyad（注意只有一条，重复行被去除）
            "commission=null; name=Ralf", // 期望结果第3行：commission为NULL，name=Ralf
            "commission=250; name=Seohyun", // 期望结果第4行：commission=250，name=Seohyun
            "commission=250; name=Hyuna", // 期望结果第5行：commission=250，name=Hyuna
            "commission=250; name=Andy", // 期望结果第6行：commission=250，name=Andy
            "commission=500; name=Kylie", // 期望结果第7行：commission=500，name=Kylie
            "commission=500; name=Guy"); // 期望结果第8行：commission=500，name=Guy
  }

  @Test void mergeUnionAllOrderByCommissionAscNullsLastAndNameDesc() { // 测试方法：测试UNION ALL操作后按多字段排序（commission升序且NULL值在后，name降序）的场景
    tester(false, // 调用tester方法创建测试环境，第一个参数forceDecorrelate=false表示不强制去关联
        new HrSchemaBig(), // 使用大型人力资源schema作为测试数据源
        "select * from (select commission, name from emps where name like 'R%' union all select commission, name from emps where name like '%y%') order by commission asc nulls last, name desc") // SQL查询：从emps表中选择name以'R'开头和name包含'y'的员工，使用UNION ALL合并所有结果，按commission升序排序（NULL值排在最后），然后按name降序排序
        .explainContains("EnumerableMergeUnion(all=[true])\n" // 验证执行计划包含EnumerableMergeUnion节点，all=[true]表示保留重复行
            + "  EnumerableSort(sort0=[$0], sort1=[$1], dir0=[ASC], dir1=[DESC])\n" // 第一个输入分支：按第0列（commission）升序排序（NULL值在后），按第1列（name）降序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['R%'], expr#6=[LIKE($t2, $t5)], commission=[$t4], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'R'开头的记录，选择commission和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：从emps表读取数据
            + "  EnumerableSort(sort0=[$0], sort1=[$1], dir0=[ASC], dir1=[DESC])\n" // 第二个输入分支：按第0列（commission）升序排序（NULL值在后），按第1列（name）降序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['%y%'], expr#6=[LIKE($t2, $t5)], commission=[$t4], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name包含'y'的记录，选择commission和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：从emps表读取数据
        .returnsOrdered( // 验证查询结果按指定排序规则返回
            "commission=250; name=Seohyun", // 期望结果第1行：commission=250，name=Seohyun（非NULL值排在前面，name降序）
            "commission=250; name=Hyuna", // 期望结果第2行：commission=250，name=Hyuna
            "commission=250; name=Andy", // 期望结果第3行：commission=250，name=Andy
            "commission=500; name=Kylie", // 期望结果第4行：commission=500，name=Kylie
            "commission=500; name=Guy", // 期望结果第5行：commission=500，name=Guy
            "commission=null; name=Taylor", // 期望结果第6行：commission为NULL，name=Taylor（NULL值排在最后，name降序）
            "commission=null; name=Riyad", // 期望结果第7行：commission为NULL，name=Riyad
            "commission=null; name=Riyad", // 期望结果第8行：commission为NULL，name=Riyad（重复行被保留因为是UNION ALL）
            "commission=null; name=Ralf"); // 期望结果第9行：commission为NULL，name=Ralf
  }

  @Test void mergeUnionOrderByCommissionAscNullsLastAndNameDesc() { // 测试方法：测试UNION操作后按多字段排序（commission升序且NULL值在后，name降序）的场景，验证去重功能
    tester(false, // 调用tester方法创建测试环境，第一个参数forceDecorrelate=false表示不强制去关联
        new HrSchemaBig(), // 使用大型人力资源schema作为测试数据源
        "select * from (select commission, name from emps where name like 'R%' union select commission, name from emps where name like '%y%') order by commission asc nulls last, name desc") // SQL查询：从emps表中选择name以'R'开头和name包含'y'的员工，使用UNION合并结果（自动去重），按commission升序排序（NULL值排在最后），然后按name降序排序
        .explainContains("EnumerableMergeUnion(all=[false])\n" // 验证执行计划包含EnumerableMergeUnion节点，all=[false]表示去除重复行
            + "  EnumerableSort(sort0=[$0], sort1=[$1], dir0=[ASC], dir1=[DESC])\n" // 第一个输入分支：按第0列（commission）升序排序（NULL值在后），按第1列（name）降序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['R%'], expr#6=[LIKE($t2, $t5)], commission=[$t4], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name以'R'开头的记录，选择commission和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 表扫描：从emps表读取数据
            + "  EnumerableSort(sort0=[$0], sort1=[$1], dir0=[ASC], dir1=[DESC])\n" // 第二个输入分支：按第0列（commission）升序排序（NULL值在后），按第1列（name）降序排序
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=['%y%'], expr#6=[LIKE($t2, $t5)], commission=[$t4], name=[$t2], $condition=[$t6])\n" // 计算节点：过滤name包含'y'的记录，选择commission和name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 表扫描：从emps表读取数据
        .returnsOrdered( // 验证查询结果按指定排序规则返回
            "commission=250; name=Seohyun", // 期望结果第1行：commission=250，name=Seohyun（非NULL值排在前面，name降序）
            "commission=250; name=Hyuna", // 期望结果第2行：commission=250，name=Hyuna
            "commission=250; name=Andy", // 期望结果第3行：commission=250，name=Andy
            "commission=500; name=Kylie", // 期望结果第4行：commission=500，name=Kylie
            "commission=500; name=Guy", // 期望结果第5行：commission=500，name=Guy
            "commission=null; name=Taylor", // 期望结果第6行：commission为NULL，name=Taylor（NULL值排在最后，name降序）
            "commission=null; name=Riyad", // 期望结果第7行：commission为NULL，name=Riyad（注意只有一条，重复行被去除）
            "commission=null; name=Ralf"); // 期望结果第8行：commission为NULL，name=Ralf
  }

  private CalciteAssert.AssertQuery tester(boolean forceDecorrelate, // 辅助方法：创建测试环境并配置优化器规则，用于测试EnumerableMergeUnion功能
      Object schema, // 参数：schema对象，用于提供测试数据源，通常是ReflectiveSchema包装的Java对象
      String sqlQuery) { // 参数：SQL查询字符串，需要测试的SQL语句
    return CalciteAssert.that() // 创建CalciteAssert测试断言对象，用于构建测试环境
        .with(CalciteConnectionProperty.LEX, Lex.JAVA) // 配置词法分析器为JAVA模式，使用Java风格的标识符和字符串字面量
        .with(CalciteConnectionProperty.FORCE_DECORRELATE, forceDecorrelate) // 配置是否强制去关联，forceDecorrelate参数控制是否强制执行子查询去关联优化
        .withSchema("s", new ReflectiveSchema(schema)) // 注册schema，使用"s"作为schema名称，通过ReflectiveSchema将Java对象包装为数据库schema
        .query(sqlQuery) // 设置要执行的SQL查询语句
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 添加优化器钩子，在优化器初始化时自定义配置
          // Force UNION to be implemented via EnumerableMergeUnion // 强制UNION操作使用EnumerableMergeUnion实现
          planner.removeRule(EnumerableRules.ENUMERABLE_UNION_RULE); // 移除默认的ENUMERABLE_UNION_RULE规则，避免使用普通的EnumerableUnion
          // Allow EnumerableLimitSort optimization // 允许使用EnumerableLimitSort优化规则
          planner.addRule(EnumerableRules.ENUMERABLE_LIMIT_SORT_RULE); // 添加ENUMERABLE_LIMIT_SORT_RULE规则，支持LIMIT和SORT的联合优化
        }); // 返回配置好的AssertQuery对象，可以链式调用explainContains()、returnsOrdered()等方法进行断言验证
  }
}
