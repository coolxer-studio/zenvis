import { request } from '@/service/request-wrapper';
import type { Dashboard, SystemInfo } from '@/types/type-system';

const prefix = '/api/v1/system';

type RawSystemInfo = {
  system_title?: string;
  system_subtitle?: string;
  system_icon?: string;
  system_logo?: string;
  system_banner?: string;
  product_name?: string;
  product_version?: string;
  product_introduction?: string;
  service_phone?: string;
  service_email?: string;
  technical_email?: string;
  integrate_link?: string;
  copyright?: string;
};

type RawDashboard = {
  id?: number;
  name?: string;
  code?: string;
  type?: string;
  type_description?: string;
  url?: string;
  config_index?: string;
  html_path?: string;
  is_default?: boolean | number;
  update_time?: string;
};

let systemInfoPromise: Promise<SystemInfo> | null = null;

const normalizeSystemInfo = (res: RawSystemInfo): SystemInfo => ({
  systemTitle: res.system_title || '',
  systemSubtitle: res.system_subtitle || '',
  systemIcon: res.system_icon || '',
  systemLogo: res.system_logo || '',
  systemBanner: res.system_banner || '',
  productName: res.product_name || '',
  productVersion: res.product_version || '',
  productIntroduction: res.product_introduction || '',
  servicePhone: res.service_phone || '',
  serviceEmail: res.service_email || '',
  technicalEmail: res.technical_email || '',
  integrateLink: res.integrate_link || '',
  copyright: res.copyright || '',
});

const normalizeDashboard = (item: RawDashboard): Dashboard => ({
  id: item.id || 0,
  name: item.name || '',
  code: item.code || '',
  type: item.type || '',
  typeDescription: item.type_description || '',
  url: item.url || '',
  configIndex: item.config_index || '',
  htmlPath: item.html_path || '',
  isDefault: Boolean(item.is_default),
  updateTime: item.update_time || '',
});

export class SystemService {
  static async getSystemInfo(): Promise<SystemInfo> {
    if (!systemInfoPromise) {
      systemInfoPromise = request<RawSystemInfo>(`${prefix}/about/info`, '', 'GET')
        .then(normalizeSystemInfo)
        .catch(error => {
          systemInfoPromise = null;
          return Promise.reject(error);
        });
    }
    return systemInfoPromise;
  }

  static invalidateSystemInfoCache(): void {
    systemInfoPromise = null;
  }

  static async uploadIcon(file: File): Promise<Object> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await request<Object>(`${prefix}/about/icon/upload`, formData, 'POST');
    SystemService.invalidateSystemInfoCache();
    return response;
  }

  static async uploadLogo(file: File): Promise<Object> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await request<Object>(`${prefix}/about/logo/upload`, formData, 'POST');
    SystemService.invalidateSystemInfoCache();
    return response;
  }

  static async uploadBanner(file: File): Promise<Object> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await request<Object>(`${prefix}/about/banner/upload`, formData, 'POST');
    SystemService.invalidateSystemInfoCache();
    return response;
  }

  static async updateSystemInfo(params: SystemInfo): Promise<void> {
    await request<void>(
      `${prefix}/about/info/update`,
      {
        system_title: params.systemTitle,
        system_subtitle: params.systemSubtitle,
        product_name: params.productName,
        product_version: params.productVersion,
        product_introduction: params.productIntroduction,
        copyright: params.copyright,
        service_phone: params.servicePhone,
        service_email: params.serviceEmail,
        technical_email: params.technicalEmail,
        integrate_link: params.integrateLink,
      },
      'PUT',
    );
    SystemService.invalidateSystemInfoCache();
  }

  static async getDashboardList(): Promise<Dashboard[]> {
    const response = await request<RawDashboard[]>(`${prefix}/dashboard/list`);
    return response.map(normalizeDashboard);
  }
}
