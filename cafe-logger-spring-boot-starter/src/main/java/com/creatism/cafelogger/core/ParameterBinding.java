package com.creatism.cafelogger.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.lang.reflect.Parameter;

@Getter
@AllArgsConstructor
public class ParameterBinding {
    private Parameter parameter;
    private Object value;
}
