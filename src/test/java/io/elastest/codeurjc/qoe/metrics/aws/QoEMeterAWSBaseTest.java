package io.elastest.codeurjc.qoe.metrics.aws;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.elastest.codeurjc.qe.openvidu.BrowserClient;
import io.elastest.codeurjc.qe.openvidu.CountDownLatchWithException;
import io.elastest.codeurjc.qe.openvidu.CountDownLatchWithException.AbortedException;
import io.elastest.codeurjc.qe.utils.MonitoringManager;
import io.github.bonigarcia.wdm.WebDriverManager;

public class QoEMeterAWSBaseTest {
	protected static final Logger logger = getLogger(lookup().lookupClass());

	public static int USERS_BY_SESSION = 2;
	public static int MAX_SESSIONS = 1;

	public static String OPENVIDU_SECRET = "MY_SECRET";
	protected static String OPENVIDU_SUT_URL;
	protected static String OPENVIDU_WEBAPP_URL;

	public static int SECONDS_OF_WAIT = 40;
	public static int BROWSER_POLL_INTERVAL = 1000;

	protected static boolean WITH_PACKET_LOSS = false;
	protected static boolean WITH_STRESS = false;

	protected static String EUS_URL;
	protected static String ET_ETM_TJOB_ATTACHMENT_API;
	protected static String EIM_API;
	protected static String EIM_SUT_AGENT_ID;
	protected static String PACKET_LOSS_VALUE = "0.01";
	protected static String STRESS_VALUE = "1";

	protected static List<BrowserClient> browserClientList;

	protected static JsonObject awsConfig;

	protected static final String AWS_AMI_ID = "ami-0bfc646d9bb6ad37c";
	protected static final int AWS_NUM_INSTANCES = 1;
	protected static final String AWS_TAG_SPECIFICATIONS = "[ { \"resourceType\":\"instance\", \"tags\":[ {\"key\":\"Type\", \"value\":\"OpenViduLoadTest\"} ] } ]";
	protected static final String AWS_INSTANCE_TYPE = "t2.xlarge";
	protected static final String AWS_SSH_USER = "ubuntu";
	protected static final String AWS_REGION = "eu-west-1";

	protected static boolean isDevelopment = false;
	public static MonitoringManager monitoringManager;

	public static void initParameters() {
		String openviduSecret = System.getenv("OPENVIDU_SECRET");
		String secondsOfWait = System.getenv("SECONDS_OF_WAIT");
		String browserPollInterval = System.getenv("BROWSER_POLL_INTERVAL");
		String withPacketLoss = System.getenv("WITH_PACKET_LOSS");
		String packetLossValue = System.getenv("PACKET_LOSS_VALUE");
		String withStress = System.getenv("WITH_STRESS");
		String stressValue = System.getenv("STRESS_VALUE");

		if (openviduSecret != null) {
			OPENVIDU_SECRET = openviduSecret;
		}
		if (secondsOfWait != null) {
			SECONDS_OF_WAIT = Integer.parseInt(secondsOfWait);
		}

		if (browserPollInterval != null) {
			BROWSER_POLL_INTERVAL = Integer.parseInt(browserPollInterval);
		}

		if (withPacketLoss != null) {
			WITH_PACKET_LOSS = withPacketLoss.equals("true") ? true : false;
		}

		if (packetLossValue != null) {
			PACKET_LOSS_VALUE = packetLossValue;
		}

		if (withStress != null) {
			WITH_STRESS = withStress.equals("true") ? true : false;
		}

		if (stressValue != null) {
			STRESS_VALUE = stressValue;
		}
	}

	@BeforeAll
	public static void setupClass() throws Exception {
		initParameters();
		browserClientList = new ArrayList<>();
		ET_ETM_TJOB_ATTACHMENT_API = System.getenv("ET_ETM_TJOB_ATTACHMENT_API");

		/* *********************************** */
		/* ******** Openvidu Sut init ******** */
		/* *********************************** */

		String sutHost = System.getenv("ET_SUT_HOST");
		String sutPort = System.getenv("ET_SUT_PORT");
		String sutProtocol = System.getenv("ET_SUT_PROTOCOL");

		if (sutHost != null) {
			sutPort = sutPort != null && !"".equals(sutPort) ? sutPort : "4443";
			sutProtocol = sutProtocol != null && !"".equals(sutProtocol) ? sutProtocol : "https";

			OPENVIDU_SUT_URL = sutProtocol + "://" + sutHost + ":" + sutPort;
			OPENVIDU_WEBAPP_URL = sutProtocol + "://" + sutHost;
		} else {
			throw new Exception("No Sut URL");
		}

		logger.info("OpenVidu Sut URL: {}", OPENVIDU_SUT_URL);
		logger.info("OpenVidu Webapp URL: {}", OPENVIDU_WEBAPP_URL);

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

		/* ************************************ */
		/* ************* EIM init ************* */
		/* ************************************ */
		EIM_API = System.getenv("ET_EIM_API");

		if (EIM_API == null) {
			logger.warn("NOT Using EIM");
			WebDriverManager.chromedriver().setup();
		} else {
			logger.info("Using EIM API URL: {}", EIM_API);
		}

		EIM_SUT_AGENT_ID = System.getenv("ET_EIM_SUT_AGENT_ID");

		if (EIM_SUT_AGENT_ID == null) {
			logger.warn("Sut without EIM agent id");
			WebDriverManager.chromedriver().setup();
		} else {
			logger.info("Using EIM SUT Agent id: {}", EIM_SUT_AGENT_ID);
		}

		/* *************************************** */
		/* *********** AWS config init *********** */
		/* *************************************** */

		// Aws Config
		String secretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
		String accessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
		String sshPrivateKey = System.getenv("AWS_SSH_PRIVATE_KEY");

		// Instances config
		String keyName = System.getenv("AWS_KEY_NAME");
		String securityGroups = System.getenv("AWS_SECURITY_GROUPS");

		JsonParser parser = new JsonParser();
		awsConfig = new JsonObject();

		awsConfig.addProperty("region", AWS_REGION);
		awsConfig.addProperty("secretAccessKey", secretAccessKey);
		awsConfig.addProperty("accessKeyId", accessKeyId);
		awsConfig.addProperty("sshUser", AWS_SSH_USER);
		awsConfig.addProperty("sshPrivateKey", sshPrivateKey.replace("\\r\\n", System.lineSeparator()));

		// Instances Config

		JsonObject awsInstancesConfig = new JsonObject();
		awsInstancesConfig.addProperty("amiId", AWS_AMI_ID);
		awsInstancesConfig.addProperty("instanceType", AWS_INSTANCE_TYPE);
		awsInstancesConfig.addProperty("keyName", keyName);

		awsInstancesConfig.addProperty("numInstances", AWS_NUM_INSTANCES);
		JsonArray securityGroupsElement = parser.parse(securityGroups).getAsJsonArray();
		awsInstancesConfig.add("securityGroups", securityGroupsElement);

		JsonArray tagSpecificationsElement = parser.parse(AWS_TAG_SPECIFICATIONS).getAsJsonArray();
		awsInstancesConfig.add("tagSpecifications", tagSpecificationsElement);
		awsConfig.add("awsInstancesConfig", awsInstancesConfig);

		logger.info("AWS Config: {}", awsConfig);

		if (OPENVIDU_WEBAPP_URL == null || OPENVIDU_WEBAPP_URL.isEmpty()) {
			throw new Exception("OpenVidu WebApp Url is empty, probably because the stack was not obtained correctly");
		}

		monitoringManager = new MonitoringManager();
		logger.info("Configured new Monitoring Manager: {}", monitoringManager);
	}

	@BeforeEach
	public void setupTest(TestInfo info) {
		String testName = info.getTestMethod().get().getName();
		logger.info("##### Start test: {}", testName);
	}

	@AfterEach
	public void teardown(TestInfo info) {
		if (browserClientList != null) {
			ExecutorService browserDisposeTaskExecutor = Executors.newCachedThreadPool();
			CountDownLatchWithException waitForBrowsersEndLatch = new CountDownLatchWithException(
					browserClientList.size());
			List<Runnable> browserThreads = new ArrayList<>();
			for (BrowserClient browserClient : browserClientList) {
				if (browserClient != null) {
					browserThreads.add(() -> {
						browserClient.dispose();
						waitForBrowsersEndLatch.countDown();
					});
				}
			}

			for (Runnable r : browserThreads) {
				browserDisposeTaskExecutor.execute(r);
			}

			try {
				waitForBrowsersEndLatch.await();
			} catch (AbortedException e1) {
			}

			browserDisposeTaskExecutor.shutdown();
			try {
				browserDisposeTaskExecutor.awaitTermination(5, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
			}
		}

		String testName = info.getTestMethod().get().getName();
		logger.info("##### Finish test: {}", testName);
		browserClientList = new ArrayList<>();

	}

	@AfterAll
	public static void clear() {

	}

	public static String convertStreamToString(InputStream in) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		StringBuilder stringbuilder = new StringBuilder();
		String line = null;
		while ((line = reader.readLine()) != null) {
			stringbuilder.append(line + "\n");
		}
		in.close();
		return stringbuilder.toString();
	}

	public static void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
		}
	}
}
