export type TStandardResponse<T> = {
  code?: number;
  status?: number;
  data: T;
  msg: string;
};
