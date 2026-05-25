# Keep AIDL interfaces and parcelables
-keep class net.osmand.aidl.** { *; }

# Keep SmartCraftData (used reflectively by parser)
-keep class com.diaznet.osmandsmartcraft.SmartCraftData { *; }
