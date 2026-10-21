<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Card from 'primevue/card'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import AtividadesDoGrupoEditor from '@/components/AtividadesDoGrupoEditor.vue'
import ConviteLink from '@/components/ConviteLink.vue'
import EnviosDoGrupo from '@/components/EnviosDoGrupo.vue'
import MembrosDoGrupo from '@/components/MembrosDoGrupo.vue'
import ProgressoDoGrupo from '@/components/ProgressoDoGrupo.vue'
import RelatorioDoGrupo from '@/components/RelatorioDoGrupo.vue'
import { useEventosDoGrupo } from '@/composables/useEventosDoGrupo'
import { buscarGrupo, finalizarGrupo, iniciarGrupo, listarAtividadesDoGrupo, listarMembrosDoGrupo } from '@/api/grupos'
import { ApiError, mensagemDeErro } from '@/api/http'
import type { GrupoAtividadeItemResponse, GrupoResponse, MembroResponse, TipoAlteracaoGrupo } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { formatarPeriodo } from '@/utils/formatacao'
import { ROTULOS_PAPEL, ROTULOS_STATUS_GRUPO } from '@/utils/rotulos'

// Tela do grupo: o conteúdo depende do papel do usuário no grupo e do status do grupo.
// Fica atualizada pelo SSE do grupo: cada evento diz o que buscar de novo

const props = defineProps<{ id: string }>()

const auth = useAuthStore()
const toast = useToast()
const confirm = useConfirm()

const grupo = ref<GrupoResponse | null>(null)
const atividades = ref<GrupoAtividadeItemResponse[]>([])
const membros = ref<MembroResponse[]>([])
const carregando = ref(true)
const erro = ref<string | null>(null)
/** Sem acesso ao grupo (não é membro, foi removido ou o grupo não existe). */
const negado = ref<string | null>(null)
const alterandoStatus = ref(false)

/** Incrementados a cada alteração: os componentes filhos observam e buscam de novo. */
const revisaoEnvios = ref(0)
const revisaoPlacar = ref(0)

const avaliador = computed(() => grupo.value?.meuPapel === 'AVALIADOR')
const status = computed(() => grupo.value?.status)
const pontuacaoMaxima = computed(() => atividades.value.reduce((total, a) => total + a.peso, 0))

function tratarErro(e: unknown) {
  if (e instanceof ApiError && (e.status === 403 || e.status === 404)) {
    negado.value =
      e.status === 403 ? 'Você não faz parte deste grupo.' : 'Grupo não encontrado. Confira o link com quem o enviou.'
    encerrar()
  } else {
    erro.value = mensagemDeErro(e)
  }
}

async function carregarGrupo() {
  grupo.value = await buscarGrupo(props.id)
}

async function carregarAtividades() {
  const itens = await listarAtividadesDoGrupo(props.id)
  atividades.value = [...itens].sort((a, b) => a.ordem - b.ordem)
}

async function carregarMembros() {
  membros.value = await listarMembrosDoGrupo(props.id)
}

/** Recarrega as partes indicadas; os erros viram mensagem na tela. */
async function recarregar(partes: (() => Promise<void>)[]) {
  try {
    await Promise.all(partes.map((carregar) => carregar()))
    erro.value = null
  } catch (e) {
    tratarErro(e)
  }
}

async function carregarTudo() {
  await recarregar([carregarGrupo, carregarAtividades, carregarMembros])
}

watch(
  () => props.id,
  async () => {
    carregando.value = true
    negado.value = null
    erro.value = null
    grupo.value = null
    await carregarTudo()
    carregando.value = false
  },
  { immediate: true },
)

function aoAlterar(tipos: Set<TipoAlteracaoGrupo>) {
  const partes: (() => Promise<void>)[] = []
  if (tipos.has('GRUPO')) {
    partes.push(carregarGrupo)
  }
  if (tipos.has('ATIVIDADES')) {
    partes.push(carregarAtividades)
  }
  if (tipos.has('MEMBROS')) {
    // O próprio papel pode ter mudado (promoção)
    partes.push(carregarMembros, carregarGrupo)
  }
  if (partes.length > 0) {
    recarregar([...new Set(partes)])
  }
  if (tipos.has('SOLUCOES')) {
    revisaoEnvios.value++
  }
  if (tipos.has('SOLUCOES') || tipos.has('ATIVIDADES') || tipos.has('MEMBROS')) {
    revisaoPlacar.value++
  }
}

const { conectado, encerrar } = useEventosDoGrupo(() => props.id, {
  aoAlterar,
  aoReconectar: () => {
    carregarTudo()
    revisaoEnvios.value++
    revisaoPlacar.value++
  },
  aoNegar: tratarErro,
})

function confirmarTransicao(acao: 'iniciar' | 'finalizar') {
  const iniciar = acao === 'iniciar'
  confirm.require({
    header: iniciar ? 'Iniciar grupo' : 'Finalizar grupo',
    message: iniciar
      ? 'Os envios serão liberados e não será mais possível alterar atividades, pesos e participantes. Iniciar agora?'
      : 'Os envios serão encerrados e o resultado final ficará disponível. Finalizar agora?',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Cancelar', severity: 'secondary', text: true },
    acceptProps: { label: iniciar ? 'Iniciar' : 'Finalizar', severity: iniciar ? undefined : 'danger' },
    accept: () => alterarStatus(acao),
  })
}

async function alterarStatus(acao: 'iniciar' | 'finalizar') {
  alterandoStatus.value = true
  try {
    grupo.value = acao === 'iniciar' ? await iniciarGrupo(props.id) : await finalizarGrupo(props.id)
    toast.add({
      severity: 'success',
      summary: acao === 'iniciar' ? 'Grupo iniciado' : 'Grupo finalizado',
      life: 3000,
    })
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Não foi possível alterar o grupo', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    alterandoStatus.value = false
  }
}
</script>

<template>
  <section class="stack">
    <RouterLink :to="{ name: 'grupos' }"><i class="pi pi-arrow-left" /> Meus grupos</RouterLink>

    <span v-if="carregando" class="texto-suave"><i class="pi pi-spin pi-spinner" /> Carregando grupo…</span>

    <div v-else-if="negado" class="stack">
      <Message severity="warn">{{ negado }}</Message>
      <div>
        <Button as="router-link" :to="{ name: 'grupos' }" label="Ir para meus grupos" icon="pi pi-users" severity="secondary" />
      </div>
    </div>

    <Message v-else-if="!grupo" severity="error">{{ erro ?? 'Não foi possível carregar o grupo.' }}</Message>

    <template v-else>
      <!-- Cabeçalho -->
      <div class="stack cabecalho">
        <div class="row">
          <h1>{{ grupo.nome }}</h1>
          <Tag :value="ROTULOS_STATUS_GRUPO[grupo.status].label" :severity="ROTULOS_STATUS_GRUPO[grupo.status].severidade" />
          <Tag :value="`Você é ${ROTULOS_PAPEL[grupo.meuPapel].label.toLowerCase()}`" :severity="ROTULOS_PAPEL[grupo.meuPapel].severidade" />
          <span class="spacer" />
          <span
            class="ao-vivo"
            :class="{ conectado }"
            :title="conectado ? 'As listas se atualizam automaticamente' : 'Tentando reconectar para atualizar automaticamente'"
          >
            <i class="pi pi-circle-fill" /> {{ conectado ? 'Ao vivo' : 'Reconectando…' }}
          </span>
          <Button
            v-if="avaliador && status === 'AGUARDANDO_INICIO'"
            label="Iniciar agora"
            icon="pi pi-play"
            :loading="alterandoStatus"
            @click="confirmarTransicao('iniciar')"
          />
          <Button
            v-if="avaliador && status === 'EM_ANDAMENTO'"
            label="Finalizar agora"
            icon="pi pi-stop"
            severity="danger"
            outlined
            :loading="alterandoStatus"
            @click="confirmarTransicao('finalizar')"
          />
        </div>
        <span class="texto-suave"><i class="pi pi-calendar" /> {{ formatarPeriodo(grupo.dataInicio, grupo.dataFim) }}</span>
        <span v-if="grupo.descricao" class="descricao">{{ grupo.descricao }}</span>
      </div>

      <Message v-if="erro" severity="error" size="small">{{ erro }}</Message>

      <!-- Aguardando início -->
      <template v-if="status === 'AGUARDANDO_INICIO'">
        <Message severity="warn" size="small">
          {{
            avaliador
              ? 'Monte a lista de atividades e convide os participantes. Depois do início, atividades, pesos e participantes não podem mais ser alterados.'
              : 'Os envios serão liberados quando o grupo iniciar.'
          }}
        </Message>

        <div class="colunas">
          <div class="stack">
            <Card>
              <template #content>
                <AtividadesDoGrupoEditor
                  v-if="avaliador"
                  :grupo-id="grupo.id"
                  :atividades="atividades"
                  @alterado="recarregar([carregarAtividades])"
                />
                <div v-else class="stack">
                  <div class="row">
                    <strong>Atividades</strong>
                    <span class="texto-suave">{{ atividades.length }} no grupo · pontuação máxima {{ pontuacaoMaxima }}</span>
                  </div>
                  <DataTable :value="atividades" data-key="atividadeId" size="small">
                    <template #empty>Nenhuma atividade no grupo ainda.</template>
                    <Column header="#" style="width: 3rem">
                      <template #body="{ index }">{{ index + 1 }}</template>
                    </Column>
                    <Column header="Atividade">
                      <template #body="{ data }">
                        <RouterLink :to="{ name: 'atividade', params: { id: data.atividadeId } }">{{ data.titulo }}</RouterLink>
                      </template>
                    </Column>
                    <Column field="peso" header="Peso" style="width: 6rem" />
                  </DataTable>
                </div>
              </template>
            </Card>
            <Card>
              <template #content>
                <ConviteLink :codigo-convite="grupo.codigoConvite" />
              </template>
            </Card>
          </div>
          <Card>
            <template #content>
              <MembrosDoGrupo
                :grupo-id="grupo.id"
                :membros="membros"
                :pode-remover="avaliador"
                @removido="recarregar([carregarMembros])"
              />
            </template>
          </Card>
        </div>
      </template>

      <!-- Em andamento -->
      <div v-else-if="status === 'EM_ANDAMENTO'" class="colunas">
        <Card>
          <template #content>
            <EnviosDoGrupo
              :grupo-id="grupo.id"
              :atividades="atividades"
              :membros="membros"
              :papel="grupo.meuPapel"
              :revisao="revisaoEnvios"
            />
          </template>
        </Card>
        <Card>
          <template #content>
            <ProgressoDoGrupo :grupo-id="grupo.id" :revisao="revisaoPlacar" :destacar-usuario-id="auth.usuario?.id" />
          </template>
        </Card>
      </div>

      <!-- Finalizado -->
      <template v-else>
        <Message severity="secondary" size="small">Grupo finalizado: os envios estão encerrados.</Message>
        <Card>
          <template #content>
            <RelatorioDoGrupo
              :grupo-id="grupo.id"
              :grupo-nome="grupo.nome"
              :atividades="atividades"
              :papel="grupo.meuPapel"
              :revisao="revisaoPlacar"
            />
          </template>
        </Card>
      </template>
    </template>
  </section>
</template>

<style scoped>
.cabecalho {
  gap: 0.375rem;
}

.descricao {
  white-space: pre-wrap;
}

.ao-vivo {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.875rem;
  color: var(--p-text-muted-color);
}

.ao-vivo .pi {
  font-size: 0.5rem;
}

.ao-vivo.conectado .pi {
  color: var(--p-green-500);
}

/* Conteúdo principal e painel lateral; uma coluna em telas estreitas */
.colunas {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}

@media (max-width: 900px) {
  .colunas {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
