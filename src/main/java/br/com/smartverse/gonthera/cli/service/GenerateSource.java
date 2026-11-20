package br.com.smartverse.gonthera.cli.service;


import br.com.smartverse.gonthera.cli.enuns.Language;
import br.com.smartverse.gonthera.cli.service.common.Common;
import br.com.smartverse.gonthera.cli.service.common.ConfigurationFileValidator;
import br.com.smartverse.gonthera.cli.service.common.ProjectValidator;
import br.com.smartverse.gonthera.cli.service.dotNet.GenerateDotNet;
import br.com.smartverse.gonthera.cli.service.node.GenerateNode;
import br.com.smartverse.gonthera.cli.service.java.GenerateJava;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


import static br.com.smartverse.gonthera.cli.service.common.Common.*;


@Mojo(name="generate-sources", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateSource extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Iniciando geração de fontes");
        generateSource();
    }

    public void generateSource() throws MojoExecutionException {
        getLog().info("Carregando metadata");
        Path projectDirectory = project == null
                ? Paths.get(System.getProperty("user.dir")).toAbsolutePath()
                : project.getBasedir().toPath();
        ConfigurationFileValidator.validateForGeneration(projectDirectory);
        Common.properties = loadProperties(projectDirectory);
        ProjectValidator.validate(Common.properties);
        ProjectValidator.warnings(Common.properties).forEach(getLog()::warn);

        if(Common.properties.getLanguage() == null){
            throw new MojoExecutionException("Language not defined");
        }
        if(Common.properties.getLanguage() == Language.JAVA){
            Path buildDirectory = project == null
                    ? projectDirectory.resolve("target")
                    : projectDirectory.resolve(project.getBuild().getDirectory());
            Path sourcesDirectory = buildDirectory.resolve("generated-sources/gonthera").toAbsolutePath().normalize();
            Path legacyDirectory = projectDirectory.resolve("src/main/java")
                    .resolve(Common.properties.getMainPackage().replace('.', '/') + "_gen");
            if (Files.exists(legacyDirectory)) {
                getLog().warn("Legacy generated sources found in " + legacyDirectory
                        + ". Update imports to the base package, move custom code out and remove the old generated files.");
            }
            try {
                GenerateJava.generateSource(Common.properties, projectDirectory, sourcesDirectory);
            } catch (IllegalStateException ex) {
                throw new MojoExecutionException("Could not generate Java sources", ex);
            }
            if (project != null) {
                project.addCompileSourceRoot(sourcesDirectory.toString());
            }
        }
        if(Common.properties.getLanguage() == Language.DOTNET){
            GenerateDotNet.generateSource(Common.properties);
        }
        if(Common.properties.getLanguage() == Language.NODE){
            GenerateNode.generateSource(Common.properties);
        }

    }

}
