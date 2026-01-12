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
package org.apache.calcite.adapter.os; // 定义包路径，该类位于 org.apache.calcite.adapter.os 包下，属于操作系统适配器模块

import org.apache.calcite.DataContext; // 导入 DataContext 类，用于在查询执行期间传递上下文信息（如会话变量、数据源等）
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入 AbstractEnumerable 抽象类，用于实现可枚举的数据集合，提供 LINQ 风格的数据访问
import org.apache.calcite.linq4j.Enumerable; // 导入 Enumerable 接口，表示可枚举的数据集合，支持 LINQ 查询操作
import org.apache.calcite.linq4j.Enumerator; // 导入 Enumerator 接口，用于遍历数据集合中的元素，类似 Java 的 Iterator
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 接口，表示关系数据类型，描述表的结构（列名、数据类型等）
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入 RelDataTypeFactory 接口，用于创建和构建关系数据类型
import org.apache.calcite.schema.ScannableTable; // 导入 ScannableTable 接口，表示可扫描的表，支持全表扫描操作
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SqlTypeName 枚举，定义 SQL 标准数据类型（如 VARCHAR、INTEGER 等）

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Nullable 注解，用于标记可能为 null 的值，帮助静态分析工具检测空指针异常

/**
 * Table function that executes the OS "mounts". // 这是一个表函数类，用于执行操作系统的 "mounts" 命令，返回系统挂载点的信息
 * 该类作为 Calcite 的表函数，允许 SQL 查询访问操作系统的挂载点信息，如磁盘分区的使用情况
 * 它实现了将操作系统命令输出转换为可查询的表数据的功能，是 Calcite 适配器模式的一个典型应用
 * 通过这个类，用户可以使用 SQL 查询来获取和操作系统的挂载点相关的信息
 * 该类使用单例模式，通过私有的构造函数防止实例化，所有功能通过静态方法提供
 */
public class MountsTableFunction { // 定义 MountsTableFunction 类，这是一个表函数类，用于返回操作系统挂载点信息的表
  private MountsTableFunction() { // 私有构造函数，防止外部实例化该类，确保该类只能通过静态方法访问（单例模式）
  } // 构造函数体为空，没有任何实现，因为该类不需要实例化

  public static ScannableTable eval(boolean b) { // 定义静态方法 eval，返回一个 ScannableTable 对象，该方法作为表函数的入口点
    // 参数 boolean b 是一个布尔标志，虽然在本实现中未使用，但保留了扩展性，可能用于控制查询行为
    // 该方法创建并返回一个 AbstractBaseScannableTable 的匿名子类实例，该实例实现了 ScannableTable 接口
    // AbstractBaseScannableTable 是操作系统适配器模块中的基类，提供了可扫描表的基本实现
    return new AbstractBaseScannableTable() { // 返回一个匿名内部类实例，继承自 AbstractBaseScannableTable
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写 scan 方法，返回可枚举的数据集合
        // 参数 root 是 DataContext 对象，包含查询执行的上下文信息（如数据源配置、会话变量等）
        // 该方法返回一个 Enumerable<Object[]>，其中每个 Object[] 代表表中的一行数据
        // @Nullable 注解表示 Object[] 数组中的元素可能为 null
        return new AbstractEnumerable<Object[]>() { // 返回一个 AbstractEnumerable 的匿名子类实例，用于提供可枚举的数据
          @Override public Enumerator<Object[]> enumerator() { // 重写 enumerator 方法，返回数据枚举器
            // 该方法创建并返回一个 OsQuery 对象，该对象实现了 Enumerator 接口
            // OsQuery 是专门用于执行操作系统查询的枚举器类，它会执行 "mounts" 命令
            // "mounts" 命令会返回系统所有挂载点的信息，包括设备名称、挂载路径、容量、使用情况等
            return new OsQuery("mounts"); // 创建并返回 OsQuery 对象，传入 "mounts" 命令作为参数
          } // enumerator 方法结束，返回 OsQuery 枚举器对象
        }; // AbstractEnumerable 匿名类定义结束
      } // scan 方法结束，返回可枚举的数据集合

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写 getRowType 方法，定义表的行类型（表结构）
        // 参数 typeFactory 是 RelDataTypeFactory 对象，用于创建和构建关系数据类型
        // 该方法返回一个 RelDataType 对象，描述了表的列名和数据类型
        // 这个方法定义了 "mounts" 命令返回的数据的表结构，包括 8 个列
        return typeFactory.builder() // 使用 typeFactory 创建一个 RelDataType 构建器
            .add("name", SqlTypeName.VARCHAR) // 添加第一列 "name"，类型为 VARCHAR，表示挂载点的名称或设备名称
            .add("volume", SqlTypeName.VARCHAR) // 添加第二列 "volume"，类型为 VARCHAR，表示卷标或文件系统类型
            .add("size", SqlTypeName.VARCHAR) // 添加第三列 "size"，类型为 VARCHAR，表示总大小（如 "100G"）
            .add("used", SqlTypeName.VARCHAR) // 添加第四列 "used"，类型为 VARCHAR，表示已使用的大小
            .add("avail", SqlTypeName.VARCHAR) // 添加第五列 "avail"，类型为 VARCHAR，表示可用大小
            .add("iused", SqlTypeName.VARCHAR) // 添加第六列 "iused"，类型为 VARCHAR，表示已使用的 inode 数量
            .add("ifree", SqlTypeName.VARCHAR) // 添加第七列 "ifree"，类型为 VARCHAR，表示可用的 inode 数量
            .add("path", SqlTypeName.VARCHAR) // 添加第八列 "path"，类型为 VARCHAR，表示挂载路径（如 "/"、"/home"）
            .build(); // 构建并返回 RelDataType 对象，完成表结构的定义
      } // getRowType 方法结束，返回表的行类型定义
    }; // AbstractBaseScannableTable 匿名类定义结束
  } // eval 方法结束，返回 ScannableTable 对象
} // MountsTableFunction 类定义结束
