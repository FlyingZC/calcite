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
package org.apache.calcite.rel.metadata; // 声明包名，表示这个类属于org.apache.calcite.rel.metadata包，这是Calcite框架中用于处理关系表达式元数据的包

import net.bytebuddy.ByteBuddy; // 导入ByteBuddy库的主类，这是一个用于在运行时动态创建和修改Java类的字节码操作库
import net.bytebuddy.description.modifier.SyntheticState; // 导入SyntheticState枚举，用于定义方法的合成状态（synthetic表示由编译器自动生成的方法）
import net.bytebuddy.description.modifier.Visibility; // 导入Visibility枚举，用于定义方法的可见性（如PUBLIC、PRIVATE等）
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy; // 导入类加载策略，用于定义如何加载动态生成的类
import net.bytebuddy.implementation.FixedValue; // 导入FixedValue类，用于定义方法返回固定值的实现

/**
 * Constructs {@link MetadataHandler} classes useful for tests. // 类的JavaDoc注释：说明这个工具类用于构造测试所需的MetadataHandler类
 * // 这个类的主要作用是：在测试环境中创建带有特殊方法（如合成方法）的MetadataHandler实现类
 * // MetadataHandler是Calcite元数据系统的核心接口，用于处理关系表达式的各种元数据（如行数、唯一性等）
 * // 这个测试工具类使用ByteBuddy字节码操作库在运行时动态生成测试用的MetadataHandler类
 * // 特别用于测试元数据处理器如何处理合成方法（synthetic methods），这是Java编译器自动生成的方法
 */ // 类级别的详细注释：TestMetadataHandlers是一个测试工具类，专门用于在单元测试中创建MetadataHandler接口的动态实现类
class TestMetadataHandlers { // 定义一个工具类，使用默认（包级）访问权限，意味着只能在同一个包内使用
  /**
   * Returns a class representing an interface extending {@link MetadataHandler} and having
   * a synthetic method. // 方法的JavaDoc注释：说明此方法返回一个扩展了MetadataHandler接口并包含合成方法的类
   *
   * @return MetadataHandler class with a synthetic method // 返回值说明：返回一个带有合成方法的MetadataHandler类
   */ // 方法级别的详细注释：这个静态工厂方法使用ByteBuddy字节码操作库动态创建一个测试用的MetadataHandler实现类
  static Class<? extends MetadataHandler<TestMetadata>> handlerClassWithSyntheticMethod() { // 定义静态方法，返回类型是MetadataHandler<TestMetadata>的子类Class对象
    return new ByteBuddy() // 创建ByteBuddy实例，这是字节码操作的起点，用于开始定义或修改类
        .redefine(BlankMetadataHandler.class) // 重新定义BlankMetadataHandler类，BlankMetadataHandler是一个空白的MetadataHandler接口实现
        .defineMethod("syntheticMethod", Void.class, SyntheticState.SYNTHETIC, Visibility.PUBLIC) // 定义一个名为"syntheticMethod"的新方法，返回类型为Void，标记为合成方法（SYNTHETIC），可见性为公共（PUBLIC）
        .intercept(FixedValue.nullValue()) // 为这个方法设置拦截器，使其返回null值，这是ByteBuddy中定义方法实现的简单方式
        .make() // 执行字节码生成，创建动态类的字节码
        .load(TestMetadataHandlers.class.getClassLoader(), ClassLoadingStrategy.Default.CHILD_FIRST) // 加载生成的类，使用当前类的类加载器，采用子类优先（CHILD_FIRST）的加载策略
        .getLoaded(); // 获取已加载的Class对象，这是最终返回的动态生成的类
  } // 方法结束，返回一个带有合成方法的MetadataHandler类

  private TestMetadataHandlers() { // 私有构造方法，防止实例化，这是工具类的标准模式
    // prevent instantiation // 注释说明：防止实例化，确保这个类只能通过静态方法使用，不能被new关键字创建对象
  } // 构造方法结束

  /**
   * A blank {@link MetadataHandler} that is used as a base for adding a synthetic method. // 内部接口的JavaDoc注释：说明这是一个空白的MetadataHandler接口，用作添加合成方法的基础
   */ // 内部接口的详细注释：BlankMetadataHandler是一个私有接口，继承自MetadataHandler<TestMetadata>，它本身不定义任何方法
  private interface BlankMetadataHandler extends MetadataHandler<TestMetadata> { // 定义私有接口，继承MetadataHandler接口并指定泛型类型为TestMetadata
  } // 接口结束，这个接口作为ByteBuddy重新定义的模板类
} // 类结束，TestMetadataHandlers工具类定义完毕
