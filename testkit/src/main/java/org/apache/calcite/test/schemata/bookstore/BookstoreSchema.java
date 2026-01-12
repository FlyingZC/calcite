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
package org.apache.calcite.test.schemata.bookstore; // 包声明：书店模式的包路径，用于测试Calcite的嵌套结构处理能力

import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值检查注解，用于标记可能为null的字段

import java.math.BigDecimal; // 导入BigDecimal类，用于精确表示经纬度坐标
import java.util.Arrays; // 导入Arrays工具类，用于创建固定大小的列表
import java.util.Collections; // 导入Collections工具类，用于创建不可变的单元素列表
import java.util.List; // 导入List接口，用于存储书籍和页面的集合

/**
 * A Schema representing a bookstore. // 代表书店的Schema（模式），用于测试Calcite的嵌套数据结构处理能力
 *
 * <p>It contains a single table with various levels/types of nesting, // 包含一个具有多级嵌套结构的表，用于测试依赖嵌套结构的代码
 * and is used mainly for testing parts of code that rely on nested // 主要用于测试Calcite中处理嵌套结构的各种功能
 * structures.
 *
 * <p>New authors can be added but attention should be made to update // 可以添加新的作者，但需要小心更新可能失败的测试用例
 * appropriately tests that might fail.
 *
 * <p>The Schema is meant to be used with // 此Schema设计用于配合ReflectiveSchema（反射模式）使用
 * {@link org.apache.calcite.adapter.java.ReflectiveSchema} thus all // 因此所有字段和方法都必须是public的，以便反射访问
 * fields, and methods, should be public.
 */
public final class BookstoreSchema { // 声明一个final类BookstoreSchema，表示书店模式，不可被继承

  // 定义一个public final的Author数组，作为Schema中的主表，包含3位作者的完整信息
  // 这个数组会被ReflectiveSchema反射识别为一个表，表名为"authors"
  public final Author[] authors = { // authors数组初始化，包含3个Author对象，代表3位不同的作者
      new Author(1, // 创建第1个作者对象，作者ID为1
          "Victor Hugo", // 作者姓名：维克多·雨果
          new Place( // 创建出生地点对象，包含坐标、城市和国家
              new Coordinate(BigDecimal.valueOf(47.24), BigDecimal.valueOf(6.02)), // 经纬度坐标：纬度47.24，经度6.02（法国贝桑松）
              "Besançon", // 城市：贝桑松
              "France"), // 国家：法国
          Collections.singletonList( // 创建只包含一个元素的不可变列表
              new Book("Les Misérables", // 创建书籍对象：书名《悲惨世界》
                  1862, // 出版年份：1862年
                  Collections.singletonList(new Page(1, "Contents"))))), // 书籍页面列表：只包含第1页，内容类型为"Contents"（目录）
      new Author(2, // 创建第2个作者对象，作者ID为2
          "Nikos Kazantzakis", // 作者姓名：尼科斯·卡赞扎基斯
          new Place( // 创建出生地点对象
              new Coordinate(BigDecimal.valueOf(35.3387), BigDecimal.valueOf(25.1442)), // 经纬度坐标：纬度35.3387，经度25.1442（希腊伊拉克利翁）
              "Heraklion", // 城市：伊拉克利翁
              "Greece"), // 国家：希腊
          Arrays.asList( // 创建包含多个元素的列表
              new Book("Zorba the Greek", // 创建第1本书对象：书名《希腊人佐尔巴》
                  1946, // 出版年份：1946年
                  Arrays.asList(new Page(1, "Contents"), // 书籍页面列表：第1页"Contents"（目录）
                      new Page(2, "Acknowledgements"))), // 第2页"Acknowledgements"（致谢）
              new Book("The Last Temptation of Christ", // 创建第2本书对象：书名《基督最后的诱惑》
                  1955, // 出版年份：1955年
                  Collections.singletonList(new Page(1, "Contents"))))), // 书籍页面列表：只包含第1页"Contents"（目录）
      new Author(3, // 创建第3个作者对象，作者ID为3
          "Homer", // 作者姓名：荷马
          new Place(null, // 创建出生地点对象，坐标为null（古代作者，无精确坐标记录）
              "Ionia", // 城市/地区：爱奥尼亚
              "Greece"), // 国家：希腊
          Collections.emptyList()) // 书籍列表：空列表，表示该作者没有书籍记录
  }; // authors数组定义结束

  /** Author. */ // Author类：表示作者信息的静态内部类，包含作者的基本信息和作品列表
  public static class Author { // 声明一个public静态内部类Author，表示作者
    public final int aid; // 作者ID，唯一标识符，final表示不可变
    public final String name; // 作者姓名，final表示不可变
    public final Place birthPlace; // 出生地信息，Place类型，包含坐标、城市和国家，final表示不可变
    @org.apache.calcite.adapter.java.Array(component = Book.class) // Calcite注解：标记这是一个数组类型的字段，元素类型为Book
    public final List<Book> books; // 作者的书籍列表，使用List<Book>类型，final表示不可变

    // Author类的构造方法：初始化作者对象的所有字段
    public Author(int aid, String name, Place birthPlace, List<Book> books) { // 构造方法参数：作者ID、姓名、出生地、书籍列表
      this.aid = aid; // 将参数aid赋值给实例字段aid
      this.name = name; // 将参数name赋值给实例字段name
      this.birthPlace = birthPlace; // 将参数birthPlace赋值给实例字段birthPlace
      this.books = books; // 将参数books赋值给实例字段books
    } // 构造方法结束
  } // Author类定义结束

  /** Place. */ // Place类：表示地点信息的静态内部类，包含坐标、城市和国家
  public static class Place { // 声明一个public静态内部类Place，表示地点
    public final @Nullable Coordinate coords; // 地理坐标，Coordinate类型，@Nullable表示可能为null
    public final String city; // 城市名称，final表示不可变
    public final String country; // 国家名称，final表示不可变

    // Place类的构造方法：初始化地点对象的所有字段
    public Place(@Nullable Coordinate coords, String city, String country) { // 构造方法参数：坐标（可为null）、城市、国家
      this.coords = coords; // 将参数coords赋值给实例字段coords
      this.city = city; // 将参数city赋值给实例字段city
      this.country = country; // 将参数country赋值给实例字段country
    } // 构造方法结束
  } // Place类定义结束

  /** Coordinate. */ // Coordinate类：表示地理坐标的静态内部类，包含纬度和经度
  public static class Coordinate { // 声明一个public静态内部类Coordinate，表示坐标
    public final BigDecimal latitude; // 纬度，使用BigDecimal类型确保精度，final表示不可变
    public final BigDecimal longtitude; // 经度，使用BigDecimal类型确保精度，final表示不可变（注意：这里拼写为longtitude，可能是longitude的笔误）

    // Coordinate类的构造方法：初始化坐标对象的所有字段
    public Coordinate(BigDecimal latitude, BigDecimal longtitude) { // 构造方法参数：纬度、经度
      this.latitude = latitude; // 将参数latitude赋值给实例字段latitude
      this.longtitude = longtitude; // 将参数longtitude赋值给实例字段longtitude
    } // 构造方法结束
  } // Coordinate类定义结束

  /** Book. */ // Book类：表示书籍信息的静态内部类，包含书名、出版年份和页面列表
  public static class Book { // 声明一个public静态内部类Book，表示书籍
    public final String title; // 书名，final表示不可变
    public final int publishYear; // 出版年份，final表示不可变
    @org.apache.calcite.adapter.java.Array(component = Page.class) // Calcite注解：标记这是一个数组类型的字段，元素类型为Page
    public final List<Page> pages; // 书籍页面列表，使用List<Page>类型，final表示不可变

    // Book类的构造方法：初始化书籍对象的所有字段
    public Book(String title, int publishYear, List<Page> pages) { // 构造方法参数：书名、出版年份、页面列表
      this.title = title; // 将参数title赋值给实例字段title
      this.publishYear = publishYear; // 将参数publishYear赋值给实例字段publishYear
      this.pages = pages; // 将参数pages赋值给实例字段pages
    } // 构造方法结束
  } // Book类定义结束

  /** Page. */ // Page类：表示页面信息的静态内部类，包含页码和内容类型
  public static class Page { // 声明一个public静态内部类Page，表示页面
    public final int pageNo; // 页码，final表示不可变
    public final String contentType; // 内容类型（如"Contents"、"Acknowledgements"等），final表示不可变

    // Page类的构造方法：初始化页面对象的所有字段
    public Page(int pageNo, String contentType) { // 构造方法参数：页码、内容类型
      this.pageNo = pageNo; // 将参数pageNo赋值给实例字段pageNo
      this.contentType = contentType; // 将参数contentType赋值给实例字段contentType
    } // 构造方法结束
  } // Page类定义结束
} // BookstoreSchema类定义结束
