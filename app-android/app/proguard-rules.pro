# Release shrinking (R8) rules.
# sherpa-onnx's native code (JNI) reads Kotlin config classes and fields by name: keep them intact.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# OkHttp / Okio ship their own consumer rules; nothing needed here.
