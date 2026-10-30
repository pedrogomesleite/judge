<script setup lang="ts">
import { computed, reactive, ref, useId, watch } from 'vue'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { criarAtividade } from '@/api/atividades'
import { ApiError, mensagemDeErro } from '@/api/http'
import type { AtividadeResponse } from '@/api/types'

// Mesmos limites do AtividadeRequest
const TITULO_MAX = 200
const TEMPO_MIN_MS = 100
const TEMPO_MAX_MS = 30_000
const MEMORIA_MIN_MB = 16
const MEMORIA_MAX_MB = 1024

const TEMPO_PADRAO_MS = 1000
const MEMORIA_PADRAO_MB = 256

const visivel = defineModel<boolean>('visible', { required: true })

const emit = defineEmits<{
  criada: [atividade: AtividadeResponse]
}>()

const id = useId()
const form = reactive({
  titulo: '',
  enunciado: '',
  limiteTempoMs: TEMPO_PADRAO_MS as number | null,
  limiteMemoriaMb: MEMORIA_PADRAO_MB as number | null,
})
const salvando = ref(false)
const erro = ref<string | null>(null)
const errosCampos = ref<Record<string, string>>({})

const podeSalvar = computed(
  () =>
    form.titulo.trim().length > 0 &&
    form.enunciado.trim().length > 0 &&
    form.limiteTempoMs != null &&
    form.limiteMemoriaMb != null &&
    !salvando.value,
)

// Cada abertura começa com o formulário limpo
watch(visivel, (aberto) => {
  if (aberto) {
    Object.assign(form, {
      titulo: '',
      enunciado: '',
      limiteTempoMs: TEMPO_PADRAO_MS,
      limiteMemoriaMb: MEMORIA_PADRAO_MB,
    })
    erro.value = null
    errosCampos.value = {}
  }
})

async function salvar() {
  if (!podeSalvar.value) {
    return
  }
  erro.value = null
  errosCampos.value = {}
  salvando.value = true
  try {
    const atividade = await criarAtividade({
      titulo: form.titulo.trim(),
      enunciado: form.enunciado.trim(),
      limiteTempoMs: form.limiteTempoMs!,
      limiteMemoriaMb: form.limiteMemoriaMb!,
    })
    emit('criada', atividade)
    visivel.value = false
  } catch (e) {
    erro.value = mensagemDeErro(e)
    if (e instanceof ApiError) {
      errosCampos.value = e.campos
    }
  } finally {
    salvando.value = false
  }
}
</script>

<template>
  <Dialog
    v-model:visible="visivel"
    modal
    header="Nova atividade"
    :style="{ width: '44rem' }"
    :breakpoints="{ '720px': '95vw' }"
  >
    <form class="stack" @submit.prevent="salvar">
      <span class="texto-suave">
        Você será o autor da atividade. Depois de criá-la, cadastre os casos de teste oficiais na tela da atividade:
        são eles que decidem o veredito.
      </span>

      <div class="campo">
        <label :for="`${id}-titulo`">Título</label>
        <InputText :id="`${id}-titulo`" v-model="form.titulo" :maxlength="TITULO_MAX" required autofocus fluid />
        <small v-if="errosCampos.titulo" class="erro-campo">{{ errosCampos.titulo }}</small>
      </div>

      <div class="campo">
        <label :for="`${id}-enunciado`">Enunciado</label>
        <Textarea
          :id="`${id}-enunciado`"
          v-model="form.enunciado"
          rows="8"
          placeholder="Descreva o problema, o formato da entrada e o formato da saída"
          auto-resize
          fluid
        />
        <small v-if="errosCampos.enunciado" class="erro-campo">{{ errosCampos.enunciado }}</small>
      </div>

      <div class="limites">
        <div class="campo">
          <label :for="`${id}-tempo`">Tempo limite (ms)</label>
          <!-- @input: o v-model do InputNumber só atualiza ao perder o foco -->
          <InputNumber
            v-model="form.limiteTempoMs"
            :input-id="`${id}-tempo`"
            :min="TEMPO_MIN_MS"
            :max="TEMPO_MAX_MS"
            :step="100"
            :use-grouping="false"
            show-buttons
            fluid
            @input="form.limiteTempoMs = $event.value as number | null"
          />
          <small v-if="errosCampos.limiteTempoMs" class="erro-campo">{{ errosCampos.limiteTempoMs }}</small>
        </div>
        <div class="campo">
          <label :for="`${id}-memoria`">Memória limite (MB)</label>
          <InputNumber
            v-model="form.limiteMemoriaMb"
            :input-id="`${id}-memoria`"
            :min="MEMORIA_MIN_MB"
            :max="MEMORIA_MAX_MB"
            :step="16"
            :use-grouping="false"
            show-buttons
            fluid
            @input="form.limiteMemoriaMb = $event.value as number | null"
          />
          <small v-if="errosCampos.limiteMemoriaMb" class="erro-campo">{{ errosCampos.limiteMemoriaMb }}</small>
        </div>
      </div>
      <small class="texto-suave">
        Tempo de {{ TEMPO_MIN_MS }} a {{ TEMPO_MAX_MS }} ms; memória de {{ MEMORIA_MIN_MB }} a {{ MEMORIA_MAX_MB }} MB.
      </small>

      <Message v-if="erro" severity="error">{{ erro }}</Message>

      <div class="row">
        <span class="spacer" />
        <Button type="button" label="Cancelar" severity="secondary" text @click="visivel = false" />
        <Button type="submit" label="Criar atividade" icon="pi pi-check" :loading="salvando" :disabled="!podeSalvar" />
      </div>
    </form>
  </Dialog>
</template>

<style scoped>
.limites {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0.75rem;
}
</style>
