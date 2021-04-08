package com.michelin.ns4kafka.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.michelin.ns4kafka.cli.models.Resource;
import com.michelin.ns4kafka.cli.models.ResourceKind;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.http.HttpRequest;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

//TODO Make subcommand understand parameters
@Command(name = "kafkactl", subcommands = {Login.class} , description = "...",
        mixinStandardHelpOptions = true)
public class KafkactlCommand implements Runnable {


    @Option(names = {"-v", "--verbose"}, description = "...")
    boolean verbose;

    String json = "";
    ResourceKind kind;
    String namespace;
    @Option(names = {"-f", "--file"}, description = "File in Yaml describing the system Kafka")
    public void convertYamlToJson(File file) throws Exception {
        //TODO better management of Exception
        Yaml yaml = new Yaml(new Constructor(Resource.class));
        ObjectMapper jsonWriter = new ObjectMapper();
        InputStream inputStream = new FileInputStream(file);

        //TODO manage multi document YAML
        Resource resourceYaml = yaml.load(inputStream);

        //Throws exception if the kind doesn't exist
        kind = ResourceKind.resourceKindFromValue(resourceYaml.getKind());
        namespace = resourceYaml.getMetadata().getNamespace();

        //convert to JSON
        json = jsonWriter.writeValueAsString(resourceYaml);

    }

    String jwt;
    @Option(names = {"-t", "--token"}, description = "Access token of Gitlab")
    public void getJWT(String token) {
        //TODO Post on Login and get jwt
        this.jwt = token;
    }

    public static void main(String[] args) throws Exception {
        PicocliRunner.execute(KafkactlCommand.class, args);
    }

    public void run() {
        // business logic here
        System.out.println("Hi!");
        if (verbose) {
            System.out.println("Hi!");
        }
        if (!json.isBlank()) {
            switch(kind) {
            case NAMESPACE:
                HttpRequest.POST(URI.create("http://localhost:8080/api/namespaces"), json);
                break;
            case ACCESSCONTROLENTRY:
                HttpRequest.POST(URI.create("http://localhost:8080/api/namespaces/" + namespace + "/acls"), json);
                break;
            case CONNECTOR:
                HttpRequest.POST(URI.create("http://localhost:8080/api/namespaces/" + namespace + "/connects"), json);
                break;
            case TOPIC:
                HttpRequest.POST(URI.create("http://localhost:8080/api/namespaces/" + namespace + "/topic"), json);
                break;
            default:
                break;
            }
        }

    }

}
