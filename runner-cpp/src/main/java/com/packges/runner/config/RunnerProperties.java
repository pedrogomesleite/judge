package com.packges.runner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * @param fila                   fila da linguagem consumida por este runner (Linguagem.fila na API).
 * @param boxes                  execuções simultâneas (um box do isolate por execução); é também a concorrência do
 *                               listener. Não pode passar do num_boxes do isolate.cf.
 * @param apiUrl                 URL base da API, usada para buscar os casos de teste no endpoint interno.
 * @param token                  token de serviço do runner (juizin.runner.token na API).
 * @param isolate                caminho do binário do isolate (setuid root).
 * @param compilador             caminho do g++ dentro do sandbox.
 * @param tempoCompilacao        limite de tempo de CPU da compilação.
 * @param memoriaCompilacaoMb    limite de memória da compilação.
 * @param tamanhoMaximoArquivoKb maior arquivo que o código (ou o compilador) pode gravar no box.
 * @param timeoutApi             timeout de conexão e de leitura das chamadas à API.
 * @param timeoutConfirmacao     espera máxima pelo publisher confirm dos resultados.
 * @param maxCasosEmCache        casos de teste guardados em memória (os menos usados saem primeiro).
 */
@ConfigurationProperties("juizin.runner")
public record RunnerProperties(
        String fila,
        Integer boxes,
        String apiUrl,
        String token,
        String isolate,
        String compilador,
        Duration tempoCompilacao,
        Integer memoriaCompilacaoMb,
        Integer tamanhoMaximoArquivoKb,
        Duration timeoutApi,
        Duration timeoutConfirmacao,
        Integer maxCasosEmCache
) {
    private static final int TAMANHO_MINIMO_TOKEN_BYTES = 32;

    public RunnerProperties {
        if (token == null || token.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_TOKEN_BYTES) {
            throw new IllegalStateException("juizin.runner.token deve ter no mínimo 32 bytes");
        }
        if (fila == null) {
            fila = "submissions.cpp";
        }
        if (boxes == null) {
            boxes = 2;
        }
        if (apiUrl == null) {
            apiUrl = "http://localhost:8080";
        }
        if (isolate == null) {
            isolate = "/usr/local/bin/isolate";
        }
        if (compilador == null) {
            compilador = "/usr/bin/g++";
        }
        if (tempoCompilacao == null) {
            tempoCompilacao = Duration.ofSeconds(10);
        }
        if (memoriaCompilacaoMb == null) {
            memoriaCompilacaoMb = 512;
        }
        if (tamanhoMaximoArquivoKb == null) {
            tamanhoMaximoArquivoKb = 65_536;
        }
        if (timeoutApi == null) {
            timeoutApi = Duration.ofSeconds(10);
        }
        if (timeoutConfirmacao == null) {
            timeoutConfirmacao = Duration.ofSeconds(5);
        }
        if (maxCasosEmCache == null) {
            maxCasosEmCache = 1000;
        }
    }
}
