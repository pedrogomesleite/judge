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
import ResultadoTag from '@/components/ResultadoTag.vue'
import { usePolling } from '@/composables/usePolling'
import { buscarAtividade } from '@/api/atividades'
import { mensagemDeErro } from '@/api/http'
import { listarLinguagens } from '@/api/linguagens'
import { buscarSolucao, enviarSolucao } from '@/api/solucoes'
import type { AtividadeResponse, LinguagemResponse, SolucaoResponse } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { comUnidade } from '@/utils/formatacao'
import { classificar } from '@/utils/rotulos'

// Mesmo limite do SolucaoRequest.codigo
const TAMANHO_MAXIMO_CODIGO = 65_536
const INTERVALO_ACOMPANHAMENTO_MS = 2000

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

const { agendar } = usePolling(acompanharSolucao, () => emAvaliacao.value, INTERVALO_ACOMPANHAMENTO_MS)

async function carregar() {
  carregando.value = true
  erroCarregamento.value = null
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
    agendar()
  } catch (e) {
    erroEnvio.value = mensagemDeErro(e)
  } finally {
    enviando.value = false
  }
}

async function acompanharSolucao() {
  if (!solucao.value) {
    return
  }
  try {
    solucao.value = await buscarSolucao(solucao.value.id)
    erroEnvio.value = null
  } catch (e) {
    erroEnvio.value = mensagemDeErro(e)
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
    <RouterLink :to="{ name: 'atividades' }"><i class="pi pi-arrow-left" /> Atividades</RouterLink>

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
