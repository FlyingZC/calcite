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
// 声明包名，该接口属于org.apache.calcite.linq4j.tree包，这是Calcite的LINQ4J模块中的树形结构包
package org.apache.calcite.linq4j.tree;

// 定义Node接口，这是所有解析树节点的基接口
// 在LINQ4J中，所有的表达式、语句等都实现了这个接口，形成一个抽象语法树(AST)
// 使用接口而非抽象类，允许不同的节点类型有不同的继承层次结构
/** Parse tree node. */ // Javadoc注释：说明这是一个解析树节点
public interface Node {
  // 定义一个泛型方法accept，用于访问者模式
  // <R>是泛型类型参数，表示访问者方法的返回类型
  // Visitor<R>是访问者接口，用于遍历和操作语法树
  // 参数visitor是访问者对象，包含了针对不同节点类型的处理逻辑
  // 返回值R是访问者处理后的结果，可以是任意类型
  // 这个方法是访问者模式的核心，允许在不修改节点类的情况下添加新的操作
  <R> R accept(Visitor<R> visitor);

  // 定义另一个accept方法，用于转换节点树
  // Shuttle是一个特殊的访问者，它会遍历整个语法树并可能创建新的节点
  // 参数shuttle是转换器，用于将当前节点转换成新的节点
  // 返回值Node是转换后的新节点，可能和原节点不同
  // 这个方法用于实现语法树的变换，比如优化、重写等操作
  Node accept(Shuttle shuttle);

  // 定义第三个accept方法，用于将节点输出为表达式字符串
  // ExpressionWriter是表达式写入器，负责将语法树转换为可读的代码字符串
  // 参数expressionWriter是写入器对象，用于输出当前节点的文本表示
  // 没有返回值，因为结果直接写入到expressionWriter中
  // 这个方法用于将抽象语法树转换回可编译的Java代码
  void accept(ExpressionWriter expressionWriter);
}
