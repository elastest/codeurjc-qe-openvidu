package io.elastest.codeurjc.qe.utils;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;

public class RestClient {
    protected static final Logger logger = getLogger(lookup().lookupClass());

    // HTTP GET request
    public StringBuffer sendGet(String url) throws Exception {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // optional default is GET
        con.setRequestMethod("GET");

        int responseCode = con.getResponseCode();
        logger.info("Sending 'GET' request to URL : " + url);
        logger.info("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response;
    }

    // HTTP POST request
    public StringBuffer sendPost(String url, String urlParameters)
            throws Exception {
        URL obj = new URL(url);
        HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

        // add reuqest header
        con.setRequestMethod("POST");
        con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

        // Send post request
        con.setDoOutput(true);

        logger.info("Sending 'POST' request to URL : " + url);
        if (urlParameters != null) {
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(urlParameters);
            wr.flush();
            wr.close();
            logger.info("Post parameters : " + urlParameters);
        }

        int responseCode = con.getResponseCode();
        logger.info("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response;
    }

    public HttpEntity postMultipart(String urlString, String fileNameWithExt,
            String body) throws Exception {
        logger.info("Doing multipart post to {}", urlString);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost uploadFile = new HttpPost(urlString);
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        // This attaches the file to the POST:
        builder.addBinaryBody("file", body.getBytes(),
                ContentType.APPLICATION_OCTET_STREAM, fileNameWithExt);

        HttpEntity multipart = builder.build();
        uploadFile.setEntity(multipart);

        CloseableHttpResponse response = httpClient.execute(uploadFile);

        final int statusCode = response.getStatusLine().getStatusCode();
        logger.info("Response Code: {}", statusCode);
        HttpEntity responseEntity = response.getEntity();
        response.close();

        if (statusCode != 200) {
            throw new Exception("Error on attach file: Code " + statusCode);
        }

        return responseEntity;
    }

}
