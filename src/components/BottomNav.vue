<script setup lang="ts">
import { useRoute } from 'vue-router'
import homeIcon from '../assets/visuals/nav-home.webp'
import exploreIcon from '../assets/visuals/nav-explore.webp'
import guideIcon from '../assets/visuals/nav-guide.webp'
import passportIcon from '../assets/visuals/nav-passport.webp'

const route = useRoute()
const items = [
  { label: '首页', path: '/', icon: homeIcon, tone: 'home' },
  { label: '探索', path: '/explore', icon: exploreIcon, tone: 'explore' },
  { label: 'AI 导览', path: '/guide', icon: guideIcon, tone: 'guide' },
  { label: '我的展册', path: '/passport', icon: passportIcon, tone: 'passport' },
]

function isActive(path: string) {
  if (path === '/') return route.path === path
  return route.path.startsWith(path)
}
</script>

<template>
  <nav class="bottom-nav" aria-label="主要导航">
    <RouterLink
      v-for="item in items"
      :key="item.path"
      :to="item.path"
      class="bottom-nav__item"
      :class="[`bottom-nav__item--${item.tone}`, { 'is-active': isActive(item.path) }]"
    >
      <span class="bottom-nav__icon" aria-hidden="true"><img :src="item.icon" alt="" /></span>
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>
