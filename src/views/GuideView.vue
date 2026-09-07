<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import SourceCard from '../components/SourceCard.vue'
import { demoExhibits, demoQuestions, demoSources } from '../mock/data'
import { usePassport } from '../composables/usePassport'
import type { GuideMode } from '../types'

type Message = { id: number; role: 'guide' | 'visitor'; text: string }

const route = useRoute()
const router = useRouter()
const { toggle, isSaved } = usePassport()
const selectedMode = ref<GuideMode>((route.query.mode as GuideMode) || 'quick')
const input = ref('')
const isSending = ref(false)
const messageList = ref<Message[]>([])
const messageArea = ref<HTMLElement | null>(null)
let nextMessageId = 2

const exhibit = computed(() => {
  const id = typeof route.query.exhibit_id === 'string' ? route.query.exhibit_id : 'demo-su-sha'
  return demoExhibits.find((item) => item.id === id) ?? demoExhibits[0]
})

const modeMeta: Record<GuideMode, { label: string; hint: string }> = {
  quick: { label: '快速讲解', hint: '先听重点' },
  deep: { label: '深入了解', hint: '继续追问' },
  child: { label: '儿童讲解', hint: '边看边发现' },
}

const answerByMode: Record<GuideMode, string> = {
  quick: '先看衣身的薄透程度，再看交领与直裾。它们分别帮助我们理解丝织工艺和汉代服饰形制。',
  deep: '这件衣物最适合从“材料”和“形制”两条线索一起看：轻薄的织物让我们注意到丝织技术，交领与直裾则把它放回汉代服饰制度与日常生活中。',
  child: '我们先来找一找：衣服的领子在哪里？再看看它为什么看起来这么轻薄。每个小细节，都是古人做衣服时留下的线索。',
}

function resetMessages() {
  messageList.value = [{ id: 1, role: 'guide', text: answerByMode[selectedMode.value] }]
}

function setMode(mode: GuideMode) {
  selectedMode.value = mode
  resetMessages()
}

function scrollMessages() {
  nextTick(() => {
    if (messageArea.value) messageArea.value.scrollTop = messageArea.value.scrollHeight
  })
}

function answerFor(question: string) {
  if (question.includes('薄')) return '馆方资料把它的轻薄感与丝织材料、织造工艺联系起来。这里我们可以确认的是资料中的材质和形制；具体工艺细节仍要以展签与公开研究为准。'
  if (question.includes('不同')) return '可以从三个角度比较：材料是否轻薄、领襟如何组织、纹样和工艺如何呈现。我们目前的演示资料支持前两项，关于完整比较需要更多已发布展品数据。'
  return '这个问题很适合从展品细节开始。你可以先观察衣身边缘和领口，我会根据当前已核验的馆方资料继续讲解。'
}

function sendQuestion(question = input.value) {
  const text = question.trim()
  if (!text || isSending.value) return
  input.value = ''
  messageList.value.push({ id: nextMessageId++, role: 'visitor', text })
  isSending.value = true
  scrollMessages()
  window.setTimeout(() => {
    messageList.value.push({ id: nextMessageId++, role: 'guide', text: answerFor(text) })
    isSending.value = false
    scrollMessages()
  }, 650)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendQuestion()
  }
}

watch(() => route.query.mode, (mode) => {
  if (mode === 'quick' || mode === 'deep' || mode === 'child') setMode(mode)
})

resetMessages()
</script>

<template>
  <div class="page guide-page">
    <header class="guide-header">
      <button type="button" class="back-link" aria-label="返回展品详情" @click="router.back()"><AppIcon name="back" :size="21" /></button>
      <div class="guide-header__title"><span class="status-dot" /><div><p class="eyebrow">AI GUIDE · DEMO</p><h1>正在讲解</h1></div></div>
      <button type="button" class="icon-button" :aria-label="isSaved(exhibit.id) ? '取消收藏' : '加入展册'" @click="toggle(exhibit, selectedMode)"><AppIcon :name="isSaved(exhibit.id) ? 'bookmark-filled' : 'bookmark'" :size="20" /></button>
    </header>

    <section class="guide-context">
      <div class="guide-context__image"><img :src="exhibit.image" :alt="`${exhibit.name} 演示图片`" /></div>
      <div><span class="eyebrow">当前展品</span><h2>{{ exhibit.name }}</h2><p>{{ exhibit.era }} · {{ exhibit.category }}</p></div>
      <RouterLink :to="`/exhibits/${exhibit.id}`" class="context-link" aria-label="查看展品详情"><AppIcon name="chevron-right" :size="18" /></RouterLink>
    </section>

    <div class="mode-switcher" role="tablist" aria-label="讲解模式">
      <button v-for="(meta, mode) in modeMeta" :key="mode" type="button" role="tab" :aria-selected="selectedMode === mode" :class="{ 'is-active': selectedMode === mode }" @click="setMode(mode as GuideMode)">{{ meta.label }}</button>
    </div>

    <section ref="messageArea" class="guide-messages" aria-live="polite">
      <div v-for="message in messageList" :key="message.id" class="message" :class="`message--${message.role}`">
        <div v-if="message.role === 'guide'" class="guide-avatar" aria-hidden="true">智</div>
        <div class="message__bubble"><span v-if="message.role === 'guide'" class="message__label">智博导览 · {{ modeMeta[selectedMode].hint }}</span><p>{{ message.text }}</p></div>
      </div>
      <div v-if="isSending" class="message message--guide"><div class="guide-avatar" aria-hidden="true">智</div><div class="message__bubble message__bubble--loading"><span class="button-loader" />正在查找馆方资料</div></div>
    </section>

    <section class="observation-strip"><div class="observation-strip__title"><AppIcon name="eye" :size="18" /><strong>重点观察</strong></div><div class="observation-mini-list"><span v-for="(item, index) in exhibit.observations.slice(0, 2)" :key="item.title"><b>{{ index + 1 }}</b>{{ item.title }}</span></div></section>

    <section class="guide-sources"><div class="section-heading section-heading--compact"><div><p class="eyebrow">WHY TRUST THIS</p><h2>馆方资料依据</h2></div><span class="evidence-badge"><AppIcon name="check" :size="13" />已核验</span></div><SourceCard v-for="source in demoSources" :key="source.title" :source="source" /></section>

    <section class="suggestion-section"><p class="eyebrow">KEEP EXPLORING</p><div class="suggestion-list"><button v-for="question in demoQuestions" :key="question" type="button" class="suggestion-chip" @click="sendQuestion(question)">{{ question }} <AppIcon name="arrow-right" :size="14" /></button></div></section>

    <form class="guide-composer" @submit.prevent="sendQuestion()">
      <label for="guide-question" class="sr-only">向导览员提问</label>
      <textarea id="guide-question" v-model="input" rows="1" maxlength="300" placeholder="问问这件展品……" @keydown="onKeydown" />
      <button type="submit" class="send-button" :disabled="!input.trim() || isSending" aria-label="发送问题"><AppIcon name="send" :size="18" /></button>
    </form>
    <p class="guide-disclaimer">AI 生成内容可能存在误差，请以馆方正式说明为准。</p>
  </div>
</template>
