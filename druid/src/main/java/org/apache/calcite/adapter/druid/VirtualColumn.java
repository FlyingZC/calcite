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
package org.apache.calcite.adapter.druid; // 声明包名，表示该类属于 Apache Calcite 的 Druid 适配器包

import com.fasterxml.jackson.core.JsonGenerator; // 导入 Jackson 库的 JsonGenerator 类，用于生成 JSON 格式的数据

import java.io.IOException; // 导入 Java IO 异常类，用于处理输入输出操作中可能出现的异常
import java.util.Locale; // 导入 Java Locale 类，用于处理地区相关的信息，如大小写转换

import static org.apache.calcite.adapter.druid.DruidQuery.writeFieldIf; // 静态导入 DruidQuery 类的 writeFieldIf 方法，用于条件性地写入 JSON 字段

import static java.util.Objects.requireNonNull; // 静态导入 Objects 类的 requireNonNull 方法，用于检查对象是否为 null

/**
 * Druid Json Expression based Virtual Column. // 基于 Druid JSON 表达式的虚拟列
 * Virtual columns is used as "projection" concept throughout Druid using expression. // 虚拟列在 Druid 中作为"投影"概念使用，通过表达式定义
 */
public class VirtualColumn implements DruidJson { // 定义 VirtualColumn 类，实现 DruidJson 接口，表示一个虚拟列
  private final String name; // 定义私有常量成员变量 name，表示虚拟列的名称，用于在查询中引用该列

  private final String expression; // 定义私有常量成员变量 expression，表示虚拟列的表达式，用于定义如何计算该列的值

  private final DruidType outputType; // 定义私有常量成员变量 outputType，表示虚拟列的输出数据类型，DruidType 是 Druid 支持的数据类型枚举

  public VirtualColumn(String name, String expression, DruidType outputType) { // 构造方法，用于创建 VirtualColumn 实例，接收列名、表达式和输出类型三个参数
    this.name = requireNonNull(name, "name"); // 使用 requireNonNull 检查 name 参数是否为 null，如果为 null 则抛出 NullPointerException
    this.expression = requireNonNull(expression, "expression"); // 使用 requireNonNull 检查 expression 参数是否为 null，如果为 null 则抛出 NullPointerException
    this.outputType = outputType == null ? DruidType.FLOAT : outputType; // 如果 outputType 为 null，则默认设置为 FLOAT 类型，否则使用传入的 outputType
  }

  @Override public void write(JsonGenerator generator) throws IOException { // 重写 DruidJson 接口的 write 方法，用于将虚拟列写入 JSON 生成器，可能抛出 IOException
    generator.writeStartObject(); // 开始写入一个 JSON 对象
    generator.writeStringField("type", "expression"); // 写入 "type" 字段，值为 "expression"，表示这是一个表达式类型的虚拟列
    generator.writeStringField("name", name); // 写入 "name" 字段，值为虚拟列的名称
    generator.writeStringField("expression", expression); // 写入 "expression" 字段，值为虚拟列的表达式
    writeFieldIf(generator, "outputType", getOutputType().toString().toUpperCase(Locale.ENGLISH)); // 如果 outputType 不为 null，则写入 "outputType" 字段，值转换为大写的英文字符串
    generator.writeEndObject(); // 结束写入 JSON 对象
  }

  public String getName() { // 定义 getName 方法，用于获取虚拟列的名称
    return name; // 返回虚拟列的名称
  }

  public String getExpression() { // 定义 getExpression 方法，用于获取虚拟列的表达式
    return expression; // 返回虚拟列的表达式
  }

  public DruidType getOutputType() { // 定义 getOutputType 方法，用于获取虚拟列的输出数据类型
    return outputType; // 返回虚拟列的输出数据类型
  }

  /**
   * Virtual Column builder. // 虚拟列的构建器，用于使用建造者模式创建 VirtualColumn 实例
   */
  public static class Builder { // 定义静态内部类 Builder，用于构建 VirtualColumn 对象
    private String name; // 定义私有成员变量 name，用于存储构建过程中的列名

    private String expression; // 定义私有成员变量 expression，用于存储构建过程中的表达式

    private DruidType type; // 定义私有成员变量 type，用于存储构建过程中的输出类型

    public Builder withName(String name) { // 定义 withName 方法，用于设置列名，返回 Builder 实例以支持链式调用
      this.name = name; // 将传入的 name 参数赋值给成员变量 name
      return this; // 返回当前 Builder 实例，支持链式调用
    }

    public Builder withExpression(String expression) { // 定义 withExpression 方法，用于设置表达式，返回 Builder 实例以支持链式调用
      this.expression = expression; // 将传入的 expression 参数赋值给成员变量 expression
      return this; // 返回当前 Builder 实例，支持链式调用
    }

    public Builder withType(DruidType type) { // 定义 withType 方法，用于设置输出类型，返回 Builder 实例以支持链式调用
      this.type = type; // 将传入的 type 参数赋值给成员变量 type
      return this; // 返回当前 Builder 实例，支持链式调用
    }

    public VirtualColumn build() { // 定义 build 方法，用于构建并返回 VirtualColumn 实例
      return new VirtualColumn(name, expression, type); // 使用当前 Builder 中设置的 name、expression 和 type 创建新的 VirtualColumn 实例并返回
    }
  }

  public static Builder builder() { // 定义静态方法 builder，用于创建新的 Builder 实例
    return new Builder(); // 创建并返回一个新的 Builder 实例
  }
}
