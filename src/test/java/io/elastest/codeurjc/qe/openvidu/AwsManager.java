package io.elastest.codeurjc.qe.openvidu;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.List;

import org.slf4j.Logger;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;

public class AwsManager {
    protected static final Logger logger = getLogger(AwsManager.class);

    protected AmazonCloudFormation awsCloudFormation;

    public AwsManager(AmazonCloudFormation awsCloudFormation) {
        this.awsCloudFormation = awsCloudFormation;
    }

    public AwsManager(String accessKeyId, String secretAccessKey,
            String region) {
        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKeyId,
                secretAccessKey);
        AWSStaticCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(
                credentials);

        awsCloudFormation = AmazonCloudFormationClient.builder()
                .withRegion(region).withCredentials(credentialsProvider)
                .build();
    }

    public CreateStackResult createStack(String stackName, String template,
            List<Parameter> parameters) {
        CreateStackRequest createRequest = new CreateStackRequest();
        createRequest.setStackName(stackName);
        createRequest.setTemplateBody(template);

        if (parameters != null && parameters.size() > 0) {
            createRequest.setParameters(parameters);
        }

        logger.info("Creating a stack called {}", stackName);

        return awsCloudFormation.createStack(createRequest);
    }

    public String waitForStackInitCompletion(String stackName,
            int stackExistTimeoutInSec) throws Exception {
        long endWaitTime = System.currentTimeMillis()
                + stackExistTimeoutInSec * 1000;

        Boolean completed = false;
        String stackStatus = "Unknown";
        String stackReason = "";

        System.out.print("Waiting for " + stackName);

        // Wait for stack initialization will be completed
        while (!completed) {
            Stack stack;
            do {
                // Wait for stack created
                Thread.sleep(2000);
                stack = getStack(stackName);
            } while (stack == null && System.currentTimeMillis() < endWaitTime);

            if (stack == null) {
                completed = true;
                stackStatus = "NO_SUCH_STACK";
                stackReason = "Stack has been deleted";
            } else {
                if (stack.getStackStatus()
                        .equals(StackStatus.CREATE_COMPLETE.toString())
                        || stack.getStackStatus()
                                .equals(StackStatus.CREATE_FAILED.toString())
                        || stack.getStackStatus()
                                .equals(StackStatus.ROLLBACK_FAILED.toString())
                        || stack.getStackStatus()
                                .equals(StackStatus.DELETE_COMPLETE.toString())
                        || stack.getStackStatus()
                                .equals(StackStatus.DELETE_FAILED.toString())) {
                    completed = true;
                    stackStatus = stack.getStackStatus();
                    stackReason = stack.getStackStatusReason();
                }

            }

            // Show we are waiting
            System.out.print(".");

            // Not done yet so sleep for N seconds.
            if (!completed)
                Thread.sleep(5000);
        }

        // Show we are done
        System.out.print("done\n");

        logger.info("Stack creation completed, the stack {} completed with {}",
                stackName, stackStatus + "(" + stackReason + ")");
        return stackStatus;
    }

    public List<Stack> getStacks() {
        return awsCloudFormation.describeStacks(new DescribeStacksRequest())
                .getStacks();
    }

    public Stack getStack(String stackName) {
        List<Stack> stacks = awsCloudFormation
                .describeStacks(
                        new DescribeStacksRequest().withStackName(stackName))
                .getStacks();

        for (Stack stack : stacks) {
            if (stack.getStackName().trim().equals(stackName.trim())) {
                return stack;
            }
        }
        return null;
    }

    public DeleteStackResult deleteStack(String stackName) {
        DeleteStackRequest deleteRequest = new DeleteStackRequest();
        deleteRequest.setStackName(stackName);
        return awsCloudFormation.deleteStack(deleteRequest);
    }

    public Parameter createParameter(String key, String value) {
        Parameter param = new Parameter();
        param.setParameterKey(key);
        param.setParameterValue(value);
        return param;
    }

}
