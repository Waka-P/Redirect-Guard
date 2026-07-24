# Room: エンティティ/DAOはリフレクション経由で使われるため難読化除外
-keep class com.example.redirectguard.data.** { *; }

# AccessibilityService はマニフェストから明示的に参照されるため通常は問題ないが、念のため保持
-keep class com.example.redirectguard.service.** { *; }

# Kotlin コルーチンのデバッグ用メタデータに関する既知の警告を抑制
-dontwarn kotlinx.coroutines.**
