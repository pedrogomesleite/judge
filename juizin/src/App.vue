<script setup lang="ts">
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'
import AppHeader from '@/components/AppHeader.vue'
</script>

<template>
  <Toast />
  <ConfirmDialog />
  <AppHeader />
  <main class="container">
    <RouterView />
  </main>
</template>
