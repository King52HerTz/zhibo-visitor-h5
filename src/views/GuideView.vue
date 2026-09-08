<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import SourceCard from '../components/SourceCard.vue'
import { demoQuestions } from '../mock/data'
import { useCatalog } from '../services/catalog'
import { usePassport } from '../composables/usePassport'
import type { GuideMode, Source } from '../types'
import { streamGuide, type GatewayEvent } from '../services/visitorGateway'
import silkBanner from '../assets/visuals/silk-banner.webp'

type Message = { id: number; role: 'guide' | 'visitor'; text: string; sources?: Source[]; outcome?: string }

const route = useRoute()
const router = useRouter()
const { toggle, isSaved } = usePassport()
const { exhibits } = useCatalog()
const selectedMode = ref<GuideMode>((route.query.mode as GuideMode) || 'quick')
const input = ref('')
const isSending = ref(false)
const loadingLabel = ref('正在查找馆方资料')
const messageList = ref<Message[]>([])
const messageArea = ref<HTMLElement | null>(null)
let abortController: AbortController | null = null
let nextMessageId = 2

const exhibit = computed(() => {
  const id = typeof route.query.exhibit_id === 'string' ? route.query.exhibit_id : 'demo-su-sha'
  return exhibits.value.find((item) => item.id === id) ?? exhibits.value[0]
})
const recognizedName = computed(() => typeof route.query.recognized_name === 'string' ? route.query.recognized_name.slice(0, 80) : '')

const modeMeta: Record<GuideMode, { label: string; hint: string }> = {
  quick: { label: '快速讲解', hint: '先听重点' },
  deep: { label: '深入了解', hint: '继续追问' },
  child: { label: '儿童讲解', hint: '边看边发现' },
}

function setMode(mode: GuideMode) {
  selectedMode.value = mode
}

function scrollMessages() {
  nextTick(() => {
    if (messageArea.value) messageArea.value.scrollTop = messageArea.value.scrollHeight
  })
}

async function sendQuestion(question = input.value) {
  const text = question.trim()
  if (!text || isSending.value) return
  input.value = ''
  messageList.value.push({ id: nextMessageId++, role: 'visitor', text })
  const answer: Message = { id: nextMessageId++, role: 'guide', text: '', sources: [] }
  messageList.value.push(answer)
  isSending.value = true
  loadingLabel.value = '正在查找馆方资料'
  abortController?.abort()
  abortController = new AbortController()
  scrollMessages()
  try {
    await streamGuide(
      {
        message: recognizedName.value ? `关于候选展品“${recognizedName.value}”：${text}` : text,
        mode: selectedMode.value,
        museum: '湖南省博物馆',
        exhibitId: recognizedName.value ? undefined : exhibit.value.id,
      },
      (event: GatewayEvent) => {
        if (event.type === 'status') loadingLabel.value = event.data.message || loadingLabel.value
        if (event.type === 'content') answer.text += event.data.delta || ''
        if (event.type === 'evidence') {
          answer.sources = (event.data.sources || []).map((source) => ({
            ...source,
            page: source.page === undefined ? undefined : String(source.page),
            status: 'verified' as const,
          }))
          if (event.data.code) answer.outcome = event.data.code
        }
        if (event.type === 'outcome') answer.outcome = event.data.code
        scrollMessages()
      },
      abortController.signal,
    )
    if (!answer.text && !answer.outcome) answer.text = '暂时没有收到导览回答，请稍后重试。'
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return
    answer.outcome = error instanceof Error && error.message === 'RATE_LIMITED' ? 'RATE_LIMITED' : 'INSUFFICIENT_EVIDENCE'
    answer.text = '暂时无法连接游客导览服务，请稍后重试。'
  } finally {
    isSending.value = false
    abortController = null
    scrollMessages()
  }
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

const latestSources = computed(() => [...messageList.value].reverse().find((message) => message.role === 'guide' && message.sources?.length)?.sources || [])

function outcomeLabel(code?: string) {
  return code === 'RATE_LIMITED' ? '访问频率较高，请稍后再试。' : '资料证据不足，暂时不能确认。'
}

onBeforeUnmount(() => abortController?.abort())
</script>

<template>
  <div class="page guide-page">
    <header class="guide-header">
      <button type="button" class="back-link" aria-label="返回展品详情" @click="router.back()"><AppIcon name="back" :size="21" /></button>
      <div class="guide-header__title"><span class="status-dot" /><div><p class="eyebrow">AI GUIDE · DEMO</p><h1>正在讲解</h1></div></div>
      <button v-if="!recognizedName" type="button" class="icon-button" :aria-label="isSaved(exhibit.id) ? '取消收藏' : '加入展册'" @click="toggle(exhibit, selectedMode)"><AppIcon :name="isSaved(exhibit.id) ? 'bookmark-filled' : 'bookmark'" :size="20" /></button>
      <span v-else class="header-spacer" />
    </header>

    <section class="guide-context">
      <div class="guide-context__image"><img :src="recognizedName ? silkBanner : exhibit.image" :alt="recognizedName ? '图片识别导览精灵' : `${exhibit.name} 演示图片`" /></div>
      <div><span class="eyebrow">{{ recognizedName ? '你刚确认的候选' : '当前展品' }}</span><h2>{{ recognizedName || exhibit.name }}</h2><p>{{ recognizedName ? '来自图片识别 · 馆方资料核验' : `${exhibit.era} · ${exhibit.category}` }}</p></div>
      <RouterLink v-if="!recognizedName" :to="`/exhibits/${exhibit.id}`" class="context-link" aria-label="查看展品详情"><AppIcon name="chevron-right" :size="18" /></RouterLink>
      <span v-else class="header-spacer" />
    </section>

    <div class="mode-switcher" role="tablist" aria-label="讲解模式">
      <button v-for="(meta, mode) in modeMeta" :key="mode" type="button" role="tab" :aria-selected="selectedMode === mode" :class="{ 'is-active': selectedMode === mode }" @click="setMode(mode as GuideMode)">{{ meta.label }}</button>
    </div>

    <section ref="messageArea" class="guide-messages" aria-live="polite">
      <div v-if="!messageList.length && !isSending" class="guide-empty"><AppIcon name="spark" :size="23" /><strong>从一个问题开始</strong><p>我会先查找馆方资料，再把依据和边界告诉你。</p></div>
      <div v-for="message in messageList" :key="message.id" class="message" :class="`message--${message.role}`">
        <div v-if="message.role === 'guide'" class="guide-avatar" aria-hidden="true">智</div>
        <div v-if="message.text || message.sources?.length || message.outcome" class="message__bubble"><span v-if="message.role === 'guide'" class="message__label">智博导览 · {{ modeMeta[selectedMode].hint }}</span><p v-if="message.text">{{ message.text }}</p><p v-if="message.outcome" class="message__outcome">{{ outcomeLabel(message.outcome) }}</p></div>
      </div>
      <div v-if="isSending" class="message message--guide"><div class="guide-avatar" aria-hidden="true">智</div><div class="message__bubble message__bubble--loading"><span class="button-loader" />{{ loadingLabel }}</div></div>
    </section>

    <section v-if="!recognizedName" class="observation-strip"><div class="observation-strip__title"><AppIcon name="eye" :size="18" /><strong>重点观察</strong></div><div class="observation-mini-list"><span v-for="(item, index) in exhibit.observations.slice(0, 2)" :key="item.title"><b>{{ index + 1 }}</b>{{ item.title }}</span></div></section>

    <section class="guide-sources"><div class="section-heading section-heading--compact"><div><p class="eyebrow">WHY TRUST THIS</p><h2>馆方资料依据</h2></div><span v-if="latestSources.length" class="evidence-badge"><AppIcon name="check" :size="13" />已核验</span></div><SourceCard v-for="source in latestSources" :key="source.title" :source="source" /></section>

    <section class="suggestion-section"><p class="eyebrow">KEEP EXPLORING</p><div class="suggestion-list"><button v-for="question in demoQuestions" :key="question" type="button" class="suggestion-chip" @click="sendQuestion(question)">{{ question }} <AppIcon name="arrow-right" :size="14" /></button></div></section>

    <form class="guide-composer" @submit.prevent="sendQuestion()">
      <label for="guide-question" class="sr-only">向导览员提问</label>
      <textarea id="guide-question" v-model="input" rows="1" maxlength="300" placeholder="问问这件展品……" @keydown="onKeydown" />
      <button type="submit" class="send-button" :disabled="!input.trim() || isSending" aria-label="发送问题"><AppIcon name="send" :size="18" /></button>
    </form>
    <p class="guide-disclaimer">AI 生成内容可能存在误差，请以馆方正式说明为准。</p>
  </div>
</template>
