<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import SourceCard from '../components/SourceCard.vue'
import { useCatalog } from '../services/catalog'
import { usePassport } from '../composables/usePassport'
import type { GuideMode } from '../types'

const route = useRoute()
const router = useRouter()
const { isSaved, toggle } = usePassport()
const { exhibits } = useCatalog()
const exhibit = computed(() => exhibits.value.find((item) => item.id === route.params.id) ?? exhibits.value[0])

function openGuide(mode: GuideMode) {
  router.push({ name: 'guide', query: { exhibit_id: exhibit.value.id, mode } })
}
</script>

<template>
  <div class="page page--narrow">
    <header class="subpage-header">
      <button type="button" class="back-link" aria-label="返回上一页" @click="router.back()"><AppIcon name="back" :size="21" /></button>
      <div><p class="eyebrow">EXHIBIT CONTEXT</p><h1>展品详情</h1></div>
      <button type="button" class="icon-button" :aria-label="isSaved(exhibit.id) ? '取消收藏' : '加入我的展册'" @click="toggle(exhibit)"><AppIcon :name="isSaved(exhibit.id) ? 'bookmark-filled' : 'bookmark'" :size="21" /></button>
    </header>

    <section class="exhibit-hero">
      <div class="exhibit-hero__visual"><img :src="exhibit.image" :alt="`${exhibit.name} 展品图片`" /><span class="exhibit-hero__tag">馆藏目录</span></div>
      <div class="exhibit-hero__meta"><span>{{ exhibit.era }}</span><i /> <span>{{ exhibit.material }}</span><i /> <span>{{ exhibit.category }}</span></div>
      <h2>{{ exhibit.name }}</h2>
      <p>{{ exhibit.summary }}</p>
    </section>

    <section class="observation-section" aria-labelledby="observation-title">
      <div class="section-heading"><div><p class="eyebrow">LOOK WITH A GUIDE</p><h2 id="observation-title">先看三个细节</h2></div><span class="number-badge number-badge--small">02</span></div>
      <div class="observation-list">
        <article v-for="(observation, index) in exhibit.observations" :key="observation.title" class="observation-card">
          <span class="observation-card__index">0{{ index + 1 }}</span>
          <div><h3>{{ observation.title }}</h3><p>{{ observation.detail }}</p></div>
          <AppIcon name="eye" :size="19" />
        </article>
      </div>
    </section>

    <section class="guide-entry" aria-labelledby="guide-entry-title">
      <div class="section-heading section-heading--compact"><div><p class="eyebrow">CHOOSE YOUR PACE</p><h2 id="guide-entry-title">怎么听这件展品？</h2></div></div>
      <div class="mode-grid">
        <button type="button" class="mode-card" @click="openGuide('quick')"><span class="mode-card__icon">01</span><strong>快速讲解</strong><small>30～60 秒，先听重点</small><AppIcon name="arrow-right" :size="16" /></button>
        <button type="button" class="mode-card mode-card--featured" @click="openGuide('deep')"><span class="mode-card__icon">02</span><strong>深入了解</strong><small>继续追问历史与工艺</small><AppIcon name="arrow-right" :size="16" /></button>
        <button type="button" class="mode-card" @click="openGuide('child')"><span class="mode-card__icon">03</span><strong>儿童讲解</strong><small>短句、观察问题、不编故事</small><AppIcon name="arrow-right" :size="16" /></button>
      </div>
    </section>

    <section class="source-section"><div class="section-heading section-heading--compact"><div><p class="eyebrow">PUBLIC EVIDENCE</p><h2>馆方资料依据</h2></div><RouterLink to="/about/sources" class="text-link">说明 <AppIcon name="arrow-right" :size="15" /></RouterLink></div><SourceCard v-for="source in exhibit.sources" :key="source.title" :source="source" /></section>
    <RouterLink to="/explore" class="button button--quiet button--full">继续探索相关展品 <AppIcon name="arrow-right" :size="17" /></RouterLink>
  </div>
</template>
