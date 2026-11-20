# Publicação no Maven Central

O projeto usa o Central Publisher Portal da Sonatype, com as coordenadas
`br.com.smartverse:gonthera-cli:2.1.4` e licença MIT. O namespace verificado deve
ser `br.com.smartverse` (sem ponto no início). Os pacotes internos agora são
`br.com.smartverse.gonthera.cli`.

## Preparar a máquina

Use Maven 3.8+ e JDK 11 ou superior. A compilação principal mantém o alvo
Java 11; as aplicações Spring geradas têm seus próprios requisitos de Java.

No Central Portal, confira se o namespace está verificado e gere um **User Token**
na conta. Use o usuário e a senha gerados pelo token, não a senha do login.

Acrescente o servidor abaixo ao seu `~/.m2/settings.xml`, preservando os outros
servidores e configurações existentes:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <servers>
    <server>
      <id>central</id>
      <username>${env.CENTRAL_TOKEN_USERNAME}</username>
      <password>${env.CENTRAL_TOKEN_PASSWORD}</password>
    </server>
  </servers>
</settings>
```

Defina `CENTRAL_TOKEN_USERNAME` e `CENTRAL_TOKEN_PASSWORD` no ambiente local ou
nos secrets do CI. Não coloque tokens no POM nem versione o seu settings pessoal.
O `publishingServerId` do plugin corresponde ao servidor `central` acima.

Instale GnuPG e crie uma chave de assinatura, caso ainda não tenha uma:

```bash
gpg --version
gpg --full-generate-key
gpg --list-secret-keys --keyid-format LONG
```

Escolha uma chave com capacidade de assinatura e proteja a chave privada com
senha. Copie o fingerprint completo e disponibilize somente a chave pública:

```bash
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys SEU_FINGERPRINT
```

Não envie nem versione a chave privada. Em um terminal Linux, se o pinentry
precisar identificar o terminal, execute `export GPG_TTY=$(tty)`.

## Validar e publicar

Antes de publicar, confirme os dados de autor, licença e SCM no `pom.xml`.
O perfil `release` gera os JARs de fontes e Javadoc, assina os artefatos com GPG
e configura o plugin oficial do Central. Não usa `distributionManagement` do
MyMavenRepo nem os endpoints antigos do OSSRH.

Teste o build normal:

```bash
mvn clean verify
```

Teste o empacotamento de release com assinatura, sem upload:

```bash
mvn -Prelease clean verify -Dgpg.keyname=SEU_FINGERPRINT
```

Em uma verificação local sem chave GPG, é possível testar o empacotamento com
`mvn -Prelease clean verify -Dgpg.skip=true`. Esse resultado não pode ser publicado
no Central sem as assinaturas.

Quando estiver pronto, envie ao Portal:

```bash
mvn -Prelease clean deploy -Dgpg.keyname=SEU_FINGERPRINT
```

O GPG solicitará a senha da chave. O Maven enviará o bundle e aguardará a validação.
Com `autoPublish=false`, finalize em
[Publishing Deployments](https://central.sonatype.com/publishing/deployments):
confira os arquivos e clique em **Publish** depois de o deployment ser validado.
Se a validação falhar, consulte os erros mostrados pelo Portal.

Depois da publicação, aguarde a disponibilização no Maven Central. Uma versão
publicada é imutável: para mudar o conteúdo, incremente `<version>` no POM.
A versão `2.1.4` foi mantida porque as novas coordenadas têm outro `groupId`;
confirme que ela ainda não existe nessas coordenadas antes do primeiro deploy.
Esse fluxo é de release; versões `-SNAPSHOT` precisam de configuração própria.

## Atualizar os consumidores

Depois que a versão estiver disponível no Central, troque o `groupId` do plugin:

```xml
<plugin>
  <groupId>br.com.smartverse</groupId>
  <artifactId>gonthera-cli</artifactId>
  <version>2.1.4</version>
</plugin>
```

Remova os blocos `repositories` e `pluginRepositories` do MyMavenRepo que eram
usados exclusivamente para o Gonthera CLI. Com o plugin declarado no POM,
os comandos continuam iguais:

```bash
mvn gonthera-cli:validate
mvn gonthera-cli:generate-sources
```

Também é possível executar usando as coordenadas completas:

```bash
mvn br.com.smartverse:gonthera-cli:2.1.4:validate
```

O `mainPackage` dos projetos consumidores continua sendo o pacote da aplicação
consumidora. Quem referenciava classes internas `com.gonthera.cli` diretamente
precisa atualizar os imports para `br.com.smartverse.gonthera.cli`.

## Referências

- [Plugin oficial de publicação](https://central.sonatype.org/publish/publish-portal-maven/)
- [Requisitos de publicação](https://central.sonatype.org/publish/requirements/)
- [Assinaturas e chave pública GPG](https://central.sonatype.org/publish/requirements/gpg/)
- [Licença MIT](https://opensource.org/license/mit)
