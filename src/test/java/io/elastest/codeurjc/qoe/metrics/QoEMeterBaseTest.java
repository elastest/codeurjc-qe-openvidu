package io.elastest.codeurjc.qoe.metrics;

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

import io.elastest.codeurjc.qe.openvidu.BrowserClient;
import io.elastest.codeurjc.qe.openvidu.CountDownLatchWithException;
import io.elastest.codeurjc.qe.openvidu.CountDownLatchWithException.AbortedException;
import io.elastest.codeurjc.qe.utils.MonitoringManager;
import io.github.bonigarcia.wdm.WebDriverManager;

public class QoEMeterBaseTest {
    protected static final Logger logger = getLogger(lookup().lookupClass());

    public static int USERS_BY_SESSION = 2;
    public static int MAX_SESSIONS = 1;

    public static String OPENVIDU_SECRET = "MY_SECRET";
    protected static String OPENVIDU_SUT_URL;
    protected static String OPENVIDU_PROTOCOL_AND_HOST_URL;

    public static int SECONDS_OF_WAIT = 40;
    public static int BROWSER_POLL_INTERVAL = 1000;

    protected static String EUS_URL;
    protected static String ET_ETM_TJOB_ATTACHMENT_API;

    protected static List<BrowserClient> browserClientList;

    protected static boolean isDevelopment = false;

    protected static String FAKE_VIDEO_URL = "http://public.openvidu.io/fakevideo_with_padding2.y4m";
    protected static String FAKE_AUDIO_URL = "http://public.openvidu.io/fakeaudio_with_padding.wav";
    protected static String FAKE_VIDEO_WITH_PADDING_NAME = "fakevideo_with_padding2.y4m";
    protected static String FAKE_AUDIO_WITH_PADDING_NAME = "fakeaudio_with_padding.wav";

    // in milliseconds
    protected static long FAKE_VIDEO_AND_AUDIO_DURATION = 30000;
    protected static long FAKE_VIDEO_AND_AUDIO_PADDING_DURATION = 12000;

    protected static boolean USE_FAKE_QOE_VMAF_CSV = false;
    protected static String FAKE_CSV_URL = "https://raw.githubusercontent.com/elastest/codeurjc-qe-openvidu/master/src/test/resources/data/vmaf.csv";
    protected static String FAKE_CSV_NAME = "vmaf.csv";

    public static MonitoringManager monitoringManager;

    public static void initParameters() {
        String openviduSecret = System.getenv("OPENVIDU_SECRET");
        String secondsOfWait = System.getenv("SECONDS_OF_WAIT");
        String browserPollInterval = System.getenv("BROWSER_POLL_INTERVAL");

        String fakeVideoUrl = System.getenv("FAKE_VIDEO_URL");
        String fakeAudioUrl = System.getenv("FAKE_AUDIO_URL");
        String fakeVideoWithPaddingName = System.getenv("FAKE_VIDEO_WITH_PADDING_NAME");
        String fakeAudioWithPaddingName = System.getenv("FAKE_AUDIO_WITH_PADDING_NAME");

        String fakeVideoAndAudioDuration = System.getenv("FAKE_VIDEO_AND_AUDIO_DURATION");
        String fakeVideoAndAudioPaddingDuration = System
                .getenv("FAKE_VIDEO_AND_AUDIO_PADDING_DURATION");

        String useFakeQoEVmafCSV = System.getenv("USE_FAKE_QOE_VMAF_CSV");
        String fakeCsvUrl = System.getenv("FAKE_CSV_URL");
        String fakeCsvName = System.getenv("FAKE_CSV_NAME");

        if (openviduSecret != null) {
            OPENVIDU_SECRET = openviduSecret;
        }
        if (secondsOfWait != null) {
            SECONDS_OF_WAIT = Integer.parseInt(secondsOfWait);
        }

        if (browserPollInterval != null) {
            BROWSER_POLL_INTERVAL = Integer.parseInt(browserPollInterval);
        }

        if (fakeVideoUrl != null) {
            FAKE_VIDEO_URL = fakeVideoUrl;
        }

        if (fakeAudioUrl != null) {
            FAKE_AUDIO_URL = fakeAudioUrl;
        }

        if (fakeVideoWithPaddingName != null) {
            FAKE_VIDEO_WITH_PADDING_NAME = fakeVideoWithPaddingName;
        }

        if (fakeAudioWithPaddingName != null) {
            FAKE_AUDIO_WITH_PADDING_NAME = fakeAudioWithPaddingName;
        }

        if (fakeVideoAndAudioDuration != null) {
            FAKE_VIDEO_AND_AUDIO_DURATION = Long.valueOf(fakeVideoAndAudioDuration);
        }

        if (fakeVideoAndAudioPaddingDuration != null) {
            FAKE_VIDEO_AND_AUDIO_PADDING_DURATION = Long.valueOf(fakeVideoAndAudioPaddingDuration);
        }

        if (useFakeQoEVmafCSV != null) {
            USE_FAKE_QOE_VMAF_CSV = "true".equals(useFakeQoEVmafCSV) ? true : false;
        }

        if (fakeCsvUrl != null) {
            FAKE_CSV_URL = fakeCsvUrl;
        }

        if (fakeCsvName != null) {
            FAKE_CSV_NAME = fakeCsvName;
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
            OPENVIDU_PROTOCOL_AND_HOST_URL = sutProtocol + "://" + sutHost;
        } else {
            throw new Exception("No Sut URL");
        }

        logger.info("OpenVidu Sut URL: {}", OPENVIDU_SUT_URL);
        logger.info("OpenVidu Webapp URL: {}", OPENVIDU_PROTOCOL_AND_HOST_URL);

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

        if (OPENVIDU_PROTOCOL_AND_HOST_URL == null || OPENVIDU_PROTOCOL_AND_HOST_URL.isEmpty()) {
            throw new Exception(
                    "OpenVidu WebApp Url is empty, probably because the stack was not obtained correctly");
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
