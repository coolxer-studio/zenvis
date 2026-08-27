import assert from 'node:assert/strict';
import test from 'node:test';
import { advancedSqlValue } from './filter-query.ts';

test('编辑高级表达式时保留刚输入的尾部空格', () => {
  assert.equal(advancedSqlValue('advanced', 'procid>0 '), 'procid>0 ');
});

test('执行高级搜索时只清理表达式首尾空白', () => {
  assert.equal(
    advancedSqlValue('advanced', "  procid>0 and event_id='demo'  ", true),
    "procid>0 and event_id='demo'",
  );
});
