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
 */ // Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.linq4j.tree; // 包声明，该类属于org.apache.calcite.linq4j.tree包

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值

import java.util.List; // 导入List接口，用于处理集合类型

/**
 * Default implementation of {@link Visitor}, which traverses a tree but does
 * nothing. In a derived class you can override selected methods.
 *
 * @param <R> Return type
 */ // 类文档注释：这是Visitor接口的默认实现类，用于遍历表达式树但不执行任何操作，派生类可以重写选定的方法来实现自定义逻辑，R是返回类型的泛型参数
@SuppressWarnings("unused") // 抑制未使用方法的警告，因为这是一个基类，方法可能在子类中使用
public class VisitorImpl<@Nullable R> implements Visitor<R> { // VisitorImpl类定义，实现了Visitor接口，R是泛型返回类型，可为null
  public VisitorImpl() { // 默认构造方法，创建VisitorImpl实例
    super(); // 调用父类Object的构造方法
  } // 构造方法结束

  @Override public R visit(BinaryExpression binaryExpression) { // 访问二元表达式节点的方法，参数是BinaryExpression对象，返回类型为R
    R r0 = binaryExpression.expression0.accept(this); // 访问二元表达式的第一个操作数，调用其accept方法并将当前访问者传入，返回结果存储在r0中
    R r1 = binaryExpression.expression1.accept(this); // 访问二元表达式的第二个操作数，调用其accept方法并将当前访问者传入，返回结果存储在r1中
    return r1; // 返回第二个操作数的访问结果
  } // visit(BinaryExpression)方法结束

  @Override public R visit(BlockStatement blockStatement) { // 访问块语句节点的方法，参数是BlockStatement对象，返回类型为R
    return Expressions.acceptNodes(blockStatement.statements, this); // 使用Expressions工具类的acceptNodes方法遍历块语句中的所有语句，传入语句列表和当前访问者
  } // visit(BlockStatement)方法结束

  @Override public R visit(ClassDeclaration classDeclaration) { // 访问类声明节点的方法，参数是ClassDeclaration对象，返回类型为R
    return Expressions.acceptNodes(classDeclaration.memberDeclarations, this); // 使用Expressions工具类的acceptNodes方法遍历类声明中的所有成员声明，传入成员列表和当前访问者
  } // visit(ClassDeclaration)方法结束

  @Override public R visit(ConditionalExpression conditionalExpression) { // 访问条件表达式节点的方法，参数是ConditionalExpression对象，返回类型为R
    return Expressions.acceptNodes(conditionalExpression.expressionList, this); // 使用Expressions工具类的acceptNodes方法遍历条件表达式中的所有子表达式，传入表达式列表和当前访问者
  } // visit(ConditionalExpression)方法结束

  @Override public R visit(ConditionalStatement conditionalStatement) { // 访问条件语句节点的方法，参数是ConditionalStatement对象，返回类型为R
    return Expressions.acceptNodes(conditionalStatement.expressionList, this); // 使用Expressions工具类的acceptNodes方法遍历条件语句中的所有表达式，传入表达式列表和当前访问者
  } // visit(ConditionalStatement)方法结束

  @Override public R visit(ConstantExpression constantExpression) { // 访问常量表达式节点的方法，参数是ConstantExpression对象，返回类型为R
    return null; // 返回null，因为常量表达式不需要进一步遍历
  } // visit(ConstantExpression)方法结束

  @Override public R visit(ConstructorDeclaration constructorDeclaration) { // 访问构造函数声明节点的方法，参数是ConstructorDeclaration对象，返回类型为R
    R r0 = Expressions.acceptNodes(constructorDeclaration.parameters, this); // 使用Expressions工具类的acceptNodes方法遍历构造函数的参数列表，传入参数列表和当前访问者，结果存储在r0中
    return constructorDeclaration.body.accept(this); // 访问构造函数的函数体，调用其accept方法并将当前访问者传入，返回访问结果
  } // visit(ConstructorDeclaration)方法结束

  @Override public R visit(DeclarationStatement declarationStatement) { // 访问声明语句节点的方法，参数是DeclarationStatement对象，返回类型为R
    R r = declarationStatement.parameter.accept(this); // 访问声明语句中的参数（变量声明），调用其accept方法并将当前访问者传入，结果存储在r中
    if (declarationStatement.initializer != null) { // 检查声明语句是否有初始化表达式
      r = declarationStatement.initializer.accept(this); // 如果有初始化表达式，则访问该表达式，调用其accept方法并将当前访问者传入，结果覆盖r
    } // if语句结束
    return r; // 返回最后一个访问结果
  } // visit(DeclarationStatement)方法结束

  @Override public R visit(DefaultExpression defaultExpression) { // 访问默认表达式节点的方法，参数是DefaultExpression对象，返回类型为R
    return null; // 返回null，因为默认表达式不需要进一步遍历
  } // visit(DefaultExpression)方法结束

  @Override public R visit(DynamicExpression dynamicExpression) { // 访问动态表达式节点的方法，参数是DynamicExpression对象，返回类型为R
    return null; // 返回null，因为动态表达式不需要进一步遍历
  } // visit(DynamicExpression)方法结束

  @Override public R visit(FieldDeclaration fieldDeclaration) { // 访问字段声明节点的方法，参数是FieldDeclaration对象，返回类型为R
    R r0 = fieldDeclaration.parameter.accept(this); // 访问字段声明中的参数（字段本身），调用其accept方法并将当前访问者传入，结果存储在r0中
    return fieldDeclaration.initializer == null ? null // 检查字段是否有初始化表达式，如果没有则返回null
        : fieldDeclaration.initializer.accept(this); // 如果有初始化表达式，则访问该表达式，调用其accept方法并将当前访问者传入，返回访问结果
  } // visit(FieldDeclaration)方法结束

  @Override public R visit(ForStatement forStatement) { // 访问for循环语句节点的方法，参数是ForStatement对象，返回类型为R
    R r0 = Expressions.acceptNodes(forStatement.declarations, this); // 使用Expressions工具类的acceptNodes方法遍历for循环的初始化声明列表，传入声明列表和当前访问者，结果存储在r0中
    R r1 = forStatement.condition == null ? null : forStatement.condition.accept(this); // 检查for循环是否有条件表达式，如果没有则返回null，如果有则访问该条件表达式，调用其accept方法并将当前访问者传入，结果存储在r1中
    R r2 = forStatement.post == null ? null : forStatement.post.accept(this); // 检查for循环是否有后置表达式（增量表达式），如果没有则返回null，如果有则访问该后置表达式，调用其accept方法并将当前访问者传入，结果存储在r2中
    return forStatement.body.accept(this); // 访问for循环的循环体，调用其accept方法并将当前访问者传入，返回访问结果
  } // visit(ForStatement)方法结束

  @Override public R visit(ForEachStatement forEachStatement) { // 访问foreach循环语句节点的方法，参数是ForEachStatement对象，返回类型为R
    R r0 = forEachStatement.parameter.accept(this); // 访问foreach循环中的参数（迭代变量），调用其accept方法并将当前访问者传入，结果存储在r0中
    R r1 = forEachStatement.iterable.accept(this); // 访问foreach循环中的可迭代对象，调用其accept方法并将当前访问者传入，结果存储在r1中
    return forEachStatement.body.accept(this); // 访问foreach循环的循环体，调用其accept方法并将当前访问者传入，返回访问结果
  } // visit(ForEachStatement)方法结束

  @Override public R visit(FunctionExpression functionExpression) { // 访问函数表达式节点的方法，参数是FunctionExpression对象，返回类型为R
    @SuppressWarnings("unchecked") final List<Node> parameterList = // 抑制未检查类型转换的警告，因为parameterList的类型转换是安全的
        functionExpression.parameterList; // 获取函数表达式的参数列表，并将其转换为Node类型的List
    R r0 = Expressions.acceptNodes(parameterList, this); // 使用Expressions工具类的acceptNodes方法遍历函数表达式的参数列表，传入参数列表和当前访问者，结果存储在r0中
    return functionExpression.body == null ? null : functionExpression.body.accept(this); // 检查函数表达式是否有函数体，如果没有则返回null，如果有则访问该函数体，调用其accept方法并将当前访问者传入，返回访问结果
  } // visit(FunctionExpression)方法结束

  @Override public R visit(GotoStatement gotoStatement) { // 访问goto语句节点的方法，参数是GotoStatement对象，返回类型为R
    return gotoStatement.expression == null ? null // 检查goto语句是否有表达式（标签表达式），如果没有则返回null
        : gotoStatement.expression.accept(this); // 如果有表达式，则访问该表达式，调用其accept方法并将当前访问者传入，返回访问结果
  } // visit(GotoStatement)方法结束

  @Override public R visit(IndexExpression indexExpression) { // 访问索引表达式节点的方法，参数是IndexExpression对象，返回类型为R
    R r0 = indexExpression.array.accept(this); // 访问索引表达式中的数组对象，调用其accept方法并将当前访问者传入，结果存储在r0中
    return Expressions.acceptNodes(indexExpression.indexExpressions, this); // 使用Expressions工具类的acceptNodes方法遍历索引表达式中的所有索引表达式，传入索引列表和当前访问者，返回访问结果
  } // visit(IndexExpression)方法结束

  @Override public R visit(InvocationExpression invocationExpression) { // 访问调用表达式节点的方法，参数是InvocationExpression对象，返回类型为R
    return null; // 返回null，因为调用表达式不需要进一步遍历
  } // visit(InvocationExpression)方法结束

  @Override public R visit(LabelStatement labelStatement) { // 访问标签语句节点的方法，参数是LabelStatement对象，返回类型为R
    return labelStatement.defaultValue.accept(this); // 访问标签语句中的默认值表达式，调用其accept方法并将当前访问者传入，返回访问结果
  } // visit(LabelStatement)方法结束

  @Override public R visit(LambdaExpression lambdaExpression) { // 访问lambda表达式节点的方法，参数是LambdaExpression对象，返回类型为R
    return null; // 返回null，因为lambda表达式不需要进一步遍历
  } // visit(LambdaExpression)方法结束

  @Override public R visit(ListInitExpression listInitExpression) { // 访问列表初始化表达式节点的方法，参数是ListInitExpression对象，返回类型为R
    return null; // 返回null，因为列表初始化表达式不需要进一步遍历
  } // visit(ListInitExpression)方法结束

  @Override public R visit(MemberExpression memberExpression) { // 访问成员表达式节点的方法，参数是MemberExpression对象，返回类型为R
    R r = null; // 初始化返回结果r为null
    if (memberExpression.expression != null) { // 检查成员表达式是否有目标表达式（即访问成员的对象）
      r = memberExpression.expression.accept(this); // 如果有目标表达式，则访问该表达式，调用其accept方法并将当前访问者传入，结果存储在r中
    } // if语句结束
    return r; // 返回访问结果
  } // visit(MemberExpression)方法结束

  @Override public R visit(MemberInitExpression memberInitExpression) { // 访问成员初始化表达式节点的方法，参数是MemberInitExpression对象，返回类型为R
    return null; // 返回null，因为成员初始化表达式不需要进一步遍历
  } // visit(MemberInitExpression)方法结束

  @Override public R visit(MethodCallExpression methodCallExpression) { // 访问方法调用表达式节点的方法，参数是MethodCallExpression对象，返回类型为R
    R r = null; // 初始化返回结果r为null
    if (methodCallExpression.targetExpression != null) { // 检查方法调用表达式是否有目标表达式（即调用方法的对象）
      r = methodCallExpression.targetExpression.accept(this); // 如果有目标表达式，则访问该表达式，调用其accept方法并将当前访问者传入，结果存储在r中
    } // if语句结束
    return Expressions.acceptNodes(methodCallExpression.expressions, this); // 使用Expressions工具类的acceptNodes方法遍历方法调用表达式中的所有参数表达式，传入参数列表和当前访问者，返回访问结果
  } // visit(MethodCallExpression)方法结束

  @Override public R visit(MethodDeclaration methodDeclaration) { // 访问方法声明节点的方法，参数是MethodDeclaration对象，返回类型为R
    R r0 = Expressions.acceptNodes(methodDeclaration.parameters, this); // 使用Expressions工具类的acceptNodes方法遍历方法声明的参数列表，传入参数列表和当前访问者，结果存储在r0中
    return methodDeclaration.body.accept(this); // 访问方法声明的函数体，调用其accept方法并将当前访问者传入，返回访问结果
  } // visit(MethodDeclaration)方法结束

  @Override public R visit(NewArrayExpression newArrayExpression) { // 访问新建数组表达式节点的方法，参数是NewArrayExpression对象，返回类型为R
    R r = null; // 初始化返回结果r为null
    if (newArrayExpression.bound != null) { // 检查新建数组表达式是否有边界表达式（数组大小）
      r = newArrayExpression.bound.accept(this); // 如果有边界表达式，则访问该表达式，调用其accept方法并将当前访问者传入，结果存储在r中
    } // if语句结束
    return Expressions.acceptNodes(newArrayExpression.expressions, this); // 使用Expressions工具类的acceptNodes方法遍历新建数组表达式中的所有初始化表达式，传入表达式列表和当前访问者，返回访问结果
  } // visit(NewArrayExpression)方法结束

  @Override public R visit(NewExpression newExpression) { // 访问新建对象表达式节点的方法，参数是NewExpression对象，返回类型为R
    R r0 = Expressions.acceptNodes(newExpression.arguments, this); // 使用Expressions工具类的acceptNodes方法遍历新建对象表达式中的所有构造函数参数，传入参数列表和当前访问者，结果存储在r0中
    return Expressions.acceptNodes(newExpression.memberDeclarations, this); // 使用Expressions工具类的acceptNodes方法遍历新建对象表达式中的所有成员声明（用于匿名类），传入成员列表和当前访问者，返回访问结果
  } // visit(NewExpression)方法结束

  @Override public R visit(ParameterExpression parameterExpression) { // 访问参数表达式节点的方法，参数是ParameterExpression对象，返回类型为R
    return null; // 返回null，因为参数表达式不需要进一步遍历
  } // visit(ParameterExpression)方法结束

  @Override public R visit(SwitchStatement switchStatement) { // 访问switch语句节点的方法，参数是SwitchStatement对象，返回类型为R
    return null; // 返回null，因为switch语句不需要进一步遍历
  } // visit(SwitchStatement)方法结束

  @Override public R visit(TernaryExpression ternaryExpression) { // 访问三元运算符表达式节点的方法，参数是TernaryExpression对象，返回类型为R
    R r0 = ternaryExpression.expression0.accept(this); // 访问三元表达式的条件表达式，调用其accept方法并将当前访问者传入，结果存储在r0中
    R r1 = ternaryExpression.expression1.accept(this); // 访问三元表达式的true分支表达式，调用其accept方法并将当前访问者传入，结果存储在r1中
    return ternaryExpression.expression2.accept(this); // 访问三元表达式的false分支表达式，调用其accept方法并将当前访问者传入，返回访问结果
  } // visit(TernaryExpression)方法结束

  @Override public R visit(ThrowStatement throwStatement) { // 访问throw语句节点的方法，参数是ThrowStatement对象，返回类型为R
    return throwStatement.expression.accept(this); // 访问throw语句中的异常表达式，调用其accept方法并将当前访问者传入，返回访问结果
  } // visit(ThrowStatement)方法结束

  @Override public R visit(TryStatement tryStatement) { // 访问try-catch-finally语句节点的方法，参数是TryStatement对象，返回类型为R
    R r = tryStatement.body.accept(this); // 访问try语句的try块，调用其accept方法并将当前访问者传入，结果存储在r中
    for (CatchBlock catchBlock : tryStatement.catchBlocks) { // 遍历try语句的所有catch块
      r = catchBlock.parameter.accept(this); // 访问catch块的参数（异常类型），调用其accept方法并将当前访问者传入，结果覆盖r
      r = catchBlock.body.accept(this); // 访问catch块的函数体，调用其accept方法并将当前访问者传入，结果覆盖r
    } // for循环结束
    if (tryStatement.fynally != null) { // 检查try语句是否有finally块（注意拼写为fynally）
      r = tryStatement.fynally.accept(this); // 如果有finally块，则访问该块，调用其accept方法并将当前访问者传入，结果覆盖r
    } // if语句结束
    return r; // 返回最后一个访问结果
  } // visit(TryStatement)方法结束

  @Override public R visit(TypeBinaryExpression typeBinaryExpression) { // 访问类型二元表达式节点的方法，参数是TypeBinaryExpression对象，返回类型为R
    return typeBinaryExpression.expression.accept(this); // 访问类型二元表达式中的表达式，调用其accept方法并将当前访问者传入，返回访问结果
  } // visit(TypeBinaryExpression)方法结束

  @Override public R visit(UnaryExpression unaryExpression) { // 访问一元表达式节点的方法，参数是UnaryExpression对象，返回类型为R
    return unaryExpression.expression.accept(this); // 访问一元表达式中的操作数，调用其accept方法并将当前访问者传入，返回访问结果
  } // visit(UnaryExpression)方法结束

  @Override public R visit(WhileStatement whileStatement) { // 访问while循环语句节点的方法，参数是WhileStatement对象，返回类型为R
    R r0 = whileStatement.condition.accept(this); // 访问while循环的条件表达式，调用其accept方法并将当前访问者传入，结果存储在r0中
    return whileStatement.body.accept(this); // 访问while循环的循环体，调用其accept方法并将当前访问者传入，返回访问结果
  } // visit(WhileStatement)方法结束

} // VisitorImpl类定义结束
