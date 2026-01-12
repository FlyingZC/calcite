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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.linq4j.tree; // 声明该类属于org.apache.calcite.linq4j.tree包

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker Framework的可空注解，用于标记可能为null的参数

import java.util.Objects; // 导入Java工具类Objects，用于生成哈希码和相等性比较

/**
 * Represents a label, which can be put in any {@link Expression} context. If it
 * is jumped to, it will get the value provided by the corresponding
 * {@link GotoStatement}. Otherwise, it receives the value in
 * {@link #defaultValue}. If the Type equals {@link Void}, no value should be
 * provided.
 */
// LabelStatement类：表示一个标签语句，用于在表达式上下文中标记一个位置
// 该标签可以被GotoStatement跳转到，类似于编程语言中的goto标签机制
// 如果标签被跳转到，它将接收对应GotoStatement提供的值；否则使用默认值
// 如果类型为Void，则不需要提供值（仅用于控制流跳转，不传递值）
public class LabelStatement extends Statement { // LabelStatement继承自Statement基类，表示一种语句节点
  public final Expression defaultValue; // 默认值表达式：当没有GotoStatement跳转到该标签时使用的值，final表示不可变

  public LabelStatement(Expression defaultValue, ExpressionType nodeType) { // 构造方法：创建一个标签语句实例
    super(nodeType, Void.TYPE); // 调用父类Statement的构造方法，传入节点类型和返回类型Void.TYPE（因为标签本身不返回值）
    this.defaultValue = defaultValue; // 将传入的默认值表达式赋值给成员变量defaultValue
  } // 构造方法结束

  @Override public LabelStatement accept(Shuttle shuttle) { // accept方法：接受Shuttle访问者模式的访问，用于遍历和转换表达式树
    return shuttle.visit(this); // 调用Shuttle的visit方法，传入当前LabelStatement实例，Shuttle会根据具体实现返回可能被修改后的LabelStatement
  } // accept方法结束，返回处理后的LabelStatement

  @Override public <R> R accept(Visitor<R> visitor) { // accept方法：接受Visitor访问者模式的访问，用于遍历表达式树并返回指定类型的结果
    return visitor.visit(this); // 调用Visitor的visit方法，传入当前LabelStatement实例，Visitor会根据具体实现返回类型为R的结果
  } // accept方法结束，返回类型为R的访问结果

  @Override public boolean equals(@Nullable Object o) { // equals方法：判断当前LabelStatement对象与另一个对象是否相等
    if (this == o) { // 检查是否是同一个对象引用（内存地址相同）
      return true; // 如果是同一个对象，直接返回true
    } // 结束同一个对象的判断
    if (o == null || getClass() != o.getClass()) { // 检查对象是否为null或类型是否不同
      return false; // 如果为null或类型不同，返回false
    } // 结束类型检查
    if (!super.equals(o)) { // 调用父类Statement的equals方法，检查父类部分是否相等
      return false; // 如果父类部分不相等，返回false
    } // 结束父类相等性检查

    LabelStatement that = (LabelStatement) o; // 将对象o强制转换为LabelStatement类型，赋值给that变量

    if (defaultValue != null ? !defaultValue.equals(that.defaultValue) : that // 检查defaultValue字段是否相等
        .defaultValue != null) { // 使用三元运算符：如果当前defaultValue不为null，检查是否与that.defaultValue相等；如果当前defaultValue为null，检查that.defaultValue是否也为null
      return false; // 如果defaultValue不相等，返回false
    } // 结束defaultValue相等性检查

    return true; // 所有检查都通过，返回true表示两个对象相等
  } // equals方法结束

  @Override public int hashCode() { // hashCode方法：生成对象的哈希码，用于哈希表等数据结构
    return Objects.hash(nodeType, type, defaultValue); // 使用Objects.hash方法，结合nodeType、type和defaultValue三个字段生成哈希码
  } // hashCode方法结束，返回计算得到的哈希码
} // LabelStatement类定义结束
