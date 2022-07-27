package uk.ac.ebi.spot.ols.config;

import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.tuckey.web.filters.urlrewrite.UrlRewriteFilter;

import javax.servlet.ServletContext;

/**
 * @author Simon Jupp
 * @date 01/04/2016
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Component
public class RedirectFilter {

    public static final String REWRITE_FILTER_CONF_PATH = "urlrewrite.xml";

    @Bean
     public FilterRegistrationBean filterRegistrationBean(final ServletContext servletContext)
     {
         FilterRegistrationBean registrationBean = new FilterRegistrationBean();

         registrationBean.setName("UrlRewriteFilter");
         registrationBean.setFilter(new UrlRewriteFilter());
         registrationBean.addUrlPatterns("*");
//         registrationBean.addInitParameter("confPath", REWRITE_FILTER_CONF_PATH);
//         registrationBean.addInitParameter("statusPath", "/redirect");
//         registrationBean.addInitParameter("statusEnabledOnHosts", "*");
         registrationBean.addInitParameter("confReloadCheckInterval", "-1");
//         registrationBean.addInitParameter("logLevel", "sysout:DEBUG");
         registrationBean.addInitParameter("logLevel", "slf4j:INFO");

         return registrationBean;
     }


}
