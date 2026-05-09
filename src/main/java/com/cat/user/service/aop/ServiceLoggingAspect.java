package com.cat.user.service.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import com.cat.user.service.dto.UserRequest;

import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@Slf4j
public class ServiceLoggingAspect {

	@Around("within(com.cat.user.service.service..*)")
	public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {
		String method = joinPoint.getSignature().toShortString();
		log.debug("--> {} {}", method, describeArgs(joinPoint.getArgs()));
		long start = System.nanoTime();
		try {
			return joinPoint.proceed();
		}
		finally {
			long durationMs = (System.nanoTime() - start) / 1_000_000L;
			log.debug("<-- {} ({}ms)", method, durationMs);
		}
	}

	private static Object describeArgs(Object[] args) {
		if (args == null || args.length == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (Object arg : args) {
			if (arg instanceof UserRequest ur) {
				sb.append("[UserRequest correo=").append(maskCorreo(ur.getCorreo())).append("] ");
			}
			else {
				sb.append(arg).append(" ");
			}
		}
		return sb.toString().trim();
	}

	private static String maskCorreo(String correo) {
		if (correo == null || correo.isBlank()) {
			return "***";
		}
		int at = correo.indexOf('@');
		if (at <= 1) {
			return "***";
		}
		return correo.charAt(0) + "***" + correo.substring(at);
	}
}
