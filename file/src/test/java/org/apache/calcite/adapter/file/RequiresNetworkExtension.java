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
package org.apache.calcite.adapter.file; // 定义包名，该类属于 org.apache.calcite.adapter.file 包，主要用于文件适配器相关的测试扩展

import org.junit.jupiter.api.extension.ConditionEvaluationResult; // 导入 JUnit 5 的条件评估结果类，用于表示测试条件的执行结果（启用或禁用）
import org.junit.jupiter.api.extension.ExecutionCondition; // 导入 JUnit 5 的执行条件接口，用于实现自定义的测试执行条件逻辑
import org.junit.jupiter.api.extension.ExtensionContext; // 导入 JUnit 5 的扩展上下文接口，提供测试执行的上下文信息
import org.junit.platform.commons.support.AnnotationSupport; // 导入 JUnit 平台的注解支持工具类，用于查找和处理注解

import java.net.Socket; // 导入 Java 的 Socket 类，用于创建网络连接以测试主机可达性

import static org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled; // 静态导入禁用条件结果的方法，用于创建表示测试被禁用的结果对象
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled; // 静态导入启用条件结果的方法，用于创建表示测试被启用的结果对象

/**
 * Enables to activate test conditionally if the specified host is reachable.
 * Note: it is recommended to avoid creating tests that depend on external servers.
 */
// JUnit 5 扩展类，实现 ExecutionCondition 接口，用于根据网络连接条件决定是否执行特定的测试方法
// 该类会检查被测试方法或类上的 @RequiresNetwork 注解，如果存在该注解且指定的主机可达，则启用测试；否则禁用测试
// 这种设计允许测试在网络环境不可用时自动跳过，避免测试失败，提高测试套件的健壮性
public class RequiresNetworkExtension implements ExecutionCondition { // 定义类名并实现 ExecutionCondition 接口，表明这是一个自定义的 JUnit 5 测试执行条件扩展
  @Override public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) { // 重写接口方法，评估测试执行条件，参数 context 为扩展上下文，包含测试的元数据信息
    return context.getElement() // 获取当前测试元素（可能是测试方法或测试类），返回 Optional 类型
        .flatMap(element -> AnnotationSupport.findAnnotation(element, RequiresNetwork.class)) // 在测试元素上查找 @RequiresNetwork 注解，如果找到则返回注解的 Optional，否则返回空 Optional
        .map(net -> { // 如果找到 @RequiresNetwork 注解，则使用 map 方法对注解进行处理，参数 net 为找到的注解对象
          try (Socket ignored = new Socket(net.host(), net.port())) { // 尝试创建 Socket 连接到注解中指定的主机和端口，使用 try-with-resources 确保连接自动关闭
            return enabled(net.host() + ":" + net.port() + " is reachable"); // 如果连接成功，返回启用的条件评估结果，包含主机可达的提示信息
          } catch (Exception e) { // 捕获连接过程中可能出现的任何异常（如连接超时、主机不可达等）
            return disabled(net.host() + ":" + net.port() + " is unreachable: " + e.getMessage()); // 如果连接失败，返回禁用的条件评估结果，包含主机不可达和异常信息的提示
          } // 结束 try-catch 块
        }) // 结束 map 操作，返回 Optional<ConditionEvaluationResult>
        .orElseGet(() -> enabled("@RequiresNetwork is not found")); // 如果没有找到 @RequiresNetwork 注解，则默认启用测试，返回启用的条件评估结果并包含未找到注解的提示信息
  } // 结束 evaluateExecutionCondition 方法
} // 结束 RequiresNetworkExtension 类定义
