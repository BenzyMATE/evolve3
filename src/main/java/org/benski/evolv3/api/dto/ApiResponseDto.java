package org.benski.evolv3.api.dto;

public class ApiResponseDto<T> {
  public boolean success;
  public T data;
  public ApiErrorDto error;
}
