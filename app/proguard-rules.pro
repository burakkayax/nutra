-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep kotlinx.serialization companions and serializers
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep,includedescriptorclasses class com.nutra.app.domain.model.**$$serializer { *; }
-keep,includedescriptorclasses class com.nutra.app.domain.repository.NutraBackupPayload$$serializer { *; }
-keep,includedescriptorclasses class com.nutra.app.core.network.**$$serializer { *; }

-dontwarn com.google.errorprone.annotations.**
