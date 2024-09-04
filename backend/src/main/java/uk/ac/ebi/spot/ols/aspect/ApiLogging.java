package uk.ac.ebi.spot.ols.aspect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class ApiLogging {
    private static final Logger logger = LoggerFactory.getLogger(ApiLogging.class);

    @Pointcut("execution(public * uk.ac.ebi.spot.ols.controller.api.*.*Controller.*(..))")
//@Pointcut("@annotation(org.springframework.web.bind.annotation.RequestMapping) && execution(* uk.ac.ebi.spot.ols.controller.api.*.*Controller.*(..))")
    public void apiCalls() {};

    @Before("apiCalls()")
    public void apiCalled(JoinPoint jp) throws Throwable {
        logger.info("Class={} Method={} Parameters={}", jp.getSignature().getDeclaringTypeName(),
                jp.getSignature().getName(), jp.getArgs());
    }

}