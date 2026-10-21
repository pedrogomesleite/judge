<script setup lang="ts">
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Message from 'primevue/message'
import ResultadoTag from '@/components/ResultadoTag.vue'
import type { SolucaoCodigoResponse, SolucaoResponse } from '@/api/types'
import { comUnidade, formatarDataHora } from '@/utils/formatacao'

// Só exibe: quem abre o diálogo carrega o código e faz o download

defineProps<{
  solucao: SolucaoResponse | null
  /** Nome de quem enviou a solução. */
  membro: string
  atividade: string
  /** null enquanto o código carrega. */
  codigo: SolucaoCodigoResponse | null
  erro: string | null
}>()

const visivel = defineModel<boolean>('visible', { required: true })

const emit = defineEmits<{
  baixar: []
}>()
</script>

<template>
  <Dialog
    v-model:visible="visivel"
    modal
    :header="`Resposta de ${membro}`"
    :style="{ width: '56rem' }"
    :breakpoints="{ '960px': '95vw' }"
  >
    <div v-if="solucao" class="stack">
      <div class="row">
        <strong>{{ atividade }}</strong>
        <span class="texto-suave">{{ solucao.linguagem }} · {{ formatarDataHora(solucao.enviadoEm) }}</span>
        <ResultadoTag :solucao="solucao" />
        <span v-if="solucao.tempoMs != null || solucao.memoriaKb != null" class="texto-suave">
          {{ comUnidade(solucao.tempoMs, 'ms') }} · {{ comUnidade(solucao.memoriaKb, 'KB') }}
        </span>
      </div>

      <Message v-if="erro" severity="error">{{ erro }}</Message>
      <span v-else-if="!codigo" class="texto-suave"><i class="pi pi-spin pi-spinner" /> Carregando código…</span>
      <pre v-else class="codigo mono">{{ codigo.codigo }}</pre>

      <div class="row">
        <span class="spacer" />
        <Button label="Fechar" severity="secondary" text @click="visivel = false" />
        <Button
          :label="codigo ? `Baixar .${codigo.extensao}` : 'Baixar'"
          icon="pi pi-download"
          :disabled="!codigo"
          @click="emit('baixar')"
        />
      </div>
    </div>
  </Dialog>
</template>

<style scoped>
/* Sem quebra de linha automática: o código aparece exatamente como foi enviado */
.codigo {
  margin: 0;
  padding: 0.75rem 1rem;
  max-height: 60vh;
  overflow: auto;
  white-space: pre;
  tab-size: 4;
  font-size: 0.875rem;
  background: var(--p-surface-100);
  border: 1px solid var(--p-content-border-color);
  border-radius: var(--p-content-border-radius);
}
</style>
