package io.elastest.codeurjc.qe.openvidu;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

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

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class BrowserClient {
    final Logger logger = getLogger(lookup().lookupClass());

    private Thread pollingThread;
    private WebDriver driver;
    private String userId;
    private int session;

    private AtomicBoolean stopped = new AtomicBoolean(false);

    private Queue<JsonObject> eventQueue;
    private Map<String, AtomicInteger> numEvents;
    private Map<String, CountDownLatch> eventCountdowns;

    JsonParser jsonParser = new JsonParser();

    public BrowserClient(WebDriver driver, String userId, int session) {
        this.driver = driver;
        this.userId = userId;
        this.session = session;

        this.eventQueue = new ConcurrentLinkedQueue<JsonObject>();
        this.numEvents = new ConcurrentHashMap<>();
        this.eventCountdowns = new ConcurrentHashMap<>();
    }

    public WebDriver getDriver() {
        return driver;
    }

    public void startEventPolling(boolean processEvents, boolean processStats) {
        logger.info("Starting event polling in user {} session {}", userId,
                session);
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
                if (ex.getClass().getSimpleName()
                        .equals("NoSuchSessionException")) {
                    logger.error(
                            "Disposing driver when running 'executeScript'");
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

    public void waitForEvent(String eventName, int eventNumber)
            throws TimeoutException {
        logger.info(
                "Waiting for event {} to occur {} times in user {} session {}",
                eventName, eventNumber, userId, session);

        CountDownLatch eventSignal = new CountDownLatch(eventNumber);
        this.setCountDown(eventName, eventSignal);
        try {
            int timeoutInSecs = 240;
            if (!eventSignal.await(timeoutInSecs * 1000,
                    TimeUnit.MILLISECONDS)) {
                throw (new TimeoutException("Timeout (" + timeoutInSecs
                        + "sec) in waiting for event " + eventName));
            }
        } catch (TimeoutException e) {
            throw e;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void getBrowserEvents(boolean processEvents, boolean processStats) {
        String eventsRaw = null;
        try {
            eventsRaw = (String) ((JavascriptExecutor) driver)
                    .executeScript("window.collectEventsAndStats();"
                            + "var result = JSON.stringify(window.openviduLoadTest);"
                            + "window.resetEventsAndStats();"
                            + "return result;");
        } catch (Exception e) {
            return;
        }

        JsonObject eventsAndStats = jsonParser.parse(eventsRaw)
                .getAsJsonObject();

        if (eventsAndStats == null || eventsAndStats.isJsonNull()) {
            return;
        }

        // EVENTS
        if (processEvents) {
            JsonArray events = eventsAndStats.get("events").getAsJsonArray();
            for (JsonElement ev : events) {
                JsonObject event = ev.getAsJsonObject();
                String eventName = event.get("event").getAsString();
                logger.info("New event received in user {} of session {}: {}",
                        userId, session, event);
                this.eventQueue.add(event);
                getNumEvents(eventName).incrementAndGet();

                if (this.eventCountdowns.get(eventName) != null) {
                    doCountDown(eventName);
                }
            }
        }

        // STATS
        if (processStats) {
            JsonObject stats = eventsAndStats.get("stats").getAsJsonObject();
            if (stats != null) {
                for (Entry<String, JsonElement> user : stats.entrySet()) {
                    JsonArray userStats = (JsonArray) user.getValue();
                    if (userStats != null) {
                        for (JsonElement userStatsElement : userStats) {
                            if (userStatsElement != null) {
                                JsonObject userStatsObj = (JsonObject) userStatsElement;
                                for (Entry<String, JsonElement> userSingleStat : userStatsObj
                                        .entrySet()) {
                                    if (userSingleStat != null && ("jitter"
                                            .equals(userSingleStat.getKey())
                                            || "delay".equals(
                                                    userSingleStat.getKey()

                                            ))) {
                                        logger.info("User '{}' Stat: {} = {}",
                                                userId, userSingleStat.getKey(),
                                                userSingleStat.getValue());
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
        return this.numEvents.computeIfAbsent(eventName,
                k -> new AtomicInteger(0));
    }

    private void setCountDown(String eventName, CountDownLatch cd) {
        logger.info("Setting countDownLatch for event {} in user {} session {}",
                eventName, userId, session);
        this.eventCountdowns.put(eventName, cd);
        for (int i = 0; i < getNumEvents(eventName).get(); i++) {
            doCountDown(eventName);
        }
    }

    private void doCountDown(String eventName) {
        logger.info("Doing countdown of event {} in user {} session {}",
                eventName, userId, session);
        this.eventCountdowns.get(eventName).countDown();

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

}
