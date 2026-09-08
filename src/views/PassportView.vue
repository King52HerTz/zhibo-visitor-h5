<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import ExhibitCard from '../components/ExhibitCard.vue'
import { useCatalog } from '../services/catalog'
import { usePassport } from '../composables/usePassport'

const { entries, count, clear } = usePassport()
const { exhibits } = useCatalog()
const savedExhibits = computed(() => entries.value.map((entry) => exhibits.value.find((exhibit) => exhibit.id === entry.exhibitId)).filter(Boolean))
const interests = computed(() => [...new Set(savedExhibits.value.flatMap((exhibit) => exhibit?.tags ?? []))].slice(0, 3))

function clearPassport() {
  if (window.confirm('确定清空当前浏览器中的数字展册吗？')) clear()
}
</script>

<template>
  <div class="page">
    <header class="page-header page-header--passport">
      <div><p class="eyebrow">YOUR VISIT, ON THIS DEVICE</p><h1>我的数字展册</h1><p>把看过、问过和想继续了解的文物留在这里。</p></div>
      <div class="passport-count"><strong>{{ count }}</strong><span>件展品</span></div>
    </header>

    <section class="local-note"><AppIcon name="info" :size="17" /><div><strong>只保存在当前设备</strong><p>无需注册，不跨设备同步。你可以随时清空这份记录。</p></div></section>

    <section class="passport-section" aria-labelledby="saved-title">
      <div class="section-heading section-heading--compact"><div><p class="eyebrow">SAVED EXHIBITS</p><h2 id="saved-title">我的收藏</h2></div><span class="small-muted">{{ count }} 件</span></div>
      <div v-if="savedExhibits.length" class="exhibit-list"><ExhibitCard v-for="exhibit in savedExhibits" :key="exhibit?.id" :exhibit="exhibit!" compact /></div>
      <div v-else class="empty-state empty-state--passport"><div class="empty-state__mark"><AppIcon name="bookmark" :size="28" /></div><h3>还没有收藏展品</h3><p>在展品详情或 AI 导览页点击书签，把你的观展线索留下来。</p><RouterLink to="/explore" class="button button--primary">去探索展品 <AppIcon name="arrow-right" :size="16" /></RouterLink></div>
    </section>

    <section class="passport-section"><div class="section-heading section-heading--compact"><div><p class="eyebrow">YOUR THREADS</p><h2>最感兴趣的主题</h2></div></div><div v-if="interests.length" class="interest-list"><span v-for="interest in interests" :key="interest" class="interest-tag">{{ interest }}</span></div><p v-else class="section-muted">收藏几件展品后，这里会形成你的主题线索。</p></section>

    <section class="passport-section passport-section--last"><div class="section-heading section-heading--compact"><div><p class="eyebrow">YOUR CONTROL</p><h2>记录管理</h2></div></div><button type="button" class="danger-action" :disabled="!count" @click="clearPassport"><AppIcon name="trash" :size="17" />清空本机记录</button></section>
  </div>
</template>
