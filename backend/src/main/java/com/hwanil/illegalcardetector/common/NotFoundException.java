package com.hwanil.illegalcardetector.common;

/**
 * 찾는 데이터가 없을 때 던지는 예외. GlobalExceptionHandler가 404 응답으로 바꿔요.
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
