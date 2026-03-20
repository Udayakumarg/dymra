package com.tirupurconnect.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchConfig {

    private final AppProperties props;
    private final ObjectMapper  objectMapper;

    // Only create ES beans when elasticsearch is explicitly enabled
    // This prevents startup failure when ES is not available
    @Bean
    @ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
    public RestClient elasticsearchRestClient() {
        AppProperties.Elasticsearch es = props.getElasticsearch();

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
            new UsernamePasswordCredentials(es.getUsername(), es.getPassword()));

        RestClient restClient = RestClient
            .builder(new HttpHost(es.getHost(), es.getPort(), "http"))
            .setHttpClientConfigCallback(
                builder -> builder.setDefaultCredentialsProvider(credentialsProvider))
            .build();

        log.info("Elasticsearch client configured: {}:{}", es.getHost(), es.getPort());
        return restClient;
    }

    @Bean
    @ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        RestClientTransport transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper(objectMapper));
        return new ElasticsearchClient(transport);
    }
}
