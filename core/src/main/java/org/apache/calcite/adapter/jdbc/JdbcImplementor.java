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
package org.apache.calcite.adapter.jdbc; // JDBC适配器包，包含JDBC相关的适配器实现

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于处理Java类型和Calcite类型之间的转换
import org.apache.calcite.rel.RelNode; // 导入关系表达式节点接口，代表关系代数中的一个操作
import org.apache.calcite.rel.core.CorrelationId; // 导入关联ID，用于标识关联变量
import org.apache.calcite.rel.rel2sql.RelToSqlConverter; // 导入关系表达式到SQL转换器的基类
import org.apache.calcite.rel.rel2sql.SqlImplementor; // 导入SQL实现器接口，定义了SQL生成的基本行为
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段，描述字段的类型信息
import org.apache.calcite.rex.RexCorrelVariable; // 导入行表达式关联变量，代表关联查询中的变量
import org.apache.calcite.sql.SqlDialect; // 导入SQL方言，用于处理不同数据库的SQL语法差异
import org.apache.calcite.sql.SqlDynamicParam; // 导入SQL动态参数，代表预处理语句中的参数占位符
import org.apache.calcite.sql.SqlNode; // 导入SQL节点接口，代表SQL语法树中的一个节点
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SQL解析位置，用于标记SQL语法树中节点的位置

import java.lang.reflect.Type; // 导入Java反射Type类，用于表示Java类型
import java.util.List; // 导入List集合接口，用于存储字段列表

/**
 * State for generating a SQL statement. // 用于生成SQL语句的状态类
 * 这个类是JDBC适配器中的核心类，负责将Calcite的关系表达式(RelNode)转换为可以在JDBC数据源上执行的SQL语句
 * 它继承自RelToSqlConverter，扩展了SQL转换能力，特别是处理关联查询和动态参数的能力
 * 主要功能：1. 将关系表达式树转换为SQL语法树 2. 处理关联变量和动态参数 3. 生成符合目标数据库方言的SQL语句
 */
public class JdbcImplementor extends RelToSqlConverter { // 定义JdbcImplementor类，继承自RelToSqlConverter基类

  private final JdbcCorrelationDataContextBuilder dataContextBuilder; // 关联数据上下文构建器，用于为关联变量分配动态参数索引
  private final JavaTypeFactory typeFactory; // Java类型工厂，用于将Calcite类型转换为Java类型

  JdbcImplementor(SqlDialect dialect, JavaTypeFactory typeFactory, // 构造方法：接收SQL方言、Java类型工厂和关联数据上下文构建器
      JdbcCorrelationDataContextBuilder dataContextBuilder) { // 参数：dialect-目标数据库的SQL方言，typeFactory-类型转换工厂，dataContextBuilder-关联变量参数构建器
    super(dialect); // 调用父类RelToSqlConverter的构造方法，传入SQL方言
    this.typeFactory = typeFactory; // 保存Java类型工厂引用，用于后续类型转换
    this.dataContextBuilder = dataContextBuilder; // 保存关联数据上下文构建器引用，用于处理关联变量
  }

  public JdbcImplementor(SqlDialect dialect, JavaTypeFactory typeFactory) { // 公开构造方法：只接收SQL方言和Java类型工厂
    this(dialect, typeFactory, new JdbcCorrelationDataContextBuilder() { // 调用完整构造方法，创建一个默认的关联数据上下文构建器匿名实现
      private int counter = 1; // 私有计数器，从1开始为每个关联变量分配唯一的参数索引
      @Override public int add(CorrelationId id, int ordinal, Type type) { // 重写add方法，为关联变量添加动态参数并返回参数索引
        return counter++; // 返回当前计数器值并递增，确保每个参数都有唯一索引
      }
    });
  }

  public Result implement(RelNode node) { // 实现方法：将关系表达式节点转换为SQL结果
    return dispatch(node); // 调用dispatch方法分发到具体的转换逻辑，返回转换后的SQL结果对象
  }

  @Override protected Context getAliasContext(RexCorrelVariable variable) { // 重写方法：获取关联变量的别名上下文，用于处理关联查询中的变量引用
    Context context = correlTableMap.get(variable.id); // 从关联表映射中查找该关联ID对应的上下文
    if (context != null) { // 如果找到了已存在的上下文
      return context; // 直接返回该上下文，避免重复创建
    }
    List<RelDataTypeField>  fieldList = variable.getType().getFieldList(); // 获取关联变量的字段列表，包含所有字段的类型信息
    // We need to provide a context which also includes the correlation variables // 我们需要提供一个包含关联变量的上下文
    // as dynamic parameters. // 将关联变量作为动态参数处理
    return new Context(dialect, fieldList.size()) { // 创建一个新的Context匿名子类，传入方言和字段数量
      @Override public SqlNode field(int ordinal) { // 重写field方法，根据字段序号生成对应的SQL节点
        RelDataTypeField field = fieldList.get(ordinal); // 从字段列表中获取指定序号的字段信息
        return new SqlDynamicParam( // 创建一个动态参数节点，代表预处理语句中的参数占位符
            dataContextBuilder.add(variable.id, ordinal, // 调用数据上下文构建器添加参数，传入关联ID、字段序号和Java类型
            typeFactory.getJavaClass(field.getType())), SqlParserPos.ZERO); // 获取字段的Java类型，并设置解析位置为零位置
      }

      @Override public SqlImplementor implementor() { // 重写implementor方法，返回当前SQL实现器实例
        return JdbcImplementor.this; // 返回当前JdbcImplementor实例，用于回调
      }
    };
  }
}
