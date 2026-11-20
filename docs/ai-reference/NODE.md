# Gonthera CLI 2.1.4 — Referência técnica para IA — Node.js

## Finalidade e estado da implementação

Este arquivo fornece contexto autocontido para uma IA trabalhar na geração Node.js
do Gonthera CLI. O alvo gera TypeScript + Express + Prisma para PostgreSQL ou
MongoDB. A versão 2.1.4 fechou o primeiro suporte MongoDB sem alterar o contrato HTTP
consolidado no PostgreSQL.

Fontes de verdade, em ordem:

1. `src/main/java/com/gonthera/cli/service/node`;
2. `src/main/resources/xsd/node`;
3. `service/common`, modelos compartilhados e testes Node;
4. este arquivo;
5. handoffs, README e portal público.

O `node-test-service` é a referência executável atual. Em 7 de setembro de 2026,
foram aprovados schema Prisma 5.22, geração do client, `db push`, compilação
TypeScript, 14 testes locais, criação/leitura real de `Customer` e o ciclo
ManyToMany `Customer.tags` / `Tag.customers` em MongoDB replica set.

## Invariantes obrigatórios

- `src/generated` é inteiramente descartável e nunca importa arquivos do consumidor.
- O consumidor mantém servidor, app, middleware, `.env`, migrations e configurações
  concretas fora de `src/generated`.
- PostgreSQL é o provider padrão retrocompatível.
- MongoDB só é válido em `language: "NODE"`.
- Rotas, DTOs, filtros, paginação, projeção, erros e OpenAPI não expõem o provider.
- O contrato de relações usa objetos DTO, nunca FKs sintéticas Prisma.
- Todo modelo Node persistido tem exatamente uma chave primária escalar.
- MongoDB 2.1.4 aceita somente chave `uuid`, armazenada como string em `_id`.
- Repositories usam transações; MongoDB precisa operar como replica set.
- Não crie fallback não transacional para MongoDB standalone.
- PostgreSQL usa Prisma Migrate; MongoDB usa `prisma db push`.
- A versão permanece em Prisma 5.22. Uma atualização para Prisma 8 é outro projeto.

## Execução e configuração

O gerador continua sendo o mesmo Maven Plugin/JAR das outras linguagens; não existe
um pacote npm do gerador.

```bash
java -jar gonthera-cli-2.1.4.jar --validate
java -jar gonthera-cli-2.1.4.jar
```

Exemplo de scripts do consumidor:

```json
{
  "scripts": {
    "gonthera-cli": "java -jar ./tools/gonthera-cli-2.1.4.jar",
    "gonthera-validate": "java -jar ./tools/gonthera-cli-2.1.4.jar --validate",
    "prisma:validate": "prisma validate",
    "prisma:generate": "prisma generate",
    "prisma:migrate": "prisma migrate dev",
    "prisma:push": "prisma db push",
    "typecheck": "tsc --noEmit",
    "build": "prisma generate && tsc"
  }
}
```

Ordem de configuração: `.gonthera`, `project.json`, `properties.json` legado. A
validação isolada exige `.gonthera`; a geração aceita temporariamente os três.

`.gonthera/project.json`:

```json
{
  "mainPackage": "com.example.customer",
  "projectName": "customer-service",
  "language": "NODE",
  "database": {
    "provider": "MONGODB"
  }
}
```

`database.provider` aceita `POSTGRESQL` e `MONGODB`. Se `database` estiver ausente,
assume PostgreSQL. Se o objeto existir, `provider` é obrigatório.

## Estrutura modular compartilhada

```text
.gonthera/
├── project.json
├── entities.json
├── endpoints.json
├── enums.json
└── messaging.json
```

Os arquivos de lista contêm arrays diretamente. `messaging.json` contém o objeto
RabbitMQ diretamente. `authorization.json` é Java-only e não deve ser apresentado
como autenticação Node.

## Contrato de entidade e campo

```json
{
  "comment": "Cadastro de clientes",
  "entityName": "customer",
  "tableName": "customer",
  "generateDefaultControllers": true,
  "controllerAbstract": false,
  "onlyDTO": false,
  "entityFields": [
    {
      "comment": "Identificador",
      "fieldName": "id",
      "list": false,
      "fieldProperties": {
        "fieldType": "uuid",
        "required": true,
        "valueDefault": ""
      },
      "metadata": {
        "nullable": false,
        "key": true
      }
    }
  ]
}
```

- `generateDefaultControllers`: padrão `true`; `false` remove controller e rota.
- `controllerAbstract`: exige factory concreta quando `true`.
- `onlyDTO`: impede repository/controller/rota para a entidade, mas preserva o
  contrato DTO necessário a referências.
- `serviceAbstract`: não cria Service Node; não documente essa flag como funcional
  neste alvo.
- `classExtends`: não possui efeito confiável.
- Tipos de entidade devem apontar a um `entityName` existente e declarar
  `relationShips`.

## Tipos Node e Prisma

| `fieldType` | DTO TypeScript | Prisma |
| --- | --- | --- |
| `uuid` | `string` | `String` |
| `string`, `password` | `string` | `String` |
| `datetime`, `date` | `string` | `DateTime` |
| `int`, `integer` | `number` | `Int` |
| `long` | `number \| string` | `BigInt` |
| `decimal`, `double` | `number` | `Float` |
| `boolean` | `boolean` | `Boolean` |
| `byte`, `byte[]`, `inputStream` | Base64 `string` | `Bytes` |
| `map` | `Record<string, unknown>` | `Json` |
| enum | tipo enum | enum Prisma |
| entidade | `<Entity>DTO` | relação Prisma |

`decimal` não possui precisão decimal nativa no MongoDB desta versão porque o
gerador usa Prisma `Float`. `long` fora da faixa segura do JavaScript é serializado
como string na saída.

## Pipeline e arquivos gerados

`GenerateNode` valida relações antes de limpar a saída, resolve o dialeto uma vez e
então recria `src/generated`:

```text
src/generated/
├── common/
│   ├── contracts.ts
│   ├── entity-metadata.ts
│   └── query.ts
├── configuration/database/database.config.ts
├── controllers/*.controller.ts
├── converters/entity-converter.ts
├── documentation/openapi.ts
├── endpoints/*.endpoint.ts
├── enums/*.enum.ts
├── messaging/rabbitmq/                 # opcional
├── models/*.model.ts
├── repositories/*.repository.ts
├── routes/*.routes.ts
├── routes/index.ts
└── static/
    ├── properties.json
    ├── resources.json
    └── postgree.sql                    # apenas PostgreSQL

prisma/schema.prisma
```

No MongoDB, `postgree.sql` não é criado e uma cópia antiga é removida ao trocar o
provider. `prisma/schema.prisma` também é gerado e não deve ser editado manualmente.

## Arquivos que pertencem ao consumidor

O Gonthera não cria nem deve sobrescrever:

- `package.json`, lockfile e `tsconfig.json`;
- `.env`;
- migrations Prisma;
- `src/app.ts` e `src/server.ts`;
- implementação concreta de `DatabaseConfig`;
- middleware de erros e autenticação/autorização;
- montagem do Swagger UI e extensões do OpenAPI;
- implementação concreta do RabbitMQ e listeners;
- subclasses de controllers e registry de factories.

## DatabaseConfig e ciclo de vida

A base abstrata importa `dotenv/config`, resolve `DATABASE_URL`, instancia um único
`PrismaClient` preguiçosamente e expõe `client`, `connect()` e `disconnect()`.
Hooks protegidos: `resolveUrl`, `createOptions`, `createClient`.

```ts
export class AppDatabaseConfig extends DatabaseConfig {}
export const database = new AppDatabaseConfig();

await database.connect();
app.use(createGeneratedRoutes(database.client, controllerFactories));
// SIGINT/SIGTERM
await database.disconnect();
```

Reutilize uma instância. Conecte antes de abrir a porta e desconecte no shutdown.
Overrides do runtime não mudam a URL lida pela CLI Prisma.

## PostgreSQL

Datasource:

```prisma
datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}
```

Regras:

- UUID usa `@db.Uuid`; date usa `@db.Date`.
- PK UUID usa `@default(uuid())`.
- PK `int`/`integer` usa `@default(autoincrement())`.
- ManyToMany é implícito no Prisma.
- A listagem usa `$transaction([...], { isolationLevel: RepeatableRead })`.
- Gera `postgree.sql`, mas serviços Prisma devem preferir migrations Prisma para
  evitar divergência da tabela implícita ManyToMany.

Fluxo:

```bash
npx prisma validate
npx prisma generate
npx prisma migrate dev --name initial
```

## MongoDB 2.1.4

Datasource:

```prisma
datasource db {
  provider     = "mongodb"
  url          = env("DATABASE_URL")
  relationMode = "prisma"
}
```

Chave suportada:

```prisma
id String @id @default(uuid()) @map("_id")
```

Não use `ObjectId` nem `@db.ObjectId`. O identificador HTTP permanece UUID textual.
Chaves inteiras Mongo são rejeitadas antes da limpeza de `src/generated`.

URL autenticada e transacional:

```dotenv
DATABASE_URL="mongodb://user:password@localhost:27017/customer?authSource=admin&replicaSet=rs0"
```

- `authSource=admin` define onde autenticar.
- `replicaSet=rs0` identifica o conjunto de réplicas.
- Os parâmetros não são equivalentes nem substitutos.
- O servidor precisa realmente estar iniciado/configurado como replica set.
- Mongo standalone produz Prisma `P2031` ao executar o repository transacional.
- Não remova transações como workaround.

Fluxo:

```bash
npx prisma validate
npx prisma generate
npx prisma db push
```

Prisma Migrate não suporta MongoDB.

## Arquitetura CRUD

```text
Express route
  -> <Entity>Controller
  -> <Entity>Repository
  -> converter/query metadata
  -> PrismaClient
```

Rotas:

| Método | Caminho | Resultado |
| --- | --- | --- |
| POST | `/customer` | DTO persistido e expandido |
| PUT | `/customer/:id` | DTO atualizado e expandido |
| DELETE | `/customer/:id` | HTTP 204 |
| GET | `/customer/:id` | DTO ou 404 |
| GET | `/customer` | envelope paginado |

Os controllers apenas traduzem Request/Response e delegam. Repositories resolvem a
chave real da metadata, convertem DTO -> Prisma input e Prisma row -> DTO.

`save` e `update` usam transação interativa. `update` verifica existência e gera 404
antes de atualizar. `getAll` executa `count` e `findMany` na mesma transação. Mongo
omite isolation level; PostgreSQL usa RepeatableRead.

Erros de entrada usam `CrudError` com status. O middleware uniforme é responsabilidade
do consumidor; o `node-test-service` retorna `{error:{code,message}}` e sanitiza
detalhes Prisma.

## Chaves primárias

- Exatamente uma chave escalar por entidade.
- A rota permanece `/:id`, mesmo se a chave se chamar `code`.
- O repository converte o texto da URL para o tipo configurado.
- Relações usam o nome real: `{"category":{"code":12}}`.
- PostgreSQL gera defaults para UUID e inteiros.
- Outras chaves sem default precisam ser enviadas no POST.
- MongoDB 2.1.4 restringe a chave a UUID.

## Contrato de relacionamentos HTTP

Entrada:

```json
{
  "name": "Alice",
  "profile": { "bio": "Perfil" },
  "tags": [{ "id": "550e8400-e29b-41d4-a716-446655440000" }]
}
```

Nunca envie `profileId`, `customerId`, `connect`, `set`, `create` ou outra operação
Prisma. O converter rejeita campos DTO desconhecidos.

- `reference: true`: conecta pela chave e ignora dados adicionais do objeto.
- `reference: false`: pode criar ou atualizar objeto aninhado.
- PUT preserva campos omitidos.
- OneToMany inverso enviado substitui a coleção e remove filhos omitidos.
- ManyToMany enviado substitui vínculos sem excluir entidades compartilhadas.
- Não existe cascade recursivo geral equivalente ao JPA.

Saída expande relações. O campo recíproco imediato vira `null`, e a profundidade
máxima é seis relações. Campos não projetados ficam `null`; FKs internas nunca saem.

## Relações Prisma

### OneToOne

O proprietário tem FK escalar `@unique`; o inverso é opcional porque o Prisma exige:

```prisma
model Profile {
  id         String   @id @default(uuid()) @map("_id")
  customer   Customer @relation("Profile_Customer_customer",
    fields: [customerId], references: [id],
    onDelete: NoAction, onUpdate: NoAction)
  customerId String   @unique @map("customer")
}
```

As ações `NoAction` são específicas do Mongo proprietário; PostgreSQL não as emite.

### ManyToOne / OneToMany

ManyToOne deve ser escalar, proprietário e guardar FK. OneToMany deve ser lista,
inverso e apontar por `mappedBy`. Mongo adiciona `@@index` na FK porque
`relationMode = "prisma"` não cria índice automaticamente.

### ManyToMany

PostgreSQL usa relação implícita. MongoDB gera arrays escalares nos dois lados:

```prisma
model Customer {
  tags    Tag[]    @relation("Customer_Tag_tags", fields: [tagsIds], references: [id])
  tagsIds String[] @map("tags_ids")
}

model Tag {
  customers    Customer[] @relation("Customer_Tag_tags", fields: [customersIds], references: [id])
  customersIds String[]   @map("customers_ids")
}
```

Os arrays são internos e não entram em models TypeScript, metadata pública, filtros
de campos ou OpenAPI. Seus nomes são derivados do campo da relação e conflitos com
campos configurados são rejeitados.

Validação real concluída: criação independente, associação por DTO, leitura pelos
dois lados, substituição por `tags: []`, remoção dos IDs nos dois documentos e
preservação da Tag compartilhada.

### Pareamento e autorrelações

- Declare ambos os lados; o gerador não inventa inversos.
- Proprietário: `bidirectional: false`.
- Inverso: `bidirectional: true`, `mappedBy` exato.
- O fallback de `mappedBy` usa o nome da entidade inversa, mas prefira explicitá-lo.
- Pares ausentes/ambíguos, cardinalidade incompatível e colisões de FK falham antes
  de apagar a saída.
- Múltiplas relações entre os mesmos models usam nomes derivados do campo.
- Autorrelações OneToOne, OneToMany e ManyToMany são suportadas.
- Models TypeScript não geram autoimport e consolidam imports duplicados.

## Controllers abstratos e factories

`controllerAbstract: true` gera uma base abstrata com CRUD implementado, repository
`protected` e métodos normais sobrescrevíveis. A aplicação registra a implementação:

```ts
class AppCustomerController extends CustomerController {
  override async save(request: Request, response: Response): Promise<void> {
    await super.save(request, response);
  }
}

const controllerFactories = {
  customer: repository => new AppCustomerController(repository)
} satisfies GeneratedControllerFactories;

app.use(createGeneratedRoutes(database.client, controllerFactories));
```

A factory é obrigatória para controller abstrato e opcional para concreto. As rotas
usam wrappers para preservar `this`, dispatch virtual e `super`. Há validação em
runtime além do contrato TypeScript. Não repita bindings HTTP no consumidor.

## Paginação, filtro, ordenação e projeção

Resposta:

```json
{"size":20,"offset":0,"total":1,"contents":[]}
```

- `size`: padrão 20, precisa ser inteiro positivo.
- `offset`: entrada mínima efetiva 1; saída zero-based.
- `filter`: string do dialeto Gonthera.
- `order`: `campo,asc|desc`, direção padrão asc.
- `displayFields`: `*` ou campos separados por `;`/`,` com caminhos.

Filtros:

- `eq`: texto faz contains case-insensitive; UUID exato; enum por nome/ordinal;
  números e booleanos são convertidos.
- `gte`/`ge`, `lte`/`le`: apenas date/datetime ISO.
- relações: caminho pontuado.
- coleções: caminho pontuado ou marcador `*`.
- `isNull`/`notNull`: relações e escalares.
- apenas uma família lógica por expressão: `and` ou `or`.

Mongo diferencia nulo e ausente. Para escalar, `isNull` gera nulo OU `isSet:false`;
`notNull` gera não nulo E `isSet:true`. PostgreSQL conserva `null`/`not:null`.

Rejeições 400: campo desconhecido, valor inválido, caminho profundo, ordering de
lista, operador não suportado, mistura `and/or`, parênteses e sintaxe de escape.

Projeção gera `select` Prisma, corta o campo recíproco e limita profundidade. Se uma
seleção ficaria vazia, a chave interna pode ser carregada apenas para satisfazer o
Prisma, mas não deve vazar na resposta.

## OpenAPI

`src/generated/documentation/openapi.ts` usa OpenAPI 3.0.3 e inclui:

- paths CRUD somente para entidades com CRUD gerado;
- schemas de resposta, POST, PUT, referência e página;
- chave e tipos reais;
- relações e enums;
- query params de paginação/filtro/order/projeção;
- erros padrão;
- endpoints customizados, anonymous e permissões como metadata.

Schemas de entidades referenciadas permanecem mesmo quando não possuem CRUD. O
consumidor pode mesclar título, servers, security, health e paths manuais, sem editar
o documento gerado.

## Endpoints customizados

O gerador cria contratos TypeScript e estrutura de rota. GET recebe inputs como
query params; POST recebe JSON. `grouper`, anonymous e permissions entram no OpenAPI,
mas autenticação e regra de negócio permanecem no consumidor. Inputs/outputs devem
ser arrays, mesmo vazios.

## RabbitMQ Node

Gerado somente quando há `pub` ou `sub`. Dependências fixadas na entrega:
`amqp-connection-manager` 5 e `amqplib` 2.

- Uma `RabbitConfig` concreta é compartilhada pelo processo.
- Publishers usam confirm channel e buffer durante reconexão.
- Subscribers restauram setup/consumer, aplicam prefetch, dão ack após sucesso e
  nack sem requeue por padrão.
- `requeueOnError` deve ser habilitado apenas com idempotência e política de poison
  messages.
- Listener, URL, exchange, startup e shutdown pertencem ao consumidor.

## Dependências esperadas

- Node 20.19+;
- TypeScript;
- Express 5 no serviço de referência;
- `prisma` e `@prisma/client` 5.22;
- `dotenv` 16;
- RabbitMQ apenas quando configurado.

Não migre para Prisma 8 dentro de uma correção da 2.1.4: a mudança envolve nova
geração de client/runtime e requisitos maiores de Node, TypeScript e MongoDB.

## Limites e riscos conhecidos

- MongoDB exige replica set.
- MongoDB só aceita chave UUID nesta versão.
- Sem Prisma Migrate no MongoDB.
- Sem ObjectId no HTTP.
- Sem precisão Decimal128.
- Filtros JSON avançados não fazem parte do contrato.
- Autenticação/autorização Node não é gerada.
- A expansão tem profundidade máxima seis.
- Renomear campos que participam do nome da relação pode exigir migração de dados.
- Trocar provider não migra dados entre PostgreSQL e MongoDB.
- O validador não compila nem executa o consumidor.

## Checklist para uma IA alterar o Node

1. Confirmar o provider e preservar PostgreSQL como fallback.
2. Validar relações e chave antes de limpar `src/generated`.
3. Manter diferenças de banco em `NodeDatabaseDialect`, não espalhadas no HTTP.
4. Alterar templates `.mxsd`, nunca usar a saída gerada como fonte.
5. Não expor FK/arrays internos em DTO, metadata ou OpenAPI.
6. Preservar transações e exigir replica set no Mongo.
7. Testar `prisma validate`, `prisma generate`, typecheck e build.
8. Para Mongo, executar `prisma db push`; para PostgreSQL, revisar migration.
9. Testar relações nos dois lados, substituição e rollback.
10. Testar filtros nulo/ausente no Mongo.
11. Conferir factory de todo controller abstrato.
12. Atualizar OpenAPI, handoffs, portal e changelog quando o contrato mudar.

## Critério de conclusão

Uma mudança Node está concluída quando ambos os schemas afetados validam, fontes
TypeScript compilam, a saída não vaza detalhes do banco, código manual sobrevive à
regeneração, transações mantêm atomicidade e o contrato HTTP/OpenAPI permanece
coerente entre PostgreSQL e MongoDB.
