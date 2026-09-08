<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import { useCatalog } from '../services/catalog'

const { contentLabel, status, isOnline, sync } = useCatalog()
</script>

<template>
  <button v-if="status !== 'fresh'" type="button" class="catalog-status" :class="{ 'is-offline': !isOnline }" :disabled="status === 'syncing'" @click="sync(true)">
    <span class="catalog-status__dot" />
    <span>{{ contentLabel }}</span>
    <AppIcon name="refresh" :size="13" />
  </button>
</template>
