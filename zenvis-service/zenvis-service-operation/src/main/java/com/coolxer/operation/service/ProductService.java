package com.coolxer.operation.service;

import com.coolxer.operation.model.AndroidActivity;
import com.coolxer.operation.model.AndroidStart;

public interface ProductService {
    public void sendAndroidStart(AndroidStart androidStart);
    public void sendAndroidActivity(AndroidActivity AndroidActivity);

}
