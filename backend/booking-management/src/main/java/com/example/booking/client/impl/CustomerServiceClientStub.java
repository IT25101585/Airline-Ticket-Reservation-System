package com.example.booking.client.impl;

import com.example.booking.client.CustomerServiceClient;
import org.springframework.stereotype.Component;

// TEMPORARY stub. Replace with the real Customer/User module integration.
@Component
public class CustomerServiceClientStub implements CustomerServiceClient {

    @Override
    public boolean isActiveCustomer(Long customerId) {
        return customerId != null && customerId > 0;
    }
}