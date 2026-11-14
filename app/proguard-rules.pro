# Keep Room entities and DAOs
-keep class com.mydocviewer.data.local.** { *; }

# Keep Apache POI XMLBeans classes used during local Office parsing
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.apache.poi.schemas.** { *; }
