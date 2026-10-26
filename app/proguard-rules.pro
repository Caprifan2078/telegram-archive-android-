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
# The compact QA APK is minified but Android instrumentation is not.
# Both production code and the independent TGS regression use Lottie's public
# parser/drawable API. Preserve precisely these class contracts so synthetic
# device tests exercise the SAME runtime library as the installed APK.
# Otherwise R8 rewrites fromJsonStringSync, producing NoSuchMethodError only
# when that test calls the method (and hiding any genuine decoder failures).
-keep class com.airbnb.lottie.LottieCompositionFactory { *; }
-keep class com.airbnb.lottie.LottieComposition { *; }
-keep class com.airbnb.lottie.LottieResult { *; }
-keep class com.airbnb.lottie.LottieDrawable { *; }

# The classifier regression tests also call Jsoup's facade directly.
-keep class org.jsoup.** { *; }
# Jsoup's compile-only nullness annotations are not needed by Android at runtime.
-dontwarn org.jspecify.annotations.**

# Commons Compress has an optional Zstandard backend. No zstd-jni is packaged;
# ZIP methods 20/93 are explicitly rejected by ArchiveReadOnly before decode.
-dontwarn com.github.luben.zstd.**
