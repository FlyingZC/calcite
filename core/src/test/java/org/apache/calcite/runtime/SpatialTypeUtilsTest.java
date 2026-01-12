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
// 声明包名，表示这个类属于org.apache.calcite.runtime包，该包包含Calcite运行时工具类
package org.apache.calcite.runtime;

// 导入JUnit 5的Test注解，用于标记测试方法
import org.junit.jupiter.api.Test;
// 导入JTS库的Coordinate类，用于表示空间坐标点(x,y)
import org.locationtech.jts.geom.Coordinate;
// 导入JTS库的Geometry接口，是所有空间几何类型的基类
import org.locationtech.jts.geom.Geometry;
// 导入JTS库的GeometryFactory类，用于创建各种几何对象
import org.locationtech.jts.geom.GeometryFactory;

// 导入Hamcrest断言库的is匹配器，用于验证值是否相等
import static org.hamcrest.CoreMatchers.is;
// 导入Hamcrest的assertThat静态方法，用于编写断言
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link org.apache.calcite.runtime.SpatialTypeUtilsTest}.
 * 该类是SpatialTypeUtils工具类的单元测试类，用于测试空间类型工具类的核心功能
 * 主要测试内容包括：
 * 1. testFromEwkt(): 测试从扩展WKT(EWKT)格式字符串解析几何对象的功能
 * 2. testAsEwkt(): 测试将几何对象转换为扩展WKT(EWKT)格式字符串的功能
 * EWKT(Extended Well-Known Text)是WKT的扩展格式，支持空间参考系统标识符(SRID)
 */
class SpatialTypeUtilsTest { // 定义测试类SpatialTypeUtilsTest，用于测试SpatialTypeUtils工具类

  @Test void testFromEwkt() { // 测试方法：测试从EWKT字符串解析几何对象的功能，使用@Test注解标记为Junit测试方法
    Geometry g1 = SpatialTypeUtils.fromEwkt("POINT(1 2)"); // 调用SpatialTypeUtils.fromEwkt方法，将标准WKT格式的点字符串"POINT(1 2)"解析为Geometry对象，存储在变量g1中
    assertThat(g1.getCoordinate().getX(), is(1D)); // 断言验证：获取g1几何对象的坐标X值，验证其等于1.0，确保解析的X坐标正确
    assertThat(g1.getCoordinate().getY(), is(2D)); // 断言验证：获取g1几何对象的坐标Y值，验证其等于2.0，确保解析的Y坐标正确

    Geometry g2 = SpatialTypeUtils.fromEwkt("srid:1234;POINT(1 2)"); // 调用fromEwkt方法，将带SRID的EWKT格式字符串"srid:1234;POINT(1 2)"解析为Geometry对象，存储在变量g2中
    assertThat(g2.getSRID(), is(1234)); // 断言验证：获取g2几何对象的空间参考系统标识符(SRID)，验证其等于1234，确保SRID正确解析
    assertThat(g2.getCoordinate().getX(), is(1D)); // 断言验证：获取g2几何对象的坐标X值，验证其等于1.0，确保带SRID的EWKT解析后坐标正确
    assertThat(g2.getCoordinate().getY(), is(2D)); // 断言验证：获取g2几何对象的坐标Y值，验证其等于2.0，确保带SRID的EWKT解析后坐标正确

    Geometry g3 = SpatialTypeUtils.fromEwkt("GEOMETRYCOLLECTION(\n" // 调用fromEwkt方法，解析一个复杂的几何集合字符串，该集合包含多个几何对象：两个多边形、一个点和一条线
        + "  POLYGON((0 0, 3 -1, 1.5 2, 0 0)),\n" // 字符串拼接：添加第一个多边形POLYGON，坐标点为(0,0)、(3,-1)、(1.5,2)、(0,0)，形成一个三角形
        + "  POLYGON((2 0, 3 3, 4 2, 2 0)),\n" // 字符串拼接：添加第二个多边形POLYGON，坐标点为(2,0)、(3,3)、(4,2)、(2,0)，形成一个四边形
        + "  POINT(5 6),\n" // 字符串拼接：添加一个点POINT，坐标为(5,6)
        + "  LINESTRING(1 1, 1 6))"); // 字符串拼接：添加一条线LINESTRING，从点(1,1)到点(1,6)的垂直线段，闭合字符串
    assertThat(g3.getSRID(), is(0)); // 断言验证：获取g3几何对象的空间参考系统标识符(SRID)，验证其等于0（默认值），确保未指定SRID时正确返回0
  }

  @Test void testAsEwkt() { // 测试方法：测试将几何对象转换为EWKT格式字符串的功能，使用@Test注解标记为Junit测试方法
    GeometryFactory gf = new GeometryFactory(); // 创建GeometryFactory对象，用于构建和创建各种几何对象实例
    Geometry g1 = gf.createPoint(new Coordinate(1, 2)); // 使用GeometryFactory创建一个点几何对象，坐标为(1,2)，存储在变量g1中
    g1.setSRID(1234); // 设置几何对象g1的空间参考系统标识符(SRID)为1234，表示该几何对象使用特定的空间参考系统
    assertThat(SpatialTypeUtils.asEwkt(g1), is("srid:1234;POINT (1 2)")); // 断言验证：调用SpatialTypeUtils.asEwkt方法将g1几何对象转换为EWKT字符串，验证结果为"srid:1234;POINT (1 2)"，确保转换格式正确
  }
} // 类定义结束
