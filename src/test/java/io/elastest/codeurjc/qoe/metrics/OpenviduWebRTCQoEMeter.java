package io.elastest.codeurjc.qoe.metrics;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.openqa.selenium.remote.SessionId;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.elastest.codeurjc.qe.openvidu.BrowserClient;
import io.elastest.codeurjc.qe.openvidu.CountDownLatchWithException;
import io.elastest.codeurjc.qe.openvidu.CountDownLatchWithException.AbortedException;
import io.elastest.codeurjc.qe.utils.RestClient;

public class OpenviduWebRTCQoEMeter extends QoEMeterBaseTest {
    final String fakeResourcesPathInBrowser = "/opt/openvidu/";

    private static CountDownLatchWithException waitForSessionReadyLatch;
    public static ExecutorService browserInitializationTaskExecutor = Executors
            .newCachedThreadPool();

    final RestClient restClient = new RestClient();

    @Test
    public void webRTCQoEMeter(TestInfo info)
            throws SessionNotCreatedException, TimeoutException, IOException {
        startBrowsers(info);

        // Get Videos
        BrowserClient user1Browser = browserClientList.get(0);
        BrowserClient user2Browser = browserClientList.get(1);
        try {
            // Get User1/User2 streamIds
            JsonArray user1SubscriberStreamIds = user1Browser.getSubscriberStreams();
            JsonArray user1PublisherStreamIds = user1Browser.getPublisherStreams();

            JsonArray user2SubscriberStreamIds = user2Browser.getSubscriberStreams();
            JsonArray user2PublisherStreamIds = user2Browser.getPublisherStreams();

            // Init and get user1/user2 localRecorderIds
            String user2InUser1LocalRecorderId = user1Browser
                    .initLocalRecorder(user1SubscriberStreamIds.get(0).getAsString());
            String user1InUser1LocalRecorderId = user1Browser
                    .initLocalRecorder(user1PublisherStreamIds.get(0).getAsString());

            String user1InUser2LocalRecorderId = user2Browser
                    .initLocalRecorder(user2SubscriberStreamIds.get(0).getAsString());
            String user2InUser2LocalRecorderId = user2Browser
                    .initLocalRecorder(user2PublisherStreamIds.get(0).getAsString());

            // Record and download Subscriber/Publisher videos
            recordAndDownloadUser1AndUser2Videos(user1Browser, user2Browser,
                    user1InUser1LocalRecorderId, user1InUser2LocalRecorderId,
                    user2InUser2LocalRecorderId, user2InUser1LocalRecorderId);

            // Get Subscriber/Publisher video paths

            String user1VideoPathInUser1Browser = getVideoPathByLocalRecorderId(
                    user1InUser1LocalRecorderId);

            String user2VideoPathInUser1Browser = getVideoPathByLocalRecorderId(
                    user2InUser1LocalRecorderId);

            String user2VideoPathInUser2Browser = getVideoPathByLocalRecorderId(
                    user2InUser2LocalRecorderId);

            String user1VideoPathInUser2Browser = getVideoPathByLocalRecorderId(
                    user1InUser2LocalRecorderId);

            // Process and generate metrics
            CountDownLatchWithException waitForQoEMetrics = new CountDownLatchWithException(2);

            final List<Runnable> browserThreads = new ArrayList<>();

            // Process and generate metrics of the user 1 video
            browserThreads.add(() -> {
                try {
                    startAndProcessVideoMetrics(user1Browser, user2Browser,
                            user1VideoPathInUser1Browser, user1VideoPathInUser2Browser);
                    waitForQoEMetrics.countDown();
                } catch (Exception e) {
                    logger.error("Error on process qoe metrics of user {} :{}",
                            user1Browser.getUserId(), e.getMessage());
                    waitForQoEMetrics.abort(e.getMessage());
                }
            });

            // Process and generate metrics of the user 2 video
            browserThreads.add(() -> {
                try {
                    startAndProcessVideoMetrics(user2Browser, user1Browser,
                            user2VideoPathInUser2Browser, user2VideoPathInUser1Browser);
                    waitForQoEMetrics.countDown();
                } catch (Exception e) {
                    logger.error("Error on process qoe metrics of user {} :{}",
                            user2Browser.getUserId(), e.getMessage());
                    waitForQoEMetrics.abort(e.getMessage());
                }
            });

            for (Runnable r : browserThreads) {
                browserInitializationTaskExecutor.execute(r);
            }

            try {
                logger.info("Waiting for QoE metrics generated and attached");
                waitForQoEMetrics.await();
                logger.info("All QoE metrics are generated and attached!");
            } catch (AbortedException e) {
                logger.error("Some QoE metrics has failed: {}", e.getMessage());
                throw new Exception("Some QoE metrics has failed: " + e.getMessage());
            }

            sleep(20000);

            // Browser 1
            sendWebRTCQoEMeterMetricsTime(user1Browser);

            // Browser 2
            sendWebRTCQoEMeterMetricsTime(user2Browser);

        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail(e.getMessage());
            waitForSessionReadyLatch.abort(e.getMessage());
        }

        logger.info("End");
        browserInitializationTaskExecutor.shutdown();

        try {
            logger.info("Await termination");
            browserInitializationTaskExecutor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            logger.error("Browsers threads could not be finished after 5 minutes");
            Assertions.fail(e.getMessage());
            return;
        }
    }

    private void sendWebRTCQoEMeterMetricsTime(BrowserClient userBrowser) throws Exception {
        List<JsonObject> events = userBrowser.getEventListByName("streamPlaying");

        if (events != null) {
            JsonObject event = events.get(0);
            if (event.get("content").toString() == "Publisher") {
                event = events.get(0);
            }

            Long startEmisionDate = Long.valueOf(event.get("date").getAsString());
            long startTime = startEmisionDate + FAKE_VIDEO_AND_AUDIO_PADDING_DURATION;
            userBrowser.sendWebRTCQoEMeterMetricsTime(EUS_URL,
                    userBrowser.getQoeServiceIds().get(0), startTime,
                    FAKE_VIDEO_AND_AUDIO_DURATION);
        }
    }

    private void startBrowsers(TestInfo info) throws TimeoutException, IOException {
        waitForSessionReadyLatch = new CountDownLatchWithException(USERS_BY_SESSION);

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
                    logger.error("Error on start browser of user {}: {}", userId, e.getMessage());
                    waitForSessionReadyLatch.abort(e.getMessage());
                }
            });
        }

        for (Runnable r : browserThreads) {
            browserInitializationTaskExecutor.execute(r);
        }

        try {
            logger.info("Waiting for all browsers are started");
            waitForSessionReadyLatch.await();
        } catch (AbortedException e) {
            final String msg = "Some browser has not started correctly: " + e.getMessage();
            logger.error(msg);
            Assertions.fail(msg);
            return;
        }

        logger.info("All browsers are now started!");

        openSutAndWaitForEventsInBrowsers();
    }

    public void startBrowser(TestInfo info, String userId)
            throws TimeoutException, IOException, SessionNotCreatedException {
        logger.info("Starting browser for user {} ", userId);
        String testName = info.getTestMethod().get().getName();
        int SESSION_ID = 1;

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

            capabilities.setCapability("testName", testName + "_" + userId.replaceAll("-", "_"));

            // This flag sets the video input (with padding)
            options.addArguments("--use-file-for-fake-video-capture=" + fakeResourcesPathInBrowser
                    + FAKE_VIDEO_WITH_PADDING_NAME);
            // This flag sets the audio input
            options.addArguments("--use-file-for-fake-audio-capture=" + fakeResourcesPathInBrowser
                    + FAKE_AUDIO_WITH_PADDING_NAME);

            HashMap<String, Object> chromePrefs = new HashMap<String, Object>();
            chromePrefs.put("profile.default_content_setting_values.automatic_downloads", 1);
            options.setExperimentalOption("prefs", chromePrefs);

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

        BrowserClient browserClient = new BrowserClient(driver, userId, SESSION_ID);
        browserClientList.add(browserClient);

        try {
            uploadFakeResourcesForTest(browserClient);
        } catch (Exception e) {
            String msg = "Error on upload fake resource for test on user " + userId + " session "
                    + SESSION_ID + ": " + e.getMessage();
            throw new IOException(msg);
        }
    }

    private void openSutAndWaitForEventsInBrowsers() {
        final List<Runnable> browserThreads = new ArrayList<>();
        waitForSessionReadyLatch = new CountDownLatchWithException(browserClientList.size());

        for (BrowserClient browserClient : browserClientList) {
            browserThreads.add(() -> {
                try {
                    this.openSutAndWaitForEvents(browserClient);
                    waitForSessionReadyLatch.countDown();
                } catch (TimeoutException | IOException | NullPointerException
                        | SessionNotCreatedException e) {
                    logger.error("Error on start browser of user {}: {}", browserClient.getUserId(),
                            e.getMessage());
                    waitForSessionReadyLatch.abort(e.getMessage());
                }
            });
        }

        for (Runnable r : browserThreads) {
            browserInitializationTaskExecutor.execute(r);
        }

        try {
            logger.info(
                    "Waiting for all browsers are fully ready (sut url opened and all events received)");
            waitForSessionReadyLatch.await();
        } catch (AbortedException e) {
            logger.error("Some browser does not have a stable session: {}", e.getMessage());
            Assertions.fail("Session did not reach stable status in timeout: " + e.getMessage());
            return;
        }

        logger.info("All browsers are now fully ready! (sut url opened and all events received)");
    }

    private void openSutAndWaitForEvents(BrowserClient browserClient)
            throws TimeoutException, IOException, SessionNotCreatedException {
        String completeUrl = OPENVIDU_PROTOCOL_AND_HOST_URL + ":5000?publicurl="
                + OPENVIDU_PROTOCOL_AND_HOST_URL + ":4443/&secret=" + OPENVIDU_SECRET
                + "&sessionId=" + browserClient.getSession() + "&userId="
                + browserClient.getUserId();
        logger.info("Opening url: {}", completeUrl);

        browserClient.getDriver().get(completeUrl);
        browserClient.startEventPolling(true, false);

        try {
            browserClient.waitForEvent("connectionCreated", USERS_BY_SESSION);
            browserClient.waitForEvent("accessAllowed", 1);
            browserClient.waitForEvent("streamCreated", USERS_BY_SESSION);
            browserClient.waitForEvent("streamPlaying", 2);

            browserClient.stopEventPolling();
        } catch (TimeoutException | NullPointerException e) {
            String msg = "Error on waiting for events on user " + browserClient.getUserId()
                    + " session " + browserClient.getSession() + ": " + e.getMessage();
            if (e instanceof TimeoutException) {
                throw new TimeoutException(msg);
            } else if (e instanceof NullPointerException) {
                throw new NullPointerException(msg);
            } else {
                throw e;
            }
        }
    }

    public void uploadFakeResourcesForTest(BrowserClient browser) throws Exception {
        browser.uploadFileFromUrl(EUS_URL, FAKE_VIDEO_URL, fakeResourcesPathInBrowser,
                FAKE_VIDEO_WITH_PADDING_NAME);
        browser.uploadFileFromUrl(EUS_URL, FAKE_AUDIO_URL, fakeResourcesPathInBrowser,
                FAKE_AUDIO_WITH_PADDING_NAME);
    }

    public String getVideoPathByLocalRecorderId(String localRecorderId) {
        final String fileName = localRecorderId + ".webm";
        return "/home/ubuntu/Downloads/" + fileName;
    }

    private void startAndProcessVideoMetrics(BrowserClient publisherBrowser,
            BrowserClient subscriberBrowser, String originalVideoInPublisherBrowser,
            String receivedVideoInSubscriber) throws Exception {
        logger.info("Starting process of user {} video metrics", publisherBrowser.getUserId());

        // Start WebRTCQoEMeter service in EUS
        String qoeServiceId = startWebRTCQoEMeter(receivedVideoInSubscriber,
                originalVideoInPublisherBrowser, publisherBrowser, subscriberBrowser);

        publisherBrowser.getQoeServiceIds().add(qoeServiceId);

        // Wait for CSV and Get
        Map<String, byte[]> csvMap = waitForCSV(qoeServiceId, publisherBrowser);

        if (csvMap == null || csvMap.size() == 0) {
            final String message = "Csv files List is null or empty for user "
                    + publisherBrowser.getUserId();
            throw new Exception(message);
        }

        // Get Metrics
        Map<String, Double> metrics = getMetric(qoeServiceId, publisherBrowser);

        if (metrics == null || metrics.size() == 0) {
            final String message = "Metric files List is null or empty for user "
                    + publisherBrowser.getUserId();
            throw new Exception(message);
        }
    }

    private void recordAndDownloadUser1AndUser2Videos(BrowserClient user1Browser,
            BrowserClient user2Browser, String user1InUser1LocalRecorder,
            String user1InUser2LocalRecorder, String user2InUser2LocalRecorder,
            String user2InUser1LocalRecorder) throws Exception {
        CountDownLatchWithException waitForRecording = new CountDownLatchWithException(4);

        final List<Runnable> browserThreads = new ArrayList<>();

        recordAndDownloadBrowserVideoInNewThread(browserThreads, waitForRecording,
                "Error on record and download browser video of user 1 in user 1 browser",
                user1Browser, user1InUser1LocalRecorder);

        recordAndDownloadBrowserVideoInNewThread(browserThreads, waitForRecording,
                "Error on record and download browser video of user 2 in user 1 browser",
                user1Browser, user2InUser1LocalRecorder);

        recordAndDownloadBrowserVideoInNewThread(browserThreads, waitForRecording,
                "Error on record and download browser video of user 2 in user 2 browser",
                user2Browser, user2InUser2LocalRecorder);

        recordAndDownloadBrowserVideoInNewThread(browserThreads, waitForRecording,
                "Error on record and download browser video of user 1 in user 2 browser",
                user2Browser, user1InUser2LocalRecorder);

        for (Runnable r : browserThreads) {
            browserInitializationTaskExecutor.execute(r);
        }

        try {
            logger.info("Waiting for all recordings are done and downloaded");
            waitForRecording.await();
            logger.info("All recordings are done!");
        } catch (AbortedException e) {
            logger.error("Some recording has failed: {}", e.getMessage());
            Assertions.fail("Some recording has failed: " + e.getMessage());
            return;
        }

    }

    public String startWebRTCQoEMeter(String presenterPath, String viewerPath,
            BrowserClient user1BrowserClient, BrowserClient user2BrowserClient) throws Exception {
        if (EUS_URL != null) {
            logger.info("Starting WebRTC QoE Meter for user {}", user1BrowserClient.getUserId());

            SessionId user1SessionId = ((RemoteWebDriver) user1BrowserClient.getDriver())
                    .getSessionId();
            SessionId user2SessionId = ((RemoteWebDriver) user2BrowserClient.getDriver())
                    .getSessionId();

            String url = EUS_URL.endsWith("/") ? EUS_URL : EUS_URL + "/";
            url += "session/" + user1SessionId.toString() + "/webrtc/qoe/meter/start";

            url += "?presenterPath=" + presenterPath;
            url += "&presenterSessionId=" + user1SessionId;
            url += "&viewerPath=" + viewerPath;
            url += "&viewerSessionId=" + user2SessionId;

            byte[] response = restClient.sendGet(url);

            String id = new String(response);

            logger.info("Started WebRTC QoE Meter for user {} successfully! Id {}",
                    user1BrowserClient.getUserId(), id);

            return id;
        }
        return null;
    }

    private void recordAndDownloadBrowserVideo(BrowserClient browser, String localRecorderId)
            throws Exception {
        browser.startRecording(localRecorderId);

        // seconds
        final int WAIT_TIME = 50;
        long endWaitTime = System.currentTimeMillis() + WAIT_TIME * 1000;

        // Wait
        while (System.currentTimeMillis() < endWaitTime) {
            sleep(1000);
        }

        browser.stopRecording(localRecorderId);

        browser.downloadRecording(localRecorderId);
    }

    private void recordAndDownloadBrowserVideoInNewThread(List<Runnable> browserThreads,
            CountDownLatchWithException waitForRecording, String errorMsg, BrowserClient browser,
            String localRecorderId) {
        browserThreads.add(() -> {
            try {
                recordAndDownloadBrowserVideo(browser, localRecorderId);
                waitForRecording.countDown();
            } catch (Exception e) {
                logger.error("{}: {}", errorMsg, e.getMessage());
                waitForRecording.abort(e.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    public Map<String, byte[]> waitForCSV(String qoeServiceId, BrowserClient browserClient)
            throws Exception {
        if (EUS_URL != null) {
            // 40min
            int timeoutSeconds = 2400;
            long endWaitTime = System.currentTimeMillis() + timeoutSeconds * 1000;

            final String userId = browserClient.getUserId();
            logger.info("Waiting for CSV generated in WebRTC QoE Meter (timeout {}s) for user {}",
                    timeoutSeconds, userId);

            SessionId sessionId = ((RemoteWebDriver) browserClient.getDriver()).getSessionId();

            String urlPrefix = EUS_URL.endsWith("/") ? EUS_URL : EUS_URL + "/";
            urlPrefix += "session/" + sessionId.toString() + "/webrtc/qoe/meter/" + qoeServiceId;
            String url = urlPrefix + "/csv/isgenerated";

            String response;

            do {
                response = new String(restClient.sendGet(url));
                logger.info("CSV not generated yet for user {}, waiting... Response: {}", userId,
                        response);
                sleep(5000);
            } while (System.currentTimeMillis() < endWaitTime && !"true".equals(response));
            logger.info("CSV Generated for user {} successfully", userId);

            url = urlPrefix + "/csv";
            response = new String(restClient.sendGet(url));
            logger.info("CSV RESPONSE for user {}: {}", userId, response);
            Map<String, byte[]> csvFiles = null;
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                csvFiles = (Map<String, byte[]>) objectMapper.readValue(response,
                        new TypeReference<Map<String, byte[]>>() {
                        });

                return csvFiles;
            } catch (IOException e) {
                throw new Exception("Error during CSV list conversion for user " + userId + ": "
                        + e.getMessage());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Double> getMetric(String qoeServiceId, BrowserClient browserClient)
            throws Exception {
        if (EUS_URL != null) {
            logger.info("Getting metric generated in WebRTC QoE Meter");

            SessionId sessionId = ((RemoteWebDriver) browserClient.getDriver()).getSessionId();

            String urlPrefix = EUS_URL.endsWith("/") ? EUS_URL : EUS_URL + "/";
            urlPrefix += "session/" + sessionId.toString() + "/webrtc/qoe/meter/" + qoeServiceId;

            String url = urlPrefix + "/metric";
            String response = new String(restClient.sendGet(url));
            logger.info("CSV RESPONSE: {}", response);
            Map<String, Double> metrics = null;
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                metrics = (Map<String, Double>) objectMapper.readValue(response,
                        new TypeReference<Map<String, Double>>() {
                        });

                return metrics;
            } catch (IOException e) {
                throw new Exception("Error during CSV list conversion: " + e.getMessage());
            }
        }
        return null;
    }
}
