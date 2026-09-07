<script setup lang="ts">
import AppIcon from '../components/AppIcon.vue'
import ExhibitCard from '../components/ExhibitCard.vue'
import { demoExhibits, demoThemes } from '../mock/data'
</script>

<template>
  <div class="page page-home">
    <header class="topbar">
      <div class="brand-lockup">
        <div class="brand-mark" aria-hidden="true">智</div>
        <div>
          <strong>智博导览</strong>
          <span>把一件文物讲给你听</span>
        </div>
      </div>
      <span class="status-pill"><span class="status-dot" />资料演示</span>
    </header>

    <section class="home-hero" aria-labelledby="home-title">
      <div class="home-hero__ornament" aria-hidden="true"><span /><span /><span /></div>
      <p class="eyebrow">MUSEUM COMPANION · MOBILE PREVIEW</p>
      <h1 id="home-title">今天，想怎么参观？</h1>
      <p class="home-hero__lead">让一件文物成为进入历史的入口。先看细节，再听懂它为什么重要。</p>
      <div class="home-hero__actions">
        <RouterLink to="/guide" class="button button--primary button--large">
          <AppIcon name="spark" :size="18" />开始 AI 导览
        </RouterLink>
        <RouterLink to="/recognize" class="button button--quiet button--large">
          <AppIcon name="camera" :size="18" />拍照识展
        </RouterLink>
      </div>
      <p class="demo-note"><AppIcon name="info" :size="15" />当前为游客端视觉原型，内容使用演示数据</p>
    </section>

    <section class="section-block" aria-labelledby="themes-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow">START WITH A THEME</p>
          <h2 id="themes-title">主题探索</h2>
        </div>
        <RouterLink to="/explore" class="text-link">全部主题 <AppIcon name="arrow-right" :size="15" /></RouterLink>
      </div>
      <div class="theme-grid">
        <RouterLink v-for="theme in demoThemes" :key="theme.name" to="/explore" class="theme-card" :class="`theme-card--${theme.tone}`">
          <span class="theme-card__line" aria-hidden="true" />
          <strong>{{ theme.name }}</strong>
          <span>{{ theme.description }}</span>
          <AppIcon name="arrow-right" :size="17" />
        </RouterLink>
      </div>
    </section>

    <section class="section-block" aria-labelledby="time-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow">MAKE IT YOUR VISIT</p>
          <h2 id="time-title">按时间参观</h2>
        </div>
      </div>
      <div class="time-grid">
        <RouterLink to="/guide?duration=15" class="time-card"><AppIcon name="clock" :size="18" /><strong>15 分钟</strong><span>快速看重点</span></RouterLink>
        <RouterLink to="/guide?duration=30" class="time-card time-card--active"><AppIcon name="clock" :size="18" /><strong>30 分钟</strong><span>连起一条线索</span></RouterLink>
        <RouterLink to="/guide?duration=deep" class="time-card"><AppIcon name="route" :size="18" /><strong>深度参观</strong><span>慢慢看、继续问</span></RouterLink>
      </div>
    </section>

    <section class="section-block section-block--last" aria-labelledby="recommend-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow">TODAY'S SUGGESTION</p>
          <h2 id="recommend-title">今日推荐</h2>
        </div>
        <span class="small-muted">演示展品</span>
      </div>
      <ExhibitCard :exhibit="demoExhibits[0]" />
    </section>
  </div>
</template>
