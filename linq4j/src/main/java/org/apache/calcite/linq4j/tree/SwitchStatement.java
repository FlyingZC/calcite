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
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于org.apache.calcite.linq4j.tree包，这是Calcite LINQ4J模块中用于处理表达式树的包

/**
 * Represents a control expression that handles multiple selections by passing
 * control to {@link SwitchCase}.
 * // 表示一个控制流表达式，通过将控制权传递给SwitchCase来处理多个选择
 * // SwitchStatement类继承自Statement，是表达式树中用于表示switch语句的抽象语法树节点
 * // 在LINQ4J中，switch语句被建模为一个表达式节点，可以用于代码生成和转换
 * // 该类作为抽象基类，定义了switch语句的基本结构和行为
 * // SwitchCase是switch语句中的case分支，包含匹配条件和对应的执行语句块
 * // 该类支持访问者模式，允许外部代码遍历和修改switch语句的结构
 */
public class SwitchStatement extends Statement { // 定义SwitchStatement类，继承自Statement基类，Statement是所有语句类型的父类
  public SwitchStatement(ExpressionType nodeType) { // 构造方法，接收一个ExpressionType参数，用于指定表达式的类型
    super(nodeType, Void.TYPE); // 调用父类Statement的构造方法，传入节点类型和返回类型Void.TYPE（表示switch语句没有返回值）
  } // 构造方法结束

  @Override public Statement accept(Shuttle shuttle) { // 重写accept方法，接受一个Shuttle访问者对象，Shuttle用于遍历和转换表达式树
    return shuttle.visit(this); // 调用shuttle的visit方法访问当前SwitchStatement节点，并返回转换后的Statement对象
  } // accept方法结束，这是访问者模式的一部分，允许外部代码修改或分析switch语句

  @Override public <R> R accept(Visitor<R> visitor) { // 重写泛型accept方法，接受一个Visitor<R>访问者对象，Visitor用于遍历表达式树并收集信息
    return visitor.visit(this); // 调用visitor的visit方法访问当前SwitchStatement节点，并返回类型为R的结果
  } // accept方法结束，这是访问者模式的另一种实现，支持泛型返回类型，用于不同的分析场景

} // 类定义结束
