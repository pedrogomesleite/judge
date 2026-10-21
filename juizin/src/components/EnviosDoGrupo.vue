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
import { ROTULOS_PAPEL } from '@/utils/rotulos'

// Envios do grupo por atividade. A API devolve ao avaliador todas as soluções do grupo e ao estudante só as próprias

interface Envio extends SolucaoResponse {
  /** Nome de quem enviou, para exibir e ordenar. */
  membro: string
  papel: PapelGrupo | null
  /** Posição do envio entre os do mesmo autor para a mesma atividade, do mais antigo (1) ao mais recente. */
  tentativa: number
}

const props = defineProps<{
  grupoId: string
  atividades: GrupoAtividadeItemResponse[]
  membros: MembroResponse[]
  /** Papel de quem vê: o estudante vê só os próprios envios, sem a coluna de membro. */
  papel: PapelGrupo
  /** Muda a cada envio ou veredito no grupo (SSE): busca a lista de novo. */
  revisao: number
}>()

const toast = useToast()

const avaliador = computed(() => props.papel === 'AVALIADOR')

const solucoes = ref<SolucaoResponse[]>([])
const carregando = ref(true)
const atualizando = ref(false)
const erro = ref<string | null>(null)

const totalEstudantes = computed(() => props.membros.filter((m) => m.papel === 'ESTUDANTE').length)

/** Número da tentativa de cada solução, por autor e atividade, em ordem de envio. */
const tentativas = computed(() => {
  const numeros = new Map<string, number>()
  const contagem = new Map<string, number>()
  const emOrdem = [...solucoes.value].sort((a, b) => Date.parse(a.enviadoEm) - Date.parse(b.enviadoEm))
  for (const solucao of emOrdem) {
    const chave = `${solucao.autorId}:${solucao.atividadeId}`
    const numero = (contagem.get(chave) ?? 0) + 1
    contagem.set(chave, numero)
    numeros.set(solucao.id, numero)
  }
  return numeros
})

const envios = computed<Envio[]>(() => {
  const porId = new Map(props.membros.map((m) => [m.usuarioId, m]))
  return solucoes.value.map((solucao) => {
    const membro = porId.get(solucao.autorId)
    return {
      ...solucao,
      membro: membro?.nome ?? 'Ex-membro',
      papel: membro?.papel ?? null,
      tentativa: tentativas.value.get(solucao.id) ?? 1,
    }
  })
})

/** Cada atividade com seus envios e quantos estudantes enviaram e tiveram resposta aceita (aceites repetidos não somam). */
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
      aceitaPorMim: daAtividade.some((e) => e.veredito === 'ACEITA'),
    }
  }),
)

async function atualizar() {
  try {
    solucoes.value = await listarSolucoesDoGrupo(props.grupoId)
    erro.value = null
  } catch (e) {
    erro.value = mensagemDeErro(e)
  }
}

async function recarregar() {
  atualizando.value = true
  await atualizar()
  atualizando.value = false
}

watch(
  () => props.grupoId,
  async () => {
    carregando.value = true
    await atualizar()
    carregando.value = false
  },
  { immediate: true },
)

watch(() => props.revisao, atualizar)

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
// Derivado da lista para o resultado acompanhar as atualizações com o diálogo aberto
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
    const partes = [tituloDa(envio.atividadeId), envio.membro, `tentativa-${envio.tentativa}`]
      .map(paraNomeDeArquivo)
      .filter(Boolean)
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
      <strong>{{ avaliador ? 'Envios dos membros' : 'Atividades e seus envios' }}</strong>
      <span v-if="avaliador" class="texto-suave">{{ plural(totalEstudantes, 'estudante', 'estudantes') }} no grupo</span>
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
    <span v-else-if="atividades.length === 0" class="texto-suave">Nenhuma atividade neste grupo.</span>
    <Accordion v-else multiple>
      <AccordionPanel v-for="item in porAtividade" :key="item.atividade.atividadeId" :value="item.atividade.atividadeId">
        <AccordionHeader>
          <span class="cabecalho">
            <span class="titulo">{{ item.numero }}. {{ item.atividade.titulo }}</span>
            <span class="spacer" />
            <Tag :value="`Peso ${item.atividade.peso}`" severity="secondary" />
            <Tag :value="plural(item.envios.length, 'envio', 'envios')" severity="secondary" />
            <template v-if="avaliador">
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
            </template>
            <Tag v-else-if="item.aceitaPorMim" value="Aceita" severity="success" icon="pi pi-check" />
          </span>
        </AccordionHeader>
        <AccordionContent>
          <div class="stack">
            <div>
              <Button
                as="router-link"
                :to="{ name: 'atividade', params: { id: item.atividade.atividadeId }, query: { groupId: grupoId } }"
                :label="avaliador ? 'Abrir atividade' : 'Abrir e enviar resposta'"
                :icon="avaliador ? 'pi pi-external-link' : 'pi pi-send'"
                size="small"
                :severity="avaliador ? 'secondary' : undefined"
                :text="avaliador"
              />
            </div>

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
              <template #empty>
                {{ avaliador ? 'Nenhum envio para esta atividade.' : 'Você ainda não enviou resposta para esta atividade.' }}
              </template>
              <Column v-if="avaliador" field="membro" header="Membro" sortable>
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
              <Column field="tentativa" header="Tentativa" sortable style="width: 7rem">
                <template #body="{ data }">#{{ data.tentativa }}</template>
              </Column>
              <Column field="enviadoEm" header="Enviada em" sortable>
                <template #body="{ data }">{{ formatarDataHora(data.enviadoEm) }}</template>
              </Column>
              <Column field="linguagem" header="Linguagem" />
              <Column header="Retorno">
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
