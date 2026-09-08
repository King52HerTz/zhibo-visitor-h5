<script setup lang="ts">
import AppIcon from '../components/AppIcon.vue'
import { useCatalog } from '../services/catalog'

const { exhibits, contentLabel, sync, isOnline } = useCatalog()

async function reconnect() {
  await sync(true)
  if (isOnline.value) window.location.assign('/explore')
}
</script>

<template>
  <div class="page offline-page"><div class="offline-mark"><AppIcon name="route" :size="34" /></div><p class="eyebrow">OFFLINE, STILL EXPLORING</p><h1>网络慢了，参观不用停</h1><p>讲解和识别暂时需要联网，但最近成功保存的 {{ exhibits.length }} 件展品仍可浏览。数字展册也会继续保存在本机。</p><span class="offline-cache-label">{{ contentLabel }}</span><div class="offline-actions"><RouterLink to="/explore" class="button button--primary">浏览离线馆藏</RouterLink><button type="button" class="button button--quiet" @click="reconnect">重新连接</button></div></div>
</template>
