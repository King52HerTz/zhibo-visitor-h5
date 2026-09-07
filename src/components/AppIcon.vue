<script setup lang="ts">
type IconName =
  | 'arrow-right'
  | 'back'
  | 'bookmark'
  | 'bookmark-filled'
  | 'camera'
  | 'check'
  | 'chevron-right'
  | 'clock'
  | 'compass'
  | 'eye'
  | 'home'
  | 'info'
  | 'route'
  | 'search'
  | 'send'
  | 'spark'
  | 'trash'
  | 'upload'

const props = withDefaults(defineProps<{ name: IconName; size?: number }>(), {
  size: 20,
})
</script>

<template>
  <svg
    class="app-icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-if="props.name === 'home'" d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z" />
    <path v-else-if="props.name === 'compass'" d="m12 3 7 4v10l-7 4-7-4V7zM9 15l2.2-5.2L16 8l-2.2 5.2z" />
    <path v-else-if="props.name === 'spark'" d="m12 2 1.5 6.5L20 10l-6.5 1.5L12 18l-1.5-6.5L4 10l6.5-1.5zM19 16l.6 2.4L22 19l-2.4.6L19 22l-.6-2.4L16 19l2.4-.6z" />
    <path v-else-if="props.name === 'bookmark'" d="M6 4.5A1.5 1.5 0 0 1 7.5 3h9A1.5 1.5 0 0 1 18 4.5V21l-6-3.6L6 21z" />
    <path v-else-if="props.name === 'bookmark-filled'" d="M6 4.5A1.5 1.5 0 0 1 7.5 3h9A1.5 1.5 0 0 1 18 4.5V21l-6-3.6L6 21z" fill="currentColor" />
    <g v-else-if="props.name === 'camera'"><path d="M4 7h3l1.3-2h7.4L17 7h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1Z" /><circle cx="12" cy="13" r="3.2" /></g>
    <path v-else-if="props.name === 'upload'" d="M12 16V4m0 0L8 8m4-4 4 4M5 14v5h14v-5" />
    <path v-else-if="props.name === 'arrow-right'" d="M4 12h15m-6-6 6 6-6 6" />
    <path v-else-if="props.name === 'back'" d="m15 18-6-6 6-6M9 12h11" />
    <path v-else-if="props.name === 'chevron-right'" d="m9 5 7 7-7 7" />
    <path v-else-if="props.name === 'check'" d="m5 12 4 4L19 6" />
    <path v-else-if="props.name === 'clock'" d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-14v5l3 2" />
    <g v-else-if="props.name === 'eye'"><path d="M2.5 12s3.5-5 9.5-5 9.5 5 9.5 5-3.5 5-9.5 5-9.5-5-9.5-5Z" /><circle cx="12" cy="12" r="2.2" /></g>
    <g v-else-if="props.name === 'info'"><circle cx="12" cy="12" r="9" /><path d="M12 10v6m0-9h.01" /></g>
    <path v-else-if="props.name === 'route'" d="M5 4h.01M19 20h.01M5 4c8 0 2 8 14 8M5 4v6m14 2v8" />
    <g v-else-if="props.name === 'search'"><circle cx="10.8" cy="10.8" r="6.8" /><path d="m16 16 5 5" /></g>
    <path v-else-if="props.name === 'send'" d="m3 11 18-8-8 18-2.2-7.8zM10.8 13.2 21 3" />
    <path v-else-if="props.name === 'trash'" d="M4 7h16m-10 4v6m4-6v6M9 7V4h6v3m-9 0 1 14h10l1-14" />
  </svg>
</template>
