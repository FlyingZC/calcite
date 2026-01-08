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
// Apache软件基金会许可证声明，定义代码的使用权限和限制
// 说明此代码遵循Apache 2.0许可证，允许在遵守条款的前提下自由使用、修改和分发

package org.apache.calcite;  // 声明此注解类属于org.apache.calcite包，这是Calcite框架的核心包之一

import org.immutables.value.Value;  // 引入Immutables库的Value注解，用于生成不可变对象的实现代码

import java.lang.annotation.ElementType;  // 引入Java注解的ElementType枚举，用于定义注解可以应用的目标类型
import java.lang.annotation.Target;  // 引入Java的Target元注解，用于指定自定义注解可以应用的程序元素

/**
 * Annotation to be used to convert interfaces/abstract classes into
 * Immutable POJO using Immutables package.
 */
// 类级JavaDoc注释：说明此注解的作用是将接口或抽象类转换为使用Immutables库生成的不可变POJO（Plain Old Java Object）对象
// Immutables是一个Java代码生成库，能够自动创建不可变对象、构建器模式等代码
// 在Calcite框架中，大量使用不可变对象来确保数据的一致性和线程安全性
@Target({ElementType.PACKAGE, ElementType.TYPE})  // @Target元注解：指定CalciteImmutable注解可以应用的目标类型，PACKAGE表示可以应用于包声明，TYPE表示可以应用于类、接口、枚举等类型
@Value.Style(  // @Value.Style是Immutables库提供的元注解，用于配置生成代码的风格和命名规范
    visibility = Value.Style.ImplementationVisibility.PACKAGE,  // visibility属性：设置生成的实现类的可见性为包级别（package-private），这意味着生成的实现类只能在同一个包内访问，对外隐藏实现细节，这是良好的封装实践
    defaults = @Value.Immutable(builder = true, singleton = true),  // defaults属性：定义默认的不可变注解配置，builder=true表示自动生成Builder模式的构建器类，singleton=true表示生成的实现类支持单例模式，这样可以优化内存使用
    get = {"is*", "get*"},  // get属性：定义getter方法的命名模式，"is*"表示布尔类型的属性使用is前缀（如isActive()），"get*"表示其他类型的属性使用get前缀（如getName()），这符合JavaBean的命名规范
    init = "with*",  // init属性：定义用于设置属性值的方法命名模式，"with*"表示所有属性修改方法都使用with前缀（如withName(String name)），这是不可变对象的标准设计模式，每次修改都会返回一个新对象
    passAnnotations = SuppressWarnings.class  // passAnnotations属性：指定在代码生成过程中需要传递的注解类型，SuppressWarnings注解将被保留到生成的代码中，用于抑制编译器的警告信息
)
public @interface CalciteImmutable { }  // 定义CalciteImmutable注解本身，这是一个标记注解（marker annotation），没有定义任何属性，它的存在就表示被注解的元素应该由Immutables库生成不可变实现类
