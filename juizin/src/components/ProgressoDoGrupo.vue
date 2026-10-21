<script setup lang="ts">
import { ref, watch } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import ProgressBar from 'primevue/progressbar'
import Message from 'primevue/message'
import { buscarProgressoDoGrupo } from '@/api/grupos'
import { mensagemDeErro } from '@/api/http'
import type { ProgressoMembroResponse } from '@/api/types'

// Placar dos estudantes: atividades aceitas (sem contar aceites repetidos) sobre o total do grupo

const props = defineProps<{
  grupoId: string
  /** Muda a cada alteração no grupo (SSE): busca o placar de novo. */
  revisao: number
  /** Linha destacada (o próprio estudante). */
  destacarUsuarioId?: string | null
}>()

const progresso = ref<ProgressoMembroResponse[]>([])
const carregando = ref(true)
const erro = ref<string | null>(null)

async function atualizar() {
  try {
    progresso.value = await buscarProgressoDoGrupo(props.grupoId)
    erro.value = null
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
}

watch([() => props.grupoId, () => props.revisao], atualizar, { immediate: true })

function percentual(item: ProgressoMembroResponse) {
  return item.totalAtividades > 0 ? Math.round((item.aceitas / item.totalAtividades) * 100) : 0
}

function classeDaLinha(item: ProgressoMembroResponse) {
  return item.usuarioId === props.destacarUsuarioId ? 'destaque' : undefined
}
</script>

<template>
  <div class="stack">
    <div class="row">
      <strong>Participantes</strong>
      <span class="texto-suave">Atividades com resposta aceita</span>
    </div>

    <Message v-if="erro" severity="error" size="small">{{ erro }}</Message>

    <DataTable
      :value="progresso"
      :loading="carregando"
      data-key="usuarioId"
      size="small"
      :row-class="classeDaLinha"
      paginator
      :rows="20"
      :always-show-paginator="false"
    >
      <template #empty>Nenhum estudante no grupo.</template>
      <Column header="#" style="width: 3rem">
        <template #body="{ index }">{{ index + 1 }}</template>
      </Column>
      <Column field="nome" header="Estudante">
        <template #body="{ data }">
          {{ data.nome }}
          <span v-if="data.usuarioId === destacarUsuarioId" class="texto-suave">(você)</span>
        </template>
      </Column>
      <Column header="Aceitas" style="width: 14rem">
        <template #body="{ data }">
          <div class="placar">
            <ProgressBar :value="percentual(data)" :show-value="false" class="barra" />
            <span class="contagem">{{ data.aceitas }}/{{ data.totalAtividades }}</span>
          </div>
        </template>
      </Column>
    </DataTable>
  </div>
</template>

<style scoped>
.placar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.barra {
  flex: 1;
  height: 0.5rem;
}

.contagem {
  min-width: 3rem;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

:deep(.destaque) {
  font-weight: 600;
  background: var(--p-highlight-background);
}
</style>
