package ru.student.reviewparser.collector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import ru.student.reviewparser.parser.AppleFeedParser;
import ru.student.reviewparser.parser.DostupnoePravoReviewParser;
import ru.student.reviewparser.source.SourceRegistry;
import ru.student.reviewparser.source.SourceService;

@Configuration(proxyBeanMethods = false)
public class CollectorConfiguration {

    @Bean
    public JsonLoader jsonLoader() {
        return new WebClientJsonLoader();
    }

    @Bean
    public HtmlLoader htmlLoader() {
        return new WebClientHtmlLoader();
    }

    @Bean
    public AppleFeedParser appleFeedParser() {
        return new AppleFeedParser();
    }

    @Bean
    public DostupnoePravoReviewParser dostupnoePravoReviewParser() {
        return new DostupnoePravoReviewParser();
    }

    @Bean
    public AppleReviewCollector appleReviewCollector(
            JsonLoader loader,
            AppleFeedParser parser
    ) {
        return new AppleReviewCollector(loader, parser);
    }

    @Bean
    public DostupnoePravoReviewCollector dostupnoePravoReviewCollector(
            HtmlLoader loader,
            DostupnoePravoReviewParser parser
    ) {
        return new DostupnoePravoReviewCollector(loader, parser);
    }

    @Bean
    @Primary
    public ReviewCollector reviewCollector(
            AppleReviewCollector appleCollector,
            DostupnoePravoReviewCollector dostupnoePravoCollector
    ) {
        return new RoutingReviewCollector(
                appleCollector,
                dostupnoePravoCollector
        );
    }

    @Bean
    public SourceRegistry sourceRegistry() {
        return new SourceRegistry();
    }

    @Bean
    public SourceService sourceService(
            SourceRegistry registry,
            ReviewCollector collector,
            @Value("${review.collection.schedule-interval-seconds:60}")
            long scheduleIntervalSeconds
    ) {
        return new SourceService(
                registry,
                collector,
                scheduleIntervalSeconds
        );
    }
}
