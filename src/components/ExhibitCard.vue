<script setup lang="ts">
import type { Exhibit } from '../types'
import AppIcon from './AppIcon.vue'

defineProps<{ exhibit: Exhibit; compact?: boolean }>()
</script>

<template>
  <RouterLink :to="`/exhibits/${exhibit.id}`" class="exhibit-card" :class="{ 'exhibit-card--compact': compact }">
    <div class="exhibit-card__visual">
      <img :src="exhibit.image" :alt="`${exhibit.name} 演示图片`" loading="lazy" />
      <span class="exhibit-card__era">{{ exhibit.era }}</span>
    </div>
    <div class="exhibit-card__body">
      <div class="eyebrow">{{ exhibit.category }}</div>
      <h3>{{ exhibit.name }}</h3>
      <p>{{ exhibit.summary }}</p>
      <span class="text-link">查看展品 <AppIcon name="arrow-right" :size="15" /></span>
    </div>
  </RouterLink>
</template>
