# Gonthera CLI 2.1.4 — Referência técnica para IA — .NET

## Finalidade e status

Este documento fornece contexto operacional para uma IA analisar ou alterar a
geração .NET/C# do Gonthera CLI 2.1.4. Ele descreve o comportamento efetivo dos
geradores e templates, inclusive limitações que não devem ser confundidas com o
comportamento mais maduro de Java ou Node.

Fontes de verdade, em ordem:

1. `src/main/java/com/gonthera/cli/service/dotNet`;
2. `src/main/resources/xsd/dotnet`;
3. validação/modelos compartilhados;
4. este documento;
5. handoff geral, README e portal.

A versão 2.1.4 não alterou o gerador .NET; MongoDB foi adicionado exclusivamente ao
Node. O .NET atual usa Entity Framework Core com PostgreSQL/Npgsql e integrações
específicas da infraestrutura histórica DataOn/PotatoTech.

## Invariantes que uma IA deve preservar

- A saída fica em `<mainPackage convertido em caminho>_gen` e é descartável.
- A pasta física foi organizada, mas o namespace C# ainda é compartilhado na forma
  `<mainPackage>.<mainPackage>_Gen`; não derive namespaces dos diretórios.
- O CRUD .NET não possui camada Service gerada.
- Controller injeta `I<Entity>Repository`, converte DTO e coordena persistência.
- `serviceAbstract` não tem efeito no .NET.
- `controllerAbstract` gera base abstrata com actions `virtual`.
- `onlyDTO` não é respeitado consistentemente pelo fluxo .NET atual.
- O banco continua PostgreSQL; `database.provider` só pode ser usado no Node.
- A pasta `static/` deve existir antes da geração.
- `postgree.sql` mantém essa grafia.
- Não atribua filtros, paginação ou erros do Node ao .NET.

## Execução e resolução da configuração

Execute o JAR ou EXE na raiz do consumidor, porque o caminho vem de
`System.getProperty("user.dir")`.

```powershell
.\gonthera-cli.exe --validate
.\gonthera-cli.exe
```

Alternativa:

```bash
java -jar gonthera-cli-2.1.4.jar --validate
java -jar gonthera-cli-2.1.4.jar
```

Ordem de resolução:

1. `.gonthera/project.json` e arquivos modulares;
2. `project.json` na raiz;
3. `properties.json` legado.

Se `.gonthera` existir, seu `project.json` é obrigatório. A validação isolada exige
essa pasta e não gera/apaga arquivos.

## Configuração modular

```text
.gonthera/
├── project.json
├── entities.json
├── endpoints.json
├── enums.json
└── messaging.json
```

`.gonthera/project.json`:

```json
{
  "mainPackage": "CustomerService",
  "projectName": "customer-service",
  "language": "DOTNET"
}
```

Os arquivos de coleções contêm arrays diretamente. Arquivos separados sobrescrevem
apenas suas seções. Não adicione `database`: o validador rejeita esse objeto quando
a linguagem não é Node. `authorization.json` configura classes exclusivas do Java.

No formato único:

```json
{
  "mainPackage": "CustomerService",
  "projectName": "customer-service",
  "language": "DOTNET",
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

Semântica relevante:

- `entityName`: base de Entity, DTO, Repository e Controller.
- `tableName`: tabela PostgreSQL; omissão pode derivar snake_case.
- `generateDefaultControllers`: padrão `true`.
- `controllerAbstract`: padrão `false`; apenas afeta o Controller.
- `serviceAbstract`: Java-only; não gera Service C#.
- `onlyDTO`: existe no modelo, mas o fluxo .NET atual ainda gera outros artefatos;
  não prometa comportamento Java neste alvo.
- `classExtends`: não é aplicado de forma confiável.

Aliases `generateDefaultHandlers` e `handlerAbstract` são aceitos com warning; nomes
novos prevalecem. O par antigo `Handler/HandlerImpl` não é mais gerado.

## Campos e tipos

| `fieldType` | Tipo C# observado | PostgreSQL |
| --- | --- | --- |
| `uuid` | `Guid?` | `uuid` |
| `string`, `password` | `string` | `varchar` |
| `datetime`, `date` | `DateTime` | `timestamp` / `date` |
| `int` | `int?` | `integer`, PK pode virar `serial` |
| `integer` | `int` | `integer` |
| `long` | `Long` no mapper compartilhado | `integer` no SQL atual |
| `decimal`, `double` | `Double` | `numeric` |
| `boolean` | `bool` | `boolean` |

Tipos `byte`, `byte[]`, `inputStream` e `map` não possuem suporte .NET confiável.
Um tipo não primitivo/não enum é tratado como outra Entity/DTO. Enums são
reconhecidos sem diferenciar caixa no mapper, mas o parser de filtro usa
`Enum.Parse` com comportamento próprio.

O mapeamento nullable possui condições legadas frágeis; sempre compile uma fixture
com campos opcionais/obrigatórios após alterar tipos.

## Pipeline e estrutura gerada

`GenerateDotNet.generateSource` remove recursivamente o diretório `_gen` e gera:

```text
<mainPackage convertido em caminho>_gen/
├── Common/
│   ├── AddScoped.cs
│   ├── DynamicFilter.cs
│   ├── ExpressionExtensions.cs
│   ├── RequestData.cs
│   └── ResponseData.cs
├── Controllers/
├── Converters/
├── Data/
│   ├── CustomDbContext.cs
│   └── DynamicSchemaModelCacheKeyFactory.cs
├── Dtos/
├── Endpoints/
├── Entities/
├── Enums/
├── Messaging/                    # opcional
└── Repositories/
```

Em `static/` sobrescreve:

```text
properties.json
resources.json
postgree.sql
```

O gerador cria subdiretórios sob `_gen`, mas não cria `static/`. Garanta sua
existência antes da execução.

## Namespace atual

As pastas são apenas organização física. Templates usam um namespace raiz comum
equivalente a:

```csharp
namespace CustomerService.CustomerService_Gen
```

Não adicione `.Controllers`, `.Repositories`, `.Dtos` etc. em imports do consumidor
apenas porque o arquivo está nessas pastas. Uma migração de namespaces exigiria
alterar todos os templates, DI, imports, testes e consumidores.

## Arquitetura CRUD .NET

```text
HTTP
  -> <Entity>Controller
     -> DTOConverter
     -> I<Entity>Repository
        -> <Entity>Repository
           -> ApiDbContext / EF Core
```

Não há Service gerado. A ação Controller realiza conversão e chama repository.

Rotas relativas a `/<entity>`:

| Método | Caminho | Ação |
| --- | --- | --- |
| GET | `/customer` | `GetAll(RequestData)` |
| GET | `/customer/{id}` | `Get(id, displayFields)` |
| POST | `/customer` | `Save(CustomerDTO)` |
| PUT | `/customer/{id}` | `Update(id, CustomerDTO)` |
| DELETE | `/customer/{id}` | `Delete(id)` |
| POST | `/customer/import` | importa lista sequencialmente |

Todas as actions são `virtual`. O Controller atual:

- converte DTO -> Entity antes de Add/Update;
- converte Entity -> DTO na resposta;
- DELETE retorna HTTP 200 com `{message: "deleted", id}`;
- import chama Add para cada item e retorna texto com a contagem;
- não implementa uma camada de transação/use case separada.

## Controllers concretos e abstratos

- `generateDefaultControllers: false`: não gera Controller CRUD.
- `true` + `controllerAbstract: false`: Controller concreto descoberto pelo ASP.NET.
- `true` + `controllerAbstract: true`: base abstrata, actions funcionais e virtuais;
  consumidor cria subtipo concreto fora de `_gen`.

```csharp
public class CustomerApplicationController : CustomerController
{
    public CustomerApplicationController(ICustomerRepository repository)
        : base(repository)
    {
    }

    public override ActionResult<CustomerDTO> Save(CustomerDTO input)
    {
        return base.Save(input);
    }
}
```

O consumidor precisa garantir descoberta MVC e evitar um segundo Controller
concreto com as mesmas rotas. Para possuir toda a superfície HTTP, prefira
`generateDefaultControllers: false`.

## Repository e persistência

São gerados `IBaseRepository`, `I<Entity>Repository` e implementação concreta.
`Add`, `Update`, `Delete`, `FindById` e `GetAll` são virtuais na implementação.

Comportamento atual importante:

- `Add` usa `Attach(obj)` e `SaveChanges()`.
- `Update` chama `_context.Update(obj)` e `SaveChanges()`.
- `Delete` faz `Find`, `Remove` e `SaveChanges()`.
- `FindById` consulta `SingleOrDefault(p => p.Id == id)`.
- `GetAll` usa `_context.<Entity>Context`, aplica DynamicFilter, conta, ordena por
  `p.Id`, faz `Skip/Take` e converte lista.

Há uma suposição fixa de propriedade `Id` em partes do repository. Embora o tipo da
chave seja configurável no Controller/interface, chaves com outro nome podem gerar
código inconsistente. Não prometa a flexibilidade de chave real existente no Node.

`catch (Exception ex) { throw ex; }` perde parte da semântica de stack trace e é um
legado do template; uma correção precisa considerar compatibilidade e testes.

## Paginação e ResponseData

`RequestData` possui defaults:

```csharp
size = 10;
offset = 1;
filter = "";
order = "";
displayFields = "";
```

No repository atual:

- offset não positivo vira 1;
- `Skip((offset - 1) * size)` usa paginação one-based;
- `total` é número de registros filtrados;
- `offset` devolvido continua one-based;
- `size` da resposta recebe `totalPages`, não o tamanho solicitado.

Esse contrato difere de Java/Node e pode ser um defeito histórico. Não “corrija” em
documentação fingindo paridade; qualquer mudança exige decisão de compatibilidade.
Os campos `order` e `displayFields` existem em `RequestData`, mas o repository atual
não os aplica de forma efetiva: ordena fixamente por `Id` e `FindById` ignora
`displayFields`.

## DynamicFilter .NET

O dialeto implementa apenas `eq` e escolhe a primeira espécie de operador lógico
encontrada (`and` ou `or`). Não existe parser equivalente ao Java/Node.

Semântica:

- string: `ToLower().Contains(value.ToLower())`;
- Guid válido: igualdade;
- enum/outros: conversão/`Enum.Parse` e igualdade;
- relação escalar: caminho pontuado, seguindo propriedades C#;
- coleção: marcador `*`, por exemplo `children*.Description eq matriz`;
- coleção usa `Enumerable.Any`.

Limitações:

- sem `isNull`, `notNull`, ranges de data, `ne`, `in` ou parênteses;
- não há precedência lógica robusta;
- apenas uma família lógica por expressão;
- nomes devem corresponder à forma das propriedades C# geradas;
- null em string/caminho pode causar falhas;
- conversões numéricas/nullable e enums são frágeis;
- expressão sem condição válida pode acessar lista vazia.

O frontend deve selecionar o dialeto pela linguagem; filtros Java/Node não são
portáveis por garantia.

## Relacionamentos EF Core

O contrato JSON continua compartilhado:

```json
"relationShips": {
  "fetchType": "LAZY",
  "relationShip": "ManyToOne",
  "mappedBy": "",
  "bidirectional": false,
  "reference": true
}
```

- `bidirectional: false`: proprietário/FK.
- `bidirectional: true`: inverso; `mappedBy` aponta ao campo proprietário.
- `list: true`: coleções OneToMany/ManyToMany.
- Relações podem gerar tipos Entity e DTO e conversão correspondente.

Java/C# possuem geração de relações mais antiga e permissiva que a validação
específica do Node. Sempre gere e compile fixtures com OneToOne, OneToMany,
ManyToOne, ManyToMany e autorrelações antes de aceitar mudanças.

Lazy loading depende de `UseLazyLoadingProxies()` no DbContext atual. Isso influencia
serialização, ciclos e consultas; use DTOConverter, não retorne Entity diretamente.

## DTOConverter

O Controller usa métodos estáticos `ToEntity` e `ToDTO`. Customizações devem ficar
fora de `_gen` ou ser incorporadas ao template. Não edite converter gerado, pois o
diretório será apagado. Avalie cuidadosamente nullable, coleções e ciclos em relações.

## DbContext e infraestrutura específica

O template `CustomDbContext` é abstrato e atualmente importa namespaces fixos:

```csharp
using DataOnBackend.Config.Database;
using DataOnBackend.Config.DatabaseMigration;
```

Também usa:

- `UseNpgsql(DBMigrator._connectionString)`;
- `UseLazyLoadingProxies()`;
- `DynamicSchemaModelCacheKeyFactory`;
- schema por `AsyncLocal<string>`;
- `ApiDbContext`, esperado na infraestrutura consumidora.

Esses nomes não são derivados de `mainPackage`. Serviços fora dessa infraestrutura
podem precisar adaptar template ou fornecer tipos compatíveis. Isso é um defeito
conhecido, não uma dependência universal do .NET.

`AddScoped.AddScopedGenerate(builder)` registra cada
`I<Entity>Repository -> <Entity>Repository`. O consumidor chama esse helper no
`Program.cs`, além de registrar DbContext e dependências externas.

## Endpoints customizados

Contrato:

```json
{
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

- `metadata.input/output` são obrigatórios, mesmo vazios.
- `httpMethod` validado: GET ou POST.
- .NET usa `grouper` para reunir métodos em uma classe `*Primitive`.
- Use `""` quando não houver grupo.
- `anonymous: true` aplica `[AllowAnonymous]`.
- `requestdata`/`responsedata` selecionam envelopes genéricos.
- A geração possui verificações históricas inconsistentes entre método e HTTP method;
  compile endpoints variados.

## RabbitMQ .NET

Gerado somente quando `messaging.RabbitMq` possui canal.

```text
Messaging/
├── RabbitExchangeAttribute.cs
├── RabbitConfig.cs
├── AddRabbitMessaging.cs
├── Pub/RabbitPublisher.cs
├── Pub/*Pub.cs
└── Sub/*Sub.cs
```

O consumidor fornece configuração concreta:

```csharp
[RabbitExchange("customer.events")]
public class AppRabbitConfig : RabbitConfig
{
    public AppRabbitConfig(IConfiguration configuration) : base(configuration) {}
}
```

Registro:

```csharp
builder.Services.AddSingleton<RabbitConfig, AppRabbitConfig>();
AddRabbitMessaging.AddRabbitMessagingGenerate(builder);
builder.Services.AddHostedService<CustomerImportedListener>();
```

Publishers usam RabbitMQ.Client. Subscribers são `BackgroundService` abstratos,
chamam `OnMessage(string)`, fazem ack em sucesso e nack no erro conforme template.
`routingKey` é obrigatória para pub e opcional para binding de sub. Exchange não
fica em `project.json`; vem do atributo no consumidor.

Configuração esperada em `appsettings.json`: host, porta, usuário, senha e virtual
host sob `RabbitMQ`.

## SQL, metadata e permissões

- `postgree.sql` é PostgreSQL completo e não migration incremental.
- `properties.json` em `static/` é a configuração normalizada.
- `resources.json` lista CRUD e endpoints/permissões.
- A chave de permissões no JSON é `premissions`.
- A pasta `static/` não é criada automaticamente.

## Dependências esperadas

- ASP.NET Core MVC e Authorization;
- Entity Framework Core;
- provider Npgsql/PostgreSQL;
- lazy-loading proxies se mantido o DbContext atual;
- infraestrutura `DataOnBackend.Config.*` ou adaptação explícita;
- RabbitMQ.Client e abstrações Microsoft.Extensions quando RabbitMQ configurado.

## Defeitos e limites conhecidos

- Sem MongoDB no alvo .NET.
- Sem camada Service gerada.
- `serviceAbstract` é ignorado.
- `onlyDTO` não é respeitado corretamente.
- namespaces não acompanham as pastas físicas.
- DbContext possui imports DataOnBackend fixos.
- repository supõe chave `Id` em vários pontos.
- `order` e `displayFields` não são implementados como no Node/Java.
- `ResponseData.size` recebe total de páginas no repository atual.
- nullable e endpoint generation contêm condições frágeis.
- filtros são muito mais limitados que Java/Node.
- a validação não compila o consumidor.

## Checklist para uma IA alterar o .NET

1. Não assumir paridade com Java ou Node; ler o template C# correspondente.
2. Manter namespace raiz atual, salvo migração explicitamente solicitada.
3. Alterar `.mxsd`, nunca arquivos `_gen` gerados.
4. Criar `static/` em fixtures antes de gerar.
5. Compilar com entidades simples, nullable, enums e todas as relações afetadas.
6. Verificar `Id` fixo se a mudança envolver chaves.
7. Verificar DI de repositories e Controller concreto/abstrato.
8. Não introduzir Service apenas por analogia com Java sem redesenhar DI e templates.
9. Não documentar filtros/order/projeção que o template não implementa.
10. Testar endpoints agrupados e `[AllowAnonymous]`.
11. Testar RabbitMQ e hosted services se mensageria mudar.
12. Atualizar handoff, changelog e portal ao alterar contrato público.

## Critério de conclusão

Uma mudança .NET está concluída quando a CLI gera a árvore esperada, o consumidor
C# compila com seus namespaces/infraestrutura, DI resolve repositories/controllers,
DTOs impedem ciclos indevidos, os contratos HTTP reais estão documentados e nenhum
código manual foi colocado em `_gen`.
