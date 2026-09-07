<script setup lang="ts">
import { ref } from 'vue'
import type { Source } from '../types'
import AppIcon from './AppIcon.vue'

defineProps<{ source: Source }>()
const expanded = ref(false)
</script>

<template>
  <article class="source-card" :class="`source-card--${source.status}`">
    <button class="source-card__header" type="button" :aria-expanded="expanded" @click="expanded = !expanded">
      <span class="source-card__badge"><AppIcon name="check" :size="14" /> 资料依据</span>
      <span class="source-card__toggle">{{ expanded ? '收起' : '展开' }} <AppIcon name="chevron-right" :size="16" /></span>
    </button>
    <h3>{{ source.title }}</h3>
    <p v-if="source.page" class="source-card__page">{{ source.page }}</p>
    <p v-if="expanded" class="source-card__excerpt">{{ source.excerpt }}</p>
    <p v-else class="source-card__hint">依据馆方资料生成 · 点击查看对应片段</p>
  </article>
</template>
