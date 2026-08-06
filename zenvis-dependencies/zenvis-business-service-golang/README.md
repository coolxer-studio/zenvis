# Zenvis Business Service Go SDK

`zenvis-business-service-golang` 是 `zenvis-business-service-spring-boot-starter` 的 Go 等价实现，用于业务服务向 Zenvis 自动注册实例、续报心跳和上报运行事件。

SDK **仅使用 Go 标准库**，不依赖 Web 框架、日志框架或第三方 UUID/HTTP 包。

## 功能

- 应用启动后立即发送 `UP` 心跳，首次心跳自动注册实例；
- 首次心跳成功后上报一次 `SERVICE_STARTED`；
- 按固定延迟周期续报心跳，默认 30 秒；
- 正常关闭时依次上报 `SERVICE_STOPPING` 和 `DOWN` 心跳；
- 自定义事件使用容量受限的单 worker 异步队列；
- 自定义事件发生在注册前时，先尝试发送注册心跳；
- 自动生成 RFC 4122 UUID v4 事件 ID；
- 对服务身份、事件类型和文本字段做与 Java Starter 一致的规范化和限长；
- 心跳 `metadata` 限制为 16 KiB，事件 `data` 限制为 64 KiB；
- 网络失败、业务响应失败和队列满不会向业务调用方传播异常；
- 支持 `ZENVIS_BUSINESS_SERVICE_*` 环境变量。

## 安装

在业务服务的 `go.mod` 中引用：

```bash
go get gitee.com/coolxer-studio/zenvis/zenvis-business-service-golang
```

代码包名为 `businessservice`。

## 最小使用

```go
package main

import (
    "context"
    "os"
    "os/signal"
    "syscall"
    "time"

    businessservice "gitee.com/coolxer-studio/zenvis/zenvis-business-service-golang"
)

func main() {
    appContext, stop := signal.NotifyContext(
        context.Background(),
        os.Interrupt,
        syscall.SIGTERM,
    )
    defer stop()

    client := businessservice.New(businessservice.Config{
        BaseURL:     "http://localhost:11001",
        ServiceCode: "order-api",
        ServiceName: "订单服务",
        Host:        "10.0.0.8",
        Port:        8080,
        Environment: "prod",
    })

    // 应在 HTTP 服务已经就绪后调用。
    client.Start(appContext)

    client.ReportEvent(
        "ORDER_SYNC_FAILED",
        businessservice.SeverityError,
        "订单同步失败",
        "下游库存接口返回 503",
        "trace-123",
        map[string]any{"downstream": "inventory-api"},
    )

    <-appContext.Done()

    shutdownContext, cancel := context.WithTimeout(context.Background(), 10*time.Second)
    defer cancel()
    _ = client.Close(shutdownContext)
}
```

`Start` 和 `Close` 均可重复调用。建议将 `Client` 作为单例注入业务组件；业务组件也可以只依赖较小的 `businessservice.Reporter` 接口。

## 环境变量配置

```go
config, err := businessservice.ConfigFromEnv()
if err != nil {
    // 配置格式非法，按宿主应用的启动失败策略处理。
    panic(err)
}
client := businessservice.New(config)
```

支持的环境变量：

| 环境变量 | 默认值/说明 |
| --- | --- |
| `ZENVIS_BUSINESS_SERVICE_ENABLED` | `true`；设为 `false` 后全部调用安全 no-op |
| `ZENVIS_BUSINESS_SERVICE_BASE_URL` | `http://localhost:11001`，不包含 `/api/v1` |
| `ZENVIS_BUSINESS_SERVICE_SERVICE_CODE` | `go-service` |
| `ZENVIS_BUSINESS_SERVICE_SERVICE_NAME` | 默认使用 `service-code` |
| `ZENVIS_BUSINESS_SERVICE_INSTANCE_ID` | 默认 `<service-code>-<host>-<port>` |
| `ZENVIS_BUSINESS_SERVICE_VERSION` | 空 |
| `ZENVIS_BUSINESS_SERVICE_ENVIRONMENT` | `default` |
| `ZENVIS_BUSINESS_SERVICE_HOST` | 本机主机名，失败时使用 `localhost` |
| `ZENVIS_BUSINESS_SERVICE_PORT` | 空；有效范围 1～65535 |
| `ZENVIS_BUSINESS_SERVICE_MANAGEMENT_URL` | 空 |
| `ZENVIS_BUSINESS_SERVICE_HEARTBEAT_INTERVAL_MILLIS` | `30000` |
| `ZENVIS_BUSINESS_SERVICE_CONNECT_TIMEOUT_MILLIS` | `2000` |
| `ZENVIS_BUSINESS_SERVICE_READ_TIMEOUT_MILLIS` | `3000` |
| `ZENVIS_BUSINESS_SERVICE_EVENT_QUEUE_CAPACITY` | `100` |
| `ZENVIS_BUSINESS_SERVICE_TIME_ZONE` | `Asia/Shanghai` |
| `ZENVIS_BUSINESS_SERVICE_METADATA` | JSON 对象，例如 `{"zone":"az-1"}` |

也可以直接构造 `Config`。未填写的基础参数会在 `New` 中补齐默认值；需要完整默认配置再局部修改时，可使用：

```go
config := businessservice.DefaultConfig()
config.ServiceCode = "order-api"
config.Port = 8080
```

## 与 Spring Boot Starter 的映射

| Spring Boot Starter | Go SDK |
| --- | --- |
| `ApplicationReadyEvent` | HTTP 服务就绪后显式调用 `Client.Start(ctx)` |
| `DisposableBean.destroy()` | 退出前显式调用 `Client.Close(ctx)`；父 context 取消也会触发关闭 |
| `BusinessServiceReporter` | `businessservice.Reporter` / `Client.ReportEvent` |
| `RestTemplate` | 标准库 `net/http` 独立客户端 |
| 单线程 `TaskExecutor` | 单 goroutine + 有界 channel |
| 固定延迟 `TaskScheduler` | `time.Timer` 固定延迟循环 |
| Spring relaxed binding | `ConfigFromEnv()` |
| `BuildProperties` / active profile / server port | Go 无框架级对应能力，需要通过 `Config` 或环境变量显式传入 |

## 失败与可靠性

SDK 是**异步、尽力上报**组件：

- Zenvis 返回非 2xx，或 JSON 响应中的 `status` 不为 `0`，视为失败；
- 事件队列满、客户端尚未 `Start`、正在关闭或传输失败时，事件可能被丢弃；
- 队列仅存在于进程内，不保证进程崩溃后的可靠交付；
- 关键事件仍应由业务应用持久化，并通过可靠消息或补偿任务重试；
- `SIGKILL`、进程崩溃或关闭超时可能导致停止事件和 `DOWN` 心跳无法送达。

公开上报协议与 Java Starter 相同：

```text
POST /api/v1/public/business-services/heartbeat
POST /api/v1/public/business-services/events
```

这两个接口不发送 `Authorization` 请求头。生产环境应在网关、防火墙或服务网络侧限制来源。

## 验证

```bash
go test -race ./...
go vet ./...
```
