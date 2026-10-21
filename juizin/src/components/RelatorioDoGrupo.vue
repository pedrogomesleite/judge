<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Accordion from 'primevue/accordion'
import AccordionPanel from 'primevue/accordionpanel'
import AccordionHeader from 'primevue/accordionheader'
import AccordionContent from 'primevue/accordioncontent'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import ColumnGroup from 'primevue/columngroup'
import Row from 'primevue/row'
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { buscarRelatorioDoGrupo } from '@/api/grupos'
import { mensagemDeErro } from '@/api/http'
import type {
  DesempenhoAtividadeResponse,
  DesempenhoEstudanteResponse,
  GrupoAtividadeItemResponse,
  PapelGrupo,
} from '@/api/types'
import { baixarTexto, carimboDeArquivo, paraNomeDeArquivo } from '@/utils/arquivos'
import { gerarCsv, TIPO_CSV, type CelulaCsv } from '@/utils/csv'

// Consolidação final do grupo. Avaliador: todos os estudantes, por atividade, com exportação CSV.
// Estudante: a própria pontuação por atividade. Um envio aceito vale o peso da atividade, uma única vez.

interface LinhaEstudante extends DesempenhoAtividadeResponse {
  usuarioId: string
  nome: string
}

const props = defineProps<{
  grupoId: string
  grupoNome: string
  atividades: GrupoAtividadeItemResponse[]
  papel: PapelGrupo
  /** Muda a cada alteração no grupo (SSE): um veredito pendente pode chegar depois da finalização. */
  revisao: number
}>()

const relatorio = ref<DesempenhoEstudanteResponse[]>([])
const carregando = ref(true)
const erro = ref<string | null>(null)

async function atualizar() {
  try {
    relatorio.value = await buscarRelatorioDoGrupo(props.grupoId)
    erro.value = null
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
}

watch([() => props.grupoId, () => props.revisao], atualizar, { immediate: true })

const pontuacaoMaxima = computed(() => props.atividades.reduce((total, a) => total + a.peso, 0))

/** Desempenho de cada estudante indexado pela atividade. */
const porEstudante = computed(
  () => new Map(relatorio.value.map((e) => [e.usuarioId, new Map(e.atividades.map((a) => [a.atividadeId, a]))])),
)

function desempenhoDe(usuarioId: string, atividadeId: string): DesempenhoAtividadeResponse | undefined {
  return porEstudante.value.get(usuarioId)?.get(atividadeId)
}

const totais = computed(() => [...relatorio.value].sort((a, b) => b.pontos - a.pontos || a.nome.localeCompare(b.nome, 'pt-BR')))

/** Avaliador: cada atividade com uma linha por estudante. */
const porAtividade = computed(() =>
  props.atividades.map((atividade, indice) => {
    const linhas: LinhaEstudante[] = relatorio.value.map((estudante) => ({
      usuarioId: estudante.usuarioId,
      nome: estudante.nome,
      ...(desempenhoDe(estudante.usuarioId, atividade.atividadeId) ?? semEnvio(atividade)),
    }))
    return {
      atividade,
      numero: indice + 1,
      linhas,
      aceitas: linhas.filter((l) => l.aceita).length,
    }
  }),
)

/** Estudante: a própria linha (a API devolve só ela). */
const meu = computed(() => relatorio.value[0] ?? null)

const minhasAtividades = computed(() =>
  props.atividades.map((atividade, indice) => ({
    atividade,
    numero: indice + 1,
    ...(meu.value ? (desempenhoDe(meu.value.usuarioId, atividade.atividadeId) ?? semEnvio(atividade)) : semEnvio(atividade)),
  })),
)

function semEnvio(atividade: GrupoAtividadeItemResponse): DesempenhoAtividadeResponse {
  return { atividadeId: atividade.atividadeId, peso: atividade.peso, tentativas: 0, aceita: false, pontos: 0 }
}

function rotuloResultado(desempenho: Pick<DesempenhoAtividadeResponse, 'aceita' | 'tentativas'>) {
  if (desempenho.aceita) {
    return { label: 'Aceita', severidade: 'success' as const, icone: 'pi pi-check' }
  }
  return desempenho.tentativas > 0
    ? { label: 'Não aceita', severidade: 'danger' as const, icone: 'pi pi-times' }
    : { label: 'Sem envios', severidade: 'secondary' as const, icone: 'pi pi-minus' }
}

function plural(quantidade: number, singular: string, plural: string) {
  return `${quantidade} ${quantidade === 1 ? singular : plural}`
}

/** Planilha com uma linha por estudante e, para cada atividade, as tentativas e os pontos. */
function exportarCsv() {
  const cabecalho: CelulaCsv[] = ['Estudante', 'Atividades aceitas', 'Pontos', 'Pontuação máxima']
  props.atividades.forEach((atividade, indice) => {
    const nome = `${indice + 1}. ${atividade.titulo} (peso ${atividade.peso})`
    cabecalho.push(`${nome} - tentativas`, `${nome} - pontos`)
  })

  const linhas: CelulaCsv[][] = totais.value.map((estudante) => {
    const linha: CelulaCsv[] = [estudante.nome, estudante.aceitas, estudante.pontos, pontuacaoMaxima.value]
    for (const atividade of props.atividades) {
      const desempenho = desempenhoDe(estudante.usuarioId, atividade.atividadeId) ?? semEnvio(atividade)
      linha.push(desempenho.tentativas, desempenho.pontos)
    }
    return linha
  })

  const nomeArquivo = [paraNomeDeArquivo(props.grupoNome) || 'grupo', 'resultado', carimboDeArquivo(new Date().toISOString())]
  baixarTexto(`${nomeArquivo.join('_')}.csv`, gerarCsv([cabecalho, ...linhas]), TIPO_CSV)
}
</script>

<template>
  <div class="stack">
    <Message v-if="erro" severity="error" size="small">{{ erro }}</Message>
    <span v-if="carregando" class="texto-suave"><i class="pi pi-spin pi-spinner" /> Carregando resultado…</span>

    <!-- Avaliador -->
    <template v-else-if="papel === 'AVALIADOR'">
      <div class="row">
        <strong>Resultado por estudante</strong>
        <span class="texto-suave">Pontuação máxima: {{ pontuacaoMaxima }}</span>
        <span class="spacer" />
        <Button
          label="Exportar CSV"
          icon="pi pi-file-export"
          size="small"
          severity="secondary"
          :disabled="relatorio.length === 0"
          @click="exportarCsv"
        />
      </div>

      <DataTable :value="totais" data-key="usuarioId" size="small" paginator :rows="20" :always-show-paginator="false">
        <template #empty>Nenhum estudante no grupo.</template>
        <Column header="#" style="width: 3rem">
          <template #body="{ index }">{{ index + 1 }}</template>
        </Column>
        <Column field="nome" header="Estudante" />
        <Column header="Aceitas" style="width: 8rem">
          <template #body="{ data }">{{ data.aceitas }}/{{ atividades.length }}</template>
        </Column>
        <Column header="Pontos" style="width: 8rem">
          <template #body="{ data }">
            <strong>{{ data.pontos }}</strong><span class="texto-suave">/{{ pontuacaoMaxima }}</span>
          </template>
        </Column>
      </DataTable>

      <strong>Envios por atividade</strong>
      <Accordion multiple>
        <AccordionPanel v-for="item in porAtividade" :key="item.atividade.atividadeId" :value="item.atividade.atividadeId">
          <AccordionHeader>
            <span class="cabecalho">
              <span class="titulo">{{ item.numero }}. {{ item.atividade.titulo }}</span>
              <span class="spacer" />
              <Tag :value="`Peso ${item.atividade.peso}`" severity="info" />
              <Tag
                :value="`Aceitas ${item.aceitas}/${item.linhas.length}`"
                :severity="item.linhas.length > 0 && item.aceitas === item.linhas.length ? 'success' : 'secondary'"
                title="Estudantes com pelo menos uma resposta aceita"
              />
            </span>
          </AccordionHeader>
          <AccordionContent>
            <DataTable :value="item.linhas" data-key="usuarioId" size="small" sort-field="nome" :sort-order="1">
              <template #empty>Nenhum estudante no grupo.</template>
              <Column field="nome" header="Estudante" sortable />
              <Column field="tentativas" header="Tentativas" sortable style="width: 8rem" />
              <Column header="Sucesso" style="width: 10rem">
                <template #body="{ data }">
                  <Tag
                    :value="rotuloResultado(data).label"
                    :severity="rotuloResultado(data).severidade"
                    :icon="rotuloResultado(data).icone"
                  />
                </template>
              </Column>
              <Column field="pontos" header="Pontos" sortable style="width: 8rem">
                <template #body="{ data }">{{ data.pontos }}/{{ data.peso }}</template>
              </Column>
            </DataTable>
          </AccordionContent>
        </AccordionPanel>
      </Accordion>
    </template>

    <!-- Estudante -->
    <template v-else>
      <div class="row">
        <strong>Sua pontuação</strong>
        <span class="spacer" />
        <span v-if="meu" class="pontuacao">
          {{ meu.pontos }}<span class="texto-suave"> de {{ pontuacaoMaxima }} pontos</span>
        </span>
      </div>

      <DataTable :value="minhasAtividades" data-key="atividadeId" size="small">
        <template #empty>Nenhuma atividade neste grupo.</template>
        <Column header="#" style="width: 3rem">
          <template #body="{ data }">{{ data.numero }}</template>
        </Column>
        <Column header="Atividade">
          <template #body="{ data }">
            <RouterLink :to="{ name: 'atividade', params: { id: data.atividadeId } }">{{ data.atividade.titulo }}</RouterLink>
          </template>
        </Column>
        <Column field="peso" header="Peso" style="width: 6rem" />
        <Column header="Tentativas" style="width: 8rem">
          <template #body="{ data }">{{ plural(data.tentativas, 'envio', 'envios') }}</template>
        </Column>
        <Column header="Resultado" style="width: 10rem">
          <template #body="{ data }">
            <Tag
              :value="rotuloResultado(data).label"
              :severity="rotuloResultado(data).severidade"
              :icon="rotuloResultado(data).icone"
            />
          </template>
        </Column>
        <Column header="Pontos" style="width: 7rem">
          <template #body="{ data }"><strong>{{ data.pontos }}</strong></template>
        </Column>
        <ColumnGroup type="footer">
          <Row>
            <Column footer="Total" :colspan="5" footer-style="text-align: right" />
            <Column :footer="meu ? `${meu.pontos}/${pontuacaoMaxima}` : `0/${pontuacaoMaxima}`" />
          </Row>
        </ColumnGroup>
      </DataTable>
    </template>
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

.pontuacao {
  font-size: 1.25rem;
  font-weight: 600;
}
</style>
