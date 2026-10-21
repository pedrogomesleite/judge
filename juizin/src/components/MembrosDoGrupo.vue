<script setup lang="ts">
import { computed, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { removerMembroDoGrupo } from '@/api/grupos'
import { mensagemDeErro } from '@/api/http'
import type { MembroResponse, PapelGrupo } from '@/api/types'
import { formatarDataHora } from '@/utils/formatacao'
import { ROTULOS_PAPEL } from '@/utils/rotulos'

const props = defineProps<{
  grupoId: string
  membros: MembroResponse[]
  /** Avaliador com o grupo em AGUARDANDO_INICIO: pode remover estudantes. */
  podeRemover: boolean
}>()

const emit = defineEmits<{
  removido: []
}>()

const toast = useToast()
const confirm = useConfirm()

const removendo = ref<string | null>(null)

// Avaliadores primeiro, depois por nome
const ordenados = computed(() =>
  [...props.membros].sort(
    (a, b) => Number(b.papel === 'AVALIADOR') - Number(a.papel === 'AVALIADOR') || a.nome.localeCompare(b.nome, 'pt-BR'),
  ),
)
const totalEstudantes = computed(() => props.membros.filter((m) => m.papel === 'ESTUDANTE').length)

function confirmarRemocao(membro: MembroResponse) {
  confirm.require({
    header: 'Remover membro',
    message: `Remover ${membro.nome} do grupo? Com o link de convite, a pessoa pode entrar de novo enquanto o grupo não iniciar.`,
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Cancelar', severity: 'secondary', text: true },
    acceptProps: { label: 'Remover', severity: 'danger' },
    accept: () => remover(membro),
  })
}

async function remover(membro: MembroResponse) {
  removendo.value = membro.usuarioId
  try {
    await removerMembroDoGrupo(props.grupoId, membro.usuarioId)
    toast.add({ severity: 'success', summary: 'Membro removido', detail: membro.nome, life: 3000 })
    emit('removido')
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Não foi possível remover o membro', detail: mensagemDeErro(e), life: 5000 })
  } finally {
    removendo.value = null
  }
}
</script>

<template>
  <div class="stack">
    <div class="row">
      <strong>Participantes</strong>
      <span class="texto-suave">
        {{ totalEstudantes }} {{ totalEstudantes === 1 ? 'estudante' : 'estudantes' }} ·
        {{ membros.length - totalEstudantes }} {{ membros.length - totalEstudantes === 1 ? 'avaliador' : 'avaliadores' }}
      </span>
    </div>

    <DataTable :value="ordenados" data-key="usuarioId" size="small" paginator :rows="15" :always-show-paginator="false">
      <template #empty>Nenhum participante.</template>
      <Column field="nome" header="Nome" />
      <Column header="Papel" style="width: 9rem">
        <template #body="{ data }">
          <Tag :value="ROTULOS_PAPEL[data.papel as PapelGrupo].label" :severity="ROTULOS_PAPEL[data.papel as PapelGrupo].severidade" />
        </template>
      </Column>
      <Column header="Entrou em" style="width: 10rem">
        <template #body="{ data }">{{ formatarDataHora(data.entrouEm) }}</template>
      </Column>
      <Column v-if="podeRemover" style="width: 4rem">
        <template #body="{ data }">
          <Button
            v-if="data.papel === 'ESTUDANTE'"
            icon="pi pi-user-minus"
            severity="danger"
            text
            rounded
            :loading="removendo === data.usuarioId"
            :aria-label="`Remover ${data.nome}`"
            title="Remover do grupo"
            @click="confirmarRemocao(data)"
          />
        </template>
      </Column>
    </DataTable>
  </div>
</template>
