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
// 声明包名，表示该类属于 org.apache.calcite.adapter.java 包，这是 Calcite 框架中 Java 适配器相关的包
package org.apache.calcite.adapter.java;

// 导入 Expression 类，用于表示 LINQ 表达式树中的表达式节点，是 Calcite LINQ4J 表达式系统的核心类型
import org.apache.calcite.linq4j.tree.Expression;
// 导入 QueryableTable 接口，定义了可查询表的行为规范，支持通过 LINQ 查询的表接口
import org.apache.calcite.schema.QueryableTable;
// 导入 SchemaPlus 类，表示 Calcite 中的模式（Schema）对象，包含表、函数等元数据信息
import org.apache.calcite.schema.SchemaPlus;
// 导入 Schemas 工具类，提供了创建表表达式等与模式相关的静态工具方法
import org.apache.calcite.schema.Schemas;
// 导入 AbstractTable 抽象类，是 Calcite 中所有表实现的基类，提供了表的基本功能
import org.apache.calcite.schema.impl.AbstractTable;

// 导入 Type 类，用于表示 Java 类型系统中的类型信息（通过反射获取）
import java.lang.reflect.Type;

/**
 * Abstract base class for implementing {@link org.apache.calcite.schema.Table}.
 * 这是一个抽象基类，用于实现 QueryableTable 接口的表
 * 
 * 类的作用详解：
 * 1. 这个类是 Calcite 框架中实现可查询表的抽象基类，专门用于基于 Java 数据源的表实现
 * 2. 它继承自 AbstractTable，提供了表的基本功能，同时实现了 QueryableTable 接口
 * 3. QueryableTable 接口表示该表可以通过 LINQ 查询进行访问和操作
 * 4. 这个类封装了元素类型（elementType）的概念，即表中每一行数据的 Java 类型
 * 5. 提供了获取表表达式（Expression）的能力，这是 Calcite 将 SQL 转换为 LINQ 表达式树的关键
 * 6. 为基于 Java 对象、集合、数组等数据源的表提供了统一的抽象实现
 * 
 * 在 Calcite 架构中的位置：
 * - 位于适配器层（adapter），是 Java 适配器的一部分
 * - 连接了 Calcite 的查询优化器（通过 Schema 和 Table 接口）和实际的 Java 数据源
 * - 是实现自定义 Java 数据表（如基于内存集合的表）的起点
 * 
 * 使用场景：
 * - 当需要将 Java 集合、数组或自定义数据源暴露为 Calcite 可查询的表时
 * - 实现 Java 适配器的表时，可以继承此类以减少重复代码
 * - 支持 LINQ 查询风格的数据访问
 */
public abstract class AbstractQueryableTable extends AbstractTable
    implements QueryableTable {
  // 成员变量：elementType - 表示表中每一行数据的 Java 类型
  // protected 访问修饰符：允许子类访问，但不允许外部直接修改
  // final 修饰符：表示该变量在构造函数初始化后不可改变，保证类型的一致性
  // Type 类型：使用 Java 反射 API 中的 Type 接口，可以表示泛型类型等复杂类型信息
  // 作用说明：
  // 1. 定义了表中每一行数据的 Java 类型，例如：Employee、Order、Map<String, Object> 等
  // 2. 这个类型信息用于 Calcite 进行类型检查、类型转换和查询优化
  // 3. 在生成 LINQ 表达式时，需要知道元素类型来正确地创建类型安全的表达式
  // 4. 对于基于集合的表，elementType 就是集合中元素的类型
  // 5. 对于基于数组的表，elementType 就是数组元素的类型
  // 6. 这个信息对于 Calcite 的查询优化器非常重要，因为它决定了如何处理表中的数据
  protected final Type elementType;

  // 构造方法：AbstractQueryableTable(Type elementType)
  // protected 访问修饰符：只能被子类调用，符合抽象类的设计模式
  // 参数说明：
  //   - elementType: Type 类型，表示表中每一行数据的 Java 类型
  // 作用说明：
  // 1. 初始化抽象基类，调用父类 AbstractTable 的无参构造函数
  // 2. 设置 elementType 成员变量，确定表的元素类型
  // 3. 子类在构造时必须指定元素类型，这是创建可查询表的基本要求
  // 4. 元素类型一旦设置就不能改变（因为字段是 final），保证了表类型的一致性
  // 5. 这个构造方法是所有子类必须调用的，确保表的基本信息被正确初始化
  protected AbstractQueryableTable(Type elementType) {
    super(); // 调用父类 AbstractTable 的无参构造函数，初始化父类的状态
    this.elementType = elementType; // 将传入的元素类型赋值给成员变量，设置表的元素类型
  }

  // 方法：getElementType()
  // @Override 注解：表示该方法重写了 QueryableTable 接口中的方法
  // public 访问修饰符：允许任何外部代码调用
  // 返回类型：Type - 返回表中每一行数据的 Java 类型
  // 作用说明：
  // 1. 实现 QueryableTable 接口要求的方法，返回表的元素类型
  // 2. Calcite 查询优化器在处理表时需要知道元素的类型信息
  // 3. 类型信息用于：
  //    - 类型检查：确保查询中的类型操作是合法的
  //    - 类型转换：在需要时进行隐式或显式的类型转换
  //    - 查询优化：根据类型信息选择最优的查询计划
  //    - 表达式生成：生成类型安全的 LINQ 表达式
  // 4. 这个方法返回的是构造函数中设置的 elementType
  // 5. 对于不同的表实现，可能返回不同的类型（如 POJO、Map、Tuple 等）
  @Override public Type getElementType() {
    return elementType; // 直接返回成员变量 elementType，即表的元素类型
  }

  // 方法：getExpression(SchemaPlus schema, String tableName, Class clazz)
  // @Override 注解：表示该方法重写了 QueryableTable 接口中的方法
  // public 访问修饰符：允许任何外部代码调用
  // 参数说明：
  //   - schema: SchemaPlus 类型，表示该表所属的模式（Schema）对象
  //            SchemaPlus 是 Calcite 中模式的增强版本，可以包含子模式和表
  //   - tableName: String 类型，表示表的名称，用于在表达式中引用该表
  //   - clazz: Class 类型，表示期望的返回类型，用于类型转换和表达式生成
  // 返回类型：Expression - 返回表示该表的 LINQ 表达式对象
  // 作用说明：
  // 1. 实现 QueryableTable 接口要求的方法，返回表示该表的 LINQ 表达式
  // 2. 这个方法是 Calcite 将 SQL 查询转换为 LINQ 表达式树的关键环节
  // 3. 表达式（Expression）是 Calcite LINQ4J 框架的核心概念，代表可执行的查询代码
  // 4. 工作流程：
  //    a. Calcite 解析 SQL 语句，生成逻辑计划
  //    b. 优化器将逻辑计划转换为物理计划
  //    c. 对于表扫描操作，需要获取表的表达式
  //    d. 调用此方法获取表的 LINQ 表达式
  //    e. 将表达式集成到整个查询的表达式树中
  // 5. 参数 schema 的作用：
  //    - 提供表的上下文信息（如表所在的模式）
  //    - 可能包含子模式、函数等元数据
  //    - 用于解析表引用和生成正确的表达式
  // 6. 参数 tableName 的作用：
  //    - 在表达式中标识表的名称
  //    - 用于生成可读的表达式代码
  //    - 在调试和日志中显示表名
  // 7. 参数 clazz 的作用：
  //    - 指定期望的表达式返回类型
  //    - 用于类型安全的表达式生成
  //    - 可能是 Queryable、Enumerable 等接口类型
  // 8. 内部实现：
  //    - 调用 Schemas.tableExpression() 静态工具方法
  //    - 传入 schema、elementType、tableName 和 clazz 参数
  //    - 该方法会创建一个表示表的表达式对象
  // 9. 返回的表达式特点：
  //    - 包含表的类型信息（elementType）
  //    - 包含表的名称（tableName）
  //    - 可以被 Calcite 的表达式求值器执行
  //    - 可以与其他表达式组合形成完整的查询
  // 10. 这个方法的重要性：
  //     - 是 Calcite 适配器机制的核心部分
  //     - 使得 Calcite 能够以统一的方式处理不同类型的数据源
  //     - 支持将 SQL 查询转换为可执行的 Java 代码
  @Override public Expression getExpression(SchemaPlus schema, String tableName,
      Class clazz) {
    // 调用 Schemas 工具类的 tableExpression 静态方法，生成表的 LINQ 表达式
    // 参数说明：
    //   - schema: 表所属的模式对象
    //   - elementType: 表的元素类型（即每一行的 Java 类型）
    //   - tableName: 表的名称
    //   - clazz: 期望的返回类型
    // 返回值：一个 Expression 对象，表示该表在 LINQ 表达式树中的表示
    return Schemas.tableExpression(schema, elementType, tableName, clazz);
  }
}
