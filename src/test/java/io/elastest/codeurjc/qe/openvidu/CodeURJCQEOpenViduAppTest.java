package io.elastest.codeurjc.qe.openvidu;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

public class CodeURJCQEOpenViduAppTest extends BaseTest {
    public static int CURRENT_SESSIONS = 0;
    private CountDownLatch waitForSessionReadyLatch;

    @Test
    public void loadTest(TestInfo info) throws Exception {
        startBrowsers(info);
    }

    public void startBrowsers(TestInfo info) throws Exception {
        CURRENT_SESSIONS++;
        waitForSessionReadyLatch = new CountDownLatch(USERS_BY_SESSION);

        final List<Runnable> browserThreads = new ArrayList<>();
        // Start N browsers
        for (int i = 0; i < USERS_BY_SESSION; i++) {
            final String userId = "user-" + CURRENT_SESSIONS + "-" + i;
            browserThreads.add(() -> {
                try {
                    this.startBrowser(info, userId);
                } catch (Exception e) {
                    // TODO: handle exception
                }
            });
        }

        sessionBrowserThreads.put(CURRENT_SESSIONS + "", browserThreads);

        for (Runnable r : browserThreads) {
            r.run();
        }

        if (CURRENT_SESSIONS < MAX_SESSIONS) {
            waitForSessionReadyLatch.await();

            // Start new session
            this.startBrowsers(info);
        } else {
            logger.info("Maximum sessions reached: {}", MAX_SESSIONS);
        }

    }

    public void startBrowser(TestInfo info, String userId)
            throws MalformedURLException, TimeoutException {
        String testName = info.getTestMethod().get().getName();
        WebDriver driver;
        if (EUS_URL == null) {
            driver = new ChromeDriver();
        } else {
            logger.info("Using ElasTest EUS URL: {}", EUS_URL);
            DesiredCapabilities caps = DesiredCapabilities.chrome();

            caps.setCapability("testName", testName);

            // AWS capabilities for browsers
            caps.setCapability("awsConfig", awsConfig);

            driver = new RemoteWebDriver(new URL(EUS_URL), caps);
        }
        BrowserClient browserClient = new BrowserClient(driver);
        browserClientList.add(browserClient);

        String publicUrl = OPENVIDU_SUT_URL
                + (OPENVIDU_SUT_URL.endsWith("/") ? "" : "/");

        browserClient.getDriver()
                .get(OPENVIDU_WEBAPP_URL + "?publicurl=" + publicUrl
                        + "&secret=" + OPENVIDU_SECRET + "&sessionId="
                        + CURRENT_SESSIONS + "&userId=" + userId);

        browserClient.startEventPolling();

        browserClient.waitUntilEventReaches("connectionCreated",
                USERS_BY_SESSION);
        browserClient.waitUntilEventReaches("accessAllowed", 1);
        browserClient.waitUntilEventReaches("streamCreated", USERS_BY_SESSION);

        browserClient.stopEventPolling();
        waitForSessionReadyLatch.countDown();
    }

}
