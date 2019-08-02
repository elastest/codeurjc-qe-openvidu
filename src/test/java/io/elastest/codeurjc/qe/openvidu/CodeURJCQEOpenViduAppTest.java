package io.elastest.codeurjc.qe.openvidu;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.elastest.codeurjc.qe.openvidu.CountDownLatchWithException.AbortedException;

public class CodeURJCQEOpenViduAppTest extends BaseTest {
    public static int CURRENT_SESSIONS = 0;
    private static CountDownLatchWithException waitForSessionReadyLatch;
    public static ExecutorService browserInitializationTaskExecutor = Executors
            .newCachedThreadPool();

    @Test
    public void loadTest(TestInfo info) throws Exception {
        logger.info("Users by session {}. Max Sessions {}", USERS_BY_SESSION,
                MAX_SESSIONS);
        startBrowsers(info);
        logger.info("Test finished!");
    }

    public void startBrowsers(TestInfo info) throws Exception {
        CURRENT_SESSIONS++;
        waitForSessionReadyLatch = new CountDownLatchWithException(
                USERS_BY_SESSION);

        try {
            monitoringManager.sendAtomicMetric("participants", "uds",
                    CURRENT_SESSIONS * USERS_BY_SESSION + "", "openvidu");
        } catch (Exception e) {
            logger.error("Cannot send metric nº of participants: {}",
                    e.getMessage());
        }

        final List<Runnable> browserThreads = new ArrayList<>();
        // Start N browsers
        for (int i = 0; i < USERS_BY_SESSION; i++) {
            final String userId = "user-" + CURRENT_SESSIONS + "-" + i;
            browserThreads.add(() -> {
                try {
                    this.startBrowser(info, userId);
                } catch (Exception e) {
                    logger.error(
                            "Error on start browser of user {} at session {}: {}",
                            userId, CURRENT_SESSIONS, e.getMessage());
                    waitForSessionReadyLatch.abort(e.getMessage());
                }
            });
        }

        sessionBrowserThreads.put(CURRENT_SESSIONS + "", browserThreads);

        for (Runnable r : browserThreads) {
            browserInitializationTaskExecutor.execute(r);
        }

        if (CURRENT_SESSIONS < MAX_SESSIONS) {
            try {
                waitForSessionReadyLatch.await();
            } catch (AbortedException e) {
                logger.error("Some browser does not have a stable session: {}",
                        e.getMessage());
                Assertions
                        .fail("Session did not reach stable status in timeout: "
                                + e.getMessage());
                return;
            }
            // Start new session
            this.startBrowsers(info);
        } else {
            logger.info("Maximum sessions reached: {}", MAX_SESSIONS);
        }

        browserInitializationTaskExecutor.shutdown();
        try {
            browserInitializationTaskExecutor.awaitTermination(5,
                    TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            logger.error(
                    "Browsers threads could not be finished after 5 minutes");
            Assertions.fail(e.getMessage());
            return;
        }

    }

    @SuppressWarnings("unchecked")
    public void startBrowser(TestInfo info, String userId)
            throws TimeoutException, JsonParseException, JsonMappingException,
            IOException {
        logger.info("Starting browser for user {} and session {}", userId,
                CURRENT_SESSIONS);

        String testName = info.getTestMethod().get().getName();

        WebDriver driver;
        DesiredCapabilities capabilities = DesiredCapabilities.chrome();
        ChromeOptions options = new ChromeOptions();
        // This flag avoids to grant the user media
        options.addArguments("--use-fake-ui-for-media-stream");
        // This flag fakes user media with synthetic video
        options.addArguments("--use-fake-device-for-media-stream");
        // This flag allows to load fake media files from host
        options.addArguments("--allow-file-access-from-files");
        capabilities.setAcceptInsecureCerts(true);
        if (EUS_URL == null) {
            capabilities.setCapability(ChromeOptions.CAPABILITY, options);

            driver = new ChromeDriver(options);
        } else {
            logger.info("Using ElasTest EUS URL: {}", EUS_URL);

            capabilities.setCapability("testName",
                    testName + "_" + userId.replaceAll("-", "_"));
            // AWS capabilities for browsers

            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> awsConfigMap = mapper
                    .readValue(awsConfig.toString(), Map.class);

            capabilities.setCapability("awsConfig", awsConfigMap);

            // This flag sets the video input
            options.addArguments("--use-file-for-fake-video-capture="
                    + "/opt/openvidu/fakevideo.y4m");
            // This flag sets the audio input
            options.addArguments("--use-file-for-fake-audio-capture="
                    + "/opt/openvidu/fakeaudio.wav");
            capabilities.setCapability(ChromeOptions.CAPABILITY, options);
            capabilities.setCapability("elastestTimeout", 3600);

            String browserVersion = System.getProperty("browserVersion");
            if (browserVersion != null) {
                capabilities.setVersion(browserVersion);
            }

            driver = new RemoteWebDriver(new URL(EUS_URL), capabilities);
        }
        BrowserClient browserClient = new BrowserClient(driver, userId,
                CURRENT_SESSIONS);
        browserClientList.add(browserClient);

        String publicUrl = OPENVIDU_SUT_URL
                + (OPENVIDU_SUT_URL.endsWith("/") ? "" : "/");

        browserClient.getDriver()
                .get(OPENVIDU_WEBAPP_URL + "?publicurl=" + publicUrl
                        + "&secret=" + OPENVIDU_SECRET + "&sessionId="
                        + CURRENT_SESSIONS + "&userId=" + userId);

        browserClient.startEventPolling();

        try {
            browserClient.waitForEvent("connectionCreated", USERS_BY_SESSION);
            browserClient.waitForEvent("accessAllowed", 1);
            browserClient.waitForEvent("streamCreated", USERS_BY_SESSION);
            browserClient.stopEventPolling();
        } catch (TimeoutException | NullPointerException e) {
            String msg = "Error on waiting for events on user " + userId
                    + " session " + CURRENT_SESSIONS + ": " + e.getMessage();
            logger.error(msg);
            waitForSessionReadyLatch.abort(msg);
            throw e;
        }
        waitForSessionReadyLatch.countDown();

    }

}
