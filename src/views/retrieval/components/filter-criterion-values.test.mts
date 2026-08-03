import assert from 'node:assert/strict';
import test from 'node:test';
import { valuesAfterOperatorChange, valuesForRequest } from './filter-criterion-values.ts';

test('切换单值操作符时保留已经输入的值', () => {
  assert.deepEqual(
    valuesAfterOperatorChange('gt', {
      value_list: ['张三'],
      value_text: '张三',
    }),
    {
      value_list: ['张三'],
      value_text: '张三',
    },
  );
});

test('从单值切换到区间时把原值保留为起始值', () => {
  assert.deepEqual(
    valuesAfterOperatorChange('between', {
      value_list: ['2026-08-03 12:00:00'],
      value_text: '2026-08-03 12:00:00',
    }),
    {
      value_list: ['2026-08-03 12:00:00', ''],
      value_text: '2026-08-03 12:00:00',
    },
  );
});

test('从区间切回单值时保留第一个值', () => {
  assert.deepEqual(
    valuesAfterOperatorChange('lt', {
      value_list: ['10', '20'],
      value_text: '',
    }),
    {
      value_list: ['10'],
      value_text: '10',
    },
  );
});

test('无值操作符在请求中忽略值但不清空编辑状态', () => {
  const state = valuesAfterOperatorChange('isnull', {
    value_list: ['张三'],
    value_text: '张三',
  });

  assert.deepEqual(state, {
    value_list: ['张三'],
    value_text: '张三',
  });
  assert.deepEqual(valuesForRequest('isnull', state.value_list), []);
  assert.deepEqual(valuesAfterOperatorChange('eq', state), {
    value_list: ['张三'],
    value_text: '张三',
  });
});
