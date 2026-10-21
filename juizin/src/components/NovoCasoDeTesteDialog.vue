<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Textarea from 'primevue/textarea'
import Checkbox from 'primevue/checkbox'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { criarCasoDeTeste } from '@/api/casosDeTeste'
import { mensagemDeErro } from '@/api/http'
import type { CasoDeTesteResponse } from '@/api/types'

const props = defineProps<{
  atividadeId: string
  /** null: caso OFICIAL (autor da atividade). Com grupo: caso COMUNITARIO compartilhado nesse grupo. */
  grupoId: string | null
}>()

const visivel = defineModel<boolean>('visible', { required: true })

const emit = defineEmits<{
  criado: [caso: CasoDeTesteResponse]
}>()

const id = useId()
const entrada = ref('')
const saidaEsperada = ref('')
const publico = ref(false)
const salvando = ref(false)
const erro = ref<string | null>(null)

const comunitario = computed(() => props.grupoId !== null)
const podeSalvar = computed(() => (entrada.value.length > 0 || saidaEsperada.value.length > 0) && !salvando.value)

// Cada abertura começa com o formulário limpo
watch(visivel, (aberto) => {
  if (aberto) {
    entrada.value = ''
    saidaEsperada.value = ''
    publico.value = false
    erro.value = null
  }
})

async function salvar() {
  erro.value = null
  salvando.value = true
  try {
    const caso = await criarCasoDeTeste(props.atividadeId, {
      entrada: entrada.value,
      saidaEsperada: saidaEsperada.value,
      tipo: comunitario.value ? 'COMUNITARIO' : 'OFICIAL',
      grupoId: props.grupoId,
      publico: !comunitario.value && publico.value,
    })
    emit('criado', caso)
    visivel.value = false
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    salvando.value = false
  }
}
</script>

<template>
  <Dialog
    v-model:visible="visivel"
    modal
    :header="comunitario ? 'Compartilhar caso no grupo' : 'Novo caso oficial'"
    :style="{ width: '44rem' }"
    :breakpoints="{ '720px': '95vw' }"
  >
    <form class="stack" @submit.prevent="salvar">
      <span class="texto-suave">
        {{
          comunitario
            ? 'Fica visível só para os membros deste grupo e não conta no veredito.'
            : 'Conta no veredito de todas as respostas desta atividade.'
        }}
      </span>

      <div class="campo">
        <label :for="`${id}-entrada`">Entrada</label>
        <Textarea
          :id="`${id}-entrada`"
          v-model="entrada"
          class="mono"
          rows="5"
          spellcheck="false"
          placeholder="Exatamente o que o programa recebe na entrada padrão"
          fluid
        />
      </div>

      <div class="campo">
        <label :for="`${id}-saida`">Saída esperada</label>
        <Textarea :id="`${id}-saida`" v-model="saidaEsperada" class="mono" rows="5" spellcheck="false" fluid />
      </div>

      <div v-if="!comunitario" class="row">
        <Checkbox v-model="publico" :input-id="`${id}-publico`" binary />
        <label :for="`${id}-publico`">Público: aparece como exemplo para qualquer pessoa, inclusive sem login</label>
      </div>

      <Message v-if="erro" severity="error">{{ erro }}</Message>

      <div class="row">
        <span class="spacer" />
        <Button type="button" label="Cancelar" severity="secondary" text @click="visivel = false" />
        <Button
          type="submit"
          :label="comunitario ? 'Compartilhar' : 'Salvar'"
          icon="pi pi-check"
          :loading="salvando"
          :disabled="!podeSalvar"
        />
      </div>
    </form>
  </Dialog>
</template>
