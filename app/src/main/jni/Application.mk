# Application.mk for Moonlight

# Our minimum version is Android 5.0
APP_PLATFORM := android-21

# We support 16KB pages
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true

# The PyroWave renderer and the codec it links are C++. Static, since only
# libmoonlight-core.so contains C++.
APP_STL := c++_static
