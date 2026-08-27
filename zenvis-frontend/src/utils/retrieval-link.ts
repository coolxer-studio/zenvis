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

export function resolveRetrievalLink(
  template: string | undefined,
  row: Record<string, unknown>,
): string | undefined {
  if (typeof template !== 'string' || !template.trim()) return undefined;
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
