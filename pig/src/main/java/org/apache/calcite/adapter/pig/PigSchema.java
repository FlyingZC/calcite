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
// Apache Calcite Pig适配器包，包含与Apache Pig数据源集成的相关类
package org.apache.calcite.adapter.pig;

// 导入Calcite的Table接口，表示数据表
import org.apache.calcite.schema.Table;
// 导入AbstractSchema抽象类，作为所有Schema实现的基类
import org.apache.calcite.schema.impl.AbstractSchema;

// 导入HashMap类，用于存储表名到表对象的映射
import java.util.HashMap;
// 导入Map接口，定义键值对集合
import java.util.Map;

/**
 * Schema that contains one more or more Pig tables.
 * // PigSchema类：包含一个或多个Pig表的Schema实现
 * // 继承自AbstractSchema，是Calcite中用于管理Pig数据源表的Schema
 * // Schema是Calcite中的概念，类似于数据库中的schema或命名空间，用于组织和管理表
 * // PigSchema专门用于管理从Apache Pig数据源加载的表，提供表的注册和访问功能
 */
public class PigSchema extends AbstractSchema {

  // tableMap：表名到表对象的映射，用于存储该Schema中包含的所有Pig表
  // 使用HashMap实现，键为表名（String类型），值为表对象（Table类型）
  // protected修饰符允许子类访问，final修饰符表示该引用不可重新赋值
  // 初始化为空的HashMap对象，后续通过registerTable方法动态添加表
  protected final Map<String, Table> tableMap = new HashMap<>();

  // getTableMap方法：重写父类AbstractSchema的getTableMap方法，返回该Schema包含的表映射
  // @Override注解表示该方法重写了父类的方法
  // protected修饰符表示该方法只能在类内部或子类中访问
  // 返回值类型为Map<String, Table>，即表名到表对象的映射
  // 该方法被Calcite框架调用，用于获取Schema中所有可用的表
  // 返回tableMap成员变量，该Map包含了所有已注册的Pig表
  @Override protected Map<String, Table> getTableMap() {
    return tableMap; // 返回存储表的映射集合
  }

  // registerTable方法：向当前Schema中注册一个新的Pig表
  // void返回类型表示该方法不返回任何值
  // 参数name：String类型，表示要注册的表名，作为Map的键
  // 参数table：PigTable类型，表示要注册的表对象，作为Map的值
  // 该方法将表名和表对象的映射关系存入tableMap中
  // 包访问权限（无修饰符），表示只能在同一包内访问
  // 通过tableMap.put方法将表注册到Schema中，使表可以被查询和使用
  void registerTable(String name, PigTable table) {
    tableMap.put(name, table); // 将表名和表对象存入映射集合
  }
}
