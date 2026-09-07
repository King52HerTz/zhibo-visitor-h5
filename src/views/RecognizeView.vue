<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import silkBanner from '../assets/visuals/silk-banner.webp'
import { createRecognition } from '../services/visitorGateway'

const router = useRouter()
const cameraInputRef = ref<HTMLInputElement | null>(null)
const galleryInputRef = ref<HTMLInputElement | null>(null)
const file = ref<File | null>(null)
const previewUrl = ref('')
const processing = ref(false)
const step = ref(0)
const uploadProgress = ref(0)
const errorMessage = ref('')
let abortController: AbortController | null = null

function handleFile(event: Event) {
  const target = event.target as HTMLInputElement
  const nextFile = target.files?.[0]
  if (!nextFile) return
  errorMessage.value = ''
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(nextFile.type)) {
    errorMessage.value = '请选择 JPEG、PNG 或 WebP 图片。'
    target.value = ''
    return
  }
  if (nextFile.size > 8 * 1024 * 1024) {
    errorMessage.value = '图片不能超过 8MB。'
    target.value = ''
    return
  }
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  file.value = nextFile
  previewUrl.value = URL.createObjectURL(nextFile)
  step.value = 0
  uploadProgress.value = 0
}

function clearPreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  file.value = null
  previewUrl.value = ''
  step.value = 0
}

function chooseImage(source: 'camera' | 'gallery') {
  const input = source === 'camera' ? cameraInputRef.value : galleryInputRef.value
  input?.click()
}

async function startRecognition() {
  if (!file.value || processing.value) return
  processing.value = true
  step.value = 1
  uploadProgress.value = 0
  errorMessage.value = ''
  abortController?.abort()
  abortController = new AbortController()
  try {
    const result = await createRecognition(file.value, (percent) => {
      uploadProgress.value = percent
      if (percent >= 100) step.value = 2
    }, abortController.signal)
    step.value = 3
    await router.push(`/recognitions/${result.recognition_id}`)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return
    errorMessage.value = error instanceof Error ? error.message : '识别失败，请稍后重试。'
    step.value = 0
  } finally {
    processing.value = false
    abortController = null
  }
}

onBeforeUnmount(() => {
  abortController?.abort()
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>

<template>
  <div class="page page--narrow">
    <header class="subpage-header">
      <RouterLink to="/" class="back-link" aria-label="返回首页"><AppIcon name="back" :size="21" /></RouterLink>
      <div><p class="eyebrow">看见 · 识别 · 核验</p><h1>拍照识展</h1></div>
      <span class="header-spacer" />
    </header>

    <section class="recognize-hero">
      <div><span class="recognize-hero__tag">视觉候选 × 馆方核验</span><h2>对准展品，<br />打开一条线索。</h2><p>模型只负责观察，馆方资料负责确认；拿不准时，你来选择。</p></div>
      <img :src="silkBanner" alt="由 T 形帛画结构转化的导览精灵" />
    </section>

    <section class="upload-panel" :class="{ 'has-preview': previewUrl }">
      <div v-if="previewUrl" class="preview-wrap">
        <img :src="previewUrl" alt="待识别的图片预览" />
        <button type="button" class="preview-remove" aria-label="移除图片" @click="clearPreview"><span>×</span></button>
      </div>
      <div v-else class="upload-placeholder">
        <span class="capture-corner capture-corner--tl" aria-hidden="true" />
        <span class="capture-corner capture-corner--tr" aria-hidden="true" />
        <span class="capture-corner capture-corner--bl" aria-hidden="true" />
        <span class="capture-corner capture-corner--br" aria-hidden="true" />
        <div class="camera-orbit"><AppIcon name="camera" :size="30" /></div>
        <strong>把展品放进取景框</strong>
        <span>正面、清晰、尽量只拍一件</span>
      </div>
      <input ref="cameraInputRef" class="sr-only" type="file" accept="image/jpeg,image/png,image/webp" capture="environment" @change="handleFile" />
      <input ref="galleryInputRef" class="sr-only" type="file" accept="image/jpeg,image/png,image/webp" @change="handleFile" />
      <div class="upload-actions">
        <button type="button" class="button button--primary" @click="chooseImage('camera')"><AppIcon name="camera" :size="18" />{{ previewUrl ? '换一张照片' : '拍一张照片' }}</button>
        <button type="button" class="button button--quiet" @click="chooseImage('gallery')"><AppIcon name="upload" :size="18" />从相册选择</button>
      </div>
      <div v-if="processing" class="upload-progress" aria-live="polite">
        <div><span>上传图片</span><strong>{{ uploadProgress }}%</strong></div>
        <span class="upload-progress__track"><i :style="{ width: `${uploadProgress}%` }" /></span>
      </div>
    </section>

    <p v-if="errorMessage" class="recognition-error" role="alert"><AppIcon name="info" :size="17" />{{ errorMessage }}</p>

    <div class="photo-guidance"><AppIcon name="info" :size="16" /><span>拍摄建议：光线均匀 · 尽量正对 · 避免遮挡 · 单张不超过 8MB</span></div>

    <section class="process-card" aria-live="polite">
      <div class="process-card__heading"><div><p class="eyebrow">三步确认</p><h2>不是“看图猜答案”</h2></div><span class="demo-label">可信识别</span></div>
      <ol class="process-steps">
        <li :class="{ done: step > 1, active: step === 1 }"><span>1</span><div><strong>检查图片</strong><small>确认格式与清晰度</small></div></li>
        <li :class="{ done: step > 2, active: step === 2 }"><span>2</span><div><strong>提出候选</strong><small>观察材质、形制与纹饰</small></div></li>
        <li :class="{ done: step > 3, active: step === 3 }"><span>3</span><div><strong>资料核验</strong><small>只对已收录内容给出结论</small></div></li>
      </ol>
    </section>

    <button type="button" class="button button--primary button--full" :disabled="!file || processing" @click="startRecognition">
      <span v-if="processing" class="button-loader" />
      <AppIcon v-else name="spark" :size="18" />
      {{ processing ? (step === 1 ? '正在上传图片…' : '正在核验馆方资料…') : '开始识别' }}
    </button>
    <p class="privacy-note">图片仅用于本次识别，由游客网关处理；浏览器不会接触模型密钥。</p>
  </div>
</template>
