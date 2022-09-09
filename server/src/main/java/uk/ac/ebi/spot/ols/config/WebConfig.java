package uk.ac.ebi.spot.ols.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.util.UrlPathHelper;

/**
 * @author Simon Jupp
 * @date 25/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Configuration
public class WebConfig extends WebMvcConfigurerAdapter {


    /**
     * This config stops the rest api from using suffix pattern matching on URLs to determine content type
     * e.g. .json for .xml in URL to get data back in specific formats
     *
     * @param configurer
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
//        UrlPathHelper urlPathHelper = new UrlPathHelper();
//               urlPathHelper.setUrlDecode(false);
//               configurer.setUrlPathHelper(urlPathHelper);
        configurer
            .setUseSuffixPatternMatch(false);


    }

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



}
