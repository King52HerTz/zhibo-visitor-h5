<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import silkBanner from '../assets/visuals/silk-banner.webp'
import {
  confirmRecognition,
  getRecognition,
  type RecognitionCandidate,
  type RecognitionResult,
} from '../services/visitorGateway'

const route = useRoute()
const router = useRouter()
const result = ref<RecognitionResult | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const confirmingId = ref('')
const abortController = new AbortController()
const recognitionId = computed(() => String(route.params.id || ''))
const verifiedCandidates = computed(() => result.value?.candidates.filter((item) => item.knowledge_status === 'VERIFIED') || [])
const isHighConfidence = computed(() => verifiedCandidates.value.length === 1 && verifiedCandidates.value[0].confidence >= 0.8)

function confidenceLabel(value: number) {
  if (value >= 0.8) return '较高匹配'
  if (value >= 0.55) return '需要确认'
  return '可能相关'
}

function knowledgeLabel(status: RecognitionCandidate['knowledge_status']) {
  if (status === 'VERIFIED') return '馆方资料已命中'
  if (status === 'PARTIAL') return '核验服务暂不可用'
  return '馆方资料暂未收录'
}

async function loadResult() {
  loading.value = true
  errorMessage.value = ''
  try {
    result.value = await getRecognition(recognitionId.value, abortController.signal)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return
    errorMessage.value = error instanceof Error ? error.message : '无法读取识别结果。'
  } finally {
    loading.value = false
  }
}

async function chooseCandidate(candidate: RecognitionCandidate) {
  if (confirmingId.value) return
  confirmingId.value = candidate.candidate_id
  errorMessage.value = ''
  try {
    result.value = await confirmRecognition(recognitionId.value, candidate.candidate_id, abortController.signal)
    if (candidate.knowledge_status === 'VERIFIED') {
      await router.push({
        path: '/guide',
        query: { recognition: recognitionId.value, recognized_name: candidate.name },
      })
    }
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return
    errorMessage.value = error instanceof Error ? error.message : '暂时无法确认候选。'
  } finally {
    confirmingId.value = ''
  }
}

onMounted(loadResult)
onBeforeUnmount(() => abortController.abort())
</script>

<template>
  <div class="page page--narrow">
    <header class="subpage-header">
      <RouterLink to="/recognize" class="back-link" aria-label="返回拍照识展"><AppIcon name="back" :size="21" /></RouterLink>
      <div><p class="eyebrow">视觉候选 · 馆方核验</p><h1>识别结果</h1></div>
      <span class="header-spacer" />
    </header>

    <section v-if="loading" class="recognition-state" aria-live="polite">
      <span class="button-loader" /><h2>正在读取识别结果</h2><p>马上就好。</p>
    </section>

    <section v-else-if="errorMessage && !result" class="recognition-state recognition-state--error" role="alert">
      <AppIcon name="info" :size="26" /><h2>结果没有加载出来</h2><p>{{ errorMessage }}</p>
      <button type="button" class="button button--quiet" @click="loadResult">重新加载</button>
    </section>

    <template v-else-if="result">
      <section v-if="result.status === 'ERROR'" class="recognition-state recognition-state--error">
        <img :src="silkBanner" alt="导览核验精灵" /><h2>这次没有核验成功</h2><p>{{ result.message }}</p>
        <RouterLink to="/recognize" class="button button--primary">重新拍摄</RouterLink>
      </section>

      <template v-else>
        <section class="recognition-summary">
          <div class="recognition-thumb"><img :src="silkBanner" alt="导览核验精灵" /></div>
          <div>
            <span class="status-label" :class="{ 'status-label--review': !isHighConfidence }"><span class="status-dot" />{{ isHighConfidence ? '较高把握' : '请你确认' }}</span>
            <h2>{{ result.status === 'NOT_FOUND' ? '资料库暂未找到对应展品' : (isHighConfidence ? '很可能就是这一件' : '镜头找到了多个候选') }}</h2>
            <p>{{ result.message }}</p>
          </div>
        </section>

        <section v-if="result.observations.length" class="recognition-observations" aria-labelledby="observation-title">
          <p id="observation-title" class="eyebrow">镜头看到的线索</p>
          <div><span v-for="item in result.observations" :key="item">{{ item }}</span></div>
        </section>

        <section v-if="result.candidates.length" class="candidate-list" aria-labelledby="candidate-title">
          <div class="section-heading section-heading--compact">
            <div><p class="eyebrow">由你做最后确认</p><h2 id="candidate-title">候选展品</h2></div>
            <span class="small-muted">{{ result.candidates.length }} 个候选</span>
          </div>
          <article
            v-for="(candidate, index) in result.candidates"
            :key="candidate.candidate_id"
            class="candidate-card"
            :class="{ 'candidate-card--selected': candidate.knowledge_status === 'VERIFIED' }"
          >
            <div class="candidate-card__top"><span class="candidate-rank">0{{ index + 1 }}</span><span class="confidence" :class="{ 'confidence--high': candidate.confidence >= 0.8 }">{{ confidenceLabel(candidate.confidence) }}</span></div>
            <h3>{{ candidate.name }}</h3>
            <div class="candidate-card__evidence" :class="{ 'candidate-card__evidence--muted': candidate.knowledge_status !== 'VERIFIED' }"><AppIcon :name="candidate.knowledge_status === 'VERIFIED' ? 'check' : 'info'" :size="15" /><span>{{ knowledgeLabel(candidate.knowledge_status) }}</span></div>
            <ul><li v-for="basis in candidate.visual_basis" :key="basis">{{ basis }}</li></ul>
            <div v-if="candidate.sources.length" class="candidate-source"><strong>{{ candidate.sources[0].title }}</strong><span v-if="candidate.sources[0].page">第 {{ candidate.sources[0].page }} 页</span><p>{{ candidate.sources[0].excerpt }}</p></div>
            <button type="button" class="button button--full" :class="candidate.knowledge_status === 'VERIFIED' ? 'button--primary' : 'button--quiet'" :disabled="Boolean(confirmingId)" @click="chooseCandidate(candidate)">
              <span v-if="confirmingId === candidate.candidate_id" class="button-loader" />
              {{ candidate.knowledge_status === 'VERIFIED' ? '确认并开始讲解' : '确认是这一件' }}
              <AppIcon v-if="confirmingId !== candidate.candidate_id" name="arrow-right" :size="17" />
            </button>
          </article>
        </section>

        <section v-else class="recognition-state">
          <h2>暂时没有清晰候选</h2><p>可以换一个角度，尽量让单件展品完整进入画面。</p>
        </section>

        <p v-if="errorMessage" class="recognition-error" role="alert"><AppIcon name="info" :size="17" />{{ errorMessage }}</p>
        <section class="source-strip"><AppIcon name="check" :size="17" /><div><strong>为什么需要你确认？</strong><p>视觉模型只提出候选，馆方资料负责核验；没有资料支持时不会生成确定性讲解。</p></div><RouterLink to="/about/sources" aria-label="查看资料说明"><AppIcon name="chevron-right" :size="18" /></RouterLink></section>
        <RouterLink to="/recognize" class="button button--quiet button--full">重新拍摄</RouterLink>
      </template>
    </template>
  </div>
</template>
