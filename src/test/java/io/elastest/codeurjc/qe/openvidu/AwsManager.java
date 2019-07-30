package io.elastest.codeurjc.qe.openvidu;

import static java.lang.invoke.MethodHandles.lookup;
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

public class AwsManager {
    protected static final Logger logger = getLogger(lookup().lookupClass());

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

    public List<Stack> getStacks() {
        return awsCloudFormation.describeStacks(new DescribeStacksRequest())
                .getStacks();
    }

    public Stack getStack(String stackName) {
        List<Stack> stacks = getStacks();

        for (Stack stack : stacks) {
            if (stack.getStackName() == stackName) {
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
