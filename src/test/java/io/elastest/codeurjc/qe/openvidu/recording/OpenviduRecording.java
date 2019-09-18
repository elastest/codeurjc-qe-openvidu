package io.elastest.codeurjc.qe.openvidu.recording;

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
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;

import io.elastest.codeurjc.qe.openvidu.BrowserClient;
import io.elastest.codeurjc.qe.openvidu.CountDownLatchWithException;
import io.elastest.codeurjc.qe.openvidu.CountDownLatchWithException.AbortedException;

public class OpenviduRecording
        extends io.elastest.codeurjc.qe.openvidu.webrtc.WebRtcBaseTest {

    private static CountDownLatchWithException waitForSessionReadyLatch;
    public static ExecutorService browserInitializationTaskExecutor = Executors
            .newCachedThreadPool();

    @Test
    public void recordAndDownloadSubscriberVideo(TestInfo info)
            throws SessionNotCreatedException, TimeoutException, IOException {
        startBrowsers(info);

        // Get streams
        BrowserClient firstBrowser = browserClientList.get(0);
        List<String> localRecorderIds = new ArrayList<>();
        try {
            List<JsonObject> subscriberStreams = firstBrowser
                    .getOnlySubscriberStreams();

            for (JsonObject stream : subscriberStreams) {
                if (stream != null) {
                    String localRecorderId = firstBrowser
                            .initLocalRecorder(stream);
                    localRecorderIds.add(localRecorderId);
                    firstBrowser.startRecording(localRecorderId);
                }
            }

            // seconds
            final int WAIT_TIME = 60;
            long endWaitTime = System.currentTimeMillis() + WAIT_TIME * 1000;

            // Wait
            while (System.currentTimeMillis() < endWaitTime) {
                sleep(1000);
            }

            for (String localRecorderId : localRecorderIds) {
                try {
                    firstBrowser.stopRecording(localRecorderId);
                    firstBrowser.downloadRecording(localRecorderId);
                } catch (Exception e1) {
                    String msg = "Error on stop or download recording for localRecorder "
                            + localRecorderId + ": " + e1.getMessage();
                    logger.error(msg);
                    Assertions.fail(msg);
                    waitForSessionReadyLatch.abort(msg);
                }
            }
            sleep(20000);
        } catch (Exception e) {
            String msg = "Error on init localRecorder or start recording: "
                    + e.getMessage();
            logger.error(msg);
            Assertions.fail(msg);
            waitForSessionReadyLatch.abort(msg);
        }

        logger.info("End");
        browserInitializationTaskExecutor.shutdown();

        try {
            logger.info("Await termination");
            browserInitializationTaskExecutor.awaitTermination(5,
                    TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            logger.error(
                    "Browsers threads could not be finished after 5 minutes");
            Assertions.fail(e.getMessage());
            return;
        }
    }

    private void startBrowsers(TestInfo info)
            throws TimeoutException, IOException {
        waitForSessionReadyLatch = new CountDownLatchWithException(
                USERS_BY_SESSION);

        final List<Runnable> browserThreads = new ArrayList<>();
        // Start N browsers
        for (int i = 0; i < USERS_BY_SESSION; i++) {
            final String userId = "user-" + (i + 1);
            browserThreads.add(() -> {
                try {
                    this.startBrowser(info, userId);
                    waitForSessionReadyLatch.countDown();
                } catch (TimeoutException | IOException | NullPointerException
                        | SessionNotCreatedException e) {
                    logger.error("Error on start browser of user {}: {}",
                            userId, e.getMessage());
                    waitForSessionReadyLatch.abort(e.getMessage());
                }
            });
        }

        for (Runnable r : browserThreads) {
            browserInitializationTaskExecutor.execute(r);
        }

        try {
            logger.info("Waiting for all browsers  are ready");
            waitForSessionReadyLatch.await();
        } catch (AbortedException e) {
            logger.error("Some browser does not have a stable session: {}",
                    e.getMessage());
            Assertions.fail("Session did not reach stable status in timeout: "
                    + e.getMessage());
            return;
        }

        logger.info("All browsers of session {} are now ready!");
    }

    @SuppressWarnings("unchecked")
    public void startBrowser(TestInfo info, String userId)
            throws TimeoutException, IOException, SessionNotCreatedException {
        logger.info("Starting browser for user {} ", userId);

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
            // Create browser session
            try {
                driver = new RemoteWebDriver(new URL(EUS_URL), capabilities);
            } catch (SessionNotCreatedException e) {
                String msg = "Error on create new RemoteWebDriver (SessionNotCreatedException) => "
                        + e.getMessage();
                throw new SessionNotCreatedException(msg);
            }
        }
        int SESSION_ID = 1;
        BrowserClient browserClient = new BrowserClient(driver, userId,
                SESSION_ID);
        browserClientList.add(browserClient);

        String publicUrl = OPENVIDU_SUT_URL
                + (OPENVIDU_SUT_URL.endsWith("/") ? "" : "/");

        browserClient.getDriver()
                .get(OPENVIDU_WEBAPP_URL + "?publicurl=" + publicUrl
                        + "&secret=" + OPENVIDU_SECRET + "&sessionId="
                        + SESSION_ID + "&userId=" + userId);

    }

}
