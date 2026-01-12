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
 */ // Apache 许可证头部声明，定义了软件的使用权限和限制
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于 org.apache.calcite.linq4j.tree 包，是 LINQ4J 表达式树的核心包

import java.lang.reflect.Type; // 导入 Type 接口，用于表示 Java 类型信息，支持泛型类型参数

/**
 * Analogous to LINQ's System.Linq.Expression.
 */ // 类级别的 JavaDoc 注释：说明该类类似于 LINQ 框架中的 System.Linq.Expression，是 LINQ4J 表达式树的抽象基类
public abstract class Expression extends AbstractNode { // 定义抽象类 Expression，继承自 AbstractNode，是所有表达式节点的基类

  /**
   * Creates an Expression.
   *
   * <p>The type of the expression may, at the caller's discretion, be a
   * regular class (because {@link Class} implements {@link Type}) or it may
   * be a different implementation that retains information about type
   * parameters.
   *
   * @param nodeType Node type
   * @param type Type of the expression
   */ // 构造方法的 JavaDoc 注释：说明构造方法的作用，表达式类型可以是普通 Class 或保留类型参数信息的其他实现
  protected Expression(ExpressionType nodeType, Type type) { // 受保护的构造方法，接收节点类型和表达式类型两个参数
    super(nodeType, type); // 调用父类 AbstractNode 的构造方法，将节点类型和类型传递给父类初始化
  } // 构造方法结束

  @Override // More specific return type. // 注解：表示该方法覆盖了父类方法，且返回类型更具体（返回 Expression 而非 AbstractNode）
  public abstract Expression accept(Shuttle shuttle); // 抽象方法：接受访问者模式的 Shuttle 对象，返回转换后的 Expression，子类必须实现此方法

  /**
   * Indicates that the node can be reduced to a simpler node. If this
   * returns true, Reduce() can be called to produce the reduced form.
   */ // 方法的 JavaDoc 注释：说明该方法用于判断表达式节点是否可以简化为更简单的节点
  public boolean canReduce() { // 公共方法：判断当前表达式节点是否可以被简化（规约）
    return false; // 默认返回 false，表示大多数表达式节点不能被简化，子类可以重写此方法返回 true
  } // 方法结束
} // 类定义结束
