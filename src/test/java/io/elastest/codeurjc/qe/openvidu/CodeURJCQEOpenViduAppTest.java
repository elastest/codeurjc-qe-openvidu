package io.elastest.codeurjc.qe.openvidu;

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

public class CodeURJCQEOpenViduAppTest extends ElastestBaseTest {

    @Test
    public void loadTest(TestInfo info) throws Exception {

    }

    public void startBrowser(TestInfo info) throws MalformedURLException {
        String testName = info.getTestMethod().get().getName();
        WebDriver driver;
        if (eusURL == null) {
            driver = new ChromeDriver();
        } else {
            logger.info("Using ElasTest EUS URL: {}", eusURL);
            DesiredCapabilities caps = DesiredCapabilities.chrome();

            caps.setCapability("testName", testName);

            // AWS capabilities for browsers
            caps.setCapability("awsConfig", awsConfig);

            driver = new RemoteWebDriver(new URL(eusURL), caps);
        }

        driverList.add(driver);

        driver.get(openviduWebAppUrl);
    }

}
