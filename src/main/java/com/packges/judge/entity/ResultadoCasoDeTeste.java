package com.packges.judge.entity;

import com.packges.judge.enums.Veredito;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "resultado_caso_de_teste")
@Getter
@Setter
@NoArgsConstructor
public class ResultadoCasoDeTeste {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solucao_id", nullable = false)
    private Solucao solucao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caso_de_teste_id", nullable = false)
    private CasoDeTeste casoDeTeste;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Veredito veredito;

    private Integer tempoMs;

    private Integer memoriaKb;
}
