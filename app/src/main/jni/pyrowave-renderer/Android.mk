# PyroWave renderer (static library), linked into libmoonlight-core.so so that
# callbacks.c can hand decode units straight to it.
#
# arm64-v8a only: PyroWave needs a Vulkan 1.3 GPU with subgroup size control,
# which no 32-bit Android device has. On other ABIs this file defines nothing,
# moonlight-core is built without HAVE_PYROWAVE, and the app never offers
# PyroWave (MoonBridge.pyroWaveIsSupported() is then a stub returning false).
PYROWAVE_RENDERER_PATH := $(call my-dir)

ifeq ($(TARGET_ARCH_ABI),arm64-v8a)

# The codec and the Granite subset it links, from the Lavagnou/pyrowave
# submodule (the same commit LavApollo and LavArtemis-Qt pin).
include $(PYROWAVE_RENDERER_PATH)/../pyrowave/lavartemis/Android.mk

LOCAL_PATH := $(PYROWAVE_RENDERER_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE := pyrowave-renderer
LOCAL_SRC_FILES := pyrowave_renderer.cpp pyrowaveframing.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../moonlight-core/moonlight-common-c/src
LOCAL_CPPFLAGS := -std=c++17 -Wall -Wextra -Wno-missing-field-initializers
LOCAL_CPP_FEATURES := exceptions rtti
LOCAL_STATIC_LIBRARIES := pyrowave
LOCAL_EXPORT_LDLIBS := -landroid -llog -ldl
include $(BUILD_STATIC_LIBRARY)

endif
