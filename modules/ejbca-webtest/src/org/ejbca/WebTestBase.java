/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.ejbca;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.firefox.internal.ProfilesIni;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.ejbca.utils.ConfigurationConstants;
import org.ejbca.utils.ConfigurationHolder;

import java.util.concurrent.TimeUnit;

/**
 * Base class to be used by all automated Selenium tests. Should be extended for each test case
 * @version $Id$
 *
 */
public abstract class WebTestBase {

    private static ConfigurationHolder config;

    private static String ejbcaDomain;
    private static String ejbcaSslPort;
    private static String ejbcaPort;

    private static WebDriver webDriver;
    public static WebDriverWait webDriverWait;

    /**
     * Sets up firefox driver and firefox profile is certificate is required
     * @param requireCert if certificate is required
     * @param profile browser profile to use. Defined in ConfigurationConstants, null will use default profile.
     */
    public static void setUp(boolean requireCert, String profile) {
        // Init properties
        setGlobalConstants();
        // Init gecko driver
        config.setGeckoDriver();
        if (requireCert) {
            ProfilesIni allProfiles = new ProfilesIni();
            FirefoxProfile firefoxProfile;
            if (profile != null) {
                firefoxProfile = allProfiles.getProfile(config.getProperty(profile));
            } else {
                firefoxProfile = allProfiles.getProfile(config.getProperty(ConfigurationConstants.PROFILE_FIREFOX_DEFAULT));
            }
            firefoxProfile.setPreference("security.default_personal_cert", "Select Automatically");
            FirefoxOptions firefoxOptions = new FirefoxOptions();
            firefoxOptions.setProfile(firefoxProfile);
            firefoxOptions.setAcceptInsecureCerts(true);
            webDriver = new FirefoxDriver(firefoxOptions);
        } else {
            webDriver = new FirefoxDriver();
        }

        webDriver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        webDriverWait = new WebDriverWait(webDriver, 5, 50);
    }

    public static void tearDown() {
        // Destroy web driver & close all windows
        webDriver.quit();
    }

    private static void setGlobalConstants() {
        config = new ConfigurationHolder();
        config.loadAllProperties();
        ejbcaDomain = config.getProperty(ConfigurationConstants.APPSERVER_DOMAIN);
        ejbcaPort = config.getProperty(ConfigurationConstants.APPSERVER_PORT);
        ejbcaSslPort = config.getProperty(ConfigurationConstants.APPSERVER_PORT_SSL);
    }

    public String getCaName() {
        return config.getProperty(ConfigurationConstants.EJBCA_CANAME);
    }

    public String getCaDn() {
        return config.getProperty(ConfigurationConstants.EJBCA_CADN);
    }

    public String getPublicWebUrl() {
        String ret = "http://" + ejbcaDomain + ":" + ejbcaPort + "/ejbca/";
        return ret;
    }

    public String getAdminWebUrl() {
        return "https://" + ejbcaDomain + ":" + ejbcaSslPort + "/ejbca/adminweb/";
    }

    public String getRaWebUrl() {
        return "https://" + ejbcaDomain + ":" + ejbcaSslPort + "/ejbca/ra/";
    }
    
    public static WebDriver getWebDriver() {
        return webDriver;
    }
}
