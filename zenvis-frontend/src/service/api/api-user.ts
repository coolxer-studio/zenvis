import { request } from '@/service/request-wrapper';
import {
  EncryptKeyResponse,
  LoginParams,
  LoginResponse,
  LogoutResponse,
  EditPasswordParams,
  EditPasswordResponse,
} from '@/types/type-user';

const prefix = '/api/v1/system';

export class UserService {
  static async getEncrypyKey(): Promise<EncryptKeyResponse> {
    return request<EncryptKeyResponse>(`${prefix}/login/encrypt/key`, '', 'GET');
  }

  static async doLogin(params: LoginParams): Promise<LoginResponse> {
    return request<LoginResponse>(`${prefix}/login/sign-in`, params);
  }

  static async doLogOut(): Promise<LogoutResponse> {
    return request<LogoutResponse>(`${prefix}/login/sign-out`);
  }

  static async editPassword(params: EditPasswordParams): Promise<EditPasswordResponse> {
    return request<EditPasswordResponse>(`${prefix}/user/update-password`, params);
  }
}
