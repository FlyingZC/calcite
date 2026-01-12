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
// Apache许可证声明，指定代码的使用权限和限制条件
package org.apache.calcite.adapter.geode.rel; // 定义包路径，该类属于org.apache.calcite.adapter.geode.rel包，专门用于Geode适配器的测试断言

import java.util.List; // 导入List接口，用于处理列表类型的数据集合
import java.util.function.Consumer; // 导入Consumer函数式接口，用于定义接收单个参数且无返回值的操作

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器，用于断言两个值相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的assertThat方法，用于执行断言验证

/**
 * Various validations for geode tests.
 */
// 这是一个用于Geode测试的各种验证工具类
// 该类提供了静态方法来创建断言逻辑，用于验证Geode适配器生成的查询语句是否符合预期
// 该类是包级私有的（没有public修饰符），只能在同一包内被访问
// 该类不提供实例化功能（私有构造函数），所有功能通过静态方法提供
class GeodeAssertions { // 定义GeodeAssertions类，用于Geode适配器的测试断言验证

  private GeodeAssertions() {} // 私有构造函数，防止该类被实例化，确保这是一个纯工具类

  static Consumer<List> query(final String query) { // 定义静态方法query，返回一个Consumer<List>类型的函数对象，用于验证生成的查询字符串是否与预期一致；参数query是预期的查询字符串，使用final修饰确保不可修改
    return actual -> { // 返回一个Lambda表达式实现的Consumer对象，该Consumer接收一个List类型的参数actual（实际生成的查询结果列表）
      String actualString = // 声明变量actualString，用于存储从actual列表中提取的实际查询字符串
          actual == null || actual.isEmpty() // 判断actual列表是否为null或为空
              ? null // 如果actual为null或为空，则actualString赋值为null
              : ((String) actual.get(0)); // 否则，从actual列表的第一个位置获取元素并转换为String类型赋值给actualString

      assertThat(actualString, is(query)); // 使用Hamcrest的assertThat方法断言actualString是否等于预期的query值，如果不相等则测试失败
    }; // Lambda表达式结束，Consumer对象定义完成
  } // query方法结束，返回一个可以验证查询字符串的Consumer对象

} // GeodeAssertions类定义结束
