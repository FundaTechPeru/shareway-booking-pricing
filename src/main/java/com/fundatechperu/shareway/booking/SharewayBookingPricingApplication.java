package com.fundatechperu.shareway.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SharewayBookingPricingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SharewayBookingPricingApplication.class, args);
    }

}
