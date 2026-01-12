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
package org.apache.calcite.adapter.os; // 指定该类所属的包，位于Calcite框架的操作系统适配器包下

import org.apache.calcite.DataContext; // 导入Calcite的数据上下文类，用于在执行过程中传递数据和环境信息
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入LINQ4J的抽象可枚举类，用于实现可枚举的数据集合
import org.apache.calcite.linq4j.Enumerable; // 导入LINQ4J的可枚举接口，表示可以枚举的数据集合
import org.apache.calcite.linq4j.Enumerator; // 导入LINQ4J的枚举器接口，用于遍历数据集合
import org.apache.calcite.rel.type.RelDataType; // 导入Calcite的关系数据类型类，表示表中的行类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入Calcite的关系数据类型工厂，用于创建关系数据类型
import org.apache.calcite.schema.ScannableTable; // 导入Calcite的可扫描表接口，表示可以被扫描的表
import org.apache.calcite.sql.type.SqlTypeName; // 导入Calcite的SQL类型名称枚举，用于定义SQL数据类型

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的可空注解，用于标记可能为null的值

/**
 * Table function that executes the OS "java_info". // 这是一个表函数类，用于执行操作系统的"java_info"命令，将Java运行环境信息作为表数据返回
 * 该类的作用是：提供一个表函数，可以查询当前Java运行环境的详细信息，包括Java版本、厂商、安装路径、虚拟机规格等17项信息
 * 通过这个表函数，用户可以使用SQL语句直接查询Java环境信息，无需编写额外的Java代码
 */
public class JavaInfoTableFunction { // 定义JavaInfoTableFunction类，这是一个表函数类，用于生成包含Java环境信息的可扫描表
  private JavaInfoTableFunction() { // 私有构造方法，防止外部实例化该类，该类只通过静态方法eval来使用
  } // 构造方法结束，该类不允许实例化

  public static ScannableTable eval(boolean b) { // 静态评估方法，参数b为布尔值（实际未使用），返回一个可扫描表对象
    return new AbstractBaseScannableTable() { // 返回AbstractBaseScannableTable的匿名子类实例，该抽象类提供了可扫描表的基本实现
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，用于扫描数据并返回可枚举对象数组集合，参数root为数据上下文
        return new AbstractEnumerable<Object[]>() { // 返回AbstractEnumerable的匿名子类实例，该抽象类提供了可枚举的基本实现
          @Override public Enumerator<Object[]> enumerator() { // 重写enumerator方法，返回一个枚举器对象，用于遍历数据
            return new OsQuery("java_info"); // 创建并返回OsQuery枚举器实例，传入命令名称"java_info"用于获取Java环境信息
          } // enumerator方法结束，返回OsQuery枚举器
        }; // AbstractEnumerable匿名类结束
      } // scan方法结束，返回可枚举集合

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，定义表的行类型（即表结构），参数typeFactory为类型工厂
        return typeFactory.builder() // 使用类型工厂创建构建器对象，用于构建关系数据类型
            .add("java_version", SqlTypeName.VARCHAR) // 添加列：java_version（Java版本），类型为VARCHAR（可变长字符串）
            .add("java_vendor", SqlTypeName.VARCHAR) // 添加列：java_vendor（Java厂商），类型为VARCHAR
            .add("java_vendor_url", SqlTypeName.VARCHAR) // 添加列：java_vendor_url（Java厂商URL），类型为VARCHAR
            .add("java_home", SqlTypeName.VARCHAR) // 添加列：java_home（Java安装目录），类型为VARCHAR
            .add("java_vm_specification_version", SqlTypeName.VARCHAR) // 添加列：java_vm_specification_version（Java虚拟机规范版本），类型为VARCHAR
            .add("java_vm_specification_vendor", SqlTypeName.VARCHAR) // 添加列：java_vm_specification_vendor（Java虚拟机规范厂商），类型为VARCHAR
            .add("java_vm_specification_name", SqlTypeName.VARCHAR) // 添加列：java_vm_specification_name（Java虚拟机规范名称），类型为VARCHAR
            .add("java_vm_version", SqlTypeName.VARCHAR) // 添加列：java_vm_version（Java虚拟机版本），类型为VARCHAR
            .add("java_vm_vendor", SqlTypeName.VARCHAR) // 添加列：java_vm_vendor（Java虚拟机厂商），类型为VARCHAR
            .add("java_vm_name", SqlTypeName.VARCHAR) // 添加列：java_vm_name（Java虚拟机名称），类型为VARCHAR
            .add("java_specification_version", SqlTypeName.VARCHAR) // 添加列：java_specification_version（Java规范版本），类型为VARCHAR
            .add("java_specification_vender", SqlTypeName.VARCHAR) // 添加列：java_specification_vender（Java规范厂商，注意拼写错误应为vendor），类型为VARCHAR
            .add("java_specification_name", SqlTypeName.VARCHAR) // 添加列：java_specification_name（Java规范名称），类型为VARCHAR
            .add("java_class_version", SqlTypeName.VARCHAR) // 添加列：java_class_version（Java类文件版本），类型为VARCHAR
            .add("java_class_path", SqlTypeName.VARCHAR) // 添加列：java_class_path（Java类路径），类型为VARCHAR
            .add("java_io_tmpdir", SqlTypeName.VARCHAR) // 添加列：java_io_tmpdir（Java临时目录），类型为VARCHAR
            .add("java_ext_dirs", SqlTypeName.VARCHAR) // 添加列：java_ext_dirs（Java扩展目录），类型为VARCHAR
            .add("java_library_path", SqlTypeName.VARCHAR) // 添加列：java_library_path（Java库路径），类型为VARCHAR
            .build(); // 构建并返回关系数据类型对象，定义了包含17个字符串列的表结构
      } // getRowType方法结束，返回表的行类型定义
    }; // AbstractBaseScannableTable匿名类结束
  } // eval方法结束，返回可扫描表对象
} // JavaInfoTableFunction类结束
