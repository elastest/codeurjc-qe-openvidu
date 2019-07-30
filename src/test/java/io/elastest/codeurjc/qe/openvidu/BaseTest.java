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

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.Stack;

import io.github.bonigarcia.wdm.WebDriverManager;

public class BaseTest {
    protected static final Logger logger = getLogger(lookup().lookupClass());

    public static int USERS_BY_SESSION = 7;
    public static int MAX_SESSIONS = 10;

    public static String OPENVIDU_SECRET = "MY_SECRET";
    protected static String OPENVIDU_SUT_URL = "https://localhost:4443/";
    protected static String OPENVIDU_WEBAPP_URL = "http://localhost:8080/";

    public static int SESSIONS = 10;
    public static int USERS_SESSION = 7;
    public static int SECONDS_OF_WAIT = 40;
    public static int BROWSER_POLL_INTERVAL = 1000;

    protected static String EUS_URL;

    protected static List<BrowserClient> browserClientList;
    public static Map<String, List<Runnable>> sessionBrowserThreads = new HashMap<>();
    protected static Map<String, Object> awsConfig;

    protected static AmazonCloudFormation awsCloudFormation;
    protected static final String STACK_NAME = "OpenViduWebAppStack";
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

        // Instances config
        String awsInstancesConfig = System.getenv("AWS_AWS_INSTANCES_CONFIG");
        String instanceType = System.getenv("AWS_INSTANCE_TYPE");
        String keyName = System.getenv("AWS_KEY_NAME");
        String securityGroups = System.getenv("AWS_SECURITY_GROUPS");
        String tagSpecifications = System.getenv("AWS_TAG_SPECIFICATIONS");
        String tags = System.getenv("AWS_TAGS");
        int numInstances = Integer.parseInt(System.getenv("AWS_NUM_INSTANCES"));

        awsConfig = new HashMap<String, Object>();

        awsConfig.put("region", region);
        awsConfig.put("secretAccessKey", secretAccessKey);
        awsConfig.put("accessKeyId", accessKeyId);
        awsConfig.put("sshUser", sshUser);
        awsConfig.put("sshPrivateKey", sshPrivateKey);
        awsConfig.put("awsInstancesConfig", awsInstancesConfig);
        awsConfig.put("instanceType", instanceType);
        awsConfig.put("keyName", keyName);
        awsConfig.put("securityGroups", securityGroups);
        awsConfig.put("tagSpecifications", tagSpecifications);
        awsConfig.put("tags", tags);
        awsConfig.put("numInstances", numInstances);

        logger.info("AWS Config: {}", awsConfig);

        /* *********************************** */
        /* ******** WebApp Sut init ******** */
        /* *********************************** */

        if (!isDevelopment) {
            BasicAWSCredentials credentials = new BasicAWSCredentials(
                    accessKeyId, secretAccessKey);
            AWSStaticCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(
                    credentials);

            awsCloudFormation = AmazonCloudFormationClient.builder()
                    .withRegion(region).withCredentials(credentialsProvider)
                    .build();

            CreateStackRequest createRequest = new CreateStackRequest();
            createRequest.setStackName(STACK_NAME);
            createRequest.setTemplateBody(convertStreamToString(BaseTest.class
                    .getResourceAsStream(CLOUD_FORMATION_FILE_NAME)));
            logger.info("Creating a stack called "
                    + createRequest.getStackName() + ".");
            awsCloudFormation.createStack(createRequest);

            for (Stack stack : awsCloudFormation
                    .describeStacks(new DescribeStacksRequest()).getStacks()) {
                DescribeStackResourcesRequest stackResourceRequest = new DescribeStackResourcesRequest();
                stackResourceRequest.setStackName(stack.getStackName());

                for (Output output : stack.getOutputs()) {
                    if (output.getOutputKey() == "WebsiteURL") {
                        OPENVIDU_WEBAPP_URL = output.getOutputValue();
                        break;
                    }
                }
            }
        }

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
    public void clear() {
        if (awsCloudFormation != null) {
            logger.info("Deleting webapp stack instance");
            DeleteStackRequest deleteRequest = new DeleteStackRequest();
            deleteRequest.setStackName(STACK_NAME);
            awsCloudFormation.deleteStack(deleteRequest);
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
