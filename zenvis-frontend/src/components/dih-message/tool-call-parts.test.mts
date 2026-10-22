import assert from 'node:assert/strict';
import test from 'node:test';
import {
  collapseLegacyToolCallParts,
  formatToolPayload,
  mergeLiveStructuredParts,
  upsertToolCallPart,
} from './tool-call-parts.ts';

test('旧版 MCP Markdown 日志合并为一张工具卡并保留结论正文', () => {
  const parts = [
    {
      type: 'markdown',
      content: '\n\n**MCP调用开始：** `lab_note_search`\n\n调用参数：\n\n',
    },
    { type: 'code', language: 'json', content: '{"keyword":"接入","limit":20}' },
    {
      type: 'markdown',
      content: '\n\n**MCP调用成功：** `lab_note_search`，耗时 41ms\n\n返回结果：\n\n',
    },
    { type: 'code', language: 'json', content: '{"rows":[],"total":0}' },
    { type: 'markdown', content: '## 结论\n\n没有匹配记录。' },
  ];

  const result = collapseLegacyToolCallParts(parts, 'message-1');

  assert.equal(result.length, 2);
  assert.equal(result[0].type, 'tool-call');
  assert.equal(result[0].status, 'succeeded');
  assert.equal(result[0].metadata?.toolName, 'lab_note_search');
  assert.equal(result[0].metadata?.arguments, '{"keyword":"接入","limit":20}');
  assert.equal(result[0].metadata?.result, '{"rows":[],"total":0}');
  assert.equal(result[0].metadata?.durationMillis, 41);
  assert.equal(result[1].type, 'markdown');
  assert.match(result[1].content || '', /没有匹配记录/);
});

test('同名工具连续调用不会被旧日志转换合并', () => {
  const parts = [
    { type: 'markdown', content: '**MCP调用开始：** `search`\n\n调用参数：' },
    { type: 'code', content: '{"q":"a"}' },
    { type: 'markdown', content: '**MCP调用成功：** `search`，耗时 10ms\n\n返回结果：' },
    { type: 'code', content: '{"total":1}' },
    { type: 'markdown', content: '**MCP调用开始：** `search`\n\n调用参数：' },
    { type: 'code', content: '{"q":"b"}' },
    { type: 'markdown', content: '**MCP调用失败：** `search`，耗时 12ms\n\n错误信息：' },
    { type: 'code', content: 'timeout' },
  ];

  const result = collapseLegacyToolCallParts(parts, 'message-2');
  const tools = result.filter(part => part.type === 'tool-call');

  assert.equal(tools.length, 2);
  assert.equal(tools[0].status, 'succeeded');
  assert.equal(tools[0].metadata?.arguments, '{"q":"a"}');
  assert.equal(tools[1].status, 'failed');
  assert.equal(tools[1].metadata?.error, 'timeout');
});

test('同一 callId 的开始与成功事件只更新一个工具部件', () => {
  const started = upsertToolCallPart([], {
    callId: 'call-1',
    toolName: 'retrieval_search',
    status: 'running',
    arguments: '{"q":"a"}',
  });
  const succeeded = upsertToolCallPart(started, {
    callId: 'call-1',
    toolName: 'retrieval_search',
    status: 'succeeded',
    arguments: '{"q":"a"}',
    result: '{"total":1}',
    durationMillis: 26,
  });

  assert.equal(succeeded.length, 1);
  assert.equal(succeeded[0].status, 'succeeded');
  assert.equal(succeeded[0].metadata?.arguments, '{"q":"a"}');
  assert.equal(succeeded[0].metadata?.result, '{"total":1}');
  assert.equal(succeeded[0].metadata?.durationMillis, 26);
});

test('最终消息缺少工具部件时保留流式工具活动', () => {
  const live = [
    { id: 'call-1', type: 'tool-call', status: 'succeeded', metadata: { toolName: 'search' } },
    { id: 'm1', type: 'markdown', content: '最终回答' },
  ];
  const final = [{ id: 'm2', type: 'markdown', content: '最终回答' }];

  const merged = mergeLiveStructuredParts(live, final);

  assert.ok(merged);
  assert.equal(merged.length, 2);
  assert.equal(merged[0].type, 'tool-call');
  assert.equal(merged[1].type, 'markdown');
  assert.equal(merged[1].content, '最终回答');
});

test('MCP text 内容块中的嵌套 JSON 会解包为标准 JSON', () => {
  const wrapped = JSON.stringify([{
    text: JSON.stringify({
      rows: [{ id: 3, title: '最后验证 Agent 能力', status: 'draft' }],
      total: 1,
    }),
  }]);

  const formatted = formatToolPayload(wrapped);

  assert.doesNotMatch(formatted, /\\"/);
  assert.doesNotMatch(formatted, /"text"/);
  assert.deepEqual(JSON.parse(formatted), {
    rows: [{ id: 3, title: '最后验证 Agent 能力', status: 'draft' }],
    total: 1,
  });
});

test('普通文本和非 JSON 返回值保持原样', () => {
  assert.equal(formatToolPayload('工具执行完成'), '工具执行完成');
  assert.equal(formatToolPayload('{invalid json'), '{invalid json');
  assert.equal(formatToolPayload('[{"text":"not json"}]'), 'not json');
});
