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
 */ // Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.pig; // 指定该类所属的包，位于org.apache.calcite.adapter.pig包下，这是Calcite中用于适配Apache Pig数据源的适配器包

import org.apache.calcite.schema.Schema; // 导入Schema接口，Calcite中表示数据库模式的核心接口，定义了表、函数等元数据结构
import org.apache.calcite.schema.SchemaFactory; // 导入SchemaFactory接口，用于创建Schema实例的工厂接口，实现了工厂模式
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，扩展了Schema接口，提供了更多的功能和上下文信息

import java.util.Map; // 导入Map接口，用于存储键值对集合，这里用于接收配置参数

/**
 * Factory that creates a {@link PigSchema}.
 * // 工厂类，用于创建PigSchema实例，实现了SchemaFactory接口
 * // PigSchema是Calcite中用于表示Apache Pig数据源的Schema实现
 * // 通过这个工厂类，可以在Calcite的模型配置文件中动态创建Pig数据源的Schema
 *
 * <p>Allows a custom schema to be included in a <code><i>model</i>.json</code>
 * file.
 * // 允许自定义的Schema被包含在model.json配置文件中
 * // model.json是Calcite的核心配置文件，用于定义数据源、Schema、表等元数据信息
 * // 通过这个工厂类，用户可以在model.json中声明使用Pig数据源，Calcite会自动创建相应的Schema
 */ // 类级别的Javadoc注释，详细说明了PigSchemaFactory的作用和使用场景
public class PigSchemaFactory implements SchemaFactory { // 定义PigSchemaFactory类，实现SchemaFactory接口，遵循工厂设计模式

  /** Public singleton, per factory contract. */
  // 公共单例实例，遵循工厂契约模式
  // SchemaFactory接口通常要求实现类提供一个单例实例，以便在整个应用中共享
  // 使用单例模式可以避免重复创建工厂实例，提高性能并保持一致性
  // INSTANCE是静态final变量，确保全局唯一且不可修改
  public static final PigSchemaFactory INSTANCE = new PigSchemaFactory(); // 创建并初始化PigSchemaFactory的单例实例，通过私有构造函数确保只能通过INSTANCE访问

  private PigSchemaFactory() { // 私有构造函数，防止外部直接实例化，确保只能通过INSTANCE单例访问该类
  } // 私有构造函数体为空，因为该类不需要初始化任何状态，只是作为一个工厂使用

  @Override public Schema create(SchemaPlus parentSchema, String name, // 实现SchemaFactory接口的create方法，用于创建PigSchema实例
      Map<String, Object> operand) { // operand参数包含了从model.json配置文件中传递的配置参数，以Map形式存储
    return new PigSchema(); // 创建并返回一个新的PigSchema实例，PigSchema是表示Pig数据源的具体Schema实现
  } // create方法结束，返回创建的PigSchema对象，该对象将被Calcite用于查询Pig数据源
} // PigSchemaFactory类定义结束，这是一个简单但重要的工厂类，作为Calcite与Pig数据源集成的入口点
