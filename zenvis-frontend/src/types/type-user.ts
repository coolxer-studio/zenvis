// 用户相关类型定义

// 加密密钥响应
export type EncryptKeyResponse = {
  key: string;
  timestamp?: number;
};

// 登录请求参数
export type LoginParams = {
  username?: string;
  user_name?: string;
  password: string;
  captcha?: string;
  auth_code?: string;
  key?: string;
  [key: string]: unknown;
};

// 登录响应
export type LoginResponse = {
  token: string;
  username: string;
  role?: string;
  permissions?: string[];
};

// 登出响应
export type LogoutResponse = {
  success: boolean;
  message?: string;
};

// 修改密码参数
export type EditPasswordParams = {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
};

// 修改密码响应
export type EditPasswordResponse = {
  success: boolean;
  message?: string;
};
