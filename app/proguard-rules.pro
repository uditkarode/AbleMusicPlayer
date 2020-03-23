-keepattributes *Annotation*
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

-dontwarn org.codehaus.mojo.animal_sniffer.*
# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**

-keep public class org.jsoup.** {
public *;
}