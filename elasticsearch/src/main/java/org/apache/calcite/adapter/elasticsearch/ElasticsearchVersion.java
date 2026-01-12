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
package org.apache.calcite.adapter.elasticsearch; // 声明该类属于org.apache.calcite.adapter.elasticsearch包，这是Calcite框架中Elasticsearch适配器的包路径

import java.util.Locale; // 导入Locale类，用于本地化相关的字符串格式化操作

import static java.lang.Integer.parseInt; // 静态导入Integer.parseInt方法，用于将字符串转换为整数
import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于参数非空校验

/**
 * Identifies current ES version at runtime. Some queries have different syntax
 * depending on version (eg. 2 vs 5).
 * 在运行时识别当前Elasticsearch的版本。某些查询在不同的版本中有不同的语法
 * （例如，版本2与版本5之间的语法差异）。这个枚举类是Calcite适配器中用于
 * 处理Elasticsearch不同版本兼容性的核心组件，它允许Calcite根据ES的不同版本
 * 生成正确的查询语法，确保与不同版本的Elasticsearch集群能够正常交互。
 */
enum ElasticsearchVersion { // 定义一个枚举类型，用于表示Elasticsearch的不同版本

  ES2(2), // 枚举常量：代表Elasticsearch 2.x版本，传入主版本号2
  ES5(5), // 枚举常量：代表Elasticsearch 5.x版本，传入主版本号5
  ES6(6), // 枚举常量：代表Elasticsearch 6.x版本，传入主版本号6
  ES7(7), // 枚举常量：代表Elasticsearch 7.x版本，传入主版本号7
  UNKNOWN(0); // 枚举常量：代表未知的Elasticsearch版本，传入主版本号0

  private final int elasticVersionMajor; // 成员变量：存储Elasticsearch的主版本号，使用final修饰表示该值在初始化后不可更改

  ElasticsearchVersion(final int elasticVersionMajor) { // 构造方法：私有构造方法，用于初始化枚举常量，接收主版本号参数
    this.elasticVersionMajor = elasticVersionMajor; // 将传入的主版本号参数赋值给成员变量elasticVersionMajor
  }

  public int elasticVersionMajor() { // 公共方法：获取当前枚举常量对应的主版本号
    return elasticVersionMajor; // 返回存储的主版本号
  }

  static ElasticsearchVersion fromString(String version) { // 静态工厂方法：根据版本字符串解析并返回对应的ElasticsearchVersion枚举常量
    requireNonNull(version, "version"); // 参数校验：确保version参数不为null，如果为null则抛出NullPointerException
    if (!version.matches("\\d+\\.\\d+\\.\\d+")) { // 正则表达式校验：检查版本字符串是否符合格式要求（数字.数字.数字，如7.10.2）
      final String message = String.format(Locale.ROOT, "Wrong version format. " // 如果格式不正确，构建错误消息
          + "Expected ${digit}.${digit}.${digit} but got %s", version); // 使用Locale.ROOT确保格式化的一致性，不受本地化影响
      throw new IllegalArgumentException(message); // 抛出IllegalArgumentException异常，提示版本格式错误
    }

    // version format is: major.minor.revision
    final int major = parseInt(version.substring(0, version.indexOf("."))); // 从版本字符串中提取主版本号：截取第一个点号之前的数字部分并转换为整数
    if (major == 2) { // 如果主版本号是2
      return ES2; // 返回ES2枚举常量
    } else if (major == 5) { // 如果主版本号是5
      return ES5; // 返回ES5枚举常量
    } else if (major == 6) { // 如果主版本号是6
      return ES6; // 返回ES6枚举常量
    } else if (major == 7) { // 如果主版本号是7
      return ES7; // 返回ES7枚举常量
    } else { // 如果主版本号不是2、5、6、7中的任何一个
      return UNKNOWN; // 返回UNKNOWN枚举常量，表示未知版本
    }
  }
}
