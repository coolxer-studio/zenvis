import assert from 'node:assert/strict';
import test from 'node:test';
import {
  readAgentRunCursor,
  resolveAgentRunReplayCursor,
  shouldAcceptAgentRunEvent,
  writeAgentRunCursor,
} from './agent-run-replay.ts';

const memoryStorage = () => {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
  };
};

test('事件按 sequence 去重，乱序和重复事件不会重复应用', () => {
  let cursor = 0;
  const accepted: number[] = [];

  [2, 1, 3, 3, 4].forEach(sequence => {
    const event = { sequence, run_id: 'run-1', type: 'assistant.delta' };
    if (shouldAcceptAgentRunEvent(cursor, event)) {
      cursor = event.sequence;
      accepted.push(sequence);
    }
  });

  assert.deepEqual(accepted, [2, 3, 4]);
  assert.equal(cursor, 4);
});

test('重开页面时从已持久化游标续订', () => {
  const storage = memoryStorage();
  writeAgentRunCursor('run-1', 17, storage);

  assert.equal(readAgentRunCursor('run-1', storage), 17);
  assert.equal(resolveAgentRunReplayCursor(null, 'run-1', 3, readAgentRunCursor('run-1', storage)), 17);
});

test('服务端快照和请求游标领先时不会回退导致事件丢失', () => {
  const snapshot = { runId: 'run-1', lastEventSequence: 25 } as any;

  assert.equal(resolveAgentRunReplayCursor(snapshot, 'run-1', 12, 9), 25);
  assert.equal(resolveAgentRunReplayCursor(null, 'run-2', 12, 20), 20);
});

test('关闭连接只停止订阅，不清除可恢复游标', () => {
  const storage = memoryStorage();
  writeAgentRunCursor('run-1', 31, storage);

  assert.equal(readAgentRunCursor('run-1', storage), 31);
});
