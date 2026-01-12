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
package org.apache.calcite.adapter.file; // 定义包名，表示该类属于file适配器包，用于处理文件数据源的表定义

import org.apache.calcite.adapter.file.JsonEnumerator.JsonDataConverter; // 导入JsonDataConverter类，用于JSON数据的类型推断和转换
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型（表的结构定义）
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型的工厂类
import org.apache.calcite.schema.Statistic; // 导入Statistic接口，表示表的统计信息（如行数、大小等）
import org.apache.calcite.schema.Statistics; // 导入Statistics工具类，提供常用的统计信息常量
import org.apache.calcite.schema.impl.AbstractTable; // 导入AbstractTable抽象类，作为所有表实现的基类
import org.apache.calcite.util.Source; // 导入Source类，表示数据源（如文件），封装了文件的访问方式

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可空的类型

import java.util.List; // 导入List接口，用于存储表数据的列表

/**
 * Table based on a JSON file. // 类的JavaDoc注释：基于JSON文件的表实现
 */
public class JsonTable extends AbstractTable { // JsonTable类继承自AbstractTable，表示这是一个基于JSON文件的表实现，用于将JSON文件映射为Calcite中的关系表
  private final Source source; // 成员变量：数据源对象，final修饰表示初始化后不可改变，指向包含JSON数据的文件
  private @Nullable RelDataType rowType; // 成员变量：表的行类型（即表结构，包含列名和列类型），可空，使用延迟初始化以提高性能
  protected @Nullable List<Object> dataList; // 成员变量：表的数据列表，存储从JSON文件解析出来的所有行数据，protected修饰允许子类访问，可空，使用延迟初始化

  public JsonTable(Source source) { // 构造方法：创建JsonTable实例，需要提供数据源参数
    this.source = source; // 将传入的数据源参数赋值给成员变量source，保存对JSON文件的引用
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写AbstractTable的getRowType方法，获取表的行类型（表结构定义），typeFactory是用于创建数据类型的工厂
    if (rowType == null) { // 检查rowType是否为null，实现延迟初始化，避免每次调用都重新推断类型
      rowType = JsonEnumerator.deduceRowType(typeFactory, source).getRelDataType(); // 如果rowType为null，调用JsonEnumerator的deduceRowType方法推断JSON文件的类型结构，然后获取RelDataType并赋值给rowType
    } // 结束if语句
    return rowType; // 返回推断或缓存的行类型，表示表的结构定义
  }

  /** Returns the data list of the table. */ // JavaDoc注释：返回表的数据列表
  public List<Object> getDataList(RelDataTypeFactory typeFactory) { // 公共方法：获取表的数据列表，typeFactory用于数据类型推断
    if (dataList == null) { // 检查dataList是否为null，实现延迟初始化，避免每次调用都重新读取和解析JSON文件
      JsonDataConverter jsonDataConverter = // 如果dataList为null，创建JsonDataConverter对象用于JSON数据的转换
          JsonEnumerator.deduceRowType(typeFactory, source); // 调用JsonEnumerator的deduceRowType方法，传入类型工厂和数据源，返回JsonDataConverter实例
      dataList = jsonDataConverter.getDataList(); // 调用JsonDataConverter的getDataList方法，从JSON文件中读取并解析数据，将结果存储到dataList成员变量中
    } // 结束if语句
    return dataList; // 返回解析后的数据列表，包含JSON文件中的所有行数据
  }

  @Override public Statistic getStatistic() { // 重写AbstractTable的getStatistic方法，获取表的统计信息，用于查询优化器进行成本估算
    return Statistics.UNKNOWN; // 返回UNKNOWN统计信息，表示表的统计信息未知，优化器将使用默认的成本估算策略
  } // 类定义结束
}
