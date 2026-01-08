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
package org.apache.calcite.adapter.arrow; // 声明包名，该类属于Calcite的Arrow适配器包，用于处理Arrow格式的数据

import org.apache.calcite.util.ImmutableIntList; // 导入Calcite工具类，用于存储不可变的整数列表，通常用于表示字段索引
import org.apache.calcite.util.Util; // 导入Calcite工具类，提供通用的工具方法，如异常转换

import org.apache.arrow.gandiva.evaluator.Projector; // 导入Arrow Gandiva的Projector类，用于执行投影操作（选择和计算字段）
import org.apache.arrow.gandiva.exceptions.GandivaException; // 导入Arrow Gandiva异常类，处理Gandiva引擎执行时的异常
import org.apache.arrow.vector.ipc.ArrowFileReader; // 导入Arrow文件读取器，用于读取Arrow格式的文件
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch; // 导入Arrow记录批次类，表示一批Arrow记录

import java.io.IOException; // 导入Java IO异常类，处理输入输出操作时的异常

/**
 * Enumerator that reads from a projected collection of Arrow value-vectors.
 * 从Arrow值向量的投影集合中读取数据的枚举器
 * 
 * 该类是AbstractArrowEnumerator的子类，专门用于处理带有投影操作的Arrow数据读取
 * 投影操作是指从原始数据中选择特定的字段，并可能对这些字段进行计算或转换
 * 
 * 主要功能：
 * 1. 使用Arrow Gandiva引擎的Projector来执行投影操作
 * 2. 逐批次读取Arrow文件中的数据
 * 3. 对每个批次应用投影操作，生成新的值向量
 * 4. 提供迭代器接口，逐行访问投影后的数据
 * 
 * 使用场景：
 * 当SQL查询中包含SELECT子句，需要从表中选择特定列或进行列计算时使用
 * 例如：SELECT name, age * 2 FROM table
 */
class ArrowProjectEnumerator extends AbstractArrowEnumerator { // 定义ArrowProjectEnumerator类，继承自AbstractArrowEnumerator基类
  private final Projector projector; // 成员变量：Arrow Gandiva的Projector对象，用于执行投影操作，final表示一旦初始化就不能修改

  ArrowProjectEnumerator(ArrowFileReader arrowFileReader, ImmutableIntList fields, // 构造方法：创建ArrowProjectEnumerator实例，参数包括Arrow文件读取器、字段索引列表和投影器
      Projector projector) { // 参数：projector是Gandiva引擎的Projector对象，用于执行投影操作
    super(arrowFileReader, fields); // 调用父类AbstractArrowEnumerator的构造方法，初始化Arrow文件读取器和字段索引列表
    this.projector = projector; // 将传入的projector参数赋值给成员变量，保存投影器引用
  } // 构造方法结束

  @Override protected void evaluateOperator(ArrowRecordBatch arrowRecordBatch) { // 重写父类方法：评估操作符，对Arrow记录批次应用投影操作
    try { // 开始try块，捕获可能发生的异常
      projector.evaluate(arrowRecordBatch, valueVectors); // 调用Projector的evaluate方法，对输入的ArrowRecordBatch执行投影操作，结果输出到valueVectors中
    } catch (GandivaException e) { // 捕获Gandiva异常，这是Gandiva引擎执行过程中可能抛出的异常
      throw Util.toUnchecked(e); // 使用Calcite的Util工具将受检异常转换为非受检异常，简化异常处理
    } // try-catch块结束
  } // evaluateOperator方法结束

  @Override public boolean moveNext() { // 重写父类方法：移动到下一行数据，返回true表示还有数据，false表示已到末尾
    if (currRowIndex >= rowCount - 1) { // 判断当前行索引是否已经到达当前批次的最后一行（rowCount-1是最后一行的索引）
      final boolean hasNextBatch; // 声明一个final变量，用于存储是否还有下一个批次
      try { // 开始try块，捕获可能发生的IO异常
        hasNextBatch = arrowFileReader.loadNextBatch(); // 调用Arrow文件读取器的loadNextBatch方法，尝试加载下一个批次的数据
      } catch (IOException e) { // 捕获IO异常，这是文件读取过程中可能抛出的异常
        throw Util.toUnchecked(e); // 使用Calcite的Util工具将受检异常转换为非受检异常
      } // try-catch块结束
      if (hasNextBatch) { // 如果成功加载了下一个批次
        currRowIndex = 0; // 将当前行索引重置为0，表示从新批次的第一行开始
        this.valueVectors.clear(); // 清空当前的值向量列表，准备接收新批次的数据
        loadNextArrowBatch(); // 调用loadNextArrowBatch方法（继承自父类），加载下一个Arrow批次并初始化值向量
      } // if块结束
      return hasNextBatch; // 返回是否还有下一个批次，如果没有批次了，返回false，迭代结束
    } else { // 如果当前行索引还没有到达当前批次的最后一行
      currRowIndex++; // 将当前行索引加1，移动到下一行
      return true; // 返回true，表示还有数据可以读取
    } // if-else块结束
  } // moveNext方法结束

  @Override public void close() { // 重写父类方法：关闭枚举器，释放资源
    try { // 开始try块，捕获可能发生的异常
      projector.close(); // 调用Projector的close方法，释放Projector占用的资源（如内存、计算上下文等）
    } catch (GandivaException e) { // 捕获Gandiva异常，这是关闭Projector时可能抛出的异常
      throw Util.toUnchecked(e); // 使用Calcite的Util工具将受检异常转换为非受检异常
    } // try-catch块结束
  } // close方法结束
} // ArrowProjectEnumerator类定义结束
