# Keep existing archive entry points and public model members for first compact QA.
# Android manifest activities are kept by AGP; keep local classes until device
# regression checks confirm it is safe to shrink them more aggressively.
-keep class org.openarchive.telegram.** { *; }
# Preserve Gson-serialized metadata and Lottie reflective models via their
# upstream consumer rules. Avoid stripping annotations used by those libs.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
# Okio optional JSR-305 Nullable marker
-dontwarn javax.annotation.Nullable

# Instrumentation shares these runtime dependencies with the target APK.
# R8 only sees target-app usages here, so removing or merging classes needed
# exclusively by AndroidJUnitRunner/ActivityScenario breaks test startup.
# Retain the shared namespaces while testing this compact QA build.
-keep class androidx.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
