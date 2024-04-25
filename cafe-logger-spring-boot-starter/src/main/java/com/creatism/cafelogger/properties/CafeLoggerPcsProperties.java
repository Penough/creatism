package com.creatism.cafelogger.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pointcuts for pointscuts
 * @author xuyu
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CafeLoggerPcsProperties {
    private Boolean enableKafka = true;
    /**
     * when enableKafka is true
     */
    private String topic;
    /**
     * partition
     */
    private String partition;
    /**
     * Pointcut
     */
    private String pointcut;
    /**
     * uri
     */
    private String uri;
    /**
     * uri
     */
    private String feignUri;
}
