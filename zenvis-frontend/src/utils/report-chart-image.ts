import * as echarts from 'echarts';
import { chooseChartImageDataUrl, normalizeChartOption } from './report-chart-library';

export const renderChartImageDataUrl = async (value: unknown) => {
  const option = normalizeChartOption(value);
  if (!option) throw new Error('图表缺少可渲染的 ECharts 配置');

  const container = document.createElement('div');
  container.style.position = 'fixed';
  container.style.left = '-10000px';
  container.style.top = '0';
  container.style.width = '960px';
  container.style.height = '540px';
  container.setAttribute('aria-hidden', 'true');
  document.body.appendChild(container);

  let chart: echarts.ECharts | undefined;
  try {
    chart = echarts.init(container, undefined, {
      renderer: 'canvas',
      width: 960,
      height: 540,
    });
    chart.setOption(
      {
        ...option,
        animation: false,
        backgroundColor: '#fff',
      },
      true,
    );
    chart.resize();
    await new Promise<void>(resolve => {
      window.requestAnimationFrame(() => window.requestAnimationFrame(() => resolve()));
    });
    const png = chart.getDataURL({
      type: 'png',
      pixelRatio: 2,
      backgroundColor: '#fff',
    });
    const jpeg = chart.getDataURL({
      type: 'jpeg',
      pixelRatio: 2,
      quality: 0.86,
      backgroundColor: '#fff',
    } as Parameters<typeof chart.getDataURL>[0]);
    return chooseChartImageDataUrl(png, jpeg);
  } finally {
    chart?.dispose();
    container.remove();
  }
};
