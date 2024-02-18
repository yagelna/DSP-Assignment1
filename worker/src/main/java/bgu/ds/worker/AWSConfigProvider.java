package bgu.ds.worker;

import org.cfg4j.provider.ConfigurationProvider;
import org.cfg4j.provider.ConfigurationProviderBuilder;
import org.cfg4j.source.ConfigurationSource;
import org.cfg4j.source.classpath.ClasspathConfigurationSource;
import org.cfg4j.source.context.filesprovider.ConfigFilesProvider;

import java.nio.file.Paths;
import java.util.Arrays;

public class AWSConfigProvider {

    private static final WorkerAWSConfig WORKER_AWS_CONFIG = build();

    private static WorkerAWSConfig build() {
        ConfigFilesProvider configFilesProvider = () -> Arrays.asList(Paths.get("application.yaml"));
        ConfigurationSource source = new ClasspathConfigurationSource(configFilesProvider);
        ConfigurationProvider provider = new ConfigurationProviderBuilder().withConfigurationSource(source).build();
        return provider.bind("aws", WorkerAWSConfig.class);
    }

    public static WorkerAWSConfig getConfig() {
        return WORKER_AWS_CONFIG;
    }

}
