<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'
import GruposTabela from '@/components/GruposTabela.vue'
import NovoGrupoDialog from '@/components/NovoGrupoDialog.vue'
import { listarMeusGrupos } from '@/api/grupos'
import { mensagemDeErro } from '@/api/http'
import type { GrupoResponse, PapelGrupo } from '@/api/types'

const router = useRouter()
const toast = useToast()

const grupos = ref<GrupoResponse[]>([])
const carregando = ref(true)
const aba = ref<PapelGrupo>('AVALIADOR')
const criandoGrupo = ref(false)

const comoAvaliador = computed(() => grupos.value.filter((g) => g.meuPapel === 'AVALIADOR'))
const comoEstudante = computed(() => grupos.value.filter((g) => g.meuPapel === 'ESTUDANTE'))

onMounted(async () => {
  try {
    grupos.value = await listarMeusGrupos()
    // Abre direto na aba que tem grupos
    if (comoAvaliador.value.length === 0 && comoEstudante.value.length > 0) {
      aba.value = 'ESTUDANTE'
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Erro ao carregar os grupos', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    carregando.value = false
  }
})

// Quem cria o grupo é o avaliador: vai direto para a tela do grupo, com o link de convite e as atividades
function aoCriarGrupo(grupo: GrupoResponse) {
  toast.add({ severity: 'success', summary: 'Grupo criado', detail: 'Você é o avaliador deste grupo.', life: 4000 })
  router.push({ name: 'grupo', params: { id: grupo.id } })
}
</script>

<template>
  <section class="stack">
    <div class="row">
      <div>
        <h1>Meus grupos</h1>
        <span class="texto-suave">
          Abra um grupo para ver as atividades, enviar respostas e acompanhar o placar. Nos grupos que você avalia,
          monte as atividades, acompanhe os envios de cada membro e exporte o resultado final.
        </span>
      </div>
      <span class="spacer" />
      <Button label="Novo grupo" icon="pi pi-plus" @click="criandoGrupo = true" />
    </div>

    <NovoGrupoDialog v-model:visible="criandoGrupo" @criado="aoCriarGrupo" />

    <Tabs v-model:value="aba">
      <TabList>
        <Tab value="AVALIADOR">
          <i class="pi pi-star" />
          Como avaliador ({{ comoAvaliador.length }})
        </Tab>
        <Tab value="ESTUDANTE">
          <i class="pi pi-book" />
          Como estudante ({{ comoEstudante.length }})
        </Tab>
      </TabList>
      <TabPanels>
        <TabPanel value="AVALIADOR">
          <GruposTabela
            :grupos="comoAvaliador"
            :carregando="carregando"
            vazio="Você ainda não avalia nenhum grupo. Use Novo grupo para criar um."
          />
        </TabPanel>
        <TabPanel value="ESTUDANTE">
          <GruposTabela
            :grupos="comoEstudante"
            :carregando="carregando"
            vazio="Você ainda não participa de nenhum grupo como estudante."
          />
        </TabPanel>
      </TabPanels>
    </Tabs>
  </section>
</template>
