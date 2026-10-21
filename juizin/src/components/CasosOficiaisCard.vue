<script setup lang="ts">
import { ref, watch } from 'vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import ToggleSwitch from 'primevue/toggleswitch'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import CasosDeTesteLista from '@/components/CasosDeTesteLista.vue'
import NovoCasoDeTesteDialog from '@/components/NovoCasoDeTesteDialog.vue'
import { alterarPublico, listarCasosOficiais } from '@/api/casosDeTeste'
import { mensagemDeErro } from '@/api/http'
import type { CasoDeTesteResponse } from '@/api/types'

// Gerenciamento dos casos oficiais: só aparece para o autor da atividade

const props = defineProps<{ atividadeId: string }>()

const emit = defineEmits<{
  /** Os casos públicos (exemplos) mudaram. */
  publicosAlterados: []
}>()

const toast = useToast()

const casos = ref<CasoDeTesteResponse[]>([])
const carregando = ref(true)
const erro = ref<string | null>(null)
/** Id do caso que está sendo salvo, para travar o switch durante a requisição. */
const alterando = ref<string | null>(null)
const criando = ref(false)

async function carregar() {
  carregando.value = true
  erro.value = null
  try {
    casos.value = await listarCasosOficiais(props.atividadeId)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
}

watch(() => props.atividadeId, carregar, { immediate: true })

function substituir(atualizado: CasoDeTesteResponse) {
  casos.value = casos.value.map((c) => (c.id === atualizado.id ? atualizado : c))
}

// Atualiza na hora e desfaz se a API recusar: o ToggleSwitch guarda o próprio estado
// e só volta ao valor certo quando o model-value muda
async function alternarPublico(caso: CasoDeTesteResponse, publico: boolean) {
  alterando.value = caso.id
  substituir({ ...caso, publico })
  try {
    substituir(await alterarPublico(props.atividadeId, caso.id, publico))
    emit('publicosAlterados')
  } catch (e) {
    substituir(caso)
    toast.add({ severity: 'error', summary: 'Não foi possível alterar o caso', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    alterando.value = null
  }
}

function aoCriar(caso: CasoDeTesteResponse) {
  casos.value = [...casos.value, caso]
  if (caso.publico) {
    emit('publicosAlterados')
  }
}
</script>

<template>
  <Card>
    <template #title>
      <div class="row">
        <span>Casos oficiais</span>
        <span class="spacer" />
        <Button label="Adicionar caso" icon="pi pi-plus" size="small" @click="criando = true" />
      </div>
    </template>
    <template #subtitle>
      Só você, autor da atividade, vê esta lista. Todos os casos oficiais contam no veredito; os públicos também
      aparecem como exemplos.
    </template>
    <template #content>
      <span v-if="carregando" class="texto-suave"><i class="pi pi-spin pi-spinner" /> Carregando casos…</span>
      <Message v-else-if="erro" severity="error">{{ erro }}</Message>
      <span v-else-if="!casos.length" class="texto-suave">Nenhum caso oficial ainda.</span>
      <CasosDeTesteLista v-else :casos="casos" rotulo="Caso">
        <template #acoes="{ caso }">
          <span class="spacer" />
          <ToggleSwitch
            :model-value="caso.publico"
            :input-id="`publico-${caso.id}`"
            :disabled="alterando === caso.id"
            @update:model-value="alternarPublico(caso, $event)"
          />
          <label :for="`publico-${caso.id}`">Público</label>
        </template>
      </CasosDeTesteLista>
    </template>
  </Card>

  <NovoCasoDeTesteDialog v-model:visible="criando" :atividade-id="atividadeId" :grupo-id="null" @criado="aoCriar" />
</template>
