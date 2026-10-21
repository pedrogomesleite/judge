<script setup lang="ts">
import type { CasoDeTesteResponse } from '@/api/types'

defineProps<{
  casos: CasoDeTesteResponse[]
  /** Prefixo de cada item: "Exemplo 1", "Caso 1"... */
  rotulo: string
}>()

defineSlots<{
  /** Ações ao lado do título de cada caso (ex.: marcar como público). */
  acoes?(props: { caso: CasoDeTesteResponse }): unknown
}>()
</script>

<template>
  <ol class="casos">
    <li v-for="(caso, indice) in casos" :key="caso.id" class="caso">
      <div class="row">
        <strong>{{ rotulo }} {{ indice + 1 }}</strong>
        <slot name="acoes" :caso="caso" />
      </div>
      <div class="colunas">
        <div class="campo">
          <span class="texto-suave">Entrada</span>
          <pre v-if="caso.entrada" class="mono">{{ caso.entrada }}</pre>
          <pre v-else class="mono vazio">(vazia)</pre>
        </div>
        <div class="campo">
          <span class="texto-suave">Saída esperada</span>
          <pre v-if="caso.saidaEsperada" class="mono">{{ caso.saidaEsperada }}</pre>
          <pre v-else class="mono vazio">(vazia)</pre>
        </div>
      </div>
    </li>
  </ol>
</template>

<style scoped>
.casos {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.caso {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.colunas {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 0.75rem;
}

/* Sem quebra de linha automática: a entrada precisa aparecer exatamente como o programa a recebe */
pre {
  margin: 0;
  padding: 0.625rem 0.75rem;
  max-height: 16rem;
  overflow: auto;
  white-space: pre;
  background: var(--p-surface-100);
  border: 1px solid var(--p-content-border-color);
  border-radius: var(--p-content-border-radius);
}

.vazio {
  font-style: italic;
  color: var(--p-text-muted-color);
}
</style>
