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
package org.apache.calcite.linq4j.tree;

/**
 * Node visitor. // 节点访问者接口，使用访问者模式遍历和处理代码树（AST）中的各种节点
 *
 * @param <R> Return type // 访问方法的返回类型，用于支持不同的访问者返回不同类型的结果
 */ // 访者模式允许在不修改节点类的情况下定义新的操作，通过将操作封装在访问者对象中实现
public interface Visitor<R> { // 这是一个泛型接口，R表示访问者方法的返回类型，可以根据具体需求返回任意类型的结果
  R visit(BinaryExpression binaryExpression); // 访问二元表达式节点，处理如加、减、乘、除等二元运算操作，返回类型R的结果
  R visit(BlockStatement blockStatement); // 访问代码块语句节点，处理包含多条语句的代码块，返回类型R的结果
  R visit(ClassDeclaration classDeclaration); // 访问类声明节点，处理类的定义信息，返回类型R的结果
  R visit(ConditionalExpression conditionalExpression); // 访问条件表达式节点，处理三元运算符(?:)等条件表达式，返回类型R的结果
  R visit(ConditionalStatement conditionalStatement); // 访问条件语句节点，处理if-else等条件控制语句，返回类型R的结果
  R visit(ConstantExpression constantExpression); // 访问常量表达式节点，处理字面量常量值，返回类型R的结果
  R visit(ConstructorDeclaration constructorDeclaration); // 访问构造函数声明节点，处理类的构造函数定义，返回类型R的结果
  R visit(DeclarationStatement declarationStatement); // 访问声明语句节点，处理变量声明语句，返回类型R的结果
  R visit(DefaultExpression defaultExpression); // 访问默认值表达式节点，处理默认值表达式，返回类型R的结果
  R visit(DynamicExpression dynamicExpression); // 访问动态表达式节点，处理动态绑定的表达式，返回类型R的结果
  R visit(FieldDeclaration fieldDeclaration); // 访问字段声明节点，处理类的字段定义，返回类型R的结果
  R visit(ForStatement forStatement); // 访问for循环语句节点，处理传统的for循环控制结构，返回类型R的结果
  R visit(ForEachStatement forEachStatement); // 访问for-each循环语句节点，处理增强for循环（遍历集合），返回类型R的结果
  R visit(FunctionExpression functionExpression); // 访问函数表达式节点，处理函数定义和调用，返回类型R的结果
  R visit(GotoStatement gotoStatement); // 访问跳转语句节点，处理goto、break、continue等跳转控制语句，返回类型R的结果
  R visit(IndexExpression indexExpression); // 访问索引表达式节点，处理数组或集合的索引访问操作，返回类型R的结果
  R visit(InvocationExpression invocationExpression); // 访问调用表达式节点，处理方法或委托的调用操作，返回类型R的结果
  R visit(LabelStatement labelStatement); // 访问标签语句节点，处理代码标签定义，返回类型R的结果
  R visit(LambdaExpression lambdaExpression); // 访问Lambda表达式节点，处理匿名函数（lambda表达式），返回类型R的结果
  R visit(ListInitExpression listInitExpression); // 访问列表初始化表达式节点，处理集合或数组的初始化语法，返回类型R的结果
  R visit(MemberExpression memberExpression); // 访问成员表达式节点，处理对象字段或属性的访问操作，返回类型R的结果
  R visit(MemberInitExpression memberInitExpression); // 访问成员初始化表达式节点，处理对象成员的初始化操作，返回类型R的结果
  R visit(MethodCallExpression methodCallExpression); // 访问方法调用表达式节点，处理实例方法或静态方法的调用，返回类型R的结果
  R visit(MethodDeclaration methodDeclaration); // 访问方法声明节点，处理类的方法定义，返回类型R的结果
  R visit(NewArrayExpression newArrayExpression); // 访问创建数组表达式节点，处理数组的创建和初始化，返回类型R的结果
  R visit(NewExpression newExpression); // 访问创建对象表达式节点，处理使用new关键字创建对象的操作，返回类型R的结果
  R visit(ParameterExpression parameterExpression); // 访问参数表达式节点，处理方法或Lambda表达式的参数定义，返回类型R的结果
  R visit(SwitchStatement switchStatement); // 访问switch语句节点，处理多分支选择控制结构，返回类型R的结果
  R visit(TernaryExpression ternaryExpression); // 访问三元表达式节点，处理三元条件运算符，返回类型R的结果
  R visit(ThrowStatement throwStatement); // 访问抛出异常语句节点，处理throw语句抛出异常，返回类型R的结果
  R visit(TryStatement tryStatement); // 访问try-catch语句节点，处理异常捕获和处理结构，返回类型R的结果
  R visit(TypeBinaryExpression typeBinaryExpression); // 访问类型二元表达式节点，处理类型相关的二元操作（如is、as），返回类型R的结果
  R visit(UnaryExpression unaryExpression); // 访问一元表达式节点，处理如取反、自增、自减等一元运算操作，返回类型R的结果
  R visit(WhileStatement whileStatement); // 访问while循环语句节点，处理while循环控制结构，返回类型R的结果
}
