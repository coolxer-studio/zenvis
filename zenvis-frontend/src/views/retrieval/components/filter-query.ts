export const advancedSqlValue = (type: 'normal' | 'advanced', sql: string, normalize = false) => {
  if (type !== 'advanced') return undefined;
  return normalize ? sql.trim() : sql;
};
