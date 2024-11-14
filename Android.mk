LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-subdir-java-files, app)

LOCAL_PACKAGE_NAME := Bugreport
LOCAL_CERTIFICATE := platform
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_MANIFEST_FILE := app/src/main/AndroidManifest.xml
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/app/src/main/res
#LOCAL_OVERRIDES_PACKAGES := \
#	SetupWizard \
#	Provision  \
#	ManagedProvisioning

include $(BUILD_PACKAGE)
