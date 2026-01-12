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
 */ // Apache License 2.0 许可证头部声明，说明代码版权和使用条款
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于 org.apache.calcite.linq4j.tree 包，是 LINQ4J 表达式树的一部分

import java.lang.reflect.Type; // 导入 Java 反射 Type 接口，用于表示 Java 类型

/**
 * Statement. // 语句抽象基类，表示 LINQ4J 表达式树中的语句节点（如赋值语句、返回语句、条件语句等）
 */ // 语句是程序执行的基本单元，与表达式不同，语句执行后会产生副作用或改变程序状态
public abstract class Statement extends AbstractNode { // Statement 是抽象类，继承自 AbstractNode，作为所有语句节点的基类
  protected Statement(ExpressionType nodeType, Type type) { // protected 构造方法，供子类调用，用于初始化 Statement 节点
    super(nodeType, type); // 调用父类 AbstractNode 的构造方法，传入节点类型和返回类型
  } // 构造方法结束，初始化语句节点的基本属性

  @Override final void accept(ExpressionWriter writer, int lprec, int rprec) { // 重写父类的 accept 方法，用于访问者模式遍历表达式树，final 表示子类不能重写
    assert lprec == 0; // 断言左优先级为 0，语句不需要优先级处理，因为语句不是表达式
    assert rprec == 0; // 断言右优先级为 0，语句不需要优先级处理，因为语句不是表达式
    accept0(writer); // 调用子类实现的 accept0 方法，将访问者传递给具体的语句节点进行处理
  } // accept 方法结束，完成语句节点的访问者模式处理

  // Make return type more specific. A statement can only become a different
  // kind of statement; it can't become an expression. // 使返回类型更具体，语句只能转换为另一种语句，不能转换为表达式
  @Override public abstract Statement accept(Shuttle shuttle); // 重写父类的 accept 方法，接受 Shuttle 访问者，返回转换后的 Statement，abstract 表示子类必须实现
} // Statement 类结束，这是所有语句节点的抽象基类
