-keepattributes SourceFile,LineNumberTable
-keep class com.reeware.android.breed.render.** { *; }
-keep class com.reeware.android.breed.ui.** { *; }
-keep class com.reeware.android.breed.audio.** { *; }
-keep class com.reeware.android.breed.billing.** { *; }
-keepclassmembers enum com.reeware.android.breed.engine.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
