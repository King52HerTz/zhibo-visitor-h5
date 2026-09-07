<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from './AppIcon.vue'

const route = useRoute()
const items = [
  { label: '首页', path: '/', icon: 'home' as const },
  { label: '探索', path: '/explore', icon: 'compass' as const },
  { label: 'AI 导览', path: '/guide', icon: 'spark' as const },
  { label: '我的展册', path: '/passport', icon: 'bookmark' as const },
]

const isActive = (path: string) => computed(() => {
  if (path === '/') return route.path === path
  return route.path.startsWith(path)
}).value
</script>

<template>
  <nav class="bottom-nav" aria-label="主要导航">
    <RouterLink
      v-for="item in items"
      :key="item.path"
      :to="item.path"
      class="bottom-nav__item"
      :class="{ 'is-active': isActive(item.path) }"
    >
      <AppIcon :name="item.icon" :size="21" />
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>
