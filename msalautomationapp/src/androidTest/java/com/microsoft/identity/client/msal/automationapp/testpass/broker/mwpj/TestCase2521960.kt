//  Copyright (c) Microsoft Corporation.
//  All rights reserved.
//
//  This code is licensed under the MIT License.
//
//  Permission is hereby granted, free of charge, to any person obtaining a copy
//  of this software and associated documentation files(the "Software"), to deal
//  in the Software without restriction, including without limitation the rights
//  to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
//  copies of the Software, and to permit persons to whom the Software is
//  furnished to do so, subject to the following conditions :
//
//  The above copyright notice and this permission notice shall be included in
//  all copies or substantial portions of the Software.
//
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//  THE SOFTWARE.
package com.microsoft.identity.client.msal.automationapp.testpass.broker.mwpj

import com.microsoft.identity.client.msal.automationapp.R
import com.microsoft.identity.client.msal.automationapp.testpass.broker.AbstractMsalBrokerTest
import com.microsoft.identity.client.ui.automation.annotations.LocalBrokerHostDebugUiTest
import com.microsoft.identity.client.ui.automation.annotations.SupportedBrokers
import com.microsoft.identity.client.ui.automation.broker.BrokerHost
import com.microsoft.identity.client.ui.automation.rules.LoadLabUserTestRule
import com.microsoft.identity.labapi.utilities.client.ILabAccount
import com.microsoft.identity.labapi.utilities.client.LabQuery
import com.microsoft.identity.labapi.utilities.constants.AzureEnvironment
import com.microsoft.identity.labapi.utilities.constants.TempUserType
import com.microsoft.identity.labapi.utilities.constants.UserType
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

// https://identitydivision.visualstudio.com/Engineering/_workitems/edit/2521960
// [MWPJ] Device registration entry migration (different upn - same tenant)
@LocalBrokerHostDebugUiTest
@SupportedBrokers(brokers = [BrokerHost::class])
class TestCase2521960 : AbstractMsalBrokerTest() {

    private lateinit var mUsGovAccount: ILabAccount
    private lateinit var mLabAccount2: ILabAccount
    private lateinit var mBrokerHostApp: BrokerHost

    @get:Rule
    val loadUsGovLabAccountUserRule: TestRule = LoadLabUserTestRule(getAdditionalLabQuery())
    @get:Rule
    val loadAdditionalLabUserRule: TestRule = LoadLabUserTestRule(TempUserType.BASIC)

    @Test
    fun test_2521960() {
        // Register 2 accounts from different tenants
        mBrokerHostApp.multipleWpjApiFragment.performDeviceRegistration(mUsGovAccount.username, mUsGovAccount.password)
        mBrokerHostApp.multipleWpjApiFragment.performDeviceRegistration(mLabAccount.username, mLabAccount.password)
        val deviceRegistrationRecords = mBrokerHostApp.multipleWpjApiFragment.allRecords
        Assert.assertEquals(2, deviceRegistrationRecords.size)

        // Unregister the device from the legacy space
        mBrokerHostApp.multipleWpjApiFragment.unregister(mUsGovAccount.username)

        // Verify that the device is unregistered for the legacy API
        val errorMessage = mBrokerHostApp.accountUpn
        Assert.assertNotNull(errorMessage)
        Assert.assertTrue(errorMessage!!.contains("Device is not Workplace Joined"))

        // Verify that the device is still registered for the second account using the MWPJ API.
        val recordInExtendedSpace = mBrokerHostApp.multipleWpjApiFragment.getRecordByUpn(mLabAccount.username)
        Assert.assertNotNull(recordInExtendedSpace)

        // Register the device with the second account (same tenant different upn) using the legacy API
        mBrokerHostApp.performDeviceRegistration(mLabAccount2.username, mLabAccount2.password)

        // Verify that the device is registered with the second account using the legacy API
        val legacyAccountMessage = mBrokerHostApp.accountUpn
        Assert.assertNotNull(legacyAccountMessage)
        Assert.assertTrue(legacyAccountMessage!!.contains(mLabAccount2.username))

        // Verify the entry in the extended space was removed and replaced with the entry from the second account.
        val recordInLegacy = mBrokerHostApp.multipleWpjApiFragment.allRecords
        Assert.assertEquals(1, recordInLegacy.size)
        Assert.assertEquals(recordInExtendedSpace["TenantId"], recordInLegacy[0]["TenantId"])
        Assert.assertNotEquals(recordInExtendedSpace["Upn"], recordInLegacy[0]["Upn"])
        Assert.assertNotEquals(recordInExtendedSpace["DeviceId"], recordInLegacy[0]["DeviceId"])
    }

    override fun getLabQuery(): LabQuery {
        return LabQuery.builder()
                .userType(UserType.CLOUD)
                .build()
    }

    override fun getTempUserType(): TempUserType? {
        return null
    }

    private fun getAdditionalLabQuery(): LabQuery {
        return LabQuery.builder()
                .userType(UserType.CLOUD)
                .azureEnvironment(AzureEnvironment.AZURE_US_GOVERNMENT)
                .build()
    }

    @Before
    fun before() {
        mUsGovAccount = (loadUsGovLabAccountUserRule as LoadLabUserTestRule).labAccount
        mLabAccount2 = (loadAdditionalLabUserRule as LoadLabUserTestRule).labAccount
        Assert.assertEquals(
                "Lab accounts are not in the same tenant",
                mLabAccount2.homeTenantId, mLabAccount.homeTenantId
        )
        Assert.assertNotEquals(
                "Lab accounts are the same",
                mLabAccount2.username, mLabAccount.username
        )
        mBrokerHostApp = broker as BrokerHost
        mBrokerHostApp.enableMultipleWpj()
    }

    /**
     * Get the scopes that can be used for an acquire token test.
     *
     * @return A string array consisting of OAUTH2 Scopes
     */
    override fun getScopes(): Array<String> {
        return arrayOf("User.read")
    }

    /**
     * Get the authority url that can be used for an acquire token test.
     *
     * @return A string representing the url for an authority that can be used as token issuer
     */
    override fun getAuthority(): String {
        return mApplication.configuration.defaultAuthority.authorityURL.toString()
    }

    /**
     * The MSAL config file that should be used to create a PublicClientApplication for the test.
     *
     * @return config file resource id
     */
    override fun getConfigFileResourceId(): Int {
        return R.raw.msal_config_default
    }

}
