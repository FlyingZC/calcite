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
package org.apache.calcite.adapter.arrow; // 声明包名，该类属于Apache Calcite的Arrow适配器包

import org.apache.calcite.util.ImmutableIntList; // 导入不可变整数列表工具类，用于存储字段索引
import org.apache.calcite.util.Util; // 导入Calcite工具类，用于异常转换等操作

import org.apache.arrow.gandiva.evaluator.Filter; // 导入Arrow Gandiva的过滤器接口，用于执行过滤表达式
import org.apache.arrow.gandiva.evaluator.SelectionVector; // 导入选择向量接口，用于标识哪些行满足过滤条件
import org.apache.arrow.gandiva.evaluator.SelectionVectorInt16; // 导入16位整数类型的选择向量实现
import org.apache.arrow.gandiva.exceptions.GandivaException; // 导入Gandiva异常类，处理Gandiva执行时的错误
import org.apache.arrow.memory.ArrowBuf; // 导入Arrow缓冲区类，用于管理Arrow的内存缓冲
import org.apache.arrow.memory.BufferAllocator; // 导入缓冲区分配器接口，用于分配和管理Arrow内存
import org.apache.arrow.memory.RootAllocator; // 导入根分配器实现，是Arrow内存分配的顶层分配器
import org.apache.arrow.vector.ipc.ArrowFileReader; // 导入Arrow文件读取器，用于读取Arrow格式的文件
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch; // 导入Arrow记录批次类，表示一批Arrow记录

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的字段

import java.io.IOException; // 导入IO异常类，处理文件读写时的错误

import static java.util.Objects.requireNonNull; // 导入静态方法，用于检查对象是否为null

/**
 * Enumerator that reads from a filtered collection of Arrow value-vectors.
 * 枚举器类，用于从经过过滤的Arrow值向量集合中读取数据
 * 
 * 这个类是Apache Calcite适配器中用于Arrow数据源的核心类之一，它负责：
 * 1. 从Arrow文件中读取数据批次（ArrowRecordBatch）
 * 2. 使用Gandiva表达式引擎对每个批次应用过滤条件
 * 3. 通过选择向量（SelectionVector）只返回满足过滤条件的记录
 * 4. 实现枚举器接口，提供逐行遍历过滤后数据的能力
 * 
 * 继承自AbstractArrowEnumerator，复用了基础的Arrow数据读取逻辑
 * 
 * 工作原理：
 * - Gandiva是Arrow的向量化表达式执行引擎，可以高效地应用过滤条件
 * - SelectionVectorInt16存储了满足过滤条件的记录索引（使用16位整数，最多支持65535条记录）
 * - moveNext()方法通过选择向量逐条返回满足条件的记录
 */
class ArrowFilterEnumerator extends AbstractArrowEnumerator { // 定义Arrow过滤枚举器类，继承自抽象Arrow枚举器基类
  private final BufferAllocator allocator; // Arrow内存分配器，用于为选择向量分配内存，final表示一旦初始化不可改变
  private final Filter filter; // Gandiva过滤器对象，封装了过滤表达式，final表示初始化后不可更改
  private @Nullable ArrowBuf buf; // Arrow缓冲区对象，用于存储选择向量的数据，@Nullable表示可能为null
  private @Nullable SelectionVector selectionVector; // 选择向量对象，用于标识哪些行满足过滤条件，@Nullable表示可能为null
  private int selectionVectorIndex; // 当前选择向量的索引位置，用于跟踪当前遍历到选择向量的哪个位置

  ArrowFilterEnumerator(ArrowFileReader arrowFileReader, ImmutableIntList fields, Filter filter) { // 构造方法，初始化Arrow过滤枚举器
    super(arrowFileReader, fields); // 调用父类构造方法，传入Arrow文件读取器和字段索引列表
    this.allocator = new RootAllocator(Long.MAX_VALUE); // 创建根内存分配器，使用最大值作为内存限制（实际使用时受系统限制）
    this.filter = filter; // 保存传入的过滤器对象，该对象包含了要应用的过滤条件表达式
  } // 构造方法结束

  @Override void evaluateOperator(ArrowRecordBatch arrowRecordBatch) { // 重写父类方法，对当前记录批次应用过滤操作
    try { // 开始try块，捕获可能出现的Gandiva异常
      this.buf = this.allocator.buffer((long) rowCount * 2); // 分配内存缓冲区，大小为行数乘以2（因为使用16位整数存储索引，每条记录占2字节）
      this.selectionVector = new SelectionVectorInt16(buf); // 创建16位整数类型的选择向量，使用刚分配的缓冲区存储索引数据
      filter.evaluate(arrowRecordBatch, selectionVector); // 调用Gandiva过滤器的evaluate方法，对记录批次应用过滤条件，结果写入选择向量
    } catch (GandivaException e) { // 捕获Gandiva执行过程中可能抛出的异常
      throw Util.toUnchecked(e); // 将受检异常转换为未受检异常，简化异常处理链
    } // try-catch块结束
  } // evaluateOperator方法结束

  @Override public boolean moveNext() { // 重写枚举器的moveNext方法，移动到下一条满足过滤条件的记录
    if (selectionVector == null // 如果选择向量为null（表示还没有加载或处理过任何批次）
        || selectionVectorIndex >= selectionVector.getRecordCount()) { // 或者当前索引已经超过选择向量中的记录数（当前批次已遍历完）
      boolean hasNextBatch; // 声明变量，用于标记是否还有下一个批次可加载
      while (true) { // 开始无限循环，持续加载批次直到找到有记录的批次或没有更多批次
        try { // 开始try块，捕获IO异常
          hasNextBatch = arrowFileReader.loadNextBatch(); // 尝试从Arrow文件中加载下一个记录批次
        } catch (IOException e) { // 捕获文件读取时可能出现的IO异常
          throw Util.toUnchecked(e); // 将受检异常转换为未受检异常
        } // try-catch块结束
        if (hasNextBatch) { // 如果成功加载了下一个批次
          selectionVectorIndex = 0; // 重置选择向量索引为0，准备从新批次的第一条记录开始
          this.valueVectors.clear(); // 清空值向量列表，释放之前批次的向量引用
          loadNextArrowBatch(); // 调用父类方法加载实际的Arrow批次数据（包括调用evaluateOperator应用过滤）
          requireNonNull(selectionVector, "selectionVector"); // 检查选择向量是否为null，如果为null则抛出NullPointerException
          if (selectionVectorIndex >= selectionVector.getRecordCount()) { // 如果当前批次经过过滤后没有满足条件的记录（记录数为0）
            // the "filtered" batch is empty, but there may be more batches to fetch // 注释：过滤后的批次为空，但可能还有更多批次需要获取
            continue; // 跳过当前批次，继续循环加载下一个批次
          } // if块结束
          currRowIndex = selectionVector.getIndex(selectionVectorIndex++); // 从选择向量中获取当前索引位置的行索引，并递增索引
        } // if块结束
        return hasNextBatch; // 返回是否有下一个批次（如果有记录则返回true，否则返回false表示遍历结束）
      } // while循环结束
    } else { // 如果选择向量存在且当前批次还有未遍历的记录
      currRowIndex = selectionVector.getIndex(selectionVectorIndex++); // 从选择向量中获取当前索引位置的行索引，并递增索引
      return true; // 返回true表示成功移动到下一条记录
    } // if-else块结束
  } // moveNext方法结束

  @Override public void close() { // 重写close方法，释放枚举器占用的资源
    try { // 开始try块，捕获可能出现的Gandiva异常
      if (buf != null) { // 如果缓冲区不为null
        buf.close(); // 关闭并释放缓冲区占用的内存
      } // if块结束
      filter.close(); // 关闭过滤器，释放过滤器占用的资源（包括编译的Gandiva表达式）
    } catch (GandivaException e) { // 捕获关闭过程中可能抛出的Gandiva异常
      throw Util.toUnchecked(e); // 将受检异常转换为未受检异常
    } // try-catch块结束
  } // close方法结束
} // 类定义结束
