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
package org.apache.calcite.adapter.arrow; // 定义包名，该类位于Arrow适配器模块中，用于处理Apache Arrow数据格式的测试扩展

import org.apache.calcite.config.CalciteSystemProperty; // 导入Calcite系统属性配置类，用于读取测试相关的系统属性配置

import org.apache.arrow.gandiva.evaluator.Projector; // 导入Arrow Gandiva投影器类，用于执行向量化投影操作，Gandiva是Arrow的向量化表达式求值引擎
import org.apache.arrow.gandiva.exceptions.GandivaException; // 导入Gandiva异常类，用于捕获Gandiva引擎执行过程中的异常
import org.apache.arrow.gandiva.expression.ExpressionTree; // 导入表达式树类，用于表示Gandiva中的表达式树结构

import org.apache.arrow.vector.types.pojo.Schema; // 导入Arrow Schema类，用于描述Arrow数据的模式（表结构），包含字段列表和元数据

import org.junit.jupiter.api.extension.ConditionEvaluationResult; // 导入JUnit5条件评估结果类，用于返回测试是否应该执行的结果
import org.junit.jupiter.api.extension.ExecutionCondition; // 导入JUnit5执行条件接口，用于实现自定义的测试执行条件逻辑
import org.junit.jupiter.api.extension.ExtensionContext; // 导入JUnit5扩展上下文接口，提供测试执行的上下文信息

import java.util.ArrayList; // 导入ArrayList类，用于创建动态数组列表
import java.util.List; // 导入List接口，用于定义列表类型

/**
 * JUnit5 extension to handle Arrow tests. // JUnit5扩展类，用于处理Arrow相关的测试
 *
 * <p>Tests will be skipped if the Gandiva library cannot be loaded on the given platform. // 如果在给定平台上无法加载Gandiva库，则跳过测试
 */
class ArrowExtension implements ExecutionCondition { // 定义ArrowExtension类，实现ExecutionCondition接口，用于控制Arrow测试的执行条件

  /**
   * Whether to run this test. // 判断是否应该运行当前测试方法
   *
   * <p>Enabled by default, unless explicitly disabled from command line // 默认启用，除非通过命令行显式禁用
   * ({@code -Dcalcite.test.arrow=false}) or if Gandiva library, used to implement arrow // 或用于实现Arrow过滤/投影的Gandiva库
   * filtering/projection, cannot be loaded. // 无法加载
   *
   * @return {@code true} if the test is enabled and can run in the current environment, // 如果测试已启用且可以在当前环境中运行，返回true
   *         {@code false} otherwise // 否则返回false
   */
  @Override public ConditionEvaluationResult evaluateExecutionCondition( // 重写ExecutionCondition接口的方法，评估测试执行条件
      final ExtensionContext context) { // 接收ExtensionContext参数，提供测试执行的上下文信息（如测试类、测试方法等）

    boolean enabled = CalciteSystemProperty.TEST_ARROW.value(); // 从系统属性中读取calcite.test.arrow配置值，判断是否启用Arrow测试
    try { // 开始try块，用于测试Gandiva库是否可以正常加载和使用
      Schema emptySchema = new Schema(new ArrayList<>(), null); // 创建一个空的Arrow Schema对象，用于测试Gandiva库是否可用，参数为空字段列表和null元数据
      List<ExpressionTree> expressions = new ArrayList<>(); // 创建一个空的表达式树列表，用于测试Projector的创建
      Projector.make(emptySchema, expressions); // 尝试创建一个Projector对象，这会触发Gandiva JNI库的加载，如果库不可用会抛出UnsatisfiedLinkError
    } catch (GandivaException e) { // 捕获Gandiva异常，表示Gandiva库已成功加载但表达式执行出错（正常情况，因为使用了空表达式）
      // this exception comes from using an empty expression, // 这个异常是因为使用了空表达式导致的
      // but the JNI library was loaded properly // 但JNI库已成功加载，说明Gandiva库可用
    } catch (UnsatisfiedLinkError e) { // 捕获链接错误，表示无法加载Gandiva的JNI库（如缺少本地库文件或平台不支持）
      enabled = false; // 设置enabled为false，禁用Arrow测试，因为Gandiva库不可用
    } // 结束catch块

    if (enabled) { // 如果enabled为true，表示测试应该运行
      return ConditionEvaluationResult.enabled("Arrow tests enabled"); // 返回启用状态，附带说明信息"Arrow tests enabled"
    } else { // 否则，enabled为false，表示测试应该跳过
      return ConditionEvaluationResult.disabled("Arrow tests disabled"); // 返回禁用状态，附带说明信息"Arrow tests disabled"
    } // 结束if-else语句
  } // 结束evaluateExecutionCondition方法
} // 结束ArrowExtension类
