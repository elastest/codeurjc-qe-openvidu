package io.elastest.codeurjc.qe.openvidu;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;

import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.bonigarcia.wdm.WebDriverManager;

public class BaseTest {
    protected static final Logger logger = getLogger(lookup().lookupClass());

    public static int USERS_BY_SESSION = 7;
    public static int MAX_SESSIONS = 10;

    public static String OPENVIDU_SECRET = "MY_SECRET";
    protected static String OPENVIDU_SUT_URL;
    protected static String OPENVIDU_WEBAPP_URL;

    public static int SESSIONS = 10;
    public static int USERS_SESSION = 7;
    public static int SECONDS_OF_WAIT = 40;
    public static int BROWSER_POLL_INTERVAL = 1000;

    protected static String EUS_URL;

    protected static List<BrowserClient> browserClientList;
    public static Map<String, List<Runnable>> sessionBrowserThreads = new HashMap<>();

    protected static AwsManager awsManager;
    protected static JsonObject awsConfig;

    protected static final String STACK_NAME = "QEElasTestOpenViduWebApp";
    protected static final String CLOUD_FORMATION_FILE_NAME = "webapp.yml";

    protected static boolean isDevelopment = false;

    public static void initParameters() {
        String openviduUrl = System.getProperty("OPENVIDU_SUT_URL");
        String openviduSecret = System.getProperty("OPENVIDU_SECRET");
        String webappUrl = System.getProperty("OPENVIDU_WEBAPP_URL");
        String sessions = System.getProperty("SESSIONS");
        String usersSession = System.getProperty("USERS_SESSION");
        String secondsOfWait = System.getProperty("SECONDS_OF_WAIT");
        String browserPollInterval = System
                .getProperty("BROWSER_POLL_INTERVAL");

        if (openviduUrl != null) {
            OPENVIDU_SUT_URL = openviduUrl;
        }
        if (openviduSecret != null) {
            OPENVIDU_SECRET = openviduSecret;
        }
        if (webappUrl != null) {
            OPENVIDU_WEBAPP_URL = webappUrl;
        }
        if (sessions != null) {
            SESSIONS = Integer.parseInt(sessions);
        }
        if (usersSession != null) {
            USERS_SESSION = Integer.parseInt(usersSession);
        }
        if (secondsOfWait != null) {
            SECONDS_OF_WAIT = Integer.parseInt(secondsOfWait);
        }

        if (browserPollInterval != null) {
            BROWSER_POLL_INTERVAL = Integer.parseInt(browserPollInterval);
        }

    }

    @BeforeAll
    public static void setupClass() throws Exception {
        initParameters();
        browserClientList = new ArrayList<>();

        /* *********************************** */
        /* ******** Openvidu Sut init ******** */
        /* *********************************** */

        String sutHost = System.getenv("ET_SUT_HOST");
        String sutPort = System.getenv("ET_SUT_PORT");
        String sutProtocol = System.getenv("ET_SUT_PROTOCOL");

        if (sutHost == null) {
            OPENVIDU_SUT_URL = "http://localhost:8080/";
        } else {
            sutPort = sutPort != null ? sutPort : "8080";
            sutProtocol = sutProtocol != null ? sutProtocol : "http";

            OPENVIDU_SUT_URL = sutProtocol + "://" + sutHost + ":" + sutPort;
        }

        logger.info("Sut URL: {}", OPENVIDU_SUT_URL);

        /* ************************************ */
        /* ************* EUS init ************* */
        /* ************************************ */
        EUS_URL = System.getenv("ET_EUS_API");

        if (EUS_URL == null) {
            logger.warn("NOT Using EUS URL");
            WebDriverManager.chromedriver().setup();
        } else {
            logger.info("Using EUS URL: {}", EUS_URL);
        }

        /* *************************************** */
        /* *********** AWS config init *********** */
        /* *************************************** */

        // Aws Config
        String region = System.getenv("AWS_REGION");
        String secretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        String accessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
        String sshUser = System.getenv("AWS_SSH_USER");
        String sshPrivateKey = System.getenv("AWS_SSH_PRIVATE_KEY");

        awsManager = new AwsManager(accessKeyId, secretAccessKey, region);

        // Instances config
        String awsAmiId = System.getenv("AWS_AMI_ID");
        String instanceType = System.getenv("AWS_INSTANCE_TYPE");
        String keyName = System.getenv("AWS_KEY_NAME");
        String securityGroups = System.getenv("AWS_SECURITY_GROUPS");
        String tagSpecifications = System.getenv("AWS_TAG_SPECIFICATIONS");
        int numInstances = Integer.parseInt(System.getenv("AWS_NUM_INSTANCES"));

        awsConfig = new JsonObject();

        awsConfig.addProperty("region", region);
        awsConfig.addProperty("secretAccessKey", secretAccessKey);
        awsConfig.addProperty("accessKeyId", accessKeyId);
        awsConfig.addProperty("sshUser", sshUser);
        awsConfig.addProperty("sshPrivateKey", sshPrivateKey);

        // Instances Config
        JsonObject awsInstancesConfig = new JsonObject();
        awsInstancesConfig.addProperty("amiId", awsAmiId);
        awsInstancesConfig.addProperty("instanceType", instanceType);
        awsInstancesConfig.addProperty("keyName", keyName);
        awsInstancesConfig.addProperty("securityGroups", securityGroups);
        awsInstancesConfig.addProperty("numInstances", numInstances);

        JsonParser parser = new JsonParser();
        JsonElement tagSpecificationsElement = parser.parse(tagSpecifications);
        awsInstancesConfig.add("tagSpecifications", tagSpecificationsElement);
        awsConfig.add("awsInstancesConfig", awsInstancesConfig);

        logger.info("AWS Config: {}", awsConfig);

        /* *********************************** */
        /* ******** WebApp Sut init ******** */
        /* *********************************** */
        if (!isDevelopment) {
            deployWebapp(keyName, awsAmiId);
        }
    }

    private static void deployWebapp(String keyName, String amiID)
            throws Exception {
        List<Parameter> parameters = new ArrayList<Parameter>();
        String template = getTestResourceAsString(CLOUD_FORMATION_FILE_NAME);

        Parameter keyNameParam = awsManager.createParameter("KeyName", keyName);
        parameters.add(keyNameParam);

        Parameter openViduSecret = awsManager.createParameter("OpenViduSecret",
                OPENVIDU_SECRET);
        parameters.add(openViduSecret);

        Parameter imageId = awsManager.createParameter("ImageId", amiID);
        parameters.add(imageId);

        awsManager.createStack(STACK_NAME, template, parameters);
        awsManager.waitForStackInitCompletion(STACK_NAME, 50);

        Stack stack = awsManager.getStack(STACK_NAME);
        logger.info("Stack: {}", stack);
        if (stack != null) {
            for (Output output : stack.getOutputs()) {
                if (output.getOutputKey() == "WebsiteURL") {
                    OPENVIDU_WEBAPP_URL = output.getOutputValue();
                    break;
                }
            }
        }

        if (OPENVIDU_WEBAPP_URL == null || OPENVIDU_WEBAPP_URL.isEmpty()) {
            throw new Exception(
                    "OpenVidu WebApp Url is empty, probably because the stack was not obtained correctly");
        }

    }

    private static String getTestResourceAsString(String name)
            throws Exception {
        InputStream resource = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(name);
        String resourceStr = convertStreamToString(resource);
        logger.info("Resource {}", resourceStr);
        return resourceStr;
    }

    @BeforeEach
    public void setupTest(TestInfo info) {
        String testName = info.getTestMethod().get().getName();
        logger.info("##### Start test: {}", testName);
    }

    @AfterEach
    public void teardown(TestInfo info) {
        if (browserClientList != null) {
            for (BrowserClient browserClient : browserClientList) {
                if (browserClient != null) {
                    browserClient.dispose();
                }
            }

        }

        String testName = info.getTestMethod().get().getName();
        logger.info("##### Finish test: {}", testName);
        browserClientList = new ArrayList<>();

        sessionBrowserThreads = new HashMap<>();
    }

    @AfterAll
    public static void clear() {
        if (awsManager != null) {
            logger.info("Deleting webapp stack instance");
            awsManager.deleteStack(STACK_NAME);
        }
    }

    public static String convertStreamToString(InputStream in)
            throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder stringbuilder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            stringbuilder.append(line + "\n");
        }
        in.close();
        return stringbuilder.toString();
    }
}
