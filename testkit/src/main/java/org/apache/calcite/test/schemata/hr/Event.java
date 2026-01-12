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
package org.apache.calcite.test.schemata.hr; // Event类所在的包路径，位于Calcite测试框架的HR（Human Resources，人力资源）模式测试包中

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker Framework的注解，用于标记可能为null的字段，帮助进行静态空值检查

import java.sql.Timestamp; // 导入Java SQL的Timestamp类，用于表示时间戳数据
import java.util.Objects; // 导入Java工具类Objects，用于生成哈希码和比较对象

/**
 * Event. // Event类：表示HR模式中的一个事件实体，用于在Calcite测试环境中模拟事件数据表
 */ // 该类是一个简单的POJO（Plain Old Java Object），用于封装事件的基本信息，包括事件ID和时间戳
public class Event { // 声明Event类，作为HR（人力资源）测试模式中的事件数据模型
  public final int eventid; // 事件ID：唯一标识一个事件的整数，使用public final修饰表示该字段是公开的且不可变的
  public final @Nullable Timestamp ts; // 时间戳：事件发生的时间，类型为java.sql.Timestamp，使用@Nullable注解表示该字段可能为null，使用public final修饰表示该字段是公开的且不可变的

  public Event(int eventid, @Nullable Timestamp ts) { // Event类的构造方法：用于创建Event对象实例，接收事件ID和时间戳作为参数
    this.eventid = eventid; // 将传入的eventid参数赋值给实例变量eventid，完成事件ID的初始化
    this.ts = ts; // 将传入的ts参数赋值给实例变量ts，完成时间戳的初始化
  } // 构造方法结束，Event对象创建完成

  @Override public String toString() { // 重写Object类的toString方法：用于返回Event对象的字符串表示形式，便于调试和日志输出
    return "Event [eventid: " + eventid + ", ts: " + ts + "]"; // 返回格式化的字符串，包含Event类名、eventid字段值和ts字段值
  } // toString方法结束

  @Override public boolean equals(Object obj) { // 重写Object类的equals方法：用于比较两个Event对象是否相等，基于eventid字段进行判断
    return obj == this // 首先检查obj是否就是当前对象this，如果是则直接返回true（对象引用相等）
        || obj instanceof Event // 如果不是同一个引用，则检查obj是否是Event类的实例
        && eventid == ((Event) obj).eventid; // 如果是Event实例，则比较两个对象的eventid字段是否相等，相等则返回true
  } // equals方法结束

  @Override public int hashCode() { // 重写Object类的hashCode方法：用于生成Event对象的哈希码，基于eventid字段计算
    return Objects.hash(eventid); // 使用Objects工具类的hash方法，基于eventid字段生成哈希码，确保与equals方法保持一致
  } // hashCode方法结束
} // Event类定义结束
