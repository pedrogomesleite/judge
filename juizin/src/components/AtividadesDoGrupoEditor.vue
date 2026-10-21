<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import Button from 'primevue/button'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { listarAtividades } from '@/api/atividades'
import { adicionarAtividadeAoGrupo, alterarPesoDaAtividade, removerAtividadeDoGrupo } from '@/api/grupos'
import { mensagemDeErro } from '@/api/http'
import type { AtividadeResponse, GrupoAtividadeItemResponse } from '@/api/types'

// Avaliador, com o grupo em AGUARDANDO_INICIO: monta a lista de atividades e define o peso de cada uma

// Mesmos limites do GrupoAtividadePesoRequest
const PESO_MINIMO = 1
const PESO_MAXIMO = 100

const props = defineProps<{
  grupoId: string
  atividades: GrupoAtividadeItemResponse[]
}>()

const emit = defineEmits<{
  /** Atividades ou pesos mudaram: recarregue a lista. */
  alterado: []
}>()

const toast = useToast()
const confirm = useConfirm()
const id = useId()

const catalogo = ref<AtividadeResponse[]>([])
const carregandoCatalogo = ref(true)
const novaAtividadeId = ref<string | null>(null)
const novoPeso = ref(PESO_MINIMO)
const adicionando = ref(false)
/** Id da atividade com operação em andamento (alterar peso ou remover). */
const ocupada = ref<string | null>(null)

/** Peso de cada linha; salvo pouco depois da última mudança (digitação ou botões +/-). */
const pesos = ref<Record<string, number | null>>({})
const ESPERA_SALVAR_PESO_MS = 600
const timersPeso = new Map<string, ReturnType<typeof setTimeout>>()

onBeforeUnmount(() => timersPeso.forEach(clearTimeout))

watch(
  () => props.atividades,
  (atividades) => {
    // Linhas com peso ainda não salvo mantêm o valor digitado
    pesos.value = Object.fromEntries(
      atividades.map((a) => [a.atividadeId, timersPeso.has(a.atividadeId) ? pesos.value[a.atividadeId] ?? a.peso : a.peso]),
    )
  },
  { immediate: true },
)

const disponiveis = computed(() => {
  const noGrupo = new Set(props.atividades.map((a) => a.atividadeId))
  return catalogo.value.filter((a) => !noGrupo.has(a.id))
})

const pesoTotal = computed(() => props.atividades.reduce((total, a) => total + a.peso, 0))

onMounted(async () => {
  try {
    catalogo.value = await listarAtividades()
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Erro ao carregar o catálogo de atividades', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    carregandoCatalogo.value = false
  }
})

async function adicionar() {
  if (!novaAtividadeId.value) {
    return
  }
  adicionando.value = true
  try {
    const ordem = props.atividades.reduce((maior, a) => Math.max(maior, a.ordem), 0) + 1
    await adicionarAtividadeAoGrupo(props.grupoId, { atividadeId: novaAtividadeId.value, ordem, peso: novoPeso.value })
    novaAtividadeId.value = null
    novoPeso.value = PESO_MINIMO
    emit('alterado')
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Não foi possível adicionar a atividade', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    adicionando.value = false
  }
}

function aoMudarPeso(atividade: GrupoAtividadeItemResponse, valor: number | null) {
  pesos.value[atividade.atividadeId] = valor
  clearTimeout(timersPeso.get(atividade.atividadeId))
  timersPeso.set(
    atividade.atividadeId,
    setTimeout(() => salvarPeso(atividade), ESPERA_SALVAR_PESO_MS),
  )
}

async function salvarPeso(atividade: GrupoAtividadeItemResponse) {
  timersPeso.delete(atividade.atividadeId)
  const peso = pesos.value[atividade.atividadeId]
  if (peso == null || peso === atividade.peso) {
    // Campo esvaziado: volta ao peso salvo
    pesos.value[atividade.atividadeId] = atividade.peso
    return
  }
  ocupada.value = atividade.atividadeId
  try {
    await alterarPesoDaAtividade(props.grupoId, atividade.atividadeId, peso)
    emit('alterado')
  } catch (e) {
    pesos.value[atividade.atividadeId] = atividade.peso
    toast.add({ severity: 'error', summary: 'Não foi possível alterar o peso', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    ocupada.value = null
  }
}

function confirmarRemocao(atividade: GrupoAtividadeItemResponse) {
  confirm.require({
    header: 'Remover atividade',
    message: `Remover "${atividade.titulo}" do grupo?`,
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Cancelar', severity: 'secondary', text: true },
    acceptProps: { label: 'Remover', severity: 'danger' },
    accept: () => remover(atividade),
  })
}

async function remover(atividade: GrupoAtividadeItemResponse) {
  ocupada.value = atividade.atividadeId
  try {
    await removerAtividadeDoGrupo(props.grupoId, atividade.atividadeId)
    emit('alterado')
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Não foi possível remover a atividade', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    ocupada.value = null
  }
}
</script>

<template>
  <div class="stack">
    <div class="row">
      <strong>Atividades</strong>
      <span class="texto-suave">
        {{ atividades.length }} no grupo · pontuação máxima {{ pesoTotal }}
      </span>
    </div>

    <form class="row adicionar" @submit.prevent="adicionar">
      <div class="campo seletor">
        <label :for="`${id}-atividade`">Adicionar atividade</label>
        <Select
          v-model="novaAtividadeId"
          :input-id="`${id}-atividade`"
          :options="disponiveis"
          option-label="titulo"
          option-value="id"
          filter
          :loading="carregandoCatalogo"
          placeholder="Selecione uma atividade do catálogo"
          empty-filter-message="Nenhuma atividade encontrada"
          empty-message="Todas as atividades já estão no grupo"
          fluid
        />
      </div>
      <div class="campo">
        <label :for="`${id}-peso`">Peso</label>
        <InputNumber
          v-model="novoPeso"
          :input-id="`${id}-peso`"
          :min="PESO_MINIMO"
          :max="PESO_MAXIMO"
          show-buttons
          :allow-empty="false"
          input-class="peso"
        />
      </div>
      <Button
        type="submit"
        label="Adicionar"
        icon="pi pi-plus"
        :loading="adicionando"
        :disabled="!novaAtividadeId"
      />
    </form>

    <DataTable :value="atividades" data-key="atividadeId" size="small">
      <template #empty>Nenhuma atividade no grupo ainda. Adicione atividades do catálogo acima.</template>
      <Column header="#" style="width: 3rem">
        <template #body="{ index }">{{ index + 1 }}</template>
      </Column>
      <Column field="titulo" header="Atividade">
        <template #body="{ data }">
          <RouterLink :to="{ name: 'atividade', params: { id: data.atividadeId } }">{{ data.titulo }}</RouterLink>
        </template>
      </Column>
      <Column header="Peso" style="width: 10rem">
        <template #body="{ data }">
          <InputNumber
            :model-value="pesos[data.atividadeId]"
            :min="PESO_MINIMO"
            :max="PESO_MAXIMO"
            show-buttons
            size="small"
            input-class="peso"
            :disabled="ocupada === data.atividadeId"
            :aria-label="`Peso de ${data.titulo}`"
            @update:model-value="aoMudarPeso(data, $event)"
          />
        </template>
      </Column>
      <Column style="width: 4rem">
        <template #body="{ data }">
          <Button
            icon="pi pi-trash"
            severity="danger"
            text
            rounded
            :loading="ocupada === data.atividadeId"
            :aria-label="`Remover ${data.titulo}`"
            @click="confirmarRemocao(data)"
          />
        </template>
      </Column>
    </DataTable>
    <small class="texto-suave">
      Um envio aceito vale o peso da atividade (1 a 100), uma única vez por estudante. Atividades e pesos podem ser
      alterados até o grupo iniciar.
    </small>
  </div>
</template>

<style scoped>
.adicionar {
  align-items: flex-end;
}

.seletor {
  flex: 1;
  min-width: 16rem;
}

:deep(.peso) {
  width: 4rem;
}
</style>
