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
package org.apache.calcite.chinook; // 声明包名，这个类属于org.apache.calcite.chinook包

import org.apache.calcite.adapter.java.AbstractQueryableTable; // 导入AbstractQueryableTable抽象类，用于实现可查询表
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供LINQ风格的查询功能
import org.apache.calcite.linq4j.QueryProvider; // 导入QueryProvider接口，用于提供查询执行能力
import org.apache.calcite.linq4j.Queryable; // 导入Queryable接口，表示可查询的数据源
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.schema.QueryableTable; // 导入QueryableTable接口，表示可查询的表
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，表示模式（Schema）及其附加信息
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL类型名称

import java.nio.charset.StandardCharsets; // 导入StandardCharsets类，提供标准字符集常量
import java.util.Base64; // 导入Base64类，提供Base64编码和解码功能

/**
 * Example Table Function for lateral join checks. // 示例表函数，用于横向连接（lateral join）检查
 * 这个类实现了一个表函数，可以根据输入的字符串参数生成一个包含哈希码和Base64编码的表
 * 主要用于演示Calcite中如何实现自定义表函数，特别是用于横向连接场景
 * 表函数是SQL中的一种特殊函数，可以返回一个表（多行多列），而不仅仅是单个值
 * 横向连接允许在连接条件中使用子查询，该子查询可以引用左侧表的列
 */
public class CodesFunction { // 定义CodesFunction类，这是一个工具类，提供静态方法创建可查询表

  private CodesFunction(){ // 私有构造方法，防止实例化，因为这个类只提供静态方法
  } // 构造方法结束

  public static QueryableTable getTable(String name) { // 静态方法，根据输入的name参数返回一个可查询表对象，name是要编码的字符串

    return new AbstractQueryableTable(Object[].class) { // 创建匿名内部类，继承AbstractQueryableTable，指定元素类型为Object[]数组
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，定义表的行类型（即表的列结构）
        return typeFactory.builder() // 使用typeFactory创建一个RelDataType构建器
            .add("TYPE", SqlTypeName.VARCHAR) // 添加第一列：列名为"TYPE"，类型为VARCHAR字符串类型
            .add("CODEVALUE", SqlTypeName.VARCHAR) // 添加第二列：列名为"CODEVALUE"，类型为VARCHAR字符串类型
            .build(); // 构建并返回RelDataType对象，完成行类型的定义
      } // getRowType方法结束

      @Override public Queryable<String[]> asQueryable(QueryProvider queryProvider, // 重写asQueryable方法，将表转换为可查询对象，queryProvider是查询提供者
                                                       SchemaPlus schema, // schema参数表示当前的模式（Schema）对象
                                                       String tableName) { // tableName参数表示表的名称
        if (name == null) { // 检查输入参数name是否为null
          return Linq4j.<String[]>emptyEnumerable().asQueryable(); // 如果name为null，返回一个空的查询对象，避免空指针异常
        } // if语句结束
        return Linq4j.asEnumerable(new String[][]{ // 如果name不为null，创建一个字符串二维数组并转换为可枚举对象，然后转换为可查询对象
            new String[]{"HASHCODE", "" + name.hashCode()}, // 第一行数据：TYPE列值为"HASHCODE"，CODEVALUE列值为name的哈希码的字符串形式
            new String[]{"BASE64", // 第二行数据：TYPE列值为"BASE64"
                Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8))} // CODEVALUE列值为name字符串的UTF-8字节数组的Base64编码结果
        }).asQueryable(); // 将可枚举对象转换为可查询对象并返回
      } // asQueryable方法结束
    }; // 匿名内部类实例化结束，返回QueryableTable对象
  } // getTable方法结束
} // CodesFunction类定义结束
