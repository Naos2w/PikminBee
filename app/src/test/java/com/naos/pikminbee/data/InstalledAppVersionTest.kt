package com.naos.pikminbee.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledAppVersionTest {
    @Test fun formatsInstalledVersionWithoutHardCodedReleaseNumbers() {
        assertEquals("0.23.1 UAT", InstalledAppVersion("0.23.1",1200L,"UAT").displayName)
        assertEquals("0.23.1 UAT", InstalledAppVersion("0.23.1-uat",1200L,"UAT").displayName)
        assertEquals("0.23.2-dev", InstalledAppVersion("0.23.2-dev",23002L,"DEV").displayName)
        assertEquals("版本待確認", InstalledAppVersion(null,null,"UAT").displayName)
        assertEquals("1.0.0", InstalledAppVersion("1.0.0",1000000L,"PROD").displayName)
    }

    @Test fun productionIsSeparateFromExistingInstallChannels() {
        assertEquals("DEV", InstalledAppVersion.environmentFor("com.naos.pikminbee"))
        assertEquals("UAT", InstalledAppVersion.environmentFor("com.naos.pikminbee.uat"))
        assertEquals("PROD", InstalledAppVersion.environmentFor("com.naos.pikminbee.prod"))
    }
}
