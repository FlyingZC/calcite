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
// 声明当前类所在的包，属于Calcite的InnoDB适配器模块
package org.apache.calcite.adapter.innodb;

// 导入Calcite核心Schema接口，表示数据库模式（schema）
import org.apache.calcite.schema.Schema;
// 导入SchemaFactory接口，用于创建Schema实例的工厂接口
import org.apache.calcite.schema.SchemaFactory;
// 导入SchemaPlus接口，表示可以嵌套包含子Schema的Schema
import org.apache.calcite.schema.SchemaPlus;

// 导入Apache Commons Lang3工具类，用于字符串操作
import org.apache.commons.lang3.StringUtils;

// 导入Java集合框架中的List接口，用于存储SQL文件路径列表
import java.util.List;
// 导入Java集合框架中的Map接口，用于存储操作参数键值对
import java.util.Map;

/**
 * Factory that creates a {@link InnodbSchema}. // 工厂类，用于创建InnoDB模式的Schema实例
 * // 这个类实现了SchemaFactory接口，是Calcite适配器模式的核心组件之一
 * // 它的作用是根据配置参数创建InnodbSchema对象，使Calcite能够直接读取MySQL InnoDB的.ibd数据文件
 * // 通过这个工厂，Calcite可以将InnoDB表作为逻辑表进行查询，无需连接MySQL服务器
 * // 主要功能包括：解析SQL DDL文件、读取.ibd数据文件、构建表结构元数据
 */
// 定义InnodbSchemaFactory类，实现SchemaFactory接口，表明这是一个Schema工厂类
public class InnodbSchemaFactory implements SchemaFactory {
  // 无参构造方法，用于创建InnodbSchemaFactory实例
  // 这个构造方法是必须的，Calcite通过反射机制调用无参构造方法来实例化工厂类
  public InnodbSchemaFactory() {
    // 空构造方法体，不需要执行任何初始化操作
  }

  // 实现SchemaFactory接口的create方法，用于创建InnodbSchema实例
  // parentSchema: 父Schema对象，用于构建Schema层级结构，当前Schema可以作为其子Schema
  // name: 当前Schema的名称，用于在Calcite中标识这个Schema
  // operand: 包含创建Schema所需参数的Map，从模型配置文件中读取
  @Override public Schema create(SchemaPlus parentSchema, String name,
      Map<String, Object> operand) {
    // 从operand参数中获取sqlFilePath键对应的值，强制转换为List<String>类型
    // sqlFilePathList包含SQL DDL文件的路径列表，这些文件定义了表结构
    // 例如：["/path/to/schema.sql", "/path/to/tables.sql"]
    final List<String> sqlFilePathList = (List<String>) operand.get("sqlFilePath");
    // 从operand参数中获取ibdDataFileBasePath键对应的值，强制转换为String类型
    // ibdDataFileBasePath是InnoDB表空间数据文件(.ibd)的基础路径
    // 例如："/var/lib/mysql/mydatabase/"
    // Calcite会从这个路径读取实际的表数据文件
    final String ibdDataFileBasePath = (String) operand.get("ibdDataFileBasePath");
    // 从operand参数中获取timeZone键对应的值，强制转换为String类型
    // timeZone表示服务器时区，用于正确处理时间戳和日期时间类型的数据
    // 例如："Asia/Shanghai", "UTC", "America/New_York"
    final String timeZone = (String) operand.get("timeZone");
    // 检查timeZone参数是否不为空且不为空白字符串
    // 如果配置了时区，则需要设置系统属性以保证时间处理的正确性
    if (StringUtils.isNotEmpty(timeZone)) {
      // 设置系统属性"innodb.java.reader.server.timezone"为指定的时区值
      // 这个属性会被InnoDB读取器使用，确保时间戳数据按照正确的时区进行解析
      // 例如：System.setProperty("innodb.java.reader.server.timezone", "Asia/Shanghai");
      System.setProperty("innodb.java.reader.server.timezone", timeZone);
    }

    // 创建并返回InnodbSchema实例
    // 传入sqlFilePathList用于解析表结构定义
    // 传入ibdDataFileBasePath用于定位和读取实际的表数据文件
    // 返回的Schema对象会被Calcite注册到查询解析器中，用于后续的SQL查询执行
    return new InnodbSchema(sqlFilePathList, ibdDataFileBasePath);
  }
}
