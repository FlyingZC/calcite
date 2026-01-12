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
package org.apache.calcite.test.fuzzer; // 声明包名，该类属于org.apache.calcite.test.fuzzer包

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，用于构建RexNode表达式节点
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示函数调用表达式节点
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式节点的基类
import org.apache.calcite.rex.RexShuttle; // 导入RexShuttle类，用于遍历和转换RexNode表达式树
import org.apache.calcite.sql.SqlKind; // 导入SqlKind类，定义SQL操作的种类（如AND、OR、CASE等）
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName类，定义SQL类型的名称（如BOOLEAN、INTEGER等）

import java.util.ArrayList; // 导入ArrayList类，用于动态数组列表
import java.util.Random; // 导入Random类，用于生成随机数

/**
 * Reduces {@link RexNode} by removing random bits of it.
 * RexShrinker类：通过随机移除RexNode表达式的部分内容来简化表达式树，主要用于模糊测试（fuzzing）场景
 * 该类继承自RexShuttle，采用访问者模式遍历和修改表达式树
 * 主要作用是在保持表达式类型一致的前提下，通过随机缩减操作来简化复杂的表达式，帮助发现潜在的bug
 */
public class RexShrinker extends RexShuttle { // 定义RexShrinker类，继承自RexShuttle以实现表达式树的遍历和转换
  private final Random r; // 成员变量：随机数生成器，用于在缩减过程中做出随机选择
  private final RexBuilder rexBuilder; // 成员变量：RexNode构建器，用于创建新的表达式节点（如字面量、类型转换等）
  private boolean didWork; // 成员变量：标志位，记录是否已经执行了缩减操作，防止在单次遍历中多次修改

  RexShrinker(Random r, RexBuilder rexBuilder) { // 构造方法：初始化RexShrinker实例
    this.r = r; // 将传入的随机数生成器赋值给成员变量r，用于后续随机选择
    this.rexBuilder = rexBuilder; // 将传入的RexBuilder赋值给成员变量rexBuilder，用于创建新节点
  } // 构造方法结束

  @Override public RexNode visitCall(RexCall call) { // 重写visitCall方法：处理RexCall类型的表达式节点（即函数调用）
    RelDataType type = call.getType(); // 获取当前RexCall表达式的返回类型，确保缩减后的表达式类型一致
    if (didWork || r.nextInt(100) > 80) { // 如果已经执行过缩减操作，或者随机数大于80（80%概率），则跳过缩减
      return super.visitCall(call); // 调用父类方法继续遍历子节点，不进行任何修改
    } // 条件判断结束
    if (r.nextInt(100) < 10 && !call.operands.isEmpty()) { // 10%概率且操作数不为空时，尝试用子表达式替换当前表达式
      // Replace with its argument // 注释：用当前表达式的某个参数替换整个表达式
      RexNode node = call.operands.get(r.nextInt(call.operands.size())); // 随机选择一个操作数（子表达式）
      if (node.getType().equals(type)) { // 检查选中的子表达式类型是否与当前表达式类型一致
        return node; // 如果类型一致，直接返回该子表达式，实现缩减
      } // 类型检查结束
    } // 替换为参数的逻辑结束
    if (r.nextInt(100) < 10) { // 10%概率时，尝试用简单常量值替换当前表达式
      // Replace with simple value // 注释：用简单的字面量值替换当前表达式
      RexNode res = null; // 初始化结果变量为null
      switch (r.nextInt(type.isNullable() ? 3 : 2)) { // 根据类型是否可空生成0、1或2之间的随机数
      case 0: // 随机数为0的情况
        if (type.getSqlTypeName() == SqlTypeName.BOOLEAN) { // 如果类型是布尔型
          res = rexBuilder.makeLiteral(true); // 创建布尔值true的字面量
        } else if (type.getSqlTypeName() == SqlTypeName.INTEGER) { // 如果类型是整型
          res = rexBuilder.makeLiteral(1, type, true); // 创建整数1的字面量，指定类型和是否为精确值
        } // 类型判断结束
        break; // 跳出switch语句
      case 1: // 随机数为1的情况
        if (type.getSqlTypeName() == SqlTypeName.BOOLEAN) { // 如果类型是布尔型
          res = rexBuilder.makeLiteral(false); // 创建布尔值false的字面量
        } else if (type.getSqlTypeName() == SqlTypeName.INTEGER) { // 如果类型是整型
          res = rexBuilder.makeLiteral(0, type, true); // 创建整数0的字面量，指定类型和是否为精确值
        } // 类型判断结束
        break; // 跳出switch语句
      case 2: // 随机数为2的情况（仅当类型可空时）
        res = rexBuilder.makeNullLiteral(type); // 创建null值的字面量，类型与原表达式一致
      } // switch语句结束
      if (res != null) { // 如果成功创建了替换的字面量
        didWork = true; // 设置标志位为true，表示已经执行了缩减操作
        if (!res.getType().equals(type)) { // 如果创建的字面量类型与原类型不完全匹配
          return rexBuilder.makeCast(call.getParserPosition(), type, res); // 添加类型转换以确保类型一致
        } // 类型检查结束
        return res; // 返回创建的字面量作为替换结果
      } // null检查结束
    } // 替换为简单值的逻辑结束
    int operandSize = call.operands.size(); // 获取当前表达式的操作数数量
    SqlKind kind = call.getKind(); // 获取当前表达式的SQL操作种类（如AND、OR、COALESCE等）
    if ((kind == SqlKind.AND || kind == SqlKind.OR) && operandSize > 2 // 如果是AND或OR操作且操作数大于2，或者是COALESCE操作
        || kind == SqlKind.COALESCE) { // 处理可变参数的逻辑操作符
      // Trim random item // 注释：随机移除一个操作数来简化表达式
      if (operandSize == 1) { // 如果只剩一个操作数
        return call.operands.get(0); // 直接返回该操作数，无需包装
      } // 单操作数情况处理结束
      ArrayList<RexNode> newOperands = new ArrayList<>(call.operands); // 创建操作数的副本列表
      newOperands.remove(r.nextInt(operandSize)); // 随机移除一个操作数
      if (newOperands.size() == 1) { // 如果移除后只剩一个操作数
        return call.operands.get(0); // 直接返回该操作数（注意：这里应该返回newOperands.get(0)）
      } // 单操作数情况处理结束
      didWork = true; // 设置标志位为true，表示已经执行了缩减操作
      return call.clone(type, newOperands); // 克隆调用表达式，使用新的操作数列表
    } // 可变参数操作符处理结束
    if ((kind == SqlKind.MINUS_PREFIX || kind == SqlKind.PLUS_PREFIX) // 如果是负号或正号的一元操作符
        && r.nextInt(100) < 10) { // 10%概率时进行缩减
      didWork = true; // 设置标志位为true，表示已经执行了缩减操作
      return call.operands.get(0); // 直接返回操作数，移除一元操作符（即 -x 变为 x，+x 变为 x）
    } // 一元操作符处理结束
    if (kind == SqlKind.CASE) { // 如果是CASE WHEN表达式
      ArrayList<RexNode> newOperands = new ArrayList<>(call.operands); // 创建操作数的副本列表
      int indexToRemove = r.nextInt(newOperands.size() - 1) & 0xfffe; // 随机选择一个偶数索引（WHEN条件），确保对齐WHEN和THEN对
      // remove case branch // 注释：移除一个WHEN-THEN分支（需要同时移除WHEN和THEN两个元素）
      newOperands.remove(indexToRemove); // 移除WHEN条件
      newOperands.remove(indexToRemove); // 再次移除THEN结果（索引位置不变，因为已经移除了前面的元素）
      didWork = true; // 设置标志位为true，表示已经执行了缩减操作
      if (newOperands.size() == 1) { // 如果移除后只剩一个操作数（只剩下ELSE部分）
        return newOperands.get(0); // 直接返回ELSE部分
      } // 单操作数情况处理结束
      return call.clone(type, newOperands); // 克隆CASE表达式，使用新的操作数列表
    } // CASE表达式处理结束
    return super.visitCall(call); // 如果以上所有缩减条件都不满足，调用父类方法继续遍历子节点
  } // visitCall方法结束
} // RexShrinker类结束
