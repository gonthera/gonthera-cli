package br.com.smartverse.gonthera.cli.service.java;

import com.google.gson.Gson;
import br.com.smartverse.gonthera.cli.model.Properties;
import br.com.smartverse.gonthera.cli.service.common.Common;
import br.com.smartverse.gonthera.cli.service.common.GenerateCommon;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;


import static br.com.smartverse.gonthera.cli.service.common.Common.loadPath;
import static br.com.smartverse.gonthera.cli.service.java.GenerateDTO.generateDTO;
import static br.com.smartverse.gonthera.cli.service.java.GenerateDTOConverter.generateDTOConverter;
import static br.com.smartverse.gonthera.cli.service.java.GenerateEndpoint.generateEndpoint;
import static br.com.smartverse.gonthera.cli.service.java.GenerateEntity.generateEntity;
import static br.com.smartverse.gonthera.cli.service.java.GenerateEnum.generateEnum;
import static br.com.smartverse.gonthera.cli.service.java.GenerateController.generateControllers;
import static br.com.smartverse.gonthera.cli.service.java.GenerateAuthorization.generateAuthorization;
import static br.com.smartverse.gonthera.cli.service.java.GenerateMessaging.generateMessaging;
import static br.com.smartverse.gonthera.cli.service.common.GenerateResources.generateResources;
import static br.com.smartverse.gonthera.cli.service.java.GenerateRepositories.generateRepositories;
import static br.com.smartverse.gonthera.cli.service.java.GenerateService.generateServices;

public class GenerateJava {


    public static void generateSource(Properties prop) {
        Path projectDirectory = Paths.get(loadPath());
        generateSource(prop, projectDirectory, projectDirectory.resolve("target/generated-sources/gonthera"));
    }

    public static void generateSource(Properties prop, Path projectDirectory, Path sourcesDirectory) {
        Path packagePath = dropAndCreateDir(prop.getMainPackage(), projectDirectory, sourcesDirectory);

        // gera a classe das entidaeds
        generateEntity(prop.getEntities(),prop.getMainPackage(),packagePath.resolve("entities"));
        // Gera as classes DTO
        generateDTO(prop.getEntities(),prop.getMainPackage(),packagePath.resolve("dtos"));
        // Gera as classes DTO
        generateDTOConverter(prop.getEntities(),prop.getMainPackage(),packagePath.resolve("converters"));
        // Gera a camada de serviços
        generateServices(prop.getEntities(), prop.getMainPackage(), packagePath.resolve("services"));
        // Gera o contrato CRUD dos controllers
        GenerateCommon.generateFileCommon(prop.getMainPackage(),packagePath.resolve("common"), "crudcontroller", "CrudController");
        // Gera Controllers de CRUD
        generateControllers(prop.getEntities(),prop.getMainPackage(),packagePath.resolve("controllers"));
        // Gera o RestConfig
        GenerateCommon.generateFileCommon(prop.getMainPackage(),packagePath.resolve("common"), "restconfig", "RestConfig");
        // Gera os componentes de autorização sem dependência de biblioteca externa
        generateAuthorization(prop.getMainPackage(), packagePath, prop.getAuthorization());
        // Gera abstrações de mensageria RabbitMQ
        generateMessaging(prop.getMessaging() == null ? null : prop.getMessaging().getRabbitMq(), prop.getMainPackage(), packagePath);
        // Gera especificação dos filtros
        GenerateCommon.generateFileCommon(prop.getMainPackage(),packagePath.resolve("common"), "especificationfilter", "SpecificationFilter");
        // Gera os endpoints
        generateEndpoint(prop.getEndpoints(),prop.getMainPackage(),packagePath.resolve("endpoints"));
        // Gera as classes Enumeration
        generateEnum(prop.getEnums(),prop.getMainPackage(),packagePath.resolve("enums"));

        // gera os repositories
        generateRepositories(prop.getEntities(),prop.getMainPackage(),packagePath.resolve("repositories"));

        // Gera requestData e outputData
        GenerateCommon.generateFileCommon(prop.getMainPackage(),packagePath.resolve("common"), "requestdata", "RequestData");
        GenerateCommon.generateFileCommon(prop.getMainPackage(),packagePath.resolve("common"), "responsedata", "ResponseData");

        // faz uma copia da properties_dot.json para a pasta static
        generateMetadata(prop);

        generateResources(prop.getEntities(),prop.getEndpoints());
    }

    private static Path dropAndCreateDir(String packageName, Path projectDirectory, Path sourcesDirectory) {
        Path packagePath = sourcesDirectory.resolve(packageName.replace('.', '/'));
        Common.resourcePath = projectDirectory.resolve("src/main/resources");
        try {
            dropFiles(sourcesDirectory);
            Files.createDirectories(packagePath);
            for (String directory : new String[]{"entities", "dtos", "converters", "repositories",
                    "services", "controllers", "endpoints", "enums", "common"}) {
                Files.createDirectories(packagePath.resolve(directory));
            }
            Files.createDirectories(Common.resourcePath);
            return packagePath;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not prepare generated Java sources in " + sourcesDirectory, ex);
        }
    }

    private static void generateMetadata(Properties prop) {
        try{
            String fileName = String.format("%s/properties.json",Common.resourcePath.toString());
            Common.dropFile(fileName);
            var path = Path.of(fileName);
            var entity = new Gson().toJson(prop);
            Files.write(path, entity.getBytes(), StandardOpenOption.CREATE);
        }catch (IOException ex){
            ex.printStackTrace();
        }
    }

    private static void dropFiles(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
