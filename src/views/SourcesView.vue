<script setup lang="ts">
import AppIcon from '../components/AppIcon.vue'
import SourceCard from '../components/SourceCard.vue'
import { demoSources } from '../mock/data'
</script>

<template>
  <div class="page page--narrow">
    <header class="subpage-header"><RouterLink to="/" class="back-link" aria-label="返回首页"><AppIcon name="back" :size="21" /></RouterLink><div><p class="eyebrow">TRANSPARENCY</p><h1>资料与可信说明</h1></div><span class="header-spacer" /></header>
    <section class="info-hero"><div class="info-hero__mark"><AppIcon name="check" :size="25" /></div><h2>知道依据，也知道边界</h2><p>智博导览不会把模型的猜测直接说成馆方事实。它先寻找资料，再把依据和不确定性告诉你。</p></section>
    <section class="principle-list"><article><span>01</span><div><h3>馆方资料优先</h3><p>回答会尽量绑定已发布资料，来源卡展示公开名称、页码和对应片段。</p></div></article><article><span>02</span><div><h3>资料不足就留白</h3><p>没有命中或证据不足时，会明确告诉你，不用流畅的语气掩盖不确定。</p></div></article><article><span>03</span><div><h3>视觉识别先核验</h3><p>拍照识展的模型只负责提出候选，最终展品上下文必须经过知识库核验和游客确认。</p></div></article></section>
    <section class="source-section"><div class="section-heading section-heading--compact"><div><p class="eyebrow">EXAMPLE SOURCES</p><h2>本次演示的来源卡</h2></div></div><SourceCard v-for="source in demoSources" :key="source.title" :source="source" /></section>
    <section class="privacy-box"><AppIcon name="info" :size="18" /><p>游客端第一版不收集姓名、手机号、人脸或精确位置。上传图片只用于识别，正式版本由游客网关负责清理。</p></section>
  </div>
</template>
