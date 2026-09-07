<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'

const router = useRouter()
const cameraInputRef = ref<HTMLInputElement | null>(null)
const galleryInputRef = ref<HTMLInputElement | null>(null)
const file = ref<File | null>(null)
const previewUrl = ref('')
const processing = ref(false)
const step = ref(0)

function handleFile(event: Event) {
  const target = event.target as HTMLInputElement
  const nextFile = target.files?.[0]
  if (!nextFile) return
  if (!nextFile.type.startsWith('image/')) return
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  file.value = nextFile
  previewUrl.value = URL.createObjectURL(nextFile)
  step.value = 0
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

function startRecognition() {
  if (!file.value || processing.value) return
  processing.value = true
  step.value = 1
  window.setTimeout(() => { step.value = 2 }, 650)
  window.setTimeout(() => { step.value = 3 }, 1250)
  window.setTimeout(() => {
    processing.value = false
    router.push('/recognitions/demo-recognition-001')
  }, 1700)
}

onBeforeUnmount(() => {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>

<template>
  <div class="page page--narrow">
    <header class="subpage-header">
      <RouterLink to="/" class="back-link" aria-label="返回首页"><AppIcon name="back" :size="21" /></RouterLink>
      <div><p class="eyebrow">LOOK CLOSER</p><h1>拍照识展</h1></div>
      <span class="header-spacer" />
    </header>

    <section class="recognize-intro">
      <span class="number-badge">01</span>
      <div><h2>让镜头先提出候选</h2><p>再由馆方资料帮你确认。识别只是开始，不会直接替资料下结论。</p></div>
    </section>

    <section class="upload-panel" :class="{ 'has-preview': previewUrl }">
      <div v-if="previewUrl" class="preview-wrap">
        <img :src="previewUrl" alt="待识别的图片预览" />
        <button type="button" class="preview-remove" aria-label="移除图片" @click="clearPreview"><span>×</span></button>
      </div>
      <div v-else class="upload-placeholder">
        <div class="camera-orbit"><AppIcon name="camera" :size="30" /></div>
        <strong>从一张照片开始</strong>
        <span>支持相机拍摄或从相册选择</span>
      </div>
      <input ref="cameraInputRef" class="sr-only" type="file" accept="image/jpeg,image/png,image/webp" capture="environment" @change="handleFile" />
      <input ref="galleryInputRef" class="sr-only" type="file" accept="image/jpeg,image/png,image/webp" @change="handleFile" />
      <div class="upload-actions">
        <button type="button" class="button button--primary" @click="chooseImage('camera')"><AppIcon name="camera" :size="18" />{{ previewUrl ? '换一张照片' : '拍一张照片' }}</button>
        <button type="button" class="button button--quiet" @click="chooseImage('gallery')"><AppIcon name="upload" :size="18" />从相册选择</button>
      </div>
    </section>

    <div class="photo-guidance"><AppIcon name="info" :size="16" /><span>拍摄建议：光线均匀 · 尽量正对 · 避免遮挡 · 单张不超过 8MB</span></div>

    <section class="process-card" aria-live="polite">
      <div class="process-card__heading"><div><p class="eyebrow">DOUBLE CHECK</p><h2>识别流程</h2></div><span class="demo-label">原型演示</span></div>
      <ol class="process-steps">
        <li :class="{ done: step > 1, active: step === 1 }"><span>1</span><div><strong>检查图片</strong><small>确认格式与清晰度</small></div></li>
        <li :class="{ done: step > 2, active: step === 2 }"><span>2</span><div><strong>提出候选</strong><small>观察材质、形制与纹饰</small></div></li>
        <li :class="{ done: step > 3, active: step === 3 }"><span>3</span><div><strong>资料核验</strong><small>只对已收录内容给出结论</small></div></li>
      </ol>
    </section>

    <button type="button" class="button button--primary button--full" :disabled="!file || processing" @click="startRecognition">
      <span v-if="processing" class="button-loader" />
      <AppIcon v-else name="spark" :size="18" />
      {{ processing ? '正在查找馆方资料…' : '开始识别' }}
    </button>
    <p class="privacy-note">图片仅用于本次识别。正式版本将由游客网关处理，浏览器不会接触模型密钥。</p>
  </div>
</template>
