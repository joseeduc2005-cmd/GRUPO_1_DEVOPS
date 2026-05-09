package com.cat.user.service.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

	@Around("within(com.cat.user.service.controller..*)")
	public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
		String method = joinPoint.getSignature().toShortString();
		log.info("--> {}", method);
		long start = System.currentTimeMillis();
		try {
			return joinPoint.proceed();
		}
		finally {
			long duration = System.currentTimeMillis() - start;
			log.info("<-- {} ({}ms)", method, duration);
		}
	}
}
