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
package org.apache.calcite.chinook; // 声明包名，该类属于org.apache.calcite.chinook包，是Calcite框架中Chinook示例数据库的一部分

import org.apache.calcite.adapter.java.AbstractQueryableTable; // 导入抽象可查询表基类，用于创建基于Java对象的可查询表
import org.apache.calcite.linq4j.Linq4j; // 导入LINQ4j工具类，提供LINQ风格的查询功能，用于将集合转换为可查询对象
import org.apache.calcite.linq4j.QueryProvider; // 导入查询提供者接口，用于执行LINQ查询
import org.apache.calcite.linq4j.Queryable; // 导入可查询接口，表示可以被LINQ查询的数据源
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示表或列的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建关系数据类型
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，表示Calcite中的模式，可以包含表、函数等
import org.apache.calcite.schema.TableFactory; // 导入表工厂接口，用于动态创建表实例
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义各种SQL数据类型如INTEGER、VARCHAR等

import com.google.common.collect.ContiguousSet; // 导入Guava库的连续集合类，用于创建连续的整数集合
import com.google.common.collect.DiscreteDomain; // 导入Guava库的离散域接口，用于定义离散值的范围
import com.google.common.collect.Range; // 导入Guava库的范围类，用于定义数值范围

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数

import java.util.Map; // 导入Map接口，用于存储键值对

/**
 * Factory for the table of albums preferred by the current user. // 类文档注释：当前用户偏好的专辑表的工厂类
 * 该类实现了TableFactory接口，用于创建一个虚拟表，该表包含当前用户偏好的专辑ID列表
 * 这个类是Calcite自定义表适配器的典型实现示例，展示了如何基于业务逻辑动态生成数据
 * 主要功能：
 * 1. 根据当前用户类型返回不同的专辑偏好列表
 * 2. 如果是特定用户(SPECIFIC_USER)，返回预定义的5个专辑ID
 * 3. 如果是其他用户，返回所有专辑ID（从1到347的连续集合）
 * 4. 实现了Calcite的TableFactory接口，允许通过配置文件动态创建表
 */
public class PreferredAlbumsTableFactory implements TableFactory<AbstractQueryableTable> { // 类定义：实现TableFactory接口，泛型参数为AbstractQueryableTable表示创建的表类型
  // 成员变量：特定用户偏好的专辑ID数组，包含5个预定义的专辑ID：4, 56, 154, 220, 321
  private static final Integer[] SPECIFIC_USER_PREFERRED_ALBUMS = // 静态常量数组，存储特定用户偏好的专辑ID
      {4, 56, 154, 220, 321}; // 专辑ID数组初始化，这些是Chinook示例数据库中的专辑ID
  private static final int FIRST_ID = 1; // 静态常量：专辑ID范围的起始值，表示专辑ID从1开始
  private static final int LAST_ID = 347; // 静态常量：专辑ID范围的结束值，表示专辑ID到347结束（Chinook数据库中共有347张专辑）

  // 方法：创建表实例，这是TableFactory接口的核心方法，由Calcite在加载表时自动调用
  @Override public AbstractQueryableTable create( // @Override注解表示重写接口方法，返回AbstractQueryableTable类型的表实例
      SchemaPlus schema, // 参数1：SchemaPlus对象，表示当前表所在的Schema模式，包含其他表和函数
      String name, // 参数2：表名称，在模型配置文件中定义的表名
      Map<String, Object> operand, // 参数3：操作数映射，包含在模型配置文件中传递的参数（键值对）
      @Nullable RelDataType rowType) { // 参数4：行类型，可选参数，指定表的行类型结构（可为null）
    // 返回一个匿名内部类实例，继承自AbstractQueryableTable，实现可查询表的功能
    return new AbstractQueryableTable(Integer.class) { // 创建AbstractQueryableTable的匿名子类，泛型参数Integer.class表示表中每行数据都是Integer类型（专辑ID）
      // 方法：获取表的行类型，定义表的结构（列名和数据类型）
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // @Override注解，参数typeFactory用于创建关系数据类型
        return typeFactory.builder().add("ID", SqlTypeName.INTEGER).build(); // 创建行类型：使用typeFactory构建器添加一个名为"ID"的列，类型为INTEGER，然后构建返回
      }

      // 方法：将表转换为可查询对象，使表可以被LINQ查询
      @Override public Queryable<Integer> asQueryable( // @Override注解，返回Queryable<Integer>表示可查询的Integer集合
          QueryProvider qp, // 参数1：查询提供者，用于执行查询操作
          SchemaPlus sp, // 参数2：SchemaPlus对象，表示表所在的模式
          String string) { // 参数3：字符串参数，通常用于传递额外的配置信息
        return fetchPreferredAlbums(); // 调用静态方法fetchPreferredAlbums()获取当前用户偏好的专辑ID列表并返回
      }
    };
  }

  // 方法：获取当前用户偏好的专辑ID列表，这是一个私有静态方法，根据当前用户类型返回不同的专辑集合
  private static Queryable<Integer> fetchPreferredAlbums() { // 私有静态方法，返回Queryable<Integer>表示可查询的专辑ID集合
    if (EnvironmentFairy.getUser() == EnvironmentFairy.User.SPECIFIC_USER) { // 条件判断：检查当前用户是否为特定用户(SPECIFIC_USER)，EnvironmentFairy是辅助类，用于模拟用户环境
      // 如果是特定用户，返回预定义的5个专辑ID
      return Linq4j.asEnumerable(SPECIFIC_USER_PREFERRED_ALBUMS).asQueryable(); // 将SPECIFIC_USER_PREFERRED_ALBUMS数组转换为可枚举对象，再转换为可查询对象返回
    } else { // 如果不是特定用户（即普通用户）
      // 创建一个从FIRST_ID到LAST_ID的连续整数集合
      final ContiguousSet<Integer> set = // 创建连续整数集合，使用final修饰表示不可变
          ContiguousSet.create(Range.closed(FIRST_ID, LAST_ID), // 使用ContiguousSet.create方法创建集合，Range.closed创建闭区间[1, 347]
              DiscreteDomain.integers()); // DiscreteDomain.integers()指定域类型为整数域
      return Linq4j.asEnumerable(set).asQueryable(); // 将连续集合转换为可枚举对象，再转换为可查询对象返回，这样普通用户可以看到所有专辑
    }
  }
}
