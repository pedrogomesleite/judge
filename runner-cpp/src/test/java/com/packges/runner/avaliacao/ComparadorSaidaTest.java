package com.packges.runner.avaliacao;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComparadorSaidaTest {

    @Test
    void ignoraEspacosNoFimDasLinhasELinhasEmBrancoNoFinal() {
        assertThat(ComparadorSaida.iguais("1 2  \n3\t\n\n\n", "1 2\n3")).isTrue();
    }

    @Test
    void ignoraADiferencaEntreQuebrasDeLinhaWindowsEUnix() {
        assertThat(ComparadorSaida.iguais("1\r\n2\r\n", "1\n2\n")).isTrue();
    }

    @Test
    void saidaVaziaEquivaleAApenasLinhasEmBranco() {
        assertThat(ComparadorSaida.iguais("", "\n\n")).isTrue();
    }

    @Test
    void valoresDiferentesNaoSaoIguais() {
        assertThat(ComparadorSaida.iguais("4\n", "5\n")).isFalse();
    }

    @Test
    void espacosNoInicioDaLinhaContam() {
        assertThat(ComparadorSaida.iguais(" 5", "5")).isFalse();
    }

    @Test
    void linhaEmBrancoNoMeioConta() {
        assertThat(ComparadorSaida.iguais("1\n\n2", "1\n2")).isFalse();
    }

    @Test
    void saidaIncompletaNaoEIgual() {
        assertThat(ComparadorSaida.iguais("1\n2", "1\n2\n3")).isFalse();
    }
}
