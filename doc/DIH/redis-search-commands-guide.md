# Redis Search (RediSearch) 常用命令详解教程

## 目录

- [1. 概述](#1-概述)
- [2. 索引管理命令](#2-索引管理命令)
- [3. 数据写入命令 (Hash + 索引自动关联)](#3-数据写入命令-hash--索引自动关联)
- [4. 全文搜索命令 (FT.SEARCH)](#4-全文搜索命令-ftsearch)
- [5. 聚合查询命令 (FT.AGGREGATE)](#5-聚合查询命令-ftaggregate)
- [6. 自动补全命令 (FT.SUGADD / FT.SUGGET)](#6-自动补全命令-ftsugadd--ftsugget)
- [7. 字典命令 (FT.DICTADD / FT.DICTDEL / FT.DICTDUMP)](#7-字典命令-ftdictadd--ftdictdel--ftdictdump)
- [8. 索引信息与调试命令](#8-索引信息与调试命令)
- [9. 向量搜索 (KNN)](#9-向量搜索-knn)
- [10. 与 Spring AI RedisVectorStore 的对应关系](#10-与-spring-ai-redisvectorstore-的对应关系)
- [11. 常见问题与最佳实践](#11-常见问题与最佳实践)

---

## 1. 概述

### 什么是 RediSearch

RediSearch 是 Redis 的全文搜索和二级索引模块，允许你在 Redis Hash 之上创建索引并执行复杂的搜索查询。从 Redis Stack 2.0 开始，RediSearch 已成为 Redis Stack 的内置模块。

### 核心特性

| 特性 | 说明 |
|------|------|
| 全文搜索 | 支持分词、词干提取、前缀匹配 |
| 二级索引 | 对 Hash 中的字段建立索引，支持精确匹配和范围查询 |
| 聚合查询 | 类似 SQL 的 GROUP BY、SUM、COUNT 等聚合操作 |
| 向量搜索 (KNN) | 支持近似最近邻向量相似度搜索 |
| 自动补全 | 内置前缀自动补全功能 |
| 多语言支持 | 支持中文、英文等多种语言的分词 |
| 排序 | 支持按相关度评分或自定义字段排序 |
| 高性能 | 查询延迟通常在毫秒级 |

### 前缀约定

RediSearch 的所有命令都以 `FT.` 开头：

```
FT.CREATE    — 创建索引
FT.DROPINDEX — 删除索引
FT.SEARCH    — 搜索查询
FT.AGGREGATE — 聚合查询
FT.INFO      — 查看索引信息
FT.GET       — 按 ID 获取文档
...
```

---

## 2. 索引管理命令

### 2.1 FT.CREATE — 创建索引

创建一个新的搜索索引，定义字段类型和索引策略。

#### 语法

```redis
FT.CREATE index_name [ON HASH | ON JSON] [PREFIX count prefix ...] [SCHEMA ...]
```

#### 常用参数

| 参数 | 说明 |
|------|------|
| `ON HASH` | 索引 Hash 类型键（默认） |
| `ON JSON` | 索引 JSON 类型键（Redis 7.2+） |
| `PREFIX count prefix ...` | 只索引以指定前缀开头的键，例如 `PREFIX 1 doc:` |
| `FILTER expression` | 只索引满足过滤条件的键 |
| `SCHEMA` | 定义索引的字段及其类型 |
| `STOPWORDS count word ...` | 停用词列表 |
| `LANGUAGE default_lang` | 默认分词语言 |

#### 字段类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `TEXT` | 全文本字段，支持分词搜索 | `TEXT title WEIGHT 5.0` |
| `NUMERIC` | 数值字段，支持范围查询 | `NUMERIC price` |
| `TAG` | 精确匹配字段（支持多值），类似枚举 | `TAG category SEPARATOR ","` |
| `GEO` | 地理位置字段 | `GEO location` |
| `VECTOR` | 向量字段，用于 KNN 搜索 | `VECTOR embedding FLAT dim 1536 dist COSINE` |

#### 基础示例

```redis
# 创建一个文章索引
FT.CREATE idx:articles ON HASH PREFIX 1 article: SCHEMA \
  title TEXT WEIGHT 5.0 \
  content TEXT \
  author TAG \
  category TAG SEPARATOR "," \
  publish_date NUMERIC \
  likes NUMERIC
```

#### 带停用词的示例

```redis
FT.CREATE idx:docs ON HASH PREFIX 1 doc: SCHEMA \
  title TEXT WEIGHT 5.0 STOPWORDS 10 the a an is are was were \
  body TEXT \
  status TAG \
  created_at NUMERIC SORTABLE
```

#### 向量索引示例（本项目实际使用场景）

```redis
# 类似 Spring AI RedisVectorStore 创建的索引
FT.CREATE idx:embedding ON HASH PREFIX 1 doc: SCHEMA \
  content TEXT \
  metadata_tag TAG \
  embedding VECTOR FLAT 6 dim 1536 distance_metric COSINE dtype float32
```

---

### 2.2 FT.DROPINDEX — 删除索引

```redis
# 删除索引（索引不存在时报错）
FT.DROPINDEX idx:articles

# 删除索引并删除索引关联的所有文档键
FT.DROPINDEX idx:articles DD
```

> **注意**：使用 `DD` 参数会同时删除所有被索引的文档键，操作不可逆！

---

### 2.3 FT.ALTER — 修改索引

```redis
# 向已有索引中添加新字段
FT.ALTER idx:articles SCHEMA ADD tags TAG SEPARATOR " "

# 从索引中删除字段（需要 Redis 7.2+）
FT.ALTER idx:articles SCHEMA DROP tags
```

---

### 2.4 FT.LIST — 列出所有索引

```redis
FT.LIST
# 返回: 1) "idx:articles" 2) "idx:embedding"
```

---

## 3. 数据写入命令 (Hash + 索引自动关联)

RediSearch 本身**不提供**数据写入命令。数据以 Redis Hash 的形式写入，只要 Hash 的键名匹配索引的 `PREFIX` 规则，就会自动被索引。

### 3.1 写入 Hash 数据

```redis
# 写入一条 Hash 数据，key 前缀为 article:，匹配索引 PREFIX 规则
HSET article:1001 title "Redis Search 入门指南" \
  content "本文介绍了 RediSearch 的基本概念和使用方法" \
  author "张三" \
  category "技术,数据库" \
  publish_date 1712524800 \
  likes 128
```

### 3.2 更新 Hash 数据

```redis
# 更新字段会自动触发索引更新
HSET article:1001 likes 256
```

### 3.3 删除 Hash 数据

```redis
# 删除 Hash 键会自动从索引中移除
DEL article:1001

# 推荐使用 UNLINK 异步删除，避免阻塞
UNLINK article:1001
```

### 3.4 批量写入（Pipeline）

```bash
# 使用 redis-cli 的 pipeline 模式批量写入
redis-cli --pipe <<EOF
HSET article:2001 title "标题1" content "内容1" author "李四"
HSET article:2002 title "标题2" content "内容2" author "王五"
HSET article:2003 title "标题3" content "内容3" author "张三"
EOF
```

---

## 4. 全文搜索命令 (FT.SEARCH)

这是 RediSearch 最核心的命令，用于执行全文搜索、精确匹配、范围查询等。

### 4.1 基本语法

```redis
FT.SEARCH index_name query [NOCONTENT] [LIMIT offset num] [RETURN count field ...]
  [SUMMARIZE] [HIGHLIGHT] [SORTBY field ASC|DESC] [FILTER field min max]
  [INKEYS count key ...] [INFIELDS count field ...] [WITHSCORES]
  [WITHSORTKEYS] [EXPLAIN] [DIALECT num]
```

### 4.2 简单词查询

```redis
# 搜索单个词
FT.SEARCH idx:articles "Redis"

# 搜索短语（用引号包裹）
FT.SEARCH idx:articles "Redis Search"

# 默认操作符是 OR，搜索包含 Redis 或 数据库 的文档
FT.SEARCH idx:articles "Redis 数据库"
```

### 4.3 布尔操作符

```redis
# AND — 必须同时包含两个词
FT.SEARCH idx:articles "Redis AND Search"

# OR — 包含任一词（默认行为）
FT.SEARCH idx:articles "Redis OR Elasticsearch"

# NOT — 排除某个词
FT.SEARCH idx:articles "Redis NOT Elasticsearch"

# 复合条件
FT.SEARCH idx:articles "(Redis OR MongoDB) AND 入门 NOT 高级"
```

### 4.4 前缀匹配

```redis
# 以 Red 开头的词（前缀匹配）
FT.SEARCH idx:articles "Red*"

# 在特定字段中进行前缀匹配
FT.SEARCH idx:articles "@title:Red*"
```

### 4.5 模糊搜索

```redis
# 编辑距离为 1 的模糊搜索（允许 1 个字符的差异）
FT.SEARCH idx:articles "%%Ridis%%"

# 编辑距离为 2 的模糊搜索
FT.SEARCH idx:articles "%%Ridiss%%2"
```

### 4.6 精确短语匹配

```redis
# 精确匹配短语
FT.SEARCH idx:articles '"全文搜索"'

# 在特定字段中精确匹配
FT.SEARCH idx:articles '@title:"Redis Search"'
```

### 4.7 字段限定搜索

```redis
# 在 title 字段中搜索
FT.SEARCH idx:articles "@title:Redis"

# 在多个字段中搜索
FT.SEARCH idx:articles "@(title,content):Redis"

# 排除某个字段
FT.SEARCH idx:articles "@title:Redis @content:-Search"
```

### 4.8 TAG 字段搜索

TAG 字段支持精确匹配，**不需要**分词。

```redis
# 精确匹配 TAG 值
FT.SEARCH idx:articles "@author:{张三}"

# 多个 TAG 值（OR 语义）
FT.SEARCH idx:articles "@author:{张三|李四}"

# 排除 TAG 值
FT.SEARCH idx:articles "@author:-{王五}"

# 多值 TAG 字段（如 category 包含逗号分隔的多个值）
FT.SEARCH idx:articles "@category:{技术}"

# 本项目实际使用示例 — 按 source 过滤文档
FT.SEARCH idx:articles "@source:{my_doc_source}"
```

> **TAG vs TEXT 区别**：TAG 是精确匹配（不分词），TEXT 是全文搜索（分词）。用户名、分类、状态等用 TAG；标题、正文等用 TEXT。

### 4.9 NUMERIC 字段范围查询

```redis
# likes >= 100
FT.SEARCH idx:articles "@likes:[100 +inf]"

# likes >= 50 AND likes <= 200
FT.SEARCH idx:articles "@likes:[50 200]"

# publish_date >= 2024-01-01（使用 Unix 时间戳）
FT.SEARCH idx:articles "@publish_date:[1704067200 +inf]"

# 排除范围
FT.SEARCH idx:articles "@likes:[-inf 10]"
```

### 4.10 分页 (LIMIT)

```redis
# LIMIT offset num — offset 是跳过多少条，num 是返回多少条
# 第一页（前 10 条）
FT.SEARCH idx:articles "Redis" LIMIT 0 10

# 第二页
FT.SEARCH idx:articles "Redis" LIMIT 10 10

# 第三页
FT.SEARCH idx:articles "Redis" LIMIT 20 10
```

### 4.11 只返回特定字段 (RETURN)

```redis
# 只返回 title 和 author 字段（不返回 content，节省带宽）
FT.SEARCH idx:articles "Redis" RETURN 2 title author

# 配合 NOCONTENT 使用：只返回文档 ID，不返回任何字段内容
FT.SEARCH idx:articles "Redis" NOCONTENT
```

### 4.12 排序 (SORTBY)

```redis
# 按发布时间降序排列
FT.SEARCH idx:articles "Redis" SORTBY publish_date DESC

# 按点赞数升序排列
FT.SEARCH idx:articles "Redis" SORTBY likes ASC

# 多字段排序
FT.SEARCH idx:articles "Redis" SORTBY publish_date DESC SORTBY likes DESC
```

### 4.13 带评分返回 (WITHSCORES)

```redis
# 返回搜索结果的 TF-IDF 评分
FT.SEARCH idx:articles "Redis" WITHSCORES
```

### 4.14 结果高亮 (HIGHLIGHT)

```redis
# 高亮匹配的词（默认用 <b> 标签）
FT.SEARCH idx:articles "Redis" HIGHLIGHT

# 自定义高亮标签
FT.SEARCH idx:articles "Redis" HIGHLIGHT TAGS "<em>" "</em>"

# 只高亮特定字段
FT.SEARCH idx:articles "Redis" HIGHLIGHT FIELDS 1 title
```

### 4.15 结果摘要 (SUMMARIZE)

```redis
# 返回匹配片段的摘要（默认取最相关的片段）
FT.SEARCH idx:articles "Redis" SUMMARIZE

# 自定义摘要长度
FT.SEARCH idx:articles "Redis" SUMMARIZE LEN 50

# 摘要片段数
FT.SEARCH idx:articles "Redis" SUMMARIZE FRAGS 3

# 摘要分隔符
FT.SEARCH idx:articles "Redis" SUMMARIZE SEPARATOR "..."

# 组合使用：摘要 + 高亮
FT.SEARCH idx:articles "Redis" SUMMARIZE HIGHLIGHT
```

### 4.16 FILTER 过滤器

`FILTER` 是一个独立的过滤条件，不参与评分计算，适合做硬性过滤。

```redis
# 过滤 likes 在 100 到 500 之间的文档
FT.SEARCH idx:articles "Redis" FILTER likes 100 500

# 多个 FILTER 是 AND 关系
FT.SEARCH idx:articles "Redis" FILTER likes 100 500 FILTER publish_date 1704067200 +inf
```

> **FILTER vs NUMERIC 查询的区别**：`FILTER` 不参与评分计算，只做过滤，性能更好。`@likes:[100 500]` 会参与评分，适合需要根据数值相关性排序的场景。

### 4.17 INKEYS — 限定搜索范围

```redis
# 只在指定的键中搜索
FT.SEARCH idx:articles "Redis" INKEYS 2 article:1001 article:1002
```

### 4.18 INFIELDS — 限定搜索字段

```redis
# 只在 title 和 author 字段中搜索 Redis
FT.SEARCH idx:articles "Redis" INFIELDS 2 title author
```

### 4.19 EXPLAIN — 查看查询执行计划

```redis
# 查看查询的执行计划（不实际执行搜索）
FT.SEARCH idx:articles "Redis AND Search" EXPLAIN
```

### 4.20 DIALECT — 查询方言版本

```redis
# 使用最新的查询方言（推荐）
FT.SEARCH idx:articles "Redis" DIALECT 3

# 兼容旧版行为
FT.SEARCH idx:articles "Redis" DIALECT 1
```

> **重要**：Dialect 1 和 Dialect 2/3 在 TAG 查询语法上有差异。Dialect 2+ 推荐使用 `@field:{value}` 语法，Dialect 1 使用 `@field=value`。

---

## 5. 聚合查询命令 (FT.AGGREGATE)

`FT.AGGREGATE` 提供类似 SQL 的 `GROUP BY`、`COUNT`、`SUM`、`AVG`、`MIN`、`MAX`、`SORT` 等聚合操作。

### 5.1 基本语法

```redis
FT.AGGREGATE index_name query [GROUPBY count property ...] [REDUCE func nargs arg ...]
  [SORTBY count property ASC|DESC] [LIMIT offset num] [FILTER expression]
  [LOAD count property ...] [WITHCURSOR [COUNT size] [MAXIDLE time]]
```

### 5.2 COUNT — 计数

```redis
# 统计包含 "Redis" 的文档总数
FT.AGGREGATE idx:articles "*" GROUPBY 0 REDUCE COUNT 0 AS total_count
```

### 5.3 GROUP BY + COUNT — 分组计数

```redis
# 按作者分组统计文章数
FT.AGGREGATE idx:articles "*" GROUPBY 1 @author REDUCE COUNT 0 AS article_count
```

### 5.4 GROUP BY + SUM — 分组求和

```redis
# 按分类分组统计总点赞数
FT.AGGREGATE idx:articles "*" GROUPBY 1 @category REDUCE SUM 1 @likes AS total_likes
```

### 5.5 GROUP BY + COUNT_DISTINCT — 去重计数

```redis
# 按分类统计不同作者数量
FT.AGGREGATE idx:articles "*" GROUPBY 1 @category \
  REDUCE COUNT_DISTINCT 1 @author AS unique_authors
```

### 5.6 多字段分组

```redis
# 按作者和分类双维度分组
FT.AGGREGATE idx:articles "*" GROUPBY 2 @author @category \
  REDUCE COUNT 0 AS count \
  REDUCE SUM 1 @likes AS total_likes
```

### 5.7 SORTBY — 聚合结果排序

```redis
# 按文章数降序排列
FT.AGGREGATE idx:articles "*" GROUPBY 1 @author \
  REDUCE COUNT 0 AS article_count \
  SORTBY 2 @article_count DESC
```

### 5.8 FILTER — 聚合前过滤

```redis
# 只统计点赞数大于 50 的文章
FT.AGGREGATE idx:articles "*" FILTER @likes >= 50 GROUPBY 1 @author \
  REDUCE COUNT 0 AS article_count
```

### 5.9 LIMIT — 聚合分页

```redis
# 分页获取分组结果
FT.AGGREGATE idx:articles "*" GROUPBY 1 @author \
  REDUCE COUNT 0 AS article_count \
  SORTBY 2 @article_count DESC \
  LIMIT 0 10
```

### 5.10 LOAD — 加载额外字段

```redis
# 聚合时加载额外字段用于展示
FT.AGGREGATE idx:articles "*" GROUPBY 1 @author \
  REDUCE COUNT 0 AS count \
  LOAD 2 @author @category
```

### 5.11 使用游标处理大数据集

```redis
# 创建游标
FT.AGGREGATE idx:articles "*" GROUPBY 1 @author REDUCE COUNT 0 AS count WITHCURSOR COUNT 100

# 继续读取下一批（使用返回的游标 ID）
FT.CURSOR READ idx:articles <cursor_id>

# 删除游标
FT.CURSOR DEL idx:articles <cursor_id>
```

---

## 6. 自动补全命令 (FT.SUGADD / FT.SUGGET)

RediSearch 内置了轻量级的自动补全功能，基于 Trie（前缀树）实现。

### 6.1 FT.SUGADD — 添加补全建议

```redis
# 添加单个建议
FT.SUGADD sug:search "Redis Search" 1.0

# 添加带权重的建议（权重影响排序）
FT.SUGADD sug:search "Redis 入门" 0.9
FT.SUGADD sug:search "Redis 集群" 0.8
FT.SUGADD sug:search "Redis 事务" 0.7

# 添加带额外 payload 的建议
FT.SUGADD sug:search "Redis Search" 1.0 WITHPAYLOAD "url:/redis-search"
```

### 6.2 FT.SUGGET — 查询补全建议

```redis
# 获取前缀匹配的建议
FT.SUGGET sug:search "Red"
# 返回: 1) "Redis Search" 2) "Redis 入门" 3) "Redis 集群" 4) "Redis 事务"

# 限制返回数量
FT.SUGGET sug:search "Red" MAX 2
# 返回: 1) "Redis Search" 2) "Redis 入门"

# 包含评分
FT.SUGGET sug:search "Red" WITHSCORES
# 返回: 1) "Redis Search" 2) "1.0" 3) "Redis 入门" 4) "0.9" ...

# 包含 payload
FT.SUGGET sug:search "Red" WITHPAYLOADS
# 返回: 1) "Redis Search" 2) 1) "url:/redis-search" ...

# 模糊匹配
FT.SUGGET sug:search "Res" FUZZY
```

### 6.3 FT.SUGDEL — 删除补全建议

```redis
FT.SUGDEL sug:search "Redis Search"
```

### 6.4 FT.SUGLEN — 获取建议总数

```redis
FT.SUGLEN sug:search
# 返回: (integer) 4
```

---

## 7. 字典命令 (FT.DICTADD / FT.DICTDEL / FT.DICTDUMP)

字典功能用于管理搜索的分词扩展词库，常用于同义词搜索。

### 7.1 FT.DICTADD — 添加词条

```redis
# 添加同义词到字典
FT.DICTADD dict:synonyms "Redis" "RediSearch" "RedisSearch"
FT.DICTADD dict:synonyms "数据库" "DB" "Database"
```

### 7.2 FT.DICTDEL — 删除词条

```redis
FT.DICTDEL dict:synonyms "RedisSearch"
```

### 7.3 FT.DICTDUMP — 获取所有词条

```redis
FT.DICTDUMP dict:synonyms
# 返回: 1) "Redis" 2) "RediSearch" 3) "Database"
```

### 7.4 在查询中使用字典扩展

```redis
# 使用 SUGEXPAND 进行查询扩展（将查询词与字典中的词进行 OR 搜索）
FT.SEARCH idx:articles "Redis" EXPANDER dict:synonyms
```

---

## 8. 索引信息与调试命令

### 8.1 FT.INFO — 查看索引详细信息

```redis
FT.INFO idx:articles
```

返回示例：

```
 1) index_name
 2) idx:articles
 3) index_options
 4) (empty list)
 5) index_definition
 6) 1) key_type
    2) HASH
    3) prefix
    4) 1) "article:"
    5) 6) "article:"
 7) fields
 8) 1) 1) title
       2) type
       3) TEXT
       4) WEIGHT
       5) "5"
    2) 1) content
       2) type
       3) TEXT
    3) 1) author
       2) type
       3) TAG
 ...
11) num_docs
12) (integer) 1250
13) max_doc_id
14) (integer) 1250
15) num_terms
16) (integer) 8932
17) num_records
18) (integer) 18500
19) inverted_sz_mb
20) "12.456"
```

**关键字段解读**：

| 字段 | 说明 |
|------|------|
| `num_docs` | 已索引的文档数 |
| `num_terms` | 倒排索引中的唯一词项数 |
| `inverted_sz_mb` | 倒排索引占用的内存大小 (MB) |
| `num_records` | 索引记录总数 |

### 8.2 FT.GET — 按 ID 获取文档

```redis
# 直接通过文档 ID 获取索引中的文档内容
FT.GET idx:articles article:1001
```

### 8.3 FT.MGET — 批量获取文档

```redis
# 批量获取多个文档
FT.MGET idx:articles 2 article:1001 article:1002
```

### 8.4 FT.EXPLAIN — 查看查询执行计划

```redis
# 分析查询如何被执行，用于性能调优
FT.EXPLAIN idx:articles "Redis AND Search"
FT.EXPLAIN idx:articles "@likes:[100 500] @author:{张三}"
```

### 8.5 FT.EXPLAINCLI — 格式化查询执行计划

```redis
# 以更友好的格式显示执行计划
FT.EXPLAINCLI idx:articles "Redis AND Search"
```

### 8.6 FT.PROFILE — 查询性能分析

```redis
# 分析查询的执行时间和各阶段耗时
FT.PROFILE idx:articles "Redis" LIMIT 0 10
```

### 8.7 FT.TAGVALS — 获取 TAG 字段的所有值

```redis
# 获取 author 字段的所有唯一值
FT.TAGVALS idx:articles author

# 获取 category 字段的所有唯一值
FT.TAGVALS idx:articles category
```

---

## 9. 向量搜索 (KNN)

Redis Search 从 2.4 版本开始支持向量相似度搜索（KNN），这是本项目 RAG 功能的核心。

### 9.1 创建向量索引

```redis
# FLAT 算法（暴力搜索，精确但慢）
FT.CREATE idx:vector ON HASH PREFIX 1 doc: SCHEMA \
  content TEXT \
  category TAG \
  embedding VECTOR FLAT 6 dim 1536 distance_metric COSINE dtype float32

# HNSW 算法（近似搜索，快但略不精确）
FT.CREATE idx:vector ON HASH PREFIX 1 doc: SCHEMA \
  content TEXT \
  category TAG \
  embedding VECTOR HNSW 6 dim 1536 distance_metric COSINE dtype float32 \
  M 16 EF_CONSTRUCT 200 EF_RUNTIME 10
```

#### 向量索引算法对比

| 算法 | 特点 | 适用场景 |
|------|------|---------|
| `FLAT` | 暴力遍历，结果精确 | 小数据集（< 10万条），需要精确结果 |
| `HNSW` | 分层导航小世界图，近似搜索 | 大数据集，需要低延迟 |

#### HNSW 关键参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `M` | 16 | 每层最大连接数，越大越精确但内存越多 |
| `EF_CONSTRUCT` | 200 | 构建索引时的搜索宽度，越大越精确但构建越慢 |
| `EF_RUNTIME` | 10 | 查询时的搜索宽度，越大越精确但查询越慢 |

#### 距离度量

| 度量 | 说明 |
|------|------|
| `COSINE` | 余弦相似度（推荐用于文本向量） |
| `L2` | 欧几里得距离 |
| `IP` | 内积 |

### 9.2 向量搜索 (FT.SEARCH + =>[KNN])

```redis
# KNN 搜索语法：在查询末尾添加 =>{VECTOR_QUERY $vector_field $topK $query_vector}
# 使用 $param 占位符传递向量参数

# 使用 redis-cli 进行 KNN 搜索
FT.SEARCH idx:vector "*" =>{$embedding:[vector_bytes]} PARAMS 2 embedding [b64_encoded_vector] DIALECT 2
```

#### 实际使用示例（Jedis API）

```java
// Java 代码示例（类似项目中的用法）
Query query = new Query("* =>[KNN 5 @embedding $query_vec AS score]")
    .addParam("query_vec", vectorBytes)
    .returnFields("content", "category", "score")
    .setSortBy("score", true)
    .dialect(2);
SearchResult result = jedis.ftSearch("idx:vector", query);
```

### 9.3 向量搜索 + 过滤条件

```redis
# 向量搜索同时按 TAG 过滤
FT.SEARCH idx:vector "*" =>{$embedding:[vector_bytes]} PARAMS 2 embedding [b64_vector] \
  FILTER @category:{技术} DIALECT 2

# 向量搜索同时按 NUMERIC 过滤
FT.SEARCH idx:vector "*" =>{$embedding:[vector_bytes]} PARAMS 2 embedding [b64_vector] \
  FILTER @likes:[100 +inf] DIALECT 2
```

### 9.4 本项目中的向量搜索架构

项目中通过 Spring AI 的 `RedisVectorStore` 封装了向量搜索操作：

```
用户查询
  ↓
Embedding 模型（OpenAI）→ 生成查询向量
  ↓
RedisVectorStore.similaritySearch(query, topK, filter)
  ↓
底层调用 Jedis: FT.SEARCH idx:* =>[KNN topK @embedding $vec] DIALECT 2
  ↓
返回相似度最高的 topK 个文档
```

---

## 10. 与 Spring AI RedisVectorStore 的对应关系

本项目通过 Spring AI 间接使用 RediSearch，以下是高层 API 与底层命令的对应关系：

| Spring AI 操作 | 底层 RediSearch 命令 | 说明 |
|---|---|---|
| `vectorStore.add(documents)` | `HSET` + 自动索引 | 写入 Hash 数据并自动索引 |
| `vectorStore.delete(id)` | `UNLINK` + 自动更新索引 | 删除 Hash 数据并更新索引 |
| `vectorStore.delete(expression)` | `FT.SEARCH` + `UNLINK` | 按条件搜索后批量删除 |
| `vectorStore.similaritySearch(query, topK)` | `FT.SEARCH ... =>[KNN topK @embedding $vec]` | KNN 向量搜索 |
| `vectorStore.similaritySearch(query, topK, filter)` | `FT.SEARCH ... =>[KNN ...] FILTER ...` | KNN + TAG 过滤 |
| `initializeSchema(true)` | `FT.CREATE` | 创建向量索引 |
| `jedis.ftSearch(index, query)` | `FT.SEARCH` | 直接搜索（项目中 `VectorStoreInitializerService` 的用法） |

### 项目中直接使用 Jedis RediSearch API 的示例

参见 `VectorStoreInitializerService.java` 中的 `unloadDocFromRag` 方法：

```java
// 1. 使用 FT.SEARCH 查找所有匹配 source 的文档 ID
Query query = new Query("@source:{%s}".formatted(docSource))
        .limit(0, 1000)
        .setNoContent();
SearchResult searchResult = jedisPooled.ftSearch(indexName, query);

// 2. 使用 Pipeline 批量 UNLINK 删除文档
Pipeline pipeline = jedisPooled.pipelined();
for (Document doc : searchResult.getDocuments()) {
    pipeline.unlink(doc.getId());
}
pipeline.sync();
```

---

## 11. 常见问题与最佳实践

### 11.1 性能优化

| 优化项 | 建议 |
|--------|------|
| 索引字段 | 只索引需要搜索的字段，减少索引体积 |
| TAG vs TEXT | 不需要分词的字段用 TAG，性能更好 |
| FILTER vs 条件查询 | 硬性过滤用 `FILTER` 参数，不参与评分，性能更优 |
| LIMIT 分页 | 避免一次返回大量数据，使用 LIMIT 分页 |
| NOCONTENT | 如果只需要文档 ID，使用 NOCONTENT 减少网络开销 |
| SORTBY | 需要排序的字段在创建索引时标记为 `SORTABLE` |
| 向量算法 | 大数据集（> 10万条）使用 HNSW，小数据集使用 FLAT |
| RETURN | 只返回需要的字段，减少数据传输 |

### 11.2 常见错误

#### 索引已存在

```
ERR Index already exists
```

解决：先 `FT.DROPINDEX idx:name` 删除旧索引再重建。

#### 未知字段

```
ERR no such field
```

解决：检查 FT.SEARCH 查询中的字段名是否与 FT.CREATE 中定义的字段名一致。

#### TAG 语法错误

```
ERR unknown tag field
```

解决：确保 TAG 字段查询使用 `{}` 包裹值，如 `@author:{张三}`。

### 11.3 索引重建策略

```bash
# 1. 创建新索引（使用临时名称）
FT.CREATE idx:articles_v2 ON HASH PREFIX 1 article: SCHEMA ...

# 2. 等待索引自动同步完成（查看文档数）
FT.INFO idx:articles_v2  # 检查 num_docs

# 3. 切换应用使用新索引

# 4. 删除旧索引
FT.DROPINDEX idx:articles
```

### 11.4 内存管理

```redis
# 查看索引内存占用
FT.INFO idx:articles  # 查看 inverted_sz_mb, doc_table_size_mb, sortable_values_size_mb

# 查看所有索引
FT.LIST

# 清理不需要的索引
FT.DROPINDEX idx:unused
```

### 11.5 查询方言 (Dialect) 建议

| Dialect | 说明 | 建议 |
|---------|------|------|
| 1 | 旧版语法，`@field=value` | 兼容旧代码时使用 |
| 2 | 新版语法，支持向量搜索 | 向量搜索必须使用 |
| 3 | 最新版，推荐使用 | 新项目推荐 |

---

## 附录：命令速查表

| 命令 | 说明 |
|------|------|
| `FT.CREATE` | 创建搜索索引 |
| `FT.DROPINDEX` | 删除索引 |
| `FT.ALTER` | 修改索引结构 |
| `FT.LIST` | 列出所有索引 |
| `FT.INFO` | 查看索引详情 |
| `FT.SEARCH` | 全文/向量搜索 |
| `FT.AGGREGATE` | 聚合查询 |
| `FT.GET` | 获取单个文档 |
| `FT.MGET` | 批量获取文档 |
| `FT.EXPLAIN` | 查看执行计划 |
| `FT.PROFILE` | 性能分析 |
| `FT.TAGVALS` | 获取 TAG 所有值 |
| `FT.SUGADD` | 添加自动补全建议 |
| `FT.SUGGET` | 查询自动补全 |
| `FT.SUGDEL` | 删除自动补全建议 |
| `FT.SUGLEN` | 获取建议数量 |
| `FT.DICTADD` | 添加字典词条 |
| `FT.DICTDEL` | 删除字典词条 |
| `FT.DICTDUMP` | 获取所有字典词条 |
| `FT.CURSOR READ` | 读取游标 |
| `FT.CURSOR DEL` | 删除游标 |
