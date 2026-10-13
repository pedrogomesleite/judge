<script setup lang="ts">
import Toast from 'primevue/toast'
import AppHeader from '@/components/AppHeader.vue'
</script>

<template>
  <Toast />
  <AppHeader />
  <main class="container">
    <RouterView />
  </main>
</template>
