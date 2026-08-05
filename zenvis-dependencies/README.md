# ZenVis Dependencies

本目录集中维护供 ZenVis 生态内外部应用复用的依赖组件，包括 Spring Boot Starter、SDK、客户端库和其他接入组件。各子项目保持独立构建，并保留自己的发布坐标和使用说明。

## 当前组件

| 目录 | 作用 | 技术栈 |
| --- | --- | --- |
| [`zenvis-business-service-spring-boot-starter`](zenvis-business-service-spring-boot-starter/) | 业务应用服务心跳和事件上报组件 | Spring Boot 3.x、Java 17 |

后续新增同类依赖时，应在本目录创建独立子项目，并同步更新本清单及根目录文档。
