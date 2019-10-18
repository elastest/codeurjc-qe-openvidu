package io.elastest.codeurjc.qe.openvidu;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

/**
 * Hello world!
 *
 */
public class CodeURJCQEOpenViduApp {
    public static void main(String[] args) {
        try {
            URL url = new URL(
                    "https://download.wetransfer.com//eu2/40c7358ca9c3c46bb6efbefad486461020191017120221/57d6652b1e7dbeb45aa49ca5745a853da49633e7/fakevideo_with_padding2.y4m?cf=y&token=eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjE1NzEzODc0MjYsInVuaXF1ZSI6IjQwYzczNThjYTljM2M0NmJiNmVmYmVmYWQ0ODY0NjEwMjAxOTEwMTcxMjAyMjEiLCJmaWxlbmFtZSI6ImZha2V2aWRlb193aXRoX3BhZGRpbmcyLnk0bSIsImhvdCI6ZmFsc2UsImJ5dGVzX2VzdGltYXRlZCI6NTk3MjA0NjM2LCJ3YXliaWxsX3VybCI6Imh0dHA6Ly9wcm9kdWN0aW9uLmJhY2tlbmQuc2VydmljZS5ldS13ZXN0LTEud3Q6OTI5Mi93YXliaWxsL3YxL2Y1YzFlMGQ3MWFjZDUzODc3YzlmZTU4NDQ1NWQ1YzUwZTgzNzAwMjg1OTJjMWRlNzYwZmM5NjY0OWE3MjM5Y2UzYmY3YWYzOWE4MGY2MGIyYzE3NDU0NzkwMWZlMzk5OWU0NzE1ZTkxZjQ5YjgyM2VjNTkwY2UzOGE2MzMzNGZkIiwiY2FsbGJhY2siOiJ7XCJmb3JtZGF0YVwiOntcImFjdGlvblwiOlwiaHR0cDovL3Byb2R1Y3Rpb24uZnJvbnRlbmQuc2VydmljZS5ldS13ZXN0LTEud3Q6MzAwMC9hcGkvYmFja2VuZC90cmFuc2ZlcnMvNDBjNzM1OGNhOWMzYzQ2YmI2ZWZiZWZhZDQ4NjQ2MTAyMDE5MTAxNzEyMDIyMS9kb3dubG9hZHMvNzU0MjQ2NjM3NS9jb21wbGV0ZWQvYjBiYzM4MWQ1NWU5MzFhYmViZDM5NWJkODEyYmU4MzgyMDE5MTAxNzEyMDIyMVwifSxcImZvcm1cIjp7XCJzdGF0dXNcIjpbXCJwYXJhbVwiLFwic3RhdHVzXCJdLFwiZG93bmxvYWRfaWRcIjpcIjc1NDI0NjYzNzVcIn19In0.CLNZzPnW6S8o_QOkb5PKimR2xMjX-CNFKVl1ZGQpnZA");

            ReadableByteChannel readChannel = Channels
                    .newChannel(url.openStream());
            FileOutputStream fileOS = new FileOutputStream("/tmp/aglia.wav");
            FileChannel writeChannel = fileOS.getChannel();
            writeChannel.transferFrom(readChannel, 0, Long.MAX_VALUE);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
