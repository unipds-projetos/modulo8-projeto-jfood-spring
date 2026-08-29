package br.com.unipds.jfood.recomendacoes.repository;

import br.com.unipds.jfood.recomendacoes.domain.Cliente;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends Neo4jRepository<Cliente, Long> {

    /**
     * A CONSULTA MATADORA: filtragem colaborativa por vizinhanca, em tres saltos.
     *
     *   eu -> restaurantes que avaliei bem
     *      <- outros clientes que tambem avaliaram bem
     *      -> o que ESSES clientes avaliaram bem
     *
     * O desenho do MATCH e literalmente o desenho do grafo. E o WHERE NOT com
     * padrao -- NOT (eu)-[:AVALIOU]->(sugestao) -- e uma clausula so; em SQL,
     * dois NOT EXISTS com subconsultas correlacionadas.
     *
     * $clienteId e parametro nomeado do Cypher. Concatenar string aqui abriria a
     * mesma porta de injecao da Etapa 3.
     */
    @Query("""
           MATCH (eu:Cliente {id: $clienteId})-[a1:AVALIOU]->(:Restaurante)
                 <-[a2:AVALIOU]-(vizinho:Cliente)-[a3:AVALIOU]->(sugestao:Restaurante)
           WHERE a1.nota >= $notaMinima
             AND a2.nota >= $notaMinima
             AND a3.nota >= $notaMinima
             AND vizinho <> eu
             AND NOT (eu)-[:AVALIOU]->(sugestao)
             AND NOT (eu)-[:PEDIU]->(sugestao)
           OPTIONAL MATCH (sugestao)-[:DO_TIPO]->(cat:Categoria)
           RETURN sugestao.id                        AS restauranteId,
                  sugestao.nome                      AS nome,
                  coalesce(cat.nome, 'Sem categoria') AS categoria,
                  count(DISTINCT vizinho)            AS forcaRecomendacao,
                  sum((a2.nota - 3) * (a3.nota - 3)) AS pesoTotal
           ORDER BY forcaRecomendacao DESC, pesoTotal DESC
           LIMIT $limite
           """)
    List<RecomendacaoRestaurante> recomendarPorVizinhanca(@Param("clienteId") Long clienteId,
                                                          @Param("notaMinima") int notaMinima,
                                                          @Param("limite") int limite);

    /**
     * O MESMO percurso, ranqueado por nota ponderada e desempatado por
     * proximidade de categoria com o que o cliente ja gosta.
     *
     * O peso (nota - 3) transforma a escala 1-5 em -2..+2: uma nota 5 de quem
     * tambem deu 5 no restaurante-base vale +4; uma nota 4 de quem deu 4 vale +1.
     * Contar vizinhos trata as duas como iguais.
     */
    @Query("""
           MATCH (eu:Cliente {id: $clienteId})-[a1:AVALIOU]->(base:Restaurante)
                 <-[a2:AVALIOU]-(vizinho:Cliente)-[a3:AVALIOU]->(sugestao:Restaurante)
           WHERE a1.nota >= $notaMinima
             AND a2.nota >= $notaMinima
             AND a3.nota >= $notaMinima
             AND vizinho <> eu
             AND NOT (eu)-[:AVALIOU]->(sugestao)
             AND NOT (eu)-[:PEDIU]->(sugestao)
           WITH sugestao,
                count(DISTINCT vizinho)            AS forcaRecomendacao,
                sum((a2.nota - 3) * (a3.nota - 3)) AS pesoTotal,
                collect(DISTINCT base)             AS bases
           OPTIONAL MATCH (sugestao)-[:DO_TIPO]->(cat:Categoria)<-[:DO_TIPO]-(b:Restaurante)
           WHERE b IN bases
           WITH sugestao, forcaRecomendacao, pesoTotal, count(DISTINCT cat) AS categoriasEmComum
           OPTIONAL MATCH (sugestao)-[:DO_TIPO]->(minhaCat:Categoria)
           RETURN sugestao.id   AS restauranteId,
                  sugestao.nome AS nome,
                  coalesce(minhaCat.nome, 'Sem categoria') AS categoria,
                  forcaRecomendacao,
                  pesoTotal
           ORDER BY pesoTotal DESC, categoriasEmComum DESC, forcaRecomendacao DESC
           LIMIT $limite
           """)
    List<RecomendacaoRestaurante> recomendarPonderado(@Param("clienteId") Long clienteId,
                                                      @Param("notaMinima") int notaMinima,
                                                      @Param("limite") int limite);

    /**
     * A SEGUNDA CONSULTA DE GRAFO: deteccao de fraude.
     *
     * Contas diferentes que compartilham cartao ou dispositivo. Em SQL seria um
     * self-join sobre uma tabela de meios de pagamento -- possivel, mas a
     * pergunta seguinte ("e quem esta conectado a essas contas por sua vez?")
     * exigiria outro nivel de join, e o proximo, mais um.
     *
     * Aqui o proximo nivel e trocar o padrao por [:PAGA_COM|ACESSA_DE*..4].
     */
    @Query("""
           MATCH (c1:Cliente)-[:PAGA_COM|ACESSA_DE]->(vinculo)
                 <-[:PAGA_COM|ACESSA_DE]-(c2:Cliente)
           WHERE elementId(c1) < elementId(c2)
           RETURN c1.nome AS contaA,
                  c2.nome AS contaB,
                  labels(vinculo)[0] AS tipoDeVinculo,
                  coalesce(vinculo.token, vinculo.id) AS vinculo
           ORDER BY contaA, contaB
           """)
    List<ContaVinculada> contasVinculadas();
}
