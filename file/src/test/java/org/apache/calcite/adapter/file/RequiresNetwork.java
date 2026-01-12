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
// Apache许可证声明，表明该代码遵循Apache 2.0许可证，允许自由使用和修改
package org.apache.calcite.adapter.file; // 定义包名，该注解属于org.apache.calcite.adapter.file包，主要用于文件适配器相关的测试

import java.lang.annotation.ElementType; // 导入ElementType枚举类，用于指定注解可以应用的程序元素类型（如类、方法、字段等）
import java.lang.annotation.Retention; // 导入Retention注解，用于指定注解的保留策略（源码级别、编译级别或运行时级别）
import java.lang.annotation.RetentionPolicy; // 导入RetentionPolicy枚举类，定义注解的三种保留策略：SOURCE、CLASS、RUNTIME
import java.lang.annotation.Target; // 导入Target注解，用于指定自定义注解可以应用在哪些Java元素上

/**
 * Enables to activate test conditionally if the specified host is reachable.
 * Note: it is recommended to avoid creating tests that depend on external servers.
 */
// 这是一个用于条件性激活测试的注解类，只有当指定的主机可达时才会运行被标记的测试方法
// 注意：建议避免创建依赖外部服务器的测试，因为这会使测试不稳定且不可靠
// 该注解主要用于集成测试场景，当测试需要访问网络资源时，可以根据网络连接情况决定是否执行测试
@Retention(RetentionPolicy.RUNTIME) // 指定注解的保留策略为RUNTIME，表示该注解在编译后会被保留到class文件中，并在运行时可以通过反射机制获取到
// RetentionPolicy.RUNTIME是三个保留策略中最强的，使得注解在程序运行的整个生命周期都可用
// 这对于测试框架在运行时动态检查注解并决定是否执行测试是必需的
@Target({ElementType.METHOD}) // 指定该注解只能应用在方法上，不能应用在类、字段、参数等其他元素上
// ElementType.METHOD表示该注解的目标是方法，这符合测试注解的使用场景，因为测试通常是以方法为单位执行的
// 使用花括号{}包裹，虽然这里只有一个元素，但这是Java注解的标准写法，便于后续扩展
public @interface RequiresNetwork { // 定义一个名为RequiresNetwork的公共注解接口，@interface关键字表示这是一个注解类型而非普通接口
// 注解名称RequiresNetwork清晰地表达了该注解的用途：要求网络连接
// public修饰符表示该注解可以在任何地方被访问和使用
// 注解本质上是一种特殊的接口，编译器会自动处理它，使其符合注解的语义规范
  String host() default  "en.wikipedia.org"; // 定义一个名为host的注解属性，类型为String，默认值为"en.wikipedia.org"
// host属性用于指定需要连接的主机名或IP地址，测试框架会尝试连接到该主机以判断网络是否可用
// 默认值设置为维基百科的英文站点，这是一个通常可访问的公共网站，适合作为网络连通性测试的目标
// String类型允许使用域名（如"en.wikipedia.org"）或IP地址（如"208.80.154.224"）
// default关键字提供了默认值，使得使用该注解时可以不指定host属性，简化注解的使用
  int port() default 80; // 定义一个名为port的注解属性，类型为int（整数类型），默认值为80（HTTP协议的标准端口）
// port属性用于指定需要连接的端口号，与host属性配合使用，确定网络连接的目标地址
// 默认值80是HTTP协议的默认端口，与默认host"en.wikipedia.org"配合使用，可以测试基本的HTTP网络连接
// int类型确保端口号必须是有效的整数（0-65535），测试框架应当验证端口号的有效性
// 常见的端口号包括：80（HTTP）、443（HTTPS）、21（FTP）、22（SSH）等，可以根据测试需求指定不同的端口
} // 注解定义结束，花括号标志着RequiresNetwork注解接口的完整定义结束
// 该注解虽然简单，但在测试框架中起到重要作用，使得测试可以根据网络环境动态启用或禁用
// 使用示例：@RequiresNetwork(host = "example.com", port = 443) 或 @RequiresNetwork()（使用默认值）
