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
package org.apache.calcite.adapter.innodb; // 定义包名,属于org.apache.calcite.adapter.innodb包,用于InnoDB适配器相关功能

/**
 * Query type of a push down condition in InnoDB data source. // 类作用:定义InnoDB数据源中下推条件的查询类型枚举,用于标识不同类型的查询操作
 */ // 该枚举用于区分不同的查询访问模式,帮助优化器选择最优的查询执行策略
public enum QueryType { // 定义一个枚举类,名为QueryType,表示查询类型
  /** Primary key point query. */ // 枚举常量注释:主键点查询,表示通过主键精确查询单条记录
  PK_POINT_QUERY(0), // 主键点查询类型,优先级为0(最高优先级),表示通过主键的等值条件查询
  /** Secondary key point query. */ // 枚举常量注释:二级索引(辅助索引)点查询,表示通过二级索引精确查询单条记录
  SK_POINT_QUERY(1), // 二级索引点查询类型,优先级为1,表示通过二级索引的等值条件查询
  /** Primary key range query with lower and upper bound. */ // 枚举常量注释:主键范围查询,表示通过主键查询一个范围内的记录,有上下界
  PK_RANGE_QUERY(2), // 主键范围查询类型,优先级为2,表示通过主键的范围条件(BETWEEN、>、<等)查询
  /** Secondary key range query with lower and upper bound. */ // 枚举常量注释:二级索引范围查询,表示通过二级索引查询一个范围内的记录,有上下界
  SK_RANGE_QUERY(3), // 二级索引范围查询类型,优先级为3,表示通过二级索引的范围条件查询
  /** Scanning table fully with primary key. */ // 枚举常量注释:主键全表扫描,表示通过主键顺序扫描整个表
  PK_FULL_SCAN(4), // 主键全表扫描类型,优先级为4,表示没有索引可用,需要通过主键顺序扫描全表
  /** Scanning table fully with secondary key. */ // 枚举常量注释:二级索引全表扫描,表示通过二级索引顺序扫描整个表
  SK_FULL_SCAN(5); // 二级索引全表扫描类型,优先级为5(最低优先级),表示通过二级索引顺序扫描全表

  private final int priority; // 成员变量:优先级字段,final修饰表示不可变,用于比较不同查询类型的优劣,数值越小优先级越高

  static QueryType getPointQuery(boolean isSk) { // 静态方法:根据是否使用二级索引返回对应的点查询类型,isSk为true返回二级索引点查询,否则返回主键点查询
    return isSk ? SK_POINT_QUERY : PK_POINT_QUERY; // 三元表达式判断,如果isSk为true则返回SK_POINT_QUERY,否则返回PK_POINT_QUERY
  }

  static QueryType getRangeQuery(boolean isSk) { // 静态方法:根据是否使用二级索引返回对应的范围查询类型,isSk为true返回二级索引范围查询,否则返回主键范围查询
    return isSk ? SK_RANGE_QUERY : PK_RANGE_QUERY; // 三元表达式判断,如果isSk为true则返回SK_RANGE_QUERY,否则返回PK_RANGE_QUERY
  }

  QueryType(int priority) { // 构造方法:枚举类型的构造方法,接收一个int类型的优先级参数
    this.priority = priority; // 将传入的priority参数赋值给成员变量priority,初始化该枚举常量的优先级
  }

  int priority() { // 成员方法:获取当前查询类型的优先级值,返回int类型的优先级
    return priority; // 返回成员变量priority的值,用于比较不同查询类型的优先级
  }
} // 枚举类结束
