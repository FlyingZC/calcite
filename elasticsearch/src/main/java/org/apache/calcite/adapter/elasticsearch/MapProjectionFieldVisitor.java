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
// 导入Apache Calcite的Rex表达式相关类，用于处理关系表达式
import org.apache.calcite.rex.RexCall; // RexCall表示一个函数调用表达式，例如函数调用或操作符应用
import org.apache.calcite.rex.RexLiteral; // RexLiteral表示一个字面量常量表达式，如字符串、数字等
import org.apache.calcite.rex.RexVisitorImpl; // RexVisitorImpl是RexVisitor接口的默认实现类，用于遍历Rex表达式树
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // SqlStdOperatorTable包含所有标准SQL操作符的定义

/**
 * Visitor that extracts the actual field name from an item expression.
 */
// MapProjectionFieldVisitor是一个访问者模式的实现类，专门用于从ITEM类型的Rex表达式中提取实际的字段名
// 它继承自RexVisitorImpl<String>，表示访问Rex表达式树并返回String类型的结果
// 这个类主要用于Elasticsearch适配器中处理Map类型的投影操作，例如从Map中提取特定字段的值
class MapProjectionFieldVisitor extends RexVisitorImpl<String> { // 继承RexVisitorImpl，泛型参数String表示访问结果类型

  // INSTANCE是一个静态常量，是MapProjectionFieldVisitor类的单例实例
  // 使用单例模式是因为这个访问者是无状态的，不需要维护任何实例变量，所以可以全局共享一个实例
  // 这样可以避免频繁创建对象，提高性能
  static final MapProjectionFieldVisitor INSTANCE = new MapProjectionFieldVisitor(); // 直接创建并初始化单例实例

  // 私有构造方法，防止外部创建新实例，保证单例模式的完整性
  // 构造方法中调用super(true)，传入true表示这个访问者会深入遍历表达式树的所有节点
  // 如果传入false，则只访问根节点，不会递归访问子节点
  private MapProjectionFieldVisitor() { // 私有构造方法，实现单例模式
    super(true); // 调用父类RexVisitorImpl的构造方法，传入true表示深度遍历表达式树
  } // 构造方法结束

  // 重写visitCall方法，用于访问和处理RexCall类型的表达式节点
  // RexCall表示一个函数调用或操作符应用，例如ITEM操作符、加减乘除等
  // 当遍历表达式树遇到RexCall节点时，会自动调用这个方法
  // 参数call是当前访问的RexCall表达式对象
  // 返回值是提取出的字段名字符串，如果不是ITEM操作符则返回父类的默认处理结果
  @Override public String visitCall(RexCall call) { // 重写父类的visitCall方法
    // 判断当前RexCall的操作符是否是ITEM操作符
    // SqlStdOperatorTable.ITEM是Calcite中用于表示字段访问或Map取值的操作符
    // 例如在SQL中"map['field']"或"table.field"会被转换为ITEM操作符
    // 只有ITEM操作符才需要提取字段名，其他操作符调用父类的默认处理
    if (call.op == SqlStdOperatorTable.ITEM) { // 检查操作符类型是否为ITEM
      // 获取ITEM操作符的第二个操作数，它通常包含字段名
      // call.getOperands()返回操作数列表，对于ITEM操作符：
      // - 第一个操作数(索引0)是被访问的对象或Map
      // - 第二个操作数(索引1)是字段名或键名，通常是RexLiteral类型
      // .get(1)获取第二个操作数，即字段名表达式
      // ((RexLiteral)...)将其强制转换为RexLiteral类型，因为字段名通常是字面量
      // .getValueAs(String.class)从字面量中提取String类型的值，即实际的字段名字符串
      return ((RexLiteral) call.getOperands().get(1)).getValueAs(String.class); // 提取并返回字段名字符串
    } // if语句结束
    // 如果不是ITEM操作符，调用父类的visitCall方法进行默认处理
    // 父类会根据操作符类型和深度遍历标志决定是否继续访问子节点
    // 这样可以保持对其他类型操作符的正常处理逻辑
    return super.visitCall(call); // 调用父类方法处理非ITEM操作符
  } // visitCall方法结束
} // MapProjectionFieldVisitor类结束
