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
package org.apache.calcite.adapter.jdbc;  // 声明包名，该类位于JDBC适配器包中，用于处理JDBC相关的数据上下文操作

import org.apache.calcite.DataContext;  // 导入DataContext接口，Calcite中表示查询执行上下文的核心接口，提供访问schema、类型工厂、查询提供者等能力
import org.apache.calcite.adapter.java.JavaTypeFactory;  // 导入Java类型工厂，用于在Calcite类型系统和Java类型之间进行转换
import org.apache.calcite.linq4j.QueryProvider;  // 导入查询提供者接口，用于创建和执行LINQ风格的查询
import org.apache.calcite.schema.SchemaPlus;  // 导入SchemaPlus接口，代表可扩展的schema，支持动态添加子schema

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入可空注解，用于标记可能为null的返回值

import static java.lang.Integer.parseInt;  // 静态导入Integer.parseInt方法，用于字符串到整数的转换

/**
 * A special DataContext which handles correlation variable for batch nested loop joins.
 */
// 类级注释：这是一个专门的数据上下文（DataContext）实现类，用于处理批处理嵌套循环连接（batch nested loop joins）中的关联变量
// 关联变量是指在子查询或连接中引用外部查询的变量，例如：SELECT * FROM emp WHERE dept_id IN (SELECT id FROM dept WHERE name = ?)
// 批处理嵌套循环连接是Calcite优化器选择的一种连接策略，用于处理带有相关子查询的场景
// 该类通过特殊的参数索引机制，将外部查询的参数值传递给内部查询执行
public class JdbcCorrelationDataContext implements DataContext {  // 声明JdbcCorrelationDataContext类，实现DataContext接口
  // 定义一个常量偏移量，值为Integer.MAX_VALUE - 10000，用于区分关联变量参数和普通参数
  // 使用最大值减去10000是为了确保这个偏移量足够大，不会与正常的参数索引冲突
  // 在JDBC参数绑定中，正常的参数索引通常从1开始，而关联变量的参数索引会使用这个偏移量来标记
  public static final int OFFSET = Integer.MAX_VALUE - 10000;  // 声明公共静态常量OFFSET，用于标识关联变量参数的起始偏移量

  // 委托的DataContext对象，用于实际执行大部分DataContext接口方法
  // 这是一个装饰器模式的实现，JdbcCorrelationDataContext包装了一个原始的DataContext
  // 当不需要处理关联变量时，直接将调用委托给这个delegate对象
  // 例如：获取根schema、类型工厂、查询提供者等操作都直接委托给delegate
  private final DataContext delegate;  // 声明私有的最终成员变量delegate，类型为DataContext，表示被包装的原始数据上下文

  // 关联变量参数数组，存储来自外部查询的参数值
  // 这些参数值需要在内部查询执行时被访问和使用
  // 例如：在嵌套循环连接中，外部查询的每一行数据会生成一组参数，用于内部查询的执行
  // 数组的索引对应于关联变量的位置，通过OFFSET偏移量来计算实际的数组索引
  private final Object[] parameters;  // 声明私有的最终成员变量parameters，类型为Object数组，存储关联变量的参数值

  // 构造方法，创建JdbcCorrelationDataContext实例
  // @param delegate 原始的DataContext对象，将被包装在这个特殊上下文中
  // @param parameters 关联变量参数数组，包含需要传递给内部查询的参数值
  // 这个构造方法通过将原始上下文和参数数组保存到成员变量中，初始化了装饰器模式的结构
  public JdbcCorrelationDataContext(DataContext delegate, Object[] parameters) {  // 声明公共构造方法，接收delegate和parameters两个参数
    this.delegate = delegate;  // 将传入的delegate参数赋值给成员变量delegate，保存对原始数据上下文的引用
    this.parameters = parameters;  // 将传入的parameters参数赋值给成员变量parameters，保存关联变量参数数组
  }

  // 实现DataContext接口的getRootSchema方法
  // 获取根schema，这是schema树的顶层节点，包含了所有的schema定义
  // 该方法直接委托给内部的delegate对象，因为获取根schema不需要处理关联变量
  // @return 根SchemaPlus对象，可能为null，因此使用@Nullable注解标记
  @Override public @Nullable SchemaPlus getRootSchema() {  // 声明公共方法，覆盖接口方法，返回可能为null的SchemaPlus对象
    return delegate.getRootSchema();  // 直接调用delegate的getRootSchema方法，返回根schema
  }

  // 实现DataContext接口的getTypeFactory方法
  // 获取Java类型工厂，用于在Calcite类型系统和Java类型系统之间进行转换
  // 该方法直接委托给内部的delegate对象，因为获取类型工厂不需要处理关联变量
  // @return JavaTypeFactory实例，用于类型转换和类型映射
  @Override public JavaTypeFactory getTypeFactory() {  // 声明公共方法，覆盖接口方法，返回JavaTypeFactory对象
    return delegate.getTypeFactory();  // 直接调用delegate的getTypeFactory方法，返回类型工厂
  }

  // 实现DataContext接口的getQueryProvider方法
  // 获取查询提供者，用于创建和执行LINQ风格的查询
  // 该方法直接委托给内部的delegate对象，因为获取查询提供者不需要处理关联变量
  // @return QueryProvider实例，用于查询执行
  @Override public QueryProvider getQueryProvider() {  // 声明公共方法，覆盖接口方法，返回QueryProvider对象
    return delegate.getQueryProvider();  // 直接调用delegate的getQueryProvider方法，返回查询提供者
  }

  // 实现DataContext接口的get方法，这是最核心的方法，用于获取变量或参数的值
  // 该方法实现了关联变量参数的特殊处理逻辑
  // @param name 变量或参数的名称，格式可能是普通变量名或以"?"开头的参数索引
  // @return 变量或参数的值，如果找不到则返回null，因此使用@Nullable注解标记
  // 处理逻辑：
  // 1. 如果name以"?"开头，说明这是一个参数引用
  // 2. 解析出参数索引（去掉"?"后的数字）
  // 3. 检查索引是否在关联变量参数范围内（OFFSET到OFFSET + parameters.length）
  // 4. 如果在范围内，从parameters数组中返回对应的值
  // 5. 否则，委托给delegate的get方法处理
  @Override public @Nullable Object get(String name) {  // 声明公共方法，覆盖接口方法，接收name参数，返回可能为null的Object
    if (name.startsWith("?")) {  // 检查name是否以"?"开头，判断是否为参数引用
      int index = parseInt(name.substring(1));  // 去掉"?"前缀，将剩余字符串解析为整数，得到参数索引
      if (index >= OFFSET && index < OFFSET + parameters.length) {  // 检查索引是否在关联变量参数的有效范围内
        return parameters[index - OFFSET];  // 通过减去OFFSET偏移量，计算在parameters数组中的实际索引，返回对应的参数值
      }  // 结束if语句，如果索引不在有效范围内，继续执行后面的代码
    }  // 结束if语句，如果name不以"?"开头，继续执行后面的代码
    return delegate.get(name);  // 如果不是关联变量参数，委托给delegate的get方法处理，返回从delegate获取的值
  }  // 结束get方法
}  // 结束JdbcCorrelationDataContext类定义
