package io.elastest.codeurjc.qe.utils;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;

public class RestClient {
    protected static final Logger logger = getLogger(lookup().lookupClass());

    // HTTP GET request
    public String sendGet(String url) throws Exception {
        logger.info("Doing get to {}", url);

        // Do request
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet uploadFile = new HttpGet(url);

        CloseableHttpResponse response = httpClient.execute(uploadFile);
        final int statusCode = response.getStatusLine().getStatusCode();
        logger.info("Response Code: {}", statusCode);
        HttpEntity responseEntity = response.getEntity();
        String responseBody = EntityUtils.toString(responseEntity);
        response.close();

        if (statusCode != 200) {
            throw new Exception("Error on attach file: Code " + statusCode);
        }

        return responseBody;
    }

    public HttpEntity postMultipart(String urlString, String fileNameWithExt,
            String body) throws Exception {
        logger.info("Doing multipart post to {}", urlString);

        // Do request
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost uploadFile = new HttpPost(urlString);
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        // This attaches the file to the POST:
        builder.addBinaryBody("file", body.getBytes(),
                ContentType.MULTIPART_FORM_DATA, fileNameWithExt);

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
