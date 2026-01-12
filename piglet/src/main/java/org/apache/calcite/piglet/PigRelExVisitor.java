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
package org.apache.calcite.piglet; // 声明包名,该类属于org.apache.calcite.piglet包,用于Piglet适配器模块

import org.apache.calcite.rel.RelNode; // 导入Calcite的关系节点类,表示关系代数操作
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型类,描述字段类型
import org.apache.calcite.rex.RexCall; // 导入Rex调用表达式类,表示函数调用
import org.apache.calcite.rex.RexLiteral; // 导入Rex字面量类,表示常量值
import org.apache.calcite.rex.RexNode; // 导入Rex节点基类,表示行表达式
import org.apache.calcite.rex.RexSubQuery; // 导入Rex子查询类,表示子查询表达式
import org.apache.calcite.rex.RexUtil; // 导入Rex工具类,提供Rex节点操作的辅助方法
import org.apache.calcite.schema.impl.ScalarFunctionImpl; // 导入标量函数实现类,表示用户定义的标量函数
import org.apache.calcite.sql.SqlOperator; // 导入SQL操作符接口,表示SQL操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入标准SQL操作符表,提供所有标准SQL操作符
import org.apache.calcite.sql.type.MultisetSqlType; // 导入多重集SQL类型,表示集合类型
import org.apache.calcite.sql.validate.SqlUserDefinedFunction; // 导入SQL用户定义函数类
import org.apache.calcite.util.NlsString; // 导入国际化字符串类,用于本地化字符串

import org.apache.pig.impl.logicalLayer.FrontendException; // 导入Pig前端异常类,表示Pig操作处理异常
import org.apache.pig.newplan.Operator; // 导入Pig操作符基类,表示Pig逻辑计划中的操作符
import org.apache.pig.newplan.OperatorPlan; // 导入Pig操作符计划类,表示Pig逻辑计划
import org.apache.pig.newplan.PlanWalker; // 导入Pig计划遍历器接口,用于遍历Pig计划
import org.apache.pig.newplan.logical.expression.AddExpression; // 导入Pig加法表达式类
import org.apache.pig.newplan.logical.expression.AndExpression; // 导入Pig逻辑与表达式类
import org.apache.pig.newplan.logical.expression.BinCondExpression; // 导入Pig二元条件表达式类(三元运算符)
import org.apache.pig.newplan.logical.expression.CastExpression; // 导入Pig类型转换表达式类
import org.apache.pig.newplan.logical.expression.ConstantExpression; // 导入Pig常量表达式类
import org.apache.pig.newplan.logical.expression.DereferenceExpression; // 导入Pig解引用表达式类(字段访问)
import org.apache.pig.newplan.logical.expression.DivideExpression; // 导入Pig除法表达式类
import org.apache.pig.newplan.logical.expression.EqualExpression; // 导入Pig等于表达式类
import org.apache.pig.newplan.logical.expression.GreaterThanEqualExpression; // 导入Pig大于等于表达式类
import org.apache.pig.newplan.logical.expression.GreaterThanExpression; // 导入Pig大于表达式类
import org.apache.pig.newplan.logical.expression.IsNullExpression; // 导入Pig判断为空表达式类
import org.apache.pig.newplan.logical.expression.LessThanEqualExpression; // 导入Pig小于等于表达式类
import org.apache.pig.newplan.logical.expression.LessThanExpression; // 导入Pig小于表达式类
import org.apache.pig.newplan.logical.expression.LogicalExpressionPlan; // 导入Pig逻辑表达式计划类
import org.apache.pig.newplan.logical.expression.LogicalExpressionVisitor; // 导入Pig逻辑表达式访问者基类
import org.apache.pig.newplan.logical.expression.MapLookupExpression; // 导入Pig映射查找表达式类
import org.apache.pig.newplan.logical.expression.ModExpression; // 导入Pig取模表达式类
import org.apache.pig.newplan.logical.expression.MultiplyExpression; // 导入Pig乘法表达式类
import org.apache.pig.newplan.logical.expression.NegativeExpression; // 导入Pig负数表达式类
import org.apache.pig.newplan.logical.expression.NotEqualExpression; // 导入Pig不等于表达式类
import org.apache.pig.newplan.logical.expression.NotExpression; // 导入Pig逻辑非表达式类
import org.apache.pig.newplan.logical.expression.OrExpression; // 导入Pig逻辑或表达式类
import org.apache.pig.newplan.logical.expression.ProjectExpression; // 导入Pig投影表达式类(字段引用)
import org.apache.pig.newplan.logical.expression.RegexExpression; // 导入Pig正则表达式匹配类
import org.apache.pig.newplan.logical.expression.ScalarExpression; // 导入Pig标量子查询表达式类
import org.apache.pig.newplan.logical.expression.SubtractExpression; // 导入Pig减法表达式类
import org.apache.pig.newplan.logical.expression.UserFuncExpression; // 导入Pig用户定义函数表达式类
import org.apache.pig.newplan.logical.relational.LOInnerLoad; // 导入Pig内部加载操作符类
import org.apache.pig.newplan.logical.relational.LogicalRelationalOperator; // 导入Pig逻辑关系操作符基类

import com.google.common.collect.ImmutableList; // 导入Google不可变列表类,提供不可变的列表实现
import com.google.common.collect.Lists; // 导入Google列表工具类,提供列表创建的便捷方法

import java.math.BigDecimal; // 导入Java大数类,用于精确的十进制运算
import java.util.ArrayDeque; // 导入Java数组双端队列类,提供高效的队列操作
import java.util.ArrayList; // 导入Java数组列表类,提供动态数组功能
import java.util.Deque; // 导入Java双端队列接口,定义双端队列的标准操作
import java.util.List; // 导入Java列表接口,定义列表的标准操作

import static com.google.common.base.Preconditions.checkArgument; // 静态导入参数检查方法

import static java.util.Objects.requireNonNull; // 静态导入非空检查方法

/**
 * Visits pig expression plans and converts them into corresponding RexNodes. // 类作用:访问Pig表达式计划并将其转换为对应的Calcite RexNode表达式节点
 * // 该类是Piglet适配器的核心组件之一,负责将Pig的各种表达式(如算术运算、比较运算、逻辑运算等)
 * // 转换为Calcite的RexNode表示,从而实现Pig脚本到关系代数的转换
 */
class PigRelExVisitor extends LogicalExpressionVisitor { // 类定义:继承自Pig的LogicalExpressionVisitor,实现访问者模式
  /** Stack used during post-order walking process when processing a Pig
   * expression plan. */ // 成员变量说明:在处理Pig表达式计划的后序遍历过程中使用的栈
  private final Deque<RexNode> stack = new ArrayDeque<>(); // 成员变量:后序遍历栈,用于临时存储转换过程中的RexNode,采用后进先出策略处理表达式树

  /** The relational algebra builder customized for Pig. */ // 成员变量说明:为Pig定制的关系代数构建器
  private final PigRelBuilder builder; // 成员变量:Pig关系代数构建器,用于构建Calcite的关系代数表达式

  // inputCount and inputOrdinal are used to select which relation in the builder
  // stack to build the projection // 注释:inputCount和inputOrdinal用于选择构建器堆栈中的哪个关系来构建投影

  /** Number of inputs. */ // 成员变量说明:输入关系的数量
  private final int inputCount; // 成员变量:输入关系的数量,表示当前表达式操作依赖的输入关系个数

  /** Input ordinal. */ // 成员变量说明:输入序号
  private final int inputOrdinal; // 成员变量:输入序号,表示当前表达式操作使用的是第几个输入关系(从0开始)

  /**
   * Creates a PigRelExVisitor. // 方法作用:创建PigRelExVisitor实例
   *
   * @param expressionPlan Pig expression plan // 参数:Pig表达式计划对象,包含要转换的Pig表达式树
   * @param walker The walker over Pig expression plan. // 参数:Pig表达式计划的遍历器,用于遍历表达式树
   * @param builder Relational algebra builder // 参数:关系代数构建器,用于构建Calcite关系代数表达式
   * @param inputCount Number of inputs // 参数:输入关系的数量
   * @param inputOrdinal Input ordinal // 参数:输入序号
   * @throws FrontendException Exception during processing Pig operators // 抛出异常:处理Pig操作符时的异常
   */
  private PigRelExVisitor(OperatorPlan expressionPlan, PlanWalker walker, // 构造方法:私有构造方法,初始化PigRelExVisitor实例
      PigRelBuilder builder, int inputCount, int inputOrdinal) // 参数列表:表达式计划、遍历器、构建器、输入数量、输入序号
      throws FrontendException { // 异常声明:可能抛出Pig前端异常
    super(expressionPlan, walker); // 调用父类构造方法,初始化LogicalExpressionVisitor基类
    this.builder = builder; // 初始化成员变量builder,保存关系代数构建器引用
    this.inputCount = inputCount; // 初始化成员变量inputCount,保存输入关系数量
    this.inputOrdinal = inputOrdinal; // 初始化成员变量inputOrdinal,保存输入序号
  }

  /**
   * Translates the given pig expression plan into a list of relational algebra
   * expressions. // 方法作用:将给定的Pig表达式计划转换为关系代数表达式列表
   *
   * @return Relational algebra expressions // 返回值:关系代数表达式列表(RexNode列表)
   * @throws FrontendException Exception during processing Pig operators // 抛出异常:处理Pig操作符时的异常
   */
  private List<RexNode> translate() throws FrontendException { // 方法定义:私有方法,执行表达式转换
    currentWalker.walk(this); // 调用遍历器的walk方法,开始遍历表达式计划,触发各个visit方法的调用
    return new ArrayList<>(stack); // 返回栈中所有RexNode的列表副本,转换完成后栈中包含所有转换结果
  }

  /**
   * Translates a Pig expression plans into relational algebra expressions. // 方法作用:静态工厂方法,将Pig表达式计划转换为关系代数表达式
   *
   * @param builder Relational algebra builder // 参数:关系代数构建器
   * @param pigEx Pig expression plan // 参数:Pig表达式计划
   * @param inputCount Number of inputs // 参数:输入关系的数量
   * @param inputOrdinal Input ordinal // 参数:输入序号
   * @return Relational algebra expressions // 返回值:关系代数表达式(单个RexNode)
   * @throws FrontendException Exception during processing Pig operators // 抛出异常:处理Pig操作符时的异常
   */
  static RexNode translatePigEx(PigRelBuilder builder, LogicalExpressionPlan pigEx, // 静态方法:重载方法1,支持指定输入数量和序号
      int inputCount, int inputOrdinal) throws FrontendException { // 参数列表:构建器、表达式计划、输入数量、输入序号
    final PigRelExWalker walker = new PigRelExWalker(pigEx); // 创建PigRelExWalker遍历器实例,用于遍历Pig表达式计划
    final PigRelExVisitor exVisitor = // 创建PigRelExVisitor访问者实例
        new PigRelExVisitor(pigEx, walker, builder, inputCount, inputOrdinal); // 传入表达式计划、遍历器、构建器等参数
    final List<RexNode> result = exVisitor.translate(); // 调用translate方法执行转换,得到结果列表
    assert result.size() == 1; // 断言:确保转换结果只包含一个RexNode(单个表达式)
    return result.get(0); // 返回列表中的第一个(也是唯一一个)RexNode
  }

  /**
   * Translates a Pig expression plans into relational algebra expressions. // 方法作用:静态工厂方法简化版,使用默认输入参数
   *
   * @param builder Relational algebra builder // 参数:关系代数构建器
   * @param pigEx Pig expression plan // 参数:Pig表达式计划
   * @return Relational algebra expressions // 返回值:关系代数表达式(单个RexNode)
   * @throws FrontendException Exception during processing Pig operators // 抛出异常:处理Pig操作符时的异常
   */
  static RexNode translatePigEx(PigRelBuilder builder, LogicalExpressionPlan pigEx) // 静态方法:重载方法2,使用默认参数(inputCount=1, inputOrdinal=0)
      throws FrontendException { // 异常声明
    return translatePigEx(builder, pigEx, 1, 0); // 调用重载方法1,使用默认的输入数量1和输入序号0
  }

  /**
   * Builds operands for an operator from expressions on the top of the visitor stack. // 方法作用:从访问者栈顶构建操作符的操作数列表
   *
   * @param numOps number of operands // 参数:操作数的数量
   * @return List of operand expressions // 返回值:操作数表达式列表(不可变列表)
   */
  private ImmutableList<RexNode> buildOperands(int numOps) { // 方法定义:私有方法,从栈中弹出指定数量的操作数
    List<RexNode> opList = new ArrayList<>(); // 创建操作数列表
    for (int i = 0; i < numOps; i++) { // 循环:弹出numOps个操作数
      opList.add(0, stack.pop()); // 从栈顶弹出RexNode并插入到列表头部,保持操作数顺序
    } // 循环结束
    return ImmutableList.copyOf(opList); // 返回操作数列表的不可变副本
  }

  /**
   * Builds operands for a binary operator. // 方法作用:构建二元操作符的操作数列表
   *
   * @return List of two operand expressions // 返回值:包含两个操作数的表达式列表
   */
  private ImmutableList<RexNode> buildBinaryOperands() { // 方法定义:私有方法,专门处理二元操作符
    return buildOperands(2); // 调用buildOperands方法,传入操作数数量2
  }

  @Override public void visit(ConstantExpression op) throws FrontendException { // 方法重写:访问常量表达式节点
    RelDataType constType = PigTypes.convertSchemaField(op.getFieldSchema(), false); // 将Pig字段模式转换为Calcite关系数据类型
    stack.push(builder.literal(op.getValue(), constType)); // 使用构建器创建字面量RexNode并压入栈中
  }

  @Override public void visit(ProjectExpression op) throws FrontendException { // 方法重写:访问投影表达式节点(字段引用)
    String fullAlias = op.getFieldSchema().alias; // 获取字段的完整别名(可能包含表名,如"tableName::fieldName")
    if (fullAlias != null) { // 条件:如果提供了字段别名
      RexNode inputRef; // 声明输入引用变量
      try { // 尝试:直接使用完整别名查找字段
        // First try the exact name match for the alias // 首先尝试精确匹配别名
        inputRef = builder.field(inputCount, inputOrdinal, fullAlias); // 使用构建器按别名查找字段引用
      } catch (IllegalArgumentException e) { // 捕获异常:精确匹配失败
        // If not found, look for the field name match only. // 如果未找到,则仅匹配字段名
        // Note that the full alias may have the format of 'tableName::fieldName' // 注意:完整别名格式可能是"tableName::fieldName"
        final List<String> fieldNames = // 获取输入关系的所有字段名列表
            builder.peek(inputCount, inputOrdinal).getRowType().getFieldNames(); // 从构建器堆栈中获取指定输入的字段名
        int index = -1; // 初始化字段索引为-1表示未找到
        for (int i = 0; i < fieldNames.size(); i++) { // 循环:遍历所有字段名
          if (fullAlias.endsWith(fieldNames.get(i))) { // 条件:如果完整别名以当前字段名结尾
            index = i; // 记录匹配的字段索引
            break; // 跳出循环
          } // 条件结束
        } // 循环结束
        if (index < 0) { // 条件:仍未找到匹配字段
          String shortAlias = fullAlias; // 初始化短别名为完整别名
          if (fullAlias.contains("::")) { // 条件:如果别名包含"::"分隔符
            String[] tokens = fullAlias.split("::"); // 按"::"分割别名
            shortAlias = tokens[tokens.length - 1]; // 取最后一个部分作为短别名(纯字段名)
          } // 条件结束
          for (int i = 0; i < fieldNames.size(); i++) { // 循环:遍历所有字段名
            if (fieldNames.get(i).equals(shortAlias)) { // 条件:如果字段名等于短别名
              index = i; // 记录匹配的字段索引
              break; // 跳出循环
            } // 条件结束
          } // 循环结束
          if (index < 0) { // 条件:仍然未找到匹配字段
            throw new IllegalArgumentException( // 抛出异常:字段未找到
                "field [" + fullAlias + "] not found; input fields are: " + fieldNames); // 异常信息包含完整别名和所有可用字段名
          } // 条件结束
        } // 条件结束
        inputRef = builder.field(inputCount, inputOrdinal, index); // 使用字段索引创建字段引用
      } // 异常处理结束
      stack.push(inputRef); // 将字段引用压入栈中
    } else { // 条件:未提供字段别名
      // Alias not provided, get data from input of LOGenerate // 未提供别名,从LOGenerate的输入获取数据
      assert op.getInputNum() >= 0; // 断言:确保输入编号非负
      final Operator pigRelOp = op.getAttachedRelationalOp(); // 获取附加的关系操作符
      final LogicalRelationalOperator childOp = (LogicalRelationalOperator) // 获取子操作符
          pigRelOp.getPlan().getPredecessors(pigRelOp).get(op.getInputNum()); // 从计划中获取指定编号的前驱操作符
      if (builder.checkMap(childOp)) { // 条件:检查子操作符是否为已处理的映射(嵌套foreach或flatten)
        // Inner plan that has been processed before (nested foreach or flatten) // 已处理的内部计划(嵌套foreach或flatten)
        builder.push(builder.getRel(childOp)); // 将子操作符对应的关系压入构建器堆栈
        final List<RexNode> fields = builder.getFields(inputCount, inputOrdinal, op.getColNum()); // 获取指定列的所有字段

        for (int i = fields.size() - 1; i >= 0; i--) { // 循环:逆序遍历字段列表
          stack.push(fields.get(i)); // 将每个字段压入栈中(逆序以保证正确顺序)
        } // 循环结束

        builder.build(); // 构建并弹出堆栈顶的关系
      } else { // 条件:简单的内部加载
        // Simple inner load // 简单的内部加载
        assert childOp instanceof LOInnerLoad; // 断言:确保子操作符是LOInnerLoad类型
        visit(((LOInnerLoad) childOp).getProjection()); // 递归访问LOInnerLoad的投影表达式
      } // 条件结束
    } // 条件结束
  }

  @Override public void visit(NegativeExpression op) { // 方法重写:访问负数表达式节点
    final RexNode operand = stack.pop(); // 从栈顶弹出操作数
    if (operand instanceof RexLiteral) { // 条件:如果操作数是字面量
      final Comparable value = ((RexLiteral) operand).getValue(); // 获取字面量的值
      if (value instanceof BigDecimal) { // 条件:如果值是BigDecimal类型
        stack.push(builder.literal(((BigDecimal) value).negate())); // 创建取负后的BigDecimal字面量并压入栈
      } else { // 条件:否则
        assert value instanceof Double; // 断言:值必须是Double类型
        stack.push(builder.literal(- (Double) value)); // 创建取负后的Double字面量并压入栈
      } // 条件结束
    } else { // 条件:操作数不是字面量
      stack.push(builder.call(SqlStdOperatorTable.UNARY_MINUS, operand)); // 创建一元减法调用表达式并压入栈
    } // 条件结束
  }

  @Override public void visit(EqualExpression op) { // 方法重写:访问等于表达式节点
    stack.push(builder.call(SqlStdOperatorTable.EQUALS, buildBinaryOperands())); // 构建等于操作符调用并压入栈
  }

  @Override public void visit(NotEqualExpression op) { // 方法重写:访问不等于表达式节点
    stack.push(builder.call(SqlStdOperatorTable.NOT_EQUALS, buildBinaryOperands())); // 构建不等于操作符调用并压入栈
  }

  @Override public void visit(LessThanExpression op) { // 方法重写:访问小于表达式节点
    stack.push(builder.call(SqlStdOperatorTable.LESS_THAN, buildBinaryOperands())); // 构建小于操作符调用并压入栈
  }

  @Override public void visit(LessThanEqualExpression op) { // 方法重写:访问小于等于表达式节点
    stack.push(builder.call(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, buildBinaryOperands())); // 构建小于等于操作符调用并压入栈
  }

  @Override public void visit(GreaterThanExpression op) { // 方法重写:访问大于表达式节点
    stack.push(builder.call(SqlStdOperatorTable.GREATER_THAN, buildBinaryOperands())); // 构建大于操作符调用并压入栈
  }

  @Override public void visit(GreaterThanEqualExpression op) { // 方法重写:访问大于等于表达式节点
    stack.push(builder.call(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, buildBinaryOperands())); // 构建大于等于操作符调用并压入栈
  }

  @Override public void visit(RegexExpression op) { // 方法重写:访问正则表达式匹配节点
    RexNode operand1 = replacePatternIfPossible(stack.pop()); // 弹出第一个操作数(模式字符串)并尝试转换模式
    RexNode operand2 = replacePatternIfPossible(stack.pop()); // 弹出第二个操作数(待匹配字符串)并尝试转换模式
    stack.push(builder.call(SqlStdOperatorTable.LIKE, ImmutableList.of(operand2, operand1))); // 构建LIKE操作符调用(注意操作数顺序反转:operand2是待匹配字符串,operand1是模式)
  }

  /**
   * Replaces Pig regular expressions with SQL regular expressions in a string. // 方法作用:将字符串中的Pig正则表达式替换为SQL正则表达式
   *
   * @param rexNode The string literal // 参数:字符串字面量(RexNode)
   * @return New string literal with Pig regular expressions replaced by SQL regular expressions // 返回值:替换后的新字符串字面量
   */
  private static RexNode replacePatternIfPossible(RexNode rexNode) { // 方法定义:私有静态方法,替换正则表达式模式
    // Until // 注释:直到以下问题解决前
    //   [CALCITE-3194] Convert Pig string patterns into SQL string patterns // CALCITE-3194:将Pig字符串模式转换为SQL字符串模式
    // is fixed, return the pattern unchanged. // 问题修复前,返回未修改的模式
    return rexNode; // 直接返回原始RexNode(暂未实现模式转换)
  }

  @Override public void visit(IsNullExpression op) { // 方法重写:访问判断为空表达式节点
    stack.push(builder.call(SqlStdOperatorTable.IS_NULL, stack.pop())); // 构建IS_NULL操作符调用并压入栈
  }

  @Override public void visit(NotExpression op) { // 方法重写:访问逻辑非表达式节点
    stack.push(builder.call(SqlStdOperatorTable.NOT, stack.pop())); // 构建NOT操作符调用并压入栈
  }

  @Override public void visit(AndExpression op) { // 方法重写:访问逻辑与表达式节点
    stack.push(builder.call(SqlStdOperatorTable.AND, buildBinaryOperands())); // 构建AND操作符调用并压入栈
  }

  @Override public void visit(OrExpression op) { // 方法重写:访问逻辑或表达式节点
    stack.push(builder.call(SqlStdOperatorTable.OR, buildBinaryOperands())); // 构建OR操作符调用并压入栈
  }

  @Override public void visit(AddExpression op) { // 方法重写:访问加法表达式节点
    stack.push(builder.call(SqlStdOperatorTable.PLUS, buildBinaryOperands())); // 构建加法操作符调用并压入栈
  }

  @Override public void visit(SubtractExpression op) { // 方法重写:访问减法表达式节点
    stack.push(builder.call(SqlStdOperatorTable.MINUS, buildBinaryOperands())); // 构建减法操作符调用并压入栈
  }

  @Override public void visit(MultiplyExpression op) { // 方法重写:访问乘法表达式节点
    stack.push(builder.call(SqlStdOperatorTable.MULTIPLY, buildBinaryOperands())); // 构建乘法操作符调用并压入栈
  }

  @Override public void visit(ModExpression op) { // 方法重写:访问取模表达式节点
    stack.push(builder.call(SqlStdOperatorTable.MOD, buildBinaryOperands())); // 构建取模操作符调用并压入栈
  }

  @Override public void visit(DivideExpression op) { // 方法重写:访问除法表达式节点
    stack.push(builder.call(SqlStdOperatorTable.DIVIDE, buildBinaryOperands())); // 构建除法操作符调用并压入栈
  }

  @Override public void visit(BinCondExpression op) { // 方法重写:访问二元条件表达式节点(三元运算符:condition?trueExpr:falseExpr)
    stack.push(builder.call(SqlStdOperatorTable.CASE, buildOperands(3))); // 构建CASE操作符调用(需要3个操作数:条件、真值表达式、假值表达式)
  }

  @Override public void visit(UserFuncExpression op) throws FrontendException { // 方法重写:访问用户定义函数表达式节点
    if (op.getFuncSpec().getClassName().equals("org.apache.pig.impl.builtin.IdentityColumn")) { // 条件:如果是Pig的IdentityColumn虚函数
      // Skip this Pig dummy function // 跳过这个Pig虚函数
      return; // 直接返回,不做任何处理
    } // 条件结束
    final int numAgrs = optSize(op.getPlan().getSuccessors(op)) // 计算函数参数数量:后继操作符数量
        + optSize(op.getPlan().getSoftLinkSuccessors(op)); // 加上软链接后继操作符数量

    final RelDataType returnType = PigTypes.convertSchemaField(op.getFieldSchema()); // 获取函数返回值类型
    stack.push( // 将转换后的函数调用压入栈
        PigRelUdfConverter.convertPigFunction( // 调用PigRelUdfConverter转换Pig函数
            builder, op.getFuncSpec(), buildOperands(numAgrs), returnType)); // 传入构建器、函数规范、操作数列表、返回类型

    String className = op.getFuncSpec().getClassName(); // 获取函数类名
    SqlOperator sqlOp = ((RexCall) stack.peek()).getOperator(); // 获取栈顶RexCall的操作符
    if (sqlOp instanceof SqlUserDefinedFunction) { // 条件:如果是用户定义函数
      ScalarFunctionImpl sqlFunc = // 获取标量函数实现
          (ScalarFunctionImpl) ((SqlUserDefinedFunction) sqlOp).getFunction(); // 从SQL操作符中提取函数实现
      // "Exec" method can be implemented from the parent class. // "Exec"方法可以从父类实现
      className = sqlFunc.method.getDeclaringClass().getName(); // 获取方法声明类的实际类名(可能不同于原始类名)
    } // 条件结束
    builder.registerPigUDF(className, op.getFuncSpec()); // 在构建器中注册Pig UDF,记录类名和函数规范的映射关系
  }

  private static int optSize(List<Operator> list) { // 方法定义:私有静态方法,安全获取列表大小
    return list != null ? list.size() : 0; // 如果列表非空返回大小,否则返回0
  }

  @Override public void visit(DereferenceExpression op) { // 方法重写:访问解引用表达式节点(访问结构体或集合的字段)
    final RexNode parentField = stack.pop(); // 从栈顶弹出父字段(结构体或集合)
    List<Integer> cols = op.getBagColumns(); // 获取要访问的字段列索引列表
    requireNonNull(cols, "cols"); // 非空检查:确保cols不为null
    checkArgument(!cols.isEmpty()); // 参数检查:确保cols不为空

    if (parentField.getType() instanceof MultisetSqlType) { // 条件:如果父字段是多重集类型(Multiset)
      // Calcite does not support projection on Multiset type. We build // Calcite不支持对Multiset类型的投影,我们构建
      // our own multiset projection in @PigRelSqlUDFs and use it here // 自己的多重集投影(在PigRelSqlUdfs中定义)并在这里使用
      final RexNode[] rexCols = new RexNode[cols.size() + 1]; // 创建RexNode数组,大小为字段数+1
      // First parent field // 第一个元素是父字段
      rexCols[0] = parentField; // 将父字段放入数组第一个位置
      // The sub-fields to be projected from parent field // 要从父字段投影的子字段
      for (int i = 0; i < cols.size(); i++) { // 循环:遍历所有字段索引
        rexCols[i + 1] = builder.literal(cols.get(i)); // 将字段索引转换为字面量并放入数组
      } // 循环结束
      stack.push(builder.call(PigRelSqlUdfs.MULTISET_PROJECTION, rexCols)); // 构建多重集投影函数调用并压入栈
    } else { // 条件:父字段不是多重集类型
      if (cols.size() == 1) { // 条件:如果只访问单个字段
        // Single field projection // 单字段投影
        stack.push(builder.dot(parentField, cols.get(0))); // 使用点操作符访问单个字段并压入栈
      } else { // 条件:访问多个字段
        // Multiple field projection, build a sub struct from the parent struct // 多字段投影,从父结构体构建子结构体
        List<RexNode> relFields = new ArrayList<>(); // 创建字段列表
        for (Object col : cols) { // 循环:遍历所有字段索引
          relFields.add(builder.dot(parentField, col)); // 使用点操作符访问每个字段并添加到列表
        } // 循环结束

        final RelDataType newRelType = // 创建新的结构体类型
            RexUtil.createStructType(PigTypes.TYPE_FACTORY, // 使用Pig类型工厂
                relFields); // 根据字段列表创建结构体类型
        stack.push( // 将结构体构造表达式压入栈
            builder.getRexBuilder() // 获取Rex构建器
                .makeCall(newRelType, SqlStdOperatorTable.ROW, relFields)); // 创建ROW操作符调用,构造新的结构体
      } // 条件结束
    } // 条件结束
  }

  @Override public void visit(CastExpression op) throws FrontendException { // 方法重写:访问类型转换表达式节点
    final RelDataType relType = PigTypes.convertSchemaField(op.getFieldSchema()); // 获取目标类型(将Pig字段模式转换为Calcite类型)
    final RexNode castOperand = stack.pop(); // 从栈顶弹出要转换的操作数
    if (castOperand instanceof RexLiteral // 条件:如果操作数是字面量
        && ((RexLiteral) castOperand).getValue() == null) { // 且值为null
      if (!relType.isStruct() && relType.getComponentType() == null) { // 条件:如果目标类型不是结构体且没有组件类型
        stack.push(builder.getRexBuilder().makeNullLiteral(relType)); // 创建指定类型的null字面量并压入栈
      } else { // 条件:目标类型是结构体或集合类型
        stack.push(castOperand); // 直接压入原始操作数(保持null值不变)
      } // 条件结束
    } else { // 条件:操作数不是null字面量
      stack.push(builder.getRexBuilder().makeCast(relType, castOperand)); // 创建类型转换表达式并压入栈
    } // 条件结束
  }

  @Override public void visit(MapLookupExpression op) { // 方法重写:访问映射查找表达式节点(根据键查找Map中的值)
    final RexNode relKey = builder.literal(op.getLookupKey()); // 创建查找键的字面量RexNode
    final RexNode relMap = stack.pop(); // 从栈顶弹出Map表达式
    stack.push(builder.call(SqlStdOperatorTable.ITEM, relMap, relKey)); // 构建ITEM操作符调用(使用键访问Map元素)并压入栈
  }

  @Override public void visit(ScalarExpression op) { // 方法重写:访问标量子查询表达式节点
    // First operand is the path to the materialized view // 第一个操作数是物化视图的路径
    RexNode operand1 = stack.pop(); // 弹出第一个操作数
    assert operand1 instanceof RexLiteral // 断言:操作数必须是字面量
               && ((RexLiteral) operand1).getValue() instanceof NlsString; // 且值必须是NlsString类型(国际化字符串)

    // Second operand is the projection index // 第二个操作数是投影索引
    RexNode operand2 = stack.pop(); // 弹出第二个操作数
    assert operand2 instanceof RexLiteral // 断言:操作数必须是字面量
               && ((RexLiteral) operand2).getValue() instanceof BigDecimal; // 且值必须是BigDecimal类型
    final int index = ((BigDecimal) ((RexLiteral) operand2).getValue()).intValue(); // 将BigDecimal转换为int类型的索引

    RelNode referencedRel = // 获取引用的关系
        builder.getRel( // 从构建器中获取关系
            ((LogicalRelationalOperator) op.getImplicitReferencedOperator()) // 获取隐式引用的操作符
                .getAlias()); // 获取操作符的别名
    builder.push(referencedRel); // 将引用的关系压入构建器堆栈
    List<RexNode> projectCol = Lists.newArrayList(builder.field(index)); // 创建投影列列表,只包含指定索引的字段
    builder.project(projectCol); // 执行投影操作

    stack.push(RexSubQuery.scalar(builder.build())); // 构建标量子查询表达式并压入栈
  }
} // 类定义结束
