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
package org.apache.calcite.interpreter;  // 声明包名，该类属于org.apache.calcite.interpreter包，这是Calcite解释器模块的核心包

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入Checker Framework的可空注解，用于标记可能为null的返回值，帮助静态分析工具检测空指针异常

/**
 * Source of rows.  // 行数据源接口，定义了从数据源读取行的基本行为
 *
 * <p>Corresponds to an input of a relational expression.  // 对应关系表达式的输入端，在查询执行过程中，关系表达式（如扫描、过滤、投影等）需要从某个数据源获取输入行，本接口就是这种数据源的抽象
 * 
 * <p>该接口是Calcite解释器（Interpreter）执行引擎的核心接口之一，解释器是一种基于解释执行模式的查询执行引擎，
 * 与传统的火山模型（迭代器模型）不同，解释器通过解释和执行关系代数操作树来处理查询。
 * 
 * <p>Source接口的主要作用：
 * 1. 提供统一的行数据读取接口，使得不同的数据源（如表扫描、子查询结果、中间结果等）都可以通过相同的接口访问
 * 2. 支持流式数据读取，每次调用receive()方法读取一行数据，直到返回null表示数据结束
 * 3. 实现AutoCloseable接口，支持资源的自动关闭和释放，确保在数据处理完成后能够正确释放资源
 * 
 * <p>典型使用场景：
 * - TableScan：从表中扫描数据行
 * - Filter：接收上游数据源，过滤后输出符合条件的行
 * - Project：接收上游数据源，对每行进行投影转换后输出
 * - Join：接收左右两个数据源，执行连接操作后输出结果行
 * 
 * <p>实现类通常包括：
 * - ArraySource：从内存数组中读取数据
 * - EnumeratorSource：包装枚举器作为数据源
 * - JoinSource：连接操作的数据源实现
 * - FilterSource：过滤操作的数据源实现
 */
public interface Source extends AutoCloseable {  // 定义Source接口，继承AutoCloseable接口以支持资源自动管理
  /** Reads a row. Null means end of data.  */  // 方法注释：读取一行数据，返回null表示数据已读完（EOF）
  @Nullable Row receive();  // 声明receive方法，这是核心的数据读取方法，每次调用返回一行数据（Row对象），当没有更多数据时返回null，@Nullable注解表示返回值可能为null

  @Override void close();  // 重写AutoCloseable接口的close方法，用于关闭数据源并释放相关资源（如文件句柄、数据库连接、内存缓冲区等），该方法应该在数据处理完成后调用，确保资源不会泄漏
}
