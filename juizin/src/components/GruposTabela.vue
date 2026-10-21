<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import type { GrupoResponse } from '@/api/types'
import { formatarPeriodo } from '@/utils/formatacao'
import { ROTULOS_STATUS_GRUPO } from '@/utils/rotulos'

defineProps<{
  grupos: GrupoResponse[]
  carregando: boolean
  vazio: string
}>()

function rotuloDoStatus(grupo: GrupoResponse) {
  return ROTULOS_STATUS_GRUPO[grupo.status]
}
</script>

<template>
  <DataTable :value="grupos" :loading="carregando" data-key="id">
    <template #empty>{{ vazio }}</template>
    <Column field="nome" header="Grupo">
      <template #body="{ data }">
        <RouterLink :to="{ name: 'grupo', params: { id: data.id } }" class="nome">{{ data.nome }}</RouterLink>
        <div v-if="data.descricao">
          <small class="texto-suave">{{ data.descricao }}</small>
        </div>
      </template>
    </Column>
    <Column header="Status" style="width: 12rem">
      <template #body="{ data }">
        <Tag :value="rotuloDoStatus(data).label" :severity="rotuloDoStatus(data).severidade" />
      </template>
    </Column>
    <Column header="Período">
      <template #body="{ data }">{{ formatarPeriodo(data.dataInicio, data.dataFim) }}</template>
    </Column>
    <Column style="width: 8rem">
      <template #body="{ data }">
        <Button
          as="router-link"
          :to="{ name: 'grupo', params: { id: data.id } }"
          label="Abrir"
          icon="pi pi-arrow-right"
          icon-pos="right"
          size="small"
          severity="secondary"
          text
        />
      </template>
    </Column>
  </DataTable>
</template>

<style scoped>
.nome {
  font-weight: 600;
}
</style>
