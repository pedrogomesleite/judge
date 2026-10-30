<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import Button from 'primevue/button'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import NovaAtividadeDialog from '@/components/NovaAtividadeDialog.vue'
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
const criandoAtividade = ref(false)
/** Atividades sendo removidas. */
const removendo = ref(new Set<string>())
/** Atividades com o peso sendo salvo. */
const salvandoPeso = ref(new Set<string>())

/** Peso de cada linha; salvo pouco depois da última mudança (digitação ou botões +/-). */
const pesos = ref<Record<string, number | null>>({})
const ESPERA_SALVAR_PESO_MS = 600
const timersPeso = new Map<string, ReturnType<typeof setTimeout>>()

// Saindo da tela (ou o grupo iniciando) com peso pendente: salva agora em vez de descartar
onBeforeUnmount(() => {
  timersPeso.forEach((timer, atividadeId) => {
    clearTimeout(timer)
    salvarPeso(atividadeId)
  })
})

watch(
  () => props.atividades,
  (atividades) => {
    // Linhas com peso ainda não salvo mantêm o valor digitado
    pesos.value = Object.fromEntries(
      atividades.map((a) => [a.atividadeId, emEdicao(a.atividadeId) ? pesos.value[a.atividadeId] ?? a.peso : a.peso]),
    )
  },
  { immediate: true },
)

const disponiveis = computed(() => {
  const noGrupo = new Set(props.atividades.map((a) => a.atividadeId))
  return catalogo.value.filter((a) => !noGrupo.has(a.id))
})

const pesoTotal = computed(() => props.atividades.reduce((total, a) => total + a.peso, 0))

const mensagemSemOpcoes = computed(() =>
  catalogo.value.length === 0
    ? 'O catálogo ainda não tem atividades. Use Criar atividade.'
    : 'Todas as atividades do catálogo já estão no grupo',
)

/** Peso digitado e ainda não salvo (ou salvando): o valor da tela não pode ser sobrescrito pela lista. */
function emEdicao(atividadeId: string) {
  return timersPeso.has(atividadeId) || salvandoPeso.value.has(atividadeId)
}

onMounted(async () => {
  try {
    catalogo.value = await listarAtividades()
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Erro ao carregar o catálogo de atividades', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    carregandoCatalogo.value = false
  }
})

// A atividade criada aqui entra no catálogo já selecionada, pronta para receber o peso
function aoCriarAtividade(atividade: AtividadeResponse) {
  catalogo.value = [atividade, ...catalogo.value]
  novaAtividadeId.value = atividade.id
  toast.add({
    severity: 'success',
    summary: 'Atividade criada',
    detail: 'Defina o peso e adicione-a ao grupo. Cadastre também os casos de teste oficiais na tela da atividade.',
    life: 6000,
  })
}

async function adicionar() {
  if (!novaAtividadeId.value) {
    return
  }
  adicionando.value = true
  try {
    const ordem = props.atividades.reduce((maior, a) => Math.max(maior, a.ordem), 0) + 1
    await adicionarAtividadeAoGrupo(props.grupoId, {
      atividadeId: novaAtividadeId.value,
      ordem,
      peso: limitarPeso(novoPeso.value),
    })
    novaAtividadeId.value = null
    novoPeso.value = PESO_MINIMO
    emit('alterado')
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Não foi possível adicionar a atividade', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    adicionando.value = false
  }
}

// Chamado a cada tecla (@input) e a cada clique nos botões +/- (update:model-value): o v-model do
// InputNumber só atualiza ao perder o foco, e o peso digitado se perdia se a tela mudasse antes disso
function aoMudarPeso(atividadeId: string, valor: number | null) {
  if (pesos.value[atividadeId] === valor && !timersPeso.has(atividadeId)) {
    return
  }
  pesos.value[atividadeId] = valor
  clearTimeout(timersPeso.get(atividadeId))
  timersPeso.set(
    atividadeId,
    setTimeout(() => salvarPeso(atividadeId), ESPERA_SALVAR_PESO_MS),
  )
}

function limitarPeso(peso: number) {
  return Math.min(Math.max(Math.round(peso), PESO_MINIMO), PESO_MAXIMO)
}

/** Peso salvo na API, pela lista mais recente recebida do pai. */
function pesoSalvo(atividadeId: string) {
  return props.atividades.find((a) => a.atividadeId === atividadeId)?.peso
}

async function salvarPeso(atividadeId: string) {
  timersPeso.delete(atividadeId)
  const salvo = pesoSalvo(atividadeId)
  if (salvo === undefined) {
    // A atividade saiu do grupo enquanto o peso era editado
    return
  }
  if (pesos.value[atividadeId] == null) {
    // Campo esvaziado: volta ao peso salvo
    pesos.value[atividadeId] = salvo
    return
  }
  // Digitando, o InputNumber aceita valores fora do intervalo até perder o foco
  const peso = limitarPeso(pesos.value[atividadeId]!)
  pesos.value[atividadeId] = peso
  if (peso === salvo) {
    return
  }
  salvandoPeso.value.add(atividadeId)
  try {
    await alterarPesoDaAtividade(props.grupoId, atividadeId, peso)
    emit('alterado')
  } catch (e) {
    if (!timersPeso.has(atividadeId)) {
      pesos.value[atividadeId] = pesoSalvo(atividadeId) ?? salvo
    }
    toast.add({ severity: 'error', summary: 'Não foi possível alterar o peso', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    salvandoPeso.value.delete(atividadeId)
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
  clearTimeout(timersPeso.get(atividade.atividadeId))
  timersPeso.delete(atividade.atividadeId)
  removendo.value.add(atividade.atividadeId)
  try {
    await removerAtividadeDoGrupo(props.grupoId, atividade.atividadeId)
    emit('alterado')
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Não foi possível remover a atividade', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    removendo.value.delete(atividade.atividadeId)
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
          :empty-message="mensagemSemOpcoes"
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
          class="peso"
          :use-grouping="false"
          fluid
          @input="novoPeso = ($event.value as number | null) ?? PESO_MINIMO"
        />
      </div>
      <Button
        type="submit"
        label="Adicionar"
        icon="pi pi-plus"
        :loading="adicionando"
        :disabled="!novaAtividadeId"
      />
      <Button
        type="button"
        label="Criar atividade"
        icon="pi pi-file-plus"
        severity="secondary"
        outlined
        @click="criandoAtividade = true"
      />
    </form>

    <NovaAtividadeDialog v-model:visible="criandoAtividade" @criada="aoCriarAtividade" />

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
      <Column header="Peso" style="width: 11rem">
        <template #body="{ data }">
          <InputNumber
            :model-value="pesos[data.atividadeId]"
            :min="PESO_MINIMO"
            :max="PESO_MAXIMO"
            show-buttons
            size="small"
            class="peso"
            :use-grouping="false"
            fluid
            :disabled="removendo.has(data.atividadeId)"
            :aria-label="`Peso de ${data.titulo}`"
            @input="aoMudarPeso(data.atividadeId, $event.value as number | null)"
            @update:model-value="aoMudarPeso(data.atividadeId, $event)"
          />
          <i v-if="salvandoPeso.has(data.atividadeId)" class="pi pi-spin pi-spinner texto-suave salvando" title="Salvando" />
        </template>
      </Column>
      <Column style="width: 4rem">
        <template #body="{ data }">
          <Button
            icon="pi pi-trash"
            severity="danger"
            text
            rounded
            :loading="removendo.has(data.atividadeId)"
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

/* Largura no componente inteiro, não só no <input>: no layout stacked, os botões +/- ficam
   posicionados sobre a borda direita e, com o input estreito, cobriam o campo e bloqueavam a digitação */
.peso {
  width: 6.5rem;
}

.salvando {
  margin-left: 0.5rem;
}
</style>
