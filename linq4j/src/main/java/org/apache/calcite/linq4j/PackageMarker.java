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
 */ // Apache许可证声明，说明代码版权和使用许可
package org.apache.calcite.linq4j; // 声明此注解属于org.apache.calcite.linq4j包

import java.lang.annotation.Retention; // 导入Retention注解，用于指定自定义注解的保留策略
import java.lang.annotation.RetentionPolicy; // 导入RetentionPolicy枚举，定义注解的三种保留策略

/**
 * This is a dummy annotation that forces javac to produce output for
 * otherwise empty package-info.java.
 * // 这是一个虚拟注解，用于强制javac编译器为原本为空的package-info.java文件产生输出
 *
 * <p>The result is maven-compiler-plugin can properly identify the scope of
 * changed files
 * // 这样做的结果是maven-compiler-plugin插件能够正确识别变更文件的作用范围
 *
 * <p>See more details in
 * <a href="https://jira.codehaus.org/browse/MCOMPILER-205">
 *   maven-compiler-plugin: incremental compilation broken</a>
 * // 更多详细信息请参考Maven编译器插件的增量编译问题报告
 */ // 类级JavaDoc注释，说明PackageMarker注解的用途和背景
@Retention(RetentionPolicy.SOURCE) // 指定注解的保留策略为SOURCE，表示注解只在源代码中保留，编译后被丢弃
public @interface PackageMarker { // 声明PackageMarker为一个公共注解类型（接口）
} // 注解定义结束，此注解不包含任何成员，是一个标记注解
