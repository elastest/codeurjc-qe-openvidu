package io.elastest.codeurjc.qe.utils;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;

public class RestClient {
    protected static final Logger logger = getLogger(lookup().lookupClass());

    // HTTP GET request
    public byte[] sendGet(String url) throws Exception {
        logger.info("Doing GET to {}", url);

        // Do request
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet uploadFile = new HttpGet(url);

        CloseableHttpResponse response = httpClient.execute(uploadFile);
        final int statusCode = response.getStatusLine().getStatusCode();
        logger.info("Response Code: {}", statusCode);
        HttpEntity responseEntity = response.getEntity();
        byte[] responseBody = EntityUtils.toByteArray(responseEntity);
        response.close();

        if (statusCode != 200) {
            throw new Exception("Error on GET: Code " + statusCode);
        }
        httpClient.close();
        return responseBody;
    }

    public HttpEntity sendPost(String urlString, String jsonBody) throws Exception {
        logger.info("Sending POST to {}", urlString);

        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(urlString);

        StringEntity entity = jsonBody != null ? new StringEntity(jsonBody) : null;
        httpPost.setEntity(entity);

        httpPost.setHeader("Accept", "*/*");
        httpPost.setHeader("Content-type", "application/json");

        CloseableHttpResponse response = client.execute(httpPost);

        final int statusCode = response.getStatusLine().getStatusCode();
        logger.info("Response Code: {}", statusCode);
        HttpEntity responseEntity = response.getEntity();
        response.close();

        if (statusCode != 200) {
            throw new Exception("Error on POST: Code " + statusCode);
        }

        client.close();
        return responseEntity;
    }

    public HttpEntity postMultipart(String urlString, String fileNameWithExt, byte[] body)
            throws Exception {
        logger.info("Doing Multipart POST to {}", urlString);

        // Do request
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost uploadFile = new HttpPost(urlString);
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        // This attaches the file to the POST:
        builder.addBinaryBody("file", body, ContentType.MULTIPART_FORM_DATA, fileNameWithExt);

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

        httpClient.close();
        return responseEntity;
    }

    public byte[] delete(String urlString) throws Exception {
        logger.info("Sending DELETE to {}", urlString);

        CloseableHttpClient httpClient = HttpClients.createDefault();

        HttpDelete httpDelete = new HttpDelete(urlString);
        httpDelete.setHeader("Accept", "application/json");

        CloseableHttpResponse response = httpClient.execute(httpDelete);
        final int statusCode = response.getStatusLine().getStatusCode();

        byte[] responseBody = EntityUtils.toByteArray(response.getEntity());
        response.close();

        if (statusCode != 200) {
            throw new Exception("Error on DELETE: Code " + statusCode);
        }

        httpClient.close();

        return responseBody;
    }

}
