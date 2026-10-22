package com.packges.judge.messaging;

import com.packges.judge.service.SolucaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publica as soluções na fila da linguagem (ADR-006, ADR-007). No caminho normal, a publicação acontece logo depois do
 * commit do envio. Como rede de segurança, a {@link ReconciliacaoPublicacao} republica as que ficaram sem publicadaEm.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(MensageriaProperties.class)
public class PublicadorSolucoes {

    private final SolucaoService solucaoService;
    private final RabbitTemplate rabbitTemplate;
    private final AmqpAdmin amqpAdmin;
    private final MensageriaProperties properties;

    /** Filas por linguagem já declaradas por esta instância. */
    private final Set<String> filasDeclaradas = ConcurrentHashMap.newKeySet();

    /** Assíncrono: o 202 do envio não espera o broker, e a thread não herda a transação já commitada. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoEnviar(SolucaoEnviadaEvent evento) {
        publicar(evento.solucaoId());
    }

    /**
     * Publica a solução se ela ainda estiver PENDENTE e sem publicadaEm. Só grava publicadaEm quando o broker confirma
     * (publisher confirm) e a mensagem chega a uma fila. Qualquer falha fica para a reconciliação.
     *
     * @return false se a publicação falhou; true se publicou ou se não havia o que publicar.
     */
    public boolean publicar(UUID solucaoId) {
        try {
            Optional<PublicacaoPendente> publicacao = solucaoService.prepararPublicacao(solucaoId);
            if (publicacao.isEmpty()) {
                return true;
            }
            if (!enviar(publicacao.get())) {
                return false;
            }
            solucaoService.marcarPublicada(solucaoId);
            log.info("Solução {} publicada em {}", solucaoId, publicacao.get().fila());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Publicação da solução {} interrompida; a reconciliação tenta de novo", solucaoId);
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warn("Falha ao publicar a solução {}; a reconciliação tenta de novo: {}", solucaoId, e.toString());
            return false;
        }
    }

    private boolean enviar(PublicacaoPendente publicacao)
            throws InterruptedException, ExecutionException, TimeoutException {
        UUID solucaoId = publicacao.pedido().solucaoId();
        declararFila(publicacao.fila());

        CorrelationData correlacao = new CorrelationData(solucaoId.toString());
        rabbitTemplate.convertAndSend(Mensageria.EXCHANGE, publicacao.fila(), publicacao.pedido(), correlacao);
        CorrelationData.Confirm confirmacao = correlacao.getFuture()
                .get(properties.timeoutConfirmacao().toMillis(), TimeUnit.MILLISECONDS);

        if (!confirmacao.ack()) {
            log.warn("O broker recusou a solução {}: {}", solucaoId, confirmacao.reason());
            return false;
        }
        // mandatory: sem fila ligada à routing key, a mensagem volta (o returned chega antes do confirm)
        if (correlacao.getReturned() != null) {
            log.warn("Solução {} sem fila de destino ({})", solucaoId, publicacao.fila());
            filasDeclaradas.remove(publicacao.fila());
            return false;
        }
        return true;
    }

    /** A declaração é idempotente no broker; aqui, ela só é repetida depois de uma falha. */
    private void declararFila(String fila) {
        if (filasDeclaradas.contains(fila)) {
            return;
        }
        Queue queue = Mensageria.filaDeTrabalho(fila);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(new DirectExchange(Mensageria.EXCHANGE)).with(fila));
        filasDeclaradas.add(fila);
    }
}
