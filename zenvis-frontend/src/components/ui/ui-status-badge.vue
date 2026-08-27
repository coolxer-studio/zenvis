<template>
  <span class="ui-status-badge" :class="`ui-status-badge--${tone}`">
    <span v-if="dot" class="ui-status-badge__dot" aria-hidden="true" />
    <span>{{ label }}</span>
  </span>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string;
    tone?: 'neutral' | 'info' | 'success' | 'warning' | 'danger';
    dot?: boolean;
  }>(),
  {
    tone: 'neutral',
    dot: false,
  },
);

defineOptions({ name: 'UiStatusBadge' });
</script>

<style scoped lang="scss">
.ui-status-badge {
  --ui-badge-color: var(--zv-text-secondary);
  --ui-badge-bg: var(--zv-bg-subtle);
  --ui-badge-border: var(--zv-border);

  display: inline-flex;
  align-items: center;
  max-width: 100%;
  min-height: 24px;
  gap: 6px;
  padding: 2px 8px;
  border: 1px solid var(--ui-badge-border);
  border-radius: var(--zv-radius-round);
  color: var(--ui-badge-color);
  background: var(--ui-badge-bg);
  font-size: var(--zv-font-size-xs);
  font-weight: var(--zv-font-weight-medium);
  line-height: 18px;
  vertical-align: middle;
}

.ui-status-badge--info {
  --ui-badge-color: var(--zv-info);
  --ui-badge-bg: var(--zv-info-soft);
  --ui-badge-border: #bfd2f2;
}

.ui-status-badge--success {
  --ui-badge-color: var(--zv-success);
  --ui-badge-bg: var(--zv-success-soft);
  --ui-badge-border: #b9e2d4;
}

.ui-status-badge--warning {
  --ui-badge-color: var(--zv-warning);
  --ui-badge-bg: var(--zv-warning-soft);
  --ui-badge-border: #efd39d;
}

.ui-status-badge--danger {
  --ui-badge-color: var(--zv-danger);
  --ui-badge-bg: var(--zv-danger-soft);
  --ui-badge-border: #efbdc7;
}

.ui-status-badge__dot {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: currentcolor;
  box-shadow: 0 0 0 2px color-mix(in srgb, currentcolor 14%, transparent);
}
</style>
