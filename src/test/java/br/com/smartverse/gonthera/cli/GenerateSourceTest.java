package br.com.smartverse.gonthera.cli;

import br.com.smartverse.gonthera.cli.service.GenerateSource;
import br.com.smartverse.gonthera.cli.service.common.Common;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class GenerateSourceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void generatesSourcesInConsumerDirectory() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath();
        Path configuration = Files.createDirectory(directory.resolve(".gonthera"));
        Files.write(configuration.resolve("project.json"),
                ("{\"mainPackage\":\"com.example.consumer\",\"projectName\":\"consumer\","
                        + "\"language\":\"JAVA\",\"entities\":[],\"endpoints\":[],\"enums\":[]}")
                        .getBytes(StandardCharsets.UTF_8));
        String originalDirectory = System.getProperty("user.dir");
        br.com.smartverse.gonthera.cli.model.Properties originalProperties = Common.properties;
        try {
            System.setProperty("user.dir", directory.toString());
            new GenerateSource().generateSource();
            assertTrue(Files.isRegularFile(directory.resolve(
                    "src/main/java/com/example/consumer_gen/common/CrudController.java")));
        } finally {
            System.setProperty("user.dir", originalDirectory);
            Common.properties = originalProperties;
        }
    }
}
