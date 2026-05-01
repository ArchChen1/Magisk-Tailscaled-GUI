# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class top.cenmin.tailcontrol.**$$serializer { *; }
-keepclassmembers class top.cenmin.tailcontrol.** {
    *** Companion;
}
-keepclasseswithmembers class top.cenmin.tailcontrol.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# libsu
-keep class com.topjohnwu.superuser.** { *; }
-keep class **.RootService { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
