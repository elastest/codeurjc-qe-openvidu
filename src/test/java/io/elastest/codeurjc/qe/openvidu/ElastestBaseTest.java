package io.elastest.codeurjc.qe.openvidu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;

import io.github.bonigarcia.wdm.WebDriverManager;

public class ElastestBaseTest {
    protected static final Logger logger = LoggerFactory
            .getLogger(ElastestBaseTest.class);

    protected static String eusURL;
    protected static String openviduSutUrl;
    protected static String openviduWebAppUrl;

    protected static List<WebDriver> driverList;

    protected static Map<String, Object> awsConfig;

    protected static AmazonCloudFormation awsCloudFormation;

    @BeforeAll
    public static void setupClass() {
        driverList = new ArrayList<>();

        /* *********************************** */
        /* ******** Openvidu Sut init ******** */
        /* *********************************** */

        String sutHost = System.getenv("ET_SUT_HOST");
        String sutPort = System.getenv("ET_SUT_PORT");
        String sutProtocol = System.getenv("ET_SUT_PROTOCOL");

        if (sutHost == null) {
            openviduSutUrl = "http://localhost:8080/";
        } else {
            sutPort = sutPort != null ? sutPort : "8080";
            sutProtocol = sutProtocol != null ? sutProtocol : "http";

            openviduSutUrl = sutProtocol + "://" + sutHost + ":" + sutPort;
        }

        if (sutHost == null) {
            openviduSutUrl = "http://localhost:8080/";
        } else {
            sutPort = sutPort != null ? sutPort : "8080";
            sutProtocol = sutProtocol != null ? sutProtocol : "http";

            openviduSutUrl = sutProtocol + "://" + sutHost + ":" + sutPort;
        }
        logger.info("Sut URL: {}", openviduSutUrl);

        /* ************************************ */
        /* ************* EUS init ************* */
        /* ************************************ */
        eusURL = System.getenv("ET_EUS_API");

        if (eusURL == null) {
            logger.warn("NOT Using EUS URL");
            WebDriverManager.chromedriver().setup();
        } else {
            logger.info("Using EUS URL: {}", eusURL);
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
        // TODO

        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKeyId,
                secretAccessKey);
        AWSStaticCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(
                credentials);

        awsCloudFormation = AmazonCloudFormationClient.builder()
                .withRegion(region).withCredentials(credentialsProvider)
                .build();
        
//        awsCloudFormation.dep

        // openviduWebAppUrl =

    }

    @BeforeEach
    public void setupTest(TestInfo info) {
        String testName = info.getTestMethod().get().getName();
        logger.info("##### Start test: {}", testName);
    }

    @AfterEach
    public void teardown(TestInfo info) {
        if (driverList != null) {
            for (WebDriver driver : driverList) {
                if (driver != null) {
                    driver.quit();
                }
            }

        }

        String testName = info.getTestMethod().get().getName();
        logger.info("##### Finish test: {}", testName);
        driverList = new ArrayList<>();
    }

}
