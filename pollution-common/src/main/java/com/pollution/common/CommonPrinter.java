package com.pollution.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommonPrinter {

    private static final Logger logger = LoggerFactory.getLogger(CommonPrinter.class);

    public static void print(String message) {
        logger.info(message);
    }
}
