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
        Properties properties = new Properties();
        properties.load(new FileReader(System.getProperty("aws.properties")));
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

        ListTagsForResourcesRequest tagsRequest = new ListTagsForResourcesRequest();
        List<String> healthCheckIds = new ArrayList<>(healthCheckResult.getHealthChecks().size());
        for (HealthCheck healthCheck : healthCheckResult.getHealthChecks()) {
            healthCheckIds.add(healthCheck.getId());
        }
        tagsRequest.setResourceIds(healthCheckIds);
        tagsRequest.setResourceType(TagResourceType.Healthcheck);
        ListTagsForResourcesResult tagsResult = route53Client.listTagsForResources(tagsRequest);
        HashMap<String, HashMap<String, String>> healthcheckTagsMap = new HashMap<>();
        for (ResourceTagSet tagSet : tagsResult.getResourceTagSets()) {
            HashMap<String, String> tags = new HashMap<>();
            for (Tag tag : tagSet.getTags()) {
                tags.put(tag.getKey().toLowerCase(), tag.getValue());
            }
            healthcheckTagsMap.put(tagSet.getResourceId(), tags);
        }

        for (HealthCheck healthCheck : healthCheckResult.getHealthChecks()) {
            Entity entity = new Entity(healthCheck.getId());

            HashMap<String, String> healthcheckTags = healthcheckTagsMap.get(healthCheck.getId());
            if (healthcheckTags == null) {
                healthcheckTags = new HashMap<>();
            }

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

            String name = healthcheckTags.get("name");
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

            for (Map.Entry<String, String> healthckeckTag : healthcheckTags.entrySet()) {
                tags.put(healthckeckTag.getKey(), healthckeckTag.getValue());
            }

            entity.setTags(tags);
            metaDataService.createOrReplaceEntity(entity);
        }
    }
}
