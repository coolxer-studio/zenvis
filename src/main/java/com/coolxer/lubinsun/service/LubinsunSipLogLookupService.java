package com.coolxer.lubinsun.service;

import com.coolxer.lubinsun.model.LubinsunSipLogLookupResult;

public interface LubinsunSipLogLookupService {

    LubinsunSipLogLookupResult lookup(String ip);
}
