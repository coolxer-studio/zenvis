import axios, { AxiosRequestConfig } from 'axios';
// 引入el 提示框，这个项目里用什么组件库这里引什么
import { newMessageError } from '@/utils/tool';
import { apiResponse } from '@u/util-common';
import { ElMessage } from 'element-plus';

type RequestMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';
type RequestOptions = {
  silent?: boolean;
};

const requestClient = axios.create({
  timeout: 60000,
  baseURL: import.meta.env.VITE_BASE_URL || '/',
  withCredentials: true,
});

requestClient.interceptors.request.use(
  config => {
    // 配置请求头
    // 如果 data 不是 FormData，则设置 Content-Type 为 json
    // FormData 请求由浏览器自动设置 multipart/form-data 头（包含 boundary）
    if (!(config.data instanceof FormData)) {
      config.headers.set('Content-Type', 'application/json;charset=UTF-8');
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  },
);

requestClient.interceptors.response.use(
  response => {
    return response;
  },
  error => {
    const { response } = error;
    const silent = Boolean(error.config?.silent);
    if (response) {
      const message = response.data?.msg || response.data?.error || '服务器响应失败';
      if (!silent) {
        ElMessage.error(message);
      }
      return Promise.reject(response.data || error);
    }
    if (!silent) {
      ElMessage.error('网络连接异常,请稍后再试!');
    }
    return Promise.reject(error);
  },
);

// 封装 GET POST 请求并导出
export function request<R>(
  url = '',
  params: unknown = {},
  type: RequestMethod = 'POST',
  options: RequestOptions = {},
): Promise<R> {
  //设置 url params type 的默认值
  if (!url) {
    ElMessage.warning('无效的请求地址');
    return Promise.reject(newMessageError('无效的请求地址'));
  }
  const method = type.toUpperCase() as RequestMethod;
  if (!['GET', 'POST', 'PUT', 'DELETE'].includes(method)) {
    return Promise.reject(newMessageError('不支持的请求方法'));
  }

  const config: AxiosRequestConfig & { silent?: boolean } = { method, url, silent: options.silent };
  if (method === 'GET') {
    config.params = params;
  } else {
    config.data = params;
  }

  return requestClient(config).then(res => {
    if (!options.silent) {
      return apiResponse<R>(res);
    }
    const businessCode = res.data.status ?? res.data.code;
    if (res.status === 200 && businessCode === 0) {
      return res.data.data;
    }
    return Promise.reject(res.data);
  });
}
