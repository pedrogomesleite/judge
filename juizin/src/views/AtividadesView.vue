<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import { useToast } from 'primevue/usetoast'
import { listarAtividades } from '@/api/atividades'
import { mensagemDeErro } from '@/api/http'
import type { AtividadeResponse } from '@/api/types'
import { formatarDataHora } from '@/utils/formatacao'

const toast = useToast()

const atividades = ref<AtividadeResponse[]>([])
const carregando = ref(true)
const busca = ref('')

const filtradas = computed(() => {
  const termo = busca.value.trim().toLowerCase()
  return termo ? atividades.value.filter((a) => a.titulo.toLowerCase().includes(termo)) : atividades.value
})

onMounted(async () => {
  try {
    atividades.value = await listarAtividades()
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Erro ao carregar as atividades', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    carregando.value = false
  }
})
</script>

<template>
  <section class="stack">
    <div class="row">
      <div>
        <h1>Atividades</h1>
        <span class="texto-suave">Resolva qualquer atividade, com ou sem grupo.</span>
      </div>
      <span class="spacer" />
      <IconField>
        <InputIcon class="pi pi-search" />
        <InputText v-model="busca" placeholder="Buscar por título" />
      </IconField>
    </div>

    <DataTable
      :value="filtradas"
      :loading="carregando"
      data-key="id"
      sort-field="criadoEm"
      :sort-order="-1"
      paginator
      :rows="10"
      striped-rows
    >
      <template #empty>Nenhuma atividade encontrada.</template>
      <Column field="titulo" header="Título" sortable>
        <template #body="{ data }">
          <RouterLink :to="{ name: 'atividade', params: { id: data.id } }">{{ data.titulo }}</RouterLink>
        </template>
      </Column>
      <Column field="limiteTempoMs" header="Tempo limite" sortable>
        <template #body="{ data }">{{ data.limiteTempoMs }} ms</template>
      </Column>
      <Column field="limiteMemoriaMb" header="Memória limite" sortable>
        <template #body="{ data }">{{ data.limiteMemoriaMb }} MB</template>
      </Column>
      <Column field="criadoEm" header="Criada em" sortable>
        <template #body="{ data }">{{ formatarDataHora(data.criadoEm) }}</template>
      </Column>
    </DataTable>
  </section>
</template>
