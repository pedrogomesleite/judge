<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import DataTable, { type DataTableRowClickEvent } from 'primevue/datatable'
import Column from 'primevue/column'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import ResultadoTag from '@/components/ResultadoTag.vue'
import { usePolling } from '@/composables/usePolling'
import { listarAtividades } from '@/api/atividades'
import { mensagemDeErro } from '@/api/http'
import { listarMinhasSolucoes } from '@/api/solucoes'
import type { SolucaoResponse } from '@/api/types'
import { comUnidade, formatarDataHora } from '@/utils/formatacao'
import { classificar, type Classificacao } from '@/utils/rotulos'

const INTERVALO_ATUALIZACAO_MS = 3000

type Filtro = Classificacao | 'todas'

const toast = useToast()
const router = useRouter()

const solucoes = ref<SolucaoResponse[]>([])
const titulos = ref<Record<string, string>>({})
const carregando = ref(true)
const filtro = ref<Filtro>('todas')

const contagem = computed(() => {
  const total: Record<Classificacao, number> = { sucesso: 0, erro: 0, andamento: 0 }
  for (const solucao of solucoes.value) {
    total[classificar(solucao)]++
  }
  return total
})

const opcoesFiltro = computed(() => [
  { valor: 'todas', label: `Todas (${solucoes.value.length})` },
  { valor: 'sucesso', label: `Sucessos (${contagem.value.sucesso})` },
  { valor: 'erro', label: `Erros (${contagem.value.erro})` },
  { valor: 'andamento', label: `Em andamento (${contagem.value.andamento})` },
])

const visiveis = computed(() =>
  filtro.value === 'todas' ? solucoes.value : solucoes.value.filter((s) => classificar(s) === filtro.value),
)

// Enquanto houver solução sem veredito, atualiza a lista
const { agendar } = usePolling(
  atualizarSolucoes,
  () => contagem.value.andamento > 0,
  INTERVALO_ATUALIZACAO_MS,
)

async function atualizarSolucoes() {
  try {
    solucoes.value = await listarMinhasSolucoes()
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Erro ao atualizar as respostas', detail: mensagemDeErro(e), life: 5000 })
  }
}

function tituloDa(solucao: SolucaoResponse): string {
  return titulos.value[solucao.atividadeId] ?? 'Atividade'
}

function abrir(evento: DataTableRowClickEvent<SolucaoResponse>) {
  const solucao = evento.data
  router.push({
    name: 'atividade',
    params: { id: solucao.atividadeId },
    query: solucao.grupoId ? { groupId: solucao.grupoId } : {},
  })
}

onMounted(async () => {
  try {
    const [lista, atividades] = await Promise.all([listarMinhasSolucoes(), listarAtividades()])
    solucoes.value = lista
    titulos.value = Object.fromEntries(atividades.map((a) => [a.id, a.titulo]))
    agendar()
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Erro ao carregar as respostas', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    carregando.value = false
  }
})
</script>

<template>
  <section class="stack">
    <div>
      <h1>Minhas respostas</h1>
      <span class="texto-suave">Clique em uma resposta para abrir a atividade.</span>
    </div>

    <SelectButton
      v-model="filtro"
      :options="opcoesFiltro"
      option-label="label"
      option-value="valor"
      :allow-empty="false"
    />

    <DataTable
      :value="visiveis"
      :loading="carregando"
      :row-class="(s) => `linha-${classificar(s)}`"
      data-key="id"
      paginator
      :rows="10"
      row-hover
      @row-click="abrir"
    >
      <template #empty>Nenhuma resposta por aqui.</template>
      <Column header="Atividade">
        <template #body="{ data }">{{ tituloDa(data) }}</template>
      </Column>
      <Column header="Contexto">
        <template #body="{ data }">
          <Tag v-if="data.grupoId" value="Em grupo" icon="pi pi-users" severity="secondary" />
          <Tag v-else value="Avulsa" severity="secondary" />
        </template>
      </Column>
      <Column field="linguagem" header="Linguagem" />
      <Column header="Resultado">
        <template #body="{ data }"><ResultadoTag :solucao="data" /></template>
      </Column>
      <Column header="Tempo">
        <template #body="{ data }">{{ comUnidade(data.tempoMs, 'ms') }}</template>
      </Column>
      <Column header="Memória">
        <template #body="{ data }">{{ comUnidade(data.memoriaKb, 'KB') }}</template>
      </Column>
      <Column header="Enviada em">
        <template #body="{ data }">{{ formatarDataHora(data.enviadoEm) }}</template>
      </Column>
    </DataTable>
  </section>
</template>

<style scoped>
:deep(tr) {
  cursor: pointer;
}

:deep(.linha-sucesso > td:first-child) {
  box-shadow: inset 4px 0 0 var(--p-green-500);
}

:deep(.linha-erro > td:first-child) {
  box-shadow: inset 4px 0 0 var(--p-red-500);
}

:deep(.linha-andamento > td:first-child) {
  box-shadow: inset 4px 0 0 var(--p-blue-500);
}
</style>
