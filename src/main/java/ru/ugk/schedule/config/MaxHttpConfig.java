package ru.ugk.schedule.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class MaxHttpConfig {
    @Bean
    RestClient maxRestClient(SslBundles bundles) {
        var client = HttpClient.newBuilder()
                .sslContext(bundles.getBundle("max").createSslContext())
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory).build();
    }
}
