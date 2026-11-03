package ru.student.reviewparser.collector;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.student.reviewparser.parser.AppleFeedParser;
import ru.student.reviewparser.source.SourceRegistry;
import ru.student.reviewparser.source.SourceService;

@Configuration(proxyBeanMethods = false)
public class CollectorConfiguration {

    @Bean
    public JsonLoader jsonLoader() {
        return new WebClientJsonLoader();
    }

    @Bean
    public AppleFeedParser appleFeedParser() {
        return new AppleFeedParser();
    }

    @Bean
    public ReviewCollector reviewCollector(
            JsonLoader loader,
            AppleFeedParser parser
    ) {
        return new AppleReviewCollector(loader, parser);
    }

    @Bean
    public SourceRegistry sourceRegistry() {
        return new SourceRegistry();
    }

    @Bean
    public SourceService sourceService(
            SourceRegistry registry,
            ReviewCollector collector
    ) {
        return new SourceService(registry, collector);
    }
}
