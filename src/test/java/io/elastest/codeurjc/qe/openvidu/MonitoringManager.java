package io.elastest.codeurjc.qe.openvidu;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;

import org.slf4j.Logger;

import com.google.gson.JsonObject;

public class MonitoringManager {
    final Logger logger = getLogger(lookup().lookupClass());

    String endpoint;
    boolean withSSL;
    String execid;
    String containerName;
    String component;
    URL url;

    public MonitoringManager() {
        withSSL = false;
        endpoint = System.getenv("ET_MON_LSHTTP_API");
        if (endpoint == null) {
            endpoint = System.getenv("ET_MON_LSHTTPS_API");
            withSSL = true;
        }

        execid = System.getenv("ET_MON_EXEC");
        component = System.getenv("COMPONENT");
        containerName = System.getenv("CONTAINER_NAME");
        try {
            url = new URL(endpoint);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    // HTTP POST request
    private void sendMonitoring(String body) throws Exception {

        if (url != null && component != null && execid != null) {
            URLConnection con = url.openConnection();
            logger.info("Sending monitoring to {}: {}", url, body);
            if (withSSL) {
                HttpURLConnection http = (HttpURLConnection) con;
                http.setRequestMethod("POST");
                http.setDoOutput(true);

                byte[] out = body.getBytes(StandardCharsets.UTF_8);
                int length = out.length;

                http.setFixedLengthStreamingMode(length);
                http.setRequestProperty("Content-Type",
                        "application/json; charset=UTF-8");
                http.connect();
                try (OutputStream os = http.getOutputStream()) {
                    os.write(out);
                }
            } else {
                HttpsURLConnection https = (HttpsURLConnection) con;
                https.setRequestMethod("POST");
                https.setDoOutput(true);

                byte[] out = body.getBytes(StandardCharsets.UTF_8);
                int length = out.length;

                https.setFixedLengthStreamingMode(length);
                https.setRequestProperty("Content-Type",
                        "application/json; charset=UTF-8");
                https.connect();
                try (OutputStream os = https.getOutputStream()) {
                    os.write(out);
                }
            }
        }
    }

    public void sendSingleMessage(String message) throws Exception {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("component", component);
        jsonObject.addProperty("exec", execid);
        jsonObject.addProperty("stream", "default_log");
        jsonObject.addProperty("message", message);
        jsonObject.addProperty("containerName", containerName);

        sendMonitoring(jsonObject.getAsString());
    }

    // public void sendMultipleLog(String... messages) {
    // String jsonMessage = "[ " + formatJsonMessage(message) + ",";
    //
    // message = String.join(" ", generateRandomWords(3));
    // jsonMessage += formatJsonMessage(message) + " ]";
    //
    //
    // JsonObject jsonObject = new JsonObject();
    // jsonObject.addProperty("component", component);
    // jsonObject.addProperty("exec", execid);
    // jsonObject.addProperty("stream", "default_log");
    // jsonObject.addProperty("messages", jsonMessage);
    // jsonObject.addProperty("containerName", containerName);
    //
    // sendMonitoring(jsonObject.getAsString());
    // }

    public void sendAtomicMetric(String metricName, String unit, String value,
            String stream) throws Exception {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("component", component);
        jsonObject.addProperty("exec", execid);
        if (containerName != null) {
            jsonObject.addProperty("containerName", containerName);
        }
        jsonObject.addProperty("et_type", metricName);
        jsonObject.addProperty("stream", stream);
        jsonObject.addProperty("stream_type", "atomic_metric");
        jsonObject.addProperty("unit", unit);
        jsonObject.addProperty("metricName", metricName);
        jsonObject.addProperty(metricName, value);

        sendMonitoring(jsonObject.toString());
    }

    public void sendComposedMetric(String metricName, String stream,
            JsonObject metricsJson, JsonObject unitsJson) throws Exception {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("component", component);
        jsonObject.addProperty("exec", execid);
        jsonObject.addProperty("containerName", containerName);
        jsonObject.addProperty("et_type", metricName);
        jsonObject.addProperty("stream", stream);
        jsonObject.addProperty("stream_type", "atomic_metric");
        jsonObject.add("units", unitsJson);
        jsonObject.add(metricName, metricsJson);

        sendMonitoring(jsonObject.getAsString());
    }

    public static String formatJsonMessage(String msg) {
        return "\"" + msg + "\"";
    }

    public static String[] generateRandomWords(int numberOfWords) {
        String[] randomStrings = new String[numberOfWords];
        Random random = new Random();
        for (int i = 0; i < numberOfWords; i++) {
            char[] word = new char[random.nextInt(8) + 3];
            for (int j = 0; j < word.length; j++) {
                word[j] = (char) ('a' + random.nextInt(26));
            }
            randomStrings[i] = new String(word);
        }
        return randomStrings;
    }

    public static int randInt(int min, int max) {
        Random rand = new Random();
        int randomNum = rand.nextInt((max - min) + 1) + min;

        return randomNum;
    }

}
