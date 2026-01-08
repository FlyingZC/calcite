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
package org.apache.calcite.interpreter;  // 声明包名，该类位于org.apache.calcite.interpreter包中，这是Calcite解释器模块的核心包

import org.apache.calcite.rel.core.Uncollect;  // 导入Uncollect关系表达式类，这是Calcite中用于UNNEST操作的逻辑关系节点

import java.util.List;  // 导入Java集合框架中的List接口，用于处理列表类型的数据解包
import java.util.Locale;  // 导入Locale类，用于格式化错误消息时指定地区设置（这里使用ROOT确保格式一致性）
import java.util.Map;  // 导入Java集合框架中的Map接口，用于处理映射类型的数据解包

/**
 * Interpreter node that implements a  // 解释器节点，用于实现
 * {@link org.apache.calcite.rel.core.Uncollect}.  // Uncollect关系表达式（即SQL中的UNNEST操作）
 * 
 * 【类作用说明】
 * UncollectNode是Calcite解释器模式下的一个执行节点，用于实现SQL中的UNNEST操作（在Calcite中称为Uncollect）。
 * 
 * 【核心功能】
 * 1. 将数组或集合类型的数据展开为多行记录
 * 2. 支持两种数据类型的解包：List（列表）和Map（映射）
 * 3. 可选支持WITH ORDINALITY子句，为展开的每一行添加序号列
 * 
 * 【使用场景】
 * 当SQL查询中包含UNNEST操作时，例如：
 *   SELECT * FROM UNNEST(ARRAY[1,2,3])
 *   SELECT * FROM UNNEST(ARRAY[1,2,3]) WITH ORDINALITY
 *   SELECT * FROM UNNEST(MAP['a',1,'b',2])
 * 
 * 【执行流程】
 * 1. 从上游节点接收包含数组或Map的行数据
 * 2. 遍历每行中的每个值
 * 3. 根据值的类型（List或Map）进行相应的展开操作
 * 4. 如果启用了withOrdinality，则为每个展开的元素添加序号
 * 5. 将展开后的行发送到下游节点
 * 
 * 【继承关系】
 * 继承自AbstractSingleNode<Uncollect>，表示这是一个单输入节点的解释器实现
 * - AbstractSingleNode: 抽象单节点类，提供source（输入源）和sink（输出目标）成员变量
 * - Uncollect: 泛型参数，表示对应的关系表达式类型
 */
public class UncollectNode extends AbstractSingleNode<Uncollect> {  // UncollectNode类定义，继承抽象单节点类，泛型参数为Uncollect关系表达式

  /**
   * 【构造方法】
   * 
   * 【参数说明】
   * @param compiler 编译器对象，用于编译和执行查询计划
   * @param uncollect Uncollect关系表达式对象，包含该节点的逻辑信息（如是否启用WITH ORDINALITY）
   * 
   * 【功能说明】
   * 创建UncollectNode实例，初始化解释器节点
   * 1. 调用父类AbstractSingleNode的构造方法
   * 2. 保存compiler和uncollect引用
   * 3. 父类会初始化source（上游数据源）和sink（下游数据接收器）
   * 4. 通过rel成员变量（继承自父类）可以访问Uncollect关系表达式的属性
   * 
   * 【重要说明】
   * - rel成员变量（继承自父类）存储了Uncollect关系表达式
   * - rel.withOrdinality属性决定了是否为展开的行添加序号列
   * - source成员变量（继承自父类）表示上游数据源节点
   * - sink成员变量（继承自父类）表示下游数据接收节点
   */
  public UncollectNode(Compiler compiler, Uncollect uncollect) {  // 构造方法，接收编译器和Uncollect关系表达式对象
    super(compiler, uncollect);  // 调用父类构造方法，初始化基础成员变量（包括source、sink、rel等）
  }

  /**
   * 【核心执行方法】
   * 
   * 【方法作用】
   * 执行Uncollect操作，将数组或Map数据展开为多行记录
   * 
   * 【执行流程】
   * 1. 从上游source节点循环接收行数据
   * 2. 对每行中的每个值进行类型判断和处理
   * 3. 如果是List类型，展开为多行（每行一个元素）
   * 4. 如果是Map类型，展开为多行（每行包含键和值）
   * 5. 根据withOrdinality决定是否添加序号列
   * 6. 将展开后的行发送到下游sink节点
   * 
   * 【异常处理】
   * - InterruptedException: 当线程被中断时抛出，支持查询取消
   * - NullPointerException: 当遇到NULL值时抛出，因为UNNEST不允许NULL值
   * - UnsupportedOperationException: 当遇到不支持的类型时抛出
   * 
   * 【数据流示例】
   * 输入行: Row.of([1, 2, 3])
   * 输出行（不带序号）: Row.of(1), Row.of(2), Row.of(3)
   * 输出行（带序号）: Row.of(1, 1), Row.of(2, 2), Row.of(3, 3)
   * 
   * 输入行: Row.of({a:1, b:2})
   * 输出行（不带序号）: Row.of(a, 1), Row.of(b, 2)
   * 输出行（带序号）: Row.of(a, 1, 1), Row.of(b, 2, 2)
   */
  @Override public void run() throws InterruptedException {  // 覆盖父类的run方法，执行Uncollect操作，可能抛出中断异常
    Row row = null;  // 声明行变量，用于存储从上游接收的数据行，初始化为null
    while ((row = source.receive()) != null) {  // 循环从上游source接收行数据，直到返回null表示数据结束
      for (Object value : row.getValues()) {  // 遍历当前行中的所有值（一个行可能包含多个需要展开的数组或Map）
        if (value == null) {  // 检查当前值是否为null
          throw new NullPointerException("NULL value for unnest.");  // 如果为null，抛出空指针异常，因为UNNEST操作不支持NULL值
        }
        int i = 1;  // 初始化序号计数器，从1开始（用于WITH ORDINALITY子句）
        if (value instanceof List) {  // 判断当前值是否为List类型（数组或列表）
          List list = (List) value;  // 将Object类型强制转换为List类型
          for (Object o : list) {  // 遍历List中的每个元素
            if (rel.withOrdinality) {  // 检查是否启用了WITH ORDINALITY（通过rel关系表达式访问）
              sink.send(Row.of(o, i++));  // 如果启用序号，发送包含元素和当前序号的行，然后序号自增
            } else {  // 如果未启用序号
              sink.send(Row.of(o));  // 只发送包含元素的行（不包含序号）
            }
          }
        } else if (value instanceof Map) {  // 判断当前值是否为Map类型（键值对映射）
          Map map = (Map) value;  // 将Object类型强制转换为Map类型
          for (Object key : map.keySet()) {  // 遍历Map的所有键
            if (rel.withOrdinality) {  // 检查是否启用了WITH ORDINALITY
              sink.send(Row.of(key, map.get(key), i++));  // 发送包含键、值和序号的行，序号自增
            } else {  // 如果未启用序号
              sink.send(Row.of(key, map.get(key)));  // 只发送包含键和值的行（不包含序号）
            }
          }
        } else {  // 如果值既不是List也不是Map
          throw new UnsupportedOperationException(  // 抛出不支持操作异常
              String.format(Locale.ROOT,  // 使用ROOT地区格式化字符串，确保格式一致性
                  "Invalid type: %s for unnest.",  // 错误消息模板
                  value.getClass().getCanonicalName()));  // 获取值类型的规范名称并插入消息中
        }
      }
    }
  }
}
