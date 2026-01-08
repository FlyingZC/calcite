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
package org.apache.calcite.prepare; // 声明包名，该类属于org.apache.calcite.prepare包，用于SQL预处理阶段

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂接口，用于Java类型和SQL类型之间的转换
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示Calcite中的数据类型系统
import org.apache.calcite.sql.SqlInsert; // 导入SQL插入语句节点类，表示INSERT语句的语法树节点
import org.apache.calcite.sql.SqlOperatorTable; // 导入SQL操作符表接口，包含所有可用的SQL函数和操作符
import org.apache.calcite.sql.validate.SqlValidatorImpl; // 导入SQL验证器实现基类，提供SQL验证的核心功能

/** Validator. */ // 类注释：CalciteSqlValidator是Calcite框架中的SQL验证器，继承自SqlValidatorImpl基类
public class CalciteSqlValidator extends SqlValidatorImpl { // 定义CalciteSqlValidator类，继承SqlValidatorImpl以扩展标准SQL验证功能

  public CalciteSqlValidator(SqlOperatorTable opTab, // 构造方法：创建CalciteSqlValidator实例，opTab参数是SQL操作符表，包含所有可用的SQL函数和操作符定义
      CalciteCatalogReader catalogReader, JavaTypeFactory typeFactory, // catalogReader参数是目录读取器，用于访问数据库元数据（如表、列等），typeFactory参数是Java类型工厂，用于创建和转换数据类型
      Config config) { // config参数是验证器配置对象，包含验证器的各种配置选项（如大小写敏感性、类型强制转换等）
    super(opTab, catalogReader, typeFactory, config); // 调用父类SqlValidatorImpl的构造方法，初始化验证器的基本功能和配置
  } // 构造方法结束

  @Override protected RelDataType getLogicalSourceRowType( // 重写父类方法：获取INSERT语句中源数据行的逻辑类型，该方法用于确定INSERT语句中SELECT查询返回的数据类型
      RelDataType sourceRowType, SqlInsert insert) { // sourceRowType参数是源数据行的原始类型，insert参数是INSERT语句的语法树节点
    final RelDataType superType = // 调用父类方法获取源数据行的逻辑类型，父类会根据INSERT语句的目标表和源数据类型进行推导和验证
        super.getLogicalSourceRowType(sourceRowType, insert); // 调用父类SqlValidatorImpl的getLogicalSourceRowType方法，执行标准的源数据类型推导
    return ((JavaTypeFactory) typeFactory).toSql(superType); // 将Java类型转换为SQL兼容的类型，确保类型系统能够正确处理INSERT操作
  } // 方法结束

  @Override protected RelDataType getLogicalTargetRowType( // 重写父类方法：获取INSERT语句中目标表行的逻辑类型，该方法用于确定INSERT语句要插入数据的表的结构类型
      RelDataType targetRowType, SqlInsert insert) { // targetRowType参数是目标表行的原始类型，insert参数是INSERT语句的语法树节点
    final RelDataType superType = // 调用父类方法获取目标表行的逻辑类型，父类会根据目标表的元数据和INSERT语句的列映射进行推导
        super.getLogicalTargetRowType(targetRowType, insert); // 调用父类SqlValidatorImpl的getLogicalTargetRowType方法，执行标准的目标数据类型推导
    return ((JavaTypeFactory) typeFactory).toSql(superType); // 将Java类型转换为SQL兼容的类型，确保类型系统能够正确处理INSERT操作
  } // 方法结束
} // 类定义结束
