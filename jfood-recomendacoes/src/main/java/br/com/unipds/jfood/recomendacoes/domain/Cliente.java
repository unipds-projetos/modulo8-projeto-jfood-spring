package br.com.unipds.jfood.recomendacoes.domain;

import java.util.List;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

/** O id vem do PostgreSQL. */
@Node
public record Cliente(

        @Id Long id,
        String nome,

        // Relacionamento SEM propriedades: a aresta e so a ligacao.
        @Relationship(type = "SEGUE", direction = Relationship.Direction.OUTGOING)
        List<Cliente> seguindo,

        // Relacionamento COM propriedades: precisa da classe @RelationshipProperties.
        @Relationship(type = "AVALIOU", direction = Relationship.Direction.OUTGOING)
        List<Avaliou> avaliacoes
) {}
