<template>
  <section class="ui-section" :class="`ui-section--${padding}`">
    <header v-if="title || description || $slots.actions" class="ui-section__header">
      <div class="ui-section__heading">
        <h2 v-if="title" class="ui-section__title">{{ title }}</h2>
        <p v-if="description" class="ui-section__description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="ui-section__actions">
        <slot name="actions" />
      </div>
    </header>
    <div class="ui-section__body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string;
    description?: string;
    padding?: 'compact' | 'normal' | 'none';
  }>(),
  {
    title: '',
    description: '',
    padding: 'normal',
  },
);

defineOptions({ name: 'UiSection' });
</script>

<style scoped lang="scss">
.ui-section {
  --ui-section-padding: var(--zv-space-5);

  min-width: 0;
  border: 1px solid var(--zv-border);
  border-radius: var(--zv-radius-md);
  background: var(--zv-bg-surface);
  box-shadow: var(--zv-shadow-1);
}

.ui-section--compact {
  --ui-section-padding: var(--zv-space-4);
}

.ui-section--none {
  --ui-section-padding: 0px;
}

.ui-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 52px;
  gap: var(--zv-space-4);
  padding: var(--zv-space-3) var(--ui-section-padding);
  border-bottom: 1px solid var(--zv-divider);
}

.ui-section--none .ui-section__header {
  padding-inline: var(--zv-space-4);
}

.ui-section__heading {
  min-width: 0;
}

.ui-section__title {
  color: var(--zv-text-primary);
  font-size: var(--zv-font-size-md);
  font-weight: var(--zv-font-weight-semibold);
  line-height: var(--zv-line-height-tight);
}

.ui-section__description {
  margin-top: var(--zv-space-1);
  color: var(--zv-text-muted);
  font-size: var(--zv-font-size-xs);
  line-height: var(--zv-line-height-normal);
}

.ui-section__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--zv-space-2);
}

.ui-section__body {
  min-width: 0;
  padding: var(--ui-section-padding);
}

@media (max-width: 767px) {
  .ui-section {
    --ui-section-padding: var(--zv-space-3);
  }
}
</style>
