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
package org.apache.calcite.chinook; // 定义包名，该类位于 org.apache.calcite.chinook 包下，属于 Calcite 框架的 Chinook 示例模块

import org.apache.calcite.adapter.java.AbstractQueryableTable; // 导入抽象可查询表基类，用于创建支持 LINQ 查询的表
import org.apache.calcite.linq4j.Linq4j; // 导入 Linq4j 工具类，提供 LINQ 查询的核心功能
import org.apache.calcite.linq4j.QueryProvider; // 导入查询提供者接口，用于执行 LINQ 查询
import org.apache.calcite.linq4j.Queryable; // 导入可查询接口，表示可被查询的数据集合
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示 Calcite 中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.schema.SchemaPlus; // 导入增强模式接口，表示包含表和函数的模式
import org.apache.calcite.schema.TableFactory; // 导入表工厂接口，用于创建表实例
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SQL 类型名称枚举，定义标准的 SQL 数据类型

import com.google.common.collect.ContiguousSet; // 导入 Google Guava 的连续集合类，用于创建连续的整数集合
import com.google.common.collect.DiscreteDomain; // 导入 Google Guava 的离散域接口，定义离散类型的域
import com.google.common.collect.Range; // 导入 Google Guava 的范围类，表示值的范围区间

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为 null 的参数

import java.util.Map; // 导入 Java 的 Map 接口，用于存储键值对映射

/**
 * Factory for the table of genres preferred by the current user.
 * 当前用户偏好的音乐流派表的工厂类，该类实现了 TableFactory 接口，用于创建表示用户偏好音乐流派的表
 * 这个表是动态的，根据当前用户的不同返回不同的音乐流派 ID 集合
 */
public class PreferredGenresTableFactory implements TableFactory<AbstractQueryableTable> { // 定义 PreferredGenresTableFactory 类，实现 TableFactory 接口，泛型参数为 AbstractQueryableTable 表示创建的表类型
  private static final Integer[] SPECIFIC_USER_PREFERRED_GENRES = // 定义特定用户偏好的音乐流派 ID 数组，静态常量，包含 5 个流派 ID
      {1, 2, 7, 9, 15}; // 具体的流派 ID 值，代表特定用户偏好的 5 种音乐流派
  private static final int FIRST_ID = 1; // 定义音乐流派 ID 的起始值，静态常量，表示流派 ID 的最小值为 1
  private static final int LAST_ID = 25; // 定义音乐流派 ID 的结束值，静态常量，表示流派 ID 的最大值为 25

  @Override public AbstractQueryableTable create( // 重写 TableFactory 接口的 create 方法，用于创建一个可查询的表实例
      SchemaPlus schema, // 参数：SchemaPlus 对象，表示包含此表的模式，可用于访问模式中的其他表和函数
      String name, // 参数：表名称，标识要创建的表的名称
      Map<String, Object> operand, // 参数：操作数映射，包含创建表时需要的配置参数
      @Nullable RelDataType rowType) { // 参数：可选的行类型，如果提供则使用该类型作为表的行类型，否则由表自己定义
    return new AbstractQueryableTable(Integer.class) { // 创建并返回一个匿名内部类的实例，继承自 AbstractQueryableTable，指定元素类型为 Integer
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写 getRowType 方法，定义表的行类型结构
        return typeFactory.builder().add("ID", SqlTypeName.INTEGER).build(); // 使用类型工厂构建行类型，添加一个名为 "ID" 的 INTEGER 类型列，并构建返回
      }

      @Override public Queryable<Integer> asQueryable( // 重写 asQueryable 方法，将表转换为可查询的 Queryable 对象
          QueryProvider qp, // 参数：查询提供者，用于执行 LINQ 查询
          SchemaPlus sp, // 参数：模式对象，提供对模式中其他资源的访问
          String string) { // 参数：字符串参数，通常传递表名称
        return fetchPreferredGenres(); // 调用 fetchPreferredGenres 方法获取当前用户偏好的音乐流派集合并返回
      }
    };
  }

  private static Queryable<Integer> fetchPreferredGenres() { // 私有静态方法，用于获取当前用户偏好的音乐流派集合，返回可查询的 Integer 集合
    if (EnvironmentFairy.getUser() == EnvironmentFairy.User.SPECIFIC_USER) { // 检查当前用户是否为特定用户（SPECIFIC_USER），通过 EnvironmentFairy 获取用户类型
      return Linq4j.asEnumerable(SPECIFIC_USER_PREFERRED_GENRES).asQueryable(); // 如果是特定用户，将特定用户偏好的流派 ID 数组转换为可枚举集合，再转换为可查询对象返回
    } else { // 如果不是特定用户，则返回所有音乐流派
      final ContiguousSet<Integer> set = // 创建一个连续的整数集合，用于表示从 FIRST_ID 到 LAST_ID 的所有整数
          ContiguousSet.create(Range.closed(FIRST_ID, LAST_ID), // 使用 Guava 的 Range 创建一个闭区间范围，包含 1 到 25 的所有整数
              DiscreteDomain.integers()); // 指定离散域为整数域，确保范围中的值是整数
      return Linq4j.asEnumerable(set).asQueryable(); // 将连续集合转换为可枚举集合，再转换为可查询对象返回
    }
  }
} // PreferredGenresTableFactory 类定义结束
