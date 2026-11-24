package com.naos.pikminbee.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledAppVersionTest {
    @Test fun formatsInstalledVersionWithoutHardCodedReleaseNumbers() {
        assertEquals("0.23.1 UAT", InstalledAppVersion("0.23.1",1200L,"UAT").displayName)
        assertEquals("0.23.1 UAT", InstalledAppVersion("0.23.1-uat",1200L,"UAT").displayName)
        assertEquals("0.23.2-dev", InstalledAppVersion("0.23.2-dev",23002L,"DEV").displayName)
        assertEquals("版本待確認", InstalledAppVersion(null,null,"UAT").displayName)
    }
}
