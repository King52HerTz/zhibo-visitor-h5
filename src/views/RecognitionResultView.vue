<script setup lang="ts">
import AppIcon from '../components/AppIcon.vue'
import { demoExhibits, demoSources } from '../mock/data'
import gauzeRobe from '../assets/visuals/gauze-robe.webp'
</script>

<template>
  <div class="page page--narrow">
    <header class="subpage-header">
      <RouterLink to="/recognize" class="back-link" aria-label="返回拍照识展"><AppIcon name="back" :size="21" /></RouterLink>
      <div><p class="eyebrow">候选已找到</p><h1>识别结果</h1></div>
      <span class="header-spacer" />
    </header>

    <section class="recognition-summary">
      <div class="recognition-thumb"><img :src="gauzeRobe" alt="素纱单衣意象精灵" /></div>
      <div><span class="status-label status-label--review"><span class="status-dot" />待确认</span><h2>镜头看到了这些线索</h2><p>轻薄丝织物 · 交领 · 直裾形制</p></div>
    </section>

    <section class="candidate-list" aria-labelledby="candidate-title">
      <div class="section-heading section-heading--compact"><div><p class="eyebrow">馆方资料核验</p><h2 id="candidate-title">候选展品</h2></div><span class="small-muted">2 个候选</span></div>
      <article class="candidate-card candidate-card--selected">
        <div class="candidate-card__top"><span class="candidate-rank">01</span><span class="confidence confidence--high">较高</span></div>
        <h3>{{ demoExhibits[0].name }}</h3><p>{{ demoExhibits[0].era }} · {{ demoExhibits[0].material }}</p>
        <div class="candidate-card__evidence"><AppIcon name="check" :size="15" /><span>馆方资料已命中</span></div>
        <ul><li>轻薄丝织物</li><li>交领与直裾</li><li>服饰工艺资料可核验</li></ul>
        <RouterLink :to="`/exhibits/${demoExhibits[0].id}`" class="button button--primary button--full">确认并开始讲解 <AppIcon name="arrow-right" :size="17" /></RouterLink>
      </article>
      <article class="candidate-card">
        <div class="candidate-card__top"><span class="candidate-rank">02</span><span class="confidence">待确认</span></div>
        <h3>汉代丝织服饰</h3><p>西汉 · 服饰工艺</p>
        <div class="candidate-card__evidence candidate-card__evidence--muted"><AppIcon name="info" :size="15" /><span>资料存在相似描述</span></div>
        <button type="button" class="candidate-select">选择这个候选 <AppIcon name="chevron-right" :size="16" /></button>
      </article>
    </section>

    <section class="source-strip"><AppIcon name="check" :size="17" /><div><strong>为什么不是直接给答案？</strong><p>视觉模型负责提出候选，馆方资料负责核验，游客可以看到判断依据。</p></div><RouterLink to="/about/sources" aria-label="查看资料说明"><AppIcon name="chevron-right" :size="18" /></RouterLink></section>
    <RouterLink to="/recognize" class="button button--quiet button--full">重新拍摄</RouterLink>
    <p class="privacy-note">以上为双重可信识别的演示状态，正式版本会展示真实公开来源。</p>
  </div>
</template>
