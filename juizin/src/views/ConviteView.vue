<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import { entrarPorConvite } from '@/api/grupos'
import { ApiError, mensagemDeErro } from '@/api/http'

const props = defineProps<{ inviteCode: string }>()

const router = useRouter()
const toast = useToast()

const erro = ref<string | null>(null)

async function entrar() {
  erro.value = null
  try {
    const grupo = await entrarPorConvite(props.inviteCode)
    toast.add({ severity: 'success', summary: 'Convite aceito', detail: `Você faz parte do grupo ${grupo.nome}.`, life: 5000 })
    // replace: voltar no navegador não reabre o convite
    await router.replace({ name: 'grupo', params: { id: grupo.id } })
  } catch (e) {
    erro.value =
      e instanceof ApiError && e.status === 404
        ? 'Convite inválido. Confira o link com quem o enviou.'
        : mensagemDeErro(e)
  }
}

watch(() => props.inviteCode, entrar, { immediate: true })
</script>

<template>
  <div class="pagina-convite">
    <Card>
      <template #title>Convite para grupo</template>
      <template #content>
        <div v-if="erro" class="stack">
          <Message severity="error">{{ erro }}</Message>
          <div>
            <Button as="router-link" :to="{ name: 'grupos' }" label="Ir para meus grupos" icon="pi pi-users" severity="secondary" />
          </div>
        </div>
        <span v-else class="texto-suave"><i class="pi pi-spin pi-spinner" /> Entrando no grupo…</span>
      </template>
    </Card>
  </div>
</template>

<style scoped>
.pagina-convite {
  max-width: 480px;
  margin: 2rem auto;
}
</style>
