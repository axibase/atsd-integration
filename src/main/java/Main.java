import ch.qos.logback.classic.Logger;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.*;
import com.axibase.tsd.client.ClientConfigurationFactory;
import com.axibase.tsd.client.HttpClientManager;
import com.axibase.tsd.client.MetaDataService;
import com.axibase.tsd.model.meta.Entity;
import com.axibase.tsd.model.system.ClientConfiguration;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Main {

    public static void main(String[] args) throws IOException {
        Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.WARN);

        String atsdPropertiesFile = System.getProperty("axibase.tsd.api.client.properties");
        if (atsdPropertiesFile == null || atsdPropertiesFile.length() == 0) {
            System.out.println("No ATSD settings file. Specify -Daxibase.tsd.api.client.properties parameter");
            return;
        }

        String awsPropertiesFile = System.getProperty("aws.properties");
        if (awsPropertiesFile == null || awsPropertiesFile.length() == 0) {
            System.out.println("No AWS Route53 settings file. Specify -Daws.properties parameter");
            return;
        }

        Properties properties = new Properties();
        properties.load(new FileReader(awsPropertiesFile));
        String awsAccessKey = properties.getProperty("aws.access.key");
        String awsSecretKey = properties.getProperty("aws.secret.key");
        String awsRegion = properties.getProperty("aws.region");

        AmazonRoute53 route53Client = AmazonRoute53ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(awsAccessKey, awsSecretKey)))
                .withRegion(awsRegion)
                .build();

        ClientConfiguration clientConfiguration = ClientConfigurationFactory
                .createInstance()
                .createClientConfiguration();
        HttpClientManager httpClientManager = new HttpClientManager(clientConfiguration);
        MetaDataService metaDataService = new MetaDataService(httpClientManager);

        System.out.println("Connecting to AWS Endpoint");
        ListHealthChecksResult healthCheckResult = route53Client.listHealthChecks();

        HashMap<String, HashMap<String, String>> healthCheckTagsMap = getHealthCheckTags(healthCheckResult, route53Client);

        for (HealthCheck healthCheck : healthCheckResult.getHealthChecks()) {
            HashMap<String, String> healthCheckTags = healthCheckTagsMap.get(healthCheck.getId());
            if (healthCheckTags == null) {
                healthCheckTags = new HashMap<>();
            }

            sendEntity(healthCheck, healthCheckTags, metaDataService);
        }

        System.out.println(healthCheckResult.getHealthChecks().size() + " health checks processed");
    }

    private static HashMap<String, HashMap<String, String>> getHealthCheckTags(
            ListHealthChecksResult healthCheckResult,
            AmazonRoute53 route53Client) {
        HashMap<String, HashMap<String, String>> healthCheckTagsMap = new HashMap<>();
        if (healthCheckResult.getHealthChecks().size() == 0) {
            return healthCheckTagsMap;
        }

        ListTagsForResourcesRequest tagsRequest = new ListTagsForResourcesRequest();
        List<String> healthCheckIds = new ArrayList<>(healthCheckResult.getHealthChecks().size());
        for (HealthCheck healthCheck : healthCheckResult.getHealthChecks()) {
            healthCheckIds.add(healthCheck.getId());
        }
        tagsRequest.setResourceIds(healthCheckIds);
        tagsRequest.setResourceType(TagResourceType.Healthcheck);

        try {
            ListTagsForResourcesResult tagsResult = route53Client.listTagsForResources(tagsRequest);
            for (ResourceTagSet tagSet : tagsResult.getResourceTagSets()) {
                HashMap<String, String> tags = new HashMap<>();
                for (Tag tag : tagSet.getTags()) {
                    tags.put(tag.getKey().toLowerCase(), tag.getValue());
                }
                healthCheckTagsMap.put(tagSet.getResourceId(), tags);
            }
        } catch (AmazonRoute53Exception e) {
            System.err.println("Tags request error: " + e.getMessage());
        }

        return healthCheckTagsMap;
    }

    private static void sendEntity(
            HealthCheck healthCheck,
            HashMap<String, String> healthCheckTags,
            MetaDataService metaDataService) {
        Entity entity = new Entity(healthCheck.getId());

        HealthCheckConfig checkConfig = healthCheck.getHealthCheckConfig();
        HashMap<String, String> tags = new HashMap<>();

        String protocol = checkConfig.getType();
        String ipAddress = checkConfig.getIPAddress();
        String domain = checkConfig.getFullyQualifiedDomainName();
        String port = String.valueOf(checkConfig.getPort());
        String path = checkConfig.getResourcePath();

        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(protocol.toLowerCase()).append("://");
        if (domain != null && domain.length() > 0) {
            urlBuilder.append(domain);
        } else {
            urlBuilder.append(ipAddress);
        }
        urlBuilder.append(":").append(port);
        if (path != null && path.length() > 0) {
            urlBuilder.append(path);
        }
        String url = urlBuilder.toString();

        String name = healthCheckTags.get("name");
        if (name != null) {
            entity.setLabel(name);
        } else {
            entity.setLabel(url);
        }

        tags.put("protocol", protocol);
        tags.put("ip_address", ipAddress);
        tags.put("domain_name", domain);
        tags.put("port", port);
        tags.put("resource_path", path);
        tags.put("url", url);

        tags.put("search_string", checkConfig.getSearchString());
        if (checkConfig.getRequestInterval() != null) {
            tags.put("request_interval", String.valueOf(checkConfig.getRequestInterval()));
        }
        if (checkConfig.getFailureThreshold() != null) {
            tags.put("failure_threshold", String.valueOf(checkConfig.getFailureThreshold()));
        }
        if (checkConfig.getMeasureLatency() != null) {
            tags.put("measure_latency", String.valueOf(checkConfig.getMeasureLatency()));
        }
        if (checkConfig.getInverted() != null) {
            tags.put("inverted", String.valueOf(checkConfig.getInverted()));
        }
        if (checkConfig.getHealthThreshold() != null) {
            tags.put("health_threshold", String.valueOf(checkConfig.getHealthThreshold()));
        }
        if (checkConfig.isEnableSNI() != null) {
            tags.put("enable_sni", String.valueOf(checkConfig.isEnableSNI()));
        }
        tags.put("regions", String.join(", ", checkConfig.getRegions()));
        tags.put("insufficient_data_health_status", checkConfig.getInsufficientDataHealthStatus());
        tags.put("children_health_checks", String.join(", ", checkConfig.getChildHealthChecks()));

        AlarmIdentifier alarmIdentifier = checkConfig.getAlarmIdentifier();
        if (alarmIdentifier != null) {
            tags.put("alarm_identifier_name", alarmIdentifier.getName());
            tags.put("alarm_identifier_region", alarmIdentifier.getRegion());
        }

        for (Map.Entry<String, String> healthCheckTag : healthCheckTags.entrySet()) {
            tags.put("tag." + healthCheckTag.getKey(), healthCheckTag.getValue());
        }

        entity.setTags(tags);
        metaDataService.createOrReplaceEntity(entity);
    }
}
