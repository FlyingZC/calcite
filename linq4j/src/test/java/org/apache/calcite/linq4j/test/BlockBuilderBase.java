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
package org.apache.calcite.linq4j.test; // 指定当前类所在的包路径，属于 calcite 的 linq4j 测试包

import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入 BlockBuilder 类，用于构建代码块
import org.apache.calcite.linq4j.tree.BlockStatement; // 导入 BlockStatement 类，表示代码块语句
import org.apache.calcite.linq4j.tree.Expression; // 导入 Expression 类，表示表达式
import org.apache.calcite.linq4j.tree.Expressions; // 导入 Expressions 工具类，用于创建各种表达式
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入 ParameterExpression 类，表示参数表达式
import org.apache.calcite.linq4j.tree.Statement; // 导入 Statement 类，表示语句

/**
 * Base methods and constants for simplified Expression testing. // 简化的表达式测试的基础方法和常量类
 * 
 * 这个类是用于简化表达式测试的基类，提供了以下功能： // 类的详细作用说明
 * 1. 提供常用的常量表达式（如 NULL、TRUE、FALSE、数字常量等） // 常量表达式的作用
 * 2. 提供表达式优化方法，用于简化和优化代码块 // 优化方法的作用
 * 3. 提供参数表达式创建的便捷方法 // 参数表达式创建方法的作用
 * 
 * 这个类主要用于 linq4j 框架的单元测试，帮助测试者快速创建和操作表达式树 // 类的应用场景
 */
public final class BlockBuilderBase { // BlockBuilderBase 类，使用 final 修饰表示不能被继承
  private BlockBuilderBase() {} // 私有构造方法，防止实例化，因为这是一个工具类，只包含静态成员

  public static final Expression NULL = Expressions.constant(null); // 常量表达式：表示 null 值，类型为 Object
  public static final Expression NULL_INTEGER = // 常量表达式：表示 null 值，类型为 Integer
      Expressions.constant(null, Integer.class); // 使用 Expressions.constant 方法创建，指定类型为 Integer.class
  public static final Expression ONE = Expressions.constant(1); // 常量表达式：表示整数 1
  public static final Expression TWO = Expressions.constant(2); // 常量表达式：表示整数 2
  public static final Expression THREE = Expressions.constant(3); // 常量表达式：表示整数 3
  public static final Expression FOUR = Expressions.constant(4); // 常量表达式：表示整数 4
  public static final Expression TRUE = Expressions.constant(true); // 常量表达式：表示布尔值 true
  public static final Expression FALSE = Expressions.constant(false); // 常量表达式：表示布尔值 false

  public static final Expression TRUE_B = // 常量表达式：表示 Boolean 类的静态字段 TRUE，用于装箱类型
      Expressions.field(null, Boolean.class, "TRUE"); // 使用 Expressions.field 方法访问 Boolean.TRUE 字段
  public static final Expression FALSE_B = // 常量表达式：表示 Boolean 类的静态字段 FALSE，用于装箱类型
      Expressions.field(null, Boolean.class, "FALSE"); // 使用 Expressions.field 方法访问 Boolean.FALSE 字段

  public static String optimize(Expression expr) { // 优化表达式并返回字符串形式的方法
    return optimize(Expressions.return_(null, expr)); // 将表达式包装成 return 语句，然后调用 optimize 方法
  }

  public static BlockStatement optimizeExpression(Expression expr) { // 优化表达式并返回 BlockStatement 的方法
    return optimizeStatement(Expressions.return_(null, expr)); // 将表达式包装成 return 语句，然后调用 optimizeStatement 方法
  }

  public static String optimize(Statement statement) { // 优化语句并返回字符串形式的方法
    return optimizeStatement(statement).toString(); // 调用 optimizeStatement 方法优化语句，然后转换为字符串
  }

  public static BlockStatement optimizeStatement(Statement statement) { // 优化语句并返回 BlockStatement 的核心方法
    BlockBuilder b = new BlockBuilder(true); // 创建一个新的 BlockBuilder，参数 true 表示启用优化
    if (!(statement instanceof BlockStatement)) { // 判断语句是否不是 BlockStatement 类型
      b.add(statement); // 如果不是代码块语句，直接添加到 BlockBuilder 中
    } else { // 如果是代码块语句
      BlockStatement bs = (BlockStatement) statement; // 将语句强制转换为 BlockStatement 类型
      for (Statement stmt : bs.statements) { // 遍历代码块中的所有语句
        b.add(stmt); // 将每个语句添加到 BlockBuilder 中
      }
    }
    BlockStatement bs = b.toBlock(); // 调用 BlockBuilder 的 toBlock 方法，构建优化后的代码块
    return bs; // 返回优化后的代码块语句
  }

  public static ParameterExpression bool(String name) { // 创建布尔类型参数表达式的便捷方法
    return Expressions.parameter(boolean.class, name); // 使用 Expressions.parameter 方法创建 boolean 类型的参数
  }

  public static ParameterExpression int_(String name) { // 创建 int 基本类型参数表达式的便捷方法
    return Expressions.parameter(int.class, name); // 使用 Expressions.parameter 方法创建 int 类型的参数
  }

  public static ParameterExpression integer(String name) { // 创建 Integer 包装类型参数表达式的便捷方法
    return Expressions.parameter(Integer.class, name); // 使用 Expressions.parameter 方法创建 Integer 类型的参数
  }
}
