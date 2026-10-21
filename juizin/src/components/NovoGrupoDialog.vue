<script setup lang="ts">
import { computed, reactive, ref, useId, watch } from 'vue'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { criarGrupo } from '@/api/grupos'
import { ApiError, mensagemDeErro } from '@/api/http'
import type { GrupoResponse } from '@/api/types'

// Mesmos limites do GrupoRequest
const NOME_MAX = 120
const DESCRICAO_MAX = 2000

const visivel = defineModel<boolean>('visible', { required: true })

const emit = defineEmits<{
  criado: [grupo: GrupoResponse]
}>()

const id = useId()
/** Datas no formato do input datetime-local (AAAA-MM-DDTHH:mm, horário local); vazio = sem data. */
const form = reactive({ nome: '', descricao: '', dataInicio: '', dataFim: '' })
const salvando = ref(false)
const erro = ref<string | null>(null)
const errosCampos = ref<Record<string, string>>({})

const podeSalvar = computed(() => form.nome.trim().length > 0 && !salvando.value)

// Cada abertura começa com o formulário limpo
watch(visivel, (aberto) => {
  if (aberto) {
    Object.assign(form, { nome: '', descricao: '', dataInicio: '', dataFim: '' })
    erro.value = null
    errosCampos.value = {}
  }
})

/** O datetime-local não tem fuso: new Date() o lê no horário local, e a API recebe o Instant em UTC. */
function paraInstant(valor: string): string | null {
  return valor ? new Date(valor).toISOString() : null
}

async function salvar() {
  erro.value = null
  errosCampos.value = {}
  const dataInicio = paraInstant(form.dataInicio)
  const dataFim = paraInstant(form.dataFim)
  // Mesma regra do GrupoService.validarDatas
  if (dataInicio && dataFim && Date.parse(dataFim) <= Date.parse(dataInicio)) {
    errosCampos.value = { dataFim: 'O fim precisa ser depois do início' }
    return
  }

  salvando.value = true
  try {
    const grupo = await criarGrupo({
      nome: form.nome.trim(),
      descricao: form.descricao.trim() || null,
      dataInicio,
      dataFim,
    })
    emit('criado', grupo)
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
    header="Novo grupo"
    :style="{ width: '36rem' }"
    :breakpoints="{ '640px': '95vw' }"
  >
    <form class="stack" @submit.prevent="salvar">
      <span class="texto-suave">
        Você entra no grupo como avaliador e acompanha os envios dos membros. Convide-os pelo link de convite do grupo.
      </span>

      <div class="campo">
        <label :for="`${id}-nome`">Nome</label>
        <InputText :id="`${id}-nome`" v-model="form.nome" :maxlength="NOME_MAX" required autofocus fluid />
        <small v-if="errosCampos.nome" class="erro-campo">{{ errosCampos.nome }}</small>
      </div>

      <div class="campo">
        <label :for="`${id}-descricao`">Descrição <span class="opcional">(opcional)</span></label>
        <Textarea :id="`${id}-descricao`" v-model="form.descricao" rows="3" :maxlength="DESCRICAO_MAX" auto-resize fluid />
        <small v-if="errosCampos.descricao" class="erro-campo">{{ errosCampos.descricao }}</small>
      </div>

      <div class="datas">
        <div class="campo">
          <label :for="`${id}-inicio`">Início <span class="opcional">(opcional)</span></label>
          <InputText :id="`${id}-inicio`" v-model="form.dataInicio" type="datetime-local" fluid />
        </div>
        <div class="campo">
          <label :for="`${id}-fim`">Fim <span class="opcional">(opcional)</span></label>
          <InputText
            :id="`${id}-fim`"
            v-model="form.dataFim"
            type="datetime-local"
            :min="form.dataInicio || undefined"
            fluid
          />
          <small v-if="errosCampos.dataFim" class="erro-campo">{{ errosCampos.dataFim }}</small>
        </div>
      </div>
      <small class="texto-suave">Com as datas preenchidas, o grupo inicia e finaliza automaticamente.</small>

      <Message v-if="erro" severity="error">{{ erro }}</Message>

      <div class="row">
        <span class="spacer" />
        <Button type="button" label="Cancelar" severity="secondary" text @click="visivel = false" />
        <Button type="submit" label="Criar grupo" icon="pi pi-check" :loading="salvando" :disabled="!podeSalvar" />
      </div>
    </form>
  </Dialog>
</template>

<style scoped>
.datas {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0.75rem;
}

.opcional {
  font-weight: 400;
  color: var(--p-text-muted-color);
}
</style>
