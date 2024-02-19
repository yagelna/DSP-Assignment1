package bgu.ds.manager.config;

import org.cfg4j.provider.ConfigurationProvider;
import org.cfg4j.provider.ConfigurationProviderBuilder;
import org.cfg4j.source.ConfigurationSource;
import org.cfg4j.source.classpath.ClasspathConfigurationSource;
import org.cfg4j.source.context.filesprovider.ConfigFilesProvider;

import java.nio.file.Paths;
import java.util.Arrays;

public class AWSConfigProvider {

    private static final ManagerAWSConfig managerAWSConfig = build();

    private static ManagerAWSConfig build() {
        ConfigFilesProvider configFilesProvider = () -> Arrays.asList(Paths.get("application.yaml"));
        ConfigurationSource source = new ClasspathConfigurationSource(configFilesProvider);
        ConfigurationProvider provider = new ConfigurationProviderBuilder().withConfigurationSource(source).build();
        return provider.bind("aws", ManagerAWSConfig.class);
    }

    public static ManagerAWSConfig getConfig() {
        return managerAWSConfig;
    }

}
