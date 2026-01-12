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
package org.apache.calcite.test.schemata.hr; // 声明当前类所在的包路径，属于org.apache.calcite.test.schemata.hr包，这是Calcite测试框架中的人力资源(human resources)测试schema包

import java.util.Objects; // 导入java.util.Objects类，该类提供了一些对象工具方法，用于计算哈希码、比较对象等，这里主要用于计算Location对象的哈希码

/**
 * Location model. // Location模型类，表示一个二维空间中的位置点，包含x和y两个坐标分量，用于Calcite测试框架中模拟地理位置数据
 */
public class  Location { // 定义Location类，这是一个不可变(immutable)的值对象类，用于表示二维坐标系中的位置点，作为Calcite测试schema中hr(人力资源)模块的Location实体模型
  public final int x; // 定义x坐标成员变量，使用public final修饰表示该字段是公开的且不可变的，代表二维空间中位置的横坐标值，一旦构造后就不能被修改
  public final int y; // 定义y坐标成员变量，使用public final修饰表示该字段是公开的且不可变的，代表二维空间中位置的纵坐标值，一旦构造后就不能被修改

  public Location(int x, int y) { // 定义Location类的构造方法，接收两个int类型参数x和y，用于创建一个新的Location实例并初始化其坐标值
    this.x = x; // 将构造方法参数x的值赋给当前对象的成员变量x，初始化位置的横坐标
    this.y = y; // 将构造方法参数y的值赋给当前对象的成员变量y，初始化位置的纵坐标
  }

  @Override public String toString() { // 重写Object类的toString方法，用于返回Location对象的字符串表示，该方法会在打印对象或字符串拼接时自动调用
    return "Location [x: " + x + ", y: " + y + "]"; // 返回格式化的字符串，包含Location类名以及x和y坐标的具体值，格式为"Location [x: 10, y: 20]"
  }

  @Override public boolean equals(Object obj) { // 重写Object类的equals方法，用于比较两个Location对象是否相等，该方法基于x和y坐标值进行相等性判断
    return obj == this // 首先检查obj引用是否就是当前对象(this)，如果是则直接返回true，这是快速的相等性检查，避免后续不必要的比较
        || obj instanceof Location // 如果obj不是当前对象，则检查obj是否是Location类的实例，如果不是则返回false
        && x == ((Location) obj).x // 如果obj是Location实例，则比较当前对象的x坐标与obj对象的x坐标是否相等，使用==比较基本类型int值
        && y == ((Location) obj).y; // 最后比较当前对象的y坐标与obj对象的y坐标是否相等，只有当x和y坐标都相等时才返回true
  }

  @Override public int hashCode() { // 重写Object类的hashCode方法，用于计算Location对象的哈希码，该方法与equals方法保持一致性，相等的对象必须具有相等的哈希码
    return Objects.hash(x, y); // 调用Objects.hash工具方法，基于x和y坐标值计算哈希码，该方法内部使用可变参数和适当的哈希算法确保分布均匀
  }
}
