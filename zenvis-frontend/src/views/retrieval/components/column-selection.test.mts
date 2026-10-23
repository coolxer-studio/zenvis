import assert from 'node:assert/strict';
import test from 'node:test';
import {
  columnsMatchingSearch,
  getColumnSelectionState,
  toggleColumnSelection,
} from './column-selection.ts';

const columns = [
  { title: '事件ID', dataIndex: 'event_id' },
  { title: '发生时间', dataIndex: 'occurred_at' },
  { title: '访问主体', dataIndex: 'subject' },
];

test('没有搜索词时返回全部字段并保持原顺序', () => {
  assert.deepEqual(
    columnsMatchingSearch(columns, '').map(column => column.dataIndex),
    ['event_id', 'occurred_at', 'subject'],
  );
});

test('搜索只返回标题包含关键字的字段', () => {
  assert.deepEqual(
    columnsMatchingSearch(columns, '时间').map(column => column.dataIndex),
    ['occurred_at'],
  );
  assert.deepEqual(columnsMatchingSearch(columns, '不存在'), []);
});

test('根据当前可见字段计算未选、半选和全选状态', () => {
  assert.equal(getColumnSelectionState(['a', 'b'], []), 'none');
  assert.equal(getColumnSelectionState(['a', 'b'], ['a']), 'partial');
  assert.equal(getColumnSelectionState(['a', 'b'], ['a', 'b', 'hidden']), 'all');
  assert.equal(getColumnSelectionState([], ['hidden']), 'none');
});

test('全选当前可见字段时保留搜索范围外的已选字段', () => {
  assert.deepEqual(toggleColumnSelection(['hidden', 'a'], ['a', 'b']), ['hidden', 'a', 'b']);
});

test('半选时批量补齐当前可见字段', () => {
  assert.deepEqual(toggleColumnSelection(['hidden', 'a'], ['a', 'b', 'c']), [
    'hidden',
    'a',
    'b',
    'c',
  ]);
});

test('取消全选只移除当前可见字段', () => {
  assert.deepEqual(toggleColumnSelection(['hidden', 'a', 'b'], ['a', 'b']), ['hidden']);
});

test('没有可见字段时不改变当前选择', () => {
  assert.deepEqual(toggleColumnSelection(['a'], []), ['a']);
});
