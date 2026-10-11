import type { LinkCondition } from '@/types/type-retrieval';

const PLACEHOLDER_PATTERN = /\{([A-Za-z_][A-Za-z0-9_]*)\}/g;
const URI_SCHEME_PATTERN = /^[A-Za-z][A-Za-z0-9+.-]*:/;
const HTTP_URL_PATTERN = /^https?:\/\//i;
const CONTROL_CHARACTER_PATTERN = /[\u0000-\u001f\u007f]/;

function encodeLinkValue(value: unknown): string | undefined {
  if (value === null || value === undefined || value === '') return undefined;
  try {
    const serialized = typeof value === 'object' ? JSON.stringify(value) : String(value);
    return serialized === undefined || serialized === '' ? undefined : encodeURIComponent(serialized);
  } catch {
    return undefined;
  }
}

function sanitizeLinkTarget(value: string): string | undefined {
  const candidate = value.trim();
  if (
    !candidate
    || candidate.startsWith('//')
    || candidate.includes('\\')
    || CONTROL_CHARACTER_PATTERN.test(candidate)
  ) {
    return undefined;
  }
  if (URI_SCHEME_PATTERN.test(candidate) && !HTTP_URL_PATTERN.test(candidate)) {
    return undefined;
  }
  try {
    const resolved = new URL(candidate, window.location.origin);
    return resolved.protocol === 'http:' || resolved.protocol === 'https:' ? candidate : undefined;
  } catch {
    return undefined;
  }
}

function isEmptyValue(value: unknown): boolean {
  return value === null || value === undefined || value === '';
}

function stringifyValue(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }
  return String(value);
}

/**
 * 评估链接展示条件是否满足
 * @param condition 链接条件
 * @param row 当前行数据
 * @returns 条件是否满足；条件异常（如字段不存在、数值转换失败）时返回 false
 */
export function evaluateLinkCondition(
  condition: LinkCondition | undefined,
  row: Record<string, unknown>,
): boolean {
  if (!condition) return true;

  const fieldValue = row[condition.field];
  const { operator, value: conditionValue } = condition;

  switch (operator) {
    case 'equal':
      return stringifyValue(fieldValue) === conditionValue;
    case 'notequal':
      return stringifyValue(fieldValue) !== conditionValue;
    case 'isnull':
      return isEmptyValue(fieldValue);
    case 'isnotnull':
      return !isEmptyValue(fieldValue);
    case 'greatthan':
    case 'greatequalthan':
    case 'lessthan':
    case 'lessequalthan': {
      const numField = Number(fieldValue);
      const numCondition = Number(conditionValue);
      if (Number.isNaN(numField) || Number.isNaN(numCondition)) {
        return false;
      }
      switch (operator) {
        case 'greatthan':
          return numField > numCondition;
        case 'greatequalthan':
          return numField >= numCondition;
        case 'lessthan':
          return numField < numCondition;
        case 'lessequalthan':
          return numField <= numCondition;
      }
      return false;
    }
    default:
      return false;
  }
}

export function resolveRetrievalLink(
  template: string | undefined,
  row: Record<string, unknown>,
  condition?: LinkCondition,
): string | undefined {
  if (typeof template !== 'string' || !template.trim()) return undefined;

  // 先判断条件，不满足直接返回
  if (!evaluateLinkCondition(condition, row)) {
    return undefined;
  }

  let invalidPlaceholder = false;
  const resolvedTemplate = template.replace(PLACEHOLDER_PATTERN, (_match, attribute: string) => {
    if (!Object.prototype.hasOwnProperty.call(row, attribute)) {
      invalidPlaceholder = true;
      return '';
    }
    const encodedValue = encodeLinkValue(row[attribute]);
    if (encodedValue === undefined) {
      invalidPlaceholder = true;
      return '';
    }
    return encodedValue;
  });
  if (invalidPlaceholder || resolvedTemplate.includes('{') || resolvedTemplate.includes('}')) {
    return undefined;
  }
  return sanitizeLinkTarget(resolvedTemplate);
}
