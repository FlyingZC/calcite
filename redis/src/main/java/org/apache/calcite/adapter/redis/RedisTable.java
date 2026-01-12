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
package org.apache.calcite.adapter.redis; // 声明包名，表示这个类属于Redis适配器包

import org.apache.calcite.DataContext; // 导入Calcite的数据上下文接口，用于在查询执行期间传递运行时信息
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入LINQ4J抽象可枚举类，用于实现可枚举的数据集合
import org.apache.calcite.linq4j.Enumerable; // 导入LINQ4J可枚举接口，表示可以枚举的数据源
import org.apache.calcite.linq4j.Enumerator; // 导入LINQ4J枚举器接口，用于遍历数据集合
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示表中的行类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系数据类型原型接口，用于延迟创建数据类型
import org.apache.calcite.schema.ScannableTable; // 导入可扫描表接口，表示可以扫描数据的表
import org.apache.calcite.schema.Table; // 导入表接口，Calcite中表的基础接口
import org.apache.calcite.schema.impl.AbstractTable; // 导入抽象表类，提供表的基本实现
import org.apache.calcite.util.Pair; // 导入Pair工具类，用于存储键值对

import com.google.common.collect.ImmutableMap; // 导入Guava的不可变Map类，用于创建不可修改的映射

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，用于标记可能为空的类型

import java.util.ArrayList; // 导入ArrayList动态数组类
import java.util.List; // 导入List接口，表示有序集合
import java.util.Map; // 导入Map接口，表示键值对映射

/**
 * Table mapped onto a redis table. // RedisTable类：将Redis数据结构映射为Calcite可查询的表
 * 这个类是Calcite Redis适配器的核心类，负责将Redis中的数据（如Hash、List等）映射为Calcite中的表结构，
 * 使得Calcite能够像查询关系型数据库表一样查询Redis中的数据。它实现了ScannableTable接口，
 * 提供了扫描数据的能力，通过RedisEnumerator来实际读取Redis中的数据。
 */
public class RedisTable extends AbstractTable // RedisTable继承自AbstractTable抽象表类，获得表的基本功能
    implements ScannableTable { // 实现ScannableTable接口，使表可以被扫描读取数据

  final RedisSchema schema; // 成员变量：所属的RedisSchema对象，表示这个表所在的Redis模式（schema），包含Redis连接信息和元数据
  final String tableName; // 成员变量：Redis中的表名或键名，用于标识要访问的Redis数据结构
  final RelProtoDataType protoRowType; // 成员变量：行类型的原型对象，用于延迟创建表的行类型（包含列名和列类型信息）
  final ImmutableMap<String, Object> allFields; // 成员变量：不可变的字段映射，存储表的所有字段名和对应的值或类型信息
  final String dataFormat; // 成员变量：数据格式标识，指定Redis数据的存储格式（如hash、list、set等）
  final RedisConfig redisConfig; // 成员变量：Redis配置对象，包含Redis服务器的连接信息（主机、端口、密码等）
  RedisEnumerator redisEnumerator; // 成员变量：Redis枚举器对象，用于实际遍历和读取Redis中的数据（非final，可被重新赋值）

  public RedisTable( // 构造方法：创建RedisTable实例，初始化所有必要的成员变量
      RedisSchema schema, // 参数：RedisSchema对象，表示表所属的模式
      String tableName, // 参数：表名或Redis键名
      RelProtoDataType protoRowType, // 参数：行类型原型，定义表的结构
      Map<String, Object> allFields, // 参数：字段映射，包含所有字段的信息
      String dataFormat, // 参数：数据格式，指定Redis数据存储方式
      RedisConfig redisConfig) { // 参数：Redis配置，包含连接信息
    this.schema = schema; // 将传入的schema参数赋值给成员变量schema，保存所属的模式对象
    this.tableName = tableName; // 将传入的tableName参数赋值给成员变量tableName，保存表名
    this.protoRowType = protoRowType; // 将传入的protoRowType参数赋值给成员变量protoRowType，保存行类型原型
    this.allFields = // 初始化allFields成员变量，处理可能为null的情况
        allFields == null ? ImmutableMap.of() // 如果allFields为null，创建一个空的不可变Map
            : ImmutableMap.copyOf(allFields); // 否则，将传入的Map转换为不可变的Map副本，确保数据不被外部修改
    this.dataFormat = dataFormat; // 将传入的dataFormat参数赋值给成员变量dataFormat，保存数据格式
    this.redisConfig = redisConfig; // 将传入的redisConfig参数赋值给成员变量redisConfig，保存Redis连接配置
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写方法：获取表的行类型（包含所有列的名称和类型信息）
    if (protoRowType != null) { // 如果已经定义了行类型原型
      return protoRowType.apply(typeFactory); // 直接使用原型创建行类型，传入类型工厂来实际创建类型对象
    } // 否则，需要从allFields字段映射中推断行类型
    final List<RelDataType> types = new ArrayList<RelDataType>(allFields.size()); // 创建类型列表，容量为字段数量，用于存储每个字段的类型
    final List<String> names = new ArrayList<String>(allFields.size()); // 创建名称列表，容量为字段数量，用于存储每个字段的名称

    for (Object key : allFields.keySet()) { // 遍历allFields中的所有字段名（键）
      final RelDataType type = typeFactory.createJavaType(allFields.get(key).getClass()); // 根据字段值的Java类创建对应的RelDataType类型
      names.add(key.toString()); // 将字段名转换为字符串并添加到名称列表中
      types.add(type); // 将创建的类型添加到类型列表中
    } // 循环结束，所有字段的名称和类型都已收集
    return typeFactory.createStructType(Pair.zip(names, types)); // 使用类型工厂创建结构类型，将名称和类型配对组合成行类型
  } // getRowType方法结束

  static Table create( // 静态工厂方法：创建RedisTable实例（使用RedisConfig对象）
      RedisSchema schema, // 参数：RedisSchema对象，表所属的模式
      String tableName, // 参数：表名或Redis键名
      RedisConfig redisConfig, // 参数：Redis配置对象
      RelProtoDataType protoRowType) { // 参数：行类型原型
    RedisTableFieldInfo tableFieldInfo = schema.getTableFieldInfo(tableName); // 从schema中获取指定表的字段信息元数据
    Map<String, Object> allFields = RedisEnumerator.deduceRowType(tableFieldInfo); // 通过RedisEnumerator推断表的字段类型信息
    return new RedisTable(schema, tableName, protoRowType, // 创建并返回新的RedisTable实例
        allFields, tableFieldInfo.getDataFormat(), redisConfig); // 传入所有必要的参数
  } // create方法结束

  static Table create( // 静态工厂方法重载：创建RedisTable实例（使用Map操作数）
      RedisSchema schema, // 参数：RedisSchema对象，表所属的模式
      String tableName, // 参数：表名或Redis键名
      Map operand, // 参数：操作数映射，可能包含配置信息
      RelProtoDataType protoRowType) { // 参数：行类型原型
    RedisConfig redisConfig = // 创建RedisConfig对象
        new RedisConfig(schema.host, schema.port, // 使用schema中的主机和端口信息
            schema.database, schema.password); // 使用schema中的数据库编号和密码
    return create(schema, tableName, redisConfig, protoRowType); // 调用另一个create方法，传入创建的redisConfig
  } // create方法结束

  @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写方法：扫描表数据，返回可枚举的对象数组集合
    return new AbstractEnumerable<Object[]>() { // 返回一个抽象可枚举对象，实现enumerator方法
      @Override public Enumerator<Object[]> enumerator() { // 重写方法：创建并返回枚举器对象
        return new RedisEnumerator(redisConfig, schema, tableName); // 创建RedisEnumerator实例，使用配置、模式和表名来初始化
      } // enumerator方法结束
    }; // 匿名内部类结束
  } // scan方法结束
} // RedisTable类结束
