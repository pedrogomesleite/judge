package com.packges.runner.sandbox;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetaIsolateTest {

    @Test
    void leExecucaoBemSucedida() {
        MetaIsolate meta = MetaIsolate.ler(List.of(
                "time:0.012", "time-wall:0.051", "max-rss:3200", "cg-mem:2048", "csw-voluntary:2", "exitcode:0"));

        assertThat(meta.sucesso()).isTrue();
        assertThat(meta.tempoMs()).isEqualTo(12);
        assertThat(meta.memoriaKb()).isEqualTo(2048);
        assertThat(meta.oomKilled()).isFalse();
    }

    @Test
    void semCgMemUsaMaxRss() {
        MetaIsolate meta = MetaIsolate.ler(List.of("time:0.100", "max-rss:3200"));

        assertThat(meta.memoriaKb()).isEqualTo(3200);
    }

    @Test
    void leTempoExcedido() {
        MetaIsolate meta = MetaIsolate.ler(List.of(
                "time:1.004", "time-wall:1.020", "killed:1", "status:TO", "message:Time limit exceeded"));

        assertThat(meta.sucesso()).isFalse();
        assertThat(meta.status()).isEqualTo(MetaIsolate.STATUS_TEMPO_EXCEDIDO);
        assertThat(meta.tempoMs()).isEqualTo(1004);
        assertThat(meta.mensagem()).isEqualTo("Time limit exceeded");
    }

    @Test
    void leMortePeloOomKiller() {
        MetaIsolate meta = MetaIsolate.ler(List.of(
                "time:0.300", "cg-mem:262144", "cg-oom-killed:1", "exitsig:9", "status:SG", "message:Caught fatal signal 9"));

        assertThat(meta.oomKilled()).isTrue();
        assertThat(meta.status()).isEqualTo("SG");
        assertThat(meta.memoriaKb()).isEqualTo(262144);
    }

    @Test
    void mensagemPodeTerDoisPontos() {
        MetaIsolate meta = MetaIsolate.ler(List.of("status:XX", "message:execve(\"./solucao\"): No such file"));

        assertThat(meta.mensagem()).isEqualTo("execve(\"./solucao\"): No such file");
    }

    @Test
    void metaVazioNaoTemTempoNemMemoria() {
        MetaIsolate meta = MetaIsolate.ler(List.of());

        assertThat(meta.tempoMs()).isZero();
        assertThat(meta.memoriaKb()).isZero();
    }
}
