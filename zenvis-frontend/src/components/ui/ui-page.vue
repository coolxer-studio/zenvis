<template>
  <section class="ui-page" :class="`ui-page--${padding}`">
    <header class="ui-page__header">
      <div class="ui-page__heading">
        <h1 class="ui-page__title">{{ title }}</h1>
        <p v-if="description" class="ui-page__description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="ui-page__actions">
        <slot name="actions" />
      </div>
    </header>
    <div class="ui-page__body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string;
    description?: string;
    padding?: 'compact' | 'normal' | 'none';
  }>(),
  {
    description: '',
    padding: 'normal',
  },
);

defineOptions({ name: 'UiPage' });
</script>

<style scoped lang="scss">
.ui-page {
  --ui-page-padding: var(--zv-space-6);

  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  color: var(--zv-text-primary);
  background: var(--zv-bg-page);
}

.ui-page--compact {
  --ui-page-padding: var(--zv-space-4);
}

.ui-page--none {
  --ui-page-padding: 0px;
}

.ui-page__header {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  gap: var(--zv-space-4);
  padding: var(--zv-space-3) var(--ui-page-padding);
  border-bottom: 1px solid var(--zv-divider);
  background: var(--zv-bg-surface);
}

.ui-page--none .ui-page__header {
  padding-inline: var(--zv-space-4);
}

.ui-page__heading {
  min-width: 0;
}

.ui-page__title {
  overflow-wrap: anywhere;
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-lg);
  font-weight: var(--zv-font-weight-semibold);
  line-height: var(--zv-line-height-tight);
}

.ui-page__description {
  margin-top: var(--zv-space-1);
  overflow-wrap: anywhere;
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
  line-height: var(--zv-line-height-tight);
}

.ui-page__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--zv-space-2);
}

.ui-page__body {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: var(--ui-page-padding);
  overflow: auto;
}

@media (max-width: 767px) {
  .ui-page {
    --ui-page-padding: var(--zv-space-3);
  }

  .ui-page__header {
    align-items: flex-start;
    min-height: 56px;
  }

  .ui-page__actions {
    max-width: 48%;
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}
</style>
