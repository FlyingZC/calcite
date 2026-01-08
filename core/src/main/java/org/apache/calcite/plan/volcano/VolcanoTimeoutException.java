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
package org.apache.calcite.plan.volcano; // 定义包名，该类属于org.apache.calcite.plan.volcano包，是Volcano优化器相关异常类

/**
 * Indicates that planning timed out. This is not an error; you can
 * retry the operation.
 * // 类作用说明：表示Volcano优化器的查询规划过程超时。这不是一个错误，可以重试该操作。
 * // Volcano优化器是Calcite中基于成本的优化器（CBO），使用动态规划算法来搜索最优的执行计划。
 * // 当优化过程超过预设的时间限制时，会抛出此异常，防止优化器陷入无限循环或消耗过多资源。
 * // 该异常继承自RuntimeException，是一个运行时异常，不需要强制捕获。
 */
public class VolcanoTimeoutException extends RuntimeException { // 定义VolcanoTimeoutException类，继承自RuntimeException，表示Volcano优化器超时异常
  // 成员变量：无显式定义的成员变量，所有成员变量都继承自父类RuntimeException
  // RuntimeException包含的成员变量主要有：
  // - detailMessage: 异常的详细信息字符串
  // - cause: 导致此异常的原因（另一个Throwable对象）
  // - stackTrace: 异常的堆栈跟踪信息

  /**
   * Default constructor.
   * // 构造方法说明：无参构造方法，创建一个VolcanoTimeoutException实例
   * // 该构造方法调用父类RuntimeException的构造方法，传入固定的错误消息"Volcano timeout"
   * // 并将cause设置为null，表示没有嵌套异常
   * // 当Volcano优化器检测到规划超时时，会抛出这个异常
   */
  public VolcanoTimeoutException() { // 无参构造方法，用于创建Volcano超时异常实例
    super("Volcano timeout", null); // 调用父类RuntimeException的构造方法，传入错误消息"Volcano timeout"和null作为cause
  } // 构造方法结束
} // VolcanoTimeoutException类定义结束
