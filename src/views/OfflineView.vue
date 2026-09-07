<script setup lang="ts">
import AppIcon from '../components/AppIcon.vue'

function reloadPage() {
  window.location.reload()
}
</script>

<template>
  <div class="page offline-page"><div class="offline-mark"><AppIcon name="route" :size="34" /></div><p class="eyebrow">A QUIET PAUSE</p><h1>导览暂时走慢了一步</h1><p>网络或游客服务暂时不可用。你可以先回到首页重试，已保存在本机的数字展册不会被清除。</p><div class="offline-actions"><RouterLink to="/" class="button button--primary">回到首页</RouterLink><button type="button" class="button button--quiet" @click="reloadPage">重新连接</button></div></div>
</template>
