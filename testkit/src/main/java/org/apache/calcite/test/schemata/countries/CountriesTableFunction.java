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
package org.apache.calcite.test.schemata.countries; // 声明包名，该类位于org.apache.calcite.test.schemata.countries包下

import org.apache.calcite.DataContext; // 导入DataContext接口，用于在查询执行时提供上下文信息（如会话变量、用户定义函数等）
import org.apache.calcite.config.CalciteConnectionConfig; // 导入Calcite连接配置类，用于获取Calcite连接的配置信息
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的数据集合，用于LINQ风格的查询
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供将数组/集合转换为Enumerable的静态方法
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型，描述表的结构（列名、类型等）
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，表示可扫描的表，提供scan方法返回数据
import org.apache.calcite.schema.Schema; // 导入Schema接口，表示模式（数据库中的命名空间）
import org.apache.calcite.schema.Statistic; // 导入Statistic接口，表示表的统计信息（如行数、唯一键等）
import org.apache.calcite.schema.Statistics; // 导入Statistics工具类，用于创建Statistic对象
import org.apache.calcite.sql.SqlCall; // 导入SqlCall类，表示SQL函数调用
import org.apache.calcite.sql.SqlNode; // 导入SqlNode接口，表示SQL语法树中的节点
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL类型名称（如VARCHAR、DECIMAL等）
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合，用于标识列索引

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，表示不可变的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的类型

/** A table function that returns all countries in the world. // 类的JavaDoc注释：这是一个返回世界上所有国家的表函数
 *
 * <p>Has same content as // 该表函数的内容与以下文件相同
 * <code>file/src/test/resources/geo/countries.csv</code>. */ // CSV文件路径，包含国家数据
public class CountriesTableFunction { // 类定义：CountriesTableFunction，用于提供国家数据的表函数
  private CountriesTableFunction() {} // 私有构造方法，防止实例化，该类只提供静态方法

  private static final Object[][] ROWS = { // 静态常量二维数组，存储所有国家的数据，每行包含[国家代码, 纬度, 经度, 国家名称]
      {"AD", 42.546245, 1.601554, "Andorra"}, // 国家数据：ISO代码AD，纬度42.546245，经度1.601554，国家名称安道尔
      {"AE", 23.424076, 53.847818, "United Arab Emirates"}, // 阿拉伯联合酋长国
      {"AF", 33.93911, 67.709953, "Afghanistan"}, // 阿富汗
      {"AG", 17.060816, -61.796428, "Antigua and Barbuda"}, // 安提瓜和巴布达
      {"AI", 18.220554, -63.068615, "Anguilla"}, // 安圭拉
      {"AL", 41.153332, 20.168331, "Albania"}, // 阿尔巴尼亚
      {"AM", 40.069099, 45.038189, "Armenia"}, // 亚美尼亚
      {"AN", 12.226079, -69.060087, "Netherlands Antilles"}, // 荷属安的列斯（已解体）
      {"AO", -11.202692, 17.873887, "Angola"}, // 安哥拉
      {"AQ", -75.250973, -0.071389, "Antarctica"}, // 南极洲
      {"AR", -38.416097, -63.616672, "Argentina"}, // 阿根廷
      {"AS", -14.270972, -170.132217, "American Samoa"}, // 美属萨摩亚
      {"AT", 47.516231, 14.550072, "Austria"}, // 奥地利
      {"AU", -25.274398, 133.775136, "Australia"}, // 澳大利亚
      {"AW", 12.52111, -69.968338, "Aruba"}, // 阿鲁巴
      {"AZ", 40.143105, 47.576927, "Azerbaijan"}, // 阿塞拜疆
      {"BA", 43.915886, 17.679076, "Bosnia and Herzegovina"}, // 波斯尼亚和黑塞哥维那
      {"BB", 13.193887, -59.543198, "Barbados"}, // 巴巴多斯
      {"BD", 23.684994, 90.356331, "Bangladesh"}, // 孟加拉国
      {"BE", 50.503887, 4.469936, "Belgium"}, // 比利时
      {"BF", 12.238333, -1.561593, "Burkina Faso"}, // 布基纳法索
      {"BG", 42.733883, 25.48583, "Bulgaria"}, // 保加利亚
      {"BH", 25.930414, 50.637772, "Bahrain"}, // 巴林
      {"BI", -3.373056, 29.918886, "Burundi"}, // 布隆迪
      {"BJ", 9.30769, 2.315834, "Benin"}, // 贝宁
      {"BM", 32.321384, -64.75737, "Bermuda"}, // 百慕大
      {"BN", 4.535277, 114.727669, "Brunei"}, // 文莱
      {"BO", -16.290154, -63.588653, "Bolivia"}, // 玻利维亚
      {"BR", -14.235004, -51.92528, "Brazil"}, // 巴西
      {"BS", 25.03428, -77.39628, "Bahamas"}, // 巴哈马
      {"BT", 27.514162, 90.433601, "Bhutan"}, // 不丹
      {"BV", -54.423199, 3.413194, "Bouvet Island"}, // 布韦岛
      {"BW", -22.328474, 24.684866, "Botswana"}, // 博茨瓦纳
      {"BY", 53.709807, 27.953389, "Belarus"}, // 白俄罗斯
      {"BZ", 17.189877, -88.49765, "Belize"}, // 伯利兹
      {"CA", 56.130366, -106.346771, "Canada"}, // 加拿大
      {"CC", -12.164165, 96.870956, "Cocos [Keeling] Islands"}, // 科科斯（基林）群岛
      {"CD", -4.038333, 21.758664, "Congo [DRC]"}, // 刚果民主共和国
      {"CF", 6.611111, 20.939444, "Central African Republic"}, // 中非共和国
      {"CG", -0.228021, 15.827659, "Congo [Republic]"}, // 刚果共和国
      {"CH", 46.818188, 8.227512, "Switzerland"}, // 瑞士
      {"CI", 7.539989, -5.54708, "Côte d'Ivoire"}, // 科特迪瓦
      {"CK", -21.236736, -159.777671, "Cook Islands"}, // 库克群岛
      {"CL", -35.675147, -71.542969, "Chile"}, // 智利
      {"CM", 7.369722, 12.354722, "Cameroon"}, // 喀麦隆
      {"CN", 35.86166, 104.195397, "China"}, // 中国
      {"CO", 4.570868, -74.297333, "Colombia"}, // 哥伦比亚
      {"CR", 9.748917, -83.753428, "Costa Rica"}, // 哥斯达黎加
      {"CU", 21.521757, -77.781167, "Cuba"}, // 古巴
      {"CV", 16.002082, -24.013197, "Cape Verde"}, // 佛得角
      {"CX", -10.447525, 105.690449, "Christmas Island"}, // 圣诞岛
      {"CY", 35.126413, 33.429859, "Cyprus"}, // 塞浦路斯
      {"CZ", 49.817492, 15.472962, "Czech Republic"}, // 捷克共和国
      {"DE", 51.165691, 10.451526, "Germany"}, // 德国
      {"DJ", 11.825138, 42.590275, "Djibouti"}, // 吉布提
      {"DK", 56.26392, 9.501785, "Denmark"}, // 丹麦
      {"DM", 15.414999, -61.370976, "Dominica"}, // 多米尼克
      {"DO", 18.735693, -70.162651, "Dominican Republic"}, // 多米尼加共和国
      {"DZ", 28.033886, 1.659626, "Algeria"}, // 阿尔及利亚
      {"EC", -1.831239, -78.183406, "Ecuador"}, // 厄瓜多尔
      {"EE", 58.595272, 25.013607, "Estonia"}, // 爱沙尼亚
      {"EG", 26.820553, 30.802498, "Egypt"}, // 埃及
      {"EH", 24.215527, -12.885834, "Western Sahara"}, // 西撒哈拉
      {"ER", 15.179384, 39.782334, "Eritrea"}, // 厄立特里亚
      {"ES", 40.463667, -3.74922, "Spain"}, // 西班牙
      {"ET", 9.145, 40.489673, "Ethiopia"}, // 埃塞俄比亚
      {"FI", 61.92411, 25.748151, "Finland"}, // 芬兰
      {"FJ", -16.578193, 179.414413, "Fiji"}, // 斐济
      {"FK", -51.796253, -59.523613, "Falkland Islands [Islas Malvinas]"}, // 福克兰群岛
      {"FM", 7.425554, 150.550812, "Micronesia"}, // 密克罗尼西亚
      {"FO", 61.892635, -6.911806, "Faroe Islands"}, // 法罗群岛
      {"FR", 46.227638, 2.213749, "France"}, // 法国
      {"GA", -0.803689, 11.609444, "Gabon"}, // 加蓬
      {"GB", 55.378051, -3.435973, "United Kingdom"}, // 英国
      {"GD", 12.262776, -61.604171, "Grenada"}, // 格林纳达
      {"GE", 42.315407, 43.356892, "Georgia"}, // 格鲁吉亚
      {"GF", 3.933889, -53.125782, "French Guiana"}, // 法属圭亚那
      {"GG", 49.465691, -2.585278, "Guernsey"}, // 根西岛
      {"GH", 7.946527, -1.023194, "Ghana"}, // 加纳
      {"GI", 36.137741, -5.345374, "Gibraltar"}, // 直布罗陀
      {"GL", 71.706936, -42.604303, "Greenland"}, // 格陵兰
      {"GM", 13.443182, -15.310139, "Gambia"}, // 冈比亚
      {"GN", 9.945587, -9.696645, "Guinea"}, // 几内亚
      {"GP", 16.995971, -62.067641, "Guadeloupe"}, // 瓜德罗普
      {"GQ", 1.650801, 10.267895, "Equatorial Guinea"}, // 赤道几内亚
      {"GR", 39.074208, 21.824312, "Greece"}, // 希腊
      {"GS", -54.429579, -36.587909, "South Georgia and the South Sandwich Islands"}, // 南乔治亚和南桑威奇群岛
      {"GT", 15.783471, -90.230759, "Guatemala"}, // 危地马拉
      {"GU", 13.444304, 144.793731, "Guam"}, // 关岛
      {"GW", 11.803749, -15.180413, "Guinea-Bissau"}, // 几内亚比绍
      {"GY", 4.860416, -58.93018, "Guyana"}, // 圭亚那
      {"GZ", 31.354676, 34.308825, "Gaza Strip"}, // 加沙地带
      {"HK", 22.396428, 114.109497, "Hong Kong"}, // 香港
      {"HM", -53.08181, 73.504158, "Heard Island and McDonald Islands"}, // 赫德岛和麦克唐纳群岛
      {"HN", 15.199999, -86.241905, "Honduras"}, // 洪都拉斯
      {"HR", 45.1, 15.2, "Croatia"}, // 克罗地亚
      {"HT", 18.971187, -72.285215, "Haiti"}, // 海地
      {"HU", 47.162494, 19.503304, "Hungary"}, // 匈牙利
      {"ID", -0.789275, 113.921327, "Indonesia"}, // 印度尼西亚
      {"IE", 53.41291, -8.24389, "Ireland"}, // 爱尔兰
      {"IL", 31.046051, 34.851612, "Israel"}, // 以色列
      {"IM", 54.236107, -4.548056, "Isle of Man"}, // 马恩岛
      {"IN", 20.593684, 78.96288, "India"}, // 印度
      {"IO", -6.343194, 71.876519, "British Indian Ocean Territory"}, // 英属印度洋领地
      {"IQ", 33.223191, 43.679291, "Iraq"}, // 伊拉克
      {"IR", 32.427908, 53.688046, "Iran"}, // 伊朗
      {"IS", 64.963051, -19.020835, "Iceland"}, // 冰岛
      {"IT", 41.87194, 12.56738, "Italy"}, // 意大利
      {"JE", 49.214439, -2.13125, "Jersey"}, // 泽西岛
      {"JM", 18.109581, -77.297508, "Jamaica"}, // 牙买加
      {"JO", 30.585164, 36.238414, "Jordan"}, // 约旦
      {"JP", 36.204824, 138.252924, "Japan"}, // 日本
      {"KE", -0.023559, 37.906193, "Kenya"}, // 肯尼亚
      {"KG", 41.20438, 74.766098, "Kyrgyzstan"}, // 吉尔吉斯斯坦
      {"KH", 12.565679, 104.990963, "Cambodia"}, // 柬埔寨
      {"KI", -3.370417, -168.734039, "Kiribati"}, // 基里巴斯
      {"KM", -11.875001, 43.872219, "Comoros"}, // 科摩罗
      {"KN", 17.357822, -62.782998, "Saint Kitts and Nevis"}, // 圣基茨和尼维斯
      {"KP", 40.339852, 127.510093, "North Korea"}, // 朝鲜
      {"KR", 35.907757, 127.766922, "South Korea"}, // 韩国
      {"KW", 29.31166, 47.481766, "Kuwait"}, // 科威特
      {"KY", 19.513469, -80.566956, "Cayman Islands"}, // 开曼群岛
      {"KZ", 48.019573, 66.923684, "Kazakhstan"}, // 哈萨克斯坦
      {"LA", 19.85627, 102.495496, "Laos"}, // 老挝
      {"LB", 33.854721, 35.862285, "Lebanon"}, // 黎巴嫩
      {"LC", 13.909444, -60.978893, "Saint Lucia"}, // 圣卢西亚
      {"LI", 47.166, 9.555373, "Liechtenstein"}, // 列支敦士登
      {"LK", 7.873054, 80.771797, "Sri Lanka"}, // 斯里兰卡
      {"LR", 6.428055, -9.429499, "Liberia"}, // 利比里亚
      {"LS", -29.609988, 28.233608, "Lesotho"}, // 莱索托
      {"LT", 55.169438, 23.881275, "Lithuania"}, // 立陶宛
      {"LU", 49.815273, 6.129583, "Luxembourg"}, // 卢森堡
      {"LV", 56.879635, 24.603189, "Latvia"}, // 拉脱维亚
      {"LY", 26.3351, 17.228331, "Libya"}, // 利比亚
      {"MA", 31.791702, -7.09262, "Morocco"}, // 摩洛哥
      {"MC", 43.750298, 7.412841, "Monaco"}, // 摩纳哥
      {"MD", 47.411631, 28.369885, "Moldova"}, // 摩尔多瓦
      {"ME", 42.708678, 19.37439, "Montenegro"}, // 黑山
      {"MG", -18.766947, 46.869107, "Madagascar"}, // 马达加斯加
      {"MH", 7.131474, 171.184478, "Marshall Islands"}, // 马绍尔群岛
      {"MK", 41.608635, 21.745275, "Macedonia [FYROM]"}, // 马其顿
      {"ML", 17.570692, -3.996166, "Mali"}, // 马里
      {"MM", 21.913965, 95.956223, "Myanmar [Burma]"}, // 缅甸
      {"MN", 46.862496, 103.846656, "Mongolia"}, // 蒙古
      {"MO", 22.198745, 113.543873, "Macau"}, // 澳门
      {"MP", 17.33083, 145.38469, "Northern Mariana Islands"}, // 北马里亚纳群岛
      {"MQ", 14.641528, -61.024174, "Martinique"}, // 马提尼克
      {"MR", 21.00789, -10.940835, "Mauritania"}, // 毛里塔尼亚
      {"MS", 16.742498, -62.187366, "Montserrat"}, // 蒙特塞拉特
      {"MT", 35.937496, 14.375416, "Malta"}, // 马耳他
      {"MU", -20.348404, 57.552152, "Mauritius"}, // 毛里求斯
      {"MV", 3.202778, 73.22068, "Maldives"}, // 马尔代夫
      {"MW", -13.254308, 34.301525, "Malawi"}, // 马拉维
      {"MX", 23.634501, -102.552784, "Mexico"}, // 墨西哥
      {"MY", 4.210484, 101.975766, "Malaysia"}, // 马来西亚
      {"MZ", -18.665695, 35.529562, "Mozambique"}, // 莫桑比克
      {"NA", -22.95764, 18.49041, "Namibia"}, // 纳米比亚
      {"NC", -20.904305, 165.618042, "New Caledonia"}, // 新喀里多尼亚
      {"NE", 17.607789, 8.081666, "Niger"}, // 尼日尔
      {"NF", -29.040835, 167.954712, "Norfolk Island"}, // 诺福克岛
      {"NG", 9.081999, 8.675277, "Nigeria"}, // 尼日利亚
      {"NI", 12.865416, -85.207229, "Nicaragua"}, // 尼加拉瓜
      {"NL", 52.132633, 5.291266, "Netherlands"}, // 荷兰
      {"NO", 60.472024, 8.468946, "Norway"}, // 挪威
      {"NP", 28.394857, 84.124008, "Nepal"}, // 尼泊尔
      {"NR", -0.522778, 166.931503, "Nauru"}, // 瑙鲁
      {"NU", -19.054445, -169.867233, "Niue"}, // 纽埃
      {"NZ", -40.900557, 174.885971, "New Zealand"}, // 新西兰
      {"OM", 21.512583, 55.923255, "Oman"}, // 阿曼
      {"PA", 8.537981, -80.782127, "Panama"}, // 巴拿马
      {"PE", -9.189967, -75.015152, "Peru"}, // 秘鲁
      {"PF", -17.679742, -149.406843, "French Polynesia"}, // 法属波利尼西亚
      {"PG", -6.314993, 143.95555, "Papua New Guinea"}, // 巴布亚新几内亚
      {"PH", 12.879721, 121.774017, "Philippines"}, // 菲律宾
      {"PK", 30.375321, 69.345116, "Pakistan"}, // 巴基斯坦
      {"PL", 51.919438, 19.145136, "Poland"}, // 波兰
      {"PM", 46.941936, -56.27111, "Saint Pierre and Miquelon"}, // 圣皮埃尔和密克隆
      {"PN", -24.703615, -127.439308, "Pitcairn Islands"}, // 皮特凯恩群岛
      {"PR", 18.220833, -66.590149, "Puerto Rico"}, // 波多黎各
      {"PS", 31.952162, 35.233154, "Palestinian Territories"}, // 巴勒斯坦领土
      {"PT", 39.399872, -8.224454, "Portugal"}, // 葡萄牙
      {"PW", 7.51498, 134.58252, "Palau"}, // 帕劳
      {"PY", -23.442503, -58.443832, "Paraguay"}, // 巴拉圭
      {"QA", 25.354826, 51.183884, "Qatar"}, // 卡塔尔
      {"RE", -21.115141, 55.536384, "Réunion"}, // 留尼汪
      {"RO", 45.943161, 24.96676, "Romania"}, // 罗马尼亚
      {"RS", 44.016521, 21.005859, "Serbia"}, // 塞尔维亚
      {"RU", 61.52401, 105.318756, "Russia"}, // 俄罗斯
      {"RW", -1.940278, 29.873888, "Rwanda"}, // 卢旺达
      {"SA", 23.885942, 45.079162, "Saudi Arabia"}, // 沙特阿拉伯
      {"SB", -9.64571, 160.156194, "Solomon Islands"}, // 所罗门群岛
      {"SC", -4.679574, 55.491977, "Seychelles"}, // 塞舌尔
      {"SD", 12.862807, 30.217636, "Sudan"}, // 苏丹
      {"SE", 60.128161, 18.643501, "Sweden"}, // 瑞典
      {"SG", 1.352083, 103.819836, "Singapore"}, // 新加坡
      {"SH", -24.143474, -10.030696, "Saint Helena"}, // 圣赫勒拿
      {"SI", 46.151241, 14.995463, "Slovenia"}, // 斯洛文尼亚
      {"SJ", 77.553604, 23.670272, "Svalbard and Jan Mayen"}, // 斯瓦尔巴和扬马延
      {"SK", 48.669026, 19.699024, "Slovakia"}, // 斯洛伐克
      {"SL", 8.460555, -11.779889, "Sierra Leone"}, // 塞拉利昂
      {"SM", 43.94236, 12.457777, "San Marino"}, // 圣马力诺
      {"SN", 14.497401, -14.452362, "Senegal"}, // 塞内加尔
      {"SO", 5.152149, 46.199616, "Somalia"}, // 索马里
      {"SR", 3.919305, -56.027783, "Suriname"}, // 苏里南
      {"ST", 0.18636, 6.613081, "São Tomé and Príncipe"}, // 圣多美和普林西比
      {"SV", 13.794185, -88.89653, "El Salvador"}, // 萨尔瓦多
      {"SY", 34.802075, 38.996815, "Syria"}, // 叙利亚
      {"SZ", -26.522503, 31.465866, "Swaziland"}, // 斯威士兰
      {"TC", 21.694025, -71.797928, "Turks and Caicos Islands"}, // 特克斯和凯科斯群岛
      {"TD", 15.454166, 18.732207, "Chad"}, // 乍得
      {"TF", -49.280366, 69.348557, "French Southern Territories"}, // 法属南部领地
      {"TG", 8.619543, 0.824782, "Togo"}, // 多哥
      {"TH", 15.870032, 100.992541, "Thailand"}, // 泰国
      {"TJ", 38.861034, 71.276093, "Tajikistan"}, // 塔吉克斯坦
      {"TK", -8.967363, -171.855881, "Tokelau"}, // 托克劳
      {"TL", -8.874217, 125.727539, "Timor-Leste"}, // 东帝汶
      {"TM", 38.969719, 59.556278, "Turkmenistan"}, // 土库曼斯坦
      {"TN", 33.886917, 9.537499, "Tunisia"}, // 突尼斯
      {"TO", -21.178986, -175.198242, "Tonga"}, // 汤加
      {"TR", 38.963745, 35.243322, "Turkey"}, // 土耳其
      {"TT", 10.691803, -61.222503, "Trinidad and Tobago"}, // 特立尼达和多巴哥
      {"TV", -7.109535, 177.64933, "Tuvalu"}, // 图瓦卢
      {"TW", 23.69781, 120.960515, "Taiwan"}, // 台湾
      {"TZ", -6.369028, 34.888822, "Tanzania"}, // 坦桑尼亚
      {"UA", 48.379433, 31.16558, "Ukraine"}, // 乌克兰
      {"UG", 1.373333, 32.290275, "Uganda"}, // 乌干达
      {"UM", null, null, "U.S.Minor Outlying Islands"}, // 美国本土外小岛屿（纬度经度为null）
      {"US", 37.09024, -95.712891, "United States"}, // 美国
      {"UY", -32.522779, -55.765835, "Uruguay"}, // 乌拉圭
      {"UZ", 41.377491, 64.585262, "Uzbekistan"}, // 乌兹别克斯坦
      {"VA", 41.902916, 12.453389, "Vatican City"}, // 梵蒂冈
      {"VC", 12.984305, -61.287228, "Saint Vincent and the Grenadines"}, // 圣文森特和格林纳丁斯
      {"VE", 6.42375, -66.58973, "Venezuela"}, // 委内瑞拉
      {"VG", 18.420695, -64.639968, "British Virgin Islands"}, // 英属维尔京群岛
      {"VI", 18.335765, -64.896335, "U.S. Virgin Islands"}, // 美属维尔京群岛
      {"VN", 14.058324, 108.277199, "Vietnam"}, // 越南
      {"VU", -15.376706, 166.959158, "Vanuatu"}, // 瓦努阿图
      {"WF", -13.768752, -177.156097, "Wallis and Futuna"}, // 瓦利斯和富图纳
      {"WS", -13.759029, -172.104629, "Samoa"}, // 萨摩亚
      {"XK", 42.602636, 20.902977, "Kosovo"}, // 科索沃
      {"YE", 15.552727, 48.516388, "Yemen"}, // 也门
      {"YT", -12.8275, 45.166244, "Mayotte"}, // 马约特
      {"ZA", -30.559482, 22.937506, "South Africa"}, // 南非
      {"ZM", -13.133897, 27.849332, "Zambia"}, // 赞比亚
      {"ZW", -19.015438, 29.154857, "Zimbabwe"}, // 津巴布韦
  }; // ROWS数组结束，包含246个国家/地区的数据

  public static ScannableTable eval(boolean b) { // 静态方法eval，返回一个ScannableTable实例，参数b是布尔值（未使用，可能是为未来扩展预留）
    return new ScannableTable() { // 创建并返回一个匿名ScannableTable对象，代表一个可扫描的表
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，扫描表数据并返回可枚举的数据集合
        return Linq4j.asEnumerable(ROWS); // 将ROWS数组转换为LINQ风格的Enumerable对象，允许迭代访问国家数据
      }; // scan方法结束

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，获取表的行类型（表结构定义）
        return typeFactory.builder() // 使用类型工厂构建器开始构建行类型
            .add("country", SqlTypeName.VARCHAR) // 添加第一列：country列，类型为VARCHAR（字符串）
            .add("latitude", SqlTypeName.DECIMAL).nullable(true) // 添加第二列：latitude列，类型为DECIMAL（小数），可为null
            .add("longitude", SqlTypeName.DECIMAL).nullable(true) // 添加第三列：longitude列，类型为DECIMAL（小数），可为null
            .add("name", SqlTypeName.VARCHAR) // 添加第四列：name列，类型为VARCHAR（字符串）
            .build(); // 构建并返回完整的RelDataType对象
      } // getRowType方法结束

      @Override public Statistic getStatistic() { // 重写getStatistic方法，获取表的统计信息
        return Statistics.of(246D, // 使用Statistics工具类创建统计对象，参数1：表的行数（246行）
            ImmutableList.of(ImmutableBitSet.of(0), ImmutableBitSet.of(3))); // 参数2：唯一键列表，ImmutableBitSet.of(0)表示第0列（country）是唯一键，ImmutableBitSet.of(3)表示第3列（name）是唯一键
      } // getStatistic方法结束

      @Override public Schema.TableType getJdbcTableType() { // 重写getJdbcTableType方法，获取表的JDBC类型
        return Schema.TableType.TABLE; // 返回表类型为TABLE（普通表）
      } // getJdbcTableType方法结束

      @Override public boolean isRolledUp(String column) { // 重写isRolledUp方法，判断指定列是否为汇总列（rollup column）
        return false; // 返回false，表示该表没有汇总列
      } // isRolledUp方法结束

      @Override public boolean rolledUpColumnValidInsideAgg(String column, SqlCall call, // 重写rolledUpColumnValidInsideAgg方法，判断汇总列是否可以在聚合函数内部使用
          @Nullable SqlNode parent, @Nullable CalciteConnectionConfig config) { // 参数：column列名，call SQL调用，parent父节点，config连接配置
        return false; // 返回false，表示不包含任何汇总列
      } // rolledUpColumnValidInsideAgg方法结束
    }; // 匿名ScannableTable对象创建结束
  } // eval方法结束
} // CountriesTableFunction类定义结束
