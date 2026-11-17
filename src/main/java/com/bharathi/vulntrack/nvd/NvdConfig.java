package com.bharathi.vulntrack.nvd;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(NvdProperties.class)
public class NvdConfig {

    @Bean
    public NvdClient nvdClient(NvdProperties props) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(props.connectTimeout());
        requestFactory.setReadTimeout(props.readTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory);

        if (props.apiKey() != null && !props.apiKey().isBlank()) {
            builder.defaultHeader("apiKey", props.apiKey());
        }
        return new NvdClient(builder.build());
    }
}