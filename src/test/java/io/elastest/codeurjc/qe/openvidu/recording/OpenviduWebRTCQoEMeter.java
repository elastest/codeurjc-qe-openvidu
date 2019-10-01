package io.elastest.codeurjc.qe.openvidu.recording;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import io.elastest.codeurjc.qe.openvidu.BrowserClient;
import io.elastest.codeurjc.qe.openvidu.CountDownLatchWithException;
import io.elastest.codeurjc.qe.openvidu.CountDownLatchWithException.AbortedException;
import io.elastest.codeurjc.qe.utils.RestClient;

public class OpenviduWebRTCQoEMeter extends RecordingBaseTest {

    private static CountDownLatchWithException waitForSessionReadyLatch;
    public static ExecutorService browserInitializationTaskExecutor = Executors
            .newCachedThreadPool();

    @Test
    public void webRTCQoEMeter(TestInfo info)
            throws SessionNotCreatedException, TimeoutException, IOException {
        startBrowsers(info);

        // Get Videos
        BrowserClient firstBrowser = browserClientList.get(0);
        try {

            // Get Subscriber/Publisher streamIds
            JsonArray subscriberStreamIds = firstBrowser.getSubscriberStreams();
            JsonArray publiserStreamIds = firstBrowser.getPublisherStreams();

            // Record and download Subscriber/Publisher videos
            String subscriberLocalRecorderId = recordAndDownloadBrowserVideo(
                    firstBrowser, subscriberStreamIds.get(0));

            String publisherLocalRecorderId = recordAndDownloadBrowserVideo(
                    firstBrowser, publiserStreamIds.get(0));

            // Get Subscriber/Publisher video paths
            String subscriberVideo = getVideoPathByLocalRecorderId(
                    subscriberLocalRecorderId);
            String publisherVideo = getVideoPathByLocalRecorderId(
                    publisherLocalRecorderId);

            // Start WebRTCQoEMeter service in EUS
            String qoeServiceId = startWebRTCQoEMeter(publisherVideo,
                    subscriberVideo, firstBrowser).toString();

            // Wait for CSV and Get
            List<InputStream> csvList = waitForCSV(qoeServiceId, firstBrowser);

            if (csvList != null && csvList.size() > 0) {
                int count = 1;
                for (InputStream csvFile : csvList) {
                    byte[] csvFileAsByteArr = IOUtils.toByteArray(csvFile);

                    attachFileToExecution(csvFileAsByteArr,
                            "csv-" + count + ".csv");
                    count++;
                }

            } else {
                Assertions.fail("Csv files List is null or empty");
            }

            sleep(20000);
        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail(e.getMessage());
            waitForSessionReadyLatch.abort(e.getMessage());
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
        int SESSION_ID = 1;

        String publicUrl = OPENVIDU_SUT_URL
                + (OPENVIDU_SUT_URL.endsWith("/") ? "" : "/");

        String completeUrl = OPENVIDU_WEBAPP_URL + "?publicurl=" + publicUrl
                + "&secret=" + OPENVIDU_SECRET + "&sessionId=" + SESSION_ID
                + "&userId=" + userId;

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

            String noUseAWS = System.getProperty("noUseAWS");
            if (noUseAWS == null || !"true".equals(noUseAWS)) {
                // AWS capabilities for browsers
                ObjectMapper mapper = new ObjectMapper();
                Map<String, String> awsConfigMap = mapper
                        .readValue(awsConfig.toString(), Map.class);

                capabilities.setCapability("awsConfig", awsConfigMap);

                // This flag sets the video input (with padding)
                options.addArguments("--use-file-for-fake-video-capture="
                        + "/opt/openvidu/fakevideo_with_padding.y4m");
                // This flag sets the audio input
                options.addArguments("--use-file-for-fake-audio-capture="
                        + "/opt/openvidu/fakeaudio.wav");
            } else { // Development (docker)
                String sutHost = System.getenv("ET_SUT_HOST");

                completeUrl = "https://" + sutHost + ":5000?publicurl=https://"
                        + sutHost + ":4443/&secret=" + OPENVIDU_SECRET
                        + "&sessionId=" + SESSION_ID + "&userId=" + userId;
            }

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

        BrowserClient browserClient = new BrowserClient(driver, userId,
                SESSION_ID);
        browserClientList.add(browserClient);

        browserClient.getDriver().get(completeUrl);
        browserClient.startEventPolling(true, false);

        try {
            browserClient.waitForEvent("connectionCreated", USERS_BY_SESSION);
            browserClient.waitForEvent("accessAllowed", 1);
            browserClient.waitForEvent("streamCreated", USERS_BY_SESSION);
            browserClient.stopEventPolling();
        } catch (TimeoutException | NullPointerException e) {
            String msg = "Error on waiting for events on user " + userId
                    + " session " + SESSION_ID + ": " + e.getMessage();
            if (e instanceof TimeoutException) {
                throw new TimeoutException(msg);
            } else if (e instanceof NullPointerException) {
                throw new NullPointerException(msg);
            } else {
                throw e;
            }
        }

    }

    public byte[] startWebRTCQoEMeter(String presenterPath, String viewerPath,
            BrowserClient browserClient) throws Exception {
        if (EUS_URL != null) {
            logger.info("Starting WebRTC QoE Meter");
            RestClient restClient = new RestClient();

            SessionId sessionId = ((RemoteWebDriver) browserClient.getDriver())
                    .getSessionId();

            String url = EUS_URL.endsWith("/") ? EUS_URL : EUS_URL + "/";
            url += "session/" + sessionId.toString()
                    + "/webrtc/qoe/meter/start";
            url += "?presenterPath=" + presenterPath + "&viewerPath="
                    + viewerPath;

            byte[] response;
            response = restClient.sendGet(url);

            logger.info("Started WebRTC QoE Meter for Session {} successfully",
                    sessionId);

            return response;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<InputStream> waitForCSV(String qoeServiceId,
            BrowserClient browserClient) throws Exception {
        if (EUS_URL != null) {
            // 20min
            int timeoutSeconds = 1200;
            long endWaitTime = System.currentTimeMillis()
                    + timeoutSeconds * 1000;

            logger.info(
                    "Waiting for CSV generated in WebRTC QoE Meter (timeout {}s)",
                    timeoutSeconds);
            RestClient restClient = new RestClient();

            SessionId sessionId = ((RemoteWebDriver) browserClient.getDriver())
                    .getSessionId();

            String urlPrefix = EUS_URL.endsWith("/") ? EUS_URL : EUS_URL + "/";
            urlPrefix += "session/" + sessionId.toString()
                    + "/webrtc/qoe/meter/" + qoeServiceId;
            String url = urlPrefix + "/csv/isgenerated";

            String response;

            do {
                response = restClient.sendGet(url).toString();
                logger.debug("CSV not generated yet, waiting...");
                sleep(2000);
            } while (System.currentTimeMillis() < endWaitTime
                    && !"true".equals(response));
            logger.info("CSV Generated for Session {} successfully", sessionId);

            url = urlPrefix + "/csv";
            response = restClient.sendGet(url).toString();

            List<InputStream> csvFiles = null;
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.configure(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        false);

                csvFiles = (List<InputStream>) objectMapper.readValue(response,
                        List.class);
                return csvFiles;
            } catch (IOException e) {
                throw new Exception(
                        "Error during CSV list conversion: " + e.getMessage());
            }
        }
        return null;
    }

    private String recordAndDownloadBrowserVideo(BrowserClient browser,
            JsonElement streamId) throws Exception {
        String localRecorderId = null;

        localRecorderId = browser.initLocalRecorder(streamId.getAsString());
        browser.startRecording(localRecorderId);

        // seconds
        final int WAIT_TIME = 60;
        long endWaitTime = System.currentTimeMillis() + WAIT_TIME * 1000;

        // Wait
        while (System.currentTimeMillis() < endWaitTime) {
            sleep(1000);
        }

        browser.stopRecording(localRecorderId);

        browser.downloadRecording(localRecorderId);

        return localRecorderId;
    }

    public String getVideoPathByLocalRecorderId(String localRecorderId) {
        final String fileName = localRecorderId + ".webm";
        return "/home/ubuntu/Downloads/" + fileName;
    }

    public void attachFileToExecution(byte[] file, String fileName)
            throws Exception {
        if (ET_ETM_TJOB_ATTACHMENT_API != null) {
            try {
                logger.info("Attaching file {} to TJob Exec", fileName);
                RestClient restClient = new RestClient();

                restClient.postMultipart(ET_ETM_TJOB_ATTACHMENT_API, fileName,
                        file);

                logger.info("File with name {} has been attached successfully",
                        fileName);
            } catch (Exception e) {
                String msg = "Error on attach file: " + e.getMessage();
                logger.error(msg);
                throw e;
            }
        }
    }
}