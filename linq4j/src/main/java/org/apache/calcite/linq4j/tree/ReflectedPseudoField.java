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
package org.apache.calcite.linq4j.tree; // 指定该类所属的包路径，位于org.apache.calcite.linq4j.tree包下，这是LINQ4J树形结构相关的包

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的注解，用于标记可能为null的参数或返回值，帮助进行空值检查

import java.lang.reflect.Field; // 导入Java反射API中的Field类，用于表示类的字段信息
import java.lang.reflect.Type; // 导入Java反射API中的Type接口，表示Java类型

import static java.util.Objects.requireNonNull; // 导入Objects类的静态方法requireNonNull，用于参数非空校验

/**
 * Represents a PseudoField that is implemented via a Java reflection
 * {@link Field}.
 * 表示一个通过Java反射Field实现的伪字段(PseudoField)
 * 
 * ReflectedPseudoField是PseudoField接口的一个实现类，它封装了Java反射中的Field对象
 * 伪字段(PseudoField)是LINQ4J框架中用于表示类字段的一个抽象概念，可以表示真实的Java字段，也可以表示其他形式的字段访问
 * 这个类的作用是提供一个统一的接口来访问Java反射字段，使得LINQ4J可以像操作普通字段一样操作反射字段
 * 
 * 主要功能：
 * 1. 封装Java反射Field对象，提供统一的字段访问接口
 * 2. 实现PseudoField接口的所有方法，包括获取字段名、类型、修饰符等
 * 3. 通过反射机制动态获取字段的值
 * 4. 提供equals和hashCode方法，支持对象比较
 * 
 * 使用场景：
 * - 在LINQ4J查询中需要动态访问对象的字段时
 * - 需要统一处理不同类型的字段访问（包括反射字段）时
 * - 在代码生成和表达式树构建中需要字段元信息时
 */
public class ReflectedPseudoField implements PseudoField { // 定义ReflectedPseudoField类，实现PseudoField接口，表示通过反射实现的伪字段
  private final Field field; // 成员变量：存储Java反射Field对象的引用，final表示该引用不可变，Field代表一个类的字段信息

  public ReflectedPseudoField(Field field) { // 构造方法：创建一个ReflectedPseudoField实例，参数field是要封装的Java反射Field对象
    this.field = requireNonNull(field, "field"); // 使用requireNonNull方法检查field参数是否为null，如果为null则抛出NullPointerException，错误信息为"field"
  } // 构造方法结束，将传入的Field对象赋值给成员变量field

  @Override public String getName() { // 重写PseudoField接口的getName方法，获取字段的名称，@Override注解表示该方法重写了接口或父类的方法
    return field.getName(); // 调用反射Field对象的getName()方法，返回该字段的名称
  } // getName方法结束，返回字段名字符串

  @Override public Type getType() { // 重写PseudoField接口的getType方法，获取字段的类型，返回Type对象
    return field.getType(); // 调用反射Field对象的getType()方法，返回该字段的类型（Type对象）
  } // getType方法结束，返回字段类型

  @Override public int getModifiers() { // 重写PseudoField接口的getModifiers方法，获取字段的修饰符（如public、private、static等）
    return field.getModifiers(); // 调用反射Field对象的getModifiers()方法，返回该字段的修饰符整数表示
  } // getModifiers方法结束，返回修饰符整数

  @Override public @Nullable Object get(@Nullable Object o) throws IllegalAccessException { // 重写PseudoField接口的get方法，获取指定对象上该字段的值，@Nullable表示参数和返回值可能为null，可能抛出IllegalAccessException异常
    return field.get(o); // 调用反射Field对象的get()方法，获取对象o上该字段的值并返回，如果字段是static的，参数o应为null
  } // get方法结束，返回字段值

  @Override public Class<?> getDeclaringClass() { // 重写PseudoField接口的getDeclaringClass方法，获取声明该字段的类
    return field.getDeclaringClass(); // 调用反射Field对象的getDeclaringClass()方法，返回声明该字段的Class对象
  } // getDeclaringClass方法结束，返回声明该字段的类

  @Override public boolean equals(@Nullable Object o) { // 重写Object类的equals方法，用于比较两个ReflectedPseudoField对象是否相等，@Nullable表示参数可能为null
    if (this == o) { // 首先检查当前对象和参数o是否是同一个对象引用（内存地址相同）
      return true; // 如果是同一个对象引用，直接返回true
    } // if判断结束
    if (o == null || getClass() != o.getClass()) { // 检查参数o是否为null，或者o的Class对象与当前对象的Class对象是否不同
      return false; // 如果o为null或类型不同，返回false
    } // if判断结束

    ReflectedPseudoField that = (ReflectedPseudoField) o; // 将参数o强制转换为ReflectedPseudoField类型，赋值给局部变量that

    if (!field.equals(that.field)) { // 比较当前对象的field成员变量与that对象的field成员变量是否相等
      return false; // 如果field不相等，返回false
    } // if判断结束

    return true; // 所有检查都通过，返回true，表示两个对象相等
  } // equals方法结束，返回比较结果

  @Override public int hashCode() { // 重写Object类的hashCode方法，用于计算对象的哈希码，@Override表示该方法重写了Object类的方法
    return field.hashCode(); // 调用field成员变量的hashCode()方法，返回该Field对象的哈希码作为当前对象的哈希码
  } // hashCode方法结束，返回哈希码整数
} // ReflectedPseudoField类定义结束
