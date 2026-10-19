package com.coolxer.plugin.infra.monitor.service;

import java.util.List;

public interface MetricSnapshotStore {

    List<MetricSeriesRow> loadLatestBatch(int lookbackMinutes);
}
