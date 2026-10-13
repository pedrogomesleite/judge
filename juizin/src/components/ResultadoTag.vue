<script setup lang="ts">
import { computed } from 'vue'
import Tag from 'primevue/tag'
import type { SolucaoResponse } from '@/api/types'
import { classificar, rotuloSolucao } from '@/utils/rotulos'

const props = defineProps<{
  solucao: Pick<SolucaoResponse, 'status' | 'veredito'>
}>()

const rotulo = computed(() => rotuloSolucao(props.solucao))
const icone = computed(() => {
  switch (classificar(props.solucao)) {
    case 'sucesso':
      return 'pi pi-check'
    case 'erro':
      return 'pi pi-times'
    default:
      return 'pi pi-spin pi-spinner'
  }
})
</script>

<template>
  <Tag :value="rotulo.label" :severity="rotulo.severidade" :icon="icone" />
</template>
