<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Accordion from 'primevue/accordion'
import AccordionPanel from 'primevue/accordionpanel'
import AccordionHeader from 'primevue/accordionheader'
import AccordionContent from 'primevue/accordioncontent'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import CodigoSolucaoDialog from '@/components/CodigoSolucaoDialog.vue'
import ResultadoTag from '@/components/ResultadoTag.vue'
import { usePolling } from '@/composables/usePolling'
import { listarMembrosDoGrupo } from '@/api/grupos'
import { mensagemDeErro } from '@/api/http'
import { buscarCodigoSolucao, listarSolucoesDoGrupo } from '@/api/solucoes'
import type {
  GrupoAtividadeItemResponse,
  MembroResponse,
  PapelGrupo,
  SolucaoCodigoResponse,
  SolucaoResponse,
} from '@/api/types'
import { baixarTexto, carimboDeArquivo, paraNomeDeArquivo } from '@/utils/arquivos'
import { formatarDataHora } from '@/utils/formatacao'
import { classificar, ROTULOS_PAPEL } from '@/utils/rotulos'

// Consolidação dos envios do grupo por atividade. Só para avaliadores: a API devolve a eles todas as soluções do grupo

const INTERVALO_ATUALIZACAO_MS = 3000

interface Envio extends SolucaoResponse {
  /** Nome de quem enviou, para exibir e ordenar. */
  membro: string
  papel: PapelGrupo | null
}

const props = defineProps<{
  grupoId: string
  atividades: GrupoAtividadeItemResponse[]
}>()

const toast = useToast()

const solucoes = ref<SolucaoResponse[]>([])
const membros = ref<MembroResponse[]>([])
const carregando = ref(true)
const atualizando = ref(false)
const erro = ref<string | null>(null)

const totalEstudantes = computed(() => membros.value.filter((m) => m.papel === 'ESTUDANTE').length)

const envios = computed<Envio[]>(() => {
  const porId = new Map(membros.value.map((m) => [m.usuarioId, m]))
  return solucoes.value.map((solucao) => {
    const membro = porId.get(solucao.autorId)
    return { ...solucao, membro: membro?.nome ?? 'Membro', papel: membro?.papel ?? null }
  })
})

/** Cada atividade com seus envios e quantos estudantes enviaram e tiveram resposta aceita. */
const porAtividade = computed(() =>
  props.atividades.map((atividade, indice) => {
    const daAtividade = envios.value.filter((e) => e.atividadeId === atividade.atividadeId)
    const deEstudantes = daAtividade.filter((e) => e.papel === 'ESTUDANTE')
    return {
      atividade,
      numero: indice + 1,
      envios: daAtividade,
      enviaram: new Set(deEstudantes.map((e) => e.autorId)).size,
      aceitos: new Set(deEstudantes.filter((e) => e.veredito === 'ACEITA').map((e) => e.autorId)).size,
    }
  }),
)

const emAndamento = computed(() => solucoes.value.some((s) => classificar(s) === 'andamento'))

async function atualizar() {
  try {
    const [lista, pessoas] = await Promise.all([listarSolucoesDoGrupo(props.grupoId), listarMembrosDoGrupo(props.grupoId)])
    solucoes.value = lista
    membros.value = pessoas
    erro.value = null
  } catch (e) {
    erro.value = mensagemDeErro(e)
  }
}

// Enquanto houver envio sem veredito, atualiza a lista
const { agendar } = usePolling(atualizar, () => emAndamento.value, INTERVALO_ATUALIZACAO_MS)

async function recarregar() {
  atualizando.value = true
  await atualizar()
  atualizando.value = false
  agendar()
}

watch(
  () => props.grupoId,
  async () => {
    carregando.value = true
    await recarregar()
    carregando.value = false
  },
  { immediate: true },
)

const titulos = computed(() => new Map(props.atividades.map((a) => [a.atividadeId, a.titulo])))

function tituloDa(atividadeId: string) {
  return titulos.value.get(atividadeId) ?? 'Atividade'
}

/** O código de uma solução não muda: guarda o que já foi carregado. */
const codigos = new Map<string, SolucaoCodigoResponse>()

async function obterCodigo(solucaoId: string) {
  let codigo = codigos.get(solucaoId)
  if (!codigo) {
    codigo = await buscarCodigoSolucao(solucaoId)
    codigos.set(solucaoId, codigo)
  }
  return codigo
}

const dialogoVisivel = ref(false)
const selecionadoId = ref<string | null>(null)
// Derivado da lista para o resultado acompanhar o polling com o diálogo aberto
const selecionado = computed(() => envios.value.find((e) => e.id === selecionadoId.value) ?? null)
const codigoSelecionado = ref<SolucaoCodigoResponse | null>(null)
const erroCodigo = ref<string | null>(null)

async function verCodigo(envio: Envio) {
  selecionadoId.value = envio.id
  codigoSelecionado.value = null
  erroCodigo.value = null
  dialogoVisivel.value = true
  try {
    const codigo = await obterCodigo(envio.id)
    // Outra resposta pode ter sido aberta enquanto esta carregava
    if (selecionadoId.value === envio.id) {
      codigoSelecionado.value = codigo
    }
  } catch (e) {
    if (selecionadoId.value === envio.id) {
      erroCodigo.value = mensagemDeErro(e)
    }
  }
}

/** Id do envio sendo baixado, para mostrar o carregamento no botão. */
const baixando = ref<string | null>(null)

async function baixar(envio: Envio) {
  baixando.value = envio.id
  try {
    const codigo = await obterCodigo(envio.id)
    const partes = [tituloDa(envio.atividadeId), envio.membro].map(paraNomeDeArquivo).filter(Boolean)
    baixarTexto(`${[...partes, carimboDeArquivo(envio.enviadoEm)].join('_')}.${codigo.extensao}`, codigo.codigo)
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Não foi possível baixar a resposta', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    baixando.value = null
  }
}

function plural(quantidade: number, singular: string, plural: string) {
  return `${quantidade} ${quantidade === 1 ? singular : plural}`
}
</script>

<template>
  <div class="stack">
    <div class="row">
      <strong>Envios dos membros</strong>
      <span class="texto-suave">{{ plural(totalEstudantes, 'estudante', 'estudantes') }} no grupo</span>
      <span class="spacer" />
      <Button
        label="Atualizar"
        icon="pi pi-refresh"
        size="small"
        severity="secondary"
        text
        :loading="atualizando"
        @click="recarregar"
      />
    </div>

    <Message v-if="erro" severity="error" size="small">{{ erro }}</Message>

    <span v-if="carregando" class="texto-suave"><i class="pi pi-spin pi-spinner" /> Carregando envios…</span>
    <Accordion v-else multiple>
      <AccordionPanel v-for="item in porAtividade" :key="item.atividade.atividadeId" :value="item.atividade.atividadeId">
        <AccordionHeader>
          <span class="cabecalho">
            <span class="titulo">{{ item.numero }}. {{ item.atividade.titulo }}</span>
            <span class="spacer" />
            <Tag :value="plural(item.envios.length, 'envio', 'envios')" severity="secondary" />
            <Tag
              :value="`Enviaram ${item.enviaram}/${totalEstudantes}`"
              severity="info"
              title="Estudantes com pelo menos um envio"
            />
            <Tag
              :value="`Aceitas ${item.aceitos}/${totalEstudantes}`"
              :severity="totalEstudantes > 0 && item.aceitos === totalEstudantes ? 'success' : 'secondary'"
              title="Estudantes com pelo menos uma resposta aceita"
            />
          </span>
        </AccordionHeader>
        <AccordionContent>
          <div class="stack">
            <RouterLink
              :to="{ name: 'atividade', params: { id: item.atividade.atividadeId }, query: { groupId: grupoId } }"
            >
              Abrir atividade
            </RouterLink>

            <DataTable
              :value="item.envios"
              data-key="id"
              size="small"
              sort-field="enviadoEm"
              :sort-order="-1"
              paginator
              :rows="10"
              :always-show-paginator="false"
            >
              <template #empty>Nenhum envio para esta atividade.</template>
              <Column field="membro" header="Membro" sortable>
                <template #body="{ data }">
                  <span class="row membro">
                    {{ data.membro }}
                    <Tag
                      v-if="data.papel === 'AVALIADOR'"
                      :value="ROTULOS_PAPEL.AVALIADOR.label"
                      :severity="ROTULOS_PAPEL.AVALIADOR.severidade"
                    />
                  </span>
                </template>
              </Column>
              <Column field="enviadoEm" header="Enviada em" sortable>
                <template #body="{ data }">{{ formatarDataHora(data.enviadoEm) }}</template>
              </Column>
              <Column field="linguagem" header="Linguagem" />
              <Column header="Resultado">
                <template #body="{ data }"><ResultadoTag :solucao="data" /></template>
              </Column>
              <Column header="Resposta" style="width: 12rem">
                <template #body="{ data }">
                  <span class="acoes">
                    <Button label="Ver" icon="pi pi-eye" size="small" text @click="verCodigo(data)" />
                    <Button
                      label="Baixar"
                      icon="pi pi-download"
                      size="small"
                      text
                      :loading="baixando === data.id"
                      @click="baixar(data)"
                    />
                  </span>
                </template>
              </Column>
            </DataTable>
          </div>
        </AccordionContent>
      </AccordionPanel>
    </Accordion>

    <CodigoSolucaoDialog
      v-model:visible="dialogoVisivel"
      :solucao="selecionado"
      :membro="selecionado?.membro ?? ''"
      :atividade="selecionado ? tituloDa(selecionado.atividadeId) : ''"
      :codigo="codigoSelecionado"
      :erro="erroCodigo"
      @baixar="selecionado && baixar(selecionado)"
    />
  </div>
</template>

<style scoped>
.cabecalho {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin-right: 1rem;
}

.titulo {
  font-weight: 600;
}

.membro {
  gap: 0.5rem;
}

.acoes {
  display: flex;
  gap: 0.25rem;
  white-space: nowrap;
}
</style>
