<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import BottomNav from './components/BottomNav.vue'
import CatalogStatus from './components/CatalogStatus.vue'

const route = useRoute()
const showBottomNav = computed(() => !route.path.startsWith('/recognitions') && !route.path.startsWith('/recognize') && !route.path.startsWith('/offline'))
</script>

<template>
  <div class="app-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <main id="main-content" class="app-main">
      <CatalogStatus v-if="showBottomNav" />
      <RouterView />
    </main>
    <BottomNav v-if="showBottomNav" />
  </div>
</template>
