<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Card from 'primevue/card'
import Tag from 'primevue/tag'
import Select from 'primevue/select'
import FileUpload, { type FileUploadUploaderEvent } from 'primevue/fileupload'
import Textarea from 'primevue/textarea'
import Button from 'primevue/button'
import Message from 'primevue/message'
import CasosCompartilhados from '@/components/CasosCompartilhados.vue'
import CasosDeTesteLista from '@/components/CasosDeTesteLista.vue'
import CasosOficiaisCard from '@/components/CasosOficiaisCard.vue'
import NovoCasoDeTesteDialog from '@/components/NovoCasoDeTesteDialog.vue'
import ResultadoTag from '@/components/ResultadoTag.vue'
import { useEventosDaSolucao } from '@/composables/useEventosDaSolucao'
import { buscarAtividade } from '@/api/atividades'
import { listarCasosCompartilhados, listarCasosPublicos } from '@/api/casosDeTeste'
import { mensagemDeErro } from '@/api/http'
import { listarLinguagens } from '@/api/linguagens'
import { enviarSolucao } from '@/api/solucoes'
import type { AtividadeResponse, CasoDeTesteResponse, LinguagemResponse, SolucaoResponse } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { comUnidade } from '@/utils/formatacao'
import { classificar } from '@/utils/rotulos'

// Mesmo limite do SolucaoRequest.codigo
const TAMANHO_MAXIMO_CODIGO = 65_536

interface Rascunho {
  linguagemId: string | null
  codigo: string
}

const props = defineProps<{ id: string }>()

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const grupoId = computed(() => (typeof route.query.groupId === 'string' ? route.query.groupId : null))

const atividade = ref<AtividadeResponse | null>(null)
const linguagens = ref<LinguagemResponse[]>([])
const carregando = ref(true)
const erroCarregamento = ref<string | null>(null)

const casosPublicos = ref<CasoDeTesteResponse[]>([])
const casosCompartilhados = ref<CasoDeTesteResponse[]>([])
const erroCasos = ref<string | null>(null)
const compartilhandoCaso = ref(false)

const ehAutor = computed(() => !!atividade.value && auth.usuario?.id === atividade.value.autorId)

const linguagemId = ref<string | null>(null)
const codigo = ref('')
const enviando = ref(false)
const erroEnvio = ref<string | null>(null)
const solucao = ref<SolucaoResponse | null>(null)

const opcoesLinguagem = computed(() =>
  linguagens.value.map((l) => ({ id: l.id, rotulo: `${l.nome} (${l.versao})` })),
)
const emAvaliacao = computed(() => solucao.value !== null && classificar(solucao.value) === 'andamento')
const podeEnviar = computed(
  () => !!linguagemId.value && codigo.value.trim().length > 0 && !enviando.value && !emAvaliacao.value,
)

// O status e o veredito chegam pelo SSE da solução, até o fim da avaliação
const { acompanhar, encerrar: encerrarAcompanhamento } = useEventosDaSolucao({
  aoAtualizar: (atualizada) => {
    solucao.value = atualizada
    erroEnvio.value = null
  },
  aoNegar: (e) => {
    erroEnvio.value = mensagemDeErro(e)
  },
})

async function carregar() {
  carregando.value = true
  erroCarregamento.value = null
  encerrarAcompanhamento()
  solucao.value = null
  try {
    const [dados, opcoes] = await Promise.all([buscarAtividade(props.id), listarLinguagens()])
    atividade.value = dados
    linguagens.value = opcoes
    linguagemId.value = opcoes[0]?.id ?? null
    restaurarRascunho()
  } catch (e) {
    erroCarregamento.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
}

watch(() => props.id, carregar, { immediate: true })

// Exemplos (casos públicos) para todos; os casos dos grupos só para quem está logado.
// Carregados de forma independente: uma falha nos casos dos grupos não esconde os exemplos
async function carregarCasos() {
  const [publicos, compartilhados] = await Promise.allSettled([
    listarCasosPublicos(props.id),
    auth.autenticado ? listarCasosCompartilhados(props.id) : Promise.resolve<CasoDeTesteResponse[]>([]),
  ])
  casosPublicos.value = publicos.status === 'fulfilled' ? publicos.value : []
  casosCompartilhados.value = compartilhados.status === 'fulfilled' ? compartilhados.value : []
  const falha = [publicos, compartilhados].find((r): r is PromiseRejectedResult => r.status === 'rejected')
  erroCasos.value = falha ? mensagemDeErro(falha.reason) : null
}

watch([() => props.id, () => auth.autenticado], carregarCasos, { immediate: true })

async function carregarArquivo(evento: FileUploadUploaderEvent) {
  const arquivo = Array.isArray(evento.files) ? evento.files[0] : evento.files
  if (arquivo) {
    codigo.value = await arquivo.text()
  }
}

async function enviar() {
  if (!atividade.value || !linguagemId.value) {
    return
  }
  erroEnvio.value = null
  enviando.value = true
  try {
    solucao.value = await enviarSolucao({
      atividadeId: atividade.value.id,
      linguagemId: linguagemId.value,
      grupoId: grupoId.value,
      codigo: codigo.value,
    })
    acompanhar(solucao.value)
  } catch (e) {
    erroEnvio.value = mensagemDeErro(e)
  } finally {
    enviando.value = false
  }
}

// Visitante: guarda o rascunho, faz login e volta para esta mesma tela

function chaveRascunho() {
  return `juizin.rascunho.${props.id}`
}

function entrarParaEnviar() {
  try {
    const rascunho: Rascunho = { linguagemId: linguagemId.value, codigo: codigo.value }
    sessionStorage.setItem(chaveRascunho(), JSON.stringify(rascunho))
  } catch {
    // Sem sessionStorage o rascunho se perde, mas o login segue normalmente
  }
  router.push({ name: 'login', query: { redirect: route.fullPath } })
}

function restaurarRascunho() {
  try {
    const salvo = sessionStorage.getItem(chaveRascunho())
    if (!salvo) {
      return
    }
    sessionStorage.removeItem(chaveRascunho())
    const rascunho = JSON.parse(salvo) as Partial<Rascunho>
    codigo.value = rascunho.codigo ?? ''
    if (linguagens.value.some((l) => l.id === rascunho.linguagemId)) {
      linguagemId.value = rascunho.linguagemId ?? null
    }
  } catch {
    // Rascunho ilegível ou sessionStorage indisponível: começa vazio
  }
}
</script>

<template>
  <section class="stack">
    <RouterLink v-if="grupoId" :to="{ name: 'grupo', params: { id: grupoId } }">
      <i class="pi pi-arrow-left" /> Voltar ao grupo
    </RouterLink>
    <RouterLink v-else :to="{ name: 'atividades' }"><i class="pi pi-arrow-left" /> Atividades</RouterLink>

    <span v-if="carregando" class="texto-suave"><i class="pi pi-spin pi-spinner" /> Carregando atividade…</span>
    <Message v-else-if="erroCarregamento" severity="error">{{ erroCarregamento }}</Message>

    <template v-else-if="atividade">
      <div class="stack">
        <h1>{{ atividade.titulo }}</h1>
        <div class="row">
          <Tag severity="secondary" icon="pi pi-clock" :value="`Tempo limite: ${atividade.limiteTempoMs} ms`" />
          <Tag severity="secondary" icon="pi pi-server" :value="`Memória limite: ${atividade.limiteMemoriaMb} MB`" />
        </div>
        <Card>
          <template #content>
            <div class="enunciado">{{ atividade.enunciado }}</div>
          </template>
        </Card>
      </div>

      <Card v-if="casosPublicos.length">
        <template #content>
          <CasosDeTesteLista :casos="casosPublicos" rotulo="Exemplo" />
        </template>
      </Card>

      <Card v-if="auth.autenticado && (casosCompartilhados.length || grupoId)">
        <template #title>
          <div class="row">
            <span>Casos compartilhados nos seus grupos</span>
            <span class="spacer" />
            <Button
              v-if="grupoId"
              label="Compartilhar caso no grupo"
              icon="pi pi-share-alt"
              size="small"
              @click="compartilhandoCaso = true"
            />
          </div>
        </template>
        <template #subtitle>
          Criados por membros dos grupos de que você participa. Servem para testar e não contam no veredito.
        </template>
        <template #content>
          <CasosCompartilhados
            v-if="casosCompartilhados.length"
            :casos="casosCompartilhados"
            :grupo-atual-id="grupoId"
          />
          <span v-else class="texto-suave">Nenhum caso compartilhado nos seus grupos para esta atividade ainda.</span>
        </template>
      </Card>

      <Message v-if="erroCasos" severity="warn">Não foi possível carregar os casos de teste: {{ erroCasos }}</Message>

      <NovoCasoDeTesteDialog
        v-if="grupoId"
        v-model:visible="compartilhandoCaso"
        :atividade-id="atividade.id"
        :grupo-id="grupoId"
        @criado="carregarCasos"
      />

      <Card>
        <template #title>Enviar resposta</template>
        <template #content>
          <form class="stack" @submit.prevent="enviar">
            <Message v-if="grupoId" severity="info">
              Envio dentro do grupo: a resposta conta para o grupo de atividade.
            </Message>

            <div class="row">
              <div class="campo">
                <label for="linguagem">Linguagem</label>
                <Select
                  v-model="linguagemId"
                  input-id="linguagem"
                  :options="opcoesLinguagem"
                  option-label="rotulo"
                  option-value="id"
                  placeholder="Selecione"
                />
              </div>
              <span class="spacer" />
              <FileUpload
                mode="basic"
                custom-upload
                auto
                accept=".c,.cpp,.cc,.h,.hpp,.txt"
                :max-file-size="TAMANHO_MAXIMO_CODIGO"
                invalid-file-size-message="O arquivo passa de {1}"
                choose-label="Carregar arquivo"
                choose-icon="pi pi-upload"
                @uploader="carregarArquivo"
              />
            </div>

            <div class="campo">
              <label for="codigo">Código</label>
              <Textarea
                id="codigo"
                v-model="codigo"
                class="mono"
                rows="16"
                :maxlength="TAMANHO_MAXIMO_CODIGO"
                spellcheck="false"
                placeholder="Cole o código aqui ou carregue um arquivo"
                fluid
              />
            </div>

            <Message v-if="erroEnvio" severity="error">{{ erroEnvio }}</Message>

            <div class="row">
              <Button
                v-if="auth.autenticado"
                type="submit"
                label="Enviar"
                icon="pi pi-send"
                :loading="enviando"
                :disabled="!podeEnviar"
              />
              <template v-else>
                <Button type="button" label="Entrar para enviar" icon="pi pi-sign-in" @click="entrarParaEnviar" />
                <span class="texto-suave">O código fica guardado enquanto você entra.</span>
              </template>
            </div>

            <div v-if="solucao" class="row resultado">
              <strong>Resultado:</strong>
              <ResultadoTag :solucao="solucao" />
              <span class="texto-suave">Tempo: {{ comUnidade(solucao.tempoMs, 'ms') }}</span>
              <span class="texto-suave">Memória: {{ comUnidade(solucao.memoriaKb, 'KB') }}</span>
              <span class="spacer" />
              <RouterLink :to="{ name: 'solucoes' }">Ver minhas respostas</RouterLink>
            </div>
          </form>
        </template>
      </Card>

      <CasosOficiaisCard v-if="ehAutor" :atividade-id="atividade.id" @publicos-alterados="carregarCasos" />
    </template>
  </section>
</template>

<style scoped>
.enunciado {
  white-space: pre-wrap;
}

.resultado {
  padding: 0.75rem 1rem;
  border: 1px solid var(--p-content-border-color);
  border-radius: var(--p-content-border-radius);
}
</style>
