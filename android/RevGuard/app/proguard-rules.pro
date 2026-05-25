# Rev Guard ProGuard / R8 rules

# Compose libraries ship their own consumer rules, so most cases
# are handled automatically. These rules cover edge cases.

# Keep Compose runtime stability annotations (required for R8)
-dontwarn androidx.compose.**

# BLE callback classes are invoked by the Android framework via reflection
-keep class com.revguard.BleService$* { *; }
-keep class com.revguard.BleManager$* { *; }
