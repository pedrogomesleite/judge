package com.packges.runner.avaliacao;

import com.packges.runner.mensageria.Veredito;
import com.packges.runner.sandbox.MetaIsolate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificadorExecucaoTest {

    private static MetaIsolate meta(String... linhas) {
        return MetaIsolate.ler(List.of(linhas));
    }

    @Test
    void compilacaoBemSucedidaNaoTemVeredito() {
        assertThat(ClassificadorExecucao.compilacao(meta("time:1.200", "exitcode:0"))).isEmpty();
    }

    @Test
    void compilacaoComErroOuTempoExcedidoEErroDeCompilacao() {
        assertThat(ClassificadorExecucao.compilacao(meta("status:RE", "exitcode:1"))).contains(Veredito.ERRO_COMPILACAO);
        assertThat(ClassificadorExecucao.compilacao(meta("status:TO"))).contains(Veredito.ERRO_COMPILACAO);
    }

    @Test
    void erroDoSandboxNaCompilacaoEErroInterno() {
        assertThat(ClassificadorExecucao.compilacao(meta("status:XX"))).contains(Veredito.ERRO_INTERNO);
    }

    @Test
    void execucaoBemSucedidaVaiParaAComparacaoDaSaida() {
        assertThat(ClassificadorExecucao.execucao(meta("time:0.010", "exitcode:0"))).isEmpty();
    }

    @Test
    void tempoExcedido() {
        assertThat(ClassificadorExecucao.execucao(meta("status:TO", "killed:1"))).contains(Veredito.TEMPO_EXCEDIDO);
    }

    @Test
    void mortePeloOomKillerEMemoriaExcedidaMesmoComStatusDeSinal() {
        assertThat(ClassificadorExecucao.execucao(meta("status:SG", "exitsig:9", "cg-oom-killed:1")))
                .contains(Veredito.MEMORIA_EXCEDIDA);
    }

    @Test
    void codigoDeSaidaDiferenteDeZeroOuSinalEErroDeExecucao() {
        assertThat(ClassificadorExecucao.execucao(meta("status:RE", "exitcode:3"))).contains(Veredito.ERRO_EXECUCAO);
        assertThat(ClassificadorExecucao.execucao(meta("status:SG", "exitsig:11"))).contains(Veredito.ERRO_EXECUCAO);
    }

    @Test
    void erroDoSandboxNaExecucaoEErroInterno() {
        assertThat(ClassificadorExecucao.execucao(meta("status:XX"))).contains(Veredito.ERRO_INTERNO);
    }
}
