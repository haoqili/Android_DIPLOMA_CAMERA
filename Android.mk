# copied from tutorial Android.mk

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# Only build apk if this package is added to CUSTOM_MODLUES in          buildspec.mk
LOCAL_MODULE_TAGS := optional
# Only compile source java files in this apk.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := sparse_matrix_mult_java_grande_forum_benchmark

# Make the app build against the current SDK
LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)
