package io.elastest.codeurjc.qe.openvidu;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Map;
import java.util.Queue;
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
    private AtomicBoolean stopped = new AtomicBoolean(false);

    private Queue<JsonObject> eventQueue;
    private Map<String, AtomicInteger> numEvents;
    private Map<String, CountDownLatch> eventCountdowns;

    JsonParser jsonParser = new JsonParser();

    public BrowserClient(WebDriver driver) {
        this.driver = driver;
    }

    public WebDriver getDriver() {
        return driver;
    }

    public void startEventPolling() {
        this.pollingThread = new Thread(() -> {
            while (!this.stopped.get()) {
                this.getBrowserEvents();
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

    public void waitUntilEventReaches(String eventName, int eventNumber)
            throws TimeoutException {
        CountDownLatch eventSignal = new CountDownLatch(eventNumber);
        this.setCountDown(eventName, eventSignal);
        try {
            if (!eventSignal.await(40 * 1000, TimeUnit.MILLISECONDS)) {
                throw (new TimeoutException(eventName));
            }
        } catch (TimeoutException e) {
            throw e;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void getBrowserEvents() {
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

        JsonArray events = eventsAndStats.get("events").getAsJsonArray();
        for (JsonElement ev : events) {
            JsonObject event = ev.getAsJsonObject();
            String eventName = event.get("event").getAsString();

            this.eventQueue.add(event);
            getNumEvents(eventName).incrementAndGet();

            if (this.eventCountdowns.get(eventName) != null) {
                this.eventCountdowns.get(eventName).countDown();
            }
        }

    }

    private AtomicInteger getNumEvents(String eventName) {
        return this.numEvents.computeIfAbsent(eventName,
                k -> new AtomicInteger(0));
    }

    private void setCountDown(String eventName, CountDownLatch cd) {
        this.eventCountdowns.put(eventName, cd);
        for (int i = 0; i < getNumEvents(eventName).get(); i++) {
            cd.countDown();
        }
    }

    public void dispose() {
        if (driver != null) {
            driver.quit();
        }
    }

}
