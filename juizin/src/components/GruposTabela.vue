<script setup lang="ts">
import { ref, watch } from 'vue'
import DataTable, { type DataTableRowExpandEvent } from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import ConviteLink from '@/components/ConviteLink.vue'
import EnviosDoGrupo from '@/components/EnviosDoGrupo.vue'
import { listarAtividadesDoGrupo } from '@/api/grupos'
import { mensagemDeErro } from '@/api/http'
import type { GrupoAtividadeItemResponse, GrupoResponse } from '@/api/types'
import { formatarPeriodo } from '@/utils/formatacao'
import { ROTULOS_STATUS_GRUPO } from '@/utils/rotulos'

const props = defineProps<{
  grupos: GrupoResponse[]
  carregando: boolean
  vazio: string
  /** Grupo que já abre expandido (ex.: o que acabou de ser acessado pelo convite). */
  expandirGrupoId?: string | null
}>()

const toast = useToast()

const expandidas = ref<Record<string, boolean>>({})
/** Atividades de cada grupo, carregadas na primeira vez que a linha é expandida. */
const atividadesPorGrupo = ref<Record<string, GrupoAtividadeItemResponse[]>>({})

function rotuloDoStatus(grupo: GrupoResponse) {
  return ROTULOS_STATUS_GRUPO[grupo.status]
}

async function carregarAtividades(grupoId: string) {
  if (atividadesPorGrupo.value[grupoId]) {
    return
  }
  try {
    const itens = await listarAtividadesDoGrupo(grupoId)
    atividadesPorGrupo.value[grupoId] = [...itens].sort((a, b) => a.ordem - b.ordem)
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Erro ao carregar as atividades do grupo', detail: mensagemDeErro(e), life: 5000 })
  }
}

function aoExpandir(evento: DataTableRowExpandEvent<GrupoResponse>) {
  carregarAtividades(evento.data.id)
}

// Expandir pelo v-model não dispara row-expand, então as atividades são carregadas aqui
watch(
  () => props.grupos,
  (grupos) => {
    const grupoId = props.expandirGrupoId
    if (grupoId && grupos.some((g) => g.id === grupoId)) {
      expandidas.value = { ...expandidas.value, [grupoId]: true }
      carregarAtividades(grupoId)
    }
  },
  { immediate: true },
)
</script>

<template>
  <DataTable
    v-model:expanded-rows="expandidas"
    :value="grupos"
    :loading="carregando"
    data-key="id"
    @row-expand="aoExpandir"
  >
    <template #empty>{{ vazio }}</template>
    <Column expander style="width: 3rem" />
    <Column field="nome" header="Grupo">
      <template #body="{ data }">
        <div class="nome">{{ data.nome }}</div>
        <small v-if="data.descricao" class="texto-suave">{{ data.descricao }}</small>
      </template>
    </Column>
    <Column header="Status" style="width: 12rem">
      <template #body="{ data }">
        <Tag :value="rotuloDoStatus(data).label" :severity="rotuloDoStatus(data).severidade" />
      </template>
    </Column>
    <Column header="Período">
      <template #body="{ data }">{{ formatarPeriodo(data.dataInicio, data.dataFim) }}</template>
    </Column>

    <template #expansion="{ data }">
      <div class="stack expansao">
        <Message v-if="data.status === 'AGUARDANDO_INICIO'" severity="warn" size="small">
          Os envios são liberados quando o grupo estiver em andamento.
        </Message>
        <Message v-else-if="data.status === 'FINALIZADO'" severity="secondary" size="small">
          Grupo finalizado: os envios estão encerrados.
        </Message>

        <span v-if="!atividadesPorGrupo[data.id]" class="texto-suave">
          <i class="pi pi-spin pi-spinner" /> Carregando atividades…
        </span>
        <span v-else-if="atividadesPorGrupo[data.id]?.length === 0" class="texto-suave">
          Nenhuma atividade neste grupo.
        </span>
        <!-- Avaliador: consolidação dos envios dos membros, por atividade -->
        <EnviosDoGrupo
          v-else-if="data.meuPapel === 'AVALIADOR'"
          :grupo-id="data.id"
          :atividades="atividadesPorGrupo[data.id] ?? []"
        />
        <ol v-else class="atividades">
          <li v-for="atividade in atividadesPorGrupo[data.id]" :key="atividade.atividadeId">
            <RouterLink
              :to="{ name: 'atividade', params: { id: atividade.atividadeId }, query: { groupId: data.id } }"
            >
              {{ atividade.titulo }}
            </RouterLink>
          </li>
        </ol>

        <!-- Qualquer membro compartilha o convite; grupo finalizado não aceita novas entradas -->
        <ConviteLink v-if="data.status !== 'FINALIZADO'" :codigo-convite="data.codigoConvite" />
      </div>
    </template>
  </DataTable>
</template>

<style scoped>
.nome {
  font-weight: 600;
}

.expansao {
  padding: 0.5rem 1rem;
}

.atividades {
  margin: 0;
  padding-left: 1.25rem;
}
</style>
