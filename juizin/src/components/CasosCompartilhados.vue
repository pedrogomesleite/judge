<script setup lang="ts">
import { computed } from 'vue'
import Tag from 'primevue/tag'
import CasosDeTesteLista from '@/components/CasosDeTesteLista.vue'
import type { CasoDeTesteResponse } from '@/api/types'

interface CasosDoGrupo {
  grupoId: string
  grupoNome: string
  casos: CasoDeTesteResponse[]
}

const props = defineProps<{
  /** Casos comunitários, cada um com o grupo de origem. */
  casos: CasoDeTesteResponse[]
  /** Grupo de onde o usuário veio (?groupId): aparece primeiro e destacado. */
  grupoAtualId: string | null
}>()

const grupos = computed(() => {
  const porGrupo = new Map<string, CasosDoGrupo>()
  for (const caso of props.casos) {
    if (!caso.grupoId) {
      continue
    }
    const grupo = porGrupo.get(caso.grupoId) ?? { grupoId: caso.grupoId, grupoNome: caso.grupoNome ?? 'Grupo', casos: [] }
    grupo.casos.push(caso)
    porGrupo.set(caso.grupoId, grupo)
  }
  const prioridade = (grupo: CasosDoGrupo) => (grupo.grupoId === props.grupoAtualId ? 0 : 1)
  return [...porGrupo.values()].sort(
    (a, b) => prioridade(a) - prioridade(b) || a.grupoNome.localeCompare(b.grupoNome, 'pt-BR'),
  )
})

function contagem(grupo: CasosDoGrupo) {
  return grupo.casos.length === 1 ? '1 caso' : `${grupo.casos.length} casos`
}
</script>

<template>
  <div class="grupos">
    <section v-for="grupo in grupos" :key="grupo.grupoId" class="stack grupo">
      <div class="row">
        <h3><i class="pi pi-users" /> {{ grupo.grupoNome }}</h3>
        <Tag v-if="grupo.grupoId === grupoAtualId" severity="info" value="Grupo atual" />
        <span class="texto-suave">{{ contagem(grupo) }}</span>
      </div>
      <CasosDeTesteLista :casos="grupo.casos" rotulo="Caso" />
    </section>
  </div>
</template>

<style scoped>
.grupos {
  display: flex;
  flex-direction: column;
}

.grupo + .grupo {
  margin-top: 1.25rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--p-content-border-color);
}

h3 {
  margin: 0;
  font-size: 1.05rem;
}
</style>
