# Keep existing archive entry points and public model members for first compact QA.
# Android manifest activities are kept by AGP; keep local classes until device
# regression checks confirm it is safe to shrink them more aggressively.
-keep class org.openarchive.telegram.** { *; }
# Preserve Gson-serialized metadata and Lottie reflective models via their
# upstream consumer rules. Avoid stripping annotations used by those libs.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
# Okio optional JSR-305 Nullable marker
-dontwarn javax.annotation.Nullable
