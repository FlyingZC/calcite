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
package org.apache.calcite.interpreter; // 定义包名，属于解释器模块

import org.apache.calcite.rel.core.SetOp; // 导入SetOp基类，表示集合操作的关系表达式

import com.google.common.collect.HashMultiset; // 导入Guava库的HashMultiset，用于支持重复元素的集合操作

import java.util.Collection; // 导入集合接口
import java.util.HashSet; // 导入HashSet，用于去重的集合操作

/**
 * 解释器节点类，用于实现集合操作(SetOp)
 * 集合操作包括三种类型：
 * {@link org.apache.calcite.rel.core.Minus} - 差集操作(EXCEPT)，返回在左集合但不在右集合中的元素
 * {@link org.apache.calcite.rel.core.Union} - 并集操作，返回两个集合的所有元素
 * {@link org.apache.calcite.rel.core.Intersect} - 交集操作，返回两个集合共有的元素
 * 
 * 该类是Calcite解释器模式的一部分，用于在运行时解释执行关系代数操作
 * 解释器模式允许Calcite直接执行查询计划，而不需要生成Java代码
 * 
 * 工作原理：
 * 1. 从左右两个输入源读取所有行数据
 * 2. 根据操作类型(UNION/INTERSECT/EXCEPT)和是否去重(all标志)进行相应的集合运算
 * 3. 将结果发送到输出sink
 * 
 * 对于支持重复元素的操作(UNION ALL等)，使用HashMultiset来保留重复次数
 * 对于去重操作(UNION/INTERSECT/EXCEPT)，使用HashSet来确保唯一性
 */
public class SetOpNode implements Node { // 定义SetOpNode类，实现Node接口，表示一个可执行的解释器节点
  private final Source leftSource; // 左侧输入源，用于从左子节点接收行数据，final修饰表示初始化后不可变
  private final Source rightSource; // 右侧输入源，用于从右子节点接收行数据，final修饰表示初始化后不可变
  private final Sink sink; // 输出接收器，用于将处理后的行数据发送到下一个节点，final修饰表示初始化后不可变
  private final SetOp setOp; // 集合操作的关系表达式对象，包含操作类型(UNION/INTERSECT/EXCEPT)和是否去重(all标志)等信息，final修饰表示初始化后不可变

  /**
   * 构造方法，用于创建SetOpNode实例
   * @param compiler 编译器对象，用于创建输入源和输出接收器
   * @param setOp 集合操作的关系表达式，包含操作类型和元数据信息
   * 
   * 构造过程：
   * 1. 使用编译器为setOp的第0个输入创建左侧数据源
   * 2. 使用编译器为setOp的第1个输入创建右侧数据源
   * 3. 使用编译器为setOp创建输出接收器
   * 4. 保存setOp引用以便后续使用
   */
  public SetOpNode(Compiler compiler, SetOp setOp) { // 构造方法，接收编译器和集合操作关系表达式作为参数
    leftSource = compiler.source(setOp, 0); // 通过编译器为setOp的第0个输入(左侧)创建数据源，用于接收左子节点的数据
    rightSource = compiler.source(setOp, 1); // 通过编译器为setOp的第1个输入(右侧)创建数据源，用于接收右子节点的数据
    sink = compiler.sink(setOp); // 通过编译器为setOp创建输出接收器，用于将结果发送到下一个节点
    this.setOp = setOp; // 保存setOp引用，以便在run方法中访问操作类型和all标志
  }

  /**
   * 关闭方法，释放资源
   * 当解释器执行完毕或需要清理资源时调用
   * 关闭左右两个输入源，释放它们占用的资源
   */
  @Override public void close() { // 实现Node接口的close方法，用于关闭节点并释放资源
    leftSource.close(); // 关闭左侧输入源，释放相关资源(如文件句柄、数据库连接等)
    rightSource.close(); // 关闭右侧输入源，释放相关资源
  }

  /**
   * 执行方法，执行集合操作的核心逻辑
   * 该方法实现了集合操作的具体算法，包括：
   * 1. 根据all标志选择合适的数据结构(HashSet或HashMultiset)
   * 2. 从左右输入源读取所有行数据
   * 3. 根据操作类型执行相应的集合运算
   * 4. 将结果发送到输出接收器
   * 
   * @throws InterruptedException 如果执行过程中被中断则抛出此异常
   * 
   * 算法详解：
   * - UNION(并集): 将左右集合合并，如果all=false则去重，all=true则保留所有重复
   * - INTERSECT(交集): 返回左右集合共有的元素，如果all=false则去重，all=true则保留最小重复次数
   * - EXCEPT(差集): 返回在左集合但不在右集合中的元素，如果all=false则去重，all=true则考虑重复次数
   */
  @Override public void run() throws InterruptedException { // 实现Node接口的run方法，执行集合操作的核心逻辑
    final Collection<Row> leftRows; // 声明左侧行集合，用于存储从左输入源读取的所有行数据，final修饰表示引用不可变
    final Collection<Row> rightRows; // 声明右侧行集合，用于存储从右输入源读取的所有行数据，final修饰表示引用不可变
    if (setOp.all) { // 判断是否需要保留重复元素(如UNION ALL、INTERSECT ALL、EXCEPT ALL)
      leftRows = HashMultiset.create(); // 创建HashMultiset实例作为左集合，HashMultiset允许元素重复，会记录每个元素的出现次数
      rightRows = HashMultiset.create(); // 创建HashMultiset实例作为右集合，用于处理需要保留重复次数的集合操作
    } else { // 如果不需要保留重复元素(如UNION、INTERSECT、EXCEPT)
      leftRows = new HashSet<>(); // 创建HashSet实例作为左集合，HashSet会自动去重，确保每个元素唯一
      rightRows = new HashSet<>(); // 创建HashSet实例作为右集合，用于处理需要去重的集合操作
    }
    Row row; // 声明行变量，用于临时存储从输入源读取的每一行数据
    while ((row = leftSource.receive()) != null) { // 循环从左侧输入源接收行数据，直到返回null表示数据读取完毕
      leftRows.add(row); // 将接收到的行添加到左集合中，如果是HashSet则自动去重，如果是HashMultiset则记录重复次数
    }
    while ((row = rightSource.receive()) != null) { // 循环从右侧输入源接收行数据，直到返回null表示数据读取完毕
      rightRows.add(row); // 将接收到的行添加到右集合中，如果是HashSet则自动去重，如果是HashMultiset则记录重复次数
    }
    switch (setOp.kind) { // 根据集合操作的类型执行不同的算法
    case INTERSECT: // 交集操作：返回左右集合共有的元素
      for (Row leftRow : leftRows) { // 遍历左集合中的每一行
        if (rightRows.remove(leftRow)) { // 尝试从右集合中移除该行，如果移除成功说明右集合中也存在该行
          sink.send(leftRow); // 将该行发送到输出接收器，因为它是左右集合的交集部分
        } // 如果移除失败，说明右集合中没有该行或该行已被处理完，不发送到输出
      } // 对于HashMultiset，remove会减少计数器，确保重复次数正确处理
      break; // 跳出switch语句
    case EXCEPT: // 差集操作：返回在左集合但不在右集合中的元素
      for (Row leftRow : leftRows) { // 遍历左集合中的每一行
        if (!rightRows.remove(leftRow)) { // 尝试从右集合中移除该行，如果移除失败说明右集合中没有该行
          sink.send(leftRow); // 将该行发送到输出接收器，因为它是左集合独有的元素
        } // 如果移除成功，说明右集合中也有该行，不发送到输出(因为差集不包括两集合共有的元素)
      } // 对于HashMultiset，remove会正确处理重复次数，确保EXCEPT ALL的正确性
      break; // 跳出switch语句
    case UNION: // 并集操作：返回左右集合的所有元素
      leftRows.addAll(rightRows); // 将右集合的所有元素添加到左集合中，实现集合合并
      for (Row r : leftRows) { // 遍历合并后的左集合中的每一行
        sink.send(r); // 将每一行发送到输出接收器，完成并集操作
      } // 如果是HashSet，addAll会自动去重；如果是HashMultiset，会保留所有重复
      break; // 跳出switch语句
    default: // 默认情况：处理未知的集合操作类型
      break; // 直接跳出switch语句，不做任何操作
    }
  }
}
