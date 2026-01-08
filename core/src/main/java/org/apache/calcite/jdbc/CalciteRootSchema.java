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
// Apache 许可证头,声明该文件的版权和使用条款
package org.apache.calcite.jdbc; // 声明该类所属的包:org.apache.calcite.jdbc,包含 JDBC 相关的类

import org.apache.calcite.schema.Schema; // 导入 Schema 接口,表示数据库模式(数据库的逻辑容器)

/**
 * Root schema.
 */
// 类文档注释:CalciteRootSchema 是 Calcite 中的根 Schema 类
// 根 Schema 是整个 Schema 层次结构的顶层节点,所有其他 Schema 都是其子 Schema
// 根 Schema 通常包含多个子 Schema,每个子 Schema 代表一个数据库或命名空间
// 这个类继承自 CachingCalciteSchema,因此具有缓存功能,可以提高性能
@Deprecated // to be removed before 2.0 // 标记为已弃用,计划在 2.0 版本之前移除
public class CalciteRootSchema extends CachingCalciteSchema { // 定义 CalciteRootSchema 类,继承自 CachingCalciteSchema 基类
  /** Creates a root schema. */
  // 方法文档注释:创建一个根 Schema 的构造方法
  // 根 Schema 是 Schema 层次结构的根节点,没有父 Schema
  // 根 Schema 的名称通常为空字符串 ""
  CalciteRootSchema(Schema schema) { // 构造方法定义,接收一个 Schema 参数作为底层 Schema
    super(null, schema, ""); // 调用父类 CachingCalciteSchema 的构造方法,传入 null 作为父 Schema(因为根 Schema 没有父 Schema),传入 schema 作为底层 Schema,传入 "" 作为名称
  } // 构造方法结束
} // CalciteRootSchema 类结束
