下面这些提示词可以直接在 `/service/dih` 或右下角 AI 浮窗里发，用来分别验证不同富消息能力。

**1. 代码块测试**
````text
请严格按下面内容原样回复，不要额外解释：

这是代码块测试。

```typescript
type User = {
  id: string;
  name: string;
};

function greet(user: User) {
  return `Hello, ${user.name}`;
}
```
````

**2. 提示卡测试**
````text
请严格按下面内容原样回复，不要额外解释：

```zenvis:notice
{"title":"配置检查提醒","content":"当前操作涉及插件配置变更，请确认名称、菜单路径和推送任务配置是否正确。","level":"warning"}
```
````

**3. 确认弹框测试**
````text
请严格按下面内容原样回复，不要额外解释：

```zenvis:confirm
{"title":"是否生成插件产物","content":"确认后只记录已确认状态，不会真正执行生成或导出动作。","action":"plugin.generate.preview","target":"demo-plugin"}
```
````

**4. 混合消息测试**
````text
请严格按下面内容原样回复，不要额外解释：

下面是本次任务摘要：

- 已生成配置草案
- 已准备预览步骤
- 等待用户确认

```json
{
  "pluginName": "demo-plugin",
  "menu": "/service/demo",
  "enabled": true
}
```

```zenvis:notice
{"title":"预览可用","content":"配置草案已准备完成，可以先预览再确认。","level":"success"}
```

```zenvis:confirm
{"title":"确认应用配置草案","content":"确认后仅记录状态，不会修改真实系统配置。","action":"plugin.config.approve","target":"demo-plugin"}
```
````



**5. 非法 JSON 回退测试**
````text
请严格按下面内容原样回复，不要额外解释：

```zenvis:confirm
{"title":
```
````

预期：第 5 条不会渲染成确认卡，而是作为普通 Markdown/代码内容展示，用来验证异常格式不会把前端打崩。


请开启深度思考，分析 17*23 等于多少。先展示思考过程，最后只给出最终结果。