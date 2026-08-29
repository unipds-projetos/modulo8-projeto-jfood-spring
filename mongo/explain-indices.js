// =============================================================================
// JFood — Etapa 8, item 5: indices com criterio, medidos com explain()
//
//   docker exec jfood-mongodb mongosh --quiet -u admin -p adminpassword \
//     --authenticationDatabase admin jfood_catalogo --file /tmp/explain.js
//
// Com 7 restaurantes nao ha o que medir. O script cria 100 mil documentos
// sinteticos, mede COM e SEM indice, e apaga tudo no fim.
// =============================================================================

const CATEGORIAS = ["Italiana", "Japonesa", "Brasileira", "Árabe", "Vegana"];

print("### carregando 100.000 restaurantes sinteticos...");
const lote = [];
for (let i = 1; i <= 100000; i++) {
  lote.push({
    sintetico: true,
    nome: "Restaurante Sintetico " + i,
    categoria: CATEGORIAS[i % 5],
    foto_url: "https://cdn.jfood.example/rest/" + i + ".jpg",
    nota_media: NumberDecimal(((i % 50) / 10 + 0.5).toFixed(1)),
    taxa_entrega: NumberDecimal("7.90"),
    aberto: i % 3 !== 0,
    abre_as: "18:00", fecha_as: "23:00",
    visualizacoes: NumberLong("0"),
    cardapio: []
  });
  if (lote.length === 5000) { db.restaurantes.insertMany(lote); lote.length = 0; }
}
if (lote.length) db.restaurantes.insertMany(lote);
print("total na colecao: " + db.restaurantes.countDocuments());

const CONSULTA = { nome: "Restaurante Sintetico 77777" };

function medir(rotulo) {
  const e = db.restaurantes.find(CONSULTA).explain("executionStats").executionStats;
  print("  " + rotulo);
  print("    estagio vencedor      : " + e.executionStages.stage);
  print("    documentos examinados : " + e.totalDocsExamined);
  print("    chaves de indice examinadas: " + e.totalKeysExamined);
  print("    documentos retornados : " + e.nReturned);
  print("    tempo (ms)            : " + e.executionTimeMillis);
}

print("");
print("### SEM indice em 'nome'");
db.restaurantes.dropIndex("idx_restaurante_nome");
medir("collection scan");

print("");
print("### COM indice em 'nome'");
db.restaurantes.createIndex({ nome: 1 }, { name: "idx_restaurante_nome" });
medir("index scan");

print("");
print("### tamanho dos indices da colecao");
const stats = db.restaurantes.stats();
Object.keys(stats.indexSizes).forEach(function (nome) {
  print("  " + nome + ": " + (stats.indexSizes[nome] / 1024).toFixed(0) + " KB");
});

print("");
print("### limpando os sinteticos");
print("  removidos: " + db.restaurantes.deleteMany({ sintetico: true }).deletedCount);
print("  restam:    " + db.restaurantes.countDocuments());
