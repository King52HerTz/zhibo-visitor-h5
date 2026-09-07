<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import ExhibitCard from '../components/ExhibitCard.vue'
import { demoExhibits, demoThemes } from '../mock/data'

const query = ref('')
const selectedTheme = ref('全部')
const filters = computed(() => ['全部', ...demoThemes.map((theme) => theme.name)])
const filteredExhibits = computed(() => demoExhibits.filter((exhibit) => {
  const matchesTheme = selectedTheme.value === '全部' || exhibit.tags.includes(selectedTheme.value)
  const text = `${exhibit.name}${exhibit.category}${exhibit.summary}`
  return matchesTheme && (!query.value.trim() || text.includes(query.value.trim()))
}))
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <p class="eyebrow">EXPLORE THE COLLECTION</p>
        <h1>探索展品</h1>
        <p>先从一个主题开始，再让问题带你走得更深。</p>
      </div>
      <RouterLink to="/recognize" class="icon-button" aria-label="拍照识展"><AppIcon name="camera" :size="21" /></RouterLink>
    </header>

    <label class="search-field">
      <AppIcon name="search" :size="19" />
      <span class="sr-only">搜索展品</span>
      <input v-model="query" type="search" placeholder="搜索展品、材质或主题" />
    </label>

    <div class="filter-row" aria-label="主题筛选">
      <button v-for="filter in filters" :key="filter" type="button" class="filter-chip" :class="{ 'is-selected': selectedTheme === filter }" @click="selectedTheme = filter">
        {{ filter }}
      </button>
    </div>

    <section class="result-section" aria-live="polite">
      <div class="section-heading section-heading--compact">
        <div><p class="eyebrow">DEMO COLLECTION</p><h2>{{ filteredExhibits.length }} 件展品</h2></div>
        <span class="small-muted">已发布资料演示</span>
      </div>
      <div v-if="filteredExhibits.length" class="exhibit-list">
        <ExhibitCard v-for="exhibit in filteredExhibits" :key="exhibit.id" :exhibit="exhibit" compact />
      </div>
      <div v-else class="empty-state">
        <AppIcon name="search" :size="28" />
        <h3>还没有找到相关展品</h3>
        <p>换一个关键词，或回到全部主题继续探索。</p>
        <button type="button" class="button button--quiet" @click="query = ''; selectedTheme = '全部'">清除筛选</button>
      </div>
    </section>
  </div>
</template>
