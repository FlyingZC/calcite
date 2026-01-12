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
package org.apache.calcite.linq4j.tree; // 包声明：该类属于org.apache.calcite.linq4j.tree包，是LINQ4J树形表达式结构的一部分

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解：用于标记可能为null的参数，提供空值检查支持

/**
 * Represents a constant null of unknown type // 类作用：表示一个类型未知的常量null值
 * Java allows type inference for such nulls, thus "null" cannot always be // Java允许对这种null进行类型推断，因此"null"不能总是
 * replaced to (Object)null and vise versa. // 被替换为(Object)null，反之亦然
 *
 * <p>{@code ConstantExpression(null, Object.class)} is not equal to // 说明：ConstantExpression(null, Object.class)不等于
 * {@code ConstantUntypedNull} However, optimizers might treat all the nulls // ConstantUntypedNull，但优化器可能会将所有null视为相等
 * equal (e.g. in case of comparison). // （例如在比较操作中）
 */
public class ConstantUntypedNull extends ConstantExpression { // 类定义：继承自ConstantExpression，表示未类型化的null常量表达式
  public static final ConstantExpression INSTANCE = new ConstantUntypedNull(); // 成员变量：静态常量实例，表示唯一的未类型化null实例，使用单例模式

  private ConstantUntypedNull() { // 构造方法：私有构造函数，确保只能通过INSTANCE访问，防止创建多个实例
    super(Object.class, null); // 调用父类ConstantExpression的构造函数，传入Object.class作为类型，null作为值
  }

  @Override void accept(ExpressionWriter writer, int lprec, int rprec) { // 方法：接受表达式访问器，用于生成代码字符串，writer是表达式写入器，lprec是左操作符优先级，rprec是右操作符优先级
    writer.append("null"); // 向表达式写入器追加"null"字符串，表示生成Java代码中的null关键字
  }

  @Override public boolean equals(@Nullable Object o) { // 方法：重写equals方法，用于比较对象是否相等，o是要比较的对象，可能为null
    return o == INSTANCE; // 只有当比较的对象就是INSTANCE本身时才返回true，确保单例对象的唯一性
  }

  @Override public int hashCode() { // 方法：重写hashCode方法，用于生成对象的哈希码
    return ConstantUntypedNull.class.hashCode(); // 返回ConstantUntypedNull类的哈希码，确保单例对象的哈希码一致性
  }
}
