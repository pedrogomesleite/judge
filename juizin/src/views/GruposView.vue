<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import { useToast } from 'primevue/usetoast'
import GruposTabela from '@/components/GruposTabela.vue'
import { listarMeusGrupos } from '@/api/grupos'
import { mensagemDeErro } from '@/api/http'
import type { GrupoResponse, PapelGrupo } from '@/api/types'

const route = useRoute()
const toast = useToast()

const grupoIdDestacado = computed(() => (typeof route.query.groupId === 'string' ? route.query.groupId : null))

const grupos = ref<GrupoResponse[]>([])
const carregando = ref(true)
const aba = ref<PapelGrupo>('AVALIADOR')

const comoAvaliador = computed(() => grupos.value.filter((g) => g.meuPapel === 'AVALIADOR'))
const comoEstudante = computed(() => grupos.value.filter((g) => g.meuPapel === 'ESTUDANTE'))

onMounted(async () => {
  try {
    grupos.value = await listarMeusGrupos()
    const destacado = grupos.value.find((g) => g.id === grupoIdDestacado.value)
    // Abre na aba do grupo destacado ou, sem ele, direto na aba que tem grupos
    if (destacado) {
      aba.value = destacado.meuPapel
    } else if (comoAvaliador.value.length === 0 && comoEstudante.value.length > 0) {
      aba.value = 'ESTUDANTE'
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Erro ao carregar os grupos', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    carregando.value = false
  }
})
</script>

<template>
  <section class="stack">
    <div>
      <h1>Meus grupos</h1>
      <span class="texto-suave">
        Expanda um grupo para ver as atividades, enviar respostas nele e copiar o link de convite. Nos grupos que você
        avalia, acompanhe também os envios de cada membro.
      </span>
    </div>

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
            :expandir-grupo-id="grupoIdDestacado"
            vazio="Você ainda não avalia nenhum grupo."
          />
        </TabPanel>
        <TabPanel value="ESTUDANTE">
          <GruposTabela
            :grupos="comoEstudante"
            :carregando="carregando"
            :expandir-grupo-id="grupoIdDestacado"
            vazio="Você ainda não participa de nenhum grupo como estudante."
          />
        </TabPanel>
      </TabPanels>
    </Tabs>
  </section>
</template>
