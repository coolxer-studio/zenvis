package com.coolxer.asset.service;

import com.coolxer.asset.model.*;

public interface ProductService {
    public void sendAndroidProbe(AndroidStart androidStart);
    public void sendAndroidDevice(AndroidDevice androidDevice);
    public void sendAndroidApp(AndroidApp androidApp);

}
