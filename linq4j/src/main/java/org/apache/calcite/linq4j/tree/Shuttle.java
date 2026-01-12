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

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Extension to {@link Visitor} that returns a mutated tree.
 */
// Shuttle类是Visitor接口的扩展，用于遍历和转换表达式树（AST），它允许在遍历过程中修改树的节点并返回修改后的树结构
// 这是一个抽象基类，提供了默认的遍历行为，子类可以重写特定方法来实现自定义的转换逻辑
// Shuttle模式类似于访问者模式，但更侧重于树的转换和变异
// 它采用了preVisit-visit两阶段模式：preVisit在访问子节点前调用，visit在访问子节点后调用
public class Shuttle {
  // preVisit方法在访问WhileStatement节点的子节点之前调用，用于在遍历子节点前进行预处理
  // 参数whileStatement：即将被访问的while语句节点
  // 返回值：返回this表示继续使用当前Shuttle实例遍历子节点，子类可以返回不同的Shuttle实例来改变遍历行为
  // 默认实现直接返回this，表示不做任何预处理
  public Shuttle preVisit(WhileStatement whileStatement) {
    return this;
  }

  public Statement visit(WhileStatement whileStatement, Expression condition,
      Statement body) {
    // visit方法在访问完WhileStatement节点的所有子节点后调用，用于构建或修改该节点
    // 参数whileStatement：原始的while语句节点
    // 参数condition：处理后的条件表达式（可能是原条件，也可能是修改后的条件）
    // 参数body：处理后的循环体语句（可能是原循环体，也可能是修改后的循环体）
    // 返回值：如果条件和循环体都没有变化，返回原始节点；否则创建新的WhileStatement节点
    // 这种设计模式称为"非破坏性转换"（non-destructive transformation），即不修改原节点，而是创建新节点
    return condition == whileStatement.condition
           && body == whileStatement.body
        ? whileStatement
        : Expressions.while_(condition, body);  // 否则使用Expressions工厂方法创建新的WhileStatement节点
  }

  public Shuttle preVisit(ConditionalStatement conditionalStatement) {
    // preVisit方法在访问ConditionalStatement（if-else条件语句）节点的子节点之前调用
    // 参数conditionalStatement：即将被访问的条件语句节点
    // 返回值：默认返回this，表示不做任何预处理，子类可以重写此方法实现自定义预处理逻辑
    return this;
  }

  public Statement visit(ConditionalStatement conditionalStatement,
      List<Node> list) {
    // visit方法在访问完ConditionalStatement节点的所有子节点后调用，用于构建或修改该节点
    // 参数conditionalStatement：原始的条件语句节点
    // 参数list：处理后的表达式列表（包含if-else的各个分支条件）
    // 返回值：如果表达式列表没有变化，返回原始节点；否则使用Expressions工厂创建新的ConditionalStatement
    // ConditionalStatement是Calcite中用于表示if-else-else if...结构的一种特殊表达形式
    return list.equals(conditionalStatement.expressionList)
        ? conditionalStatement
        : Expressions.ifThenElse(list);
  }

  public Shuttle preVisit(BlockStatement blockStatement) {
    // preVisit方法在访问BlockStatement（代码块语句）节点的子节点之前调用
    // 参数blockStatement：即将被访问的代码块节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public BlockStatement visit(BlockStatement blockStatement,
      List<Statement> statements) {
    // visit方法在访问完BlockStatement节点的所有子节点后调用，用于构建或修改该节点
    // 参数blockStatement：原始的代码块节点
    // 参数statements：处理后的语句列表（代码块中的所有语句）
    // 返回值：如果语句列表没有变化，返回原始节点；否则创建新的BlockStatement节点
    return statements.equals(blockStatement.statements)
        ? blockStatement
        : Expressions.block(statements);
  }

  public Shuttle preVisit(GotoStatement gotoStatement) {
    // preVisit方法在访问GotoStatement（跳转语句）节点的子节点之前调用
    // 参数gotoStatement：即将被访问的跳转语句节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public Statement visit(GotoStatement gotoStatement, @Nullable Expression expression) {
    // visit方法在访问完GotoStatement节点的所有子节点后调用，用于构建或修改该节点
    // 参数gotoStatement：原始的跳转语句节点
    // 参数expression：处理后的表达式（用于带值的跳转，如return表达式）
    // 返回值：如果表达式没有变化，返回原始节点；否则创建新的GotoStatement节点
    // GotoStatement用于表示break、continue、return等跳转语句
    return expression == gotoStatement.expression
        ? gotoStatement
        : Expressions.makeGoto(
            gotoStatement.kind, gotoStatement.labelTarget,
            expression);
  }

  public LabelStatement visit(LabelStatement labelStatement) {
    // visit方法处理LabelStatement（标签语句）节点
    // 参数labelStatement：标签语句节点
    // 返回值：直接返回原始节点，标签语句通常不需要修改
    return labelStatement;
  }

  public Shuttle preVisit(ForStatement forStatement) {
    // preVisit方法在访问ForStatement（for循环语句）节点的子节点之前调用
    // 参数forStatement：即将被访问的for循环节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public ForStatement visit(ForStatement forStatement,
      List<DeclarationStatement> declarations, @Nullable Expression condition,
      @Nullable Expression post, Statement body) {
    // visit方法在访问完ForStatement节点的所有子节点后调用，用于构建或修改该节点
    // 参数forStatement：原始的for循环节点
    // 参数declarations：处理后的初始化声明语句列表
    // 参数condition：处理后的循环条件表达式
    // 参数post：处理后的循环后操作表达式（如i++）
    // 参数body：处理后的循环体语句
    // 返回值：如果所有子节点都没有变化，返回原始节点；否则创建新的ForStatement节点
    return declarations.equals(forStatement.declarations)
        && condition == forStatement.condition
        && post == forStatement.post
        && body == forStatement.body
        ? forStatement
        : Expressions.for_(declarations, condition, post, body);
  }

  public Shuttle preVisit(ForEachStatement forEachStatement) {
    // preVisit方法在访问ForEachStatement（foreach循环语句）节点的子节点之前调用
    // 参数forEachStatement：即将被访问的foreach循环节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public ForEachStatement visit(ForEachStatement forEachStatement,
      ParameterExpression parameter, Expression iterable, Statement body) {
    // visit方法在访问完ForEachStatement节点的所有子节点后调用，用于构建或修改该节点
    // 参数forEachStatement：原始的foreach循环节点
    // 参数parameter：处理后的迭代变量参数表达式
    // 参数iterable：处理后的可迭代表达式
    // 参数body：处理后的循环体语句
    // 返回值：如果所有子节点都没有变化，返回原始节点；否则创建新的ForEachStatement节点
    return parameter.equals(forEachStatement.parameter)
        && iterable.equals(forEachStatement.iterable)
        && body == forEachStatement.body
        ? forEachStatement
        : Expressions.forEach(parameter, iterable, body);
  }

  public Shuttle preVisit(ThrowStatement throwStatement) {
    // preVisit方法在访问ThrowStatement（抛出异常语句）节点的子节点之前调用
    // 参数throwStatement：即将被访问的抛出异常语句节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public Statement visit(ThrowStatement throwStatement, Expression expression) {
    // visit方法在访问完ThrowStatement节点的所有子节点后调用，用于构建或修改该节点
    // 参数throwStatement：原始的抛出异常语句节点
    // 参数expression：处理后的异常表达式
    // 返回值：如果异常表达式没有变化，返回原始节点；否则创建新的ThrowStatement节点
    return expression == throwStatement.expression
        ? throwStatement
        : Expressions.throw_(expression);
  }

  public Shuttle preVisit(DeclarationStatement declarationStatement) {
    // preVisit方法在访问DeclarationStatement（变量声明语句）节点的子节点之前调用
    // 参数declarationStatement：即将被访问的变量声明语句节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public DeclarationStatement visit(DeclarationStatement declarationStatement,
      @Nullable Expression initializer) {
    // visit方法在访问完DeclarationStatement节点的所有子节点后调用，用于构建或修改该节点
    // 参数declarationStatement：原始的变量声明语句节点
    // 参数initializer：处理后的初始化表达式
    // 返回值：如果初始化表达式没有变化，返回原始节点；否则创建新的DeclarationStatement节点
    return declarationStatement.initializer == initializer
        ? declarationStatement
        : Expressions.declare(
            declarationStatement.modifiers, declarationStatement.parameter,
            initializer);
  }

  public Expression visit(LambdaExpression lambdaExpression) {
    // visit方法处理LambdaExpression（lambda表达式）节点
    // 参数lambdaExpression：lambda表达式节点
    // 返回值：直接返回原始节点，lambda表达式通常不需要修改
    return lambdaExpression;
  }

  public Shuttle preVisit(FunctionExpression functionExpression) {
    // preVisit方法在访问FunctionExpression（函数表达式）节点的子节点之前调用
    // 参数functionExpression：即将被访问的函数表达式节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public Expression visit(FunctionExpression functionExpression,
      @Nullable BlockStatement body) {
    // visit方法在访问完FunctionExpression节点的所有子节点后调用，用于构建或修改该节点
    // 参数functionExpression：原始的函数表达式节点
    // 参数body：处理后的函数体（代码块）
    // 返回值：如果函数体没有变化，返回原始节点；否则创建新的FunctionExpression节点
    // FunctionExpression与LambdaExpression类似，但更接近于传统的方法定义
    return Objects.equals(body, functionExpression.body)
        ? functionExpression
        : Expressions.lambda(
            requireNonNull(body, "body"),
            functionExpression.parameterList);
  }

  public Shuttle preVisit(BinaryExpression binaryExpression) {
    // preVisit方法在访问BinaryExpression（二元表达式）节点的子节点之前调用
    // 参数binaryExpression：即将被访问的二元表达式节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public Expression visit(BinaryExpression binaryExpression,
      Expression expression0, Expression expression1) {
    // visit方法在访问完BinaryExpression节点的所有子节点后调用，用于构建或修改该节点
    // 参数binaryExpression：原始的二元表达式节点
    // 参数expression0：处理后的左操作数表达式
    // 参数expression1：处理后的右操作数表达式
    // 返回值：如果两个操作数都没有变化，返回原始节点；否则创建新的BinaryExpression节点
    // 二元表达式包括：+、-、*、/、==、!=、&&、||等运算符
    return binaryExpression.expression0 == expression0
           && binaryExpression.expression1 == expression1
        ? binaryExpression
        : Expressions.makeBinary(binaryExpression.nodeType, expression0,
            expression1);
  }

  public Shuttle preVisit(TernaryExpression ternaryExpression) {
    // preVisit方法在访问TernaryExpression（三元表达式）节点的子节点之前调用
    // 参数ternaryExpression：即将被访问的三元表达式节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public Expression visit(TernaryExpression ternaryExpression,
      Expression expression0, Expression expression1, Expression expression2) {
    // visit方法在访问完TernaryExpression节点的所有子节点后调用，用于构建或修改该节点
    // 参数ternaryExpression：原始的三元表达式节点（条件?表达式1:表达式2）
    // 参数expression0：处理后的条件表达式
    // 参数expression1：处理后的true分支表达式
    // 参数expression2：处理后的false分支表达式
    // 返回值：如果三个子表达式都没有变化，返回原始节点；否则创建新的TernaryExpression节点
    return ternaryExpression.expression0 == expression0
           && ternaryExpression.expression1 == expression1
           && ternaryExpression.expression2 == expression2
        ? ternaryExpression
        : Expressions.makeTernary(ternaryExpression.nodeType, expression0,
            expression1, expression2);
  }

  public Shuttle preVisit(IndexExpression indexExpression) {
    // preVisit方法在访问IndexExpression（数组索引表达式）节点的子节点之前调用
    // 参数indexExpression：即将被访问的数组索引表达式节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public Expression visit(IndexExpression indexExpression, Expression array,
      List<Expression> indexExpressions) {
    // visit方法在访问完IndexExpression节点的所有子节点后调用，用于构建或修改该节点
    // 参数indexExpression：原始的数组索引表达式节点
    // 参数array：处理后的数组表达式
    // 参数indexExpressions：处理后的索引表达式列表（支持多维数组）
    // 返回值：如果数组和索引都没有变化，返回原始节点；否则创建新的IndexExpression节点
    return indexExpression.array == array
           && indexExpression.indexExpressions.equals(indexExpressions)
        ? indexExpression
        : new IndexExpression(array, indexExpressions);
  }

  public Shuttle preVisit(UnaryExpression unaryExpression) {
    // preVisit方法在访问UnaryExpression（一元表达式）节点的子节点之前调用
    // 参数unaryExpression：即将被访问的一元表达式节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public Expression visit(UnaryExpression unaryExpression,
      Expression expression) {
    // visit方法在访问完UnaryExpression节点的所有子节点后调用，用于构建或修改该节点
    // 参数unaryExpression：原始的一元表达式节点
    // 参数expression：处理后的操作数表达式
    // 返回值：如果操作数没有变化，返回原始节点；否则创建新的UnaryExpression节点
    // 一元表达式包括：-（负号）、!（逻辑非）、~（按位取反）、++、--等
    return unaryExpression.expression == expression
        ? unaryExpression
        : Expressions.makeUnary(unaryExpression.nodeType, expression,
            unaryExpression.type, null);
  }

  public Shuttle preVisit(MethodCallExpression methodCallExpression) {
    // preVisit方法在访问MethodCallExpression（方法调用表达式）节点的子节点之前调用
    // 参数methodCallExpression：即将被访问的方法调用表达式节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public Expression visit(MethodCallExpression methodCallExpression,
      @Nullable Expression targetExpression, List<Expression> expressions) {
    // visit方法在访问完MethodCallExpression节点的所有子节点后调用，用于构建或修改该节点
    // 参数methodCallExpression：原始的方法调用表达式节点
    // 参数targetExpression：处理后的目标对象表达式（对于静态方法为null）
    // 参数expressions：处理后的参数表达式列表
    // 返回值：如果目标对象和参数都没有变化，返回原始节点；否则创建新的MethodCallExpression节点
    return methodCallExpression.targetExpression == targetExpression
           && methodCallExpression.expressions.equals(expressions)
        ? methodCallExpression
        : Expressions.call(targetExpression, methodCallExpression.method,
            expressions);
  }

  public Expression visit(DefaultExpression defaultExpression) {
    // visit方法处理DefaultExpression（默认值表达式）节点
    // 参数defaultExpression：默认值表达式节点
    // 返回值：直接返回原始节点，默认值表达式通常不需要修改
    return defaultExpression;
  }

  public Expression visit(DynamicExpression dynamicExpression) {
    // visit方法处理DynamicExpression（动态表达式）节点
    // 参数dynamicExpression：动态表达式节点
    // 返回值：直接返回原始节点，动态表达式通常不需要修改
    return dynamicExpression;
  }

  public Shuttle preVisit(MemberExpression memberExpression) {
    // preVisit方法在访问MemberExpression（成员访问表达式）节点的子节点之前调用
    // 参数memberExpression：即将被访问的成员访问表达式节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public Expression visit(MemberExpression memberExpression,
      @Nullable Expression expression) {
    // visit方法在访问完MemberExpression节点的所有子节点后调用，用于构建或修改该节点
    // 参数memberExpression：原始的成员访问表达式节点
    // 参数expression：处理后的目标对象表达式（对于静态字段为null）
    // 返回值：如果目标对象没有变化，返回原始节点；否则创建新的MemberExpression节点
    // 成员访问表达式用于访问对象的字段或属性，如obj.field
    return memberExpression.expression == expression
        ? memberExpression
        : Expressions.field(expression, memberExpression.field);
  }

  public Expression visit(InvocationExpression invocationExpression) {
    // visit方法处理InvocationExpression（调用表达式）节点
    // 参数invocationExpression：调用表达式节点
    // 返回值：直接返回原始节点，调用表达式通常不需要修改
    return invocationExpression;
  }

  public Shuttle preVisit(NewArrayExpression newArrayExpression) {
    // preVisit方法在访问NewArrayExpression（创建数组表达式）节点的子节点之前调用
    // 参数newArrayExpression：即将被访问的创建数组表达式节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public Expression visit(NewArrayExpression newArrayExpression, int dimension,
      @Nullable Expression bound, @Nullable List<Expression> expressions) {
    // visit方法在访问完NewArrayExpression节点的所有子节点后调用，用于构建或修改该节点
    // 参数newArrayExpression：原始的创建数组表达式节点
    // 参数dimension：数组维度
    // 参数bound：处理后的数组边界表达式（用于指定数组大小，如new int[n]）
    // 参数expressions：处理后的初始化表达式列表（用于数组初始化，如new int[]{1,2,3}）
    // 返回值：如果边界和初始化表达式都没有变化，返回原始节点；否则创建新的NewArrayExpression节点
    return Objects.equals(expressions, newArrayExpression.expressions)
        && Objects.equals(bound, newArrayExpression.bound)
        ? newArrayExpression
        : expressions == null
        ? Expressions.newArrayBounds(
            Types.getComponentTypeN(newArrayExpression.type), dimension, bound)
        : Expressions.newArrayInit(
            Types.getComponentTypeN(newArrayExpression.type),
            dimension, expressions);
  }

  public Expression visit(ListInitExpression listInitExpression) {
    // visit方法处理ListInitExpression（集合初始化表达式）节点
    // 参数listInitExpression：集合初始化表达式节点
    // 返回值：直接返回原始节点，集合初始化表达式通常不需要修改
    return listInitExpression;
  }

  public Shuttle preVisit(NewExpression newExpression) {
    // preVisit方法在访问NewExpression（创建对象表达式）节点的子节点之前调用
    // 参数newExpression：即将被访问的创建对象表达式节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public Expression visit(NewExpression newExpression,
      List<Expression> arguments, @Nullable List<MemberDeclaration> memberDeclarations) {
    // visit方法在访问完NewExpression节点的所有子节点后调用，用于构建或修改该节点
    // 参数newExpression：原始的创建对象表达式节点
    // 参数arguments：处理后的构造函数参数列表
    // 参数memberDeclarations：处理后的成员声明列表（用于匿名类）
    // 返回值：如果参数和成员声明都没有变化，返回原始节点；否则创建新的NewExpression节点
    return arguments.equals(newExpression.arguments)
        && Objects.equals(memberDeclarations, newExpression.memberDeclarations)
        ? newExpression
        : Expressions.new_(newExpression.type, arguments, memberDeclarations);
  }

  public Statement visit(SwitchStatement switchStatement) {
    // visit方法处理SwitchStatement（switch语句）节点
    // 参数switchStatement：switch语句节点
    // 返回值：直接返回原始节点，switch语句通常不需要修改
    return switchStatement;
  }

  public Shuttle preVisit(TryStatement tryStatement) {
    // preVisit方法在访问TryStatement（try-catch-finally语句）节点的子节点之前调用
    // 参数tryStatement：即将被访问的try-catch-finally语句节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public Statement visit(TryStatement tryStatement,
      Statement body, List<CatchBlock> catchBlocks, @Nullable Statement fynally) {
    // visit方法在访问完TryStatement节点的所有子节点后调用，用于构建或修改该节点
    // 参数tryStatement：原始的try-catch-finally语句节点
    // 参数body：处理后的try块语句
    // 参数catchBlocks：处理后的catch块列表
    // 参数fynally：处理后的finally块语句（注意参数名拼写为fynally以避免与关键字冲突）
    // 返回值：如果try块、catch块和finally块都没有变化，返回原始节点；否则创建新的TryStatement节点
    return body.equals(tryStatement.body)
           && Objects.equals(catchBlocks, tryStatement.catchBlocks)
           && Objects.equals(fynally, tryStatement.fynally)
           ? tryStatement
           : new TryStatement(body, catchBlocks, fynally);
  }

  public Expression visit(MemberInitExpression memberInitExpression) {
    // visit方法处理MemberInitExpression（成员初始化表达式）节点
    // 参数memberInitExpression：成员初始化表达式节点
    // 返回值：直接返回原始节点，成员初始化表达式通常不需要修改
    return memberInitExpression;
  }

  public Shuttle preVisit(TypeBinaryExpression typeBinaryExpression) {
    // preVisit方法在访问TypeBinaryExpression（类型二元表达式）节点的子节点之前调用
    // 参数typeBinaryExpression：即将被访问的类型二元表达式节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public Expression visit(TypeBinaryExpression typeBinaryExpression,
      Expression expression) {
    // visit方法在访问完TypeBinaryExpression节点的所有子节点后调用，用于构建或修改该节点
    // 参数typeBinaryExpression：原始的类型二元表达式节点（如instanceof表达式）
    // 参数expression：处理后的表达式
    // 返回值：如果表达式没有变化，返回原始节点；否则创建新的TypeBinaryExpression节点
    return typeBinaryExpression.expression == expression
        ? typeBinaryExpression
        : new TypeBinaryExpression(expression.getNodeType(), expression,
            expression.type);
  }

  public Shuttle preVisit(MethodDeclaration methodDeclaration) {
    // preVisit方法在访问MethodDeclaration（方法声明）节点的子节点之前调用
    // 参数methodDeclaration：即将被访问的方法声明节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public MemberDeclaration visit(MethodDeclaration methodDeclaration,
      BlockStatement body) {
    // visit方法在访问完MethodDeclaration节点的所有子节点后调用，用于构建或修改该节点
    // 参数methodDeclaration：原始的方法声明节点
    // 参数body：处理后的方法体（代码块）
    // 返回值：如果方法体没有变化，返回原始节点；否则创建新的MethodDeclaration节点
    return body.equals(methodDeclaration.body)
        ? methodDeclaration
        : Expressions.methodDecl(methodDeclaration.modifier,
            methodDeclaration.resultType, methodDeclaration.name,
            methodDeclaration.parameters, body);
  }

  public Shuttle preVisit(FieldDeclaration fieldDeclaration) {
    // preVisit方法在访问FieldDeclaration（字段声明）节点的子节点之前调用
    // 参数fieldDeclaration：即将被访问的字段声明节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public MemberDeclaration visit(FieldDeclaration fieldDeclaration,
      @Nullable Expression initializer) {
    // visit方法在访问完FieldDeclaration节点的所有子节点后调用，用于构建或修改该节点
    // 参数fieldDeclaration：原始的字段声明节点
    // 参数initializer：处理后的初始化表达式
    // 返回值：如果初始化表达式没有变化，返回原始节点；否则创建新的FieldDeclaration节点
    return Objects.equals(initializer, fieldDeclaration.initializer)
        ? fieldDeclaration
        : Expressions.fieldDecl(fieldDeclaration.modifier,
            fieldDeclaration.parameter, initializer);
  }

  public Expression visit(ParameterExpression parameterExpression) {
    // visit方法处理ParameterExpression（参数表达式）节点
    // 参数parameterExpression：参数表达式节点
    // 返回值：直接返回原始节点，参数表达式通常不需要修改
    return parameterExpression;
  }

  public ConstantExpression visit(ConstantExpression constantExpression) {
    // visit方法处理ConstantExpression（常量表达式）节点
    // 参数constantExpression：常量表达式节点
    // 返回值：直接返回原始节点，常量表达式通常不需要修改
    return constantExpression;
  }

  public Shuttle preVisit(ClassDeclaration classDeclaration) {
    // preVisit方法在访问ClassDeclaration（类声明）节点的子节点之前调用
    // 参数classDeclaration：即将被访问的类声明节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public ClassDeclaration visit(ClassDeclaration classDeclaration,
      List<MemberDeclaration> memberDeclarations) {
    // visit方法在访问完ClassDeclaration节点的所有子节点后调用，用于构建或修改该节点
    // 参数classDeclaration：原始的类声明节点
    // 参数memberDeclarations：处理后的成员声明列表（包括字段、方法、构造函数等）
    // 返回值：如果成员声明列表没有变化，返回原始节点；否则创建新的ClassDeclaration节点
    return Objects.equals(memberDeclarations,
        classDeclaration.memberDeclarations)
        ? classDeclaration
        : Expressions.classDecl(classDeclaration.modifier,
            classDeclaration.name, classDeclaration.extended,
            classDeclaration.implemented, memberDeclarations);
  }

  public Shuttle preVisit(ConstructorDeclaration constructorDeclaration) {
    // preVisit方法在访问ConstructorDeclaration（构造函数声明）节点的子节点之前调用
    // 参数constructorDeclaration：即将被访问的构造函数声明节点
    // 返回值：默认返回this，表示不做任何预处理
    return this;
  }

  public MemberDeclaration visit(ConstructorDeclaration constructorDeclaration,
      BlockStatement body) {
    // visit方法在访问完ConstructorDeclaration节点的所有子节点后调用，用于构建或修改该节点
    // 参数constructorDeclaration：原始的构造函数声明节点
    // 参数body：处理后的构造函数体（代码块）
    // 返回值：如果构造函数体没有变化，返回原始节点；否则创建新的ConstructorDeclaration节点
    return body.equals(constructorDeclaration.body)
        ? constructorDeclaration
        : Expressions.constructorDecl(constructorDeclaration.modifier,
            constructorDeclaration.resultType,
            constructorDeclaration.parameters,
            body);
  }
}
