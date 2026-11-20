package br.com.smartverse.gonthera.cli;

import br.com.smartverse.gonthera.cli.service.GenerateSource;
import br.com.smartverse.gonthera.cli.service.common.Common;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

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
                    "target/generated-sources/gonthera/com/example/consumer/common/CrudController.java")));
        } finally {
            System.setProperty("user.dir", originalDirectory);
            Common.properties = originalProperties;
        }
    }
    @Test
    public void registersGeneratedSourcesForMavenModuleWithCustomBuildDirectory() throws Exception {
        Path directory = temporaryFolder.newFolder("module").toPath();
        Path configuration = Files.createDirectory(directory.resolve(".gonthera"));
        Files.write(configuration.resolve("project.json"),
                ("{\"mainPackage\":\"com.example.consumer\",\"projectName\":\"consumer\","
                        + "\"language\":\"JAVA\",\"entities\":[],\"endpoints\":[],\"enums\":[]}")
                        .getBytes(StandardCharsets.UTF_8));
        Path legacy = directory.resolve("src/main/java/com/example/consumer_gen/Keep.java");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, "// Existing consumer file".getBytes(StandardCharsets.UTF_8));
        MavenProject project = new MavenProject();
        project.setFile(directory.resolve("pom.xml").toFile());
        Build build = new Build();
        build.setDirectory("custom-target");
        project.setBuild(build);
        GenerateSource generator = new GenerateSource();
        java.lang.reflect.Field projectField = GenerateSource.class.getDeclaredField("project");
        projectField.setAccessible(true);
        projectField.set(generator, project);
        br.com.smartverse.gonthera.cli.model.Properties originalProperties = Common.properties;
        Path originalResourcePath = Common.resourcePath;
        try {
            generator.execute();
            Path sources = directory.resolve("custom-target/generated-sources/gonthera");
            assertTrue(project.getCompileSourceRoots().contains(sources.toString()));
            assertTrue(Files.isRegularFile(sources.resolve("com/example/consumer/common/CrudController.java")));
            assertTrue(Files.isRegularFile(directory.resolve("src/main/resources/properties.json")));
            assertFalse(Files.exists(directory.resolve("target")));
            assertEquals("// Existing consumer file", Files.readString(legacy));
        } finally {
            Common.properties = originalProperties;
            Common.resourcePath = originalResourcePath;
        }
    }

}
