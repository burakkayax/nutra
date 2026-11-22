-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep kotlinx.serialization companions, serializers, and serialized enums/DTOs
-keepclassmembers class com.nutra.app.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep enum com.nutra.app.domain.model.** { *; }
-keep,includedescriptorclasses class com.nutra.app.domain.model.**$$serializer { *; }
-keep,includedescriptorclasses class com.nutra.app.domain.repository.**$$serializer { *; }
-keep,includedescriptorclasses class com.nutra.app.core.database.**$$serializer { *; }
-keep class com.nutra.app.domain.repository.NutraBackupPayload { *; }
-keep class com.nutra.app.core.database.SeedCatalogPayloadDto { *; }
-keep class com.nutra.app.core.database.SeedFoodDto { *; }
-keep class com.nutra.app.core.database.SeedServingDto { *; }

-dontwarn com.google.errorprone.annotations.**
