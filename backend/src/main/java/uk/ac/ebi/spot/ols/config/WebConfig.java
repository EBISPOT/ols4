package uk.ac.ebi.spot.ols.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.*;

import java.util.List;

/**
 * @author Simon Jupp
 * @date 25/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {


    @Value("${ols.search.max-rows:1000}")
    private int maxPageSize;

//     @Bean
//     MaintenanceInterceptor getMaintenanceInterceptor() {
//         return new MaintenanceInterceptor();
//     }

//     @Autowired
//     MaintenanceInterceptor interceptor;
//     @Override
//      public void addInterceptors(InterceptorRegistry registry) {
//          registry.addInterceptor(interceptor);
//      }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("*").allowedHeaders("*").allowedMethods("GET");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        PageableHandlerMethodArgumentResolver resolver = new PageableHandlerMethodArgumentResolver();
        resolver.setFallbackPageable(PageRequest.of(0, maxPageSize));
        resolver.setMaxPageSize(maxPageSize);
        argumentResolvers.add(resolver);
    }



}
