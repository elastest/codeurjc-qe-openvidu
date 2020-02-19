package io.elastest.codeurjc.qe.openvidu;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;
import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.elastest.codeurjc.qe.utils.RestClient;

public class BrowserClient {
    final Logger logger = getLogger(lookup().lookupClass());
    RestClient restClient;

    private Thread pollingThread;
    private WebDriver driver;
    private String userId;
    private int session;

    private AtomicBoolean stopped = new AtomicBoolean(false);

    private Queue<JsonObject> eventQueue;
    private Map<String, AtomicInteger> numEvents;
    private Map<String, CountDownLatch> eventCountdowns;

    private Map<String, List<JsonObject>> receivedEventsMap;
    private List<String> qoeServiceIds;

    JsonParser jsonParser = new JsonParser();

    public BrowserClient(WebDriver driver, String userId, int session) {
        this.driver = driver;
        this.userId = userId;
        this.session = session;

        this.eventQueue = new ConcurrentLinkedQueue<JsonObject>();
        this.numEvents = new ConcurrentHashMap<>();
        this.eventCountdowns = new ConcurrentHashMap<>();
        this.receivedEventsMap = new HashMap<>();
        this.restClient = new RestClient();
        this.qoeServiceIds = new ArrayList<>();
    }

    public Thread getPollingThread() {
        return pollingThread;
    }

    public String getUserId() {
        return userId;
    }

    public int getSession() {
        return session;
    }

    public AtomicBoolean getStopped() {
        return stopped;
    }

    public Queue<JsonObject> getEventQueue() {
        return eventQueue;
    }

    public Map<String, AtomicInteger> getNumEvents() {
        return numEvents;
    }

    public Map<String, CountDownLatch> getEventCountdowns() {
        return eventCountdowns;
    }

    public JsonParser getJsonParser() {
        return jsonParser;
    }

    public WebDriver getDriver() {
        return driver;
    }

    public List<String> getQoeServiceIds() {
        return qoeServiceIds;
    }

    /* ******************************************** */
    /* ****************** EVENTS ****************** */
    /* ******************************************** */

    public void startEventPolling(boolean processEvents, boolean processStats) {
        logger.info("Starting event polling in user {} session {}", userId, session);
        this.pollingThread = new Thread(() -> {
            while (!this.stopped.get()) {
                this.getBrowserEvents(processEvents, processStats);
                try {
                    Thread.sleep(BaseTest.BROWSER_POLL_INTERVAL);
                } catch (InterruptedException e) {
                    logger.debug("OpenVidu events polling thread interrupted");
                }
            }
        });

        Thread.UncaughtExceptionHandler handler = new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread th, Throwable ex) {
                if (ex.getClass().getSimpleName().equals("NoSuchSessionException")) {
                    logger.error("Disposing driver when running 'executeScript'");
                }
            }
        };

        this.pollingThread.setUncaughtExceptionHandler(handler);
        this.pollingThread.start();
    }

    public void stopEventPolling() {
        this.eventCountdowns.clear();
        this.numEvents.clear();
        this.stopped.set(true);
        this.pollingThread.interrupt();
    }

    public void waitForEvent(String eventName, int eventNumber) throws TimeoutException {
        logger.info("Waiting for event {} to occur {} times in user {} session {}", eventName,
                eventNumber, userId, session);

        CountDownLatch eventSignal = new CountDownLatch(eventNumber);
        this.setCountDown(eventName, eventSignal);
        try {
            int timeoutInSecs = 240;
            if (!eventSignal.await(timeoutInSecs * 1000, TimeUnit.MILLISECONDS)) {
                throw (new TimeoutException(
                        "Timeout (" + timeoutInSecs + "sec) in waiting for event " + eventName));
            }
        } catch (TimeoutException e) {
            throw e;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public JsonObject getBrowserEventsAndStatsObject() throws Exception {
        String eventsRaw = (String) ((JavascriptExecutor) driver)
                .executeScript("window.collectEventsAndStats();"
                        + "var result = JSON.stringify(window.openviduLoadTest);"
                        + "window.resetEventsAndStats();" + "return result;");
        return jsonParser.parse(eventsRaw).getAsJsonObject();

    }

    public JsonArray getEventsFromObject(JsonObject eventsAndStats) {
        return eventsAndStats.get("events").getAsJsonArray();
    }

    public JsonObject getStatsFromObject(JsonObject eventsAndStats) {
        return eventsAndStats.get("stats").getAsJsonObject();
    }

    public void getBrowserEvents(boolean processEvents, boolean processStats) {
        JsonObject eventsAndStats = null;
        try {
            eventsAndStats = getBrowserEventsAndStatsObject();
        } catch (Exception e) {
            return;
        }

        if (eventsAndStats == null || eventsAndStats.isJsonNull()) {
            return;
        }

        // EVENTS
        if (processEvents) {
            JsonArray events = getEventsFromObject(eventsAndStats);
            for (JsonElement ev : events) {
                // { event: 'name', content: content, date: 1581939451510 (sometimes) }
                JsonObject event = ev.getAsJsonObject();
                String eventName = event.get("event").getAsString();
                logger.info("New event received in user {} of session {}: {}", userId, session,
                        event);
                this.eventQueue.add(event);
                getNumEvents(eventName).incrementAndGet();

                if (!receivedEventsMap.containsKey(eventName)) {
                    receivedEventsMap.put(eventName, new ArrayList<>());
                }

                receivedEventsMap.get(eventName).add(event);

                if (this.eventCountdowns.get(eventName) != null) {
                    doCountDown(eventName);
                }
            }
        }

        // STATS
        if (processStats) {
            JsonObject stats = getStatsFromObject(eventsAndStats);
            if (stats != null) {
                for (Entry<String, JsonElement> user : stats.entrySet()) {
                    JsonArray userStats = (JsonArray) user.getValue();
                    if (userStats != null) {
                        for (JsonElement userStatsElement : userStats) {
                            if (userStatsElement != null) {
                                JsonObject userStatsObj = (JsonObject) userStatsElement;
                                for (Entry<String, JsonElement> userSingleStat : userStatsObj
                                        .entrySet()) {
                                    if (userSingleStat != null
                                            && ("jitter".equals(userSingleStat.getKey())
                                                    || "delay".equals(userSingleStat.getKey()

                                                    ))) {
                                        logger.info("User '{}' Stat: {} = {}", userId,
                                                userSingleStat.getKey(), userSingleStat.getValue());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private AtomicInteger getNumEvents(String eventName) {
        return this.numEvents.computeIfAbsent(eventName, k -> new AtomicInteger(0));
    }

    private void setCountDown(String eventName, CountDownLatch cd) {
        logger.info("Setting countDownLatch for event {} in user {} session {}", eventName, userId,
                session);
        this.eventCountdowns.put(eventName, cd);
        for (int i = 0; i < getNumEvents(eventName).get(); i++) {
            doCountDown(eventName);
        }
    }

    private void doCountDown(String eventName) {
        logger.info("Doing countdown of event {} in user {} session {}", eventName, userId,
                session);
        this.eventCountdowns.get(eventName).countDown();

    }

    public List<JsonObject> getEventListByName(String eventName) {
        return receivedEventsMap.get(eventName);
    }

    public void dispose() {
        try {
            if (driver != null) {
                logger.info(
                        "Stopping browser of user {} session {}. This process can take a while, since the videos are going to be downloaded",
                        userId, session);
                driver.quit();
            }
        } catch (Exception e) {
        }
    }

    /* ********************************************* */
    /* ****************** Streams ****************** */
    /* ********************************************* */

    public JsonArray getSubscriberStreams() throws Exception {
        String streams = (String) ((JavascriptExecutor) driver).executeScript(
                "var result = JSON.stringify(getSubscriberStreams());" + "return result;");
        logger.info("Subscriber Streams2 string: {}", streams);
        return jsonParser.parse(streams).getAsJsonArray();

    }

    public JsonArray getPublisherStreams() throws Exception {
        String streams = (String) ((JavascriptExecutor) driver).executeScript(
                "var result = JSON.stringify(getPublisherStreams());" + "return result;");
        logger.info("Publisher streams string: {}", streams);
        return jsonParser.parse(streams).getAsJsonArray();

    }

    public String initLocalRecorder(String streamId) throws Exception {
        try {
            logger.info("Init local recorder from streamId '{}'", streamId);
            String localRecorderId = (String) ((JavascriptExecutor) driver)
                    .executeScript("var localRecorderId = initLocalRecorder('" + streamId + "');"
                            + "return localRecorderId;");
            if (localRecorderId == null) {
                throw new Exception("Local recorder ID from streamId " + streamId + " is null");
            }
            logger.info("Local recorder from streamId '{}' has been initialized with ID '{}'",
                    streamId, localRecorderId);
            return localRecorderId;
        } catch (Exception e) {
            String msg = "Error on init local recorder for stream " + streamId + ": "
                    + e.getMessage();
            throw new Exception(msg);
        }
    }

    public void startRecording(String localRecorderId) throws Exception {
        try {
            logger.info("Starting recording with local recorder Id: {}", localRecorderId);
            ((JavascriptExecutor) driver)
                    .executeScript("startRecording('" + localRecorderId + "');");
            logger.info("Recording with local recorder Id '{}' has been started", localRecorderId);
        } catch (Exception e) {
            String msg = "Error on start recording for localRecorder " + localRecorderId + ": "
                    + e.getMessage();
            throw new Exception(msg);
        }
    }

    public void stopRecording(String localRecorderId) throws Exception {
        try {
            logger.info("Stopping recording with local recorder Id: {}", localRecorderId);
            ((JavascriptExecutor) driver)
                    .executeScript("stopRecording('" + localRecorderId + "');");
            logger.info("Recording with local recorder Id '{}' has been stopped", localRecorderId);
        } catch (Exception e) {
            String msg = "Error on stop recording for localRecorder " + localRecorderId + ": "
                    + e.getMessage();
            throw new Exception(msg);
        }
    }

    public void downloadRecording(String localRecorderId) throws Exception {
        try {
            logger.info("Downloading recording with local recorder Id: {}", localRecorderId);
            ((JavascriptExecutor) driver)
                    .executeScript("downloadRecording('" + localRecorderId + "');");
            logger.info("Recording with local recorder Id '{}' has been downloaded",
                    localRecorderId);
        } catch (Exception e) {
            String msg = "Error on download recording for localRecorder " + localRecorderId + ": "
                    + e.getMessage();
            throw new Exception(msg);
        }
    }

    /* ********************************************* */
    /* ****************** EUS API ****************** */
    /* ********************************************* */

    public byte[] getFile(String hubUrl, String completePath) throws Exception {
        if (hubUrl != null) {
            SessionId sessionId = ((RemoteWebDriver) getDriver()).getSessionId();
            logger.info("Getting file {} from browser with session id {}", completePath, sessionId);

            String url = hubUrl.endsWith("/") ? hubUrl : hubUrl + "/";
            url += "browserfile/session/" + sessionId + "/" + completePath;
            url += "?isDirectory=false";

            return restClient.sendGet(url);
        }
        return null;
    }

    @SuppressWarnings("resource")
    public void uploadFile(String hubUrl, InputStream fileStream, String completeFilePath,
            String fileName) throws Exception {
        if (hubUrl != null) {
            SessionId sessionId = ((RemoteWebDriver) getDriver()).getSessionId();
            logger.info("Starting upload of file {} to browser with session id {}", fileName,
                    sessionId);

            String url = hubUrl.endsWith("/") ? hubUrl : hubUrl + "/";
            url += "browserfile/session/" + sessionId;
            url += "?path=" + completeFilePath;

            String folderPath = "/tmp/" + sessionId + "/";
            File folder = new File(folderPath);

            if (!folder.exists()) {
                logger.debug("Try to create folder structure: {}", folderPath);
                logger.info("Creating folder at {}.", folder.getAbsolutePath());
                boolean created = folder.mkdirs();
                if (!created) {
                    logger.error("Folder does not created at {}.", folderPath);
                    return;
                }
                logger.info("Folder created at {}.", folderPath);
            }

            ReadableByteChannel readChannel = Channels.newChannel(fileStream);
            FileOutputStream fileOS = new FileOutputStream(folder + fileName);
            FileChannel writeChannel = fileOS.getChannel();
            writeChannel.transferFrom(readChannel, 0, Long.MAX_VALUE);

            File targetFile = new File(folder + fileName);
            FileInputStream input = new FileInputStream(targetFile);

            logger.info("File {} for browser with session id {} converted to byte array!", fileName,
                    sessionId);

            restClient.postMultipart(url, fileName, IOUtils.toByteArray(input));
        }
    }

    public void uploadFileFromUrl(String hubUrl, String fileUrl, String completeFilePath,
            String fileName) throws Exception {
        if (hubUrl != null) {
            SessionId sessionId = ((RemoteWebDriver) getDriver()).getSessionId();
            logger.info("Starting upload of file {} to browser with session id {}", fileName,
                    sessionId);

            String url = hubUrl.endsWith("/") ? hubUrl : hubUrl + "/";
            url += "browserfile/session/" + sessionId;
            url += "?fileUrl=" + URLEncoder.encode(fileUrl, "UTF-8");
            url += "&fileName=" + fileName;
            url += "&path=" + completeFilePath;

            restClient.sendPost(url, null);
        }
    }

    public void sendWebRTCQoEMeterMetricsTime(String hubUrl, String qoeServiceId, long startTime,
            long videoDuration) throws Exception {
        if (hubUrl != null) {
            SessionId sessionId = ((RemoteWebDriver) getDriver()).getSessionId();
            logger.info(
                    "Sending WebRTC QoE metrics time from session {} and WebRTCQoEService id {}",
                    sessionId, qoeServiceId);

            String url = hubUrl.endsWith("/") ? hubUrl : hubUrl + "/";
            url += "session/" + sessionId;
            url += "/webrtc/qoe/meter/" + qoeServiceId + "/metrics/time";

            JsonObject body = new JsonObject();
            body.addProperty("startTime", startTime);
            body.addProperty("videoDuration", videoDuration);

            restClient.sendPost(url, body.toString());
        }
    }

    public void stopEusRecording(String hubUrl) throws Exception {
        if (hubUrl != null) {
            SessionId sessionId = ((RemoteWebDriver) getDriver()).getSessionId();
            logger.info("Stopping recording of session {}", sessionId);

            String url = hubUrl.endsWith("/") ? hubUrl : hubUrl + "/";
            url += "session/" + sessionId;
            url += "/recording/stop";

            restClient.delete(url);
        }
    }

    public String createWebRTCQoEMeter(String eusURL) throws Exception {
        if (eusURL != null) {
            logger.info("Starting WebRTC QoE Meter for user {}", getUserId());

            SessionId sessionId = ((RemoteWebDriver) getDriver()).getSessionId();

            String url = eusURL.endsWith("/") ? eusURL : eusURL + "/";
            url += "session/" + sessionId + "/webrtc/qoe/meter/create";

            byte[] response = restClient.sendGet(url);

            String id = new String(response);

            logger.info("Created WebRTC QoE Meter for user {} successfully! Id {}", getUserId(),
                    id);

            return id;
        }
        return null;
    }

    public void uploadCsvToQoE(String hubUrl, String identifier, String fileUrl, String fileName)
            throws Exception {
        if (hubUrl != null) {
            SessionId sessionId = ((RemoteWebDriver) getDriver()).getSessionId();
            logger.info(
                    "Starting upload of csv file {} to browser with session id {} and WebRTCQoEMeter with id {}",
                    fileName, sessionId, identifier);

            String url = hubUrl.endsWith("/") ? hubUrl : hubUrl + "/";
            url += "session/" + sessionId;
            url += "/webrtc/qoe/meter/" + identifier + "/csv";
            url += "?fileUrl=" + URLEncoder.encode(fileUrl, "UTF-8");
            url += "&fileName=" + fileName;

            restClient.sendPost(url, null);
        }
    }
}
