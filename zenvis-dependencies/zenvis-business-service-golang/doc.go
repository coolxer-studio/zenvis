// Package businessservice 提供 Zenvis 业务服务注册、心跳和事件上报 SDK。
//
// SDK 仅使用 Go 标准库，不依赖第三方框架。调用 New 创建客户端，应用就绪后调用
// Start，并在正常退出前调用 Close。
package businessservice
