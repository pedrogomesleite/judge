package com.packges.judge.entity;

import com.packges.judge.enums.TipoCasoDeTeste;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.UUID;

@Entity
@Table(name = "caso_de_teste")
@Getter
@Setter
@NoArgsConstructor
public class CasoDeTeste {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atividade_id", nullable = false)
    private Atividade atividade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    /** Preenchido apenas para casos COMUNITARIO. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id")
    private GrupoAtividade grupo;

    @Column(nullable = false, columnDefinition = "text")
    private String entrada;

    @Column(nullable = false, columnDefinition = "text")
    private String saidaEsperada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCasoDeTeste tipo;

    /** Só para casos OFICIAL: o caso aparece como exemplo para qualquer pessoa, inclusive sem login. */
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean publico;
}
