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
package org.apache.calcite.linq4j.tree; // 声明包路径，该类属于org.apache.calcite.linq4j.tree包

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可空参数

import java.util.List; // 导入List接口，用于存储索引表达式列表
import java.util.Objects; // 导入Objects工具类，用于equals和hashCode方法

import static com.google.common.base.Preconditions.checkArgument; // 静态导入checkArgument方法，用于参数校验

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于非空检查

/**
 * Represents indexing a property or array. // 表示对属性或数组进行索引操作的表达式类，用于表示数组访问、属性访问等索引操作
 */
public class IndexExpression extends Expression { // IndexExpression类继承自Expression基类，表示索引表达式
  public final Expression array; // 成员变量：array表示被索引的目标对象（数组、集合或属性），final修饰表示不可变
  public final List<Expression> indexExpressions; // 成员变量：indexExpressions表示索引表达式列表，用于多维数组或多级索引访问

  public IndexExpression(Expression array, List<Expression> indexExpressions) { // 构造方法：创建索引表达式实例
    super(ExpressionType.ArrayIndex, // 调用父类构造方法，设置表达式类型为ArrayIndex（数组索引类型）
        requireNonNull(Types.getComponentType(array.getType()), // 获取数组元素的类型，并确保不为null，用于确定索引表达式的返回类型
            () -> "component type for " + array)); // 如果获取组件类型失败，提供错误信息
    this.array = array; // 将传入的array参数赋值给成员变量array
    this.indexExpressions = // 将传入的indexExpressions参数赋值给成员变量indexExpressions
        requireNonNull(indexExpressions, "indexExpressions"); // 确保indexExpressions不为null，否则抛出NullPointerException
    checkArgument(!indexExpressions.isEmpty(), // 校验indexExpressions列表不能为空，否则抛出IllegalArgumentException
        "indexExpressions should not be empty"); // 校验失败时的错误信息
  }

  @Override public Expression accept(Shuttle shuttle) { // 重写accept方法：接受访问者（Shuttle）模式访问，用于遍历和转换表达式树
    shuttle = shuttle.preVisit(this); // 调用shuttle的preVisit方法进行前置访问，允许在访问子节点前进行预处理
    Expression array = this.array.accept(shuttle); // 递归访问array表达式，允许shuttle转换array子节点
    List<Expression> indexExpressions = // 递归访问所有索引表达式，允许shuttle转换indexExpressions子节点
        Expressions.acceptExpressions(this.indexExpressions, shuttle); // 使用工具方法批量访问索引表达式列表
    return shuttle.visit(this, array, indexExpressions); // 调用shuttle的visit方法，传入转换后的array和indexExpressions，返回可能被转换后的表达式
  }

  @Override public <R> R accept(Visitor<R> visitor) { // 重写accept方法：接受Visitor访问者模式访问，用于表达式树的遍历和类型化访问
    return visitor.visit(this); // 调用visitor的visit方法，将当前IndexExpression实例传递给visitor，返回类型化的结果R
  }

  @Override void accept(ExpressionWriter writer, int lprec, int rprec) { // 重写accept方法：使用ExpressionWriter将表达式写入输出流，lprec和rprec表示左右优先级
    array.accept(writer, lprec, nodeType.lprec); // 将array表达式写入writer，传入左优先级和当前节点类型的左优先级
    writer.list("[", ", ", "]", indexExpressions); // 将索引表达式列表写入writer，使用方括号包围，索引之间用逗号分隔
  }

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法：判断当前IndexExpression对象是否与另一个对象相等
    if (this == o) { // 如果引用相同，直接返回true
      return true;
    }
    if (o == null || getClass() != o.getClass()) { // 如果o为null或类型不同，返回false
      return false;
    }
    if (!super.equals(o)) { // 调用父类的equals方法，如果父类不相等则返回false
      return false;
    }

    IndexExpression that = (IndexExpression) o; // 将o转换为IndexExpression类型

    if (!array.equals(that.array)) { // 比较array成员变量是否相等
      return false;
    }
    if (!indexExpressions.equals(that.indexExpressions)) { // 比较indexExpressions成员变量是否相等
      return false;
    }

    return true; // 所有比较都通过，返回true表示两个对象相等
  }

  @Override public int hashCode() { // 重写hashCode方法：生成对象的哈希码，用于HashMap、HashSet等哈希集合中
    return Objects.hash(nodeType, type, array, indexExpressions); // 使用Objects.hash方法组合nodeType、type、array和indexExpressions的哈希值
  }
}
