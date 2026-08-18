import { App } from 'vue';
import layout_blank from './layout/layout-blank.vue';
import layout_full from './layout/layout-full.vue';
import layout_header from './layout/layout-header.vue';
import UiPage from './ui/ui-page.vue';
import UiSection from './ui/ui-section.vue';
import UiStatusBadge from './ui/ui-status-badge.vue';

const components = [
  layout_blank,
  layout_full,
  layout_header,
  UiPage,
  UiSection,
  UiStatusBadge,
];

export default function (app: App) {
  components.forEach(item => {
    if (item.name) {
      app.component(item.name, item);
    }
  });
}
