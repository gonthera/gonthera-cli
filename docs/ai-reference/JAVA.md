# Gonthera CLI 2.2.0 (em desenvolvimento) — Referência técnica para IA — Java

## Finalidade e status

Este arquivo é contexto operacional para uma IA que precise analisar, corrigir,
estender ou integrar a geração Java do Gonthera CLI. Ele descreve o comportamento
consolidado da versão 2.1.4 e a nova saída Java da 2.2.0. Não é um tutorial genérico de Spring: é um mapa do
contrato e das decisões específicas deste repositório.

Use como fontes de verdade, nesta ordem:

1. código em `src/main/java/br/com/smartverse/gonthera/cli/service/java` e templates em
   `src/main/resources/xsd/java`;
2. validação compartilhada em `service/common` e modelos em `model`;
3. este documento;
4. `docs/handoff/gonthera-cli-project/HANDOFF.md`;
5. `README.md` e documentação pública.

Se a documentação divergir do código, confirme o comportamento no código antes de
alterar qualquer coisa e atualize ambos. A versão 2.1.4 adicionou MongoDB apenas ao
Node; o fluxo Java continua direcionado a PostgreSQL.

## Invariantes que uma IA deve preservar

- A saída Java fica em `target/generated-sources/gonthera`, sob `mainPackage`, sem `_gen`.
- Customizações do consumidor ficam em `src/main/java`, com nomes diferentes das classes geradas.
- O fluxo CRUD é `Controller -> Service -> Repository`.
- Transações pertencem ao Service, não ao Controller.
- `serviceAbstract` existe apenas para Java.
- O banco gerado para Java é PostgreSQL; `database.provider` não é aceito em projeto
  `language: "JAVA"`.
- A propriedade atual é `generateDefaultControllers`; o alias
  `generateDefaultHandlers` é legado e gera warning.
- A propriedade atual é `controllerAbstract`; `handlerAbstract` é alias legado.
- Campos usam `fieldProperties.fieldType` e `metadata.nullable/key`; não invente
  propriedades simplificadas no nível do campo.
- Relacionamentos usam a propriedade escrita exatamente como `relationShips`.
- O lado proprietário usa `bidirectional: false`; o inverso usa
  `bidirectional: true` e `mappedBy`.
- A chave JSON de permissões continua escrita `premissions` por compatibilidade.
- O arquivo SQL se chama `postgree.sql`, inclusive com essa grafia.

## Execução e resolução da configuração

O Gonthera CLI é Maven Plugin e JAR standalone. Ele resolve arquivos a partir de
a base do módulo Maven (`project.basedir`). No JAR standalone, usa
`System.getProperty("user.dir")`; execute na raiz do serviço consumidor.

Ordem de resolução:

1. `.gonthera/project.json` e arquivos modulares;
2. `project.json` na raiz;
3. `properties.json` legado na raiz.

Quando `.gonthera/` existe, `.gonthera/project.json` é obrigatório e não há fallback
para a raiz. A validação isolada exige `.gonthera`; a geração ainda aceita os
formatos legados.

Comandos:

```bash
mvn gonthera-cli:validate
mvn gonthera-cli:generate-sources

java -jar gonthera-cli-2.1.4.jar --validate
java -jar gonthera-cli-2.1.4.jar
```

O goal tem fase padrão `generate-sources`. O consumidor deve declarar uma
`execution` com `<phase>generate-sources</phase>` e esse goal no POM para gerar e registrar os fontes antes de compilar
em cada build. Declarar o plugin sem `executions` não aciona a geração no lifecycle.
O registro da raiz gerada só vale na sessão atual do Maven.
`target/generated-sources/gonthera` é a raiz dos fontes, não parte do pacote.
No IntelliJ, use **Generate Sources and Update Folders** após atualizar o POM.

## Configuração modular

```text
.gonthera/
├── project.json
├── entities.json
├── endpoints.json
├── enums.json
├── messaging.json
└── authorization.json
```

`.gonthera/project.json`:

```json
{
  "mainPackage": "com.example.customer",
  "projectName": "customer-service",
  "language": "JAVA"
}
```

- `entities.json`, `endpoints.json` e `enums.json` contêm arrays diretamente.
- `messaging.json` contém diretamente o objeto equivalente a `messaging`.
- `authorization.json` contém diretamente o objeto equivalente a `authorization`.
- Cada arquivo separado sobrescreve somente sua seção no `project.json`.
- Listas ausentes nos dois lugares são normalizadas para listas vazias.
- O validador estrutural rejeita propriedades desconhecidas e tipos JSON errados.

No formato de arquivo único, inclua pelo menos:

```json
{
  "mainPackage": "com.example.customer",
  "projectName": "customer-service",
  "language": "JAVA",
  "entities": [],
  "endpoints": [],
  "enums": []
}
```

## Contrato de entidade

```json
{
  "comment": "Cadastro de clientes",
  "entityName": "customer",
  "tableName": "customer",
  "classExtends": "",
  "generateDefaultControllers": true,
  "controllerAbstract": false,
  "serviceAbstract": false,
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

Semântica:

- `entityName`: base de nomes de classe; use `lowerCamelCase`.
- `tableName`: nome físico. Se for `null`/omitido, pode ser derivado em snake_case;
  string vazia não equivale necessariamente a omissão.
- `comment`: descrição usada em partes da geração.
- `classExtends`: existe no modelo, mas não é aplicado consistentemente.
- `generateDefaultControllers`: padrão efetivo `true`.
- `controllerAbstract`: padrão efetivo `false`.
- `serviceAbstract`: padrão `false`; controla o bean do Service Java.
- `onlyDTO`: no Java, impede Entity, Converter, Repository, Service e Controller,
  mas o DTO continua gerado. A entidade ainda participa do SQL atual.
- `entityFields`: obrigatório, não vazio e com exatamente uma chave segundo o
  validador compartilhado.

Os aliases legados são aceitos com warning. Se nome novo e legado coexistirem, o
novo prevalece.

## Contrato de campo e tipos

Cada campo deve declarar `comment`, `fieldName`, `fieldProperties` e `metadata`.
`list: true` envolve o tipo em `List<T>`.

| `fieldType` | Java | PostgreSQL gerado |
| --- | --- | --- |
| `uuid` | `UUID` | `uuid` |
| `string`, `password` | `String` | `varchar` |
| `datetime` | `LocalDateTime` | `timestamp` |
| `date` | `LocalDate` | `date` |
| `int` | `Integer` | `integer`, ou `serial` se PK |
| `integer` | `int` | `integer`, ou `serial` se PK |
| `long` | `Long` | `integer` no SQL atual |
| `decimal`, `double` | `Double` | `numeric` |
| `boolean` | `boolean` | `boolean` |
| `byte` | `byte` | sem contrato SQL portátil completo |
| `byte[]` | `byte[]` | sem contrato SQL portátil completo |
| `inputStream` | `InputStream` | sem contrato SQL portátil completo |
| `map` | `Map<String, Object>` | sem contrato SQL portátil completo |

Um tipo que não seja primitivo nem enum é tratado como entidade e vira
`<Tipo>Entity` ou `<Tipo>DTO`. O nome precisa corresponder a um `entityName`.
Enums são reconhecidos sem diferenciar maiúsculas/minúsculas.

`fieldProperties.required` e `valueDefault` fazem parte do modelo, mas não controlam
todo o código gerado de forma uniforme. `metadata.nullable` é a fonte mais relevante
para anotações e nulabilidade; evite combinações contraditórias.

## Pipeline e saída gerada

`GenerateJava.generateSource` limpa a raiz `target/generated-sources/gonthera` e recria:

```text
target/generated-sources/gonthera/<mainPackage convertido em caminho>/
├── authorization/
├── common/
├── controllers/
├── converters/
├── dtos/
├── endpoints/
├── entities/
├── enums/
├── messaging/                 # quando RabbitMQ configurado
├── repositories/
└── services/
```

Também sobrescreve em `src/main/resources/`:

```text
properties.json
resources.json
postgree.sql
```

Ordem conceitual da geração: entidades, DTOs, converters, services, contrato CRUD,
controllers, configuração REST, autorização, RabbitMQ, filtros, endpoints, enums,
repositories, envelopes comuns, metadata, permissões e SQL.

## Arquitetura CRUD Java

```text
HTTP
  -> <Entity>Controller
  -> <Entity>Service
  -> <Entity>Repository (Spring Data JPA)
  -> PostgreSQL
```

Rotas relativas a `/<entityName>`:

| Método | Caminho | Operação |
| --- | --- | --- |
| POST | `/customer` | `save(dto)` |
| PUT | `/customer/{id}` | `update(dto, id)` |
| DELETE | `/customer/{id}` | `delete(id)` |
| GET | `/customer/{id}` | `get(id)` |
| GET | `/customer` | `getAll(params)` |

O Controller implementa `CrudController<T, ID>`, traduz query params em
`RequestData` e delega. Não deve ganhar acesso direto ao repository.

O Service gerado possui dependências `protected` para repository, DTO converter e
`SpecificationFilter`, permitindo especialização. Comportamento efetivo:

- `save`: DTO -> nova Entity -> `repository.save` -> DTO;
- `update`: DTO -> Entity, força a chave recebida na URL com setter e salva;
- `delete`: `repository.deleteById`;
- `get`: `repository.getReferenceById` e conversão;
- `getAll`: normaliza paginação, cria `PageRequest`, aplica sort e Specification,
  consulta e monta `ResponseData`.

Transações:

- `save`, `update` e `delete`: `@Transactional`;
- `get` e `getAll`: `@Transactional(readOnly = true)`.

Não mova transações para Controller. Uma subclasse que mudar a semântica deve
declarar explicitamente a anotação adequada no método sobrescrito.

## Controllers e Services abstratos

Combinações seguras:

| Objetivo | Controllers | `controllerAbstract` | `serviceAbstract` | Responsabilidade do consumidor |
| --- | ---: | ---: | ---: | --- |
| CRUD pronto | `true` | `false` | `false` | nenhuma |
| customizar negócio | `true` | `false` | `true` | um bean `@Service` concreto |
| customizar HTTP | `true` | `true` | `false` | um `@RestController` concreto |
| customizar ambos | `true` | `true` | `true` | controller e service concretos |
| possuir todas as rotas | `false` | ignorado | qualquer | controller manual; service concreto se abstrato |

Com `serviceAbstract: false`, o Service recebe `@Service`. Não crie outro bean do
mesmo tipo sem resolver ambiguidade. Com `serviceAbstract: true`, a base mantém
implementação funcional, mas não recebe `@Service`:

```java
@Service
public class CustomerApplicationService extends CustomerService {
    @Override
    @Transactional
    public CustomerDTO save(CustomerDTO dto) {
        return super.save(dto);
    }
}
```

Com `controllerAbstract: true`, a base ainda recebe as anotações/mapeamentos, mas o
consumidor precisa fornecer um subtipo concreto descoberto pelo Spring:

```java
@RestController
public class CustomerApplicationController extends CustomerController {
}
```

Evite dois controllers concretos com as mesmas rotas e evite múltiplos Services
atribuíveis ao mesmo tipo sem `@Primary`/`@Qualifier`.

## Paginação, ordenação e resposta

O GET de coleção aceita `size`, `offset`, `filter`, `order` e `displayFields`.

- `size`: padrão 20; não há limite superior gerado.
- `offset`: padrão 1 na requisição; valores não positivos viram 1; o Service reduz
  um e devolve índice zero na resposta.
- `order`: `campo,asc` ou `campo,desc`; sem direção assume `asc`.
- `order` aceita caminhos pontuados e valida forma/direção; erro vira HTTP 400 por
  `ServiceException`.
- `ResponseData`: `{size, offset, total, contents}`.

## Dialeto de filtros Java

O parâmetro `filter` não é JPQL nem SQL. Operadores implementados:

- `field eq value`;
- `relation.field eq value`;
- `field isNull` e `field notNull`;
- datas com `gte`/`ge` e `lte`/`le`;
- condições ligadas por `and` ou `or`.

Semântica:

- `eq` em `String` faz `lower(field) like '%value%'`;
- UUID usa igualdade exata e exige UUID válido;
- enum aceita nome e também ordinal dentro do intervalo;
- intervalo funciona apenas para `LocalDate` e `LocalDateTime` em formato ISO;
- caminhos relacionados criam joins JPA, normalmente INNER JOIN;
- filtro inválido gera `ServiceException(BAD_REQUEST, "Invalid filter: ...")`.

Limitações que uma IA não deve esconder:

- igualdade numérica/booleana não é convertida com segurança;
- não existem `ne`, `gt`, `lt`, `in`, `between`, `not` ou escape;
- não misture `and` e `or`; a precedência não é um contrato confiável;
- parênteses não formam uma gramática robusta;
- valores contendo ` and ` ou ` or ` são divididos;
- nomes são atributos Java/JSON em lowerCamelCase, não colunas SQL.

## Relacionamentos JPA

Formato:

```json
"relationShips": {
  "fetchType": "LAZY",
  "relationShip": "ManyToOne",
  "mappedBy": "",
  "bidirectional": false,
  "reference": true
}
```

- Proprietário: `bidirectional: false`; gera `@JoinColumn`, coluna e FK.
- Inverso: `bidirectional: true`; usa `mappedBy`, cascade ALL e orphanRemoval no
  padrão atual, sem nova coluna SQL.
- `OneToOne`: campo escalar; proprietário guarda FK.
- `ManyToOne`: escalar proprietário; par típico com `OneToMany` inverso em lista.
- `ManyToMany`: coleções e tabela de junção SQL.
- Em autorrelação, declare primeiro o proprietário e depois o inverso.

Exemplo:

```json
{
  "fieldName": "parentCode",
  "list": false,
  "fieldProperties": { "fieldType": "costCenter" },
  "metadata": { "nullable": true, "key": false },
  "relationShips": {
    "fetchType": "LAZY",
    "relationShip": "ManyToOne",
    "bidirectional": false,
    "reference": true
  }
}
```

O inverso `children` usa `list: true`, `OneToMany`, `bidirectional: true` e
`mappedBy: "parentCode"`. O converter reamarra filhos com o pai ao converter DTO
para Entity. `reference: true` ajuda a impedir recursão na conversão para DTO.

## DTOs, projeção e ciclos

Cada entidade gera DTO e converter. O converter executa Entity <-> DTO e interpreta
`displayFields`. `*` solicita todos os campos; caminhos podem limitar relações.
Relacionamentos bidirecionais exigem supressão do lado de referência para evitar
`pai -> filhos -> pai` indefinidamente. Não substitua essa regra por serialização
direta das Entities.

## Endpoints customizados

Endpoint mínimo:

```json
{
  "comment": "Consulta cliente",
  "methodName": "findCustomer",
  "httpMethod": "GET",
  "grouper": "customer",
  "metadata": {
    "anonymous": false,
    "input": [],
    "output": []
  }
}
```

- Apenas `GET` e `POST` são validados.
- `metadata.input` e `metadata.output` são arrays obrigatórios.
- Java gera uma interface por endpoint e não usa `grouper` para agrupamento.
- `requestdata`/`responsedata` ativam envelopes genéricos.
- `metadata.anonymous: true` aplica a anotação `@Anonymous` gerada localmente.
- O gerador define o contrato; a regra de negócio continua no consumidor.

## Autorização Java

O código não depende mais de `com.potatotech.authorization`. Ele gera:

```text
authorization/
├── exception/ServiceException.java
├── permission/PermissionType.java
├── permission/Permissions.java
├── security/Authenticate.java
├── security/Roles.java
├── security/UserSupplier.java
├── stereotype/Anonymous.java
├── stereotype/SecureResource.java
├── tenant/TenantConfiguration.java
└── tenant/TenantContext.java
```

`authorization.json`:

```json
{
  "authenticateAbstract": false,
  "tenantConfigurationAbstract": false
}
```

Flags `true` removem o stereotype Spring e exigem um bean concreto em `src/main/java`.
`Authenticate` expõe hooks protegidos para segredo, bearer token, claims, usuário e
token. Configure `SECRET_JWT` e JJWT 0.11.5. Sempre limpe `TenantContext` em `finally`.

## RabbitMQ Java

`messaging.RabbitMq.pub/sub` gera código somente quando há canais. Publishers
exigem `routingKey`; subscribers podem omiti-la. O consumidor fornece uma classe:

```java
@Configuration
@RabbitExchange("customer.events")
public class AppRabbitConfig extends RabbitConfig {}
```

Publishers usam Spring `RabbitTemplate`. Subscribers gerados são abstratos, possuem
`@RabbitListener` e exigem implementação de `onMessage(String)` como bean Spring.
A conexão é configuração do Spring Boot do consumidor.

## SQL, resources e efeitos destrutivos

- O SQL é PostgreSQL completo, não migration incremental.
- `postgree.sql` é recriado; revise antes de aplicar.
- `resources.json` contém permissões CRUD e de endpoints.
- `properties.json` é a cópia normalizada usada como metadata Java.
- A raiz gerada do Gonthera é removida antes da geração; o Maven respeita `build/directory`.
- O Maven registra a raiz com `addCompileSourceRoot`. `clean` remove esses fontes.
- Fontes legados em `src/main/java/..._gen` são preservados com aviso; o consumidor deve revisá-los e removê-los e atualizar imports de `<mainPackage>_gen` para `<mainPackage>` para evitar tipos obsoletos e beans duplicados.
- Não execute geração em diretório errado e não armazene código manual na saída.

## Dependências esperadas no consumidor

- Java 17 normalmente para Spring 6/Jakarta, embora a CLI compile em Java 11;
- Spring Web, Data JPA e Transactions;
- Jakarta Persistence;
- Lombok;
- PostgreSQL/Npgsql equivalente Java conforme aplicação;
- JJWT 0.11.5 para autenticação;
- Spring AMQP quando RabbitMQ estiver configurado.

## Defeitos e limites conhecidos

- `classExtends` não é aplicado de forma efetiva.
- `required` e `valueDefault` não governam todo o código gerado.
- O SQL assume metadata e relações válidas e pode falhar em contratos incompletos.
- `long` usa `integer` no SQL atual.
- Tipos binários/map não possuem portabilidade SQL completa.
- O parser de filtro é limitado e não é uma linguagem de consulta geral.
- `getReferenceById` pode postergar detecção de ausência até acesso/conversão.
- A validação melhora erros estruturais, mas não compila o projeto consumidor.

## Checklist para uma IA alterar o Java

1. Ler configuração, entidades relacionadas e consumidores que estendem classes geradas.
2. Identificar se a mudança pertence a Controller, Service, Repository, Converter,
   template comum, autorização, RabbitMQ ou SQL.
3. Alterar o template `.mxsd`, não a saída gerada.
4. Preservar aliases legados e emitir warnings quando aplicável.
5. Não mudar Java ao implementar comportamento exclusivo de Node ou .NET.
6. Validar antes de limpar a saída.
7. Gerar em fixture temporária e inspecionar imports, package e nomes.
8. Compilar a CLI e, quando possível, o serviço consumidor.
9. Testar relações bidirecionais e autorrelações em ambos os sentidos.
10. Atualizar handoff, changelog e documentação pública se o contrato mudar.

## Critério de conclusão

Uma mudança Java só está concluída quando o JSON válido gera fontes no pacote
correto, o projeto consumidor compila, o contrato HTTP continua explícito, código
manual sobrevive à regeneração e limitações/rupturas foram documentadas.
