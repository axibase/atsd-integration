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
import java.nio.file.AccessDeniedException;
import java.util.*;

public class Main {

    public static void main(String[] args) throws IOException {
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

        ListHealthChecksResult healthCheckResult = route53Client.listHealthChecks();

        HashMap<String, HashMap<String, String>> healthCheckTagsMap = getHealthCheckTags(healthCheckResult, route53Client);

        for (HealthCheck healthCheck : healthCheckResult.getHealthChecks()) {
            HashMap<String, String> healthCheckTags = healthCheckTagsMap.get(healthCheck.getId());
            if (healthCheckTags == null) {
                healthCheckTags = new HashMap<>();
            }

            sendEntity(healthCheck, healthCheckTags, metaDataService);
        }
    }

    private static HashMap<String, HashMap<String, String>> getHealthCheckTags(
            ListHealthChecksResult healthCheckResult,
            AmazonRoute53 route53Client) {
        ListTagsForResourcesRequest tagsRequest = new ListTagsForResourcesRequest();
        List<String> healthCheckIds = new ArrayList<>(healthCheckResult.getHealthChecks().size());
        for (HealthCheck healthCheck : healthCheckResult.getHealthChecks()) {
            healthCheckIds.add(healthCheck.getId());
        }
        tagsRequest.setResourceIds(healthCheckIds);
        tagsRequest.setResourceType(TagResourceType.Healthcheck);

        HashMap<String, HashMap<String, String>> healthCheckTagsMap = new HashMap<>();
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
            System.out.println("User not authorized to perform listTagsForResources request");
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

        addEntityTag(tags, "protocol", protocol);
        addEntityTag(tags, "ip_address", ipAddress);
        addEntityTag(tags, "domain_name", domain);
        addEntityTag(tags, "port", port);
        addEntityTag(tags, "resource_path", path);
        addEntityTag(tags, "url", url);

        addEntityTag(tags, "search_string", checkConfig.getSearchString());
        addEntityTag(tags, "request_interval", checkConfig.getRequestInterval());
        addEntityTag(tags, "failure_threshold", checkConfig.getFailureThreshold());
        addEntityTag(tags, "measure_latency", checkConfig.getMeasureLatency());
        addEntityTag(tags, "inverted", checkConfig.getInverted());
        addEntityTag(tags, "health_threshold", checkConfig.getHealthThreshold());
        addEntityTag(tags, "enable_sni", checkConfig.isEnableSNI());
        addEntityTag(tags, "regions", String.join(", ", checkConfig.getRegions()));
        addEntityTag(tags, "insufficient_data_health_status", checkConfig.getInsufficientDataHealthStatus());
        addEntityTag(tags, "children_health_checks", String.join(", ", checkConfig.getChildHealthChecks()));

        AlarmIdentifier alarmIdentifier = checkConfig.getAlarmIdentifier();
        if (alarmIdentifier != null) {
            addEntityTag(tags, "alarm_identifier_name", alarmIdentifier.getName());
            addEntityTag(tags, "alarm_identifier_region", alarmIdentifier.getRegion());
        }

        for (Map.Entry<String, String> healthCheckTag : healthCheckTags.entrySet()) {
            addEntityTag(tags, "tag." + healthCheckTag.getKey(), healthCheckTag.getValue());
        }

        entity.setTags(tags);
        metaDataService.createOrReplaceEntity(entity);
    }

    private static void addEntityTag(HashMap<String, String> tags, String key, String value) {
        if (value != null) {
            tags.put(key, value);
        }
    }

    private static void addEntityTag(HashMap<String, String> tags, String key, Integer value) {
        if (value != null) {
            tags.put(key, String.valueOf(value));
        }
    }

    private static void addEntityTag(HashMap<String, String> tags, String key, Boolean value) {
        if (value != null) {
            tags.put(key, String.valueOf(value));
        }
    }
}
