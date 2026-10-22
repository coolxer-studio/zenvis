import assert from 'node:assert/strict';
import test from 'node:test';
import {
  normalizeChatSessionId,
  resolveChatSessionId,
  shouldCreateChatSession,
} from './chat-session-id.ts';

test('空 props 和空 route 时仍生成非空会话ID', () => {
  const context = {
    currentSessionId: '',
    propsSessionId: '',
    routeSessionId: null,
  };

  assert.equal(resolveChatSessionId(context, () => 'generated-session-id'), 'generated-session-id');
  assert.equal(shouldCreateChatSession(context), true);
});

test('优先复用已有会话ID而不是重复生成', () => {
  assert.equal(resolveChatSessionId({
    currentSessionId: ' current-id ',
    propsSessionId: 'props-id',
    routeSessionId: 'route-id',
  }, () => 'generated-id'), 'current-id');

  assert.equal(resolveChatSessionId({
    propsSessionId: 'props-id',
    routeSessionId: 'route-id',
  }, () => 'generated-id'), 'props-id');
});

test('路由会话ID兼容数组参数且不应标记为新会话', () => {
  const context = {
    routeSessionId: ['route-id', 'duplicate-id'],
  };

  assert.equal(normalizeChatSessionId(context.routeSessionId), 'route-id');
  assert.equal(resolveChatSessionId(context, () => 'generated-id'), 'route-id');
  assert.equal(shouldCreateChatSession(context), false);
});
