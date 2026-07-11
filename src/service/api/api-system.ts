import { request } from '@/service/request-wrapper';
import { SystemInfo ,Dashboard } from '@/types/type-system';
const prefix = '/api/v1/system';

export class SystemService {
  static async getSystemInfo(): Promise<SystemInfo> {
    const res = await request<SystemInfo>(`${prefix}/about/info`, '', 'GET');
    return {
      systemTitle: (res as any).system_title || '',
      systemIcon: (res as any).system_icon || '',
      systemLogo: (res as any).system_logo || '',
      systemBanner: (res as any).system_banner || '',
      productName: (res as any).product_name || '',
      productVersion: (res as any).product_version || '',
      productIntroduction: (res as any).product_introduction || '',
      servicePhone: (res as any).service_phone || '',
      serviceEmail: (res as any).service_email || '',
      technicalEmail: (res as any).technical_email || '',
      integrateLink: (res as any).integrate_link || '',
      copyright: (res as any).copyright || '',
    };
  }

  static async uploadIcon(file: File): Promise<Object> {
    const formData = new FormData();
    formData.append('file', file);
    return request<Object>(`${prefix}/about/icon/upload`, formData, 'POST');
  }

  static async uploadLogo(file: File): Promise<Object> {
    const formData = new FormData();
    formData.append('file', file);
    return  request<Object>(`${prefix}/about/logo/upload`, formData, 'POST');
  }

  static async uploadBanner(file: File): Promise<Object> {
    const formData = new FormData();
    formData.append('file', file);
    return  request<Object>(`${prefix}/about/banner/upload`, formData, 'POST');
  }

  static async updateSystemInfo(params: SystemInfo): Promise<void> {
    await request<void>(`${prefix}/about/info/update`, {
      system_title: params.systemTitle,
      product_name: params.productName,
      product_version: params.productVersion,
      product_introduction: params.productIntroduction,
      copyright: params.copyright,
      service_phone: params.servicePhone,
      service_email: params.serviceEmail,
      technical_email: params.technicalEmail,
      integrate_link: params.integrateLink,
    }, 'PUT');
  }

  static async getDashboardList() : Promise<Dashboard[]>{
    const response = await request<Array<object>>(`${prefix}/dashboard/list`);
    // 返回DashboardItem[]对象
    return response.map((item) => ({
      id: (item as any).id || 0,
      name: (item as any).name || '',
      code: (item as any).code || '',
      type: (item as any).type || '',
      typeDescription: (item as any).type_description || '',
      url: (item as any).url || '',
      configIndex: (item as any).config_index || '',
      htmlPath: (item as any).html_path || '',
      updateTime: (item as any).update_time || '',
    }))
  }
}
