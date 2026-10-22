package com.packges.runner.exception;

/**
 * Falha do runner, e não da solução: isolate, API, broker ou disco. A mensagem é tentada de novo e, depois, vai para
 * a DLQ, onde a API marca a solução como FALHA (ERRO_INTERNO).
 */
public class FalhaInfraestruturaException extends RuntimeException {

    public FalhaInfraestruturaException(String mensagem) {
        super(mensagem);
    }

    public FalhaInfraestruturaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
