package com.packges.runner.casos;

import com.packges.runner.config.RunnerProperties;
import com.packges.runner.exception.FalhaInfraestruturaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lê os casos de teste OFICIAL no endpoint interno da API, com o token de serviço do runner (ADR-008).
 * <p>
 * Os casos são imutáveis, então ficam em cache pelo id sem expirar; só saem, os menos usados primeiro, quando o cache
 * enche. A API só é chamada quando falta algum caso do pedido.
 */
@Slf4j
@Component
public class CasosDeTesteClient {

    private final RestClient restClient;
    private final Map<UUID, CasoDeTeste> cache;

    public CasosDeTesteClient(RestClient.Builder builder, RunnerProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeoutApi()).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(httpClient);
        fabrica.setReadTimeout(properties.timeoutApi());

        this.restClient = builder
                .requestFactory(fabrica)
                .baseUrl(properties.apiUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                .build();

        int maximo = properties.maxCasosEmCache();
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, CasoDeTeste> menosUsado) {
                return size() > maximo;
            }
        });
    }

    /** Casos na ordem dos ids do pedido. */
    public List<CasoDeTeste> obter(UUID atividadeId, List<UUID> ids) {
        List<CasoDeTeste> casos = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            CasoDeTeste caso = cache.get(id);
            if (caso == null) {
                return buscarNaApi(atividadeId, ids);
            }
            casos.add(caso);
        }
        return casos;
    }

    private List<CasoDeTeste> buscarNaApi(UUID atividadeId, List<UUID> ids) {
        log.debug("Buscando os casos de teste da atividade {}", atividadeId);
        Map<UUID, CasoDeTeste> daAtividade = buscar(atividadeId).stream()
                .collect(Collectors.toMap(CasoDeTeste::id, Function.identity()));
        cache.putAll(daAtividade);

        List<UUID> faltando = ids.stream().filter(id -> !daAtividade.containsKey(id)).toList();
        if (!faltando.isEmpty()) {
            throw new FalhaInfraestruturaException("Casos de teste %s não encontrados na atividade %s"
                    .formatted(faltando, atividadeId));
        }
        return ids.stream().map(daAtividade::get).toList();
    }

    private List<CasoDeTeste> buscar(UUID atividadeId) {
        try {
            List<CasoDeTeste> casos = restClient.get()
                    .uri("/api/internal/activities/{id}/test-cases", atividadeId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<CasoDeTeste>>() {
                    });
            return Objects.requireNonNullElse(casos, List.of());
        } catch (RestClientException e) {
            throw new FalhaInfraestruturaException("Falha ao buscar os casos de teste da atividade " + atividadeId, e);
        }
    }
}
