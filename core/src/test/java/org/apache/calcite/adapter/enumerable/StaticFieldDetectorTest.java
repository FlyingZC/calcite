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
package org.apache.calcite.adapter.enumerable;  // 定义包名，该测试类位于可枚举适配器包下

import org.apache.calcite.linq4j.tree.ClassDeclaration;  // 导入类声明类，用于表示Java类的声明结构
import org.apache.calcite.linq4j.tree.Expressions;  // 导入表达式工具类，用于创建各种表达式
import org.apache.calcite.linq4j.tree.FieldDeclaration;  // 导入字段声明类，用于表示类的字段声明

import com.google.common.collect.ImmutableList;  // 导入不可变列表类，用于创建不可修改的列表

import org.junit.jupiter.api.Test;  // 导入JUnit5的Test注解，用于标记测试方法

import java.lang.reflect.Modifier;  // 导入修饰符类，用于处理Java访问修饰符
import java.util.Arrays;  // 导入数组工具类，用于数组操作

import static org.hamcrest.CoreMatchers.is;  // 导入Hamcrest断言工具，用于值比较
import static org.hamcrest.MatcherAssert.assertThat;  // 导入Hamcrest断言工具，用于执行断言

/**
 * Tests for {@link EnumerableInterpretable.StaticFieldDetector}.
 * // 该类用于测试EnumerableInterpretable.StaticFieldDetector静态字段检测器的功能
 * // StaticFieldDetector是一个访问者模式实现，用于检测类声明中是否包含静态字段
 * // 该测试类通过创建各种类声明场景来验证检测器的正确性
 */
public final class StaticFieldDetectorTest {  // 定义测试类，使用final修饰表示不可被继承

  @Test void testClassWithoutStaticFields() {  // 测试方法：测试不包含静态字段的类
    ClassDeclaration classDeclaration =  // 创建类声明对象，表示一个Java类的声明
        createClassDeclaration(  // 调用辅助方法创建类声明，传入字段声明数组
            new FieldDeclaration(  // 创建第一个字段声明
                Modifier.PUBLIC,  // 字段修饰符为public，表示公共访问权限
                Expressions.parameter(int.class, "x"),  // 字段参数表达式，类型为int，名称为x
                Expressions.constant(0)));  // 字段初始化值为常量0

    EnumerableInterpretable.StaticFieldDetector detector =  // 创建静态字段检测器实例
        new EnumerableInterpretable.StaticFieldDetector();  // 调用无参构造函数创建检测器
    classDeclaration.accept(detector);  // 使用访问者模式，让检测器访问类声明并检测静态字段
    assertThat(detector.containsStaticField, is(false));  // 断言检测器的containsStaticField属性为false，表示未检测到静态字段
  }

  @Test void testClassWithOnlyStaticFields() {  // 测试方法：测试只包含静态字段的类
    ClassDeclaration classDeclaration =  // 创建类声明对象
        createClassDeclaration(  // 调用辅助方法创建类声明
            new FieldDeclaration(  // 创建第一个字段声明
                Modifier.PUBLIC | Modifier.STATIC,  // 字段修饰符为public和static，表示公共静态字段
                Expressions.parameter(int.class, "x"),  // 字段参数表达式，类型为int，名称为x
                Expressions.constant(0)),  // 字段初始化值为常量0
            new FieldDeclaration(  // 创建第二个字段声明
                Modifier.STATIC,  // 字段修饰符为static，表示静态字段（默认包访问权限）
                Expressions.parameter(int.class, "y"),  // 字段参数表达式，类型为int，名称为y
                Expressions.constant(0)));  // 字段初始化值为常量0

    EnumerableInterpretable.StaticFieldDetector detector =  // 创建静态字段检测器实例
        new EnumerableInterpretable.StaticFieldDetector();  // 调用无参构造函数创建检测器
    classDeclaration.accept(detector);  // 让检测器访问类声明并检测静态字段
    assertThat(detector.containsStaticField, is(true));  // 断言检测器的containsStaticField属性为true，表示检测到静态字段
  }

  @Test void testClassWithStaticAndNonStaticFields() {  // 测试方法：测试同时包含静态字段和非静态字段的类（静态字段在前）
    ClassDeclaration classDeclaration =  // 创建类声明对象
        createClassDeclaration(  // 调用辅助方法创建类声明
            new FieldDeclaration(  // 创建第一个字段声明
                Modifier.PUBLIC | Modifier.STATIC,  // 字段修饰符为public和static，表示公共静态字段
                Expressions.parameter(int.class, "x"),  // 字段参数表达式，类型为int，名称为x
                Expressions.constant(0)),  // 字段初始化值为常量0
            new FieldDeclaration(  // 创建第二个字段声明
                Modifier.PUBLIC,  // 字段修饰符为public，表示公共实例字段
                Expressions.parameter(int.class, "y"),  // 字段参数表达式，类型为int，名称为y
                Expressions.constant(0)));  // 字段初始化值为常量0

    EnumerableInterpretable.StaticFieldDetector detector =  // 创建静态字段检测器实例
        new EnumerableInterpretable.StaticFieldDetector();  // 调用无参构造函数创建检测器
    classDeclaration.accept(detector);  // 让检测器访问类声明并检测静态字段
    assertThat(detector.containsStaticField, is(true));  // 断言检测器的containsStaticField属性为true，表示检测到静态字段
  }

  @Test void testClassWithNonStaticAndStaticFields() {  // 测试方法：测试同时包含非静态字段和静态字段的类（非静态字段在前）
    ClassDeclaration classDeclaration =  // 创建类声明对象
        createClassDeclaration(  // 调用辅助方法创建类声明
            new FieldDeclaration(  // 创建第一个字段声明
                Modifier.PUBLIC,  // 字段修饰符为public，表示公共实例字段
                Expressions.parameter(int.class, "x"),  // 字段参数表达式，类型为int，名称为x
                Expressions.constant(0)),  // 字段初始化值为常量0
            new FieldDeclaration(  // 创建第二个字段声明
                Modifier.PUBLIC | Modifier.STATIC,  // 字段修饰符为public和static，表示公共静态字段
                Expressions.parameter(int.class, "y"),  // 字段参数表达式，类型为int，名称为y
                Expressions.constant(0)));  // 字段初始化值为常量0

    EnumerableInterpretable.StaticFieldDetector detector =  // 创建静态字段检测器实例
        new EnumerableInterpretable.StaticFieldDetector();  // 调用无参构造函数创建检测器
    classDeclaration.accept(detector);  // 让检测器访问类声明并检测静态字段
    assertThat(detector.containsStaticField, is(true));  // 断言检测器的containsStaticField属性为true，表示检测到静态字段
  }

  private static ClassDeclaration createClassDeclaration(FieldDeclaration... fieldDeclarations) {  // 私有静态辅助方法：创建类声明对象
    return new ClassDeclaration(  // 返回新创建的类声明对象
        Modifier.PUBLIC,  // 类修饰符为public，表示公共类
        "MyClass",  // 类名为"MyClass"
        null,  // 父类为null，表示默认继承Object类
        ImmutableList.of(),  // 接口列表为空，表示不实现任何接口
        Arrays.asList(fieldDeclarations));  // 字段声明列表，将可变参数转换为列表
  }

}  // 类定义结束
