package uk.ac.ebi.spot.ols.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * @author Simon Jupp
 * @date 27/09/2016
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 *
 * Detects when we are in maintenance mode and redirects to maintenance page
 * Can also be configured to take down certain API endpoints depending on which backend service is effected
 *
 */
@Component
public class MaintenanceInterceptor extends HandlerInterceptorAdapter  {

    @Value("${ols.maintenance.start:#{null}}")
    String start = null;


    @Value("${ols.maintenance.end:#{null}}")
    String end = null;


    /**
     * have the ability to take down specific services
     * for instance if mongo is down, you can still support search and graph api.
     */


    /**
     * Setting solr to true will diable the api/search endpoint
     */
    @Value("${ols.maintenance.solr:#{false}}")
    boolean solr;

    /**
     * Setting neo4j to true will disable the api/terms and api/ontologies/xx/terms endpoint
     */
    @Value("${ols.maintenance.neo4j:#{false}}")
    boolean neo4j;
    /**
     * Setting mongo to true will diable the api/ontologies endpoint
     */
    @Value("${ols.maintenance.mongo:#{false}}")
    boolean mongo;

    @Value("${ols.maintenance.message:#{null}}")
    String message;


    //before the actual handler will be executed
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler)
            throws Exception {

        if (request.getRequestURL().toString().contains("/maintenance")
                ||  request.getRequestURL().toString().contains("/api/unavailable")
                ) {
            return true;
        }

        if (start != null && end != null) {


            try {
                Calendar cal = Calendar.getInstance();
                Date currenTime = cal.getTime();
                SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date maintenanceStartTime = format1.parse( start );
                Date maintenanceEndTime = format1.parse( end );

                if (currenTime.after(maintenanceStartTime) && currenTime.before(maintenanceEndTime)) {
                    //maintenance time, send to maintenance page

                    if (request.getRequestURL().toString().contains("/api")) {
                        if (request.getRequestURL().toString().contains("/api/search") && solr) {
                            response.sendRedirect( request.getContextPath() + "/api/unavailable");
                            return false;
                        }

                        if ( (request.getRequestURL().toString().contains("/terms") || request.getRequestURL().toString().contains("/properties") || request.getRequestURL().toString().contains("/individuals") )
                                && neo4j) {
                            response.sendRedirect( request.getContextPath() + "/api/unavailable");
                            return false;
                        }

                        if (request.getRequestURL().toString().contains("/ontologies") && !request.getRequestURL().toString().contains("/terms") && mongo) {
                            response.sendRedirect( request.getContextPath() + "/api/unavailable");
                            return false;
                        }
                    } else {
                        response.sendRedirect( request.getContextPath() + "/maintenance");
                        return false;

                    }
                    return true;
                } else {
                    return true;
                }
            } catch (Exception e) {
                throw new Exception("Can't parse maintenance mode start or end time");
            }

        }
        return true;

    }




}
