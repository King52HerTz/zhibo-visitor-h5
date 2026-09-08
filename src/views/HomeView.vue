<script setup lang="ts">
import AppIcon from '../components/AppIcon.vue'
import ExhibitCard from '../components/ExhibitCard.vue'
import { useCatalog } from '../services/catalog'
import museumPortal from '../assets/visuals/museum-portal.webp'
import silkBanner from '../assets/visuals/silk-banner.webp'
import gauzeRobe from '../assets/visuals/gauze-robe.webp'
import lacquerCup from '../assets/visuals/lacquer-cup.webp'

const visualThemes = [
  { name: '帛画里的宇宙', description: '从天上、人间到地下，看汉代人的想象', image: silkBanner, tone: 'sky' },
  { name: '素纱的轻盈', description: '沿着材质与织造，读懂一件衣服', image: gauzeRobe, tone: 'apricot' },
  { name: '漆器的流云', description: '在黑地与云气纹里寻找观看线索', image: lacquerCup, tone: 'mint' },
]
const { exhibits, contentLabel } = useCatalog()
</script>

<template>
  <div class="page page-home">
    <header class="topbar">
      <div class="brand-lockup"><div class="brand-mark" aria-hidden="true">湘</div><div><strong>智博导览</strong><span>湖南博物院探索助手</span></div></div>
      <RouterLink to="/about/sources" class="status-pill"><span class="status-dot" />资料可追溯</RouterLink>
    </header>

    <section class="home-hero" aria-labelledby="home-title">
      <div class="home-hero__content">
        <p class="home-kicker">HUNAN · 穿过时间之门</p>
        <h1 id="home-title">一眼入湘，<br />千年有话说。</h1>
        <p class="home-hero__lead">从马王堆的想象力出发，让眼前的细节成为你的参观路线。</p>
      </div>
      <img class="home-hero__portal" :src="museumPortal" alt="由湖南博物院漂浮屋顶意象转化的探索入口" />
      <span class="home-hero__spark home-hero__spark--one" aria-hidden="true" />
      <span class="home-hero__spark home-hero__spark--two" aria-hidden="true" />
    </section>

    <section class="home-actions" aria-label="开始参观">
      <RouterLink to="/recognize" class="home-action home-action--primary">
        <span class="home-action__icon"><AppIcon name="camera" :size="22" /></span>
        <span><strong>拍照识展</strong><small>镜头提出候选，馆方资料核验</small></span>
        <AppIcon name="arrow-right" :size="20" />
      </RouterLink>
      <RouterLink to="/guide" class="home-action">
        <span class="home-action__icon"><AppIcon name="spark" :size="22" /></span>
        <span><strong>问问 AI 导览</strong><small>从一个问题开始自由参观</small></span>
        <AppIcon name="arrow-right" :size="20" />
      </RouterLink>
    </section>

    <section class="section-block" aria-labelledby="themes-title">
      <div class="section-heading">
        <div><p class="section-index">01 · 按兴趣</p><h2 id="themes-title">沿着一条线索看展</h2></div>
        <RouterLink to="/explore" class="text-link">全部主题 <AppIcon name="arrow-right" :size="15" /></RouterLink>
      </div>
      <div class="theme-grid theme-grid--visual">
        <RouterLink v-for="(theme, index) in visualThemes" :key="theme.name" to="/explore" class="theme-card theme-card--visual" :class="`theme-card--${theme.tone}`">
          <span class="theme-card__copy"><small>0{{ index + 1 }}</small><strong>{{ theme.name }}</strong><span>{{ theme.description }}</span></span>
          <img :src="theme.image" alt="" loading="lazy" />
          <span class="theme-card__arrow"><AppIcon name="arrow-right" :size="18" /></span>
        </RouterLink>
      </div>
    </section>

    <section class="section-block" aria-labelledby="time-title">
      <div class="section-heading"><div><p class="section-index">02 · 按时间</p><h2 id="time-title">把节奏交给你</h2></div></div>
      <div class="time-grid">
        <RouterLink to="/guide?duration=15" class="time-card"><strong>15</strong><span>分钟速览</span></RouterLink>
        <RouterLink to="/guide?duration=30" class="time-card time-card--active"><strong>30</strong><span>分钟精选</span></RouterLink>
        <RouterLink to="/guide?duration=deep" class="time-card"><AppIcon name="route" :size="24" /><span>深度参观</span></RouterLink>
      </div>
    </section>

    <section class="section-block section-block--last" aria-labelledby="recommend-title">
      <div class="section-heading">
        <div><p class="section-index">03 · 今日一件</p><h2 id="recommend-title">停下来，仔细看</h2></div>
        <span class="small-muted">{{ contentLabel }}</span>
      </div>
      <ExhibitCard v-if="exhibits[0]" :exhibit="exhibits[0]" />
    </section>

    <p class="demo-note demo-note--home"><AppIcon name="info" :size="16" />当前为首版内容目录，正式讲解以可追溯馆方资料为准。</p>
  </div>
</template>
