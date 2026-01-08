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
package org.apache.calcite.jdbc; // 声明包名，该类位于org.apache.calcite.jdbc包下，是Calcite JDBC层的核心组件之一

import org.apache.calcite.avatica.MetaImpl.MetaTable; // 导入Avatica框架的MetaTable接口，Avatica是Calcite的JDBC驱动基础框架，MetaTable表示元数据表的抽象
import org.apache.calcite.jdbc.CalciteMetaImpl.CalciteMetaTable; // 导入Calcite特定的元数据表实现类，CalciteMetaTable是Calcite对MetaTable的具体实现
import org.apache.calcite.schema.Table; // 导入Calcite的Table接口，代表Calcite中的表结构定义

/** Default implementation of CalciteMetaTableFactory. */ // 类的JavaDoc注释：这是CalciteMetaTableFactory接口的默认实现类
public class CalciteMetaTableFactoryImpl // 定义类CalciteMetaTableFactoryImpl，实现了CalciteMetaTableFactory接口
    implements CalciteMetaTableFactory { // 实现CalciteMetaTableFactory接口，该接口定义了创建元数据表的工厂方法

  /** Singleton instance. */ // 成员变量的JavaDoc注释：这是该工厂类的单例实例，确保整个应用中只有一个工厂实例
  public static final CalciteMetaTableFactoryImpl INSTANCE = // 声明一个公共静态常量INSTANCE，类型为CalciteMetaTableFactoryImpl，使用单例模式
      new CalciteMetaTableFactoryImpl(); // 直接创建该类的实例并赋值给INSTANCE，采用饿汉式单例模式，在类加载时就完成实例化

  /** Internal constructor; protected to allow subclassing. */ // 构造方法的JavaDoc注释：内部构造方法，使用protected修饰以允许子类继承和扩展
  protected CalciteMetaTableFactoryImpl() {} // 定义protected构造方法，空实现，防止外部直接实例化，但允许子类继承

  @Override public CalciteMetaTable createTable( // 重写接口方法，创建CalciteMetaTable实例，返回CalciteMetaTable类型对象
      Table table, // 参数1：table，类型为Table，表示Calcite中的表对象，包含表的元数据信息（如字段、类型等）
      String tableCat, // 参数2：tableCat，类型为String，表示表的catalog（目录）名称，用于标识表所属的逻辑目录
      String tableSchem, // 参数3：tableSchem，类型为String，表示表的schema（模式）名称，用于标识表所属的逻辑模式
      String tableName) { // 参数4：tableName，类型为String，表示表的名称，用于唯一标识一个表
    return new CalciteMetaTable(table, tableCat, tableSchem, tableName); // 创建并返回CalciteMetaTable实例，将传入的表信息和catalog、schema、表名封装到CalciteMetaTable对象中
  } // 方法结束，该方法实现了工厂模式，负责根据输入参数创建对应的元数据表对象

  @Override public Class<? extends MetaTable> getMetaTableClass() { // 重写接口方法，获取该工厂创建的元数据表类的Class对象，返回类型为Class<? extends MetaTable>
    return CalciteMetaTable.class; // 返回CalciteMetaTable类的Class对象，表明该工厂创建的是CalciteMetaTable类型的实例
  } // 方法结束，该方法用于反射或其他需要知道具体实现类类型的场景
} // 类定义结束，CalciteMetaTableFactoryImpl是一个简单的工厂类，采用单例模式，负责创建CalciteMetaTable实例
