export type TPromiseCallback<T> = (data: T) => void;

export type TFn<T, R> = (data: T) => R;

// eslint-disable-next-line @typescript-eslint/no-empty-function
export const FVoidFn = (): void => {};

export const FPromise = () => {
  return new Promise<void>(resolve => resolve());
};

export type TCustomRender = {
  index: number;
};

export type TQuery = {
  search: string;
};
export type TPage = {
  size: number;
  page: number;
};
export type TPagination = {
  current: number;
  pageSize: number;
  total: number;
  showSizeChanger?: boolean;
  pageSizeOptions?: string[];
  showTotal?: TFn<number, void>;
  showQuickJumper?: boolean;
};
export type listResponse<T> = {
  total?: number;
  datalist: T[];
};

export type TColumns = {
  [key: string]: unknown;
};

export type TTable<T> = {
  columns: TColumns[];
  data: T[];
  pagination: TPagination;
};

export type TDynamicTableParams = {
  sourceColumns: TColumns[];
  disabledTitles: string[];
  selectedCol: TColumns[];
  selectedKeyCol?: string[];
  entity?: string;
  minWidth?: number,
  width?: number;
};
export type TTableNoPagination<T> = {
  columns: TColumns[];
  data: T[];
};
