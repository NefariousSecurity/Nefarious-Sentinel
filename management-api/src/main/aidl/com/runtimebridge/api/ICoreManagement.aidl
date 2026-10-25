package com.runtimebridge.api;

interface ICoreManagement {
    int getProtocolVersion();
    boolean isDeviceOwner();
    int getAuthorityMode();

    boolean isBetaBuild();
    int getBetaLifecycleState();
    boolean isBetaDestructiveSuppressed();
    boolean isBetaRearmRequired();
    long getBetaRemainingMs();
    int getBetaExpiredVersionCode();
    boolean isBetaLifecycleStateHealthy();
    int rearmBetaLifecycle(String pin);

    int getPinState();
    long getRemainingLockoutMs();

    int enrollPin(String pin);
    int authenticatePin(String pin);

    boolean isSessionAuthenticated();
    void logout();

    String beginSensorPermissionHandoff();
    boolean redeemSensorPermissionHandoff(String token);

    String beginSmsPermissionHandoff();
    boolean redeemSmsPermissionHandoff(String token);

    int removeStandardAdminSafely(String pin);

    boolean isUsbDebuggingProtectionEnabled();
    boolean isWirelessDebuggingProtectionEnabled();

    int setUsbDebuggingProtectionEnabled(boolean enabled);
    int setWirelessDebuggingProtectionEnabled(boolean enabled);

    boolean isEsimEraseEnabled();
    boolean isEsimEraseAvailable();
    int setEsimEraseEnabled(boolean enabled);

    boolean isUsbProtectionEnabled();
    boolean isUsbStrictModeEnabled();

    int getUsbConnectionState();

    boolean isUsbMaintenanceActive();
    long getUsbMaintenanceRemainingMs();
    int getUsbMaintenanceExtendsUsed();
    int getUsbMaintenanceMaxExtends();

    int setUsbProtectionEnabled(boolean enabled);
    int setUsbStrictModeEnabled(boolean enabled);

    int startUsbMaintenance();
    int extendUsbMaintenance();
    int cancelUsbMaintenance();

    int getDeadmanState();
    boolean isDeadmanEnabled();
    long getDeadmanIntervalMs();
    long getDeadmanExpiryWallClockMs();
    long getDeadmanRemainingMs();
    long getDeadmanMinIntervalMs();
    long getDeadmanMaxIntervalMs();

    int setDeadmanEnabled(boolean enabled);
    int setDeadmanIntervalMs(long intervalMs);
    int checkInDeadman();

    boolean isMotionEnabled();
    int getMotionOperationalState();
    int getMotionSensitivity();
    int getMotionMinSensitivity();
    int getMotionMaxSensitivity();
    int getMotionAction();
    boolean isMotionRebootAvailable();

    int setMotionEnabled(boolean enabled);
    int setMotionSensitivity(int sensitivity);
    int setMotionAction(int action);

    boolean isSmsEnabled();
    boolean hasSmsPermission();
    boolean isSmsSenderConfigured();
    String getSmsSender();
    boolean isSmsKeywordConfigured();
    boolean isSmsArmed();

    int getSmsSenderMaxLength();
    int getSmsKeywordMinLength();
    int getSmsKeywordMaxLength();

    int grantSmsPermission();
    int releaseSmsPermissionPolicy();

    int setSmsSender(String sender);
    int setSmsKeyword(String keyword);
    int setSmsEnabled(boolean enabled);

    int simulateSms(String sender, String body);
}
