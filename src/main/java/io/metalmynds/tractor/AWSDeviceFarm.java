//
// Copyright 2015-2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License").
// You may not use this file except in compliance with the License.
// A copy of the License is located at
//
// http://aws.amazon.com/apache2.0
//
// or in the "license" file accompanying this file. This file is distributed
// on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied. See the License for the specific language governing
// permissions and limitations under the License.
//
package io.metalmynds.tractor;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.devicefarm.AWSDeviceFarmClient;
import com.amazonaws.services.devicefarm.model.*;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.util.IOUtils;
import io.metalmynds.tractor.frameworks.AppiumJavaTestNGTest;
import io.metalmynds.tractor.frameworks.InstrumentationTest;
import io.metalmynds.tractor.frameworks.XCTestUITest;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import io.metalmynds.tractor.frameworks.AppiumWebJavaJUnitTest;
import io.metalmynds.tractor.frameworks.AppiumWebJavaTestNGTest;
import io.metalmynds.tractor.frameworks.AppiumWebPythonTest;
import io.metalmynds.tractor.frameworks.AppiumJavaJUnitTest;
import io.metalmynds.tractor.frameworks.AppiumPythonTest;
import io.metalmynds.tractor.frameworks.CalabashTest;
import io.metalmynds.tractor.frameworks.UIAutomationTest;
import io.metalmynds.tractor.frameworks.UIAutomatorTest;
import io.metalmynds.tractor.frameworks.XCTestTest;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * AWS Device Farm API wrapper class.
 */
public class AWSDeviceFarm {
    private AWSDeviceFarmClient api;
    private PrintStream log;

    private static final Integer DEFAULT_JOB_TIMEOUT_MINUTE = 60;

    //// Constructors

    /**
     * AWSDeviceFarm constructor.
     *
     * @param roleArn Role ARN to use for authentication.
     */
    public AWSDeviceFarm(String roleArn) {
        this(null, roleArn);
    }

    /**
     * AWSDeviceFarm constructor.
     *
     * @param creds AWSCredentials to use for authentication.
     */
    public AWSDeviceFarm(AWSCredentials creds) {
        this(creds, null);
    }

    /**
     * Private AWSDeviceFarm constructor. Uses the roleArn to generate STS creds if the roleArn isn't null; otherwise
     * just uses the AWSCredentials creds.
     *
     * @param creds   AWSCredentials creds to use for authentication.
     * @param roleArn Role ARN to use for authentication.
     */
    private AWSDeviceFarm(AWSCredentials creds, String roleArn) {
        if (roleArn != null) {
            STSAssumeRoleSessionCredentialsProvider sts = new STSAssumeRoleSessionCredentialsProvider
                    .Builder(roleArn, RandomStringUtils.randomAlphanumeric(8))
                    .build();
            creds = sts.getCredentials();
        }

        ClientConfiguration clientConfiguration = new ClientConfiguration().withUserAgent("AWS Device Farm - Jenkins v1.0");
        api = new AWSDeviceFarmClient(creds, clientConfiguration);
        api.setServiceNameIntern("io/metalmynds/tractor");
    }

    //// Builder Methods

    /**
     * Logger setter.
     *
     * @param logger The log print stream.
     * @return The AWSDeviceFarm object.
     */
    public AWSDeviceFarm withLogger(PrintStream logger) {
        this.log = logger;
        return this;
    }

    //// AWS Device Farm Wrapper Methods

    /**
     * Get all Device Farm projects.
     *
     * @return A List of the Device Farm projects.
     */
    public List<Project> getProjects() {
        ListProjectsResult result = api.listProjects(new ListProjectsRequest());
        if (result == null) {
            return new ArrayList<Project>();
        } else {
            return result.getProjects();
        }
    }

    /**
     * Get Device Farm project by name.
     *
     * @param projectName String name of the Device Farm project.
     * @return The Device Farm project.
     * @throws AWSDeviceFarmException if project named is not found.
     */
    public Project getProject(String projectName) throws AWSDeviceFarmException {
        for (Project p : getProjects()) {
            if (p.getName().equals(projectName)) {
                return p;
            }
        }
        throw new AWSDeviceFarmException(String.format("Project '%s' not found.", projectName));
    }

    /**
     * Get Device Farm device pools for a given Device Farm project.
     *
     * @param projectName String name of the Device Farm project.
     * @return A List of the Device Farm device pools.
     * @throws AWSDeviceFarmException if get device pool list fails, check projectName.
     */
    public List<DevicePool> getDevicePools(String projectName) throws AWSDeviceFarmException {
        return getDevicePools(getProject(projectName));
    }

    /**
     * Get Device Farm device pools for a given Device Farm project.
     *
     * @param project Device Farm Project.
     * @return A List of the Device Farm device pools.
     */
    public List<DevicePool> getDevicePools(Project project) {
        ListDevicePoolsResult poolsResult = api.listDevicePools(new ListDevicePoolsRequest().withArn(project.getArn()));
        List<DevicePool> pools = poolsResult.getDevicePools();
        return pools;
    }

    /**
     * Get Device Farm device pool by Device Farm project and device pool name.
     *
     * @param projectName    String name of the Device Farm project.
     * @param devicePoolName String name of the device pool.
     * @return The Device Farm device pool.
     * @throws AWSDeviceFarmException if get device pool fails.
     */
    public DevicePool getDevicePool(String projectName, String devicePoolName) throws AWSDeviceFarmException {
        return getDevicePool(getProject(projectName), devicePoolName);
    }

    /**
     * Get Device Farm device pool by Device Farm project and device pool name.
     *
     * @param project        The Device Farm project.
     * @param devicePoolName String name of the device pool.
     * @return The Device Farm device pool.
     * @throws AWSDeviceFarmException if get device pool fails.
     */
    public DevicePool getDevicePool(Project project, String devicePoolName) throws AWSDeviceFarmException {
        List<DevicePool> pools = getDevicePools(project);

        for (DevicePool dp : pools) {
            if (dp.getName().equals(devicePoolName)) {
                return dp;
            }
        }

        throw new AWSDeviceFarmException(String.format("DevicePool '%s' not found.", devicePoolName));
    }

    /**
     * Gets projects list of devices.
     *
     * @param project The Device Farm project.
     * @return The Device Farm device list.
     * @throws AWSDeviceFarmException if get project devices fails.
     */
    public List<Device> getProjectDevices(Project project) throws AWSDeviceFarmException {
        return api.listDevices(new ListDevicesRequest().withArn(project.getArn())).getDevices();
    }

    /**
     * Gets projects list of devices.
     *
     * @param project The Device Farm project name.
     * @return The Device Farm device list.
     * @throws AWSDeviceFarmException if get project devices fails.
     */
    public List<Device> getProjectDevices(String project) throws AWSDeviceFarmException {
        return api.listDevices(new ListDevicesRequest().withArn(getProject(project).getArn())).getDevices();
    }

    /**
     * Upload an app to Device Farm to be tested.
     *
     * @param project     The Device Farm project to upload to.
     * @param appArtifact String path to the app to be uploaded to Device Farm.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of application artifact is unrecognised or if upload fails.
     */
    public Upload uploadApp(Project project, String appArtifact) throws AWSDeviceFarmException {
        AWSDeviceFarmUploadType type;
        if (appArtifact.toLowerCase().endsWith("apk")) {
            type = AWSDeviceFarmUploadType.ANDROID_APP;
        } else if (appArtifact.toLowerCase().endsWith("ipa") || appArtifact.toLowerCase().endsWith("zip")) {
            type = AWSDeviceFarmUploadType.IOS_APP;
        } else {
            throw new AWSDeviceFarmException(String.format("Unknown app artifact to upload: %s", appArtifact));
        }

        return upload(project, appArtifact, type);
    }

    /**
     * Upload an extra data file to Device Farm.
     *
     * @param project           The Device Farm project to upload to.
     * @param extraDataArtifact String path to the extra data to be uploaded to Device Farm.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of data file is not a zip archive.
     */
    public Upload uploadExtraData(Project project, String extraDataArtifact) throws AWSDeviceFarmException {
        AWSDeviceFarmUploadType type;
        if (extraDataArtifact.toLowerCase().endsWith("zip")) {
            type = AWSDeviceFarmUploadType.EXTERNAL_DATA;
        } else {
            throw new AWSDeviceFarmException(String.format("Unknown extra data file artifact to upload: %s", extraDataArtifact));
        }

        return upload(project, extraDataArtifact, type);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of test artifact is unrecognised or if upload fails.
     */
    public Upload uploadTest(Project project, InstrumentationTest test) throws AWSDeviceFarmException {
        return upload(project, test.getArtifact(), AWSDeviceFarmUploadType.INSTRUMENTATION);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of test artifact is unrecognised or if upload fails.
     */
    public Upload uploadTest(Project project, CalabashTest test) throws AWSDeviceFarmException {
        return upload(project, test.getFeatures(), AWSDeviceFarmUploadType.CALABASH);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of test artifact is unrecognised or if upload fails.
     */
    public Upload uploadTest(Project project, UIAutomatorTest test) throws AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.UIAUTOMATOR);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of test artifact is unrecognised or if upload fails.
     */
    public Upload uploadTest(Project project, UIAutomationTest test) throws AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.UIAUTOMATION);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of test artifact is unrecognised or if upload fails.
     */
    public Upload uploadTest(Project project, XCTestTest test) throws AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.XCTEST);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of test artifact is unrecognised or if upload fails.
     */
    public Upload uploadTest(Project project, XCTestUITest test) throws AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.XCTEST_UI);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of test artifact is unrecognised or if upload fails.
     */
    public Upload uploadTest(Project project, AppiumJavaTestNGTest test) throws AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_JAVA_TESTNG);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of test artifact is unrecognised or if upload fails.
     */
    public Upload uploadTest(Project project, AppiumJavaJUnitTest test) throws AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_JAVA_JUNIT);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of test artifact is unrecognised or if upload fails.
     */
    public Upload uploadTest(Project project, AppiumPythonTest test) throws AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_PYTHON);
    }


    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of test artifact is unrecognised or if upload fails.
     */
    public Upload uploadTest(Project project, AppiumWebJavaTestNGTest test) throws AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_WEB_JAVA_TESTNG);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of test artifact is unrecognised or if upload fails.
     */
    public Upload uploadTest(Project project, AppiumWebJavaJUnitTest test) throws AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_WEB_JAVA_JUNIT);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if type of test artifact is unrecognised or if upload fails.
     */
    public Upload uploadTest(Project project, AppiumWebPythonTest test) throws AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_WEB_PYTHON);
    }

    /**
     * Private method to handle uploading apps and tests to Device Farm.
     *
     * @param project    The Device Farm project to upload to.
     * @param artifact   Possibly glob-y path to the file to be uploaded.
     * @param uploadType The type of upload (app/test/etc.).
     * @return The Device Farm Upload object.
     * @throws IOException if upload is interrupted.
     * @throws AWSDeviceFarmException if file is not found or artifact filename not specified.
     */
    private Upload upload(Project project, String artifact, AWSDeviceFarmUploadType uploadType) throws AWSDeviceFarmException {
        if (artifact == null || artifact.isEmpty()) {
            throw new AWSDeviceFarmException("Must have an artifact path.");
        }

//        File file = getArtifactFile(env.expand(artifact));
        File file = new File(artifact);

        if (file == null || !file.exists()) {
            throw new AWSDeviceFarmException(String.format("File artifact %s not found.", artifact));
        }

        return upload(file, project, uploadType);
    }

    /**
     * Private method to handle uploading apps and tests to Device Farm.
     *
     * @param file       The file to upload.
     * @param project    The Device Farm project to upload to.
     * @param uploadType The type of upload (app/test/etc.).
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if upload fails or is interrupted.
     */
    private Upload upload(File file, Project project, AWSDeviceFarmUploadType uploadType) throws AWSDeviceFarmException {
        return upload(file, project, uploadType, true);
    }

    /**
     * Private method to handle upload apps and tests to Device Farm.
     *
     * @param file        The file to upload.
     * @param project     TheDevice Farm project to upload to.
     * @param uploadType  The type of upload (app/test/etc.).
     * @param synchronous Whether or not to wait for the upload to complete before returning.
     * @return The Device Farm Upload object.
     * @throws AWSDeviceFarmException if upload fails or is interrupted.
     */
    private Upload upload(File file, Project project, AWSDeviceFarmUploadType uploadType, Boolean synchronous) throws  AWSDeviceFarmException {
        CreateUploadRequest appUploadRequest = new CreateUploadRequest()
                .withName(file.getName())
                .withProjectArn(project.getArn())
                .withContentType("application/octet-stream")
                .withType(uploadType.toString());
        Upload upload = api.createUpload(appUploadRequest).getUpload();

        CloseableHttpClient httpClient = HttpClients.createSystem();
        HttpPut httpPut = new HttpPut(upload.getUrl());
        httpPut.setHeader("Content-Type", upload.getContentType());

        FileEntity entity = new FileEntity(file);
        httpPut.setEntity(entity);

        HttpResponse response;

        try {
            response = httpClient.execute(httpPut);
        } catch (IOException ex) {
            throw new AWSDeviceFarmException("Upload failed to execute!", ex);
        }

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new AWSDeviceFarmException(String.format("Upload returned non-200 responses: %d", response.getStatusLine().getStatusCode()));
        }

        if (synchronous) {
            while (true) {
                GetUploadRequest describeUploadRequest = new GetUploadRequest()
                        .withArn(upload.getArn());
                GetUploadResult describeUploadResult = api.getUpload(describeUploadRequest);
                String status = describeUploadResult.getUpload().getStatus();

                if ("SUCCEEDED".equalsIgnoreCase(status)) {
                    break;
                } else if ("FAILED".equalsIgnoreCase(status)) {
                    throw new AWSDeviceFarmException(String.format("Upload %s failed! Server Message: %s", file.getName(), describeUploadResult.getUpload().getMetadata()));
                } else {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        throw new AWSDeviceFarmException("Wait for upload was interrupted!", e);
                    }
                }
            }
        }

        return upload;
    }

    /**
     * Schedule a test run on Device Farm.
     *
     * @param projectArn       The ARN of the Device Farm project to run the test on.
     * @param name              The name of the test run.
     * @param appArn            The ARN of the app to test.
     * @param devicePoolArn     The ARN of the device pool to test against.
     * @param test              The run test.
     * @param jobTimeoutMinutes The maximum run time before automatic termination.
     * @param configuration The run configuration.
     * @return The result of the schedle run.
     */
    public ScheduleRunResult scheduleRun(String projectArn,
                                         String name,
                                         String appArn,
                                         String devicePoolArn,
                                         ScheduleRunTest test,
                                         Integer jobTimeoutMinutes,
                                         ScheduleRunConfiguration configuration) {
        ScheduleRunRequest request = new ScheduleRunRequest()
                .withProjectArn(projectArn)
                .withName(name)
                .withDevicePoolArn(devicePoolArn)
                .withTest(test);

        ExecutionConfiguration exeConfiguration = new ExecutionConfiguration();
        if (!jobTimeoutMinutes.equals(DEFAULT_JOB_TIMEOUT_MINUTE)) {
            exeConfiguration.setJobTimeoutMinutes(jobTimeoutMinutes);
            request.withExecutionConfiguration(exeConfiguration);
        }

        if (configuration != null) {
            request.withConfiguration(configuration);
        }

        if (appArn != null) {
            request.withAppArn(appArn);
        }

        return api.scheduleRun(request);
    }

    public GetRunResult describeRun(String runArn) {
        return api.getRun(new GetRunRequest()
                .withArn(runArn));
    }

    public ListArtifactsResult listArtifacts(String runArn, ArtifactCategory category) {
        ListArtifactsRequest request = new ListArtifactsRequest()
                .withArn(runArn)
                .withType(category);

        return api.listArtifacts(request);
    }

    public List<Path> getArtifacts(String runArn, File destination) throws IOException, InterruptedException {

        List<Path> artifactPaths = new ArrayList<>();

        Map<String, File> jobs = getJobs(runArn, destination);
        Map<String, File> suites = getSuites(runArn, jobs);
        Map<String, File> tests = getTests(runArn, suites);

        for (ArtifactCategory category : new ArrayList<ArtifactCategory>(Arrays.asList(ArtifactCategory.values()))) {
            ListArtifactsResult result = listArtifacts(runArn, category);
            for (Artifact artifact : result.getArtifacts()) {
                String arn = artifact.getArn().split(":")[6];
                String testArn = arn.substring(0, arn.lastIndexOf("/"));
                String id = arn.substring(arn.lastIndexOf("/") + 1);
                String extension = artifact.getExtension().replaceFirst("^\\.", "");
                Path artifactTargetPath = Paths.get(tests.get(testArn).getAbsolutePath(), String.format("%s-%s.%s", artifact.getName(), id, extension));
                URL artifactSourcePath = new URL(artifact.getUrl());
                Files.write(artifactTargetPath, IOUtils.toByteArray(artifactSourcePath.openStream()));
                artifactPaths.add(artifactTargetPath);
            }
        }

        return artifactPaths;
    }

    private Map<String, File> getSuites(String runArn, Map<String, File> jobs) throws IOException, InterruptedException {
        Map<String, File> suites = new HashMap<String, File>();
        String components[] = runArn.split(":");
        // constructing job ARN for each job using the run ARN
        components[5] = "job";
        for (Map.Entry<String, File> jobEntry : jobs.entrySet()) {
            String jobArn = jobEntry.getKey();
            components[6] = jobArn;
            String fullJobArn = StringUtils.join(components, ":");
            ListSuitesResult result = listSuites(fullJobArn);
            for (Suite suite : result.getSuites()) {
                String arn = suite.getArn().split(":")[6];
                suites.put(arn, new File(jobs.get(jobArn), suite.getName()));
                suites.get(arn).mkdirs();
            }
        }
        return suites;
    }

    private Map<String, File> getTests(String runArn, Map<String, File> suites) throws IOException, InterruptedException {
        Map<String, File> tests = new HashMap<String, File>();

        String components[] = runArn.split(":");
        // constructing suite ARN for each job using the run ARN
        components[5] = "suite";
        for (Map.Entry<String, File> suiteEntry : suites.entrySet()) {
            String suiteArn = suiteEntry.getKey();
            components[6] = suiteArn;
            String fullsuiteArn = StringUtils.join(components, ":");
            ListTestsResult result = listTests(fullsuiteArn);
            for (Test test : result.getTests()) {
                String arn = test.getArn().split(":")[6];
                tests.put(arn, new File(suites.get(suiteArn), test.getName()));
                tests.get(arn).mkdirs();
            }
        }
        return tests;
    }

    private Map<String, File> getJobs(String runArn, File resultsDir) throws IOException, InterruptedException {
        Map<String, File> jobs = new HashMap<String, File>();
        ListJobsResult result = listJobs(runArn);
        for (Job job : result.getJobs()) {
            String arn = job.getArn().split(":")[6];
            String jobId = arn.substring(arn.lastIndexOf("/") + 1);
            // Two jobs can have same name. Appending Os version information to job name
            String osVersion = null;
            if (job.getDevice() != null) {
                osVersion = job.getDevice().getOs();
            }
            jobs.put(arn, new File(resultsDir, job.getName() + "-" + (osVersion != null ? osVersion : jobId)));
            jobs.get(arn).mkdirs();
        }
        return jobs;
    }


    public ListJobsResult listJobs(String runArn) {
        ListJobsRequest request = new ListJobsRequest()
                .withArn(runArn);

        return api.listJobs(request);
    }

    public ListSuitesResult listSuites(String jobArn) {
        ListSuitesRequest request = new ListSuitesRequest()
                .withArn(jobArn);

        return api.listSuites(request);
    }

    public ListTestsResult listTests(String suiteArn) {
        ListTestsRequest request = new ListTestsRequest().withArn(suiteArn);

        return api.listTests(request);
    }

    public int getUnmeteredDevices(String os) {
        AccountSettings accountSettings = getAccountSettings();
        if (accountSettings == null) {
            return 0;
        } else if (os.equalsIgnoreCase("ANDROID")) {
            return getAccountSettings().getUnmeteredDevices().get("ANDROID");
        } else if (os.equalsIgnoreCase("IOS")) {
            return getAccountSettings().getUnmeteredDevices().get("IOS");
        } else {
            return 0;
        }
    }

    public String getOs(String appArtifact) throws AWSDeviceFarmException {
        if (appArtifact.toLowerCase().endsWith("apk")) {
            return "Android";
        } else if (appArtifact.toLowerCase().endsWith("ipa")) {
            return "IOS";
        } else {
            throw new AWSDeviceFarmException(String.format("Unknown app artifact to upload: %s", appArtifact));
        }
    }

    public AccountSettings getAccountSettings() {
        try {
            GetAccountSettingsRequest request = new GetAccountSettingsRequest();
            return api.getAccountSettings(request).getAccountSettings();
        } catch (NotFoundException e) {
            return null;
        }
    }
}
