-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep kotlinx.serialization companions, serializers, and serialized enums
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep enum com.nutra.app.domain.model.** { *; }
-keep,includedescriptorclasses class com.nutra.app.domain.model.**$$serializer { *; }
-keep,includedescriptorclasses class com.nutra.app.domain.repository.NutraBackupPayload$$serializer { *; }
-keep,includedescriptorclasses class com.nutra.app.core.database.**$$serializer { *; }

-dontwarn com.google.errorprone.annotations.**
