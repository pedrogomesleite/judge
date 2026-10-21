<script setup lang="ts">
import { computed, useId } from 'vue'
import { useRouter } from 'vue-router'
import InputGroup from 'primevue/inputgroup'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'

const props = defineProps<{ codigoConvite: string }>()

const router = useRouter()
const toast = useToast()
const inputId = useId()

// URL absoluta da rota de convite, para colar em qualquer lugar
const link = computed(
  () => new URL(router.resolve({ name: 'convite', params: { inviteCode: props.codigoConvite } }).href, window.location.origin).href,
)

function selecionar(evento: FocusEvent) {
  ;(evento.target as HTMLInputElement).select()
}

async function copiar() {
  try {
    await navigator.clipboard.writeText(link.value)
    toast.add({ severity: 'success', summary: 'Link de convite copiado', life: 3000 })
  } catch {
    // Clipboard indisponível (ex.: HTTP fora do localhost) ou negado pelo navegador
    toast.add({ severity: 'warn', summary: 'Não foi possível copiar', detail: 'Clique no link e copie com Ctrl+C.', life: 5000 })
  }
}
</script>

<template>
  <div class="campo convite">
    <label :for="inputId">Link de convite</label>
    <InputGroup>
      <InputText :id="inputId" :model-value="link" class="mono" readonly @focus="selecionar" />
      <Button icon="pi pi-copy" label="Copiar" severity="secondary" @click="copiar" />
    </InputGroup>
    <small class="texto-suave">Quem acessar o link entra no grupo como estudante.</small>
  </div>
</template>

<style scoped>
.convite {
  max-width: 40rem;
}
</style>
