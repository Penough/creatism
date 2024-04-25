package com.creatism.cafelogger.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CafeLoggerProperties {
    List<CafeLoggerPcsProperties> loggerPoints = Collections.EMPTY_LIST;
    /**
     * plan to use it configure log path
     */
    String logPath;
}
