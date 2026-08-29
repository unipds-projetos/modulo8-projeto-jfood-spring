package br.com.unipds.jfood.catalogo.service;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;

import br.com.unipds.jfood.catalogo.domain.Restaurante;
import br.com.unipds.jfood.catalogo.web.dto.RelatorioPorCategoria;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
public class RelatorioService {

    private final MongoTemplate mongoTemplate;

    public RelatorioService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * "Qual o preco medio e a quantidade de itens por categoria de cozinha,
     * considerando apenas restaurantes abertos?"
     *
     * A ORDEM DOS ESTAGIOS e a decisao de performance da pipeline: o $match vem
     * ANTES dos dois $unwind. Um $unwind multiplica os documentos -- cada
     * restaurante vira uma linha por secao, e depois uma por item. Filtrar depois
     * seria descartar documentos que voce mesmo acabou de fabricar, e o $match
     * inicial ainda consegue usar indice; depois do $unwind, nao consegue mais.
     */
    public List<RelatorioPorCategoria> precoMedioPorCategoria() {
        Aggregation pipeline = newAggregation(
                match(Criteria.where("aberto").is(true)),          // <- ANTES do unwind
                unwind("cardapio"),
                unwind("cardapio.itens"),
                group("categoria")
                        .count().as("totalItens")
                        .avg("cardapio.itens.preco").as("precoMedio")
                        .min("cardapio.itens.preco").as("precoMinimo")
                        .max("cardapio.itens.preco").as("precoMaximo")
                        .addToSet("_id").as("restaurantes"),
                project("totalItens", "precoMedio", "precoMinimo", "precoMaximo")
                        .and("_id").as("categoria")
                        .and("restaurantes").size().as("restaurantesAbertos"),
                sort(Sort.by(Sort.Direction.DESC, "totalItens")));

        return mongoTemplate.aggregate(pipeline, Restaurante.class, RelatorioPorCategoria.class)
                .getMappedResults();
    }
}
