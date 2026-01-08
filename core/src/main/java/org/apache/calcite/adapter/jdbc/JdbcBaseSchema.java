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
// Apache许可证声明，说明代码版权和使用许可
package org.apache.calcite.adapter.jdbc; // 声明包名，该类位于JDBC适配器包下，用于处理JDBC数据源的Schema

import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类，用于表示LINQ表达式树中的节点，常用于代码生成和表达式构建
import org.apache.calcite.rel.type.RelProtoDataType; // 导入RelProtoDataType接口，用于表示关系数据类型的原型，可以延迟创建实际的RelDataType
import org.apache.calcite.schema.Function; // 导入Function接口，表示Calcite中的函数定义
import org.apache.calcite.schema.Schema; // 导入Schema接口，这是Calcite中Schema的核心接口，定义了Schema的基本行为
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，扩展了Schema接口，提供了额外的功能如父子Schema关系
import org.apache.calcite.schema.SchemaVersion; // 导入SchemaVersion接口，用于表示Schema的版本信息，支持Schema快照功能
import org.apache.calcite.schema.Schemas; // 导入Schemas工具类，提供了Schema相关的静态工具方法
import org.apache.calcite.schema.Table; // 导入Table接口，表示Calcite中的表定义
import org.apache.calcite.schema.lookup.LikePattern; // 导入LikePattern类，用于支持LIKE模式的匹配，常用于模糊查询表名等
import org.apache.calcite.schema.lookup.Lookup; // 导入Lookup接口，提供了基于名称查找对象的功能，支持模式匹配

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值，帮助进行空值检查

import java.util.Collection; // 导入Collection接口，表示集合类型
import java.util.Collections; // 导入Collections工具类，提供了操作集合的静态方法
import java.util.Set; // 导入Set接口，表示不包含重复元素的集合

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Base class for JDBC schemas.
 * JDBC Schema的基类，提供了JDBC数据源Schema的通用实现
 * 
 * 这个类是所有JDBC适配器Schema的抽象基类，定义了JDBC Schema的基本行为和接口
 * JDBC Schema用于将外部JDBC数据源（如MySQL、PostgreSQL等）映射到Calcite的查询引擎中
 * 
 * 主要功能：
 * 1. 提供表和子Schema的查找机制
 * 2. 支持Schema的不可变性和快照功能
 * 3. 提供表达式生成能力，用于代码生成
 * 4. 默认不支持类型和函数，返回空集合
 * 
 * 设计模式：模板方法模式，定义了Schema的骨架，具体实现由子类完成
 * 
 * 子类需要实现的关键方法：
 * - tables(): 返回表的查找器
 * - subSchemas(): 返回子Schema的查找器
 */
public abstract class JdbcBaseSchema implements Schema { // 声明JdbcBaseSchema为抽象类，实现Schema接口

  @Override public abstract Lookup<Table> tables(); // 抽象方法，要求子类实现，返回表的查找器（Lookup<Table>），用于通过名称查找表，支持模式匹配
// 该方法是核心方法，定义了如何获取Schema中的所有表，Lookup接口提供了灵活的查找和名称匹配能力


  @Deprecated @Override public @Nullable Table getTable(String name) { // 已废弃的方法，通过表名获取表对象，使用tables().get(name)替代
    return tables().get(name); // 调用tables()方法获取Lookup，然后通过get(name)查找指定名称的表，如果不存在返回null
  } // 该方法已被废弃，建议使用tables()方法提供的Lookup接口进行更灵活的表查找

  @Deprecated @Override public Set<String> getTableNames() { // 已废弃的方法，获取所有表名的集合，使用tables().getNames(LikePattern.any())替代
    return tables().getNames(LikePattern.any()); // 调用tables()获取Lookup，然后使用getNames方法配合LikePattern.any()获取所有匹配的表名（即所有表名）
  } // 该方法已被废弃，建议使用tables()方法提供的Lookup接口进行更灵活的表名获取

  @Override public abstract Lookup<? extends Schema> subSchemas(); // 抽象方法，要求子类实现，返回子Schema的查找器（Lookup<? extends Schema>），用于通过名称查找子Schema
// 该方法定义了如何获取Schema中的所有子Schema，支持嵌套Schema结构，Lookup接口提供了灵活的查找和名称匹配能力

  @Deprecated @Override public @Nullable Schema getSubSchema(String name) { // 已废弃的方法，通过名称获取子Schema对象，使用subSchemas().get(name)替代
    return subSchemas().get(name); // 调用subSchemas()方法获取Lookup，然后通过get(name)查找指定名称的子Schema，如果不存在返回null
  } // 该方法已被废弃，建议使用subSchemas()方法提供的Lookup接口进行更灵活的子Schema查找

  @Deprecated @Override public Set<String> getSubSchemaNames() { // 已废弃的方法，获取所有子Schema名称的集合，使用subSchemas().getNames(LikePattern.any())替代
    return subSchemas().getNames(LikePattern.any()); // 调用subSchemas()获取Lookup，然后使用getNames方法配合LikePattern.any()获取所有匹配的子Schema名称（即所有子Schema名）
  } // 该方法已被废弃，建议使用subSchemas()方法提供的Lookup接口进行更灵活的子Schema名称获取


  @Override public @Nullable RelProtoDataType getType(String name) { // 根据名称获取关系数据类型原型，JDBC Schema默认不支持自定义类型
    return null; // 返回null，表示JDBC Schema不支持自定义类型，所有类型都由表的列定义决定
  } // JDBC Schema中的类型信息通常直接从JDBC数据库的元数据中获取，不需要单独定义类型

  @Override public Set<String> getTypeNames() { // 获取所有自定义类型的名称集合
    return Collections.emptySet(); // 返回空集合，表示JDBC Schema没有自定义类型
  } // JDBC Schema不需要维护额外的类型定义，所有类型都通过表的列信息获取

  @Override public final Collection<Function> getFunctions(String name) { // 根据名称获取函数集合，final方法表示子类不能重写
    return Collections.emptyList(); // 返回空集合，表示JDBC Schema默认不支持函数
  } // JDBC Schema中的SQL函数通常由底层数据库提供，Calcite通过函数映射机制处理，不需要在Schema中定义

  @Override public final Set<String> getFunctionNames() { // 获取所有函数名称的集合，final方法表示子类不能重写
    return Collections.emptySet(); // 返回空集合，表示JDBC Schema没有定义函数
  } // JDBC Schema不需要维护函数定义，函数处理由Calcite的函数注册和映射机制完成

  @Override public Expression getExpression(final @Nullable SchemaPlus parentSchema, // 获取该Schema的表达式表示，用于代码生成和表达式构建
      final String name) { // 参数name：Schema的名称
    requireNonNull(parentSchema, "parentSchema"); // 校验parentSchema参数不能为null，如果为null抛出NullPointerException
    return Schemas.subSchemaExpression(parentSchema, name, getClass()); // 调用Schemas工具类的subSchemaExpression方法，生成访问该子Schema的表达式
  } // 该方法用于生成代码，通过表达式可以动态访问Schema，参数包括父Schema、当前Schema名称和Schema的类类型

  @Override public boolean isMutable() { // 判断Schema是否可变，即Schema的结构是否可以动态修改
    return false; // 返回false，表示JDBC Schema是不可变的，一旦创建其结构（表、子Schema等）不会改变
  } // 不可变的Schema更安全，可以支持并发访问，也便于实现缓存和优化

  @Override public Schema snapshot(final SchemaVersion version) { // 创建Schema的快照，用于 SchemaVersion 参数指定快照的版本
    return this; // 返回this，因为JdbcBaseSchema是不可变的，所以快照就是自身，不需要创建副本
  } // 不可变对象的快照就是自身，这提高了性能，避免了不必要的对象复制
} // 类定义结束
