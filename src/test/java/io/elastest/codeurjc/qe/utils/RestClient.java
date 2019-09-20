package io.elastest.codeurjc.qe.utils;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

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

    public StringBuffer postMultipart(String urlString, String fileNameWithExt,
            byte[] body) throws Exception {

        String attachmentName = "file";
        String attachmentFileName = fileNameWithExt;
        String crlf = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";

        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(true);
        con.setRequestMethod("POST");

        con.setRequestProperty("Content-Type",
                "multipart/form-data;boundary=" + boundary);
        DataOutputStream request = new DataOutputStream(con.getOutputStream());

        request.writeBytes(twoHyphens + boundary + crlf);
        request.writeBytes(
                "Content-Disposition: form-data; name=\"" + attachmentName
                        + "\";filename=\"" + attachmentFileName + "\"" + crlf);

        request.writeBytes(crlf);
        request.write(body);
        request.writeBytes(crlf);
        request.writeBytes(twoHyphens + boundary + twoHyphens + crlf);
        request.flush();
        request.close();

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

    public StringBuffer postMultipart2(String urlString, String fileNameWithExt,
            String body) throws Exception {

        String[] splittedFileName = fileNameWithExt.split("\\.");
        File temp = File.createTempFile(splittedFileName[0],
                splittedFileName[1]);
        temp.setWritable(true);
        temp.setReadable(true);
        temp.setExecutable(false);

        BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
        bw.write(body);
        bw.close();

        // Connect to the web server endpoint
        URL serverUrl = new URL(urlString);
        HttpURLConnection urlConnection = (HttpURLConnection) serverUrl
                .openConnection();

        String boundaryString = "----SomeRandomText";
        String fileUrl = temp.getAbsolutePath();
        File logFileToUpload = new File(fileUrl);

        // Indicate that we want to write to the HTTP request body
        urlConnection.setDoOutput(true);
        urlConnection.setRequestMethod("POST");
        urlConnection.addRequestProperty("Content-Type",
                "multipart/form-data; boundary=" + boundaryString);

        OutputStream outputStreamToRequestBody = urlConnection
                .getOutputStream();
        BufferedWriter httpRequestBodyWriter = new BufferedWriter(
                new OutputStreamWriter(outputStreamToRequestBody));

        // Include value from the myFileDescription text area in the post data
        httpRequestBodyWriter.write("\n\n--" + boundaryString + "\n");
        httpRequestBodyWriter.write(
                "Content-Disposition: form-data; name=\"myFileDescription\"");
        httpRequestBodyWriter.write("\n\n");
        httpRequestBodyWriter.write("Attachment");

        // Include the section to describe the file
        httpRequestBodyWriter.write("\n--" + boundaryString + "\n");
        httpRequestBodyWriter.write("Content-Disposition: form-data;"
                + "name=\"myFile\";" + "filename=\"" + logFileToUpload.getName()
                + "\"" + "\nContent-Type: text/plain\n\n");
        httpRequestBodyWriter.flush();

        // Write the actual file contents
        FileInputStream inputStreamToLogFile = new FileInputStream(
                logFileToUpload);

        int bytesRead;
        byte[] dataBuffer = new byte[1024];
        while ((bytesRead = inputStreamToLogFile.read(dataBuffer)) != -1) {
            outputStreamToRequestBody.write(dataBuffer, 0, bytesRead);
        }

        outputStreamToRequestBody.flush();

        // Mark the end of the multipart http request
        httpRequestBodyWriter.write("\n--" + boundaryString + "--\n");
        httpRequestBodyWriter.flush();

        // Close the streams
        outputStreamToRequestBody.close();
        httpRequestBodyWriter.close();

        temp.delete();
        // Read response from web server, which will trigger the multipart HTTP
        // request to be sent.
        BufferedReader httpResponseReader = new BufferedReader(
                new InputStreamReader(urlConnection.getInputStream()));
        String lineRead;
        StringBuffer response = new StringBuffer();
        while ((lineRead = httpResponseReader.readLine()) != null) {
            response.append(lineRead);
        }
        inputStreamToLogFile.close();
        return response;
    }

}
