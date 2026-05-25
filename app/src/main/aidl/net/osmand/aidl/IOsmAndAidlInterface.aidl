package net.osmand.aidl;

import net.osmand.aidl.mapwidget.AMapWidget;
import net.osmand.aidl.mapwidget.AddMapWidgetParams;
import net.osmand.aidl.mapwidget.RemoveMapWidgetParams;
import net.osmand.aidl.mapwidget.UpdateMapWidgetParams;

// Method order MUST match OsmAnd's actual IOsmAndAidlInterface exactly.
// AIDL uses sequential transaction codes based on declaration order.

interface IOsmAndAidlInterface {
    // 1
    boolean addMapMarker(in AddMapWidgetParams params);
    // 2
    boolean removeMapMarker(in RemoveMapWidgetParams params);
    // 3
    boolean updateMapMarker(in UpdateMapWidgetParams params);

    // 4 - actual addMapWidget
    boolean addMapWidget(in AddMapWidgetParams params);
    // 5
    boolean removeMapWidget(in RemoveMapWidgetParams params);
    // 6
    boolean updateMapWidget(in UpdateMapWidgetParams params);
}
