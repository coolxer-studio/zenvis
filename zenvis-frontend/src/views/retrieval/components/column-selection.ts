export type ColumnSelectionState = 'none' | 'partial' | 'all';

export const columnsMatchingSearch = <T extends { title: string }>(
  columns: readonly T[],
  keyword: string,
): T[] => {
  if (!keyword) return [...columns];
  return columns.filter(column => column.title.includes(keyword));
};

export const getColumnSelectionState = (
  visibleDataIndices: readonly string[],
  selectedDataIndices: readonly string[],
): ColumnSelectionState => {
  if (!visibleDataIndices.length) return 'none';
  const selected = new Set(selectedDataIndices);
  const selectedCount = visibleDataIndices.filter(dataIndex => selected.has(dataIndex)).length;
  if (selectedCount === 0) return 'none';
  return selectedCount === visibleDataIndices.length ? 'all' : 'partial';
};

export const toggleColumnSelection = (
  selectedDataIndices: readonly string[],
  visibleDataIndices: readonly string[],
): string[] => {
  if (!visibleDataIndices.length) return [...selectedDataIndices];
  const visible = new Set(visibleDataIndices);
  const allVisibleSelected = visibleDataIndices.every(dataIndex =>
    selectedDataIndices.includes(dataIndex),
  );

  if (allVisibleSelected) {
    return selectedDataIndices.filter(dataIndex => !visible.has(dataIndex));
  }

  return [
    ...selectedDataIndices,
    ...visibleDataIndices.filter(dataIndex => !selectedDataIndices.includes(dataIndex)),
  ];
};
