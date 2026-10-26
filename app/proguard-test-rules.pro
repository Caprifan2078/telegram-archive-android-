# Test-only compile-time Error Prone annotation references a JDK compiler enum.
# It is never loaded by Android or the runner.
-dontwarn javax.lang.model.element.Modifier
-keep class org.openarchive.telegram.** { *; }
-keepattributes *Annotation*
