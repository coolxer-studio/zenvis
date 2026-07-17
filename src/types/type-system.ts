export type SystemInfo = {
  systemTitle: string;
  systemIcon?: string;
  systemLogo?: string;
  systemBanner?: string;
  productName: string;
  productVersion: string;
  productIntroduction: string;
  servicePhone: string;
  serviceEmail: string;
  technicalEmail: string;
  integrateLink?: string;
  copyright: string;
};

export type Dashboard = {
  id: number;
  name: string;
  code?: string;
  type: string;
  typeDescription: string;
  url: string;
  configIndex: string;
  htmlPath: string;
  isDefault: boolean;
  updateTime: string;
}
