# Default ProGuard rules for the JuSi Meet Android app.
#
# Add project-specific rules here.  Compose, Retrofit/Moshi and LiveKit
# typically work without additional rules in their default consumer rules,
# but if you hit a NoSuchMethodError or NoClassDefFoundError on a release
# build, this is the place to add a -keep clause.

# Keep our DTOs reflectively used by Moshi.
-keep class com.jusi.meet.data.api.dto.** { *; }
